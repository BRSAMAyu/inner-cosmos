#!/usr/bin/env bash
# INNO-MOBILE-009 — reproducible local Keycloak for the PKCE round-trip.
# Mirrors the prior session's realm/client (deploy/compose/keycloak/inner-cosmos-realm.json)
# but runs Keycloak standalone on the host-reachable hostname http://localhost:8081, so the
# authorize/token/JWKS endpoints can be driven from the host without an Android emulator
# (10.0.2.2 is only meaningful inside the emulator).
#
# Realm: inner-cosmos | public PKCE client: inner-cosmos-mobile-local | S256 required
# Redirect URI: innercosmos://oauth/callback  (the app-owned deep link the client validates)
# Demo user: mobile-demo (see realm JSON; throwaway dev cred).
set -euo pipefail

NAME="${IC_KEYCLOAK_CONTAINER:-ic-keycloak-w2}"
HOST_PORT="${IC_KEYCLOAK_PORT:-8081}"
REALM_FILE="$(cd "$(dirname "$0")/../../../../deploy/compose/keycloak" && pwd -W)/inner-cosmos-realm.json"

if [ ! -f "$REALM_FILE" ]; then
  echo "realm file not found: $REALM_FILE" >&2
  exit 1
fi

docker rm -f "$NAME" >/dev/null 2>&1 || true
echo "starting Keycloak 26.7.0 on 127.0.0.1:${HOST_PORT} (hostname=http://localhost:${HOST_PORT}) ..."
docker run -d --name "$NAME" \
  -p "127.0.0.1:${HOST_PORT}:8080" \
  -v "${REALM_FILE}:/opt/keycloak/data/import/inner-cosmos-realm.json:ro" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=local-admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=test-only-mobile-local-admin \
  quay.io/keycloak/keycloak:26.7.0 \
  start-dev --import-realm --hostname="http://localhost:${HOST_PORT}" --health-enabled=true >/dev/null

echo "waiting for Keycloak to serve the 'inner-cosmos' realm discovery doc ..."
# Keycloak's /health/ready lives on the management port 9000 (not exposed here); the most
# reliable host-side readiness signal is the realm's OIDC discovery doc on the main port.
for i in $(seq 1 120); do
  if curl -fsS -m 5 "http://localhost:${HOST_PORT}/realms/inner-cosmos/.well-known/openid-configuration" >/dev/null 2>&1; then
    echo "Keycloak ready (realm 'inner-cosmos' discovery confirmed) after ${i}s"
    docker ps --filter "name=${NAME}" --format 'container={{.Names}} image={{.Image}} ports={{.Ports}} status={{.Status}}'
    exit 0
  fi
  sleep 1
done
echo "Keycloak did not become ready in 90s; container logs:" >&2
docker logs "$NAME" | tail -40 >&2 || true
exit 1
