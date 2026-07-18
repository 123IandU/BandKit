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
