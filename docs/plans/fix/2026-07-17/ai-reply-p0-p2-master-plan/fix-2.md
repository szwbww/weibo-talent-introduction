# fix-2：AI 回复 P0-P2 总计划第二轮整体复验

## 原计划 / 轮次

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`
- 上轮：`docs/plans/fix/ai-reply-p0-p2-master-plan/fix-1.md`
- 关联约束：`docs/plans/2026-07-16/ai-reply-review-authority-fail-closed.md`
- 实现基线：HEAD `7fc1011c` + 当前 worktree 的 fix-1 修复。
- 本轮只审计 fix-1 变更文件、新增文件及其跨计划边界；P1 数从 3 降为 1。

## 约束清单

- 公司身份 alias、coverage intent、运行时 QA keywords 必须闭合。
- 研究画像需求只由 intent catalog 决定；画像与 programme scope 缺一不可 READY。
- 高风险 phrase family 的答案侧与来源侧必须对称匹配。
- 首轮和 continuation 的 AI 草稿都必须有服务端持久化 authority；客户端 turns/source/confirmation 不可信。
- 无 authority、authority 损坏或 snapshot 不自洽必须在草稿暴露/投递前 fail closed。
- continuation 不新增 initial-draft 审计记录，并保持既有 identity/session 语义。
- 不修改已应用迁移；不恢复已删除临时 heuristic；不改自动回复路径。

## fix-1 关闭情况

| 上轮项 | Verdict | 证据 |
|---|---|---|
| P1-1 公司身份 alias 缺 QA candidate | ✅ | 新增 V77 仅追加 `full legal name/legal name/full name/company name`，带目标规则与防重复条件；`QaMatchServiceTest`、`AiReplyDraftServiceTest` 覆盖独立问法。 |
| P1-2 研究画像双 authority | ✅ | `AiReplyIntentCatalog` 声明 `requiresProfile`；`AiReplyContextService` 与 `AiReplyDraftService` 均消费 catalog 结果和显式 `researchProfileSufficient`，不再用 warning 反推。 |
| P1-3 高风险 family 非 key 成员绕过 | ✅ | `AiReplyHighRiskClaimValidator` 的答案侧与来源侧均遍历同一 family values，并共用边界安全、忽略大小写 matcher；单测与 DraftService fallback 集成测试齐全。 |

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 证据 |
|---|---|---|---|---|
| P1-1 | P1 | 任意直接/非官方客户端在尚无 initial-draft 审计时提交非空 `turns`，可稳定触发 | `turns` 完全由客户端提供。controller 对非空 turns 跳过 `recordInitialDraft()`，随后以 `authorityResult?.available ?: true` 将无权威 continuation 默认视为可用并返回 raw/rendered AI 正文及 null identity。调用方再以空 `replySource`、null confirmation 发送该正文时，发送校验因 latest draft 不存在而判为 `MANUAL`，形成完整 authority 绕过。 | `UnmatchedInboundMailController.kt:318-331,376-415,641-648`；`AiReplyReviewAuditService.kt:161-174`；`UnmatchedInboundAiReplyTurnKnowledgeTest.kt:541-583`；`ai-reply-review-authority-fail-closed.md:219-226`。 |

## 根因

1. controller 把“客户端声称是后续轮次”等同于“服务端已建立首轮权威”。
2. continuation 分支没有读取/验证 current persistent authority，也没有返回其 identity。
3. 发送 gate 必须允许真正的纯人工回复；因此一旦 AI 正文在无审计状态下被暴露，后续请求可伪装成纯人工，发送层无法追溯正文来源。
4. 现有测试只验证 continuation 不重复记录 initial draft，没有覆盖“无 existing authority 的伪造 continuation”。

## 修复规格

### P1-1：continuation 必须以服务端 current authority 为前置条件

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

要求：

1. 为 continuation 增加只读的服务端权威读取/校验入口；只接受当前 inbound 最新、可解析且 snapshot/readiness 自洽的 initial-draft authority，并返回其 draft identity。解析规则必须与发送 gate 共用，禁止复制第二套宽松 parser。
2. `AiReplyTurnRequest.turns.isNotEmpty()` 时，在生成或暴露任何 AI 正文前调用该入口。无记录、损坏记录、缺 identity 或 snapshot 不自洽时 fail closed；响应不得含 raw/rendered draft，preview 不得执行。
3. 合法 continuation 不新增 `AI_REPLY_DRAFTED` 事件，不改变质量指标分母；response 返回/保持 current server identity，前端既有 per-draft/session 语义不变。
4. 首轮继续走 `recordInitialDraft()` 的原子暴露规则；真正纯人工发送仍允许 blank source + null confirmation，不得用关闭人工路径掩盖 continuation 漏洞。
5. 不信任客户端 `sessionId`、turns 内容或旧 response identity 来证明 authority；权威只来自当前 inbound 的服务端 latest 记录。
6. 失败必须发生在 DraftService/preview 的可用正文离开服务端之前，发送 gate 的 fail-closed 校验继续保留，形成双边界防护。

定向测试：

- 无 latest authority + 非空 turns：拒绝或返回空正文/`draftAuthorityAvailable=false`；DraftService/preview 至少不得产生可采用正文。
- latest authority 损坏、缺 identity、snapshot/readiness 不自洽 + 非空 turns：全部拒绝。
- 合法 latest authority + 非空 turns：可继续生成，返回原 current identity，且不新增 initial-draft action。
- 首轮 authority 持久化失败仍为空正文；纯人工发送回归保持通过。
- 漏洞链回归：伪造 continuation 取得正文再以 manual payload 发送，必须在第一步已被截断，不能进入投递。

## 测试要求

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyReviewAuditServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest,PendingMailOperationServiceTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --check src/main/resources/static/app.js
node --test src/test/js/*.test.js
```

## 当前状态（修前）

- JVM：PASS — 1763 tests，0 failures，0 errors，4 skipped（1759 passed）。
- JS：PASS — 353 tests，0 failures，0 skipped。
- JS 语法：PASS。
- fix-1 三项：全部关闭。
- 功能/约束审计：FAIL — 1 个 P1；测试全绿不足以打开发布门。

## 总计划逐项合规审计

| 子计划 | Verdict | Evidence |
|---|---|---|
| 01 readiness / compound coverage | ✅ | request-group evidence 隔离、聚合 readiness、prompt facts 与 send IDs 分离；旧 partial heuristic 已删除。 |
| 02 company identity split | ✅ | V75/V76 保持不变；V77 增量补齐 catalog alias，独立/组合问法测试通过。 |
| 03 readiness UI | ✅ | 两入口展示服务端 generation/readiness/coverage，状态不进入正文。 |
| 04 coverage keys backend | ✅ | `QaCoverageKeyCatalog` 单源；管理读写、校验、V76 回填及 API metadata 闭合。 |
| 05 coverage keys UI | ✅ | metadata 原子加载；编辑、保存、重置与展示消费同一后端目录。 |
| 06 intent coverage matrix | ✅ | catalog 单源匹配；研究 intent 的 profile + programme scope 双证据约束闭合。 |
| 07 structured output / claim validation | ✅ | 首轮与 retry 均严格 materialize；高风险 family 对称校验，失败整次 fallback。 |
| 08 review authority backend | ❌ P1-1 | 首轮与发送 gate 正确，但伪造 continuation 可在无 persistent authority 时暴露 AI 正文。 |
| 09 review confirmation UI | ✅ | per-draft review state、不可变确认 payload、逐项 checkbox/note 和编号闸门齐全。 |
| 10 quality metrics | ✅ | 5 类服务端审计计数、零分母 rate、selected association 统计保持。 |

## 语义完整性检查

- Accumulation check：✅ 质量指标按服务端 action log 时间窗直接 count，无跨刷新累计。
- State machine check：❌ `NO_AUTHORITY -> forged continuation -> AI body exposed -> MANUAL send` 是可达非法状态。
- Cross-plan check：❌ Phase 08 的 continuation 边界与后续 fail-closed 计划的服务端 authority 约束未闭合。
- Deleted code：✅ `isPartialCoverage/PARTIAL_DETAIL/TEMPORARY_COMPOUND` 等临时路径未恢复。
- No extras：✅ fix-1 业务变更均对应上轮 3 项；本轮仅写 fix-2、知识条目与命中元数据，不改业务代码。

## 轮次收敛 / 发布结论

- fix-1：3 个 P1。
- fix-2：1 个 P1，严格下降；且问题位于 fix-1 已变更的 `UnmatchedInboundMailController.kt`，符合后续轮次文件范围。
- 原计划已明确要求伪造 continuation 无 authority 时拒绝，属于实现缺陷，不修订原计划。
- 当前不得判定 P0-P2 总计划整体通过。完成本 fix-2 后再次调用 `fix-v`；要求 P1=0 才可关闭发布门。
