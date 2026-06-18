// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

/**
 * Minimal protobuf wire-format encoder for MiWear WearPacket commands.
 * Constructs protobuf bytes that nativeSendProtobuf can L2-encode and transmit.
 */
object ProtobufBuilder {

    private class ByteArrayBuilder {
        private val buffer = mutableListOf<Byte>()

        fun writeByte(value: Int) {
            buffer.add((value and 0xFF).toByte())
        }

        fun writeBytes(data: ByteArray) {
            for (b in data) buffer.add(b)
        }

        fun toByteArray(): ByteArray = buffer.toByteArray()
    }

    private fun writeVarint(value: Int, out: ByteArrayBuilder) {
        var v = value
        while (v > 0x7F) {
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.writeByte(v and 0x7F)
    }

    private fun writeFieldVarint(fieldNumber: Int, value: Int, out: ByteArrayBuilder) {
        writeVarint((fieldNumber shl 3) or 0, out)
        writeVarint(value, out)
    }

    private fun writeFieldBytes(fieldNumber: Int, data: ByteArray, out: ByteArrayBuilder) {
        writeVarint((fieldNumber shl 3) or 2, out)
        writeVarint(data.size, out)
        out.writeBytes(data)
    }

    private fun writeFieldNested(fieldNumber: Int, messageBytes: ByteArray, out: ByteArrayBuilder) {
        writeFieldBytes(fieldNumber, messageBytes, out)
    }

    /**
     * Build a WearPacket protobuf.
     * @param typeId WearPacket.Type enum value (1=ACCOUNT, 2=SYSTEM, 4=WATCH_FACE, 20=THIRDPARTY_APP)
     * @param commandId Command-specific ID
     * @param payloadBytes Optional nested payload message bytes
     */
    fun buildWearPacket(typeId: Int, commandId: Int, payloadBytes: ByteArray? = null): ByteArray {
        val out = ByteArrayBuilder()

        writeFieldVarint(1, typeId, out)
        writeFieldVarint(2, commandId, out)
        if (payloadBytes != null) {
            writeFieldBytes(3, payloadBytes, out)
        }
        return out.toByteArray()
    }

    // === System commands ===

    /** Build GET_DEVICE_INFO request (System type=2, id=2) */
    fun buildGetDeviceInfo(): ByteArray = buildWearPacket(typeId = 2, commandId = 2)

    /** Build GET_DEVICE_STATUS request (System type=2, id=1) */
    fun buildGetDeviceStatus(): ByteArray = buildWearPacket(typeId = 2, commandId = 1)

    /** Build GET_STORAGE_INFO request (System type=2, id=62) */
    fun buildGetStorageInfo(): ByteArray = buildWearPacket(typeId = 2, commandId = 62)

    // === WatchFace commands ===

    /** Build GET_INSTALLED_LIST for watchfaces (WatchFace type=4, id=0) */
    fun buildGetWatchfaceList(): ByteArray = buildWearPacket(typeId = 4, commandId = 0)

    /** Build SET_WATCH_FACE request (WatchFace type=4, id=1) */
    fun buildSetWatchface(watchfaceId: String): ByteArray {
        val payload = buildStringMessage(2, watchfaceId)
        return buildWearPacket(typeId = 4, commandId = 1, payloadBytes = payload)
    }

    /** Build REMOVE_WATCH_FACE request (WatchFace type=4, id=2) */
    fun buildRemoveWatchface(watchfaceId: String): ByteArray {
        val payload = buildStringMessage(2, watchfaceId)
        return buildWearPacket(typeId = 4, commandId = 2, payloadBytes = payload)
    }

    // === ThirdpartyApp commands ===

    /** Build GET_INSTALLED_LIST for apps (ThirdpartyApp type=20, id=0) */
    fun buildGetAppList(): ByteArray = buildWearPacket(typeId = 20, commandId = 0)

    /** Build LAUNCH_APP request (ThirdpartyApp type=20, id=4) */
    fun buildLaunchApp(packageName: String): ByteArray {
        val launchInfo = buildLaunchInfo(packageName)
        val payload = buildFieldMessage(6, launchInfo)
        return buildWearPacket(typeId = 20, commandId = 4, payloadBytes = payload)
    }

    /** Build REMOVE_APP request (ThirdpartyApp type=20, id=3) */
    fun buildRemoveApp(packageName: String): ByteArray {
        val payload = buildStringMessage(1, packageName)
        return buildWearPacket(typeId = 20, commandId = 3, payloadBytes = payload)
    }

    // === File installation (Mass type=19) ===

    /** Build prepare install request (Mass type=19, id=1) */
    fun buildPrepareInstall(resType: Int, fileSize: Int, packageName: String?): ByteArray {
        val out = ByteArrayBuilder()
        writeFieldVarint(1, resType, out)
        writeFieldVarint(2, fileSize, out)
        if (packageName != null) {
            writeFieldBytes(3, packageName.encodeToByteArray(), out)
        }
        return buildWearPacket(typeId = 19, commandId = 1, payloadBytes = out.toByteArray())
    }

    /** Build install data chunk (Mass type=19, id=2) */
    fun buildInstallData(data: ByteArray, offset: Int): ByteArray {
        val out = ByteArrayBuilder()
        writeFieldVarint(1, offset, out)
        writeFieldBytes(2, data, out)
        return buildWearPacket(typeId = 19, commandId = 2, payloadBytes = out.toByteArray())
    }

    /** Build finish install request (Mass type=19, id=3) */
    fun buildFinishInstall(): ByteArray = buildWearPacket(typeId = 19, commandId = 3)

    // === Helper builders ===

    /** Build a simple string field message */
    private fun buildStringMessage(fieldNumber: Int, value: String): ByteArray {
        val out = ByteArrayBuilder()
        writeFieldBytes(fieldNumber, value.encodeToByteArray(), out)
        return out.toByteArray()
    }

    /** Build a nested message as a field */
    private fun buildFieldMessage(fieldNumber: Int, messageBytes: ByteArray): ByteArray {
        val out = ByteArrayBuilder()
        writeFieldNested(fieldNumber, messageBytes, out)
        return out.toByteArray()
    }

    /** Build LaunchInfo protobuf (field 1=package_name, field 2=page) */
    private fun buildLaunchInfo(packageName: String): ByteArray {
        val out = ByteArrayBuilder()
        writeFieldBytes(1, packageName.encodeToByteArray(), out)
        writeFieldBytes(2, ByteArray(0), out)
        return out.toByteArray()
    }
}
