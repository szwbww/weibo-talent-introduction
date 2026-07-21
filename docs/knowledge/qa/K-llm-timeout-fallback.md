---
id: K-llm-timeout-fallback
domain: qa
created: 2026-07-21
last_used: 2026-07-21
hit_count: 20
source: fix-v:qa-rules-phase3:fix-1
last_source: fix-v:ai-reply-failure-trust-closure-master-plan:blocked-after-fix-1
severity: P1
---
经验：LLM 超时不能只坍缩成 nullable 空响应并静默显示 fallback；运营必须知道是 timeout、限流、网络、服务异常还是空响应，且失败结果不能伪装成可采用草稿。
正确做法：专用 HTTP client 必须接 connect/read timeout；回复专用 observed seam 返回稳定失败分类，服务层做一次有界 transient retry，并把最终稳定 warning 传到页面和审计。旧 nullable seam 只保留给兼容 caller。最终失败 fallback 只能是明确标记的内部参考，禁止采用和自动发送。
反例：HTTP client catch 所有异常返回 null，DraftService 统一写 `FALLBACK_NO_RESPONSE`，页面只显示“模型无有效响应”。
