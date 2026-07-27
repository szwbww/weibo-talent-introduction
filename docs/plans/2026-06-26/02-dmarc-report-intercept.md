# Plan 02 — DMARC 聚合报告识别、拦截与监控入库

> 合并需求拆分件 2/2（后做）。前置件：`01-mailbox-attachment-view-download.md`。
> 本 plan 独立可部署、可验证；不依赖 Plan 01 的运行结果（仅同改 `AutoMailReplyService`，按顺序合并即可）。

## 需求描述

**可观察结果**：收件轮询遇到 DMARC 聚合报告（如 Google 发来的 `Report domain: ... Submitter: google.com Report-ID: ...`）时，不再把它当作专家回复塞进「待处理列表」；改为解析其聚合 XML，将「发信域名认证健康」汇总（通过率、来源 IP 等）写入独立的 `dmarc_report` 表，并在 IMAP 标记已读。

**不得改变**：
- 现有 bounce 跳过逻辑（`receiveAndAutoReply` 中 `bounceDetector.isBounce` 的 `return@forEach`）保持原样、顺序在前。
- 正常专家回复的意图分类、QA/会议/handoff 流程不动。
- 会话状态机不动（DMARC 报告根本不进 `processSingle`，不产生 `InboundMailProcessing`/`MailRecord`/会话状态变更）。

**范围外（显式延后）**：
- 认证 pass 率的前端展示面板与告警（本 plan 只识别+解析+入库）。
- DMARC failure 报告（forensic/ruf）解析；只处理 aggregate（rua）报告。
- 把已误入待处理列表的历史 DMARC 项做回溯清理。

## 关键不变量

### Invariant I-1：DMARC 报告不进会话流
- Rule：被判定为 DMARC 聚合报告的邮件**不得**调用 `processSingle`，不得产生 `InboundMailProcessing`、`MailRecord`、`InboundIntent`、`ManualHandoff` 或任何会话状态迁移；只允许写 `dmarc_report` 表并 `markSeen`。
- Applies to：`AutoMailReplyService.receiveAndAutoReply` 新增的拦截分支。
- Violation consequence：DMARC 报告再次污染待处理列表，或伪造专家联系状态。

### Invariant I-2：识别先于联系人匹配
- Rule：DMARC 判定必须发生在 bounce 跳过之后、`processSingle` 之前，与 bounce 同层。判定只依据邮件自身特征（发件人、主题、contentType、附件名），不依赖联系人是否存在。
- Applies to：`receiveAndAutoReply` 轮询循环顺序。
- Violation consequence：DMARC 报告走到 `CONTACT_NOT_FOUND` 而落入待处理列表。

### Invariant I-3：解析失败不阻断轮询
- Rule：DMARC 解析（gunzip/XML）异常时，记录日志并仍 `markSeen` + `return@forEach`，绝不让单封报告异常中断整轮收件，也不回退到 `processSingle`。
- Applies to：拦截分支的异常处理。
- Violation consequence：一封坏报告卡死收件，或异常下又把报告塞进待处理列表。

### Invariant I-4：幂等
- Rule：同一 `report_id`（来自 XML `<report_metadata><report_id>`）重复收到时，`dmarc_report` 不产生重复行（按 `report_id` 唯一约束 upsert/忽略）。
- Applies to：`DmarcReportRepository` 写入；`V37` 唯一索引。
- Violation consequence：重复轮询导致统计重复累计。

## 现状审计

### 收件轮询：`AutoMailReplyService.receiveAndAutoReply`（mail/service，L491–543）
- 流程：`fetchUnread(account, maxMessages)` → 逐封：
  1. `bounceDetector.isBounce(from, subject, contentType=null)` 为真 → `log.debug` + `return@forEach`（**既有跳过范式**，DMARC 拦截照此插入其后）。
  2. 否则 `processSingle(account, it, skipImapAck=false)`。
- `processSingle` 的 `CONTACT_NOT_FOUND` 分支（L68–85）：DMARC 报告发件人（`noreply-dmarc-support@google.com` 等）匹配不到联系人 → 当前正是从这里落入待处理列表的根因。

### 识别范式参考：`BounceDetector`（mail/service）
- `isBounce(from, subject, contentType)`：纯特征判定、零副作用、`@Service`。DMARC 检测器照此结构。
- 注意：轮询调用 bounce 时传 `contentType=null`（`ReceivedMail` 未带 contentType 字段）。DMARC 检测同样只能用 `from`/`subject`/`attachments`（`ReceivedMailAttachment.fileName`/`contentType`），**不能依赖顶层 contentType**。

### 收件数据结构：`ReceivedMail` / `ReceivedMailAttachment`（mail/service/MailReceiveService）
- `ReceivedMail`：`from, subject, body, messageId, inReplyTo, receivedAt, attachments`。无顶层 contentType。
- `ReceivedMailAttachment`：`fileName, contentType, content: ByteArray`。DMARC aggregate 附件典型名 `<receiver>!<domain>!<begin>!<end>.xml.gz`（或 `.zip`/`.xml`），contentType 多为 `application/gzip`。解析直接读 `content` 字节。

### 存储：新表 `dmarc_report`
- 现无相关表。最新迁移为 `V35`（已确认 migration 目录），新表用 `V37`（Plan 01 占用 V36；本 plan 始终用 V37，保证两 plan 顺序合并时迁移号不冲突）。
- 无既有读路径（本 plan 不建前端；后续健康面板再消费）。

### `markSeen`：`MailReceiveService.markSeen(account, imapUid)`
- bounce 跳过路径未显式 `markSeen`（靠后续 `collectBounces` 或下轮处理）；但 DMARC 报告永不进 `processSingle`，**必须在拦截分支显式 `markSeen`**，否则每轮重复拉取。

## 实现方案

### Stage A — 识别（I-2）
1. 新增 `DmarcReportDetector`（mail/service，`@Service`）：
   - `isDmarcAggregateReport(from: String?, subject: String?, attachments: List<ReceivedMailAttachment>): Boolean`：
     - 发件人含 `dmarc`（如 `noreply-dmarc-support@`、`dmarcreport@`）；或
     - 主题/正文特征含 `report domain` 且含 `submitter`/`report-id`；或
     - 任一附件名匹配 `.*!.*\.xml(\.gz|\.zip)?$` 或文件名/ contentType 指向 gzip+xml 的聚合报告。
   - 纯判定，无副作用，仿 `BounceDetector`。

### Stage B — 解析（I-3, I-4）
2. 新增 `DmarcReportParser`（mail/service）：
   - `parse(attachment: ReceivedMailAttachment): DmarcReportSummary?`：按文件名/魔数判定 gzip/zip → 解压 → 解析 aggregate XML：
     - `report_metadata`：`report_id`、`org_name`、`date_range.begin/end`；
     - `policy_published.domain`；
     - 遍历 `record`：累计 `count`、`row.policy_evaluated.dkim/spf` 的 pass 数、DMARC pass 数、出现最多的 `source_ip`。
   - 返回汇总 DTO；解析异常返回 `null`（由调用方按 I-3 处理）。
3. domain `DmarcReport`（mail/domain，`@Table("dmarc_report")`，Spring Data JDBC 不可变 data class）：
   `id, reportId, orgName, domain, dateBegin, dateEnd, totalCount, dkimPassCount, spfPassCount, dmarcPassCount, topSourceIp, createdAt`。
4. `DmarcReportRepository`（mail/repository，`CrudRepository`）：`existsByReportId(reportId: String): Boolean`（幂等用，I-4）。

### Stage C — 拦截接线（I-1, I-2, I-3）
5. `AutoMailReplyService.receiveAndAutoReply` 轮询循环：在 bounce 跳过分支**之后**、`processSingle` 调用**之前**插入：
   ```
   if (dmarcReportDetector.isDmarcAggregateReport(it.from, it.subject, it.attachments)) {
       try { dmarcReportIngestService.ingest(it.attachments) }
       catch (e: Exception) { log.warn("DMARC parse failed uid={}", it.imapUid, e) }
       mailReceiveService.markSeen(account, it.imapUid)   // I-1: 显式标读，不进 processSingle
       return@forEach
   }
   ```
   - 新增构造注入：`dmarcReportDetector`、`dmarcReportIngestService`。
   - 只动这一处循环体；其余方法不变。
6. 新增 `DmarcReportIngestService`（mail/service）：`ingest(attachments)` → 对每个聚合附件 `parse` → 非空且 `!existsByReportId` 时 `save`（I-4）。封装解析+幂等+落库，使 `AutoMailReplyService` 仅依赖一个协作者。

### Stage D — 迁移
7. 新迁移 `V37__create_dmarc_report.sql`（新文件）：建 `dmarc_report` 表，`report_id` 加 `UNIQUE KEY`（I-4），其余字段如 domain 模型；不改任何已应用迁移。

## 变更文件清单（≤10）

| # | 文件 | 动作 |
|---|------|------|
| 1 | `src/main/kotlin/.../mail/service/DmarcReportDetector.kt` | 新增 |
| 2 | `src/main/kotlin/.../mail/service/DmarcReportParser.kt` | 新增 |
| 3 | `src/main/kotlin/.../mail/service/DmarcReportIngestService.kt` | 新增 |
| 4 | `src/main/kotlin/.../mail/domain/DmarcReport.kt` | 新增 |
| 5 | `src/main/kotlin/.../mail/repository/DmarcReportRepository.kt` | 新增 |
| 6 | `src/main/kotlin/.../mail/service/AutoMailReplyService.kt` | 改：轮询插入拦截分支 + 注入 |
| 7 | `src/main/resources/db/migration/V37__create_dmarc_report.sql` | 新增 |

新增字段：全部在新表 `dmarc_report`，不触碰任何既有共享存储。子系统：DMARC 摄取（检测/解析/入库）1 个 + 收件接线 1 个，2 个。

## 验收标准

- I-1：喂入一封 DMARC 聚合报告样本，断言处理后 `inbound_mail_processing`/`mail_record`/`inbound_intent`/`manual_handoff` 均无新增行，会话状态无迁移；仅 `dmarc_report` +1。
- I-2：构造「DMARC 报告 + 系统中无对应联系人」场景，断言不落入待处理列表（不走 CONTACT_NOT_FOUND）。
- I-3：喂入损坏的 gz/XML 报告，断言：不抛出中断整轮、该邮件被 `markSeen`、不产生待处理项、后续邮件仍正常处理。
- I-4：同一 `report_id` 报告投递两次，断言 `dmarc_report` 只 1 行。
- 解析正确性：用真实 Google aggregate XML 样本，断言 `org_name=google.com`、`report_id`、`domain`、各 pass 计数与样本一致。
- 回归：bounce 跳过、正常专家回复全链路用例不受影响；全量 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`。

## 自检清单

- [x] 关键不变量含每个新机制规则（I-1 不进会话流、I-4 幂等）
- [x] 现状审计经 grep 列全收件轮询读写与 markSeen 语义
- [x] 无未被不变量覆盖的新写路径（dmarc_report 写入受 I-4 约束）
- [x] 文件数 7 ≤ 10
- [x] 子系统 2 ≤ 2
- [x] 每个任务引用其约束不变量编号
- [x] 验收标准每条不变量至少一项检查
- [x] 文件清单无「相关文件/etc.」
- [x] 范围外显式延后（前端面板、告警、forensic 报告、历史回溯）
- [x] 保存至 docs/plans/2026-06-26/
