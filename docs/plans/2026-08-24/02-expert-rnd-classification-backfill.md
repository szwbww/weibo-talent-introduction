# 子计划 02：专家分类模拟与线上回填任务

## 需求描述

可观察结果：登录管理员可通过无前端按钮的异步 API 对指定 ES 层执行 DRY_RUN 或正式分类回填，查看进度、取消、重复执行；正式模式仅局部写入 `expertClassification`。同时交付发布后人工执行的线上 runbook。

必须保持不变：发布/启动应用不自动回填；DRY_RUN 零 ES 写入；原文档其他字段逐字不变；同一时刻最多一个分类任务；现有任务类型和进度接口语义不变。

范围外：分类规则修改、发信门禁、自动增量调度、前端按钮、新任务表/数据库迁移、外部 API enrichment。

前置：子计划 01 已通过验证并部署到执行分支。

## 关键不变量

### Invariant I2-1: 模拟模式零写入
- Rule: `mode=DRY_RUN` 只扫描、分类和聚合，禁止调用 `ExpertIndexWriterService.bulkUpdateExpertClassifications` 或任何 ES PUT/POST 写方法。
- Applies to: backfill service、controller、测试、runbook 第一次执行。
- Violation consequence: 用户以为预览，实际污染生产数据。
- 来源: original

### Invariant I2-2: 正式模式局部且不 upsert
- Rule: 每个 bulk item 按 `ExpertProfile.esDocId` 更新原索引 `_id`；body 只能是 `doc.expertClassification` + `doc_as_upsert:false`，禁止根级 `updatedAt`、禁止自动创建缺失文档、禁止 `_update_by_query`。
- Applies to: `ExpertIndexWriterService.bulkUpdateExpertClassifications`。
- Violation consequence: 覆盖原资料、误建重复专家、放大不可回滚。
- 来源: original

### Invariant I2-3: 显式目标和双重确认
- Rule: 请求必须显式提供 level、mode、version；version 只能等于 `rnd-v1-2026`。所有 EXECUTE 要求 confirmation 精确等于 `EXECUTE_<LEVEL>:rnd-v1-2026`；RAW 不允许默认值。
- Applies to: request validation、controller、runbook。
- Violation consequence: 误把 DRY_RUN、CANDIDATE 或测试版本写到 428 万 RAW。
- 来源: original

### Invariant I2-4: 可取消、可重跑、失败可见
- Rule: 每批前检查取消；成功写入的同版本文档在默认 `onlyPending=true` 重跑时跳过；bulk 单项失败保留在 pending 集合并记录总数、原因和最多 100 个 `_id`，任务不得把部分失败报告为 SUCCESS。
- Applies to: scan filters、progressStore、结果对象、task execution 终态。
- Violation consequence: 中断后只能从头人工修数据，或运营误判全部完成。
- 来源: K-circuit-breaker-terminal-status

### Invariant I2-5: 异步任务互斥和身份绑定
- Rule: taskType 固定 `EXPERT_CLASSIFICATION_BACKFILL`；启动用 `tryStartWithToken`，executor 内 `runAndRecordWithResult`，onStarted 绑定 token→executionId，所有进度更新带 expected executionId，finally 清理对应上下文。
- Applies to: controller、TaskTypeCatalog、progress/cancel API。
- Violation consequence: HTTP 超时、并发双跑、旧任务覆盖新任务进度。
- 来源: K-task-type-semantics-three-lists, K-progress-log-pending-token-orphan

### Invariant I2-6: 统计口径完整
- Rule: 结果至少包含 scanned、classifiedByType 六类计数、sendable、notSendable、writeSuccess、writeNoop、writeFailure、skippedMissingDocId、reasonCounts、wasCancelled、policyVersion、level、mode；`scanned = 六类计数之和`，`sendable = 前三类之和`。
- Applies to: result DTO、progress details、TaskExecutionSummaryProvider、runbook 验收。
- Violation consequence: 无法判断规则质量或写入完整性。
- 来源: original

## 现状审计

### 三层 ES
- Schema/mapping: 子计划 01 新增一个 `expertClassification` 顶层对象；现有索引仍 `dynamic:false`，应用启动由 `ExpertIndexService.bootstrapMappings` 推送新增 mapping。
- Write paths:
  1. `ExpertDiscoveryService.consumeOutcome/promoteDiscoveredToCandidate/updateRawDocumentEmail/promoteRawToCandidateWithEmail/updateExpertAcademicFields` — 发现、晋级与 enrichment。
  2. `ExpertIndexWriterService.markApplicationClosed/syncOperatorStatus/syncOperatorStatusBatch/syncApplicationStatus/addTag/removeTag` — 现有局部更新。
  3. `ExpertIndexWriterService.indexToRaw/promoteToCandidate/writeCandidateDocument/promoteToApplication/demoteToRaw/removeFromCandidateIndex` — 整文档写、层级复制与删除。
  4. 本计划新增 `ExpertIndexWriterService.bulkUpdateExpertClassifications`。
- Read paths: `ExpertSearchService.searchAfterExpertsFiltered` 可按 filters、orcidId sort、batch size 读取 ExpertProfile；默认 `_source` 已由子计划 01包含分类对象。
- Interaction points: 扫描过程中写入会让文档退出“缺失或版本不匹配”过滤集合；sort key 不改变，失败文档留待下一次重跑。

### ExpertIndexWriterService
- Schema/mapping: 已有 `syncOperatorStatusBatch` 使用 NDJSON `_bulk`、每批 500、`doc_as_upsert=false` 和逐 item 状态解析；已有 `promoteToApplication/promoteToCandidate` 整文档复制。
- Write paths: 本计划只在此服务增加分类 bulk 方法，禁止 backfill service 自己复制认证头和 bulk 协议。
- Read paths: backfill service 消费返回的 success/noop/failure/错误样本。
- Interaction points: bulk 响应 `result=updated/noop` 与 HTTP status 都要解析；404 计 failure/skip，不得 upsert。

### 任务与控制 API
- Schema/mapping: `TaskTypeCatalog` 是 task type 单一声明源；`TaskProgressStore` 支持 pending token、互斥、取消、持久化日志；全 `/api/**` 由 `AuthInterceptor` 要求管理员 session。
- Write paths: `TaskExecutionService.runAndRecordWithResult` 写 task_execution；`TaskProgressStore.update` 写 task_progress_log。
- Read paths: `/api/task-progress/EXPERT_CLASSIFICATION_BACKFILL`、`/logs`、`/executions`。
- Interaction points: catalog 登记 `hasProgressUi=true` 才能查询 executions；无需新增前端按钮。（来源: K-task-type-semantics-three-lists；K-allowedTaskTypes-whitelist 已被现代码的 catalog 派生替代）

### 生产首发暂停路径
- Schema/mapping: 多配置批量首发通过 `/api/mail/batch-send/configs` 列出并可 `PATCH /configs/{id}/enabled`；兼容 INTRODUCTION 调度可 `POST /types/INTRODUCTION/pause-schedule`；旧 scheduler 由 `MAIL_SCHEDULING_ENABLED` 和 `MAIL_SCHEDULING_INITIAL_OUTREACH_CRON` 控制。
- Write paths: runbook 只改变已有批量发送配置 enabled/runtime 状态，不新增存储结构。
- Read paths: 批量配置列表/状态接口和环境变量检查。
- Interaction points: 线上回填前必须同时确认多配置 scheduler 与旧 initial-outreach scheduler 不会发信。

## 实现方案

### Task 1：分类 bulk 唯一写入口（I2-2、I2-4）

修改文件：

- `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt`

新增 `bulkUpdateExpertClassifications(level, updates)`：

- updates 元素只包含 `esDocId + ExpertClassification`。
- 以调用方传入 level 解析唯一目标 index，不跨三层循环。
- 每批上限 1000，实际由 backfill request 控制。
- NDJSON meta 使用 `_index` 和 `_id`；data 逐字结构为 `{"doc":{"expertClassification":...},"doc_as_upsert":false}`。
- 不调用 ORCID→docId 二次查询；esDocId 为空由 service 上游计 skipped。
- 返回每项 updated/noop/failure；保存最多 100 个失败样本但统计全部失败。

### Task 2：扫描、聚合、限速、取消（I2-1～I2-4、I2-6）

修改文件：

- 新增 `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt`
- 新增 `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillServiceTest.kt`

请求模型固定字段：

```text
level: RAW | CANDIDATE | APPLICATION（必填）
mode: DRY_RUN | EXECUTE（必填）
version: rnd-v1-2026（必填且只允许此值）
batchSize: 500（范围100..1000）
delayMs: 250（范围0..5000）
maxDocs: null（可选，正整数；用于抽样）
onlyPending: true
confirmation: EXECUTE_<LEVEL>:rnd-v1-2026（EXECUTE必填）
```

扫描 filter：

- `onlyPending=true`：`must_not exists expertClassification.version OR must_not term version=rnd-v1-2026`，两分支 should/minimum_should_match=1。
- `onlyPending=false`：match_all；仅用于明确 force 重算，不允许 controller 默认。
- 使用 `searchAfterExpertsFiltered`，batch handler 内分类；DRY_RUN 只聚合，EXECUTE 调 writer。
- 每批完成写 progress；delay 可取消地分段等待，单次 sleep 不超过 1 秒并重复检查取消。
- result 实现 `TaskExecutionSummaryProvider`：successCount=writeSuccess（DRY_RUN 时为 scanned），failureCount=writeFailure，取消返回 `taskFinalStatus=CANCELLED`，部分失败为 PARTIAL_SUCCESS，全失败为 FAILED。
- 遇到 mapping 不存在/首批全部 400 mapper error 立即 FAILED；不得继续刷 428 万失败请求。

### Task 3：无前端管理 API（I2-3、I2-5）

修改文件：

- 新增 `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt`
- 新增 `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt`
- `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt`

端点：

- `POST /api/expert-classification/backfill`：校验请求、抢互斥锁、提交单线程 `expertClassificationExecutor`，立即返回 HTTP 202 `{"message":"任务已启动","taskType":"EXPERT_CLASSIFICATION_BACKFILL"}`。
- 同任务运行中返回 409；executor 拒绝返回 409 并清理 pending context。
- status/log/cancel 不另造端点，复用 `/api/task-progress/EXPERT_CLASSIFICATION_BACKFILL`、`/logs`、`/cancel`。
- 不豁免 AuthInterceptor；未登录请求必须 401。

### Task 4：任务目录登记（I2-5、I2-6）

修改文件：

- `src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalog.kt`
- 新增 `src/test/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalogTest.kt`

登记：code=`EXPERT_CLASSIFICATION_BACKFILL`、label=`专家研发类型回填`、group=`MANUAL`、metricLabel=`已处理/失败`、summaryRule=null、hasProgressUi=true、drilldown=null。测试断言 executions API 白名单由 catalog 派生后包含该类型。

### Task 5：编写线上执行手册（I2-1～I2-6）

修改文件：

- 新增 `docs/runbooks/expert-classification-backfill.md`

手册必须包含可复制命令，但不得写任何真实密码/token：

1. 发布前：备份/快照确认；记录 CANDIDATE/APPLICATION/RAW 精确 doc count；列出所有启用的 INTRODUCTION batch configs；确认/暂停运行任务；检查 `MAIL_SCHEDULING_ENABLED` 与 `MAIL_SCHEDULING_INITIAL_OUTREACH_CRON`。
2. 发布后 mapping 检查：对三层查询 `expertClassification.type/sendable/version` mapping；任一缺失立即停止。
3. 登录：用 `/api/auth/login` 获取仅存 `/tmp`、权限 600 的 cookie jar；变量名不得使用 HOME/CODEX_HOME；结束后安全删除 cookie jar。正文使用 `<ADMIN_USERNAME>`、`<ADMIN_PASSWORD>` 占位。
4. CANDIDATE 全量 DRY_RUN：batchSize=500、delayMs=250、onlyPending=false；轮询 status/log；记录六类比例、sendable 数和 top reasons。
5. 抽样：每类随机至少 100 人；医生漏网 0/100，科研/生产误杀率由用户人工确认后才继续。手册不把抽样 API 的新增实现塞入本计划；用只读 ES 查询完成。
6. CANDIDATE EXECUTE：confirmation 精确值；监控 bulk failure、ES cluster health、CPU、JVM；failure>0 或 cluster health red 即 cancel，修复后 onlyPending=true 重跑。
7. APPLICATION：先 DRY_RUN 后 EXECUTE；数量虽小仍走相同步骤。
8. 恢复 INTRODUCTION 前：查询 CANDIDATE 中 `sendable=true`、false、缺失的精确数；执行子计划 03 的 A3 场景。
9. RAW：只在候选稳定后单独维护窗口执行；先 maxDocs=10000 DRY_RUN，再全量 DRY_RUN，再 EXECUTE；默认 delayMs≥250；不可与 discovery/enrichment 并跑。
10. 回滚：停止任务；不删除分类字段；通过暂停 INTRODUCTION 保持安全。若确需数据回滚，只允许按 execution 前快照恢复，禁止手写 `_update_by_query` 删除对象。
11. 完成记录：task execution id、policy version、各层 counts、失败/重跑次数、人工抽样人和时间。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | 分类 bulk 唯一写入口 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt` | 扫描、分类、进度、结果 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt` | 异步管理 API |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt` | 单线程分类 executor |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalog.kt` | 新任务类型 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` | bulk NDJSON 测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillServiceTest.kt` | 回填行为测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt` | API/互斥/认证测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalogTest.kt` | catalog 测试 |
| 10 | `docs/runbooks/expert-classification-backfill.md` | 线上执行手册 |

共 10 个文件、2 个子系统（专家维护、任务基础设施）、不新增共享 store 字段。

## 验收标准

- I2-1: DRY_RUN 处理多批 fixture，verify writer 零调用；ES mock 仅收到 search 请求。
- I2-2: 逐字断言 bulk NDJSON 每项只含分类对象与 `doc_as_upsert:false`；原 `_source` fixture 回读其他字段相同；grep 无 `_update_by_query`。
- I2-3: 缺 level/mode/version、错误 version、EXECUTE confirmation 不匹配均 400；RAW 无专用确认绝不写入。
- I2-4: 第二批取消返回 CANCELLED；部分 item 500 返回 PARTIAL_SUCCESS 和完整 failure 总数；同版本重跑查询只命中 pending。
- I2-5: 并发第二次启动 409；202 不等待任务结束；token 成功绑定真实 executionId；stale finally 不清理新任务。
- I2-6: 六类之和、sendable 之和、write 计数恒等式均由测试断言；TaskExecution success/failure 与 result 一致。
- Runbook: 包含上述 11 节、所有危险操作停止条件、无真实 secret、无自动回填指令。
- 回归：`mvn test` 全绿；应用启动测试证明 writer 未被调用。

## 人工验收清单

### A2-1: DRY_RUN 零写入
- 前置条件: 测试 CANDIDATE 有 10 条无分类文档，记录完整 `_source` 哈希。
- 操作步骤: 1. 登录；2. POST DRY_RUN maxDocs=10；3. 轮询任务完成；4. 重新计算 `_source` 哈希。
- 预期结果: scanned=10、六类之和=10、writeSuccess/writeNoop/writeFailure 均 0；10 条哈希逐条相同。
- 覆盖: I2-1、I2-6

### A2-2: 正式局部回填
- 前置条件: 同 10 条文档，保存非分类字段 JSON。
- 操作步骤: 1. POST EXECUTE CANDIDATE，confirmation=`EXECUTE_CANDIDATE:rnd-v1-2026`；2. 等待完成；3. 回读文档。
- 预期结果: 10 条均有完整分类对象；非分类字段 JSON 逐字相同；writeFailure=0。
- 覆盖: I2-2、I2-3

### A2-3: 中断与重跑
- 前置条件: 测试索引 2000 条，batchSize=100、delayMs=1000。
- 操作步骤: 1. 启动 EXECUTE；2. 至少完成 2 批后调用 cancel；3. 等待 CANCELLED；4. 用 onlyPending=true 重启。
- 预期结果: 第一次终态 CANCELLED；第二次不重写已完成同版本文档，最终 2000 条全部有 v1，累计无重复文档。
- 覆盖: I2-4、I2-5

### A2-4: 认证与并发
- 前置条件: 一个任务正在运行。
- 操作步骤: 1. 无 cookie 启动；2. 有 cookie 再启动一次；3. 查询 progress。
- 预期结果: 第一步 401；第二步 409；原任务 executionId 和进度不变。
- 覆盖: I2-5、必须保持不变第 4 条

### A2-5: 发布不自动写
- 前置条件: 10 条无分类文档。
- 操作步骤: 1. 部署并重启；2. 等待 2 分钟；3. 查询存在分类字段数量及任务历史。
- 预期结果: 存在数 0；无 EXPERT_CLASSIFICATION_BACKFILL 新记录。
- 覆盖: 必须保持不变第 1 条

### A2-6: Runbook 演练
- 前置条件: 隔离测试环境、管理员测试账号、三层各有 fixture。
- 操作步骤: 由未参与开发的人逐条执行 runbook 至 CANDIDATE 完成，不读源码。
- 预期结果: 每条命令可直接执行；能暂停首发、完成 DRY_RUN、抽样、EXECUTE、查询进度、取消和重跑；没有一步要求猜测参数或真实凭证位置。
- 覆盖: I2-1～I2-6、需求描述

人工验收开始时导出 `02-expert-rnd-classification-backfill-acceptance.md`。
