// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandkit.app.core.BandBurgManager
import com.bandkit.app.core.IO
import com.bandkit.app.core.PickedFile
import com.bandkit.app.core.detectFileType
import com.bandkit.app.core.extractFileIdentifier
import com.bandkit.app.core.formatFileSize
import com.bandkit.app.core.formatTimestamp
import com.bandkit.app.core.pickFileFromPicker
import com.bandkit.app.core.showToast
import com.bandkit.app.models.DeviceSession
import com.bandkit.app.models.LogEntry
import com.bandkit.app.models.LogType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ─── InstallSection ───
@Composable
internal fun InstallSection(
    session: DeviceSession?,
    manager: BandBurgManager,
    context: Any,
    onLog: (String, LogType) -> Unit,
    onInstallComplete: (resType: Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var selectedFile by remember { mutableStateOf<PickedFile?>(null) }
    var selectedFileId by remember { mutableStateOf<String?>(null) }
    var installProgress by remember { mutableStateOf(-1f) }
    var installMessage by remember { mutableStateOf("") }
    var isInstalling by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("文件安装", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            if (session == null) {
                Text("请先连接设备", fontSize = 14.sp)
            } else {
                Text("选择文件进行安装（支持 .bin / .rpk）", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val file = pickFileFromPicker(context)
                            if (file != null) {
                                selectedFile = file
                                selectedFileId = extractFileIdentifier(file.name, file.data)
                                onLog("已选择: ${file.name} (${formatFileSize(file.data.size)})", LogType.INFO)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("选择文件")
                }
                selectedFile?.let { file ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${file.name} (${formatFileSize(file.data.size)})", fontSize = 13.sp)
                    if (!selectedFileId.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    // 显示提取的包名或表盘 ID
                    if (!selectedFileId.isNullOrBlank()) {
                        val label = if (file.name.endsWith(".rpk", true)) "包名" else "表盘 ID"
                        Text("$label: $selectedFileId", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isInstalling = true
                            installProgress = 0f
                            val resType = detectFileType(file.name, file.data)
                            val resTypeName = when (resType) {
                                64 -> "RPK(第三方应用)"
                                32 -> "FW(固件)"
                                16 -> "BIN(表盘)"
                                else -> "$resType"
                            }
                            onLog("开始安装: ${file.name} (${formatFileSize(file.data.size)}, 类型=$resTypeName)", LogType.INFO)
                            installMessage = "正在安装 ${file.name}..."
                            scope.launch {
                                try {
                                    val result = withContext(IO) {
                                        manager.installFile(
                                            session,
                                            file.name,
                                            file.data,
                                            resType,
                                            null,
                                        ) { progress ->
                                            installProgress = progress
                                            installMessage = "安装 ${file.name}: ${(progress * 100).toInt()}%"
                                        }
                                    }
                                    if (result) {
                                        onLog("文件安装成功: ${file.name}", LogType.SUCCESS)
                                        showToast(context, "安装成功: ${file.name}")
                                        onInstallComplete(resType)
                                    } else {
                                        onLog("文件安装失败: ${file.name}，请查看 Logcat 获取详细错误", LogType.ERROR)
                                        showToast(context, "安装失败: ${file.name}")
                                    }
                                } catch (e: Exception) {
                                    onLog("安装异常: ${e.message}", LogType.ERROR)
                                } finally {
                                    isInstalling = false
                                    installProgress = -1f
                                    installMessage = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isInstalling,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(if (isInstalling) "安装中..." else "开始安装")
                    }
                }
                if (installProgress >= 0f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(installMessage, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = installProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ─── LogSection ───
@OptIn(FlowPreview::class)
@Composable
internal fun LogSection(logs: List<LogEntry>) {
    val listState = rememberLazyListState()
    // 用户是否靠近顶部（前 3 条内），靠近则自动跟随
    val isNearTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex < 3 }
    }
    // 新日志到达时自动滚动到顶部
    LaunchedEffect(Unit) {
        snapshotFlow { logs.size }
            .debounce(300)
            .collect { if (isNearTop) listState.animateScrollToItem(0) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).height(200.dp)) {
            Text("操作日志", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            SimpleDivider()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                items(logs, key = { it.id }) { entry ->
                    val color = when (entry.type) {
                        LogType.SUCCESS -> MiuixTheme.colorScheme.primary
                        LogType.ERROR -> MiuixTheme.colorScheme.error
                        LogType.WARNING -> MiuixTheme.colorScheme.error.copy(alpha = 0.6f)
                        LogType.INFO -> MiuixTheme.colorScheme.onSurface
                    }
                    val time = formatTimestamp(entry.timestamp)
                    Text(
                        "[$time] ${entry.message}",
                        fontSize = 12.sp,
                        color = color,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
