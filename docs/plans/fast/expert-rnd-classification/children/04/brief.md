# Fast-P Child Brief — 04

- Child: 04
- Plan: docs/plans/2026-08-24/04-expert-rnd-incremental-classification.md
- Plan identity: commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a
- Depends on: 01,02,03 (plan 前置: 01~03 完成 + 02 CANDIDATE 回填/抽样通过)
- Base: bad5a164fa0ea9ed4a41ae5f1871fd083cac932b (child 03 terminal code head)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification

## Global constraints (binding, from master plan docs/plans/2026-08-24/00-expert-rnd-classification-master.md)

1. JDK 11 mandatory. Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command; bare mvn fails the build on newer JDKs.
2. Master invariants M-2 and M-6 bind this child: classification semantics ONLY in `ExpertClassificationService` (scheduler constructs a fixed backfill request, never re-derives); incremental schedule default OFF — release/startup must not trigger classification writes.
3. Child invariants I4-1..I4-5 (plan) bind: default disabled and zero startup side effects; fixed CANDIDATE/EXECUTE/rnd-v1-2026/onlyPending=true request only (no RAW/APPLICATION/force); shares EXPERT_CLASSIFICATION_BACKFILL taskType + ExpertClassificationBackfillService + expertClassificationExecutor with the manual path (lock failure -> skip log, no queued second task); bounded run (batchSize 500/100..1000, delayMs 250/0..5000, maxDocsPerRun 50000/1..200000, cap reached = SUCCESS with remaining, not failure); conservative under-recall for same-version enrichment updates (no script query comparing ES fields).
4. Full regression gate: `JAVA_HOME=... mvn test` must end `BUILD SUCCESS` with `Tests run: N, Failures: 0, Errors: 0` (baseline 2739 green at master base; child 03 head 2822 green).
5. Git: commit locally only, exactly one implementation commit with message `feat(fast-p): implement 04`. Never push, merge, rebase, amend, or rewrite history. Exclude fast-p report/log files (docs/plans/fast/) from the implementation commit; the controller commits evidence separately.
6. Serial writer rule (master plan): child 04 is the sole writer now. The runbook `docs/runbooks/expert-classification-backfill.md` was created by child 02 (authorized) — child 04 APPENDS the incremental-enable section (T3); do not rewrite or restructure the child-02 sections.
7. Do not review or repair unrelated behavior. Skip formatters/linters and project-wide suites beyond the required commands.

## Authorized files (6; modify nothing else)

1. src/main/kotlin/com/weibo/talentintroduction/config/ExpertClassificationProperties.kt (NEW, T1)
2. src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt (T1 — register properties in @EnableConfigurationProperties)
3. src/main/resources/application.yml (T1 — default-disabled config block, exact YAML from plan Task 1)
4. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt (NEW, T2)
5. src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationSchedulerTest.kt (NEW, T2)
6. docs/runbooks/expert-classification-backfill.md (T3 — append incremental-enable section only)

## Required commands (run all; from plan 验收标准 + master plan)

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertClassificationSchedulerTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   (full regression; must contain Tests run: N, Failures: 0, Errors: 0; exit 0)
- git diff --check   (clean)

## Downstream interfaces (last child; consumed by runbook and manual acceptance only)

- `ExpertClassificationProperties` bound under `talent-introduction.expert-classification` with env fallbacks: incremental-enabled (default false), incremental-cron (default `0 0 4 * * ?`), batch-size (default 500, range 100..1000), delay-ms (default 250, range 0..5000), max-docs-per-run (default 50000, range 1..200000); validation on construction.
- `ExpertClassificationScheduler` bean `@ConditionalOnProperty(prefix="talent-introduction.expert-classification", name=["incremental-enabled"], havingValue="true")`; `@Scheduled(cron="${talent-introduction.expert-classification.incremental-cron:0 0 4 * * ?}")`; fixed request CANDIDATE/EXECUTE/rnd-v1-2026/onlyPending=true/confirmation=`EXECUTE_CANDIDATE:rnd-v1-2026`; identical tryStartWithToken -> expertClassificationExecutor -> runAndRecordWithResult -> bind -> finally clear pattern as child 02 controller; lock-failure log `incremental classification skipped: task already running`; executor-rejection clears pending token and logs warn.
- Shared-file extraction (if any common boilerplate is needed) must stay within child 02's listed files; no new unlisted files.
- Runbook appendix: enable only after CANDIDATE backfill/sampling/send-gate acceptance passed; verify task history before/after enabling; state clearly that the automatic task does not process RAW, does not recompute same-version enrichment updates, and UNKNOWN stays unsendable.

## Plan text (exact approved content; authoritative)

Read the committed plan file: docs/plans/2026-08-24/04-expert-rnd-incremental-classification.md
Follow its 需求描述 / 关键不变量 I4-1..I4-5 / 现状审计 / 实现方案 T1-T3 / 变更文件清单 / 验收标准 exactly. The exact YAML block, property ranges, scheduler request fields, and runbook additions are normative.
