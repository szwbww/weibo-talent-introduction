---
id: K-backfill-column-specific-update
domain: campaign
created: 2026-06-29
last_used: 2026-06-29
hit_count: 1
source: fix-v:01-contact-country-foundation:fix-1
severity: P1
---
经验：当回填计划声明“只写某一列”时，使用 `CrudRepository.save(entity.copy(field = value))` 不等价于列级更新；聚合保存会扩大写边界，可能覆盖同一行运行期间其它路径写入的字段。
正确做法：在 repository 上提供 `@Modifying @Query("UPDATE table SET target_col = :value WHERE id = :id")` 这类列级更新方法，回填服务只调用该方法；测试 verify 定向 update，而不是 verify `save(...)`。
反例：`ContactCountryBackfillService.kt:68` 用 `expertContactRepository.save(contact.copy(country = country))` 执行 country 回填，违反 `expert_contact.country` 回填只写 country 的不变量。
