# Phase 4 修复计划：AI 回复无授权动作硬拦截

> 原计划：`docs/plans/2026-07-12/ai-reply-safety-model-plan-index.md`
> 子计划：`docs/plans/2026-07-12/ai-reply-action-policy-runtime.md`
> 复验对象：Phase 4 AI 回复无授权动作硬拦截

## 约束摘录

- I-1：`allowedActions` 默认为空；仅来信明确意图/已提供材料、明确会议意图/同意会议，或运营明确授权时加入相应动作。
- I-2：动作授权、校验、清理只能使用受测短语/regex；不得调用 LLM、URL 或 enrichment。
- I-3：客观流程描述不是直接请求；中英文直接请求必须识别。
- I-4：最终草稿绝不含未授权 CV/材料请求或会议/通话提议；最多纠偏一次，仍违规则清理并告警。
- I-5：纠偏/清理不得改变 QA ids、mode、覆盖元数据或 few-shot refs。

## 修正记录表

| P1 | 触发频率 | 根因 |
| --- | --- | --- |
| 未授权的英文 CV 请求可穿透最终硬拦截 | LLM 英文回复的常见措辞；如 `Could you share your CV?`、`Would you mind forwarding your résumé?` | `MATERIAL_REQUEST` 仅覆盖 imperative `please/send/share/provide` 和 `reply with`，遗漏疑问式请求及 `résumé` 同义词。`findViolations` 与 `sanitize` 共同依赖该不完整集合。 |

## 修复规格

1. 修改 `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`：在保持 I-3 流程描述豁免的前提下，扩展 `MATERIAL_REQUEST` 对英文直接索要材料的受测 regex，至少覆盖 `Could/Would you (please) share/send/provide ... CV/résumé` 等疑问式 CTA 与 `résumé` 拼写；不得放宽 `deriveAllowed` 的授权条件，不得引入 LLM、外部访问或新动作枚举。
2. 修改 `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt`：为未授权疑问式 CV/résumé 请求增加 `findViolations` 与 `sanitize` 断言；同时保留流程事实不被删除的反例。
3. 修改 `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`：让首轮或重试返回一个上述疑问式 CTA，断言最终 `draftText` 不含该 CTA、存在 `UNAUTHORIZED_ACTION_REMOVED`，并验证 I-5 元数据不变。

预期：未授权动作的疑问式英文变体与现有 imperative 变体一样被 retry/sanitize 拦截；明确授权时仍允许；流程说明保留。

## 当前状态

- 编译/测试：本次仅执行 Phase 4 设计合规审计，未运行。
- 复现证据：`AiReplyActionPolicy.kt:32-37` 不匹配 `Could you share your CV?` 或 `Would you mind forwarding your résumé?`；`AiReplyActionPolicy.kt:85-114` 与 `AiReplyDraftService.kt:255-289` 都依赖该匹配结果，因此此类 CTA 可直接返回。

## 合规审计

- I-1：✅ `AiReplyActionPolicy.kt:61-82` 从来信、当前运营指令和历史运营指令派生；`AiReplyDraftService.kt:79-83` 两入口共享调用。
- I-2：✅ `AiReplyActionPolicy.kt:14-59,85-114` 仅本地 regex/文本分句；无 LLM、URL 或 enrichment 调用。
- I-3：❌ `AiReplyActionPolicy.kt:32-37` 未覆盖疑问式英文直接 CV/résumé 请求；`AiReplyActionPolicyTest.kt:76-109` 也未覆盖该类请求。
- I-4：❌ `AiReplyDraftService.kt:255-289` 的 retry、sanitize 和最终 gate 都依赖 I-3 的遗漏检测，故可返回未授权 CTA。
- I-5：✅ `AiReplyDraftService.kt:291-300` 重建结果时复用 `resolved`、`mode`、`fewShotDialogRefs` 和计数元数据。
- 删除代码：✅ 原 `fallback` 已由 `fallbackDraftText` 加 `enforceActionPolicy` 取代，见 `AiReplyDraftService.kt:85-215,317-341`。
- No extras：✅ 仅原计划列出的 4 个文件变更。

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ 无状态机。
- Cross-plan check：✅ 本子计划对 Phase 1-3 的 DTO/元数据契约保持一致；两入口均经同一 `generate` seam，见 `AiTrainingController.kt:205-212`、`UnmatchedInboundMailController.kt:296-304`。
