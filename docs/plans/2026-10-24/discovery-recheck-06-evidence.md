# 06 · 原文与学术证据的只读采集入口

日期：2026-10-24。状态：待执行；本文件不证明修复已上线。前置及发布门禁见 [总计划](discovery-recheck-master.md)。基线 HEAD `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`，须保留工作区已有修改。

## 需求描述

结果：解析对照和核验使用同一原文分析内核；能按专家来源定位论文并提取本人资格、研究方向补证，不夹带准入或三层资料写入。保持原文保护/删除、现有独立学术补全任务；不新增全文下载爬虫，不按姓名猜OpenAlex身份。

## 关键不变量

### I-1：计算与保存分开
- 规则：PaperBodyAnalyzer仅输入bytes/metadata/baseline/versions并返回结果；ArchiveAnalysisService仍显式commitAnalysis；核验只用只读compute，结果进本轮证据/轨迹，不覆盖analysis_json/first或ES。
- 适用：分析按钮与新核验采集服务
- 违反后果：对照污染业务基线。
- 来源：K-enrichment-write-three-layers

### I-2：论文定位及本人绑定
- 规则：优先原verification的sourceName/sourceItemKey对应archive record，再用externalIds的doi/pmid/pmcid规范ID验证；跨execution同paper可复用但必须声明来源run/bodySHA，重复SHA去重。本人必须由本轮可靠邮箱/姓名proof或已绑定作者ID关联，单纯同论文不能继承其他作者资格。
- 适用：ArchiveRepository lookup、PaperVerificationEvidenceService
- 违反后果：同名、相邻作者串证。
- 来源：需求及下列源码审计

### I-3：论文事实映射
- 规则：provider=PAPER，来源URL使用真实doi/sourceUrl，找不到可引用同域原文详情URL，不能伪造第三方主页；assertionSource=SELF且标明论文作者自述。completedPhD映射education role="PhD completed" completed=true；发表时job映射HISTORICAL employment；birthDate只来自明确日期。source excerpt/hash/页/段/年全部保留。
- 适用：Paper证据桥接→Rules/Gate
- 违反后果：旧职位当现职或自述假成机构认证。
- 来源：需求及下列源码审计

### I-4：证据模式严格
- 规则：REUSE不发任何外部HTTP；缺缓存记EVIDENCE_NOT_RETAINED，不能自动刷新；REFRESH可用被冻结的Apollo配置及已有ORCID/OpenAlex客户端。学术指标只供本轮判断/展示，不调用enrichProfiles或updateExpertAcademicFields的写回/旁路复评。
- 适用：证据服务及后续Worker
- 违反后果：付费意外调用或无法归因修复效果。
- 来源：需求及下列源码审计

### I-5：可信学术ID
- 规则：从IdentityVerification绑定OpenAlex author/ORCID且DiscoveryIdentity.allowed通过才查询，EMAIL-*不当ORCID；同ORCID返回多个author为歧义；无ID为不足而非不合格。
- 适用：新学术只读采集
- 违反后果：只靠姓名补错研究方向。
- 来源：需求及下列源码审计

## 现状审计

AnalysisService.analyze:19–119同时计算与commitAnalysis，必须拆纯函数；ArchiveService.analysisLease:56/commitAnalysis:77已有删除保护与revision/fileSet校验。ArchiveRepository.record/records/attempts目前不足以批量按来源定位，需要显式lookup而不是每人扫描4k JSON。
既有来源字段：ExpertDiscoveryService.buildExternalIds:3541保存doi/pmid/pmcid；sourceItemKey是envelope论文键。QualificationEvidenceParser.rebuild:98只替换可恢复ORCID端点，保留其他provider证据，可保留PAPER。
OpenAlexDataSource:538/:609是实际batch只读采集；ExpertDiscoveryService:3357–3382定义可信身份，:3459以后却会写三层+复评，不能调用该外壳。存储写者不新增；只读ArchiveRepository、ES快照、provider；派生输出由07写item/trace。

完整检索回执、共享存储全集及交互矩阵：[证据目录](discovery-recheck-evidence/README.md)。以下列出的写入边界为准，不因引用共享审计而扩大文件范围。

## 实现方案

1. 新PaperBodyAnalyzer抽出AnalysisService计算部分，包含版本/format/身份bindings/qualification/coverage/delta。AnalysisService负责lease/cache/commit；compute路径不含repository.save。保持02/04缓存兼容合同。（I-1）
2. ArchiveRepository新增限定paperKey/source或规范ID的批量查询；最先用现有唯一索引(execution/source/key)，历史候选必要时按source/key或JSON DOI查，服务端分页且不装载全库到每个人。相同SHA只解析一次；缺失原文不阻断已存在可靠身份的ORCID/Apollo流程。受现有页数/文本上限限制时标PARTIAL，不能报“全原文已分析”。（I-2）
3. PaperVerificationEvidenceService接收冻结profile+原来源refs、REUSE/REFRESH、evaluatedAt；在analysisLease中读取bytes并调用纯内核；输出archiveRecordId/attemptId/bodySHA/coverage、可靠绑定及资格facts。映射I-3，歧义只留诊断不提供正向事实；不得通过改profile.hash绕过Gate。（I-1/2/3）
4. 新DiscoveryRecheckAcademicEvidenceService对REFRESH复用OpenAlex batchEnrichByAuthorIds/Orcids与RequestKind.HISTORY_ENRICHMENT；实现可信ID选择必须与现有方法输入矩阵同测，不调用enrichProfiles；对REUSE只从冻结profile/本轮显式选定的历史证据读取。返回指标/researchFields/响应来源与失败类型；不写ES，不据此单独判准入。（I-4/5）
5. 对照输入应在准备阶段固定archiveRecordId、attemptId、bodyHash清单；运行时文件被删/改变只能报SOURCE_UNAVAILABLE，不切到新下载替代。不自动设置protected；明确保护不是永久锁，lease仅覆盖实际读取。（I-1/2/4）

## 变更文件清单

仅下列 9 个文件。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperBodyAnalyzer.kt` | 新增纯分析内核 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisService.kt` | 复用compute后显式保存 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt` | 批量来源查找 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PaperVerificationEvidenceService.kt` | 新增本人原文证据桥接 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckAcademicEvidenceService.kt` | 新增只读学术采集 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisServiceTest.kt` | 原接口回归/无意写 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepositoryIT.kt` | 索引定位/跨run/缺文件 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PaperVerificationEvidenceServiceTest.kt` | 新增绑定/时间/coverage测试 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckAcademicEvidenceServiceTest.kt` | 新增可信ID/REUSE零HTTP测试 |

## 验收标准

- I-1：相同原文新内核与原分析接口result一致；纯计算写库/ES0，按钮显式保存1次且first不变。
- I-2：本人唯一绑定成功；同名、共享邮箱、非本人简介不产资格；来源缺失与歧义独立码；超长原文不能伪FULL_SCAN。
- I-3：paper employment一律HISTORICAL，不能单独触发CURRENT professor；SELF原样保留；PhD student不能映射completed=true；真实URL/hash/page可追溯。
- I-4：REUSE的HTTPspy=0，缺失缓存返回不足；REFRESH不写资料、不调用旧复评。
- I-5：EMAIL-*、W开头、未绑定authorId、ORCID多候选拒绝绑定；正确identity receipt可调用批次client。

## 人工验收清单

### A-1：本人来源回溯
- 前置条件：测试专家保存doi/original source key，对应两次留存同一SHA论文；另一作者有教授简介。
- 操作步骤：用只读核验fixture采集该专家证据，再打开其来源原文。
- 预期结果：只引用本人事实；同SHA解析1次；另一作者教授不出现；record/attempt/SHA/页码可定位。
- 覆盖：I-2/3

### A-2：无副作用对照
- 前置条件：事先保存RAW/CANDIDATE/APPLICATION、admission、archive first/analysis摘要。
- 操作步骤：REUSE采集并对比摘要与HTTP计数；删除一份未保护文件后再读固定清单。
- 预期结果：业务摘要不变、外部请求0；已删文件明确SOURCE_UNAVAILABLE，未从网络补一份冒充原样本。
- 覆盖：I-1/4

### A-3：学术ID边界
- 前置条件：三个fixture：EMAIL-*主键无绑定ID；有效绑定A123；同ORCID映射两作者。
- 操作步骤：REFRESH运行学术采集。
- 预期结果：分别NO_TRUSTED_ACADEMIC_ID、返回A123指标、AMBIGUOUS_IDENTITY；不新增ES字段，不改既有独立补全任务。
- 覆盖：I-4/5

人工验收开始后，才从本节导出同名前缀 `-acceptance.md`；机器通过不代表人工验收完成。
