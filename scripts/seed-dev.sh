#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${NEXO_SEED_PASSWORD:?Define una contraseña de desarrollo para los 5 usuarios ficticios}"
exec ./scripts/run.sh --spring.profiles.active=dev --app.seed.enabled=true --server.port=0
