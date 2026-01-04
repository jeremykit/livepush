#!/bin/bash

# Hardware Sample Rate Detection Test Script for LivePush
# Usage: ./scripts/test-sample-rate-detection.sh [device-id]
# Example: ./scripts/test-sample-rate-detection.sh emulator-5554
# If no device-id specified, uses the first connected device

set -e

# Configuration
DEVICE_ID=${1:-}
PACKAGE_NAME="com.livepush"
TEST_DURATION=90  # seconds (1.5 minutes to capture initialization)
LOG_DIR="./test-results/sample-rate-detection"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "========================================="
echo "Hardware Sample Rate Detection Test"
echo "========================================="
echo ""

# Create log directories
mkdir -p "${LOG_DIR}/device-logs"
mkdir -p "${LOG_DIR}/reports"

# Check ADB connection
echo "Checking ADB connection..."
DEVICE_COUNT=$(adb devices | grep -c "device$" || echo "0")
if [ "${DEVICE_COUNT}" -eq "0" ]; then
    echo -e "${RED}ERROR: No device connected via ADB${NC}"
    echo "Please connect a device and enable USB debugging"
    exit 1
fi

# Auto-detect device if not specified
if [ -z "${DEVICE_ID}" ]; then
    DEVICE_ID=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
    echo -e "${YELLOW}No device specified, using: ${DEVICE_ID}${NC}"
fi

# Define ADB command with device selector
if [ -n "${DEVICE_ID}" ]; then
    ADB="adb -s ${DEVICE_ID}"
else
    ADB="adb"
fi

echo -e "${GREEN}✓ Device: ${DEVICE_ID}${NC}"
echo ""

# Get device information
echo "Gathering device information..."
DEVICE_MODEL=$(${ADB} shell getprop ro.product.model | tr -d '\r')
DEVICE_MANUFACTURER=$(${ADB} shell getprop ro.product.manufacturer | tr -d '\r')
ANDROID_VERSION=$(${ADB} shell getprop ro.build.version.release | tr -d '\r')
API_LEVEL=$(${ADB} shell getprop ro.build.version.sdk | tr -d '\r')
DEVICE_NAME="${DEVICE_MANUFACTURER} ${DEVICE_MODEL}"

# Create device-specific log file prefix
DEVICE_LOG_PREFIX="${LOG_DIR}/device-logs/${DEVICE_ID}_${TIMESTAMP}"

# Save device info
cat > "${DEVICE_LOG_PREFIX}_device-info.txt" <<EOF
Device ID: ${DEVICE_ID}
Manufacturer: ${DEVICE_MANUFACTURER}
Model: ${DEVICE_MODEL}
Android Version: ${ANDROID_VERSION}
API Level: ${API_LEVEL}
Test Timestamp: ${TIMESTAMP}
EOF

echo "Device Information:"
echo "  Manufacturer: ${DEVICE_MANUFACTURER}"
echo "  Model: ${DEVICE_MODEL}"
echo "  Android: ${ANDROID_VERSION} (API ${API_LEVEL})"
echo ""

# Expected sample rate based on device
EXPECTED_RATE="Unknown"
if [[ "${DEVICE_MANUFACTURER}" == "samsung" ]]; then
    EXPECTED_RATE="48000 Hz (Samsung typical)"
elif [[ "${DEVICE_MANUFACTURER}" == "Google" ]]; then
    EXPECTED_RATE="44100 Hz (Pixel typical)"
elif [ "${API_LEVEL}" -lt 24 ]; then
    EXPECTED_RATE="44100 Hz (API <24 fallback)"
else
    EXPECTED_RATE="44100 or 48000 Hz (hardware dependent)"
fi

echo "Expected sample rate: ${EXPECTED_RATE}"
echo ""

# Check if app is installed
echo "Checking if ${PACKAGE_NAME} is installed..."
if ! ${ADB} shell pm list packages | grep -q "${PACKAGE_NAME}"; then
    echo -e "${RED}ERROR: ${PACKAGE_NAME} not installed on device${NC}"
    echo "Please run: adb -s ${DEVICE_ID} install app/build/outputs/apk/debug/app-debug.apk"
    exit 1
fi
echo -e "${GREEN}✓ App installed${NC}"
echo ""

# Clear previous logs
echo "Clearing logcat buffer..."
${ADB} logcat -c
echo -e "${GREEN}✓ Logcat cleared${NC}"
echo ""

# Start logcat capture in background
echo "Starting logcat capture..."
(
    ${ADB} logcat \
        RtmpStreamManager:* AudioCaptureManager:* AudioEncoderConfig:* \
        RtmpCamera:* AudioRecord:* MediaCodec:* AudioBuffer:* AudioHealth:* \
        *:E \
        > "${DEVICE_LOG_PREFIX}_full-logcat.log" 2>&1
) &
LOGCAT_PID=$!

echo -e "${GREEN}✓ Logcat capture started (PID: ${LOGCAT_PID})${NC}"
echo ""

# Launch app
echo "Launching ${PACKAGE_NAME}..."
${ADB} shell am start -n com.livepush/.MainActivity >/dev/null 2>&1
sleep 3
echo -e "${GREEN}✓ App launched${NC}"
echo ""

echo "========================================="
echo "MANUAL STEPS REQUIRED:"
echo "========================================="
echo "1. Grant Camera and Microphone permissions if prompted"
echo "2. Enter RTMP URL (e.g., rtmp://localhost/live/test)"
echo "3. Tap 'Start Stream' button"
echo ""
echo "This script will monitor for ${TEST_DURATION} seconds..."
echo "Press Ctrl+C to stop early if needed"
echo ""

# Monitor for sample rate detection
echo "Monitoring for sample rate detection..."
echo ""

START_TIME=$(date +%s)
END_TIME=$((START_TIME + TEST_DURATION))
SAMPLE_RATE_DETECTED=false
DETECTED_RATE=""
BUFFER_CONFIG=""

# Progress monitoring
while [ "$(date +%s)" -lt "${END_TIME}" ]; do
    ELAPSED=$(($(date +%s) - START_TIME))
    REMAINING=$((TEST_DURATION - ELAPSED))

    printf "\r${BLUE}[%02ds elapsed, %02ds remaining]${NC} Monitoring...  " "${ELAPSED}" "${REMAINING}"

    # Check for sample rate detection in logs
    if [ "${SAMPLE_RATE_DETECTED}" = false ]; then
        # Check for hardware detection (API 24+)
        if grep -q "Hardware sample rate detected:" "${DEVICE_LOG_PREFIX}_full-logcat.log" 2>/dev/null; then
            DETECTED_RATE=$(grep "Hardware sample rate detected:" "${DEVICE_LOG_PREFIX}_full-logcat.log" | tail -1 | awk -F: '{print $NF}' | tr -d ' ')
            SAMPLE_RATE_DETECTED=true
            echo ""
            echo -e "${GREEN}✓ Hardware sample rate detected: ${DETECTED_RATE}${NC}"
        # Check for fallback (API <24)
        elif grep -q "Using configured sample rate:" "${DEVICE_LOG_PREFIX}_full-logcat.log" 2>/dev/null; then
            DETECTED_RATE=$(grep "Using configured sample rate:" "${DEVICE_LOG_PREFIX}_full-logcat.log" | tail -1 | awk -F: '{print $3}' | awk '{print $1}')
            SAMPLE_RATE_DETECTED=true
            echo ""
            echo -e "${YELLOW}✓ Using configured sample rate (API <24 fallback): ${DETECTED_RATE}${NC}"
        fi

        # Check for buffer configuration
        if grep -q "Buffer size calculated:" "${DEVICE_LOG_PREFIX}_full-logcat.log" 2>/dev/null; then
            BUFFER_CONFIG=$(grep "Buffer size calculated:" "${DEVICE_LOG_PREFIX}_full-logcat.log" | tail -1 | awk -F: '{print $NF}')
            echo -e "${GREEN}✓ Buffer configured:${BUFFER_CONFIG}${NC}"
        fi
    fi

    sleep 2
done

echo ""
echo ""

# Stop logcat capture
echo "Stopping logcat capture..."
kill ${LOGCAT_PID} 2>/dev/null || true
wait ${LOGCAT_PID} 2>/dev/null || true
echo -e "${GREEN}✓ Logcat capture stopped${NC}"
echo ""

# Analysis
echo "========================================="
echo "Analyzing Results"
echo "========================================="
echo ""

# Extract key log sections
echo "Extracting sample rate detection logs..."
grep -E "sample rate|SampleRate|prepareAudio|AudioConfig" "${DEVICE_LOG_PREFIX}_full-logcat.log" \
    > "${DEVICE_LOG_PREFIX}_sample-rate-detection.log" 2>/dev/null || echo "No sample rate logs found"

echo "Checking for buffer warnings..."
grep -iE "buffer.*mismatch|overflow|underrun|error|fail" "${DEVICE_LOG_PREFIX}_full-logcat.log" \
    > "${DEVICE_LOG_PREFIX}_buffer-warnings.log" 2>/dev/null || echo "No buffer warnings"

echo "Extracting audio configuration..."
grep -E "Audio prepared|prepareAudio|Buffer size calculated" "${DEVICE_LOG_PREFIX}_full-logcat.log" \
    > "${DEVICE_LOG_PREFIX}_audio-config.log" 2>/dev/null || echo "No audio config logs"

# Count issues
WARNING_COUNT=$(wc -l < "${DEVICE_LOG_PREFIX}_buffer-warnings.log" 2>/dev/null || echo "0")
ERROR_COUNT=$(grep -ic "error" "${DEVICE_LOG_PREFIX}_full-logcat.log" 2>/dev/null || echo "0")

echo ""
echo "========================================="
echo "Test Results Summary"
echo "========================================="
echo "Device: ${DEVICE_NAME}"
echo "API Level: ${API_LEVEL}"
echo "Expected Rate: ${EXPECTED_RATE}"
echo "Detected Rate: ${DETECTED_RATE:-NOT DETECTED}"
echo "Buffer Config: ${BUFFER_CONFIG:-NOT DETECTED}"
echo ""
echo "Issues Found:"
echo "  - Buffer warnings: ${WARNING_COUNT}"
echo "  - Errors: ${ERROR_COUNT}"
echo ""

# Determine pass/fail
PASS=true
FAIL_REASONS=()

if [ "${SAMPLE_RATE_DETECTED}" = false ]; then
    FAIL_REASONS+=("Sample rate not detected in logs")
    PASS=false
fi

if [ "${WARNING_COUNT}" -gt 0 ]; then
    FAIL_REASONS+=("Buffer warnings found (${WARNING_COUNT})")
    PASS=false
fi

if [ "${ERROR_COUNT}" -gt 5 ]; then
    # Allow up to 5 errors (some non-critical system errors are common)
    FAIL_REASONS+=("Excessive errors found (${ERROR_COUNT})")
    PASS=false
fi

# Validate detected rate matches expectations for known devices
if [ "${SAMPLE_RATE_DETECTED}" = true ]; then
    if [[ "${DEVICE_MANUFACTURER}" == "samsung" ]] && [ "${DETECTED_RATE}" != "48000" ]; then
        FAIL_REASONS+=("Unexpected rate for Samsung (expected 48000, got ${DETECTED_RATE})")
        echo -e "${YELLOW}⚠️  Note: This Samsung device uses ${DETECTED_RATE} Hz instead of typical 48000 Hz${NC}"
    fi

    if [[ "${DEVICE_MANUFACTURER}" == "Google" ]] && [ "${DETECTED_RATE}" != "44100" ]; then
        FAIL_REASONS+=("Unexpected rate for Pixel (expected 44100, got ${DETECTED_RATE})")
        echo -e "${YELLOW}⚠️  Note: This Pixel device uses ${DETECTED_RATE} Hz instead of typical 44100 Hz${NC}"
    fi

    if [ "${API_LEVEL}" -lt 24 ] && [ "${DETECTED_RATE}" != "44100" ]; then
        FAIL_REASONS+=("API <24 should use 44100 Hz fallback (got ${DETECTED_RATE})")
        PASS=false
    fi
fi

echo "========================================="

if [ "${PASS}" = true ]; then
    echo -e "${GREEN}RESULT: PASS${NC}"
    echo "PASS" > "${DEVICE_LOG_PREFIX}_result.txt"
    echo -e "${GREEN}✓ Sample rate detection working correctly${NC}"
    echo -e "${GREEN}✓ No buffer mismatches${NC}"
    echo -e "${GREEN}✓ Configuration appropriate for device${NC}"
else
    echo -e "${RED}RESULT: FAIL${NC}"
    echo "FAIL" > "${DEVICE_LOG_PREFIX}_result.txt"
    echo ""
    echo "Failure reasons:"
    for reason in "${FAIL_REASONS[@]}"; do
        echo -e "${RED}  ✗ ${reason}${NC}"
    done
fi

echo "========================================="
echo ""

# Generate device report
REPORT_FILE="${LOG_DIR}/reports/${DEVICE_ID}_${TIMESTAMP}_report.txt"
cat > "${REPORT_FILE}" <<EOF
========================================
Hardware Sample Rate Detection Test Report
========================================

Test Date: $(date)
Device: ${DEVICE_NAME}
Device ID: ${DEVICE_ID}
Manufacturer: ${DEVICE_MANUFACTURER}
Model: ${DEVICE_MODEL}
Android Version: ${ANDROID_VERSION}
API Level: ${API_LEVEL}

Expected Sample Rate: ${EXPECTED_RATE}
Detected Sample Rate: ${DETECTED_RATE:-NOT DETECTED}
Buffer Configuration: ${BUFFER_CONFIG:-NOT DETECTED}

Statistics:
- Buffer warnings: ${WARNING_COUNT}
- Errors logged: ${ERROR_COUNT}
- Test duration: ${TEST_DURATION} seconds

Result: $([ "${PASS}" = true ] && echo "PASS" || echo "FAIL")

$(if [ "${PASS}" = false ]; then
    echo "Failure Reasons:"
    for reason in "${FAIL_REASONS[@]}"; do
        echo "  - ${reason}"
    done
fi)

Log Files:
- Full logcat: ${DEVICE_LOG_PREFIX}_full-logcat.log
- Sample rate detection: ${DEVICE_LOG_PREFIX}_sample-rate-detection.log
- Buffer warnings: ${DEVICE_LOG_PREFIX}_buffer-warnings.log
- Audio configuration: ${DEVICE_LOG_PREFIX}_audio-config.log
- Device info: ${DEVICE_LOG_PREFIX}_device-info.txt

========================================
EOF

echo "Report saved to: ${REPORT_FILE}"
echo ""
echo "Log files generated:"
echo "  - ${DEVICE_LOG_PREFIX}_device-info.txt"
echo "  - ${DEVICE_LOG_PREFIX}_full-logcat.log"
echo "  - ${DEVICE_LOG_PREFIX}_sample-rate-detection.log"
echo "  - ${DEVICE_LOG_PREFIX}_buffer-warnings.log"
echo "  - ${DEVICE_LOG_PREFIX}_audio-config.log"
echo "  - ${DEVICE_LOG_PREFIX}_result.txt"
echo ""

if [ "${PASS}" = true ]; then
    echo -e "${GREEN}✓ Test completed successfully for this device${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Test remaining devices using this script"
    echo "  2. Fill out test report: ./docs/testing/test-report-sample-rate-detection.md"
    echo "  3. After all devices pass, proceed to subtask-5-3"
    exit 0
else
    echo -e "${RED}✗ Test failed for this device${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Review log files in: ${DEVICE_LOG_PREFIX}_*.log"
    echo "  2. Check for sample rate detection code issues"
    echo "  3. Fix issues and re-test"
    echo "  4. Do NOT proceed to next subtask until all devices PASS"
    exit 1
fi
