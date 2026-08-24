## Execution Result: PLAN_CONFLICT

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification/docs/plans/2026-08-24/01-expert-rnd-classification-core.md
Plan SHA-256: 0f7c8c5f2c4050c7a56a7f9fa4e236bb0a82d84bb617852093a12d768a0d760f
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification/docs/plans/2026-08-24/01-expert-rnd-classification-core.md@0f7c8c5f2c4050c7a56a7f9fa4e236bb0a82d84bb617852093a12d768a0d760f
Execution epoch: NEW
Approval basis: current invocation (fast-p child brief 01, controller-approved master plan 00)
Executor: Imp01
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification
Target branch: fast/expert-rnd-classification
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification@fast/expert-rnd-classification@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-rnd-classification
Pre-execution code SHA: c004a18d675b86040597f17f5911aa52f718d156 (child base; branch HEAD at start 3a4162c9 = plan seed docs commit)
Post-execution code SHA: a8cf1723df1403682a04babbf213f3c17a8ccc1b
Evidence HEAD: N/A (controller commits evidence separately; docs/plans/fast/ untouched by implementation commit)
Implementation boundary: c004a18d675b86040597f17f5911aa52f718d156..a8cf1723df1403682a04babbf213f3c17a8ccc1b (9 authorized files)

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 领域对象 (I1-1/I1-4/I1-5) | IMPLEMENTED | ExpertClassification.kt (NEW), ExpertProfile.kt | ExpertClassificationServiceTest 28/28 PASS（六类型 sendable 派生参数化、构造无 sendable 参数、JSON 仍输出 sendable、尾部可空字段默认参数） |
| T2 分类策略 (I1-1~I1-4) | IMPLEMENTED | ExpertClassificationService.kt (NEW), ExpertClassificationServiceTest.kt (NEW) | ExpertClassificationServiceTest 28/28 PASS（全部优先级、阈值 45/50、封顶 100、裸 doctor/MD/PhD 不误杀、中英文词边界、白名单、固定 Clock、指纹稳定/变化/64 位、证据 code 声明顺序、A1-1 五样本） |
| T3 ES 对象 (I1-5) | IMPLEMENTED | ExpertSearchService.kt, 3× orcid_info_*.json, ExpertSearchServiceTest.kt | ExpertSearchServiceTest 52/52 PASS（_source 含 expertClassification、round-trip 派生 sendable 忽略不可信 ES sendable、缺失/null/未知 type/残缺对象 fail closed、三种日期格式、三份 mapping 结构一致） |
| 全量回归 gate（全局约束 4：mvn test 必须 BUILD SUCCESS） | CONFLICT | — | mvn test：Tests run: 2776, Failures: 2, Errors: 0, Skipped: 4 → BUILD FAILURE（详见下） |

### Commands

| Command | Result | Evidence |
|---|---|---|
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertClassificationServiceTest | PASS | Tests run: 28, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest | PASS | Tests run: 52, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test | FAIL | Tests run: 2776, Failures: 2, Errors: 0, Skipped: 4；两项失败均为计划授权变更必然破坏的、不在授权清单内的"钉死值"守卫测试（见 Deviations） |
| git diff --check | PASS | exit 0, 无空白错误 |

### Changed Files

- src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertClassification.kt — 新增：ExpertType 六值枚举 + ExpertClassification 领域对象（sendable 只读派生 getter）(NEW)
- src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt — 尾部新增 `expertClassification: ExpertClassification? = null`
- src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationService.kt — v1 策略：归一化、临床/医学域/白名单词表、生产/科研计分、判定优先级、证据 code、SHA-256 指纹、注入 Clock (NEW)
- src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt — sourceFields() 增加 expertClassification；toExpertProfile() 显式解析子字段，未知 type 记录 warn 并整体置 null（fail closed）
- src/main/resources/es/orcid_info_raw.json — 新增唯一顶层对象 expertClassification（9 子字段，classifiedAt 日期格式与现有字段一致）
- src/main/resources/es/orcid_info_candidate.json — 同上（结构与 RAW 逐字节一致）
- src/main/resources/es/orcid_info_application.json — 同上（结构与 RAW 逐字节一致）
- src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationServiceTest.kt — I1-1~I1-4 + A1-1 全量规则测试 (NEW)
- src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt — I1-5 读取/round-trip/mapping 结构测试

### Deviations

无实现偏差：9 个授权文件全部按计划落地，T1/T2/T3 聚焦测试全绿。**但全量回归门被两个不在授权清单（brief「Authorized files (9; modify nothing else)」/计划「变更文件清单」）内的钉死值守卫测试阻断**，其失败是计划授权变更的必然结果，修复必须修改未授权文件：

1. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt:170`
   `assertEquals(32, singleFieldPuts, ...)`（注释「32 in orcid_info_raw.json」）钉死 RAW mapping 顶层字段数。
   计划 Task 3 / I1-5 强制三份 mapping 各新增一个顶层 `expertClassification` 对象 → RAW 字段数 32→33。
   **任何满足计划的实现都会使该断言变红**（实际 33）。修复需把 32→33（唯一机械修正），但该测试文件不在授权清单内。
2. `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`（EXCLUDED_NOISE_SITES）
   以 路径+行号+上下文 排除非写入噪声；`ExpertSearchService.kt:431`（上下文 `operatorStatus = source.nullableText`）是 431→445 的钉死行号。
   计划 Task 3 授权修改 ExpertSearchService.kt，必须新增 import（warn 日志需 LoggerFactory、领域类型、java.time 等）→ 行号整体下移 14 行 → 排除失效 → 守卫把只读的 `toExpertProfile` 构造误报为 operator_status 写入点。
   修复需把钉死行号 431→445（唯一机械修正）；守卫逻辑本身无需改动。本仓既有先例（batch-task-filters p3a brief M-5：「If it turns red from this plan's mapping lines, that is a HUMAN-authorized noise-site registration decision — return PLAN_CONFLICT instead of touching the guard」）明确规定此类守卫刷新必须人工授权，执行 agent 不得触碰守卫文件。

执行 agent 未修改上述两个未授权文件。工作树其余改动=0；实现提交 a8cf172 仅含 9 个授权文件。

### Freshness

- Plan identity rechecked: YES（0f7c8c5f2c4050c7a56a7f9fa4e236bb0a82d84bb617852093a12d768a0d760f，与执行前一致）
- Worktree identity rechecked: YES（root/branch/git-dir 与执行前一致；worktree_identity.py 因公共仓库存在失效的 /sessions/* 锁定 worktree 注册项而无法运行，已用其完全相同逻辑手工计算并记录，见下）
- Reported commits reachable from target branch: YES（a8cf172 为 fast/expert-rnd-classification 的 HEAD，父提交为计划 seed 3a4162c9）
- Required commands run this invocation: YES（三项测试命令 + git diff --check 均本调用内新鲜执行）
- Historical evidence used only as baseline: YES（基线绿色来自 ledger；本调用失败复现均现场运行确认）

### Remaining Blocker

最小缺失授权：人工（或修订计划/子计划 brief）授权对以下两个**未授权测试文件**做机械性钉死值刷新（无逻辑变更）：
- `ExpertIndexServiceTest.kt:170`：32 → 33（含注释文案），以反映计划强制新增的第 33 个顶层 mapping 字段；
- `OperatorStatusWriteSeamGuardTest.kt` EXCLUDED_NOISE_SITES：`ExpertSearchService.kt` 钉死行号 431 → 445（M-5 先例：守卫刷新属人工授权决策）。

或由 controller 修订 brief 将这两个文件加入授权清单后再执行修复轮。

### Next Action

- PLAN_CONFLICT → 获取人工决策（授权上述钉死值刷新）或修订计划/授权清单；批准后执行机械修复并重跑 `mvn test`，即可翻绿进入 READY_FOR_VERIFICATION。实现主体（T1/T2/T3）已完成并提交于 a8cf172，无需重做。
