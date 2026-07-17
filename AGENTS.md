# BandKit

Kotlin Multiplatform Compose project targeting Android, Desktop (JVM), and Web (WasmJS).
UI uses Miuix library (`top.yukonga.miuix.kmp`). Bluetooth SPP communication with Xiaomi wearable devices via Rust JNI.

## Build commands

```bash
# Android
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:assembleRelease

# Desktop
./gradlew :desktopApp:run
./gradlew :desktopApp:packageExe

# Web (WasmJS)
./gradlew :webApp:wasmJsBrowserDevelopmentRun

# Rust native (Android only) — 自动通过 assembleDebug/Release 触发
./gradlew :androidApp:buildRustLibs      # 仅编译 Rust（3 目标并行）
./gradlew :androidApp:copyRustLibs       # 编译 + 复制到 build/rust-jni/

# Formatting (runs on all modules)
./gradlew spotlessApply
./gradlew spotlessCheck

# Compile check (all targets)
./gradlew :shared:compileKotlinDesktop :shared:compileAndroidMain :shared:compileKotlinWasmJs
```

Windows: use `gradlew.bat` or run `build.bat` for an interactive menu.

## Code formatting

Spotless with ktlint + compose rules is enforced. Run `spotlessApply` before committing.

Every `.kt` and `.kts` file must have this header:

```
// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
```

Kotlin Compose function naming rules are relaxed (`@Composable` functions can use any name).

## Project structure

- `shared/` — KMP common code. All platform apps depend on this. `App.kt` is the shared entrypoint.
  - `commonMain/` — models, core interfaces (BandBurgManager, BluetoothScanner, FileDetector), ProtobufBuilder, App UI, ResponseParser, PlatformUtils
  - `androidMain/` — BandBurgManager (NativeDevice JNI adapter), BluetoothScanner (BLE), PlatformUtils/PlatformContext actuals
  - `desktopMain/` / `wasmJsMain/` — stubs for BandBurgManager, BluetoothScanner, PlatformUtils
- `androidApp/` — Android shell (MainActivity, AndroidManifest, jniLibs), bundletool build tasks. Rust JNI auto-build integration in build.gradle.kts
- `desktopApp/` — Desktop JVM app, depends on `:shared`, entry `MainKt`
- `webApp/` — WasmJS app, depends on `:shared`
- `build-plugins/` — Convention plugins (`module.spotless`, `module.kotlin-jvm-toolchain`) and `BuildConfig.kt`
- `rust/` — Rust native code
  - `app_android/` — Android JNI cdylib (`bandkit-app-android`), compiles to `libbandkit_app_android.so`
  - `app_wasm/` — WasmJS binding crate
- `gradle/libs.versions.toml` — Version catalog

## Key versions

- Gradle 9.5.1, AGP 9.2.1, Kotlin 2.4.0, kotlinx-serialization 1.11.0
- Compose Multiplatform 1.11.1, AndroidX Activity 1.13.0
- Miuix UI/Icons/Preference 0.9.3 (hardcoded in androidApp/build.gradle.kts)
- Rust edition 2021 (crate `bandkit-app-android`), JDK 21 (toolchain)
- Android compileSdk 37, minSdk 23, targetSdk 37, NDK 30.0.14904198
- bundletool 1.18.1 (downloaded on demand to `tools/`)

## Protocol stack

The project implements the Xiaomi Vela wearable device communication protocol:

- **Layer 1 (L1)**: Packet framing with magic `0xA5A5`, type/seq/length/CRC16-ARC
- **Layer 2 (L2)**: Channel routing (Pb=1, Mass=2, Ota=6, etc.) with AES-128-CTR encryption
- **Protobuf**: WearPacket with 22+ subsystem types (Account, System, WatchFace, ThirdpartyApp, etc.)
- **Auth**: HMAC-SHA256 KDF with tag "miwear-auth", AES-128-CCM for companion device info

Reference implementation: AstroBox-NG (`D:\Resource\AstroBox-NG`). Rust core protocol crate: `corelib` (git dep from `AstroBox-NG-Module-Core`).

## Rust JNI build pipeline

集成在 `androidApp/build.gradle.kts` 中，配置缓存兼容：

1. **编译** — 三个 `Exec` 任务 `buildRustLibsArm64v8a`/`Armeabiv7a`/`X86_64` 并行运行 `cargo build --target <triple> -p bandkit-app-android`，工作目录 `rust/app_android/`
2. **复制** — 三个 `Copy` 任务 `copyRustLibsArm64v8a`/`Armeabiv7a`/`X86_64` 将 `.so` 从 `rust/app_android/target/<triple>/<mode>/` 复制到 `build/rust-jni/<abi>/`
3. **打包** — `mergeDebugJniLibFolders`/`mergeReleaseJniLibFolders` 依赖 `copyRustLibs`，自动打包进 APK

Cargo 配置 `.cargo/config.toml` 指定 NDK 链接器路径。

## Miuix components used

- `Scaffold`, `SmallTopAppBar`, `Card`, `Text`, `Button`, `TextField`, `Switch`
- `TabRowWithContour`, `BasicComponent`, `LinearProgressIndicator`, `InfiniteProgressIndicator`
- `OverlayBottomSheet`, `OverlayDropdownPreference`
- `Icon`, `IconButton` from `top.yukonga.miuix.kmp.icon.MiuixIcons`
- Import path: `top.yukonga.miuix.kmp.icon.extended.Settings`, `top.yukonga.miuix.kmp.icon.extended.Back`

## Gotchas

- `kotlin.mpp.applyDefaultHierarchyTemplate=false` is set — don't re-enable it
- `android.nonTransitiveRClass=true` — use qualified R references
- BuildConfig lives in `build-plugins/src/main/kotlin/BuildConfig.kt`, not generated
- JitPack is used for dependencies (`jitpack.io` in repositories)
- Configuration cache and parallel builds are enabled in `gradle.properties`
- ktlint `modifier-clickable-order` rule: `.clickable` must come AFTER `.background(shape)`, not before
- `expect`/`actual` classes (`BandBurgManager`, `BluetoothScanner`) — editing common code requires updating desktop/wasm stubs too
- `kotlinx-serialization` is used in commonMain (`SavedDevice` is `@Serializable`); don't remove the plugin
- AndroidManifest declares Bluetooth permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`) — needed for BLE scanning
- `java.io.ByteArrayOutputStream`/`DataOutputStream` not available in WasmJS — use `ByteArrayBuilder` pattern in commonMain
- `System.currentTimeMillis()` not available in WasmJS — use `currentTimeMillis()` from `PlatformUtils`
- `Dispatchers.IO` not available in WasmJS — use `IO` from `PlatformUtils` (falls back to `Dispatchers.Default`)
- `org.json.*` not available in WasmJS — use `kotlinx.serialization.json` in commonMain
- Miuix `Text` has no `onClick` parameter — use `Modifier.clickable` instead
- Miuix `OverlayBottomSheet` requires `Scaffold` providing `MiuixPopupHost` to render
- Bluetooth SPP uses UUID `00001101-0000-1000-8000-00805F9B34FB`
- Auth flow: SPP hello → L1StartReq → AuthStep1 → wait DeviceVerify → nativeHandleAuthStep2 → AppConfirm → L2 cipher active
- File install MASS protocol: `comp_data(0x00) | data_type(1B) | md5(16B) | length(4B LE) | data | crc32(4B LE)`
- Watchface install uses `PrepareInstallWatchFace` (type=4, id=4), ThirdPartyApp uses `PrepareInstallApp` (type=20, id=1)
- Rust source files MUST be UTF-8 (no GBK/other encoding) — rustc requires it. Use `/// English` comments.
- Rust `Exec` tasks run parallelly and can contend for Cargo package lock; sequential execution is normal.
