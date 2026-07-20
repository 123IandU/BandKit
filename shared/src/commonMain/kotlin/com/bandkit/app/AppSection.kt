// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandkit.app.core.BandBurgManager
import com.bandkit.app.core.IO
import com.bandkit.app.models.DeviceSession
import com.bandkit.app.models.InstalledApp
import com.bandkit.app.models.LogType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ─── AppSection ───
@Composable
internal fun AppSection(
    apps: List<InstalledApp>,
    session: DeviceSession?,
    manager: BandBurgManager,
    addLog: (String, LogType) -> Unit,
    onUpdate: (List<InstalledApp>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("应用列表", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (isLoading) {
                    InfiniteProgressIndicator(modifier = Modifier.padding(8.dp))
                } else {
                    IconButton(
                        onClick = {
                            session?.let { s ->
                                scope.launch {
                                    isLoading = true
                                    addLog("加载应用...", LogType.INFO)
                                    try {
                                        val r = withContext(IO) { manager.getAppList(s) }
                                        onUpdate(r)
                                        addLog("已加载 ${r.size} 个应用", LogType.SUCCESS)
                                    } catch (e: Exception) {
                                        addLog("失败: ${e.message}", LogType.ERROR)
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        enabled = session != null,
                    ) {
                        Icon(imageVector = MiuixIcons.Refresh, contentDescription = "刷新")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (apps.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("未连接到设备或没有应用数据", fontSize = 14.sp)
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(apps.size) { index ->
                            val app = apps[index]
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(app.packageName, fontSize = 12.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            val s = session ?: return@IconButton
                                            scope.launch { withContext(IO) { manager.launchApp(s, app.packageName) } }
                                        },
                                    ) {
                                        Icon(imageVector = MiuixIcons.Play, contentDescription = "启动")
                                    }
                                    IconButton(
                                        onClick = {
                                            val s = session ?: return@IconButton
                                            scope.launch {
                                                withContext(IO) { manager.uninstallApp(s, app.packageName) }
                                                onUpdate(apps.filter { it.packageName != app.packageName })
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = "删除",
                                            tint = MiuixTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                            if (index < apps.lastIndex) {
                                SimpleDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
