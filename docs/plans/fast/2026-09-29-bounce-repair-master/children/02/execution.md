# Child 02 Execution Report — DSN 永久退信与收件地址无效分离

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master/docs/plans/2026-09-29/bounce-address-invalid-separation.md`
- Plan SHA-256: `9295a43683dacc0149020976864c82cc3bf9a39b55a18ce153f352dc4b25267a`（执行前、执行后一致）
- Execution ID: `…/docs/plans/2026-09-29/bounce-address-invalid-separation.md@9295a43683dacc0149020976864c82cc3bf9a39b55a18ce153f352dc4b25267a`
- Execution epoch: NEW
- Approval basis: fast-p master plan `docs/plans/2026-09-29/bounce-repair-master.md` + child brief `docs/plans/fast/2026-09-29-bounce-repair-master/children/02/brief.md`
- Executor: ImplBounce02
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master`
- Target branch: `fast/2026-09-29-bounce-repair-master`
- Worktree ID: `…/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master@fast/2026-09-29-bounce-repair-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master`
- child_base_sha (brief) = `4bc9f11956fe77d87063afc8bc393689f0ec3487`（child 01 code head）；实际执行起点 HEAD = `f9dd55b96cbbfde728ea8f6455cd2b48de9bbd9f`（child 01 的 evidence-only docs 提交，`git diff 4bc9f11..f9dd55b -- src` 为空）
- Post-execution code SHA (final HEAD, 8 files) = `9ab0b519bd7d0991a02b92c04746b78d14ce7d3b`
- Evidence HEAD: N/A（子计划未要求独立 evidence 提交；本报告不提交）
- Implementation boundary: `4bc9f11956fe77d87063afc8bc393689f0ec3487..9ab0b519bd7d0991a02b92c04746b78d14ce7d3b`（`git diff --name-status 4bc9f11..HEAD -- src` = 恰好授权 8 文件，见下）

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-2 新纯判据 helper（路径/类名/方法签名冻结） | IMPLEMENTED | `RecipientAddressFailureClassifier.kt` | `:29 isInvalidDsnStatus`、白名单 `:22`、整字段匹配 `:17/:20/:29-37` |
| I-3 完整增强码与解析次序（三个 regex 边界） | IMPLEMENTED | `BounceDetector.kt` | `:247 STATUS_PATTERN`、`:248 DSN_STATUS_PATTERN`、`:249 HARD_SMTP_CODE_PATTERN`；MIME 优先次序未改（`parseBounceDetails:48-75` 未改） |
| I-1/I-2/I-5 ingest 地址证据门槛 | IMPLEMENTED | `BounceCollectionService.kt` | `:164-171` 条件与原有 `markEmailInvalid` 调用点同一位置；保存字段/归因/去重未改（`:130-163`） |
| I-2/I-4 对账 DSN 集合 + 里程碑优先 | IMPLEMENTED | `OperatorStatusReconcileService.kt` | `:70-82` 只收「HARD + 有 contact + 白名单 DSN」；`:216-223` 里程碑优先→地址证据→CONTACTED/NOT_CONTACTED |
| 先补失败测试再最小实现 | IMPLEMENTED | 4 个测试文件 | 见「Commands」runner A/B：同一套测试在回退生产改动后 20 个失败，恢复实现后全绿 |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest=RecipientAddressFailureClassifierTest,BounceDetectorTest,BounceCollectionServiceTest,BounceBackfillServiceTest,OperatorStatusReconcileServiceTest,ExpertOperatorStatusServiceTest,OperatorStatusWriteSeamGuardTest,ManualExpertMaterialUploadFlowTest test` | PASS | exit 0；`Tests run: 136, Failures: 0, Errors: 0, Skipped: 0` + BUILD SUCCESS（129s）；逐类计数见下 A |
| （red-run 验证，runner B）同上命令去掉 Backfill/Expert/Guard/MaterialUpload 四项，且 3 个生产文件临时 `git checkout HEAD --` 回退 | FAIL（预期） | exit 1；`Tests run: 97, Failures: 20, Errors: 0`：BounceDetectorTest 6 / BounceCollectionServiceTest 5 / OperatorStatusReconcileServiceTest 9 / RecipientAddressFailureClassifierTest 0（新 helper 文件为纯新增，无旧行为可比） |

### A. 最终验收运行逐类计数（exit 0，BUILD SUCCESS）

| 测试类 | Tests | Failures | Errors | 基线 |
|---|---|---|---|---|
| `BounceDetectorTest` | 18 | 0 | 0 | 11 → +7 |
| `RecipientAddressFailureClassifierTest`（新增） | 30 | 0 | 0 | absent |
| `BounceCollectionServiceTest` | 29 | 0 | 0 | 16 → +13 |
| `BounceBackfillServiceTest`（只运行） | 3 | 0 | 0 | 3 |
| `ManualExpertMaterialUploadFlowTest`（只运行） | 13 | 0 | 0 | 13 |
| `OperatorStatusWriteSeamGuardTest`（只运行） | 1 | 0 | 0 | 1 |
| `OperatorStatusReconcileServiceTest` | 27 | 0 | 0 | 12 → +15 |
| `ExpertOperatorStatusServiceTest`（只运行） | 15 | 0 | 0 | 15 |
| 合计 | 136 | 0 | 0 | 71 既有全绿 |

### B. red-run 失败明细（回退 `BounceDetector.kt` / `BounceCollectionService.kt` / `OperatorStatusReconcileService.kt` 到 HEAD 后）

- BounceDetectorTest：`keeps the full 5_1_10 … after MIME round trip`、`keeps a three digit detail intact`、`does not truncate a dotted suffix status`、`rejects a two digit class`、`detect keeps the full enhanced status from a text Status line`、`… from an smtp status line`（6）
- BounceCollectionServiceTest：`ingest keeps HARD bounces without address evidence and never marks the expert[1..5]`（5）
- OperatorStatusReconcileServiceTest：`qualifying address evidence does not override the replied / materials received / invited milestone`、`non address permanent bounce with a sent introduction still expects CONTACTED`、`hard bounce without address evidence never implies EMAIL_INVALID[1..5]`（9）
- 回退后已按原样恢复（`cp` 备份回写，`grep` 复核 regex 与 helper 调用点），并在此基础上重跑验收命令（上表 A）。

## Changed Files（`git diff --name-status 4bc9f11..HEAD -- src`）

- `src/main/kotlin/com/weibo/talentintroduction/mail/service/RecipientAddressFailureClassifier.kt`（A）— 新增无依赖 Kotlin object，`isInvalidDsnStatus(value: String?): Boolean`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceDetector.kt`（M）— 仅 `STATUS_PATTERN`/`DSN_STATUS_PATTERN`/`HARD_SMTP_CODE_PATTERN` 长度与边界
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt`（M、8 行）— `ingest` HARD 分支加地址证据门槛
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt`（M、40 行）— 对账退信集合改用 helper；`deriveExpectedStatus` 里程碑优先
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/RecipientAddressFailureClassifierTest.kt`（A）— 真/假矩阵 30 例
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceDetectorTest.kt`（M）— MIME 往返/文本/SMTP/多位数/冲突优先级 7 例 + 冲突夹具
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt`（M）— 门槛正反矩阵、SOFT 异常码、端到端 MIME（2 项 × 参数）等 13 例 + `contactFixture`/`mimeDsnReferencing(status=)` 夹具参数
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt`（M）— 里程碑优先 3 例、正反参数矩阵、SOFT 反例，`hardBounce(dsnStatus=)` 夹具参数

未触碰：`docs/plans/fast/**`（工作区中 `ledger.md` 的 controller 改动保持未提交）、`docs/plans/2026-09-29/**`、migration、`MailRecordRepository.kt`、`ExpertContactRepository.kt`、`ExpertSearchService.kt`、`ExpertOperatorStatusService.kt`、三个只运行测试文件。

## Per-invariant Evidence

| 不变量 | 证据（file:line） |
|---|---|
| I-1 永久失败不是地址判据 | `BounceCollectionService.kt:164-170`（HARD + contact + helper 三重条件）；端到端反例 `BounceCollectionServiceTest.kt:338 collectBounces keeps HARD bounces from real MIME without address evidence without marking`（参数 5.7.1/5.4.1/5.2.2/5.0.0/5.1.100：HARD 保存、归因 10L、`verifyNoInteractions(expertOperatorStatusService)`）；SOFT 携带 5.1.1 反例 `BounceCollectionServiceTest.kt:252` |
| I-1 HARD 分类未被改成 SOFT | `BounceDetector.kt:83-88 classifyBounceType` 未改；`BounceCollectionServiceTest.kt:338` 各码仍断言 `"HARD"` |
| I-2 白名单精确（4 码 / 拒 5.1.7、5.1.8 / 无前缀匹配） | `RecipientAddressFailureClassifier.kt:22 ALLOWED_STATUSES`、`:29-37`；矩阵 `RecipientAddressFailureClassifierTest.kt:17/23/55/60`（含 5.1.5/5.1.6/5.1.7/5.1.8/5.1.100/5.1.10.1/15.1.1/多码/正文/URL/尾随文本 → false） |
| I-2 只消费 dsn_status 整串（legacy `5xx + 空白/连字符`） | `RecipientAddressFailureClassifier.kt:20 LEGACY_SMTP_PREFIXED` + `matchEntire`；`:29-37`；`RecipientAddressFailureClassifierTest.kt:23`（`550 5.1.1`/`550-5.1.1`/`550 5.1.10` 为 true）、`:55`（`550`、`550 5.1.1 user unknown` 为 false） |
| I-2 无 Spring 构造参数/无依赖 | `RecipientAddressFailureClassifier.kt` 无 import 语句（object 内仅 `Regex`/`setOf`） |
| I-3 class 一位、subject/detail 1-3 位、不截断 | `BounceDetector.kt:247-249`；MIME 往返 `BounceDetectorTest.kt:79`（`dsnStatus == "5.1.10"` + HARD）、`:90`（`5.1.100` 完整保留且 helper=false）、`:100`（`5.1.10.1` → `dsnStatus == null`）、`:109`（`15.1.1` → null） |
| I-3 MIME 优先、4.2.2 仍 SOFT、inputStream fallback 保持 | `BounceDetector.kt:48-75 parseBounceDetails`（`:55 mimeDsn`、`:61 dsnStatus = mimeDsn ?: heuristic?.dsnStatus`，优先级未改）、`:210-222 findDeliveryStatusBody`（未改，含 `readPartAsText` fallback）；`BounceDetectorTest.kt:117`（MIME 5.7.1 vs 正文 5.1.1 → 5.7.1）、`:70`（4.2.2 → SOFT） |
| I-3 文本路径同样完整 | `BounceDetectorTest.kt:127`（`Status: 5.1.10` → `"5.1.10"`）、`:139`（`550 5.1.10` → `"550 5.1.10"`） |
| I-4 里程碑优先、退信不遮盖既有通信事实 | `OperatorStatusReconcileService.kt:216-223`；`OperatorStatusReconcileServiceTest.kt:278/292/307`（REPLIED/MATERIALS_RECEIVED/INVITED + 合格 HARD → 0 dbVsExpected、consistent=1） |
| I-4 CONTACTED + 合格证据仍预测 EMAIL_INVALID；CONTACTED + 5.7.1 保持 CONTACTED | `OperatorStatusReconcileService.kt:220-221`；测试 `:321`、`:333`、`:346`（4 码正例）、`:359`（5 码反例）、`:371`（SOFT 不入集合） |
| I-4 不从 DB 既有 EMAIL_INVALID 反推证据 / 不重复写 | `OperatorStatusReconcileService.kt:70-82` 只由 `bounce_record` + helper 组成集合（未读取 `contact.operatorStatus` 作证据）；在线幂等与回退保护落在未改的 `ExpertOperatorStatusService.markEmailInvalid`（`ExpertOperatorStatusServiceTest` 15 例全绿） |
| I-4 人工覆盖 / COMPLETED / 零 DB-ES 写入回归 | `OperatorStatusReconcileServiceTest.kt:385/408/442`（原样全绿；`:442` 断言全部写方法与 `_search` 闭包） |
| I-5 保存字段/dedupe/归因不扩张 | `BounceCollectionServiceTest.kt:309/335/356/379/405/426/453/474/496` 原样全绿（`git diff` 未触及这些断言）；`:474` 补 `verifyNoInteractions(expertOperatorStatusService)` |
| I-5 无 contact 仍保存 HARD 且不写专家 | `BounceCollectionServiceTest.kt:83`（补 `verifyNoInteractions(expertOperatorStatusService)`） |
| I-5 流水线同一 DSN 供统计/列表/对账读取 | 端到端 `BounceCollectionServiceTest.kt:311`（真实 MIME `Status: 5.1.1/5.1.10` → 保存 `dsnStatus` 原值 → `markEmailInvalid` 恰好一次）与 `:338`（非证据码 → 不标记）；对账用同一 helper `OperatorStatusReconcileService.kt:80` |
| 零 DDL / 零历史 UPDATE / 零新 ES 同步调用 | `git diff --name-status 4bc9f11..HEAD -- src` 仅 8 文件、无 `src/main/resources/**`；`OperatorStatusWriteSeamGuardTest`（白名单闭包 + 排除行号自检）1 例绿 |

## Deviations

1. **本 child 有两个提交，主题相同**：`6446179 feat(fast-p): implement 02`（生产 3 + 测试 4 个文件的实现与主体测试）与 `9ab0b51 feat(fast-p): implement 02`（`BounceCollectionServiceTest.kt` +65 行：真实 MIME→ingest 端到端矩阵 2 项 × 参数与 `mimeDsnReferencing(status=)` 夹具参数）。原因：提交后按验收标准「Detector→ingest 同一 DSN」追加更强的端到端证据，而 brief 禁止 `--amend`。**Code head 取最终 HEAD `9ab0b519bd7d0991a02b92c04746b78d14ce7d3b`**（child 03 的 base）。
2. `markEmailInvalid` 的 `reason` 实参保持 `"HARD_BOUNCE"`：子计划只说「reason 使用准确的地址证据表述即可」而非必须变更，且该形参在 `ExpertOperatorStatusService.markEmailInvalid` 内未被使用；门槛条件已保证调用即地址证据。
3. `BounceDetector` 的 `BOUNCE_BODY_PATTERNS`（`BounceDetector.kt:268-274`）首条启发式 regex（`\b5\d\d\s+5\.\d\.\d\b`）未改：I-3 只点明 `STATUS_PATTERN`/`DSN_STATUS_PATTERN`/`HARD_SMTP_CODE_PATTERN`，且该条仅作「是否退信」布尔探测，不产生落库 `dsnStatus`（落库值只来自已修复的三条 + MIME Status）。
4. 未新增/删除任何构造参数（三个服务的构造签名与调用点未变），因此无 Spring 装配变更。

## Freshness

- Plan identity rechecked: YES（`9295a436…267a` 前后一致）
- Worktree identity rechecked: YES（root/branch/git-dir 与执行前一致，HEAD=`9ab0b519…`）
- Reported commits reachable from target branch: YES（`git branch --contains HEAD` → `fast/2026-09-29-bounce-repair-master`）
- Required commands run this invocation: YES（验收命令在最终实现状态下重跑，exit 0；另有 red-run 与恢复后复跑）
- Historical evidence used only as baseline: YES（仅用于对比 71 例既有计数）

## Remaining Blocker

- None。子计划声明、本 child 明确不覆盖的 SMTP 即时失败分支（03 负责）与对账计数重叠（明确延期）不属于本 child 范围。

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`。
