#!/bin/sh
set -eu
BASE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CACHE="${MAVEN_USER_HOME:-$BASE/.cache/maven}"
VERSION=3.9.11
MAVEN="$CACHE/apache-maven-$VERSION/bin/mvn"
if [ ! -x "$MAVEN" ]; then
 mkdir -p "$CACHE"
 ARCHIVE="$CACHE/maven.tar.gz"
 curl -fSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$VERSION/apache-maven-$VERSION-bin.tar.gz" -o "$ARCHIVE"
 printf '%s  %s\n' 'bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978' "$ARCHIVE" | sha512sum -c -
 tar -xzf "$ARCHIVE" -C "$CACHE"
 rm "$ARCHIVE"
fi
exec "$MAVEN" "-Dmaven.repo.local=${MAVEN_REPO_LOCAL:-$BASE/.cache/repository}" "$@"
