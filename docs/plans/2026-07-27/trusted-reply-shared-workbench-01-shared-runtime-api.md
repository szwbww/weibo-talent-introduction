# 可信回复工作台 01：共享运行时与 API

日期：2026-07-27  
状态：待批准、待执行  
前置：无  
后续：[02 逐项 AI、版本锁定与无改写整合](./trusted-reply-shared-workbench-02-item-lock-assembly.md)

## 需求描述

新增一套与页面无关的可信回复工作台后端：用显式来源引用解析训练邮件或真实待处理来信，并通过同一服务返回 bootstrap、完整生成、SSE 进度和取消结果。此计划完成后只新增兼容 API，不切换现有前端。

必须不改变：

1. 既有 `/api/ai-training/simulate` 行为、响应和精确 `mailRecordId` 规则。
2. 既有 `/api/mail/unmatched-inbound/{id}/ai-reply/turn(-stream)`、取消接口和实时工作台。
3. `GroundedAutoReplyDecisionService` 的自动回复生成入口。
4. `/manual-rich-reply` 的最终渲染、校验、SMTP、落库和审计顺序。
5. 模拟生成不写发送记录，不写 live AI draft audit。

本计划不包含：逐项 AI 调整、逐项版本/锁定、最终整合、训练评估、前端改造、旧接口删除。

## 关键不变量

### Invariant I-1: 来源引用具有唯一且不可降级的语义
- Rule: 仅接受 `sourceType=TRAINING_MAIL|LIVE_INBOUND`。`TRAINING_MAIL.sourceId` 必须精确读取该 `MailRecord.id`，且记录必须为 `direction='INBOUND'` 并具有有效 `expertContactId`；禁止用 `expertContactId` 查询“最新一封”。`LIVE_INBOUND.sourceId` 必须精确读取该 `InboundMailProcessing.id` 且必须已绑定联系人。
- Applies to: `TrustReplyWorkbenchService.resolveSource`、bootstrap、同步/流式完整生成、取消 scope 校验。
- Violation consequence: 模拟对象漂移，评估或草稿对应到另一封邮件。
- 来源: K-ai-simulate-exact-mail-id

### Invariant I-2: 两类来源先归一化再进入生成
- Rule: 两类来源必须归一化为同一不可变上下文：`sourceRef/contact/inboundText/subject/messageId/senderAccountCode/mailHistory/profileText/researchProfileSufficient/contextWarnings/sourceVersion`；`inboundText` 均优先 `cleanedBody`、否则 `body`。`sourceVersion` 必须由稳定、有序的来源类型、来源 ID、联系人 ID、messageId、subject、sender account、最终 inboundText SHA-256、有序 mailHistory 摘要 SHA-256、profileText SHA-256、researchProfileSufficient 推导，禁止加入当前时间；所有进入 prompt 的可变上下文都必须被版本绑定。
- Applies to: `TrustReplyWorkbenchService.resolveSource`、`bootstrap`、`generate`。
- Violation consequence: 两入口得到不同问题矩阵，或陈旧响应无法识别。
- 来源: original

### Invariant I-3: bootstrap 使用同一 canonical request→fact 矩阵
- Rule: 两类来源都必须通过同一个 `QaFactSelectionService.select(inboundText, requestedFactIds, researchProfileSufficient)` 得到有序 `requestFacts`、`sendQaRuleIds` 和 readiness；未传 `requestedFactIds` 表示服务端自动选择，传入时必须由服务端重新匹配和 canonicalize，禁止直接信任浏览器 ID。bootstrap 同时从 `AiReplyModel` 返回稳定 `availableModels` 与 `defaultModel`，两入口不得各自维护不同模型集合。
- Applies to: `TrustReplyWorkbenchService.bootstrap`、后续完整生成。
- Violation consequence: UI 选中的事实与 prompt/发送审计事实不一致，或跨 request 引用不相关依据。
- 来源: K-request-facts-not-flat-pool, K-explicit-fact-selection-must-match-request, K-ai-reply-prompt-vs-send-rule-ids

### Invariant I-4: 完整生成只有一个共享业务入口
- Rule: 公共控制器不得复制上下文构建、知识注入、preview 或响应映射；必须调用 `TrustReplyWorkbenchService.generate`，该服务再调用现有 `AiReplyDraftService.generate()`。训练与真实来源的 `turns/qaRuleIds/operatorInstruction/model/双 TTL/cancellation/progress` 语义完全一致。
- Applies to: `TrustReplyWorkbenchController.generateStream`、`TrustReplyWorkbenchService.generate`。
- Violation consequence: 两个页面出现 prompt、模型、超时、校验或 response 字段漂移。
- 来源: K-ai-generate-single-freeform-seam

### Invariant I-5: SSE 生命周期与取消是全局共享状态机
- Rule: 一个 Spring singleton `AiReplyGenerationCoordinator` 统一管理两来源 generation；canonical UUID 唯一、全局最多 40 个 active generation、队列拒绝返回 429、同 ID 返回 409；事件固定为 `ready/progress/heartbeat/result/cancelled/error`。只有 `REGISTERED|RUNNING` 可取消，进入 `COMMITTING` 后返回 `TOO_LATE`；断连必须取消 token、worker、heartbeat 并清理 map。
- Applies to: `AiReplyGenerationCoordinator.start/cancel`、公共流式生成与取消接口。
- Violation consequence: 任务泄漏、跨来源误取消、取消后仍提交或进度倒退。
- 来源: K-llm-attempt-total-budget-cancel, K-ai-stream-progress-no-fake-percent

### Invariant I-6: 进度只表达真实观测
- Rule: `progressSeq` 必须单调递增；进度使用既有 `QUEUED/PREPARING/CALLING/VALIDATING/REPAIRING/FINALIZING`、provider activity、attempt/total TTL 和最近活动字段，不新增伪百分比。相同 phase 最多每秒推送一次，phase 变化立即推送。
- Applies to: `AiReplyGenerationCoordinator` 的 ready、progress、heartbeat 映射。
- Violation consequence: UI 显示虚假完成率或采用陈旧 generation 事件。
- 来源: K-ai-stream-progress-no-fake-percent

### Invariant I-7: raw/rendered 与模式副作用严格分离
- Rule: 生成响应同时返回 `draftText` 原模板和通过最终 contact/sender 渲染的 `renderedDraftText`；preview warning 与生成 warning 按出现顺序去重。`TRAINING_MAIL` 不写 `operator_action_log`；`LIVE_INBOUND` 仅首轮生成沿用既有 bounded draft audit，continuation 只构造 snapshot、不重复写初稿事件。
- Applies to: `TrustReplyWorkbenchService.generate`、preview、live audit 分支。
- Violation consequence: 变量在采用时丢失、模拟污染生产审计或同一会话重复计数。
- 来源: K-ai-preview-raw-adoption-boundary, K-ai-draft-audit-version-hash-not-replay, K-review-event-audit-payload-bounds

### Invariant I-8: 公共 API 没有发送能力
- Rule: `/api/trust-reply/workbench/**` 只能读取来源、选择事实、生成与取消；不得调用 `PendingMailOperationService.sendManualRichReply`、`MailDeliveryService`、`MailRecordRepository.save` 或任何 outbound 写路径。
- Applies to: `TrustReplyWorkbenchService`、`TrustReplyWorkbenchController`。
- Violation consequence: 模拟页面可能外发，或绕过人工最终校验。
- 来源: K-ai-generation-observability-not-send-gate, K-manual-rich-render-before-send

### Invariant I-9: 兼容接口在本计划中保持原样
- Rule: 现有训练和真实 AI controller 方法、URL、DTO 与前端调用均不删除、不改签名；公共 API 是纯新增。旧入口清理必须等 04 切换完成后另立计划。
- Applies to: 本计划全部生产文件。
- Violation consequence: 01 单独部署即破坏现网前端。
- 来源: original

## 现状审计

### `mail_record`（只读来源）
- Schema/mapping: `V1__create_business_tables.sql:97-115` 定义 `id/expert_contact_id/direction/message_id/subject/body` 及联系人、QA 外键；后续迁移增加 sender/source/cleaned/error/attempt 字段；`MailRecord.kt:7-28` 当前 mapping 中 `expertContactId` 非空，`cleanedBody` 可空。
- Write paths（`rg mailRecordRepository.save` 全集）:
  1. `AutoMailReplyService:264,577,767,965` — 入站采集及自动/会议回复写记录。
  2. `ManualOutreachTxHelper:50,102`、`InitialOutreachService` 间接 helper — 首发记录。
  3. `MeetingScheduleService:133` — 会议邮件记录。
  4. `ManualExpertMailService:60` — 人工专家邮件。
  5. `ManualReplySendAttemptService:248,327` — 人工回复成功落 outbound 记录。
  本计划不新增或调用上述写路径。
- Read paths:
  1. `AiTrainingController.simulate:180-221` — 当前按精确 mail ID（兼容 fallback 可按联系人最新）读取来源、联系人和历史。
  2. `MailboxService`、`InboundMailSummaryController`、`UnmatchedInboundMailService` — 邮箱详情、线程、关联解析。
  3. `AiQaExtractionService`、`AiReplyContextService` 调用方 — 训练知识和历史上下文。
  4. monitoring/document/manual/auto services — 查询与统计；本计划不改变字段或查询。
- Interaction points: 新 `TRAINING_MAIL` resolver 读取同一实体，但必须删除“联系人最新邮件”语义；既有所有 writer 继续决定 sourceVersion 是否变化。

### `inbound_mail_processing`（只读来源）
- Schema/mapping: `V5__create_inbound_mail_processing.sql:1-20` 定义账号+UID 唯一键、状态索引和联系人外键；V10/V14/V15 增加 body/cleaned/reason/resolution 字段；`InboundMailProcessing.kt:7-29` 为当前 mapping。
- Write paths:
  1. `AutoMailReplyService:1019,1068` — 创建或更新人工处理记录。
  2. `UnmatchedInboundMailService:179,219` — 绑定、解决待处理记录。
  3. `PendingMailOperationService:695` — 人工发送后标记处理完成。
- Read paths:
  1. `UnmatchedInboundMailController.getUnmatchedDetail/executeAiReplyTurn` — 当前 live 详情与生成。
  2. `PendingMailOperationService` — suggest/evaluate/preflight/send。
  3. `MailboxService`、`InboundMailSummaryController`、monitoring/tag/preview services — 展示与统计。
- Interaction points: live source 在用户停留期间可能被上述 writer 更新；每次公共请求必须重新 resolve 并复算 sourceVersion，不能缓存实体。

### `expert_contact`（只读来源）
- Schema/mapping: `V1:79-95` 定义 campaign/orcid/email 唯一关系；V11/V12/V14/V19/V48/V51 增加自动回复、层级、运营状态、国家和跟进字段；`ExpertContact.kt:7-30` 为当前 mapping。
- Write paths: `InitialOutreachService`、`ManualInitialOutreachService`、`ExpertContactManagementService`、`ExpertIndexLevelOperationService`、`ExpertOperatorStatusService`、`ConversationStateService`、`AutoMailReplyService`、`AutomaticApplicationPromotionService`、`UnmatchedInboundMailService`、`PendingMailOperationService`、`ExpertIndexController`。
- Read paths: 所有邮件上下文、邮箱/监控、专家详情、QA preview 和本计划 resolver 都通过 `ExpertContactRepository` 读取。
- Interaction points: resolver 每次以来源记录上的 contact ID 读取，不接受浏览器单独传 contact ID；profile/history 必须来自该联系人。

### `qa_rule` 与 `reply_snippet`（只读生成依赖）
- Schema/mapping: `QaRule.kt:39-69`；`V79` 使 `answer_body` 非空，`V80` 增加 `reply_policy`；`ReplySnippet.kt:7-18` 与 `V47/V64` 定义 frame/snippet。
- Write paths:
  1. `QaRuleManagementService.createRule/updateRule/setRuleEnabled/deleteRule:66-124` 与 Flyway seed/update migrations 写 QA。
  2. `ReplySnippetService.create/update/setEnabled/setDefault/delete:51-167` 写 frame；content variants 同步由该 service 调用。
- Read paths:
  1. `QaFactSelectionService:20-72` 读取 enabled、matchable、非空 `answerBody` 并建立 request matrix。
  2. `AiReplyDraftService`、`AiReplyHighRiskClaimValidator`、`AiReplyPointByPointComposer` 读取 `answerBody` 与 evidence version。
  3. `ReplySnippetService.resolveManualFrame:27-38` 和 composer/draft prompt 读取 frame。
- Interaction points: QA 或 frame 运行时更新必须在下一次 bootstrap/generate 中被观察；公共服务不得缓存规则正文。(来源: K-answerbody-source-exclusive, K-ai-reply-evidence-version-deterministic)

### `operator_action_log`（仅 live 初稿沿用既有写入）
- Schema/mapping: `V19__add_operator_status_and_action_log.sql:32-52`；`OperatorActionLog.kt:7-21`。`after_value/note` 为 TEXT，action_type 最大 64。
- Write paths: 唯一 repository save 位于 `OperatorActionLogService.record:19-44`；业务服务均通过该方法写。公共 service 仅 live 首轮调用既有 `AiReplyReviewAuditService.recordInitialDraft`。
- Read paths: `OperatorActionLogService.search` → `/api/operator-action-logs`；live 详情按 inbound ID 读取；`QaRuleAuditService` 按精确 action type 聚合质量指标；repository 另有 latest AI draft 查询。
- Interaction points: training 分支不得写；live 分支的 action type、bounded snapshot 和指标计数必须与旧 live 入口一致。

### 当前重复入口
- `AiTrainingController.simulate:180-260` 自行解析 MailRecord、联系人、历史、知识、context、generate、preview、response mapping。
- `UnmatchedInboundMailController.executeAiReplyTurn:324-455` 重复上述链路并增加 live audit。
- `UnmatchedInboundMailController:462-778` 私有实现 generation map、SSE、heartbeat、取消与 cleanup，只能服务 live inbound ID。
- `AiReplyDraftService.generate()` 是三个生产入口的共享生成 seam；本计划保持该 seam，不新增第二套 prompt/claim gate。(来源: K-ai-generate-single-freeform-seam)

### API 目标合同

`POST /api/trust-reply/workbench/bootstrap`

```json
{
  "source": {"sourceType": "TRAINING_MAIL", "sourceId": 123},
  "requestedFactIds": null
}
```

响应至少包含：`source/sourceVersion/inboundSubject/inboundText/expertName/expertEmail/llmEnabled/availableModels/defaultModel/suggestedFactIds/canonicalFactIds/rulesByCategory/requestCoverage/draftReadiness/evidenceSetVersion`。`availableModels` 由服务端 `AiReplyModel` 产生，当前为 `DEEPSEEK_V4_FLASH|DEEPSEEK_V4_PRO`；`requestCoverage` 保持原邮件顺序和 intent 子结构。

`POST /api/trust-reply/workbench/generations/stream`（SSE）请求包含：`source/turns/qaRuleIds/operatorInstruction/operatorName/model/generationId/llmAttemptTimeoutSeconds/llmTotalTimeoutSeconds`。结果字段与当前 live response 对齐并额外回显 `source/sourceVersion`。

`POST /api/trust-reply/workbench/generations/{generationId}/cancel` 请求 body 只含 source ref；只可取消相同 scope 的 generation。

## 实现方案

### T1：先锁定双来源解析与 bootstrap 合同
- Governs: I-1、I-2、I-3、I-8、I-9。
- Files: `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt`。
- 新增失败测试：精确训练 mail、拒绝 OUTBOUND、拒绝无 contact、live 精确 inbound、cleanedBody 优先、稳定 sourceVersion、body/messageId/subject/sender/history/profile/research flag 任一变化时 version 必变、两来源同 normalized context 得到同 request matrix、两来源模型集合/default 完全一致、显式事实需 canonicalize、公共 API 无 send 方法。
- Controller contract 测试固定上述三条 URL、请求字段、响应回显和错误码：来源不存在/非法为 4xx，stale 或跨 scope 不得返回成功。

### T2：实现无缓存的共享来源与生成服务
- Governs: I-1、I-2、I-3、I-4、I-7、I-8。
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`。
- 在单文件内定义 `TrustReplySourceType/TrustReplySourceRef/ResolvedTrustReplySource` 和公共 request/response domain；每次调用重新读取 source、contact、history、QA knowledge/context。
- `bootstrap()` 直接使用 `QaFactSelectionService`，仅返回 enabled + matchable + 非空 `answerBody` 的事实元数据，不向前端返回 `answerBody`。
- `generate()` 调用唯一 `AiReplyDraftService.generate()`；preview 使用 `AiReplyDraftPreviewService`。`LIVE_INBOUND` 首轮调用现有 bounded audit，`TRAINING_MAIL` 不调用；该服务不注入任何发送 service。
- 新读路径由现有 MailRecord/Inbound/Contact writers 提供，不需调整写路径；sourceVersion 和 evidenceSetVersion 分别保护来源与事实变更。

### T3：提取通用 SSE/取消协调器
- Governs: I-5、I-6、I-8。
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinator.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinatorTest.kt`。
- 将现有 controller 私有 `GenerationControl` 行为抽象成 `start(scopeKey, generationId, timeoutPolicy, operation)` 与 `cancel(scopeKey, generationId)`；使用现有 `aiReplyStreamExecutor/aiReplyStreamScheduler` beans。
- 测试覆盖：canonical UUID、40 active 上限、重复 ID、ready 首事件、progressSeq/phase 节流、heartbeat、result/error/cancelled 单终态、断连 cleanup、RUNNING cancel、COMMITTING TOO_LATE、错误文案不泄露 exception message。

### T4：新增公共 controller，保持旧 controller 不动
- Governs: I-4、I-5、I-6、I-8、I-9。
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt`。
- controller 仅负责 DTO 校验、调用 service/coordinator 和 HTTP/SSE 映射；不直接访问 repository，不自行构建 prompt/context/preview。
- generation scopeKey 必须为服务端规范化的 `"<sourceType>:<sourceId>"`；取消 body 与 generation scope 不一致返回 `NOT_ACTIVE`。
- 不注册任何 send/adopt/evaluation endpoint。

### T5：兼容与全量回归
- Governs: I-7、I-8、I-9。
- Files: 上述 6 个文件；不得为修复回归修改其他生产文件。
- 运行现有 `AiTrainingSimulateTest`、`UnmatchedInboundAiReplyTurnKnowledgeTest`、`GroundedAutoReplyDecisionServiceTest`、`PendingMailOperationServiceTest`，证明旧入口、自动 decision 和发送服务未变。

## 变更文件清单

| # | 文件 | 动作 | 目的 |
|---:|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 新增 | 双来源解析、bootstrap、完整生成与 preview |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinator.kt` | 新增 | 通用 SSE/取消状态机 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | 新增 | 公共工作台 API |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | 新增 | 来源、矩阵、副作用测试 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinatorTest.kt` | 新增 | SSE/取消并发状态测试 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt` | 新增 | HTTP/SSE 合同测试 |

文件数：6。子系统数：2（共享来源/生成业务；SSE 运行时）。无 schema 变更、无新共享存储字段。

## 验收标准

- I-1: service 测试证明 `TRAINING_MAIL(123)` 只读 123；传 contact ID、OUTBOUND、无 contact 或不存在 source 均失败；live 只读精确 inbound ID。
- I-2: 固定 normalized context 重复 bootstrap 的 sourceVersion 相同；仅修改正文、messageId、subject、sender、contact、历史摘要、profile 或 research flag 任一输入后版本变化；不含时间值。
- I-3: 两类来源的相同 normalized context 得到相同 request 顺序、status、intent、canonical fact IDs、availableModels/defaultModel；非法显式 fact ID 被拒绝/剔除且不能进入 response。
- I-4: controller test 证明所有生成只调用 `TrustReplyWorkbenchService.generate`；源码 grep 公共 controller 不含 repository、`buildKnowledgeContext` 或 `aiReplyDraftService.generate` 直接调用。
- I-5: coordinator 并发测试覆盖 40 上限、重复 UUID、scope 隔离、取消/commit 竞争、单终态和 map cleanup。
- I-6: 事件断言无 `percent/completionPercent`；progressSeq 单调，phase 变化立即发送，相同 phase 不超过每秒一次。
- I-7: training mock 验证 audit 0 次；live 首轮 1 次、continuation 0 次；raw 与 rendered 同时返回，warning 顺序去重。
- I-8: `rg -n "sendManualRichReply|MailDeliveryService|mailRecordRepository.save"` 对三个新增生产文件无命中。
- I-9: 现有 URL/DTO 源码未改；旧定向测试全绿。
- 定向：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=TrustReplyWorkbenchServiceTest,AiReplyGenerationCoordinatorTest,TrustReplyWorkbenchControllerTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,GroundedAutoReplyDecisionServiceTest,PendingMailOperationServiceTest test
```

- 全量：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`。

## 人工验收清单

### A-1: 训练来源严格命中所选历史邮件
- 前置条件: 同一联系人至少有两封正文不同的 INBOUND `mail_record`，记录 ID 分别为 M1、M2。
- 操作步骤: 1. 调 `/api/trust-reply/workbench/bootstrap`，source=`TRAINING_MAIL/M1`；2. 再以 M2 调用；3. 比较响应。
- 预期结果: 第一次 `sourceId=M1` 且 `inboundText` 为 M1 正文；第二次为 M2；两个 `sourceVersion` 不同；均未出现“最新邮件替代”行为。
- 覆盖: I-1、I-2。

### A-2: 真实来源严格命中待处理记录
- 前置条件: 存在一条已绑定联系人的 `inbound_mail_processing` L1。
- 操作步骤: 1. 以 `LIVE_INBOUND/L1` 调 bootstrap；2. 使用不存在的 ID 再调一次。
- 预期结果: L1 返回其清洗后正文和联系人；不存在 ID 返回 4xx，不回退其他待处理记录。
- 覆盖: I-1、I-2。

### A-3: 双来源共享矩阵
- 前置条件: 一封 mail_record 与一条 inbound processing 保存相同 cleanedBody、联系人和当前 QA 数据。
- 操作步骤: 分别以两种 sourceType 调 bootstrap。
- 预期结果: `requestCoverage` 数量、顺序、每项 status/intents/factRuleIds 及 `canonicalFactIds` 完全一致；仅 source ref/version 可不同。
- 覆盖: I-3、需求可观察结果。

### A-4: 公共 SSE 与取消
- 前置条件: LLM enabled，准备一个会持续数秒的生成请求。
- 操作步骤: 1. 使用 canonical UUID 调 generate-stream；2. 观察事件；3. 在 `CALLING` 阶段调 cancel。
- 预期结果: 首事件 `ready`；后续进度含 phase、attempt/total TTL、provider activity；取消返回 `CANCEL_REQUESTED`，流只收到一个 `cancelled` 终态，此 generation 随后返回 `NOT_ACTIVE`。
- 覆盖: I-5、I-6。

### A-5: 模拟不产生生产副作用
- 前置条件: 记下 `mail_record` outbound 数量和该联系人相关 operator log 数量。
- 操作步骤: 用 `TRAINING_MAIL` 完成一次公共完整生成，再查询两项数量。
- 预期结果: outbound 数量不变；没有新增 `AI_REPLY_DRAFT_READY/NEEDS_REVIEW/BLOCKED`；响应有 raw/rendered 文本。
- 覆盖: I-7、I-8。

### A-6: live 初稿审计保持旧语义
- 前置条件: 一条 live 待处理来信，记下其 AI draft audit 数量。
- 操作步骤: 1. 用公共接口生成首轮；2. 带一组 turns 生成 continuation；3. 查询日志。
- 预期结果: 首轮新增恰好 1 条 readiness 对应的 AI draft action；continuation 不再新增初稿 action；日志不含回复正文。
- 覆盖: I-7；interaction point `generate → operator_action_log → audit readers`。

### A-7: 旧入口与人工发送回归
- 前置条件: 准备训练邮件、live 待处理邮件、纯人工回复正文。
- 操作步骤: 1. 调旧 `/api/ai-training/simulate`；2. 调旧 live turn-stream；3. 不经过工作台直接提交纯人工 `/manual-rich-reply`。
- 预期结果: 两个旧生成入口保持原响应；纯人工发送仍由既有链路成功处理，不要求 sourceVersion、generationId 或工作台历史。
- 覆盖: I-8、I-9、must-not-change 1～4。
