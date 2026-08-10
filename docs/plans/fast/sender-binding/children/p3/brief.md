# Fast-P Child Brief — p3 (sender-binding-03-assignment-stock-balance)

> 唯一权威契约 = `docs/plans/2026-08-10/sender-binding-03-assignment-stock-balance.md`。
> 本 brief 只承载主计划级约束与跨子计划接口，不重述子计划正文；以子计划为准。
> P1/P2 已落地；上游接口见下。

## 上游交付（P1/P2，已验）

- `expert_contact.bound_sender_account_code` / `sender_account_bound_at`（V85，索引 `idx_expert_contact_bound_sender`；country 有 `idx_expert_contact_country`）
- `SenderAccountBindingService.resolveForSend(contact, manual, ignoreWarmup=false)`（P2 最终签名，P3 不得再改）
- 发送路径已绑定优先：`selectAccount(...)` 现在只出现在**无绑定兜底**分支（`InitialOutreachService:48` 新建 contact、`ManualInitialOutreachService` 材料提醒轮兜底 + 首封轮无绑定分支）
- `ManualInitialOutreachServiceTest.kt` 已有 `resolveForSend` 桩（A3 适配），P3 在该文件 +1 例

## 全局约束（主计划，违反即 LIGHT_FAIL）

- **M-1**：本批次严格串行 P1→P2→P3→P4→P5；你是唯一 writer。
- **M-2 方法级所有权（P3 范围，排他）**：
  - `campaign/repository/ExpertContactRepository.kt` — P3 只加 `countBindingsByAccount` / `countBindingsByAccountAndCountry` + 2 个投影类
  - `mail/service/SenderAccountAssignmentService.kt` — P3 全部改动（快照类型 / `loadBindingStock` / `assignmentScore` / `selectAccount` 签名）
  - `campaign/service/InitialOutreachService.kt` — P3 只改 `:32` 取快照 + `:48` 传参
  - `campaign/service/ManualInitialOutreachService.kt` — P3 只改两轮外层取快照 + 两处 `selectAccount` 传参
  - 测试：`SenderAccountAssignmentServiceTest`（+6 例，既有零改动）、`InitialOutreachServiceTest`（+1）、`ManualInitialOutreachServiceTest`（+1）
  - 发现需要改本计划名下之外的方法/字段 → 停止并上报
- **M-3**：`resolveForSend` 签名 P3 不得再改。
- **M-6**：本计划迁移：无，禁止新增迁移；**禁止**加 `(bound_sender_account_code, country)` 复合索引（计划已显式声明）。
- **M-7 知识写回（P3 Phase 6）**：**新建** `docs/knowledge/campaign/K-sender-binding-stock-balance.md`，记录存量均衡的系数量纲原则（I-2：存量项归一化 [0,1] 且用独立系数 STOCK_TOTAL_WEIGHT=0.5 / STOCK_SEGMENT_WEIGHT=0.3，不得复用批内 0.2/0.02 系数）。
- **M-8**：存量再平衡不做（只影响新增分配）；已绑定专家的解析结果不变。
- **G-2**：NULL = 未绑定。**G-3**：SIMULATOR_NOOP 永不入绑定（存量统计也要排除）。

## 变更文件清单（P3 的 7 个授权文件）

1. `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentServiceTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`

知识写回：新建 `docs/knowledge/campaign/K-sender-binding-stock-balance.md`（Phase 6）。

> 注意：`SenderAccountAssignmentService` 要注入 `expertContactRepository`（构造参数 +1）。全仓构造点只在 `SenderAccountAssignmentServiceTest.kt`（已授权，`:19`/`:99`/`:133`/`:166`），无越界编译风险。测试内注入 `Mockito.mock(ExpertContactRepository::class.java)` 即可，既有用例不改。

## 关键实现要点（详见计划正文）

- **I-1**：`loadBindingStock()` 每批次调用一次，在 round 循环**之外**；`assignmentScore` / `selectAccount` 方法体内**不得出现** `expertContactRepository.`。
- **I-2**：存量项必须是占比（`totalShare` / `segmentShare` 返回 [0,1]），乘独立系数 `STOCK_TOTAL_WEIGHT = 0.5` / `STOCK_SEGMENT_WEIGHT = 0.3`（companion 常量 + 取值理由注释）；禁止裸计数代入 0.2/0.02。
- **I-3**：打分公式恰 5 项：`baseScore - strategyWeight*0.2*sameSegmentCount - strategyWeight*0.02*totalAccountCount - strategyWeight*STOCK_TOTAL_WEIGHT*stockTotalShare - strategyWeight*STOCK_SEGMENT_WEIGHT*stockSegmentShare`。
- **I-4**：`stock: SenderBindingStock = SenderBindingStock.EMPTY` 默认形参；空快照时两个存量项恒 0，既有 `SenderAccountAssignmentServiceTest` 用例**零改动**通过。
- **I-5**：两个 `@Query` 都带 `bound_sender_account_code IS NOT NULL AND bound_sender_account_code <> '' AND bound_sender_account_code <> 'SIMULATOR_NOOP'`；国别归一再 `LOWER(TRIM(country))`（SQL 侧），空串归一到 `"unknown"` 在 Kotlin 侧（`normalizeKey`，与 `distributionKey` 同一份归一逻辑——把 `distributionKey(expert)` 函数体改为 `normalizeKey(expert.country)`）。
- **I-6**：不写 `bound_sender_account_code` / `today_sent_count` / `auto_send_paused`。
- **IP-1**：`assignments.add(...)` 的位置**不得移动**（快照统计批前绑定，assignments 统计批内新增，二者不重复计数）。
- **IP-2**：材料提醒轮对**所有**专家累加 assignments（含走绑定的）是期望行为，保持不变。

## 必须验证的命令（JDK 11 zulu；裸 mvn 会失败）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=SenderAccountAssignmentServiceTest,InitialOutreachServiceTest,ManualInitialOutreachServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='SenderAccountAssignmentServiceTest#empty stock keeps score identical to legacy behavior'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

> 已知怪癖：`-Dtest=A+B` 的 `+` 分隔在 surefire 2.22.2 下报 "No tests were executed"（exit 1）；**用逗号分隔**。计划正文的 `+` 写法是文档瑕疵。
> 通过判据：退出码 0、`Tests run: N, Failures: 0, Errors: 0`、`BUILD SUCCESS`。基线：P2 后 2256 tests / 0 F / 0 E / 4 skipped、node 479/0。

## 下流接口（后续子计划依赖）

- `SenderBindingStock` 快照类型（`totalShare` / `segmentShare` / `grandTotal` / `EMPTY`）
- `selectAccount(expert, currentBatchAssignments = emptyList(), ignoreWarmup = false, stock = SenderBindingStock.EMPTY): MailSenderAccount`
- `ExpertContactRepository.countBindingsByAccount(): List<AccountBindingCount>`（**P5 的账号池「绑定专家数」列直接消费**：单次 GROUP BY，已排除 NULL/空串/SIMULATOR_NOOP）
- `loadBindingStock()` 批起始调用语义（P4 迁移会使批中快照陈旧一轮，可接受，属 IP-2 近似语义）

## 产物与提交

- 实现 commit 消息：`feat(fast-p): implement p3`
- 完整执行结果追加到 `docs/plans/fast/sender-binding/children/p3/execution.md`
- 只提交实现文件 + execution.md；**不提交** ledger/verify-log/fix-log。
- 返回格式：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + report 路径。
