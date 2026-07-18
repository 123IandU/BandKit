// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.models

import com.bandkit.app.core.currentTimeMillis
import kotlinx.serialization.Serializable

@Serializable
data class ScriptDoc(
    val id: String,
    val name: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        fun create(name: String, content: String = ""): ScriptDoc {
            val now = currentTimeMillis()
            return ScriptDoc(
                id = "script_${now}_${(0..9999).random()}",
                name = name,
                content = content,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
