// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import android.content.Context
import android.util.Log
import com.bandkit.app.core.formatFileSize
import com.bandkit.app.models.DeviceInfo
import com.bandkit.app.models.DeviceSession
import com.bandkit.app.models.InstalledApp
import com.bandkit.app.models.SavedDevice
import com.bandkit.app.models.Watchface
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
        NativeDevice.registerThirdpartyAppMessageCallback { json ->
            Log.d(TAG, "NativeDevice thirdpartyapp_message: $json")
        }
        NativeDevice.registerPbPacketCallback { json ->
            Log.d(TAG, "NativeDevice pb_packet: $json")
        }
        Log.d(TAG, "NativeDevice event sinks registered")
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
        sarVersion: Int,
    ): DeviceSession = withContext(Dispatchers.IO) {
        Log.d(TAG, "Connecting to $addr via NativeDevice")
        val result = NativeDevice.deviceConnect(
            name = name,
            addr = addr,
            authkey = authkey,
            sarVersion = sarVersion.toLong(),
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
            Log.d(TAG, "installFile: resType=64, no packageName provided, extracting from RPK...")
            val pkg = extractRpkgPackageName(fileData)
            Log.d(TAG, "installFile: extractRpkgPackageName returned: ${pkg ?: "null"}")
            pkg
        } else {
            Log.d(TAG, "installFile: using provided packageName=$packageName, resType=$resType")
            packageName
        }

        return try {
            Log.d(
                TAG,
                "Installing file: $fileName (type=$resType, size=${formatFileSize(fileData.size)})" +
                    if (resolvedPackageName != null) ", pkg=$resolvedPackageName" else "",
            )
            onProgress(0f)

            Log.d(TAG, "installFile: calling NativeDevice.deviceInstall (addr=$addr, resType=${resType.toByte()})...")
            // 表盘先尝试从 .bin 文件提取 ID，否则用 MD5 哈希生成
            val installWatchfaceId = if (resType == 16) {
                extractWatchfaceIdFromFile(fileData) ?: run {
                    val md = java.security.MessageDigest.getInstance("MD5")
                    md.update(fileData)
                    val hash = md.digest()
                    val num = hash.take(6).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) } % 1_000_000_000_000L
                    "%012d".format(num)
                }
            } else {
                null
            }
            val result = withContext(Dispatchers.IO) {
                NativeDevice.deviceInstall(
                    addr = addr,
                    resType = byteArrayOf(resType.toByte()),
                    data = fileData,
                    packageName = resolvedPackageName,
                    progressCb = { jsonPayload: Any? ->
                        if (jsonPayload is String) {
                            try {
                                val obj = Json.parseToJsonElement(jsonPayload).jsonObject
                                val progress = obj["progress"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                                if (progress != null) onProgress(progress)
                            } catch (_: Exception) {
                                // 忽略格式异常的进度 JSON
                            }
                        }
                    },
                    watchfaceId = installWatchfaceId,
                )
            }
            Log.d(TAG, "installFile: NativeDevice.deviceInstall returned: $result")

            if (result) {
                onProgress(1f)
                Log.d(TAG, "File install completed: $fileName")
            } else {
                Log.e(TAG, "File install FAILED: $fileName")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "installFile threw exception for: $fileName", e)
            false
        }
    }

    /**
     * 从 .bin 文件元数据中提取表盘 ID（与 corelib resutils::get_watchface_id 逻辑一致）
     * 默认偏移 34，字段长度 24，扫描 9 或 12 位字母数字段
     */
    private fun extractWatchfaceIdFromFile(fileData: ByteArray): String? {
        val offset = 34
        val fieldLen = 24
        if (fileData.size < offset + fieldLen) return null
        val field = fileData.copyOfRange(offset, offset + fieldLen)
        var i = 0
        while (i < fieldLen) {
            val c = field[i].toInt().toChar()
            if (!c.isLetterOrDigit()) {
                i++
                continue
            }
            val start = i
            while (i < fieldLen && field[i].toInt().toChar().isLetterOrDigit()) i++
            val runLen = i - start
            if (runLen == 9 || runLen == 12) {
                return field.copyOfRange(start, i).decodeToString().take(runLen)
            }
        }
        return null
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
     * 读取 manifest.json 中的 "package" 字段
     */
    private fun extractRpkgPackageName(fileData: ByteArray): String? {
        return try {
            val zis = ZipInputStream(ByteArrayInputStream(fileData))
            val rpkJson = Json { ignoreUnknownKeys = true }
            var entry = zis.nextEntry
            var entryIndex = 0
            Log.d(TAG, "extractRpkgPackageName: scanning ZIP entries...")
            while (entry != null) {
                entryIndex++
                val nameClean = entry.name.removePrefix("/")
                Log.d(TAG, "extractRpkgPackageName: entry #$entryIndex: ${entry.name} (size=${entry.size}, clean=$nameClean)")
                if (nameClean == "manifest.json" || nameClean.endsWith("/manifest.json")) {
                    Log.d(TAG, "extractRpkgPackageName: found manifest.json at entry #$entryIndex")
                    val rawBytes = zis.readBytes()
                    val jsonText = rawBytes.decodeToString()
                    Log.d(TAG, "extractRpkgPackageName: manifest.json content (${rawBytes.size} bytes): $jsonText")
                    val pkg = try {
                        val obj = rpkJson.parseToJsonElement(jsonText).jsonObject
                        val found = obj["package"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        Log.d(TAG, "extractRpkgPackageName: parsed JSON, 'package' field = ${found ?: "null/blank"}")
                        found
                    } catch (e: Exception) {
                        Log.w(TAG, "extractRpkgPackageName: failed to parse manifest.json JSON: ${e.message}")
                        null
                    }
                    zis.close()
                    if (pkg != null) {
                        Log.d(TAG, "extractRpkgPackageName: found package name: $pkg")
                        return pkg
                    }
                    Log.w(TAG, "extractRpkgPackageName: manifest.json found but no 'package' field")
                    return null
                }
                entry = zis.nextEntry
            }
            zis.close()
            Log.w(TAG, "extractRpkgPackageName: no manifest.json found in RPK (scanned $entryIndex entries)")
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
