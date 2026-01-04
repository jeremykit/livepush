# Audio Latency Compliance Testing

Quick reference guide for testing audio latency compliance (≤100ms requirement).

## Overview

**Requirement:** Audio latency must stay within 100ms throughout stream duration (Spec Requirement 5)

**Test Duration:** 1 hour minimum

**Acceptance:** Both baseline (T=0) and post-stream (T=1h) measurements ≤100ms

## Quick Start

### Prerequisites Check

```bash
# Verify device connected
adb devices

# Check app installed
adb shell pm list packages | grep com.livepush

# Test dumpsys access
adb shell dumpsys media.audio_flinger | head -20
```

### Run Full Automated Test

```bash
# Start streaming in LivePush app, then:
./scripts/test-audio-latency-compliance.sh
```

This script:
1. Measures baseline latency
2. Monitors for 1 hour
3. Measures post-stream latency
4. Generates comparison report

**Output:** `test_results/latency/compliance_[timestamp]/latency_compliance_report.md`

### Manual Testing (Step-by-Step)

1. **Start stream** in LivePush app (wait 60s to stabilize)

2. **Baseline measurement:**
   ```bash
   ./scripts/measure-audio-latency.sh baseline
   ```

3. **Wait 1 hour** (keep streaming)

4. **Post-stream measurement:**
   ```bash
   ./scripts/measure-audio-latency.sh post_1hour
   ```

5. **Compare results** manually or use report template

## Understanding Results

### Passing Test Example

```
Baseline Latency:   45 ms  ← PASS (≤100ms)
Post-Stream Latency: 47 ms  ← PASS (≤100ms)
Drift:              +2 ms  ← Stable (<20%)
Overall:            PASS ✓
```

### Failing Test Example

```
Baseline Latency:   55 ms  ← PASS (≤100ms)
Post-Stream Latency: 115 ms ← FAIL (>100ms)
Drift:              +60 ms ← Unstable (>20%)
Overall:            FAIL ✗
```

**Action:** Investigate latency degradation (memory leaks, queue buildup)

## Key Metrics

| Metric | Threshold | Interpretation |
|--------|-----------|----------------|
| **Baseline** | ≤100ms | Initial latency at stream start (Required) |
| **Post-Stream** | ≤100ms | Latency after 1 hour (Required) |
| **Drift** | <20% | Stability indicator (Recommended) |
| **Monitoring** | ±10ms | AudioHealthMonitor accuracy (Required) |

## Troubleshooting

### High Baseline Latency (>80ms)

**Possible Causes:**
- Device audio hardware limitations
- Buffer size misconfiguration
- Sample rate mismatch

**Actions:**
1. Check AudioEncoderConfig buffer size calculation
2. Verify sample rate detection (44100 vs 48000 Hz)
3. Test on different device

### Latency Degradation (Drift >20%)

**Possible Causes:**
- Memory leaks
- Audio buffer queue buildup
- MediaCodec buffer not released

**Actions:**
1. Check for memory leaks (dumpsys meminfo)
2. Review BufferReleaseManager integration
3. Monitor AudioHealthMonitor logs

### Cannot Access dumpsys

**Error:**
```
Can't find service: media.audio_flinger
```

**Solutions:**
1. Verify device API level (requires API 24+)
2. Try alternative: `adb shell dumpsys media.audio_policy`
3. Check ADB connection: `adb kill-server && adb start-server`

### Multiple Output Threads

If `dumpsys` shows multiple audio streams, identify LivePush stream by:
- Matching timestamp (stream start time)
- Matching sample rate (44100 or 48000 Hz)
- Matching buffer size (from AudioEncoderConfig)

## Files and Documentation

| File | Purpose |
|------|---------|
| `audio-latency-compliance-test-guide.md` | Complete test procedure with troubleshooting |
| `test-report-latency-compliance.md` | Template for documenting test results |
| `measure-audio-latency.sh` | Script for single latency measurement |
| `test-audio-latency-compliance.sh` | Full automated 1-hour test |

## Integration with Build Progress

### After PASS

1. Update implementation plan:
   ```json
   "subtask-5-3": {
     "status": "completed",
     "notes": "Latency compliance verified: baseline Xms, post-stream Yms"
   }
   ```

2. Update `build-progress.txt`:
   ```
   subtask-5-3 COMPLETED: Audio latency compliance verified (commit [hash])
   - Baseline: X ms
   - Post-stream: Y ms
   - Drift: Z ms (W%)
   - Result: PASS
   ```

3. Commit test results:
   ```bash
   git add test_results/latency/
   git commit -m "auto-claude: subtask-5-3 - Verify audio latency compliance (PASS)"
   ```

4. Proceed to `subtask-5-4` (4-hour extended stability test)

### After FAIL

1. Document failure in `build-progress.txt`
2. Investigate root cause (see Troubleshooting)
3. Implement fixes
4. Re-run test before proceeding

## Related Documentation

- **Spec:** `.auto-claude/specs/003-long-session-audio-stability/spec.md` (Requirement 5)
- **AudioHealthMonitor:** `app/src/main/java/com/livepush/streaming/monitor/AudioHealthMonitor.kt`
- **AudioEncoderConfig:** `app/src/main/java/com/livepush/streaming/encoder/AudioEncoderConfig.kt`
- **Android Audio Latency:** https://source.android.com/devices/audio/latency

## Support

For issues or questions:
1. Review test guide troubleshooting section
2. Check logcat for audio-related errors
3. Verify device compatibility (API 24+, physical device)
4. Test on alternative device to rule out hardware issues

---

**Quick Command Reference:**

```bash
# Full automated test
./scripts/test-audio-latency-compliance.sh

# Single measurement
./scripts/measure-audio-latency.sh [baseline|post_1hour]

# Monitor during test
adb logcat | grep -E "(AudioHealth|AudioLatency)"

# Check results
cat test_results/latency/compliance_*/latency_compliance_report.md
```
