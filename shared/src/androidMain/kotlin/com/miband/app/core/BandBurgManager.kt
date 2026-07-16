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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

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

        // 如果是第三方应用（type=64）且没传包名，从 RPK 中提取
        val resolvedPackageName = if (packageName == null && resType == 64) {
            extractRpkgPackageName(fileData)
        } else {
            packageName
        }

        return try {
            Log.d(
                TAG,
                "Installing file: $fileName (type=$resType, size=${fileData.size})" +
                    if (resolvedPackageName != null) ", pkg=$resolvedPackageName" else "",
            )
            onProgress(0f)

            val result = withContext(Dispatchers.IO) {
                NativeDevice.deviceInstall(
                    addr = addr,
                    resType = byteArrayOf(resType.toByte()),
                    data = fileData,
                    packageName = resolvedPackageName,
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

    /**
     * 从 RPK/ZIP 文件中提取第三方应用包名
     * 优先读取 manifest.json 中的 "package" 字段，
     * 其次扫描 ZIP 条目目录名匹配 com.xxx.xxx 格式
     */
    private fun extractRpkgPackageName(fileData: ByteArray): String? {
        return try {
            val zis = ZipInputStream(ByteArrayInputStream(fileData))

            // 第一遍：找 manifest.json 并解析 "package" 字段
            val rpkJson = Json { ignoreUnknownKeys = true }
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.removePrefix("/").let { it == "manifest.json" || it.endsWith("/manifest.json") }) {
                    val jsonText = zis.readBytes().decodeToString()
                    val pkg = try {
                        val obj = rpkJson.parseToJsonElement(jsonText).jsonObject
                        obj["package"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    } catch (_: Exception) { null }
                    if (pkg != null) {
                        zis.close()
                        Log.d(TAG, "extractRpkgPackageName: found from manifest.json: $pkg")
                        return pkg
                    }
                }
                entry = zis.nextEntry
            }

            // 第二遍：扫描目录名匹配 com.xxx.xxx
            zis.close()
            val zis2 = ZipInputStream(ByteArrayInputStream(fileData))
            entry = zis2.nextEntry
            while (entry != null) {
                val parts = entry.name.split("/")
                for (part in parts) {
                    if (part.count { it == '.' } >= 2) {
                        val trimmed = part.trim()
                        if (trimmed.matches(Regex("^[a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*)+$"))) {
                            zis2.close()
                            Log.d(TAG, "extractRpkgPackageName: found from path: $trimmed")
                            return trimmed
                        }
                    }
                }
                entry = zis2.nextEntry
            }
            zis2.close()
            Log.w(TAG, "extractRpkgPackageName: no package name found in RPK")
            null
        } catch (e: Exception) {
            Log.w(TAG, "extractRpkgPackageName failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "BandBurgManager"
    }
}

actual fun createBandBurgManager(): BandBurgManager = BandBurgManager()

actual fun initBandBurgContext(manager: BandBurgManager, context: Any) {
    // NativeDevice handles Bluetooth internally; Context no longer needed
    // init() is called separately via manager.init() to avoid main-thread JNI
}
