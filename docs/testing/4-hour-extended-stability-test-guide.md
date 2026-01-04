# 4-Hour Extended Stability Test Guide

## Overview

This is the **CRITICAL** acceptance test that validates the audio pipeline's ability to maintain quality and stability during professional-grade extended streaming sessions. This test must PASS on at least 2 different physical devices before the feature can be considered complete.

## Test Significance

This test validates:
- **Spec Requirement 1**: Audio quality consistent for 4+ hours without degradation
- **Spec Requirement 2**: Zero buffer overflow/underflow during normal operation
- **Spec Requirement 3**: Automatic recovery from transient failures
- **Spec Requirement 4**: Memory stability with no leaks over extended sessions
- **Spec Requirement 5**: Audio latency ≤100ms throughout stream duration

**Critical Success Criteria:**
- Test must PASS on **2+ different devices** (e.g., Samsung + Pixel)
- Zero audio distortion, popping, or quality degradation for full 4 hours
- Memory growth <40MB over 4 hours (<10MB/hour)
- Zero buffer overflow/underflow errors
- Stream automatically recovers from simulated CPU spike
- Audio/video synchronization maintained throughout

## Prerequisites

### Hardware Requirements

**REQUIRED:** 2 physical Android devices (API 24+)

Recommended device combinations:
1. **Device A**: Samsung Galaxy (S21/S22/S23) - 48kHz audio hardware
2. **Device B**: Google Pixel (5/6/7/8) - 44.1kHz audio hardware

**OR:**
1. **Device A**: Mid-range Samsung device
2. **Device B**: Different manufacturer (Pixel, OnePlus, Xiaomi)

**Device Preparation:**
```bash
# For each device:
# 1. Enable USB debugging
# 2. Keep device awake during test
# 3. Fully charge (or keep plugged in)
# 4. Close all background apps
# 5. Disable battery optimization for LivePush

# Via ADB (for each device):
adb -s <device-id> shell settings put global stay_on_while_plugged_in 7
adb -s <device-id> logcat -G 32M  # Large buffer for 4-hour logs
```

### Software Requirements

- **Android Studio** with Profiler
- **ADB** accessible in system PATH
- **Python 3.x** (for analysis scripts)
- **RTMP test server** with 4+ hours uptime guarantee
- **Release build** of LivePush app (not debug)

### Test Environment

**CRITICAL REQUIREMENTS:**
- Stable RTMP server (no downtime for 4+ hours)
- Stable network connection (WiFi preferred over mobile data)
- Climate-controlled environment (prevent device overheating)
- Uninterrupted power for both devices
- Monitoring computer available for full 4 hours

### Pre-Test Validation

```bash
# Create test environment
mkdir -p test-results/4hour-stability/device-A
mkdir -p test-results/4hour-stability/device-B

# Verify both devices connected
adb devices | grep -c "device$"
# Expected: 2 (or more if testing on >2 devices)

# Identify device IDs
adb devices -l

# Export device IDs for testing
export DEVICE_A="<device-id-1>"
export DEVICE_B="<device-id-2>"

# Verify RTMP server availability
curl -I http://<rtmp-server>:8080
# Expected: HTTP 200 OK
```

## Test Execution

### Stage 1: Release Build Preparation

```bash
# Navigate to project root
cd E:\237\live\livepush

# Clean build
./gradlew clean

# Build RELEASE APK (not debug - production configuration)
./gradlew assembleRelease

# Verify release APK created
ls -lh app/build/outputs/apk/release/app-release.apk

# Install on Device A
adb -s ${DEVICE_A} install -r app/build/outputs/apk/release/app-release.apk

# Install on Device B
adb -s ${DEVICE_B} install -r app/build/outputs/apk/release/app-release.apk

# Verify installations
adb -s ${DEVICE_A} shell pm list packages | grep com.livepush
adb -s ${DEVICE_B} shell pm list packages | grep com.livepush
```

### Stage 2: Monitoring Setup (Parallel for Both Devices)

#### Terminal 1: Device A Monitoring
```bash
# Start automated 4-hour monitoring for Device A
./scripts/test-4hour-stability.sh ${DEVICE_A} device-A
```

#### Terminal 2: Device B Monitoring
```bash
# Start automated 4-hour monitoring for Device B
./scripts/test-4hour-stability.sh ${DEVICE_B} device-B
```

These scripts will automatically:
- Monitor logcat for audio/video/buffer errors
- Sample memory every 5 minutes
- Track buffer health metrics
- Record latency measurements every 30 minutes
- Detect CPU spikes and recovery events
- Generate reports after completion

### Stage 3: Start Streaming (Both Devices)

**Device A:**
1. Launch LivePush app
2. Grant all permissions (Camera, Microphone)
3. Configure RTMP settings:
   - URL: `rtmp://<server>/live/test-device-a`
   - Resolution: 1280x720
   - Bitrate: 2 Mbps
   - Audio: 128 Kbps, stereo
4. Start streaming
5. Wait 60 seconds for stabilization

**Device B:**
1. Launch LivePush app
2. Grant all permissions
3. Configure RTMP settings:
   - URL: `rtmp://<server>/live/test-device-b`
   - Resolution: 1280x720
   - Bitrate: 2 Mbps
   - Audio: 128 Kbps, stereo
4. Start streaming
5. Wait 60 seconds for stabilization

**Set timer for 4 hours.**

### Stage 4: Baseline Measurements (T=0)

#### Memory Baseline
```bash
# Device A
adb -s ${DEVICE_A} shell dumpsys meminfo com.livepush > test-results/4hour-stability/device-A/memory-baseline.txt

# Device B
adb -s ${DEVICE_B} shell dumpsys meminfo com.livepush > test-results/4hour-stability/device-B/memory-baseline.txt
```

#### Latency Baseline
```bash
# Device A
./scripts/measure-audio-latency.sh ${DEVICE_A} baseline > test-results/4hour-stability/device-A/latency-baseline.txt

# Device B
./scripts/measure-audio-latency.sh ${DEVICE_B} baseline > test-results/4hour-stability/device-B/latency-baseline.txt
```

#### Audio Quality Baseline
**Manually verify on both devices:**
- [ ] Audio clear, no distortion
- [ ] Audio/video in sync
- [ ] No popping or clicking sounds
- [ ] Consistent volume levels

### Stage 5: Periodic Monitoring (Every 30 Minutes)

Create a monitoring checklist and verify at **T = 30m, 1h, 1.5h, 2h, 2.5h, 3h, 3.5h, 4h**:

#### Device Status Checks
```bash
# For each device, every 30 minutes:

# 1. Memory snapshot
adb -s <device-id> shell dumpsys meminfo com.livepush | grep "TOTAL" | head -1

# 2. Buffer health check
adb -s <device-id> logcat -d -s AudioHealth:* | tail -5

# 3. Temperature check (if supported)
adb -s <device-id> shell dumpsys battery | grep temperature

# 4. CPU usage
adb -s <device-id> shell top -n 1 | grep com.livepush
```

#### Audio Quality Checks (Manual)
At each checkpoint, verify on both devices:
- [ ] Audio quality consistent with baseline
- [ ] No distortion, popping, or artifacts
- [ ] Audio/video sync maintained
- [ ] No audible degradation from previous checkpoint

#### Visual Verification
- [ ] App remains responsive
- [ ] Stream preview displays correctly
- [ ] No ANR (Application Not Responding) dialogs
- [ ] Device temperature acceptable (not overheating)

### Stage 6: CPU Spike Simulation (T = 2 hours)

**Purpose:** Validate automatic recovery mechanism (Spec Requirement 3)

#### On Device A:
```bash
# Simulate CPU spike with stress test
adb -s ${DEVICE_A} shell "while true; do cat /dev/urandom > /dev/null; done &"

# Get stress process PID
STRESS_PID=$(adb -s ${DEVICE_A} shell "ps | grep cat | grep urandom" | awk '{print $2}')

# Run for 30 seconds
sleep 30

# Kill stress process
adb -s ${DEVICE_A} shell "kill ${STRESS_PID}"
```

#### Verify Recovery (Within 60 seconds):
```bash
# Check logs for recovery indicators
adb -s ${DEVICE_A} logcat -d -s AudioHealth:* | grep -i "recovery\|resumed"

# Expected indicators:
# - "Audio pipeline recovered"
# - "Buffer health: OK"
# - No crash or restart required
```

#### Recovery Acceptance Criteria:
- [ ] Stream continues without manual restart
- [ ] Audio quality returns to normal within 60 seconds
- [ ] Buffer health returns to "OK" status
- [ ] No MediaCodec errors in logs
- [ ] Memory remains stable (no spike)

**Repeat on Device B at T = 2.5 hours**

### Stage 7: Final Measurements (T = 4 hours)

#### Stop Streaming
```bash
# Stop streams on both devices
# Tap "Stop Stream" button in app on both devices
```

#### Final Memory Dump
```bash
# Device A
adb -s ${DEVICE_A} shell dumpsys meminfo com.livepush > test-results/4hour-stability/device-A/memory-final.txt

# Device B
adb -s ${DEVICE_B} shell dumpsys meminfo com.livepush > test-results/4hour-stability/device-B/memory-final.txt
```

#### Final Latency Measurement
```bash
# Device A
./scripts/measure-audio-latency.sh ${DEVICE_A} final > test-results/4hour-stability/device-A/latency-final.txt

# Device B
./scripts/measure-audio-latency.sh ${DEVICE_B} final > test-results/4hour-stability/device-B/latency-final.txt
```

#### Heap Dump Collection (For Leak Analysis)
```bash
# Device A
adb -s ${DEVICE_A} shell am dumpheap com.livepush /data/local/tmp/heap-4hour.hprof
adb -s ${DEVICE_A} pull /data/local/tmp/heap-4hour.hprof test-results/4hour-stability/device-A/

# Device B
adb -s ${DEVICE_B} shell am dumpheap com.livepush /data/local/tmp/heap-4hour.hprof
adb -s ${DEVICE_B} pull /data/local/tmp/heap-4hour.hprof test-results/4hour-stability/device-B/
```

#### Complete Logcat Export
```bash
# Device A - full logs
adb -s ${DEVICE_A} logcat -d > test-results/4hour-stability/device-A/logcat-full.txt

# Device B - full logs
adb -s ${DEVICE_B} logcat -d > test-results/4hour-stability/device-B/logcat-full.txt
```

### Stage 8: Results Analysis

#### Automated Analysis
```bash
# Run analysis script for both devices
./scripts/analyze-4hour-results.sh test-results/4hour-stability/device-A
./scripts/analyze-4hour-results.sh test-results/4hour-stability/device-B
```

#### Manual Analysis

**For each device, calculate and verify:**

1. **Memory Growth**
   ```bash
   # Extract baseline and final TOTAL memory
   BASELINE=$(grep "TOTAL" test-results/4hour-stability/device-A/memory-baseline.txt | head -1 | awk '{print $2}')
   FINAL=$(grep "TOTAL" test-results/4hour-stability/device-A/memory-final.txt | head -1 | awk '{print $2}')
   GROWTH=$((FINAL - BASELINE))
   GROWTH_MB=$(echo "scale=2; ${GROWTH}/1024" | bc)

   echo "Memory growth: ${GROWTH_MB} MB"
   # PASS if < 40 MB (10 MB/hour average)
   ```

2. **Buffer Error Count**
   ```bash
   # Count overflow/underrun events
   OVERFLOW=$(grep -ci "overflow" test-results/4hour-stability/device-A/logcat-full.txt)
   UNDERRUN=$(grep -ci "underrun" test-results/4hour-stability/device-A/logcat-full.txt)

   echo "Buffer overflows: ${OVERFLOW}"
   echo "Buffer underruns: ${UNDERRUN}"
   # PASS if both == 0
   ```

3. **Latency Compliance**
   ```bash
   # Compare baseline vs final latency
   # PASS if both ≤ 100ms
   ```

4. **MediaCodec Errors**
   ```bash
   ERRORS=$(grep -ci "mediacodec.*error" test-results/4hour-stability/device-A/logcat-full.txt)
   echo "MediaCodec errors: ${ERRORS}"
   # PASS if == 0
   ```

5. **AudioRecord Failures**
   ```bash
   FAILURES=$(grep -ci "audiorecord.*fail" test-results/4hour-stability/device-A/logcat-full.txt)
   echo "AudioRecord failures: ${FAILURES}"
   # PASS if == 0
   ```

6. **Recovery Success**
   ```bash
   # Check for recovery after CPU spike
   grep -i "recovery\|resumed" test-results/4hour-stability/device-A/logcat-full.txt
   # PASS if recovery indicators present
   ```

## Success Criteria

### PASS Conditions (MUST meet ALL criteria on BOTH devices)

#### Audio Quality
- [x] No distortion, popping, or clicking throughout 4 hours
- [x] No audio dropouts or silence gaps
- [x] Audio/video synchronization maintained
- [x] Quality consistent with baseline at all checkpoints

#### Buffer Health
- [x] Zero buffer overflow events
- [x] Zero buffer underrun events
- [x] Buffer health status "OK" throughout test
- [x] No buffer warnings in logs

#### Memory Stability
- [x] Memory growth < 40 MB over 4 hours
- [x] No unbounded heap growth pattern
- [x] Heap dump shows no memory leaks
- [x] GC patterns normal (no excessive collections)

#### Latency Compliance
- [x] Baseline latency ≤ 100ms
- [x] Final latency (4 hours) ≤ 100ms
- [x] Latency drift < 30% over session
- [x] AudioHealthMonitor accuracy ±10ms

#### Error-Free Operation
- [x] Zero MediaCodec errors
- [x] Zero AudioRecord failures
- [x] No app crashes or ANRs
- [x] No system warnings or force stops

#### Automatic Recovery
- [x] Stream recovers from simulated CPU spike within 60s
- [x] Recovery occurs without manual intervention
- [x] Audio quality returns to normal after recovery
- [x] No error cascade after recovery

#### Device Stability
- [x] App remains responsive throughout test
- [x] Device temperature acceptable (no thermal throttling)
- [x] Battery drain acceptable (if unplugged)
- [x] No system resource exhaustion

### FAIL Conditions (ANY failure requires investigation)

- [ ] Any audio distortion, popping, or quality degradation
- [ ] Buffer overflow or underrun detected
- [ ] Memory growth ≥ 40 MB
- [ ] Latency > 100ms at any measurement point
- [ ] Any MediaCodec or AudioRecord errors
- [ ] App crash or ANR during test
- [ ] Stream fails to recover from CPU spike
- [ ] Different results between Device A and Device B (inconsistency)

## Test Report

After completing the test, fill out: `./docs/testing/test-report-4hour-stability.md`

### Report Requirements

Include for **BOTH devices:**
1. Device specifications (model, Android version, audio hardware)
2. All baseline and final measurements
3. Memory growth calculations with charts
4. Complete error counts
5. Recovery test results
6. Audio quality assessment at all checkpoints
7. Screenshots from Android Studio Profiler
8. PASS/FAIL determination with justification

## Troubleshooting

### Issue: Stream disconnects before 4 hours

**Diagnosis:**
```bash
# Check for network errors
adb logcat -d | grep -i "network\|disconnect\|timeout"

# Check RTMP server logs
curl http://<rtmp-server>:8080/stats
```

**Solutions:**
- Verify RTMP server stability (server-side logs)
- Switch to WiFi if using mobile data
- Increase network timeout settings
- Check for network interference

### Issue: Device overheats and throttles

**Diagnosis:**
```bash
# Check temperature
adb shell dumpsys battery | grep temperature

# Check thermal throttling
adb shell cat /sys/devices/virtual/thermal/thermal_zone*/temp
```

**Solutions:**
- Move device to cooler environment
- Remove device case for better ventilation
- Reduce resolution/bitrate temporarily
- Use external cooling (fan, cooling pad)

### Issue: Memory growth exceeds limits

**Diagnosis:**
```bash
# Analyze heap dump in Android Studio
# File → Open → Select heap-4hour.hprof
# Look for:
# - Retained objects not being garbage collected
# - Large bitmap allocations
# - Leaked Activity/Context references
```

**Solutions:**
- Document memory leak evidence
- Report as bug with heap dump attached
- Do NOT mark test as PASS
- Investigation required before proceeding

### Issue: Audio quality degrades over time

**Diagnosis:**
```bash
# Check for buffer health degradation
adb logcat -d -s AudioHealth:* | grep -i "degraded\|warning"

# Check for CPU/memory pressure
adb shell top -n 1 | head -20
```

**Solutions:**
- Document exact timestamp when degradation started
- Capture audio recording for analysis
- Check server-side for issues
- Report as critical bug

### Issue: Recovery fails after CPU spike

**Diagnosis:**
```bash
# Check for crash or error cascade
adb logcat -d | grep -A 20 "cpu.*spike"

# Check if AudioCaptureManager recovery triggered
adb logcat -d -s AudioHealth:* | grep -i "recovery"
```

**Solutions:**
- Document recovery failure with logs
- Verify AudioCaptureManager.triggerRecovery() implementation
- Report as critical bug (Spec Requirement 3 not met)
- Do NOT mark test as PASS

### Issue: Different results on Device A vs Device B

**Possible Causes:**
- Hardware sample rate mismatch (48kHz vs 44.1kHz)
- Different Android versions with different audio pipelines
- Device-specific audio HAL implementations
- Different background app interference

**Investigation:**
```bash
# Compare audio configurations
adb -s ${DEVICE_A} logcat -d | grep "SampleRate"
adb -s ${DEVICE_B} logcat -d | grep "SampleRate"

# Compare device specs
adb -s ${DEVICE_A} shell getprop ro.product.model
adb -s ${DEVICE_B} shell getprop ro.product.model
```

**Action:**
- Document differences in test report
- If one device passes and other fails: investigate device-specific issues
- Both must PASS for overall test PASS

## Post-Test Actions

### On PASS (Both Devices)
1. Complete test report with all evidence
2. Archive test artifacts (logs, heap dumps, screenshots)
3. Update implementation_plan.json: Mark subtask-5-4 as "completed"
4. Commit test results: `git add test-results/ && git commit -m "test: 4-hour stability test PASS on Device A and Device B"`
5. Proceed to QA sign-off

### On FAIL (Any Device)
1. Complete test report documenting failures
2. Create GitHub issues for each failure category
3. Attach logs, heap dumps, and evidence
4. **DO NOT** mark subtask as completed
5. **DO NOT** proceed to QA sign-off
6. Investigate and fix issues
7. Re-run test after fixes

## Related Documentation

- **Test Report Template**: `./docs/testing/test-report-4hour-stability.md`
- **Monitoring Script**: `./scripts/test-4hour-stability.sh`
- **Analysis Script**: `./scripts/analyze-4hour-results.sh`
- **Latency Measurement**: `./scripts/measure-audio-latency.sh`
- **Spec Document**: `./.auto-claude/specs/003-long-session-audio-stability/spec.md`
- **Implementation Plan**: `./.auto-claude/specs/003-long-session-audio-stability/implementation_plan.json`

## Test Duration

**Total time required: ~5-6 hours**
- Setup and preparation: 30 minutes
- Test execution: 4 hours
- Analysis and reporting: 30-60 minutes

**Plan accordingly:**
- Block calendar for uninterrupted 6-hour window
- Ensure RTMP server uptime guarantee
- Have backup devices ready in case of hardware failure
- Schedule during stable network hours (avoid peak times)
