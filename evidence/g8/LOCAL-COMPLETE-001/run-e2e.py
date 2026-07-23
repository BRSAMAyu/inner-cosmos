#!/usr/bin/env python3
"""
G8.LOCAL-COMPLETE end-to-end driver.
Drives the FULL product journey against the local-complete HTTPS edge
(https://localhost:8443) using the session-auth path (the SPA/browser path),
with real DeepSeek chat + Qwen embedding + Qwen TTS behind it.

CSRF note: AuthController.register() calls httpRequest.changeSessionId(), so the
session token rotates once at register. We re-read /api/auth/csrf right after.
"""
import json, sys, time, urllib3, requests
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

BASE = "https://localhost:8443"
OUT = {}
def save(k, v):
    OUT[k] = v
    sys.stdout.write(f"[{k}] done\n"); sys.stdout.flush()

s = requests.Session()
s.verify = False

def csrf():
    r = s.get(f"{BASE}/api/auth/csrf", timeout=15)
    d = r.json()["data"]
    return d["token"], d["headerName"]

def post(path, **kw):
    # Prod mode (Spring Session Redis) rotates the HttpSession CSRF token on every
    # request, so the token must be read immediately before each unsafe request
    # (same contract the SPA uses on its 403-CSRF retry, web/src/api.ts:620).
    tok, hn = csrf()
    headers = kw.pop("headers", {}) or {}
    headers["Content-Type"] = "application/json"
    headers[hn] = tok
    return s.post(f"{BASE}{path}", timeout=kw.pop("timeout", 120), headers=headers, **kw)

# 1. establish session + register
t1, h = csrf()
u = f"lc_user_{int(time.time())}"
reg = post("/api/auth/register", timeout=20,
           json={"username": u, "password": "lc-Passw0rd!2026",
                 "nickname": "LC Test", "email": f"{u}@local.test"})
save("register", reg.json())
print("register HTTP", reg.status_code)

# 2. confirm auth (session path, not OIDC)
cur = s.get(f"{BASE}/api/auth/current", timeout=15)
save("current", cur.json())
print("current HTTP", cur.status_code, "user=", cur.json().get("data", {}).get("id"))

# 3. Create a dialog session (Aurora conversation)
sc = post("/api/dialog/session/create", timeout=20,
          json={"title": "LC E2E", "sessionType": "AURORA_CHAT"})
save("session_create", sc.json())
sid = sc.json().get("data", {}).get("id")
print("dialog session HTTP", sc.status_code, "| sessionId =", sid)

# 4. Aurora greeting (real DeepSeek) on that session
g = post("/api/aurora/greeting", timeout=120,
         json={"sessionId": sid, "mode": "DAILY_TALK"})
save("greeting", g.json())
print("greeting HTTP", g.status_code)
gd = g.json().get("data", {})
gmsgs = gd.get("messages") or []
print("greeting reply head:", (gmsgs[0] if gmsgs else "")[:160])

# 5. Aurora message turn (real DeepSeek)
msg = "今天工作有点累，但帮同事解决了一个棘手的bug，心里其实挺有成就感的。"
m = post("/api/aurora/message", timeout=120,
         json={"sessionId": sid, "message": msg, "mode": "DAILY_TALK"})
save("message", m.json())
md = m.json().get("data", {})
print("message HTTP", m.status_code, "| reply head:", (md.get("reply") or "")[:200])

# 6. Settle session -> crystallize memories (+ embedding writes via pgvector path)
se = post("/api/aurora/settle", timeout=180, params={"sessionId": sid})
save("settle", se.json())
print("settle HTTP", se.status_code)

# 7. Inner-voice TTS preview (real Qwen DashScope WebSocket synthesis)
voices = s.get(f"{BASE}/api/me/tts/voices", timeout=15)
save("tts_voices", voices.json())
voice_id = voices.json().get("data", {}).get("currentVoiceId") or "warm_gentle_female"
print("tts currentVoiceId =", voice_id)
pv = post("/api/me/tts/preview", timeout=60, json={"voiceId": voice_id})
save("tts_preview_status", pv.status_code)
save("tts_preview_body", pv.json() if pv.status_code != 200 else None)
audio = pv.json().get("data", {}).get("audio", "") if pv.status_code == 200 else ""
save("tts_preview_bytes", (len(audio.split("base64,")[1]) * 3 // 4) if "base64," in audio else 0)
print("tts preview HTTP", pv.status_code, "| audio bytes ~", OUT.get("tts_preview_bytes"))

# 8. Inner-voice SSE path: stage + open the turn stream, capture event names
st = post("/api/aurora/stream-stage", timeout=30,
          json={"sessionId": sid, "message": "睡前想记一笔今天的小成就。", "mode": "SLEEP_REVIEW"})
save("stream_stage", st.json())
tok = st.json().get("data", {}).get("token")
events = []
try:
    with s.get(f"{BASE}/api/aurora/stream", timeout=120,
               params={"sessionId": sid, "message": "睡前想记一笔今天的小成就。",
                       "mode": "SLEEP_REVIEW", "token": tok or ""}, stream=True) as ss:
        for line in ss.iter_lines(decode_unicode=True):
            if line and line.startswith("event:"):
                events.append(line.split(":", 1)[1].strip())
            if len(events) > 40:
                break
except Exception as e:
    save("stream_error", str(e))
save("stream_events", events)
print("stream events seen:", sorted(set(events)))

with open("e2e-outputs.json", "w", encoding="utf-8") as f:
    json.dump(OUT, f, ensure_ascii=False, indent=2)
print("RESULT_JSON=e2e-outputs.json")
