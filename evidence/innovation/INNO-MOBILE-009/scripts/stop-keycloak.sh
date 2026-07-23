#!/usr/bin/env bash
# Stop the local Keycloak started by start-keycloak.sh.
set -euo pipefail
NAME="${IC_KEYCLOAK_CONTAINER:-ic-keycloak-w2}"
if docker ps -a --format '{{.Names}}' | grep -qx "$NAME"; then
  docker rm -f "$NAME" >/dev/null
  echo "removed container ${NAME}"
else
  echo "no container ${NAME}"
fi
