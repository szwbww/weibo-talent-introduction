---
id: K-grounded-json-materialize-before-policy
domain: llm
created: 2026-07-13
last_used: 2026-07-16
hit_count: 9
source: create-p:ai-reply-03-structured-answer-materialization
severity: P1
---
经验：多请求 grounded LLM 若直接返回整封字符串，后端无法验证 request index 完整性，也无法防止模型复述内部 status。动作策略重试若仍接受自由文本，还会绕过首轮结构契约。
正确做法：模型先返回严格 request-index JSON，后端验证 index/status 边界后统一组装邮件；初始响应与动作重试都先 materialize 再进入 CTA sanitizer，无效结构走确定性 fallback，raw JSON 永不进入 response。
