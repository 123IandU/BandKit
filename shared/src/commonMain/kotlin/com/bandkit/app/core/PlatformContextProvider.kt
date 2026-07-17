// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformContextProvider(content: @Composable () -> Unit)
