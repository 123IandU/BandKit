// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.models

@kotlinx.serialization.Serializable
data class XiaomiDeviceInfo(
    val mac: String = "",
    val name: String = "",
    val authKey: String = "",
)
