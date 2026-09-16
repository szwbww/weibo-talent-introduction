# 02 · 会议邀请发送成功后自动排期

状态：待审批；依赖01。一个发送子系统，5个文件；不新增共享表字段。

## 需求描述

人工回复中的“会议确认”邀请成功发送后生成一场排期。保留普通人工回复、无来信跟进、QA/RAG证据、安全确认、退订门禁、线程归属和既有幂等。只接结构化 meeting 请求，不从邮件主题/正文猜时间，不把普通“会议邀约模板”邮件自动识别为有日期的会议。

## 关键不变量

### Invariant I-1: 单次校验产物贯穿全链
- Rule: executeManualRichSend 的 validateAndBuild 同一次返回同时产生 calendarAttachment 与 meetingEvent；meetingEvent只含已校验startUtc/endUtc/zoomUrl，不接受浏览器额外排期对象。二者同有同无；不重复生成ICS或重新取当前时间。SendPayload新增meetingEvent默认null，不改变普通发送身份。
- Applies to: 来信人工回复→executeManualRichSend→SendPayload。
- Violation consequence: 正文、ICS、日历时刻不一致。
- 来源: K-manual-rich-final-body-single-seam / K-meeting-confirmation-generic-template-boundary。

### Invariant I-2: 成功落库原子生成
- Rule: finalizeSuccess 的REQUIRES_NEW事务内，在真实SENT mail_record保存取得id之后、attempt标SENT之前调用01 createFromSentMail；同一个事务提交。finalizeFailure及预览均不创建。无事件创建成功就不能返回整个发送成功。
- Applies to: 新邮件与原失败记录copy两条成功分支。
- Violation consequence: 邮件成功记录存在而日历丢失，或失败邮件占用排期。
- 来源: K-smtp-idempotency-reservation-before-delivery。

### Invariant I-3: 未知发送不得补发
- Rule: SMTP成功后若排期/DB提交失败，沿用现有DELIVERY_UNKNOWN与“发送状态未知，请勿重复发送”错误，不更改既有异常类别；不能自动再次SMTP，也不伪造成功排期。DEDUP_SENT返回已成功结果，不刷新已有排期、不覆盖人工改期、不复活已取消排期。会话requestId已完成的短路收敛继续在锚点重选之前。
- Applies to: 成功提交失败捕获、claim分支、重复请求。
- Violation consequence: 专家收到重复邀请或人工修改被覆盖。
- 来源: K-manual-send-unknown-must-converge / K-manual-rich-reply-anchor-must-be-real。

### Invariant I-4: 普通邮件兼容
- Rule: meetingEvent不额外进入hash，现有calendarAttachment.semanticSha256已经涵盖时间/链接；没有meeting的指纹golden、SMTP MIME、发件账号、绑定/配额、QA/RAG记录均保持。日历创建不会再发一次邮件或修改expert状态。
- Applies to: executeManualRichSend两入口、computeFingerprint、所有claim结果。
- Violation consequence: 普通邮件去重失效或流程副作用。
- 来源: K-manual-send-fingerprint-complete-identity。

## 现状审计

- MeetingConfirmationService.kt:31明确只读；:106-179生成预览、startUtc/endUtc/chinaTime及ICS。当前不写任何排期表。
- PendingMailOperationService.kt:147来信入口，:352会话入口，:476共同发送门；:543-580重算并校验SHA/正文；:626仅带calendarAttachment；:645 claim；:671 SMTP；:676 finalizeSuccess；:737与:791失败未知处理；:832 DEDUP_SENT。
- ManualReplySendAttemptService.kt:196 prepareAndClaim REQUIRES_NEW；:301 finalizeSuccess REQUIRES_NEW；:352 save mail_record；:367保存SENT attempt；:377 finalizeFailure。只有本类测试文件直接new该service（检索命令 rg -n 'ManualReplySendAttemptService\\(' src/test）。
- mail_record schema：V1基础、V6正文、V15账号/触发/来源、V23 attempt/error、V24 attempt唯一FK、V31索引、V101 task、V123 ICS。注意：现有Pending抛带409的ResponseStatusException，但GlobalExceptionHandler.kt:65会把一般异常映射500；这里的服务状态与实际HTTP状态不可混写。本阶段保持现有异常输出和禁止重发语义，不顺带修改全部旧HTTP错误映射。当前ICS四个赋值点:329/349/411/432，success/failure × new/copy齐备。
- 本阶段不新增mail_record或attempt列。既有mail_record写入为ManualExpertMailService、ManualOutreachTxHelper两处、旧MeetingScheduleService、AutoMailReplyService四处及本服务成功/失败；全部读取/迁移/脚本命中见[全量回执](meeting-mail-evidence.md#完整检索回执)。其他写入不具meetingEvent，维持原行为。
- attempt完整读写：MailSendAttemptRepository insertIgnore/findForUpdate/状态CAS/状态错误更新，人工发送prepare/finalize、首封ManualOutreachTxHelper、任务恢复消费者；全仓attempt_all回执为边界。只更改人工finalize，不增状态枚举或改其他attempt。
- 新排期表新增唯一写入口为finalizeSuccess→01服务；读取仍是01日历/摘要。
- IP-1：同一个服务端预览→外发ICS和结构化排期。IP-2：SMTP外部副作用→数据库事务/UNKNOWN。IP-3：来源去重→后续人工修改/取消不能被重发请求覆盖。

## 实现方案

1. 修改PendingMailOperationService（I-1/I-4）：将现有局部重算结果保存供后续payload使用，构造01的结构化时间输入；保留现有SHA、正文包含检查的先后顺序。不要新增第二条模板渲染/发送链；不要改变calendar semantic hash格式。
2. 修改ManualReplySendAttemptService（I-1～I-4）：SendPayload增加默认null的meetingEvent，构造器注入必需的MeetingCalendarService；finalizeSuccess按I-2接入。非空配对、来源邮件和UTC输入由服务校验；新/copy记录都在同一保存后的调用点处理。失败不调用。无空依赖兜底吞掉排期写入错误。
3. 修改两份现存单测、新增事务测试（I-1～I-4）：使用真实MySQL事务验证注入排期写失败时mail_record与attempt成功更新回滚；上层保留UNKNOWN。测试同源两次、先改期再重复发送、取消再重复发送；只mock SMTP，不mock事务提交。更新ManualReplySendAttemptServiceTest构造依赖，其原golden hash断言保留。

## 变更文件清单

|序号|文件|操作|内容|
|---|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`|修改|复用本次服务端重算会议数据|
|2|`src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt`|修改|成功事务写排期；扩展SendPayload|
|3|`src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`|修改|预览/失败/普通邮件不创建|
|4|`src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt`|修改|构造依赖、来源唯一与成功分支|
|5|`src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt`|新增|实际事务回滚与重试收敛|

## 验收标准

- I-1：UTC秒/起止/link与真实重算返回一致；伪造客户端previewSha在SMTP前400，无排期。
- I-2：只预览、保存草稿、SMTP安全失败、SMTP未知都0新增；SENT新增1；mock事件写异常必须使成功事务整体回滚。
- I-3：重提SENT请求SMTP调用总数1；改期/取消保持；UNKNOWN重试不调用SMTP。
- I-4：原golden hash逐字不变；普通来信和会话跟进、QA/RAG、安全两次确认、线程头已有断言全通过。
- JDK11 mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,MeetingCalendarSendIntegrationTest；事务用例显式启用 -DmysqlIt=true。校验模拟SMTP，无真实专家投递。

## 人工验收清单

### A-1：成功才有排期
- 前置条件：测试环境配置本地SMTP收件沙箱；已有可回复来信的测试专家；日期设2026-09-18北京10:00–10:30。
- 操作步骤：1.点击会议确认预览、填入正文，查询日历。2.人工点击发送，沙箱收到邮件后刷新日历。3.下载ICS查看时间。
- 预期结果：发送前0场；发送后1场，startUtc为02:00Z，ICS的DTSTART为20260918T020000Z；SMTP只收到1封。
- 覆盖：I-1/I-2/I-4；IP-1；自动排期与普通流程保留。

### A-2：失败与重试
- 前置条件：同测试专家；沙箱依次设置连接拒绝、正常投递、投递后断开连接造成结果未知；每组使用不同正文和日期。
- 操作步骤：1.失败时查日历。2.安全重试成功后重复同请求。3.未知分组重复请求。
- 预期结果：失败0场；成功组恰1场且同请求不再投递；未知显示“发送状态未知，请勿重复发送”，不新增有效排期，不再次SMTP。
- 覆盖：I-2/I-3；IP-2。

### A-3：排期变更与既有邮件回归
- 前置条件：A-1成功事件；03完成可用UI，未完成可调用01API；另备无meeting普通回复和无来信有SENT锚点会话。
- 操作步骤：1.把事件改到北京14:00后重提原成功邀请。2.取消事件后再重提。3.普通回复与会话跟进各发一次。
- 预期结果：第一次仍14:00，第二次仍CANCELLED；原ICS仍10:00且邮件正文未改写；两封普通回信不新建排期，原跟进锚点/安全确认仍生效。
- 覆盖：I-3/I-4；IP-3；保持原行为。
