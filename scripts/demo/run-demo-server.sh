#!/usr/bin/env bash
# Demo: start the Inner Cosmos backend on the laptop with REAL providers (GLM chat + Qwen embedding
# + Qwen TTS), reading keys at runtime from the operator's gitignored API及文档.txt. Never writes a
# key into any committed file. Use this for the in-class demo so the experience runs on real models.
#
# Usage: bash scripts/demo/run-demo-server.sh [PORT]   (default 8080)
# Env:   DEMO_PORT, JAVA_HOME (defaults to C:\Program Files\Java\jdk-21.0.10 on Windows)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
PORT="${DEMO_PORT:-${1:-8080}}"
KEYS="$ROOT/API及文档.txt"

if [ ! -f "$KEYS" ]; then
  echo "ERROR: $KEYS not found. Put the operator's real keys there (it is gitignored)." >&2
  exit 1
fi

# Extract keys without echoing the values (length only, for confirmation).
QWEN_KEY="$(grep '^qwen:' "$KEYS" | head -1 | cut -d: -f2-)"
GLM_KEY="$(grep -i '^glm' "$KEYS" | head -1 | sed -E 's/^[Gg][Ll][Mm][:：] *//')"
DS_KEY="$(grep -i 'deepseek' "$KEYS" | head -1 | sed -E 's/.*apikey[:：] *//')"
if [ -z "$QWEN_KEY" ]; then
  echo "ERROR: could not find the qwen: line in $KEYS (needed for embedding + TTS)." >&2
  exit 1
fi
# Chat provider: DeepSeek by default (proven 0 schema-drift on this HEAD, see
# evidence/innovation/INNO-EVAL-005). GLM (glm-4-flash) is available but exhibits ~3 first-attempt
# JSON-parse failures per dual-kernel run, which can surface deterministic fallback text instead of
# the model's real words -- so it is NOT the default for a demo. Override with DEMO_PROVIDER=glm.
DEMO_PROVIDER="${DEMO_PROVIDER:-deepseek}"
case "$DEMO_PROVIDER" in
  deepseek)
    if [ -z "$DS_KEY" ]; then echo "ERROR: DEMO_PROVIDER=deepseek but no deepseek key in $KEYS." >&2; exit 1; fi
    CHAT_PROVIDER="deepseek"; CHAT_KEY="$DS_KEY" ;;
  glm)
    if [ -z "$GLM_KEY" ]; then echo "ERROR: DEMO_PROVIDER=glm but no glm: line in $KEYS." >&2; exit 1; fi
    CHAT_PROVIDER="glm"; CHAT_KEY="$GLM_KEY"
    echo "WARNING: GLM has known schema-drift (~3 fallbacks/run); DeepSeek is the recommended demo provider." >&2 ;;
  *) echo "ERROR: DEMO_PROVIDER must be deepseek or glm (got: $DEMO_PROVIDER)." >&2; exit 1 ;;
esac
echo "Loaded keys: qwen(len=${#QWEN_KEY}) ${DEMO_PROVIDER}(len=${#CHAT_KEY}) — values not printed."

# Point embedding + TTS at this account's private Aliyun/DashScope gateway (from API及文档.txt).
export MEMORY_EMBEDDING_ENABLED=true
export MEMORY_EMBEDDING_API_KEY="$QWEN_KEY"
export MEMORY_EMBEDDING_BASE_URL="https://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"
export MEMORY_EMBEDDING_MODEL="text-embedding-v4"
export TTS_ENABLED=true
export TTS_API_KEY="$QWEN_KEY"
export TTS_WS_URL="wss://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference"

# Real chat provider (DeepSeek by default; GLM optional via DEMO_PROVIDER=glm), dev profile (H2).
export LLM_MODE=dev
export LLM_PROVIDER="$CHAT_PROVIDER"
export GLM_API_KEY="$GLM_KEY"          # present in env regardless; only used if LLM_PROVIDER=glm
export DEEPSEEK_API_KEY="$DS_KEY"      # likewise; only used if LLM_PROVIDER=deepseek
export LLM_ALLOW_FALLBACK=false        # a demo must show the REAL provider, never silently fall back to Mock

# --- Public-tunnel hardening (2026-07-24 8-agent delivery-readiness audit, P1-4/P1-5/P1-7) ---
# The whole point of this script is to be reachable over a public Cloudflare Tunnel (see
# docs/demo/DEMO-RUNBOOK.md), so it must not run with plain-localhost-only defaults.
export COOKIE_SECURE=true                                    # tunnel is HTTPS end-to-end; never ship an insecure cookie publicly
export COOKIE_SAME_SITE=none                                 # the demo APK's WebView origin (https://localhost) is cross-site from
                                                               # the tunnel origin; SameSite=Lax silently drops the session cookie there
export INNER_COSMOS_SECURITY_TRUSTED_PROXY_ENABLED=true       # honor X-Forwarded-For from cloudflared so rate limits are per-visitor,
                                                               # not one shared bucket for every judge hitting the tunnel from localhost
export MANAGEMENT_HEALTH_REDIS_ENABLED=false                  # this demo path never starts Redis; without this the health
                                                               # endpoint falsely reports DOWN even though the app works fine
# Never combine this demo path with SPRING_PROFILES_ACTIVE=demo/mysql or SEED_ENABLED=true: those
# profiles default seed-enabled:true, which would put a hardcoded admin/admin123 account on the
# public tunnel. This script intentionally never sets either.

# JAVA_HOME for the Maven wrapper on Windows.
if [ -z "${JAVA_HOME:-}" ] && [ -d "/c/Program Files/Java/jdk-21.0.10" ]; then
  export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"
fi

echo "Starting Inner Cosmos on :${PORT} with LLM_PROVIDER=${DEMO_PROVIDER} + real embedding + real TTS..."
echo "Health check:  http://localhost:${PORT}/actuator/health"
echo "App:           http://localhost:${PORT}/app/aurora/"
echo "(Keep this terminal open. Stop with Ctrl+C.)"
exec ./mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=${PORT}"
