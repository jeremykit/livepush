package com.livepush.streaming.encoder

import com.livepush.domain.model.AudioCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for AudioEncoderConfig buffer size calculations
 *
 * Tests verify:
 * - Buffer size calculations are accurate
 * - Minimum buffer size is >= 2x calculated size
 * - Frame size calculations are correct
 * - Configuration validation works properly
 * - Edge cases handle invalid inputs
 */
class AudioEncoderConfigTest {

    // Test: Buffer size calculation accuracy
    @Test
    fun testCalculateBufferSize_standardConfig() {
        // Standard config: 44100 Hz, stereo, 16-bit, 20ms buffer
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitrate = 128_000,
            codec = AudioCodec.AAC,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        // Expected: (44100 * 20 / 1000) samples * 2 channels * 2 bytes = 3528 bytes
        val expectedBufferSize = 3528
        assertEquals(expectedBufferSize, config.calculateBufferSize())
    }

    @Test
    fun testCalculateBufferSize_48kHz() {
        // High-quality config: 48000 Hz, stereo, 16-bit, 20ms buffer
        val config = AudioEncoderConfig(
            sampleRate = 48000,
            channelCount = 2,
            bitrate = 192_000,
            codec = AudioCodec.AAC,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        // Expected: (48000 * 20 / 1000) samples * 2 channels * 2 bytes = 3840 bytes
        val expectedBufferSize = 3840
        assertEquals(expectedBufferSize, config.calculateBufferSize())
    }

    @Test
    fun testCalculateBufferSize_monoConfig() {
        // Mono config: 44100 Hz, mono, 16-bit, 20ms buffer
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 1,
            bitrate = 64_000,
            codec = AudioCodec.AAC,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        // Expected: (44100 * 20 / 1000) samples * 1 channel * 2 bytes = 1764 bytes
        val expectedBufferSize = 1764
        assertEquals(expectedBufferSize, config.calculateBufferSize())
    }

    @Test
    fun testCalculateBufferSize_longerDuration() {
        // Longer buffer: 44100 Hz, stereo, 16-bit, 40ms buffer
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitrate = 128_000,
            codec = AudioCodec.AAC,
            bitsPerSample = 16,
            bufferDurationMs = 40
        )

        // Expected: (44100 * 40 / 1000) samples * 2 channels * 2 bytes = 7056 bytes
        val expectedBufferSize = 7056
        assertEquals(expectedBufferSize, config.calculateBufferSize())
    }

    // Test: Minimum buffer size is 2x calculated size (critical for long sessions)
    @Test
    fun testCalculateMinBufferSize_isAtLeast2xCalculatedSize() {
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        val calculatedSize = config.calculateBufferSize()
        val minBufferSize = config.calculateMinBufferSize()

        // CRITICAL: Minimum buffer must be at least 2x to prevent overflow in long sessions
        assertTrue(
            "Minimum buffer size ($minBufferSize) must be at least 2x calculated size ($calculatedSize)",
            minBufferSize >= calculatedSize * 2
        )
        assertEquals(calculatedSize * 2, minBufferSize)
    }

    @Test
    fun testCalculateMinBufferSize_48kHz() {
        val config = AudioEncoderConfig(
            sampleRate = 48000,
            channelCount = 2,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        val calculatedSize = config.calculateBufferSize()
        val minBufferSize = config.calculateMinBufferSize()

        assertEquals(calculatedSize * 2, minBufferSize)
        assertEquals(7680, minBufferSize) // 3840 * 2
    }

    // Test: Frame size calculations
    @Test
    fun testCalculateFrameSize_standardConfig() {
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        // Expected: (44100 / 1000) samples per ms * 2 channels * 2 bytes = 176.4 ≈ 176 bytes
        // Note: Integer division truncates
        val expectedFrameSize = 176
        assertEquals(expectedFrameSize, config.calculateFrameSize())
    }

    @Test
    fun testCalculateFrameSize_48kHz() {
        val config = AudioEncoderConfig(
            sampleRate = 48000,
            channelCount = 2,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        // Expected: (48000 / 1000) samples per ms * 2 channels * 2 bytes = 192 bytes
        val expectedFrameSize = 192
        assertEquals(expectedFrameSize, config.calculateFrameSize())
    }

    @Test
    fun testCalculateFrameSize_monoConfig() {
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 1,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        // Expected: (44100 / 1000) samples per ms * 1 channel * 2 bytes = 88 bytes
        val expectedFrameSize = 88
        assertEquals(expectedFrameSize, config.calculateFrameSize())
    }

    // Test: Configuration validation
    @Test
    fun testIsValid_validConfig() {
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitrate = 128_000,
            codec = AudioCodec.AAC,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        assertTrue("Valid configuration should pass validation", config.isValid())
    }

    @Test
    fun testIsValid_allStandardSampleRates() {
        val standardSampleRates = listOf(8000, 16000, 22050, 44100, 48000)

        standardSampleRates.forEach { sampleRate ->
            val config = AudioEncoderConfig(sampleRate = sampleRate)
            assertTrue(
                "Sample rate $sampleRate should be valid",
                config.isValid()
            )
        }
    }

    @Test
    fun testIsValid_allStandardBitDepths() {
        val standardBitDepths = listOf(8, 16, 24, 32)

        standardBitDepths.forEach { bitDepth ->
            val config = AudioEncoderConfig(bitsPerSample = bitDepth)
            assertTrue(
                "Bit depth $bitDepth should be valid",
                config.isValid()
            )
        }
    }

    @Test
    fun testIsValid_monoAndStereo() {
        val monoConfig = AudioEncoderConfig(channelCount = 1)
        val stereoConfig = AudioEncoderConfig(channelCount = 2)

        assertTrue("Mono configuration should be valid", monoConfig.isValid())
        assertTrue("Stereo configuration should be valid", stereoConfig.isValid())
    }

    // Test: Edge cases - invalid configurations
    @Test
    fun testIsValid_invalidSampleRate() {
        val config = AudioEncoderConfig(sampleRate = 0)
        assertFalse("Zero sample rate should be invalid", config.isValid())
    }

    @Test
    fun testIsValid_negativeSampleRate() {
        val config = AudioEncoderConfig(sampleRate = -44100)
        assertFalse("Negative sample rate should be invalid", config.isValid())
    }

    @Test
    fun testIsValid_invalidChannelCount() {
        val invalidChannelCounts = listOf(0, 3, 4, -1)

        invalidChannelCounts.forEach { channelCount ->
            val config = AudioEncoderConfig(channelCount = channelCount)
            assertFalse(
                "Channel count $channelCount should be invalid",
                config.isValid()
            )
        }
    }

    @Test
    fun testIsValid_invalidBitrate() {
        val config = AudioEncoderConfig(bitrate = 0)
        assertFalse("Zero bitrate should be invalid", config.isValid())
    }

    @Test
    fun testIsValid_negativeBitrate() {
        val config = AudioEncoderConfig(bitrate = -128000)
        assertFalse("Negative bitrate should be invalid", config.isValid())
    }

    @Test
    fun testIsValid_invalidBitsPerSample() {
        val invalidBitDepths = listOf(0, 1, 12, 17, 64, -16)

        invalidBitDepths.forEach { bitDepth ->
            val config = AudioEncoderConfig(bitsPerSample = bitDepth)
            assertFalse(
                "Bit depth $bitDepth should be invalid",
                config.isValid()
            )
        }
    }

    @Test
    fun testIsValid_invalidBufferDuration() {
        val config = AudioEncoderConfig(bufferDurationMs = 0)
        assertFalse("Zero buffer duration should be invalid", config.isValid())
    }

    @Test
    fun testIsValid_negativeBufferDuration() {
        val config = AudioEncoderConfig(bufferDurationMs = -20)
        assertFalse("Negative buffer duration should be invalid", config.isValid())
    }

    // Test: Companion object constants
    @Test
    fun testCompanionConstants_sampleRates() {
        assertEquals(8000, AudioEncoderConfig.SAMPLE_RATE_8000)
        assertEquals(16000, AudioEncoderConfig.SAMPLE_RATE_16000)
        assertEquals(22050, AudioEncoderConfig.SAMPLE_RATE_22050)
        assertEquals(44100, AudioEncoderConfig.SAMPLE_RATE_44100)
        assertEquals(48000, AudioEncoderConfig.SAMPLE_RATE_48000)
    }

    @Test
    fun testCompanionConstants_bufferDurations() {
        assertEquals(10, AudioEncoderConfig.BUFFER_DURATION_10MS)
        assertEquals(20, AudioEncoderConfig.BUFFER_DURATION_20MS)
        assertEquals(40, AudioEncoderConfig.BUFFER_DURATION_40MS)
        assertEquals(60, AudioEncoderConfig.BUFFER_DURATION_60MS)
    }

    @Test
    fun testCompanionConstants_bitrates() {
        assertEquals(64_000, AudioEncoderConfig.BITRATE_LOW)
        assertEquals(128_000, AudioEncoderConfig.BITRATE_MEDIUM)
        assertEquals(192_000, AudioEncoderConfig.BITRATE_HIGH)
        assertEquals(320_000, AudioEncoderConfig.BITRATE_VERY_HIGH)
    }

    // Test: Default values
    @Test
    fun testDefaultValues() {
        val config = AudioEncoderConfig()

        assertEquals(44100, config.sampleRate)
        assertEquals(2, config.channelCount)
        assertEquals(128_000, config.bitrate)
        assertEquals(AudioCodec.AAC, config.codec)
        assertEquals(16, config.bitsPerSample)
        assertEquals(20, config.bufferDurationMs)
    }

    @Test
    fun testDefaultConfig_isValid() {
        val config = AudioEncoderConfig()
        assertTrue("Default configuration should be valid", config.isValid())
    }

    // Test: Real-world scenarios from spec
    @Test
    fun testRealWorld_44100HzStereo_20msBuffer() {
        // Most common Android audio hardware scenario
        val config = AudioEncoderConfig(
            sampleRate = 44100,
            channelCount = 2,
            bitrate = 128_000,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        assertTrue(config.isValid())
        assertEquals(3528, config.calculateBufferSize())
        assertEquals(7056, config.calculateMinBufferSize()) // 2x for safety
    }

    @Test
    fun testRealWorld_48000HzStereo_20msBuffer() {
        // High-end Android audio hardware scenario (Samsung, etc.)
        val config = AudioEncoderConfig(
            sampleRate = 48000,
            channelCount = 2,
            bitrate = 192_000,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        assertTrue(config.isValid())
        assertEquals(3840, config.calculateBufferSize())
        assertEquals(7680, config.calculateMinBufferSize()) // 2x for safety
    }

    @Test
    fun testRealWorld_lowQualityMono() {
        // Low-quality scenario for bandwidth-constrained situations
        val config = AudioEncoderConfig(
            sampleRate = 22050,
            channelCount = 1,
            bitrate = 64_000,
            bitsPerSample = 16,
            bufferDurationMs = 20
        )

        assertTrue(config.isValid())
        assertEquals(882, config.calculateBufferSize())
        assertEquals(1764, config.calculateMinBufferSize())
    }

    @Test
    fun testRealWorld_highQualityStereo() {
        // High-quality scenario for professional streaming
        val config = AudioEncoderConfig(
            sampleRate = 48000,
            channelCount = 2,
            bitrate = 320_000,
            bitsPerSample = 16,
            bufferDurationMs = 40 // Longer buffer for stability
        )

        assertTrue(config.isValid())
        assertEquals(7680, config.calculateBufferSize())
        assertEquals(15360, config.calculateMinBufferSize())
    }

    // Test: Buffer increase factor verification (from spec)
    @Test
    fun testBufferIncreaseFactor_meetsSpecRequirement() {
        // Spec requires bufferIncreaseFactor of 2.0 or higher
        val config = AudioEncoderConfig()

        val calculatedSize = config.calculateBufferSize()
        val minBufferSize = config.calculateMinBufferSize()

        val actualFactor = minBufferSize.toDouble() / calculatedSize.toDouble()

        assertTrue(
            "Buffer increase factor ($actualFactor) must be >= 2.0 as per spec",
            actualFactor >= 2.0
        )
    }

    @Test
    fun testBufferIncreaseFactor_appliedToAllConfigs() {
        val configs = listOf(
            AudioEncoderConfig(sampleRate = 8000),
            AudioEncoderConfig(sampleRate = 16000),
            AudioEncoderConfig(sampleRate = 22050),
            AudioEncoderConfig(sampleRate = 44100),
            AudioEncoderConfig(sampleRate = 48000),
            AudioEncoderConfig(channelCount = 1),
            AudioEncoderConfig(bufferDurationMs = 10),
            AudioEncoderConfig(bufferDurationMs = 40),
            AudioEncoderConfig(bufferDurationMs = 60)
        )

        configs.forEach { config ->
            val calculatedSize = config.calculateBufferSize()
            val minBufferSize = config.calculateMinBufferSize()
            val factor = minBufferSize.toDouble() / calculatedSize.toDouble()

            assertTrue(
                "All configs must have buffer increase factor >= 2.0, got $factor for $config",
                factor >= 2.0
            )
        }
    }

    // Test: Codec selection
    @Test
    fun testCodecSelection_AAC() {
        val config = AudioEncoderConfig(codec = AudioCodec.AAC)
        assertEquals(AudioCodec.AAC, config.codec)
        assertTrue(config.isValid())
    }

    @Test
    fun testCodecSelection_Opus() {
        val config = AudioEncoderConfig(codec = AudioCodec.OPUS)
        assertEquals(AudioCodec.OPUS, config.codec)
        assertTrue(config.isValid())
    }

    // Test: Data class properties (immutability verification)
    @Test
    fun testDataClass_copy() {
        val original = AudioEncoderConfig(sampleRate = 44100, channelCount = 2)
        val copied = original.copy(sampleRate = 48000)

        assertEquals(44100, original.sampleRate)
        assertEquals(48000, copied.sampleRate)
        assertEquals(original.channelCount, copied.channelCount)
    }

    @Test
    fun testDataClass_equality() {
        val config1 = AudioEncoderConfig(sampleRate = 44100, channelCount = 2)
        val config2 = AudioEncoderConfig(sampleRate = 44100, channelCount = 2)
        val config3 = AudioEncoderConfig(sampleRate = 48000, channelCount = 2)

        assertEquals(config1, config2)
        assertTrue(config1 != config3)
    }
}
