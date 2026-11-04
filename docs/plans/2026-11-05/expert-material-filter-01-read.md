# 01：历史签名附件读取过滤与操作保护

状态：DRAFT。依赖：无；为 02/03 提供规则。基线/完整审计见 [主计划](expert-material-filter-master.md)。本子计划 9 文件（生产 5、测试 4），没有 schema/frontend 变更，可独立上线修复历史材料页。

## 需求描述

O1：现有签名记录在材料列表/summary/会话计数/旧文档/联系人详情及消息附件中被排除，分页与数量一致。O2：携带历史签名 ID 的获取、下载、预览、ZIP、新 AI 提取被拒绝，补建不能把旧签名重新登记成材料。

保持：普通 PDF/DOCX/图片/手动上传材料（含 0 字节）可见；权限、来源选择、审核状态、ready 状态与原始行/文件不变；GET 零写入、零网络。

范围外：新收信和手动上传入口（02）、旧邮箱 has_attachment/预览提示/运营对账（03）、图像内容识别、历史状态降级、底层仓储方法改语义、已有队列取消。

## 关键不变量

### Invariant I-1：统一分类与 SQL 一致
- Rule：严格复用主计划 I-1 的两种 MIME 或 `.p7s` 规则；大小/图片/CID/owner 不参与。未知类型保留。禁止 `.p7m` 连带排除。
- Applies to：新建 `ExpertMaterialEligibility`；01 全部读取和补建。
- Violation consequence：错排真实材料或 Kotlin/SQL 数量不一致。
- 来源：original。

### Invariant I-2：分页前过滤，历史零迁移
- Rule：`assemblePage` 在 resolve/filter/sort/pagination/summary 之前取得有效 rows；summary 对所有有效 rows，搜索结果 total 对搜索后有效 rows。raw attachment/document/transfer 不更新。
- Applies to：材料页、旧文档、联系人详情、会话计数。
- Violation consequence：空页/错误总数或读取产生破坏性修复。
- 来源：K-attachment-metadata-consumer-chain。

### Invariant I-3：整批先校验，文件读取沿用权限链
- Rule：主计划 I-4；混选签名+普通 ID 获取整体 400，队列零改变；ZIP/文件解析若同时有普通文件未就绪或越权，保留原有错误优先级，以下类型400断言以其他ID归属有效且已就绪为前提；ZIP/提取在所有 ID 校验前不读文件内容、不输出内容。scoped 校验 doc/contact/owner 后再判类型；unscoped 仍依赖既有权限拦截。报错统一 `BAD_REQUEST` + “所选文件包含邮件数字签名，不属于专家材料”。
- Applies to：requestTransfers、resolveReadyFile、resolveReadyFileUnscoped；其 ZIP/预览/下载/提取调用者。
- Violation consequence：隐藏项仍可消费或部分成功。
- 来源：K-expert-document-ownership-chain。

### Invariant I-4：来源优先级按原始集合决定
- Rule：主计划 I-5；不重写 raw repository 方法，不将过滤前置到 direct/bridge 是否为空的判定。OUTBOUND 仍空，旧匹配歧义仍拒绝。
- Applies to：resolveMessageAttachments。
- Violation consequence：直连只含签名时错误回退到另一来源。
- 来源：K-calendar-not-expert-material-owner。

### Invariant I-5：补建可重复且不复活签名
- Rule：dryRun/apply 候选采用同一 SQL 有效谓词；apply 原有 FOR UPDATE 与存在性检查保留。已有签名 document 不删除，未知/普通附件按原规则补建；01 不改 ensureDocumentsForProcessingAttachments，留给 02。
- Applies to：ExpertMaterialService.findReconcileCandidates/reconcileDryRun/reconcileApply。
- Violation consequence：旧签名在补建后重新污染业务读者或 dryRun/apply 不一致。
- 来源：K-mail-attachment-write-paths。

## 现状审计

完整 store/access 回执见主计划 S1–S3；本计划源码路径前缀均 `src/main/kotlin/com/weibo/talentintroduction/`。

### 三表和文件系统（本计划新增持久写入为零）

- Schema：V7 attachment/document；V36 processing owner；V118 nullable size/path；V119 transfer disposition/state；V130 manual owner。文件名/MIME 已足够实现本规则，无需新增有效性字段。document 无 attachment unique，本计划不顺带去重。
- 全部 attachment 写方：MailAttachmentService metadata、legacy record、legacy processing；ManualExpertMaterialUploadService.upload；AttachmentTransferWorker 的 size/path UPDATE。
- 全部 document 写方：MailAttachmentService legacy record、ensureExpertDocument、ensureDocumentsForProcessingAttachments；ManualExpertMaterialUploadService.upload；本文件 reconcileApply。以上供给历史过滤；本计划只改变最后一个写方的**候选**，不更新历史行。（来源：K-mail-attachment-write-paths）
- transfer 写方：登记、桥接、enqueueMaterial/enqueueTransferByIds、worker claim/lease/commit/fail/recover；本计划不改状态写方，只在用户获取前拦截。读方为登记幂等、统一材料服务、transfer service/worker。
- 原件写方：legacy 两入口、manual streamed upload、worker；读方：统一材料服务、旧文档、文本提取、邮箱下载、ZIP。临时 ZIP 是导出产物，签名检查发生在 createArchiveFile 前。

### 本计划修改的读取与调用证据

| 方法/SQL | 基线位置 | 已证明的现状/改法 |
|---|---|---|
| `assemblePage` | `document/service/ExpertMaterialService.kt:304` | 直接 rows.map，未判签名；同处先过滤，防单元直接传 rows 绕过 |
| `summarize` | 同文件 `:550` | total=rows.size；输入必须改为有效 rows |
| `resolveReadyFile` / unscoped | 同文件 `:107/127` | 归属后直接 resolveFileReady；增加类型 guard |
| `requestTransfers` | 同文件 `:595–617` | 仅校验 document 归属；增加全部 attachment 类型校验再查队列/调用 enqueue |
| reconcile 两 SQL | 同文件 `:861/873` | 只有 owner 与 NOT EXISTS；都加入共享谓词 |
| `resolveMessageAttachments` | 同文件 `:707–749` | direct、bridge 均有 early return，不能只在末尾 `.filter` 而漏 return |
| `materialCountByContacts` | `mail/repository/MailboxConversationRepository.kt:515–525` | 直接 document count；改为 JOIN attachment + 同一谓词 |
| `listDocuments` | `document/service/ExpertDocumentBrowseService.kt:46–95` | docs.map 返回所有附件；获取附件后排除签名 |
| `getContactDetail` | `campaign/service/ExpertContactManagementService.kt:69–81` | attachments 来自 mails；documents 独立全量；两者都过滤，不能只用邮件附件 ID 过滤 documents |

无需修改但必须实测的读者：MailboxConversationService:200/247/350；ExpertMaterialArchiveService:29；DocumentTextExtractor:34；MailboxAttachmentService:35/39；ExpertDocumentBrowseController 的 documents/preview/download；它们依赖修改后的服务。旧分析结果文件名回显保留。ExpertMailPermissionGuard:197 仍读取原始 owner；不改底层 repository。

Interaction points：IP-1 历史行→多入口列表，IP-2 历史 ID→操作，IP-3 补建→列表，IP-5 manual-owner→列表，IP-1/IP-2 内部来源选择子场景。逐条映射 A-1 至 A-5。

### 补充离线入口审计

`tmp/lucio-dorneles/register_materials.sh:34/39–51/69–87/96` 在当前 checkout 中直接查重、复制 PDF 并写 manual_upload/attachment/document，提交成功后清暂存、失败清本次原件。文件名与 MIME 固定 PDF，属于一次性离线写方，按 I-1 全部保留；本计划不修改/执行。证据见主计划额外可执行脚本回执。不要把“scripts/ 无命中”扩展成“仓库无离线写入”。

## 实现方案

### T1：纯规则（I-1）

文件：`document/service/ExpertMaterialEligibility.kt`（新）、对应 `ExpertMaterialEligibilityTest.kt`（新）。

固定为无状态 Kotlin object，提供 `isMaterial(fileName: String, contentType: String?): Boolean` 与固定 SQL 常量 `MATERIAL_SQL`。不注入 DB、不查文件、不读字节、不引入新枚举状态。固定错误消息常量供服务使用。

Kotlin：`fileName.trim(' ').lowercase(Locale.ROOT)`；`contentType.orEmpty().substringBefore(';').trim(' ').lowercase(Locale.ROOT)`。返回非 `.p7s` 且 MIME 不在两个签名类型内。

SQL 仅允许固定 alias `a`，字面值按以下合同，禁止用户提供 alias/表达式；BINARY 避免数据库默认不区分重音等 collation 扩大黑名单：

```sql
(
  BINARY LOWER(TRIM(COALESCE(a.file_name, ''))) NOT LIKE '%.p7s'
  AND BINARY LOWER(TRIM(SUBSTRING_INDEX(COALESCE(a.content_type, ''), ';', 1)))
      NOT IN ('application/pkcs7-signature', 'application/x-pkcs7-signature')
)
```

现有源和历史行供给这个判断；不把 mime 声明变成内容鉴定，不处理换行/tab 之外的额外推断。SQL/Kotlin 规范化等价须用真实 SQL 输入矩阵验证。

### T2：统一材料服务（I-1～I-5）

文件：`document/service/ExpertMaterialService.kt`、`document/service/ExpertMaterialServiceTest.kt`。

1. assemblePage：先得 eligibleRows，再现有 resolveItem→filter→sort→page；summary 用全体 eligibleRows 对应 resolved；搜索/来源/状态筛选不能改变 summary 的全体口径。
2. resolveReadyFile：先原始 attachment/document/owner 校验，再 `require(isMaterial(...))`，最后原 ready/path 检查。unscoped：原始 attachment 查找后类型 guard，再 ready。不得让类型 guard 吞掉原有越权异常。
3. requestTransfers：distinct 范围验证后先全批 doc/contact 校验，再全批加载 attachment 并判断有效；任何命中按 I-3 抛错，之前不得调用 countQueued/enqueueMaterial。空、越权、未知 ID 沿用现有拒绝语义。普通批次的 queue-full 429 合同不改。
4. reconcile：两个 SQL 的 WHERE 使用 `MATERIAL_SQL`；不靠写后删除，不把已存在签名 document 判为“不存在”。dryRun 和 apply 用相同条件，FOR UPDATE 保留。
5. resolveMessageAttachments：提取私有 raw resolver 或使用明确局部变量包裹原分支，再统一过滤。不能漏掉现有 `return byDirect`/`return bridged`，不能用过滤后的 empty 决定回退。禁止顺带把 direct/bridge 改为 union；代码当前是优先回退而非注释所说相加。
6. 本文件 `loadMaterialRows` 可保持原 SQL 读全 rows；必须在纯 assemblePage 起点过滤，保证已存在的纯装配测试与生产一致。

以上读取由旧、新 metadata、legacy、manual 写方供给。01 兼容旧写方继续产生签名：业务读取照样隐藏；02 才停止新增。

### T3：其余业务读者（I-1/I-2/I-4）

文件：`mail/repository/MailboxConversationRepository.kt`、`document/service/ExpertDocumentBrowseService.kt`、`campaign/service/ExpertContactManagementService.kt`。

- materialCountByContacts：`expert_document d JOIN mail_attachment a ON a.id=d.mail_attachment_id`；WHERE 保留 contactIds，并添加共享谓词；GROUP BY d.expert_contact_id、COUNT(*) 保持，不引入 DISTINCT 或逐专家 N+1。空 ids 仍直接空 map；0 个材料的专家由调用者既有 `?:0L` 补零。
- 旧文档：现有拿 attachment 与归属验证保留，采用 mapNotNull/预过滤跳过签名；有效项 DTO/排序和 URL 不变。
- 联系人详情：原 mails→attachments 过滤签名；独立加载 documents，并一次 `findAllById` 取得这些 documents 的 attachment 元数据，按同一 predicate 筛 documents。**不能**把 processing/manual 文档与 mails 派生附件列表求交。遇到无 attachment 的损坏关联明确失败，不静默伪造成零材料；正常库有 FK。
- 不改 MailboxConversationService 或 UI；不改底层 repository 查找行为。

### T4：操作穿透与真实 SQL（I-1～I-5）

文件：`document/service/ExpertMaterialArchiveServiceTest.kt`、新 `document/service/ExpertMaterialEligibilityReadIT.kt`。

- ZIP 混选历史签名：使用真实 ExpertMaterialService（mock repo 可），确保有效文件和签名都在本地，传入顺序正反各一次；断言 IllegalArgumentException→HTTP 400 合同、consume 不调用、createArchiveFile/读取输入文件均不发生，现有磁盘内容不变。
- 新 IT 使用已有 Testcontainers MySQL（不新增依赖），每个测试类自管临时库；采用与实际迁移同构字段/FK，优先 Flyway 迁移实际 schema。测试源明确记录数据库版本，至少 MySQL 5.7.44 与 8.0.36 的同组查询均执行；容器不可用则记录阻塞，不能假定测试通过。
- 使用真实 NamedParameterJdbcTemplate 调用 ExpertMaterialService.listMaterials/reconcile 与 MailboxConversationRepository.materialCountByContacts；不能手抄一条“等价 SQL”冒充生产查询测试。
- 同一测试文件补旧 Browse 与 ContactManagement 的服务读取场景，可 mock 无关依赖，数据读取仓储使用同一 fixture；确认三 owner 的 documents 均保留。这些回归属于新增测试文件内部，不为凑数修改其他测试文件。
- 测试 fixture 所有记录为合成资料；严禁默认连接 application.yml 数据源或在真实库 cleanup。

## 变更文件清单

| # | 文件 | 操作 | 用途 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialEligibility.kt` | 新增 | 纯分类规则与固定 SQL 谓词 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt` | 修改 | 列表/统计/操作校验/补建/消息附件 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` | 修改 | 会话材料数量 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt` | 修改 | 旧文档列表过滤 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt` | 修改 | 专家详情两类数组过滤 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialEligibilityTest.kt` | 新增 | 分类边界测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialServiceTest.kt` | 修改 | 历史读取、操作及补建回归 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialArchiveServiceTest.kt` | 修改 | 历史签名 ID 混选拒绝 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialEligibilityReadIT.kt` | 新增 | 真实 MySQL SQL/服务读取与旧读者集成 |


合计 9；不修改通用 repository。文件路径未列出则不属于本子计划修改范围。新增 IT 内的纯服务回归可以使用单独测试类，运行命令必须列全类名。

## 验收标准

### I-1 输入矩阵（Kotlin 与实际生产 SQL 均验证）

| 文件名 | MIME | 材料 |
|---|---|---|
| `smime.p7s` | null | false |
| ` SMIME.P7S ` | application/octet-stream | false |
| `cv.p7s` | application/pdf | false（扩展名规则优先，属产品明确选择） |
| `signature.bin` | ` Application/PKCS7-Signature ; name=foo ` | false |
| `signature.bin` | application/x-pkcs7-signature | false |
| `CV.docx` | application/vnd.openxmlformats-officedocument.wordprocessingml.document | true |
| `certificate.png` | image/png | true |
| `image001.jpg` | image/jpeg | true |
| `smime.p7m` | application/pkcs7-mime | true |
| `smime.p7s.pdf` | application/pdf | true |
| `file` | null/空串/未知值 | true |
| `cv.pdf` | `application/pkcs7-signaturé` | true（不因 collation 折叠误排） |

- I-2：5→2；只有签名→items=[]、total=0、summary 各计数=0；普通 0 字节仍为 1。混合 25 行（12 签名、13 普通）size=10 时第 1 页 10、第 2 页 3、total=13，summary 不随 q/state 改。三 owner + transfer 六状态 + legacy 无 transfer 均覆盖，审核状态不变。
- I-3：混选 ID 的 transfer 请求零 enqueue；签名本地存在/不存在都 400；普通缺文件仍 409；越权仍现有错误；ZIP/提取不读任何内容，HTTP 仍 JSON 错误而非半截二进制。
- I-4：direct 仅签名+bridge 普通→空；direct 普通+bridge 任意→direct 普通；bridge 签名+legacy 普通→空；无 direct/bridge 时 legacy 恰好唯一按原规则返回有效项；歧义与 OUTBOUND 空。
- I-5：dryRun 仅返回普通候选；apply 第一次 createdCount=1，第二次=0；历史已存在签名 document 不删不补；GET 不调用 reconcile。
- 机器检查：无产品 DELETE、无新增 schema 字段、无 Files.delete 对原件、无 DB/ES 状态更新；原测试继续运行。

执行命令（未来执行，当前未运行）：

```bash
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ExpertMaterialEligibilityTest,ExpertMaterialServiceTest,ExpertMaterialArchiveServiceTest,ExpertMaterialControllerHttpTest -DskipNodeTests=true
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ExpertMaterialEligibilityReadIT -DmysqlIt=true -DskipNodeTests=true
```

若 IT 文件采用额外纯测试类，追加该类名到第一条；不得依靠未被 surefire 发现的类。01 完成后即可验证历史用户可见效果，但尚不能宣称停止所有新增签名落库。

## 人工验收清单

### A-1：历史 5→2 与分页
- 前置条件：隔离测试环境旧版本中，用测试邮箱三封来信登记三个 `.p7s`；一封带真实 DOCX，获取该 DOCX；手动上传真实 PDF。记录 contactId、五个 attachmentId。材料页显示 5、stored=2、metadataOnly=3。
- 操作步骤：1. 部署 01；2. 关闭重开材料弹窗；3. 刷新会话；4. 搜索 smime.p7s，再清空搜索；5. GET `/api/expert-contacts/{contactId}/materials?page=0&size=1`，再 page=1。
- 预期结果：材料总数 2、stored=2、metadataOnly=0；搜索返回 total=0，但 summary.total=2；两页各 1 项，仅 DOCX/PDF；会话材料按钮为 2。无需执行历史清理。
- 覆盖：O1、I-1/I-2、IP-1。

### A-2：普通图片、manual/processing 及旧入口
- 前置条件：旧版本中为测试专家手动上传 certificate.png 和 0 字节 notes.txt；另一封待匹配来信包含 scan.jpg，绑定该专家；另有正常 CV.docx 与 smime.p7s。记录四份正常材料的 ID，确认各自来源。
- 操作步骤：1. 部署 01；2. 打开材料页；3. GET `/api/expert-contacts/{contactId}/documents` 和 `/api/expert-contacts/{contactId}`；4. 下载已存图片和 0 字节文件。
- 预期结果：三个接口的材料/文档集合保留四个正常 ID，排除 p7s；详情 attachments 保持原来 record-owner 范围并排除签名，不额外混入 processing/manual；0 字节下载长度=0，图片哈希不变。
- 覆盖：O1、必须保持普通材料、I-1/I-2、IP-1/IP-5。

### A-3：历史选择绕过
- 前置条件：A-1 历史签名 ID 仍存在；为其中一个签名在旧版本先完成获取，另保留 metadata-only；记录全部原始状态。测试专家可由当前登录用户访问。
- 操作步骤：1. POST `/materials/transfers`，body 为 `{"attachmentIds":[普通ID,签名ID]}`；2. 同集合 POST `/materials/download-zip`；3. 请求 scoped `/attachments/{签名ID}/download` 和 `/preview`；4. 请求 `/api/mail/mailbox/attachments/{签名ID}/download`；5. 在测试 API 客户端POST `/api/expert-contacts/{contactId}/ai-analysis`，body 为同一 attachmentIds 集合。
- 预期结果：均 400/BAD_REQUEST，提示“所选文件包含邮件数字签名，不属于专家材料”；没有 ZIP/图片/PDF/AI 结果输出；普通 ID transfer 状态不因失败请求改变。正常未就绪 PDF 单独下载仍 409。
- 覆盖：O2、保持权限和原件、I-3、IP-2。

### A-4：来源回退和只读
- 前置条件：仅在隔离测试库：旧版本检查一封未匹配的仅签名信，记录 processing ID=P（其签名直接 owner=P）；另检查已匹配的 PDF 信，记录该 PDF 的 transfer ID=T。用下方“人工 fixture SQL”把该 transfer 桥接到P，并记录更新前值。另向两个测试收信账号投递相同 Message-ID 的测试信以构造 legacy 歧义。
- 操作步骤：1. GET `/api/mail/mailbox/INBOUND_PROCESSING/{id}/attachments`；2. 打开消息列表；3. 对歧义 legacy processing 重复同操作；4. 在数据库审计日志中检查这次 GET 区间，比较原件哈希。
- 预期结果：第一个 processing 附件列表=[]、attachmentCount=0，不出现 bridge 普通附件；歧义来源仍为空；没有业务 DML、IMAP FETCH，原件哈希不变。
- 覆盖：必须保持来源/只读、I-2/I-4、IP-1/IP-2。

### A-5：显式历史补建
- 前置条件：旧版检查未匹配来信（PDF+p7s），记录 processing ID=P、两附件 ID、可访问测试专家 ID=C；尚未绑定所以无 document。仅在隔离测试库执行下方“人工 fixture SQL”设置 processing 的 expert_contact_id=C，不调用后台绑定，以保留缺文档状态。
- 操作步骤：1. POST `/api/expert-contacts/{contactId}/materials/reconcile`，`{"dryRun":true}`；2. `{"dryRun":false}` 执行两次；3. 查看材料页和原始 attachment 行。
- 预期结果：dryRun 仅有 PDF ID；第一次 createdCount=1，第二次=0；签名原始 attachment 仍在但未建 document，材料列表只有 PDF；不改变 owner、审核状态或其他联系人。
- 覆盖：O2、I-5、IP-3。

### 人工 fixture SQL（仅隔离测试数据，非发布脚本）

先通过上述旧版 UI/测试邮箱生成记录，在数据库查询界面将尖括号替换为已记录的正整数；先 SELECT 确认只命中自己的测试记录。执行前后保存查询结果，验收结束恢复原值或删除整个隔离测试库，不在生产运行。

```sql
-- A-4：原来的 direct-owner 签名集合必须真实存在。
SELECT id, file_name FROM mail_attachment WHERE inbound_processing_id = <P>;
SELECT id, attachment_id, inbound_processing_id FROM mail_attachment_transfer WHERE id = <T>;
UPDATE mail_attachment_transfer SET inbound_processing_id = <P> WHERE id = <T>;
-- A-5：旧版未匹配信尚无 document；只关联 contact，故意不走绑定补建。
SELECT a.id, a.file_name, d.id AS document_id
FROM mail_attachment a LEFT JOIN expert_document d ON d.mail_attachment_id=a.id
WHERE a.inbound_processing_id = <P>;
UPDATE inbound_mail_processing SET expert_contact_id = <C> WHERE id = <P>;
```

A-4 的歧义数据若受入站 UID/Message-ID 去重阻止，应使用该测试库中的真实 mail_record 合成副本，并在测试工具导入前检查相同 contact/account 的匹配条件；不能声称两封实际入站信必然创建两条 record。该歧义回归以机器测试为主，人工 A-4 必做 direct/bridge 场景。
