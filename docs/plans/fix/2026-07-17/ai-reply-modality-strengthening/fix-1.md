# fix-1：条件来源到确定回答的语气强化复验

## 原计划 / 子计划引用

- 子计划：`docs/plans/2026-07-16/ai-reply-modality-strengthening.md`
- 实现提交：`4dd8e947 fix: reject plain-will modality upgrades from conditional QA`

## 约束摘录

- I-1：条件来源不能升级为资金、费用、权利或合同的确定承诺；普通 `will/shall/is entitled` 只在高风险结果谓词 family 中处理。
- I-2：来源在同一 definitive predicate family 明确支持时不得误拦截；但不能因来源出现任意条件词全局拒绝。
- I-3：只使用 answer 的 `sourceRuleIds` 对应 subject/body；既有高风险校验不短路。
- I-4：首轮与 CTA retry 均 materialize→claim validation；任一失败整次回退。
- I-5：严格 JSON、matrix 边界、固定邮件结构不回归。

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 证据 |
|---|---|---|---|
| P1-1 | P1 | 条件 QA 同时含一条同 family 明确句，且模型在 answer 中附加 `guaranteed`/`absolutely` 等通用强承诺词时 | `detectsModalityStrengthening()` 一旦命中 predicate family，便直接返回 family 比较结果，未再检查既有 `STRENGTHENING_PHRASES`。例如来源 `Candidates may receive salary support. Selected candidates will receive a certificate.`，回答 `You will receive salary support; this is guaranteed.` 会因 source 有 `will receive` 被放行。 | `AiReplyHighRiskClaimValidator.kt:119-141` |

## 修复规格

### P1-1：通用强承诺词不得被 family 正例短路

**文件：**

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt`

1. 在 `detectsModalityStrengthening()` 中先统一计算条件来源；只要来源为条件性且 answer 含既有 `STRENGTHENING_PHRASES`，立即视为强化，不得由 family 明确支持覆盖。
2. 再保留现有 family 比较：无通用强词时，同 family 的明确来源仍允许普通 `will/shall` 答复；低风险裸 `will` 仍不命中。
3. 增加回归：上述混合来源 + `will receive ... guaranteed` 必须返回 `AI_REPLY_CLAIM_MODALITY_STRENGTHENED`；保留现有同 family 正例与低风险 `We will share details` 正例。

## 当前状态（修前）

- Build/Test：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q test`：1732 passed，0 failed，0 errors，3 skipped。
- 定向：PASS — `AiReplyHighRiskClaimValidatorTest` 28 passed；`AiReplyDraftServiceTest` 102 passed。
- JS：PASS — `node --check src/main/resources/static/app.js`；`node --test src/test/js/*.test.js`：350 passed，0 failed。

## 合规审计

| Constraint | Verdict | Evidence |
|---|---|---|
| I-1 条件来源不得升级确定承诺 | ❌ P1-1 | `AiReplyHighRiskClaimValidator.kt:124-132` 的 family 分支在同 family source definitive 时直接返回 false，绕过 `:135-140` 的 `STRENGTHENING_PHRASES`。 |
| I-2 明确来源不误拦截 | ✅ | `AiReplyHighRiskClaimValidator.kt:129-132` 比较命中的具体 family；正反矩阵在 `AiReplyHighRiskClaimValidatorTest.kt:413-577`。 |
| I-3 只读引用来源且全部 claim 检查继续执行 | ✅ | `AiReplyHighRiskClaimValidator.kt:26-40` 只由 `sourceRuleIds` 解析文本，并连续执行事实、modality、高风险声明检查；`:51-70` 缺失或空来源 fail closed。 |
| I-4 首轮与 CTA retry 共用校验并整体 fallback | ✅ | `AiReplyDraftService.kt:231-276` 首轮 claim 无效回退；`:399-447` retry materialize/claim 无效也回退；retry 回归在 `AiReplyDraftServiceTest.kt:3653-3685`。 |
| I-5 严格 JSON 与结构契约 | ✅ | `AiReplyGroundedDraftMaterializer.kt:79-178` 要求固定字段、integral index/ID、intent/evidence 精确集合；`:191-204` 要求完整 request 集。 |
| Deleted code | ✅ | 无删除要求。 |
| No extras | ✅ | `4dd8e947` 仅改计划列出的 validator 与两个测试文件。 |

## 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ N/A；本计划无持久化状态机。
- Cross-plan check：✅ 正常路径与 CTA retry 均复用 materialize/claim 链路；P1-1 仅为该 validator 内通用强词分支漏检。
