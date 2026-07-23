# G8.LOCAL-COMPLETE-001 — reproducible local-complete profile exercise

Gate `G8.LOCAL-COMPLETE` ("a reproducible local-complete profile runs the full AI and
product experience with PostgreSQL/pgvector, Redis, real-provider configuration, and core
E2E journeys"). Driven from a clean checkout of `codex/w4-local-complete` @ `16a3322`,
Docker 28.5.1. All provider credentials are process-env-only (read at runtime from the
gitignored `API及文档.txt`); `scripts/scan-secrets.ps1` = **0 findings** after the run.

## What ran (env / role set)

- `deploy/compose/local-complete.yml` unmodified in intent — `prod,local-complete` profiles,
  runtime role `all` (default), single `app` service + TLS Postgres/pgvector + TLS Redis +
  nginx HTTPS edge (127.0.0.1:8443). `tls-init` generates a 2-day local CA + per-service certs.
- Real providers, env-injected per process (keys redacted):
  - Chat: `LLM_PROVIDER=deepseek`, `LLM_API_KEY=sk-…`, `DEEPSEEK_MODEL=deepseek-chat`
    (INNO-EVAL-005 proven-clean dual-kernel provider).
  - Embedding: `MEMORY_EMBEDDING_ENABLED=true`, `MEMORY_EMBEDDING_API_KEY=sk-ws-…`
    (Qwen/DashScope), `MEMORY_EMBEDDING_BASE_URL=https://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`,
    `MEMORY_EMBEDDING_MODEL=text-embedding-v4`, `…DIMENSIONS=1536` (INNO-INNER-012).
  - TTS: `TTS_ENABLED=true`, `TTS_API_KEY=sk-ws-…`,
    `TTS_WS_URL=wss://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference`
    (INNO-INNER-013, `qwen-audio-3.0-tts-flash`).
  - DB/Redis passwords: fixed literals (env-only).
- OIDC IdP: a **real local Keycloak 26.7.0** on HTTPS `https://localhost:8444/realms/inner-cosmos`
  (realm + public PKCE client imported from `deploy/compose/keycloak/inner-cosmos-realm.json`,
  self-signed cert with SAN `DNS:localhost,DNS:keycloak`). The prod guard (`ProductionStartupGuard`,
  role `all`) requires OIDC HTTPS endpoints — this satisfies it with a genuine running IdP whose
  discovery + JWKS are reachable (see below). The product E2E is driven over the **session-auth**
  path (register/CSRF are `permitAll`; OIDC is the resource server for bearer tokens only), which is
  the SPA/browser contract — the bearer-token PKCE round-trip itself is INNO-MOBILE-009's scope.

Bring-up (env sourced from a temp file outside the repo):

```bash
set -a && . /tmp/ic-local-complete.env && set +a
docker compose -f deploy/compose/local-complete.yml up -d   # tls-init -> pg/redis -> app -> edge
# plus a separate: docker run … quay.io/keycloak/keycloak:26.7.0 start-dev --import-realm \
#   --hostname=https://localhost:8444 --https-certificate-file=… --https-certificate-key-file=…
```

## Proven (real observed outputs)

- **Boot / guard**: profiles `prod,local-complete` active; Flyway applied **23 migrations** on
  PostgreSQL 16.12 with `sslmode=verify-full`; `LlmClient for provider: deepseek, mode: prod,
  fallbackAllowed: false`; `Started InnerCosmosApplication in ~11s`; no ERROR/WARN. Health
  `{"status":"UP"}` via `https://localhost:8443/actuator/health`.
- **pgvector real**: `vector 0.8.1`; `tb_memory_embedding.embedding_vector vector(1536)`.
- **OIDC IdP real**: `https://localhost:8444/realms/inner-cosmos/.well-known/openid-configuration`
  → issuer/jwks/auth/token HTTPS; JWKS returns 2 RSA keys.
- **Core E2E** (`run-e2e.py`, `e2e-outputs.json`): CSRF → register (user id, 200) → `/current` (200)
  → dialog session create (200) → **real DeepSeek greeting** ("晚上好呀～这个点还没休息？") →
  **real DeepSeek message turn** ("听起来今天虽然累，但那种帮同事解决棘手bug之后的满足感…")
  → **settle 200** (no longer a 400 — see fix 1) → memory_card written with DeepSeek-extracted
  title "帮同事解决bug的成就感", summary, memory_type `work`.
- **Real pgvector embedding write + retrieval**: `MemoryEmbeddingRebuildJob` log
  `selected=1, indexed=1, failed=0, remaining=0`; row `model=text-embedding-v4, version=2026-01,
  dimensions=1536, has_vector=t`; `vector_dims(embedding_vector)=1536`,
  vector `[-0.02993033,-0.05778953,0.026343742,…]`; cosine retrieval `1 - (v <=> q) = 1.0000`.
- **Real Qwen TTS**: `/api/me/tts/preview` 200, ~**54 426 bytes** of MP3 (voice `warm_gentle_female`).
- **Inner-voice path** (`capture-inner-voice.py`, `inner-voice-evidence.json`): the Aurora turn
  SSE stream emitted `[…, meta, turn.completed, inner_voice, done]`; the `inner_voice` event
  carried composed text "听他这么自责，我心里一紧，但最要紧的是让他自己选择如何面对。" **and** a
  synthesized `audio/mpeg` data-URI of ~**87 026 bytes** (real Qwen-Audio-TTS inline synthesis).

## Defects found and fixed (source)

1. **Structured-output parser rejected real DeepSeek field names** —
   `StructuredOutputParser` used a default `ObjectMapper` (`FAIL_ON_UNKNOWN_PROPERTIES=true`), so
   DeepSeek's `MEMORY_SETTLEMENT` output (`eventCards[].title`) threw `UnrecognizedPropertyException`,
   returning null → settle 400 → no memory, no embedding. Fixed by configuring
   `FAIL_ON_UNKNOWN_PROPERTIES=false` and adding `@JsonAlias("title")` on
   `StructuredAiResults.Event.eventTitle`. TDD: new
   `StructuredOutputParserTest#toleratesDeepSeekSettlementFieldNamesAndExtraFields` (red→green);
   `StructuredAiServiceBadOutputTest` still green.
2. **local-complete compose silently dropped embedding/TTS env** —
   `deploy/compose/local-complete.yml` forwarded `LLM_*` and `OIDC_*` but not `MEMORY_EMBEDDING_*` /
   `TTS_*`, so an operator's keys never reached the container → `memory.embedding.enabled=false`
   (the rebuild job bean was never registered → no pgvector row) and `tts provider is not
   configured`. Fixed by forwarding those vars (full-experience defaults ON; the app fail-closes if a
   key is missing while enabled). Verified: job now registers + indexes, TTS preview now 200.

## Known boundary (not fixed in this run)

In `prod` + Spring Session Redis, the `HttpSessionCsrfTokenRepository` CSRF token is **not stable
across requests** (3 consecutive `GET /api/auth/csrf` with the same session cookie return 3 different
tokens; the auth attribute persists, so the session itself is fine). The SPA already masks this via
its 403→refetch→retry (`web/src/api.ts:620`), so the product works, but every unsafe request can pay
an extra round-trip. Recommended fix (own TDD cycle, security-sensitive): switch the prod CSRF store
to `CookieCsrfTokenRepository`, or set Spring Session `save-mode: always`. Driven around here by
reading `/api/auth/csrf` immediately before each unsafe request.

## Cleanup

Stack brought down (`docker compose down`) and the Keycloak container removed before reporting;
Docker left clean. No merges/pushes; branch `codex/w4-local-complete` only.
