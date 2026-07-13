---
id: K-ai-preview-raw-adoption-boundary
domain: mail
created: 2026-07-13
last_used: 2026-07-13
hit_count: 4
source: fix-v:ai-reply-05-rendered-preview:fix-1
severity: P1
---
经验：双表示草稿的 preview/采用边界不能只把 rendered 文本放入人工编辑器；否则 raw 模板在最终发送前丢失，切换 sender account 或 preview 缺账号时无法按最终权威数据渲染。只比对 `innerText` 也不够：加粗、链接等富文本编辑不改文字却会被 raw 重渲染吞掉。
正确做法：UI 显示和编辑 rendered 值，但对未编辑的 AI adopt 草稿保留 raw 模板及 rendered text/HTML baseline；只有文本和 HTML 均仍等于 baseline 时才传 raw 给最终发送链，任一富文本编辑后必须以用户编辑内容为准。
反例：`src/main/resources/static/app.js:9383-9389` 只比较 `editor.innerText`，用户加粗后仍传 `templateTextBody`，服务端用纯文本 HTML 覆盖编辑器格式。
