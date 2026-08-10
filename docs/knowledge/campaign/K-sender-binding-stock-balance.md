---
id: K-sender-binding-stock-balance
domain: campaign
created: 2026-08-10
last_used: 2026-08-10
hit_count: 1
source: create-p:sender-binding-03-assignment-stock-balance
---
经验：`SenderAccountAssignmentService.assignmentScore` 的存量均衡项必须用**占比**（[0,1]）而非裸计数，且使用**独立系数**（`STOCK_TOTAL_WEIGHT = 0.5` / `STOCK_SEGMENT_WEIGHT = 0.3`，companion 常量），**不得复用**批内分散的 `0.2` / `0.02` 系数。量纲原则：`baseScore ∈ [0, strategyWeight]`，存量项最大也是 `strategyWeight` 同量级；若把百千量级的绑定条数直接代入为批内小整数（0..批大小）设计的 0.2/0.02，存量项会绝对压倒 `baseScore`，退化为"永远选绑定数最少的账号"，`strategyWeight` 与剩余额度彻底失效。
推论：存量快照 `SenderBindingStock`（`totalShare` / `segmentShare` / `grandTotal` / `EMPTY`）每**批次**取一次（`loadBindingStock()`，round 循环之外），只读、不参与写决策（I-6）；空快照下两个存量项恒为 0，打分与引入存量前逐字相同（I-4 除零保护 `max(1, ...)` 语义由 `<= 0L` 短路实现）。打分公式恰 5 项：`baseScore - strategyWeight*0.2*sameSegmentCount - strategyWeight*0.02*totalAccountCount - strategyWeight*STOCK_TOTAL_WEIGHT*stockTotalShare - strategyWeight*STOCK_SEGMENT_WEIGHT*stockSegmentShare`；总量权重 > 国别权重（总量是主目标，国别分散是次目标）。
关联：存量 GROUP BY（`ExpertContactRepository.countBindingsByAccount` / `countBindingsByAccountAndCountry`）必须排除 `NULL` / 空串 / `SIMULATOR_NOOP`（G-3）；国别归一分两层——SQL 侧 `LOWER(TRIM(country))`，空串在 Kotlin 侧 `normalizeKey` 归一到 `"unknown"`（与 `distributionKey(expert)` 同一份归一逻辑，`distributionKey` 函数体即 `normalizeKey(expert.country)`）。存量项统计"批次开始前已存在的绑定"，批内新增由 `assignments` 计数覆盖，二者不重复计数（IP-1）。存量再平衡不做（M-8：只影响新增分配）；已绑定专家的解析结果不受打分影响（P2 `resolveForSend` 优先）。
