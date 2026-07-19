---
id: K-ai-draft-edit-not-review-confirmation
domain: frontend
created: 2026-07-15
last_used: 2026-07-20
hit_count: 12
source: create-p:ai-reply-p0-p2
severity: P0
---
经验：以“editor text/html 已不同于 AI baseline”作为缺口解除条件，只能证明发生过编辑，不能证明每个 unsupported/partial intent 已被补充或核验；加空格、改粗体即可绕过。
正确做法：编辑差异只用于决定是否还能提交 raw template；缺口发送必须保留每草稿 review snapshot，由操作员逐 reviewKey 确认并交给后端发送前校验，成功后单独审计。
关联：[[K-ai-draft-review-state-per-draft]]、[[K-ai-preview-raw-adoption-boundary]]。
