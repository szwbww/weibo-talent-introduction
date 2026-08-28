# 子计划 03：研发类型改为必填非空（写侧先行）

> 主计划：[`00-single-gate-master.md`](./00-single-gate-master.md)
> 依赖：无（与 01、02 解耦）
> **必须早于子计划 04 发布**：04 会把「类型集合」变成唯一门禁，届时空集 = 发给零个人。
> 本计划先把所有存量与新建配置填成显式非空，04 上线时才不会出现静默停发。

---

## 需求描述

**Observable outcome**

1. INTRODUCTION 的「研发类型」在定时任务配置与手动发送两条路径上都成为**必填项**：
   空集合保存/启动时被拒绝，并给出明确提示。
2. 存量的 15 条定时任务配置（当前 `expert_types_json` 全部为 `'[]'`）被迁移为显式的
   三类数组 `["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]` ——
   与今天 `SENDABLE_TYPES` 的口径逐字等价，**线上发信人群零变化**。
3. 新建配置与手动发送表单的默认值同样是这三类。

**What must NOT change**

1. 本计划**不删除**任何既有门禁（`expertSendableFilter()` 与三处内存判定原样保留）——
   删除是子计划 04 的事。
2. MATERIAL_REMINDER 的配置校验与启动流程逐字不变（研发类型只对 INTRODUCTION 必填）。
3. 迁移后线上实际发信的人群集合与迁移前**完全一致**（三类 = `SENDABLE_TYPES`，
   老门禁仍在，二者取交集仍是同一批人）。
4. `expertTypesFilter()` 的既有契约不变（空集合返回 `null`）。

**Out of scope**

- 删除 `sendable` / 版本门禁（子计划 04）。
- 删除 `ExpertClassification.sendable` 派生属性与 mapping 声明（子计划 05）。
- 列表页的研发类型筛选（那是查询维度，不是发信门禁，保持"空 = 不限"）。

---

## 关键不变量

### Invariant I3-1: 必填只对 INTRODUCTION 生效
- Rule: 非空校验的条件是 `mailType == BatchSendType.INTRODUCTION.name`。
  MATERIAL_REMINDER 的 `expertTypes` 仍允许为空集合。
- Applies to: `BatchSendTaskConfigService`（配置保存）、`BatchSendControlService.validateSnapshotFields`（手动启动）。
- Violation consequence: 材料提醒任务会被误拒，属于 must-NOT-change 第 2 条违规。
- 来源: original

### Invariant I3-2: 两个写入口都要校验，缺一即漏
- Rule: 非空校验必须同时落在**两处**：
  ① 定时任务配置保存 —— `BatchSendTaskConfigService.kt:299-310` 的既有 `expertTypes` 校验段；
  ② 手动发送启动 —— `BatchSendControlService.validateSnapshotFields():417-438`。
- Applies to: 上述两处。
- Violation consequence: 手动面板直接 POST 快照（`BatchSendConfigController:98` 同款 body），
  不经过配置服务。只改 ① 会留下一条绕过必填的通路，04 上线后该通路等价于"发给零个人"，
  且报错点在运行时而非提交时，极难定位。
- 来源: original（2026-08-28 grep 复核：`BatchExecutionSnapshot` 不落库，
  手动路径的快照来自请求体，只经 `validateSnapshotFields` 一道校验）

### Invariant I3-3: 迁移值必须与今天的 `SENDABLE_TYPES` 逐字等价
- Rule: V109 写入的三个值必须是 `PRODUCTION_RND`、`ACADEMIC_RND`、`HYBRID_RND`，
  与 `ExpertClassification.kt:41-45` 的 `SENDABLE_TYPES`（枚举前三值）一一对应。
- Applies to: `V109__*.sql`。
- Violation consequence: 多一个值 → 04 上线后开始给医学越界/纯服务发信；
  少一个值 → 发信人群缩小。两者都是"迁移改变了线上行为"，违反 must-NOT-change 第 3 条。
- 来源: original

### Invariant I3-4: 迁移只覆盖当前为空的行
- Rule: V109 的 `UPDATE` 必须带 `WHERE expert_types_json IS NULL
  OR expert_types_json = '' OR expert_types_json = '[]'`，
  不得无条件覆盖所有行。
- Applies to: `V109__*.sql`。
- Violation consequence: 若运营在本计划发布前已手工勾选过某个配置，
  无条件覆盖会静默抹掉他的选择（[[K-qa-rule-runtime-vs-migration-writes]]：
  迁移不得覆盖运营运行时改动）。
- 来源: K-qa-rule-runtime-vs-migration-writes

### Invariant I3-5: 迁移文件不得含 `${...}`
- Rule: V109 的 SQL 正文中不得出现 `${`。
- Applies to: `V109__*.sql`。
- Violation consequence: 虽然 `application.yml:8-13` 已显式设 `placeholder-replacement: false`，
  但该配置是"必须维持的约束"而非永久保证；本计划无需占位符，直接避开即可。
- 来源: K-flyway-placeholder-replacement

### Invariant I3-6: 白名单仍是唯一权威
- Rule: 前端默认值与后端校验都不得手写六值名单；取值合法性仍由
  `ExpertSearchService.ALLOWED_EXPERT_TYPES:115-116`（从 `ExpertType.values()` 派生）判定。
  前端默认值是三个字面量常量，必须与 `batchExpertTypeOptions()`（`app.js:14532-14543`）
  中的 value 逐字一致。
- Applies to: `BatchSendTaskConfigService`、`app.js`。
- 来源: K-（沿用 2026-08-25 子计划 02 的 I2-1 / M-2）

---

## 样式契约

### S3-1: 研发类型选择器（不改结构，只改默认值与错误提示）
- **复用**：`batch-tag-picker` 整套既有结构与样式，逐字不动。
  编辑器：`index.html:1269-1279`；手动面板：`index.html:1478-1488`。
  标签 `class="batch-config-field-label"`（`styles.css:8981-8987`：`font-size:12px`、
  `font-weight:600`、`color:var(--text-sidebar)`、`margin-bottom:6px`）。
- **新增**：无。本计划**不新增任何 CSS 规则、不新增任何 class、不改动任何 DOM 结构**。
- **错误提示**：复用既有 `showStatus(<msg>, "error")` 通道（与
  `app.js:15048` 的 `showStatus("每轮数量需 ≥ 1", "error")` 同款），文案逐字为：

```javascript
        showStatus("请至少选择一个研发类型", "error");
```

- **禁止项**：inline style；新增 class；修改 `.batch-tag-picker*` / `.batch-config-field-label`
  的任何规则块；把提示做成新的 DOM 节点（必须走 `showStatus`）。

---

## 现状审计

### `batch_send_task_config` 表

- Schema：`expert_types_json TEXT NOT NULL`，由 `V108__add_expert_types_to_batch_send_task_config.sql`
  添加（`AFTER operator_statuses_json`），同一迁移里 `UPDATE ... SET expert_types_json = '[]'`。
  **当前线上全部为 `'[]'`。**
  TEXT 列不能带 `DEFAULT`，故照 V98/V93 的两步范式（[[K-batch-multi-value-filter-seams]]）。
- **写路径**
  1. `BatchSendTaskConfigService.kt:299-310` — 唯一规范化/校验点。现有校验：
     `trim` → 丢空 → `distinct` → 逐项 `require(it in ALLOWED_EXPERT_TYPES)` → 逐项禁逗号。
     **无非空校验。**
  2. `BatchSendTaskConfigService.kt:333`（新建）/ `:76`、`:111`（更新）写 `expertTypesJson`。
  3. `V108` 迁移（历史）。
- **读路径**
  1. `BatchSendTaskConfigService.parseExpertTypes(row.expertTypesJson)` — `:197`、`:479`。
  2. → `BatchExecutionSnapshot.expertTypes` → `RecipientScope.expertTypes`
     （`BatchExecutionModels.kt:154`，注释写「空集合 = 不限」）。
  3. → ES 侧 `ManualInitialOutreachService.buildEsFiltersForLevel:1327`
     （`expertTypesFilter` 返回 null 时不追加）。
  4. → 内存侧 `BatchExecutionModels.kt:83-91`（`expertTypes.isNotEmpty()` 才判定）。
- **Interaction point**：写路径 1（配置保存，本计划加非空）→ 读路径 3/4
  （子计划 04 会把"空 = 不限"翻转成"空 = 谁都不发"）。本计划**只补写侧**，读侧语义留到 04，
  因此两次发布之间线上行为完全不变。

### 手动发送路径（第二个写入口）

- `BatchSendConfigController.kt:98` `previewRecipients(@RequestBody snapshot: BatchExecutionSnapshot)`
  —— 预估直接吃请求体。
- 启动路径经 `BatchSendControlService.validateSnapshotFields():417-438`，
  现校验 `roundSize` / `roundsPerRun` / `perMailIntervalMs` / `perRoundIntervalMs`
  / `selfCheckTtlMinutes` / `funnelLevel` / `regions`。**无 expertTypes 校验。**
- `grep -rn "BatchExecutionSnapshot" src/main/kotlin | grep -i "json\|repository\|entity\|column"`
  只命中上面两处，**快照不落库** —— 因此不存在"存量快照"需要迁移，
  只需保证新提交的快照合法。

### 前端

- 选项表：`app.js:14532-14543` `batchExpertTypeOptions()`，七项（六枚举 + `UNCLASSIFIED`），
  label 与列表页 chip 文案同源。
- 默认值当前为空：`fillManualFormDefaults()`（`app.js:15128` `expertTypes: []`）；
  编辑器新建走 `setBatchMultiPickerValue("batchConfigEditorExpertTypes", ... : [])`（`:14115`）。
- 提交前校验：编辑器 `app.js:15046-15049` 一段 `if (payload.xxx < 1) { showStatus(...); return; }`，
  本计划在同一段落追加一条。

### 前端样式盘点

- 可复用 class：`.batch-config-field-label`（`styles.css:8981-8987`）、
  `.batch-tag-picker` / `-control` / `-chips` / `-search` / `-chevron` / `-dropdown`（既有整套）。
- 设计基准 token：label `12px / 600 / var(--text-sidebar) / margin-bottom 6px`；
  提示文案走 `showStatus`，不使用 `.batch-gate-hint`（`styles.css:9381-9385`）。
- DOM 约定：多选选择器一律 `div.batch-tag-picker[data-tag-picker="<id>"]` +
  隐藏 `input#<id>`，值以逗号分隔（[[K-batch-picker-comma-delimited-contract]]）。
- 改动前基线：`index.html:1269-1279`（编辑器）、`index.html:1478-1488`（手动面板），
  本计划**一行不改**。

---

## 实现方案

### Task 1：配置保存加非空校验（I3-1、I3-2、I3-6）

`BatchSendTaskConfigService.kt`，在既有 `expertTypes` 校验段（`:299-310`）之后追加。
`mailType` 在同一函数内已由 `resolveMailType(fields.templateId)` 得到（`:315` 附近），
需确保校验点在其之后；若顺序不允许，则把非空校验移到 `mailType` 计算之后、
`expertTypesJson` 序列化之前：

```kotlin
// I3-1/I3-2: INTRODUCTION 的研发类型必填非空 —— 空集合在子计划 04 之后
// 等价于「发给零个人」，必须在保存时就拒绝，不能留到运行时。
if (mailType == BatchSendType.INTRODUCTION) {
    require(expertTypes.isNotEmpty()) { "研发类型至少选择一个" }
}
```

### Task 2：手动启动加非空校验（I3-1、I3-2）

`BatchSendControlService.validateSnapshotFields()`（`:417-438`），在 `regions` 校验之后追加：

```kotlin
            // I3-2: 手动路径的快照直接来自请求体，不经配置服务，必须在此独立校验。
            if (snapshot.mailType == BatchSendType.INTRODUCTION.name) {
                require(snapshot.expertTypes.any { it.isNotBlank() }) { "研发类型至少选择一个" }
            }
```

沿用该函数既有的 `IllegalArgumentException` → `422 UNPROCESSABLE_ENTITY` 通道，不新增分支。

### Task 3：V109 存量迁移（I3-3、I3-4、I3-5）

新建 `src/main/resources/db/migration/V109__require_expert_types_on_batch_send_task_config.sql`：

```sql
-- I3-3: 三个值与 ExpertClassification.SENDABLE_TYPES（枚举前三值）逐字等价，
--       迁移后线上发信人群零变化。
-- I3-4: 只覆盖当前为空的行，不抹掉运营已手工勾选的配置。
UPDATE batch_send_task_config
SET expert_types_json = '["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]'
WHERE expert_types_json IS NULL
   OR expert_types_json = ''
   OR expert_types_json = '[]';
```

### Task 4：前端默认值与提示（S3-1、I3-6）

- `app.js:15128` `fillManualFormDefaults()` 的 `expertTypes: []`
  改为 `expertTypes: ["PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"]`。
- `app.js:14115` 编辑器回填：新建配置（`config` 为 null）时用同一组默认值；
  编辑既有配置时仍用 `config.expertTypes`，逐字不变。
- 编辑器提交前校验（`app.js:15046-15049` 同段落）追加，文案按 S3-1 逐字：

```javascript
    if (readBatchMultiPickerValue("batchConfigEditorExpertTypes").length === 0) {
        showStatus("请至少选择一个研发类型", "error"); return;
    }
```

### Task 5：测试

`BatchSendTaskConfigServiceTest`：
1. INTRODUCTION + 空 `expertTypes` → 保存抛 `IllegalArgumentException`，消息含「研发类型至少选择一个」。
2. MATERIAL_REMINDER + 空 `expertTypes` → 保存成功（I3-1 回归）。
3. INTRODUCTION + 非空 → 保存成功，`expertTypesJson` 逐字为传入值的 JSON。

`BatchSendControlServiceTest`（若无该类则新建）：
4. INTRODUCTION 快照 + 空 `expertTypes` → 返回 422，body 含该消息。
5. MATERIAL_REMINDER 快照 + 空 → 不因该项被拒（I3-1 回归）。

迁移文本断言（照 `QaSeedEncodingRepairMigrationTest` 范式，
不需要 Docker，可进全量 `mvn test`，见 [[K-flyway-placeholder-replacement]]）：
6. 读 `V109__*.sql` 文本，断言：含 `["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]`（I3-3）；
   含 `WHERE` 且含 `'[]'`（I3-4）；**不含** `${`（I3-5）。

---

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/.../campaign/service/BatchSendTaskConfigService.kt` | Task 1 |
| 2 | `src/main/kotlin/.../campaign/service/BatchSendControlService.kt` | Task 2 |
| 3 | `src/main/resources/db/migration/V109__require_expert_types_on_batch_send_task_config.sql` | 新建 |
| 4 | `src/main/resources/static/app.js` | Task 4 |
| 5 | `src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt` | Task 5 第 1~3 条 |
| 6 | `src/test/kotlin/.../campaign/service/BatchSendControlServiceTest.kt` | Task 5 第 4~5 条（无则新建） |
| 7 | `src/test/kotlin/.../campaign/repository/V109ExpertTypesMigrationTest.kt` | Task 5 第 6 条（新建） |

合计 7 个文件；子系统 2 个（campaign 后端 / 前端静态资源）。

---

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,V109ExpertTypesMigrationTest'

# 前端语法与用例
node --check src/main/resources/static/app.js
node --test src/test/js/*.test.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：Maven 输出 `Tests run: N, Failures: 0, Errors: 0` 且 `BUILD SUCCESS`；
`node --test` 退出码 0 且输出含 `# fail 0`；`git diff --check` 无输出。
来源：`CLAUDE.md:5-20`「Commands」章节 + [[K-js-tests-run-via-exec-plugin]]。

---

## 验收标准

- **I3-1**：单测断言 MATERIAL_REMINDER 空集合仍可保存/启动。
- **I3-2**：`grep -rn "研发类型至少选择一个" src/main/kotlin` 恰好命中**两个文件**
  （`BatchSendTaskConfigService.kt`、`BatchSendControlService.kt`）。
- **I3-3**：迁移文本断言含 `["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]`；
  且 `grep -n "SENDABLE_TYPES" -A 6 src/main/kotlin/.../ExpertClassification.kt`
  显示该集合仍是枚举前三值（人工核对两者一致）。
- **I3-4**：迁移文本断言含 `WHERE` 且含 `'[]'`。
- **I3-5**：迁移文本断言不含 `${`。
- **I3-6**：`git diff` 显示未新增任何硬编码六值名单；前端三个默认值与
  `batchExpertTypeOptions()` 的 value 逐字一致。
- **S3-1**：`git diff src/main/resources/static/styles.css` 与
  `git diff src/main/resources/static/index.html` 均为空；
  `app.js` 的 diff 中不含 `style=` 与新 class 名。
- 回归：执行「验证命令」节的全量测试命令与构建命令通过。

---

## 人工验收清单

### A3-1: 定时任务配置不能保存空的研发类型
- 前置条件: 已登录管理后台，进入「批量邮件任务控制台 → 定时任务配置」。
- 操作步骤: 1. 新建一个 INTRODUCTION 配置；2. 把「研发类型」的所有 chip 删空；3. 点保存。
- 预期结果: 保存被拒绝，页面顶部出现红色提示 **「请至少选择一个研发类型」**；配置未被写入。
- 覆盖: I3-2、需求描述第 1 条

### A3-2: 新建配置默认已勾三类
- 前置条件: 同上。
- 操作步骤: 点「新建配置」，不做任何操作，观察「研发类型」字段。
- 预期结果: 已有三个 chip：**生产研发、学术科研、混合研发**；没有「纯服务」「医学越界」「未知」「未分类」。
- 覆盖: 需求描述第 3 条、I3-6

### A3-3: 手动发送同样拒绝空集合
- 前置条件: 进入「手动发送」面板，邮件类型选 INTRODUCTION。
- 操作步骤: 1. 清空「研发类型」；2. 点启动。
- 预期结果: 启动被拒，返回 422，页面提示「请至少选择一个研发类型」。
  **注意：这条必须单独验，它走的不是配置保存那条通路。**
- 覆盖: I3-2

### A3-4: 材料提醒不受影响（回归）
- 前置条件: 手动发送面板，邮件类型选 MATERIAL_REMINDER。
- 操作步骤: 1. 清空「研发类型」；2. 点启动。
- 预期结果: **正常启动**，不出现研发类型相关的报错。
- 覆盖: I3-1、must-NOT-change 第 2 条

### A3-5: 迁移后发信人群零变化（回归，本计划最关键的一条）
- 前置条件: 记录迁移**前**某个定时任务配置的收件人预估命中数（页面上「当前条件命中 N 位专家」）。
- 操作步骤: 1. 发布本计划并确认 V109 已应用；2. 打开同一个配置，不改任何字段；3. 读预估命中数。
- 预期结果: **两次数字逐字相同**。三类 = 老门禁的 `SENDABLE_TYPES`，
  二者取交集仍是同一批人。若数字变化，说明 V109 写入的值与 `SENDABLE_TYPES` 不一致，
  立即回滚并核对 I3-3。
- 覆盖: I3-3、must-NOT-change 第 3 条

### A3-6: 运营已手工勾选的配置不被抹掉（回归）
- 前置条件: 在发布前，手工把某个配置的研发类型改成只勾「学术科研」并保存
  （此时 `expert_types_json` 为 `["ACADEMIC_RND"]`）。
- 操作步骤: 发布本计划后重新打开该配置。
- 预期结果: 仍然只勾着「学术科研」，**没有**被改成三类。
- 覆盖: I3-4
