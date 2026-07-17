# Android JNI 迁移：Kotlin Core �?Native .so �?实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用预编译 `libbandkit_app_android.so` 替代内建 `libcorelib_standalone.so`，将 BandBurgManager Android actual 重写�?NativeDevice 适配�?
**Architecture:** NativeDevice 内部管理蓝牙 + 协议，Kotlin 侧只�?JSON 解析和协程桥接。expect 接口不变，Desktop/Wasm stub 不变�?
**Tech Stack:** Kotlin/JNI, `kotlinx.serialization.json`, `kotlinx.coroutines`

## Global Constraints

- `shared/commonMain/` expect 接口不改
- `shared/src/desktopMain/` + `wasmJsMain/` stub 不改
- BluetoothScanner 保留不动
- rust/ 目录保留不删
- .so 文件已在 `androidApp/src/main/jniLibs/` 就位
- spotless 格式化通过

---

### Task 1: 新增 NativeDevice JNI 绑定

**Files:**
- Create: `androidApp/src/main/java/com/astrobox/app/NativeDevice.kt`

**Interfaces:**
- Produces: `NativeDevice` object，所有方法同步阻塞（内部 tokio runtime�?
- [ ] **Step 1: 创建 NativeDevice.kt**

```kotlin
// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

object NativeDevice {
    init {
        System.loadLibrary("bandkit_app_android")
    }

    // ======== 事件回调 ========
    external fun registerEventSink(callback: (String, String) -> Unit)

    // ======== 设备连接 ========
    external fun deviceConnect(
        name: String,
        addr: String,
        authkey: String,
        sarVersion: Long,
        connectType: String,
        txWinOverrunAllowance: ByteArray,
    ): String

    external fun deviceDisconnect(addr: String): Boolean
    external fun deviceGetConnectedDevices(): String

    // ======== 设备数据 ========
    external fun deviceGetData(addr: String, dataType: String): String
    external fun deviceInstall(
        addr: String,
        resType: ByteArray,
        data: ByteArray,
        packageName: String?,
        progressCb: Any?,
        watchfaceId: String?,
    ): Boolean
    external fun deviceGetFileType(file: ByteArray, name: String): Byte

    // ======== 表盘 ========
    external fun watchfaceGetList(addr: String): String
    external fun watchfaceSetCurrent(addr: String, watchfaceId: String): Boolean
    external fun watchfaceUninstall(addr: String, watchfaceId: String): Boolean

    // ======== 第三方应�?========
    external fun thirdpartyappGetList(addr: String): String
    external fun thirdpartyappSendMessage(addr: String, packageName: String, data: String): Boolean
    external fun thirdpartyappLaunch(addr: String, packageName: String, page: String): Boolean
    external fun thirdpartyappUninstall(addr: String, packageName: String): Boolean
}
```

- [ ] **Step 2: 验证编译**

```powershell
.\gradlew.bat :androidApp:compileDebugKotlin
```

期望：编译通过（symbol 解析在链接时完成，编译阶段不需�?.so 存在即可通过�?
---

### Task 2: 重写 BandBurgManager Android actual

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/miband/app/core/BandBurgManager.kt` �?完整重写

**Interfaces:**
- Consumes: `NativeDevice` (Task 1), `ResponseParser.parseDeviceInfo`, `ResponseParser.parseWatchfaceList`, `ResponseParser.parseAppList`, `DeviceSession`, `SavedDevice`, `DeviceInfo`, `Watchface`, `InstalledApp`
- Produces: `actual class BandBurgManager`, `actual fun createBandBurgManager()`, `actual fun initBandBurgContext()`

- [ ] **Step 1: 写入新的适配层代�?*

```kotlin
// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import android.content.Context
import android.util.Log
import com.bandkit.app.core.NativeDevice
import com.bandkit.app.models.DeviceInfo
import com.bandkit.app.models.DeviceSession
import com.bandkit.app.models.InstalledApp
import com.bandkit.app.models.SavedDevice
import com.bandkit.app.models.Watchface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

actual class BandBurgManager {

    private val sessions = ConcurrentHashMap<String, DeviceSession>()

    actual fun init() {
        NativeDevice.registerEventSink { event, payload ->
            Log.d(TAG, "NativeDevice event: $event -> $payload")
        }
        Log.d(TAG, "NativeDevice event sink registered")
    }

    fun init(appContext: Context) {
        // NativeDevice handles Bluetooth internally, no Context needed
        init()
    }

    actual suspend fun connect(
        name: String,
        addr: String,
        authkey: String,
        connectType: Int,
    ): DeviceSession = withContext(Dispatchers.IO) {
        Log.d(TAG, "Connecting to $addr via NativeDevice")
        val result = NativeDevice.deviceConnect(
            name = name,
            addr = addr,
            authkey = authkey,
            sarVersion = 2L,
            connectType = if (connectType == 1) "BLE" else "SPP",
            txWinOverrunAllowance = byteArrayOf(8),
        )
        Log.d(TAG, "Connect result: $result")

        val session = DeviceSession(
            handle = addr.hashCode().toLong(),
            device = SavedDevice(
                id = System.currentTimeMillis().toString(),
                name = name,
                addr = addr,
                authkey = authkey,
                connectType = if (connectType == 1) "BLE" else "SPP",
            ),
        )
        sessions[addr] = session
        session
    }

    actual fun disconnect(session: DeviceSession) {
        NativeDevice.deviceDisconnect(session.device.addr)
        sessions.remove(session.device.addr)
        Log.d(TAG, "Disconnected from ${session.device.addr}")
    }

    actual fun destroySession(session: DeviceSession) {
        disconnect(session)
    }

    actual suspend fun getDeviceInfo(session: DeviceSession): DeviceInfo {
        val addr = session.device.addr
        return try {
            val infoJson = withContext(Dispatchers.IO) {
                NativeDevice.deviceGetData(addr, "info")
            }
            ResponseParser.parseDeviceInfo(infoJson, "[]", "[]", session.device.name)
        } catch (e: Exception) {
            Log.e(TAG, "getDeviceInfo failed", e)
            DeviceInfo(model = session.device.name, serialNumber = session.device.addr)
        }
    }

    actual suspend fun getWatchfaceList(session: DeviceSession): List<Watchface> {
        val addr = session.device.addr
        return try {
            val json = withContext(Dispatchers.IO) {
                NativeDevice.watchfaceGetList(addr)
            }
            ResponseParser.parseWatchfaceList(json)
        } catch (e: Exception) {
            Log.e(TAG, "getWatchfaceList failed", e)
            emptyList()
        }
    }

    actual suspend fun setWatchface(session: DeviceSession, watchfaceId: String): Boolean {
        val addr = session.device.addr
        return withContext(Dispatchers.IO) {
            NativeDevice.watchfaceSetCurrent(addr, watchfaceId)
        }
    }

    actual suspend fun uninstallWatchface(session: DeviceSession, watchfaceId: String): Boolean {
        val addr = session.device.addr
        return withContext(Dispatchers.IO) {
            NativeDevice.watchfaceUninstall(addr, watchfaceId)
        }
    }

    actual suspend fun getAppList(session: DeviceSession): List<InstalledApp> {
        val addr = session.device.addr
        return try {
            val json = withContext(Dispatchers.IO) {
                NativeDevice.thirdpartyappGetList(addr)
            }
            ResponseParser.parseAppList(json)
        } catch (e: Exception) {
            Log.e(TAG, "getAppList failed", e)
            emptyList()
        }
    }

    actual suspend fun launchApp(session: DeviceSession, packageName: String): Boolean {
        val addr = session.device.addr
        return withContext(Dispatchers.IO) {
            NativeDevice.thirdpartyappLaunch(addr, packageName, "")
        }
    }

    actual suspend fun uninstallApp(session: DeviceSession, packageName: String): Boolean {
        val addr = session.device.addr
        return withContext(Dispatchers.IO) {
            NativeDevice.thirdpartyappUninstall(addr, packageName)
        }
    }

    actual suspend fun installFile(
        session: DeviceSession,
        fileName: String,
        fileData: ByteArray,
        resType: Int,
        packageName: String?,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val addr = session.device.addr
        return try {
            Log.d(TAG, "Installing file: $fileName (type=$resType, size=${fileData.size})")
            onProgress(0f)

            val result = withContext(Dispatchers.IO) {
                NativeDevice.deviceInstall(
                    addr = addr,
                    resType = byteArrayOf(resType.toByte()),
                    data = fileData,
                    packageName = packageName,
                    progressCb = null,
                    watchfaceId = null,
                )
            }

            if (result) {
                onProgress(1f)
                Log.d(TAG, "File install completed: $fileName")
            } else {
                Log.e(TAG, "File install failed: $fileName")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "installFile failed: $fileName", e)
            false
        }
    }

    actual fun processReceivedData(session: DeviceSession, data: ByteArray): String = ""

    actual suspend fun sendCommand(
        session: DeviceSession,
        typeId: Int,
        commandId: Int,
        payload: ByteArray?,
    ): String = ""

    companion object {
        private const val TAG = "BandBurgManager"
    }
}

actual fun createBandBurgManager(): BandBurgManager = BandBurgManager()

actual fun initBandBurgContext(manager: BandBurgManager, context: Any) {
    manager.init(context as android.content.Context)
}
```

- [ ] **Step 2: 编译验证**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid
```

期望：编译通过，无 `Unresolved reference` 错误

---

### Task 3: 删除�?NativeLib

**Files:**
- Delete: `shared/src/androidMain/kotlin/com/bandburg/core/NativeLib.kt`

**Interfaces:**
- Consumes: 无（Task 2 完成后不再引用）

- [ ] **Step 1: 确认无引�?*

```powershell
Select-String -Path "D:\Android\Project\Miband\shared\src\androidMain\kotlin\com\miband\app\core\BandBurgManager.kt" -Pattern "com\.bandburg\.core\.NativeLib"
```

期望：无匹配（Task 2 已移�?import�?
- [ ] **Step 2: 删除文件**

```powershell
Remove-Item "D:\Android\Project\Miband\shared\src\androidMain\kotlin\com\bandburg\core\NativeLib.kt"
```

- [ ] **Step 3: 编译验证**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid
```

期望：编译通过

---

### Task 4: 清理构建配置

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Consumes: �?- Produces: �?rust-android-gradle 依赖

- [ ] **Step 1: 修改 androidApp/build.gradle.kts �?删除 rust 相关配置**

删除以下行：
- L7: `alias(libs.plugins.rustAndroid)` �?插件声明
- L12-L20: `cargo { ... }` �?cargo 配置�?- L22: `val rustJniLibsDir = ...` �?未使用变�?- L24-L26: `tasks.matching { ... }.configureEach { dependsOn("cargoBuild") }` �?任务依赖

修改后的文件�?
```kotlin
// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    id("module.kotlin-jvm-toolchain")
    id("module.spotless")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity)
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.2")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.2")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.2")
    debugImplementation(compose.uiTooling)
}

android {
    ndkVersion = "30.0.14904198"
    buildToolsVersion = BuildConfig.BUILD_TOOLS_VERSION
    compileSdk {
        version =
            release(BuildConfig.COMPILE_SDK) {
                minorApiLevel = BuildConfig.COMPILE_SDK_MINOR
            }
    }
    defaultConfig {
        applicationId = BuildConfig.APPLICATION_ID
        minSdk = BuildConfig.MIN_SDK
        targetSdk = BuildConfig.TARGET_SDK
        versionName = "1.0.0"
        versionCode = 1
    }
    namespace = BuildConfig.APPLICATION_ID
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
```

- [ ] **Step 2: 修改 gradle/libs.versions.toml �?移除 rust-android**

删除�?- `[versions]` 中的 `rust-android = "0.10.1"`
- `[plugins]` 中的 `rustAndroid = { id = "net.mullvad.rust-android", version.ref = "rust-android" }`

- [ ] **Step 3: 编译验证**

```powershell
.\gradlew.bat :androidApp:compileDebugKotlin
```

期望：编译通过

---

### Task 5: 全量编译 + 格式化验�?
**Files:**
- 无新�?修改

- [ ] **Step 1: spotless 格式�?*

```powershell
.\gradlew.bat spotlessApply
```

- [ ] **Step 2: 全量 Android 编译**

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

期望：APK 构建成功，`libbandkit_app_android.so` 正常打包

- [ ] **Step 3: 编译检查其他平台不受影�?*

```powershell
.\gradlew.bat :shared:compileKotlinDesktop :shared:compileKotlinWasmJs
```

期望：编译通过
