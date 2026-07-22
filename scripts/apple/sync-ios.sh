#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT/web"
pnpm install --frozen-lockfile
pnpm mobile:sync
test -d ios/App/App.xcodeproj
echo "IOS_PROJECT=$ROOT/web/ios/App/App.xcodeproj"
echo "NEXT=xed '$ROOT/web/ios/App/App.xcodeproj'"
echo "GATE=Select an Apple Development Team, enable Push Notifications when credentials exist, then run on a physical iPhone."
