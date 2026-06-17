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
- `androidApp/` — Android app, depends on `:shared`
- `desktopApp/` — Desktop JVM app, depends on `:shared`, entry `MainKt`
- `webApp/` — WasmJS app, depends on `:shared`
- `build-plugins/` — Convention plugins (`module.spotless`, `module.kotlin-jvm-toolchain`) and `BuildConfig.kt`
- `rust/` — Rust native code (corelib-standalone + pb protobuf library)
- `gradle/libs.versions.toml` — Version catalog

## Key versions

- Gradle 9.5.1, AGP 9.2.1, Kotlin 2.4.0
- Compose Multiplatform 1.11.1
- Rust 1.90.0+ (edition 2024), JDK 21 (toolchain)
- Android compileSdk 37, minSdk 23

## Gotchas

- `kotlin.mpp.applyDefaultHierarchyTemplate=false` is set — don't re-enable it
- `android.nonTransitiveRClass=true` — use qualified R references
- BuildConfig lives in `build-plugins/src/main/kotlin/BuildConfig.kt`, not generated
- JitPack is used for dependencies (`jitpack.io` in repositories)
- Configuration cache and parallel builds are enabled in `gradle.properties`
- Rust targets must be installed: `rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android`
