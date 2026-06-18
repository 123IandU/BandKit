// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

actual class BluetoothScanner {

    private var context: android.content.Context? = null
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var isScanning = false

    actual fun init(context: Any) {
        this.context = context as? android.content.Context
    }

    @android.annotation.SuppressLint("MissingPermission")
    actual fun startScan(onDeviceFound: (ScannedDevice) -> Unit, onScanComplete: () -> Unit) {
        val ctx = context
        if (ctx == null) {
            android.util.Log.e(TAG, "Context not initialized")
            onScanComplete()
            return
        }

        // Check permissions first
        if (!hasPermissions(ctx)) {
            android.util.Log.e(TAG, "Bluetooth permissions not granted")
            onScanComplete()
            return
        }

        try {
            val bluetoothManager = ctx.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adapter = bluetoothManager?.adapter
            if (adapter == null || !adapter.isEnabled) {
                android.util.Log.e(TAG, "Bluetooth adapter not available or disabled")
                onScanComplete()
                return
            }

            scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                android.util.Log.e(TAG, "BLE scanner not available")
                onScanComplete()
                return
            }

            isScanning = true
            android.util.Log.d(TAG, "Starting BLE scan...")

            scanner?.startScan(object : android.bluetooth.le.ScanCallback() {
                override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                    val device = result.device
                    val name = device.name ?: device.alias ?: "未知设备"
                    onDeviceFound(ScannedDevice(name, device.address, result.rssi))
                }

                override fun onBatchScanResults(results: MutableList<android.bluetooth.le.ScanResult>) {
                    results.forEach { r ->
                        onDeviceFound(ScannedDevice(r.device.name ?: r.device.alias ?: "未知设备", r.device.address, r.rssi))
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    android.util.Log.e(TAG, "Scan failed: $errorCode")
                    isScanning = false
                    onScanComplete()
                }
            })
        } catch (e: SecurityException) {
            android.util.Log.e(TAG, "Bluetooth permission denied", e)
            isScanning = false
            onScanComplete()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Scan error: ${e.message}", e)
            isScanning = false
            onScanComplete()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    actual fun stopScan() {
        if (isScanning) {
            @Suppress("DEPRECATION")
            scanner?.stopScan(null as android.bluetooth.le.ScanCallback?)
            isScanning = false
            android.util.Log.d(TAG, "Scan stopped")
        }
    }

    private fun hasPermissions(ctx: android.content.Context): Boolean = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

actual fun createBluetoothScanner(): BluetoothScanner = BluetoothScanner()

private const val TAG = "BluetoothScanner"
