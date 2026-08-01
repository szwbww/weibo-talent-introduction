---
id: K-llm-attempt-total-budget-cancel
domain: llm
created: 2026-07-23
last_used: 2026-08-01
hit_count: 13
source: create-p:ai-reply-streaming-dual-ttl-cancel-plan
last_source: fix-v:ai-reply-streaming-dual-ttl-cancel-plan:blocked-after-fix-1
severity: P1
---
经验：长耗时 LLM 链路不能用一个 timeout 同时表达单次 provider 上限、整条生成链上限和重试次数；三者语义不同。手动停止也不能只 abort 浏览器请求，否则 provider 调用仍可能占用连接和额度。

正确做法：单次预算约束每个 provider 调用，总预算使用单一单调时钟 deadline 并由所有重试/修复共享，重试次数另行保持有界；取消通过稳定 generationId、服务端 cancellation token 和运行时注册表传播到 worker 与 provider stream。取消和最终审核/结果提交之间必须有原子 COMMITTING 边界，取消先赢则不提交，提交先赢则取消返回 TOO_LATE。

反例：每次重试重新创建总预算；把“总预算是单次预算十倍”解释为允许十次重试；停止按钮只移除 loading；取消后仍把部分响应转为 fallback 或写入初始草稿审核。
