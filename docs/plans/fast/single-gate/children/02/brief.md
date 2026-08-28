# Child 02 Brief — 旧首发链路改为显式配置研发类型

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate (branch `fast/single-gate`)
- Child base SHA: cec6ce15ba3b41a6bf76e70eae503cdc5a925560
- Execution report: docs/plans/fast/single-gate/children/02/execution.md

## Approved Contract

Exact approved child plan: `docs/plans/2026-08-28/02-legacy-outreach-explicit-types.md`
(same directory in this worktree; read it in full first — it is the complete approved contract).
Master plan (context): `docs/plans/2026-08-28/00-single-gate-master.md`.

## Global Constraints (master plan)

- **M-1**: 不引入任何新的隐式门禁；本计划把旧首发链路从 `sendable` 硬门禁切到显式配置的类型集合。
- **M-3**: `ExpertClassificationService` 一行不改。
- 本计划零前端文件。

## Authorized Files (exactly these 7; nothing else)

1. `src/main/kotlin/.../config/MailSchedulingProperties.kt` — Task 1 尾部加字段 `initialOutreachExpertTypes: List<String> = emptyList()`
2. `src/main/resources/application.yml` — `initial-outreach-expert-types: ${MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES:}` 一行
3. `src/test/resources/application.yml` — 同步同一行
4. `src/main/kotlin/.../expert/service/ExpertSearchService.kt` — 新增 `searchExpertsByTypesWithEmail`（不删旧方法）
5. `src/main/kotlin/.../campaign/service/InitialOutreachService.kt` — Task 3 切换
6. `src/test/kotlin/.../expert/service/ExpertSearchServiceTest.kt` — Task 4 第 1~3 条
7. `src/test/kotlin/.../campaign/service/InitialOutreachServiceTest.kt` — Task 4 第 5~9 条（15 处 stub 换到新方法）

**禁止**: 删 `expertSendableFilter()` / `searchSendableExpertsWithEmail`（子计划 04 删）；
动 `ManualInitialOutreachService`；改 `expertTypesFilter` / `expertTypePredicate`；任何前端文件。

## Key Invariants (from plan; must hold after implementation)

- **I2-1**: 配置默认值两层都为空（Kotlin `emptyList()`、yml `${...:}` 冒号后无内容），禁止代码兜底具体类型。
- **I2-2**: `sendInitialBatch` 取目标前 `require(types.isNotEmpty())`，消息含 `initial-outreach-expert-types`。
- **I2-3**: 合法性判定用 `ExpertSearchService.ALLOWED_EXPERT_TYPES`，禁止手写六值名单。
- **I2-4**: 新查询方法 filter 恰好两项：`exists email` + `expertTypesFilter(types)`，不得混入其他条件。
- **I2-5**: 发送前内存门禁与查询同口径：`UNCLASSIFIED` = 分类对象/类型为 null。

## Downstream Interfaces (for child 04)

- 02 之后 `searchSendableExpertsWithEmail` 必须零生产调用点（`grep -rn "searchSendableExpertsWithEmail" src/main` 零命中）
  —— 04 才能删它。`expertSendableFilter()` 函数保留但 InitialOutreachService 不再调用
  （`grep -rn "expertSendableFilter" src/main/kotlin/.../campaign/service/InitialOutreachService.kt` 零输出）。
- `searchExpertsByTypesWithEmail(size, level, expertTypes)` 签名固定：`(Int, ExpertIndexLevel, List<String>)`。

## Required Commands (must run; JDK 11 zulu required)

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='ExpertSearchServiceTest,InitialOutreachServiceTest'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

Full-suite `mvn test` regression required before READY_FOR_VERIFICATION
(baseline at de228e1: 2952 Kotlin tests green, 755 JS tests green).

## Working Notes

- Plan line numbers may drift; **symbol/identifier names are authoritative**.
- Worktree contains untracked fast-p docs (`docs/plans/fast/`, `docs/runbooks/`) — never commit them; commit only the authorized files.
- Do not run the ops/deploy checklist (CP-2 related `ssh` commands are human/deploy-time actions, not yours).

## Deliverable

Implementation commit message: `feat(fast-p): implement child 02`.
Full result (files changed, commands + exit codes, test counts, deviations) to the execution report path.
