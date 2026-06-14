package com.nszconverter.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstracción de I/O entre SAF URIs y archivos accesibles desde Python.
 *
 * Chaquopy/CPython no puede leer content:// URIs. Estrategia:
 *  - Si el URI resuelve a un file path real (ExternalStorageProvider sobre
 *    /storage/emulated/0 o SD primaria) → lo usamos directo (cero copias).
 *  - Si no → caemos al fallback de copiar a externalCacheDir.
 *
 * Para outputs grandes (Yakuza Kiwami: 22 GB NSP) usamos externalCacheDir
 * en lugar de cacheDir interno, porque comparte la partición FUSE de 87+ GB
 * en lugar de la /data de ~30 GB.
 */
@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Cache interno — solo para archivos pequeños (keys, etc). */
    fun cacheInputDir(): File = File(context.cacheDir, "input").apply { mkdirs() }

    /**
     * Working dir para outputs grandes. Usa externalCacheDir (storage FUSE
     * compartido) si está disponible, sino /data como fallback.
     */
    fun workingOutputDir(): File {
        val external = context.externalCacheDir
        val base = external ?: context.cacheDir
        return File(base, "output").apply { mkdirs() }
    }

    fun keysFile(): File = File(File(context.filesDir, "keys").apply { mkdirs() }, "prod.keys")

    fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    /**
     * Intenta resolver un content:// (típicamente del ExternalStorageProvider)
     * a un file path real que Python pueda leer/escribir directamente.
     *
     * Soporta:
     *  - `primary:Some/Path`  →  /storage/emulated/0/Some/Path  (almacenamiento interno)
     *  - `<VOLUME-ID>:Path`   →  /storage/<VOLUME-ID>/Path      (tarjeta SD montada)
     *
     * Retorna null si el URI viene de otro provider (Downloads, Drive, etc.)
     * o si el path resultante no existe / no se puede leer.
     */
    fun resolveDocumentToPath(uri: Uri, requireExists: Boolean = true): File? {
        val docId = runCatching {
            if (DocumentsContract.isTreeUri(uri)) DocumentsContract.getTreeDocumentId(uri)
            else DocumentsContract.getDocumentId(uri)
        }.getOrNull() ?: return null

        val candidate: File? = when (uri.authority) {
            // ExternalStorageProvider: docId = "primary:Path/To/File" o "<volume>:Path"
            "com.android.externalstorage.documents" -> {
                val parts = docId.split(":", limit = 2)
                if (parts.size != 2) null
                else {
                    val volume = parts[0]
                    val relative = parts[1]
                    val base = if (volume == "primary") {
                        Environment.getExternalStorageDirectory().absolutePath
                    } else {
                        "/storage/$volume"
                    }
                    File(if (relative.isEmpty()) base else "$base/$relative")
                }
            }
            // DownloadsProvider: docId puede ser "raw:/storage/.../file" o un id numérico
            "com.android.providers.downloads.documents" -> when {
                docId.startsWith("raw:") -> File(docId.substring(4))
                docId.startsWith("msf:") -> null // MediaStore id — no es un file path
                else -> null
            }
            // Algunos providers exponen file URI directamente
            else -> if (uri.scheme == "file") uri.path?.let { File(it) } else null
        }

        candidate ?: return null
        return if (!requireExists || candidate.exists()) candidate else null
    }

    /** Resuelve una tree URI a un directorio escribible. */
    fun resolveTreeToDir(treeUri: Uri): File? {
        val dir = resolveDocumentToPath(treeUri, requireExists = false) ?: return null
        return if (dir.exists() && dir.isDirectory && dir.canWrite()) dir else null
    }

    fun availableBytes(path: File): Long = runCatching {
        StatFs(path.absolutePath).availableBytes
    }.getOrDefault(0L)

    fun availableInternalBytes(): Long = availableBytes(context.cacheDir)
    fun availableExternalCacheBytes(): Long = availableBytes(context.externalCacheDir ?: context.cacheDir)

    fun availableTreeBytes(treeUri: Uri): Long {
        // Caso preferido: resolver a un path real
        resolveTreeToDir(treeUri)?.let { return availableBytes(it) }
        // Fallback: pedirle al provider
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.openFileDescriptor(docUri, "r")?.use {
                StatFs(it.fileDescriptor.toString()).availableBytes
            } ?: availableInternalBytes()
        }.getOrElse { availableInternalBytes() }
    }

    suspend fun copyUriToFile(src: Uri, dst: File, onProgress: ((bytes: Long) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(src)?.use { input ->
                FileOutputStream(dst).use { output ->
                    val buf = ByteArray(1 shl 20)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        copied += n
                        onProgress?.invoke(copied)
                    }
                }
            } ?: return@withContext false
            true
        }.getOrDefault(false)
    }

    suspend fun copyFileToTree(src: File, treeUri: Uri, displayName: String, mimeType: String = "application/octet-stream"): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
            val uniqueName = uniquifyName(tree, displayName)
            val out = tree.createFile(mimeType, uniqueName) ?: return@withContext null
            context.contentResolver.openOutputStream(out.uri)?.use { os ->
                src.inputStream().use { it.copyTo(os, 1 shl 20) }
            }
            out.uri
        }.getOrNull()
    }

    /**
     * Mueve un archivo de output a la carpeta destino SAF.
     *
     * Si el destino resuelve a un path real (mismo filesystem que src),
     * usamos `renameTo` que es atómico e instantáneo → no doble I/O para los
     * 22 GB de un Yakuza Kiwami. Sino, fallback al copy clásico.
     */
    suspend fun moveOrCopyToTree(src: File, treeUri: Uri, displayName: String): Uri? = withContext(Dispatchers.IO) {
        val targetDir = resolveTreeToDir(treeUri)
        if (targetDir != null) {
            val finalName = uniquifyNameInDir(targetDir, displayName)
            val dst = File(targetDir, finalName)
            if (src.renameTo(dst)) {
                // Construimos el URI del documento creado
                val parentDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
                return@withContext if (parentDocId != null) {
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, "$parentDocId/$finalName")
                } else {
                    Uri.fromFile(dst)
                }
            }
            // El rename puede fallar si cruzan filesystems — usamos copy + delete
            val ok = src.copyTo(dst, overwrite = false).exists()
            if (ok) {
                src.delete()
                return@withContext Uri.fromFile(dst)
            }
        }
        copyFileToTree(src, treeUri, displayName, "application/octet-stream")
    }

    private fun uniquifyName(parent: DocumentFile, desired: String): String {
        if (parent.findFile(desired) == null) return desired
        val (base, ext) = splitName(desired)
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (parent.findFile(candidate) == null) return candidate
            i++
        }
    }

    private fun uniquifyNameInDir(dir: File, desired: String): String {
        if (!File(dir, desired).exists()) return desired
        val (base, ext) = splitName(desired)
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!File(dir, candidate).exists()) return candidate
            i++
        }
    }

    private fun splitName(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    }

    fun cleanupCache(vararg files: File) {
        files.forEach { runCatching { if (it.exists()) it.delete() } }
    }

    fun cleanupDir(dir: File) {
        runCatching {
            dir.walkBottomUp().forEach { runCatching { it.delete() } }
        }
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return if (cleaned.isEmpty()) "file" else cleaned
    }
}
