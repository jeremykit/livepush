# Testing Guide: Automatic Network Reconnection

> **Feature:** Automatic RTMP stream reconnection with exponential backoff
> **Version:** 1.0
> **Last Updated:** 2026-01-04

## Overview

This document provides comprehensive testing procedures for the automatic network reconnection feature in LivePush. The feature automatically attempts to restore RTMP streams when network connectivity is lost, using an exponential backoff strategy to minimize server load while maximizing reconnection success.

## Prerequisites

### Environment Setup

1. **Android Device or Emulator**
   - Physical device recommended for realistic network testing
   - Minimum: Android 7.0 (API 24)
   - USB debugging enabled

2. **Development Tools**
   - Android SDK installed with `ANDROID_HOME` environment variable set
   - ADB (Android Debug Bridge) accessible from command line
   - Device connected: verify with `adb devices`

3. **RTMP Test Server**
   - Option 1: Public test server (e.g., Twitch: `rtmp://live.twitch.tv/app/{stream_key}`)
   - Option 2: Local Nginx RTMP module (`rtmp://localhost/live/test`)
   - Option 3: Any RTMP ingest endpoint you have access to

### Build and Install

```bash
# 1. Set Android SDK path (if not already set)
export ANDROID_HOME=/path/to/Android/sdk  # Linux/Mac
# or
set ANDROID_HOME=C:\Users\YourName\AppData\Local\Android\Sdk  # Windows

# 2. Build debug APK
bash gradlew assembleDebug

# 3. Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Test Scenarios

### Test 1: Auto-Reconnect Success

**Objective:** Verify stream automatically reconnects when network is restored

**Steps:**
1. Launch LivePush app
2. Enter RTMP server URL
3. Tap "Start Streaming" button
4. Verify LIVE badge appears (stream is active)
5. **Trigger disconnect:** Toggle airplane mode ON
6. **Observe:** Orange reconnection banner appears within 2 seconds
7. **Verify banner text:** "Reconnecting... Attempt 1/5"
8. **Verify banner components:** Spinning progress indicator + Cancel button
9. Pull down notification shade
10. **Verify notification:** Text shows "正在重连... 尝试 1/5"
11. Wait 2 seconds → observe attempt counter increments to "2/5"
12. Wait 4 more seconds → observe attempt counter increments to "3/5"
13. **Restore network:** Toggle airplane mode OFF
14. **Expected result:** Within 2-8 seconds, stream automatically resumes
15. **Verify:** LIVE badge returns
16. **Verify:** Orange banner disappears
17. **Verify:** Notification reverts to "推流中" (Streaming)

**Logcat Monitoring:**
```bash
adb logcat -c  # Clear previous logs
adb logcat | grep -E "RtmpStreamManager|Reconnection"
```

**Expected Log Output:**
```
RtmpStreamManager: onDisconnect called, starting reconnection
RtmpStreamManager: Reconnection attempt 1/5, delay: 2000ms
RtmpStreamManager: Reconnection attempt 2/5, delay: 4000ms
RtmpStreamManager: Reconnection attempt 3/5, delay: 8000ms
RtmpStreamManager: Reconnection successful, resuming stream
RtmpStreamManager: State = Streaming
```

**Pass Criteria:**
- ✅ Banner appears within 2 seconds of disconnect
- ✅ Attempt counter increments correctly (1/5 → 2/5 → 3/5...)
- ✅ Notification updates during reconnection
- ✅ Stream resumes automatically when network returns
- ✅ Exponential backoff delays are visible in logs (2s, 4s, 8s...)

---

### Test 2: Manual Cancellation

**Objective:** Verify user can manually abort reconnection attempts

**Steps:**
1. Launch LivePush app
2. Start streaming to RTMP server
3. Verify LIVE badge appears
4. Toggle airplane mode ON
5. **Observe:** Orange reconnection banner appears
6. **Verify:** Banner shows "Reconnecting... Attempt 1/5" with "Cancel" button
7. **Action:** Click the "Cancel" button
8. **Expected:** Banner disappears immediately
9. **Expected:** LIVE badge disappears (stream stopped)
10. **Expected:** Camera preview continues to show
11. **Expected:** No more reconnection attempts in logs
12. Check notification shade
13. **Expected:** Notification no longer shows reconnection status

**Logcat Monitoring:**
```bash
adb logcat | grep -E "cancelReconnection|Reconnection"
```

**Expected Log Output:**
```
StreamViewModel: cancelReconnection called
RtmpStreamManager: Reconnection cancelled by user
RtmpStreamManager: Reconnection job cancelled
RtmpStreamManager: State = Previewing
```

**Pass Criteria:**
- ✅ Cancel button is visible and responsive
- ✅ Reconnection stops immediately when clicked
- ✅ Stream does not resume even if network is restored
- ✅ Camera preview remains active (not frozen)
- ✅ No crashes or ANRs

---

### Test 3: Maximum Retries Exhausted

**Objective:** Verify error state after all reconnection attempts fail

**Steps:**
1. Launch LivePush app
2. Start streaming to RTMP server
3. Verify LIVE badge appears
4. **Trigger disconnect:** Toggle airplane mode ON
5. **Important:** Keep airplane mode ON throughout this test
6. **Observe reconnection attempts:**
   - Attempt 1: appears after ~2 seconds
   - Attempt 2: appears after 4 more seconds (6s total elapsed)
   - Attempt 3: appears after 8 more seconds (14s total)
   - Attempt 4: appears after 16 more seconds (30s total)
   - Attempt 5: appears after 30 more seconds (60s total, capped at 30s max delay)
7. **After 5th attempt fails:**
   - **Expected:** Error state appears
   - **Expected:** Error message: "连接丢失" or "Max reconnection attempts reached"
   - **Expected:** Orange banner disappears
   - **Expected:** Stream is stopped
8. **Restore network:** Toggle airplane mode OFF
9. **Expected:** Stream does NOT automatically resume
10. **Expected:** User must manually tap "Start Streaming" to restart

**Logcat Monitoring:**
```bash
adb logcat | grep -E "Reconnection|Error|attempts"
```

**Expected Log Output:**
```
RtmpStreamManager: Reconnection attempt 1/5, delay: 2000ms
RtmpStreamManager: Attempt 1 failed
RtmpStreamManager: Reconnection attempt 2/5, delay: 4000ms
RtmpStreamManager: Attempt 2 failed
RtmpStreamManager: Reconnection attempt 3/5, delay: 8000ms
RtmpStreamManager: Attempt 3 failed
RtmpStreamManager: Reconnection attempt 4/5, delay: 16000ms
RtmpStreamManager: Attempt 4 failed
RtmpStreamManager: Reconnection attempt 5/5, delay: 30000ms
RtmpStreamManager: Attempt 5 failed
RtmpStreamManager: Max reconnection attempts (5) reached
RtmpStreamManager: State = Error(ConnectionLost)
```

**Pass Criteria:**
- ✅ Exactly 5 reconnection attempts (no more, no less)
- ✅ Exponential backoff timing verified: 2s, 4s, 8s, 16s, 30s
- ✅ Error state displayed after 5th failed attempt
- ✅ Stream does not auto-resume when network returns
- ✅ User can manually restart stream afterwards

---

### Test 4: Multiple Rapid Disconnects

**Objective:** Verify reconnection handles rapid network fluctuations gracefully

**Steps:**
1. Launch LivePush app
2. Start streaming to RTMP server
3. **Rapidly toggle airplane mode:** ON → OFF → ON → OFF (4 toggles within 10 seconds)
4. **Observe app behavior:**
   - No crashes or ANRs
   - Reconnection banner appears/disappears accordingly
   - Attempt counter may reset when stream reconnects
5. Let network stabilize (airplane mode OFF)
6. **Expected:** Stream eventually reconnects and continues normally

**Logcat Monitoring:**
```bash
adb logcat | grep -E "Reconnection|Job|onDisconnect|onConnection"
```

**Expected Log Output:**
```
RtmpStreamManager: onDisconnect called, starting reconnection
RtmpStreamManager: Previous reconnection job cancelled
RtmpStreamManager: Reconnection attempt 1/5
RtmpStreamManager: onConnectionSuccess, cancelling reconnection
RtmpStreamManager: onDisconnect called, starting reconnection
RtmpStreamManager: Previous reconnection job cancelled
RtmpStreamManager: Reconnection attempt 1/5
...
```

**Pass Criteria:**
- ✅ Previous reconnection job is properly cancelled before starting new one
- ✅ Attempt counter resets on successful reconnect
- ✅ No memory leaks or coroutine leaks
- ✅ App remains responsive throughout
- ✅ Stream eventually stabilizes

---

### Test 5: Background Reconnection

**Objective:** Verify reconnection continues when app is in background (foreground service)

**Steps:**
1. Launch LivePush app
2. Start streaming to RTMP server
3. Verify LIVE badge appears
4. **Send app to background:** Press Home button
5. Toggle airplane mode ON
6. Pull down notification shade
7. **Expected:** Notification shows "正在重连... 尝试 1/5"
8. Wait 10 seconds
9. **Expected:** Notification updates with incrementing attempt count
10. Toggle airplane mode OFF
11. **Expected:** Within a few seconds, notification shows "推流中"
12. **Reopen app:** Tap app icon or select from recents
13. **Expected:** LIVE badge is active, no orange banner visible

**Logcat Monitoring:**
```bash
adb logcat | grep -E "StreamingService|Reconnection|Notification"
```

**Pass Criteria:**
- ✅ Foreground service keeps reconnection alive in background
- ✅ Notification accurately reflects reconnection state and attempt count
- ✅ Stream resumes successfully while app is backgrounded
- ✅ App state is correct when brought back to foreground

---

### Test 6: Stop Stream During Reconnection

**Objective:** Verify clean shutdown when user stops stream during reconnection

**Steps:**
1. Launch LivePush app
2. Start streaming to RTMP server
3. Toggle airplane mode ON
4. **Observe:** Orange reconnection banner appears
5. **Action:** Tap "Stop Streaming" button (or navigate back)
6. **Expected:** Reconnection job is cancelled immediately
7. **Expected:** Streaming service stops
8. **Expected:** Foreground notification disappears
9. **Expected:** App returns to home screen or preview screen
10. Toggle airplane mode OFF
11. **Expected:** No reconnection attempts occur (stream was stopped)

**Logcat Monitoring:**
```bash
adb logcat | grep -E "stopStream|Reconnection|Service|cleanup"
```

**Expected Log Output:**
```
StreamViewModel: stopStream called
RtmpStreamManager: Stopping stream, cancelling reconnection job
RtmpStreamManager: Reconnection job cancelled
RtmpStreamManager: Cleanup completed
StreamingService: onDestroy called
StreamingService: Service stopped
```

**Pass Criteria:**
- ✅ Reconnection job cancelled immediately on stop
- ✅ Service stops cleanly without errors
- ✅ No reconnection attempts after stream stopped
- ✅ No resource leaks (verify with Android Profiler)

---

## Additional Verification

### UI Verification Checklist

- [ ] **Banner Color:** Orange (#FF9800)
- [ ] **Banner Position:** Top center, below TopAppBar
- [ ] **Progress Indicator:** Circular, 20dp size, 2dp stroke width, white color
- [ ] **Text Format:** "Reconnecting... Attempt X/Y" (white color)
- [ ] **Cancel Button:** White text, clearly visible, responsive to tap
- [ ] **Banner Animation:** Appears/disappears smoothly without flickering
- [ ] **Banner Layout:** Proper padding (12dp), horizontal arrangement

### Notification Verification Checklist

- [ ] **Text During Reconnection:** "正在重连... 尝试 X/Y"
- [ ] **Text After Resume:** "推流中"
- [ ] **Notification Channel:** Uses correct channel ID (consistent)
- [ ] **Notification Icon:** Visible and correct
- [ ] **No Duplicates:** Only one notification visible at a time
- [ ] **Persistent:** Cannot be swiped away during streaming

### State Management Verification

- [ ] `StreamState.Reconnecting(attempt, maxAttempts)` has correct values
- [ ] **Success path:** Streaming → Reconnecting → Streaming
- [ ] **Max retries path:** Streaming → Reconnecting → Error
- [ ] **Cancel path:** Streaming → Reconnecting → Previewing
- [ ] `streamState` Flow properly updates UI components

### Performance Verification

- [ ] **No ANRs:** No "Application Not Responding" dialogs
- [ ] **Smooth Preview:** Camera preview remains fluid during reconnection
- [ ] **Memory:** No memory leaks (check Android Studio Profiler)
- [ ] **Battery:** No excessive battery drain
- [ ] **Coroutines:** All jobs properly cancelled on cleanup

### Regression Testing Checklist

Ensure existing functionality still works:

- [ ] Start stream (normal flow)
- [ ] Stop stream (normal flow)
- [ ] Switch camera (front/back) during streaming
- [ ] Mute/unmute audio during streaming
- [ ] Toggle flash during streaming
- [ ] Device rotation during streaming
- [ ] Settings changes persist across app restarts

### Edge Cases to Test

- [ ] Network loss during initial connection attempt
- [ ] Network loss during camera switch operation
- [ ] Network loss during mute/unmute operation
- [ ] Invalid RTMP URL entered (reconnection should fail gracefully)
- [ ] App killed during reconnection (state should not persist)
- [ ] Device rotated during reconnection (banner should remain visible)
- [ ] Low battery during reconnection
- [ ] Incoming phone call during reconnection

---

## Troubleshooting

### Issue: Banner doesn't appear on disconnect

**Possible Causes:**
- `onDisconnect()` callback not triggered by RTMP library
- State transition not triggering UI recomposition
- `streamState` Flow not being collected properly

**Debug Steps:**
```bash
adb logcat | grep "onDisconnect"
# Should see: "RtmpStreamManager: onDisconnect called"
```

**Solution:** Check RtmpStreamManager's ConnectChecker implementation

---

### Issue: Reconnection doesn't resume stream

**Possible Causes:**
- RTMP server rejecting reconnection attempts
- `rtmpCamera.startStream()` not being called
- Network not fully restored yet
- Stream URL changed or became invalid

**Debug Steps:**
```bash
adb logcat | grep -E "startStream|onConnection"
# Should see: "RtmpStreamManager: Calling rtmpCamera.startStream"
# Followed by: "onConnectionSuccess" or "onConnectionFailed"
```

**Solution:** Verify RTMP server accepts reconnections and URL is valid

---

### Issue: Attempt counter doesn't increment

**Possible Causes:**
- State not updating before each attempt
- UI not observing state changes
- Delay calculation incorrect

**Debug Steps:**
```bash
adb logcat | grep "Reconnecting"
# Should see state updates with incrementing attempt numbers
```

**Solution:** Verify `_streamState.value = StreamState.Reconnecting(attempt, maxAttempts)` is called

---

### Issue: Notification doesn't update

**Possible Causes:**
- StreamingService not observing `streamState` Flow
- `NotificationManager.notify()` not called with same ID
- Notification channel muted by user

**Debug Steps:**
```bash
adb logcat | grep "StreamingService"
# Should see: "updateNotification called with state: Reconnecting"
```

**Solution:** Check StreamingService's streamState observation and notification update logic

---

## Acceptance Criteria

**All of the following must be TRUE for this feature to pass QA:**

- ✅ Auto-reconnection triggers within 2 seconds of network disconnect
- ✅ Exponential backoff timing verified: 2s, 4s, 8s, 16s, 30s (capped)
- ✅ Orange banner displays with correct attempt count format "X/Y"
- ✅ Cancel button stops reconnection immediately
- ✅ Stream resumes automatically when network is restored (before max retries)
- ✅ Error state shown after max retries exhausted
- ✅ Notification updates correctly reflect reconnection status
- ✅ Background reconnection works via foreground service
- ✅ No crashes, ANRs, or memory leaks detected
- ✅ All existing streaming features work without regression

---

## Test Results Template

### Test Execution Summary

| Test Case | Status | Issues Found | Notes |
|-----------|--------|--------------|-------|
| Test 1: Auto-Reconnect Success | ⬜ PASS / ⬜ FAIL | | |
| Test 2: Manual Cancellation | ⬜ PASS / ⬜ FAIL | | |
| Test 3: Max Retries Exhausted | ⬜ PASS / ⬜ FAIL | | |
| Test 4: Multiple Rapid Disconnects | ⬜ PASS / ⬜ FAIL | | |
| Test 5: Background Reconnection | ⬜ PASS / ⬜ FAIL | | |
| Test 6: Stop During Reconnection | ⬜ PASS / ⬜ FAIL | | |

### Device Information

- **Device Model:** ________________
- **Android Version:** ________________
- **Build Variant:** Debug / Release
- **APK Version:** ________________
- **Test Date:** ________________
- **Tester Name:** ________________

### Issues Found

| Issue ID | Severity | Description | Reproduction Steps | Status |
|----------|----------|-------------|-------------------|--------|
| | | | | |

### Overall Assessment

**All Tests Passed:** ⬜ YES / ⬜ NO

**Ready for Production:** ⬜ YES / ⬜ NO (with caveats)

**Caveats / Notes:**
```
[Space for additional observations, recommendations, or conditional approvals]
```

**QA Sign-off:** ________________ **Date:** ________________

---

## References

- **Specification:** `.auto-claude/specs/002-automatic-network-reconnection/spec.md`
- **Implementation Plan:** `.auto-claude/specs/002-automatic-network-reconnection/implementation_plan.json`
- **Architecture Docs:** `docs/architecture.md`
- **PRD:** `docs/prd.md`

---

**Document Version:** 1.0
**Last Updated:** 2026-01-04
**Maintained By:** LivePush Development Team
