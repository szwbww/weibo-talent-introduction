---
id: K-es-tag-to-mail-cross-store-join
domain: mail
created: 2026-07-02
last_used: 2026-07-13
hit_count: 3
source: create-p:ai-training-redesign
---
经验：按 ES `tags` 字段筛选 MySQL `mail_record` 数据是跨存储查询，不能在单个 SQL 中完成。
正确做法：先从 ES 查出匹配的 `orcidId` 列表 → 通过 `expert_contact.orcid_id` 查出 `contactId` 列表 → 作为 IN 条件查 `mail_record`。注意 ES 返回的 orcidId 数量可能很大，需要设合理上限或分批。
关联表：`expert_contact.orcid_id` 是 ES 到 MySQL 的唯一桥接字段。
