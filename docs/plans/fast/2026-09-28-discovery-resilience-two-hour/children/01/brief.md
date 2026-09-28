# Fast-P Child Brief — 01（深度发现：有限延迟续跑与每两小时调度）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-09-28/discovery-resilience-two-hour.md`，identity `commit:3f167a2820c3f9bdb344f19da2123d6a9ff2f12a`。
- 本 child 批准计划（完整合同，必须先通读）：同一路径 `docs/plans/2026-09-28/discovery-resilience-two-hour.md`（单子计划 run：master 计划即本 child 计划）。
- 只读证据附件（计划引用的 E-*/grep 收据）：同目录 `docs/plans/2026-09-28/discovery-resilience-evidence/`（`code-baseline.json`、`config-readers.txt`、`cursor-paths.txt`、`entrypoints.txt`、`progress-writers.txt`、`store-users.txt`、`budget-paths.txt`、`production-config.json`）。
- Worktree / branch / `child_base_sha`：见派发消息。
- 依赖：none。无下游 child；本 child 之后另有独立审查，不在本 run 内。

## 全局约束

1. JDK 11 固定：所有 Maven 命令必须 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
2. 只允许修改「Authorized Files」表内 8 个文件；不得新建白名单外文件；不得触碰已应用的 Flyway 迁移（本计划无 DB 迁移）；不得修改 `OpenAlexRequestPolicy`、`OpenAlexDataSource`、`RestTemplateConfig`、`TaskProgressStore`、通用任务 repository、任何前端文件（计划「变更文件清单」明确列出）。
3. 不得修改 `docs/plans/**`（fast-p 证据与计划由控制方提交）；不得 push、merge、rebase、squash、amend、reset；不得修改本 worktree 之外的主工作区。
4. 产品代码提交格式：`feat(fast-p): implement 01`；把 fast-p 报告/日志（`docs/plans/fast/**`）排除在该提交之外，报告单独留在工作树由控制方提交。
5. 若计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不要自行扩范围或改计划。
6. 本计划不含生产发布动作；R-0 的服务器探针与发布步骤不属于本 child（计划 T-4 部分为人工/上线阶段，执行者只做可在本机完成的实现与测试）。
7. 取消语义沿用现状：取消只止本轮，下一定时点仍可触发；不得实现“永久暂停”。

## Authorized Files（8）

| # | 文件 | 变更 | 归属 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 同轮有限恢复、上下文与计数、等待进度、时间测试接缝 | T-1/T-2 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SourceStats.kt` | 唯一 retry 观察对象及同文件类型 | T-1/T-2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryProperties.kt` | 搜索恢复间隔及校验 | T-1 |
| 4 | `src/main/resources/application.yml` | 搜索恢复列表、两小时默认 cron | T-1/T-3 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt` | 取消每日闸门、固定时区、准确初始化文案 | T-3 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 新恢复测试及现有边界回归 | T-1/T-2 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryPropertiesTest.kt`（新增） | 列表默认/绑定/非法值/空值关闭 | T-1 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt` | 两小时/互斥/模式/异常测试 | T-3 |

计划 T-1～T-4 与「验收标准」「关键不变量 I-1～I-8」给出逐条契约（配置项名与默认值、retry 字段与语义、恢复组上限 3/间隔 30s/120s/300s、12 次上限、游标与累计计数、`RETRY_WAIT` 字符串、cron `0 0 */2 * * ?` + `zone = "Asia/Shanghai"`、消息文案、测试用例）。以计划文本为准。

## 必须保持不变（计划 P-1～P-6 与 I-1～I-8）

- P-1 姓名—邮箱身份核验、学科准入、RAW→CANDIDATE 晋升、异步补全与晋升读投影全部保持；不为提高产量放宽条件。
- P-2 只有完整消费页才能推进游标；RAW 写失败、入队失败、去重失败、半页、取消、预算结束均不得跳页；旧查询与新查询不能混用检查点。
- P-3 共享 credits 账本、补全保留额度、429 冷却、UNKNOWN 预占保护、证书验证、禁止跨站认证重定向。
- P-4 手动/关键词/定时入口共享当前进程互斥；取消打断本轮（含重试等待）；永久停定时用 cron `-` 配置并重启生效。
- P-5 `pipeline-enabled=true` 的既有持久化暂停、恢复、tick 行为不变；本计划不切换模式。
- P-6 失败与部分成功如实记账；不把等待、取消、限额结束显示为「已全部穷尽」。
- I-1 仅白名单可重试错误进入恢复；每来源每轮最多 3 个恢复组；同页最多 12 次实际搜索尝试。
- I-2 页入口与查询归属保持；恢复使用本轮内存中的确切入口与前端 `sourceKey` 编码不变。
- I-3 额度/统计/deadline 跨恢复组累积；不新建 `DiscoveryStats`/executionId/deadline；不重复累加 `papers_processed_total`。
- I-4 等待是运行中状态：只新增来源停止原因字符串 `RETRY_WAIT`，不新增 task_execution 状态；等待期间 `sourceFailureCount=0`、`source.pendingWork=true`、`source.exhausted=false`。
- I-5 `SourceStats` 只新增一个可空对象字段 `retry { round, maxRounds, nextRetryAt, reason }`；ISO-8601 UTC；仅作观察，不用于启动恢复。
- I-6 两小时只改触发频率：默认 cron `0 0 */2 * * ?` + 显式 `zone = "Asia/Shanghai"`；删除旧「本日已执行」闸门；保留 `tryStartWithToken`；不得删除通用 `countScheduledSince/countActiveSince` API。
- I-7 取消/停定时/pipeline 暂停分别处理；等待检查粒度 ≤100ms；恢复检查不得因仍有重试条目发出下一请求。
- I-8 预算与安全闸门不绕过：429/`OpenAlexBudgetDeferredException` 不进网络延迟恢复；不得关闭证书验证或自动跟随携凭证跨站重定向。

## 已知基线事实（本 worktree 实测，勿按其他工作区推测）

- 本 worktree 由 `main @ f98e27c7538d091bfcdecfcb6ffc10360a35ba04` 创建，仅多一个计划 seed 提交；主工作区的未提交修改不随 worktree 复制。
- 基线命令结果见 `children/01/baseline.md`（控制方在 seed 提交上 fresh 运行；记录 exit code 与计数）。测试若在基线上已红，必须在报告中按基线对比归类，不得把预置红算成新引入失败；也不得为了变绿改旧期望。
- 计划明确：原测试存在需要本地 socket 的真实 XML 管道 fixture；若本机端口受限导致失败，须如实报告为环境限制，不得记为业务通过。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest,ExpertDiscoveryPropertiesTest,DiscoveryCheckpointCodecTest,OpenAlexRequestPolicyTest,DiscoveryPromotionProgressServiceTest,TaskProgressStoreTest,TaskProgressStoreRebindTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest,DiscoveryPipelineServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

`ExpertDiscoveryPropertiesTest` 为新增文件；seed 基线上它不存在属于预期。

## 交付

- 按计划 T-1→T-2→T-3 顺序实现；计划要求 TDD：先写失败断言（记录原代码失败证据），再实现到通过，不得靠修改旧期望变绿。
- 执行报告写入 `docs/plans/fast/2026-09-28-discovery-resilience-two-hour/children/01/execution.md`（报告本身不进实现提交）：写明每个子任务的文件:行证据、命令与 exit code/计数、与基线的对比、计划要求但未做到的显式偏差。
- 只返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不得修复白名单外问题、不得重构相邻代码、不得为本计划之外的目标（如 ARXIV `RAW_WRITE_INCOMPLETE`、TLS 根因）做改动。
