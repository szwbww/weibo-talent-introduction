# 03 — 已验证的 PDF 邮箱与姓名关联修复

状态：待执行。依赖 02 完成留存与回退能力；本切片单独可部署。收益是补关联，不是增加邮箱或承诺准入人数。

## 需求描述

在现有版面解析中增加“同栏完整姓名后紧邻邮箱”和“同栏作者联系块”两条已做过真实样本实验的规则。新发现使用新规则，留存原解析以便对照；不自动改写历史专家或强制重抓论文。

必须保持：旧标记归属、跨作者边界、共享邮箱冲突、邮箱黑名单、下载预算、已有 79 组关联不退化。范围外：姓名顺序猜测、按邮箱前缀猜人、OCR、无原文样本收益推断、扩大页数、自动历史准入。

## 关键不变量

### Invariant I-1: 唯一完整元数据姓名锚点
- Rule: 新规则只接受一整行去边缘署名标记后的完整姓名，且规范化后在 paper.authors 中恰好对应一人。缺 given/family、同名两作者、正文提及、多个姓名拼接不得成为锚点。
- Applies to: 新增两条规则、回放及所有来源经 PDF parser 的调用。
- Violation consequence: 把全文中任意人名绑定到邻近邮箱。
- 来源: K-author-identity-needs-email-evidence。

### Invariant I-2: 几何和段落边界
- Rule: 同页、同栏、水平交叠、左边界差≤36pt；总垂直距离≤90pt，相邻有效行距离≤24pt；最多 5 行中间内容。遇下一位作者、章节边界、第一条含 @ 的行就终止；该邮箱行必须整行只含一邮箱及可选 Email 标签。跨栏、列表共享邮箱、跳过首个不合法邮箱行继续找，不允许。
- Applies to: PdfAuthorContactLayout 新 contact 生成。
- Violation consequence: 跨栏和跨段错绑。
- 来源: K-source-recall-needs-original-layout；2026-10-23 真实试验 candidates 函数。

### Invariant I-3: 旧证据与新证据统一裁决
- Rule: 新 contact 与原有 text/marker/contact claims 一起交给 SourceAuthorEmailResolver；同邮箱多 owner 必须冲突降级，不能新规则先命中就覆盖旧归属。只补已经从正文提取出的合法邮箱，不增加/合成邮箱地址。证据包含规则名、页码、姓名原句、联系原句。
- Applies to: resolver 合并、identity proof 生成、测试回放。
- Violation consequence: 旧正确关联被覆盖、收益虚增。
- 来源: K-author-identity-needs-email-evidence。

### Invariant I-4: 版本与历史不回写
- Rule: 新增PDF布局实现版本PdfAuthorContactLayout.RULE_VERSION=20261023；保持DiscoveryIdentity.EXTRACTION_VERSION=20261004及身份凭证VERSION=20260925不变；旧原文first outcome/version不改。队列已缓存 extractionJson 仍按其版本消费；不为版本升级清空缓存、改变事实指纹或批量重建专家。
- Applies to: Pdf contact evidence、DiscoveryIdentity、archive replay 对比。
- Violation consequence: 无法解释“当时为何没匹配”、历史状态意外晋升。
- 来源: K-identity-cache-version-and-admission；K-verification-cached-evidence-replay；K-source-recall-needs-original-layout。

## 现状审计

### 实验事实（2026-10-23 再次 SSH 读取确认）

服务器 `/root/discovery-layout-gain-czy_fjjk/gain-summary.json`：294 份有原文样本，旧已关联 79、保留 79；补回 52 组，涉及 23 份；相邻规则 34、作者块规则 18；新增邮箱地址 0；候选归属冲突 0、跨样本姓名冲突 0；合成边界检查 8。`gain-validation.json`：45 组在第 1 页、3 组第 2 页、4 组第 34 页；第 34 页是否落在实际末页须在冻结 manifest 核对，不能自行扩页补指标。

实现前必须验证原文仍存在并固定 SHA256；这些是一次 Python 实验的结果，**不是 Kotlin 修复已经达到的效果，更不是整批专家准确率**。源算法见 [实验代码](discovery-paper-layout-probe.py) 的 candidates/owner_of/checks；敏感 `gain-private.json` 保持服务器私有，Git 仅收 hash 与汇总。

### 代码与存储
- `PdfAuthorContactLayout.kt:43` collect 收集 glyphs；`:69` 处理 endContactBlocks，`:70` authorHeader 限制现有 marker 路径；`:293` toLines 提供坐标。当前没有实验里的独立同栏 full-name anchor 扫描。
- `PdfEmailExtractor.kt:313` selectedPages=前 maxPages（默认 2）+ tailPages（默认 1）；`:320` 交由 collect；`:340` resolver 合并。
- `SourceAuthorEmailResolver.kt:35–46` textClaims+contacts→results，统一多 owner 冲突；不另建身份系统。
- `DiscoveryIdentity.kt:26` 当前EXTRACTION_VERSION=20261004，这是缓存兼容版本；身份proof用独立VERSION=20260925。`ExpertDiscoveryService.consumeQueuedItem:2154`仅接受严格相等的缓存版本，直接升级EXTRACTION_VERSION会把旧缓存变成IDENTITY_EXTRACTION_VERSION_UNSUPPORTED。因此本次加独立PDF布局实现版本，**不动缓存协议常量**。归档记录实现版本，不要求旧缓存作废。
- 归档原始 outcome 的写者为 02 capture，消费者为 04 对照分析；不直接修改归档表。

## 实现方案

### T-1 在同一版面模型补两规则（I-1/I-2/I-3）

仅修改 `PdfAuthorContactLayout.kt`，新增 `adjacentAuthorContacts(lines,authors)`；在 collect 每页 toLines 后、authorHeader 的 `?: continue` **之前**调用，避免没有传统 header 的独立作者块继续漏掉。保留 selectedPages、toLines 和已有 marker/paragraph 路径。

算法逐项固定：
1. 完整姓名规范化：NFKD 去组合音调符、字母数字保留、其余变空格、小写、压空格；它只用于整行唯一 owner 查找，不反向猜姓名。锚点只去首尾 `*∗†‡§★`、尾部数字/逗号空白；不能删掉中间字母。
2. 行按 y/x 排序；逐锚点向下扫描；`line.y>anchor.y+1`，`line.y-anchor.y<=90`；`abs(line.x-anchor.x)<=36` 且 `line.right>=anchor.x-8` 且 `line.x<=anchor.right+8`；非同栏行跳过，不参与相邻行距离；合格行间距>24立即终止。
3. 下一个唯一作者锚点或行首 abstract/references/introduction/acknowledg/keywords/conclusion 停止。中间行>5停止。遇第一条含 @ 的行后结束该块，无论解析成败。
4. 邮箱行整行模式：可选 `E-mail:`/`Email:` + 一合法 mailbox + 空白；走现有 PlainTextEmailExtractor 及 blacklist 后才成为 Contact，不允许 brace-list、多地址或邮箱拼接。
5. 没有中间行→PDF_NAME_EMAIL_ADJACENT；有中间行→PDF_AUTHOR_CONTACT_BLOCK，额外要求中间内容包含实验的机构词 `universit|department|institut|school|laborator|college|faculty|centre|center|academy`，**或本页至少两个不同 owner 出现同样独立作者块结构**。不新增翻译/机构推断。
6. Contact 新增可选 rule 字段，默认 null 保持旧构造；evidenceText 旧 contact 字节不改，新 contact 追加 `RULE:<code>`，保留 PAGE/AUTHOR/CONTACT。所有候选一起返回原 resolver，不在这里覆写 AuthorEmail。

### T-2 版本与对照工具（I-3/I-4）

- 在PdfAuthorContactLayout定义RULE_VERSION=20261023；修改02新增的DiscoveryPaperArchiveCapture记录parserVersion为结构化对象`{identityExtraction:20261004,pdfLayout:20261023}`。非PDF正文也保留实际identityExtraction版本，pdfLayout用于说明当前应用所带PDF解析实现。旧first不改，latest及04新analysis记录新实现版本。禁止修改DiscoveryIdentity.EXTRACTION_VERSION或现有缓存版本门禁。
- `PdfEmailExtractorTest.kt` 用 PDFBox 构造实际双栏 PDF，不能只测字符串；包含跨栏、相邻下一个作者、同名、参考文献、多邮箱、未知机构重复作者块、页边界、符号损坏、旧 marker 共用归属。
- `SourceAuthorEmailResolverTest.kt` 添加旧 claim 与新 contact 冲突的整合测试；保留旧回归。
- 新 `DiscoveryPaperLayoutRegressionTest.kt` 支持环境变量提供**服务器私有**冻结样本路径；常规 CI 只运行合成 fixtures，真实样本缺失应明确 SKIPPED，发布验收不得把 SKIPPED 当 PASS。复用现有 DiscoverySampleReplay 的纯 parser，无 Spring/网络/ES/SQL 写入。
- `scripts/discovery_paper_archive/verify_layout_gain.py` 读取两份回放报告及私有 labels，按 (paper/bodySha,email,normalizedName) 对比；输出 aggregate JSON、原始已关联丢失、冲突、新增邮箱、新增关联。不得用运行中新下载文件代替同一原文。

## 变更文件清单

6 文件；子系统 1；既有共享 store 新字段 0。

| 文件 | 动作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt | 两条规则及 contact 证据 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCapture.kt | 记录独立PDF实现版本，不动缓存协议 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt | 真实版面合成回归 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt | 冲突合并回归 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperLayoutRegressionTest.kt | 私有原文回放入口 |
| scripts/discovery_paper_archive/verify_layout_gain.py | 汇总与 labels 对照 |

## 验收标准

- I-1：缺姓名/同名/仅姓名子串均新增关联 0。
- I-2：8 个原实验边界逐条移植，并补实际双栏 PDF、两不同作者块重复布局、5/6 行及 24/25pt 边界。
- I-3：固定完整 294 样本集、同一 SHA 原文：原79保留79、补关联目标52（34+18）、新增邮箱0、错误归属0。若少于目标，必须给出逐项差异证据；不可为凑数放松边界。原样本缺失则结论为未完成真实复验，不能重新命名另一批“294”代替。
- I-4：PDF实现版本可在新归档outcome查到；旧 first、旧 queue extractionJson 不改；业务写入 0。读回归包含既有 PdfEmailExtractorTest、SourceAuthorEmailResolverTest、DiscoverySampleReplayTest。
- 发布前把基线/新版本 HEAD、dirtyDiffHash、运行类 hash、配置、原文 manifest、结果汇总放服务器私有验收目录；Git 仅记录汇总和 hash。

## 人工验收清单

### A-1: 真实收益复核
- 前置条件：通过 RegressionTest 的私有样本入口固定原 294 样本；执行代理生成 hash 清单及前后对照汇总，所有原文仍在服务器。
- 操作步骤：1. 分别运行基线及新 parser。2. 执行 verify_layout_gain.py。3. 用原文查看器逐条检查新增52组对应页码/完整姓名/邮箱块；打开原有79组的差异清单。
- 预期结果：79/79保留，新增52，邮箱新增0，无错误归属；原有79差异为空。文件缺失必须显示不完整，不报已验证。
- 覆盖：I-1/I-2/I-3；真实原文→新规则→汇总。

### A-2: 反例与旧行为
- 前置条件：合成 PDF fixtures 分别为跨栏、两作者同名、一块两个邮箱、下一作者介入、旧共享标记。
- 操作步骤：1. 使用同一回放入口分析。2. 比较部署前后解析结果。3. 检查带旧版本的队列缓存与原留档 first。
- 预期结果：五个反例均没有新增独占归属；旧有效归属不减少；缓存/first版本仍为原值，新归档pdfLayout版本20261023、identityExtraction仍20261004；专家和准入表没有写入。
- 覆盖：I-1～I-4；must-NOT-change 全部回归。
