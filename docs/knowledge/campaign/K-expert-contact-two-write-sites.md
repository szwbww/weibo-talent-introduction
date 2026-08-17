---
id: K-expert-contact-two-write-sites
domain: campaign
created: 2026-06-29
last_used: 2026-08-16
hit_count: 5
source: create-p:contact-country-foundation
---
经验：`ExpertContact(...)` 全仓只有 2 处**构造**（新建行）——`InitialOutreachService.kt:45`（自动首发）与 `ManualInitialOutreachService.kt:273`（手动/调度批量，`existingContact ?: run { save(...) }`）。两处作用域内都持有 `expert: ExpertProfile`，可取 `.country` 等画像字段。其余所有 `expertContactRepository.save(...)` 都是 `contact.copy(...)` 更新已有行。
推论：给 expert_contact 加"创建期固化"的新字段，只需改这 2 处构造 + 一个回填器；`copy(...)` 路径给字段默认值即自动保留原值，不必逐处改。
关联：服务商归一的唯一来源是 `ProviderResolver.resolve(email)`（值域 gmail/outlook/yahoo/edu/tencent/netease/other），发送流程 ManualInitialOutreachService:269 已用；任何"按服务商"统计都应复用它而非另写域名集合。地区折算唯一来源 `CountryContinentMapping.toRegion`（见 K-agg-filter-source-of-truth）。

> 2026-08-10（sender-binding-01-schema-and-establish，P1 落地后）新增第 3 个写路径：`ExpertContactRepository.updateBindingById` 列级补写（`SenderAccountBindingService.bindIfAbsent` 使用，只 SET `bound_sender_account_code` / `sender_account_bound_at` 两列，禁止聚合 save 回写整行）。
