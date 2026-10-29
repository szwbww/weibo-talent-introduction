# Child 03 — Approved Execution Brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master
- Branch: fast/draft-review-master
- Exact plan: docs/plans/2026-10-29/draft-review-03-send.md
- Plan identity: sha256:f960f013f569b5de074722c605b41d5fa382da28c1637666b9de773c860515f7
- Approval: User invocation `/fast-p docs/plans/2026-10-29/draft-review-master.md` on 2026-10-29 authorizes execution of these exact plan bytes despite historical DRAFT headings. Do not amend plan bytes.
- Dependencies: 01,02; dispatch only after predecessor checkpoint.
- Product base: controller supplies the exact child_base_sha at dispatch; evidence HEAD may be later.
- Execution report: docs/plans/fast/draft-review-master/children/03/execution.md

## Global Constraints
Read the exact child plan completely from disk and use execute-p. Its authorized file table is exhaustive; no changes outside that table. Read docs/design.md before mail/conversation changes and applicable repository guidance. Master invariants I-23/I-24 apply: sequential incremental work; existing shared tables gain zero columns; preserve existing mail, admin drafts, restricted reader access, and unrelated user changes. No deployment, push, merge, history rewriting, or extra worktrees. No plan amendments without human approval. Evidence docs are separately authorized only in the designated fast-p report files; exclude them from product commits.

One writer at a time. Do not run build/lint/tests/formatters mid-flight; collect all required fresh commands and smoke proof once implementation is complete. Do not fix unlisted files or silently narrow requirements. Report PLAN_CONFLICT/BLOCKED with exact evidence if necessary. Full verification is deferred; do not invoke verify-p, review-p, repair-p or fix-v inside this loop. The controller will dispatch an isolated four-gate verifier.

## Runtime and Commands
JDK11: /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. Docker daemon is available via OrbStack. For MySQL/Flyway ITs set DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock and pass -DargLine=-Dapi.version=1.41 when needed. Run every command from the child plan freshly, all mandatory ITs enabled and non-skipped. Product smoke must exercise the changed API/UI, not only tests; use throwaway scenarios and clean those afterward. Browser evidence for 04/05 must use actual UI; do not substitute source inspection or fake localStorage. Test SMTP only; never production recipients.

## Authorized Requirements and Downstream Interface
The exact plan below defines all tasks, invariants, authorized files, public DTOs/methods, tests, styles, and acceptance. Its quoted historical source line numbers are pointers, not permission to replace newer predecessor code. Preserve exact planned DTO and API names for downstream children. Necessary predecessor execution evidence is available in earlier child artifact directories; read only the dependency outputs needed by this child, not later plans. Required source evidence is in docs/plans/2026-10-29/draft-review-evidence/.

# 03 · 已审核发送与对话更新确认

状态：DRAFT / 待用户批准实施。仅开发计划，不代表代码已实现或测试已通过。
基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`，2026-10-29 工作树；原有脏文件见 [状态快照](draft-review-evidence/worktree-status.txt)，不得覆盖。
证据：[逐字源码 E01–E31](draft-review-evidence/source-excerpts.md)、[数据库迁移链](draft-review-evidence/schema-chain.md)、[读写调用全集](draft-review-evidence/06-repository-call-sites.txt)、[SQL/存储引用全集](draft-review-evidence/05-all-store-read-write.txt)、[来源 SHA256](draft-review-evidence/sha256.json)。文中“新增/拟”都是设计，不声称已存在。

前置：01、02门禁通过；依次基于前一产物执行。

## 需求描述
1. admin 显式发送指定已审核草稿，未审核拒绝；审核通过本身不发送。
2. 批准后邮件对话有更新，确认最新对话仍适用后再点击发送；后来再次更新需再次确认。
必须保持：原人工回复、既有账号/QA/退订/身份/安全检查、SMTP幂等与未知结果处理。
不做：user1发送、自动发送、定时发送、审核正文直接改写、SMTP重构、全局消息版本锁。
本步8文件、2子系统（审核对象 + 既有人工发送），共享表新增字段0；可以独立部署，旧请求不带内部review引用不改变行为。

## 关键不变量
### Invariant I-11: 批准和发送分离
- Rule: 只有精确admin能调用审核发送；审批、确认更新、切换内容均SMTP=0。发送只能引用该id当前APPROVED冻结版本；客户端不能传正文/收件人/账号/附件/QA/RAG；服务器读取canonical内容和原target并重验联系人/目标归属。
- Applies to: /review-drafts/{id}/send、MailReviewSendService与公共发送seam。
- Violation consequence: 未审核外发或发送另一份正文。
- 来源: original

### Invariant I-12: 更新必须重新确认
- Rule: 发送前在prepareAndClaim同一事务内重新读当前contextToken；等于approvedToken可继续，否则要求有效CONTEXT_CONFIRMED事件：同draftId/version/admin，payload token等于当前token。没有/伪造/过期确认409 REVIEW_CONTEXT_CHANGED，SMTP=0。confirm-context本身不发送；用户再按发送才进入发送链。
- Applies to: context/confirm-context/send/prepareAndClaim。
- Violation consequence: 一次旧确认覆盖后续新来信。
- 来源: original

### Invariant I-13: 发送幂等及未知结果不绕过
- Rule: review引用=服务器构造的id/version/冻结内容/固定目标/确认凭据。无review的现有指纹字节不变；有review追加域标记review-draft-v1及id/version，避免和普通人工回复或另一份审核草稿混绑。prepare锁review行再操作attempt；bind与claim同事务。SENT重复只返回原结果；IN_PROGRESS/UNKNOWN不得创建新attempt或换requestId。只有既有安全重试状态可重试；复用该草稿固定send_request_id。
- Applies to: 两发送入口提前返回、findCompletedByPayload、prepareAndClaim、DEDUP_SENT、finalizeSuccess/Failure。
- Violation consequence: 双发、未知投递后盲目重发或把其它发送标为此草稿。
- 来源: K-manual-send-fingerprint-complete-identity；K-manual-send-success-transaction-and-dedup

### Invariant I-14: 真实发送结果与审计同事务
- Rule: APPROVED是审核状态，成功发送仍APPROVED；发送情况从绑定attempt及其唯一mail_record读取，不另造SENT审核态。finalizeSuccess同时写mail_record/attempt结果和SEND_SUCCEEDED事件；finalizeFailure同事务追加SEND_RESULT事件，保留正文；回调失败不能误报“尚未发送”。DEDUP及完成重查不重复事件。未知返回明确待确认，绝不自动重试。
- Applies to: claim/finalize/Detail投影。
- Violation consequence: 数据库已发送但UI诱发第二次发送。
- 来源: K-manual-send-success-transaction-and-dedup

### Invariant I-15: 普通人工回复保持原路径
- Rule: reviewSendRef为空时所有校验、指纹、附件、会议、QA/RAG、identity与安全确认语义不变；不加“已采用AI草稿必须审核”的残留闸门。管理员可继续自由人工回复，不能把审核权限扩展成所有admin邮件都需审批。
- Applies to: PendingMailOperationService两入口+executeManualRichSend，ManualReplySendAttemptService。
- Violation consequence: 普通回复功能退化或通用发送被审核功能绑架。
- 来源: K-ai-adopt-direct-send-no-residual-gates

## 现状审计
### 既有存储与交互边界（所有子计划共用的审计收据）
- `expert_mail_read_access`：V166 主键 `(username,expert_contact_id)`，contact 删除级联删授权。`ExpertMailReadAccessService.grant/revoke` 唯一业务写者；`getAccess/grantCount` 与 `MailboxConversationRepository.hasReadAccess/expertPredicates` 读取。新审核读写也实时检查授权，不新增授权缓存。E01/E02/E07。
- `mailbox_reply_draft`：V152 每个 owner/contact/kind/processing/scope 唯一；ACTIVE/SENT/DISCARDED。全部数据库写方法是 `MailReplyDraftRepository.insert/save/discard/bindAttempt/closeSent/releaseCompletedBinding`；全部读方法是 `findTarget/findOwned/list/summaries/lockOwned`，由 `MailReplyDraftService`、人工发送两入口及 `ManualReplySendAttemptService` 使用。`discard/closeSent` 清空正文，不能充当审核历史。E03/E04/E05；本功能不改此表及其状态。
- `mail_record`：V1 基表，V15 来源列，V23/V24 attempt 关联及唯一约束；当前映射见 `mail/domain/MailRecord.kt:8`，完整后续 DDL 见迁移链。`inbound_mail_processing`：V5 基表，V11 正文/处理列、V167 folder 唯一键；映射见 `mail/domain/InboundMailProcessing.kt:8`。会话唯一读取来源为 OUTBOUND mail_record + 已关联 contact 的 inbound_mail_processing，排除模拟账号，允许 disabled 账号；不能读 mail_record INBOUND 来替代 processing。E06；来源 K-mail-record-source-inbound-id、K-sender-account-enabled-scope。
- 上述两表全部业务写入口逐条列在 [写路径表](draft-review-evidence/write-paths.md)：AutoMailReplyService 的 processSingleCore/saveMailRecord/sendMeetingInvitation/confirmManualReviewWithBody/confirmProcessed/reclassifyFirstReplyAutoResponse；UnmatchedInboundMailService 的 bindToContact/markResolved；PendingMailOperationService.markResolved/cancelResolved；ManualExpertMailService.sendManualMailInternal；MeetingScheduleService.confirmMeetingAndEmail；ManualOutreachTxHelper.recordSuccess/recordFailure；ManualReplySendAttemptService.finalizeSuccess/finalizeFailure。直接 SQL 还包括 `InboundMailProcessingRepository.reopenManualResolved:71`、`scripts/reclassify-auto-responses.py:435,580`；历史迁移回填见迁移链。所有入口保持原语义，不为“对话版本”逐个加写钩子。
- 读入口全集按源码依赖与 SQL 扫描列在上方两份收据：会话/邮箱/监控、任务记录、人工/自动回复、AI/可信工作台/RAG、材料/附件/通知、退信、联系人管理/外联/排期/运营状态核对均消费这些表。新增上下文摘要只读同一会话 UNION；不改变上述读者。不要把这项新摘要当作全局邮件版本或更改 process_status 的依据。
- `mail_send_attempt`：V23/V24，唯一 `(orcid_id,mail_type)`、message_id；mail_record 的 attempt 外键唯一。写入口为 ManualInitialOutreachService.runIntroductionFromSnapshot、ManualOutreachTxHelper.recordSuccess/recordFailure、ManualReplySendAttemptService.prepareAndClaim/finalizeSuccess/finalizeFailure；repository insertIgnore/claimStatus/updateStatusAndError，加 CrudRepository.save。读取是上述发送服务及 MailReplyDraftRepository 关联读取；详见收据。审核发送只扩展既有人工发送 seam，不改公共 attempt schema。
- 无现成审核表/类/查询参数：`03-new-feature-absence.txt` 中 `mailbox_review_draft|MailReviewDraft|reviewPendingOnly` 0 命中。原型 localStorage、模拟 revision 不作数据库或权限证据。

### 必须覆盖的真实发送分支
- E23/E24：有来信 `sendManualRichReply` 先按原draftRef尝试完成收敛；后构造validatedDraft；E09：无来信 `sendConversationManualRichReply` 的 `findCompletedByRequestId` 提前返回发生在查新锚点之前。
- E12：公共 `executeManualRichSend` 在claim前还有 `findCompletedByPayload` 提前返回；E10：prepareAndClaim另有 DEDUP_SENT；E11：finalizeSuccess在REQUIRES_NEW中写mail_record、排期、attempt并关闭原草稿。失败写记录也在finalizeFailure独立事务。
- `PendingMailOperationService.kt:613–659` 已做变量渲染、文本/HTML换行规范化、20,000字符最终校验；现有邮件正文转换器不能误当安全清洗。冻结审核正文需在这一步之后比对，不能只在Controller比较rawHTML。
- X8：APPROVE冻结正文/目标 → service内部引用 → 最终payload/SMTP/已发记录；X9：任意邮件新增 → context → claim之前的确认；X10：SMTP成功/失败 → attempt事务 → 草稿详情与历史；X11：原人工回复/原草稿 → 无review引用分支回归。
- 无需更改被 `OperatorStatusWriteSeamGuardTest` 钉行号的 MailRecordRepository/UnmatchedInboundMailController；使用新controller调用原Service，因此不授权修改该guard。来源 K-line-number-guard-breaks-on-any-insertion。

## 实现方案
### T1：唯一审核发送入口（I-11/I-12/I-15）
文件：`MailReviewDraftController.kt`、`MailReviewSendService.kt`、`MailReviewDraftModels.kt`、`MailReviewDraftService.kt`。
新增 **POST /api/mail/mailbox/review-drafts/{id}/send**，严格请求 `{expectedVersion,confirmationEventId?,safetyWarningConfirmed=false,strongConfirmationText?}`；handler名 `send` **不进入reader白名单**。返回已有 PendingMailSendResult；未完成/冲突用稳定code，用户可重读detail查看结果。未知字段（例如html/recipient/attachmentIds）422，不接收review身份自报。原ManualSendSafetyBlockedException交给既有GlobalExceptionHandler:44返回原结构，不能被新通用catch吞掉；新controller对下游ResponseStatusException做局部状态码映射，保留4xx，避免落入全局Exception兜底500（现有全局未列ResponseStatusException专用handler）。不修改全局异常策略；测试覆盖安全阻断和目标422。
ReviewSendService先校验admin、草稿归属、APPROVED、version、未deleted、真实目标。INBOUND调用现有sendManualRichReply(target_id,固定accountCode,服务器subject/html/text)；OUTBOUND调用sendConversationManualRichReply(固定send_request_id,accountScope=固定accountCode,anchorMailRecordId=target_id)。attachmentIds=[]、meeting=null、QA/RAG/assembly均不传。来源邮件被移到别的专家、目标不存在、账号被删除/模拟器一律422，不静默换最新目标。
内部追加可空 `reviewSendRef` 参数到两入口/公共函数，非HTTP DTO，默认null保持所有旧调用点兼容。不得同时带draftRef和reviewSendRef，冲突422；原管理员草稿不被绑定或关闭。

### T2：冻结正文与最终载荷（I-11/I-15）
文件：`PendingMailOperationService.kt`、`MailReviewDraftService.kt`、`MailReviewDraftModels.kt`。
01保存时先Safelist，再调用现有 normalizeManualRichHtmlLineBreaks，text=htmlToPlainText后normalizeManualTextLineBreaks；subject.trim。新审核正文不支持模板变量（使用已有MailPlaceholderService.placeholderKeysIn及requireValidPlaceholders识别占位符（E29），发现任何变量在submit时422提示“请填写实际内容后提交”）；不是正则猜测变量语法。完整长度沿现有20,000字符finalValidationText口径做提交预检，防止“审完永远发不出”。
公共函数得到 renderedSubject/finalHtml/finalText 后对reviewRef的冻结三字段逐字验证，相异409 REVIEW_CONTENT_CHANGED，不更新approved正文。原身份提醒/退订/安全确认仍可阻断；用户可在既有安全提示里确认重提，review凭据必须一并保留。前端没有“自动补全称呼/模板/附件”写入已审核文本的入口；需修改内容时由user1另建草稿走审核。

### T3：事务绑定、上下文与重复收敛（I-12/I-13/I-14）
文件：`MailReviewDraftRepository.kt`、`MailReviewDraftService.kt`、`ManualReplySendAttemptService.kt`、`PendingMailOperationService.kt`。
1. SendPayload新增服务端内部可空review引用，computeFingerprint仅其非空时追加域标记+draftId+version的长度前缀；无review的原指纹测试必须逐字不变。无来信短键仍固定send_request_id，不再新造第二套幂等键。
2. prepareAndClaim：先锁review草稿再读其绑定attempt；SENT校验关联及内容后直接原结果，UNKNOWN/IN_PROGRESS返回既有阻断结果，不清绑定；安全失败可按原状态重试。未发送时对当前token和确认事件校验；不存在合法确认返回409。claim/bind/SEND_CLAIMED事件同一事务；不包SMTP在DB事务里。
3. 对两入口的completedBinding、findCompletedByRequestId，以及公共findCompletedByPayload提前返回：review非空不得误走“无draftRef”捷径。只能在同review/version/已绑定attempt关系校验成功时收敛。新路径的授权/所有权检查在返回旧成功前执行；不要先因自身成功发件让context变化而拒绝已成功结果查询。
4. finalizeSuccess、finalizeFailure：沿“review行锁→attempt”的锁顺序追加事件，成功事件与record/attempt同提交。`(draftId,attemptId,eventType,result)`在行锁下查已存在则不重插；同一安全重试的多次claim可以保留各次事件。失败不清正文；SEND_SUCCEEDED含mailRecordId，其他包含实际结果。普通draft逻辑保留。
5. Detail发送投影：`sendStatus`取attempt真实status或NOT_SENT，`sentMailRecordId`仅有效SENT关联时非空。SENT隐藏再次发送；UNKNOWN显示“发送结果待确认，请勿重复发送”。不缓存为本地已发送、不提供强行换requestId按钮。
6. confirm-context的事件是确认凭据，客户端不能仅传currentToken当许可；事件必须同actor/draft/version/currentContext。点击确认与点击发送分开。确认后又收到新邮件再发409并重新弹框；取消不产生确认事件。

### T4：发送集成验证（I-11–I-15）
文件：`MailReviewSendIT.kt`。沿 MailReplyDraftSendIntegrationTest 的真实Spring事务/JDBC与 mock MailDeliveryService 结构。覆盖：未审/失权/伪造payload；同草稿双请求一次SMTP；两个不同草稿同正文指纹不同；手工发送同正文不混绑；成功事务后HTTP后续异常重查结果；DEDUP、UNKNOWN、安全失败重试；确认后再来信；最终正文渲染差异；既有安全确认重提。
不使用生产收件人做自动化测试。

## 变更文件清单
| # | 文件（相对仓库根目录） | 操作 / 边界 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendService.kt` | 新增仅admin的审核发送协调入口 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt` | 新增服务器构造的审核发送引用及发送DTO |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt` | 发送绑定与事件insert；读取既有attempt结果 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt` | 锁定/验证/绑定/落审计的事务内辅助方法 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailReviewDraftController.kt` | admin send handler；局部异常映射 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 两条人工回复入口与公共执行函数传内部review引用，保护提前返回 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` | review可选指纹段、claim/finalize事务内关联 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendIT.kt` | 新增真实事务+SMTP mock覆盖重复/未知/更新确认 |

## 验收标准
- I-11：approve/confirm SMTP=0；DRAFT/RETURNED/SUBMITTED/send 409；user1 send403；APPROVED显式send只发DB冻结正文，无附件。
- I-12：旧token无确认/伪造event/其他draft event/其他version event全部409且claim/SMTP=0；确认再发送1次；确认后新来信再次409。
- I-13：并发2请求SMTP总调用1；SENT重试不新增mail_record；UNKNOWN不生成新attempt；无review指纹回归；两种提前返回和DEDUP分支实际触发，不只grep。
- I-14：注入event写失败验证事务回滚；SMTP成功但后续HTTP错误时detail显示SENT，重试不双发；所有历史仍有批准正文。
- I-15：现有有来信/无来信/AI采用/附件/会议/安全确认测试通过，普通请求不要求审核。

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailReviewSendIT,MailReplyDraftSendIntegrationTest -DmysqlIt=true
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest
node --test src/test/js/ragAdoptSendBridge.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxOutboundAttachments.test.js
```
本步测试中SMTP用mock；不能拿前端toast作为投递证明。

## 人工验收清单
### A-1: 批准不发、显式发送
- 前置条件: 测试SMTP收件箱；user1已提交“测试审核回复”。
- 操作步骤:
  1. admin批准，检查测试收件箱和往来记录。
  2. admin POST该id/send；随后重复相同请求。
  3. user1请求同一send接口。
- 预期结果: 批准后新增邮件0；第一次显式发送新增1；重复后仍1；user1为403；Detail仍已审核且sendStatus=SENT。
- 覆盖: 需求1、保持项；I-11/I-13/I-14；X8/X10。
### A-2: 新对话二次确认
- 前置条件: 已审核尚未发；在测试邮箱发新来信并执行检查回复直到时间线出现。
- 操作步骤:
  1. 调用send不带确认。
  2. GET context并POST confirm-context；不调用send，查收件箱。
  3. 再制造一封来信并检查回复，使用刚才的confirmationEventId发送。
  4. 取新context再确认，再发送。
- 预期结果: 第一次409；只确认新增发送0；旧确认再次409；新确认后手动发送新增1。
- 覆盖: 需求2；I-12；X9。
### A-3: 安全提醒与普通回复回归
- 前置条件: 测试账号有原管理员草稿，含已有会议或附件用例；准备能触发既有身份提醒的审核草稿。
- 操作步骤:
  1. 按原界面发送普通草稿并完成既有提醒。
  2. 调用审核发送，遇既有提醒确认后再提交同draft/version。
  3. 测试环境令SMTP mock返回DELIVERY_UNKNOWN再重复请求。
- 预期结果: 原人工回复无审批步骤，附件/会议照常；审核回复保留安全提醒；UNKNOWN显示待确认，重复SMTP次数不增加。
- 覆盖: 保持项；I-13/I-14/I-15；X10/X11。

人工验收开始时才导出同前缀 `-acceptance.md`。
