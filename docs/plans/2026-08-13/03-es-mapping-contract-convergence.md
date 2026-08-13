# P-B：ES mapping 契约收敛

优先级 **P1（地基）** ｜ 前置：无 ｜ 子系统：1（后端 + 资源） ｜ 文件数：7
**解锁 P-E**

## 需求描述

**Observable outcome**

1. `orcid_info_application.json` 声明 `operatorStatus`，重启后 APPLICATION 索引可按该字段过滤
   （解锁 P-E 在 APPLICATION 层的状态过滤）。
2. APPLICATION 层的 4 个 enrichment 字段（`enrichedAt` / `enrichmentSource` /
   `patentTitles` / `recentWorkTitles`）变为可查询。
3. 此后新增的任何 ES 字段只需改 JSON 即可到达索引，**不再需要同步修改 Kotlin 白名单**。
4. 存在一个测试，能在 JSON 与索引契约漂移时失败。

**What must NOT change**

- 现有可查询字段的类型与查询行为。
- 晋升路径的 `_source` 全量透传（`K-promotion-source-passthrough`）。
- CANDIDATE 层 `operatorStatus` 的现有行为（线上已是 keyword，工作正常）。

**Out of scope**

- 把 `enrichedAt` 从 `keyword` 改回 `date`（需 reindex，见「未决决策」）。
- CANDIDATE / RAW 的 `dynamic: true` → `false` 收敛（需 reindex + 全量字段对齐）。
- 线上那 10 个动态映射产生的野字段（`_class` / `beDeleted` / `countryZh` …）的清理。

## 根因（实测，此前诊断有误，已更正）

**误判**：曾以为"bootstrap 只在 404 建索引，改本地 JSON 对既有索引零作用"
（误信 `K-es-bootstrap-create-only-on-404`，未读代码）。

**实际**：`ExpertIndexService.bootstrapMappings()`（`:36-72`）每次启动都对三层执行
`updateMappingIfNeeded` → `PUT /{index}/_mapping`。真正的阻塞是
`loadMappingProperties`（`:118-141`）把 JSON 过滤成硬编码白名单：

```kotlin
// ExpertIndexService.kt:111-117 逐字
private val phase5NewFields = setOf(
    "hIndex", "citationCount", "lastPublicationYear",
    "researchFields", "disciplineCategory", "institution", "emailSource", "emailVerifiedLevel",
    "dataSource", "externalIds", "discoveredAt", "filterResult", "filterRejectReason",
    "updatedAt", "worksCount",
    "tags", "operatorStatus"
)
```

实测过滤结果：

| 索引 | JSON 声明 | 实际推送 | 被挡 |
|---|---|---|---|
| APPLICATION | 43 | 16 | **27** |
| CANDIDATE | 34 | 17 | 17 |
| RAW | 32 | 16 | 16 |

被挡字段含 `enrichedAt` / `enrichmentSource` / `patentTitles` / `recentWorkTitles`。

**结论：Phase 5 之后新增的任何 ES 字段都不会到达任何索引，且无任何报错。**
这与 P-A 的 operator_status 双写入口是同构问题：同一份契约存在两处
（JSON 声明 / Kotlin 白名单），无强制收敛。

## 关键不变量

### I-1：JSON 是 mapping 的唯一声明源
- **Rule**：索引应有哪些字段，只由 `src/main/resources/es/*.json` 决定。
  Kotlin 侧不得存在任何字段名白名单/黑名单。
- **Violation consequence**：即当前形态——JSON 改了没人知道没生效。
- **来源**：original

### I-2：单字段冲突不得污染整批推送
- **Rule**：`PUT _mapping` 批量失败时，必须降级为逐字段推送，使无冲突字段仍能落地；
  每个字段的成功/失败须分别记入日志。
- **依据**：ES 对 `PUT _mapping` 是**整批原子**的——任一字段与既有 mapping 类型冲突，
  整个请求 400，其余字段一并不生效。当前代码 `updateMappingIfNeeded:96-98` 捕获
  `HttpClientErrorException` 后只 `log.warn` 继续，即静默全批丢失。
- **Violation consequence**：移除白名单后，`enrichedAt`（JSON=date，线上 CANDIDATE=keyword）
  的冲突会让 CANDIDATE 的全部 34 个字段一个都推不上去——**比现状更糟**。
- **来源**：original（本计划新发现）

### I-3：dynamic 设置按索引区分，不得一概而论
- **Rule**：APPLICATION 是 `dynamic: false`，CANDIDATE / RAW 是 `dynamic: true`（ES 默认）。
  任何"三层一致"的假设都必须先实测。
- **实测证据**：

```
GET /orcid_info_candidate/_mapping?filter_path=**.dynamic   → {}
GET /orcid_info/_mapping?filter_path=**.dynamic             → {}
GET /orcid_info_application/_mapping?filter_path=**.dynamic → {"orcid_info_application":{"mappings":{"dynamic":"false"}}}
```

APPLICATION 有返回值证明查询语法有效 → 前两者的空结果表示键确实不存在 → ES 默认 true。

- **Violation consequence**：`K-es-dynamic-false` 正是基于错误假设写成的，
  导致后续计划把"补 JSON 声明"当成 CANDIDATE 的必要条件（实际不必要）
  和 APPLICATION 的充分条件（实际还需 reindex 存量）。
- **来源**：K-es-dynamic-false（**本次证伪，必须就地修正**）

### I-4：新增 mapping 不追溯存量文档
- **Rule**：给既有索引 `PUT _mapping` 新字段后，存量文档 `_source` 中该字段的值
  **不会**自动进入倒排索引，须 `POST /{index}/_update_by_query` 触发重建。
- **Applies to**：APPLICATION 的 `operatorStatus`（经晋升 `_source` 透传已有值）
  与 4 个 enrichment 字段。
- **Violation consequence**：只改 JSON 不 reindex，则"新写入的文档能查、老文档查不到"，
  比全查不到更难排查。
- **来源**：original

## 现状审计

### 三层索引：本地 JSON vs 线上（实测）

**APPLICATION**（`dynamic: false`，线上 39 字段 / 本地 43）
- 本地有线上无：`enrichedAt`、`enrichmentSource`、`patentTitles`、`recentWorkTitles`
- 线上多一个 `dynamic_templates` 块（本地 JSON 无），因 `dynamic:false` **不生效**
- **无 `operatorStatus`**（白名单里有，但 JSON 里没声明 → 从未被推送）
- 实测：`_source` 含 `enrichedAt` 2 次，`exists(enrichedAt)` = **0**。实锤未索引。

**CANDIDATE**（`dynamic: true`，线上 40 字段 / 本地 34）
- 六处类型不一致，**全部是 → keyword**（`strings: match_mapping_type=string→keyword`
  动态模板的产物）：

```
employment / familyNames / givenNames / keyword : 本地 text → 线上 keyword
enrichedAt                                      : 本地 date → 线上 keyword
candidateValidatedAt                            : format 不同
```

- 线上有 10 个本地未声明字段：`_class` `beDeleted` `candidateLevel` `candidateRuleVersion`
  `countryZh` `creditName` `employmentZh` `keywordZh` `otherName` `work`
  （`dynamic:false` 下不可能出现，是 dynamic:true 的铁证）
- 线上缺 4 个本地声明字段：`age` `id` `nationality` `orcid`（从无文档携带）
- `operatorStatus: keyword` ✅ 存在且类型正确——**纯属运气**，动态模板猜对了

**RAW**（`dynamic: true`）：形态同 CANDIDATE，`enrichedAt` 亦为 keyword。

### ES operatorStatus 写路径全集

```
grep -rn "syncCandidateOperatorStatus" src/main/kotlin
  BounceCollectionService.kt:105
  ManualInitialOutreachService.kt:708
  ExpertOperatorStatusService.kt:42, :62
  ManualOutreachTxHelper.kt:84                    ← P-A 会删除此处
  ExpertIndexWriterService.kt:66 (单条), :108 (批量)
  CandidateOperatorStatusSyncService.kt:22
```

底层两个方法**只写 CANDIDATE 一层**：
`ExpertIndexWriterService:67` 与 `:112` 均硬编码 `indexName(ExpertIndexLevel.CANDIDATE)`。

对比 `K-enrichment-write-three-layers` 记载的既有约定——
`ExpertDiscoveryService.updateExpertAcademicFields:1098` 对
`listOf(RAW, CANDIDATE, APPLICATION)` 逐层 `_update`。**operatorStatus 是唯一的单层例外。**

### 回刷按钮的前置校验

`CandidateOperatorStatusSyncService.reconcileAll():16-19` 先调
`expertIndexService.checkCandidateOperatorStatusMapping()`，该方法（`ExpertIndexService:143-171`）
只检查 **CANDIDATE** 的 `operatorStatus` 是否为 keyword。三层化后此校验需相应扩展。

### Interaction points

| # | 写 | 读 | 验收 |
|---|---|---|---|
| IP-1 | JSON 声明 operatorStatus | `bootstrapMappings` PUT | A-1 |
| IP-2 | `_update_by_query` 重建 | APPLICATION 层 `exists(operatorStatus)` 查询 | A-3 |
| IP-3 | 三层 syncOperatorStatus | `checkOperatorStatusMapping` 前置校验 | A-4 |
| IP-4 | 移除白名单 | 三层 PUT 的逐字段降级 | A-2 |

## 实现方案

### T-1 JSON 补声明【I-1】
文件：`src/main/resources/es/orcid_info_application.json`

新增 `"operatorStatus": { "type": "keyword" }`（与 candidate JSON `:38` 逐字一致）。

**同时把与线上冲突的字段类型改为线上实际值**，否则触发 I-2 的整批失败：
`enrichedAt` 由 `date(...)` 改为 `keyword`，并在 JSON 旁加注释说明这是"迁就存量、
待 reindex 后回正"的技术债（见「未决决策」）。CANDIDATE / RAW 的
`givenNames` / `familyNames` / `employment` / `keyword` 同理由 `text` 改 `keyword`。

### T-2 移除白名单 + 逐字段降级【I-1, I-2】
文件：`expert/service/ExpertIndexService.kt`

1. 删除 `phase5NewFields`（`:111-117`）与 `loadMappingProperties` 中的过滤分支（`:124-127`），
   改为返回 JSON 声明的全部 properties。
2. `updateMappingIfNeeded`（`:74-107`）改为：先整批 PUT；若返回 4xx，
   **降级为逐字段 PUT**，逐个记录成功/失败与失败原因（ES 会在 error.reason 里
   明确指出冲突字段与冲突类型）。汇总日志形如
   `index=X 推送 N 字段：成功 M，冲突 K（字段列表）`。

### T-3 operatorStatus 三层写入【I-4】
文件：`expert/service/ExpertIndexWriterService.kt`

`syncCandidateOperatorStatus`（`:66`）与 `syncCandidateOperatorStatusBatch`（`:108`）
改为对 `listOf(RAW, CANDIDATE, APPLICATION)` 按需 `_update`，
对齐 `ExpertDiscoveryService.updateExpertAcademicFields:1098` 的既有写法
（**照抄其"文档不存在则跳过"的判定，不要另写**）。方法名一并去掉 `Candidate` 前缀。

同步扩展 `ExpertIndexService.checkCandidateOperatorStatusMapping`（`:143`）为三层检查，
并更新 `CandidateOperatorStatusSyncService:16-19` 的调用。

### T-4 契约守卫测试【I-1】
新增：`src/test/kotlin/.../expert/EsMappingContractTest.kt`

断言：三个 JSON 均可解析；`ExpertIndexService` 源码中**不存在**字段名白名单常量
（正则扫 `setOf(` 且含字段名字符串）；`orcid_info_application.json` 含 `operatorStatus`。

> 机制先例：`QaRuleManagementServiceTest` 已按相对路径读 `src/main/resources/...`
> （`:1080` 等十余处），证明 `mvn test` 工作目录为工程根。

### T-5 存量重建 runbook【I-4】
新增：`docs/runbooks/es-mapping-reindex.md`

记录部署后必须执行的操作，含可复制命令：

```bash
# 1. 确认新 mapping 已落地
curl -s -u "$ES_USER:$ES_PASS" "$ES_BASE/orcid_info_application/_mapping/field/operatorStatus"

# 2. 触发存量文档重建倒排（无 script 的 no-op update_by_query 即可）
curl -s -XPOST -u "$ES_USER:$ES_PASS" \
  "$ES_BASE/orcid_info_application/_update_by_query?conflicts=proceed&wait_for_completion=false"

# 3. 轮询任务
curl -s -u "$ES_USER:$ES_PASS" "$ES_BASE/_tasks/<taskId>"

# 4. 验证可查询
curl -s -u "$ES_USER:$ES_PASS" "$ES_BASE/orcid_info_application/_count" \
  -H 'Content-Type: application/json' -d '{"query":{"exists":{"field":"operatorStatus"}}}'
```

### T-6 修正知识条目
- `docs/knowledge/es-index/K-es-dynamic-false.md` —— 就地修正：仅 APPLICATION 为
  `dynamic: false`，CANDIDATE/RAW 为 ES 默认 true（附本计划的实测命令与输出），
  并按 create-p Phase 6 规则 bump `created`（re-validated）。
- 新增 `docs/knowledge/es-index/K-es-mapping-single-declaration-source.md` ——
  记录白名单陷阱与 I-2 的整批原子性。

## 变更文件清单（7 个）

| # | 文件 | 类型 | 任务 |
|---|---|---|---|
| 1 | `src/main/resources/es/orcid_info_application.json` | 改 | T-1 |
| 2 | `src/main/resources/es/orcid_info_candidate.json` | 改 | T-1 |
| 3 | `src/main/resources/es/orcid_info_raw.json` | 改 | T-1 |
| 4 | `expert/service/ExpertIndexService.kt` | 改 | T-2, T-3 |
| 5 | `expert/service/ExpertIndexWriterService.kt` | 改 | T-3 |
| 6 | `test/…/expert/EsMappingContractTest.kt` | 新增 | T-4 |
| 7 | `docs/runbooks/es-mapping-reindex.md` | 新增 | T-5 |

> 知识条目（T-6）按 create-p Phase 6 处理，不计入变更清单。
> `CandidateOperatorStatusSyncService.kt` 的调用点改动随 T-3 一并提交——
> 若执行时发现需独立修改该文件，文件数变为 8，仍 ≤10。

> **Amendments A3（HUMAN 批准 2026-08-13，扩权）**：T-1/T-2/T-3 落地必然破坏断言旧契约的既有测试，
> 与 T-3 改名必然波及生产调用方，原清单未授权这些文件。经实证（ExpertIndexServiceTest:211 断言
> APPLICATION 不得含 operatorStatus；:25-73/:93-135 断言旧白名单行为；:224-253 与
> ExpertIndexWriterServiceTest:442-546 断言单层写入；4 个生产调用方按旧方法名编译），
> 授权文件扩至下列文件，随本计划一并更新为**新契约**（更新断言，不弱化计划验收标准）：
> - 测试：`ExpertIndexServiceTest.kt`、`ExpertIndexWriterServiceTest.kt`、
>   `CandidateOperatorStatusSyncServiceTest.kt`、`ExpertOperatorStatusServiceTest.kt`、
>   `ManualInitialOutreachServiceTest.kt`、`ManualOutreachTxHelperTest.kt`
> - T-3 改名调用方：`BounceCollectionService.kt`、`ManualInitialOutreachService.kt`、
>   `ExpertOperatorStatusService.kt`（实际调用点以 grep 实测为准，ManualOutreachTxHelper 经
>   P-A 收敛后可能已无 syncCandidateOperatorStatus 调用）
> 上述任一文件若实际不受影响则保持零改动。其余禁止项不变。

## 验证命令

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=EsMappingContractTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。
来源：CLAUDE.md「Commands」章节。

> **注意**：本计划的核心效果（mapping 真正落地）**无法由单测覆盖**，
> 必须依赖 A-1 ～ A-4 的人工验收 + 启动日志。

## 验收标准

- **I-1**：`grep -n "phase5NewFields" src/main/kotlin` → 0 hits；
  `EsMappingContractTest` 通过。
- **I-2**：人为在某个 JSON 里放一个与线上冲突的字段类型，启动后日志应显示
  "冲突 1（字段名）"而其余字段仍推送成功（A-2）。
- **I-3**：`K-es-dynamic-false.md` 已修正且含实测命令输出。
- **I-4**：runbook 存在且命令可复制执行。
- **回归**：执行『验证命令』节全部通过。

## 人工验收清单

### A-1：operatorStatus 到达 APPLICATION【outcome 1 / IP-1】
- 步骤：① 部署重启；② 看启动日志的 mapping 推送汇总行；
  ③ `curl "$ES_BASE/orcid_info_application/_mapping/field/operatorStatus"`。
- 预期：② 日志显示 APPLICATION 推送字段数 **> 16**（改动前恒为 16）；
  ③ 返回 `{"type":"keyword"}` 而非空对象。

### A-2：单字段冲突不污染整批【I-2】
- 前置：CANDIDATE 线上 `enrichedAt` 为 keyword。
- 步骤：临时把 `orcid_info_candidate.json` 的 `enrichedAt` 改回 `date`，重启，看日志；验证后回滚。
- 预期：日志出现"冲突 1（enrichedAt）"或等价信息，**且其余字段推送成功**；
  `age` / `id` / `nationality` / `orcid` 出现在 CANDIDATE 的 mapping 中
  （改动前它们永远不会出现）。

### A-3：存量文档可查询【outcome 1,2 / IP-2】
- 前置：完成 A-1，并按 runbook 执行 `_update_by_query`。
- 步骤：① 查 `exists(operatorStatus)` 的 count；② 查 `exists(enrichedAt)` 的 count。
- 预期：①② 均 **> 0**（改动前 `exists(enrichedAt)` 实测为 0）。

### A-4：三层同步生效【outcome 1 / IP-3】
- 前置：选一位已晋升到 APPLICATION 的专家。
- 步骤：① 在专家详情页手工把其运营状态改为「已回复」；
  ② 分别查 CANDIDATE 与 APPLICATION 索引中该 orcid 的 `operatorStatus`。
- 预期：两层**均为 `REPLIED`**（改动前 APPLICATION 层不会更新）。

### A-5：既有查询无回归【must-NOT-change】
- 步骤：① 专家漏斗页按「未联系」筛选；② 按标签、地区、邮箱服务商分别筛选并看聚合数字；
  ③ 「回刷 ES」按钮跑一次。
- 预期：结果与升级前一致；回刷 toast 无新增失败。

## 未决决策（需求方拍板）

**`enrichedAt` 的类型债**：线上三层均为 `keyword`（动态映射产物），
而 `ExpertDiscoveryService:795/806` 在其上做 `range` 查询——
字典序比较，因写入与 cutoff 同用 `ofPattern("yyyy-MM-dd HH:mm:ss")`（`:74`）
定宽零填充而**当前侥幸正确**。

- **方案甲（本计划采用）**：JSON 迁就线上，声明为 `keyword`，记录技术债。零成本，
  但类型语义仍错，且靠格式巧合维持正确性。
- **方案乙**：改回 `date` + 全量 reindex 三个索引（CANDIDATE 实测 114628 文档）。
  正确，但需停机窗口或双写切换，成本另议。

本计划默认走甲。若选乙，须拆为独立计划（涉及 reindex 流程与回滚预案）。
