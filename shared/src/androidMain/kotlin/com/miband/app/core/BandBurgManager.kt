// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

import android.content.Context
import android.util.Log
import com.astrobox.app.NativeDevice
import com.miband.app.models.DeviceInfo
import com.miband.app.models.DeviceSession
import com.miband.app.models.InstalledApp
import com.miband.app.models.SavedDevice
import com.miband.app.models.Watchface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

actual class BandBurgManager {

    private val sessions = ConcurrentHashMap<String, DeviceSession>()
    private val initialized = AtomicBoolean(false)

    actual fun init() {
        if (!initialized.compareAndSet(false, true)) {
            Log.d(TAG, "NativeDevice already initialized, skipping")
            return
        }
        NativeDevice.registerEventSink { event, payload ->
            Log.d(TAG, "NativeDevice event: $event -> $payload")
        }
        Log.d(TAG, "NativeDevice event sink registered")
    }

    fun init(appContext: Context) {
        // NativeDevice handles Bluetooth internally, no Context needed
        init()
    }

    actual suspend fun connect(
        name: String,
        addr: String,
        authkey: String,
        connectType: Int,
    ): DeviceSession = withContext(Dispatchers.IO) {
        Log.d(TAG, "Connecting to $addr via NativeDevice")
        val result = NativeDevice.deviceConnect(
            name = name,
            addr = addr,
            authkey = authkey,
            sarVersion = 2L,
            connectType = if (connectType == 1) "BLE" else "SPP",
            txWinOverrunAllowance = byteArrayOf(8),
        )
        Log.d(TAG, "Connect result: $result")

        if (result.startsWith("Error:")) {
            throw IllegalStateException("Device connect failed: $result")
        }

        val session = DeviceSession(
            handle = addr.hashCode().toLong(),
            device = SavedDevice(
                id = System.currentTimeMillis().toString(),
                name = name,
                addr = addr,
                authkey = authkey,
                connectType = if (connectType == 1) "BLE" else "SPP",
            ),
        )
        sessions[addr] = session
        session
    }

    actual fun disconnect(session: DeviceSession) {
        NativeDevice.deviceDisconnect(session.device.addr)
        sessions.remove(session.device.addr)
        Log.d(TAG, "Disconnected from ${session.device.addr}")
    }

    actual fun destroySession(session: DeviceSession) {
        disconnect(session)
    }

    actual suspend fun getDeviceInfo(session: DeviceSession): DeviceInfo {
        val addr = session.device.addr
        return try {
            val infoJson = withContext(Dispatchers.IO) {
                NativeDevice.deviceGetData(addr, "info")
            }
            val statusJson = withContext(Dispatchers.IO) {
                NativeDevice.deviceGetData(addr, "status")
            }
            val storageJson = withContext(Dispatchers.IO) {
                NativeDevice.deviceGetData(addr, "storage")
            }
            ResponseParser.parseDeviceInfo(infoJson, statusJson, storageJson, session.device.name)
        } catch (e: Exception) {
            Log.e(TAG, "getDeviceInfo failed", e)
            DeviceInfo(model = session.device.name, serialNumber = session.device.addr)
        }
    }

    actual suspend fun getWatchfaceList(session: DeviceSession): List<Watchface> {
        val addr = session.device.addr
        return try {
            val json = withContext(Dispatchers.IO) {
                NativeDevice.watchfaceGetList(addr)
            }
            ResponseParser.parseWatchfaceList(json)
        } catch (e: Exception) {
            Log.e(TAG, "getWatchfaceList failed", e)
            emptyList()
        }
    }

    actual suspend fun setWatchface(session: DeviceSession, watchfaceId: String): Boolean {
        val addr = session.device.addr
        return withContext(Dispatchers.IO) {
            NativeDevice.watchfaceSetCurrent(addr, watchfaceId)
        }
    }

    actual suspend fun uninstallWatchface(session: DeviceSession, watchfaceId: String): Boolean {
        val addr = session.device.addr
        return withContext(Dispatchers.IO) {
            NativeDevice.watchfaceUninstall(addr, watchfaceId)
        }
    }

    actual suspend fun getAppList(session: DeviceSession): List<InstalledApp> {
        val addr = session.device.addr
        return try {
            val json = withContext(Dispatchers.IO) {
                NativeDevice.thirdpartyappGetList(addr)
            }
            ResponseParser.parseAppList(json)
        } catch (e: Exception) {
            Log.e(TAG, "getAppList failed", e)
            emptyList()
        }
    }

    actual suspend fun launchApp(session: DeviceSession, packageName: String): Boolean {
        val addr = session.device.addr
        return withContext(Dispatchers.IO) {
            NativeDevice.thirdpartyappLaunch(addr, packageName, "")
        }
    }

    actual suspend fun uninstallApp(session: DeviceSession, packageName: String): Boolean {
        val addr = session.device.addr
        return withContext(Dispatchers.IO) {
            NativeDevice.thirdpartyappUninstall(addr, packageName)
        }
    }

    actual suspend fun installFile(
        session: DeviceSession,
        fileName: String,
        fileData: ByteArray,
        resType: Int,
        packageName: String?,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val addr = session.device.addr
        return try {
            Log.d(TAG, "Installing file: $fileName (type=$resType, size=${fileData.size})")
            onProgress(0f)

            val result = withContext(Dispatchers.IO) {
                NativeDevice.deviceInstall(
                    addr = addr,
                    resType = byteArrayOf(resType.toByte()),
                    data = fileData,
                    packageName = packageName,
                    progressCb = null,
                    watchfaceId = null,
                )
            }

            if (result) {
                onProgress(1f)
                Log.d(TAG, "File install completed: $fileName")
            } else {
                Log.e(TAG, "File install failed: $fileName")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "installFile failed: $fileName", e)
            false
        }
    }

    actual fun processReceivedData(session: DeviceSession, data: ByteArray): String = ""

    actual suspend fun sendCommand(
        session: DeviceSession,
        typeId: Int,
        commandId: Int,
        payload: ByteArray?,
    ): String = ""

    companion object {
        private const val TAG = "BandBurgManager"
    }
}

actual fun createBandBurgManager(): BandBurgManager = BandBurgManager()

actual fun initBandBurgContext(manager: BandBurgManager, context: Any) {
    // NativeDevice handles Bluetooth internally; Context no longer needed
    // init() is called separately via manager.init() to avoid main-thread JNI
}
