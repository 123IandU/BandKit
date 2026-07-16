// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app

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
import com.miband.app.core.handleDeviceExportResult
import com.miband.app.core.handleDeviceImportResult
import com.miband.app.core.handleFilePickerResult

class MainActivity : ComponentActivity() {
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d(TAG, "Bluetooth permissions result: $allGranted $permissions")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        requestBluetoothPermissions()

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

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                9999 -> data?.data?.let { handleFilePickerResult(this, it) }
                8888 -> data?.data?.let { handleDeviceExportResult(this, it) }
                8889 -> data?.data?.let { handleDeviceImportResult(this, it) }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
