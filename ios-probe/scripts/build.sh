#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This build needs macOS with Xcode. Source preparation can run on Windows." >&2
  exit 1
fi
command -v xcodegen >/dev/null || { echo "Install XcodeGen first: brew install xcodegen" >&2; exit 1; }
python3 scripts/fetch_dependencies.py
python3 scripts/fetch_setup_assets.py
swift test --package-path RouteCore
xcodegen generate --spec project.yml
xcodebuild -project RouteForgeProbe.xcodeproj -scheme RouteForgeProbe \
  -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath build/DerivedData CODE_SIGNING_ALLOWED=NO build
python3 scripts/package_ipa.py \
  build/DerivedData/Build/Products/Release-iphoneos/RouteForgeProbe.app \
  build/RouteForgeProbe-unsigned.ipa
