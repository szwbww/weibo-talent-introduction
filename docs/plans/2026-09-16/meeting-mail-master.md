# 会议排期与人工回复附件 · 开发总计划

日期：2026-09-16。状态：**计划待审批；未执行生产代码修改、数据库迁移或发布**。

用户最后确认：附件上传入口只保留图标。截图和预览只作为交互参考，不作为已实现后端能力的证据。本计划只交付以下两个需求。

## 需求描述

### 需求一：会议日历与邮箱排期

1. 主导航新增“会议日历”；人工在收发件箱的会议确认中发送带明确日期的邀请，**服务端确认发送成功并完成落库后**自动产生排期。
2. 邮箱专家列表显示“已有排期”及最近时间；头部操作支持新增、改期、取消；日历也能新增、改期、取消，多场可逐条选择。
3. 两边共用同一份持久化排期；取消保留记录，默认排期视图隐藏，可查看取消历史。
4. 专家会议日期用中文、北京时间，输入、显示、月历日期格同口径；浏览器/服务器本地时区不影响结果。

### 需求二：人工回复通用附件

1. 富文本“链接”右侧新增**仅回形针图标**按钮，悬停/无障碍名称“上传附件”，没有可见文字。
2. 任意文件格式、多选上传，显示文件名/大小/上传状态；发送前可移除和下载校对；和会议ICS共存。
3. 发送成功后对话消息显示附件，点击下载原文件；页面刷新、应用重启后已发附件仍能下载。

### 保持

- 现有人工回复/无来信跟进的真实锚点、发送账号、正文、安全两级确认、退订门禁、QA/RAG审计与未知状态不重发规则。
- 原会议确认模板/ICS生成和下载；改期不改写已经发送的正文或ICS。
- 专家材料数量、原入站附件/DMARC、原会话筛选/标签/草稿缓存、专家流程状态。

### 不在范围

重复会议、提醒通知、拖拽、参会人邀请系统、取消/改期自动发邮件、识别自由文本排期、历史数据自动回填、旧排期模块迁移；自动发信/群发通用附件；在线文档预览/转码/OCR；新对象存储、队列、WebSocket、文件管理后台、跨刷新未发送草稿恢复。

## 关键不变量

### Invariant I-1: 已发送与排期一致
- Rule: 人工会议发送成功的mail_record、attempt SENT、新排期在同一成功事务；SMTP结果未知不补发；重复成功请求不覆盖改期/取消。
- Applies to: 01/02、05的共享finalize、06发送链。
- Violation consequence: 重复邀请、幽灵排期、人工修改丢失。
- 来源: K-smtp-idempotency-reservation-before-delivery / K-manual-send-unknown-must-converge。

### Invariant I-2: 双端时间与状态一致
- Rule: 结构化UTC存储、北京本地输入、中文北京显示；ACTIVE/CANCELLED一份真值；取消保留；版本冲突409，旧操作不得覆盖新操作。
- Applies to: 01/03所有CRUD和查询。
- Violation consequence: 双端不同、时区错日、已取消复活。
- 来源: original。

### Invariant I-3: 原文件身份与授权完整
- Rule: 用户只交上传id；服务端定owner、hash与路径；同一有序快照用于SMTP与存档；消息下载按真实SENT关系校验，字节与原上传一致。
- Applies to: 04～07。
- Violation consequence: 附件丢失、重复投递、跨专家下载。
- 来源: K-manual-send-fingerprint-complete-identity / K-download-context-path-host-injection。

### Invariant I-4: UI范围明确
- Rule: 上传仅图标；日期中文北京；所有异步归属捕获owner；原CSS字节合同不变；最终9个资源统一缓存键。
- Applies to: 03/07/08。
- Violation consequence: 界面不符合确认、串草稿、缓存旧版本。
- 来源: K-mailbox-draft-cache-owner-capture / K-mailbox-chat-css-byte-contract / K-frontend-cache-key-triad。

### Invariant I-5: 只做两项功能
- Rule: 新增两张专用窄表、mail_record一个nullable快照字段；不引入框架/调度器/泛用附件平台，不更改旧专家流程。实现文件必须在相应子计划清单中；超出需先修订计划，不靠“相关文件”扩范围。
- Applies to: 全部子计划与发布。
- Violation consequence: 过度设计、回归面失控。
- 来源: 用户范围要求与create-p范围规则。

## 现状审计

### 代码直接证明

|事实|当前代码证据|落实决定|
|---|---|---|
|现有会议确认是只读生成器|MeetingConfirmationService.kt:31、:106-179|复用已校验startUtc/endUtc，不解析邮件正文|
|人工发送只持久化ICS，未连排期|PendingMailOperationService.kt:543-580、:626；ManualReplySendAttemptService.kt:329/349/411/432|在成功finalize事务增加来源唯一排期|
|旧排期日期是字符串，取消会变更专家状态|V9__create_meeting_schedule_and_template.sql:1-17；MeetingScheduleService.kt:214-234|新结构化日历窄表和服务，旧模块保持|
|通用外发附件不能混入专家材料|V7/V36的mail_attachment owner约束；document/ExpertMaterialService.kt:671-707|独立上传元数据与outbound目录，材料计数不变|
|当前SMTP只处理正文与ICS|IntroductionMailComposer.kt:73；SmtpMailDeliveryService.kt:49-100|原ComposedMail新增默认空文件列表，扩mixed MIME|
|消息列表已一次批量读SENT记录|MailboxConversationService.kt:233-241|同结果投影通用附件，无额外逐封查询|
|草稿重建会遗漏没列出的字段|mailbox-chat.js:3249、:3891|显式保留一个outboundAttachmentDraft字段|
|新按钮准确插入点已存在|mailbox-chat.js:2668-2674|链接后、会议确认前；图标没有可见文字|
|全局兜底会吞ResponseStatusException为500|GlobalExceptionHandler.kt:65；MeetingConfirmationController.kt:23|新错误使用专用handler，不假定HTTP状态自然透传|
|缓存版本实际已到9月14|index.html全部?v=；9份测试固定同键|按当前键更新，不能照旧知识猜版本|

上述Kotlin相对前缀为src/main/kotlin/com/weibo/talentintroduction/；前端为src/main/resources/static/；完整路径、代码快照SHA与全部grep回执见[证据文档](meeting-mail-evidence.md)。所有新表、接口、容量限制都是**本次拟实现设计**，不是现有功能陈述。

### 存储与读写边界

|对象|现存读写审计|本次增量与消费者|
|---|---|---|
|旧meeting_schedule|6个服务保存点、自动提取、专家详情/controller/app；回执meeting_schedule_all|0修改；不迁移|
|新meeting_calendar_event|当前0命中|01手动CRUD/02成功来源创建→日历/邮箱摘要|
|mail_record|基础及V6/15/23/24/31/101/123；所有生产保存、查询、SQL、脚本回执mail_record_all|05增加1 nullable字段；四分支存档→06消息/下载|
|mail_send_attempt|V23/24、repository/发送/恢复路径回执attempt_all|不增字段状态；只扩人工payload hash内容|
|mail_attachment/expert_document/transfer|V7/V36与全消费者回执attachment_consumers|0写；维持入站材料链|
|新outbound_mail_attachment和磁盘outbound/|当前0命中|04不可变上传→06发送和下载|
|sessionStore/drafts|34处读写命中，回执draft_writes|07一个附件草稿字段；保留原owner/LRU界限|
|静态资源缓存|9资源、9测试；回执assets/cache_tests|08统一新键，无额外资源注册|

跨模块IP：已校验会议→SMTP/成功事务→日历与邮箱；上传→草稿→发送指纹/MIME/快照→会话/下载；会话owner→异步回包；index版本→浏览器资源。各子计划给出相应不变量、具体文件和人工验收。

### 本次设计取值与限制

- 新日历只收录上线后通过“会议确认”发送的带结构化时间的邀请，以及手动新增；普通会议邀约文案没有具体日期，不猜测排期。既有专家列表也能手动选择，邮箱入口不要求先发邮件。
- 改期/取消只更新系统排期。页面明示“排期操作不会自动发送通知邮件”；历史邮件和ICS作为原件保留。
- 多人并发更新用现有updated_at作为完整版本标识，事务锁行比较；不新增版本表或改期历史事件平台。
- 任意格式，容量拟定10MiB/文件、20MiB/封、最多10个通用附件；原ICS上限沿用现有64KiB。容量是资源边界，不是文件格式白名单。
- 未发送的上传文件本期保留；不添加自动清理任务；上传中断即时清理本次临时文件，崩溃残留不成为可用附件。磁盘容量及实际代理请求体限制是上线验收项，当前未宣称线上已验证。
- 单应用页面两侧保存后立即回读；跨浏览器在重新获得焦点/进入Tab时回读，不新增推送连接。
- 前端沿用原静态HTML/JS/CSS，日历代码放现有app.js；不引入日历库或新构建链。新增CSS全文与DOM已在03/07逐字定义。

## 实现方案

### 顺序与范围

|顺序|子计划|文件数|独立可验证产物|
|---|---|---|---|
|1|[01-calendar-api](meeting-mail-01-calendar-api.md)|8|手动排期API与持久数据|
|2|[02-calendar-send](meeting-mail-02-calendar-send.md)|5|邀请成功自动排期事务|
|3|[03-calendar-ui](meeting-mail-03-calendar-ui.md)|6|日历与邮箱双端界面|
|4|[04-attachment-storage](meeting-mail-04-attachment-storage.md)|10|任意格式上传与草稿原件下载|
|5|[05-attachment-delivery](meeting-mail-05-attachment-delivery.md)|8|MIME与有序附件存档|
|6|[06-attachment-flow](meeting-mail-06-attachment-flow.md)|10|双发送入口、消息与下载闭环|
|7|[07-attachment-ui](meeting-mail-07-attachment-ui.md)|4|仅图标上传和会话文件卡|
|8|[08-assets-release](meeting-mail-08-assets-release.md)|10|缓存激活与完整回归|

每个子计划≤10文件、≤2子系统、每个现有共享store≤1新增字段。拆分是为了满足create-p的变更规模要求；不是新增8项业务。中间后端阶段保持旧客户端兼容，可独立部署验证API/协议；前端03/07可在无缓存测试页面独立验证，正常线上发布时必须合并08缓存激活，不能发布旧键资源后宣称已生效。

- 01→02→03完成需求一。
- 04→05→06→07完成需求二（05依赖02的发送服务基线）。
- 08是两项共用的静态资源发布检查。
- 每阶段先运行该阶段指定测试，最后一次全量Maven/JS与显式数据库门禁；只在新失败/新改动后重跑相应检查。
- 不修改当前已有的docs/releases.json及scripts/expert_discovery未提交改动；计划执行使用明确隔离工作区或确认基线，不覆盖其他工作。

## 变更文件清单

本文件是调度总览，不是一个允许整体修改51文件的单体实施计划；**每个子计划的文件表才是其执行边界**。总去重51个实现/测试文件，逐项全集如下，新增/修改性质见对应子计划。

- `src/main/resources/db/migration/V125__create_meeting_calendar_event.sql`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/MeetingCalendarEvent.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarController.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt`
- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/mailbox-chat.js`
- `src/main/resources/static/styles.css`
- `src/test/js/meetingCalendar.test.js`
- `src/test/js/mailboxCalendarIntegration.test.js`
- `src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql`
- `src/main/kotlin/com/weibo/talentintroduction/mail/domain/OutboundMailAttachment.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/OutboundMailAttachmentRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentModels.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentController.kt`
- `src/main/resources/application.yml`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentServiceTest.kt`
- `src/main/resources/db/migration/V127__add_outbound_attachments_snapshot.sql`
- `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt`
- `src/test/js/mailboxOutboundAttachments.test.js`
- `src/test/js/meetingConfirmationIntegration.test.js`
- `src/test/js/ragWorkbenchRender.test.js`
- `src/test/js/meetingConfirmationAssets.test.js`
- `src/test/js/overlayAndDialogContrast.test.js`
- `src/test/js/mailboxChatStyle.test.js`
- `src/test/js/ragKnowledgeBasePage.test.js`
- `src/test/js/manualReplySubjectPrefill.test.js`
- `src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt`
- `src/test/js/trustReplyWorkbenchSharedMount.test.js`
- `src/test/js/checkRepliesRelocation.test.js`
- `src/test/js/batchSendTaskConsoleVisualFix.test.js`

本轮实际交付仅本总计划、8份子计划、证据文档及知识库审计更新；没有修改上述实现文件。

## 验收标准

- I-1：02真实事务/失败/重复SENT/取消后重提用例；05/06混合附件不能破坏。
- I-2：01CRUD/并发与03三个浏览器时区/双端同步/取消历史用例。
- I-3：04文件边界、05真实MIME字节、06两个入口及消息下载归属、07实际浏览器下载。
- I-4：03/07逐字样式与DOM/草稿测试、08全部9资源缓存一致。
- I-5：逐子计划diff核对文件表，生产依赖清单无新框架；素材预览脚本不进入生产；人工材料计数/旧状态机回归。
- 子计划必须分别报告测试实际结果；Docker/MySQL/代理/SMTP沙箱未准备时可以标明“未验证”，不能记通过。此计划阶段不执行产品测试、不宣称实现已验证。

## 人工验收清单

此总清单用于整体验收；每份子计划A-n是对应细节的权威清单。人工验收开始前才按技能导出-acceptance.md，本轮不生成平行勾选副本。

### A-1：两项完整链路
- 前置条件：完整实现测试部署/talent；登录，测试专家有一封来信；SMTP沙箱；中文zip文件记录SHA。
- 操作步骤：1.点击链接后的回形针，选zip。2.会议确认填北京2026-09-18 10:00–10:30，填入人工回复。3.发送成功后查看对话和日历。4.从日历改到9月19日14:00，再回邮箱取消。5.刷新，勾选显示已取消，下载zip/ICS。
- 预期结果：按钮无可见文字；SMTP恰1封且有zip/ICS；成功时排期1场、邮箱有已有排期；改期双端同北京时间14:00；取消有效0场、历史1场；zip SHA始终一致，原ICS仍9月18日10:00对应02:00Z；材料数量不增。
- 覆盖：I-1～I-5；两需求全部主路径；所有主要IP。

### A-2：手动排期无需邮件
- 前置条件：测试专家无会议邀请；已登录，浏览器时区设America/Los_Angeles。
- 操作步骤：1.邮箱手动新增9月20日15:00–15:30。2.日历再手动新增同专家9月21日16:00–16:30。3.分别改期/取消，刷新页面。
- 预期结果：两条都可逐条管理，日期中文北京；没有任何新邮件；取消记录保留，专家原流程状态不变。
- 覆盖：I-2/I-4/I-5；手动新增与原流程保护。

### A-3：发送失败、草稿隔离和下载权限
- 前置条件：测试专家A/B、登录用户U/V；沙箱可模拟拒绝/未知；已有A成功附件。
- 操作步骤：1.U在A上传期间切B输入草稿。2.A发送失败后安全重试；未知组重提相同请求。3.V下载A已发附件；用B消息id拼同附件URL。4.普通无附件与仅ICS邮件各发送一次。
- 预期结果：B草稿不被清；安全重试不漏附件；未知请求不再投递；已发合法下载200、错消息404；旧两种邮件能力保持。
- 覆盖：I-1/I-3/I-4/I-5；异步owner、两个发送入口、下载边界与保持项。


## 计划自查记录

- 8个子计划文件数：8 / 5 / 6 / 10 / 8 / 10 / 4 / 10；每个≤10。总去重51文件包括自动化测试与9份固定缓存键测试，不代表51个生产功能模块。
- 已逐项校验：需求/不变量/审计/任务/文件表/机器验收/人工验收章节顺序；前端03/07/08具样式合同；新文件均标新增，后续修改04新建controller属于明确依赖。
- 自查修正：取消弹窗固定为同一dialog确认态；移除重复storage_key字段；发现GlobalExceptionHandler吞HTTP状态，新增业务错误改为专用映射，multipart上限异常加入04清单。
- 当前代码证据与拟新增设计分列；尚未执行实现/测试/迁移/部署；所有A-n是待实施后的验收步骤，不是已通过报告。
- Phase 0读取15条知识，另补读异常映射1条；知识主题分别为事务/身份/未知结果/正文/锚点/模板/材料/草稿/视图/缓存/下载/CSS/DOM/Flyway/异常。引用已分配给对应不变量；未将旧版本号或历史“未实施”说明当当前事实。
- 本次没有知识条目新跨hit_count=10；重叠主题不足5条，无强制合并。仓库未发现agents目录或templates/project-CLAUDE.md，不新建角色文件扩范围。
