# Child 04 Execution Report — 自动准入、人工批准与数据写入衔接

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/2026-10-04/discovery-review-04-admission-writes.md`
Plan SHA-256: `446bc9bb2203b5a7ebcba2dbab575859e34e8735490316b9c69d40fa9e9ef36a`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/2026-10-04/discovery-review-04-admission-writes.md@446bc9bb2203b5a7ebcba2dbab575859e34e8735490316b9c69d40fa9e9ef36a`
Execution epoch: RESUME（上轮 BLOCKED：实现完成、根因已修但未复跑）
Approval basis: child 04 brief（master identity `commit:8853573`；plan identity `commit:07beaafc`）+ 人工预授权规则 11
Executor: `ImplDiscoveryReview04R`（execute-p RESUME；上轮 `ImplDiscoveryReview04`）
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`
Target branch: `fast/2026-10-04-discovery-review-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master@fast/2026-10-04-discovery-review-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`
Pre-execution code SHA: `cecd26e603a57bf8f6a90d161f8891fdb3829098`（child 03 code head + 03 evidence commit）
Post-execution code SHA: `08f5bd5421333447f9173d34fad1c55ac43c3ba5`
Evidence HEAD: `08f5bd5421333447f9173d34fad1c55ac43c3ba5`
Implementation boundary: 单次产品提交，精确包含 Authorized Files 表内 10 个文件；无白名单外文件。

> **本轮处置**：上轮遗留的未提交实现（10 文件）经 diff 逐文件核对均在授权范围内且符合 I-1..I-4；
> 在最终实现状态 fresh 复跑两条必需命令。第二次命令首跑仍为 `304 tests / 3 failures / 0 errors`，
> 定位并修复 2 处测试夹具根因后复跑全绿，提交 `feat(fast-p): implement 04`。

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| 1 — `DiscoveryReviewService`：`recordAutomatic` / `resolveAdmission` / `resolveAdmissionBatch` / `projectApprovedCandidate` / `retryBatchCandidateSync` / `initializeExistingAdmissions` + `runInitializeWorker` | IMPLEMENTED | `discovery/service/DiscoveryReviewService.kt` | 新增 04 段落（I-1/I-2/I-4）；`recordAutomatic` 人工/legacy 保护 + CAS 更新；`resolveAdmissionBatch` 身份变化→`decision=null`；投影失败记 `CANDIDATE_SYNC_FAILED` |
| 2 — `DiscoveryReviewController`：`POST /initialize`、`GET /initialize/{key}`、`POST /batches/{key}/sync-retry` | IMPLEMENTED | `discovery/controller/DiscoveryReviewController.kt` | 三端点，登录态 actor，202/200 |
| 3 — `ExpertDiscoveryService` 两初次写路径共用统一准入 | IMPLEMENTED | `discovery/service/ExpertDiscoveryService.kt` | `admitDiscoveryExpert`（recordAutomatic → 投影），删除旧 inline `promoteDiscoveredToCandidate`；ORCID 分支与 `consumeOutcomeInternal` 均改走它 |
| 4 — `ExpertRevalidationService` 尊重人工准入 | IMPLEMENTED | `expert/service/ExpertRevalidationService.kt` | `resolveAdmission` 优先：MANUAL/LEGACY → 只补投影不写 qualification；HOLD/REJECTED → no-op；身份变化 → 自动路径；自动路径补 `recordAutomatic` |
| 5 — `ExpertIndexWriterService` 真实 docId/CAS 投影 | IMPLEMENTED | `expert/service/ExpertIndexWriterService.kt` | `projectDiscoveryCandidate(docId, source, decision)` + `DiscoveryCandidateProjection`：有效层存在→不反向复制；候选存在→不覆盖；`op_type=create` 409 复读身份一致才算已存在 |
| 6 — 测试覆盖（5 文件） | IMPLEMENTED | 5 个授权测试文件 | writer 投影 6 例、service 准入/投影/初始化 12 例、revalidation 人工 4 例 + 非发现 1 例、discovery 统一准入 2 例；5 个测试类全绿 |

### Commands（fresh；worktree 根；JAVA_HOME=zulu-11）

| # | Command | Result | Exact counts |
|---|---|---|---|
| 1 | `mvn -DskipTests test-compile` | PASS，exit 0 | 全量主/测源编译通过（`/tmp/04-testcompile.log`） |
| 2 | `mvn -Dtest=DiscoveryReviewServiceTest,ExpertDiscoveryServiceTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest,ExpertIndexWriterServiceTest test` | PASS，exit 0 | `Tests run: 304, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS` |

第二个命令按类计数：

| 测试类 | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `ExpertDiscoveryServiceTest` | 179 | 0 | 0 | 0 |
| `DiscoveryReviewServiceTest` | 27 | 0 | 0 | 0 |
| `ExpertRevalidationServiceTest` | 31 | 0 | 0 | 0 |
| `ExpertRevalidationServiceBehaviorTest` | 22 | 0 | 0 | 0 |
| `ExpertIndexWriterServiceTest` | 45 | 0 | 0 | 0 |
| 合计 | 304 | 0 | 0 | 0 |

baseline 对照：`docs/plans/fast/2026-10-04-discovery-review-master/baseline.md`（同两命令；本片仅新增/改写本 child 的测试用例，总数 304）。

### 本轮修复的测试夹具根因（均在 Authorized Files 内，仅改断言/夹具，不弱化、不删除防错断言）

首跑（修复前）残留 `3 failures / 0 errors`（较上轮 `3 failures + 16 errors` 已消除全部 error）：

1. `DiscoveryReviewServiceTest.runInitializeWorker records only new admissions and is idempotent`
   —— 第二轮 `findAdmissions` 桩返回的两条 `admission("AUTO_PASSED")` 默认 `docId` 同为 `doc1`，
   被去重成 1 个已知 id，导致 `doc2` 被误记为 `recorded`（断言 `expected: <0> but was: <1>`）。
   修复：改为 `listOf(admission("AUTO_PASSED", docId = "doc1"), admission("AUTO_PASSED", docId = "doc2"))`。
2. `ExpertRevalidationServiceTest.discovery automatic path refreshes the automatic conclusion`
   与 `... discovery identity change falls back to the automatic path`
   —— 共享的 `eligibilityService`（`CandidateEligibilityService`）内部使用**真实** `ExpertClassificationService`，
   对无出版证据的发现档案判 `UNKNOWN` 且 `negativeEvidence` 含 `RND_SCOPE_UNCONFIRMED`，
   使 `eligibility.rejectReasons=[RND_SCOPE_UNCONFIRMED]`，结果 `Rejected` 而非 `Promoted`。
   修复：`serviceWithReview` 改为构造 `CandidateEligibilityService(filterService, emailValidationService, classificationService)`，
   两例把 `classificationService.classify(anyProfile())` 桩从 `ExpertType.UNKNOWN` 改为 `ExpertType.PRODUCTION_RND`
   （真实资格判定与候选分类共用同一桩，避免真实分类器对无据档案误判）。

### Changed Files（均在本 child Authorized Files 表内）

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` — 04 准入写入/批量解析/投影/初始化 + `JdbcTemplate` 尾随默认参数
- `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewController.kt` — 初始化与同步重试端点
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — 两初次写路径统一准入；删除旧 inline 晋升
- `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` — 尊重有效人工准入
- `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` — 真实 `_id`/create/409 投影接缝
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewServiceTest.kt` — 准入/投影/初始化用例
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` — 两初次写路径 + 统一准入用例
- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt` — 自动/人工重验用例
- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt` — 非发现回归用例
- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` — CAS/create/409 用例

无规则 11 重同步文件（未触发任何既有计数/集合断言）。未改迁移、未改 `pom.xml`、未改 `docs/plans/**`（除本报告）、未 push/merge/rebase/squash/amend/reset。

### Deviations

1. **`DiscoveryReviewService` 直接持有 `JdbcTemplate`（尾随默认参数）**：计划要求「审核投影失败在原 `item.error_code` 记 `CANDIDATE_SYNC_FAILED`，state 仍 `APPLIED`」与「自动结论可更新」，但 `DiscoveryReviewRepository` **不在 10 个授权文件内**，其现有方法只能把 `error_code` 与 `FAILED`/`STALE` 一起写。为在授权范围内满足 I-1/I-2，两处窄写（自动结论 CAS 更新、`APPLIED` 项上的同步失败标记/清除）落在 `ReviewService`（计划原文即「写入口收敛到 ReviewService」）。未修改未授权的 repository。
2. **`ExpertDiscoveryService` / `ExpertRevalidationService` 新增尾随默认参数** `discoveryReviewService: DiscoveryReviewService? = null`：遵守「构造只允许追加带默认值尾参数」。为空时（未装配/历史单测）不伪造结论、不晋升，按 `ADMISSION_SERVICE_UNAVAILABLE` 计入过滤（生产恒注入）。
3. **存量初始化 taskType** 使用 `DISCOVERY_REVIEW_INITIALIZE` 字符串（`TaskTypeCatalog` 未授权，无法登记中文名/进度白名单）；`TaskExecutionService` 支持任意 taskType（审计 E5）。
4. **`ExpertDiscoveryServiceTest` 用策略桩**（`DiscoveryAdmissionPolicy` mock，按 eligibility 语义返回 AUTO_PASSED/NEEDS_REVIEW + 原码）：保住既有 179 例语义，同时新增 2 例真实覆盖「NEEDS_REVIEW 不建候选」「RAW 成功后持久结论 + 共享投影」。
5. **REJECTED 人工结论在重验中按 `HOLD` 同义处置**（均 `no-op` 不晋升）：`resolveAdmission` 的 `manual` 谓词覆盖 MANUAL/HOLD/REJECTED/LEGACY；`revalidateDiscovery` 对 `HOLD|REJECTED` 都不自动晋升，与 I-3「人工暂缓/拒绝不自动晋升」一致。
6. 未改任何迁移、未改 `pom.xml`、未改 `docs/plans/**`（除本报告）、未 push/merge。

### Freshness

- Plan identity rechecked: YES（`446bc9bb…` 执行前后一致；plan SHA-256 与 brief 记录一致）
- Worktree identity rechecked: YES（toplevel = 目标 worktree；branch = `fast/2026-10-04-discovery-review-master`）
- Reported commit reachable from target branch: YES（`08f5bd5` 为 `fast/2026-10-04-discovery-review-master` HEAD，父提交 `cecd26e`）
- Required commands run this invocation: YES（两条均 fresh；第一条 exit 0，第二条 exit 0 / 304 全绿）
- Historical evidence used only as baseline: YES

### Verification Notes（供独立验证）

- `resolveAdmission` 的 `manual` 同时覆盖 `discoveryReviewDecision.manual` 与 `LEGACY_APPROVED`；`decision == null`（未初始化/身份变化）一律不视为批准。
- 投影失败**不**改写审核决策、**不**把 `state` 置 `FAILED`，仅在 `error_code` 记 `CANDIDATE_SYNC_FAILED`（`state` 仍 `APPLIED`），重试端点只重跑该类项。
- `projectDiscoveryCandidate` 只在 RAW 源存在（`source` 非空）且决策 ∈ `{AUTO_PASSED, MANUAL_APPROVED, LEGACY_APPROVED}` 时写候选；写正文逐字来自 RAW 源，不改 `filterResult`/`identityVerification`/机构/国家。
- 存量初始化幂等（`INSERT IGNORE` 语义 + 已存在即 `unchanged`），不删文档、不发邮件、不取消人工降级；缺 RAW 层时使用现存层 `_source` 并记录 `level`。
- 本片不切换发送逻辑（D1 未定案），未删除/绕过旧发信门禁。
