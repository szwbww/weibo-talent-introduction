---
id: K-grounded-json-paragraph-order
domain: llm
created: 2026-07-27
last_used: 2026-07-27
hit_count: 5
source: create-p:ai-reply-grounded-server-owned-envelope-plan
last_source: create-p:ai-reply-grounded-server-owned-envelope-plan
severity: P1
---
经验：让模型复制 paragraphs 与其数组顺序，会把服务端已经确定的展示顺序变成无业务价值的结构失败点；但把 claims 简单转成 map 又可能吞掉重复或缺失。
正确做法：paragraph grouping/order、missingFacts 与 review 元数据只由 immutable server plan 持有；模型仅返回 claimKey→text。服务端先验证 claimKey 唯一且集合精确相等，再按 plan 顺序绑定和组装；模型数组顺序不具 authority，duplicate/missing/unknown key 一律 invalid/fallback。
