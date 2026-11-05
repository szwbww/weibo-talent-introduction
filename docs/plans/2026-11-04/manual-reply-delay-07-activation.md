# 07 人工入口接通与完整集成门禁

前置01～06及07-source-contract逐项通过。发送入口、发布配置两个子系统，11个文件（A4审核请求字段归03；A24来源恢复前置已完成；A27/A28十文件状态/响应契约；A29/A30单个必要既有Worker测试兼容）。此阶段才切换新请求行为；本计划不包含直接部署生产或发送真实外部联系人邮件。

## 需求描述

普通人工来信回复、人工跟进编辑器和「发送已审核回复」统一返回60秒等待任务，用户可在邮件底部确认撤回或重发。保持非目标发送路径、旧SENT幂等、权限/证据/附件、成功存证与只读行为。批量发送不接入。

## 关键不变量

### Invariant I-1 — 只在公共准备完成后入队
- Rule — enabled=true且当前请求是目标人工回复时，prepare完成后enqueue并返回WAITING；请求线程SMTP=0。身份检查和已完成去重仍在原位置，不因新开关绕过。
- Applies to — Pending两public入口、executeManualRichSend、review adapter。
- Violation consequence — 部分入口立即发送或排队未校验内容。
- 来源 — K-manual-rich-final-body-single-seam。

### Invariant I-3 — 兼容和取消代次
- Rule — 原SENT请求仍直接返回完成；WAITING重复请求不变deadline。新增delayExpectedRevision可空，首次允许空，CANCELLED重排必须匹配；HTTP不能传dispatchToken/verified refs/snapshot。FAILED只能显式retry。
- Applies to — 两普通request模型与controller、MailReviewSendRequest/Service。
- Violation consequence — 旧网络请求复活撤回任务或重复投递。
- 来源 — K-manual-send-fingerprint-complete-identity。

### Invariant I-5 — 仅事实完成才报成功
- Rule — WAITING响应不带伪sentAt/record/归档成功；成功事实由worker最终事务生成，归档失败保持SENT。UNKNOWN保持不可重发。
- Applies to — PendingMailSendResult、worker、前端消费。
- Violation consequence — 提交就计成功、用户重复发。
- 来源 — K-manual-send-success-transaction-and-dedup。

### Invariant I-7 — 审核与普通同样再校验
- Rule — review从APPROVED快照通过原prepareSend构造，延迟分支不能直接接受客户端正文。worker后续仍检查最新context/权限；普通legacy null用户仅为旧内部同步兼容，开启入队必须有真实会话actor。
- Applies to — review send adapter、HTTP session→Pending参数、enqueue。
- Violation consequence — 审核绕过或以空身份发信。
- 来源 — K-explicit-outbound-anchor-server-revalidation。

### Invariant I-11 — 开关与逐步发布
- Rule — 默认properties.enabled=false；完整功能验证后application配置显式开启，只影响新请求。关闭开关不能把已排队同attempt走同步投递，03守卫必须继续有效；已有任务继续worker/取消/查询。
- Applies to — application.yml、路由选择、回滚流程。
- Violation consequence — 旧任务丢失或回滚重复发。
- 来源 — original。

## 现状审计

- 目标public入口为PendingMailOperationService:162与397，公共claim在838；review adapter MailReviewSendService:10–23使用同public入口。对应HTTP UnmatchedInboundMailController:268、MailboxConversationController:425均显式传会话身份。
- 旧qa-reply (`UnmatchedInboundMailController:257–265`) 和composed-reply (`:364–372`)已经410 GONE，不是遗漏的可发送人工入口；继续保持410。
- PendingManualRichReplyRequest和PendingMailSendResult定义在PendingMailOperationService文件尾；会话request和DTO在MailboxConversationController；MailReviewSendRequest在MailReviewDraftModels。精确修改这些声明，不新增平行API。
- 现有application.yml已存在用户未提交修改，本计划实施时只增manual-reply-delay配置项，不覆盖文件。worker先例、数据库、附属表完整证据见总计划。
- 交互X-1旧claim兼容、X-2草稿、X-3审核、X-4成功事实、X-5附件、X-6权限、X-7前端、X-8重启、X-9新来信均由本阶段完整链路验收。它不增加新的存储写者，调用03/04。
- A24实证补齐：`sendConversationManualRichReply`原有null draftRef/optional anchor请求必须保留支持；旧LEGACY只接受inboundId的缺口由07-source-contract先行解决。原始nullable输入与actual anchor分别存储，恢复使用既有anchor参数明确原冻结线程；本阶段消费其契约，不重新设计来源或强制draft。完整grep与transport证据见`local://07-source-contract-amendment-evidence.md`。
- A28当前源码证据：MailReviewDraftController:99–110的JsonNode allowlist/构造遗漏已存在的delayExpectedRevision，导致前端当前代次在到达03守卫前被拒；其局部handler不覆盖DelayedManualReplyException，GlobalExceptionHandler:23–25会把既有409生命周期异常降成400。ManualReplySendAttemptService现findCompletedByPayload只返回SENT，delayedJobFor需要attemptId，Queued公开view故意不含reserved Message-ID，无法直接生成07所需的既有任务权威状态/响应。完整源码、生产/src/test grep和查询计数回执：`local://07-activation-contract-amendment-evidence.md`、`local://07-activation-contract-amendment-grep.json`；这些仅为pre-edit审计，不是运行通过证据。
- A28补充生产者审计：MailReviewDraftService的prepareSend/lockForSend→validateSendRow在读准备阶段拒绝WAITING/SENDING，导致重复审核请求无法到达03权威状态机；原LifecycleIT:879明确要求默认prepareSend仍409。完整src/main+src/test引用及read/dispatch矩阵见同evidence:64–134、`local://07-activation-review-preparation-grep.json`、`local://07-activation-review-all-references.json`；18匹配/5文件包含声明与同名噪音，并非18调用点。实际xd://lsp references返回`No language server found for this action`，已逐调用读补齐，不称LSP通过。仅read-preparation模式可变，同步claim与结果提交不变。
- A30实际运行兼容证据：指定JDK11的test-compile成功，随后Pending/新E2E/Snapshot/Controller/Worker及mysqlIt命令117项/0fail/36error/0skip。Worker9项的7error均为反射fixture尝试mock final ManualReplyDelayProperties；Pending49、Snapshot14、Controller16通过。新E2E29项均上下文扫描错误，未执行其行为，须在原授权IT内修复。完整命令/源码53–61/拟改唯一参数映射/其他反射消费者边界见`local://07-activation-worker-compatibility-evidence.md`和`local://07-activation-worker-compatibility-grep.json`；原输出artifact://2844（raw2848）。不重跑建立已知失败，不把这些结果记为通过。

## 实现方案

### T1 接通普通入口（I-1/I-3/I-5/I-7/I-11）

修改PendingMailOperationService：注入ManualReplyDelayProperties和DelayedManualReplyService（禁止service反向依赖Pending造成循环），公共准备成功后按enabled选择enqueue或02同步deliver。既有completedBinding/findCompletedByRequestId/findCompletedByPayload提前完成命中继续；对已有job任意开关状态都先查03守卫并返回其权威状态，不调用同步claim。

两public方法末尾新增可空delayExpectedRevision参数，PendingManualRichReplyRequest和ConversationManualRichReplyRequest也增加该字段；两个controller逐项传递，不能用operatorName冒充authenticatedUsername。首次提交为null，取消重排传当前值，任意negative/0为400。PendingMailSendResult末尾新增可空delayedReply（04公开投影）；WAITING messageId为已保留ID但不声称实际投递，archiveStatus=NOT_APPLICABLE、count=0。

新依赖Spring生产必须完整注入。为兼容旧直接构造测试可提供nullable默认，但只允许enabled=false旧路径；一旦enabled=true缺依赖立即启动/请求失败，禁止悄悄同步发信。

#### T1a 既有状态/响应的窄读取生产者（A28，I-1/I-3/I-5/I-7/I-11）

在既有ManualReplySendAttemptService添加 `internal fun findExistingByPayload(payload: SendPayload): MailSendAttempt?`：复用当前computeFingerprint，单次调用只执行一次既有findByOrcidIdAndMailType查询，存在则复用private requireFingerprintIdentity并返回既有immutable row，无新DTO/SQL/锁/写入/reserve/claim/finalize或SMTP。既有findCompletedByPayload仅按T2将其lockForSend读准备显式设为allowQueuedResult=true，其余顺序、检查、SENT查询/返回、指纹算法不动。这是A15已批准状态/响应字段的直接生产者补齐，不是将A11泛化为任意新lookup权限。

Pending仅用它识别已存在attempt，再用既有delayedJobFor读job；读取结果本身不授予权限或外发权。公共prepare、真实会话FULL、旧completed与来源/安全检查保留。enabled或已有job均进入原DelayedManualReplyService.enqueue及其actor/source/origin/matrix/revision守卫，flag=false也不得进同步claim绕队列。WAITING/SENDING/SENT、UNKNOWN、FAILED、CANCELLED均由现状态机决定，不在reader旁路拼成功。Queued的messageId取真实持久attempt值，不用新生成的fingerprint ID、不扩公共view；已有校验过的attempt可复用，确需读取新保留行时才再查，避免无用重复查找。

三个目标controller仅增加对既有DelayedManualReplyException的精确局部handler，原样保留其status/code/公开message；禁止catch-all、吞错、泄露cause或修改GlobalExceptionHandler。其它异常协议不动。

### T2 审核入口（I-1/I-3/I-7）

A4：复用03已实现的 `MailReviewSendRequest.delayExpectedRevision` 及 CANCELLED 准备校验，不在本阶段重新添加字段。MailReviewSendService仍先prepareSend取得冻结row/ref，再把revision传两条Pending方法。不接受html/text/body覆盖，不改稿版本和approvedContextToken。

review首次WAITING、重复同请求WAITING原due；取消后再次发送需当前job revision。刷新后通过04按origin查询定位job，不能仅靠浏览器内存得revision。REVIEW_CONTEXT_CHANGED失败后的retry需要现有confirmContext产生当前确认；03/04重建时只更新确认引用、不改正文指纹。

A28：MailReviewDraftController的既有send JsonNode allowlist接受可空delayExpectedRevision，非空必须positive integral且可转换Long范围，再传给既有MailReviewSendRequest；不另建request模型，不放行html/recipient/attachmentIds/trusted refs。旧expectedVersion/confirmation解析和admin/source权限不降级。此controller也按T1a精确转译既有delayed异常，保留409协议；直接HTTP用例写在已授权ManualReplyDelayEndToEndIT。

A28读准备边界：仅在MailReviewDraftService.prepareSend/lockForSend尾部加server-only `allowQueuedResult: Boolean = false` 并传给private validateSendRow。复用一次原queuedStatus读取；true仅允许WAITING/SENDING通过该队列检查，UNKNOWN仍拒绝，admin/原作者当前grant/deleted/version/APPROVED/真实target/内容相等/CANCELLED代次/context-confirmation校验全保留。仅MailReviewSendService.prepareSend与ManualReplySendAttemptService.findCompletedByPayload内lockForSend显式true；prepareAndClaim仍默认false，lockForSendResult及成功/失败提交不改，不向HTTP、ref或snapshot添加该flag。旧LifecycleIT默认queued409断言原样保留。新E2E必须证明true/false开关下重复审核同job/due且SMTP0；取得true读准备ref后直接同步claim仍409/SMTP0/job+due不变，UNKNOWN及作者权限撤销仍拒绝。该读取模式不授予外发权，生命周期enqueue/worker原门禁独立完整执行。

### T3 配置和可回退发布（I-11）

application.yml添加 `talent-introduction.manual-reply-delay.enabled: ${MANUAL_REPLY_DELAY_ENABLED:false}`，先默认false部署01～06；07联调通过后发布环境设置MANUAL_REPLY_DELAY_ENABLED=true，写发布操作记录（运行产物，不在本代码清单中）。不把生产开关自动改为true提交。发布前确认V174（或已核定替代号）应用、worker bean/线程存在、前端统一新资源键、三个入口WAITING、异步任务健康。

停用新入队时置false，旧任务仍处理；不得直接回滚到不识别delayed表的旧版本同时还有WAITING/SENDING/UNKNOWN。需回滚代码时先停止新入队，让WAITING正常完成或在期限内明确取消，确认SENDING/UNKNOWN人工收敛；历史表/快照保留，不执行drop/删队列。UNKNOWN没有证据证明未发时不重投。

### T4 完整集成（I-1/I-3/I-5/I-7/I-11）

新增ManualReplyDelayEndToEndIT，使用隔离Testcontainers MySQL、真实Spring事务代理/全部repository、可控SMTP测试替身（验证调用与消息内容，不连生产SMTP）。通过真实controller request+session→prepare→DB queue→worker→finalize→query链路；不mock掉核心事务。使用数据库任务时间fixture/可控Clock适配而非sleep60秒，边界准确到毫秒。最终另在隔离环境真实等待60秒做总A-1～A-12黑盒。

矩阵：INBOUND有draft、INBOUND legacy QA/RAG/assembly、OUTBOUND明确anchor、REVIEW有/无context confirmation；每类包含WAITING和成功。异常矩阵包含451、550、DATA断链、取消/claim竞争、数据库finalize回滚、重复POST丢响应、重启、撤权、退订、附件变更、新来信、关闭开关。

PendingServiceTest新增开关分支测试；原测试默认关闭，依赖配置不能使旧构造的测试无意走新链。真实IT执行与前端contract测试输出一起保存验收证据；验证不过只修本子计划范围或回到对应子计划，不临时扩大功能。

复用已通过07-source-contract的准备/restore契约；接通后必须额外证明：无draftRef且未指定anchor的首次conversation请求先WAITING；取消后原输入恢复再提交显式原actual anchor/当前revision而非较新邮件；不创建伪普通draft、不改旧正文指纹。新DTO的delayExpectedRevision与持久原输入分工保持前置C-1～C-7。必跑回归同时包含DelayedManualReplySnapshotTest、DelayedManualReplyControllerTest、DelayedManualReplyLifecycleIT（mysqlIt=true）及mailboxDelayedReply，前置通过不替代本阶段集成证据。

## 变更文件清单

|#|文件|操作|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt|入口分支/request/result|
|2|src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt|传递revision；精确delayed异常status/code|
|3|src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt|request/传递revision；精确delayed异常status/code|
|4|src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendService.kt|审核接通，消费03请求代次|
|5|src/main/resources/application.yml|新入队开关，保留已有修改|
|6|src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplyDelayEndToEndIT.kt|新增端到端IT|
|7|src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt|开关/响应回归|
|8|src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailReviewDraftController.kt|A28仅revision allowlist/严格校验/转发与精确delayed handler|
|9|src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt|A28纯internal findExistingByPayload及既有completed读准备显式true，其余不变|
|10|src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt|A28仅默认false的server-only队列状态读准备模式，保留严格同步claim/UNKNOWN/所有其他校验|
|11|src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyWorkerTest.kt|A30仅反射构造fixture为ManualReplyDelayProperties使用真实默认false实例，其余mock/override/全部断言不变|

## 验收标准

- I-1 — 三入口HTTP200+WAITING、SMTP0；due>=serverNow且首次差60000；满60秒后worker一次；批量/自动/模板直接发送没有新增delay行。
- I-3 — 老SENT请求不新排；取消后旧请求409、当前revision重新60秒；失败原send不能绕retry；开关关闭也不能抢旧queue。
- I-5 — 请求受理不生成SENT/排期；worker提交后record/QA或RAG/会议/draft/review/job一致；SMTP断链UNKNOWN，前端无重发。
- I-7 — REVIEW仍来源APPROVED服务端冻结数据；READ普通发送403、FULL可发、审核仅admin；null actor不能入队；已发落库不受事后降权影响。
- I-11 — false不产生新任务，true产生；false仍处理旧任务；重启WAITING继续；超时SENDING转UNKNOWN不重投；配置只新增目标键。
- A28/I-3/I-5/I-7 — 真实REVIEW首次仍允许空代次；取消后重排当前正代次可入队、缺失/陈旧409，非空非法类型/范围/非正数400；禁止字段仍拒绝，真实actor不丢。既有任务在flag=false仍过原状态机，跨actor/origin不能接管，FAILED只显式retry/UNKNOWN不能重发，Queued回真实reserved Message-ID且SMTP0；读取helper不claim，审核true读准备ref不能绕过默认同步claim guard或作者权限，重复WAITING/SENDING不重置due。原必跑命令不减，这些直接回归纳入新E2EIT及PendingServiceTest。
- 同时覆盖总I-2/I-4/I-6/I-8/I-9/I-10以及06 S-1～S-4，以总A-1～A-12逐条人工验收收口。
- 编译JDK11；FlywayMigrationIntegrationTest带migrationIt=true；MailboxConversationRepositoryIT带mysqlIt=true且隔离datasource；新Testcontainers IT与原draft/review测试必须实际执行。普通mvn test绿色不等于这些门禁通过。记录命令/环境/用例数/跳过数/失败日志，不能填预期结果冒充运行记录。
- A30兼容修订仅限既有Worker Runtime的构造器参数映射：override优先，其后ManualReplyDelayProperties使用真实默认实例，其他类型继续Mockito.mock；不改生产类型/默认开关/mock插件，不删断言。必跑命令保持Worker/Snapshot/Controller/Pending/新E2E与mysqlIt及原所有门禁；该单文件例外不授权其他生产或测试编辑。

## 人工验收清单

### A-1 三入口与非目标回归
- 前置条件 — 隔离SMTP与MySQL，环境开关true，准备来信普通稿、跟进稿、APPROVED稿；另有批量模板/自动回复/专家模板。
- 操作步骤 — 1. 分别通过三个入口提交并刷新。2. 在60秒前撤回一封。3. 其余等到成功。4. 分别执行非目标发送。5. 访问旧qa/composed端点。
- 预期结果 — 三入口先WAITING；撤回邮件收件0；其余各1封且成功后草稿/审核收敛；非目标无delay任务；旧端点仍410。
- 覆盖 — I-1/I-3/I-5/I-7，总N-1～N-4，X-1～X-7。

### A-2 发布停用与恢复
- 前置条件 — 开关true排两封，第一WAITING，第二通过SMTP测试钩子停在已claim后。
- 操作步骤 — 1. 开关改false重启。2. 查看第一封结果。3. 等第二封恢复阈值，尝试重发。4. 用原HTTP重复请求第一封。
- 预期结果 — 第一封仍按原任务一次完成；第二UNKNOWN，retry409不重复SMTP；原请求回SENT或既有状态，不同步再发。新请求按关闭后的原路径处理。
- 覆盖 — I-3/I-5/I-11，X-8；总A-8补充真实60秒与恢复证据。

### A-3 最终交付门禁
- 前置条件 — 01～07自动核验均实际通过，隔离环境与测试账号/SMTP完备。
- 操作步骤 — 1. 从总计划导出人工验收勾选文件。2. 执行总A-1～A-12。3. 每项填写操作者、日期、结果和截图/接口证据。4. 有失败先修权威plan/对应实现再复验。
- 预期结果 — 12项都有明确通过证据；未经执行项标未验收；全部通过前不标功能完成、不启生产开关。
- 覆盖 — 总I-1～I-11、S-1～S-4、所有需求与交互点。

## 修正记录

- A4（2026-11-05 用户批准，与总计划A2/子计划03 A3同一决定）：`MailReviewDraftModels.kt` 请求字段归属提前到03，本阶段删去该文件修改授权，仅在发送适配器传递现有 `delayExpectedRevision` 并完成全链路验收；文件数由8改7，其他要求不变。连续执行到可进行 review-fast-p，但不直接调用该技能。


- A24（适用用户已批准A15及后续子计划连续执行授权）：无稿OUTBOUND来源/恢复缺口在任何07产品编辑前发现，按文件上限拆出07-source-contract十文件前置；本计划仍仅七文件，前置完成后再按其终态Code head开始新实施，原pre-edit研究保留。所有三入口/60秒/重启/总A-1～A-12验收不降级，不静默要求draftRef，不在enabled=true时回落同步。此修订不重开已完成01～06，不执行review-fast-p。

- A27/A28（适用已批准A15「授权本次及同类契约补齐」）：pre-edit审计发现审核controller漏消费03代次且丢失delayed409；既有SENT-only lookup缺少任务/保留Message-ID响应生产者，审核读准备又提前拦截重复WAITING/SENDING。仅增MailReviewDraftController、ManualReplySendAttemptService、MailReviewDraftService三个现有文件（7→10），分别窄HTTP代次/异常协议、纯身份读取/既有completed读准备模式、默认false的server-only读准备模式。仅两个指定读取调用true，UNKNOWN、严格同步claim、其他权限/来源/context/版本守卫与结果提交不变；读取不授予外发权，不新增HTTP权限flag/公共projection/写路径，不以A11泛授权。原集成/真实60秒/重启/总A-1～A-12全部保留，同epoch实现继续，未发生独立核验修复轮。

- A29/A30（适用已批准A5「授权必要测试兼容修订并持续执行」）：具体必跑失败证明新增Pending构造器依赖与旧Worker反射fixture不兼容，仅授权DelayedManualReplyWorkerTest.kt的真实配置实例兼容，10→11为单个既有测试例外而非生产范围扩张。错误/命令/原文/完整grep见现状审计A30；全部行为断言、原命令与完整集成验收保留。新E2E自身上下文错误在其原授权范围修正，未冒充执行通过；同epoch1/fix0继续，不计独立核验修复轮。
