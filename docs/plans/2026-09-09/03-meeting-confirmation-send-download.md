# 03 人工回复接入与历史日历下载

依赖01→02；子系统：人工回复业务和会话读取，共2个；10文件；新增表字段0。API扩展可独立部署，旧浏览器省略meeting时保持原行为。

## 需求描述

R1：现有人工富文本发送可携带已预览会议配置，实际邮件包含日历。R2：刷新会话后可下载已发送日历原件。
必须保留 M1：原安全确认、QA/RAG、防重、未知/失败处理；M2：材料数量/权限来源、普通手工回复和无来信跟进。范围外：旧线程头全面修复、会议状态同步、通用文件附件、外部存储服务。

## 关键不变量

### Invariant I-1: 发送重算和核对
- Rule：request.meeting可空；非空时用真实processing/contact/account和01重算，sha必须等于请求previewAttachmentSha256；已生成的会议text必须在最终text与htmlToPlainText(finalHtml)规范化文本中完整连续存在，否则400。最终正文仍取人工编辑器，不覆盖整封信。
- Applies to：PendingDTO/Controller/sendManualRichReply
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：K-manual-rich-render-before-send

### Invariant I-2: 不绕过既有门禁
- Rule：meeting校验在最终变量渲染之后、Safety/claim/SMTP之前；保留抑制、RAG/QA来源互斥、safetyWarningConfirmed/strongConfirmationText以及审计收敛。新分支的inReplyTo/references取同一个真实processing.messageId；空则null。
- Applies to：Pending传参与ComposedMail
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：K-manual-send-safety-chain-reads-qa-rule；K-outbound-thread-headers-single-seam

### Invariant I-3: 原件下载与精确归属
- Rule：GET下载仅SENT+OUTBOUND+MANUAL_RICH_REPLY、record.expertContactId等于path、账号在当前会话active范围；存在且快照合法才返回存储UTF8字节。不得从配置/当前模板重新生成，不读客户端文件路径；坏JSON/hash失败返回404固定文案并日志不含URL密码。
- Applies to：CalendarAttachmentController、timeline
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：K-attachment-metadata-consumer-chain；K-document-file-read-via-storage-path

### Invariant I-4: 单独附件元数据
- Rule：ConversationMessageItemResponse末尾calendarAttachment默认null，含filename/byteLength/downloadUrl；仅MAIL_RECORD+OUTBOUND+SENT行批量按ID查快照。attachmentCount/firstAttachmentNames仍只代表现有专家材料，不增加日历。不将source_inbound_id用作processingID。
- Applies to：MailboxConversationService/DTO
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：K-mail-record-source-inbound-id

## 现状审计
审计正文与原始检索是本节不可分割的组成部分：[完整审计](meeting-confirmation-audit.md)、[代码证据目录](meeting-confirmation-evidence/)、[源文件 SHA-256](meeting-confirmation-evidence/source-sha256.json)。当前工作区代码为证据；拟新增的接口/类型/样式均明确属于本计划决策，不宣称已经存在。已有 dirty 文件不清理、不覆盖。

适用D2/D3/D4/D5。IP-2（目标复核）、IP-3（传输→指纹/SMTP）、IP-4（存档→下载）、IP-5（材料统计隔离）。app.js mcHostSendRichReply对requestBody直接序列化且安全确认spread扩展，不需修改；03不增加第二个send API。

## 实现方案

T1（I-1/I-2）：PendingManualRichReplyRequest与sendManualRichReply末尾加`meeting:MeetingInput?=null, previewAttachmentSha256:String?=null`，UnmatchedInboundMailController.manualRichReply传递。meeting与digest须同时出现/同时为空，否则400“会议附件配置不完整，请重新预览”。为Pending服务追加MeetingConfirmationService依赖，并在3个现有Pending构造测试中传真实/模拟依赖；禁止默认null让生产依赖悄悄消失。

在原finalSubject/finalText/finalHtml渲染后调用01 validateAndBuild，用服务端解析后的真实account/recipient/contact与processing；digest不一致400“会议配置已变化，请重新预览”；模板禁用沿01拒绝。对expected text、finalText、MailContentService.htmlToPlainText(finalHtml)统一CRLF→LF、NBSP→space、所有连续空白→单空格、trim；要求后两者均包含完整expected文本；不是只检索日期/链接关键词。错误400“会议正文与附件不一致，请编辑会议后重新生成，或移除日历附件”。这是避免常见手工改时间误发的检查，不宣称可证明整封任意HTML语义无冲突。确认后用户仍可修改会议块外内容；会议块内调整措辞应在本次模板里改，或移除附件。

将01返回snapshot同一实例传SendPayload和ComposedMail。有meeting时inReplyTo与references都取清理后的真实record.messageId（现有max255检查保留）；无meeting仍原构造调用。安全校验不能因Zoom来自表单而自动确认；触发原warning时要求原确认流程，payload扩展字段保留。DEDUP/SENT/UNKNOWN/安全失败严格沿原分支。

T2（I-3/I-4）：MailboxConversationService加MailRecordRepository依赖，在当前page.rows的MAIL_RECORD OUTBOUND SENT id集合上findAllById一次；只加入归属contact/账号一致且校验成功snapshot的metadata。INBOUND同数字id不读取outboundsnapshot，避免source碰撞。不要改union/keyset/count/当前专家材料解析。DTO末尾nullable default以保持已有构造点。

新建 `mail/controller/CalendarAttachmentController.kt` 注入MailRecordRepository、MailSenderAccountRepository；GET `/api/mail/conversations/{contactId}/messages/{mailRecordId}/calendar-attachment`。快照解析两处统一调用01 CalendarAttachmentCodec.parseOrNull；归属/状态权限两处显式保持。不存在/不匹配/未SENT/空快照/损坏统一404“日历附件不可用”；会话active范围用与MailboxConversationService.activeAccountCodes:408相同表达式findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE)，不得误加enabled条件，不新造用户级权限。响应200、Content-Type text/calendar; charset=UTF-8、Content-Disposition attachment（Spring ContentDisposition构造安全filename）、Content-Length真实字节、Cache-Control private,no-store、X-Content-Type-Options nosniff。该path沿auth；下载不写日志中的URL/query、不访问外网、不变处理状态。

T3（I-1..I-4）：在现有PendingMailOperationServiceTest中增加calendar场景，覆盖真实生成器+控制器传参+实际ComposedMail、Controller传参、安全确认保留meeting、sha/内容/归属拒绝、无meeting旧路径；在MailboxConversationControllerTest.kt同文件新增CalendarAttachmentIntegrationTest类，覆盖timeline批量读/归属/损坏/旧行null和真实HTTP bytes。新增依赖的3个Pending测试补构造注入；MailboxConversationControllerTest增加MailRecordRepository的@MockBean及默认findAllById(empty/任意)返回空集合，新增calendar场景时单独stub，避免mysqlIt上下文缺bean；原断言不删除。

T4（I-2）：UnmatchedInboundMailController新增传参会移动其后operatorStatus映射的行号。同步OperatorStatusWriteSeamGuardTest的NoiseSite中该文件两条实际行号，保留path/context不变，不扩大白名单。以`rg -n 'operatorStatus = ' UnmatchedInboundMailController.kt`实测新行号，禁止预估偏移；片段改变即超出本计划，不用机械行号调整掩盖。此机械同步在本计划白名单内。（来源 K-line-number-guard-breaks-on-any-insertion）

## 变更文件清单

| # | 文件（仓库根目录相对路径） | 操作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | request与方法接入 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 透传扩展字段 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt` | 批量快照metadata |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | 响应可空字段 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt` | 新增下载入口 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 依赖与旧路径回归 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | 依赖与旧QA回归 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt` | 依赖与RAG回归 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 新增repository mock兼容 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 同步controller行号钉 |

## 验收标准

- I-1：meeting无digest/错digest/修改正文时间/模板禁用/跨联系人400且claim、SMTP=0；外部段落编辑可以成功；html/text双向核验；无meeting旧测试通过。
- I-2：安全warning未确认仍原422，明确确认后同meeting字段重试；未知不自动重发；发送MIME线程头等于真实来信，非会议回归不变；QA/RAG互斥测试保留。
- I-3：下载字节sha=preview=SMTP；删改模板后下载仍旧字节；401/404/坏json/错误hash、SENT之外无下载；缓存与文件头检查。
- I-4：混合来源同id不串附件，批量只查一次；材料attachmentCount/firstNames前后相同；只有新calendarAttachment，null行不渲染卡。
- IP-2..IP-5：隔离邮箱/SMTP捕获器整链路01→03发一封，DB状态、MIME、下载对比；`mvn test`，并在专用新建测试数据库配置下执行 `mvn -Pmysql-it -Dtest=MailboxConversationControllerTest test`（该现有IT读取spring.datasource；不可用默认业务库）。不以Mock返回一模一样硬编码字串替代真实生成结果。

## 人工验收清单

### A-1: 发送并刷新下载
- 前置条件：隔离环境部署01..03，SMTP指向验收者的测试邮箱或Mailpit；测试专家有真实来信，按01 A-2预览得到meeting/digest。
- 操作步骤：1. POST现有manual-rich-reply，正文使用预览html/text，附meeting/digest。2. 如果422按原安全确认流程提交相同配置。3. 查看测试邮箱和会话messages接口。4. 下载calendarAttachment.downloadUrl。5. 后台改模板措辞后再下载。
- 预期结果：只发1封，1个ICS；时间中国15:00–15:30；timeline出现文件名；两次历史下载SHA与初次预览相同，邮件为SENT。
- 覆盖：R1/R2/M1；I-1..I-4；IP-2/IP-3/IP-4

### A-2: 拒绝不一致及隔离
- 前置条件：沿用A-1的测试专家，另建一个测试专家B有不同contactId。
- 操作步骤：1. 修改正文10:00为11:00但保留meeting/digest提交。2. 将下载URL contactId换为B。3. 比较专家材料数。4. 再发一封不带meeting的普通人工回复。
- 预期结果：第1步400且显示“会议正文与附件不一致…”；第2步404“日历附件不可用”；材料数不变；普通回复没有ICS且沿原安全确认。
- 覆盖：M1/M2；I-1/I-2/I-3/I-4；IP-2/IP-4/IP-5
