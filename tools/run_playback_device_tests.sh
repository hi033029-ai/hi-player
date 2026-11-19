#!/usr/bin/env bash
set -euo pipefail

suites=com.example.player.PlaybackDeviceSmokeTest,com.example.player.PlaybackGestureDeviceTest,com.example.player.ColorPresetGpuTest,com.example.filemanager.RecycleBinDeviceTest
case "${1:?Android API level required}" in
  30)
    suites="com.example.browser.gecko.GeckoMigrationDeviceTest,com.example.browser.gecko.GeckoChromeDeviceTest,$suites"
    exec gradle --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' \
      :app:connectedDebugAndroidTest -PHI_PLAYER_GECKO_ENABLED=true \
      -PHI_PLAYER_GECKO_DEVICE_TEST=true -PHI_PLAYER_GECKOVIEW_ABI=x86_64 \
      -PHI_PLAYER_ABI_FILTERS=x86_64 --max-workers=1 \
      "-Pandroid.testInstrumentationRunnerArguments.class=$suites"
    ;;
  24) suites="com.example.browser.gecko.GeckoFallbackDeviceTest,$suites" ;;
  *) echo "Unsupported test image: $1" >&2; exit 2 ;;
esac

# The Android 7 ddmlib split-install session times out writing this large APK.
# Stage each standalone APK first, then install it from the emulator's disk.
shopt -s nullglob
app_apks=(app/build/outputs/apk/debug/*x86_64*.apk)
test_apks=(app/build/outputs/apk/androidTest/debug/*.apk)
if (( ${#app_apks[@]} != 1 || ${#test_apks[@]} != 1 )); then
  echo "Expected one x86_64 app APK and one instrumentation APK." >&2
  exit 1
fi
reports=build/playback-diagnostics/api-24
mkdir -p "$reports"
for apk in "${app_apks[0]}" "${test_apks[0]}"; do
  remote="/data/local/tmp/$(basename "$apk")"
  timeout 5m adb push "$apk" "$remote"
  result=$(timeout 8m adb shell pm install -r -t "$remote" | tr -d '\r')
  echo "$result"
  grep -Fxq Success <<< "$result"
  adb shell rm -f "$remote"
done

runner=com.aistudio.hiplayer.vdmk.test/androidx.test.runner.AndroidJUnitRunner
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
