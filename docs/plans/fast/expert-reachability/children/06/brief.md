# Fast-P Child Brief — 06

- Child: 06
- Plan: docs/plans/2026-08-16/expert-reachability-06-batch-config.md
- Plan identity: commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b
- Depends on: 05
- Base: child 05 terminal Code head (set at dispatch time)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability

## Global constraints (binding, from master plan docs/plans/2026-08-16/expert-reachability-00-execution-order.md)

1. JDK 11 mandatory. Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command; bare mvn fails the build on newer JDKs.
2. Master plan shared invariants I-1..I-6 apply (child-specific I-6-1..I-6-5 in the child plan). Default = 不过滤 (null/empty; I-6-5). Legacy typed adapter MUST explicitly preserve the new column (I-6-1; K-batch-config-legacy-adapter-field-preservation). Migration must not contain `${` (I-6-3; K-flyway-placeholder-replacement).
3. Validation reuses `ExpertSearchService.ALLOWED_REACHABILITY_MODES` — no second string set in BatchSendTaskConfigService (I-6-4).
4. `toView()` + three `*Fields()` get the new column; `toLegacyConfig()` and updateLegacyConfig return construction do NOT (I-6-2). `BatchSendConfig` (KV data class) untouched (N-3).
5. `gateFilterEnabled` all 12 front-end touch roles (R-13) replicated for the new field; behavior of the 12 unchanged (N-4).
6. Full regression gate: `JAVA_HOME=... mvn test` must end `BUILD SUCCESS` with `Tests run: N, Failures: 0, Errors: 0` (baseline at master base: 2456/0/0/4).
7. Migration integration test requires local Docker; if Docker is unavailable record the skipped result and note it (do not fake a pass).
8. Git: commit locally only, exactly one implementation commit with message `feat(fast-p): implement 06`. Never push, merge, rebase, amend, or rewrite history. Exclude fast-p report/log files (docs/plans/fast/) from the implementation commit; the controller commits evidence separately.
9. Do not repair unrelated behavior. Skip formatters/linters and project-wide suites beyond the required commands.

## Authorized files (8; modify nothing else)

1. src/main/resources/db/migration/V100__add_reachability_filter_to_batch_send_task_config.sql (NEW, T1)
2. src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt (T2)
3. src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt (T2/T3/T4/T5)
4. src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt (T6)
5. src/main/resources/static/index.html (T7)
6. src/main/resources/static/app.js (T7)
7. src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigReachabilityTest.kt (NEW, T8)
8. src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt (T8 addition)

## Required commands (run all; from plan 验证命令 + master plan 验证命令)

- node --check src/main/resources/static/app.js
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigReachabilityTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true   (needs Docker; record actual result)
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   (full regression; must contain Tests run: N, Failures: 0, Errors: 0; exit 0)
- git diff --check   (clean)

## Downstream interfaces

- V100 adds `reachability_filter VARCHAR(32) NULL` to `batch_send_task_config` (NULL = 不过滤). Must be V100 — do NOT reuse existing migration numbers.
- `BatchSendTaskConfig.reachabilityFilter: String? = null` + `BatchSendTaskConfigUpdateCommand` same (defaulted).
- `resolveScope` passes `reachabilityFilter = config.reachabilityFilter` into `RecipientScope` for config-driven paths only (O-2: independent manual run paths do not pass it).
- Frontend: 3-option select (不过滤 / EXCLUDE_BLOCKED / HIGH_ONLY) in both editors (index.html:1244 and :1441 areas), pill `可达性 · <label>` via `.batch-gate-pill` reuse (only when non-empty), change-log value formatting branch for key `reachabilityFilter`.

## Plan text (exact approved content; authoritative)

Read the committed plan file: docs/plans/2026-08-16/expert-reachability-06-batch-config.md
Follow its 需求描述 / 关键不变量 I-6-1..I-6-5 / 现状审计 (R-11 migration version, R-12 preservation lines, R-13 12 touch roles) / 实现方案 T1-T8 / 变更文件清单 / 验证命令 / 验收标准 / 样式契约 S-6-1 S-6-2 exactly.
