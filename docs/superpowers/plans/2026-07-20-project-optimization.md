# Project Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize code and project file structure while preserving existing functionality

**Architecture:** Split large files by feature, centralize build configuration, clean dead code

**Tech Stack:** Kotlin Multiplatform Compose, Miuix UI, Rust JNI, Gradle

## Global Constraints

- Do not modify desktopApp or webApp modules
- Keep all public APIs unchanged
- Preserve existing business logic
- Maintain copyright headers on all .kt and .kts files
- Follow existing code style (Chinese comments, Miuix components)

---

## File Structure

### Build Configuration Changes

| File | Action | Responsibility |
|------|--------|----------------|
| `gradle/libs.versions.toml` | Modify | Add Miuix version and library entries |
| `androidApp/build.gradle.kts` | Modify | Use version catalog for Miuix |
| `shared/build.gradle.kts` | Modify | Use version catalog for Miuix |
| `rust/app_android/.cargo/config.toml` | Modify | Fix NDK version |
| `rust/app_android/build-all.bat` | Delete | Remove stale script |

### Code Structure Changes

| File | Action | Responsibility |
|------|--------|----------------|
| `shared/src/commonMain/kotlin/com/bandkit/app/App.kt` | Modify | Keep only App() and AppContent() |
| `shared/src/commonMain/kotlin/com/bandkit/app/DeviceStatusBar.kt` | Create | DeviceStatusBar, SavedDevicesBottomSheet |
| `shared/src/commonMain/kotlin/com/bandkit/app/AddDeviceBottomSheet.kt` | Create | AddDeviceBottomSheet |
| `shared/src/commonMain/kotlin/com/bandkit/app/WatchfaceSection.kt` | Create | WatchfaceSection |
| `shared/src/commonMain/kotlin/com/bandkit/app/AppSection.kt` | Create | AppSection |
| `shared/src/commonMain/kotlin/com/bandkit/app/InstallSection.kt` | Create | InstallSection, LogSection, helpers |
| `shared/src/androidMain/kotlin/com/bandkit/app/ScriptBridgeJs.kt` | Create | JS bridge string generation |
| `shared/src/androidMain/kotlin/com/bandkit/app/ScriptRunnerScreen.kt` | Modify | Remove JS bridge code |
| `shared/src/commonMain/kotlin/com/bandkit/app/core/PlatformUtils.kt` | Modify | Add formatFileSize, parseStoragePercent |
| `shared/src/androidMain/kotlin/com/bandkit/app/core/PlatformUtils.kt` | Modify | Remove DeviceExportImportState |
| `shared/src/androidMain/kotlin/com/bandkit/app/core/DeviceExportImportState.kt` | Create | DeviceExportImportState singleton |

### Cleanup Changes

| File | Action | Responsibility |
|------|--------|----------------|
| `shared/src/androidMain/kotlin/com/bandkit/app/highlight/` | Delete | Remove empty directory |
| `README.md` | Modify | Fix minSdk from 23 to 24 |
| `shared/src/androidMain/kotlin/com/bandkit/app/core/BluetoothScanner.kt` | Modify | Move TAG to companion object |

---

## Task 1: Centralize Miuix Versions in Version Catalog

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `androidApp/build.gradle.kts:14-16`
- Modify: `shared/build.gradle.kts:38-40`

**Interfaces:**
- Consumes: None (first task)
- Produces: `libs.miuix.ui`, `libs.miuix.icons`, `libs.miuix.preference` version catalog entries

- [ ] **Step 1: Add Miuix entries to libs.versions.toml**

Add to `gradle/libs.versions.toml`:

```toml
[versions]
miuix = "0.9.3"

[libraries]
miuix-ui = { module = "top.yukonga.miuix.kmp:miuix-ui", version.ref = "miuix" }
miuix-icons = { module = "top.yukonga.miuix.kmp:miuix-icons", version.ref = "miuix" }
miuix-preference = { module = "top.yukonga.miuix.kmp:miuix-preference", version.ref = "miuix" }
```

- [ ] **Step 2: Update androidApp/build.gradle.kts**

Replace lines 14-16:
```kotlin
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
```

With:
```kotlin
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
```

- [ ] **Step 3: Update shared/build.gradle.kts**

Replace lines 38-40:
```kotlin
            implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
            implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.3")
            implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
```

With:
```kotlin
            implementation(libs.miuix.ui)
            implementation(libs.miuix.icons)
            implementation(libs.miuix.preference)
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :shared:compileAndroidMain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml androidApp/build.gradle.kts shared/build.gradle.kts
git commit -m "build: centralize Miuix versions in version catalog"
```

---

## Task 2: Fix NDK Version Mismatch

**Files:**
- Modify: `rust/app_android/.cargo/config.toml`

**Interfaces:**
- Consumes: None
- Produces: Aligned NDK version

- [ ] **Step 1: Update NDK version in Cargo config**

Open `rust/app_android/.cargo/config.toml` and find all occurrences of `30.0.15729638`. Replace with `30.0.14904198`.

- [ ] **Step 2: Verify Rust build (optional)**

If NDK 30.0.14904198 is installed:
Run: `cd rust/app_android && cargo build --target aarch64-linux-android -p bandkit-app-android`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add rust/app_android/.cargo/config.toml
git commit -m "build: align NDK version to 30.0.14904198"
```

---

## Task 3: Delete Stale build-all.bat

**Files:**
- Delete: `rust/app_android/build-all.bat`

**Interfaces:**
- Consumes: None
- Produces: Cleaner project root

- [ ] **Step 1: Delete the file**

```bash
git rm rust/app_android/build-all.bat
```

- [ ] **Step 2: Commit**

```bash
git commit -m "build: remove stale build-all.bat"
```

---

## Task 4: Move Utility Functions to PlatformUtils

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/bandkit/app/core/PlatformUtils.kt`
- Modify: `shared/src/commonMain/kotlin/com/bandkit/app/App.kt`

**Interfaces:**
- Consumes: None
- Produces: `formatFileSize()` and `parseStoragePercent()` in PlatformUtils

- [ ] **Step 1: Add formatFileSize to PlatformUtils.kt (commonMain)**

Add to `shared/src/commonMain/kotlin/com/bandkit/app/core/PlatformUtils.kt`:

```kotlin
fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

fun parseStoragePercent(used: Long, total: Long): Float {
    if (total == 0L) return 0f
    return (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
```

- [ ] **Step 2: Remove functions from App.kt**

Delete the `formatFileSize` and `parseStoragePercent` functions from `App.kt` (around lines 115-143).

- [ ] **Step 3: Add import to App.kt**

Add at the top of `App.kt`:
```kotlin
import com.bandkit.app.core.formatFileSize
import com.bandkit.app.core.parseStoragePercent
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :shared:compileAndroidMain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/bandkit/app/core/PlatformUtils.kt shared/src/commonMain/kotlin/com/bandkit/app/App.kt
git commit -m "refactor: move utility functions to PlatformUtils"
```

---

## Task 5: Extract DeviceExportImportState

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/bandkit/app/core/PlatformUtils.kt`
- Create: `shared/src/androidMain/kotlin/com/bandkit/app/core/DeviceExportImportState.kt`

**Interfaces:**
- Consumes: None
- Produces: Standalone `DeviceExportImportState` singleton

- [ ] **Step 1: Create DeviceExportImportState.kt**

Create `shared/src/androidMain/kotlin/com/bandkit/app/core/DeviceExportImportState.kt`:

```kotlin
// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

object DeviceExportImportState {
    private var exportLauncher: ActivityResultLauncher<Intent>? = null
    private var importLauncher: ActivityResultLauncher<Array<String>>? = null
    private var onExportResult: ((String?) -> Unit)? = null
    private var onImportResult: ((String?) -> Unit)? = null

    fun setup(activity: Activity) {
        exportLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                onExportResult?.invoke(uri?.toString())
            } else {
                onExportResult?.invoke(null)
            }
        }

        importLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            onImportResult?.invoke(uri?.toString())
        }
    }

    fun exportJson(content: String, fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        exportLauncher?.launch(intent)
    }

    fun importJson(onResult: (String?) -> Unit) {
        onImportResult = onResult
        importLauncher?.launch(arrayOf("application/json"))
    }
}
```

- [ ] **Step 2: Remove DeviceExportImportState from PlatformUtils.kt**

Delete the `DeviceExportImportState` object from `shared/src/androidMain/kotlin/com/bandkit/app/core/PlatformUtils.kt`.

- [ ] **Step 3: Update imports in files that use DeviceExportImportState**

Files that import `DeviceExportImportState` from `PlatformUtils` need to be updated to import from the new file. Check `ScriptRunnerScreen.kt` and `ScriptScreen.kt`.

- [ ] **Step 4: Verify build**

Run: `./gradlew :shared:compileAndroidMain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add shared/src/androidMain/kotlin/com/bandkit/app/core/DeviceExportImportState.kt shared/src/androidMain/kotlin/com/bandkit/app/core/PlatformUtils.kt
git commit -m "refactor: extract DeviceExportImportState to standalone file"
```

---

## Task 6: Delete Empty highlight/ Directory

**Files:**
- Delete: `shared/src/androidMain/kotlin/com/bandkit/app/highlight/` (empty directory)

**Interfaces:**
- Consumes: None
- Produces: Cleaner project structure

- [ ] **Step 1: Remove the empty directory**

```bash
git rm -r shared/src/androidMain/kotlin/com/bandkit/app/highlight/
```

- [ ] **Step 2: Commit**

```bash
git commit -m "chore: remove empty highlight directory"
```

---

## Task 7: Split App.kt - Extract DeviceStatusBar

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/bandkit/app/App.kt`
- Create: `shared/src/commonMain/kotlin/com/bandkit/app/DeviceStatusBar.kt`

**Interfaces:**
- Consumes: `formatFileSize`, `parseStoragePercent` from Task 4
- Produces: `DeviceStatusBar` and `SavedDevicesBottomSheet` composables

- [ ] **Step 1: Read App.kt to identify DeviceStatusBar and SavedDevicesBottomSheet**

Read `App.kt` and locate:
- `DeviceStatusBar` composable (around line 624)
- `SavedDevicesBottomSheet` composable (around line 676)

- [ ] **Step 2: Create DeviceStatusBar.kt**

Create `shared/src/commonMain/kotlin/com/bandkit/app/DeviceStatusBar.kt` with the extracted composables. Include all necessary imports.

- [ ] **Step 3: Remove composables from App.kt**

Delete `DeviceStatusBar` and `SavedDevicesBottomSheet` from `App.kt`.

- [ ] **Step 4: Add imports to App.kt**

Add at the top of `App.kt`:
```kotlin
import com.bandkit.app.DeviceStatusBar
import com.bandkit.app.SavedDevicesBottomSheet
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :shared:compileAndroidMain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/bandkit/app/DeviceStatusBar.kt shared/src/commonMain/kotlin/com/bandkit/app/App.kt
git commit -m "refactor: extract DeviceStatusBar from App.kt"
```

---

## Task 8: Split App.kt - Extract AddDeviceBottomSheet

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/bandkit/app/App.kt`
- Create: `shared/src/commonMain/kotlin/com/bandkit/app/AddDeviceBottomSheet.kt`

**Interfaces:**
- Consumes: None
- Produces: `AddDeviceBottomSheet` composable

- [ ] **Step 1: Read App.kt to identify AddDeviceBottomSheet**

Read `App.kt` and locate `AddDeviceBottomSheet` composable (around line 741).

- [ ] **Step 2: Create AddDeviceBottomSheet.kt**

Create `shared/src/commonMain/kotlin/com/bandkit/app/AddDeviceBottomSheet.kt` with the extracted composable. Include all necessary imports.

- [ ] **Step 3: Remove composable from App.kt**

Delete `AddDeviceBottomSheet` from `App.kt`.

- [ ] **Step 4: Add imports to App.kt**

Add at the top of `App.kt`:
```kotlin
import com.bandkit.app.AddDeviceBottomSheet
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :shared:compileAndroidMain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/bandkit/app/AddDeviceBottomSheet.kt shared/src/commonMain/kotlin/com/bandkit/app/App.kt
git commit -m "refactor: extract AddDeviceBottomSheet from App.kt"
```

---

## Task 9: Split App.kt - Extract WatchfaceSection

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/bandkit/app/App.kt`
- Create: `shared/src/commonMain/kotlin/com/bandkit/app/WatchfaceSection.kt`

**Interfaces:**
- Consumes: None
- Produces: `WatchfaceSection` composable

- [ ] **Step 1: Read App.kt to identify WatchfaceSection**

Read `App.kt` and locate `WatchfaceSection` composable (around line 873).

- [ ] **Step 2: Create WatchfaceSection.kt**

Create `shared/src/commonMain/kotlin/com/bandkit/app/WatchfaceSection.kt` with the extracted composable. Include all necessary imports.

- [ ] **Step 3: Remove composable from App.kt**

Delete `WatchfaceSection` from `App.kt`.

- [ ] **Step 4: Add imports to App.kt**

Add at the top of `App.kt`:
```kotlin
import com.bandkit.app.WatchfaceSection
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :shared:compileAndroidMain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/bandkit/app/WatchfaceSection.kt shared/src/commonMain/kotlin/com/bandkit/app/App.kt
git commit -m "refactor: extract WatchfaceSection from App.kt"
```

---

## Task 10: Split App.kt - Extract AppSection

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/bandkit/app/App.kt`
- Create: `shared/src/commonMain/kotlin/com/bandkit/app/AppSection.kt`

**Interfaces:**
- Consumes: None
- Produces: `AppSection` composable

- [ ] **Step 1: Read App.kt to identify AppSection**

Read `App.kt` and locate `AppSection` composable (around line 982).

- [ ] **Step 2: Create AppSection.kt**

Create `shared/src/commonMain/kotlin/com/bandkit/app/AppSection.kt` with the extracted composable. Include all necessary imports.

- [ ] **Step 3: Remove composable from App.kt**

Delete `AppSection` from `App.kt`.

- [ ] **Step 4: Add imports to App.kt**

Add at the top of `App.kt`:
```kotlin
import com.bandkit.app.AppSection
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :shared:compileAndroidMain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/bandkit/app/AppSection.kt shared/src/commonMain/kotlin/com/bandkit/app/App.kt
git commit -m "refactor: extract AppSection from App.kt"
```

---

## Task 11: Split App.kt - Extract InstallSection

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/bandkit/app/App.kt`
- Create: `shared/src/commonMain/kotlin/com/bandkit/app/InstallSection.kt`

**Interfaces:**
- Consumes: None
- Produces: `InstallSection`, `LogSection` composables, and helper functions

- [ ] **Step 1: Read App.kt to identify InstallSection and LogSection**

Read `App.kt` and locate:
- `InstallSection` composable (around line 1084)
- `LogSection` composable (around line 1201)

- [ ] **Step 2: Create InstallSection.kt**

Create `shared/src/commonMain/kotlin/com/bandkit/app/InstallSection.kt` with the extracted composables. Include all necessary imports.

- [ ] **Step 3: Remove composables from App.kt**

Delete `InstallSection` and `LogSection` from `App.kt`.

- [ ] **Step 4: Add imports to App.kt**

Add at the top of `App.kt`:
```kotlin
import com.bandkit.app.InstallSection
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :shared:compileAndroidMain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/bandkit/app/InstallSection.kt shared/src/commonMain/kotlin/com/bandkit/app/App.kt
git commit -m "refactor: extract InstallSection from App.kt"
```

---

## Task 12: Extract ScriptBridgeJs from ScriptRunnerScreen

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/bandkit/app/ScriptRunnerScreen.kt`
- Create: `shared/src/androidMain/kotlin/com/bandkit/app/ScriptBridgeJs.kt`

**Interfaces:**
- Consumes: None
- Produces: `buildJsBridge()` function in standalone file

- [ ] **Step 1: Read ScriptRunnerScreen.kt to identify buildJsBridge**

Read `ScriptRunnerScreen.kt` and locate `buildJsBridge()` function (around line 94).

- [ ] **Step 2: Create ScriptBridgeJs.kt**

Create `shared/src/androidMain/kotlin/com/bandkit/app/ScriptBridgeJs.kt` with the extracted function. Include all necessary imports.

- [ ] **Step 3: Remove function from ScriptRunnerScreen.kt**

Delete `buildJsBridge()` from `ScriptRunnerScreen.kt`.

- [ ] **Step 4: Add imports to ScriptRunnerScreen.kt**

Add at the top of `ScriptRunnerScreen.kt`:
```kotlin
import com.bandkit.app.buildJsBridge
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :shared:compileAndroidMain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add shared/src/androidMain/kotlin/com/bandkit/app/ScriptBridgeJs.kt shared/src/androidMain/kotlin/com/bandkit/app/ScriptRunnerScreen.kt
git commit -m "refactor: extract ScriptBridgeJs from ScriptRunnerScreen"
```

---

## Task 13: Fix README.md minSdk

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: None
- Produces: Correct minSdk documentation

- [ ] **Step 1: Update minSdk in README.md**

Find and replace `minSdk 23` with `minSdk 24` in `README.md`.

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: fix minSdk from 23 to 24"
```

---

## Task 14: Unify TAG Constant Placement

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/bandkit/app/core/BluetoothScanner.kt`

**Interfaces:**
- Consumes: None
- Produces: Consistent TAG placement

- [ ] **Step 1: Read BluetoothScanner.kt**

Read `shared/src/androidMain/kotlin/com/bandkit/app/core/BluetoothScanner.kt` to find the TAG constant.

- [ ] **Step 2: Move TAG to companion object**

Move the `private const val TAG = "BluetoothScanner"` from top-level to inside the class's `companion object`.

- [ ] **Step 3: Verify build**

Run: `./gradlew :shared:compileAndroidMain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add shared/src/androidMain/kotlin/com/bandkit/app/core/BluetoothScanner.kt
git commit -m "refactor: move TAG to companion object in BluetoothScanner"
```

---

## Task 15: Final Verification

**Files:**
- None (verification only)

**Interfaces:**
- Consumes: All previous tasks
- Produces: Confirmed working build

- [ ] **Step 1: Run full build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run spotless check**

Run: `./gradlew spotlessCheck`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify no regressions**

Check that all files compile and no imports are missing.

- [ ] **Step 4: Final commit (if needed)**

If spotless made changes:
```bash
git add -A
git commit -m "style: apply spotless formatting"
```
