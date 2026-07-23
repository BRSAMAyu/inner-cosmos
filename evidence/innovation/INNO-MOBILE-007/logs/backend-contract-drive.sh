#!/usr/bin/env bash
# Real backend contract drive against the Mock dev instance on :8082 (in-memory H2, no Redis, no keys).
# All JSON bodies are loaded via --data-binary @file so the Chinese payloads are not mangled by the
# shell. This is the exact journey the desktop/mobile shell drives through the api.ts client.
set -u
BASE="http://127.0.0.1:8082"
D="$(pwd)/evidence/innovation/INNO-MOBILE-007/logs"
JAR="$D/cookies.txt"; LOG="$D/backend-contract-drive.log"; SSE="$D/aurora-sse-raw.txt"; BOD="$D/bodies"
pyget(){ python -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['headerName']+'|'+d['token'])"; }
echo "=== register journey_user_1 ===" | tee "$LOG"
C=$(curl -s -b "$JAR" -c "$JAR" "$BASE/api/auth/csrf"); HT=$(echo "$C"|pyget); H="${HT%%|*}"; T="${HT##*|}"
echo "csrf header=$H token=${T:0:12}..." | tee -a "$LOG"
REG=$(curl -s -b "$JAR" -c "$JAR" -X POST "$BASE/api/auth/register" -H "Content-Type: application/json; charset=UTF-8" -H "$H: $T" --data-binary @"$BOD/register.json")
echo "register=$REG" | tee -a "$LOG"
C=$(curl -s -b "$JAR" -c "$JAR" "$BASE/api/auth/csrf"); HT=$(echo "$C"|pyget); H="${HT%%|*}"; T="${HT##*|}"
SID=$(curl -s -b "$JAR" -c "$JAR" -X POST "$BASE/api/dialog/session/create" -H "Content-Type: application/json; charset=UTF-8" -H "$H: $T" --data-binary @"$BOD/session-create.json" | python -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
echo "sessionId=$SID" | tee -a "$LOG"
sed "s/__SID__/$SID/" "$BOD/stage.json" > "$BOD/stage-resolved.json"
C=$(curl -s -b "$JAR" -c "$JAR" "$BASE/api/auth/csrf"); HT=$(echo "$C"|pyget); H="${HT%%|*}"; T="${HT##*|}"
STAGE=$(curl -s -b "$JAR" -c "$JAR" -X POST "$BASE/api/aurora/stream-stage" -H "Content-Type: application/json; charset=UTF-8" -H "$H: $T" --data-binary @"$BOD/stage-resolved.json")
echo "stream-stage=$STAGE" | tee -a "$LOG"
TOKEN=$(echo "$STAGE" | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
echo "stageToken len=${#TOKEN}" | tee -a "$LOG"
echo "=== SSE stream (raw events, 35s budget) ===" | tee -a "$LOG"
curl -s -N -b "$JAR" --max-time 35 "$BASE/api/aurora/stream?sessionId=$SID&message=hello&token=$TOKEN" | sed -e 's/\r$//' > "$SSE"
echo "--- SSE bytes: $(wc -c < "$SSE") ---" | tee -a "$LOG"
echo "--- distinct SSE event types (event: lines) ---" | tee -a "$LOG"
grep -E "^event:" "$SSE" | sort | uniq -c | tee -a "$LOG"
echo "--- inner_voice occurrences in stream ---" | tee -a "$LOG"
grep -c "inner_voice" "$SSE" 2>/dev/null | tee -a "$LOG"
echo "--- bubble / turn terminal signals ---" | tee -a "$LOG"
grep -oE '"(type|event|status)":"[^"]+"' "$SSE" | sort | uniq -c | head -20 | tee -a "$LOG"
echo "=== drive complete ===" | tee -a "$LOG"
