// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("bandburg", MODE_PRIVATE)
        var showLogsState by mutableStateOf(prefs.getBoolean("show_logs", true))

        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.System) }

            MiuixTheme(controller = controller) {
                SettingsScreen(
                    onBack = { finish() },
                    showLogs = showLogsState,
                    onShowLogsChange = {
                        showLogsState = it
                        prefs.edit().putBoolean("show_logs", it).apply()
                    },
                )
            }
        }
    }
}
