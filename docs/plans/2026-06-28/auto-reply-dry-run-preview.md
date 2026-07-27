# 自动回复 Dry-Run 预览（人工工作台）开发计划

> 用 create-p 编写。配套技能：fix-v 验证。

## 需求描述

- **可观察结果**：运营在人工回复某封专家来信（人工工作台 `unmatched-inbound` 详情页）时，能点一个「预览自动回复」按钮，看到「如果该专家此刻开启自动回复，系统会回什么」——包括：识别出的意图、会走哪条分支（QA 自动回复 / 发会议邀请 / 转人工 / 关闭）、若是 QA 则展示拼装后的回复主题与正文、若是会议邀请则展示渲染后的邀请正文、若转人工则展示转人工原因。用于运营据此调整自动回复策略（QA 关键词、规则覆盖）。
- **不真发邮件、不写库、不改状态**：纯只读 dry-run，零副作用。
- **必须不变**：
  - `AutoMailReplyService.processSingle` 的真实自动回复行为、事务、状态机、发件、写库一律不改。
  - `InboundIntentClassifier` / `QaMatchService` / `MailTemplateService` / `MailBodyCleaner` 不改实现，只被只读复用。
  - 现有人工工作台接口（`composed-reply/suggest`、`composed-reply`、`qa-reply`、`manual-rich-reply` 等）行为不变。
- **范围外（显式延后）**：
  - 完整端到端模拟器（`docs/auto-reply-simulator-plan.md` 的方案 2）——本计划只做轻量只读预览。
  - 附件驱动的意图覆盖（`effectiveIntent` 里 `UNKNOWN + 附件 → 由附件推断`）——预览不计入附件意图，仅以文本预览，页面显式标注「未计入附件意图」。见 I-3。
  - 退订（`EmailSuppressionService`）拦截、会议已发（`hasMeetingInvitation`）等「是否真的发得出去」的运行期闸门，仅作为信息标记，不影响预览展示的「会回什么内容」。见 I-2。
  - 任意自由文本输入的预览（先只支持基于已有入站记录 `inboundProcessingId` 的预览）。

## 关键不变量

### Invariant I-1: 预览决策链与真实自动回复决策链同源同序
- Rule: 预览的意图判定、分支选择、QA 拼装、会议正文渲染，必须复用与 `AutoMailReplyService.processSingle` **完全相同的注入 Bean**（`InboundIntentClassifier.classify`、`QaMatchService.match`、`MailTemplateService.render("MEETING_INVITATION", ...)`、`MailBodyCleaner.clean`、`MailContentService.plainTextToHtml`），并按 `processSingle` 中相同的 **分支判定顺序** 复现：`classify → effectiveIntent(文本部分) → when(autoAction){ MANUAL_REVIEW / CLOSE / SEND_MEETING_INVITATION / QA } → QA 时再 match → null|!autoReplyEnabled|handoffRequired ⇒ QA_NO_MATCH；gapDetected ⇒ QA_GAP；否则 QA_AUTO_REPLIED`。
- Applies to: 新增 `AutoReplyPreviewService.preview(...)`。
- Violation consequence: 预览展示的内容与真实自动回复不一致，运营据此误调策略；属误导性功能（比 K-composed-reply-order-contract 更严重，因为这是「预测真实行为」的功能）。
- 来源: original（受 K-composed-reply-order-contract 启发：预览与实际外发必须同序同源）

### Invariant I-2: 预览是「假如开启自动回复」的反事实，忽略 per-contact 启停/状态闸门
- Rule: 预览**不**因 `contact.autoReplyEnabled == false`、`contact.currentStatus == MANUAL_HANDOFF`、退订、会议已发等「运行期是否会真的自动回」的闸门而短路隐藏内容；它回答的是「若自动回复开启、且未被这些闸门拦截，会回什么内容」。这些闸门只作为**信息性提示**附在结果里（如 `wouldBeBlockedBy: ["AUTO_REPLY_DISABLED"]`），不改变 `previewKind` 与展示的回复正文。
- Applies to: `AutoReplyPreviewService.preview(...)`。
- Violation consequence: 运营在「已转人工/已关自动回复」的记录上点预览却看不到 QA 内容，功能对最常见场景（人工队列里的记录本就 auto-reply 关闭）失效。
- 来源: original

### Invariant I-3: 预览输入体与真实管线输入体一致，附件意图覆盖被显式排除
- Rule: 预览的分类输入 = `record.cleanedBody`（非空时）否则 `MailBodyCleaner.clean(record.body)`，主题 = `record.subject`；与 `processSingle` 对入站文本的取法一致。预览**不**执行 `effectiveIntent` 的附件意图覆盖（`UNKNOWN + 附件 → MANUAL_REVIEW`），因此当真实来信带附件且文本为 `UNKNOWN` 时，预览结果可能与真实不同；该差异必须在响应中以 `attachmentIntentIgnored=true` 标注，前端显式提示。
- Applies to: `AutoReplyPreviewService.preview(...)`，响应 DTO。
- Violation consequence: 静默偏差 → 误导；I-3 把它变成「已知且可见」的偏差。
- 来源: original

## 现状审计

本功能**只读**，不新增任何写路径。审计聚焦「被复用的只读判定链」与「数据来源记录」。

### `AutoMailReplyService.processSingle`（决策链权威实现，本计划镜像对象，不改）
- 关键只读判定（`src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`）：
  - `:206` `inboundIntentClassifier.classify(cleanedBody, received.subject)` → `:207` 私有 `effectiveIntent(classified, attachments)`（含附件覆盖，I-3 排除）。
  - `:225` `when (intent.autoAction)`：`MANUAL_REVIEW`(:226) / `CLOSE`(:258) / `SEND_MEETING_INVITATION`(:291) / `QA`(:380)。
  - `:227` `manualReviewReason(intent.intentCode)`（私有；预览需镜像同一映射）。
  - `:292` `hasMeetingInvitation(contactId)`（会议已发 → 转 CONFIRM_MEETING，不再发）。
  - `:383` `qaMatchService.match(cleanedBody)`；`:384` `match==null || !match.autoReplyEnabled || match.handoffRequired` ⇒ QA_NO_MATCH；`:415` `match.gapDetected` ⇒ QA_GAP；否则 `:470` `plainBody=match.replyBody`、`:473` subject。
  - `:812` 会议邀请正文 = `mailTemplateService.render("MEETING_INVITATION", mailTemplateVariables(account))`。
- 私有成员预览需复制（纯函数，无状态）：`effectiveIntent`(文本分支部分)、`manualReviewReason`、`hasMeetingInvitation`/`hasIntroductionInquiry` 的等价只读查询。
  - 交互点：`effectiveIntent`、`manualReviewReason` 当前为 `private`。**决策**：执行时优先把这两段所需的纯逻辑复制进预览服务（不改 `AutoMailReplyService` 可见性，降低对核心类的扰动）；并由 I-1 + 单测锁定其与权威实现一致。

### `InboundIntentClassifier`（只读，纯函数）
- `classify(body, subject)` → `InboundIntentClassification(intentCode, confidence, matchedKeywords, autoAction)`。关键词表与 `autoAction` 映射见 `src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundIntentClassifier.kt`。
- autoAction 映射事实：`INTERESTED→SEND_MEETING_INVITATION`；`ASK_REMOTE_PART_TIME/ASK_PROCESS/ASK_MORE_INFO→QA`；`UNKNOWN(非空文本)→QA`；空文本→`UNKNOWN/MANUAL_REVIEW`；`NOT_INTERESTED→CLOSE`；其余 ASK_*/MEETING_*/CV/DOCS→MANUAL_REVIEW。

### `QaMatchService.match`（只读，不写库）
- 返回 `QaMatchResult(ruleId, replySubject, replyBody, handoffRequired, autoReplyEnabled, matchedRuleIds, gapDetected)` 或 `null`。
- 读路径：`qaRuleRepository.findAllEnabledOrdered()`、`qaCategoryRepository.findAll()`。`applySupersede` + `QaReplyComposer.compose`（按 category composeOrder）。
  - 来源 K-overview-gap-supersede：`detectGap` 用覆盖前命中集；预览直接用 `match.gapDetected`，天然一致，不重算。
  - 来源 K-composed-reply-order-contract：预览展示 `match.replyBody` 即自动回复实际拼装顺序的正文，**不**自行重排。

### `MailTemplateService.render`（只读渲染）
- `render("MEETING_INVITATION", variables)` → `RenderedMail(subject, body)`。变量来自 sender account 字段（`mailTemplateVariables`）。
  - 交互点：预览需要一个 account 来填模板变量。真实管线用 contact 绑定的发件账号。**决策**：预览取 `record.senderAccountCode` 对应账号（`MailSenderAccountService`）渲染；仅用于展示，不发件。

### 数据来源：`InboundMailProcessing`（只读）
- 字段：`expertContactId`、`subject`、`body`、`cleanedBody`、`senderAccountCode`（见 `UnmatchedInboundMailController.InboundMailProcessingResponse`）。预览以 `inboundProcessingId` 取该记录。
- 读路径已存在：`inboundMailProcessingRepository.findById`（`PendingMailOperationService.suggestComposedReply` 同款用法）。

### 接口落点
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`，与 `:201 GET /unmatched-inbound/{id}/composed-reply/suggest` 并列，新增 `GET /unmatched-inbound/{id}/auto-reply-preview`。

### 前端落点
- `src/main/resources/static/app.js`：人工工作台详情面板（渲染 `composed-reply/suggest` 的同一区域附近）加「预览自动回复」按钮 + 结果展示。
- `src/main/resources/static/index.html` / `styles.css`：按需加按钮容器与样式。

## 实现方案

### 阶段一：后端只读预览服务（遵循 I-1 / I-2 / I-3）

**Task 1 — 新增 `AutoReplyPreviewService`**（I-1, I-2, I-3）
- 新文件 `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt`。
- 注入：`InboundMailProcessingRepository`、`MailBodyCleaner`、`InboundIntentClassifier`、`QaMatchService`、`MailTemplateService`、`MailContentService`、`MailSenderAccountService`、`MailRecordRepository`（仅用于 `hasMeetingInvitation`/`hasIntroductionInquiry` 只读 `existsBy...`）、`ExpertContactRepository`（读 `autoReplyEnabled`/`currentStatus` 仅供 I-2 信息标记）。
- 方法 `fun preview(inboundProcessingId: Long): AutoReplyPreviewResult`：
  1. 取 `record`；`cleanedBody = record.cleanedBody?.ifBlank{null} ?: mailBodyCleaner.clean(record.body.orEmpty())`（I-3）。
  2. `classification = inboundIntentClassifier.classify(cleanedBody, record.subject)`（**不**做附件覆盖；置 `attachmentIntentIgnored = 真实记录是否有附件`，I-3）。
  3. 按 `processSingle` 同序 `when(classification.autoAction)` 计算 `previewKind` 与内容（I-1）：
     - `MANUAL_REVIEW` → `previewKind=MANUAL_HANDOFF`，`reason=manualReviewReason(intentCode)`（镜像映射），无回复正文。
     - `CLOSE` → `previewKind=MANUAL_HANDOFF`（关闭语义落人工），`reason=INTENT_<code>`。
     - `SEND_MEETING_INVITATION` → 若 `hasMeetingInvitation(contactId)` 为真：`previewKind=MEETING_ALREADY_SENT`（信息提示，仍展示模板正文）；否则渲染 `MEETING_INVITATION` 模板，`previewKind=MEETING_INVITATION`，回填 `replySubject/replyBody`。
     - `QA` → `match = qaMatchService.match(cleanedBody)`：
       - `match==null || !match.autoReplyEnabled || match.handoffRequired` → `previewKind=QA_NO_MATCH`，`reason=QA_NO_MATCH`。
       - `match.gapDetected` → `previewKind=QA_GAP`，`reason=QA_GAP`。
       - 否则 → `previewKind=QA_AUTO_REPLIED`，`replySubject=match.replySubject ?: "Re: <subject>"`，`replyBody=match.replyBody`，`matchedRuleIds=match.matchedRuleIds`。
  4. I-2 信息标记：计算 `wouldBeBlockedBy: List<String>`，含 `AUTO_REPLY_DISABLED`（`!contact.autoReplyEnabled`）、`MANUAL_HANDOFF_STATUS`（`currentStatus==MANUAL_HANDOFF`）、`INTRODUCTION_NOT_SENT`（`!hasIntroductionInquiry`）。**不**因此改 `previewKind` 或隐藏正文。
- 不加 `@Transactional`（纯只读，避免误导有写）。所有 repo 调用均为 `findById`/`existsBy`。
- 同文件定义 `data class AutoReplyPreviewResult(previewKind, intentCode, autoAction, confidence, matchedKeywords, replySubject?, replyBody?, reason?, matchedRuleIds, wouldBeBlockedBy, attachmentIntentIgnored)` 与 `enum class AutoReplyPreviewKind { QA_AUTO_REPLIED, QA_NO_MATCH, QA_GAP, MEETING_INVITATION, MEETING_ALREADY_SENT, MANUAL_HANDOFF }`。

**Task 2 — Controller 暴露只读接口**（I-1）
- 改 `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`：注入 `AutoReplyPreviewService`，新增：
  ```
  @GetMapping("/unmatched-inbound/{id}/auto-reply-preview")
  fun previewAutoReply(@PathVariable id: Long): AutoReplyPreviewResponse
  ```
- 在本文件新增 `data class AutoReplyPreviewResponse(...)` 与 `AutoReplyPreviewResult.toResponse()`（字段一一映射；`previewKind`/`intentCode`/`autoAction` 输出为字符串）。

### 阶段二：前端按钮与展示（I-2, I-3）

**Task 3 — 人工工作台加「预览自动回复」**
- 改 `src/main/resources/static/app.js`：详情面板加按钮，调 `GET /api/mail/unmatched-inbound/{id}/auto-reply-preview`，渲染：
  - 顶部徽标：`previewKind`（QA 自动回复 / 会议邀请 / 转人工 / QA 未命中 / QA 缺口 / 会议已发）。
  - 意图行：`intentCode` + `confidence` + `matchedKeywords`。
  - 若有 `replyBody`：用现有 `.pre`（white-space:pre-wrap，参考 K-plaintext-reply-client-reflow）展示 `replySubject` + `replyBody`，并经 `escapeHtml`。
  - 若 `previewKind` 属转人工类：展示 `reason`，不展示回复正文。
  - `wouldBeBlockedBy` 非空 → 黄色提示条「当前若收到此信不会自动发送，原因：…（本预览仍展示假如开启会回的内容）」（I-2）。
  - `attachmentIntentIgnored==true` → 提示「该来信含附件，本预览未计入附件意图推断，实际自动处理可能转人工」（I-3）。
- `src/main/resources/static/index.html` / `styles.css`：按需加按钮与提示条样式（若可复用现有类则不改 css）。

### 阶段三：测试（验收 I-1 / I-2 / I-3）

**Task 4 — `AutoReplyPreviewServiceTest`**
- 新文件 `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt`，mock 各 repo/service，覆盖：
  - QA 命中 → `QA_AUTO_REPLIED` + 正文/主题来自 `match`。
  - QA `match==null` / `handoffRequired` / `!autoReplyEnabled` → `QA_NO_MATCH`。
  - `gapDetected` → `QA_GAP`。
  - `INTERESTED` 且未发过会议 → `MEETING_INVITATION` + 渲染正文；已发过 → `MEETING_ALREADY_SENT`。
  - `ASK_FUNDING`/`CV` 等 → `MANUAL_HANDOFF` + 正确 `reason`（镜像 `manualReviewReason`）。
  - `NOT_INTERESTED` → `MANUAL_HANDOFF`。
  - I-2：`autoReplyEnabled=false` / `currentStatus=MANUAL_HANDOFF` 时，正文仍展示，`wouldBeBlockedBy` 含对应项。
  - I-3：record 带附件且文本 `UNKNOWN` → `attachmentIntentIgnored=true`，且走文本分支（`UNKNOWN→QA`）。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt` | 新增 | 只读预览服务 + 结果 DTO/enum |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 修改 | 新增 GET 预览接口 + 响应 DTO + 映射 |
| 3 | `src/main/resources/static/app.js` | 修改 | 预览按钮 + 结果渲染 + 提示条 |
| 4 | `src/main/resources/static/index.html` | 修改 | 预览按钮容器（如需要） |
| 5 | `src/main/resources/static/styles.css` | 修改 | 提示条/徽标样式（如不可复用） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt` | 新增 | 决策链覆盖测试 |

文件数 6 ≤ 10。子系统：后端预览（1-2,6）+ 前端（3-5），共 2 ≤ 2。

## 验收标准

- **I-1**：`AutoReplyPreviewServiceTest` 对每种 `autoAction` 分支断言 `previewKind`/`reason`/正文来源，与 `processSingle` 同序同源；QA 正文断言等于 `qaMatchService.match(...).replyBody`（不重排）。手工核对预览服务的 `when` 顺序与 `AutoMailReplyService.kt:225-381` 完全一致。
- **I-2**：测试断言 `autoReplyEnabled=false`、`currentStatus=MANUAL_HANDOFF`、无 INTRODUCTION 三种情形下，`replyBody` 仍按意图/QA 计算返回，且 `wouldBeBlockedBy` 含 `AUTO_REPLY_DISABLED`/`MANUAL_HANDOFF_STATUS`/`INTRODUCTION_NOT_SENT`。
- **I-3**：测试断言带附件 + 文本 `UNKNOWN` 时 `attachmentIntentIgnored=true` 且走文本 `QA` 分支；前端在该标记下渲染提示。
- **集成/接口**：`GET /api/mail/unmatched-inbound/{id}/auto-reply-preview` 返回 200 与完整 DTO；不产生任何 `mail_record`/状态变更/发件（人工核对：服务无 `@Transactional`、无 `save`/`mailDeliveryService.send` 调用）。
- **回归**：`processSingle`、`PendingMailOperationService` 及既有人工工作台接口无改动；`mvn test` 全绿。

## 自审清单

- [x] 关键不变量含 ≥1 条针对新行为（I-1/I-2/I-3）
- [x] 现状审计列出被复用判定链的全部读路径（已 grep `processSingle`/`QaMatchService`/`InboundIntentClassifier`/控制器）
- [x] 无新增写路径（纯只读）；故无「写路径未被不变量覆盖」风险
- [x] 文件数 ≤ 10（6）
- [x] 子系统 ≤ 2（后端 + 前端）
- [x] 每个 Task 引用其约束不变量编号
- [x] 验收标准每条不变量至少一项检查
- [x] 文件清单无「等/相关文件」，均具名
- [x] 范围外显式延后（完整模拟器、附件意图、运行期闸门拦截、自由文本预览）
- [x] Phase 0 知识：K-composed-reply-order-contract / K-overview-gap-supersede / K-plaintext-reply-client-reflow / K-gap-items-compose-only 已用于约束设计
- [x] 计划存于 `docs/plans/2026-06-28/`

## Phase 0 知识使用记录

- **K-composed-reply-order-contract**（命中）：预览展示 `match.replyBody`（自动回复实际拼装顺序），禁止前端/预览重排 → I-1。
- **K-overview-gap-supersede**（命中）：预览直接复用 `match.gapDetected`，不自行重算缺口 → 现状审计 QA 段。
- **K-plaintext-reply-client-reflow**（命中）：前端用 `.pre`(pre-wrap)+`escapeHtml` 展示纯文本正文 → Task 3。
- **K-gap-items-compose-only**（命中）：明确 `gapItems` 是人工组装台数据，预览不接入 `detectGap`，仅消费 `match.gapDetected` → 不污染自动语义。
