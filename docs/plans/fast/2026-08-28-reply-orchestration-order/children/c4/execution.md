# Execution Report — c4 · 13-letter-orchestrator（一次编排 LLM 调用 + 六道校验）

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/2026-08-28/13-letter-orchestrator.md
Plan SHA-256: 249ceb07fd3da0081f35736f66caf85523be074b445ceb26b5bdc1ad39d54f6f
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/2026-08-28/13-letter-orchestrator.md@249ceb07fd3da0081f35736f66caf85523be074b445ceb26b5bdc1ad39d54f6f
Execution epoch: NEW
Approval basis: current invocation（brief = 完整批准契约，`docs/plans/fast/2026-08-28-reply-orchestration-order/children/c4/brief.md`）
Executor: C4Impl
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order
Target branch: fast/2026-08-28-reply-orchestration-order
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order@fast/2026-08-28-reply-orchestration-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order
Pre-execution code SHA: 764d1a214c5b7cc5dbdb84af812350561a12f9de
Post-execution code SHA: 889210e339c3c5dd2533777d35076bdfc5c65793
Evidence HEAD: N/A（证据由控制器单独提交，本次只提交实现）
Implementation boundary: 62edb0b..889210e339c3c5dd2533777d35076bdfc5c65793（5 个授权文件）

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 paragraphPlan/PlanFact 构造（f<ruleId>/x<n>、groupIdOf、frozen、>4 拆分、gapCondition） | IMPLEMENTED | AiReplyLetterCloser.kt | `buildPlan`/`splitTopic`/`factIdOf`；缺口挂 `gapCondition`（I-6） |
| T-2 编排调用（提示词内联 plan/事实/topicOrder/gap/动作授权，复用 AiReplyTimeoutPolicy + 取消，失败 → null） | IMPLEMENTED | AiReplyLetterOrchestrator.kt | `orchestrate`/`buildPrompt`/`executeCall`（budget + cancellationToken + observed stream） |
| T-3 六道服务端纯函数校验 + 5 个新校验码 | IMPLEMENTED | AiReplyLetterOrchestrator.kt、AiReplyValidationDiagnostic.kt | 解析器内 G1..G6；新增 ORCH_FACT_ID_UNKNOWN / ORCH_REQUIRED_FACT_COUNT_INVALID / ORCH_VERBATIM_BODY_MISSING / ORCH_ACTION_IN_PARAGRAPH / ORCH_PLAN_MISMATCH；G5 复用既有 ACTION_* 码 |
| T-4 接入：第 3 步先试编排、失败退回确定性归并（I-8），CTA 收口两路径都执行 | IMPLEMENTED | AiReplyLetterCloser.kt | `close` 第 3 步 → `orchestratedGroupsOrFallback`；第 4 步 `reconcileCta` 不变 |
| T-5 测试（六道失败+通过、受控逐字取自常量、冻结 id 1/3/21 真实正文、编排 null 退回带 warning、ORCH_PLAN_MISMATCH） | IMPLEMENTED | AiReplyLetterOrchestratorTest.kt、AiReplyLetterCloserTest.kt | 19 + 2 用例全绿；I-4/IP-2 grep 断言 0 硬编码 |

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyLetterOrchestratorTest,AiReplyLetterCloserTest` | PASS | exit 0；OrchestratorTest 19/0/0/0、LetterCloserTest 9/0/0/0（surefire） |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0；`Tests run: 2991, Failures: 0, Errors: 0, Skipped: 5`；node 760 全绿 |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0；target/weibo-talent-introduction-1.0.0-SNAPSHOT.war 构建成功 |
| `git diff --check` | PASS | exit 0 |

### Changed Files
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterOrchestrator.kt` — 新增：PlanFact/ParagraphPlanEntry/OrchestratedParagraph/OrchestratedLetter/OrchestrationAttempt 协议 + 一次编排 LLM 调用 + 六道校验（G1..G6）纯函数解析器（T-2/T-3）
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterCloser.kt` — 修改第 3 步：构造 paragraphPlan → 试编排，失败退回确定性归并（I-8）；第 1/2/4/5 步原样保留（T-1/T-4）
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyValidationDiagnostic.kt` — 恰好新增 5 个 ORCH_* 校验码常量（T-3）
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterOrchestratorTest.kt` — 新增：T-5.1~T-5.3、T-5.5（19 用例）
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterCloserTest.kt` — 新增 T-5.4（编排 null 退回 + warning；另加编排成功路径用例）

### Deviations
- None。5 个授权文件之外未改任何实现/测试/计划文件；`docs/plans/fast/**` 未触碰；`AiReplyDraftService.kt` 未修改（接缝无需它）。
- 说明（非偏差）：G2「0 次」用例必然与 G6（ORCH_PLAN_MISMATCH）同时命中——段内 factIds 集合与 plan 不符时来源封闭/集合检查先于计数检查触发；验收只要求该码被触发，两码都会进入日志。

### Freshness
- Plan identity rechecked: YES（sha256 不变：249ceb07fd3da0081f35736f66caf85523be074b445ceb26b5bdc1ad39d54f6f）
- Worktree identity rechecked: YES（branch/git_dir/HEAD=62edb0b 不变）
- Reported commits reachable from target branch: YES（见实现提交）
- Required commands run this invocation: YES
- Historical evidence used only as baseline: YES（2952 seed / 2970 c3 head 仅作基线）

### Remaining Blocker
- None

### Next Action
- READY_FOR_VERIFICATION → run `verify-p`

## 验收判据对照

- I-1：`paragraph topics not matching topic order fails plan consistency` + `paragraph count not matching plan fails plan consistency` 通过（ORCH_PLAN_MISMATCH）；pass 用例断言 `paragraphs.size == paragraphPlan.size`。
- I-2：`unknown fact id fails source closure` 通过（ORCH_FACT_ID_UNKNOWN）。
- I-3：`required fact appearing zero times` / `required fact appearing twice` 均通过（ORCH_REQUIRED_FACT_COUNT_INVALID）。
- I-4：`rewritten controlled body fails verbatim` + 冻结 id 1（占位符改写）/ id 3（`--`→`—`）/ id 21（`15–20` en dash→连字符）失败用例通过；期望串全部取自 `QaCoverageKeyCatalog.controlledGroups()` / `PlanFact.body`（IP-2）；grep `"Your materials are kept strictly"` 于测试文件 = 0。
- I-5：`action sentence in paragraph fails`（ORCH_ACTION_IN_PARAGRAPH）+ `frozen meeting body verbatim passes with frozen CTA exemption` 通过；G5 复用 ACTION_TEXT_INVALID / ACTION_NOT_ALLOWED / ACTION_BODY_MISMATCH。
- I-6：`gap condition entry is honored with matching paragraph count` 通过；缺口经 `gapCondition` 挂在主题条目上。
- I-7：六道全部为解析器内纯函数；`grep -c "AiReplyValidationCodes\.\|ORCH_" AiReplyLetterOrchestrator.kt` = 34（覆盖全部六道）。
- I-8：`orchestration null falls back to deterministic closure with warning` 通过（输出 == 纯确定性结果，日志含 13-letter-orchestrator warning）。
- 回归：`mvn test` 2991/0/0/5、node 760、`mvn clean package` 全绿；12 的 LetterCloser 7 个既有用例原样通过。
