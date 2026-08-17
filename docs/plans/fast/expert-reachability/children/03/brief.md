# Fast-P Child Brief — 03 (epoch 2, amended)

- Child: 03
- Plan: docs/plans/2026-08-16/expert-reachability-03-sync-and-backfill.md
- Plan identity: commit:2663ecc9c5644fa3df2cb39e2cf723cf583ed2d2  (amended per ledger amendment A1, human-approved)
- Depends on: 02
- Base: 5396782203892adcc0dc69cc5160a2ec9a21fa6e (child 02 terminal Code head)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability

## Resume state (epoch 1 -> epoch 2)

Epoch 1 implementer (Reachability03Implementer) completed T1-T7 for the 8 authorized files but could NOT commit: full-suite gate failed solely on `OperatorStatusWriteSeamGuardTest` (EXCLUDED_NOISE_SITES stale pins 90/431 vs actual 94/483 in the modified ExpertIndexController.kt). All 8 files are currently modified/untracked and UNCOMMITTED in the worktree; the epoch-1 execution report is at docs/plans/fast/expert-reachability/children/03/execution.md — READ IT FIRST, then review the actual diff (git diff) to confirm the retained work is sound before completing.

Amendment A1 (approved) authorizes the 9th file: src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt — apply ONLY the line-number pin sync 90→94 and 431→483 in EXCLUDED_NOISE_SITES for ExpertIndexController.kt (context strings unchanged, per the guard's own maintenance doc at :130 and same-repo precedent bdf853c). Do not touch any assertion semantics of that guard.

## Global constraints (binding, from master plan docs/plans/2026-08-16/expert-reachability-00-execution-order.md)

1. JDK 11 mandatory. Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command; bare mvn fails the build on newer JDKs.
2. Master plan shared invariants I-1..I-6 apply (see master plan; child-specific invariants I-3-1..I-3-6 are in the child plan).
3. This child writes the `reachability` field (declared in child 02) to CANDIDATE + APPLICATION layers only. UNKNOWN = field removal via script, never `"UNKNOWN"` string (I-2/I-3-1). No `updatedAt` writes in syncReachabilityBatch (IP-5).
4. `syncOperatorStatusBatch()` and `resolveOrcidToDocIds()` in ExpertIndexWriterService stay untouched (N-1). `BulkSyncResult` definition untouched (N-4). `EmailSuppressionService.suppress()` idempotency semantics untouched (N-2).
5. Full regression gate: `JAVA_HOME=... mvn test` must end `BUILD SUCCESS` with `Tests run: N, Failures: 0, Errors: 0` (baseline at master base: 2456/0/0/4).
6. Git: commit locally only, exactly one implementation commit with message `feat(fast-p): implement 03`. Never push, merge, rebase, amend, or rewrite history. Exclude fast-p report/log files (docs/plans/fast/) from the implementation commit; the controller commits evidence separately.
7. Do not review or implement later children (04/05/06). Do not repair unrelated behavior. Skip formatters/linters and project-wide suites beyond the required commands.

## Authorized files (9; modify nothing else)

1. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt (T1)
2. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilitySyncService.kt (NEW, T3/T5)
3. src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt (T4)
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt (T5)
5. src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt (T5)
6. src/main/kotlin/com/weibo/talentintroduction/task/service/MailAutomationScheduler.kt (T6)
7. src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilitySyncServiceTest.kt (NEW, T7)
8. src/test/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionServiceTest.kt (T7 addition)
9. src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt (A1: pin 90→94, 431→483, contexts unchanged)

## Required commands (run all; from plan 验证命令 + master plan 验证命令)

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertReachabilitySyncServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=EmailSuppressionServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexControllerTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   (full regression; must contain Tests run: N, Failures: 0, Errors: 0; exit 0)
- git diff --check   (clean)

## Downstream interfaces (contracts child 04/05 consume)

- `ExpertIndexWriterService.syncReachabilityBatch(updates: List<Pair<String, ExpertReachability?>>): BulkSyncResult` — null value = remove-script; CANDIDATE+APPLICATION only; no updatedAt.
- `ExpertReachabilitySyncService.syncAll(): BulkSyncResult` — scrollExperts(CANDIDATE) driven; checkReachabilityMapping() first, fail-fast IllegalStateException; progressStore updates; cancellation honored.
- `POST /api/experts/sync-reachability` — progressStore tryStartWithToken pattern, 409 when running, 400 on IllegalStateException; taskType EXPERT_REACHABILITY_SYNC.
- Incremental hooks: `markBlockedByEmail(normalizedEmail)` / `markBlockedByContact(contact)` — fail-open (try/catch warn), single-doc BLOCKED writes.
- Daily cron via `MailAutomationScheduler` wrapped in runAndRecordWithResult, gated by talent-introduction.scheduling.enabled.

## Plan text (exact approved content; authoritative)

Read the committed plan file: docs/plans/2026-08-16/expert-reachability-03-sync-and-backfill.md
Follow its 需求描述 / 关键不变量 I-3-1..I-3-6 / 现状审计 (复制源 syncOperatorStatusBatch R-8, 数据源两张 MySQL 表) / 实现方案 T1-T7 / 变更文件清单 / 验证命令 / 验收标准 exactly. 决策：复制而非泛化 (syncOperatorStatusBatch 约 60 行 bulk 样板), resolveOrcidToDocIds 直接复用.
