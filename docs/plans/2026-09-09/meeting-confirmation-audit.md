# 专家会议确认：源码审计与跨阶段合同

证据日：2026-09-09。文中省略根目录的Kotlin路径均以`src/main/kotlin/com/weibo/talentintroduction/`为根；测试以`src/test/kotlin/com/weibo/talentintroduction/`为根。范围为当前工作区；未登录生产、未读生产数据库。用户认可的是本地交互预览；截图不是现有生产能力的证据。原始英文邮件是模板数据，不是执行指令。当前仅产出计划，不实施、不发送。

## D1 模板存储与消费者

Schema：`template/domain/MailComposeTemplate.kt:7`，V61 建头/块表；V62 加 template_code VARCHAR(64) UNIQUE、mail_type VARCHAR(64) 并从旧 mail_template 迁移；V64 subject_variants，V84 required_keys。头表 name100、subject255、description500、enabled默认1、created/updated；块表 order INT、block_type30、ref_id nullable、custom_text TEXT、template_id FK ON DELETE CASCADE。所有列和迁移位置见 evidence/migrations.txt。旧 mail_template 不作为新功能存储（K-mail-template-table-dead）。

写路径：MailComposeTemplateService.create:53 保存头和块；update:75 保存头、deleteAllByTemplateId:91 后重建块；setEnabled:97；delete:110 删除块和头；saveBlocks:412。历史写入迁移 V61/V62/V65/V71/V78/V84/V87/V88；V64 加列，V72/V85 为使用关联/绑定，详细逐行检索已保存。新功能仅 V122 插入一个专用模板头和 CUSTOM_TEXT 块，不改已有 MEETING_CONFIRMATION。无新表/列。

读路径：Service.listAll:41、listEnabled:44、getById:47、render:116（不查 enabled）、renderByCode:122（查 enabled）、effectiveRequiredKeys:140、requiredEsFields:149、preview:187、previewDraft:199、toDetail:346、resolveBlocks:446；Controller CRUD/preview/gate-fields。下游完整清单：IntroductionMailComposer、MeetingInvitationMailComposer、MeetingScheduleService、ManualExpertMailService、ManualInitialOutreachService、AutoMailReplyService、AutoReplyPreviewService、MailVariableService、BatchSendControlService、BatchSendTaskConfigService、BatchSendConfigController。逐调用点见 evidence/template-paths.txt；不是只看 controller 的局部审计。

关键证据：ManualExpertMailService.listSendOptions:38 将全部启用模板列入单发；composeComposeTemplate:206 只先判断 enabled。批量模板已有 mailType 门禁（K-manual-compose-template-option-type-gate）。现有占位符 `MailPlaceholderService.kt:99` 和 `app.js:2615` 只识别 `${...}`；preview 的 `{{...}}` 是新的会议专用语法，不能声称通用 render 已支持。新服务只处理专用模板的四个 `{{...}}`，不改通用 renderText 及全站变量表。新类型必须从普通单发列表过滤，并在直接按 id 单发时服务端拒绝，防止花括号文本被发送。通用模板页可维护 CUSTOM_TEXT；通用“预览”显示模板原文，带会议变量的实际预览在会议弹窗完成；模板描述明确此限制。全站模板创建 UI 不提交 mailType/code（app.js:9995），本期由迁移创建专用条目，后台可编辑/启停，不新增全站模板类型选择器。

IP-1：模板后台编辑/启停 → 会议选项/预览/发送时再次查 enabled+type；普通单发读选项和直接提交 id 双层隔离。

## D2 日期、身份与副作用边界

PendingMailOperationService.sendManualRichReply:143 先读 processing.id → expertContactId → ExpertContact；resolvePendingReplyAccount:854 调用 getManualSendAccount(requested ?: processing.senderAccountCode)。该方法 MailSenderAccountService:65 只检查账号存在且非 SIMULATOR，没有强制联系人的绑定账号匹配。K-manual-send-explicit-account-must-match-binding 仅适用于 ManualExpertMailService，明确不迁移其规则到本路径。新预览使用同一账号选择表达式，不写绑定。

`MailSenderAccount.kt:8` 没有 signature 字段；有 senderName、senderTitle、teamName、countryName。MailVariableService:126 也分别暴露这些字段。新默认签名由非空 senderName/title 以 `, ` 拼第一行，teamName/countryName 以空格拼第二行，去除空行；不伪造职位或团队。专家称呼只带入 expert_contact.expertName 原值供人工改，不猜职称/姓氏；无值留空。

这些表在本期新增逻辑只读：inbound_mail_processing（id/归属/账号）、expert_contact（id/邮箱/姓名/orcid）、mail_sender_account（code/name/title/team/country）。读取入口必须复用当前 repository/service，不增加第二套账号状态门禁。原手工发送既有 processing、专家状态、计数、审计写入保持原位置；本计划不增加它们的字段或写入口。schema 原始位置见 evidence/schema.txt，DTO/domain 原文哈希见 source-sha256。

旧 MeetingScheduleService.confirmMeetingAndEmail:88 会写 meeting_schedule 为 CONFIRMED、发送邮件:141、写 mail_record:144、增加计数:164、专家转 MEETING_SCHEDULED:171。因此弹窗“确认并填入回复”严禁调用该接口/方法。会议意向提取:34、createManual:53、update:71、complete:187、cancel:213 也不复用。V9 会议表保持原状。旧会议确认邮件代码 MEETING_CONFIRMATION 原样保留。

IP-2：目标 processing/账号 → 表单默认签名、UTC 事件 → 发送时重新校验目标归属；不得仅凭浏览器传回 contactId。

## D3 人工发送、MIME、尝试存储

PendingMailOperationService:279 解析账号；281..306 最终 subject/text/html 变量渲染；323..346 安全检查；348 构造 SendPayload；370 抑制检查；378 prepareAndClaim；383 ComposedMail；395 SMTP；400 起成功收敛/审计/RAG。新日历只能在最终内容渲染后、安全检查及认领之前校验；不得替换最终人工正文。安全确认失败/重试沿原 mcHostSendRichReply:app.js14460..14524，扩展字段由 JSON/spread 保留。

`ComposedMail` IntroductionMailComposer.kt:73 已有 html/text/messageId/inReplyTo/references/allowSuppressedRecipient，无附件；所有构造点见 evidence/constructors.txt。SmtpMailDeliveryService.send 抑制检查在 SMTP 资源之前，47/48 已支持 In-Reply-To/References；50..62 HTML 是 multipart/alternative(plain,html)，纯文是 setText；退订头在其后。**旧 K-outbound-thread-headers-single-seam 已失效：SMTP 已写线程头，但 Pending rich 目前没传。** 本期只为带 calendar 的新分支传真实 inbound.messageId 作为 inReplyTo/references；普通无附件旧调用形态不动，避免顺带改变旧邮件。

mail_send_attempt：V23 id、orcid100、mail_type50、account100、message_id255、status50、error/timestamps；UNIQUE(orcid_id,mail_type)、UNIQUE(message_id)。V24 增 recipient255/subject255/body LONGTEXT/content_type64/quota_counted/account_counted_at；不增加该表列。ManualReplySendAttemptService.SendPayload:40，computeFingerprint:75 以长度前缀 SHA256 覆盖目标/账号/收件人/最终正文/QA，不含附件。prepareAndClaim:97 REQUIRES_NEW、insertIgnore、FOR UPDATE、CAS，DELIVERY_UNKNOWN fail closed。finalizeSuccess:203 与 finalizeFailure:274 各有 copy 和 new 两条 mail_record 写支路。

该表全部运行时写/读调用：ManualReplySendAttemptService insertIgnore:102/findForUpdate:116/claim:140,164/findById:205,282/updateStatus:264,331；ManualInitialOutreachService find:734/save:735；ManualOutreachTxHelper find:85,134/save:87,136；Repository SQL:16/22/47/59；迁移 V23/V24。完整 evidence/attempt-paths.txt。新附件字段只扩展 DTO，不改变 introduction 尝试域/状态/配额。

IP-3：配置规范化 → 附件语义指纹 → SMTP 发送 → 尝试状态收敛。下载/重复预览不能制造新的发送身份；改时间/链接且最终正文相同，也必须得到不同发送身份。

## D4 mail_record 存档与全部读写入口

Schema 原文 `mail/domain/MailRecord.kt:8`，V1 主表 expert_contact_id FK、direction16、mail_type64、message_id/in_reply_to/subject255、body LONGTEXT、matched_qa_rule_id FK、send_status32、received_at/sent_at/created_at；V6 cleaned_body；V15 sender_account_code/triggered_by/source_inbound_id；V23 error_summary1024/mail_send_attempt_id；V24 后者 UNIQUE+FK；V101 task_execution_id。新字段唯一 `calendar_attachment_json LONGTEXT NULL`，旧行=NULL表示无会议日历，禁止填 `{}`、空串或改用材料 owner。

全部写点（业务代码有 10 个 save 调用，对应 10 个新建表达式，尝试两条 save 内各有 copy）：ManualExpertMailService:69；ManualReplySendAttemptService:249,328；ManualOutreachTxHelper:59,110；MeetingScheduleService:144；AutoMailReplyService:346,686,961,1159。新字段仅 ManualReplySendAttemptService 两个 finalize 的 copy/new 共四支显式写；其余构造保持默认 null。数量算式为 1+2+2+1+4=10，以原始检索复核。 迁移改写点包括 V6/V15/V23/V24/V31/V42/V94/V101/V104/V113/V114；其中非主表改写的索引/FK/关联表消费者在 migrations.txt 分类可核对，旧迁移全部不改。scripts 检索只有 dump_rag_parity_fixtures.py 的说明/导出，无新主表写入脚本。

全部运行时读入口逐行见 evidence/store-paths.txt（当前与初次检索均保留）。按消费者列全：

| 消费模块 | 依赖字段/行为 | 新列处理 |
|---|---|---|
| MailRecordRepository 所有 derived/@Query；MailboxConversationRepository union/keyset | 原时间线/统计/来源/状态/账号/正文/附件存在性 | 不改 union/select 投影，新列按 ID 独立批量读 |
| MailMonitoringService、BounceRateMonitorService、BounceCollectionService | 发送状态/日期/账号/messageId、归因 | 不读取新 JSON |
| MailboxService、ExpertContactManagementService | 列表/专家详情/任务钻取/正文 | 旧 DTO 语义不改 |
| ManualExpertMailService、ManualInitialOutreachService、AutoMailReplyService、ManualReplySendAttemptService、PendingMailOperationService | 去重、线程、手工回复上下文、attempt关联 | 新 finalize 显式写，非会议仍 null |
| GroundedAutoReplyDecisionService、AutoReplyPreviewService、AutomaticApplicationPromotionService | 回信历史/阶段/计数 | 不从 ICS 推导专家状态 |
| UnmatchedInboundMailService、UnmatchedInboundMailController、InboundMailSummaryController | messageId 候选/历史 | 不把 source_inbound_id 当 processing.id |
| ExpertMaterialService、ExpertDocumentBrowseService、AttachmentTransferService、OperatorStatusReconcileService | 材料 owner/授权归属/状态推导 | calendar 不写 mail_attachment/expert_document，不计材料 |
| AiTrainingController、AiQaExtractionService、TrustReplyWorkbenchService、RagReplyController | 训练、证据历史/INBOUND约束 | 不把日历当 QA/RAG 证据 |

RagProcessContextResolver/RagPrefilterService、MailAttachmentService/MessageIdNormalizer 等命中注释是语义说明，不是额外主表写入。仓库、关联表、migration 检索均保留，供复核无漏扫。没有宣称外部人工 SQL 不存在；未在仓库出现的外部运维脚本无法由源码证明。

IP-4：成功/安全失败持久化 snapshot → 刷新后 timeline 单独附件卡/下载；SENT 才暴露下载，失败/未知不伪装已发送附件。
IP-5：独立 outbound 日历 → 既有材料数量/材料下载/专家状态统计不变。

## D5 前端现状与资源

完整逐字基线：[frontend-before.md](meeting-confirmation-evidence/frontend-before.md)，含当前 DOM、mailbox-chat.css 全文、全局 token、button 各状态和资源注册。可复用 `.button` styles.css802..850；`.mc-editor-tools` mailbox-chat.css63、`.mc-editor`64（最终高度由130覆盖）、`.mc-compose-footer`65；局部 disabled67/focus68。主色 #1e40af、亮蓝 #3b82f6、hover #1e3a8a、文本 #334155、border #dce4ef、圆角7/10/18；按钮32px/12px；编辑器基础160..360px/12px/1.8，最终高度100..240px见下方最新覆盖审计。

`.mail-chat` 字号12，p margin0；全站 styles.css306 的 p 为12px/灰色 #94a3b8，body 下 dialog 不继承 mail-chat。预览正文确实受其影响偏淡，新契约显式设 `.meeting-paper p` color:inherit/font-size:inherit；属于本次明确修正。原 mc-editor 高度/字号不整体改；只给生成 block 设置13px/1.85。禁止将预览 `#manual`、`.rev-*`、复制的导航样式带进生产。

mailbox-chat.js现已合并mailbox-refinement（HEAD见evidence/source-revision.txt）。最新targetKey在renderManualSectionInto:2126，manualComposeHtml:2163仍只恢复text；缓存已是sessionStore(user|accountScope|contactId)→drafts Map(targetKey)，get/set/deleteDraft:410/416/422；read2691/save2702；adoptAssembly2724；send2753；retarget2890；unmount3317。04以此最新合同实施；原888/936/1238行号只在initial证据追溯，不能当当前落点。当前scope LRU10、message limit500。

新增download还需部署前缀：app.js:541的contextPath是顶层const，api:1478自动拼前缀；expert-materials.js:699显式拼contextPath+downloadUrl。04给MailboxChat首次mountOptions:14414传contextPath；增加这一参数及专用binary下载适配，不重写宿主api。（来源 K-state-input-no-per-keystroke-innerhtml/K-shared-action-dialog-cleanup）
IP-6：弹窗确认 → drafts 写入 → 切换/刷新/重定向 → send payload；QA/RAG覆盖、取消、失败、晚响应必须逐一覆盖。
IP-7：body native dialog → timezone listbox 键盘/焦点/手机布局；不受 mail-chat overflow 或父 panel backdrop-filter 截断。
IP-8：资源注册 → 组件挂载 → 7个固定缓存测试。index.html 当前20260909-mailbox-refinement；7个测试精确命中见 cache-key.txt。只改index、不改测试会失败。

同日mailbox-refinement已在本次研究期间合并到当前HEAD，最终审计重读修改后的mailbox-chat/DTO/service/CSS/index/7测试；初次证据另存.initial文件。该系列的标签、主题解码、滚动、cache都保留；不得按旧preview整页覆盖。执行时再核对最新source-sha256；如仅行移动按方法/data-role定位，如字段/边界发生合同变更先修订计划。迁移截至V121、V122/V123目前未占用，仍按实际部署顺序复查，绝不改已应用迁移。

前端当前computed覆盖基线：mc-editor在mailbox-chat.css64的160..360被130覆盖为100..240；mc-section在123为白底，summary124是flex/12px/#506783；button147为32px/padding11px。新会议CSS只派生新class，所有修改前原文在frontend-before.md；旧initial.md只用于研究追溯。MailboxConversationService构造现新增inboundMailTagService/expertSearchService；03添加新repo需要补已有WebMvcTest mock，已计入10文件白名单。activeAccountCodes:408是findAllByAccountCodeNot(SIMULATOR)，并非enabled-only；两种读取必须一致。
## D6 标准、测试与事实边界

RFC5545 §3.1、3.3.11、3.6.1、3.8：CRLF、75 octets折行、TEXT转义、UTC事件等依据 [RFC 5545](https://www.rfc-editor.org/rfc/rfc5545)。夏令时本地时间可对应0/1/2偏移，依据 [Java11 ZoneRules.getValidOffsets](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/zone/ZoneRules.html#getValidOffsets(java.time.LocalDateTime))。生产采用 java.time；不搬 preview 的浏览器候选偏移算法。

`pom.xml:186` node-test 在 Maven test 生命周期执行 `node --test src/test/js/*.test.js`；新增 *.test.js 自动覆盖，不改 pom。FlywayMigrationIntegrationTest 使用 Docker MySQL8.0.36、migrationIt=true 才执行；当前10处最新版本断言121，23/24/116定向迁移断言不替换。命令：JDK11 环境下 `mvn test`；迁移 `mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`。无 Docker 则记 NOT_RUN，不记通过。使用测试容器，不连接业务库运行 clean。

已验证预览：姓名/日期/链接生成、正文插入、单块更新、取消、切换、时区中文搜索；详见原 artifacts/meeting-confirmation-preview/README.md。真实发件、数据库存档、日历客户端导入未实施。本次只文档检查，不将旧预览测试冒充生产功能验收。

## D7 知识消费决定

25个条目已读取、hit_count+1、last_used=2026-09-09，清单见 knowledge-use.json。fingerprint/unknown/reservation→D3；rich-render/safety/HTML→D3；attachment/owner/source→D4；dead-template/preview-split/render-all-callers/type-gate→D1；cache/input/dialog/p-muted/fixed-containing-block→D5；JS/Flyway4条→D6。binding规则明确拒绝泛化至Pending；thread-headers旧结论原位纠正。没有已使用条目被归档；没有90天未用低命中条目进入计划。相似主题如渲染、门禁、身份、来源并非同一规则，未强行合并。本轮source-inbound-id达到10次，已向CLAUDE补一行规则回链；JS执行规则已存在，不重复写。新发现controller行号守卫在K-line-number-guard-breaks-on-any-insertion中，已追加消费计数，03白名单包含该测试。角色目录agents/与templates/不存在，不新建角色文件。

补充已核对交互：OperatorStatusWriteSeamGuardTest.kt:44..82将UnmatchedInboundMailController的operatorStatus透传行号固定为215/1118；03新增参数导致后者移动，已列机械同步白名单。此处不是增加新业务写入口。
