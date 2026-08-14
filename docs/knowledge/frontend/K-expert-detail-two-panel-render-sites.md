---
id: K-expert-detail-two-panel-render-sites
domain: frontend
created: 2026-08-14
last_used: 2026-08-14
hit_count: 1
source: create-p:expert-mail-preview
revalidated_by: create-p:expert-detail-head
severity: P1
---

> **2026-08-14 行号校正（结论全部成立，行号全部漂移）**：本条原写于同日更早，
> 记录的 7 处行号在当天的后续改动后已全部失效。下方为重新 grep 的实际行号。
> 教训同 [[K-manual-expert-mail-sender-only-variables]]：**带行号的 K 条目进 plan 前必须重新 grep**。

经验：专家详情区的子标签是**一处定义、两处渲染**。只改一处会得到静默空白面板（不报错）。

- 标签定义共用：`renderDetailSubTabs(activeTab)` `app.js:6499-6514`，改一次两处生效。
- 切换共用：`activateDetailSubTab(btn)` `app.js:6561-6583`，用
  `p.hidden = p.dataset.panel !== tabKey` 显隐；未命中的 `data-panel` 只是"全部 hidden"，
  **不抛错**——这就是为什么漏改一处是静默失败。
  该函数内已有懒加载范式：`if (tabKey === "template")` + `panel.dataset.loaded` 一次性守卫（`:6571-6582`）。
- panel DOM 各写各的（grep `detail-tab-panel` 回执）：
  - `showExpertDetail()` `:6629` → `:6665` / `:6668` / `:6740` / `:6743`，默认激活 `academic`（`:6663`）
  - `loadContactDetail()` `:6967` → `:7039` / `:7042` / `:7200` / `:7203`，默认激活 `contact`（`:7037`）
- 分流点：`handleContactAction` 的 `select-expert` 分支按 `expert?.contactId` 二选一（`app.js:8536-8549`）。
- 事件绑定共用：`#contactDetail` 上的 click 委托（`app.js:11031-11047`），
  `closest("[data-sub-tab]")` → `activateDetailSubTab`。新标签自动继承，无需新增绑定；
  但**只监听了 click**，需要 `change`（如面板内下拉）时要另加一个 change 委托
  —— 现已存在一个：`app.js:11048-11057`，只处理 `[data-role="mail-preview-template"]`。

正确做法：新增/修改专家详情子标签时，`data-panel="<key>"` 必须在 `app.js` 中出现 **2 次**，
可用 `grep -c 'data-panel="<key>"' app.js` 与既有 `data-panel="template"` 的计数比对作为验收断言
（`expertMailPreviewTab.test.js:322-329` 已把该断言固化）。
面板内元素一律用 `data-role` + `panel.querySelector` 作用域查询，不要用全局 id
（两套面板会生成结构相同的 DOM，且面板宿主是 app.js 模板串而非 index.html，
`grep index.html` 式的存在性核对在此不适用）。

**例外**：顶部操作栏 `#contactHeadActions` 只有 `loadContactDetail` 一处渲染
（`showExpertDetail` 在 `:6642-6643` 把它清空并隐藏），该区域沿用全局 id 是安全的。

关联：[[K-dom-stub-tests-hide-dangling-refs]]（DOM stub 会掩盖宿主缺失）、
[[K-expert-tag-editor-shared-render-contract]]（姓名行的标签编辑器同样是两处渲染）。
