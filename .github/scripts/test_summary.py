#!/usr/bin/env python3
"""Writes a Markdown summary of a CI run's test results to $GITHUB_STEP_SUMMARY (stdout when unset).

Usage: test_summary.py <gametest-report.xml> <gradle.log> [junit-results-dir]

The game test server writes its JUnit-style report only when it finishes cleanly, so when it crashes the report is
missing and the summary falls back to the Gradle log: the failed tests it listed, or the crash and the batch it was in.
Failed tests are also printed as workflow commands, which GitHub shows as annotations on the run.
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

MAX_ANNOTATIONS = 10
SLOWEST = 10


def read_cases(path):
    """Yields (name, seconds, status, message) for every test case of a JUnit-style file."""
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError):
        return
    for case in root.iter("testcase"):
        status, message = "passed", ""
        for tag, label in (("failure", "failed"), ("error", "failed"), ("skipped", "skipped")):
            found = case.find(tag)
            if found is not None:
                status = label
                message = (found.get("message") or found.text or "").strip()
                break
        try:
            seconds = float(case.get("time") or 0)
        except ValueError:
            seconds = 0.0
        yield case.get("name") or "?", seconds, status, message


def log_failures(log):
    """The failed tests the game test server listed in the log, for a run that wrote no report."""
    pattern = re.compile(r"\[minecraft/GameTestServer\]:\s+- (\S+): (.*)")
    return [(m.group(1), m.group(2).strip()) for m in map(pattern.search, log) if m]


def crash_note(log):
    if not any("Game test server crashed" in line for line in log):
        return None
    error = next((line.strip() for line in log if re.match(r"\s*java\.lang\.\w+(Error|Exception)", line)), "")
    batch = next((m.group(1) for m in map(re.compile(r"Running test environment '([^']+)'").search, reversed(log)) if m), "")
    note = "The game test server crashed before it could write a report."
    if batch:
        note += f" It was running the `{batch}` batch."
    if error:
        note += f"\n\n```\n{error[:400]}\n```"
    return note


def cell(text):
    return text.replace("|", "\\|").replace("\n", " ")[:300]


def section(title, cases):
    passed = [c for c in cases if c[2] == "passed"]
    failed = [c for c in cases if c[2] == "failed"]
    skipped = [c for c in cases if c[2] == "skipped"]
    total = sum(c[1] for c in cases)
    out = [f"## {title}", "", f"**{len(passed)} passed**, **{len(failed)} failed**, {len(skipped)} skipped, {len(cases)} in all"
           + (f" ({total:.0f} s of test time)" if total else ""), ""]
    if failed:
        out += ["### Failed", "", "| Test | Message |", "| --- | --- |"]
        out += [f"| `{name}` | {cell(message)} |" for name, _, _, message in failed]
        out.append("")
    if skipped:
        out += ["<details><summary>Skipped (optional tests that failed)</summary>", "", "| Test | Message |", "| --- | --- |"]
        out += [f"| `{name}` | {cell(message)} |" for name, _, _, message in skipped]
        out += ["", "</details>", ""]
    slowest = sorted(cases, key=lambda c: -c[1])[:SLOWEST]
    if slowest and slowest[0][1] > 0:
        out += [f"<details><summary>Slowest {len(slowest)} tests</summary>", "", "| Test | Seconds |", "| --- | --- |"]
        out += [f"| `{name}` | {seconds:.1f} |" for name, seconds, _, _ in slowest]
        out += ["", "</details>", ""]
    return out


def annotate(failures):
    for name, message in failures[:MAX_ANNOTATIONS]:
        text = f"{name}: {message}".replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print(f"::error title=Test failed::{text}")
    if len(failures) > MAX_ANNOTATIONS:
        print(f"::notice title=More failed tests::{len(failures) - MAX_ANNOTATIONS} more failed tests are in the job summary")


def main():
    report = sys.argv[1] if len(sys.argv) > 1 else "build/test-report.xml"
    log_path = sys.argv[2] if len(sys.argv) > 2 else "gradle.log"
    junit_dir = sys.argv[3] if len(sys.argv) > 3 else "build/test-results/test"
    log = open(log_path, errors="replace").read().splitlines() if os.path.exists(log_path) else []

    out, failures = [], []
    game = list(read_cases(report)) if os.path.exists(report) else []
    if game:
        out += section("GameTests", game)
        failures += [(name, message) for name, _, status, message in game if status == "failed"]
    else:
        out += ["## GameTests", ""]
        note = crash_note(log)
        listed = log_failures(log)
        if note:
            out += [note, ""]
        if listed:
            out += ["### Failed (from the log)", "", "| Test | Message |", "| --- | --- |"]
            out += [f"| `{name}` | {cell(message)} |" for name, message in listed]
            out.append("")
            failures += listed
        if not note and not listed:
            out += ["No GameTest report was written, and the log lists no failed test (the build may have failed before the tests ran).", ""]
        if note:
            failures.append(("game test server", "crashed before it wrote a report"))

    unit = [c for path in sorted(glob.glob(os.path.join(junit_dir, "*.xml"))) for c in read_cases(path)]
    if unit:
        out += section("Unit tests", unit)
        failures += [(name, message) for name, _, status, message in unit if status == "failed"]

    text = "\n".join(out) + "\n"
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a") as file:
            file.write(text)
    else:
        print(text)
    annotate(failures)


if __name__ == "__main__":
    main()
