---
id: K-grounding-status-ui-only
domain: llm
created: 2026-07-13
last_used: 2026-07-19
hit_count: 10
source: create-p:ai-reply-03-structured-answer-materialization
severity: P1
---
经验：`GROUNDED/PARTIAL/UNSUPPORTED` 是操作端审核控制数据，不是对外邮件文案；fallback composer 若直接把 confirmation/unsupported 常量拼入 section，会把内部状态原样外发。
正确做法：状态留在 requestCoverage；PARTIAL 正文只输出有据事实，UNSUPPORTED 不生成答案；操作端逐项提示，后端最终 materializer 拒绝 raw 状态 token 和内部提示句。
