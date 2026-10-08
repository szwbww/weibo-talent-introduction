# 02：把草稿关闭接入真实发送事务

状态：待评审，未实施。研究日期：2026-10-08。代码基线：`7c86599f85f6462e00a3fcd2c5a74f1ca57f013d`。

本文中的“现状”有仓库证据；表结构、API、800ms 防抖、1MiB 文本上限为本计划提出的设计值，不能当作已经存在的功能。证据目录：[源码摘录](mailbox-server-drafts-evidence/source-excerpts.md)、[文件 SHA256](mailbox-server-drafts-evidence/source-manifest.json)。

前置：01 已实施且独立验证通过。共享 `MailReplyDraftService.kt` 必须基于 01 最终版本修改。

## 需求描述

O-1：通过现有人工回复接口发送草稿，只有发送成功落库后才关闭发送的那个版本。
O-2：发送失败、结果未知、取消确认、浏览器离开、重复请求和发送期间产生新版本，都不误删稿；重试继续使用原有幂等机制。

必须保留 N-1：draftRef 缺省的既有客户端与内部调用、发送指纹、账号与线程锚点解析不变。N-2：原模板渲染、安全确认、QA/RAG、会议排期、附件校验和审计规则不绕过。

不做：新发信接口、SMTP 重试队列、重写发送状态机、修改自动回信/批量触达、跨所有邮件入口自动找稿删除。

## 关键不变量

### Invariant I-1: 引用只能指向本人同目标同快照
- Rule: 新增可选 draftRef={id,version}；真实 owner 仍从 Session 传入。首次发送前验证本人 ACTIVE、版本、联系人、目标账号与原始正文/附件/会议/QA 等本次发送投影一致。跟进稿虽走 conversation endpoint，仍可属于 INBOUND 目标，但 saved followUpAnchorMailRecordId 必须与请求 anchor 完全一致。已完成可信绑定的重试允许读取无正文终态，严格按I-6处理。引用本身不改变解析目标。
- Applies to: 两个 controller、两个 service 入口、公共发送前校验
- Violation consequence: 发送 A 却清掉 B、他人草稿被删除。
- 来源: original

### Invariant I-2: 兼容原指纹与校验
- Rule: draftRef=null 完全走原路径；SendPayload 新增内部可选引用，默认 null，不进入 computeFingerprint。所有 raw template 仍经过原校验/渲染，不能拿持久化快照直接当 finalHtml 发出；QA/RAG/安全确认/附件/会议不因有草稿跳过。
- Applies to: SendPayload、executeManualRichSend、legacy callers
- Violation consequence: 重复发信或绕过业务安全校验。
- 来源: K-manual-rich-render-before-send

### Invariant I-3: 发送关联先落库
- Rule: SMTP 前在 prepareAndClaim 的短事务中锁草稿、复核版本、复用现有 claim，并绑定 attemptId+发送版本。无事务跨 SMTP。相同草稿关联的 DELIVERY_IN_PROGRESS/DELIVERY_UNKNOWN 禁止另开新 attempt；允许继续保存更高版本内容。失败安全重试沿用原 requestId/fingerprint，不自建重试状态。
- Applies to: prepareAndClaim、草稿内部事务接口、并发发送
- Violation consequence: 刷新/多窗口丢失发送占用，重复投递。
- 来源: original

### Invariant I-4: 成功同事务按版本关闭
- Rule: finalizeSuccess 中 mail_record=SENT、attempt=SENT、会议排期与草稿关闭同一 REQUIRES_NEW 事务。仅 ACTIVE 且 owner/id/currentVersion==sendVersion 时转 SENT、version+1、清内容；currentVersion>sendVersion 则内容与版本保持，解除已完成绑定。存储错误回滚整个成功事务；版本变新是正常分支而非异常。
- Applies to: finalizeSuccess 的更新/新建 record 分支、内部 close 方法
- Violation consequence: 邮件成功记录与清稿不同步，旧版本清掉新稿。
- 来源: original

### Invariant I-5: 所有非成功保留
- Rule: 未进入成功事务的异常、FAILED_SAFE_TO_RETRY、DELIVERY_UNKNOWN、永久失败、安全确认取消都不清稿。仅 durable SENT 有清理资格；SMTP 已投递而落库失败属于结果未知，仍保留，不能自动重发。finalizeSuccess 之后的审计异常不能把已关闭稿复活。
- Applies to: 前置校验、finalizeFailure、异常处理、读取状态
- Violation consequence: 丢稿或未知投递被重复发送。
- 来源: original

### Invariant I-6: 成功去重也必须有证据
- Rule: early findCompletedByRequestId 与 DEDUP_SENT 不能绕过 draft owner/版本/内容一致性。已经完成且关联同 attempt/sendVersion 的终态允许幂等返回成功；另一 ACTIVE 新稿不能仅凭 requestId 相同被删。新 draftRef 没有可信完成绑定时走完整原发送指纹校验，DEDUP_SENT 仅完整匹配时按本次版本关闭且不发 SMTP。
- Applies to: 会话 early completed 分支、通用 DEDUP_SENT 分支
- Violation consequence: 旧 requestId 吞掉无关新稿。
- 来源: original

### Invariant I-7: 锁顺序与放弃
- Rule: 涉及草稿的发送事务统一先锁 draft，再访问/锁 attempt；不在持锁时网络调用。DELIVERY_IN_PROGRESS 的显式放弃返回409 DRAFT_SEND_IN_PROGRESS；DELIVERY_UNKNOWN 允许用户确认后放弃，但不表示撤回邮件。终态迟到保存按01拒绝。
- Applies to: claim/finalize/discard 的交互
- Violation consequence: 死锁、已放弃稿被迟到操作复活、误以为放弃等于撤回。
- 来源: original

## 现状审计

### mail_record / mail_send_attempt 与成功事务
- Schema：`V1__create_business_tables.sql` 创建 mail_record；`V23` 引入发送尝试关联，`V24` 的唯一约束确保 attempt 对应唯一 record；`V123`/`V127` 分别有日历/通用附件快照。具体迁移文件名和引用见 [完整存储收据](mailbox-server-drafts-evidence/mail-store-references.txt)。本步不改 schema。
- 写点按 repository 引用检索，不能只看 Controller：`AutoMailReplyService`（入/出站/失败记录）、`ManualExpertMailService.send`、`ManualOutreachTxHelper`（成功/失败）、`MeetingScheduleService`（会议通知）、`ManualReplySendAttemptService.finalizeSuccess/finalizeFailure`（既有行更新/新建）。（来源: K-mail-record-save-sites）其他 writer 继续不携带 draftRef，不参与清稿。
- attempt 写点：既有 campaign pipeline、`ManualOutreachTxHelper`、`MailSendAttemptRepository` 与人工发送 `prepareAndClaim/finalize*`；逐个save/claim调用见 [mail-record-save-calls.txt](mailbox-server-drafts-evidence/mail-record-save-calls.txt)，其中 `ManualInitialOutreachService.kt:1090` 也会直接写attempt。只在人工发送事务添加草稿协作，不改别的 claim。
- 读点：`MailboxConversationService` 的会话/消息列表、邮件与附件下载、线程锚点解析、调度/批处理、已有完成请求查找；读取 SENT/方向/联系人/attempt关联/附件 JSON 的行为不变。全部引用路径见收据，不新增按草稿查已发邮件的通用聚合。
- `ManualReplySendAttemptService.kt:233` prepareAndClaim 是 REQUIRES_NEW；`:339` finalizeSuccess 写记录、QA、会议排期后将 attempt 标 SENT；`:431` finalizeFailure 记录失败。
- `PendingMailOperationService.kt:398` 会话完成重试直接返回；`:717` claim；`:753` finalizeSuccess；`:909` DEDUP_SENT 直接返回。`SendPayload` 在 `ManualReplySendAttemptService.kt:43`；`PendingManualRichReplyRequest` 在 PendingMailOperationService 文件末部 `:1832`，不是独立 DTO 文件。
- IP-1：01 保存的版本/内容 → 发送请求校验 → claim 关联；IP-2：SMTP → finalizeSuccess/Failure → 草稿列表；IP-3：成功重试/去重 → 终态或较新草稿。

### 01 草稿存储（继承而非重新设计）
- Schema/CRUD/全部内部写点以 01 为准。新增消费路径仅本步两个入口与人工发送 service。
- IP-4：用户发送期间 PUT 新版本/显式 DELETE → claim/finalize 的锁与 CAS。版本大于 sendVersion 时清理绑定而不清内容。
- 事务 service 不能依赖 PendingMailOperationService，避免循环依赖；草稿 service 只依赖 repository 与已有只读目标/附件解析。

### 身份与发送边界
- `UnmatchedInboundMailController.kt:258` 与 `MailboxConversationController.kt:384` 已传 authenticatedUsername 给附件校验；复用，不能新增 body username。
- `ConversationManualRichReplyRequest` 位于 `MailboxConversationController.kt:112`，会话请求只有自由正文、scope、requestId、可选 anchor 和附件；不能把入站 QA/meeting 字段塞入该接口。
- `PendingMailOperationService` 已有可选 `OutboundAttachmentService?=null` 构造注入兼容旧单测模式。新协作者可同样默认 null，但 draftRef 非空必须 requireNotNull/fail closed；生产 Bean 必须注入真实对象，不能静默略过关闭。
- `ManualReplySendAttemptService` 的直接测试构造点在 `ManualReplySendAttemptServiceTest.kt`；真实 Spring 事务导入在 `MeetingCalendarSendIntegrationTest.kt`。见 [发送路径与构造检索](mailbox-server-drafts-evidence/send-paths.txt)，因此两者均列文件表。

## 实现方案

### T-1：可选引用与提交快照校验（I-1/I-2/I-5/I-6）
文件：`PendingMailOperationService.kt`、`MailboxConversationController.kt`、`UnmatchedInboundMailController.kt`、`MailReplyDraftService.kt`。

在两个现有请求 DTO 末尾加 `draftRef: MailReplyDraftRef? = null`（类型由01 Models 提供）；方法参数和内部 SendPayload 同样尾部默认 null，避免破坏原构造点。owner 只作为内部参数传递。

在任何 SMTP/claim 前，按现有客户端发送投影比较“本次请求”与“已保存版本”：
- subject、htmlBody、textBody 使用现有 sendManualReply 的相同 trim/空值规则，比较 raw 输入，不比较渲染后的正文。
- INBOUND：processingId、账号；RAG codes/fingerprint；会议 input/previewAttachmentSha256；附件有序 IDs。编辑状态/freeTextPreview 从已保存 qa.baselineText 与正文按原算法派生；禁止任意 QA 字段混入匹配稿的请求。
- OUTBOUND/显式跟进：contactId、scope、requestId、anchor（空与真实 id 明确区分）、自由正文、附件有序 IDs；不要求入站 RAG/meeting，因为该现有接口不接受它们。跟进草稿有不可发送的会议时沿用现有拒绝规则。
- 安全确认布尔/二次确认文本是发送时交互，不属于草稿内容版本；它们不引发自动保存或新 fingerprint。

版本冲突/内容不匹配在发信前返回409 `DRAFT_VERSION_CONFLICT` / `DRAFT_CONTENT_MISMATCH`；不替用户改存储，不接受客户端“强制删稿”标志。生成内部 validated ref，包括 owner/id/version/target；最终 claim 持草稿锁后复核，防止入口校验与 claim 之间被另一窗口修改。

### T-2：事务绑定、成功关闭与失败保留（I-2～I-7）
文件：`ManualReplySendAttemptService.kt`、`MailReplyDraftService.kt`、`PendingMailOperationService.kt`。

复用现有状态机，执行顺序固定：
1. 入口验证及原模板/安全/附件/会议检查通过。
2. `prepareAndClaim`：若有 ref，锁该 owner 草稿并复核版本/ACTIVE；已有绑定 attempt 为 IN_PROGRESS/UNKNOWN 时，返回原受阻结果，不生成新投递。然后执行既有指纹碰撞验证/claim。CLAIMED 或 SAFE_RETRY_CLAIMED 成功时写 send_attempt_id/send_version；commit 后才 SMTP。
3. 已有关联的安全失败：未改内容重试复用相同 attempt；修改内容后允许原规则产生新 attempt，替换绑定必须在确认旧 attempt 已是安全失败/永久失败后。IN_PROGRESS/UNKNOWN 禁止用内容变化或新 requestId 绕过。
4. `finalizeSuccess` 入口先按一致顺序锁 draft；执行原 mail_record/QA/会议/attempt 成功写入；调用01 MANDATORY close 方法。当前版本相等→SENT、内容四列 NULL、version+1；当前版本较新→保留 ACTIVE 内容/version，清已完成关联。SENT 终态保留完成 attempt/sendVersion 供同次响应丢失后的重试识别。
5. `finalizeFailure` 保留 draft 正文与版本；绑定提供当前原始 attempt 状态给前端。草稿记录不会因 transient exception 变 SENT/DISCARDED。
6. 放弃路径只增加与已有 attempt 的状态校验：IN_PROGRESS 阻止，UNKNOWN 可明确放弃；不加入“撤回”能力。成功与放弃并发依次锁草稿，后者发现版本变化返回409。

需要原始请求投影复核的数据放内部校验对象，不加入 fingerprint，不新增 mail_record/attempt 列。01 service 不改变其对外 API 结构。

### T-3：覆盖两条成功捷径（I-1/I-4/I-6）
文件：`PendingMailOperationService.kt`、`ManualReplySendAttemptService.kt`、`MailReplyDraftService.kt`。

- 无 draftRef 的 `findCompletedByRequestId` 维持现状。
- 有 ref 且存在同 owner/id/sendVersion 对应 completed attempt 的可信绑定：按终态/较新版本规则返回原 SENT 结果，不重复发送、不再清新版本。不要求已清空的终态还能拿出正文。
- 没有可信绑定的有 ref 请求不能从 early completed 直接清稿；走公共指纹/内容匹配检查。原 requestId 对应不同内容，仍按既有碰撞逻辑拒绝，草稿保留。
- `prepareAndClaim` 的 DEDUP_SENT 在完整指纹校验之后才可按本次 validated ref 关闭相同版本；该分支已经存在 durable SENT，关闭在当前短事务中完成，不调用 SMTP。若存在非本次绑定的 IN_PROGRESS/UNKNOWN，只返回原受阻结果，不“认领”其他请求并附加清稿责任。
- 不在 Controller 的 HTTP 成功回调另删表；前端只重读服务器状态（03）。

### T-4：真实事务与旧路径回归（I-1～I-7）
文件：四个测试文件见表。新集成测试 SMTP 可 mock，数据库/事务/CAS 不 mock；通过 latch 阻塞模拟投递期间编辑，故障注入模拟草稿更新失败导致成功事务回滚。既有会议集成测试导入01真实服务/repository与所需只读协作，不用 mock 事务主体绕过新路径。

## 变更文件清单

| 序号 | 精确路径 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 请求 DTO、校验与成功捷径 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 入站可选 ref 传递 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | 会话 DTO 与 ref 传递 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` | claim/finalize 事务协作 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt` | 发送快照验证及绑定/放弃协作 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 两种入口与捷径/旧请求回归 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt` | 构造依赖、指纹与状态分支 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt` | 真实事务依赖更新及会议回归 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftSendIntegrationTest.kt` | 新增草稿发送全链路真实 MySQL 测试 |

共 9 文件、2 子系统；不新增共享存储字段。01 的 Models 必须事先包含 MailReplyDraftRef 与内部关联 DTO，否则先修订01而不是悄悄加第10文件。

## 验收标准

- I-1：错 owner、错版本、同用户另一专家、不同来信/账号、篡改正文/附件/anchor 均不调用 SMTP、不关闭任何稿；正向普通入站、无来信出站、显式跟进分别覆盖。
- I-2：旧请求指纹黄金用例通过；raw template 确认经过原 validate/render；QA/RAG/会议/附件原验证失败仍失败，不删稿。
- I-3：同稿并发发送只一个 SMTP 调用；刷新读取 IN_PROGRESS/UNKNOWN 后不能换 requestId 另发；安全失败可原 requestId 重试；绑定与 claim 原子提交。
- I-4：成功事务更新/新建 mail_record 两分支均覆盖；发送版本关闭为 SENT；并发更高版本保留；草稿写故障使 attempt、record、会议事务整体回滚。
- I-5：前置校验异常、SMTP 安全失败、SMTP 未知、落库失败、取消确认均保留。模拟成功事务提交后审计失败，数据库仍 SENT，不能恢复正文或触发二次 SMTP。
- I-6：early completed 同可信绑定幂等；未绑定同 requestId 不同内容拒绝；完整匹配 DEDUP_SENT 零 SMTP、只关闭对应版本；重放旧 ref 不影响新稿。
- I-7：并发 discard/save/send 用真实 MySQL，无锁顺序反转；IN_PROGRESS 放弃409，UNKNOWN 明确放弃可终态；迟到 save409。
- 命令：`mvn -Dtest=PendingMailOperationServiceTest,ManualReplySendAttemptServiceTest test`；`mvn -Pmysql-it -Dtest=MailReplyDraftSendIntegrationTest,MeetingCalendarSendIntegrationTest test`。记录实际执行/跳过数量；集成测试 skip 不计通过。

## 人工验收清单

测试使用隔离数据库与测试收件人；通过 HTTP API 可先独立验收，UI 尚未上线不妨碍本步。

### A-1: 成功关闭与重试
- 前置条件: 01 API 保存 version=1 的完整可发稿；测试 SMTP 计数从0开始。
- 操作步骤: 1. 原人工发送 API 带 draftRef={id,version:1} 发送。2. GET 草稿。3. 重放相同发送请求。
- 预期结果: 首次返回 SENT，草稿 SENT/version=2、正文空；重试仍 SENT；SMTP 总调用1；列表无此 ACTIVE 稿。
- 覆盖: O-1/O-2、I-1/I-3/I-4/I-6、IP-1/IP-2/IP-3

### A-2: 失败、未知与安全确认
- 前置条件: 分别准备三个已保存测试稿；测试 SMTP 可配置安全失败与投递超时；另一个稿触发已有安全确认。
- 操作步骤: 1. 安全失败发送后重读草稿。2. 超时发送后刷新并尝试新 requestId。3. 在安全确认弹窗取消。
- 预期结果: 三份正文均保留 ACTIVE；未知结果不触发第二次投递；取消不发 SMTP。安全失败恢复 SMTP 后原 requestId 可成功重试。
- 覆盖: O-2、N-2、I-2/I-3/I-5、IP-2

### A-3: 发送期间继续编辑
- 前置条件: 保存 v1；测试 SMTP 暂停返回；两个 Session 使用同一账号。
- 操作步骤: 1. A 发送 v1。2. B GET 后保存正文“发送中的新修改”为v2。3. 释放 SMTP 使v1成功。4. GET 并列出草稿。
- 预期结果: 已发邮件内容为v1；草稿仍 ACTIVE/version=2，正文“发送中的新修改”；列表仍有1份。
- 覆盖: O-2、I-3/I-4/I-7、IP-2/IP-4

### A-4: 放弃与失败事务
- 前置条件: 准备一个 IN_PROGRESS 和一个 UNKNOWN 稿；测试故障注入可令草稿关闭 SQL 抛错。
- 操作步骤: 1. 对 IN_PROGRESS DELETE。2. 对 UNKNOWN 明确 DELETE。3. 另发一稿并注入关闭写失败。4. 读取发送状态与草稿。
- 预期结果: 步骤1为409 DRAFT_SEND_IN_PROGRESS；步骤2为DISCARDED；步骤3不宣称 durable SENT，原稿保留，不自动重发；失败成功事务不留下孤立成功排期。
- 覆盖: O-2、I-4/I-5/I-7、IP-2/IP-4

### A-5: 旧发送与目标/附件回归
- 前置条件: 准备不带draftRef的旧请求、入站稿、出站稿、跟进稿、模板占位符、RAG证据、已预览会议及本人/他人附件。
- 操作步骤: 1. 按原接口分别发送合法测试请求。2. 将draftRef改成另一专家/另一用户。3. 篡改草稿正文/anchor。4. 用他人附件或失效会议预览。
- 预期结果: 旧请求仍按原规则发送/去重；合法三类目标保持原线程；会议成功只生成1条对应排期；错误身份、内容、附件和会议均在SMTP前拒绝且草稿不清除。
- 覆盖: O-1、N-1/N-2、I-1/I-2/I-6、IP-1/IP-3

