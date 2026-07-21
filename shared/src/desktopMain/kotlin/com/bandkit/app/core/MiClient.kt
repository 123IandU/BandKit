// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

actual object MiClient {
    actual fun get(url: String, headers: Map<String, String>, cookies: Map<String, String>): MiHttpResponse = MiHttpResponse(0, "")
    actual fun post(url: String, data: Map<String, String>, headers: Map<String, String>, cookies: Map<String, String>): MiHttpResponse = MiHttpResponse(0, "")
}
