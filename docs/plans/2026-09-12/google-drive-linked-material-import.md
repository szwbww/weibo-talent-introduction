# Google Drive 受控链接并入专家资料导入

## 需求描述

把专家来信正文中的受控 Google Drive 文件分享链接识别为“专家上传资料”，继续使用现有材料清单、选择、排队、下载、失败展示、下载到电脑和后续文档能力。首个明确支持的输入仅为：

```text
文件名 <https://drive.google.com/file/d/{fileId}/view?...>
```

本次真实样本：

```text
China_Collaborator.zip <https://drive.google.com/file/d/1eUOvutQu2yinCWYHiWIbVAVt2icbSwW9/view?usp=drive_web>
```

识别阶段只登记元数据，不自动获取远端文件；用户仍在现有“专家上传资料”面板点击“获取到服务器”或“获取所选到服务器”后才触发下载。

明确范围：

- 支持 `https`、精确主机 `drive.google.com`、精确路径 `/file/d/{fileId}/view`；query 只忽略，不参与下载地址拼接。
- 支持纯文本 URL、HTML `<a href>`，以及 multipart/alternative 中同一链接的去重。
- 仅在现有 `metadataOnly=true` 模式启用，避免给已停用的旧附件模式引入半套外链语义。
- 继续使用真实来信来源和既有专家材料入口；不新建页面、弹窗、接口、表、字段、状态或配置项。

范围外：Google Docs/Sheets/Slides 导出链接、Drive 文件夹、`open?id=` 等其他 URL 形态、OAuth/登录态/私有账号授权、任意网站 URL、自动下载、历史邮件回填、解压 ZIP、病毒扫描、修改 AI 支持类型、用下载响应文件名回写已登记名称。

### 已验证事实

- 2026-09-12 从生产应用所在服务器 `150.158.92.103` 只读验证：分享页返回 `200 text/html`、约 81,919 字节，不能当附件保存。
- 同机使用从 `fileId` 固定构造的 `https://drive.usercontent.google.com/download?id=1eUOvutQu2yinCWYHiWIbVAVt2icbSwW9&export=download&confirm=t` 返回 `200 application/octet-stream`、`Content-Disposition: attachment; filename="China_Collaborator.zip"`、`Content-Length: 32880706`。
- 完整下载被 `file` 识别为 ZIP；SHA-256 为 `64817085955e153c6ce7a3b486898ce397e23469eba13cd38859b1412d1ba52a`。验证临时文件已删除。
- 线上 Tomcat 已配置 JVM `https.proxyHost=127.0.0.1`、`https.proxyPort=7890`；使用 JDK URL 连接会沿用该系统代理，不新增代理配置。

## 关键不变量

### Invariant I-1: 只接受固定 Google Drive 文件链接
- Rule: 仅匹配 scheme=`https`、host 精确等于 `drive.google.com`、path 精确符合 `/file/d/([A-Za-z0-9_-]{1,248})/view` 的 URL；248 来自 `part_path VARCHAR(255)` 减去 7 字符 `gdrive:` 前缀。拒绝 HTTP、用户信息、非默认端口、主机后缀伪装、其他 path 和空/超长 fileId。
- Applies to: 纯文本、HTML href、下载地址构造。
- Violation consequence: SSRF、任意主机访问或把非文件页面登记为资料。
- 来源: original；真实链接验证。

### Invariant I-2: 检查回复只登记，不触网不落盘
- Rule: IMAP 收信只解析正文并生成 `content=null` 的 linked material 描述；不得在检查邮件线程访问 Drive、创建目录或写文件。真实获取只能由现有用户入队动作和 transfer worker 完成。
- Applies to: `ImapMailReceiveService`、`MailAttachmentService`、`AttachmentTransferWorker`。
- Violation consequence: 邮件检查被大文件/慢网络阻塞，破坏现有显式获取策略。
- 来源: K-attachment-metadata-consumer-chain；`MailAttachmentService.kt:18-33`；`expert-materials.js:472-474`。

### Invariant I-3: 机器邮件只看到真实 MIME 附件
- Rule: Drive linked materials 在 `ReceivedMail` 中与 `attachments` 分离；self-check、bounce、DMARC 检测/解析继续只读 MIME `attachments`。仅在公共 `processSingle` 事务入口合并，确保 `receiveAndAutoReply`、`processByUids` 和外部直接调用一致。
- Applies to: `ReceivedMail`、`AutoMailReplyService.receiveAndAutoReply/processSingle/processByUids`。
- Violation consequence: DMARC 误把 ZIP/链接资料当报告，或不同调用入口漏登记。
- 来源: K-process-single-all-callers；K-linked-materials-after-machine-mail-routing；`AutoMailReplyService.kt:77-99,789-829,886-897`。

### Invariant I-4: 复用现有附件所有权和资料清单
- Rule: 合并后的 linked material 必须走现有 `saveInboundAttachments` / `saveUnmatchedAttachments` / `bridgeInboundProcessing`，生成 `mail_attachment`、`mail_attachment_transfer(purpose=MATERIAL,state=METADATA_ONLY)` 和已知专家的 `expert_document`；不得旁路写表。
- Applies to: 已匹配、未匹配、无首信、正文截断、全局/联系人自动回复关闭、重复正文等全部业务分支。
- Violation consequence: 材料不出现在当前 UI、来源丢失、文档重复或某些人工分支漏件。
- 来源: K-inbound-processing-write-paths；K-expert-document-ownership-chain；`MailAttachmentService.kt:46-117,150-188,195-268`。

### Invariant I-5: 来源身份可判别且幂等
- Rule: linked material 沿用真实 `accountCode/folder/uidValidity/imapUid/messageId`，`partPath` 固定为 `gdrive:{fileId}`；同一消息中同一 fileId 至多登记一份。MIME partPath 继续为点分数字；worker 只按这两种语法分派。
- Applies to: DTO、transfer 唯一键、worker fetcher 选择。
- Violation consequence: alternative 双份重复、重试重复建文档、Drive 来源误走 IMAP 获取器。
- 来源: K-mailbox-inbound-source-authority；`V119__create_mail_attachment_transfer.sql:8-16,25-55`；`MailAttachmentService.kt:195-268`。

### Invariant I-6: 当前回复边界和正文保持不变
- Rule: 只扫描当前回复；使用现有 `MailBodyCleaner` 去掉引用历史、签名和免责声明后再提取链接。HTML 扫描视图保留 href 和锚文本，但 `ReceivedMail.body` 继续使用现有正文选择/清洗结果，绝不因材料识别而改写。
- Applies to: text/plain、text/html、multipart/mixed、multipart/alternative。
- Violation consequence: 每次往返信都重复导入旧链接，或改变意图识别/自动回复正文。
- 来源: `MailBodyCleaner.kt:7-35,67-87`；`ImapMailReceiveService.kt:431-467,589-592`。

### Invariant I-7: 文件名是受限提示，不是路径
- Rule: 文件名优先取合法锚文本，其次取 URL 前紧邻的合法文件名 token；要求有 1–10 位字母数字扩展名，去掉包围符、控制符和 `/\\`，限制 255 字符。否则使用 `GoogleDrive-{fileId}`。文件名不得参与落盘路径；登记时 `contentType=null`，继续由 `ExpertMaterialService.resolveContentType` 按现有文件名规则推断，未知为 `application/octet-stream`。
- Applies to: 列表展示、document type 推断、content type；不适用于物理路径。
- Violation consequence: 路径穿越、超长元数据、把“click here”误作文件名、错误开放预览/AI。
- 来源: `AttachmentTransferWorker.kt:562-589`；`MailAttachmentService.kt:370-393`。

### Invariant I-8: 下载固定、流式、有界、可中止
- Rule: worker 从 `gdrive:` 后只取已校验 fileId，固定构造 `drive.usercontent.google.com/download` URL；禁止使用邮件原 URL或跟随重定向。必须要求 HTTP 200、非 `text/html`、`Content-Disposition` 为 attachment；声明长度超限立即失败，未知长度仍由现有流式 `transferMaxBytes` 限制。连接/读取超时、总时限、租约续期、watchdog、`.part` 和原子转正全部复用现有机制。
- Applies to: Google Drive fetcher、`AttachmentTransferWorker.downloadPart`。
- Violation consequence: 登录页被存成 ZIP、绕过大小限制、阻塞线程、残留半文件。
- 来源: `MailAttachmentStorageProperties.kt:14-25`；`AttachmentTransferWorker.kt:234-259,357-427,429-489`；`ResolvedAttachmentPart:62-73`。

### Invariant I-9: 失败分类和敏感信息边界不变
- Rule: 除 408/429 外的 4xx、重定向、HTML/缺少 attachment disposition 记 `SOURCE_UNAVAILABLE`；408、429、5xx、连接/读取故障记 `FAILED`；大小超限记 `FAILED/LIMIT_EXCEEDED`；watchdog 中止沿用 `TIMEOUT`。错误信息不得包含原 URL、响应正文、Cookie、代理或邮箱凭据。
- Applies to: fetcher 抛出的 `AttachmentFetchException`、worker 失败提交。
- Violation consequence: 不可重试/可重试混淆，或敏感信息进入数据库/UI/日志。
- 来源: `ImapAttachmentContentFetcher.kt:50-59`；`AttachmentTransferWorker.kt:514-559`。

### Invariant I-10: 现有 UI 和数据库契约零扩张
- Rule: 不改前端文件、不新增数据库迁移。新行必须自然满足现有材料 SQL、`canFetch`、状态标签和 POST 入队接口。
- Applies to: `expert_document`、`mail_attachment`、`mail_attachment_transfer`、ExpertMaterial API/UI。
- Violation consequence: 重复功能、两套状态机、发布迁移风险或界面行为分叉。
- 来源: `ExpertMaterialService.kt:224-277,317-381,772-807`；`expert-materials.js:43-58,406-415,444-565,671-725`。

## 现状审计

### 数据模型和约束

- `mail_attachment`：V7 创建，V118 允许 `file_size/storage_path=NULL` 表示只登记元数据；V36 已建立 mail record / inbound processing owner XOR。无需新列。
- `expert_document`：V7 以 `mail_attachment_id` 关联附件，初始 `document_status=PENDING_REVIEW`。`MailAttachmentService.ensureExpertDocument` 已按 attachment 幂等复用。
- `mail_attachment_transfer`：V119 的来源唯一键为 `(account_code,folder,uid_validity,imap_uid,part_path)`，`part_path VARCHAR(255)` 没有数字格式 DB CHECK；状态和 purpose 已覆盖本需求。
- 当前最高 Flyway 版本为 V124；本方案不创建 V125，也不修改迁移测试版本钉点。

### 收信解析读路径

- `fetchInboundSince` 与 `fetchByUids` 都调用 `convertToReceivedMail`（`ImapMailReceiveService.kt:36-113,290-323`），因此提取放在此共享 MIME 遍历可覆盖轮询与 UID 回补。
- `walk` 当前只返回 `bodyText/bodyTruncated/attachments`（`:374-399`）；multipart/alternative 只选首个非空正文（`:431-454`），但附件会聚合全部子节点。
- HTML 当前在 `stripHtml` 时丢弃 href（`:462-467,589-592`），所以不能从最终 `ReceivedMail.body` 推断链接；必须在 text/html 叶的原始有界文本内生成独立扫描视图。
- 正文读取已有单信字节、MIME 节点、单信总时限和账号窗口限制（`:401-425,541-587`）；链接解析不得第二次读取 part stream。

### 来信业务写路径

- 公共事务入口是 `processSingle`（`AutoMailReplyService.kt:77-99`）；`receiveAndAutoReply` 和 `processByUids` 都调用它（`:829,:896`）。在这里合并可覆盖全部入口。
- self-check、bounce、DMARC 在 `processSingle` 之前执行；DMARC 明确读取 `mail.attachments`（`:789-829`）。linked materials 必须保持单独字段到该阶段之后。
- 已匹配来信的附件写入点为 `saveInboundAttachments`（`:198-202,256-260,324-328,368-372`）。
- 未匹配、无首信、正文截断/旧 UID 存疑通过 `registerProcessingOwnerMaterials` 写入（`:155-185,1306-1345`）。
- 确认 processing 后，`bridgeInboundProcessing` 把 metadata transfer 关联到 processing（`:1294-1299`）。合并后的列表可直接复用，无需新增分支。

### 附件和文档全部写路径

- `MailAttachmentService.saveInboundAttachments/saveUnmatchedAttachments` 按 `content` 是否为空分流（`MailAttachmentService.kt:46-117`）。linked material 使用 `content=null+source` 即进入现成 metadata 路径。
- `registerMetadataAttachment` 只要求 `partPath` 非空，不要求数字 MIME 语法；先按 V119 唯一身份查询，再创建 attachment/transfer（`:202-268`）。所以 `gdrive:{fileId}` 无需 schema 变更。
- `mail_attachment` 的全部写路径为 metadata 的 `registerMetadataAttachment`（`:236-245`）、旧 content 的 `saveLegacyRecordAttachment/saveLegacyProcessingAttachment`（`:298-355`），以及 worker 成功后的 `storage_path/file_size` UPDATE（`AttachmentTransferWorker.kt:448-475`）。新功能只走第一条登记和最后一条完成写。
- `expert_document` 有四条写路径：metadata matched 登记的 `ensureExpertDocument`（`MailAttachmentService.kt:271-291`）、metadata 未匹配邮件绑定后的 `ensureDocumentsForProcessingAttachments`（`:119-144`）、旧 content matched 的 `saveLegacyRecordAttachment`（`:298-330`）、显式历史修复 `ExpertMaterialService.reconcileApply`（`ExpertMaterialService.kt:599-637`）。新功能复用前两条；旧模式和 reconcile 不改语义。
- transfer 登记写入在 `MailAttachmentService` 和 `AttachmentTransferService`；状态写入全部收口在 `MailAttachmentTransferRepository.markRequested/tryClaim/renewLease/commitStored/failAttempt/recoverExpiredLeases`（`MailAttachmentTransferRepository.kt:77-229`）。本方案不增加第三套状态写路径。
- 文件名从不参与 worker 物理路径（`AttachmentTransferWorker.kt:562-589`）。Drive 下载必须复用该确定性 transfer 目录。

### 附件传输读路径和交互

- `AttachmentTransferWorker.downloadPart` 当前先查 sender account，再无条件调用 IMAP fetcher（`AttachmentTransferWorker.kt:357-427`）；这是唯一需要按 `partPath` 分派的下载入口。
- `ResolvedAttachmentPart` 已提供 `streamContent/maxBytes/abortReason/forceClose`（`ImapAttachmentContentFetcher.kt:62-73`），可让 HTTP 实现复用 worker 的租约、总时限和文件提交。
- `ExpertMaterialService` 现有查询已经 join document/attachment/transfer/source（`ExpertMaterialService.kt:772-807`）；`METADATA_ONLY/FAILED/SOURCE_UNAVAILABLE` 通过 `REQUESTABLE_STATES` 计算 `canFetch`，STORED 校验真实文件后提供下载/预览（`:317-381`）；空 content type 已按文件名推断 PDF/图片/文本，其他类型回退 `application/octet-stream`（`:464-477`）。
- 文件字节读取全部收口到 `ExpertMaterialService.resolveReadyFile/resolveReadyFileUnscoped` 的 owner + realpath 校验（`ExpertMaterialService.kt:87-152`；K-document-file-read-via-storage-path）；专家下载/预览由 `ExpertDocumentBrowseService.kt:92-110` 委托，邮箱附件下载由 `MailboxAttachmentService.kt:25-46` 委托，AI 文本提取由 `DocumentTextExtractor.kt:31-75` 委托。新文件写回同一 storagePath 后，这些读路径无需修改。
- `expert-materials.js` 已显示“仅文件信息/排队中/获取中/已存服务器/获取失败/来源不可用”（`:43-58`），POST 现有批量 transfer 接口（`:406-415`），并提供现有面板及两个获取按钮（`:444-565,671-725`）。前端零改动即可承接新数据。

### 不采用的方案

- 不把分享页 HTML直接保存：生产验证已证明响应为 `text/html`，不是附件。
- 不新增“Drive 导入”页面/API/表：现有材料链路已具备登记、授权动作、下载状态和文件读取。
- 不把 linked material 直接放进 MIME `attachments`：会污染 DMARC 检测。
- 不接 Google Drive OAuth/API：真实公开链接经固定下载端点已验证可取；OAuth 超出当前样本和授权范围。
- 不做通用 URL 下载器：超出受控 host/path，扩大 SSRF 面。

## 实现方案

### 阶段 1：先写识别契约测试，再扩展接收 DTO

- 修改 `ImapMailReceiveServiceTest.kt`，先加入失败测试：
  1. 纯文本真实样本解析出一份 `linkedMaterials`，名称为 `China_Collaborator.zip`、content=null、source 坐标完整、partPath=`gdrive:1eU...`；原 `body` 不变。
  2. HTML anchor、裸 URL、multipart/alternative 双版本去重为同一 fileId 一份。
  3. 引用历史内链接、HTTP、`drive.google.com.evil`、非 `/file/d/.../view`、空/非法/过长 id 均不登记。
  4. metadataOnly=false 不登记 linked material；真实 MIME attachment 行为和 bodyTruncated 保持原断言。
- 修改 `MailReceiveService.kt`：给 `ReceivedMail` 尾部增加唯一新字段 `linkedMaterials: List<ReceivedMailAttachment> = emptyList()`，不移动任何既有参数，以保持具名、默认及潜在位置构造兼容；修订 `ImapAttachmentSource.partPath` 注释为数字 MIME path 或 `gdrive:{fileId}`，其余来源字段仍是实际 IMAP 消息身份。
- 新增 `GoogleDriveMaterialSource.kt` 的纯解析部分：
  - 定义唯一常量前缀 `gdrive:` 和 fileId/path 校验；解析必须先用 `java.net.URI` 做 scheme/host/port/path 结构校验，再取 id，不能只用包含字符串或宽松 host 后缀。
  - 对 text/plain 直接生成扫描文本；对 text/html 单次原始有界文本先把 `<a href>` 变为“锚文本 <href>”，保留 `<br>/<p>` 行边界、去 script/style/其他标签并解实体；随后调用现有 `MailBodyCleaner.clean`，只在当前回复范围提取。
  - 文件名按 I-7 选择和截断；不新增 MIME 映射。
  - 结果按 fileId 保序去重，再构造 `contentType=null`、`content=null`、`encodedSize=null`、`disposition=external-link` 的 `ReceivedMailAttachment`。
- 修改 `ImapMailReceiveService.kt`：扩展内部 `NodeResult/WalkResult` 聚合 linked materials；每个 text leaf 的 `readBoundedText` 结果同时用于既有 body 和 Drive 扫描，禁止二次读流。`convertToReceivedMail` 写入新字段。仅 `properties.metadataOnly` 为 true 时调用解析器。
- 遵守：I-1、I-2、I-5、I-6、I-7。

### 阶段 2：在公共事务入口并入现有材料写链

- 修改 `AutoMailReplyServiceTest.kt`，先加入失败测试：
  1. `processSingle` 收到一份 MIME attachment 和一份 linked material 时，传给 matched `saveInboundAttachments` 及 `bridgeInboundProcessing` 的列表顺序为 MIME 后 linked，共两份。
  2. 未匹配/无首信路径传给 `saveUnmatchedAttachments` 的列表包含 linked material。
  3. DMARC 来信即使 `linkedMaterials` 非空，探测和 ingest 仍只收到原 MIME attachments，linked material 不建 MATERIAL transfer。
  4. `processByUids` 通过 `processSingle` 得到相同合并行为；空 linkedMaterials 的现有测试不变。
- 修改 `AutoMailReplyService.kt`：在 `processSingle` 开始事务前构造一次 `businessMail = received.copy(attachments = received.attachments + received.linkedMaterials, linkedMaterials = emptyList())`，并把它传给 `processSingleCore`；markSeen 仍使用原 UID。self-check/bounce/DMARC 位于调用前，因此仍读取原 MIME attachments。不得在各业务分支逐个追加。
- `MailAttachmentService`、`AttachmentTransferService`、repository、schema 不改：现有 metadata 分流、owner、bridge、幂等和 document 创建直接承接。
- 遵守：I-3、I-4、I-5、I-10。

### 阶段 3：先写 HTTP/worker 测试，再增加固定 Drive fetcher

- 修改 `AttachmentTransferWorkerIT.kt`：在现有真实 MySQL + worker 测试中加入本地 JDK HTTP fixture，通过 package-internal 测试构造入口把固定下载 host 替换为 loopback，生产构造始终使用 `drive.usercontent.google.com`。覆盖：
  1. `gdrive:{id}` QUEUED 行不依赖 sender account 查找，返回带 attachment disposition 的 200 字节流后落 `content.bin`、状态 STORED、`mail_attachment.file_size/storage_path` 正确。
  2. MIME 数字 partPath 仍调用原 IMAP fixture；同一 worker 混合两种来源均完成。
  3. redirect、200 text/html、缺失 attachment disposition、403/404 → SOURCE_UNAVAILABLE，目录无最终文件且 `.part` 清理。
  4. 500/连接中断 → FAILED；声明长度或实际流超过测试上限 → FAILED/LIMIT_EXCEEDED。
  5. 慢 HTTP 源在总时限触发 `forceClose/disconnect`，旧 worker 不提交，现有租约恢复语义不变。
- 在 `GoogleDriveMaterialSource.kt` 同文件新增 `@Component GoogleDriveAttachmentContentFetcher`：
  - 公共生产构造只接受 `MailAttachmentStorageProperties`；package-internal 测试构造只替换 endpoint factory，不允许业务传任意 URL。
  - `resolve(fileId)` 返回 `ResolvedAttachmentPart`；使用 `HttpURLConnection`，关闭自动 redirect，套用现有 connect/read timeout，验证 status/header/length 后才返回。
  - `streamContent` 使用 `properties.transferBufferBytes` 缓冲；每次读前后检查 abortReason，实际字节超过 maxBytes 抛 `LimitExceeded`；`close/forceClose` 都 disconnect。
  - 按 I-9 只抛现有 `AttachmentFetchException.SourceUnavailable/TransferFailed/LimitExceeded/Aborted`，消息固定脱敏。
- 修改 `AttachmentTransferWorker.kt`：在查 sender account 之前判别 source：
  - 数字 MIME partPath → 保持现有 account lookup + `ImapAttachmentContentFetcher.resolve`。
  - `gdrive:` → 严格解析 fileId + `GoogleDriveAttachmentContentFetcher.resolve`，不需要邮箱凭据。
  - 其他语法 → `SOURCE_UNAVAILABLE/UNSUPPORTED_SOURCE`，不触网。
  - 两种 fetcher 最终都交给现有 `.part`、stream、watchdog、commit/fail 逻辑；不得复制下载或提交代码。
- 修改 `MailAttachmentTransfer.kt` 注释，记录 `partPath` 的两种允许语义；不新增 enum/state/字段。
- 修改 `AttachmentTransferWorkerLifecycleTest.kt`：给手工构造 worker 增加 Drive fetcher，继续验证 ApplicationReady 启动和 context close 停止，防止新依赖破坏生命周期。
- 遵守：I-1、I-5、I-7、I-8、I-9、I-10。

### 阶段 4：组合回归与边界核验

- 运行定向单测，确认解析、业务合并、DMARC 隔离和 lifecycle。
- 运行 `-Pmysql-it` 的 worker IT，必须显式打开门禁；不能用默认 skip 当证据。
- 运行全量 `mvn test`，确认现有 MIME 附件、材料 API/UI JS 测试、DMARC、收信游标和其他模块无回归。
- 用 `git diff --check`；核对 diff 中没有 migration、controller、ExpertMaterialService 或前端文件。
- 在非生产测试邮箱做人工端到端验收；生产真实文件只用于已完成的可下载性证据，不在自动测试依赖外网。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReceiveService.kt` | `ReceivedMail.linkedMaterials`；远端来源注释 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt` | 单次 MIME 遍历提取并聚合 Drive linked materials |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/GoogleDriveMaterialSource.kt` | 新增严格 URL/文件名解析器和固定域 HTTP fetcher |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | `processSingle` 唯一合并点，保持 DMARC 隔离 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferWorker.kt` | 按 partPath 分派 IMAP/Drive fetcher，复用传输状态机 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachmentTransfer.kt` | partPath 双来源语义注释 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt` | plain/HTML/alternative/引用/恶意 URL/legacy 识别测试 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 合并写链、未匹配、processByUids、DMARC 隔离测试 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferWorkerIT.kt` | 本地 HTTP fixture 与 Drive worker 成功/失败/限流/中止 IT |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferWorkerLifecycleTest.kt` | 新 fetcher 构造依赖和生命周期回归 |

范围计数：10 个实现/测试文件、2 个子系统（收信识别/登记；附件传输）、0 个数据库字段、0 个迁移、0 个前端文件。达到 create-p 单计划上限但未超限，无需拆子计划。

## 验收标准

- I-1：只有规范 `https://drive.google.com/file/d/{validId}/view` 产生 linked material；恶意 host、HTTP、端口、其他 path、非法 id 自动测试均为 0 条。
- I-2：调用 `convertToReceivedMail` 只得到内存描述且 HTTP fixture 请求计数为 0；完成 `processSingle` 登记后 transfer 为 METADATA_ONLY、attachment 的 storagePath/fileSize 为空；直到显式 enqueue + worker start 才出现 HTTP 请求。
- I-3：DMARC detector/ingest 的 Mockito captor 只含 MIME attachment；普通专家信在 `processSingle` 捕获到 MIME+linked 两份；`processByUids` 同样。
- I-4：matched 路径生成一组 attachment/transfer/document；unmatched 路径生成 processing-owner attachment/transfer，绑定后只补一份 document；全部现有分支测试通过。
- I-5：同一消息 plain+HTML 重复 fileId 只产生 `partPath=gdrive:{id}` 一行；相同 fileId 在不同 UID 下各有一行；重复处理同一 UID 不复制。
- I-6：带引用旧 Drive 链接的新回复不登记旧链接；材料提取前后 `ReceivedMail.body`、bodyTruncated 和现有 intent 输入逐字一致。
- I-7：真实样本登记名严格为 `China_Collaborator.zip`；非法/非文件锚文本回退为 `GoogleDrive-{id}`；没有任何客户端文件名进入物理路径。
- I-8：32,880,706 字节真实样本在生产同网络条件可下载的证据已存在；自动 IT 证明 200 attachment 流保存、redirect/HTML/超限拒绝、慢源可由 watchdog 断开，且无 `.part` 残留。
- I-9：HTTP 确定性不可用显示 SOURCE_UNAVAILABLE；瞬时/5xx 显示 FAILED；超限为 LIMIT_EXCEEDED；error_message/log 断言不含 URL、body、Cookie、密码。
- I-10：现有 `GET /api/expert-contacts/{id}/materials` 直接返回新资料，现有 POST transfers 可入队，现有 UI 显示“仅文件信息→排队中/获取中→已存服务器”，无需前端 diff。
- 定向单测：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=ImapMailReceiveServiceTest,AutoMailReplyServiceTest,AttachmentTransferWorkerLifecycleTest test`。
- 显式 IT：同一 Java 11 环境运行 `mvn -Pmysql-it -Dtest=AttachmentTransferWorkerIT test`；必须 0 failure/0 error/0 skip。
- 全量门禁：同一 Java 11 环境运行 `mvn test`；`git diff --check` 通过。

## 人工验收清单

### A-1: 真实样本只登记到现有面板
- 前置条件: 测试邮箱绑定已知专家；`metadataOnly=true`；邮件正文含本计划真实样本，且不附 MIME 文件。
- 操作步骤: 1. 执行“检查回复”；2. 打开该专家现有“专家上传资料”面板；3. 不点击获取。
- 预期结果: 仅新增一行 `China_Collaborator.zip`；状态“仅文件信息”、实际大小未知、来源主题/时间为该来信；服务器不存在 content.bin；没有新页面或新按钮。
- 覆盖: I-1、I-2、I-4、I-5、I-7、I-10

### A-2: 通过现有按钮下载真实文件
- 前置条件: 完成 A-1；Drive 分享权限仍允许持链接访问。
- 操作步骤: 1. 勾选该行；2. 点击“获取所选到服务器”；3. 等待状态完成；4. 点击“下载到电脑”；5. 计算 SHA-256。
- 预期结果: 状态依次进入排队/获取并最终“已存服务器”；实际大小为 32,880,706；下载文件是 ZIP；SHA-256 为 `64817085955e153c6ce7a3b486898ce397e23469eba13cd38859b1412d1ba52a`；只产生一份 attachment/transfer/document。
- 覆盖: I-2、I-4、I-5、I-8、I-10

### A-3: HTML 与 alternative 去重
- 前置条件: 测试邮件同时含 plain 和 HTML alternative；HTML 使用 `China_Collaborator.zip` 锚文本指向同一 Drive URL。
- 操作步骤: 1. 检查回复；2. 打开资料面板；3. 按来源筛选该来信。
- 预期结果: 同一 fileId 只有一行，文件名正确；来信正文展示及自动回复判断与改动前一致。
- 覆盖: I-5、I-6、I-7

### A-4: 引用历史不重复导入
- 前置条件: 首封邮件已导入一个 Drive 链接；专家第二封回复只在 quoted history 中带回旧链接，当前回复无新链接。
- 操作步骤: 1. 检查第二封回复；2. 查看两封来源对应的材料。
- 预期结果: 第二封不新增旧链接材料；第一封原材料保持一份。
- 覆盖: I-5、I-6

### A-5: 私有或不可下载文件
- 前置条件: 发送一个相同规范 URL，但目标文件不允许持链接访问或已删除。
- 操作步骤: 1. 检查回复；2. 在现有面板点击获取；3. 等待 worker 结束。
- 预期结果: 元数据先正常登记；获取后为“来源不可用”并显示脱敏原因；无最终文件、无 `.part`；其他邮件处理不受阻塞。
- 覆盖: I-2、I-8、I-9、I-10

### A-6: 恶意 URL 不登记
- 前置条件: 邮件正文分别含 HTTP、`drive.google.com.evil`、带非默认端口、`/open?id=`、Drive folder URL。
- 操作步骤: 1. 检查回复；2. 查看该来源材料。
- 预期结果: 这些 URL 均不新增材料、不产生 transfer、不发出外部 HTTP 请求；真实 MIME 附件仍正常登记。
- 覆盖: I-1、I-4、I-10

### A-7: MIME 与 Drive 混合回归
- 前置条件: 已知专家邮件同时附一个 PDF MIME 文件，并在正文含一个 Drive ZIP 链接。
- 操作步骤: 1. 检查回复；2. 查看专家资料；3. 只选择 ZIP 获取；4. 再选择 PDF 获取；5. 回到该来源邮件的附件区分别下载。
- 预期结果: 同一现有专家资料面板显示两行；可独立选择和下载；ZIP 走 HTTP、PDF 走 IMAP；来源均指向同一来信；来源邮件附件区也显示两份且下载内容一致；无重复文档。
- 覆盖: I-3、I-4、I-5、I-8、I-10

### A-8: DMARC 回归
- 前置条件: 测试 DMARC 报告邮件带合法报告 MIME 附件，正文额外出现规范 Drive 链接。
- 操作步骤: 1. 检查回复；2. 查看 DMARC 处理记录和专家材料表。
- 预期结果: DMARC 仍只登记/解析报告 MIME 附件并正常 markSeen；正文 Drive 链接不进入报告列表、不创建专家 MATERIAL。
- 覆盖: I-3、I-9

### A-9: 待匹配来信绑定后进入同一资料清单
- 前置条件: 用系统未识别的发件地址发送含真实样本链接的邮件；后台存在可绑定的测试专家。
- 操作步骤: 1. 检查回复；2. 在“待匹配”页找到该邮件；3. 使用现有绑定动作绑定测试专家；4. 打开该专家“专家上传资料”。
- 预期结果: 绑定前该邮件显示在“待匹配”；绑定后它从“待匹配”移出，测试专家资料总数恰好增加 1，新增行名为 `China_Collaborator.zip`、状态“仅文件信息”；刷新页面不再增加第二行。
- 覆盖: I-4、I-5、I-10

### A-10: 旧附件模式回归
- 前置条件: 测试环境临时设置 `metadataOnly=false` 并重启；准备一封同时含 PDF MIME 附件和 Drive URL 的测试邮件。
- 操作步骤: 1. 检查回复；2. 打开专家资料；3. 下载 PDF。
- 预期结果: PDF 继续按旧模式在收信时直接保存并可下载；Drive URL 不新增材料；无 `gdrive:` transfer。
- 覆盖: I-2、I-4、I-10
