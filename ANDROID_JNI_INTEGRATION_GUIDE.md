# app_android JNI 库集成指南

## 📦 文件结构

将编译好的 `.so` 文件放入 Android 项目的 `jniLibs` 目录：

```
androidApp/src/main/jniLibs/
├── arm64-v8a/
│   └── libastrobox_app_android.so    (真机 64位)
├── armeabi-v7a/
│   └── libastrobox_app_android.so    (真机 32位旧设备)
└── x86_64/
    └── libastrobox_app_android.so    (模拟器)
```

## 🧩 Kotlin 绑定代码

创建文件 `androidApp/src/main/java/com/astrobox/app/NativeDevice.kt`：

```kotlin
package com.astrobox.app

object NativeDevice {
    init {
        System.loadLibrary("astrobox_app_android")
    }

    // ======== 事件回调 ========
    external fun registerEventSink(callback: (String, String) -> Unit)

    // ======== 设备连接 ========
    external fun deviceConnect(
        name: String,
        addr: String,
        authkey: String,
        sarVersion: Long,
        connectType: String,   // "SPP" 或 "BLE"
        txWinOverrunAllowance: ByteArray,
    ): String  // 返回 JSON 或 "Error: ..."

    external fun deviceDisconnect(addr: String): Boolean
    external fun deviceGetConnectedDevices(): String  // JSON

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

    // ======== 第三方应用 ========
    external fun thirdpartyappGetList(addr: String): String
    external fun thirdpartyappSendMessage(addr: String, packageName: String, data: String): Boolean
    external fun thirdpartyappLaunch(addr: String, packageName: String, page: String): Boolean
    external fun thirdpartyappUninstall(addr: String, packageName: String): Boolean
}
```

## 📋 AndroidManifest.xml — 蓝牙权限

```xml
<manifest>
    <!-- 蓝牙权限 -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <!-- 允许应用访问附近设备（Android 12+） -->
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    ...
```

## ⚡ 使用示例

### 1. 请求运行时权限

```kotlin
class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS)
    }
}
```

### 2. 注册事件监听 + 连接设备

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissions()
    }

    override fun onResume() {
        super.onResume()
        connectToDevice()
    }

    private fun connectToDevice() {
        // 注册事件回调
        NativeDevice.registerEventSink { event, payload ->
            runOnUiThread {
                when (event) {
                    "device-connected" -> {
                        // payload 是 DeviceConnectionInfo 的 JSON
                        Log.d("AstroBox", "设备已连接: $payload")
                    }
                    "device-disconnected" -> {
                        Log.d("AstroBox", "设备已断开: $payload")
                    }
                }
            }
        }

        // 连接设备（在主线程外调用，避免 ANR）
        Thread {
            val result = NativeDevice.deviceConnect(
                name = "Mi Band",
                addr = "AA:BB:CC:DD:EE:FF",  // 蓝牙 MAC 地址
                authkey = "your_auth_key",
                sarVersion = 2L,
                connectType = "SPP",
                txWinOverrunAllowance = byteArrayOf(8),
            )
            Log.d("AstroBox", "连接结果: $result")
        }.start()
    }
}
```

### 3. 获取设备数据

```kotlin
// 获取设备信息
Thread {
    val info = NativeDevice.deviceGetData(deviceAddr, "info")
    Log.d("AstroBox", "设备信息: $info")

    // 获取表盘列表
    val watchfaces = NativeDevice.watchfaceGetList(deviceAddr)
    Log.d("AstroBox", "表盘列表: $watchfaces")

    // 获取第三方应用列表
    val apps = NativeDevice.thirdpartyappGetList(deviceAddr)
    Log.d("AstroBox", "应用列表: $apps")
}.start()
```

### 4. 断开连接

```kotlin
NativeDevice.deviceDisconnect(deviceAddr)
```

## 🪵 查看 Rust 侧日志

初始化库后，Rust 侧的所有 `log::info!()`、`log::debug!()`、`log::error!()` 会输出到 Android Logcat。

在 Android Studio 底部 **Logcat** 窗口，过滤条件设为：

```
tag:^AstroBox$
```

或直接搜索 `AstroBox`，即可看到类似：

```
2026-07-16 13:55:01.123  I  AstroBox JNI library initialized
2026-07-16 13:55:01.456  I  [spp] Connecting to AA:BB:CC:DD:EE:FF...
2026-07-16 13:55:02.012  I  [spp] RFCOMM socket connected
2026-07-16 13:55:02.345  D  [core] Device registered: Mi Band
```

## 🔁 编译更新 .so

当 Rust 代码修改后，重新编译并复制：

```bash
# 编译 arm64
cargo build --target aarch64-linux-android -p astrobox-app-android ^
  --manifest-path D:\Resource\app_android\Cargo.toml ^
  --config "target.aarch64-linux-android.linker='D:\Android\SDK\ndk\30.0.15729638\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android21-clang.cmd'"

# 复制到项目
copy D:\Resource\app_android\target\aarch64-linux-android\debug\libastrobox_app_android.so ^
  D:\Android\Project\Miband\androidApp\src\main\jniLibs\arm64-v8a\ /Y
```

或直接使用 `build-all.bat` 编译三种架构并一次性复制（需自行修改脚本中的目标路径）。

## ⚠️ 注意事项

1. **所有 JNI 函数都是同步阻塞的**（`deviceConnect` 内部创建 tokio runtime 跑异步任务），建议在子线程调用，不要在主线程（UI 线程）调用，否则会 ANR。
2. **`registerEventSink` 必须在 `deviceConnect` 之前调用**，否则连接成功的事件无法送达。
3. `deviceConnect` 会自动断开已有连接（`disconnect_all_sessions`），无需手动调用 `deviceDisconnect`。
4. Debug 模式的 `.so` 约 200-250 MB（含调试符号），Release 模式约 35 MB。发布 APK 前务必用 `--release` 编译。
5. 返回 `jstring` 的函数（`deviceConnect`、`deviceGetData` 等）返回的是 JSON 字符串，需要用 `JSONObject`/`JSONArray` 解析。
