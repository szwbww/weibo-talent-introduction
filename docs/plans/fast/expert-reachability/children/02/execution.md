# Child 02 执行报告 — 四档口径、classify 纯函数与 ES mapping 声明

- Child: 02
- Plan: docs/plans/2026-08-16/expert-reachability-02-classifier-and-mapping.md (commit 1c7cf0e4c11c53d1f4d20f28964fce837f70442b)
- Base SHA: edda3e4e67e8b4511f3c7ca76b09926c56e4f69a
- 执行日期: 2026-08-16
- 结果: READY_FOR_VERIFICATION

## 变更总览（8 个授权文件，无越界）

| # | 文件 | 任务 | 状态 |
|---|------|------|------|
| 1 | `src/main/resources/es/orcid_info_candidate.json` | T2 | 新增 `"reachability": { "type": "keyword" }`（properties 末尾） |
| 2 | `src/main/resources/es/orcid_info_application.json` | T2 | 同上 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertReachability.kt` | T1 | 新增枚举，恰 4 成员（BLOCKED_UNSUBSCRIBED / BLOCKED_BOUNCED / HIGH / LOW），`esValue` 属性，无未知档成员 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilityClassifier.kt` | T1 | 新增 `@Component`，唯一构造依赖 `ProviderResolver`，`classify(profile, suppressedEmails, hardBouncedOrcids): ExpertReachability?` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexService.kt` | T3 | 新增 `checkReachabilityMapping(): Boolean`，仅遍历 CANDIDATE + APPLICATION |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt` | T4 | 末尾追加 `val reachability: String? = null`（既有字段顺序不变） |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | T4 | `toExpertProfile` 追加 `reachability = source.nullableText("reachability")`；`sourceFields()` 末尾追加 `"reachability"`（30 → 31 字段，仅追加） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilityClassifierTest.kt` | T5 | 新增 13 个用例 |

未改动：`orcid_info_raw.json`（N-1）、`checkOperatorStatusMapping()` / `syncOperatorStatusBatch()`（N-2）、`ExpertIndexResponse`（本计划管道不含，计划 04 处理）。

## 各任务实现要点

### T1 — 枚举与 classifier

- `ExpertReachability`：4 成员，`esValue` 为 ES 侧字符串（与 `name` 一致）；无未知档成员（I-2-3），未知档由 `classify()` 返回 null 表达。
- `classify()` 判定顺序（严格按四档口径表）：
  1. BLOCKED 第一段短路（I-2-4）：`profile.email` 非空且归一化（`trim().lowercase(Locale.ROOT)`，复用 `EmailSuppressionService.normalize()` 语义）后命中 `suppressedEmails` → `BLOCKED_UNSUBSCRIBED`（优先于硬退）；
  2. `ExpertIdNormalizer.normalize(profile.orcidId)` 命中 `hardBouncedOrcids` → `BLOCKED_BOUNCED`；
  3. `profile.emailSource.isNullOrBlank()` → `null`（I-2-5，空串与 null 同权）；
  4. `emailSource == "PAPER_FULLTEXT"` 且 `providerResolver.resolve(email) !in CONSUMER_PROVIDERS` → `HIGH`；否则 `LOW`。
- `CONSUMER_PROVIDERS = setOf("gmail", "outlook", "yahoo", "tencent", "netease")` 为 companion 常量，不进配置文件；`other`/`edu` 均不视为消费级（A-3 反向使用约束）。
- 无 `Year.now()` / Repository / RestTemplate / 时钟注入（I-2-2）；无 `enrichedAt` 引用（I-2-5 口径已改用 emailSource）。

### T2 — mapping 声明

两份 JSON（candidate / application）的 `properties` 末尾各追加：

```json
"reachability": { "type": "keyword" }
```

`orcid_info_raw.json` 未改动（N-1）。字段为全新字段，计划 03 的 bulk update 首次写入即索引，无回溯问题（R-10）。

### T3 — mapping 前置断言

`checkReachabilityMapping()` 照抄 `checkOperatorStatusMapping()` 结构，两处差异：
- 字段名 `operatorStatus` → `reachability`；
- 层级列表 `listOf(ExpertIndexLevel.RAW, CANDIDATE, APPLICATION)` → `listOf(CANDIDATE, APPLICATION)`（I-2-6 / I-4：断言层级 = 写入层级，RAW 结构上不可能声明该字段）。

### T4 — 读取管道

三处同步（domain 定义 / `toExpertProfile` 映射 / `sourceFields()` 白名单），避免 `K-expert-profile-source-sync` 记载的静默失效点。本计划值恒为 null（管道期，计划 03 负责写入）。

### T5 — 单测（13 用例，覆盖计划矩阵 1-13）

| # | 用例 | 断言 |
|---|------|------|
| 1 | 退订 + 硬退同时命中 | `BLOCKED_UNSUBSCRIBED`（子档优先级） |
| 2 | 仅硬退 | `BLOCKED_BOUNCED` |
| 3 | emailSource = null，无 BLOCKED | `null`（I-2-5） |
| 4 | emailSource = ""，无 BLOCKED | `null`（空串与 null 同权） |
| 5 | emailSource = null 但已退订 | `BLOCKED_UNSUBSCRIBED`（短路优先于未知档） |
| 6 | PAPER_FULLTEXT + a@mit.edu | `HIGH` |
| 7 | PAPER_FULLTEXT + a@uni-heidelberg.de（resolve → other） | `HIGH` |
| 8 | PAPER_FULLTEXT + a@gmail.com | `LOW` |
| 9 | PAPER_FULLTEXT + qq/163/outlook/yahoo | 均 `LOW` |
| 10 | ORCID_PUBLIC + a@mit.edu | `LOW` |
| 11 | ORCID_PUBLIC + a@gmail.com | `LOW` |
| 12 | `  A@Mit.EDU  ` vs 集合 `a@mit.edu` | `BLOCKED_UNSUBSCRIBED`（大小写/空白归一化） |
| 13 | email = null 不抛异常，按非消费级 | `HIGH` |

## 验证命令（全部执行，JAVA_HOME=zulu-11）

| 命令 | 结果 | 退出码 |
|------|------|--------|
| `mvn test -Dtest=ExpertReachabilityClassifierTest` | Tests run: 13, Failures: 0, Errors: 0 | 0 |
| `mvn test -Dtest=ExpertIndexServiceTest` | Tests run: 8, Failures: 0, Errors: 0 | 0 |
| `mvn test -Dtest=ExpertSearchServiceTest` | Tests run: 43, Failures: 0, Errors: 0 | 0 |
| `mvn test`（全量回归） | **Tests run: 2469, Failures: 0, Errors: 0, Skipped: 4**，BUILD SUCCESS | 0 |
| `git diff --check` | 无输出（clean） | 0 |

基线 2456 + 新增 13 = 2469，与全量回归一致。node 套件 584/584 随 mvn test 一并通过。

## 验收标准核对

- I-2-1：`grep -rln "PAPER_FULLTEXT" --include=*.kt src/main/kotlin` → 仅 `ExpertDiscoveryService.kt`（写入点）与 `ExpertReachabilityClassifier.kt`（消费点）；`CONSUMER_PROVIDERS` 定义 1 处、引用仅在该类内。
- I-2-2：`grep -nE "Year\.now\(\)|Repository|RestTemplate" ExpertReachabilityClassifier.kt` → 0 命中；构造参数仅 `ProviderResolver`。
- I-2-3：`grep -n "UNKNOWN" ExpertReachability.kt` → 0 命中；`classify` 返回类型 `ExpertReachability?`。
- I-2-4：用例 1 断言 `BLOCKED_UNSUBSCRIBED`；用例 5 断言 BLOCKED 而非 null。
- I-2-5：用例 3、4 断言 `null`；classifier 内无 `enrichedAt` 引用。
- I-2-6：`checkReachabilityMapping` 函数体内无 `ExpertIndexLevel.RAW`（sed 提取函数体 grep 0 命中）。
- N-1：`git diff --stat src/main/resources/es/orcid_info_raw.json` 输出为空。
- N-3：`sourceFields()` diff 仅新增行（1 行改逗号 + 1 行新增 `"reachability"`），无删除行。
- 回归：全量测试通过（见上表）。

## 偏差与说明

1. **注释措辞为满足字面 grep 验收而调整**：`ExpertReachability.kt` 与 `ExpertReachabilityClassifier.kt` 的 KDoc 避免出现 `UNKNOWN`、`Repository`、`RestTemplate` 字面量（I-2-2 / I-2-3 验收为字面 grep 零命中，注释命中同样算违规）。功能代码不受影响。
2. **`ExpertSearchService.mapToProfile` 实际方法名为 `toExpertProfile`**：计划文本称 `mapToProfile`，代码中为 `private fun toExpertProfile(hit: JsonNode)`（约 :400），追加行落在该函数。
3. **`reachability` 声明置于两份 JSON `properties` 末尾**（`enrichmentSource` 之后），无尾随逗号（JSON 合法）；计划中 `"reachability": { "type": "keyword" },` 的尾随逗号为插入位置示意。
4. `ExpertIndexResponse` 未改动（授权文件清单不含 `ExpertIndexController.kt`；响应透传属计划 04）。
5. 文档类文件（本执行报告、docs/plans/fast/）不进入实现提交；由控制器按 fast-p 证据流程单独提交。

## 提交

- 提交信息: `feat(fast-p): implement 02`
- 内容: 上述 8 个授权文件；`docs/plans/fast/` 已排除。
