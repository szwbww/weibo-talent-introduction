---
id: K-css-ident-cannot-start-with-digit
domain: frontend
created: 2026-08-19
last_used: 2026-08-26
hit_count: 2
source: create-p:workbench-repair-01-tab-focus-selector
severity: P1
---

经验：把 UUID 派生的实例 id 拼进裸 `#id` 选择器会**按概率**抛 `SyntaxError`。CSS 标识符不能以数字开头，而 UUID v4 首字符在 `0-f` 上均匀分布，**10/16 = 62.5% 的挂载会炸**，剩下 37.5% 正常——表现为「时灵时不灵」，极易被当成偶发问题放过。

注意：以数字开头的 `id` 属性在 **HTML5 里完全合法**，DOM 里元素确实存在。坏的只有「用裸 `#x` 去选它」这一步，所以 `aria-controls` / `aria-labelledby` 这类按 id 关联的属性照常工作。

反例：`trust-reply-workbench.js:1514`（2026-08-05 由 82a23b4 引入）
`host.querySelector(\`#${state.instanceId}-tab-${page}\`)`，`instanceId = makeId()` 返回 UUID v4。
该行在 `render()` **之后**执行，所以页签视觉上切换成功，只有 `focus()` 丢失 + 控制台报错 + roving tabindex 失效。

正确做法：组件内定位元素用**属性选择器**（`[role="tab"][data-page="facts"]`、`[data-page-panel="frame"]`），id 属性保留给 ARIA 关联。若必须用 id 选择器，走 `CSS.escape()`——但本仓库零先例，且 DOM stub 测试里没有 `CSS` 全局对象，优先选属性选择器。

同类隐患排查：`grep -rn 'querySelector([\`"]#' src/main/resources/static/`。命中里只有含实例 id 的才有问题，静态字符串 id（如 `app.js:7335` 的 `#contactList`）无碍。

关联：[[K-dom-stub-tests-hide-dangling-refs]]（本仓 DOM stub 的 `querySelector` 从不校验选择器语法、永不抛异常，所以两个既有页签测试走过这条路径却全绿）。
