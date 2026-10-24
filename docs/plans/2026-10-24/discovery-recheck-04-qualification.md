# 04 · 明确资格原句与已绑定姓名/职称兼容

日期：2026-10-24。状态：待执行；本文件不证明修复已上线。前置及发布门禁见 [总计划](discovery-recheck-master.md)。基线 HEAD `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`，须保留工作区已有修改。

## 需求描述

结果：补解析已确认的简介句式，规则兼容已绑定ORCID的姓名顺序和完整复合职称。保持SELF可用、既有准入门槛/Apollo免费付费规则，不扩成不受约束的姓名乱序匹配；论文职称仅为发表时事实，不直接升级为当前任职。

## 关键不变量

### I-1：资格原句唯一主体
- 规则：只在作者简介段中已锁定唯一元数据作者时解析同段he/she；新作者/标题/参考文献立即截断。completed doctorate、student、assistant/associate/professor分别保存；原句否定/冲突不转正。
- 适用：PaperQualificationEvidenceExtractor
- 违反后果：把学生或相邻作者变博士/教授。
- 来源：需求及下列源码审计

### I-2：时间与出生信息
- 规则：论文任职与在读事实temporalScope=PUBLICATION_TIME；已获学位可作为完成事实，当前任职不能从旧论文推出。新增BIRTH_DATE只接受本人明确完整年月日，ISO日期合法且早于或等于已知完整发表日期；只有发表年时取该年12月31日上界；不得毕业年份推龄。
- 适用：PaperQualificationFact、后续证据桥接
- 违反后果：错误年龄/现职放行或否定专家。
- 来源：需求及下列源码审计

### I-3：姓名顺序必须有身份锚点
- 规则：ruleVersion>=3仅在evidence来自snapshot绑定ORCID person/other-names端点且身份hash/fact吻合时允许完整token保留的姓前名后旋转及唯一姓+首字母；不能在Apollo普通同名比较全局开放乱序/拼写模糊。
- 适用：Rules.evaluate的name冲突检查
- 违反后果：无关同名被已明确。
- 来源：需求及下列源码审计

### I-4：版本化复合职称
- 规则：ruleVersion>=3允许既有professor角色被斜线和学科后缀包装；保留emeritus/retired/student/assistant to等否定。旧ruleVersion0/1/2判定不变。新增run用3，续跑用冻结版本；评估时刻支持显式冻结。
- 适用：Rules、Worker、Registration、后续Gate
- 违反后果：旧已提交记录Gate复算不一致。
- 来源：K-verification-cached-evidence-replay

## 现状审计

PaperQualificationEvidenceExtractor:58–77要求BIOGRAPHY/独立姓名行，且姓名后直接is/received，已证实漏10文件13人事实；models中PaperQualificationFact已有PUBLICATION_TIME，不需要改当前任职定义。Rules:85–87先做sameName全证据冲突，:375–385不认姓前；:426–430 whole-string职称不认Lecturer/Assistant Professor in Marketing。原isProfessor集合包含assistant，不借本次修复悄悄改门槛。
规则结果写者Worker/Commit，读者AdmissionGate、QueryService、ReplayTool；新版本须同时被Worker/Registration接受，旧payload仍复算旧语义。qualification事实存analysis_json，不在此子计划写ES或admission。

完整检索回执、共享存储全集及交互矩阵：[证据目录](discovery-recheck-evidence/README.md)。以下列出的写入边界为准，不因引用共享审计而扩大文件范围。

## 实现方案

1. Extractor支持已核实的：姓名同行简介、IEEE member注释、同段He/She、received BSc/MSc/PhD、holds PhD、dr.philos、Since YYYY she has been Associate Professor；由唯一简介主体控制，先识别句级断言再判否定，不能一段有student就误删其他已完成学位。新增BIRTH_DATE枚举及原句取值，保留page/paragraph/year/SHA。（I-1/2）
2. RULE_VERSION独立常量写入analysis parserVersion中的qualification。缓存分析版本不得继续复用旧NOT_FOUND。DiscoveryPaperAnalysisService增加该key而不动first。（I-1/2）
3. Rules新增受ORCID端点约束的本地姓名冲突兼容，不改Apollo同名公用函数含义；完整token旋转/唯一姓和initial保守匹配，Maguluri/mangulur、缺少首字母Amina不能擅自通过。（I-3）
4. 复合职称仅分解到完整允许的职位短语，`assistant to professor`不能因切割通过；ruleVersion3才启用。Rules.evaluate增加可选evaluatedAt，计算年龄/trace用同一时刻，默认维持当前时钟；Worker支持0..3，Registration新run冻结3。后续07将时刻贯穿Gate及对照。（I-4）

## 变更文件清单

仅下列 9 个文件。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt` | 主体、句式与实现版本 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveModels.kt` | 新增BIRTH_DATE kind |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisService.kt` | 缓存资格解析版本 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt` | 锚定姓名、复合职称、评估时间 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | 支持冻结rule3 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` | 新run冻结rule3 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractorTest.kt` | 简介正负/日期/版本 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt` | 三反事实及旧版本 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | 新旧冻结版本兼容 |

## 验收标准

- I-1：13人正向事实对应结构fixture覆盖；相邻4个反例、博士在读/否定完成、标题冲突输出不得正向冒认。
- I-2：所有论文职位保持PUBLICATION_TIME；完整出生日期才能出BIRTH_DATE；出生年份或毕业年不推出日期。
- I-3：同一冻结证据下Friday Joseph Agbo/Agbo Friday Joseph仅移除错误NAME_CONFLICT；未绑定ORCID、其他姓名拼错仍不通过；R.Krishnamoorthy修好姓名仍UNKNOWN，不冒称准入。
- I-4：Brendan复合职称在rule3可MET但SEARCH_INCOMPLETE仍PENDING；rule2旧结果逐字段不变；显式冻结日期重复运行trace/age相同。

## 人工验收清单

### A-1：论文资格不是现职
- 前置条件：测试文件含某作者2019年获得PhD、2022年assistant professor，另段另一作者在读PhD。
- 操作步骤：运行原文解析并看资格卡。
- 预期结果：已完成学位、发表时助理教授、博士在读分列；后一人不显示已毕业；职称不标“当前”。
- 覆盖：I-1/2

### A-2：已绑定姓名顺序
- 前置条件：用审计5086的脱敏冻结结构构造rule2和rule3对照，同时一条未绑定ORCID的乱序姓名。
- 操作步骤：运行固定证据规则测试接口/脚本并读差异报告。
- 预期结果：已绑定样本NAME_CONFLICT消失；未绑定仍冲突；不宣称所有姓名变体可准入。
- 覆盖：I-3/4

### A-3：复合职位与旧记录
- 前置条件：准备当前岗位Lecturer/Assistant Professor in Marketing、assistant to professor、退休教授三条证据，另保留rule2历史项。
- 操作步骤：rule3计算并查询旧项。
- 预期结果：第一条qualification=MET；后两条不由教授条件通过；旧rule2记录结果不变。
- 覆盖：I-4

人工验收开始后，才从本节导出同名前缀 `-acceptance.md`；机器通过不代表人工验收完成。
