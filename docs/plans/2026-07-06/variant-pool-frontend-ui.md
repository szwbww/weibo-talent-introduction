# 变体池前端管理 UI + 后端 DTO 贯通

## 需求描述

**可观测结果**：运营人员在后台 UI 中可以查看、编辑邮件模板的 `subjectVariants`（主题变体列表）和回复片段的 `variantGroup`（变体组标识），实现变体池的可视化管理。当前这两个字段已在数据库和渲染引擎中生效（P2 后端已上线），但 CRUD 接口和前端表单完全缺失，只能通过 SQL 手动维护。

**不得改变**：
- 变体选择算法（`Math.floorMod(seed, size)`）和确定性规则（来源: K-positive-hash-index）
- 模板 block 组装顺序契约（来源: K-composed-reply-order-contract）
- `MailComposeTemplateService.render/renderByCode` 的渲染逻辑
- 现有 snippet 的 CRUD 语义（snippetType、isDefault 等）

**不在范围**：
- 变体池的渲染预览（预览面板暂不展示变体选择效果，保持当前行为）
- 新建侧栏 Tab 或视图（不触发 K-view-registration-triad 四联契约）
- 数据库 schema 变更（字段已在 V64 迁移中添加）

## 关键不变量

### Invariant I-1: subjectVariants 通过 JSON 数组格式在全链路传递
- Rule: 前端以 `["变体A","变体B"]` JSON 数组字符串发送给后端，后端原样存入 `mail_compose_template.subject_variants` TEXT 列。前端读取时从 API 响应的 `subjectVariants` 字符串字段解析为列表展示。空数组或 null 均表示不使用变体。
- Applies to: `saveComposeTemplate()` (app.js), `MailComposeTemplateRequest.toCommand()`, `MailComposeTemplateService.create/update`, `MailComposeTemplateService.toDetail()`, `openComposeTemplateEditor()` (app.js)
- Violation consequence: 前端保存的变体丢失（不持久化），或编辑时看不到已配置的变体。
- 来源: original

### Invariant I-2: variantGroup 通过字符串在全链路传递
- Rule: 前端以纯字符串发送 `variantGroup`（如 `"greeting"`），后端存入 `reply_snippet.variant_group` VARCHAR(64) 列。列表展示时显示为标签。空字符串和 null 均表示不属于任何变体组（不参与变体选择）。
- Applies to: `saveReplySnippet()` (app.js), `ReplySnippetCreateRequest/UpdateRequest.toCommand()`, `ReplySnippetService.create/update`, `ReplySnippet.toResponse()`, `fillReplySnippetForm()` (app.js), `renderReplySnippetRow()` (app.js)
- Violation consequence: 运营无法在 UI 管理变体组，只能 SQL 手动维护。
- 来源: original

### Invariant I-3: 空值不破坏现有数据
- Rule: 对于不含 subjectVariants 的旧模板和不含 variantGroup 的旧片段，编辑保存后这两个字段保持 null/空，不会被意外写入默认值。
- Applies to: `saveComposeTemplate()`, `saveReplySnippet()`, 后端 `create/update` 方法
- Violation consequence: 旧数据被污染，可能导致变体选择意外触发。
- 来源: original

## 现状审计

### MailComposeTemplate 后端 DTO 链路（write path: 前端 → API → DB）
- 前端 `saveComposeTemplate()` (app.js:5928) → POST/PUT `/api/compose-templates`
- `MailComposeTemplateRequest` (Controller:54) → `.toCommand()` → `MailComposeTemplateCommand` (Service:390)
- `MailComposeTemplateService.create()` (Service:36) → `MailComposeTemplate(...)` constructor
- `MailComposeTemplateService.update()` (Service:57) → `existing.copy(...)` 

**缺口**：链路中所有 DTO 均无 `subjectVariants` 字段：
1. `MailComposeTemplateRequest` — 无 subjectVariants
2. `MailComposeTemplateCommand` — 无 subjectVariants
3. `MailComposeTemplateDetail` — 无 subjectVariants（API 响应不含该字段）
4. `create()` 构造 `MailComposeTemplate` 时不传 subjectVariants
5. `update()` 的 `existing.copy(...)` 不传 subjectVariants（**会保留旧值**，因为 Kotlin data class copy 默认保留未指定字段）
6. `toDetail()` (Service:145) 不映射 subjectVariants 到 Detail

### ReplySnippet 后端 DTO 链路（write path: 前端 → API → DB）
- 前端 `saveReplySnippet()` (app.js:2250) → POST/PUT `/api/reply-snippets`
- `ReplySnippetCreateRequest` (Controller:59) → `.toCommand()` → `ReplySnippetCreateCommand` (Service:162)
- `ReplySnippetUpdateRequest` (Controller:76) → `.toCommand()` → `ReplySnippetUpdateCommand` (Service:170)
- `ReplySnippetService.create()` (Service:45) → `ReplySnippet(...)` constructor
- `ReplySnippetService.update()` (Service:72) → `existing.copy(...)` 

**缺口**：链路中所有 DTO 均无 `variantGroup` 字段：
1. `ReplySnippetCreateRequest` — 无 variantGroup
2. `ReplySnippetUpdateRequest` — 无 variantGroup
3. `ReplySnippetCreateCommand` — 无 variantGroup
4. `ReplySnippetUpdateCommand` — 无 variantGroup
5. `ReplySnippetResponse` — 无 variantGroup（API 响应不含该字段）
6. `ReplySnippet.toResponse()` (Controller:100) 不映射 variantGroup
7. `create()` 构造 `ReplySnippet` 时不传 variantGroup
8. `update()` 的 `existing.copy(...)` 不传 variantGroup（**会保留旧值**）

### 前端表单（read + write path）
- `openComposeTemplateEditor()` (app.js:5761)：回填模板数据但无 subjectVariants
- `saveComposeTemplate()` (app.js:5928)：payload 无 subjectVariants
- `fillReplySnippetForm()` (app.js:2233)：回填片段数据但无 variantGroup
- `saveReplySnippet()` (app.js:2250)：payload 无 variantGroup
- `renderReplySnippetRow()` (app.js:2145)：表格行无 variantGroup 展示

### 交互点
- 模板 subjectVariants 写入 → `MailComposeTemplateService.selectSubjectVariant()` 读取（已实现，不动）
- 片段 variantGroup 写入 → `MailComposeTemplateService.resolveSnippetVariant()` 读取（已实现，不动）
- 片段列表 API 响应 → 模板编辑器 block 行的 snippet 选择下拉框（snippet label 可展示 variantGroup 信息，非必须但有帮助）

## 实现方案

### Phase A: 后端 DTO 贯通（subjectVariants）

#### Task A-1: MailComposeTemplateRequest/Command/Detail 添加 subjectVariants (I-1, I-3)
- 文件: `MailComposeTemplateController.kt`
  - `MailComposeTemplateRequest` 添加 `val subjectVariants: String? = null`
  - `toCommand()` 传递 subjectVariants
- 文件: `MailComposeTemplateService.kt`
  - `MailComposeTemplateCommand` 添加 `val subjectVariants: String? = null`
  - `MailComposeTemplateDetail` 添加 `val subjectVariants: String? = null`
  - `create()`: 构造 `MailComposeTemplate` 时传入 `subjectVariants = command.subjectVariants`
  - `update()`: `existing.copy(...)` 中传入 `subjectVariants = command.subjectVariants`（注意：不做 `?: existing.subjectVariants` 因为用户可能主动清空变体）
  - `toDetail()`: 映射 `subjectVariants = template.subjectVariants`

### Phase B: 后端 DTO 贯通（variantGroup）

#### Task B-1: ReplySnippet Request/Command/Response 添加 variantGroup (I-2, I-3)
- 文件: `ReplySnippetController.kt`
  - `ReplySnippetCreateRequest` 添加 `val variantGroup: String? = null`，`toCommand()` 传递
  - `ReplySnippetUpdateRequest` 添加 `val variantGroup: String? = null`，`toCommand()` 传递
  - `ReplySnippetResponse` 添加 `val variantGroup: String? = null`
  - `ReplySnippet.toResponse()` 映射 `variantGroup = variantGroup`
- 文件: `ReplySnippetService.kt`
  - `ReplySnippetCreateCommand` 添加 `val variantGroup: String? = null`
  - `ReplySnippetUpdateCommand` 添加 `val variantGroup: String? = null`
  - `create()`: 构造 `ReplySnippet` 时传入 `variantGroup = command.variantGroup?.trim()?.takeIf { it.isNotBlank() }`
  - `update()`: `existing.copy(...)` 中传入 `variantGroup = command.variantGroup?.trim()?.takeIf { it.isNotBlank() }`

### Phase C: 前端 — 模板编辑器 subjectVariants UI

#### Task C-1: index.html 模板编辑器添加主题变体区域 (I-1)
- 文件: `index.html`
- 在 subject 输入框下方添加「主题变体」区域：
  - 一个容器 `<div id="subjectVariantsContainer">`
  - 包含动态行列表，每行一个 input + 删除按钮
  - 底部一个「添加变体」按钮
  - 提示文本：「留空则使用上方默认主题」

#### Task C-2: app.js 主题变体管理逻辑 (I-1, I-3)
- 文件: `app.js`
- `openComposeTemplateEditor()`: 解析 `template.subjectVariants` JSON 字符串为数组，调用 `renderSubjectVariantRows(variants)` 渲染行
- `renderSubjectVariantRows(variants)`: 渲染变体输入行列表（空数组时显示零行）
- `addSubjectVariantRow()`: 追加一个空输入行
- `removeSubjectVariantRow(index)`: 删除指定行
- `collectSubjectVariants()`: 收集非空行的值，序列化为 JSON 数组字符串；全为空则返回 null
- `saveComposeTemplate()`: payload 中添加 `subjectVariants: collectSubjectVariants()`

### Phase D: 前端 — 片段编辑器 variantGroup UI

#### Task D-1: index.html 片段编辑器添加变体组字段 (I-2)
- 文件: `index.html`
- 在片段编辑表单中（displayOrder 字段附近）添加 variantGroup 输入框：
  - `<label>变体组 <input type="text" name="variantGroup" placeholder="如 greeting（留空则不参与变体选择）"></label>`

#### Task D-2: app.js 片段变体组管理逻辑 (I-2, I-3)
- 文件: `app.js`
- `fillReplySnippetForm()`: 回填 `form.variantGroup.value = snippet?.variantGroup || ""`
- `saveReplySnippet()`: payload 添加 `variantGroup: values.variantGroup?.trim() || null`（create payload 中也要加）
- `renderReplySnippetRow()`: 在内容列后添加 variantGroup 标签列（有值时显示 badge，无值时空）
- `renderReplySnippetTypePanel()`: thead 增加「变体组」列头

## 变更文件清单

| # | 文件路径 | 改动类型 | 所属 Phase |
|---|---------|---------|-----------|
| 1 | `template/controller/MailComposeTemplateController.kt` | 修改 | A |
| 2 | `template/service/MailComposeTemplateService.kt` | 修改 | A |
| 3 | `reply/controller/ReplySnippetController.kt` | 修改 | B |
| 4 | `reply/service/ReplySnippetService.kt` | 修改 | B |
| 5 | `src/main/resources/static/index.html` | 修改 | C+D |
| 6 | `src/main/resources/static/app.js` | 修改 | C+D |

共 6 个文件，2 个子系统（模板 DTO: 1-2, 片段 DTO: 3-4），前端为两者的消费端 (5-6)。

## 验收标准

- **I-1 (subjectVariants round-trip)**:
  1. 编辑模板 → 添加 3 个主题变体 → 保存 → 重新打开编辑器 → 确认 3 个变体行仍在且内容正确
  2. 编辑模板 → 清空所有变体行 → 保存 → 查数据库确认 `subject_variants` 为 null
  3. 对已有 subjectVariants 的模板调用 GET `/api/compose-templates` → 响应 JSON 包含 subjectVariants 字段

- **I-2 (variantGroup round-trip)**:
  1. 编辑片段 → 设置 variantGroup 为 "greeting" → 保存 → 重新打开编辑器 → 确认 variantGroup 字段值为 "greeting"
  2. 片段列表中有 variantGroup 的片段显示标签，无的不显示
  3. 编辑片段 → 清空 variantGroup → 保存 → 查数据库确认 `variant_group` 为 null

- **I-3 (空值安全)**:
  1. 编辑一个从未设置 subjectVariants 的旧模板 → 只修改 subject → 保存 → 确认 `subject_variants` 仍为 null
  2. 编辑一个从未设置 variantGroup 的旧片段 → 只修改 content → 保存 → 确认 `variant_group` 仍为 null

- **集成场景**:
  1. 创建模板 → 添加 2 个 subject 变体 → 保存 → 编辑模板添加一个 REPLY_SNIPPET block 引用带 variantGroup 的 snippet → 保存 → 预览 → 确认正常渲染（预览使用默认 seed 0）
  2. 确认 `mvn test` 全量通过（来源: K-template-feature-coverage 提醒不能只靠测试通过）
