---
id: K-batch-send-filter-retry-parity
domain: campaign
created: 2026-07-11
last_used: 2026-07-14
hit_count: 6
source: fix-v:discipline-filter-batch-send:fix-1
severity: P1
---
经验：批量发送存在 ES 新目标和数据库 NEW 重试联系人两条目标来源时，新增发送范围过滤若只接入 ES 查询，重试路径会静默绕过配置并造成错发。
正确做法：将两条来源置于同一个配置过滤语义下；pending 统计与实际发送必须复用已过滤的重试目标集合。
反例：`ManualInitialOutreachService.kt:577-596` 只按 NEW、SENT、EMAIL_INVALID 筛选重试联系人，未读取 `BatchSendConfig.discipline`。
