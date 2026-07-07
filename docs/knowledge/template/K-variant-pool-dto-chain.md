---
id: K-variant-pool-dto-chain
domain: template
created: 2026-07-06
last_used: 2026-07-07
hit_count: 1
source: create-p:variant-pool-frontend-ui
---

经验：`MailComposeTemplate.subjectVariants` 和 `ReplySnippet.variantGroup` 字段在 DB domain 类和渲染引擎中已存在，但 CRUD DTO 链路（Controller Request → Service Command → Service Detail/Response）完全未贯通。Spring Data JDBC 的 `copy()` 不传的字段会保留旧值（不丢失），但 `MailComposeTemplate(...)` 构造器不传的 nullable 字段会默认 null（create 路径丢失）。

DTO 贯通检查清单（每次新增 domain 字段后必须逐层检查）：
1. Controller Request DTO — 接收前端值
2. Request.toCommand() — 传递到 Service Command
3. Service Command — 持有值
4. Service.create() — 构造器传入
5. Service.update() — copy() 传入（注意：用户清空字段时不应 fallback 到旧值）
6. Service.toDetail() / toResponse() — 映射到 API 响应 DTO
7. Detail/Response DTO — 包含字段供前端读取

关联：[[K-template-feature-coverage]]（模板功能验证必须逐层检查）
