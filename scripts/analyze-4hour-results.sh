#!/bin/bash

# 4-Hour Stability Test Results Analyzer
# Usage: ./scripts/analyze-4hour-results.sh <test-results-dir>
# Example: ./scripts/analyze-4hour-results.sh test-results/4hour-stability/device-A_20260104_120000

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
RESULTS_DIR=${1:-}
MEMORY_LIMIT_MB=40
LATENCY_LIMIT_MS=100
GROWTH_RATE_LIMIT_MB_PER_HOUR=10.0

# Usage check
if [ -z "${RESULTS_DIR}" ]; then
    echo -e "${RED}ERROR: Test results directory required${NC}"
    echo "Usage: $0 <test-results-dir>"
    echo ""
    echo "Example:"
    echo "  $0 test-results/4hour-stability/device-A_20260104_120000"
    exit 1
fi

if [ ! -d "${RESULTS_DIR}" ]; then
    echo -e "${RED}ERROR: Directory not found: ${RESULTS_DIR}${NC}"
    exit 1
fi

echo "========================================"
echo "4-Hour Stability Test Results Analyzer"
echo "========================================"
echo "Results Directory: ${RESULTS_DIR}"
echo ""

# Check for required files
REQUIRED_FILES=(
    "memory-baseline.txt"
    "memory-final.txt"
    "memory-samples.csv"
    "logcat-full.log"
)

echo "Checking for required files..."
MISSING_FILES=0
for file in "${REQUIRED_FILES[@]}"; do
    if [ ! -f "${RESULTS_DIR}/${file}" ]; then
        echo -e "${RED}✗ Missing: ${file}${NC}"
        MISSING_FILES=$((MISSING_FILES + 1))
    else
        echo -e "${GREEN}✓ Found: ${file}${NC}"
    fi
done

if [ "${MISSING_FILES}" -gt 0 ]; then
    echo -e "${RED}ERROR: Missing ${MISSING_FILES} required file(s)${NC}"
    echo "Cannot proceed with analysis."
    exit 1
fi

echo -e "${GREEN}All required files present${NC}"
echo ""

# Create analysis output directory
ANALYSIS_DIR="${RESULTS_DIR}/analysis"
mkdir -p "${ANALYSIS_DIR}"

echo "========================================"
echo "Memory Analysis"
echo "========================================"

# Memory metrics
BASELINE_MEMORY=$(cat "${RESULTS_DIR}/memory-baseline.txt")
FINAL_MEMORY=$(cat "${RESULTS_DIR}/memory-final.txt")
MEMORY_GROWTH=$((FINAL_MEMORY - BASELINE_MEMORY))
MEMORY_GROWTH_MB=$(echo "scale=2; ${MEMORY_GROWTH}/1024" | bc)

echo "Baseline Memory: ${BASELINE_MEMORY} KB ($(echo "scale=2; ${BASELINE_MEMORY}/1024" | bc) MB)"
echo "Final Memory: ${FINAL_MEMORY} KB ($(echo "scale=2; ${FINAL_MEMORY}/1024" | bc) MB)"
echo "Memory Growth: ${MEMORY_GROWTH} KB (${MEMORY_GROWTH_MB} MB)"
echo "Limit: ${MEMORY_LIMIT_MB} MB"

# Calculate average growth rate
if [ -f "${RESULTS_DIR}/memory-samples.csv" ]; then
    # Extract last rate from CSV (excluding header)
    LAST_RATE=$(tail -1 "${RESULTS_DIR}/memory-samples.csv" | cut -d',' -f8)
    if [ -n "${LAST_RATE}" ] && [ "${LAST_RATE}" != "Rate_MB_per_Hour" ]; then
        echo "Average Growth Rate: ${LAST_RATE} MB/hour"
        RATE_LIMIT_CHECK=$(echo "${LAST_RATE} <= ${GROWTH_RATE_LIMIT_MB_PER_HOUR}" | bc -l)
        if [ "${RATE_LIMIT_CHECK}" = "1" ]; then
            echo -e "${GREEN}✓ Growth rate within limit${NC}"
        else
            echo -e "${RED}✗ Growth rate exceeds ${GROWTH_RATE_LIMIT_MB_PER_HOUR} MB/hour${NC}"
        fi
    fi
fi

# Memory PASS/FAIL
MEMORY_GROWTH_MB_INT=$(echo "${MEMORY_GROWTH_MB}" | awk '{print int($1+0.5)}')
if [ "${MEMORY_GROWTH_MB_INT}" -le "${MEMORY_LIMIT_MB}" ]; then
    echo -e "${GREEN}✓ PASS: Memory growth within ${MEMORY_LIMIT_MB} MB limit${NC}"
    MEMORY_STATUS="PASS"
else
    echo -e "${RED}✗ FAIL: Memory growth ${MEMORY_GROWTH_MB} MB exceeds ${MEMORY_LIMIT_MB} MB limit${NC}"
    MEMORY_STATUS="FAIL"
fi

# Generate memory growth chart (text-based)
echo ""
echo "Memory Growth Timeline:"
if [ -f "${RESULTS_DIR}/memory-samples.csv" ]; then
    awk -F',' 'NR>1 {
        elapsed=$2
        delta_mb=$7
        # Create bar chart (each # = 2 MB)
        bars=int(delta_mb/2)
        if (bars < 0) bars=0
        bar=""
        for(i=0; i<bars; i++) bar=bar"#"
        printf "  %3dm: [%-20s] %5.1f MB\n", elapsed, bar, delta_mb
    }' "${RESULTS_DIR}/memory-samples.csv" | head -20
    echo "  ..."
    awk -F',' 'NR>1 {
        elapsed=$2
        delta_mb=$7
        bars=int(delta_mb/2)
        if (bars < 0) bars=0
        bar=""
        for(i=0; i<bars; i++) bar=bar"#"
        printf "  %3dm: [%-20s] %5.1f MB\n", elapsed, bar, delta_mb
    }' "${RESULTS_DIR}/memory-samples.csv" | tail -5
fi

echo ""

# Save memory analysis
cat > "${ANALYSIS_DIR}/memory-analysis.txt" <<EOF
Memory Analysis Report
Generated: $(date '+%Y-%m-%d %H:%M:%S')

Baseline Memory: ${BASELINE_MEMORY} KB ($(echo "scale=2; ${BASELINE_MEMORY}/1024" | bc) MB)
Final Memory: ${FINAL_MEMORY} KB ($(echo "scale=2; ${FINAL_MEMORY}/1024" | bc) MB)
Memory Growth: ${MEMORY_GROWTH} KB (${MEMORY_GROWTH_MB} MB)
Limit: ${MEMORY_LIMIT_MB} MB
Status: ${MEMORY_STATUS}
EOF

echo "========================================"
echo "Buffer Health Analysis"
echo "========================================"

# Count buffer events
OVERFLOW_COUNT=$(grep -ci "overflow" "${RESULTS_DIR}/logcat-full.log" 2>/dev/null || echo "0")
UNDERRUN_COUNT=$(grep -ci "underrun" "${RESULTS_DIR}/logcat-full.log" 2>/dev/null || echo "0")
BUFFER_ERRORS=$((OVERFLOW_COUNT + UNDERRUN_COUNT))

echo "Buffer Overflows: ${OVERFLOW_COUNT}"
echo "Buffer Underruns: ${UNDERRUN_COUNT}"
echo "Total Buffer Errors: ${BUFFER_ERRORS}"

# Buffer health PASS/FAIL
if [ "${BUFFER_ERRORS}" -eq 0 ]; then
    echo -e "${GREEN}✓ PASS: Zero buffer errors${NC}"
    BUFFER_STATUS="PASS"
else
    echo -e "${RED}✗ FAIL: ${BUFFER_ERRORS} buffer error(s) detected${NC}"
    BUFFER_STATUS="FAIL"

    # Extract buffer error lines for investigation
    echo ""
    echo "Buffer Error Details:"
    grep -i "overflow\|underrun" "${RESULTS_DIR}/logcat-full.log" | head -10 > "${ANALYSIS_DIR}/buffer-errors.txt"
    head -10 "${ANALYSIS_DIR}/buffer-errors.txt"
    if [ "$(wc -l < "${ANALYSIS_DIR}/buffer-errors.txt")" -gt 10 ]; then
        echo "  ... (see ${ANALYSIS_DIR}/buffer-errors.txt for full list)"
    fi
fi

echo ""

# Save buffer analysis
cat > "${ANALYSIS_DIR}/buffer-analysis.txt" <<EOF
Buffer Health Analysis Report
Generated: $(date '+%Y-%m-%d %H:%M:%S')

Buffer Overflows: ${OVERFLOW_COUNT}
Buffer Underruns: ${UNDERRUN_COUNT}
Total Buffer Errors: ${BUFFER_ERRORS}
Expected: 0
Status: ${BUFFER_STATUS}
EOF

echo "========================================"
echo "Error Analysis"
echo "========================================"

# Count various error types
MEDIACODEC_ERRORS=$(grep -ci "mediacodec.*error" "${RESULTS_DIR}/logcat-full.log" 2>/dev/null || echo "0")
AUDIORECORD_FAILURES=$(grep -ci "audiorecord.*fail" "${RESULTS_DIR}/logcat-full.log" 2>/dev/null || echo "0")
CRITICAL_ERRORS=$(grep -ci "crash\|fatal\|exception" "${RESULTS_DIR}/logcat-full.log" 2>/dev/null || echo "0")
TOTAL_ERRORS=$((MEDIACODEC_ERRORS + AUDIORECORD_FAILURES + CRITICAL_ERRORS))

echo "MediaCodec Errors: ${MEDIACODEC_ERRORS}"
echo "AudioRecord Failures: ${AUDIORECORD_FAILURES}"
echo "Critical Errors: ${CRITICAL_ERRORS}"
echo "Total Errors: ${TOTAL_ERRORS}"

# Error PASS/FAIL
if [ "${TOTAL_ERRORS}" -eq 0 ]; then
    echo -e "${GREEN}✓ PASS: Zero errors detected${NC}"
    ERROR_STATUS="PASS"
else
    echo -e "${RED}✗ FAIL: ${TOTAL_ERRORS} error(s) detected${NC}"
    ERROR_STATUS="FAIL"

    # Extract error details
    echo ""
    echo "Error Details:"
    grep -iE "mediacodec.*error|audiorecord.*fail|crash|fatal|exception" "${RESULTS_DIR}/logcat-full.log" | head -10 > "${ANALYSIS_DIR}/error-details.txt"
    head -10 "${ANALYSIS_DIR}/error-details.txt"
    if [ "$(wc -l < "${ANALYSIS_DIR}/error-details.txt")" -gt 10 ]; then
        echo "  ... (see ${ANALYSIS_DIR}/error-details.txt for full list)"
    fi
fi

echo ""

# Save error analysis
cat > "${ANALYSIS_DIR}/error-analysis.txt" <<EOF
Error Analysis Report
Generated: $(date '+%Y-%m-%d %H:%M:%S')

MediaCodec Errors: ${MEDIACODEC_ERRORS}
AudioRecord Failures: ${AUDIORECORD_FAILURES}
Critical Errors: ${CRITICAL_ERRORS}
Total Errors: ${TOTAL_ERRORS}
Expected: 0
Status: ${ERROR_STATUS}
EOF

echo "========================================"
echo "Latency Analysis"
echo "========================================"

if [ -f "${RESULTS_DIR}/latency-samples.csv" ]; then
    # Parse latency samples
    LATENCY_COUNT=$(awk -F',' 'NR>1 {print $3}' "${RESULTS_DIR}/latency-samples.csv" | grep -c "[0-9]" || echo "0")

    if [ "${LATENCY_COUNT}" -gt 0 ]; then
        MAX_LATENCY=$(awk -F',' 'NR>1 {if($3>max || max=="") max=$3} END {print max}' "${RESULTS_DIR}/latency-samples.csv")
        MIN_LATENCY=$(awk -F',' 'NR>1 {if($3<min || min=="") min=$3} END {print min}' "${RESULTS_DIR}/latency-samples.csv")
        AVG_LATENCY=$(awk -F',' 'NR>1 {sum+=$3; count++} END {if(count>0) print sum/count; else print 0}' "${RESULTS_DIR}/latency-samples.csv")

        # Count FAIL samples
        LATENCY_FAILS=$(awk -F',' -v limit="${LATENCY_LIMIT_MS}" 'NR>1 && $3 > limit {count++} END {print count+0}' "${RESULTS_DIR}/latency-samples.csv")

        echo "Latency Samples: ${LATENCY_COUNT}"
        echo "Maximum Latency: ${MAX_LATENCY} ms"
        echo "Minimum Latency: ${MIN_LATENCY} ms"
        echo "Average Latency: $(printf "%.1f" "${AVG_LATENCY}") ms"
        echo "Limit: ${LATENCY_LIMIT_MS} ms"
        echo "Samples Exceeding Limit: ${LATENCY_FAILS}"

        # Latency PASS/FAIL
        if [ "${LATENCY_FAILS}" -eq 0 ] && (( $(echo "${MAX_LATENCY} <= ${LATENCY_LIMIT_MS}" | bc -l) )); then
            echo -e "${GREEN}✓ PASS: All latency measurements ≤ ${LATENCY_LIMIT_MS}ms${NC}"
            LATENCY_STATUS="PASS"
        else
            echo -e "${RED}✗ FAIL: ${LATENCY_FAILS} measurement(s) exceed ${LATENCY_LIMIT_MS}ms limit${NC}"
            LATENCY_STATUS="FAIL"
        fi

        # Latency timeline
        echo ""
        echo "Latency Timeline:"
        awk -F',' 'NR>1 {
            elapsed=$2
            latency=$3
            status=$4
            printf "  %3dm: %3d ms [%s]\n", elapsed, latency, status
        }' "${RESULTS_DIR}/latency-samples.csv"
    else
        echo -e "${YELLOW}WARNING: No latency samples found${NC}"
        LATENCY_STATUS="UNKNOWN"
    fi

    # Save latency analysis
    cat > "${ANALYSIS_DIR}/latency-analysis.txt" <<EOF
Latency Analysis Report
Generated: $(date '+%Y-%m-%d %H:%M:%S')

Latency Samples: ${LATENCY_COUNT}
Maximum Latency: ${MAX_LATENCY} ms
Minimum Latency: ${MIN_LATENCY} ms
Average Latency: $(printf "%.1f" "${AVG_LATENCY}") ms
Limit: ${LATENCY_LIMIT_MS} ms
Samples Exceeding Limit: ${LATENCY_FAILS}
Status: ${LATENCY_STATUS}
EOF
else
    echo -e "${YELLOW}WARNING: latency-samples.csv not found${NC}"
    LATENCY_STATUS="UNKNOWN"
fi

echo ""

echo "========================================"
echo "Recovery Analysis"
echo "========================================"

# Check for recovery events
RECOVERY_COUNT=$(grep -ci "recovery\|recovered" "${RESULTS_DIR}/logcat-full.log" 2>/dev/null || echo "0")
CPU_SPIKE_COUNT=$(grep -c "CPU SPIKE DETECTED" "${RESULTS_DIR}/cpu-spikes.log" 2>/dev/null || echo "0")

echo "CPU Spikes Detected: ${CPU_SPIKE_COUNT}"
echo "Recovery Events: ${RECOVERY_COUNT}"

if [ "${RECOVERY_COUNT}" -gt 0 ]; then
    echo -e "${GREEN}✓ Recovery mechanism activated${NC}"
    echo ""
    echo "Recovery Event Details:"
    grep -i "recovery\|recovered" "${RESULTS_DIR}/logcat-full.log" | head -5 > "${ANALYSIS_DIR}/recovery-events.txt"
    cat "${ANALYSIS_DIR}/recovery-events.txt"
    RECOVERY_STATUS="OBSERVED"
else
    echo -e "${CYAN}No recovery events detected${NC}"
    RECOVERY_STATUS="NOT_TESTED"
fi

echo ""

# Save recovery analysis
cat > "${ANALYSIS_DIR}/recovery-analysis.txt" <<EOF
Recovery Analysis Report
Generated: $(date '+%Y-%m-%d %H:%M:%S')

CPU Spikes Detected: ${CPU_SPIKE_COUNT}
Recovery Events: ${RECOVERY_COUNT}
Status: ${RECOVERY_STATUS}
EOF

echo "========================================"
echo "Overall Test Result"
echo "========================================"

# Determine overall PASS/FAIL
OVERALL_PASS=true
FAIL_REASONS=()

if [ "${MEMORY_STATUS}" = "FAIL" ]; then
    OVERALL_PASS=false
    FAIL_REASONS+=("Memory growth exceeded limit")
fi

if [ "${BUFFER_STATUS}" = "FAIL" ]; then
    OVERALL_PASS=false
    FAIL_REASONS+=("Buffer errors detected")
fi

if [ "${ERROR_STATUS}" = "FAIL" ]; then
    OVERALL_PASS=false
    FAIL_REASONS+=("Errors detected in logs")
fi

if [ "${LATENCY_STATUS}" = "FAIL" ]; then
    OVERALL_PASS=false
    FAIL_REASONS+=("Latency exceeded limit")
fi

# Summary
echo "Test Results Summary:"
echo "  Memory: ${MEMORY_STATUS}"
echo "  Buffer Health: ${BUFFER_STATUS}"
echo "  Error-Free: ${ERROR_STATUS}"
echo "  Latency: ${LATENCY_STATUS}"
echo "  Recovery: ${RECOVERY_STATUS}"
echo ""

if [ "${OVERALL_PASS}" = true ]; then
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}OVERALL RESULT: PASS${NC}"
    echo -e "${GREEN}========================================${NC}"
    OVERALL_RESULT="PASS"
    EXIT_CODE=0
else
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}OVERALL RESULT: FAIL${NC}"
    echo -e "${RED}========================================${NC}"
    echo ""
    echo "Failure Reasons:"
    for reason in "${FAIL_REASONS[@]}"; do
        echo -e "${RED}  ✗ ${reason}${NC}"
    done
    OVERALL_RESULT="FAIL"
    EXIT_CODE=1
fi

echo ""

# Generate summary report
cat > "${ANALYSIS_DIR}/summary-report.txt" <<EOF
4-Hour Extended Stability Test Analysis Summary
Generated: $(date '+%Y-%m-%d %H:%M:%S')
Results Directory: ${RESULTS_DIR}

============================================
OVERALL RESULT: ${OVERALL_RESULT}
============================================

Memory Analysis:
  Baseline: ${BASELINE_MEMORY} KB
  Final: ${FINAL_MEMORY} KB
  Growth: ${MEMORY_GROWTH_MB} MB
  Limit: ${MEMORY_LIMIT_MB} MB
  Status: ${MEMORY_STATUS}

Buffer Health Analysis:
  Overflows: ${OVERFLOW_COUNT}
  Underruns: ${UNDERRUN_COUNT}
  Total Errors: ${BUFFER_ERRORS}
  Status: ${BUFFER_STATUS}

Error Analysis:
  MediaCodec Errors: ${MEDIACODEC_ERRORS}
  AudioRecord Failures: ${AUDIORECORD_FAILURES}
  Critical Errors: ${CRITICAL_ERRORS}
  Total Errors: ${TOTAL_ERRORS}
  Status: ${ERROR_STATUS}

Latency Analysis:
  Status: ${LATENCY_STATUS}
$([ -n "${MAX_LATENCY}" ] && echo "  Max Latency: ${MAX_LATENCY} ms")
$([ -n "${AVG_LATENCY}" ] && echo "  Avg Latency: $(printf "%.1f" "${AVG_LATENCY}") ms")

Recovery Analysis:
  CPU Spikes: ${CPU_SPIKE_COUNT}
  Recovery Events: ${RECOVERY_COUNT}
  Status: ${RECOVERY_STATUS}

$(if [ "${OVERALL_PASS}" = false ]; then
    echo "Failure Reasons:"
    for reason in "${FAIL_REASONS[@]}"; do
        echo "  - ${reason}"
    done
fi)

Analysis files generated in: ${ANALYSIS_DIR}
EOF

echo "Analysis complete. Files saved to:"
echo "  ${ANALYSIS_DIR}/summary-report.txt"
echo "  ${ANALYSIS_DIR}/memory-analysis.txt"
echo "  ${ANALYSIS_DIR}/buffer-analysis.txt"
echo "  ${ANALYSIS_DIR}/error-analysis.txt"
[ -f "${ANALYSIS_DIR}/latency-analysis.txt" ] && echo "  ${ANALYSIS_DIR}/latency-analysis.txt"
[ -f "${ANALYSIS_DIR}/recovery-analysis.txt" ] && echo "  ${ANALYSIS_DIR}/recovery-analysis.txt"
echo ""

if [ "${OVERALL_PASS}" = true ]; then
    echo -e "${GREEN}✓ Test PASSED. Review summary report and update test report template.${NC}"
else
    echo -e "${RED}✗ Test FAILED. Review analysis files for details and create GitHub issues.${NC}"
fi

echo ""

exit ${EXIT_CODE}
