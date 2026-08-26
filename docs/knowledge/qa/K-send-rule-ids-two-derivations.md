---
id: K-send-rule-ids-two-derivations
domain: qa
created: 2026-08-26
last_used: 2026-08-26
hit_count: 0
source: create-p:01-llm-fact-retrieval
severity: P1
---

# `sendQaRuleIds` 有两套互不相同的产生口径——只改一处必然让正文与审计对不上

`ResolvedQaRules.sendQaRuleIds`（声明 `AiReplyDraftService.kt:377-391`）有两个构造点，算法**不同**：

| 路径 | 表达式 | 位置 | 读什么 |
|---|---|---|---|
| `select()`（自动回复 + 人工发送，12 个调用点） | `orderEvidenceRuleIds(requestFacts, promptPool)` | `QaFactSelectionService.kt:64` / `:544-559` | **只读 `item.intents[*].evidenceRuleIds`** |
| `selectForWorkbench()` → `workbenchResult()`（工作台，1 个调用点） | `ordered.flatMap { it.factRuleIds }.distinct()` | `:367` | **只读 `item.factRuleIds`** |

后果：任何**只往 `factRuleIds` 里加事实、不经过 intent 证据集**的改动
（人工矩阵绑定、LLM 检索、任何旁路），在 workbench 路径会自动进 `sendQaRuleIds`，
在 `select()` 路径**不会**。而 `sendQaRuleIds` → `AiReplyDraftResult.qaRuleIds`
（`AiReplyDraftService.kt:1802/1872/2109`）→ `AutoMailReplyService.kt:637` 写
`mail_record_qa_rule`，是外发审计的唯一来源（读点 `QaRuleAuditService.kt:81-86`）。

→ 正文引用了事实 X 而审计里没有 X，且 `GroundedAutoReplyDecisionService.passesSendGate`
的 `draft.qaRuleIds != verifiedAutoRuleIds`（`:169-171`）判定基础被破坏。

正确做法：任何扩大 `factRuleIds` 的计划，必须**同时**声明它对两条口径各自的影响，
并在验收里断言两条路径的 `sendQaRuleIds` 都包含新事实。

关联：[[K-ai-reply-prompt-vs-send-rule-ids]]、[[K-audit-selected-source]]、[[K-request-facts-not-flat-pool]]
