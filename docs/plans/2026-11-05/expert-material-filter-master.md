# 专家材料签名附件过滤：主计划

状态：DRAFT，待审阅；仅规划，未改产品代码、未执行测试、未访问生产数据库或邮箱。
日期：2026-11-05（Asia/Shanghai）。代码基线：`71bb4879c77c9fcb69f1688e7192ad9aea5c0c4b`。
工作区已有大量未提交改动；源文件指纹见 [source-manifest.json](expert-material-filter-evidence/source-manifest.json)。执行时先核对指纹和实际 diff，不覆盖他人改动。

## 需求描述

O-1：数字签名附件不再出现在专家材料列表、材料总数、待获取数、旧文档入口和相关消息附件列表；历史记录在代码上线后重新查询即排除。截图对应的 **3 个 `smime.p7s` + 1 个 DOCX + 1 个 PDF**，在这五条确为全部记录的前提下，结果为 **2 份、已存 2、待获取 0**。

O-2：新收信、直接业务入口、后续绑定/补建和手动上传都不能把签名注册成专家材料；用户携带历史签名 ID 请求获取、预览、下载、打包或新 AI 提取时，后端拒绝。

O-3：邮件附件标记、预览提示和运营状态只读对账使用相同的签名排除规则；新增“只有签名”的来信不再因附件存在而推进 `MATERIALS_RECEIVED` 或推断 `DOCS_ATTACHED`。

必须保持：
- N-1：DOCX/PDF、证书扫描图、正常 JPG/PNG 附件和手动上传图片正常可见、计数、下载；不按大小、`image*` 文件名或 `image/*` 类型一刀切。
- N-2：保留原始邮件、历史 attachment/document/transfer 行和已经保存的原件；GET 不写库、不触发 IMAP 下载；来源归属、路径校验、审核状态不被绕过。
- N-3：保留收信事务、UID 去重、游标、异常不确认语义；DMARC/退信/自检和 Google Drive 外链入口语义保持。
- N-4：不自动回退历史运营状态、不删除历史意图/AI 结果、不重新发信、不修改人工审核及 `expert_material_status` 材料目录状态。

明确排除范围：
1. **不新增通用“无效图片”识别。** 截图证据是 `.p7s`；未提供装饰图原始 MIME。`disposition=inline`、CID、尺寸、文件名都不能单独证明图片没有业务价值。已有“无文件名、非 attachment 的非文本叶”过滤保留，带文件名图片保留。历史 CID/HTML 关联补查、人工忽略 UI、OCR/AI 图片分类另立需求。
2. 不做 `.p7m/.p7c/.asc/.sig` 扩展名黑名单，不解密、不验证签名、不解压 `winmail.dat`，不修改出站附件和会议日历。
3. 不物理清理历史记录、不批量改 `operator_status`/ES 层级、不撤销历史 promotion；“材料数量已纠正”不等于历史“已回复材料”标签自动消失。
4. 不取消上线前已经 QUEUED/DOWNLOADING 的任务。它们可按现有 worker 收敛；新读取仍过滤，新材料获取 API 拒绝再次请求签名。重启 worker、清理队列不属于本计划。
5. 不改 UI、API DTO 字段、数据库 schema、依赖或配置；不发布、不部署。

## 关键不变量

### Invariant I-1：唯一、保守、可复算的签名规则
- Rule：本次唯一新增排除条件为规范化 MIME 等于 `application/pkcs7-signature` 或 `application/x-pkcs7-signature`，**或**规范化文件名以 `.p7s` 结尾。规范化：文件名仅去首尾 ASCII 空格，再 `lowercase(Locale.ROOT)`；MIME 先截取 `;` 前首段，再去首尾 ASCII 空格、小写。null MIME 当空串。其他一律保留；不按人工/邮件 owner、文件大小、存储状态、审核状态改变规则。ASCII 之外的空白不额外推断为协议分隔符。
- Applies to：所有子计划的 MIME 读取、附件写入、材料查询、ID 操作校验、SQL 计数、只读对账。
- Violation consequence：历史/新增、Kotlin/SQL、不同 UI 入口的数量和可操作性不一致，或误删正常材料。
- 来源：original（拟定产品规则；不声称已验证文件字节）。

### Invariant I-2：历史修复是业务读取过滤
- Rule：使用已有 `mail_attachment.file_name/content_type` 即可排除命中记录，无需用户逐个操作，也不依赖重新收信。既有行、原件和 review/transfer 状态不改写。列表分页前过滤，summary 对过滤后的全体材料统计。
- Applies to：`ExpertMaterialService`、旧文档/联系人详情、会话计数、消息附件、旧邮箱 SQL。
- Violation consequence：只修新数据，截图中的旧记录仍存在；或分页总数与内容分裂。
- 来源：K-mail-attachment-write-paths、K-attachment-metadata-consumer-chain。

### Invariant I-3：业务入口集合与写入集合一致
- Rule：`processSingle` 中用于保存、附件数、意图、promotion、桥接的附件集合一致；保留原 MIME part 路径与先后顺序，过滤不重排 partPath。登记服务独立兜底。桥接只要求有效材料的 transfer 存在；有效材料缺登记仍必须失败。
- Applies to：`AutoMailReplyService.processSingle`；`MailAttachmentService.saveInboundAttachments/saveUnmatchedAttachments/bridgeInboundProcessing/ensureDocumentsForProcessingAttachments/inferPrimaryIntentFromAttachments`。
- Violation consequence：只过滤持久化却仍误推进状态，或签名未登记导致整封信桥接失败。
- 来源：K-process-single-all-callers、K-linked-materials-after-machine-mail-routing。

### Invariant I-4：ID 操作不能绕过列表
- Rule：材料获取、scoped/unscoped 文件解析在读字节/入队前排除签名。类型混选且其他 ID 归属有效、文件已就绪时整体 HTTP 400，不能先获取有效部分或输出部分 ZIP。若另有越权/不存在/普通文件未就绪，沿用既有先遇到的错误优先级，不强行覆盖成类型错误；所有失败均零部分输出。沿用 `BAD_REQUEST`，固定业务提示“所选文件包含邮件数字签名，不属于专家材料”。现有归属/权限校验优先，不能借过滤泄露他人文件名。
- Applies to：`ExpertMaterialService.requestTransfers/resolveReadyFile/resolveReadyFileUnscoped`；由此被保护的 ZIP/预览/下载/文本提取。
- Violation consequence：旧浏览器选中集合或手工构造请求仍可消费已隐藏项。
- 来源：K-expert-document-ownership-chain。

### Invariant I-5：先按原规则解析来源，再过滤结果
- Rule：`resolveMessageAttachments` 保持现有 direct → bridge → 唯一 legacy 的选择顺序，分支选择依据**原始集合**。direct 集合只有签名时最终返回空，不因过滤为空而转去 bridge/legacy 搜索别的附件；不同 owner/账号/消息的文件不能串出。
- Applies to：消息列表、旧邮箱详情、附件列表与其调用者。
- Violation consequence：过滤造成来源回退，展示另一来源的文件。
- 来源：K-calendar-not-expert-material-owner、K-expert-document-ownership-chain。

### Invariant I-6：业务状态不做历史自动降级
- Rule：新增签名不作为 promotion/意图的附件证据；历史对账只去掉签名事件证据。已有 `operator_status`、ES `applicationIndexed/currentIndexLevel`、人工覆盖、历史意图、AI 结果不重写；邀请及人工终态的既有优先级不变。
- Applies to：新增 `processSingle`、`OperatorStatusReconcileService.reconcile`；只读 `ExpertContactManagementService`。
- Violation consequence：把不该降级的专家打回，或把对账报告误当修复完成。
- 来源：K-operator-status-single-writer、K-material-status-cv-reader-and-route-collision。

## 现状审计

### 证据边界与可复查回执

- [store-access.txt](expert-material-filter-evidence/store-access.txt)：生产 Kotlin 与 scripts 的表名、仓储类型、别名调用检索。
- [schema.txt](expert-material-filter-evidence/schema.txt)：全部迁移中的三张核心表引用。
- [material-consumers.txt](expert-material-filter-evidence/material-consumers.txt)：材料计数、获取、解析、入口调用链。
- [executable-store-scan.txt](expert-material-filter-evidence/executable-store-scan.txt)：当前 checkout 的额外可执行脚本与 SQL 扫描，覆盖 tmp/、docs/。
- [test-gates.txt](expert-material-filter-evidence/test-gates.txt)：实际测试开关及行号守卫。
- [source-manifest.json](expert-material-filter-evidence/source-manifest.json)：检索命令、退出码、文件行数及 SHA-256。检索使用 `--no-ignore`：当前环境默认 rg 会漏掉被忽略路径。
- 下列 `file:line` 均针对本次基线，执行前必须重新定位方法。检索“未发现”仅限清单覆盖的仓库文件；未读生产数据，不声明线上具体 attachmentId、MIME、影响人数。
- 协议依据：[`RFC 8551 §3.2/§3.5`](https://www.rfc-editor.org/rfc/rfc8551.html) 描述签名 MIME 与 `.p7s`。代码证明的是当前判定和传播行为；黑名单是本计划明确的新规则。

### S1：mail_attachment + expert_document

Schema/mapping：
- `V7__create_mail_attachment_and_expert_document.sql:1/14`：附件 file_name/content_type/file_size/storage_path；document 关联 contact/attachment，类型和审核状态独立；没有业务有效性字段。
- `V36__add_mail_attachment_inbound_processing_link.sql:1`：增加 processing owner，record owner 可空。
- `V118__allow_attachment_metadata_only.sql:6`：size/path 可空、filename TEXT。
- `V130__add_manual_expert_material_upload.sql:26/34/63`：manual 上传来源、第三类 owner、owner 三选一；无 CID 字段。owner CHECK 在 MySQL 5.7 可能不执行，保持代码侧构造约束。
- `MailAttachment.kt:20`、`ExpertDocument.kt:8`：模型与上述字段一致。`expert_document` 原 DDL **没有 attachment 唯一约束**，本计划不顺带去重；已有行数语义保留。

以下源码路径省略统一前缀 `src/main/kotlin/com/weibo/talentintroduction/`；迁移位于 `src/main/resources/db/migration/`。全部已发现应用生产写路径（不将入口和内部 save 重复算成两条独立写链）：

| 写路径 | 源码证明 | 写入/本次处理 |
|---|---|---|
| metadata 登记 | `mail/service/MailAttachmentService.kt:202/236/248` | attachment + transfer；02 入口过滤后仍复用来源唯一键 |
| legacy record 登记 | 同文件 `:298/309/311/322` | 文件 + attachment + document；02 过滤前置 |
| legacy processing 登记 | 同文件 `:333/343/345` | 文件 + attachment；known contact 经 ensure 补 document；02 过滤前置 |
| 关联文档幂等补建 | 同文件 `:272/283` | `ensureExpertDocument`；调用集合受 02 控制 |
| 未匹配转绑定补建 | 同文件 `:124/133` | processing-owner → document；02 再判签名 |
| 显式 reconcile | `document/service/ExpertMaterialService.kt:655/663` | document 补建；01 dryRun/apply 同规则排除 |
| 手动上传 | `document/service/ManualExpertMaterialUploadService.kt:78/103/112/126` | manual source + attachment + document + 原件；02 在落盘前拒绝 |
| worker 回写 | `mail/service/AttachmentTransferWorker.kt:477/498` | `storage_path/file_size` UPDATE；不变；不能复活被读取过滤的记录 |

**额外离线写入**：仓库 `tmp/lucio-dorneles/register_materials.sh:34/39–51/69–87` 有直接查重、复制文件、INSERT manual_upload/attachment/document 的一次性脚本。三个输入文件名固定为 PDF，MIME 固定 application/pdf（:18–27/:71/:78/:85）；本次规则全部保留，不修改/执行该脚本。它不是动态上传服务，但必须列入写入审计。脚本失败时清本次原件，成功清暂存文件（:51/:96），不属于本次历史清理。

迁移检索只发现上述建表/改表，没有签名清理迁移；scripts/ 内对三张表未发现命中，但不据此断言仓库其他目录无写方。扩展扫描见 [executable-store-scan.txt](expert-material-filter-evidence/executable-store-scan.txt)，排除独立旧 worktree、build 输出和明确历史备份后发现上述 tmp 脚本。无本次 DELETE/UPDATE 历史数据路径。（来源：K-mail-attachment-write-paths）

全部已发现生产读取路径及处置：

| 读者 | 依赖 | 处置 |
|---|---|---|
| `ExpertMaterialService:250/269/304/550` | SQL rows → 列表/筛选/summary | 01，assemblePage 起点过滤 |
| `ExpertMaterialService:108/128/595` | doc owner、attachment owner、path、transfer | 01，ID guard；归属校验保持 |
| `ExpertMaterialService:678` | missing document 候选 | 01，两个 reconcile SQL 都加条件 |
| `ExpertMaterialService:707` | direct/bridge/legacy | 01，保持来源选择后过滤 |
| `ExpertDocumentBrowseService:46/101` | doc 列表、attachment MIME/path | 01，列表过滤；下载委托已有 guard |
| `ExpertContactManagementService:69–81` | mails→attachments 与独立 documents | 01，两个数组均处理；processing/manual 文档不能因无 record 而丢失 |
| `MailboxConversationRepository:515` | 直接 `COUNT(*) FROM expert_document` | 01，JOIN attachment 后按统一谓词计数 |
| `MailboxConversationService:200/247/350` | 上述计数/消息附件结果 | 无需改，随 01 生效 |
| `ExpertMaterialArchiveService:29/32` | resolveReadyFile；未就绪错误文件名 | 无需改生产代码，01 加混选测试 |
| `DocumentTextExtractor:34/59` | 全部解析 ready 后才读取内容 | 无需改，01 guard 保护新提取 |
| `ExpertDocumentAnalysisService:128/195` | 旧分析结果的来源文件名回显 | 保留历史结果；不新增分析消费旁路 |
| `MailboxAttachmentService:35/39` | 消息列表 + unscoped ready | 无需改，01 guard 保护 |
| `MailboxService:205/313/331` | 实体投影/消息详情 | 03 修实体 hasAttachment；详情随 01 |
| `MailRecordRepository:514/545/782/821` | 四个 EXISTS has_attachment 投影 | 03 全部修；不扩写 owner/消息匹配逻辑 |
| `AutoReplyPreviewService:56–58` | processing 直接附件存在性 | 03 只调整提示布尔值，不改变预览分类动作 |
| `OperatorStatusReconcileService:57/70/207` | record-owner 附件事件 → 期望状态 | 03 过滤签名；只读报告，不回写 |
| `MailAttachmentService:129/232/279` | 绑定补建、重入幂等 | 02 补建过滤，保留源身份/幂等原始查找 |
| `AttachmentTransferService:218/273/391` | 原始 attachment、doc owner、磁盘 ready | 不改；唯一生产 enqueueMaterial 调用位于 `ExpertMaterialService:617`，由 01 全批校验保护 |
| `ExpertMailPermissionGuard:197` | attachment 三类 owner 权限链 | 保留，不在底层仓储隐藏行或破坏审计/权限判断 |

底层 `MailAttachmentRepository`、`ExpertDocumentRepository` 继续返回原始事实，不将通用 find/save 改成隐式过滤器。

### S2：mail_attachment_transfer

Schema：`V119__create_mail_attachment_transfer.sql:25–69`，source 五元组唯一、attachment_id 唯一、purpose `MATERIAL/DMARC`；保存 disposition，**不保存 Content-ID、HTML cid 引用、图片尺寸**。已有 6 种 state 不增加。

写路径：`MailAttachmentService.registerMetadataAttachment:248` 新建；`bridgeInboundProcessing:184` 补 processing；`AttachmentTransferService.register:123` 新建（含 DMARC）；`enqueueMaterial:322`、`enqueueTransferByIds:376` 请求；`MailAttachmentTransferRepository:81/102/140/162/187/215` 分别覆盖入队、领取、续租、提交、失败、租约恢复。调用 worker 见 `AttachmentTransferWorker:166/213/247/488/530/578`。02 只阻止新增签名进入材料登记，01 只阻止用户新请求；不新增状态、不取消已有任务。

读路径：`MailAttachmentService` 的 source 幂等/桥接；`ExpertMaterialService` 的列表状态/来源/排队上限；`AttachmentTransferService` 的幂等/排队；`AttachmentTransferWorker` 的候选/租约/purpose；通用 repository 的 count/find；无新增读者。DMARC transfer 无 attachment_id，不能套本次材料过滤。

### S3：附件文件系统

写路径：离线 `tmp/lucio-dorneles/register_materials.sh:39–51/96` 复制原件/失败清理/成功清暂存（本次不执行）；`MailAttachmentService:306/309/340/343` legacy 文件；`ManualExpertMaterialUploadService:93–98/167–195` 流写临时文件、原子转正、失败清理；`AttachmentTransferWorker:294–375/599–625` `.part`/final/清理；`ExpertMaterialArchiveService:48–82/99` 只生成下载临时 ZIP 并 finally 删除。

读路径：`ExpertMaterialService:134–177/531–546` 路径及就绪检查；`AttachmentTransferService:399` 文件就绪；worker 已存在 final 复用；`ExpertDocumentBrowseService:101` 下载；`MailboxAttachmentService:39` 下载；`DocumentTextExtractor:65` 内容读取；`ExpertMaterialArchiveService:40–75` ZIP 流；不把路径或二进制内容用于签名分类。02 对命中项在任何落盘前退出/拒绝；01 guard 在读取签名字节前拒绝；原件不删。

### S4：入站内存附件集合、状态与 UI 事实

- `ImapMailReceiveService.kt:554/594–606`：带文件名立即判附件；`:608–644` 登记 metadata 或读取 bytes。`:580` 只保护未归为附件的二进制叶。已有过滤不包含命名签名。
- `MailReceiveService.kt:84–102` 只携带文件名、MIME、bytes/source；source 有 disposition，无 CID。不能从历史表恢复未保存的 CID。
- `AutoMailReplyService.kt:108/121–129` 统一入口合并外链；`:380/:438` 两条禁用分支直接以 received.attachments.size 做 promotion，正常分支 `:550` 用 savedDocuments.size。只改 save 会漏状态。
- `MailAttachmentService.kt:358–366` 非空附件无 CV 即 DOCS_ATTACHED；`AutoMailReplyService:1371–1379` 在 UNKNOWN 时采用。
- `AutomaticApplicationPromotionService.kt:40–57` 有材料即推进 MATERIALS_RECEIVED；`ExpertOperatorStatusService.kt:46–66` 单调推进，不回退历史状态。
- `static/app.js:661/670` 中“已回复材料”是 MATERIALS_RECEIVED 的持久状态标签，**不是按材料条数即时计算**。因此不能承诺它随历史列表过滤自动消失。
- `static/mailbox-chat.js:3768/3785` 材料按钮用后端 materialCount；`ExpertMaterialController.kt:77/92/116/136` 是列表/获取/上传/补建入口；现有前端无需改 DOM/CSS。
- `campaign/service/ExpertMaterialService` 与本次 document 同名服务不同：目录请求状态独立。本计划不改 `expert_material_status` 或 RAG pending materials。（来源：K-material-status-cv-reader-and-route-collision）

### 跨路径 interaction points

| 编号 | 写→读/调用链 | 必须覆盖的场景 | 子计划/人工项 |
|---|---|---|---|
| IP-1 | 旧 attachment/document→新材料页/会话计数/旧文档详情 | 历史 5→2、三 owner、分页 | 01 A-1/A-2/A-4 |
| IP-2 | 旧 ID→transfer/ZIP/preview/AI | 混选整批失败、零部分结果 | 01 A-3 |
| IP-3 | processing 历史附件→绑定/reconcile→文档 | 签名不被补建，普通附件幂等补建 | 01 A-5；02 A-4 |
| IP-4 | MIME/直接 processSingle→保存/意图/promotion/bridge | 仅签名、签名+CV、禁用分支 | 02 A-1/A-2/A-3 |
| IP-5 | manual upload→磁盘/三表→材料页 | 签名拒绝、图片/零字节正常 | 02 A-5 |
| IP-6 | 旧 attachment→旧邮箱两 SQL/实体/预览提示 | 仅签名标志 false，普通附件 true | 03 A-1/A-2 |
| IP-7 | 旧 attachment/mail_record→状态对账 | expected REPLIED，DB 不回退 | 03 A-3/A-4 |
| IP-8 | DMARC/Drive/UID 入口→业务事务 | 机器信不因过滤进业务、Drive 合并顺序不变、重复 UID 不重跑 | 02 A-6/A-7 |

## 实现方案

按 **01 → 02 → 03** 顺序执行，每份独立验证；01 提供历史读取保护，可先部署；02 停止新增污染；03 收齐旧查询和只读对账口径。中间阶段不能宣称全部入口统一，完整需求在三份均通过后交付。

1. [01 历史读取与操作保护](expert-material-filter-01-read.md)：I-1/I-2/I-4/I-5，9 个文件。
2. [02 新增材料入口](expert-material-filter-02-ingress.md)：I-1/I-3/I-6，9 个文件，依赖 01 的纯规则。
3. [03 旧投影与状态对账](expert-material-filter-03-projections.md)：I-1/I-2/I-6，9 个文件，依赖 01 的规则与消息解析。

不增加共享字段，不新增缓存。子计划仅有“材料服务/入站写链”和“旧投影/只读对账”两个相互有明确数据合同的业务面；各子计划至多两个可独立验证子系统。

执行前重新核验 manifest 的源码指纹；发现不匹配先审查变化与本计划证据，必要时修订计划，不能按旧行号机械写代码。每个子计划的列表是唯一产品改动白名单。代码片段与测试约定在子计划中固定，不能把图像识别、历史状态回退加入实现。

## 变更文件清单

本主计划是编排文档，不直接授权额外产品文件；精确清单见三个子计划及 [change-manifest.json](expert-material-filter-evidence/change-manifest.json)。

| 执行单元 | 文件数 | 主计划引用 |
|---|---:|---|
| 01 | 9 | `expert-material-filter-01-read.md` |
| 02 | 9 | `expert-material-filter-02-ingress.md` |
| 03 | 9 | `expert-material-filter-03-projections.md` |

**去重合计 27 个产品/测试文件：13 个生产文件、14 个测试文件；4 个新增文件，23 个既有文件。** 这是基于完整调用链后的清单，替代聊天中 12–16 的粗估；计划、证据、知识维护文件另计。子计划间没有重复修改的产品文件。

## 验收标准

- I-1：Kotlin 规则与真实 MySQL 谓词使用同一输入矩阵；大写、MIME 参数、null MIME、普通图片、`.p7m` 均有明确断言。
- I-2：三 owner、历史 transfer 六状态和无 transfer；材料列表/summary/会话 count/旧文档/联系人详情一致；GET 零数据库 DML/零 IMAP。
- I-3：新附件登记、状态和意图使用相同有效集合；签名 stream 不能被打开，过滤后保留 partPath；有效材料缺 transfer 仍失败，不推进确认。
- I-4：仅签名/混选均 400，批次无入队、无 ZIP 和无提取；普通材料仍走既有权限、path 和 ready 检验。
- I-5：direct 只有签名且 bridge 有普通附件时，结果必须空；仍维持原始来源优先级。
- I-6：新签名不推进材料状态；历史只报告差异，人工覆盖和既有终态不改；无生产 ES/DB 状态回写代码新增。
- 每份子计划机器验证通过后再做人工 A-n；尚未执行的测试不能记 PASS，`skipped>0` 不能证明对应门禁通过。不在本次规划阶段生成 acceptance 勾选文件。
- JDK 11：本机核验存在 `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`；新真实 SQL IT 使用仓库已有 Testcontainers MySQL 依赖 (`pom.xml:88`)，隔离数据库，不连接默认开发库。01/03 IT 显式 `-DmysqlIt=true` 并确保实际执行。
- `MailboxConversationRepositoryIT.kt:1418–1431` 会全表 DELETE，不直接在日常开发或生产数据库运行该旧 fixture；新 IT 必须自管临时 MySQL 容器。IMAP IT 虽同名 mysqlIt 开关，实际是本地 socket fixture (`ImapMetadataFetchIT.kt:39–49`)。

## 人工验收清单

本节是整体验收入口；子计划 A-n 提供逐条权威步骤。开始人工验收时再导出同目录同前缀 acceptance 文件。

### A-1：截图场景与历史安全
- 前置条件：在隔离测试环境、旧版本中，用测试邮箱发送三封各含一个 `smime.p7s` 的来信，其中一封附 DOCX；检查回复仅登记元数据，手动上传一个 PDF，再获取 DOCX，记录附件 ID、文档审核状态和原件哈希。此时 5 份、stored=2、metadataOnly=3。不得把截图姓名当唯一身份，使用测试 contactId。
- 操作步骤：1. 完成三子计划并部署测试环境；2. 重新打开专家材料与旧文档列表；3. 刷新会话；4. 用记录的旧签名 ID 调用获取/ZIP/下载；5. 比较升级前后的原始行和原件哈希。
- 预期结果：材料 2、stored=2、metadataOnly=0；三份签名不显示；混选操作 400、有效项不被部分入队；原始行/审核状态/哈希不变。历史持久状态不因刷新改变。
- 覆盖：O-1/O-2、N-1/N-2/N-4、I-1/I-2/I-4、IP-1/IP-2。

### A-2：新收信与普通材料回归
- 前置条件：隔离测试邮箱与已发首信的测试专家，运营状态 REPLIED；自动发送关闭以免验收发出真实自动邮件。
- 操作步骤：1. 发正文 `fixture-neutral-01`、只附 `smime.p7s` 的邮件并检查回复；2. 再发签名+`cv.pdf`；3. 再发 `certificate.png`；4. 手动上传 `scan.jpg`；5. 按 02 A-6/A-7 验证 DMARC/Drive/去重。
- 预期结果：材料数依次 0、1、2、3；第一封不推进 MATERIALS_RECEIVED，第二封保留既有推进；正常文件可下载；重复检查不重复创建或回复。
- 覆盖：O-2/O-3、N-1/N-3、I-3/I-6、IP-4/IP-5/IP-8。

### A-3：绑定、旧查询、对账
- 前置条件：按 01 A-5/02 A-4 准备未匹配 processing-owner 普通材料+签名；按 03 A-3 准备仅签名但历史状态为 MATERIALS_RECEIVED 的测试专家，无邀请、无人工覆盖。
- 操作步骤：1. 绑定并补建两次；2. 查看旧邮箱、任务邮件列表、自动回复预览；3. 调用只读运营对账；4. 刷新状态。
- 预期结果：只补普通材料一份；仅签名的 hasAttachment/attachmentIntentIgnored 为 false；对账 expected=REPLIED、dbStatus=MATERIALS_RECEIVED，DB/ES 没有自动回退。
- 覆盖：O-3、N-4、I-2/I-5/I-6、IP-3/IP-6/IP-7。

## 自检记录

- [x] 事实/拟定规则/未知信息已区分；每项根因均有源码定位与检索回执。
- [x] 三张表及文件系统写读路径已核查；没有把 schema 缺失字段当作可用。
- [x] 3 份子计划均 9 文件、无新持久字段、无前端文件；样式契约不适用。
- [x] 新旧读写入口、旁路 ID、补建、原始来源优先级和人工状态边界已定义。
- [x] 各子计划具备 I-n/任务/测试/A-n 映射；暂不导出 acceptance。
- [x] Phase 0 已使用 9 条知识并增加 hit_count/last_used；未使用的同目录主题不引入本计划。
- [x] 相关知识均在 90 天窗口内或本轮被使用，无本轮需衰减归档项；同主题不足 5 条独立重复项，不强行合并。
- [x] 本轮无条目新跨越 hit_count=10；已超阈值项不重复追加 CLAUDE.md。新证据回写知识，未修改产品源代码。

知识级联检查：当前 checkout 未发现 `agents/*.md` 或 `templates/project-CLAUDE.md`，无可追加角色指针的既有目标；不为本次计划新建角色体系。新增知识为规划审计事实，尚未推广为已实施过滤规则。
