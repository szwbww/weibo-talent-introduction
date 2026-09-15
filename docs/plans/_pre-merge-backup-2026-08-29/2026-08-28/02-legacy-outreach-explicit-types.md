# 子计划 02：旧首发链路改为显式配置研发类型

> 主计划：[`00-single-gate-master.md`](./00-single-gate-master.md)
> 依赖：无（可与 01、03 并行）
> **必须早于子计划 04**：04 会删除 `expertSendableFilter()`，而本链路是它的第二个调用点。
> 本计划先把这条链路从「隐式硬门禁」切换到「显式配置的类型集合」，04 才能安全删函数。

---

## 需求描述

**Observable outcome**

1. 旧的定时/队列/接口首发链路（`InitialOutreachService.sendInitialBatch`）不再依赖
   `sendable` 硬门禁，改为按**显式配置的研发类型集合**取目标。
2. 该配置未设置时，这条链路**快速失败并明确报错**，绝不退化成"发给所有人"。

**What must NOT change**

1. 批量邮件控制台（`ManualInitialOutreachService`）的任何行为——本计划不碰它。
2. `expertSendableFilter()` 本身仍然保留（删除是子计划 04 的事），
   本计划只是让这条链路**不再调用**它。
3. `InitialOutreachService` 的发送、账号分配、抑制名单、contact 创建等逻辑逐字不变，
   只改「取哪批人」和「发送前门禁判据」两处。
4. `expertTypesFilter()` / `expertTypePredicate()` 的实现与契约不变。

**Out of scope**

- 删除这条链路（3 个调用方 + 队列消息 + 属性 + 测试共涉及 16 个文件，超出上限；
  且在 CP-2 结论出来前不宜删）。
- 给该链路加地区/标签/邮箱服务商等其他筛选维度。
- 前端改动（本计划零前端文件）。

---

## 关键不变量

### Invariant I2-1: 类型集合必须来自配置，不得有代码默认值
- Rule: `MailSchedulingProperties.initialOutreachExpertTypes` 的 Kotlin 默认值必须是
  **空列表**，`application.yml` 的默认值必须是**空字符串**。
  禁止在任何一层写入 `PRODUCTION_RND` 等具体值作为兜底。
- Applies to: `MailSchedulingProperties.kt`、`application.yml`、`src/test/resources/application.yml`。
- Violation consequence: 代码里的默认类型名单就是主计划 M-1 要消除的黑盒门禁 ——
  运营在任何页面上都看不到它，却决定着发给谁。
- 来源: original（主计划 M-1）

### Invariant I2-2: 未配置 = 快速失败，不是"不限"
- Rule: `sendInitialBatch` 在取目标之前 `require(types.isNotEmpty())`，
  抛 `IllegalArgumentException` 且消息指明缺哪个配置项。
- Applies to: `InitialOutreachService.sendInitialBatch()`。
- Violation consequence: 空集合若被解释成"不限"，这条链路会把医学越界、纯服务全部发出去；
  若被静默跳过，任务会"成功但零发送"，同样难以发现。**必须显式抛错。**
- 来源: original

### Invariant I2-3: 取值白名单仍是唯一权威
- Rule: 配置值的合法性由 `ExpertSearchService.ALLOWED_EXPERT_TYPES:115-116` 判定
  （从 `ExpertType.values()` 派生 + `"UNCLASSIFIED"`）。禁止手写六值名单。
- Applies to: `InitialOutreachService`（启动时校验）。
- 来源: K-（2026-08-25 子计划 02 的 I2-1）

### Invariant I2-4: 新查询方法不得混入非类型条件
- Rule: 替换 `searchSendableExpertsWithEmail` 的新方法只允许两个 filter 项：
  `exists email` 与 `expertTypesFilter(types)` 的返回值。
  不得追加 `expertSendableFilter()`、`disciplineFilter` 或任何其他条件。
- Applies to: `ExpertSearchService`。
- Violation consequence: 混入即等于换了个位置继续做黑盒过滤（M-1 违规）。
- 来源: original

### Invariant I2-5: 发送前门禁与查询同口径
- Rule: `InitialOutreachService.kt:44-45` 的内存门禁改为按**同一个类型集合**判定：
  类型为 `null` 时只有集合含 `"UNCLASSIFIED"` 才放行 ——
  与 `expertTypePredicate` 的 `UNCLASSIFIED = must_not exists` 语义一致。
- Applies to: `InitialOutreachService`。
- Violation consequence: 两侧分裂会让查询取回的人在发送前被莫名跳过
  （[[K-batch-send-filter-retry-parity]] 记录过同类事故）。
- 来源: K-batch-send-filter-retry-parity

---

## 现状审计

### 旧首发链路的三个调用方（2026-08-28 grep 复核）

`grep -rn "sendInitialBatch" src/main/kotlin`

| 调用方 | 位置 | 触发条件 |
|---|---|---|
| HTTP 接口 | `MailAutomationController.kt:64-70` `POST /api/mail-automation/initial-outreach` | 手工调用；`grep -n "initial-outreach" src/main/resources/static/app.js` **零命中**，前端不调它 |
| 队列消费者 | `MailQueueConsumer.kt:22-34` | `@ConditionalOnProperty talent-introduction.mail-queue.enabled=true` |
| 定时任务 | `MailAutomationScheduler.kt:41-74` | `@ConditionalOnProperty talent-introduction.scheduling.enabled=true`（`application.yml:72` 默认 **false**）且 `initial-outreach-cron` 非 `-`（`:75` 默认 `-`） |

**本计划不改这三个调用方**（签名不变），因此文件面控制在 7 个以内。

### 目标查询

`ExpertSearchService.searchSendableExpertsWithEmail(size, level)`（`:404-425`）：
`filter = [exists email, expertSendableFilter()]`，`_source = sourceFields()`。
唯一生产调用点是 `InitialOutreachService.kt:34`。
测试引用 16 处（`InitialOutreachServiceTest` 15 处 stub + `ExpertSearchServiceTest:2250/2322` 两个用例）。

### 发送前门禁

`InitialOutreachService.kt:40-49`：
`classification?.sendable != true || classification.version !in ACCEPTED_CLASSIFICATION_VERSIONS` → `skipped++`。
注释标记为 I3-1/I3-4「发送前最后门禁」。

### 配置

`MailSchedulingProperties`（`config/MailSchedulingProperties.kt`）是 `@ConstructorBinding` data class，
现有 12 个字段，`initialOutreach*` 系列 4 个。
`InitialOutreachService` 的构造参数已含 `schedulingProperties: MailSchedulingProperties`
（`:30`），**无需新增依赖**。
`application.yml:71-82` 与 `src/test/resources/application.yml:59-65` 两份都声明了 `scheduling` 段。

### Interaction points

1. 配置（`MailSchedulingProperties`）→ 查询（`ExpertSearchService`）→ 发送前门禁（`InitialOutreachService`）
   —— 同一个类型集合穿过三层，任一层口径不同即分裂（I2-5）。
2. 本计划保留 `expertSendableFilter()` 函数但去掉本链路的调用
   → 子计划 04 删除该函数时，只剩控制台一个调用点。

---

## 实现方案

### Task 1：配置项（I2-1）

`MailSchedulingProperties` 尾部新增字段（保持 data class 的尾部追加范式）：

```kotlin
    /** I2-1：旧首发链路的研发类型集合。**无代码默认值**，未配置即空 → 启动时快速失败（I2-2）。 */
    val initialOutreachExpertTypes: List<String> = emptyList()
```

`src/main/resources/application.yml` 在 `initial-outreach-send-jitter-ms` 之后新增：

```yaml
    initial-outreach-expert-types: ${MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES:}
```

`src/test/resources/application.yml` 的 `scheduling` 段同步新增同一行。

### Task 2：按类型取目标的查询方法（I2-4）

`ExpertSearchService`，**新增**方法（先不删旧方法，由子计划 04 连同 `expertSendableFilter` 一起删）：

```kotlin
    /**
     * I2-4: 旧首发链路专用 —— filter 只有两项：exists email 与研发类型集合。
     * 不得追加任何其他条件（主计划 M-1：唯一收口点）。
     * 调用方保证 expertTypes 非空（I2-2），故这里不处理空集合。
     */
    fun searchExpertsByTypesWithEmail(
        size: Int,
        level: ExpertIndexLevel = ExpertIndexLevel.CANDIDATE,
        expertTypes: List<String>
    ): ExpertSearchResult {
        require(size in 1..1000) { "size must be between 1 and 1000" }
        val typesFilter = expertTypesFilter(expertTypes)
            ?: throw IllegalArgumentException("expertTypes must not be empty")
        // 其余部分（_source、sort、分页、结果映射）逐字照搬 searchSendableExpertsWithEmail:409-425
    }
```

排序、`_source`、结果映射必须与 `searchSendableExpertsWithEmail` 逐字一致，
只替换 filter 数组的第二项。

### Task 3：链路切换（I2-2、I2-3、I2-5）

`InitialOutreachService.sendInitialBatch()` 开头（`:33` 之前）新增：

```kotlin
        // I2-2: 未配置即快速失败，绝不退化成"不限"。
        val types = schedulingProperties.initialOutreachExpertTypes
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(types.isNotEmpty()) {
            "未配置 talent-introduction.scheduling.initial-outreach-expert-types，旧首发链路拒绝执行"
        }
        // I2-3: 白名单唯一权威在 ExpertSearchService。
        types.forEach {
            require(it in ExpertSearchService.ALLOWED_EXPERT_TYPES) { "Invalid expert type: $it" }
        }
```

`:34` 的取目标改为：

```kotlin
        val experts = expertSearchService
            .searchExpertsByTypesWithEmail(size, ExpertIndexLevel.CANDIDATE, types).experts
```

`:40-49` 的发送前门禁改为（I2-5）：

```kotlin
            // I2-5: 与查询同口径 —— UNCLASSIFIED = 分类对象/类型不存在。
            val typeName = expert.expertClassification?.type?.name
            val matched = types.any { if (it == "UNCLASSIFIED") typeName == null else typeName == it }
            if (!matched) {
                skipped += 1
                return@forEachIndexed
            }
```

`skipped` 的记账与后续逻辑逐字不变。

### Task 4：测试

`ExpertSearchServiceTest`：
1. 新增：`searchExpertsByTypesWithEmail` 发出的请求体 `filter` **恰好两项**：
   `exists email` 与类型 should 结构；**不含** `expertClassification.sendable`
   或 `expertClassification.version`（I2-4）。
2. 新增：空 `expertTypes` 抛 `IllegalArgumentException`。
3. 新增：`level` 显式传 `APPLICATION` 时索引名与排序与旧方法一致（回归）。
4. 既有 `searchSendableExpertsWithEmail` 的两个用例（`:2250`、`:2322`）**保持不动**
   —— 该方法本计划不删。

`InitialOutreachServiceTest`：
5. 新增：`initialOutreachExpertTypes` 为空时，`sendInitialBatch` 抛
   `IllegalArgumentException` 且消息含配置项名（I2-2）。
6. 新增：配置为 `["ACADEMIC_RND"]` 时，传给 `searchExpertsByTypesWithEmail` 的第三个参数
   逐字为该列表。
7. 新增：类型为 `OUT_OF_SCOPE` 的专家在配置为 `["ACADEMIC_RND"]` 时被跳过（`skipped` +1）；
   配置含 `"OUT_OF_SCOPE"` 时**被发送**（证明不再有隐式门禁，I2-5）。
8. 新增：`expertClassification == null` 的专家，仅当配置含 `"UNCLASSIFIED"` 时被发送（I2-5）。
9. 既有 15 处 `searchSendableExpertsWithEmail` 的 stub 需改为 stub 新方法
   —— 这是本计划改动量最大的一处，执行时逐处替换，不得整体重写测试类。

---

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/.../config/MailSchedulingProperties.kt` | Task 1 尾部加字段 |
| 2 | `src/main/resources/application.yml` | Task 1 加一行 |
| 3 | `src/test/resources/application.yml` | Task 1 加一行 |
| 4 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | Task 2 新增方法 |
| 5 | `src/main/kotlin/.../campaign/service/InitialOutreachService.kt` | Task 3 |
| 6 | `src/test/kotlin/.../expert/service/ExpertSearchServiceTest.kt` | Task 4 第 1~3 条 |
| 7 | `src/test/kotlin/.../campaign/service/InitialOutreachServiceTest.kt` | Task 4 第 5~9 条 |

合计 7 个文件；子系统 2 个（expert / campaign+config）。**零前端文件，故无 `## 样式契约`。**

---

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='ExpertSearchServiceTest,InitialOutreachServiceTest'

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

- **I2-1**：`grep -n "initialOutreachExpertTypes" src/main/kotlin/.../MailSchedulingProperties.kt`
  显示默认值为 `emptyList()`；两份 `application.yml` 的默认值为空
  （`${MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES:}` 冒号后无内容）。
- **I2-2**：单测断言空配置抛 `IllegalArgumentException` 且消息含
  `initial-outreach-expert-types`。
- **I2-3**：`git diff` 中不出现手写的六值名单字面量数组。
- **I2-4**：单测断言新方法的 filter **恰好两项**；
  `grep -n "expertSendableFilter" src/main/kotlin/.../InitialOutreachService.kt` 零输出。
- **I2-5**：单测断言 `OUT_OF_SCOPE` / `null` 两种类型在不同配置下的放行结果，
  与 `expertTypePredicate` 的语义一致。
- 回归：执行「验证命令」节的全量测试命令与构建命令通过。

---

## 部署检查清单（与 CP-2 联动）

主计划 CP-2 的结论决定发布这份计划**之前**是否必须先加环境变量：

```bash
ssh root@150.158.92.103 \
  'grep -E "MAIL_SCHEDULING_ENABLED|MAIL_SCHEDULING_INITIAL_OUTREACH_CRON|MAIL_QUEUE" \
   /opt/apache-tomcat-9.0.71/bin/setenv.sh || echo "(未设置 → 默认关闭)"'
```

- **三条链路都关闭**（2026-08-28 实测支持该结论：`MAIL_QUEUE_ENABLED=false`；
  `MAIL_SCHEDULING_INITIAL_OUTREACH_CRON` 未设置 ⇒ 默认 `-` ⇒ 该 `@Scheduled` 方法禁用；
  HTTP 接口无前端调用方）→ 直接发布，无需加环境变量。这条链路本来就不跑，
  快速失败也不会被触发。收口证据：`task_execution` 表中 `task_type='INITIAL_OUTREACH'` **零行**（2026-08-28 实测），
  该链路从未产生过执行记录。详见主计划 CP-2。
- **任一开启** → 发布**之前**必须先在 `setenv.sh` 加：
  `export MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES=PRODUCTION_RND,ACADEMIC_RND,HYBRID_RND`
  （与今天 `SENDABLE_TYPES` 等价，行为不变），否则该链路下次触发时会抛错。

---

## 人工验收清单

### A2-1: 未配置时拒绝执行
- 前置条件: 环境变量 `MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES` 未设置。
- 操作步骤: `curl -X POST "$BASE_URL/mail-automation/initial-outreach?campaignId=1&size=1"`（带登录 cookie）。
- 预期结果: 返回错误，消息含「未配置 talent-introduction.scheduling.initial-outreach-expert-types」。
  **不得**返回"成功发送 0 封"，也不得真的发出邮件。
- 覆盖: I2-2、需求描述第 2 条

### A2-2: 配置后按类型取人
- 前置条件: 设置 `MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES=ACADEMIC_RND` 并重启。
- 操作步骤: 同上，`size=5`。
- 预期结果: 返回结果里被处理的专家类型**全部**是 `ACADEMIC_RND`（可在专家列表页按 orcidId 逐个核对）；
  `skipped` 为 0 或极小（仅因抑制名单等其他原因）。
- 覆盖: I2-4、I2-5、需求描述第 1 条

### A2-3: 医学越界也能被显式选中（证明无隐式门禁）
- 前置条件: 设置 `MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES=OUT_OF_SCOPE` 并重启。
- 操作步骤: `POST .../initial-outreach?campaignId=<一个测试 campaign>&size=1`。
- 预期结果: **能取到人**（改造前恒为 0 —— 被 `sendable` 挡掉）。
  ⚠️ 请用测试 campaign，验完立刻把环境变量改回去。
- 覆盖: I2-4、主计划 M-1

### A2-4: 批量控制台零影响（回归）
- 前置条件: 记录本计划上线前某个定时任务配置的预估命中数。
- 操作步骤: 上线后用同样条件再看一次。
- 预期结果: 数字**逐字相同**。本计划不碰控制台。
- 覆盖: must-NOT-change 第 1 条
