# PUBLIC-DEMO-001

## Claim

On 2026-07-24 the Windows laptop ran Inner Cosmos as a publicly reachable classroom server through
a Cloudflare Quick Tunnel. The verifier used newly registered, non-seeded users and drove the
complete core product trajectory over the public HTTPS origin.

## Verified trajectory

Command:

```powershell
.\scripts\demo\verify-public-demo.ps1 `
  -Origin "https://daniel-miller-instructors-january.trycloudflare.com"
```

Result:

- public health: `UP`;
- landing page and APK download: `PASS`;
- two fresh users registered;
- each user discovered the other;
- friend request, acceptance and bidirectional friend visibility: `PASS`;
- private group create, invite, accept and two-member view: `PASS`;
- real Aurora response length: `1753`;
- conversation finish produced one memory card;
- owner previewed, compiled and published a consent-bound capsule;
- the second user discovered the published capsule;
- capsule conversation returned a non-empty response longer than the verifier's minimum;
- slow letter entered durable status `SENT` and appeared in the visitor outbox.

The public APK was downloaded again and hashed independently. It matched the emulator-tested local
APK exactly:

```text
19d4489b07313925e7423e7d9fd736a7253c4537c1a27d03c87dda86319aa61a
```

## Stack under proof

- PostgreSQL 16 + pgvector + Flyway;
- Redis sessions, rate limiting, scheduler leases, idempotency and Aurora stream state;
- JDBC transactional outbox;
- real DeepSeek chat;
- real Qwen embedding and TTS;
- Spring Boot served through a localhost-bound port and outbound Cloudflare HTTPS tunnel;
- no Demo seed users or capsules.

## Reproduction

```powershell
.\scripts\demo\run-public-demo.ps1
.\scripts\demo\status-public-demo.ps1
```

The URL above is an ephemeral evidence snapshot. Every fresh Quick Tunnel produces a different
origin; `run-public-demo.ps1` therefore rebuilds the APK against the new origin before publishing
it.

## Honest boundary

This proves the machine-executable public Demo path. A physical Android sideload and a second
human's subjective product review remain final classroom rehearsals, not missing application code.
