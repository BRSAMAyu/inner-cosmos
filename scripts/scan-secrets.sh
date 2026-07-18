#!/usr/bin/env bash
# Portable secret scan — bash/ripgrep mirror of scripts/scan-secrets.ps1, for machines
# without PowerShell (e.g. macOS dev). Working-tree scan only; CI (java-baseline.yml) still
# covers full git history. Never prints a matched value — only "path [rule]".
#
#   ./scripts/scan-secrets.sh        # scan the working tree, exit 1 on any finding
#
# Rules and allowlist are kept in lockstep with scan-secrets.ps1.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

TOKEN_RE='(sk-[A-Za-z0-9_-]{16,}|AIza[0-9A-Za-z_-]{20,}|gh[pousr]_[0-9A-Za-z]{20,}|AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16})'
ASSIGN_RE='(api[-_]?key|client[-_]?secret|secret[-_]?access[-_]?key|session[-_]?token|password)[[:blank:]]*[:=][[:blank:]]*["'"'"']?[A-Za-z0-9+/_=-]{16,}'
ALLOW_RE='(?i)(test-only|placeholder|example|configured|not-configured|your_|change_me|redacted|removed_from_history|\$\{|<)'

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

# Rule 1: known-token-prefix (all files).
{ rg --hidden --no-heading -oN -e "$TOKEN_RE" -g '!target/**' -g '!.git/**' 2>/dev/null || true; } | \
while IFS= read -r line; do
  file="${line%%:*}"; val="${line#*:}"
  printf '%s' "$val" | rg -qiP "$ALLOW_RE" && continue
  echo "$file [known-token-prefix]" >> "$tmp"
done

# Rule 2: literal-sensitive-assignment (skip test sources, matching the ps1 exclusion).
{ rg --hidden --no-heading -oNi -e "$ASSIGN_RE" -g '!target/**' -g '!.git/**' -g '!src/test/java/**' 2>/dev/null || true; } | \
while IFS= read -r line; do
  file="${line%%:*}"; val="${line#*:}"
  printf '%s' "$val" | rg -qiP "$ALLOW_RE" && continue
  echo "$file [literal-sensitive-assignment]" >> "$tmp"
done

if [ -s "$tmp" ]; then
  n="$(sort -u "$tmp" | wc -l | tr -d ' ')"
  echo "Secret scan FAILED: $n redacted finding(s)."
  sort -u "$tmp" | sed 's/^/  /'
  exit 1
fi
echo "Secret scan PASS: 0 findings (values are never printed)."
