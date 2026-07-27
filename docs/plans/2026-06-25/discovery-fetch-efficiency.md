# 深度发现：抓取效率（EPMC 复用 search 邮箱 + 全文抽取批内并行）

> 计划日期：2026-06-25
> 目标：缩短单次发现耗时（线上 02:00→03:49 近 2h），减少冗余请求。

## 需求描述

**可观测产出**
1. EuropePMC：当 search 结果已带 `authorEmail` 时，直接复用，跳过一次全文 XML 抓取。
2. 单批次内各论文的全文/邮箱抽取（纯 I/O）并行执行，批次墙钟耗时下降；统计与 ES 写入仍单线程、结果不变。

**不可改变**
- 发现漏斗的最终产出（indexed/promoted/filtered、去重、邮箱校验结果）必须与串行实现逐条等价。
- 全局限额 `maxPapersPerRun/maxAuthorsPerRun`、单源 `sourceLimit`、取消、游标推进语义不变。
- 各源 `requestDelayMs` 礼貌延时语义保留（并行下用并发度控制总速率，不是无限并发）。

**不在本次范围**
- 抓取重试/HTML 兜底（见 `discovery-fetch-reliability.md`）。
- 跨源并行（各源仍串行，避免共享 stats 竞争与配额叠加）。
- 批次号/调度/失败原因（各有独立计划）。

---

## 关键不变量

### Invariant I-1: 仅纯抽取并行，状态变更串行
- 规则：可并行的只有 `AcademicDataSource.extractAuthorEmails(paper)`（纯函数式：仅 HTTP+解析，返回 `EmailExtractionOutcome`，不触碰 `SourceStats`/`DiscoveryStats`/ES）。所有计数自增、去重查询、ES 写入、晋升必须在**单一消费线程**顺序执行。
- 适用写路径：`ExpertDiscoveryService.processPaper` 拆分后的"消费"段；批次循环。
- 违反后果：`SourceStats` 为非线程安全可变字段，并发自增会丢计数/错乱。

### Invariant I-2: 限额与顺序在消费段判定
- 规则：`maxPapersPerRun/maxAuthorsPerRun/sourceLimit` 的判断与 break 在顺序消费段执行；并行抽取可能对接近上限的批多取几次全文（无害），但**索引/晋升数量绝不超过**串行实现。
- 适用写路径：批次消费循环。
- 违反后果：超额索引/越过取消点。

### Invariant I-3: EPMC 复用邮箱与全文抽取等价
- 规则：EPMC `extractAuthorEmails` 若 `paper.authors` 含非空 `email`，用其构造 `AuthorEmail`（带姓名/affiliation/orcid）并返回成功，跳过 XML；否则回退原全文抽取。复用所得邮箱仍要经下游 `emailValidationService`/去重/资格（不在本方法内短路）。
- 适用写路径：`EuropePmcDataSource.extractAuthorEmails`。
- 违反后果：绕过校验或字段缺失导致档案质量下降。

---

## 现状审计

### EPMC search 已带邮箱但被忽略
- `EuropePmcDataSource.parsePaperSearchResult`（:148-163）：`PaperAuthor.email = authorNode.path("authorEmail").asText(null)`（:161）—— search core 结果**已可能带邮箱**。
- `extractAuthorEmails`（:93-106）：无视 `paper.authors` 的 email，**总是** `extractEmailsFromFullText`（:101）→ 一次额外 HTTP。
- `PaperAuthor`（domain）含 `email/givenNames/familyNames/orcidId/affiliation/isCorresponding`，足以构造 `AuthorEmail`。

### 抽取是否纯函数（并行前提）
- `processPaper`（service:581-657）：`source.extractAuthorEmails(paper)`（:584）仅返回 outcome（纯 I/O）；其后才是 `sourceStats.*++`、`emailValidationService.validate`、`existsInRawIndexBy*`、`indexToRaw`、晋升 —— **全是共享状态/ES**。
- 各源 `extractAuthorEmails`（EPMC/PmcOa/Crossref/OpenAlex/Arxiv/Core）经审阅仅用入参 + restTemplate + 本地变量，**不写共享 stats** → 可安全并行。
- 串行驱动：`discover()`（:234-254）各源串行；`discoverFromSource`（:323-412）`do/while` 批内 `for (paper in batch.papers)`（:368）顺序 `processPaper`，每论文前 sleep（在各源方法内）。

### 限额/取消判定位置
- 批内每论文前：`isCancelled`、`totalPapers/totalAuthors/sourceLimit` 检查（:369-373）→ break。这些必须保留在消费段。

### 线程资源
- 无现成抽取线程池（`ManualOutreachConfig` 的 executor 属外联，不复用）。需新增受管、可停机的小型池。

### 交互点
- IP-1：批次循环（并行抽取 + 顺序消费）↔ `SourceStats`/`DiscoveryStats`（仅消费线程写）↔ ES 写入。
- IP-2：并发度 ↔ `requestDelayMs` 总速率（并发 N 个抽取≈把有效速率放大 N 倍，需对外礼貌；用小并发度如 4，并保留各源 sleep）。

---

## 实现方案

### 阶段 1：EPMC 复用 search 邮箱（I-3）—— 可独立交付、零并发风险

**Task 1.1** `EuropePmcDataSource.extractAuthorEmails`（:93-106）：进入全文抓取前，先扫描 `paper.authors`，收集 `email` 非空者构造 `AuthorEmail(email, givenNames, familyNames, isCorresponding, affiliation, orcidId)`；若非空则返回 `EmailExtractionOutcome(list, "SEARCH_FIELD", null, httpRequests = 0)`。否则维持现有全文路径（依赖 reliability 计划修复后的 FETCH_FAILED 区分，二者不冲突）。
- 不在本方法做校验/去重（交下游，保 I-3）。

### 阶段 2：批内并行抽取（I-1、I-2）

**Task 2.1** 新建 `config/DiscoveryExecutorConfig.kt`：受管 `ThreadPoolTaskExecutor`（如 `discoveryFetchExecutor`，corePoolSize=并发度、命名线程、`waitForTasksToCompleteOnShutdown=true`），并发度由 `ExpertDiscoveryProperties.fetchConcurrency`（默认 4）控制。

**Task 2.2** `ExpertDiscoveryProperties.kt`：增 `fetchConcurrency: Int = 4`（=1 时退化为串行，便于回滚/对照）。

**Task 2.3** `ExpertDiscoveryService.kt`：
- 把 `processPaper` 拆为：`extractOutcome(paper, source): EmailExtractionOutcome`（纯，可并行）与 `consumeOutcome(paper, outcome, source, stats, sourceStats)`（顺序，原 :591-656 逻辑）。
- 批次循环：先按消费前置条件（取消/限额）决定要处理的论文子集；对子集用 executor 并行 `extractOutcome`（保持与论文的配对与原始顺序，如 `map{ paper -> paper to future }` 再按序 `get()`）；随后**按原顺序**逐条 `consumeOutcome`，并在消费段内保留 `isCancelled`/限额 break（I-2）。
- `fetchConcurrency<=1` 时直接走原串行路径（不提交线程池）。

> 并行只包住 `extractOutcome`（含各源内的 sleep+HTTP）；`consumeOutcome` 全程单线程，`SourceStats`/`DiscoveryStats`/ES 不被并发触碰（I-1）。

---

## 变更文件清单

| # | 文件 | 改动 | 不变量 |
|---|------|------|--------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt` | search 邮箱复用 | I-3 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | processPaper 拆分 + 批内并行抽取 | I-1/I-2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryProperties.kt` | 加 fetchConcurrency | I-1 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt` | 新增受管抽取线程池 | I-1 |
| 5 | `src/main/resources/application.yml` | fetchConcurrency 默认值（可选） | I-1 |

文件数 5（≤10 ✅）；子系统 2（EPMC 复用 / 批内并行，≤2 ✅）；新增共享存储字段 0。

---

## 验收标准

- **I-3**：单测 EPMC——`paper.authors` 含 email → 返回该邮箱、`httpRequests==0`、不触发全文抓取（mock 全文调用断言未被调用）；authors 无 email → 回退全文路径。
- **I-1**：并发压力测试——`fetchConcurrency=4` 跑含上百论文的批，断言最终 `indexed/promoted/filtered/duplicates/emailsValid` 与 `fetchConcurrency=1` 串行运行**逐项相等**（确定性数据源 mock）。
- **I-2**：构造接近 `maxPapersPerRun` 的场景，断言索引数不超过串行；取消信号在消费段被及时响应（不再消费剩余 outcome）。
- **吞吐**：相同数据集，`fetchConcurrency=4` 较 `=1` 墙钟显著下降（记录耗时对比）。
- **回归**：`mvn clean package` + `mvn test` 通过（JDK 11）；线程池随上下文关闭（与调度计划的优雅停机一致）。

## 自检清单
- [x] 每个新行为有不变量（并行 I-1、限额 I-2、复用 I-3）
- [x] 现状审计确认 extractAuthorEmails 为纯函数（并行前提）
- [x] 文件数 5 ≤ 10；子系统 2 ≤ 2；新增共享字段 0
- [x] 任务标注不变量编号
- [x] 验收含"并行=串行逐项等价"硬校验
- [x] 文件逐一具名
- [x] 明确推迟跨源并行/重试
- [x] 提供 fetchConcurrency=1 回滚开关
- [x] 保存至 docs/plans/2026-06-25/
