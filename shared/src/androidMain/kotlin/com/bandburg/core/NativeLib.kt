// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandburg.core

/**
 * JNI bridge to the Rust corelib-standalone library.
 * Handles MiWear protocol encoding/decoding, authentication, and packet framing.
 */
object NativeLib {
    init {
        System.loadLibrary("corelib_standalone")
    }

    /** Initialize the session manager. Must be called once at app startup. */
    external fun nativeInit()

    /**
     * Create a new device session.
     * @param name Device display name
     * @param addr Bluetooth MAC address
     * @param authkey 32-char hex authentication key
     * @param connectType 0=SPP, 1=BLE
     * @return Native session handle (pointer)
     */
    external fun nativeCreateSession(
        name: String,
        addr: String,
        authkey: String,
        connectType: Int,
    ): Long

    /** Destroy a device session and release native resources. */
    external fun nativeDestroySession(handle: Long)

    /**
     * Build the SPP hello handshake packet.
     * Only non-empty for SPP connections.
     */
    external fun nativeBuildSppHello(handle: Long): ByteArray

    /**
     * Build the L1StartReq command packet (initiates transport handshake).
     */
    external fun nativeBuildL1StartReq(handle: Long): ByteArray

    /**
     * Build the auth step 1 packet (sends app random nonce).
     * The returned bytes are L2-encoded and ready to send over SPP/BLE.
     */
    external fun nativeBuildAuthStep1(handle: Long): ByteArray

    /**
     * Process raw data received from the device.
     * Handles L1 framing, L2 decoding, and PB deserialization.
     * @return JSON array of decoded WearPacket objects
     */
    external fun nativeProcessData(handle: Long, data: ByteArray): String

    /**
     * Handle auth step 2: process device verify response and build confirmation.
     * @param deviceVerifyJson JSON-encoded DeviceVerify protobuf
     * @param forceAndroid Force Android device type (false = iOS for BLE)
     * @return L2-encoded auth confirmation packet bytes
     */
    external fun nativeHandleAuthStep2(
        handle: Long,
        deviceVerifyJson: String,
        forceAndroid: Boolean,
    ): ByteArray

    /**
     * Encode a WearPacket protobuf and L2-encode it for transmission.
     * @param pbData Serialized protobuf bytes
     * @return L2-encoded packet bytes ready for SPP/BLE send
     */
    external fun nativeSendProtobuf(handle: Long, pbData: ByteArray): ByteArray

    /** Get the number of active sessions. */
    external fun nativeGetSessionCount(): Long
}
