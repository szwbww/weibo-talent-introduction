# 现状审计与证据索引

核对日期：2026-09-07。所有路径相对仓库根目录。以下代码行号为本次快照，执行时按符号复核。完整检索命中（包括测试、脚本、SQL）见 [code-search-evidence.md](code-search-evidence.md)，逐字 DOM/CSS 见 [frontend-baseline.md](frontend-baseline.md)。

## 证据分级

- **代码事实**：当前仓库源代码及迁移；不是线上 commit 证明。
- **线上页面事实**：本会话已通过 Dia 只读查看 `https://qingfei.szwbww.com/talent/` 的收发件箱、可信工作台、人工回复、专家资料及 AI 选件窗；用户提供两张生产截图。检查记录见仓库 `artifacts/mailbox-chat-preview/README.md` 的“实际页面参考”和“V5”。
- **已确认预览意图**：`artifacts/mailbox-chat-preview/chat-materials-v4.png`、`expert-materials-v5.png`、`expert-analysis-v5.png`。预览文件名/大小来自本地 ZIP 元数据；40 条中含 1 条历史 CV + 39 条新文件。下载状态、来源时间、任务进度是模拟，不可称为线上实时结果。
- **故障线索**：先前排查任务记载 LuKai UID217/218，19+20 个附件，整封 MIME 约 21.3/29.1 MB。此处不声称重新验证了线上修复或文件齐全；不得把整封 MIME 大小当附件解码后大小。
- **新增设计**：本文档后续 API、队列状态、限额、CSS 均为拟实现契约，不伪装成已有能力。上线时须补实际部署版本、IMAP 命令日志和测试结果。

## E1：接收与正文解析

`mail/service/ImapMailReceiveService.kt:21` 的 `fetchInboundSince` 先取 UID 窗口，再逐封 `toReceivedMail`，整批 `.toList()` 后返回；`:60 fetchByUids` 同样路径。`:127 toReceivedMail` 同步调用正文和附件提取；`:190 extractAttachments` 在 `:205` 调用 `part.inputStream.readBytes()`。`MailReceiveService.kt:31` 的附件 DTO 强制 `content: ByteArray`。

`:150 extractBody` 在判断 multipart 时使用 `part.content is Multipart`，即使不是正文也可能触发远程 FETCH；有文件名的 text/plain 附件亦可能进入正文。`:185` DSN 读取也用 `readBytes()`。只替换 extractAttachments 无法满足零附件字节下载。（来源：K-extract-body-multipart-subtype、K-mime-dsn-content-handler-absent）

`:220` 现有 connect/read timeout 都是 10000 ms；它们不等于单封或账号总时限。正文白名单、附件分段定位与强制关闭连接必须通过真实 MIME roundtrip / 本地协议测试验证，不能仅 Mock `getContent`。

JavaMail 依据：[IMAP 参数说明](https://javaee.github.io/javamail/docs/api/com/sun/mail/imap/package-summary.html)、[IMAPFolder.fetch/forceClose](https://javaee.github.io/javamail/docs/api/com/sun/mail/imap/IMAPFolder.html)。`forceClose()` 提供不等待服务端响应的关闭入口；具体版本兼容性仍以项目依赖编译和协议测试为准。

## E2：mail_attachment / expert_document / 本地文件

Schema：`db/migration/V7__create_mail_attachment_and_expert_document.sql:1`：附件 file_name VARCHAR(255)，file_size BIGINT NOT NULL DEFAULT 0，storage_path VARCHAR(1024) NOT NULL。expert_document 关联 contact/attachment，document_status 默认 PENDING_REVIEW。`V36__add_mail_attachment_inbound_processing_link.sql` 增加 inbound_processing_id，并约束 mail_record_id 与 inbound_processing_id **恰好一个非空**。expert_document 没有 attachment 唯一约束。

**业务写路径全集（核对仓库检索）**：`MailAttachmentService.saveInboundAttachments:22` 写文件、附件、ExpertDocument；`saveUnmatchedAttachments:64` 写文件、以 processing 为 owner 的附件，不写 ExpertDocument。暂无其他生产附件/资料 repository save/delete 写入口；迁移为表结构/历史约束写入口。计划新增 transfer worker、元数据登记及人工绑定资料关联，是需新覆盖的写路径。

**读路径全集（附件/资料）**：

| 文件/方法 | 当前依赖 | 交互风险 |
|---|---|---|
| MailboxService.resolveAttachments:279 / toItem | source owner、messageId 回退、存在性 | 回退只取同 messageId 最新 mail_record，未限定账号/专家/方向；存在串件风险 |
| MailboxAttachmentService.list/download:33 | 必填 size/path；realpath 保护 | 元数据未落盘不能被当作可下载 |
| MailboxAttachmentController | Content-Length、InputStreamResource | 只对就绪文件输出实际长度 |
| ExpertContactManagementService.getDetail:74 | 按该专家 mailRecord ids 取附件，再读 document | processing-owner 资料不在旧 attachments 数组；统一组件改用新材料 API |
| ExpertContactManagementController.MailAttachmentResponse:482 / mapper:601 | size/path 非空，mailRecordId requireNotNull | nullable 改动必须同步 DTO；不硬塞 processing-owner 附件到旧数组 |
| ExpertDocumentBrowseService.listDocuments:44 / validateAndResolve:100 | doc→attachment→mail_record→contact，预览类型、路径 | 当前拒绝 processing-owner，绑定后需兼容双 owner |
| DocumentTextExtractor.resolveAttachment:44 | 相同所有权链、realpath | 统一文件就绪与所有权解析，不能绕过 |
| ExpertDocumentAnalysisService.analyze:41 / toResponse:180 | 先所有权校验再提取；文件名 | 必须全部所选就绪后才产生/替换结果 |
| AutoReplyPreviewService.preview:56 | 附件名→意图 | 不需要内容，不可触发下载 |
| OperatorStatusReconcileService.reconcile:56 | findAll 附件并通过 mailRecord 关联 | 已发现材料仍保持现有“有附件”语义，不等于审核通过 |
| MailRecordRepository 邮箱列表/分组 SQL:498/529/766/805 | EXISTS 附件 | 不改为“只有 STORED 才算附件” |

文件读取必须经过基目录 realpath 和所有权验证；不能依据客户端路径。下载完成不写 document_status、专家层级、operatorStatus、needsManualAttention。（来源：K-document-file-read-via-storage-path、K-expert-document-ownership-chain）

## E3：inbound_mail_processing / mail_record / 游标

Schema：`V5__create_inbound_mail_processing.sql` 当前唯一键 `(sender_account_code,imap_uid)`，无 UIDVALIDITY；当前实体见 `mail/domain/InboundMailProcessing.kt`。`V49__create_mail_inbox_cursor.sql` 按账号记录 uid_validity/last_uid。当前只收 INBOX，本期不增加任意文件夹功能。

processing **创建入口**：`AutoMailReplyService.confirmManualReviewWithBody:1050` 与 `confirmProcessed:1092`。它们由 `processSingle` 各分支调用。processing **更新入口**：`UnmatchedInboundMailService.bindToContact/markResolved`、`PendingMailOperationService` 的人工处理/回复/重试/撤销路径；完整方法与行号在检索证据。新 uidValidity 创建必填实际值；旧记录的 copy 更新保留原值，不全库猜测回填。

附件分支：未匹配 `:83`；未发首信 `:106` 当前漏存附件；全局关闭 `:117`、专家关闭/人工 `:176`、重复正文 `:244`、正常 `:289` 都有已匹配附件写入。必须逐一覆盖，不只正常自动回复。

`processSingle:74` 虽有 @Transactional，但 `receiveAndAutoReply:677`、`processByUids:791` 在同类调用，Spring 自调用无法证明事务生效。计划只修正文/处理记录/附件索引提交边界，不承诺跨 SMTP 的 exactly-once 重构。（来源：K-process-single-all-callers、K-inbound-processing-write-paths）

入站路径全集：批量全部账号/选定专家检查→BatchAutoMailReplyService；单账号 API、定时任务、异步队列→receiveAndAutoReply；指定 UID/回补→processByUids；这些均落 processSingle。`mailReceiveService.markSeen` 是已读标记，不是幂等或可靠队列。

游标唯一生产写入口 MailInboxCursorService 的初始化/UIDVALIDITY 重置/advance。AutoMailReplyService 按成功 UID 集推进连续前缀；失败 UID 不能被后续成功 UID 的 max 覆盖。元数据登记成功后下载失败不影响游标。（来源：K-inbound-seen-not-processed-marker）

**纠正历史知识**：`V15__add_mail_monitoring_columns_and_promotion_audit.sql:7` 明确 `mail_record.source_inbound_id` 是触发 OUTBOUND 的 **INBOUND mail_record.id**，非 processing 外键。AutoMailReplyService `:274/:811` 新建 INBOUND 时该字段为 null；`:621` OUTBOUND 写入 inboundMailRecordId；confirmProcessed 不建立两表直接关联。ManualExpertMailService 透传 command.sourceInboundId，MeetingScheduleService 使用 sourceMailRecordId；ManualReplySendAttemptService 则写 null。**禁止将此字段当 processing.id 使用**。新附件采用 transfer.inbound_processing_id 的明确桥接；旧附件回退须唯一、同账号/专家/方向匹配，歧义即返回来源需核对。（来源：K-mail-record-source-inbound-id，本轮证据推翻旧条目，已修订）

mail_record 其他写者包含 ManualOutreachTxHelper、ManualExpertMailService、ManualReplySendAttemptService、MeetingScheduleService、MailMonitoringService；自动/人工推广记录的 sourceInboundId 属于另外的审计表。全部源代码命中在证据快照，本期不修改这些发送与推广写语义。新会话只读取 OUTBOUND；来信唯一 authority 是 processing，不能再 UNION INBOUND mail_record 重复计数。（来源：K-mailbox-inbound-source-authority）

## E4：机器邮件、进度及共享执行器

`AutoMailReplyService:703` 内联 bounce 检测早于 DMARC/专家分支；`:718` DMARC 检测后同步 `DmarcReportIngestService.ingest`；DmarcReportParser 读取附件 byte[]、解压 XML。不能去掉字节而静默丢报表。机器报告使用同一有界传输基础设施的独立 purpose，无专家关联，不混入材料列表。`BounceCollectionService.collectBounces` 仍有 fetchUnseenMessages 路径，须回归 DSN，不借本期重写退信分类策略。

`BatchAutoMailReplyService.pollAccounts:80` 先调用账号处理，**完成后**才 `onProgress(accountResult,...)`；`MailAutomationController:170` 此时显示“正在检查邮箱: 刚完成账号”。这解释了进度可能指向上一账号；不能据 UI 名称判断正在阻塞哪个邮箱。

CHECK_REPLIES 与 manual outreach 共用现有 executor；材料 worker 必须独立有界。TaskProgressStore.update 有 execution token 检查并写 TaskProgressLog；读者为 TaskProgressController、app.js 任务弹窗、任务记录摘要。新增阶段信息放现有 details JSON，不增加任务表字段、不改变各任务原有终态枚举。（来源：K-manual-outreach-executor-shared）

## E5：专家会话与关注

`MailboxController` 已有 /mailbox 与 /mailbox/by-expert；MailboxService.listByExpert:90 是 DB 分组分页→取本页专家全部符合条件的邮件。MailRecordRepository 分组使用 OUTBOUND + linked processing，latest 为收/发事件最大时间；该顺序应保留。待处理来自 processing 的现有待处理谓词，不能用是否已读或是否回复代替。（来源：K-group-before-pagination，旧“仅最近收信时间”描述以本次 SQL 为准）

原 date/direction/tag 过滤会影响计数，不能据过滤后 receivedCount=0 推断专家从未回复。新 summary 的收发计数在账号范围全历史计算；筛选决定专家 membership；右侧 timeline 以完整往来为默认，明确显示当前账号范围。waiting 严格为真实来信数 0 且 SENT 发件数>0；FAILED-only 单独显示“发送失败”，不计待回复。

登录是自定义 Session：`auth/controller/AuthController.kt:35` 写 `AuthSessionKeys.USERNAME`，仅允许 admin；不是 Spring Security Principal。关注当前用户身份必须从 Session 取，不能客户端传 username。无需新增用户/权限体系。

## E6：真实前端挂载与样式

当前是单份 index.html + app.js + styles.css、共享 trust-reply-workbench.js，非 Vue/React。`index.html:662` 专家双栏，`:712` 邮箱，`:767` mailboxList。app.js `:7978 loadContactDetail` / `:8254` 资料挂载；`:7556 showExpertDetail` 原始 ES 专家可能没有 contactId，显示未建立联系提示，不构造假 ID。（来源：K-expert-detail-two-panel-render-sites）

`renderExpertDocuments:8363` 全量 document-row；`openAiAnalysisModal:8408` 请求 documents 和历史结果；默认类型为 CV/PHD_DEGREE/MASTER_DEGREE/BACHELOR_DEGREE；`startAiAnalysis:8541` 调现有同步分析 API。提取器只支持 PDF/text，图片没有 OCR。

`renderMailboxActions:10191` 当前单信操作；`mountLiveTrustReply:10650` 固定 LIVE_INBOUND 与 processing ID，采用回人工编辑器；`:10895` 手动 details 当前默认关闭；`:11313 submitManualRichReply` 走现有人工富文本服务。新聊天只换 host，可信生成/QA事实/采用/发送校验保留，不创建另一套工作台。（来源：K-shared-workbench-fixed-mode-host-adapter、K-ai-adopt-direct-send-no-residual-gates）

只有发件时可用既有 `/api/expert-contacts/{contactId}/manual-mail`（ExpertContactManagementController:192），仍使用真实账户和模板/人工输入契约；无 processing ID 不伪造人工回复 endpoint 参数。已核对ManualMailOptionType仅COMPOSE_TEMPLATE且command无subject/body；本期提供“选择模板发送跟进邮件”，不新增未经审计的富文本发送旁路。

样式值：primary #1e40af，bright #3b82f6，hover #1e3a8a；bg #f5f7fb；panel rgba(255,255,255,.55)；border rgba(15,23,42,.11)；text #1e293b/#475569/#94a3b8；success #059669、warning #d97706、error #e11d48；radius 7/10/18px。既有 button:802/838、metadata-card:1659、document-row:1714、contacts-layout:906、document-card-header:3061，全文见 baseline。

不改既有全局 CSS，新增 `.expert-materials`、`.mail-chat` 命名空间；可信工作台内部仍复用 styles.css:7329 起的既有规则。专家列表宽度偏好/resize 控制照旧，专家页只替换资料区。（来源：K-contacts-layout-width-preference）

## E7：验证与缓存约束

Flyway 当前最高 V117；FlywayMigrationIntegrationTest 多处固定117，每次新增迁移的子计划均列入该测试，分别升级期待版本，不删除旧迁移断言。新版本号 V118–V121 是本次顺序预留，执行前若冲突须明确调整文件表及全部引用。

前端三个旧资源键 `20260903-bounce-warning` 必须一起更新，当前实际 **7 个** JS 测试固定此键，非旧知识所写4个：batchSendTaskConsoleVisualFix、checkRepliesRelocation、manualReplySubjectPrefill、overlayAndDialogContrast、ragKnowledgeBasePage、ragWorkbenchRender、trustReplyWorkbenchSharedMount。（来源：K-frontend-cache-key-triad，本轮修正数量）

避免修改 MailRecordRepository/ExpertContactRepository 的巨大 SQL 与行号 guard：新会话查询使用专门 JDBC repository；不顺手修业务状态写入。（来源：K-line-number-guard-breaks-on-any-insertion）

验证命令以项目为准：Java11 的 mvn test；node --test src/test/js/*.test.js；MySQL 迁移测试需独立测试库 `-DmigrationIt=true`。测试服务不得连接生产邮箱、SMTP、正式数据库。不能把未运行的测试写成已通过。

## E8：分析结果与异常处理补充核验

`V59__create_expert_analysis_result.sql`：contact FK、field_key/label/value、source_attachment_id（无附件FK）、excerpt/verified、display_order与时间。唯一业务写者ExpertDocumentAnalysisService：analyze:93先deleteAll再save；updateField:125更新；addField:135新增；clearResults:149删除。唯一业务读取为同service.getResults/updateField/addField/toView；Controller暴露GET/POST/PUT/DELETE，前端openAiAnalysisModal/render结果/字段编辑消费。本期不变表结构，09只在任何结果写入之前增加“所选不支持/无文字则明确失败”校验，其他写路径保持原义。

现有 `ExpertDocumentAnalysisService.kt:49` 会filter掉空文本/unsupported，只要还有1件可读就继续。不能仅在前端承诺“明确每个空PDF”而不修改这一服务；09已列入服务及测试。下载就绪失败和提取失败都必须在deleteAll之前返回，不能损坏历史结果。

`GlobalExceptionHandler.kt:65` 捕获所有Exception并返回500；因此仅在service抛ResponseStatusException无法证明HTTP409。06采用同新controller文件内、限定四个controller类型的高优先级advice，仅处理新材料异常；原AnalysisFailedException仍保持ANALYSIS_FAILED映射。

`V37__create_dmarc_report.sql` report_id唯一；原IngestService唯一写者，按reportId查重后save，读取为邮件监控的JDBC聚合/报表查询，表结构不变。`V22__create_task_progress_log.sql` details_json为TEXT；TaskProgressStore.save/rebindPendingExecutionId写、TaskAuditRetentionService.deleteOlderThan清理；TaskProgressController/TaskExecutionSummaryExtractor/BatchSendConfigController及原任务UI读取。05只扩展一个accountProgress对象键，不改变保留策略与原计数。

知识消纳：本轮命中的18项条目均用于E1–E7或子计划I项；没有把旧sourceInboundId/仅MAX(received_at)描述继续当事实。专题条目跨mail/document/frontend/task，未发现至少5条同主题重复可安全合并，故不做批量归档。命中条目本日已更新last_used与hit_count；达到10次的共享工作台规则提升到CLAUDE。无本次已用条目超过90天需要归档。
