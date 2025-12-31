# LivePush - Android RTMP/WebRTC 推流应用

## 项目概述

LivePush 是一款轻量级的 Android 端直播推流工具，支持 RTMP 和 WebRTC 两种推流协议。**面向个人用户**，无需账号登录，开箱即用，适用于直播、远程监控等场景。

### 核心特性

- 双协议推流 (RTMP / WebRTC)
- 前后摄像头切换
- 推流地址支持: 手动输入、粘贴、二维码扫描
- 无需账号，本地存储配置

## 技术栈

- **语言**: Kotlin
- **最低 SDK**: Android 7.0 (API 24)
- **目标 SDK**: Android 14 (API 34)
- **构建工具**: Gradle 8.x + Kotlin DSL
- **架构模式**: MVVM + Clean Architecture
- **UI 框架**: Jetpack Compose + Material 3

### 核心依赖

| 功能 | 库 |
|------|-----|
| RTMP 推流 | rtmp-rtsp-stream-client-java |
| WebRTC | Google libwebrtc |
| 相机采集 | CameraX |
| 视频编码 | MediaCodec (硬编码) |
| 二维码扫描 | ML Kit Barcode Scanning / ZXing |
| 依赖注入 | Hilt |
| 异步处理 | Kotlin Coroutines + Flow |
| 网络请求 | OkHttp + Retrofit |
| 滤镜效果 | GPUImage / OpenGL ES |
| 本地存储 | DataStore / Room |

## 项目结构

```
app/
├── src/main/
│   ├── java/com/livepush/
│   │   ├── app/                 # Application 入口
│   │   ├── di/                  # 依赖注入模块
│   │   ├── data/                # 数据层
│   │   │   ├── repository/      # 仓库实现
│   │   │   └── source/          # 数据源 (本地存储)
│   │   ├── domain/              # 领域层
│   │   │   ├── model/           # 领域模型
│   │   │   ├── repository/      # 仓库接口
│   │   │   └── usecase/         # 用例
│   │   ├── presentation/        # 表现层
│   │   │   ├── ui/              # Compose UI
│   │   │   │   ├── home/        # 首页
│   │   │   │   ├── stream/      # 推流页
│   │   │   │   ├── scanner/     # 扫码页
│   │   │   │   └── settings/    # 设置页
│   │   │   ├── viewmodel/       # ViewModel
│   │   │   └── navigation/      # 导航
│   │   ├── streaming/           # 推流核心
│   │   │   ├── rtmp/            # RTMP 实现
│   │   │   ├── webrtc/          # WebRTC 实现
│   │   │   ├── encoder/         # 编码器
│   │   │   └── capture/         # 采集模块
│   │   ├── scanner/             # 二维码扫描模块
│   │   └── util/                # 工具类
│   └── res/                     # 资源文件
├── build.gradle.kts
└── proguard-rules.pro
```

## 开发规范

### 代码风格

- 遵循 Kotlin 官方编码规范
- 使用 ktlint 进行代码格式化
- 命名规范:
  - 类名: PascalCase (如 `RtmpStreamManager`)
  - 函数/变量: camelCase (如 `startStreaming`)
  - 常量: SCREAMING_SNAKE_CASE (如 `DEFAULT_BITRATE`)
  - 包名: 全小写 (如 `com.livepush.streaming`)

### Git 规范

- 分支命名: `feature/xxx`, `bugfix/xxx`, `hotfix/xxx`
- Commit 格式: `type(scope): description`
  - type: feat, fix, docs, style, refactor, test, chore
  - 示例: `feat(rtmp): add reconnection mechanism`

### 文档位置

- 需求文档: `docs/prd.md`
- 技术架构: `docs/architecture.md`
- UI 设计: `docs/ui-design.md`
- API 文档: `docs/api.md`

## 常用命令

```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease

# 运行单元测试
./gradlew test

# 运行 Lint 检查
./gradlew lint

# 代码格式化
./gradlew ktlintFormat
```

## 关键配置

### 推流默认参数

| 参数 | 默认值 | 范围 |
|------|--------|------|
| 视频分辨率 | 1280x720 | 640x360 ~ 1920x1080 |
| 视频帧率 | 30 fps | 15 ~ 60 fps |
| 视频码率 | 2 Mbps | 500 Kbps ~ 8 Mbps |
| 音频采样率 | 44100 Hz | 22050 ~ 48000 Hz |
| 音频码率 | 128 Kbps | 64 ~ 320 Kbps |

### 必要权限

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

## 注意事项

1. **编码器兼容性**: 优先使用硬编码，部分低端设备需要软编码降级
2. **后台推流**: 需要前台服务保活，Android 14+ 需声明服务类型
3. **网络状态**: 监听网络变化，实现断线重连
4. **功耗优化**: 合理控制帧率和分辨率，避免过度发热
5. **二维码扫描**: 使用 ML Kit 优先 (离线识别)，ZXing 作为备选
6. **摄像头切换**: 前后摄像头切换时需要重新初始化编码器

## 测试服务器

- RTMP 测试: `rtmp://localhost/live/test`
- WebRTC 信令: `wss://localhost:8443/signaling`
- STUN 服务器: `stun:stun.l.google.com:19302`
