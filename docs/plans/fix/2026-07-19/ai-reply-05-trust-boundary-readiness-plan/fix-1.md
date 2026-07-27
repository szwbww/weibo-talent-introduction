# 修复计划：AI 回复第 5 步信任边界与草稿状态

## 原计划引用

`docs/plans/2026-07-19/ai-reply-05-trust-boundary-readiness-plan.md`

## 约束摘录

- I-2：服务机构角色必须保留；企业来源明确“尚未匹配”时，正文必须保留不确定性，不能强化为已有具体企业。
- I-3：仅阻断敏感材料的直接索取；流程说明或非请求表述不得误判。
- I-5：关键/未知 UNSUPPORTED、空 evidence、NEVER、修复耗尽才为 `BLOCKED`；已分类非关键 UNSUPPORTED 为 `NEEDS_REVIEW`。
- I-6：二次确定性校验失败走 fallback，附 `AI_REPLY_TRUST_REPAIR_EXHAUSTED`，且状态为 `BLOCKED`。
- I-8：自动决策先识别 validation warning；有校验失败时固定 `AI_REPLY_VALIDATION_FAILED`。

## 修正记录

| P1 | 问题 | 触发频率 |
|---|---|---|
| P1-1 | `enterprise.*` 不确定性校验位于 `agency/company` 分支内部，永不可达 | 每次企业来源含未匹配事实且模型强化为已匹配企业 |
| P1-2 | 任意已分类非关键 `UNSUPPORTED` 仍被标为 `BLOCKED` | 每次只有 enterprise 等非关键事实缺失 |
| P1-3 | 两次校验失败后的完整事实 fallback 可返回 `READY` | LLM 连续两次违反结构、claim、trust 或 action 门禁时 |
| P1-4 | 自动决策在 validation warning 前以空 `qaRuleIds` 返回 `QA_NO_MATCH` | 无可发送证据的 grounded 请求同时产生校验失败时 |
| P1-5 | 敏感材料 matcher 把任意提及都当作直接索取 | 正文解释“不索要护照”等非请求表述时 |

## 修复规格

### P1-1：企业不确定性校验可达

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`
- 修改：将 `enterprise.*` 的来源不确定性检查从 `agency/company` 角色分支拆出，按其自身 claim 执行；来源命中 uncertainty family 时，正文必须保留同族不确定表达，或拒绝 certainty family。
- 预期：`enterprise.*` 来源写“尚未匹配”而草稿写具体已匹配企业时，候选 invalid 并含 `AI_REPLY_CLAIM_ENTERPRISE_UNGROUNDED`。
- 测试：新增正反例，覆盖中文和英文同族；不得改变角色披露检查。

### P1-2：非关键缺口进入 NEEDS_REVIEW

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- 修改：删除/收窄对全部 `UNSUPPORTED` 的无条件 `BLOCKED` 分支；仅 blocking trust 或空 intents 保持 `BLOCKED`，已分类非关键 `UNSUPPORTED` 返回 `NEEDS_REVIEW`。
- 预期：`enterprise.matching` 缺失且被分类时不自动发送，但状态为 `NEEDS_REVIEW`；critical/unknown 仍 fail closed。
- 测试：替换现有“any UNSUPPORTED = BLOCKED”断言，覆盖 critical、unknown、noncritical 三类。

### P1-3：修复耗尽强制 BLOCKED

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- 修改：将 validation/action failure（包括 `AI_REPLY_TRUST_REPAIR_EXHAUSTED`）作为 readiness 输入或在 exhaustion 返回分支显式设为 `BLOCKED`；不得仅按 request facts 重算。
- 预期：完整 AUTO 事实下两次模型失败仍返回 `FALLBACK_NO_RESPONSE + BLOCKED`，不能成为 READY；LLM disabled/客户端不可用的正常 deterministic fallback 保持既有状态语义。
- 测试：分别以 trust rhetoric、sensitive action 和 malformed JSON 连续两次失败断言两次调用、warning 及 `BLOCKED`。

### P1-4：validation reason 优先

- 文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt`
- 修改：`resolveReason()` 的第一项业务判断改为 `hasValidationFailure(contextWarnings)`，再处理空 rule IDs、policy 与 grounding gap。
- 预期：任意 validation warning（即使 `qaRuleIds` 为空）都返回 `AI_REPLY_VALIDATION_FAILED`；无 validation 的空证据维持 `QA_NO_MATCH/QA_GROUNDING_GAP` 既有语义。
- 测试：新增空 `qaRuleIds + AI_REPLY_CLAIM_*` 和空 `qaRuleIds + 无 warning` 对照。

### P1-5：区分敏感提及与直接 CTA

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`
- 修改：敏感材料 pattern 仅在与请求动词/疑问/委婉 CTA 同一 token 单元组成直接索取时返回 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`；`detectActions`、`findViolations`、`sanitize` 共享该判定。
- 预期：`We do not request a passport at this stage.` 原文不变且无 violation；`Could you send your passport?` 仍被拒绝/删除。
- 测试：中英文祈使、疑问、委婉请求及否定/流程描述各一组，保留 byte-identical 回归。

## 当前状态（修复前）

- 编译：PASS — Kotlin 主/测试 classes 已生成，未见编译错误；`git diff --check` PASS。
- 定向 Maven 测试：PASS — 193 passed, 0 failed, 0 skipped。
- JS 测试：PASS — 336 passed, 0 failed。
- 全量 Surefire：环境受 PPID checker 中断（`Cannot use PPID ... process information`）；无断言失败输出。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 来源只读 answerBody、规则可用性 | ✅ | `AiReplyHighRiskClaimValidator.kt:153-174` 逐 ID 检查存在、enabled 与非空 answerBody。 |
| I-2 信任修辞/角色/企业不确定性 | ❌ | `AiReplyHighRiskClaimValidator.kt:105-116` 将 enterprise 检查嵌在 agency/company 条件内；`enterprise.*` 永不可达。 |
| I-3 敏感材料只阻断直接索取 | ❌ | `AiReplyActionPolicy.kt:128-135,234-238` 单元内任何 sensitive token 即产生 violation，未要求 CTA。 |
| I-4 server plan 动作与 CV 条件 | ✅ | `AiReplyActionPolicy.kt:113-121,244-259` 收缩 blocking trust 的材料动作并校验 CV purpose/optionality。 |
| I-5 三态规则 | ❌ | `AiReplyDraftService.kt:815-817` 无条件阻断所有 UNSUPPORTED。 |
| I-6 至多一次统一修复 | ❌ | `AiReplyDraftService.kt:404-421,576` 修复耗尽虽写 warning，却只按 facts 计算 readiness，可为 READY。 |
| I-7 稳定、无正文 warning code | ✅ | `AiReplyHighRiskClaimValidator.kt:269-277` 定义稳定 claim codes；`AiReplyDraftService.kt:595-613` 修复提示仅传 code/allowed actions。 |
| I-8 validation reason 优先 | ❌ | `GroundedAutoReplyDecisionService.kt:99-104` 空 qaRuleIds 在 validation warning 前返回 QA_NO_MATCH。 |
| I-9 JSON/段落/sourceIds 契约 | ✅ | `AiReplyGroundedDraftMaterializer.kt:246-339` 严格字段、顺序、ID integral/range/subset 校验。 |
| I-10 人工发送解耦 | ✅ | 本计划清单外的 `PendingMailOperationService` 未改；`src/test/js/aiDraftReviewState.test.js` 直接发送契约通过。 |
| Deleted code | ✅ | Grounded 路径未再调用 `enforceActionPolicy()`；`AiReplyDraftService.kt:222-240,638-718` 仅 FREE_FORM 使用它。 |
| No extras | ❌ | 当前 diff 含计划清单外 materializer/composer 与 controller test；作为观察需人工确认其是否属于第 4 步遗留。 |

### 语义完整性

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ N/A，无新状态机。
- Cross-plan check：✅ 第 4/5 步 JSON 与 action contract 可连通；本轮 P1 均在第 5 步实现内。
