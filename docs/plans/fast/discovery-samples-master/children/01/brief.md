# Child 01 — Approved implementation brief

- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-samples-master
- Target branch: fast/discovery-samples-master
- Child product base: 3efc192faf043badc1ef27bef124a71d3120c4c5
- Approved plan: docs/plans/2026-10-18/discovery-samples-01-store.md
- Plan identity: sha256:34c941c52e1c85ba139b0c94a677743acd90333948913399fc14a582c512fc7b
- Approval: explicit user /fast-p docs/plans/2026-10-18/discovery-samples-master.md invocation.
- Amendments: none.

## Complete contract
Read the entire exact child plan from disk, then the master plan, discovery-samples-contracts.md (C-1 through C-6), and discovery-samples-code-audit.md at the same plan directory. These unchanged artifacts are authoritative; the brief does not relax any acceptance criterion.

## Authorized Files
- src/main/kotlin/com/weibo/talentintroduction/config/DiscoverySampleProperties.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleModels.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleStore.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleCapture.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleStoreTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleCaptureTest.kt

## Commands and baseline
JDK 11 is mandatory: set JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home.
Before product edits run mvn -DskipTests -DskipNodeTests=true test-compile and record the baseline command exit/counts in execution.md. Then implement; skip intermediate build/lint/formatter runs, except the plan's necessary failing-before behavior check. Run the full required command after implementation:
`mvn -Dtest=DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DskipNodeTests=true -Dsample.acceptanceDir=/tmp/discovery-sample-acceptance test`.
The acceptanceDir switch exercises real store/capture through the planned tests and exports all required case JSON/packages, without production services. Record observed elapsed times and resource limits; no zero-overhead claims.

## Invariants and downstream interfaces
Exact contracts C-1..C-6, child I-1..I-3, master I-1..I-3. Preserve PaperMetadata, PaperAuthor, EmailExtractionOutcome and DiscoveryIdentity definitions. Default disabled does no IO/log/cleanup; business delegate executes once and preserves return/exception identity; ordinary ThreadLocal with finally restoration; HostBusy thrown OR hostBusyInScope suppresses samples. Strict configured/hard bounds, no partial/oversized samples, on-disk quotas survive restart, private permissions, no symlink traversal, try locks and atomic move only, scrub URI credentials/query/fragment without changing authors/results. No network/business DB/ES writes.
Child 02 will use capture(context,paper,delegate), nullable optional dependency in existing service, and static no-op current-scope hooks for attempts/headers/full bytes/end. Child 03 will reuse immutable schemaVersion=1 models and read canonical packages. Implement these exact planned seams, not call-site integration or replay.

## Evidence and Git authority
Use execute-p for this exact child plan. Local implementation commit authorized: feat(fast-p): implement 01. Commit only the six authorized files, never plans or fast-p evidence. Append the full execute-p result and baseline/final command evidence to execution.md; do not replace its heading or prior entries. Do not modify verify-log.md or ledger.md. No whole-system review, push, merge, rewrite, unrelated fixes, later-child work, or new unauthorized files. Return READY_FOR_VERIFICATION, BLOCKED, or PLAN_CONFLICT, with commit and report path. If the plan requires unlisted files or new behavioral authority, stop and record it.
