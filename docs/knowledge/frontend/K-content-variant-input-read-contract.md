---
id: K-content-variant-input-read-contract
domain: frontend
created: 2026-08-14
last_used: 2026-08-14
hit_count: 10
source: create-p:ui-collapsible-preview-and-variant-carousel
severity: P1
---
经验：内容变体编辑器（`#qaRuleVariantsContainer` / `#replySnippetVariantsContainer`）的读取契约是「遍历容器内全部 `.content-variant-input` textarea」——`collectContentVariants`（app.js:7843）、`validateContentVariantInputs`（7858）、`updateContentVariantsCountBadge`（7829）、`saveQaRule`/`saveReplySnippet` 全依赖此。
任何改造变体编辑器 UI（如改为轮播/一次显示一个）**必须保持每个变体各有一个 `.content-variant-input` 常驻 DOM**，仅用显隐控制可见性；严禁把非活跃变体移出 DOM 或改由 JS 数组托管值，否则保存时静默丢变体、校验漏检。渲染入口统一在 `renderContentVariantRows`（7744），两处编辑器共用，改一处即两处生效。

> 2026-08-14 复验（create-p:expert-mail-preview）：行号已按当前 `app.js` 重新实测校正
> （原记录的 6624/6642/6656/6671 已过期）；读取契约本身复验有效，未变。
