# 04 后台投递与操作API

前置03通过。后台执行、HTTP权限两个子系统，7个文件。不开启生产入队入口。

## 需求描述

持久任务到期后独立投递，浏览器关闭不影响执行；提供有权限的状态读取、撤回、重发接口。保持原SMTP分类、线程头、账号与附件验证；不接入批量、不引入RabbitMQ、不自动重发未知结果。

## 关键不变量

### Invariant I-1 — 截止时间与领取
- Rule — 到期判断采用数据库时间；独立线程有容量才尝试领取，不提前领取到本地队列等候。cancel、retry必须携带expectedRevision，重复请求不能延长due。
- Applies to — worker tick、action controller。
- Violation consequence — 未到期发出、工作队列滞留或旧点击操作新轮。
- 来源 — original。

### Invariant I-4 — 冻结内容与运行时重建
- Rule — rehydrate只重建账号/原件/来源实体并验证当前身份，SMTP正文、ICS和事实仍取冻结快照；重建后computeFingerprint必须等于原attempt full hash。不能再调用公开send入口。
- Applies to — Pending rehydrate/worker/retry preflight。
- Violation consequence — 二次排队或实际邮件变化。
- 来源 — K-manual-rich-final-body-single-seam。

### Invariant I-5 — 不确定结果不重投
- Rule — claim后进程中断、SMTP结果不确定或完成事务失败，最终收敛UNKNOWN；恢复只改状态，不再调用SMTP。持有同token的确定成功可按03收敛。
- Applies to — worker所有异常分支、恢复、重试能力。
- Violation consequence — 超时后重复邮件。
- 来源 — K-manual-send-unknown-must-converge。

### Invariant I-7 — 双层授权
- Rule — HTTP资源闸检查contact，service再核对job所属contact和actor/origin；worker以快照actor做当前授权、账号启用、真实收件人/线程、退订与附件检查。任何job JSON都不直接返回snapshot。READ只能看脱敏投影，不能取恢复正文/附件私有载荷或执行动作。
- Applies to — controller、ExpertMailPermissionGuard、rehydrate、restore DTO。
- Violation consequence — 跨联系人动作、撤权后投递、凭据泄露。
- 来源 — K-mailbox-readonly-needs-lifecycle-audit、K-manual-send-error-response-opaque。

### Invariant I-10 — 明确能力和公开原因
- Rule — canCancel/canRetry由服务端计算而非客户端状态猜测。UNKNOWN、永久失败、无快照历史记录均不能retry；临时失败只有当前源仍有效时canRetry=true。
- Applies to — GET/action response、public error mapping。
- Violation consequence — 用户重复投递或被原始SMTP堆栈误导。
- 来源 — K-manual-send-error-response-opaque。

### Invariant I-11 — 有界执行与恢复
- Rule — 每进程固定一个独立投递线程、SynchronousQueue无排队；@Scheduled仅提交短任务，忙则略过该tick。WAITING重启仍可领取；SENDING超600000ms仅标UNKNOWN，阈值不构成“肯定没发”的证明。入口enabled不控制worker。
- Applies to — executor启动/关闭、tick、recovery。
- Violation consequence — 占用共享调度线程或恢复重复发。
- 来源 — original；ExpertAcademicEnrichmentWorker:19–46先例。

## 现状审计

- `TalentIntroductionApplication.kt:9`启用调度；`ExpertAcademicEnrichmentWorker.kt:46`独立executor避免共享调度阻塞。现项目Rabbit为配置启用，不能假设生产必备。
- `PendingMailOperationService.kt:843–868`构造ComposedMail与同步SMTP，`:1591`分类；`SmtpConnectionRetrySender.kt`只对连接重试，不能把连接超时数值当总投递上界。
- 权限`ExpertMailPermissionGuard.kt:34–78`固定handler/verb映射，`:114–137`解析contact及附件owner；`MailReadOnlyPolicy.kt`接受isResourceHandler，由新handler注册即可进入资源闸，不增加泛路径白名单。
- 附件完整读写见总计划X-5：OutboundAttachmentService.resolveForSend重读文件及SHA。review来源与上下文见总X-3/X-9；新来信期间可能变化。
- 新API写路径只调用03状态机，读取新表/源草稿/当前授权。原发送存储写集合不变。交互为X-5原件→worker，X-6路由→service，X-8任务→重启，X-9真实来信→审核再校验。

## 实现方案

### T1 运行时重建（I-4/I-5/I-7/I-10）

在PendingMailOperationService新增internal rehydrateDelayed(snapshot,actor)及preflight，只依赖01模型和既有服务，禁止Pending→worker依赖。依次核验schema、来源/contact、accountCode启用、当前收件人与快照一致、来源真实线程锚点、权限、抑制、草稿/审核冻结身份、最新审核context、原件size/SHA。当前时间敏感校验（例如会议时段）仍执行原规则；不重新生成ICS/模板/QA。

恢复原ValidatedDraftSendRef/ValidatedReviewSendRef时通过各自服务锁定/验证，不能从未经校验的HTTP JSON制造trusted ref。preflight返回准备结果或固定失败码。等待期间失败使用03 preflightFailure，不写成功记录。retry在重新排WAITING前做同样验证；最终claim短事务还核验source version/context/权限，避免读取到claim之间已变更。

### T2 worker（I-1/I-4/I-5/I-11）

新增DelayedManualReplyWorker，构造线程名manual-reply-delay、maxPoolSize=1的ThreadPoolExecutor，SynchronousQueue，拒绝忙任务不记业务失败；@PreDestroy停止接收并有界等待，未完成靠UNKNOWN恢复。tick每1000ms，在独立线程扫描最多20个due id，逐个preflight→claim短事务→deliverPrepared；没有claim成功绝不SMTP。

恢复扫描使用单独短调度回调执行03 recoverStale，不能被唯一SMTP线程长期阻塞。它只扫描超阈值id并进行短CAS；采用同一锁序。捕获每个任务异常，日志只写job/attempt/token和服务端诊断，不把原文放到DTO。确定未开始SMTP的失败走FAILED；已claim但无法证明未发送时走UNKNOWN。finally释放本地忙标志，不把task删除。多实例由DB CAS防重复，不靠JVM锁。

### T3 API契约（I-1/I-7/I-10）

新增DelayedManualReplyController，路径均以 `/api/mail/mailbox/conversations/{contactId}/delayed-replies` 为前缀：

| 方法 | 后缀 | 入参/返回 |
|---|---|---|
|GET|空后缀|originKind/originId（LEGACY用processingId）查询当前源任务；仅当前actor或admin，固定上限20，返回公开投影，供刷新后找回revision|
|GET|`/{jobId}`|返回单任务公开状态，serverNowMs随响应生成|
|GET|`/{jobId}/restore`|仅原actor/admin且普通任务CANCELLED/FAILED；返回原输入，供旧编辑器显式恢复；REVIEW不返回可编辑副本|
|POST|`/{jobId}/cancel`|JSON `{expectedRevision: Long}`；返回新状态及source引用|
|POST|`/{jobId}/retry`|同上；先rehydrate当前校验，再03 retry；返回WAITING新due/revision|

公开投影字段固定：`id, revision, status, createdAtMs, dueAtMs, serverNowMs, originKind, originId, originVersion, canCancel, canRetry, publicErrorCode, publicErrorMessage, sentMailRecordId`。read-only移除恢复能力，动作能力false。restore额外返回expectedCancelledRevision/原输入；不得把整个内部evidence/credential对象直出。原样HTML只回给有编辑权限的原操作者并通过现有编辑器消毒规则载入。

异常契约：越权403；不存在或不属于contact为404；revision/状态冲突409且前端回读；到期撤回409 `DELAYED_REPLY_TOO_LATE`/「已超过撤回期限」；非法快照/请求422；超大快照413。保留现有全局异常处理方式，不另创HTTP200失败体。

ExpertMailPermissionGuard注册controller.get/list为READ_CONTACT、restore/cancel/retry为CONTACT；service再验证admin审核限制及job归属。list只允许精确源查询且核对源属于contact，不能枚举别人的任务。测试明确验证user1READ能GET但403 restore/action。

### T4 公开原因（I-10）

映射在01 Models的既定模型之上、具体方法放本次新controller或worker共同调用的03 service既有公开映射（不新增越表文件）。实现时03应已提供映射，本阶段只校验：

| 码 | 文案 | 原内容重发 |
|---|---|---|
|TEMPORARY_DELIVERY_FAILURE|发送暂时失败，可安全重试|重新校验通过可用|
|PERMANENT_DELIVERY_FAILURE|邮件被拒收，请修改内容或联系管理员|禁用|
|DELIVERY_UNKNOWN|发送结果待确认，请勿重复发送|禁用|
|REVIEW_CONTEXT_CHANGED|来信内容已更新，请先重新确认审核上下文|确认前禁用|
|ACCESS_CHANGED|权限已变更，邮件未发送|权限恢复且校验通过才可用|
|SOURCE_CHANGED|回复目标已变更，邮件未发送|旧源不一致禁用|
|ATTACHMENT_UNAVAILABLE|附件不可用，邮件未发送|完整性恢复才可用|
|ACCOUNT_UNAVAILABLE|发件账号不可用，邮件未发送|恢复并校验后可用|
|RECIPIENT_SUPPRESSED|当前收件人不可发送|禁用|
|SNAPSHOT_INVALID|发送内容无法恢复，请联系管理员|禁用|

## 变更文件清单

|#|文件|操作|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyWorker.kt|新增|
|2|src/main/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyController.kt|新增|
|3|src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt|rehydrate/preflight|
|4|src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt|精确handler映射|
|5|src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyWorkerTest.kt|新增时钟/投递/恢复测试|
|6|src/test/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyControllerTest.kt|新增HTTP矩阵|
|7|src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt|新增路由授权矩阵|

## 验收标准

- I-1 — 59999ms发送0次，60000ms首次允许claim；线程忙时不提前claim其他任务；过期cancel409。
- I-4 — 重启反序列化后的HTML/text/headers/ICS/附件顺序完全一致；通用附件篡改阻断SMTP。
- I-5 — DATA后断链、claim后进程中断、完成事务失败均UNKNOWN；恢复SMTP调用数不增加；迟到成功使用原token收敛。
- I-7 — HTTP路由真HandlerMethod测试，跨contact/actor、READ/FULL/NONE、审核admin矩阵；等待期间撤权/退订/账号停用/新来信测试。
- I-10 — 所有码只输出固定文案；堆栈、认证原文、内部路径不出响应；UNKNOWN与永久失败retry409。
- I-11 — worker容量1、无60秒sleep、共享调度不执行SMTP、disabled仍处理旧任务；两个worker对同job只一个SMTP。03真实MySQL并发IT一并运行。

## 人工验收清单

### A-1 关页面与重启
- 前置条件 — 隔离测试SMTP、03启动器排一封WAITING，记录dueAt。
- 操作步骤 — 1. 关闭所有页面。2. dueAt前重启服务。3. 检查测试收件箱。
- 预期结果 — 截止前0封；到期后首次空闲tick开始投递，最终1封；任务SENT。enabled=false也不阻断已存在任务。
- 覆盖 — I-1/I-4/I-11，X-5/X-8。

### A-2 API权限和期限
- 前置条件 — 普通任务属于user1，FULL授权；另建admin审核任务。
- 操作步骤 — 1. user1 GET两任务。2. 对审核任务POST cancel。3. 对普通任务到期后cancel。4. 降为READ再GET/restore/retry。
- 预期结果 — 可读状态GET200；审核action403；到期cancel409「已超过撤回期限」；READ GET200且能力false，restore/retry403。
- 覆盖 — I-1/I-7/I-10，X-6。

### A-3 未知与上下文变化
- 前置条件 — SMTP支持DATA后断链；另一份APPROVED任务等待中。
- 操作步骤 — 1. 投递断链任务后调用retry。2. 给审核目标新增真实来信，再等到期。3. 查看公开状态。
- 预期结果 — 断链UNKNOWN、retry409、SMTP不重复；审核任务FAILED，显示「来信内容已更新，请先重新确认审核上下文」，SMTP0封。
- 覆盖 — I-5/I-7/I-10，X-9。
