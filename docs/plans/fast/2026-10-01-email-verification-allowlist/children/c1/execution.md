## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist/docs/plans/2026-10-01/email-verification-allowlist-backend.md`
Plan SHA-256: `2b09828c4d45280d9b4870caac4615c5770c608d5678548f494b3f91744a4f6d`（与 ledger 记录一致；执行前与提交后各核对一次）
Execution ID: `…/docs/plans/2026-10-01/email-verification-allowlist-backend.md@2b09828c4d45280d9b4870caac4615c5770c608d5678548f494b3f91744a4f6d`
Execution epoch: NEW（本 run 首个 c1 执行轮，`children/c1/fix-log.md` 无历史轮次）
Approval basis: master 总览 `docs/plans/2026-10-01/email-verification-allowlist.md`（`49d61d1e…4b73`）+ c1 brief `children/c1/brief.md`（10 授权文件、I-1..I-5、两条必需命令、提交纪律）
Executor: `C1Impl`（fast-p child c1，backend slice）
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist`
Target branch: `fast/2026-10-01-email-verification-allowlist`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist@fast/2026-10-01-email-verification-allowlist@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist`
Pre-execution code SHA: `2452f4a96f138137eacc04b2dc1694e325d6c916`（child_base_sha `26a81bfa…` 之后的 fast-p 证据提交；源码基线与其一致）
Post-execution code SHA: `e7441004aa6dd68fb3f8e486537d3e520f71e0fd`（`feat(fast-p): implement c1`，本 worktree 的 HEAD，10 文件）
Evidence HEAD: N/A（本 child 无独立证据提交；执行报告不提交）
Implementation boundary: `2452f4a..e744100`（10 个授权文件，其中 5 main + 5 test）

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 快照与原因码（I-1/I-3/I-5） | IMPLEMENTED | `campaign/domain/BatchExecutionModels.kt` | `EmailVerificationAllowedStates`（ALLOWED/requireValid/normalize/allowedFor，`null`=三态全放行）；`BatchExecutionSnapshot.emailVerificationAllowedStates: List<String>? = null` + `init` 严格校验；`EMAIL_VERIFICATION_POLICY_SKIP` + 中文标签；修正 `EMAIL_VERIFICATION_REJECTED` 的“占处理槽”误导注释（循环未改） |
| T-2 按本次策略作决定（I-2/I-3/I-5） | IMPLEMENTED | `campaign/service/BatchEmailVerificationService.kt`、`campaign/service/ManualInitialOutreachService.kt` | `verify(..., allowedStates: List<String>? = null)` 第三参；`decisionForState(state, allowed)` 只对走完协议/回显校验的明确结果生效；`conclude(..., providerState)` 分流 undeliverable（标签 + `EMAIL_VERIFICATION_REJECTED`）与策略跳过（`SKIPPED` + `EMAIL_VERIFICATION_POLICY_SKIP`，零标签）；新增 `VerificationResult.PolicySkipped`；`ReusedProviderResult` 去掉 `decision` 字段（复用只带供应商事实）；引擎入口 `normalize(snapshot.emailVerificationAllowedStates)` 并把有效集合作为第三参传入，新增 `PolicySkipped` 分支（processed/skipped 计数、不占 roundPassed、无 contact/选号/SMTP） |
| T-3 复用及清理谓词对称（I-2/I-4） | IMPLEMENTED | `campaign/repository/BatchEmailVerificationRepository.kt`、`task/repository/TaskExecutionRepository.kt` | `FIND_REUSABLE_BY_EMAILS_SQL` 主查询与 `NOT EXISTS` 镜像的 SKIP 集合都增补 `deliverable`；`deleteOlderThan` 保留谓词同口径增补；仍保留 `request_count>0 AND reused_from_id IS NULL AND error_code IS NULL` 与一年窗口；新增两段说明两条谓词必须逐字对称的注释 |
| T-4 针对性测试（I-1～I-5） | IMPLEMENTED | 5 个测试文件 | 目标单测 247 → 264（+17），MySQL IT 14 → 17（+3），全部 0 失败；新增用例覆盖四态 × `null/[]/[deliverable]/[risky,unknown]`、旧 PASS→新 SKIP 与旧 SKIP→新 PASS、同执行内存复用、坏响应残留 state 不得改判、空/非法集合 4xx、并发配额不消耗、SKIP deliverable 最新行与 90 天清理保护、两条复用谓词对称 |
| 人工验收 A-1…A-7 | NOT_EXECUTED（超出本 child 范围） | — | 需隔离预发环境与真实 Emailable/SMTP；A-7 的“启动前 4xx”已在单测层用真实 `GlobalExceptionHandler` 证明，见 Deviations |

### 计划行号复核（计划要求：先核对再改）
计划/证据附件引用的行号已逐条与实际代码核对，存在 ≤10 行漂移，全部按内容重新定位后修改，未照旧行号盲改：
- `conclude` 计划引 `BatchEmailVerificationService.kt:191-206`，实际起始行 `:183`（基线编译告警 “BatchEmailVerificationService.kt: (183, 9) Parameter 'context' is never used” 可复核）。
- `decisionForState` 计划引 `:298-303`，实际 `:296-302`。
- 介绍邮件验证分支计划引 `ManualInitialOutreachService.kt:757-845`，实际 `:759-846`（+2）。
- `FIND_REUSABLE_BY_EMAILS_SQL` 计划引 `:209-228`，实际 `:209-232`（改后 `:214-233`）。
- `TaskExecutionRepository.deleteOlderThan` 计划引 `:178-193`，实际命中 `:183` 的 `SELECT 1 FROM batch_email_verification v`，谓词位于 `:188-189`。
- 计数事实复核：内层成功配额判据确为 `roundPassed < roundQuota`（`ManualInitialOutreachService.kt:687-688` 附近），`EMAIL_VERIFICATION_REJECTED` 只增 processed/roundRejected，故按计划仅修正注释、未改循环。

### Commands
| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -q -DskipTests compile` | PASS | exit 0（最终实现态后再次全量编译 main；期间修正一次表达式体 `return` 编译错误） |
| `JAVA_HOME=…zulu-11… mvn -B -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest,TaskRetentionMigrationTest test`（必需命令） | PASS | exit 0；**264 tests / 0 failures / 0 errors / 0 skipped**（BatchEmailVerificationServiceTest 40、ManualInitialOutreachServiceTest 169、BatchSendControlServiceTest 47、TaskRetentionMigrationTest 8）；基线为 247（33+165+43+6），+17 为本次新增 |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=…zulu-11… mvn -B -DmysqlIt=true -Dapi.version=1.40 -Dtest=BatchEmailVerificationRepositoryIT test`（必需命令） | PASS | exit 0；**17 tests / 0 failures / 0 errors / 0 skipped**；Testcontainers `mysql:8.0.36` 容器真实启动（JDBC `localhost:32773`），基线为 14 tests，+3 为本次新增 |
| `docker version --format '{{.Server.Version}}'`（环境预检） | PASS | exit 0，OrbStack `29.4.0`，故 MySQL IT 为**已执行并验证**，非“未验证” |
| 全量 `mvn test` | 未执行（计划明确为 run 级 c3 收尾命令，非本 child 必需命令） | — |

提交后未再修改任何源文件，故上述两条必需命令的结果对提交 `e744100` 有效。

### Changed Files
- `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` — 允许值集合/严格校验对象、快照字段 + init 校验、策略跳过原因码与标签、误导注释修正
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt` — `verify` 第三参、按本次集合重算决策、undeliverable 与策略跳过分流、复用只带供应商事实
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` — 入口复核快照集合、第三参传参、`PolicySkipped` 跳过分支
- `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt` — 复用查询主谓词与 `NOT EXISTS` 镜像增补 SKIP deliverable + 对称性注释
- `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt` — 90 天清理保留谓词同口径增补 SKIP deliverable
- `src/test/kotlin/…/campaign/service/BatchEmailVerificationServiceTest.kt` — 放行集合矩阵、复用重判、坏响应残留 state、内存复用策略跳过、非法集合拒绝
- `src/test/kotlin/…/campaign/service/ManualInitialOutreachServiceTest.kt` — 第三参 matcher 同步（13 处 stub/verify）、`PolicySkipped` fixture、无副作用/不占配额/传参断言
- `src/test/kotlin/…/campaign/service/BatchSendControlServiceTest.kt` — 快照绑定（缺字段/null/空/数组）、非法集合（含非字符串元素）4xx（真实 `GlobalExceptionHandler` + standalone MVC）、启动载荷保留
- `src/test/kotlin/…/campaign/repository/BatchEmailVerificationRepositoryIT.kt` — SKIP deliverable 最新行、主查询/镜像对称、清理保留
- `src/test/kotlin/…/task/service/TaskRetentionMigrationTest.kt` — 清理 SQL 保护 SKIP deliverable、复用谓词两处对称的文本契约

### Invariants（逐条落地证据）
- **I-1 严格白名单**：`BatchExecutionSnapshot` 新增 nullable 列表（缺字段 = null），`init` 对直接构造与 `copy` 都拒绝 `undeliverable`/未知值/大小写与空白变体；JSON 绑定期非法值为 `JsonMappingException`（→ `HttpMessageNotReadableException` → 400），非法用例在 `BatchSendControlServiceTest` 用真实 handler 断言 400 且只放行 1 次执行；非字符串元素（`[123]`、`[null]`）被绑定拒绝或强制转为字符串后由校验拒绝，绝不静默转为允许值。引擎入口 `normalize()` 复核后本次只读该副本。
- **I-2 供应商事实 ≠ 放行决定**：`ReusedProviderResult` 不再携带 decision（结构性保证“复用只复用事实”）；新请求、内存复用、仓储复用三处都用 `decisionForState(providerState, allowed)` 重算；`undeliverable` 恒 SKIP；HTTP/超时/坏响应即使残留 `provider_state` 也走 `ServiceFailure`（新增用例直接断言）。`provider_state/provider_reason/checked_at` 与 `reused_from_id` 原样保留。
- **I-3 策略跳过零异常标签**：`conclude` 仅在 `providerState == undeliverable` 时写标签与 `EMAIL_VERIFICATION_REJECTED`；策略跳过写 `decision=SKIP`、`send_status=SKIPPED`、`send_reason=EMAIL_VERIFICATION_POLICY_SKIP`，`tag_status` 停留 `NOT_REQUIRED`（零 `recordTag`、零 `appendEmailAbnormalTag`、零 `expertSearchService` 调用）；引擎该分支不建/绑 contact、不选号、不 SMTP、不占账号量，只加 processed/skipped 并继续扫描补足 `roundPassed`（新增用例：跳过 3 个仍补足 2 封成功）。审计写失败仍抛 `EmailVerificationAuditException` 停止整次执行（唯一新增调用是 `recordSend`，仍在引擎既有审计边界内）。
- **I-4 复用与保留对称**：`FIND_REUSABLE_BY_EMAILS_SQL` 主查询与 `NOT EXISTS` **两处**谓词同时增补 `SKIP deliverable`；`deleteOlderThan` 保留谓词同口径；`request_count>0 AND reused_from_id IS NULL AND error_code IS NULL` 与一年窗口未变；复用行不延长保留。IT 构造“较旧 SKIP undeliverable + 较新 SKIP deliverable”断言新行胜出，并断言同邮箱只返回一行（镜像对称），以及 90 天清理保留未过期的 SKIP deliverable 原始行、删除只有过期行的执行。
- **I-5 既有守卫不变**：`emailVerificationEnabled=false` 路径零改动（`beginExecution` 时机不变，关闭时不建上下文）；MATERIAL_REMINDER + true 仍拒绝；未完成/超时仍按单邮箱暂缓；鉴权/额度/限流/服务故障仍停止；`markSending`（PASS+NOT_SENT→SENDING）未改；发放前占比/配额/节奏未改（既有 165→169 个引擎用例全绿，含 58/32/20 数量断言）。

### Downstream interfaces（c2/c3 依赖，已成立）
- 快照字段名 `emailVerificationAllowedStates: List<String>? = null`；缺失字段绑定为 null（旧请求三态全放行）；`[]` 为明确全跳过；Jackson 对非字符串元素不会静默放行。
- 允许值集合与严格校验位于 `com.weibo.talentintroduction.campaign.domain.EmailVerificationAllowedStates`：`ALLOWED`（deliverable, risky, unknown 固定顺序）、`requireValid(List<String>?)`（严格抛 `IllegalArgumentException`）、`normalize(List<String>?)`（校验+去重+固定顺序，null 原样）、`allowedFor(List<String>?)`（得到本次放行集合，null = 三态全集）。c2 做配置列 JSON 解析时可直接复用 `requireValid`/`normalize`。
- `BatchEmailVerificationService.verify(context, target, allowedStates: List<String>? = null)`；不传第三参 = 原三态全放行（现有直接调用兼容）。
- 原因码 `BatchOutcomeReasonCodes.EMAIL_VERIFICATION_POLICY_SKIP` + 中文标签「邮箱验证策略跳过」；策略跳过四实值 `SKIP / SKIPPED / EMAIL_VERIFICATION_POLICY_SKIP / NOT_REQUIRED`。
- state 比较沿用 `lowercase(Locale.ROOT)`；审计存供应商原值。

### Deviations
- 无授权范围偏离：改动仅限 10 个授权文件（`git status --porcelain` 提交前仅这 10 项；提交仅含这 10 项）。
- 实现内一处内部接缝调整（在授权文件内、由 I-2 直接要求）：`internal data class ReusedProviderResult` 删除 `decision` 字段，使“复用只复用供应商事实”在类型上不可绕过。
- 新增 `VerificationResult.PolicySkipped`（计划要求“将 PolicySkipped 与 Rejected(undeliverable) 分开”）；同时按 Kotlin 1.9 sealed `when` 穷尽性补上引擎分支。
- A-7 的 4xx 断言在单测层以 standalone MockMvc + 真实 `GlobalExceptionHandler` 证明（400 + 只启动 1 次执行），未改任何控制器代码；浏览器级人工验收仍待人工阶段执行。
- 未执行人工验收 A-1…A-6（需隔离预发环境、真实 Emailable/SMTP），未执行全量 `mvn test`（计划定为 c3 收尾）。

### Freshness
- Plan identity rechecked: YES（backend sha256 = `2b09828c…4a4f6d`，执行前后一致；master = `49d61d1e…4b73`）
- Worktree identity rechecked: YES（root/branch/git-dir 与 ledger 一致；提交前逐项 `git add` 指定路径，未 `git add -A`）
- Reported commits reachable from target branch: YES（`e744100` 为该 worktree 分支 HEAD，父提交为 `2452f4a`）
- Required commands run this invocation: YES（两条必需命令 + main 编译均在最终实现态后新鲜执行）
- Historical evidence used only as baseline: YES（`docs/plans/fast/…/baseline/*.txt` 只用于对比计数，不作为通过依据）
- 未提交 `docs/plans/fast/**`、`baseline/**`、本报告；未 push / merge / rebase / amend / reset；未触碰主工作区或其他 worktree。

### Remaining Blocker
- None（本 child 的授权范围内工作已完成且两条必需命令全绿）。

### Next Action
- READY_FOR_VERIFICATION → run `verify-p`（独立复核 c1 与 I-1..I-5、下游接口稳定性），随后由 controller 派发 c2。
