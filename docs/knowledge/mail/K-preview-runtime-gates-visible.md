---
id: K-preview-runtime-gates-visible
domain: mail
created: 2026-08-18
last_used: 2026-08-18
hit_count: 6
source: fix-v:auto-reply-dry-run-preview:fix-1
severity: P1
---
经验：自动回复 dry-run 预览若只展示“会生成什么正文”，却漏掉真实发送前的运行期闸门（尤其退订拦截），运营会把不可发送场景误读成可自动发送，导致策略判断失真。
正确做法：预览保持反事实正文不变，但必须把 `AutoMailReplyService.processSingle` 真实发送前会检查的闸门全部作为 `wouldBeBlockedBy` 信息标记暴露；退订用 `EmailSuppressionService.isSuppressed(record.fromEmail)` 只读检查并标 `RECIPIENT_UNSUBSCRIBED`，不得调用写入型 suppress/remove。
反例（历史，**已修复**）：`AutoMailReplyService.kt:323-345` 与 `:446-467` 会因 `blockedByUnsubscribe` 返回 `RECIPIENT_UNSUBSCRIBED`，但当时的 `AutoReplyPreviewService` 只标 `AUTO_REPLY_DISABLED`、`MANUAL_HANDOFF_STATUS`、`INTRODUCTION_NOT_SENT`。

## 复核（2026-08-18，create-p:01-decide-context-closure）

当前 `AutoReplyPreviewService.buildWouldBeBlockedBy()` 已覆盖 **5 个**标记，退订缺口已闭合：

1. `RECIPIENT_UNSUBSCRIBED` — `emailSuppressionService.isSuppressed(fromEmail)`（只读，符合本条要求）
2. `ACCOUNT_AUTO_SEND_DISABLED` — `!account.enabled`
3. `AUTO_REPLY_DISABLED` — `!contact.autoReplyEnabled`
4. `MANUAL_HANDOFF_STATUS` — `contact.currentStatus == MANUAL_HANDOFF`
5. `INTRODUCTION_NOT_SENT` — 无 OUTBOUND/INTRODUCTION 的 `mail_record`

本条的**规则仍然有效**（新增运行期闸门必须同步补进预览的 `wouldBeBlockedBy`），只是原反例已不成立。
下次给 `processSingle()` 加发送前闸门时，仍须回到这里核对预览侧是否跟上。
