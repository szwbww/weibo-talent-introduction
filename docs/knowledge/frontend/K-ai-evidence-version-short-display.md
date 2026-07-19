---
id: K-ai-evidence-version-short-display
domain: frontend
created: 2026-07-20
last_used: 2026-07-20
hit_count: 1
source: fix-v:ai-reply-06-draft-audit-evidence-preflight-plan:fix-2
severity: P1
---
经验：后端将 evidenceSetVersion 改为纯 SHA-256 后，前端若沿用按分隔符切割的旧展示逻辑，会把 64 位完整 token 显示出来，破坏短 hash 与窄屏布局契约。
正确做法：版本的展示必须经固定长度截断 helper；不要从 token 格式推导短值，完整 token 只用于请求比对。
反例：src/main/resources/static/app.js:3933-3937,7030-7035。
