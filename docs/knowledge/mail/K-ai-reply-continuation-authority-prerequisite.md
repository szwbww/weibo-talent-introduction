---
id: K-ai-reply-continuation-authority-prerequisite
domain: mail
created: 2026-07-16
last_used: 2026-07-16
hit_count: 2
source: fix-v:ai-reply-p0-p2-master-plan:fix-2
severity: P1
---
经验：AI 回复 continuation 的 turns/session 是客户端输入，不能据其非空就假定首轮权威已建立；否则调用方可跳过首轮审计取得 AI 正文，再以无 source、无 confirmation 的“纯人工”请求通过发送 gate。
正确做法：处理或暴露 continuation 前，先从服务端持久化记录验证当前 draft authority，并沿用其 identity；无记录、损坏记录或不自洽记录都必须 fail closed，且不得新增首轮审计事件。
反例：controller 在 `turns.isNotEmpty()` 时跳过 `recordInitialDraft()`，又用 `authorityResult?.available ?: true` 将无权威 continuation 默认为可用。
