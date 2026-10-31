#!/usr/bin/env python3
"""Stage a signed YouTube-blocker release for the dedicated blocker-updates branch.

Keep the private signing key OUTSIDE the repository. This does not push, build an
APK, alter APK versions, or create/rotate the app's pinned verification key.
"""
import argparse
import base64
import hashlib
import json
import subprocess
import tempfile
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--key", type=Path, required=True)
    parser.add_argument("--version", type=int, required=True)
    parser.add_argument("--min-app-version", type=int, default=77)
    parser.add_argument("--expires-days", type=int, default=30)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    key = args.key.resolve()
    if key.is_relative_to(root):
        parser.error("Private signing keys must be outside the repository")
    if args.version < 1 or not 1 <= args.expires_days <= 90 or args.min_app_version < 77:
        parser.error("Invalid package version, expiry, or app compatibility floor")
    public = subprocess.check_output(["openssl", "pkey", "-in", str(key), "-pubout"], stderr=subprocess.DEVNULL)
    pinned = (root / "app/src/main/assets/blocker_update_public_key.pem").read_bytes()
    if public.strip() != pinned.strip():
        parser.error("Signing key does not match the app's pinned public key")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    old = output / "manifest.json"
    if old.is_file() and args.version <= json.loads(old.read_text())["version"]:
        parser.error("New release version must be higher than the previous package")
    script = (root / "app/src/main/assets/orbit_youtube.js").read_bytes()
    if not 0 < len(script) <= 100_000:
        parser.error("Script exceeds the updater's byte limit")
    digest = hashlib.sha256(script).hexdigest()
    now = int(time.time() * 1000)
    manifest = dict(schema=1, asset="orbit_youtube.js", version=args.version,
                    issuedAt=now, expiresAt=now + args.expires_days * 86400000,
                    minAppVersionCode=args.min_app_version, sha256=digest)
    data = (json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n").encode()
    with tempfile.TemporaryDirectory() as temp:
        source = Path(temp) / "manifest.json"
        signature = Path(temp) / "signature.der"
        source.write_bytes(data)
        subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(key), "-out", str(signature), str(source)],
                       check=True, stderr=subprocess.DEVNULL)
        signed = signature.read_bytes()
    packages = output / "packages"
    packages.mkdir(exist_ok=True)
    (packages / f"{digest}.js").write_bytes(script)
    (output / "manifest.json").write_bytes(data)
    (output / "manifest.sig.b64").write_bytes(base64.b64encode(signed) + b"\n")
    print(f"Staged signed blocker version {args.version}; min app {args.min_app_version}. No APK built or pushed.")


if __name__ == "__main__":
    main()
