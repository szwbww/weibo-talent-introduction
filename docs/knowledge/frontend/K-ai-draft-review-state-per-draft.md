---
id: K-ai-draft-review-state-per-draft
domain: frontend
created: 2026-07-13
last_used: 2026-07-16
hit_count: 18
source: fix-v:ai-reply-review-authority-fail-closed:fix-1
severity: P1
---
经验：AI 聊天可同时保留多个历史草稿，若只存“最后一次 response”，采用旧草稿时会错误继承新草稿的状态。
正确做法：每个 draft entry 必须与 raw/rendered 一起保存自身的采用边界；当前“采用后直接人工发送”策略不保存或传递 review state。若未来重新启用逐项审核，才额外保存该草稿自己的 review snapshot，不能复用最后一次 response。
