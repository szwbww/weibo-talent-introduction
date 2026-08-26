---
id: K-markdown-emphasis-not-request-bullet
domain: qa
created: 2026-08-24
last_used: 2026-08-24
hit_count: 1
source: systematic-debugging:live-inbound-124
severity: P1
---

# Markdown 强调行不能当作请求 bullet

`QaRequestExtractor.BULLET_LINE_PATTERN = ^(?:[-*•]|\d+[.)]\s)` 对 `*` 分支不要求标记后空格，因此邮件清洗产生的 Markdown 强调/签名行（如 `*Name*`、`*Title*`、`*Phone...*`）全部被识别成 `Kind.BULLET` 请求。

线上 `LIVE_INBOUND:124` 的 exact `cleanedBody` 用当前生产同源 extractor 复现：共 6 条 request，其中 1 条真实 QUESTION，另外 5 条是姓名、职位、机构、电话、地址签名。日志 `kind=BULLET,QUESTION` 与复现一致。

正确边界：星号 bullet 必须要求 list-marker 语法（至少 `*` 后有空白），并增加 Markdown emphasis/signature fixture；修复不得影响 `- item`、`* item`、`• item`、`1. item`、`1) item` 及 soft-wrap offset/order 契约。
