# 深度发现：批次号乱序修复

> 计划日期：2026-06-25
> 目标：任务记录里批次明细按真实时间顺序、不再相互覆盖。

## 需求描述

**可观测产出**
单次发现执行的批次明细表，批次号在多个数据源之间全局连续、唯一、单调递增，行不再因不同源复用同一批次号而被覆盖或时间错位。

**不可改变**
- 发现漏斗业务：搜索/全文/去重/晋升/限额/熔断/游标逻辑不变。
- `batchProcessed/batchPassed/batchRejected/processedCount/totalCount` 取值不变。
- 单源日志里给人看的"[源] 批次 N"可读性（保留每源本地计数用于日志文案）。
- `task_progress_log` 表结构不变（本计划不加列、不加迁移）。

**不在本次范围**
- 在批次表新增"源"列（需新增列+迁移，另做；本计划用全局连续号即可解决排序）。
- 失败原因可观测性（见 `discovery-failure-reason-logging.md`）。

---

## 关键不变量

### Invariant I-1: 批次号在单次 execution 内全局唯一且单调递增
- 规则：每持久化一条 `RUNNING` 批次进度（`batchNumber > 0`），其 `batchNumber` 必须取自**执行级全局序号**（跨所有源连续自增），不得按源各自从 1 计。
- 适用写路径：`ExpertDiscoveryService.discoverFromSource` 与 `discoverFromOrcid` 中 `progressStore.update(... TaskProgress(batchNumber=...))`。
- 违反后果：`TaskProgressController.getProgressLogs(batchOnly)` 按 `batchNumber` groupBy 取最新会跨源覆盖；`app.js renderBatchTable` 同样 groupBy 覆盖 → 行错位/丢失（即线上 4722 现象）。

### Invariant I-2: 全局序号与本地序号职责分离
- 规则：持久化的 `TaskProgress.batchNumber` = 全局序号；单源 `log.info("[源] 批次 N ...")` 的 N 可继续用每源本地计数。两者不得混用同一变量。
- 适用写路径：`discoverFromSource`（:361 本地 `batchNumber++`，:389 日志，:399 持久化）、`discoverFromOrcid`。
- 违反后果：要么日志可读性下降，要么持久化号又退回按源计。

---

## 现状审计

### 写路径：批次号产生
- `ExpertDiscoveryService.discoverFromSource`（service:308-435）：本地 `var batchNumber = 0`（:318），每批 `batchNumber++`（:361），用于日志文案（:389）**和**持久化 `TaskProgress(batchNumber = batchNumber, ...)`（:399）。
- `ExpertDiscoveryService.discoverFromOrcid`（:437+）：独立 `var batchNumber = 0`（:449），同样既日志又持久化。
- `discover`（:234-254）顺序遍历各源后再跑 ORCID —— **各源串行**，所以全局序号只需简单自增、无并发问题。
- 初始/结束态用 `batchNumber = 0`（:211 等）或 `-1`（:282/:294），均被下游 `batchNumber > 0` 过滤，不受影响。

### 持久化与还原
- `TaskProgressStore.persistProgressLog`（store:181-201）原样落 `batchNumber`。
- `restoreFromLog`（:203-235）读回 `batchNumber` 仅用于展示，无业务依赖（grep 确认无逻辑按 batchNumber 取值判断）。

### 读路径（受影响）
- `TaskProgressController.getProgressLogs`（controller:50-71）：`batchOnly=true` → `filter{batchNumber>0}.groupBy{batchNumber}.map{last()}.sortedBy{batchNumber}`。
- `app.js renderBatchTable`（:764-794）：`latestByBatch = Map(batchNumber -> log)` 后按号排序。
- 两者都**仅**依赖 `batchNumber` 的唯一性与有序性 —— 全局唯一递增即可同时修好两端，无需改读路径代码。

### 交互点
- IP-1：`discoverFromSource/Orcid`（写全局号）↔ Controller groupBy ↔ 前端 groupBy。全局唯一是三者共同的正确性前提。

---

## 实现方案

### Task 1（I-1、I-2）`DiscoveryStats.kt`
新增执行级序号：
```kotlin
var globalBatchSeq: Int = 0
fun nextBatchSeq(): Int = ++globalBatchSeq
```
（`DiscoveryStats` 每次 `discover()` 新建一个实例，天然 execution 作用域；各源串行调用，无需原子类。）

### Task 2（I-1、I-2）`ExpertDiscoveryService.kt`
- `discoverFromSource`：保留本地 `batchNumber` 仅用于 `log.info`（:389）文案；把 `progressStore.update` 里的 `TaskProgress(batchNumber = batchNumber, ...)`（:399）改为 `batchNumber = stats.nextBatchSeq()`。每批只调用一次 `nextBatchSeq()`，并可将该值放入本地 val 供同一次 update 复用。
- `discoverFromOrcid`：同样改持久化处为 `stats.nextBatchSeq()`，本地号留作日志。
- 不改 `discover()` 主流程、不改初始/结束态（仍 0 / -1）。

---

## 变更文件清单

| # | 文件 | 改动 | 不变量 |
|---|------|------|--------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryStats.kt` | 加 `globalBatchSeq` + `nextBatchSeq()` | I-1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 持久化批次号改用全局序号（两处） | I-1/I-2 |

文件数 2（≤10 ✅）；子系统 1；新增共享存储字段 0。

---

## 验收标准

- **I-1**：集成测试——启用 ≥2 个源跑一次发现，查 `task_progress_log` 中该 execution 的 `batchNumber>0` 行，断言 `batchNumber` 为 1..K 连续无重复，且 `created_at` 随 `batchNumber` 单调不减。
- **I-1 端到端**：前端展开该执行，批次明细行数 = 实际批次总数（不再被覆盖），时间列单调。
- **I-2**：检查日志输出 `[EUROPE_PMC] 批次 1..` 与 `[OPENALEX] 批次 1..` 每源本地号仍从 1 开始（文案不变），而 DB 持久化号全局连续。
- **回归**：`batchProcessed/batchPassed/batchRejected/processedCount` 与改前一致；`mvn clean package` + `mvn test` 通过（JDK 11）。

## 自检清单
- [x] 关键不变量含每个新行为
- [x] 现状审计列全写/读路径（grep 确认无按 batchNumber 的业务分支）
- [x] 文件数 2 ≤ 10；子系统 1 ≤ 2；新增共享字段 0 ≤ 1
- [x] 任务标注不变量编号
- [x] 验收每条不变量有检查
- [x] 文件逐一具名
- [x] 明确推迟"源列"与失败原因
- [x] 保存至 docs/plans/2026-06-25/
