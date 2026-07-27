# 收发邮件页合并待处理邮件 + 收发标签 开发计划

> 日期: 2026-06-22
> 状态: 待实施
> 关联前序计划: `2026-06-22-mailbox-tab-plan.md`（本计划修正其「孤立收件记录 out-of-scope」决策）

---

## 需求描述

**可观察结果**：
1. 「收发邮件」页（`收发件箱` / `view-mailbox`）展示**已激活邮箱账号的全部收发邮件**——不再只显示匹配到专家的邮件，也包含未匹配/待处理/已绑定的入站邮件。原独立「待处理邮件」页合并进本页：以「待处理」标签 + 行内「处理」操作呈现，复用既有处理流程。
2. 列表展示**收发账号、邮件主题、收发时间**（这三列已存在，本计划保留并保证合并后仍正确）。
3. 新增**标签功能**：每行渲染派生标签，识别 `专家 / 待匹配`、`自动回复`、`手动回复`、`首发`、`待处理`、`收件 / 发件`，并支持按标签/「仅待处理」筛选。

**不可变更项**：
- 不修改 `mail_record`、`inbound_mail_processing`、`mail_sender_account`、`expert_contact` 任何表结构（**零 Schema 变更，零新增 Flyway 迁移**）。
- 不修改任何邮件**写路径**（发送/接收/分类/自动回复/绑定/标记已处理流程）。
- 不修改 `/api/mail/unmatched-inbound/*` 端点的行为（仅前端复用调用）。
- 保留现有「收发账号 / 主题 / 时间 / 内容预览 / 附件 / 状态 / 来源」列语义。

**不在范围内**：
- 从该页发送/内联编辑邮件（待处理回复仍走既有 `showUnmatchedDetail` 详情流程）。
- 附件下载/预览（仅「有附件」标记，沿用现状）。
- 标签落库 / 按标签做后端聚合统计（标签为前端可见的派生值，后端只做必要的筛选透传）。
- 历史数据回填（无新增字段，无需回填）。
- 新增数据库索引（沿用现有索引，初期数据量足够；后续按性能再评估）。

---

## 关键不变量

### Invariant I-1: 统一数据源 = 出站(mail_record) ∪ 入站(inbound_mail_processing)
- Rule: 收发邮件列表的数据来源固定为两段 `UNION ALL`：
  - **出站段**：`mail_record WHERE direction = 'OUTBOUND'`
  - **入站段**：`inbound_mail_processing`（**全部入站行**，含 matched / unmatched / 已绑定）
  - **禁止**从 `mail_record` 取 `direction='INBOUND'` 行。
- 依据（审计证实）：每封被处理的入站邮件都会在 `inbound_mail_processing` 写入且仅写入一行（管道顶部以 `(sender_account_code, imap_uid)` 去重，见 `AutoMailReplyService.kt:63`，否则会重复处理）；而 `INBOUND` 的 `mail_record` 行只在 `AutoMailReplyService.kt:167/553` 两处创建，且这两条路径**同时**写入对应的 `inbound_mail_processing` 行——因此 `inbound_mail_processing` 是入站邮件的**完备超集**。
- Applies to: `MailRecordRepository.listMailbox/countMailbox`（改为 UNION 查询）。
- Violation consequence: 若入站同时取两表 → 匹配邮件重复显示；若仅取 `mail_record` → 未匹配/已绑定邮件丢失（即当前 Bug）。

### Invariant I-2: 行唯一标识为 (source, id) 复合键
- Rule: 两表 `id` 自增序列相互独立、**会重叠**。每行必须带 `source ∈ {MAIL_RECORD, INBOUND_PROCESSING}`；前端任何按钮、链接、详情路由必须用 `(source, id)` 区分。待处理处理操作使用入站行的 `inboundProcessingId`（即 `INBOUND_PROCESSING` 行的 `id`）。
- Applies to: `MailboxItemResponse`（新增 `source`、`inboundProcessingId`）、`app.js` 行渲染与事件委托。
- Violation consequence: 出站记录 id 与待处理记录 id 撞号 → 点错记录、误操作。

### Invariant I-3: 查询范围限定已激活账号
- Rule: 两段查询都必须 `sender_account_code IN (所有 enabled=true 账号 code)`；账号下拉只列已激活账号。`inbound_mail_processing.sender_account_code` 非空，可直接过滤。
- Applies to: `MailboxService.listMailbox()` 两段 WHERE、前端账号下拉。
- Violation consequence: 暴露已停用账号邮件，信息泄露（沿用前序计划 I-2）。

### Invariant I-4: 标签为派生值，不落库，规则固定
- Rule: 标签在后端 `MailboxService` 由现有字段派生为 `tags: List<String>`，**不新增任何持久化字段**。派生规则（顺序无关，可叠加）：
  - 专家归属：`expertContactId != null` → `专家`；否则 → `待匹配`
  - 方向：`direction == 'INBOUND'` → `收件`；`'OUTBOUND'` → `发件`
  - 自动回复（仅出站）：`triggeredBy == 'SYSTEM'` **或** `matchedQaRuleId != null` **或** `mailType ∈ {QA_REPLY, MEETING_INVITATION, MEETING_CONFIRMATION}` → `自动回复`
  - 手动回复（仅出站）：`triggeredBy ∈ {OPERATOR, MANUAL}` **或** `mailType == 'MANUAL_QA_REPLY'` → `手动回复`
  - 首发（仅出站）：`mailType == 'INTRODUCTION'` → `首发`
  - 待处理：`source == 'INBOUND_PROCESSING'` **且** `processStatus == 'MANUAL_REVIEW'` → `待处理`
- Applies to: `MailboxService` DTO 组装、前端 badge 渲染。
- Violation consequence: 标签与监控页 `triggeredBy`/`reason_type` 语义不一致，误导运营。

### Invariant I-5: 合并复用既有待处理端点，不新增后端写路径
- Rule: 「待处理」行的处理动作（绑定专家、QA 回复、人工富文本回复、标记已处理、改运营状态、改索引层级）一律复用已有端点 `/api/mail/unmatched-inbound/*`，键用 `inboundProcessingId`。本计划**不**新增/修改任何后端写逻辑或这些端点的行为。
- Applies to: `app.js`（从收发邮件页触发既有 `showUnmatchedDetail` 及处理 handler）。
- Violation consequence: 重复实现处理逻辑导致状态机/工单数据分叉。

### Invariant I-6: 分页与 totalCount 基于 UNION 后的完整结果集
- Rule: `totalCount` 通过对相同 UNION 结构的 `COUNT(*)` 查询取得；排序、`LIMIT/OFFSET` 作用于 `UNION ALL` 后的整体（按 `COALESCE(sent_at, received_at)` 时间倒序），**不得**两表各自分页后拼接。列表只返回 `bodyPreview`（`COALESCE(cleaned_body, body)` 截前 200 字符），不返回完整 body。
- Applies to: `MailRecordRepository` 的 UNION 数据/计数查询、`MailboxService`。
- Violation consequence: 翻页错乱/重复/遗漏；响应体过大卡顿（沿用前序计划 I-4/I-5）。

---

## 现状审计

### mail_record 表（出站数据源）
- Schema 关键字段（`V1__create_business_tables.sql:97`，后续迁移补列）：`id` PK、`expert_contact_id BIGINT NOT NULL FK`、`direction`(INBOUND/OUTBOUND)、`mail_type`(INTRODUCTION/REPLY/QA_REPLY/MANUAL_QA_REPLY/MEETING_INVITATION/MEETING_CONFIRMATION)、`sender_account_code`、`triggered_by`(SYSTEM/OPERATOR/MANUAL，仅出站)、`matched_qa_rule_id`、`subject`、`body` LONGTEXT、`cleaned_body` LONGTEXT、`send_status`(SENT/FAILED)、`sent_at`、`received_at`、`created_at`、`source_inbound_id`。
- **入站 mail_record 写路径（全仓仅 2 处，已 grep 确认）**：
  1. `AutoMailReplyService.kt:164-179` — 自动回复主路径，INSERT INBOUND/REPLY。
  2. `AutoMailReplyService.kt:545-555` `saveMailRecord()` — 自动回复关闭/人工接管路径，INSERT INBOUND/REPLY。
  - 两处**都**在同一方法内写入对应 `inbound_mail_processing` 行（见下表写路径）。⇒ 入站 mail_record ⊂ inbound_mail_processing。
- **出站 mail_record 写路径（本计划不改）**：`InitialOutreachService`(首发)、`AutoMailReplyService`(自动 QA/会议)、`PendingMailOperationService`(人工/QA 回复)、`ManualExpertMailService`(手动邮件)。
- **读路径**：
  1. `findAllByExpertContactIdOrderByCreatedAtAsc` — 专家详情时间线
  2. 监控页 `listIntroductions/listOutboundReplies/aggregateSenderAccountStats`
  3. `findByMessageId` — 待处理候选推荐 / 入站附件富集
  4. **【本计划修改】** `listMailbox/countMailbox` — 改为 UNION（出站取此表）
- 现有索引：`idx_mail_record_dir_type_sent`、`idx_mail_record_dir_received`、`idx_mail_record_sender_sent`、`idx_mail_record_triggered_sent`、`idx_mail_record_source_inbound`、`idx_mail_record_status_created`、`idx`(created_at, V31)。

### inbound_mail_processing 表（入站数据源）
- Schema（`InboundMailProcessing.kt` / `V5` / `V10`）：`id` PK、`sender_account_code NOT NULL`、`imap_uid`、`message_id`、`in_reply_to`、`from_email NOT NULL`、`subject`、`body`、`cleaned_body`、`received_at NOT NULL`、`process_status`(MANUAL_REVIEW/PROCESSED)、`process_reason`、`reason_type`(UNMATCHED_CONTACT/UNCLEAR_INTENT/QA_NO_MATCH/RECIPIENT_UNSUBSCRIBED/…)、`expert_contact_id`(**可空**)、`resolved_*`、`created_at`、`updated_at`。
- **写路径（本计划不改）**：
  1. `AutoMailReplyService.confirmManualReviewWithBody()`（`:789`）— 未匹配/需人工，`MANUAL_REVIEW`，存 `body+cleaned_body`，`expert_contact_id` 可空。
  2. `AutoMailReplyService.confirmProcessed()`（`:821`）— 已处理(`PROCESSED`)或人工(`MANUAL_REVIEW`)，`body = body ?: received.body`（**body 必有值**），`cleaned_body` 可空。
  3. `UnmatchedInboundMailService.bindToContact()`（`:167`）— 绑定后 `UPDATE` 置 `expert_contact_id`，`processStatus='PROCESSED'`，`processReason='MANUAL_BOUND'`（**不写 mail_record**）。
  4. `UnmatchedInboundMailService.markResolved()`（`:201`）— `UPDATE` 置 `PROCESSED`。
  - 顶部去重：`findBySenderAccountCodeAndImapUid` 命中即跳过（`AutoMailReplyService.kt:63`）⇒ 每封入站仅一行。
- **读路径**：
  1. `findManualReviewQueue/countManualReviewQueue`（仅 `process_status='MANUAL_REVIEW'`）— 现「待处理邮件」页
  2. `listInboundActivity/countInboundActivity` — 监控页收信子 Tab
  3. `countGroupedByReasonType` / `count*Between` — 监控指标 & 导航徽标
  4. **【本计划新增】** UNION 入站段
- **绑定语义关键点**：`bindToContact` 仅更新 processing 行、**不**创建 INBOUND `mail_record`（`UnmatchedInboundMailService.kt:130-176` 确认）。⇒ 入站必须以本表为源，否则「已绑定但无 mail_record」的邮件会从收发页消失。

### mail_attachment 表（入站附件标记，仅 EXISTS 子查询）
- 附件以 `mail_record_id` 关联，仅匹配的入站 mail_record 才有附件行；未匹配 processing 行无附件。入站段「有附件」标记通过 `EXISTS(mail_attachment ma JOIN mail_record mr2 ON ma.mail_record_id=mr2.id WHERE mr2.message_id = imp.message_id AND mr2.direction='INBOUND')` 富集（`message_id` 为空则视为无附件，符合现状）。

### mail_sender_account 表（账号过滤）
- 复用 `findAllByEnabledTrue()`，仅读 `account_code/enabled`。

### 前端现状
- 导航：`index.html:106-114`「待处理邮件」`data-view="unmatched"`（含 `unmatchedBadgeHigh/Normal` 徽标）；`index.html:115-121`「收发件箱」`data-view="mailbox"`。
- 收发件箱视图：`index.html:567-611`，表头已含 时间/方向/邮箱账号/专家邮箱/专家姓名/主题/内容预览/邮件类型/来源/附件/发送状态（满足问题2三列）。
- 待处理视图：`index.html` `view-unmatched`（表头 `:543-549`：工单ID/来信发件人/主题/接收时间/匹配候选/挂起原因/操作）+ 详情面板 `#unmatchedDetailPanel`(`:557-564`)。
- `app.js`：`setView`(`:1042`，`:1066` unmatched / `:1067` mailbox)；`loadUnmatched`(`:4089`)、`renderUnmatchedTable`(`:4127`)、`renderUnmatchedActions`(`:4155`)、`showUnmatchedDetail`(`:4165`)；待处理处理 handler 事件委托(`:4361/4374/4414/4431/4451/4471/4497`，调 `/api/mail/unmatched-inbound/*`)；`loadMailbox`(`:5740`)、`renderMailboxTable`(`:5782`)、`renderMailboxPagination`(`:5836`)。

### 交互点
- 新 UNION 读路径消费 `mail_record.{direction,triggered_by,matched_qa_rule_id,mail_type,sender_account_code}` 与 `inbound_mail_processing.{process_status,reason_type,expert_contact_id,sender_account_code}`——全部为既有写路径已落字段，本计划只读不改，无写冲突。
- 前端 `showUnmatchedDetail` + 待处理 handler 从「收发邮件」页复用——键改为 `inboundProcessingId`，端点不变（I-5）。

---

## 实现方案

### Phase 1: 后端 Repository — UNION 查询（遵循 I-1, I-2, I-3, I-6）

**Task 1.1** 改写 `MailRecordRepository.listMailbox/countMailbox` 为 `UNION ALL` 查询，并扩展 `MailboxRow`。
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt`

- `MailboxRow` 增/改字段：`source: String`、`expertContactId: Long?`（改为可空）、`mailType: String`、`triggeredBy: String?`、`matchedQaRuleId: Long?`、`processStatus: String?`、`reasonType: String?`、`inboundProcessingId: Long?`，其余沿用（`subject/bodyPreview/sendStatus/sentAt/receivedAt/senderAccountCode/expertEmail/expertName/hasAttachment`）。
- 数据查询结构（要点，非逐字）：

```sql
SELECT * FROM (
  -- 出站段：仅 mail_record OUTBOUND
  SELECT 'MAIL_RECORD' AS source, mr.id AS id, mr.expert_contact_id,
         mr.direction, mr.mail_type, mr.sender_account_code, mr.triggered_by,
         mr.matched_qa_rule_id, mr.send_status, mr.subject,
         SUBSTRING(COALESCE(mr.cleaned_body, mr.body),1,200) AS body_preview,
         mr.sent_at AS ts, CAST(NULL AS CHAR) AS process_status, CAST(NULL AS CHAR) AS reason_type,
         ec.expert_email, ec.expert_name,
         CAST(EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS SIGNED) AS has_attachment,
         CAST(NULL AS SIGNED) AS inbound_processing_id
    FROM mail_record mr LEFT JOIN expert_contact ec ON mr.expert_contact_id = ec.id
   WHERE mr.direction='OUTBOUND' AND mr.sender_account_code IN (:accountCodes)
     AND (:direction IS NULL OR :direction='OUTBOUND')
     AND (:onlyPending = 0)
     AND (:accountCode IS NULL OR mr.sender_account_code=:accountCode)
     AND (:keyword IS NULL OR mr.subject LIKE CONCAT('%',:keyword,'%') OR COALESCE(mr.cleaned_body,mr.body) LIKE CONCAT('%',:keyword,'%'))
     AND (:recipientEmail IS NULL OR ec.expert_email LIKE CONCAT('%',:recipientEmail,'%'))
     AND (:startTime IS NULL OR mr.sent_at >= :startTime)
     AND (:endTime IS NULL OR mr.sent_at < :endTime)
  UNION ALL
  -- 入站段：inbound_mail_processing 全部
  SELECT 'INBOUND_PROCESSING' AS source, imp.id AS id, imp.expert_contact_id,
         'INBOUND' AS direction, 'REPLY' AS mail_type, imp.sender_account_code, CAST(NULL AS CHAR) AS triggered_by,
         CAST(NULL AS SIGNED) AS matched_qa_rule_id, CAST(NULL AS CHAR) AS send_status, imp.subject,
         SUBSTRING(COALESCE(imp.cleaned_body, imp.body),1,200) AS body_preview,
         imp.received_at AS ts, imp.process_status, imp.reason_type,
         COALESCE(ec2.expert_email, imp.from_email) AS expert_email, ec2.expert_name,
         CAST(EXISTS(SELECT 1 FROM mail_attachment ma JOIN mail_record mr2 ON ma.mail_record_id=mr2.id
                      WHERE mr2.message_id = imp.message_id AND mr2.direction='INBOUND') AS SIGNED) AS has_attachment,
         imp.id AS inbound_processing_id
    FROM inbound_mail_processing imp LEFT JOIN expert_contact ec2 ON imp.expert_contact_id = ec2.id
   WHERE imp.sender_account_code IN (:accountCodes)
     AND (:direction IS NULL OR :direction='INBOUND')
     AND (:onlyPending = 0 OR imp.process_status='MANUAL_REVIEW')
     AND (:accountCode IS NULL OR imp.sender_account_code=:accountCode)
     AND (:keyword IS NULL OR imp.subject LIKE CONCAT('%',:keyword,'%') OR COALESCE(imp.cleaned_body,imp.body) LIKE CONCAT('%',:keyword,'%'))
     AND (:recipientEmail IS NULL OR imp.from_email LIKE CONCAT('%',:recipientEmail,'%'))
     AND (:startTime IS NULL OR imp.received_at >= :startTime)
     AND (:endTime IS NULL OR imp.received_at < :endTime)
) u
ORDER BY ts DESC
LIMIT :limit OFFSET :offset
```

- `countMailbox`：同一内层 `UNION ALL` 外包 `SELECT COUNT(*) FROM ( ... ) u`（去掉 ORDER/LIMIT）。
- 参数：在原签名基础上新增 `onlyPending: Int`（0/1）。`direction` 取值约束为 `OUTBOUND`/`INBOUND`/`null`。
- 约束遵循：出站段禁取 INBOUND、入站段独占 processing（I-1）；`source`+`inbound_processing_id` 透传（I-2）；两段均过滤 `accountCodes`（I-3）；UNION 整体排序分页 + COUNT（I-6）。

### Phase 2: 后端 Service + Controller — 标签派生与筛选透传（遵循 I-3, I-4, I-5, I-6）

**Task 2.1** `MailboxService.listMailbox`：传入 `onlyPending`，把 `MailboxRow` 映射为 `MailboxItemResponse`，按 **I-4** 计算 `tags`。
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt`
- 复用 `findAllByEnabledTrue()` + 指定账号合法性校验（I-3，沿用现逻辑）。
- 新增 `private fun computeTags(row): List<String>` 实现 I-4 全部规则。
- `timestamp = COALESCE(sentAt, receivedAt)` ISO；`expertContactId` 可空透传。

**Task 2.2** `MailboxController`：扩展 DTO 与查询参数。
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxController.kt`
- `MailboxItemResponse` 新增：`source: String`、`tags: List<String>`、`processStatus: String?`、`reasonType: String?`、`inboundProcessingId: Long?`；`expertContactId: Long?` 改可空。
- `MailboxRow` 同步上述字段（若放本文件）。
- `list(...)` 新增可选参数 `pending: Boolean = false`（转 `onlyPending` 0/1 传给 service）；其余参数不变，路径 `/api/mail/mailbox` 不变。

### Phase 3: 前端 — 合并待处理 + 标签 + 筛选（遵循 I-2, I-4, I-5）

**Task 3.1** `index.html`
文件：`src/main/resources/static/index.html`
- 删除「待处理邮件」导航项（`:106-114`）与 `view-unmatched` 列表 section；将徽标元素 `unmatchedBadgeHigh/Normal` 移到「收发件箱」导航项上（保留 id，便于复用计数逻辑）。
- 将待处理详情面板 `#unmatchedDetailPanel`（`:557-564`）整体移动到 `view-mailbox` 内（列表下方）。
- 收发件箱工具栏新增：`标签筛选`下拉（全部/专家/待匹配/自动回复/手动回复/首发/待处理/收件/发件）+ `仅待处理`复选框（对应后端 `pending`，标签筛选默认走前端过滤，仅 `待处理` 走后端 `pending` 以保证跨页完整）。
- 收发件箱表格：在「主题」后新增「标签」列、末尾新增「操作」列；`colspan` 同步更新。

**Task 3.2** `app.js`
文件：`src/main/resources/static/app.js`
- `setView`：移除 `unmatched` 分支（`:1066`），统一由 `mailbox` 承载；进入 mailbox 时若 URL/状态带 `pending` 则置「仅待处理」。
- `loadMailbox`（`:5740`）：拼接新增 `pending` 参数；`state.mailbox` 增加 `onlyPending/tagFilter`。
- `renderMailboxTable`（`:5782`）：
  - 用 `(row.source, row.id)` 作行键；专家邮箱链接仅在 `expertContactId != null` 时可点（I-2）。
  - 新增「标签」列：把 `row.tags` 渲染为 badge（`专家`=ok、`待匹配`=warn、`自动回复`=warn、`手动回复`=普通、`首发`=普通、`待处理`=error、`收件/发件`=普通）。
  - 新增「操作」列：`source=='INBOUND_PROCESSING' && processStatus=='MANUAL_REVIEW'` 行渲染「查看/处理」(data-action=open-pending, data-id=inboundProcessingId) 与「标记已处理」(复用现有 `mark-unmatched-resolved`)；其它行可选「查看专家」。
  - 前端按 `tagFilter` 过滤当前页（`待处理` 除外，其走后端 `pending`）。
- 事件委托：`#mailboxTableBody` 监听 `open-pending` → `showUnmatchedDetail(inboundProcessingId)`（详情面板已移入本视图）；保留并复用既有待处理 handler（绑定/QA/富文本/标记/运营状态/索引层级，端点不变，I-5）。
- 徽标计数：保留既有未处理计数轮询逻辑，目标元素改为收发件箱导航徽标（沿用 id）。
- `loadUnmatched`/`renderUnmatchedTable`/`renderUnmatchedActions` 列表逻辑随 `view-unmatched` 移除而删除；**保留** `showUnmatchedDetail` 及全部处理 handler。

### Phase 4: 测试（遵循 I-1, I-3, I-4, I-6）

**Task 4.1** 扩展 `MailboxServiceTest`
文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt`
- 用例：①未匹配入站（仅 processing 行）出现在列表且带 `待匹配`+`待处理` 标签；②已绑定入站（processing 有 contact、无 mail_record）出现且带 `专家`、不带 `待处理`；③出站 INTRODUCTION 带 `首发`+`发件`；④系统 QA 回复带 `自动回复`；⑤人工回复带 `手动回复`；⑥匹配入站不重复（仅一行）；⑦`pending=true` 仅返回 `MANUAL_REVIEW` 入站；⑧禁用账号邮件不出现（I-3）；⑨`totalCount` 与 UNION COUNT 一致、翻页不重不漏（I-6）。

---

## 变更文件清单

| # | 文件路径 | 操作 | 说明 |
|---|---------|------|------|
| 1 | `src/main/kotlin/.../mail/repository/MailRecordRepository.kt` | 修改 | `listMailbox/countMailbox` 改 UNION；扩展 `MailboxRow` 字段 |
| 2 | `src/main/kotlin/.../mail/service/MailboxService.kt` | 修改 | 传 `onlyPending`；`computeTags` 派生标签；映射新 DTO |
| 3 | `src/main/kotlin/.../mail/controller/MailboxController.kt` | 修改 | 扩展 `MailboxItemResponse`/`MailboxRow`；新增 `pending` 参数 |
| 4 | `src/main/resources/static/index.html` | 修改 | 移除待处理导航/列表、详情面板迁入收发件箱、新增标签/操作列与筛选 |
| 5 | `src/main/resources/static/app.js` | 修改 | 标签/操作渲染、复用待处理 handler、筛选、setView/徽标调整、删除待处理列表逻辑 |
| 6 | `src/test/kotlin/.../mail/service/MailboxServiceTest.kt` | 修改 | 合并/标签/去重/分页用例 |

**总计 6 个文件**（全部修改，0 新增类/迁移），≤10 ✓。
**子系统数**：2（后端 mail 只读查询 / 前端展示），≤2 ✓。
**新增持久化字段**：0（零 Schema 变更），≤1 ✓。

---

## 验收标准

### 按不变量逐项验证
- **I-1**：构造「未匹配入站」「已绑定入站(无 mail_record)」「匹配入站(双表都有)」三类数据，列表分别**有且仅有一行**；匹配入站不因双表而重复；任意入站不丢失。
- **I-2**：构造 `mail_record.id` 与 `inbound_mail_processing.id` 撞号场景，验证出站/待处理行操作互不串号；待处理操作命中正确 `inboundProcessingId`。
- **I-3**：禁用某账号后刷新，该账号收发邮件与账号下拉均不出现。
- **I-4**：逐类断言标签：首发=`首发+发件`、系统QA=`自动回复+发件`、人工回复=`手动回复+发件`、未匹配入站=`待匹配+收件+待处理`、已绑定入站=`专家+收件`（无`待处理`）；规则不读任何新表字段。
- **I-5**：从收发邮件页对待处理行执行 绑定/QA/标记已处理，断言请求命中 `/api/mail/unmatched-inbound/*`，无新增端点；工单状态机行为与原待处理页一致。
- **I-6**：`pageSize=5`，`totalCount` == UNION `COUNT(*)`；连续翻页数据按时间倒序、不重不漏；`bodyPreview ≤ 200` 且不返回完整 body。

### 集成场景
1. 无筛选：返回全部已激活账号收发邮件，按 `COALESCE(sent_at,received_at)` 倒序。
2. `仅待处理`：只剩 `MANUAL_REVIEW` 入站行，且都带「处理」操作。
3. `direction=INBOUND/OUTBOUND`：分别只剩入站段/出站段。
4. 账号/关键词/收件人/日期筛选：两段一致生效（入站收件人匹配 `from_email`，出站匹配 `expert_email`）。
5. 待处理行「查看/处理」打开（已迁入本页的）详情面板，绑定专家后刷新该行标签由 `待匹配/待处理` 变为 `专家`。
6. 空数据/无激活账号：返回空列表不报错。

---

## 自检清单
- [x] 关键不变量存在，覆盖合并数据源(I-1)、复合键(I-2)、激活范围(I-3)、标签派生(I-4)、端点复用(I-5)、分页(I-6)
- [x] 现状审计经 grep 证实入站 mail_record 写路径仅 2 处且均伴随 processing 写入；绑定不写 mail_record（决定入站取 processing 表）
- [x] 无任务引入新写路径（全只读 + 复用既有端点）
- [x] 文件数 6 ≤ 10；子系统 2 ≤ 2；新增持久化字段 0 ≤ 1
- [x] 每个 Task 标注遵循的不变量编号
- [x] 验收标准每个不变量均有检查 + 跨交互点集成场景
- [x] 文件清单无「等/related files」，逐一具名
- [x] Out-of-scope 明确排除发信编辑、附件预览、标签落库、回填、索引优化

---

## 修正记录（对 `2026-06-22-mailbox-tab-plan.md`）
- 该计划「不在范围内」第 3 条将 `inbound_mail_processing` 中未绑定到 `expert_contact` 的孤立收件记录排除在收发件箱之外。**本计划推翻该决策**：收发邮件页需展示全部已激活账号收发邮件（含未匹配/待处理/已绑定入站），入站数据源由 `mail_record(INBOUND)` 改为 `inbound_mail_processing`（完备超集），并合并原「待处理邮件」页。理由见本计划 I-1 与现状审计。
