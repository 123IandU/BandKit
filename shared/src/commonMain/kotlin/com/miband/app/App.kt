// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.miband.app

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miband.app.core.BandBurgManager
import com.miband.app.core.BluetoothScanner
import com.miband.app.core.IO
import com.miband.app.core.LocalPlatformContext
import com.miband.app.core.PickedFile
import com.miband.app.core.PlatformContextProvider
import com.miband.app.core.ScannedDevice
import com.miband.app.core.createBandBurgManager
import com.miband.app.core.createBluetoothScanner
import com.miband.app.core.createFilePicker
import com.miband.app.core.currentTimeMillis
import com.miband.app.core.formatTimestamp
import com.miband.app.core.initBandBurgContext
import com.miband.app.core.loadSavedDevices
import com.miband.app.core.pickFileFromPicker
import com.miband.app.core.saveSavedDevices
import com.miband.app.models.ConnectionStatus
import com.miband.app.models.DeviceInfo
import com.miband.app.models.InstalledApp
import com.miband.app.models.LogEntry
import com.miband.app.models.LogType
import com.miband.app.models.SavedDevice
import com.miband.app.models.Watchface
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
private fun SimpleDivider() {
    Box(
        modifier = Modifier.fillMaxWidth().height(1.dp)
            .padding(horizontal = 16.dp),
    )
}

@Composable
fun App(modifier: Modifier = Modifier) {
    PlatformContextProvider {
        AppContent(modifier)
    }
}

@Composable
private fun AppContent(modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { createBandBurgManager() }
    val scanner = remember { createBluetoothScanner() }
    val controller = remember { ThemeController(ColorSchemeMode.System) }

    var activeTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf(ConnectionStatus.DISCONNECTED) }
    var deviceSession by remember { mutableStateOf<com.miband.app.models.DeviceSession?>(null) }
    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }
    var savedDevices by remember { mutableStateOf(loadSavedDevices(context)) }
    var watchfaces by remember { mutableStateOf(emptyList<Watchface>()) }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var logs by remember {
        mutableStateOf(
            listOf(LogEntry(currentTimeMillis(), "欢迎使用 BandBurg - Vela 设备管理工具")),
        )
    }

    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var deviceFormTab by remember { mutableIntStateOf(0) }
    var deviceName by remember { mutableStateOf("") }
    var deviceAddr by remember { mutableStateOf("") }
    var deviceAuthkey by remember { mutableStateOf("") }
    var deviceSarVersion by remember { mutableIntStateOf(1) }
    var deviceConnectTypeBle by remember { mutableStateOf(false) }
    var scannedDevices by remember { mutableStateOf(emptyList<ScannedDevice>()) }
    var isScanning by remember { mutableStateOf(false) }

    val addLog: (String, LogType) -> Unit = { message, type ->
        logs = listOf(LogEntry(currentTimeMillis(), message, type)) + logs.take(49)
    }

    fun connectToDevice(device: SavedDevice) {
        scope.launch {
            connectionStatus = ConnectionStatus.CONNECTING
            addLog("正在连接 ${device.name}...", LogType.INFO)
            try {
                val session = withContext(IO) {
                    manager.connect(
                        device.name,
                        device.addr,
                        device.authkey,
                        if (device.connectType == "BLE") 1 else 0,
                    )
                }
                deviceSession = session
                connectionStatus = ConnectionStatus.CONNECTED
                addLog("${device.name} 连接成功", LogType.SUCCESS)
                val info = withContext(IO) { manager.getDeviceInfo(session) }
                deviceInfo = info
                addLog("设备: ${info.model} (${info.firmwareVersion})", LogType.SUCCESS)
            } catch (e: Exception) {
                connectionStatus = ConnectionStatus.DISCONNECTED
                addLog("连接失败: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun disconnectFromDevice() {
        deviceSession?.let { session ->
            scope.launch {
                addLog("正在断开...", LogType.INFO)
                withContext(IO) { manager.disconnect(session) }
                deviceSession = null
                connectionStatus = ConnectionStatus.CONNECTED
                deviceInfo = DeviceInfo()
                addLog("已断开", LogType.SUCCESS)
            }
        }
    }

    DisposableEffect(Unit) {
        manager.init()
        initBandBurgContext(manager, context)
        scanner.init(context)
        onDispose {
            scanner.stopScan()
            deviceSession?.let { manager.destroySession(it) }
        }
    }

    MiuixTheme(controller = controller) {
        if (showSettings) {
            SettingsScreen(onBack = { showSettings = false })
        } else {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    SmallTopAppBar(
                        title = "BANDBURG",
                        actions = {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(
                                    imageVector = MiuixIcons.Settings,
                                    contentDescription = "设置",
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                        item {
                            DeviceStatusBar(
                                connectionStatus,
                                deviceInfo,
                                deviceSession,
                                onDisconnect = ::disconnectFromDevice,
                                onConnect = {
                                    deviceSession?.let { connectToDevice(it.device) }
                                        ?: run { showAddDeviceDialog = true }
                                },
                            )
                        }
                        item {
                            SavedDevicesSection(
                                savedDevices,
                                deviceSession,
                                onConnect = ::connectToDevice,
                                onDelete = { device ->
                                    if (deviceSession?.device?.id == device.id) disconnectFromDevice()
                                    savedDevices = savedDevices.filter { it.id != device.id }
                                    saveSavedDevices(context, savedDevices)
                                    addLog("${device.name} 已删除", LogType.SUCCESS)
                                },
                            )
                        }
                        item {
                            Button(
                                onClick = { showAddDeviceDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(),
                            ) {
                                Text("+ 添加新设备")
                            }
                        }
                        item {
                            TabRowWithContour(
                                tabs = listOf("表盘", "应用", "安装"),
                                selectedTabIndex = activeTab,
                                onTabSelected = { activeTab = it },
                            )
                        }
                        item {
                            when (activeTab) {
                                0 -> WatchfaceSection(watchfaces, deviceSession, manager, addLog) { watchfaces = it }
                                1 -> AppSection(apps, deviceSession, manager, addLog) { apps = it }
                                2 -> InstallSection(deviceSession, manager) { msg, type -> addLog(msg, type) }
                            }
                        }
                        item { LogSection(logs) }
                        item { Footer() }
                    }
                }
                if (showAddDeviceDialog) {
                    AddDeviceBottomSheet(
                        deviceFormTab, { deviceFormTab = it },
                        deviceName, { deviceName = it },
                        deviceAddr, { deviceAddr = it },
                        deviceAuthkey, { deviceAuthkey = it },
                        deviceSarVersion, { deviceSarVersion = it },
                        deviceConnectTypeBle, { deviceConnectTypeBle = it },
                        scannedDevices, isScanning,
                        onStartScan = {
                            scannedDevices = emptyList()
                            isScanning = true
                            addLog("开始扫描附近蓝牙设备...", LogType.INFO)
                            scanner.startScan(
                                onDeviceFound = { dev -> if (scannedDevices.none { it.address == dev.address }) scannedDevices = scannedDevices + dev },
                                onScanComplete = {
                                    isScanning = false
                                    addLog("扫描完成 (如无设备请检查蓝牙权限)", LogType.INFO)
                                },
                            )
                        },
                        onStopScan = {
                            scanner.stopScan()
                            isScanning = false
                        },
                        onDeviceSelected = { dev ->
                            deviceName = dev.name
                            deviceAddr = dev.address
                            deviceFormTab = 0
                        },
                        onSave = {
                            if (deviceName.isBlank()) {
                                addLog("请填写设备名称", LogType.ERROR)
                                return@AddDeviceBottomSheet
                            }
                            if (deviceAuthkey.isBlank()) {
                                addLog("请填写认证密钥", LogType.ERROR)
                                return@AddDeviceBottomSheet
                            }
                            val dev = SavedDevice(
                                id = currentTimeMillis().toString(),
                                name = deviceName,
                                addr = deviceAddr.ifBlank { "00:00:00:00:00:00" },
                                authkey = deviceAuthkey,
                                sarVersion = if (deviceSarVersion == 1) 2 else 1,
                                connectType = if (deviceConnectTypeBle) "BLE" else "SPP",
                            )
                            savedDevices = savedDevices + dev
                            saveSavedDevices(context, savedDevices)
                            addLog("设备 ${dev.name} 保存成功", LogType.SUCCESS)
                            showAddDeviceDialog = false
                        },
                        onDismiss = { showAddDeviceDialog = false },
                    )
                }
            }
        }
    }
}

// ─── SettingsScreen ───
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "设置",
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("设备管理", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("蓝牙扫描设置", fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                        SimpleDivider()
                        Text("SAR 版本默认值", fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                        SimpleDivider()
                        Text("连接超时时间", fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("关于", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("版本: 1.0.0", fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                        SimpleDivider()
                        Text("Powered by ASTROBOX", fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}

// ─── DeviceStatusBar ───
@Composable
private fun DeviceStatusBar(
    status: ConnectionStatus,
    info: DeviceInfo,
    session: com.miband.app.models.DeviceSession?,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val title = when (status) {
                    ConnectionStatus.CONNECTED -> session?.device?.name ?: "已连接"
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
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                    ),
                ) {
                    Text("断开连接")
                }
            } else {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("连接设备")
                }
            }
        }
    }
}

// ─── SavedDevicesSection ───
@Composable
private fun SavedDevicesSection(
    devices: List<SavedDevice>,
    currentSession: com.miband.app.models.DeviceSession?,
    onConnect: (SavedDevice) -> Unit,
    onDelete: (SavedDevice) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("已保存设备", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (devices.isEmpty()) {
                Text("暂无保存的设备", fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
            } else {
                devices.forEachIndexed { index, device ->
                    val isCurrent = currentSession?.device?.id == device.id
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            .clickable { onConnect(device) },
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
                            onClick = { onDelete(device) },
                            colors = ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.error,
                            ),
                        ) {
                            Text("删除")
                        }
                    }
                    if (index < devices.lastIndex) {
                        SimpleDivider()
                    }
                }
            }
        }
    }
}

// ─── AddDeviceBottomSheet ───
@Composable
private fun AddDeviceBottomSheet(
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
    onStopScan: () -> Unit,
    onDeviceSelected: (ScannedDevice) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        title = "添加新设备",
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
                        label = "设备地址（可选）",
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
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        .clickable { onDeviceSelected(dev) }
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
            Text("保存设备")
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ─── WatchfaceSection ───
@Composable
private fun WatchfaceSection(
    watchfaces: List<Watchface>,
    session: com.miband.app.models.DeviceSession?,
    manager: BandBurgManager,
    addLog: (String, LogType) -> Unit,
    onUpdate: (List<Watchface>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("表盘列表", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        session?.let { s ->
                            scope.launch {
                                addLog("加载表盘...", LogType.INFO)
                                try {
                                    val r = withContext(IO) { manager.getWatchfaceList(s) }
                                    onUpdate(r)
                                    addLog("已加载 ${r.size} 个表盘", LogType.SUCCESS)
                                } catch (e: Exception) {
                                    addLog("失败: ${e.message}", LogType.ERROR)
                                }
                            }
                        }
                    },
                    enabled = session != null,
                ) {
                    Text("刷新")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (watchfaces.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("未连接到设备或没有表盘数据", fontSize = 14.sp)
                }
            } else {
                watchfaces.forEachIndexed { index, wf ->
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
                                Button(
                                    onClick = {
                                        val s = session ?: return@Button
                                        scope.launch {
                                            withContext(IO) { manager.setWatchface(s, wf.id) }
                                            onUpdate(watchfaces.map { it.copy(isCurrent = it.id == wf.id) })
                                        }
                                    },
                                ) {
                                    Text("设为当前", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val s = session ?: return@Button
                                        scope.launch {
                                            withContext(IO) { manager.uninstallWatchface(s, wf.id) }
                                            onUpdate(watchfaces.filter { it.id != wf.id })
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        color = MiuixTheme.colorScheme.error,
                                    ),
                                ) {
                                    Text("卸载", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    if (index < watchfaces.lastIndex) {
                        SimpleDivider()
                    }
                }
            }
        }
    }
}

// ─── AppSection ───
@Composable
private fun AppSection(
    apps: List<InstalledApp>,
    session: com.miband.app.models.DeviceSession?,
    manager: BandBurgManager,
    addLog: (String, LogType) -> Unit,
    onUpdate: (List<InstalledApp>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("应用列表", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        session?.let { s ->
                            scope.launch {
                                addLog("加载应用...", LogType.INFO)
                                try {
                                    val r = withContext(IO) { manager.getAppList(s) }
                                    onUpdate(r)
                                    addLog("已加载 ${r.size} 个应用", LogType.SUCCESS)
                                } catch (e: Exception) {
                                    addLog("失败: ${e.message}", LogType.ERROR)
                                }
                            }
                        }
                    },
                    enabled = session != null,
                ) {
                    Text("刷新")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (apps.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("未连接到设备或没有应用数据", fontSize = 14.sp)
                }
            } else {
                apps.forEachIndexed { index, app ->
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
                            Button(
                                onClick = {
                                    val s = session ?: return@Button
                                    scope.launch { withContext(IO) { manager.launchApp(s, app.packageName) } }
                                },
                            ) {
                                Text("启动", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    val s = session ?: return@Button
                                    scope.launch {
                                        withContext(IO) { manager.uninstallApp(s, app.packageName) }
                                        onUpdate(apps.filter { it.packageName != app.packageName })
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    color = MiuixTheme.colorScheme.error,
                                ),
                            ) {
                                Text("删除", fontSize = 12.sp)
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

// ─── InstallSection ───
@Composable
private fun InstallSection(
    session: com.miband.app.models.DeviceSession?,
    manager: BandBurgManager,
    onLog: (String, LogType) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedFile by remember { mutableStateOf<PickedFile?>(null) }
    var installProgress by remember { mutableStateOf(-1f) }
    var installMessage by remember { mutableStateOf("") }
    var isInstalling by remember { mutableStateOf(false) }
    val filePicker = remember { createFilePicker() }

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
                            val file = pickFileFromPicker(filePicker)
                            if (file != null) {
                                selectedFile = file
                                onLog("已选择: ${file.name} (${file.data.size} 字节)", LogType.INFO)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("选择文件")
                }
                selectedFile?.let { file ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${file.name} (${file.data.size} 字节)", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isInstalling = true
                            installProgress = 0f
                            installMessage = "正在安装 ${file.name}..."
                            scope.launch {
                                try {
                                    val resType = when {
                                        file.name.endsWith(".rpk", true) -> 64
                                        file.name.endsWith(".bin", true) -> 16
                                        else -> 16
                                    }
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
                                    } else {
                                        onLog("文件安装失败: ${file.name}", LogType.ERROR)
                                    }
                                } catch (e: Exception) {
                                    onLog("安装失败: ${e.message}", LogType.ERROR)
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
@Composable
private fun LogSection(logs: List<LogEntry>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("操作日志", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            SimpleDivider()
            logs.forEach { entry ->
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

// ─── Footer ───
@Composable
private fun Footer() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text("Powered by ASTROBOX", fontSize = 12.sp)
    }
}
