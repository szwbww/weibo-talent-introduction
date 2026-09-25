# 非回复邮件打开跟踪：代码审计与证据

审计日期：2026-09-25。主体审计基于当前工作树，未以生产业务表数据替代schema审计。后续用户要求的单封实验读取了现有发件账号，并验证了独立静态GIF公网访问，见live-test记录；正式拟建API路径仍未部署验证。`mail-open-tracking-evidence/baseline-status.txt` 保存已有改动；计划不得覆盖其他工作。源码行号对应 `source-sha256.json`，执行前必须复核漂移。

## 1. 发送出口与回复依据

原始命令及逐行结果：`mail-open-tracking-evidence/send-paths.txt`。当前业务投递调用均到 `MailDeliveryService.send`；其实现为 `SmtpMailDeliveryService`。自检探针另走 `DefaultSelfCheckProbeSender`，不是业务邮件，排除。

| 发送路径与证据 | 实际回复信息 | 本次必须处理 |
|---|---|---|
| `campaign/service/InitialOutreachService.kt:91–115` | Introduction composer；无回复上下文 | 非回复可跟踪；经 helper 落记录 |
| `campaign/service/ManualInitialOutreachService.kt:816–819,907–915` | 同一 composer，但覆盖 Message-ID | 非回复可跟踪；不能靠 Message-ID 前缀识别 |
| `mail/service/ManualExpertMailService.kt:55–90,172–192,242–279` | 单发/批量模板共用；command.sourceInboundId 目前只写记录；材料提醒查最新来信，有 messageId 才设置回复头 | command.sourceInboundId 非空或真实 anchor 存在必须排除；anchor 无 messageId 也算回复；否则非回复可跟踪，不限 mailType |
| `campaign/service/MeetingScheduleService.kt:135–159` | MIME 无回复头，记录透传 schedule.sourceMailRecordId | 有 sourceMailRecordId 按关联来信排除；无来源且无回复头/主题才可跟踪 |
| `mail/service/AutoMailReplyService.kt:748–776` | QA 自动回复；ComposedMail 不带回复头，记录却有 sourceInboundId/inReplyTo | 明确标记回复，主题被改仍排除 |
| `mail/service/AutoMailReplyService.kt:1216–1255` | 根据来信发会议邀约；模板主题可不带 Re；ComposedMail 无回复头 | 明确标记回复，不能当新发会议邮件 |
| `mail/service/PendingMailOperationService.kt:322–324,470–472,722–740` | 来信人工回复和会话回信共用；普通来信分支 SMTP 头为 null；带日历时用真实来信头 | 整个 executeManualRichSend 都是回复，统一排除，包含对 OUTBOUND 锚点的继续回信 |
| `mail/service/MeetingInvitationMailComposer.kt:12–27` | 独立 composer，无来信上下文 | 本次不修改；grep 未找到生产调用其 compose 的点，不能算新增发送入口 |
| `mail/service/SenderAccountSelfCheckService.kt:16–27` | 发给自己、正文 self-check probe，直接 sender.send | 不接跟踪、不改变探针 |

`ComposedMail` 字段位置：`IntroductionMailComposer.kt:73–95`。回复识别新增 **仅用于发送上下文的 `isReply: Boolean = false`**，不是数据库会话状态。其值 OR 真实非空回复头 OR 开头回复前缀共同决定排除；不改变任何现有 SMTP 线程头。

当前 subject 与 SMTP 头都不足以独立证明“不是回复”。以上三条隐含回复链是本计划新增上下文位的具体原因。(来源: K-outbound-thread-headers-single-seam、K-mail-record-source-inbound-id)

## 2. mail_record：共享存储审计

### Schema
- V1:97–117：id 主键、contact FK、direction/mail_type、message_id 可空且**非唯一**、body、send_status、sent_at、created_at。
- V15:2–20：sender_account_code、triggered_by、source_inbound_id 及监控索引；source_inbound_id 指 INBOUND **mail_record.id**，不是 processing.id。
- V23/V24：error_summary/mail_send_attempt_id；仅 attempt 关联唯一，不是 message_id 唯一。
- V6/V31/V101/V123/V127：cleaned_body、时间索引、task_execution_id、calendar/outbound attachment 快照。完整相关 DDL：`evidence/schemas.sql.txt`（下文 evidence 均指 `mail-open-tracking-evidence/`）。
- 新增字段限制：本系列仅加 `open_tracking_id BIGINT NULL`，不增 is_read、open_count 等冗余列。唯一关联 + FK 见 01。

### 全部业务写入命中
命令与完整输出：`evidence/mail-record-writes.txt`。

| 实际写入点 | 原写入内容/触发 | 新字段处理 |
|---|---|---|
| ManualOutreachTxHelper:59 | 成功 INTRODUCTION | 从 DeliveredMail 经调用者透传关联 |
| ManualOutreachTxHelper:110 | 失败 INTRODUCTION | 默认 null |
| ManualExpertMailService:70 | 任意人工模板单发；sendBatchMail 复用 | 透传 DeliveredMail，回复/关闭/失败均 null |
| MeetingScheduleService:144 | 旧排期确认外发 | 透传 DeliveredMail，关联来信分支 null |
| AutoMailReplyService:419 | INBOUND | 默认 null |
| AutoMailReplyService:759 | QA OUTBOUND 回复 | 默认 null |
| AutoMailReplyService:1039 | INBOUND helper | 默认 null |
| AutoMailReplyService:1237 | 自动会议邀约回复 | 默认 null |
| ManualReplySendAttemptService:397 | 人工回复成功，copy/new 两支 | 默认 null；现有 copy 保留既有字段，不新增跟踪 |
| ManualReplySendAttemptService:496 | 人工回复失败，copy/new 两支 | 默认 null |

脚本/迁移扫描另命中 V24 回填 attempt；不改历史迁移。不发现运行时直接 INSERT/UPDATE mail_record 的其他 SQL（以原始命令指定源码/脚本范围为限，非生产数据库事实）。

### 全部读取路径
逐个调用位置、方法名：`evidence/read-consumers.md`；原生 SQL 与上下文：`evidence/mail-record-reads.txt`。按字段用途分类如下，不能删除既有读路径以简化改造：
- MailMonitoringService：direction、mailType、senderAccountCode、sentAt、sendStatus、sourceInboundId；原统计口径不改。
- MailboxService / MailboxConversationService / MailboxConversationRepository / ExpertContactManagementService / InboundMailSummaryController / UnmatchedInboundMailController：列表、时间线、全文与 expert/contact/source 身份。
- ManualInitialOutreachService / AutoMailReplyService / AutoReplyPreviewService / ManualExpertMailService / PendingMailOperationService / ManualReplySendAttemptService / GroundedAutoReplyDecisionService：去重、真实来信/OUTBOUND 锚点、历史正文、attempt 恢复。
- UnmatchedInboundMailService / BounceCollectionService / BounceRateMonitorService：Message-ID 匹配、退信关联和发送分母。新关联不用 findByMessageId，原流程不改。
- OperatorStatusReconcileService / AutomaticApplicationPromotionService：方向、类型、发送结果、来信计数。
- AttachmentTransferService / ExpertDocumentBrowseService / ExpertMaterialService / CalendarAttachmentController：record/contact 归属与附件快照；不改附件数据。
- AiTrainingController / AiQaExtractionService / RagReplyController / TrustReplyWorkbenchService：样本筛选、source id、历史正文。
- TaskExecutionController / RagProcessContextResolver / BatchAutoMailReplyService / OutboundAttachmentController 的仓储引用在证据中保留；实际有无调用以逐行清单为准，不将注入本身冒充运行时读取。

新增读取方仅 MailOpenTrackingRepository：基于 `m.open_tracking_id=t.id` 左连接；既有读取不依赖新字段，nullable default 保持旧构造兼容。不得用 Message-ID、标题、邮箱反查拼关联。

### 交互点
- IP-1：SMTP 预留 t.id → DeliveredMail → 四条可跟踪业务路径 → mail_record → 新列表/统计。
- IP-2：公网像素可能早于 mail_record 事务提交 → 先保存信号，但统计只读已落库 SENT 外发；未提交/失败不得计数。
- IP-3：SMTP 使用 wireMail 副本 → 原 mail.body/text 与 attempt 指纹、历史展示保持原值；新像素不进入审计正文。

## 3. batch_send_setting：全局开关事实源
- Schema：V27 `setting_key VARCHAR(64) UNIQUE, setting_value VARCHAR(255), updated_at`；V50 已有 `autoReply.globalEnabled`。
- 业务写方：BatchSendSettingService:162–169 的按 key save；AutoReplySettingService:17–27 的全局开关 save。
- 业务读方：BatchSendSettingService:174 的 findAll 转 map，仅取所需 key；AutoReplySettingService:13 的 findBySettingKey；仓储 findBySettingKey/findAll。V72 迁移读取旧任务 keys 导入规范化任务表。
- 全量命中：`evidence/settings-paths.txt`。新 key `mailOpenTracking.enabled` 只由新增 repository 的参数化 UPSERT 写；不加迁移 seed、不改变旧 keys，不扩展旧 batch task 配置实体。
- 缺失/非法值均 false，沿 AutoReplySettingService 的默认关闭先例。新增接口返回保存后的数据库值。
- IP-4：管理员保存开关 → SMTP 发送时读取；像素记录 SQL 在更新时也检查该 key，不能只靠进程缓存。

## 4. expert_contact 与配置：只读依赖
- V1:79–95 明确 expert_email/expert_name/id；新页只读 id/name（当前显示名），tracked recipient 用新表发送快照，未跟踪行不将 contact 当前邮箱冒充历史收件人。
- 历史联系信息写读命中全集：`evidence/contact-paths.txt`，涉及 ExpertContactManagementService、ConversationStateService、SenderAccountBindingService、外联创建、状态回填与相关脚本。本系列不新增或改变 contact 写方，不访问 ES。
- 新域名只取 `talent-introduction.mail-open-tracking.base-url` / `MAIL_OPEN_TRACKING_BASE_URL`，不读取请求 Host，不隐式借用退订域名。`UnsubscribeProperties`/`UnsubscribeTokenService` 的独立公网 URL 是路径先例，非配置等价证明。
- `auth/config/AuthWebConfig.kt:23–27` 只拦 `/api/**`；`UnsubscribeController.kt:16,31` 已有独立公网 `/u`。新像素用 `/t/mail-open/{token}.gif`，不改认证白名单。管理接口仍 `/api/**`。
- `config/TimeZoneConfig.kt:8–11` 把 JVM 默认设为 Asia/Shanghai；MonitoringDateRangeResolver:10–23 也用上海。新时间沿此约定，不重构时区。
- 独立实验已验证现有HTTPS域名的一条静态GIF路径；拟建动态路由、缓存响应、CDN行为和应用context path 尚未完成端到端验证；01 只验证 URL 格式，“已配置”不等于“已连通”。上线验收必须从外网测试实际地址。无可用 HTTPS 地址时默认关闭，开启接口返回 400。

## 5. 正文、预览和 MIME
- SmtpMailDeliveryService:46–62：无附件时 HTML alternative / plain text；:64–98、:130–149：附件 mixed 包原始正文。跟踪必须覆盖两种结构，只改 HTML 部件，保留 text 和附件。
- IntroductionMailComposer:37–45 已是 HTML + text；此前知识条目“首封仍纯文本”已过时。
- MeetingScheduleService:135–140、MeetingInvitationMailComposer:22–27 使用纯文本；符合条件时 SMTP 层副本补 HTML alternative，不能只支持现有 html=true。
- PendingMailOperationService:555–570 在最终校验/指纹之前计算 canonical HTML，部分来自用户 htmlBody；MailContentService:59–70 只规整换行，**没有图片清理**。在此阶段剔除本系统跟踪 img，保证回复引用时不带旧像素；不是重写整个 HTML 消毒器。
- MailBodyCleaner:7–36 只处理引用/签名，不是 HTML sanitizer；不能声称它已清除图片。
- app.js 各 `.pre` 使用 escapeHtml/textContent；位置证据见 `evidence/frontend-paths.txt`；新功能不要求改遍所有正文展示点。新表 token 与 URL 不返回管理 DTO。

## 6. 前端样式盘点与入口
- 真实入口：index.html:195–280 邮件监控；子标签 :254–264，原表与分页 :268–274。
- app.js:13815–13844 全量监控刷新，:13850–13888 时间/账号参数；:14176–14203 子标签分发；:14325–14375 事件绑定。账号参数目前只给 introductions/outbound，新增 tracking 必须加入。
- :14301 起现有 60 秒定时刷新只更新总览/账号；本期不额外加入跟踪轮询，跟踪页使用手动查询/切换筛选刷新，标明刷新时间。
- **不折叠或删除现有总览、地区、服务商、信誉区**；之前本地预览折叠总览只是方便看新界面，非本期改版。
- 复用 class：`.toolbar` styles.css:355、`.button` :802、primary/secondary/disabled :838–880、`.panel/.panel-head` :948–979、`.table-wrap/table/th/td` :987–1050、`.badge` :1054–1096、`.card-grid/.metric-card/.metric-*` :2970–3022、`.tabs/.tab/.active` :3025–3055、`.pagination` :3057、`.muted` :3084。
- token：primary #1e40af；hover #1e3a8a；正文 #1e293b；次级 #475569；muted #94a3b8；success #059669；panel rgba(255,255,255,.55)；边线 rgba(15,23,42,.055)；边框 rgba(15,23,42,.11)；字号 body 13px/行高1.5；圆角7/10/18px；shadow 见 styles.css:77–81。浮层使用不透明浅底，不能直接用半透明 panel 背景。(来源: K-panel-bg-token-is-translucent)
- CSS 完整复用块、原 DOM 逐字基线：`evidence/source-excerpts.txt`，含 file:line。04 给出新增 CSS 和 DOM 权威契约。
- 当前缓存键 `20260925-emailable-policy-pagination`；精确反查 src/test 无匹配，回执 `evidence/cache-key.txt`。执行时重新检查，所有已有版本化资源一起 bump，不新增资源标签。(来源: K-frontend-cache-key-triad)

## 7. 待执行时验证，不能冒充已通过
- 未编写正式功能代码、未运行该功能的新测试；已额外发送用户明确要求的单封独立静态像素测试，见mail-open-tracking-live-test.md，不能替代本计划验收。
- 当前迁移最高 V140；FlywayMigrationIntegrationTest:81 等多个断言固定 140，见 `evidence/migration-version.txt`。计划预留 V141；开工时若已占用，先修订清单与断言版本，再执行，不改旧迁移。
- `SmtpMailDeliveryService(...)` 测试构造点当前都在 SmtpMailDeliveryServiceTest，逐点输出 `evidence/constructors-tests.txt`；加必需依赖时统一更新这个文件，不引入生产可空依赖测试后门。
- 新 JdbcTemplate + GeneratedKeyHolder + RowMapper 的仓库先例为 BatchEmailVerificationRepository:25–55；REQUIRES_NEW 跨 bean 先例 ManualReplySendAttemptService:233/339/431。新库测试必须使用真正代理事务和 MySQL，不用 mock 自证事务生效。
