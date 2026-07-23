# Inner Cosmos — Demo Runbook (laptop as server over school WiFi)

Goal: run the whole product on **your laptop**, and let teachers/classmates **visit the website or
download the app** and use it over the school's WiFi — even though the laptop's IP keeps changing.

The robust path is a **public HTTPS tunnel** (Cloudflare Tunnel). It works **regardless of the
laptop's DHCP IP** and **even if the school WiFi enforces client/AP isolation** (devices on the same
WiFi can't talk to each other directly) — because traffic goes out to the internet and back through
the tunnel. The tunnel also gives a **valid HTTPS** URL, which mobile apps require.

## 0. One-time prep

- JDK 21, Node 22, the repo checked out. (You already have these.)
- Real provider keys in `API及文档.txt` at the repo root (gitignored — never commit it). The
  scripts read the key from there at runtime; it is never written into a committed file.
- `cloudflared` — the tunnel binary. `scripts/demo/start-tunnel.sh` downloads it automatically the
  first time (Windows `.exe` into `scripts/demo/bin/`). No account needed.

## 1. Start the backend (real LLM) on the laptop

```bash
bash scripts/demo/run-demo-server.sh
```

This boots Spring Boot on **:8080** with a **real chat provider** (DeepSeek by default; the script's
own startup line prints exactly which one is active) + **real Qwen embedding** + **real Qwen TTS**
(keys read from `API及文档.txt`), dev H2 DB, Mock disabled for the configured providers. Wait for
`Started InnerCosmosApplication`. Health: `http://localhost:8080/actuator/health`.

> If :8080 is already taken by another instance, set `DEMO_PORT=8086` before running the script and
> use that port in the tunnel command below.

## 2. Expose it over the school WiFi (the tunnel)

```bash
bash scripts/demo/start-tunnel.sh 8080
```

This starts `cloudflared`, prints a public HTTPS URL like
`https://<random-words>.trycloudflare.com`, and keeps it stable **as long as the terminal stays
open**. **That URL does not change when your laptop's IP changes** (DHCP) — the tunnel routes by the
URL, not your IP. Share this URL with the class.

> **Known, accepted exposure (2026-07-24 8-agent audit P2-8):** the free quick-tunnel forwards the
> *entire* `:8080` origin, including `/actuator/metrics/*` and `/actuator/prometheus` (permitAll —
> intentionally, so the real in-cluster Prometheus scraper in the Kubernetes showcase doesn't need
> app-level credentials; NetworkPolicy is the isolation boundary there, not this app). Over the
> public tunnel, anyone with the URL can read JVM/DB-pool/request-timing metrics (no user PII).
> Low-stakes for a course demo, but not zero — **tear the tunnel down (`Ctrl+C`) as soon as grading
> ends** rather than leaving it running indefinitely.

- **Website visitors**: open `<that-url>/app/aurora/` in any browser → register → full experience.
  The web app is served same-origin by Spring, so no CORS or extra config. This is the easiest path
  for most of the class.
- **App downloaders**: build a demo APK pointed at that URL (step 3).

### Stable URL across restarts (do this before an unattended/asynchronous grading window)

A free quick-tunnel URL is random and changes if you restart cloudflared — and any already-built demo
APK, or a URL already shared with judges, silently breaks on that restart with no error pointing at
the cause. This is **optional for a live, supervised, single-session demo** where you control every
restart, but it is **not optional** if grading happens asynchronously or the laptop might sleep/reboot
during the window — set this up first in that case. For a **stable, memorable URL**
(e.g. `https://inner-cosmos-demo.your-domain.com`) that survives restarts and IP changes:

1. Create a free Cloudflare account + add a domain (or a free `.workers.dev` route).
2. `cloudflared tunnel login`, `cloudflared tunnel create inner-cosmos-demo`, route a hostname to it,
   `cloudflared tunnel run inner-cosmos-demo`.
3. Put the stable URL in the demo build (step 3) once; it never needs rebuilding.

For a one-off class demo, the random quick-tunnel URL + re-sharing if it restarts is fine.

## 3. Build the demo Android app pointed at the server

```bash
bash scripts/demo/build-demo-apk.sh https://<your-tunnel-url>.trycloudflare.com
```

This writes a throwaway `.env.demo` (gitignored) with that URL, builds the web bundle in demo mode,
syncs Capacitor, and produces `web/android/app/build/outputs/apk/debug/app-debug.apk`. The tunnel URL
is a **public HTTPS origin**, so the app's existing production URL validator accepts it unchanged —
**no production security is weakened**. Share the APK; classmates install it and it connects to your
laptop through the tunnel.

**Login in the APK**: a `VITE_DEMO_MODE=true` build (this script always sets it) uses the same
username/password login as the website instead of requiring an OIDC identity provider — there is no
Keycloak to stand up for this path (2026-07-24 8-agent audit P0-1). This *does* require
`run-demo-server.sh`'s `COOKIE_SECURE=true` + `COOKIE_SAME_SITE=none` (already set by that script) so
the session cookie survives the cross-origin request from the APK's `https://localhost` WebView
origin to your tunnel origin. **Verify a real logged-in journey (register → chat → see a reply) on an
actual installed APK on a real device before relying on this for grading** — it has not yet been
rehearsed on a physical device over a real network in this repo's evidence.

> If you change the tunnel URL later, rebuild with the new URL. (A runtime server-URL setting inside
> the app — so classmates can re-point without a rebuild — is a tracked follow-up; the baked demo
> build is sufficient for a demo.)

## 4. Fallback: laptop mobile hotspot (if the school blocks outbound tunneling)

If the school WiFi proxies/blocks cloudflared, turn your laptop into a **mobile hotspot**
(Windows: Settings → Network → Mobile hotspot). Classmates join THAT WiFi. Your laptop's IP on the
hotspot is stable (Windows gateway is usually `192.168.137.1`). Then:

- Website: `http://192.168.137.1:8080/app/aurora/` (plain HTTP — fine for a browser).
- App: build with `build-demo-apk.sh http://192.168.137.1:8080` (the demo build allows this; note
  Android needs "cleartext permitted" for HTTP — the demo APK enables it; production builds do not).

The hotspot bypasses the school WiFi entirely (it's your own network), so AP isolation doesn't
apply. Range/capacity is limited by your laptop's hotspot.

## 5. Verify before the demo

- Backend health green, and a real Aurora reply — the run script's startup line prints
  `LLM_PROVIDER=<provider>`; confirm the reply isn't the Mock fallback text.
- From **another device** (phone on school WiFi, not the laptop's own network), open the tunnel URL →
  register → send a message → see a real streamed reply + (if you enabled TTS) hear the inner voice.
  Also try an intentionally mundane, slightly-stressed message (e.g. "我今天有点焦虑，工作压力很大") and
  confirm it gets an ordinary supportive reply, not an emergency-contact/crisis message — a real
  bug reproduced during the 2026-07-24 audit and fixed, but worth a live sanity check before grading.
- The demo APK installs on a **real Android device** (not just the emulator) and completes a full
  register → login → chat journey through the tunnel — this exact path was not yet proven on
  physical hardware as of the 2026-07-24 audit; rehearse it, don't assume it from the code fix alone.
- If grading is asynchronous or unattended, confirm the **stable named tunnel** (section 2) is set up
  and both the backend and `cloudflared` are configured not to die if the laptop sleeps.

## Troubleshooting

- **Can't reach the laptop IP directly from another device** → the school WiFi has AP isolation. Use
  the **tunnel** (step 2) or the **hotspot** (step 4); both bypass AP isolation.
- **App refuses to connect / "not HTTPS"** → you're serving plain HTTP. Use the tunnel (HTTPS) or
  the demo APK (which permits cleartext for the demo). Production builds require HTTPS by design.
- **Tunnel URL changed after a restart** → re-share it (free tier) or set up a named tunnel (step 2,
  stable-URL section).
- **"Mock" replies instead of real LLM** → the provider key wasn't read; confirm `API及文档.txt` is at
  the repo root and check exactly which provider name `run-demo-server.sh`'s startup line printed
  (`LLM_PROVIDER=<deepseek|glm>` — DeepSeek is the current default; it is no longer always GLM).
- **APK login fails / session doesn't persist** → confirm `run-demo-server.sh` is the script that
  started the backend (it sets `COOKIE_SECURE=true` + `COOKIE_SAME_SITE=none`, both required for the
  APK's cross-origin session cookie); a manually-started `spring-boot:run` without those env vars will
  not work for the APK's login even though the website still will.
