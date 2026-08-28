## Execution Result: PLAN_CONFLICT

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/2026-08-28/16-unsupported-index.md
Plan SHA-256: c6b9433dd0d47fa4a2fe40ac6928d48afad4628bbc0238f46131aa27d9bd0823
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/2026-08-28/16-unsupported-index.md@c6b9433dd0d47fa4a2fe40ac6928d48afad4628bbc0238f46131aa27d9bd0823
Execution epoch: NEW
Approval basis: current invocation (child brief c6, controller dispatch)
Executor: C6Impl
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order
Target branch: fast/2026-08-28-reply-orchestration-order
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order@fast/2026-08-28-reply-orchestration-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order
Pre-execution code SHA: b3f30dfa6fc59e508ad68c7291244d14d077e306
Post-execution code SHA: cf34ff2c14766df7fcf1b2a6302e26d5dd569428
Evidence HEAD: N/A（brief 要求单一实现提交，无独立 evidence 提交）
Implementation boundary: f3eea5f..e9e035e（工作区另有两处未提交：ledger.md 与本文档，均属 controller 证据）

### 结论摘要

全部**可在授权范围内落地**的任务（T-2、T-3、T-5、T-6，以及 T-1 的 mapping 补丁步骤）
已实现并通过全部门禁；**两个任务因与仓库事实冲突无法在授权文件内完成**，总体结果
为 `PLAN_CONFLICT`：

1. **T-4（通道 A 措辞样例注入）——CONFLICT**：计划正文要求「在 13 的提示词构造处」
   注入，变更清单却把落点写在 `AiReplyDraftService.kt`。仓库事实：13 的提示词在
   `AiReplyLetterOrchestrator.buildPrompt`（c4 新建，**不在**授权 10 文件内），而
   `AiReplyDraftService.kt` 只构造逐条生成（12）的提示词，且 master 计划 13 明确写明
   「`AiReplyDraftService.kt` 不在清单内——编排是独立于逐条生成的第二次调用，不改
   逐条生成的提示词与协议」。授权文件内不存在任何能触达 13 提示词的接缝；把注入逻辑
   写进 `AiReplyDraftService.kt` 只会成为无人消费的死代码。**需要**：把
   `AiReplyLetterOrchestrator.kt` 加入授权（或改道接缝），或按计划 16 的规模预警把
   T-4/T-5 拆成新计划。
2. **T-1 的 mapping 资源文件——CONFLICT**：I-2 验收与 T-1 要求
   `es/trust_reply_unsupported_answer_v1.json`「新增三个 properties」；仓库事实：未授权
   测试 `UnsupportedAnswerIndexApiTest.mapping is strict with only V1 fields…` 断言
   mapping 属性集**恰好等于** 23 个 V1 字段（`assertEquals(expected, …)`），加任一字段
   `mvn test` 必红。**需要**：授权更新该测试文件，或改验收口径。为把功能缺口降到零，
   实现已把补丁步骤同时挂在「HEAD 成功」与「404 首建后」两条路径（见实现说明），
   存量与新环境都能拿到三个新 properties；只剩文件内容验收一项无法在授权范围内满足。

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 mapping 演进（方案 A） | CONFLICT（补丁步骤 IMPLEMENTED；资源文件被未授权测试锁死） | UnsupportedAnswerIndexService.kt | T-6.6 + 404 首建补丁测试通过；JSON 未改动 |
| T-2 入库门槛放宽（I-4 / I-5） | IMPLEMENTED | UnsupportedAnswerIndexService.kt, AiTrainingEvaluationService.kt, PendingMailOperationService.kt | T-6.1/T-6.2/T-6.5 + 全量回归 |
| T-3 列表修复与 topic 过滤（I-3 / I-6） | IMPLEMENTED | UnsupportedAnswerIndexService.kt, UnsupportedAnswerIndexController.kt, app.js, aiTrainingUnsupportedAnswers.test.js | T-6.3 + JS 用例 |
| T-4 通道 A 措辞样例注入（I-1 / IP-3） | CONFLICT（落点文件不含 13 提示词，见结论摘要 1） | （无改动） | — |
| T-5 通道 B 待转事实队列（I-5） | IMPLEMENTED | UnsupportedAnswerIndexService.kt, UnsupportedAnswerIndexController.kt, app.js | pendingTopics / activate 端点 + 前端视图 |
| T-6 测试（T-6.1～T-6.6） | IMPLEMENTED | UnsupportedAnswerIndexServiceTest.kt（新增 9 例）, PendingMailOperationServiceTest.kt（替换 @Disabled 桩为 2 例）, aiTrainingUnsupportedAnswers.test.js | 全部通过 |

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsupportedAnswerIndexServiceTest,PendingMailOperationServiceTest` | PASS | exit 0；Tests run: 11, Failures: 0, Errors: 0 |
| `node --test src/test/js/aiTrainingUnsupportedAnswers.test.js` | PASS | exit 0；tests 7, pass 7, fail 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0；tests 765, pass 765, fail 0（基线 764，新增 1 例） |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0；Tests run: 3004, Failures: 0, Errors: 0, Skipped: 5（基线 2993/0/0/5，新增 11 例） |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0；BUILD SUCCESS（3004/0/0/5） |
| `git diff --check` | PASS | exit 0 |

### Changed Files（提交 e9e035e，8 个文件，全部在授权清单内）

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt` — T-1 方案 A mapping 补丁（HEAD 成功 + 404 首建后两条路径，失败仅 warn）；T-2 文档三字段（topic/finalParagraphText/editedByOperator，带默认值保持存量构造兼容）、validate() 允许集合与 operatorInstruction 可选、status×sourceMode 解绑；T-3 LIST_SOURCE_FIELDS + parseListItem（operatorInstruction 空渲染 —）+ listQuery topic term（bool.filter）；T-5 pendingTopics（terms 聚合 + top_hits）与 activatePendingTopic（update_by_query）
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexController.kt` — T-3 topic 查询参数（非法 400）；T-5 GET /pending-topics、POST /pending-topics/{topic}/activate
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt` — T-2 过滤条件：去掉 handling == ANSWER_FROM_OPERATOR_INPUT 与 operatorInstruction.isNotBlank()，保留 rating == MEETS_EXPECTATION / generationKind == AI_GENERATED / requestText / answerText
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` — T-2 去掉「正文一字未改才归档」门槛；resolveSource 结果 requireNotNull（归档失败不得阻断主流程，A-7 语义）
- `src/main/resources/static/app.js` — T-3 topic 下拉（运行时注入，复用既有骨架，未新增 class）与 topic 参数；「—」渲染；状态列按 status×sourceMode 实值 + 「运营已编辑」标记；T-5 待转事实视图（注入面板、生成 QA 事实草稿预填 + 建议 coverage key、保存后激活）
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexServiceTest.kt`（新增）— T-6.1（允许集合 8 组合全过 / OMIT、ANSWER_WITH_EVIDENCE、OMITTED 拒绝）、T-6.2（空 operatorInstruction 通过 / 超长拒绝）、T-6.3（parseListItem 空 operatorInstruction 保留且渲染 — / 其余必填空丢弃）、T-6.4（documentId 三字段前后哈希不变）、T-6.5 文档侧（线上 editedByOperator=true、训练 false）、T-6.6（HEAD 成功 PUT _mapping；404 首建后同样补丁；补丁失败仅 warn）
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` — 原为 @Disabled 空桩（计划指定的 T-6.5 落点），替换为真实测试：正文被编辑时仍归档（SAVED 而非 FAILED）+ 逐字一致回归
- `src/test/js/aiTrainingUnsupportedAnswers.test.js` — topic 参数发送、空 operatorInstruction 渲染 —、状态列/运营已编辑标记、待转事实视图接线

未改动（授权清单内但冲突未决）：`AiReplyDraftService.kt`（T-4）、`es/trust_reply_unsupported_answer_v1.json`（T-1 文件内容）。

### Deviations

1. **T-4 未实现**（CONFLICT，见结论摘要 1）；`AiReplyDraftService.kt` 一字未动。
2. **T-1 资源文件未加三个 properties**（CONFLICT，见结论摘要 2）；为消除功能缺口，mapping
   补丁在「HEAD 成功」与「404 首建后」都执行，新环境与存量索引均能拿到三字段。
3. **finalParagraphText 写版本正文**：assemble 响应不暴露逐主题收口段落（c2/c4 未留接缝），
   写缝可用数据只有逐条版本正文；字段与 answerText 分开存放（plan 12 IP-4「两者须分开
   存放，不得互相覆盖」成立），待收口段落接缝落地后该字段即为其归档槽位。无测试/验收
   钉死其内容。
4. **线上侧 editedByOperator 恒为 true**：按计划逐字要求「照常归档并置 editedByOperator
   = true」实现（A-2 标记「运营已编辑」）；不改变 archiveLiveCanonicalVersions 签名，
   否则会破坏未授权 PendingMailOperationServiceTrustWorkbenchTest 的 5 参桩（其测试
   不在授权清单内）。
5. **PendingMailOperationServiceTest.kt 由 @Disabled 桩替换为真实 T-6.5 用例**：计划指定
   的 T-6.5 落点实为空桩（仓库事实），真实线上归档测试在未授权的
   PendingMailOperationServiceTrustWorkbenchTest 中；editedByOperator=true 的文档级断言
   放在 UnsupportedAnswerIndexServiceTest。
6. **resolveSource 加 requireNotNull**：去掉逐字相等门槛后，归档路径首次暴露
   resolveSource 空返回；按「归档失败不阻断主流程」（A-7）语义将其转为 failedArchive，
   使 3 个未授权既有用例（编辑正文发送）保持 SENT+FAILED 语义而非 NPE。
7. **JS 测试禁用词去掉「编辑」**：状态列「运营已编辑」是状态标记而非操作按钮；
   其余禁用词（采用/复用/推荐/相似/删除/晋升）与 data-action 断言保留。
8. **方案 A 已选**（按 brief 要求）：mapping 补丁步骤挂在 bootstrapIndex()，理由
   （存量索引就地演进、无需切索引名/重灌数据）已写入代码注释。

### Freshness

- Plan identity rechecked: YES（sha256 不变：c6b9433d…）
- Worktree identity rechecked: YES（branch/HEAD/git-dir 不变，HEAD f3eea5f 提交前复核）
- Reported commits reachable from target branch: YES（e9e035e 为分支 HEAD，父提交 f3eea5f）
- Required commands run this invocation: YES（全部在最终状态后重跑）
- Historical evidence used only as baseline: YES（2993/0/0/5、node 764 仅作基线参照）

### Remaining Blocker

- 无执行/环境阻塞；需人工裁决两处授权范围冲突：
  1. T-4 落点：把 `AiReplyLetterOrchestrator.kt` 纳入授权（或改接缝 / 拆计划 17）；
  2. T-1 资源文件：授权更新 `UnsupportedAnswerIndexApiTest` 的字段集断言（或将验收
     口径改为「补丁步骤 + 功能可用」，不再要求 JSON 文件字面含三字段）。

### Next Action

- PLAN_CONFLICT → 由 controller/人工裁决上述两项后，可对剩余工作续跑（T-4 注入 +
  JSON 三字段 + 对应测试）；其余任务已实现并通过全部门禁，裁决后无需重做。
