---
id: K-reply-model-stable-enum-mapping
domain: llm
created: 2026-07-12
last_used: 2026-07-12
hit_count: 1
source: create-p:ai-reply-model-selection-backend
---
经验：AI 回复模型选择应由浏览器传稳定业务枚举，服务端映射可变 provider model id；不要直接扩改全局 `chat` 签名，否则文档分析、QA 提炼、polish 和大量 fake client 都被迫跟改。
正确做法：保留旧 `chat/stitchDraft`，增加只由 `AiReplyDraftService` 消费的窄 model-aware seam；两个回复入口共用 null 默认、校验、映射和回显。
