# c2 执行报告：定时配置与手动快照开关贯通

执行者：C2Impl（execute-p，child c2）
计划（批准版，字节冻结）：`docs/plans/2026-09-24/emailable-02-task-config.md`（identity `commit:99aa2ed7e9c93cfc5552f47889670fae96816f01`，sha256 `c14acac5ce8c0974b7e7c181defacf282a69ef53783c3db38fbf5d45db9c7549`，与 ledger 记录一致）
主计划：`docs/plans/2026-09-24/emailable-pre-send-verification.md`（sha256 `5d68d9c6a9d2b0f93a4fe230ea8fb2d096d75e9dc87ee5bc5d9082b9c16b5ad8`，一致）
Child brief：`docs/plans/fast/2026-09-24-emailable-pre-send-verification/children/c2/brief.md`（sha256 `d08cbd3ecdc7be6d98924b621efa90019c357d4029ba6f1439ae0cc4f561a426`）
Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-24-emailable-pre-send-verification`
Branch：`fast/2026-09-24-emailable-pre-send-verification`（worktree git dir：`/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-24-emailable-pre-send-verification`）
child_base_sha：`0965a037d94e198f6ce8b13900149a09d35683b4`（= c1 Code head）
执行前 HEAD：`fa4db333fc89f6cb22b67e9e251e5d95148b8942`（c1 证据 + ledger 提交，docs-only）
实施提交：`1968f01d0d07afda1f0d2de571cf3dc3bf72e776` — `feat(fast-p): implement c2`（父提交 = 执行前 HEAD，恰 1 个提交）
结果：**READY_FOR_VERIFICATION**

## 1. 变更文件（与计划「变更文件清单」1:1，恰 8 个）

| # | 计划清单文件 | 实际改动 | 状态 |
|---|---|---|---|
| 1 | `campaign/domain/BatchSendTaskConfig.kt` | 实体 `:40`、View `:74`、Create `:109` 新开关（`Boolean = false`）；Update `:138` `Boolean? = null` | 已改 |
| 2 | `campaign/service/BatchSendTaskConfigService.kt` | create/update/toView/legacy 保留、`ConfigFields`/`NormalizedConfig`、三组 `toFields`、`normalizeAndValidate` 类型守卫 | 已改 |
| 3 | `campaign/domain/BatchExecutionModels.kt` | `toExecutionSnapshot` 复制开关（`:373`） | 已改 |
| 4 | `campaign/service/BatchSendControlService.kt` | `validateSnapshotFields` 直接手动快照类型守卫（`:419-427`） | 已改 |
| 5 | `db/migration/V139__add_batch_email_verification_enabled.sql` | 新增（4 行）：配置表加 1 个布尔列，不回填开启 | 新增 |
| 6 | `test/.../BatchSendTaskConfigServiceTest.kt` | 读写/旧接口/缺省兼容/类型守卫（11 个新用例 + 3 个 helper 参数） | 已改 |
| 7 | `test/.../BatchSendControlServiceTest.kt` | 定时/从配置手动/临时覆盖/非法快照/旧载荷（6 个新用例 + 既有 KV 用例补 1 条断言） | 已改 |
| 8 | `test/.../FlywayMigrationIntegrationTest.kt` | 20 处「迁到最新」断言 138 → 139；新增 V139 迁移契约测试 | 已改 |

`git status --porcelain` 在本 child 内恰好为上述 8 个文件（7 改 + 1 新增）；白名单外无任何改动（`docs/plans/fast/**`、前端 `static/**`、`BatchSendScheduler`、控制器均未触碰）。

实施提交内容核对（`git show --stat 1968f01`）：8 files changed, 613 insertions(+), 35 deletions(-)，不含 `docs/plans/fast/**` 证据文件。

## 2. 关键不变量证据（file:line）

| 不变量 | 实现位置 | 说明 |
|---|---|---|
| I-1 一个布尔字段 | `V139__add_batch_email_verification_enabled.sql:3-4`；`BatchSendTaskConfig.kt:40,74,109,138`；`BatchSendControlServiceTest.kt:949` | 迁移只 `ADD COLUMN email_verification_enabled BOOLEAN NOT NULL DEFAULT FALSE`（存量行 = 0，不回填开启）；实体/View/Create 均 `Boolean = false`；Update `Boolean? = null`；旧 `request_payload` JSON 缺字段读作 false（control 测试 `:949`） |
| I-1 缺省/显式合并语义 | `BatchSendTaskConfigService.kt:107-112`（`cmd.emailVerificationEnabled ?: existing.emailVerificationEnabled`）、`:136` | 缺省或 null 保留现值、显式 false 关闭；由 `BatchSendTaskConfigServiceTest.kt:1653,1671` 两侧覆盖 |
| I-2 所有写路径保值 | create `:91`；update `:136`；`ConfigFields:648`；`NormalizedConfig:673`；三组 `toFields` `:696,725,748`；`toView:551`；`updateLegacyConfig:226`（显式带 `existing` 值）；`setEnabled` copy `:160-163`；`softDelete` copy `:175-179`（未改动，`copy` 天然保值） | 逐一测试（`BatchSendTaskConfigServiceTest.kt`）：create `:1620,1637`、update `:1653,1671`、setEnabled `:1688`、softDelete `:1735`、legacy adapter `:1750`；纯 KV 兼容快照无字段 → false（`BatchSendControlServiceTest.kt:768`） |
| I-3 双入口类型守卫 | `BatchSendTaskConfigService.kt:367-369`（模板解析后 `require(mailType == INTRODUCTION \|\| !flag)`）、`BatchSendControlService.kt:419-427` | 配置三路（`BatchSendTaskConfigServiceTest.kt`）：create `:1794`、update `:1808`、setEnabled `:1717`，均拒绝且不落库；直接手动快照（`BatchSendControlServiceTest.kt`）：`:894` 返回 400 且不启动执行器；INTRODUCTION + true 放行 `:1620`（配置）与 `:914`（手动） |
| I-3 预估不调用验证 | 未改动 `countBySnapshot`（`ManualInitialOutreachService.kt:473-492`，区间内验证引用数 0）；`batchEmailVerificationService.*` 的全部 6 个调用点都在同一文件的发送引擎内（`:160,538,696,891,1162,1168`） | 人数预估路径结构性不触达验证服务/密钥，也不需要 Emailable 额度 |
| I-4 快照不可被追改 | `BatchExecutionModels.kt:373`（启动时逐字复制）；`BatchSendControlService.kt:65,106,123,153,257`（既有入口沿用同一 `toExecutionSnapshot`） | 定时 `:827` 与从配置手动 `:841` 捕获的快照均为 true；临时覆盖 `:868` 启动快照为 false 且来源配置零回写（`verify(repo, never()).save(...)`），`sourceConfigId` 审计身份保留；运行中改配置/软删不改已固定快照（`BatchSendScheduler`、调度机制未改） |

下游接口（c3 依赖，逐项落地）：

| 接口 | 位置 | 证据 |
|---|---|---|
| `BatchSendTaskConfigView.emailVerificationEnabled: Boolean` | `BatchSendTaskConfig.kt:74` + `BatchSendTaskConfigService.kt:551` | wire smoke：序列化 JSON 含 `"emailVerificationEnabled":true` |
| Create `Boolean = false` / Update `Boolean? = null`，JSON 名 `emailVerificationEnabled` | `BatchSendTaskConfig.kt:109,138` | wire smoke：缺省 → create `false` / update `null`；显式 true/false/null 各自绑定正确 |
| `toExecutionSnapshot` 复制开关 | `BatchExecutionModels.kt:373` | `BatchSendTaskConfigServiceTest.kt:1633,1649` |
| `validateSnapshotFields` 对直接手动快照做类型守卫 | `BatchSendControlService.kt:419-427` | `BatchSendControlServiceTest.kt:894`（c3 手动草稿走同一路径） |

## 3. 命令结果（本 child 最终状态下的 fresh 运行）

| # | 命令 | 结果 | 计数 |
|---|---|---|---|
| 1 | `JAVA_HOME=<zulu-11> mvn test -Dtest=BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,BatchSendTaskRuntimeIntegrationTest,BatchSendSchedulerTest` | **PASS**（exit 0，BUILD SUCCESS） | 152 tests / 0 failures / 0 errors（config 85 + control 40 + runtime 22 + scheduler 5）；node-test 同批触发：tests 1152 / suites 230 / pass 1152 / fail 0；`node --check app.js`、`task-modal-runtime.js` 均通过 |
| 2 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=<zulu-11> mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40` | **PASS**（exit 0，BUILD SUCCESS） | 30 tests / 0 failures / 0 errors；日志 29 次 `now at version v139`；含 `Successfully applied 1 migration ... now at version v139`（V138 → V139 增量升级路径） |

基线对照：

| 基线（ledger / brief） | 本 child 终态 | 结论 |
|---|---|---|
| 定向 4 类 = 74+34+22+5 = 135 tests / 0 fail | 152 tests / 0 fail（新增 17 个用例：config +11、control +6） | 新增用例全绿，旧用例零回归 |
| JS 1152 pass | 1152 pass（未改前端） | 零回归 |
| `FlywayMigrationIntegrationTest` 28 tests / 19 failures（`expected: <136> but was: <137>`，预置红） | 30 tests / 0 failures / 0 errors | c1 把 136→138，本 child 把 20 处 138→139 并新增 V139 契约测试，预置红在本 child 内彻底清零 |

补充 smoke（throwaway，不入库、不进提交）：用项目 classpath 直接反序列化/序列化配置命令与 View（`/tmp/c2/WireContract.java`），exit 0：

```
update.absent       = null
update.explicitTrue = true
update.explicitFalse= false
update.explicitNull = null
create.absent       = false
create.explicitTrue = true
view.json           = true
```

中间失败与修正（诚实记录，同一生产代码）：

- 首次运行命令 1：5 errors —— 我新写的两处 `captureValue(captor, anySnapshot())` 对同一形参注册了两个 matcher（`anySnapshot()` 内部调用 `Mockito.any()`），以及 `setEnabled` 用例漏写 `verify(...).save(captor.capture())`。全部为测试侧写法问题（无生产代码改动）。
- 第二次运行命令 1：1 failure —— `setEnabled` 用例在第二次 `save` 前读取 captor（captor 只在 verify 时捕获），改为 `verify(repository, times(2))` + `captor.allValues`。
- 第三次运行（上表命令 1、命令 2）：全绿，exit 0。

## 4. 与计划的偏差

1. **直接手动快照的守卫返回显式 400**（`BatchSendControlService.kt:419-427`）：`validateSnapshotFields` 既有分支对其它字段一律返回 422，但 I-3 Rule 与人工验收 A-4 都要求「材料提醒 + true → 400」，且配置保存路径的 `require` 经 `GlobalExceptionHandler`（`common/controller/GlobalExceptionHandler.kt:18-20`）也是 400。故守卫单独返回 400，既有 422 语义不动。
2. **守卫顺序**：类型守卫在 `validateSnapshotFields` 中先于其它字段校验执行 —— 同时非法时优先报类型错误（fail-closed、确定性优先），计划未规定顺序。
3. **`UpdateCommand.toFields` 签名**：按计划「在 UpdateCommand.toFields 增加 existing 值参数或在调用处明确合并」，采用显式参数 `mergedEmailVerificationEnabled`（`BatchSendTaskConfigService.kt:697-703`），调用处传 `cmd.x ?: existing.x`；未引入任何通用「空值清空/保留」逻辑。
4. **测试增量**：新增 17 个用例 + 1 条既有断言（KV 兼容快照 false）+ 1 个 IT 契约用例；另有 3 个既有测试 helper（`createCmd`/`updateCmd`/`row`）新增默认参数，不改既有断言。
5. **IT latest 断言**：本 child 只把 20 处 138 → 139（c1 已完成 136 → 138）；显式历史 target 断言（135/133/131/130/123/116/… ）逐字未动。
6. **未做的计划外事项**：`BatchSendScheduler`、控制器、前端、`BatchSendSettingService`（纯 KV 兼容快照保持无该字段 → 默认 false）一律未改，符合计划「不做」清单。

## 5. 剩余风险 / 未覆盖

- **计划的人工验收 A-1～A-4（真实 REST 黑盒）未在本 child 执行**：属人类门禁步骤；本 child 提供代码级证据 + wire 契约 smoke（JSON 字段名/缺省语义）作为替代证明。
- **`BatchSendConfigControllerTest` 未在本次运行**（不在 brief 的必需命令集内）：控制器不复制任何字段（直接委托 service），字段绑定面已由 wire smoke 覆盖；若控制方后续跑全量命令，该用例是唯一可能触及绑定面的地方。
- **两处拒绝文案是字面量**：服务与快照守卫都写「发送前邮箱验证只支持介绍邮件（INTRODUCTION）」，测试按子串断言；未来改文案需同时改两处测试（`BatchSendTaskConfigServiceTest.kt:1802,1826`、`BatchSendControlServiceTest.kt:909`）。
- **Emailable 运行期行为**（HTTP 契约、密钥、明细落库）属 c1 已交付范围，本 child 未触碰、未重验。
- **`email_verification_enabled` 的运维可见性**：c3 负责前端 pill/编辑回填；本 child 只保证 View 回显权威值（`BatchSendTaskConfigService.kt:551`）。

## 6. 提交与清理

- 实施提交：`1968f01d0d07afda1f0d2de571cf3dc3bf72e776` `feat(fast-p): implement c2`，父提交 `fa4db333fc89f6cb22b67e9e251e5d95148b8942`，位于 `fast/2026-09-24-emailable-pre-send-verification`，恰 1 个提交，仅含 8 个业务/测试/迁移文件。
- 本报告由控制方单独提交（当前为未跟踪文件）；`/tmp/c2/**` 的日志与 throwaway smoke 源码不在仓库内，无需清理。
- 未 push、未 merge、未 rebase、未 amend、未 squash；未改动 `docs/plans/**`；工作区其余改动（`children/c3/brief.md`，控制方写入）未被触碰、未被提交。
