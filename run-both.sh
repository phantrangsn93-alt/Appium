#!/usr/bin/env bash
# Runs the same TestNG suite on Android and iOS at the same time (one Maven process per platform).
# Usage: ./run-both.sh [suite.xml] [ios-wda-url]
#   ./run-both.sh testng-readonly.xml http://192.168.1.95:8100
# Results: target/android/... and target/ios/... (allure-results, surefire-reports, evidence), logs in target/*.log
set -euo pipefail
cd "$(dirname "$0")"
SUITE="${1:-testng-readonly.xml}"
WDA_URL="${2:-}"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/java/jdk-17.0.20+8/Contents/Home}"
export PATH="$HOME/.local/apache-maven-3.9.9/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

mvn -q -o test-compile                      # compile once: the two runs must not compile concurrently
rm -rf target/android target/ios

COMMON=(-o -Dsuite="$SUITE" -Dmaven.main.skip=true)
mvn "${COMMON[@]}" test -Dplatform.name=Android -Dresults.dir=target/android > target/android.log 2>&1 &
PID_A=$!
mvn "${COMMON[@]}" test -Dplatform.name=iOS -Dresults.dir=target/ios ${WDA_URL:+-Dios.wda.url=$WDA_URL} > target/ios.log 2>&1 &
PID_I=$!
echo "Android PID $PID_A (target/android.log) | iOS PID $PID_I (target/ios.log)"

rc=0
wait $PID_A || rc=1
wait $PID_I || rc=1
grep -h 'Tests run:.*Skipped: [0-9]*$' target/android.log | tail -1 | sed 's/^/Android: /' || true
grep -h 'Tests run:.*Skipped: [0-9]*$' target/ios.log | tail -1 | sed 's/^/iOS:     /' || true
exit $rc
