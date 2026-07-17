# BandKit

基于 Kotlin Multiplatform Compose + Rust JNI 的小米/华米智能手环管理工具。

## 功能

- 蓝牙扫描与连接（SPP 协议）
- 设备信息读取（型号、固件版本、电量、存储）
- 表盘管理（列表、设置、卸载）
- 第三方应用管理（列表、启动、卸载、安装 RPK）
- 原生层协议处理，认证加密

## 技术栈

- **前端**: Kotlin Multiplatform Compose (Miuix UI)
- **后端协议**: Rust + JNI (libbandkit_app_android.so)
- **平台**: Android (targetSdk 37)

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
