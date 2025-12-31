# LivePush 技术架构文档

## 1. 整体架构

### 1.1 分层架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Presentation Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │
│  │   Compose   │  │  ViewModel  │  │  Navigation │                  │
│  │     UI      │  │   + State   │  │   Graph     │                  │
│  └─────────────┘  └─────────────┘  └─────────────┘                  │
├─────────────────────────────────────────────────────────────────────┤
│                         Domain Layer                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │
│  │   UseCase   │  │   Entity    │  │  Repository │                  │
│  │             │  │   Model     │  │  Interface  │                  │
│  └─────────────┘  └─────────────┘  └─────────────┘                  │
├─────────────────────────────────────────────────────────────────────┤
│                          Data Layer                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │
│  │  Repository │  │ DataSource  │  │   Mapper    │                  │
│  │    Impl     │  │ Local/Remote│  │             │                  │
│  └─────────────┘  └─────────────┘  └─────────────┘                  │
├─────────────────────────────────────────────────────────────────────┤
│                        Streaming Layer                               │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                     StreamManager                              │  │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐             │  │
│  │  │ Capture │ │ Encoder │ │  Muxer  │ │  Pusher │             │  │
│  │  │ Module  │ │ Module  │ │ Module  │ │ Module  │             │  │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘             │  │
│  └───────────────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────────┤
│                        Platform Layer                                │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐       │
│  │ CameraX │ │MediaCodec│ │ OpenGL  │ │  RTMP   │ │ WebRTC  │       │
│  │   API   │ │   API   │ │   ES    │ │  Lib    │ │  Lib    │       │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘       │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 模块依赖关系

```
                    ┌─────────┐
                    │   app   │
                    └────┬────┘
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
    ┌──────────┐  ┌──────────┐  ┌──────────┐
    │   :ui    │  │ :domain  │  │:streaming│
    └────┬─────┘  └────┬─────┘  └────┬─────┘
         │             │              │
         └──────┬──────┘              │
                ▼                     │
          ┌──────────┐                │
          │  :data   │                │
          └────┬─────┘                │
               │                      │
               └──────────┬───────────┘
                          ▼
                    ┌──────────┐
                    │  :core   │
                    └──────────┘
```

---

## 2. 推流核心架构

### 2.1 数据流管道

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Data Pipeline                                │
│                                                                      │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐          │
│  │  Camera │───▶│ Surface │───▶│ OpenGL  │───▶│ Encoder │          │
│  │ Preview │    │ Texture │    │ Process │    │ Surface │          │
│  └─────────┘    └─────────┘    └─────────┘    └────┬────┘          │
│                                                     │                │
│                                                     ▼                │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐          │
│  │   Mic   │───▶│ AudioRec│───▶│  Audio  │───▶│  Muxer  │          │
│  │  Input  │    │  Buffer │    │ Encoder │    │ FLV/RTP │          │
│  └─────────┘    └─────────┘    └─────────┘    └────┬────┘          │
│                                                     │                │
│                                                     ▼                │
│                                               ┌─────────┐           │
│                                               │ Network │           │
│                                               │  Pusher │           │
│                                               └─────────┘           │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 核心类设计

```kotlin
// 推流管理器接口
interface StreamManager {
    val state: StateFlow<StreamState>
    val stats: StateFlow<StreamStats>

    suspend fun prepare(config: StreamConfig): Result<Unit>
    suspend fun startPreview(surface: Surface)
    suspend fun startStream(url: String): Result<Unit>
    suspend fun stopStream()
    suspend fun release()

    fun switchCamera()
    fun setMute(mute: Boolean)
    fun setFlash(enabled: Boolean)
}

// 推流状态
sealed class StreamState {
    object Idle : StreamState()
    object Preparing : StreamState()
    object Previewing : StreamState()
    object Connecting : StreamState()
    data class Streaming(val startTime: Long) : StreamState()
    object Reconnecting : StreamState()
    data class Error(val error: StreamError) : StreamState()
}

// 推流统计
data class StreamStats(
    val videoBitrate: Long,      // bps
    val audioBitrate: Long,      // bps
    val fps: Float,
    val droppedFrames: Int,
    val rtt: Long,               // ms (WebRTC)
    val duration: Long           // ms
)

// 推流配置
data class StreamConfig(
    val videoConfig: VideoConfig,
    val audioConfig: AudioConfig,
    val protocol: StreamProtocol
)

data class VideoConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitrate: Int = 2_000_000,
    val codec: VideoCodec = VideoCodec.H264,
    val keyFrameInterval: Int = 2
)

data class AudioConfig(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val bitrate: Int = 128_000,
    val codec: AudioCodec = AudioCodec.AAC
)

enum class StreamProtocol { RTMP, WEBRTC }
enum class VideoCodec { H264, H265 }
enum class AudioCodec { AAC, OPUS }
```

### 2.3 RTMP 推流实现

```
┌─────────────────────────────────────────────────────────────────────┐
│                      RtmpStreamManager                               │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                    CameraXCapture                            │    │
│  │  ┌───────────┐    ┌───────────┐    ┌───────────┐            │    │
│  │  │  Preview  │    │  Analysis │    │  VideoCapture          │    │
│  │  │  UseCase  │    │  UseCase  │    │  UseCase   │            │    │
│  │  └───────────┘    └───────────┘    └─────┬─────┘            │    │
│  └──────────────────────────────────────────┼──────────────────┘    │
│                                              │                       │
│  ┌──────────────────────────────────────────┼──────────────────┐    │
│  │              OpenGLProcessor              │                  │    │
│  │  ┌───────────┐    ┌───────────┐    ┌─────▼─────┐            │    │
│  │  │  Beauty   │───▶│  Filter   │───▶│ Watermark │            │    │
│  │  │  Effect   │    │  Effect   │    │  Overlay  │            │    │
│  │  └───────────┘    └───────────┘    └─────┬─────┘            │    │
│  └──────────────────────────────────────────┼──────────────────┘    │
│                                              │                       │
│  ┌──────────────────────────────────────────┼──────────────────┐    │
│  │              MediaCodecEncoder            │                  │    │
│  │  ┌───────────┐                      ┌─────▼─────┐           │    │
│  │  │   Audio   │                      │   Video   │           │    │
│  │  │  Encoder  │                      │  Encoder  │           │    │
│  │  │  (AAC)    │                      │  (H.264)  │           │    │
│  │  └─────┬─────┘                      └─────┬─────┘           │    │
│  └────────┼──────────────────────────────────┼─────────────────┘    │
│           │                                  │                       │
│  ┌────────┼──────────────────────────────────┼─────────────────┐    │
│  │        ▼           FlvMuxer               ▼                 │    │
│  │  ┌───────────┐                      ┌───────────┐           │    │
│  │  │   Audio   │─────────┬────────────│   Video   │           │    │
│  │  │   Frame   │         │            │   Frame   │           │    │
│  │  └───────────┘         ▼            └───────────┘           │    │
│  │                  ┌───────────┐                               │    │
│  │                  │  FLV Tag  │                               │    │
│  │                  └─────┬─────┘                               │    │
│  └────────────────────────┼────────────────────────────────────┘    │
│                           │                                          │
│  ┌────────────────────────┼────────────────────────────────────┐    │
│  │                        ▼    RtmpClient                       │    │
│  │  ┌───────────┐   ┌───────────┐   ┌───────────┐              │    │
│  │  │ Handshake │──▶│  Connect  │──▶│  Publish  │              │    │
│  │  └───────────┘   └───────────┘   └─────┬─────┘              │    │
│  │                                        │                     │    │
│  │                                        ▼                     │    │
│  │                              ┌───────────────────┐           │    │
│  │                              │   Send Packets    │           │    │
│  │                              │  (TCP Socket)     │           │    │
│  │                              └───────────────────┘           │    │
│  └──────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.4 WebRTC 推流实现

```
┌─────────────────────────────────────────────────────────────────────┐
│                     WebRtcStreamManager                              │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                   SignalingClient                            │    │
│  │  ┌───────────┐    ┌───────────┐    ┌───────────┐            │    │
│  │  │ WebSocket │───▶│  Message  │───▶│  Handler  │            │    │
│  │  │  Client   │    │  Parser   │    │  Callback │            │    │
│  │  └───────────┘    └───────────┘    └───────────┘            │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                   PeerConnection                             │    │
│  │                                                              │    │
│  │  ┌─────────────────────────────────────────────────────┐    │    │
│  │  │                  ICE Agent                           │    │    │
│  │  │  ┌─────────┐  ┌─────────┐  ┌─────────┐              │    │    │
│  │  │  │  STUN   │  │  TURN   │  │  Host   │              │    │    │
│  │  │  │ Client  │  │ Client  │  │Candidate│              │    │    │
│  │  │  └─────────┘  └─────────┘  └─────────┘              │    │    │
│  │  └─────────────────────────────────────────────────────┘    │    │
│  │                                                              │    │
│  │  ┌───────────────────────┐  ┌───────────────────────┐       │    │
│  │  │     VideoTrack        │  │     AudioTrack        │       │    │
│  │  │  ┌───────────────┐    │  │  ┌───────────────┐    │       │    │
│  │  │  │ CameraCapturer│    │  │  │ AudioCapturer │    │       │    │
│  │  │  └───────────────┘    │  │  └───────────────┘    │       │    │
│  │  │  ┌───────────────┐    │  │  ┌───────────────┐    │       │    │
│  │  │  │ VideoEncoder  │    │  │  │ AudioEncoder  │    │       │    │
│  │  │  │ (VP8/H264)    │    │  │  │ (Opus)        │    │       │    │
│  │  │  └───────────────┘    │  │  └───────────────┘    │       │    │
│  │  └───────────────────────┘  └───────────────────────┘       │    │
│  │                                                              │    │
│  │  ┌─────────────────────────────────────────────────────┐    │    │
│  │  │                 DTLS/SRTP Transport                  │    │    │
│  │  │  ┌─────────┐  ┌─────────┐  ┌─────────┐              │    │    │
│  │  │  │  DTLS   │  │  SRTP   │  │   RTP   │              │    │    │
│  │  │  │Handshake│  │ Encrypt │  │ Packetize│             │    │    │
│  │  │  └─────────┘  └─────────┘  └─────────┘              │    │    │
│  │  └─────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│                              ┌───────────────┐                       │
│                              │  UDP Socket   │                       │
│                              │   (P2P/SFU)   │                       │
│                              └───────────────┘                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. 状态管理

### 3.1 ViewModel 设计

```kotlin
@HiltViewModel
class StreamViewModel @Inject constructor(
    private val streamManager: StreamManager,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamUiState())
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    val streamState: StateFlow<StreamState> = streamManager.state
    val streamStats: StateFlow<StreamStats> = streamManager.stats

    fun startStream(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            streamManager.startStream(url)
                .onSuccess {
                    historyRepository.addHistory(url)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun stopStream() {
        viewModelScope.launch {
            streamManager.stopStream()
        }
    }

    // ... 其他方法
}

data class StreamUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val showBeautyPanel: Boolean = false,
    val beautyConfig: BeautyConfig = BeautyConfig()
)
```

### 3.2 状态流转图

```
                              ┌──────────────┐
                              │     Idle     │
                              └──────┬───────┘
                                     │ prepare()
                                     ▼
                              ┌──────────────┐
                              │  Preparing   │
                              └──────┬───────┘
                                     │ success
                                     ▼
                              ┌──────────────┐
                   ┌──────────│  Previewing  │◀──────────┐
                   │          └──────┬───────┘           │
                   │                 │ startStream()     │
                   │                 ▼                   │
                   │          ┌──────────────┐           │
                   │          │  Connecting  │           │
                   │          └──────┬───────┘           │
                   │    ┌────────────┼────────────┐      │
                   │    ▼            │            ▼      │
                   │ ┌──────┐        │      ┌──────────┐ │
                   │ │Error │        │      │Streaming │─┤
                   │ └──┬───┘        │      └────┬─────┘ │
                   │    │            │           │       │
                   │    └────────────┼───────────┘       │
                   │                 │ disconnect        │
                   │                 ▼                   │
                   │          ┌──────────────┐           │
                   │          │ Reconnecting │───────────┘
                   │          └──────────────┘ success
                   │ stopStream()
                   └─────────────────────────────────────┘
```

---

## 4. 网络层设计

### 4.1 重连策略

```kotlin
class ReconnectionStrategy(
    private val maxAttempts: Int = 5,
    private val baseDelay: Long = 2000L,
    private val maxDelay: Long = 30000L
) {
    private var currentAttempt = 0

    fun shouldRetry(): Boolean = currentAttempt < maxAttempts

    fun getNextDelay(): Long {
        val delay = (baseDelay * 2.0.pow(currentAttempt)).toLong()
            .coerceAtMost(maxDelay)
        currentAttempt++
        return delay
    }

    fun reset() {
        currentAttempt = 0
    }
}
```

### 4.2 自适应码率 (ABR)

```kotlin
class AdaptiveBitrateController(
    private val encoder: VideoEncoder,
    private val minBitrate: Int = 500_000,
    private val maxBitrate: Int = 4_000_000
) {
    private var currentBitrate = maxBitrate

    fun onNetworkQualityChanged(quality: NetworkQuality) {
        val newBitrate = when (quality) {
            NetworkQuality.EXCELLENT -> maxBitrate
            NetworkQuality.GOOD -> (maxBitrate * 0.75).toInt()
            NetworkQuality.MODERATE -> (maxBitrate * 0.5).toInt()
            NetworkQuality.POOR -> (maxBitrate * 0.25).toInt()
            NetworkQuality.BAD -> minBitrate
        }

        if (newBitrate != currentBitrate) {
            currentBitrate = newBitrate
            encoder.setBitrate(currentBitrate)
        }
    }
}

enum class NetworkQuality {
    EXCELLENT, GOOD, MODERATE, POOR, BAD
}
```

---

## 5. 线程模型

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Thread Model                                 │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                      Main Thread                             │    │
│  │  • UI 渲染 (Compose)                                         │    │
│  │  • 用户交互处理                                               │    │
│  │  • ViewModel 状态更新                                         │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                     Camera Thread                            │    │
│  │  • CameraX 相机采集回调                                       │    │
│  │  • 图像数据处理                                               │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                      GL Thread                               │    │
│  │  • OpenGL ES 渲染                                            │    │
│  │  • 美颜/滤镜处理                                              │    │
│  │  • Surface 绘制                                               │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                    Encoder Thread                            │    │
│  │  • MediaCodec 编码                                           │    │
│  │  • 编码数据输出                                               │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                    Network Thread                            │    │
│  │  • RTMP 数据发送                                             │    │
│  │  • WebRTC 数据传输                                            │    │
│  │  • 网络状态监控                                               │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                     Audio Thread                             │    │
│  │  • AudioRecord 采集                                          │    │
│  │  • 音频编码                                                   │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. 依赖注入

### 6.1 Hilt 模块设计

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object StreamingModule {

    @Provides
    @Singleton
    fun provideStreamManager(
        @ApplicationContext context: Context,
        rtmpClient: RtmpClient,
        webRtcClient: WebRtcClient,
        cameraCapture: CameraCapture,
        encoder: MediaCodecEncoder
    ): StreamManager {
        return StreamManagerImpl(context, rtmpClient, webRtcClient, cameraCapture, encoder)
    }

    @Provides
    @Singleton
    fun provideRtmpClient(): RtmpClient {
        return RtmpClientImpl()
    }

    @Provides
    @Singleton
    fun provideWebRtcClient(
        @ApplicationContext context: Context
    ): WebRtcClient {
        return WebRtcClientImpl(context)
    }

    @Provides
    @Singleton
    fun provideCameraCapture(
        @ApplicationContext context: Context
    ): CameraCapture {
        return CameraXCapture(context)
    }

    @Provides
    @Singleton
    fun provideEncoder(): MediaCodecEncoder {
        return MediaCodecEncoderImpl()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "livepush.db")
            .build()
    }

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
}
```

---

## 7. 错误处理

### 7.1 错误类型定义

```kotlin
sealed class StreamError : Exception() {
    // 连接错误
    data class ConnectionFailed(override val message: String) : StreamError()
    data class ConnectionTimeout(val timeout: Long) : StreamError()
    data class ConnectionLost(val reason: String) : StreamError()

    // 编码错误
    data class EncoderNotSupported(val codec: String) : StreamError()
    data class EncoderConfigFailed(override val message: String) : StreamError()

    // 设备错误
    object CameraNotAvailable : StreamError()
    object MicrophoneNotAvailable : StreamError()
    data class PermissionDenied(val permission: String) : StreamError()

    // 网络错误
    object NetworkUnavailable : StreamError()
    data class ServerError(val code: Int, override val message: String) : StreamError()

    // WebRTC 错误
    data class SignalingFailed(override val message: String) : StreamError()
    data class IceFailed(val reason: String) : StreamError()
}
```

### 7.2 错误恢复策略

| 错误类型 | 恢复策略 |
|----------|----------|
| ConnectionLost | 自动重连 (指数退避) |
| ConnectionTimeout | 重试 3 次后提示用户 |
| EncoderNotSupported | 降级到软编码 |
| NetworkUnavailable | 监听网络恢复后自动重连 |
| IceFailed | ICE Restart |

---

## 8. 性能优化

### 8.1 内存优化

- 使用 SurfaceTexture 避免 YUV 数据拷贝
- 复用编码器输出 ByteBuffer
- 及时释放不用的 Surface 和 Texture

### 8.2 功耗优化

- 后台推流时降低帧率至 15fps
- 使用硬件编码器
- 合理设置编码 Profile (Baseline for compatibility)

### 8.3 延迟优化

- 禁用 B 帧
- 减小 GOP 间隔
- 使用零拷贝数据传递
- WebRTC 使用 UDP 传输

---

## 9. 测试策略

### 9.1 单元测试

- StreamManager 状态机测试
- 编码参数验证测试
- 重连策略逻辑测试
- ABR 算法测试

### 9.2 集成测试

- RTMP 推流端到端测试
- WebRTC 连接建立测试
- 编码器兼容性测试

### 9.3 性能测试

- 长时间推流稳定性测试 (> 4 小时)
- 弱网环境模拟测试
- 内存泄漏检测
- CPU/GPU 占用监控
