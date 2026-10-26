#!/usr/bin/env bash
set -euo pipefail
umask 077
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
java_home="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home}"
if [[ -z "${REPLAY_CLASSPATH:-}" ]]; then
  [[ -f "$root/target/classes/com/weibo/talentintroduction/discovery/service/DiscoveryHistoricalReplayTool.class" ]] || { printf '%s\n' 'Compile the approved revision with JDK 11 before replay.' >&2; exit 2; }
  temporary="$(mktemp -d "${TMPDIR:-/tmp}/discovery-replay-cp.XXXXXXXX")"
  trap 'rm -rf -- "$temporary"' EXIT
  (cd -- "$root" && JAVA_HOME="$java_home" mvn -q dependency:build-classpath -DincludeScope=runtime "-Dmdep.outputFile=$temporary/classpath")
  REPLAY_CLASSPATH="$root/target/classes:$(cat -- "$temporary/classpath")"
fi
# Arguments are passed literally; no shell SQL, configuration dump or credential echo.
"$java_home/bin/java" -cp "$REPLAY_CLASSPATH" com.weibo.talentintroduction.discovery.service.DiscoveryHistoricalReplayTool "$@"
