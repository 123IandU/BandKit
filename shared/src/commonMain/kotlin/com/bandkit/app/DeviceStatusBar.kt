// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandkit.app.core.parseStoragePercent
import com.bandkit.app.models.ConnectionStatus
import com.bandkit.app.models.DeviceInfo
import com.bandkit.app.models.SavedDevice
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SimpleDivider() {
    Box(
        modifier = Modifier.fillMaxWidth().height(1.dp)
            .background(Color.LightGray.copy(alpha = 0.3f)),
    )
}

// ─── DeviceStatusBar ───
@Composable
internal fun DeviceStatusBar(
    status: ConnectionStatus,
    info: DeviceInfo,
    session: com.bandkit.app.models.DeviceSession?,
    onDeviceNameClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Spacer(modifier = Modifier.weight(2f))
            Column(modifier = Modifier.weight(3f)) {
                val title = when (status) {
                    ConnectionStatus.CONNECTED -> session?.device?.name ?: "已连接"
                    ConnectionStatus.CONNECTING -> "正在连接..."
                    ConnectionStatus.DISCONNECTED -> "暂未连接设备"
                }
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onDeviceNameClick() },
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = info.batteryPercent / 100f,
                        size = 24.dp,
                        strokeWidth = 3.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${info.batteryPercent}%", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                val usedPercent = parseStoragePercent(info.usedStorage, info.totalStorage)
                Column {
                    LinearProgressIndicator(
                        progress = usedPercent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${info.usedStorage} / ${info.totalStorage}", fontSize = 12.sp)
                }
            }
        }
    }
}

// ─── SavedDevicesBottomSheet ───
@Composable
internal fun SavedDevicesBottomSheet(
    savedDevices: List<SavedDevice>,
    currentSession: com.bandkit.app.models.DeviceSession?,
    onConnect: (SavedDevice) -> Unit,
    onEdit: (SavedDevice) -> Unit,
    onDelete: (SavedDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        title = "已保存设备",
        onDismissRequest = onDismiss,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
            if (savedDevices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无已保存设备", fontSize = 14.sp, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(savedDevices, key = { it.id }) { device ->
                        val isCurrent = currentSession?.device?.id == device.id
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onConnect(device) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    device.name + if (isCurrent) " [当前]" else "",
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text("${device.addr} · ${device.connectType}", fontSize = 12.sp)
                            }
                            IconButton(onClick = { onEdit(device) }) {
                                Icon(imageVector = MiuixIcons.Edit, contentDescription = "编辑", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onDelete(device) }) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(20.dp),
                                    tint = MiuixTheme.colorScheme.error,
                                )
                            }
                        }
                        SimpleDivider()
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}
