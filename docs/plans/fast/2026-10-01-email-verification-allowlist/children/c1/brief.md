# Child c1 Brief — Emailable 放行结果：执行与审计（backend）

## Identity
- Child: c1（计划第 1/3 片）
- Master plan: `docs/plans/2026-10-01/email-verification-allowlist.md`（总览；3 子计划严格顺序 backend → config → frontend，共享文件不可并行修改）
- Child plan（完整合同，权威）: `docs/plans/2026-10-01/email-verification-allowlist-backend.md`
- 证据附件（逐字源码 / grep 回执）: `docs/plans/2026-10-01/email-verification-allowlist-evidence.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist`
- Branch: `fast/2026-10-01-email-verification-allowlist`
- child_base_sha: 见派发消息（已记录于 ledger）。

## Authorized files（仅此 10 个；超出必须 PAUSE，不得自行扩权）
1. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskRetentionMigrationTest.kt`

非产品输出：只允许写执行报告 `docs/plans/fast/2026-10-01-email-verification-allowlist/children/c1/execution.md`；其余路径只读。

## 关键不变量（以 child plan「关键不变量」全文为准）
- I-1 快照严格白名单：`BatchExecutionSnapshot.emailVerificationAllowedStates: List<String>? = null`；null 仅代表旧请求三态全放行；非 null 数组可为空但元素仅可精确为 `deliverable/risky/unknown`；`undeliverable`、未知值、非字符串元素在启动前拒绝；快照写入 `task_execution.request_payload` 后本次不读取可变配置。
- I-2 供应商事实不是放行决定：`provider_state/provider_reason/checked_at` 原样存；新请求、同执行内存复用、跨执行仓储复用都只复用供应商事实，按本次快照重算 PASS/SKIP；`undeliverable` 恒 SKIP；非法供应商状态仍 ERROR。仅在供应商返回已通过协议/邮箱回显校验的明确结果时应用列表；HTTP/超时/坏响应的 ERROR 不得按其残留 providerState 改为 PASS/SKIP。
- I-3 策略跳过不打异常标签：未选中结果写 `decision=SKIP`、`send_status=SKIPPED`、`send_reason=EMAIL_VERIFICATION_POLICY_SKIP`、`tag_status=NOT_REQUIRED`；零 `appendEmailAbnormalTag`、零 contact、零 SMTP、不占成功数/账号量，仅加 processed/skipped 并继续扫描补足本轮 `roundPassed` 配额；`undeliverable` 继续 `EMAIL_VERIFICATION_REJECTED` + 标签；审计写失败继续停止整次执行。
- I-4 一年内有效原始行复用 + 90 天清理保留：原始 `SKIP deliverable/risky/unknown/undeliverable` 与原始 `PASS deliverable/risky/unknown` 均视为有效供应商事实；`FIND_REUSABLE_BY_EMAILS_SQL`（注意：主查询与 `NOT EXISTS` 镜像两处谓词都要改）与 `TaskExecutionRepository.deleteOlderThan` 保留谓词对称增补 `SKIP deliverable`；仍要求 `request_count>0 AND reused_from_id IS NULL AND error_code IS NULL` 与一年窗口；复用行不延长保留。
- I-5 既有发信守卫不变：`emailVerificationEnabled=false` 零请求零明细；材料提醒开启验证仍拒绝；未完成/超时仍按单邮箱暂缓；鉴权/额度/限流/服务故障仍停止；`PASS+NOT_SENT → SENDING` 条件预占保持；发送前预占/配额/节奏不变。

## Downstream interfaces（c2/c3 依赖，必须成立且稳定命名）
- 快照字段名 `emailVerificationAllowedStates`；Jackson 绑定兼容旧 payload（缺字段 = null；非字符串元素必须被拒绝或绑定失败，不得被静默转为允许值）。
- 允许值集合与严格校验函数定义在 `BatchExecutionModels.kt`；c2 将复用它们做配置列 JSON 解析（本 child 不实现配置列解析）。
- `BatchEmailVerificationService.verify(..., allowedStates: List<String>? = null)` 第三参；现有直接调用不传 = 三态全放行；`ManualInitialOutreachService` 显式传快照值；相关 Mockito stub/verify 同步第三参。
- 原因码 `EMAIL_VERIFICATION_POLICY_SKIP` 进入 `BatchOutcomeReasonCodes`（含中文标签）；策略跳过四实值 `SKIP / SKIPPED / EMAIL_VERIFICATION_POLICY_SKIP / NOT_REQUIRED`；c3 按该码显示文案。
- state 比较沿用现有 `lowercase(Locale.ROOT)`；审计保留供应商原值。

## Required commands（必须执行并记录 exit/counts）
```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest,TaskRetentionMigrationTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -DmysqlIt=true -Dtest=BatchEmailVerificationRepositoryIT test
```
- MySQL IT 需 Docker/Testcontainers；Docker 不可用时明确记录「IT 未验证」，不声称通过。
- 全量 `mvn test` 是 run 级收尾命令（在 c3 收尾统一执行），不是本 child 的必需命令。
- 计划要求：执行前核对证据附件中的行号引用；若代码已变化，按实际代码定位，不照旧行号盲改。

## 范围与纪律
- 只改 Authorized Files；不重构相邻代码、不新增依赖、不新增表/迁移、不改 `Material reminder` 循环、不改历史不可投递预筛选（仍只按 `providerState == undeliverable`）。
- 修正 `BatchExecutionModels.kt` 内“占处理槽”的误导注释（计划明确），但不改计数循环行为。
- 测试只断言可观察状态，不复制内部 decision helper 实现。
- 不 push、不 merge、不 rebase、不 amend、不改写历史；实现提交：`feat(fast-p): implement c1`。
- 不提交 `docs/plans/fast/**`（由 controller 单独提交）；不触碰主工作区与其他 worktree。
