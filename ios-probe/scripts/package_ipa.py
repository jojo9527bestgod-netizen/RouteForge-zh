#!/usr/bin/env python3
"""Package an already built unsigned .app for local iloader signing."""
import argparse
import hashlib
import json
import plistlib
import zipfile
from pathlib import Path


def validate_setup_resources(app):
    lock_path = app / "DeviceSupportLock.json"
    if not lock_path.is_file():
        raise SystemExit("Built app is missing its setup resource lock")
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    expected = {"BuildManifest.plist", "Image.dmg", "Image.dmg.trustcache", "Image.dmg.cryptex_info", "Image.dmg.root_hash"}
    if len(lock["files"]) != len(expected) or {r["name"] for r in lock["files"]} != expected:
        raise SystemExit("Unexpected built setup resource list")
    for record in lock["files"]:
        path = app / "DeviceSupport/Restore" / record["name"]
        if not path.is_file() or path.stat().st_size != record["bytes"]:
            raise SystemExit("Built app is missing a setup resource")
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        if digest != record["sha256"]:
            raise SystemExit("Built setup resource digest mismatch")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("app", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    app = args.app.resolve()
    if not app.is_dir() or app.suffix != ".app":
        raise SystemExit("Expected a built .app directory")
    metadata = plistlib.loads((app / "Info.plist").read_bytes())
    version = tuple(int(part) for part in metadata["CFBundleShortVersionString"].split("."))
    if version >= (0, 3):
        validate_setup_resources(app)
    executable = app / metadata["CFBundleExecutable"]
    if not executable.is_file() or not executable.stat().st_mode & 0o111:
        raise SystemExit("Built app executable is missing or not executable")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for item in sorted(app.rglob("*")):
            if item.is_file():
                if item.is_symlink():
                    raise SystemExit("Unexpected symlink inside device app")
                archive.write(item, (Path("Payload") / app.name / item.relative_to(app)).as_posix())
    print(f"Unsigned IPA ready: {args.output}")


if __name__ == "__main__":
    main()
