// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

import com.miband.app.models.DeviceInfo
import com.miband.app.models.DeviceSession
import com.miband.app.models.InstalledApp
import com.miband.app.models.Watchface

actual class BandBurgManager {
    actual fun init() {}
    actual suspend fun connect(name: String, addr: String, authkey: String, connectType: Int): DeviceSession = throw UnsupportedOperationException("WasmJS not supported")
    actual fun disconnect(session: DeviceSession) {}
    actual fun destroySession(session: DeviceSession) {}
    actual suspend fun getDeviceInfo(session: DeviceSession): DeviceInfo = DeviceInfo()
    actual suspend fun getWatchfaceList(session: DeviceSession): List<Watchface> = emptyList()
    actual suspend fun setWatchface(session: DeviceSession, watchfaceId: String): Boolean = false
    actual suspend fun uninstallWatchface(session: DeviceSession, watchfaceId: String): Boolean = false
    actual suspend fun getAppList(session: DeviceSession): List<InstalledApp> = emptyList()
    actual suspend fun launchApp(session: DeviceSession, packageName: String): Boolean = false
    actual suspend fun uninstallApp(session: DeviceSession, packageName: String): Boolean = false
    actual fun processReceivedData(session: DeviceSession, data: ByteArray): String = ""
    actual suspend fun sendCommand(session: DeviceSession, typeId: Int, commandId: Int, payload: ByteArray?): String = "[]"
    actual suspend fun installFile(session: DeviceSession, fileName: String, fileData: ByteArray, resType: Int, packageName: String?, onProgress: (Float) -> Unit): Boolean = false
}

actual fun createBandBurgManager(): BandBurgManager = BandBurgManager()

actual fun initBandBurgContext(manager: BandBurgManager, context: Any) {}
