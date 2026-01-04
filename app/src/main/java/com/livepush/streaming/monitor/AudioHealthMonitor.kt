package com.livepush.streaming.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors audio pipeline health in real-time during streaming sessions.
 *
 * This monitor tracks:
 * - Buffer overflow and underrun events
 * - Audio latency measurements
 * - Memory usage and leak detection
 * - Overall pipeline health status
 *
 * Designed for extended streaming sessions (4+ hours) with automatic
 * degradation detection and recovery triggering.
 */
@Singleton
class AudioHealthMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _healthMetrics = MutableStateFlow(AudioHealthMetrics())
    val healthMetrics: StateFlow<AudioHealthMetrics> = _healthMetrics.asStateFlow()

    private val _healthStatus = MutableStateFlow<AudioHealthStatus>(AudioHealthStatus.Idle)
    val healthStatus: StateFlow<AudioHealthStatus> = _healthStatus.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitoringJob: Job? = null

    private var sessionStartTime: Long = 0L
    private var baselineMemoryMb: Long = 0L
    private var lastLatencyCheckTime: Long = 0L

    companion object {
        private const val MONITORING_INTERVAL_MS = 1000L // Check every second
        private const val LATENCY_CHECK_INTERVAL_MS = 5000L // Check latency every 5 seconds

        // Thresholds for health degradation detection
        private const val MAX_BUFFER_OVERFLOW_RATE = 0.01 // 1% overflow rate
        private const val MAX_BUFFER_UNDERRUN_RATE = 0.01 // 1% underrun rate
        private const val MAX_LATENCY_MS = 100L // Maximum acceptable latency
        private const val MAX_MEMORY_GROWTH_MB = 50L // Maximum memory growth per hour
        private const val CRITICAL_MEMORY_GROWTH_MB = 100L // Critical memory growth threshold

        // Recovery trigger thresholds
        private const val OVERFLOW_RECOVERY_THRESHOLD = 10 // Trigger recovery after 10 overflows
        private const val UNDERRUN_RECOVERY_THRESHOLD = 10 // Trigger recovery after 10 underruns
        private const val LATENCY_SPIKE_THRESHOLD_MS = 200L // Trigger recovery on latency spike
    }

    /**
     * Starts health monitoring for the current streaming session.
     * Should be called when streaming begins.
     */
    fun startMonitoring() {
        if (monitoringJob?.isActive == true) {
            Timber.tag("AudioHealth").w("Monitoring already active")
            return
        }

        sessionStartTime = System.currentTimeMillis()
        baselineMemoryMb = getCurrentMemoryUsageMb()
        lastLatencyCheckTime = System.currentTimeMillis()

        _healthStatus.value = AudioHealthStatus.Monitoring(sessionStartTime)
        _healthMetrics.value = AudioHealthMetrics(
            sessionStartTime = sessionStartTime,
            baselineMemoryMb = baselineMemoryMb
        )

        monitoringJob = scope.launch {
            Timber.tag("AudioHealth").d("Health monitoring started, baseline memory: ${baselineMemoryMb}MB")

            while (isActive) {
                try {
                    updateHealthMetrics()
                    checkHealthStatus()
                    delay(MONITORING_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.tag("AudioHealth").e(e, "Error during health monitoring")
                }
            }
        }
    }

    /**
     * Stops health monitoring.
     * Should be called when streaming ends.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null

        val finalMetrics = _healthMetrics.value
        Timber.tag("AudioHealth").d(
            "Health monitoring stopped. Session duration: ${finalMetrics.sessionDurationMs}ms, " +
            "Total overflows: ${finalMetrics.bufferOverflowCount}, " +
            "Total underruns: ${finalMetrics.bufferUnderrunCount}, " +
            "Final latency: ${finalMetrics.currentLatencyMs}ms, " +
            "Memory growth: ${finalMetrics.memoryGrowthMb}MB"
        )

        _healthStatus.value = AudioHealthStatus.Idle
    }

    /**
     * Reports a buffer overflow event.
     * Called by AudioCaptureManager when overflow is detected.
     */
    fun reportBufferOverflow() {
        _healthMetrics.update { metrics ->
            metrics.copy(
                bufferOverflowCount = metrics.bufferOverflowCount + 1,
                lastOverflowTime = System.currentTimeMillis()
            )
        }

        Timber.tag("AudioHealth").w(
            "Buffer overflow detected (total: ${_healthMetrics.value.bufferOverflowCount})"
        )

        checkForRecoveryTrigger()
    }

    /**
     * Reports a buffer underrun event.
     * Called by AudioCaptureManager when underrun is detected.
     */
    fun reportBufferUnderrun() {
        _healthMetrics.update { metrics ->
            metrics.copy(
                bufferUnderrunCount = metrics.bufferUnderrunCount + 1,
                lastUnderrunTime = System.currentTimeMillis()
            )
        }

        Timber.tag("AudioHealth").w(
            "Buffer underrun detected (total: ${_healthMetrics.value.bufferUnderrunCount})"
        )

        checkForRecoveryTrigger()
    }

    /**
     * Updates audio latency measurement.
     * Called by audio pipeline with measured latency value.
     *
     * @param latencyMs Measured audio latency in milliseconds
     */
    fun updateLatency(latencyMs: Long) {
        _healthMetrics.update { metrics ->
            val maxLatency = maxOf(metrics.maxLatencyMs, latencyMs)
            val avgLatency = if (metrics.latencySampleCount > 0) {
                (metrics.averageLatencyMs * metrics.latencySampleCount + latencyMs) /
                (metrics.latencySampleCount + 1)
            } else {
                latencyMs
            }

            metrics.copy(
                currentLatencyMs = latencyMs,
                averageLatencyMs = avgLatency,
                maxLatencyMs = maxLatency,
                latencySampleCount = metrics.latencySampleCount + 1
            )
        }

        Timber.tag("AudioHealth").v("Latency updated: ${latencyMs}ms")

        if (latencyMs > LATENCY_SPIKE_THRESHOLD_MS) {
            Timber.tag("AudioHealth").w("Latency spike detected: ${latencyMs}ms")
            checkForRecoveryTrigger()
        }
    }

    /**
     * Resets all health metrics.
     * Should be called when starting a new streaming session.
     */
    fun reset() {
        monitoringJob?.cancel()
        monitoringJob = null

        sessionStartTime = 0L
        baselineMemoryMb = 0L
        lastLatencyCheckTime = 0L

        _healthMetrics.value = AudioHealthMetrics()
        _healthStatus.value = AudioHealthStatus.Idle

        Timber.tag("AudioHealth").d("Health metrics reset")
    }

    /**
     * Updates health metrics periodically.
     */
    private fun updateHealthMetrics() {
        val currentTime = System.currentTimeMillis()
        val currentMemoryMb = getCurrentMemoryUsageMb()

        _healthMetrics.update { metrics ->
            metrics.copy(
                currentMemoryMb = currentMemoryMb,
                memoryGrowthMb = currentMemoryMb - baselineMemoryMb,
                sessionDurationMs = currentTime - sessionStartTime
            )
        }
    }

    /**
     * Checks overall health status and updates health state.
     */
    private fun checkHealthStatus() {
        val metrics = _healthMetrics.value
        val issues = mutableListOf<String>()

        // Check buffer overflow rate
        if (metrics.bufferOverflowRate > MAX_BUFFER_OVERFLOW_RATE) {
            issues.add("High buffer overflow rate: ${String.format("%.2f", metrics.bufferOverflowRate * 100)}%")
        }

        // Check buffer underrun rate
        if (metrics.bufferUnderrunRate > MAX_BUFFER_UNDERRUN_RATE) {
            issues.add("High buffer underrun rate: ${String.format("%.2f", metrics.bufferUnderrunRate * 100)}%")
        }

        // Check latency
        if (metrics.currentLatencyMs > MAX_LATENCY_MS) {
            issues.add("High latency: ${metrics.currentLatencyMs}ms (max: ${MAX_LATENCY_MS}ms)")
        }

        // Check memory growth
        if (metrics.memoryGrowthMb > MAX_MEMORY_GROWTH_MB) {
            val hourlyGrowth = metrics.memoryGrowthPerHourMb
            if (hourlyGrowth > CRITICAL_MEMORY_GROWTH_MB) {
                issues.add("Critical memory growth: ${String.format("%.1f", hourlyGrowth)}MB/hour")
            } else {
                issues.add("High memory growth: ${String.format("%.1f", hourlyGrowth)}MB/hour")
            }
        }

        // Update health status
        _healthStatus.value = when {
            issues.isEmpty() -> {
                AudioHealthStatus.Healthy(metrics)
            }
            issues.size <= 2 -> {
                AudioHealthStatus.Degraded(issues)
            }
            else -> {
                AudioHealthStatus.Critical(issues)
            }
        }

        // Log health status periodically
        if (issues.isNotEmpty()) {
            Timber.tag("AudioHealth").w("Health issues detected: ${issues.joinToString(", ")}")
        } else {
            Timber.tag("AudioHealth").v("Health check OK - Duration: ${metrics.sessionDurationMs / 1000}s")
        }
    }

    /**
     * Checks if automatic recovery should be triggered.
     */
    private fun checkForRecoveryTrigger() {
        val metrics = _healthMetrics.value

        val shouldRecover = when {
            metrics.bufferOverflowCount >= OVERFLOW_RECOVERY_THRESHOLD -> {
                Timber.tag("AudioHealth").e("Recovery triggered: overflow threshold reached")
                true
            }
            metrics.bufferUnderrunCount >= UNDERRUN_RECOVERY_THRESHOLD -> {
                Timber.tag("AudioHealth").e("Recovery triggered: underrun threshold reached")
                true
            }
            metrics.currentLatencyMs > LATENCY_SPIKE_THRESHOLD_MS -> {
                Timber.tag("AudioHealth").e("Recovery triggered: latency spike detected")
                true
            }
            else -> false
        }

        if (shouldRecover) {
            _healthStatus.value = AudioHealthStatus.RecoveryNeeded(
                reason = "Buffer health degradation detected",
                metrics = metrics
            )
        }
    }

    /**
     * Gets current memory usage for the app process.
     *
     * @return Memory usage in megabytes
     */
    private fun getCurrentMemoryUsageMb(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val pid = Process.myPid()
        val processMemoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))

        return if (processMemoryInfo.isNotEmpty()) {
            // Total PSS (Proportional Set Size) in KB, convert to MB
            (processMemoryInfo[0].totalPss / 1024).toLong()
        } else {
            0L
        }
    }

    /**
     * Releases all resources.
     */
    fun release() {
        stopMonitoring()
        Timber.tag("AudioHealth").d("AudioHealthMonitor released")
    }
}

/**
 * Audio health status sealed class.
 */
sealed class AudioHealthStatus {
    object Idle : AudioHealthStatus()
    data class Monitoring(val startTime: Long) : AudioHealthStatus()
    data class Healthy(val metrics: AudioHealthMetrics) : AudioHealthStatus()
    data class Degraded(val issues: List<String>) : AudioHealthStatus()
    data class Critical(val issues: List<String>) : AudioHealthStatus()
    data class RecoveryNeeded(val reason: String, val metrics: AudioHealthMetrics) : AudioHealthStatus()
}

/**
 * Audio health metrics data class.
 * Tracks various health indicators for the audio pipeline.
 */
data class AudioHealthMetrics(
    val sessionStartTime: Long = 0L,
    val sessionDurationMs: Long = 0L,

    // Buffer health
    val bufferOverflowCount: Long = 0L,
    val bufferUnderrunCount: Long = 0L,
    val lastOverflowTime: Long = 0L,
    val lastUnderrunTime: Long = 0L,

    // Latency tracking
    val currentLatencyMs: Long = 0L,
    val averageLatencyMs: Long = 0L,
    val maxLatencyMs: Long = 0L,
    val latencySampleCount: Long = 0L,

    // Memory tracking
    val baselineMemoryMb: Long = 0L,
    val currentMemoryMb: Long = 0L,
    val memoryGrowthMb: Long = 0L
) {
    /**
     * Buffer overflow rate (overflows per second).
     */
    val bufferOverflowRate: Double
        get() = if (sessionDurationMs > 0) {
            bufferOverflowCount.toDouble() / (sessionDurationMs / 1000.0)
        } else {
            0.0
        }

    /**
     * Buffer underrun rate (underruns per second).
     */
    val bufferUnderrunRate: Double
        get() = if (sessionDurationMs > 0) {
            bufferUnderrunCount.toDouble() / (sessionDurationMs / 1000.0)
        } else {
            0.0
        }

    /**
     * Memory growth rate (MB per hour).
     */
    val memoryGrowthPerHourMb: Double
        get() = if (sessionDurationMs > 0) {
            memoryGrowthMb.toDouble() / (sessionDurationMs / 3600000.0)
        } else {
            0.0
        }

    /**
     * Overall health score (0.0 = critical, 1.0 = perfect).
     */
    val healthScore: Double
        get() {
            var score = 1.0

            // Deduct for buffer issues
            if (bufferOverflowRate > 0.01) score -= 0.3
            if (bufferUnderrunRate > 0.01) score -= 0.3

            // Deduct for latency issues
            if (currentLatencyMs > 100) score -= 0.2
            if (currentLatencyMs > 200) score -= 0.2

            // Deduct for memory issues
            if (memoryGrowthPerHourMb > 50) score -= 0.1
            if (memoryGrowthPerHourMb > 100) score -= 0.2

            return maxOf(0.0, score)
        }

    /**
     * Whether the audio pipeline is healthy.
     */
    val isHealthy: Boolean
        get() = healthScore >= 0.7

    /**
     * Session duration in human-readable format.
     */
    val sessionDurationFormatted: String
        get() {
            val seconds = sessionDurationMs / 1000
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, secs)
        }
}
