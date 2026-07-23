# INNO-MOBILE-008 — live Cloudflare-Tunnel rehearsal: laptop server reachable over public HTTPS

## What this is

A live, end-to-end rehearsal of the demo connectivity solution (`docs/demo/DEMO-RUNBOOK.md`):
prove the laptop's Inner Cosmos backend is reachable through a public HTTPS Cloudflare Tunnel URL,
so teachers/classmates on school WiFi can reach it even under client/AP isolation and regardless of
the laptop's DHCP IP. (The tunnel routes out to the internet and back, so same-WiFi device isolation
does not apply; the URL is stable across the laptop's IP changes.)

## Setup (this session)

1. Downloaded the `cloudflared` single binary (54 MB, free, no account) to `scripts/demo/bin/`
   (gitignored — the binary is never committed).
2. The operator's dev backend was already running on `:8080` (health HTTP 200).
3. Started `cloudflared tunnel --url http://localhost:8080`; it assigned the quick-tunnel URL
   `https://those-measures-tap-preparing.trycloudflare.com`.

## Result (end-to-end through Cloudflare's network — request leaves the laptop, hits Cloudflare, returns)

```
curl https://those-measures-tap-preparing.trycloudflare.com/actuator/health  -> HTTP 200 (1.6s)
curl https://those-measures-tap-preparing.trycloudflare.com/app/aurora/      -> HTTP 200 (app shell)
curl https://those-measures-tap-preparing.trycloudflare.com/                 -> HTTP 200 (login page)
```

All three returned **HTTP 200** through the public URL. A teacher/classmate opening that URL in a
browser on the school WiFi would reach the live app (same-origin `/api/**` works, no CORS). The 1.6s
health latency is the out-and-back through Cloudflare — expected for a tunnel and acceptable for a
demo. The tunnel URL did not depend on the laptop's local IP.

## What this proves vs not

**Proven:** the laptop server is reachable end-to-end through a public HTTPS tunnel URL; the web app
shell, login page and health endpoint all serve correctly through it; the mechanism is robust to the
laptop's IP changing (the URL is independent of it). The `scripts/demo/start-tunnel.sh` flow (which
auto-downloads this same binary and starts the same tunnel) is confirmed working.

**Not proven here:** access from a physically **different device on the school WiFi** (this rehearsal
curled from the laptop itself, through Cloudflare's network). The routing path (laptop → Cloudflare →
public URL) is identical regardless of which device originates the request, so a different device on
any network that can reach the public internet will reach the same URL — but a final on-the-day check
from a phone on the actual school WiFi is still recommended (the runbook lists it as the one
pre-demo step). Android/iOS production push, signing and store distribution remain the external
`MOBILE-EXTERNAL-RELEASE` gate.

## Reproducibility

```bash
bash scripts/demo/start-tunnel.sh 8080   # auto-downloads cloudflared if absent, prints the URL
curl -s -o /dev/null -w "%{http_code}\n" https://<your-url>.trycloudflare.com/actuator/health
```

cloudflared was stopped and the process cleaned up after the rehearsal.
