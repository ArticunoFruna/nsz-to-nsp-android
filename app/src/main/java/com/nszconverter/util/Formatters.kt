package com.nszconverter.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.humanBytes(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = this.toDouble()
    var idx = 0
    while (size >= 1024 && idx < units.lastIndex) {
        size /= 1024
        idx++
    }
    return if (idx == 0) "${this} B" else "%.1f %s".format(size, units[idx])
}

fun Int.humanSeconds(): String {
    if (this < 0) return "—"
    if (this < 60) return "${this}s"
    val m = this / 60
    val s = this % 60
    if (m < 60) return "${m}m ${s}s"
    val h = m / 60
    val mm = m % 60
    return "${h}h ${mm}m"
}

fun Long.formatTimestamp(pattern: String = "yyyy-MM-dd HH:mm"): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(this))
