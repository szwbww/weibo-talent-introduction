# fix-1: ai-reply-04 toast label + restore JS contracts

## 原计划
`docs/plans/2026-07-12/ai-reply-04-generation-state-feedback.md`

## 修正
| ID | 问题 | 修复 |
|---|---|---|
| P2-1 | 邮箱成功 toast 仍用「AI 生成完成」，未走共享 label | 统一 `aiReplyGenerationStateLabel` |
| P2-2 | `aiReplyLoadingFeedback.test.js` 重写丢掉 loading/mailRecordId 源契约 | 补回源契约 describe |

## 不做
- `finalState` 在 used=false 时拒绝 LLM_USED：现有分支不会传入该组合（观察）
