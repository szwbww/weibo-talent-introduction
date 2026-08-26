---
id: K-panel-head-space-between-third-child
domain: frontend
created: 2026-08-21
last_used: 2026-08-21
hit_count: 0
source: create-p:ui-tweaks-01-check-replies-move-and-auto-preview-removal
severity: P2
---

经验：`.panel-head`（`styles.css:815-822`）是
`display:flex; align-items:center; justify-content:space-between; gap:12px`。
它靠「恰好 2 个子元素」把标题推到左、操作推到右。

**往里直接再塞一枚按钮 → 变成 3 个子元素被 `space-between` 均分**，新按钮会漂到标题与
原按钮之间的空白处，而不是贴着原按钮。这不是间距没调好，是布局语义变了；靠加 `margin-left:auto`
之类的补丁只会让下一次再加按钮时重演。

正确做法：把所有操作按钮包进一个容器，让 `.panel-head` 永远只有 2 个子元素：

```css
.panel-head-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
}
```

```html
<div class="panel-head">
  <h2>面板标题</h2>
  <div class="panel-head-actions">
    <button class="button">次要</button>
    <button class="button primary">主要</button>
  </div>
</div>
```

配套陷阱：`src/test/js/batchEntryRelocation.test.js`（「I3-2: button is the second child of
the panel-head, right after the panel h2」）用**正则钉死**了「`<h2>…</h2>` 紧跟 button」这一骨架。
任何往 `.panel-head` 插入包裹层的改动都必须把该用例一起改，否则 `mvn test` 在 test phase 中止。

关联：[[K-ui-removal-retires-obsolete-contract-tests]]
