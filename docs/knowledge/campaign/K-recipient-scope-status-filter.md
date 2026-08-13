---
id: K-recipient-scope-status-filter
domain: campaign
created: 2026-08-13
last_used: 2026-08-13
hit_count: 0
source: create-p:batch-send-status-consistency-05
severity: P1
---
批量发送的「专家状态」筛选（`operator_status` on `batch_send_task_config`，NULL = 不限）必须覆盖
**三条查询旁路**，缺一不可（结构同 [[K-discipline-unclassified-filter-bypasses]]，事故形态同
[[K-batch-send-filter-retry-parity]]）：

1. `ManualInitialOutreachService.buildEsFiltersForLevel()` —— ES 目标查询
2. `RecipientScope.matchesExpert()`（`BatchExecutionModels.kt`）—— 数据库重试联系人内存过滤
3. `ManualInitialOutreachService.buildMaterialReminderEsFilters()` —— 材料提醒查询

`NOT_CONTACTED` 的语义唯一（I-3）：ES 文档**没有** `operatorStatus` 字段 =
未联系（`ExpertIndexWriterService.syncOperatorStatus` 对 NOT_CONTACTED 执行的是
`ctx._source.remove('operatorStatus')`）。权威表达是 `ExpertSearchService.notContactedWithEmailFilters()`
的 `must_not exists operatorStatus`；`ExpertSearchService.operatorStatusFilter(status)` 抽出该特判
（`status == "NOT_CONTACTED"` → must_not exists，其余 → `term`），三条旁路共用，禁止另写
`term operatorStatus=NOT_CONTACTED`。

`buildEsFiltersForLevel` 的 INTRODUCTION+CANDIDATE 分支有一个关键陷阱（I-2）：当显式选择
**非** NOT_CONTACTED 状态（如 CONTACTED）时，必须**替换** `notContactedWithEmailFilters` 基座
（它带 `must_not exists operatorStatus`，与 `term operatorStatus=CONTACTED` 并存恒为空），
换成「exists email + domain + discipline + `operatorStatusFilter(status)`」；留空或 NOT_CONTACTED
才继续走 `notContactedWithEmailFilters`。else 分支（APPLICATION / MATERIAL_REMINDER）直接
在状态无关基座上追加 `operatorStatusFilter`。`matchesExpert` 的判定与 ES 完全同口径：
NOT_CONTACTED → `profile.operatorStatus.isNullOrBlank()`，其余 → 相等。

配置实体链路（新增筛选维度时须同步的全部位置，照 [[K-batch-config-legacy-adapter-field-preservation]]）：

- `BatchSendTaskConfig` / `BatchSendTaskConfigView` / `...CreateCommand` / `...UpdateCommand`
  四个 data class（`campaign/domain/BatchSendTaskConfig.kt`）—— 字段默认 `null`
- `BatchSendTaskConfigService`：`toView()`、`ConfigFields`、三个 `*Fields()`、
  `normalizeAndValidate`（校验白名单引用 `OperatorStatus.entries`，照 `ALLOWED_DISCIPLINES` 范式）
- `updateLegacyConfig()` 必须显式写 `operatorStatus = existing.operatorStatus`
  （旧 typed API 只改 cron，漏写会命中 Kotlin 默认值静默重置）
- **不要**加 `toLegacyConfig()` 与 updateLegacyConfig 返回值构造（KV 兼容层不拖进变更范围）
- `BatchExecutionSnapshot` / `RecipientScope`（`BatchExecutionModels.kt`）：
  `fromSnapshot` 与 `toExecutionSnapshot` 透传

前端注册点（行号漂移幅度已达千行量级，改前必须 grep 复核全集）：

- 配置编辑器：`showBatchConfigEditor`（fill）、`saveBatchConfigEditor`（payload）、
  选项填充 helper `fillBatchOperatorStatusSelectOptions`（在 `bindBatchSendTaskEvents` 中调用）
- 手动执行面板：`fillManualFormDefaults` / `deepCloneConfig` / `fillManualFormFromDraft` /
  `readManualFormValues` / `normalizeManualSnapshot` / `formatManualDiffValue` /
  `computeManualDiffs` fieldDefs / `computeAndRenderDiffs` fieldMap / `clearAllDiffMarkers` /
  `confirmManualExecution` snapshot
- 样式复用 `.batch-config-field` + `.bsc-input`，styles.css 零改动

守卫测试注意：`OperatorStatusWriteSeamGuardTest`（plan 02）扫描 `src/main/kotlin` 中所有
`operatorStatus = ` 行做白名单闭包。本筛选维度的**配置列映射**（`toView` / `toFields` /
`updateLegacyConfig` / `normalizeAndValidate` 等命名参数）不是 `expert_contact` 表写入，
属于该守卫的 DTO 类噪声——若守卫因本维度新增映射行而失败，需 HUMAN 授权把对应
`path:line:context` 登记进 `EXCLUDED_NOISE_SITES`（照 plan 04 A4 先例），不得自行改守卫。

关联：[[K-batch-send-filter-retry-parity]]、[[K-batch-config-legacy-adapter-field-preservation]]、
[[K-operator-status-single-writer]]、[[K-material-reminder-single-es-filter-seam]]
