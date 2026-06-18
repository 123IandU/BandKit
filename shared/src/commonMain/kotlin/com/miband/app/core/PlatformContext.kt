// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app.core

import androidx.compose.runtime.compositionLocalOf

val LocalPlatformContext = compositionLocalOf<Any> { error("No platform context provided") }
