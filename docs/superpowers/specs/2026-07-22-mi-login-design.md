# 小米账号登录获取 AuthKey — 设计文档

## 概述

在 BandKit App 中实现小米账号登录流程，从 Mi Fitness 服务器获取已绑定设备的蓝牙配对密钥（authkey），自动添加到设备列表。

## 架构

```
┌─────────────────────────────────────────────────┐
│ commonMain (Compose UI)                         │
│  MiLoginScreen.kt — 登录界面 + 状态管理          │
│  MiClient.kt — expect 声明                      │
├─────────────────────────────────────────────────┤
│ androidMain                                     │
│  MiClient.android.kt — OkHttp HTTP 实现         │
│  CaptchaWebView.kt — 验证码 WebView Dialog      │
├─────────────────────────────────────────────────┤
│ Rust (app_android)                              │
│  mi_crypto.rs — RC4/SHA1/SHA256/HMAC (已有)     │
└─────────────────────────────────────────────────┘
```

## 小米登录流程

```
1. GET  https://account.xiaomi.com/pass/serviceLogin
    params: _json=true, sid=miothealth, _locale=en_US
    cookies: userId, deviceId
    → 返回 _sign, qs, callback

2. POST https://account.xiaomi.com/pass/serviceLoginAuth2
    data: qs, callback, _json, _sign, user, hash(MD5 密码大写), sid, _locale
    cookies: deviceId
    → 成功 → ssecurity, nonce, userId, location, cUserId
    → 需要验证码 → code=713 (或非0) + captcha_url

3. [如果需要验证码] 弹出 WebView 加载 captcha_url
    用户输入验证码后, 小米会302跳转到 callback URL
    从 cookie/redirect 中取到 serviceToken

4. GET location?clientSign=...
    → 获取 serviceToken cookie

5. POST https://hlth.io.mi.com/app/v1/source/get_source_list
    加密请求 (调 Rust mi_encrypt_params)
    响应解密 (调 Rust mi_decrypt_response)
    → 返回绑定的设备列表（含 auth_key）
```

## 验证码处理

- 用 AlertDialog 弹出一个 WebView
- WebView 加载 captcha_url
- 用户完成验证码后拦截回调 URL
- 从 cookie 中提取 serviceToken
- 自动继续后续流程

## 获取设备 ↔ 添加到 App

- 登录成功 → `get_source_list` 获取已绑定设备列表
- 列表显示设备名、MAC 地址、配对密钥
- 用户点击"添加" → 自动创建 SavedDevice 并保存
- 支持已有设备忽略（MAC 去重）

## UI 设计

### 设置页入口
在"关于"页面或设置页面添加"小米账号登录"按钮。

### 登录弹窗
- 账号输入框 (email)
- 密码输入框 (password)
- 登录按钮
- 状态文本（登录中/需要验证码/成功/失败）
- 验证码弹窗（WebView）

### 设备选择弹窗
- 登录成功后自动跳转到设备列表
- 每行显示：设备名 | MAC | 密钥
- "添加"按钮

## 错误处理

| 场景 | 处理 |
|------|------|
| 网络错误 | 显示错误 Toast，可重试 |
| 账号密码错误 | 显示"账号或密码错误" |
| 需要验证码 | 弹出 WebView |
| 验证码错误 | 提示重新输入 |
| 无绑定设备 | 显示"未找到绑定设备" |
| 设备已存在 | 跳过不添加 |

## 依赖

- 已有：OkHttp（通过 Ktor 或直接依赖）
- 已有：Rust mi_crypto（RC4/SHA1/SHA256）
- 新增无外部依赖

## 文件清单

### 新增

| 文件 | 内容 |
|------|------|
| `shared/.../MiLoginScreen.kt` | 登录 UI + 状态管理 |
| `shared/.../MiClient.kt` | expect 声明 |
| `androidMain/.../MiClient.kt` | OkHttp 实际实现 |
| `androidMain/.../CaptchaWebView.kt` | 验证码弹窗 |

### 修改

| 文件 | 改动 |
|------|------|
| `App.kt` | 添加登录入口按钮 |
| `NativeDevice.kt` | 已有（mi crypto JNI 已加） |
| `PlatformUtils.kt` | 可能添加 expect/actual |
