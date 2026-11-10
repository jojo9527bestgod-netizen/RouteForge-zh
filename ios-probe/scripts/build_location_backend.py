#!/usr/bin/env python3
"""Build the small RouteForge extension inside the pinned MIT idevice FFI crate.

One static library owns all Rust handles; never link two copies of the runtime.
The original, verified public C header stays in place. RFCoreLocation.h declares
only our added ABI. No device files, signing credentials or pairing are involved.
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_URL = "https://api.github.com/repos/jkcoxson/idevice/tarball/v0.1.68"
SOURCE_SHA256 = "b4668726cff51ad1813837f7d17689badc95612aa83f81471aa99317d543f357"
# Match Xcode 27's LLVM 21 reader; newer Rust bitcode is not readable by nm/ld.
RUST_VERSION = "1.94.0"
FEATURES = "full,aws-lc"


def prepare(archive=None):
    vendor = ROOT / "Vendor"
    vendor.mkdir(exist_ok=True)
    archive = archive or vendor / "idevice-v0.1.68-source.tar.gz"
    if not archive.exists():
        request = urllib.request.Request(SOURCE_URL, headers={"User-Agent": "RouteForge-iOS-Probe"})
        temporary = archive.with_suffix(".download")
        with urllib.request.urlopen(request, timeout=120) as incoming, temporary.open("wb") as output:
            shutil.copyfileobj(incoming, output)
        temporary.replace(archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise SystemExit("Backend source digest mismatch")
    destination = vendor / "idevice-source"
    destination.mkdir(exist_ok=True)
    with tarfile.open(archive) as source:
        # No links or unexpected paths from a network archive are accepted.
        members = source.getmembers()
        roots = {Path(member.name).parts[0] for member in members}
        if len(roots) != 1:
            raise SystemExit("Unexpected backend source archive layout")
        readme_link = next(iter(roots)) + "/idevice/README.md"
        for member in members:
            path = destination / member.name
            if member.name == readme_link and member.issym() and member.linkname == "../README.md":
                continue
            if not path.resolve().is_relative_to(destination.resolve()) or not (member.isdir() or member.isfile()):
                raise SystemExit("Unexpected backend source archive entry")
        source.extractall(destination, members=[m for m in members if m.isdir() or m.isfile()], filter="data")
    workspace = destination / roots.pop()
    shutil.copyfile(workspace / "README.md", workspace / "idevice/README.md")
    extension = ROOT / "Backend/rf_core_location.rs"
    shutil.copyfile(extension, workspace / "ffi/src/rf_core_location.rs")
    library = workspace / "ffi/src/lib.rs"
    with library.open("a", encoding="utf-8", newline="\n") as output:
        output.write('\n#[cfg(feature = "core_device")]\npub mod rf_core_location;\n')
    return workspace


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--prepare-only", action="store_true")
    args = parser.parse_args()
    workspace = prepare(args.archive)
    if args.prepare_only:
        print(workspace)
        return
    if os.uname().sysname != "Darwin":
        raise SystemExit("The iPhone backend must be compiled on macOS with Xcode")
    subprocess.run(["rustup", "toolchain", "install", RUST_VERSION, "--profile", "minimal"], check=True)
    subprocess.run(["rustup", "target", "add", "--toolchain", RUST_VERSION, "aarch64-apple-ios"], check=True)
    environment = dict(os.environ, IPHONEOS_DEPLOYMENT_TARGET="17.4",
                       CARGO_BUILD_JOBS=os.environ.get("CARGO_BUILD_JOBS", "2"))
    subprocess.run(["cargo", f"+{RUST_VERSION}", "test", "--locked", "-p", "idevice-ffi", "--lib",
                    "--no-default-features", "--features", FEATURES, "rf_core_location::tests"],
                   cwd=workspace, env=environment, check=True)
    command = ["cargo", f"+{RUST_VERSION}", "build", "--locked", "--release", "-p", "idevice-ffi",
               "--target", "aarch64-apple-ios", "--no-default-features", "--features", FEATURES]
    subprocess.run(command, cwd=workspace, env=environment, check=True)
    archive = workspace / "target/aarch64-apple-ios/release/libidevice_ffi.a"
    # Assert all additions are exported before replacing the shipped dependency.
    symbols = subprocess.check_output(["nm", "-gU", str(archive)], text=True)
    for name in ["connect_rsd", "set", "start", "clear", "free"]:
        if "_rf_core_location_" + name not in symbols:
            raise SystemExit("Missing CoreDevice location export: " + name)
    output = ROOT / "Vendor/IDevice.xcframework/ios-arm64/libidevice_ffi.a"
    shutil.copyfile(archive, output)
    (ROOT / "Vendor/core-location-backend-lock.json").write_text(json.dumps({
        "base_version": "0.1.68", "source_url": SOURCE_URL, "source_sha256": SOURCE_SHA256,
        "extension_sha256": hashlib.sha256((ROOT / "Backend/rf_core_location.rs").read_bytes()).hexdigest(),
        "rust_version": RUST_VERSION, "features": FEATURES, "target": "aarch64-apple-ios",
        "library_sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
    }, indent=2) + "\n", encoding="utf-8")
    print("CoreDevice route backend compiled and exports verified")


if __name__ == "__main__":
    main()
