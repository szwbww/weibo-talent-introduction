# 邮件模板（Compose Template）— 开发计划

## 需求描述

**可观察结果**：运营在管理后台看到一个「邮件模板」Tab（侧栏），内含三个子 Tab（QA 规则 / 回复片段 / 邮件模板）。「邮件模板」子 Tab 可新建/编辑/启用/禁用模板，模板由多个内容块按顺序拼接而成，每个块可以是：引用 QA 规则、引用回复片段、自定义文本。专家联系页的「手动发送邮件」下拉框不再显示 QA 规则，仅显示邮件模板（compose template）和系统模板（INTRODUCTION / MEETING_INVITATION / MATERIAL_REMINDER）。

**不变的行为**：
- QA 规则的自动匹配/自动回复管线（`AutoMailReplyService`、`QaMatchService`）不受影响
- 收发件箱的组装回复工作台（composed reply）不受影响
- 回复片段的管理与解析（`ReplySnippetService`）不受影响
- 系统模板（`mail_template` 表 INTRODUCTION/MEETING_INVITATION/MATERIAL_REMINDER）的自动触达、会议邀约发送不受影响
- `PendingMailOperationService` 的 `sendQaReply`/`sendManualComposedReply`/`sendManualRichReply` 三条发送路径不受影响

**不在范围内**：
- 组装回复工作台的 UI/逻辑改动
- AI 训练/AI 回复草稿逻辑改动
- 变量模板占位符（如 `${senderEmail}`）在 compose template 中的替换——compose template 只拼 QA/snippet/自定义文本纯文本
- QA 规则批量迁移为 compose template（运营手动按需创建）

---

## 关键不变量

### Invariant I-1: 引用而非拷贝
- Rule: `mail_compose_template_block` 只存 `ref_id`（QA 规则 ID 或回复片段 ID）和 `block_type`。渲染模板正文时，**实时**从 `qa_rule` / `reply_snippet` 表读取最新内容，绝不在 block 行中冗余存储引用源正文。
- Applies to: `MailComposeTemplateService.render()`、`MailComposeTemplateController.preview()`
- Violation consequence: 修改 QA 规则或回复片段后，模板正文不同步，运营看到的预览与实际发送不一致。
- 来源: original

### Invariant I-2: Block 顺序即渲染顺序
- Rule: 模板内容块按 `block_order ASC` 渲染。前端编辑器保存的顺序 → DB `block_order` → API 响应顺序 → 渲染正文拼接顺序 → 实际发送邮件正文，四者必须一致。
- Applies to: 前端保存接口、`MailComposeTemplateService.render()`、前端预览、`ManualExpertMailService.sendManualMail()`
- Violation consequence: 运营看到的顺序与实际发送顺序不一致（与 K-composed-reply-order-contract 同类问题）。
- 来源: K-composed-reply-order-contract

### Invariant I-3: 手动发送选项不含裸 QA 规则
- Rule: `ManualExpertMailService.listSendOptions()` 返回的列表中，`optionType` 只有 `TEMPLATE`（系统模板）和 `COMPOSE_TEMPLATE`（新 compose template）两种，不再有 `QA` 类型。
- Applies to: `ManualExpertMailService.listSendOptions()`、`ManualExpertMailService.compose()`
- Violation consequence: 运营在手动发送时看到裸 QA 规则，无法分辨来源（原始问题未解决）。
- 来源: original

### Invariant I-4: Tab 注册四联契约
- Rule: 新 Tab「邮件模板」必须四处同步注册：(1) `index.html` 侧栏 `.nav-tab[data-view="mail-templates"]`；(2) `index.html` `<section class="view" id="view-mail-templates">`；(3) `app.js` `viewMeta["mail-templates"]`；(4) `app.js` `refreshCurrentView()` 分支。同时移除原「QA 规则」和「回复片段」两个独立 Tab 的侧栏入口，其内容作为子 Tab 嵌入新 view 中。
- Applies to: `index.html`、`app.js`
- Violation consequence: Tab 切换报错或不加载内容。
- 来源: K-view-registration-triad

### Invariant I-5: 引用失效时的降级处理
- Rule: 如果 compose template 的某个 QA_RULE 或 REPLY_SNIPPET 块引用的源记录已被禁用（`enabled=false`），渲染和预览时**跳过该块并标注**（不抛异常、不发送空内容、不静默包含已禁用内容）。如果所有块都失效导致正文为空，则阻止发送并报错。
- Applies to: `MailComposeTemplateService.render()`、前端预览
- Violation consequence: 引用源被禁用后，模板仍发出不该发的内容或发出空邮件。
- 来源: original

### Invariant I-6: MailRecord.mailType 区分 compose template 路径
- Rule: 通过 compose template 手动发送的邮件，`mailType` 设为 `"COMPOSE_TEMPLATE"`，`matchedQaRuleId` 存实际拼接中的第一个 QA 规则 ID（如果有），所有关联的 QA 规则 ID 通过 `mail_record_qa_rule` 关联表记录（与 composed reply 一致）。
- Applies to: `ManualExpertMailService.sendManualMail()` 的 compose template 路径
- Violation consequence: 审计日志和监控无法区分发送路径。
- 来源: original

---

## 现状审计

### 手动发送选项（ManualExpertMailService）

- **当前逻辑** (`listSendOptions()` L31-58):
  - 从 `mail_template` 取 `fixedTemplateCodes`（INTRODUCTION, MEETING_INVITATION, MATERIAL_REMINDER）→ `TEMPLATE` 类型选项
  - 从 `qa_rule` 取全部 `enabled=1` 规则 → `QA` 类型选项
  - 两者合并返回

- **Write paths** (`sendManualMail()` L62-117):
  1. `compose()` → 根据 `optionType` 分发到 `composeTemplate()` 或 `composeQa()`
  2. `composeQa()` → 直接用 `rule.replyBody` 作为正文，`mailType="MANUAL_QA_REPLY"`
  3. `composeTemplate()` → 通过 `MailTemplateService.render()` 做变量替换
  4. 写入 `mail_record`（direction=OUTBOUND, triggeredBy=OPERATOR）
  5. 通过 `conversationStateService.transition()` 转换对话状态
  6. 更新 `mail_sender_account.lastSentAt`

- **Read paths**:
  1. 前端 `loadMailSendOptions()` → `GET /api/expert-contacts/mail-send-options` → `listSendOptions()`（有客户端缓存）
  2. 前端 `handleContactAction("send-manual-mail")` → `POST /api/expert-contacts/{id}/manual-mail`
  3. 批量发送 `POST /api/expert-contacts/batch-mail` → `sendBatchMail()` → 循环调用 `sendManualMail()`

### QA 规则管理

- **Schema**: `qa_rule` 表 — `id, category_id, keywords, match_mode, priority, reply_subject, reply_body, display_name, section_title, auto_reply_enabled, handoff_required, supersedes_children, enabled`
- **Write paths**: `QaRuleManagementService.createRule/updateRule/setRuleEnabled`
- **Read paths**:
  1. 自动匹配: `QaMatchService.match/matchAllRuleIds/suggestComposition` → `findAllEnabledOrdered()`
  2. 管理列表: `QaRuleManagementService.listRules()` → `findAllByOrderByPriorityAscIdAsc()`
  3. **手动发送选项: `ManualExpertMailService.listSendOptions()` → `findAllEnabledOrdered()`（本次将移除）**
  4. 组装回复: `PendingMailOperationService.sendManualComposedReply()` → `qaRuleRepository.findById()`
  5. 单封 QA 回复: `PendingMailOperationService.sendQaReply()` → `qaRuleRepository.findById()`
  6. AI 回复: `AiReplyDraftService` → `findAllEnabledOrdered()`
  7. 来信标签: 前端 `populateInboundAddTagQaOptions()` → `GET /api/qa/rules`

### 回复片段管理

- **Schema**: `reply_snippet` 表 — `id, snippet_type, content, display_order, is_default, enabled`
- **Write paths**: `ReplySnippetService.create/update/setEnabled/setDefault/delete`
- **Read paths**:
  1. 管理列表: `ReplySnippetService.listAll/listByType`
  2. Frame 解析: `ReplySnippetService.resolveManualFrame()` → 组装回复的 salutation/greeting/closing/ack
  3. ACK 解析: `ReplySnippetService.resolveAck(ackSnippetId)` → 组装回复/AI 回复

### 前端 Tab 结构

- **侧栏 nav-tab** (`index.html` L87-100):
  - `data-view="qa"` → "QA 规则"
  - `data-view="reply-snippets"` → "回复片段"
- **view section**:
  - `id="view-qa"` (L316) — QA 规则表格 + 编辑弹窗 + 审计面板
  - `id="view-reply-snippets"` (L362) — 按类型分面板的片段管理
- **viewMeta** (`app.js` L138-139): qa / reply-snippets 各一行
- **refreshCurrentView** (`app.js` L1252-1253): `loadQa()` / `loadReplySnippets()` 各一分支

### 交互点

1. **compose template block → qa_rule**: block 引用 qa_rule.id，QA 规则的增删改会影响模板渲染结果（I-1）
2. **compose template block → reply_snippet**: block 引用 reply_snippet.id，片段的增删改会影响模板渲染结果（I-1）
3. **compose template → ManualExpertMailService**: `listSendOptions()` 改为查 compose template；`sendManualMail()` 新增 compose template 分支（I-3）
4. **前端 Tab 注册**: 侧栏 nav-tab 移除旧 Tab + 新增合并 Tab；view section 合并；viewMeta 和 refreshCurrentView 同步更新（I-4）
5. **前端手动发送 cache**: `state.mailSendOptions` 缓存逻辑需要感知 compose template 变更

---

## 实现方案

### Phase A: 后端 — 数据模型 + CRUD + 渲染

#### Task A-1: DB 迁移
- 文件: `src/main/resources/db/migration/V60__create_mail_compose_template.sql`
- 创建两张表:

```sql
CREATE TABLE mail_compose_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    subject VARCHAR(255) NOT NULL COMMENT '邮件主题',
    description VARCHAR(500) NULL COMMENT '模板用途描述',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE mail_compose_template_block (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    block_order INT NOT NULL COMMENT '块排序（ASC）',
    block_type VARCHAR(30) NOT NULL COMMENT 'QA_RULE | REPLY_SNIPPET | CUSTOM_TEXT',
    ref_id BIGINT NULL COMMENT 'qa_rule.id 或 reply_snippet.id，CUSTOM_TEXT 时为 NULL',
    custom_text TEXT NULL COMMENT 'CUSTOM_TEXT 时存正文',
    FOREIGN KEY (template_id) REFERENCES mail_compose_template(id) ON DELETE CASCADE
);
```

- 守护: I-1（block 只存 ref_id，不存正文副本）

#### Task A-2: Domain + Repository
- 文件: `src/main/kotlin/com/weibo/talentintroduction/template/domain/MailComposeTemplate.kt`（含 `MailComposeTemplate` + `MailComposeTemplateBlock` 两个 data class）
- 文件: `src/main/kotlin/com/weibo/talentintroduction/template/repository/MailComposeTemplateRepository.kt`（含 `MailComposeTemplateRepository` + `MailComposeTemplateBlockRepository` 两个接口）

```kotlin
// Domain
@Table("mail_compose_template")
data class MailComposeTemplate(
    @Id val id: Long? = null,
    val templateName: String,
    val subject: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)

@Table("mail_compose_template_block")
data class MailComposeTemplateBlock(
    @Id val id: Long? = null,
    val templateId: Long,
    val blockOrder: Int,
    val blockType: String,  // QA_RULE | REPLY_SNIPPET | CUSTOM_TEXT
    val refId: Long? = null,
    val customText: String? = null
)
```

- Repository 查询:
  - `MailComposeTemplateRepository`: `findAllByOrderByIdAsc()`, `findAllByEnabledTrueOrderByIdAsc()`
  - `MailComposeTemplateBlockRepository`: `findAllByTemplateIdOrderByBlockOrderAsc(templateId)`, `deleteAllByTemplateId(templateId)`
- 守护: I-2（block 查询按 blockOrder ASC）

#### Task A-3: Service — CRUD + 渲染
- 文件: `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`
- 功能:
  - `listAll()`: 返回全部 compose template（含 blocks）
  - `listEnabled()`: 返回启用的 compose template（手动发送用）
  - `getById(id)`: 获取单个 compose template + blocks
  - `create(command)`: 创建模板 + blocks（事务）
  - `update(id, command)`: 更新模板，先 `deleteAllByTemplateId` 再重新插入 blocks（事务）
  - `setEnabled(id, enabled)`: 启用/禁用
  - `delete(id)`: 删除模板（cascade 删除 blocks）
  - `render(id)`: **实时渲染**正文 — 按 blockOrder 遍历 blocks，逐块解析:
    - `QA_RULE` → `qaRuleRepository.findById(refId)` → 取 `replyBody`（如果 rule 已禁用则跳过并标注）
    - `REPLY_SNIPPET` → `replySnippetRepository.findById(refId)` → 取 `content`（如果已禁用则跳过）
    - `CUSTOM_TEXT` → 取 `customText`
    - 各块用 `\n\n` 拼接
  - `preview(id)`: 返回渲染结果 + 每个块的来源标注（用于前端预览）
- 守护: I-1（render 时实时查源表）、I-2（按 blockOrder 渲染）、I-5（disabled 跳过处理）
- 依赖: `QaRuleRepository`、`ReplySnippetRepository`（只读引用）

#### Task A-4: Controller — REST API
- 文件: `src/main/kotlin/com/weibo/talentintroduction/template/controller/MailComposeTemplateController.kt`
- 端点:
  - `GET /api/compose-templates` — 列表（含 blocks 详情 + 引用源 displayName）
  - `POST /api/compose-templates` — 创建
  - `PUT /api/compose-templates/{id}` — 更新
  - `POST /api/compose-templates/{id}/enable` — 启用
  - `POST /api/compose-templates/{id}/disable` — 禁用
  - `DELETE /api/compose-templates/{id}` — 删除
  - `GET /api/compose-templates/{id}/preview` — 预览渲染结果（含逐块来源标注）

### Phase B: 后端 — 手动发送接入

#### Task B-1: 修改 ManualExpertMailService
- 文件: `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt`
- 改动 1 — `listSendOptions()`:
  - 保留 `templateOptions`（INTRODUCTION / MEETING_INVITATION / MATERIAL_REMINDER）
  - 移除 `qaOptions`（不再查 `qaRuleRepository.findAllEnabledOrdered()`）
  - 新增 `composeTemplateOptions`：查 `MailComposeTemplateService.listEnabled()` → 映射为 `ManualMailOption(optionType="COMPOSE_TEMPLATE", optionValue=id, optionName=templateName, subject=subject)`
  - 返回 `templateOptions + composeTemplateOptions`
- 改动 2 — `ManualMailOptionType` 枚举新增 `COMPOSE_TEMPLATE`
- 改动 3 — `compose()` 方法新增 `COMPOSE_TEMPLATE` 分支:
  - 调用 `MailComposeTemplateService.render(templateId)` 获取渲染后正文
  - 返回 `ManualComposedMail(mailType="COMPOSE_TEMPLATE", mail=..., matchedQaRuleId=firstQaRuleId)`
- 改动 4 — `sendManualMail()` 中 mail_record 保存后，如果 mailType 是 `COMPOSE_TEMPLATE`，遍历模板 blocks 中所有 QA_RULE 块的 refId，写入 `mail_record_qa_rule` 关联表
- 改动 5 — `nextStatus()` 新增 `"COMPOSE_TEMPLATE"` 分支 → 保持当前状态不变（运营手动控制）
- 守护: I-2（渲染顺序）、I-3（不含裸 QA 规则）、I-6（mailType + 关联表）

### Phase C: 前端 — Tab 合并 + 模板编辑器 + 手动发送

#### Task C-1: Tab 合并（index.html）
- 文件: `src/main/resources/static/index.html`
- 改动:
  - 移除侧栏 `data-view="qa"` 和 `data-view="reply-snippets"` 两个 `.nav-tab` 按钮
  - 新增侧栏 `data-view="mail-templates"` 按钮，文案「邮件模板」
  - 移除 `<section id="view-qa">` 和 `<section id="view-reply-snippets">`
  - 新增 `<section id="view-mail-templates">` 内含:
    - 子 Tab 导航栏（三个按钮: QA 规则 / 回复片段 / 邮件模板）
    - 三个子面板（`.sub-panel`），分别包含原 view-qa 和 view-reply-snippets 的全部 HTML + 新 compose template 面板
- 守护: I-4（四联契约）

#### Task C-2: Tab 逻辑 + 模板编辑器（app.js）
- 文件: `src/main/resources/static/app.js`
- 改动:
  - `viewMeta`: 移除 `qa` / `reply-snippets`，新增 `"mail-templates": ["邮件模板", "统一管理 QA 规则、回复片段与邮件模板。"]`
  - `refreshCurrentView()`: 移除 `view === "qa"` / `view === "reply-snippets"` 分支，新增 `view === "mail-templates"` 分支调用 `loadMailTemplatesView()` → 同时加载 `loadQa()` + `loadReplySnippets()` + `loadComposeTemplates()`
  - 子 Tab 切换逻辑: `.sub-tab` 按钮切换 `.sub-panel` 显隐
  - 新增 `loadComposeTemplates()`: `GET /api/compose-templates` → 渲染模板列表表格（名称、主题、内容块摘要 pill、启用状态、操作按钮）
  - 新增模板编辑器（modal）:
    - 模板名称 input
    - 邮件主题 input
    - 内容块列表（可拖拽排序），每个块:
      - 类型选择: QA 规则 / 回复片段 / 自定义文本
      - QA 规则: 下拉选择已启用的 QA 规则
      - 回复片段: 下拉选择已启用的回复片段
      - 自定义文本: textarea
      - 删除按钮 + 拖拽手柄
    - 「+ 添加内容块」按钮
    - 邮件预览区域（调用 `GET /api/compose-templates/{id}/preview` 或本地预览）
  - 保存时: `POST/PUT /api/compose-templates` 提交 blocks 数组，`blockOrder` 从数组索引生成
- 改动 — 手动发送下拉框:
  - `state.mailSendOptions` 的缓存在切换到 `mail-templates` view 时清空: `state.mailSendOptions = []`
  - 下拉框渲染改为 `<optgroup>` 分组:
    - `<optgroup label="系统模板">` → TEMPLATE 类型
    - `<optgroup label="邮件模板">` → COMPOSE_TEMPLATE 类型
- 守护: I-2（blocks 数组索引 → blockOrder）、I-4（Tab 注册）

---

## 变更文件清单

| # | 文件路径 | 操作 | 说明 |
|---|---------|------|------|
| 1 | `src/main/resources/db/migration/V60__create_mail_compose_template.sql` | 新增 | 建表 |
| 2 | `src/main/kotlin/.../template/domain/MailComposeTemplate.kt` | 新增 | Domain (2 data class) |
| 3 | `src/main/kotlin/.../template/repository/MailComposeTemplateRepository.kt` | 新增 | Repository (2 interface) |
| 4 | `src/main/kotlin/.../template/service/MailComposeTemplateService.kt` | 新增 | CRUD + 渲染 service |
| 5 | `src/main/kotlin/.../template/controller/MailComposeTemplateController.kt` | 新增 | REST API controller |
| 6 | `src/main/kotlin/.../mail/service/ManualExpertMailService.kt` | 修改 | 接入 compose template，移除裸 QA 选项 |
| 7 | `src/main/resources/static/index.html` | 修改 | Tab 合并 + 新 compose template UI |
| 8 | `src/main/resources/static/app.js` | 修改 | Tab 逻辑 + 模板编辑器 + 手动发送改造 |

共 8 文件（5 新增 + 3 修改），2 个子系统（后端 6 文件 + 前端 2 文件）。

---

## 验收标准

- **I-1 (引用不拷贝)**: 创建一个 compose template 引用某 QA 规则 → 预览正文包含该规则 replyBody → 修改该 QA 规则的 replyBody → 重新预览，正文已更新。数据库 `mail_compose_template_block` 表中无 reply_body 列。
- **I-2 (块顺序一致)**: 创建模板含 3 个块（A→B→C）→ 预览按 A→B→C 拼接 → 在编辑器拖拽改为 C→A→B → 保存 → 预览按 C→A→B 拼接 → 实际发送邮件正文按 C→A→B 拼接。
- **I-3 (无裸 QA)**: 调用 `GET /api/expert-contacts/mail-send-options` → 返回中无 `optionType="QA"` 的项 → 专家详情手动发送下拉框无裸 QA 规则选项。
- **I-4 (Tab 注册)**: 点击侧栏「邮件模板」→ 视图切换正常、标题显示正确、内容加载 → 切换子 Tab（QA 规则 / 回复片段 / 邮件模板）各面板正常显示 → 原有 QA 规则和回复片段的全部管理功能（新建/编辑/启停）无回归。
- **I-5 (引用失效)**: 创建模板引用某 QA 规则 → 禁用该 QA 规则 → 预览模板 → 该块被跳过或标注「已禁用」→ 如果所有块都失效，发送时返回明确错误。
- **I-6 (mailType 审计)**: 通过 compose template 手动发送 → `mail_record.mail_type` 为 `"COMPOSE_TEMPLATE"` → `mail_record_qa_rule` 表有对应关联行。
- **集成**: 自动回复管线不受影响 — 来信自动匹配、自动 QA 回复、组装回复工作台的功能均无回归。
