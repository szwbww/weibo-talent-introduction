---
id: K-group-before-pagination
domain: mail
created: 2026-09-09
last_used: 2026-09-09
hit_count: 5
source: create-p:mailbox-pending-by-expert
severity: P1
---
经验：列表若以“专家”为分页单位，必须先在数据库按 `expert_contact_id` 聚合并分页，再批量查询该页专家的子记录；不能先分页邮件再由前端 `groupBy`，否则同一专家会跨页、专家总数和组内数量都会失真。纯来信分组可用 `MAX(received_at)`；排序指标必须按当前产品契约选择：可用入站received_at与出站COALESCE(sent_at,created_at)合成事件MAX表示最近活动，但不得把该指标当作所有会话列表的强制规则；2026-09-09收发件箱契约改为全部tab待处理优先、组内最近来信倒序，发件不提升排序，计数应使用去重专家数，并为第二段 `IN (:expertContactIds)` 查询设置空页短路。
