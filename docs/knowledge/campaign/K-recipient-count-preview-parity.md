---
id: K-recipient-count-preview-parity
domain: campaign
created: 2026-08-13
last_used: 2026-08-13
hit_count: 1
source: plan:2026-08-13:06-recipient-count-preview
severity: P1
---
经验：批量发送的"收件人预估"数字如果与实际执行目标数不一致，运营会据此做错误决策（低估则超发、高估则漏发）。预估必须与执行**同源**，而不是另写一套查询。

正确做法：
1. 预估接口的入参直接用执行快照 `BatchExecutionSnapshot`（不另设预估 DTO），入参即执行参数，新增过滤维度时无需改两处。
2. 预估计算复用执行路径的同一套目标计算：`RecipientScope.fromSnapshot` + `buildRetryableTargets` + `countEsTargets`（INTRODUCTION），MATERIAL_REMINDER 复用 `buildMaterialReminderSnapshot(scope, config).targets.size`。`countBySnapshot(snapshot)` 与 `runIntroductionFromSnapshot` 的 `totalEstimate` 必须数值相等（单测断言）。
3. 预估路径必须零副作用：不创建 `task_execution`、不写 `expert_contact`、不建 campaign。campaign 用只读 `campaignRepository.findByCampaignCode("MANUAL_OUTREACH")` 判空——**不得**调 `getOrCreateManualCampaign()`（它会建行）。
4. 前端并发保护：预估请求用序号丢弃过期响应，慢请求不得覆盖新结果；防抖 500ms；失败显示"预估不可用"而非报错弹窗，避免打断编辑。

反例：预估接口为 GET 且入参是独立 DTO、预估另写 count 逻辑 → 两套实现必然漂移（见 K-batch-send-filter-retry-parity 的同源原则）。
