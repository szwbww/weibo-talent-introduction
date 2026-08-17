# Child 05 Execution Report — 四处筛选落点与列表筛选控件

- Child: 05
- Plan: docs/plans/2026-08-16/expert-reachability-05-filter-seams.md
- Plan SHA-256: 44928ffa53e8f3dcd22d8d92e331d1822dd1dddc4910737dd46a11d67a426766
- Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability/docs/plans/2026-08-16/expert-reachability-05-filter-seams.md@44928ffa53e8f3dcd22d8d92e331d1822dd1dddc4910737dd46a11d67a426766
- Execution epoch: NEW
- Executor: Reachability05Implementer
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability
- Target branch: fast/expert-reachability
- Pre-execution HEAD (evidence): f0168a140e9068963c1e09b164bfeb38a9d7a80c
- Pre-execution code SHA (child 04 product head): 8530af46bdf0b6575a607645392e12a2bfbdc3e6
- Post-execution code SHA: see commit below

## Result

**PLAN_CONFLICT** — 全量回归门仅因 `OperatorStatusWriteSeamGuardTest` 的 EXCLUDED_NOISE_SITES 行号 pin 过期失败
（该文件不在本 child 授权范围，未编辑；按 A1/A2 先例需人工批准 pin 同步 amendment）。
实现本身完成，7 个授权文件全部就绪，聚焦测试全绿。

## What changed (per task)

### T1 — `ExpertSearchService.reachabilityFilter()` 权威实现（I-5-1 / I-5-2 / I-5-4）
- companion 新增 `ALLOWED_REACHABILITY_MODES`（HIGH_ONLY / EXCLUDE_BLOCKED / UNKNOWN_ONLY / BLOCKED_ONLY，唯一真源，计划 06 复用）
- 新增 `fun reachabilityFilter(mode: String?): Map<String, Any>?`：
  - null/空/空白 → null（I-5-4）
  - HIGH_ONLY → `term reachability=HIGH`（取值经 `ExpertReachability.HIGH.esValue`，不写死字符串）
  - EXCLUDE_BLOCKED → `bool { must: [exists reachability], must_not: [terms reachability in (BLOCKED_UNSUBSCRIBED, BLOCKED_BOUNCED)] }`
  - UNKNOWN_ONLY → `bool { must_not: [exists reachability] }`（I-5-2，无 term UNKNOWN）
  - BLOCKED_ONLY → `terms reachability in (BLOCKED_UNSUBSCRIBED, BLOCKED_BOUNCED)`
  - 非法值 `require(...)` → IllegalArgumentException（GlobalExceptionHandler 映射 400）
- 私有常量 `BLOCKED_REACHABILITY_VALUES` 复用 ExpertReachability 枚举 esValue，避免魔法字符串

### T2 — 内存侧同口径（I-5-2 / I-5-3 / I-5-4）
- `RecipientScope` 末尾追加 `val reachabilityFilter: String? = null`（带默认值；resolveScope 接线属计划 06，未触碰）
- `matchesExpert` 在 operatorStatuses 段之后、discipline 段之前新增可达性判定段：
  - HIGH_ONLY = `value == HIGH`；EXCLUDE_BLOCKED = `!isNullOrBlank() && value != 两个 BLOCKED 值`；
    UNKNOWN_ONLY = `isNullOrBlank()`；BLOCKED_ONLY = 两个 BLOCKED 值之一；非法档位抛 IllegalArgumentException（与 ES 侧一致 fail-fast）
  - null/空 → 跳过该维度判定（I-5-4）

### T3 — 三处 ES 构造点接线（I-5-1 / I-5-4）
- `buildExpertFilters`：追加 `reachability: String? = null` 参数（放末位，保持既有 filter 项与顺序，N-1）；
  末尾 `reachabilityFilter(reachability)?.let { filters.add(it) }`
- `searchExperts`：签名追加 `reachability: String? = null` 并透传（末位，位置调用方不受影响）
- `ExpertIndexController.listExperts`：追加 `@RequestParam(required = false) reachability: String? = null` 并透传
- `buildEsFiltersForLevel`：末尾 `ExpertSearchService.reachabilityFilter(scope.reachabilityFilter)?.let { filters.add(it) }`
- `buildMaterialReminderEsFilters`：签名追加 `reachabilityFilter: String? = null`（该函数无 scope 参数，当前无调用方），
  同款一行委托 `ExpertSearchService.reachabilityFilter(reachabilityFilter)?.let { filters.add(it) }`

### T4 — 前端控件（N-1）
- `index.html`：`expertRecentYearsFilter` 之后新增 `<select id="expertReachabilityFilter">`，5 个 option
  （"" / HIGH_ONLY / EXCLUDE_BLOCKED / UNKNOWN_ONLY / BLOCKED_ONLY）
- `app.js` 4 处同步：loadContacts 读取+params（:4670 区块）、updateFilterBadge 激活判定（:11448 区块）、
  change 监听重置列表（:11462 区块）、gateSummaryParams 读取（:11713 区块）
  （行号为改动后实际；改动前分别为 4669 / 11448 / 11462 / 11713 附近，与本 plan 现状审计 4 处一一对应）

### T5 — `ReachabilityFilterSeamTest`（新增）
- 参数化 4 档（null=UNKNOWN / HIGH / LOW / BLOCKED_UNSUBSCRIBED）× 5 模式（空 / HIGH_ONLY / EXCLUDE_BLOCKED /
  UNKNOWN_ONLY / BLOCKED_ONLY）= 20 组，断言 `matchesExpert` 与按 reachabilityFilter 语义手写的期望值一致（I-5-3）
- `reachabilityFilter(null)` / `reachabilityFilter("")` / `reachabilityFilter("  ")` 均返回 null（I-5-4）
- 非法档位抛 IllegalArgumentException
- 四个档位表达式结构断言（I-5-2：UNKNOWN_ONLY 含 must_not exists；无 term UNKNOWN）
- `searchExperts` 未传 reachability 时 filter 数 = 改动前基线（tag+operatorStatus+region+hasField+discipline=5）且无 reachability 项（I-5-4）
- `searchExperts` 传 UNKNOWN_ONLY 时追加 1 项 must_not exists reachability

## Commands (final state, JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home)

| # | Command | Exit | Result |
|---|---------|------|--------|
| 1 | `node --check src/main/resources/static/app.js` | 0 | PASS |
| 2 | `mvn test -Dtest=ReachabilityFilterSeamTest` | 0 | PASS — Tests run: 25, Failures: 0, Errors: 0, Skipped: 0 |
| 3 | `mvn test -Dtest=ExpertSearchServiceTest` | 0 | PASS — Tests run: 43, Failures: 0, Errors: 0, Skipped: 0 |
| 4 | `mvn test -Dtest=ManualInitialOutreachServiceTest` | 0 | PASS — Tests run: 86, Failures: 0, Errors: 0, Skipped: 0 |
| 5 | `mvn test`（全量回归） | 1 | **Tests run: 2509, Failures: 1, Errors: 0, Skipped: 4** — 唯一失败：`OperatorStatusWriteSeamGuardTest.operator_status write sites exactly match whitelist`（stale pins，见下） |
| 6 | `git diff --check` | 0 | PASS — clean |

（注：聚焦组合命令 `mvn test -Dtest=ReachabilityFilterSeamTest,ExpertSearchServiceTest,ManualInitialOutreachServiceTest` 亦独立复跑通过：
Tests run: 154, Failures: 0, Errors: 0, Skipped: 0。）

## 全量门失败根因（唯一失败，PLAN_CONFLICT 依据）

`OperatorStatusWriteSeamGuardTest` 的 EXCLUDED_NOISE_SITES 钉死行号（child 04 代码头），本 child 授权的 T1/T3
改动使三处 pin 过期。该 guard 文件不在授权 7 文件内，按任务约束未编辑。**stale vs actual**：

| Guard pin（stale） | 实际行号 | 上下文（context 不变） |
|---|---|---|
| `expert/controller/ExpertIndexController.kt:94` | **95** | `operatorStatus = contact?.operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED"` |
| `expert/controller/ExpertIndexController.kt:484` | **485** | `operatorStatus = operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED",` |
| `expert/service/ExpertSearchService.kt:431` | **476** | `operatorStatus = source.nullableText("operatorStatus"),` |

原因：T3 给 `listExperts` 追加 `@RequestParam reachability`（+1 行）；T1 在 companion 追加
`ALLOWED_REACHABILITY_MODES` + `BLOCKED_REACHABILITY_VALUES` + `reachabilityFilter()` 及 import（+45 行）。
白名单闭包断言（ALLOWED_WRITE_SITES）本身无变化、无新增写入点；仅噪声排除行号过期（guard 自带规程 :130「排除名单失效必须同步更新」）。

## 验收标准核对

- I-5-1：`grep -rn '"reachability"' --include=*.kt src/main/kotlin` 命中中，构造 ES query 的仅
  `ExpertSearchService.reachabilityFilter`（:264-276）；其余为 writer 字段名（ExpertIndexWriterService:246）、
  mapping 断言（ExpertIndexService:221/223）、mapToProfile 读取（ExpertSearchService:483）、sourceFields 白名单（:506）。PASS
- I-5-2：`grep -rn 'reachability" to "UNKNOWN"\|term.*reachability.*UNKNOWN' --include=*.kt src/main/kotlin` 零命中。PASS
- I-5-3：ReachabilityFilterSeamTest 20 组参数化全绿。PASS
- I-5-4：单测断言 reachabilityFilter(null/""/"  ") 返回 null；searchExperts 未传 reachability 时 filter 数与改动前基线一致。PASS
- N-3：`git diff` 中 ALLOWED_HAS_FIELDS 零改动行。PASS
- 前端 4 处同步：`grep -c "expertReachabilityFilter" src/main/resources/static/app.js` = 4（≥4）。PASS
- 回归：全量测试除 guard stale pins 外全绿（2509/1/0/4，唯一失败为 guard）。见上。

## Changed files (7 authorized)

1. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt — T1/T3（权威表达式 + 两处接线）
2. src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt — T2（字段 + matchesExpert 段）
3. src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt — T3（两处接线）
4. src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt — T3（@RequestParam）
5. src/main/resources/static/index.html — T4（控件）
6. src/main/resources/static/app.js — T4（4 处同步）
7. src/test/kotlin/com/weibo/talentintroduction/expert/service/ReachabilityFilterSeamTest.kt — 新增（T5）

## Deviations

- 无功能偏差。仅实现选择：BLOCKED 取值经 `ExpertReachability` 枚举 esValue（避免字符串魔法，语义不变）；
  `buildMaterialReminderEsFilters` 无 scope 入参，reachability 档位以带默认值的新参数传入（函数当前无调用方，安全）。
- T5 测试中 `@CsvSource` 空单元格被 JUnit 以 null 注入，模式参数声明为 `String?`（匹配 RecipientScope 字段类型）。

## Freshness

- Plan identity rechecked: YES（44928ffa…，未变）
- Worktree identity rechecked: YES（fast/expert-reachability @ f0168a1 基础）
- Required commands run this invocation after final state: YES
- 实施 commit 位于 target branch、排除 docs/plans/fast/（controller 证据另行提交）

## Remaining Blocker

- 唯一阻塞：OperatorStatusWriteSeamGuardTest stale pins（ExpertIndexController.kt 94→95、484→485；
  ExpertSearchService.kt 431→476）需要人工批准 amendment 同步行号（同 A1/A2 机制，context 不变，仅行号）。
