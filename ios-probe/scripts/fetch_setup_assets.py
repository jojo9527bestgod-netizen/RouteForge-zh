#!/usr/bin/env python3
"""Fetch a pinned, hash-verified DDI resource set without any device or account data."""
import argparse
import hashlib
import json
import plistlib
import shutil
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {"BuildManifest.plist", "Image.dmg", "Image.dmg.trustcache", "Image.dmg.cryptex_info", "Image.dmg.root_hash"}


def verified(path, record):
    if not path.is_file() or path.stat().st_size != record["bytes"]:
        return False
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest() == record["sha256"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-directory", type=Path, help="Use an already downloaded resource set")
    args = parser.parse_args()
    lock = json.loads((ROOT / "Resources/DeviceSupportLock.json").read_text(encoding="utf-8"))
    records = lock["files"]
    if len(records) != len(EXPECTED) or {r["name"] for r in records} != EXPECTED:
        raise SystemExit("Unexpected resource file set")
    restore = ROOT / "Vendor/DeviceSupport/Restore"
    restore.mkdir(parents=True, exist_ok=True)
    for record in records:
        name = record["name"]
        if not 1 <= record["bytes"] <= 64_000_000:
            raise SystemExit("Unexpected resource size")
        prefix = f"https://raw.githubusercontent.com/doronz88/DeveloperDiskImage/{lock['source_commit']}/PersonalizedImages/Xcode_iOS_DDI_Cryptex/"
        if record["url"] != prefix + name:
            raise SystemExit("Unpinned resource URL")
        destination = restore / name
        if verified(destination, record):
            continue
        temporary = destination.with_name(name + ".download")
        try:
            if args.source_directory:
                source = args.source_directory / name
                if not verified(source, record):
                    raise SystemExit("Cached resource verification failed")
                shutil.copyfile(source, temporary)
            else:
                request = urllib.request.Request(record["url"], headers={"User-Agent": "RouteForge-Device-Setup"})
                with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
                    remaining = record["bytes"]
                    while block := response.read(min(1024 * 1024, remaining + 1)):
                        remaining -= len(block)
                        if remaining < 0:
                            raise SystemExit("Resource larger than pinned size")
                        output.write(block)
            if not verified(temporary, record):
                raise SystemExit("Resource digest mismatch")
            temporary.replace(destination)
        finally:
            temporary.unlink(missing_ok=True)
    manifest = plistlib.loads((restore / "BuildManifest.plist").read_bytes())
    if manifest.get("ProductBuildVersion") != lock["build"]:
        raise SystemExit("DDI build mismatch")
    components = {"Cryptex1,GenericDmg": "Image.dmg", "Cryptex1,GenericTrustCache": "Image.dmg.trustcache",
                  "Cryptex1,CryptexInfoPlist": "Image.dmg.cryptex_info", "Cryptex1,GenericVolume": "Image.dmg.root_hash"}
    identities = [item for item in manifest["BuildIdentities"] if all(key in item["Manifest"] for key in components)]
    if len(identities) != 1:
        raise SystemExit("Expected exactly one Cryptex1 build identity")
    for key, name in components.items():
        entry = identities[0]["Manifest"][key]
        if entry["Info"]["Path"] != name or hashlib.sha384((restore / name).read_bytes()).digest() != entry["Digest"]:
            raise SystemExit("Apple manifest payload digest mismatch")
    print(f"Verified setup resources {lock['build']}: {sum(r['bytes'] for r in records)} bytes")


if __name__ == "__main__":
    main()
