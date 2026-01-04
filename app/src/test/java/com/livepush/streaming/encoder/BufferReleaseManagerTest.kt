package com.livepush.streaming.encoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for BufferReleaseManager buffer release and PTS calculation
 *
 * Tests verify:
 * - PTS (Presentation Timestamp) calculation is accurate
 * - PTS is monotonically increasing to prevent audio/video drift
 * - Buffer duration calculations are correct
 * - Statistics tracking works properly
 * - Reset functionality clears state properly
 * - Edge cases handle invalid inputs
 *
 * CRITICAL: These tests validate the core logic that prevents the 25-40 minute
 * crash pattern in long streaming sessions.
 */
class BufferReleaseManagerTest {

    private lateinit var bufferReleaseManager: BufferReleaseManager

    @Before
    fun setUp() {
        bufferReleaseManager = BufferReleaseManager()
        bufferReleaseManager.reset()
    }

    // Test: PTS calculation accuracy
    @Test
    fun testCalculatePresentationTimeUs_standardConfig() {
        // Standard config: 44100 Hz, stereo, 16-bit
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitsPerSample = 16
        )

        // Buffer size: 3528 bytes (20ms of audio)
        val bufferSize = 3528

        // Expected calculation:
        // bytesPerSample = 16 / 8 = 2
        // totalBytesPerSample = 2 channels * 2 = 4
        // sampleCount = 3528 / 4 = 882
        // bufferDurationUs = (882 * 1_000_000) / 44100 = 20000us (20ms)

        val pts1 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)

        // First PTS should be close to base timestamp (within buffer duration)
        assertTrue("First PTS should be >= 0", pts1 >= 0)

        // Second buffer should have PTS increased by buffer duration
        val pts2 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
        val ptsDiff = pts2 - pts1

        // Allow for small monotonic increment (+1) but should be close to 20000us
        assertTrue("PTS difference should be close to 20ms", ptsDiff >= 20000)
        assertTrue("PTS difference should not exceed 21ms", ptsDiff <= 21000)
    }

    @Test
    fun testCalculatePresentationTimeUs_48kHz() {
        // High-quality config: 48000 Hz, stereo, 16-bit
        val config = AudioEncoderConfig(
            sampleRate = 48000,
            channelCount = 2,
            bitsPerSample = 16
        )

        // Buffer size: 3840 bytes (20ms of audio at 48kHz)
        val bufferSize = 3840

        // Expected calculation:
        // bytesPerSample = 16 / 8 = 2
        // totalBytesPerSample = 2 channels * 2 = 4
        // sampleCount = 3840 / 4 = 960
        // bufferDurationUs = (960 * 1_000_000) / 48000 = 20000us (20ms)

        val pts1 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
        val pts2 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
        val ptsDiff = pts2 - pts1

        // Should be approximately 20ms
        assertTrue("PTS difference should be close to 20ms for 48kHz", ptsDiff >= 20000)
        assertTrue("PTS difference should not exceed 21ms for 48kHz", ptsDiff <= 21000)
    }

    @Test
    fun testCalculatePresentationTimeUs_monoConfig() {
        // Mono config: 44100 Hz, mono, 16-bit
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 1,
            bitsPerSample = 16
        )

        // Buffer size: 1764 bytes (20ms of mono audio)
        val bufferSize = 1764

        // Expected calculation:
        // bytesPerSample = 16 / 8 = 2
        // totalBytesPerSample = 1 channel * 2 = 2
        // sampleCount = 1764 / 2 = 882
        // bufferDurationUs = (882 * 1_000_000) / 44100 = 20000us (20ms)

        val pts1 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
        val pts2 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
        val ptsDiff = pts2 - pts1

        // Should be approximately 20ms even for mono
        assertTrue("PTS difference for mono should be close to 20ms", ptsDiff >= 20000)
        assertTrue("PTS difference for mono should not exceed 21ms", ptsDiff <= 21000)
    }

    // Test: PTS monotonically increasing (CRITICAL for long sessions)
    @Test
    fun testCalculatePresentationTimeUs_monotonicIncreasing() {
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitsPerSample = 16
        )
        val bufferSize = 3528

        var previousPts = 0L

        // Simulate 100 consecutive buffers (2 seconds of audio)
        repeat(100) {
            val currentPts = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)

            assertTrue(
                "PTS must be monotonically increasing: iteration $it, " +
                "previous=$previousPts, current=$currentPts",
                currentPts > previousPts
            )

            previousPts = currentPts
        }
    }

    @Test
    fun testCalculatePresentationTimeUs_monotonicIncreasing_longSession() {
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitsPerSample = 16
        )
        val bufferSize = 3528

        // Simulate 1000 buffers (20 seconds of audio)
        // This tests for potential drift accumulation issues
        val ptsList = mutableListOf<Long>()
        repeat(1000) {
            ptsList.add(bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config))
        }

        // Verify all are monotonically increasing
        for (i in 1 until ptsList.size) {
            assertTrue(
                "PTS must increase at index $i: ${ptsList[i-1]} -> ${ptsList[i]}",
                ptsList[i] > ptsList[i-1]
            )
        }

        // Verify total duration is approximately correct
        val totalDurationUs = ptsList.last() - ptsList.first()
        val expectedDurationUs = 1000 * 20000L // 1000 buffers * 20ms each = 20 seconds

        // Allow 1% tolerance for monotonic increment adjustments
        val tolerance = (expectedDurationUs * 0.01).toLong()
        assertTrue(
            "Total duration should be close to expected: " +
            "actual=$totalDurationUs, expected=$expectedDurationUs",
            Math.abs(totalDurationUs - expectedDurationUs) <= tolerance
        )
    }

    @Test
    fun testCalculatePresentationTimeUs_neverDecrease() {
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitsPerSample = 16
        )

        // Test with varying buffer sizes (simulating real-world scenarios)
        val bufferSizes = listOf(3528, 3500, 3600, 3400, 3528)

        var previousPts = 0L
        bufferSizes.forEach { bufferSize ->
            val currentPts = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
            assertTrue(
                "PTS must never decrease even with varying buffer sizes: " +
                "previous=$previousPts, current=$currentPts, bufferSize=$bufferSize",
                currentPts > previousPts
            )
            previousPts = currentPts
        }
    }

    // Test: Buffer duration calculation correctness
    @Test
    fun testBufferDurationCalculation_various16BitConfigs() {
        val testCases = listOf(
            // (sampleRate, channels, bufferSize, expectedDurationUs)
            Triple(44100, 2, 3528, 20000L),  // Standard stereo
            Triple(48000, 2, 3840, 20000L),  // High-quality stereo
            Triple(44100, 1, 1764, 20000L),  // Standard mono
            Triple(48000, 1, 1920, 20000L),  // High-quality mono
            Triple(22050, 2, 1764, 20000L),  // Low-quality stereo
            Triple(22050, 1, 882, 20000L)    // Low-quality mono
        )

        testCases.forEach { (sampleRate, channels, bufferSize, expectedDuration) ->
            bufferReleaseManager.reset()

            val config = AudioEncoderConfig(
                sampleRate = sampleRate,
                channelCount = channels,
                bitsPerSample = 16
            )

            val pts1 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
            val pts2 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
            val actualDuration = pts2 - pts1

            // Allow small tolerance for monotonic increment
            val tolerance = 1000L // 1ms tolerance
            assertTrue(
                "Duration calculation for $sampleRate Hz, $channels ch, $bufferSize bytes: " +
                "expected ~$expectedDuration us, got $actualDuration us",
                Math.abs(actualDuration - expectedDuration) <= tolerance
            )
        }
    }

    @Test
    fun testBufferDurationCalculation_various24BitConfigs() {
        val config = AudioEncoderConfig(
            sampleRate = 48000,
            channelCount = 2,
            bitsPerSample = 24 // 24-bit audio
        )

        // For 24-bit: bytesPerSample = 3, totalBytesPerSample = 6
        // 20ms at 48kHz = 960 samples * 6 bytes = 5760 bytes
        val bufferSize = 5760

        val pts1 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
        val pts2 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
        val duration = pts2 - pts1

        // Should still be approximately 20ms
        assertTrue("24-bit audio duration should be close to 20ms", duration >= 20000)
        assertTrue("24-bit audio duration should not exceed 21ms", duration <= 21000)
    }

    // Test: Reset functionality
    @Test
    fun testReset_clearsPTSState() {
        val config = AudioEncoderConfig()
        val bufferSize = 3528

        // Generate some PTS values
        val pts1 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
        val pts2 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)

        assertTrue("PTS should increase before reset", pts2 > pts1)

        // Reset
        bufferReleaseManager.reset()

        // Generate new PTS - should restart from new base timestamp
        val pts3 = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)

        // After reset, PTS should be independent of previous values
        // It will be based on new System.nanoTime() timestamp
        assertTrue("PTS after reset should be valid", pts3 >= 0)
    }

    @Test
    fun testReset_clearsBufferStats() {
        val stats = bufferReleaseManager.bufferStats.value

        assertEquals("Initial totalBuffersProcessed should be 0", 0L, stats.totalBuffersProcessed)
        assertEquals("Initial totalBytesProcessed should be 0", 0L, stats.totalBytesProcessed)
        assertEquals("Initial bufferErrors should be 0", 0L, stats.bufferErrors)
        assertEquals("Initial releaseErrors should be 0", 0L, stats.releaseErrors)
        assertEquals("Initial lastPresentationTimeUs should be 0", 0L, stats.lastPresentationTimeUs)
    }

    @Test
    fun testReset_allowsMultipleSessions() {
        val config = AudioEncoderConfig()
        val bufferSize = 3528

        // Session 1
        repeat(10) {
            bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
        }

        bufferReleaseManager.reset()

        // Session 2 - should work independently
        var previousPts = 0L
        repeat(10) {
            val currentPts = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)
            if (previousPts > 0) {
                assertTrue("Session 2 PTS should be monotonic", currentPts > previousPts)
            }
            previousPts = currentPts
        }
    }

    // Test: BufferStats functionality
    @Test
    fun testBufferStats_initialState() {
        val stats = bufferReleaseManager.bufferStats.value

        assertEquals("Initial total buffers", 0L, stats.totalBuffersProcessed)
        assertEquals("Initial total bytes", 0L, stats.totalBytesProcessed)
        assertEquals("Initial buffer errors", 0L, stats.bufferErrors)
        assertEquals("Initial release errors", 0L, stats.releaseErrors)
        assertEquals("Initial last PTS", 0L, stats.lastPresentationTimeUs)
        assertEquals("Initial average buffer size", 0L, stats.averageBufferSize)
        assertEquals("Initial error rate", 0.0, stats.errorRate, 0.001)
        assertFalse("Initial hasErrors should be false", stats.hasErrors)
    }

    @Test
    fun testBufferStats_averageBufferSize() {
        // Test the computed property logic
        val stats1 = BufferReleaseManager.BufferStats(
            totalBuffersProcessed = 10L,
            totalBytesProcessed = 35280L
        )
        assertEquals("Average should be 3528", 3528L, stats1.averageBufferSize)

        val stats2 = BufferReleaseManager.BufferStats(
            totalBuffersProcessed = 0L,
            totalBytesProcessed = 0L
        )
        assertEquals("Average should be 0 when no buffers", 0L, stats2.averageBufferSize)
    }

    @Test
    fun testBufferStats_errorRate() {
        // Test error rate calculation
        val stats1 = BufferReleaseManager.BufferStats(
            totalBuffersProcessed = 100L,
            bufferErrors = 2L,
            releaseErrors = 3L
        )
        assertEquals("Error rate should be 5%", 0.05, stats1.errorRate, 0.001)

        val stats2 = BufferReleaseManager.BufferStats(
            totalBuffersProcessed = 0L,
            bufferErrors = 0L,
            releaseErrors = 0L
        )
        assertEquals("Error rate should be 0 when no buffers", 0.0, stats2.errorRate, 0.001)

        val stats3 = BufferReleaseManager.BufferStats(
            totalBuffersProcessed = 1000L,
            bufferErrors = 1L,
            releaseErrors = 0L
        )
        assertEquals("Error rate should be 0.1%", 0.001, stats3.errorRate, 0.0001)
    }

    @Test
    fun testBufferStats_hasErrors() {
        val noErrors = BufferReleaseManager.BufferStats(
            bufferErrors = 0L,
            releaseErrors = 0L
        )
        assertFalse("Should not have errors", noErrors.hasErrors)

        val withBufferError = BufferReleaseManager.BufferStats(
            bufferErrors = 1L,
            releaseErrors = 0L
        )
        assertTrue("Should have errors with buffer error", withBufferError.hasErrors)

        val withReleaseError = BufferReleaseManager.BufferStats(
            bufferErrors = 0L,
            releaseErrors = 1L
        )
        assertTrue("Should have errors with release error", withReleaseError.hasErrors)

        val withBothErrors = BufferReleaseManager.BufferStats(
            bufferErrors = 2L,
            releaseErrors = 3L
        )
        assertTrue("Should have errors with both types", withBothErrors.hasErrors)
    }

    // Test: Edge cases
    @Test
    fun testCalculatePresentationTimeUs_smallBufferSize() {
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitsPerSample = 16
        )

        // Very small buffer: 176 bytes (1ms of audio)
        val smallBufferSize = 176

        val pts1 = bufferReleaseManager.calculatePresentationTimeUs(smallBufferSize, config)
        val pts2 = bufferReleaseManager.calculatePresentationTimeUs(smallBufferSize, config)

        assertTrue("PTS should increase even with small buffers", pts2 > pts1)

        val duration = pts2 - pts1
        // Should be approximately 1ms
        assertTrue("Small buffer duration should be close to 1ms", duration >= 900)
        assertTrue("Small buffer duration should not exceed 2ms", duration <= 2000)
    }

    @Test
    fun testCalculatePresentationTimeUs_largeBufferSize() {
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitsPerSample = 16
        )

        // Large buffer: 17640 bytes (100ms of audio)
        val largeBufferSize = 17640

        val pts1 = bufferReleaseManager.calculatePresentationTimeUs(largeBufferSize, config)
        val pts2 = bufferReleaseManager.calculatePresentationTimeUs(largeBufferSize, config)

        assertTrue("PTS should increase with large buffers", pts2 > pts1)

        val duration = pts2 - pts1
        // Should be approximately 100ms
        assertTrue("Large buffer duration should be close to 100ms", duration >= 100000)
        assertTrue("Large buffer duration should not exceed 102ms", duration <= 102000)
    }

    @Test
    fun testCalculatePresentationTimeUs_zeroBufferSize() {
        val config = AudioEncoderConfig()

        // Zero buffer size - edge case
        val pts1 = bufferReleaseManager.calculatePresentationTimeUs(0, config)
        val pts2 = bufferReleaseManager.calculatePresentationTimeUs(0, config)

        // Even with zero size, PTS must still increase (monotonic requirement)
        assertTrue("PTS must increase even with zero buffer size", pts2 > pts1)

        // Duration should be minimal (just the +1 increment)
        val duration = pts2 - pts1
        assertTrue("Zero buffer should have minimal duration", duration <= 10)
    }

    @Test
    fun testCalculatePresentationTimeUs_oddBufferSizes() {
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitsPerSample = 16
        )

        // Test with non-aligned buffer sizes (real-world scenario)
        val oddBufferSizes = listOf(3527, 3529, 3530, 3525)

        var previousPts = 0L
        oddBufferSizes.forEach { bufferSize ->
            val currentPts = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)

            if (previousPts > 0) {
                assertTrue(
                    "PTS must increase with odd buffer size $bufferSize",
                    currentPts > previousPts
                )
            }
            previousPts = currentPts
        }
    }

    // Test: Real-world scenarios
    @Test
    fun testRealWorld_4HourStreamSimulation() {
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitsPerSample = 16
        )
        val bufferSize = 3528 // 20ms buffers

        // 4 hours = 14400 seconds = 720000 buffers of 20ms each
        // Testing with 1000 buffers as a representative sample
        val sampleBufferCount = 1000

        var previousPts = 0L
        var allMonotonic = true

        repeat(sampleBufferCount) { iteration ->
            val currentPts = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config)

            if (previousPts > 0 && currentPts <= previousPts) {
                allMonotonic = false
            }

            previousPts = currentPts
        }

        assertTrue(
            "PTS must remain monotonic throughout 4-hour session simulation",
            allMonotonic
        )

        // Verify no overflow issues with large PTS values
        assertTrue("Final PTS should be positive and reasonable", previousPts > 0)
        assertTrue("Final PTS should not overflow", previousPts < Long.MAX_VALUE / 2)
    }

    @Test
    fun testRealWorld_mixedSampleRatesInSequence() {
        // Simulate switching between sample rates (e.g., hardware detection change)
        val configs = listOf(
            AudioEncoderConfig(sampleRate = 44100, channelCount = 2, bitsPerSample = 16),
            AudioEncoderConfig(sampleRate = 48000, channelCount = 2, bitsPerSample = 16),
            AudioEncoderConfig(sampleRate = 44100, channelCount = 2, bitsPerSample = 16)
        )

        val bufferSizes = listOf(3528, 3840, 3528)

        var previousPts = 0L

        configs.forEachIndexed { index, config ->
            val currentPts = bufferReleaseManager.calculatePresentationTimeUs(
                bufferSizes[index],
                config
            )

            if (previousPts > 0) {
                assertTrue(
                    "PTS must remain monotonic when switching sample rates at index $index",
                    currentPts > previousPts
                )
            }

            previousPts = currentPts
        }
    }

    @Test
    fun testRealWorld_continuousStreamWithReset() {
        val config = AudioEncoderConfig()
        val bufferSize = 3528

        // First session
        val session1Pts = mutableListOf<Long>()
        repeat(100) {
            session1Pts.add(bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config))
        }

        // Verify session 1 monotonic
        for (i in 1 until session1Pts.size) {
            assertTrue(
                "Session 1 should be monotonic",
                session1Pts[i] > session1Pts[i-1]
            )
        }

        // Reset for new session
        bufferReleaseManager.reset()

        // Second session
        val session2Pts = mutableListOf<Long>()
        repeat(100) {
            session2Pts.add(bufferReleaseManager.calculatePresentationTimeUs(bufferSize, config))
        }

        // Verify session 2 monotonic
        for (i in 1 until session2Pts.size) {
            assertTrue(
                "Session 2 should be monotonic",
                session2Pts[i] > session2Pts[i-1]
            )
        }

        // Sessions should be independent
        // Note: We can't assume session2Pts[0] < session1Pts[0] because
        // System.nanoTime() will be different, but we can verify they're both valid
        assertTrue("Session 1 should have valid timestamps", session1Pts.all { it >= 0 })
        assertTrue("Session 2 should have valid timestamps", session2Pts.all { it >= 0 })
    }

    // Test: Data class properties
    @Test
    fun testBufferStats_dataClass_copy() {
        val original = BufferReleaseManager.BufferStats(
            totalBuffersProcessed = 100L,
            totalBytesProcessed = 352800L,
            bufferErrors = 2L,
            releaseErrors = 1L,
            lastPresentationTimeUs = 2000000L
        )

        val copied = original.copy(bufferErrors = 5L)

        assertEquals("Original bufferErrors should be unchanged", 2L, original.bufferErrors)
        assertEquals("Copied bufferErrors should be updated", 5L, copied.bufferErrors)
        assertEquals("Other fields should be copied", original.totalBuffersProcessed, copied.totalBuffersProcessed)
    }

    @Test
    fun testBufferStats_dataClass_equality() {
        val stats1 = BufferReleaseManager.BufferStats(
            totalBuffersProcessed = 100L,
            totalBytesProcessed = 352800L
        )

        val stats2 = BufferReleaseManager.BufferStats(
            totalBuffersProcessed = 100L,
            totalBytesProcessed = 352800L
        )

        val stats3 = BufferReleaseManager.BufferStats(
            totalBuffersProcessed = 200L,
            totalBytesProcessed = 352800L
        )

        assertEquals("Identical stats should be equal", stats1, stats2)
        assertNotEquals("Different stats should not be equal", stats1, stats3)
    }
}
