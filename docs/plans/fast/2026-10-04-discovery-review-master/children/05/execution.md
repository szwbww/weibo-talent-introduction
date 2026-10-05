## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/2026-10-04/discovery-review-05-explicit-send.md
Plan SHA-256: 650d0c472e9a9f77292da6ee9a44ed9b096854ea3db47ffb93c83bd38b208306
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/2026-10-04/discovery-review-05-explicit-send.md@650d0c472e9a9f77292da6ee9a44ed9b096854ea3db47ffb93c83bd38b208306
Execution epoch: EPOCH 3 (A5) — 本文件顶部字段为 epoch 2 快照；最新 epoch 3（A5）状态见文末「Epoch 3 (A5)」章节
Approval basis: fast-p child 05 控制方合同 + 人工批准的 A3/A4 扩权（`BatchSendControlService.kt`、`MailComposeTemplateService.kt` 授权 + 预览 DTO 字段扩充）+ A5 扩权（`54ddacf3`：MailOpenTrackingPersistenceTest / LegacyDiscoveryApprovalTest 最小修 + DiscoveryReviewService/Repository 修复 04 回归）
Executor: ImplDiscoveryReview05E3（epoch 3）；epoch 1/2 执行者 ImplDiscoveryReview05 / ImplDiscoveryReview05E2
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master
Target branch: fast/2026-10-04-discovery-review-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master@fast/2026-10-04-discovery-review-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-04-discovery-review-master
Pre-execution code SHA: ee0168b5d2228e4c39c27363ed8b32a7a4f841fd (epoch 1 产品提交 6a54f00 已在 HEAD；child_base_sha=08f5bd5421333447f9173d34fad1c55ac43c3ba5)
Post-execution code SHA: f34d59ade2ae823f3cea3e3d7853680761bc4a1d
Evidence HEAD: N/A（执行报告由控制方单独提交，不进产品提交）
Implementation boundary: ee0168b..f34d59a

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| step 5 (a) 预估冻结模板版本摘要（token 随预览响应下发） | IMPLEMENTED | MailComposeTemplateService.kt, ManualInitialOutreachService.kt | `loadSnapshot/loadSnapshotByCode` + `versionTokenOf`(SHA-256)；`PendingOutreachSummary.template.templateVersionToken`；测试 `preview freezes the template version summary (A3 I-4)` |
| step 5 (b) 执行开始读取同模板版本 / 拒绝过期预览令牌 | IMPLEMENTED | BatchSendControlService.kt, BatchExecutionModels.kt | `ManualBatchExecutionRequest.previewTemplateToken`；`validatePreviewTemplateToken`（`startManual` 入口，409）；测试 `startManual rejects a stale preview template token with 409` + `accepts a matching preview token…` |
| step 5 (c) 运行中渲染复用内存模板内容快照 | IMPLEMENTED | MailComposeTemplateService.kt, ManualInitialOutreachService.kt, IntroductionMailComposer.kt, ManualExpertMailService.kt | `ComposeTemplateSnapshot`；`run()` 执行开始读取一次并贯通两条循环（`composeFromSnapshot`/`sendManualMailFromSnapshot`）；测试 `run renders every mail from the frozen template snapshot` + `composeFromSnapshot renders the frozen in-memory content` |
| step 5 (d) 账号变量缺项 = 启动前模板/账号配置错误 | IMPLEMENTED | BatchSendControlService.kt, MailComposeTemplateService.kt | `ACCOUNT_VARIABLE_KEYS` + `accountRequiredKeys`；`validateAccountVariablesAtLaunch`（按所选账号集合，422；不再用 account=null 筛专家）；测试 `startManual surfaces a missing account variable as a config error before launch` |
| step 5 快照信息写 `request_payload`（不加列） | IMPLEMENTED | BatchExecutionModels.kt | `ManualBatchExecutionRequest` 携带 `snapshot` + `previewTemplateToken`，随 launch 作为 `requestPayload` 落 `task_execution.request_payload` |
| 预览 DTO 准入计数（准入通过/待审核/显式条件排除/本次目标） | IMPLEMENTED | ManualInitialOutreachService.kt, BatchRecipientSelectionService.kt | `PendingOutreachSummary.admission: RecipientAdmissionCounts`；`AdmissionPreviewAccumulator`（按 docId 去重）；测试 `preview exposes admission counts and per-person reason keys` |
| 预览 DTO 逐人 reasonHits/filterKeys（06 数据源） | IMPLEMENTED | ManualInitialOutreachService.kt, BatchRecipientSelectionService.kt | `reasonHits` + `excludedRecipients: List<PreviewExcludedRecipient>`；`BatchRecipientSelectionService.reasonKeysOf` 单一口径；同上测试 + `reason keys mirror the selection口径 exactly` |
| 门禁开启的预估经 `evaluateForBatch`（替代 ES 字段白名单子集近似） | IMPLEMENTED | ManualInitialOutreachService.kt, IntroductionMailComposer.kt | `batchTemplateGate`/`batchGateExcludes` 调用 `evaluateForBatch(account=null,…)`；同源接入预估 `countEsTargets`、NEW 重试与执行 `filterPage`；原有 `preview and execution resolve identical gateEsFields` 回归 + 新增测试 |
| I-1..I-5（epoch 1 已交付）保持 | IMPLEMENTED | 见 epoch 1 | 既有 209 个 Kotlin / 1434 个 JS 用例回归通过 |
| D1 分支保留（不启用任何发送切换） | IMPLEMENTED (retained) | ManualInitialOutreachService.kt | 见下方 D1 清单 |

### Commands
| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…/zulu-11 mvn -DskipTests test-compile` | PASS | exit 0；`BUILD SUCCESS`（本 epoch 最终状态下 fresh 运行） |
| `JAVA_HOME=…/zulu-11 mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,BatchTemplateGateParityTest test` | PASS | exit 0；Tests run 209，Failures 0，Errors 0（BatchTemplateGateParity 5 / ManualInitialOutreach 176 / BatchRecipientSelection 8 / InitialOutreach 20）；同一 test 阶段 JS `node --test` 1434 pass / 0 fail；`BUILD SUCCESS` |

补充（非必需命令，用于确认无跨套件回归）：`mvn -Dtest=BatchSendTaskRuntimeIntegrationTest test` = 22/22 通过（`@JvmOverloads` 保住反射签名的旧重载）。全量 `mvn test` = 4653 tests / 9 failures / 21 errors，全部落在未授权且与本 child API 无关的既有失败集合（见 Deviations）。

### Changed Files (all 10 inside the 12 Authorized Files)
- src/main/.../campaign/domain/BatchExecutionModels.kt — `ManualBatchExecutionRequest.previewTemplateToken`（追加在末尾，保持既有位置参数构造形态）。
- src/main/.../campaign/service/BatchRecipientSelectionService.kt — `reasonKeysOf`/`admissionReasonKey` 收敛到 companion；`select` 改用同一口径。
- src/main/.../campaign/service/BatchSendControlService.kt — `validateTemplateAtLaunch(snapshot)` 增加账号变量预检；新增 `validatePreviewTemplateToken`（`startManual` 入口）。
- src/main/.../campaign/service/ManualInitialOutreachService.kt — 预览 DTO 字段与累加器；执行开始读取内存模板快照并贯通两条循环；`batchTemplateGate` 精确门禁接入预估/重试/取页；`resolveScope` KDoc 澄清；`@JvmOverloads` 保住 `countEsTargets`/`buildRetryableTargets` 旧反射签名。
- src/main/.../mail/service/IntroductionMailComposer.kt — `composeFromSnapshot`；`evaluateForBatch(accountCode: String?)`；`compose` 内部走快照 seam（读取失败回退旧 render）。
- src/main/.../mail/service/ManualExpertMailService.kt — `sendManualMailFromSnapshot`；私有 `sendManualMailInternal`；`composeComposeTemplate` 支持内存快照渲染。
- src/main/.../template/service/MailComposeTemplateService.kt — `ComposeTemplateSnapshot` + `loadSnapshot/loadSnapshotByCode` + `versionTokenOf` + `ACCOUNT_VARIABLE_KEYS`/`accountRequiredKeys`。
- src/test/.../campaign/service/BatchRecipientSelectionServiceTest.kt — 新增 `reasonKeysOf` 口径用例。
- src/test/.../campaign/service/ManualInitialOutreachServiceTest.kt — 新增 6 个 A3 用例（准入计数/逐人原因、模板版本冻结、运行期快照渲染、过期令牌 409、匹配令牌、账号变量 422）；gate-on 同源回归补 `evaluateForBatch` stub。
- src/test/.../mail/service/BatchTemplateGateParityTest.kt — 新增 2 个冻结快照渲染用例。

### D1 branches retained (per plan step 6 / master I-7) — 本 epoch 未启用、未重命名、未删除
- 退订/抑制（`isSuppressed` 拦截）不变。
- 历史已发（INTRO/MATERIAL `hasSent…` DEDUP）不变。
- 账号绑定（`BOUND_SENDER_ALREADY_SET`）不变。
- 首封永久失败阻断不变。
- MATERIAL_REMINDER 需既有 contact 不变。
- 旧 cron 路径（`legacyKv*`）无页面快照：不校验预览令牌、账号变量预检按 `toLegacySnapshot`（gate 关闭）自然跳过，保持各自默认。

### Effective scope this epoch
- 新增：预览冻结模板版本令牌 + 执行期过期令牌拒绝（409）。
- 新增：一次执行的内存模板内容快照（预估→执行→渲染全链复用）。
- 新增：门禁开启时账号变量缺项的启动前 422 配置错误。
- 新增：预估响应携带准入计数 + 原因命中 + 逐人 filterKeys/reasonKeys。
- 改造：门禁开启的预估/重试/取页从「ES 字段白名单子集近似」改为调用 `evaluateForBatch` 的精确专家变量判定（账号变量缺项由启动前预检负责，不逐人筛专家）。
- 无 DB/ES 迁移、无新列、无依赖、无 pom/前端改动。

### Deviations
- 计划 5.5「发现模板已变则更新预估提示」选择为**拒绝使用过期预估令牌**（409，要求重新预估）这一分支；未做「静默沿用旧人数」。
- MATERIAL_REMINDER 的门禁精确判定仍由发送期 `ManualExpertMailService` 负责（材料模板变量与 contact 语义不同于介绍模板，`evaluateForBatch` 是 IntroductionMailComposer 的判定）；其预览只补准入计数/原因与版本摘要，不新增材料模板逐人门禁（超出本 child 授权与计划文本）。
- `@JvmOverloads` 用于 `countEsTargets`/`buildRetryableTargets`：为不改动未授权测试文件 `BatchSendTaskRuntimeIntegrationTest.kt` 的反射目标（`long,RecipientScope` / `RecipientScope,LocalDateTime,Function0`）而保留旧 JVM 重载；未改变任何行为。
- 未新增/删除任何测试断言；`preview and execution resolve identical gateEsFields` 仅补一行 `evaluateForBatch` stub（新增调用点的必要同步），其余为新增用例。
- 既有失败（非本 epoch 引入、未授权文件、与本 child API 无引用）：
  - `MailOpenTrackingPersistenceTest` 2 errors：`compose(eq,any,isNull)` 3 参 matcher 对 4 参调用 → `InvalidUseOfMatchersException`。根因是 epoch 1 提交 `6a54f00` 给 `IntroductionMailComposer.compose` 加了第 4 个默认参数 `enforcePersonalizationGate`（`git log -S` 证明），该测试最后改动于 child 03（`46d7e17`）。修复需授权该测试文件（把 stub 补成 4 参）；epoch 1 报告「为让未授权测试文件编译不变」只保证编译、未覆盖此运行时 matcher 失配。
  - `ExpertContactLocationServiceTest` 19 errors（国家时区目录 `CL` 配置）、`DiscoveryReviewAllPagesTest` 5 failures、`LegacyDiscoveryApprovalTest` 4 failures：均不引用本 child 任何改动 API，属 child 01/03/04 域既有失败。

### Freshness
- Plan identity rechecked: YES（650d0c47… 未变）
- Worktree identity rechecked: YES
- Reported commits reachable from target branch: YES（f34d59a = HEAD，`git branch --contains` 命中目标分支）
- Required commands run this invocation: YES（最终状态下 fresh 运行，exit 0/0）
- Historical evidence used only as baseline: YES

### Remaining Blocker
- None for the A3 授权范围；计划实现方案 5 的六项与预览 DTO 扩充均已实现并有直接测试证据。

### Next Action
- READY_FOR_VERIFICATION → run `verify-p`

---

## Epoch 3 (A5) — 修复本 child 引发的两类测试失败 + child 04 回归

- Executor: ImplDiscoveryReview05E3
- 输入边界: a25fc5b（epoch 1/2 产品提交 6a54f00 / f34d59a 已在 HEAD；child_base_sha 08f5bd5）
- 授权: A5 扩权（`54ddacf3`）新增 #13 `MailOpenTrackingPersistenceTest.kt`、#14 `LegacyDiscoveryApprovalTest.kt`、#15 `DiscoveryReviewService.kt`、#16 `DiscoveryReviewRepository.kt`（后者本次无需改动）。
- 本 epoch 只做最小修复：不改审核/发送语义、不删除仍有效的断言、不触碰 19 个既有 `ExpertContactLocationServiceTest` 错误。

### RCA

**1. `DiscoveryReviewAllPagesTest` 5F（child 04 回归，自 `08f5bd5` 起红）**
- 症状：所有经 `confirm` → `runApplyWorker` 的用例 `applied` 读成 0 / `counts["APPLIED"]` 读成 null（11 tests 中 5F）。
- 根因：epoch-2 的 04 在 `applyItemAndView` / `confirmItems` 成功分支新增 `syncCandidateAfterApproval(item.id, …)`（04 候选投影 seam）。该函数 → `projectApprovedCandidate` → `writer.projectDiscoveryCandidate(...)`；03 的 AllPages 测试 Mockito mock 了 `ExpertIndexWriterService` 且未 stub 这个 04 新增方法（返回 null），`projectApprovedCandidate` 的 `when(result)` else 分支执行 `result.name` → NPE。NPE 从 `applyItemAndView` 抛出后被 `runApplyWorker` 的逐项 `catch (ex: Exception)` 捕获并调用 `repository.markItemFailed(id, "APPLY_ERROR")`，把**已经 APPLIED 的审核项覆盖成 FAILED** → applied=0。
- 判定：**产品缺陷**，不是测试过时（未授权测试文件本身正确）：04 自身 KDoc 承诺「候选投影失败绝不把审核本身标失败」，但异常冒泡违反了该契约。修复放产品侧。
- 修复（仅 `DiscoveryReviewService.kt`，`syncCandidateAfterApproval`）：用 try/catch 隔离投影调用；抛错时以独立错误码 `CANDIDATE_SYNC_FAILED` 记录（`jdbcTemplate` 为空时 no-op）并 return，绝不冒泡到 worker 的 `markItemFailed`。完全落在 04 既有契约内，未改审核结论。
- 验证：`DiscoveryReviewAllPagesTest` 11/11 通过（修复前 5F）；测试文件未改。

**2. `MailOpenTrackingPersistenceTest` 2E（epoch 1 引入）**
- 根因：epoch-1 给 `IntroductionMailComposer.compose` 增加第 4 个默认参数 `enforcePersonalizationGate: Boolean = true`；测试对 3 参调用 `composer.compose(eq, any, isNull())` 打桩，Kotlin 展开为 4 参 `compose$default` 虚拟调用 → Mockito `InvalidUseOfMatchersException`（4 matchers expected, 3 recorded）。
- 最小修复：两处打桩补齐第 4 个 matcher `Mockito.anyBoolean()`；断言未改。

**3. `LegacyDiscoveryApprovalTest` 4F（epoch 1 引入）**
- 根因：epoch-1 按计划移除 `RecipientScope.matchesEsTarget`/`matchesExpert` 中的 `matchesDiscoveryOutreach` 学术/发现门禁（准入迁移到统一 selector 消费持久结论）。旧断言（:28、:40、:49、:57）仍期望 scope 用 legacy/academic 门禁拒绝。
- 最小修复：只把这 4 处过时门禁断言改写为新契约（scope 不再二次判定准入，显式条件命中即 true），保留 `legacyOutreachApproved`、`allowed=false`、evidence-null、digest、身份绑定失效与可见过滤断言不变。

### Epoch 3 命令（fresh，JDK11 zulu-11）
| Command | Exit | Counts |
|---|---|---|
| `mvn -DskipTests test-compile` | 0 | BUILD SUCCESS |
| `mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,BatchTemplateGateParityTest test` | 0 | Tests run 209, Failures 0, Errors 0 |
| `mvn -Dtest=MailOpenTrackingPersistenceTest,LegacyDiscoveryApprovalTest,DiscoveryReviewAllPagesTest test` | 0 | Tests run 22, Failures 0, Errors 0 |
| `mvn test`（全量） | 1 | Tests run 4653, **Failures 0**, **Errors 19**, Skipped 13；19E 全部为既有 `ExpertContactLocationServiceTest.<init>`（国家 CL 时区目录，祖先 ab8e4cb 既有），无其它失败类 |

### Epoch 3 变更文件（均在 16 文件授权内）
- `src/test/kotlin/…/mail/service/MailOpenTrackingPersistenceTest.kt`（#13）— 2 处 stub 补第 4 matcher。
- `src/test/kotlin/…/expert/domain/LegacyDiscoveryApprovalTest.kt`（#14）— 4 条过时门禁断言改写。
- `src/main/kotlin/…/discovery/service/DiscoveryReviewService.kt`（#15）— `syncCandidateAfterApproval` 投影异常隔离。
- `DiscoveryReviewRepository.kt`（#16）— 未改动。
- 未触碰 `DiscoveryReviewAllPagesTest.kt`（未授权；产品侧修复即足够）。

### Epoch 3 验收
- A5：本 child 引入的两类失败（MailOpenTracking 2E、LegacyDiscoveryApproval 4F）与 child 04 回归（AllPages 5F）全部消失；全量仅剩既有 `ExpertContactLocationServiceTest` 19E。满足。
