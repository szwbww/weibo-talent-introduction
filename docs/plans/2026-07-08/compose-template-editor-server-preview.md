<!-- status: implemented -->

# 模板编辑弹窗预览统一：本地渲染迁移到服务端渲染管道

> 背景：`feat: unify mail preview drawer`（e532567a）把 QA 规则 / 回复片段等单文本域预览统一到
> 抽屉（服务端 `POST /api/qa/render-preview` + 专家抽样 `POST /api/qa/preview/random-expert`），
> 但「编辑邮件模板」弹窗是复合草稿（主题 + 主题变体 + 多内容块），抽屉的 textarea/targetId 模型
> 装不下，被遗留在纯前端本地渲染（`renderLocalComposeTemplatePreview`，app.js:6673-6727）。
> 本计划让编辑弹窗预览改走服务端渲染管道，保持现有右侧内嵌面板布局不变。

## 需求描述

**可观察结果**：模板编辑弹窗右侧「邮件预览」由服务端渲染——变量替换、默认值语法、严格占位符
跳过与真实发送管道语义一致；状态文案从「本地预览」变为「服务端预览」；新增「随机抽取」按钮
（复用抽屉的抽样接口）。未保存的草稿改动（改主题/加内容块）即时反映在预览中。
弹窗整体加宽（1380px → 1600px 上限），右侧预览栏加宽（360px → 480px 上限）。

**不得改变**：
- 统一预览抽屉（`#previewDrawer`）的行为与其消费的接口契约
- `POST /api/qa/render-preview` 的请求/响应结构（K-render-preview-response-consumers：只加不改，
  且本计划不动它，新能力走新端点）
- `GET /api/compose-templates/{id}/preview`（已保存模板的块解析预览）现有响应
- 模板保存逻辑（saveComposeTemplate）与发送管道

**Out of scope**（显式推迟）：
- 编辑弹窗接入统一抽屉的 UI 形态（本期保留内嵌面板，仅换数据源）
- 收发件箱/组装台等其他预览点的改造
- 预览专家/发件邮箱筛选交互重做

## 关键不变量

### Invariant P-1: 预览镜像真实渲染管道
- Rule: 编辑弹窗预览的变量替换、默认值解析、严格占位符跳过判定必须全部发生在服务端
  （`MailVariableService.renderPreview` / 模板块解析），前端不得保留任何本地变量替换实现；
  草稿块正文进入 `renderPreview` 前不得经本地 `renderText()` 预处理（`renderVariables=false`）。
- Applies to: app.js 预览路径、新端点 preview-draft、`MailComposeTemplateService.resolveBlocks`
- Violation consequence: 预览与实际外发不一致（本计划要消除的问题本身）。
- 来源: K-preview-mirrors-pipeline、K-preview-draft-raw-before-render

### Invariant P-2: 既有预览契约只加不改
- Rule: 不修改 `POST /api/qa/render-preview` 与 `GET /api/compose-templates/{id}/preview` 的
  请求/响应字段；草稿预览走**新端点** `POST /api/compose-templates/preview-draft`。
- Applies to: MailComposeTemplateController, QaRuleManagementController（不动）
- Violation consequence: 抽屉与组装台内联预览等"沉默消费者"被破坏。
- 来源: K-render-preview-response-consumers

### Invariant P-3: 草稿预览是纯只读操作
- Rule: preview-draft 不落库、不写 mail_record、不消耗发送账号每日计数、不改联系人状态。
- Applies to: MailComposeTemplateService 新方法
- Violation consequence: 预览污染业务数据与配额。
- 来源: original

### Invariant P-4: 删除本地渲染函数前全调用点审计
- Rule: `renderLocalComposeTemplatePreview`、`renderComposeTemplateText`(app.js:6360)、
  `composeTemplateTextHasAllPlaceholders`(:6382)、
  `selectedComposeTemplatePreviewVariables`(:6414) 删除前必须 grep 全部调用点；仅当调用点全部
  位于编辑器预览路径内方可删除，否则保留该函数并在计划外记录观察项（不得顺手改其他消费者）。
- Applies to: app.js
- Violation consequence: 破坏预览之外的隐蔽消费者（同 K-render-preview-response-consumers 教训）。
- 来源: original

### Invariant P-5: 抽样接口复用而非复制
- Rule: 「随机抽取」直接调用既有 `POST /api/qa/preview/random-expert`，不新建抽样端点、
  不在前端复制抽样逻辑；范围/模式参数与抽屉取值一致（CANDIDATE/APPLICATION、SATISFY_ALL/MISSING_ANY）。
- Applies to: app.js
- Violation consequence: 双份抽样逻辑漂移。
- 来源: original（依托 e532567a 的 ExpertSearchService 抽样实现）

### Invariant P-6: 样式改动白名单
- Rule: styles.css 仅允许改两条既有规则的数值：① `.compose-template-editor` 的 `width`
  改为 `min(1600px, calc(100vw - 48px))`（styles.css:5640）；② `.compose-template-body` 的
  `grid-template-columns` 改为 `minmax(0, 1fr) minmax(360px, 480px)`（styles.css:5674）。
  不得新增任何 CSS 类/规则；新 DOM 元素一律套用既有类（见 T3 样式映射表）。
- Applies to: styles.css, app.js, index.html
- Violation consequence: 样式漂移、与既有视觉体系不一致、暗色/响应式行为不可控。
- 来源: original

## 样式契约

> 原则：既有样式引用行号，修改逐字给出，执行 agent 只许复制、不许改写。
> 禁止项（全局）：inline style；未在本契约声明的新 class；对既有 class 的未声明修改；
> "样式与现有一致 / 参考 XX 组件"类模糊表述。本计划**新增 class 数量为 0**。

### S-1: 弹窗加宽（就地修改既有规则）
- 修改 `.compose-template-editor`（styles.css:5640），逐字替换为：

```css
.compose-template-editor {
    width: min(1600px, calc(100vw - 48px));
    max-height: min(92vh, 980px);
}
```

- 使用点 grep 全集：仅 index.html:1435 一处（模板编辑弹窗本体）→ 就地修改，无波及。

### S-2: 预览栏加宽（就地修改既有规则）
- 修改 `.compose-template-body`（styles.css:5672）的 `grid-template-columns` 一行，逐字：

```css
    grid-template-columns: minmax(0, 1fr) minmax(360px, 480px);
```

- 其余属性（gap/padding/overflow/background:#f3f6fc）不动。
- 使用点 grep 全集：仅 index.html:1447 一处 → 就地修改，无波及。

### S-3: 「服务端预览」状态标记
- 挂载点：`#composeTemplatePreviewStatus`（index.html:1499）；静态占位文案「服务端预览」。
- 预览成功：`renderServerComposeTemplatePreview`（app.js:6707）写入
  `innerHTML = '<span class="preview-source-badge">服务端预览</span>'`；
  复用 `.preview-source-badge`（styles.css:6591，抽屉同款），不新建。
- 预览失败：同节点 `textContent = "预览失败，请重试"`（plain text，无 badge）。

### S-4: 预览面板渲染内容（JS 模板字符串产出）
- 渲染入口：`renderServerComposeTemplatePreviewPanel`（app.js:6648）；变量行子函数
  `renderComposeTemplatePreviewVariableRows`（app.js:6636）。
- 复用 class 清单（禁止近似自造；行号 styles.css）：
  - To/Subject 头 `.compose-preview-mail-head`（:5916）
  - 块 pill `.compose-block-pill`（:5999）/ 跳过 `.compose-block-pill.skipped`（:6010）
  - 块注释容器 `.compose-preview-block-notes`（:5944）
  - 跳过汇总 `.compose-preview-skipped`（:5948）
  - 渲染正文 `.compose-preview-mail-body`（:5939）
  - 变量区 `.preview-var-section`（:6612）/ 行 `.preview-var-row`（:6614）
  - 变量 dot `.preview-var-dot` + `.filled`（:6616）/ `.fallback`（:6617）
  - 变量 key/label/value `.preview-var-key`（:6619）/ `.preview-var-label`（:6620）/
    `.preview-var-value`（:6621）
- DOM 骨架（`#composeTemplatePreviewPanel` innerHTML，与 app.js:6669-6677 一致）：

```html
<div class="compose-preview-mail-head">
    <div><span>To</span><strong>{toEmail 或 —}</strong></div>
    <div><span>Subject</span><strong>{renderedSubject}</strong></div>
</div>
<div class="compose-preview-block-notes">
    <div class="compose-block-pill">#{blockOrder+1} {label}</div>
    <div class="compose-block-pill skipped">#{blockOrder+1} {label} — 已跳过（{skipReason}）</div>
</div>
<!-- strict 跳过汇总：仅 skipReason === "存在未满足占位符" 的块计数 > 0 时插入 -->
<div class="compose-preview-skipped">已跳过 {N} 段：存在未满足占位符</div>
<div class="compose-preview-mail-body">{renderedBody 或「添加内容块后显示预览。」}</div>
<!-- preview.variables 非空时插入 -->
<div class="preview-var-section">
    <div class="preview-var-row">
        <span class="preview-var-dot {filled|fallback}"></span>
        <span class="preview-var-key">{key}</span>
        <span class="preview-var-label">{label}</span>
        <span class="preview-var-value" title="{value}">{value 或 —}</span>
    </div>
</div>
```

- dot 着色规则（app.js:6638）：`usedFallback → fallback`；否则 `filled → filled`；其余 `fallback`。
- 正文须 `escapeHtml`；**不得**使用 `.preview-var-value-tag`（抽屉正文高亮专用，本面板不用）。

### S-5: 随机抽取按钮
- 复用：`.button small`（styles.css 通用 `.button` 体系）。
- 挂载：index.html:1501 `#randomComposeTemplatePreviewBtn`，与 `#refreshComposeTemplatePreviewBtn`（:1502）
  同排，位于 `.compose-template-blocks-head` 内、预览控件之上。
- 绑定：`app.js:9234-9236` → `randomComposeTemplatePreviewExpert`。

### S-6: 实现核对（样式契约 ↔ 代码）
- [x] S-1/S-2 加宽：`styles.css:5641`（1600px）、`:5674`（360–480px 列）
- [x] S-3 成功态 badge + 失败态 plain text
- [x] S-4 变量行四列结构（dot/key/label/value）与抽屉 `#previewDrawer` 变量面板同 class
- [x] S-5 随机抽取按钮 DOM + 事件绑定
- [x] 面板挂载 `#composeTemplatePreviewPanel.compose-template-preview`（index.html:1518）
- [ ] 窄屏目视复核（`.modal-panel` 响应式，styles.css:3764）——人工回归项

## 现状审计

### 前端编辑弹窗预览（app.js）
- 本地渲染链：`refreshComposeTemplatePreview`(:6651) → `renderLocalComposeTemplatePreview`(:6673)
  → `renderComposeTemplateText`(:6360，本地 ${var|default} 替换) +
  `composeTemplateTextHasAllPlaceholders`(:6382，本地严格占位符判定) +
  `selectedComposeTemplatePreviewVariables`(:6414，从预览专家/发件邮箱下拉收集变量)。
- QA_RULE / REPLY_SNIPPET 块依赖 `state.qaRules` / `state.replySnippets` 前端缓存，未加载时
  显示"[…将在服务端预览时渲染]"占位（:6697、:6706）——本地渲染天然不完整的证据。
- 控件：预览专家输入、发件邮箱下拉、「允许默认值 / 必须满足全部占位符」切换
  （`#composeTemplatePreviewStrictPlaceholders`）、「刷新预览」按钮、状态文案
  `#composeTemplatePreviewStatus`（:6725 固定写"本地预览"）。

### 服务端渲染与抽样
- `MailVariableService.renderPreview(text, account, contact)`(:90)：真实管道的变量渲染 +
  fallbackKeys；由 `POST /api/qa/render-preview`（QaRuleManagementController:40-43）消费。
- `POST /api/qa/preview/random-expert`（QaRuleManagementController:52）：抽屉的专家抽样。
- `MailComposeTemplateService.preview(id)`(:133) + 私有块解析(:239-330)：解析已保存模板的
  QA 规则/片段/自定义文本块（含跳过原因），**但不做专家变量渲染**、只支持已保存实体。
- Write paths（本计划涉及的存储）：无——preview-draft 零写入〔P-3〕。
- Read paths：qa_rule、reply_snippet、expert_contact/ES（经既有 service 读取），复用不改。

### 前端样式盘点（Step 1b-fe）
- 可复用 class：见 ## 样式契约 S-3/S-4/S-5/S-6（class 名 + styles.css 行号已逐条列出）。
- 设计基准 token：主色 `var(--primary)`（蓝）；弹窗内容区背景 `#f3f6fc`；卡片圆角 12px；
  pill 圆角 999px、字号 11-12px；正文字号 13px。
- DOM 结构约定：弹窗 = `.modal-shell > .modal-backdrop + section.panel.editor-panel.modal-panel
  .compose-template-editor`（index.html:1435）；预览面板挂载点
  `#composeTemplatePreviewPanel.compose-template-preview`（index.html:1518）；
  状态文案 `#composeTemplatePreviewStatus`（index.html:1499，成功态由 JS 写 badge）。
- 改动前基线（逐字）：
  `.compose-template-editor { width: min(1380px, calc(100vw - 48px)); max-height: min(92vh, 980px); }`
  （styles.css:5640-5642）；
  `.compose-template-body` 原网格列 `grid-template-columns: minmax(0, 1fr) minmax(310px, 360px);`
  （styles.css:5674）；
  状态文案原值「本地预览」（已改为服务端预览 + badge，见 S-3）。

### Interaction points
- 新端点响应 ↔ 前端预览面板渲染（唯一新增交互点）。
- 块解析逻辑当前绑定已保存实体 → 需提取为可接收内存草稿的共享私有方法，`preview(id)` 与
  preview-draft 共用（防止两份块解析漂移）。

## 实现方案

### T1 后端端点：MailComposeTemplateController.kt 〔P-2, P-3〕
新增 `POST /api/compose-templates/preview-draft`，请求体：
`subject, subjectVariants: List<String>, blocks: List<DraftBlock(blockType, refId?, customText?, blockOrder)>,
orcidId?, contactId?, senderAccountCode?, strictPlaceholders: Boolean`。
响应（命名对齐抽屉契约，便于前端复用渲染代码）：
`subject（已渲染）, body（已渲染拼接）, blocks: List<ComposeTemplatePreviewBlock>（含 included/skipReason）,
fallbackKeys: List<String>, toEmail?, variables: List<PreviewVariableItem>（与 render-preview 同结构，供
.preview-var-section 渲染）`。

### T2 后端服务：MailComposeTemplateService.kt 〔P-1, P-3〕
- 把 :239-330 的块解析私有方法重构为接收内存块列表（`preview(id)` 改为先取实体再调它——
  行为不变，既有测试守护）。
- `resolveBlocks(..., renderVariables: Boolean = true)`：`render()` / `preview(id)` 走默认 true；
  **`previewDraft()` 必须传 `renderVariables = false`**，使 QA/片段/自定义块的 `rawTextsByOrder`
  保留原始 `${key|fallback}` token，变量替换只在后续 `MailVariableService.renderPreview` 发生
  〔K-preview-draft-raw-before-render，fix-1 P1-1〕。
- 新 `previewDraft(...)`：块解析（raw）→ 逐段 + 主题调 `MailVariableService.renderPreview(text, account, contact)`
  → strictPlaceholders=true 时按服务端 fallbackKeys / variables 状态判定跳过段落（语义与发送管道一致）。
  contact 解析：contactId 优先，其次 orcidId（复用 render-preview 的解析方式）；两者皆无时
  返回未渲染原文 + 全量占位符列为 fallbackKeys（前端提示选择专家）。

### T3 前端：app.js 〔P-1, P-4, P-5〕
1. `renderLocalComposeTemplatePreview` 整体替换为异步 `renderServerComposeTemplatePreview`：
   收集表单草稿（复用 `collectComposeTemplateBlocksFromForm` + `collectSubjectVariants`）
   → POST preview-draft → `renderServerComposeTemplatePreviewPanel` 复用现有
   `compose-preview-mail-head / compose-block-pill / compose-preview-mail-body /
   preview-var-section` DOM 结构（类名零新增）；`renderComposeTemplatePreviewVariableRows`
   消费响应 `variables` 字段。
2. `#composeTemplatePreviewStatus` 写 `<span class="preview-source-badge">服务端预览</span>`；
   接口失败时写「预览失败，请重试」并保留上次内容（不回退本地渲染〔P-1〕）。
3. 并发请求用 `composeTemplatePreviewRequestId` 丢弃过期响应；主题/块/专家/发件账号 input
   与块增删改事件均触发 `renderServerComposeTemplatePreview()`，满足草稿即时预览。
4. 「随机抽取」按钮：调 `POST /api/qa/preview/random-expert`（level=CANDIDATE, mode=SATISFY_ALL）
   后回填 `#composeTemplatePreviewExpertInput` 并刷新〔P-5〕。
5. 本地渲染四函数按 P-4 审计后删除：`renderLocalComposeTemplatePreview`、
   `renderComposeTemplateText`、`composeTemplateTextHasAllPlaceholders`、
   `selectedComposeTemplatePreviewVariables`；grep 零残留。
6. 样式一律按 ## 样式契约 执行（S-3/S-4/S-5 复用映射 + DOM 骨架），不得偏离〔P-6〕。

### T4 前端结构：index.html
预览面板控件区加「随机抽取」按钮（沿用 `.button small` 既有类）；其余 DOM 不动。

### T4b 样式加宽：styles.css 〔P-6〕
仅改两条既有规则的数值，不新增规则：
- `.compose-template-editor`（:5640）：`width: min(1380px, calc(100vw - 48px))`
  → `width: min(1600px, calc(100vw - 48px))`；`max-height` 不动。
- `.compose-template-body`（:5674）：`grid-template-columns: minmax(0, 1fr) minmax(310px, 360px)`
  → `grid-template-columns: minmax(0, 1fr) minmax(360px, 480px)`。
说明：小屏由 `calc(100vw - 48px)` 与 minmax 下限自然兜底，styles.css:3764 的响应式
`.modal-panel` 媒体查询不需改动（执行时目视复核一次窄屏表现即可）。

### T5 测试：MailComposeTemplateServiceTest.kt + composeTemplatePreview.test.js
- previewDraft：草稿含 QA 规则/片段/自定义块 + 指定 contact → 变量被服务端渲染、
  strictPlaceholders 跳过段落与 fallbackKeys 正确。
- previewDraft 无专家上下文 → 返回原文 + fallbackKeys 全量。
- previewDraft 含 `${researchFields|Science}` 且专家缺 researchFields →
  `mailVariableService.renderPreview` 收到原始 token（非预渲染文本）；strict 时块被跳过。
- 重构后 `preview(id)` 既有断言不变（回归守护）。
- JS 单测（`src/test/js/composeTemplatePreview.test.js`）：面板渲染、preview-draft 调用、
  random-expert 端点绑定。

## 变更文件清单

| # | 文件 | 动作 |
|---|------|------|
| 1 | src/main/kotlin/com/weibo/talentintroduction/template/controller/MailComposeTemplateController.kt | 修改 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt | 修改 |
| 3 | src/main/resources/static/app.js | 修改 |
| 4 | src/main/resources/static/index.html | 修改 |
| 5 | src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt | 修改 |
| 6 | src/main/resources/static/styles.css | 修改（P-6 白名单两条数值；见修正记录 M-1） |
| 7 | src/test/js/composeTemplatePreview.test.js | 修改 |

## 验收标准

- P-1: 前端 grep 无任何 `${...}` 本地替换实现残留于预览路径；带默认值语法/未满足占位符的
  草稿，弹窗预览结果与 `render-preview` 对同文本的渲染一致。
- P-2: `render-preview`、`/{id}/preview` 的请求/响应 git diff 为空。
- P-3: previewDraft 单测断言零写库（无 repository.save 调用路径）。
- P-4: 提交说明中附三函数调用点 grep 结果；删除仅限预览路径内引用。
- P-5: 「随机抽取」网络请求指向既有 `/api/qa/preview/random-expert`。
- P-6: styles.css 计划内加宽已落地（:5641 宽度、:5674 网格列）；S-6 样式核对项除窄屏人工回归外均 ✅；
  宽屏弹窗 1600px、预览栏可达 480px；前端 DOM 无白名单外的新类名（M-1 额外 CSS 见修正记录）。
- 集成：编辑一个未保存草稿（改主题+加自定义块）→ 预览即时反映且由服务端渲染
  （QA 规则块不再出现"[…将在服务端预览时渲染]"占位）。
- 全量：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 通过。

## 执行进度

| 任务 | 状态 | 提交/证据 |
|------|------|-----------|
| T1 后端端点 preview-draft | ✅ | `MailComposeTemplateController.kt:55-57` |
| T2 服务 refactor + previewDraft | ✅ | `MailComposeTemplateService.kt:156-225`；`renderVariables=false` |
| T3 前端服务端预览 | ✅ | `app.js:6632-6743`；本地渲染四函数已删 |
| T4 随机抽取按钮 | ✅ | `index.html:1501`；`app.js:9234-9236` |
| T4b 样式加宽 | ✅ | `styles.css:5641,5674` |
| T5 单测 | ✅ | `MailComposeTemplateServiceTest.kt` + `composeTemplatePreview.test.js` |
| fix-1 P1-1 raw-before-render | ✅ | `resolveBlocks(..., renderVariables=false)`；见修正记录 |

## 修正记录

| ID | 来源 | 问题 | 决策 |
|----|------|------|------|
| P1-1 | fix-v fix-1 | `previewDraft` 先调 `resolveBlocks()` 时本地 `renderText()` 会提前吃掉 `${key\|fallback}`，fallback 检测与 strict skip 失真 | `resolveBlocks` 增 `renderVariables` 参数；`previewDraft` 传 `false`，变量渲染统一在 `MailVariableService.renderPreview`；单测 `previewDraft passes raw fallback placeholder tokens to renderPreview` 守护。知识沉淀：`K-preview-draft-raw-before-render` |
| M-1 | fix-v fix-1 用户口头修正 | 同次提交 `styles.css` / `app.js` 含邮件详情、AI 训练等 UI 改动，超出 P-6 白名单 | **保留**，不按计划外回滚；P-6 验收仅约束模板编辑弹窗相关两条数值与 DOM 类名白名单 |

## 验收结果（fix-v round 1，2026-07-08）

| 不变量 | 结果 | 证据 |
|--------|------|------|
| P-1 | ✅ | 前端预览路径无本地 `${}` 替换；`previewDraft` raw-before-render 已修复 |
| P-2 | ✅ | 新端点 `POST /api/compose-templates/preview-draft`；旧 `GET /{id}/preview` 与 `render-preview` 契约未改 |
| P-3 | ✅ | `previewDraft` 只读，无 save/send 路径 |
| P-4 | ✅ | grep 无 `renderLocalComposeTemplatePreview` 等四函数残留 |
| P-5 | ✅ | `randomComposeTemplatePreviewExpert` → `/api/qa/preview/random-expert` |
| P-6 | ✅ | S-1~S-5 已落地；S-6 核对项除窄屏目视外全 ✅；M-1 额外 UI 按修正保留 |
| 全量测试 | ✅ | `mvn test` 1277 passed；`node --test src/test/js/*.test.js` 198 passed |

## 未完成 / 显式推迟（Out of scope，本期不做）

- 编辑弹窗接入统一 `#previewDrawer` UI 形态
- 收发件箱 / 组装台等其他预览点改造
- 预览专家 / 发件邮箱筛选交互重做
- 主题变体随机抽样（`selectDraftSubject` 当前取 variants[0]，与已保存模板 `selectSubjectVariant` 行为不同——仅影响草稿预览，发送管道不受影响）

## 自检清单

- [x] 文件数 7 ≤ 10；子系统 2（template 后端 / 前端 static）≤ 2；新增共享存储字段 0
- [x] 新端点有不变量覆盖（P-2/P-3）；删除类改动有审计护栏（P-4）
- [x] 现状审计含本地渲染全链路行号与服务端既有能力清单（grep 验证）
- [x] Phase 0 知识：K-preview-mirrors-pipeline、K-render-preview-response-consumers 已消费；
  K-qa-outbound-render-seams 复核后不涉及（外发渲染缝不动）
- [x] fix-1 修正记录已回写；K-preview-draft-raw-before-render 已沉淀
