---
id: K-dead-template-field-save-ignore
domain: template
created: 2026-07-09
last_used: 2026-07-09
hit_count: 1
source: fix-v:cv-1-content-variant-engine:fix-1
severity: P1
---
经验：把旧模板字段标记为死字段时，只停读不够；create/update 继续保存请求值会让旧前端字段复活，后续清理计划会被历史脏数据拖住。
正确做法：死字段进入 expand-contract 的“停用”阶段后，DTO 可保留以兼容旧请求，但服务层保存路径必须忽略或清空该字段，并用 create/update 单测断言请求值不会落库。
反例：`MailComposeTemplateService.kt:59,82` 在 CV-1 中仍把 `command.subjectVariants` 写回 `mail_compose_template.subject_variants`，违反“保存忽略”。
