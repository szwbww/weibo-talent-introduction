# 专家分级权限 · 精确资源守卫与人工发送

状态：DRAFT，待批准实施。基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`。仅计划，不代表实现/测试已通过。

证据：[审计总表](expert-access-evidence/audit.md)、[逐字源码E01–E37](expert-access-evidence/source-excerpts.md)、[schema迁移链](expert-access-evidence/schema-chain.md)、[检索收据](expert-access-evidence/store-usage.txt)、[SHA256](expert-access-evidence/sha256.json)。

前置：02；本步只开已具备服务端资源校验的精确方法，body方法留04。本文件是可执行边界，先读[总计划](expert-access-master.md)的协议/最终UI与依赖修正。

## 需求描述
FULL只可操作该专家的邮件、附件、单专家管理/排期；未授权与READ请求仍被服务端拒绝。

必须保持：管理员、认证首次改密、旧资源归属/路径校验、发送幂等和安全确认不变。

不做：不允许/api前缀放行，不解析任意请求body做通用权限框架，不开放AI训练/批量/全局账号。

## 关键不变量
### Invariant I-2: 精确接口与资源权限同时成立
- Rule: user1仍是user1，仅邮箱导航。认证开启时默认拒绝未列出的HandlerMethod+HTTP method；允许handler后仍查目标专家当前权限。READ只能读授权邮件/标签与审核草稿；FULL只操作该专家。不得按URL前缀、按钮可见性或请求体operatorName授权。管理员授权接口永不向user1放行。
- Applies to: AuthInterceptor/Policy、目标守卫、body专用校验、app请求闸门
- Violation consequence: 横向越权或变相全局管理员
- 来源: K-session-auth-does-not-imply-mail-authorization

### Invariant I-4: 由真实实体解析授权对象
- Rule: processing→contact、mailRecord→contact、tag→processing→contact、attachment三种owner→contact、schedule→contact、草稿owner+target均由DB解析。请求contactId必须与解析结果一致。expertId经现有Normalizer/IdentityResolver规范化后匹配FULL contact的orcid与当前层级；无匹配拒绝。同一专家共享档案/标签不是新增contact邮箱授权。
- Applies to: 所有新放行详情、编辑、文件与发送入口
- Violation consequence: 换id访问别人的邮件、附件或资料
- 来源: K-expert-document-ownership-chain

### Invariant I-8: FULL发送复用现有事务链
- Rule: FULL可直接人工发送，无需草稿审核；仍复用两个现有rich-reply入口及attempt幂等、附件/锚点归属、安全确认。鉴权位于业务副作用前，服务入口用authenticatedUsername再次校验；请求体operatorName不代表身份，user1审计写session名。已获准开始的SMTP请求不承诺被后续撤权召回；撤权后的新请求403。
- Applies to: PendingMailOperationService两个入口、旧草稿绑定、下载
- Violation consequence: 重复发送或绕过安全/资源校验
- 来源: K-manual-send-success-transaction-and-dedup

### Invariant I-13: 不扩大成全局管理
- Rule: FULL范围为当前专家人工邮件/附件资料/人工档案/标签/备注位置/排期/现有单专家状态管理；全局邮箱账号配置、批量发送、任务/AI训练、模板配置、全局专家搜索、人工接管分派、会议视频库不开放。自动收发/晋级/标签流程不受user1交互授权守卫影响。READ附件仍不可查看下载，邮件正文邮箱不做脱敏承诺。
- Applies to: 精确路由表、用户宿主菜单、后台任务
- Violation consequence: 无关功能扩权或后台任务中断
- 来源: original

## 现状审计
E01/E12/E15/E28/E29及审计§3/§4；默认白名单阻断全部写，附件有unscoped入口，不能只加controller路径。E30真实排期按event id，E31是当前UI调用。guard仅鉴权读取既有存储，不成为附件/资料写者。X1/X6/X7/X9。

全量读写清单是本计划的组成部分：[审计§1–§5](expert-access-evidence/audit.md)。其中每张表列明schema、显式写者、FK/后台写入、读取与X交互点；未列为本步变更的writer保持原逻辑，不能因grant扩权改变后台任务。

## 实现方案
1. 在ExpertMailPermissionGuard建立固定handler键→目标解析规则，输入为session、HandlerMethod、已解析URI变量及query；非法id400，缺资源沿既有404，无权限403 MAIL_WRITE_FORBIDDEN。AuthInterceptor仅在user1分支调用；AuthWebConfig注入。未知handler拒绝，非HandlerMethod不按前缀放行（I-2/I-4）。
2. 目标矩阵见总计划“精确准入表”：contact路径验证FULL；processing/mail/tag/file/calendar-event/draft id必须从真实行解析。下载先鉴权再读文件；所有已有document-owner、snapshot、realpath条件继续由原服务执行（I-4/I-13）。对依赖body的handler在04完成controller守卫之前**不加入白名单**。
3. PendingMailOperationService两条rich发送函数，在尝试绑定草稿、建attempt及任何SMTP之前，用现有authenticatedUsername再验FULL；user1 operatorName固定session值；原admin/内部自动调用契约保持，不能把内部null解释成user1授权。返回重复成功也先核对当前可访问目标（I-8）。只调用既有发送链，不改指纹/安全规则。
4. 旧草稿handler在04 SQL范围完成前保持拒绝；全局无目标工具只按总计划精确名单开放，不把sender-account/template/task/global contact list开放（I-2/I-13）。新增测试逐个验证guard解析和拒绝前副作用为0，既有Auth测试更新注册与构造依赖（I-2/I-4/I-8）。
5. 分阶段准入：UnmatchedInboundMailController.getUnmatchedDetail在04b第3项完成已批准的user1 candidates=[]/跳过suggestCandidates之前保持拒绝，不提前改该controller。pendingBadge及suspension GET/suspend/updateSuspensionReason/resumeSuspension在06个人owner SQL和挂起准入落地前保持拒绝，06再精确激活；最终能力不删减。
6. 对requestTransfers/downloadZip等已有FULL path contact的端点，只有原服务已在任何队列/文件副作用前校验全部成员属于该contact时才可在03开放；证明现有全成员校验并覆盖混入外专家id的拒绝。此类不是缺失body授权守卫的旁路；不读取inputStream、不改既有资源规则。其余真正依赖未完成DTO授权的方法仍等04。

仅以下文件可变更；每个任务同时受其引用的不变量/样式契约约束。共享文件按本步前置版本接续，不覆盖上一阶段改动。

## 变更文件清单

| # | 路径 | 工作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt` | 精确handler方法/verb白名单分组 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptor.kt` | 调用资源守卫，固定403 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt` | 注入守卫，保留auth开关 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt` | 新增固定业务资源解析，不建通用RBAC |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 两条人工发送入口根据session再验FULL |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt` | 现有认证默认拒绝回归 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt` | 真实配置注册及reader边界 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt` | 新增归属与发送副作用验证 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperArchiveControllerTest.kt` | 仅非空安全依赖构造接线，保留原断言 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt` | 同上 |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsIT.kt` | 同上 |
| 12 | `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMaterialArchiveFlowTest.kt` | 同上 |
| 13 | `src/test/kotlin/com/weibo/talentintroduction/document/controller/ManualExpertMaterialUploadFlowTest.kt` | 同上 |
| 14 | `src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt` | 同上 |
| 15 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt` | 同上 |
| 16 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftSendIntegrationTest.kt` | 同上 |
| 17 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendIT.kt` | 同上 |
| 18 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt` | 同上 |
| 19 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentServiceTest.kt` | 同上 |
| 20 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 同上 |
| 21 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | 同上 |
| 22 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ReplyIdentityServiceTest.kt` | 同上 |
| 23 | `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt` | 仅必需非空guard bean/context接线，保留稳定安全与业务断言 |
| 24 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryIdentityRecoveryControllerTest.kt` | 同上 |
| 25 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt` | 同上 |
| 26 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckControllerTest.kt` | 同上 |
| 27 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt` | 同上 |
| 28 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewCountsControllerTest.kt` | 同上 |
| 29 | `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoControllerTest.kt` | 同上 |
| 30 | `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt` | 同上 |
| 31 | `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt` | 同上 |
| 32 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/EmailSuppressionControllerAuthTest.kt` | 同上 |
| 33 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationControllerTest.kt` | 同上 |
| 34 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactNoteControllerTest.kt` | 同上 |
| 35 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertInboundNotificationControllerTest.kt` | 同上 |
| 36 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailOpenTrackingControllerTest.kt` | 同上 |
| 37 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 两个MVC slice均补必需guard，保留稳定安全与业务断言 |
| 38 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt` | 仅必需非空guard bean/context接线，保留稳定安全与业务断言 |
| 39 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt` | 真实guard/依赖接线；仅退役已被03取代的FULL临时全局写禁用断言，保留NONE/READ/跨专家/admin/撤权及拒绝零副作用 |
| 40 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt` | 仅非扫描manual context必需guard singleton接线 |
| 41 | `src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplyIdentityDefaultsIntegrationTest.kt` | 同上 |

合计 **41 文件**；I-14例外仅为14个直接构造fixture加19个确需bean接线的MVC/manual context（完整证据见Epoch2），原5个产品文件及auth/mail产品子系统不扩大，新增共享store字段0。除下述明确取代的临时断言外，不弱化既有权限/业务断言、不加入nullable/default安全依赖。

## 验收标准
- I-2：真实AuthWebConfig+MVC逐个运行路由矩阵：NONE/READ/FULL/跨专家/admin；403之前SMTP、ES和文件流读取调用为0；未列端点仍403。
- I-4：用A的路径搭配B的processing/tag/attachment/schedule/draft/expertId；均无副作用。三种附件owner各测一次，保留原件路径和快照校验。
- I-8：SMTP mock证明FULL一次发送、READ/NONE/换专家0次；重试/DEDUP_SENT/DELIVERY_UNKNOWN维持原语义；user1审计不是body伪造的admin。
- I-13：管理员全路径回归；user1无法打开其他tab及全局端点。定时收信与自动标签仍执行，READ附件响应仍空且直链403。

门禁：JDK11 mvn test -Dtest=AuthInterceptorTest,AuthFlowIntegrationTest,ExpertMailPermissionGuardTest；现有人工发送与附件测试回归。真实MVC必须加载AuthWebConfig，不能只用孤立controller。
追加上述直接构造fixture原测试回归（DiscoveryApolloSettingsIT、MailReviewSendIT等按其实际门禁显式启用，目标不得跳过）；保持原管理员/内部调用、附件、审核、幂等断言。阶段性尚未开放能力以实际03运行smoke记录，不新增钉死临时阶段缺省状态的永久契约测试；06在既有owner IT中以真实AuthWebConfig验证延迟路由激活。
新增context fixture亦须原测试回归，实际opt-in门禁显式启用、目标零skip。ExpertMailReadAccessIT:425–459原READ/FULL循环中的FULL follow/rich-send统一403是02临时禁用状态，随03正式开放必须退役；保留READ403、NONE/跨专家403、授权管理admin-only、live等级、撤权及拒绝零副作用，FULL正向/幂等由本步原测试证明。若仅旧全局MAIL_READ_ONLY文字与本步明确MAIL_WRITE_FORBIDDEN契约冲突，退役旧文字断言而不重钉措辞，保留403及资源/副作用安全断言；不得据此删除稳定权限检查。

测试环境使用隔离MySQL/测试SMTP与临时附件目录；不得把mysqlIt连生产库。跳过/未运行必须记NOT_RUN，不能写PASS。

## 人工验收清单
### A-5: 同专家直接发送及越权拒绝
- 前置条件: 甲FULL、乙READ、丙未授权；测试SMTP收集器启用；准备各自附件/排期/来信id。
- 操作步骤:
  1. user1对甲写人工回复并显式发送。
  2. 把请求中的来信/附件/锚点/排期id分别换成乙或丙。
  3. 对乙尝试发送。
- 预期结果: 甲收到一次测试发送记录；审计user1；越权请求403或既有资源不匹配错误，无新增SMTP/文件内容；乙没有发送能力。
- 覆盖: I-2/I-4/I-8/X6/X7

### A-6: 三种附件归属与材料操作
- 前置条件: 甲FULL，准备record-owner/processing-owner/manual-upload-owner各一附件；丙未授权，准备其附件。
- 操作步骤:
  1. user1从甲材料drawer预览/下载三类文件，上传一个测试附件。
  2. 尝试丙文件直链、把甲path搭配丙attachmentId。
  3. 降READ后刷新并重试甲直链。
- 预期结果: FULL甲三类原件可读；丙和错配不返回文件字节；降READ后甲直链403、附件投影为空；上传本身不产生SMTP。
- 覆盖: I-4/I-8/I-13/X6

### A-13: 管理员与自动流程不回归
- 前置条件: admin现有人工草稿/挂起/筛选与测试自动收信可用；准备禁用真实邮箱和SIMULATOR。
- 操作步骤:
  1. admin按原流程保存并发测试草稿、查看附件、筛选挂起。
  2. 执行测试收信、自动标签、UID补收。
  3. 查看全局来信汇总及个人待处理。
- 预期结果: 旧人工草稿/幂等/附件保持；自动流程不因无user1 session失败；禁用真实邮箱邮件仍可见、SIMULATOR不计；全局汇总保留所有人，个人待处理分归属。
- 覆盖: I-5/I-7/I-8/I-12/I-13/X3/X4/X7

本节为权威清单；进入人工验收时才导出同名前缀-acceptance.md，本次不生成衍生表。涉及尚未到本步的UI，使用同会话API执行等价动作并在最终08再次通过UI验证。

跨阶段人工项引用的其他I/S条款，以总计划同编号定义为准；本步实现仍严格限定本文件清单。

## 修正记录
- 2026-10-30：按用户持续授权，非空安全依赖必然影响14个未列直接构造fixture；完整grep工具pattern/path/output及路径清单见fast-p children/03/execution.md Epoch1。追加上述仅接线文件，8→22，明确I-14有界例外，不引入nullable/default shim或测试权限旁路，不修改原业务/断言。原请求中的第23个产品controller不提前纳入：04b第3项已有getUnmatchedDetail去候选职责，03先拒绝该handler。
- 2026-10-30：pendingBadge/suspension原SQL无owner条件（同报告含逐点收据），按I-2/I-7/I-13在03保持拒绝，06完成既定owner SQL后再激活精确方法；对应06名单追加policy/guard，仅用于该激活。材料批量端点沿已存在全成员同contact校验，必须以混入越权id证明副作用前拒绝。没有降低最终08/人工验收。
- 2026-10-30：非空依赖完整Spring消费审计还发现19个真正缺guard的context fixture（17个WebMvcTest、2个无扫描manual context），而完整ComponentScan/无AuthWebConfig测试不纳入；完整grep/逐文件最小import或注册证据见children/03/execution.md Epoch2。按持续授权补这19文件，22→41，仅必要fixture接线，产品仍原5文件。另明确退役ExpertMailReadAccessIT的02临时FULL全局写禁用pin，稳定拒绝/跨资源/管理权限/撤权断言不减；不借fixture例外扩大产品。
