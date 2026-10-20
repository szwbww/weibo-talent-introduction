# 可执行修改方案：仅首封回复增加自动回复判断

版本：V3（2026-10-20）；状态：待用户评审，未实施、未部署。
编排：create-p；先完成本后端计划，再完成配套文案计划，分别验证。
本版完整替代此前扩大业务联动的方案；业务规则以本版为准。

配套：[“待匹配”改为“待确认”](./mailbox-pending-confirmation-label.md)。两份可独立验证；后端 10 个实施文件、前端 5 个实施文件，共 15 个：生产代码/资源 7 个、测试 7 个、历史修正脚本 1 个。无数据库结构变更。
基线：`5f1e99d4fe024d698759c16ae5e2c9ba40554648`；保留工作区其他未提交改动。

## 需求描述

仅在原逻辑已匹配专家、通过原收信安全/首发检查、确认是该专家首封回复时，增加邮件头和五分钟两个判断。符合任一条件、原本会进入 MANUAL_REVIEW 的首封回复记为系统已处理，保留原文。

**先判首封，再检查两条规则；第二封及后续回复不进入这两条判断。未匹配、收件归属不明确及原安全兜底路径照旧。**

| 规则 | 精确条件 | 结果 |
|---|---|---|
| 首封＋邮件头 | 原始顶层 `Auto-Submitted` 的值为 `auto-replied` | 不计待处理 |
| 首封＋五分钟 | 我方对应成功发信后 0～300 秒收到对方来信，包含第 300 秒 | 不计待处理 |
| 首封但均未命中 | 无上述邮件头且时间窗口未命中/无法计算 | 保留原处理结果 |

非首封回复即使带 auto-replied 或在五分钟内到达，也完全沿用原处理结果。标题、正文、休假关键词、合作意向不参与判断；真人首封回复在窗口内也按用户规则排除。

必须保持：
- 原文、邮件历史和既有附件处理；仅首封增加判断，不拉黑邮箱，不按新一轮发信重置首封。
- 专家晋级、专家状态/人工交接、群提醒、自动发送、回复预览沿用原流程。
- 用户挂起状态/原因、跟进标记、收件/已回复/上次回复统计不变。
- 物理账号、UIDVALIDITY、UID 判重、事务提交后确认与失败重试不变。
- 非首封/未命中来信的人工处理、取消处理保持；未匹配来信的入队、分页、绑定、人工操作全部保持。

范围外：提前中断收信业务流程、阻止自动发送/群提醒、删除邮件/材料、降级专家、改专家人工关注标记、改回复率、改退信/DMARC、重构 UI。当前需求中的“待处理”指邮件队列，不扩展到专家状态机或人工交接工单。

## 关键不变量

### Invariant I-1：仅两条 OR 规则
- Rule：只有 I-7 首封资格成立后才计算 HEADER OR FAST_REPLY_5M；不接收标题/正文作为分类输入。`Auto-Submitted: no` 仅代表邮件头规则未命中，不能否决独立的五分钟规则；其他扩展头值亦同。两条均命中时记录 HEADER。
- Applies to：新信检测、历史修正。
- Violation consequence：产生用户未要求的文字判断或漏掉快速回复。
- 来源：2026-10-20 用户明确修订。

### Invariant I-2：五分钟关联与时间
- Rule：取同一专家（包括已有绑定别名）、同一逻辑收发账号、来信到达之前最近一封 `OUTBOUND/SENT/sent_at非空` 记录。`0 <= receivedAt - sentAt <= 300秒` 命中；仅用于已确认的首封回复，不要求 In-Reply-To，不把其他专家/账号或失败发信作为起点。
- Applies to：detector、历史修正脚本、UID 回补。
- Violation consequence：跨人误关联，或迟轮询造成错误时间差。
- 来源：用户窗口要求；关联约束为本方案实施约定。
- 使用 IMAP 实际 receivedDate 与持久化 sent_at，统一服务端时区；禁止用轮询时间、created_at、按分钟取整或截断秒数。首封资格成立但缺实际时间/有效发信记录时仅尝试 HEADER；未关联专家/唯一账号不明确时完整走原流程，两规则均不执行。300.001秒不属于窗口。

### Invariant I-3：复用状态，限定写入
- Rule：只将本次新创建、具备 I-7 首封资格、仍为 MANUAL_REVIEW 且命中的 processing 变为 `PROCESSED`；`reason_type=AUTO_RESPONSE`，`process_reason=AUTO_RESPONSE_HEADER` 或 `AUTO_RESPONSE_FAST_5M`。resolved_by/resolved_at 保持 null，不冒充 MANUAL_RESOLVED。更新 updated_at，其余字段不变。
- Applies to：收信事务末尾的新归类写入、历史修正。
- Violation consequence：覆盖原本已处理/已自动回复原因，或破坏人工取消处理守卫。
- 来源：K-inbound-processing-write-paths；original。

### Invariant I-4：业务流程保持，计数结果同步
- Rule：在原匹配路径中、本封入站写入之前取得 I-7 首封资格快照，原 processSingleCore 继续执行，再于同一事务末尾调整待处理归类。不得新增提前 return 绕过晋级、发送、附件、意图、QA 标签或通知。仅对确实调整了状态的新信返回 `AUTO_RESPONSE_PENDING_EXCLUDED`（不属于 MANUAL_REVIEW_OUTCOMES）；保留 result 的 recorded、专家/邮件 id、发送结果与其他字段，仅更新 outcome/reason。
- Applies to：processSingle、批量计数及全部邮件 pending 读取。
- Violation consequence：擅自扩大业务变更，或队列不计数而批量结果仍显示转人工。
- 来源：K-process-single-all-callers；本轮范围收窄。

### Invariant I-5：只处理新行，确认语义不变
- Rule：物理重复/历史重复返回时不归类、不回填旧行；自检、退信、DMARC 原路径不变。归类与原业务写入同事务，失败回滚，不 markSeen/推进游标；仅提交后确认。BODY_TRUNCATED、LEGACY_UID_UNVERIFIABLE、RECIPIENT_UNRESOLVED、CONTACT_NOT_FOUND、INTRODUCTION_NOT_SENT 原分支全部绕过新判断，原状态/原因不变；原内容重复结果亦不做新归类。
- Applies to：正常轮询、检查回复、UID 回补、队列/定时收信。
- Violation consequence：重放覆盖人工操作、漏信或跨共享邮箱归属错乱。
- 来源：K-process-single-all-callers、K-imap-source-vs-business-account。

### Invariant I-6：历史修正可审计、幂等、可回滚
- Rule：仅修改核准清单内仍为 MANUAL_REVIEW 且首封资格/身份/旧值/更新时间/正文哈希/对应发信快照未变化的行；只改 I-3 字段。默认 dry-run，apply 重复执行零更新；rollback 仅恢复仍与本次写后快照一致的行，不覆盖后续人工操作。
- Applies to：修正脚本。
- Violation consequence：误改或覆盖并发处理。
- 来源：original。

### Invariant I-7：首封资格先于两个判断
- Rule：首封范围为原系统的 `expert_contact.id`，跨该专家全部逻辑账号与已绑定邮箱别名；不因换账号、再次外联、人工处理或首封被自动排除而重置。新收信在写入当前邮件之前，历史 `inbound_mail_processing` 中无该专家行，且 `mail_record` 中无该专家 INBOUND 行，才有首封资格；历史状态为 PROCESSED 也算已有回复。不是“第一封待处理”，更不是“本次发信后的第一封”。
- Applies to：原匹配后的正常收信路径、历史修正、单账号/全账号/UID入口。
- Violation consequence：第二封真人快速回复被误排除，或首封自身落库后总被判成非首封。
- 来源：用户“只有首封”要求；首封范围及落库口径为本方案明确实现约定；K-first-reply-at-is-promotion-snapshot。
- 首封以系统首次成功记录的回复为准，不追溯重排延迟导入/回补的旧邮件；已有历史则回补也不是新首封。相同专家并发来信按数据库事务锁串行处理，只能第一个成功落库者获取资格；不承诺尚未收取邮件的到达顺序。
- 在既有 contact 行上 `SELECT ... FOR UPDATE`，历史存在性使用当前锁定读，禁止用旧事务快照或进程内锁替代。失败回滚释放资格，无新增持久化标志。
- 未关联专家/账号归属不明确无法取得首封资格，完整保留原待匹配逻辑；不通过本规则自动绑定或删除未匹配邮件。
- `expert_contact.first_reply_at` 是晋级快照，不能用于判断；双表读取补足人工绑定来信可能只有 processing、没有 INBOUND mail_record 的情况。

## 现状审计

### 线上证据

2026-10-20，通过 `ssh root@150.158.92.103` 在数据库只读事务内查得：

| 项目 | 结果 |
|---|---|
| 专家 | Hamid Rezatofighi；contact id `10129` |
| 邮箱 | `hamid.rezatofighi@monash.edu` |
| 来信 | processing id `806`；2026-10-20 19:07:48 |
| 标题 | `out of office Re: Research collaboration with companies in China: Advanced Neural Network Applications` |
| 正文 | 说明 OSP leave、旅行、annual leave，返岗后再回复，并列出紧急事务替代联系人 |
| 错误分类 | `MANUAL_REVIEW / GLOBAL_AUTO_REPLY_DISABLED / GLOBAL_AUTO_REPLY_DISABLED` |
| 业务/物理账号 | `WuWei_QF / WuWei_WB` |
| UIDVALIDITY / UID | `1782117036 / 851` |
| 邮件记录 | OUTBOUND `11914`，19:07:29；INBOUND `11925`，19:07:48 |
| 当前专家状态 | `INTRO_SENT`，`needs_manual_attention=0` |

首封发出约 19 秒收到该信，时间差符合五分钟规则；实施前还须复核 #806 的首封资格、#11914 的 SENT 状态和账号一致性。标题、正文只保留为调查记录，不再作为分类依据。数据库没有保存原始自动回复头，本轮未读取 IMAP 原始头，不声称已确认其 `Auto-Submitted`。

以下为修订前的调查检索，仅留作证据，不作为新规则的候选筛选或更新条件：

```sql
SELECT process_status, process_reason, COUNT(*) AS n
FROM inbound_mail_processing
WHERE LOWER(subject) REGEXP 'out of office|automatic reply|auto.?reply|autoreply'
GROUP BY process_status, process_reason;
```

返回：`MANUAL_REVIEW/GLOBAL_AUTO_REPLY_DISABLED = 1`；`PROCESSED/MANUAL_RESOLVED = 16`。这些是标题候选数量，不代表全部机器邮件或每封均已确认。对待处理正文补查 `out of (the )?office|automatic reply|automated (reply|response)|auto.?reply|annual leave|on leave`，本次仅返回 #806。16 封已人工处理的候选不在本次回填范围。

### inbound_mail_processing

- 实际线上 `SHOW CREATE TABLE`：`process_status VARCHAR(32)`、`process_reason VARCHAR(64)`、`reason_type VARCHAR(32)`，并非数据库 enum；body/cleaned_body 为 TEXT。
- 主键 id；专家外键；逻辑 `(sender_account_code,uid_validity,imap_uid)` 与物理 `(mailbox_owner_code,uid_validity,imap_uid)` 唯一键并存；owner 历史可为空。
- 本地 schema 依据：V5、V10、V14、V15、V120、V134；实体 `mail/domain/InboundMailProcessing.kt`。
- 写路径：`AutoMailReplyService.confirmManualReviewWithBody:1328`、`confirmProcessed:1384`；`UnmatchedInboundMailService.bindToContact:193`、`markResolved:238`；`PendingMailOperationService.markResolved:1558`、`:1629 → InboundMailProcessingRepository.reopenManualResolved:50`；历史迁移 V14/V15。
- `confirmProcessed` 会自动打 QA 标签、桥接附件；正式版保留这些调用，不修改两个既有落库 helper。仅在共同入口同一事务的处理末尾调整本封新记录的待处理状态。
- `reopenManualResolved` 同时限定 PROCESSED、process_reason、reason_type 均符合人工处理；AUTO_RESPONSE 不可从该接口自动恢复。`bindToContact` 只要求 contact 为空，属于显式人工改归属，保留其现有行为。
- 读路径：`MailboxConversationRepository` 聚合/时间线/挂起数量、`MailboxSuspensionService` 角标及 pendingCount、`InboundMailProcessingRepository` 人工队列/未关联队列/汇总、`MailboxService` 邮件展示、`UnmatchedInboundMailService` 详情、`PendingMailOperationService` 操作、`AutoReplyPreviewService` 预览、`MailMonitoringService` 统计；附件、材料、草稿、通知、RAG/工作台、联系人清理也读取 processing。
- 完整检索凭据：[read-paths.txt](./auto-response-evidence/read-paths.txt)、[write-paths.txt](./auto-response-evidence/write-paths.txt)。前者逐项保留路径/行号，区分读取与 schema/写入引用；实施前重跑，不以历史行号替代核对。
- 交互点 X1：收信写状态 → 会话列表/导航角标/挂起计数。
- 交互点 X2：处理原因 → 手动取消处理守卫。预览不在本次变更范围。
- 交互点 X3：绑定/人工处理/取消处理 → 再次查询人工队列。

### mail_record 与旁路副作用

- `MailRecord.kt`：非空 contact id、direction/mailType；INBOUND 接收时间与 OUTBOUND 发送时间分离。沿用现有 INBOUND 保存，不新增字段或改变 `source_inbound_id` 语义（来源：K-mail-record-source-inbound-id）。
- 写路径 grep：`AutoMailReplyService:439/780/1067/1266`、`ManualReplySendAttemptService:437/538`、`ManualExpertMailService:134`、`ManualOutreachTxHelper:69/127`、`MeetingScheduleService:145`。现有 mail_record 写者及调用全部保留，本轮新增读取成功发信时间作为分类依据（来源：K-mail-record-save-sites）。
- 读取：MailRecordRepository 的邮件历史、去重、外发归属、监控聚合；会话 union 的 OUTBOUND 分支；联系人/会议/预览/人工回复查询。保留 INBOUND 因而既有收件/已回复统计不在此变更。
- `AutoMailReplyService:287` 全局关闭分支在任何自动回执识别前落邮件、调用 `applyPromotionAndStatus`，再写 MANUAL_REVIEW；`InboundIntentClassifier` 无自动回执分类。
- `ReceivedMail` 及 `ImapMailReceiveService.fetchEnvelopeHeaders` 没有 Auto-Submitted 字段。
- `MailRecordRepository:44` 已有 `findAllByExpertContactIdOrderByCreatedAtAsc`；本计划不修改该仓库；新 detector 用参数化只读 SQL 在首封锁内按 I-2 读取发送时间。`:886` 的 `findLatestSentOutboundAnchor` 不带收信时间上界，且回退 created_at，不能直接用来计算历史五分钟窗口。
- `ImapMailReceiveService:339–343` 使用 `message.receivedDate` 转服务端本地时间，缺失时回退 now；新规则必须携带“原始收到时间是否存在”的内存标记，禁止把该 fallback 当真实到达时间。
- 时间规则新读取边界 X6：各 OUTBOUND 写者的 `sent_at/send_status/sender_account_code/expert_contact_id` → detector → processing 状态；UID 回补也必须按历史收到时刻挑发信，不能取当前最新一封。
- `processSingle:162` 左右的群提醒只排除重复结果；本次不增加通知过滤，原有通知开关、入队条件保持。`ExpertInboundNotificationService:73` 起的入队读取不以 process_status 为条件。
- 交互点 X4：入站共同入口 → 物理判重/事务/确认/通知。
- 交互点 X5：单信返回结果 → 批量 `manualReview` 汇总（AutoMailReplyService:947），新排除结果不属于 MANUAL_REVIEW_OUTCOMES；其他发送/记录数量仍取原结果字段。

### 首封读取、并发与兼容性补充审计

- `ExpertContact.kt`、V1:79–94：主键 id、专家与 campaign 关联；新增逻辑仅锁定该已有行，零 UPDATE，无 schema 变更。
- `AutomaticApplicationPromotionService:69/87`：firstReplyAt 依赖晋级写入；不能代表收信历史（来源：K-first-reply-at-is-promotion-snapshot）。
- `InboundMailProcessingRepository:84` 可读专家全部 processing；`UnmatchedInboundMailService.bindToContact:193` 可让已存在来信关联专家但不创建 INBOUND mail_record。首封检查必须覆盖两表，不能只查一个计数。
- `AutoMailReplyService:156/194` 是 processSingleCore 调用/定义；`:229–283` 已有收件路由、contact、安全兜底与首发检查。新资格计算放在这些原路径明确的边界内。
- `MailboxSuspensionService:247–250` 已有 contact `FOR UPDATE` 模式；新判定沿用数据库行锁，不另建表。为避免两事务先插带 contact 外键的 processing 再升级锁产生死锁，锁必须在该 contact 的任何本信写入之前取得。
- 交互点 X7：原 contact 匹配/人工绑定历史 → 入站前首封资格 → 两条件判断 → 本次 processing 状态；并发后第二个事务必须看见第一封已提交记录。
- 此处只是收信读取＋锁；不新增 expert_contact、mail_record 写者。现存写者见上方审计与检索凭据。
- 本轮检索命令及输出：[first-reply-v3-audit.txt](./auto-response-evidence/first-reply-v3-audit.txt)；方案结构检查：[formal-plan-check.json](./auto-response-evidence/formal-plan-check.json)。后者仅证明文档结构与清单，不代表实现通过测试。

## 实现方案

### T1：补充检测输入（I-1/I-2）

修改 `MailReceiveService.kt`、`ImapMailReceiveService.kt`：

- ReceivedMail 增加内存字段 `autoSubmitted: String? = null`、`receivedAtReliable: Boolean = false`；不加数据库字段。
- 顶层 Auto-Submitted 去参数、大小写规范化后只认合法单值 auto-replied；重复/冲突头不当成单值命中，不影响五分钟规则。
- 同一次读取 receivedDate 后用于时间转换与 reliable 判断；缺失时原 receivedAt fallback 可保留兼容，但 reliable=false，不能用于五分钟判定。常规拉取与 fetchByUids 共用转换。

### T2：首封门禁及规则组件（I-1/I-2/I-7）

新增 `InboundAutoResponseDetector.kt` 为 Spring 组件，构造注入 `NamedParameterJdbcTemplate`；仅提供参数化读取/锁和纯规则判断，不 save、不发信。不改已有 Repository 文件，避免扩大行号守卫变更。

固定方法边界（名称可直接实现）：

- `lockContact(contactId: Long): Unit`：在调用方现有事务中锁 contact 行；不新开事务，不修改该行；无行按现有联系人错误处理。
- `evaluateFirstReply(contactId: Long, accountCode: String, received: ReceivedMail): String?`：先检查首封，非首封立即 null，**不解析 autoSubmitted、不查询五分钟锚点**。首封才调用下面两条规则，返回 AUTO_RESPONSE_HEADER / AUTO_RESPONSE_FAST_5M 或 null。
- 纯 `classify(autoSubmitted, receivedAt, receivedAtReliable, sentAt)`：方便单测边界；不接收正文/标题。

首封存在性 SQL（在 contact 锁内，均为当前读；任意查到一行即非首封）：

```sql
SELECT id FROM expert_contact WHERE id = :contactId FOR UPDATE;
SELECT id FROM inbound_mail_processing
 WHERE expert_contact_id = :contactId ORDER BY id LIMIT 1 FOR UPDATE;
SELECT id FROM mail_record
 WHERE expert_contact_id = :contactId AND direction = 'INBOUND'
 ORDER BY id LIMIT 1 FOR UPDATE;
```

- 第一条由 lockContact 执行一次；后两条仅 evaluateFirstReply 执行。已有回复不再计算新规则。严禁把本封先 save 后再查询，也不能用 `process_status='MANUAL_REVIEW'` 限定历史。
- 两表均空才进入 HEADER OR FAST_REPLY_5M；HEADER 命中无需查发送锚点。五分钟的发送锚点只读：

```sql
SELECT sent_at FROM mail_record
 WHERE expert_contact_id = :contactId AND sender_account_code = :accountCode
   AND direction = 'OUTBOUND' AND send_status = 'SENT'
   AND sent_at IS NOT NULL AND sent_at <= :receivedAt
 ORDER BY sent_at DESC, id DESC LIMIT 1 FOR UPDATE;
```

- 按完整 Duration 比较，0/300秒包含，300.001秒不含。锁定读避免 repeatable-read 下取得在锁等待之前冻结的旧历史；不依赖 createdAt。
- `TransactionSynchronizationManager.isActualTransactionActive()` 断言存在真实调用方事务，避免误用导致锁立即释放。保持同一 DataSource/transaction manager。
- 采用 contact id 串行会使同一专家并发收信等待既有业务事务完成（该事务当前包含 SMTP）；不同专家不共用同一 contact 行锁，仍须检查 SQL 执行计划及 InnoDB 范围锁。验证锁等待/死锁重试保持原失败不确认语义，不为本需求重构 SMTP 事务。

### T3：原路径保留，首封结果传到事务末尾（I-3～I-5/I-7）

仅修改 `AutoMailReplyService.kt`，构造器尾部注入新 detector；同步该服务测试构造。原两个落库 helper、意图分类器、匹配服务、预览服务不改。

1. `processSingle` 的每次 transactionTemplate.execute 内新建一个局部资格/原因上下文（只能是本调用局部变量或对象，不用服务字段/ThreadLocal）。将回调 `onEligibleReply(contactId, accountCode)` 传给 private processSingleCore；回调调用 evaluateFirstReply 并记录 nullable reason。
2. processSingleCore 沿用物理/历史重复处理、收件账号路由、专家邮箱/别名查找。找到 contact 后，在 BODY_TRUNCATED/legacy/contact相关写入之前调用 lockContact；只加锁、不改变分支结果。未匹配与 unresolved 路径不取专家锁、不计算新规则。
3. BODY_TRUNCATED、LEGACY_UID_UNVERIFIABLE、CONTACT_NOT_FOUND、INTRODUCTION_NOT_SENT 按原顺序原样返回；通过既有 hasIntroductionInquiry 后、全局自动回复开关分支之前调用 onEligibleReply。此时本封尚未写 processing/mail_record，资格快照不会把自己算成历史。
4. 原业务流程继续，资格命中也不提前 return；不阻断晋级/附件/意图/QA/发送/通知。原逻辑在后来判内容重复的结果仍不归类。
5. core 返回后，仅本地 reason 非空、结果非重复时按 owner+uidValidity+uid 回读本封新 processing；只有确实新建且仍 MANUAL_REVIEW 才 save(copy(...))，仅改 I-3 字段。原已 PROCESSED、未落行或资格未命中均原样返回。
6. 确实调整状态才返回 result.copy(outcome=AUTO_RESPONSE_PENDING_EXCLUDED, reason=命中原因)，保留所有其他字段；新 outcome 不在 MANUAL_REVIEW_OUTCOMES。原通知条件和调用不增加排除，新结果继续其原通知行为。
7. 保持同事务提交与外层 markSeen/游标确认；第一封提交后，processing 即使已 PROCESSED 也算历史，第二封完全不进入两条件判断。手动处理/取消/绑定路径不调用新 detector。

执行伪码：

```text
原路由与专家匹配 → 匹配不到：原待匹配流程
已匹配 → 锁专家行 → 原安全/首发检查（未通过：原结果）
  → 尚无回复历史？
      否：不执行两个新判断
      是：邮件头 auto-replied OR 0～300秒 → 保存本调用原因
  → 原业务处理继续
  → 首封命中且本封原状态 MANUAL_REVIEW？改为 PROCESSED：保持原结果
  → 原通知、提交、IMAP确认
```

### T4：仅首封历史待处理修正（I-2/I-3/I-6/I-7）

新增 `scripts/reclassify-auto-responses.py`：

- scan/dry-run 只读已关联专家且原安全/首发条件满足的 MANUAL_REVIEW；先证明候选为该专家首封，后按 I-2 时间口径输出候选。未匹配与后续回复从候选中排除。候选包含 processing id、匹配 outbound id、收发时间、差值、旧字段、更新时间和正文 SHA-256。
- 历史无 Auto-Submitted 存储，不从标题或正文猜测 HEADER；若后续提供已从原邮箱核实的原始头证据才允许按 HEADER 核准。已知 received_at 来源存疑的行先核实原邮箱实际时间，不按 now 猜测。
- 历史首封证明：候选必须是该专家最早创建的 processing（created_at、id 排序）；关联同封 mail_record 用同专家/账号/非空 Message-ID，并核对收到时间，不能用两表 id 相等。同封 INBOUND 不算第二封；早于候选的独立 INBOUND 或同时间无法排除歧义的历史则不自动修正，输出“首封无法确认”。已有后续回复不妨碍修正已证明的首封，但绝不连带修改后续邮件。
- 以核准清单 apply，事务内先锁 contact，再当前读重查首封证明、候选与匹配发信快照；条件 UPDATE 只改 I-3 字段。处理期间数据变化则跳过并报告；不全表 UPDATE、不改已人工处理行。
- 本次 Hamid #806 收发间隔约 19 秒；apply 前复核 #806 首封证明、#11914 为 SENT 且账号一致。其他目标需按新规则扫描，现阶段不承诺全库候选数量。
- 保存每行 before/after 与行数；rollback 按写后快照条件恢复。脚本连接凭据不写入日志。保留邮件历史、挂起/跟进、专家状态、ES 和工单。
- 上线新归类逻辑后再执行历史修正，避免新的待处理继续积压。本轮仅形成方案，不执行。

脚本接口固定为以下三种显式模式；默认无 mode 等同 dry-run，不存在隐式 apply：

```text
python3 scripts/reclassify-auto-responses.py scan --output <候选清单.json>
python3 scripts/reclassify-auto-responses.py apply --manifest <核准清单.json> --audit-output <写后审计.json>
python3 scripts/reclassify-auto-responses.py rollback --audit <写后审计.json> --output <回滚结果.json>
```

清单包含 `schemaVersion=1`、`ruleVersion=FIRST_REPLY_V3`、`generatedAt`、`rows`；每行必须有 processing/contact/outbound id、逻辑/物理账号、UIDVALIDITY/UID、Message-ID、收发时间、delta、首封证明、正文哈希、旧状态/原因/updated_at、核准原因。apply 在加锁后重新验证，不能只相信文件内 firstReply=true。审计包含 before/after 与 affected/skipped/conflict 数量，不包含密码、完整正文或邮件头中的其他隐私字段。

### T5：验证（I-1～I-7）

- AutoMailReplyServiceTest：首封 HEADER/五分钟/未命中；第二封即使双命中也不调用新规则；未匹配、原兜底、原业务调用保持、批量 manualReview、UID 回补/重复/回滚。既有测试默认 detector 返回 null，保持旧行为；新用例显式控制首封判断。
- ImapMailReceiveServiceTest：原始头、真实 receivedDate、缺失时间 fallback、既有 MIME/共享邮箱转换回归。
- 新增 InboundAutoResponseDetectorTest：纯分类边界与非首封短路验证；0/19/299/300/300.001/301秒、负差、no/auto-generated、跨账号、失败发信、空时间、迟轮询。首封范围跨账号，时间锚点不跨账号。
- MailboxConversationRepositoryIT、MailboxSuspensionServiceIT：MANUAL_REVIEW→PROCESSED 前后，列表、pendingOnly、挂起横幅数、导航 badge 的计数一致；未关联来信保持 MANUAL_REVIEW 也覆盖。
- 在 MailboxConversationRepositoryIT 中注入新 detector，加两事务并发首封用例：锁相同 contact，第一事务写 processing 后提交，第二事务必须判非首封；第一事务回滚则第二事务可获得资格。另覆盖只有 INBOUND/只有已绑定 processing 的旧历史均非首封。
- 脚本在隔离测试库跑 dry-run/apply/重复 apply/rollback/并发冲突，固定边界数据与 detector 对照。

执行顺序：

- [ ] E1：在指定工作区核对当前 diff 与本清单，保留其他改动；补首封/非首封/未匹配失败用例，确认失败因缺失规则而非环境。
- [ ] E2：实现 T1/T2，跑 ImapMailReceiveServiceTest、InboundAutoResponseDetectorTest。
- [ ] E3：实现 T3，跑 AutoMailReplyServiceTest；确认后续邮件/原待匹配用例保持。
- [ ] E4：补 T5 中两份 MySQL IT，验证真实锁、回滚、列表与角标。
- [ ] E5：实现 T4，隔离库核验 scan/apply/重复/rollback；再跑下方全量门禁。
- [ ] E6：完成配套文案计划及 JS 验证；交付实现差异、测试结果、历史 dry-run 清单；按用户后续部署授权执行上线，未上线不标“已生效”。

## 变更文件清单

以下路径相对于项目根；共 10 个实施文件。单后端子系统加定向运维修正，不触及前端、数据库 schema 或已有 SQL 仓库代码（新组件包含参数化只读/锁 SQL）。

| # | 文件 | 内容 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReceiveService.kt` | 内存头字段、时间可靠性 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt` | 填充原始头与时间 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundAutoResponseDetector.kt` | 新增首封锁/当前读及两规则组件 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 事务末尾归类、批量计数 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 入口/副作用保持回归 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt` | 收信元数据回归 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundAutoResponseDetectorTest.kt` | 新增规则边界测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt` | 会话计数回归 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt` | 挂起/导航计数回归 |
| 10 | `scripts/reclassify-auto-responses.py` | 历史扫描/修正/回滚 |

配套文案计划另有 5 个明确文件。不修改 AutoReplyPreviewService、匹配服务和已有 Repository；锁/首封读取集中在新组件，配套回归放既有测试文件。总范围仍为 15 个文件。

## 验收标准

- I-1：仅首封执行两条 OR；auto-replied 即使超过五分钟仍命中；no/auto-generated 在窗口内也命中。主题/正文不同而头和时间相同，结果完全相同。
- I-2：0/300秒包含，300.001/301秒排除；负差、空时间、其他专家/账号、失败发信不命中；晚轮询、回补时的新增 OUTBOUND 不改变历史窗口。
- I-3：仅首封资格成立的新 MANUAL_REVIEW 被调为 PROCESSED/AUTO_RESPONSE；resolved_* 不伪造人工；已人工处理、QA 自动回复的旧原因不变。取消人工处理接口不接受 AUTO_RESPONSE 原因。
- I-4：同一业务输入仅改变分类规则是否命中时，晋级、状态、附件、SMTP、群消息调用与原流程一致；允许变化仅为 processing 指定字段及结果 outcome/reason、manualReview 计数。列表/角标/挂起待处理同时减少。
- I-5：重复 UID 零归类更新；单信失败不确认，重试不新增第二条邮件；共享物理/逻辑账号、附件来源、游标仍与基线一致。截断正文只影响原业务处理，不加入新分类规则。
- I-6：dry-run 零写；首次 apply 只改核准行，重复为 0；并发人工处理后跳过；rollback 只恢复未被后续修改的写后快照。
- I-7：历史任意状态都使新邮件非首封；首封已排除后第二封带 HEADER 且时间在5分钟内仍走原逻辑；换账号/别名不重置；已有手动绑定 processing 或历史 INBOUND 均阻止首封资格；两事务并发仅一封取得资格，回滚不消耗资格；未匹配/安全兜底不执行两判断。
- X1/X5：新邮件状态与批量处理汇总一致；X2/X3：人工操作与重新读取保持；X4：提交/确认/通知保持；X6：所有外发来源沿用现有 SENT/sent_at 快照计算；X7：资格在本封入站写入前读取，跨事务读取看见已提交首封。

实施后定向检查，再跑规定 JDK11 全量测试：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT
```

第二条仅连接隔离本地测试库，明确核对数据源；仓库 IT 会清理测试数据，禁止连接生产。未具备 MySQL 条件时记录未执行，不能把默认跳过算通过。本轮仅文档修订，未运行实现测试。

## 人工验收清单

### A-1：邮件头独立生效
- 前置条件：测试邮箱全局自动回复关闭，已有成功发信且无任何历史回复；准备第一封超过五分钟、带 auto-replied 头的来信。
- 操作步骤：1. 检查回复；2. 刷新全部邮件/待处理/角标；3. 查看原文。
- 预期结果：邮件原文保留，状态已处理；待处理增量 0；系统记录原因 AUTO_RESPONSE_HEADER。原开关仍关闭。
- 覆盖：I-1/I-3、X1/X5。

### A-2：首封窗口边界
- 前置条件：两个测试专家各仅一封成功发信、均无回复历史；全局自动回复关闭。
- 操作步骤：1. 为专家甲构造发信后300秒收到首封无auto-replied回复；2. 为乙构造301秒首封；3. 延迟到十分钟后检查两封。
- 预期结果：甲首封已处理，乙首封 MANUAL_REVIEW；总待处理新增1；迟检查不改变实际收发时间差。
- 覆盖：I-1/I-2/I-7、X1/X6/X7。

### A-3：Hamid 历史修正与回滚
- 前置条件：隔离测试库导入 #806/#11914 及原挂起原因、跟进标记；核对首封证明、SENT 与账号，清单仅核准 #806。
- 操作步骤：1. dry-run；2. apply；3. 刷新全部/待处理/已挂起/导航；4. 重复 apply；5. rollback。
- 预期结果：dry-run 0 写；首次 apply 1 行；对应会话待处理 1→0，原文、挂起原因与跟进标记保留；重复 0 行；无后续修改时回滚恢复 1。导航仅按其原有“排除本人挂起”的规则计数，不要求已挂起邮件再减一次。
- 覆盖：I-3/I-4/I-6、X1。

### A-4：原业务行为及人工处理保持
- 前置条件：隔离环境保留一份未修改版本的基线；分别准备未关联且带auto-replied的来信、已关联非首封来信、5分钟内附附件的首封来信，设置相同自动回复/群提醒开关，使用测试 SMTP 与测试群接收端。
- 操作步骤：1. 两版本跑同一夹具，比较专家状态/层级、附件、发送、群通知；2. 将未命中未关联来信绑定专家；3. 标已处理后取消；4. 预览普通来信。
- 预期结果：未关联带自动回复头仍进入原待匹配队列；除符合首封条件的新分类状态/原因及待处理计数外，业务效果一致；绑定后待处理为1，处理/取消为0→1；预览与基线一致；不新增群提醒屏蔽或附件漏存。
- 覆盖：I-3/I-4、X2/X3/X4；全部 must-NOT-change 业务项。

### A-5：共享账号、重复与时间缺失
- 前置条件：物理 owner=`WuWei_WB`、逻辑=`WuWei_QF` 的测试邮箱；为两个无历史回复的专家各准备首封：一封时间命中、一封缺真实到达时间且无 auto-replied 头。
- 操作步骤：1. 正常收信；2. 对同 UID 回补；3. 导入缺时间场景；4. 制造归类落库失败后重试。
- 预期结果：成功信只有1条 processing；逻辑/物理账号保持；重复不覆盖旧状态；缺时间信仍按原流程，不能凭轮询时间命中；失败不确认，成功重试后才推进游标。
- 覆盖：I-2/I-5、X4/X6。

### A-6：历史修正并发保护
- 前置条件：测试库 dry-run 产生核准清单，包含两个专家各自一封已证明为首封的时间命中待处理信。
- 操作步骤：1. 对其中一封人工标已处理；2. apply 旧清单；3. 再对已修正行执行显式人工操作；4. rollback。
- 预期结果：apply 跳过人工已处理行，只改另一行；rollback 不覆盖后续人工变化并报告冲突；专家、工单、ES、挂起备注不被脚本改写。
- 覆盖：I-6、X2/X3。

### A-7：第二封绝不重判
- 前置条件：专家首封已按规则排除，全局自动回复关闭；原挂起状态与跟进标记存在。
- 操作步骤：1. 我方再发一封；2. 30秒后收到该专家第二封，且带auto-replied；3. 检查回复；4. 换至同专家已绑定邮箱别名再回复。
- 预期结果：第二封和后续别名回复按原流程进入 MANUAL_REVIEW，不因邮件头或30秒而排除；首封仍为已处理；挂起原因、跟进标记不变。
- 覆盖：I-1/I-4/I-7、X7；首封之外原逻辑回归。

### A-8：并发与已有历史
- 前置条件：隔离测试库两条数据库连接、无历史回复的同一contact；另备一个仅有人工绑定processing历史、一个仅有INBOUND历史的contact。
- 操作步骤：1. 两连接按本计划SQL竞争同一专家锁；2. 第一连接写入首封并提交；3. 第二连接执行首封查询；4. 重做第一连接回滚场景；5. 对两个历史contact各收快速回复。
- 预期结果：提交场景仅第一封有资格，回滚场景第二连接有资格；已有任意一种历史的contact均无首封资格；没有把旧快照读成“尚未回复”。
- 覆盖：I-5/I-7、X4/X7。

## 修正记录

- V1：提前拦截并阻断晋级/群提醒/材料方案已撤销。
- V2：仅调整待处理，但对所有邮件都检查两个条件；该适用范围已撤销。
- V3：**仅原流程已匹配专家且确认首封的回复才进入两个新判断**；第二封以后、未匹配及原安全兜底均原样兼容。增加首封历史读取、并发串行、回补/历史修正边界和对应验收，不扩大业务副作用。
- 用户“待匹配改待确认”显示请求仍保留，接口和队列成员规则不改变。

## 自检

- I-1～I-7覆盖两个条件、首封范围、空值/时间/并发/重试与新写路径；X1～X7都有机器和人工验证。
- 后端10文件、前端5文件，分别执行；无schema变更，无匹配服务/状态机/通知策略扩改。
- UI样式契约及人工验收见配套计划；本计划不改前端。
- 本轮只完成可执行方案与结构检查，未改业务代码、未运行实现测试、未上线。
