---
id: K-preview-draft-raw-before-render
domain: template
created: 2026-07-08
last_used: 2026-08-14
hit_count: 6
source: fix-v:compose-template-editor-server-preview:fix-1
severity: P1
---
经验：草稿预览若先用模板服务本地 `renderText()` 解析正文块，再交给 `MailVariableService.renderPreview()`，会提前吃掉 `${key|fallback}` token，导致 fallbackKeys、变量状态和 strict skip 与真实发送管道不一致。
正确做法：preview-draft 的正文块解析必须保留原始模板文本；变量替换、默认值解析、fallback 检测、strict 占位符判定统一只在 `MailVariableService.renderPreview(rawText, account, contact)` 中发生。
反例：`MailComposeTemplateService.previewDraft()` 先调 `resolveBlocks()`，而 `resolveBlocks()` 在 QA/片段/自定义正文路径调用本地 `renderText()`（`MailComposeTemplateService.kt:165`, `404`, `437`, `453`）。
