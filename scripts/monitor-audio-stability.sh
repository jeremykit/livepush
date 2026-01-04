#!/bin/bash

# Audio Stability Monitoring Script for LivePush
# Usage: ./scripts/monitor-audio-stability.sh <duration_minutes>
# Example: ./scripts/monitor-audio-stability.sh 30

set -e

# Configuration
DURATION_MINUTES=${1:-30}
PACKAGE_NAME="com.livepush"
LOG_DIR="./test-results/$(date +%Y%m%d_%H%M%S)"
MEMORY_SAMPLE_INTERVAL=60  # seconds

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================"
echo "LivePush Audio Stability Monitor"
echo "========================================"
echo "Duration: ${DURATION_MINUTES} minutes"
echo "Package: ${PACKAGE_NAME}"
echo "Log directory: ${LOG_DIR}"
echo ""

# Create log directory
mkdir -p "${LOG_DIR}"

# Check ADB connection
echo "Checking ADB connection..."
DEVICE_COUNT=$(adb devices | grep -c "device$" || echo "0")
if [ "${DEVICE_COUNT}" -eq "0" ]; then
    echo -e "${RED}ERROR: No device connected via ADB${NC}"
    echo "Please connect a device and enable USB debugging"
    exit 1
fi
echo -e "${GREEN}✓ Device connected${NC}"
echo ""

# Check if app is installed
echo "Checking if ${PACKAGE_NAME} is installed..."
if ! adb shell pm list packages | grep -q "${PACKAGE_NAME}"; then
    echo -e "${RED}ERROR: ${PACKAGE_NAME} not installed${NC}"
    echo "Please run: ./gradlew installDebug"
    exit 1
fi
echo -e "${GREEN}✓ App installed${NC}"
echo ""

# Clear previous logs
echo "Clearing logcat buffer..."
adb logcat -c
echo -e "${GREEN}✓ Logcat cleared${NC}"
echo ""

# Capture baseline memory
echo "Capturing baseline memory..."
BASELINE_MEMORY=$(adb shell dumpsys meminfo "${PACKAGE_NAME}" | grep "TOTAL" | head -1 | awk '{print $2}')
echo "Baseline memory: ${BASELINE_MEMORY} KB"
echo "${BASELINE_MEMORY}" > "${LOG_DIR}/memory-baseline.txt"
echo ""

# Start background monitoring tasks
echo "Starting monitoring tasks..."

# Task 1: Logcat monitoring for audio errors
(
    adb logcat AudioHealth:* AudioBuffer:* MediaCodec:E AudioRecord:E RtmpCamera:* \
        | tee "${LOG_DIR}/audio-health.log" \
        | while read -r line; do
            if echo "${line}" | grep -qi "overflow\|underrun\|error\|fail"; then
                echo -e "${RED}[$(date +%H:%M:%S)] ALERT: ${line}${NC}" | tee -a "${LOG_DIR}/alerts.log"
            fi
        done
) &
LOGCAT_PID=$!

# Task 2: Memory sampling
(
    echo "Time,Total_KB,Native_KB,Dalvik_KB,Delta_KB" > "${LOG_DIR}/memory-samples.csv"

    for i in $(seq 1 $((DURATION_MINUTES * 60 / MEMORY_SAMPLE_INTERVAL))); do
        sleep "${MEMORY_SAMPLE_INTERVAL}"

        TIMESTAMP=$(date +%H:%M:%S)
        MEMINFO=$(adb shell dumpsys meminfo "${PACKAGE_NAME}" | grep "TOTAL\|Native Heap\|Dalvik Heap")

        TOTAL=$(echo "${MEMINFO}" | grep "TOTAL" | head -1 | awk '{print $2}')
        NATIVE=$(echo "${MEMINFO}" | grep "Native Heap" | awk '{print $2}')
        DALVIK=$(echo "${MEMINFO}" | grep "Dalvik Heap" | awk '{print $2}')
        DELTA=$((TOTAL - BASELINE_MEMORY))

        echo "${TIMESTAMP},${TOTAL},${NATIVE},${DALVIK},${DELTA}" >> "${LOG_DIR}/memory-samples.csv"
        echo "[${TIMESTAMP}] Memory: ${TOTAL} KB (Δ ${DELTA} KB, Native: ${NATIVE} KB, Dalvik: ${DALVIK} KB)"

        # Alert if growth > 10 MB
        if [ "${DELTA}" -gt 10240 ]; then
            echo -e "${YELLOW}[${TIMESTAMP}] WARNING: Memory growth exceeds 10 MB (${DELTA} KB)${NC}" | tee -a "${LOG_DIR}/alerts.log"
        fi
    done
) &
MEMORY_PID=$!

# Task 3: Buffer health stats
(
    sleep 10  # Wait for streaming to start

    while true; do
        BUFFER_STATS=$(adb logcat -d -s AudioHealth:* | grep "Buffer health" | tail -10)
        if [ -n "${BUFFER_STATS}" ]; then
            echo "${BUFFER_STATS}" > "${LOG_DIR}/buffer-health-latest.txt"
        fi
        sleep 30
    done
) &
BUFFER_PID=$!

echo -e "${GREEN}✓ Monitoring started${NC}"
echo ""
echo "Monitoring processes:"
echo "  - Logcat monitor (PID: ${LOGCAT_PID})"
echo "  - Memory sampler (PID: ${MEMORY_PID})"
echo "  - Buffer health (PID: ${BUFFER_PID})"
echo ""
echo "Test will run for ${DURATION_MINUTES} minutes..."
echo "Press Ctrl+C to stop early"
echo ""

# Progress indicator
START_TIME=$(date +%s)
END_TIME=$((START_TIME + DURATION_MINUTES * 60))

while [ "$(date +%s)" -lt "${END_TIME}" ]; do
    ELAPSED=$(($(date +%s) - START_TIME))
    REMAINING=$((DURATION_MINUTES * 60 - ELAPSED))

    MINUTES=$((REMAINING / 60))
    SECONDS=$((REMAINING % 60))

    printf "\r[%02d:%02d remaining] " "${MINUTES}" "${SECONDS}"
    sleep 5
done

echo ""
echo ""
echo "========================================"
echo "Test Duration Complete"
echo "========================================"

# Stop monitoring tasks
echo "Stopping monitoring tasks..."
kill ${LOGCAT_PID} ${MEMORY_PID} ${BUFFER_PID} 2>/dev/null || true
wait 2>/dev/null || true
echo -e "${GREEN}✓ Monitoring stopped${NC}"
echo ""

# Collect final metrics
echo "Collecting final metrics..."

# Final memory
FINAL_MEMORY=$(adb shell dumpsys meminfo "${PACKAGE_NAME}" | grep "TOTAL" | head -1 | awk '{print $2}')
MEMORY_GROWTH=$((FINAL_MEMORY - BASELINE_MEMORY))
echo "Final memory: ${FINAL_MEMORY} KB"
echo "Memory growth: ${MEMORY_GROWTH} KB ($(echo "scale=2; ${MEMORY_GROWTH}/1024" | bc) MB)"
echo "${FINAL_MEMORY}" > "${LOG_DIR}/memory-final.txt"
echo "${MEMORY_GROWTH}" > "${LOG_DIR}/memory-growth.txt"

# Full memory dump
adb shell dumpsys meminfo "${PACKAGE_NAME}" > "${LOG_DIR}/meminfo-full.txt"

# Count errors
ERROR_COUNT=$(grep -ic "error\|fail" "${LOG_DIR}/audio-health.log" 2>/dev/null || echo "0")
OVERFLOW_COUNT=$(grep -ic "overflow" "${LOG_DIR}/audio-health.log" 2>/dev/null || echo "0")
UNDERRUN_COUNT=$(grep -ic "underrun" "${LOG_DIR}/audio-health.log" 2>/dev/null || echo "0")

echo ""
echo "========================================"
echo "Test Results Summary"
echo "========================================"
echo "Duration: ${DURATION_MINUTES} minutes"
echo "Baseline memory: ${BASELINE_MEMORY} KB"
echo "Final memory: ${FINAL_MEMORY} KB"
echo "Memory growth: ${MEMORY_GROWTH} KB ($(echo "scale=2; ${MEMORY_GROWTH}/1024" | bc) MB)"
echo ""
echo "Buffer Events:"
echo "  - Errors: ${ERROR_COUNT}"
echo "  - Overflows: ${OVERFLOW_COUNT}"
echo "  - Underruns: ${UNDERRUN_COUNT}"
echo ""

# Determine pass/fail
PASS=true

if [ "${MEMORY_GROWTH}" -gt 10240 ]; then
    echo -e "${RED}✗ FAIL: Memory growth exceeds 10 MB${NC}"
    PASS=false
else
    echo -e "${GREEN}✓ PASS: Memory growth within limits (<10 MB)${NC}"
fi

if [ "${ERROR_COUNT}" -gt 0 ]; then
    echo -e "${RED}✗ FAIL: Errors detected in audio logs${NC}"
    PASS=false
else
    echo -e "${GREEN}✓ PASS: No errors in audio logs${NC}"
fi

if [ "${OVERFLOW_COUNT}" -gt 0 ]; then
    echo -e "${RED}✗ FAIL: Buffer overflows detected${NC}"
    PASS=false
else
    echo -e "${GREEN}✓ PASS: No buffer overflows${NC}"
fi

if [ "${UNDERRUN_COUNT}" -gt 0 ]; then
    echo -e "${RED}✗ FAIL: Buffer underruns detected${NC}"
    PASS=false
else
    echo -e "${GREEN}✓ PASS: No buffer underruns${NC}"
fi

echo ""
echo "========================================"

if [ "${PASS}" = true ]; then
    echo -e "${GREEN}OVERALL: PASS${NC}"
    echo "PASS" > "${LOG_DIR}/result.txt"
else
    echo -e "${RED}OVERALL: FAIL${NC}"
    echo "FAIL" > "${LOG_DIR}/result.txt"
fi

echo "========================================"
echo ""
echo "Test results saved to: ${LOG_DIR}"
echo ""
echo "Files generated:"
echo "  - audio-health.log       : Full audio health logs"
echo "  - memory-samples.csv     : Memory usage over time"
echo "  - meminfo-full.txt       : Final memory dump"
echo "  - alerts.log             : Critical alerts during test"
echo "  - result.txt             : Pass/Fail result"
echo ""

if [ "${PASS}" = true ]; then
    echo -e "${GREEN}Test completed successfully. Proceed to next subtask.${NC}"
    exit 0
else
    echo -e "${RED}Test failed. Please investigate logs before proceeding.${NC}"
    echo "Review files in ${LOG_DIR} for details."
    exit 1
fi
