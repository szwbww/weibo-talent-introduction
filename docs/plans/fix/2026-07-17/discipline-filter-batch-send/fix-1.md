# 学科筛选与批量发送过滤配置 — fix-1

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-11/discipline-filter-batch-send.md`
- 复验对象：Plan B（系列计划 2/2）
- 轮次：1/3

## 约束摘录

- I-2：待发送数与实际批量发送范围同源；配置 discipline 后，UI 待发送数与发送目标必须一致。
- 需求 2：pending-count 与实际批量发送范围同步受配置约束。
- A-3：配置仅理工科后，待发送数按该配置计算并持久化。
- A-4：配置仅理工科后，本轮实际发送专家全部为 STEM。

## 修正记录表

| ID | 级别 | 问题 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | `discipline` 只进入 ES 新目标过滤；`NEW` 且未有 SENT introduction 的重试联系人由 `buildRetryableTargets()` 无条件加入发送队列和 pending 的 retryable 数。配置“仅理工科”后，曾暂时失败或中断的 HUMANITIES 联系人仍会被重发。 | 常态下 retryable 可能为 0；任何 SMTP 临时失败、执行中断或遗留 NEW 联系人时触发。 |

## 修复规格

### P1-1：过滤重试联系人并统一 count/run 口径

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

1. 将 retryable 目标构造收敛为可被 `countPending()` 与 `runScheduledBatch()` 共同调用的单一私有路径，输入使用同一份 `BatchSendConfig.discipline`。
2. `discipline` 为空时保持现有重试逻辑；为 `STEM` / `HUMANITIES` 时，仅保留已加载 `ExpertProfile.disciplineCategory` 与该值相等的 `NEW`、未有 SENT introduction、且非 `EMAIL_INVALID` 联系人。
3. `countPending()` 的 retryable 数取上述过滤后集合；`runScheduledBatch()` 发送的 retryableTargets 也取同一集合。ES 新目标继续使用已有 `notContactedWithEmailFilters(emailDomain, discipline)`。
4. 不修改 `runScheduledBatch()` 的轮次、限额、预热、自检主循环；不接入 `InitialOutreachService` 或 `MailAutomationScheduler`；不新增迁移、状态或 DTO。

文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`

1. 覆盖 `discipline=STEM` 时非 STEM retryable 不计入 `countPending()`，且不会进入 `runScheduledBatch()` 的目标。
2. 覆盖 `discipline=""` 时 retryable 行为保持现状。

## 当前状态

- 编译：PASS（JDK 11，`mvn test`）
- 后端测试：PASS — 1355 passed, 0 failed, 0 errors, 3 skipped
- 前端测试：PASS — 203 passed, 0 failed
- `git diff --check`：PASS

## 合规审计

- I-1：✅ `ExpertSearchService.kt:28-38,56-58,777-779` 将 STEM/HUMANITIES/UNCLASSIFIED DSL 集中在 `disciplineFilter()`，列表与 ES 批量新目标共用。
- I-2：❌ `ManualInitialOutreachService.kt:98-101,139-142` 过滤 ES 新目标正确；但 `:577-596` 构造 retryableTargets 时未读取 discipline，`:80-90` 的 retryable 计数也未过滤，违反 pending/实际范围同步。
- I-3：✅ `BatchSendSettingService.kt:30,46,82,117-120,150,164-165` KV 读写、非法值回退和白名单均已实现；无迁移改动。
- I-4：✅ `app.js:3522-3550,3660-3813` 列表和按当前筛选批量收集均传 discipline。
- I-5：✅ `InitialOutreachService.kt`、`MailAutomationScheduler.kt` 无改动。
- I-6：✅ `app.js:3511-3515,3528-3550,3668,3813,5006,5087,10104,10121` 覆盖摘要、两类参数、徽章、监听与配置表单读写。
- S-1/S-2：✅ `index.html:525-533,1078-1085` DOM、id、class、option 值符合契约；`styles.css` 无改动。

## 语义完整性审计

- Accumulation check：✅ N/A（无本计划新增时间窗口累计器）。
- State machine check：✅ N/A（本计划不新增状态机）。
- Cross-plan check：❌ Plan A 提供 `disciplineCategory`，Plan B 的 ES 新目标读取该字段，但 `NEW` 重试联系人通过另一条读取路径绕过该约束；错误恢复场景（非 STEM 临时失败 → 配置 STEM → 重试）会错发。
