# EXPERIENCE-AXE-AUDIT-001

## Scope

Automated WCAG 2.2 AA and performance regression evidence for the five-space React product. The
tests run all five spaces for:

- day + `zh-CN`;
- night + `zh-CN`;
- day + `en-SG`;
- night + `en-SG`.

This produces 20 axe scans per run. The performance test measures the real Aurora initial journey
from navigation/login to an interactive composer.

## Findings and repairs

The first audit found active-state color contrast failures in five CSS rules. Theme-aware
`--on-accent-strong` and `--on-plum-strong` tokens replaced the unsafe literals.

The final audit was deliberately run against the real public Demo rather than only loopback. It
then found one additional WCAG 2.2 `target-size` failure in every matrix combination:

```text
.boundary-check > input[type="checkbox"]
```

The Echo Capsule boundary controls now provide a 24px checkbox inside a 48px minimum-height clickable
label. The same public tests changed from `4 failed / 1 passed` to `5 passed`.

## Final public-Demo result

Command shape:

```powershell
$env:INNER_COSMOS_BASE_URL = "https://<current-quick-tunnel>.trycloudflare.com"
npx playwright test e2e/accessibility-audit.spec.ts e2e/performance-budget.spec.ts
```

Result:

```text
5 passed
```

Final performance sample:

| Metric | Value |
|---|---:|
| TTFB | 80.1 ms |
| FCP | 316 ms |
| LCP | 524 ms |
| CLS | 0 |
| Approximate TBT | 27 ms |
| Long tasks | 1 |
| Aurora composer interactive | 1580 ms |

The budget remains intentionally loose enough for a classroom network: composer interactive under
15 seconds and CLS under 0.1. These numbers are a regression receipt for one run, not a universal
latency guarantee.

## Other verification on the same integrated tree

- Vitest: `522/522` using one worker;
- TypeScript + production Vite/PWA build: pass;
- Maven: `1195` tests, `0` failures, `0` errors, `1` existing environment-gated skip;
- SpotBugs: `0` findings;
- Secret scan: `0` findings.

## Boundary

Automated axe does not replace TalkBack narration quality, keyboard-only task completion or a
non-author aesthetic review. Those human checks remain honest final rehearsals rather than being
misrepresented as machine-complete.
