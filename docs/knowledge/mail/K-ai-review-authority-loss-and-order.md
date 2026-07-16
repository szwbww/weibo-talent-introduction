---
id: K-ai-review-authority-loss-and-order
domain: mail
created: 2026-07-16
last_used: 2026-07-16
hit_count: 8
source: fix-v:ai-reply-review-authority-fail-closed:fix-1
severity: P1
---
经验：服务端 authority 不仅要防客户端伪造，还要覆盖“审计写失败”和“同时间戳多版本”路径；无记录直接放行或只按秒级时间排序都会让 current identity 失真。
正确做法：非 READY 审计失败时不暴露可采用草稿；最新 authority 使用稳定 tie-break；action/readiness/snapshot 损坏 fail closed；确认日志只能由已验证的服务端结果触发。
