# 可信回复工作台无依据回答 V1－05：正式发送成功后入索引

日期：2026-07-29
状态：待批准、未执行
前置：[01 后端逐项语义与版本合同](./trust-reply-unsupported-answer-v1-01-backend-item-semantics.md)、[03 ES 索引与训练只读列表](./trust-reply-unsupported-answer-v1-03-es-index-training-list.md) 已通过；建议 [04 训练评估合格后入索引](./trust-reply-unsupported-answer-v1-04-training-qualified-archive.md) 已通过
后续：无；完成后执行总计划全链路验证

## 需求描述

把真实人工发送成功作为无依据回答进入 ES 的资格门：只有可信回复工作台 assembly 被采用后未经过人工编辑、既有 manual-rich-reply 安全校验通过、SMTP 明确发送成功且 `finalizeSuccess` 已落 outbound `mail_record`，才把 canonical `ANSWER_FROM_OPERATOR_INPUT` 版本写为 `ACTIVE`。

纯人工回复、编辑后的工作台草稿、发送失败/未知状态不得归档。ES 失败不能改变邮件已经发送成功的事实。

必须不改变：

1. manual-rich-reply 的最终 subject/text/html/QA/high-risk 校验与纯人工可发送合同。
2. `mail_send_attempt` 指纹、CAS、CLAIMED/SAFE_RETRY/DEDUP/UNKNOWN/PERMANENT 状态机与 Message-ID 幂等。
3. `finalizeSuccess/finalizeFailure` 的 mail_record、mail_record_qa_rule 和 attempt 写入语义。
4. 现有 send audit 为 best-effort；失败不反转 SENT。
5. 训练宿主永不发送，真实工作台只先采用到编辑器。

明确不纳入：编辑后正文拆分归档、纯人工正文归档、自动补偿/outbox、重发按钮、索引复用、发送校验重构、CSS 改造。

## 关键不变量

### Invariant I-1: 浏览器只在未编辑时携带 archive assembly

- Rule: record/raw/text/HTML baseline 四项均匹配时才同时提交 templateTextBody 与显式 reassemble request；否则二者均不提交。
- Applies to: adoptContext、live completion callback、manual-rich-reply request builder。
- Violation consequence: 编辑后正文与逐项回答不一致仍被错误归档。
- 来源: K-ai-preview-raw-adoption-boundary。

- `adoptTrustReplyAssembly` 保存两份数据：既有 raw/rendered baseline，以及用于服务端重放的 `TrustReplyAssembleRequest` 快照。
- 点击发送时必须同时满足：当前 record 相同、raw template 非空、editor.innerText 与 rendered baseline 相等、editor.innerHTML 与 rendered baseline HTML 精确相等，才发送 `templateTextBody` 和 `trustReplyAssembly`。
- 任一字符/HTML 变化后，两者都不携带；邮件仍按纯人工/编辑后流程发送，但不归档工作台逐项回答。（来源：K-ai-preview-raw-adoption-boundary）
- `trustReplyAssembly` 结构必须显式映射：`source`、`expectedSourceVersion`、`expectedEvidenceSetVersion`、canonical `requestedFactIds`、`lockedItems`；不能直接依赖 response 字段名偶然兼容。

### Invariant I-2: 服务端对 archive assembly authoritative replay

- Rule: 候选 assembly 只在发送成功后重放，并精确验证 live source、raw template、rendered final text；客户端内容无 authority。
- Applies to: optional mail DTO、post-send helper、workbench assemble、document filter。
- Violation consequence: 恶意或陈旧 payload 把未发送内容写入 ES。
- 来源: original；K-trust-reply-resolved-version-single-source。

- `PendingManualRichReplyRequest.trustReplyAssembly` 是可选候选元数据，不是发送许可。
- 只有在发送成功后，服务端才调用 `TrustReplyWorkbenchService.assemble` 重新解析当前 source/evidence/request/versions。
- 必须验证 source 精确为 `LIVE_INBOUND + 当前 inboundProcessingId`。
- authoritative reassemble 的 `rawDraftText` 必须精确等于本次 `templateTextBody`，其 rendered text 必须精确等于本次实际 `finalTextBody`；任一不符不归档。
- 客户端自报 requestText、contact/campaign、answer hash、status 均无 authority。

### Invariant I-3: 发送终态先于 ES，ES 不改变发送事实

- Rule: SMTP 明确成功且 finalizeSuccess 返回 mailRecordId 后才可 archive；发送非 SENT 分支零 ES；archive 异常不 finalizeFailure。
- Applies to: CLAIMED/SAFE_RETRY_CLAIMED success、所有 failure branches、response mapping。
- Violation consequence: ES 成为发送 gate，或已发送邮件被错误标记失败并诱发重发。
- 来源: K-ai-generation-observability-not-send-gate。

固定顺序：

```text
既有输入/模板/事实/高风险校验
-> prepareAndClaim
-> SMTP send 明确成功
-> finalizeSuccess 返回 outbound mail_record.id
-> 调用既有 best-effort send audit
-> best-effort authoritative reassemble + ES archive
-> SENT response + archive status
```

- 在 SMTP 前不得访问 ES，也不得因 assembly 缺失/非法阻止发送。
- 发送失败、safe retry、permanent failure、delivery unknown、finalize failure 均不写 ES。
- ES 失败只记录并返回 archive warning，不调用 `finalizeFailure`、不改变 `sendStatus=SENT`。（来源：K-ai-generation-observability-not-send-gate）

### Invariant I-4: DEDUP_SENT 只补归档，不重复发送或文档

- Rule: DEDUP_SENT 读取既有 outbound record 并走同一 archive helper；不 SMTP，deterministic ID create-only。
- Applies to: DEDUP_SENT branch、mail_record lookup、ES result。
- Violation consequence: 重复邮件、重复索引文档或虚构 qualificationId。
- 来源: original。

- `ManualReplySendAttemptService.ClaimResult.DEDUP_SENT` 已证明同一发送 payload 成功；若能找到既有 outbound mail record，可再次执行同一 archive 校验。
- deterministic ES `_id` 使重复调用成为 ALREADY_EXISTS；不得再次 SMTP、不得创建第二条 ES 文档。
- 找不到既有 outbound record 时，邮件仍返回 SENT，但携带 archive FAILED/NOT_APPLICABLE，不伪造 qualificationId。

### Invariant I-5: LIVE 文档资格字段由服务端固定

- Rule: 只写 ACTIVE/LIVE/LIVE_SEND/operator-directed canonical versions，qualificationId 为 outbound mail_record.id。
- Applies to: live document factory、ES create、training list reader。
- Violation consequence: 非发送内容、ACK/OMIT/有据回答进入生产级集合。
- 来源: original。

- `sourceMode=LIVE`、`status=ACTIVE`、`qualificationType=LIVE_SEND`。
- `qualificationId=outbound mail_record.id`，不是浏览器 draftId 或不稳定页面 token。
- `approvedBy` 使用本次发送规范化 operatorName；contact/campaign 从当前服务端 contact 获取。
- 只过滤 authoritative assembled `ANSWER_FROM_OPERATOR_INPUT + AI_GENERATED + 非空说明/回答`；ACK、OMIT、有据回答不写。

### Invariant I-6: SENT 响应独立表达归档结果

- Rule: sendStatus/messageId 保持权威，archive 四态/count 只表达附属写入；不泄露 ES 细节。
- Applies to: `PendingMailSendResult`、controller JSON、frontend success/warn path。
- Violation consequence: 用户把 ES 失败误判为邮件失败并重复发送。
- 来源: original。

`PendingMailSendResult` 追加带默认值字段：

```text
unsupportedAnswerArchiveStatus = NOT_APPLICABLE | SAVED | PARTIAL | FAILED
unsupportedAnswerArchivedCount
unsupportedAnswerArchiveFailedCount
```

- 没有 assembly、编辑后发送、assembly 无 eligible items为 NOT_APPLICABLE。
- ES CREATED/ALREADY_EXISTS 计成功；部分失败为 PARTIAL；全部失败/重放不匹配为 FAILED。
- 返回值不包含正文、ES 错误 body、凭据或内部堆栈。

### Invariant I-7: V1 失败只记录，不伪装自动补偿

- Rule: 无 outbox/retry/re-send hack；结构化日志和 response warning 是唯一失败可观察性。
- Applies to: exception handling、UI 文案、变更文件范围。
- Violation consequence: 未持久化任务丢失、重复发送或范围失控。
- 来源: original。

- 不新增 DB outbox、后台 retry 或“重新发送以补索引”操作。
- ES 失败写结构化日志，前端提示索引未完整记录；邮件事实保持 SENT。
- 后续若需要自动补偿，必须单独设计持久化 outbox，不能从 mail body 猜回逐项版本。

## 样式契约

### S-1: 复用既有发送结果提示，不新增 DOM/CSS

- 复用: `showStatus(message,"ok"|"warn")`、现有人工回复成功 alert/状态路径、`.button.primary`；工作台/编辑器结构不变。
- 新增: 无 DOM、无 class、无 CSS、无 inline style；只依据 response archive status 选择实值文案与 ok/warn 类型。
- DOM 结构: manual-rich-reply 原有 editor/button/detail 不移动；成功后仍清理 context 并刷新详情。
- 禁止项: 用 error/失败 alert 表示 ES 失败；新增“重发/补写”按钮；新增 modal/class/style；修改 `styles.css`。

- 继续使用现有 `showStatus` 和成功 alert/提示路径；`SENT + PARTIAL/FAILED` 文案必须先写“邮件发送成功”，再写“索引写入失败/部分失败”。
- 不使用红色“发送失败”样式表示 ES 失败；使用 warning 语义，避免用户重复发送。
- 不新增按钮、modal、颜色或 inline style；不修改 `styles.css`。
- 工作台和人工富文本编辑器布局保持不变。

## 现状审计

### `mail_send_attempt` store

- Schema/mapping: V23/V24 建表并扩展 SENT/FAILED_SAFE_TO_RETRY/DELIVERY_IN_PROGRESS/DELIVERY_UNKNOWN 等状态；`mail_record.mail_send_attempt_id` 唯一外键保证一次 attempt 对应最多一封记录；本计划不改 schema。
- Write paths: `ManualReplySendAttemptService.prepareAndClaim` 创建/claim；`finalizeSuccess` 更新 SENT；`finalizeFailure` 更新失败/未知；无其他 service 直接写状态。
- Read paths: 同 service 按 fingerprint/id 查重和 CAS；`PendingMailOperationService` 消费 ClaimResult；测试与运维查询状态。
- Interaction points: archive 只能消费 CLAIMED 成功终态或 DEDUP_SENT，不能插入/update attempt，也不能触发 finalizeFailure。

### `mail_record` 与 `mail_record_qa_rule` stores

- Schema/mapping: `mail_record` 记录 inbound/outbound body/send status/message/attempt；V42 的关联表按 mailRecordId+qaRuleId+ordinal 保存有序事实。此计划不改 schema。
- Write paths: `ManualReplySendAttemptService.finalizeSuccess/finalizeFailure` 写人工 rich reply；`ManualOutreachTxHelper`、`MeetingScheduleService`、`ManualExpertMailService`、`AutoMailReplyService` 写各自 mail 类型；QA 关联由 `ManualReplySendAttemptService`、`ManualExpertMailService`、`AutoMailReplyService` 写。
- Read paths: mailbox/detail/monitoring/document/AI training/history/QA extraction/workbench source 和发送 dedup 均读取 mail_record；QA audit/发送验证读取 association。
- Interaction points: 仅 `finalizeSuccess` 返回的真实 outbound id可成为 qualificationId；archive 不写/改 mail stores；DEDUP 从 `findByMailSendAttemptId` 读取同一 id。

### `trust_reply_unsupported_answer_v1` store

- Schema/mapping: 03 strict mapping，无新增字段。
- Write paths: 03 gateway 唯一低层 create；04 training caller；本计划新增 live post-send caller。
- Read paths: 03 AI Training list Tab；无发送路径 reader、无复用 reader。
- Interaction points: live writer 必须使用 authoritative workbench response，list reader显示 ACTIVE/LIVE；ES 结果只进入 send response metadata，不回写 mail_record。

### 前端采用与发送边界

- `app.js:9031-9049` 的 `adoptTrustReplyAssembly` 当前保存 rawTemplate、rendered text/HTML baseline、record、facts、evidence 与 draftHash，但没有保存可重放 locked items。
- `app.js:9733-9761` 当前仅在 record/text/HTML baseline 完全一致时添加 `templateTextBody`；这是 archive assembly 的正确同行条件。
- `submitManualRichReply` 当前忽略 response body并直接提示发送成功；需要读取新增 archive status，但不能把 warning 抛成发送失败。

### 前端样式盘点与改动前基线

- 可复用 class/token: S-1；primary `#2563eb`、warning `#d97706`、success `#059669`，现有 button 高 32px/字号 12px/圆角 7px。
- 改动前发送请求只在 baseline 精确匹配时执行 `requestBody.templateTextBody = adopt.rawTemplate;`；随后 `submitManualRichReply` API 成功后清 context、`alert("人工回复邮件发送成功")`、刷新详情。
- 现有 manual-rich editor/detail/send button DOM 不改；本计划不新增元素或 class。

### Controller/DTO

- `PendingManualRichReplyRequest` 当前位于 `PendingMailOperationService.kt:917`，只含 sender/subject/body/QA/template 字段。
- `UnmatchedInboundMailController.kt:235-258` 手工把 request 字段传给 service；新增 assembly 必须显式传递。
- 直接复用 service domain `TrustReplyAssembleRequest` 作为可选嵌套 DTO，避免再维护第三套 locked item 映射；JSON 仍由服务端 enum/data class 校验。

### 正式发送终态

- `PendingMailOperationService.sendManualRichReply:115` 是唯一正式人工富文本发送入口。
- 当前 261 行 `finalizeSuccess` 返回 outbound mailRecordId；这是 LIVE_SEND qualificationId 的正确来源。
- 当前 359 行 DEDUP_SENT 从 `mailRecordRepository.findByMailSendAttemptId` 获取既有记录，可作为幂等补写资格。
- IN_PROGRESS/UNKNOWN/PERMANENT_FAILED 和 delivery failure 分支都在返回前抛错，不应触发 archive。
- `PendingMailSendResult:901` 当前无 archive 元数据，可安全在末尾追加默认字段。

### 服务端安全重放

- `TrustReplyWorkbenchService.assemble` 已重新校验 source/evidence/request/version，并返回 canonical itemVersions/raw/rendered。
- 归档必须复用它，不能直接把浏览器 lockedItems 写 ES。
- 现有发送校验仍基于最终 subject/text/html 和当前 QA facts；archive assembly 不参与 `performFinalBlockingCheck`。

## 实现方案

### T0：执行前研究检查点

- Governs：I-1～I-7、S-1。
- Exact files: 本计划清单 1～10。
- 重新 `rg` `sendManualRichReply` 全部 controller/service/test 调用、`PendingManualRichReplyRequest/PendingMailSendResult` 构造、ClaimResult when、finalizeSuccess/failure、mailRecord writer、template baseline、adoptContext 和 asset-version 断言。
- 验证 `recordSendAudit` 当前为 best-effort/catch 行为；不得把它误改为归档资格 gate。
- 若发现另一正式 rich-send 入口、第三个 attempt finalizer、或需更改 schema/第 11 个文件，停止并修订/拆分计划。

### T1：先写前端 payload 失败测试

- Governs：I-1、S-1。
- Exact files: `src/test/js/aiReplyReviewConfirmation.test.js`。
- 在 `aiReplyReviewConfirmation.test.js` 扩展 live adopt/send 静态与可抽取函数场景：
  - 采用 assembly 后 adoptContext 保存精确 reassemble request。
  - text 与 HTML baseline 均未变时，manual-rich-reply body 同时含 templateTextBody + trustReplyAssembly。
  - 改一个字符、只改 HTML、切换 record、采用失效时二者均不含。
  - 训练 host 永不构造 manual-rich-reply payload。
- 保留纯人工发送不需要 workbench 的既有合同。

### T2：前端保存并提交精确 assembly 快照

- Governs：I-1、S-1。
- Exact files: `src/main/resources/static/app.js`、`src/test/js/aiReplyReviewConfirmation.test.js`。
- `adoptTrustReplyAssembly` 从 response 构造不可变/深复制快照：

```javascript
{
  source: assembly.source,
  expectedSourceVersion: assembly.sourceVersion,
  expectedEvidenceSetVersion: assembly.evidenceSetVersion,
  requestedFactIds: [...(assembly.requestedFactIds || [])],
  lockedItems: assembly.itemVersions.map(copyAllVersionFields)
}
```

- 02 阶段/01 阶段必须保证 assemble response 包含服务端 canonical `requestedFactIds`；禁止回退为仅含实际 claims 的 `canonicalFactIds`，否则 OMIT/未使用事实可能改变 request matrix。
- 发送时在既有 baseline 条件块内一起赋值；条件外不发送。

### T3：扩展 mail request/result 合同与 controller 映射

- Governs：I-2、I-6。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`、`src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`、`src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt`。
- `PendingManualRichReplyRequest` 末尾追加 nullable `trustReplyAssembly`。
- `sendManualRichReply` 方法末尾追加同类型默认 null 参数，降低既有调用改动。
- `PendingMailSendResult` 末尾追加 archive status/count 默认值。
- Controller 显式透传；`UnmatchedInboundTrustWorkbenchTest` 固定 JSON 绑定和 service 参数，确保任意额外 document 字段不能直达 ES。

### T4：实现发送后 authoritative archive helper

- Governs：I-2～I-7。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`、`src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt`、`src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt`。
- 给 `PendingMailOperationService` 注入 workbench service 与 index gateway。
- 增加私有 helper，输入当前 inbound ID/contact、templateTextBody、finalTextBody、operatorName、outbound mailRecordId、候选 assembly：
  1. 无候选直接 NOT_APPLICABLE。
  2. 验证 source type/id。
  3. 调用 authoritative assemble。
  4. 精确比较 raw/template 与 rendered/final text。
  5. 过滤 canonical operator-directed versions，构建 LIVE_SEND documents。
  6. 调 gateway，汇总 status/count。
  7. 捕获 stale/422/ES/未知异常，记录安全日志并返回 FAILED，不抛到发送主路径。
- helper 只能在 SENT 终态调用。

### T5：连接 CLAIMED 与 DEDUP_SENT 两条成功分支

- Governs：I-3、I-4、I-5。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`、`src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`。
- CLAIMED/SAFE_RETRY_CLAIMED：保留 `mailRecordId` 到 response 构造处；调用现有 best-effort send audit 后调用 helper，audit 自身不是归档资格门。
- DEDUP_SENT：取得既有 record.id 后调用同一 helper；没有 record 时不创建虚构 qualification。
- 所有非 SENT 分支增加 mock verify：workbench/index gateway 零调用。
- 不改变 existing delivery classification、Message-ID、finalizeFailure 或 ResponseStatusException。

### T6：前端显示“发送成功 + 归档警告”

- Governs：I-6、I-7、S-1。
- Exact files: `src/main/resources/static/app.js`、`src/test/js/aiReplyReviewConfirmation.test.js`。
- `submitManualRichReply` 保存 API response：
  - SAVED：正常发送成功，可附“已记录 n 条无依据回答”。
  - PARTIAL/FAILED：仍显示“人工回复邮件发送成功”，随后 warn“无依据回答索引未完整写入，请勿重复发送”。
  - NOT_APPLICABLE：保持现有成功文案。
- 无论 archive status 如何，成功后都清理 manual/adopt/preflight state 并刷新详情；不能进入 catch 的发送失败 alert。

### T7：测试正式发送顺序与失败隔离

- Governs：I-1～I-7。
- Exact files: `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt`。

- `PendingMailOperationServiceTrustWorkbenchTest.kt` 覆盖：
  - SMTP/finalize/audit 之后才 archive；文档 ACTIVE/LIVE/LIVE_SEND 和 qualificationId 正确。
  - 编辑/纯人工/无 eligible item不 archive。
  - source 不符、stale、raw mismatch、rendered mismatch -> SENT + FAILED/NOT_APPLICABLE。
  - ES 失败 -> SENT + FAILED，不 finalizeFailure。
  - delivery error/unknown/finalize failure -> archive 零调用。
  - DEDUP_SENT -> 无 SMTP、幂等 archive。
- `UnsupportedAnswerIndexApiTest.kt` 补 LIVE document validation。

### T8：静态资源与全回归

- Governs：I-1～I-7、S-1。
- Exact files: `src/main/resources/static/index.html`、`src/test/js/batchSendTaskConsoleVisualFix.test.js`，以及本计划其余文件用于测试执行。

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q \
  -Dtest=PendingMailOperationServiceTrustWorkbenchTest,UnmatchedInboundTrustWorkbenchTest,UnsupportedAnswerIndexApiTest test
node --check src/main/resources/static/app.js
node --test src/test/js/aiReplyReviewConfirmation.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --test src/test/js/*.test.js
git diff --check
```

## 变更文件清单

| # | 文件 | 动作 | 目的 |
|---:|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt` | 修改 | LIVE canonical document 构建与结果汇总 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 修改 | 可选 assembly、发送成功后重放与 best-effort archive、响应字段 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 修改 | 嵌套 assembly 透传 |
| 4 | `src/main/resources/static/app.js` | 修改 | 保存 assembly、未编辑时提交、显示归档结果 |
| 5 | `src/main/resources/static/index.html` | 修改 | 静态资源 cache-buster |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt` | 修改 | LIVE 文档合同测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | 修改 | 发送终态、顺序、幂等与失败隔离主测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt` | 修改 | HTTP DTO/透传合同 |
| 9 | `src/test/js/aiReplyReviewConfirmation.test.js` | 修改 | 采用 baseline、直接发送与 archive payload 测试 |
| 10 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | 静态资源版本合同 |

文件数：10；子系统：正式发送/归档后端、真实收发前端，共 2 个。

## 验收标准

- I-1: JS 参数化测试对 unchanged/text-edit/html-edit/record-switch/no-adopt 断言 templateTextBody 与 trustReplyAssembly 同时存在或同时缺失。
- I-2: 后端测试篡改 source/stale/version/raw/rendered，断言 authoritative assemble/精确比较失败且 gateway 无 create；客户端文档字段不被采用。
- I-3: Mockito/InOrder 断言 SMTP→finalizeSuccess→recordSendAudit 调用→archive；所有非 SENT/finalize failure 分支 archive 零调用；archive 异常无 finalizeFailure。
- I-4: DEDUP_SENT 断言 SMTP 零调用、读取 existing record、调用同 helper、201/409 均 SENT 且 ES 只有一个 deterministic ID。
- I-5: 捕获 LIVE 文档断言 ACTIVE/LIVE/LIVE_SEND/outbound mailRecordId/canonical contact/campaign/operator；ACK/OMIT/grounded 过滤。
- I-6: request/result JSON 合同测试断言新增字段默认值和四态/count；SENT/messageId 不因 archive result 改变，错误 body不返回。
- I-7: grep/file-list 断言无 migration/outbox/scheduler/retry/re-send route；失败日志只含安全 ID/类别。
- S-1: `git diff -- styles.css` 为空；manual editor/button DOM snapshot 不变；PARTIAL/FAILED 使用 warn 且实值文案先声明“邮件发送成功”。
- Store interaction: finalizeSuccess 创建的 mail_record.id 精确传给 ES qualificationId；ES 结果不 update mail_send_attempt/mail_record/mail_record_qa_rule。
- Regression: 纯人工、QA、template raw/rendered、preflight、五类 ClaimResult、send audit、训练宿主与全后端/JS 测试通过。

## 人工验收清单

### A-1: 未编辑工作台结果发送后入 ACTIVE
- 前置条件: 真实来信工作台含 1 个已采用 operator-directed 无据版本；ES/SMTP 可用；记录 ES 总数 N。
- 操作步骤: 1. 采用整合结果到人工编辑器；2. 不修改文本或格式；3. 点击发送；4. 记下 outbound mail_record.id；5. 索引 Tab 过滤 LIVE。
- 预期结果: 邮件只发送一次，sendStatus=SENT；ES 总数 N+1，新增 ACTIVE/LIVE/LIVE_SEND，qualificationId 等于 outbound id，answerText 等于实际发送的该项回答。
- 覆盖: I-1～I-3、I-5、I-6、需求主结果。

### A-2: 文本编辑后只发送不归档
- 前置条件: 另一封同类来信，记录 ES 总数 N。
- 操作步骤: 1. 采用工作台结果；2. 在编辑器增加一个字符；3. 发送；4. 刷新索引。
- 预期结果: 邮件成功且正文含新增字符；请求不带 template/assembly；ES 总数仍 N，archiveStatus=NOT_APPLICABLE。
- 覆盖: I-1、I-3、must-not-change 1。

### A-3: 仅 HTML 格式编辑也不归档
- 前置条件: 另一封已采用结果，文本保持相同。
- 操作步骤: 选中一段加粗或改变 HTML，再发送并刷新索引。
- 预期结果: 邮件成功；因 innerHTML baseline 不同不携带 assembly；ES 不新增文档。
- 覆盖: I-1、K-ai-preview-raw-adoption-boundary。

### A-4: 纯人工发送回归
- 前置条件: 一封真实来信，不打开/采用工作台；ES 可用。
- 操作步骤: 手工填写主题和正文并发送。
- 预期结果: 按既有校验发送成功、mail_record/attempt 正确；不访问 workbench assemble、不写 ES；无“必须先使用 AI”提示。
- 覆盖: I-3、I-7、must-not-change 1/5。

### A-5: ES 故障不诱导重发
- 前置条件: 采用未编辑工作台结果；让 ES create 返回 503；SMTP 正常。
- 操作步骤: 点击发送一次，查看页面、mail_record、attempt 与索引。
- 预期结果: 邮件已发送并有 SENT mail_record/attempt；页面先显示“邮件发送成功”，再显示“无依据回答索引写入失败，请勿重复发送”；不调用 finalizeFailure、不出现发送失败 alert。
- 覆盖: I-3、I-6、I-7、S-1。

### A-6: 发送失败或未知不入索引
- 前置条件: 分别配置 SMTP safe-retry、delivery-unknown、permanent-failure 三个测试响应；每次有未编辑 assembly。
- 操作步骤: 各发送一次并查询 ES。
- 预期结果: 三次均按既有 503/409/422 语义返回；ES create 次数均为 0；attempt 状态与改前一致。
- 覆盖: I-3、must-not-change 2/3。

### A-7: DEDUP_SENT 幂等补写
- 前置条件: A-1 的同一发送 payload 与既有 SENT attempt/record；记录 SMTP 调用和 ES 总数 N。
- 操作步骤: 重放同一 manual-rich-reply 请求。
- 预期结果: SMTP 调用数不增加；response 仍 SENT；ES 总数保持 N（ALREADY_EXISTS）或仅在首次失败时补成 N+1，永不产生两条同版本文档。
- 覆盖: I-4、I-5、must-not-change 2。

### A-8: 防篡改与 stale 不影响已发送事实
- 前置条件: 测试客户端可篡改 assembly；SMTP 正常。
- 操作步骤: 分别改 sourceId、versionId、raw template 或在采用后让 source/evidence 变化，再发送。
- 预期结果: 邮件仍依据最终正文和既有校验得到 SENT；archiveStatus=FAILED、ES 无文档；response 不泄露 ES/QA 正文或堆栈。
- 覆盖: I-2、I-3、I-6。

### A-9: 索引列表与 UI 样式
- 前置条件: 至少一条 LIVE 文档；浏览器 1440px 和 390px。
- 操作步骤: 在索引 Tab 过滤 LIVE，查看并翻译问题/回答；返回真实来信检查发送区域。
- 预期结果: 列表只读且无复用/编辑/删除；译文不入 ES；发送区域 DOM/按钮布局与改前一致，成功/归档警告分别用 ok/warn，不使用红色发送失败态。
- 覆盖: I-5～I-7、S-1、must-not-change 4/5。
