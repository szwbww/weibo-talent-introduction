---
id: K-grounded-paragraph-cap-never-drop-claims
domain: llm
created: 2026-07-19
last_used: 2026-08-19
hit_count: 11
source: create-p:ai-reply-04-grounded-trust-content-plan
last_source: fix-v:ai-reply-failure-trust-closure-master-plan:blocked-after-fix-1
severity: P1
---
经验：Grounded 回复限制正文段落数时，不能在 composer 末端对 answer/paragraph 做 `take(N)`；复合问题超过 N 项时会静默丢失后续已取证 claim，而 readiness 仍可能保持 READY。
正确做法：服务端内容计划先把相邻 request 的 claim 分组到最多 N 段，materializer 验证每个 claim 恰好出现一次，composer 只按计划组装并断言全覆盖，绝不截断事实。
