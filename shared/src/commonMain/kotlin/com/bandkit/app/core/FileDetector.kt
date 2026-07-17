// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

object FileDetector {

    const val TYPE_WATCHFACE = 16
    const val TYPE_FIRMWARE = 32
    const val TYPE_THIRD_PARTY_APP = 64

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val WATCHFACE_MAGIC = byteArrayOf(0x5A.toByte(), 0xA5.toByte(), 0x34, 0x12)

    fun detectFileType(fileName: String, fileData: ByteArray): Int {
        if (fileData.size < 4) {
            return TYPE_WATCHFACE
        }

        if (fileData.startsWith(ZIP_MAGIC)) {
            val text = try {
                val sb = StringBuilder()
                for (b in fileData) {
                    val c = b.toInt() and 0xFF
                    if (c in 0x20..0x7E || c == 0x0A || c == 0x0D || c == 0x09) {
                        sb.append(c.toChar())
                    }
                }
                sb.toString()
            } catch (_: Exception) {
                null
            }
            if (text != null) {
                if (text.contains("toolkit") || text.contains("manifest-watch.json")) {
                    return TYPE_THIRD_PARTY_APP
                }
            }
            val lowerName = fileName.lowercase()
            if (lowerName.endsWith(".rpk") || lowerName.endsWith(".abp")) {
                return TYPE_THIRD_PARTY_APP
            }
            if (lowerName.endsWith(".mwz")) {
                return TYPE_WATCHFACE
            }
            return TYPE_WATCHFACE
        }

        if (fileData.startsWith(WATCHFACE_MAGIC)) {
            return TYPE_WATCHFACE
        }

        val lowerName = fileName.lowercase()
        if (lowerName.endsWith(".rpk")) {
            return TYPE_THIRD_PARTY_APP
        }
        if (lowerName.endsWith(".bin")) {
            return TYPE_WATCHFACE
        }

        return TYPE_WATCHFACE
    }

    fun detectPackageName(fileData: ByteArray): String? {
        if (fileData.size < 4 || !fileData.startsWith(ZIP_MAGIC)) {
            return null
        }
        return null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i].toByte()) return false
        }
        return true
    }
}
