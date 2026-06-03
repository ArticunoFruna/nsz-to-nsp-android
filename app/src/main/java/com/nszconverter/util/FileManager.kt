package com.nszconverter.util

import android.content.Context
import android.net.Uri
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
 * Chaquopy/CPython no puede leer content:// URIs, así que copiamos al cache
 * dir, ejecutamos la conversión, y copiamos el resultado de vuelta a SAF.
 */
@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun cacheInputDir(): File = File(context.cacheDir, "input").apply { mkdirs() }
    fun cacheOutputDir(): File = File(context.cacheDir, "output").apply { mkdirs() }
    fun keysFile(): File = File(File(context.filesDir, "keys").apply { mkdirs() }, "prod.keys")

    fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    fun querySize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
    }

    fun availableInternalBytes(): Long {
        val stat = StatFs(context.cacheDir.absolutePath)
        return stat.availableBytes
    }

    fun availableTreeBytes(treeUri: Uri): Long {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.openFileDescriptor(docUri, "r")?.use {
                val stat = StatFs(it.fileDescriptor.toString())
                stat.availableBytes
            } ?: availableInternalBytes()
        }.getOrElse { availableInternalBytes() }
    }

    suspend fun copyUriToFile(src: Uri, dst: File, onProgress: ((bytes: Long) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(src)?.use { input ->
                FileOutputStream(dst).use { output ->
                    val buf = ByteArray(1 shl 20) // 1 MiB
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

    private fun uniquifyName(parent: DocumentFile, desired: String): String {
        if (parent.findFile(desired) == null) return desired
        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (parent.findFile(candidate) == null) return candidate
            i++
        }
    }

    fun cleanupCache(vararg files: File) {
        files.forEach { runCatching { if (it.exists()) it.delete() } }
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return if (cleaned.isEmpty()) "file" else cleaned
    }
}
