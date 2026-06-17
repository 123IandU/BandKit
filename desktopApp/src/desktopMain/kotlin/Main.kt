// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.miband.app.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Miband",
    ) {
        App()
    }
}
