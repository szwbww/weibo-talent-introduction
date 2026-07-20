---
id: K-manual-send-unknown-must-converge
domain: mail
created: 2026-07-20
last_used: 2026-07-20
hit_count: 3
source: fix-v:ai-reply-07-final-send-integrity-plan:fix-1
last_source: fix-v:ai-reply-07-final-send-integrity-plan:stop-after-fix-3
severity: P1
---
经验：SMTP 调用或成功后的持久化异常若只向上抛出，会遗留 DELIVERY_IN_PROGRESS，既没有最终结果记录也无法安全判断是否可重发。
正确做法：已 claim 后的任何无法确认投递结果必须尽力在独立事务收敛为 DELIVERY_UNKNOWN；标记失败也只能 fail closed，不得重发。
反例：PendingMailOperationService.kt:235-245。
