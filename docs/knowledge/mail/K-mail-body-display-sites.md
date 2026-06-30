---
id: K-mail-body-display-sites
domain: mail
created: 2026-06-29
last_used: 2026-06-30
hit_count: 5
source: create-p:translate-button-everywhere
severity: P2
---
经验：前端「邮件正文」展示点分散在 app.js 多处，正文框统一类名 `.pre`（styles.css:~1506，white-space:pre-wrap，经 escapeHtml 渲染）。任何要在「所有正文位置」统一加能力（翻译、复制、高亮等）的需求，必须按此全集逐点改，且不能给页面所有 `.pre` 盲挂（会污染 QA/JSON/监控等非正文 `.pre`）。
正文框全集（read paths of mail body）：
① `renderMailItem()` app.js:~3873「查看完整正文」`.pre`（专家详情面板邮件 :~4128、收发信箱来信处理面板内嵌专家历史信件 :~5562）；其上 `.mail-preview` 是 compact 预览行。
② 收发件箱详情面板 :~4810「正文」`.pre`（源 `/api/mail/mailbox/...`）。
③ 未匹配来信详情 :~5600「原始正文」`.pre`。
④ 未匹配来信详情 :~5606「清洗后正文」`.pre`。
⑤ 自动回复预览 :~5160「回复正文」`.pre`（OUTBOUND 草稿 preview.replyBody）。
非正文片段（通常应排除）：日志详情 `bodyPreviewText` :~4325/4332（截断预览）、列表表格 `cleanedBody.slice(0,80)` :~5909。
正确做法：抽共享渲染器替换各 `<div class="pre">`，集中行为；行号会漂移，改前先 grep `class="pre"` 复核。
关联：K-plaintext-reply-client-reflow（`.pre` 纯文本渲染契约）。
