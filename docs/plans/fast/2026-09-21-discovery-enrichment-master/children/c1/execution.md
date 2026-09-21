# Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/01-openalex-auth-budget.md
Plan SHA-256: e239aaf3cd286f61af829212566ad60ea4d631e984df1dde75d102d44c08f231
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/01-openalex-auth-budget.md@e239aaf3cd286f61af829212566ad60ea4d631e984df1dde75d102d44c08f231
Execution epoch: NEW
Approval basis: child brief `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c1/brief.md` (child c1, plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`) + the controller invocation that bound implementing c1 to this plan file.
Executor: C1Implementer
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Target branch: fast/2026-09-21-discovery-enrichment-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master@fast/2026-09-21-discovery-enrichment-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Pre-execution code SHA: f0c41271fc56d7455e14d28a71d563a5341dfdeb (`child_base_sha`; branch HEAD when execution started was 831e6604cf97e7acba005d8f00827659b49ce010, the plan-seed docs commit)
Post-execution code SHA: 147dc953a194a27ea73b7934a8e2bc334beca655
Evidence HEAD: N/A (single product/test commit; the plan requires no separate evidence commit)
Implementation boundary: 831e6604cf97e7acba005d8f00827659b49ce010..147dc953a194a27ea73b7934a8e2bc334beca655

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Task 1 配置与认证 (I-1): apiKey/每秒限额/预算保留比例配置；专用拦截器按 HTTPS origin 注入 Bearer；跨 origin 剥离凭据；空 Key 匿名兼容 | IMPLEMENTED | `OpenAlexProperties.kt`, `RestTemplateConfig.kt`, `application.yml`, `RestTemplateConfigTest.kt` | `RestTemplateConfigTest` 10/10 PASS；`openAlexRestTemplate sends bearer to the configured https origin` 用 `MockRestServiceServer` 断言真实外发请求头为 `Bearer test-key`（含 `:443` 显式默认端口）；`keeps the key off every other origin` 断言 8443 / `api.openalex.org.evil.com` / 外部 HTTPS / HTTP 四类 origin 无 Authorization；`strips a credential from an untrusted origin` 断言跨 origin 目标上预先存在的 Authorization 被移除；`anonymous configuration never sends a credential` 断言空 Key 无认证头；`configuration never prints a live key` 断言配置对象 toString 脱敏 |
| Task 2 额度决策 (I-2, I-3): 单例 `OpenAlexRequestPolicy`，区分 DISCOVERY/NEW_ENRICHMENT/HISTORY_ENRICHMENT；请求前串行预留成本、响应后按实际头校正；更新 resetAt；上层可识别额度暂停 | IMPLEMENTED | `OpenAlexRequestPolicy.kt` (new), `RestTemplateConfig.kt` (`openAlexRequestPolicy` bean), `OpenAlexDataSource.kt`, `application.yml`, `OpenAlexRequestPolicyTest.kt`, `OpenAlexDataSourceTest.kt` | `OpenAlexRequestPolicyTest` 13/13 PASS（预算档位、20% 保留、Deferred.resetAt、日耗尽无密集重试、UTC reset 恢复、付费余额不自动消费、429 有界退避与恢复、速率上限钳制、列表/全文分开计数、无响应释放预留、并发只放行一次）；`OpenAlexDataSourceTest` 37/37 PASS，新增 3 例在请求边界断言：`searchPapers marks DISCOVERY and reconciles the real quota headers`（真实头 989/1000/1/76723 → `remainingCredits()==989`）、`discovery defers on the reserved share while new-expert enrichment still runs`（发现/历史回填抛 `OpenAlexBudgetDeferredException`，NEW_ENRICHMENT 仍成功）、`a failed call releases its reservation without counting a call` |
| 免费预算与保留口径 (brief): 带 Key $1/UTC 日、无 Key $0.10/UTC 日；effective limit = min(配置上限, 供应商剩余/上限)；不自动使用付费余额 | IMPLEMENTED | `OpenAlexRequestPolicy.kt`, `OpenAlexProperties.kt`, `application.yml` | `keyless budget is the 0_10 dollar free budget…`(1000 credits)、`keyed budget is ten times…`(10000)、`provider remaining and limit headers cap the configured budget`、`paid prepaid balance is never consumed automatically`；单位 1 credit = $0.0001 由线上响应头实证（见 Commands 第 4 行） |
| 下游接口保持 (brief): `Permit.Allowed/Deferred(resetAt)`、`beforeRequest(kind)`、`recordResponse(headers)`、`RequestKind.DISCOVERY/NEW_ENRICHMENT/HISTORY_ENRICHMENT`；非 Spring 构造点可用；既有公开方法签名不变 | IMPLEMENTED | `OpenAlexRequestPolicy.kt`, `OpenAlexDataSource.kt` | 类型与签名逐字一致（`sealed class Permit { object Allowed; data class Deferred(val resetAt: Instant) }`、`fun beforeRequest(kind: RequestKind): Permit`、`fun recordResponse(headers: HttpHeaders): Unit`）；`OpenAlexDataSource` 第 5 个构造参数带默认值（非 Spring 调用点 `OpenAlexDataSource(rt, props, pmc, pdfExtractor)` 仍编译）；补全入口新增 `kind` 重载，既有 1 参重载保留 |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -o -q compile` | PASS | exit 0（主源码编译，最终实现状态前） |
| `JAVA_HOME=… mvn -o -q test-compile` | PASS | exit 0（测试源码编译） |
| `JAVA_HOME=… mvn test -Dtest=RestTemplateConfigTest,OpenAlexRequestPolicyTest,OpenAlexDataSourceTest` | PASS | exit 0，BUILD SUCCESS；surefire 汇总 `Tests run: 60, Failures: 0, Errors: 0, Skipped: 0`（OpenAlexRequestPolicyTest 13 / RestTemplateConfigTest 10 / OpenAlexDataSourceTest 37）；同命令 test 阶段由 `exec-maven-plugin` 触发的 JS 回归 `tests 1035, pass 1035, fail 0`。首次运行有 2 个**测试自身**的用例错误（`Cannot add more expectations after actual requests are made`），已改为先声明全部期望再发请求，最终运行全部通过 |
| `curl -s -D - -o /dev/null "https://api.openalex.org/works?per_page=1&mailto=…"` | PASS | 2026-09-21 实测响应头：`x-ratelimit-limit: 1000`、`x-ratelimit-remaining: 989`、`x-ratelimit-credits-used: 1`、`x-ratelimit-cost-usd: 0.0001`、`x-ratelimit-limit-usd: 0.1`、`x-ratelimit-reset: 76723`、`x-ratelimit-prepaid-remaining-usd: 0`；据此确定 1 credit = $0.0001（1,000 次 list+filter = $0.10 → 无 Key 上限 1,000 credits、带 Key 10,000 credits）、全文下载 100 credits、`X-RateLimit-Reset` 为距 UTC 午夜秒数 |
| `python3 .agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-09-21/01-openalex-auth-budget.md` | PASS | canonical path + sha256 `e239aaf3…f231`（执行开始与交付前一致） |
| `python3 .agents/skills/execute-p/scripts/worktree_identity.py … --expect-root … --expect-branch … --expect-git-dir …` | PASS | worktree root/branch/git-dir 与记录一致，HEAD 831e660 → 提交后 147dc95 |

## Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexProperties.kt` — 新增 `apiKey`、`maxRequestsPerSecond`(5.0)、`newEnrichmentReserveRatio`(0.2)、`dailyBudgetUsd`(0=用供应商免费预算)、`rateLimitBackoffMaxMs`(60s)；`toString()` 脱敏 apiKey（I-1）
- `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt`（新增）— 共享额度/速率权威：`RequestKind`、`Permit`、`OpenAlexBudgetDeferredException`、`PolicyTimeSource`、预算预留与响应头校正、保留份额、UTC 日翻转、有界 429 退避、列表/全文分开计数
- `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt` — `OpenAlexAuthInterceptor`（仅 HTTPS 配置 origin 注入 Bearer，其余 origin 剥离凭据）接入 `openAlexRestTemplate`；新增 `openAlexRequestPolicy` bean
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` — 所有 OpenAlex HTTP 经 `getJson(kind, url)`：显式 kind、请求前预留、响应后按真实头校正、Deferred 抛可识别异常；`searchPapers` 标记 DISCOVERY；补全入口新增 `kind` 重载并以 try/释放语义配对
- `src/main/resources/application.yml` — `OPENALEX_API_KEY`、`OPENALEX_MAX_REQUESTS_PER_SECOND`、`OPENALEX_NEW_ENRICHMENT_RESERVE_RATIO`、`OPENALEX_DAILY_BUDGET_USD`、`OPENALEX_RATE_LIMIT_BACKOFF_MAX_MS`
- `src/test/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicyTest.kt`（新增）— 13 例
- `src/test/kotlin/com/weibo/talentintroduction/config/RestTemplateConfigTest.kt` — 10 例（含真实外发请求头的认证边界）
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` — 37 例（既有 34 例断言逐条保留，仅把 `getForObject` 桩改为 `exchange` 以读取真实响应头；新增 3 例额度/认证接缝）

## Deviations

Plan 未唯一确定的实现细节，均已落在授权文件内、未新增文件、未改任何既有公开签名：

- 额度单位与默认档位取值：按线上响应头实证 1 credit = $0.0001；`X-RateLimit-Limit/Remaining` 视为 credits（无 Key 1,000、带 Key 10,000），`X-RateLimit-Credits-Used` 为本次请求实际成本，全文下载按 100 credits 单列；`X-RateLimit-Prepaid-Remaining-USD` 只读不使用（brief：付费余额不自动消费）。
- `dailyBudgetUsd` 用 `0` 表示「未配置」而非可空类型，避免 `application.yml` 里 `${OPENALEX_DAILY_BUDGET_USD:}` 空串到 `Double?` 的绑定歧义；仍可由环境变量覆盖。
- 既有 1 参补全入口（`enrichAuthor`/`enrichAuthorByOrcid`/`enrichAuthorByOrcidWithReason`/`batchEnrichByOrcids`）默认按 `HISTORY_ENRICHMENT` 记账——它们当前的唯一生产调用方是既有「补充学术数据」回填路径，符合「历史回填优先级最低」；c6/c7/c8 可改用带 `kind` 的重载显式传 `NEW_ENRICHMENT`。
- 「额度暂停可识别」的载体：`beforeRequest` 返回 `Deferred(resetAt)` 时抛 `OpenAlexBudgetDeferredException(resetAt)`（继承 `IllegalStateException`），并在各补全入口的通用 `catch (e: Exception)` 之前重新抛出，避免被误判为「无补全数据」。
- 请求频率 429 与日额度 429 的判别：`Remaining <= 0` → 日额度耗尽（所有 kind 立即 `Deferred`，不 sleep）；否则 `Retry-After` 存在（或有 remaining 无成本头）→ 请求频率 429，仅作有界指数退避（`max(Retry-After, 退避值)`，封顶新增配置 `rateLimitBackoffMaxMs`，默认 60s），成功响应即清零。
- 「启动后先读取额度」按主计划「重启必须用 /rate-limit 或响应头重建」实现为：未观测到头时按已配置免费预算保守限流（不假定无限），首个响应头即重建额度与 resetAt；未新增启动期网络探测（`/rate-limit` 需有效 Key，且 c1 无授权的启动钩子文件）。
- `OpenAlexDataSource` 内部传输由 `getForObject` 改为 `exchange`，以取得 I-2 要求的真实响应头；公开方法签名与返回类型不变。
- 无响应失败路径调用 `recordResponse(HttpHeaders())`：释放预留、按估计成本保守计费、不伪造调用计数、并施加有界保守退避。

## Freshness

- Plan identity rechecked: YES（`e239aaf3…f231` 未变；brief 与主计划未被修改）
- Worktree identity rechecked: YES（root/branch/git-dir 一致，HEAD 由 831e660 推进到 147dc95）
- Reported commits reachable from target branch: YES（147dc95 为 `fast/2026-09-21-discovery-enrichment-master` 的 HEAD，提交内容仅 8 个授权文件，未包含 `docs/plans/fast/**`）
- Required commands run this invocation: YES（最终实现状态后重新运行，exit 0，60/60 + JS 1035/1035）
- Historical evidence used only as baseline: YES（无历史测试输出被当作证据）

## Remaining Blocker

- None

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
