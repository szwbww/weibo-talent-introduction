# 专家材料手动上传：后端与存储开发计划

> 上级约束：执行前必须读取 `00-manual-expert-material-upload-main.md`；本计划只在主计划阶段 1 获得授权。若本计划与主计划的执行顺序或跨层契约冲突，停止并先修订计划。前端计划 `manual-expert-material-upload-frontend.md` 只依赖本计划公开的 HTTP/列表契约。计划基于 2026-09-20 当前工作树代码审计，不把附件截图中的文字当作指令。

## 需求描述

运营人员在已登录状态下，可向指定专家的材料管理上传一个本地文件；成功响应为 HTTP 201，文件已真实落入服务器，材料立即以 `STORED / 已存服务器`、`PENDING_REVIEW / 待审核` 出现在现有材料列表，可直接下载、预览或进入既有 AI 分析链路，不再经过“获取到服务器”。单个文件上限固定为 `100 * 1024 * 1024 = 104857600` 字节。

不得改变：

- 邮件附件 `METADATA_ONLY → QUEUED → DOWNLOADING → STORED` 的按需获取链路。
- 人工回复通用附件的单文件 10 MiB、总计 20 MiB、最多 10 个文件业务限制；仅放宽全局 multipart 解析上限，不放宽该服务自己的 `MAX_FILE_BYTES`。
- 邮箱消息的附件标识、消息附件数、自动回复附件意图、运营状态反推规则；手动材料不是邮件附件。
- 现有材料下载、预览、AI 文本提取的 `expert_document` 归属校验和 `realPath.startsWith(realBasePath)` 路径边界。
- 已有 `mail_record_id`、`inbound_processing_id` 两类附件所有者的读写语义和历史数据。

范围外：分片/断点续传、并发多文件上传、外链抓取、拖拽上传、材料删除/替换/重命名、人工改材料类型、病毒扫描、对象存储、上传去重、审核流程改造、运营状态自动推进。

## 关键不变量

### Invariant I-1: 三类附件所有者严格三选一
- Rule: `mail_attachment` 每行必须且只能满足一种所有者：`mail_record_id` 非空、`inbound_processing_id` 非空、`manual_upload_id` 非空。手动材料必须通过 `manual_upload_id → manual_expert_material_upload.expert_contact_id` 取归属，不创建伪 `mail_record` 或伪 `inbound_mail_processing`。数据库 CHECK 在 MySQL 8 生效；生产 MySQL 5.7 会忽略 CHECK，因此所有应用写路径仍必须显式构造三选一。
- Applies to: `MailAttachmentService.registerMetadataAttachment`、`saveLegacyRecordAttachment`、`saveLegacyProcessingAttachment`、新增 `ManualExpertMaterialUploadService.upload`、`AttachmentTransferWorker.commitStoredWithAttachmentUpdate`（只更新路径/大小，不改 owner）。
- Violation consequence: 材料可能串专家、污染邮箱时间线，或在 MySQL 5.7 中写出无 owner/多 owner 脏数据。
- 来源: K-expert-document-ownership-chain

### Invariant I-2: 文件与三张元数据表同成同败
- Rule: 上传顺序固定为：验证会话/专家/文件名 → 流式写 `${basePath}/manual/.tmp-<UUID>` 并计数 → 完整校验后同目录原子移动到 `${basePath}/manual/<UUID>` → 在一个 `TransactionTemplate.execute` 中依次写 `manual_expert_material_upload`、`mail_attachment`、`expert_document`。文件写入或移动失败时零数据库写；事务体或提交失败时删除最终文件；临时文件在所有分支删除。成功后禁止存在“有文件无元数据”或“有元数据无文件”。
- Applies to: 新增 `ManualExpertMaterialUploadService.upload`。
- Violation consequence: 列表出现不可下载材料，或磁盘残留无法追踪文件。
- 来源: original

### Invariant I-3: 100 MiB 双层边界
- Rule: 手动材料业务上限精确为 `104857600` 字节；`0..104857600` 接受，`104857601` 起拒绝。服务端必须按实际读入字节流计数，不能只信 `MultipartFile.size`。`spring.servlet.multipart.max-file-size=100MB`，`max-request-size=101MB` 只负责容器前置保护。超限统一 HTTP 413、`code=PAYLOAD_TOO_LARGE`，不回路径。人工回复通用附件仍由既有 `MAX_FILE_BYTES=10 MiB` 二次拒绝。
- Applies to: `application.yml`、新增上传服务、`GlobalExceptionHandler.handleMaxUploadSizeExceeded`、复用的 `OutboundAttachmentException.payloadTooLarge`。
- Violation consequence: 100 MiB 合法文件被误拒，或超限文件进入磁盘/数据库；全局上限变更意外放宽发信附件。
- 来源: K-custom-exception-http-status-mapping

### Invariant I-4: 成功即为已存且待审核
- Rule: 手动上传不创建 `mail_attachment_transfer`；`mail_attachment.file_size` 和 `storage_path` 在创建时均非空，`expert_document.document_status=PENDING_REVIEW`，`document_type` 复用 `MailAttachmentService.inferDocumentType(fileName)`。列表无 transfer 行时必须通过现有真实文件校验得出 `STORED`，`canFetch=false`，下载/预览/AI 能力仍按 MIME 与真实文件决定。
- Applies to: 新增上传服务、`ExpertMaterialService.storageStateOf`、`resolveItem`、`resolveReadyFile`。
- Violation consequence: 手动文件还要求二次获取、审核状态错误，或展示为来源不可用。
- 来源: K-document-file-read-via-storage-path

### Invariant I-5: 手动来源可解释且可筛选
- Rule: 列表查询通过 `mail_attachment.manual_upload_id` 左连接上传记录；手动材料来源固定为 `type=MANUAL_UPLOAD`、`id=contactId`、`subject=手动上传`、`receivedAt=上传时间`、`uploadedBy=会话用户名`、`accountCode=null`。用 `id=contactId` 是为了将该专家全部手动材料归为一个来源筛选项，而不是每文件一个筛选项。排序仍以来源时间倒序。
- Applies to: `ExpertMaterialService.MATERIAL_LIST_SQL`、row mapper、`resolveItem`、`resolveSource`、`SOURCE_TYPES`、`MaterialSource` DTO。
- Violation consequence: 手动材料显示“来源待核对”、无法按来源筛选，或泄露服务器路径代替业务来源。
- 来源: original

### Invariant I-6: 会话身份与归属不可伪造
- Rule: Controller 只从 `AuthSessionKeys.USERNAME` 取上传者，不接收客户端 `uploadedBy`；用户名 trim 后必须非空且不超过数据库 `VARCHAR(100)`，不得截断。`contactId` 必须存在。下载/预览/AI 必须同时满足 `expert_document.expert_contact_id` 与 manual owner 的 `expert_contact_id` 都等于请求 contactId。
- Applies to: `ExpertMaterialController.uploadMaterial`、新增上传服务、`ExpertMaterialService.resolveOwnerContact/resolveReadyFile`。
- Violation consequence: 审计身份伪造、跨专家读取、同名截断导致身份混淆。
- 来源: K-expert-document-ownership-chain

### Invariant I-7: 手动材料不成为邮件事件
- Rule: 手动材料 `mail_record_id` 与 `inbound_processing_id` 必须为 null；`resolveMessageAttachments` 不增加 `MANUAL_UPLOAD` 分支；`OperatorStatusReconcileService` 仍仅用非空 `mailRecordId` 且对应 INBOUND 记录推进 `MATERIALS_RECEIVED`。`expert_document` 计数自然增加 1，这是材料管理的预期。
- Applies to: 新增上传服务、`OperatorStatusReconcileService`、`MailboxService`、`MailRecordRepository`、`MailboxConversationRepository.materialCountByContacts`。
- Violation consequence: 邮箱消息凭空出现附件，或上传本地文件意外推进专家运营状态。
- 来源: K-operator-status-reconcile, K-calendar-not-expert-material-owner

### Invariant I-8: 迁移兼容 MySQL 5.7 与 8.0
- Rule: V130 先建上传来源表和外键，再给 `mail_attachment` 增加唯一可空 `manual_upload_id`；删除旧 `chk_mail_attachment_owner` 前必须照 V129 用 `information_schema.TABLE_CONSTRAINTS + PREPARE` 探测，不能直接 `DROP CHECK`。新三选一 CHECK 在 8.0 真实验证，在 5.7 可被解析后忽略；历史两类 owner 行不得改写。
- Applies to: `V130__add_manual_expert_material_upload.sql`、`FlywayMigrationIntegrationTest`。
- Violation consequence: 生产 MySQL 5.7 迁移 E1064 中断，或 MySQL 8 无法写入手动 owner。
- 来源: original（代码证据：`V129__add_material_request_codes.sql:17-49` 已记录生产为 MySQL 5.7.41）

## 现状审计

### `mail_attachment` 表与本地文件存储（来源: K-attachment-metadata-consumer-chain）
- Schema/mapping:
  - `V7__create_mail_attachment_and_expert_document.sql:1-12` 创建 `mail_record_id/file_name/content_type/file_size/storage_path/created_at`。
  - `V36__add_mail_attachment_inbound_processing_link.sql:1-16` 将 `mail_record_id` 改为 nullable，增加 `inbound_processing_id`、外键和二选一 CHECK。
  - `V118__allow_attachment_metadata_only.sql:6-13` 允许 `file_size/storage_path` 为 null，明确真实 0 字节仍写 0。
  - `MailAttachment.kt:8-17` 当前实体只有两个 owner；新增字段必须放在末尾并给 `null` 默认值，避免破坏现有命名/位置构造。
- Write paths（生产代码全量 grep）：
  1. `MailAttachmentService.registerMetadataAttachment`（`:196-263`）— 写 mail record 或 inbound processing owner，文件字段为 null。
  2. `MailAttachmentService.saveLegacyRecordAttachment`（`:297-332`）— 先 `Files.write`，再写 mail record owner 与真实大小/路径。
  3. `MailAttachmentService.saveLegacyProcessingAttachment`（`:335-356`）— 先 `Files.write`，再写 inbound processing owner。
  4. `AttachmentTransferWorker.commitStoredWithAttachmentUpdate`（`:480-506`）— 仅对既有行补 `storage_path/file_size`，不改 owner。
  5. 数据库迁移 V7/V36/V118 — 建表与调整 nullable/owner 约束。
- Read paths（生产代码全量 grep）：
  1. `ExpertMaterialService`（`:72-152,191-380,772-832`）— owner、真实文件、统一材料列表、来源、状态与 reconcile。
  2. `ExpertDocumentBrowseService`（`:47-83`）— 按 expert_document 取附件元数据；实际文件读取委托统一 resolver。
  3. `DocumentTextExtractor` / `ExpertDocumentAnalysisService` — 通过统一 resolver 读真实文件或读附件名。
  4. `AttachmentTransferService`（`:391`）— 以 expert_document 判断获取请求归属；手动材料无 transfer 且已 ready，不应入队。
  5. `OperatorStatusReconcileService`（`:53-69,195-201`）— 全表读取但只消费非空 `mailRecordId`。
  6. `ExpertContactManagementService`（`:69-82`）— 旧附件数组只按 mail record；文档数组按 expert_document 全量返回。
  7. `MailboxService`、`MailRecordRepository` — 只按 `mail_record_id` 判断消息附件。
  8. `AutoReplyPreviewService` — 只按 `inbound_processing_id` 判断来信附件意图。
  9. `MailboxConversationRepository.materialCountByContacts`（`:363-374`）— 按 expert_document 统计专家材料总数。
- Interaction points:
  - 新 manual owner 写入 → `ExpertMaterialService.resolveOwnerContact` 必须识别，否则下载/预览/AI 失败。
  - 新 manual owner 写入 → 列表 SQL/row/source 必须识别，否则 ownerValid=false、来源待核对。
  - 新行没有 transfer → 现有 `storageStateOf` 只有真实路径通过校验才返回 STORED；上传必须写最终路径后再提交元数据。
  - 新行的两个邮件 owner 均为空 → 邮箱/状态消费者应自然忽略；不得为兼容旧 reader 填假邮件 owner。

### `expert_document` 表
- Schema/mapping: `V7__create_mail_attachment_and_expert_document.sql:14-28` 规定 contact/attachment 非空外键、默认 `PENDING_REVIEW`；`ExpertDocument.kt:8-39` 定义状态与材料类型枚举。
- Write paths:
  1. `MailAttachmentService.bindExistingProcessingAttachments`（`:129-144`）。
  2. `MailAttachmentService.ensureExpertDocument`（`:272-293`）。
  3. `MailAttachmentService.saveLegacyRecordAttachment`（`:322-331`）。
  4. `ExpertMaterialService.reconcileApply`（`:615-636`）。
  5. 新增 `ManualExpertMaterialUploadService.upload`。
- Read paths: `ExpertMaterialService` 归属/列表/获取校验；`ExpertDocumentBrowseService` 旧浏览接口；`ExpertContactManagementService` 专家详情；`MailboxConversationRepository.materialCountByContacts` 材料数；`AttachmentTransferService` 获取归属。
- Interaction points: 手动上传写一条文档，因此材料计数增加、旧浏览接口可见；必须保持 `PENDING_REVIEW` 与唯一 attachment 对应，不能覆盖已有审核结果。

### 新表 `manual_expert_material_upload`
- Schema/mapping: 当前不存在。V130 固定字段：`id BIGINT AUTO_INCREMENT PK`、`expert_contact_id BIGINT NOT NULL FK`、`uploaded_by VARCHAR(100) NOT NULL`、`created_at DATETIME(6) NOT NULL`，索引 `(expert_contact_id, created_at, id)`。不存文件路径、文件名或重复状态。
- Write paths: 仅新增上传服务，在同一事务中每成功文件写一行。
- Read paths: 新 repository 供 owner resolver；材料列表 SQL 读取 contact、operator、created_at。
- Interaction points: `mail_attachment.manual_upload_id` 对该表为唯一外键；删除行为不在本计划，外键不设级联删除。

### `${talent-introduction.mail-attachment-storage.base-path}` 文件存储
- Schema/mapping: `MailAttachmentStorageProperties.basePath` 是权威根；`ExpertMaterialService.resolveFileReady`（`:118-152`）将路径标准化、要求存在/普通文件并校验真实路径在根内。（来源: K-document-file-read-via-storage-path）
- Write paths: 旧 `MailAttachmentService` 两个 legacy `Files.write`；`AttachmentTransferWorker` 的 `.part → atomic move`；`OutboundAttachmentService.upload`（`:49-123`）提供“临时文件、流式计数、同目录原子移动、DB 失败删最终文件”先例。
- Read paths: 下载、预览、`DocumentTextExtractor`、AI 分析都经统一 resolver。
- Interaction points: 新服务必须只把 UUID 当路径段，原始文件名只存元数据；否则文件名可造成路径穿越。成功元数据路径必须指向 final，不得指向 `.tmp-*`。

### Multipart 与异常映射
- Schema/mapping: `application.yml:14-21` 当前全局为 10MB/11MB；`OutboundAttachmentModels.kt:13` 另有硬 10 MiB 业务上限。`GlobalExceptionHandler.kt:67-80` 已将 `OutboundAttachmentException` 和容器 `MaxUploadSizeExceededException` 映射为 413；`:82-84` 会把未映射 RuntimeException 变成 500。（来源: K-custom-exception-http-status-mapping）
- Write paths: Multipart 仅承载字节；成功写路径由服务控制。
- Read paths: 新 controller 从 session 取 username 的模式与 `ExpertMaterialController.requestTransfers:49-59` 一致。
- Interaction points: 把全局 parser 提到 100MB 会让旧发信上传进入其服务；其 `MAX_FILE_BYTES=10 MiB` 必须继续拒绝并由现有测试守住。

## 实现方案

### 阶段 1：迁移与实体（I-1、I-7、I-8）

1. 新增 `src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql`：
   - 创建上文固定的新表、索引和 expert_contact 外键。
   - 给 `mail_attachment` 增加唯一 nullable `manual_upload_id` 及外键。
   - 用 V129 同款存在性守卫删除旧二选一 CHECK，再添加三选一 CHECK。
   - 不 UPDATE/回填任何历史行。
2. 修改 `MailAttachment.kt`，只在构造器末尾追加 `val manualUploadId: Long? = null`；原三条写路径无需改调用，其 null 值保持既有两类 owner。
3. 新增 `ManualExpertMaterialUpload.kt` 与 `ManualExpertMaterialUploadRepository.kt`；repository 仅需 `CrudRepository`，不加入删除 API。
4. 在 `FlywayMigrationIntegrationTest.kt`：
   - 所有“迁到最新”的版本断言从 129 改 130，保留 V129 专项测试名称与内容。
   - 新增 V130 测试：历史 mail/inbound owner 行保持不变；新表字段/索引/外键/unique 存在；MySQL 8 下三种合法 owner 可写，零 owner和双 owner被拒；manual upload 不能指向不存在 contact，attachment 不能指向不存在 manual row。

### 阶段 2：有界、原子上传服务（I-1～I-4、I-6、I-7）

1. 新增 `ManualExpertMaterialUploadService.kt`，公开：
   ```kotlin
   fun upload(
       contactId: Long,
       authenticatedUsername: String,
       originalFilename: String?,
       declaredContentType: String?,
       stream: InputStream
   ): ManualExpertMaterialUploadResponse
   ```
2. 固定常量 `MAX_MANUAL_MATERIAL_BYTES = 100L * 1024 * 1024`、目录 `manual`、buffer `64 * 1024`。复用已有 `normalizeOutboundFileName`、`normalizeOutboundContentType` 以及 `MailAttachmentService.inferDocumentType`，不另造第二套文件名/MIME/材料类型规则。
3. 先校验 contact 存在与会话用户名，再按 I-2 流式写临时文件；每次 read 后累加，首次 `>104857600` 立即抛 `OutboundAttachmentException.payloadTooLarge("单个材料不能超过 104857600 字节")`。允许 0 字节并写 `fileSize=0`。
4. 原子移动后，在一个 `TransactionTemplate` 中写三行：manual source → attachment（三选一 owner、真实大小、final path）→ document（PENDING_REVIEW、沿用推断类型）。捕获 transaction/commit 异常删除 final 后原样抛出；响应只含业务元数据：`attachmentId/documentId/fileName/contentType/fileSize/documentType/documentStatus/storageState=STORED`，不含 storagePath。
5. 修改 `application.yml` 为 `max-file-size: 100MB`、`max-request-size: 101MB`，注释明确这是共享 parser ceiling；旧 outbound 服务仍硬拒绝 >10 MiB。

### 阶段 3：HTTP 与统一读模型（I-3～I-7）

1. 修改 `ExpertMaterialController.kt`：注入新服务；新增 `POST /api/expert-contacts/{contactId}/materials/uploads`，只接一个必填 multipart `file`，会话 username 取法与 transfers 一致，`inputStream.use` 调服务，返回 201。缺 file 沿 Spring binding → 400；匿名仍由既有 AuthInterceptor → 401。
2. 修改 `ExpertMaterialService.kt`：
   - 注入 manual repository；`resolveOwnerContact` 第三分支读取 manual source contact。
   - MATERIAL_LIST_SQL 增加 `a.manual_upload_id`、manual source LEFT JOIN 和 contact/operator/time 投影；row mapper/data class 同步。
   - `ownerValid` 增加 manual contact 精确相等；`resolveSource` 优先识别 manual owner，构造 I-5 来源；`SOURCE_TYPES` 加 `MANUAL_UPLOAD`。
   - `MaterialSource` 末尾追加 nullable `uploadedBy`，既有两个来源显式/null 默认，不改其 JSON 字段语义。
   - 不改 `resolveMessageAttachments`；不改 transfer/reconcile SQL。
3. 新增 `ManualExpertMaterialUploadFlowTest.kt`，在同一文件覆盖：
   - 小文件 HTTP 201、会话 username 进入 source、磁盘字节一致、三 repository 写入值、PENDING_REVIEW、无 transfer。
   - 文件名路径段/控制字符规范化，final 路径只有 UUID 且位于 manual root。
   - 0 字节、恰好 104857600 成功；104857601 返回/抛 413，临时/final 文件与三类元数据均为空。
   - DB 第二/第三次写入及 commit 失败均回滚并删除 final；文件写/移动失败不写库。
   - manual owner 错专家时 download resolver 拒绝；同专家列表为 STORED、canFetch=false、source=MANUAL_UPLOAD、uploadedBy 正确，下载/预览/AI 共用 resolver 可读。
   - `source=MANUAL_UPLOAD&sourceId=<contactId>` 只筛出手动材料。
   - MockMvc 断言匿名 401、缺 file 400、未知 contact 404、超限 413 且响应无物理路径。
   - 静态读取 application.yml 断言 parser 100MB/101MB；继续运行既有 `OutboundAttachmentServiceTest` 验证其 10 MiB 硬边界。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql` | 新增来源表、manual owner、兼容性约束 |
| 2 | `src/main/resources/application.yml` | multipart parser ceiling 调为 100MB/101MB |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachment.kt` | 末尾新增 nullable manualUploadId |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/document/domain/ManualExpertMaterialUpload.kt` | 新增上传来源实体 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/document/repository/ManualExpertMaterialUploadRepository.kt` | 新增 repository |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ManualExpertMaterialUploadService.kt` | 新增流式落盘与事务写入 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/document/controller/ExpertMaterialController.kt` | 新增 multipart 201 接口 |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt` | manual owner、来源与列表投影 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/document/controller/ManualExpertMaterialUploadFlowTest.kt` | 新增服务/HTTP/读链路测试 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 最新版本与 V130 约束测试 |

文件数：10。子系统：数据库/存储写入、材料 HTTP/读模型，共 2 个。`mail_attachment` 共享表只新增 1 个字段。

## 验收标准

- I-1：迁移 IT 证明三类 owner 各自合法、零/双 owner 拒绝；grep 所有生产 `MailAttachment(` 构造点，旧三条均只设置旧 owner，新服务只设置 manual owner。
- I-2：测试逐项注入流失败、repository 第二/第三次失败、transaction commit 失败；断言数据库 mock 无残留语义且 `${basePath}/manual` 不留 `.tmp-*`/final。
- I-3：边界测试断言 104857600 成功、104857601 为 413；`OutboundAttachmentServiceTest` 的 10 MiB+1 仍为 413。
- I-4：上传后列表项断言 `storageState=STORED`、`documentStatus=PENDING_REVIEW`、`canFetch=false`、`canDownload=true`；不存在 transfer 行。
- I-5：列表与 source filter 测试断言 `MANUAL_UPLOAD/contactId/手动上传/uploadedBy/upload time`，邮件来源投影回归不变。
- I-6：匿名 401、未知专家 404、跨专家下载拒绝；响应 JSON 不含 `storagePath`、绝对根路径或临时文件名。
- I-7：新增 `ManualExpertMaterialUploadFlowTest` 构造“只有 manual owner、没有 inbound mail record”的 reconcile fixture，断言不推为 MATERIALS_RECEIVED；邮箱 message attachment 既有测试全绿；材料计数增加 1。
- I-8：`-DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` 在 MySQL 8 容器迁至 130，V130 专项断言全绿；人工预发 MySQL 5.7 迁移见 A-8。
- 定向命令：
  ```bash
  JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ManualExpertMaterialUploadFlowTest,ExpertMaterialServiceTest,ExpertDocumentBrowseServiceTest,DocumentTextExtractorTest,OperatorStatusReconcileServiceTest,OutboundAttachmentServiceTest test
  JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test
  git diff --check
  ```
- 全量门禁：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 退出码 0；如当前工作树已有与本计划无关的红灯，必须记录基线与失败归属，不得把它算作本计划通过。

## 人工验收清单

### A-1: 小文件上传后立即可用
- 前置条件: 测试环境存在专家 contactId；登录运营账号；准备一个内容可识别的 1 MB PDF。
- 操作步骤: 1. 用 multipart POST `/api/expert-contacts/{contactId}/materials/uploads` 上传该 PDF；2. GET 同专家 materials；3. 点击返回项 downloadUrl。
- 预期结果: POST 为 201；列表新增 1 项，状态 `STORED`、审核 `PENDING_REVIEW`、来源 `MANUAL_UPLOAD`、上传者为当前登录名、实际大小为 1048576；无“获取到服务器”动作；下载字节与原文件一致。
- 覆盖: I-2、I-4、I-5、I-6、需求可观察结果

### A-2: 100 MiB 边界
- 前置条件: 准备精确 104857600 字节与 104857601 字节两个文件，上传前记录该专家材料总数和 `${basePath}/manual` 文件数。
- 操作步骤: 1. 先上传 104857600 字节文件；2. 再上传 104857601 字节文件；3. 刷新列表与存储目录。
- 预期结果: 第一个 201 且新增 1 个 final 文件/1 条材料；第二个 413、`code=PAYLOAD_TOO_LARGE`，材料总数和 final 文件数不再增加，目录无 `.tmp-*`。
- 覆盖: I-2、I-3

### A-3: 多来源列表回归
- 前置条件: 同一专家已有一个已存邮件附件和一个仅元数据邮件附件，再完成一次手动上传。
- 操作步骤: 1. GET 全部材料；2. 分别按 MAIL_RECORD/INBOUND_PROCESSING 既有来源筛选；3. 按 `MANUAL_UPLOAD + sourceId=contactId` 筛选。
- 预期结果: 全部列表三项均在；原邮件附件状态与获取按钮不变；MANUAL_UPLOAD 筛选只返回手动材料，来源显示“手动上传”和实际上传者。
- 覆盖: I-4、I-5、不得改变邮件获取链路、interaction: 新写入→列表读取

### A-4: 下载、预览与 AI 共用安全文件
- 前置条件: 上传一个文本 PDF 到专家 A；另有专家 B。
- 操作步骤: 1. 以 A 的 URL 下载和预览；2. 将该附件加入 A 的 AI 分析；3. 把 URL 中 contactId 改成 B。
- 预期结果: A 下载/预览 200 且 AI 能读取文本；B 请求被拒绝且不返回文件字节；任何响应都不出现服务器绝对路径。
- 覆盖: I-4、I-6、interaction: 上传写入→下载/预览/文本提取

### A-5: 邮箱与运营状态不受影响
- 前置条件: 选择一个当前无入站附件、状态不是 MATERIALS_RECEIVED 的专家，记录其邮箱消息附件数和 operatorStatus。
- 操作步骤: 1. 手动上传一个文件；2. 刷新邮箱会话；3. 执行运营状态 reconcile；4. 查看专家卡材料计数。
- 预期结果: 邮箱消息附件数不变；operatorStatus 不因本次上传变为 MATERIALS_RECEIVED；专家材料计数增加 1。
- 覆盖: I-7、不得改变邮箱/状态、interaction: 新写入→既有消费者

### A-6: 旧发信附件上限回归
- 前置条件: 打开人工回复，准备 11 MiB 文件。
- 操作步骤: 1. 通过原人工回复附件入口上传；2. 观察响应和草稿卡。
- 预期结果: 仍返回 413；不会因全局 multipart ceiling 提高而接受；草稿不出现 ready 附件。
- 覆盖: I-3、不得改变通用附件 10 MiB 限制

### A-7: 异常与清理
- 前置条件: 在测试环境令数据库在 `expert_document` 写入阶段失败；准备小文件。
- 操作步骤: 1. 调用上传接口；2. 恢复数据库；3. 查询三表并检查 manual 目录。
- 预期结果: 请求为 5xx；三表均无本次行；manual 目录无本次 final 和 `.tmp-*`；恢复后再次上传可成功。
- 覆盖: I-2、interaction: 文件系统↔数据库事务

### A-8: MySQL 5.7 迁移演练
- 前置条件: 从生产同版本 MySQL 5.7.41 的匿名化备份恢复测试库，Flyway schema version=129。
- 操作步骤: 1. 部署只含后端计划的构建；2. 运行 Flyway 至 130；3. 查询 columns/foreign keys；4. 上传一个小文件并读取列表。
- 预期结果: 无 E1064；版本为 130；`manual_upload_id` 与两条外键存在；历史附件数不变；小文件上传 201 且列表 STORED。
- 覆盖: I-1、I-8
