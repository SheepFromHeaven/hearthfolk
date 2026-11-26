#!/usr/bin/env bash
# Runs the gametest suite N times (or only the tests matching a pattern) and tallies which tests fail and how often.
# Usage: scripts/flaky.sh [runs=5] [tests-pattern]
# Output: build/flaky/run-<i>.xml, run-<i>.log, and a summary of failures with their first message.
set -u
cd "$(dirname "$0")/.."
runs="${1:-5}"
pattern="${2:-}"
out=build/flaky
mkdir -p "$out"
rm -f "$out"/run-*
for i in $(seq 1 "$runs"); do
  args=(--console=plain -q)
  [ -n "$pattern" ] && args+=("-Ptests=$pattern")
  rm -f build/test-report.xml
  ./gradlew runGameTestServer "${args[@]}" > "$out/run-$i.log" 2>&1
  if [ ! -f build/test-report.xml ]; then echo "run $i: NO REPORT (build or server failed, see $out/run-$i.log)"; continue; fi
  cp build/test-report.xml "$out/run-$i.xml"
  n=$(python3 -I - "$out/run-$i.xml" <<'PY'
import sys, re
x = open(sys.argv[1]).read()
print(len(re.findall(r'<testcase ', x)), len(re.findall(r'<(failure|error)', x)))
PY
)
  echo "run $i: tests/failures $n"
done
python3 -I - "$out" "$runs" <<'PY'
import sys, re, glob, collections
out, runs = sys.argv[1], int(sys.argv[2])
fails = collections.defaultdict(list)
for f in sorted(glob.glob(out + '/run-*.xml')):
    x = open(f).read()
    for m in re.finditer(r'<testcase [^>]*name="([^"]+)"[^>]*?(?:/>|>(.*?)</testcase>)', x, re.S):
        body = m.group(2) or ''
        fm = re.search(r'<(?:failure|error)[^>]*message="([^"]*)"', body)
        if fm:
            fails[m.group(1)].append(fm.group(1))
print()
for name, msgs in sorted(fails.items(), key=lambda kv: -len(kv[1])):
    print(f"{len(msgs)}/{runs}  {name}\n      {msgs[0][:200]}")
if not fails: print("no failures")
PY
