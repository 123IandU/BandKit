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

/** 检测文件类型，返回对应的 resType（16=表盘, 32=固件, 64=第三方应用） */
expect fun detectFileType(fileName: String, fileData: ByteArray): Int

/** 显示 Toast 提示 */
expect fun showToast(context: Any, message: String)

/** 保存最后一次连接的设备，用于启动时自动连接 */
expect fun saveLastDevice(context: Any, device: com.bandkit.app.models.SavedDevice?)

/** 读取最后一次连接的设备，不存在返回 null */
expect fun loadLastDevice(context: Any): com.bandkit.app.models.SavedDevice?
