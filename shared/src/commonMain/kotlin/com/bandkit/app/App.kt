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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandkit.app.core.BandBurgManager
import com.bandkit.app.core.IO
import com.bandkit.app.core.LocalPlatformContext
import com.bandkit.app.core.PickedFile
import com.bandkit.app.core.PlatformContextProvider
import com.bandkit.app.core.ScannedDevice
import com.bandkit.app.core.createBandBurgManager
import com.bandkit.app.core.createBluetoothScanner
import com.bandkit.app.core.currentTimeMillis
import com.bandkit.app.core.exportSavedDevicesToFile
import com.bandkit.app.core.formatTimestamp
import com.bandkit.app.core.importSavedDevicesFromFile
import com.bandkit.app.core.initBandBurgContext
import com.bandkit.app.core.launchAboutActivity
import com.bandkit.app.core.loadSavedDevices
import com.bandkit.app.core.loadShowLogs
import com.bandkit.app.core.pickFileFromPicker
import com.bandkit.app.core.saveSavedDevices
import com.bandkit.app.core.saveShowLogs
import com.bandkit.app.core.extractFileIdentifier
import com.bandkit.app.models.ConnectionStatus
import com.bandkit.app.models.DeviceInfo
import com.bandkit.app.models.InstalledApp
import com.bandkit.app.models.LogEntry
import com.bandkit.app.models.LogType
import com.bandkit.app.models.SavedDevice
import com.bandkit.app.models.Watchface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.time.Duration.Companion.seconds

/** 格式化文件大小：自适应 B/KB/MB */
fun formatFileSize(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> "${"%.1f".format(bytes.toFloat() / (1024 * 1024))} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
private fun SimpleDivider() {
    Box(
        modifier = Modifier.fillMaxWidth().height(1.dp)
            .background(Color.LightGray.copy(alpha = 0.3f)),
    )
}

private fun parseStoragePercent(used: String, total: String): Float {
    fun parseBytes(s: String): Float {
        val num = s.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: return 0f
        return when {
            s.contains("TB", ignoreCase = true) -> num * 1024f * 1024f * 1024f * 1024f
            s.contains("GB", ignoreCase = true) -> num * 1024f * 1024f * 1024f
            s.contains("MB", ignoreCase = true) -> num * 1024f * 1024f
            s.contains("KB", ignoreCase = true) -> num * 1024f
            else -> num
        }
    }
    val usedBytes = parseBytes(used)
    val totalBytes = parseBytes(total)
    return if (totalBytes > 0f) (usedBytes / totalBytes).coerceIn(0f, 1f) else 0f
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
    var showLogs by remember { mutableStateOf(loadShowLogs(context)) }
    var selectedNavIndex by remember { mutableIntStateOf(0) }
    var connectionStatus by remember { mutableStateOf(ConnectionStatus.DISCONNECTED) }
    var deviceSession by remember { mutableStateOf<com.bandkit.app.models.DeviceSession?>(null) }
    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }
    val savedDevices = remember { mutableStateListOf<SavedDevice>() }.also {
        if (it.isEmpty()) it.addAll(loadSavedDevices(context))
    }
    val watchfaces = remember { mutableStateListOf<Watchface>() }
    val apps = remember { mutableStateListOf<InstalledApp>() }
    val logs = remember {
        mutableStateListOf(
            LogEntry(currentTimeMillis(), "欢迎使用 BandKit - Vela 设备管理工具"),
        )
    }

    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var showSavedDevicesSheet by remember { mutableStateOf(false) }
    var deviceFormTab by remember { mutableIntStateOf(0) }
    var deviceName by remember { mutableStateOf("") }
    var deviceAddr by remember { mutableStateOf("") }
    var deviceAuthkey by remember { mutableStateOf("") }
    var deviceSarVersion by remember { mutableIntStateOf(1) }
    var deviceConnectTypeBle by remember { mutableStateOf(false) }
    val scannedDevices = remember { mutableStateListOf<ScannedDevice>() }
    var isScanning by remember { mutableStateOf(false) }

    val addLog: (String, LogType) -> Unit = { message, type ->
        logs.add(0, LogEntry(currentTimeMillis(), message, type))
        if (logs.size > 50) logs.removeLast()
    }

    fun connectToDevice(device: SavedDevice) {
        scope.launch {
            if (deviceSession != null) {
                addLog("正在断开当前设备...", LogType.INFO)
                withContext(IO) { manager.disconnect(deviceSession!!) }
                deviceSession = null
                connectionStatus = ConnectionStatus.DISCONNECTED
                deviceInfo = DeviceInfo()
                watchfaces.clear()
                apps.clear()
            }
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
                delay(1.seconds)
                addLog("正在获取设备信息...", LogType.INFO)
                var info = withContext(IO) { manager.getDeviceInfo(session) }
                if (info.model == device.name && info.firmwareVersion == "-") {
                    addLog("首次查询无数据，重试中...", LogType.INFO)
                    delay(2.seconds)
                    info = withContext(IO) { manager.getDeviceInfo(session) }
                }
                deviceInfo = info
                addLog("设备: ${info.model} (${info.firmwareVersion})", LogType.SUCCESS)
                addLog("电量: ${info.batteryPercent}% | 存储: ${info.totalStorage}", LogType.SUCCESS)
                addLog("正在加载表盘和应用列表...", LogType.INFO)
                try {
                    val wf = withContext(IO) { manager.getWatchfaceList(session) }
                    watchfaces.clear()
                    watchfaces.addAll(wf)
                    addLog("已加载 ${wf.size} 个表盘", LogType.SUCCESS)
                } catch (e: Exception) {
                    addLog("表盘加载失败: ${e.message}", LogType.ERROR)
                }
                try {
                    val appList = withContext(IO) { manager.getAppList(session) }
                    apps.clear()
                    apps.addAll(appList)
                    addLog("已加载 ${appList.size} 个应用", LogType.SUCCESS)
                } catch (e: Exception) {
                    addLog("应用加载失败: ${e.message}", LogType.ERROR)
                }
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
                connectionStatus = ConnectionStatus.DISCONNECTED
                deviceInfo = DeviceInfo()
                watchfaces.clear()
                apps.clear()
                addLog("已断开", LogType.SUCCESS)
            }
        }
    }

    DisposableEffect(Unit) {
        scanner.init(context)
        onDispose {
            scanner.stopScan()
            deviceSession?.let { manager.destroySession(it) }
        }
    }

    LaunchedEffect(Unit) {
        withContext(IO) {
            manager.init()
            initBandBurgContext(manager, context)
        }
    }

    MiuixTheme(controller = controller) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                SmallTopAppBar(
                    title = "BANDKIT",
                    actions = {
                        if (selectedNavIndex == 0) {
                            IconButton(onClick = { showAddDeviceDialog = true }) {
                                Icon(
                                    imageVector = MiuixIcons.Link,
                                    contentDescription = "添加设备",
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedNavIndex == 0,
                        onClick = { selectedNavIndex = 0 },
                        icon = MiuixIcons.Home,
                        label = "设备",
                    )
                    NavigationBarItem(
                        selected = selectedNavIndex == 1,
                        onClick = { selectedNavIndex = 1 },
                        icon = MiuixIcons.Play,
                        label = "脚本",
                    )
                    NavigationBarItem(
                        selected = selectedNavIndex == 2,
                        onClick = { selectedNavIndex = 2 },
                        icon = MiuixIcons.Settings,
                        label = "设置",
                    )
                }
            },
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (selectedNavIndex) {
                    0 -> {
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
                                    onDeviceNameClick = { showSavedDevicesSheet = true },
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
                                    0 -> WatchfaceSection(watchfaces, deviceSession, manager, addLog) { newItems ->
                                        watchfaces.clear()
                                        watchfaces.addAll(newItems)
                                    }

                                    1 -> AppSection(apps, deviceSession, manager, addLog) { newItems ->
                                        apps.clear()
                                        apps.addAll(newItems)
                                    }

                                    2 -> InstallSection(deviceSession, manager, context) { msg, type -> addLog(msg, type) }
                                }
                            }
                            if (showLogs) {
                                item { LogSection(logs) }
                            }
                        }
                    }

                    1 -> {
                        PlatformScriptScreen(session = deviceSession)
                    }

                    2 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            item {
                                SmallTitle(text = "通用")
                            }
                            item {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    SwitchPreference(
                                        title = "显示操作日志",
                                        summary = "在主界面显示操作日志面板",
                                        checked = showLogs,
                                        onCheckedChange = {
                                            showLogs = it
                                            saveShowLogs(context, it)
                                        },
                                    )
                                }
                            }
                            item {
                                SmallTitle(text = "设备管理")
                            }
                            item {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    BasicComponent(
                                        title = "导入设备",
                                        summary = "从文件导入已保存的设备配置",
                                        onClick = {
                                            scope.launch {
                                                addLog("正在导入设备...", LogType.INFO)
                                                val imported = withContext(IO) {
                                                    importSavedDevicesFromFile(context)
                                                }
                                                if (imported != null) {
                                                    val existingAddrs = savedDevices.map { it.addr }.toSet()
                                                    val newDevices = imported.filter { it.addr !in existingAddrs }
                                                    val skipped = imported.size - newDevices.size
                                                    savedDevices.addAll(newDevices)
                                                    saveSavedDevices(context, savedDevices)
                                                    val msg = buildString {
                                                        append("成功导入 ${newDevices.size} 个设备")
                                                        if (skipped > 0) append("，跳过 $skipped 个重复设备")
                                                    }
                                                    addLog(msg, LogType.SUCCESS)
                                                } else {
                                                    addLog("设备导入失败或已取消", LogType.WARNING)
                                                }
                                            }
                                        },
                                    )
                                    BasicComponent(
                                        title = "导出设备",
                                        summary = "将已保存的设备配置导出到文件",
                                        onClick = {
                                            scope.launch {
                                                addLog("正在导出设备...", LogType.INFO)
                                                val success = withContext(IO) {
                                                    exportSavedDevicesToFile(context, savedDevices)
                                                }
                                                if (success) {
                                                    addLog("设备导出成功", LogType.SUCCESS)
                                                } else {
                                                    addLog("设备导出失败或已取消", LogType.WARNING)
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                            item {
                                SmallTitle(text = "关于")
                            }
                            item {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    ArrowPreference(
                                        title = "关于 BandKit",
                                        summary = "v${AppBuildConfig.VERSION_NAME}",
                                        onClick = { launchAboutActivity(context) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (showAddDeviceDialog) {
                AddDeviceBottomSheet(
                    tab = deviceFormTab, onTabChange = {
                        if (deviceFormTab == 1 && it != 1 && isScanning) {
                            scanner.stopScan()
                            isScanning = false
                        }
                        deviceFormTab = it
                    },
                    deviceName, { deviceName = it },
                    deviceAddr, { deviceAddr = it },
                    deviceAuthkey, { deviceAuthkey = it },
                    deviceSarVersion, { deviceSarVersion = it },
                    deviceConnectTypeBle, { deviceConnectTypeBle = it },
                    scannedDevices, isScanning,
                    onStartScan = {
                        scannedDevices.clear()
                        isScanning = true
                        addLog("开始扫描附近蓝牙设备...", LogType.INFO)
                        var lastCount = 0
                        var stableRounds = 0
                        scope.launch {
                            while (isScanning) {
                                delay(1.seconds)
                                val current = scannedDevices.size
                                if (current == lastCount) {
                                    stableRounds++
                                    if (stableRounds >= 3) {
                                        scanner.stopScan()
                                        isScanning = false
                                        addLog("扫描完成，发现 ${scannedDevices.size} 个设备", LogType.SUCCESS)
                                        break
                                    }
                                } else {
                                    stableRounds = 0
                                    lastCount = current
                                }
                            }
                        }
                        scanner.startScan(
                            onDeviceFound = { dev -> if (scannedDevices.none { it.address == dev.address }) scannedDevices.add(dev) },
                            onScanComplete = {
                                if (isScanning) {
                                    isScanning = false
                                    addLog("扫描完成，发现 ${scannedDevices.size} 个设备", LogType.SUCCESS)
                                }
                            },
                        )
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
                        savedDevices.add(dev)
                        saveSavedDevices(context, savedDevices)
                        addLog("设备 ${dev.name} 添加成功", LogType.SUCCESS)
                        deviceName = ""
                        deviceAddr = ""
                        deviceAuthkey = ""
                        deviceSarVersion = 1
                        deviceConnectTypeBle = false
                        deviceFormTab = 0
                        showAddDeviceDialog = false
                    },
                    onDismiss = {
                        if (isScanning) {
                            scanner.stopScan()
                            isScanning = false
                        }
                        showAddDeviceDialog = false
                    },
                )
            }
            if (showSavedDevicesSheet) {
                SavedDevicesBottomSheet(
                    savedDevices = savedDevices,
                    currentSession = deviceSession,
                    onConnect = { device ->
                        showSavedDevicesSheet = false
                        connectToDevice(device)
                    },
                    onEdit = { device ->
                        deviceName = device.name
                        deviceAddr = device.addr
                        deviceAuthkey = device.authkey
                        deviceSarVersion = if (device.sarVersion == 2) 1 else 0
                        deviceConnectTypeBle = device.connectType == "BLE"
                        savedDevices.remove(device)
                        saveSavedDevices(context, savedDevices)
                        deviceFormTab = 0
                        showSavedDevicesSheet = false
                        showAddDeviceDialog = true
                    },
                    onDelete = { device ->
                        if (deviceSession?.device?.id == device.id) disconnectFromDevice()
                        savedDevices.remove(device)
                        saveSavedDevices(context, savedDevices)
                        addLog("${device.name} 已删除", LogType.SUCCESS)
                    },
                    onDismiss = { showSavedDevicesSheet = false },
                )
            }
        }
    }
}

// ─── DeviceStatusBar ───
@Composable
private fun DeviceStatusBar(
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
private fun SavedDevicesBottomSheet(
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
                    items(savedDevices) { device ->
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

// ─── WatchfaceSection ───
@Composable
private fun WatchfaceSection(
    watchfaces: List<Watchface>,
    session: com.bandkit.app.models.DeviceSession?,
    manager: BandBurgManager,
    addLog: (String, LogType) -> Unit,
    onUpdate: (List<Watchface>) -> Unit,
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
                Text("表盘列表", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (isLoading) {
                    InfiniteProgressIndicator(modifier = Modifier.padding(8.dp))
                } else {
                    IconButton(
                        onClick = {
                            session?.let { s ->
                                scope.launch {
                                    isLoading = true
                                    addLog("加载表盘...", LogType.INFO)
                                    try {
                                        val r = withContext(IO) { manager.getWatchfaceList(s) }
                                        onUpdate(r)
                                        addLog("已加载 ${r.size} 个表盘", LogType.SUCCESS)
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
            if (watchfaces.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("未连接到设备或没有表盘数据", fontSize = 14.sp)
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(watchfaces.size) { index ->
                            val wf = watchfaces[index]
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                val s = session ?: return@IconButton
                                                scope.launch {
                                                    withContext(IO) { manager.setWatchface(s, wf.id) }
                                                    onUpdate(watchfaces.map { it.copy(isCurrent = it.id == wf.id) })
                                                }
                                            },
                                        ) {
                                            Icon(imageVector = MiuixIcons.Ok, contentDescription = "设为当前")
                                        }
                                        IconButton(
                                            onClick = {
                                                val s = session ?: return@IconButton
                                                scope.launch {
                                                    withContext(IO) { manager.uninstallWatchface(s, wf.id) }
                                                    onUpdate(watchfaces.filter { it.id != wf.id })
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Delete,
                                                contentDescription = "卸载",
                                                tint = MiuixTheme.colorScheme.error,
                                            )
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
    }
}

// ─── AppSection ───
@Composable
private fun AppSection(
    apps: List<InstalledApp>,
    session: com.bandkit.app.models.DeviceSession?,
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

// ─── InstallSection ───
@Composable
private fun InstallSection(
    session: com.bandkit.app.models.DeviceSession?,
    manager: BandBurgManager,
    context: Any,
    onLog: (String, LogType) -> Unit,
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
                            val resType = when {
                                file.name.endsWith(".rpk", true) -> 64
                                file.name.endsWith(".bin", true) -> 16
                                else -> 16
                            }
                            val resTypeName = when (resType) {
                                64 -> "RPK(第三方应用)"
                                16 -> "BIN(表盘/固件)"
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
                                    } else {
                                        onLog("文件安装失败: ${file.name}，请查看 Logcat 获取详细错误", LogType.ERROR)
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
@Composable
private fun LogSection(logs: List<LogEntry>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).height(200.dp)) {
            Text("操作日志", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            SimpleDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs, key = { "${it.timestamp}_${it.message.hashCode()}" }) { entry ->
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
