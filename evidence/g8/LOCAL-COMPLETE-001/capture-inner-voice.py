#!/usr/bin/env python3
"""Capture the inner_voice SSE event from the Aurora turn stream."""
import urllib3, requests, json, sys, time
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
BASE="https://localhost:8443"
s=requests.Session(); s.verify=False
def csrf():
    r=s.get(f"{BASE}/api/auth/csrf",timeout=15);d=r.json()["data"];return d["token"],d["headerName"]
def post(p,**kw):
    t,h=csrf();kw.setdefault("headers",{});kw["headers"][h]=t;kw["headers"]["Content-Type"]="application/json"
    kw.setdefault("timeout",150);return s.post(f"{BASE}{p}",**kw)

t,h=csrf()
u=f"iv_{int(time.time())}"
post("/api/auth/register",json={"username":u,"password":"lc-Passw0rd!2026","nickname":"IV","email":f"{u}@l.t"})
sc=post("/api/dialog/session/create",json={"title":"iv","sessionType":"AURORA_CHAT"})
sid=sc.json()["data"]["id"]
print("user/session:",u,sid)

# Emotionally rich message more likely to route through the dual kernel + compose inner-voice.
msg="最近一直觉得自己不够好，工作里犯了个错被领导指出来了，虽然同事说没关系，但我心里很自责，晚上也睡不好。"
for attempt in range(2):
    st=post("/api/aurora/stream-stage",json={"sessionId":sid,"message":msg,"mode":"SOCRATIC"})
    tok=st.json().get("data",{}).get("token")
    events=[]
    inner_payload=None
    try:
        with s.get(f"{BASE}/api/aurora/stream",timeout=180,
                   params={"sessionId":sid,"message":msg,"mode":"SOCRATIC","token":tok or ""},
                   stream=True) as ss:
            for raw in ss.iter_lines(decode_unicode=True):
                if not raw: continue
                if raw.startswith("event:"):
                    events.append(raw.split(":",1)[1].strip())
                elif raw.startswith("data:") and events and events[-1]=="inner_voice":
                    inner_payload=raw[5:].strip()[:200]
    except Exception as e:
        print("stream err",e)
    print(f"attempt {attempt+1} events:",events)
    if "inner_voice" in events:
        print("INNER_VOICE captured:",inner_payload)
        break
    msg="我真的很怕让大家失望，这种压力让我喘不过气，又不知道该跟谁说。"
print("done")
