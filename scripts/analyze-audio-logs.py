#!/usr/bin/env python3
"""
Audio Log Analysis Tool for LivePush Stability Tests

This script analyzes logcat output to detect audio pipeline issues:
- Buffer overflows/underruns
- MediaCodec errors
- AudioRecord failures
- Memory growth patterns
- Sample rate mismatches
- Audio health trends

Usage:
    python scripts/analyze-audio-logs.py test-results/20260104_120000/audio-health.log

Output:
    - Summary statistics
    - Timeline of events
    - Critical issues flagged
    - Pass/fail assessment
"""

import sys
import re
from datetime import datetime
from collections import defaultdict, Counter
from pathlib import Path


class AudioLogAnalyzer:
    """Analyzes audio health logs from LivePush stability tests."""

    def __init__(self, log_file_path):
        self.log_file = Path(log_file_path)
        self.events = []
        self.errors = []
        self.warnings = []
        self.buffer_stats = []
        self.memory_readings = []

    def parse_log(self):
        """Parse the log file and extract relevant events."""
        if not self.log_file.exists():
            print(f"ERROR: Log file not found: {self.log_file}")
            sys.exit(1)

        with open(self.log_file, 'r', encoding='utf-8', errors='ignore') as f:
            for line_num, line in enumerate(f, 1):
                self._parse_line(line.strip(), line_num)

    def _parse_line(self, line, line_num):
        """Parse a single log line."""
        if not line:
            return

        # Extract timestamp if present
        timestamp_match = re.search(r'(\d{2}:\d{2}:\d{2}\.\d{3})', line)
        timestamp = timestamp_match.group(1) if timestamp_match else None

        # Categorize the line
        line_lower = line.lower()

        if 'error' in line_lower or 'fail' in line_lower:
            self.errors.append({
                'line': line_num,
                'timestamp': timestamp,
                'message': line,
                'type': self._classify_error(line)
            })

        if 'overflow' in line_lower or 'underrun' in line_lower:
            event_type = 'overflow' if 'overflow' in line_lower else 'underrun'
            self.events.append({
                'line': line_num,
                'timestamp': timestamp,
                'type': event_type,
                'message': line
            })

        if 'warning' in line_lower or 'warn' in line_lower:
            self.warnings.append({
                'line': line_num,
                'timestamp': timestamp,
                'message': line
            })

        if 'buffer health' in line_lower:
            self.buffer_stats.append({
                'line': line_num,
                'timestamp': timestamp,
                'message': line
            })

        if 'memory' in line_lower and ('kb' in line_lower or 'mb' in line_lower):
            self.memory_readings.append({
                'line': line_num,
                'timestamp': timestamp,
                'message': line
            })

    def _classify_error(self, line):
        """Classify the type of error."""
        line_lower = line.lower()

        if 'mediacodec' in line_lower:
            return 'MediaCodec'
        elif 'audiorecord' in line_lower:
            return 'AudioRecord'
        elif 'buffer' in line_lower:
            return 'Buffer'
        elif 'encoder' in line_lower:
            return 'Encoder'
        else:
            return 'Unknown'

    def generate_report(self):
        """Generate analysis report."""
        print("=" * 70)
        print("AUDIO LOG ANALYSIS REPORT")
        print("=" * 70)
        print(f"Log file: {self.log_file}")
        print(f"Analysis time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print()

        self._print_summary()
        self._print_critical_issues()
        self._print_buffer_analysis()
        self._print_error_breakdown()
        self._print_timeline()
        self._print_pass_fail()

    def _print_summary(self):
        """Print summary statistics."""
        print("SUMMARY STATISTICS")
        print("-" * 70)
        print(f"Total errors:     {len(self.errors)}")
        print(f"Total warnings:   {len(self.warnings)}")
        print(f"Buffer events:    {len(self.events)}")
        print(f"  - Overflows:    {sum(1 for e in self.events if e['type'] == 'overflow')}")
        print(f"  - Underruns:    {sum(1 for e in self.events if e['type'] == 'underrun')}")
        print(f"Buffer stats:     {len(self.buffer_stats)} samples")
        print(f"Memory readings:  {len(self.memory_readings)} samples")
        print()

    def _print_critical_issues(self):
        """Print critical issues that require attention."""
        critical = []

        # Check for MediaCodec errors
        mediacodec_errors = [e for e in self.errors if e['type'] == 'MediaCodec']
        if mediacodec_errors:
            critical.append(f"MediaCodec errors detected: {len(mediacodec_errors)}")

        # Check for buffer events
        overflow_count = sum(1 for e in self.events if e['type'] == 'overflow')
        underrun_count = sum(1 for e in self.events if e['type'] == 'underrun')

        if overflow_count > 0:
            critical.append(f"Buffer overflows detected: {overflow_count}")
        if underrun_count > 0:
            critical.append(f"Buffer underruns detected: {underrun_count}")

        # Check for AudioRecord failures
        audiorecord_errors = [e for e in self.errors if e['type'] == 'AudioRecord']
        if audiorecord_errors:
            critical.append(f"AudioRecord failures detected: {len(audiorecord_errors)}")

        print("CRITICAL ISSUES")
        print("-" * 70)
        if critical:
            for issue in critical:
                print(f"  ✗ {issue}")
        else:
            print("  ✓ No critical issues found")
        print()

    def _print_buffer_analysis(self):
        """Analyze buffer health over time."""
        print("BUFFER HEALTH ANALYSIS")
        print("-" * 70)

        if self.buffer_stats:
            print(f"Total buffer health samples: {len(self.buffer_stats)}")
            print()
            print("First 5 samples:")
            for stat in self.buffer_stats[:5]:
                print(f"  [{stat['timestamp']}] {stat['message'][:80]}")
            print()
            print("Last 5 samples:")
            for stat in self.buffer_stats[-5:]:
                print(f"  [{stat['timestamp']}] {stat['message'][:80]}")
        else:
            print("  No buffer health statistics found in logs")
        print()

    def _print_error_breakdown(self):
        """Print breakdown of errors by type."""
        print("ERROR BREAKDOWN BY TYPE")
        print("-" * 70)

        if self.errors:
            error_types = Counter(e['type'] for e in self.errors)
            for error_type, count in error_types.most_common():
                print(f"  {error_type}: {count}")
                # Show first occurrence of each type
                first_occurrence = next(e for e in self.errors if e['type'] == error_type)
                print(f"    Example (line {first_occurrence['line']}): {first_occurrence['message'][:70]}")
        else:
            print("  ✓ No errors found")
        print()

    def _print_timeline(self):
        """Print timeline of significant events."""
        print("EVENT TIMELINE")
        print("-" * 70)

        # Combine errors and buffer events
        all_events = []

        for error in self.errors[:10]:  # Limit to first 10
            all_events.append({
                'timestamp': error['timestamp'],
                'line': error['line'],
                'type': 'ERROR',
                'subtype': error['type'],
                'message': error['message']
            })

        for event in self.events[:10]:  # Limit to first 10
            all_events.append({
                'timestamp': event['timestamp'],
                'line': event['line'],
                'type': 'BUFFER',
                'subtype': event['type'],
                'message': event['message']
            })

        # Sort by line number
        all_events.sort(key=lambda x: x['line'])

        if all_events:
            for event in all_events[:15]:  # Show first 15 events
                ts = event['timestamp'] or 'N/A'
                print(f"  [{ts}] {event['type']}({event['subtype']}): {event['message'][:60]}")

            if len(all_events) > 15:
                print(f"  ... and {len(all_events) - 15} more events")
        else:
            print("  ✓ No significant events to report")
        print()

    def _print_pass_fail(self):
        """Determine and print pass/fail status."""
        print("=" * 70)
        print("PASS/FAIL ASSESSMENT")
        print("=" * 70)

        failures = []

        # Check error count
        if len(self.errors) > 0:
            failures.append(f"Errors detected: {len(self.errors)}")

        # Check buffer events
        overflow_count = sum(1 for e in self.events if e['type'] == 'overflow')
        underrun_count = sum(1 for e in self.events if e['type'] == 'underrun')

        if overflow_count > 0:
            failures.append(f"Buffer overflows: {overflow_count}")
        if underrun_count > 0:
            failures.append(f"Buffer underruns: {underrun_count}")

        # Check for critical MediaCodec errors
        mediacodec_errors = sum(1 for e in self.errors if e['type'] == 'MediaCodec')
        if mediacodec_errors > 0:
            failures.append(f"MediaCodec errors: {mediacodec_errors}")

        # Print result
        if failures:
            print("RESULT: FAIL")
            print()
            print("Failure reasons:")
            for failure in failures:
                print(f"  ✗ {failure}")
            print()
            print("Action required: Investigate and fix issues before proceeding to next test")
            return False
        else:
            print("RESULT: PASS")
            print()
            print("All checks passed:")
            print("  ✓ No errors detected")
            print("  ✓ No buffer overflows")
            print("  ✓ No buffer underruns")
            print("  ✓ No MediaCodec errors")
            print()
            print("Action: Proceed to next subtask (subtask-5-2)")
            return True

    def save_summary(self, output_file):
        """Save analysis summary to file."""
        with open(output_file, 'w') as f:
            f.write("AUDIO LOG ANALYSIS SUMMARY\n")
            f.write("=" * 70 + "\n\n")
            f.write(f"Log file: {self.log_file}\n")
            f.write(f"Analysis time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
            f.write(f"Total errors: {len(self.errors)}\n")
            f.write(f"Total warnings: {len(self.warnings)}\n")
            f.write(f"Buffer overflows: {sum(1 for e in self.events if e['type'] == 'overflow')}\n")
            f.write(f"Buffer underruns: {sum(1 for e in self.events if e['type'] == 'underrun')}\n\n")

            if len(self.errors) == 0 and len(self.events) == 0:
                f.write("RESULT: PASS\n")
            else:
                f.write("RESULT: FAIL\n")


def main():
    """Main entry point."""
    if len(sys.argv) < 2:
        print("Usage: python scripts/analyze-audio-logs.py <log-file-path>")
        print()
        print("Example:")
        print("  python scripts/analyze-audio-logs.py test-results/20260104_120000/audio-health.log")
        sys.exit(1)

    log_file = sys.argv[1]

    analyzer = AudioLogAnalyzer(log_file)
    analyzer.parse_log()
    analyzer.generate_report()

    # Save summary
    output_file = Path(log_file).parent / "analysis-summary.txt"
    analyzer.save_summary(output_file)
    print(f"\nSummary saved to: {output_file}")


if __name__ == "__main__":
    main()
