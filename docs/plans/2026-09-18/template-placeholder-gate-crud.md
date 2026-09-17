# 邮件模板变量门禁与 CRUD

本计划先执行；跨计划约束见同目录 `00-university-email-template-main.md`，关联的批量任务“研究方向有/无/不限”见 `batch-research-direction-filter.md`。两计划各自可验证。仅写计划，不创建模板、任务或导入 500 人。

## 需求描述

用户可在现有邮件模板页面自行新建介绍邮件模板，查看并插入全部已支持变量，完成新增、查看、编辑、启停、删除和预览。模板主题及实际正文中的 `${key}` 表示该 key 必填、缺值阻止发送；`${key|非空默认值}` 缺值时使用默认值、不形成门禁。`${institution}` 使用现有变量，不新增字段。批量任务的“按模板门禁”预筛与发送时硬门禁读取同一模板规则。

必须保持：现有发件变量取值、模板正文块顺序、已绑定任务的模板引用、未选择门禁预筛时的预估行为、其他 QA/回复片段编辑器的占位符校验。范围外：新建任何模板/批量任务、导入或分类 500 人、邮箱验证、修改专家 ES 文档、设计新的变量或群发策略。

## 关键不变量

### Invariant I-1：占位符是门禁唯一来源
- Rule：对当前模板主题和当前可用正文，`${key}` 是必填；`${key|非空默认值}` 不是必填；`|` 后为空、未知 key、残缺 token 在模板保存时拒绝。按首次出现顺序去重。旧 `required_keys` 不再决定新规则，也不得与占位符叠加。
- Applies to：模板 create/update、gate-fields、批量预筛、介绍邮件和人工选模板发送。
- Violation consequence：新模板不生效，或有默认值的旧模板误挡人。
- 来源：original。

### Invariant I-2：实际发送精确、预筛保守
- Rule：发送时按本次实际选中的正文/变体里的无默认值 token 和变量值判定缺失，空串/纯空白均算缺失；原有最终 `${...}` 残留拦截继续保留。ES 预筛仅对每次渲染必需且在 `ALLOWED_HAS_FIELDS` 内的字段执行，不可把只出现在某一片段变体中的 key 当所有人都必需；不可预筛的 key 在发送端判定。
- Applies to：`effectiveRequiredKeys`、`requiredEsFields`、`PersonalizationGateService.evaluate`、`ManualInitialOutreachService.resolveScope` 既有调用。
- Violation consequence：错误排除收件人或漏过缺值邮件。
- 来源：original。

### Invariant I-3：模板 CRUD 不破坏引用
- Rule：新建介绍邮件模板必须能被批量任务 `resolveMailType` 识别为 `INTRODUCTION`。编辑保留 id、既有 mailType/templateCode 和未改字段；删除未被任务引用的模板及块成功；被 `batch_send_task_config.template_id` 引用的模板删除要给出明确冲突，不清空任务引用、不吞掉 DB 错误。启停状态和列表/预览即时一致。
- Applies to：`MailComposeTemplateService` 的 create/update/delete/setEnabled、现有列表和编辑器调用。
- Violation consequence：用户新建模板无法选入任务、任务悬空或 CRUD 失败不透明。
- 来源：original。

### Invariant I-4：变量展示与存储对齐
- Rule：插入菜单完整展示 `/api/qa/template-variables-meta` 返回的所有 key（包括 `institution`、`primaryResearchField`），模板编辑器插入可形成必填的 `${key}`，并允许用户写 `${key|默认值}`；前端仅放宽模板编辑器，QA/回复片段原有规则不变。保存后重开，原文和块顺序不丢失。
- Applies to：`app.js`、`index.html`、服务端模板校验。
- Violation consequence：门禁无法显式配置或变量编辑丢失。
- 来源：original。

## 样式契约

### S-1：模板变量菜单及提示
- 复用：`styles.css:5857-5929` 的 `.var-chip`、`.var-insert-wrap`、`.var-insert-btn`、`.var-insert-menu`、`.var-insert-group-label`、`.var-insert-group`、`.var-validation-hint`；`styles.css:6449` 的 `.compose-template-editor`。本计划不改 CSS、不新增 class。
- DOM 结构：保留 `index.html:1932-1940` 的 `.var-editor-wrap > .var-editor-toolbar > .var-insert-wrap > button.var-insert-btn + div.var-insert-menu`，以及 `app.js:9852-9870` 内容块中同一骨架；只改现有提示文字和按钮插入行为。上述复用 class 还见 `index.html:1839-1840,1883-1884` 的 QA/片段编辑器，不就地改其规则。
- 禁止项：inline style、新 class、修改既有 class 样式。

### S-2：批量任务门禁展示
- 复用：`styles.css:9453-9509` 的 `.batch-gate-field`、`.batch-gate-hint`、`.batch-gate-keys`、`.batch-gate-keys-label`、`.batch-gate-keys-dropped` 和 `.tag-chip`。本计划只改 `app.js` 的文案与数据映射。
- DOM 结构：保留 `index.html:1310-1321,1521-1532` 的 `#editorFieldGateFilter`、`#manualFieldGateFilter`，各自包含 checkbox、hint、keys；不新增 DOM。
- 禁止项：inline style、新 class、修改既有 class 样式。

## 现状审计

### `mail_compose_template` / `mail_compose_template_block`（MySQL）
- Schema：`V61__create_mail_compose_template.sql:1-20` 建主表及块表，块表外键 `ON DELETE CASCADE`；`V84__add_required_keys_to_compose_template.sql:5-7` 的 `required_keys` 可空；`V72__create_batch_send_task_config.sql:29-31` 有任务到模板的非级联外键；`MailComposeTemplate.kt:7-31` 对应实体。
- 运行时写路径：`MailComposeTemplateService.kt:52-114,413-425` 的 create、update（先删块再写）、setEnabled、delete；历史迁移写路径：`V62`, `V71`, `V78`, `V87`, `V88`, `V122`。本计划不改已执行迁移。
- 读路径：同 service 的 list/get/render/preview/previewDraft/gate-fields；`MailComposeTemplateController.kt:17-65` 暴露 CRUD 与预览；`BatchSendTaskConfigService.kt:349-368` 读取 mailType/enabled；`IntroductionMailComposer.kt:20-32` 和 `ManualExpertMailService.kt:229-242` 读必填 key；`ManualInitialOutreachService.kt:426-441` 读预筛字段；`app.js:9658-10116` 读列表和 CRUD 结果。
- 交互点：编辑器 POST/PUT → DB → 列表/预览/任务模板选择；模板内容 → gate-fields → 批量预估；模板内容 → composer → SMTP 前门禁；删除模板 → 任务外键。
- 代码事实：`MailComposeTemplateService.kt:142-169` 只解析 `required_keys`，新建/更新并不写此列；`PersonalizationGateService.kt:46-54` 只检查“使用了 fallback”而非无默认值缺失；`MailComposeTemplateService.kt:56-72` 新模板 mailType 来源可为 null，`BatchSendTaskConfigService.kt:363-367` 拒绝不支持类型。

### 变量、片段和 ES（本计划只读）
- `MailPlaceholderService.kt:24-90,93-164` 声明变量及 `institution→institution`、`primaryResearchField→researchFields`；`MailVariableService.kt:110-154` 已从专家 `institution` 和 `researchFields` 取值。无需新字段。
- `reply_snippet` 见 `V47`，`content_variant` 见 `V67`；`ReplySnippetService`/`ContentVariantService.kt:16-69` 是片段/变体写路径，模板 `resolveBlocks` 在 `MailComposeTemplateService.kt:435-527` 读取当前启用片段和选中变体。模板保存不会复制这些正文，故门禁不能只在保存时缓存。
- ES `orcid_info_candidate.json:1-50` 为 `dynamic:false`，`researchFields` 为 keyword、`institution` 为 text；`ExpertSearchService.kt:35-77` 只允许 6 个存在性预筛字段。本计划不写 ES、不改映射。
- `app.js:2580-2877` 菜单由元数据生成，`validatePlaceholderText` 目前把 nullable 且无默认值视为非法，`placeholderDefaultFallback` 缺 `primaryResearchField`；`index.html:1953` 仅提示默认值。

### 前端样式盘点
- 可复用 class：见 S-1/S-2，逐字样式规则位于上述 `styles.css` 行号；不修改它们。
- 设计基准 token：`styles.css:1-76` 主色 `#1e40af`、hover `#1e3a8a`、文字弱化 `#94a3b8`、强边框 `#cbd5e1`、错误色 `#be123c`、小圆角 `7px`、菜单 z-index `20`、阴影 `0 10px 28px -8px rgba(15, 23, 42, 0.14), 0 2px 6px rgba(15, 23, 42, 0.05)`；暗色覆盖在 `styles.css:9706-9745`。
- DOM 结构约定、改动前基线：`index.html:1932-1955` 现有 subject 输入和正文块容器、提示原文“支持默认值语法 ${变量名|默认值}”；`app.js:9852-9870` 现有块行骨架；`styles.css:5857-5929,9453-9509` 为对应逐字 CSS 基线。按 S-1/S-2 只改文案和 JS，不改骨架或 CSS。

## 实现方案

1. 占位符语义（I-1/I-2/I-4）：在 `MailPlaceholderService.kt` 加单一解析入口，区分无 `|`、非空默认值、空默认值、未知 key，保持原 QA/片段 `requireValidPlaceholders` 旧语义。`MailComposeTemplateService.kt` 从实时主题、启用块、片段/变体求发送必填 key；`requiredEsFields` 仅返回所有可能渲染均必需且可映射字段；不新增 DB 字段、不维护第二份 key 清单。`PersonalizationGateService.kt` 对本次 rawTexts 中无默认值的必填 key 查实际变量空白；默认值 key 不挡；最终残留检查保留。
2. 模板 CRUD（I-3/I-4）：`MailComposeTemplateService.kt` 新建介绍模板写 `mailType=INTRODUCTION`；编辑保留已有 mailType，保存前校验主题和自定义块 token；未引用删除照旧，被任务外键引用时捕获约束错误并返回明确业务错误（沿用现有全局 `IllegalArgumentException→400`，不改全局异常体系）。保持事务回滚、块顺序和启停。
3. 编辑器（I-1/I-4/S-1/S-2）：`app.js` 只在 `#composeTemplateForm` 放宽无默认值变量并使插入按钮写 `${key}`；变量菜单仍从 metadata 全量渲染；更新模板保存原 mailType，新增模板显式提交 `INTRODUCTION`。`index.html` 现有提示改为“`${变量名}` 必填；`${变量名|默认值}` 可缺省”；批量门禁无字段提示去掉 `required_keys` 术语，保持原 DOM/CSS。
4. 验证（I-1 至 I-4）：在清单中的 Kotlin/JS 测试覆盖旧模板有默认值、新模板裸变量、片段动态更新、变体安全预筛、空默认值/未知 key、CRUD 往返、在用删除、`institution`、变量完整性；运行定向测试及 JS 测试。

## 变更文件清单

| # | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt` | 单一 token 解析与模板校验 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt` | 动态门禁、CRUD |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateService.kt` | 发送时缺值判定 |
| 4 | `src/main/resources/static/app.js` | 编辑器插入/校验、门禁文案 |
| 5 | `src/main/resources/static/index.html` | 现有提示文案 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt` | 模板服务及 CRUD |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateServiceTest.kt` | 发送门禁 |
| 8 | `src/test/js/composeTemplatePreview.test.js` | 前端变量/CRUD 回归 |
| 9 | `src/test/js/gateTemplateFilter.test.js` | 批量门禁展示/预筛回归 |

## 验收标准

- I-1：定向测试断言 subject/body 的 `${institution}` + `${primaryResearchField|your research area}` 得 requiredKeys=`[institution]`；去掉 `|...` 后 requiredKeys 含 `primaryResearchField`；旧 `required_keys` 值不影响结果。
- I-2：变量缺失时裸 token 抛 `PersonalizationGateException`；有默认值时正常渲染；片段变体仅部分包含必填 key 时 ES 预筛不提前排除，命中该变体的发送仍硬拦；无占位符残留出 SMTP。
- I-3：create→list/get→update→preview→enable/disable→delete 测试；新增模板 `mailType=INTRODUCTION`；被任务引用删除得到明确错误且原模板/任务仍存在。
- I-4：元数据全部 key 均进入菜单，`institution`/`primaryResearchField` 可插入裸 token；模板保存重开文本与块顺序相同；QA/片段无默认值校验仍按旧规则。
- S-1/S-2：diff 仅改现有提示/JS，无 CSS、新 class、inline style 或 DOM 骨架改动；菜单和门禁区域目测与基线一致。
- 运行：相应 Kotlin 定向测试及 `src/test/js/composeTemplatePreview.test.js`、`gateTemplateFilter.test.js`；最终 `git diff --check`。

## 人工验收清单

### A-1：新增与变量完整性
- 前置条件：测试环境可访问“邮件模板”；有已启用介绍邮件任务但不执行发送。
- 操作步骤：1. 新建模板；2. 打开主题和自定义正文的“插入变量”；3. 对照接口 `/api/qa/template-variables-meta` 的 key 清单；4. 插入 `institution` 与 `primaryResearchField`，主题写 `Hello ${institution}`，正文写 `${primaryResearchField|your research area}` 并保存。
- 预期结果：菜单含接口全部 key；保存后列表出现模板；重开仍有原文、块顺序；模板可在介绍邮件任务模板下拉中选择。
- 覆盖：I-3/I-4/S-1，模板写→列表/任务读。

### A-2：门禁变化与预览
- 前置条件：A-1 模板；一名 `institution` 有值、`researchFields` 空的测试专家。
- 操作步骤：1. 调 `/api/compose-templates/{id}/gate-fields`；2. 开模板预览选该专家；3. 将正文改为 `${primaryResearchField}` 保存；4. 再查 gate-fields 和批量任务“按模板门禁”预估。
- 预期结果：改前 `requiredKeys=[institution]` 且研究方向用默认值；改后 `requiredKeys` 含 `primaryResearchField`、`esFields` 含 `researchFields`，预估排除该专家，实际发送前也拦截。
- 覆盖：I-1/I-2/S-2，模板写→gate-fields→ES 预筛/发送。

### A-3：CRUD 与引用保护
- 前置条件：A-1 模板已绑定一条测试批量任务。
- 操作步骤：1. 编辑名称并禁用、重开查看；2. 启用；3. 尝试删除；4. 将任务改绑别的模板，再删除。
- 预期结果：名称与启停回显正确；绑定期间删除给明确“模板被任务引用”错误且任务仍指原模板；解除引用后模板从列表消失。
- 覆盖：I-3，模板写→任务引用读。

### A-4：旧编辑器回归
- 前置条件：现有 QA 规则、回复片段各一条。
- 操作步骤：1. 分别尝试插入/保存无默认值的 nullable 专家变量；2. 给其添加非空默认值后保存；3. 预览旧介绍模板。
- 预期结果：QA/片段裸变量仍提示非法；加默认值可保存；旧模板默认值不再被旧 `required_keys` 误判为必填。
- 覆盖：I-1/I-4、must-not-change。
