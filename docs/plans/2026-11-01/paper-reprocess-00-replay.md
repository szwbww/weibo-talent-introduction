# 00 — 固定原文真值与生产解析器对照

状态：待执行；2026-11-01。仅计划，不表示已实施或发布。

## 需求描述

输出可复跑的原文对照清单：确认哪些漏绑可由本次代码修复，哪些缺原文或仍有歧义。不得把 325 当作已验证修复人数。

保持不变：①线上 ES、准入、人物登记、邮件、Apollo 请求计数均零写；②原始审计文件与首次解析快照不覆盖。

不做：修改业务解析规则、调用 Apollo、OCR、线上全库扫描、执行补录。单一子系统：离线验证工具。

## 关键不变量

### Invariant I-1: 证据单位
- Rule: 一行真值键为 recordId + bodySha256 + normalizedEmailHash + authorKey/规范全名 + page；不同邮箱不因同名合并。337 组合、333 保守分组、325 本批补绑机会是三个原审计口径，均需分别复算。
- Applies to: replay 工具、归并脚本、结果文件
- Violation consequence: 把论文、组合、人数和新增档案混算。
- 来源: K-author-identity-needs-email-evidence

### Invariant I-2: 原文重放
- Rule: 必须同一 PDF 原始 bytes、原 metadata.authors、原解析配置、原 first.outcome；PDFBox 生产路径跑 old/new。不得用人工排过行的文本证明 PDF 端到端可修复。缺任何输入为 SOURCE_MISSING，不记成功。
- Applies to: 全部输入与输出
- Violation consequence: 独立 PyMuPDF 命中被误当成生产修复证据。
- 来源: K-source-recall-needs-original-layout

### Invariant I-3: 零业务副作用
- Rule: 只读文件/有界 DB 与 ES 目标查询；不初始化调度器，不装配写服务，外部网络客户端禁用；报告落本地审计目录，原始 PDF 留在批准的安全位置且不进 git。
- Applies to: CLI、脚本
- Violation consequence: 测试触发准入或拖垮线上。
- 来源: K-paper-facts-need-owner-time-and-readonly-compute

## 现状审计

完整源码回执：[paper-reprocess-evidence/README.md](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/README.md)、[paper-reprocess-evidence/paths.json](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/paths.json)。共享存储全部生产读写路径及交互编号见主计划「现状审计」；本子计划只改变下述接缝，其他匹配路径为回归边界。当前本地基线 `b758eb0`，工作区存在先前改动；不得当作已核实的生产部署版本。
- `docs/audits/2026-11-01-discovery-30711-pdf/report.md:18–51` 明确说明 325 不等于新增，且尚未完成每条在线解析器根因重放；337 组合六类分布为 199/72/40/16/9/1。
- `PaperBodyAnalyzer.kt:28–46` 是可复用纯计算入口，校验 SHA，使用 metadata.authors，并分发至实际 PDF/XML/HTML/TEXT 解析器。
- `PdfEmailExtractor.kt:320–349`、`PdfAuthorContactLayout.kt:37–42` 限制读取页；审计逐页读取不等于生产读取范围。
- 原文存储 schema V159 与 baseline first/outcome、blobs SHA 见主计划 X-1；新工具不向这些存储写入。输出由 01/最终验证读取（X-0）。

## 实现方案

1. **固定输入（I-1/I-2/I-3）**：实现新增 `DiscoveryPaperIdentityReplayTool.kt`，输入只接受原文目录及 manifest；逐篇核对 SHA、大小、metadata hash。默认离线、单进程；不创建整应用 Spring context。允许按 recordId 分批执行，断点仅写自己的回执。
2. **逐条根因（I-1/I-2）**：新测试和脚本对审计 337 组合逐条比较。输出 recordId、attemptId、bodyHash、页码、作者 ID、邮箱指纹、作者元数据是否存在、旧解析姓名、期望姓名、实际 PDFBox 文本覆盖、命中的生产规则、具体文件方法与条件、状态。状态为 REPRODUCED/ALREADY_RECOVERED/SOURCE_MISSING/AMBIGUOUS/NOT_REPRODUCED；后两类不得自动进入修复真值。保留 47 个分栏未确认项为负面/待调查集合，不加入 325。
3. **档案盘点（I-1/I-3）**：只对已确认邮箱有界查询 RAW/CANDIDATE/APPLICATION，区分未建档/空名/完整但缺凭证/已完整/多命中冲突/人工或联系保护。输出 docId 与版本，不读取无关专家；禁止把全库姓名查找当作归人依据。
4. **进入 01 的门槛（I-2）**：三份报告必须列出可重现规则类别及明确失败样本，原文可获得的 337 组合均有行。只允许 01 改已由 production replay 证实的类别；发现越界格式/不同根因先修订计划，不让 agent 临场加启发式。本门槛不依赖预先承诺全部 325 修复。
5. **性能边界（I-3）**：优先本地/隔离环境；线上仅导出必要索引元数据，不在生产 JVM 全量重跑。用 `/usr/bin/time` 记录峰值 RSS、CPU、每文件耗时；后续并发默认 1，依据此实测决定能否提高，不在本计划承诺时长。

## 变更文件清单

| 文件（仓库根目录相对路径） | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayTool.kt` | 新增只读原文重放入口；无 Spring 业务写入上下文 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt` | 输入完整性、逐条输出、零副作用测试 |
| `scripts/discovery_admission_probe/reconcile_paper_identity_replay.py` | 归并独立审计与生产解析器逐条结果；禁止仅按姓名归人 |
| `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json` | 执行时生成：固定原文/元数据/版本/基线和逐条原因 |
| `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-results.csv` | 执行时生成：每一证据组合的旧、新、期望与原因 |
| `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-report.md` | 执行时生成：缺失源清单、准确召回差异和未修复清单 |

共 6 个文件。未列文件禁止扩改；新增问题先记录证据并修订范围。

## 验收标准

- I-1：`python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py` 复算旧审计；新 CSV 每组合唯一，缺源同样输出，不能只输出成功。
- I-2：真实原 PDF 在修改前至少重现每个拟修类别 1 个，且保存负例；报告逐条标出页覆盖/模式不识别/元数据不匹配等实际判定，不以正则猜根因。
- I-3：测试注入写服务/网络陷阱，触发次数均 0；审计前后 ES 和准入版本不变。
- X-0/X-1：01 读取同一 manifest，不许替换更容易的样本集。

## 人工验收清单

### A-1: 完整对照清单
- 前置条件：取得 #30711 原审计 337 组合及其可用原文；在隔离环境运行工具。
- 操作步骤：1. 导出旧解析报告；2. 打开 CSV 与摘要。
- 预期结果：CSV 337 个审计组合都有状态；缺源显示 SOURCE_MISSING，333/325 仅在旧口径栏显示；没有“325 已准入”结论。
- 覆盖：I-1/I-2，X-0/X-1

### A-2: 只读与输入不变
- 前置条件：用一份 PDF，另复制一份故意改动 1 byte；记录 ES/准入版本和审计文件 SHA。
- 操作步骤：1. 分别导入两份；2. 查看结果；3. 对比版本和 SHA。
- 预期结果：改动副本报 BODY_HASH_MISMATCH；原始审计 SHA、专家/准入版本均不变，外部请求数 0。
- 覆盖：I-2/I-3，保持不变①②


人工验收开始时才从此处导出同前缀 `-acceptance.md`；本次不生成衍生勾选文件。
