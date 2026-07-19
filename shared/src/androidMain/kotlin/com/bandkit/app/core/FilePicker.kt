// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import android.content.Context
import android.net.Uri
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object FilePickerState {
    var pendingResult: ((PickedFile?) -> Unit)? = null
}

actual fun createFilePicker(): Any = Unit

actual suspend fun pickFileFromPicker(picker: Any): PickedFile? = suspendCoroutine { cont ->
    FilePickerState.pendingResult = { file ->
        cont.resume(file)
    }

    val launcher = DeviceExportImportState.filePickerLauncher
    if (launcher == null) {
        FilePickerState.pendingResult = null
        cont.resume(null)
        return@suspendCoroutine
    }

    try {
        launcher("*/*")
    } catch (e: Exception) {
        FilePickerState.pendingResult = null
        cont.resume(null)
    }
}

fun handleFilePickerResult(context: Context, uri: Uri?) {
    val callback = FilePickerState.pendingResult ?: return
    FilePickerState.pendingResult = null

    if (uri == null) {
        callback(null)
        return
    }

    try {
        val contentResolver = context.contentResolver
        val fileName = run {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && it.moveToFirst()) {
                    it.getString(nameIndex)
                } else {
                    null
                }
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "unknown"
        }
        val inputStream = contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()

        if (bytes != null) {
            callback(PickedFile(fileName, bytes))
        } else {
            callback(null)
        }
    } catch (e: Exception) {
        callback(null)
    }
}
