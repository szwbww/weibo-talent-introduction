# Fast-P Child Brief — 03

- Child: 03
- Plan: docs/plans/2026-08-24/03-expert-rnd-send-gate.md
- Plan identity: commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a
- Depends on: 01 (plan 前置: 子计划 01 完成; production send gate additionally requires 02 CANDIDATE backfill per M-6)
- Base: ec7226b485dbfff98a33260e68ef289df3fa1169 (child 02 terminal code head)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification

## Global constraints (binding, from master plan docs/plans/2026-08-24/00-expert-rnd-classification-master.md)

1. JDK 11 mandatory. Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command; bare mvn fails the build on newer JDKs.
2. Master invariants M-1 and M-5 bind this child: INTRODUCTION only passes `expertClassification.sendable == true` (missing/UNKNOWN/mapping error all fail closed, no fallback to old email-exists semantics, no bypass/config off-switch); preview and execution share the same seams (`countBySnapshot`/`countEsTargets`/`fetchEsPage` reuse `buildEsFiltersForLevel`; retry preview+execution reuse `buildRetryableTargets`; MATERIAL_REMINDER does NOT apply the gate).
3. Child invariants I3-1..I3-5 (plan) bind: ES term `expertClassification.sendable=true` + memory predicate `profile.expertClassification?.sendable == true` only for mailType=INTRODUCTION; last-check before delivery in both `InitialOutreachService.sendInitialBatch` and the `ManualInitialOutreachService` round loop; failed gate records `EXPERT_NOT_SENDABLE` skipped reason and creates NO contact/mail_record; MATERIAL_REMINDER zero impact.
4. Full regression gate: `JAVA_HOME=... mvn test` must end `BUILD SUCCESS` with `Tests run: N, Failures: 0, Errors: 0` (baseline 2739 green at master base; child 02 head 2810 green).
5. Git: commit locally only, exactly one implementation commit with message `feat(fast-p): implement 03`. Never push, merge, rebase, amend, or rewrite history. Exclude fast-p report/log files (docs/plans/fast/) from the implementation commit; the controller commits evidence separately.
6. Serial writer rule (master plan): shared files across children must be modified by one writer at a time; child 03 is the sole writer now. `ExpertSearchService.kt` was modified by child 01 (authorized); child 03 extends it (T1).
7. Do not review or implement later children (04). Do not repair unrelated behavior. Skip formatters/linters and project-wide suites beyond the required commands.

## Authorized files (9; modify nothing else)

1. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt (T1)
2. src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt (T2)
3. src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt (T3)
4. src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt (T3, T4)
5. src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt (T1)
6. src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt (T2)
7. src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt (T3, T4)
8. src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt (A3: EXCLUDED_NOISE_SITES ExpertSearchService.kt line 445→491, context unchanged)
9. src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt (A3: expert() fixture helper gains default sendable classification — type=ACADEMIC_RND, sendable=true, version=rnd-v1-2026; no other change)

## Required commands (run all; from plan 验收标准 + master plan)

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=InitialOutreachServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   (full regression; must contain Tests run: N, Failures: 0, Errors: 0; exit 0)
- git diff --check   (clean)

## Downstream interfaces (child 04 consumes)

- `ExpertSearchService.expertSendableFilter()` (companion): returns exactly `mapOf("term" to mapOf("expertClassification.sendable" to true))`.
- `ExpertSearchService.searchSendableExpertsWithEmail(size, level=CANDIDATE)`: filter = `exists email AND expertSendableFilter()`; existing `searchExpertsWithEmail` behavior unchanged.
- `InitialOutreachService.sendInitialBatch`: target query switched to `searchSendableExpertsWithEmail`; forEach first-line re-check `expert.expertClassification?.sendable == true` else skipped++/continue; requested/candidates/sent/failed/skipped semantics unchanged (gate-blocked counts as skipped).
- `RecipientScope.matchesExpert`: mailType==INTRODUCTION → memory sendable check first; MATERIAL_REMINDER skips it.
- `ManualInitialOutreachService.buildEsFiltersForLevel`: INTRODUCTION funnel levels append `expertSendableFilter()` at end of filters; MATERIAL_REMINDER does not.
- `ManualInitialOutreachService` round loop: after iterator returns profile, before email/account/contact handling → re-check sendable; reject → `recordSkipped(EXPERT_NOT_SENDABLE)` + increment processed/roundProcessed/roundRejected; no contact/account/render/delivery.
- `BatchOutcomeReasonCodes.EXPERT_NOT_SENDABLE` with label `专家非生产/科研可发类型`.
- Call topology of `countBySnapshot`/`countEsTargets`/`fetchEsPage`/`buildRetryableTargets` unchanged (shared seams).

## Plan text (exact approved content; authoritative)

Read the committed plan file: docs/plans/2026-08-24/03-expert-rnd-send-gate.md
Follow its 需求描述 / 关键不变量 I3-1..I3-5 / 现状审计 / 实现方案 T1-T4 / 变更文件清单 / 验收标准 exactly. Predicate forms, reason code, last-check placement, and MATERIAL_REMINDER exemptions are normative.

## Amendment A3 (approved 2026-08-24, epoch 2)

Authorized two test-file refreshes (files 8-9): seam guard `ExpertSearchService.kt` pin 445→491 (context unchanged, same mechanism as A1); `BatchSendTaskRuntimeIntegrationTest.expert()` helper gains a default sendable classification (`type=ACADEMIC_RND`, sendable=true, `version=rnd-v1-2026`) so existing scope-filter assertions keep their meaning under the I3-2 gate — the gate's rejection behavior is proven by the authorized `ManualInitialOutreachServiceTest` additions. Zero production-logic change. Epoch 1 implementation commit f3da97af stays; remaining work is exactly these two refreshes plus a green full regression.
