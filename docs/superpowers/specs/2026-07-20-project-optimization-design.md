# 项目优化设计文档

**日期**: 2026-07-20
**目标**: 在保证现有功能不变的情况下，优化代码及项目文件结构，使项目符合规范，便于日后维护和升级

## 背景

项目当前存在以下问题：
- `App.kt` (1,214 行) 包含所有 UI 屏幕，难以维护
- `ScriptRunnerScreen.kt` (1,085 行) 包含 670 行内嵌 JS 字符串
- NDK 版本在 Gradle 和 Rust 之间不匹配
- Miuix 库版本在 6 个地方硬编码
- 工具函数错放位置
- 空目录和过时文件

## 方案选择

采用 **方案 B：功能拆分 + 构建修复**，在改进与风险之间取得平衡。

---

## 第 1 部分：构建配置修复

### 1.1 NDK 版本对齐

**现状**：
- `androidApp/build.gradle.kts`: `ndkVersion = "30.0.14904198"`
- `rust/app_android/.cargo/config.toml`: 引用 `30.0.15729638`

**操作**：
- 统一为 `30.0.14904198`（Gradle 版本，主构建系统）
- 更新 `rust/app_android/.cargo/config.toml` 中的链接器路径

**文件变更**：
- `rust/app_android/.cargo/config.toml`

### 1.2 统一 Miuix 版本

**现状**：`0.9.3` 在 2 个构建文件中硬编码了 6 次

**操作**：添加到 `gradle/libs.versions.toml`：
```toml
[versions]
miuix = "0.9.3"

[libraries]
miuix-ui = { module = "top.yukonga.miuix.kmp:miuix-ui", version.ref = "miuix" }
miuix-icons = { module = "top.yukonga.miuix.kmp:miuix-icons", version.ref = "miuix" }
miuix-preference = { module = "top.yukonga.miuix.kmp:miuix-preference", version.ref = "miuix" }
```

**文件变更**：
- `gradle/libs.versions.toml` (添加)
- `androidApp/build.gradle.kts` (改用 `alias(libs.miuix.ui)` 等)
- `shared/build.gradle.kts` (改用 `alias(libs.miuix.ui)` 等)

### 1.3 删除过时的 build-all.bat

**现状**：引用 `D:\Resource\app_android\` 和 `astrobox-app-android`

**操作**：删除 `rust/app_android/build-all.bat`（已被 Gradle 任务替代）

**文件变更**：
- `rust/app_android/build-all.bat` (删除)

---

## 第 2 部分：代码结构优化

### 2.1 拆分 App.kt (1,214 行 → ~5 个文件)

按功能屏幕拆分，所有文件保持在 `com.bandkit.app` 包下：

| 新文件 | 包含内容 | 预估行数 |
|--------|----------|----------|
| `App.kt` | `App()`、`AppContent()` 主入口 | ~500 |
| `DeviceStatusBar.kt` | `DeviceStatusBar`、`SavedDevicesBottomSheet` | ~115 |
| `AddDeviceBottomSheet.kt` | `AddDeviceBottomSheet` | ~130 |
| `WatchfaceSection.kt` | `WatchfaceSection` | ~110 |
| `AppSection.kt` | `AppSection` | ~100 |
| `InstallSection.kt` | `InstallSection`、`LogSection`、工具函数 | ~160 |

**文件变更**：
- `shared/src/commonMain/kotlin/com/bandkit/app/App.kt` (拆分)
- 新增 5 个文件

### 2.2 从 ScriptRunnerScreen.kt 提取 JS Bridge

**现状**：`buildJsBridge()` 包含 ~670 行 JS 模板字符串

**操作**：提取为 `ScriptBridgeJs.kt`，只包含 JS 字符串生成函数

**结果**：ScriptRunnerScreen.kt 从 1,085 行减少到 ~540 行

**文件变更**：
- `shared/src/androidMain/kotlin/com/bandkit/app/ScriptRunnerScreen.kt` (拆分)
- 新增 `ScriptBridgeJs.kt`

### 2.3 移动错放的工具函数

- `formatFileSize`：从 `App.kt` 移动到 `core/PlatformUtils.kt`（commonMain）
- `parseStoragePercent`：从 `App.kt` 移动到 `core/PlatformUtils.kt`
- `DeviceExportImportState`：从 `PlatformUtils.kt`（androidMain）提取为独立文件 `DeviceExportImportState.kt`

**文件变更**：
- `shared/src/commonMain/kotlin/com/bandkit/app/core/PlatformUtils.kt` (添加函数)
- `shared/src/androidMain/kotlin/com/bandkit/app/core/PlatformUtils.kt` (提取 DeviceExportImportState)
- 新增 `shared/src/androidMain/kotlin/com/bandkit/app/core/DeviceExportImportState.kt`

### 2.4 清理死代码

- 删除空目录 `shared/src/androidMain/kotlin/com/bandkit/app/highlight/`

---

## 第 3 部分：其他清理

### 3.1 修复 README.md 中的 minSdk 错误

**现状**：README.md 写 minSdk 23，但 BuildConfig.kt 实际是 24

**操作**：更新 README.md 中的 minSdk 为 24

**文件变更**：
- `README.md`

### 3.2 统一 TAG 常量位置

**现状**：
- `BluetoothScanner.kt`：顶层 `private const val TAG`
- `BandBurgManager.kt`：`companion object` 中的 `private const val TAG`

**操作**：统一为 `companion object` 方式（更符合 Kotlin 惯例）

**文件变更**：
- `shared/src/androidMain/kotlin/com/bandkit/app/core/BluetoothScanner.kt`

### 3.3 不涉及 desktopApp 和 webApp

保持这两个模块现状不变。

---

## 执行顺序

1. **构建配置修复** (第 1 部分) — 低风险，先做
2. **代码结构优化** (第 2 部分) — 核心改动
3. **其他清理** (第 3 部分) — 收尾工作
4. **验证构建** — 确保所有改动后项目仍可编译

## 验证方式

- `./gradlew :shared:compileAndroidMain` — 验证 shared 模块编译
- `./gradlew :androidApp:assembleDebug` — 验证完整 APK 构建
- `./gradlew spotlessCheck` — 验证代码格式
- 手动检查拆分后的文件导入是否正确

## 风险控制

- 每个部分独立完成，可单独验证
- 保持所有公共 API 不变
- 不修改业务逻辑，只调整文件结构
- 拆分时保持完整的导入关系
