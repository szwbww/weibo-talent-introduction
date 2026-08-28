# 子计划 05：删除 sendable 概念

> 主计划：[`00-single-gate-master.md`](./00-single-gate-master.md)
> **依赖（硬）**：子计划 04 必须先上线 —— `sendable` 还有过滤用途时不能删。
> 04 上线后它只剩三处非过滤用途（序列化、统计、API DTO），本计划把这三处一并清掉。

---

## 需求描述

**Observable outcome**

1. 代码中不再存在 `sendable` 这个概念：删除 `ExpertClassification.sendable` 派生属性
   与 `SENDABLE_TYPES` 常量。
2. 分类回填的进度与结果不再报「可发信 N / 不可发信 M」——
   改为按**研发类型**的分项计数，与运营在页面上看到的口径一致。
3. 专家列表 API 不再返回 `expertSendable` 字段。

**What must NOT change**

1. `ExpertClassificationService.classify()` 的判定链、打分、阈值、`VERSION` —— 一行不改（主计划 M-3）。
2. `ExpertType` 六个枚举值的名称与顺序不变。
3. 回填的**选取口径**（`pendingOnlyFilter` 按版本选人）与写入行为不变，
   本计划只改它的**统计口径**。
4. 前端行为：`grep -n "sendable" src/main/resources/static/app.js` 当前**零命中**
   （前端从未消费该字段），因此删除 DTO 字段对页面零影响。

**Out of scope**

- 删除三份 ES mapping 里的 `sendable: {"type":"boolean"}` 声明，以及清理存量文档里已写入的值。
  停止写入后它成为孤儿字段，与 `batch_send_task_config.reachability_filter`（V103）同款处理：
  **保留、不写迁移、记录在案**。理由：`dynamic:false` 下删声明不会删存量值，
  收益为零而回滚成本非零。
- 任何过滤/门禁逻辑（已在 04 完成）。

---

## 关键不变量

### Invariant I5-1: 统计口径改为按类型分项，不得留二元口径
- Rule: `BackfillCounters` 的 `sendable` / `notSendable` 两个计数器删除，
  替换为 `byType: MutableMap<String, Long>`（key 为 `ExpertType.name`）。
  进度消息与结果对象都改为输出分项计数。
- Applies to: `ExpertClassificationBackfillService.kt`（`:44` 结果字段、`:254` 进度消息、
  `:261` 计数器、`:274` 累加、`:291` statsMap、`:313` toResult）。
- Violation consequence: 保留"可发信/不可发信"的说法等于保留一个已经不存在的判据 ——
  04 之后"可发信"取决于运营勾了什么，服务端无从得知。继续这么报是误导。
- 来源: original

### Invariant I5-2: 类型计数从枚举派生，不得手写名单
- Rule: `byType` 的键集合从 `ExpertType.values()` 派生；
  未出现的类型可以不出现在 map 里，但**不得**在代码中枚举字符串字面量。
- Applies to: `ExpertClassificationBackfillService.kt`。
- 来源: K-（沿用 2026-08-25 子计划 01 的 M-2「禁止手写六值名单」）

### Invariant I5-3: 停止写入，但不改 mapping、不清存量
- Rule: 删除 `ExpertIndexWriterService.kt:355` 的 `put("sendable", ...)`；
  **不得**新增迁移/脚本去删除三份 mapping 的声明或清理存量文档中的该字段。
- Applies to: `ExpertIndexWriterService.kt`。
- Violation consequence: `dynamic:false` 下删 mapping 声明不会删存量值，且会让
  `ExpertIndexServiceTest` 的逐字段降级计数（当前 34）连带变动，
  平白扩大改动面（[[K-es-dynamic-false]]、[[K-es-mapping-single-declaration-source]]）。
- 来源: original

### Invariant I5-4: 反序列化不受影响
- Rule: `ExpertSearchService` 解析 `expertClassification` 时**从来不读** `sendable`
  （`:559` 注释已声明「`sendable` 不读自 ES：领域 getter 恒由 type 派生」），
  本计划不得因删属性而改动该解析函数。
- Applies to: `ExpertSearchService.kt`（**本计划不改此文件**）。
- Violation consequence: 若误以为要改解析，会动到 `toExpertProfile`，
  影响面扩散到所有读路径。
- 来源: original（2026-08-28 grep 复核：`toExpertProfile` 无 `sendable`）

### Invariant I5-5: 改动面若超出清单即停止并拆分
- Rule: 执行前必须先跑
  `grep -rln "sendable" src/main/kotlin src/test/kotlin`，
  若命中文件多于本计划「变更文件清单」所列（扣除下述固定排除项），**停止执行并回到 create-p 重新拆分**，
  不得就地扩大范围。
  **固定排除项（2026-08-28 实测，A3 修订、A4 再修订）**：`ManualInitialOutreachService.kt` /
  `InitialOutreachServiceTest.kt`（发件账号语义与 `sendableClassification()` 构造 helper，不读
  `sendable` 属性）、`V109ExpertTypesMigrationTest.kt`（用例名与 I3-3 注释引用，文本断言不受影响）、
  `BatchSendTaskRuntimeIntegrationTest.kt`（:777 既有注释）、`ExpertClassificationService.kt`
  （M-3 零改动规约下的 KDoc）、`ExpertSearchService.kt`（I5-4 零改动规约下的 KDoc）。
  **A4 修正**：`ManualInitialOutreachServiceTest.kt` **不再属于排除项** —— 其
  `classification(type)` fixture helper（:4304/:4305/:4307）有 3 处真实代码引用
  `ExpertClassification.SENDABLE_TYPES`（大写常量名，小写 grep 漏检），Task 1 删除常量即编译失败；
  已授权为变更文件清单第 12 项做机械修复。其余排除项命中均为注释/helper 名，
  不读 `expertClassification.sendable` 属性、不触发守卫正则、不破坏编译。
- Applies to: 本计划全部任务。
- Violation consequence: 本计划文件数已贴近上限（11），任何扩张都会让验证进入多轮返工。
- 来源: create-p 的硬上限规则；A3 修订（基线审计未覆盖上述 6 个文件的注释/helper 命中）

---

## 现状审计

### `sendable` 的全部引用（2026-08-28 `grep -rn "sendable" src/main`）

> 说明：`ManualInitialOutreachService` 中另有一批 `sendable` 变量
> （`:182/225-243/565-587/1248-1261`），那是**可发信的发件账号列表**，
> 与专家分类无关，**本计划不得触碰**。

| 用途 | 位置 | 04 之后是否还在 | 本计划处置 |
|---|---|---|---|
| 派生属性定义 | `ExpertClassification.kt:36-37`（`get() = type in SENDABLE_TYPES`）、`:41-45`（`SENDABLE_TYPES`） | 在 | **删除** |
| 序列化写入 | `ExpertIndexWriterService.kt:355` `put("sendable", classification.sendable)` | 在 | **删除该行** |
| 回填统计 | `ExpertClassificationBackfillService.kt:44/254/261/274/291/313` | 在 | **改为按类型分项** |
| API DTO | `ExpertIndexController.kt:404` 字段声明 + `:454` 赋值 | 在 | **删除** |
| ES 谓词 | `ExpertSearchService.kt:51/59` | **04 已删** | — |
| 内存门禁 | `BatchExecutionModels.kt:71`、`ManualInitialOutreachService.kt:609`、`InitialOutreachService.kt:44` | **04 / 02 已删** | — |
| 解析读取 | 无（`:559` 注释明确不读） | — | — |
| mapping 声明 | 三份 `es/orcid_info_*.json` 的 `sendable: {"type":"boolean"}` | 在 | **保留为孤儿字段**（I5-3） |

### 回填统计的当前形态

- `ExpertClassificationBackfillResult`（`:40-56`）含 `val sendable: Long`（`:44`）与 `notSendable`。
- `BackfillCounters`（`:261` 起）：`var sendable: Long = 0`；`record()` 在 `:274`
  按 `if (classification.sendable) sendable++ else notSendable++` 累加。
- 进度消息 `:252-256`：`"已扫描 ${counters.scanned}，可发信 ${counters.sendable}，不可发信 ${counters.notSendable}"`。
- `statsMap()` `:286-292` 输出 `"sendable" to sendable`；`toResult()` `:301-313` 同。

### 测试面

`grep -rln "sendable" src/test/kotlin` 命中 10 个文件，其中：

- **04 之后不再引用**：`InitialOutreachServiceTest`（02 改）、`ManualInitialOutreachServiceTest`、
  `BatchSendTaskRuntimeIntegrationTest`（均在 02/04 中处理；残留命中为发件账号语义与注释，
  见 I5-5 固定排除项）。`ExpertSearchServiceTest` 例外：两个 I1-5 派生用例（~:1874/:1929）仍读
  `c.sendable` 属性，由 **A3 授权删除**（新增文件清单第 11 项）。
- **本计划需要改**：`ExpertClassificationServiceTest`（断言 `classification.sendable`）、
  `ExpertIndexWriterServiceTest`（断言写入的 `sendable` 键）、
  `ExpertClassificationBackfillServiceTest`（断言统计字段）、
  `ExpertClassificationAdminControllerTest`（结果 JSON 含 `sendable`）、
  `ExpertClassificationSchedulerTest`（同上）。
- `src/test/js/gateTemplateFilter.test.js` 的 `sendable` 属于发件账号语义，**不改**
  （执行前须用 `grep -n "sendable" src/test/js/gateTemplateFilter.test.js` 复核确认）。

### Interaction points

1. `ExpertIndexWriterService`（停止写入）× 存量文档（已有该字段）
   —— 新写入的文档缺该键、老文档有，两者并存。因为无人读取，无影响；
   但必须在验收中确认确实无人读（I5-4）。
2. `ExpertClassificationBackfillService`（统计口径变化）× 管理接口/调度器的返回结构
   —— 三个测试类都断言该结构，必须同批修改。

---

## 实现方案

### Task 1：删除派生属性与常量（I5-1 前置）

`ExpertClassification.kt`：删除 `:36-37` 的 `sendable` 属性、`:41-45` 的 `SENDABLE_TYPES`，
以及 `:6`、`:20-22` 中描述该属性的文档注释段落。`ExpertType` 枚举与其余字段逐字不动。

### Task 2：停止序列化写入（I5-3）

`ExpertIndexWriterService.kt`：删除 `:355` 的 `put("sendable", classification.sendable)` 一行。
`classificationNode` 的其余键（`type`/`productionScore`/`researchScore`/
`positiveEvidence`/`negativeEvidence`/`version`/`sourceFingerprint`/`classifiedAt`）逐字不动。

### Task 3：回填统计改为按类型分项（I5-1、I5-2）

`ExpertClassificationBackfillService.kt`：

1. `BackfillCounters`：删 `sendable` / `notSendable`，新增
   `val byType: MutableMap<String, Long> = linkedMapOf()`。
2. `record(classification)`（`:271-278`）：
   `byType.merge(classification.type.name, 1L) { a, b -> a + b }`。
3. 进度消息（`:252-256`）改为：
   `"已扫描 ${counters.scanned}，${counters.byType.entries.joinToString("，") { "${it.key} ${it.value}" }}"`。
4. `statsMap()`（`:286-292`）把 `"sendable" to sendable` 换成 `"byType" to LinkedHashMap(byType)`。
5. `ExpertClassificationBackfillResult`（`:40-56`）把 `sendable` / `notSendable` 两个字段
   换成 `byType: Map<String, Long>`；`toResult()`（`:301-313`）同步。

### Task 4：删除 API DTO 字段

`ExpertIndexController.kt`：删除 `:404` 的 `val expertSendable: Boolean? = null`
与 `:454` 的 `expertSendable = expert.expertClassification?.sendable`。
其余 DTO 字段与 `from()` 参数逐字不动。

### Task 5：测试

1. `ExpertClassificationServiceTest`：删除所有 `sendable` 断言，
   改为直接断言 `type`（语义等价且更直接）。**不得新增或修改任何分类用例的输入**（M-3）。
2. `ExpertIndexWriterServiceTest`：断言序列化产物**不含** `sendable` 键，其余键逐字不变。
3. `ExpertClassificationBackfillServiceTest`：把 `sendable`/`notSendable` 断言改为
   `byType` 分项断言（如 `byType["ACADEMIC_RND"] == 3L`）。
4. `ExpertClassificationAdminControllerTest`、`ExpertClassificationSchedulerTest`：
   同步结果结构断言。
5. 新增：`ExpertClassificationVersionGateGuardTest` 的 `sendable` 守卫用例（04 建立）
   的白名单收窄为**空集** —— 04 时的四个白名单文件在本计划全部清理完毕。

---

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/.../expert/domain/ExpertClassification.kt` | Task 1 |
| 2 | `src/main/kotlin/.../expert/service/ExpertIndexWriterService.kt` | Task 2 |
| 3 | `src/main/kotlin/.../expert/service/ExpertClassificationBackfillService.kt` | Task 3 |
| 4 | `src/main/kotlin/.../expert/controller/ExpertIndexController.kt` | Task 4 |
| 5 | `src/test/kotlin/.../expert/service/ExpertClassificationServiceTest.kt` | Task 5-1 |
| 6 | `src/test/kotlin/.../expert/service/ExpertIndexWriterServiceTest.kt` | Task 5-2 |
| 7 | `src/test/kotlin/.../expert/service/ExpertClassificationBackfillServiceTest.kt` | Task 5-3 |
| 8 | `src/test/kotlin/.../expert/controller/ExpertClassificationAdminControllerTest.kt` | Task 5-4 |
| 9 | `src/test/kotlin/.../expert/service/ExpertClassificationSchedulerTest.kt` | Task 5-4 |
| 10 | `src/test/kotlin/.../expert/service/ExpertClassificationVersionGateGuardTest.kt` | Task 5-5 |
| 11 | `src/test/kotlin/.../expert/service/ExpertSearchServiceTest.kt` | A3 授权：删除两个 I1-5 派生用例（`parses expertClassification with type-derived sendable…` ~:1874、`ES sendable=true cannot override OUT_OF_SCOPE…` ~:1929，断言被删的 `c.sendable` 属性，编译阻塞；语义已被其余 type 断言覆盖） |
| 12 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | A4 授权：`classification(type)` fixture helper 的 3 处 `ExpertClassification.SENDABLE_TYPES` 成员判定（:4304/:4305/:4307）改为 fixture 局部集合 `setOf(ExpertType.PRODUCTION_RND, ExpertType.ACADEMIC_RND, ExpertType.HYBRID_RND)`（原常量前三值，行为逐字不变） |

合计 12 个文件（A3 + A4 授权，见 Amendments 表）；子系统 2 个（expert / campaign 测试）。**零前端文件，故无 `## 样式契约`。**

---

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。

```bash
# 执行前的范围闸门（I5-5）：命中文件多于变更清单即停止
grep -rln "sendable" src/main/kotlin src/test/kotlin

# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='ExpertClassificationServiceTest,ExpertIndexWriterServiceTest,ExpertClassificationBackfillServiceTest,ExpertClassificationAdminControllerTest,ExpertClassificationSchedulerTest,ExpertClassificationVersionGateGuardTest'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：Maven 输出 `Tests run: N, Failures: 0, Errors: 0` 且 `BUILD SUCCESS`；
`git diff --check` 无输出。
来源：`CLAUDE.md:5-20`「Commands」章节。

---

## 验收标准

- **I5-1**：`grep -rn "notSendable\|可发信\|不可发信" src/main/kotlin` 零输出；
  单测断言回填结果含 `byType` 分项。
- **I5-2**：`git diff` 中不出现手写的类型字符串名单；`byType` 的键来自 `classification.type.name`。
- **I5-3**：`git diff src/main/resources/es/` 为空；
  `grep -rn "sendable" src/main/resources/es/*.json` 仍命中三处（孤儿字段保留）。
- **I5-4**：`git diff src/main/kotlin/.../ExpertSearchService.kt` 为空。
- **I5-5**：执行前的范围闸门命令输出与变更文件清单一致
  （`gateTemplateFilter.test.js`、`ManualInitialOutreachService.kt` 的发件账号语义命中不计，
  以及 I5-5 固定排除项中列出的 6 个文件不计；A3 修订）。
- **M-1 终局判据**：`ExpertClassificationVersionGateGuardTest` 的 `sendable` 守卫白名单为**空集**
  且用例通过 —— 全仓库不再有任何一处读取 `expertClassification.sendable`。
- 回归：执行「验证命令」节的全量测试命令与构建命令通过。

---

## 人工验收清单

### A5-1: 回填进度显示按类型分项
- 前置条件: 本计划已发布。
- 操作步骤: 触发一次分类回填（DRY_RUN 即可），观察任务进度消息。
- 预期结果: 消息形如「已扫描 5000，ACADEMIC_RND 1800，UNKNOWN 2100，OUT_OF_SCOPE 1050…」；
  **不再出现**「可发信 / 不可发信」字样。
- 覆盖: I5-1、需求描述第 2 条

### A5-2: 专家列表页零影响（回归）
- 前置条件: 记录本计划上线前专家列表页某一页的显示内容（类型 chip、分数 tooltip）。
- 操作步骤: 上线后打开同一页。
- 预期结果: 完全一致 —— 因为前端从未消费 `expertSendable`
  （`grep -n "sendable" app.js` 零命中）。
- 覆盖: must-NOT-change 第 4 条

### A5-3: 新写入的文档不再带 sendable（抽查）
- 前置条件: 本计划已发布，且跑过一次分类回填（EXECUTE）。
- 操作步骤: 取一条本次被回填的文档，看其 `expertClassification` 对象。
- 预期结果: 含 `type` / `productionScore` / `researchScore` / `version` /
  `sourceFingerprint` / `classifiedAt` / 两个 evidence 数组；**不含** `sendable`。
  （存量未被回填的文档仍可能残留该键，属预期，见 I5-3。）
- 覆盖: I5-3

### A5-4: 分类结果零变化（回归，最关键的一条）
- 前置条件: 本计划上线**前**跑一次全 CANDIDATE 层的类型分布聚合并记录。
- 操作步骤: 上线后（不跑回填）再聚合一次。
- 预期结果: 各类型条数**逐字相同**。本计划只删表述，不动判定（主计划 M-3）。
- 覆盖: must-NOT-change 第 1 条

### A5-5: 发信人群零变化（回归）
- 前置条件: 记录上线前某个定时任务配置的预估命中数。
- 操作步骤: 上线后用同样条件再看一次。
- 预期结果: 数字**逐字相同**。
- 覆盖: must-NOT-change 第 1、3 条
