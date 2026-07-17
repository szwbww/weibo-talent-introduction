---
id: K-content-variant-input-read-contract
domain: frontend
created: 2026-07-09
last_used: 2026-07-17
hit_count: 3
source: create-p:ui-collapsible-preview-and-variant-carousel
severity: P1
---
经验：内容变体编辑器（`#qaRuleVariantsContainer` / `#replySnippetVariantsContainer`）的读取契约是「遍历容器内全部 `.content-variant-input` textarea」——`collectContentVariants`（app.js:6656）、`validateContentVariantInputs`（6671）、`updateContentVariantsCountBadge`（6642）、`saveQaRule`/`saveReplySnippet` 全依赖此。
任何改造变体编辑器 UI（如改为轮播/一次显示一个）**必须保持每个变体各有一个 `.content-variant-input` 常驻 DOM**，仅用显隐控制可见性；严禁把非活跃变体移出 DOM 或改由 JS 数组托管值，否则保存时静默丢变体、校验漏检。渲染入口统一在 `renderContentVariantRows`（6624），两处编辑器共用，改一处即两处生效。
