# 04 · 人工回复通用附件上传与原文件保存

状态：待审批；依赖01（迁移顺序），与02/03业务独立。一个附件子系统，10文件；现有共享表新增字段0。

## 需求描述

提供不限扩展名的多附件选择所需上传API，每次上传一个原文件，返回可加入人工回复的附件id；发送前可下载校对。保持入站附件/专家材料/DMARC原链路。范围不含文件转换、在线预览、OCR、病毒扫描服务、分片上传或对象存储迁移。

本计划拟定容量边界：每个文件≤10MiB，每封人工邮件最多10个通用附件、通用附件总计≤20MiB；这些是本次明确设计值，不是从现有代码推断的线上限制。“任何附件”指格式不限。ICS按既有64KiB上限另算。

## 关键不变量

### Invariant I-1: 上传不等于发件，不变成专家材料
- Rule: 上传只写outbound_mail_attachment与独立outbound目录，不写mail_attachment/expert_document，不调用SMTP、不建排期、不变更专家状态。UUID id永不复用，元数据和原字节创建后不可改；移除草稿附件只解除草稿引用，不删已上传文件。
- Applies to: upload、草稿移除、后续发送读取。
- Violation consequence: 材料数量/状态污染，历史邮件下载内容变化。
- 来源: K-calendar-not-expert-material-owner / K-attachment-metadata-consumer-chain。

### Invariant I-2: 身份和归属来自服务器
- Rule: contactId来自当前已存在专家；created_by取会话AuthSessionKeys.USERNAME，忽略body operatorName作为身份。草稿下载、首次发送解析均校验同contactId且同上传用户；客户端只交附件id列表，不能交服务器路径、hash或大小。每次按服务端元数据重新校验。
- Applies to: upload/downloadDraft/resolveForSend。
- Violation consequence: 跨专家或跨用户引用未发送附件。
- 来源: original；AuthInterceptor会话边界。

### Invariant I-3: 任意格式、真实字节、有界资源
- Rule: 不设扩展名/MIME allowlist；0字节文件合法；逐流累计尺寸，超10MiB立即413并清本次临时文件；最多10个、总20MiB在发送前校验，重复id拒绝400。UTF-8文件名取最后路径段并去控制字符，空则attachment，保留中文与扩展名，最多255字符；MIME只接受合法type/subtype且不带CRLF，否则application/octet-stream。SHA-256和byteLength由原字节计算。
- Applies to: 上传、快照构造、发送解析。
- Violation consequence: 内存/磁盘无界、文件名注入、下载内容不符。
- 来源: original；这些限制为拟新增合同。

### Invariant I-4: 文件路径与下载
- Rule: 复用MailAttachmentStorageProperties.basePath，在其outbound/<UUID>目录命名下存文件，不用用户文件名当路径。先写临时文件、完整校验后原子移动到最终UUID路径、最后保存元数据；写库失败删除本次文件，进程崩溃遗留文件不得被返回成可用附件。读取校验realpath在outbound根内、常规文件、大小/hash一致；缺失或损坏返回404/409，不静默忽略后继续发信。下载强制Content-Disposition attachment UTF-8，nosniff、private,no-store。
- Applies to: 持久化、草稿下载、SMTP前原件读取、后续已发下载。
- Violation consequence: 路径穿越、HTML执行、收到残缺邮件。
- 来源: K-download-context-path-host-injection；现有ExpertMaterialService的路径校验经验。

### Invariant I-5: 快照协议固定
- Rule: OutboundAttachmentSnapshot包含schemaVersion=1、id、filename、contentType、byteLength、sha256；不含绝对路径/字节/用户名。列表按用户选择顺序；codec严格解析、限制数量/长度、拒绝未知schema和非法sha；mail_record无通用附件的唯一存储形态是SQL NULL，非空才存JSON数组（05接入）。发送载荷OutboundMailFile由快照＋已验证ByteArray组成；字节不写正文/日志/审计JSON。
- Applies to: 上传响应、resolveForSend、后续存档和读取。
- Violation consequence: 字段随意变化、敏感路径泄露、存档与SMTP不一致。
- 来源: K-manual-send-fingerprint-complete-identity。

## 现状审计

### 既有附件和磁盘
- V7创建mail_attachment，mail_record_id原必填；V36允许其为空、增加inbound_processing_id，要求二者恰有其一；expert_document外键指mail_attachment。document/ExpertMaterialService.kt:671-707按真实消息来源解析；:113 resolveReadyFileUnscoped / :119 resolveFileReady检查入站transfer/realpath。
- mail_attachment全部消费者检索见evidence.attachment_consumers；包含MailAttachmentService、IMAP元数据、AttachmentTransferService、ExpertMaterialService、材料浏览、DMARC、operator状态重建。它们依赖入站来源与材料分类，不是通用外发文件仓。
- MailAttachmentStorageProperties.kt basePath=/opt/talent/uploads/mail-attachments，metadataOnly=true和transferMaxBytes=100MiB属于入站拉取；不作为此次外发容量设定依据。
- 新表/目录暂无现存写入或读取（新名字检索0）；本阶段全部拟写路径为upload；全部拟读路径为downloadDraft/resolveForSend，05/06消费同服务原件与快照。
- GlobalExceptionHandler.kt:65通用Exception映射500，目前无multipart/附件异常专用handler；这是新上传明确413所需的直接依赖。当前application.yml无显式spring.servlet.multipart限制（rg -n multipart为0）；本计划明确设置，不声称当前线上代理或框架默认数值已验证。HTTP反向代理请求体上限需要发布验收实际上传验证，不能只凭application.yml断言线上可传。
- IP-1：上传原件→预发送/SMTP与已发下载。IP-2：登录会话→草稿owner检查。IP-3：新附件与入站专家材料计数隔离。

## 实现方案

### T1：迁移、模型（I-1/I-2/I-5）
清单1～3、8～9。新表列：id CHAR(36) PK（服务端随机UUID）；expert_contact_id BIGINT NOT NULL FK expert_contact(id) RESTRICT；created_by VARCHAR(100) NOT NULL；file_name VARCHAR(255) NOT NULL；content_type VARCHAR(255) NOT NULL；byte_length BIGINT NOT NULL；sha256 CHAR(64) NOT NULL；created_at DATETIME(6) NOT NULL UTC。索引(expert_contact_id,created_at)。服务端拒绝超过created_by列宽的身份并报配置错误，不截断成另一个用户。

元数据仅插入，不存在公共更新/删除API；文件路径由经过UUID解析的id派生，不再存一份相同storage_key。使用JdbcTemplate显式insert，避免UUID非空被CrudRepository.save当UPDATE。读取按id列表批量取再恢复请求顺序。未引用文件本期保留；不上自动清理任务，不为清理增加新状态/租约/调度表。上线记录outbound目录容量，未来清理需独立需求并证明不删历史快照引用。

### T2：存储与协议（I-1～I-5）
清单4～5。常量MAX_FILE_BYTES=10*1024*1024、MAX_TOTAL_BYTES=20*1024*1024、MAX_FILES=10；构建流式SHA及原件路径。服务所有读写均用配置基目录；不得忽略metadataOnly而修改入站行为，因为本模块不走入站拉取。上传时不占发送attempt。resolveForSend返回不可变的有界文件集合和有序快照，供05/06在claim前使用。

### T3：HTTP（I-2～I-4）
清单6～8、10。
- POST /api/mail/conversations/{contactId}/outbound-attachments，multipart字段file；返回201 {id,filename,contentType,byteLength,sha256,downloadUrl}，downloadUrl为context-relative /api/...路径。
- GET /api/mail/conversations/{contactId}/outbound-attachments/{id}/download，当前用户草稿下载，统一同专家/上传者检查；匿名401、不存在或不属当前用户404。
- application.yml的spring.servlet.multipart显式max-file-size: 10MB、max-request-size: 11MB（一个请求一个文件，留multipart边界空间）；超限响应必须413。在GlobalExceptionHandler只增加MaxUploadSizeExceededException→413以及OutboundAttachmentException→其固定400/404/409/413的映射，响应ApiErrorResponse且不含路径/堆栈；该附件异常定义在OutboundAttachmentModels.kt。不能靠controller局部handler处理multipart解析阶段异常。保留所有其他handler，尤其不在本需求中泛化ResponseStatusException全局映射；超限覆盖实际容器HTTP测试。
- 草稿下载强制attachment，无inline图片/网页执行；是否能在邮件客户端打开某格式由客户端决定，应用不转换。

## 变更文件清单

|序号|文件|操作|内容|
|---|---|---|---|
|1|`src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql`|新增|上传原件元数据表|
|2|`src/main/kotlin/com/weibo/talentintroduction/mail/domain/OutboundMailAttachment.kt`|新增|不可变上传元数据|
|3|`src/main/kotlin/com/weibo/talentintroduction/mail/repository/OutboundMailAttachmentRepository.kt`|新增|保存、按id批量读取|
|4|`src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentModels.kt`|新增|快照codec、文件载荷、固定限制|
|5|`src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentService.kt`|新增|有界上传、归属校验、读取原件|
|6|`src/main/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentController.kt`|新增|multipart上传、草稿下载|
|7|`src/main/resources/application.yml`|修改|显式multipart上限|
|8|`src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentServiceTest.kt`|新增|真实临时目录＋接口安全/边界|
|9|`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`|修改|最新125→126，新表约束|
|10|`src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt`|修改|仅增加附件业务异常和multipart超限映射|

## 验收标准

- I-1：上传及移除后mail_record/mail_attachment/expert_document/meeting_calendar_event行数均不变。
- I-2：用户A上传，用户B或其他contactId请求404；伪造operatorName不起作用。
- I-3：txt/pdf/zip/png/无扩展名/自定义扩展名/中文文件名/0字节均可上传下载；10MiB可用，10MiB+1字节413；总20MiB边界及11个附件发送解析拒绝。
- I-4：下载SHA等于上传SHA；越界路径、symlink逃逸、缺失文件失败；磁盘/DB写失败无成功元数据返回；强制download安全头。
- I-5：codec NULL/非空、损坏JSON/schema/sha拒绝；不暴露绝对路径。
- JDK11 mvn test -Dtest=OutboundAttachmentServiceTest；该测试含MockMvc会话和真实临时目录，容器multipart超限另用实际HTTP验证；Flyway到126，历史target不变。

## 人工验收清单

### A-1：任意格式与中文文件
- 前置条件：测试环境登录用户A；准备“会议资料.zip”、无扩展名文件、0字节txt，记录原SHA；选真实专家id。
- 操作步骤：1.分别调用multipart上传。2.按返回downloadUrl下载。3.比较文件名、大小、SHA；查看专家材料计数。
- 预期结果：三个请求201；下载逐字节一致；中文名称保留；0字节仍可下载；材料计数和已发送数不变。
- 覆盖：I-1/I-3/I-4/I-5；IP-1/IP-3。

### A-2：跨用户与超限
- 前置条件：A上传一个文件；另登录B；准备恰10MiB和10MiB+1字节文件。
- 操作步骤：1.B直接打开A草稿下载URL。2.A改URL专家id。3.A上传两个边界文件；未登录重试。
- 预期结果：前两次404；边界文件201、超出1字节413且不返回附件id；未登录401。
- 覆盖：I-2/I-3/I-4；IP-2。

### A-3：持久保存与原件校验
- 前置条件：A-1上传成功，记录下载URL；仅使用测试环境。
- 操作步骤：1.重启应用后下载。2.把测试outbound文件移到备份位置，重试下载，再还原。3.上传含HTML内容的.html文件后点击下载。
- 预期结果：重启仍能下载且SHA一致；缺件返回404不下载占位文字；HTML以附件下载，不在系统页面执行。
- 覆盖：I-4/I-5；IP-1；保持入站能力不变。
