# BandKit

基于 Kotlin Multiplatform Compose + Rust JNI 的小米/华米智能手环管理工具。

## 下载

从 [Releases](https://github.com/123IandU/BandKit/releases) 页面下载最新版本。

## 功能

- 蓝牙扫描与连接（SPP / BLE）
- 设备信息读取（型号、固件版本、电量、存储）
- 表盘管理（列表、设置当前表盘、卸载）
- 第三方应用管理（列表、启动、卸载）
- 文件安装（RPK 应用、BIN 固件）
- 设备导入/导出（JSON 格式）
- 已保存设备管理（点击设备名称快速切换）

## 技术栈

- **UI**: Kotlin Multiplatform Compose (Miuix UI)
- **协议层**: Rust + JNI (libbandkit_app_android.so)
- **平台**: Android (minSdk 23, targetSdk 37)

## 构建

```bash
./gradlew :androidApp:assembleDebug
```

## 开源许可

本项目基于 **GNU Affero General Public License v3.0 (AGPL-3.0)** 授权发布。

本软件使用了以下开源项目：

- **[AstroBox-NG](https://github.com/AstralSightStudios/AstroBox-NG)** — AGPL-3.0 with additional attribution, Copyright (C) 2025 AstralSight Studios
- **[compose-miuix-ui (Miuix)](https://github.com/compose-miuix-ui/miuix)** — Apache License 2.0

详情见 [LICENSE](LICENSE) 和 [NOTICE.md](NOTICE.md)。
