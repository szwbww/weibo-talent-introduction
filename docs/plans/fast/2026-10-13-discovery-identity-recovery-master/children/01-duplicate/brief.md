# Child Brief — 01-duplicate（重复发现的同身份补证）

- Child ID: `01-duplicate`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-13/discovery-identity-recovery-01-duplicate.md`（此 worktree 内已提交于 b06a718；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`（含 A1—A9 修订；本子计划仅补证接口代码，不执行旧 10,686 名单；开发顺序要求 01/02 先行，04—10 依赖其已验证产物）。
- child_base_sha: `d8ca3ca28fc94c26ec796a04c6ee332c1e760533`（前序子计划 admission-relaxation 的 Code head，已 LIGHT_PASS_WITH_NOTES 验证）。
- 起始工作树 HEAD（开始前必须核对且 `git status` 干净）: `9d364fd89f0845220dc11ed5c8f2b49c32bf1fba`（含前序 fast-p 记账提交 docs/plans/fast/**，属 controller 记账，不得回退、不得纳入你的提交）。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- 要求使用 `execute-p` 技能执行本子计划。

## 前置已完成（可直接引用，勿重复实现）
- admission-relaxation 已验证：`ExpertRevalidationService.revalidateDiscovery` 软/硬分离（软原因不撤候选）、`DiscoveryReviewService.recordAutomatic` CAS 复读、`DiscoveryAdmissionPolicy.POLICY_VERSION=2026-10-13.1`、`InitializeRequest` mode/dryRun。
- 按计划："成功后入队并调用现有准入/重验接缝；禁止在 repair service 里复制政策"。

## Authorized files（精确 8 个；如需第 9 个文件先停止并返回 PLAN_CONFLICT）

| # | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 重复路径接入与消费错误传播 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt` | 新增：补证协调器及结果类型 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | 三层读取/唯一 ID 检查/条件 proof 更新 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt` | 身份修复失败的明确重试原因 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 同步/队列接缝与计数 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairServiceTest.kt` | 新增：证据矩阵及部分失败 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` | 新条件写入与运营字段保护 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt` | 部分失败不终结成功、租约重试 |

## Required commands（JDK 11 前缀不可省略；记录退出码与精确计数）
- C1: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test`
- C2: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityTest,DiscoveryReviewServiceTest test`（既有测试，仅执行、不得修改）
- 每次 `mvn test` 会自动执行 JS 门禁（exec-maven-plugin）；失败数必须为 0，总量不低于 1657（基线 1657/1657/0）。

## 必须兑现的合同要点（以子计划正文为准；这里是索引）
- I-1 只补证、不推测身份：incoming 只能来自现有提取器；必须有完整姓名及有效 identityEvidence；规范邮箱及已填姓名精确一致；已有 proof 若存在但无效/冲突，不自动覆盖；LEGACY_APPROVED 不改；空姓名不是已证实冲突 → 返回 `MISSING_NAME_NEEDS_RECOVERY`，不自动补名。
- I-2 同邮箱须唯一业务 ID、三层版本保护：补证前按规范邮箱查全量命中并按 `_id` 去重（原 `size=1` 结果只能用于发现重复，不能作为修复全集）；不同 ID 同邮箱一律冲突；读取该 ID 三层快照；非空作者编号任意相冲突即不写；每层只写 identityVerification，用 seq_no/primary_term；缺层不创建、运营字段不重写。查询失败不得当不存在。
- I-3 成功状态与重试真实：只有所有已有层一致且回读匹配才为 `REPAIRED`；否则 `PARTIAL/CONFLICT/FAILED`；部分失败让消费保留可重试错误，不能推进为修复成功；重复调用对已落目标 proof 的层视为已完成；不自动回滚另一人的并发改动；没有可信作者编号时补证可以成功，学术补全仍可 `NO_TRUSTED_ID`，不得偷用旧编号。
- 统计与传播：处理结果分别统计 `REPAIRED`、`ALREADY_VERIFIED`、`MISSING_NAME_NEEDS_RECOVERY`、`CONFLICT`、`PARTIAL`、`FAILED`；该文件内 `ConsumeOutcomeResult/QueuedItemConsumption` 增加默认 false 的 `identityRepairFailed`（仅内存返回值），`succeeded` 必须包含此项；`DiscoveryPipelineService` 将其记为 `IDENTITY_REPAIR_FAILED` 并沿原重试/租约处理；同步页完成也检查该失败，不推进失败页；合法且原 proof 已存在的重复流程沿用原入队行为。
- 回滚/留痕：只针对本次新增 proof、且当前 proof 等于本次目标且版本仍匹配的层，条件删除该字段；保存前值和结果供 02 报告消费；不动后续人工操作。
- 边界：无前端、无迁移、不新增 DB/ES 字段；不改队列 payload 契约以外语义；禁止任何生产动作/SMTP/发信；`docs/plans/fast/**` 不得进入你的提交。

## 下游接口（02/05 依赖，保持稳定）
- `DiscoveryIdentityRepairService`：服务端校验 + 分层结果类型（REPAIRED/ALREADY_VERIFIED/MISSING_NAME_NEEDS_RECOVERY/CONFLICT/PARTIAL/FAILED）；05 将在其上统一登记，02 将扩展「只填空姓名 + proof」受限模式；入口签名与结果枚举保持稳定。
- `ExpertIndexWriterService`：新增「邮箱全量命中」查询与「按版本条件仅写 proof」方法（02 将在此基础上加受限模式）。
- `DiscoveryPipelineService`：`IDENTITY_REPAIR_FAILED` 语义保持（05 沿用其传播规则）。

## 报告与提交
- 完整结果写入：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/children/01-duplicate/execution.md`（逐命令退出码/计数、改动摘要、未决问题）。
- 本地提交仅含 8 个授权文件：`feat(fast-p): implement 01-duplicate`。
- 返回仅限：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不审查后续子计划、不修复无关行为、不 push/merge/改写历史；遇到计划未覆盖的行为决策或需要新文件：返回 BLOCKED/PLAN_CONFLICT。
