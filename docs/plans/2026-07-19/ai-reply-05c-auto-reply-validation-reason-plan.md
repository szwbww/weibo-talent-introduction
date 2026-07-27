# AI 回复第 5C 步：自动回复 validation reason

## 需求描述

- 可观察结果：Grounded 草稿含 `UNAUTHORIZED_ACTION_REMOVED` 时，自动回复/预览统一给出 `AI_REPLY_VALIDATION_FAILED`，不误归类为普通 grounding gap 或 no match。

必须保持不变：

1. validation warning 的判断继续先于空 rule ID、policy、grounding gap 与 generation unavailable。
2. 无 validation warning 的 `QA_NO_MATCH`、`QA_POLICY_REVIEW`、`QA_GROUNDING_GAP`、`AI_GENERATION_UNAVAILABLE` 语义不变。
3. `passesSendGate()` 的 exact AUTO evidence、READY、LLM_USED、非空正文和无 PARTIAL/UNSUPPORTED 要求不放宽。

不在本计划范围：

- 产生/清理 action violation 的 matcher。
- `AiReplyDraftService` 的 retry、fallback 或 readiness 计算。
- preview UI、邮件投递、审计或 schema。

## 关键不变量

### Invariant I-1: 所有稳定 action validation warning 同类归因
- Rule：`AI_REPLY_ACTION_*`、`AI_REPLY_TRUST_REPAIR_EXHAUSTED` 与 legacy stable `UNAUTHORIZED_ACTION_REMOVED` 均属于 validation failure；`resolveReason()` 必须返回 `AI_REPLY_VALIDATION_FAILED`。
- Applies to：`GroundedAutoReplyDecisionService.hasValidationFailure()` 与 `resolveReason()`。
- Violation consequence：不安全 LLM 输出被伪装成普通无匹配/缺口，运营无法区分模型校验失败。
- 来源：K-validation-reason-before-no-match、K-grounded-action-violation-must-retry。

### Invariant I-2: 发送门禁不因 reason 修正放宽
- Rule：reason 归因变化不得改变 `passesSendGate()`；任何 validation failure 都不得 readyToSend。
- Applies to：`decide()`、`resolveReason()`、`passesSendGate()`。
- Violation consequence：为了改状态文案而意外允许自动外发。
- 来源：K-preview-mirrors-pipeline。

## 现状审计

### Grounded 自动回复 decision
- Schema/mapping：`AiReplyDraftResult.contextWarnings` 是上游传入的稳定字符串 code；`GroundedAutoReplyDecision.reason` 是返回 preview/自动处理的 reason。
- Write paths：
  1. `AiReplyDraftService.materializeAndValidateGroundedCandidate()` 将无专用 code 的未授权 action 映射为 `UNAUTHORIZED_ACTION_REMOVED`。
  2. `AiReplyDraftService.groundedFallbackResult()` 在最终 sanitize 删除时同样写该 warning。
- Read paths：
  1. `GroundedAutoReplyDecisionService.resolveReason()` 先调用 `hasValidationFailure()`。
  2. `GroundedAutoReplyDecisionService.passesSendGate()` 决定 `readyToSend`。
- Interaction points：action policy → DraftResult warning → decision reason；该链需把 legacy warning 与 `AI_REPLY_ACTION_*` 同等处理。(来源: K-grounded-action-violation-must-retry)

## 实现方案

### T1：补齐 legacy action warning 的 validation 映射
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt`
- 在 `hasValidationFailure()` 显式识别 `AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED`；保持现有 validation-first 顺序，不引入新 reason 常量或 DTO 字段。
- 单测构造 `READY` 与 `BLOCKED` 草稿各一条，均只带该 warning；断言 reason 为 `AI_REPLY_VALIDATION_FAILED`、`readyToSend=false`，并保留无 warning 对照的既有 reason。
- 遵守：I-1、I-2。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt` | legacy unauthorized warning 的 validation 归因。 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt` | reason 优先级与发送门禁回归。 |

## 验收标准

- I-1：`contextWarnings=[UNAUTHORIZED_ACTION_REMOVED]` 时，无论 rule IDs 是否为空、readiness 为 READY 或 BLOCKED，reason 都为 `AI_REPLY_VALIDATION_FAILED`。
- I-2：上述任何 case 的 `readyToSend=false`；无 warning 的 READY/AUTO/LLM_USED 仍为 `QA_AUTO_REPLIED`，普通缺口仍为原有 gap/review reason。
- 定向测试：`mvn -Dtest=GroundedAutoReplyDecisionServiceTest test`。

## 人工验收清单

### A-1：未授权动作的预览原因
- 前置条件：测试 LLM 生成未获授权的会议或 CV CTA，并使草稿带 `UNAUTHORIZED_ACTION_REMOVED`。
- 操作步骤：打开该来信的自动回复预览。
- 预期结果：预览不可发送，reason 显示 `AI_REPLY_VALIDATION_FAILED`；不显示 `QA_NO_MATCH` 或 `QA_GROUNDING_GAP`。
- 覆盖：I-1、I-2、现状审计 interaction point。
