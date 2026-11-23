"""Cold-launch the installed app with indexed media on a disposable CI emulator."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.aistudio.hiplayer.vdmk"
FOLDER = "HiPlayerLaunchCheck"
VIDEO = "launch-check.mp4"
COMPONENT = f"{PACKAGE}/com.example.MainActivity"


def adb(*args, check=True):
    return subprocess.run(["adb", *args], text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, check=check, timeout=30).stdout.strip()


def cold_start():
    adb("shell", "am", "force-stop", PACKAGE)
    result = adb("shell", "am", "start", "-a", "android.intent.action.MAIN",
                 "-c", "android.intent.category.LAUNCHER", "-n", COMPONENT)
    if "Error:" in result or "Exception" in result:
        raise AssertionError(result)


def _dump_labels():
    dump = adb("shell", "uiautomator", "dump", "/data/local/tmp/hi-launch.xml", check=False)
    if "dumped" not in dump.lower():
        return None, []
    root = ET.fromstring(adb("shell", "cat", "/data/local/tmp/hi-launch.xml"))
    labels = []
    bounds_by_label = {}
    for node in root.iter("node"):
        if node.get("visible-to-user", "true").lower() != "true":
            continue
        for name in ("text", "content-desc"):
            value = node.get(name, "")
            if value:
                labels.append(value)
        if node.get("package") == PACKAGE:
            bounds = [int(n) for n in re.findall(r"\d+", node.attrib["bounds"])]
            for name in ("text", "content-desc"):
                value = node.get(name, "")
                if value and value not in bounds_by_label:
                    bounds_by_label[value] = bounds
    return bounds_by_label, labels


def wait_for(label):
    deadline = time.monotonic() + 60
    labels = []
    previous = None
    refreshed = False
    while time.monotonic() < deadline:
        crash = adb("logcat", "-d", "-b", "crash")
        if f"Process: {PACKAGE}," in crash:
            raise AssertionError("Launcher process crashed: " + crash[-6000:])
        bounds_by_label, labels = _dump_labels()
        if bounds_by_label is not None:
            target = bounds_by_label.get(label)
            if target is not None and target == previous:
                return target
            previous = target
            if target is None and not refreshed and "No videos found" in labels and "Refresh" in bounds_by_label:
                rx1, ry1, rx2, ry2 = bounds_by_label["Refresh"]
                adb("shell", "input", "tap", str((rx1 + rx2) // 2), str((ry1 + ry2) // 2))
                refreshed = True
                time.sleep(1.5)
                continue
        time.sleep(.5)
    raise AssertionError(f"Launcher did not display {label!r}. Visible labels: {labels}")


def tap(label):
    x1, y1, x2, y2 = wait_for(label)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def tap_until(label, expected):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        tap(label)
        sub_deadline = min(deadline, time.monotonic() + 6)
        while time.monotonic() < sub_deadline:
            bounds_by_label, _ = _dump_labels()
            if bounds_by_label is not None and expected in bounds_by_label:
                return
            time.sleep(0.5)
    wait_for(expected)


def stage_video(source, folder, name):
    adb("shell", "mkdir", "-p", f"/sdcard/Movies/{folder}")
    adb("push", f"app/src/debug/assets/{source}", f"/sdcard/Movies/{folder}/{name}")
    adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
        "-d", f"file:///storage/emulated/0/Movies/{folder}/{name}", check=False)
    try:
        adb("shell", "content", "call", "--uri", "content://media/external",
            "--method", "scan_file", "--arg", f"/sdcard/Movies/{folder}/{name}", check=False)
    except subprocess.TimeoutExpired:
        pass
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        out = adb("shell", "content", "query",
                  "--uri", "content://media/external/video/media",
                  "--projection", "_display_name", check=False)
        if name in out:
            return
        time.sleep(1.0)


def main():
    api = int(adb("shell", "getprop", "ro.build.version.sdk"))
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard", check=False)
    adb("shell", "input", "keyevent", "82")
    for perm in (["READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO"] if api >= 33 else ["READ_EXTERNAL_STORAGE"]):
        adb("shell", "pm", "grant", PACKAGE, "android.permission." + perm)
    stage_video("gecko_test.mp4", FOLDER, VIDEO)
    cold_start()
    tap("Skip setup")
    tap_until(FOLDER, VIDEO)
    print("First launch opened the indexed video folder.", flush=True)
    adb("shell", "input", "keyevent", "4")
    cold_start()
    tap_until(FOLDER, VIDEO)
    print(f"API {api}: returning cold launch opened the video list.", flush=True)
    adb("shell", "am", "force-stop", PACKAGE)
    stage_video("portrait-check.mkv", "HiPlayerPortraitCheck", "portrait-check.mkv")


if __name__ == "__main__":
    main()
