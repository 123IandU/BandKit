// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import com.bandkit.app.models.SavedDevice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual val IO: CoroutineDispatcher = Dispatchers.IO

actual fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun loadSavedDevices(context: Any): List<SavedDevice> = emptyList()

actual fun saveSavedDevices(context: Any, devices: List<SavedDevice>) {}

actual fun launchAboutActivity(context: Any) {}

actual fun loadShowLogs(context: Any): Boolean = true

actual fun saveShowLogs(context: Any, value: Boolean) {}

actual suspend fun exportSavedDevicesToFile(context: Any, devices: List<SavedDevice>): Boolean = false

actual suspend fun importSavedDevicesFromFile(context: Any): List<SavedDevice>? = null

actual fun extractFileIdentifier(fileName: String, fileData: ByteArray): String? = null

actual fun detectFileType(fileName: String, fileData: ByteArray): Int {
    return when {
        fileName.endsWith(".rpk", true) -> 64
        fileName.endsWith(".bin", true) -> 16
        else -> 16
    }
}

actual fun showToast(context: Any, message: String) {}

actual fun saveLastDevice(context: Any, device: SavedDevice?) {}

actual fun loadLastDevice(context: Any): SavedDevice? = null
