---
id: K-reply-mode-filter-gate-parity
domain: campaign
created: 2026-06-29
last_used: 2026-06-30
hit_count: 2
source: create-p:expert-contact-reply-mode-filter
severity: P1
---
经验：「会不会自动回复」由闸门 `AutoMailReplyService.kt:106` 定义为 `!autoReplyEnabled || currentStatus==MANUAL_HANDOFF`。`auto_reply_enabled` 与 `current_status` 两标志可能不一致（如开关 true 但已 MANUAL_HANDOFF，见 318 排查）。
正确做法：凡要按「回复模式」分类/筛选专家，必须用复合口径——人工模式 = `auto_reply_enabled=false OR current_status='MANUAL_HANDOFF'`，自动模式 = 其严格否定。禁止只看单个 `auto_reply_enabled` 字段近似，否则不一致记录会被误分类。
