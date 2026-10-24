#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# El runtime base trae Java 21, pero no el JDK completo (ct.sym/javac).
if [[ ! -x /workspace/jdk-21/bin/javac ]]; then
 image='eclipse-temurin:21-jdk@sha256:23cc5734ff46e776f51ce66eb9ee08444fd47caabec0a95b7eb6e94be2d3696d'
 docker pull "$image"
 container=$(docker create "$image")
 trap 'docker rm "$container" >/dev/null 2>&1 || true' EXIT
 docker cp "$container":/opt/java/openjdk /workspace/jdk-21
 docker rm "$container" >/dev/null
 trap - EXIT
fi
python3 - <<'PY'
from pathlib import Path
import secrets
p=Path('.env')
if not p.exists():
    p.write_text('DB_PASSWORD='+secrets.token_urlsafe(24)+'\nMYSQL_ROOT_PASSWORD='+secrets.token_urlsafe(24)+'\nADMIN_EMAIL=admin@example.com\nADMIN_PASSWORD='+secrets.token_urlsafe(24)+'\n')
    p.chmod(0o600)
PY
docker compose pull db
./scripts/cloud-maven.sh verify
npm ci --ignore-scripts --cache /workspace/.npm
