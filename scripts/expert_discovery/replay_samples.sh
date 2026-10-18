#!/usr/bin/env bash
set -euo pipefail

repo="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
cd -- "$repo"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 11 installation}"
java="$JAVA_HOME/bin/java"
[[ -x "$java" ]] || { printf '%s\n' 'JDK11_REQUIRED' >&2; exit 64; }
version="$("$java" -version 2>&1)"
[[ "$version" =~ version\ \"11[\".] ]] || { printf '%s\n' 'JDK11_REQUIRED' >&2; exit 64; }
# clean is allowed only in an independent analysis worktree, never the primary checkout.
gitdir="$(git rev-parse --absolute-git-dir)"
common="$(git rev-parse --path-format=absolute --git-common-dir)"
branch="$(git symbolic-ref --quiet --short HEAD)"
[[ "$gitdir" != "$common" && "$branch" != main && "$branch" != master ]] || {
    printf '%s\n' 'INDEPENDENT_ANALYSIS_WORKTREE_REQUIRED' >&2; exit 64;
}
lock="$gitdir/discovery-sample-replay.lock"
mkdir -- "$lock" 2>/dev/null || { printf '%s\n' 'REPLAY_ALREADY_RUNNING' >&2; exit 2; }
classpath_file=''
cleanup() {
    if [[ -n "$classpath_file" ]]; then rm -f -- "$classpath_file"; fi
    rmdir -- "$lock"
}
trap cleanup EXIT
classpath_file="$(mktemp "${TMPDIR:-/tmp}/discovery-replay-classpath.XXXXXX")"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -DskipTests -DskipNodeTests=true clean test-compile dependency:build-classpath -Dmdep.includeScope=test "-Dmdep.outputFile=$classpath_file"
classpath="$(cat -- "$classpath_file")"
"$java" "-Dreplay.repository=$repo" -Dreplay.cleanBuild=true -cp "$repo/target/test-classes:$repo/target/classes:$classpath" \
    com.weibo.talentintroduction.discovery.sample.DiscoverySampleReplay "$@"
