---
id: K-view-registration-triad
domain: frontend
created: 2026-07-01
last_used: 2026-08-16
hit_count: 20
source: create-p:ai-training-tab
---
经验：静态后台（`src/main/resources/static/`）新增一个侧栏 Tab/视图，必须四处同步注册，缺一即切换时 `viewMeta[view]` undefined 报错或不加载。
正确做法（四联契约）：
1. `index.html` 侧栏加 `.nav-tab[data-view="<name>"]`。
2. `index.html` 主区加 `<section class="view" id="view-<name>">`。
3. `app.js` 的 `viewMeta` 加 `"<name>": [标题, 副标题]`（`setView` L1185 读它写 `#viewTitle/#viewSubtitle`）。
4. `app.js` 的 `refreshCurrentView()`（L1203）加 `if (state.view==="<name>") await loadXxx();`。
`setView` 靠 `data-view` 切 `.active`、靠 `view-<name>` 切 section，二者命名必须一致。
