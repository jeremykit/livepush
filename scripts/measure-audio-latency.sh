#!/bin/bash
#
# Audio Latency Measurement Script
# Captures audio_flinger dump and extracts latency metrics for LivePush app
#
# Usage:
#   ./measure-audio-latency.sh <measurement_name>
#
# Example:
#   ./measure-audio-latency.sh baseline
#   ./measure-audio-latency.sh post_1hour
#

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_DIR="$PROJECT_ROOT/test_results/latency"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Arguments
MEASUREMENT_NAME="${1:-measurement}"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Output files
RAW_DUMP="$RESULTS_DIR/audio_flinger_${MEASUREMENT_NAME}_${TIMESTAMP}.txt"
PARSED_OUTPUT="$RESULTS_DIR/latency_${MEASUREMENT_NAME}_${TIMESTAMP}.txt"
JSON_OUTPUT="$RESULTS_DIR/latency_${MEASUREMENT_NAME}_${TIMESTAMP}.json"

echo -e "${BLUE}=== Audio Latency Measurement Tool ===${NC}"
echo "Measurement: $MEASUREMENT_NAME"
echo "Timestamp: $TIMESTAMP"
echo ""

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

# Check ADB
if ! command -v adb &> /dev/null; then
    echo -e "${RED}ERROR: ADB not found. Please install Android SDK platform-tools.${NC}"
    exit 1
fi

# Check device connection
DEVICE_COUNT=$(adb devices | grep -v "List of devices" | grep -c "device$" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${RED}ERROR: No Android device connected.${NC}"
    echo "Connect device and enable USB debugging."
    exit 1
elif [ "$DEVICE_COUNT" -gt 1 ]; then
    echo -e "${RED}ERROR: Multiple devices connected.${NC}"
    echo "Please specify device with: export ANDROID_SERIAL=<device_id>"
    exit 1
fi

DEVICE_MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
DEVICE_SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo -e "${GREEN}✓${NC} Device: $DEVICE_MODEL (API $DEVICE_SDK)"

# Check if LivePush is running
if ! adb shell ps | grep -q com.livepush; then
    echo -e "${YELLOW}WARNING: LivePush app not running.${NC}"
    echo "Start streaming in the app before measuring latency."
    read -p "Press Enter when streaming has started, or Ctrl+C to cancel..."
fi

echo ""
echo -e "${YELLOW}Capturing audio_flinger dump...${NC}"

# Capture audio_flinger dump
if ! adb shell dumpsys media.audio_flinger > "$RAW_DUMP" 2>&1; then
    echo -e "${RED}ERROR: Failed to capture audio_flinger dump.${NC}"
    echo "This may require root access on some devices."

    # Try alternative method
    echo "Trying alternative: media.audio_policy"
    if adb shell dumpsys media.audio_policy > "$RAW_DUMP" 2>&1; then
        echo -e "${GREEN}✓${NC} Captured using media.audio_policy"
    else
        echo -e "${RED}ERROR: Cannot access audio system dumps.${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}✓${NC} Raw dump saved: $RAW_DUMP"
echo ""

# Parse latency information
echo -e "${YELLOW}Parsing latency metrics...${NC}"

# Extract device info
DEVICE_INFO=$(cat <<EOF
Device Information:
  Model: $DEVICE_MODEL
  Android API: $DEVICE_SDK
  Measurement: $MEASUREMENT_NAME
  Timestamp: $(date -Iseconds)
EOF
)

# Parse output threads (where latency info is)
OUTPUT_THREADS=$(grep -n "^Output thread" "$RAW_DUMP" || echo "")

if [ -z "$OUTPUT_THREADS" ]; then
    echo -e "${RED}ERROR: No output threads found in audio_flinger dump.${NC}"
    echo "The device may not be actively playing audio."
    exit 1
fi

echo -e "${GREEN}✓${NC} Found output threads"

# Parse each output thread
THREAD_COUNT=0
LATENCY_VALUES=()

while IFS= read -r thread_line; do
    THREAD_COUNT=$((THREAD_COUNT + 1))
    LINE_NUM=$(echo "$thread_line" | cut -d: -f1)

    # Extract ~100 lines after the thread declaration
    THREAD_BLOCK=$(sed -n "${LINE_NUM},$((LINE_NUM + 100))p" "$RAW_DUMP")

    # Extract key metrics
    LATENCY=$(echo "$THREAD_BLOCK" | grep -i "latency" | head -1 | grep -oP '\d+' | head -1 || echo "0")
    SAMPLE_RATE=$(echo "$THREAD_BLOCK" | grep -i "sample rate" | grep -oP '\d+' | head -1 || echo "0")
    BUFFER_SIZE=$(echo "$THREAD_BLOCK" | grep -i "buffer.*frames" | grep -oP '\d+' | head -1 || echo "0")
    FORMAT=$(echo "$THREAD_BLOCK" | grep -i "format" | head -1 || echo "unknown")

    # Calculate latency if not directly reported
    if [ "$LATENCY" -eq 0 ] && [ "$BUFFER_SIZE" -gt 0 ] && [ "$SAMPLE_RATE" -gt 0 ]; then
        LATENCY=$(awk "BEGIN {printf \"%.1f\", ($BUFFER_SIZE / $SAMPLE_RATE) * 1000}")
    fi

    LATENCY_VALUES+=("$LATENCY")

    echo ""
    echo "Output Thread $THREAD_COUNT:"
    echo "  Latency: ${LATENCY} ms"
    echo "  Sample Rate: ${SAMPLE_RATE} Hz"
    echo "  Buffer Size: ${BUFFER_SIZE} frames"
    echo "  Format: ${FORMAT}"

done <<< "$OUTPUT_THREADS"

# Find the primary (active) stream
# Heuristic: Highest sample rate (44100 or 48000)
PRIMARY_LATENCY=0
for latency_val in "${LATENCY_VALUES[@]}"; do
    if (( $(echo "$latency_val > $PRIMARY_LATENCY" | bc -l) )); then
        PRIMARY_LATENCY=$latency_val
    fi
done

echo ""
echo -e "${BLUE}=== Summary ===${NC}"
echo "Primary Stream Latency: ${PRIMARY_LATENCY} ms"

# Evaluate against threshold
THRESHOLD=100
if (( $(echo "$PRIMARY_LATENCY <= $THRESHOLD" | bc -l) )); then
    echo -e "Status: ${GREEN}PASS${NC} (≤${THRESHOLD}ms)"
    RESULT="PASS"
else
    echo -e "Status: ${RED}FAIL${NC} (>${THRESHOLD}ms)"
    RESULT="FAIL"
fi

# Save parsed output
cat > "$PARSED_OUTPUT" <<EOF
$DEVICE_INFO

Latency Measurement Results
============================

Primary Stream Latency: ${PRIMARY_LATENCY} ms
Threshold: ${THRESHOLD} ms
Result: $RESULT

All Output Threads:
$(echo "${LATENCY_VALUES[@]}" | tr ' ' '\n' | nl)

Raw dump location: $RAW_DUMP
EOF

echo ""
echo -e "${GREEN}✓${NC} Parsed output saved: $PARSED_OUTPUT"

# Save as JSON for automation
cat > "$JSON_OUTPUT" <<EOF
{
  "measurement_name": "$MEASUREMENT_NAME",
  "timestamp": "$(date -Iseconds)",
  "device": {
    "model": "$DEVICE_MODEL",
    "api_level": $DEVICE_SDK
  },
  "latency": {
    "primary_ms": $PRIMARY_LATENCY,
    "all_threads": [$(IFS=,; echo "${LATENCY_VALUES[*]}")],
    "threshold_ms": $THRESHOLD,
    "result": "$RESULT"
  },
  "files": {
    "raw_dump": "$RAW_DUMP",
    "parsed_output": "$PARSED_OUTPUT"
  }
}
EOF

echo -e "${GREEN}✓${NC} JSON output saved: $JSON_OUTPUT"
echo ""

# Final result
if [ "$RESULT" = "PASS" ]; then
    echo -e "${GREEN}✓ MEASUREMENT COMPLETE - PASS${NC}"
    exit 0
else
    echo -e "${RED}✗ MEASUREMENT COMPLETE - FAIL${NC}"
    echo "Latency exceeds ${THRESHOLD}ms threshold."
    exit 1
fi
