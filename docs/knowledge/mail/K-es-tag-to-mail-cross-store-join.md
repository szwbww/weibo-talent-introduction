---
id: K-es-tag-to-mail-cross-store-join
domain: mail
created: 2026-07-02
last_used: 2026-09-12
hit_count: 6
source: create-p:ai-training-redesign
---
经验：按 ES `tags` 字段筛选 MySQL `mail_record` 数据是跨存储查询，不能在单个 SQL 中完成。
正确做法：先从 ES 查出匹配的 `orcidId` 列表 → 通过 `expert_contact.orcid_id` 查出 `contactId` 列表 → 作为 IN 条件查 `mail_record`。注意 ES 返回的 orcidId 数量可能很大，需要设合理上限或分批。
关联表：`expert_contact.orcid_id` 是 ES 到 MySQL 的唯一桥接字段。

补充（create-p:standalone-historical-follow-up-pilot，2026-09-12）：当 MySQL 邮件成功记录与 ES 专家标签由同一外部 worker 串联时，不能把二者伪装成原子事务。`mail_send_attempt/mail_record` 的 SENT 是投递事实源；ES 标签只能在 SENT 提交后写，并允许通过 SENT 记录幂等重建。标签失败只形成可观测的同步滞后，绝不能驱动邮件重发。更新标签前还必须先按 `orcidId` 搜真实 ES `_id`，不得假设文档 `_id` 等于 ORCID。
