---
id: K-ai-review-authority-loss-and-order
domain: mail
created: 2026-07-16
last_used: 2026-07-16
hit_count: 11
source: fix-v:ai-reply-review-authority-fail-closed:fix-1
severity: P1
---
适用范围：仅当产品启用“AI 非 READY 草稿必须人工审核后发送”的 authority gate 时。
经验：该 gate 的服务端 authority 必须覆盖“审计写失败”和“同时间戳多版本”路径；无记录直接放行或只按秒级时间排序都会让 current identity 失真。
正确做法：启用 gate 时，非 READY 审计失败不暴露可采用草稿；latest authority 使用稳定 tie-break；action/readiness/snapshot 损坏 fail closed；确认日志只能由已验证的服务端结果触发。当前“采用后直接人工发送”策略不使用该 gate，生成日志不得阻断草稿或人工外发。
