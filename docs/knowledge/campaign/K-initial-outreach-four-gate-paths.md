---
id: K-initial-outreach-four-gate-paths
domain: campaign
created: 2026-08-24
last_used: 2026-08-24
hit_count: 0
source: create-p:expert-rnd-classification
---

INTRODUCTION 首发不是单一路径，至少有四个必须同时审计的门禁点：

1. `InitialOutreachService`：旧 scheduler 同步分支和 MQ consumer 最终共享此 service；
2. `ManualInitialOutreachService.buildEsFiltersForLevel`：批量 ES 新目标，且被 count/page 复用；
3. `RecipientScope.matchesExpert`：MySQL NEW 重试联系人加载 ES profile 后的内存过滤；
4. 两个发送 service 的最后发送前检查：防查询/缓存/未来重构绕过。

新增收件人资格规则若只改 1 或 2，会静默漏掉其他入口。批量 preview 必须继续复用
`buildEsFiltersForLevel + buildRetryableTargets`；MATERIAL_REMINDER 是否应用规则必须显式声明，
不能因共享 RecipientScope 而被意外波及。
