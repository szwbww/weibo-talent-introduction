# 01 — 明确联系证据的漏解析修复

状态：待执行；2026-11-01。仅计划，不表示已实施或发布。

## 需求描述

让首次采集、论文解析对照、历史重放使用相同修复后的姓名邮箱关联规则。只修 00 已重现且原文可明确归属的案例。

保持不变：①不猜邮箱前缀、最近作者或共同通讯名单；②资格/Apollo 准入规则不变；③旧身份凭证和 PAPER/RECORD 缓存兼容，原文 first.outcome 不覆盖。

不做：OCR、跨语言姓名猜测、元数据单作者直接认领、直接更新专家、扩大下载量。两个子系统：解析器与归档纯计算。

## 关键不变量

### Invariant I-1: 明确归属
- Rule: 接受原文命名联系行、邮箱后唯一全名/缩写、唯一作者标记脚注；缩写必须在同论文作者集合中唯一且原文明确标注。单作者也必须有本人联系证据。相互矛盾的两个作者声明返回未知，不取第一个。
- Applies to: SourceAuthorEmailResolver、PdfAuthorContactLayout
- Violation consequence: 错绑专家身份。
- 来源: K-author-identity-needs-email-evidence

### Invariant I-2: 版面边界
- Rule: 跨行只在同页、同栏、同明确联系块内；下一作者任职段、references、abstract 均终止当前块。标记需署名侧与邮箱侧唯一对应，机构脚注本身不能变成作者证据。
- Applies to: PDF 坐标与文本两个出口
- Violation consequence: 跨栏串人与参考文献误绑。
- 来源: K-source-recall-needs-original-layout

### Invariant I-3: 统一版本与事实
- Rule: 新增解析规则只更新独立 parser 实现版本；DiscoveryIdentity.VERSION=20260925、EXTRACTION_VERSION=20261004 保持。论文职称事实仍为发表当时且必须归属本人，不将历史事实直接变成当前资格。first.outcome 不改。
- Applies to: 首次抽取、PaperBodyAnalyzer、归档缓存消费者
- Violation consequence: 旧缓存被整体拒绝或误准入。
- 来源: K-identity-cache-version-and-admission

## 现状审计

完整源码回执：[paper-reprocess-evidence/README.md](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/README.md)、[paper-reprocess-evidence/paths.json](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/paths.json)。共享存储全部生产读写路径及交互编号见主计划「现状审计」；本子计划只改变下述接缝，其他匹配路径为回归边界。当前本地基线 `b758eb0`，工作区存在先前改动；不得当作已核实的生产部署版本。
- `SourceAuthorEmailResolver.kt:117–183`：仅特定显式前缀姓名 + 下一行 Email 被拼接；全名先匹配且剩余内容受限制；`matchesExplicitLabel` 的 initials 使用逐字母点号。说明现有限制存在，但每个样本是否因此失败需 00 重放确认。
- `PdfAuthorContactLayout.kt:145–218`：adjacentAuthorContacts 从完整姓名行向下找邮箱，横向差和垂直距离有界；endContactBlocks 依赖特定标题；authorHeader 位于页首 36% 区域。不得把所有 325 归咎于其中一个条件。
- `PdfEmailExtractor.kt:333` 仅将 head page 的正文传入 text resolver，tail 另作线索；03 不允许另造解析器绕开该入口。
- `PaperBodyAnalyzer.kt:23–46` 供分析和资格证据复用（X-1/X-2）；解析测试位置见文件清单。

## 实现方案

前置：00 完成真实回执。以下是**受证据门控的实现清单**，不是已经证明每个类别均可修复的断言。

1. **联系语法（I-1/I-2）**：在 `SourceAuthorEmailResolver.kt` 扩展 00 证明的显式联系块：姓名→机构行→邮箱、邮箱后括号全名/唯一缩写、邮箱与姓名换行。统一 Unicode/空白处理但保存原句；不能 strip 全文换行后做跨段匹配。缩写是否带点可归一，仅对显式括号/联系标签操作。
2. **版面（I-1/I-2）**：`PdfAuthorContactLayout.kt` 采用已有 Line(page,x,right,y,text) 与 boundary，不另造 OCR/坐标框架。仅加入真实样本证明的同一联系行/联系块与标记形式；完整署名作者中遗漏的未知署名仍阻断唯一标记判断。47 个未确认分栏项不能靠放宽 x/y 阈值放行。
3. **页覆盖（I-2/I-3）**：`PdfEmailExtractor.kt` 保留页号与原始顺序；已选择的 tail 文本若有明确联系条目应进入相同 resolver。00 若证明证据在未读中间页，先将这些行单列 PAGE_NOT_SCANNED；本子计划不擅自全量扫描所有 PDF 页面/改变线上 maxPages 配置。该类仍未修复必须在报告显式计数，不能算入召回。
4. **版本/结果（I-3）**：`PdfAuthorContactLayout.RULE_VERSION` 升为执行当天唯一实现版本，`PaperBodyAnalyzer.versions()` 同步读取；文本规则也由该版本组合失效。保留原 first 基线。qualification 解析不完整与 identity 失败分别记录；不得用资格为空推断身份失败。
5. **验证（I-1–I-3）**：三个测试文件加入原始字节输入回归（原文外置安全目录；合成负例内联在本清单测试文件中，不新增未列fixture文件）。00 工具以同一输入分别跑旧新产物；每类至少正例与反例，输出 gain/loss/conflict 列表。解析出的完整名字必须继续经 `DiscoveryIdentity.validEvidence` 与实际 consumer，不能只断言正则命中。

## 变更文件清单

| 文件（仓库根目录相对路径） | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` | 明确联系条目的跨行/姓名或唯一缩写归属 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` | 真实坐标联系块与作者标记边界 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | 原始段落保留、可观察页覆盖；仅重放证实的读取遗漏 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperBodyAnalyzer.kt` | 共享解析版本/完整度诊断 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | 语义正负例 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperLayoutRegressionTest.kt` | 生产 PDFBox 字节级版面回归 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisServiceTest.kt` | 首次基线、版本缓存和纯计算回归 |

共 7 个文件。未列文件禁止扩改；新增问题先记录证据并修订范围。

## 验收标准

- I-1：新旧同一原文差异逐条对照；任一无证据的错误新增关联为阻断，不允许拿召回增长抵消误绑。
- I-2：跨栏、下一位作者、共享邮箱、相同缩写、多署名同标记、参考文献邮箱全部保持不绑定；未扫描页显示 PAGE_NOT_SCANNED。
- I-3：新规则能使新分析缓存失效；既有 PAPER/RECORD 序列化结果仍可消费；资格返回值不因单纯姓名修复改变。
- X-1/X-2：同 bytes/metadata/版本下首次抽取、分析、重放的姓名邮箱集合完全相同。运行文件清单中的 JDK11 测试并重跑 00。

## 人工验收清单

### A-1: 真实漏绑样本回归
- 前置条件：00 已确定一条旧失败、原文明示姓名的记录；保留旧新构建和同一 PDF。
- 操作步骤：1. 分别对照解析；2. 打开证据与差异行。
- 预期结果：旧结果姓名空；新结果为原文中该完整姓名，且有 recordId、SHA、页码、原句；不出现新增准入。
- 覆盖：I-1/I-2，X-1/X-2

### A-2: 歧义与历史资格
- 前置条件：准备共享邮箱对应两作者、两个同缩写作者，以及仅历史教授原句的三个 fixture。
- 操作步骤：1. 解析三个；2. 查看绑定/资格与业务档案。
- 预期结果：前两份关联数 0；历史教授显示 HISTORICAL；专家和准入写入数 0。
- 覆盖：I-1/I-2/I-3，保持不变①②

### A-3: 旧数据兼容
- 前置条件：准备原有身份版本20260925和抽取版本20261004的 PAPER/RECORD 测试缓存。
- 操作步骤：1. 更新解析器；2. 消费旧缓存；3. 再查看原 first 快照。
- 预期结果：不新增 IDENTITY_EXTRACTION_VERSION_UNSUPPORTED；first 快照 SHA 不变；未覆盖页列为 PAGE_NOT_SCANNED。
- 覆盖：I-3，保持不变③


人工验收开始时才从此处导出同前缀 `-acceptance.md`；本次不生成衍生勾选文件。
