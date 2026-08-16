# 计划 06 — 批量发送任务的可达性过滤配置

> 依赖：计划 05（表达式与 `RecipientScope` 字段已就绪）。本族最后一份。共享证据见主计划。

## 需求描述

**Observable outcome**

1. 批量发送任务配置新增「可达性过滤」项，取值：不过滤 / 排除已失效（默认推荐）/ 仅高可达。
2. 该配置随任务执行传入 `RecipientScope.reachabilityFilter`，实际影响 ES 目标查询与重试路径。
3. 任务列表的配置摘要 pill 显示当前可达性过滤状态。

**What must NOT change**

- N-1 `batch_send_task_config` 既有全部列的默认值与语义。
- N-2 旧 typed API（`/api/mail/batch-send/types/{sendType}/config`）的请求体结构与响应结构。
- N-3 `BatchSendConfig`（KV 兼容 data class）的字段集 —— 新列**不加**进该类（`K-batch-config-legacy-adapter-field-preservation` 明确：加进去会把 KV 兼容层拖进变更范围）。
- N-4 既有 `gateFilterEnabled` 的全部 12 处前端触点行为（主计划 R-13）。

**Out of scope**

- O-1 收件人预估数按可达性拆分展示（「已排除 N 位失效」）。第一版只做过滤，不做归因展示。
- O-2 独立运行（非配置驱动）的手动批量发送入口传值（`K-independent-manual-run-not-in-config-lists`）。

## 关键不变量

### Invariant I-6-1: 旧 typed 适配器必须显式保留新列
- Rule: `BatchSendTaskConfigService.updateLegacyConfig()` 中必须新增一行 `reachabilityFilter = existing.reachabilityFilter`。
- Applies to: `BatchSendTaskConfigService.updateLegacyConfig()`（`:173-198`）。
- Violation consequence: 该方法用只含旧字段的请求调用**全量** `update(...)`，漏写即命中 `BatchSendTaskConfigUpdateCommand` 的 Kotlin 默认值，把存量配置**静默重置**为默认值——运营从旧界面改一次任意字段，可达性过滤就被抹掉，且无任何报错。
- 来源: K-batch-config-legacy-adapter-field-preservation（主计划 R-12 已实测复核该方法现有两条同款保留行 `operatorStatuses` 与 `gateFilterEnabled`，注释分别标 M-2 与 I4a-6）

### Invariant I-6-2: 三类映射按知识条目分工处理
- Rule: `toView()`（`:338` 附近）**要加**新列；`toLegacyConfig()`（`:208` 附近）与 `updateLegacyConfig` 的返回构造**不加**；三个 `*Fields()`（`:423/:439/:455` 附近）**要加**（走校验）。
- Applies to: `BatchSendTaskConfigService`。
- Violation consequence: 漏 `toView` → 前端读不到；误加 `toLegacyConfig` → 违反 N-3。
- 来源: K-batch-config-legacy-adapter-field-preservation

### Invariant I-6-3: 迁移不得含 `${}`
- Rule: V100 迁移文件中不得出现 `${` 字符序列。
- Applies to: `V100__add_reachability_filter_to_batch_send_task_config.sql`。
- Violation consequence: 生产 `application.yml` 未关 `placeholder-replacement`（默认 true），含 `${}` 的新迁移会导致生产启动即抛 "No value provided for placeholder expressions"。本迁移只加一个 VARCHAR 列，天然不含，但须在验收中显式断言。
- 来源: K-flyway-placeholder-replacement

### Invariant I-6-4: 校验白名单与筛选模式常量同源
- Rule: 配置项的合法值集合必须复用 `ExpertSearchService.ALLOWED_REACHABILITY_MODES`（计划 05 定义），禁止在 `BatchSendTaskConfigService` 内另写一份字符串集合。
- Applies to: `BatchSendTaskConfigService` 的字段校验。
- Violation consequence: 与 `K-discipline-unclassified-filter-bypasses` 记载的 `ALLOWED_DISCIPLINES` 双白名单同构——两处集合漂移后，界面能选的值在保存时 422，或保存成功但查询恒 0 命中。
- 来源: K-discipline-unclassified-filter-bypasses（其中「另有两处白名单不含 UNCLASSIFIED，会在保存配置时先一步 422」）

### Invariant I-6-5: 默认值 = 不过滤
- Rule: 新列 DB 默认值与 Kotlin 默认值均为「不过滤」（空串或 NULL，与 `reachabilityFilter(null) → null` 对齐）。
- Applies to: V100 迁移、实体、`BatchSendTaskConfigUpdateCommand`。
- Violation consequence: 若默认「排除已失效」，存量任务在升级后**静默改变投放范围**。主计划的上线节奏约束明确要求先展示、后开过滤。
- 来源: 主计划「上线节奏」

## 现状审计

### 迁移版本号（主计划 R-11）
```bash
ls src/main/resources/db/migration/ | sed 's/V\([0-9]*\)__.*/\1/' | sort -n | tail -3
```
```
97
98
99
```
新迁移为 **V100**。

### `updateLegacyConfig` 现有保留行（主计划 R-12，逐字）
```kotlin
                // M-2: 旧 typed API 不传该字段，必须显式保留现有多值状态（漏写会命中默认值静默重置）。
                operatorStatuses = parseOperatorStatuses(existing.operatorStatusesJson),
                templateId = request.templateId,
                // I4a-6 (M-2): 旧 typed API 不传门禁开关，必须显式保留存量值（漏写会命中默认值静默重置为 false）。
                gateFilterEnabled = existing.gateFilterEnabled
```

### 前端触点规模（主计划 R-13）
```bash
grep -n "gateFilterEnabled" src/main/resources/static/app.js
```
输出 12 行：`13389, 13550, 14281, 14301, 14337, 14419, 14503, 14524, 14555, 14631, 14649, 14659`。

角色分类（实现时须逐一对照，`gateFilterEnabled` 是最近的同款先例）：
`:13389` 列表 pill 渲染 / `:13550` 表单回填 / `:14281,:14419,:14631` 表单收集 /
`:14301,:14337` 提交 payload / `:14503,:14524` 默认值与视图映射 / `:14555` 回填 /
`:14649` 快照 / `:14659` 变更日志的值格式化。

**新配置项须覆盖同样 12 类角色**，其中 `:14659` 的格式化函数需新增分支
（`if (key === "reachabilityFilter") return {...}[value] || "不过滤"`），
否则变更日志显示原始枚举串。

### Interaction points

| # | 写入 | 读取 | 处置 |
|---|------|------|------|
| IP-1 | 配置表单 → `batch_send_task_config` | `resolveScope` → `RecipientScope.reachabilityFilter` | 本计划接线；未接通则配置永不生效 |
| IP-2 | 旧 typed API | 新列 | I-6-1 显式保留 |
| IP-3 | 新列 | `toView()` → 前端回填 | I-6-2 |
| IP-4 | `RecipientScope.reachabilityFilter` | 计划 05 的 4 处筛选 | 计划 05 已实现，本计划只传值 |

## 实现方案

### T1 — V100 迁移（遵 I-6-3、I-6-5）
```sql
ALTER TABLE batch_send_task_config
    ADD COLUMN reachability_filter VARCHAR(32) NULL COMMENT '可达性过滤模式：NULL=不过滤';
```

### T2 — 实体与命令对象（遵 I-6-5）
`BatchSendTaskConfig` 加 `val reachabilityFilter: String? = null`；
`BatchSendTaskConfigUpdateCommand` 同款加字段（**带默认值**，遵 `K-entity-field-default-for-test-constructors`）。

### T3 — 三类映射（遵 I-6-2）
`toView()` 加；三个 `*Fields()` 加；`toLegacyConfig()` 与 `updateLegacyConfig` 返回构造**不加**。

### T4 — 旧 typed 适配器保留行（遵 I-6-1）
在 `gateFilterEnabled = existing.gateFilterEnabled` 之后追加：
```kotlin
                // 同 M-2：旧 typed API 不传可达性过滤，必须显式保留存量值。
                reachabilityFilter = existing.reachabilityFilter
```

### T5 — 校验（遵 I-6-4）
字段校验复用 `ExpertSearchService.ALLOWED_REACHABILITY_MODES`，非法值抛
`IllegalArgumentException`（映射 400）。

### T6 — `resolveScope` 接线（遵 IP-1）
在构造 `RecipientScope` 的位置传入 `reachabilityFilter = config.reachabilityFilter`。
实现前先执行 `grep -rn "RecipientScope(" --include=*.kt src/main/kotlin` 逐一核对每个构造点
是否属于「配置驱动」路径——独立手动运行路径按 O-2 不传值。

### T7 — 前端 12 类角色（遵 N-4）
逐一对照 `gateFilterEnabled` 的 12 处，为 `reachabilityFilter` 补齐同样角色，含
`:14659` 的值格式化分支。控件为 3 选项 select，落位在「邮件模版门禁过滤」之后
（`index.html:1244` 与 `:1441` 两处编辑器各一份 —— 实现前须确认这两处是否为
「编辑器」与「新建」两个弹窗，两处都要加）。

### T8 — 测试
`BatchSendTaskConfigReachabilityTest`：默认值为不过滤、非法值 400、
**旧 typed API 更新任意字段后新列不变**（I-6-1 的核心用例）、`toView` 透出、
`toLegacyConfig` 不含该字段。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `src/main/resources/db/migration/V100__add_reachability_filter_to_batch_send_task_config.sql` | 新增（T1） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | T2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | T2/T3/T4/T5 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | T6 |
| 5 | `src/main/resources/static/index.html` | T7 |
| 6 | `src/main/resources/static/app.js` | T7 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigReachabilityTest.kt` | 新增（T8） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | 补断言 |

文件数 8 ≤ 10。子系统 2（配置持久化 / 前端配置 UI）。新增 ES 字段 0；新增 DB 列 1。

## 样式契约

### S-6-1: 可达性过滤配置控件
- **复用**：与 `index.html:1244` 的「邮件模版门禁过滤」同一 `.batch-config-field-label` +
  控件容器结构；不新增任何 CSS class。
- **DOM 结构**：
```html
<span class="batch-config-field-label">可达性过滤</span>
<select id="batchConfigEditorReachabilityFilter">
    <option value="">不过滤</option>
    <option value="EXCLUDE_BLOCKED">排除已失效</option>
    <option value="HIGH_ONLY">仅高可达</option>
</select>
<span class="batch-gate-hint">已退订与硬退专家不会被发送；「仅高可达」还会排除数据未补充的专家。</span>
```
- **禁止项**：inline style；新增 class；修改 `.batch-config-field-label` / `.batch-gate-hint` 规则块。

### S-6-2: 任务列表配置摘要 pill
- **复用**：`.batch-gate-pill`（`app.js:13389` 使用），**就地复用不派生**。
- **DOM 结构**：`<span class="batch-gate-pill">可达性 · 排除已失效</span>`，
  仅在 `reachabilityFilter` 非空时渲染，位置紧随门禁 pill 之后。
- **既有 class 使用点核对**：
```bash
grep -rn "batch-gate-pill" src/main/resources/static/
```
实现前执行并列出全部使用点，确认本计划为「就地复用」而非修改。

## 验证命令

见主计划「验证命令」节。本计划专属：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigReachabilityTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
```

## 验收标准

- I-6-1：`grep -n "reachabilityFilter = existing.reachabilityFilter" src/main/kotlin/.../BatchSendTaskConfigService.kt` 命中 1 行且位于 `updateLegacyConfig` 函数体内；单测「旧 typed API 更新 cron 后新列不变」通过。
- I-6-2：`grep -n "reachabilityFilter" src/main/kotlin/.../BatchSendTaskConfigService.kt` 的命中出现在 `toView` 与三个 `*Fields()` 中，**不**出现在 `toLegacyConfig` 中。
- I-6-3：`grep -c '\${' src/main/resources/db/migration/V100__*.sql` 输出 0。
- I-6-4：`grep -rn "ALLOWED_REACHABILITY_MODES" --include=*.kt src/main/kotlin` 定义 1 处、引用 ≥2 处；`BatchSendTaskConfigService` 内无自持的可达性字符串集合。
- I-6-5：迁移中列定义为 `NULL`（无 `DEFAULT` 非空值）；单测断言新建配置的 `reachabilityFilter == null`。
- N-3：`git diff` 中 `BatchSendConfig` data class 零改动行。
- S-6-1 / S-6-2：`git diff src/main/resources/static/index.html` 无新增 class 定义、无 inline style；`grep -rn "batch-gate-pill" src/main/resources/static/styles.css` 的规则块零改动。
- 回归：执行主计划「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 配置项保存与回填
- 前置条件: 存在一个介绍邮件的批量任务配置。
- 操作步骤: 1) 打开配置编辑器，将「可达性过滤」设为「排除已失效」。2) 保存。3) 关闭并重新打开编辑器。
- 预期结果: 下拉回填为「排除已失效」；任务列表该行出现 pill `可达性 · 排除已失效`。
- 覆盖: Observable outcome 1 / Observable outcome 3 / IP-3

### A-2: 旧界面改动不重置新配置（I-6-1 核心）
- 前置条件: A-1 已完成，配置为「排除已失效」。
- 操作步骤: 1) 通过旧 typed 界面（或直接 PUT `/api/mail/batch-send/types/INTRODUCTION/config`）只修改 cron。2) 重新打开新版配置编辑器。
- 预期结果: 「可达性过滤」仍为「排除已失效」，**未被重置为「不过滤」**；门禁开关与状态多选也未被重置。
- 覆盖: I-6-1 / N-2

### A-3: 过滤真正影响投放目标
- 前置条件: 候选层中存在已知数量的 BLOCKED 专家（记为 B）。
- 操作步骤: 1) 配置为「不过滤」，记录收件人预估数 N1。2) 改为「排除已失效」，记录 N2。3) 改为「仅高可达」，记录 N3。
- 预期结果: `N2 = N1 - B`（误差应为 0）；`N3 < N2`；三次预估均无报错。
- 覆盖: Observable outcome 2 / IP-1 / IP-4

### A-4: 非法值被拒绝
- 前置条件: 可直接调用配置更新接口。
- 操作步骤: PUT 配置，`reachabilityFilter` 传 `"MEDIUM"`。
- 预期结果: 返回 HTTP 400，响应 message 指出非法取值；配置未被修改。
- 覆盖: I-6-4

### A-5: 回归 —— 升级后存量任务投放范围不变
- 前置条件: 升级前记录一个存量任务的收件人预估数。
- 操作步骤: 1) 执行 V100 迁移并重启。2) 不修改任何配置，重新查看该任务的收件人预估数。
- 预期结果: 与升级前完全一致；该任务的「可达性过滤」显示为「不过滤」。
- 覆盖: I-6-5 / N-1

### A-6: 回归 —— 门禁过滤开关行为不变
- 前置条件: 存在一个开启了「邮件模版门禁过滤」的任务。
- 操作步骤: 1) 查看列表 pill。2) 打开编辑器确认开关状态。3) 保存后再次确认。4) 查看该任务的变更日志。
- 预期结果: 门禁 pill 与开关状态、变更日志文案与改动前一致；两个 pill 并存时不重叠、不换行错位。
- 覆盖: N-4
