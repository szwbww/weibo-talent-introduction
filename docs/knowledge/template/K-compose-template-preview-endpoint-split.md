---
id: K-compose-template-preview-endpoint-split
domain: template
created: 2026-08-14
last_used: 2026-08-14
hit_count: 1
source: create-p:expert-mail-preview
severity: P1
---

经验：邮件模板有两个名字都叫 "preview" 的端点，语义完全不同，选错必然渲染出带 `${}` 的原始模板文本。

- `GET /api/compose-templates/{id}/preview`（`MailComposeTemplateController.kt:59-61` → `MailComposeTemplateService.preview()` `:187-197`）
  只做 `resolveBlocks(blocks).includedTexts.joinToString("\n\n")`，**没有 orcidId/contactId/expertEmail 入参，不做变量渲染、不返回 fallbackKeys**。
  它回答的是"这个模板由哪些块拼成"，不是"某位专家会收到什么"。
- `POST /api/compose-templates/preview-draft`（`Controller:63-65` → `Service.previewDraft()` `:199+`）
  入参 `ComposeTemplatePreviewDraftRequest`（`:685-695`）含 `orcidId / expertEmail / contactId / senderAccountCode / variantIndex / strictPlaceholders`，
  返回 `ComposeTemplatePreviewDraftResult`（`:697-704`）含 `subject / body / blocks / fallbackKeys / toEmail / variables`。
  这才是"针对某位专家渲染"的唯一入口。

正确做法：任何"看这个专家会收到什么"的需求一律走 `preview-draft`，即使模板已经保存、即使只是只读展示；
前端把已保存模板的 `subject + blocks` 原样喂进去即可（`MailComposeTemplateBlockDetail` `:644-651` 是
`ComposeDraftBlock` `:678-683` 的同名超集，但应显式映射 4 个字段，不依赖 Jackson 的
`FAIL_ON_UNKNOWN_PROPERTIES=false` 默认值——本仓库无任何 Jackson 定制配置，该默认值是隐式依赖）。
既有前端调用范式见 `app.js:8178-8210 renderServerComposeTemplatePreview`。

只读展示场景也不要传 `strictPlaceholders: true`：数据不全的专家会直接失败，
而这恰恰是最需要看到的情况——应传 `false` 并把 `fallbackKeys` 显式呈现出来。

关联：[[K-preview-draft-raw-before-render]]（变量替换只允许发生在 `MailVariableService.renderPreview()`）、
[[K-preview-mirrors-pipeline]]（预览必须复用发送同源路径）。
