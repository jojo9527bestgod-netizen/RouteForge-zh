#!/usr/bin/env python3
"""Fetch the pinned MIT backend. No credentials or device access required."""
import argparse
import hashlib
import json
import plistlib
import shutil
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.1.68"
SHA256 = "c9eccdd1942de756d746a2569d93302774ab0eff1cff314ec8118f768b3d8dc7"
URL = f"https://github.com/jkcoxson/idevice/releases/download/v{VERSION}/idevice-xcframework-v{VERSION}.zip"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", type=Path, help="Use an already downloaded official archive")
    args = parser.parse_args()
    vendor = ROOT / "Vendor"
    vendor.mkdir(exist_ok=True)
    archive = args.archive or vendor / f"idevice-xcframework-v{VERSION}.zip"
    if not archive.exists():
        temporary = archive.with_suffix(".download")
        request = urllib.request.Request(URL, headers={"User-Agent": "RouteForge-iOS-Probe"})
        with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
        temporary.replace(archive)
    with archive.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    if digest != SHA256:
        raise SystemExit("Dependency digest mismatch; nothing extracted")
    # This first build targets a physical iPhone. Keep the device slice unchanged.
    prefix = "swift/IDevice.xcframework/"
    framework = vendor / "IDevice.xcframework"
    with zipfile.ZipFile(archive) as source:
        info = plistlib.loads(source.read(prefix + "Info.plist"))
        info["AvailableLibraries"] = [
            item for item in info["AvailableLibraries"] if item["LibraryIdentifier"] == "ios-arm64"
        ]
        if len(info["AvailableLibraries"]) != 1:
            raise SystemExit("Official archive is missing the iPhone arm64 slice")
        for item in source.infolist():
            if not item.filename.startswith(prefix + "ios-arm64/") or item.is_dir():
                continue
            relative = Path(item.filename.removeprefix(prefix))
            destination = (framework / relative).resolve()
            if not destination.is_relative_to(framework.resolve()) or (item.external_attr >> 16) & 0o170000 == 0o120000:
                raise SystemExit("Unexpected archive path")
            destination.parent.mkdir(parents=True, exist_ok=True)
            with source.open(item) as incoming, destination.open("wb") as output:
                shutil.copyfileobj(incoming, output)
        (framework / "Info.plist").write_bytes(plistlib.dumps(info))
    (vendor / "dependency-lock.json").write_text(
        json.dumps({"version": VERSION, "url": URL, "sha256": digest, "slice": "ios-arm64"}, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Verified idevice {VERSION}; iPhone arm64 dependency ready")


if __name__ == "__main__":
    main()
