// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

import android.content.Context
import com.miband.app.models.SavedDevice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual val IO: CoroutineDispatcher = Dispatchers.IO

actual fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun loadSavedDevices(context: Any): List<SavedDevice> = try {
    val ctx = context as Context
    val prefs = ctx.getSharedPreferences("bandburg", Context.MODE_PRIVATE)
    kotlinx.serialization.json.Json.decodeFromString(prefs.getString("devices", "[]") ?: "[]")
} catch (_: Exception) {
    emptyList()
}

actual fun saveSavedDevices(context: Any, devices: List<SavedDevice>) {
    val ctx = context as Context
    val prefs = ctx.getSharedPreferences("bandburg", Context.MODE_PRIVATE)
    prefs.edit().putString("devices", kotlinx.serialization.json.Json.encodeToString(devices)).apply()
}
