# Plan 01 — 收发邮件附件查看与下载

> 合并需求拆分件 1/2（先做）。配套件：`02-dmarc-report-intercept.md`（后做）。
> 两个 plan 各自独立可部署、可验证；本 plan 不依赖 Plan 02。

## 需求描述

**可观察结果**：在「收发件箱」邮件详情中，凡是带附件的邮件（无论是已匹配专家的正常往来邮件，还是陌生发件人/未匹配的待处理来信）都能列出附件清单（文件名、大小、类型）并逐个下载。

**不得改变**：
- 现有会话状态机、意图分类、QA/会议自动回复流程，全部不动。
- 已匹配邮件现有的附件落盘与 `expert_document` 关联逻辑（`saveInboundAttachments` 既有行为）保持不变。
- `ExpertDocumentBrowseService` 的专家文档下载/预览接口不动。
- 邮箱列表 `hasAttachment` 既有判定（已匹配走 mail_record_id、未匹配走 messageId→MailRecord）保持兼容。

**范围外（显式延后）**：
- DMARC 报告识别与入库（Plan 02）。
- 附件在线预览（仅做下载；现有 expert-document 的 preview 不扩展到 mailbox）。
- 附件杀毒/类型白名单校验。
- 出站系统邮件（INTRODUCTION/QA/MEETING）目前不带附件，不为其新增上传能力。

## 关键不变量

### Invariant I-1：附件归属二选一
- Rule：`mail_attachment` 行的归属来源**有且仅有一个**：要么 `mail_record_id` 非空（已建 MailRecord 的邮件），要么 `inbound_processing_id` 非空（未匹配、只有 InboundMailProcessing 的邮件）。两者不可同时为空，也不可同时非空。
- Applies to：`MailAttachmentService.saveInboundAttachments`（已匹配路径，写 `mail_record_id`）、新增 `saveUnmatchedAttachments`（未匹配路径，写 `inbound_processing_id`）。
- Violation consequence：附件无法被任一解析路径定位，或在两个详情视图重复出现。

### Invariant I-2：未匹配附件不进 expert_document
- Rule：通过 `inbound_processing_id` 落盘的附件**不得**写 `expert_document` 行（无 expertContactId，无法满足其 NOT NULL + FK）。`expert_document` 仅来自已匹配的 `saveInboundAttachments`。
- Applies to：新增 `saveUnmatchedAttachments`；`ExpertDocumentBrowseService`（其读路径只认有 expert_document 的附件，必须不受影响）。
- Violation consequence：FK 约束失败或脏数据进入专家文档浏览。

### Invariant I-3：下载只信库内 storagePath
- Rule：下载接口只接受 `attachmentId`，文件路径只取自库内 `storage_path`，并强制校验解析后的真实路径位于配置 `basePath` 之内；绝不接受任何外部传入的路径片段。
- Applies to：新增 `MailboxAttachmentService.download`。
- Violation consequence：路径穿越，任意文件读取。

### Invariant I-4：详情视图与下载解析一致
- Rule：邮件详情 `hasAttachment`、附件清单接口、下载接口三者对「某封邮件有哪些附件」的解析必须走同一套 `(source, id) → 附件集合` 逻辑，不得各自实现。
- Applies to：`MailboxService` 内新增的统一解析方法，被 `hasAttachment`/清单接口共同复用。
- Violation consequence：列表显示有附件但清单为空，或反之。

## 现状审计

### 存储：`mail_attachment` 表（V7）
- Schema：`id, mail_record_id BIGINT NOT NULL, file_name, content_type, file_size, storage_path VARCHAR(1024) NOT NULL, created_at`；`KEY idx_mail_attachment_record(mail_record_id, created_at)`；`FK fk_mail_attachment_record → mail_record(id)`。
- Domain：`mail/domain/MailAttachment.kt` — `mailRecordId: Long`（非空）。
- Write paths：
  1. `MailAttachmentService.saveInboundAttachments`（mail/service）— 已匹配邮件：落盘到 `basePath/{contactId}/{mailRecordId}/`，写 `mail_attachment` + `expert_document`。被 `AutoMailReplyService` 两处调用（L107 禁用/handoff 分支、L186 正常分支）。
  2. 无其他写路径。
- Read paths：
  1. `MailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc` — 被 `MailboxService`（L134、L151 算 hasAttachment）、`ExpertContactManagementService`（L48）调用。
  2. `mailAttachmentRepository.findById` — 被 `ExpertDocumentBrowseService`（L47 列文档、L105 下载校验）调用，且与 `expert_document` 联查。
  3. `MailRecordRepository` 列表 SQL：`EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id)`（L235、L266）算列表项 hasAttachment。
- Interaction points：
  - 让 `mail_record_id` 可空 × Read path 2（`ExpertDocumentBrowseService` 用 `attachment.mailRecordId` 反查 MailRecord，L108）：该路径只处理有 `expert_document` 的附件，而未匹配附件按 I-2 不进 expert_document，故 `attachment.mailRecordId` 在该路径恒非空 → 不受影响。**需在验收中确证。**
  - 新增 `inbound_processing_id` × Read path 3（列表 SQL 仅按 mail_record_id 判 hasAttachment）：未匹配项的列表 hasAttachment 由 `MailboxService` 详情逻辑而非该 SQL 决定，需确认列表行 hasAttachment 来源（见下）。

### 邮箱详情/列表：`MailboxService`（mail/service）+ `MailboxController`
- `getMailboxDetail(source, id)`：`source ∈ {MAIL_RECORD, INBOUND_PROCESSING}`。
  - `toDetailFromMailRecord`：`hasAttachment = findAllByMailRecordIdOrderByCreatedAtAsc(recordId).isNotEmpty()`。
  - `toDetailFromInbound`：先 `messageId → findFirstByMessageIdOrderByCreatedAtDesc` 找 MailRecord，再按其 id 查附件；找不到则 `hasAttachment=false`。
- `MailboxDetailResponse` 已含 `hasAttachment: Boolean`、`source`、`id`、`inboundProcessingId`。
- 列表 `listMailbox` 的行 `hasAttachment` 来自 `row.hasAttachment != 0L`（`MailRecordRepository` 聚合 SQL）。

### 未匹配来信落盘缺口（核心缺口）
- `AutoMailReplyService.processSingle` 的 `CONTACT_NOT_FOUND` 分支（L68–L85）：只 `confirmManualReviewWithBody` 写 `InboundMailProcessing`，**完全没有调用 `saveInboundAttachments`** → 陌生发件人邮件的附件字节被丢弃，详情里恒 `hasAttachment=false`。这是「未匹配邮件也能下载附件」必须补的根因。
- `ReceivedMail.attachments`（`MailReceiveService`）在该分支可用（IMAP 已解析附件字节），只是没被持久化。

### 既有下载范式（复用参考）
- `ExpertDocumentBrowseService.validateAndResolve`（document/service，L98–125）+ `ExpertDocumentBrowseController`（`/api/expert-contacts/{contactId}/attachments/{attachmentId}/download`）：已实现「按 attachmentId 取 storagePath、`toRealPath` 校验位于 `basePath` 内、流式返回」的安全下载。新接口照此模式，但**去掉 contactId 维度**（mailbox 下载不绑定专家）。

### 存储路径属性
- `MailAttachmentStorageProperties.basePath`（config）：所有附件根目录；下载校验以它为信任根。

## 实现方案

### Stage A — 数据模型：让 mail_attachment 支持未匹配归属（I-1, I-2）
1. 新迁移 `V36__add_mail_attachment_inbound_processing_link.sql`（新文件，不改 V7）：
   - `ALTER TABLE mail_attachment ADD COLUMN inbound_processing_id BIGINT NULL`；
   - `ALTER TABLE mail_attachment MODIFY mail_record_id BIGINT NULL`；
   - 加 `KEY idx_mail_attachment_inbound(inbound_processing_id, created_at)`；
   - 加 `FK → inbound_mail_processing(id)`；
   - （MySQL 8 可加 `CHECK ((mail_record_id IS NULL) <> (inbound_processing_id IS NULL))` 表达 I-1；若目标库不支持 CHECK，则仅靠应用层保证并在验收中断言）。
2. `mail/domain/MailAttachment.kt`：`mailRecordId: Long?`（改可空）、新增 `inboundProcessingId: Long? = null`。遵守 I-1/I-2。
3. `mail/repository/MailAttachmentRepository.kt`：新增 `findAllByInboundProcessingIdOrderByCreatedAtAsc(inboundProcessingId: Long): List<MailAttachment>`。

### Stage B — 持久化未匹配附件（I-1, I-2）
4. `MailAttachmentService`：
   - 新增 `saveUnmatchedAttachments(inboundProcessingId: Long, attachments: List<ReceivedMailAttachment>): List<MailAttachment>`：落盘到 `basePath/unmatched/{inboundProcessingId}/`，写 `mail_attachment`（`mailRecordId=null, inboundProcessingId=set`），**不写 expert_document**（I-2）。复用既有 `toSafeFileName`。
   - `saveInboundAttachments` 保持原状（`inboundProcessingId=null`）。
5. `AutoMailReplyService.processSingle` 的 `CONTACT_NOT_FOUND` 分支（L68–L85）：
   - `confirmManualReviewWithBody` 写入 `InboundMailProcessing` 后取回其 id，调用 `saveUnmatchedAttachments(inboundProcessingId, received.attachments)`。
   - 注意时序：需先拿到持久化后的 `InboundMailProcessing.id` 再落附件 → 调整 `confirmManualReviewWithBody` 返回保存实体或其 id（仅该方法签名微调，不改其它调用语义）。
   - 仅此分支改动，其余分支不动（已匹配路径已落附件）。

### Stage C — 统一解析 + 清单/下载接口（I-3, I-4）
6. `MailboxService`：新增 `resolveAttachments(source: String, id: Long): List<MailAttachment>`：
   - `MAIL_RECORD` → `findAllByMailRecordIdOrderByCreatedAtAsc(id)`；
   - `INBOUND_PROCESSING` → 先 `findAllByInboundProcessingIdOrderByCreatedAtAsc(id)`；为空再回退到既有 `messageId → MailRecord → findAllByMailRecordId`（兼容历史已匹配但以 inbound 源展示的项）。
   - `hasAttachment`（`toDetailFromMailRecord`/`toDetailFromInbound`）改为复用 `resolveAttachments(...).isNotEmpty()`（I-4）。
7. 新增 `MailboxAttachmentService`（mail/service）：
   - `listAttachments(source, id): List<AttachmentMetaResponse>` → 调 `resolveAttachments`，映射为 `{id, fileName, contentType, fileSize}`。
   - `download(attachmentId): DownloadResult` → 仿 `ExpertDocumentBrowseService.validateAndResolve`：取 `MailAttachment` → `storagePath.toRealPath()` 且 `startsWith(basePath.toRealPath())`（I-3）→ 返回 `Resource` + contentType + fileName。**不绑定 contactId**。
8. 新增 `MailboxAttachmentController`（mail/controller，挂 `/api/mail/mailbox`）：
   - `GET /{source}/{id}/attachments` → 清单。
   - `GET /attachments/{attachmentId}/download` → `ResponseEntity<Resource>` + `Content-Disposition: attachment; filename=...`。

### Stage D — 前端（I-4）
9. `static/app.js` `showMailDetail(source, id)`（约 L4400）：
   - 详情渲染后，若 `detail.hasAttachment`，`GET /api/mail/mailbox/{source}/{id}/attachments`，在「附件」卡片下渲染清单：每项「文件名（大小）+ 下载按钮」，下载指向 `/api/mail/mailbox/attachments/{attachmentId}/download`。
   - 无附件维持现状文案。仅此函数与必要的小工具函数改动。

## 变更文件清单（≤10）

| # | 文件 | 动作 |
|---|------|------|
| 1 | `src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql` | 新增 |
| 2 | `src/main/kotlin/.../mail/domain/MailAttachment.kt` | 改：mailRecordId 可空 + inboundProcessingId |
| 3 | `src/main/kotlin/.../mail/repository/MailAttachmentRepository.kt` | 改：加 findAllByInboundProcessingId |
| 4 | `src/main/kotlin/.../mail/service/MailAttachmentService.kt` | 改：加 saveUnmatchedAttachments |
| 5 | `src/main/kotlin/.../mail/service/AutoMailReplyService.kt` | 改：CONTACT_NOT_FOUND 分支落附件 |
| 6 | `src/main/kotlin/.../mail/service/MailboxService.kt` | 改：resolveAttachments + hasAttachment 复用 |
| 7 | `src/main/kotlin/.../mail/service/MailboxAttachmentService.kt` | 新增 |
| 8 | `src/main/kotlin/.../mail/controller/MailboxAttachmentController.kt` | 新增 |
| 9 | `src/main/resources/static/app.js` | 改：详情附件清单 + 下载 |

新增字段：仅 `mail_attachment.inbound_processing_id`（1 个，符合上限）。子系统：附件持久化 + 附件服务，2 个。

## 验收标准

- I-1：插入一条未匹配附件后断言 `mail_record_id IS NULL AND inbound_processing_id IS NOT NULL`；已匹配附件断言反之。无任一行两列同空或同非空。
- I-2：模拟 `CONTACT_NOT_FOUND` 含附件来信，处理后 `mail_attachment` 有行而 `expert_document` 无新增行；`ExpertDocumentBrowseService` 列表/下载不受影响（回归测试通过）。
- I-3：`download` 对越界/伪造 storagePath 的构造样本拒绝（不在 basePath 内 → 拒绝）；对不存在 attachmentId 返回 404。
- I-4：对同一封邮件，详情 `hasAttachment=true` ⇔ 清单接口返回非空；二者用例交叉断言一致。
- 集成场景（跨交互点）：
  1. 已匹配专家、带附件正常来信：详情可列附件、下载字节与原始一致。
  2. 陌生发件人、带附件来信（待处理项）：详情可列附件并下载，且未污染 expert_document / 专家文档浏览。
  3. 既有「以 inbound 源展示但实际已建 MailRecord」的历史数据：resolveAttachments 回退路径仍能列出附件。
- 全量回归：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`。

## 自检清单

- [x] 关键不变量含每个新字段/路径的规则（I-1 覆盖 inbound_processing_id）
- [x] 现状审计经 grep 列全 mail_attachment 全部读写路径
- [x] 无未被不变量覆盖的新写路径（saveUnmatchedAttachments 受 I-1/I-2 约束）
- [x] 文件数 9 ≤ 10
- [x] 子系统 2 ≤ 2
- [x] 每个任务引用其约束不变量编号
- [x] 验收标准每条不变量至少一项检查
- [x] 文件清单无「相关文件/etc.」
- [x] 范围外显式延后（DMARC、预览、杀毒）
- [x] 保存至 docs/plans/2026-06-26/
