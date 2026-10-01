# Child c2 Execution — 任务配置（config）Emailable 放行结果

## Execution Result: READY_FOR_VERIFICATION

- Plan（权威合同）: `docs/plans/2026-10-01/email-verification-allowlist-config.md`
- Brief: `docs/plans/fast/2026-10-01-email-verification-allowlist/children/c2/brief.md`
- 证据附件: `docs/plans/2026-10-01/email-verification-allowlist-evidence.md`（E-1/E-2）
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist`
- Branch: `fast/2026-10-01-email-verification-allowlist`
- child_base_sha（c1 Code head）: `e7441004aa6dd68fb3f8e486537d3e520f71e0fd`
- Pre-execution HEAD: `692262514628e65d4da3da97be54e73e16cec865`（c1 light verification docs 提交）
- Implementation commit: `ac37fcd9fc570897ec42b3ce7745a9a7104cebe7` — `feat(fast-p): implement c2`
- Executor: C2Impl

### 行号核对（计划要求：执行前核对证据附件行号）

证据附件（生成于更早基线）与当前工作树的实际行号已漂移，本次按**实际代码**定位，未照抄旧行号：

| 计划/证据引用 | 证据行号 | 实际行号（本 child 执行前） |
|---|---|---|
| `toExecutionSnapshot` | BatchExecutionModels.kt:367 | BatchExecutionModels.kt:425 |
| `parseSenderAccountCodes` | BatchExecutionModels.kt:443 | BatchExecutionModels.kt:508 |
| 配置服务 update 合并入口 | BatchSendTaskConfigService.kt:103 | BatchSendTaskConfigService.kt:105 |
| 配置服务 updateLegacyConfig | BatchSendTaskConfigService.kt:199 | BatchSendTaskConfigService.kt:206 |
| `toView` | BatchSendTaskConfigService.kt:552 附近 | BatchSendTaskConfigService.kt:565 |

结论：seam 位置与计划描述一致（create/update/updateLegacyConfig/toFields×3/toView/toExecutionSnapshot），仅行号漂移。

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 迁移 V145 + 实体 nullable 列 | IMPLEMENTED | `V145__add_batch_email_verification_allowed_states.sql`, `BatchSendTaskConfig.kt` | 迁移文件逐字 `TEXT NULL`；实体 `emailVerificationAllowedStatesJson: String? = null` |
| T-2 严格 JSON 解析 helper（复用 c1 允许值校验） | IMPLEMENTED | `BatchExecutionModels.kt` | `parseEmailVerificationAllowedStates(objectMapper, json)`：SQL NULL→三态；非 NULL 必须 array/textual/合法值；`[]` 保持空；JSON null/非数组/非字符串/非法值/空白文本一律拒绝 |
| T-2 create 默认与规范化 | IMPLEMENTED | `BatchSendTaskConfig.kt`, `BatchSendTaskConfigService.kt` | `CreateCommand.emailVerificationAllowedStates` 非 nullable 默认 `[deliverable]`；`encodeEmailVerificationAllowedStates` 用 c1 `normalize` 规范化后序列化 |
| T-2 update 缺字段/null 逐字保留、显式 `[]` 存储 | IMPLEMENTED | `BatchSendTaskConfigService.kt` | `update()` 入口 `cmd.x?.let(encode) ?: existing.x`（保留原始列文本，含 SQL NULL） |
| T-2 updateLegacyConfig 显式保留 | IMPLEMENTED | `BatchSendTaskConfigService.kt` | 显式 `emailVerificationAllowedStates = null`（本列 null 语义 = 逐字保留），见 K-batch-config-legacy-adapter-field-preservation 注释 |
| T-2 ConfigFields/NormalizedConfig/三个 toFields/toView | IMPLEMENTED | `BatchSendTaskConfigService.kt` | 三处 toFields 均传新字段；`normalizeAndValidate` 对非 null 文本按同一解析点严格校验 |
| T-2 toExecutionSnapshot 传值 | IMPLEMENTED | `BatchExecutionModels.kt` | `emailVerificationAllowedStates = parseEmailVerificationAllowedStates(objectMapper, emailVerificationAllowedStatesJson)` |
| T-3 回归测试（配置链 / 坏值 / 生命周期 / 快照隔离） | IMPLEMENTED | 三个测试文件 | 见下「命令与计数」 |
| T-3 迁移测试 + 最新版本断言 145 | IMPLEMENTED | `FlywayMigrationIntegrationTest.kt` | 24 处「最新版本」断言 142→145；中间固定目标 139/140/141/144 保持原值；新增 V145 用例 |

### 关键不变量落实

- **I-1**：单列 `email_verification_allowed_states_json TEXT NULL`；无新表、无 KV 双写、KV DTO 未改。SQL NULL 仅在读侧投影为 `deliverable,risky,unknown`；非 NULL 坏值（坏 JSON / `null` / 非数组 / 非字符串元素 / 未知值 / 大小写与空白变体 / 空白文本）在 `toView`、`toExecutionSnapshot`、`normalizeAndValidate` 三处拒绝，绝不降级为三态放行。规范化顺序固定 `deliverable,risky,unknown` 并去重。
- **I-2**：Create 非 nullable 默认 `[deliverable]`（请求体显式 null 由 Jackson/Kotlin 非空绑定拒绝）；Update nullable，缺字段/null 逐字保留原始列（含 SQL NULL，测试用 `[ "unknown" ]` 证明不做重新序列化）；显式 `[]` 存 `[]`；View 恒回有效数组，旧 SQL NULL 回三项。
- **I-3**：`toExecutionSnapshot` 解析本列；`startScheduled` / `startManualFromConfig` / `startManual(request)` 均固定进 `task_execution.request_payload`；手动覆盖（`[]` 与三态数组）只进本次快照，`repository.save` 零调用（来源配置不被回写）。
- **I-4**：`setEnabled` / `softDelete` 的 copy 不带本字段（保留列）；cron 重排事件未新增/取消；关闭验证开关仍保存放行选择（测试断言）；材料提醒仍禁止开启验证（既有校验未改）；迁移不回填、不自动开启 `email_verification_enabled`（迁移 IT 断言）。

## Changed Files（仅 7 个授权文件）

1. `src/main/resources/db/migration/V145__add_batch_email_verification_allowed_states.sql` —— 新增 `TEXT NULL` 列（执行前核对：迁移目录最大版本为 V144，V145 未被占用，无需顺延）。
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` —— 实体 nullable 列 + View/Create/Update 命令字段。
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` —— 复用 c1 `EmailVerificationAllowedStates` 的严格解析 helper + `toExecutionSnapshot` 传值。
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` —— create/update/updateLegacyConfig/ConfigFields/NormalizedConfig/三个 toFields/toView/encode helper。
5. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` —— 12 个新用例（默认/规范化/坏值/绑定拒绝/逐字保留/SQL NULL 保持/显式 `[]`/生命周期/旧 typed/读侧拒绝）。
6. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt` —— 4 个新用例（定时、配置手动、旧 NULL 三态、手动覆盖不回写）。
7. `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` —— 新增 V145 迁移用例；24 处最新版本断言 142→145。

未改动：`src/main/kotlin/.../BatchSendControlService.kt`（既有 `toExecutionSnapshot` 调用点无需改动）、`BatchSendConfigController.kt`（直接绑定命令 DTO，无额外映射层）、KV DTO、其它迁移。

## Downstream interfaces（c3 依赖，均已就位）

- View `BatchSendTaskConfigView.emailVerificationAllowedStates: List<String>`：旧 SQL NULL 回 `["deliverable","risky","unknown"]`；`[]` 原样。
- 执行快照 `BatchExecutionSnapshot.emailVerificationAllowedStates`（c1 字段）由 `toExecutionSnapshot` 赋值（旧 NULL → 显式三项，语义等价 c1 的 `allowedFor(null)`）。
- 手动覆盖只进执行快照，不 PUT/回写来源配置。

## Commands（均在最终实现状态下、本次调用内新跑）

| 命令 | 结果 | 证据 |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=BatchSendTaskConfigServiceTest,BatchSendControlServiceTest test` | PASS | exit 0 / BUILD SUCCESS；`Tests run: 156, Failures: 0, Errors: 0, Skipped: 0`（BatchSendTaskConfigServiceTest 105、BatchSendControlServiceTest 51） |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -DmigrationIt=true -Dapi.version=1.40 -Dtest=FlywayMigrationIntegrationTest test` | PASS | exit 0 / BUILD SUCCESS；`Tests run: 34, Failures: 0, Errors: 0, Skipped: 0`（322s，含 Testcontainers MySQL） |

基线对照（只读，未修改）：

- 迁移 IT 基线红：`baseline/mvn-migrationit.txt` → `Tests run: 33, Failures: 24, Errors: 0`，24 条全部 `expected: <142> but was: <144>`（24 处「最新版本」断言）；本 child 更新为 145 后全绿，中间固定目标 139/140/141/144 未改动。
- Docker 可用性：`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock docker version` → Server 29.4.0（IT 非跳过执行，未以 skip 代替通过）。

执行过程中的一次自纠（仍在本 child 授权文件内）：首个版本的 `updateLegacyConfig` 用例在 for 循环里重复 stub `repository.save(any())`，触发 Mockito「stub 期间旧 answer 收到 matcher 的 null 参数」NPE；已重构为 `updateLegacyConfigOnce(storedJson)` helper + 两个独立用例（SQL NULL / 既有文本），重跑全绿。

## Freshness

- Plan identity：以执行前从磁盘重读的 brief + child plan 为准（未在会话中记忆复用）。
- Worktree identity：所有读写/测试/提交均在 worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist`，分支 `fast/2026-10-01-email-verification-allowlist`。
- 提交：`ac37fcd9fc570897ec42b3ce7745a9a7104cebe7`（`feat(fast-p): implement c2`）为分支 HEAD，仅含上述 7 个文件；未 push/merge/rebase/amend/reset。
- 未提交：`docs/plans/fast/**`（含本报告）、`baseline/**`、Main 的 ledger 改动（工作区保留、不入本提交）。
- 命令本次新跑：是（两条必需命令都在最终状态下重跑）。
- 历史产物（baseline/*.txt）仅作基线对照，未当作通过证据。

## Deviations

- 无范围扩张。计划未明确的一处读侧细节由本 child 按 I-1 判定：**非 NULL 的空白文本视为坏值拒绝**（而不是像 `sender_account_codes_json` 那样当作「未设置」），因为只有 SQL NULL 才允许降级为三态放行；已在 helper 注释与测试中固定。
- `updateLegacyConfig` 的「显式保留」写法为 `emailVerificationAllowedStates = null`：本列 UpdateCommand 的 null 语义即「逐字保留 existing 原始列」，若回传解析后的三态列表会把旧 SQL NULL 写成显式三态数组（违反 I-2 逐字保留）。文件内已按 K-batch-config-legacy-adapter-field-preservation 注明，并有测试覆盖 SQL NULL 与既有文本两种存量形态。
