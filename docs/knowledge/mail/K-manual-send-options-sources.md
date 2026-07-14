---
id: K-manual-send-options-sources
domain: mail
created: 2026-07-13
last_used: 2026-07-14
hit_count: 7
source: create-p:material-reminder-batch-send
---
现状：手动/批量发送选项（`ManualExpertMailService.listSendOptions()`）只返回 enabled `COMPOSE_TEMPLATE`，数据源为 `mail_compose_template`；旧 `mail_template` 和裸 QA option 已退出该入口。实际正文由 `MailComposeTemplateService.render()` 解析 QA 规则、回复片段、自定义文本和内容变体。

正确做法：前端若要按模板业务类型展示专用行为，后端 option DTO 必须透出 `templateCode/mailType`，禁止使用模板名称、数据库 ID 或 option 顺序猜测。预览使用 `/api/compose-templates/{id}/preview`，最终发送仍以同一 templateId 的 `render()` 为权威。

注意：前端 `state.mailSendOptions` 有客户端缓存；模板保存/删除后必须清缓存。模板正文预览应单独请求权威 preview endpoint，不能把缓存 option 当正文源。

关联：K-view-registration-triad（Tab 注册）、K-composed-reply-order-contract（顺序契约）、K-batch-send-template-type-gate。
