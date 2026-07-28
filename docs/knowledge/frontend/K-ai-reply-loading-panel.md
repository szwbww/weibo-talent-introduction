---
id: K-ai-reply-loading-panel
domain: frontend
created: 2026-07-12
last_used: 2026-07-27
hit_count: 31
source: fix-v:ai-reply-review-authority-fail-closed:fix-1
last_source: fix-v:ai-reply-streaming-dual-ttl-cancel-plan:blocked-after-fix-1
---
经验：AI 回复 loading 不能复用 tag-editor overlay 并挂在可被 `:empty { display:none }` 隐藏、且会被结果 render 重写 innerHTML 的 messages 容器上。收发件 AI 与训练模拟也不能各写一套状态。
正确做法：统一在稳定的 `.ai-chat-panel { position:relative }` 上挂专用 overlay；helper 保存/恢复控件原 disabled（含 `select`），两个入口使用 requestSeq + 当前邮件 id + 模型快照丢弃陈旧响应，`finally` 必收遮罩。
