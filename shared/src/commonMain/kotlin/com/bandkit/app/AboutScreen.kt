// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandkit.app.core.LocalPlatformContext
import com.bandkit.app.core.loadShowLogs
import com.bandkit.app.core.saveShowLogs
import com.bandkit.app.core.showToast
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "关于",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val context = LocalPlatformContext.current
        var tapCount by remember { mutableIntStateOf(0) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
        ) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "BandKit",
                        color = MiuixTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        modifier = Modifier.clickable {
                            tapCount++
                            val remaining = 5 - tapCount
                            when {
                                remaining > 0 -> showToast(context, "再点 $remaining 次显示操作日志")

                                tapCount == 5 -> {
                                    val newValue = !loadShowLogs(context)
                                    saveShowLogs(context, newValue)
                                    showToast(context, if (newValue) "操作日志已显示" else "操作日志已隐藏")
                                    tapCount = 0
                                }
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "v${AppBuildConfig.VERSION_NAME} (${AppBuildConfig.VERSION_CODE})",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SmallTitle(text = "应用信息")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    BasicComponent(
                        title = "应用名称",
                        summary = AppBuildConfig.APPLICATION_NAME,
                    )
                    BasicComponent(
                        title = "版本号",
                        summary = AppBuildConfig.VERSION_NAME,
                    )
                    BasicComponent(
                        title = "版本代码",
                        summary = "${AppBuildConfig.VERSION_CODE}",
                    )
                    BasicComponent(
                        title = "包名",
                        summary = AppBuildConfig.APPLICATION_ID,
                    )
                }
            }
            item {
                SmallTitle(text = "项目")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "BandKit",
                        summary = "github.com/123IandU/BandKit",
                        onClick = { onOpenUrl("https://github.com/123IandU/BandKit") },
                    )
                    ArrowPreference(
                        title = "AstroBox-NG",
                        summary = "github.com/AstralSightStudios/AstroBox-NG",
                        onClick = { onOpenUrl("https://github.com/AstralSightStudios/AstroBox-NG") },
                    )
                    ArrowPreference(
                        title = "Miuix",
                        summary = "github.com/compose-miuix-ui/miuix",
                        onClick = { onOpenUrl("https://github.com/compose-miuix-ui/miuix") },
                    )
                }
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
