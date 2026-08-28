# Child 04 Brief — 删除 sendable / 版本门禁，研发类型成为唯一收口点

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate (branch `fast/single-gate`)
- Child base SHA: bc8a93762cca39c2542d79d1f3801589b6e4e155
- Execution report: docs/plans/fast/single-gate/children/04/execution.md

## Approved Contract

Exact approved child plan: `docs/plans/2026-08-28/04-single-gate-remove-sendable.md`
(same directory in this worktree; read it in full first — it is the complete approved contract).
Master plan (context): `docs/plans/2026-08-28/00-single-gate-master.md`.

## Global Constraints (master plan)

- **M-1**: INTRODUCTION 发信目标判定**有且只有**研发类型集合一个过滤条件；删除所有基于
  `expertClassification` 的其他判定（`sendable`、策略版本、硬编码名单）。
- **M-2**: 空集合 = 发给零个人（fail-closed），不是「不限」。
- **M-3**: `ExpertClassificationService` 唯一允许改动 = 删除 `ACCEPTED_CLASSIFICATION_VERSIONS`。
- 子计划 02 已把旧首发链路切到显式类型配置（`searchSendableExpertsWithEmail` 零生产调用点）；
  子计划 03 已保证存量配置非空（V109）。本计划的前提全部满足。

## Authorized Files (exactly these 8; nothing else)

1. `src/main/kotlin/.../expert/service/ExpertSearchService.kt` — 删 `expertSendableFilter`；加 `MATCH_NONE_FILTER`；删 `searchSendableExpertsWithEmail`
2. `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` — `buildEsFiltersForLevel` 尾段替换 + 发送前门禁改为 `scope.matchesExpertType`
3. `src/main/kotlin/.../campaign/domain/BatchExecutionModels.kt` — 删硬门禁块；抽 `matchesExpertType`；label 改文案
4. `src/main/kotlin/.../expert/service/ExpertClassificationService.kt` — **只**删 `ACCEPTED_CLASSIFICATION_VERSIONS` 及其注释
5. `src/test/kotlin/.../expert/service/ExpertSearchServiceTest.kt` — 删 2 个旧方法用例 + 新增 MATCH_NONE_FILTER 断言
6. `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` — Task 5 第 4~8 条（含 :4150 label 断言同步）
7. `src/test/kotlin/.../expert/service/ExpertClassificationVersionGateGuardTest.kt` — 新增第二个守卫用例（sendable 读取白名单 = 4 文件）
8. `src/test/kotlin/.../campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` — Task 5 第 10 条（ES/内存同口径断言）

**禁止**: 动 `ExpertClassification.kt`、`ExpertIndexWriterService.kt`、`ExpertClassificationBackfillService.kt`、
`ExpertIndexController.kt`（属子计划 05）；任何前端文件；`expertTypesFilter` / `expertTypePredicate` 函数体；
`EXPERT_NOT_SENDABLE` 常量名与字符串值。

## Key Invariants (from plan; must hold after implementation)

- **I4-1**: INTRODUCTION 判定只读 `expertClassification.type`；禁止读 `sendable` / `.version` / 硬编码名单。
- **I4-2**: 空集合 → ES 侧追加 `MATCH_NONE_FILTER`（恒不命中），内存侧返回 false。
- **I4-3**: `expertTypesFilter` / `expertTypePredicate` 逐字不改；fail-closed 在调用点表达。
- **I4-4**: Kotlin 侧类型判定唯一实现 `RecipientScope.matchesExpertType(profile)`，两处共用。
- **I4-5**: ES 与内存口径一致（`UNCLASSIFIED` = 类型字段不存在 / null）。
- **I4-6**: `ExpertClassificationService.kt` 除删 `ACCEPTED_CLASSIFICATION_VERSIONS` 外零改动（`VERSION` 保留）。

## Downstream Interfaces (for child 05)

- 04 之后 `sendable` 的读取只允许出现在 4 个文件：
  `ExpertClassification.kt`（派生属性定义）、`ExpertIndexWriterService.kt`（序列化）、
  `ExpertClassificationBackfillService.kt`（统计）、`ExpertIndexController.kt`（API DTO）。
  `ExpertSearchService.kt` / `BatchExecutionModels.kt` / `ManualInitialOutreachService.kt`
  出现在 sendable 守卫命中集合即失败。
- 机器判据：`grep -rn "expertSendableFilter\|ACCEPTED_CLASSIFICATION_VERSIONS" src/main/kotlin` 零输出；
  `grep -rn "searchSendableExpertsWithEmail" src/main src/test` 仅剩被删除前的引用清理。

## Required Commands (must run; JDK 11 zulu required)

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='ExpertSearchServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest,ExpertClassificationVersionGateGuardTest'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertClassificationServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

Full-suite `mvn test` regression required before READY_FOR_VERIFICATION
(baseline: after children 01-03, 2974 Kotlin + 755 JS green).

## Working Notes

- Plan line numbers may drift; **symbol/identifier names are authoritative**.
- Worktree contains untracked fast-p docs (`docs/plans/fast/`, `docs/runbooks/`) — never commit them; commit only the authorized files.
- This is the semantic flip point (M-2): empty expertTypes = send to nobody. The old empty=unrestricted semantics must be gone from INTRODUCTION paths only; list-page query filter (empty = unrestricted) stays as-is.
- `EXPERT_NOT_SENDABLE` label change: `:183` label from 「专家非生产/科研可发类型」to「研发类型不在本次选择范围内」; string value unchanged; sync test assertion at ManualInitialOutreachServiceTest ~:4150.

## Deliverable

Implementation commit message: `feat(fast-p): implement child 04`.
Full result (files changed, commands + exit codes, test counts, deviations) to the execution report path.
