---
id: K-preview-runtime-gates-visible
domain: mail
created: 2026-06-28
last_used: 2026-07-19
hit_count: 3
source: fix-v:auto-reply-dry-run-preview:fix-1
severity: P1
---
经验：自动回复 dry-run 预览若只展示“会生成什么正文”，却漏掉真实发送前的运行期闸门（尤其退订拦截），运营会把不可发送场景误读成可自动发送，导致策略判断失真。
正确做法：预览保持反事实正文不变，但必须把 `AutoMailReplyService.processSingle` 真实发送前会检查的闸门全部作为 `wouldBeBlockedBy` 信息标记暴露；退订用 `EmailSuppressionService.isSuppressed(record.fromEmail)` 只读检查并标 `RECIPIENT_UNSUBSCRIBED`，不得调用写入型 suppress/remove。
反例：`AutoMailReplyService.kt:323-345` 与 `:446-467` 会因 `blockedByUnsubscribe` 返回 `RECIPIENT_UNSUBSCRIBED`，但 `AutoReplyPreviewService.kt:125-140` 只标 `AUTO_REPLY_DISABLED`、`MANUAL_HANDOFF_STATUS`、`INTRODUCTION_NOT_SENT`。
