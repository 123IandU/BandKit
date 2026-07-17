// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
import androidx.compose.ui.window.ComposeViewport
import com.bandkit.app.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "composeApp") {
        App()
    }
}
