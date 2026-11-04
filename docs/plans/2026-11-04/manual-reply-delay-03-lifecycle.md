# 03 事务状态机与草稿绑定

前置02通过。单一发送事务子系统，10个文件。入口仍关闭，使用隔离MySQL直接调用服务验收。

## 需求描述

建立排队、撤回、重排、领取及完成的原子状态迁移；撤回后普通草稿保留，已审核稿仍为APPROVED。保持旧指纹、同步发送、权限与草稿版本语义；不实现HTTP、调度或页面。

## 关键不变量

### Invariant I-1 — 排队时间
- Rule — 新排队事务读取数据库毫秒时间，dueAt加60000；WAITING重复请求不修改期限。CANCELLED重新提交必须携带该任务当前revision；没有代次的旧提交返回409。
- Applies to — enqueue/requeue/retry。
- Violation consequence — 旧网络包复活已撤回邮件。
- 来源 — original；总I-1/I-3。

### Invariant I-2 — 统一锁序与领取互斥
- Rule — 统一采用原审核授权行（适用时）→源草稿/审核稿→attempt→job的锁序；查询job以识别source可以无锁读取，但锁后必须重新核对。所有状态迁移和绑定在同一个事务。WAITING撤回需now<due，领取需now>=due，revision CAS使二者互斥。
- Applies to — enqueue/cancel/retry/claim/finalize/draft save/discard/reopen。
- Violation consequence — 死锁、撤回后发送、草稿失去关联。
- 来源 — K-smtp-idempotency-reservation-before-delivery。

### Invariant I-3 — 复用身份且阻断同步绕行
- Rule — reservation继续使用computeFingerprint；unique attempt只关联一个job和固定actor/origin。普通prepareAndClaim发现该attempt已有job时必须收敛或拒绝，不能直接把PREPARED改为IN_PROGRESS。只有claim成功生成的server-only dispatchToken可完成该轮结果。
- Applies to — attempt reservation、prepareAndClaim、job claim、finalize。
- Violation consequence — 老同步入口绕过60秒或抢发。
- 来源 — K-manual-send-fingerprint-complete-identity。

### Invariant I-5 — 发送结果原子收敛
- Rule — 已有最终事务加入job结果；SMTP期间不持数据库事务。UNKNOWN从不重新领取。恢复标UNKNOWN后，仅最后同一dispatchToken的确定成功回报允许UNKNOWN→SENT；其他迟到错误不降级SENT或覆盖新代次。
- Applies to — finalizeSuccess/finalizeFailure、recoverStale、preflightFailure。
- Violation consequence — 重复投递、旧失败覆盖真实成功。
- 来源 — K-manual-send-unknown-must-converge。

### Invariant I-6 — 草稿与审核状态
- Rule — 普通稿WAITING/SENDING禁止save/discard/reopen/重绑。取消只释放完全相同id/version/attempt绑定，不清正文/context。LEGACY的原始恢复载荷保留在取消任务，不强制写入另一个普通草稿。REVIEW保持APPROVED和原版本，写发送事件，不能改已审核正文。
- Applies to — draft全部写方法、review bind/result、cancel。
- Violation consequence — 自动保存覆盖待发内容，撤回丢稿或绕审。
- 来源 — K-reply-draft-single-target-is-not-review-history。

### Invariant I-7 — 操作权限
- Rule — 普通任务限原actor或admin，user1需当前FULL；审核任务限admin且校验原作者授权。取消不调用SMTP或归档。失败重试重新校验源内容/权限，不能把旧确认当作新上下文确认。
- Applies to — enqueue/cancel/retry/claim授权与review confirmContext。
- Violation consequence — 跨操作者取消或重发。
- 来源 — K-explicit-outbound-anchor-server-revalidation。

### Invariant I-10 — 六态与公开错误
- Rule — job六态与attempt映射如下；冲突状态fail closed，错误码只能取固定白名单。attempt枚举不扩展。
- Applies to — repository CAS、service结果投影、草稿/审核详情。
- Violation consequence — 错把UNKNOWN当可重试或泄露原始SMTP错误。
- 来源 — K-manual-send-error-response-opaque。

## 现状审计

- 完整schema、写者与读者集合继承总计划mail_send_attempt、两类草稿、mail_record/附属表章节及证据01/02/07；本阶段新增表写者仅本service与attempt最终事务。
- `ManualReplySendAttemptService.kt:280`的prepareAndClaim是REQUIRES_NEW，`:362–405`直接领取；不能从新的排队事务调用它进行reservation，否则会先提交半成品。`:447/545`的最终事务可继续作为完成入口。
- `MailReplyDraftService.kt:37–63` save未包行锁事务；`:66–77` discard只防IN_PROGRESS；`:142–169`bind/close有版本语义；`MailReplyDraftRepository.kt:37/43/47/95/103/110`为全部显式写点。需要统一守卫而不只禁用按钮。
- `MailReviewDraftService.kt:177–238` prepareSend/lockForSend/lockForSendResult/validateSendContext/bindSend/recordSendResult区分发送前权限与发送后落库。`MailReviewDraftRepository.kt:121–149` sendProjection/result事件只认识attempt状态，PREPARED目前被UI当作可发送。
- 交互X-1旧同步claim↔队列；X-2草稿自动保存↔绑定；X-3审核上下文/事件↔queue；X-4成功事务↔记录/事实/排期。X-6操作授权与结果落库采用不同闸口，实际已发结果不能因事后降权丢失。

## 实现方案

### T1 reservation与排队（I-1/I-2/I-3/I-6/I-7/I-10）

新建DelayedManualReplyService。在现有attempt service抽取MANDATORY的reserveDelayed，复用指纹、唯一冲突处理、Message-ID生成和payload比较，不调用REQUIRES_NEW prepareAndClaim。源行锁→attempt插入/锁定→job插入→绑定→审核事件必须同事务，任一步失败全部回滚。

新增enqueue(preparedSnapshot, actor, expectedCancelledRevision?)：首次PREPARED入WAITING；已WAITING/SENDING返回原投影且不变due；SENT返回完成结果；UNKNOWN拒绝；FAILED只允许显式retry，不能用原send请求绕过二次确认。CANCELLED只有当前revision匹配、相同actor/origin、源内容重新验证后才重排；失败返回409 `DELAYED_REPLY_REVISION_CONFLICT`。同一DRAFT/REVIEW有其他未决任务时拒绝换指纹另排，LEGACY也按真实目标与actor检查未决任务，不能换requestId绕行。

此处相同origin指kind/id和owner，而非要求普通稿version永远不变。DRAFT取消后可能自动保存产生新version：若同一draft当前完整校验后fingerprint仍完全相同，可在显式revision重排事务内更新origin_version及restore中的已验证draftRef/原输入，再绑定当前版本；冻结最终正文/附件不能变。若fingerprint改变，走新attempt/job，旧任务留CANCELLED。FAILED_SAFE_TO_RETRY明确retry同样允许同一源的更高版本，但必须重新证明完整payload相同；不同内容拒绝旧job retry，提示从编辑器提交新回复。REVIEW不适用普通稿版本更新规则，仍要求原APPROVED冻结版本。测试必须覆盖“撤回→保存相同正文→重新发送”和“撤回→修改正文→新发送”。

### T2 CAS状态表（I-1/I-2/I-3/I-5/I-10）

| 操作 | job前→后 | attempt处理 | 限制 |
|---|---|---|---|
|enqueue|不存在→WAITING|保留PREPARED|只冻结，不SMTP|
|cancel|WAITING→CANCELLED|保持PREPARED或FAILED_SAFE_TO_RETRY|now<due，revision匹配，释放普通绑定|
|retry|FAILED→WAITING|仅FAILED_SAFE_TO_RETRY可进入|源/权限/上下文再校验，due=now+60000|
|requeue|CANCELLED→WAITING|同一attempt|当前revision与源重新验证，不改正文|
|claim|WAITING→SENDING|PREPARED/FAILED_SAFE_TO_RETRY→DELIVERY_IN_PROGRESS|now>=due，生成token，事务一起提交|
|preflightFailure|WAITING→FAILED|明确未调用SMTP，置FAILED_SAFE_TO_RETRY|记录固定原因；修复前canRetry=false|
|finalizeSuccess|SENDING→SENT|SENT|现有成功附属写+job一起提交|
|finalizeFailure|SENDING→FAILED/UNKNOWN|现有classifier结果|FAILED仅明确失败，UNKNOWN绝不重发|
|recoverStale|SENDING→UNKNOWN|DELIVERY_UNKNOWN|claimedAt超阈值，同token，不调用SMTP|
|lateSuccess|UNKNOWN→SENT|DELIVERY_UNKNOWN→SENT|只有原token的确定SMTP成功，完整最终事务|

每次实际变更revision+1；读/幂等重复不增加。清理错误码仅在成功排入WAITING时进行。重复相同cancel读回CANCELLED作为幂等成功；旧revision不能取消后续WAITING。retry重复若已进入新WAITING返回409并要求回读，不能再延长。恢复和finalize先按同一源锁序进入，不能先锁job后锁draft。

固定公开码包含 `TEMPORARY_DELIVERY_FAILURE`、`PERMANENT_DELIVERY_FAILURE`、`DELIVERY_UNKNOWN`、`ACCESS_CHANGED`、`SOURCE_CHANGED`、`REVIEW_CONTEXT_CHANGED`、`ATTACHMENT_UNAVAILABLE`、`ACCOUNT_UNAVAILABLE`、`RECIPIENT_SUPPRESSED`、`SNAPSHOT_INVALID`。本阶段在新service实现04T4表中的逐字文案映射（只引用该静态协议，不依赖04代码）。内部错误仍写原attempt.error_detail，API不返回原文。

### T3 草稿全写路径与审核事件（I-2/I-6/I-7/I-10）

修改MailReplyDraftService及Repository：save、discard、重开已关闭目标均在事务中锁目标并读绑定job；WAITING/SENDING拒绝，UNKNOWN也保留不可改的未决发送边界。FAILED/CANCELLED按原已完成绑定规则释放后允许用户修改；新内容新指纹，旧失败任务再retry时必须比对原稿版本，过期内容不自动覆盖当前稿。bind/close/release增加精确绑定条件，维持原late-save保护。

普通详情sendAttemptStatus通过联表优先返回WAITING/SENDING/UNKNOWN；取消释放后恢复原状态投影，不新增草稿表列。审核sendProjection同理：WAITING/SENDING不可发送；CANCELLED投影可再次准备但必须携带queue revision。保留审核attempt绑定以追踪历史，通过job状态判断是否发送锁；不改APPROVED内容/版本。增加去重事件SEND_QUEUED/SEND_CANCELLED/SEND_REQUEUED，以job+revision标识，每轮最多一个。recordSendResult沿用既有去重。

审核confirmContext仅在非WAITING/SENDING状态允许重新确认；worker发现新来信而失败后，用户在原审核面板确认最新context，再retry。retry时服务端查找当前admin对相同draftId/version、当前contextToken的最新CONTEXT_CONFIRMED事件，重新验证后把其引用写入snapshot.confirmation；禁止自建确认事件，禁止复用其他操作者事件。不改冻结subject/html/text/指纹。

### T4 完成与恢复（I-2/I-3/I-5/I-10）

ManualReplySendAttemptService的finalize接受02的delayedRef，锁后验证job、attempt、dispatchToken和来源。SENT重复返回已存在记录；同token的UNKNOWN仅允许确定成功完成，不能让通用同步分支重发UNKNOWN。成功把02的QA/RAG/会议/草稿/审核与job一起提交。失败写FAILED mail_record仍sentAt=null，保持旧record读取语义。

DelayedManualReplyService提供recoverStale与preflightFailure短事务；已知投递前失败不伪造sentAt/排期。错误导致的FAILED仍保留恢复快照，后续消息读者以job为准；不要求必须存在mail_record。

## 变更文件清单

|#|文件|操作|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyService.kt|新增事务状态机|
|2|src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt|CAS与绑定查询|
|3|src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt|reservation/同步守卫/结果收敛|
|4|src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt|全写守卫|
|5|src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt|精确绑定与状态投影|
|6|src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt|发送锁/事件/上下文|
|7|src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt|队列投影/事件去重|
|8|src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyLifecycleIT.kt|新增真实MySQL并发测试|
|9|src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftSendIntegrationTest.kt|草稿/成功事务回归|
|10|src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftIT.kt|审核回归|

## 验收标准

- I-1 — 数据库时钟下重复enqueue due不变；取消后旧无revision请求409；显式新revision重排足60秒。
- I-2 — Testcontainers MySQL双连接屏障并发cancel/claim、save/claim、discard/enqueue、retry/finalize；一次赢家、无半绑定。事务中不得用sleep模拟60秒。
- I-3 — 相同指纹并发插入唯一一行；旧prepareAndClaim不抢队列；同目标换UUID不能绕未决锁；旧token不能完成新轮。
- I-5 — 注入QA/RAG/排期/job最后写失败，整个最终事务回滚；恢复UNKNOWN不调用SMTP；原token迟到成功一次收敛；重复结果不新增记录/事件。
- I-6 — 待发自动保存/放弃/重开409；取消正文、context、附件ID逐字保持；普通新稿不被旧任务覆盖；审核状态和版本保持APPROVED。
- I-7 — 当前FULL/READ/NONE、非owner、admin矩阵；confirmContext不能改正在等待的审核快照；最终结果不被事后权限变化丢弃。
- I-10 — 六态与attempt矩阵全部覆盖，非法组合拒绝。同期运行原attempt、draft、review测试，不能只做mock仓储断言。

## 人工验收清单

### A-1 状态与撤回
- 前置条件 — 隔离MySQL、enabled=false；测试启动器调用真实service排入一封有附件的普通稿，无worker。
- 操作步骤 — 1. 读回任务dueAt。2. 用相同请求再次enqueue。3. 调草稿保存/放弃API。4. 在期限内调用cancel。5. 回读草稿。
- 预期结果 — 重复dueAt相同；保存/放弃409；取消状态CANCELLED；正文和附件ID不变且可编辑；SMTP计数0。
- 覆盖 — I-1/I-2/I-3/I-6，X-1/X-2。

### A-2 审核与权限
- 前置条件 — user1有READ的APPROVED稿，测试启动器以admin排队。
- 操作步骤 — 1. user1尝试取消。2. admin取消。3. 查看审核详情及事件。4. 用旧提交重复入队，再使用当前revision提交。
- 预期结果 — user1为403；APPROVED和版本不变；SEND_QUEUED/SEND_CANCELLED各一次；旧提交409，新提交WAITING且新60秒。
- 覆盖 — I-1/I-3/I-6/I-7/I-10，X-3/X-6。

### A-3 完成事务与旧链
- 前置条件 — 隔离SMTP/数据库，有RAG及会议两份任务；可调用测试claim和deliver。
- 操作步骤 — 1. 到期领取并发送。2. 查事实审计、排期、草稿、审核结果。3. enabled=false通过原入口另发一封。
- 预期结果 — 每个成功任务一封SMTP、一条SENT、对应事实/排期一次；旧入口仍同步SENT，无新delay job。未知模拟不允许retry。
- 覆盖 — I-4（总计划）/I-5/I-10，保持旧链，X-4。
