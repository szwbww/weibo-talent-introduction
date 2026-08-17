# Fast-P Child Brief — 05 (epoch 2, amended)

- Child: 05
- Plan: docs/plans/2026-08-16/expert-reachability-05-filter-seams.md
- Plan identity: commit:e9badbbd347dd12fdb6a65c6c3a9191763ecaefd  (amended per ledger amendment A3, human-approved)
- Depends on: 03
- Base: 8530af46bdf0b6575a607645392e12a2bfbdc3e6 (child 04 terminal Code head)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability

## Resume state (epoch 1 -> epoch 2)

Epoch 1 implementer (Reachability05Implementer) completed T1-T5 and COMMITTED the 7 authorized files as f5025fcfd2d98d16f55c1cf79d55bf12c24ad4b6 (`feat(fast-p): implement 05`), but full-suite gate fails solely on `OperatorStatusWriteSeamGuardTest` (3 stale EXCLUDED_NOISE_SITES pins). Read the epoch-1 execution report at docs/plans/fast/expert-reachability/children/05/execution.md FIRST.

Amendment A3 (approved) authorizes the 8th file: src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt — apply ONLY the line-number pin syncs 94→95 and 484→485 (ExpertIndexController.kt) and 431→476 (ExpertSearchService.kt), contexts unchanged (per guard maintenance doc :130 and A1/A2 precedent). Do not touch any assertion semantics of that guard.

This is epoch-2 FIX ROUND 1: apply the A3 pin sync as a separate fix commit `fix(fast-p): repair 05 round 1`, then the full suite must be green.

## Global constraints (binding, from master plan docs/plans/2026-08-16/expert-reachability-00-execution-order.md)

1. JDK 11 mandatory. Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command; bare mvn fails the build on newer JDKs.
2. Master plan shared invariants I-1..I-6 apply (child-specific I-5-1..I-5-4 in the child plan). UNKNOWN = `must_not exists` / `isNullOrBlank()` (I-5-2, I-2). Expression authority only in `ExpertSearchService.reachabilityFilter()` (I-5-1). Empty/unspecified adds nothing (I-5-4, N-1).
3. Four construction points delegate to the authority; no self-held term/terms/must_not expressions (R-6 architecture).
4. `ALLOWED_HAS_FIELDS` untouched (N-3). Existing filter items/order in the four points untouched (N-1).
5. Full regression gate: `JAVA_HOME=... mvn test` must end `BUILD SUCCESS` with `Tests run: N, Failures: 0, Errors: 0` (baseline at master base: 2456/0/0/4).
6. Git: commit locally only, exactly one implementation commit with message `feat(fast-p): implement 05`. Never push, merge, rebase, amend, or rewrite history. Exclude fast-p report/log files (docs/plans/fast/) from the implementation commit; the controller commits evidence separately.
7. Do not review or implement later children (06). Do not repair unrelated behavior. Skip formatters/linters and project-wide suites beyond the required commands.

## Authorized files (8; modify nothing else)

1. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt (T1/T3)
2. src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt (T2)
3. src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt (T3)
4. src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt (T3)
5. src/main/resources/static/index.html (T4)
6. src/main/resources/static/app.js (T4)
7. src/test/kotlin/com/weibo/talentintroduction/expert/service/ReachabilityFilterSeamTest.kt (NEW, T5)
8. src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt (A3: pins 94→95, 484→485, 431→476; contexts unchanged)

## Required commands (run all; from plan 验证命令 + master plan 验证命令)

- node --check src/main/resources/static/app.js
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ReachabilityFilterSeamTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   (full regression; must contain Tests run: N, Failures: 0, Errors: 0; exit 0)
- git diff --check   (clean)

## Downstream interfaces (contract child 06 consumes)

- `ExpertSearchService.reachabilityFilter(mode: String?): Map<String, Any>?` — modes: HIGH_ONLY / EXCLUDE_BLOCKED / UNKNOWN_ONLY / BLOCKED_ONLY; null/empty -> null; illegal value -> IllegalArgumentException via require(...). `ALLOWED_REACHABILITY_MODES` companion constant (single source of truth; child 06 validation reuses it).
- `RecipientScope.reachabilityFilter: String?` (default null) + `matchesExpert` reachability segment after operatorStatuses segment.
- `buildExpertFilters(reachability: String? = null, ...)` + `searchExperts` param + `listExperts` `@RequestParam` — frontend select id `expertReachabilityFilter` wired in 4 app.js sync points (read 4669 block, active-check 11420, reset list 11435, read 11685).
- Child 06 wires config -> `RecipientScope.reachabilityFilter` at resolveScope; it does NOT reimplement the expression.

## Plan text (exact approved content; authoritative)

Read the committed plan file: docs/plans/2026-08-16/expert-reachability-05-filter-seams.md
Follow its 需求描述 / 关键不变量 I-5-1..I-5-4 / 现状审计 (four construction points R-6, RecipientScope fields, filter control 4 sync points) / 实现方案 T1-T5 / 变更文件清单 / 验证命令 / 验收标准 exactly.
