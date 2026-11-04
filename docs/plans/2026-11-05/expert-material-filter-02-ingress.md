# 02：新增材料入口、桥接与补建过滤

状态：DRAFT。依赖：[01](expert-material-filter-01-read.md) 的 `ExpertMaterialEligibility`；9 文件（生产 4、测试 5），无 schema/frontend 变更。先完成 01 验证再实施本计划。

## 需求描述

O1：新收信和直接 processSingle 都不把签名登记为专家材料，不因仅有签名而推断材料意图或推进 MATERIALS_RECEIVED。O2：绑定/补建与手动上传堵住新增旁路；签名过滤不破坏整封信确认和正常材料获取。

保持：普通附件/命名图片/0 字节手动文件、正常签名邮件的正文与真实附件可用；UID/事务/来源/partPath/游标保持；DMARC/退信/自检及 Drive 外链按现有路由；历史行/文件/人工状态不改。

范围外：图片内容识别、MIME 加密解包、签名验真、历史状态回退、队列清理、原件删除、旧查询投影（03）。

## 关键不变量

### Invariant I-1：同一规则、读流前排除
- Rule：只调用 01 的纯规则，不复制新一套扩展名逻辑。MIME 被确认为签名叶后直接空 NodeResult；metadata/legacy 均不打开签名 inputStream/getContent。multipart/signed 容器照常递归，保留正文与其他子节点。
- Applies to：ImapMailReceiveService.walk；MailAttachmentService 各写入口；手动上传。
- Violation consequence：签名进入正文、落盘或阻塞收信；把 signed 容器整体丢掉导致简历丢失。
- 来源：original、K-attachment-metadata-consumer-chain。

### Invariant I-2：公共业务集合唯一
- Rule：`processSingle` 合并 MIME + linkedMaterials 后，在首次业务事务/分类/状态变更之前过滤；传给后续全部路径的是同一个 filtered businessMail。保留原始集合顺序、source、UID、partPath，linkedMaterials 合并后清空。
- Applies to：已匹配、未匹配、无首信、来源存疑、全局关闭、专家关闭、人工、重复正文、正常收信、直接 processSingle 与 UID 补收。
- Violation consequence：某分支仍用 received.attachments.size 把仅签名邮件当材料，或桥接集合不一致。
- 来源：K-process-single-all-callers、K-linked-materials-after-machine-mail-routing。

### Invariant I-3：登记、补建、桥接三者一致
- Rule：两个 save 入口先 filter 再触达文件/仓储；inferPrimaryIntent 对有效集合为空返回 null；ensureDocumentsForProcessingAttachments 跳过签名；bridge 跳过签名，但有效 metadata 附件缺 transfer 仍抛异常。重复来源有效件沿原唯一源身份复用，不改 name/path 不新增重复行。
- Applies to：saveInboundAttachments/saveUnmatchedAttachments/ensureDocumentsForProcessingAttachments/bridgeInboundProcessing/inferPrimaryIntentFromAttachments；其 private save/ensure 调用链。
- Violation consequence：跳过登记却仍桥接导致回滚，或直接调用保存/绑定绕过入口。
- 来源：K-mail-attachment-write-paths。

### Invariant I-4：手动上传拒绝在落盘前
- Rule：normalize filename/MIME 后立即判定；签名抛 `OutboundAttachmentException.badRequest("邮件数字签名不属于专家材料")`，HTTP 400/BAD_REQUEST；检查必须位于 manualRoot/createDirectories/writeStreamedFile/transaction 之前。普通文件仍完整保留原 owner/大小/原子转正/失败清理语义。
- Applies to：ManualExpertMaterialUploadService.upload。
- Violation consequence：返回拒绝但留下文件、source 或孤儿文档。
- 来源：K-mail-attachment-write-paths。

### Invariant I-5：过滤不修改通信状态机
- Rule：新“仅签名”不产生附件证据，但正文分类仍正常；不能强制该邮件都进人工，也不能强制 UNKNOWN。正常 CV 继续既有 promotion；全局/专家禁用、recoveryOnly、自检/退信/DMARC、事务失败和重复 UID 既有行为不变；不自动修旧状态。
- Applies to：processSingle 和其调用链；MIME/body 遍历。
- Violation consequence：为了去掉附件却改变自动发信、游标或历史权威状态。
- 来源：K-process-single-all-callers、K-operator-status-single-writer。

## 现状审计

源码路径前缀：`src/main/kotlin/com/weibo/talentintroduction/`。完整 grep、schema 和所有读取闭包见[主计划 S1–S4](expert-material-filter-master.md)。

### 写入存储及全部写方

- mail_attachment：V7/V36/V118/V130；file_name/content_type 有现成字段，三 owner XOR，size/path 可空。写方是 MailAttachmentService.registerMetadataAttachment:236、saveLegacyRecordAttachment:311、saveLegacyProcessingAttachment:345、ManualExpertMaterialUploadService:112、worker UPDATE:498。02 只前置过滤四个 INSERT 链，不动 worker。
- expert_document：V7:14；写方 MailAttachmentService.ensureExpertDocument:283、legacyRecord:322、ensureDocumentsForProcessingAttachments:133、ManualExpertMaterialUploadService:126、ExpertMaterialService.reconcileApply:663。最后一个在 01 已有有效候选保护，本计划补前三链与 manual。
- transfer：V119；MailAttachmentService:248 登记、:184 桥接；AttachmentTransferService:123 登记、:322/:376 入队；repository 六类状态 SQL 和 worker 负责后续。02 不加 state/field，不影响 DMARC purpose。
- manual_expert_material_upload：V130:26；唯一写方 ManualExpertMaterialUploadService.upload:103，与 attachment/document 同事务；只读方 ManualExpertMaterialUploadRepository（owner）、ExpertMaterialService MATERIAL_LIST_SQL（上传者/时间）。拒绝签名时三表都零 INSERT。
- 文件：legacy 两入口 Files.write；manual 临时文件→final→失败清理；worker `.part`→final；ZIP 临时产物。02 在写前排除，旧原件不清理。

### 读取、业务分支与交互证据

| 源码 | 已证明行为 | 本计划动作 |
|---|---|---|
| ImapMailReceiveService:530–550 | 遍历 multipart 子节点累积附件 | 保留 signed 容器与原索引 |
| 同文件 :554/594–606 | 文件名优先把叶判成附件 | 在登记前判签名 |
| 同文件 :608–644 | legacy 读 inputStream，metadata 返回 content=null | 排除检查在读流之前 |
| MailReceiveService:84–102 | source 没 CID，只有 disposition/定位字段 | 不新增 DTO，不臆测图像用途 |
| AutoMailReplyService:121–129 | 外链合并到 businessMail | 合并后统一过滤 |
| 同文件 :380/:438 与 :550 | 两关闭分支用 received.size，正常分支用 savedDocuments.size | 入口 filtered 集合覆盖两类计数 |
| 同文件 :1371–1379 | UNKNOWN + 附件 → inferPrimaryIntent | 服务再过滤，纯签名返回 null |
| 同文件 :1495/:1552 | confirm 调 bridgeInboundProcessing | 签名不查缺失 transfer，正常缺失仍失败 |
| MailAttachmentService:124–143 | 绑定时补建每个 processing 附件 | 跳过签名 |
| ManualExpertMaterialUploadService:89–98 | normalize 后直接落盘 | normalize 后立即拒绝 |
| AutomaticApplicationPromotionService:40–57 | savedDocumentCount>0 推进材料状态 | 不改此服务；有效集合/保存计数改正确 |

新数据读者：01 中列表、旧文档、详情、count、文件解析和 ZIP；03 的旧投影/对账；transfer worker 只消费有效 MATERIAL 行；DMARC 继续独立 purpose。底层仓储原始事实读取不变。

Interaction points：IP-3（绑定→文档）、IP-4（MIME→业务状态/桥接）、IP-5（手动上传→文件/三表）、IP-8（机器邮件/Drive/UID→事务），分别由 A-1～A-7 验证。

### 补充离线入口审计

`tmp/lucio-dorneles/register_materials.sh:34/39–51/69–87/96` 在当前 checkout 中直接查重、复制 PDF 并写 manual_upload/attachment/document，提交成功后清暂存、失败清本次原件。文件名与 MIME 固定 PDF，属于一次性离线写方，按 I-1 全部保留；本计划不修改/执行。证据见主计划额外可执行脚本回执。不要把“scripts/ 无命中”扩展成“仓库无离线写入”。

## 实现方案

### T1：MIME 叶排除（I-1/I-5）

文件：`mail/service/ImapMailReceiveService.kt`、`mail/service/ImapMailReceiveServiceTest.kt`、`mail/service/ImapMetadataFetchIT.kt`。

- 在 walk 完成 contentType 解析、multipart 分支之后，且 isAttachmentLeaf/registerAttachment/text fallback 之前判规则；使用与 registerAttachment 一致的 MimeUtility.decodeText 文件名解码，解码失败保留原值。MIME 缺失时现有失败/忽略行为不改。
- 绝不能只给 isAttachmentLeaf 返回 false 后放任 text 类型的 `.p7s` 进入正文：命中签名应直接 `NodeResult("", false, emptyList())`。
- 不改变点分 partPath；例如 `[正文=1, 签名=2, CV=3]`，输出 CV 的 partPath 仍是 `3`，不能重编号为 2。
- 单测覆盖 named/no-name MIME signature、octet-stream+.p7s、伪装 text/plain+.p7s、multipart/signed 包含真正正文+CV、legacy/metadata 两模式。签名流实现为抛错 fixture，证明根本未打开。
- 本地 socket IMAP IT 添加 `.p7s`+PDF 混合元数据场景；沿用协议日志断言零签名字节 FETCH 与零普通附件 bytes FETCH，保留 BODYSTRUCTURE/头与正文读取。`ImapMetadataFetchIT` 需要显式 mysqlIt=true，实际不访问 MySQL。

### T2：公共业务入口（I-2/I-5）

文件：`mail/service/AutoMailReplyService.kt`、`mail/service/AutoMailReplyServiceTest.kt`。

- 仅重写 businessMail 组装：MIME + linkedMaterials 保留顺序，按 isMaterial 过滤，然后保持 recoveryOnly/source 逻辑。下游已统一收到 businessMail，禁止逐分支分别打补丁。
- 业务 public processSingle 可被直接调用，因此即使 MIME 已过滤也必须再判。bulk 的 self-check/bounce/DMARC 在 processSingle 前执行，不将 linkedMaterials 提前混入原始 MIME 列表。
- 测试明确覆盖：正常已匹配、全局关闭、专家关闭、MANUAL_HANDOFF、未匹配、无首信、来源存疑/人工处理、重复正文各已存在 fixture；在每条分支传入 raw signature，断言保存/桥接得到空有效集合。有关 promotion 的三条分支断言 count=0、不调用材料推进；正文分类为固定 UNKNOWN 的 fixture 不应产生 DOCS_ATTACHED。
- 混合 CV+签名 count=1，顺序/source 不变；repeat UID 沿原去重返回，不新增收信记录和 SMTP；`recoveryOnly` 原开关不改。
- 禁止从“过滤后没材料”推出“必须发信/必须不发信”，继续由原正文/权限/自动回复设置决定。

### T3：保存服务防漏（I-2/I-3）

文件：`mail/service/MailAttachmentService.kt`、`mail/service/MailAttachmentServiceTest.kt`。

- saveInboundAttachments/saveUnmatchedAttachments：先筛 eligible，再判断 empty、取 now、map。纯签名连没有 source 的 metadata 参数也直接忽略，不为被排除项验证 source 或创建目录。
- inferPrimaryIntentFromAttachments：仅有效集合；空→null，CV→CV_ATTACHED，其余正常→DOCS_ATTACHED。
- bridgeInboundProcessing：在 source/content 分流前跳过签名；不要把所有缺 transfer 改成 continue。用“有效 metadata 缺 transfer”反例保留抛错。
- ensureDocumentsForProcessingAttachments：按现有 metadataOnly 开关、owner 不变；在 findFirst/save 前过滤签名；重复绑定幂等。private ensureExpertDocument 的所有调用仍由过滤后集合供给，重入查原始 existing attachment/document 的语义不变。
- 测试 metadata/legacy × record/processing × contact已知/未知；排除项保存数/transfer数/document数/文件数均 0。混合正常项为 1；同 source 重试不重复；已有签名 document 不删除。

### T4：手动入口（I-1/I-4/I-5）

文件：`document/service/ManualExpertMaterialUploadService.kt`、`document/controller/ManualExpertMaterialUploadFlowTest.kt`。

- normalize 后按统一规则拒绝，在任何文件操作与事务之前。HTTP status/code 复用现有 OutboundAttachmentException/GlobalExceptionHandler，不新增 ControllerAdvice。
- API 测试以 `.p7s`+octet-stream 和 `.bin`+signature MIME 各发一次，断言 400、message 固定、无 manual/attachment/document/transfer 行、basePath 下无新增文件；签名 stream 在服务层可用抛错 fixture证明未读。
- 正常 certificate.png、scan.jpg、0B notes.txt 上传 201、STORED、PENDING_REVIEW，文件哈希/实际大小不变。上传不会自动推进 operator_status（现有服务注释 :59），该行为保持。

## 变更文件清单

| # | 文件 | 操作 | 用途 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt` | 修改 | MIME 叶节点提前排除 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改 | 公共业务入口过滤附件集合 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt` | 修改 | 两类登记/绑定补建/桥接/意图防漏 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ManualExpertMaterialUploadService.kt` | 修改 | 手动上传提前拒绝签名 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt` | 修改 | 两模式 MIME 回归 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMetadataFetchIT.kt` | 修改 | 真实 JavaMail 协议零附件字节读取 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 修改 | 多业务分支不误推进 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt` | 修改 | 登记、桥接及补建幂等 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/document/controller/ManualExpertMaterialUploadFlowTest.kt` | 修改 | 上传拒绝及正常图片回归 |


9 文件，测试与实现逐一绑定；不修改 `MailReceiveService.kt` DTO 或 `AutomaticApplicationPromotionService.kt`。

## 验收标准

- I-1：所有签名类型两模式输出附件空且正文不含签名字节；signed multipart 的正文与 CV 都保留；正常命名/无 disposition 图片附件不误排；已有 unnamed inline 非文本排除测试仍通过。
- I-2：businessMail 的实际保存、count、effectiveIntent 与 bridge 捕获值一致；三个 promotion 计数分支都覆盖；批量与直接 processSingle 都通过。
- I-3：保存/绑定/桥接六个公开入口有反例；正常 metadata 缺 transfer 仍抛错；过滤签名不造成 markSeen/游标失败；同源重入幂等。
- I-4：签名上传 400、零文件零三表行；0B 和合法图片回归 201。超过原大小限制仍按既有 413，不修改阈值。
- I-5：DMARC 报表附件保留；Drive 在既有阶段合并；repeat UID 不产生重复写或 SMTP；分类只移除附件推断，正文意图与 recoveryOnly 保持。
- 与 01 集成：旧签名被隐藏，新签名无新增行；签名+CV 最终材料页 total=1；已存在历史 status 不降级。

执行命令（未来执行，当前未运行）：

```bash
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ImapMailReceiveServiceTest,AutoMailReplyServiceTest,MailAttachmentServiceTest,ManualExpertMaterialUploadFlowTest,BatchAutoMailReplyServiceTest -DskipNodeTests=true
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ImapMetadataFetchIT -DmysqlIt=true -DskipNodeTests=true
```

需审查 surefire 确认第二条不是 skipped；若本机不能开 socket，标记未通过，不更改测试使其假绿。

## 人工验收清单

### A-1：仅签名与签名+简历
- 前置条件：隔离测试邮箱、已发首信的测试专家、operatorStatus=REPLIED；全局自动发送关闭。准备 smime.p7s（可使用合成字节，因为本规则只判 metadata）和正常 CV.pdf。
- 操作步骤：1. 发正文 fixture-neutral-01 + p7s 并检查回复；2. 查看材料页和运营状态；3. 发同类正文但不同 Message-ID 的 p7s+CV.pdf，再检查回复；4. 获取 CV 并下载。
- 预期结果：第一封材料0、不推进 MATERIALS_RECEIVED；第二封材料1，既有材料推进保留，下载文件哈希与输入一致；邮件正文可读，无二进制签名文本。
- 覆盖：O1、正常材料回归、I-1/I-2/I-5、IP-4。

### A-2：关闭与人工分支
- 前置条件：三个独立测试专家均已发首信、状态 REPLIED；分别全局关闭、专家关闭、会话 MANUAL_HANDOFF。使用不同 UID/Message-ID 的签名邮件。
- 操作步骤：1. 每位专家收到仅签名邮件；2. 检查回复；3. 查看材料与状态；4. 用正常图片+签名复测。
- 预期结果：仅签名均为0材料，运营状态不因附件推进；图片+签名各1份；关闭/人工设置不被开启，不产生自动发送。
- 覆盖：O1、通信策略保持、I-2/I-5、IP-4。

### A-3：事务桥接与重复收取
- 前置条件：测试邮箱收到 metadata-only 模式的签名+PDF；另使用测试 fixture 注入“有效 PDF 已处理但 transfer 登记缺失”的失败场景。所有数据隔离。
- 操作步骤：1. 检查混合信两次；2. 看邮件确认和材料数；3. 用失败 fixture 运行同一检查流程；4. 修复 fixture 后重试。
- 预期结果：混合信只登记/桥接PDF1份，不因签名缺 transfer 失败，第二次无重复；故意缺 PDF transfer 时操作失败且不确认该信、不推进其成功游标，重试成功后仍1份。
- 覆盖：O2、事务/UID保持、I-2/I-3/I-5、IP-4/IP-8。

### A-4：待匹配与无首信
- 前置条件：测试邮件来自未匹配邮箱和已知但未发首信的专家，各附签名+普通PDF；另保留旧版本写入的 processing-owner p7s+PDF，尚无 document。
- 操作步骤：1. 新邮件检查回复；2. 在后台绑定未匹配信到测试专家；3. 对旧邮件执行绑定/材料补建两次；4. 查看资料。
- 预期结果：新邮件各只登记PDF；旧 p7s 原行保留但不补 document；每组普通PDF最多1份；来源归属不移动、审核PENDING_REVIEW不因过滤被批准。
- 覆盖：O2、I-3、IP-3/IP-4。

### A-5：手动上传
- 前置条件：测试专家可访问；记录上传前材料数量与服务器测试目录文件清单；准备 p7s、MIME 为 signature 的 bin、certificate.png、0B notes.txt。
- 操作步骤：1. UI 上传 p7s；2. API multipart 上传 bin 并指定 signature MIME；3. 上传图片和空文本；4. 下载这两份正常文件。
- 预期结果：前两次400/BAD_REQUEST、“邮件数字签名不属于专家材料”，无新增数据库行和原件；后两次201/STORED/PENDING_REVIEW，材料增加2、图片哈希一致、文本0字节；运营状态不被上传修改。
- 覆盖：O2、正常图片/0B保持、I-4/I-5、IP-5。

### A-6：DMARC 与 Drive
- 前置条件：隔离测试环境已有可工作的 DMARC 报表 fixture 和受控 Drive 测试文件链接；账号网络设置按原测试要求准备，不使用真实专家数据。
- 操作步骤：1. 输入报表邮件（合法报表附件+签名）；2. 检查回复；3. 输入普通专家信（签名+受控 Drive 文件链接）；4. 查看材料来源并获取文件。
- 预期结果：DMARC 仍走报表处理，purpose=DMARC、attachment_id为空，不产生专家材料；Drive 信只登记1个外链材料，partPath 为原 gdrive: 前缀；没有把链接提前交给 DMARC 解析。
- 覆盖：N-3、I-2/I-5、IP-8。

### A-7：来源定位与正文
- 前置条件：用本地测试邮箱导入合成 MIME：multipart/signed 内包含正文和CV，签名为独立叶；另一个 mixed 邮件原part顺序为正文1、签名2、PDF3。测试 UID 与账号已记录。
- 操作步骤：1. metadataOnly 模式检查回复；2. 获取PDF3；3. 使用隔离的 legacy 模式重复同结构新邮件；4. 对照收件箱原始正文和附件。
- 预期结果：两模式正文保留、签名不出现在正文/材料；PDF仍按partPath=3读取正确内容；签名流不读取的证据来自对应协议IT；UID/账号/来源未变。
- 覆盖：正文/来源保持、I-1/I-2/I-5、IP-4/IP-8。
