# 4-Hour Extended Stability Testing

## Quick Start

This is the **CRITICAL** acceptance test for the Long Session Audio Stability feature. This test must PASS on at least 2 different physical devices before the feature can be released.

### Prerequisites

- 2 physical Android devices (API 24+)
- Stable RTMP server (4+ hours uptime)
- ~6 hours of uninterrupted time (setup + test + analysis)
- Android Studio with Profiler
- ADB configured

### Quick Test Execution

```bash
# 1. Verify prerequisites
adb devices | grep -c "device$"  # Should show 2+

# 2. Build and install release APK on both devices
./gradlew assembleRelease
adb -s <device-A-id> install -r app/build/outputs/apk/release/app-release.apk
adb -s <device-B-id> install -r app/build/outputs/apk/release/app-release.apk

# 3. Start monitoring on both devices (2 separate terminals)
# Terminal 1:
./scripts/test-4hour-stability.sh <device-A-id> device-A

# Terminal 2:
./scripts/test-4hour-stability.sh <device-B-id> device-B

# 4. Start streaming on both devices
# Follow prompts in the app on each device

# 5. Wait 4 hours (monitoring scripts will run automatically)

# 6. After completion, fill out test report
# Edit: docs/testing/test-report-4hour-stability.md

# 7. If PASS on both devices, mark subtask complete
# Update: .auto-claude/specs/003-long-session-audio-stability/implementation_plan.json
```

## Test Overview

### What This Test Validates

This test validates **ALL** critical requirements from the spec:

1. **Spec Requirement 1**: Audio quality consistent for 4+ hours without degradation
2. **Spec Requirement 2**: Zero buffer overflow/underflow during normal operation
3. **Spec Requirement 3**: Automatic recovery from transient failures
4. **Spec Requirement 4**: Memory stability with no leaks over extended sessions
5. **Spec Requirement 5**: Audio latency ≤100ms throughout stream duration
6. **Spec Requirement 6**: Hardware compatibility (44100Hz vs 48000Hz)

### Why 2 Devices Are Required

Testing on multiple devices ensures:
- Hardware compatibility across different audio subsystems
- Consistent behavior across manufacturers (Samsung, Google, etc.)
- Sample rate detection works correctly (48kHz vs 44.1kHz hardware)
- No device-specific bugs or edge cases

**Recommended device combinations:**
- Samsung Galaxy (48kHz) + Google Pixel (44.1kHz)
- Mid-range Samsung + Different manufacturer
- High-end device + Low-end device (API 24-26)

### Test Duration

**Total time: ~5-6 hours**

| Phase | Duration | Description |
|-------|----------|-------------|
| Setup | 30 min | Device preparation, build installation, RTMP server setup |
| Test Execution | 4 hours | Automated monitoring, periodic quality checks |
| Analysis | 30-60 min | Log analysis, report generation, evidence collection |

## Test Procedure

### Detailed Guide

Comprehensive step-by-step instructions:
- **Location**: `docs/testing/4-hour-extended-stability-test-guide.md`
- **Read this first** before starting the test

### Test Report Template

Structured report with all required metrics:
- **Location**: `docs/testing/test-report-4hour-stability.md`
- Fill out after test completion
- Required for PASS/FAIL determination

## Automated Monitoring

### Monitoring Script

**Script**: `scripts/test-4hour-stability.sh`

**What it monitors:**
- Memory usage (every 5 minutes)
- Audio latency (every 30 minutes)
- Buffer health (every 10 minutes)
- CPU usage (continuous)
- Logcat errors (continuous)

**Outputs:**
- `logcat-full.log` - Complete logcat output
- `memory-samples.csv` - Memory timeline
- `latency-samples.csv` - Latency measurements
- `buffer-health-samples.csv` - Buffer health metrics
- `result.txt` - PASS/FAIL determination

### Analysis Script

**Script**: `scripts/analyze-4hour-results.sh` (if available)

Automatically analyzes test results and generates summary report.

## Acceptance Criteria

### PASS Criteria (ALL must be met on BOTH devices)

#### Memory
- [x] Memory growth < 40 MB over 4 hours
- [x] No memory leaks in heap dump
- [x] Growth rate < 10 MB/hour

#### Buffer Health
- [x] Zero buffer overflow events
- [x] Zero buffer underrun events
- [x] Buffer health score > 0.9 throughout

#### Audio Quality
- [x] No distortion, popping, or clicking
- [x] No audio dropouts
- [x] Audio/video sync maintained
- [x] Quality consistent with baseline

#### Latency
- [x] All measurements ≤ 100ms
- [x] Latency drift < 30%
- [x] No latency warnings in logs

#### Errors
- [x] Zero MediaCodec errors
- [x] Zero AudioRecord failures
- [x] Zero critical errors
- [x] No app crashes or ANRs

#### Recovery
- [x] Stream recovers from CPU spike
- [x] Recovery within 60 seconds
- [x] No manual intervention required

### FAIL Criteria (ANY failure requires investigation)

- [ ] Memory growth ≥ 40 MB
- [ ] Any buffer overflow/underflow events
- [ ] Audio quality degradation
- [ ] Latency > 100ms at any point
- [ ] MediaCodec or AudioRecord errors
- [ ] App crash or ANR
- [ ] Recovery failure
- [ ] Inconsistent results between devices

## Common Issues and Solutions

### Issue: Device overheats during 4-hour test

**Solution:**
- Move to cooler environment
- Remove device case
- Reduce resolution/bitrate temporarily
- Use external cooling (fan)

### Issue: RTMP server disconnects before 4 hours

**Solution:**
- Use more stable RTMP server
- Switch to WiFi if using mobile data
- Check server-side logs
- Consider local Docker RTMP server

### Issue: Memory growth exceeds limits

**Solution:**
- Capture heap dump for leak analysis
- Document leak evidence
- Create GitHub issue with heap dump
- DO NOT mark test as PASS
- Investigation required

### Issue: One device PASS, one FAIL

**Analysis needed:**
- Compare device specifications (model, Android version, audio hardware)
- Check for device-specific issues
- Review sample rate detection logs
- Both devices must PASS for overall PASS

## Test Artifacts

### What to Collect

**For each device:**
- Baseline and final memory dumps
- Heap dump (for leak analysis)
- Complete logcat (4 hours)
- Memory samples CSV
- Latency samples CSV
- Buffer health samples CSV
- Android Studio Profiler screenshots
- Audio recordings (if quality issues detected)

### Where to Store

```
test-results/4hour-stability/
├── device-A_[timestamp]/
│   ├── logcat-full.log
│   ├── memory-samples.csv
│   ├── latency-samples.csv
│   ├── buffer-health-samples.csv
│   ├── meminfo-baseline-full.txt
│   ├── meminfo-final-full.txt
│   ├── heap-4hour.hprof
│   └── result.txt
└── device-B_[timestamp]/
    ├── [same structure as device-A]
    └── ...
```

## Post-Test Actions

### If PASS (Both Devices)

1. ✅ Complete test report with all evidence
2. ✅ Archive test artifacts
3. ✅ Update implementation plan:
   ```bash
   # Edit: .auto-claude/specs/003-long-session-audio-stability/implementation_plan.json
   # Set subtask-5-4 status to "completed"
   ```
4. ✅ Commit test results:
   ```bash
   git add test-results/ docs/testing/test-report-4hour-stability.md
   git commit -m "test: 4-hour stability test PASS on Device A (Samsung) and Device B (Pixel)"
   ```
5. ✅ Proceed to QA sign-off

### If FAIL (Any Device)

1. ❌ Complete test report documenting ALL failures
2. ❌ Create GitHub issues for each failure category
3. ❌ Attach logs, heap dumps, and evidence to issues
4. ❌ **DO NOT** mark subtask as completed
5. ❌ **DO NOT** proceed to QA sign-off
6. ❌ Investigate root cause
7. ❌ Implement fixes
8. ❌ Re-run COMPLETE 4-hour test after fixes

## Success Metrics

### Feature Acceptance

**This test is CRITICAL for feature acceptance.**

The Long Session Audio Stability feature **CANNOT** be considered complete until:
- ✅ 4-hour test PASSES on 2+ different physical devices
- ✅ All acceptance criteria met on both devices
- ✅ Test report completed and reviewed
- ✅ QA sign-off obtained

### Impact

**If this test fails:**
- Feature is **NOT** ready for release
- Professional streamers will experience audio degradation
- App crashes may occur after 25-40 minutes
- Memory leaks will cause system instability
- Audio/video sync will drift over time

**If this test passes:**
- Professional streamers can reliably stream for 4+ hours
- Zero audio distortion or quality degradation
- Memory remains stable throughout sessions
- Automatic recovery from transient failures
- Consistent behavior across different devices

## Related Documentation

### Test Documentation
- **Test Guide**: `docs/testing/4-hour-extended-stability-test-guide.md`
- **Test Report**: `docs/testing/test-report-4hour-stability.md`
- **Monitoring Script**: `scripts/test-4hour-stability.sh`

### Previous Tests
- **30-Minute Stability**: `docs/testing/30-minute-stability-test-guide.md`
- **Hardware Sample Rate**: `docs/testing/hardware-sample-rate-test-guide.md`
- **Latency Compliance**: `docs/testing/audio-latency-compliance-test-guide.md`

### Spec and Planning
- **Spec**: `.auto-claude/specs/003-long-session-audio-stability/spec.md`
- **Implementation Plan**: `.auto-claude/specs/003-long-session-audio-stability/implementation_plan.json`
- **Build Progress**: `.auto-claude/specs/003-long-session-audio-stability/build-progress.txt`

## Questions?

### Before Starting
- Review the detailed test guide: `docs/testing/4-hour-extended-stability-test-guide.md`
- Ensure all prerequisites are met
- Verify RTMP server stability
- Schedule uninterrupted 6-hour time block

### During Test
- Monitor terminals for alerts
- Perform periodic quality checks
- Note any anomalies or observations
- Take screenshots at key checkpoints

### After Test
- Fill out complete test report
- Analyze all metrics carefully
- Make clear PASS/FAIL determination
- Follow post-test actions based on result

## Test Checklist

Use this checklist to ensure nothing is missed:

### Pre-Test
- [ ] 2 physical devices available (API 24+)
- [ ] ADB connection verified on both devices
- [ ] Release APK built and installed on both devices
- [ ] RTMP server running and tested (4+ hours uptime)
- [ ] Network stable (WiFi preferred)
- [ ] Devices fully charged or plugged in
- [ ] Devices set to stay awake
- [ ] Logcat buffer increased to 32MB
- [ ] Test environment prepared (quiet, climate-controlled)
- [ ] 6-hour time block scheduled

### During Test
- [ ] Monitoring scripts running on both devices
- [ ] Baseline measurements captured
- [ ] Streaming active on both devices
- [ ] Periodic quality checks performed (every 30 min)
- [ ] CPU spike simulation executed at T=2h and T=2.5h
- [ ] Android Studio Profiler screenshots taken
- [ ] Any anomalies documented

### Post-Test
- [ ] Final measurements captured
- [ ] Monitoring scripts completed
- [ ] All artifacts collected
- [ ] Test report filled out completely
- [ ] PASS/FAIL determination made
- [ ] Evidence archived
- [ ] Post-test actions completed
- [ ] Implementation plan updated (if PASS)
- [ ] GitHub issues created (if FAIL)

---

**Last Updated:** 2026-01-04
**Test Version:** 1.0
**Critical Status:** ⚠️ MUST PASS FOR FEATURE ACCEPTANCE
