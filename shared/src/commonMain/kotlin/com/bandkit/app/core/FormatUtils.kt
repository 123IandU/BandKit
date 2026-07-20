// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

/** 格式化文件大小：自适应 B/KB/MB */
fun formatFileSize(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> "${"%.1f".format(bytes.toFloat() / (1024 * 1024))} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/** 解析存储使用百分比（字符串格式如 "1.5 GB"） */
fun parseStoragePercent(used: String, total: String): Float {
    fun parseBytes(s: String): Float {
        val num = s.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: return 0f
        return when {
            s.contains("TB", ignoreCase = true) -> num * 1024f * 1024f * 1024f * 1024f
            s.contains("GB", ignoreCase = true) -> num * 1024f * 1024f * 1024f
            s.contains("MB", ignoreCase = true) -> num * 1024f * 1024f
            s.contains("KB", ignoreCase = true) -> num * 1024f
            else -> num
        }
    }
    val usedBytes = parseBytes(used)
    val totalBytes = parseBytes(total)
    return if (totalBytes > 0f) (usedBytes / totalBytes).coerceIn(0f, 1f) else 0f
}
