// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.bandkit.app.models.DeviceSession
import top.yukonga.miuix.kmp.basic.Text

@Composable
actual fun PlatformScriptScreen(session: DeviceSession?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "脚本功能仅支持 Android",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
