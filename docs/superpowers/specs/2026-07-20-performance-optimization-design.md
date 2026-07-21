# 性能优化设计文档

**日期**: 2026-07-20
**目标**: 在不改变 app 功能的情况下，优化 app 的性能

## 背景

项目当前存在以下性能问题：
- `AppContent` 单一 composable 包含 23+ 个 state，任何变化都触发整体重组
- LazyColumn 缺少 `key` 参数，列表变化时无法高效追踪
- `addLog`、`connectToDevice` 等 lambda 每次重组都重新创建
- 事件回调注册后没有清理，可能导致内存泄漏
- 文件读取、JSON 解析在主线程执行
- `SimpleDateFormat` 每次调用都新建
- `getDeviceInfo` 三次 JNI 调用分别切换协程上下文

## 方案选择

采用 **方案 B：UI 重构 + 快速修复**，在性能提升与风险之间取得平衡。

---

## 第 1 部分：LazyColumn Keys 和快速修复

### 1.1 添加 LazyColumn key 参数

为所有 `items()` 调用添加 `key` 参数，让 Compose 高效追踪列表变化：

| 文件 | 当前 | 修复 |
|------|------|------|
| `WatchfaceSection.kt:98` | `items(watchfaces.size)` | `items(watchfaces, key = { it.id })` |
| `AppSection.kt:99` | `items(apps.size)` | `items(apps, key = { it.id })` |
| `DeviceStatusBar.kt:131` | `items(savedDevices)` | `items(savedDevices, key = { it.id })` |
| `AddDeviceBottomSheet.kt:134` | `items(scannedDevices)` | `items(scannedDevices, key = { it.address })` |
| `ScriptScreen.kt:281` | `items(scripts)` | `items(scripts, key = { it.id })` |

### 1.2 缓存 SimpleDateFormat

`PlatformUtils.kt:19` 每次调用都创建新的 `SimpleDateFormat`。改为 `ThreadLocal` 缓存：

```kotlin
private val timestampFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
}
actual fun formatTimestamp(timestamp: Long): String =
    timestampFormatter.get()!!.format(Date(timestamp))
```

### 1.3 合并 getDeviceInfo 的 withContext 块

`BandBurgManager.kt:97-114` 三次 `withContext(Dispatchers.IO)` 合并为一次，减少协程切换开销：

```kotlin
actual suspend fun getDeviceInfo(session: DeviceSession): DeviceInfo {
    return withContext(Dispatchers.IO) {
        val addr = session.device.addr
        val infoJson = NativeDevice.deviceGetData(addr, "info")
        val statusJson = NativeDevice.deviceGetData(addr, "status")
        val storageJson = NativeDevice.deviceGetData(addr, "storage")
        ResponseParser.parseDeviceInfo(infoJson, statusJson, storageJson, session.device.name)
    }
}
```

### 1.4 稳定 lambda

- `addLog`：用 `remember(showLogs)` 包装
- `connectToDevice`：用 `remember` 包装
- `onUpdate` 回调：用 `remember` 包装

---

## 第 2 部分：UI 重组优化

### 2.1 提取 AppContentState

将共享状态提取到独立的 `@Stable` 类：

```kotlin
@Stable
class AppContentState {
    var connectionStatus = mutableStateOf(ConnectionStatus.DISCONNECTED)
    var deviceInfo = mutableStateOf<DeviceInfo?>(null)
    var deviceSession = mutableStateOf<DeviceSession?>(null)
    val watchfaces = mutableStateListOf<Watchface>()
    val apps = mutableStateListOf<InstalledApp>()
    val logs = mutableStateListOf<LogEntry>()
    var logCounter = mutableIntStateOf(0)
    var showLogs = mutableStateOf(false)
    val savedDevices = mutableStateListOf<SavedDevice>()
    
    fun addLog(message: String, type: LogType) { ... }
    fun connectToDevice(device: SavedDevice, scope: CoroutineScope) { ... }
    fun refreshWatchfaces(manager: BandBurgManager, session: DeviceSession) { ... }
    fun refreshApps(manager: BandBurgManager, session: DeviceSession) { ... }
}
```

### 2.2 提取 Tab 内容为独立 Composable

将每个 tab 的内容提取为独立 composable：

| 新文件 | 内容 |
|--------|------|
| `DeviceTabContent.kt` | 设备信息、连接状态 |
| `WatchfaceTabContent.kt` | 表盘列表、管理 |
| `AppTabContent.kt` | 应用列表、管理 |
| `InstallTabContent.kt` | 文件安装、日志 |

### 2.3 优化 LogSection

- 使用 `reverseLayout = true` 避免 `add(0, ...)` 的性能问题
- 添加 `contentType` 参数优化重组
- 防抖自动滚动（`snapshotFlow` + `debounce`）

---

## 第 3 部分：内存和 IO 优化

### 3.1 修复事件回调泄漏

`ScriptRunnerScreen.kt:346-382` 注册事件回调后没有清理。添加 `DisposableEffect`：

```kotlin
DisposableEffect(session) {
    NativeDevice.registerEventSink(...)
    onDispose { NativeDevice.unregisterEventSink() }
}
```

### 3.2 主线程 IO 修复

| 文件 | 问题 | 修复 |
|------|------|------|
| `PlatformUtils.kt:179` | `handleDeviceImportResult` 在主线程读文件 | 移到 `Dispatchers.IO` |
| `FilePicker.kt:59` | `handleFilePickerResult` 在主线程读文件 | 移到 `Dispatchers.IO` |
| `ScriptScreen.kt:350,369` | 资源文件读取在主线程 | 移到 `scope.launch(Dispatchers.IO)` |
| `App.kt:145` | `loadSavedDevices` 在主线程解析 JSON | 移到 `LaunchedEffect` |

### 3.3 清理 ScriptBridge 资源

- `consoleBuffer` 在 `DisposableEffect` 中清理
- `hexToBytes` 中的 Regex 预编译为常量
- JS bridge 注入添加防重复检查

### 3.4 优化 LogSection 滚动

使用 `snapshotFlow` + `debounce` 替代 `LaunchedEffect(logCount)`：

```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { logs.size }
        .debounce(300)
        .collect { if (isNearTop) listState.animateScrollToItem(0) }
}
```

---

## 执行顺序

1. **快速修复** (第 1 部分) — 低风险，先做
2. **UI 重组优化** (第 2 部分) — 核心改动
3. **内存和 IO 优化** (第 3 部分) — 收尾工作
4. **验证构建** — 确保所有改动后项目仍可编译

## 验证方式

- `./gradlew :shared:compileAndroidMain` — 验证 shared 模块编译
- `./gradlew :androidApp:assembleDebug` — 验证完整 APK 构建
- `./gradlew spotlessCheck` — 验证代码格式

## 风险控制

- 每个部分独立完成，可单独验证
- 保持所有公共 API 不变
- 不修改业务逻辑，只优化性能
- 拆分时保持完整的导入关系
