# 专家身份解析修复（阶段一）

状态：待执行。依据：docs/audits/2026-09-25-expert-identity/README.md。此次只调查、提供方案，未修改应用代码或生产数据。

## 需求描述

论文中的邮箱必须归属于原文明确指向的作者；共用通讯说明不能把所有邮箱分配给第一位作者。

必须保持：邮箱线索保留；明确的一人多邮箱保留；已有PDF强匹配规则；原有发现任务、分页、来源开关与索引晋级流程。

范围外：全库批量改名、邮件补发、模板改写、UI、作者ID重命名。存量处置见阶段二。

## 关键不变量

### I-1：结构化节点也可能有歧义
- 规则：作者与邮箱身份必须唯一；多人引用同一corresp/author-notes节点，不构成节点内每个邮箱属于每位作者的证据。
- 适用：parseContribEmails、parseXrefCorrespondence、mergeResults。
- 后果：姓名、机构、ORCID整包错绑；已在生产同SHA解析器复现35条。
- 来源：K-author-identity-needs-email-evidence、此次原文重放。

### I-2：身份作为整体合并
- 规则：givenNames、familyNames、affiliation、orcidId来自同一唯一身份；不得分别firstOrNull拼接。相互冲突的可信身份不得选第一条。
- 适用：mergeResults。
- 后果：虚构混合身份并污染后续补全。
- 来源：此次审计。

### I-3：歧义不丢邮箱、不猜姓名
- 规则：贡献者节点中的直接邮箱为明确证据；单一作者独占的通讯说明为明确证据；多人共享说明中的完整姓名或原文显式括号缩写，必须在该说明引用者中唯一匹配。缩写来自原文标签，禁止对邮箱字符串套首字母猜测。
- 无法唯一归属时保留邮箱，姓名、机构、ORCID全部为空；单一作者多个邮箱允许保留。
- 来源：K-author-identity-needs-email-evidence。

### I-4：保留既有路径行为
- 不修改PDF/Core已部署的verifiedAuthorFor，不改网络下载、任务消费和ES写入契约。

## 现状审计

### 解析结果与发现写入
- JatsXmlEmailParser.kt:61直接contrib邮箱、:100通讯说明无名邮箱、:131文本邮箱、:194 xref说明归属，最终:302按邮箱合并。
- :235给引用者绑定目标节点内所有邮箱；:307-310逐字段取首个非空值，冲突被隐去。
- EuropePmcDataSource.extractAuthorEmails消费结果；OpenAlexDataSource亦会经Europe PMC分支使用它。
- ExpertDiscoveryService.buildProfile(:1825)复制姓名、机构、身份；toIndexMap(:1840)写RAW；promoteDiscoveredToCandidate(:1859)整份复制CANDIDATE；后续层级晋升可能复制APPLICATION。
- 持久队列discovery_paper_job.extraction_json也可能保存旧解析结果；修复上线不能宣称存量队列自动重解析。
- 生产JatsXmlEmailParser.class与本地target/classes SHA256均为42d31cfd6fc58362d806598561853c1ae3cb7523e6a34a21d3c9c5dd65634993。
- 两篇重放样本：PMC13241006中aamousavi@gmail.com被输出为Sajjad Abbasi；PMC13293455中latharadhakrishna@gmail.com被输出为Suvarna Hebbar。
- 下游联系人由InitialOutreachService、ManualInitialOutreachService保存expert.displayName；MailVariableService读ES姓名及联系人快照，技术ID过滤不能检验真实归属。（来源：K-dual-outreach-paths）

## 实现方案

1. 修改JatsXmlEmailParser.kt：先建立说明节点→引用作者集合；邮箱归属按I-1/I-3计算，不依赖作者遍历顺序。共享说明先按原文完整姓名/显式唯一缩写分组，无法唯一定位则输出无名线索。
2. 同文件修改mergeResults：以完整身份候选做一致性检查；无名结果不覆盖明确身份；不同作者冲突保留邮箱并清空身份，不逐字段拼接（I-2）。
3. 修改JatsXmlEmailParserTest.kt：用短小的公开结构摘录/最小fixture覆盖共享说明、逗号分隔同人多邮箱、分号分隔不同作者、重复首字母、直接邮箱与歧义xref并存、多个rid、独占说明、作者顺序反转、身份冲突。预期来自原文，不能来自被测实现（I-1至I-4）。
4. 发布前处理在途任务和旧extraction_json：旧版本已抽取但未消费的结果须定向重新抽取或隔离；禁止只部署新parser就恢复风险批次。该生产操作由阶段二执行。

## 变更文件清单

| 文件 | 动作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParser.kt | 修复歧义归属、整体身份合并 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParserTest.kt | 原文结构回归 |

## 验收标准

- I-1：原文重放不得把Ali Akbar Moosavi/Latha Thimmappa归给其他作者；调整作者顺序不改变结果。
- I-2：冲突姓名/ORCID不可取firstOrNull；任何结果的身份字段属于同一个作者候选。
- I-3：不明确结果保留邮箱且身份为空；SA对应的三个邮箱仍全部属于Sajjad Abbasi；相同缩写不得猜测。
- I-4：原有JatsXmlEmailParserTest、PdfEmailExtractorTest、CoreDataSourceTest及相关发现集成测试通过；用JDK11运行实际存在的对应测试类。
- 同步核实生产字节码SHA、两篇原文离线重放，再进行无发信的发现预览/测试环境入库验证。

## 人工验收清单

### A-1：共享通讯段
- 前置：测试环境使用PMC13241006与PMC13293455公开原文，关闭邮件发送。
- 操作：执行发现解析、打开新增档案详情。
- 预期：aamousavi@gmail.com姓名为Ali Akbar Moosavi；latharadhakrishna@gmail.com姓名为Latha Thimmappa；其他邮箱不得被删除。
- 覆盖：I-1、I-2、I-3；解析→入库→展示。

### A-2：正常多邮箱与歧义
- 前置：同人三个邮箱及相同缩写的两作者fixture。
- 操作：解析并查看输出。
- 预期：同人三邮箱保留；无法区分的邮箱仍存在但无姓名/ORCID/机构。
- 覆盖：I-3；必须保持的邮箱线索与别名行为。

### A-3：发现流程回归
- 前置：测试环境配置原有来源与晋级规则，含正常PDF样本。
- 操作：各执行一批PDF与XML发现，查看任务统计、原始/候选库。
- 预期：PDF既有正确作者不变；分页/来源开关不变；晋级仅由原有资格规则决定，申请库不被无条件创建。
- 覆盖：I-4；任务→RAW→CANDIDATE路径。
