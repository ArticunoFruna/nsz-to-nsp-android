package com.nszconverter.data.repository

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.nszconverter.domain.model.KeysStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PythonBridge @Inject constructor() {

    private val converter: PyObject get() = Python.getInstance().getModule("converter")

    suspend fun validateKeys(localPath: String): KeysStatus = withContext(Dispatchers.IO) {
        val res = converter.callAttr("validate_keys", localPath)
        val valid = res.dictBool("valid")
        if (valid) {
            val count = res.dictInt("key_count")
            val hasTitle = res.dictBool("has_title_keys")
            KeysStatus.Valid(count, hasTitle)
        } else {
            val err = res.dictStr("error")
            if (err.isEmpty()) KeysStatus.NotConfigured else KeysStatus.Invalid(err)
        }
    }

    suspend fun setupKeys(localPath: String): String = withContext(Dispatchers.IO) {
        converter.callAttr("setup_keys", localPath).toString()
    }

    suspend fun decompress(nszPath: String, outputDir: String): DecompressResult = withContext(Dispatchers.IO) {
        val py = converter.callAttr("decompress_nsz", nszPath, outputDir)
        DecompressResult(
            success = py.dictBool("success"),
            outputPath = py.dictStr("output_path"),
            error = py.dictStr("error"),
            durationSeconds = py.dictFloat("duration_seconds"),
            stdoutTail = py.dictStr("stdout"),
            stderrTail = py.dictStr("stderr"),
        )
    }

    suspend fun nszVersion(): String = withContext(Dispatchers.IO) {
        runCatching { converter.callAttr("nsz_version").toString() }.getOrDefault("unknown")
    }

    private fun PyObject.dictStr(key: String): String =
        runCatching { callAttr("get", key)?.toString().orEmpty() }.getOrDefault("")

    private fun PyObject.dictBool(key: String): Boolean =
        runCatching { callAttr("get", key)?.toBoolean() ?: false }.getOrDefault(false)

    private fun PyObject.dictInt(key: String): Int =
        runCatching { callAttr("get", key)?.toInt() ?: 0 }.getOrDefault(0)

    private fun PyObject.dictFloat(key: String): Float =
        runCatching { callAttr("get", key)?.toFloat() ?: 0f }.getOrDefault(0f)

    data class DecompressResult(
        val success: Boolean,
        val outputPath: String,
        val error: String,
        val durationSeconds: Float,
        val stdoutTail: String,
        val stderrTail: String,
    )
}
