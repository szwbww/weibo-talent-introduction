---
id: K-request-extractor-offset-order
domain: qa
created: 2026-07-13
last_used: 2026-07-13
hit_count: 3
source: create-p:ai-reply-01-source-order-request-extraction
severity: P1
---
经验：把 bullet 列表与问号句分别提取后执行 `bullets + questions` 会打乱邮件来源顺序；使用禁止 `\n` 的问句正则还会把软换行问题截成问号所在最后一行。
正确做法：所有请求候选携带原文 offset，URL 等长掩码后在段落级 searchable view 中允许跨单换行定位，bullet/question overlap 去重后统一按 offset 排序；返回文本只折叠软换行，不改语义。
