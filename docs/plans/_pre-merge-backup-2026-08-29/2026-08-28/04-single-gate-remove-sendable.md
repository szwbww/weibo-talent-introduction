# 子计划 04：删除 sendable / 版本门禁，研发类型成为唯一收口点

> 主计划：[`00-single-gate-master.md`](./00-single-gate-master.md)
> **依赖（硬）**：
> - 子计划 03 必须**先上线**且 V109 已应用 —— 否则本计划上线瞬间所有空配置等价于「发给零个人」。
> - 子计划 02 必须**先上线**（旧首发链路改为显式配置类型集合）—— 02 之后
>   `searchSendableExpertsWithEmail`（`ExpertSearchService.kt:404-425`）**零生产调用点**，
>   本计划才能直接删掉它；否则那条链路会失去唯一的取人条件。

---

## 需求描述

**Observable outcome**

1. INTRODUCTION 的发信目标判定**只剩「研发类型集合」一个条件**。
   代码中不再存在 `sendable`、分类策略版本或任何硬编码类型名单参与发信判定。
2. 运营在页面上勾选的类型，就是实际收到邮件的人群 —— 所见即所得。
3. 类型集合为空时**发给零个人**（fail-closed），而不是"不限"。

**What must NOT change**

1. `ExpertClassificationService` 的分类规则、阈值、词表、`VERSION` —— 一行不改（主计划 M-3）。
2. MATERIAL_REMINDER 的目标构建逐字不变（本计划所有改动都在
   `mailType == INTRODUCTION` 分支内）。
3. `expertTypePredicate()` 与 `expertTypesFilter()` 的既有实现与契约不变
   （空集合仍返回 `null`；fail-closed 的决定放在调用点，不改这两个函数）。
4. 跳过原因码 `EXPERT_NOT_SENDABLE` 的**常量名与字符串值**不变
   （已落库的历史执行记录依赖它）；只改它的中文 label。
5. 列表页的研发类型筛选（查询维度）仍是「空 = 不限」，不受本计划影响。

**Out of scope**

- 删除 `ExpertClassification.sendable` 派生属性、`SENDABLE_TYPES`、
  `ExpertIndexWriterService` 的 `sendable` 序列化、三份 mapping 中的 `sendable` 声明
  —— 全部属子计划 05（本计划只删**读取方**，字段本身留到下一步删，避免一次改动面过大）。
- 任何前端改动（本计划零前端文件）。

---

## 关键不变量

### Invariant I4-1: 发信判定的唯一输入是 `scope.expertTypes`
- Rule: INTRODUCTION 的目标判定只允许读取 `expertClassification.type`（与 `scope.expertTypes` 比对）。
  禁止读取 `expertClassification.sendable`、`.version`，禁止出现任何硬编码类型名单。
- Applies to: `ExpertSearchService`、`ManualInitialOutreachService.buildEsFiltersForLevel`、
  `ManualInitialOutreachService.runIntroductionFromSnapshot`、`RecipientScope.matchesExpert`。
- Violation consequence: 留下任一隐式门禁即违反主计划 M-1，页面口径与实际发送再次分裂。
- 来源: original（主计划 M-1 的落地形式）

### Invariant I4-2: 空集合 = 发给零个人（fail-closed），且必须显式表达
- Rule: `scope.expertTypes` 为空时，ES 侧必须追加一个**恒不命中**的 filter，
  内存侧必须返回 `false`。不得沿用"空 = 不追加 filter"的旧语义。
- Applies to: `buildEsFiltersForLevel`（ES）、`RecipientScope.matchesExpertType`（内存）。
- Violation consequence: 沿用旧语义 = 删掉门禁后空集合等于「发给全部人（含医学越界、纯服务）」，
  这是本次改造唯一会造成线上事故的路径（主计划 M-2）。
- 来源: original

### Invariant I4-3: `expertTypesFilter` 的既有契约不动，fail-closed 放在调用点
- Rule: 不修改 `ExpertSearchService.expertTypesFilter():124-134`（空集合返回 `null`）
  与 `expertTypePredicate():142-152`。空集合的处理写在
  `buildEsFiltersForLevel` 的调用点，用一个显式命名的 `MATCH_NONE_FILTER` 表达。
- Applies to: 同上。
- Violation consequence: 直接把 `expertTypesFilter` 改成「空集合返回 should:[]」会污染
  列表页等其他调用点（那里空 = 不限是正确语义），造成列表页恒零命中。
- 来源: K-batch-multi-value-filter-seams（"空集合必须返回 null，否则所有不限的任务静默停发"
  —— 本计划反向利用同一机制时，必须限定在发信调用点）

### Invariant I4-4: 类型判定在 Kotlin 侧只有一份实现
- Rule: 内存侧的类型判定抽成 `RecipientScope.matchesExpertType(profile)` 唯一实现，
  由 `matchesExpert()` 与 `ManualInitialOutreachService` 的发送前门禁**共同调用**。
  禁止第二份逐字复刻。
- Applies to: `BatchExecutionModels.kt`、`ManualInitialOutreachService.kt:604-620`。
- Violation consequence: 今天四处门禁里有三份是同一段代码的手抄副本
  （见主计划「现状审计」），正是它导致本次改造要同步改四个地方。
  不收敛成一份，下一次改动会重蹈覆辙。
- 来源: original（对 [[K-sendable-gate-two-implementations]] 的直接修正）

### Invariant I4-5: ES 与内存两侧口径必须一致
- Rule: 对同一个 `(profile, scope)`，ES 谓词与 `matchesExpertType` 的判定结果必须相同，
  包括 `UNCLASSIFIED`（= `expertClassification.type` 字段**不存在** / Kotlin 侧为 `null`）。
- Applies to: `expertTypePredicate` ↔ `matchesExpertType`。
- Violation consequence: 两侧分裂会让"预估命中数"与"实际发送数"对不上
  （[[K-batch-send-filter-retry-parity]] 记录过同类事故）。
- 来源: K-batch-send-filter-retry-parity

### Invariant I4-6: `ACCEPTED_CLASSIFICATION_VERSIONS` 随门禁一起删除
- Rule: 删除 `ExpertClassificationService.kt:227` 的 `ACCEPTED_CLASSIFICATION_VERSIONS`。
  这是本计划**唯一**允许对该文件做的改动（主计划 M-3）。
- Applies to: `ExpertClassificationService.kt`。
- Violation consequence: 留着无消费者的常量，下一个人会以为门禁还在。
- 来源: original

---

## 现状审计

### 发信门禁：四处（2026-08-28 grep 复核，子计划 02 上线后剩两处）

`grep -rn "classification.version\|expertSendableFilter" src/main/kotlin`

| # | 位置 | 形态 | 本计划处置 |
|---|---|---|---|
| 1 | `ExpertSearchService.expertSendableFilter():55-63` | ES 谓词 | **删除函数** |
| 1a | `ManualInitialOutreachService.kt:1326` | 调用点 | 删除该行，改为强制类型 filter |
| 1b | `ExpertSearchService.kt:420` | 调用点（`searchSendableExpertsWithEmail`） | **删除整个方法**（02 之后零生产调用点） |
| 2 | `BatchExecutionModels.kt:66-72` | 内存（`matchesExpert` 开头的硬门禁块） | **删除该块** |
| 3 | `ManualInitialOutreachService.kt:604-620` | 内存（发送前最后门禁） | 改为调用 `scope.matchesExpertType` |
| 4 | `InitialOutreachService.kt:44-45` | 内存 | **子计划 02 已改为按配置的类型集合判定**，本计划不再触及 |

> **对既有知识条目 [[K-sendable-gate-two-implementations]] 的更正**：该条写「两处独立实现」，
> 实测为**四处**（内存侧三份逐字相同的复刻）；其记录的行号
> （`:1324` / `:376` / `:1951`）已随 05A / 05A-2 偏移。本计划完成后必须回写更正。

### 类型筛选现状（子计划 02〈2026-08-25 目录〉已建成）

- `ALLOWED_EXPERT_TYPES`（`ExpertSearchService.kt:115-116`）
  = `ExpertType.values()` 六值 + 字面量 `"UNCLASSIFIED"`。
- `expertTypesFilter(types)`（`:124-134`）：trim/丢空/去重 → 空集合返回 `null`
  → 否则产出单个 `bool.should + minimum_should_match:1`。
- `expertTypePredicate(type)`（`:142-152`）：`UNCLASSIFIED` → `must_not exists
  expertClassification.type`；其余 → `term expertClassification.type = <type>`。
- ES 追加点 `buildEsFiltersForLevel`（`ManualInitialOutreachService.kt:1325-1330`）当前形态：

```kotlin
        if (scope.mailType == BatchSendType.INTRODUCTION.name) {
            // I2-1/I2-6: 类型筛选是硬门禁之内的可选收窄（空集合返回 null，不追加，I2-3）。
            ExpertSearchService.expertTypesFilter(scope.expertTypes)?.let { filters.add(it) }
            filters.add(ExpertSearchService.expertSendableFilter())
        }
```

- 内存侧 `matchesExpert`（`BatchExecutionModels.kt:65-91`）当前形态：先硬门禁块（`:66-72`），
  再 `operatorStatuses`（`:74-82`），再类型块（`:83-91`，`expertTypes.isNotEmpty()` 才判定）。
- `buildEsFiltersForLevel` 有 3 个调用方（[[K-batch-multi-value-filter-seams]]）：
  材料提醒目标构建、`countEsTargets`（预估）、`fetchEsPage`（发送）
  —— 改这一处即三条通路同时生效，**预估与实际发送不会分裂**。

### 跳过原因码

- `BatchOutcomeReasonCodes.EXPERT_NOT_SENDABLE`（`BatchExecutionModels.kt:171`）
  值为字符串 `"EXPERT_NOT_SENDABLE"`；label 在 `:183` 为「专家非生产/科研可发类型」。
- 测试引用：`ManualInitialOutreachServiceTest.kt:4149`（count）、`:4150`（label 逐字断言）、
  `:4192`、`:4228`。**改 label 必须同步改 `:4150` 的断言。**

### `runIntroductionFromSnapshot` 内的可用变量

`ManualInitialOutreachService.kt:499` 起，`val scope = resolveScope(snapshot)` 在 `:510`，
早于 `:604-620` 的发送前门禁 —— **`scope` 在该处可直接使用**，无需新增参数传递。

### Interaction points

1. `buildEsFiltersForLevel`（ES 写查询）× `countEsTargets` / `fetchEsPage`（读）
   —— 预估与发送共用同一 seam，必须同时验证两者。
2. `matchesExpertType`（内存）× `expertTypePredicate`（ES）
   —— 同一语义两种实现，必须有一条同口径断言（I4-5）。
3. `scope.expertTypes` 由子计划 03 保证非空 × 本计划的 fail-closed
   —— 两者叠加后，正常路径永远不会走到 fail-closed 分支；
   fail-closed 只是防御老快照/直连 API 的兜底。

---

## 实现方案

### Task 1：ES 侧删除门禁，类型 filter 变强制（I4-1、I4-2、I4-3）

`ExpertSearchService.kt`：

1. **删除** `expertSendableFilter()`（`:50-63`，含其上方 I3-2 注释块）。
2. 新增一个显式命名的恒不命中谓词，与 `expertTypesFilter` 并列：

```kotlin
        /**
         * I4-2: 发信目标的 fail-closed 表达 —— 类型集合为空时追加它，命中恒为 0。
         * 只允许用在 INTRODUCTION 的发信目标查询上；列表页等"空 = 不限"的调用点禁止使用。
         */
        val MATCH_NONE_FILTER: Map<String, Any> = mapOf(
            "bool" to mapOf("must_not" to listOf(mapOf("match_all" to emptyMap<String, Any>())))
        )
```

`ManualInitialOutreachService.buildEsFiltersForLevel`（`:1325-1330`）整段替换为：

```kotlin
        if (scope.mailType == BatchSendType.INTRODUCTION.name) {
            // I4-1: INTRODUCTION 的唯一收口点 —— 只按研发类型集合判定，无第二个门禁。
            // I4-2: 空集合 = 发给零个人（fail-closed），不是"不限"。
            filters.add(
                ExpertSearchService.expertTypesFilter(scope.expertTypes)
                    ?: ExpertSearchService.MATCH_NONE_FILTER
            )
        }
```

### Task 2：内存侧收敛为一份实现（I4-1、I4-2、I4-4、I4-5）

`BatchExecutionModels.kt`：

1. **删除** `matchesExpert` 开头的硬门禁块（`:66-72`，含其注释）。
2. 把类型判定抽成成员函数（`RecipientScope` 内）：

```kotlin
    /**
     * I4-4: 研发类型判定的**唯一** Kotlin 实现，由 [matchesExpert] 与
     * ManualInitialOutreachService 的发送前门禁共同调用，禁止再复刻第二份。
     * I4-5: 与 ES 的 expertTypePredicate 同口径 —— `UNCLASSIFIED` = 类型为 null。
     * I4-2: 空集合返回 false（fail-closed）。
     */
    fun matchesExpertType(profile: ExpertProfile): Boolean {
        val typeName = profile.expertClassification?.type?.name
        return expertTypes.any { if (it == "UNCLASSIFIED") typeName == null else typeName == it }
    }
```

3. 原类型块（`:83-91`）替换为：

```kotlin
        // I4-1: INTRODUCTION 的唯一收口点；MATERIAL_REMINDER 不判定（零影响）。
        if (mailType == BatchSendType.INTRODUCTION.name && !matchesExpertType(profile)) return false
```

4. `:183` 的 label 由「专家非生产/科研可发类型」改为「研发类型不在本次选择范围内」。
   常量名与字符串值 `"EXPERT_NOT_SENDABLE"` **不变**（must-NOT-change 第 4 条）。

### Task 3：发送前门禁改为调用同一实现（I4-1、I4-4）

`ManualInitialOutreachService.kt:604-620`，把 `classification?.sendable != true ||
classification.version !in ...` 的判定整体替换为：

```kotlin
                // I4-1/I4-4: 发送前最后门禁 —— 与 ES 查询、内存重试过滤共用同一份类型判定。
                // 查询/缓存/未来重构错误可能绕过 ES 侧，创建 contact 前再判一次。
                if (!scope.matchesExpertType(expert)) {
```

`accumulator.recordSkipped(...)` 的原因码保持 `EXPERT_NOT_SENDABLE`，
消息文案由「专家非生产/科研可发类型：${expert.orcidId}」改为
「研发类型不在本次选择范围内：${expert.orcidId}」。其余记账逻辑（`processedTotal++` 等）逐字不动。

### Task 4：删除版本集合常量（I4-6）

`ExpertClassificationService.kt:222-227`，删除 `ACCEPTED_CLASSIFICATION_VERSIONS`
及其文档注释。`VERSION`（`:220`）**逐字保留**。

### Task 5：测试

`ExpertSearchServiceTest`：
1. **删除** `expertSendableFilter` 的逐字结构断言用例（函数已不存在）。
2. **删除** `searchSendableExpertsWithEmail` 的两个用例（`:2250`、`:2322`）—— 方法本身被删除。
   删除前须确认 `grep -rn "searchSendableExpertsWithEmail" src/main src/test` 除这两处外零命中
   （02 已把 `InitialOutreachServiceTest` 的 15 处 stub 改到新方法上）。
3. 新增：`MATCH_NONE_FILTER` 的结构逐字断言。

`ManualInitialOutreachServiceTest`：
4. 新增：INTRODUCTION + `expertTypes = ["ACADEMIC_RND"]` 时，`buildEsFiltersForLevel`
   产出的 filters **不含** 任何 `expertClassification.sendable` 或 `.version` 项，
   且含 `expertTypesFilter` 的 should 结构（I4-1）。
5. 新增：INTRODUCTION + `expertTypes = []` 时，filters 含 `MATCH_NONE_FILTER`（I4-2）。
6. 新增：MATERIAL_REMINDER 时 filters 不含任何类型项（must-NOT-change 第 2 条）。
7. 修改 `:4150` 的 label 断言为新文案。
8. 新增：`type = OUT_OF_SCOPE` 的专家在 `expertTypes = ["ACADEMIC_RND"]` 下被跳过并记
   `EXPERT_NOT_SENDABLE`；`type = OUT_OF_SCOPE` 且 `expertTypes` **含** `"OUT_OF_SCOPE"` 时
   **被发送**（证明不再有隐式门禁，I4-1）。

`ExpertClassificationVersionGateGuardTest`（**既有守卫，本计划扩展**）：
9. 该类现有用例扫描 `classification.version` 与 `VERSION` 的直接比较，白名单为空集
   （05A-2 Part C 的产物）。本计划**新增第二个用例**，用同一套扫描机制断言：
   `src/main/kotlin` 下读取 `expertClassification.sendable` / `classification.sendable`
   的位置必须恰好等于白名单 —— 白名单只允许
   `ExpertClassification.kt`（派生属性自身的定义）、
   `ExpertIndexWriterService.kt`（序列化，子计划 05 才删）、
   `ExpertClassificationBackfillService.kt`（统计计数，子计划 05 才改）、
   `ExpertIndexController.kt`（API DTO，子计划 05 才删）四个文件。
   **`ExpertSearchService.kt`、`BatchExecutionModels.kt`、`ManualInitialOutreachService.kt`
   出现在命中集合里即失败** —— 这就是主计划 M-1 的机器判据。

`BatchSendTaskRuntimeIntegrationTest`：
10. 新增 ES/内存同口径断言（I4-5）：对同一组 profile（含 `expertClassification = null` 的一条），
   `matchesExpertType` 的结果与 `expertTypePredicate` 生成的谓词语义一致
   （`UNCLASSIFIED` ↔ 类型字段不存在）。

---

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | 删 `expertSendableFilter`；加 `MATCH_NONE_FILTER` |
| 2 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | Task 1 尾段 + Task 3 |
| 3 | `src/main/kotlin/.../campaign/domain/BatchExecutionModels.kt` | Task 2 |
| 4 | `src/main/kotlin/.../expert/service/ExpertClassificationService.kt` | 删 `ACCEPTED_CLASSIFICATION_VERSIONS` |
| 5 | `src/test/kotlin/.../expert/service/ExpertSearchServiceTest.kt` | Task 5 第 1~3 条 |
| 6 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | Task 5 第 4~8 条 |
| 7 | `src/test/kotlin/.../expert/service/ExpertClassificationVersionGateGuardTest.kt` | Task 5 第 9 条（扩展守卫） |
| 8 | `src/test/kotlin/.../campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | Task 5 第 10 条 |

合计 8 个文件；子系统 2 个（expert / campaign）。**零前端文件，故无 `## 样式契约`。**

---

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='ExpertSearchServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest,ExpertClassificationVersionGateGuardTest'

# 分类器回归（本计划只允许删一个常量，判定逻辑零改动）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=ExpertClassificationServiceTest

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

- **I4-1（本计划最重要的机器判据）**：`ExpertClassificationVersionGateGuardTest` 的**两个**用例通过
  —— 版本比较白名单为空集；`sendable` 读取白名单恰好是
  `ExpertClassification.kt` / `ExpertIndexWriterService.kt` /
  `ExpertClassificationBackfillService.kt` / `ExpertIndexController.kt` 四个文件
  （均为子计划 05 的范围，且都不是过滤用途）。
  辅以一条 grep 必须零输出：

```bash
grep -rn "expertSendableFilter\|ACCEPTED_CLASSIFICATION_VERSIONS" src/main/kotlin
```
- **I4-2**：单测断言空 `expertTypes` 时 filters 含 `MATCH_NONE_FILTER`；
  `matchesExpertType` 对任意 profile 返回 `false`。
- **I4-3**：`git diff` 显示 `expertTypesFilter` 与 `expertTypePredicate` 两个函数体逐字未改。
- **I4-4**：`grep -rn "UNCLASSIFIED\") typeName == null" src/main/kotlin` 恰好命中 **1 处**
  （`BatchExecutionModels.kt`）。
- **I4-5**：`BatchSendTaskRuntimeIntegrationTest` 的同口径用例通过。
- **I4-6**：`git diff src/main/kotlin/.../ExpertClassificationService.kt` 只有删除，
  且不含 `VERSION` 行。
- 回归：执行「验证命令」节的全量测试命令与构建命令通过。

---

## 人工验收清单

### A4-1: 勾什么发什么 —— 正向
- 前置条件: 新建一个 INTRODUCTION 定时任务配置，研发类型只勾「学术科研」，其余条件不限。
- 操作步骤: 1. 看页面预估「当前条件命中 N 位专家」；2. 用 ES 直接查
  `expertClassification.type = "ACADEMIC_RND"` 且满足同样其余条件的数量。
- 预期结果: 两个数字**一致**（允许因索引刷新有极小偏差）。
  说明再没有第二个看不见的条件在收窄。
- 覆盖: I4-1、需求描述第 1、2 条

### A4-2: 勾什么发什么 —— 反向（本计划的核心证明）
- 前置条件: 新建一个 INTRODUCTION 配置，研发类型**只勾「医学越界」**。
- 操作步骤: 看预估命中数。
- 预期结果: 命中数 **> 0**（实测该类型全库约 2.1 万人）。
  改造前这个组合恒为 0（被 `sendable` 硬门禁挡掉）。
  **这一条通过，才证明黑盒门禁真的没了。**
  ⚠️ 只看预估，**不要真的发送**。
- 覆盖: I4-1

### A4-3: 空集合发给零个人
- 前置条件: 需要绕过子计划 03 的前端必填（用 API 直接 POST 一个 `expertTypes: []` 的快照到
  `/talent/api/mail/batch-send/recipients/preview`）。
- 操作步骤: 提交该预估请求。
- 预期结果: 命中数为 **0**。绝不能返回全量。
- 覆盖: I4-2

### A4-4: 预估与实际发送口径一致
- 前置条件: 一个只勾「学术科研」、限定小地区使预估数在 10~50 之间的配置。
- 操作步骤: 1. 记录预估命中数；2. 实际跑一轮；3. 看执行结果里的已发送 + 跳过明细。
- 预期结果: 不出现 `EXPERT_NOT_SENDABLE` 跳过（因为 ES 侧已按同一条件筛过）；
  已发送数与预估数在正常损耗范围内（邮箱抑制、账号配额等）相符。
- 覆盖: Interaction point 1、I4-5

### A4-5: 材料提醒零影响（回归）
- 前置条件: 一个 MATERIAL_REMINDER 任务，研发类型留空。
- 操作步骤: 1. 记录本计划上线**前**的预估命中数；2. 上线后用同样条件再看一次。
- 预期结果: 两次数字**逐字相同**。
- 覆盖: must-NOT-change 第 2 条

### A4-6: 跳过原因文案已更新
- 前置条件: 构造一次会产生跳过的发送（如内存重试通路里混入一个类型不匹配的联系人）。
- 操作步骤: 看执行详情的跳过原因。
- 预期结果: 文案为「研发类型不在本次选择范围内」，原因码仍是 `EXPERT_NOT_SENDABLE`。
- 覆盖: must-NOT-change 第 4 条

### A4-7: 列表页筛选未被 fail-closed 污染（回归）
- 前置条件: 专家列表页，研发类型 chip 一个都不勾。
- 操作步骤: 看列表结果数。
- 预期结果: 显示**全部**专家（空 = 不限），不是 0。
  说明 `MATCH_NONE_FILTER` 没有泄漏到查询维度。
- 覆盖: I4-3、must-NOT-change 第 5 条
