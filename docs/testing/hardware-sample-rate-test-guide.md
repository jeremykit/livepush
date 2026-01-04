# Hardware Sample Rate Detection Test Guide

## Overview
This test verifies that the audio pipeline correctly detects and adapts to different hardware audio sample rates across various Android devices. This prevents buffer size mismatches that cause audio distortion in extended streaming sessions.

## Test Objectives
- Verify sample rate auto-detection on API 24+ devices
- Test fallback behavior on API <24 devices (Android 6.x)
- Confirm no buffer mismatch warnings across different hardware
- Validate support for 44100Hz (Pixel) and 48000Hz (Samsung) hardware

## Prerequisites

### Required Test Devices
You need **at least 3 physical devices** from different categories:

| Category | Hardware | Example Devices | Expected Sample Rate |
|----------|----------|-----------------|---------------------|
| **Samsung** | 48kHz native | Galaxy S21/S22/S23, Note 20 | 48000 Hz |
| **Google Pixel** | 44.1kHz native | Pixel 5/6/7/8 | 44100 Hz |
| **Legacy Device** | API 24-26 | Any Android 7.0-8.0 device | 44100 Hz (fallback) |

**Note:** If you don't have access to all device types, test on whatever devices are available and document the results.

### Software Requirements
- Android Studio with Android SDK
- ADB accessible in system PATH
- RTMP test server (see 30-minute-stability-test-guide.md for setup)

### Device Preparation
For **each device**, run:
```bash
# Enable USB debugging
# Settings → Developer Options → USB Debugging

# Verify ADB connection
adb devices

# Note: If multiple devices connected, use -s flag:
# adb -s <device-id> <command>

# Check device API level
adb shell getprop ro.build.version.sdk

# Check device model
adb shell getprop ro.product.model
```

## Test Execution

### Phase 1: Preparation

#### Step 1: Build Debug APK
```bash
# Navigate to project root
cd E:\237\live\livepush

# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Verify APK created
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

#### Step 2: Create Test Log Directory
```bash
# Create directory for test results
mkdir -p ./test-results/sample-rate-detection
mkdir -p ./test-results/sample-rate-detection/device-logs
```

### Phase 2: Device-by-Device Testing

For **each test device**, perform the following steps:

#### Device Setup
```bash
# Connect device and verify
adb devices

# If multiple devices, note the device ID for -s flag usage
DEVICE_ID=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
echo "Testing device: ${DEVICE_ID}"

# Get device information
adb -s ${DEVICE_ID} shell getprop ro.product.model > ./test-results/sample-rate-detection/device-logs/${DEVICE_ID}-info.txt
adb -s ${DEVICE_ID} shell getprop ro.build.version.sdk >> ./test-results/sample-rate-detection/device-logs/${DEVICE_ID}-info.txt
adb -s ${DEVICE_ID} shell getprop ro.product.manufacturer >> ./test-results/sample-rate-detection/device-logs/${DEVICE_ID}-info.txt

# Display device info
cat ./test-results/sample-rate-detection/device-logs/${DEVICE_ID}-info.txt
```

#### Install and Prepare
```bash
# Uninstall previous version
adb -s ${DEVICE_ID} uninstall com.livepush 2>/dev/null || true

# Install fresh build
adb -s ${DEVICE_ID} install app/build/outputs/apk/debug/app-debug.apk

# Clear logcat buffer
adb -s ${DEVICE_ID} logcat -c

# Increase logcat buffer size
adb -s ${DEVICE_ID} logcat -G 16M
```

#### Run Automated Test Script
```bash
# Use the automated detection script
./scripts/test-sample-rate-detection.sh ${DEVICE_ID}

# This will:
# - Launch the app
# - Monitor logcat for sample rate detection
# - Capture audio configuration logs
# - Check for buffer mismatch warnings
# - Generate device-specific report
```

#### Manual Verification Steps

**Terminal 1: Sample Rate Detection Log**
```bash
# Monitor for sample rate detection
adb -s ${DEVICE_ID} logcat -c
adb -s ${DEVICE_ID} logcat | grep -E "SampleRate|AudioConfig|prepareAudio" \
    | tee ./test-results/sample-rate-detection/device-logs/${DEVICE_ID}-sample-rate.log
```

**Terminal 2: Buffer Health Monitoring**
```bash
# Watch for buffer mismatch warnings
adb -s ${DEVICE_ID} logcat | grep -E "buffer.*mismatch|BufferOverflow|BufferUnderrun" \
    | tee ./test-results/sample-rate-detection/device-logs/${DEVICE_ID}-buffer-warnings.log
```

**App Actions:**
1. Launch app on device: `adb -s ${DEVICE_ID} shell am start -n com.livepush/.MainActivity`
2. Grant Camera and Microphone permissions if prompted
3. Enter RTMP URL: `rtmp://localhost/live/test` (or your test server)
4. Tap "Start Stream" button
5. **Wait 60 seconds** for audio pipeline initialization
6. Check Terminal 1 for sample rate detection logs
7. Check Terminal 2 for any buffer warnings
8. Tap "Stop Stream" button

#### Capture Results
```bash
# Capture full logcat for analysis
adb -s ${DEVICE_ID} logcat -d > ./test-results/sample-rate-detection/device-logs/${DEVICE_ID}-full-logcat.txt

# Extract key configuration lines
grep -E "Hardware sample rate|Using configured sample rate|Audio prepared" \
    ./test-results/sample-rate-detection/device-logs/${DEVICE_ID}-full-logcat.txt \
    > ./test-results/sample-rate-detection/device-logs/${DEVICE_ID}-config-summary.txt

# Display summary
echo "========================================="
echo "Device: ${DEVICE_ID}"
cat ./test-results/sample-rate-detection/device-logs/${DEVICE_ID}-info.txt
echo "-----------------------------------------"
cat ./test-results/sample-rate-detection/device-logs/${DEVICE_ID}-config-summary.txt
echo "========================================="
```

### Phase 3: Analysis

#### Expected Log Patterns

**API 24+ Devices (Modern):**
```
D/RtmpStreamManager: Preparing audio with detected sample rate
D/AudioCaptureManager: Hardware sample rate detected: 48000 Hz
I/RtmpCamera: prepareAudio: sampleRate=48000, bitrate=128000, isStereo=true
D/AudioBuffer: Buffer size calculated: 7680 bytes (factor=2.0)
```
OR
```
D/RtmpStreamManager: Preparing audio with detected sample rate
D/AudioCaptureManager: Hardware sample rate detected: 44100 Hz
I/RtmpCamera: prepareAudio: sampleRate=44100, bitrate=128000, isStereo=true
D/AudioBuffer: Buffer size calculated: 7056 bytes (factor=2.0)
```

**API <24 Devices (Legacy Fallback):**
```
D/RtmpStreamManager: API <24, using configured sample rate
D/AudioCaptureManager: Using configured sample rate: 44100 Hz (API <24)
I/RtmpCamera: prepareAudio: sampleRate=44100, bitrate=128000, isStereo=true
D/AudioBuffer: Buffer size calculated: 7056 bytes (factor=2.0)
```

**Unexpected Patterns (FAIL):**
```
E/AudioRecord: Buffer size mismatch: expected 3840, got 7680
W/MediaCodec: AudioRecord buffer size does not match encoder expectations
E/RtmpCamera: Audio capture failed: buffer overflow
```

#### Check for Issues
```bash
# Check all device logs for errors
for log in ./test-results/sample-rate-detection/device-logs/*-buffer-warnings.log; do
    if [ -s "$log" ]; then
        echo "⚠️  Warnings found in: $log"
        cat "$log"
    fi
done

# Count buffer mismatches across all devices
grep -r "mismatch\|overflow\|underrun" ./test-results/sample-rate-detection/device-logs/ | wc -l
```

## Success Criteria

### Overall PASS Conditions
- [x] **Samsung device (48kHz)**: Sample rate detected as 48000 Hz, no buffer warnings
- [x] **Pixel device (44.1kHz)**: Sample rate detected as 44100 Hz, no buffer warnings
- [x] **API 24-26 device**: Fallback to 44100 Hz works, no buffer warnings
- [x] **All devices**: 'SampleRate' messages appear in logcat
- [x] **Zero buffer mismatches**: No "buffer mismatch" warnings on any device

### Per-Device PASS Conditions
For each device:
- [ ] Sample rate detection log present
- [ ] No buffer overflow/underrun warnings
- [ ] Audio stream starts successfully
- [ ] No AudioRecord or MediaCodec errors
- [ ] Configuration summary shows expected sample rate

### FAIL Conditions (requires investigation)
- [ ] Sample rate not detected on API 24+ device
- [ ] Buffer mismatch warnings appear
- [ ] Different sample rate than expected for hardware
- [ ] AudioRecord initialization fails
- [ ] Stream fails to start

## Test Report Template

After testing all devices, fill out: `./docs/testing/test-report-sample-rate-detection.md`

Include for each device:
- Device model and manufacturer
- Android API level
- Detected sample rate
- Buffer configuration
- Any warnings or errors
- Screenshot of successful stream start

## Troubleshooting

### Issue: "Sample rate not logged"
```bash
# Check if audio pipeline initialized
adb logcat -d | grep -i "AudioCaptureManager\|RtmpStreamManager"

# Verify app has RECORD_AUDIO permission
adb shell dumpsys package com.livepush | grep "android.permission.RECORD_AUDIO"

# Check for permission denial
adb logcat -d | grep -i "permission denied"
```

### Issue: "Buffer mismatch on Samsung device"
```bash
# This indicates sample rate detection may not be working
# Check if SAMPLE_RATE_UNSPECIFIED is being used

# Expected in code:
# val actualSampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
#     AudioFormat.SAMPLE_RATE_UNSPECIFIED
# } else {
#     audioConfig.sampleRate
# }

# Verify logs show detection, not hardcoded value
adb logcat -d | grep "sample rate"
```

### Issue: "Multiple devices connected, can't test individually"
```bash
# List all connected devices
adb devices

# Use -s flag for specific device
adb -s <device-id> <command>

# Example:
adb -s emulator-5554 logcat
```

### Issue: "API level check shows device is API 24+ but using fallback"
```bash
# This indicates a code issue
# Check RtmpStreamManager implementation

# Verify Build.VERSION.SDK_INT check is correct
# Should use AudioFormat.SAMPLE_RATE_UNSPECIFIED on API 24+
```

## Device-Specific Notes

### Samsung Devices (48kHz)
- Galaxy S series (S8 and newer) typically use 48000 Hz native sample rate
- Some older Galaxy devices may use 44100 Hz
- Check device-specific audio HAL configuration if unexpected results

### Google Pixel Devices (44.1kHz)
- Pixel 2 through Pixel 8 typically use 44100 Hz native sample rate
- Consistent across Pixel lineup
- Should detect 44100 Hz on API 24+

### Legacy Devices (API <24)
- Android 7.0 (API 24) is the minimum for SAMPLE_RATE_UNSPECIFIED
- Android 6.x (API 23) devices must use fallback
- Expected to use 44100 Hz configured value

### Emulator Testing
**Note:** Android emulators may not accurately represent physical device audio hardware.
- Emulator typically reports 44100 Hz
- Physical device testing is REQUIRED for valid results
- Do not rely solely on emulator results

## Next Steps

### After PASS:
1. Document all device results in test report
2. Archive logs: `tar -czf sample-rate-test-results.tar.gz ./test-results/sample-rate-detection/`
3. Proceed to subtask-5-3 (Audio latency compliance test)

### After FAIL:
1. Analyze failure logs for each affected device
2. Check for code issues in sample rate detection logic
3. Verify AudioFormat.SAMPLE_RATE_UNSPECIFIED usage
4. Re-test after fixes
5. Do NOT proceed until all devices PASS

## Related Documentation
- **Test Report Template**: `./docs/testing/test-report-sample-rate-detection.md`
- **Automated Test Script**: `./scripts/test-sample-rate-detection.sh`
- **Spec Document**: `./.auto-claude/specs/003-long-session-audio-stability/spec.md`
- **30-Minute Stability Test**: `./docs/testing/30-minute-stability-test-guide.md`
- **Sample Rate Detection Pattern**: See spec.md section "RootEncoder Audio Configuration"
