# Audio Latency Compliance Test Guide

## Overview

This document provides step-by-step instructions for verifying that audio latency remains ≤100ms throughout extended streaming sessions. This test validates **Requirement 5** from the spec: "Audio latency stays within 100ms throughout stream duration."

## Test Objectives

1. Measure baseline audio latency at stream start
2. Verify latency remains stable after 1 hour of continuous streaming
3. Confirm both measurements are ≤100ms (acceptance criteria)
4. Validate AudioHealthMonitor latency tracking accuracy

## Prerequisites

### Hardware Requirements

- Physical Android device (API 24+)
  - Emulator audio latency measurements are unreliable
  - Recommended: Mid-range device (e.g., Pixel 5+, Galaxy S21+)
- USB cable for ADB connection
- Optional: Professional audio monitoring equipment for validation

### Software Requirements

- Android Debug Bridge (ADB) installed and configured
- Device with USB debugging enabled
- Stable RTMP test server (must run for 1+ hours)
- LivePush app debug build installed

### Test Environment

- **Network**: Stable connection (no packet loss)
- **Server**: RTMP server with audio monitoring capability
- **Background apps**: Close unnecessary apps to reduce interference
- **Power**: Device plugged in (prevent battery optimization)

### Verify Prerequisites

```bash
# Check device connection
adb devices

# Verify app installed
adb shell pm list packages | grep com.livepush

# Test audio_flinger access
adb shell dumpsys media.audio_flinger | head -20

# Check available disk space (for logs)
adb shell df -h | grep data
```

## Test Procedure

### Phase 1: Baseline Latency Measurement (T=0)

#### Step 1: Start the Stream

1. Launch LivePush app on device
2. Configure RTMP stream settings:
   - **URL**: Your stable test RTMP server
   - **Resolution**: 1280x720 (default)
   - **Bitrate**: 2 Mbps
   - **Audio**: 128 Kbps, stereo
3. Start streaming
4. Wait 60 seconds for stream to stabilize

#### Step 2: Capture Baseline Latency

Run the latency measurement script:

```bash
./scripts/measure-audio-latency.sh baseline
```

Or manually:

```bash
# Capture full audio_flinger dump
adb shell dumpsys media.audio_flinger > latency_baseline.txt

# Extract latency value (look for active stream)
grep -A 50 "Output thread" latency_baseline.txt | grep -i "latency"
```

#### Step 3: Parse Latency Value

The `dumpsys media.audio_flinger` output contains multiple latency metrics:

**Key Metrics to Record:**
- **Hardware Latency**: Direct audio output latency
- **Total Latency**: End-to-end from capture to output
- **Buffer Size**: Related to latency calculation

Example output:
```
Output thread 0x7f8c4d2000:
  ...
  Latency: 24 ms
  ...
  Fast track multiplier: 2
  Actual buffer size: 960 frames
```

**Calculation**:
```
Latency (ms) = (buffer_size_frames / sample_rate) * 1000
Example: (960 / 48000) * 1000 = 20 ms
```

#### Step 4: Document Baseline

Record in test report:
- Timestamp: [ISO 8601 format]
- Hardware latency: [X ms]
- Total latency: [Y ms]
- Sample rate: [Hz]
- Buffer size: [frames]
- Device model: [from adb shell getprop ro.product.model]

**BASELINE ACCEPTANCE**: Must be ≤100ms

### Phase 2: Continuous Streaming (T=0 to T=1 hour)

#### Step 1: Start Monitoring

Keep the stream running continuously. Use automated monitoring:

```bash
# Start 1-hour monitoring (checks every 5 minutes)
./scripts/monitor-audio-latency.sh --duration 60
```

Or set up manual monitoring:

```bash
# Monitor logcat for latency warnings
adb logcat | grep -E "(AudioHealth|AudioLatency|audio_flinger)"
```

#### Step 2: Visual Quality Check

Every 15 minutes, verify on RTMP server side:
- Audio is synchronized with video
- No audio dropouts or distortion
- No perceptible lag

#### Step 3: Monitor Health Metrics

Check AudioHealthMonitor metrics:

```bash
# Every 15 minutes
adb logcat -d | grep "AudioHealth.*latency"
```

Expected output:
```
AudioHealth: Current latency: 22ms (threshold: 100ms) - OK
AudioHealth: Peak latency: 28ms (max observed)
```

### Phase 3: Post-Stream Latency Measurement (T=1 hour)

#### Step 1: Verify Stream Still Active

Check stream status:

```bash
adb logcat -d | grep "RtmpStreamManager.*streaming"
```

Expected: Stream should still be in "streaming" state

#### Step 2: Capture Post-Stream Latency

Wait exactly 1 hour (3600 seconds) from stream start, then:

```bash
./scripts/measure-audio-latency.sh post_1hour
```

Or manually:

```bash
# Capture audio_flinger dump after 1 hour
adb shell dumpsys media.audio_flinger > latency_post_1hour.txt

# Extract latency
grep -A 50 "Output thread" latency_post_1hour.txt | grep -i "latency"
```

#### Step 3: Document Post-Stream Measurement

Record in test report:
- Timestamp: [ISO 8601 format, should be ~1 hour after baseline]
- Hardware latency: [X ms]
- Total latency: [Y ms]
- Sample rate: [Hz]
- Buffer size: [frames]
- Latency delta: [post - baseline] ms

**POST-STREAM ACCEPTANCE**: Must be ≤100ms

#### Step 4: Stop Stream

1. Stop streaming in LivePush app
2. Wait 30 seconds for cleanup
3. Capture final logs:

```bash
adb logcat -d > logs_latency_test_complete.txt
```

### Phase 4: Analysis

#### Step 1: Compare Measurements

Calculate latency stability:

```
Latency Drift = Post-Stream Latency - Baseline Latency
Drift Percentage = (Drift / Baseline) * 100
```

**Acceptance Criteria**:
- Baseline latency ≤100ms: **REQUIRED**
- Post-stream latency ≤100ms: **REQUIRED**
- Latency drift: **<20% recommended** (indicates stability)

#### Step 2: Validate AudioHealthMonitor

Cross-reference with app telemetry:

```bash
# Extract AudioHealthMonitor latency logs
grep "AudioHealth.*latency" logs_latency_test_complete.txt > audio_health_latency.txt

# Check for consistency with dumpsys measurements
```

Expected: AudioHealthMonitor reported values should be within ±10ms of dumpsys values

#### Step 3: Check for Anomalies

Look for warnings in logs:

```bash
# Search for latency spikes
grep -i "latency.*exceed\|latency.*high" logs_latency_test_complete.txt

# Search for buffer issues that affect latency
grep -i "buffer.*overflow\|buffer.*underrun" logs_latency_test_complete.txt
```

**Pass Criteria**: Zero latency warnings, zero buffer issues

## Expected Results

### Passing Test

**Baseline Measurement (T=0)**:
```
Hardware Latency: 24 ms
Total Latency: 45 ms
Sample Rate: 48000 Hz
Buffer Size: 960 frames
Status: PASS (≤100ms)
```

**Post-Stream Measurement (T=1 hour)**:
```
Hardware Latency: 26 ms
Total Latency: 47 ms
Sample Rate: 48000 Hz
Buffer Size: 960 frames
Drift: +2 ms (+4.4%)
Status: PASS (≤100ms, drift minimal)
```

**AudioHealthMonitor Validation**:
```
Reported Average Latency: 46 ms
Peak Latency: 52 ms
Latency Spikes: 0
Status: PASS (consistent with dumpsys)
```

### Failing Test Examples

**Failure 1: Baseline Exceeds Threshold**
```
Baseline Latency: 120 ms
Status: FAIL - Baseline exceeds 100ms threshold
Action: Investigate buffer size configuration
```

**Failure 2: Latency Degradation**
```
Baseline: 45 ms
Post-stream: 115 ms
Drift: +70 ms (+156%)
Status: FAIL - Post-stream exceeds 100ms threshold
Action: Investigate memory leaks or buffer queue buildup
```

**Failure 3: AudioHealthMonitor Mismatch**
```
dumpsys: 45 ms
AudioHealthMonitor: 85 ms
Discrepancy: +40 ms
Status: FAIL - Monitoring inaccurate
Action: Review AudioHealthMonitor calculation logic
```

## Troubleshooting

### Issue 1: Cannot Access audio_flinger

**Symptom**:
```
adb shell dumpsys media.audio_flinger
Error: Can't find service: media.audio_flinger
```

**Solutions**:
1. Check ADB connection: `adb devices`
2. Try alternative service name: `adb shell dumpsys media.audio_policy`
3. Restart ADB server: `adb kill-server && adb start-server`
4. Check device API level (requires API 24+)

### Issue 2: Multiple Audio Streams in Dump

**Symptom**: `audio_flinger` dump shows multiple output threads

**Solution**: Identify LivePush stream by:
1. Check thread creation timestamp (should match stream start)
2. Look for matching sample rate (44100 or 48000 Hz)
3. Check buffer size (should match AudioEncoderConfig)

Example identification:
```bash
# Filter by timestamp (within 1 minute of stream start)
adb shell dumpsys media.audio_flinger | grep -A 100 "Output thread" | head -50

# Look for matching configuration
grep -i "48000.*stereo" latency_baseline.txt
```

### Issue 3: Latency Measurement Inconsistent

**Symptom**: Multiple measurements show wildly different values (±50ms)

**Possible Causes**:
1. Background audio apps interfering
2. System under heavy load (CPU/memory pressure)
3. Audio routing changes (Bluetooth, headphones)

**Solutions**:
1. Close all other apps
2. Disable Bluetooth
3. Use wired connection only
4. Take multiple measurements and average:

```bash
# Take 3 measurements 10 seconds apart
./scripts/measure-audio-latency.sh baseline_1
sleep 10
./scripts/measure-audio-latency.sh baseline_2
sleep 10
./scripts/measure-audio-latency.sh baseline_3

# Calculate average manually
```

### Issue 4: Stream Stops Before 1 Hour

**Symptom**: Stream terminates during test (network, crash, etc.)

**Recovery**:
1. Note exact time of failure
2. Capture crash logs: `adb logcat -d > crash_logs.txt`
3. Check for network errors, MediaCodec errors, OOM
4. Fix underlying issue
5. **Restart test from beginning** (baseline measurement invalid)

**Note**: This test requires uninterrupted streaming. Partial sessions do not count.

### Issue 5: High Baseline Latency (>80ms)

**Symptom**: Baseline latency is high but still <100ms

**Investigation**:
1. Check device audio hardware: `adb shell getprop | grep audio`
2. Verify buffer size configuration in AudioEncoderConfig
3. Check sample rate mismatch (44100 vs 48000 Hz)
4. Review device specifications (some low-end devices have inherent high latency)

**Acceptable**:
- 80-100ms on low-end devices: Document as limitation
- 20-50ms on mid-range: Expected
- <20ms on high-end: Excellent

## Test Automation

### Using Provided Scripts

**Full automated test (1 hour)**:
```bash
# Start test (measures baseline, monitors, measures post-stream)
./scripts/test-audio-latency-compliance.sh
```

**Output**:
- `latency_baseline.txt` - Baseline dump
- `latency_post_1hour.txt` - Post-stream dump
- `latency_monitoring_log.txt` - Monitoring data
- `latency_test_report.md` - Auto-generated report

**Manual review required**: Verify pass/fail criteria in generated report

### Manual Step-by-Step

For debugging or validation:

1. **Baseline**: `./scripts/measure-audio-latency.sh baseline`
2. **Wait**: 1 hour (3600 seconds)
3. **Post-stream**: `./scripts/measure-audio-latency.sh post_1hour`
4. **Compare**: Review output files in `./test_results/latency/`

## Success Criteria Summary

| Criterion | Threshold | Status |
|-----------|-----------|--------|
| Baseline latency | ≤100ms | Required |
| Post-stream latency (T=1h) | ≤100ms | Required |
| Latency drift | <20% | Recommended |
| AudioHealthMonitor accuracy | ±10ms | Required |
| Latency warnings in logs | 0 | Required |
| Buffer overflow/underrun | 0 | Required |

**Overall PASS**: All required criteria met

**Overall FAIL**: Any required criterion not met

## Test Report Template

After completing the test, fill out: `docs/testing/test-report-latency-compliance.md`

## Related Documentation

- **Spec**: `.auto-claude/specs/003-long-session-audio-stability/spec.md` (Requirement 5)
- **AudioHealthMonitor**: `app/src/main/java/com/livepush/streaming/monitor/AudioHealthMonitor.kt`
- **Android audio_flinger Guide**: https://source.android.com/devices/audio/latency/measure

## Changelog

| Date | Version | Changes |
|------|---------|---------|
| 2026-01-04 | 1.0 | Initial test procedure |
