# iOS 版本开发计划

> 本文档记录 LivePush iOS 版本的开发方案和实现细节，作为下一阶段的开发任务。

## 目标

在 Windows 开发环境下，通过 GitHub Actions 构建 iOS 应用，支持在 iPhone 上测试和发布。

---

## 方案对比

### 免费方案 vs 付费方案

| 对比项 | 免费方案 | 付费方案 |
|-------|---------|---------|
| 费用 | 免费 | $99/年 (约 ¥700) |
| 签名有效期 | 7 天 | 1 年 |
| 测试设备数 | 3 台 | 100 台 |
| TestFlight | ❌ 不支持 | ✅ 支持 (10000 用户) |
| App Store | ❌ 不能发布 | ✅ 可以发布 |
| 推送通知 | ❌ 不支持 | ✅ 支持 |
| 安装方式 | AltStore/电脑连接 | TestFlight 直接下载 |

---

## 免费方案详解

### 适用场景
- 个人测试、学习
- 项目初期验证可行性
- 不需要分发给他人

### 实现流程

```
┌─────────────────────────────────────────────────────────┐
│ 1. Windows 电脑                                         │
│    └── 编写 KMP 代码 → git push                         │
└─────────────────────────┬───────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ 2. GitHub Actions (macos-latest)                        │
│    ├── 编译 iOS 项目                                    │
│    ├── 生成 .app 文件                                   │
│    └── 打包为 .ipa 并上传到 Artifacts                   │
└─────────────────────────┬───────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ 3. 下载 .ipa 文件到 Windows 电脑                        │
└─────────────────────────┬───────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ 4. 使用 AltStore 安装到 iPhone                          │
│    ├── Windows 安装 AltStore: https://altstore.io/      │
│    ├── iPhone 连接电脑                                  │
│    ├── AltStore 安装 .ipa                               │
│    └── 每 7 天需要重新签名安装                          │
└─────────────────────────────────────────────────────────┘
```

### 所需工具
1. **AltStore** (Windows 版): https://altstore.io/
2. **iTunes** (Windows): 用于连接 iPhone
3. **免费 Apple ID**: 普通 Apple 账号即可

### GitHub Actions 配置示例

```yaml
name: iOS Build (Free)

on:
  push:
    tags:
      - 'v*'

jobs:
  build-ios:
    runs-on: macos-latest

    steps:
    - uses: actions/checkout@v4

    - name: Setup Xcode
      uses: maxim-lobanov/setup-xcode@v1
      with:
        xcode-version: latest-stable

    - name: Build iOS App
      run: |
        cd iosApp
        xcodebuild -workspace iosApp.xcworkspace \
          -scheme iosApp \
          -configuration Release \
          -sdk iphoneos \
          -derivedDataPath build \
          CODE_SIGN_IDENTITY="" \
          CODE_SIGNING_REQUIRED=NO \
          CODE_SIGNING_ALLOWED=NO

    - name: Create IPA
      run: |
        mkdir -p Payload
        cp -r build/Build/Products/Release-iphoneos/iosApp.app Payload/
        zip -r LivePush-unsigned.ipa Payload

    - name: Upload IPA
      uses: actions/upload-artifact@v4
      with:
        name: ios-unsigned-ipa
        path: LivePush-unsigned.ipa
```

### 限制说明
- 签名有效期 **7 天**，过期后 App 无法打开
- 需要每周重新用 AltStore 安装
- 最多 3 个 App（同一 Apple ID 签名）
- 不支持后台推送等高级功能

---

## 付费方案详解

### 适用场景
- 需要分发给测试用户
- 准备上架 App Store
- 需要推送通知等完整功能

### 准备工作

#### 1. 注册 Apple Developer Program
- 网址: https://developer.apple.com/programs/enroll/
- 费用: $99/年
- 审核时间: 1-2 个工作日

#### 2. 创建证书和配置文件
在 Apple Developer 后台创建：
- **Certificates**: iOS Distribution Certificate
- **Identifiers**: App ID (com.livepush.app)
- **Profiles**: Ad Hoc / App Store Provisioning Profile

#### 3. 配置 GitHub Secrets
| Secret 名称 | 说明 |
|------------|------|
| `IOS_CERTIFICATE_BASE64` | .p12 证书文件的 Base64 |
| `IOS_CERTIFICATE_PASSWORD` | 证书密码 |
| `IOS_PROVISION_PROFILE_BASE64` | .mobileprovision 文件的 Base64 |
| `APPSTORE_CONNECT_API_KEY` | App Store Connect API 密钥 |
| `APPSTORE_CONNECT_ISSUER_ID` | API 发行者 ID |
| `APPSTORE_CONNECT_KEY_ID` | API 密钥 ID |

### GitHub Actions 配置示例

```yaml
name: iOS Build & Deploy

on:
  push:
    tags:
      - 'v*'

jobs:
  build-ios:
    runs-on: macos-latest

    steps:
    - uses: actions/checkout@v4

    - name: Setup Xcode
      uses: maxim-lobanov/setup-xcode@v1
      with:
        xcode-version: latest-stable

    - name: Install certificates
      env:
        CERTIFICATE_BASE64: ${{ secrets.IOS_CERTIFICATE_BASE64 }}
        CERTIFICATE_PASSWORD: ${{ secrets.IOS_CERTIFICATE_PASSWORD }}
        PROVISION_PROFILE_BASE64: ${{ secrets.IOS_PROVISION_PROFILE_BASE64 }}
      run: |
        # 创建临时 keychain
        KEYCHAIN_PATH=$RUNNER_TEMP/app-signing.keychain-db
        security create-keychain -p "" $KEYCHAIN_PATH
        security set-keychain-settings -lut 21600 $KEYCHAIN_PATH
        security unlock-keychain -p "" $KEYCHAIN_PATH

        # 导入证书
        echo $CERTIFICATE_BASE64 | base64 --decode > certificate.p12
        security import certificate.p12 -P $CERTIFICATE_PASSWORD \
          -A -t cert -f pkcs12 -k $KEYCHAIN_PATH
        security list-keychain -d user -s $KEYCHAIN_PATH

        # 安装 provisioning profile
        echo $PROVISION_PROFILE_BASE64 | base64 --decode > profile.mobileprovision
        mkdir -p ~/Library/MobileDevice/Provisioning\ Profiles
        cp profile.mobileprovision ~/Library/MobileDevice/Provisioning\ Profiles/

    - name: Build iOS App
      run: |
        cd iosApp
        xcodebuild -workspace iosApp.xcworkspace \
          -scheme iosApp \
          -configuration Release \
          -sdk iphoneos \
          -archivePath build/iosApp.xcarchive \
          archive

    - name: Export IPA
      run: |
        cd iosApp
        xcodebuild -exportArchive \
          -archivePath build/iosApp.xcarchive \
          -exportPath build/ipa \
          -exportOptionsPlist ExportOptions.plist

    - name: Upload to TestFlight
      env:
        APPSTORE_CONNECT_API_KEY: ${{ secrets.APPSTORE_CONNECT_API_KEY }}
        APPSTORE_CONNECT_ISSUER_ID: ${{ secrets.APPSTORE_CONNECT_ISSUER_ID }}
        APPSTORE_CONNECT_KEY_ID: ${{ secrets.APPSTORE_CONNECT_KEY_ID }}
      run: |
        xcrun altool --upload-app \
          --type ios \
          --file iosApp/build/ipa/iosApp.ipa \
          --apiKey $APPSTORE_CONNECT_KEY_ID \
          --apiIssuer $APPSTORE_CONNECT_ISSUER_ID

    - name: Upload IPA to Release
      uses: softprops/action-gh-release@v2
      with:
        files: iosApp/build/ipa/iosApp.ipa
```

---

## KMP 项目迁移计划

### 目标结构

```
livepush/
├── shared/                        # 共享 Kotlin 模块
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/            # 通用代码
│       │   └── kotlin/
│       │       ├── domain/
│       │       │   ├── model/     # StreamConfig, StreamState 等
│       │       │   ├── repository/ # 仓库接口
│       │       │   └── usecase/   # StreamManager 接口
│       │       └── util/          # 工具类
│       ├── androidMain/           # Android 特定实现
│       │   └── kotlin/
│       │       └── streaming/     # RootEncoder 实现
│       └── iosMain/               # iOS 特定实现
│           └── kotlin/
│               └── streaming/     # HaishinKit 桥接
│
├── androidApp/                    # Android 应用 (现有代码迁移)
│   ├── build.gradle.kts
│   └── src/main/
│       └── kotlin/
│           ├── app/
│           ├── di/
│           ├── presentation/      # Jetpack Compose UI
│           └── ...
│
├── iosApp/                        # iOS 应用 (新建)
│   ├── iosApp.xcodeproj
│   └── iosApp/
│       ├── App.swift
│       ├── ContentView.swift      # SwiftUI UI
│       └── ...
│
├── build.gradle.kts               # 根项目配置
├── settings.gradle.kts
└── .github/workflows/
    └── build.yml                  # 双平台构建
```

### 迁移步骤

#### 阶段 1: 项目结构重构
1. 创建 KMP 项目结构
2. 配置 Gradle 多平台插件
3. 创建 shared 模块

#### 阶段 2: 共享代码抽取
1. 迁移 domain/model 到 commonMain
2. 迁移 domain/repository 接口到 commonMain
3. 定义 expect/actual 推流接口

#### 阶段 3: Android 适配
1. 将现有代码迁移到 androidApp
2. 实现 actual 推流类 (RootEncoder)
3. 验证 Android 功能正常

#### 阶段 4: iOS 开发
1. 创建 iosApp Xcode 项目
2. 实现 actual 推流类 (HaishinKit 桥接)
3. 开发 SwiftUI 界面

#### 阶段 5: CI/CD 配置
1. 配置 GitHub Actions 双平台构建
2. 配置 Android Release 签名
3. 配置 iOS 签名 (免费或付费方案)

---

## iOS 推流库选择

| 库 | 协议 | Stars | 维护状态 |
|---|------|-------|---------|
| [HaishinKit](https://github.com/shogo4405/HaishinKit.swift) | RTMP/SRT | 2.7k | 活跃 |
| [LFLiveKit](https://github.com/LaiFengiOS/LFLiveKit) | RTMP | 4.4k | 停止维护 |
| [VideoCore](https://github.com/jgh-/VideoCore) | RTMP | 1.8k | 停止维护 |

**推荐: HaishinKit** - 活跃维护，支持 RTMP 和 SRT

---

## 时间预估

| 阶段 | 预估时间 | 前置条件 |
|-----|---------|---------|
| KMP 项目结构搭建 | 1-2 天 | 无 |
| 共享代码迁移 | 2-3 天 | 阶段 1 完成 |
| Android 适配验证 | 1 天 | 阶段 2 完成 |
| iOS UI 开发 | 3-5 天 | 阶段 3 完成 |
| iOS 推流功能 | 2-3 天 | 阶段 4 完成 |
| CI/CD 配置 | 1-2 天 | 阶段 5 完成 |

**总计: 约 10-16 天**

---

## 参考资源

- [Kotlin Multiplatform 官方文档](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [HaishinKit iOS 推流库](https://github.com/shogo4405/HaishinKit.swift)
- [Apple Developer Program](https://developer.apple.com/programs/)
- [AltStore](https://altstore.io/)
- [GitHub Actions macOS runner](https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners)

---

## 下一步行动

准备开始 iOS 开发时：
1. 决定使用免费方案还是付费方案
2. 如选付费方案，先注册 Apple Developer Program
3. 告诉 Claude 开始迁移项目到 KMP 结构
