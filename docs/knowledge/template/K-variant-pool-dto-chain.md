---
id: K-variant-pool-dto-chain
domain: template
created: 2026-09-23
last_used: 2026-09-23
hit_count: 10
source: create-p:variant-pool-frontend-ui
---

2026-09-23 复核：历史列/DTO 存在不等于功能有效。MailComposeTemplateService.create:72/update:98 明确写 subjectVariants=null，renderTemplate:266 只渲染 subject；previewDraft 的同名旧字段也不是有效主题变体入口。此前“subjectVariants/variantGroup 已贯通 CRUD 全链”的结论过期。新字段审计必须覆盖真正的读写方法，而不是只 grep DTO 属性。

经验：Spring Data JDBC 的 `copy()` 不传的字段会保留旧值（不丢失），但 `MailComposeTemplate(...)` 构造器不传的 nullable 字段会默认 null（create 路径丢失）。

DTO 贯通检查清单（每次新增 domain 字段后必须逐层检查）：
1. Controller Request DTO — 接收前端值
2. Request.toCommand() — 传递到 Service Command
3. Service Command — 持有值
4. Service.create() — 构造器传入
5. Service.update() — copy() 传入（注意：用户清空字段时不应 fallback 到旧值）
6. Service.toDetail() / toResponse() — 映射到 API 响应 DTO
7. Detail/Response DTO — 包含字段供前端读取

关联：[[K-template-feature-coverage]]（模板功能验证必须逐层检查）、[[K-variant-seed-call-sites]]
