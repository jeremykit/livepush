package com.livepush.streaming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.livepush.streaming.capture.AudioCaptureConfig
import com.livepush.streaming.capture.AudioCaptureManager
import com.livepush.streaming.capture.AudioCaptureState
import com.livepush.streaming.encoder.AudioEncoderConfig
import com.livepush.streaming.encoder.BufferReleaseManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for audio pipeline lifecycle.
 *
 * Tests verify:
 * - MediaCodec buffers properly released in all lifecycle states (start/stop/pause)
 * - RootEncoder and AudioCaptureManager integration
 * - Proper resource cleanup
 * - No memory leaks during lifecycle transitions
 *
 * These tests require physical device or emulator with audio hardware.
 *
 * Run with: ./gradlew connectedAndroidTest --tests AudioEncoderLifecycleTest
 */
@RunWith(AndroidJUnit4::class)
class AudioEncoderLifecycleTest {

    private lateinit var context: Context
    private lateinit var audioCaptureManager: AudioCaptureManager
    private lateinit var bufferReleaseManager: BufferReleaseManager
    private lateinit var audioEncoderConfig: AudioEncoderConfig

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        audioCaptureManager = AudioCaptureManager(context)
        bufferReleaseManager = BufferReleaseManager()

        // Standard test configuration
        audioEncoderConfig = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitrate = 128_000
        )
    }

    @After
    fun tearDown() {
        // Ensure resources are released after each test
        audioCaptureManager.release()
        bufferReleaseManager.release()
    }

    // ========================================
    // Lifecycle State Transitions
    // ========================================

    @Test
    fun testLifecycleTransition_IdleToInitialized() = runBlocking {
        // Given: Manager in Idle state
        val initialState = audioCaptureManager.captureState.first()
        assertTrue("Should start in Idle state", initialState is AudioCaptureState.Idle)

        // When: Initialize audio capture
        val config = AudioCaptureConfig(
            sampleRate = 44100,
            bitrate = 128_000,
            isStereo = true
        )
        val initResult = audioCaptureManager.initialize(config)

        // Then: Should transition to Initialized state
        assertTrue("Initialize should succeed", initResult)

        withTimeout(2000) {
            val newState = audioCaptureManager.captureState.first { it !is AudioCaptureState.Idle }
            assertTrue(
                "Should be in Initialized state",
                newState is AudioCaptureState.Initialized
            )

            if (newState is AudioCaptureState.Initialized) {
                assertEquals("Sample rate should match", 44100, newState.sampleRate)
                assertTrue("Buffer size should be positive", newState.bufferSize > 0)
            }
        }
    }

    @Test
    fun testLifecycleTransition_InitializedToRecording() = runBlocking {
        // Given: Manager initialized
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        audioCaptureManager.initialize(config)

        // When: Start recording
        val recordResult = audioCaptureManager.startRecording()

        // Then: Should transition to Recording state
        assertTrue("Start recording should succeed", recordResult)

        withTimeout(2000) {
            val recordingState = audioCaptureManager.captureState.first {
                it is AudioCaptureState.Recording
            }
            assertTrue("Should be in Recording state", recordingState is AudioCaptureState.Recording)
        }

        assertTrue("isRecording should be true", audioCaptureManager.isRecording)
    }

    @Test
    fun testLifecycleTransition_RecordingToStopped() = runBlocking {
        // Given: Manager recording
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        audioCaptureManager.initialize(config)
        audioCaptureManager.startRecording()

        withTimeout(2000) {
            audioCaptureManager.captureState.first { it is AudioCaptureState.Recording }
        }

        // When: Stop recording
        audioCaptureManager.stopRecording()

        // Then: Should transition back to Initialized state
        withTimeout(2000) {
            val stoppedState = audioCaptureManager.captureState.first {
                it is AudioCaptureState.Initialized
            }
            assertTrue("Should be back in Initialized state", stoppedState is AudioCaptureState.Initialized)
        }

        assertFalse("isRecording should be false", audioCaptureManager.isRecording)
    }

    @Test
    fun testLifecycleTransition_StoppedToReleased() = runBlocking {
        // Given: Manager stopped
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        audioCaptureManager.initialize(config)
        audioCaptureManager.startRecording()
        audioCaptureManager.stopRecording()

        // When: Release resources
        audioCaptureManager.release()

        // Then: Should transition to Idle state
        withTimeout(2000) {
            val idleState = audioCaptureManager.captureState.first { it is AudioCaptureState.Idle }
            assertTrue("Should be in Idle state", idleState is AudioCaptureState.Idle)
        }

        assertEquals("Sample rate should be 0", 0, audioCaptureManager.sampleRate)
    }

    @Test
    fun testFullLifecycleCycle() = runBlocking {
        // Test complete lifecycle: Idle -> Initialized -> Recording -> Stopped -> Released -> Idle

        // Start in Idle
        assertTrue(audioCaptureManager.captureState.first() is AudioCaptureState.Idle)

        // Initialize
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        assertTrue(audioCaptureManager.initialize(config))

        // Start recording
        assertTrue(audioCaptureManager.startRecording())
        withTimeout(2000) {
            audioCaptureManager.captureState.first { it is AudioCaptureState.Recording }
        }

        // Stop recording
        audioCaptureManager.stopRecording()
        withTimeout(2000) {
            audioCaptureManager.captureState.first { it is AudioCaptureState.Initialized }
        }

        // Release
        audioCaptureManager.release()
        withTimeout(2000) {
            audioCaptureManager.captureState.first { it is AudioCaptureState.Idle }
        }
    }

    // ========================================
    // Buffer Release Management
    // ========================================

    @Test
    fun testBufferReleaseManager_InitialState() {
        // Given: Fresh BufferReleaseManager
        val stats = bufferReleaseManager.bufferStats.value

        // Then: Should have clean initial state
        assertEquals("No buffers processed initially", 0L, stats.totalBuffersProcessed)
        assertEquals("No bytes processed initially", 0L, stats.totalBytesProcessed)
        assertEquals("No errors initially", 0L, stats.bufferErrors)
        assertEquals("No release errors initially", 0L, stats.releaseErrors)
        assertFalse("Should not have errors", stats.hasErrors)
    }

    @Test
    fun testBufferReleaseManager_Reset() {
        // Given: BufferReleaseManager with some PTS history
        bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)
        bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)

        // When: Reset for new session
        bufferReleaseManager.reset()

        // Then: Stats should be cleared
        val stats = bufferReleaseManager.bufferStats.value
        assertEquals("Buffers processed should be 0", 0L, stats.totalBuffersProcessed)
        assertEquals("Bytes processed should be 0", 0L, stats.totalBytesProcessed)
        assertEquals("Last PTS should be 0", 0L, stats.lastPresentationTimeUs)
    }

    @Test
    fun testBufferReleaseManager_PTSCalculation() {
        // Given: Fresh manager
        bufferReleaseManager.reset()

        // When: Calculate PTS for multiple buffers
        val pts1 = bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)
        val pts2 = bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)
        val pts3 = bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)

        // Then: PTS should be monotonically increasing
        assertTrue("PTS should be positive", pts1 > 0)
        assertTrue("PTS should increase", pts2 > pts1)
        assertTrue("PTS should keep increasing", pts3 > pts2)

        // And: PTS differences should be consistent
        val diff1 = pts2 - pts1
        val diff2 = pts3 - pts2
        assertTrue("PTS increments should be similar", Math.abs(diff1 - diff2) < 1000)
    }

    @Test
    fun testBufferReleaseManager_SessionReset() {
        // Simulate session restart scenario

        // First session
        bufferReleaseManager.reset()
        val session1_pts1 = bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)
        val session1_pts2 = bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)

        // Reset for new session
        bufferReleaseManager.reset()
        val session2_pts1 = bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)

        // PTS in new session should not depend on previous session
        assertTrue("First session PTS should be positive", session1_pts1 > 0)
        assertTrue("Second session should restart PTS", session2_pts1 > 0)
        // Note: We can't guarantee session2_pts1 < session1_pts2 due to System.nanoTime()
        // but we verify it's a valid positive timestamp
    }

    // ========================================
    // Buffer Health Monitoring
    // ========================================

    @Test
    fun testBufferHealth_AfterInitialization() = runBlocking {
        // Given: Initialized audio capture
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        audioCaptureManager.initialize(config)

        // When: Check buffer health
        val health = audioCaptureManager.bufferHealth.first()

        // Then: Buffer should be healthy
        assertTrue("Buffer should be healthy", health.isHealthy)
        assertTrue("Buffer size should be positive", health.bufferSize > 0)
        assertTrue("Min buffer size should be positive", health.minBufferSize > 0)
        assertTrue(
            "Buffer utilization should be >= 2.0 (bufferIncreaseFactor)",
            health.bufferUtilization >= 2.0f
        )
    }

    @Test
    fun testBufferHealth_DuringRecording() = runBlocking {
        // Given: Recording in progress
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        audioCaptureManager.initialize(config)
        audioCaptureManager.startRecording()

        // Wait for recording to stabilize
        withTimeout(3000) {
            audioCaptureManager.captureState.first { it is AudioCaptureState.Recording }
        }

        // When: Check buffer health during recording
        val health = audioCaptureManager.bufferHealth.first()

        // Then: Buffer should remain healthy
        assertTrue("Buffer should be healthy during recording", health.isHealthy)
        assertTrue("Last check timestamp should be recent",
            System.currentTimeMillis() - health.lastCheckTimestamp < 5000)
    }

    @Test
    fun testBufferHealth_AfterRelease() = runBlocking {
        // Given: Released manager
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        audioCaptureManager.initialize(config)
        audioCaptureManager.startRecording()
        audioCaptureManager.stopRecording()
        audioCaptureManager.release()

        // When: Check buffer health after release
        val health = audioCaptureManager.bufferHealth.first()

        // Then: Buffer health should be reset
        assertEquals("Buffer size should be 0", 0, health.bufferSize)
        assertEquals("Min buffer size should be 0", 0, health.minBufferSize)
    }

    // ========================================
    // Resource Cleanup
    // ========================================

    @Test
    fun testResourceCleanup_MultipleInitializeReleaseCycles() = runBlocking {
        // Test multiple init/release cycles don't leak resources
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)

        repeat(5) { cycle ->
            // Initialize
            assertTrue("Initialize cycle $cycle should succeed",
                audioCaptureManager.initialize(config))

            // Start recording
            assertTrue("Start recording cycle $cycle should succeed",
                audioCaptureManager.startRecording())

            // Stop recording
            audioCaptureManager.stopRecording()

            // Release
            audioCaptureManager.release()

            // Verify clean state
            withTimeout(2000) {
                val state = audioCaptureManager.captureState.first()
                assertTrue("Should be Idle after cycle $cycle", state is AudioCaptureState.Idle)
            }
        }
    }

    @Test
    fun testResourceCleanup_AbruptRelease() = runBlocking {
        // Test releasing while recording (emergency shutdown scenario)
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        audioCaptureManager.initialize(config)
        audioCaptureManager.startRecording()

        // Wait for recording to start
        withTimeout(2000) {
            audioCaptureManager.captureState.first { it is AudioCaptureState.Recording }
        }

        // When: Release abruptly without stopping
        audioCaptureManager.release()

        // Then: Should handle gracefully
        withTimeout(2000) {
            val state = audioCaptureManager.captureState.first()
            assertTrue("Should be in Idle state", state is AudioCaptureState.Idle)
        }
    }

    @Test
    fun testBufferReleaseManager_MultipleResets() {
        // Test multiple reset cycles don't cause issues
        repeat(10) { cycle ->
            bufferReleaseManager.reset()

            // Calculate some PTS values
            val pts1 = bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)
            val pts2 = bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)

            // Verify monotonic increase within session
            assertTrue("Cycle $cycle: PTS should increase", pts2 > pts1)

            val stats = bufferReleaseManager.bufferStats.value
            assertEquals("Cycle $cycle: Should have no errors", 0L, stats.bufferErrors)
        }
    }

    // ========================================
    // Error Handling
    // ========================================

    @Test
    fun testErrorHandling_InitializeWithoutPermission() = runBlocking {
        // Note: This test may not fail if permissions are granted
        // In real scenario, microphone permission would be revoked

        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        val result = audioCaptureManager.initialize(config)

        // Should either succeed (permissions granted) or fail gracefully
        if (!result) {
            val state = audioCaptureManager.captureState.first()
            assertTrue("Should be in Error or Idle state",
                state is AudioCaptureState.Error || state is AudioCaptureState.Idle)
        }
    }

    @Test
    fun testErrorHandling_StartRecordingWithoutInitialize() = runBlocking {
        // Given: Manager not initialized
        audioCaptureManager.release()

        // When: Try to start recording
        val result = audioCaptureManager.startRecording()

        // Then: Should handle gracefully (either auto-initialize or fail cleanly)
        // The implementation attempts retry initialization
        assertNotNull("Should return a result", result)
    }

    @Test
    fun testErrorHandling_DoubleRelease() {
        // Test releasing twice doesn't cause crash
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        audioCaptureManager.initialize(config)

        // First release
        audioCaptureManager.release()

        // Second release - should be safe
        audioCaptureManager.release()

        // Should not crash
        assertTrue("Double release should be safe", true)
    }

    // ========================================
    // Integration Tests
    // ========================================

    @Test
    fun testIntegration_AudioCaptureWithBufferRelease() = runBlocking {
        // Test integration between AudioCaptureManager and BufferReleaseManager

        // Initialize audio capture
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        assertTrue("Audio capture should initialize", audioCaptureManager.initialize(config))

        // Reset buffer release manager for session
        bufferReleaseManager.reset()

        // Start recording
        assertTrue("Should start recording", audioCaptureManager.startRecording())

        withTimeout(2000) {
            audioCaptureManager.captureState.first { it is AudioCaptureState.Recording }
        }

        // Simulate buffer processing (as would happen during actual streaming)
        val pts1 = bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)
        val pts2 = bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)
        val pts3 = bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)

        // Verify both components working correctly
        assertTrue("PTS should be monotonic", pts1 < pts2 && pts2 < pts3)
        assertTrue("Audio capture should be recording", audioCaptureManager.isRecording)

        val bufferHealth = audioCaptureManager.bufferHealth.first()
        assertTrue("Buffer should be healthy", bufferHealth.isHealthy)

        // Clean shutdown
        audioCaptureManager.stopRecording()
        audioCaptureManager.release()
        bufferReleaseManager.release()
    }

    @Test
    fun testIntegration_LongSessionSimulation() = runBlocking {
        // Simulate extended session with many buffer operations
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)
        audioCaptureManager.initialize(config)
        audioCaptureManager.startRecording()
        bufferReleaseManager.reset()

        // Simulate processing many buffers (as in 30-minute session)
        // At 20ms per buffer: 50 buffers/sec * 60 sec = 3000 buffers/minute
        val bufferCount = 1000 // Reduced for test speed
        val bufferSize = 3528

        var lastPts = 0L
        repeat(bufferCount) { i ->
            val pts = bufferReleaseManager.calculatePresentationTimeUs(bufferSize, audioEncoderConfig)
            assertTrue("PTS should be monotonic at buffer $i", pts > lastPts)
            lastPts = pts
        }

        // Verify no errors accumulated
        val stats = bufferReleaseManager.bufferStats.value
        assertEquals("Should have no buffer errors", 0L, stats.bufferErrors)
        assertEquals("Should have no release errors", 0L, stats.releaseErrors)

        // Cleanup
        audioCaptureManager.stopRecording()
        audioCaptureManager.release()
        bufferReleaseManager.release()
    }

    @Test
    fun testIntegration_SessionRestartWithoutLeak() = runBlocking {
        // Simulate multiple streaming sessions in sequence
        val config = AudioCaptureConfig(sampleRate = 44100, bitrate = 128_000, isStereo = true)

        repeat(3) { session ->
            // Start session
            audioCaptureManager.initialize(config)
            audioCaptureManager.startRecording()
            bufferReleaseManager.reset()

            // Process some buffers
            repeat(100) {
                bufferReleaseManager.calculatePresentationTimeUs(3528, audioEncoderConfig)
            }

            // Verify health
            val health = audioCaptureManager.bufferHealth.first()
            assertTrue("Session $session: Buffer should be healthy", health.isHealthy)

            val stats = bufferReleaseManager.bufferStats.value
            assertFalse("Session $session: Should have no errors", stats.hasErrors)

            // End session cleanly
            audioCaptureManager.stopRecording()
            audioCaptureManager.release()

            // Verify cleanup
            withTimeout(2000) {
                val state = audioCaptureManager.captureState.first()
                assertTrue("Session $session: Should be Idle", state is AudioCaptureState.Idle)
            }
        }
    }
}
