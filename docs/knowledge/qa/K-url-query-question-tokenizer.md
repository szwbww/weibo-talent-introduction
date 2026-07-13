---
id: K-url-query-question-tokenizer
domain: qa
created: 2026-07-12
last_used: 2026-07-13
hit_count: 8
source: create-p:ai-reply-url-safe-request-extraction
severity: P1
---
经验：用 `[^?.!\n]*\?` 从原始邮件提问号句，会把 URL query delimiter 当成句末，Google Scholar `citations?user=` 与 Scopus `detail.uri?authorId=` 因而生成 `com/citations?`、`uri?` 伪请求。
正确做法：问句定位前统一掩码 `http(s)://` URL 标点，并让自动 gap 与 AI suggestion 共用同一 tokenizer；返回值仍从原文 offset 还原，URL-only 片段必须过滤。
