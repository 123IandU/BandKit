// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

data class PickedFile(
    val name: String,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedFile) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()
}

expect fun createFilePicker(): Any

expect suspend fun pickFileFromPicker(picker: Any): PickedFile?
