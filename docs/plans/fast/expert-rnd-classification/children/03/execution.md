## Execution Result: PLAN_CONFLICT

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification/docs/plans/2026-08-24/03-expert-rnd-send-gate.md
Plan SHA-256: 05dbebcb16814e7d6b993a7e9e4fe34a7386574fe9460fba73e96fd29efc3368
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification/docs/plans/2026-08-24/03-expert-rnd-send-gate.md@05dbebcb16814e7d6b993a7e9e4fe34a7386574fe9460fba73e96fd29efc3368
Execution epoch: NEW
Approval basis: current invocation (fast-p child brief 03, controller-approved master plan 00)
Executor: Imp03
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification
Target branch: fast/expert-rnd-classification
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification@fast/expert-rnd-classification@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-rnd-classification
Pre-execution code SHA: ec7226b485dbfff98a33260e68ef289df3fa1169 (child base; branch HEAD at start 583744f = child 02 docs evidence)
Post-execution code SHA: f3da97af6dc9dfced2a73f7fa13ba8cf66b8fd1e
Evidence HEAD: N/A (controller commits evidence separately; docs/plans/fast/ untouched by implementation commit)
Implementation boundary: ec7226b..f3da97a (7 authorized files; intermediate 583744f is docs-only child-02 evidence)

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 共享 sendable 谓词 (I3-1/I3-2) | IMPLEMENTED | ExpertSearchService.kt, ExpertSearchServiceTest.kt | ExpertSearchServiceTest 56/56 PASS（expertSendableFilter 逐字 term；searchSendableExpertsWithEmail 请求体含 exists email + term true 且按层级排序；searchExpertsWithEmail 保持通用查询不带 term） |
| T2 旧定时/队列首发双门禁 (I3-1/I3-4) | IMPLEMENTED | InitialOutreachService.kt, InitialOutreachServiceTest.kt | InitialOutreachServiceTest 13/13 PASS（查询改用 searchSendableExpertsWithEmail；forEach 首行 sendable 门禁：null 分类零写入、混合场景仅可发者创建 contact/投递） |
| T3 批量 ES 与重试同口径 (I3-1~I3-3/I3-5) | IMPLEMENTED | BatchExecutionModels.kt, ManualInitialOutreachService.kt, ManualInitialOutreachServiceTest.kt | ManualInitialOutreachServiceTest 92/92 PASS（matchesExpert 内存门禁参数化六类型+null；内存/ES 谓词 parity；buildEsFiltersForLevel 末尾追加 term 且 MATERIAL_REMINDER 不追加；EXPERT_NOT_SENDABLE reason code；预估/执行同源） |
| T4 批量最后门禁 (I3-4) | IMPLEMENTED | ManualInitialOutreachService.kt, ManualInitialOutreachServiceTest.kt | ManualInitialOutreachServiceTest 92/92 PASS（round loop 最后门禁：null/false profile → 不创建 contact/不选账号/不渲染/不投递，reason=EXPERT_NOT_SENDABLE） |
| 全量回归 gate（master 约束 4：mvn test 必须 BUILD SUCCESS） | CONFLICT | — | mvn test：Tests run: 2822, Failures: 3, Errors: 0, Skipped: 4 → BUILD FAILURE（3 项失败均为计划授权变更必然破坏的、不在授权清单内的测试，见 Deviations） |

### Commands

| Command | Result | Evidence |
|---|---|---|
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest | PASS | Tests run: 56, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=InitialOutreachServiceTest | PASS | Tests run: 13, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest | PASS | Tests run: 92, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test | FAIL | Tests run: 2822, Failures: 3, Errors: 0, Skipped: 4 → BUILD FAILURE；失败全部位于未授权测试文件，是计划规范变更的必然结果（详见 Deviations） |
| git diff --check | PASS | exit 0，无空白错误 |

### Changed Files

- src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt — companion 新增 `expertSendableFilter()`（逐字 `term expertClassification.sendable=true`，I3-2）；新增 `searchSendableExpertsWithEmail(size, level=CANDIDATE)`（exists email AND sendable term）；`searchExpertsWithEmail` 行为不变
- src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt — `sendInitialBatch` 目标查询改用 `searchSendableExpertsWithEmail`；forEach 首行最后门禁 `expert.expertClassification?.sendable != true` → skipped++/continue（I3-1/I3-4）
- src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt — `RecipientScope.matchesExpert` 顶部 INTRODUCTION 内存 sendable 门禁（MATERIAL_REMINDER 跳过，I3-2/I3-5）；`BatchOutcomeReasonCodes.EXPERT_NOT_SENDABLE` + label `专家非生产/科研可发类型`
- src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt — `buildEsFiltersForLevel` 对 INTRODUCTION 所有 funnel level 末尾追加 `expertSendableFilter()`（MATERIAL_REMINDER 不追加）；INTRODUCTION round loop 取回 profile 后、邮箱/账号/contact 处理前最后门禁：`recordSkipped(EXPERT_NOT_SENDABLE)` + processedTotal/roundProcessed/roundRejected 递增，更新进度后 continue（I3-4）
- src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt — expertSendableFilter 精确值、sendable 查询请求体（term + exists email + 层级排序）、显式 level、通用查询不带 term（4 个新测试）
- src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt — 全部桩切到 searchSendableExpertsWithEmail；expert() fixture 默认 sendable；新增最后门禁 2 测试（混合场景仅可发者发送、null 分类零写入）
- src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt — expert() fixture 默认 sendable；既有 filter 断言同步追加 sendable term（N3a-2/I4a-1/I4a-2/I4a-3/I4a-4/runScheduledBatch emailDomain+discipline/regions/discipline/operatorStatus 系列）；新增 I3-1 参数化、I3-2 内存/ES parity、I3-5 MATERIAL_REMINDER 零影响、I3-3 预估/执行同源、I3-4 最后门禁（null 与 false 两路）

### Deviations

无实现偏差：T1–T4 全部按计划在 7 个授权文件内落地，聚焦测试全绿。**但全量回归门被三个不在授权清单（brief「Authorized files (7; modify nothing else)」/计划「变更文件清单」）内的测试阻断**，其失败是计划授权变更的必然结果，修复必须修改未授权文件：

1. `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt:62`（EXCLUDED_NOISE_SITES）
   以 路径+行号+上下文 排除只读噪声；`ExpertSearchService.kt` 钉死行号 445（上下文 `operatorStatus = source.nullableText`，系 fast-p 01 A1 授权修正后的值）。
   计划 Task 1 授权修改 ExpertSearchService.kt，必须新增 `expertSendableFilter()` + `searchSendableExpertsWithEmail`（约 46 行，位于该读取点之前）→ 行号 445→491 → 排除失效 → 守卫把只读的 `toExpertProfile` 构造误报为 operator_status 写入点（实际命中文件多出 ExpertSearchService.kt）。
   修复需把钉死行号 445→491（唯一机械修正，context 不变）；守卫逻辑本身无需改动。本仓先例（batch-task-filters p3a brief M-5：「If it turns red from this plan's mapping lines, that is a HUMAN-authorized noise-site registration decision — return PLAN_CONFLICT instead of touching the guard」；fast-p 01 A1 同类修正）明确规定此类守卫刷新必须人工授权，执行 agent 不得触碰守卫文件。
2. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:228`
   `tags use OR within field discipline and provider use AND`：`assertTrue(scope.matchesExpert(expert("0001", ...)))`（INTRODUCTION scope）。
   计划 Task 3 强制 `RecipientScope.matchesExpert` 对 INTRODUCTION 先做内存 sendable 检查（I3-1/I3-2）→ 无分类 fixture 必然 false。修复需给 4 个 fixture profile 补充 `expertClassification`（sendable 类型），唯一机械 fixture 更新。
3. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:253`
   `retry path applies same scope filters as ES matcher`：`assertEquals(1, targets.size)`。
   同上：3 个 retryable fixture profile 无分类 → matchesExpert false → retryable=0。修复需给 3 个 profile 补充 sendable `expertClassification`（tags/domain/discipline 过滤语义不变，仍恰好命中 0001），唯一机械 fixture 更新。

执行 agent 未修改上述三个未授权文件。工作树其余改动=0；实现提交 f3da97a 仅含 7 个授权文件。

### Freshness

- Plan identity rechecked: YES（05dbebcb16814e7d6b993a7e9e4fe34a7386574fe9460fba73e96fd29efc3368，与执行前一致）
- Worktree identity rechecked: YES（root/branch/git-dir 与执行前一致；worktree_identity.py 因公共仓库存在失效的 /sessions/* 锁定 worktree 注册项而无法运行，已用其完全相同逻辑手工计算并记录，与 child 01 同况）
- Reported commits reachable from target branch: YES（f3da97a 为 fast/expert-rnd-classification 的 HEAD，父提交为 583744f）
- Required commands run this invocation: YES（三项聚焦测试命令、全量 mvn test、git diff --check 均本调用内新鲜执行）
- Historical evidence used only as baseline: YES（基线绿色来自 ledger；本调用失败复现均现场运行确认）

### Remaining Blocker

最小缺失授权：人工（或修订计划/子计划 brief）授权对以下三个**未授权测试文件**做机械性刷新（无逻辑变更）：
- `OperatorStatusWriteSeamGuardTest.kt` EXCLUDED_NOISE_SITES：`ExpertSearchService.kt` 钉死行号 445 → 491（context `operatorStatus = source.nullableText` 不变；M-5 先例：守卫刷新属人工授权决策）；
- `BatchSendTaskRuntimeIntegrationTest.kt`：`tags use OR within field discipline and provider use AND` 的 4 个 fixture profile 与 `retry path applies same scope filters as ES matcher` 的 3 个 fixture profile 补充 sendable `expertClassification`。

或由 controller 修订 brief 将这两个文件加入授权清单后再执行修复轮。

### Next Action

- PLAN_CONFLICT → 获取人工决策（授权上述机械刷新）或修订计划/授权清单；批准后执行机械修复并重跑 `mvn test`，即可翻绿进入 READY_FOR_VERIFICATION。实现主体（T1–T4）已完成并提交于 f3da97a，无需重做。
