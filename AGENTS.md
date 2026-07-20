# BandKit

Kotlin Multiplatform Compose 项目，当前仅 Android 目标活跃。UI 使用 Miuix 库（`top.yukonga.miuix.kmp`）。通过 Rust JNI 实现小米穿戴设备蓝牙 SPP 通信。

## Commands

```bash
# Android
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:assembleRelease
./gradlew :androidApp:buildApks          # bundletool: AAB → .apks
./gradlew :androidApp:installApks        # 通过 bundletool 安装到设备

# Rust native — 自动通过 assembleDebug/Release 触发
./gradlew :androidApp:buildRustLibs      # 仅编译 Rust（3 目标并行）
./gradlew :androidApp:copyRustLibs       # 编译 + 复制 .so 到 build/rust-jni/

# 格式化
./gradlew spotlessApply
./gradlew spotlessCheck

# 编译检查
./gradlew :shared:compileAndroidMain :shared:compileKotlinDesktop
```

Windows 下使用 `gradlew.bat`，或运行 `build.bat` 交互菜单（选项 0-6）。

## Code formatting

Spotless + ktlint + compose rules（`io.nlopez.compose.rules:ktlint:0.6.1`）。提交前必须 `spotlessApply`。

每个 `.kt` 和 `.kts` 文件必须有文件头：

```
// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
```

Composable 函数命名规则已放宽（`@Composable` 函数可以使用任意名称）。

已禁用的 ktlint compose 规则：`modifier-missing-check`、`compositionlocal-allowlist`、`mutable-state-param-check`、`parameter-naming`、`modifier-naming`。

## 项目结构

- `shared/` — KMP 公共代码。`App()` composable 在 `App.kt`，是共享入口点。
  - `commonMain/` — models、核心接口（BandBurgManager、BluetoothScanner）、ProtobufBuilder、App UI（App.kt、AboutScreen、ScriptDoc、PlatformScriptScreen）、ResponseParser、PlatformUtils
  - `androidMain/` — BandBurgManager（NativeDevice JNI 适配器）、BluetoothScanner（BLE）、PlatformUtils 实现、NativeDevice、FilePicker、ScriptScreen（Scripta 编辑器 + 脚本列表）、ScriptRunnerScreen（WebView 运行器 + ScriptBridge）、DeviceExportImportState 单例
  - `desktopMain/` / `wasmJsMain/` — 仅存 stub，未实现
- `androidApp/` — Android 壳（MainActivity、AboutActivity、ScriptRunnerActivity、AndroidManifest、jniLibs），bundletool 构建任务，Rust JNI 自动构建集成
- `desktopApp/` / `webApp/` — 仅存壳，未实现
- `build-plugins/` — 约定插件（`module.spotless`、`module.kotlin-jvm-toolchain`）和 `BuildConfig.kt`（版本号、SDK 版本）
- `rust/` — Rust 本地代码（均为 git submodule）
  - `app_android/` — Android JNI cdylib（`bandkit-app-android`），编译为 `libbandkit_app_android.so`
  - `app_wasm/` — WasmJS binding crate（未使用）
- `third_party/scripta/` — 代码编辑器库，以 composite build 方式引入（`scripta:editor`，仅在 androidMain 使用）
- `spotless/` — `copyright.txt`（Apache 2.0 文件头模板）
- `gradle/libs.versions.toml` — 版本目录

## Key versions

- Gradle 9.6.1（wrapper）、AGP 9.2.1、Kotlin 2.4.0、kotlinx-serialization 1.11.0
- Compose Multiplatform 1.11.1、AndroidX Activity 1.13.0、Spotless 8.6.0
- Miuix UI/Icons/Preference 0.9.3
- Rust edition 2021（crate `bandkit-app-android`）、JDK 21（toolchain）
- Android compileSdk 37、minSdk 24、targetSdk 37、NDK 30.0.14904198
- bundletool 1.18.1（按需下载到 `tools/`）
- ktlint compose rules: `io.nlopez.compose.rules:ktlint:0.6.1`
- ⚠️ README.md 错误地写 minSdk 23，实际为 24（BuildConfig.kt）

## Protocol stack

Xiaomi Vela 穿戴设备通信协议：

- **Layer 1**：包帧，magic `0xA5A5`、type/seq/length/CRC16-ARC
- **Layer 2**：通道路由（Pb=1、Mass=2、Ota=6 等）+ AES-128-CTR 加密
- **Protobuf**：WearPacket，22+ 子系统类型（Account、System、WatchFace、ThirdpartyApp 等）
- **Auth**：HMAC-SHA256 KDF（tag "miwear-auth"）、AES-128-CCM 传输伴侣设备信息

参考实现：AstroBox-NG（`D:\Resource\AstroBox-NG`）。Rust 核心协议 crate：`corelib`（git 依赖自 `AstroBox-NG-Module-Core`）。

## Rust JNI 构建流程

集成在 `androidApp/build.gradle.kts` 中：

1. **编译** — 三个并行 `Exec` 任务 `buildRustLibsArm64v8a`/`Armeabiv7a`/`X86_64` 运行 `cargo build --target <triple> -p bandkit-app-android`
2. **复制** — 三个 `Copy` 任务将 `.so` 从 `rust/app_android/target/<triple>/<mode>/` 复制到 `build/rust-jni/<abi>/`
3. **打包** — `merge{(Debug|Release)}JniLibFolders` 和 `merge{(Debug|Release)}NativeLibs` 依赖 `copyRustLibs`；`afterEvaluate` 将 `build/rust-jni/` 注册为 jniLibs 源目录

Cargo 配置 `.cargo/config.toml` 指定 NDK 链接器路径。`corelib` 是本地路径依赖（`../../AstroBox-NG/src-tauri/modules/core`），必须存在才能编译 Rust。

### JNI 安全规则（Rust 侧）

- **jni 0.21 字节数组：** `get_byte_array_region` / `set_byte_array_region` 操作 `&[i8]` 而非 `&[u8]`，需 `.map(|&b| b as u8)` / `.map(|&b| b as i8)` 转换
- **Pending exceptions：** JNI 调用失败时只能调用 `ExceptionCheck`/`ExceptionOccurred`/`ExceptionClear`
- **跨线程对象使用 GlobalRef：** JNI 局部引用在调用线程之外无效
- 详细规则见 `rust/app_android/CLAUDE.md`

## Miuix 组件使用

- `Scaffold`、`SmallTopAppBar`、`Card`、`Text`、`Button`、`TextField`、`Switch`
- `TabRowWithContour`、`BasicComponent`、`LinearProgressIndicator`、`InfiniteProgressIndicator`
- `OverlayBottomSheet`、`OverlayDropdownPreference`
- `Icon`、`IconButton` from `top.yukonga.miuix.kmp.icon.MiuixIcons`
- 常用图标：`top.yukonga.miuix.kmp.icon.extended.Settings`、`Back`、`Play`、`Add`、`Delete`、`Ok`、`Refresh`、`Home`、`Link`、`Edit`

## 脚本功能（ScriptRunner）

使用 WebView 执行 JavaScript 脚本，通过 `ScriptBridge`（`@JavascriptInterface`）桥接 Android JNI 设备通信。bandburg 兼容的 `sandbox.wasm.*` API。

- `ScriptDoc.kt`（commonMain） — 脚本数据模型
- `ScriptScreen.kt`（androidMain） — Scripta `CodeEditor` 编辑器 + 单文件模式 + 脚本管理对话框 + 在线脚本商店（内嵌 JS）
- `ScriptRunnerScreen.kt`（androidMain） — 单 WebView（脚本执行 + GUI 渲染同一 DOM）+ ScriptBridge + 控制台输出
- `ScriptRunnerActivity.kt`（androidApp） — 接收脚本代码 via Intent，启动 WebView 运行；处理文件选择器回调
- `PlatformScriptScreen.kt`（commonMain + 平台 stub） — expect/actual 入口

脚本 API（与 bandburg 兼容）：
- `sandbox.log/warn/error`、`sandbox.currentDevice`、`sandbox.devices`
- `sandbox.wasm.*` — `miwear_*`、`thirdpartyapp_*`、`watchface_*`、`register_event_sink`
- `sandbox.gui(config)` — DOM 渲染 GUI（`label/input/textarea/select/button/file`），controller 方法：`getValues/getValue/setValue/on/close/show/hide`
- `sandbox.storage` — 内存级 get/set/remove/clear
- `sandbox.utils.hexToBytes`/`bytesToHex`
- `sandbox.saveScript(name, content)` — 保存脚本到 app SharedPreferences（BandKit 扩展）
- 向后兼容：`var bandkit = sandbox`

### 未实现 / 已知限制

- **`sandbox.wasm.miwear_get_file_type`** / **`sandbox.wasm.miwear_install`** — stub 实现，返回空值/ false。
- **RPK 安装体验改进**：安装进度已通过 progress_cb 实时回传（`7aadf81`）。watchfaceId 已修复：先尝试从 .bin 文件提取，失败则用 MD5 前 6 字节生成 12 位数字 ID。
- **设备事件回调**：4 种事件（`device_connected/disconnected`、`thirdpartyapp_message`、`pb_packet`）已通过 Rust JNI → NativeDevice → ScriptBridge → WebView 全链路打通。`thirdpartyapp_message` 的 hex payload 会自动解码为 JSON。事件名从连字符规范化为下划线。详见 [ScriptRunnerScreen.kt](shared/src/androidMain/kotlin/com/bandkit/app/ScriptRunnerScreen.kt) 的 `pushEvent` 和 `_emitEvent`。

## Gotchas

- `kotlin.mpp.applyDefaultHierarchyTemplate=false` — 不要重新启用
- `android.nonTransitiveRClass=true` — 使用完整 R 引用路径
- BuildConfig 在 `build-plugins/src/main/kotlin/BuildConfig.kt`，非自动生成
- JitPack 用于依赖（`jitpack.io` 在 repositories 中）
- 启用配置缓存和并行构建（`gradle.properties`）
- `android.suppressUnsupportedCompileSdk=37.0` 在 `gradle.properties`
- ktlint `modifier-clickable-order`：`.clickable` 必须在 `.background(shape)` 之后
- `expect`/`actual` 类（`BandBurgManager`、`BluetoothScanner`）—— 编辑 common 代码需同步更新 desktop/wasm stub
- `kotlinx-serialization` 在 commonMain 使用（`SavedDevice` 是 `@Serializable`）；不要移除插件
- AndroidManifest 声明蓝牙权限（`BLUETOOTH`、`BLUETOOTH_CONNECT`、`BLUETOOTH_SCAN`、`ACCESS_FINE_LOCATION` 等）
- Miuix `Text` 没有 `onClick` 参数 — 使用 `Modifier.clickable`
- Miuix `OverlayBottomSheet` 需要 `Scaffold` 提供 `MiuixPopupHost`
- 蓝牙 SPP UUID：`00001101-0000-1000-8000-00805F9B34FB`
- Auth 流程：SPP hello → L1StartReq → AuthStep1 → 等待 DeviceVerify → nativeHandleAuthStep2 → AppConfirm → L2 cipher 激活
- 文件安装 MASS 协议：`comp_data(0x00) | data_type(1B) | md5(16B) | length(4B LE) | data | crc32(4B LE)`
- 表盘安装用 `PrepareInstallWatchFace`（type=4, id=4），第三方应用用 `PrepareInstallApp`（type=20, id=1）
- Rust 源文件必须是 UTF-8 编码
- Rust `Exec` 任务并行执行，可能竞争 Cargo 包锁；串行执行正常
- Android → KMP 桥接：`DeviceExportImportState` 单例持有 Activity result launcher，由 `MainActivity.onCreate` 设置
- `MainActivity` 使用 `enableEdgeToEdge()`、`WindowCompat.setDecorFitsSystemWindows(window, false)`、运行时申请 BLE 权限（SDK ≥ S）
- 共享入口点：`com.bandkit.app` 包中的 `App()` composable（由 `MainActivity.setContent` 调用）
- `rust/app_android` 和 `rust/app_wasm` 都是 git submodule — `git submodule update --init --recursive` 初始化
- `third_party/scripta` 是 composite build（`includeBuild("third_party/scripta")` 在 settings.gradle.kts）
- `ScriptRunnerActivity` 使用 `Class.forName` 方式在 shared 模块中引用 — 修改 Activity 类名需同步更新 `ScriptScreen.kt` 中的字符串

- `@JavascriptInterface` 方法在 WebView 后台线程执行 — 修改 Compose 状态必须通过 `Handler(Looper.getMainLooper()).post {}` 切到主线程，否则不会触发重组
- WebView `<input type="file">` 需要 `WebChromeClient.onShowFileChooser` + Activity `onActivityResult` 回调
- 内嵌在线脚本商店通过 assets `script_store.js` 加载，商店脚本通过 `sandbox.saveScript()` 写入 SharedPreferences，脚本页通过 `LifecycleEventObserver ON_RESUME` 刷新列表
- WebView `loadDataWithBaseURL` 的 base URL 设为 `"https://bandkit.app"`（非 null），否则文档 origin 为 null 导致 `localStorage` 被安全策略拒绝。`domStorageEnabled` 已开启

## Notes
