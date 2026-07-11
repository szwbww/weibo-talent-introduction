---
id: K-expert-filter-registration-sites
domain: frontend
created: 2026-07-11
last_used: 2026-07-11
hit_count: 0
source: create-p:discipline-filter-batch-send
---

专家漏斗视图新增筛选控件须在 app.js 同步注册**五处**(缺一即隐蔽缺陷):① `loadContacts` 参数构造(~L3780,`params.set`);② `collectBatchMailContactIds` 参数构造(~L3511,按筛选批量发送——漏掉即静默错发,参见 K-bulk-actions-must-cover-full-filter-set);③ 筛选摘要文案(~L3495 `parts.push` 系列);④ `updateFilterBadge` 活跃计数数组(~L10061);⑤ change 监听 id 数组(~L10086,触发 `reloadContactsFromStart`)。HTML 侧控件统一为 `#contactsFilterGroup` 内 `label.toolbar-label > select`(styles.css:353,select 无 class)。行号随版本漂移,改前先 grep `expertRegionFilter` 复核全集。
