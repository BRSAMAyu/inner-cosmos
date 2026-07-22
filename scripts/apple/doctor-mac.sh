#!/usr/bin/env bash
set -euo pipefail

for tool in xcodebuild xcrun node pnpm rustup cargo; do
  command -v "$tool" >/dev/null || { echo "MISSING=$tool" >&2; exit 1; }
done

xcodebuild -version
xcrun simctl list devices available
node --version
pnpm --version
rustc --version
cargo --version

if [[ -z "${DEVELOPMENT_TEAM:-}" ]]; then
  echo "EXTERNAL_GATE=DEVELOPMENT_TEAM is not set; unsigned build preparation can continue."
fi
if [[ -z "${APPLE_ID:-}" || -z "${APPLE_PASSWORD:-}" || -z "${APPLE_TEAM_ID:-}" ]]; then
  echo "EXTERNAL_GATE=Apple notarization credentials are not injected."
fi
