// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.models

import kotlinx.serialization.Serializable

@Serializable
data class SavedDevice(
    val id: String,
    val name: String,
    val addr: String,
    val authkey: String,
    val sarVersion: Int = 2,
    val connectType: String = "SPP",
)

data class DeviceInfo(
    val model: String = "-",
    val firmwareVersion: String = "-",
    val serialNumber: String = "-",
    val batteryPercent: Int = 0,
    val totalStorage: String = "-",
    val usedStorage: String = "-",
    val freeStorage: String = "-",
)

data class Watchface(
    val id: String,
    val name: String,
    val isCurrent: Boolean = false,
)

data class InstalledApp(
    val packageName: String,
    val name: String,
)

data class LogEntry(
    val timestamp: Long,
    val message: String,
    val type: LogType = LogType.INFO,
)

enum class LogType { INFO, SUCCESS, ERROR, WARNING }

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

enum class ConnectType(val value: Int) {
    SPP(0),
    BLE(1),
}

data class DeviceSession(
    val handle: Long,
    val device: SavedDevice,
)
