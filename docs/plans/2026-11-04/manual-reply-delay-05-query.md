# 05 会话时间线、列表和状态投影

前置04通过。会话查询子系统，8个文件（A11/A12）。前端尚未展示新动作，但旧响应字段保持兼容。

## 需求描述

列表和会话读取到同一任务的等待、失败、撤回、成功状态，失败重发成功后仍只有一张邮件卡。保持账号/日期/方向/关键词筛选、真实来信标签、附件归属、只读授权与审核context语义。不改历史mail_record数据。

## 关键不变量

### Invariant I-8 — 稳定身份和统一计数
- Rule — job的source=DELAYED_MANUAL_REPLY、id=job.id、source_rank=3、eventAt=首次createdAt；其attempt关联mail_record从UI union排除，所有job状态保留一行。SENT计成功1，FAILED/UNKNOWN计异常1，WAITING/SENDING/CANCELLED均不计成功/失败；received/pending均0。
- Applies to — summary/count/latest/timeline/membership/accountCodes/cursor。
- Violation consequence — 双卡片、分页漏项、列表与对话不一致。
- 来源 — K-mail-record-source-inbound-id；总I-8。

### Invariant I-8b — 审核context与真实业务锚点
- Rule — reviewContext继续使用原始真实邮件union，保留原source/id/status/排序字节，任务入队/撤回不能改变context；实际新来信/成功发件仍改变。latestInbound与真实OUTBOUND锚点选择不得选择job.id。
- Applies to — reviewContext、latestInbound、来源查询。
- Violation consequence — 已审核稿排队即失效，或任务id冒充mail_record。
- 来源 — 当前MailboxConversationRepository:33–76交叉审计。

### Invariant I-7 — 可读与可操作分离
- Rule — 每次读取执行当前权限；READ无附件/restore/action能力。批量按当前页job id获取脱敏投影，不给前端snapshot或actor私有证据。
- Applies to — listMessages/listConversations/DTO。
- Violation consequence — 跨专家/只读泄露或无界N+1。
- 来源 — K-mailbox-readonly-needs-lifecycle-audit。

### Invariant I-10 — 状态来源明确
- Rule — job元数据来自新表；历史MAIL_RECORD失败仅显示通用异常，不能捏造job或canRetry。未发送附件只显示允许的文件名元数据；已发送下载使用真实sentMailRecordId构造原下载URL，绝不把job.id传给邮件原件API。
- Applies to — detail projection、attachment dispatch、summary error。
- Violation consequence — 错附件下载、未知结果可重发。
- 来源 — K-mail-attachment-write-paths、K-manual-send-error-response-opaque。

## 现状审计

- `MailboxConversationRepository.kt:687–755`现union为MAIL_RECORD rank1与INBOUND_PROCESSING rank2；所有字符串显式utf8mb4_unicode_ci。`:207/263/312/417/455/496/655`的count/filter/page/latest/inbound/account/timeline均消费该union。完整调用证据04。
- `:785–800` membership直接EXISTS mail_record，并非只使用union。只新增timeline第三段会遗漏日期/关键词筛选；summary目前聚合全历史，日期只限定专家入选，不应改成区间计数。
- `:33–76` reviewContext也使用union并哈希source/id/body/status；本计划必须拆出语义明确的rawMailUnionSql与uiMessageUnionSql。禁止一个Boolean参数让调用点默认误选。
- `MailboxConversationService.kt:318–380`仅为MAIL_RECORD+OUTBOUND+SENT批量读快照，`:345`对每行调用resolveMessageAttachments(row.source,row.id)；新source要显式分支，不能顺势当作收信源。
- `:550`游标以contact/account/time/rank/id编码并验证rank，新增3但继续接受旧1/2。`ConversationMessageItemResponse:163`和summary DTO在MailboxConversationController文件内。
- `OperatorStatusWriteSeamGuardTest.kt:74/255`固定引用service行号；本次插入DTO逻辑会移动行号，需要更新实际允许行，不放宽守卫。源码证据07。
- 数据写者为03 enqueue/cancel/retry/claim/finalize和旧mail_record/inbound写者；所有读写/schema继承总计划与证据01/02/07。交互X-3 reviewContext，X-4结果→查询，X-5附件投影，X-6权限，X-7增量刷新。
- A12执行前审计：05 epoch1 `../fast/manual-reply-delay-master/children/05-query/execution.md:33-85`记录当前OutboundAttachmentService的无SQL完整性校验在private `readVerifiedFile`，公共resolveForMessageDownload/resolveForSend均先查元数据；整页合并调用resolveForSend又会把每封MAX_FILES/MAX_TOTAL_BYTES限制误用于整页。复用原安全方法比复制真实路径/size/SHA代码更安全，所需最小授权是该文件的内部可见性，不改变方法体。READ一词指权限级别；FULL/admin可读文件保持04能力语义，但SQL仍须按页批量。

## 实现方案

### T1 UI union与membership（I-8/I-8b/I-10）

修改MailboxConversationRepository：保留原始union作为rawMailUnionSql，仅reviewContext用它；UI统一union的MAIL_RECORD段排除存在delayed_manual_reply.send_attempt_id=mr.mail_send_attempt_id的记录，第三段选择job。job各字符串用原collation，created_at_ms通过数据库与原DATETIME一致的会话时区转换，仅用于展示/排序；due权限仍用epoch。测试UTC与Asia/Shanghai数据库session，不把Java默认时区混入截止判断。

第三段字段来源明确：subject/text来自job派生列；messageId/inReplyTo只在有真实结果时从attempt/关联mail_record读取；body为纯finalText，cleanedBody同值；sendStatus=job.status。新增delayedWaitingFlag只在WAITING/SENDING为1；不与人工待处理pendingFlag混用。

summary在原failedCount基础上正确聚合第三段，新增delayedPendingCount字段（末尾默认0保持构造兼容）；失败图标用failedCount>0，不只看latestMessage。membership的OUTBOUND EXISTS改为与UI语义一致的旧record排除+job存在性并集，subject/keyword/日期/账号/recipient条件必须落在同一封job。INBOUND label及direction组合保持原规则。summary/date仍为原全历史聚合口径。

latestMessage可返回job；latestInbound仍只取真实processing；真实发件锚点逻辑不改。CANCELLED仍留一张「已撤回」卡，便于恢复，不能计作成功。

### T2 DTO与附件来源（I-7/I-8/I-10）

修改MailboxConversationController DTO新增可空 `delayedReply`（04公开投影类型）和list response的serverNowMs；末尾默认值保持旧构造点编译。新增字段不属于数据库共享表列。MailboxConversationService按页批量读取job投影、当前权限以及相应sentMailRecordId，不逐条HTTP/SQL查询。

对于DELAYED_MANUAL_REPLY：不调用expertMaterialService.resolveMessageAttachments(job.source,job.id)；已SENT时以真实mailRecord对象投影现有calendar/outbound下载；未发送时只给原actor/admin可见的附件名/大小，下载沿现有owner/contact API并再次授权。READ两者均隐藏，磁盘不读取。

新增Repository批量投影方法限制输入当前页ids并核对contact/account，不让客户端无限传入查询集合。能力计算采用03/04相同规则，过期截止或源失效不展示可执行动作；动作API仍最终重验。

### T2a 复用无SQL附件校验（I-7/I-10，A12）

在新增授权的 `OutboundAttachmentService.kt` 仅将 `private fun readVerifiedFile(attachment: OutboundMailAttachment): OutboundMailFile` 改为internal，方法体、公共上传/发送/下载API、路径/文件存在与regular-file/size/SHA校验保持原样。不得复制一套路径安全实现或添加绕过校验参数。

已授权DelayedManualReplyRepository仅按服务端选出的当前页job/contact/account批量取元数据；MailboxConversationService在调用内部方法前维持每个job的actor/contact归属、附件顺序、去重、每封数量/总大小限制，返回快照与该job冻结快照比较。不接受HTTP传入的实体对象，不把admin身份替换原actor的上传归属。READ不执行附件元数据批量查询、不取私有附件快照、不调用磁盘校验；FULL/admin保留03/04当前完整性/源有效性规则，不能用metadata-only canRetry替代。动作API仍最终重验。

### T3 游标与回归（I-7/I-8/I-8b/I-10）

MailboxConversationService支持rank3编码/解码；同毫秒三类source/id可碰撞但复合键不同；失败→WAITING→SENT不改createdAt。旧cursor继续有效。更新OperatorStatusWriteSeamGuardTest中被移动的精确行号，并核对该行仍为原operatorStatus写点。

增加DelayedManualReplyQueryIT（Testcontainers隔离MySQL），对同一数据集检查count/page/summary/latest/timeline/membership和reviewContext。修改现有MailboxConversationRepositoryIT补充新表清理顺序与回归；该IT会连接配置数据源，运行时必须显式覆盖到隔离测试库，禁止默认连开发/线上库。

## 变更文件清单

|#|文件|操作|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt|UI/raw union与查询|
|2|src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt|投影/附件/游标|
|3|src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt|DTO|
|4|src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt|批量脱敏投影|
|5|src/test/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyQueryIT.kt|新增真实SQL测试|
|6|src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt|筛选/清理回归|
|7|src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt|精确行号更新|
|8|src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentService.kt|A12：仅readVerifiedFile的private→internal，方法体不变|

## 验收标准

- I-8 — 六态、同秒三source、两账号、页大小1与50、重试最终成功、取消后新内容：计数正确、主键稳定、跨页无漏重、summary无整份snapshot。
- I-8b — 原数据reviewContext哈希与改动前完全相同；enqueue/cancel/retry不变；新真实来信/成功mail_record才改变；真实锚点始终mail_record.id。
- I-7 — READ不查询附件快照、不返回恢复/操作能力；NONE拒绝；列表和详情账户范围一致；页级固定次数查询、无按消息N+1。
- I-10 — job与真实mail_record id刻意相同时下载URL仍引用正确记录；历史FAILED无delayedReply；错误原因来自固定映射。
- 运行新增IT、现有MailboxConversationRepositoryIT（mysqlIt=true且隔离数据源）、SqlCompatTest、OperatorStatusWriteSeamGuardTest和现有附件/只读测试。记录测试实际执行数，不以跳过充通过。
- A12 — 新QueryIT覆盖页大小/附件数增长时SQL次数固定、每封附件限额不跨job聚合、合法/删除/大小变化/hash变化文件的FULL重试能力、跨contact/原actor归属拒绝，以及READ零私有附件查询/零文件校验/空附件/动作false。既有OutboundAttachmentServiceTest仍必跑，保持原安全断言。

## 人工验收清单

### A-1 API状态贯通
- 前置条件 — 隔离库准备同专家两账号、历史出入站和六态job；通过04worker使其中FAILED任务retry后SENT。
- 操作步骤 — 1. GET列表。2. GET消息第一页和后续页。3. 先按账号、再按日期/主题/关键词筛选。4. 重发成功后重新查询。
- 预期结果 — FAILED/UNKNOWN各计异常1，WAITING/SENDING/CANCELLED计0；成功只出现一个稳定job主键，关联mail_record不再单列；日期仅限定专家入选，成功数仍按原全历史口径。
- 覆盖 — I-8/I-10，X-4/X-7，保持筛选/分页。

### A-2 审核与只读附件
- 前置条件 — APPROVED稿及有附件已发job，user1起初FULL。
- 操作步骤 — 1. 读取reviewContext。2. enqueue再读取。3. 新增真实来信再读取。4. 降user1为READ并GET消息。
- 预期结果 — 第2步token不变，第3步变化；READ仍见正文和状态但附件集合空、操作false；FULL下载地址使用真实mailRecordId。
- 覆盖 — I-7/I-8b/I-10，X-3/X-5/X-6。

### A-3 批量能力与附件完整性（A12）
- 前置条件 — 隔离库同页有多封临时失败任务，各自附件满足单封限制但合计超过单封限制；原actor具有FULL权限，另有READ身份。
- 操作步骤 — 1. FULL读取消息页。2. 删除或改写其中一份实际测试附件，再读同页。3. READ读取同页。4. 以带跨contact或跨上传者附件的隔离任务读取能力。
- 预期结果 — 第1步不误用整页限制，合法任务能力遵循04规则；第2步受影响任务canRetry=false，其他不受影响；READ附件空且动作false、不访问私有附件或磁盘；跨归属不能获得重发能力。页级SQL次数不随任务/附件数量逐条增长。
- 覆盖 — I-7/I-10，T2/T2a；复用既有路径/size/SHA安全检查而不放宽。

## 修正记录

- A12（2026-11-05 用户选择「授权本次及同类内部接口修订」）：新增OutboundAttachmentService.kt内部可见性单点授权，解决固定页级SQL与复用原文件安全校验的调用边界缺口。文件数7→8，READ/FULL权限语义未改变；详见T2a和05 epoch1审计。Master A11同步计数并记录后续同类内部接口修订的严格授权边界。无生产实现或测试结果随本计划修订产生。

