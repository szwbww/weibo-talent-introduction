# 03 · P-B：ES mapping 契约收敛

> 主计划：`docs/plans/2026-08-13/README.md`（计划集索引）
> 本计划：`docs/plans/2026-08-13/03-es-mapping-contract-convergence.md`（commit:37ebb355894783cbf4f380484359bf6218d62949）——**该文件即完整契约，必须全文阅读后执行**
> 依赖：none ｜ 解锁 05（P-E）｜ 子系统：1（后端 + 资源）｜ 7 文件

## 授权文件（Authorized Files）

| # | 文件 | 类型 | 任务 |
|---|---|---|---|
| 1 | `src/main/resources/es/orcid_info_application.json` | 改 | T-1 |
| 2 | `src/main/resources/es/orcid_info_candidate.json` | 改 | T-1 |
| 3 | `src/main/resources/es/orcid_info_raw.json` | 改 | T-1 |
| 4 | `src/main/kotlin/…/expert/service/ExpertIndexService.kt` | 改 | T-2, T-3 |
| 5 | `src/main/kotlin/…/expert/service/ExpertIndexWriterService.kt` | 改 | T-3 |
| 6 | `src/test/kotlin/…/expert/EsMappingContractTest.kt` | 新增 | T-4 |
| 7 | `docs/runbooks/es-mapping-reindex.md` | 新增 | T-5 |

> 知识条目 T-6（`docs/knowledge/es-index/K-es-dynamic-false.md` 就地修正 + 新增 `K-es-mapping-single-declaration-source.md`）随计划 Phase 6 处理，计入本计划交付但不在上表。`CandidateOperatorStatusSyncService.kt` 的调用点改动随 T-3 提交；若需独立修改该文件，文件数 8 仍 ≤10。

> **Amendments A3（HUMAN 批准 2026-08-13，扩权）**：授权文件扩至下列（仅实际受影响者才改，未受影响保持零改动）：
> - 测试（断言旧契约，随计划更新为新契约，不弱化验收标准）：`src/test/kotlin/…/expert/ExpertIndexServiceTest.kt`、`src/test/kotlin/…/expert/ExpertIndexWriterServiceTest.kt`、`src/test/kotlin/…/expert/CandidateOperatorStatusSyncServiceTest.kt`、`src/test/kotlin/…/campaign/service/ExpertOperatorStatusServiceTest.kt`、`src/test/kotlin/…/campaign/service/ManualInitialOutreachServiceTest.kt`、`src/test/kotlin/…/campaign/service/ManualOutreachTxHelperTest.kt`
> - T-3 改名调用方：`src/main/kotlin/…/mail/service/BounceCollectionService.kt`、`…/campaign/service/ManualInitialOutreachService.kt`、`…/campaign/service/ExpertOperatorStatusService.kt`（实际调用点以 grep 实测为准）

**禁止**：任何前端文件；其他后端/资源/测试文件；`docs/plans/fast/*`。

## 关键不变量（详见计划文件）

- **I-1** JSON 是 mapping 唯一声明源：删除 `phase5NewFields`（ExpertIndexService.kt:111-117）与 `loadMappingProperties` 过滤分支（:124-127）。
- **I-2** 单字段冲突不污染整批：`updateMappingIfNeeded`（:74-107）先整批 PUT，4xx 时降级逐字段 PUT，逐个记录成功/失败与原因；汇总日志 `index=X 推送 N 字段：成功 M，冲突 K（字段列表）`。
- **I-3** dynamic 按索引区分：APPLICATION `dynamic:false`，CANDIDATE/RAW 默认 true；修正 `K-es-dynamic-false.md`（附实测命令与输出），bump created。
- **I-4** 新增 mapping 不追溯存量：`_update_by_query` runbook（T-5）。
- T-1：application JSON 加 `"operatorStatus": {"type":"keyword"}`（与 candidate :38 逐字一致）；`enrichedAt` 由 `date` 改 `keyword`（迁就存量技术债，加注释）；CANDIDATE/RAW 的 `givenNames`/`familyNames`/`employment`/`keyword` 由 `text` 改 `keyword`（对齐线上）。
- T-3：`syncCandidateOperatorStatus`/`syncCandidateOperatorStatusBatch` 改三层（RAW/CANDIDATE/APPLICATION）按需 `_update`，照抄 `ExpertDiscoveryService.updateExpertAcademicFields:1098` 的"文档不存在则跳过"判定；方法名去掉 `Candidate` 前缀；`checkCandidateOperatorStatusMapping`（:143）扩展三层检查；更新 `CandidateOperatorStatusSyncService:16-19` 调用。

## 必跑命令（Required Commands，zulu-11，全部 fresh 运行）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=EsMappingContractTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。核心效果（mapping 落地）无法单测覆盖，以验收标准 + 启动日志为准。

## 基线（Baseline）

`mvn clean package` exit 0：surefire 2378 / 0 / 0 / 4 skipped；JS 496 pass。

## 下游接口（Downstream Interfaces）

- **05（P-E）**：依赖 APPLICATION 层 `operatorStatus` mapping 存在（JSON 声明 + PUT 推送）；`operatorStatus` 写入需覆盖三层（IP-3）；`checkOperatorStatusMapping` 三层化。
- 验收标准：`grep "phase5NewFields" src/main/kotlin` → 0 hits；runbook 存在且可复制执行。

## 执行契约

1. 先读 `skill://execute-p` 并严格遵循。
2. 只改上述授权文件（含 T-6 两个知识条目）；只提交授权文件；不得提交 `docs/plans/fast/*`。
3. 最终实现状态上 fresh 运行全部必跑命令。
4. 实现提交：`feat(fast-p): implement 03`。
5. 完整执行结果追加写入 `<worktree>/docs/plans/fast/batch-send-status-consistency/children/03/execution.md`。
6. 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。
7. 不做：review 后续计划、修复无关问题、push、merge、amend、squash、rewrite history。
