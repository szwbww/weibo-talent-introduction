# 计划 02 — 四档口径、classify 纯函数与 ES mapping 声明

> 依赖：**无**（计划 01 已作废，本计划为首个可执行计划）。后继：计划 03。
> 共享证据见主计划 `expert-reachability-00-execution-order.md`。
>
> **2026-08-16 口径修正**：已按主计划「修正记录」A-1~A-4 重写四档口径 ——
> 删除 `lastPublicationYear` 与 `emailVerifiedLevel` 判据，新增消费级邮箱域名判据，
> `UNKNOWN` 判据由 `enrichedAt` 缺失改为 `emailSource` 缺失。

## 需求描述

**Observable outcome**

1. 仓库中存在唯一的可达性判定实现 `ExpertReachabilityClassifier.classify()`，四档口径以可执行代码固化并有单测覆盖。
2. `orcid_info_candidate.json` 与 `orcid_info_application.json` 声明 `reachability: keyword`；应用启动后该 mapping 推送到既有索引。
3. `ExpertProfile` / `sourceFields()` / `ExpertIndexResponse` 具备读取与透传 `reachability` 的能力（本计划只做管道，值由计划 03 写入，故此时全量为 UNKNOWN）。

**What must NOT change**

- N-1 `orcid_info_raw.json` 不新增任何字段（主计划 I-4 / O-4）。
- N-2 `checkOperatorStatusMapping()` 与 `syncOperatorStatusBatch()` 一行不改（主计划 N-4）。
- N-3 `sourceFields()` 既有 30 个字段的顺序与内容不变，只追加。
- N-4 不改 `ExpertProfile` 既有字段的顺序与可空性。

**Out of scope**

- O-1 不写入任何 `reachability` 值（属计划 03）。
- O-2 不新增数值分 `reachabilityScore`（主计划 I-6：每个存储只加 1 个字段）。
- O-3 不做前端展示与筛选（属计划 04/05）。

## 关键不变量

### Invariant I-2-1: 判定实现唯一
- Rule: 可达性判定表达式只允许存在于 `ExpertReachabilityClassifier.classify()` 一处。禁止在 `ExpertDiscoveryService`、`ExpertSearchService`、前端或任何服务中重写等价表达式。
- Applies to: 计划 03 的 sync 服务、计划 05 的四处筛选落点（筛选按**已落库的值**过滤，不重新判定）。
- Violation consequence: 与 `K-discipline-unclassified-filter-bypasses` 记载的同类分裂——ES 侧口径与内存侧口径漂移，表现为「筛出来的和显示的不一致」且无报错。
- 来源: K-discipline-unclassified-filter-bypasses（该条目行号已过期，见主计划 R-6 的更正）

### Invariant I-2-2: classify 是纯函数
- Rule: `classify(profile: ExpertProfile, suppressedEmails: Set<String>, hardBouncedOrcids: Set<String>): ExpertReachability?` 不得注入 Repository / RestTemplate / 时钟。唯一允许的注入依赖是 `ProviderResolver`（其 `resolve()` 是纯字符串运算，无 IO —— 见主计划 R-45 的全文）。
- Applies to: `ExpertReachabilityClassifier`。
- Violation consequence: 一旦引入 IO 或时钟，单测无法固定用例，且全量扫描的每文档判定会退化为逐条查询。
- 来源: original

### Invariant I-2-3: 返回 null 表示 UNKNOWN
- Rule: `classify()` 返回 `null` 表示 UNKNOWN，**不返回** `ExpertReachability.UNKNOWN` 枚举值；枚举本身只含 `BLOCKED_UNSUBSCRIBED` / `BLOCKED_BOUNCED` / `HIGH` / `LOW` 四个成员。
- Applies to: `ExpertReachability` 枚举定义、`classify()` 返回类型、计划 03 的 `mapNotNull` 过滤。
- Violation consequence: 一旦枚举里存在 `UNKNOWN` 成员，迟早有人把它 `.name` 写进 ES，直接违反主计划 I-2（字段缺失 = UNKNOWN）。用类型系统堵死这条路。
- 来源: 主计划 I-2 的实现约束

### Invariant I-2-4: BLOCKED 短路优先于一切
- Rule: `classify()` 的第一段必须是 BLOCKED 判定；命中后立即返回，不再计算任何其他维度。两个 BLOCKED 子档的优先级：退订（`BLOCKED_UNSUBSCRIBED`）优先于硬退（`BLOCKED_BOUNCED`）。
- Applies to: `ExpertReachabilityClassifier.classify()`。
- Violation consequence: 违反主计划 I-1。同时命中两者时若返回硬退，运营会以为「换个邮箱就能继续发」，而实际该专家已明确退订——合规风险。
- 来源: 主计划 I-1；短路写法先例见 `OperatorStatusReconcileService.deriveExpertedStatus` 的 `EMAIL_INVALID` 无条件短路与 `K-operator-status-single-writer` 的 I-2

### Invariant I-2-5: 信息缺失返回 null，不返回 LOW
- Rule: 当 `profile.emailSource` 为 null 或空串、且无任何 BLOCKED 事实时，一律返回 `null`（UNKNOWN）。禁止把「没有 emailSource」当作 `LOW` 处理。
- Applies to: `ExpertReachabilityClassifier.classify()`。
- Violation consequence: 违反主计划 I-3。
- 来源: 主计划 I-3

### Invariant I-2-6: mapping 断言层级 = 写入层级
- Rule: `checkReachabilityMapping()` 只遍历 `listOf(ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)`，不含 RAW。
- Applies to: `ExpertIndexService.checkReachabilityMapping()`。
- Violation consequence: 见主计划 R-9 —— `checkOperatorStatusMapping()` 遍历三层而 `orcid_info_raw.json` 从未声明该字段，断言结构上无法通过。
- 来源: 主计划 I-4

## 四档口径（本计划的规范定义，计划 03/04/05 引用此表）

> 本表已按主计划「修正记录」A-1~A-4 修正。**判据只有两个**，且两者均在建档时写入、
> 不依赖 enrichment（主计划 R-46），故回填当天即全量覆盖。

| 档 | 枚举值 | ES 值 | 判定 | 优先级 |
|----|-------|-------|------|-------|
| 已退订 | `BLOCKED_UNSUBSCRIBED` | `"BLOCKED_UNSUBSCRIBED"` | `profile.email` 归一化后命中 `suppressedEmails` | 1（最高，短路） |
| 邮箱失效 | `BLOCKED_BOUNCED` | `"BLOCKED_BOUNCED"` | `profile.orcidId` 归一化后命中 `hardBouncedOrcids` | 2（短路） |
| 未知 | —（`null`） | 字段缺失 | `profile.emailSource.isNullOrBlank()` | 3 |
| 可达高 | `HIGH` | `"HIGH"` | `emailSource == "PAPER_FULLTEXT"` **且** 邮箱域名不属消费级 provider | 4 |
| 可达低 | `LOW` | `"LOW"` | 有 `emailSource`、非 BLOCKED，且不满足 HIGH（即 `emailSource == "ORCID_PUBLIC"` **或** 邮箱落在消费级 provider） | 5（兜底） |

**消费级 provider 判定**（遵主计划 A-3 的反向使用约束）：

```
CONSUMER_PROVIDERS = setOf("gmail", "outlook", "yahoo", "tencent", "netease")
isConsumerEmail(email) = providerResolver.resolve(email) in CONSUMER_PROVIDERS
```

`ProviderResolver.resolve()` 的其余返回值 `edu` 与 `other` 一律**不**视为消费级。
**禁止**反过来把 `other` 断言为机构域名 —— 该桶混有未被 `edu` 规则识别的机构域名
（`.ac.jp` / `.edu.cn` / 欧陆高校等）与真正的未知域名，正向断言会产生假阳性。

`CONSUMER_PROVIDERS` 定义为 `ExpertReachabilityClassifier` 的 companion 常量，**不进配置文件**
（避免运营调参绕过 I-1；BLOCKED 与它无关，调参不会放行 BLOCKED）。

> **已移除的判据（勿重新提出，见主计划修正记录）**：
> `lastPublicationYear`（A-1）、`emailVerifiedLevel`（A-2）。

## 现状审计

### CANDIDATE / APPLICATION mapping 现状

见主计划 R-9 的 grep 输出：两份 JSON 均 `"dynamic": false`（`:7`），
均已声明 `operatorStatus: keyword`（分别 `:38` / `:48`）。
本计划在两份 JSON 中各追加一行 `"reachability": { "type": "keyword" },`。

`dynamic: false` 意味着**不声明就永远查不到**：写进 `_source` 但不进倒排索引，
且无任何报错（该行为与 `enrichedAt` 在 APPLICATION 层的既有缺陷同源，见团队记录
`es-mapping-live-vs-repo-drift`）。

### mapping 推送链路

见主计划 R-10：`loadMappingProperties`（`ExpertIndexService:149-152`）已明确「es/*.json 是唯一声明源，
Kotlin 侧无字段白名单」。`bootstrapMappings()`（`:37`）每次启动对各层执行
`updateMappingIfNeeded` → `PUT /{index}/_mapping`；整批 4xx 时降级为
`pushFieldsIndividually`（`:117`）逐字段推送。故新增 keyword 字段不会因
`enrichedAt` 的既有类型冲突而被整批拖垮。

### `ExpertProfile` 读取管道

```bash
grep -n "enrichmentSource" src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt
```
```
src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt:30:    val enrichmentSource: String? = null
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:437:            enrichmentSource = source.nullableText("enrichmentSource")
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:458:            "enrichmentSource", "recentWorkTitles", "patentTitles", "enrichedAt"
```

新增字段须同步三处（domain 定义、`mapToProfile` 映射、`sourceFields()` 白名单）——
**漏掉 `sourceFields()` 是静默失效点**：ES 只返回白名单内的 `_source` 字段，
未列入者在 `mapToProfile` 中恒为 null，无任何报错。（`K-expert-profile-source-sync`）

### Interaction points

| # | 写入 | 读取 | 处置 |
|---|------|------|------|
| IP-1 | 计划 03 的 bulk update | `ExpertSearchService.mapToProfile:400-437` | 本计划先建管道，值恒 null |
| IP-2 | ES mapping 声明 | `ExpertSearchService` 各 filter | 计划 05 消费；本计划只保证字段可查 |
| IP-3 | `ExpertIndexResponse.from`（主计划 R-18） | 前端 `loadContacts` | 计划 04 消费 |

## 实现方案

### T1 — 枚举与 classifier（遵 I-2-1 ~ I-2-5）

新建 `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertReachability.kt`：
枚举 4 成员（无 UNKNOWN，遵 I-2-3）+ `esValue` 属性。

新建 `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilityClassifier.kt`：
`@Component`，构造注入 `ProviderResolver`（唯一依赖），
唯一公开方法 `classify(profile, suppressedEmails, hardBouncedOrcids): ExpertReachability?`。
邮箱归一化复用 `EmailSuppressionService.normalize()` 的同款语义（`trim().lowercase(Locale.ROOT)`），
ORCID 归一化复用 `ExpertIdNormalizer.normalize()`。

**跨模块依赖说明**：`ProviderResolver` 位于 `com.weibo.talentintroduction.mail.service`，
本类位于 `expert.service`。它是无状态 `@Service`、无 IO、无其他依赖，注入不构成环。

### T2 — mapping 声明（遵 I-2-6、N-1）

`es/orcid_info_candidate.json` 与 `es/orcid_info_application.json` 各加：
```json
      "reachability": { "type": "keyword" },
```
`es/orcid_info_raw.json` **不改**。

### T3 — mapping 前置断言（遵 I-2-6）

`ExpertIndexService` 新增 `checkReachabilityMapping(): Boolean`，结构照抄
`checkOperatorStatusMapping()`（`:170-200`），差异仅两点：字段名换为 `reachability`；
层级列表去掉 RAW。

### T4 — 读取管道（遵 N-3、N-4）

- `ExpertProfile.kt`：末尾追加 `val reachability: String? = null`
- `ExpertSearchService.mapToProfile`：追加 `reachability = source.nullableText("reachability")`
- `ExpertSearchService.sourceFields()`：末尾追加 `"reachability"`

### T5 — 单测

`ExpertReachabilityClassifierTest`，用例矩阵至少覆盖：

1. 退订命中且同时硬退 → `BLOCKED_UNSUBSCRIBED`（验 I-2-4 的子档优先级）
2. 仅硬退 → `BLOCKED_BOUNCED`
3. `emailSource` 为 null 且无 BLOCKED → `null`（验 I-2-5）
4. `emailSource` 为空串且无 BLOCKED → `null`（空串与 null 同权）
5. `emailSource` 为 null 但已退订 → `BLOCKED_UNSUBSCRIBED`（验短路优先于 UNKNOWN）
6. `PAPER_FULLTEXT` + `a@mit.edu` → `HIGH`
7. `PAPER_FULLTEXT` + `a@uni-heidelberg.de`（`resolve` 返回 `other`）→ `HIGH`
8. `PAPER_FULLTEXT` + `a@gmail.com` → `LOW`（消费级域名压过论文邮箱来源）
9. `PAPER_FULLTEXT` + `a@qq.com` / `a@163.com` / `a@outlook.com` / `a@yahoo.com` → 均 `LOW`
10. `ORCID_PUBLIC` + `a@mit.edu` → `LOW`
11. `ORCID_PUBLIC` + `a@gmail.com` → `LOW`
12. 大小写与前后空格不同的邮箱仍能命中退订集合（`  A@Mit.EDU  ` vs 集合中的 `a@mit.edu`）
13. `profile.email` 为 null 时不抛异常，按非消费级处理（`resolve(null)` 返回 `other`）

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `src/main/resources/es/orcid_info_candidate.json` | T2 |
| 2 | `src/main/resources/es/orcid_info_application.json` | T2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertReachability.kt` | 新增（T1） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilityClassifier.kt` | 新增（T1） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexService.kt` | T3 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt` | T4 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | T4 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilityClassifierTest.kt` | 新增（T5） |

文件数 8 ≤ 10。子系统 2（ES mapping 声明 / expert 服务）。新增 ES 字段 1。

## 验证命令

见主计划「验证命令」节。本计划专属：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertReachabilityClassifierTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest
```

## 验收标准

- I-2-1：`grep -rn "PAPER_FULLTEXT" --include=*.kt src/main/kotlin` 的结果中，除 `ExpertDiscoveryService`（写入 emailSource 处）外，只有 `ExpertReachabilityClassifier` 一个消费点；`grep -rn "CONSUMER_PROVIDERS" --include=*.kt src/main/kotlin` 定义 1 处、引用仅在该类内。
- I-2-2：`grep -n "Year.now()\|Repository\|RestTemplate" src/main/kotlin/.../ExpertReachabilityClassifier.kt` 零命中；构造参数只有 `ProviderResolver` 一个。
- I-2-3：`grep -n "UNKNOWN" src/main/kotlin/.../ExpertReachability.kt` 零命中；`classify` 返回类型为 `ExpertReachability?`。
- I-2-4：单测用例 1 断言返回 `BLOCKED_UNSUBSCRIBED`；用例 5 断言返回 BLOCKED 而非 null。
- I-2-5：单测用例 3、4 断言返回 `null`；`grep -n "enrichedAt" src/main/kotlin/.../ExpertReachabilityClassifier.kt` 零命中（口径已不使用该字段）。
- I-2-6：`grep -n "ExpertIndexLevel.RAW" src/main/kotlin/.../ExpertIndexService.kt` 在 `checkReachabilityMapping` 函数体范围内零命中。
- N-1：`git diff --stat src/main/resources/es/orcid_info_raw.json` 输出为空。
- N-3：`git diff src/main/resources/.../ExpertSearchService.kt` 中 `sourceFields()` 仅有新增行，无删除行。
- 回归：执行主计划「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: mapping 推送到既有索引
- 前置条件: 应用可重启，可访问 ES。
- 操作步骤: 1) 部署并重启应用。2) 查看启动日志中 `index=... 推送 N 字段` 汇总行。3) 执行 `GET /orcid_info_candidate/_mapping/field/reachability` 与 `GET /orcid_info_application/_mapping/field/reachability`。
- 预期结果: 两个请求均返回 `{"...":{"mappings":{"reachability":{"full_name":"reachability","mapping":{"reachability":{"type":"keyword"}}}}}}`；启动日志无 `字段映射冲突` 相关 WARN 涉及 `reachability`。
- 覆盖: Observable outcome 2 / I-2-6

### A-2: RAW 层未被改动
- 前置条件: 同 A-1。
- 操作步骤: 执行 `GET /orcid_info/_mapping/field/reachability`。
- 预期结果: 返回空对象 `{}`（该层无此字段声明），且此为**预期行为**，非缺陷。
- 覆盖: N-1 / 主计划 I-4

### A-3: 回归 —— 专家列表与详情未受影响
- 前置条件: 无。
- 操作步骤: 1) 打开专家列表，切换候选层/有效层。2) 打开任一专家详情。3) 使用「近 N 年」「H 指数」筛选各一次。
- 预期结果: 列表条数、字段展示、筛选结果与改动前一致；浏览器控制台无报错。
- 覆盖: N-2 / N-3 / N-4
