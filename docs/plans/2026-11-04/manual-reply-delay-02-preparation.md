# 02 冻结准备与实际投递分离

前置01通过。此阶段保留同步入口语义，给后续worker提供可复用投递seam。

## 需求描述

同一套既有校验产出可持久化准备结果，同一套发送逻辑可从准备结果执行。同步入口的成功、失败、安全确认、Message-ID、审核与草稿结果保持原行为。范围外为入口切换、任务调度、UI。

## 关键不变量

### Invariant I-3 — 指纹与已有请求兼容
- Rule — computeFingerprint原有字段/顺序/域段不改变。同步准备与发送后邮件相等，DEDUP_SENT和completedBinding/findCompletedByRequestId提前返回继续有效。
- Applies to — 两个send入口、prepare、deliver、attempt service。
- Violation consequence — 旧请求被重复发出。
- 来源 — K-manual-send-fingerprint-complete-identity。

### Invariant I-4 — 最终正文单一来源
- Rule — prepare保留当前校验顺序，产出final subject/text/html及已验证证据、同一ICS/event、有序原件快照。deliver不重渲染变量、不重新选择事实、不生成第二份ICS。source运行时对象不进持久JSON。
- Applies to — prepareManualRichSend、deliverPrepared、snapshot factory。
- Violation consequence — 校验过的内容与真实发送不同。
- 来源 — K-manual-rich-final-body-single-seam。

### Invariant I-5 — 成功与存证一起提交
- Rule — 对RAG把saveAll从Pending的finalize之后移动到ManualReplySendAttemptService.finalizeSuccess事务，数据来源仍是已验证evidence。QA与RAG互斥。审计和unsupported归档仍在发送事实已提交后执行；其异常不得改写已提交SENT或再发SMTP。
- Applies to — finalizeSuccess、catch分类、归档与重复成功分支。
- Violation consequence — 发送成功但RAG失败被当未知/重复发送。
- 来源 — K-manual-send-success-transaction-and-dedup。

## 现状审计

- `PendingMailOperationService.kt:605–836` 当前准备含变量、规范化、会议、安全确认、附件与抑制；`:838–1069` 混合claim/投递/落库/返回。`:1894–1935` source与evidence类型为private。
- `ManualReplySendAttemptService.kt:447–542` 成功REQUIRES_NEW写记录/QA/会议/attempt/草稿/审核，`:545–625`失败REQUIRES_NEW写记录与attempt。RAG在Pending:922、位于该成功事务外。事实表schema/所有读写见总计划与证据07。
- `findCompletedByRequestId:231`、`findCompletedByPayload:254`、`completedBinding`存在多个提前返回，抽取不能删除。
- 实际SMTP `SmtpMailDeliveryService.send` 携带isReply=true绕过首次外联像素；线程头在Pending:850–857分来信/会议/会话。保持这些分支。
- interaction X-1准备→指纹/SMTP；X-4成功→RAG/QA/排期/草稿/审核；X-5附件快照→MIME。完整原文source-excerpts。

## 实现方案

### T1 抽取准备（I-3/I-4）

仅修改PendingMailOperationService与01的Models。新增internal `PreparedManualReply`（运行时含解析后的账号/原件）和显式snapshot factory。既有两个public入口继续原签名/默认值，内部调用prepare→原同步claim→deliver。prepare返回完成命中或准备结果的明确类型，不能用null兼作多种状态。

冻结字段逐一对应01T2。legacy恢复内容在解析前捕获原请求参数；DRAFT捕获服务端ValidatedDraftSendRef；REVIEW捕获已验证冻结行引用。快照不得从前端重新组装。

### T2 抽取投递（I-3/I-4/I-5）

Pending新增internal `deliverPrepared(prepared, claimedAttempt, delayedRef = null)`，复用当前ComposedMail构造、classifyDelivery、发送后审计/归档。该方法只接受已经持久claim的ClaimedAttempt；后续worker不能再次调用public send使其循环入队。

delayedRef是server-only任务ID/revision/token，为后续完成事务传递授权，不能出现在HTTP绑定模型。此阶段无队列行时沿旧完成逻辑，编译不依赖03类。

### T3 收敛RAG事务（I-5）

在SendPayload的internal server-only部分新增ragEvidence值（factCodes/corpusFingerprint），不进入指纹流。ManualReplySendAttemptService增加可测试注入的RAG repository，在SENT记录取得id后、事务提交前写入有序事实；empty表示无RAG，不伪造QA。

把Pending当前`:915–932`的saveAll移除。finalize成功返回后，后续审计/ES归档只调整archive响应，不进入UNKNOWN兜底。实际失败finalize仍保留当前安全重试/未知/永久分类。不得顺便改变账户额度或收信pending状态。

### T4 回归（I-3/I-4/I-5）

现有PendingMailOperationServiceTest覆盖同步发送/safety/attachment/RAG结果，TrustWorkbenchTest覆盖assembly/source/归档。ManualReplySendAttemptServiceTest验证原hash、QA/RAG、finalize事务调用。MailReplyDraftSendIntegrationTest真实事务验证失败rollback与成功closeSent。RagSendBridgeTest的3个RAG成功用例原断言Pending内saveAll，T3迁移后改为断言SendPayload.ragEvidence有序证据与最终事务落库（A1修订）。新增snapshot codec矩阵放Models对应的新test。

## 变更文件清单

|#|文件|操作|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt|抽取|
|2|src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt|成功事务RAG存证|
|3|src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt|准备映射|
|4|src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt|回归|
|5|src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt|回归|
|6|src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt|回归|
|7|src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplySnapshotTest.kt|新增快照矩阵|
|8|src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt|修改（A1）：RAG成功用例改断言ragEvidence与最终事务|

MailReplyDraftSendIntegrationTest只运行不修改，如发现真实相关失配，先在03列出的该文件范围内修复并通过后才能继续。

## 验收标准

- I-3 — 运行既有attempt指纹矩阵和同步入口测试，old payload hash/claim结果保持；不同事实顺序仍不同。
- I-4 — 快照round-trip用正文含Unicode/链接/变量、无附件/ICS/10附件、RAG/QA/assembly/DRAFT/REVIEW/LEGACY分别验证。不存凭据，非法版本/超限拒绝。
- I-5 — RAG保存抛异常导致最终事务回滚；unsupported归档抛异常时已提交SENT保持，SMTP恰一次。FAILED/UNKNOWN的现分类用例保持。
- JDK11执行上述6个test类及现有MailReplyDraftSendIntegrationTest（加mysqlIt开关如该类要求），记录实际运行/跳过结果。

## 人工验收清单

### A-1 同步旧链回归
- 前置条件 — 01数据库，enabled=false，测试SMTP，普通稿和审核稿各一份。
- 操作步骤 — 1. 分别发送。2. 查看时间线/草稿/审核详情。3. 重复原请求。
- 预期结果 — 各一封SMTP，SENT记录各一条；普通稿关闭、审核结果SENT；重复请求不新增邮件。
- 覆盖 — I-3/I-4、保持同步行为、X-1/X-4。

### A-2 事实与附件
- 前置条件 — 测试SMTP，准备含RAG的回复与会议ICS/通用附件回复。
- 操作步骤 — 1. 分别发送。2. 下载附件比对摘要。3. 查看事实审计和排期。
- 预期结果 — RAG顺序与提交相同且QA行0；附件摘要相同，会议排期1条，失败不提前创建排期。
- 覆盖 — I-4/I-5、X-4/X-5。
