# 06 · 双人工发送入口与会话下载贯通

状态：待审批；依赖04/05。发送与附件两个子系统，10文件；不新增共享表字段。

## 需求描述

收发件箱有来信人工回复、无来信/选中历史发件跟进均支持通用附件；发送成功后会话可看到并下载。保留真实回复锚点、发件账号、两级安全确认、QA/RAG审计、SENT请求收敛和既有ICS下载。只改人工富文本入口，自动邮件/模板群发不新增上传。

## 关键不变量

### Invariant I-1: 同一最终发送门
- Rule: 两类request DTO仅新增attachmentIds: List<String> = emptyList；未知/重复/越权id不得发送。身份从HttpSession读，operatorName继续只是旧审计显示值。Pending的两个入口传同一executeManualRichSend，读取04服务元数据与原件、验证同专家同上传用户和容量必须在prepareAndClaim之前；生成唯一文件集合同时喂SendPayload快照和ComposedMail载荷。
- Applies to: inbound/conversation两入口、两controller、共同发送门。
- Violation consequence: 另一入口漏带附件、越权、失败烧掉幂等claim。
- 来源: K-manual-rich-final-body-single-seam / K-smtp-idempotency-reservation-before-delivery。

### Invariant I-2: 成功重提与附件变化有明确结果
- Rule: 会话requestId已SENT先读原记录，不重选锚点/不SMTP；当本次或原记录有通用附件时，以04批量元数据校验归属后比较filename/type/size/hash有序语义（不要求原件仍在线）。相同返回原SENT；不同抛04专用OutboundAttachmentException，由04的明确映射返回409“该请求已发送，附件与原请求不同，请发起新回复”，不谎报新附件已发。无通用附件的现有短路路径不变。UNKNOWN/IN_PROGRESS仍不重发；不得因结果未知自动换requestId。
- Applies to: sendConversationManualRichReply已完成分支、prepareAndClaim分类。
- Violation consequence: 界面以为新附件已经发送，或重复投递。
- 来源: K-manual-rich-reply-anchor-must-be-real / K-manual-send-unknown-must-converge。

### Invariant I-3: 会话只展示真实SENT快照
- Rule: message.outboundAttachments默认[]；仅MAIL_RECORD + OUTBOUND + MANUAL_RICH_REPLY + SENT解析05快照。沿用当前窗口findAllById批量结果，不逐封额外查询；元数据列表不读磁盘/IMAP。损坏快照该行附件列表为空并记不含正文/路径的诊断，不能使整页500。既有attachmentCount/firstAttachmentNames/materialCount继续只代表原入站材料口径；ICS的calendarAttachment字段保持独立。
- Applies to: listMessages映射、DTO。
- Violation consequence: 失败伪装已发、N+1、材料计数被污染。
- 来源: K-attachment-metadata-consumer-chain / K-calendar-not-expert-material-owner。

### Invariant I-4: 下载授权以消息关系为准
- Rule: 已发下载校验record.contactId等于path，OUTBOUND/MANUAL_RICH_REPLY/SENT，senderAccountCode真实且非SIMULATOR_NOOP（禁用账号仍可读），附件id属于该记录持久快照且upload元数据同contact/hash/size/name/type；按04路径/原件检查返回attachment。已发文件按现有会话登录可见边界允许其他登录操作员下载，不要求是上传者；草稿仍只上传者可下载。不存在/错专家/错消息/快照未引用统一404；匿名401。
- Applies to: 新sent download端点。
- Violation consequence: id猜测下载未发送/其他专家文件，或团队无法查看已发附件。
- 来源: CalendarAttachmentController.kt:45-82的既有边界；K-download-context-path-host-injection。

### Invariant I-5: 附件只扩充传输，不修改回复语义
- Rule: 不改source_inbound_id语义、线程锚点、QA/RAG选择/正文渲染、退订前置检查、会议正文SHA检查。安全确认重提保留attachmentIds，同一payload文件集合不因确认变更。状态失败保留草稿；服务端错误在claim前失败，不将读文件错误归类为SMTP UNKNOWN。
- Applies to: 所有人工发送路径及失败处理。
- Violation consequence: 回复错误邮件、安全门绕过或不必要UNKNOWN。
- 来源: K-manual-rich-final-body-single-seam / K-manual-send-fingerprint-complete-identity。

## 现状审计

- UnmatchedInboundMailController.kt:250将PendingManualRichReplyRequest逐字段转发；DTO在PendingMailOperationService.kt:1699，已含meeting+previewAttachmentSha256，不含通用附件。
- MailboxConversationController.kt:82请求为requestId/accountScope/anchorMailRecordId/body/safety；:233发送端点无session参数，需追加HttpServletRequest并读取已存在sessionUsername。无来信分支不接受meeting，保持。
- PendingMailOperationService.kt:147和:352共享:476；:379已SENT requestId优先短路。新附件服务为必需构造依赖；直接new此服务的测试命中3个：PendingMailOperationServiceTest、PendingMailOperationServiceTrustWorkbenchTest、mail/RagSendBridgeTest，均已列清单。
- MailboxConversationService.kt:233-241本窗口SENT出站只批量读取一次MailRecord；:253读取入站材料计数；:277与:387独立calendarAttachment映射。新通用附件字段可复用同一次查询。
- CalendarAttachmentController.kt:45已按record/contact/direction/mailType/sendStatus及非模拟账号校验；下载使用UTF-8 attachment、nosniff/no-store。新下载沿此真实边界，不宣称系统已有逐账号个人ACL。
- 存储读写全集：上传表/原件写仅04 upload，本阶段只读；mail_record新增快照的全部写为05四分支，其他生产者null；消息服务和下载新增两个消费者。完整旧消费者和写入路径在[evidence](meeting-mail-evidence.md#完整检索回执)，本阶段不扩大其他读入口。
- IP-1：上传owner→两个发送入口；IP-2：相同文件集合→SMTP及记录快照；IP-3：快照→消息DTO→消息归属下载；IP-4：安全确认和已完成请求→相同附件。

## 实现方案

1. 清单1～3、6～8（I-1/I-2/I-5）：增加request默认空attachmentIds；两个controller取Session username传入service新增参数authenticatedUsername（默认null仅兼容已有无附件内部调用，有附件必须有真实session）。不要用请求operatorName兜底。Pending注入OutboundAttachmentService，两个入口都传ids/username到最终发送门；旧meeting能力不扩到无来信路径。
2. 在claim前调用resolveForSend得到有界字节；原件校验失败在投递try块外返回400/404/413/409。已有SENT会话只调用04的元数据读取和语义比较，不再加载字节、选择新锚点。手工修改附件后的新邮件需新requestId（UI07负责），不可自动把UNKNOWN重置成新请求。按I-1同集合构造payload.outboundAttachments和mail.outboundAttachments。
3. 清单3/4/9（I-3）：新增ConversationOutboundAttachment DTO：id,filename,contentType,byteLength,downloadUrl；只投影SENT快照，downloadUrl由服务端固定路径构建。保持所有旧DTO字段的默认/空值语义，损坏快照不整页崩溃；SQL UNION不用改。
4. 清单5/10（I-4）：GET /api/mail/conversations/{contactId}/messages/{mailRecordId}/outbound-attachments/{attachmentId}/download；先查消息快照成员资格再调用04服务按id读原件。不得接受任意上传id直接当已发下载。账号可见性复制既有CalendarAttachmentController读取规则；不依赖请求传accountScope当权限。
5. 所有测试更新构造器/Mockito签名；新增贯通测试使用临时附件目录、真实codec/文件读写、模拟SMTP。验证两个入口、一封多附件＋ICS、跨消息下载、scope真实锚点、安全确认重提和请求结果未知（I-1～I-5）。

## 变更文件清单

|序号|文件|操作|内容|
|---|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`|修改|两个入口附件id→共享最终发送门|
|2|`src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`|修改|传会话身份与attachmentIds|
|3|`src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`|修改|请求attachmentIds及消息附件DTO|
|4|`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`|修改|SENT窗口批量投影附件快照|
|5|`src/main/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentController.kt`|修改|已发消息原件下载与归属校验|
|6|`src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`|修改|构造依赖和双入口附件发送|
|7|`src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`|修改|构造依赖、安全确认/RAG回归|
|8|`src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt`|修改|构造依赖、证据归档不受附件影响|
|9|`src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`|修改|请求会话身份、timeline DTO/批量读取|
|10|`src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt`|新增|上传→发送→会话→原件下载贯通|

## 验收标准

- I-1：两个入口都传相同原字节/元数据；无效id、跨用户/专家、文件丢失、超总量均SMTP=0、attempt=0。
- I-2：SENT同requestId重提不重选锚点；换附件同requestId409且SMTP=0；旧无附件短路测试保持。
- I-3：本页N封依旧单次findAllById；失败/入站消息outboundAttachments=[]；materialCount/附件原字段不增；破损行不阻断正常行。
- I-4：越权组合均404；已发跨操作员200；禁用真实账号200，模拟账号404；下载SHA与SMTP相同。
- I-5：现有QA/RAG、无来信锚点、两级安全确认、退订、UNKNOWN回归全部通过；缺失原件不误入SMTP UNKNOWN。
- JDK11 mvn test -Dtest=PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,RagSendBridgeTest,OutboundAttachmentFlowTest；MailboxConversationControllerTest启用 -DmysqlIt=true验证真实查询。

## 人工验收清单

### A-1：两条入口完整闭环
- 前置条件：测试专家A有来信；B无来信但有真实SENT发件；本地SMTP沙箱；用户U上传同一txt分别给A/B。
- 操作步骤：1.A人工回复附文件发送。2.B通过会话跟进附文件发送。3.分别打开会话附件下载。
- 预期结果：两封各1附件、各对应自己专家，下载SHA与上传一致；B沿其真实SENT锚点线程；材料计数均不变。
- 覆盖：I-1/I-3/I-4/I-5；IP-1/IP-2/IP-3。

### A-2：请求重提与缺文件
- 前置条件：B上一次requestId已成功；另准备新文件及新回复草稿。
- 操作步骤：1.原requestId原附件重提。2.同requestId换另一附件重提。3.新请求发件前把测试原文件移动到备份目录，再发送。
- 预期结果：步骤1返回原SENT；步骤2为409“该请求已发送，附件与原请求不同，请发起新回复”；步骤3失败且没有新增attempt/邮件/排期；还原文件后可重试。
- 覆盖：I-1/I-2/I-5；IP-2/IP-4。

### A-3：会话下载边界
- 前置条件：U已经发送A的一份文件；另一登录操作员V；另有B消息id。
- 操作步骤：1.V下载A已发送附件。2.把URL的contactId或mailRecordId替换为B。3.尝试把未发送附件id插进A消息下载URL。4.退出登录再访问。
- 预期结果：合法已发下载200；三类错配404；匿名401；原ICS下载仍有效。
- 覆盖：I-3/I-4；IP-3；保留既有下载。

### A-4：安全确认与未知结果
- 前置条件：测试回复正文触发已有安全确认，附一份文件；沙箱可模拟DATA后超时。
- 操作步骤：1.取消确认。2.确认后发送。3.另一次请求模拟UNKNOWN并重试。
- 预期结果：取消确认0封且文件仍待发送；确认后附件恰1份；UNKNOWN显示原警告，不换requestId再次SMTP，QA/RAG审计口径保持。
- 覆盖：I-1/I-2/I-5；IP-4。
