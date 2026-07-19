---
id: K-ai-preflight-stale-response-draft-identity
domain: frontend
created: 2026-07-19
last_used: 2026-07-20
hit_count: 3
source: fix-v:ai-reply-06-draft-audit-evidence-preflight-plan:fix-1
severity: P1
---
经验：用 qaRuleIds 比较采用草稿身份，两个草稿共用事实时旧请求仍可把结果绘制到新采用草稿。
正确做法：采用上下文和请求快照必须携带 draftId；重新采用、切换邮件、清空和发送时立即递增序号并取消旧请求，响应同时比对 recordId、seq、draftId 和 exact text。
反例：app.js:8850-8925, 9652-9679。
