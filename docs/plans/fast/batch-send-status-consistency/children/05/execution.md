# Execution Report — 05 P-E：RecipientScope 接入专家状态过滤

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency/docs/plans/2026-08-13/05-recipient-scope-status-filter.md
Plan SHA-256: 7cd9316617056271e1f598d4411d15121aec3635b79f58b8efa956dbc96e159a
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency/docs/plans/2026-08-13/05-recipient-scope-status-filter.md@7cd9316617056271e1f598d4411d15121aec3635b79f58b8efa956dbc96e159a
Execution epoch: RESUME (epoch 2, Amendments A5 HUMAN-approved; prior epoch-1 implementation verified against amended plan)
Approval basis: current invocation (fast-p child 05 resume instruction) + Amendments A5 (HUMAN 2026-08-13)
Executor: Impl05
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency
Target branch: fast/batch-send-status-consistency
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency@fast/batch-send-status-consistency@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-send-status-consistency
Pre-execution code SHA: e590785798990381c86daff1642abd6b7e51c177 (plan-amendment commit, A5)
Post-execution code SHA: b3ae95ac31ad4e24c3a4670d66e65850ab80d8cf
Evidence HEAD: N/A (single product commit; no separate evidence commit)
Implementation boundary: e590785..b3ae95a

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 V95 migration（operator_status 可空，默认 NULL=不限） | IMPLEMENTED | `src/main/resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql` (new) | ALTER TABLE batch_send_task_config ADD COLUMN operator_status VARCHAR(32) NULL AFTER discipline; committed b3ae95a |
| T-2 实体与命令 4 个 data class 加字段 | IMPLEMENTED | `BatchSendTaskConfig.kt` | BatchSendTaskConfig / View / CreateCommand / UpdateCommand 各 +`operatorStatus: String? = null` |
| T-3 服务层映射（toView / ConfigFields / 3×toFields / updateLegacyConfig 保留 / 白名单引用 OperatorStatus.entries） | IMPLEMENTED | `BatchSendTaskConfigService.kt` | toView :399、ConfigFields、CreateCommand.toFields :527 / UpdateCommand.toFields :545 / BatchSendTaskConfig.toFields :563、updateLegacyConfig :187 `operatorStatus = existing.operatorStatus`、ALLOWED_OPERATOR_STATUSES = OperatorStatus.entries.map{it.name}.toSet()；toLegacyConfig/返回值构造未动（I-5） |
| T-4 Scope 与快照（BatchExecutionSnapshot / RecipientScope / fromSnapshot / matchesExpert / toExecutionSnapshot） | IMPLEMENTED | `BatchExecutionModels.kt` | matchesExpert :60-64 状态判定（NOT_CONTACTED→isNullOrBlank，其余→相等）；fromSnapshot :107；toExecutionSnapshot :243 |
| T-5 三条查询旁路 + operatorStatusFilter 抽出 | IMPLEMENTED | `ManualInitialOutreachService.kt`, `ExpertSearchService.kt` | buildEsFiltersForLevel 两分支 :1220-1239（I-2 else 分支 :1237）、buildMaterialReminderEsFilters :1090-1092、operatorStatusFilter 定义于 ExpertSearchService（NOT_CONTACTED→must_not exists 复用 notContactedWithEmailFilters，其余→term）；matchesExpert 经 buildRetryableTargets :952 覆盖重试路径 |
| T-6 前端两个面板下拉（styles.css 零 diff） | IMPLEMENTED | `index.html`, `app.js` | 配置编辑器 select `batchConfigEditorOperatorStatus` + 手动面板 `batchManualOperatorStatus`；fillBatchOperatorStatusSelectOptions 取自 operatorStatusOptions（app.js:618）+「全部状态」空值；样式 .batch-config-field + .bsc-input；styles.css diff 0 行 |
| T-7 测试（ES 按状态 / 重试按状态 / APPLICATION 生效 / 留空不变 / I-4 保留） | IMPLEMENTED | `ManualInitialOutreachServiceTest.kt` | +6 测试：I-2 CANDIDATE term 替换、I-3 NOT_CONTACTED must_not exists（无 term）、I-2 APPLICATION 生效、I-1 重试 REPLIED 排除（A-3 形态）、留空不变、I-4 updateLegacyConfig 保留（ArgumentCaptor 断言 operatorStatus=NOT_CONTACTED）；focused 63/0/0/0 |
| T-8 知识文档 | IMPLEMENTED | `docs/knowledge/campaign/K-recipient-scope-status-filter.md` (new) | 三条旁路、I-3 语义、陷阱（非 NOT_CONTACTED 替换基座）、配置链路、前端注册点、守卫注意事项 |
| A5 守卫排除项更新（:332→:345 + 10 处配置映射登记） | IMPLEMENTED | `OperatorStatusWriteSeamGuardTest.kt` | EXCLUDED_NOISE_SITES：ExpertSearchService 钉死点 345（上下文不变）；新增 BatchExecutionModels.kt :107/:243 与 BatchSendTaskConfigService.kt :74/:107/:187/:292/:399/:527/:545/:563 共 10 项，均附 file:line+context；ALLOWED_WRITE_SITES 与闭包断言未动；guard 1/0/0/0 |

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=...zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0；surefire Tests run: 2410, Failures: 0, Errors: 0, Skipped: 4；JS 496 pass（fail 0） |
| `JAVA_HOME=...zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest` | PASS | exit 0；Tests run: 63, Failures: 0, Errors: 0, Skipped: 0 |
| `JAVA_HOME=...zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0；BUILD SUCCESS；Tests run: 2410, Failures: 0, Errors: 0, Skipped: 4 |
| `git diff --check` | PASS | exit 0，无 whitespace 错误 |
| （补充）`mvn test -Dtest=OperatorStatusWriteSeamGuardTest`（A5 前置验证，两次：失败定位→修复后通过） | PASS | 修复后 Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 |

FlywayMigrationIntegrationTest：按 Amendments A2（HUMAN 2026-08-13）跳过，未运行。

### Changed Files

- `src/main/resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql` — T-1 新列（新增）
- `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` — T-2 四个 data class 加字段
- `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` — T-4 快照/Scope/matchesExpert 透传
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` — T-3 映射 + I-4 保留 + 白名单
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` — T-5 三条旁路
- `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` — T-5 operatorStatusFilter 抽出
- `src/main/resources/static/index.html` — T-6 两面板下拉
- `src/main/resources/static/app.js` — T-6 注册/载荷/差异显示
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` — T-7 +6 测试
- `docs/knowledge/campaign/K-recipient-scope-status-filter.md` — T-8 知识条目（新增）
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` — A5 排除项更新

Implementation commit: `b3ae95ac31ad4e24c3a4670d66e65850ab80d8cf feat(fast-p): implement 05`（恰好 11 个授权文件：10 + A5 守卫测试；docs/plans/fast/* 与 styles.css 均未纳入）

### Deviations

- None。epoch-1（未提交）实现经逐任务核对与修订计划一致，无缺口；唯一新增改动为 A5 授权守卫排除项更新。
- 说明（非偏差）：`buildMaterialReminderEsFilters` 在本仓库无生产调用点（基线 9df711a 起即如此，05 之前已存在）；材料提醒实际发送路径经 `buildMaterialReminderSnapshotFromScope` → `buildEsFiltersForLevel` 覆盖，同样携带状态过滤。计划 I-1 明确要求该函数本体接入，已接入。

### Freshness

- Plan identity rechecked: YES（7cd93166…，与执行前一致，未变）
- Worktree identity rechecked: YES（root/branch/git_dir 一致；HEAD 变为产品提交 b3ae95a）
- Reported commits reachable from target branch: YES（b3ae95a 即分支 HEAD）
- Required commands run this invocation: YES（全部 4 条 fresh 运行）
- Historical evidence used only as baseline: YES（epoch-1 实现仅作核对起点，最终态 fresh 验证）

### Remaining Blocker

- None

### Next Action

- READY_FOR_VERIFICATION → run `verify-p`（verify-fast-p / review-fast-p 按主计划节奏）
