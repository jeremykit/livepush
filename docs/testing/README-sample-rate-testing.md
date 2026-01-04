# Sample Rate Detection Testing

## Quick Start

This directory contains comprehensive testing infrastructure for validating hardware sample rate detection across multiple Android devices.

### What This Tests

The sample rate detection test verifies that LivePush correctly:
- Detects native hardware sample rates (44100Hz vs 48000Hz)
- Uses `AudioFormat.SAMPLE_RATE_UNSPECIFIED` on API 24+ devices
- Falls back to configured rate on API <24 devices
- Prevents buffer size mismatches that cause audio distortion

### Why This Matters

**Problem:** Different Android devices use different native audio sample rates:
- Samsung devices: typically 48000 Hz
- Google Pixel devices: typically 44100 Hz

**Impact:** If app hardcodes 44100 Hz but hardware uses 48000 Hz, buffer size mismatches cause audio popping, distortion, and crashes during long streaming sessions (4+ hours).

**Solution:** Hardware sample rate detection with `SAMPLE_RATE_UNSPECIFIED` (API 24+) ensures buffer sizes match hardware expectations.

## Files in This Directory

| File | Purpose | When to Use |
|------|---------|-------------|
| `hardware-sample-rate-test-guide.md` | Comprehensive test procedure | Read first - step-by-step manual testing instructions |
| `test-report-sample-rate-detection.md` | Test results template | Fill out after testing all devices |
| `README-sample-rate-testing.md` | This file | Overview and quick reference |

## Test Execution Workflow

### Prerequisites
- 3+ physical devices (Samsung, Pixel, API 24-26 device)
- ADB installed and devices connected
- RTMP test server running
- Debug APK built and installed

### Steps

#### 1. Prepare Environment
```bash
# Navigate to project
cd E:\237\live\livepush

# Build debug APK
./gradlew clean assembleDebug

# Create results directory
mkdir -p ./test-results/sample-rate-detection/device-logs
```

#### 2. Test Each Device
For each device, run:
```bash
# Get device ID
adb devices

# Run automated test
./scripts/test-sample-rate-detection.sh <device-id>

# Example:
./scripts/test-sample-rate-detection.sh emulator-5554
```

The script will:
- Capture device information
- Launch app and monitor logcat
- Detect sample rate in logs
- Check for buffer warnings
- Generate pass/fail report

#### 3. Manual Testing (if automated script unavailable)
Follow detailed steps in `hardware-sample-rate-test-guide.md`:
1. Install app on device
2. Clear logcat: `adb -s <device-id> logcat -c`
3. Start stream in app
4. Monitor logs: `adb -s <device-id> logcat | grep -E "SampleRate|AudioConfig"`
5. Verify sample rate detected
6. Check for buffer warnings

#### 4. Document Results
Fill out `test-report-sample-rate-detection.md` for each device tested.

#### 5. Analyze and Sign-Off
- Review all device results
- Ensure all critical conditions met
- Sign-off on test report

## Expected Results by Device Type

### Samsung Devices (Galaxy S series)
```
✅ Expected: 48000 Hz detected
✅ Log pattern: "Hardware sample rate detected: 48000 Hz"
✅ Buffer config: 7680 bytes (factor=2.0)
✅ No buffer warnings
```

### Google Pixel Devices
```
✅ Expected: 44100 Hz detected
✅ Log pattern: "Hardware sample rate detected: 44100 Hz"
✅ Buffer config: 7056 bytes (factor=2.0)
✅ No buffer warnings
```

### API 24-26 Devices (Android 7.0-8.0)
```
✅ Expected: 44100 Hz (hardware detection OR fallback)
✅ Log pattern: "Hardware sample rate detected: 44100 Hz"
   OR: "Using configured sample rate: 44100 Hz (API <24)"
✅ Buffer config: 7056 bytes (factor=2.0)
✅ No buffer warnings
```

### API <24 Devices (Android 6.x)
```
✅ Expected: 44100 Hz (fallback)
✅ Log pattern: "Using configured sample rate: 44100 Hz (API <24)"
✅ Buffer config: 7056 bytes (factor=2.0)
✅ No buffer warnings
```

## Pass/Fail Criteria

### Overall PASS
All of these conditions must be true:
- [ ] At least 3 devices tested (Samsung, Pixel, Legacy)
- [ ] Sample rate detected on all devices
- [ ] Zero buffer mismatch warnings
- [ ] Sample rates match expected values for hardware
- [ ] All devices stream successfully for at least 90 seconds

### Overall FAIL
Any of these conditions triggers FAIL:
- [ ] Sample rate not detected on any device
- [ ] Buffer mismatch warnings appear
- [ ] Incorrect sample rate for known hardware
- [ ] Stream fails to start
- [ ] AudioRecord or MediaCodec errors

## Troubleshooting

### Issue: "Sample rate not detected in logs"
**Cause:** App may not have initialized audio pipeline
**Solution:**
1. Verify app has RECORD_AUDIO permission
2. Ensure "Start Stream" was pressed
3. Wait at least 30 seconds after starting stream
4. Check for permission denial in logcat

### Issue: "Buffer mismatch on Samsung device"
**Cause:** Sample rate detection not working, using hardcoded 44100 Hz
**Solution:**
1. Verify code uses `AudioFormat.SAMPLE_RATE_UNSPECIFIED` on API 24+
2. Check `RtmpStreamManager.kt` implementation
3. Ensure `AudioCaptureManager` properly detects hardware rate
4. Review spec.md section on RootEncoder audio configuration

### Issue: "Multiple devices show different results"
**Analysis:** This is expected - different hardware has different capabilities
**Action:**
- Document actual rates for each device
- Verify no buffer warnings on any device
- As long as detection works and no warnings, this is PASS

## Related Documentation

- **Spec:** `.auto-claude/specs/003-long-session-audio-stability/spec.md`
- **Code:** `app/src/main/java/com/livepush/streaming/RtmpStreamManager.kt`
- **Code:** `app/src/main/java/com/livepush/streaming/capture/AudioCaptureManager.kt`
- **Previous Test:** `./30-minute-stability-test-guide.md`
- **Next Test:** `./audio-latency-test-guide.md` (subtask-5-3)

## Support

If you encounter issues during testing:
1. Check `hardware-sample-rate-test-guide.md` troubleshooting section
2. Review full logcat: `./test-results/sample-rate-detection/device-logs/*_full-logcat.log`
3. Check for known issues in spec.md
4. Consult RootEncoder issue #743 for reference

## Test Status Checklist

After completing sample rate detection testing:
- [ ] Tested on Samsung device (48kHz) - PASS
- [ ] Tested on Pixel device (44.1kHz) - PASS
- [ ] Tested on API 24-26 device - PASS
- [ ] Test report completed
- [ ] Logs archived
- [ ] Ready to proceed to subtask-5-3 (audio latency compliance)
