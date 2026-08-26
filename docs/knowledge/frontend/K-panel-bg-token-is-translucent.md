---
id: K-panel-bg-token-is-translucent
domain: frontend
created: 2026-08-16
last_used: 2026-08-21
hit_count: 3
source: create-p:batch-console-log-drawer
severity: P1
---

经验：`--panel-bg` **不是**不透明面板底色。实值为 `rgba(255, 255, 255, 0.55)`
（`styles.css:15`；暗色 `rgba(21, 31, 48, 0.55)`，`styles.css:9304`）。
任何需要遮住底层内容的浮层（抽屉、sticky 工具条、悬浮面板）用它当 `background`，
底下的表格/表单会直接透出来 —— 且这类缺陷 z-index 排查不出来
（`elementFromPoint` 返回的仍是浮层本身，层级是对的，透的是像素）。

本仓库既定的「不透明浮层」写法（两处先例，抄这个）：

```css
background: rgba(255, 255, 255, .96);
backdrop-filter: blur(8px);
```

- `.batch-manual-actions-sticky` — `styles.css:9166-9178`
- `.batch-config-editor-actions` — `styles.css:8684-8697`

反例：`.batch-log-drawer`（`styles.css:8773`）曾写 `background: var(--panel-bg)` 且无
`backdrop-filter`，是三者中唯一漏网的。

排查手法：`getComputedStyle(el).backgroundColor` 的 alpha 是否 < 1，比读 CSS 源码可靠。

注意：这两处先例都是硬编码浅色，**弹窗内部尚无暗色适配**。给单个浮层补
`prefers-color-scheme: dark` 会造成同一弹窗内深浅撕裂 —— 要么整体做，要么都不做。
