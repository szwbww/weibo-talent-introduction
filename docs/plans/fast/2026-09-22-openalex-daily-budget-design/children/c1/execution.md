# c1 执行报告 — OpenAlex 账号免费预算（fast-p）

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design/docs/plans/2026-09-22/01-openalex-account-budget.md`
- Plan SHA-256: `bbe7728ee0dec8e7313053a580f355b0fc0eab53134286d8cd4724287d720229`（执行前后一致，未变）
- Execution ID: `.../docs/plans/2026-09-22/01-openalex-account-budget.md@bbe7728e…`
- Execution epoch: `NEW`（本 worktree 首次执行 c1）
- Approval basis: 本次 `fast-p` 主计划调用 + child brief `docs/plans/fast/2026-09-22-openalex-daily-budget-design/children/c1/brief.md`
- Executor: `C1Implementer`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design`
- Target branch: `fast/2026-09-22-openalex-daily-budget-design`
- Worktree ID: `<worktree>@fast/2026-09-22-openalex-daily-budget-design@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design`
- `child_base_sha`: `e2247680592603b091af791ef3629d70739a015b`；执行起点 HEAD = 计划种子提交 `ee1dfcd5439de54475c12ff51c9c713e984a82ec`
- **实现提交（HEAD，可被目标分支到达）：`2dd074a`** — `feat(fast-p): implement c1`（10 files changed, 2971 insertions(+), 276 deletions(-)）
- Evidence HEAD: N/A（`docs/plans/fast/**` 未纳入实现提交，由控制器另行提交）
- Implementation boundary: `ee1dfcd..2dd074a`

## Task Status

| 需求 | 状态 | 文件 | 证据 |
|---|---|---|---|
| T-1 共享账本与原子操作（I-2/I-3/I-4/I-6） | IMPLEMENTED | 清单 1、2 | V132 三表；`OpenAlexBudgetRepository`（固定锁序、唯一 permit、恰好一次结算、按周期归档） |
| T-2 策略与所有调用收口（I-1 至 I-5） | IMPLEMENTED | 清单 3、4、5、6、7 | 显式 `Operation`、permit/Deferred、共享 JDBC 账本注入、逐路径成本、计量主机与重定向拒绝 |
| T-3 验证及独立发布（I-1 至 I-6） | IMPLEMENTED | 清单 8、9、10 | 3 个测试文件；`OpenAlexBudgetRepositoryIT` 在真实 Testcontainers MySQL 上执行 |

## 变更文件（恰好 10 个授权文件）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V132__create_openalex_budget.sql`（新） | `openalex_budget_account` / `openalex_budget_day` / `openalex_budget_reservation`：InnoDB、`DATETIME(3)` UTC、BIGINT、唯一键、非负 CHECK、状态 CHECK；**无任何 API Key 列** |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/OpenAlexBudgetRepository.kt`（新） | JDBC 账本实现：`reserve/settle/markUnknown/applyCooldown/observeProvider/reconcile/claimSyncLease/releaseSyncLease/recordNewEnrichmentRequest/countUnfinishedEnrichmentJobs/recentNewEnrichmentCosts/cleanupClosedCycles` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt` | `Operation`/`DeferredReason`、permit 化 reserve、显式操作目标校验、共享账本接缝、补全保留、快照、官方校准与租约 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexProperties.kt` | `accountScope` / `freeBudgetCredits` / `budgetSyncInterval` + 负值启动失败校验；`toString` 仍不打印 Key |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt` | 生产 Bean `sharedOpenAlexRequestPolicy`（强制注入 JDBC 账本 + 校准接缝）、`HttpOpenAlexBudgetSyncSource`、OpenAlex API client 禁用自动重定向、公共全文执行器逐跳重定向检查 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 全部 `getJson` 显式声明 `Operation`、permit 结算/超时标 UNKNOWN、计量主机候选与重定向拒绝 |
| 7 | `src/main/resources/application.yml` | `account-scope` / `free-budget-credits` / `budget-sync-interval`（全部可由环境变量覆盖）+ 语义注释 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicyTest.kt` | 40 个场景（I-1 至 I-6） |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 67 个场景（逐路径成本、重试二次记账、计量主机/重定向、既有发现回归） |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/OpenAlexBudgetRepositoryIT.kt`（新） | 12 个真实 MySQL 并发/恢复/约束用例，`@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")` |

## 下游接口（c2/c3 直接消费，形状与 brief 完全一致）

- 显式操作预占：`OpenAlexRequestPolicy.reserve(kind: RequestKind, operation: Operation, targetUrl: String?): Permit`（`OpenAlexRequestPolicy.kt:659`），返回 `Permit.Allowed(permitId)` 或 `Permit.Deferred(reason, retryAt)`。
- 兼容入口保留：`beforeRequest(kind)`（`:673`，等价 LIST）、`recordResponse(headers)`（`:714`）、`recordResponse(permitId, headers)`（`:678`）、`recordTimeout(permitId)`（`:740`）。
- `DeferredReason` 五值固定：`DAILY_BUDGET` / `RATE_LIMIT` / `BUDGET_SYNC` / `BUDGET_STORE_UNAVAILABLE` / `ENRICHMENT_RESERVE`（`:58-79`），`Permit.Deferred` 同时带 `retryAt`。
- 只读快照 `OpenAlexRequestPolicy.snapshot(): OpenAlexBudgetSnapshot`（`:614`），字段恰为 brief 的 11 项：`accountScope, resetAt, officialLimitCredits, officialRemainingCredits, confirmedSpentCredits, reservedCredits, effectiveRemainingCredits, enrichmentReserveCredits, lastSyncedAt, deferredReason, retryAt`（`:545-557`），不含 Key / 认证 URL。
- 官方校准：`syncOfficialBudget(force: Boolean = false)`（`:750`）供 c3 的调度 tick 直接调用。
- 归档：`cleanupArchivedCycles(retention = 7d, batchSize = 1000)`（`:783` 附近）。

## 逐不变量证据（file:line + 测试名）

### I-1 操作成本与消费用途分离
- 成本分类单点：`OpenAlexRequestPolicy.kt:38-53`（`LIST(1)`/`SEARCH(10)`/`SINGLETON(0)`/`CONTENT(100)`/`RATE_LIMIT(0)`）；`RequestKind` 仍只表示用途。
- 显式传入 + 目标校验（绝不 substring 推断）：`OpenAlexRequestPolicy.validateOperationTarget`（`:968`）与结构化 host/path/query 推导（`:975-998`）；`OpenAlexDataSource.kt:56`（关键词 → SEARCH，否则 LIST）、`:282`（`/authors/{id}` → SINGLETON）、`:309`/`:355`/`:425`（列表/过滤 → LIST）。
- 测试：`OpenAlexRequestPolicyTest.operation costs are list 1 search 10 singleton 0 content 100 rate limit 0 (I-1)`；`OpenAlexRequestPolicyTest.the declared operation must match the target host and path (I-1)`；`OpenAlexDataSourceTest.list search and singleton calls are charged 1 10 and 0 credits per path (I-1)`（账本确认消耗 1 → 11 → 11）。
- 重试二次记账：`OpenAlexDataSource.kt:74-87`（每次重试重新 `reserve`）；测试 `OpenAlexDataSourceTest.a retried request reserves and is charged a second time (I-1)`（失败尝试保留 1 credit 未结算 + 重试再记 1 = 2）。
- Content 本期拒绝：`OpenAlexContentDisabledException`（`:103`）在预占前抛出，不产生账本行；测试 `OpenAlexRequestPolicyTest.metered content downloads are refused and never write a ledger row (I-1)`（模拟 CONTENT=100 后 `confirmedSpent=0`、`reserved=0`）。
- 计量主机不作为公开全文候选：`OpenAlexMeteredDestinations`（`:109`）+ `OpenAlexDataSource.publicCandidateUrl`（`:183`）与下载前二次拦截（`:159`）；测试 `OpenAlexDataSourceTest.a metered content candidate is skipped and never writes a ledger row (I-1)`、`an api host is not a public fulltext candidate either (I-1)`。
- 出版社请求 0 笔账本：测试 `OpenAlexDataSourceTest.a publisher download leaves the shared policy untouched (I-1)`。
- 重定向逐跳检查：`RestTemplateConfig.kt:363-395`（手工跟随 + `MeteredRedirectException`/`TooManyRedirectsException`，`:255-263`）、API client 禁止自动重定向（`:166-172`，`:114`）；测试 `OpenAlexDataSourceTest.a redirect into an openalex metered destination is refused hop by hop (I-1)`（含「合法公开 302 仍被跟随」的反向断言）。

### I-2 账号周期唯一预算与凭证隔离
- 有效免费上限 = `min(静态官方额度, freeBudgetCredits, dailyBudgetUsd 折算)`，预付永不加入：`OpenAlexRequestPolicy.configuredFreeLimitCredits()`（`:1000` 附近）；配置项与负值启动失败：`OpenAlexProperties.kt:42-63`。
- 稳定非秘密 scope：`OpenAlexProperties.accountScope`（`:42`）、`application.yml:225`；账本三表**无 Key 列**（V132），快照不含 Key。
- 测试：`OpenAlexRequestPolicyTest.keyless budget is the 0_10 dollar free budget and is never unlimited (I-2)`、`keyed budget is ten times the keyless free budget (I-2)`、`official balance caps the effective limit and never adds the prepaid balance (I-2)`（官方 10000/剩余 9773 → `effectiveRemainingCredits == 9773`；带 `X-RateLimit-Prepaid-Remaining-USD: 100` 不抬高）、`paid prepaid balance is never consumed automatically (I-2)`、`a protected free budget only tightens and a legacy usd cap only tightens (I-2)`、`negative configuration fails startup (I-2)`、`key rotation and a second instance share one account scope without resetting the budget (I-2)`（换 Key / 第二实例同一 scope：已用 100 不清零）、`the account scope never derives from the api key (I-2)`。
- 真实 MySQL：`OpenAlexBudgetRepositoryIT.unique constraints non negative checks and state values are enforced by the migration (I-6)`（account 主键唯一 → 重复 ensure 仍 1 行）。

### I-3 预占先于外部请求，乱序响应保守处理
- 单事务锁账号行 → 周期行 → permit：`OpenAlexBudgetRepository.reserve`（`:89-138`）、`lockAccount`（`:402`）、`lockCurrentCycle`（`:429`）、`lockReservation`（`:464`）；`available = min(free_limit − confirmed_spent, provider_ceiling) − outstanding_reserved`（`:49-57`）。
- 恰好一次结算 / UNKNOWN 不退还 / 高成本立即追扣：`settle`（`:140-179`）、`markUnknown`（`:180-190`）。
- 测试：`OpenAlexRequestPolicyTest.a settle is applied exactly once even when the response is handled twice (I-3)`、`a timed out reservation stays unknown and is never refunded (I-3)`、`a higher actual cost is charged immediately and blocks the next over budget request (I-3)`、`a lower balance followed by a higher balance never increases availability (I-3)`、`two connections racing for the last ten credits approve at most ten credits in total (I-3)`。
- 真实 MySQL 并发：`OpenAlexBudgetRepositoryIT.two connections racing for the last ten credits approve at most ten credits in total (I-3)`（11 条独立连接：10 LIST + 1 SEARCH，批准总成本 ≤ 10）、`a permit is reserved before the call and settles exactly once (I-3, I-6)`（重复 settle 返回 false 且不重复扣）、`a higher actual cost is charged immediately and a missing response keeps its reservation (I-3)`。

### I-4 同步与冷却不能变成长时间阻塞
- 未获得可信余额 / 账本不可用即延期：`ensureTrustedLedger`（`:882`）、`reserveInternal`（`:798`）对 `DataAccessException` 一律 `BUDGET_STORE_UNAVAILABLE`；0 成本操作不要求可信周期但账本不可用仍延期。
- 共享 429 冷却（写账号行，跨实例可见，只收紧）：`applyCooldown`（`:192-203`）+ `rateLimitDelayMs`（`:992` 附近：`Retry-After` 一律照做，指数退避才受上限约束）。
- 官方校准：`syncOfficialBudget`（`:750`）走校准租约（`claimSyncLease`，`:282`）+ 只接受本周期快照（`reconcile`，`:229`）。
- 测试：`OpenAlexRequestPolicyTest.a store failure defers metered requests and never falls back to a local budget (I-4)`、`a failed official sync defers metered requests instead of guessing a balance (I-4)`、`a 429 cooldown is shared by every instance of the same account (I-4)`、`a confirmed official cycle unlocks metered requests and an expired one forces a new sync (I-4)`、`a response from before the reset never writes the new cycle (I-3, I-4)`、`waiting for a rate slot returns retryAt instead of sleeping for minutes (I-4)`（0.01/s → `retryAt` = +100s 且 `sleeps` 为空）、`exhausted day budget defers every consumer without dense retries (I-2, I-4)`、`an exhausted budget still allows zero cost operations (I-4)`。
- 真实 MySQL：`only one calibrator can hold the account lease (I-4)`、`zero cost operations write no ledger row but still respect the account rate slot (I-4)`、`a reservation from before the reset only settles its own cycle row (I-4, I-6)`（旧响应只能写旧行）、`an expired cycle is not a trusted cycle and a stale snapshot is ignored (I-4)`。

### I-5 补全保留可借用但有依据
- 保留区计算：`enrichmentReserveCredits`（`:900-916`）：目标 = `floor(freeLimit × ratio)`；有待补 = `min(目标, max(待补数 × 估算, 10))`；无待补且 60 秒无 `NEW_ENRICHMENT` 请求 = 0；永不超过 `effectiveRemaining`。
- 估算：`enrichmentEstimateCredits`（`:918-925`）：无样本 10；有样本 `max(10, 最近 100 次已结算 NEW_ENRICHMENT 成本 P95 × 3)`。
- 只读现有任务表：`OpenAlexBudgetRepository.countUnfinishedEnrichmentJobs`（`:316`，PENDING/RUNNING/RETRY_WAIT）；样本：`recentNewEnrichmentCosts`（`:326`）。
- 测试：`OpenAlexRequestPolicyTest.the enrichment reserve follows the pending work and is released after sixty idle seconds (I-5)`（2000 → 空闲 0 → 重新入队恢复，且不超过实际剩余）、`the enrichment estimate uses the p95 of the last settled new-enrichment costs (I-5)`（P95 30×3=90 → 1800）、`at least one reserved request is kept while the target is sufficient (I-5)`、`discovery and history backfill stop at the share reserved for new-expert enrichment (I-3, I-5)`；`OpenAlexDataSourceTest.discovery defers on the reserved share while new-expert enrichment still runs (I-3, V-3)`（历史补全被保留区挡住、新补全仍可发、0 成本单实体读取不受影响）。
- 真实 MySQL：`OpenAlexBudgetRepositoryIT.unfinished enrichment jobs are counted by status and costs sampled from settled permits (I-5)`。

### I-6 新表字段均有生命周期
- 迁移：V132（account 唯一 scope、day 唯一 `(account_scope, reset_at)`、reservation 唯一 `permit_id` + `day_id` 关联周期、状态 CHECK `RESERVED→SETTLED|UNKNOWN`、`amount_reserved`/`amount_actual` 分列、`request_kind`/`operation` 分列、UTC `DATETIME(3)`、非负 CHECK、`rate_next_at`/`cooldown_until`/`sync_lease_token`/`sync_lease_until`/`last_synced_at` 在账号行）。
- 固定锁序 account → day → reservation：`OpenAlexBudgetRepository` 类注释 + `:402/:429/:464`；UTC 读写：`:475`（`toDb`）/`:476`（`toInstant`）。
- 归档：`cleanupClosedCycles`（`:348-374`，只删 `closed_at` 过期的周期与其 permit，批 ≤ 1000）+ 常量 `ARCHIVE_RETENTION = 7d`、`CLEANUP_BATCH_SIZE = 1000`（`OpenAlexRequestPolicy.kt:1000` 附近）。
- 测试：`OpenAlexBudgetRepositoryIT.a permit is reserved before the call and settles exactly once (I-3, I-6)`、`the ledger aggregate always equals the permit detail (I-6)`（`confirmed_spent` = 已结算 `amount_actual` 之和；`outstanding_reserved` = RESERVED+UNKNOWN 的 `amount_reserved` 之和）、`unique constraints non negative checks and state values are enforced by the migration (I-6)`、`utc timestamps survive the round trip through the ledger (I-6)`、`an active unknown is never cleaned up while closed cycles are archived in batches (I-6)`；`OpenAlexRequestPolicyTest.the read-only snapshot carries exactly the downstream fields and no credential (I-6)`、`closed cycles are archived in bounded batches and active cycles are never cleaned (I-6)`。

## 命令与结果

### 1. 必跑命令（brief 指定，最终实现状态上执行）

```
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock \
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
mvn -B -Dtest=OpenAlexRequestPolicyTest,OpenAlexDataSourceTest,OpenAlexBudgetRepositoryIT \
    -DmysqlIt=true -Dapi.version=1.40 test
```

- 退出码 **0**，`BUILD SUCCESS`，`Tests run: 119, Failures: 0, Errors: 0, Skipped: 0`
  - `OpenAlexRequestPolicyTest` — Tests run: 40, Failures: 0, Errors: 0
  - `OpenAlexBudgetRepositoryIT` — Tests run: 12, Failures: 0, Errors: 0（**真实执行**：`Creating container for image: mysql:8.0.36` → `Container mysql:8.0.36 started in PT12.6S` → `JDBC URL: jdbc:mysql://localhost:32829/talent_introduction`；耗时 22.9s，非 skipped）
  - `OpenAlexDataSourceTest` — Tests run: 67, Failures: 0, Errors: 0
- `test` 阶段同时经 `exec-maven-plugin` 跑 node JS 套件：`tests 1037 / pass 1037 / fail 0`（与 baseline 一致）

### 2. 受触及主题的定向回归（补充证据，非全量套件）

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
mvn -B -Dtest=ExpertDiscoveryServiceTest,RestTemplateConfigTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest,ExpertAcademicEnrichmentWorkerTest test
```

- 退出码 **0**，`BUILD SUCCESS`，`Tests run: 211, Failures: 0, Errors: 0, Skipped: 0`
  - `RestTemplateConfigTest` 18（改动 `RestTemplateConfig` 后仍全绿）、`ExpertDiscoveryServiceTest` 121、`ExpertDiscoveryControllerTest` 17、`ExpertDiscoveryControllerMvcTest` 3、`ExpertDiscoverySchedulerTest` 8、`TaskProgressControllerTest` 8、`TaskProgressControllerExecutionsTest` 26、`ExpertAcademicEnrichmentWorkerTest` 10。
- baseline 的同类清单为 277 个（含本报告第 1 条命令已覆盖的 `OpenAlexRequestPolicyTest` 13 + `OpenAlexDataSourceTest` 63）；两者相加覆盖 baseline 全部 277。

### 3. 计划身份 / worktree 身份

```
python3 <execute-p>/scripts/plan_identity.py docs/plans/2026-09-22/01-openalex-account-budget.md
→ sha256 bbe7728ee0dec8e7313053a580f355b0fc0eab53134286d8cd4724287d720229（执行前、提交前、提交后一致）

python3 <execute-p>/scripts/worktree_identity.py docs/plans/2026-09-22/01-openalex-account-budget.md \
  --expect-root <worktree> --expect-branch fast/2026-09-22-openalex-daily-budget-design --expect-git-dir <worktree-git-dir>
→ 匹配
```

## Deviations（与计划文本的显式差异，均已在本报告说明）

1. **启动校准是「首个计量请求前的惰性校准」，不是 Bean 初始化时的网络调用**：`@PostConstruct` 会在测试上下文里发起真实 HTTP。当前实现于启动后第一次计量请求前、官方 reset 到期、额度不足、异常恢复（超时后 `forceSyncBeforeNextReserve`）以及每 `budgetSyncInterval`（300s）触发校准，并额外提供 `syncOfficialBudget()` 供 c3 的调度 tick 调用。语义仍是「未获得可信余额即延期」，且不为调度线程引入长睡眠。
2. **`dailyBudgetUsd` 与 `freeBudgetCredits` 的合并语义**：`有效免费上限 = min(静态官方免费额度[带 Key 10000 / 匿名 1000 credits], freeBudgetCredits, dailyBudgetUsd>0 时的折算值)`；`dailyBudgetUsd = 0` 表示采用免费保护默认值（绝不无限）；负值启动失败。
3. **全文下载回调不再写额度口径**（删除了上一轮的 `reportOpenAlexQuota`）：按 I-1/T-2，计量 Content 主机（`content.openalex.org`/`api.openalex.org`）既不是公开候选、也不作为重定向落点，因此下载路径不可能产生 OpenAlex 计量消耗；`fulltextDownloadCount()` 保留为兼容观察路径的遥测计数（本阶段恒 0）。
4. **`remainingCredits()` 与快照 `effectiveRemainingCredits` 统一为同一口径**（都扣除全部未结算预占）。旧口径不含在飞预占；受影响的是授权测试文件 `OpenAlexRequestPolicyTest`/`OpenAlexDataSourceTest` 中的既有断言（已按新口径更新，语义见各测试注释）。
5. **`RestTemplateConfig.openAlexRequestPolicy(properties)` 保留为普通（非 `@Bean`）兼容工厂**：未授权的既有 `RestTemplateConfigTest:224` 直接调用它，不能修改。生产 Bean 改为 `sharedOpenAlexRequestPolicy`，**强制注入** `OpenAlexBudgetStore` 与 `OpenAlexBudgetSyncSource`（缺失即 Spring 启动失败），因此不存在生产静默内存降级；内存账本仅存在于单元测试与兼容工厂。
6. **DAILY_BUDGET 的 `retryAt` 取「已确认的官方周期 reset」**，而不是响应头 `X-RateLimit-Reset` 换算出的未确认时刻（周期身份只能由官方同步变更，避免旧响应污染新周期）。
7. **兼容观察路径（无 permit 的 `recordResponse(headers)`）只收紧 provider ceiling，不再本地重复记账**：provider 的 `remaining` 已包含该次消耗，重复相加会双重扣减。
8. **真实 MySQL 上 CHECK 违例被 Spring 归为 `UncategorizedSQLException`（error 3819 / SQLState HY000）**，因此 IT 对 CHECK 断言 `DataAccessException`，对唯一键断言 `DataIntegrityViolationException`。
9. **I-5 语义落地**：保留区不再是固定 20% 下限，而是「有待补 → 生效；无待补且 60s 无新补全 → 降 0」；既有两处测试因此需要注入待补任务数（`InMemoryOpenAlexBudgetStore.pendingEnrichmentJobs`）。
10. **`PolicyTimeSource.nanoTime()` 已删除**（旧内存策略用它做槽位计时；共享账本按 UTC 时刻记账，该成员成为死代码）。仅本 child 的两个授权测试实现该接口，已同步更新。

## Freshness

- Plan identity rechecked: YES（执行前、提交前、提交后同值）
- Worktree identity rechecked: YES（提交前用 `--expect-*` 复核；提交后确认 `HEAD = 2dd074a` 且为目标分支祖先）
- Reported commits reachable from target branch: YES（`git merge-base --is-ancestor HEAD fast/…` 通过）
- Required commands run this invocation: YES（最终状态上重跑，退出码 0）
- Historical evidence used only as baseline: YES（baseline 仅用于对比 JS 1037 与既有 Java 用例集合）
- 工作区状态：仅 `docs/plans/fast/2026-09-22-openalex-daily-budget-design/` 未跟踪（按 brief 排除于实现提交，由控制器提交）

## Remaining Blocker

- None。

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
