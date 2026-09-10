---
id: K-meeting-confirmation-generic-template-boundary
domain: mail
created: 2026-09-10
last_used: 2026-09-10
hit_count: 1
source: create-p:meeting-confirmation-template-timezone-repair
severity: P1
---

会议确认正文应调用 `MailComposeTemplateService.renderByCode("MEETING_INVITATION", variables, seed)`，不能从 `MailComposeTemplateDetail.blocks` 中仅摘取 `CUSTOM_TEXT`。前者会按顺序解析 `REPLY_SNIPPET` 与 `CUSTOM_TEXT`，并由 `renderText` 替换调用方提供的 `${key}`。

会议专用值应只在该调用的变量 map 中增加 `meeting_time` 和 `zoom_url`；不要修改全局 `renderText` 语法或把它们注册为所有模板运行时都存在的变量。发送前 `PendingMailOperationService` 已重走 `MeetingConfirmationService.validateAndBuild` 并核验正文/ICS，预览与重建必须使用相同入口。

证据：`MeetingConfirmationService.kt:212-224,243-280`、`MailComposeTemplateService.kt:116-131,459-552,611-619`、`PendingMailOperationService.kt:501-545`。
