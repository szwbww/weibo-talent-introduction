# Child 02 Execution Report — 专家分类模拟与线上回填任务

## Execution Result: PLAN_CONFLICT

- Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification/docs/plans/2026-08-24/02-expert-rnd-classification-backfill.md
- Plan SHA-256: 64f0738273930fbc000ad2f86f14a5d49082a65c49666dccd23a30490d10134e
- Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification/docs/plans/2026-08-24/02-expert-rnd-classification-backfill.md@64f0738273930fbc000ad2f86f14a5d49082a65c49666dccd23a30490d10134e
- Execution epoch: NEW
- Approval basis: current invocation (fast-p child brief 02 + committed plan)
- Executor: Imp02 (task subagent)
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification
- Target branch: fast/expert-rnd-classification
- Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification@fast/expert-rnd-classification@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-rnd-classification
- Pre-execution code SHA: 773527c7ed2ac65d4ae92d0233be82ab7417b1ef (child 01 terminal code head)
- Post-execution code SHA: 9d1d9f8 (feat(fast-p): implement 02)
- Evidence HEAD: N/A (evidence committed by controller)
- Implementation boundary: 773527c..9d1d9f8 (implementation commit only; docs/plans/fast excluded)

> 注：worktree_identity.py 在本机因 `git worktree list` 含一个不存在的挂载点
> （/sessions/... 来自其他会话）而崩溃；使用同一脚本逻辑的修补副本（跳过不存在的 worktree 条目）
> 完成身份校验，结果如上。

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 分类 bulk 唯一写入口 (I2-2/I2-4) | IMPLEMENTED | ExpertIndexWriterService.kt, ExpertIndexWriterServiceTest.kt | 新增 `bulkUpdateExpertClassifications`（单层 index、单批上限 1000、NDJSON meta `_index`+`_id`、data 逐字 `{"doc":{"expertClassification":{...}},"doc_as_upsert":false}`、无 ORCID→docId 二次查询、每项 updated/noop/failure、失败样本上限 100 但统计全部、整批异常记录 wholesaleError）、`checkExpertClassificationMapping`；测试逐字断言 NDJSON 结构与 classifiedAt 格式、索引不跨层、1000 分片、150 失败统计/100 样本、mapper 错误标记、404=失败 |
| T2 扫描/聚合/限速/取消 (I2-1~I2-4, I2-6) | IMPLEMENTED | ExpertClassificationBackfillService.kt (NEW), ExpertClassificationBackfillServiceTest.kt (NEW) | 固定请求模型；onlyPending filter（should/minimum_should_match=1，两分支 must_not exists / must_not term rnd-v1-2026）；searchAfterExpertsFiltered 批内分类；DRY_RUN 只聚合（writer 零调用断言）、EXECUTE 调 writer；可取消分段 delay（单次 sleep ≤1000ms）；mapping 缺失/首批全 mapper 错误立即 FAILED；result 实现 TaskExecutionSummaryProvider（success=writeSuccess/DRY_RUN 为 scanned，failure=writeFailure，CANCELLED/PARTIAL_SUCCESS/FAILED/SUCCESS） |
| T3 无前端管理 API (I2-3/I2-5) | IMPLEMENTED | ExpertClassificationAdminController.kt (NEW), ExpertClassificationAdminControllerTest.kt (NEW), DiscoveryExecutorConfig.kt | POST /api/expert-classification/backfill：校验→tryStartWithToken→单线程 `expertClassificationExecutor`→立即 202 {"message":"任务已启动","taskType":"EXPERT_CLASSIFICATION_BACKFILL"}；运行中 409；executor 拒绝 409 并清理 pending；不豁免 AuthInterceptor（401 测试）；token→executionId 绑定 + finally 按绑定 id 清理 |
| T4 任务目录登记 (I2-5/I2-6) | IMPLEMENTED | TaskTypeCatalog.kt, TaskTypeCatalogTest.kt (NEW) | code=EXPERT_CLASSIFICATION_BACKFILL、label=专家研发类型回填、group=MANUAL、metricLabel=已处理/失败、summaryRule=null、hasProgressUi=true、drilldown=null；测试断言 catalog 派生白名单包含该类型 |
| T5 线上执行手册 (I2-1~I2-6) | IMPLEMENTED | docs/runbooks/expert-classification-backfill.md (NEW) | 11 节 + 第 0 节变量/第 11 节完成记录；可复制命令；<ADMIN_USERNAME>/<ADMIN_PASSWORD> 占位；cookie jar 仅 /tmp 权限 600 且变量名避开 HOME/CODEX_HOME；无真实 secret；无自动回填指令（增量章节留待子计划 04） |
| 全量回归 gate（全局约束 4：mvn test 必须 BUILD SUCCESS） | CONFLICT | —（详见下） | mvn test：Tests run: 2810, Failures: 3, Errors: 0, Skipped: 4 → BUILD FAILURE；3 个失败全部为 **TaskExecutionSummaryExtractorTest**（不在授权清单内）对 TaskTypeCatalog 的「钉死值」库存守卫 |

## PLAN_CONFLICT 原因（唯一阻塞项）

计划 Task 4 要求登记新任务类型 `EXPERT_CLASSIFICATION_BACKFILL`（规范性：hasProgressUi=true、
metricLabel=已处理/失败），这必然改变 TaskTypeCatalog 的成员集合（17→18、hasProgressUi 白名单 6→7）。
`src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt` 中
三个测试把旧库存逐字锁死（执行 require 的 mvn test 全绿无法满足）：

1. `catalog hasProgressUi set equals the six-item whitelist`（:187-）— 断言 hasProgressUi 恰好 6 项（旧白名单）；计划要求新类型 hasProgressUi=true → 7 项，必然失败。
2. `catalog covers the sixteen audited task types`（:212-）— 断言 taskType 全集精确等于 17 项旧清单；新类型 → 18 项，必然失败。
3. `catalog metricLabel decisions are locked`（:226-）— 断言类型总数 17；→ 18，必然失败。

该文件不在子计划 02 的授权文件清单（10 个）内，execute-p 规定不得编辑未列文件；
计划「验收标准：回归 mvn test 全绿」与「变更文件清单共 10 个文件、不得修改其他文件」在此冲突。
按 execute-p「authoritative requirements conflict → stop, do not choose silently」，
以 PLAN_CONFLICT 终止，等待人工修正（对照子计划 01 的 A1 修正记录先例：授权钉值守卫测试同步更新）。

## Commands

| Command | Result | Evidence |
|---|---|---|
| JAVA_HOME=... mvn test -Dtest=ExpertIndexWriterServiceTest | PASS | Tests run: 29, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS |
| JAVA_HOME=... mvn test -Dtest=ExpertClassificationBackfillServiceTest | PASS | Tests run: 15, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS |
| JAVA_HOME=... mvn test -Dtest=ExpertClassificationAdminControllerTest | PASS | Tests run: 8, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS |
| JAVA_HOME=... mvn test -Dtest=TaskTypeCatalogTest | PASS | Tests run: 2, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS |
| JAVA_HOME=... mvn test（全量回归） | FAIL | Tests run: 2810, Failures: 3, Errors: 0, Skipped: 4 → BUILD FAILURE；3 个失败均在 TaskExecutionSummaryExtractorTest（未授权文件，见上） |
| git diff --check | PASS | exit 0，无空白错误 |
| M-4 检查：新代码无 `_update_by_query` / `_delete_by_query` / `_update/`，doc 仅含 expertClassification | PASS | grep 0 命中；writer 测试逐字断言 doc 只有 expertClassification 且无根级 updatedAt |

（所有命令均以 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home 运行。）

## Changed Files（实现提交 9d1d9f8，10 个授权文件）

- src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt — 分类 bulk 唯一写入口 + mapping 检查 + 结果类型（T1）
- src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt — 扫描/聚合/限速/取消/结果（T2, NEW）
- src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt — 异步管理 API（T3, NEW）
- src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt — 单线程 expertClassificationExecutor（T3）
- src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalog.kt — 新任务类型登记（T4）
- src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt — bulk NDJSON/分片/失败样本/mapping 测试（T1）
- src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillServiceTest.kt — DRY_RUN 零写/EXECUTE/取消/部分失败/过滤结构/抽样/限速/校验（T2, NEW）
- src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt — 401/202/409/executor 拒绝/token 绑定/校验 400（T3, NEW）
- src/test/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalogTest.kt — catalog 语义 + 白名单派生（T4, NEW）
- docs/runbooks/expert-classification-backfill.md — 11 节线上手册（T5, NEW）

## Deviations

- 无实现偏差：全部 10 个授权文件按计划完成；T1-T5 行为符合 I2-1..I2-6。
- 唯一冲突项为上述 3 个未授权钉值守卫测试，需要人工修正授权（未执行，避免越权改文件）。
- 工具注记：worktree_identity.py 原脚本在本机因陈旧 worktree 挂载点崩溃，用逻辑等价修补副本运行（见首部注）。

## Freshness

- Plan identity rechecked: YES（SHA-256 64f07382... 不变）
- Worktree identity rechecked: YES（commit 前后 --expect-root/branch/git-dir 校验通过）
- Reported commits reachable from target branch: YES（9d1d9f8 为 fast/expert-rnd-classification HEAD）
- Required commands run this invocation: YES（6 条全部本次运行）
- Historical evidence used only as baseline: YES

## Remaining Blocker

- 最小缺失授权：更新 `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt` 的
  三个库存守卫（类型全集 17→18 含 EXPERT_CLASSIFICATION_BACKFILL、hasProgressUi 白名单 6→7、
  metricLabel 锁定 17→18 并在锁表补充 已处理/失败）。该文件不在子计划 02 授权清单内。

## Next Action

- PLAN_CONFLICT → 人工决策/修订计划授权该守卫测试（对照子计划 01 A1 先例），随后恢复执行
  补一个修复提交（消息建议 `fix(fast-p): repair 02 round 1`），再跑全量回归。
