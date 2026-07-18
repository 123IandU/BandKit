// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.bandkit.app.models.DeviceSession
import com.bandkit.app.models.SavedDevice
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

class ScriptRunnerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val scriptCode = intent.getStringExtra(EXTRA_SCRIPT_CODE) ?: ""
        val deviceAddr = intent.getStringExtra(EXTRA_DEVICE_ADDR) ?: ""
        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: ""
        val authKey = intent.getStringExtra(EXTRA_AUTH_KEY) ?: ""

        val session = if (deviceAddr.isNotEmpty()) {
            DeviceSession(
                handle = 0L,
                device = SavedDevice(
                    id = "",
                    name = deviceName,
                    addr = deviceAddr,
                    authkey = authKey,
                    sarVersion = 2,
                    connectType = "SPP",
                ),
            )
        } else {
            null
        }

        setContent {
            Scaffold(
                topBar = {
                    SmallTopAppBar(
                        title = "脚本运行器",
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = "返回",
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    ScriptRunnerContent(
                        scriptCode = scriptCode,
                        session = session,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
