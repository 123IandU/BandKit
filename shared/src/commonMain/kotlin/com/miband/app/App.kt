// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun App(
    modifier: Modifier = Modifier,
) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    var switchState by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0.5f) }

    MiuixTheme(controller = controller) {
        Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Text(
                    text = "Hello Miuix",
                    fontSize = 28.sp,
                )

                Spacer(modifier = Modifier.height(32.dp))

                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Settings", fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Dark mode", fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Switch(
                            checked = switchState,
                            onCheckedChange = { switchState = it },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                        )
                        Text(text = "Volume: ${(sliderValue * 100).toInt()}%", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
