# 专家分级授权：存储与调用审计

取证日期：2026-10-29。本文件是代码审计附件，不是可独立执行的计划。代码行号均针对本轮工作树；[逐字源码](source-excerpts.md)、[SHA256](sha256.json)、[原始存储检索](store-usage.txt)、[仓储调用检索](repository-calls.txt)、[扩展存储检索](domain-writes.txt)、[迁移原文](schema-chain.md)供复核。未 SSH 核对部署版本，不把 localhost 原型计数当线上事实。

## 1. 授权、认证与会话投影
- V166 建 `expert_mail_read_access`，PK(username, expert_contact_id)，FK contact ON DELETE CASCADE；只有 granted_by/created_at，没有等级。唯一显式写者 `ExpertMailReadAccessService.grant/revoke`；联系人删除由FK清理；本表无回填脚本（store-usage收据）。`grant`当前重复PUT不更新时间、不降级状态（E02/E03）。
- 读者不只授权 service：`MailboxConversationRepository.hasReadAccess:484`和`expertPredicates:704–793`读取行存在性；前者控制详情，后者在SQL分页前限定授权专家（E05）。代码注释“唯一读者”为过时说明，不能据此漏查仓储。
- `MailReadOnlyPolicy:49`只放行会话list/messages和logout（E01）；`AuthInterceptor:53`执行精确HandlerMethod白名单（E28）；`AuthWebConfig:11–28`构造拦截器，改构造参数须同步（E29）。前端`app.js:21094`还有请求白名单（E24），`startAuthenticatedApp:21210`在user1分支提前return，不运行admin启动链（E25）。
- `MailboxConversationService:166/207`已批量返回expertTags；`:262/322`已有消息tags。`:273/299/327/329`按username==user1一律关闭附件；新增FULL后应按当前专家等级关闭，不能按全局用户名关闭（E04）。
- 同服务`:174`仍向user1返回email；当前“隐藏邮箱”是UI不渲染，不是响应数据已脱敏。本期保持用户端只显示姓名，不借本期承诺邮件正文邮箱脱敏。
- 交互X1：授权写入→会话count/page/详情/附件/编辑保存；每次请求重查，无长期权限缓存。X2：ES标签/邮件标签写入→会话批量投影→两端展示。来源K-session-auth-does-not-imply-mail-authorization、K-mailbox-readonly-needs-lifecycle-audit。

## 2. 待处理权威数据与全量写入口
- V5建`inbound_mail_processing`；V10补正文/人工处理字段，V14 reason_type，V15监控列，V120 uid_validity，V134物理邮箱owner，V167 folder及新物理唯一键。当前实体无处理人归属。**mailbox_owner_code是物理邮箱，不能复用为admin/user1。** 全迁移原文见schema-chain。
- 实体构造扫描只有两处生产新行sink：`AutoMailReplyService.confirmManualReviewWithBody:1463`、`confirmProcessed:1517`。二者在`processSingle:162`的事务内执行；定时收取、UID补收均汇入processSingle。新增归属只在这两个sink计算一次（E08、constructors收据）。
- 全部生产`inboundMailProcessingRepository.save`六处：AutoMailReplyService:246首封自动响应重分类、:1473新建、:1529新建；UnmatchedInboundMailService:193人工绑定、:238人工完成；PendingMailOperationService:1618人工完成。另有`InboundMailProcessingRepository.reopenManualResolved:70`窄UPDATE，由PendingMailOperationService:1689调用。不能只审计两个new构造（E07/E10/E11）。
- `expert_contact.needsManualAttention`是专家整体状态：两个markResolved都用该专家全部MANUAL_REVIEW数量清零，不得改成“当前用户剩0”清零，另一个用户可能还有待处理。
- 读取：MailboxConversationRepository.rangeUnionSql:611 pending_flag仅判断MANUAL_REVIEW；page/count/explain共用union/谓词，pending排序及pendingOnly HAVING都依赖它。suspensionStatesByContacts:419统计跨账号待处理。MailboxSuspensionService.pendingBadge:122按用户名排除挂起，**尚未按用户名分配来信**；pendingCount:230同样没有处理归属。
- 旧队列：UnmatchedInboundMailService.listManualReviewQueue:30调用find/countManualReviewQueue及countManualReviewByAccounts/countGroupedByReasonTypeForAccounts；UnmatchedInboundMailController.list:92尚无session参数（E09）。Repository还存在监控日期统计、来信摘要、全量历史读，不属于“我的待处理”，保留全部来源，不机械加owner条件。
- 新写读交互X3：新建来信→列表/排序/待处理专家数/顶栏邮件数/更多图标邮件数/挂起计数/旧队列。X4：完成、撤销完成、人工绑定、重分类→同一统计。X5：降级/撤权→未处理来信回管理员；不能出现双方都看不见的队列。来源K-inbound-processing-write-paths、K-mailbox-inbound-source-authority、K-sender-account-enabled-scope。

## 3. 附件、发送与持久草稿
- mail_attachment迁移链V7/V36/V118/V119/V130有record-owner、processing-owner、manual-upload-owner三条归属；expert_document另持contactId。`ExpertMaterialService.resolveOwnerContact:78`逐一解析三类owner，`resolveReadyFile:107`核对document及owner，再校验路径。不能凭附件id相同或请求contactId放行（E12）。
- 写者全集（domain-writes/store-usage）：MailAttachmentService元数据桥接、legacy record/processing同步与建档；AttachmentTransferService下载完成路径；ManualExpertMaterialUploadService人工原件及document；ExpertMaterialService关联修复；ExpertDocumentAnalysisService解析结果。读取为MailboxService/MailboxConversationService/ExpertMaterialService/ExpertDocumentBrowseService/ExpertMaterialArchiveService。文件原件、transfer状态及document关系本期不重建。
- `MailboxAttachmentController.download`调用unscoped文件解析，新增user1准入必须先查真实owner；`ExpertDocumentBrowseController`contact路径、OutboundAttachmentController草稿/已发下载、CalendarAttachmentController下载各有不同归属校验，全部保留。
- V126 outbound_mail_attachment原件不可变，OutboundAttachmentService唯一INSERT；已发快照在mail_record V127列，发送服务写；下载已检查contact+记录方向+SENT+快照成员资格+真实邮箱账号。user1 FULL只是增加一道权限，不替换这些条件。
- 直接发送两条：MailboxConversationController.conversationManualRichReply:391→PendingMailOperationService.sendConversationManualRichReply:390；UnmatchedInboundMailController.sendManualRichReply:260→PendingMailOperationService.sendManualRichReply:161。都有authenticatedUsername，不能信任operatorName；沿用原校验/attempt/SMTP/安全确认，不新建SMTP旁路。最终成功由ManualReplySendAttemptService事务确定，HTTP失败不等于没有发送。
- V152原mailbox_reply_draft owner+target唯一，ACTIVE/SENT/DISCARDED；MailReplyDraftService:25–84及Repository负责写入/list/summary，当前只验证owner身份和target实体存在，不验证expert grant。FULL开放前，保存/读取/删除/发送及分页列表都须叠加当前FULL；READ走审核草稿，不得通过旧draft API直接发。
- X6：上传→草稿→发送→快照下载；其中任何目标改成未授权专家均拒绝。X7：发送成功/重复成功/未知结果与草稿结束状态同步，不能退化为点击后直接标SENT。来源K-expert-document-ownership-chain、K-mail-attachment-write-paths、K-manual-send-success-transaction-and-dedup、K-manual-send-fingerprint-complete-identity。

## 4. 专家资料、两类标签和排期
- ES三个真实mapping均dynamic:false，tags:keyword：raw:7/45，candidate:7/47，application:7/57。字段已存在，本期不增ES字段。人工标签写者ExpertIndexController.addTag/removeTag:289/303→ExpertIndexWriterService:646/664；另外晋级、发现、核验写标签，不改变自动路径权限。读者ExpertSearchService投影/聚合、MailboxConversationService按实际currentIndexLevel批量读；不对user1开放全局搜索/聚合来取某专家标签。
- 邮件标签是V53 inbound_mail_tag，与expertTags不同。唯一CRUD服务InboundMailTagService（autoApplyQaTags/addQaTag/addCustomTag/deleteTag）；读listTags/listTagsBatch/stats。自动写来自收信两个sink，手动写来自InboundMailSummaryController:144–179；删除仅给tagId，需反查processing→contact→FULL。READ仅显示，不开放写端点。
- 人工资料V153 expert_manual_profile以规范化expert_key为PK、version乐观锁；ExpertManualProfileService:49/55读写唯一入口，ExpertManualProfileController:45/55目前只看登录。expertId/level在query/body不能靠path守卫处理，须规范化后匹配FULL专家真实身份、保留expectedVersion。相同专家多contact共享人工档案/ES标签，这是现有存储语义，不复制私有档案；授权一个contact允许更新其对应专家共享档案，但不扩大其他contact邮件访问。
- expert_contact既有管理写路径集中业务服务，状态流转仍经ConversationStateService；标签授权不等于允许更改全局邮箱或批次。管理panel里operator-status/index-level可按目标contact开放；手动接管分派/全局账户迁移不在本期端点白名单。
- V9 meeting_schedule含contact FK；后续排期字段在迁移收据完整保留。MeetingScheduleService的extractAndCreate/createManual/updateSchedule/confirmMeetingAndEmail/completeMeeting/cancelMeeting及人工发送finalizeSuccess均写排期；getContactDetail、MeetingCalendar查询、邮件确认读取。`findOwnedSchedule:269`已核对schedule.contactId，必须保留，授权不能只看path contact。确认并发邮件是显式操作，普通新增/改期/批准草稿不自动发送。
- **当前UI实际排期是另一张表**：app.js:27294的mcHostOpenMeetingSchedule调用`/api/meeting-calendar/events`；MeetingCalendarController:23–68处理events/summaries/create/update/cancel（E30/E31）。V125 `meeting_calendar_event`有contact FK、source_mail_record_id唯一键、ACTIVE/CANCELLED、updated_at微秒版本。唯一写仓储MeetingCalendarEventRepository.insert/updateMutable/cancel；入口MeetingCalendarService.createManual/createFromSentMail/update/cancel，发送入口ManualReplySendAttemptService:478调用createFromSentMail。读get/list/summaries与收发件箱摘要、日历页共用。完整调用扫描见calendar-usage.txt。本期重点守卫这套真实入口；旧MeetingSchedule外部接口不因新授权自动放行，旧admin行为保持。
- 备注/位置：V150 expert_contact_note PK contact，note VARCHAR(2000)、updated_by/time、FK CASCADE；ExpertContactNoteService.get/save为显式读写唯一入口，空note DELETE，否则UPSERT。V146 expert_contact_location PK contact、country_code/可空zone_id、FK CASCADE；ExpertContactLocationService.get/save/timing读取所在地与来信统计，save唯一UPSERT。user1只沿原服务写当前contact，不能另建权限缓存或用户私有资料。原文与全集见supporting-store-usage.txt及schema-chain。
- X8：后台/自动标签变化→READ可见且不可删；FULL编辑→admin回读。X9：FULL资料CAS、排期修改→原管理员详情/日历读取；同orcid别contact不因此获得邮件访问。

## 5. 前端与既有审核计划
- user1实际模块为mailbox-readonly.js，renderList:249只输出姓名/邮件主题；renderConversation:296不输出expertTags/tags（E17）。无需为只读标签新增接口。
- 原popover七字段FILTER_FIELDS:95（E18），DOM:1644–1670（E19），字段节点迁入/退出还原。mc-filter-count当前用于已应用筛选数量；新待处理角标要独立class，不能把active-filter数误显示成邮件数。
- 原header授权按钮+badge在mailbox-chat.js:3728（E20）。新select直接替换这两个节点，不再另加大面板。
- CSS完整基线E21–E23：popover宽430px、padding18px 20px、gap14px、select高34px、圆角7px；按钮36px；z-dropdown=20。只改新增控件，原面板结构/媒体规则/opacity/portal归属保留。
- `MailboxChat.selectExpert:3499`默认会请求联系人、备注、挂起、时区（E27），mount还有列表、草稿、标签选项等加载；不能给user1直接调用完整admin mount。`mcHostOpenExpertDetail:21562`调用openContactInList，会跳专家列表；必须提供限定当前专家的资料编辑宿主，不让user1获得其他导航。
- 方案选择：保留MailboxReadOnly管理授权列表和READ详情；FULL选中时在右侧挂载MailboxChat受限conversation-only模式，复用同一发送/附件/管理/排期组件。该模式跳过左栏、全局筛选迁移、默认tab探测及批量启动请求；只有明确允许的当前专家API。切换READ/FULL/NONE均卸载并清缓存。
- 既有draft-review-master及01–05目前只是计划，src/main检索MailReviewDraft/review_draft无命中（no-review收据exit=1）；其V169未落地。本计划依赖该计划实现审核能力，并以本计划最终筛选位置替代旧顶部按钮。不得伪造pendingReviewCount，也不能启用一个会查询不存在表的菜单项。
- X10：bootstrap/refresh/focus/visibility/logout/晚响应→不触发admin全局调用，不跨专家复用可写状态。X11：popover原字段迁移→新增第一项及角标刷新→clear/reset/unmount不丢原功能。X12：READ审核稿与FULL人工稿并存，升级不解锁已审核正文，不自动把已提交稿发送。
- 知识采用：K-mailbox-popover-scope-and-fixed-containing-block、K-expert-tag-editor-shared-render-contract、K-relocated-control-refresh-owner、K-frontend-cache-key-triad。K-expert-filter-registration-sites是专家漏斗页，具体注册点不适用；只采用多入口需同步的教训。K-master-plan-shared-file-sequential-gates约束共享文件顺序。无5条同义重复需合并，命中条目不做过期归档。

跨阶段人工项引用的其他I/S条款，以总计划同编号定义为准；本步实现仍严格限定本文件清单。
