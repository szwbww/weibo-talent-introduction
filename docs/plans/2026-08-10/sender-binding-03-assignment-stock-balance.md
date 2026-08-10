# P3：分发打分计入存量绑定（决策 ③ 强一致均衡）

> 依赖 P1（绑定列）与 P2（绑定优先解析）。
> 主计划（跨计划约束 M-1..M-8、全局不变量 G-1..G-3）见 [00-main-plan-sender-binding.md](00-main-plan-sender-binding.md)。

## 需求描述

**Observable outcome**

1. 给**新专家**选号时，打分不再只看"本批次内已分配了多少"，
   还要看"该账号名下已绑定多少存量专家"以及"同国别存量多少"。
   结果是长期看各账号的绑定存量趋于按 `strategyWeight` 分布，
   而不是每批重新洗牌、跨批次记忆为零。
2. 存量快照每批次只查一次，不产生 per-expert 查询。

**What must NOT change**

- 已绑定专家的账号解析结果（P2 已收口，本计划只影响**新专家**的首次选号）。
- 批内分散逻辑（`sameSegmentCount` / `totalAccountCount` 两项惩罚）的系数与语义。
- 候选账号过滤谓词：`enabled && 未超额 && 非模拟器 && !autoSendPaused`
  （`SenderAccountAssignmentService.kt:20-27`），本计划**不动过滤，只动打分**。
- `ignoreWarmup` 的逐层下传语义（K-operator-send-quota-paths 补充段落）。
- `distributionKey` 的取值口径：`expert.country` 归一小写、空则 `"unknown"`
  （`SenderAccountAssignmentService.kt:56-61`）。

**Out of scope**

- 换绑接口与标记 → P4
- 前端 → P5
- 存量均衡的"再平衡"（把已绑定专家迁到别的账号）——本计划只影响新增分配，
  不做存量迁移。
- 让 `remainingDailyCapacity` / `todayTotalCapacity` 等容量统计感知绑定。

## 关键不变量

### I-1: 存量快照每批次取一次
- Rule: 存量绑定分布只允许在批次开始处查询一次，结果以不可变快照形式传入
  `selectAccount(...)`。禁止在 `selectAccount` 内部或 `assignmentScore` 内部查库。
- Applies to: `SenderAccountAssignmentService.selectAccount`、
  `InitialOutreachService.sendInitialBatch`、
  `ManualInitialOutreachService` 首封轮与材料提醒轮的 `assignments` 初始化处。
- Violation consequence: `selectAccount` 在批量循环里每个专家调用一次
  （`InitialOutreachService.kt:48` 在 `experts.forEachIndexed` 内），
  内部查库即 N+1，千人批量会打出上千次 GROUP BY 全表扫描。

### I-2: 存量项必须归一化到 [0,1]，不得与批内计数共用系数
- Rule: 存量惩罚项以**占比**（该账号绑定数 / 总绑定数）参与打分，
  乘以独立系数 `STOCK_TOTAL_WEIGHT` / `STOCK_SEGMENT_WEIGHT`；
  禁止直接把绑定条数代入现有的 `0.2` / `0.02` 系数。
- Applies to: `SenderAccountAssignmentService.assignmentScore`。
- Violation consequence: 现有系数是为**批内小整数**（0..N，N 为批大小）设计的；
  存量是百千量级，直接代入会让存量项绝对压倒 `baseScore`，
  退化为"永远选绑定数最少的那个账号"，`strategyWeight` 与剩余额度彻底失效。

### I-3: 存量与批内两个维度都要计，且各自独立
- Rule: 打分公式为
  ```
  score = strategyWeight * remainingRatio
        - strategyWeight * 0.2  * sameSegmentCount        // 批内同国别（既有，不动）
        - strategyWeight * 0.02 * totalAccountCount       // 批内总量（既有，不动）
        - strategyWeight * STOCK_TOTAL_WEIGHT   * stockTotalShare(account)
        - strategyWeight * STOCK_SEGMENT_WEIGHT * stockSegmentShare(account, key)
  ```
  其中 `stockTotalShare = stock.total(code) / max(1, stock.grandTotal)`，
  `stockSegmentShare = stock.segment(code, key) / max(1, stock.segmentTotal(key))`。
- Applies to: `SenderAccountAssignmentService.assignmentScore`。
- Violation consequence: 只计总量会破坏国别分散（`distributionKey` 是 country，
  见 `:56-61`）；只计国别会让小国别噪声主导。

### I-4: 空快照必须等价于当前行为
- Rule: 当 `stock.grandTotal == 0`（全新库/无任何绑定）时，两个存量项恒为 0，
  打分结果与本计划实施前**逐字相同**。
- Applies to: `assignmentScore` 的除零保护（`max(1, ...)` 且分子为 0）。
- Violation consequence: 空库场景下打分漂移，`SenderAccountAssignmentServiceTest`
  的既有用例会失败，且首次上线时账号分布突变。

### I-5: 存量统计排除模拟器与空绑定（全局 G-2/G-3）
- Rule: 存量 GROUP BY 必须带 `WHERE bound_sender_account_code IS NOT NULL
  AND bound_sender_account_code <> 'SIMULATOR_NOOP'`。
- Applies to: `ExpertContactRepository` 新增的两个统计查询。
- Violation consequence: `SIMULATOR_NOOP` 计入 `grandTotal` 会稀释所有真实账号的占比，
  使存量项整体失真。

### I-6: 存量快照是只读派生数据，不参与任何写决策
- Rule: 快照只用于打分；不得据此修改 `bound_sender_account_code`、
  `todaySentCount`、`autoSendPaused` 或触发任何迁移。
- Applies to: 本计划全部改动。
- Violation consequence: 打分逻辑变成隐式的存量迁移器，绕过 P4 的换绑审计。

## 现状审计

### `SenderAccountAssignmentService`（`mail/service/SenderAccountAssignmentService.kt`，全文 66 行）

- `selectAccount(expert, currentBatchAssignments = emptyList(), ignoreWarmup = false):16-32`
  - 候选过滤 `:22-27`：`findAllByEnabledTrue()` +
    `todaySentCount < warmup.effectiveDailyLimit(it, ignoreWarmup)` +
    `accountCode != SIMULATOR_NOOP` + `!autoSendPaused`
  - 取最大 `:28-31`：`maxWithOrNull(compareBy { assignmentScore(...) }.thenBy { it.id ?: 0L })`
  - 无候选 → `NoAvailableSenderAccountException(:31)`
- `assignmentScore(account, distributionKey, assignments, ignoreWarmup):34-52`
  - `baseScore = strategyWeight * (effectiveLimit - todaySentCount) / effectiveLimit`
  - `sameSegmentCount` = `assignments.count { accountCode 相同 && distributionKey 相同 }`
  - `totalAccountCount` = `assignments.count { accountCode 相同 }`
  - `return baseScore - strategyWeight*0.2*sameSegmentCount - strategyWeight*0.02*totalAccountCount`
  - **全部输入均为批内状态，无任何持久化视图** ← 本计划要补的正是这一点
- `distributionKey(expert):54-61` = `expert.country` 小写去空，空则 `"unknown"`
- `SenderExpertAssignment(accountCode, expertId, distributionKey):63`

- **调用方全集**（`grep -rn "senderAccountAssignmentService.selectAccount"`）
  1. `InitialOutreachService.kt:48` —— 在 `experts.forEachIndexed`（`:36`）循环内，
     `assignments` 是 `:32` 的 `mutableListOf`，批开始时为空。
  2. `ManualInitialOutreachService.kt:272` —— 材料提醒轮（P2 后仅作无绑定兜底）
  3. `ManualInitialOutreachService.kt:552` —— 首封轮（P2 后仅作无绑定分支）

  三处都在循环内逐专家调用 → I-1 的 N+1 风险来源。

- **既有测试** `src/test/kotlin/.../mail/service/SenderAccountAssignmentServiceTest.kt`
  —— 全部基于"空存量"场景，I-4 保证它们零改动通过。

### MySQL `expert_contact`（P1 已加列）

- `bound_sender_account_code VARCHAR(64) NULL`，索引 `idx_expert_contact_bound_sender`。
- `country VARCHAR(128) NULL`，索引 `idx_expert_contact_country`（`V48`）。
- 存量分国别统计需要 `(bound_sender_account_code, country)` 两列组合；
  现有两个单列索引足以支撑 GROUP BY（数据量级为万级），**本计划不加复合索引**
  （加索引属于后续性能优化，且会与 P4 的迁移撞版本号）。
- `ExpertContactRepository`（`campaign/repository/ExpertContactRepository.kt`）
  已有 `@Query` 用法示例（`:41-63` 的 `findFilteredContacts`）与
  `@Modifying @Query`（`:65-67` 的 `updateCountryById`），投影类可放同文件。

### Interaction points

- **IP-1**：P1/P2 写入 `bound_sender_account_code`（写）× 本计划 GROUP BY（读）——
  快照在批次开始时取，批内新建的绑定不会反映到快照里；
  这部分由既有的 `assignments`（批内计数）补齐，两者**不重复计数**：
  快照统计的是"批次开始前已存在的绑定"，`assignments` 统计的是"本批新分配的"。
  执行时必须确认 `assignments.add(...)` 的位置不变，否则会出现双计。
- **IP-2**：`ManualInitialOutreachService` 材料提醒轮在 P2 之后
  `selectAccount` 只在无绑定兜底时调用 —— 该轮的 `assignments`（`:338-341`）
  对**所有**专家都在累加（包括走绑定的），这会让兜底选号误以为批内已分配很多。
  本计划需确认：这是**期望行为**（绑定专家确实占用了该账号的当日发送量），
  保持不变，仅在计划中记录该语义。

## 实现方案

### 阶段 1 — 存量快照查询

**T1.1 `ExpertContactRepository.kt` 新增两个统计查询**（遵 I-5）

在 `updateCountryById`（`:65-67`）之后追加：

```kotlin
    @Query("""
        SELECT bound_sender_account_code AS account_code, COUNT(*) AS bound_count
          FROM expert_contact
         WHERE bound_sender_account_code IS NOT NULL
           AND bound_sender_account_code <> ''
           AND bound_sender_account_code <> 'SIMULATOR_NOOP'
         GROUP BY bound_sender_account_code
    """)
    fun countBindingsByAccount(): List<AccountBindingCount>

    @Query("""
        SELECT bound_sender_account_code AS account_code,
               COALESCE(LOWER(TRIM(country)), '') AS distribution_key,
               COUNT(*) AS bound_count
          FROM expert_contact
         WHERE bound_sender_account_code IS NOT NULL
           AND bound_sender_account_code <> ''
           AND bound_sender_account_code <> 'SIMULATOR_NOOP'
         GROUP BY bound_sender_account_code, COALESCE(LOWER(TRIM(country)), '')
    """)
    fun countBindingsByAccountAndCountry(): List<AccountCountryBindingCount>
```

同文件末尾加两个投影：

```kotlin
data class AccountBindingCount(val accountCode: String, val boundCount: Long)
data class AccountCountryBindingCount(
    val accountCode: String,
    val distributionKey: String?,
    val boundCount: Long
)
```

> SQL 侧只做 `LOWER(TRIM(country))`，空串归一到 `"unknown"` 在 Kotlin 侧完成，
> 以保证与 `SenderAccountAssignmentService.distributionKey(:54-61)` **同一份归一逻辑**，
> 避免两处漂移（该方法是 `distributionKey` 的唯一定义点）。

### 阶段 2 — 快照类型与打分

**T2.1 `SenderAccountAssignmentService.kt` 新增快照类型**

文件末尾（`SenderExpertAssignment` 之后）加：

```kotlin
/**
 * 批次开始时刻的存量绑定分布快照（I-1：每批次取一次，只读，I-6：不参与写决策）。
 * 空快照（EMPTY）下所有存量项恒为 0，打分与引入存量前逐字相同（I-4）。
 */
data class SenderBindingStock(
    private val totalByAccount: Map<String, Long>,
    private val segmentByAccount: Map<Pair<String, String>, Long>,
    private val segmentTotals: Map<String, Long>
) {
    val grandTotal: Long = totalByAccount.values.sum()

    fun totalShare(accountCode: String): Double =
        if (grandTotal <= 0L) 0.0
        else (totalByAccount[accountCode] ?: 0L).toDouble() / grandTotal

    fun segmentShare(accountCode: String, distributionKey: String): Double {
        val segTotal = segmentTotals[distributionKey] ?: 0L
        if (segTotal <= 0L) return 0.0
        return (segmentByAccount[accountCode to distributionKey] ?: 0L).toDouble() / segTotal
    }

    companion object {
        val EMPTY = SenderBindingStock(emptyMap(), emptyMap(), emptyMap())
    }
}
```

**T2.2 新增快照装配方法**（遵 I-1/I-5，归一逻辑复用 `distributionKey` 口径）

在 `SenderAccountAssignmentService` 内新增（需注入 `expertContactRepository`）：

```kotlin
    /** 批次开始时调用一次（I-1）。 */
    fun loadBindingStock(): SenderBindingStock {
        val totals = expertContactRepository.countBindingsByAccount()
            .associate { it.accountCode to it.boundCount }
        val segments = expertContactRepository.countBindingsByAccountAndCountry()
            .associate { (it.accountCode to normalizeKey(it.distributionKey)) to it.boundCount }
        val segmentTotals = segments.entries
            .groupBy { it.key.second }
            .mapValues { (_, entries) -> entries.sumOf { it.value } }
        return SenderBindingStock(totals, segments, segmentTotals)
    }

    private fun normalizeKey(raw: String?): String =
        raw?.lowercase(Locale.ROOT)?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
```

并把 `distributionKey(expert)`（`:54-61`）的函数体改为 `normalizeKey(expert.country)`，
消除两份归一实现（保持对外行为逐字不变）。

**T2.3 打分接入存量**（遵 I-2/I-3/I-4）

`selectAccount` 签名加第 4 个默认形参，`assignmentScore` 同步：

```kotlin
    fun selectAccount(
        expert: ExpertProfile,
        currentBatchAssignments: List<SenderExpertAssignment> = emptyList(),
        ignoreWarmup: Boolean = false,
        stock: SenderBindingStock = SenderBindingStock.EMPTY
    ): MailSenderAccount
```

`assignmentScore` 末尾的 return 改为：

```kotlin
        return baseScore -
            account.strategyWeight * 0.2 * sameSegmentCount -
            account.strategyWeight * 0.02 * totalAccountCount -
            account.strategyWeight * STOCK_TOTAL_WEIGHT * stock.totalShare(account.accountCode) -
            account.strategyWeight * STOCK_SEGMENT_WEIGHT *
                stock.segmentShare(account.accountCode, distributionKey)
```

伴随 companion 常量与取值理由注释：

```kotlin
    companion object {
        /**
         * 存量惩罚系数。存量项是"占比"（[0,1]），与批内计数（小整数）量纲不同，
         * 因此使用独立系数（I-2）。取值理由：
         *  - baseScore ∈ [0, strategyWeight]，存量项最大也是 strategyWeight 的同量级，
         *    保证"剩余额度"仍能与"存量公平"竞争，而非被单方碾压。
         *  - 总量权重 > 国别权重：总量是主目标，国别分散是次目标。
         */
        const val STOCK_TOTAL_WEIGHT = 0.5
        const val STOCK_SEGMENT_WEIGHT = 0.3
    }
```

**默认形参 `stock = EMPTY` 保证 I-4**：任何未传快照的既有调用方
（包括 `SenderAccountAssignmentServiceTest` 的全部既有用例）行为逐字不变。

### 阶段 3 — 批量入口装配快照

**T3.1 `InitialOutreachService.kt`**（遵 I-1）

`:32` 的 `val assignments = mutableListOf<SenderExpertAssignment>()` 之后加一行：

```kotlin
        val stock = senderAccountAssignmentService.loadBindingStock()
```

`:48` 改为 `senderAccountAssignmentService.selectAccount(expert, assignments, stock = stock)`。

**T3.2 `ManualInitialOutreachService.kt`**（遵 I-1、IP-2）

在两个轮次的**外层**（`assignments` 初始化处，位于 round 循环之外）各取一次快照，
把 `:272` 与 `:552`（P2 之后位于无绑定兜底分支内）的调用改为
`selectAccount(expert, assignments, ignoreWarmup, stock)`。

> 快照取在 round 循环**之外**而非每轮之内：批量任务可能跑数轮，
> 每轮重取会引入 I-1 想避免的重复扫描；轮内新增绑定由 `assignments` 覆盖（IP-1）。

### 阶段 4 — 测试

**T4.1 `SenderAccountAssignmentServiceTest.kt`**

既有用例**全部不改**（I-4 的回归证据）。新增：

| 用例 | 断言 |
|---|---|
| `empty stock keeps score identical to legacy behavior` | 同一组账号，传 `EMPTY` 与不传 stock 的选中结果一致（I-4） |
| `account with larger bound stock is deprioritized` | A/B 同权重同额度，A 存量 900、B 存量 100 → 选中 B（I-3） |
| `stock penalty does not override strategy weight entirely` | A 权重 1000/存量占比 0.9，B 权重 10/存量占比 0.0 → 仍选 A（I-2：存量不得碾压） |
| `country segment stock is considered` | A/B 总量存量相同，但 "germany" 段 A 占 0.9 → 德国专家选 B（I-3） |
| `unknown country falls into unknown segment` | `country=null` 与 `country="  "` 归一到同一段（T2.2 归一复用） |
| `zero segment total yields zero segment penalty` | 全新国别 → `segmentShare == 0.0`（I-4 除零保护） |

**T4.2 `InitialOutreachServiceTest.kt` / `ManualInitialOutreachServiceTest.kt` 各加 1 例**

`loads binding stock once per batch`：mock `loadBindingStock()`，
跑一个 ≥3 个专家的批次，断言
`Mockito.verify(senderAccountAssignmentService, times(1)).loadBindingStock()`（I-1）。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt` | 修改 | 2 个 GROUP BY 查询 + 2 个投影类 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt` | 修改 | 快照类型、装配、打分接入 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 修改 | 批次开始取快照并传入 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 | 同上（两个轮次） |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentServiceTest.kt` | 修改 | +6 例，既有零改动 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | 修改 | +1 例 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 | +1 例 |

文件数 7 ≤ 10 ✓　子系统 1（选号打分）≤ 2 ✓　新增存储字段 0 ✓

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。
> 来源：项目根 `CLAUDE.md`「Commands」+「项目元信息」。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=SenderAccountAssignmentServiceTest+InitialOutreachServiceTest+ManualInitialOutreachServiceTest

# 单方法（示例语法）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='SenderAccountAssignmentServiceTest#empty stock keeps score identical to legacy behavior'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`，`BUILD SUCCESS`。
来源：`CLAUDE.md` 项目元信息；过滤语法取自「Commands」章节示例。

## 验收标准

- **I-1**: `grep -n "loadBindingStock()" src/main/kotlin` 的调用点全部位于循环**之外**；
  `InitialOutreachServiceTest#loads binding stock once per batch` 与
  `ManualInitialOutreachServiceTest` 同名用例通过。
  `SenderAccountAssignmentService.kt` 内 `assignmentScore` 与 `selectAccount`
  的方法体不含 `expertContactRepository.`。
- **I-2**: `assignmentScore` 的存量两项乘的是 `stock.totalShare(...)` / `stock.segmentShare(...)`
  （返回值 ∈ [0,1]），不含裸计数；
  `stock penalty does not override strategy weight entirely` 通过。
- **I-3**: 打分表达式含且仅含 5 项，与 I-3 公式逐项对应；
  `account with larger bound stock is deprioritized` 与
  `country segment stock is considered` 均通过。
- **I-4**: `SenderAccountAssignmentServiceTest.kt` 的既有用例在 `git diff` 中**零改动**且全绿；
  `empty stock keeps score identical to legacy behavior` 通过。
- **I-5**: 两个 `@Query` 均含 `<> 'SIMULATOR_NOOP'` 与 `IS NOT NULL`。
- **I-6**: `git diff` 中不含对 `bound_sender_account_code` / `today_sent_count` /
  `auto_send_paused` 的新增写语句。
- **回归**: 执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 存量倾斜的账号在新批次中被降权
- 前置条件: 两个 enabled 账号 `A`、`B`，`strategy_weight` 与 `daily_send_limit` 相同，
  `today_sent_count` 均为 0。手工构造存量：
  `UPDATE expert_contact SET bound_sender_account_code='A' WHERE id IN (...);`
  使 `A` 的绑定数约为 `B` 的 9 倍
  （`SELECT bound_sender_account_code, COUNT(*) FROM expert_contact
    WHERE bound_sender_account_code IS NOT NULL GROUP BY 1;` 确认比例）。
- 操作步骤:
  1. 跑一次首封批量，目标 20 位**新**专家（此前无 contact）。
  2. 批量结束后执行上面的 GROUP BY 查询，看本批新增的 20 条落在哪。
- 预期结果: 本批 20 位中落到 `B` 的数量**明显多于** `A`（预期 ≥15 位在 `B`）。
- 覆盖: I-3、需求描述第 1 条

### A-2: 高权重账号不会被存量碾压
- 前置条件: `A` 的 `strategy_weight=1000` 且存量占比约 0.9；
  `B` 的 `strategy_weight=10` 且存量占比约 0.0；两者均 enabled、额度充足。
- 操作步骤: 跑一次 10 人的首封批量，看账号分布。
- 预期结果: `A` 仍承担**多数**（≥6 位）。若 10 位全落 `B`，即为 I-2 违反。
- 覆盖: I-2、must-NOT-change 第 4 条

### A-3: 国别维度仍然分散
- 前置条件: `A`、`B` 总存量相同，但 `A` 的 `country='germany'` 存量远高于 `B`
  （`SELECT bound_sender_account_code, LOWER(TRIM(country)), COUNT(*) ...
    GROUP BY 1,2;` 确认）。
- 操作步骤: 跑一次只含德国专家的小批量（5 人）。
- 预期结果: 5 人中落到 `B` 的数量多于 `A`。
- 覆盖: I-3

### A-4（回归）: 全新库/无绑定时分布与上线前一致
- 前置条件: 一个 `expert_contact` 全部 `bound_sender_account_code IS NULL` 的环境
  （测试库；或临时 `UPDATE expert_contact SET bound_sender_account_code=NULL;` 后再恢复）。
- 操作步骤: 跑一次首封批量，记录账号分布；与本计划上线前同配置的分布对比。
- 预期结果: 分布形态一致（各账号承担量比例同量级）。
- 覆盖: I-4

### A-5（回归）: 已绑定专家的发件账号不受打分影响
- 前置条件: 一位绑定账号为 `A` 的专家，且 `A` 存量占比极高（会被打分重罚）。
- 操作步骤: 在其详情页发送一封模板邮件。
- 预期结果: 邮件仍从 `A` 发出（P2 的绑定解析优先，打分完全不参与）。
- 覆盖: must-NOT-change 第 1 条、I-6、全局 G-1

### A-6（回归）: 批量任务不因存量查询变慢
- 前置条件: `expert_contact` 行数 ≥ 1 万的环境。
- 操作步骤: 跑一次 200 人的首封批量，记录任务总耗时，与上线前同规模任务对比。
- 预期结果: 总耗时增幅在 5% 以内；数据库慢查询日志中**不出现**每专家一次的
  `GROUP BY bound_sender_account_code`（应恰好出现 2 次/批：总量 + 国别）。
- 覆盖: I-1

### A-7（回归）: 候选过滤未被改动
- 前置条件: 三个账号——`A` enabled、`B` `enabled=0`、`C` `auto_send_paused=1`。
- 操作步骤: 跑一次首封批量。
- 预期结果: 全部邮件由 `A` 发出，`B`、`C` 一封不发。
- 覆盖: must-NOT-change 第 3 条
