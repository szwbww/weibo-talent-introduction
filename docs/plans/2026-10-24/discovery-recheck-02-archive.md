# 02 · 修正原文格式识别和姓名关联展示

日期：2026-10-24。状态：待执行；本文件不证明修复已上线。前置及发布门禁见 [总计划](discovery-recheck-master.md)。基线 HEAD `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`，须保留工作区已有修改。

## 需求描述

结果：已有原文的真实姓名关联不再显示假0；“未分析”明确为尚未运行解析对照。PDF magic识别覆盖误报MIME的旧文件，合法DOCTYPE XML可安全解析。保持原文/first快照、保护/批量取消保护、删除锁及预览不自动核验；不批量修改历史blob记录，不自动给4k原文生成分析结果。

## 关键不变量

### I-1：首次提取不是再分析
- 规则：extractionSummary从first.outcome.emails实际结构按现有数组口径计数；resolved为givenNames和familyNames均非空的条目数，unresolved=emails-resolved。该显示不宣称identityEvidence有效或准入通过。analysis_json缺失只表示尚未运行解析对照。
- 适用：ArchiveService.row、discovery-papers.js
- 违反后果：把已提取姓名误判全部丢失。
- 来源：需求及下列源码审计

### I-2：字节决定PDF，XML安全一致
- 规则：%PDF-优先识别PDF，不能依赖HTTP MIME。XML允许声明DOCTYPE，但外部general/parameter entity、external DTD/schema、XInclude全部禁用。格式不合法保留ERROR/PARTIAL，不能伪装NOT_FOUND。
- 适用：保存、旧文件预览、分析及safeText
- 违反后果：14份旧PDF错路由，38份正常XML误拒；或引入XXE。
- 来源：需求及下列源码审计

### I-3：旧历史与删除语义不变
- 规则：不改SHA原文和first；分析缓存键包含新的format/qualification实现版本；预览读取不写analysis；全部读文件沿用lease及finally关闭。
- 适用：ArchiveService/AnalysisService
- 违反后果：历史覆盖、误删在读文件或旧缓存继续假0。
- 来源：需求及下列源码审计

## 样式契约

### S-1：只替换文本
基线DOM来自discovery-papers.js:89–90：`append(binding, 'span', '', '邮箱 ' + ... + ' / 姓名关联 ' + ...)`和`append(binding, 'span', 'dp-sub', delta(...))`。目标层级仍为td→span[data-dp=bindings]、span.dp-sub[data-dp=delta]；详情p.dp-muted保留。没有新增CSS，禁止改已有字体/布局/全局p；仅按实现方案列出的完整文案替换。17个版本化资源同键，数量不变。

## 现状审计

ArchiveCapture:81–116保存EmailExtractionOutcome而非resolved/unresolved；ArchiveService:343–348读不存在字段得到0；AnalysisService:64–68只按MIME进PDF；ArchiveService:242/:383也同时要求MIME，旧blob误标无法靠新保存修复。PaperQualificationEvidenceExtractor:108–123禁止所有DOCTYPE，而JatsXmlEmailParser:35–45禁外部实体但允许声明，构成重放行为不一致。
共享存储为V159四表和blob目录；只有现有ArchiveRepository/Service写者，新增格式工具纯函数；读为列表/详情/withContent/analysisLease/AnalysisService。交互：Capture→row；saveBody→preview→analysis。前端discovery-papers.js:78/90/258–265使用“未分析”；沿用已有`.dp-sub/.dp-muted`，只改文本。

完整检索回执、共享存储全集及交互矩阵：[证据目录](discovery-recheck-evidence/README.md)。以下列出的写入边界为准，不因引用共享审计而扩大文件范围。

## 实现方案

1. 新增 PaperContentFormat 纯字节识别方法，返回PDF/XML/HTML/TEXT；前5字节%PDF-即PDF；非PDF保留原kind判XML/HTML规则。不按扩展名猜文件。ArchiveService保存和withContent复用，旧media_type不改但响应Content-Type正确。（I-2/3）
2. ArchiveService.row用first.outcome.emails计算展示；缺/损坏outcome返回null使前端显示未知，不把异常变0；原数组重复条目口径不擅改去重。（I-1）
3. safeText与JATS同安全设置，DOCTYPE允许但禁止联网/本地文件实体；抓外部实体fixture确认不会输出secret。AnalysisService复用格式识别，并把format版本加入parserVersion缓存判断；analysisVersion升级2，first版本不变。（I-2/3）
4. discovery-papers.js把列表“未分析”改“未运行解析对照”，详情无analysis改“尚未运行解析对照；首次提取结果见下方”；保留邮箱/姓名关联列及现有dp-sub/dp-muted。index全部资源统一key=`20261024-paper-format`。（S-1）

## 变更文件清单

仅下列 10 个文件。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperContentFormat.kt` | 新增格式纯函数 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt` | 计数与保存/预览识别 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisService.kt` | 分析格式、缓存版本 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt` | DOCTYPE兼容且防XXE |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveServiceTest.kt` | 计数、旧MIME预览 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisServiceTest.kt` | 分析格式/缓存 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractorTest.kt` | DOCTYPE正负测试 |
| `src/main/resources/static/discovery-papers.js` | 说明文本 |
| `src/main/resources/static/index.html` | 统一版本键 |
| `src/test/js/discoveryPapers.test.js` | 新文本与旧行为 |

## 验收标准

- I-1：3条邮箱/2条完整姓名输出3/2/1；有姓名无proof仍显示姓名2而不宣称已核验；缺outcome显示未知。
- I-2：真实PDF+text/plain仍由PDF解析并preview为application/pdf；HTML伪application/pdf不能当PDF；合法DOCTYPE通过；外部实体URI本地/HTTP请求0且内容不泄漏。
- I-3：分析前后first JSON/SHA一致；缓存版本变更重新分析；只预览analysis_json不变；并发删除仍按lease阻挡。
- S-1：仅指定文本替换、无新CSS/class，既有保护/删除/下载JS用例全通过。

## 人工验收清单

### A-1：首次关联与对照区分
- 前置条件：用测试接口装载3条邮箱，其中2条姓名完整、analysis=null的原文记录。
- 操作步骤：打开论文原文列表再预览。
- 预期结果：显示“邮箱3 / 姓名关联2”“未运行解析对照”；预览后analysis仍为空。
- 覆盖：I-1/3，S-1

### A-2：旧PDF和XML
- 前置条件：测试库保存一份%PDF-文件但media_type=text/plain，以及带合法DOCTYPE的JATS；沿用原始SHA。
- 操作步骤：分别预览并点解析对照；检查first与下载SHA。
- 预期结果：PDF可预览、XML不再仅因DOCTYPE报PARSE_ERROR；两份first与SHA不变。
- 覆盖：I-2/3

### A-3：保护与删除回归
- 前置条件：两份原文一份已保护；测试解析器在读取时暂停。
- 操作步骤：尝试批量删除；取消保护后在解析完成再预览删除。
- 预期结果：保护与忙碌文件仍被排除；完成并取消保护后才可按既有确认删除。
- 覆盖：I-3，S-1

人工验收开始后，才从本节导出同名前缀 `-acceptance.md`；机器通过不代表人工验收完成。
