// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandkit.app.core.ScannedDevice
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

// ─── AddDeviceBottomSheet ───
@Composable
internal fun AddDeviceBottomSheet(
    tab: Int,
    onTabChange: (Int) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    addr: String,
    onAddrChange: (String) -> Unit,
    authkey: String,
    onAuthkeyChange: (String) -> Unit,
    sarVersion: Int,
    onSarVersionChange: (Int) -> Unit,
    connectTypeBle: Boolean,
    onConnectTypeBleChange: (Boolean) -> Unit,
    scannedDevices: List<ScannedDevice>,
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onDeviceSelected: (ScannedDevice) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        title = "添加设备",
        onDismissRequest = onDismiss,
    ) {
        TabRowWithContour(
            tabs = listOf("直接添加", "扫描附近设备"),
            selectedTabIndex = tab,
            onTabSelected = onTabChange,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
            if (tab == 0) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = "设备名称 *",
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = addr,
                        onValueChange = onAddrChange,
                        label = "设备地址*",
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = authkey,
                        onValueChange = onAuthkeyChange,
                        label = "认证密钥 *",
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OverlayDropdownPreference(
                        title = "SAR 版本",
                        items = listOf("SAR v1", "SAR v2"),
                        selectedIndex = sarVersion,
                        onSelectedIndexChange = onSarVersionChange,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OverlayDropdownPreference(
                        title = "连接类型",
                        items = listOf("SPP", "BLE"),
                        selectedIndex = if (connectTypeBle) 1 else 0,
                        onSelectedIndexChange = { onConnectTypeBleChange(it == 1) },
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("附近蓝牙设备", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (isScanning) {
                            InfiniteProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        } else {
                            Text(
                                "刷新",
                                fontSize = 14.sp,
                                modifier = Modifier.clickable { onStartScan() },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (scannedDevices.isEmpty() && !isScanning) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("点击 开始扫描 搜索附近设备", fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        ) {
                            items(scannedDevices) { dev ->
                                BasicComponent(
                                    title = dev.name,
                                    summary = dev.address,
                                    endActions = {
                                        Text("RSSI: ${dev.rssi}", fontSize = 12.sp)
                                    },
                                    onClick = { onDeviceSelected(dev) },
                                )
                            }
                        }
                        if (isScanning) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("扫描中... (${scannedDevices.size} 个设备)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("添加设备")
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
