#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then set -a; source .env; set +a; fi
: "${DB_PASSWORD:?Configura DB_PASSWORD en .env o en el entorno}"
exec java ${JAVA_OPTS:-} -jar target/inventario-1.0.0.jar
