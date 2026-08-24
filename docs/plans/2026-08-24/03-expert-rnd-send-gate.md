# 子计划 03：INTRODUCTION 研发专家硬门禁

## 需求描述

可观察结果：所有 INTRODUCTION 首发入口仅选择并发送 `expertClassification.sendable=true` 的专家；缺字段、未知、纯服务、医学越界全部安全排除。批量收件人预估与实际执行一致。

必须保持不变：MATERIAL_REMINDER 不受分类门禁；现有邮箱、运营状态、标签、地区、学科、模板字段、抑制、配额、发件节奏过滤继续生效；不新增发送配置项或前端开关。

范围外：分类计算、分类回填、数据源补全、历史邮件处理、前端分类筛选。

前置：子计划 01 完成；生产启用发送前必须按子计划 02 回填 CANDIDATE。

## 关键不变量

### Invariant I3-1: INTRODUCTION fail closed
- Rule: `expertClassification?.sendable == true` 是 INTRODUCTION 必要条件；null/false/解析失败都拒绝。任何发送入口不得提供 bypass、默认 true 或配置关闭开关。
- Applies to: 旧定时/队列查询与最后发送检查；批量 ES 查询、重试内存过滤、最后发送检查。
- Violation consequence: 医生或未分类专家被发送。
- 来源: original

### Invariant I3-2: ES 与内存谓词等价
- Rule: ES 谓词固定为 `term expertClassification.sendable=true`；内存谓词固定为 `profile.expertClassification?.sendable == true`，仅在 mailType=INTRODUCTION 应用。
- Applies to: `ExpertSearchService`、`RecipientScope.matchesExpert`、`ManualInitialOutreachService.buildEsFiltersForLevel`。
- Violation consequence: MySQL 重试联系人绕过或 ES/重试结果分裂。
- 来源: K-batch-send-filter-retry-parity

### Invariant I3-3: 预估与执行同源
- Rule: `countBySnapshot`、`countEsTargets`、`fetchEsPage` 必须继续复用 `buildEsFiltersForLevel`；重试预估和执行继续复用 `buildRetryableTargets`，不得为预估单写分类 count。
- Applies to: 批量 INTRODUCTION 预估与执行。
- Violation consequence: 预估可发 N 人，实际发 M 人。
- 来源: K-recipient-count-preview-parity

### Invariant I3-4: 最后发送前再次检查
- Rule: 两个真正调用 mail delivery 的 service 在创建联系人/渲染邮件前再次检查 sendable；失败记录 `EXPERT_NOT_SENDABLE` skipped reason，绝不创建新 contact 或 mail_record。
- Applies to: `InitialOutreachService.sendInitialBatch`、`ManualInitialOutreachService` round loop。
- Violation consequence: 查询/缓存/未来重构错误可绕过硬门禁。
- 来源: original

### Invariant I3-5: MATERIAL_REMINDER 零影响
- Rule: mailType=MATERIAL_REMINDER 时不追加 ES sendable term、不执行内存分类拒绝、不执行 INTRODUCTION 最后门禁。
- Applies to: `RecipientScope.matchesExpert`、`buildEsFiltersForLevel`、材料提醒 snapshot 与发送循环。
- Violation consequence: 已经回复并承诺材料的联系人因旧数据缺分类而无法跟进。
- 来源: original

## 现状审计

### 旧定时/队列首发
- Schema/mapping: `InitialOutreachService.sendInitialBatch:32-33` 直接调用 `searchExpertsWithEmail`；该查询 `ExpertSearchService:297-327` 只加 `exists email`。
- Write paths: `sendInitialBatch` 在发送前创建 ExpertContact，随后 success/failure 写 mail_record；本计划不改变成功/失败写语义。
- Read paths: `MailAutomationScheduler.scheduleInitialOutreach` 同步路径和 `MailQueueConsumer` 队列路径最终都调用同一个 `sendInitialBatch`。
- Interaction points: 共享查询先过滤，service 最后检查；两处缺一都会留下绕过面。

### 批量 INTRODUCTION ES 新目标
- Schema/mapping: `ManualInitialOutreachService.buildEsFiltersForLevel:1277-1301` 是 count 与 page fetch 的活跃共享 filter seam。
- Write paths: 目标通过后才创建/复用 contact、写 mail attempt/record。
- Read paths: `countEsTargets:1243-1249` 与 `fetchEsPage:1252-1274` 均复用该 filter。
- Interaction points: 在共享 filter 末尾追加 sendable term即可覆盖预估和执行；不得只在取回后 filter 导致 under-filled page 或 totalEstimate 偏大。

### 批量 INTRODUCTION MySQL 重试目标
- Schema/mapping: `buildRetryableTargets:985-1019` 从 MySQL NEW 联系人取 ORCID，再从 ES 各 funnel level加载 profile，最后调用 `scope.matchesExpert`。
- Write paths: 重试发送复用现有 contact，不新建。
- Read paths: preview 和执行都调用 `buildRetryableTargets`。（来源: K-batch-send-filter-retry-parity, K-recipient-count-preview-parity）
- Interaction points: `RecipientScope.matchesExpert` 必须有等价内存门禁，否则重试绕过 ES filter。

### MATERIAL_REMINDER
- Schema/mapping: 材料提醒使用相同 RecipientScope 类型，但发送对象是已存在 APPLICATION 联系人。
- Write paths: reminder mail record。
- Read paths: `buildMaterialReminderSnapshotFromScope` 调 `buildEsFiltersForLevel`。
- Interaction points: 分类 term 必须按 mailType 条件追加，不能全局追加。

## 实现方案

### Task 1：共享 sendable 谓词（I3-1、I3-2）

修改文件：

- `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt`

要求：

- companion object 新增唯一 `expertSendableFilter()`，逐字返回 `mapOf("term" to mapOf("expertClassification.sendable" to true))`。
- 新增 `searchSendableExpertsWithEmail(size, level=CANDIDATE)`；查询 filter 为 `exists email AND expertSendableFilter()`。
- 保留现有 `searchExpertsWithEmail` 行为，避免把通用读 API 悄悄改成发信专用。
- 测试断言 true term 存在，字段缺失/false 不命中，查询仍按现有层级排序。

### Task 2：旧定时/队列首发双门禁（I3-1、I3-4）

修改文件：

- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt`

要求：

- 目标查询改用 `searchSendableExpertsWithEmail`。
- forEach 首行再次判断 `expert.expertClassification?.sendable == true`；否则 skipped++ 并 continue，且 verify contact repository save、composer、delivery 均无调用。
- 不改变 requested/candidates/sent/failed/skipped 既有含义；被最后门禁挡住计 skipped。

### Task 3：批量 ES 与重试同口径（I3-1～I3-3、I3-5）

修改文件：

- `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`

要求：

- `RecipientScope.matchesExpert` 在 `mailType == INTRODUCTION` 时先做内存 sendable 检查；MATERIAL_REMINDER 跳过。
- `buildEsFiltersForLevel` 在所有 INTRODUCTION funnel level 的 filters 末尾追加 `ExpertSearchService.expertSendableFilter()`；MATERIAL_REMINDER 不追加。
- 不改 `countBySnapshot/countEsTargets/fetchEsPage/buildRetryableTargets` 的调用拓扑，以复用共享 seam。
- `BatchOutcomeReasonCodes` 新增 `EXPERT_NOT_SENDABLE`，中文 label=`专家非生产/科研可发类型`。

### Task 4：批量最后门禁（I3-4）

修改文件：

- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`

要求：

- INTRODUCTION round loop 从 iterator 取出 profile 后、邮箱/账号/contact 处理前再次检查 sendable。
- 拒绝时 accumulator.recordSkipped(EXPERT_NOT_SENDABLE)，递增 processed/roundProcessed/roundRejected；不递增成功；更新进度后 continue。
- 测试构造 iterator 意外返回 false/null profile，断言不创建 contact、不选择账号、不渲染、不发送。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 共享 ES 谓词与发信专用查询 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 重试内存门禁与 reason code |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | ES filter、最后门禁 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 旧路径查询与最后门禁 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | ES 查询测试 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 批量四路径测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | 旧路径测试 |

共 7 个文件、2 个子系统（专家查询、campaign 发送）、不新增 store 字段。

## 验收标准

- I3-1: 参数化测试对 null/false/三种不可发 type 全部拒绝，对三种可发 type 全部允许；无 bypass 配置。
- I3-2: ES query 精确含 term true；内存 fixture 与 ES fixture 对相同分类集合得到相同 ORCID 集合。
- I3-3: 同一 INTRODUCTION snapshot 的 preview totalSendable 与 runIntroductionFromSnapshot totalEstimate 相同；sendable false 同时从 pending 和 retryable 排除。
- I3-4: 两个最后门禁测试均 verifyNoInteractions composer/delivery，且 manual reason 为 EXPERT_NOT_SENDABLE。
- I3-5: MATERIAL_REMINDER 查询 JSON 不含 expertClassification；缺分类 APPLICATION fixture 仍计入并发送。
- 回归：所有现有邮箱、状态、标签、地区、学科、门禁字段、抑制、quota 测试继续通过；`mvn test` 全绿。

## 人工验收清单

### A3-1: 旧定时/队列路径排除不可发专家
- 前置条件: 为同步和队列分别准备独立 campaign；每个 campaign 对应的 CANDIDATE 池均有两个有效邮箱，分别 sendable=true/false，且无历史 contact。
- 操作步骤: 1. 调用 `POST /api/mail/initial-outreach?campaignId=<SYNC_TEST_ID>&size=2`；2. 调用 `POST /api/mail/initial-outreach/async?campaignId=<QUEUE_TEST_ID>&size=2`；3. 等待 queue consumer 完成；4. 分 campaign 查询 contact/mail records。
- 预期结果: 两个 campaign 均只为 true 专家创建 1 条 contact 和 1 条 SENT/FAILED attempt；false 专家无 contact、无 mail record。
- 覆盖: I3-1、I3-4、需求描述

### A3-2: 批量 ES 目标预估/执行一致
- 前置条件: 两名 NOT_CONTACTED CANDIDATE，其他过滤均通过，分别 true/false。
- 操作步骤: 1. 请求 INTRODUCTION preview；2. 启动大小 2 的手动执行；3. 查看执行 target 和 mail records。
- 预期结果: pending=1、totalSendable=1、execution target=1；仅 true 专家进入发送。
- 覆盖: I3-1～I3-3

### A3-3: MySQL 重试目标不绕过
- 前置条件: 两名 NEW 未成功发送联系人，ES profile 分别 true/false。
- 操作步骤: 1. preview；2. 执行；3. 查 retryable、target、mail records。
- 预期结果: retryable=1、target=1；false 联系人无新增 OUTBOUND INTRODUCTION。
- 覆盖: I3-2、I3-3、K-batch-send-filter-retry-parity

### A3-4: 缺分类安全失败
- 前置条件: 一名有邮箱的 CANDIDATE 无 expertClassification。
- 操作步骤: 分别走旧首发、批量 ES、构造 NEW 重试三种入口。
- 预期结果: 三种入口目标均为 0；无 contact/mail 新写入。
- 覆盖: I3-1、I3-4

### A3-5: MATERIAL_REMINDER 回归
- 前置条件: 一名缺分类的 APPLICATION 联系人满足材料提醒现有条件。
- 操作步骤: 1. preview；2. 执行一次 reminder。
- 预期结果: preview=1、target=1，邮件按现有流程发送；ES 请求不含 sendable term。
- 覆盖: I3-5、必须保持不变第 1 条

人工验收开始时导出 `03-expert-rnd-send-gate-acceptance.md`。
