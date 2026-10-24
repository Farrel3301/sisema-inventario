#!/usr/bin/env bash
# Adapta el proxy HTTPS del entorno cloud a Maven sin desactivar TLS.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .cache
if [[ -x /workspace/jdk-21/bin/javac ]]; then export JAVA_HOME=/workspace/jdk-21; fi
if [[ -f /etc/ssl/certs/java/cacerts ]]; then
 export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
python3 - <<'PY'
import os, urllib.parse
from pathlib import Path
from xml.sax.saxutils import escape
p=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',os.environ.get('https_proxy','')))
settings='<settings>'
if p.hostname:
    settings+='<proxies><proxy><id>cloud</id><active>true</active><protocol>http</protocol><host>'+escape(p.hostname)+'</host><port>'+str(p.port or 80)+'</port><nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy></proxies>'
settings+='</settings>'
Path('.cache/settings.xml').write_text(settings)
PY
exec ./mvnw -s .cache/settings.xml "$@"
