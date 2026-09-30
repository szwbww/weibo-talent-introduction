# PDF 作者标记与邮箱完整性修复计划

状态：待执行；本次只完成计划、代码审计、原文证据归档。用户范围：代码和测试；不处理存量。

基线：`a37efe970e4446242b121c5628db02daa631fc92`，2026-09-30。执行前重新核对下列文件及常量，工作区已有其他未提交修改，禁止覆盖。技术栈沿用 Kotlin 1.9.25、Java 11、PDFBox 2.0.31、JUnit 5，无新依赖。

## 需求描述

### 可观察结果

- O-1：相同原始 PDF 和作者元数据再次解析时，`ysjang@ucla.edu` 不再绑定 Chee Wei Wong，`alessandro.siria@ens.fr` 不再绑定 Lydéric Bocquet。来源不能唯一确定归属时，保留无身份邮箱线索。
- O-2：`∗ lun yue@msn.com` 不再生成 `yue@msn.com`；PDF 中现有 `mailto:lun_yue@msn.com` 仍保留为无身份线索。本计划不承诺自动恢复其作者绑定。
- O-3：修复前缓存不能绕过新规则产生错误专家；继续使用现有版本拒绝语义。

### 必须保持

- N-1：完整、唯一的作者标记仍能绑定邮箱；明确的一人多邮箱继续成立，共享/未知/冲突身份不猜测。
- N-2：有明确来源证据时，姓名、ORCID、OpenAlex 作者 ID、机构随同一个作者传递；无归属线索保持这些字段为空。
- N-3：完整邮箱、花括号邮箱、既有 at/dot 混淆还原、`user@\nhost.edu` 紧邻换行、运营邮箱过滤保留；不做广义空白拼邮箱。
- N-4：PDF 头两页加尾页、mailto 单邮箱线索、HTML/XML 正常明确联系人保留。文本星号脚注的残缺邮箱过滤适用于共用文本提取器，其他格式不新增归属规则。
- N-5：`DiscoveryIdentity.VERSION=20260925`、专家 ID、现有新专家消费/去重/写入/晋升规则不变；只更新抽取缓存兼容版本。

### 不在范围

存量专家/候选/联系人/邮件修复；线上操作与部署；SQL 清理或重抽取任务；新数据库字段/ES mapping；新外联门禁；作者姓名模糊匹配；邮箱拼写猜作者；按名单屏蔽这三条；OCR、PDF 字体修复、PDFBox 升级；PDF 链接坐标归属系统；跨来源补证与召回优化。

## 关键不变量

### Invariant I-1：作者的连续标记组必须整体计数

- Rule：在已确定的作者署名区，数字/逗号/空白连接的同一署名后标记组，如 `1,†,*`，其中每个受支持符号均登记该署名的拥有者。`∗` 与 `*` 归一；未知署名登记 null；不得因第二个符号前不是姓名而跳过该符号。跨入下一个姓名即结束此组。
- Applies to：`PdfAuthorContactLayout.markerOwners` → `collect` 星号脚注 Contact 生成。
- Violation consequence：共享标记被误判独占，一人的邮箱及学术身份串到另一人。
- 来源：K-author-identity-needs-email-evidence；本次 Chee 原文 trace。

### Invariant I-2：损坏的作者区不能提供独占标记证明

- Rule：仅在 `authorArea` 返回的署名区检测已证实的异常符号 `?`，并同等处理 Unicode 替换字符 U+FFFD。出现任一符号时，该页的“标记 → 作者”归属不可用。不得将其猜测替换成 `*`。不阻断独立的明确姓名联系记录、其他页、其他论文。
- Applies to：`collect` 的 markerOwners/uniqueOwner 分支；`endContactBlocks`、独立姓名联系段落、resolver 的 textClaims 不增加此门禁。
- Violation consequence：PDF 丢失一个共享星号时，把剩余识别者误当唯一拥有者。
- 来源：original；Lydéric 原文中 Siria 的视觉星号实际解码为 `?`。
- 边界：不是“发现任意问号就丢整篇”；只检查现有署名区域。对其他不可见/未知编码损坏，不声称本修复已经覆盖。

### Invariant I-3：裸标记脚注的残缺邮箱不能成为有效邮箱结果

- Rule：保留既有 Email 标签截断保护，新增对裸联系标记开头片段的保护：邮箱匹配前若为行首空白 + 一个支持标记 `*∗†‡§` + 空白 + 一个 local-part 字符词片段 + 水平空白，则该邮箱匹配是残缺候选，丢弃。直接完整的 `* x@host.edu` 无该前缀，不丢弃。已有 Email 标签句式仍走既有规则；不把该规则推广成“邮箱前有单词就拒绝”。
- Applies to：`PlainTextEmailExtractor.extract` 的统一匹配过滤；`PdfAuthorContactLayout` 标记片段抽取必须保留/补回该片段自己的标记上下文，使其与整页文本提取结果一致。
- Violation consequence：整页结果删了错误邮箱，Contact 分支仍认证它，或反之留下可再次利用的残缺线索。
- 来源：original；Lun 原文 `∗ lun yue@msn.com` 与代码 `splitLocalPartBeforeMailbox`。
- 本次明确范围：横向空白造成的裸标记 local-part 断裂。既有 Email 标签/换行测试必须保留；不新建任意词数、任意段落的拼接/猜测算法。

### Invariant I-4：独立证明与线索语义保持

- Rule：拒绝不完整标记证明不等于删除完整邮箱线索。Chee 两个完整邮箱、Lydéric 两个完整邮箱均保留，姓名和学术身份为空；Lun 的错误后缀缺席，正确 mailto 保留且身份为空。合法一人多邮箱仍可各有证据。
- Applies to：`collect` → `resolvePdf` → `AuthorEmail` → `consumeOutcomeInternal`；本计划不改 resolver/consumer 生产代码。
- Violation consequence：把修复做成全来源拒绝，或把 mailto 当成作者归属证据。
- 来源：K-source-recall-needs-original-layout、K-author-identity-needs-email-evidence、K-identity-cache-version-and-admission。

### Invariant I-5：缓存规则版本与历史身份证明版本分离

- Rule：`EXTRACTION_VERSION` 从 `20261003` 更新为 `20261004`；它是现有递增兼容号，不是本次日期。`VERSION` 保持 `20260925`。旧值/null/其他值按现有消费者返回 `IDENTITY_EXTRACTION_VERSION_UNSUPPORTED`，队列保持 FAILED、不下载、不写专家；不增加自动重抽取。新抽取结果按现有 producer 自动带新版本。
- Applies to：`extractQueuedItem` 的序列化、`consumeQueuedItem` 的比较、`DiscoveryPipelineService` 缓存消费。
- Violation consequence：保存过的错误抽取结果跳过解析继续入库；或误使历史专家证明失效。
- 来源：K-identity-cache-version-and-admission；按当前代码纠正旧知识里的“重抽取”表述。
- 已知影响：该常量共用于 PAPER/RECORD；升级后所有来源的旧缓存都按现有规则拒绝，不只是 PDF。本计划不新增每来源版本字段来缩小这个既有机制的影响。

### Invariant I-6：证据必须来自同一原文，测试不得靠旧构建假绿

- Rule：三篇 PDF 与当次 OpenAlex JSON 使用归档原字节及 SHA256；作者映射走 `openAlexPaperAuthors`，PDF 走 `extractOwnershipContent`（真实 PDFBox、真实 extractor，仅替换 HTTP）。不得改排 PDF 为“姓名:邮箱”，不得手工补作者证据。修改源码后重新编译，再测试。
- Applies to：原文 ZIP、解析测试、真实消费链测试和测试报告文件。
- Violation consequence：错测替身或旧 target/classes，无法证明修复真实案例。
- 来源：K-named-fixture-must-use-real-row、K-source-recall-needs-original-layout；本次已遇到旧 target/classes 与源码行为不同。

## 现状审计

### 1. 已确认的根因及证据

代码路径统一前缀：`src/main/kotlin/com/weibo/talentintroduction/`。行号以本计划基线为准。

| 根因 | 代码证明 | 原文证明 |
| --- | --- | --- |
| 连续符号漏计 | `discovery/service/PdfAuthorContactLayout.kt:165-175` 遍历单个 glyph；168 行 `signatureBefore(...) ?: continue`；181-185 行只匹配符号前姓名，33 行正则尾部只允许数字/逗号/空白。因此 `†,*` 中的第二个符号不能继承第一处署名。 | `trace.json` Chee：`†:[null], *:[5]`；仅诊断性移除 `†,` 后 `*:[null,5]`，错误独占消失。 |
| 解码损坏导致假独占 | 同文件20行仅识别 `[*∗†‡§]`，191-192行 `singleOrNull()`；`?` 不参与计数。 | Siria 行为 `Alessandro Siria1,?`，Bocquet 为正常 `∗`，集合 `*:[6]`；诊断替换后 `[5,6]`。视觉原文见审计 PDF 第二页。 |
| 裸脚注截断绕过 | `PlainTextEmailExtractor.kt:12,18-20,35-41` 邮箱正则找后缀，截断过滤要求 `e-mail/email address:`。`PdfAuthorContactLayout.kt:78-81` 的 segment 已剥掉标记；同文件207行 `substring(offset + 1,end)`。 | Lun Contact 为 `∗ lun yue@msn.com`，认证了后缀；原 PDF mailto 仍为 `lun_yue@msn.com`。 |
| 正确 mailto 不能纠错 | `PdfEmailExtractor.kt:306-312` 只把单地址加入 clues；316-318拼到文本。`SourceAuthorEmailResolver.kt:158-169` 按地址查证据，故完整地址无身份、后缀有身份。 | `prod-results.json` 的 Lun 输出同时包含二者。 |

审计目录：`docs/audits/2026-09-30-discovery-three-case-replay/`。线上字节码和当前源码重编译均复现 3/3，三个错误结果的 SOURCE_SHA256 与存量完全一致；这里是定向样本复现率，不是总体错误率。

已归档 `original-inputs.zip`，SHA256：`9f5802b0d0862126c570c81f15a0e6053d26fa3abc99ab25ce69aaa81fec6a27`。成员为三篇 PDF、三份 OpenAlex JSON、manifest；不再依赖临时目录。其 PDF SHA256：

- chee.pdf：`0daf96634f750d280bb1330d9b95d87f4ea04e54bee813db5c372092028fdb99`
- lyderic.pdf：`7459f1af390be438463bbd1b97d5cc79a183fd2ffb09310e2448d12736fb8f9b`
- lun.pdf：`7e2468ed9ad9ada830f943a1a5b1bc423704c0a6b51ea672fe250b5aa74805ba`

(来源: K-named-fixture-must-use-real-row、K-source-recall-needs-original-layout)

### 2. 内存 Contact / AuthorEmail，不新增存储格式

- 模型：`PdfAuthorContactLayout.kt:12-14`，Contact 含 email/authorIndex/page/authorText/contactText；`evidenceText` 为原始页面/作者/联系片段。格式不改。
- Contact 写入：`collect` 标记分支81行、作者联系段落105行；`endContactBlocks` 131行。只改标记分支输入的完整性，不给段落/文末名单新规则。
- Contact 读取：`SourceAuthorEmailResolver.resolvePdf:42-43` 转 Claim；`results:158-169` 按唯一作者输出完整身份或全空身份。
- grep 回执：`rg -n 'PdfAuthorContactLayout\.(collect|selectedPages)|resolvePdf\(' src/main src/test`。
  - 生产：`PdfEmailExtractor.kt:291,298,316`；`SourceAuthorEmailResolver.kt:35` 定义。
  - 测试：`SourceAuthorEmailResolverTest.kt:88,91,126,214,335,346`；`PdfEmailExtractorTest.kt:92,206`。
- 交互 X-1：标记解析生成 Contact → resolver 将其视为强证据。必须同时检查中间 Contact 和最终 AuthorEmail，不能只测最后名字为空。

### 3. PlainTextEmailExtractor 共用读路径

无持久化状态；输入是源文本，输出邮箱字符串列表。抽取逻辑改动会影响以下调用，不将其误称为 PDF 私有实现。

回执命令：`rg -n 'plainTextExtractor\.extract|emailExtractor\.extract|emails\.extract' src/main/kotlin/com/weibo/talentintroduction/discovery`。

| 调用位置 | 读取目的 |
| --- | --- |
| `PdfAuthorContactLayout.kt:80,104` | 标记片段、作者联系段落 |
| `PdfEmailExtractor.kt:305,311` | 尾页线索、mailto 地址过滤 |
| `SourceAuthorEmailResolver.kt:32,45,58` | 文本/PDF/HTML 可见文本邮箱集合 |
| 同文件 `77,104,152` | HTML 联系节点、命名 mailto、明确文本联系字段 |
| `CoreDataSource.kt:161` | 判断内嵌全文是否已有邮箱，决定是否回退 PDF |

- 交互 X-2：同一邮箱同时经过 PDF 页面文字和 Contact 两条抽取路径。标记上下文若只保留在其中一条，保护会被绕过。
- 交互 X-3：HTML/CORE 也调用共用 extractor；新增过滤必须限定裸标记残缺形态，以现有 HTML/CORE/纯文本正常样本回归。`PdfEmailExtractor` 的 mailto 保持当前无归属线索语义。

### 4. discovery_paper_job.extraction_json 缓存契约

本计划不改 SQL/schema；常量升级影响既有缓存是否可消费，因此审计其读写边界。

- Schema：`V133__create_discovery_paper_queue.sql:119-157`。`extraction_json MEDIUMTEXT NULL`；`metadata_json MEDIUMTEXT NOT NULL`；输入版本为 `payload_version INT NOT NULL`，与 JSON 内 `identityRuleVersion` 分离。`(stream_id,item_key)` 唯一；status 限 PENDING/RUNNING/SUCCEEDED/RETRY_WAIT/FAILED；租约与 generation 控制更新。
- JSON 模型：`EmailExtractionOutcome.kt:21`，`identityRuleVersion: Int? = null`，不新增字段。
- 写路径：
  1. `ExpertDiscoveryService.extractQueuedItem:2005` 写新结果版本进序列化 JSON。
  2. `DiscoveryPipelineService:1022-1024` 调 `DiscoveryPaperQueueRepository.saveExtraction:1009-1049`，以 RUNNING+lease_token+generation 条件保存 JSON，更新字节配额。
  3. `enqueuePage:775/818`、`insertFailedItem:1252/1275` 新建 job，不列 extraction_json，初值 NULL；去重插入不覆写结果。
  4. `clearTerminalPayloads:1351/1373` 清终态 JSON；`deleteTerminalJobs:1391` 删除过期终态行。两者不改。
- 读路径：
  1. `jobRow:1497/1506` + `JOB_COLUMNS:1534` 读取 JSON；由 `nextDueJob:920`、`nextDueOrdinaryJob:940`、`lockJob:1442` 使用。
  2. `DiscoveryPipelineService:1005-1006` 非空跳过抽取；1074行交消费者，1076-1082行将不可恢复结果置 FAILED。
  3. `ExpertDiscoveryService.consumeQueuedItem:2041-2047` 反序列化并比较版本，版本不符即返回，不走身份建立/邮箱验证/ES 写入。
  4. Repository `completeJob:1086`、`completeJobWithExperts:1149` 用 JSON 是否为空决定释放配额；不解释版本。
- 交互 X-4：新 parser → 缓存 producer → 非空结果重用 → consumer。需要版本升级而非只改 parser。
- 代码现状纠正：当前不兼容缓存的行为是 FAILED，**不是自动重抽取**。沿用此机制，禁止扩成缓存迁移工程。
- 本次全仓代码扫描：`rg -n --no-ignore 'extraction_json|saveExtraction\(' --glob '!docs/**' --glob '!target/**' --glob '!output/**' --glob '!.git/**' --glob '!node_modules/**' .`，命中路径/行：
  - main：Pipeline `981,1022`；Repository `281,1009,1025,1373,1506,1536`；V133 `127`。
  - test：PipelineTest `618`；RepositoryIT `425,434,460,475,590`。
  - 补充 `rg -n 'extractionJson|identityRuleVersion|EXTRACTION_VERSION' src/main` 对应上列序列化/读取位置。

(来源: K-identity-cache-version-and-admission、K-plan-quantified-claims-need-grep-receipts)

### 5. 消费及专家写入边界（仅隔离测试，不修改存储）

- 同步 `consumeOutcome:1612-1636` 与队列 `consumeQueuedItem:2059` 进入同一 `consumeOutcomeInternal`。
- `ExpertDiscoveryService:1702-1711` 先 `identityRejection`；`2220-2223` 当前按 given/family 是否空判断。未归属邮箱计 `IDENTITY_UNRESOLVED`，在邮箱验证、去重、写入之前 continue。
- `1731-1748` 构建 profile、`toIndexMap`、`ExpertIndexWriterService.indexToRaw`、补全入队、`promoteDiscoveredToCandidate`；不改这些写入逻辑。
- 交互 X-5：解析后身份为空 → 既有 consumer 拒绝新建错误专家。测试复用 `ExpertDiscoveryServiceTest.installOwnershipStorage:565` 的真实 writer + 内存 HTTP 存储，以及 `ownershipEnvelope:591`，不接线上 DB/ES。
- ES schema/其他读写路径不属变更目标：本计划无 ES 字段、mapping、writer 或历史文档变更，不做 ES 全仓迁移式改造。

### 6. 已有测试与明确边界

- `SourceAuthorEmailResolverTest:355` 已保护“一作者两个邮箱”；377行保护带 `Email:` 的截断，但没有此次裸标记形态。
- 同文件134行六篇真实原文、208行未知署名共享标记、223行同一联系行多标记、239/252行作者段落边界，必须保持。
- `PdfEmailExtractorTest` 的 `mailto URI is a single filtered clue and cannot override text ownership` 测试明确“不覆盖文本身份”；该行为不改。
- `PlainTextEmailExtractorTest:39,73,82` 保护真实 brace/wrapped 样本与换行/段落边界。
- `DiscoveryIdentityTest:54` 固定当前 `20261003`，必须更新，不可仅修改生产常量。
- `DiscoveryPipelineServiceTest:1855-1888` 已有旧版拒绝 + 当前版成功用例，但旧值目前为 `20261002`，需覆盖直接前版 `20261003`，并保留更旧值/null 拒绝。
- 知识取舍：采用唯一来源证据、真实原文、跨 producer/consumer 回归；拒绝旧知识里扩大外联/晋升 gate 或自动重抽取的建议。所读条目未达90天衰减；不做无关知识库清理。

## 实现方案

### 阶段 1：固化真实失败与回归输出（I-1～I-6）

文件：清单4、5、6、10；本阶段先写失败测试，尚不改生产逻辑。

- [ ] 将审计 `original-inputs.zip` **逐字复制**为清单10；验证 archive SHA 和内含 PDF/JSON SHA，不联网更新作者名，不重生成 PDF。
- [ ] 在清单4放资源读取辅助函数，复用 `zipMembers`、`fixtureSha256`、`openAlexPaperAuthors`；不新建通用测试框架。
- [ ] 在清单6用 `extractOwnershipContent` 跑原文。断言 Chee/Lydéric 完整邮箱仍存在且身份全空，Lun 后缀缺席、完整 mailto 存在且身份全空。额外抓取 layout contacts，断言两处错绑 Contact 不存在、Lun 错误后缀 Contact 不存在。
- [ ] 将三条 baseline 证据哈希作为重放前诊断记录，不把错误哈希作为修复后应保留值。修复前预期三项负例失败；保留失败输出。
- [ ] 测试产生 `target/discovery-plan-acceptance/pdf-contact-integrity.json`，包含 fixture SHA、每篇 contacts/resolved、所有身份字段、methodUsed/fulltextObtained/httpRequests、断言通过值，以及阶段2/3的 syntheticControls、正向 positiveControls 和文本输入输出表；不写仓库固定结果来冒充运行输出。

### 阶段 2：标记组与损坏标记局部修复（I-1、I-2、I-4）

文件：清单1、4。

- [ ] `markerOwners` 改为按连续标记组解析：组是由数字、逗号、空白及受支持符号组成、至少含一个支持符号的后缀段。只为组首寻找前置署名，组内每个符号共享该署名的拥有者集合。跨字母姓名边界不能继承前一个拥有者。
- [ ] 未能解析署名的已识别符号至少登记 null，不能 `continue` 后让其他作者看似独占；不新增姓名连字符标准化/模糊匹配。Chee 的 OpenAlex 连字符不同仍可为 unknown，这足以拒绝共享绑定。
- [ ] 在 markerOwners 输入的 `authorArea` 有 `?` 或 U+FFFD 时，返回不可提供独占证据的空映射。只影响该页标记分支，独立姓名联系段/文末名单仍按旧规则处理；保留原文 evidenceText，不“纠正”源文星号。
- [ ] 增加 SYNTHETIC 用例：`Jane Doe1,†,*` + `John Smith2,*`；第二署名未匹配元数据；同一作者独占 `†,*`；同一联系行两标记；署名区损坏符号；正文/标题/其他页问号不阻断已确定正常署名区；损坏标记但另有独立 `Jane Doe: a@uni.edu` 仍可凭独立记录绑定。
- [ ] 保持 `Contact`、`collect` 和 resolver 的对外签名，不新增持久字段。私有 helper 可在本文件内调整，不拆新模块。

### 阶段 3：裸标记的残缺邮箱过滤（I-3、I-4，保护 N-3/N-4）

文件：清单1、2、4、5、6。

- [ ] 在 `PlainTextEmailExtractor.extract` 的 match 过滤处增加 I-3 的窄规则，与现有 `splitLocalPartBeforeMailbox` 并列；保留现有规则，不改 emailRegex 的邮箱语法。
- [ ] 匹配上下文限定当前行/当前标记片段；横向空白用 `[ \t]` 或等价字符类，不能无意用 `\s` 跨越段落去吞上一个作者姓名。
- [ ] `markerSegments` 的消费处把该片段自己的 marker 传给 extractor（例如 `"$marker$segment"`），不把整行交给该 owner；Contact/evidenceText 仍存原始行。由此 `∗ lun yue@msn.com` 在 found 和 claims 两路均被过滤。
- [ ] 明确输入/输出回归：
  - `∗ lun yue@msn.com` → 空；`* yin- qiu001@e.ntu.edu.sg` → 空；既有 `*Email: ...` 坏例仍空。
  - `*lun_yue@msn.com`、`* lun_yue@msn.com` → 完整地址；`* a@uni.edu; b@uni.edu` → 两个地址。
  - `* Email: a@uni.edu`、`*Corresponding author: a@uni.edu`、`Contact: a@uni.edu` → 完整地址。
  - `* a@\nuni.edu` → `a@uni.edu`；`a@\n\nuni.edu` → 空；既有花括号/at/dot 样本结果逐项不变。
  - 正确 mailto 与被截断文字同时存在：仅错误后缀被删除，完整 mailto 不获得虚构身份。
- [ ] 不为 `lun` 特判、不追加邮箱长度门槛、不从两个邮箱的后缀关系推断冲突、不实现坐标链接系统。

### 阶段 4：缓存兼容与真实消费者回归（I-4～I-6）

文件：清单3、7、8、9；读取 parser 和 fixture，不改 consumer/repository 生产代码。

- [ ] `DiscoveryIdentity.EXTRACTION_VERSION=20261004`；保留 `VERSION=20260925`。执行前若该值已被其他工作占用，停止常量编辑并报告基线变化，不能悄悄用同版本表达不同规则。
- [ ] 清单7更新精确常量断言；历史有效 proof 的 accepted 行为保持。
- [ ] 清单8复用 `installOwnershipStorage`：三篇原文结果按当前版本进入真实 `consumeQueuedItem`，断言目标无归属邮箱不调用邮箱验证、不产生 raw/candidate 捕获写入；Lun 错误后缀根本不进入消费者。
- [ ] 加正向控制：真实现有正常 PDF + 合成明确一人两邮箱经过同一消费者，正确姓名/作者ID/机构整体传递，两个地址各保留。复用已有 fixture/帮助方法，不手工给负例补 evidence。
- [ ] 清单8直接消费旧 `20261003`、更旧版本、null，均为 unsupported、0 专家写入。新 producer `extractQueuedItem` 的序列化 JSON 必须带20261004；不把手工 copy 常量当 producer 验证。
- [ ] 清单9扩展已有 superseded-cache 用例：20261003、20261002、null 分别在新 harness 下 FAILED、reason 精确为 unsupported、下载次数0、专家写入0；当前版本合法控制 SUCCEEDED/indexedExperts=1，仍无重复下载。异常样本的新版本线索不会生成专家。
- [ ] 报告 `target/discovery-plan-acceptance/pdf-contact-consumer.json`（含真实 producer 版本、实际捕获文档与验证调用）、`pdf-contact-cache.json`（队列状态），记录版本、状态、原因、下载/写入次数与正向控制，不改变运行时 API。

### 阶段 5：编译与定向验收（I-1～I-6）

- [ ] 在执行工作区先完整重编译；不以现成 target/classes 的行为替代源码验证。
- [ ] 最终使用下列命令（单行；不启动应用，不连接线上服务）：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=SourceAuthorEmailResolverTest,PlainTextEmailExtractorTest,PdfEmailExtractorTest,DiscoveryIdentityTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,CoreDataSourceTest,JatsXmlEmailParserTest test
```

- [ ] 查看 Surefire 汇总和三份运行报告。已有真实六篇 PDF、HTML 命名联系人、花括号/换行、尾页、超时测试不删断言、不降低真值。
- [ ] 若已有其他测试基线失败，记录同基线重现证据，不能改范围外文件“顺手修”；报告本计划验收是否受阻。
- [ ] 本次不部署、不触碰存量；实现完成后按计划独立验证，再开始人工验收。

## 变更文件清单

执行白名单 **10 个文件**：3 个生产文件、6 个测试文件、1 个原文 ZIP；一个发现解析与消费子系统，新增共享存储字段0。计划正文和本次审计/知识文档属于规划产物，不计入未来代码执行白名单。

| # | 精确路径 | 改动 |
| --- | --- | --- |
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` | 标记组、损坏署名区、保留脚注标记上下文 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractor.kt` | 裸标记残缺邮箱窄过滤 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` | 仅抽取兼容版本常量 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | 标记回归、fixture 读取辅助、独立证据正向控制 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractorTest.kt` | 残缺/完整文本边界 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | 三篇原文、mailto、实际 extractor 报告 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` | 新缓存版本/旧 proof 保持 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 原文 parser→consumer→writer 隔离测试及报告 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt` | 缓存版本生产/消费边界回归及报告 |
| 10 | `src/test/resources/discovery/ownership-20260930.zip` | 逐字复制已归档原文 ZIP |

`PdfEmailExtractor.kt`、`SourceAuthorEmailResolver.kt`、业务 consumer、repository、配置、migration 生产代码不在修改白名单。若测试证明这些文件确需改变，先提供失败证据并修订本计划，不自行扩大。

## 验收标准

- I-1：真实 Chee 错绑不再有 Contact/身份；连续组共享/未知拥有者不绑定；独占连续组能绑定；不同作者相邻组不串；现有同联系行多标记回归通过。
- I-2：真实 Siria 星号损坏不再误归属 Bocquet；署名区 ?/U+FFFD 阻断标记证明；独立明确记录及其他页不被该局部规则阻断。无 `?→*` 猜测替换。
- I-3：Lun 的 `yue@msn.com` 在 contacts 与最终 emails 均缺席；正确 `lun_yue@msn.com` 留存；纯文本边界表逐项验证。完整标准邮箱不得靠前缀长度或名单拒绝。
- I-4：两篇共享邮箱仍在 resolved，givenNames/familyNames/orcidId/openAlexAuthorId/affiliation/institutionName/institutionCountry/institutionSource/institutionType/identityEvidence 全空；对应 isCorresponding 为 false。正常唯一作者一人多邮箱保持证据和完整身份；HTML/XML/CORE 正向回归通过。
- I-5：producer 输出20261004；direct consumer 和真实 pipeline 都拒绝20261003/20261002/null，精确 reason + 写入/下载0；合法新缓存成功；旧持久化 proof 版本未变。租约/CAS/字节配额逻辑 diff 为0。
- I-6：ZIP与成员 SHA一致；实际测试重新编译后运行；报告出自本次执行，不沿用上次 target 输出；真实负例与 SYNTHETIC 控制有区分。
- X-1/X-2：真实 PDF → Contact → resolver → extractor outcome 同时断言，不仅名字空。
- X-3：CORE内嵌全文、HTML联系人、完整 mailto 与通用文本提取器回归保留；仅新增的坏形态行为变化。
- X-4/X-5：真实原文解析结果序列化进消费链，捕获 raw/candidate 写入，不能只用 Mockito 返回空列表当修复成功。
- 范围：生产 diff 仅清单1～3；无线上连接/修复脚本；无新依赖、DB/ES字段、外联规则。执行阶段报告文件仅在 target 生成。

## 人工验收清单

下面为权威版本。执行完成后，人工验收开始时才导出同目录 `discovery-pdf-contact-integrity-acceptance.md`，含 A-n、勾选框、验收人、日期、结果/备注；现在不生成。

各项均在实施完成的隔离工作区进行；下载/API/ES/MySQL 为测试传输替身，不使用线上。验收人按命令和 JSON 值查看结果，无需阅读生产代码。测试失败也要能输出已观测数据，不能使用预填通过报告。

### A-1：两篇共享标记原文不再错绑

- 前置条件：执行者已提供清单10及编译好的测试；归档SHA为上文 `9f5802...`。
- 操作步骤：1. 运行阶段5命令。2. 打开 `target/discovery-plan-acceptance/pdf-contact-integrity.json`。3. 对照ZIP里的 chee.pdf 第1页、lyderic.pdf 第2页与输出。
- 预期结果：chee 的 `ysjang@ucla.edu`、`cheewei.wong@ucla.edu` 均存在且 identityEvidence/givenNames/familyNames/ORCID/作者ID/机构字段为空；lyderic 的两个 ens.fr 邮箱同样为空身份；不存在错误 Contact。methodUsed 为 PDF_PARSE，httpRequests=1，fulltextObtained=true。
- 覆盖：O-1、I-1/I-2/I-4/I-6、X-1。

### A-2：残缺邮箱消失，正确 mailto 留存

- 前置条件：同A-1。
- 操作步骤：1. 查看同一报告 lun 条目。2. 对照 lun.pdf 第1页邮箱与报告里的 mailto/最终邮箱。
- 预期结果：contacts 和 resolved 中 `yue@msn.com` 数量均0；`lun_yue@msn.com` 恰1条、身份字段为空；`mgaarde1@lsu.edu` 仍存在。报告不声称已恢复 Lun 的作者身份。
- 覆盖：O-2、N-4、I-3/I-4/I-6、X-2/X-3。

### A-3：正常唯一作者、一人多邮箱和独立证据仍有效

- 前置条件：阶段5测试已生成同一报告的 syntheticControls 与 positiveControls，输入文本/作者列表一并输出。
- 操作步骤：1. 查看独占 `Jane Doe1,†,*` 控制。2. 查看 `*Email: first@uni.edu; second@uni.edu` 控制。3. 查看署名损坏但另有 `Jane Doe: a@uni.edu` 独立证据控制。4. 查看 `pdf-contact-consumer.json` 正向控制。
- 预期结果：前两项均绑定 Jane Doe；两邮箱控制输出恰2地址且各有 SOURCE_SHA256；第三项 a@uni.edu 仍凭独立记录绑定。消费报告中正向作者ID/机构等值等于其输入作者字段，两个邮箱分别通过真实writer进入隔离raw/candidate捕获器。
- 覆盖：N-1/N-2、I-1/I-2/I-4、X-1/X-5。

### A-4：文本和其他来源回归未扩大误杀

- 前置条件：同A-1，报告包含阶段3明确输入输出表与现有回归测试结果。
- 操作步骤：1. 查看文本控制表。2. 查看 Surefire 的 PlainTextEmailExtractorTest、CoreDataSourceTest、JatsXmlEmailParserTest、SourceAuthorEmailResolverTest、PdfEmailExtractorTest 汇总。3. 查看 mailto/尾页正向控制。
- 预期结果：`* a@uni.edu; b@uni.edu` → 两地址；`a@\nuni.edu` → a@uni.edu；双换行坏例为空；30个原始 brace 地址总量不变（现有 fixture 断言）；正常HTML/XML联系人测试失败数0；现有尾页选择为[1,2,62]，完整 mailto 不凭链接新增身份，URL query 的 cc/bcc 不变成收件线索。
- 覆盖：N-3/N-4、I-3/I-4、X-3。

### A-5：旧缓存不能绕过，新结果正常消费

- 前置条件：阶段4测试分别构造旧版/null和新版隔离队列，各用独立harness，生成缓存报告。
- 操作步骤：1. 打开 `target/discovery-plan-acceptance/pdf-contact-cache.json`。2. 查看每个版本的consumer/pipeline字段，再从 `pdf-contact-consumer.json` 查看真实producer版本。3. 查看 DiscoveryIdentityTest 汇总。
- 预期结果：producer版本20261004；20261003/20261002/null均FAILED，reason为IDENTITY_EXTRACTION_VERSION_UNSUPPORTED，下载0、专家写入0；当前版正常控制SUCCEEDED、indexedExperts=1、缓存消费下载0；身份证明版本仍20260925且已有有效proof测试通过。
- 覆盖：O-3、N-5、I-5、X-4。

### A-6：解析错误不会进入新专家写入链

- 前置条件：同A-1；消费者仅连接测试捕获器，未连接线上存储。
- 操作步骤：1. 打开 `target/discovery-plan-acceptance/pdf-contact-consumer.json`。2. 查看三篇的解析地址、filtered原因、邮箱验证调用、raw/candidate捕获文档。3. 与A-3正常控制比较。
- 预期结果：未归属目标地址计IDENTITY_UNRESOLVED，不进行邮箱验证、不出现在捕获文档中；错误后缀yue@msn.com在输入就不存在；正向唯一联系人仍可写入。报告列出实际捕获文档，不能仅提供“passed=true”。
- 覆盖：O-1/O-2、N-2/N-5、I-4/I-6、X-5。

---

规划自检：所需七节齐备；O-1～3、N-1～5、X-1～5均有A项；无前端，无样式契约；代码白名单10文件/1子系统/0新共享字段；每阶段标注不变量；缓存与消费者交互有代码行证据。原文证据已固定，未知损坏类型明确未承诺。未执行修复，未生成提前验收勾选文件。
