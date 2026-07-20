// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bandkit.app.core.BandBurgManager
import com.bandkit.app.core.IO
import com.bandkit.app.core.currentTimeMillis
import com.bandkit.app.core.loadSavedDevices
import com.bandkit.app.core.loadShowLogs
import com.bandkit.app.core.saveLastDevice
import com.bandkit.app.core.showToast
import com.bandkit.app.models.ConnectionStatus
import com.bandkit.app.models.DeviceInfo
import com.bandkit.app.models.DeviceSession
import com.bandkit.app.models.InstalledApp
import com.bandkit.app.models.LogEntry
import com.bandkit.app.models.LogType
import com.bandkit.app.models.SavedDevice
import com.bandkit.app.models.Watchface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

@Stable
class AppContentState(
    private val manager: BandBurgManager,
    private val context: Any,
    private val scope: CoroutineScope,
) {
    var connectionStatus by mutableStateOf(ConnectionStatus.DISCONNECTED)
        private set

    var deviceSession by mutableStateOf<DeviceSession?>(null)
        private set

    var deviceInfo by mutableStateOf(DeviceInfo())
        private set

    val watchfaces = mutableStateListOf<Watchface>()

    val apps = mutableStateListOf<InstalledApp>()

    var showLogs by mutableStateOf(loadShowLogs(context))
        internal set

    var logCounter by mutableLongStateOf(0L)
        private set

    val logs = mutableStateListOf(
        LogEntry(currentTimeMillis(), "欢迎使用 BandKit - Vela 设备管理工具"),
    )

    val savedDevices = mutableStateListOf<SavedDevice>().also {
        it.addAll(loadSavedDevices(context))
    }

    fun addLog(message: String, type: LogType) {
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

    fun refreshDeviceInfo() {
        val session = deviceSession ?: return
        scope.launch {
            addLog("正在刷新设备信息...", LogType.INFO)
            try {
                val info = withContext(IO) { manager.getDeviceInfo(session) }
                deviceInfo = info
                addLog("设备: ${info.model} (${info.firmwareVersion})", LogType.SUCCESS)
                addLog("电量: ${info.batteryPercent}% | 存储: ${info.totalStorage}", LogType.SUCCESS)
            } catch (e: Exception) {
                addLog("设备信息刷新失败: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun refreshWatchfaces() {
        val session = deviceSession ?: return
        scope.launch {
            addLog("正在刷新表盘列表...", LogType.INFO)
            try {
                val wf = withContext(IO) { manager.getWatchfaceList(session) }
                watchfaces.clear()
                watchfaces.addAll(wf)
                addLog("已加载 ${wf.size} 个表盘", LogType.SUCCESS)
            } catch (e: Exception) {
                addLog("表盘刷新失败: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun refreshApps() {
        val session = deviceSession ?: return
        scope.launch {
            addLog("正在刷新应用列表...", LogType.INFO)
            try {
                val appList = withContext(IO) { manager.getAppList(session) }
                apps.clear()
                apps.addAll(appList)
                addLog("已加载 ${appList.size} 个应用", LogType.SUCCESS)
            } catch (e: Exception) {
                addLog("应用刷新失败: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun disconnect() {
        deviceSession?.let { session ->
            scope.launch {
                addLog("正在断开...", LogType.INFO)
                withContext(IO) { manager.disconnect(session) }
                deviceSession = null
                connectionStatus = ConnectionStatus.DISCONNECTED
                deviceInfo = DeviceInfo()
                watchfaces.clear()
                apps.clear()
                saveLastDevice(context, null)
                addLog("已断开", LogType.SUCCESS)
            }
        }
    }
}
