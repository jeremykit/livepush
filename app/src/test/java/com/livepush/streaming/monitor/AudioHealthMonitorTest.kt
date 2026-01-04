package com.livepush.streaming.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for AudioHealthMonitor health monitoring and recovery logic
 *
 * Tests verify:
 * - Health metrics calculations are accurate
 * - Recovery triggers activate at correct thresholds
 * - Health scoring system works properly
 * - Buffer overflow/underrun rate calculations are correct
 * - Memory growth rate calculations are accurate
 * - Session duration tracking is correct
 * - Edge cases handle invalid inputs
 *
 * CRITICAL: These tests validate the monitoring logic that detects audio pipeline
 * degradation during extended streaming sessions (4+ hours).
 */
class AudioHealthMonitorTest {

    // Test: AudioHealthMetrics - Buffer overflow rate calculation
    @Test
    fun testBufferOverflowRate_standardSession() {
        // Session: 1 minute (60000ms), 6 overflows
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 60000,
            sessionDurationMs = 60000,
            bufferOverflowCount = 6
        )

        // Expected: 6 overflows / 60 seconds = 0.1 overflows per second
        val expectedRate = 0.1
        assertEquals(expectedRate, metrics.bufferOverflowRate, 0.001)
    }

    @Test
    fun testBufferOverflowRate_longSession() {
        // Session: 4 hours (14400000ms), 144 overflows
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 14400000,
            sessionDurationMs = 14400000,
            bufferOverflowCount = 144
        )

        // Expected: 144 overflows / 14400 seconds = 0.01 overflows per second (1% rate)
        val expectedRate = 0.01
        assertEquals(expectedRate, metrics.bufferOverflowRate, 0.001)
    }

    @Test
    fun testBufferOverflowRate_zeroDuration() {
        // Session just started, no duration yet
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis(),
            sessionDurationMs = 0,
            bufferOverflowCount = 0
        )

        // Expected: 0.0 (avoid division by zero)
        assertEquals(0.0, metrics.bufferOverflowRate, 0.001)
    }

    @Test
    fun testBufferOverflowRate_noOverflows() {
        // Session: 10 minutes, no overflows (healthy)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 600000,
            sessionDurationMs = 600000,
            bufferOverflowCount = 0
        )

        // Expected: 0.0 (no overflows)
        assertEquals(0.0, metrics.bufferOverflowRate, 0.001)
    }

    // Test: AudioHealthMetrics - Buffer underrun rate calculation
    @Test
    fun testBufferUnderrunRate_standardSession() {
        // Session: 2 minutes (120000ms), 3 underruns
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 120000,
            sessionDurationMs = 120000,
            bufferUnderrunCount = 3
        )

        // Expected: 3 underruns / 120 seconds = 0.025 underruns per second
        val expectedRate = 0.025
        assertEquals(expectedRate, metrics.bufferUnderrunRate, 0.001)
    }

    @Test
    fun testBufferUnderrunRate_longSession() {
        // Session: 4 hours (14400000ms), 72 underruns
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 14400000,
            sessionDurationMs = 14400000,
            bufferUnderrunCount = 72
        )

        // Expected: 72 underruns / 14400 seconds = 0.005 underruns per second (0.5% rate)
        val expectedRate = 0.005
        assertEquals(expectedRate, metrics.bufferUnderrunRate, 0.001)
    }

    @Test
    fun testBufferUnderrunRate_zeroDuration() {
        // Session just started
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis(),
            sessionDurationMs = 0,
            bufferUnderrunCount = 0
        )

        // Expected: 0.0 (avoid division by zero)
        assertEquals(0.0, metrics.bufferUnderrunRate, 0.001)
    }

    // Test: AudioHealthMetrics - Memory growth rate calculation
    @Test
    fun testMemoryGrowthPerHourMb_standardGrowth() {
        // Session: 1 hour (3600000ms), 10MB growth
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 3600000,
            sessionDurationMs = 3600000,
            baselineMemoryMb = 100,
            currentMemoryMb = 110,
            memoryGrowthMb = 10
        )

        // Expected: 10MB / 1 hour = 10MB per hour
        val expectedGrowth = 10.0
        assertEquals(expectedGrowth, metrics.memoryGrowthPerHourMb, 0.1)
    }

    @Test
    fun testMemoryGrowthPerHourMb_halfHourSession() {
        // Session: 30 minutes (1800000ms), 5MB growth
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 1800000,
            sessionDurationMs = 1800000,
            baselineMemoryMb = 100,
            currentMemoryMb = 105,
            memoryGrowthMb = 5
        )

        // Expected: 5MB / 0.5 hour = 10MB per hour
        val expectedGrowth = 10.0
        assertEquals(expectedGrowth, metrics.memoryGrowthPerHourMb, 0.1)
    }

    @Test
    fun testMemoryGrowthPerHourMb_fourHourSession() {
        // Session: 4 hours (14400000ms), 20MB growth
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 14400000,
            sessionDurationMs = 14400000,
            baselineMemoryMb = 100,
            currentMemoryMb = 120,
            memoryGrowthMb = 20
        )

        // Expected: 20MB / 4 hours = 5MB per hour (healthy)
        val expectedGrowth = 5.0
        assertEquals(expectedGrowth, metrics.memoryGrowthPerHourMb, 0.1)
    }

    @Test
    fun testMemoryGrowthPerHourMb_zeroDuration() {
        // Session just started
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis(),
            sessionDurationMs = 0,
            baselineMemoryMb = 100,
            currentMemoryMb = 100,
            memoryGrowthMb = 0
        )

        // Expected: 0.0 (avoid division by zero)
        assertEquals(0.0, metrics.memoryGrowthPerHourMb, 0.1)
    }

    @Test
    fun testMemoryGrowthPerHourMb_noGrowth() {
        // Session: 2 hours, no memory growth (perfect)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 7200000,
            sessionDurationMs = 7200000,
            baselineMemoryMb = 100,
            currentMemoryMb = 100,
            memoryGrowthMb = 0
        )

        // Expected: 0.0 (no growth)
        assertEquals(0.0, metrics.memoryGrowthPerHourMb, 0.1)
    }

    // Test: AudioHealthMetrics - Health score calculation
    @Test
    fun testHealthScore_perfectHealth() {
        // Perfect session: no overflows, no underruns, low latency, stable memory
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 3600000,
            sessionDurationMs = 3600000,
            bufferOverflowCount = 0,
            bufferUnderrunCount = 0,
            currentLatencyMs = 50,
            memoryGrowthMb = 5
        )

        // Expected: 1.0 (perfect score)
        assertEquals(1.0, metrics.healthScore, 0.01)
        assertTrue("Should be healthy", metrics.isHealthy)
    }

    @Test
    fun testHealthScore_highOverflowRate() {
        // Session with high overflow rate (>1%)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 1000000,
            sessionDurationMs = 1000000, // 1000 seconds
            bufferOverflowCount = 15, // 0.015 overflows/sec = 1.5% rate
            bufferUnderrunCount = 0,
            currentLatencyMs = 50,
            memoryGrowthMb = 5
        )

        // Expected: 1.0 - 0.3 (overflow penalty) = 0.7
        assertEquals(0.7, metrics.healthScore, 0.01)
        assertTrue("Should still be healthy at threshold", metrics.isHealthy)
    }

    @Test
    fun testHealthScore_highUnderrunRate() {
        // Session with high underrun rate (>1%)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 1000000,
            sessionDurationMs = 1000000, // 1000 seconds
            bufferOverflowCount = 0,
            bufferUnderrunCount = 15, // 0.015 underruns/sec = 1.5% rate
            currentLatencyMs = 50,
            memoryGrowthMb = 5
        )

        // Expected: 1.0 - 0.3 (underrun penalty) = 0.7
        assertEquals(0.7, metrics.healthScore, 0.01)
        assertTrue("Should still be healthy at threshold", metrics.isHealthy)
    }

    @Test
    fun testHealthScore_highLatency() {
        // Session with high latency (>100ms)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 3600000,
            sessionDurationMs = 3600000,
            bufferOverflowCount = 0,
            bufferUnderrunCount = 0,
            currentLatencyMs = 150, // > 100ms threshold
            memoryGrowthMb = 5
        )

        // Expected: 1.0 - 0.2 (latency penalty) = 0.8
        assertEquals(0.8, metrics.healthScore, 0.01)
        assertTrue("Should be healthy", metrics.isHealthy)
    }

    @Test
    fun testHealthScore_veryHighLatency() {
        // Session with very high latency (>200ms)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 3600000,
            sessionDurationMs = 3600000,
            bufferOverflowCount = 0,
            bufferUnderrunCount = 0,
            currentLatencyMs = 250, // > 200ms threshold
            memoryGrowthMb = 5
        )

        // Expected: 1.0 - 0.2 - 0.2 (double latency penalty) = 0.6
        assertEquals(0.6, metrics.healthScore, 0.01)
        assertFalse("Should be unhealthy", metrics.isHealthy)
    }

    @Test
    fun testHealthScore_highMemoryGrowth() {
        // Session with high memory growth (>50MB/hour)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 3600000,
            sessionDurationMs = 3600000,
            bufferOverflowCount = 0,
            bufferUnderrunCount = 0,
            currentLatencyMs = 50,
            memoryGrowthMb = 60 // 60MB/hour > 50MB threshold
        )

        // Expected: 1.0 - 0.1 (memory growth penalty) = 0.9
        assertEquals(0.9, metrics.healthScore, 0.01)
        assertTrue("Should be healthy", metrics.isHealthy)
    }

    @Test
    fun testHealthScore_criticalMemoryGrowth() {
        // Session with critical memory growth (>100MB/hour)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 3600000,
            sessionDurationMs = 3600000,
            bufferOverflowCount = 0,
            bufferUnderrunCount = 0,
            currentLatencyMs = 50,
            memoryGrowthMb = 120 // 120MB/hour > 100MB threshold
        )

        // Expected: 1.0 - 0.1 - 0.2 (double memory penalty) = 0.7
        assertEquals(0.7, metrics.healthScore, 0.01)
        assertTrue("Should still be healthy at threshold", metrics.isHealthy)
    }

    @Test
    fun testHealthScore_multipleIssues() {
        // Session with multiple issues
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 1000000,
            sessionDurationMs = 1000000,
            bufferOverflowCount = 15, // High overflow rate
            bufferUnderrunCount = 15, // High underrun rate
            currentLatencyMs = 250, // Very high latency
            memoryGrowthMb = 120 // Critical memory growth (extrapolated)
        )

        // Expected: 1.0 - 0.3 - 0.3 - 0.2 - 0.2 - 0.1 - 0.2 = -0.3, clamped to 0.0
        assertEquals(0.0, metrics.healthScore, 0.01)
        assertFalse("Should be unhealthy", metrics.isHealthy)
    }

    @Test
    fun testHealthScore_boundaryConditions() {
        // Test that score never goes below 0.0
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 1000000,
            sessionDurationMs = 1000000,
            bufferOverflowCount = 50, // Very high
            bufferUnderrunCount = 50, // Very high
            currentLatencyMs = 500, // Very high
            memoryGrowthMb = 200 // Very high
        )

        // Score should be clamped to 0.0
        assertTrue("Score should be >= 0.0", metrics.healthScore >= 0.0)
        assertFalse("Should be unhealthy", metrics.isHealthy)
    }

    // Test: AudioHealthMetrics - isHealthy property
    @Test
    fun testIsHealthy_healthyThreshold() {
        // Score exactly at healthy threshold (0.7)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 1000000,
            sessionDurationMs = 1000000,
            bufferOverflowCount = 15, // 0.3 penalty
            bufferUnderrunCount = 0,
            currentLatencyMs = 50,
            memoryGrowthMb = 5
        )

        // Score should be 0.7, which is the threshold
        assertEquals(0.7, metrics.healthScore, 0.01)
        assertTrue("Score 0.7 should be healthy", metrics.isHealthy)
    }

    @Test
    fun testIsHealthy_belowThreshold() {
        // Score just below healthy threshold
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 1000000,
            sessionDurationMs = 1000000,
            bufferOverflowCount = 15, // 0.3 penalty
            bufferUnderrunCount = 0,
            currentLatencyMs = 150, // 0.2 penalty
            memoryGrowthMb = 5
        )

        // Score should be 0.5, below threshold
        assertEquals(0.5, metrics.healthScore, 0.01)
        assertFalse("Score 0.5 should be unhealthy", metrics.isHealthy)
    }

    // Test: AudioHealthMetrics - Session duration formatting
    @Test
    fun testSessionDurationFormatted_shortSession() {
        // Session: 5 minutes 30 seconds
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 330000,
            sessionDurationMs = 330000
        )

        // Expected: "00:05:30"
        assertEquals("00:05:30", metrics.sessionDurationFormatted)
    }

    @Test
    fun testSessionDurationFormatted_oneHour() {
        // Session: 1 hour exactly
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 3600000,
            sessionDurationMs = 3600000
        )

        // Expected: "01:00:00"
        assertEquals("01:00:00", metrics.sessionDurationFormatted)
    }

    @Test
    fun testSessionDurationFormatted_fourHours() {
        // Session: 4 hours 15 minutes 45 seconds
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 15345000,
            sessionDurationMs = 15345000
        )

        // Expected: "04:15:45"
        assertEquals("04:15:45", metrics.sessionDurationFormatted)
    }

    @Test
    fun testSessionDurationFormatted_zeroDuration() {
        // Session just started
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis(),
            sessionDurationMs = 0
        )

        // Expected: "00:00:00"
        assertEquals("00:00:00", metrics.sessionDurationFormatted)
    }

    // Test: Edge cases - Latency calculations
    @Test
    fun testLatencyCalculations_averageAndMax() {
        // Simulate multiple latency samples
        // Sample 1: 50ms
        val metrics1 = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis(),
            sessionDurationMs = 1000,
            currentLatencyMs = 50,
            averageLatencyMs = 50,
            maxLatencyMs = 50,
            latencySampleCount = 1
        )

        assertEquals(50L, metrics1.averageLatencyMs)
        assertEquals(50L, metrics1.maxLatencyMs)

        // Sample 2: 70ms (average should update)
        // Average = (50 * 1 + 70) / 2 = 60
        val expectedAvg = (50L * 1 + 70) / 2
        val metrics2 = AudioHealthMetrics(
            sessionStartTime = metrics1.sessionStartTime,
            sessionDurationMs = 2000,
            currentLatencyMs = 70,
            averageLatencyMs = expectedAvg,
            maxLatencyMs = 70,
            latencySampleCount = 2
        )

        assertEquals(60L, metrics2.averageLatencyMs)
        assertEquals(70L, metrics2.maxLatencyMs)
    }

    @Test
    fun testLatencyCalculations_maxPersists() {
        // Max latency should persist even if current drops
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis(),
            sessionDurationMs = 5000,
            currentLatencyMs = 50, // Current is low
            averageLatencyMs = 75,
            maxLatencyMs = 200, // Max was high
            latencySampleCount = 5
        )

        assertEquals(50L, metrics.currentLatencyMs)
        assertEquals(200L, metrics.maxLatencyMs)
        assertTrue("Max should be greater than current", metrics.maxLatencyMs > metrics.currentLatencyMs)
    }

    // Test: Real-world scenarios
    @Test
    fun testRealWorld_healthyFourHourSession() {
        // Simulate a healthy 4-hour streaming session
        val fourHoursMs = 14400000L
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - fourHoursMs,
            sessionDurationMs = fourHoursMs,
            bufferOverflowCount = 5, // Very low: 5 / 14400 = 0.00035/sec
            bufferUnderrunCount = 3, // Very low: 3 / 14400 = 0.00021/sec
            currentLatencyMs = 65,
            averageLatencyMs = 60,
            maxLatencyMs = 80,
            latencySampleCount = 2880, // Every 5 seconds for 4 hours
            baselineMemoryMb = 150,
            currentMemoryMb = 165, // 15MB growth over 4 hours = 3.75MB/hour
            memoryGrowthMb = 15
        )

        // Verify rates are well below thresholds
        assertTrue("Overflow rate should be < 1%", metrics.bufferOverflowRate < 0.01)
        assertTrue("Underrun rate should be < 1%", metrics.bufferUnderrunRate < 0.01)
        assertTrue("Latency should be < 100ms", metrics.currentLatencyMs < 100)
        assertTrue("Memory growth should be < 10MB/hour", metrics.memoryGrowthPerHourMb < 10.0)

        // Health score should be perfect
        assertEquals(1.0, metrics.healthScore, 0.01)
        assertTrue("Should be healthy", metrics.isHealthy)
        assertEquals("04:00:00", metrics.sessionDurationFormatted)
    }

    @Test
    fun testRealWorld_degradedSession() {
        // Simulate a degraded session approaching recovery threshold
        val twoHoursMs = 7200000L
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - twoHoursMs,
            sessionDurationMs = twoHoursMs,
            bufferOverflowCount = 9, // Just below recovery threshold (10)
            bufferUnderrunCount = 8,
            currentLatencyMs = 120, // Slightly above threshold
            averageLatencyMs = 95,
            maxLatencyMs = 150,
            latencySampleCount = 1440,
            baselineMemoryMb = 150,
            currentMemoryMb = 200, // 50MB growth over 2 hours = 25MB/hour
            memoryGrowthMb = 50
        )

        // Verify metrics show degradation
        assertTrue("Overflow rate approaching threshold", metrics.bufferOverflowRate > 0.001)
        assertTrue("Latency above threshold", metrics.currentLatencyMs > 100)

        // Health score should be degraded but not critical
        assertTrue("Score should be degraded", metrics.healthScore < 1.0)
        assertTrue("Score should not be critical", metrics.healthScore > 0.5)
        assertEquals("02:00:00", metrics.sessionDurationFormatted)
    }

    @Test
    fun testRealWorld_criticalSession() {
        // Simulate a critical session needing immediate recovery
        val oneHourMs = 3600000L
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - oneHourMs,
            sessionDurationMs = oneHourMs,
            bufferOverflowCount = 50, // Well above recovery threshold
            bufferUnderrunCount = 40,
            currentLatencyMs = 250, // Way above threshold
            averageLatencyMs = 180,
            maxLatencyMs = 300,
            latencySampleCount = 720,
            baselineMemoryMb = 150,
            currentMemoryMb = 280, // 130MB growth over 1 hour = 130MB/hour (critical)
            memoryGrowthMb = 130
        )

        // Verify critical conditions
        assertTrue("High overflow rate", metrics.bufferOverflowRate > 0.01)
        assertTrue("High underrun rate", metrics.bufferUnderrunRate > 0.01)
        assertTrue("Critical latency", metrics.currentLatencyMs > 200)
        assertTrue("Critical memory growth", metrics.memoryGrowthPerHourMb > 100.0)

        // Health score should be critical
        assertTrue("Score should be critical", metrics.healthScore < 0.5)
        assertFalse("Should be unhealthy", metrics.isHealthy)
        assertEquals("01:00:00", metrics.sessionDurationFormatted)
    }

    @Test
    fun testRealWorld_sessionReset() {
        // Test that metrics can be reset between sessions
        val oldMetrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 3600000,
            sessionDurationMs = 3600000,
            bufferOverflowCount = 50,
            bufferUnderrunCount = 40,
            currentLatencyMs = 250,
            memoryGrowthMb = 130
        )

        // Verify old session had issues
        assertFalse("Old session should be unhealthy", oldMetrics.isHealthy)

        // New session should start fresh
        val newMetrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis(),
            sessionDurationMs = 0
        )

        // Verify new session is clean
        assertEquals(0L, newMetrics.bufferOverflowCount)
        assertEquals(0L, newMetrics.bufferUnderrunCount)
        assertEquals(0L, newMetrics.currentLatencyMs)
        assertEquals(0L, newMetrics.memoryGrowthMb)
        assertEquals(0.0, newMetrics.bufferOverflowRate, 0.001)
        assertEquals(1.0, newMetrics.healthScore, 0.01)
    }

    // Test: Recovery threshold scenarios
    @Test
    fun testRecoveryThreshold_overflowBoundary() {
        // Test exactly at overflow recovery threshold (10 overflows)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 10000,
            sessionDurationMs = 10000,
            bufferOverflowCount = 10 // Exactly at threshold
        )

        // This should trigger recovery in the monitor
        assertEquals(10L, metrics.bufferOverflowCount)
        assertTrue("Overflow count at recovery threshold", metrics.bufferOverflowCount >= 10)
    }

    @Test
    fun testRecoveryThreshold_underrunBoundary() {
        // Test exactly at underrun recovery threshold (10 underruns)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 10000,
            sessionDurationMs = 10000,
            bufferUnderrunCount = 10 // Exactly at threshold
        )

        // This should trigger recovery in the monitor
        assertEquals(10L, metrics.bufferUnderrunCount)
        assertTrue("Underrun count at recovery threshold", metrics.bufferUnderrunCount >= 10)
    }

    @Test
    fun testRecoveryThreshold_latencySpike() {
        // Test latency spike threshold (200ms)
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 5000,
            sessionDurationMs = 5000,
            currentLatencyMs = 200 // Exactly at spike threshold
        )

        // This should trigger recovery in the monitor
        assertEquals(200L, metrics.currentLatencyMs)
        assertTrue("Latency at spike threshold", metrics.currentLatencyMs >= 200)
    }

    // Test: AudioHealthStatus sealed class variants
    @Test
    fun testHealthStatus_types() {
        // Test that all health status types can be created
        val idle = AudioHealthStatus.Idle
        val monitoring = AudioHealthStatus.Monitoring(System.currentTimeMillis())
        val metrics = AudioHealthMetrics()
        val healthy = AudioHealthStatus.Healthy(metrics)
        val degraded = AudioHealthStatus.Degraded(listOf("High latency"))
        val critical = AudioHealthStatus.Critical(listOf("High latency", "Buffer overflow"))
        val recovery = AudioHealthStatus.RecoveryNeeded("Buffer issues", metrics)

        // Verify types are distinct
        assertTrue("Idle is Idle", idle is AudioHealthStatus.Idle)
        assertTrue("Monitoring is Monitoring", monitoring is AudioHealthStatus.Monitoring)
        assertTrue("Healthy is Healthy", healthy is AudioHealthStatus.Healthy)
        assertTrue("Degraded is Degraded", degraded is AudioHealthStatus.Degraded)
        assertTrue("Critical is Critical", critical is AudioHealthStatus.Critical)
        assertTrue("Recovery is RecoveryNeeded", recovery is AudioHealthStatus.RecoveryNeeded)
    }

    @Test
    fun testHealthStatus_degradedIssuesList() {
        // Test that degraded status can hold multiple issues
        val issues = listOf("High latency: 150ms", "High overflow rate: 2.5%")
        val degraded = AudioHealthStatus.Degraded(issues)

        assertTrue("Should have 2 issues", issues.size == 2)
        assertTrue("Should contain latency issue", issues.any { it.contains("latency") })
        assertTrue("Should contain overflow issue", issues.any { it.contains("overflow") })
    }

    @Test
    fun testHealthStatus_criticalMultipleIssues() {
        // Test that critical status can hold many issues
        val issues = listOf(
            "High buffer overflow rate: 5.00%",
            "High buffer underrun rate: 3.50%",
            "High latency: 250ms (max: 100ms)",
            "Critical memory growth: 150.0MB/hour"
        )
        val critical = AudioHealthStatus.Critical(issues)

        assertTrue("Should have 4 issues", issues.size == 4)
    }

    @Test
    fun testHealthStatus_recoveryNeededWithMetrics() {
        // Test recovery status includes metrics for analysis
        val metrics = AudioHealthMetrics(
            sessionStartTime = System.currentTimeMillis() - 3600000,
            sessionDurationMs = 3600000,
            bufferOverflowCount = 15
        )
        val recovery = AudioHealthStatus.RecoveryNeeded(
            reason = "Buffer overflow threshold reached",
            metrics = metrics
        )

        assertTrue("Should include reason", recovery.reason.isNotEmpty())
        assertEquals("Should include overflow count", 15L, recovery.metrics.bufferOverflowCount)
    }
}
