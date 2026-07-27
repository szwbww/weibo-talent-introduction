# 深度发现：失败原因可观测性修复

> 计划日期：2026-06-25
> 目标：让"深度发现专家"任务的失败原因在日志/界面中真实、逐批可见。

## 需求描述

**可观测产出**
1. EuropePMC 全文抓取失败不再被误记为"无邮箱"，失败原因统计真实可信。
2. 任务记录里每个批次行能看到"本批为什么被拒"（逐批失败原因明细），不再只有一个 `拒绝` 数字。

**不可改变（必须保留）**
- 现有发现漏斗的业务行为：游标续跑、限额、熔断、去重、晋升逻辑不变。
- `batchProcessed / batchPassed / batchRejected` 三个已有字段的语义与取值不变。
- `task_progress_log` 已有列、`detailsJson.bySource[].failureReasons`（累计）继续保留。
- PmcOa 等已正确区分失败原因的源行为不变。

**不在本次范围（明确推迟）**
- EPMC 复用 search 结果里的 `authorEmail` 以省一次请求（性能优化，另开计划）。
- 全文/PDF 抓取的重试 + 退避（可靠性优化，另开计划）。
- PDF 落地页 HTML 兜底抽取（另开计划）。
- 串行 `requestDelayMs` 吞吐优化、scheduler 线程泄漏 / 当日幂等（属另一问题域，另开计划）。
- 批次号"按源各自从 0"导致的乱序去重问题（已在另一讨论中，另开计划）。

---

## 关键不变量

### Invariant I-1: 全文抓取失败必须与"无邮箱"区分
- 规则：当全文/XML **未取到**（HTTP 异常、超时、null）时，`failureReason` 必须是 `FULLTEXT_FETCH_FAILED`；当取到全文但解析为空时才是 `NO_EMAIL_IN_FULLTEXT`；解析抛异常时为 `XML_PARSE_FAILED`。三者互斥。
- 适用写路径：`EuropePmcDataSource.extractAuthorEmails`（本次修复对象）；`PmcOaDataSource.extractAuthorEmails`（已合规，作为参照，不改）。
- 违反后果：抓取失败被吞进"无邮箱"，且 `processPaper` 会错误执行 `fulltextObtained++`（service:606），漏斗虚高，运营无法判断"平台挂了"还是"论文真没邮箱"。

### Invariant I-2: 逐批失败原因必须是增量（delta），不得是累计值
- 规则：写入某批次的 `batchRejectReasons` 必须等于"该批结束计数 − 该批开始计数"，绝不能直接写 `sourceStats.failureReasons` 累计快照。
- 适用写路径：`ExpertDiscoveryService.discoverFromSource` 批次循环内的 `progressStore.update(...)`；`discoverFromOrcid` 同理。
- 违反后果：每行显示的都是到当前为止的累计，无法定位"这一批"出了什么问题，与 `batchProcessed/batchPassed/batchRejected` 的逐批语义不一致。

### Invariant I-3: 新增 JSON 列读写对称且 null 安全
- 规则：`TaskProgress.batchRejectReasons`（`Map<String,Int>?`）→ `TaskProgressLog.batchRejectReasonsJson`（`String?`）→ 持久化/还原必须成对；为空时写 `null`，不写 `"{}"` 抛错；反序列化失败不得使整条进度持久化失败。
- 适用写路径：`TaskProgressStore.persistProgressLog` 与 `restoreFromLog`。
- 违反后果：进度日志持久化整体失败（`persistProgressLog` 已 try/catch 吞错），会丢批次行。

---

## 现状审计

### 数据源：EuropePMC 全文抽取（`EuropePmcDataSource.kt`）
- `fetchFullTextXml`（:62-77）：任何异常 → `log.debug` + 返回 `null`。
- `extractEmailsFromFullText`（:79-91）：`fetchFullTextXml(...) ?: return emptyList()`（:84）；解析异常也返回 `emptyList()`（:87-90）。**→ null（抓取失败）、解析空、解析异常三种情况全部塌缩为 `emptyList`。**
- `extractAuthorEmails`（:93-106）：`emails.isEmpty()` → 一律返回 `NO_EMAIL_IN_FULLTEXT`（:102-104）。**违反 I-1。**
- 调用方：仅 `extractAuthorEmails`（:101）内部调用 `extractEmailsFromFullText`；`extractEmailsFromFullText`/`fetchFullTextXml` 为 public 但全仓无其他调用方（grep 确认）。`backfillRawEmailsAndPromote` 走 `tryGetEmailFromOrcid`，不受影响。
- 参照实现：`PmcOaDataSource.extractAuthorEmails`（:47-69）已正确区分 `FULLTEXT_FETCH_FAILED` / `NO_EMAIL_IN_FULLTEXT` / `XML_PARSE_FAILED`。

### 失败原因消费：`ExpertDiscoveryService.processPaper`（:581-657）
- `outcome.failureReason != null` → `sourceStats.failureReasons.merge(reason,1)`（:593-594）。**所有源的失败原因都进这张累计 map。**
- 空邮箱分支（:599-609）：`NO_PMC_ID/NO_DOI`→`papersSkippedNoId++`；`null/NO_EMAIL_IN_FULLTEXT/NO_EMAIL_IN_TEXT`→`noEmailInFulltext++` 且 `fulltextObtained++`；其余（含 `FULLTEXT_FETCH_FAILED/XML_PARSE_FAILED/PDF_*`）落 else，仅进 failureReasons map、不加 `fulltextObtained`。**→ 一旦 I-1 修复，EPMC 抓取失败自动落 else，漏斗虚高随之消除，processPaper 无需改动。**
- 邮箱级拒绝（不在 failureReasons map 内）：`emailsRejected`（:621）、`duplicates`（:625/:631）、`dedupErrors`（:626/:632）、`rawWriteFailed`（:644）。
- 注意：资格淘汰 `filtered`（:651）的论文是**先 `indexed++` 再 filtered**，计入 `batchPassed`，**不属于"拒绝"**，逐批原因里不计入。

### 批次进度写入：`ExpertDiscoveryService.discoverFromSource`（:308-435）
- 批次循环：`batchNumber++`（:361）→ 处理论文 → 算 `batchProcessed/batchPassed/batchRejected`（:385-387）→ `progressStore.update(... TaskProgress(...))`（:397-408）。
- `discoverFromOrcid`（:437+）结构类似，单独维护 batchNumber。
- 源完成 `log.info`（:417-432）打印漏斗，但**未打印 `failureReasons` map**。

### 进度持久化：`TaskProgressStore.kt`
- `TaskProgress` data class（:238-254）：含 `batchProcessed/batchPassed/batchRejected`，**无** reject 原因字段。
- `persistProgressLog`（:181-201）：逐字段映射到 `TaskProgressLog`；`detailsJson`/`errorsJson` 用 `objectMapper.writeValueAsString`。
- `restoreFromLog`（:203-235）：反向还原；整体 try/catch（:231）。
- 写路径：仅 `persistProgressLog` 一处写 `task_progress_log`（grep 确认无其他写入者）。

### 日志表：`task_progress_log`（V22）+ 领域类 `TaskProgressLog.kt`
- 列：`...batch_processed/batch_passed/batch_rejected, message, details_json, errors_json, created_at`。
- 领域类字段与列一一对应。最新迁移版本为 **V34**，下一个为 **V35**。

### 读路径 / 前端：`TaskProgressController` + `app.js`
- `getProgressLogs`（Controller:50-71）：`batchOnly=true` 时按 `batchNumber` groupBy 取最新、升序返回 `List<TaskProgressLog>`（原样 JSON，新增字段会自动序列化，**Controller 无需改**）。
- `renderBatchDetailRow`（app.js:825-849）：批次明细表头 6 列（批次/本批处理/通过/拒绝/累计进度/时间）。
- `renderBatchTable`（app.js:764-794）：渲染每行，6 个 `<td>`，**colspan=6**。
- `renderBySourceTable`（app.js:906-945）：已有"失败原因"列，读 `stats.failureReasons`（累计、按源），仅在运行中进度面板显示——本次不改，作为累计视图保留。

### 交互点
- IP-1：EPMC `extractAuthorEmails`（写 failureReason）↔ `processPaper`（:593-609 按 reason 分流计数）。修 I-1 后必须确认 EPMC 新增的 `FULLTEXT_FETCH_FAILED/XML_PARSE_FAILED` 落入 else 分支（已是现状，PmcOa 同款 reason 已在线运行，天然兼容）。
- IP-2：`discoverFromSource`（写 `batchRejectReasons`）↔ `TaskProgressStore.persist/restore`（序列化）↔ `app.js renderBatchTable`（读取展示）。

---

## 实现方案

### 阶段 1：修复失败原因标签（满足 I-1）—— 可独立交付

**Task 1.1**（I-1）改 `EuropePmcDataSource.extractAuthorEmails`（:93-106），对齐 PmcOa 写法：
- `pmcId == null` → `NO_PMC_ID`（不变）。
- 取全文：调用 `fetchFullTextXml(pmcId)`，`== null` → 返回 `FULLTEXT_FETCH_FAILED`（`httpRequests = 1`）。
- 取到后 `JatsXmlEmailParser.parse`：包 try/catch，异常 → `XML_PARSE_FAILED`；`emails.isEmpty()` → `NO_EMAIL_IN_FULLTEXT`；否则成功（`failureReason = null`）。
- 保留 public `fetchFullTextXml`（:62-77，仍吞异常返回 null，作为"抓取失败"的信号源）。`extractEmailsFromFullText`（:79-91）已无外部调用方：可保留不动，或让 `extractAuthorEmails` 直接内联 fetch+parse 以消除信息丢失（**推荐内联**，否则 null 与空仍无法区分）。
- 不改 `processPaper`：新 reason 自动落 else 分支，`fulltextObtained` 不再被错误自增（消除漏斗虚高）。

**验证点**：构造 fetch 返回 null / 解析抛异常 / 解析空 / 解析有邮箱 四种输入，断言 `failureReason` 分别为 `FULLTEXT_FETCH_FAILED` / `XML_PARSE_FAILED` / `NO_EMAIL_IN_FULLTEXT` / `null`。

### 阶段 2：逐批失败原因（满足 I-2、I-3）—— 依赖阶段 1 的 reason 词表

**Task 2.1**（I-2）`ExpertDiscoveryService`：新增私有 `snapshotRejectReasons(sourceStats): Map<String,Int>`，返回 `failureReasons` 拷贝 + 合成邮箱级键 `EMAIL_INVALID=emailsRejected`、`DUPLICATE=duplicates`、`DEDUP_ERROR=dedupErrors`、`RAW_WRITE_FAILED=rawWriteFailed`。在 `discoverFromSource` 批次循环：处理前快照 `before`，处理后快照 `after`，逐键算 `after-before>0` 的 delta map → 作为 `batchRejectReasons` 传入该批 `progressStore.update` 的 `TaskProgress`。`discoverFromOrcid` 同样处理。
> 说明：合成键为诊断量，论文级（failureReasons）与邮箱级计数口径不同，delta 之和不强求等于 `batchRejected`；用途是定位主因。在 `验收标准` 中体现该语义。

**Task 2.2**（I-2、I-3）`TaskProgressStore.kt`：
- `TaskProgress` 增字段 `val batchRejectReasons: Map<String, Int>? = null`。
- `persistProgressLog`：映射 `batchRejectReasonsJson = progress.batchRejectReasons?.let { objectMapper.writeValueAsString(it) }`。
- `restoreFromLog`：`batchRejectReasons = latestLog.batchRejectReasonsJson?.let { objectMapper.readValue<Map<String,Int>>(it) }`（沿用现有外层 try/catch，I-3 null 安全）。

**Task 2.3**（I-3）`TaskProgressLog.kt`：增 `val batchRejectReasonsJson: String? = null`。

**Task 2.4**（I-3）新建迁移 `V35__add_task_progress_batch_reject_reasons.sql`：
```sql
ALTER TABLE task_progress_log
    ADD COLUMN batch_reject_reasons_json TEXT NULL COMMENT '本批次拒绝原因明细(JSON: reason->count)';
```

**Task 2.5**（IP-2）`app.js`：
- `renderBatchDetailRow`（:825-849）表头增第 7 列"失败原因"，把两处 `colspan="6"` 改为 `colspan="7"`（含 `renderBatchTable` 空态 :766/:778）。
- `renderBatchTable`（:780-793）每行增 `<td>`：解析 `log.batchRejectReasonsJson`（JSON.parse，try/catch 容错），按 count 降序取前 3 拼 `reason:count`，空则 `-`；复用现有 `escapeHtml`，加 `max-width/ellipsis` 样式同 bySource 列（:926）。

**Task 2.6**（可观测增强，非阻断）`ExpertDiscoveryService` 源完成 `log.info`（:417-432）末尾追加 `failureReasons` map 输出，使应用日志（不依赖 UI）也能看到各源失败原因分布。

---

## 变更文件清单

| # | 文件 | 改动 | 阶段 |
|---|------|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt` | 区分 FETCH_FAILED/XML_PARSE_FAILED/NO_EMAIL（I-1） | 1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 逐批 reject delta（I-2）+ 源完成日志增 failureReasons | 2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStore.kt` | TaskProgress 增字段 + persist/restore（I-3） | 2 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskProgressLog.kt` | 增 batchRejectReasonsJson 字段（I-3） | 2 |
| 5 | `src/main/resources/db/migration/V35__add_task_progress_batch_reject_reasons.sql` | 新增列（I-3） | 2 |
| 6 | `src/main/resources/static/app.js` | 批次明细表增"失败原因"列（IP-2） | 2 |

文件数：6（≤10 ✅）。独立子系统：阶段1（EPMC 标签）/ 阶段2（逐批原因贯穿后端+前端）共 2（≤2 ✅）。新增共享存储字段：`task_progress_log` 仅 1 个（≤1 ✅）。

---

## 验收标准

- **I-1**：单测 `EuropePmcDataSource.extractAuthorEmails`——mock `fetchFullTextXml` 返回 null → `FULLTEXT_FETCH_FAILED`；返回非法字节使解析抛异常 → `XML_PARSE_FAILED`；返回合法但无邮箱 XML → `NO_EMAIL_IN_FULLTEXT`；返回含邮箱 XML → `failureReason == null` 且 emails 非空。
- **I-1 漏斗**：构造一篇 EPMC 论文使全文抓取失败，跑 `processPaper`，断言 `sourceStats.fulltextObtained` **不**自增、`failureReasons["FULLTEXT_FETCH_FAILED"] == 1`。
- **I-2**：单测/集成——连续两批，批 N 制造 a 个 NO_EMAIL + b 个 DUPLICATE，断言批 N 行的 `batchRejectReasons` 仅含该批 delta（不含批 N-1 的量）。
- **I-3**：`batchRejectReasons` 为非空 map 时 → DB `batch_reject_reasons_json` 为对应 JSON；为 null 时 → 列为 NULL；`restoreFromLog` 能往返还原；反序列化异常不影响其余字段还原（持久化不抛）。
- **IP-1**：跑一次发现（或集成测试），确认 `processPaper` 对 `FULLTEXT_FETCH_FAILED`/`XML_PARSE_FAILED` 不进入 `noEmailInFulltext` 分支。
- **IP-2 / 端到端**：界面任务记录展开某次执行 → 批次明细每行显示"失败原因"列；构造一批以失败为主的数据，确认 Top 原因正确呈现（如 `NO_EMAIL_IN_FULLTEXT:80, FULLTEXT_FETCH_FAILED:12`）。
- **回归**：`batchProcessed/batchPassed/batchRejected` 取值与改动前一致；`detailsJson.bySource.failureReasons` 累计视图不变；构建 `mvn clean package` + `mvn test` 通过（JDK 11）。

---

## 自检清单

- [x] `关键不变量` 存在，新字段/新行为均有不变量（I-1 标签、I-2 delta、I-3 JSON 列）
- [x] `现状审计` 列出 `task_progress_log` 全部写路径（仅 persistProgressLog，grep 确认）及 EPMC 调用方
- [x] 无任务引入未被不变量覆盖的写路径
- [x] 文件数 6 ≤ 10
- [x] 子系统数 2 ≤ 2
- [x] 每个任务标注其约束不变量编号
- [x] 验收标准每条不变量至少一项检查
- [x] 文件清单无"等/related files"，逐一具名
- [x] 已明确推迟 EPMC 复用邮箱、重试、HTML 兜底、吞吐、调度幂等、批次号乱序
- [x] 计划保存至 `docs/plans/2026-06-25/`
