# Fast-P Ledger — master: docs/plans/2026-08-10/00-main-plan-sender-binding.md

- Status: RUNNING
- Master plan: docs/plans/2026-08-10/00-main-plan-sender-binding.md (commit 403169e514413c693c2bad40b7b981ccf3956d9d)
- Amendments: A1,A2,A3,A4,A5
- Master base: e6662677cc715421566006bbb90e3d47a75302b6
- Branch: fast/sender-binding
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/sender-binding
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-10T16:30:00+08:00
- Current child: p2
- Waiting role: VERIFIER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A
- Active agents: p2 implementer = P2Implementer-2 (epoch 2); p2 verifier = P2Verifier

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| p1 | docs/plans/2026-08-10/sender-binding-01-schema-and-establish.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | none | 2 | LIGHT_PASS_WITH_NOTES | e6662677cc715421566006bbb90e3d47a75302b6 | d957683635a304d7b2f7611053250546f720e638 | 0 | — | d957683635a304d7b2f7611053250546f720e638 | 49911a77af1c9297cc5268887a2fd248c7f95f11 | 基座：字段/回填/建立点/解析服务，10 文件；epoch 1 = PLAN_CONFLICT（未列入清单的 BatchSendTaskRuntimeIntegrationTest.kt 编译依赖），A1/A2 修订后恢复；RECORD_ONLY: O-1（-Dtest=A+B surefire 语法怪癖）/ O-2（T1.3 措辞 bindOnCreate vs bindIfAbsent）见 verify-log |
| p2 | docs/plans/2026-08-10/sender-binding-02-send-path-consistency.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | p1 | 2 | LIGHT_PASS_WITH_NOTES | d957683635a304d7b2f7611053250546f720e638 | 5dc9f95cb4782c68b53dd0ecdbaa89853ecb9a3b | 0 | — | 5dc9f95cb4782c68b53dd0ecdbaa89853ecb9a3b | — | 事故直接修复，10 文件；epoch 1 = PLAN_CONFLICT（未授权测试文件），A3/A4/A5 修订后恢复并回退可空参数变通；RECORD_ONLY: O-1（p1 fix-log.md EOF 空行）见 verify-log |
| p3 | docs/plans/2026-08-10/sender-binding-03-assignment-stock-balance.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | p1,p2 | 1 | PENDING | — | — | 0 | — | — | — | 打分计入存量，7 文件 |
| p4 | docs/plans/2026-08-10/sender-binding-04-rebind-api-and-audit.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | p1,p2 | 1 | PENDING | — | — | 0 | — | — | — | 换绑/迁移/审计，7 文件 |
| p5 | docs/plans/2026-08-10/sender-binding-05-frontend-visibility.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | p3,p4 | 1 | PENDING | — | — | 0 | — | — | — | 前端可见性，8 文件 |

## Baseline
- 全量测试命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`，在 master base e6662677 上运行
- 结果：exit 0，BUILD SUCCESS；Kotlin surefire Tests run: 2236, Failures: 0, Errors: 0, Skipped: 4（179 个报告文件）；node --test 479 pass / 0 fail / 85 suites
- 基线失败集合：无

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-10/00-main-plan-sender-binding.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | commit:96c81133a568ee6e9a0cbd2c9cbd68e502ea3480 | M-2 | P1 构造函数注入使未列入矩阵的 BatchSendTaskRuntimeIntegrationTest.kt（:655 位置传参）编译失败，M-2 矩阵补该文件的 P1 所有权行 | HUMAN:批准修订（推荐），2026-08-10T16:42+08:00 |
| A2 | docs/plans/2026-08-10/sender-binding-01-schema-and-establish.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | commit:76778b7de0b7da6f7bd40069e4a23e5e684e2730 | M-2 | 变更文件清单加 BatchSendTaskRuntimeIntegrationTest.kt，授权构造实参 +1（Mockito.mock(SenderAccountBindingService::class.java)） | HUMAN:批准修订（推荐），2026-08-10T16:42+08:00 |
| A3 | docs/plans/2026-08-10/sender-binding-02-send-path-consistency.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | commit:5d222f1a1c5851cf54478cbca9353ee4819d634f | M-2 | P2 绑定优先行为使 ManualInitialOutreachServiceTest.kt 9 例缺 resolveForSend 桩而 NPE（该测试类本就在 P2 验证命令内但不在变更清单），授权桩适配：不新增用例、不改断言语义 | HUMAN:批准 A3+A4（推荐），2026-08-10T17:20+08:00 |
| A4 | docs/plans/2026-08-10/sender-binding-02-send-path-consistency.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | commit:5d222f1a1c5851cf54478cbca9353ee4819d634f | M-2 | 非空构造注入使 ManualExpertMailServiceGateTest.kt:54 位置传参编译失败，授权构造实参 +1；并要求回退实施者的可空参数变通 | HUMAN:批准 A3+A4（推荐），2026-08-10T17:20+08:00 |
| A5 | docs/plans/2026-08-10/00-main-plan-sender-binding.md | commit:96c81133a568ee6e9a0cbd2c9cbd68e502ea3480 | commit:403169e514413c693c2bad40b7b981ccf3956d9d | M-2 | M-2 所有权矩阵同步：ManualInitialOutreachServiceTest.kt / ManualExpertMailServiceGateTest.kt 的 P2 列 | HUMAN:批准 A3+A4（推荐），2026-08-10T17:20+08:00 |
