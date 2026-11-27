#!/usr/bin/env bash
set -euo pipefail

suites=com.example.ui.theme.AppTextScaleTest,com.example.player.PlaybackDeviceSmokeTest,com.example.player.PlaybackGestureDeviceTest,com.example.player.ColorPresetGpuTest,com.example.filemanager.RecycleBinDeviceTest
api=${1:?Android API level required}
case "$api" in 24|30|36) ;; *) echo "Unsupported test image: $api" >&2; exit 2 ;; esac

# The Android 7 ddmlib split-install session times out writing this large APK.
# Stage each standalone APK first, then install it from the emulator's disk.
shopt -s nullglob
app_apks=(app/build/outputs/apk/debug/*x86_64*.apk)
test_apks=(app/build/outputs/apk/androidTest/debug/*.apk)
if (( ${#app_apks[@]} != 1 || ${#test_apks[@]} != 1 )); then
  echo "Expected one x86_64 app APK and one instrumentation APK." >&2
  exit 1
fi
install_test_apk() {
  local apk=$1 remote result
  remote="/data/local/tmp/$(basename "$apk")"
  timeout 5m adb push "$apk" "$remote"
  result=$(timeout 8m adb shell pm install -r -t "$remote" | tr -d '\r')
  echo "$result"
  grep -Fxq Success <<< "$result"
  adb shell rm -f "$remote"
}
install_test_apk "${app_apks[0]}"
reports="build/playback-diagnostics/api-$api"
mkdir -p "$reports"
adb logcat -c
# The Android system's first-use immersive prompt is outside the app under test.
adb shell settings put secure immersive_mode_confirmations confirmed
python3 tools/test_cold_launch.py || {
  adb logcat -d -b crash | tee "$reports/launcher-crash.txt"
  adb logcat -d > "$reports/launcher-logcat.txt"
  adb shell dumpsys activity activities > "$reports/launcher-activities.txt"
  adb shell dumpsys window > "$reports/launcher-window.txt"
  exit 1
}
install_test_apk "${test_apks[0]}"
runner=com.aistudio.hiplayer.vdmk.test/androidx.test.runner.AndroidJUnitRunner
library_diagnostics() {
  adb exec-out run-as com.aistudio.hiplayer.vdmk cat files/library-screen.png > "$reports/library-screen.png" || true
  adb exec-out run-as com.aistudio.hiplayer.vdmk cat files/library-state.txt > "$reports/library-state.txt" || true
  adb shell dumpsys activity activities > "$reports/library-activities.txt" || true
  adb logcat -d > "$reports/library-logcat.txt" || true
}
trap library_diagnostics ERR
timeout 4m adb shell am instrument -w -r -e class com.example.player.LibraryPlaybackDeviceTest "$runner" \
  | tr -d '\r' | tee "$reports/library-playback.txt"
grep -Exq 'OK \(2 tests\)' "$reports/library-playback.txt"
grep -Fxq 'INSTRUMENTATION_CODE: -1' "$reports/library-playback.txt"
trap - ERR
case "$api" in
  36) exit 0 ;;
  30)
    # The compiled APKs are already installed. Keep every original test suite,
    # but run Gecko integration and Compose chrome separately so a stalled
    # instrumentation cannot consume the entire job without a failure report.
    result=0
    run_gecko_suite() {
      local suite=$1 name=$2 limit=$3 log
      log="$reports/$name.txt"
      adb logcat -c
      if timeout --kill-after=15s "$limit" adb shell am instrument -w -r -e class "$suite" "$runner" \
          | tr -d '\r' | tee "$log" &&
          grep -Exq 'OK \([1-9][0-9]* tests?\)' "$log" &&
          grep -Fxq 'INSTRUMENTATION_CODE: -1' "$log"; then
        local expected
        IFS=, read -ra expected_suites <<< "$suite"
        for expected in "${expected_suites[@]}"; do
          if ! grep -Fxq "INSTRUMENTATION_STATUS: class=$expected" "$log"; then
            echo "Missing test suite: $expected" >&2
            result=1
          fi
        done
      else
        echo "Failed or timed out: $name ($suite)" >&2
        result=1
      fi
      timeout 20s adb logcat -d > "$reports/$name-logcat.txt" || true
      timeout 20s adb shell dumpsys activity activities > "$reports/$name-activities.txt" || true
      timeout 20s adb shell dumpsys window > "$reports/$name-window.txt" || true
      # Do not leave a stuck runner or app holding resources for the next suite.
      timeout 20s adb shell am force-stop com.aistudio.hiplayer.vdmk.test || result=1
      timeout 20s adb shell am force-stop com.aistudio.hiplayer.vdmk || result=1
    }
    run_gecko_suite com.example.browser.gecko.GeckoMigrationDeviceTest gecko-integration 6m
    run_gecko_suite com.example.browser.gecko.GeckoChromeDeviceTest gecko-chrome 4m
    run_gecko_suite "$suites" playback-and-app 12m
    exit "$result"
    ;;
  24) suites="com.example.browser.gecko.GeckoFallbackDeviceTest,$suites" ;;
esac

adb logcat -c
trap 'adb logcat -d -s TestRunner AndroidRuntime HiPlayerColor > "$reports/logcat.txt" || true' EXIT
timeout 12m adb shell am instrument -w -r -e class "$suites" "$runner" \
  | tr -d '\r' | tee "$reports/instrumentation.txt"

# am instrument can exit zero after a test failure or a process crash.
grep -Exq 'OK \([1-9][0-9]* tests?\)' "$reports/instrumentation.txt"
grep -Fxq 'INSTRUMENTATION_CODE: -1' "$reports/instrumentation.txt"
IFS=, read -ra expected_suites <<< "$suites"
for suite in "${expected_suites[@]}"; do
  grep -Fxq "INSTRUMENTATION_STATUS: class=$suite" "$reports/instrumentation.txt"
done
