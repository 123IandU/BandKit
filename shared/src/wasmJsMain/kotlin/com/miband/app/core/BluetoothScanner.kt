// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

actual class BluetoothScanner {
    actual fun init(context: Any) {}
    actual fun startScan(onDeviceFound: (ScannedDevice) -> Unit, onScanComplete: () -> Unit) {
        onScanComplete()
    }
    actual fun stopScan() {}
}

actual fun createBluetoothScanner(): BluetoothScanner = BluetoothScanner()
