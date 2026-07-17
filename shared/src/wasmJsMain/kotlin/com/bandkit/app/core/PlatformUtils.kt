// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import com.bandkit.app.models.SavedDevice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val IO: CoroutineDispatcher = Dispatchers.Default

actual fun formatTimestamp(timestamp: Long): String {
    val totalSeconds = timestamp / 1000
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

@JsFun("() => Date.now()")
private external fun jsDateNow(): Double

actual fun currentTimeMillis(): Long = jsDateNow().toLong()

actual fun loadSavedDevices(context: Any): List<SavedDevice> = emptyList()

actual fun saveSavedDevices(context: Any, devices: List<SavedDevice>) {}

actual fun launchSettingsActivity(context: Any) {}

actual fun loadShowLogs(context: Any): Boolean = true

actual fun saveShowLogs(context: Any, value: Boolean) {}

actual suspend fun exportSavedDevicesToFile(context: Any, devices: List<SavedDevice>): Boolean = false

actual suspend fun importSavedDevicesFromFile(context: Any): List<SavedDevice>? = null
