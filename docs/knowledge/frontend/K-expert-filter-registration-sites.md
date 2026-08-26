---
id: K-expert-filter-registration-sites
domain: frontend
created: 2026-08-12
last_used: 2026-08-25
hit_count: 6
source: create-p:discipline-filter-batch-send
revalidated_by: create-p:batch-send-rhythm-and-filter-00-master
---

专家漏斗视图新增筛选控件须在 app.js 同步注册**五处**(缺一即隐蔽缺陷):① `loadContacts` 参数构造(`params.set`);② `collectBatchMailContactIds` 参数构造(按筛选批量发送——漏掉即静默错发,参见 [[K-bulk-actions-must-cover-full-filter-set]]);③ 筛选摘要文案(`parts.push` 系列);④ `updateFilterBadge` 活跃计数数组;⑤ change 监听 id 数组(触发 `reloadContactsFromStart`)。

HTML 侧控件统一为 `#contactsFilterGroup` 内 `label.toolbar-label > select`(**styles.css:431**,select 无 class)。

**2026-08-12 复核更正**:原条目记 `styles.css:353`,实测 `.toolbar-label` 规则块在 **`:431`**;`updateFilterBadge` 计数数组实测在 `app.js:11142` 附近、change 监听 id 数组在 `:11160` 附近(原记 ~L10061/~L10086)。行号漂移幅度已达千行量级——**本条目的行号只能当作"存在性提示",改前必须 grep `expertRegionFilter` 复核全集**,不可直接按行号定位。

只改既有筛选控件的**显示标签**(如地区英文→中文)时,五处注册点均无需变更,但第③处筛选摘要文案若含该维度的值,需一并本地化;见 [[K-region-constant-not-display-label]]。

**2026-08-25 再次复核（create-p:01-expert-list-type-filter）——五处已变为四处**：

实测 `grep -n "expertRegionFilter" src/main/resources/static/app.js` 命中 7 处，逐一核对后：

| 原条目第 n 处 | 当前状态 |
|---|---|
| ① `loadContacts` 参数构造 | **仍在** — 取值 `app.js:4855-4860`，`params.set` `:5009` 一带 |
| ② `collectBatchMailContactIds` | **已不存在** — 该函数名零命中；等价的参数构造点迁入 `initExpertGateFilter`（函数起始 `:12070`，构造块 `:12128-12136`） |
| ③ 筛选摘要文案 `parts.push` 系列 | **已不存在** — `parts.push` 的 19 处命中全部属于 AI 回复面板与回复拼装，无一属于专家筛选摘要 |
| ④ `updateFilterBadge` 计数数组 | **仍在** — `app.js:11854-11869`（原记 ~`:11142`） |
| ⑤ change 监听 id 数组 | **仍在** — `app.js:11878-11884`（原记 ~`:11160`） |

`.toolbar-label` 规则块实测在 **`styles.css:467-477`**（本条目原记 `:431`，更早一版记 `:353`——
三次记录三个行号，行号在本文件中已无参考价值）。

**新增事实**：工具栏内的**多值**筛选不要用 `<select multiple>`，用
`<span class="toolbar-label"><span class="tag-select" id="..."><button class="tag-chip" data-value="...">` ——
`#hasFieldTagSelect`（`index.html:564-572`）是活体先例，CSS 全在
`.tag-select`（`styles.css:576-581`）/ `.tag-chip`（`:583-600`）/ `:hover`（`:602-606`）/
`.active`（`:608-613`），零新增样式。外层必须是 `<span>` 不是 `<label>`（隐式聚焦转移）。
**chip 组没有 `change` 事件**，因此第 ⑤ 处注册对它不适用，必须在 chip 点击处理里主动调
`reloadContactsFromStart()`。
