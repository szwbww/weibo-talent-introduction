# 专家身份解析修复（阶段一）

状态：2026-09-25修订，待执行；本轮仅修改方案，未修改应用代码。数据修复/删除已另行完成。完整防复发范围见 [路线](deep-discovery-identity-roadmap.md)；本子计划独立修复解析器，不能替代准入、队列和发送门禁。

## 需求描述

论文中的邮箱必须归属于原文明确指向的作者；共用通讯说明不能把所有邮箱分配给第一位作者。

必须保持：邮箱线索保留；明确的一人多邮箱保留；已有PDF强匹配规则；原有发现任务、分页、来源开关与索引晋级流程。

范围外：全库批量改名、邮件补发、模板改写、UI、作者ID重命名。存量处置见阶段二。

## 关键不变量

### I-1：结构化节点也可能有歧义
- 规则：作者与邮箱身份必须唯一；多人引用同一corresp/author-notes节点，不构成节点内每个邮箱属于每位作者的证据。
- 适用：parseContribEmails、parseXrefCorrespondence、mergeResults。
- 后果：姓名、机构、ORCID整包错绑；生产同SHA解析器的原文重放已证实缺陷，数量以最终审计报告为准。
- 来源：K-author-identity-needs-email-evidence、此次原文重放。

### I-2：身份作为整体合并
- 规则：givenNames、familyNames、affiliation、orcidId来自同一唯一身份；不得分别firstOrNull拼接。相互冲突的可信身份不得选第一条。
- 适用：mergeResults。
- 后果：虚构混合身份并污染后续补全。
- 来源：此次审计。

### I-3：歧义不丢邮箱、不猜姓名
- 规则：文章作者自身地址/直接邮箱可构成证据，必须检查节点边界，禁止吸收嵌套作者、机构公共邮箱、编辑组或被引用文献的邮箱。通讯说明仅一人引用仍不足以无条件绑定：须为无冲突的专属联系方式，出现其他人名/机构联系人或不能划定范围时保持未知。共享说明中的完整姓名或原文显式括号缩写必须唯一匹配作者节点；缩写来自原文标签，禁止对邮箱字符串套首字母猜测。
- 无法唯一归属时保留邮箱，姓名、机构、ORCID全部为空；单一作者多个邮箱允许保留。
- 来源：K-author-identity-needs-email-evidence。

### I-4：保留既有路径行为
- 不修改PDF/Core已部署的verifiedAuthorFor，不改网络下载、任务消费和ES写入契约。
- 这是本子计划边界，不代表PDF/Core推断已经符合最终来源证明要求；后续证据准入阶段另行收紧。

### I-5：边界、顺序及真实同名
- 规则：`邮箱A（作者A）, 邮箱B（作者B）`中A的尾部标签不能成为B的前缀证据；多个rid按空白拆分，同一个id重复定义不任意选第一项。姓名相同不代表同一作者，作者节点/一致外部ID才用于候选区分。反转作者、脚注和抽取策略遍历顺序不能改变归属。
- 适用：全部解析策略与合并。冲突时保留邮箱，清空姓名、机构、ORCID、OpenAlex ID、institutionType整组身份字段；弱或无名线索不覆盖唯一明确身份。
- 后果：今天审计脚本也曾因此误判3条，不能直接移植旧脚本并用其输出自证正确。
- 来源：本次审计纠错。

### I-6：XML安全与输入兼容
- 规则：继续支持带DOCTYPE的JATS，但不解析外部实体、不加载远程DTD/Schema；坏XML保持既有异常契约，没邮箱返回空列表。
- 适用：parse及所有调用者。后果：修身份不应引入外部访问或改变失败重试语义。
- 来源：原有实现与测试。

## 现状审计

### 解析结果与发现写入
- JatsXmlEmailParser.kt:61直接contrib邮箱、:100通讯说明无名邮箱、:131文本邮箱、:194 xref说明归属，最终:302按邮箱合并。
- :235给引用者绑定目标节点内所有邮箱；:307-310逐字段取首个非空值，冲突被隐去。
- EuropePmcDataSource.extractAuthorEmails/fetch路径、PmcOaDataSource消费结果；OpenAlexDataSource亦会经Europe PMC分支使用它。
- ExpertDiscoveryService.buildProfile(:1825)复制姓名、机构、身份；toIndexMap(:1840)写RAW；promoteDiscoveredToCandidate(:1859)整份复制CANDIDATE；后续层级晋升可能复制APPLICATION。
- 持久队列discovery_paper_job.extraction_json也可能保存旧解析结果；修复上线不能宣称存量队列自动重解析。
- 早前事故审计记录的生产JatsXmlEmailParser.class与本地target/classes SHA256均为42d31cfd6fc58362d806598561853c1ae3cb7523e6a34a21d3c9c5dd65634993；本轮未重新核对线上部署。
- 两篇重放样本：PMC13241006中aamousavi@gmail.com被输出为Sajjad Abbasi；PMC13293455中latharadhakrishna@gmail.com被输出为Suvarna Hebbar。
- 下游联系人由InitialOutreachService、ManualInitialOutreachService保存expert.displayName；MailVariableService读ES姓名及联系人快照，技术ID过滤不能检验真实归属。（来源：K-dual-outreach-paths）

本计划直接改动的存储仅内存List<AuthorEmail>：四个parse策略写入、mergeResults读取并产出；所有身份字段及调用者上述列全。本阶段不修改DB/ES schema或持久化写入。现有DTO没有验证状态，故null仅表示无身份，无法在下游保证禁止发信；必须完成路线中的后续门禁。

## 实现方案

1. 修改JatsXmlEmailParser.kt：限定文章作者范围；建立唯一作者节点及说明id→引用者集合，重复id保守处理；rid按空白拆分。邮箱归属按I-1/I-3/I-5计算，先标定每个邮箱及对应姓名标签的文本跨度，再解释独立片段，不依赖作者遍历顺序；不能明确划界则输出无名线索。保留XML安全设置（I-6）。
2. 同文件修改mergeResults：内部候选保留作者节点身份/证据直到最终投影AuthorEmail；以完整身份候选做一致性检查。无名结果不覆盖明确身份；不同作者或同作者的相互矛盾身份冲突保留邮箱并清空身份，不逐字段拼接（I-2/I-5）。同名不同节点不可因字符串相同被消除冲突。
3. 修改JatsXmlEmailParserTest.kt：用短小的公开结构摘录/最小fixture覆盖共享说明、逗号分隔同人多邮箱、分号分隔不同作者、重复首字母、直接邮箱与歧义xref并存、多个rid、重复id、独占说明含其他姓名、编辑组、嵌套作者、引用文献邮箱、作者顺序反转、身份冲突。预期来自原文独立核对，不来自被测实现或旧审计程序（I-1至I-6）。
4. 同测试文件加入PMC13280751的imaginglu@hotmail.com→Jie Lu、wangwei37@buaa.edu.cn→Wei Wang；PMC13196294的jointwwg@163.com→Weiguo Wang，以及前后邮箱姓名标签隔离断言（I-5）。不得以统一输出null通过明确样本。

发布限制：旧版本已抽取但未消费的结果须定向重新抽取或隔离；本阶段不修改任务、库或发送配置。此限制由后续队列/准入子计划解决，不能部署本parser后立即恢复风险批次。

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
- I-5：上述3条真实纠错样本精确匹配；不同策略/作者/脚注排列归属相同；同名不同作者冲突输出身份全空。
- I-6：既有DOCTYPE/错误XML/无邮箱用例保持通过；额外实体测试确认本地文件内容及外部网络内容不被解析。
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

### A-4：三条审计误判回归
- 前置：测试环境关闭发送，导入PMC13280751、PMC13196294原文作为XML来源响应，不使用旧extraction_json。
- 操作：执行发现并按邮箱打开专家详情，对照原文通讯段。
- 预期：imaginglu@hotmail.com为Jie Lu；wangwei37@buaa.edu.cn为Wei Wang；jointwwg@163.com为Weiguo Wang；不存在Shibao Lu、Jie Lu、Sébastien Lustig顺位串绑。
- 覆盖：I-1、I-2、I-5；来源解析→资料显示。

### A-5：来源分支与安全输入
- 前置：隔离测试环境分别配置Europe PMC、PMC OA、OpenAlex转PMC来源响应为带DOCTYPE的已知正确XML；准备无邮箱及坏XML响应各一份，关闭邮件发送。
- 操作：分别执行一个来源批次，查看任务结果与新增档案；重复使用无邮箱和坏XML响应。
- 预期：三个来源对正确XML产生相同邮箱身份；无邮箱新增0；坏XML进入既有失败统计、没有创建专家；测试网络日志中远程DTD请求0。
- 覆盖：I-4、I-6；所有直接与间接解析消费者、原有失败语义。
