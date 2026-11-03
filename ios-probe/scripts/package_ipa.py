#!/usr/bin/env python3
"""Package an already built unsigned .app for local iloader signing."""
import argparse
import plistlib
import zipfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("app", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    app = args.app.resolve()
    if not app.is_dir() or app.suffix != ".app":
        raise SystemExit("Expected a built .app directory")
    metadata = plistlib.loads((app / "Info.plist").read_bytes())
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
