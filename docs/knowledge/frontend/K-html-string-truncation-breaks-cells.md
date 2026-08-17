---
id: K-html-string-truncation-breaks-cells
domain: frontend
created: 2026-08-16
last_used: 2026-08-16
hit_count: 0
source: create-p:batch-console-log-drawer
severity: P1
---

经验：对**已拼接完成的 HTML 字符串**做 `substring` / `slice` 限长，截点迟早落进标签或属性内部，
后续的 `</td><td>` 会被当成属性值吞掉，整行少一个单元格 —— 在 `table-layout: fixed` 下
表现为「从截断列往后所有列左移一格」，而不是「文字被截短」。

实例：`app.js` `renderBatchConfigRow` 的 `scopeHtml.substring(0, 300)`。
实测（Chromium + 真实函数输出）：收件范围 3 个筛选条件 = 257 字符（正常），
第 4 个 = 308 字符即截断成 `<span class="batch-task-sc</td>`，该 `<tr>` 的
`children.length` 从 7 掉到 6；模板列显示成 cron 文案，操作列宽度塌成 78px。
门禁 pill 因为拼在末尾也一起被砍掉，列表看不到门禁状态。

正确做法：限长只能发生在**结构化数据层** —— 截数组元素（`parts.slice(0, N)`）或截 DOM 节点，
再各自包标签；需要「更多」时用 `<details>` 折叠而不是丢弃。若只是想限高，用 CSS
（`-webkit-line-clamp`），不要动字符串。

排查手法：`document.querySelectorAll('tbody tr')` 逐行数 `children.length`，
与 `<colgroup>` 的 `<col>` 数比对，不一致即是本类缺陷。

关联：[[K-batch-console-regression-contract]]
