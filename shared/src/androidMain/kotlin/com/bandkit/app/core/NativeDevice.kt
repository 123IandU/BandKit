// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

object NativeDevice {
    init {
        System.loadLibrary("bandkit_app_android")
    }

    // ======== 事件回调 ========
    external fun registerEventSink(callback: (String, String) -> Unit)
    external fun registerThirdpartyAppMessageCallback(callback: (String) -> Unit)
    external fun registerPbPacketCallback(callback: (String) -> Unit)

    // ======== 设备连接 ========
    external fun deviceConnect(
        name: String,
        addr: String,
        authkey: String,
        sarVersion: Long,
        connectType: String,
        txWinOverrunAllowance: ByteArray,
    ): String

    external fun deviceDisconnect(addr: String): Boolean
    external fun deviceGetConnectedDevices(): String

    // ======== 设备数据 ========
    external fun deviceGetData(addr: String, dataType: String): String
    external fun deviceInstall(
        addr: String,
        resType: ByteArray,
        data: ByteArray,
        packageName: String?,
        progressCb: Any?,
        watchfaceId: String?,
    ): Boolean
    external fun deviceGetFileType(file: ByteArray, name: String): Byte

    // ======== 表盘 ========
    external fun watchfaceGetList(addr: String): String
    external fun watchfaceSetCurrent(addr: String, watchfaceId: String): Boolean
    external fun watchfaceUninstall(addr: String, watchfaceId: String): Boolean

    // ======== 第三方应用 ========
    external fun thirdpartyappGetList(addr: String): String
    external fun thirdpartyappSendMessage(addr: String, packageName: String, data: String): Boolean
    external fun thirdpartyappLaunch(addr: String, packageName: String, page: String): Boolean
    external fun thirdpartyappUninstall(addr: String, packageName: String): Boolean
}
