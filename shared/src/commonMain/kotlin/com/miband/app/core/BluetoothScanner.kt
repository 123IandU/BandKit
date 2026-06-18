// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

data class ScannedDevice(val name: String, val address: String, val rssi: Int)

expect class BluetoothScanner {
    fun init(context: Any)
    fun startScan(onDeviceFound: (ScannedDevice) -> Unit, onScanComplete: () -> Unit)
    fun stopScan()
}

expect fun createBluetoothScanner(): BluetoothScanner
