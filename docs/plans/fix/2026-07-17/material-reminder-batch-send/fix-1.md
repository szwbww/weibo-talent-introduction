# fix-1：材料提醒统一批量发送

## 原计划 / 子计划引用

`docs/plans/2026-07-13/material-reminder-batch-send.md`

## 约束摘录

- I-7：INTRODUCTION 的显式模板必须是 enabled `INTRODUCTION`；REMINDER 必须是 enabled `MATERIAL_REMINDER`；配置写入和任务启动均校验。
- I-9：材料提醒复用当前类型的 `dailyCap`、轮次、自检、额度与节流，不得绕过发送保护。
- I-8：执行继续通过同一 `MANUAL_INITIAL_OUTREACH` mutex 串行化。
- 修正记录 R1：`dailyCap` 为跨调用、跨重启的自然日成功总量。

## 修正记录表

| 编号 | 级别 | 发现 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | 任务启动的模板二次闸门对 INTRODUCTION 直接返回成功；旧 KV、直接数据库写入或模板类型被后台改写后，可用显式非 INTRODUCTION 模板启动介绍邮件。 | 存在历史/外部写入的显式模板配置时触发；低频，但会错误发送邮件。 |
| P1-2 | P1 | REMINDER `dailySentTotal` 每个 execution 从 `0` 初始化，只在内存中递增；同日第二次手动执行、cron 重触发或进程重启可再次发送最多 `dailyCap` 封。 | 每日多次触发、人工补跑或重启后触发；常规运营可发生。 |

## 修复规格

### P1-1：启动时对 INTRODUCTION 也校验显式模板

- 文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`
- 修改：`validateTemplateGate()` 对 INTRODUCTION 的 `templateId == null` 保持允许；非空时加载模板，要求 enabled 且 `mailType == INTRODUCTION`。REMINDER 规则保持不变。
- 预期：任何类型配置在 `launchExecution()` 前均被校验；不匹配/禁用/不存在时返回 409，且不得创建 progress/execution 或投递邮件。
- 测试：在既定 `ManualInitialOutreachServiceTest.kt` 的控制服务 scoped tests 中覆盖 INTRODUCTION 的非空错误类型、禁用模板和 null 默认模板三种路径。

### P1-2：按自然日持久化累计 reminder dailyCap

- 文件：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt`、`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
- 修改：新增只读统计，按 `direction=OUTBOUND`、`mailType=MATERIAL_REMINDER`、`sendStatus=SENT`、`sentAt >= 当日零点` 统计成功数；`runMaterialReminderBatch()` 以该值初始化剩余额度，并只对本次成功递增。共享 mutex 保证进程内两类型不并发；重启和下一次执行仍从持久化记录恢复。
- 预期：当天已成功 N 封时，任一新 execution 最多再发送 `max(0, dailyCap-N)` 封；FAILED/CANCELLED 不计入；次日自然恢复完整 cap。
- 测试：先模拟当天已有 SENT，再执行 reminder，断言只发送余量；模拟 FAILED 不减少余量；第二次 invocation 不得超 cap。

## 当前状态

- 编译：PASS。
- Node：PASS — 20 passed。
- Maven：PASS — 1511 passed，0 failed，3 skipped。

## 合规审计

- I-1：✅ `index.html:580` 仅保留 `bulkOutreachBtn`；旧 batch-tag 链路已删除。
- I-2：✅ `BatchSendSettingService.kt:23-29,63-78,84-102` 使用独立 key namespace 与类型配置。
- I-3：✅ `ManualInitialOutreachService.kt:998-1043` 固定 APPLICATION+精确 tag、分页、ORCID join、去重。
- I-4：✅ `app.js:5511-5584,5589-5630` 按 type/token 加载 provider 与 pending count。
- I-5：✅ `ManualInitialOutreachService.kt:1028-1040` 只读 tag 搜索；提醒路径未调用标签写入。
- I-6：✅ `ManualInitialOutreachService.kt:998-1043,251-257` 有 >10000 前置拒绝与发送前 SENT 双检。
- I-7：❌ `BatchSendControlService.kt:224-226` 对 INTRODUCTION 直接跳过启动校验；`257-259` 因此可启动错误显式模板。
- I-8：✅ `BatchSendControlService.kt:276-280` 用共享 task mutex；`BatchSendScheduler.kt:48-90` 分类型 future。
- I-9：❌ `ManualInitialOutreachService.kt:158,185,271` dailyCap 仅本次 execution 内累计，违反修正记录 R1。
- I-10：✅ `ManualExpertMailService.kt:60-77,187-194` 写 OUTBOUND/OPERATOR，MATERIAL_REMINDER 保持原状态。
- I-11：✅ `app.js:5565-5630` token 与 type 双重检查迟到响应。
- S-1/S-2/S-3：✅ `index.html:580,1001-1024` 复用既有入口/ID；无 styles.css 改动。
- No extras：✅ 业务改动限定原计划范围；本 fix 的 repository 扩展仅为 R1 持久计数。

## 语义完整性检查

- Accumulation：❌ P1-2；`dailySentTotal` 为 per-run 变量，但 `dailyCap` 必须跨调用累计。
- State machine：✅ `PAUSED` 可由 `resumeSchedule()` 回到 IDLE；共享执行冲突返回 409。
- Cross-plan：✅ N/A（单计划）。
