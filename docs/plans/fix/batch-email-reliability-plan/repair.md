# Repair Plan: batch-email-reliability-plan

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun/docs/plans/2026-09-26/batch-email-reliability-plan.md
Governing identity: SHA-256 d0226d4fd73fd6e542d77a85ceab3d9285e0aacef4047668c0e7983735890a77; recorded commit 38ba555b4147970ee77569e71f863955e2c4a2b5; approved target-only amendments A1–A5.
Verification report: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun/docs/plans/review/batch-email-reliability/machine-verification.md — Epoch 1; independent reviewer /root/aggregate_reviewer; FAIL / INITIAL; finding V-1.
Implementation boundary: 64c0394a940bd79c2ecc04e5c497650f045faa75..418c77ff35fff6a570ded92f5bb64e523a603f50
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun
Target branch: fast/batch-email-reliability-rerun
Prior code head: 418c77ff35fff6a570ded92f5bb64e523a603f50; subsequent review-only documentation commits do not replace this code boundary.

## Objective

介绍邮件执行在历史过滤预扫描期间收到取消后，停止后续分页/漏斗层扫描并返回、记录 CANCELLED；全部候选被过滤后的空结果不得覆盖取消。

## Findings in Scope

| Finding | Severity | Requirement | Root Cause | Classification |
|---|---|---|---|---|
| V-1 | P1 | 子计划02 T3，第103行：“长扫描需可取消……持久化 CANCELLED，不误报 COMPLETED”；I-4验收及A-3取消要求 | 新增执行前 countEsTargets 历史过滤 scroll 没有接入执行取消信号，callback恒true；随后 totalEstimate==0 直接返回 emptyResult，绕开末尾取消收尾 | REPAIRABLE |

证据：ManualInitialOutreachService.kt:564–579、1658–1674、1105–1119；ExpertSearchService.kt:754–767 已支持 callback=false 停止及 finally 清理；TaskExecutionService.kt:167–198 采用 taskFinalStatus 持久化。独立临时探针使用现有编译产物和 Mockito，首个历史过滤批次设置取消，在两个漏斗层仍继续共4次callback，最终 cancelled=true / finalStatus=COMPLETED / taskFinalStatus=SUCCESS / wasCancelled=false。探针日志：/private/tmp/aggregate-batch-cancel-probe.log。缺陷与修复依据完整记录于上述持久化审查报告，临时日志不是执行前置依赖。

## Findings Excluded

| Finding | Reason |
|---|---|
| N/A | 无其它确认缺陷纳入本修复；既有深分页/跨层估算重复等非目标不扩修 |

## Unchanged Contract

- 最新有效原始验证谓词、一年边界、固定北京now、500邮箱分批、规范化和只读过滤保持不变。
- 预估请求仍沿用既有完整计数行为；过滤off仍走原count快路径，不新增历史查询。
- 介绍/材料实际收件地址、候选顺序、原始页分页、去重及成功发送额度保持不变。
- 发送前实时验证独立；局部暂缓、全局停止、249两次/500ms、审计失败和SMTP语义不变。
- 配置默认/持久化、手动快照隔离、UI、autoEnabled/cron、迁移、保留策略不变。
- 不新增后台线程、类、状态、接口、缓存、schema、数据库迁移或生产外部调用。不得修改原计划、其它产品文件或测试文件。
- 人工验收仍为PENDING；测试通过不替代人工验收。

## Authorized Files

| File | Purpose |
|---|---|
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt | 将执行取消接入历史过滤预扫描及空结果收尾 |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun/src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | 在既有服务测试内新增一个判别性取消回归场景 |

仅以上两文件属于产品提交授权。下述执行交接文档是单独限定的证据写入，不属于产品修复范围。

## Repair Tasks

### R-1: 历史过滤预扫描与空结果使用同一取消边界

- Resolves: V-1。
- Root cause: 执行前历史计数没有使用任务取消信号，且空结果提前返回绕开取消终态。
- Files: 上述两份 Authorized Files。
- Change: 介绍邮件执行的历史过滤预扫描须在后续页及下一漏斗层前响应现有 progressStore 取消信号，利用已有 scroll callback 停止能力保留 finally 资源清理；结束预扫描后、尤其零候选提前返回前，取消必须优先决定进度及结果终态。正常预估不受执行取消信号影响。已取消的未完成扫描不得继续选号、验证或SMTP，也不得作为正常完成上报。
- Regression test: 在现有服务fixture安排首个历史查询批次将取消标记置true并使该批全部被排除；scroll mock尊重callback返回值，并准备后一页和第二漏斗层。断言后续页/层及其历史查询均不执行、无选号/验证/发送、最后进度CANCELLED、wasCancelled=true、finalStatus/taskFinalStatus均CANCELLED、stopReason=CANCELLED。以同一场景覆盖oneRoundOnly=false/true，防空结果正常完成及原一轮PAUSED分支覆盖取消。
- Existing verification: 下列全部命令；重点检查既有过滤off快路径、过滤on正常预估、整页过滤后继续、迭代期间取消、暂缓后取消和全局故障计数。
- Must not change: 上述 Unchanged Contract；复用既有取消/终态/统计机制，不伪造发送量或完整扫描结果。
- Prohibited: 修改ExpertSearchService、TaskExecutionService、OutreachTargetIterator、其它测试、UI、schema、HTTP客户端；调整断言以接受SUCCESS/COMPLETED；以异常失败代替CANCELLED。

## Verification Commands

全部命令cwd均为 /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun，Maven串行执行。MySQL命令使用已确认的环境兼容参数，必须实际运行MySQL 8.0.36，skipped不得记PASS。

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchSendTaskConfigServiceTest,ManualInitialOutreachServiceTest,OutreachTargetIteratorTest,BatchSendTaskRuntimeIntegrationTest test`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchEmailVerificationServiceTest,TaskRetentionMigrationTest test`
3. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=BatchEmailVerificationRepositoryIT -Dapi.version=1.40 test`
4. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest -Dapi.version=1.40 test`
5. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest test`
6. `node --test src/test/js/batchEmailVerification.test.js`
7. `node --test src/test/js/*.test.js`
8. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
9. `git diff --check`
10. `git diff --check 418c77ff35fff6a570ded92f5bb64e523a603f50`

## Completion Criteria

- V-1判别性场景通过：首次过滤批次取消后无后续页/层扫描，无验证/SMTP，进度和TaskExecutionSummaryProvider终态为CANCELLED；oneRoundOnly两个值均成立。
- 非取消空结果、正常过滤预估和执行、关闭过滤、原有迭代取消及验证故障规则继续通过。
- 所有必需命令成功；真实MySQL两组测试无跳过；完整Maven中的既有跳过须如实记录，不替代明确要求的数据库验收。
- 产品差异仅限两份Authorized Files。不得修订baseline合同。
- 产品提交和单独交接证据提交完成，工作树/index干净后发出READY_FOR_VERIFICATION；独立聚合审查及人工验收仍须后续完成。

## Human Approval

执行须由人明确批准此文件当前内容。批准并执行的唯一入口：

`$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun/docs/plans/fix/batch-email-reliability-plan/repair.md`

代理建议、自动继续或内部skill调用不构成批准。

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun/docs/plans/fix/batch-email-reliability-plan/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only the two Authorized Files, with product commit subject `fix(batch-email): honor cancellation during historical filtering`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun/docs/plans/review/batch-email-reliability/repair-execution.md` with the exact human approval source, repair identity/SHA-256, pre/post code SHAs, evidence parent SHA, changed files, commands and exit codes/counts, deviations, executor identity when exposed, and clean-state evidence. Preserve prior epochs append-only.
4. Exactly one docs-only evidence commit containing only that execution handoff, with evidence commit subject `docs(review-fast-p): record batch email cancellation repair execution`. Stage only the exact handoff path; do not stage this repair plan or unrelated controller evidence.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
