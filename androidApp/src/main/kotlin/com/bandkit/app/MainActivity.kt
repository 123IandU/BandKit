// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bandkit.app.core.DeviceExportImportState
import com.bandkit.app.core.handleDeviceExportResult
import com.bandkit.app.core.handleDeviceImportResult
import com.bandkit.app.core.handleFilePickerResult

class MainActivity : ComponentActivity() {

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d(TAG, "Bluetooth permissions result: $allGranted $permissions")
    }

    private val importDeviceLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            handleDeviceImportResult(this, uri)
        } else {
            val callback = DeviceExportImportState.pendingImportResult ?: return@registerForActivityResult
            DeviceExportImportState.pendingImportResult = null
            callback(null)
        }
    }

    private val exportDeviceLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            handleDeviceExportResult(this, uri)
        } else {
            val callback = DeviceExportImportState.pendingExportResult ?: return@registerForActivityResult
            DeviceExportImportState.pendingExportResult = null
            callback(false)
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) handleFilePickerResult(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        requestBluetoothPermissions()

        // Expose launchers so KMP code can use them
        DeviceExportImportState.importLauncher = { type ->
            importDeviceLauncher.launch(arrayOf(type))
        }
        DeviceExportImportState.exportLauncher = { type, title ->
            exportDeviceLauncher.launch(title)
        }
        DeviceExportImportState.filePickerLauncher = { type ->
            filePickerLauncher.launch(arrayOf(type))
        }

        setContent {
            App()
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = listOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                Log.d(TAG, "Requesting Bluetooth permissions: $needed")
                bluetoothPermissionLauncher.launch(needed.toTypedArray())
            } else {
                Log.d(TAG, "Bluetooth permissions already granted")
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
