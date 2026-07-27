# 邮件模板专家变量注入 + Fallback 语法

> 创建日期: 2026-07-06
> 触发原因: 邮件投诉率 9.1%，核心原因之一是外发模板零个性化（"Dear Professor" 无姓名、无研究方向），同时模板 Subject 固定无法个性化

## 需求描述

**可观测结果**：运营在模板编辑器中可使用专家维度的占位符（如 `${expertName}`、`${researchFields}`），使首次外发邮件的正文和主题包含收件专家的个性化信息。占位符支持 `${key|默认值}` fallback 语法，当专家字段为空时自动降级为默认值而非留空。

**不可变更**：
1. 非 INTRODUCTION 类型的模板调用方（`AutoMailReplyService`、`MeetingInvitationMailComposer`、`ManualExpertMailService`、`AutoReplyPreviewService`）的行为不变 — 它们仍然只传 sender 变量，模板中不含专家占位符时行为完全一致。
2. `MailComposeTemplateService.render()` / `renderByCode()` 的签名不变（`variables: Map<String, String>`）。
3. 现有不含 `|` 的占位符 `${senderName}` 等行为不变 — fallback 语法仅在检测到 `|` 时激活。

**不做**：
- 不改 MEETING_INVITATION / QA 自动回复等模板的变量注入（它们没有 ExpertProfile 上下文）
- 不改模板的"服务端预览"逻辑（预览时无真实专家数据，保持原样）
- 不改 `senderDisplayName` 缺失问题（IntroductionMailComposer 当前未传此变量，属于已有行为，不在本次范围内）

## 关键不变量

### Invariant I-1: Fallback 语法向后兼容
- Rule: `renderText` 改造后，不含 `|` 的占位符 `${key}` 行为与当前完全一致 — 若 variables 中有该 key 则替换为 value，否则保留原文 `${key}` 不动。
- Applies to: `MailComposeTemplateService.renderText()`（唯一实现点），被 `renderTemplate()`、`resolveBlocks()` 全部 6 处调用点间接使用。
- Violation consequence: 所有模板类型（INTRODUCTION / MEETING_INVITATION / QA / REPLY_SNIPPET / CUSTOM_TEXT）的渲染全部受影响，可能导致外发邮件正文异常。
- 来源: original

### Invariant I-2: 专家变量空值降级为空字符串
- Rule: `IntroductionMailComposer.compose()` 中新增的专家变量，当 `ExpertProfile` 对应字段为 `null` 时，传入 variables 的值为空字符串 `""`。这确保 `${researchFields}` 在无 fallback 时被替换为空而非保留占位符原文。
- Applies to: `IntroductionMailComposer.compose()` — 唯一注入专家变量的位置
- Violation consequence: 外发邮件正文中出现 `${researchFields}` 原始占位符文本，暴露系统内部实现。
- 来源: original

### Invariant I-3: Fallback 语法格式
- Rule: Fallback 格式为 `${key|fallback text}`。管道符 `|` 作为分隔符，仅取第一个 `|` 左侧为 key、右侧为 fallback。当 key 对应 value 非空时使用 value；当 value 为空字符串或 key 不存在于 variables 时使用 fallback text。
- Applies to: `MailComposeTemplateService.renderText()`
- Violation consequence: 运营在模板中写 `${researchFields|Science}` 但 fallback 不生效，导致 Subject/正文出现空白片段。
- 来源: original

## 现状审计

### 模板变量注入 — `IntroductionMailComposer.compose()`
- 当前 variables map 仅 5 个 sender 字段: `senderEmail`, `senderName`, `senderTitle`, `teamName`, `countryName`
- `ExpertProfile` 参数已传入但仅用于取 `expert.email` 作为收件地址
- 写路径:
  1. `InitialOutreachService.sendInitialBatch()` (line 61) — 自动批量外发，调用 `compose(accountCode, expert)`
  2. `ManualInitialOutreachService.runScheduledBatch()` (line 297) — 手动/调度批量外发，调用 `compose(accountCode, expert, config.templateId)`
- 读路径: `compose()` 返回的 `ComposedMail` 被传给 `mailDeliveryService.send()` 和 `txHelper.recordSuccess()`，后者将 subject/body 持久化到 `mail_record` 表

### 模板渲染引擎 — `MailComposeTemplateService.renderText()`
- 实现: 简单 `fold` + `replace("\${key}", value)`，不支持 fallback
- 调用点（全部在 `MailComposeTemplateService` 内部）:
  1. line 115: `renderTemplate()` → subject 替换
  2. line 248: `resolveBlocks()` → QA_RULE block body 替换
  3. line 279: `resolveBlocks()` → REPLY_SNIPPET block content 替换
  4. line 294: `resolveBlocks()` → CUSTOM_TEXT block customText 替换
- 外部调用方（传 variables 的入口）:
  1. `IntroductionMailComposer` — sender 变量（本次将扩展）
  2. `AutoMailReplyService.mailTemplateVariables()` — sender 变量
  3. `ManualExpertMailService.mailTemplateVariables()` — sender 变量
  4. `AutoReplyPreviewService.mailTemplateVariables()` — sender 变量
  5. `MeetingInvitationMailComposer` — sender 变量

### 前端模板编辑器 — 变量提示栏
- 位置: `index.html` line 1400-1407, `<div class="compose-template-variable-row">`
- 当前展示 6 个 span: `senderName`, `senderTitle`, `teamName`, `countryName`, `senderEmail`, `senderDisplayName`
- 预览元数据 (line 1423): 硬编码 `<strong>6 个</strong>`

### 交互点
- `renderText` fallback 改造影响所有模板类型的渲染 → I-1 保障向后兼容
- 专家变量只在 `IntroductionMailComposer` 注入 → 其他调用方不传专家变量，模板中也不含专家占位符，无交叉影响

## 实现方案

### 阶段 1: renderText fallback 语法 (I-1, I-3)

**Task 1.1**: 改造 `MailComposeTemplateService.renderText()`

文件: `src/main/kotlin/.../template/service/MailComposeTemplateService.kt`

当前实现:
```kotlin
private fun renderText(text: String, variables: Map<String, String>): String =
    variables.entries.fold(text) { rendered, (key, value) ->
        rendered.replace("\${$key}", value)
    }
```

改为: 先用正则 `\$\{(\w+)\|([^}]*)\}` 处理带 fallback 的占位符，再用原有逻辑处理普通占位符。处理顺序:
1. 扫描所有 `${key|fallback}` 模式 — 若 `variables[key]` 非空则替换为 value，否则替换为 fallback text
2. 扫描所有 `${key}` 模式 — 沿用原有 fold + replace 逻辑 (I-1 兼容保障)

**Task 1.2**: 添加 `renderText` 单元测试

文件: `src/test/kotlin/.../template/service/MailComposeTemplateServiceTest.kt`

新增测试用例:
- `${key}` 有值 → 替换（I-1 回归）
- `${key}` 无值 → 保留原文（I-1 回归）
- `${key|fallback}` 有值 → 用 value（I-3）
- `${key|fallback}` 值为空字符串 → 用 fallback（I-3）
- `${key|fallback}` key 不存在 → 用 fallback（I-3）
- `${key|含|管道符}` → 仅第一个 `|` 作为分隔符，fallback 可含 `|`（I-3）
- 混合使用 `${a}` 和 `${b|default}` → 各自独立替换（I-1 + I-3）

### 阶段 2: 注入专家变量 (I-2)

**Task 2.1**: 扩展 `IntroductionMailComposer.compose()` 的 variables map

文件: `src/main/kotlin/.../mail/service/IntroductionMailComposer.kt`

在现有 5 个 sender 变量基础上追加 6 个专家变量:

| 占位符 key | 来源 | 含义 |
|---|---|---|
| `expertName` | `expert.displayName` | 专家全名 (如 John Smith) |
| `expertFamilyName` | `expert.familyNames.orEmpty()` | 专家姓氏 (如 Smith) |
| `researchFields` | `expert.researchFields.orEmpty()` | 研究领域 |
| `institution` | `expert.institution.orEmpty()` | 所属机构 |
| `keyword` | `expert.keyword.orEmpty()` | 研究关键词 |
| `expertCountry` | `expert.country.orEmpty()` | 专家所在国家 |

所有 nullable 字段用 `.orEmpty()` 降级为空字符串 (I-2)。

**Task 2.2**: 更新 `IntroductionMailComposerTest`

文件: `src/test/kotlin/.../mail/service/IntroductionMailComposerTest.kt`

两个现有测试的 mock 匹配需更新 — variables map 从 5 个 key 扩展为 11 个 key。新增一个测试用例验证 null 字段降级为空字符串 (I-2)。

### 阶段 3: 前端变量提示栏 (无不变量依赖)

**Task 3.1**: 扩展 `index.html` 的变量提示栏

文件: `src/main/resources/static/index.html`

将 line 1400-1407 的 `compose-template-variable-row` 区域改为两行:
- 第一行: 发送方变量 `${senderName}` `${senderTitle}` `${teamName}` `${countryName}` `${senderEmail}` `${senderDisplayName}`
- 第二行: 专家变量（仅项目介绍邮件可用）`${expertName}` `${expertFamilyName}` `${researchFields}` `${institution}` `${keyword}` `${expertCountry}`
- 第三行: 小字说明 fallback 语法 — `提示: 支持默认值语法 ${变量名|默认值}，变量为空时使用默认值`

同时更新预览元数据 line 1423 的硬编码 `6 个` 为动态计算或改为 `12 个`。

## 变更文件清单

| # | 文件 | 改动类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/.../template/service/MailComposeTemplateService.kt` | 修改 | `renderText()` 增加 fallback 语法 |
| 2 | `src/main/kotlin/.../mail/service/IntroductionMailComposer.kt` | 修改 | `compose()` variables map 扩展专家字段 |
| 3 | `src/main/resources/static/index.html` | 修改 | 模板编辑器变量提示栏扩展 |
| 4 | `src/test/kotlin/.../template/service/MailComposeTemplateServiceTest.kt` | 修改 | renderText fallback 测试 |
| 5 | `src/test/kotlin/.../mail/service/IntroductionMailComposerTest.kt` | 修改 | 更新 mock 匹配 + 空值降级测试 |

共 5 个文件，2 个子系统（模板渲染引擎 + Introduction 邮件组装）。

## 验收标准

- **I-1 (向后兼容)**: `renderText("Hello ${senderName}", mapOf("senderName" to "Chen"))` → `"Hello Chen"`；`renderText("Hello ${unknown}", emptyMap())` → `"Hello ${unknown}"`。现有测试 `renderByCode renders custom text variables and returns mail type` 通过。
- **I-2 (空值降级)**: 构造 `ExpertProfile(researchFields = null, institution = null, ...)`，`compose()` 返回的 variables 中 `researchFields` 和 `institution` 的值为 `""`，而非 `"null"` 或抛异常。
- **I-3 (fallback)**: `renderText("Topic: ${researchFields|Science}", mapOf("researchFields" to ""))` → `"Topic: Science"`；`renderText("Topic: ${researchFields|Science}", mapOf("researchFields" to "AI"))` → `"Topic: AI"`。
- **集成**: 模板 subject 配置为 `Collaboration in ${researchFields|Research}` 且专家 `researchFields = "Machine Learning"` 时，实际外发邮件的 Subject 为 `Collaboration in Machine Learning`。专家 `researchFields = null` 时，Subject 为 `Collaboration in Research`。
- **前端**: 模板编辑器打开后，变量提示栏展示全部 12 个变量，分发送方/专家两行，底部有 fallback 语法说明。
- **回归**: `mvn test` 全量通过。
