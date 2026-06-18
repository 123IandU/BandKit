// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.bandburg.core.NativeLib
import com.miband.app.models.DeviceInfo
import com.miband.app.models.DeviceSession
import com.miband.app.models.InstalledApp
import com.miband.app.models.Watchface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

actual class BandBurgManager {

    private lateinit var context: Context
    private var initialized = false
    private val connections = mutableMapOf<Long, Connection>()
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val authComplete = ConcurrentHashMap<Long, CompletableDeferred<Boolean>>()

    private data class Connection(
        val socket: BluetoothSocket,
        val inputStream: InputStream,
        val outputStream: OutputStream,
        val readerThread: Thread,
    )

    fun init(appContext: Context) {
        context = appContext
    }

    actual fun init() {
        if (!initialized) {
            NativeLib.nativeInit()
            initialized = true
            Log.d(TAG, "NativeLib initialized")
        }
    }

    @SuppressLint("MissingPermission")
    actual suspend fun connect(
        name: String,
        addr: String,
        authkey: String,
        connectType: Int,
    ): DeviceSession {
        init()
        check(::context.isInitialized) { "Call init(appContext) first" }

        val handle = NativeLib.nativeCreateSession(name, addr, authkey, connectType)
        Log.d(TAG, "Session created: handle=$handle")

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
            ?: throw IllegalStateException("Bluetooth not available")

        val device: BluetoothDevice = adapter.getRemoteDevice(addr)
            ?: throw IllegalArgumentException("Invalid Bluetooth address: $addr")

        val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val socket = device.createRfcommSocketToServiceRecord(sppUuid)
            ?: throw IllegalStateException("Failed to create Bluetooth socket")

        adapter.cancelDiscovery()
        socket.connect()

        val inputStream = socket.inputStream
        val outputStream = socket.outputStream

        Log.d(TAG, "Bluetooth connected to $addr")

        val session = DeviceSession(
            handle = handle,
            device = com.miband.app.models.SavedDevice(
                id = System.currentTimeMillis().toString(),
                name = name,
                addr = addr,
                authkey = authkey,
                connectType = if (connectType == 1) "BLE" else "SPP",
            ),
        )

        val readerThread = Thread({
            readLoop(session, inputStream)
        }, "BandBurg-Reader-$addr").apply {
            isDaemon = true
            start()
        }

        connections[handle] = Connection(socket, inputStream, outputStream, readerThread)

        performHandshake(session)

        return session
    }

    private suspend fun performHandshake(session: DeviceSession) {
        try {
            val conn = connections[session.handle] ?: return

            val sppHello = NativeLib.nativeBuildSppHello(session.handle)
            if (sppHello.isNotEmpty()) {
                conn.outputStream.write(sppHello)
                conn.outputStream.flush()
                Log.d(TAG, "SPP hello sent (${sppHello.size} bytes)")
                kotlinx.coroutines.delay(100)
            }

            val l1Start = NativeLib.nativeBuildL1StartReq(session.handle)
            conn.outputStream.write(l1Start)
            conn.outputStream.flush()
            Log.d(TAG, "L1StartReq sent (${l1Start.size} bytes)")
            kotlinx.coroutines.delay(200)

            val auth1 = NativeLib.nativeBuildAuthStep1(session.handle)
            conn.outputStream.write(auth1)
            conn.outputStream.flush()
            Log.d(TAG, "AuthStep1 sent (${auth1.size} bytes)")

            val authDeferred = CompletableDeferred<Boolean>()
            authComplete[session.handle] = authDeferred

            try {
                withTimeout(10000L) {
                    authDeferred.await()
                }
                Log.d(TAG, "Auth handshake completed")
            } catch (e: Exception) {
                Log.w(TAG, "Auth handshake timed out, proceeding anyway")
            } finally {
                authComplete.remove(session.handle)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed", e)
        }
    }

    private fun readLoop(session: DeviceSession, inputStream: InputStream) {
        val buffer = ByteArray(4096)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                if (bytesRead == 0) continue

                val data = buffer.copyOf(bytesRead)
                val jsonResult = NativeLib.nativeProcessData(session.handle, data)

                if (jsonResult.isNotBlank() && jsonResult != "[]") {
                    handleReceivedPackets(session.handle, jsonResult)
                }
            }
        } catch (e: Exception) {
            if (!Thread.currentThread().isInterrupted) {
                Log.e(TAG, "Read loop error", e)
            }
        }
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private fun handleReceivedPackets(handle: Long, jsonResult: String) {
        try {
            val array = jsonParser.parseToJsonElement(jsonResult) as? JsonArray ?: return
            for (element in array) {
                val packet = element.jsonObject
                val type = packet["type"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                val id = packet["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                Log.d(TAG, "Packet: type=$type id=$id")

                if (type == 1 && id == 4) {
                    val authDeferred = authComplete[handle]
                    if (authDeferred != null) {
                        val payload = packet["authDeviceVerify"] ?: packet["device_verify"]
                        if (payload != null) {
                            Log.d(TAG, "Received DeviceVerify, sending AppConfirm")
                            try {
                                val deviceVerifyJson = payload.toString()
                                val appConfirmBytes = NativeLib.nativeHandleAuthStep2(handle, deviceVerifyJson, true)
                                val conn = connections[handle]
                                if (conn != null && appConfirmBytes.isNotEmpty()) {
                                    conn.outputStream.write(appConfirmBytes)
                                    conn.outputStream.flush()
                                    Log.d(TAG, "AppConfirm sent (${appConfirmBytes.size} bytes)")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to send AppConfirm", e)
                            }
                            authDeferred.complete(true)
                        }
                    }
                }

                val key = "$type:$id"
                val deferred = pendingResponses.remove(key)
                if (deferred != null) {
                    deferred.complete(jsonResult)
                    Log.d(TAG, "Completed pending response for $key")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: $jsonResult", e)
        }
    }

    actual fun disconnect(session: DeviceSession) {
        val conn = connections.remove(session.handle) ?: return
        conn.readerThread.interrupt()
        try {
            conn.inputStream.close()
        } catch (_: Exception) {}
        try {
            conn.outputStream.close()
        } catch (_: Exception) {}
        try {
            conn.socket.close()
        } catch (_: Exception) {}
        NativeLib.nativeDestroySession(session.handle)
        Log.d(TAG, "Disconnected from ${session.device.addr}")
    }

    actual fun destroySession(session: DeviceSession) {
        disconnect(session)
    }

    actual suspend fun sendCommand(session: DeviceSession, typeId: Int, commandId: Int, payload: ByteArray?): String {
        val conn = connections[session.handle] ?: throw IllegalStateException("Not connected")

        val key = "$typeId:$commandId"
        val deferred = CompletableDeferred<String>()
        pendingResponses[key] = deferred

        return try {
            val pbBytes = ProtobufBuilder.buildWearPacket(typeId, commandId, payload)
            val encoded = NativeLib.nativeSendProtobuf(session.handle, pbBytes)
            conn.outputStream.write(encoded)
            conn.outputStream.flush()

            withTimeout(5000L) {
                deferred.await()
            }
        } catch (e: Exception) {
            pendingResponses.remove(key)
            Log.e(TAG, "sendCommand failed: type=$typeId id=$commandId", e)
            "[]"
        }
    }

    actual suspend fun getDeviceInfo(session: DeviceSession): DeviceInfo = try {
        val infoJson = sendCommand(session, typeId = 2, commandId = 2)
        val statusJson = sendCommand(session, typeId = 2, commandId = 1)
        val storageJson = sendCommand(session, typeId = 2, commandId = 62)
        ResponseParser.parseDeviceInfo(infoJson, statusJson, storageJson, session.device.name)
    } catch (e: Exception) {
        Log.e(TAG, "getDeviceInfo failed", e)
        DeviceInfo(model = session.device.name, serialNumber = session.device.addr)
    }

    actual suspend fun getWatchfaceList(session: DeviceSession): List<Watchface> = try {
        val json = sendCommand(session, typeId = 4, commandId = 0)
        ResponseParser.parseWatchfaceList(json)
    } catch (e: Exception) {
        Log.e(TAG, "getWatchfaceList failed", e)
        emptyList()
    }

    actual suspend fun setWatchface(session: DeviceSession, watchfaceId: String): Boolean {
        val conn = connections[session.handle] ?: return false
        return try {
            val pbBytes = ProtobufBuilder.buildSetWatchface(watchfaceId)
            val encoded = NativeLib.nativeSendProtobuf(session.handle, pbBytes)
            conn.outputStream.write(encoded)
            conn.outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "setWatchface failed", e)
            false
        }
    }

    actual suspend fun uninstallWatchface(session: DeviceSession, watchfaceId: String): Boolean {
        val conn = connections[session.handle] ?: return false
        return try {
            val pbBytes = ProtobufBuilder.buildRemoveWatchface(watchfaceId)
            val encoded = NativeLib.nativeSendProtobuf(session.handle, pbBytes)
            conn.outputStream.write(encoded)
            conn.outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "uninstallWatchface failed", e)
            false
        }
    }

    actual suspend fun getAppList(session: DeviceSession): List<InstalledApp> = try {
        val json = sendCommand(session, typeId = 20, commandId = 0)
        ResponseParser.parseAppList(json)
    } catch (e: Exception) {
        Log.e(TAG, "getAppList failed", e)
        emptyList()
    }

    actual suspend fun launchApp(session: DeviceSession, packageName: String): Boolean {
        val conn = connections[session.handle] ?: return false
        return try {
            val pbBytes = ProtobufBuilder.buildLaunchApp(packageName)
            val encoded = NativeLib.nativeSendProtobuf(session.handle, pbBytes)
            conn.outputStream.write(encoded)
            conn.outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "launchApp failed", e)
            false
        }
    }

    actual suspend fun uninstallApp(session: DeviceSession, packageName: String): Boolean {
        val conn = connections[session.handle] ?: return false
        return try {
            val pbBytes = ProtobufBuilder.buildRemoveApp(packageName)
            val encoded = NativeLib.nativeSendProtobuf(session.handle, pbBytes)
            conn.outputStream.write(encoded)
            conn.outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "uninstallApp failed", e)
            false
        }
    }

    actual fun processReceivedData(session: DeviceSession, data: ByteArray): String = NativeLib.nativeProcessData(session.handle, data)

    actual suspend fun installFile(
        session: DeviceSession,
        fileName: String,
        fileData: ByteArray,
        resType: Int,
        packageName: String?,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val conn = connections[session.handle] ?: return false
        return try {
            Log.d(TAG, "Installing file: $fileName (type=$resType, size=${fileData.size})")
            onProgress(0f)

            val preparePb = when (resType) {
                16 -> {
                    val watchfaceId = packageName ?: fileName.substringBeforeLast(".")
                    ProtobufBuilder.buildWatchfaceInstallRequest(watchfaceId, fileData.size)
                }

                64 -> {
                    ProtobufBuilder.buildThirdPartyAppInstallRequest(
                        packageName ?: "unknown.app",
                        fileData.size,
                    )
                }

                32 -> {
                    val md5 = java.security.MessageDigest.getInstance("MD5").digest(fileData)
                    ProtobufBuilder.buildFirmwareInstallRequest(md5, fileData.size)
                }

                else -> ProtobufBuilder.buildPrepareInstall(resType, fileData.size, packageName)
            }
            val prepareEncoded = NativeLib.nativeSendProtobuf(session.handle, preparePb)
            conn.outputStream.write(prepareEncoded)
            conn.outputStream.flush()

            val prepareKey = when (resType) {
                16 -> "4:7"
                64 -> "20:3"
                32 -> "2:4"
                else -> "19:1"
            }
            val deferred = CompletableDeferred<String>()
            pendingResponses[prepareKey] = deferred
            try {
                withTimeout(10000L) { deferred.await() }
                Log.d(TAG, "Prepare response received for type=$resType")
            } catch (e: Exception) {
                pendingResponses.remove(prepareKey)
                Log.w(TAG, "Prepare response timeout for type=$resType, continuing anyway")
            }

            val md5 = java.security.MessageDigest.getInstance("MD5").digest(fileData)
            val massHeader = ByteArray(22)
            massHeader[0] = 0x00
            massHeader[1] = resType.toByte()
            System.arraycopy(md5, 0, massHeader, 2, 16)
            massHeader[18] = (fileData.size and 0xFF).toByte()
            massHeader[19] = ((fileData.size shr 8) and 0xFF).toByte()
            massHeader[20] = ((fileData.size shr 16) and 0xFF).toByte()
            massHeader[21] = ((fileData.size shr 24) and 0xFF).toByte()
            val massPayload = massHeader + fileData

            val crc32 = java.util.zip.CRC32()
            crc32.update(massPayload)
            val crcVal = crc32.value
            val crcBytes = ByteArray(4)
            crcBytes[0] = (crcVal and 0xFF).toByte()
            crcBytes[1] = ((crcVal shr 8) and 0xFF).toByte()
            crcBytes[2] = ((crcVal shr 16) and 0xFF).toByte()
            crcBytes[3] = ((crcVal shr 24) and 0xFF).toByte()
            val fullPayload = massPayload + crcBytes

            val chunkSize = 666
            val totalChunks = (fullPayload.size + chunkSize - 1) / chunkSize
            for (i in 0 until totalChunks) {
                val offset = i * chunkSize
                val end = minOf(offset + chunkSize, fullPayload.size)
                val chunk = fullPayload.copyOfRange(offset, end)

                val dataPb = ProtobufBuilder.buildInstallData(chunk, offset)
                val dataEncoded = NativeLib.nativeSendProtobuf(session.handle, dataPb)
                conn.outputStream.write(dataEncoded)
                conn.outputStream.flush()

                onProgress((i + 1).toFloat() / totalChunks)

                if (i < totalChunks - 1) {
                    kotlinx.coroutines.delay(10)
                }
            }

            onProgress(1f)
            Log.d(TAG, "File install completed: $fileName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "installFile failed: $fileName", e)
            false
        }
    }

    companion object {
        private const val TAG = "BandBurgManager"
    }
}

actual fun createBandBurgManager(): BandBurgManager = BandBurgManager()

actual fun initBandBurgContext(manager: BandBurgManager, context: Any) {
    manager.init(context as android.content.Context)
}
