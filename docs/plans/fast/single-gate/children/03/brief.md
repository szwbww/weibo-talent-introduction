# Child 03 Brief — 研发类型改为必填非空（写侧先行）

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate (branch `fast/single-gate`)
- Child base SHA: 658b60c25370bd8dd974e6a98d6eacc48315943b
- Execution report: docs/plans/fast/single-gate/children/03/execution.md

## Approved Contract

Exact approved child plan: `docs/plans/2026-08-28/03-expert-types-required.md`
(same directory in this worktree; read it in full first — it is the complete approved contract).
Master plan (context): `docs/plans/2026-08-28/00-single-gate-master.md`.

## Global Constraints (master plan)

- **M-3**: `ExpertClassificationService` 一行不改（本计划唯一允许的该文件改动在子计划 04）。
- 本计划**不删除**任何既有门禁（`expertSendableFilter` 与三处内存判定原样保留）——删除是子计划 04。
- `expertTypesFilter()` 契约不变（空集合返回 null）。

## Authorized Files (exactly these 7; nothing else)

1. `src/main/kotlin/.../campaign/service/BatchSendTaskConfigService.kt` — Task 1（INTRODUCTION 非空校验）
2. `src/main/kotlin/.../campaign/service/BatchSendControlService.kt` — Task 2（手动启动非空校验）
3. `src/main/resources/db/migration/V109__require_expert_types_on_batch_send_task_config.sql` — **新建**（I3-3/3-4/3-5）
4. `src/main/resources/static/app.js` — Task 4（默认值 + 提交前校验）
5. `src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt` — Task 5 第 1~3 条
6. `src/test/kotlin/.../campaign/service/BatchSendControlServiceTest.kt` — Task 5 第 4~5 条（无则新建）
7. `src/test/kotlin/.../campaign/repository/V109ExpertTypesMigrationTest.kt` — **新建**，Task 5 第 6 条
   （照 `QaSeedEncodingRepairMigrationTest` 范式，文本断言，不需要 Docker）

**禁止**: 改 `src/main/resources/static/index.html`（零改动）；改 `styles.css`（零改动）；
删任何门禁；改 MATERIAL_REMINDER 路径；改 `expertTypesFilter`；写手写六值名单字面量数组。

## Key Invariants (from plan; must hold after implementation)

- **I3-1**: 非空校验条件 = `mailType == BatchSendType.INTRODUCTION.name`；MATERIAL_REMINDER 空集合仍放行。
- **I3-2**: 两个写入口都校验：`BatchSendTaskConfigService`（保存）+ `BatchSendControlService.validateSnapshotFields`（手动启动）。
- **I3-3**: V109 写入值逐字 `["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]`。
- **I3-4**: V109 `UPDATE ... WHERE expert_types_json IS NULL OR expert_types_json = '' OR expert_types_json = '[]'`。
- **I3-5**: V109 正文不含 `${`。
- **I3-6**: 前端默认值三个字面量 = `batchExpertTypeOptions()` 的 value 逐字一致；不手写六值名单。
- **S3-1**: `styles.css` 与 `index.html` diff 均为空；错误提示文案逐字 `请至少选择一个研发类型`，
  走 `showStatus(<msg>, "error")`，无 inline style、无新 class、无新 DOM 节点。

## Downstream Interfaces (for child 04)

- 04 上线时全部存量配置必须非空（V109 保证）。本计划不得改变读侧语义
  （`RecipientScope.expertTypes` 空集合仍 = 不限，翻转是 04 的事）。
- 新迁移版本号 V109 必须唯一（现状库内最新为 V108）。

## Required Commands (must run; JDK 11 zulu required)

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,V109ExpertTypesMigrationTest'
node --check src/main/resources/static/app.js
node --test src/test/js/*.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

Full-suite `mvn test` regression required before READY_FOR_VERIFICATION
(baseline at de228e1: 2952 Kotlin tests green, 755 JS tests green).

## Working Notes

- Plan line numbers may drift; **symbol/identifier names are authoritative**.
- Worktree contains untracked fast-p docs (`docs/plans/fast/`, `docs/runbooks/`) — never commit them; commit only the authorized files.
- Migration file numbering: confirm V109 is the next free version in `src/main/resources/db/migration/` before creating.

## Deliverable

Implementation commit message: `feat(fast-p): implement child 03`.
Full result (files changed, commands + exit codes, test counts, deviations) to the execution report path.
