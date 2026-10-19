#!/usr/bin/env bash
set -euo pipefail
umask 077
: "${JAVA_HOME:?Set JAVA_HOME explicitly to JDK 11}"
java="$JAVA_HOME/bin/java"
[[ -x "$java" ]] || { printf '%s\n' JDK11_REQUIRED >&2; exit 64; }
version="$("$java" -version 2>&1)"
[[ "$version" =~ version\ \"11[\".] ]] || { printf '%s\n' JDK11_REQUIRED >&2; exit 64; }
# Supply an already-built classpath, or an unpacked WAR's WEB-INF directory. Never build or start Spring here.
if [[ -n "${REPLAY_CLASSPATH:-}" ]]; then
    classpath="$REPLAY_CLASSPATH"
elif [[ -n "${REPLAY_WEBINF:-}" && -d "$REPLAY_WEBINF/classes" && -d "$REPLAY_WEBINF/lib" ]]; then
    classpath="$REPLAY_WEBINF/classes:$REPLAY_WEBINF/lib/*"
else
    printf '%s\n' 'Set REPLAY_CLASSPATH or REPLAY_WEBINF to the approved compiled artifact' >&2
    exit 64
fi
exec "$java" -cp "$classpath" com.weibo.talentintroduction.discovery.service.DiscoveryVerificationReplayTool "$@"
