// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import android.content.Context
import android.net.Uri
import com.bandkit.app.models.SavedDevice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual val IO: CoroutineDispatcher = Dispatchers.IO

actual fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun loadSavedDevices(context: Any): List<SavedDevice> = try {
    val ctx = context as Context
    val prefs = ctx.getSharedPreferences("bandburg", Context.MODE_PRIVATE)
    Json.decodeFromString(prefs.getString("devices", "[]") ?: "[]")
} catch (_: Exception) {
    emptyList()
}

actual fun saveSavedDevices(context: Any, devices: List<SavedDevice>) {
    val ctx = context as Context
    val prefs = ctx.getSharedPreferences("bandburg", Context.MODE_PRIVATE)
    prefs.edit().putString("devices", Json.encodeToString(devices)).apply()
}

actual fun launchAboutActivity(context: Any) {
    val ctx = context as Context
    val intent = android.content.Intent()
    intent.setClassName(ctx.packageName, "com.bandkit.app.AboutActivity")
    ctx.startActivity(intent)
}

actual fun loadShowLogs(context: Any): Boolean {
    val ctx = context as Context
    val prefs = ctx.getSharedPreferences("bandburg", Context.MODE_PRIVATE)
    return prefs.getBoolean("show_logs", true)
}

actual fun saveShowLogs(context: Any, value: Boolean) {
    val ctx = context as Context
    val prefs = ctx.getSharedPreferences("bandburg", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("show_logs", value).apply()
}

actual fun extractFileIdentifier(fileName: String, fileData: ByteArray): String? {
    return if (fileName.endsWith(".rpk", true)) {
        // 从 RPK 的 manifest.json 中提取 package 字段
        try {
            val zis = java.util.zip.ZipInputStream(fileData.inputStream())
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.contains("manifest.json")) {
                    val jsonStr = zis.readBytes().decodeToString()
                    zis.closeEntry()
                    zis.close()
                    // 简单 JSON 解析提取 package 字段
                    val pkgRegex = Regex(""""package"\s*:\s*"([^"]+)"""")
                    return pkgRegex.find(jsonStr)?.groupValues?.get(1)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
            null
        } catch (_: Exception) {
            null
        }
    } else if (fileName.endsWith(".bin", true)) {
        // 从 .bin 文件 offset=34, field_len=24 中扫描 9/12 位 ID
        val offset = 34; val fieldLen = 24
        if (fileData.size < offset + fieldLen) return null
        val field = fileData.copyOfRange(offset, offset + fieldLen)
        var i = 0
        while (i < fieldLen) {
            val c = field[i].toInt().toChar()
            if (!c.isLetterOrDigit()) { i++; continue }
            val start = i
            while (i < fieldLen && field[i].toInt().toChar().isLetterOrDigit()) i++
            val runLen = i - start
            if (runLen == 9 || runLen == 12) return field.copyOfRange(start, i).decodeToString().take(runLen)
        }
        null
    } else null
}

object DeviceExportImportState {
    var pendingExportResult: ((Boolean) -> Unit)? = null
    var pendingImportResult: ((List<SavedDevice>?) -> Unit)? = null
    var exportDevices: List<SavedDevice>? = null
    var importLauncher: ((String) -> Unit)? = null
    var exportLauncher: ((String, String) -> Unit)? = null
    var filePickerLauncher: ((String) -> Unit)? = null
}

actual suspend fun exportSavedDevicesToFile(context: Any, devices: List<SavedDevice>): Boolean = suspendCoroutine { cont ->
    DeviceExportImportState.exportDevices = devices
    DeviceExportImportState.pendingExportResult = { result ->
        DeviceExportImportState.exportDevices = null
        cont.resume(result)
    }

    val launcher = DeviceExportImportState.exportLauncher
    if (launcher == null) {
        DeviceExportImportState.pendingExportResult = null
        cont.resume(false)
        return@suspendCoroutine
    }

    try {
        launcher("application/json", "bandburg_devices.json")
    } catch (e: Exception) {
        DeviceExportImportState.pendingExportResult = null
        cont.resume(false)
    }
}

actual suspend fun importSavedDevicesFromFile(context: Any): List<SavedDevice>? = suspendCoroutine { cont ->
    DeviceExportImportState.pendingImportResult = { result ->
        cont.resume(result)
    }

    val launcher = DeviceExportImportState.importLauncher
    if (launcher == null) {
        DeviceExportImportState.pendingImportResult = null
        cont.resume(null)
        return@suspendCoroutine
    }

    try {
        launcher("application/json")
    } catch (e: Exception) {
        DeviceExportImportState.pendingImportResult = null
        cont.resume(null)
    }
}

fun handleDeviceExportResult(context: Context, uri: Uri?) {
    val callback = DeviceExportImportState.pendingExportResult ?: return
    DeviceExportImportState.pendingExportResult = null

    if (uri == null) {
        callback(false)
        return
    }

    try {
        val devices = DeviceExportImportState.exportDevices ?: run {
            callback(false)
            return
        }
        val json = Json.encodeToString(devices)
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(json.toByteArray())
        }
        callback(true)
    } catch (e: Exception) {
        callback(false)
    }
}

fun handleDeviceImportResult(context: Context, uri: Uri?) {
    val callback = DeviceExportImportState.pendingImportResult ?: return
    DeviceExportImportState.pendingImportResult = null

    if (uri == null) {
        callback(null)
        return
    }

    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()

        if (bytes != null) {
            val json = String(bytes)
            val devices = Json.decodeFromString<List<SavedDevice>>(json)
            callback(devices)
        } else {
            callback(null)
        }
    } catch (e: Exception) {
        callback(null)
    }
}

actual fun detectFileType(fileName: String, fileData: ByteArray): Int {
    try {
        val raw = NativeDevice.deviceGetFileType(fileData, fileName).toInt() and 0xFF
        // 只认有效 MassDataType 值（16/32/48/50/52/53/64/91），否则走 fallback
        if (raw in listOf(16, 32, 48, 50, 52, 53, 64, 91)) return raw
    } catch (_: Exception) { /* fallback */ }
    return when {
        fileName.endsWith(".rpk", true) -> 64
        fileName.endsWith(".fw", true) || fileName.endsWith(".ota", true) -> 32
        fileName.endsWith(".bin", true) -> 16
        else -> 16
    }
}

actual fun showToast(context: Any, message: String) {
    try {
        val ctx = context as android.content.Context
        android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
    } catch (_: Exception) { /* ignore */ }
}

actual fun saveLastDevice(context: Any, device: SavedDevice?) {
    try {
        val ctx = context as android.content.Context
        val prefs = ctx.getSharedPreferences("bandburg", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("last_device", if (device != null) Json.encodeToString(device) else null).apply()
    } catch (_: Exception) { /* ignore */ }
}

actual fun loadLastDevice(context: Any): SavedDevice? {
    return try {
        val ctx = context as android.content.Context
        val prefs = ctx.getSharedPreferences("bandburg", android.content.Context.MODE_PRIVATE)
        val json = prefs.getString("last_device", null) ?: return null
        Json.decodeFromString<SavedDevice>(json)
    } catch (_: Exception) { null }
}
