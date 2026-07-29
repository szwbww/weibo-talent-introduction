---
id: K-es-bootstrap-create-only-on-404
domain: es-index
created: 2026-07-29
last_used: 2026-07-29
hit_count: 0
source: create-p:trust-reply-unsupported-answer-v1
severity: P1
---

ES 索引 bootstrap 必须区分“不存在”和“不可访问”：

- `HEAD/GET` 2xx：索引存在，不创建。
- 只有明确 404：加载受版本控制的 mapping 并创建。
- 401/403/429/5xx、网络超时、TLS/DNS 错误、mapping 读取失败：记录分类日志并停止本次 bootstrap，禁止继续 PUT。

把任意异常都解释为 404 会在凭据错误、ES 故障或限流时发出误创建请求，掩盖真实原因。可选索引 bootstrap 失败还应与业务主流程隔离，并使用有限连接/读取超时。

