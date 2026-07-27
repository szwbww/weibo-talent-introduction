# 修复计划：AI 回复第 5 步信任边界与草稿状态（第 2 轮）

## 原计划 / 子计划引用

`docs/plans/2026-07-19/ai-reply-05-trust-boundary-readiness-plan.md`

上轮：`docs/plans/fix/2026-07-19/ai-reply-05-trust-boundary-readiness-plan/fix-1.md`（5 个 P1）。本轮仅复验其修复涉及的文件；发现 4 个 P1，数量严格下降，继续收敛。

## 约束摘录

- I-1：Grounded claim 的唯一事实正文是对应、启用且非空的 `answerBody`；不得使用 `replyBody`、变体或其他字段回退。
- I-2：enterprise 来源明确“尚未匹配”时，正文必须保留同族不确定性，且不得宣称已有具体企业。
- I-3：仅阻断敏感材料的直接索取；否定、流程说明和非请求提及不得误判。
- I-4 / I-6：任一未授权动作属于确定性失败，必须进入唯一一次修复；第二次失败 fallback 并 `BLOCKED`。
- I-7 / I-8：warning 为稳定 code；自动决策优先将 action/claim validation 归为 `AI_REPLY_VALIDATION_FAILED`。

## 修正记录

| P1 | 问题 | 触发频率 |
|---|---|---|
| P1-1 | `enterprise.*` 仅拒绝 certainty，未要求正文保留来源中的未匹配不确定性 | 每次企业事实为“尚未匹配”、模型以模糊语句省略该限制时 |
| P1-2 | `displayName` 被拼入 claim 的批准 sourceText | 每次运营标题含正文未批准的数字、URL 或高风险声明且模型复述时 |
| P1-3 | 否定敏感材料请求被误当作直接 CTA | 安全说明包含“不索要护照/身份证”等表述时 |
| P1-4 | 无 code 的未授权 meeting/material CTA 未进入 Grounded validation/retry | LLM 生成未获 server plan 授权的会议或普通材料 CTA 时 |

## 修复规格

### P1-1：强制保留企业未匹配边界

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`、对应测试。
- 修改：当 enterprise claim 的 `answerBody` 命中 uncertainty family 时，答案必须命中同族不确定性；同时保留 certainty family 拒绝。检查仍以该 claim 的 `answerBody` 为准。
- 预期：`The enterprise has not yet been matched.` 不能被 `We will share details later.` 或任何具体企业断言替代；`A specific enterprise will be disclosed after matching.` 通过。

### P1-2：claim 校验仅使用 answerBody

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`、对应测试。
- 修改：`resolveSourceText()` 仅拼接各规则的非空 `answerBody`，不纳入 `displayName`；保留 rule 存在、enabled、body 非空的 fail-closed 行为。
- 预期：标题中的 `RMB 500,000`、URL 或“no fees”不能让正文同类声明通过；相同内容在 answerBody 中仍可通过。

### P1-3：区分否定提及与敏感材料索取

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`、对应测试。
- 修改：敏感 matcher 先识别否定/说明语义（如 `do not request a passport`），仅当 sensitive token 处于正向直接索取 CTA 的作用域时返回 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`；否定不得掩盖同单元后续的正向索取。
- 预期：`We do not request a passport at this stage.` byte-identical 且无 violation；`Could you send your passport?`、中文祈使/疑问/委婉索取仍失败并被删除。

### P1-4：所有 action violation 进入一次统一修复

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`、`src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt`、对应测试。
- 修改：`materializeAndValidateGroundedCandidate()` 对每个 `ActionViolation` 生成稳定 validation code；无专用 code 的未授权动作复用 `UNAUTHORIZED_ACTION_REMOVED` 或映射为内部稳定 action code，并让 `hasValidationFailure()` 识别它。
- 预期：未授权 meeting/CV CTA 首次触发一次 correction；第二次仍失败时总调用数为 2、结果为 `FALLBACK_NO_RESPONSE + BLOCKED`，自动原因为 `AI_REPLY_VALIDATION_FAILED`；不新增 action enum、DTO 或状态。

## 当前状态（修复前）

- 编译：PASS — `mvn clean test` 成功完成编译；`git diff --check` PASS。
- Maven 测试：PASS — 1,749 passed, 0 failed, 4 skipped。
- JS 测试：PASS — 336 passed, 0 failed, 0 skipped。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 只读 answerBody | ❌ | `AiReplyHighRiskClaimValidator.kt:168-172` 将 `displayName` 拼入 `sourceText`。 |
| I-2 信任修辞、角色、企业不确定性 | ❌ | `AiReplyHighRiskClaimValidator.kt:112-115` enterprise 仅拒绝 certainty，未要求 uncertainty family 保留。 |
| I-3 敏感材料只阻断直接索取 | ❌ | `AiReplyActionPolicy.kt:244-251` 任何含 `request` 的敏感材料否定句均返回 violation。 |
| I-4 server plan 动作与 CV 条件 | ❌ | `AiReplyDraftService.kt:473-480` 未授权 action 的 `code=null` 未进入 warnings。 |
| I-5 三态规则 | ✅ | `AiReplyDraftService.kt:804-853` critical/unknown 为 BLOCKED，已分类 noncritical UNSUPPORTED 为 NEEDS_REVIEW。 |
| I-6 至多一次统一修复 | ❌ | `AiReplyDraftService.kt:473-480` 漏掉无 code action 后直接进入 valid 分支，跳过 correction。 |
| I-7 稳定、无正文 warning code | ✅ | `AiReplyDraftService.kt:599-617` correction 仅传 code 与允许动作；`AiReplyHighRiskClaimValidator.kt:269-277` 为稳定 claim code。 |
| I-8 validation reason 优先 | ✅ | `GroundedAutoReplyDecisionService.kt:99-109` 先判 validation，再判 rule IDs/gap。 |
| I-9 JSON/段落/sourceIds 契约 | ✅ | `AiReplyDraftService.kt:441-489` 两次候选都经 strict materialize 后才做 claim/action；`AiReplyGroundedDraftMaterializer.kt:246-339` 继续严格校验。 |
| I-10 人工发送解耦 | ✅ | `PendingMailOperationService` 未在本计划/修复范围内修改；JS direct-send 336 测试通过。 |
| Deleted code | ✅ | Grounded 路径未调用 `enforceActionPolicy()`；仅 FREE_FORM 保留该路径。 |
| No extras | ⚠️ | 原工作树仍含 materializer/composer/controller test 等计划外改动；本轮未把它们纳入 P1 或修复范围。 |

### 语义完整性

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ N/A，无新状态机。
- Cross-plan check：❌ error-then-recovery — 未授权 Grounded action 未进入统一 correction，绕过第 5 步 generate → decision contract（P1-4）。
