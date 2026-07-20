// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.preview

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandkit.app.core.formatFileSize
import com.bandkit.app.models.ConnectionStatus
import com.bandkit.app.models.DeviceInfo
import com.bandkit.app.models.InstalledApp
import com.bandkit.app.models.LogEntry
import com.bandkit.app.models.LogType
import com.bandkit.app.models.Watchface
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

// ─── DeviceStatusBar Preview ───
@Preview(name = "DeviceStatusBar - Disconnected", showBackground = true)
@Composable
private fun PreviewDeviceStatusBarDisconnected() {
    val controller = remember { ThemeController(ColorSchemeMode.Light) }
    MiuixTheme(controller = controller) {
        Scaffold {
            Box(modifier = Modifier.padding(16.dp)) {
                PreviewDeviceStatusBar(
                    status = ConnectionStatus.DISCONNECTED,
                    info = DeviceInfo(),
                )
            }
        }
    }
}

@Preview(name = "DeviceStatusBar - Connected", showBackground = true)
@Composable
private fun PreviewDeviceStatusBarConnected() {
    val controller = remember { ThemeController(ColorSchemeMode.Light) }
    MiuixTheme(controller = controller) {
        Scaffold {
            Box(modifier = Modifier.padding(16.dp)) {
                PreviewDeviceStatusBar(
                    status = ConnectionStatus.CONNECTED,
                    info = DeviceInfo(
                        model = "Xiaomi Smart Band 8",
                        firmwareVersion = "1.2.3",
                        batteryPercent = 75,
                        totalStorage = "32 MB",
                        usedStorage = "12 MB",
                    ),
                )
            }
        }
    }
}

@Composable
private fun PreviewDeviceStatusBar(status: ConnectionStatus, info: DeviceInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val title = when (status) {
                    ConnectionStatus.CONNECTED -> "Xiaomi Smart Band 8"
                    ConnectionStatus.CONNECTING -> "正在连接..."
                    ConnectionStatus.DISCONNECTED -> "暂未连接设备"
                }
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("🔋 ${info.batteryPercent}%", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("总空间: ${info.totalStorage} / 已使用: ${info.usedStorage}", fontSize = 12.sp)
            }
            if (status == ConnectionStatus.CONNECTED) {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error),
                ) {
                    Text("断开连接")
                }
            } else {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("连接设备")
                }
            }
        }
    }
}

// ─── SavedDevicesSection Preview ───
@Preview(name = "SavedDevicesSection", showBackground = true)
@Composable
private fun PreviewSavedDevicesSection() {
    val controller = remember { ThemeController(ColorSchemeMode.Light) }
    val devices = listOf(
        com.bandkit.app.models.SavedDevice("1", "Mi Band 8", "AA:BB:CC:DD:EE:FF", "0123456789abcdef", connectType = "SPP"),
        com.bandkit.app.models.SavedDevice("2", "Xiaomi Watch S3", "11:22:33:44:55:66", "abcdef0123456789", connectType = "BLE"),
    )
    MiuixTheme(controller = controller) {
        Scaffold {
            Box(modifier = Modifier.padding(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("已保存设备", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        devices.forEachIndexed { index, device ->
                            val isCurrent = index == 0
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${device.name}${if (isCurrent) " [当前]" else ""}",
                                        fontSize = 14.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text("${device.addr} · ${device.connectType}", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {},
                                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error),
                                ) {
                                    Text("删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── AddDeviceBottomSheet Preview ───
@Preview(name = "AddDeviceBottomSheet - Direct", showBackground = true)
@Composable
private fun PreviewAddDeviceDirect() {
    val controller = remember { ThemeController(ColorSchemeMode.Light) }
    var name by remember { mutableStateOf("") }
    var addr by remember { mutableStateOf("") }
    var authkey by remember { mutableStateOf("") }
    var sarVersion by remember { mutableIntStateOf(1) }
    var connectTypeBle by remember { mutableStateOf(false) }
    MiuixTheme(controller = controller) {
        Scaffold {
            OverlayBottomSheet(
                show = true,
                title = "添加新设备",
            ) {
                TabRow(
                    tabs = listOf("直接添加", "扫描附近设备"),
                    selectedTabIndex = 0,
                    onTabSelected = {},
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextField(value = name, onValueChange = { name = it }, label = "设备名称 *", singleLine = true)
                Spacer(modifier = Modifier.height(12.dp))
                TextField(value = addr, onValueChange = { addr = it }, label = "设备地址（可选）", singleLine = true)
                Spacer(modifier = Modifier.height(12.dp))
                TextField(value = authkey, onValueChange = { authkey = it }, label = "认证密钥 *", singleLine = true)
                Spacer(modifier = Modifier.height(12.dp))
                OverlayDropdownPreference(
                    title = "SAR 版本",
                    items = listOf("SAR v1", "SAR v2"),
                    selectedIndex = sarVersion,
                    onSelectedIndexChange = { sarVersion = it },
                )
                Spacer(modifier = Modifier.height(12.dp))
                OverlayDropdownPreference(
                    title = "连接类型",
                    items = listOf("SPP", "BLE"),
                    selectedIndex = if (connectTypeBle) 1 else 0,
                    onSelectedIndexChange = { connectTypeBle = it == 1 },
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("保存设备")
                }
            }
        }
    }
}

@Preview(name = "AddDeviceBottomSheet - Scan", showBackground = true)
@Composable
private fun PreviewAddDeviceScan() {
    val controller = remember { ThemeController(ColorSchemeMode.Light) }
    val scannedDevices = listOf(
        com.bandkit.app.core.ScannedDevice("Mi Band 8", "AA:BB:CC:DD:EE:FF", -45),
        com.bandkit.app.core.ScannedDevice("Xiaomi Watch S3", "11:22:33:44:55:66", -60),
        com.bandkit.app.core.ScannedDevice("Redmi Band 2", "FF:EE:DD:CC:BB:AA", -72),
    )
    MiuixTheme(controller = controller) {
        Scaffold {
            OverlayBottomSheet(
                show = true,
                title = "添加新设备",
            ) {
                TabRow(
                    tabs = listOf("直接添加", "扫描附近设备"),
                    selectedTabIndex = 1,
                    onTabSelected = {},
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("附近蓝牙设备", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = {}) { Text("停止扫描") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    items(scannedDevices) { dev ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(dev.address, fontSize = 12.sp)
                            }
                            Text("RSSI: ${dev.rssi}", fontSize = 12.sp)
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text("扫描中... (${scannedDevices.size} 个设备)", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("保存设备")
                }
            }
        }
    }
}

// ─── WatchfaceSection Preview ───
@Preview(name = "WatchfaceSection", showBackground = true)
@Composable
private fun PreviewWatchfaceSection() {
    val controller = remember { ThemeController(ColorSchemeMode.Light) }
    val watchfaces = listOf(
        Watchface("1", "经典表盘", isCurrent = true),
        Watchface("2", "运动表盘"),
        Watchface("3", "数字表盘"),
    )
    MiuixTheme(controller = controller) {
        Scaffold {
            Box(modifier = Modifier.padding(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("表盘列表", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Button(onClick = {}) { Text("刷新") }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        watchfaces.forEach { wf ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(wf.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("ID: ${wf.id}", fontSize = 12.sp)
                                }
                                if (wf.isCurrent) {
                                    Text("当前", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {}) { Text("设为当前", fontSize = 12.sp) }
                                        Button(
                                            onClick = {},
                                            colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error),
                                        ) { Text("卸载", fontSize = 12.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── AppSection Preview ───
@Preview(name = "AppSection", showBackground = true)
@Composable
private fun PreviewAppSection() {
    val controller = remember { ThemeController(ColorSchemeMode.Light) }
    val apps = listOf(
        InstalledApp("com.example.weather", "天气"),
        InstalledApp("com.example.timer", "计时器"),
    )
    MiuixTheme(controller = controller) {
        Scaffold {
            Box(modifier = Modifier.padding(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("应用列表", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Button(onClick = {}) { Text("刷新") }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        apps.forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(app.packageName, fontSize = 12.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {}) { Text("启动", fontSize = 12.sp) }
                                    Button(
                                        onClick = {},
                                        colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error),
                                    ) { Text("删除", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── InstallSection Preview ───
@Preview(name = "InstallSection", showBackground = true)
@Composable
private fun PreviewInstallSection() {
    val controller = remember { ThemeController(ColorSchemeMode.Light) }
    MiuixTheme(controller = controller) {
        Scaffold {
            Box(modifier = Modifier.padding(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("文件安装", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("选择文件进行安装（支持 .bin / .rpk）", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("选择文件") }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("watchface_v1.bin (${formatFileSize(24576)})", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) { Text("开始安装") }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("安装 watchface_v1.bin: 65%", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(progress = 0.65f, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ─── LogSection Preview ───
@Preview(name = "LogSection", showBackground = true)
@Composable
private fun PreviewLogSection() {
    val controller = remember { ThemeController(ColorSchemeMode.Light) }
    val logs = listOf(
        LogEntry(System.currentTimeMillis() - 3000, "正在连接 Mi Band 8...", LogType.INFO),
        LogEntry(System.currentTimeMillis() - 2000, "Mi Band 8 连接成功", LogType.SUCCESS),
        LogEntry(System.currentTimeMillis() - 1000, "设备: Xiaomi Smart Band 8 (1.2.3)", LogType.SUCCESS),
        LogEntry(System.currentTimeMillis(), "加载表盘...", LogType.INFO),
    )
    MiuixTheme(controller = controller) {
        Scaffold {
            Box(modifier = Modifier.padding(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("操作日志", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        logs.forEach { entry ->
                            val color = when (entry.type) {
                                LogType.SUCCESS -> MiuixTheme.colorScheme.primary
                                LogType.ERROR -> MiuixTheme.colorScheme.error
                                LogType.WARNING -> MiuixTheme.colorScheme.error.copy(alpha = 0.6f)
                                LogType.INFO -> MiuixTheme.colorScheme.onSurface
                            }
                            Text(
                                "[${entry.message}]",
                                fontSize = 12.sp,
                                color = color,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Full Page Preview ───
@Preview(name = "FullPage", showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun PreviewFullPage() {
    val controller = remember { ThemeController(ColorSchemeMode.Light) }
    var activeTab by remember { mutableIntStateOf(0) }
    MiuixTheme(controller = controller) {
        Scaffold(
            topBar = {
                SmallTopAppBar(title = "BANDBURG")
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                item {
                    PreviewDeviceStatusBar(
                        status = ConnectionStatus.CONNECTED,
                        info = DeviceInfo(model = "Mi Band 8", batteryPercent = 75, totalStorage = "32 MB", usedStorage = "12 MB"),
                    )
                }
                item {
                    TabRow(
                        tabs = listOf("表盘", "应用", "安装"),
                        selectedTabIndex = activeTab,
                        onTabSelected = { activeTab = it },
                    )
                }
                item {
                    when (activeTab) {
                        0 -> PreviewWatchfaceSectionContent()
                        1 -> PreviewAppSectionContent()
                        2 -> PreviewInstallSectionContent()
                    }
                }
                item { PreviewLogSectionContent() }
            }
        }
    }
}

@Composable
private fun PreviewWatchfaceSectionContent() {
    val watchfaces = listOf(
        Watchface("1", "经典表盘", isCurrent = true),
        Watchface("2", "运动表盘"),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("表盘列表", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            watchfaces.forEach { wf ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(wf.name, fontSize = 14.sp)
                        Text("ID: ${wf.id}", fontSize = 12.sp)
                    }
                    if (wf.isCurrent) Text("当前", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun PreviewAppSectionContent() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("应用列表", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("未连接到设备或没有应用数据", fontSize = 14.sp)
        }
    }
}

@Composable
private fun PreviewInstallSectionContent() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("文件安装", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text("选择文件进行安装（支持 .bin / .rpk）", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("选择文件") }
        }
    }
}

@Composable
private fun PreviewLogSectionContent() {
    val logs = listOf(
        LogEntry(System.currentTimeMillis(), "欢迎使用 BandKit", LogType.INFO),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("操作日志", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            logs.forEach { entry ->
                Text("[${entry.message}]", fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}
