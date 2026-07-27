# 批量发送邮件模板选择器

## 需求描述

- **可观测结果**：运营在"批量发送介绍邮件"弹窗中可通过下拉框选择要使用的邮件模板（`mail_compose_template`），选中后下方显示模板预览（subject + body 摘要）。发送时使用选定模板而非硬编码 `INTRODUCTION`。已选模板 ID 持久化到 `batch_send_setting`，下次打开弹窗自动恢复。
- **不可变更**：现有模板管理 CRUD（`/api/compose-templates`）不变；自动回复流程（`AutoMailReplyService`）不受影响；`MailComposeTemplate` 表结构不变。
- **不在范围**：发送记录（`mail_record`）加 `template_id` 字段做审计追溯——拆到后续 plan。

## 关键不变量

### Invariant I-1: 模板选择持久化键

- Rule: 选中的模板 ID 通过 `batch_send_setting` 表的 `settingKey = "batchSend.templateId"` 持久化，值为模板的数据库 ID（`Long`）的字符串形式。缺失此 key 或值为空串时，回退到按 `templateCode = "INTRODUCTION"` 查找（保持向后兼容）。
- Applies to: `BatchSendSettingService.getConfig()` / `updateConfig()`、前端 `readBatchSendConfigForm()` / `fillBatchSendConfigForm()`
- Violation consequence: 重启或刷新后模板选择丢失，运营无感知地使用错误模板发邮件。

### Invariant I-2: compose 路径统一使用 templateId

- Rule: `IntroductionMailComposer.compose()` 接受 `templateId: Long?` 参数。当 `templateId != null` 时调用 `MailComposeTemplateService.render(templateId)`；当 `templateId == null` 时回退到 `renderByCode("INTRODUCTION")`（兼容旧路径）。
- Applies to: `IntroductionMailComposer.compose()`、`ManualInitialOutreachService.runScheduledBatch()`、`InitialOutreachService.sendInitialBatch()`
- Violation consequence: 模板选择无效或旧调用路径报错。

### Invariant I-3: 前端模板下拉与配置表单联动

- Rule: 模板下拉框的 value 是模板 ID（数字）。下拉框 change 时立即更新预览区。保存配置时 `templateId` 随其他字段一起 PUT `/api/mail/batch-send/config`。打开弹窗时从 `preload` 返回的 `batchConfig.templateId` 回填下拉框。
- Applies to: `index.html` 模板下拉框、`app.js` 的 `fillBatchSendConfigForm()`、`readBatchSendConfigForm()`、模板预览渲染
- Violation consequence: 运营看到的预览与实际发送内容不一致，或选择未持久化。

### Invariant I-4: 仅展示已启用的 INTRODUCTION 类模板

- Rule: 前端加载模板列表调用 `/api/compose-templates`（已有接口），但下拉框只展示 `enabled = true` 且 `mailType = "INTRODUCTION"` 的模板（前端过滤或新增后端 endpoint）。
- Applies to: 前端模板下拉框渲染逻辑
- Violation consequence: 运营可能选到禁用模板或非介绍类模板导致发送错误内容。

## 现状审计

### batch_send_setting 表

- Schema: `id BIGINT PK, setting_key VARCHAR UNIQUE, setting_value VARCHAR, updated_at DATETIME`
- Write paths:
  1. `BatchSendSettingService.upsert(key, value)` — 所有配置更新入口（`updateConfig`、`setAutoEnabled`、`setRuntimeStatus`）
  2. `V27__create_batch_send_setting.sql` — 建表迁移
- Read paths:
  1. `BatchSendSettingService.loadAll()` → `getConfig()` / `getRuntimeStatus()` — 读取全部配置
  2. `ManualInitialOutreachService.runScheduledBatch()` — 读 `getConfig()` 获取 emailDomain、dailyCap、roundSize 等
  3. `BatchSendControlService.startAuto/startManual/runManualOnce` — 读 `getConfig()` 判断 autoEnabled
- Interaction points: `getConfig()` 的返回值 `BatchSendConfig` 被 `ManualInitialOutreachService` 消费，新增 `templateId` 字段后消费侧需透传到 compose 调用。

### IntroductionMailComposer

- Write paths: 无（纯组装逻辑，不写存储）
- Read paths:
  1. `InitialOutreachService.sendInitialBatch()` — 调用 `compose(accountCode, expert)`
  2. `ManualInitialOutreachService.runScheduledBatch()` — 调用 `compose(accountCode, expert)`
- 当前硬编码: `renderByCode("INTRODUCTION")` — 这就是"黑盒"的根源。

### mail_compose_template 表

- Write paths: `MailComposeTemplateService.create/update/delete/setEnabled`
- Read paths:
  1. `MailComposeTemplateService.listAll/listEnabled/getById/render/renderByCode/preview`
  2. `IntroductionMailComposer` 间接通过 `renderByCode`
- Interaction points: 新增模板选择后，`IntroductionMailComposer` 将从 `renderByCode` 切换到 `render(id)` 路径。

### 前端 batchSendConfigPanel

- Write paths: `saveBatchSendConfig()` → PUT `/api/mail/batch-send/config`
- Read paths: `fillBatchSendConfigForm(config)` 从 `preload` 结果或 API 响应填充表单
- Interaction points: 新增模板下拉框需在 `readBatchSendConfigForm()` 中收集 `templateId`，在 `fillBatchSendConfigForm()` 中回填。

## 实现方案

### Phase 1: 后端 — 配置层支持 templateId

**Task 1.1**: `BatchSendSettingService` + `BatchSendConfig` 增加 `templateId` 字段 (I-1)

- 文件: `campaign/service/BatchSendSettingService.kt`
- `BatchSendConfig` data class 增加 `val templateId: Long? = null`
- `BatchSendConfigUpdateRequest` 增加 `val templateId: Long? = null`
- `getConfig()` 中增加 `templateId = longValue(values, KEY_TEMPLATE_ID, null)` （新增常量 `KEY_TEMPLATE_ID = "batchSend.templateId"`，默认 null）
- `updateConfig()` 中增加 `upsert(KEY_TEMPLATE_ID, cmd.templateId?.toString() ?: "")`
- `validate()` 中可选校验：若 templateId 非 null 则必须 > 0
- 遵循不变量: I-1

**Task 1.2**: `IntroductionMailComposer.compose()` 接受 `templateId` 参数 (I-2)

- 文件: `mail/service/IntroductionMailComposer.kt`
- 修改 `compose` 签名为 `fun compose(accountCode: String, expert: ExpertProfile, templateId: Long? = null): ComposedMail`
- 当 `templateId != null` 时调用 `mailComposeTemplateService.render(templateId, variables)`
- 当 `templateId == null` 时保持原 `renderByCode("INTRODUCTION", variables)` 调用
- 遵循不变量: I-2

**Task 1.3**: `ManualInitialOutreachService` 透传 templateId (I-2)

- 文件: `campaign/service/ManualInitialOutreachService.kt`
- `runScheduledBatch()` 中从 `config` 取 `config.templateId`，传递给 `introductionMailComposer.compose(account.accountCode, expert, config.templateId)`
- 遵循不变量: I-2

### Phase 2: 前端 — 模板选择下拉框

**Task 2.1**: `index.html` 增加模板选择区域 (I-3, I-4)

- 文件: `src/main/resources/static/index.html`
- 在 `batchSendConfigPanel` 的 `<p class="batch-send-config-panel-hint">` 后（定时调度 fieldset 前）插入一个新 fieldset "邮件模板"，包含：
  - 一个 `<select id="batchSendTemplateId">` 下拉框
  - 一个 `<div id="batchSendTemplatePreview">` 预览区（显示 subject + body 摘要）
- 遵循不变量: I-3, I-4

**Task 2.2**: `app.js` — 加载模板列表、预览、表单联动 (I-3, I-4)

- 文件: `src/main/resources/static/app.js`
- 在 `MANUAL_INITIAL_OUTREACH.preload` 中额外请求 `/api/compose-templates`，返回模板列表。
- 新增 `fillBatchSendTemplateSelector(templates, selectedId)` 函数：过滤 `enabled && mailType === "INTRODUCTION"`，填充 `<select>` options (I-4)。
- 修改 `fillBatchSendConfigForm(config)` 末尾调用 `fillBatchSendTemplateSelector()`（模板列表从 preload 缓存或单独变量获取）。
- `<select>` change 事件：调用 `/api/compose-templates/{id}/preview` 获取预览内容，渲染到 `#batchSendTemplatePreview`。
- 修改 `readBatchSendConfigForm()` 增加 `templateId: Number(val("batchSendTemplateId")) || null`。
- 遵循不变量: I-3, I-4

**Task 2.3**: `styles.css` — 模板预览区样式

- 文件: `src/main/resources/static/styles.css`
- 为 `#batchSendTemplatePreview` 增加预览卡片样式（灰色背景、圆角、最大高度限制 + overflow scroll）

### Phase 3: 状态展示 — 让发送过程中可见当前模板

**Task 3.1**: `BatchSendStatusView` 返回 templateName (I-1)

- 文件: `campaign/service/BatchSendControlService.kt`
- `getStatus()` 中从 `config.templateId` 查出模板名称（可选），加入 `BatchSendStatusView`：`val templateName: String? = null`
- 前端 `renderBatchSendAccountTable()` 或状态区域展示 "当前模板: xxx"
- 遵循不变量: I-1

## 变更文件清单

| # | 文件 | 变更类型 | 说明 |
|---|------|----------|------|
| 1 | `campaign/service/BatchSendSettingService.kt` | 修改 | 增加 templateId 字段到 config/request |
| 2 | `mail/service/IntroductionMailComposer.kt` | 修改 | compose() 接受 templateId 参数 |
| 3 | `campaign/service/ManualInitialOutreachService.kt` | 修改 | 透传 templateId 到 compose 调用 |
| 4 | `campaign/service/BatchSendControlService.kt` | 修改 | getStatus 返回 templateName |
| 5 | `src/main/resources/static/index.html` | 修改 | 增加模板选择 fieldset |
| 6 | `src/main/resources/static/app.js` | 修改 | 加载模板列表、预览、表单联动 |
| 7 | `src/main/resources/static/styles.css` | 修改 | 模板预览区样式 |

共 7 个文件，≤ 10 ✓；2 个子系统（后端配置/发送 + 前端）≤ 2 ✓；1 个新字段（templateId on batch_send_setting KV 表，非 schema 变更）✓

## 验收标准

- **I-1**: 
  - 在弹窗中选择模板 A → 保存配置 → 关闭弹窗 → 重新打开 → 下拉框仍显示模板 A
  - 调用 `GET /api/mail/batch-send/config` 返回 `templateId` 等于选中模板的 ID
  - 不配置 templateId 时（新部署/首次使用），系统回退到 `templateCode = "INTRODUCTION"` 正常发送
- **I-2**: 
  - 配置 templateId=X 后手动执行一轮 → 发出的邮件 subject 和 body 与模板 X 的预览一致
  - templateId=null（未配置）时 → 行为与改动前完全一致（`renderByCode("INTRODUCTION")`）
- **I-3**: 
  - 切换下拉框 → 预览区实时显示选中模板的 subject + body 摘要
  - 保存配置时 payload 包含正确的 `templateId`
- **I-4**: 
  - 下拉框仅展示 `enabled=true` 且 `mailType="INTRODUCTION"` 的模板
  - 若当前配置的 templateId 对应的模板被禁用，下拉框显示该模板但标注 "(已禁用)"

### 集成场景

1. **首次部署兼容**：无 `batchSend.templateId` 记录 → `config.templateId = null` → compose 走 `renderByCode("INTRODUCTION")` → 行为不变。
2. **模板被删除后发送**：已配置 templateId=5 但模板被删 → `render(5)` 抛异常 → 发送失败、错误日志记录 → 不会静默发错误内容。（可考虑在 compose 层 catch 并 fallback 到 INTRODUCTION，但当前 plan 选择 fail-fast 以避免静默发送错误模板。）
3. **并发：运营改模板 + 发送进行中**：`config` 在 `runScheduledBatch` 开始时读一次并缓存，整批使用同一模板 ID — 不受中途配置变更影响。
