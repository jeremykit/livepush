#!/bin/bash

# 4-Hour Extended Stability Test Script for LivePush
# Usage: ./scripts/test-4hour-stability.sh <device-id> <device-name>
# Example: ./scripts/test-4hour-stability.sh ABC123DEF456 device-A

set -e

# Configuration
DEVICE_ID=${1:-}
DEVICE_NAME=${2:-"device"}
DURATION_HOURS=4
DURATION_MINUTES=$((DURATION_HOURS * 60))
PACKAGE_NAME="com.livepush"
LOG_DIR="./test-results/4hour-stability/${DEVICE_NAME}_$(date +%Y%m%d_%H%M%S)"
MEMORY_SAMPLE_INTERVAL=300  # 5 minutes
LATENCY_SAMPLE_INTERVAL=1800  # 30 minutes
HEALTH_CHECK_INTERVAL=600  # 10 minutes

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Usage check
if [ -z "${DEVICE_ID}" ]; then
    echo -e "${RED}ERROR: Device ID required${NC}"
    echo "Usage: $0 <device-id> <device-name>"
    echo ""
    echo "Example:"
    echo "  $0 ABC123DEF device-A"
    echo ""
    echo "To get device ID, run: adb devices"
    exit 1
fi

echo "========================================"
echo "LivePush 4-Hour Stability Test"
echo "========================================"
echo "Device ID: ${DEVICE_ID}"
echo "Device Name: ${DEVICE_NAME}"
echo "Duration: ${DURATION_HOURS} hours"
echo "Package: ${PACKAGE_NAME}"
echo "Log directory: ${LOG_DIR}"
echo ""

# Create log directory
mkdir -p "${LOG_DIR}"

# Save test configuration
cat > "${LOG_DIR}/test-config.txt" <<EOF
Test: 4-Hour Extended Stability Test
Device ID: ${DEVICE_ID}
Device Name: ${DEVICE_NAME}
Package: ${PACKAGE_NAME}
Duration: ${DURATION_HOURS} hours
Started: $(date '+%Y-%m-%d %H:%M:%S')
Memory Sample Interval: ${MEMORY_SAMPLE_INTERVAL}s
Latency Sample Interval: ${LATENCY_SAMPLE_INTERVAL}s
Health Check Interval: ${HEALTH_CHECK_INTERVAL}s
EOF

# Check ADB connection
echo "Checking ADB connection to device ${DEVICE_ID}..."
if ! adb -s "${DEVICE_ID}" get-state &>/dev/null; then
    echo -e "${RED}ERROR: Device ${DEVICE_ID} not connected${NC}"
    echo "Available devices:"
    adb devices
    exit 1
fi
echo -e "${GREEN}✓ Device connected${NC}"
echo ""

# Get device info
echo "Collecting device information..."
DEVICE_MODEL=$(adb -s "${DEVICE_ID}" shell getprop ro.product.model | tr -d '\r')
ANDROID_VERSION=$(adb -s "${DEVICE_ID}" shell getprop ro.build.version.release | tr -d '\r')
SDK_VERSION=$(adb -s "${DEVICE_ID}" shell getprop ro.build.version.sdk | tr -d '\r')

echo "  Model: ${DEVICE_MODEL}"
echo "  Android: ${ANDROID_VERSION} (API ${SDK_VERSION})"

# Save device info
cat > "${LOG_DIR}/device-info.txt" <<EOF
Model: ${DEVICE_MODEL}
Android Version: ${ANDROID_VERSION}
SDK Version: ${SDK_VERSION}
Device ID: ${DEVICE_ID}
Device Name: ${DEVICE_NAME}
EOF

# Check if app is installed
echo "Checking if ${PACKAGE_NAME} is installed..."
if ! adb -s "${DEVICE_ID}" shell pm list packages | grep -q "${PACKAGE_NAME}"; then
    echo -e "${RED}ERROR: ${PACKAGE_NAME} not installed on device${NC}"
    echo "Please install the app first:"
    echo "  ./gradlew assembleRelease"
    echo "  adb -s ${DEVICE_ID} install -r app/build/outputs/apk/release/app-release.apk"
    exit 1
fi
echo -e "${GREEN}✓ App installed${NC}"
echo ""

# Clear previous logs
echo "Clearing logcat buffer..."
adb -s "${DEVICE_ID}" logcat -c
echo -e "${GREEN}✓ Logcat cleared${NC}"
echo ""

# Increase logcat buffer size for 4-hour test
echo "Increasing logcat buffer size..."
adb -s "${DEVICE_ID}" logcat -G 32M
echo -e "${GREEN}✓ Buffer size increased to 32MB${NC}"
echo ""

# Capture baseline memory
echo "Capturing baseline memory..."
BASELINE_MEMORY=$(adb -s "${DEVICE_ID}" shell dumpsys meminfo "${PACKAGE_NAME}" | grep "TOTAL" | head -1 | awk '{print $2}')
if [ -z "${BASELINE_MEMORY}" ] || [ "${BASELINE_MEMORY}" = "0" ]; then
    echo -e "${YELLOW}WARNING: Could not capture baseline memory. App may not be running yet.${NC}"
    echo -e "${YELLOW}Start streaming in the app, then press Enter to continue...${NC}"
    read -r
    BASELINE_MEMORY=$(adb -s "${DEVICE_ID}" shell dumpsys meminfo "${PACKAGE_NAME}" | grep "TOTAL" | head -1 | awk '{print $2}')
fi
echo "Baseline memory: ${BASELINE_MEMORY} KB ($(echo "scale=2; ${BASELINE_MEMORY}/1024" | bc) MB)"
echo "${BASELINE_MEMORY}" > "${LOG_DIR}/memory-baseline.txt"
adb -s "${DEVICE_ID}" shell dumpsys meminfo "${PACKAGE_NAME}" > "${LOG_DIR}/meminfo-baseline-full.txt"
echo ""

# Capture baseline latency
echo "Capturing baseline audio latency..."
if [ -f "./scripts/measure-audio-latency.sh" ]; then
    ./scripts/measure-audio-latency.sh "${DEVICE_ID}" baseline > "${LOG_DIR}/latency-baseline.txt" 2>&1 || echo "Latency measurement failed"
else
    adb -s "${DEVICE_ID}" shell dumpsys media.audio_flinger > "${LOG_DIR}/latency-baseline.txt" 2>&1 || echo "Manual latency capture"
fi
echo -e "${GREEN}✓ Baseline latency captured${NC}"
echo ""

# Start background monitoring tasks
echo "Starting monitoring tasks..."
echo ""

# Task 1: Comprehensive logcat monitoring
(
    adb -s "${DEVICE_ID}" logcat AudioHealth:* AudioBuffer:* MediaCodec:* AudioRecord:* RtmpCamera:* AudioEncoder:* *:E \
        | tee "${LOG_DIR}/logcat-full.log" \
        | while IFS= read -r line; do
            # Critical errors (red)
            if echo "${line}" | grep -qiE "crash|fatal|exception"; then
                echo -e "${RED}[$(date +%H:%M:%S)] CRITICAL: ${line}${NC}" | tee -a "${LOG_DIR}/critical-alerts.log"
            # Audio buffer errors (red)
            elif echo "${line}" | grep -qiE "overflow|underrun"; then
                echo -e "${RED}[$(date +%H:%M:%S)] BUFFER: ${line}${NC}" | tee -a "${LOG_DIR}/buffer-alerts.log"
            # MediaCodec/AudioRecord errors (yellow)
            elif echo "${line}" | grep -qiE "mediacodec.*error|audiorecord.*fail"; then
                echo -e "${YELLOW}[$(date +%H:%M:%S)] ERROR: ${line}${NC}" | tee -a "${LOG_DIR}/codec-alerts.log"
            # Recovery events (green)
            elif echo "${line}" | grep -qiE "recovery|recovered|resumed"; then
                echo -e "${GREEN}[$(date +%H:%M:%S)] RECOVERY: ${line}${NC}" | tee -a "${LOG_DIR}/recovery-events.log"
            fi
        done
) &
LOGCAT_PID=$!

# Task 2: Memory sampling (every 5 minutes)
(
    echo "Time,Elapsed_Min,Total_KB,Native_KB,Dalvik_KB,Delta_KB,Delta_MB,Rate_MB_per_Hour" > "${LOG_DIR}/memory-samples.csv"

    SAMPLE_COUNT=0
    for i in $(seq 1 $((DURATION_MINUTES * 60 / MEMORY_SAMPLE_INTERVAL))); do
        sleep "${MEMORY_SAMPLE_INTERVAL}"

        TIMESTAMP=$(date +%H:%M:%S)
        ELAPSED_MIN=$(((i * MEMORY_SAMPLE_INTERVAL) / 60))
        MEMINFO=$(adb -s "${DEVICE_ID}" shell dumpsys meminfo "${PACKAGE_NAME}" 2>/dev/null | grep "TOTAL\|Native Heap\|Dalvik Heap" || echo "")

        if [ -z "${MEMINFO}" ]; then
            echo "[${TIMESTAMP}] WARNING: Could not capture memory (app stopped?)" | tee -a "${LOG_DIR}/warnings.log"
            continue
        fi

        TOTAL=$(echo "${MEMINFO}" | grep "TOTAL" | head -1 | awk '{print $2}')
        NATIVE=$(echo "${MEMINFO}" | grep "Native Heap" | awk '{print $2}')
        DALVIK=$(echo "${MEMINFO}" | grep "Dalvik Heap" | awk '{print $2}')
        DELTA=$((TOTAL - BASELINE_MEMORY))
        DELTA_MB=$(echo "scale=2; ${DELTA}/1024" | bc)

        # Calculate growth rate (MB per hour)
        if [ "${ELAPSED_MIN}" -gt 0 ]; then
            RATE_MB_PER_HOUR=$(echo "scale=2; (${DELTA}/1024) / (${ELAPSED_MIN}/60)" | bc)
        else
            RATE_MB_PER_HOUR="0.00"
        fi

        echo "${TIMESTAMP},${ELAPSED_MIN},${TOTAL},${NATIVE},${DALVIK},${DELTA},${DELTA_MB},${RATE_MB_PER_HOUR}" >> "${LOG_DIR}/memory-samples.csv"
        echo -e "${CYAN}[${TIMESTAMP}] Memory: ${TOTAL} KB (Δ ${DELTA_MB} MB, Rate: ${RATE_MB_PER_HOUR} MB/h)${NC}"

        # Alert if growth rate > 10 MB/hour
        if (( $(echo "${RATE_MB_PER_HOUR} > 10.0" | bc -l) )); then
            echo -e "${YELLOW}[${TIMESTAMP}] WARNING: Memory growth rate ${RATE_MB_PER_HOUR} MB/h exceeds 10 MB/h threshold${NC}" | tee -a "${LOG_DIR}/memory-warnings.log"
        fi

        # Alert if absolute growth > 40 MB (max for 4 hours)
        DELTA_MB_INT=$(echo "${DELTA_MB}" | awk '{print int($1+0.5)}')
        if [ "${DELTA_MB_INT}" -gt 40 ]; then
            echo -e "${RED}[${TIMESTAMP}] ALERT: Total memory growth ${DELTA_MB} MB exceeds 40 MB limit${NC}" | tee -a "${LOG_DIR}/memory-alerts.log"
        fi

        SAMPLE_COUNT=$((SAMPLE_COUNT + 1))
    done

    echo "Memory sampling completed. Total samples: ${SAMPLE_COUNT}"
) &
MEMORY_PID=$!

# Task 3: Latency sampling (every 30 minutes)
(
    echo "Time,Elapsed_Min,Latency_MS,Status" > "${LOG_DIR}/latency-samples.csv"

    for i in $(seq 1 $((DURATION_MINUTES * 60 / LATENCY_SAMPLE_INTERVAL))); do
        sleep "${LATENCY_SAMPLE_INTERVAL}"

        TIMESTAMP=$(date +%H:%M:%S)
        ELAPSED_MIN=$(((i * LATENCY_SAMPLE_INTERVAL) / 60))

        # Capture latency
        if [ -f "./scripts/measure-audio-latency.sh" ]; then
            LATENCY_OUTPUT=$(./scripts/measure-audio-latency.sh "${DEVICE_ID}" sample 2>&1 || echo "")
            LATENCY_MS=$(echo "${LATENCY_OUTPUT}" | grep -oP 'Latency: \K[0-9]+' || echo "0")
        else
            LATENCY_MS="0"
        fi

        # Determine status
        if [ "${LATENCY_MS}" -eq 0 ]; then
            STATUS="UNKNOWN"
        elif [ "${LATENCY_MS}" -le 100 ]; then
            STATUS="PASS"
        else
            STATUS="FAIL"
        fi

        echo "${TIMESTAMP},${ELAPSED_MIN},${LATENCY_MS},${STATUS}" >> "${LOG_DIR}/latency-samples.csv"

        if [ "${STATUS}" = "PASS" ]; then
            echo -e "${GREEN}[${TIMESTAMP}] Latency: ${LATENCY_MS} ms (PASS ≤100ms)${NC}"
        elif [ "${STATUS}" = "FAIL" ]; then
            echo -e "${RED}[${TIMESTAMP}] Latency: ${LATENCY_MS} ms (FAIL >100ms)${NC}" | tee -a "${LOG_DIR}/latency-alerts.log"
        else
            echo -e "${YELLOW}[${TIMESTAMP}] Latency: measurement failed${NC}"
        fi
    done

    echo "Latency sampling completed."
) &
LATENCY_PID=$!

# Task 4: Buffer health monitoring (every 10 minutes)
(
    echo "Time,Elapsed_Min,Overflow_Count,Underrun_Count,Error_Count,Health_Score" > "${LOG_DIR}/buffer-health-samples.csv"

    for i in $(seq 1 $((DURATION_MINUTES * 60 / HEALTH_CHECK_INTERVAL))); do
        sleep "${HEALTH_CHECK_INTERVAL}"

        TIMESTAMP=$(date +%H:%M:%S)
        ELAPSED_MIN=$(((i * HEALTH_CHECK_INTERVAL) / 60))

        # Count errors since last check
        OVERFLOW_COUNT=$(adb -s "${DEVICE_ID}" logcat -d -s AudioBuffer:* | grep -ci "overflow" || echo "0")
        UNDERRUN_COUNT=$(adb -s "${DEVICE_ID}" logcat -d -s AudioBuffer:* | grep -ci "underrun" || echo "0")
        ERROR_COUNT=$(adb -s "${DEVICE_ID}" logcat -d -s AudioHealth:* | grep -ci "error" || echo "0")

        # Calculate health score (0.0 to 1.0)
        TOTAL_ISSUES=$((OVERFLOW_COUNT + UNDERRUN_COUNT + ERROR_COUNT))
        if [ "${TOTAL_ISSUES}" -eq 0 ]; then
            HEALTH_SCORE="1.0"
        elif [ "${TOTAL_ISSUES}" -le 5 ]; then
            HEALTH_SCORE="0.9"
        elif [ "${TOTAL_ISSUES}" -le 10 ]; then
            HEALTH_SCORE="0.7"
        else
            HEALTH_SCORE="0.5"
        fi

        echo "${TIMESTAMP},${ELAPSED_MIN},${OVERFLOW_COUNT},${UNDERRUN_COUNT},${ERROR_COUNT},${HEALTH_SCORE}" >> "${LOG_DIR}/buffer-health-samples.csv"
        echo -e "${BLUE}[${TIMESTAMP}] Buffer Health: Score=${HEALTH_SCORE} (Overflow=${OVERFLOW_COUNT}, Underrun=${UNDERRUN_COUNT}, Errors=${ERROR_COUNT})${NC}"

        # Alert if health degraded
        if (( $(echo "${HEALTH_SCORE} < 0.9" | bc -l) )); then
            echo -e "${YELLOW}[${TIMESTAMP}] WARNING: Buffer health degraded (score ${HEALTH_SCORE})${NC}" | tee -a "${LOG_DIR}/health-warnings.log"
        fi
    done

    echo "Buffer health monitoring completed."
) &
HEALTH_PID=$!

# Task 5: CPU spike detection
(
    while true; do
        CPU_USAGE=$(adb -s "${DEVICE_ID}" shell top -n 1 -o %CPU | grep "${PACKAGE_NAME}" | awk '{print $9}' | head -1 || echo "0")

        # Remove % sign if present
        CPU_USAGE=${CPU_USAGE//%/}

        # Alert if CPU > 80%
        if (( $(echo "${CPU_USAGE} > 80.0" | bc -l 2>/dev/null || echo "0") )); then
            TIMESTAMP=$(date +%H:%M:%S)
            echo -e "${MAGENTA}[${TIMESTAMP}] CPU SPIKE DETECTED: ${CPU_USAGE}%${NC}" | tee -a "${LOG_DIR}/cpu-spikes.log"
        fi

        sleep 10
    done
) &
CPU_PID=$!

echo -e "${GREEN}✓ All monitoring tasks started${NC}"
echo ""
echo "Monitoring processes:"
echo "  - Logcat monitor (PID: ${LOGCAT_PID})"
echo "  - Memory sampler (PID: ${MEMORY_PID})"
echo "  - Latency sampler (PID: ${LATENCY_PID})"
echo "  - Health monitor (PID: ${HEALTH_PID})"
echo "  - CPU monitor (PID: ${CPU_PID})"
echo ""
echo "========================================"
echo "TEST IN PROGRESS"
echo "========================================"
echo "Duration: ${DURATION_HOURS} hours (${DURATION_MINUTES} minutes)"
echo "Start time: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "Ensure the stream is running on device!"
echo "Press Ctrl+C to stop early (will generate partial report)"
echo ""

# Progress indicator
START_TIME=$(date +%s)
END_TIME=$((START_TIME + DURATION_MINUTES * 60))

while [ "$(date +%s)" -lt "${END_TIME}" ]; do
    ELAPSED=$(($(date +%s) - START_TIME))
    REMAINING=$((DURATION_MINUTES * 60 - ELAPSED))

    HOURS=$((REMAINING / 3600))
    MINUTES=$(((REMAINING % 3600) / 60))
    SECONDS=$((REMAINING % 60))

    ELAPSED_HOURS=$((ELAPSED / 3600))
    ELAPSED_MINUTES=$(((ELAPSED % 3600) / 60))

    PERCENT=$((ELAPSED * 100 / (DURATION_MINUTES * 60)))

    printf "\r[%02d:%02d:%02d remaining] [%02d:%02d elapsed] [%d%% complete]  " \
        "${HOURS}" "${MINUTES}" "${SECONDS}" \
        "${ELAPSED_HOURS}" "${ELAPSED_MINUTES}" \
        "${PERCENT}"

    sleep 5
done

echo ""
echo ""
echo "========================================"
echo "TEST DURATION COMPLETE"
echo "========================================"
echo "Completed: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# Stop monitoring tasks
echo "Stopping monitoring tasks..."
kill ${LOGCAT_PID} ${MEMORY_PID} ${LATENCY_PID} ${HEALTH_PID} ${CPU_PID} 2>/dev/null || true
wait 2>/dev/null || true
echo -e "${GREEN}✓ All monitoring tasks stopped${NC}"
echo ""

# Collect final metrics
echo "Collecting final metrics..."
echo ""

# Final memory
FINAL_MEMORY=$(adb -s "${DEVICE_ID}" shell dumpsys meminfo "${PACKAGE_NAME}" | grep "TOTAL" | head -1 | awk '{print $2}')
MEMORY_GROWTH=$((FINAL_MEMORY - BASELINE_MEMORY))
MEMORY_GROWTH_MB=$(echo "scale=2; ${MEMORY_GROWTH}/1024" | bc)

echo "Final memory: ${FINAL_MEMORY} KB ($(echo "scale=2; ${FINAL_MEMORY}/1024" | bc) MB)"
echo "Memory growth: ${MEMORY_GROWTH} KB (${MEMORY_GROWTH_MB} MB)"
echo "${FINAL_MEMORY}" > "${LOG_DIR}/memory-final.txt"
echo "${MEMORY_GROWTH}" > "${LOG_DIR}/memory-growth.txt"

# Full memory dump
adb -s "${DEVICE_ID}" shell dumpsys meminfo "${PACKAGE_NAME}" > "${LOG_DIR}/meminfo-final-full.txt"

# Final latency
echo "Capturing final latency..."
if [ -f "./scripts/measure-audio-latency.sh" ]; then
    ./scripts/measure-audio-latency.sh "${DEVICE_ID}" final > "${LOG_DIR}/latency-final.txt" 2>&1 || echo "Latency measurement failed"
else
    adb -s "${DEVICE_ID}" shell dumpsys media.audio_flinger > "${LOG_DIR}/latency-final.txt" 2>&1 || echo "Manual latency capture"
fi

# Count errors
ERROR_COUNT=$(grep -ci "error\|fail" "${LOG_DIR}/logcat-full.log" 2>/dev/null || echo "0")
OVERFLOW_COUNT=$(grep -ci "overflow" "${LOG_DIR}/logcat-full.log" 2>/dev/null || echo "0")
UNDERRUN_COUNT=$(grep -ci "underrun" "${LOG_DIR}/logcat-full.log" 2>/dev/null || echo "0")
MEDIACODEC_ERRORS=$(grep -ci "mediacodec.*error" "${LOG_DIR}/logcat-full.log" 2>/dev/null || echo "0")
AUDIORECORD_FAILURES=$(grep -ci "audiorecord.*fail" "${LOG_DIR}/logcat-full.log" 2>/dev/null || echo "0")
RECOVERY_COUNT=$(grep -ci "recovery\|recovered" "${LOG_DIR}/logcat-full.log" 2>/dev/null || echo "0")

echo ""
echo "========================================"
echo "TEST RESULTS SUMMARY - ${DEVICE_NAME}"
echo "========================================"
echo "Device: ${DEVICE_MODEL} (Android ${ANDROID_VERSION})"
echo "Duration: ${DURATION_HOURS} hours"
echo "Completed: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "Memory Metrics:"
echo "  Baseline: ${BASELINE_MEMORY} KB ($(echo "scale=2; ${BASELINE_MEMORY}/1024" | bc) MB)"
echo "  Final: ${FINAL_MEMORY} KB ($(echo "scale=2; ${FINAL_MEMORY}/1024" | bc) MB)"
echo "  Growth: ${MEMORY_GROWTH} KB (${MEMORY_GROWTH_MB} MB)"
echo "  Limit: 40 MB (10 MB/hour)"
echo ""
echo "Buffer Events:"
echo "  Errors: ${ERROR_COUNT}"
echo "  Overflows: ${OVERFLOW_COUNT}"
echo "  Underruns: ${UNDERRUN_COUNT}"
echo "  MediaCodec Errors: ${MEDIACODEC_ERRORS}"
echo "  AudioRecord Failures: ${AUDIORECORD_FAILURES}"
echo "  Recovery Events: ${RECOVERY_COUNT}"
echo ""

# Determine pass/fail
PASS=true
FAIL_REASONS=()

# Memory check
MEMORY_GROWTH_MB_INT=$(echo "${MEMORY_GROWTH_MB}" | awk '{print int($1+0.5)}')
if [ "${MEMORY_GROWTH_MB_INT}" -gt 40 ]; then
    echo -e "${RED}✗ FAIL: Memory growth ${MEMORY_GROWTH_MB} MB exceeds 40 MB limit${NC}"
    PASS=false
    FAIL_REASONS+=("Memory growth exceeded")
else
    echo -e "${GREEN}✓ PASS: Memory growth within limits${NC}"
fi

# Error checks
if [ "${ERROR_COUNT}" -gt 0 ]; then
    echo -e "${RED}✗ FAIL: ${ERROR_COUNT} errors detected in audio logs${NC}"
    PASS=false
    FAIL_REASONS+=("Errors detected")
else
    echo -e "${GREEN}✓ PASS: No errors in audio logs${NC}"
fi

if [ "${OVERFLOW_COUNT}" -gt 0 ]; then
    echo -e "${RED}✗ FAIL: ${OVERFLOW_COUNT} buffer overflows detected${NC}"
    PASS=false
    FAIL_REASONS+=("Buffer overflows")
else
    echo -e "${GREEN}✓ PASS: No buffer overflows${NC}"
fi

if [ "${UNDERRUN_COUNT}" -gt 0 ]; then
    echo -e "${RED}✗ FAIL: ${UNDERRUN_COUNT} buffer underruns detected${NC}"
    PASS=false
    FAIL_REASONS+=("Buffer underruns")
else
    echo -e "${GREEN}✓ PASS: No buffer underruns${NC}"
fi

if [ "${MEDIACODEC_ERRORS}" -gt 0 ]; then
    echo -e "${RED}✗ FAIL: ${MEDIACODEC_ERRORS} MediaCodec errors detected${NC}"
    PASS=false
    FAIL_REASONS+=("MediaCodec errors")
else
    echo -e "${GREEN}✓ PASS: No MediaCodec errors${NC}"
fi

if [ "${AUDIORECORD_FAILURES}" -gt 0 ]; then
    echo -e "${RED}✗ FAIL: ${AUDIORECORD_FAILURES} AudioRecord failures detected${NC}"
    PASS=false
    FAIL_REASONS+=("AudioRecord failures")
else
    echo -e "${GREEN}✓ PASS: No AudioRecord failures${NC}"
fi

echo ""
echo "========================================"

if [ "${PASS}" = true ]; then
    echo -e "${GREEN}OVERALL RESULT: PASS${NC}"
    echo "PASS" > "${LOG_DIR}/result.txt"
    EXIT_CODE=0
else
    echo -e "${RED}OVERALL RESULT: FAIL${NC}"
    echo "FAIL" > "${LOG_DIR}/result.txt"
    echo ""
    echo "Failure reasons:"
    for reason in "${FAIL_REASONS[@]}"; do
        echo "  - ${reason}"
    done
    EXIT_CODE=1
fi

echo "========================================"
echo ""
echo "Test results saved to: ${LOG_DIR}"
echo ""
echo "Files generated:"
echo "  - logcat-full.log           : Complete logcat output"
echo "  - memory-samples.csv        : Memory usage over time"
echo "  - latency-samples.csv       : Latency measurements"
echo "  - buffer-health-samples.csv : Buffer health metrics"
echo "  - meminfo-final-full.txt    : Final memory dump"
echo "  - result.txt                : Pass/Fail result"
echo ""

if [ "${PASS}" = true ]; then
    echo -e "${GREEN}✓ Test completed successfully${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Fill out test report: docs/testing/test-report-4hour-stability.md"
    echo "2. If both devices PASS, mark subtask-5-4 as completed"
    echo "3. Proceed to QA sign-off"
else
    echo -e "${RED}✗ Test failed${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Review logs in ${LOG_DIR}"
    echo "2. Document failures in test report"
    echo "3. Create GitHub issues for failures"
    echo "4. Fix issues and re-run test"
    echo "5. DO NOT proceed to QA until test passes"
fi

echo ""

exit ${EXIT_CODE}
