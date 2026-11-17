#!/usr/bin/env bash
# Run against the physical phone while reproducing playback. No media URLs/credentials are collected by this script.
set -euo pipefail
out=${1:-build/playback-diagnostics}
pkg=com.aistudio.hiplayer.vdmk
mkdir -p "$out"
adb get-state
adb shell getprop ro.build.fingerprint > "$out/device.txt"
adb shell dumpsys display > "$out/display.txt"
adb shell dumpsys media.codec > "$out/codecs.txt"
adb shell dumpsys SurfaceFlinger > "$out/surfaces.txt"
adb shell dumpsys activity lastanr > "$out/last-anr.txt"
# Only app color diagnostics; broad logcat/bugreports may include personal data.
adb logcat -d -v threadtime -s HiPlayerLibVLC:E HiPlayerColor:I AndroidRuntime:E > "$out/player-logcat.txt"
pid=$(adb shell pidof "$pkg" | tr -d '\r')
if [ -n "$pid" ]; then adb shell kill -3 "$pid" || true; fi
cat > "$out/perfetto.cfg" <<'CFG'
buffers { size_kb: 32768 fill_policy: RING_BUFFER }
data_sources { config { name: "linux.ftrace" ftrace_config {
  ftrace_events: "sched/sched_switch"
  ftrace_events: "sched/sched_wakeup"
  ftrace_events: "power/cpu_frequency"
  atrace_categories: "view"
  atrace_categories: "video"
  atrace_categories: "am"
  atrace_categories: "wm"
  atrace_apps: "com.aistudio.hiplayer.vdmk"
} } }
duration_ms: 20000
CFG
adb push "$out/perfetto.cfg" /data/local/tmp/hiplayer-perfetto.cfg >/dev/null
adb shell perfetto --txt -c /data/local/tmp/hiplayer-perfetto.cfg -o /data/misc/perfetto-traces/hiplayer.pftrace
adb pull /data/misc/perfetto-traces/hiplayer.pftrace "$out/hiplayer.pftrace"
