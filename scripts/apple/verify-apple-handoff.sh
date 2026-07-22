#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
plutil -lint "$ROOT/web/ios/App/App/Info.plist"
plutil -lint "$ROOT/web/ios/App/App/App.entitlements"
plutil -lint "$ROOT/web/src-tauri/Info.plist"
plutil -lint "$ROOT/web/src-tauri/Entitlements.plist"
grep -q 'innercosmos' "$ROOT/web/ios/App/App/Info.plist"
grep -q 'applinks:app.innercosmos.sg' "$ROOT/web/ios/App/App/App.entitlements"
grep -q 'capacitorDidRegisterForRemoteNotifications' "$ROOT/web/ios/App/App/AppDelegate.swift"
echo "APPLE_HANDOFF_STATIC_PASS"
