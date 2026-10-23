# 04 — 留存原文解析对照、职称与学位原句

状态：待执行。依赖 01～03；仅分析，不修改准入策略。

## 需求描述

可从某篇已保存原文生成解析对照，展示邮箱与姓名关联、职称/学位原句、人物绑定、页码和论文年份；结果保存后可重复查看。没有证据、部分页未分析和明确命中分开显示。

必须保持：原留档 first outcome 不改；ES 人物档案、当前职称、学位、年龄、国籍、准入及邮件状态不改。范围外：把论文作者顺序当师生关系、用论文质量评分资格、OCR、LLM、外网补抓、自动历史准入、历史 samples 自动迁移。

## 关键不变量

### Invariant I-1: 只有本人原句才是事实
- Rule: 证据必须绑定 paper.authors 中唯一的完整姓名；来自作者简介/明确个人说明段落，同段只有一个目标主体，保存原句、页码、paperYear、bodySha256。参考文献/致谢/导师称谓、另一作者的职称、含糊代词不得给目标人赋值。
- Applies to: PDF/HTML/XML/TEXT 证据抽取、JSON 持久化、UI。
- Violation consequence: 合作论文上的学生被错误标教授。
- 来源: K-author-identity-needs-email-evidence。

### Invariant I-2: 历史与现在分开
- Rule: 所有本文证据 temporalScope=PUBLICATION_TIME；未命中=NOT_FOUND，截断=PARTIAL，解析失败=ERROR，不能等于 NOT_MET。Assistant Professor、Associate Professor、Professor、博士已获学位、博士在读分类分开；PhD candidate/student 不能变成已获 PhD。不从共同署名推导师关系。
- Applies to: 词法分类、结果模型、对照、界面标签。
- Violation consequence: 历史在读被当成当前不合格，或助理教授被泛匹配成教授。
- 来源: K-paper-affiliation-is-not-employment；真实资格审计。

### Invariant I-3: 分析无业务写入
- Rule: 只能写 record.analysis_json/revision/updated_at；不调用专家写入、准入提交、候选同步、邮件或 Apollo。请求不下载缺失原文；文件已删仍可查看旧分析，重新分析返回 410。
- Applies to: 分析 API、重试、删除后查看。
- Violation consequence: 点击预览产生晋升/付费请求。
- 来源: K-extraction-samples-are-not-consumption。

### Invariant I-4: 对照可追溯
- Rule: analysis 带 schemaVersion=1、parserVersion（identityExtraction/pdfLayout）、analysisVersion=1、analyzedAt、inputBodyHashes、baselineCaptureAt、coverage。原始采集结果和新 parser 对保存原文的结果分别保留；缺旧基线显示 BASELINE_MISSING，不能用空数组假装原来全部漏掉。新增关联不是新增邮箱或专家数。
- Applies to: 持久化、缓存、统计、逐人对照。
- Violation consequence: 虚构优化收益，无法同批比较。
- 来源: K-source-recall-needs-original-layout；K-plan-quantified-claims-need-grep-receipts。

## 现状审计

- 服务器 `/root/paper-qualification-audit-4yo_4f8l/qualification-summary.json` 已重新读取：23 篇、432 页、52 个不同姓名；论文发表时“教授且已获博士”1 人，“博士在读”2 人，其余49无足够个人资格证据；证据论文年份2023；未复查当前任职、生产写入0。**这证明论文可能含本人事实，不证明当前代码已有自动提取器。**
- `PdfEmailExtractor.kt:315` 当前 private 字节解析→`:320` collect→SourceAuthorEmailResolver；无论文职称/学位输出。
- `PdfAuthorContactLayout.Contact.evidenceText` 已有 PAGE/AUTHOR/CONTACT；03 为新规则加 RULE，不把所有正文都写进人物档案。
- 01 record 的 metadata_json、outcome_json、analysis_json；已有写者01 service/02 capture，当前读者01 controller；本切片 analysis 是 analysis_json 唯一写者；读者 controller +05页面。不修改 schema、不改旧样本 schema1。
- 当前本地资格核验 `DiscoveryVerificationRules.kt:80–120` 明确处理 CURRENT employment/education/age；本切片不注入该 evidence，不修改规则或门禁。
- PDFBox 已是现有依赖；HTML 有现有安全文本解析套路；不新增依赖和外部服务。

## 实现方案

### T-1 按需分析与同一原文（I-3/I-4）

- `DiscoveryPaperAnalysisService` 通过 archive service 获取 record/可读 blob，不直接拼文件路径。
- 新 API `POST /api/expert-discovery/papers/{id}/analysis`，只允许 `force=false/true`；同 record 并发分析返回409 ANALYSIS_RUNNING，不加新队列表。已存在相同 parser/analysis version 且 input hashes 未变化，force=false返回缓存，不再解析。
- 在 01 的文件变更锁内取得短生命周期读取句柄，分析期间持有该文件读保护；删除预检查显示 BUSY，重试即可。不要持整个 SQL 事务解析 PDF；只在最后 CAS 写 analysis_json。前后 record revision/可用文件集变化时返回409，不提交过期分析。
- 原始 first outcome 增补 02 已定义的 `attemptIds` 列表作为准确输入锚点；04 只读取这批 hash 的仍可用文件进行对照。缺其中任一文件则 baselineCoverage=PARTIAL，不能给完整净增结论。没有原始 baseline 时仍可分析，但 delta=null。
- 同原文 PDF 调用 `PdfEmailExtractor.extractEmailsFromBytes`（仅将 private 改 internal，不改变现有参数、解析流程）；XML 用 JatsXmlEmailParser，HTML/TEXT 用 SourceAuthorEmailResolver。新输出按规范化邮箱和作者全名去重；合并冲突保留 unknown，不强行覆盖。
- delta 为“对照当时采集记录，新解析识别的关联”，不是严格端到端收益；UI注明原始 source fallback 与单原文重解析有差异。严格修复收益以03固定同原文双版本回放为准。

### T-2 有界、保守的本人资格证据（I-1/I-2/I-3）

新 `PaperQualificationEvidenceExtractor.kt` 是纯函数，不使用网络或模型：
1. PDF 按页读取文字和位置；最多100页、累计2MiB文本，每页前后检查10秒软时限；超限停止并记录 scannedPages/totalPages、PARTIAL。一个 PDFBox 页解析本身不保证被硬中断，不能把软时限宣传为绝对超时。邮箱解析仍用03的前2+末1页，不偷偷扩大生产解析范围。
2. XML/HTML不执行脚本/外部实体，只提安全文本/现有作者节点；XML 禁止 DTD/external entity；TEXT 用段落边界。没有 PDF 页码则 page=null，locator 为明确段落序号，不能伪造第1页。
3. 只接受作者简介标题（Biography/Biographies/About the author(s)）后的本人段落，或独立姓名行+后续连续本人说明段。本人必须唯一完整元数据姓名；下一个作者名/章节/参考文献即止。只靠 He/She、不含本人全名或含另一元数据作者完整名的段落，暂不出结构化事实。
4. 职称按长短顺序识别原句：Assistant Professor→ASSISTANT_PROFESSOR；Associate Professor→ASSOCIATE_PROFESSOR；其他独立 Professor→PROFESSOR。名词关键词只是必要条件，必须是本人任职断言（`<姓名> is/was ... professor`），不能是“with/supervised by Professor ...”。保存原词，不做中外职称等级翻译。
5. 学位：明确本人 `received/earned/obtained/was awarded ... Ph.D./PhD/doctorate`→COMPLETED_DOCTORATE；`PhD/doctoral student/candidate`→DOCTORAL_STUDENT。排除否定句及“expected/pursuing/working toward”；同段冲突保留 CONFLICT，不挑有利一条。规则词表逐字写入新类并用合成句测试，不从毕业年份反推博士完成。
6. 每条 fact `{authorIndex,authorName,kind,originalTerm,quote,page,paragraph,paperYear,bodySha256,temporalScope,rule}`；quote最多1000字符，定位原文仍完整保留；每 record最多100事实，超出则PARTIAL并记录原因。同人多条不重复计算人数；0个事实时不是0人符合，是“未发现本人明确原句”。

中文职称、非英文简介、扫描件本版只展示原文/未识别，不凭翻译猜测；后续需带真实原文单独验证后增规则。

### T-3 结果模型与查询（I-2～I-4）

在 `DiscoveryPaperArchiveModels.kt` 定义 analysis DTO；`DiscoveryPaperArchiveRepository.kt`增加analysis CAS及列表JSON投影，service调用这一唯一SQL写口，不能让controller直接写SQL。01已预留analysis_json，此处不新增数据库列。list的hasGain只作用于有完整baseline的deltaAddedBindings>0；未分析记录返回null，不归到“0新增”。本版只保存最新一次分析，原始first永远保留；不另建多版本分析历史表。

详情含 before/after/added/removed/conflicts、qualificationFacts/coverage；PDF仅跳页，不承诺浏览器内建阅读器能精确画框高亮；文本定位用quote和段落。01已删除文件时仍返回 analysis，content=410。

## 变更文件清单

9 文件；子系统 1（归档分析）；既有共享 store 新字段 0。

| 文件 | 动作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt | 新纯函数 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisService.kt | 新分析编排 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt | 只读句柄、分析CAS、列表投影 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt | analysis唯一SQL写口及列表投影 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveModels.kt | 分析DTO |
| src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperArchiveController.kt | analysis端点 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt | 暴露现有纯字节入口 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractorTest.kt | 主体/职称/历史语义 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisServiceTest.kt | 缺文件/缓存/CAS/零业务写 |

## 验收标准

- I-1：同段另一个人教授、导师Professor、参考文献教授、仅He/She、同名作者，均不能给目标生成事实；独立本人句可生成带hash/locator事实。
- I-2：Assistant/Associate/Professor三类不同；PhD student不为completed；缺证据NOT_FOUND，超限PARTIAL；历史事实不进入CURRENT employment。
- I-3：分析及再次查看网络请求=0、业务库/ES写=0；仅analysis_json/revision/updated_at变；删除后已有分析仍可读，POST重分析410；并发保护/删除/新原文触发CAS拒绝。
- I-4：first outcome逐字不变；不完整基线delta=null且missingBodies列明；重复请求命中缓存；真实23篇审计集用私有labels核查，不能超出已核实1+2给其余49造资格。若保守规则未能覆盖某条，报告NOT_FOUND并提供原句差异，不以猜测补齐指标。

## 人工验收清单

### A-1: 职称/学位、年份和主体
- 前置条件：隔离测试夹具含三位不同作者：甲“Assistant Professor”，乙“received his PhD”，丙“PhD student”；均在明确本人简介且paperYear=2023；另含对他人教授的致谢。
- 操作步骤：1. 对原文POST analysis。2. GET详情。3. 打开原句所在页/段落。4. 查这三人现有专家/准入状态。
- 预期结果：三种原词和分类分开，年份2023、PUBLICATION_TIME；致谢不赋资格；原文定位一致；专家和准入状态完全不变，没有“符合准入3人”计数。
- 覆盖：I-1/I-2/I-3；原文→分析→查看。

### A-2: 对照、缺失与删除
- 前置条件：一记录有完整旧first和正文，一记录缺旧基线，一记录2份正文中缺1份，一记录只有扫描图。
- 操作步骤：1. 分别分析四条。2. 对第一条再次分析。3. 删除第一条原文再GET详情/POST analysis。
- 预期结果：完整者显示真实前后关联数；缺基线者BASELINE_MISSING、delta=null；缺文件者PARTIAL、delta=null；扫描图NOT_FOUND而非NOT_MET；第二次命中缓存；删除后分析可读、重分析410。
- 覆盖：I-2/I-3/I-4；删除与分析交互。

### A-3: 真实资格审计回归
- 前置条件：服务器私有23篇/432页固定hash与人工labels，需提前验证尚有原文；仅在隔离验收实例运行。
- 操作步骤：1. 批量调用纯函数验收入口。2. 比对1位教授且博士、2位当时博士在读与其余49人。3. 查看所有新增事实的原句。
- 预期结果：任何结构化事实均有本人原句；其余49不能被自动认定满足/不满足；未覆盖的已知原句列为未实现覆盖，不作虚假成功；生产写入和外部请求均0。
- 覆盖：I-1～I-4；真实证据边界。
