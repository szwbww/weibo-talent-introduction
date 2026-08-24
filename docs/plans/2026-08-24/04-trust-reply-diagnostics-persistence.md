# 04 最终发送/评估的事实与意图诊断留痕

> 执行顺序：第 4 份；依赖计划 01、02、03。当前只预留结构化记录，不开发筛选 UI、统计接口或新表。

## 需求描述

在工作台最终 assembly 时由服务端生成有界诊断快照，并只在两类最终事件中持久化：线上发送成功、训练邮件完成评估。后续可以识别哪些邮件存在意图不匹配、未识别 ask、UNSUPPORTED 上人工添加事实、同一事实跨摘要重复绑定。临时添加/删除事实、切换 handling、生成版本、锁定、预览、bootstrap 不写审计行。

复用现有 `operator_action_log.after_value` JSON；不新增 action type、不改 DB schema。现有 `/api/operator-action-logs` 可按 inbound/action 读取记录；按诊断 flag 的索引化查询属于后续功能，本计划不声称已支持。

关联知识：[[K-training-evaluation-bounded-action-log]]、[[K-rich-reply-qa-audit-reuse]]、[[K-audit-selected-source]]、[[K-locked-item-assembly-list-not-set]]、[[K-fact-matrix-two-semantics-in-one-field]]。

## 关键不变量

### Invariant I-1：只记录最终事件

- LIVE：SMTP 成功且 `finalizeSuccess` 已提交后，在该次发送既有 action（有事实为 `SEND_MANUAL_COMPOSED_REPLY`，无事实为 `SEND_MANUAL_RICH_REPLY`）的 `after_value` 增加 diagnostics。
- TRAINING：用户提交 rating 且 assembly 校验通过时，在现有 `AI_TRAINING_REPLY_EVALUATED` action snapshot 增加 diagnostics。
- 不新增“事实被点击/移除/移动”等 action rows。

### Invariant I-2：诊断由服务端权威数据计算

仅使用计划 03 的 verified assembly `selection + response.itemVersions + canonical matrix`。不得接收客户端自报 flags、matched ids 或 unrecognized count。

### Invariant I-3：诊断不参与业务授权

diagnostics 只进入 response/audit snapshot；不得进入 status、factRuleIds、allowed handling、version/evidence hash、safety decision、SMTP、归档资格。

### Invariant I-4：有界且不存邮件正文

固定 schema version；最多 50 个 request snapshot，每条最多 20 个 intent key、50 个 fact id；requestKey/handling/status 等字符串最多 200 字符。只存 id、计数、短枚举、flags、truncated 标记；不存 inbound body、request quote、answerText、fact answerBody、operator instruction、电话/地址等原文。

### Invariant I-5：flag 口径稳定

顶层和逐 request 允许以下稳定 flag：

- `MANUAL_FACT_SELECTED`：本条人工最终 fact 非空；
- `INTENT_MISMATCH`：`intentMismatchFactRuleIds` 非空；
- `UNRECOGNIZED_ASK`：本条 `unrecognizedAsks` 非空；
- `MANUAL_FACT_ON_UNSUPPORTED`：自然 status 为 UNSUPPORTED 且人工 fact 非空；
- `DUPLICATE_MANUAL_FACT_ASSIGNMENT`：同一 fact id 出现在多个 request selection。

flag 只描述，不阻断。

### Invariant I-6：沿用现有事务/失败语义

LIVE audit 继续是 `recordSendAudit` 的 after-commit best-effort：审计写失败只记 warn，不反转已发送邮件。TRAINING evaluation 继续以 action-log 行作为评估记录；record 失败则评估未保存，保持现状。

### Invariant I-7：无 assembly 路径兼容

纯人工 rich reply、旧 QA 发送没有 verified assembly 时不写伪造 diagnostics；现有 action after payload 字段逐字保留。

## 现状审计（代码证据）

### `operator_action_log` 全链路

- `V19__add_operator_status_and_action_log.sql:32-52`：`after_value TEXT`，已有 inbound/action/created indexes，无 JSON flag 索引。
- `OperatorActionLogService.record():19-45` 用 ObjectMapper 把 `after` 序列化后写一行。
- `OperatorActionLogService.search():47-68` 和 `OperatorActionLogController:/api/operator-action-logs` 只按 contact/inbound/action/operator/time 查询，response 原样返回 `afterValue`。
- 因此本计划能“留档并按邮件/action 取回”，不能高效按 flag 聚合；后续若需要 flag 筛选应另立 migration/API 计划。

### 训练评估写路径

- `AiTrainingEvaluationService.save():53-84` 先 `assemble`，再 `buildSnapshot`，最后只写一条 `AI_TRAINING_REPLY_EVALUATED`。
- `buildSnapshot():131-169` 已有 `schemaVersion`、hash、handling counts、最多 50 item、字符串 200、model 上限；适合嵌入同样有界的 diagnostics。
- 现 snapshot 不含自然 status、人工 facts、intent mismatch 或 unrecognized ask。

### LIVE 发送写路径

- `ManualReplySendAttemptService.recordSendAudit():340-412` 按 `carriesQa` 在 `SEND_MANUAL_COMPOSED_REPLY` 与 `SEND_MANUAL_RICH_REPLY` 之间选现有 action；前者已有 canonical/suggested ids、edited、status、subject、bodyPreview，后者也已有 mail/status/subject/bodyPreview。
- `:401-410` 已实现 after-commit；`:394-399` 写失败只 warn。
- `PendingMailOperationService:303-329` 在 `finalizeSuccess` 后调用该方法，正是唯一应附加 LIVE diagnostics 的位置。

### 诊断源

计划 02 产出每条 `intentMatchedFactRuleIds/intentMismatchFactRuleIds`、自然 status、unrecognizedAsks；计划 03 的 `VerifiedTrustReplyAssembly` 同时持有服务端 selection 和已验证 item versions，可在一次 assembly 中生成快照，无需重新解析或调用 LLM。

## 实现方案

### 阶段 1：共享有界诊断模型与 builder

在 `TrustReplyWorkbenchService.kt` 增加 response DTO：

- `TrustReplyDiagnostics(schemaVersion, flags, requestSnapshots, requestTotal, requestTruncated)`；
- `TrustReplyRequestDiagnostic(requestKey, status, handling, detectedIntentKeys, unrecognizedAskCount, manualFactRuleIds, intentMatchedFactRuleIds, intentMismatchFactRuleIds, flags, factIdsTruncated, intentKeysTruncated)`。

builder 输入 verified selection + materialized versions：按 request index 关联，截断后生成逐项/顶层 flags。重复事实通过 canonical matrix 的 `factRuleIds` 计数，不能用已经 distinct 的 canonical union 推导。

`TrustReplyAssembleResponse` 增加 `diagnostics`；它不参与 `draftHash/evidenceSetVersion/versionId`。

### 阶段 2：训练评估复用 diagnostics

`AiTrainingEvaluationService.buildSnapshot` 在现有字段后追加 `trustReplyDiagnostics`，直接使用 assembled response 中的服务端快照。保留既有 item snapshot、rating、hash 和所有上限；更新 snapshot schema version为 v2，测试同时证明不含正文/说明。

### 阶段 3：LIVE 成功 action 附加 diagnostics

1. `PendingMailOperationService` 将计划 03 verified response 的 diagnostics 传给 `recordSendAudit`；无 assembly 传 null。
2. `ManualReplySendAttemptService.recordSendAudit` 仅在 `diagnostics != null` 时给当前分支的现有 after map 增加 `trustReplyDiagnostics`；因此“工作台无事实但完成发送”仍可记录 unrecognized/unsupported 诊断。
3. 不新增 action row/action type，不改 before/note/subject/bodyPreview 字段，不把 diagnostics 放入 `SendPayload` 或 attempt 幂等键。

### 阶段 4：持久化与负面测试

- LIVE：模拟发送成功，捕获 `operatorActionLogService.record`，断言 after JSON/Map 含正确 flags/ids/counts；发送失败、safety blocked、assembly null 均无 diagnostics。
- TRAINING：三种 rating 均保存同结构 diagnostics；assembly invalid 时不写 evaluation。
- 边界：51 requests、超长 key、51 facts、21 intents，断言截断标记和最大值；全文/answerBody/instruction 独特 canary 字符串不得出现在序列化结果。

## 变更文件清单

| 文件 | 修改 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 诊断 DTO、builder、assemble response |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt` | evaluation snapshot v2 嵌入 diagnostics |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 成功发送时传递 verified diagnostics |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` | 现有 action after map 增加可空 diagnostics |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationServiceTest.kt` | 训练写入、bounds、隐私回归 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | LIVE 最终事件/非最终事件分流 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt` | action after payload 与 best-effort 语义 |

范围：7 个文件；2 个子系统（LLM evaluation/workbench、mail audit）。无 DB migration、repository/controller、前端/UI 变更。

## 验收标准

- 同一 verified assembly 在训练评估与 LIVE 成功发送中产生语义相同的 `trust-reply-diagnostics-v1`。
- mismatch、unrecognized、unsupported+manual、duplicate assignment 四类 fixture 的 flags 和对应 ids/counts准确；诊断不改变 assembly canonical facts 或 handling。
- 临时事实操作、bootstrap、generate、adjust、lock、preview 不新增 action row。
- LIVE 只复用该次发送原本会产生的 `SEND_MANUAL_COMPOSED_REPLY` 或 `SEND_MANUAL_RICH_REPLY` 行；TRAINING 只复用 `AI_TRAINING_REPLY_EVALUATED` 行。
- 纯人工 rich reply/无 assembly QA 发送 after payload 不含 diagnostics。
- 51/21/51 边界按约定截断；序列化 JSON 不含 inbound/request/answer/fact body/operator instruction canary。
- 现有 `/api/operator-action-logs?inboundProcessingId=...` 能取回 afterValue；可再按实际 action type 缩小范围，不新增按 flag 筛选能力。
- 测试：
  - `mvn -q -Dtest=AiTrainingEvaluationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,ManualReplySendAttemptServiceTest test`
  - `git diff --check`

## 人工验收清单

1. 对一封含 mismatch、unrecognized、UNSUPPORTED+manual fact 的 LIVE 邮件成功发送。
2. 通过 operator action log 按 inbound id/action type 读取记录；确认 afterValue 含 diagnostics/flags，不含邮件或事实正文。
3. 对训练邮件做一次评估；确认 evaluation action 含同结构 diagnostics。
4. 在工作台反复添加/删除/移动事实但不发送、不评估；确认没有新增诊断 action 行。
5. 发送纯人工 rich reply；确认原 action payload 不多出伪诊断。
