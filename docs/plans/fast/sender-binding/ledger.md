# Fast-P Ledger — master: docs/plans/2026-08-10/00-main-plan-sender-binding.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-08-10/00-main-plan-sender-binding.md (commit 89a216412bc53bebd93300ada6bf817a7c6c39c7)
- Amendments: A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11
- Master base: e6662677cc715421566006bbb90e3d47a75302b6
- Branch: fast/sender-binding
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/sender-binding
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-10T16:30:00+08:00
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A
- Active agents: all five children terminal (p1..p5 LIGHT_PASS_WITH_NOTES)

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| p1 | docs/plans/2026-08-10/sender-binding-01-schema-and-establish.md | commit:76778b7de0b7da6f7bd40069e4a23e5e684e2730 | none | 2 | LIGHT_PASS_WITH_NOTES | e6662677cc715421566006bbb90e3d47a75302b6 | d957683635a304d7b2f7611053250546f720e638 | 0 | — | d957683635a304d7b2f7611053250546f720e638 | 49911a77af1c9297cc5268887a2fd248c7f95f11 | 基座：字段/回填/建立点/解析服务，10 文件；epoch 1 = PLAN_CONFLICT（未列入清单的 BatchSendTaskRuntimeIntegrationTest.kt 编译依赖），A1/A2 修订后恢复；RECORD_ONLY: O-1（-Dtest=A+B surefire 语法怪癖）/ O-2（T1.3 措辞 bindOnCreate vs bindIfAbsent）见 verify-log |
| p2 | docs/plans/2026-08-10/sender-binding-02-send-path-consistency.md | commit:5d222f1a1c5851cf54478cbca9353ee4819d634f | p1 | 2 | LIGHT_PASS_WITH_NOTES | d957683635a304d7b2f7611053250546f720e638 | 5dc9f95cb4782c68b53dd0ecdbaa89853ecb9a3b | 0 | — | 5dc9f95cb4782c68b53dd0ecdbaa89853ecb9a3b | f9bfb6f60fa932c4589367d5411d29a17b424e0f | 事故直接修复，10 文件；epoch 1 = PLAN_CONFLICT（未授权测试文件），A3/A4/A5 修订后恢复并回退可空参数变通；RECORD_ONLY: O-1（p1 fix-log.md EOF 空行）见 verify-log |
| p3 | docs/plans/2026-08-10/sender-binding-03-assignment-stock-balance.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | p1,p2 | 1 | LIGHT_PASS_WITH_NOTES | 5dc9f95cb4782c68b53dd0ecdbaa89853ecb9a3b | 66e19ecf43a5bb44487adea2b9ce687612938d6e | 0 | — | 66e19ecf43a5bb44487adea2b9ce687612938d6e | 02f2a0f8a5ad92a79f72144960578b23cbac6634 | 打分计入存量，7 文件；桩 arity 偏差判定为 matcher 数适配（无断言变化）；RECORD_ONLY: O-1/O-2 见 verify-log |
| p4 | docs/plans/2026-08-10/sender-binding-04-rebind-api-and-audit.md | commit:7b1597fabd9d961358208659ef436e9a5f313039 | p1,p2 | 1 | LIGHT_PASS_WITH_NOTES | 66e19ecf43a5bb44487adea2b9ce687612938d6e | 4330726e29bb71b438e2b611437e447e7dc223f2 | 0 | — | 4330726e29bb71b438e2b611437e447e7dc223f2 | e84aed514831b882beeaa0a891043ebec5f16080 | 换绑/迁移/审计，8 文件（A6 修订）；RECORD_ONLY: O-1（p3 fix-log.md EOF 空行）/ O-2（boundary 含 harness docs）见 verify-log |
| p5 | docs/plans/2026-08-10/sender-binding-05-frontend-visibility.md | commit:f58b7f0b35a31512a054b47303ec2fd61f5fafec | p3,p4 | 2 | LIGHT_PASS_WITH_NOTES | 4330726e29bb71b438e2b611437e447e7dc223f2 | ce353c818028e86615e95b1b5a716463d06969af | 1 | 60e8e3c04400643dbd27abc6a826cf20df250d19 | 60e8e3c04400643dbd27abc6a826cf20df250d19 | 35c924ceaccc1238d255aff9ae905d9834fb30f7 | 前端可见性，10 文件；epoch 1 = PLAN_CONFLICT（MailSenderAccountContextTest 装配缺 bean，实施 commit 已落库），A10/A11 修订后 round 1 修复（60e8e3c0）；RECORD_ONLY: O-1（Array.isArray 守卫）/ O-2（p4-epoch 文档 EOF 空行）见 verify-log |

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
| A6 | docs/plans/2026-08-10/sender-binding-04-rebind-api-and-audit.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | commit:7b1597fabd9d961358208659ef436e9a5f313039 | M-2 | P4 控制器注入非默认参数使 ExpertContactManagementControllerTest.kt:17 命名参数构造编译失败，授权构造实参 +1（不改断言） | HUMAN:批准 A6+A7（推荐），2026-08-10T17:50+08:00 |
| A7 | docs/plans/2026-08-10/00-main-plan-sender-binding.md | commit:403169e514413c693c2bad40b7b981ccf3956d9d | commit:e6d2553c91e4645517e88cca38edd1430c6a125b | M-2 | M-2 所有权矩阵同步：ExpertContactManagementControllerTest.kt 的 P4 列 | HUMAN:批准 A6+A7（推荐），2026-08-10T17:50+08:00 |
| A8 | docs/plans/2026-08-10/sender-binding-05-frontend-visibility.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | commit:e19c48b7ec4a13300d2845b75a8ac6897799bd61 | M-2 | P5 在 MailSenderAccountService 注入非默认参数使 MailSenderAccountServiceTest.kt 5 处位置传参构造编译失败，授权构造实参 +1（M-4 锁定测试逐字不变） | HUMAN:批准 A8+A9（推荐），2026-08-10T18:05+08:00 |
| A9 | docs/plans/2026-08-10/00-main-plan-sender-binding.md | commit:e6d2553c91e4645517e88cca38edd1430c6a125b | commit:d788da93320c3d9e4fcc2823294779d465fde945 | M-2 | M-2 所有权矩阵同步：MailSenderAccountServiceTest.kt 的 P5 列 | HUMAN:批准 A8+A9（推荐），2026-08-10T18:05+08:00 |
| A10 | docs/plans/2026-08-10/sender-binding-05-frontend-visibility.md | commit:2df2fca0e3ba5fda932a08c028f34bd0cc424d30 | commit:f58b7f0b35a31512a054b47303ec2fd61f5fafec | M-2 | P5 注入 ExpertContactRepository 使 MailSenderAccountContextTest.kt（ApplicationContextRunner 装配）NoSuchBeanDefinitionException，授权 withBean 注册 +1（不改断言） | HUMAN:批准 A10+A11（推荐），2026-08-10T18:20+08:00 |
| A11 | docs/plans/2026-08-10/00-main-plan-sender-binding.md | commit:d788da93320c3d9e4fcc2823294779d465fde945 | commit:89a216412bc53bebd93300ada6bf817a7c6c39c7 | M-2 | M-2 所有权矩阵同步：MailSenderAccountContextTest.kt 的 P5 列 | HUMAN:批准 A10+A11（推荐），2026-08-10T18:20+08:00 |
