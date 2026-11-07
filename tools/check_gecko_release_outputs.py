#!/usr/bin/env python3
"""Validate the test-signed Gecko release APK splits and multi-ABI AAB."""
import hashlib
import json
import struct
import zipfile
from pathlib import Path

APK_PATHS = {
    "arm64-v8a": Path("dist/hi-player-arm64-v8a-release.apk"),
    "armeabi-v7a": Path("dist/hi-player-armeabi-v7a-release.apk"),
}
BUNDLE_PATH = Path("dist/hi-player-release.aab")
EXPECTED_ABIS = sorted(APK_PATHS)
MAX_APK_BYTES = 250 * 1024 * 1024


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_arm64_alignment(archive: zipfile.ZipFile, name: str) -> int:
    with archive.open(name) as stream:
        header = stream.read(64)
        if len(header) < 64 or header[:5] != bytes((127, 69, 76, 70, 2)):
            return 0
        offset = struct.unpack_from("<Q", header, 32)[0]
        entry_size, entry_count = struct.unpack_from("<HH", header, 54)
        table_end = offset + entry_size * entry_count
        if table_end < 64:
            return 0
        table = header + stream.read(table_end - 64)
        alignments = [
            struct.unpack_from("<Q", table, offset + index * entry_size + 48)[0]
            for index in range(entry_count)
            if struct.unpack_from("<I", table, offset + index * entry_size)[0] == 1
        ]
        assert alignments, f"No ELF load segments found in {name}"
        minimum = min(alignments)
        assert minimum >= 16384, f"Native library lacks 16 KB page alignment: {name} ({minimum})"
        return minimum


def inspect_apk(abi: str, path: Path) -> dict:
    assert path.is_file(), f"Missing release APK: {path}"
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        packaged_abis = sorted({
            name.split("/")[1]
            for name in names
            if name.startswith("lib/") and name.endswith(".so")
        })
        assert packaged_abis == [abi], f"{path} should contain only {abi}, found {packaged_abis}"
        assert not any(name.endswith("/libgav1JNI.so") for name in names), "Unsafe legacy JNI bridge packaged"
        assert "assets/hi-gecko/manifest.json" in names, "Local Gecko extension is missing"
        assert "assets/gecko_component_licenses.txt" in names, "Offline Gecko notices are missing"
        alignment = {}
        if abi == "arm64-v8a":
            for name in names:
                if name.startswith("lib/arm64-v8a/") and name.endswith(".so"):
                    alignment[name] = verify_arm64_alignment(archive, name)
            assert alignment, "No arm64 native libraries were checked"
    assert path.stat().st_size <= MAX_APK_BYTES, f"{abi} APK exceeds the 250 MiB size budget"
    return {
        "file": str(path),
        "abi": abi,
        "bytes": path.stat().st_size,
        "mib": round(path.stat().st_size / 1048576, 2),
        "sha256": sha256(path),
        "arm64_min_load_alignment_bytes": min(alignment.values()) if alignment else None,
    }


def inspect_bundle(path: Path) -> dict:
    assert path.is_file(), f"Missing release AAB: {path}"
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        packaged_abis = sorted({
            name.split("/")[2]
            for name in names
            if name.startswith("base/lib/") and name.endswith(".so")
        })
        assert packaged_abis == EXPECTED_ABIS, f"AAB ABI set should be {EXPECTED_ABIS}, found {packaged_abis}"
        assert not any(name.endswith("/libgav1JNI.so") for name in names), "Unsafe legacy JNI bridge packaged"
        assert "base/assets/hi-gecko/manifest.json" in names, "Local Gecko extension is missing from AAB"
        assert "base/assets/gecko_component_licenses.txt" in names, "Offline Gecko notices are missing from AAB"
    return {
        "file": str(path),
        "abis": packaged_abis,
        "bytes": path.stat().st_size,
        "mib": round(path.stat().st_size / 1048576, 2),
        "sha256": sha256(path),
    }


report = {
    "apk_splits": [inspect_apk(abi, APK_PATHS[abi]) for abi in EXPECTED_ABIS],
    "aab": inspect_bundle(BUNDLE_PATH),
    "release_abis": EXPECTED_ABIS,
}
Path("build").mkdir(exist_ok=True)
Path("build/gecko-release-review.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
