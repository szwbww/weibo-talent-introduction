# 03：审核所有页

依赖：02。复用02的两张表与现有TaskExecution，不新增共享存储字段。

## 需求描述

“审核通过所有页”覆盖所显示服务端筛选范围的全部待审核专家，先固定名单、展示准确人数，再确认后台执行；支持刷新、取消及失败重试。保持当前页/所选审核的精确范围、同身份批准和审计语义。

范围外：分布式任务平台、自动循环审批新发现专家、浏览器逐页点击脚本、真实发信。

## 关键不变量

### Invariant I-1：全页快照完整后才能确认
- Rule：prepare(scope=ALL_MATCHING)固定规范筛选、action、准备人、时间；服务端ES scroll每批500，扫描完成后才标记快照READY并返回总数/hash。名单存review_item，不塞进task_execution.TEXT。失败的快照不可confirm，禁止对部分名单假称所有页。
- Applies to：prepare worker、list/filter service、confirm API。
- Violation consequence：漏审10000条之后或只审到了浏览器当前页。
- 来源：ExpertSearchService.scrollExpertsFiltered:720已有5m scroll/finally clear；用户要求。

### Invariant I-2：目标不可扩张
- Rule：只收当前筛选内NEEDS_REVIEW；已通过、HOLD/REJECTED不自动覆盖。要改变HOLD/REJECTED必须明确选中后审核。confirm消费已存batchKey+hash，不再执行筛选查询扩大范围；新发现记录不加入。
- Applies to：名单构建、确认、执行、重试。
- Violation consequence：越过用户确认范围。
- 来源：用户要求。

### Invariant I-3：持久状态决定进度
- Rule：total=APPLIED+STALE+FAILED+CANCELLED+未处理；失败原因逐人保存。STAGED/READY只表示名单阶段，不表示审核完成。进程中断后原已完成项不重复；失败/未处理项只有明确重试才继续。TaskProgress内存丢失不丢名单与决策。
- Applies to：DB状态CAS、任务SummaryProvider、页面轮询。
- Violation consequence：202被误当全部通过、重启后从头重复授权。
- 来源：E5 TaskExecution/TaskProgress。

### Invariant I-4：确认过期/并发可见
- Rule：准备快照24小时后未确认则过期，明确提示重新准备；确认前校验操作者权限。每项按expected_revision和snapshot身份CAS，冲突STALE；既有确认重复调用返回同一任务/结果，不新建执行。取消只影响未应用项。
- Applies to：confirm、resume/retry、单页共享服务。
- Violation consequence：旧标签/旧身份被批量误审。
- 来源：原始设计选择，非现有代码事实。

## 现状审计

现有分页from+size受ES窗口约束；scroll方法已支持分页耗尽与finally清理。现有enrich controller:328–377提供enrichmentExecutor、tryStartWithToken、TaskExecution记录、finally清理范例。TaskTypeCatalog是中文名/进度白名单来源；添加新类型不能只硬写前端字符串。

02新表写路径：prepare插入明细，confirm应用，revoke新建事件；读路径：review列表、历史、04准入读取。本计划增加全页prepare worker及retry调用同一应用方法；不另写绕过版本检查的bulk批准SQL。交互X5、X9。

## 实现方案

1. **I-1/I-2**：扩展02 prepare接受ALL_MATCHING。复用现有scroll模式；为审核返回完整_source与真实_id，需要专用query adapter或现有方法的明确投影扩展，不能使用缺discoveredAt的旧DTO冒充完整事实。采用本计划新增 `DiscoveryReviewScanService.kt` 统一列表与全页条件，固定同一份tag/level/q/issue/decision语义。每批一次读取admission记录，禁止每人一次数据库查询。
2. **I-1/I-4**：快照构建独立task type DISCOVERY_REVIEW_PREPARE；TaskExecution request只存batchKey/action/筛选/actor，result存hash/counts/阶段。构建中轮询“正在固定名单”；成功才能确认，失败为PREPARE_FAILED不可用。相同requestKey+payload幂等，不同payload冲突409。recordService只接受prepare完整终态与全部明细一致的hash。
3. **I-3/I-4**：确认创建DISCOVERY_REVIEW_APPLY任务，使用现有executor和单任务token，逐500项领取、逐项事务写入。为跨进程并发，用02 admission revision CAS与每项状态CAS；只有持有本任务执行标识者可完成。不要把本地内存互斥当持久幂等。
4. **I-3**：重启将活跃执行按现有任务恢复规则显示INTERRUPTED；不自动继续审核。`POST /batches/{key}/retry`在用户明确点击后仅重新领取FAILED/未处理项；STALE需重新prepare并确认。取消清理scroll，保留已应用项；任务成功/失败计数来自持久明细。
5. **I-1–I-4**：后台列表分页采用持久item.id游标而非反复offset跳过；记录APPLIED之前事务已经提交。确认仅代表保存审核，候选投影由04逐项维护，投影失败须独立显示且不重签批准。

## 变更文件清单

共9文件，审核+现有任务框架2子系统，既有表新增字段0。（A1 修正：新增第 9 个文件为既有目录点数断言测试的最小重同步；见下表末行。）

| 文件 | 操作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewScanService.kt | 新增批量读取/服务端筛选 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt | 扩展全页与恢复 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryReviewRepository.kt | 批次CAS/进度聚合 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewController.kt | 全页/重试/取消 |
| src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalog.kt | 2种任务中文名/计数 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt | 新增10005人、并发与恢复 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryReviewRepositoryIT.kt | 持久幂等 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt | 快照状态/API |
| src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt | 目录断言重同步（A1：2 个新 taskType 的计数/集合断言 18→20 与 hasProgressUi 集；仅同步断言、不弱化） |

## 验收标准

- I-1：10005命中完整保存；第二批ES失败则confirm拒绝；finally清scroll；未在request_payload存全部ID。
- I-2：快照READY后新增1人、修改筛选、刷新浏览器均不扩张batch；HOLD/REJECTED/已批准不被默认全页审批覆盖。
- I-3：注入10002成功/3失败，重启后状态一致；重试仅3项；计数和恒等式成立；没有仅凭202设置成功总数。
- I-4：同batch并发confirm只有一执行生效；24小时过期被拒；并发人工修改产生STALE不是成功；取消已完成结果不倒退。
- 回归02三个人工用例；隔离MySQL/ES使用确定数据，SMTP mock零调用。

## 人工验收清单

### A-1：超过一万条
- 前置条件：隔离环境导入10005待审核+2已通过样本，同标签。
- 操作步骤：1. prepare ALL_MATCHING。2. 等待READY并查看10005。3. 新增1条。4. confirm。5. 查看末页和任务记录。
- 预期结果：审核10005条包含第10005条；新增1条保持待审核；2条已通过不重新签发。
- 覆盖：I-1/I-2、X5。

### A-2：失败与恢复
- 前置条件：同批次执行人为让3条DB应用失败，其余成功。
- 操作步骤：1. 查看10002成功+3失败。2. 重启应用。3. 点击重试失败项。4. 重复点击confirm。
- 预期结果：重启后计数不清零；补成10005成功；重复确认不新增审核记录、不发邮件。
- 覆盖：I-3/I-4、X9、必须保持项。

### A-3：准备失败与取消
- 前置条件：ES第二批抛错的隔离环境。
- 操作步骤：1. 准备全页。2. 尝试确认失败批次。3. 修复后重新准备，执行期间取消。
- 预期结果：失败快照不可确认；新快照取消后已应用数保持，剩余标取消，绝不显示“所有页审核完成”。
- 覆盖：I-1/I-3/I-4。
