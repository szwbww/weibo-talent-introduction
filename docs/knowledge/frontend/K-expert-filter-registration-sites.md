---
id: K-expert-filter-registration-sites
domain: frontend
created: 2026-08-12
last_used: 2026-08-12
hit_count: 5
source: create-p:discipline-filter-batch-send
revalidated_by: create-p:batch-send-rhythm-and-filter-00-master
---

专家漏斗视图新增筛选控件须在 app.js 同步注册**五处**(缺一即隐蔽缺陷):① `loadContacts` 参数构造(`params.set`);② `collectBatchMailContactIds` 参数构造(按筛选批量发送——漏掉即静默错发,参见 [[K-bulk-actions-must-cover-full-filter-set]]);③ 筛选摘要文案(`parts.push` 系列);④ `updateFilterBadge` 活跃计数数组;⑤ change 监听 id 数组(触发 `reloadContactsFromStart`)。

HTML 侧控件统一为 `#contactsFilterGroup` 内 `label.toolbar-label > select`(**styles.css:431**,select 无 class)。

**2026-08-12 复核更正**:原条目记 `styles.css:353`,实测 `.toolbar-label` 规则块在 **`:431`**;`updateFilterBadge` 计数数组实测在 `app.js:11142` 附近、change 监听 id 数组在 `:11160` 附近(原记 ~L10061/~L10086)。行号漂移幅度已达千行量级——**本条目的行号只能当作"存在性提示",改前必须 grep `expertRegionFilter` 复核全集**,不可直接按行号定位。

只改既有筛选控件的**显示标签**(如地区英文→中文)时,五处注册点均无需变更,但第③处筛选摘要文案若含该维度的值,需一并本地化;见 [[K-region-constant-not-display-label]]。
