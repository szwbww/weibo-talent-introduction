---
id: K-smtp-idempotency-reservation-before-delivery
domain: mail
created: 2026-07-20
last_used: 2026-07-20
hit_count: 0
source: create-p:ai-reply-07-final-send-integrity-plan
---

## Context

数据库事务中先写发送尝试、再调用 SMTP、最后统一提交，看似原子，但进程可能在 SMTP 已接收后、事务提交前崩溃。

## Failure

事务回滚会删除发送占位；恢复后的相同请求无法识别已尝试投递，从而再次发送。SMTP 超时或断链也不能证明对端没有接收。

## Rule

调用 SMTP 前，用独立事务提交唯一发送占位并原子 claim 为 `DELIVERY_IN_PROGRESS`。只有 claim winner 可以投递；无明确未投递证据的异常进入 `DELIVERY_UNKNOWN`，重复请求 fail closed。成功或失败结果在后续独立事务中更新同一 attempt/mail record。

## Test

并发相同 payload 只有一个 claim；模拟 SMTP 接收后进程失败，持久化占位仍存在，后续相同请求不得再次调用 SMTP。

## Links

- `docs/plans/2026-07-20/ai-reply-07-final-send-integrity-plan.md`
