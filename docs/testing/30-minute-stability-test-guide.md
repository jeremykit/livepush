# 30-Minute Stability Test Guide

## Overview
This test verifies that the audio pipeline maintains stability for extended streaming sessions without buffer overflows, memory leaks, or quality degradation.

## Prerequisites

### Hardware Requirements
- **Physical Android device** (API 24+)
  - Recommended: Samsung Galaxy or Google Pixel
  - Must have stable USB connection for ADB
  - Ensure device stays awake during test (Developer Options → Stay Awake enabled)

### Software Requirements
- Android Studio installed with Android SDK
- ADB accessible in system PATH
- RTMP test server running (see Server Setup below)

### Device Preparation
```bash
# Enable USB debugging
# Settings → Developer Options → USB Debugging

# Verify ADB connection
adb devices

# Expected output:
# List of devices attached
# <device-id>    device

# Keep device awake during test
adb shell settings put global stay_on_while_plugged_in 7

# Increase logcat buffer size
adb logcat -G 16M
```

## Server Setup

### Option 1: Local RTMP Server (Recommended)
```bash
# Using Docker
docker run -d -p 1935:1935 -p 8080:8080 --name rtmp-server tiangolo/nginx-rtmp

# Test URL will be:
# rtmp://localhost/live/test
```

### Option 2: Remote Test Server
```bash
# Use a stable RTMP server with at least 4 hours uptime guarantee
# Example: rtmp://your-test-server.com/live/test
```

## Test Execution

### Step 1: Build and Install Debug APK
```bash
# Navigate to project root
cd E:\237\live\livepush

# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Verify APK created
ls -lh app/build/outputs/apk/debug/app-debug.apk

# Install on device
./gradlew installDebug

# Verify installation
adb shell pm list packages | grep com.livepush
```

### Step 2: Start Monitoring Script
Open a **new terminal window** and run:
```bash
# Navigate to project
cd E:\237\live\livepush

# Run automated monitoring script
./scripts/monitor-audio-stability.sh 30

# This script will:
# - Monitor logcat for audio errors
# - Track memory usage every 60 seconds
# - Collect buffer health metrics
# - Generate report after test completion
```

### Step 3: Start Android Studio Profiler
1. Open Android Studio
2. Navigate to **View → Tool Windows → Profiler**
3. Click **+** to start new profiling session
4. Select your connected device
5. Select **com.livepush** process
6. Enable **Memory** profiler

**What to Monitor:**
- **Heap size** should remain stable (not continuously increasing)
- **Allocations** should show steady GC cycles, not unbounded growth
- **Native memory** should be stable after initial streaming setup

Take screenshots at:
- **0 minutes** (baseline)
- **15 minutes** (mid-test)
- **30 minutes** (final)

### Step 4: Start Streaming
```bash
# Launch app
adb shell am start -n com.livepush/.MainActivity

# OR manually:
# 1. Tap LivePush icon on device
# 2. Grant Camera and Microphone permissions if prompted
# 3. Enter RTMP URL: rtmp://localhost/live/test
# 4. Tap "Start Stream" button
```

**Set timer for 30 minutes.**

### Step 5: Monitor in Real-Time

#### Terminal 1: Audio Health Logs
```bash
adb logcat -c  # Clear logs
adb logcat -s AudioHealth:* AudioBuffer:* MediaCodec:E AudioRecord:E | tee test-audio-health.log

# Watch for these indicators:
# ✅ GOOD: "Buffer health: OK"
# ✅ GOOD: "PTS calculation: monotonic"
# ❌ BAD: "Buffer overflow detected"
# ❌ BAD: "Buffer underrun detected"
# ❌ BAD: "MediaCodec error"
```

#### Terminal 2: Memory Monitoring
```bash
# Run this every 5 minutes manually, or use the monitoring script
watch -n 300 'adb shell dumpsys meminfo com.livepush | grep -E "(TOTAL|Native Heap|Dalvik Heap)"'

# Expected behavior:
# - Initial TOTAL: ~50-80 MB (baseline)
# - After 30 min: Should increase by <10 MB
# - No continuous growth pattern
```

#### Terminal 3: Sample Rate Verification
```bash
adb logcat | grep -i "SampleRate"

# Expected output (one of these):
# "Hardware sample rate detected: 48000 Hz"  (Samsung devices)
# "Hardware sample rate detected: 44100 Hz"  (Pixel devices)
# "Using configured sample rate: 44100 Hz"   (API <24 fallback)
```

### Step 6: During Test - Quality Verification

**Audio Quality Checks** (every 10 minutes):
- [ ] No popping or clicking sounds
- [ ] No distortion or robotic voice
- [ ] No audio dropouts or silence gaps
- [ ] Audio/video sync maintained

**Device Stability:**
- [ ] App remains responsive
- [ ] No ANR (Application Not Responding) dialogs
- [ ] Device temperature acceptable (not overheating)
- [ ] No system warnings

### Step 7: Stop Streaming After 30 Minutes
```bash
# Tap "Stop Stream" button in app
# OR via ADB:
adb shell input keyevent KEYCODE_BACK
```

### Step 8: Collect Results

#### Memory Report
```bash
# Final memory dump
adb shell dumpsys meminfo com.livepush > test-memory-final.txt

# Compare with baseline
# Calculate growth:
# Growth = Final TOTAL - Initial TOTAL
# PASS if Growth < 10 MB
```

#### Logcat Analysis
```bash
# Check for errors
grep -i "error\|fail\|overflow\|underrun" test-audio-health.log

# Expected: No matching lines
# If errors found, investigate each occurrence
```

#### Heap Dump (Optional, for leak investigation)
```bash
# Trigger GC first
adb shell am broadcast -a com.livepush.TRIGGER_GC

# Capture heap dump
adb shell am dumpheap com.livepush /data/local/tmp/heap-30min.hprof

# Pull to local machine
adb pull /data/local/tmp/heap-30min.hprof ./test-results/

# Analyze with Android Studio:
# File → Open → Select heap-30min.hprof
# Look for memory leaks in Leak Canary view
```

## Success Criteria

### PASS Conditions
- [x] **Zero buffer errors**: No overflow/underflow in logs
- [x] **Memory stable**: Growth <10 MB over 30 minutes
- [x] **Audio quality**: No distortion, popping, or degradation
- [x] **No crashes**: App runs continuously for 30 minutes
- [x] **Logcat clean**: No MediaCodec or AudioRecord errors
- [x] **Sample rate detected**: Hardware rate properly detected

### FAIL Conditions (requires investigation)
- [ ] Buffer overflow/underflow detected
- [ ] Memory growth >10 MB
- [ ] Audio quality degradation
- [ ] App crashes or ANR
- [ ] MediaCodec errors in logs
- [ ] Sample rate mismatch warnings

## Test Report Template

After completing the test, fill out: `./docs/testing/test-report-30min-stability.md`

## Troubleshooting

### Issue: "adb: device unauthorized"
```bash
# On device, check for USB debugging authorization prompt
# Tap "Always allow from this computer" → OK
# Run: adb devices
```

### Issue: "App crashes on start"
```bash
# Check crash logs
adb logcat -d | grep -i "crash\|exception" > crash.log

# Verify permissions granted
adb shell dumpsys package com.livepush | grep permission

# Reinstall clean
adb uninstall com.livepush
./gradlew installDebug
```

### Issue: "RTMP connection failed"
```bash
# Verify RTMP server running
curl -I http://localhost:8080  # Should return 200 OK

# Check network reachability
adb shell ping -c 3 <rtmp-server-ip>

# Check app logs
adb logcat | grep -i "rtmp"
```

### Issue: "Memory profiler shows large growth"
```bash
# Trigger manual GC in profiler
# Check if growth persists after GC
# If yes, capture heap dump and investigate
# Look for retained objects in Memory Profiler → Heap Dump
```

## Next Steps

After **PASS** result:
- Proceed to subtask-5-2 (Hardware sample rate detection test)
- Document findings in test report

After **FAIL** result:
- Analyze failure logs
- Report issue with detailed evidence
- Do NOT proceed to next subtask until resolved

## Related Documentation
- **Test Report Template**: `./docs/testing/test-report-30min-stability.md`
- **Monitoring Script**: `./scripts/monitor-audio-stability.sh`
- **Log Analysis Tool**: `./scripts/analyze-audio-logs.py`
- **Spec Document**: `./.auto-claude/specs/003-long-session-audio-stability/spec.md`
