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

This boots Spring Boot on **:8080** with the **real GLM** chat provider + **real Qwen embedding** +
**real Qwen TTS** (keys read from `API及文档.txt`), dev H2 DB, Mock disabled for the configured
providers. Wait for `Started InnerCosmosApplication`. Health: `http://localhost:8080/actuator/health`.

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

- **Website visitors**: open `<that-url>/app/aurora/` in any browser → register → full experience.
  The web app is served same-origin by Spring, so no CORS or extra config. This is the easiest path
  for most of the class.
- **App downloaders**: build a demo APK pointed at that URL (step 3).

### Stable URL across restarts (optional, for "smooth and easy")

A free quick-tunnel URL is random and changes if you restart cloudflared. For a **stable, memorable
URL** (e.g. `https://inner-cosmos-demo.your-domain.com`) that survives restarts and IP changes:

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

- Backend health green, and a real Aurora reply (the run script logs the provider; it must be GLM,
  not Mock fallback).
- From **another device** (phone on school WiFi), open the tunnel URL → register → send a message →
  see a real streamed reply + (if you enabled TTS) hear the inner voice.
- The demo APK installs and reaches the same URL.

## Troubleshooting

- **Can't reach the laptop IP directly from another device** → the school WiFi has AP isolation. Use
  the **tunnel** (step 2) or the **hotspot** (step 4); both bypass AP isolation.
- **App refuses to connect / "not HTTPS"** → you're serving plain HTTP. Use the tunnel (HTTPS) or
  the demo APK (which permits cleartext for the demo). Production builds require HTTPS by design.
- **Tunnel URL changed after a restart** → re-share it (free tier) or set up a named tunnel (step 2,
  stable-URL section).
- **"Mock" replies instead of real LLM** → the provider key wasn't read; confirm `API及文档.txt` is at
  the repo root and `run-demo-server.sh` printed `LLM_PROVIDER=glm`.
