# Child 04 Execution Report — 删除 sendable / 版本门禁，研发类型成为唯一收口点

- Plan: `docs/plans/2026-08-28/04-single-gate-remove-sendable.md`
- Plan SHA-256: `e9ec275eff1ea779aaab1a416f81e6e52b90330e82ccc2aa872330a98f810c24`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/docs/plans/2026-08-28/04-single-gate-remove-sendable.md@e9ec275eff1ea779aaab1a416f81e6e52b90330e82ccc2aa872330a98f810c24`
- Execution epoch: NEW
- Executor: `Impl04RemoveGates`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate`
- Target branch: `fast/single-gate`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate@fast/single-gate@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-single-gate`
- Child base SHA: `bc8a93762cca39c2542d79d1f3801589b6e4e155`（child 03 实现提交）
- Pre-execution HEAD: `f6ba1ecaabc774a6f6d75b3848aa202a4cacc8cd`（03 light verification 后）
- Implementation commit: `742d1a27261d47c0aec00775a7da2f2dae92b7ee` — `feat(fast-p): implement child 04`（仅 8 个授权文件）

## Result: PLAN_CONFLICT

计划授权范围内的工作已**全部完成并提交**（8 个授权文件），但计划强制要求的改动
（I4-6 删除 `ACCEPTED_CLASSIFICATION_VERSIONS`、Task 1 删除 `expertSendableFilter`/`searchSendableExpertsWithEmail`）
**直接破坏了授权清单之外的两个测试文件**，导致 brief 要求的全部 mvn 命令在测试编译期失败。
按 brief 的硬性约定（「若计划强制改动破坏授权清单之外的测试，STOP 并返回 PLAN_CONFLICT，不得编辑未授权文件」），
本实现不修改这两个文件，需要人工修订（amendment）授权后再做修复。

## 变更文件（恰好 8 个授权文件，均已提交）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | 删 `expertSendableFilter()`（含 I3-2 注释块）；新增 `MATCH_NONE_FILTER` 常量（I4-2 fail-closed 表达）；删 `searchSendableExpertsWithEmail()` 整个方法 |
| 2 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | `buildEsFiltersForLevel` INTRODUCTION 尾段改为 `expertTypesFilter(...) ?: MATCH_NONE_FILTER`；发送前门禁改为 `scope.matchesExpertType(expert)`，跳过消息文案改为「研发类型不在本次选择范围内：${orcidId}」；移除不再使用的 `ExpertClassificationService` import |
| 3 | `src/main/kotlin/.../campaign/domain/BatchExecutionModels.kt` | 删 `matchesExpert` 开头硬门禁块；新增 `RecipientScope.matchesExpertType(profile)`（I4-4 唯一 Kotlin 实现，空集合返回 false）；原类型块替换为单行 `matchesExpertType` 调用；`EXPERT_NOT_SENDABLE` label 改为「研发类型不在本次选择范围内」（常量名与字符串值不变）；import 换为 `ExpertProfile`；两处过时「空集合 = 不限」注释同步为 I4-2 语义 |
| 4 | `src/main/kotlin/.../expert/service/ExpertClassificationService.kt` | **只**删 `ACCEPTED_CLASSIFICATION_VERSIONS` 及其文档注释；`VERSION` 逐字保留（I4-6 / M-3） |
| 5 | `src/test/kotlin/.../expert/service/ExpertSearchServiceTest.kt` | 删 `expertSendableFilter` 结构断言用例 + 2 个 `searchSendableExpertsWithEmail` 用例；新增 `MATCH_NONE_FILTER` 结构逐字断言（I4-2） |
| 6 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | 见「测试改动明细」 |
| 7 | `src/test/kotlin/.../expert/service/ExpertClassificationVersionGateGuardTest.kt` | 新增第二个守卫用例：sendable 读取白名单 = `ExpertClassification.kt` / `ExpertIndexWriterService.kt` / `ExpertClassificationBackfillService.kt` / `ExpertIndexController.kt` 四文件（I4-1 / M-1 机器判据）；类 KDoc 更新 |
| 8 | `src/test/kotlin/.../campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | 新增 I4-5 ES/内存同口径断言（对含 `expertClassification = null` 的 profile 组，`matchesExpertType` 与 `expertTypesFilter` 产出的 ES bool.should 结构逐 profile 一致，UNCLASSIFIED ↔ 类型字段不存在） |

## 测试改动明细（文件 6）

- 新增（Task 5 第 4~8 条）：
  - `INTRODUCTION adds only the expertTypes filter without sendable or version items (I4-1)`：filters 含 `expertTypesFilter` should 结构且位于末尾，JSON 不含 `expertClassification.sendable` / `.version`。
  - `empty expertTypes appends MATCH_NONE_FILTER on CANDIDATE (I4-2)`：空集合 → 末尾追加恒不命中项（原 I2-3「空 = 不限」测试重写）。
  - `MATERIAL_REMINDER with expertTypes adds no type filter`：改为断言不含类型 filter 且不含 `MATCH_NONE_FILTER`。
  - `OUT_OF_SCOPE experts follow only the explicit expertTypes decision (I4-1)`：`OUT_OF_SCOPE` 在 `["ACADEMIC_RND"]` 下被拒、在含 `"OUT_OF_SCOPE"` 时放行（A4-2 反向证明）。
  - `matchesExpert applies only the expertTypes decision for INTRODUCTION (I4-1)`：旧策略版本 + 类型命中 → true（版本门禁已删）。
  - `matchesExpert ignores classification version entirely (I4-1)`。
  - `matchesExpertType semantics per profile including UNCLASSIFIED and empty (I4-2 I4-5)`：UNCLASSIFIED ↔ null type；空集合对任意 profile 返回 false。
  - `buildEsFiltersForLevel appends the type decision to every INTRODUCTION level not MATERIAL_REMINDER`。
  - `round loop last gate no longer rejects stale policy version (I4-1)`：旧版本 profile 正常发送。
  - `runScheduledBatch legacy config without expertTypes sends nobody (I4-2)`：legacy 无类型配置 → sent=0、全部记 `EXPERT_NOT_SENDABLE`（M-2 回归）。
  - :4150 label 断言同步为「研发类型不在本次选择范围内」。
- 既有用例适配（I4-2 语义翻转的直接后果，均在授权文件内）：
  - `runScheduledBatch`/`runBulkOutreach` 的 legacy 入口（`BatchSendConfig.toSnapshot` 私有派生**不带** expertTypes）在 04 之后恒 fail-closed。23 个测试的 subject 是 run 流本身（轮次/容量/SMTP/预热），新增测试内 `runScheduledSnapshot()` 助手按 `toSnapshot` 相同派生规则构造等价快照并携带 fixture 类型（`PRODUCTION_RND/ACADEMIC_RND/HYBRID_RND`），改走现代 `service.run(...)` 入口；测试名 `runScheduledBatch X` → `run X`。
  - 4 个 `countPending` retryable 用例改经 `countBySnapshot(runScheduledSnapshot())` 验证（保留 discipline 过滤覆盖）。
  - 其余 `buildEsFiltersForLevel`/`countExperts` 预期（快照无 expertTypes 的 INTRODUCTION）从 `expertSendableFilter()` 改为 `MATCH_NONE_FILTER`。
  - 需要 `matchesExpert` 通过的类型判定用例补 `expertTypes`（fixture 分类 PRODUCTION_RND）。
  - `CONTACTED`/`mixed` 状态基座用例的 `must_not` 断言显式排除 `MATCH_NONE_FILTER`（其自带 bool.must_not）。

## 命令记录

### 必需命令（最终状态原样运行，全部被未授权文件编译错误阻断）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `JAVA_HOME=…zulu-11… mvn test -Dtest='ExpertSearchServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest,ExpertClassificationVersionGateGuardTest'` | 1 | BUILD FAILURE — `ExpertClassificationServiceTest.kt:(506,52) Unresolved reference: ACCEPTED_CLASSIFICATION_VERSIONS`（测试编译期，未运行任何用例） |
| `JAVA_HOME=…zulu-11… mvn test -Dtest=ExpertClassificationServiceTest` | 1 | BUILD FAILURE — 同上编译错误 |
| `JAVA_HOME=…zulu-11… mvn test` | 1 | BUILD FAILURE — 同上编译错误 |
| `git diff --check` | 0 | PASS，无输出 |

### 辅助验证（为隔离未授权文件破坏，临时把 `ExpertClassificationServiceTest.kt` 移出/移回，字节级还原，`git status` 确认零改动）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `mvn compile`（主代码） | 0 | PASS |
| `mvn test-compile`（仅移出该文件） | 0 | PASS — 全部测试源码（含 4 个授权测试类）编译通过 |
| `mvn test -Dtest='ExpertSearchServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest,ExpertClassificationVersionGateGuardTest'`（移出后） | 0 | 首轮 16 个失败全部修复后：**全部通过** |
| `mvn clean test`（移出后） | 1 | **Tests run: 2943, Failures: 1, Errors: 0, Skipped: 5** — 唯一失败 `OperatorStatusWriteSeamGuardTest`（见下方冲突 2）；4 个授权测试类：ManualInitialOutreachServiceTest 129/0、BatchSendTaskRuntimeIntegrationTest 22/0、ExpertSearchServiceTest 69/0、ExpertClassificationVersionGateGuardTest 2/0 |

## 机器判据（最终状态）

- `grep -rn "expertSendableFilter\|ACCEPTED_CLASSIFICATION_VERSIONS" src/main/kotlin` → **零输出** ✓
- `grep -rn "searchSendableExpertsWithEmail" src/main src/test` → **零输出** ✓
- I4-3：`expertTypesFilter` / `expertTypePredicate` 函数体逐字未改（diff 零命中）✓
- I4-6：`ExpertClassificationService.kt` diff 只有 `ACCEPTED_CLASSIFICATION_VERSIONS` + 注释删除，`VERSION` 行未动 ✓
- I4-5：`BatchSendTaskRuntimeIntegrationTest` 同口径用例通过 ✓

## 冲突明细（PLAN_CONFLICT 依据）

**冲突 1 — `ExpertClassificationServiceTest.kt`（未授权文件，编译失败）**
- 文件：`src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationServiceTest.kt`
- 用例：`:505` `ACCEPTED_CLASSIFICATION_VERSIONS contains VERSION without duplicates and size 1 (I5a2-10)`
- 原因：计划 I4-6 强制删除 `ExpertClassificationService.ACCEPTED_CLASSIFICATION_VERSIONS`（机器判据要求 src/main/kotlin 零命中），该用例 3 处引用常量 → 测试编译期 `Unresolved reference`。计划变更清单恰好 8 个文件，**未包含**此测试文件；brief 必需的 `mvn test -Dtest=ExpertClassificationServiceTest` 必然失败。
- 修复所需（需 amendment 授权）：删除该 I5a2-10 用例（常量已无消费者，与 I4-6 同向）。

**冲突 2 — `OperatorStatusWriteSeamGuardTest.kt`（未授权文件，行号钉死失效）**
- 文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
- 用例：`:139` `operator_status write sites exactly match whitelist`
- 原因：该守卫的 `EXCLUDED_NOISE_SITES` 钉死 `ExpertSearchService.kt:545`（context `operatorStatus = source.nullableText`）。Task 1 删除 `expertSendableFilter`（63 行）后该写入点移至 `:498` → 排除项失效，守卫自带「排除名单过期自检」（`assertTrue(staleExclusions.isEmpty())`）失败。计划未授权修改此文件（行号修正需要 amendment，与 01/05A/05A-2 时代同款 A 类授权）。
- 修复所需（需 amendment 授权）：`NoiseSite("…ExpertSearchService.kt", 545, "operatorStatus = source.nullableText")` 行号 545 → 498（并更新其上注释）。

## 偏差

1. **注释同步（授权文件内）**：`BatchExecutionModels.kt` 两处「空集合 = 不限」旧注释（`expertTypes` 属性文档、`fromSnapshot`）与 I4-2 fail-closed 语义冲突，已同步为 I4-2 表述；`runScheduledBatch`/`runBulkOutreach`/`countPending` 相关用例注释同步更新。属 M-2 语义翻转的直接清理，均在授权文件内。
2. **legacy 入口用例迁移**：`runScheduledBatch`（23 个用例）、`runBulkOutreach`（11 个调用点）、`countPending` retryable（4 个用例）经 `runScheduledSnapshot()` 助手改走 `run`/`countBySnapshot` 现代入口（计划未逐条枚举，但属 I4-2 翻转在授权测试文件内的必要适配；新增 1 个 legacy fail-closed 回归用例覆盖旧入口新语义）。
3. **I4-4 验收 grep 命中 2 处而非 1 处**：`grep -rn "UNCLASSIFIED\") typeName == null" src/main/kotlin` 命中 `BatchExecutionModels.kt:129`（本计划）与 `InitialOutreachService.kt:54`（子计划 02 的旧首发链路类型判定，计划现状审计第 4 条明确「本计划不再触及」）。不变量实质成立（批发送链路两处门禁共用 `matchesExpertType` 一份实现）；验收 grep 措辞未计入 02 的 InitialOutreachService，属计划验收文本过期，未改动该文件。
4. **worktree_identity.py 运行兼容**：脚本对 `git worktree list` 中本机不存在的远程会话路径 `resolve(strict=True)` 抛 FileNotFoundError（Python 3.13），用仅放宽枚举循环的补丁副本运行，`--expect-*` 校验全部通过。

## 新鲜度确认

- Plan identity rechecked: YES（sha256 不变 `e9ec275e…`）
- Worktree identity rechecked: YES（root/branch/git-dir 与提交时一致；HEAD = 提交 742d1a2）
- Reported commit reachable from target branch: YES（`742d1a2` 为 `fast/single-gate` 当前 HEAD）
- Required commands run this invocation: YES（按 brief 原样运行并记录失败；辅助验证另附）
- Historical evidence used only as baseline: YES

## Remaining Blocker（最小缺失授权）

1. 授权删除 `ExpertClassificationServiceTest.kt` 的 I5a2-10 用例（或将该文件加入变更清单）。
2. 授权将 `OperatorStatusWriteSeamGuardTest.kt` 的 `ExpertSearchService.kt` NoiseSite 行号 545 → 498 修正。

获得上述 amendment 后，8 个授权文件无需再动；修复仅触及这两个未授权文件，随后重跑 brief 全部必需命令即可转 READY_FOR_VERIFICATION。

## Next Action

PLAN_CONFLICT → 人工决定 / 修订计划（授权两个未授权文件的修复）。
