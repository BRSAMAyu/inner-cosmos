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
if [ -z "$QWEN_KEY" ] || [ -z "$GLM_KEY" ]; then
  echo "ERROR: could not find the qwen: and glm: lines in $KEYS." >&2
  exit 1
fi
echo "Loaded keys: qwen(len=${#QWEN_KEY}) glm(len=${#GLM_KEY}) — values not printed."

# Point embedding + TTS at this account's private Aliyun/DashScope gateway (from API及文档.txt).
export MEMORY_EMBEDDING_ENABLED=true
export MEMORY_EMBEDDING_API_KEY="$QWEN_KEY"
export MEMORY_EMBEDDING_BASE_URL="https://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"
export MEMORY_EMBEDDING_MODEL="text-embedding-v4"
export TTS_ENABLED=true
export TTS_API_KEY="$QWEN_KEY"
export TTS_WS_URL="wss://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference"

# Real GLM chat provider (the project's default failover chain), dev profile (H2), on the chosen port.
export LLM_MODE=dev
export LLM_PROVIDER=glm
export GLM_API_KEY="$GLM_KEY"

# JAVA_HOME for the Maven wrapper on Windows.
if [ -z "${JAVA_HOME:-}" ] && [ -d "/c/Program Files/Java/jdk-21.0.10" ]; then
  export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"
fi

echo "Starting Inner Cosmos on :${PORT} with LLM_PROVIDER=glm + real embedding + real TTS..."
echo "Health check:  http://localhost:${PORT}/actuator/health"
echo "App:           http://localhost:${PORT}/app/aurora/"
echo "(Keep this terminal open. Stop with Ctrl+C.)"
exec ./mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=${PORT}"
