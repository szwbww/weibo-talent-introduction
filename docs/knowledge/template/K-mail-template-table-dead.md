---
id: K-mail-template-table-dead
domain: template
created: 2026-08-11
last_used: 2026-08-11
hit_count: 0
source: create-p:unsubscribe-01-body-link
severity: P2
---

经验：`mail_template` 表**已无任何代码读取方**，不是正文 SSOT。

证据：`grep -rn "mail_template\b\|MailTemplateRepository\|mailTemplateRepository" src/main/kotlin`（排除 `mail_compose_template*`）**零命中**。

正文 SSOT 是 `mail_compose_template_block.custom_text`：

- `V61__create_mail_compose_template.sql` 建 `mail_compose_template` + `mail_compose_template_block`。
- `V62__unify_mail_templates.sql:38-72` 把 `mail_template.body` 搬进 `block_order = 0` / `block_type = 'CUSTOM_TEXT'` 的块。
- INTRODUCTION / MEETING_INVITATION / MATERIAL_REMINDER / MEETING_CONFIRMATION 当前都是单 CUSTOM_TEXT 块形态。

读路径：`MailComposeTemplateService.resolveBlocks()`（`:248` QA_RULE / `:279` REPLY_SNIPPET / `:294` CUSTOM_TEXT）→ `renderText()` `:588`；对外是 `renderByCode(templateCode, ...)` 与 `render(templateId, ...)`。

**How to apply**：正文内容类迁移**只改** `mail_compose_template_block`，不要双写 `mail_template`。`V56`/`V71` 的双写是历史做法（当时两表并存），沿用只会产生两份漂移正文并误导后续排查。

关联：[[K-renderText-all-callers]]、[[K-unsubscribe-variable-injection-sites]]、[[K-flyway-placeholder-replacement]]（这些正文里的 `${...}` 正是 Flyway 占位符冲突的来源）。
