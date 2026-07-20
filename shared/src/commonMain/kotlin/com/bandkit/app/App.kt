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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.bandkit.app.core.detectFileType
import com.bandkit.app.core.exportSavedDevicesToFile
import com.bandkit.app.core.extractFileIdentifier
import com.bandkit.app.core.formatFileSize
import com.bandkit.app.core.formatTimestamp
import com.bandkit.app.core.importSavedDevicesFromFile
import com.bandkit.app.core.initBandBurgContext
import com.bandkit.app.core.launchAboutActivity
import com.bandkit.app.core.loadLastDevice
import com.bandkit.app.core.loadSavedDevices
import com.bandkit.app.core.loadShowLogs
import com.bandkit.app.core.parseStoragePercent
import com.bandkit.app.core.pickFileFromPicker
import com.bandkit.app.core.saveLastDevice
import com.bandkit.app.core.saveSavedDevices
import com.bandkit.app.core.saveShowLogs
import com.bandkit.app.core.showToast
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
import top.yukonga.miuix.kmp.basic.Text
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
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.time.Duration.Companion.seconds

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
    // 从关于页返回时重新读取日志开关状态
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                showLogs = loadShowLogs(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var selectedNavIndex by remember { mutableIntStateOf(0) }
    var connectionStatus by remember { mutableStateOf(ConnectionStatus.DISCONNECTED) }
    var deviceSession by remember { mutableStateOf<com.bandkit.app.models.DeviceSession?>(null) }
    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }
    val savedDevices = remember { mutableStateListOf<SavedDevice>() }.also {
        if (it.isEmpty()) it.addAll(loadSavedDevices(context))
    }
    val watchfaces = remember { mutableStateListOf<Watchface>() }
    val apps = remember { mutableStateListOf<InstalledApp>() }
    var logCounter by remember { mutableLongStateOf(0L) }
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
        if (showLogs) {
            logCounter++
            logs.add(0, LogEntry(currentTimeMillis(), message, type, logCounter))
            if (logs.size > 200) logs.removeAt(logs.lastIndex)
        }
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
                showToast(context, "${device.name} 连接成功")
                saveLastDevice(context, device)
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
                showToast(context, "连接失败: ${e.message}")
            }
        }
    }

    // 启动时自动连接上一次连接的设备
    LaunchedEffect(Unit) {
        val lastDevice = loadLastDevice(context)
        if (lastDevice != null) {
            addLog("发现上次连接的设备: ${lastDevice.name}，正在自动连接...", LogType.INFO)
            connectToDevice(lastDevice)
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
                saveLastDevice(context, null) // 清除记忆，不自动重连
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

                                    2 -> InstallSection(
                                        deviceSession,
                                        manager,
                                        context,
                                        onLog = { msg, type -> addLog(msg, type) },
                                        onInstallComplete = { resType ->
                                            scope.launch {
                                                val s = deviceSession ?: return@launch
                                                if (resType == 64) {
                                                    val newApps = withContext(IO) { manager.getAppList(s) }
                                                    apps.clear()
                                                    apps.addAll(newApps)
                                                    addLog("应用列表已刷新", LogType.INFO)
                                                } else if (resType == 16) {
                                                    val newWf = withContext(IO) { manager.getWatchfaceList(s) }
                                                    watchfaces.clear()
                                                    watchfaces.addAll(newWf)
                                                    addLog("表盘列表已刷新", LogType.INFO)
                                                }
                                            }
                                        },
                                    )
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
                                    // 操作日志开关已移至关于页：连续点击 BandKit 标题 5 次
                                    BasicComponent(
                                        title = "操作日志",
                                        summary = "在关于页连续点击 BandKit 标题 5 次进行开关",
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
