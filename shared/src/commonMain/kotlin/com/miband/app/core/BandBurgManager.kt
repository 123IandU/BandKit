// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

import com.miband.app.models.DeviceInfo
import com.miband.app.models.DeviceSession
import com.miband.app.models.InstalledApp
import com.miband.app.models.Watchface

expect class BandBurgManager {
    fun init()

    suspend fun connect(
        name: String,
        addr: String,
        authkey: String,
        connectType: Int,
    ): DeviceSession

    fun disconnect(session: DeviceSession)

    fun destroySession(session: DeviceSession)

    suspend fun getDeviceInfo(session: DeviceSession): DeviceInfo

    suspend fun getWatchfaceList(session: DeviceSession): List<Watchface>

    suspend fun setWatchface(session: DeviceSession, watchfaceId: String): Boolean

    suspend fun uninstallWatchface(session: DeviceSession, watchfaceId: String): Boolean

    suspend fun getAppList(session: DeviceSession): List<InstalledApp>

    suspend fun launchApp(session: DeviceSession, packageName: String): Boolean

    suspend fun uninstallApp(session: DeviceSession, packageName: String): Boolean

    fun processReceivedData(session: DeviceSession, data: ByteArray): String

    suspend fun sendCommand(session: DeviceSession, typeId: Int, commandId: Int, payload: ByteArray? = null): String

    suspend fun installFile(session: DeviceSession, fileName: String, fileData: ByteArray, resType: Int, packageName: String?, onProgress: (Float) -> Unit): Boolean
}

expect fun createBandBurgManager(): BandBurgManager

expect fun initBandBurgContext(manager: BandBurgManager, context: Any)
