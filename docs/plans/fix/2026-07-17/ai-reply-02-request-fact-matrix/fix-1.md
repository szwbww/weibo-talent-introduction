# fix-1: ai-reply-02 request-fact-matrix P1

复验对象: `docs/plans/2026-07-12/ai-reply-02-request-fact-matrix.md`

## P1

| ID | 问题 | 修复 |
|---|---|---|
| P1-A | `isPartialCoverage`：`factRuleIds` 非空但 `findById` 全 miss → `ruleTexts` 空 → `all{}` 真空真 → 误 PARTIAL | `ruleTexts` 空或 `size != factRuleIds.size` 时 return false（不判 PARTIAL） |
| P1-B | 缺显式 `qaRuleIds` 交集断言（计划 T4） | 测 `qaRuleIds=[1]`、candidates `[1,2]`、suggested 更大 → `factRuleIds=[1]`、`sendQaRuleIds=[1]` |

## 不适用

| 项 | 理由 |
|---|---|
| Flat `promptRuleIds` / I-2「prompt consumer」/ Observable「生成层不再面对扁平事实池」 | 延后 phase 3；总索引：计划 3 是唯一正文消费者；本计划 Out of scope 含正文排版/fallback |

## 已有决策（不回退）

- `RequestGroundingStatus` / `RequestFactItem` 保持 public（挂 `AiReplyDraftResult`）
