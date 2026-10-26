# 03 · 补齐有明确归属的邮箱与姓名

日期：2026-10-24。状态：待执行；本文件不证明修复已上线。前置及发布门禁见 [总计划](discovery-recheck-master.md)。基线 HEAD `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`，须保留工作区已有修改。

## 需求描述

结果：修正页底/行内标记、左侧字符截断、明确邮箱后姓名标签的漏绑定；去除正文at产生的伪邮箱。保持一邮箱唯一归属、共享标记歧义拒绑和既有缓存/身份凭证兼容；不开放邻近作者猜配、不承诺519全部恢复。

## 关键不变量

### I-1：来源唯一归属
- 规则：仅原文明确email(作者名/缩写)或完整作者区内唯一符号映射可产生身份proof；元数据里首字母唯一仅在原文明确标签存在时可用。共享/未知作者/损坏署名/相邻作者均不得推定拥有者。
- 适用：PdfAuthorContactLayout、SourceAuthorEmailResolver
- 违反后果：把其他作者论文/资格写错人。
- 来源：K-author-identity-needs-email-evidence

### I-2：布局不硬裁内容
- 规则：不以x<40或页底10%直接删除文字；页码/页眉噪声只用结构规则过滤。行内/下一行邮箱必须局限于同标记片段，换作者/空段/参考文献结束。
- 适用：layout.collect/toLines/markerSegments
- 违反后果：修复一处导致新的截名/跨作者串联。
- 来源：需求及下列源码审计

### I-3：规范化不制造邮箱
- 规则：括号at/dot维持支持；裸at/dot只在明确email/contact字段及完整邮箱形态中替换。available at www.nature.com不产生邮箱，不做nature黑名单。
- 适用：PlainTextEmailExtractor
- 违反后果：伪邮箱污染有效率。
- 来源：需求及下列源码审计

### I-4：版本独立
- 规则：提高PdfAuthorContactLayout.RULE_VERSION至20261024；保持DiscoveryIdentity.VERSION=20260925、EXTRACTION_VERSION=20261004。新实现版本进入捕获/分析parserVersion；兼容旧队列不会自动重抽取。
- 适用：解析/捕获/分析/队列消费者
- 违反后果：一次升级拒绝所有来源旧缓存。
- 来源：K-identity-cache-version-and-admission

## 现状审计

PdfAuthorContactLayout:78 y>90%跳过，:286 markerSegments首标记行首限制，:344 x<40跳过；SourceAuthorEmailResolver.resolvePdf合并文字claims+layoutcontacts，results发SOURCE_SHA256证明；PlainTextEmailExtractor:28与:69–74全局at替换。真实记录3/20、4/29、144和全批明确标签审计见报告。
本子计划不增业务存储写路径。既有parser输出由PdfEmailExtractor→Capture.first/latest、队列extraction_json及consumeOutcomeInternal读取；结果写入路径仍在原服务。SOURCE_SHA256证据→DiscoveryIdentity.validEvidence/proofFor→入库为跨模块交互。

完整检索回执、共享存储全集及交互矩阵：[证据目录](discovery-recheck-evidence/README.md)。以下列出的写入边界为准，不因引用共享审计而扩大文件范围。

## 实现方案

1. Layout删除硬x/y截断，保留真实字符与段边界；只放行完整署名区且拥有者集合唯一的符号，行内“机构句. *email”按符号分片而非全文最近作者；断行仅拼同contact段。（I-1/2）
2. SourceAuthorEmailResolver.textClaims增加明确`email (B. Bayerlein)`和`email (A.M.A.)`语法；缩写按已知作者所有given/family initials展开且唯一；同段两个邮箱各自标签隔离。仍由results统一冲突处理、产生proof；不得在审计脚本里补造proof。（I-1）
3. PlainTextEmailExtractor精确限制裸at/dot上下文；保留brace、mailto、显式混淆邮箱；别名“available”并不全局禁用。（I-3）
4. 同步RULE_VERSION并用程序生成PDF fixture测试页面坐标，不把版权全文加入仓库。回归负例：Favero邮箱不能绑Alessandro Achille；共享星号、多位同initial、首字母损坏、上一邮箱标签干扰。真实原文全量回放由10完成。（I-1/2/4）
5. 按2026-10-25用户授权，同步已有回归中钉死旧解析结果的断言：捕获layout版本改为当前RULE_VERSION；lun仅Mette B. Gaarde的唯一†页底邮箱可绑定，Lun邮箱仍无归属；debie中的Nour/Marwa按明确独立符号分别归属。消费测试只同步这些确证身份带来的计数和审计，不修改consumer/准入逻辑。保留所有真正的共享、未知、相邻、损坏署名和错误所有者负例；不得宽化为任意非空结果。（I-1/2/4）

## 变更文件清单

仅下列9个文件；新增授权仅为三个既有回归测试，不扩大生产实现范围。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` | 布局/标记/版本 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` | 显式邮箱作者标签 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractor.kt` | 限定混淆词替换 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperLayoutRegressionTest.kt` | 真实布局结构正负fixture |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | 标签与归属冲突 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractorTest.kt` | 伪邮箱及回归 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt` | 同步当前layout实现版本断言，保留first/identity/extraction不变量 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | 精确核对Mette唯一页底归属，保留Lun及真实歧义负例 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 同步确证身份恢复的消费计数/审计断言，消费与准入逻辑不改 |

## 验收标准

- I-1：明确唯一标签/marker产出正确姓名+validEvidence；所有共享/未识别所有者负例0绑定；不同邮箱之间不能串标签。
- I-2：坐标x=32的Bernd不能变ernd；y=96%页高的独占脚注恢复；参考文献同邮箱不得凭位置关联。
- I-3：available at www.nature.com输出0；Email: bayerlein at example.edu输出真实地址；`a(at)example.edu`仍输出；97是原批出现次数不是必达新增数量。
- I-4：缓存兼容/身份证明常量diff为0；已有来源及队列消费测试运行；新layout版本在first与analysis均可读。
- 新授权回归须通过：`DiscoveryPaperArchiveCaptureTest`、`PdfEmailExtractorTest`、`ExpertDiscoveryServiceTest`；真实队列/核验MySQL兼容测试以`-DmysqlIt=true`执行，不以默认关闭的skip代替证据。私有original294和主动关闭的在线Europe PMC烟测须明确记录，不冒称全批历史验证完成。

## 人工验收清单

### A-1：确切标签恢复
- 前置条件：测试环境加载原文短片段fixture：完整作者表、b@example.edu (B. Bayerlein)、唯一星号页底邮箱，及同initial两作者负例。
- 操作步骤：通过论文解析对照各运行一次。
- 预期结果：正例显示唯一姓名及证据，负例邮箱保留但姓名未解析；不多绑其他作者。
- 覆盖：I-1/2

### A-2：伪邮箱与左裁切
- 前置条件：加载坐标x32的Bernd fixture和含available at www.nature.com的正文。
- 操作步骤：运行解析对照并查看差异。
- 预期结果：Bernd完整；available@www.nature.com不存在；正常显式邮箱仍存在。
- 覆盖：I-2/3

### A-3：旧缓存回归
- 前置条件：测试队列已有identityRuleVersion=20261004的可消费PAPER/RECORD。
- 操作步骤：升级后消费既有队列，再新采集同结构论文。
- 预期结果：旧队列不因版本号被拒；新解析记录pdfLayout=20261024；旧first不覆写。
- 覆盖：I-4

人工验收开始后，才从本节导出同名前缀 `-acceptance.md`；机器通过不代表人工验收完成。

## 修正记录

- 2026-10-25，用户“授权 继续”：依据总计划I-M4，清单由6→9，仅增加上述三个既有测试；旧断言把本计划要求恢复的唯一归属当成无归属，或仍钉死旧layout版本。同步精确正例，不改历史fixture，不放宽身份边界，不扩生产行为。授权与提交身份见`../fast/discovery-recheck-master/ledger.md`的Amendments。
