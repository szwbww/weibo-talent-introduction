# QA 重构 01：模板与 QA 事实源解耦

## 需求描述

在修改 QA 正文语义前，把现有邮件模板中的 `QA_RULE` 块无损快照为 `CUSTOM_TEXT`，并禁止新建 `QA_RULE` 模板块。可观察结果：运营修改 QA 后，项目介绍邮件内容不随之变化。

必须不变：

- `INTRODUCTION` 当前 subject、text、html、变量渲染结果逐字不变。
- `REPLY_SNIPPET`、`CUSTOM_TEXT`、subject variants、稳定 variant seed 行为不变。
- 已保存模板的块顺序不变。

Out of scope：修改项目介绍邮件内容；修改 QA schema；删除 `ComposeBlockType.QA_RULE` 的兼容读取分支。

## 关键不变量

### Invariant I-1：模板快照使用主正文
- Rule：迁移把 `QA_RULE.ref_id` 对应的 `qa_rule.reply_body` 原样写入同一块的 `custom_text`，同时设 `block_type='CUSTOM_TEXT'`、`ref_id=NULL`；不 trim、不渲染变量、不重排。
- Applies to：V78 迁移；模板预览；`render/renderByCode`。
- Violation consequence：介绍信内容漂移或 `${...}` 在迁移时被错误固化。
- 来源：original；K-manual-send-options-sources。

### Invariant I-2：变体前置条件
- Rule：迁移前 `content_variant WHERE owner_type='QA_RULE'` 必须为 0；不为 0 时停止发布，禁止随意选择某个变体快照。
- Applies to：上线前检查、V78 人工验收。
- Violation consequence：不同专家原本命中的正文被静默统一。
- 来源：original；K-content-variant-input-read-contract。

### Invariant I-3：新写路径禁止 QA_RULE
- Rule：`create/update/previewDraft` 提交 `blockType=QA_RULE` 均返回 400；前端块类型只提供 `REPLY_SNIPPET`、`CUSTOM_TEXT`，新增块默认 `CUSTOM_TEXT`。
- Applies to：`MailComposeTemplateService.validateBlockCommand`、前端 collect/render。
- Violation consequence：QA 再次成为介绍邮件/批量模板的隐式正文源。
- 来源：original。

### Invariant I-4：兼容只读
- Rule：`resolveBlocks()` 暂时保留历史 `QA_RULE` 的只读渲染；不得继续解析 QA 内容变体；仅用于迁移遗漏/回滚版本产生的数据诊断。
- Applies to：`MailComposeTemplateService.resolveBlocks`。
- Violation consequence：部署中残留历史块时模板直接不可渲染，或继续存在随机正文。
- 来源：original；K-qa-replybody-outbound-sites。

## 样式契约

### S-1：模板块编辑行
- 复用：`.compose-template-block-row`、`.block-drag-handle`、`.block-order`、`.block-type-select`、`.block-ref`、`.block-actions`，定义见 `src/main/resources/static/styles.css:6272-6327`。
- 新增：无新 class、无 CSS。
- DOM 结构：保持以下骨架，只删除 `QA_RULE` option/ref 分支，默认类型改 `CUSTOM_TEXT`：

```html
<div class="compose-template-block-row" data-block-index="0" draggable="true">
  <span class="block-drag-handle">⋮⋮</span>
  <span class="block-order">#1</span>
  <select class="block-type-select" data-field="blockType">
    <option value="REPLY_SNIPPET">回复片段</option>
    <option value="CUSTOM_TEXT">自定义文本</option>
  </select>
  <div class="block-ref">
    <!-- REPLY_SNIPPET 分支只渲染下列 select；CUSTOM_TEXT 分支只渲染其后的编辑器 -->
    <select data-field="refId"><option value="">请选择回复片段</option></select>
    <div class="var-editor-wrap">
      <div class="var-editor-toolbar">
        <div class="var-insert-wrap">
          <button type="button" class="var-insert-btn" data-var-insert-target="composeBlockCustomText-0">+ 插入变量 ▾</button>
          <div class="var-insert-menu" hidden></div>
        </div>
      </div>
      <textarea id="composeBlockCustomText-0" data-field="customText" rows="4" placeholder="输入自定义文本"></textarea>
    </div>
  </div>
  <div class="block-actions">
    <button type="button" class="button small" data-action="move-compose-block-up" data-index="0" disabled>↑</button>
    <button type="button" class="button small" data-action="move-compose-block-down" data-index="0">↓</button>
    <button type="button" class="button small danger" data-action="remove-compose-block" data-index="0">×</button>
  </div>
</div>
```

- 禁止项：修改 `styles.css`；新增 inline style；改动块行尺寸、间距、颜色、hover/disabled 状态。

## 现状审计

### `mail_compose_template_block`
- Schema/mapping：V61；`block_type VARCHAR(30)`，`ref_id BIGINT NULL`，`custom_text TEXT NULL`；`ref_id` 无外键，QA 引用可悬空。
- Write paths：
  1. `V62__unify_mail_templates.sql` — 删除并重建系统模板块为 `CUSTOM_TEXT`。
  2. `V71__update_material_reminder_template.sql` — 更新材料提醒模板块。
  3. `MailComposeTemplateService.create/update -> saveBlocks()` — 先保存模板，再逐块 `blockRepository.save()`。
  4. `MailComposeTemplateService.delete()` — 级联/显式删除块。
- Read paths：
  1. `MailComposeTemplateService.get/list` — 返回块详情和引用显示名。
  2. `render/renderByCode/previewDraft` — `resolveBlocks()` 解析 `QA_RULE/REPLY_SNIPPET/CUSTOM_TEXT`。
  3. `ManualExpertMailService`、`IntroductionMailComposer`、会议与批量发送调用 render 结果，不直接读块表。
- Production：1 个 `QA_RULE` 块，`INTRODUCTION` 模板引用规则 1；引用有效；QA 变体 0。
- Interaction points：V78 写块类型/正文 → render/preview/IntroductionMailComposer 读；前端写块 DTO → service validate/save 读写。

### `qa_rule`（本计划只读）
- Schema/mapping：V1 + 后续迁移；快照源为 `reply_body TEXT NOT NULL`。
- Write paths：Flyway 历史迁移、`QaRuleManagementService.create/update/setEnabled/delete`。（来源：K-qa-rule-runtime-vs-migration-writes）
- Read paths：本计划只在 V78 和兼容 `resolveBlocks()` 中读取 `reply_body`。
- Interaction points：迁移完成后，QA 运行时更新不得影响模板块。

### 前端样式盘点
- 可复用 class：模板编辑器 `.compose-template-editor` (`styles.css:6048-6051`)；卡片 `.compose-template-card` (`6104-6110`)；块行 `6272-6327`；通用表单 `827-849`。
- 设计基准 token：主色 `#2563eb`、正文 `#1e293b`、边框 `rgba(15,23,42,.11)`、小圆角 `7px`，见 `styles.css:1-59`。
- DOM 结构约定：`composeTemplateBlockRowHtml()` 生成块行；`collectComposeTemplateBlocksFromForm()` 读取 `[data-field]`，现状见 `app.js:7371-7417`。
- 改动前基线：类型 select 当前含 `QA_RULE/REPLY_SNIPPET/CUSTOM_TEXT`，新增块默认 `QA_RULE`。

## 实现方案

### T1：部署前只读门禁
- 文件：无代码变更；执行记录写入发布单。
- 执行 SQL：

```sql
SELECT COUNT(*) FROM content_variant WHERE owner_type='QA_RULE';
SELECT COUNT(*) FROM mail_compose_template_block WHERE block_type='QA_RULE';
SELECT COUNT(*)
FROM mail_compose_template_block b
LEFT JOIN qa_rule q ON q.id=b.ref_id
WHERE b.block_type='QA_RULE' AND q.id IS NULL;
```

- 断言依次为 `0 / 1 / 0`；否则停止。遵守 I-1、I-2。

### T2：V78 快照迁移
- 文件：`src/main/resources/db/migration/V78__decouple_compose_templates_from_qa_rule.sql`
- 用单条 join update 原位转换所有有效 `QA_RULE` 块；不创建新块、不改变 `block_order/template_id`。
- 迁移后断言 `block_type='QA_RULE'` 为 0；迁移文件不修改 V61/V62/V71。遵守 I-1、I-2。

### T3：后端禁止新写、保留只读兼容
- 文件：`src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`
- `validateBlockCommand()` 对 `QA_RULE` 明确抛 `IllegalArgumentException("QA_RULE blocks are read-only and cannot be created")`。
- `previewDraft()` 同样通过 validate 失败，不允许浏览器绕过。
- `resolveBlocks()` 保留旧块读取，但固定 `useVariants=false`，并在 preview skipReason/日志路径标记 legacy；其他 block 分支不变。遵守 I-3、I-4。

### T4：前端移除 QA 模板块入口
- 文件：`src/main/resources/static/app.js`
- `composeBlockTypeLabels` 可保留 `QA_RULE` 仅用于历史详情显示。
- `composeTemplateBlockRowHtml()` 删除 QA option/ref 生成，默认 `CUSTOM_TEXT`。
- `collectComposeTemplateBlocksFromForm()` 默认 `CUSTOM_TEXT`。
- `collectComposeTemplatePreviewSampleText()` 保留历史 QA 只读预览分支，避免查看旧缓存时报错。
- 遵守 I-3、S-1。

### T5：测试
- 文件：
  - `src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt`
  - `src/test/js/composeTemplatePreview.test.js`
- 覆盖：create/update/previewDraft 拒绝 QA；legacy render 主正文且不选变体；CUSTOM_TEXT 快照变量在 render 时解析；前端不出现 QA option且默认 CUSTOM_TEXT。遵守 I-1..I-4、S-1。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V78__decouple_compose_templates_from_qa_rule.sql` | 新增原位快照迁移 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt` | 禁止新 QA block、保留只读兼容 |
| 3 | `src/main/resources/static/app.js` | 移除 QA block 编辑入口 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt` | 后端回归 |
| 5 | `src/test/js/composeTemplatePreview.test.js` | 前端契约回归 |

共 5 个文件、2 个子系统（template + frontend），符合限制。

## 验收标准

- I-1：迁移前后对 `INTRODUCTION` 执行同一 preview，subject/raw/rendered text/html 逐字相同；块 ID/order/templateId 不变。
- I-2：QA 变体非 0 时发布单不可继续；测试 fixture 明确覆盖该门禁说明。
- I-3：后端三条写入口对 QA_RULE 返回 400；前端源码不含 `<option value="QA_RULE">`。
- I-4：直接构造 legacy block 调 render 仍可得到主正文，且 mock `ContentVariantService.resolveBody(QA_RULE,...)` 不被调用。
- S-1：`styles.css` diff 为空；块行 DOM 除 QA option/ref 删除外与契约一致；无新增 inline style/class。
- 集成：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=MailComposeTemplateServiceTest,IntroductionMailComposerTest test
node --test src/test/js/composeTemplatePreview.test.js
```

## 人工验收清单

### A-1：介绍信输出回归
- 前置条件：选择一个已有联系专家与启用账号；保存发布前 INTRODUCTION 预览 subject/text/html。
- 操作步骤：1. 应用 V78；2. 重新打开同一模板；3. 用同一专家和账号预览。
- 预期结果：subject/text/html 与发布前逐字一致；模板块显示“自定义文本”，顺序仍为 `#1`。
- 覆盖：I-1、需求“不改变介绍信”。

### A-2：模板编辑器不再提供 QA
- 前置条件：进入“邮件模板→组合模板”，新建模板。
- 操作步骤：1. 新增内容块；2. 打开类型下拉。
- 预期结果：只有“回复片段”“自定义文本”；默认“自定义文本”；保存后刷新仍一致。
- 覆盖：I-3、S-1。

### A-3：回复片段与变体回归
- 前置条件：准备一个含回复片段变体的测试模板。
- 操作步骤：分别用两个稳定 seed 预览。
- 预期结果：仍按原稳定 seed 选择回复片段变体；本计划未改变其内容和顺序。
- 覆盖：must-NOT-change、I-4。

### A-4：跨路径快照验证
- 前置条件：V78 已执行。
- 操作步骤：1. 修改原规则 1 的 `reply_body` 测试副本；2. 再预览 INTRODUCTION。
- 预期结果：介绍信仍显示 V78 快照文本，不显示测试副本内容。
- 覆盖：`qa_rule` 写 → 模板 render 读的解耦 interaction point。
