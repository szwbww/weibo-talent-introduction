# 05 · 通用附件进入SMTP与人工发件存档

状态：待审批；依赖02、04。发送与附件两个子系统，8文件；共享mail_record只新增1列。

## 需求描述

已有人工发件底座可发送原附件并持久化快照，可与会议ICS同时存在。保留无附件/仅ICS发送的线程头、正文、幂等、风险校验和失败语义。该阶段仅增加可测试的内部能力，06接HTTP输入，07接UI；不开放自动回复/首封群发附件配置。

## 关键不变量

### Invariant I-1: 唯一无附件表示与四写分支
- Rule: mail_record.outbound_attachments_json LONGTEXT NULL，无附件只能NULL；非空为04的有序有效快照JSON。SendPayload.outboundAttachments默认emptyList；finalizeSuccess/finalizeFailure的新建/copy四分支全部显式写本次快照（无附件显式null），禁止保留上一次尝试旧快照。其他生产者创建MailRecord默认null。
- Applies to: 人工发件所有finalize分支、迁移、MailRecord实体。
- Violation consequence: 失败重试残留附件、会话下载错版本。
- 来源: K-attachment-metadata-consumer-chain；现有calendar_attachment_json四分支模式。

### Invariant I-2: 发送身份包含完整有序附件语义
- Rule: 原hash编码保持，通用附件非空才追加长度前缀段outbound-attachments-v1、数量、逐项filename/contentType/byteLength/sha256；保持选取顺序。上传UUID/磁盘路径/上传时间不参与hash。改变文件内容、文件名、MIME或顺序改变完整hash；同字节同文件名重传不制造新发送身份。无附件原golden hash逐字一致，ICS semanticSha段保持原顺序。
- Applies to: computeFingerprint→prepareAndClaim。
- Violation consequence: 错误去重或网络重试重复发信。
- 来源: K-manual-send-fingerprint-complete-identity / K-smtp-idempotency-reservation-before-delivery。

### Invariant I-3: MIME原件与存档一致
- Rule: ComposedMail.outboundAttachments默认空，元素是04已验证快照及ByteArray。无ICS且无通用附件保留原MIME；任一存在时外层multipart/mixed，第一part保持原plain或multipart/alternative，之后先既有ICS，再通用附件按选择顺序。所有附件Disposition=ATTACHMENT，中文名称正确编码；不改变字节、不展开压缩包、不把任意文件按text/calendar发送。
- Applies to: SmtpMailDeliveryService.send。
- Violation consequence: 收件端看不到附件、原文丢失、ICS失效。
- 来源: original；当前SmtpMailDeliveryService.kt:49-100。

### Invariant I-4: 原事务与去重状态保持
- Rule: 原claim在SMTP之前独立提交，SENT/FAILED/UNKNOWN状态机不增加新状态；通用附件无语义不创建会议。成功邮件记录和02日历仍在同一finalize事务；04原件保存早于SMTP。失败也可存元数据，但对话与已发下载只展示SENT（06负责）。
- Applies to: 所有claim/finalize/SMTP返回分类。
- Violation consequence: 重复投递、误建日历或把失败误显已发。
- 来源: K-manual-send-unknown-must-converge。

## 现状审计

- MailRecord.kt:35只存在calendarAttachmentJson；V123添加同名单列。整个mail_record schema、ALL读写、迁移和脚本见[evidence.mail_record_all](meeting-mail-evidence.md#完整检索回执)；现有读仓储SELECT *兼容新nullable字段，计数/聚合/UNION显式字段读不会自动展示通用附件，因此06通过现有SENT记录批量读取点显式投影。
- 全部实体保存路径：ManualExpertMailService.kt:70；ManualOutreachTxHelper.kt:59/110；MeetingScheduleService.kt:144；AutoMailReplyService.kt:356/696/971/1169；ManualReplySendAttemptService.kt:352/435。只有最后两处改动，其他新增实体依靠nullable默认值；本次检索MailRecordRepository无UPDATE/DELETE原生写入，V24历史UPDATE只回填attempt链接，不重跑或改写。
- 读取分组：MailRecordRepository所有list/count/monitoring/anchor SQL；MailboxConversationRepository UNION与过滤；MailboxService/MonitoringService详情；ExpertContactManagementService旧详情；AutoMailReply/ManualExpert/ManualInitial/Pending发件上下文；LLM/RAG/训练/自动晋级/退信/材料解析消费者。全部方法位置已在回执逐项留存；其新字段需求均为“保持不使用”，新字段对话读取只在06。
- ComposedMail真实定义位于IntroductionMailComposer.kt:73，不是独立ComposedMail.kt；扩展带默认值的字段，MailDeliveryService.send签名不改。
- SmtpMailDeliveryService.kt:49 calendar==null原分支，:66 mixed分支；附件当前只支持ICS。ManualReplySendAttemptService.kt:101 hash，:329/:349/:411/:432 ICS四写点。
- IP-1：04 bytes/snapshot→MIME。IP-2：快照→hash/claim→成功或失败存档。IP-3：存档→06对话与下载。IP-4：同时ICS→02排期，通用附件不污染材料。

## 实现方案

1. 清单1/2/8（I-1）：V127只ALTER mail_record ADD outbound_attachments_json LONGTEXT NULL；无回填、默认空串或额外索引。MailRecord末尾增加默认null。最新版本断言126→127，显式历史版本保留；验证既有行新列NULL。
2. 清单3/6（I-1/I-2/I-4）：SendPayload新增04快照列表，统一调用codec得到本次snapshotJson；覆盖成功/失败×新建/copy。指纹追加独立版本段，字段必须长度前缀编码，不用带分隔符拼字符串。已有短键/UUID、calendar semantic段不动。用golden与单因素变化断言保护。
3. 清单4/5/7（I-3/I-4）：ComposedMail加入默认空载荷；MIME公共body构造只做必要提取，保留原无附件形态；用JavaMail解析生成邮件，验证multipart层级、正文双版本、ICS及各原件的原字节hash、中文filename。所有其他ComposedMail调用保持默认不改。
4. 测试使用真实mimeMessage序列化后再解析（不能只verify addAttachment调用）；不需要对这两项需求之外的SMTP配置或账号服务做重构。

## 变更文件清单

|序号|文件|操作|内容|
|---|---|---|---|
|1|`src/main/resources/db/migration/V127__add_outbound_attachments_snapshot.sql`|新增|mail_record增加唯一快照字段|
|2|`src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt`|修改|outboundAttachmentsJson默认null|
|3|`src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt`|修改|有序指纹、四分支存档|
|4|`src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt`|修改|只扩展ComposedMail默认空通用附件|
|5|`src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`|修改|混合MIME支持ICS＋任意原件|
|6|`src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt`|修改|指纹与成功/失败快照|
|7|`src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt`|修改|实际MIME结构和原字节|
|8|`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`|修改|最新126→127、列为空兼容|

## 验收标准

- I-1：四分支分别含2个附件和空列表验证；null保真，无[]/空字符串存档；旧生产者无参数仍可编译。
- I-2：原golden不变；同内容不同上传id同hash；改任一元数据/字节/顺序不同hash；并发claim不重发。
- I-3：0附件、仅ICS、仅通用、多通用＋ICS四组合；text/html、线程头、中文名、zip二进制逐项校验。
- I-4：现有prepare/finalize/UNKNOWN测试全通过；只附普通文件不触发排期服务；失败记录不产生SENT事件。
- JDK11 mvn test -Dtest=ManualReplySendAttemptServiceTest,SmtpMailDeliveryServiceTest；FlywayMigrationIntegrationTest -DmigrationIt=true。此阶段可以部署保持旧界面行为；新HTTP功能在06启用。

## 人工验收清单

### A-1：混合附件实收
- 前置条件：06/07完成；测试专家和SMTP沙箱；准备中文txt与zip并保存SHA。
- 操作步骤：1.选择两文件、生成会议确认并人工发送。2.在沙箱查看MIME/下载所有附件。3.打开会话记录。
- 预期结果：恰1封、3个附件（ICS＋txt＋zip）；txt/zip原SHA相同，ICS仍可导入日历；正文text/html完整，日历新增恰1场。
- 覆盖：I-1/I-3/I-4；IP-1/IP-3/IP-4。

### A-2：重复提交与文件变化
- 前置条件：06接口可用；使用有来信的人工回复，固定正文和同一文件元数据。
- 操作步骤：1.发送成功后原请求重提。2.重新上传同名同字节后以新上传id重提。3.把文件内容改一个字节，再正常发起新发送。
- 预期结果：前两次合计SMTP1封；新字节是新发送身份，再增加1封；历史第一封下载仍原字节。
- 覆盖：I-1/I-2/I-4；IP-2/IP-3。

### A-3：普通回信与失败保留
- 前置条件：沙箱可正常投递/模拟连接拒绝，06完成。
- 操作步骤：1.不带附件发普通回信。2.只带ICS发邀请。3.带通用附件模拟安全失败，再安全重试。
- 预期结果：普通信原格式与线程保持；仅ICS没有空通用附件卡；失败没有“已发送附件”展示，重试后只有1条SENT记录和一份原文件快照。
- 覆盖：I-1/I-3/I-4；IP-2/IP-4；保持行为。

