// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PlatformContextProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    CompositionLocalProvider(LocalPlatformContext provides context) {
        content()
    }
}
