# LivePush

[![Android CI](https://github.com/jeremykit/livepush/actions/workflows/android.yml/badge.svg)](https://github.com/jeremykit/livepush/actions/workflows/android.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)

轻量级 Android 直播推流工具，支持 RTMP 和 WebRTC 双协议推流。无需账号登录，开箱即用。

## 功能特性

- **双协议支持**: RTMP / WebRTC 推流
- **多种输入方式**: 手动输入、粘贴、二维码扫描推流地址
- **摄像头切换**: 支持前后摄像头实时切换
- **参数可调**: 分辨率、帧率、码率自由配置
- **无需账号**: 本地存储配置，保护隐私
- **Material You**: 现代化 UI 设计，支持深色模式

## 截图

<!-- 添加应用截图 -->
| 首页 | 推流 | 设置 |
|:---:|:---:|:---:|
| ![Home](docs/screenshots/home.jpg) | ![Stream](docs/screenshots/stream.jpg) | ![Settings](docs/screenshots/settings.jpg) |

## 下载安装

### 从 Release 下载

前往 [Releases](https://github.com/jeremykit/livepush/releases) 页面下载最新版本 APK。

### 从源码构建

```bash
# 克隆仓库
git clone https://github.com/jeremykit/livepush.git
cd livepush

# 构建 Debug 版本
./gradlew assembleDebug

# APK 路径: app/build/outputs/apk/debug/app-debug.apk
```

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Clean Architecture |
| 依赖注入 | Hilt |
| 相机 | CameraX |
| RTMP | RootEncoder |
| 二维码 | ML Kit Barcode Scanning |
| 存储 | Room + DataStore |

## 系统要求

- Android 7.0 (API 24) 及以上
- 摄像头和麦克风权限
- 网络连接

## 默认推流参数

| 参数 | 默认值 | 范围 |
|------|--------|------|
| 视频分辨率 | 1280x720 | 640x360 ~ 1920x1080 |
| 视频帧率 | 30 fps | 15 ~ 60 fps |
| 视频码率 | 2 Mbps | 500 Kbps ~ 8 Mbps |
| 音频采样率 | 44100 Hz | 22050 ~ 48000 Hz |
| 音频码率 | 128 Kbps | 64 ~ 320 Kbps |

## 开发

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17
- Gradle 8.9

### 构建命令

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease

# 运行测试
./gradlew test

# Lint 检查
./gradlew lint
```

## GitHub Actions 自动构建与发布

本项目使用 GitHub Actions 自动构建和发布 APK。

### CI/CD 流程

| 触发条件 | 执行操作 |
|---------|---------|
| Push/PR 到 main/master | 构建 Debug APK + Lint 检查 |
| 推送 Tag (v*) | 构建签名 Release APK，自动发布到 Releases |
| 手动触发 | 在 Actions 页面点击 `Run workflow` |

### 配置签名密钥（首次发布前需要）

#### 步骤 1：生成签名密钥

```bash
keytool -genkey -v -keystore livepush.jks -keyalg RSA -keysize 2048 -validity 10000 -alias livepush
```

按提示输入密码和相关信息，**请牢记你设置的密码**。

#### 步骤 2：将密钥转为 Base64

**Linux/macOS:**
```bash
base64 -i livepush.jks | pbcopy  # macOS，直接复制到剪贴板
base64 -i livepush.jks           # Linux，输出到终端
```

**Windows PowerShell:**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("livepush.jks")) | Set-Clipboard
```

#### 步骤 3：配置 GitHub Secrets

1. 打开仓库设置：`Settings` → `Secrets and variables` → `Actions`
2. 点击 `New repository secret`，添加以下 4 个 Secrets：

| Secret 名称 | 值 |
|------------|-----|
| `SIGNING_KEY_BASE64` | 步骤 2 中获取的 Base64 字符串 |
| `SIGNING_KEY_ALIAS` | 密钥别名，如 `livepush` |
| `SIGNING_KEY_PASSWORD` | 创建密钥时设置的密钥密码 |
| `SIGNING_STORE_PASSWORD` | 创建密钥时设置的密钥库密码 |

### 发布新版本

配置好 Secrets 后，发布新版本只需两条命令：

```bash
# 1. 创建版本标签
git tag v1.0.0

# 2. 推送标签到 GitHub
git push origin v1.0.0
```

推送 Tag 后，GitHub Actions 会自动：
1. 构建 Release APK
2. 使用配置的密钥签名
3. 创建 GitHub Release
4. 上传签名后的 APK 到 Release 页面

发布完成后，可在 [Releases](https://github.com/jeremykit/livepush/releases) 页面下载。

### 版本号规范

建议使用 [语义化版本](https://semver.org/lang/zh-CN/)：
- `v1.0.0` - 首个正式版本
- `v1.0.1` - Bug 修复
- `v1.1.0` - 新功能
- `v2.0.0` - 重大更新/不兼容变更

## 项目结构

```
app/src/main/java/com/livepush/
├── app/                 # Application 入口
├── di/                  # 依赖注入模块
├── data/                # 数据层
│   ├── repository/      # 仓库实现
│   └── source/          # 数据源
├── domain/              # 领域层
│   ├── model/           # 领域模型
│   ├── repository/      # 仓库接口
│   └── usecase/         # 用例
├── presentation/        # 表现层
│   ├── ui/              # Compose UI
│   ├── viewmodel/       # ViewModel
│   └── navigation/      # 导航
├── streaming/           # 推流核心
└── scanner/             # 二维码扫描
```

## 许可证

```
Copyright 2024 LivePush

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 致谢

- [RootEncoder](https://github.com/pedroSG94/RootEncoder) - RTMP/RTSP 推流库
- [CameraX](https://developer.android.com/training/camerax) - Android 相机库
- [ML Kit](https://developers.google.com/ml-kit) - 二维码扫描
