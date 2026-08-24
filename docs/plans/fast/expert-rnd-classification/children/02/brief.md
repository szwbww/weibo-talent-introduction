# Fast-P Child Brief — 02

- Child: 02
- Plan: docs/plans/2026-08-24/02-expert-rnd-classification-backfill.md
- Plan identity: commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a
- Depends on: 01
- Base: 773527c7ed2ac65d4ae92d0233be82ab7417b1ef (child 01 terminal code head)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification

## Global constraints (binding, from master plan docs/plans/2026-08-24/00-expert-rnd-classification-master.md)

1. JDK 11 mandatory. Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command; bare mvn fails the build on newer JDKs.
2. Master invariants M-2, M-4, M-6 bind this child: classification normalization/evidence/scores/priority/type/sendable come ONLY from `ExpertClassificationService` (backfill calls it, never re-derives); formal backfill is `_bulk update` per ES `_id` with `doc_as_upsert=false` writing ONLY `expertClassification` (no root `updatedAt`, no `_update_by_query`); release must not auto-start backfill — only the explicit admin API (and later the disabled-by-default incremental scheduler) starts it.
3. Child invariants I2-1..I2-6 (plan) bind: DRY_RUN zero writes; EXECUTE local partial update; explicit level/mode/version + exact confirmation `EXECUTE_<LEVEL>:rnd-v1-2026`; cancel/rerun/failure visibility; taskType `EXPERT_CLASSIFICATION_BACKFILL` with `tryStartWithToken` + `runAndRecordWithResult` + token->executionId binding; complete result statistics.
4. Full regression gate: `JAVA_HOME=... mvn test` must end `BUILD SUCCESS` with `Tests run: N, Failures: 0, Errors: 0` (baseline 2739 green at master base; child 01 head 2776 green).
5. Git: commit locally only, exactly one implementation commit with message `feat(fast-p): implement 02`. Never push, merge, rebase, amend, or rewrite history. Exclude fast-p report/log files (docs/plans/fast/) from the implementation commit; the controller commits evidence separately.
6. Serial writer rule (master plan): shared files across children must be modified by one writer at a time; child 02 is the sole writer now. Child 04 appends to `docs/runbooks/expert-classification-backfill.md` later — do not pre-write its section.
7. Do not review or implement later children (03/04). Do not repair unrelated behavior. Skip formatters/linters and project-wide suites beyond the required commands.

## Authorized files (11; modify nothing else)

1. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt (T1)
2. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt (NEW, T2)
3. src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt (NEW, T3)
4. src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt (T3)
5. src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalog.kt (T4)
6. src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt (T1)
7. src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillServiceTest.kt (NEW, T2)
8. src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt (NEW, T3)
9. src/test/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalogTest.kt (NEW, T4)
10. docs/runbooks/expert-classification-backfill.md (NEW, T5)
11. src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt (A2: three catalog inventory-lock pins — hasProgressUi whitelist 6→7, audited task-type set 17→18, total count 17→18; all three add exactly EXPERT_CLASSIFICATION_BACKFILL)

## Required commands (run all; from plan 验收标准 + master plan)

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexWriterServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertClassificationBackfillServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertClassificationAdminControllerTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskTypeCatalogTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   (full regression; must contain Tests run: N, Failures: 0, Errors: 0; exit 0)
- git diff --check   (clean)

## Downstream interfaces (contracts children 03/04 consume)

- `ExpertIndexWriterService.bulkUpdateExpertClassifications(level, updates)`: single classification bulk write entry; `updates` elements are `esDocId + ExpertClassification`; per-call single target index resolved from level; batch cap 1000; NDJSON meta `_index`+`_id`, data exactly `{"doc":{"expertClassification":...},"doc_as_upsert":false}`; returns per-item updated/noop/failure; keeps up to 100 failure samples but counts all.
- `ExpertClassificationBackfillService`: fixed request model (level/mode/version=rnd-v1-2026/batchSize 500 (100..1000)/delayMs 250 (0..5000)/maxDocs null/onlyPending=true/confirmation); onlyPending filter = must_not exists expertClassification.version OR must_not term version=rnd-v1-2026 (should/minimum_should_match=1); uses `searchAfterExpertsFiltered`; DRY_RUN aggregates only, EXECUTE calls writer; cancellable segmented delay (single sleep <=1s); result implements `TaskExecutionSummaryProvider` with successCount=writeSuccess (DRY_RUN: scanned), failureCount=writeFailure, CANCELLED/PARTIAL_SUCCESS/FAILED terminal states; immediate FAILED on missing mapping / first-batch-all-400.
- Controller `POST /api/expert-classification/backfill`: 202 `{"message":"任务已启动","taskType":"EXPERT_CLASSIFICATION_BACKFILL"}`; 409 when running; 401 unauthenticated; no frontend; status/log/cancel reuse `/api/task-progress/EXPERT_CLASSIFICATION_BACKFILL` family; single-thread executor named `expertClassificationExecutor` (added to DiscoveryExecutorConfig).
- `TaskTypeCatalog`: code=`EXPERT_CLASSIFICATION_BACKFILL`, label=`专家研发类型回填`, group=`MANUAL`, metricLabel=`已处理/失败`, summaryRule=null, hasProgressUi=true, drilldown=null; executions API whitelist derived from catalog must include it.
- Runbook `docs/runbooks/expert-classification-backfill.md`: 11 sections as listed in plan Task 5; copyable commands; placeholders `<ADMIN_USERNAME>`/`<ADMIN_PASSWORD>`; no real secrets; no auto-backfill instructions.

## Plan text (exact approved content; authoritative)

Read the committed plan file: docs/plans/2026-08-24/02-expert-rnd-classification-backfill.md
Follow its 需求描述 / 关键不变量 I2-1..I2-6 / 现状审计 / 实现方案 T1-T5 / 变更文件清单 / 验收标准 exactly. Request field defaults, scan filters, endpoint contract, catalog values, and the 11-section runbook outline are normative.

## Amendment A2 (approved 2026-08-24, epoch 2)

Authorized the catalog inventory-lock pin sync above (file 11): `TaskExecutionSummaryExtractorTest.kt` — hasProgressUi whitelist 6→7 (+`EXPERT_CLASSIFICATION_BACKFILL`), audited task-type set 17→18 (+`EXPERT_CLASSIFICATION_BACKFILL`), `entries.size` 17→18. Zero assertion-semantics change. Epoch 1 implementation commit 9d1d9f8 stays; the remaining work is exactly this sync plus a green full regression.
