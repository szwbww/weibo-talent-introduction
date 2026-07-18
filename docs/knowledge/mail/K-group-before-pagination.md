---
id: K-group-before-pagination
domain: mail
created: 2026-07-18
last_used: 2026-07-18
hit_count: 0
source: create-p:mailbox-pending-by-expert
severity: P1
---
经验：列表若以“专家”为分页单位，必须先在数据库按 `expert_contact_id` 聚合并分页，再批量查询该页专家的子记录；不能先分页邮件再由前端 `groupBy`，否则同一专家会跨页、专家总数和组内数量都会失真。聚合排序应使用 `MAX(received_at)`，计数应使用去重专家数，并为第二段 `IN (:expertContactIds)` 查询设置空页短路。
