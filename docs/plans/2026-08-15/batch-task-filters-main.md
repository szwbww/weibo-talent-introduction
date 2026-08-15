# 主计划：批量邮件任务筛选能力增强（门禁开关 / 邮箱多选 / 状态多选 / cron 回显修复）

> 本文件**不含代码改动**。它只定义子计划边界、执行顺序、跨计划共享的不变量与验证命令。
> 每个子计划独立可部署、独立验证。

创建日期：2026-08-15
创建者：create-p

---

## 需求描述

### Observable outcomes

1. **O-1** 批量邮件任务（定时任务编辑器 + 手动执行）新增「邮件模版门禁过滤」开关；开启后收件范围只包含满足该模板 `required_keys` 的专家，收件预估显示被门禁排除的人数与该模板必填字段。
2. **O-2** 「邮箱服务商」筛选从单选改为多选（定时 + 手动），多个服务商之间取 **或**。
3. **O-3** 「专家状态」筛选从单选改为多选（定时 + 手动），多个状态之间取 **或**。
4. **O-4** 打开一个 cron 为 `0 0 9-17 * * ?` 的定时任务，「执行频率」显示「自定义 cron」且表达式原样回填；保存后 cron 不变。

### What must NOT change

- **N-1** 专家列表页（`#expertEmailDomainFilter` / `#contactStatusFilter`）的单选筛选行为与接口参数不变 —— 本轮只改批量任务控制台。
- **N-2** `ExpertSearchService.searchExperts / aggregateTags / aggregateRegions / aggregateEmailDomains` 的既有单值 `operatorStatus` / `emailDomain` 形参语义与调用方不变。
- **N-3** 标签、地区两个既有多选筛选器（`batchConfigEditorTags` / `batchConfigEditorRegions` / `batchManualTags` / `batchManualRegions`）的行为、DOM、样式不变。
- **N-4** 手动执行「已修改」标红（`.is-config-diff` + `.batch-config-diff-badge` + `.batch-config-diff-original`）的既有 7 个字段行为不变。
- **N-5** 收件预估的零副作用契约不变（不建 `task_execution` / `expert_contact` / campaign，见 `K-recipient-count-preview-parity`）。
- **N-6** 定时任务「自定义 cron」保存时直接提交输入框原串，不重新按「每天+时间」拼装（已有回归测试 `batchSendTaskConsoleInteraction.test.js:466`）。

### Out of scope（本轮明确不做）

- 专家列表页「按模板门禁」筛选器（`#expertGateTemplateFilter`）的任何改动 —— 它已存在且工作正常。
- `mail_compose_template.required_keys` 的 seed / 回填迁移 —— 当前全库为 NULL（= 门禁关闭）是既定事实（见 `intro-mail-fallback-renders-as-title`），本轮只做筛选，不改门禁启用状态。
- 发送路径门禁（`IntroductionMailComposer.kt:28-29`、`ManualExpertMailService.kt:230-232`）的任何改动。
- 「学科」「漏斗层级」改多选。
- 旧 KV 兼容层 `batch_send_setting` 的任何语义扩展（见 `K-batch-send-setting-kv`）。
- 删除本轮发现的死代码 `ManualInitialOutreachService.buildMaterialReminderEsFilters`（见下方 X-1）—— 记为观察项，不在任何子计划的任务里。

---

## 子计划与执行顺序

每个维度都拆成「后端 a / 前端 b」两份，原因见下方「为什么拆到 7 份」。

| 序 | 子计划 | 文件数 | 子系统数 | 迁移版本 | 依赖 |
|---|---|---|---|---|---|
| 1 | `p1-cron-echo-whitelist.md` | 2 | 1（前端） | — | 无 |
| 2 | `p2a-email-domain-multi-backend.md` | 10 | 2（campaign / expert） | **V97** | 无 |
| 3 | `p2b-email-domain-multi-frontend.md` | 3 | 1（前端） | — | P2a |
| 4 | `p3a-operator-status-multi-backend.md` | 10 | 2 | **V98** | P2a |
| 5 | `p3b-operator-status-multi-frontend.md` | 3 | 1（前端） | — | P2b + P3a |
| 6 | `p4a-template-gate-filter-backend.md` | 10 | 2 | **V99** | P3a |
| 7 | `p4b-template-gate-filter-frontend.md` | 4 | 1（前端） | — | P3b + P4a |

建议合并顺序即上表顺序。P1 与其余无耦合，可最先单独合并上线。

### 为什么拆到 7 份

一次把某个维度的前后端放进同一计划会超过 10 文件上限：以邮箱域为例，后端 7 个改动文件 + 3 个测试文件 + 前端 3 个 = 13。前后端切开后各自 10 / 3，且各自可独立部署验证（后端阶段用 curl 打接口验收，前端阶段用 UI 验收）。

### 为什么必须串行（不是保守，是有具体冲突点）

- **P3b 依赖 P2b**：P2b 建立通用多选 picker 基座 `renderBatchMultiPicker` 系列函数与 `BATCH_MULTI_PICKER_REGISTRY`（P2b 的 I2b-1）。P3b 只往 registry 注册一项，不再重复实现。若并行，两份 picker 实现必然重复。
- **P3a 依赖 P2a**：两者都改 `buildEsFiltersForLevel` 的**同一个函数体**（`ManualInitialOutreachService.kt:1245-1274`），并行必冲突；且 P3a 的基座切换判据要建立在 P2a 已改成 `emailDomainsFilter` 的形态之上。
- **P4a 依赖 P3a**：P4a 在 `buildEsFiltersForLevel` 末尾追加门禁 filter，需要 P3a 完成后的稳定形态。
- **P4b 依赖 P3b + P4a**：P4b 的双请求预估要把 P2/P3 的多值字段一起带进 snapshot。
- **迁移版本必须依序占用**：V97 → V98 → V99。乱序会产生版本号冲突，且 `FlywayMigrationIntegrationTest` 会红。
- **四个前端计划都改 `app.js` 的同一批函数**（`showBatchConfigEditor` / `readManualFormValues` / `computeManualDiffs` / `computeAndRenderDiffs` / `buildConfigEditorRecipientSnapshot` / `renderBatchConfigRow`），并行必冲突。

---

## 跨计划共享不变量

以下不变量对 P2 / P3 / P4 **全部生效**，子计划中以 `M-n` 引用，不重复展开。

### Invariant M-1: 筛选维度必须同时覆盖两条活体目标来源

- Rule: 批量发送有且只有 **两条**目标来源，新增/改造任一筛选维度必须同时接入：
  1. **ES 新目标** —— `ManualInitialOutreachService.buildEsFiltersForLevel(scope, level)`（`ManualInitialOutreachService.kt:1245`），被 3 处调用：`:1160`（材料提醒目标）、`:1214`（`countEsTargets`，预估）、`:1226`（`fetchEsPage`，发送）。
  2. **MySQL 重试联系人** —— `RecipientScope.matchesExpert(profile)`（`BatchExecutionModels.kt:57`）。
- Applies to: P2 的 `emailDomains`、P3 的 `operatorStatuses`、P4 的 `gateEsFields`。
- Violation consequence: 只接 ES 会让重试联系人静默绕过配置并**错发**（`K-batch-send-filter-retry-parity`，severity P1，hit_count 8）。
- 来源: K-batch-send-filter-retry-parity（已用 grep 复核，见 X-1 对旁路数量的更正）

### Invariant M-2: 新增列必须在 `updateLegacyConfig` 显式保留

- Rule: `batch_send_task_config` 每新增一列，必须在 `BatchSendTaskConfigService.updateLegacyConfig()` 中显式写 `newField = existing.newField`。
- Applies to: P2 / P3 / P4 各自新增的列。
- Violation consequence: 旧 typed API（`/api/mail/batch-send/types/{sendType}/config`）传入只含旧字段的 `BatchSendConfigUpdateRequest`，却调用全量 `update(...)`；漏写会命中 `BatchSendTaskConfigUpdateCommand` 的 Kotlin 默认值，把存量配置**静默重置**，无任何报错。
- 来源: K-batch-config-legacy-adapter-field-preservation

### Invariant M-3: 配置实体链路的映射点全集

- Rule: 新增/改造一个配置列，必须同步以下位置，一个不落：
  - `campaign/domain/BatchSendTaskConfig.kt` 的 **4 个 data class**：`BatchSendTaskConfig`（`:8`）、`BatchSendTaskConfigView`（`:34`）、`BatchSendTaskConfigCreateCommand`（`:59`）、`BatchSendTaskConfigUpdateCommand`（`:76`）
  - `BatchSendTaskConfigService.kt`：`create` 组装（`:72` 附近）、`update` 组装（`:105` 附近）、`toView()`（`:397` 附近）、`ConfigFields`（`:478`）、`NormalizedConfig`（`:494`）、3 个 `*Fields()`（`:512` / `:530` / `:548` 附近）、`normalizeAndValidate`（`:255-295`）
  - `campaign/domain/BatchExecutionModels.kt`：`BatchExecutionSnapshot`（`:8`）、`RecipientScope`（`:48`）、`RecipientScope.fromSnapshot`（`:93`）、`toExecutionSnapshot`（`:235` 附近）
  - **不加** `toLegacyConfig()`（`:208` 附近）与 `updateLegacyConfig` 的返回值构造 —— 不把 KV 兼容层拖进变更范围
- Applies to: P2 / P3 / P4。
- Violation consequence: 漏 `toView()` → 前端读不到；漏 `*Fields()` → 绕过校验；漏 `fromSnapshot` → 发送路径拿不到值。
- 来源: K-recipient-scope-status-filter（映射点清单已用本轮 grep 逐行复核，行号为 2026-08-15 实测）

### Invariant M-4: 预估与执行同源

- Rule: 收件预估必须复用执行路径的同一套目标计算 —— `RecipientScope.fromSnapshot` + `buildRetryableTargets` + `countEsTargets`；入参必须是执行快照 `BatchExecutionSnapshot` 本身，不另设预估 DTO。
- Applies to: P2 / P3 / P4 对 `countBySnapshot`（`ManualInitialOutreachService.kt:423`）与 `POST /api/mail/batch-send/recipients/preview`（`BatchSendConfigController.kt:97`）的任何触碰。
- Violation consequence: 预估与实发数漂移 → 运营低估则超发、高估则漏发。
- 来源: K-recipient-count-preview-parity

### Invariant M-5: `OperatorStatusWriteSeamGuardTest` 噪声边界

- Rule: `src/test/kotlin/.../campaign/OperatorStatusWriteSeamGuardTest.kt` 扫描 `src/main/kotlin` 中所有 `operatorStatus = ` 行做白名单闭包。**配置列映射**（`toView` / `toFields` / `updateLegacyConfig` / `normalizeAndValidate` 的命名参数）不是 `expert_contact` 表写入，属该守卫的 DTO 噪声。
- Applies to: P3（改 `operatorStatus` → `operatorStatuses` 时该守卫可能红/绿翻转）。
- Violation consequence: 执行 agent 自行修改守卫的白名单逻辑 → 真正的写入点漏网。
- 处置: 守卫若因本轮映射行变化而失败，必须 **HUMAN 授权**把对应 `path:line:context` 登记进 `EXCLUDED_NOISE_SITES`；**不得**自行改守卫判定逻辑。
- 来源: K-recipient-scope-status-filter

---

## 现状审计（跨计划共享部分）

### X-1 ⚠️ 既有知识条目 `K-recipient-scope-status-filter` 已过期，本计划予以更正

该条目声称批量发送筛选有 **3 条 ES 查询旁路**，第 3 条是 `ManualInitialOutreachService.buildMaterialReminderEsFilters()`。

**本轮 grep 证明它是死代码，零调用方：**

```
$ grep -rn "buildMaterialReminderEsFilters" src/
src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt:1102:    private fun buildMaterialReminderEsFilters(
src/main/kotlin/.../expert/service/ExpertSearchService.kt:141:         * （buildEsFiltersForLevel / buildMaterialReminderEsFilters / matchesExpert）共用此实现，
```

命中两行：`:1102` 是**定义行**，`ExpertSearchService.kt:141` 是**文档注释**。无任何调用。

材料提醒的真实取数路径是 `buildMaterialReminderSnapshotFromScope`（`:1152`），它在 `:1160` 调用的是 **`buildEsFiltersForLevel(scope, level)`**，与 INTRODUCTION 路径同一个函数：

```kotlin
// ManualInitialOutreachService.kt:1158-1161
for (level in scope.funnelLevels) {
    val filters = buildEsFiltersForLevel(scope, level)
    val levelHits = expertSearchService.countExperts(ExpertIndexLevel.valueOf(level), filters)
```

**结论（M-1 的依据）：活体 ES 旁路只有 1 条 `buildEsFiltersForLevel`（3 个调用点），加上 DB 重试路径 `matchesExpert`，共 2 条来源。**

处置：
- 各子计划按 **2 条**来源做接入与验收，不按 3 条。
- 死代码 `buildMaterialReminderEsFilters` 本轮**不删**（Out of scope），仅记为观察项 —— 它是一颗定时炸弹：将来有人"照着它改"会改到一个永不执行的分支。
- Phase 6 已更正知识条目，见本计划末「知识回写」。

### X-2 `batch_send_task_config` 表结构（迁移逐条核对）

| 迁移 | 内容 |
|---|---|
| `V72__create_batch_send_task_config.sql` | 建表。`email_domain VARCHAR(120) NULL`、`discipline VARCHAR(120) NULL`、`tags_json TEXT NOT NULL`、`funnel_level VARCHAR(32) NULL`；含 `active_config_name` 生成列做活跃名唯一 |
| `V73` | `task_execution` 加 `batch_config_id` |
| `V74` | 字符集修复 |
| `V91` | 加 `rounds_per_run INT NOT NULL DEFAULT 1` |
| `V92` | **删** `daily_cap` 列（本仓允许在迁移中 DROP 列的先例） |
| `V93` | 加 `regions_json TEXT NOT NULL AFTER funnel_level` + `UPDATE ... SET regions_json='[]' WHERE regions_json=''`（**TEXT 不能带 DEFAULT，故用后续 UPDATE 兜底 —— 新增 JSON 列必须照抄这个两步范式**） |
| `V94` | 回填 `expert_contact.operator_status`（手动发送历史；幂等，前置 NOT_CONTACTED） |
| `V95` | 加 `operator_status VARCHAR(32) NULL AFTER discipline`；注释明确 NULL = 不限 |

**下一个可用版本号：`V97`。** 已核对 `src/main/resources/db/migration/` 最高为 V96（`V96__add_name_to_reply_snippet.sql`，来自 expert-mail-preview 运行并已合并进 main —— 本审计的版本号已过期）。P2a / P3a / P4a 依序占用 **V97 / V98 / V99**。

⚠️ **迁移写法强制约束（来源: K-flyway-placeholder-replacement）**：生产 `application.yml` 未关 `placeholder-replacement`（默认 true）。本轮三个子计划的迁移**均不得包含 `${...}` 字面量**。若确有需要，必须同提交加 `spring.flyway.placeholder-replacement: false` 并加配置回归断言。

### X-3 前端样式盘点（P2/P3/P4 共享）

#### 可复用 class（`src/main/resources/static/styles.css`）

| class | 行号 | 用途 |
|---|---|---|
| `.batch-tag-picker` | 8915 | 多选 picker 外壳（relative 定位容器） |
| `.batch-tag-picker-control` | 8920 | 输入区（flex wrap，min-height 42px，padding 6px 36px 6px 10px） |
| `.batch-tag-picker:focus-within .batch-tag-picker-control` | 8933 | 聚焦态边框 + 3px 光晕 |
| `.batch-tag-picker-chips` | 8938 | 已选 chip 容器 |
| `.batch-tag-picker-chip` | 8942 | 单个已选 chip |
| `.batch-tag-picker-chip button` | 8956 | chip 上的 × 移除按钮 |
| `.batch-tag-picker-search` | 8966 | 内嵌搜索输入 |
| `.batch-tag-picker-chevron` | 8977 | 右侧 ⌄ |
| `.batch-tag-picker-dropdown` | 8985 | 下拉面板 |
| `.batch-tag-picker-option` | 8999 | 下拉选项 |
| `.batch-tag-picker-option:hover, .is-selected` | 9013 | 选项 hover / 选中态 |
| `.batch-tag-picker-check` | 9019 | 选项左侧 ✓ 位 |
| `.batch-tag-picker-option.is-selected .batch-tag-picker-check` | 9031 | 选中态 ✓ |
| `.batch-tag-picker-empty` | 9036 | 空态提示 |
| `.batch-config-field` | 8873 | 字段卡片（relative + 1px 边框 + panel-bg） |
| `.batch-config-field.is-config-diff` | 8881 | 手动执行「已修改」红框（border `--error`，bg `#fff7f8`） |
| `.batch-manual-section .batch-config-field` | 8887 | 手动面板下去边框变体 |
| `.batch-manual-section .batch-config-field.is-config-diff` | 8894 | 手动面板下的红框（padding 10px + 1px error 边框） |
| `.batch-config-diff-badge` | 8904 | 「已修改」徽标（absolute top 10 right 10，`--error-strong`，700） |
| `.batch-config-diff-original` | 8913 | 「原配置：xxx」行 |
| `.batch-config-field-label` | 8676 | 字段标题（12px / 600 / `--text-sidebar`） |
| `.batch-config-editor-hint` | 8602 | 收件预估提示条（`rgba(37,99,235,.06)` 底、12px、line-height 1.6） |
| `.batch-config-editor-hint strong` | 8612 | 提示条内高亮数字（`--primary` / 600） |
| `.batch-task-status-toggle` | 8699 | 开关外壳（inline-flex column，11px/500） |
| `.batch-task-status-switch` | 8723 | 开关轨道 36×20 圆角 999 |
| `.batch-task-status-switch::after` | 8733 | 开关滑块 16×16 |
| `.batch-task-status-toggle input:checked + .batch-task-status-switch` | 8746 | 开启态轨道变 `--primary` |
| `.batch-task-status-toggle input:checked + .batch-task-status-switch::after` | 8750 | 滑块 translateX(16px) |
| `.batch-task-status-toggle input:focus-visible + ...` | 8754 | 键盘聚焦光晕 |
| `.batch-task-status-toggle input:checked ~ .batch-task-status-label` | 8758 | 开启态文字变 `--primary` |
| `.tag-chip` / `:hover` / `.active` | 583 / 602 / 608 | 只读字段徽标（P4 用） |
| `.batch-task-scope-line` | 8520 | 任务列表「收件范围」列的换行块 |
| `.batch-task-scope-line + .batch-task-scope-line` | 8521 | 第二行起 margin-top 3px + `--text-muted` |
| `.bsc-input` / `.bsc-select` | 5205 / 5216 | 输入框 / 下拉基座 |

#### 设计基准 token 实值（`styles.css:3-51`）

```
--primary: #1e40af          --primary-rgb: 30, 64, 175
--primary-light: rgba(var(--primary-rgb), 0.07)
--text-main: #1e293b        --text-secondary: #475569
--text-sidebar: #64748b     --text-muted: #94a3b8
--error: #e11d48            --error-strong: #be123c
--warning: #d97706          --warning-strong: #b45309
--success: #059669
```

#### DOM 结构约定 —— 既有多选 picker 骨架（`index.html:1176-1186`，地区，逐字）

```html
<div class="batch-config-field">
    <span class="batch-config-field-label">地区</span>
    <div class="batch-tag-picker" data-tag-picker="batchConfigEditorRegions">
        <div class="batch-tag-picker-control">
            <div id="batchConfigEditorRegionsChips" class="batch-tag-picker-chips"></div>
            <input type="search" id="batchConfigEditorRegionsSearch" class="batch-tag-picker-search" placeholder="搜索并选择地区" autocomplete="off" aria-controls="batchConfigEditorRegionsDropdown" aria-expanded="false">
            <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
        </div>
        <input type="hidden" id="batchConfigEditorRegions" value="">
        <div id="batchConfigEditorRegionsDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
    </div>
</div>
```

**命名契约（`readBatchRegionPickerValue` / `renderBatchRegionPicker` 依赖，`app.js:13773-13808`）**：
`<valueId>` 是隐藏 input 的 id；同族元素固定为 `<valueId>Chips` / `<valueId>Search` / `<valueId>Dropdown`；
外壳带 `data-tag-picker="<valueId>"`；**值以逗号分隔存在隐藏 input 的 `value` 里**：

```js
// app.js:13773-13776
function readBatchRegionPickerValue(valueId) {
    var input = document.getElementById(valueId);
    return String(input ? input.value : "").split(",").map(function(v) { return v.trim(); }).filter(Boolean);
}
```

⚠️ **逗号分隔的直接后果**：选项 value 自身**不得含逗号**。邮箱域名与 `OperatorStatus` 枚举名均不含逗号，满足；P2/P3 的校验须显式断言这一点。

#### 手动面板字段的既有骨架（`index.html:1368-1375`，逐字）

```html
<label class="batch-config-field" id="manualFieldEmailDomain">
    <span class="batch-config-field-label">邮箱服务商</span>
    <select id="batchManualEmailDomain" class="bsc-input bsc-select">
        <option value="">全部服务商</option>
    </select>
    <span class="batch-config-diff-badge" hidden>已修改</span>
    <div class="batch-config-diff-original" hidden></div>
</label>
```

⚠️ 改成 picker 后**外层必须由 `<label>` 改为 `<div>`** —— `<label>` 包裹多选 picker 时，点击 chip / 选项会触发 label 的隐式聚焦转移，导致下拉立刻收起。既有的 `manualFieldTags` / `manualFieldRegions` 正是 `<div class="batch-config-field">`（`index.html:1391` / `index.html:1405`），照抄它们。

---

## 验证命令

> 本项目**必须**用 JDK 11（zulu-11）。裸 `mvn` 会构建失败。
> JS 测试挂在 Maven `test` 阶段的 `exec-maven-plugin`（`pom.xml:184-201`），执行 `node --test src/test/js/*.test.js`；也可脱离 Maven 单跑。

```bash
# 全量测试（回归门禁，含 Kotlin + JS）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建（WAR）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 单个 Kotlin 测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest

# 单个 Kotlin 测试方法
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest#methodName

# 全部 JS 测试（不走 Maven，迭代快）
node --test src/test/js/*.test.js

# 单个 JS 测试文件
node --test src/test/js/batchSendTaskConsoleInteraction.test.js

# Flyway 迁移集成测试（需本地 Docker；平时被 @EnabledIfSystemProperty 跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 空白/换行卫生
git diff --check
```

通过判据：
- Maven：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`，且 `BUILD SUCCESS`。
- `node --test`：退出码 0，输出末尾 `fail 0`。
- `git diff --check`：无输出。

来源：`CLAUDE.md`「Commands」章节 + 项目元信息 `test_command:` / `build_command:`；JS 命令取自 `pom.xml:199` 的 `exec-maven-plugin` argument 原串。

---

## 验收标准（主计划层）

- **M-1**：`grep -rn "buildEsFiltersForLevel\|matchesExpert" src/main/kotlin` 的结果中，每条新增筛选维度在两处都出现；P2/P3/P4 各自的验收标准逐项落实。
- **M-2**：`grep -n "updateLegacyConfig" -A 40 src/main/kotlin/.../BatchSendTaskConfigService.kt` 中出现 `<新字段> = existing.<新字段>`。
- **M-3**：对每个新增字段跑 `grep -rn "<字段名>" src/main/kotlin | wc -l`，与子计划列出的映射点数量一致（子计划须贴出期望数字与实际输出）。
- **M-4**：`countBySnapshot` 的入参仍是 `BatchExecutionSnapshot`；`BatchSendConfigController.kt:97` 的 `@PostMapping("/recipients/preview")` 签名未变。
- **M-5**：`OperatorStatusWriteSeamGuardTest` 绿；若红，检查是否为 HUMAN 授权登记的噪声站点。
- 回归：执行「验证命令」节的全量测试命令通过。

---

## 人工验收清单（主计划层）

### A-M1: 四个子计划全部上线后的端到端冒烟
- 前置条件：至少 2 条定时任务；其中一条 cron 为 `0 0 9-17 * * ?`；至少一个模板配置了非空 `required_keys`（若无，可在模板管理界面手工给某模板勾 2 个必填变量）。
- 操作步骤：
  1. 打开「批量邮件任务控制台」→「定时任务」，编辑 cron 为 `0 0 9-17 * * ?` 的那条。
  2. 确认「执行频率」= 自定义 cron，表达式框内容为 `0 0 9-17 * * ?`。
  3. 「邮箱服务商」选中 2 个域名，「专家状态」选中 2 个状态，记下预估行数字。
  4. 打开「邮件模版门禁过滤」开关，记下新的预估行数字。
  5. 直接点「保存任务」。
  6. 重新打开该任务。
- 预期结果：第 6 步回显与第 2-4 步完全一致 —— cron 仍是 `0 0 9-17 * * ?`；两个 picker 各 2 个 chip；门禁开关为开。第 4 步的命中数 ≤ 第 3 步。
- 覆盖：O-1 / O-2 / O-3 / O-4、N-6

### A-M2: 回归 —— 专家列表页筛选未受影响
- 前置条件：无。
- 操作步骤：打开专家列表页，使用「邮箱服务商」下拉选一个域名、「状态」下拉选一个状态。
- 预期结果：两者仍是**单选** `<select>`；列表按所选值过滤；URL query 参数仍为 `emailDomain=<单值>` 与 `operatorStatus=<单值>`。
- 覆盖：N-1、N-2

### A-M3: 回归 —— 标签 / 地区 picker 未受影响
- 前置条件：无。
- 操作步骤：在定时任务编辑器与手动执行面板中，各自打开「标签」「地区」下拉，勾选 / 取消 / 点 chip 上的 ×。
- 预期结果：行为与改动前一致；chip 样式、下拉高度、✓ 位置无肉眼可见变化。
- 覆盖：N-3

---

## 知识回写（Phase 6，已执行）

| 动作 | 条目 | 说明 |
|---|---|---|
| **更正** | `K-recipient-scope-status-filter` | 「3 条 ES 旁路」→ 更正为「1 条活体 ES 旁路 `buildEsFiltersForLevel`（3 调用点）+ 1 条 DB 旁路 `matchesExpert`」；`buildMaterialReminderEsFilters` 标注为零调用方死代码。附本计划 X-1 的 grep 输出。 |
| 新增 | `K-batch-multi-value-filter-seams` | 批量任务把单值筛选改多值时必须同步的全集（M-1 + M-3 + 逗号分隔契约）。 |
| 新增 | `K-batch-picker-comma-delimited-contract` | `batch-tag-picker` 族的值以逗号分隔存隐藏 input；选项 value 不得含逗号；同族元素 id 命名契约。 |
| 新增 | `K-cron-echo-whitelist-not-blacklist` | cron 回显必须白名单反解；`dow === "?"` 不足以判定「每天」，还须校验时/分为单值。 |
