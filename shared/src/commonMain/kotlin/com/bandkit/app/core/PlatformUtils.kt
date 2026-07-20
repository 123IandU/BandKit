// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import com.bandkit.app.models.SavedDevice

expect val IO: kotlinx.coroutines.CoroutineDispatcher

expect fun formatTimestamp(timestamp: Long): String

expect fun currentTimeMillis(): Long

expect fun loadSavedDevices(context: Any): List<SavedDevice>

expect fun saveSavedDevices(context: Any, devices: List<SavedDevice>)

expect suspend fun exportSavedDevicesToFile(context: Any, devices: List<SavedDevice>): Boolean

expect suspend fun importSavedDevicesFromFile(context: Any): List<SavedDevice>?

expect fun launchAboutActivity(context: Any)

expect fun loadShowLogs(context: Any): Boolean

expect fun saveShowLogs(context: Any, value: Boolean)

/** 从文件数据中提取包名（.rpk）或表盘 ID（.bin），提取不到返回 null */
expect fun extractFileIdentifier(fileName: String, fileData: ByteArray): String?
