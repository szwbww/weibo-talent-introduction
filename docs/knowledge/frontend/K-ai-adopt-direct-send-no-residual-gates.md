---
id: K-ai-adopt-direct-send-no-residual-gates
domain: frontend
created: 2026-07-16
last_used: 2026-08-21
hit_count: 13
source: fix-v:ai-adopt-direct-manual-send:fix-1
last_source: fix-v:ai-reply-07-final-send-integrity-plan:stop-after-fix-3
severity: P1
---
经验：把 AI 审核改为“采用后直接人工发送”时，只删除 modal、identity 和 review-event 不够；任何依赖 adopted draft 的 readiness、requestCount、缺口或编号完整性来阻止 manual-rich API 的 guard，仍会让运营看到“点击发送即可”流程失败。
正确做法：人工发送仅保留主题/正文、最终变量渲染、QA 与账号等邮件本身校验；删除所有只因 AI 草稿元数据而 return 的前端 gate，并以采用 BLOCKED/多项草稿后恰好一次 manual-rich 提交覆盖。
反例：`src/main/resources/static/app.js:9515-9592` 的 `validateSectionNumbering(...)` 在审核 modal 已删除后仍按 adopted requestCount 阻断发送。
