# 02 MIME附件、防重和邮件存档

依赖01；子系统：投递/尝试状态与邮件持久化，共2个；8文件；只给mail_record新增1列。原调用默认null，旧前端可独立部署使用。

## 需求描述

R1：邮件投递器可发送一份ICS附件，发送成功或安全失败时存档同一内容。R2：重复发送保护区分会议内容变化。
必须保留 M1：无calendar的正文/MIME/指纹与旧状态机；M2：首次外联配额、QA/RAG记录、专家材料路径。范围外：公开发送入口（03接入）、任意附件上传、重做SMTP重试/未知状态管理。

## 关键不变量

### Invariant I-1: 只有一列和一种absence
- Rule：mail_record.calendar_attachment_json LONGTEXT NULL，Kotlin calendarAttachmentJson:String?=null末尾追加；null=无日历。非null只能是01规范快照JSON，schemaVersion1，UTF8解析和hash必须一致；禁止空JSON当无附件。
- Applies to：V123/domain/finalizeSuccess和finalizeFailure四支
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：K-mail-record-source-inbound-id

### Invariant I-2: 发件身份包含日历语义
- Rule：无calendar时computeFingerprint原字节流完全不变；有calendar在原全部字段之后追加长度前缀meeting-calendar-v1和semanticSha256。禁止用随机UID/DTSTAMP或整个rawICS造成同意图重复发送。
- Applies to：SendPayload/computeFingerprint/prepareAndClaim
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：K-manual-send-fingerprint-complete-identity

### Invariant I-3: 投递内容与快照相同
- Rule：ComposedMail.calendarAttachment默认null；有值外层multipart/mixed，第一part包原alternative(plain,html)或text/plain；第二part为snapshot.icsText UTF8字节、text/calendar; charset=UTF-8、disposition attachment、安全filename。最终保存该同一实例的JSON，不再独立生成第二份。
- Applies to：SMTP/finalizeSuccess/finalizeFailure
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：original

### Invariant I-4: 状态机不分叉
- Rule：预留/认领在SMTP前，成功SENT、失败sentAt=null，UNKNOWN不重发、不伪成功；new/copy四支均明确snapshot。已有attempt DEDUP_SENT返回旧record，不覆盖已发送snapshot。失败安全重试按现有认领条件。
- Applies to：attempt所有分支
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：K-smtp-idempotency-reservation-before-delivery；K-manual-send-unknown-must-converge

## 现状审计
审计正文与原始检索是本节不可分割的组成部分：[完整审计](meeting-confirmation-audit.md)、[代码证据目录](meeting-confirmation-evidence/)、[源文件 SHA-256](meeting-confirmation-evidence/source-sha256.json)。当前工作区代码为证据；拟新增的接口/类型/样式均明确属于本计划决策，不宣称已经存在。已有 dirty 文件不清理、不覆盖。

D3全部attempt读写和ComposedMail构造、D4全部mail_record读写及schema为本阶段审计。IP-3/4/5：最终规范附件→指纹/MIME→快照→03下载；旧material统计仍仅mail_attachment。未改列的所有MailRecord构造默认null，不强迫无关调用改参数。

## 实现方案

T1（I-1）：新增V123 `ALTER TABLE mail_record ADD COLUMN calendar_attachment_json LONGTEXT NULL;`；不默认回填、不加JSON有效约束（应用01严格生成/读取验证）。MailRecord末尾加默认null属性。Flyway 10处最新断言122→123；23/24/116目标断言和旧checksum不改；增加schema列nullable、历史行null检查。

T2（I-2/I-4）：ManualReplySendAttemptService.SendPayload末尾加`calendarAttachment: CalendarAttachmentSnapshot?=null`；computeFingerprint原11字段后的两次appendLengthPrefix仅非null执行。SCHEMA_VERSION/content_type既有常量不整体改；calendar域标记使其不同于无附件；shortKey仍MANUAL_RICH:+32字符，符合VARCHAR50。快照JSON统一由01 CalendarAttachmentCodec.serialize生成，不给attempt新增必选构造依赖。所有4个mail_record分支写入`payload.calendarAttachment?.let{...serialize...}`；null显式清空该分支新写的snapshot，不沿用安全失败记录的旧附件。禁止动QA/RAG关系保存和sourceInboundId=null。

T3（I-3/I-4）：IntroductionMailComposer.kt的ComposedMail末尾加默认calendarAttachment。SMTP保留无附件分支逐字实现；有附件分支用javax.mail.internet.MimeMultipart/MimeBodyPart与javax.mail.util.ByteArrayDataSource绑定字节，禁止自拼整个MIME字符串。保持Message-ID、From显示名、In-Reply-To/References、List-Unsubscribe和错误分类相同位置。日历没有METHOD参数；不以text/plain/可见正文替代附件；没有重复第二个html/plain。校验快照hash/64KiB限制调用01 CalendarAttachmentCodec，不增加网络调用。

T4（I-1..I-4）：修改两个现有测试与Flyway测试；Mockito捕获真实MimeMessage并writeTo→MimeMessage重新解析，校验multipart结构/文件名/UTF8字节完全相等。重试测试覆盖安全失败后成功copy分支、初次new、DEDUP/UNKNOWN；含附件hash差异和无附件原golden fingerprint。没有公开入口前，用服务级测试完成独立验收，不人为直连专家邮箱。

## 变更文件清单

| # | 文件（仓库根目录相对路径） | 操作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql` | 新增一列 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt` | 末尾可空字段 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt` | 载体可空字段 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt` | 新增mixed分支 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` | 指纹/四支存档 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt` | MIME回归 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt` | 指纹/状态/快照 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 列迁移/最新版本 |

## 验收标准

- I-1：迁移旧行NULL；new/copy成功/失败4支均有值且解析为同一snapshot；未带calendar仍null；其它10个构造调用兼容默认值。
- I-2：冻结01语义值，generatedAt不同指纹相同；时间/URL/称呼/签名/账号/目标改变逐项断言不同；null指纹与修改前golden逐字相同。
- I-3：mixed恰好2part，首part alternative恰好plain/html两part，ICS MIME解码字节等于preview；纯文+附件与纯文无附件均验证；退订/线程/messageId保持。
- I-4：SENT不能再投、UNKNOWN不能重发、安全失败可认领；失败sentAt=null，重试成功唯一mail_record；旧外联/QA/RAG测试继续通过。
- IP-3/4/5：01生成器产物直接送入真实MIME构造和attempt finalize测试，不能分别手写两个相同假串来伪证集成。运行`mvn test`及隔离迁移测试。

## 人工验收清单

### A-1: 邮件容器与快照
- 前置条件：隔离数据库迁移至123；执行本计划SMTP测试所产的.eml fixture（测试保存到target/meeting-confirmation.eml），其内容由01样例真实生成。
- 操作步骤：1. 用邮件客户端打开.eml。2. 下载其中附件并与01样例同配置下载比较SHA256。3. 查看正文plain/html和附件名称。
- 预期结果：仅一个meeting-2026-09-11-Professor-Basdogan.ics附件；时间15:00–15:30中国；SHA相同；收件箱没有额外发送。
- 覆盖：R1；I-1/I-3；IP-3/IP-4

### A-2: 尝试行为与旧流程
- 前置条件：隔离测试数据，允许用测试报告/测试库只读查询；运行新增attempt场景，标注其测试attemptId。
- 操作步骤：1. 对同配置重复及改URL两种场景读取报告和对应attempt行。2. 查看安全失败后成功记录。3. 查看无calendar/外联回归报告和材料数量。
- 预期结果：同配置一次CLAIMED后DEDUP_SENT；改URL新短键；安全失败FAILED且sent_at为NULL，重试后同record为SENT；非会议calendar_attachment_json=NULL，材料数量未增加。
- 覆盖：R2/M1/M2；I-1/I-2/I-4；IP-3/IP-4/IP-5
