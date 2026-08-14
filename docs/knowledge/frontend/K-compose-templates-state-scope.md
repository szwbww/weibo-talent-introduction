---
id: K-compose-templates-state-scope
domain: frontend
created: 2026-08-14
last_used: 2026-08-14
hit_count: 1
source: create-p:expert-mail-preview
severity: P1
---

经验：`state.composeTemplates` 是**视图局部**缓存，只有 `mail-templates` 视图会填充它；
其他视图直接读会拿到初始值，表现为"先逛过模板页才好使"的偶发缺陷。

填充链路唯一：`loadComposeTemplates()`（`app.js:7992-7995`）← `loadMailTemplatesView()`（`:7653-7657`）
← `refreshCurrentView()` 的 `state.view === "mail-templates"` 分支（`:1644`）。
同类还有 `state.composeTemplatePreviewExperts` / `state.composeTemplatePreviewAccounts`，
但那两个已有幂等守卫 `state.composeTemplatePreviewOptionsLoaded`（`:7980-7991`）可作范式。

**配套陷阱：`setView()` 不是 async。** `app.js:1619-1640` 中第 1634 行的 `refreshCurrentView()`
**未 await**。因此"切视图 → 立刻读该视图的数据"必然读到旧值。跨视图跳转必须在 `setView` 之前
自己 `await` 好所需数据，不得依赖 `setView` 的副作用。

正确先例：`openContactInList(contactId)`（`app.js:7198-7212`）= `setView("contacts")`
+ 显式 `await loadContacts()` + `await loadContactDetail()`，没有依赖副作用。

正确做法：任何在非宿主视图消费 `state.<视图数据>` 的新功能，都要自带幂等加载函数
（已非空则早退），并在跨视图跳转序列的**第一步**完成数据准备。

关联：[[K-detail-es-backed-fields-need-authoritative-read]]（详情页不得把别处缓存当权威数据源）、
[[K-view-registration-triad]]（新增侧栏 view 的四处注册；详情区子标签不适用该条）。
