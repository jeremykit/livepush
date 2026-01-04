#!/bin/bash
#
# Audio Latency Compliance Test
# Full automated test: baseline measurement -> 1 hour wait -> post-stream measurement
#
# Usage:
#   ./test-audio-latency-compliance.sh
#
# This script:
# 1. Measures baseline latency
# 2. Monitors stream for 1 hour
# 3. Measures post-stream latency
# 4. Generates comparison report
#

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TEST_DURATION_SECONDS=3600  # 1 hour
MONITORING_INTERVAL=300      # 5 minutes

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Audio Latency Compliance Test (1 Hour)            ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

# Check prerequisites
if ! command -v adb &> /dev/null; then
    echo -e "${RED}ERROR: ADB not found${NC}"
    exit 1
fi

if [ "$(adb devices | grep -v "List" | grep -c "device$")" -eq 0 ]; then
    echo -e "${RED}ERROR: No device connected${NC}"
    exit 1
fi

# Prepare
TEST_START=$(date -Iseconds)
RESULTS_DIR="$PROJECT_ROOT/test_results/latency/compliance_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$RESULTS_DIR"
cd "$RESULTS_DIR"

echo "Test Start: $TEST_START"
echo "Results Directory: $RESULTS_DIR"
echo ""

# Prompt user to start stream
echo -e "${YELLOW}═══ Step 1: Start Streaming ═══${NC}"
echo ""
echo "Please ensure:"
echo "  1. LivePush app is open"
echo "  2. RTMP server is configured and stable"
echo "  3. Device is plugged in (prevent sleep)"
echo "  4. Background apps are closed"
echo ""
read -p "Press Enter when streaming has started (stable for 60 seconds)..."
echo ""

# Phase 1: Baseline measurement
echo -e "${CYAN}═══ Phase 1: Baseline Latency Measurement ═══${NC}"
echo ""

if "$SCRIPT_DIR/measure-audio-latency.sh" "baseline"; then
    BASELINE_JSON=$(ls -t "$PROJECT_ROOT/test_results/latency/latency_baseline_"*.json | head -1)
    BASELINE_LATENCY=$(jq -r '.latency.primary_ms' "$BASELINE_JSON")
    echo ""
    echo -e "${GREEN}✓ Baseline: ${BASELINE_LATENCY} ms${NC}"

    if (( $(echo "$BASELINE_LATENCY > 100" | bc -l) )); then
        echo -e "${RED}FAIL: Baseline latency exceeds 100ms threshold${NC}"
        echo "Test cannot continue. Investigate audio configuration."
        exit 1
    fi
else
    echo -e "${RED}ERROR: Baseline measurement failed${NC}"
    exit 1
fi

# Phase 2: Monitoring period
echo ""
echo -e "${CYAN}═══ Phase 2: 1-Hour Monitoring Period ═══${NC}"
echo ""
echo "Monitoring for $TEST_DURATION_SECONDS seconds (1 hour)..."
echo "Checks every $MONITORING_INTERVAL seconds (5 minutes)"
echo ""
echo "You can minimize this terminal. Do NOT:"
echo "  - Disconnect the device"
echo "  - Stop the stream"
echo "  - Put device to sleep"
echo ""

MONITORING_LOG="$RESULTS_DIR/monitoring_log.txt"
echo "Monitoring Log" > "$MONITORING_LOG"
echo "Start Time: $TEST_START" >> "$MONITORING_LOG"
echo "" >> "$MONITORING_LOG"

ELAPSED=0
CHECK_COUNT=0

while [ $ELAPSED -lt $TEST_DURATION_SECONDS ]; do
    REMAINING=$((TEST_DURATION_SECONDS - ELAPSED))
    REMAINING_MIN=$((REMAINING / 60))

    echo -e "${YELLOW}[$(date +%H:%M:%S)] $REMAINING_MIN minutes remaining...${NC}"

    # Check if stream is still running
    if ! adb shell ps | grep -q com.livepush; then
        echo -e "${RED}ERROR: LivePush app stopped!${NC}"
        echo "Test failed. Stream must run continuously for 1 hour."
        exit 1
    fi

    # Log health check
    CHECK_COUNT=$((CHECK_COUNT + 1))
    echo "Check $CHECK_COUNT at T+${ELAPSED}s:" >> "$MONITORING_LOG"
    adb logcat -d -s AudioHealth:I AudioLatency:I | tail -20 >> "$MONITORING_LOG" 2>&1
    echo "" >> "$MONITORING_LOG"

    # Sleep for interval
    sleep $MONITORING_INTERVAL
    ELAPSED=$((ELAPSED + MONITORING_INTERVAL))
done

echo ""
echo -e "${GREEN}✓ Monitoring period complete (1 hour elapsed)${NC}"

# Phase 3: Post-stream measurement
echo ""
echo -e "${CYAN}═══ Phase 3: Post-Stream Latency Measurement ═══${NC}"
echo ""

if "$SCRIPT_DIR/measure-audio-latency.sh" "post_1hour"; then
    POST_JSON=$(ls -t "$PROJECT_ROOT/test_results/latency/latency_post_1hour_"*.json | head -1)
    POST_LATENCY=$(jq -r '.latency.primary_ms' "$POST_JSON")
    echo ""
    echo -e "${GREEN}✓ Post-Stream: ${POST_LATENCY} ms${NC}"
else
    echo -e "${RED}ERROR: Post-stream measurement failed${NC}"
    exit 1
fi

# Phase 4: Analysis
echo ""
echo -e "${CYAN}═══ Phase 4: Analysis & Report ═══${NC}"
echo ""

# Calculate drift
DRIFT=$(awk "BEGIN {printf \"%.1f\", $POST_LATENCY - $BASELINE_LATENCY}")
DRIFT_PERCENT=$(awk "BEGIN {printf \"%.1f\", ($DRIFT / $BASELINE_LATENCY) * 100}")

# Evaluate results
BASELINE_PASS=$([[ $(echo "$BASELINE_LATENCY <= 100" | bc -l) -eq 1 ]] && echo "PASS" || echo "FAIL")
POST_PASS=$([[ $(echo "$POST_LATENCY <= 100" | bc -l) -eq 1 ]] && echo "PASS" || echo "FAIL")
DRIFT_ACCEPTABLE=$([[ $(echo "${DRIFT#-} <= 20" | bc -l) -eq 1 ]] && echo "YES" || echo "NO")

# Overall result
if [ "$BASELINE_PASS" = "PASS" ] && [ "$POST_PASS" = "PASS" ]; then
    OVERALL_RESULT="PASS"
    RESULT_COLOR="$GREEN"
else
    OVERALL_RESULT="FAIL"
    RESULT_COLOR="$RED"
fi

# Generate report
REPORT_FILE="$RESULTS_DIR/latency_compliance_report.md"
cat > "$REPORT_FILE" <<EOF
# Audio Latency Compliance Test Report

**Test Date:** $(date -Iseconds)
**Duration:** 1 hour (3600 seconds)
**Device:** $(adb shell getprop ro.product.model | tr -d '\r')
**Android API:** $(adb shell getprop ro.build.version.sdk | tr -d '\r')

## Results Summary

| Metric | Value | Threshold | Status |
|--------|-------|-----------|--------|
| **Baseline Latency** | ${BASELINE_LATENCY} ms | ≤100 ms | **${BASELINE_PASS}** |
| **Post-Stream Latency** | ${POST_LATENCY} ms | ≤100 ms | **${POST_PASS}** |
| **Latency Drift** | ${DRIFT} ms | ±20 ms | ${DRIFT_ACCEPTABLE} |
| **Drift Percentage** | ${DRIFT_PERCENT}% | <20% | ${DRIFT_ACCEPTABLE} |

## Overall Result: **${OVERALL_RESULT}**

---

## Detailed Measurements

### Baseline (T=0)
- **Timestamp:** $TEST_START
- **Latency:** ${BASELINE_LATENCY} ms
- **Raw Data:** $(basename "$BASELINE_JSON")

### Post-Stream (T=1 hour)
- **Timestamp:** $(date -Iseconds)
- **Latency:** ${POST_LATENCY} ms
- **Raw Data:** $(basename "$POST_JSON")

## Analysis

### Latency Stability
- **Absolute Drift:** ${DRIFT} ms
- **Relative Drift:** ${DRIFT_PERCENT}%
- **Interpretation:** $(if [ "$DRIFT_ACCEPTABLE" = "YES" ]; then echo "Stable - drift within acceptable range"; else echo "Unstable - significant drift detected"; fi)

### Compliance
- **100ms Threshold:** $(if [ "$OVERALL_RESULT" = "PASS" ]; then echo "✓ Both measurements compliant"; else echo "✗ One or both measurements exceed threshold"; fi)
- **Requirement 5:** Audio latency stays within 100ms throughout stream duration
- **Status:** $(if [ "$OVERALL_RESULT" = "PASS" ]; then echo "✓ SATISFIED"; else echo "✗ NOT SATISFIED"; fi)

## Monitoring Log

See: \`monitoring_log.txt\` for detailed health checks during the 1-hour period.

- **Health Checks Performed:** $CHECK_COUNT
- **Interval:** Every $MONITORING_INTERVAL seconds

## Test Artifacts

1. \`latency_baseline_*.json\` - Baseline measurement data
2. \`latency_post_1hour_*.json\` - Post-stream measurement data
3. \`audio_flinger_baseline_*.txt\` - Raw audio_flinger dump (baseline)
4. \`audio_flinger_post_1hour_*.txt\` - Raw audio_flinger dump (post-stream)
5. \`monitoring_log.txt\` - Health monitoring logs
6. \`latency_compliance_report.md\` - This report

## Next Steps

$(if [ "$OVERALL_RESULT" = "PASS" ]; then
    echo "✓ Test passed. Latency compliance verified."
    echo ""
    echo "- Update implementation_plan.json: subtask-5-3 status → completed"
    echo "- Proceed to subtask-5-4 (4-hour extended stability test)"
else
    echo "✗ Test failed. Action required:"
    echo ""
    echo "1. Review AudioHealthMonitor latency tracking"
    echo "2. Check for buffer size misconfiguration"
    echo "3. Investigate memory leaks or queue buildup"
    echo "4. Verify hardware sample rate detection"
    echo "5. Re-test after fixes"
fi)

---

**Generated by:** \`test-audio-latency-compliance.sh\`
**Report Location:** \`$REPORT_FILE\`
EOF

# Display summary
echo ""
echo -e "${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║              Test Complete - Summary                 ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""
echo "  Baseline Latency:   ${BASELINE_LATENCY} ms"
echo "  Post-Stream Latency: ${POST_LATENCY} ms"
echo "  Drift:              ${DRIFT} ms (${DRIFT_PERCENT}%)"
echo ""
echo -e "  Overall Result:     ${RESULT_COLOR}${OVERALL_RESULT}${NC}"
echo ""
echo "Report: $REPORT_FILE"
echo ""

if [ "$OVERALL_RESULT" = "PASS" ]; then
    echo -e "${GREEN}✓ Audio latency compliance verified (≤100ms)${NC}"
    echo ""
    exit 0
else
    echo -e "${RED}✗ Audio latency compliance failed${NC}"
    echo "Review the report for details and corrective actions."
    echo ""
    exit 1
fi
