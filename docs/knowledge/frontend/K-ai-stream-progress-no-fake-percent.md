---
id: K-ai-stream-progress-no-fake-percent
domain: frontend
created: 2026-07-24
last_used: 2026-07-27
hit_count: 11
source: create-p:ai-reply-streaming-dual-ttl-cancel-plan
last_source: fix-v:ai-reply-streaming-dual-ttl-cancel-plan:blocked-after-fix-1
severity: P1
---
经验：LLM 流通常没有可验证的总 token 或完成比例，前端不能把已收字符数、事件数或耗时伪装成“生成完成百分比”；否则进度可能倒退、停在 99% 或误导运营。

正确做法：展示服务端稳定阶段、provider 活动类型、总 TTL 已用比例、单次/总耗时和最近活动时间。阶段切换立即推送，token 活动聚合后限频推送，heartbeat 携带最新快照；进度事件只含有界指标，不含 prompt、正文、delta 或 reasoning，并用 generationId/progressSeq 丢弃陈旧事件。

反例：按字符数估算完成率；逐 token 转发到浏览器；把 TTL 已用比例标成生成完成度；旧 generation 的 progress 覆盖当前邮件。
