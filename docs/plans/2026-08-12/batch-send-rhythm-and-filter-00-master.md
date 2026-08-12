# 批量发送节奏改造与筛选增强 —— 主计划（拆分说明）

> 本文件不是可执行计划，是拆分决策记录。每个子计划独立可部署、独立验证，按序号顺序执行。

## 需求原文（2026-08-12）

1. 去掉定时任务中的日限额，限额只用账号的限额，到达限额就停止定时任务
2. 定时任务中加一个「执行轮次数」（int），表示每个定时调度执行多少轮次。执行轮次 2 × 每轮数量 20 = 单次调度共发 40 封
3. 过滤条件中添加「地区」，允许多选
4. 地区前端下拉框显示英文，要改为中文（批量任务配置 + 专家列表两处）
5. 专家列表中的学科有「未分类」，为什么过滤条件中没有
6. 定时调度支持直接传入 cron 表达式，并提供测试按钮，能看到最近 5 次将要执行的时间
7. 定时调度列表加「下次执行时间」字段，与「最近一次执行时间」合并到一列

## 已确认的需求方决策（2026-08-12）

| 议题 | 决策 |
|---|---|
| 「到达账号限额就停止定时任务」的语义 | **仅结束本次执行**。`autoEnabled` 保持 true，下个 cron 周期正常再试，次日账号额度重置后自动恢复。等同现状行为，只是去掉 `dailyCap` 那道闸门 |
| `dailyCap` 如何下线 | **删列 + 删 UI + 删闸门**。新增迁移 `DROP COLUMN daily_cap` |
| cron 输入与现有「执行频率」下拉的关系 | **下拉 + 高级自定义**。保留每小时/每天/每周一，新增「自定义 cron」选项，选中后展开输入框 + 测试按钮 |
| 「下次执行时间 / 最近一次执行时间」数据来源 | **后端算好一起返回**。`BatchSendTaskConfigView` 增加 `nextFireTime` + `lastExecutedAt` |

## Phase 2 范围检查结论：必须拆分

`create-p` 硬约束：单计划 ≤ 10 文件、≤ 2 个独立子系统、每个共享存储 ≤ 1 个新增字段。本需求实测超限：

**`dailyCap` 引用点全集（grep 实测，非记忆）**

- 生产代码 7 个文件：`BatchSendTaskConfig.kt`(4 处)、`BatchExecutionModels.kt`(2 处)、`BatchSendTaskConfigService.kt`(12 处)、`BatchSendControlService.kt`(11 处)、`ManualInitialOutreachService.kt`(11 处)、`BatchSendSettingService.kt`(5 处)、`V72__create_batch_send_task_config.sql`(4 处)
- 前端 2 个文件：`app.js`(19 处，横跨旧 KV 控制台 `batchSendDailyCap`、配置编辑器 `batchConfigEditorDailyCap`、手动 tab `batchManualDailyCap` 与 diff 字段表)、`index.html`
- 测试 12 个文件：Kotlin 8 个（`BatchSendControlServiceTest` 10 处、`BatchSendSettingServiceTest` 24 处、`BatchSendTaskConfigServiceTest` 14 处、`BatchSendTaskRuntimeIntegrationTest` 21 处、`ManualInitialOutreachServiceTest` 50 处、`BatchSendConfigControllerTest` 16 处、`MailAutomationControllerTest` 3 处、`BatchSendSchedulerTest` 1 处）、JS 4 个（`batchSendControls.test.js`、`expertTagBatchFix.test.js`、`batchManualExecutionLog.test.js`、`batchSendTaskConsoleInteraction.test.js`）

单是第 1 条需求就是 21 个文件。加上其余 6 条，独立子系统至少 4 个（批量发送配置 / 发送执行循环 / 专家 ES 检索 / 前端控制台）。

## 子计划清单（共 7 份，全部已写完）

| 序号 | 计划文件 | 覆盖需求 | 子系统 | 文件数 | 依赖 |
|---|---|---|---|---|---|
| 01 | `batch-send-rhythm-01-rounds-per-run.md` | 需求 2（后端） | 批量发送配置 + 发送执行循环 | 9 | — |
| 02a | `batch-send-rhythm-02a-remove-daily-cap-gates.md` | 需求 1（闸门语义） | 发送执行链路 | 5 | 01 |
| 02b | `batch-send-rhythm-02b-drop-daily-cap-field.md` | 需求 1（字段与列） | 批量发送配置 | **12**（已记录偏离） | 02a |
| 03 | `batch-send-scope-03-region-multiselect-backend.md` | 需求 3（后端） | 批量发送配置 + 专家 ES 检索 | 10 | — |
| 04a | `batch-send-console-04a-cron-preview-and-exec-time-backend.md` | 需求 6、7（后端） | 批量发送配置 + 任务执行记录 | 7 | — |
| 04b | `batch-send-console-04b-editor-and-list-frontend.md` | 需求 1/2/3/6/7（前端） | 前端控制台 | 7 | 01、02b、03、04a |
| 05 | `expert-filter-05-region-i18n-and-unclassified.md` | 需求 4、5 | 专家 ES 检索 + 前端控制台 | 10 | 03、04b |

> 原计划的 02 与 04 各自拆成了 a/b 两份。**拆分依据是 grep 实测的文件数，不是估算**：
> - 02 若合并为一份需变更 **12 个文件**（6 个生产 + 6 个测试），且删除 data class 字段会同时打断其全部具名参数构造点，无法留下可编译的中间提交。故拆为「02a 移除语义（5 文件）→ 02b 机械删除（12 文件，零交互面，已记录偏离）」。
> - 04 的后端（新增接口与字段）与前端（DOM 重排）是两个独立可验证的子系统，且前端部分必须等 01/02b/03 的字段全部就位才能一次性重排，故拆为 04a/04b。
> - 原计划中「03 含前端」的安排被撤销：地区多选控件与日限额下线、执行轮次、cron 自定义落在**同一个 `#batchConfigEditor` modal**，合并到 04b 一次重排，避免对同一段 DOM 做 3~4 轮样式契约与 JS 测试改写。

### 执行顺序

```
01 (加轮次闸门) ──► 02a (拆日限额闸门) ──► 02b (删字段与列) ──┐
                                                              ├──► 04b (前端一次性重排) ──► 05 (中文标签 + 未分类)
03 (地区多选后端) ────────────────────────────────────────────┤
04a (cron 预览接口 + 执行时间字段) ───────────────────────────┘
```

**01 → 02a → 02b 是强制安全序。** `dailyCap` 目前是服务端唯一的「单次调度发送量」硬闸门（G-2）。先加 `roundsPerRun` 再拆 `dailyCap`，保证任何一次提交后系统都至少有一道服务端闸门。反序会出现「已删日限额、轮次上限未落地」的窗口期，此时 `oneRoundOnly = false` 的定时执行会一路跑到 ES 目标或账号容量耗尽。
02a 与 02b 之间也不可颠倒：02b 是纯机械字段删除，其「零语义变更」的验收前提由 02a 提供。

**01 / 03 / 04a 三者互不依赖，可并行。** 它们分别改 `roundsPerRun`、`regions_json`、`View` 的两个只读字段，无共享写路径。

**04b 依赖前四者全部完成。** 它一次性消费 `roundsPerRun`（01）、`dailyCap` 的消失（02b）、`regions`（03）、`nextFireTime`/`lastExecutedAt`/`cron 预览接口`（04a）。
⚠ **02b 与 04b 不可同时进行**：04b 之前的前端仍在 payload 中发 `dailyCap`（`app.js:13481`、`:13705`），依赖 Jackson 忽略未知字段；若 04b 先上线而 02b 未上线，前端不发 `dailyCap` 会命中当时仍存在的 `require(fields.dailyCap > 0)` 而 422。详见 02b 的 I-4 与交互点 X-1。

**05 依赖 03、04b。** 中文标签映射需要覆盖 04b 引入的地区多选控件；04b 已刻意把选项设计为 `{ value, label }` 双字段，05 只改 `label` 一列。

## 跨子计划共享不变量（每个子计划须复述并各自验证）

### G-1：地区常量是领域值，不可中文化

`CountryContinentMapping` 的 9 个大区英文串（`China` / `Asia (Japan & Korea)` / `Asia (Other)` / `Europe` / `North America` / `South America` / `Africa` / `Oceania` / `Other`）是领域常量，参与 ES term 查询构造（`countriesForRegion` → `esTermVariants`）。需求 4 的「改为中文」**只能作用于显示标签**；API 传值、DB 存值、ES 查询值必须保持英文原串。

- 违反后果：中文串进 `regionFilter` → ES 查不到任何文档 → 地区筛选静默全空。
- 来源：original（由 `CountryContinentMapping.kt:6-14,254-292` 审计得出）

### G-2：服务端始终存在至少一道单次调度发送量硬闸门

从 01 提交开始到 02 提交完成，`ManualInitialOutreachService` 的轮次循环必须始终受一个服务端配置字段约束（先是 `dailyCap`，01 后新增 `roundsPerRun`，02 后仅剩 `roundsPerRun` + 账号容量）。

- 违反后果：定时执行一次跑完全部 ES 目标，无法按小时铺开，且绕过运营对单次发送量的控制。
- 来源：original

### G-3：`UNCLASSIFIED` 学科的过滤实现必须同源

`ExpertSearchService.disciplineFilter()`（`ExpertSearchService.kt:55-65`）已正确实现 `UNCLASSIFIED` = `must_not exists disciplineCategory`，且 `ALLOWED_DISCIPLINES`（`:53`）已含该值。但存在**未复用它**的缺陷点：

| # | 位置 | 性质 | 后果 |
|---|---|---|---|
| 1 | `ManualInitialOutreachService.buildEsFiltersForLevel()` else 分支（`:1219`）直接写 `term disciplineCategory = it` | **活跃旁路** | ES 路径命中 0 条 |
| 2 | `RecipientScope.matchesExpert()`（`BatchExecutionModels.kt:54`）直接写 `profile.disciplineCategory != discipline` | **活跃缺陷** | 重试路径全量被过滤 |
| 3 | `BatchSendTaskConfigService.ALLOWED_DISCIPLINES`（`:473`）= `setOf("STEM","HUMANITIES")` | **白名单缺项** | 保存配置即 422，**最先暴露** |
| 4 | `BatchSendSettingService.ALLOWED_DISCIPLINES`（`:236`）= `setOf("","STEM","HUMANITIES")` | 白名单缺项（KV 兼容层） | 旧 typed API 拒绝，**05 有意不改** |
| 5 | `ManualInitialOutreachService.buildMaterialReminderEsFilters()`（`:1088`） | ⚠️ **死代码** | 当前无运行时影响 |
| 6 | 前端 `index.html:1199-1201`（配置编辑器）、`:1336-1338`（手动 tab）缺 option | UI 缺项 | 无法选择 |

> **#5 的判定依据（grep 实证，非推断）**：
> ```
> $ grep -rn "buildMaterialReminderEsFilters" --include=*.kt src/ | grep -v "private fun buildMaterialReminderEsFilters"
> （无输出）
> ```
> 材料提醒的发送与统计两条路径实际都经 `buildMaterialReminderSnapshotFromScope()`（`:1120`）的 `:1128` 调用 `buildEsFiltersForLevel()`。**本主计划初稿曾把它列为活跃旁路，系推断错误，已更正。**

需求 5 补下拉 option 时必须同步打通 #1、#2、#3、#6（#5 顺手修但无运行时效果，#4 有意留下并记录为已知限制）。

- 违反后果：需求 5 表面完成，实际不可用；且属于 K-batch-send-filter-retry-parity 的同类复发（ES 路径与重试路径口径不一致）。
- 来源：K-batch-send-filter-retry-parity（同类复发）+ 2026-08-12 grep 实测

### G-4：运行中只消费启动快照

任何子计划新增的配置字段（`roundsPerRun`、`regions`）都必须经 `BatchExecutionSnapshot` 传入执行循环，禁止在循环内重新读 `batch_send_task_config`。

- 违反后果：配置编辑污染在途任务。
- 来源：K-batch-task-config-snapshot-log-identity

### G-5：调度重排的触发条件是 cron ∪ autoEnabled

`BatchSendScheduler.reload()` 目前仅在 `scheduledCrons[configId] != cron` 时重排（`BatchSendScheduler.kt:62-73`）。04 引入自定义 cron 后该路径压力增大，必须确认「沿用原 cron、仅把 autoEnabled 由 false 改 true」的场景仍会重排。

- 违反后果：配置显示已启用但进程内无对应 future。
- 来源：K-batch-send-scheduler-reschedule-on-enable

## 明确不在本批范围

- 不改账号侧 `dailySendLimit` / 预热 ramp 的语义与配置入口（`SenderWarmupService`、`mail_sender_account`）
- 不改 `AccountRateLimiter` 的动态间隔算法
- 不改 `oneRoundOnly` 的手动单轮语义
- 不迁移 `batch_send_setting` KV 兼容表（K-batch-send-setting-kv：它是旧 typed API 的 KV 兼容层，不是配置 SSOT）
- 不引入「跨执行的自然日发送量统计」替代品。需求方已确认按账号限额兜底即可；`TaskExecutionService.sumSuccessCountTodayByBatchConfigId()` 在 02 之后将无生产调用方，**保留方法与其测试**，不在本批删除（留给后续独立清理）

## 全批新增迁移版本号（避免子计划间撞号）

当前最高已应用迁移为 `V87__append_unsubscribe_line_to_cold_outreach_templates.sql`（`ls src/main/resources/db/migration/ | sort -V | tail -1` 实测）。

⚠ **已发现冲突并已避让**：同目录的退订批次计划已占用 V88、V89（grep 实测）：

| 版本 | 文件 | 所属计划 |
|---|---|---|
| V88 | `V88__rewrite_unsubscribe_line_wording.sql` | `unsubscribe-06-html-anchor-body.md` |
| V89 | `V89__create_unsubscribe_token.sql` | `unsubscribe-07-opaque-token.md` |

故本批从 **V91** 起编号（留 V90 给退订批次可能的追加）：

| 版本 | 文件 | 所属计划 |
|---|---|---|
| V91 | `V91__add_rounds_per_run_to_batch_send_task_config.sql` | 01 |
| V92 | `V92__drop_daily_cap_from_batch_send_task_config.sql` | 02b |
| V93 | `V93__add_regions_to_batch_send_task_config.sql` | 03 |

04a / 04b / 05 **无迁移**。

**执行期强制步骤**：新建迁移前必须先跑
```bash
ls src/main/resources/db/migration/ | sort -V | tail -3
grep -rn "V9[0-9]__" docs/plans/ | grep -v "$(basename $PWD)"
```
确认目标版本号未被其他在途计划占用。若两个批次并行推进导致实际落地顺序与上表不同，**按实际落地顺序重新编号**并同步修改对应计划的「变更文件清单」与不变量中的文件名引用。**已应用的迁移一律不得编辑**（项目 CLAUDE.md 硬约束）。
三份迁移**均不得包含 `${...}`**（K-flyway-placeholder-replacement：生产 `application.yml` 未关 `placeholder-replacement`，含 `${}` 的新迁移会导致生产启动即抛 "No value provided for placeholder expressions"）。

## 计划撰写期的自查更正

写作过程中通过 grep 实证推翻了两条初稿推断，均已就地更正并留痕：

1. **`buildMaterialReminderEsFilters` 是死代码，不是活跃旁路**（影响 G-3、03、05）。见 G-3 的判定依据。
2. **`BatchSendTaskConfig` 等 data class 的新增字段必须带默认值**（影响 01、03）。`BatchSendTaskConfig(` 全仓 11 个构造点，其中 10 个在测试里（`BatchSendConfigControllerTest.kt:52,73`、`BatchSendSchedulerTest.kt:26`、`BatchSendTaskRuntimeIntegrationTest.kt:630`、`BatchSendControlServiceTest.kt:78,204`、`BatchSendTaskConfigServiceTest.kt:117,428,467`、`ManualInitialOutreachServiceTest.kt:2240`），生产侧仅 `BatchSendTaskConfigService.kt:51`。不带默认值会把 3 个范围外测试类拖进来，突破文件数硬约束。

同时更正了一条过期知识条目：`K-expert-filter-registration-sites` 记的 `styles.css:353`（`.toolbar-label`）实测在 `:431`，行号漂移千行量级，已在条目中标注「只能当存在性提示，必须 grep 复核」。

### 二次自查（应需求方质询「是否都有代码证据」，逐条复验后追加）

对全部计划中的量化断言做了一轮回归验证，又查出 4 处问题，均已就地修正：

| # | 位置 | 问题性质 | 更正 |
|---|---|---|---|
| 3 | 01 的 `updateProgressWithAccumulator` 调用点计数 | **确凿的猜测**——原写「介绍邮件 8 处、材料提醒 7 处」，我并未数过 | grep 实测：材料提醒 **5** 处（269/298/364/377/400）、介绍邮件 **8** 处（472/562/580/649/661/789/803/828）。另发现 `updateProgress()` 还有 1 个调用点（`:190`）此前完全漏列 |
| 4 | 02a 的 `dailySentTotal` 清理 | 计数对但**漏了陷阱** | 8 处命中中有 2 处（`:1050`/`:1319`）是进度 `details` 的**同名字符串 key**，取值来自 `sent`/`breakdown.success`，与被删变量无关。全局替换会误伤。已把验收断言从「grep 为空」改为「恰剩 2 行且均为字符串 key 形式」 |
| 5 | 02a 的 `countSentByMailTypeSince` | **漏了测试侧影响** | 生产侧确为唯一调用点，但测试里有 **6 处 Mockito stub**（`ManualInitialOutreachServiceTest` 1314/1454/1753/1811/1870/1906）。删生产调用后若启用 strict stubbing 会抛 `UnnecessaryStubbingException`，报错与 `dailyCap` 无关联、极易误判。已列为「本计划最可能让 CI 变红的一处」 |
| 6 | 04a 的 `BatchSendTaskConfigService` 实例化点 | **措辞错误** | 原写「仅测试中的 `service()` 工厂」，实测有 **2 处**（另有 `BatchSendConfigControllerTest.kt:33`）。结论未变（两处都已在文件清单内），但「仅」字是错的 |

**另查出一处此前未标注的框架假设**（04a）：`@Query` 返回 DTO 投影在本仓库**零先例**——grep 实测全部 `@Query` 方法只返回实体、标量或 `List<String>`。已在 04a 的 A-1 中加「执行前必做 spike」与两个有先例的降级方案，并把「框架假设已消解」列入验收标准。

> **方法论教训**：凡在计划里写出**具体数字**（N 个调用点、N 处残留）或**「仅/唯一」这类全称判断**，必须有对应的 grep 输出支撑。本轮 6 处问题中有 5 处是这两类。已蒸馏为 `K-plan-quantified-claims-need-grep-receipts`。

## 当前进度

- [x] 00 主计划（本文件）
- [x] 01 `batch-send-rhythm-01-rounds-per-run.md`
- [x] 02a `batch-send-rhythm-02a-remove-daily-cap-gates.md`
- [x] 02b `batch-send-rhythm-02b-drop-daily-cap-field.md`
- [x] 03 `batch-send-scope-03-region-multiselect-backend.md`
- [x] 04a `batch-send-console-04a-cron-preview-and-exec-time-backend.md`
- [x] 04b `batch-send-console-04b-editor-and-list-frontend.md`
- [x] 05 `expert-filter-05-region-i18n-and-unclassified.md`

计划已全部写完，可按「执行顺序」开始实施。每份子计划的 `## 人工验收清单` 在人工验收开始时导出为 `<plan-name>-acceptance.md`（create-p 规定：导出文件是衍生物，清单有误须先改 plan 内本节再重新导出）。
