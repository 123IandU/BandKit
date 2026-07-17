# Android JNI 迁移：Kotlin Core → Native .so 库

> 日期: 2026-07-16  
> 状态: 已设计，待实现

## 目标

用预编译的 `libbandkit_app_android.so` 替代内建编译的 `libcorelib_standalone.so`，将蓝牙连接和协议处理全部下沉到 native 层。Android `BandBurgManager` actual 改为薄适配层，通过 `NativeDevice` JNI 桥接。

## 架构变更

```
迁移前:
App.kt (commonMain) → expect BandBurgManager
                         ↓ actual (androidMain)
                     [RFCOMM套接字管理] → NativeLib (JNI) → libcorelib_standalone.so
                     [读线程 + 响应路由]     (rust-android-gradle 编译)
                     [握手编排]

迁移后:
App.kt (commonMain) → expect BandBurgManager
                         ↓ actual (androidMain)
                     [薄适配层 ~100行] → NativeDevice (JNI) → libbandkit_app_android.so
                                          (预编译 .so, jniLibs)
```

蓝牙连接、L1/L2 协议处理、认证全部下沉到 .so，Kotlin 侧只做 JSON 解析和协程桥接。

## 变更文件清单

### 新增
- `androidApp/src/main/java/com/astrobox/app/NativeDevice.kt` — JNI 绑定对象，按 `ANDROID_JNI_INTEGRATION_GUIDE.md` 模板创建

### 修改
- `shared/src/androidMain/kotlin/com/miband/app/core/BandBurgManager.kt` — 从 ~300 行重写为 ~100 行 NativeDevice 适配层
- `androidApp/build.gradle.kts` — 删除 `cargo` 块、`rust-android-gradle` 插件引用、`dependsOn("cargoBuild")`
- `gradle/libs.versions.toml` — 移除 `rustAndroid` 插件版本（如有）
- `build.gradle.kts` (root) — 移除 `rust-android-gradle` 插件声明（如有）

### 删除
- `shared/src/androidMain/kotlin/com/bandburg/core/NativeLib.kt` — 旧 JNI 绑定

### 不变
- `shared/commonMain/` — expect 接口、App.kt、ProtobufBuilder、ResponseParser 等全部不变
- `shared/src/desktopMain/` + `shared/src/wasmJsMain/` — stub 不变
- `rust/` — 保留为历史参考，不再编译
- `androidApp/src/main/jniLibs/` — .so 文件已就位

## 适配层映射表

| expect 方法 | NativeDevice 调用 | 备注 |
|---|---|---|
| `init()` | `registerEventSink { event, payload -> }` | 当前事件不触发，后续 .so 更新后生效 |
| `connect(name, addr, authkey, connectType)` → `DeviceSession` | `deviceConnect(name, addr, authkey, 2L, "SPP", byteArrayOf(8))` | `withContext(IO)` 同步阻塞调用 |
| `disconnect(session)` | `deviceDisconnect(addr)` | |
| `destroySession(session)` | 同 `disconnect` | |
| `getDeviceInfo(session)` → `DeviceInfo` | `deviceGetData(addr, "info")` → JSON 解析 | 复用 `ResponseParser` |
| `getWatchfaceList(session)` → `List<Watchface>` | `watchfaceGetList(addr)` → JSON 解析 | |
| `setWatchface(session, id)` → `Boolean` | `watchfaceSetCurrent(addr, id)` | |
| `uninstallWatchface(session, id)` → `Boolean` | `watchfaceUninstall(addr, id)` | |
| `getAppList(session)` → `List<InstalledApp>` | `thirdpartyappGetList(addr)` → JSON 解析 | |
| `launchApp(session, pkg)` → `Boolean` | `thirdpartyappLaunch(addr, pkg, "")` | page 参数传空 |
| `uninstallApp(session, pkg)` → `Boolean` | `thirdpartyappUninstall(addr, pkg)` | |
| `installFile(session, name, data, resType, pkg, onProgress)` → `Boolean` | `deviceInstall(addr, resTypeBytes, data, pkg, null, null)` | 进度回调暂不支持 |
| `sendCommand(...)` → `String` | 空实现返回 `""` | 原生命令不再需要 |
| `processReceivedData(...)` → `String` | 空实现返回 `""` | 数据收发已下沉到 .so |

## 关键技术决策

### 同步 JNI → suspend 桥接
`NativeDevice` 所有方法为同步阻塞（内部 tokio runtime），适配层用 `withContext(IO) { ... }` 桥接到协程，复用 `Dispatchers.IO` 线程池。

### addr → DeviceSession 映射
`NativeDevice` 用 `addr: String` 标识设备，expect 用 `DeviceSession(handle: Long, ...)`。适配层维护 `ConcurrentHashMap<String, DeviceSession>`，`connect` 时创建映射，后续通过 `addr` 查找。

### installFile 进度
`NativeDevice.deviceInstall` 的 `progressCb` 参数在 Rust 侧被忽略（`_progress_cb`），传 `null`。安装进度暂时丢失，不影响功能。

### 事件回调
`registerEventSink` 照常调用注册，但 Rust 侧 `emit_event` 当前未被调用（`deviceConnect` 成功后无 `device-connected` 通知）。后续 .so 更新补全后自动生效。

## 已知限制

1. **registerEventSink 不触发** — `emit_event` 函数存在但未被调用，需 Rust 侧在 `deviceConnect` 返回前补 `emit_event("device-connected", ...)`
2. **installFile 无进度** — `progressCb` 参数在 JNI 侧被忽略
3. **sarVersion 硬编码 2L** — 不同设备可能需要不同值
4. **txWinOverrunAllowance 硬编码 byteArrayOf(8)** — 指南示例默认值

## Release .so 编译

Debug .so ~250MB 不可打包 APK。编译 release 版：

```bash
cargo build --release --target aarch64-linux-android -p bandkit-app-android ^
  --manifest-path D:\Resource\app_android\Cargo.toml ^
  --config "target.aarch64-linux-android.linker=..."
```
