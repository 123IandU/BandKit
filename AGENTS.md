# Miband

Kotlin Multiplatform Compose project targeting Android, Desktop (JVM), and Web (WasmJS). UI uses Miuix library (`top.yukonga.miuix.kmp`).

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

# Rust native (Android only, triggered by assembleDebug/Release)
./gradlew :androidApp:cargoBuild

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
  - `commonMain/` — shared models, core interfaces, ProtobufBuilder, App UI, ResponseParser, FileDetector
  - `androidMain/` — BandBurgManager (Bluetooth + NativeLib), BluetoothScanner (BLE), NativeLib JNI, PlatformUtils/PlatformContext actuals
  - `desktopMain/` / `wasmJsMain/` — stubs for BandBurgManager, BluetoothScanner, PlatformUtils
- `androidApp/` — Android shell (MainActivity), also has `cargo` block for Rust build, Preview.kt
- `desktopApp/` — Desktop JVM app, depends on `:shared`, entry `MainKt`
- `webApp/` — WasmJS app, depends on `:shared`
- `build-plugins/` — Convention plugins (`module.spotless`, `module.kotlin-jvm-toolchain`) and `BuildConfig.kt`
- `rust/` — Rust native code (corelib-standalone + pb protobuf library)
- `gradle/libs.versions.toml` — Version catalog

## Key versions

- Gradle 9.5.1, AGP 9.2.1, Kotlin 2.4.0
- Compose Multiplatform 1.11.1
- Miuix UI/Icons/Preference 0.9.2
- Rust 1.90.0+ (edition 2024), JDK 21 (toolchain)
- Android compileSdk 37, minSdk 23, NDK 30.0.14904198

## Protocol stack

The project implements the Xiaomi Vela wearable device communication protocol:

- **Layer 1 (L1)**: Packet framing with magic `0xA5A5`, type/seq/length/CRC16-ARC
- **Layer 2 (L2)**: Channel routing (Pb=1, Mass=2, Ota=6, etc.) with AES-128-CTR encryption
- **Protobuf**: WearPacket with 22+ subsystem types (Account, System, WatchFace, ThirdpartyApp, etc.)
- **Auth**: HMAC-SHA256 KDF with tag "miwear-auth", AES-128-CCM for companion device info

Reference implementation: AstroBox-NG (`D:\Resource\AstroBox-NG`)

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
- Rust targets must be installed: `rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android`
- Rust build requires Visual Studio Build Tools with "C++ Desktop development" workload — MSYS2's `link.exe` in PATH will break cargo builds
- `net.mullvad.rust-android` plugin (v0.10.1) manages Rust cross-compilation; `cargo` block config is in `androidApp/build.gradle.kts`
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
- Reference projects: BandBurg web app (`D:\Resource\bandburg`), AstroBox-NG Rust lib (`D:\Resource\AstroBox-NG`)
