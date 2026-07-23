#!/usr/bin/env bash
# Demo: expose the local Inner Cosmos backend to the school WiFi (and the internet) via a free
# Cloudflare Tunnel. Gives a public HTTPS URL that is STABLE regardless of the laptop's DHCP IP, and
# works even if the school WiFi has client/AP isolation (traffic goes out to the internet and back).
# No Cloudflare account required for the quick tunnel.
#
# Usage: bash scripts/demo/start-tunnel.sh [PORT]   (default 8080)
set -euo pipefail

PORT="${1:-8080}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BIN_DIR="$ROOT/scripts/demo/bin"
mkdir -p "$BIN_DIR"

# Locate or download cloudflared (single binary; free; no account).
CF=""
for c in "$BIN_DIR/cloudflared" "$BIN_DIR/cloudflared.exe" cloudflared; do
  if command -v "$c" >/dev/null 2>&1 || [ -x "$c" ]; then CF="$c"; break; fi
done

if [ -z "$CF" ]; then
  echo "cloudflared not found. Downloading the single binary into $BIN_DIR ..."
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
      URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
      curl -L --fail -o "$BIN_DIR/cloudflared.exe" "$URL" && chmod +x "$BIN_DIR/cloudflared.exe"
      CF="$BIN_DIR/cloudflared.exe" ;;
    Darwin)
      URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin.tgz"
      curl -L --fail -o /tmp/cf.tgz "$URL" && tar -xzf /tmp/cf.tgz -C "$BIN_DIR" && chmod +x "$BIN_DIR/cloudflared"
      CF="$BIN_DIR/cloudflared" ;;
    Linux*)
      URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64"
      curl -L --fail -o "$BIN_DIR/cloudflared" "$URL" && chmod +x "$BIN_DIR/cloudflared"
      CF="$BIN_DIR/cloudflared" ;;
    *) echo "Unrecognized OS: $(uname -s). Install cloudflared manually." >&2; exit 1 ;;
  esac
fi

echo "Starting Cloudflare Tunnel -> http://localhost:${PORT} ..."
echo "Look for the line:   |  https://<random-words>.trycloudflare.com  |   <-- share THIS url."
echo "(Keep this terminal open. Stop with Ctrl+C. The URL is stable while this runs and ignores"
echo " your laptop's IP changes.)"
echo
exec "$CF" tunnel --url "http://localhost:${PORT}"
