# Child c2 Brief — Emailable 放行结果：任务配置（config）

## Identity
- Child: c2（计划第 2/3 片）
- Master plan: `docs/plans/2026-10-01/email-verification-allowlist.md`（总览；顺序 backend → config → frontend）
- Child plan（完整合同，权威）: `docs/plans/2026-10-01/email-verification-allowlist-config.md`
- 证据附件: `docs/plans/2026-10-01/email-verification-allowlist-evidence.md`（E-1/E-2 检索回执）
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist`
- Branch: `fast/2026-10-01-email-verification-allowlist`
- child_base_sha: 见派发消息（= c1 Code head `e7441004aa6dd68fb3f8e486537d3e520f71e0fd`）。
- 前序 child 输出（已 LIGHT_PASS，可直接依赖）：c1 在 `BatchExecutionModels.kt` 实现了 `object EmailVerificationAllowedStates`（`ALLOWED`（deliverable,risky,unknown 固定顺序）、`requireValid(List<String>?)`、`normalize(List<String>?)`、`allowedFor(List<String>?)`），快照字段 `emailVerificationAllowedStates: List<String>? = null`（含 init 严格校验），原因码 `EMAIL_VERIFICATION_POLICY_SKIP`，`verify(..., allowedStates)` 第三参。c2 必须复用该 helper，不得另建重复类型/校验。

## Authorized files（仅此 7 个；超出必须 PAUSE）
1. `src/main/resources/db/migration/V145__add_batch_email_verification_allowed_states.sql`
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`

非产品输出：只允许写执行报告 `docs/plans/fast/2026-10-01-email-verification-allowlist/children/c2/execution.md`；其余路径只读。

## 关键不变量（以 child plan「关键不变量」全文为准）
- I-1 一列配置事实源：新增 `email_verification_allowed_states_json TEXT NULL`；SQL NULL = 旧配置兼容三态全放行；非 NULL 必须为合法数组、元素仅 `deliverable/risky/unknown`，`[]` 明确全跳过；损坏 JSON / JSON null / 非数组 / 非法元素不得当旧 NULL 或全放行；规范化顺序固定 `deliverable,risky,unknown`、重复去重。
- I-2 新建默认与更新保留：`CreateCommand.emailVerificationAllowedStates` 缺省 `[deliverable]`；`UpdateCommand` nullable，缺字段或 null 都保留 existing 原始列（包括 SQL NULL）；显式 `[]` 存 `[]`；View 必须回有效数组，旧 SQL NULL 回三项；create 显式 null 不可升级成旧模式，按非 nullable DTO 绑定拒绝。
- I-3 配置进快照：`toExecutionSnapshot` 解析并复制本列有效列表；定时、按配置手动、旧实体启动路径都消费相同列表；手动请求传 `[]` 与三态数组覆盖来源配置，不更新配置列。
- I-4 既有配置生命周期不变：`setEnabled`/`softDelete`/cron 重排事件/旧 typed update 保留列；验证关闭仍保存选择；材料提醒仍禁止开启验证；历史 `emailVerificationEnabled`/`excludeVerifiedUnavailableEmails` 不因迁移或保存自动开启。

## 实施要点（child plan T-1..T-3 为准）
- V145 逐字：`ALTER TABLE batch_send_task_config ADD COLUMN email_verification_allowed_states_json TEXT NULL;` —— 执行前核对迁移目录最大版本仍为 V144；若 V145 被占用，只顺延新迁移版本号并同步本 brief/计划引用与测试（计划允许的顺延）。
- 严格 JSON 解析 helper 也放 `BatchExecutionModels.kt`（复用 c1 的允许值校验）：SQL NULL→三项；JSON 文本必须 array/textual/合法值；空数组保持空；坏值拒绝。所有读取统一使用它。
- 覆盖 create、update、updateLegacyConfig、`ConfigFields`、`NormalizedConfig`、三个 `toFields`、`toView`、`toExecutionSnapshot`。**K-batch-config-legacy-adapter-field-preservation**：`updateLegacyConfig` 用只含旧字段的请求调用全量 update，每个新增列必须显式 `newField = existing.newField`，否则旧 typed API 一次调用会把新配置静默重置。
- 实体 nullable String 默认 null；Create 非 nullable List 默认 `[deliverable]`；Update nullable List 默认 null；启停/软删 copy 保留列；不回填存量。
- 迁移测试：只把「最新版本」断言改为实际最新（计划写 145）；固定中间目标 139/140/141 等保持原值；绝不修改已应用迁移 V1..V144。
- `request_payload` 不加 DB 列；快照字段绑定由 c1 完成，本 child 负责配置列→`toExecutionSnapshot` 传值。

## Downstream interfaces（c3 依赖）
- 配置 View 字段 `emailVerificationAllowedStates`：有效数组（旧 SQL NULL 回 `deliverable,risky,unknown`；`[]` 原样）。
- 执行快照 `emailVerificationAllowedStates`（c1 字段）由 `toExecutionSnapshot` 赋值。
- 手动覆盖只进执行快照，不 PUT/回写来源配置。

## Required commands（必须执行并记录 exit/counts）
```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=BatchSendTaskConfigServiceTest,BatchSendControlServiceTest test
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -DmigrationIt=true -Dapi.version=1.40 -Dtest=FlywayMigrationIntegrationTest test
```
- 迁移 IT 基线（master base）为红：33 tests / 24 failures，全部 `expected: <142> but was: <144>`（计划已文档化）；本 child 更新最新断言后必须全绿。
- 全量 `mvn test` 是 run 级收尾命令（c3 收尾统一执行），不是本 child 必需命令。
- 计划要求：执行前核对证据附件行号；若代码已变化，按实际代码定位，不照旧行号盲改。

## 范围与纪律
- 只改 7 个 Authorized Files；不新增表/接口/KV 双写；不把字段加到 KV DTO；不重构相邻代码。
- 不 push、不 merge、不 rebase、不 amend；实现提交：`feat(fast-p): implement c2`（逐项 `git add` 指定文件，禁止 `git add -A`）。
- 不提交 `docs/plans/fast/**`、`baseline/**`、报告文件；不触碰主工作区与其他 worktree。
- 若必需改动落在授权文件之外，返回 PLAN_CONFLICT；不得自行扩权。
