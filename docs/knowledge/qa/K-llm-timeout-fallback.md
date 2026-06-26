---
id: K-llm-timeout-fallback
domain: qa
created: 2026-06-26
last_used: 2026-06-26
hit_count: 2
source: fix-v:qa-rules-phase3:fix-1
severity: P1
---
经验：可选 LLM 链路声明“超时回退”时，不能只 catch 异常；HTTP client 必须实际配置连接/读取超时，否则慢请求会阻塞人工工作台。
正确做法：为 LLM client 使用专用 `RestTemplate`/HTTP client，并把 `talent-introduction.llm.timeout-ms` 接到 connect/read timeout；超时异常返回 null，由确定性组装兜底。
反例：`LlmProperties.kt:13` 定义 `timeoutMs`，但 `RestTemplateConfig.kt:37-38` 创建无 timeout 的通用 `RestTemplate`，`HttpLlmDraftClient.kt:60-69` 只能在异常发生后 fallback。
