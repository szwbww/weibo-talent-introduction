# 生产型/科研型专家分类与发信门禁主计划

## 需求描述

可观察结果：线上专家被版本化分类为生产研发、学术科研、混合研发、纯服务、医学越界或未知；所有 INTRODUCTION 首发路径仅允许明确 `sendable=true` 的生产型/科研型专家，纯临床医生和证据不足者不再进入发信目标。

必须保持不变：

- 原始姓名、邮箱、论文、专利、标签、运营状态字段不被分类回填覆盖。
- MATERIAL_REMINDER、已回复联系人处理、退订/抑制、配额、发件节奏保持现状。
- 发布应用不得自动启动 11.6 万或 428 万全量写入；生产回填必须人工触发。
- 不增加前端按钮；线上操作通过已登录的管理 API 和 SSH 内的 `curl` 执行。

范围外：接入 CORDIS/OpenAIRE/EPO、调用大模型逐人判断、删除现有候选人、修改学科分类、重抓全部外部数据、清洗历史已发送邮件。

## 关键不变量

### Invariant M-1: 安全失败
- Rule: INTRODUCTION 仅放行 `expertClassification.sendable == true`；字段缺失、`UNKNOWN`、映射异常均视为不可发送，不允许回退到旧的“有邮箱即可发送”。
- Applies to: 定时首发、队列首发、批量首发 ES 目标、批量首发 MySQL 重试目标、收件人预估。
- Violation consequence: 医生或未验证专家继续收到邮件。
- 来源: original

### Invariant M-2: 单一分类语义
- Rule: `rnd-v1-2026` 的归一化、证据、分数、优先级、类型和 `sendable` 只由 `ExpertClassificationService` 计算；回填、增量任务和发信代码不得复制关键词或重新推断。
- Applies to: 子计划 01、02、04 的所有分类调用。
- Violation consequence: 同一专家因入口不同得到不同结果。
- 来源: original

### Invariant M-3: 一个共享顶层字段
- Rule: 三个 ES 索引各只新增一个顶层对象 `expertClassification`；对象内保存类型、分数、证据、版本、指纹与分类时间。禁止并列增加 `sendable`、`expertType` 等第二事实源。
- Applies to: RAW、CANDIDATE、APPLICATION mapping 与所有写入路径。
- Violation consequence: 过滤字段和展示/审计字段漂移。
- 来源: original

### Invariant M-4: 回填只做局部更新
- Rule: 正式回填必须按 ES `_id` 使用 `_bulk update`，`doc_as_upsert=false`，只写 `expertClassification`，不写根级 `updatedAt`，不使用一次性 `_update_by_query`。
- Applies to: 候选、有效、原始索引正式回填。
- Violation consequence: 原始资料被覆盖、误建文档或任务不可控地压垮 ES。
- 来源: original

### Invariant M-5: 预估与执行同源
- Rule: 批量首发预估、ES 执行目标和 MySQL 重试目标必须执行同一 `sendable=true` 语义；MATERIAL_REMINDER 不应用该门禁。
- Applies to: `countBySnapshot`、`countEsTargets`、`fetchEsPage`、`buildRetryableTargets`、`RecipientScope.matchesExpert`。
- Violation consequence: 页面预估与实际发送不一致，或重试路径绕过门禁。
- 来源: K-batch-send-filter-retry-parity, K-recipient-count-preview-parity

### Invariant M-6: 发布与数据写入分离
- Rule: 应用发布只安装 mapping、代码和 API；初始回填只能按线上执行手册人工启动，先 DRY_RUN、再 CANDIDATE、再 APPLICATION、最后 RAW。
- Applies to: 子计划 02 的控制器、任务服务、线上手册；子计划 04 的调度默认配置。
- Violation consequence: 发布即产生不可预期的生产写放大。
- 来源: original

## 现状审计

### RAW / CANDIDATE / APPLICATION ES
- Schema/mapping: 三份 `src/main/resources/es/orcid_info_*.json` 均为 `dynamic:false`；现有字段有 employment、institution、researchFields、lastPublicationYear、hIndex、worksCount、recentWorkTitles、patentTitles，但没有专家类型或可发信字段。
- Write paths:
  1. `ExpertDiscoveryService.consumeOutcome → ExpertIndexWriterService.indexToRaw`、`promoteDiscoveredToCandidate`、`updateRawDocumentEmail`、`promoteRawToCandidateWithEmail`、`updateExpertAcademicFields` — 新发现、邮件补全、RAW→CANDIDATE、三层学术字段补全。
  2. `ExpertIndexWriterService.markApplicationClosed/syncOperatorStatus/syncOperatorStatusBatch/syncApplicationStatus/addTag/removeTag` — 现有局部字段更新，不替换 classification。
  3. `ExpertIndexWriterService.indexToRaw/promoteToCandidate/writeCandidateDocument/promoteToApplication/demoteToRaw` — 整文档 RAW 写入与层级复制；`removeFromCandidateIndex` 和 `demoteToRaw` 还执行删除。
  4. `ExpertRevalidationService.promoteRawToCandidate` — 经 `readRawDocument/writeCandidateDocument` 完整复制 RAW→CANDIDATE。
  5. `ExpertClassificationBackfillService → ExpertIndexWriterService.bulkUpdateExpertClassifications`（子计划 02 新增）— 唯一分类批量局部更新路径。
- Read paths:
  1. `ExpertSearchService.sourceFields/toExpertProfile` — 所有搜索、回填和重试内存过滤的共享反序列化路径。
  2. `ManualInitialOutreachService` — 批量 ES 目标、重试目标、预估。
  3. `InitialOutreachService` — 旧定时/队列首发。
- Interaction points: mapping → search 反序列化 → backfill 写入 → 两套首发读取；RAW→CANDIDATE→APPLICATION 的整文档复制必须保留对象字段。

### 批量首发
- Schema/mapping: `RecipientScope` 是 ES 与重试内存过滤的共享语义对象。
- Write paths: 本计划不新增 MySQL 写字段；现有发送成功/失败写法保持不变。
- Read paths:
  1. `ManualInitialOutreachService.buildEsFiltersForLevel` — ES 新目标。
  2. `RecipientScope.matchesExpert` — MySQL NEW 重试联系人加载 ES profile 后的内存过滤。
  3. `ManualInitialOutreachService.countBySnapshot` — 复用上述两条路径做预估。
- Interaction points: 新门禁必须同时进入 ES 和内存过滤；否则重试绕过或预估漂移。（来源: K-batch-send-filter-retry-parity, K-recipient-count-preview-parity）

### 旧定时/队列首发
- Schema/mapping: `InitialOutreachService.sendInitialBatch` 直接调用 `ExpertSearchService.searchExpertsWithEmail`，当前仅要求 email 存在。
- Write paths: 发送后写 `expert_contact`、`mail_record`，本计划不修改这些写语义。
- Read paths: `MailAutomationScheduler` 同步分支和 `MailQueueConsumer` 队列分支最终都进入 `sendInitialBatch`。
- Interaction points: 只改批量发送不足以覆盖旧定时/队列路径；共享查询必须增加分类门禁。

### 任务执行与进度
- Schema/mapping: `TaskProgressStore.tryStartWithToken/bindExecutionId` 提供互斥、进度持久化、取消；`TaskTypeCatalog` 是任务类型语义单一声明源。（来源: K-task-type-semantics-three-lists, K-progress-log-pending-token-orphan）
- Write paths: 新回填控制器通过 `TaskExecutionService.runAndRecordWithResult` 和 `TaskProgressStore.update` 写任务历史与批次进度。
- Read paths: `/api/task-progress/{taskType}`、`/logs`、`/executions`。
- Interaction points: 新任务类型必须登记 catalog；异步启动必须绑定 pending token，取消和终态必须与返回结果一致。

## 实现方案

按顺序执行并分别验证：

1. [01-expert-rnd-classification-core.md](./01-expert-rnd-classification-core.md) — 建立版本化、确定性的分类领域模型与 ES 对象 mapping。遵守 M-2、M-3。
2. [02-expert-rnd-classification-backfill.md](./02-expert-rnd-classification-backfill.md) — 增加 DRY_RUN/EXECUTE 后台任务、局部 bulk 更新、进度/取消和生产执行手册。遵守 M-2、M-4、M-6。
3. [03-expert-rnd-send-gate.md](./03-expert-rnd-send-gate.md) — 覆盖全部 INTRODUCTION 入口并保持预估/执行一致。遵守 M-1、M-5。
4. [04-expert-rnd-incremental-classification.md](./04-expert-rnd-incremental-classification.md) — 初始回填完成后，按显式开关处理新增未分类文档。遵守 M-2、M-6。

发布顺序：四个子计划可逐个实现验证，但生产发布建议一次发布；因为子计划 03 会安全失败，发布后未回填的 INTRODUCTION 目标数为 0。随后严格按子计划 02 交付的 runbook 做线上 DRY_RUN 与回填。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| 无 | 主计划不直接授权业务文件；以四个子计划各自的穷举清单为准 |

执行 agent 禁止把跨子计划共享文件并行修改；必须按 01→02→03→04 顺序串行落地并在每步运行对应测试。

## 验收标准

- M-1: 自动测试证明字段缺失、false、UNKNOWN、SERVICE_ONLY、OUT_OF_SCOPE 均不进入任何 INTRODUCTION 目标；true 才进入。
- M-2: grep 证明分类关键词和分数仅存在于 `ExpertClassificationService` 及其测试；其他文件只调用结果。
- M-3: 三份 mapping 只有一个新顶层 `expertClassification`；无根级 `sendable` 或 `expertType`。
- M-4: bulk 请求逐条含 `update` + `doc` + `doc_as_upsert:false`，`doc` 仅含 `expertClassification`；无 `_update_by_query`。
- M-5: 同一 snapshot 的 preview 总数等于执行的 totalEstimate，重试和 ES 新目标均排除不可发专家；MATERIAL_REMINDER 测试零变化。
- M-6: 应用启动测试不调用 backfill；增量调度默认关闭；只有显式管理 API 可启动初始回填。

## 人工验收清单

### A-1: 发布后安全失败
- 前置条件: 测试环境存在一名有邮箱但无 `expertClassification` 的 CANDIDATE；INTRODUCTION 配置可做收件人预估。
- 操作步骤: 1. 发布四个子计划；2. 不运行回填；3. 请求 INTRODUCTION 收件人预估；4. 触发一次大小为 1 的手动首发。
- 预期结果: 预估为 0；任务发送数为 0；该专家没有新增 OUTBOUND/INTRODUCTION/SENT 邮件。
- 覆盖: M-1、M-6、需求描述第 1 条

### A-2: 候选回填后只放行研发专家
- 前置条件: 准备 4 名候选：明确临床医生、近期论文科研人员、专利+产品研发人员、信息不足人员；均有有效邮箱。
- 操作步骤: 1. 运行 CANDIDATE DRY_RUN；2. 运行正式回填；3. 查询四人的分类；4. 请求首发预估；5. 执行手动首发。
- 预期结果: 类型依次为 SERVICE_ONLY、ACADEMIC_RND、PRODUCTION_RND、UNKNOWN；仅中间两人 `sendable=true`；预估为 2；实际目标为 2，医生和未知人员无发送记录。
- 覆盖: M-1、M-2、M-4、M-5

### A-3: 重试路径不绕过
- 前置条件: MySQL 中存在两个未发送成功的 NEW 联系人；其 ES profile 分别 `sendable=true`、`sendable=false`。
- 操作步骤: 1. 请求同一 snapshot 的预估；2. 启动批量首发；3. 查看任务结果。
- 预期结果: retryable=1；实际仅发送 `sendable=true` 联系人；false 联系人仍无 SENT 记录。
- 覆盖: M-5、K-batch-send-filter-retry-parity

### A-4: 材料提醒回归
- 前置条件: 存在一名 APPLICATION 联系人，标签为“承诺回复材料”，其分类字段缺失。
- 操作步骤: 1. 请求 MATERIAL_REMINDER 预估；2. 执行一次材料提醒。
- 预期结果: 该联系人仍计入预估并可发送；分类门禁不参与 MATERIAL_REMINDER。
- 覆盖: M-5、必须保持不变第 2 条

### A-5: 原字段不被覆盖
- 前置条件: 记录一名候选回填前的 email、employment、tags、operatorStatus、updatedAt 原值。
- 操作步骤: 1. 正式回填该文档；2. 重新读取完整 `_source`。
- 预期结果: 五个原值逐字相同；仅新增/替换 `expertClassification`。
- 覆盖: M-3、M-4、必须保持不变第 1 条

### A-6: 发布不自动回填
- 前置条件: 测试环境至少有 10 条无分类 CANDIDATE。
- 操作步骤: 1. 重启应用；2. 等待 2 分钟；3. 查询缺失 `expertClassification` 的数量和任务历史。
- 预期结果: 缺失数仍为 10；没有新建 EXPERT_CLASSIFICATION_BACKFILL 执行记录。
- 覆盖: M-6、必须保持不变第 3 条

人工验收开始时，从本节导出 `00-expert-rnd-classification-master-acceptance.md`；不得提前生成。
