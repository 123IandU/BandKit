// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object FilePickerState {
    var pendingResult: ((PickedFile?) -> Unit)? = null
}

actual fun createFilePicker(): Any = Unit

actual suspend fun pickFileFromPicker(picker: Any): PickedFile? = suspendCoroutine { cont ->
    val context = picker as? Context ?: run {
        cont.resume(null)
        return@suspendCoroutine
    }

    FilePickerState.pendingResult = { file ->
        cont.resume(file)
    }

    try {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val activity = context as? Activity
        if (activity == null) {
            cont.resume(null)
            return@suspendCoroutine
        }
        @Suppress("DEPRECATION")
        activity.startActivityForResult(Intent.createChooser(intent, "选择文件"), 9999)
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
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "unknown"
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
