---
id: K-action-sanitizer-preserve-layout
domain: llm
created: 2026-07-12
last_used: 2026-07-16
hit_count: 10
source: create-p:ai-reply-01-format-preservation
severity: P1
---
经验：动作安全 sanitizer 若把句子 `joinToString(" ")` 并全局压缩空白，即使没有违规也会摧毁邮件换行、编号和签名；前端 `white-space:pre-wrap` 无法恢复服务端丢失的布局。
正确做法：无删除时逐字返回原文；有删除时按原始 span 删除命中句，只规范删除接缝的过量空行，检测与清理共用同一 tokenizer。
