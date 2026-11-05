#!/usr/bin/env bash
# Execute the complete acceptance sequence in one shell. The emulator runner
# invokes each newline of its inline "script" as a separate shell command,
# so shell variables, if-blocks, and exit codes cannot span those invocations.
set -uo pipefail

test_status=0
gradle --no-daemon --stacktrace :app:assembleDebug :app:connectedDebugAndroidTest || test_status=$?

# Keep benchmark evidence even if instrumentation fails.
adb logcat -d -s ColmeyVisionBenchmark:I '*:S' > instrument-photo-benchmark.log || true
cat instrument-photo-benchmark.log

if [ "$test_status" -ne 0 ]; then
    exit "$test_status"
fi

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm path ai.colmey.instrument
adb shell am start -W -n ai.colmey.instrument/.MainActivity
