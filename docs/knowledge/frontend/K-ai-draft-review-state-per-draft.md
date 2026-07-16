---
id: K-ai-draft-review-state-per-draft
domain: frontend
created: 2026-07-13
last_used: 2026-07-16
hit_count: 14
source: fix-v:ai-reply-review-authority-fail-closed:fix-1
severity: P1
---
经验：AI 聊天可同时保留多个历史草稿，依据缺口状态若只存“最后一次 response”，采用旧草稿时会错误继承新草稿的完整状态。
正确做法：每个 draft entry 与 raw/rendered 一起保存其 requestCoverage 派生 review state；adopt 复制该 entry 状态，未修改缺口草稿只在 UI 阻止直发，coverage 文案不得进入正文或 payload。
