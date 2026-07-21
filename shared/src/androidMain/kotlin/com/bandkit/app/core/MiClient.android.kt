// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

actual object MiClient {
    actual fun get(
        url: String,
        headers: Map<String, String>,
        cookies: Map<String, String>,
    ): MiHttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        return executeRequest(conn, headers, cookies, null)
    }

    actual fun post(
        url: String,
        data: Map<String, String>,
        headers: Map<String, String>,
        cookies: Map<String, String>,
    ): MiHttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        conn.requestMethod = "POST"
        if (data.isNotEmpty()) {
            val body = data.entries.joinToString("&") { "${it.key}=${it.value}" }
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }
        }
        return executeRequest(conn, headers, cookies, null)
    }

    private fun executeRequest(
        conn: HttpURLConnection,
        headers: Map<String, String>,
        cookies: Map<String, String>,
        body: String?,
    ): MiHttpResponse {
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (cookies.isNotEmpty()) {
            conn.setRequestProperty(
                "Cookie",
                cookies.entries.joinToString("; ") { "${it.key}=${it.value}" },
            )
        }
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000

        val statusCode = conn.responseCode
        val bodyText =
            try {
                conn.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

        val responseCookies =
            conn.headerFields?.entries
                ?.filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                ?.flatMap { it.value }
                ?.mapNotNull { line ->
                    val parts = line.split(";")[0].split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
                ?.toMap() ?: emptyMap()

        conn.disconnect()
        return MiHttpResponse(
            statusCode = statusCode,
            body = bodyText,
            cookies = responseCookies,
        )
    }
}
