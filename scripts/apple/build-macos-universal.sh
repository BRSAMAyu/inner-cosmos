#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT/web"
pnpm install --frozen-lockfile
rustup target add aarch64-apple-darwin x86_64-apple-darwin
pnpm exec tauri build --target universal-apple-darwin

find src-tauri/target/universal-apple-darwin/release/bundle -type f \( -name '*.app' -o -name '*.dmg' \) -print 2>/dev/null || true
echo "GATE=Developer ID signing and notarization require locally injected Apple credentials."
