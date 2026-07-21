// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

data class MiHttpResponse(
    val statusCode: Int,
    val body: String,
    val cookies: Map<String, String> = emptyMap(),
)

expect object MiClient {
    fun get(url: String, headers: Map<String, String> = emptyMap(), cookies: Map<String, String> = emptyMap()): MiHttpResponse
    fun post(url: String, data: Map<String, String> = emptyMap(), headers: Map<String, String> = emptyMap(), cookies: Map<String, String> = emptyMap()): MiHttpResponse
}
