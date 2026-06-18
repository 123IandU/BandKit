// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

import com.miband.app.models.SavedDevice
import kotlinx.coroutines.CoroutineDispatcher

expect val IO: CoroutineDispatcher

expect fun formatTimestamp(timestamp: Long): String

expect fun currentTimeMillis(): Long

expect fun loadSavedDevices(context: Any): List<SavedDevice>

expect fun saveSavedDevices(context: Any, devices: List<SavedDevice>)
