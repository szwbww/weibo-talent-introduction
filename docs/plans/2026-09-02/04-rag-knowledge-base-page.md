# 04 RAG 知识库管理页（替换「QA 知识库」子 Tab）

> 顺序权威：`00-execution-order.md`。**依赖 01**，可与 02/03 并行。
> 全局不变量 G-1 ~ G-8 适用，本文不重复定义。
> 界面基准：`docs/mockups/rag-knowledge-base.html`。

## 需求描述

**Observable outcome**

1. 「AI 回复训练」视图下的「QA 知识库」子 Tab 变为「RAG 知识库」，展示 45 条事实：
   左侧按分类与属性筛选、中间列表、右侧编辑面板。
2. 运营可以修改一条事实的 `title / answer / question_variants / coverage_keys` 与四个枚举
   （`render_mode / risk_level / status / reply_policy`）并启停；保存后语料指纹随之更新。
3. `render_mode=VERBATIM` 的事实在编辑面板顶部有明显警示条，说明这段文字会原封不动出现在
   发给专家的邮件里。

**What must NOT change**

1. 侧栏「AI 回复训练」视图本身的注册与其余四个子 Tab（对话范例 / AI 提示词与约束 /
   历史邮件模拟回复 / 无依据回答索引）的行为。
2. `/api/qa/*` 的任何端点行为——本计划不碰旧 QA 后端（停写在 07）。
3. 可信工作台、自动回复、手动发信的任何行为。

**Out of scope**

- 检索规则四张表的编辑界面与「试跑」面板：本轮**只做事实库一块**。
  设计稿里的另外两个页签留待后续计划（登记为 X-1）。
- 旧 QA 子 Tab 的后端停写（→ 07）。
- 提示词页（→ 06）。

## 关键不变量

### I-20: 保存必须走 `RagKnowledgeBase.republish()` 这一个原子入口
- Rule: `rag_fact` 的任何写入都必须包在 `RagKnowledgeBase.republish { ... }` 内
  （01 的 I-3 / I-3b）：事务内完成「写 fact → 重算指纹 → 更新 `rag_kb_meta`」，
  提交后才发布新快照，返回新指纹给前端。前端在页头展示当前指纹。
  **禁止**在管理服务里自行拼「先写 fact、再调启动期校验、再回写 meta」——
  启动期校验（`verifyAndPublish()`）会因为「新指纹 ≠ 库里旧指纹」把第一次合法编辑就拦下，
  这是 2026-09-02 计划评审发现的 P0-3。
- Applies to: `RagFactAdminService.update()` / `toggleEnabled()`。
- Violation consequence: 要么知识库永远改不了（误用校验入口），
  要么库改了快照没换（不调 republish）→ 运营看到新值、生成用旧值。
- 来源: original（2026-09-02 计划评审修正；配套 01 的 I-3 / I-3b）

### I-21: 改动留痕落到 `rag_fact_audit`，且必须与发信存证闭环
- Rule: 每次字段变更在**同一个 `republish` 事务内**写一行 `rag_fact_audit`：
  `fact_code / field / old_value / new_value / fingerprint_before / fingerprint_after /
  operator / created_at`。`answer` 的 old/new 存**全文**（`MEDIUMTEXT`），
  其余字段存字符串形式。
- **不得复用** `qa/service/QaRuleAuditService.kt` —— 它写的是 QA 审计表、按 `ruleId: Long`
  索引，复用会让 `rag_*` 与 `qa_*` 产生运行时耦合（G-4）。
- **闭环要求**：03b 的 `mail_record_rag_fact` 只存 `fact_code + corpus_fingerprint`，
  **单独无法还原「这封信发出时那条事实的原文」**。还原路径是
  「存证行的 `corpus_fingerprint` → 在 `rag_fact_audit` 里按 `fingerprint_after` 定位那次变更
  → 沿 `old_value` 链回放到当时版本」。因此 `fingerprint_before` / `fingerprint_after`
  两列是必需的，不是可选的诊断字段。
- Applies to: `RagFactAdminService.update()` / `toggleEnabled()`；V114 迁移。
- Violation consequence: 没有审计表 → 对外话术改了查不到是谁改的；
  没有 fingerprint 两列 → 有审计也接不上 03b 的发信存证，「当时发的是哪一版原文」永远答不出来。
- 来源: original（2026-09-02 第二轮计划评审 P0-3）

### I-22: fact_code / area / seq / legacy_rule_id 前端只读
- Rule: 管理页不得修改 `fact_code`、`area`、`seq`、`legacy_rule_id`。
  服务端对这四个字段的入参一律忽略（不是报错，是忽略），并以库中现值为准。
- Applies to: `RagFactAdminController` 的请求 DTO 与 `RagFactAdminService.update()`。
- Violation consequence: `fact_code` 可改 → 强制事实规则表里的引用悬空（G-1）。
- 来源: original（I-1）

### I-23: 新增与删除事实本轮不开放
- Rule: 管理页只提供「修改」与「启停」，**不提供新增事实与删除事实**。
  服务端不暴露 create / delete 端点。
- Applies to: `RagFactAdminController`。
- Violation consequence: 新增事实会让语料指纹与脚本永久分叉，而本轮的验收基准
  （02 的平价测试）建立在 45 条之上。放开新增前必须先给出新的指纹管理办法。
- 来源: original（D-2 的直接推论）

## 现状审计

### 前端子 Tab 注册（G-6 的三点）
- ① 按钮：`src/main/resources/static/index.html:823`
  `<button type="button" class="ai-tab" data-tab="qa">QA 知识库</button>`
  （同组还有 824 dialogues / 825 prompts / 826 simulate(active) / 827 unsupportedAnswers）
- ② 面板：`index.html:830` `<div class="ai-tab-content" id="aiTabQa">`
  （其余面板起始行：866 aiTabDialogues / 886 aiTabPrompts / 911 aiTabSimulate / 942 aiTabUnsupportedAnswers）
- ③ 白名单映射链：`src/main/resources/static/app.js:3298-3303`
  ```
  const active = (tab === "qa" && panelId === "aiTabQa")
      || (tab === "dialogues" && panelId === "aiTabDialogues")
      || (tab === "prompts" && panelId === "aiTabPrompts")
      || (tab === "simulate" && panelId === "aiTabSimulate")
      || (tab === "unsupportedAnswers" && panelId === "aiTabUnsupportedAnswers");
  ```
- 视图注册（不动）：`index.html:124` `.nav-tab[data-view="ai-training"]`；
  `index.html:821` `<section class="view" id="view-ai-training">`；
  `app.js:545` `viewMeta["ai-training"]`；`app.js:1697` `refreshCurrentView()`。

### 旧 QA 子 Tab 的渲染函数（本计划替换其调用，不删函数体——删除在 07）
- `app.js:3316` `renderAiTrainingQaPager()`
- `app.js:3331` `renderAiTrainingQaTable()`
- `app.js:3429` `loadAiTrainingQa()` — 调 `/api/qa/rules`
- `loadAiTraining()`（`app.js` 尾部）以 `Promise.all` 并行加载 QA / 对话 / 提示词配置。

### 后端 QA 管理端点（本计划不碰）
`qa/controller/QaRuleManagementController.kt:30` `@RequestMapping("/api/qa")`，
其中与列表编辑相关的是 `:108 GET /rules`、`:112 POST /rules`、`:116 PUT /rules/{ruleId}`、
`:123 POST /rules/{ruleId}/enable`、`:127 POST /rules/{ruleId}/disable`。

### `rag_fact` 表（01 产出）
- Write paths（本计划新增第一个应用层写入点）：
  1. `V112` 迁移种子（01）
  2. **`rag/service/RagFactAdminService.update()`（新增）**
  3. **`rag/service/RagFactAdminService.toggleEnabled()`（新增）**
- Read paths:
  1. `rag/service/RagKnowledgeBase.verifyAndPublish()`（01，仅启动时）
     与 `RagKnowledgeBase.republish { }`（01，编辑时；写与读在同一事务内）
  2. **`rag/service/RagFactAdminService.list()`（新增）**
  3. 02 / 03 通过快照间接读
- **Interaction point 1**：`RagFactAdminService.update()`（写）× `RagKnowledgeBase.snapshot()`（读）。
  保存后若不 reload，03 的生成仍用旧 answer。由 I-20 约束，A-3 验收。
- **Interaction point 2**：`RagFactAdminService.toggleEnabled(false)`（写）×
  02 的 `RagMandatoryResolver`（读）。停用一条被强制规则引用的事实（如 `KB-FUND-033`）后，
  强制解析必须跳过它而不是抛异常。由 01 的 I-2 + 02 的 T3 过滤保证，A-4 验收。

### 前端样式盘点

**可复用 class（一律引用，不重写）**
| class | 位置 | 用途 |
|---|---|---|
| `.panel` | `styles.css:932-939` | 面板容器（毛玻璃） |
| `.panel-head` | `styles.css:946-953` | 面板头，`display:flex; justify-content:space-between; padding:12px 16px` |
| `.panel-head h2` | `styles.css:955-957` | 面板标题 |
| `.button` | `styles.css:786-800` | 通用按钮，`min-height:32px; font-size:12px; radius var(--radius-sm)` |
| `.button.primary` | `styles.css:822-830` | 主按钮 |
| `.muted` | `styles.css:2967-2970` | `color: var(--text-muted); font-size: 12px` |
| `.data-table` | `styles.css:3304-3308` | `width:100%; border-collapse:collapse; font-size:11px` |
| `.data-table th, .data-table td` | `styles.css:3310-3314` | `padding:6px 8px; border-bottom:1px solid var(--line)` |
| `#view-ai-training .ai-tab` | `styles.css:6445-6457` | 子 Tab 按钮（含 `:hover` 6458、`.active` 6462） |
| `#view-ai-training .ai-tab-content` | `styles.css:6467-6470` | 子 Tab 面板（`.active` 6471） |
| `#view-ai-training .ai-training-panel .panel-head` | `styles.css:6977-6984` | AI 训练面板头覆写 |
| `#view-ai-training .ai-training-panel .table-wrap` | `styles.css:6985-6989` | 表格滚动容器 |
| `#view-ai-training .ai-training-prompt-form .field` | `styles.css:7010-7031` | 表单字段块 |

**设计基准 token 实值（`styles.css:1-76`）**
```
--primary: #1e40af          --primary-light: rgba(30,64,175,0.07)
--panel-bg: rgba(255,255,255,0.55)   ← 半透明，遮不住底层内容（K-panel-bg-token-is-translucent）
--line: rgba(15,23,42,0.055)         --border: rgba(15,23,42,0.11)
--surface: rgba(15,23,42,0.022)
--ink: #1e293b              --text-secondary: #475569
--text-strong: #334155      --text-muted: #94a3b8
--success: #059669  --success-bg: rgba(5,150,105,0.08)  --success-border: rgba(5,150,105,0.18)
--warning: #d97706  --warning-bg: rgba(217,119,6,0.08)  --warning-border: rgba(217,119,6,0.2)
--error:   #e11d48  --error-bg:   rgba(225,29,72,0.07)  --error-border:   rgba(225,29,72,0.16)
--radius-sm: 7px  --radius-md: 10px  --radius-lg: 18px
--glass-border: rgba(255,255,255,0.5)  --glass-blur: blur(16px)
--glass-shadow: 0 8px 32px rgba(30,64,175,0.1)
```
本仓**没有** `--violet` 系列 token。逐字出信的紫色标识需新增三个变量（见 S-1）。

**DOM 结构约定**
子 Tab 面板的骨架（取自 `index.html:886-892` 的 prompts 面板）：
```html
<div class="ai-tab-content" id="aiTab<Name>">
    <section class="panel ai-training-panel">
        <div class="panel-head">
            <h2>标题</h2>
            <span id="..." class="muted"></span>
        </div>
        ...
    </section>
</div>
```

**改动前基线**：`index.html:830-865` 的 `aiTabQa` 面板整块（QA 规则表格 + 分页 + 编辑弹窗触发）
将被整体替换。执行前先 `git show HEAD:src/main/resources/static/index.html | sed -n '830,865p'`
留档。

## 样式契约

### S-1: 逐字出信标识色（新增 token）
新增三个 CSS 变量，追加到 `styles.css` 的 `:root` 块**末尾**（不得插到中间打乱既有顺序）。
逐字复制，不得改值：

```css
    --verbatim: #7c3aed;
    --verbatim-bg: rgba(124, 58, 237, 0.06);
    --verbatim-border: rgba(124, 58, 237, 0.24);
```

- 禁止项：不得复用 `--primary` 表示逐字（蓝色已代表「强制」语义，见 S-4）；
  不得写死 `#7c3aed` 在规则里。
- 暗色适配：`styles.css:9304` 起有暗色块。本轮**不给这三个变量加暗色覆写**——
  与 `.batch-manual-actions-sticky` / `.batch-config-editor-actions` 两处先例保持一致
  （K-panel-bg-token-is-translucent 末段：要么整体做，要么都不做）。

### S-2: 知识库三栏布局
新增 class，逐字复制：

```css
.rag-kb-layout {
    display: grid;
    grid-template-columns: 186px minmax(0, 1fr) 400px;
    gap: 14px;
    align-items: start;
}

@media (max-width: 1200px) {
    .rag-kb-layout {
        grid-template-columns: 1fr;
    }
}
```

- DOM 骨架：
```html
<div class="rag-kb-layout">
    <section class="panel ai-training-panel"><div class="panel-head"><h2>筛选</h2></div>
        <div id="ragKbFilters" class="rag-kb-filters"></div></section>
    <section class="panel ai-training-panel"><div class="panel-head"><h2>事实列表</h2>
        <span id="ragKbListCount" class="muted"></span></div>
        <div class="rag-kb-search"><input id="ragKbSearch" placeholder="搜索 fact_code / 名称 / 问法 / 正文…"></div>
        <div id="ragKbList" class="rag-kb-list"></div></section>
    <section class="panel ai-training-panel" id="ragKbDetail"></section>
</div>
```
- 禁止项：inline style；未在本契约声明的新 class。

### S-3: 筛选栏与列表
```css
.rag-kb-filters {
    padding: 8px 0;
}

.rag-kb-filter-label {
    font-size: 10.5px;
    letter-spacing: 0.06em;
    color: var(--text-muted);
    padding: 6px 14px 4px;
}

.rag-kb-filter-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 5px 14px;
    font-size: 12.5px;
    cursor: pointer;
    color: var(--text-secondary);
    border-left: 2px solid transparent;
}

.rag-kb-filter-item:hover {
    background: var(--surface);
}

.rag-kb-filter-item.active {
    background: var(--primary-light);
    border-left-color: var(--primary);
    color: var(--primary);
    font-weight: 500;
}

.rag-kb-search {
    padding: 9px 12px;
    border-bottom: 1px solid var(--line);
}

.rag-kb-search input {
    width: 100%;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 6px 10px;
    font-size: 12.5px;
    font-family: inherit;
    outline: none;
    background: var(--surface);
}

.rag-kb-search input:focus {
    border-color: var(--primary);
    background: #fff;
}

.rag-kb-list {
    max-height: 640px;
    overflow: auto;
}

.rag-kb-row {
    padding: 9px 14px;
    border-bottom: 1px solid var(--line);
    cursor: pointer;
    display: grid;
    grid-template-columns: 1fr auto;
    gap: 4px 10px;
}

.rag-kb-row:hover {
    background: var(--surface);
}

.rag-kb-row.active {
    background: var(--primary-light);
    box-shadow: inset 2px 0 0 var(--primary);
}

.rag-kb-row.disabled {
    opacity: 0.45;
}

.rag-kb-row-code {
    color: var(--primary);
    font-weight: 600;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 11px;
}

.rag-kb-row-meta {
    grid-column: 1 / 2;
    font-size: 11.5px;
    color: var(--text-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
```

### S-4: 徽章
```css
.rag-badge {
    display: inline-block;
    font-size: 10.5px;
    padding: 1px 7px;
    border-radius: 999px;
    border: 1px solid;
    line-height: 1.6;
    white-space: nowrap;
}

.rag-badge.verbatim {
    color: var(--verbatim);
    background: var(--verbatim-bg);
    border-color: var(--verbatim-border);
    font-weight: 600;
}

.rag-badge.risk-high {
    color: var(--error);
    background: var(--error-bg);
    border-color: var(--error-border);
}

.rag-badge.risk-medium {
    color: var(--warning);
    background: var(--warning-bg);
    border-color: var(--warning-border);
}

.rag-badge.risk-low {
    color: var(--success);
    background: var(--success-bg);
    border-color: var(--success-border);
}

.rag-badge.status-review {
    color: var(--warning);
    background: var(--warning-bg);
    border-color: var(--warning-border);
}

.rag-badge.status-approved {
    color: var(--success);
    background: var(--success-bg);
    border-color: var(--success-border);
}

.rag-badge.status-disabled {
    color: var(--text-muted);
    background: var(--surface);
    border-color: var(--border);
}
```
- 禁止项：徽章不得用 `.pill` / `.tag` 等既有名（本仓无同名 class，新建以避免与后续冲突）。

### S-5: 编辑面板与逐字警示条
```css
.rag-kb-detail-body {
    padding: 14px;
}

.rag-kb-field {
    margin-bottom: 15px;
}

.rag-kb-field-label {
    font-size: 10.5px;
    letter-spacing: 0.06em;
    color: var(--text-muted);
    margin-bottom: 5px;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.rag-kb-answer {
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 10px 12px;
    font-size: 12.5px;
    line-height: 1.75;
    background: var(--surface);
    white-space: pre-wrap;
    width: 100%;
    font-family: inherit;
    color: var(--ink);
}

.rag-kb-answer.verbatim {
    border-color: var(--verbatim-border);
    background: var(--verbatim-bg);
}

.rag-kb-verbatim-warning {
    display: flex;
    gap: 8px;
    align-items: flex-start;
    background: var(--verbatim-bg);
    border: 1px solid var(--verbatim-border);
    border-radius: var(--radius-sm);
    padding: 8px 11px;
    font-size: 11.5px;
    color: var(--verbatim);
    margin-bottom: 7px;
    line-height: 1.6;
}

.rag-kb-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;
}

.rag-kb-chip {
    font-size: 11px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 2px 7px;
    color: var(--text-secondary);
}

.rag-kb-chip.coverage {
    color: var(--primary);
    background: var(--primary-light);
    border-color: rgba(30, 64, 175, 0.18);
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.rag-kb-detail-foot {
    border-top: 1px solid var(--line);
    padding: 11px 14px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
}
```
- 警示条文案（逐字）：
  `逐字出信。这段文字会原封不动出现在发给专家的邮件里，模型只拿到占位符、无权改写。改这里 = 改对外话术。`
- 禁止项：警示条不得用 `.muted`（`styles.css:2967` 是灰色小字，会弱化警示）。

### S-6: 既有 class 的改动声明
本计划**不修改任何既有 class 的规则块**。唯一对既有文件的样式改动是 S-1 在 `:root` 末尾追加三个变量。
`index.html:823` 的 `.ai-tab[data-tab="qa"]` 按钮**就地改文案与 data-tab 值**
（`qa` → `ragKb`，「QA 知识库」→「RAG 知识库」），其 CSS 规则块不动。

## 实现方案

### T0 — V114 迁移：审计表
新建 `src/main/resources/db/migration/V114__create_rag_fact_audit.sql`：
```
CREATE TABLE rag_fact_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fact_code VARCHAR(32) NOT NULL,
    field VARCHAR(32) NOT NULL,          -- answer / title / render_mode / ...
    old_value MEDIUMTEXT NULL,
    new_value MEDIUMTEXT NULL,
    fingerprint_before VARCHAR(64) NOT NULL,
    fingerprint_after VARCHAR(64) NOT NULL,
    operator VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_rag_fact_audit_code (fact_code, id),
    KEY idx_rag_fact_audit_fp (fingerprint_after)
);
```
表注释写明还原路径（I-21 的闭环要求）。**不声明外键到 `rag_fact`**——
事实可能被改写，审计不应随之失效。版本号 V114 的分配依据见 00 的 G-9。

遵循 I-21。

### T1 — 后端管理服务与端点
新建 `rag/service/RagFactAdminService.kt`：`list()` / `update(factCode, dto, operator)` /
`toggleEnabled(factCode, enabled, operator)`。写入一律包在
`ragKnowledgeBase.republish { repository.save(...) ; auditService.record(...) }` 内（I-20），
由 republish 在同一事务里更新 `rag_kb_meta` 并在提交后发布快照，返回新指纹；
按字段逐条写 `rag_fact_audit`（I-21，与写 fact 在同一事务内，`fingerprint_before` 取
republish 前的快照指纹、`fingerprint_after` 取 republish 算出的新指纹），
忽略只读字段（I-22）。**不得**调用 `verifyAndPublish()`，**不得**复用 `QaRuleAuditService`。
新建 `rag/controller/RagFactAdminController.kt`：`@RequestMapping("/api/rag/facts")`，
`GET ""`、`PUT "/{factCode}"`、`POST "/{factCode}/enable"`、`POST "/{factCode}/disable"`。
**不提供** create / delete（I-23）。

### T2 — 前端面板（G-6 三点同步）
1. `index.html:823` 按钮改为 `data-tab="ragKb"`，文案「RAG 知识库」。
2. `index.html:830` 面板 id 改为 `aiTabRagKb`，内容整体替换为 S-2 的骨架。
3. `app.js:3298` 白名单链把 `(tab === "qa" && panelId === "aiTabQa")` 改为
   `(tab === "ragKb" && panelId === "aiTabRagKb")`。

### T3 — 前端逻辑
在 `app.js` 新增 `loadRagKb()` / `renderRagKbFilters()` / `renderRagKbList()` / `renderRagKbDetail()` /
`saveRagFact()`；把 `loadAiTraining()` 的 `Promise.all` 中的 `loadAiTrainingQa()` 换成 `loadRagKb()`。
`renderAiTrainingQaPager/Table/loadAiTrainingQa` 三个函数**保留不删**（07 统一清理），
但不再被调用——07 会连同其测试一起处理（G-7）。

### T4 — 样式
按 S-1 ~ S-5 逐字追加到 `styles.css` 末尾（S-1 例外，追加到 `:root` 末尾）。

### T5 — 缓存键（G-5）
执行前先跑 G-5 的复核命令；把 `index.html` 三处 `?v=` 与
`src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 同步 bump 为
`20260902-rag-knowledge-base`。

### T6 — 测试
新建 `src/test/js/ragKnowledgeBasePage.test.js`：
- 断言 `index.html` 源文本含 `id="aiTabRagKb"`、`data-tab="ragKb"`、`id="ragKbList"`、
  `id="ragKbDetail"`、`id="ragKbFilters"`（G-8：必须断言真实 HTML，不能只断言 stub）
- 断言 `app.js` 的白名单链含 `"ragKb"` 且不再含 `panelId === "aiTabQa"`
- 断言 `styles.css` 含 `--verbatim:` 与 `.rag-kb-verbatim-warning`
- 用 DOM stub 跑 `renderRagKbDetail`，断言 VERBATIM 事实渲染出 `.rag-kb-verbatim-warning`
新建 `src/test/kotlin/.../rag/RagFactAdminServiceTest.kt`：I-20 / I-21 / I-22 / I-23 各一条。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V114__create_rag_fact_audit.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagFactAdminService.kt` | 新增（含审计写入，用 `NamedParameterJdbcTemplate` 直写，先例 `TrustReplyWorkbenchStateStore.kt:22-60`） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagFactAdminController.kt` | 新增 |
| 4 | `src/main/resources/static/index.html` | 修改（按钮 823、面板 830-865、三处缓存键） |
| 5 | `src/main/resources/static/app.js` | 修改（白名单链 3298、新增渲染函数、`loadAiTraining` 换调用） |
| 6 | `src/main/resources/static/styles.css` | 修改（`:root` 追加 3 变量 + 末尾追加 S-2~S-5） |
| 7 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改（缓存键 49-51） |
| 8 | `src/test/js/ragKnowledgeBasePage.test.js` | 新增 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagFactAdminServiceTest.kt` | 新增 |

文件数 9，子系统 2（前端静态页 + 后端管理服务）。
审计的领域/仓储不单独建类——单表直写用 `NamedParameterJdbcTemplate`，
先例 `llm/service/TrustReplyWorkbenchStateStore.kt:22-60`。

## 验证命令

> 本项目必须用 JDK 11（zulu-11）。前端 JS 用例由 `exec-maven-plugin` 绑在 `mvn test` 的 test 阶段
> （`pom.xml:186-232`）；`verify.sh` 只跑单个文件，不能当前端回归门禁。

```bash
# 缓存键复核（改 index.html 之前先跑）
grep -rn "v=$(grep -o 'styles.css?v=[^"]*' src/main/resources/static/index.html | cut -d= -f3)" src/test/js/

# 全量测试（回归门禁，含前端 JS 用例）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的测试
node --test src/test/js/ragKnowledgeBasePage.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagFactAdminServiceTest

# 前端 JS 全量 + 语法检查
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0 且 `Tests run: N, Failures: 0, Errors: 0`；
`node --test` 退出码 0 且 `# fail 0`；`node --check` 无输出；`git diff --check` 无输出。
来源：`CLAUDE.md:10-27` Commands + `CLAUDE.md:66` 团队沉淀知识。

## 验收标准

- **I-20**：`RagFactAdminServiceTest` 断言 `update()` **不抛异常**（P0-3 的直接钉死点）、
  返回的新指纹与 `rag_kb_meta.fingerprint` 相等、`RagKnowledgeBase.snapshot()` 是新实例；
  另断言 `RagFactAdminService` 源码中不出现 `verifyAndPublish`
  （`grep -n "verifyAndPublish" src/main/kotlin/.../RagFactAdminService.kt` 无输出）；
  再加一条「连续两次编辑」用例，断言第二次同样成功（防止只修好第一次）。
- **I-21**：断言改 `answer` 后 `rag_fact_audit` 新增一行，`old_value` / `new_value` 为**全文**、
  `fingerprint_before` 等于改动前的快照指纹、`fingerprint_after` 等于返回的新指纹；
  断言事务回滚时审计行也不落库（与 01 的 I-3b 同一事务）；
  断言 `RagFactAdminService` 源码中不出现 `QaRuleAuditService`
  （`grep -n "QaRuleAuditService" src/main/kotlin/.../RagFactAdminService.kt` 无输出）。
- **I-22**：断言请求体里带 `factCode="KB-XXX-999"` / `area="ZZZ"` / `legacyRuleId=999` 时被忽略，
  库中这四列不变且不抛异常。
- **I-23**：断言 `RagFactAdminController` 中不存在 `@PostMapping("")` 与 `@DeleteMapping`；
  `grep -n "DeleteMapping" src/main/kotlin/.../RagFactAdminController.kt` 无输出。
- **S-1**：`grep -n -- "--verbatim:" src/main/resources/static/styles.css` 命中且值为 `#7c3aed`；
  `grep -c -- "--verbatim" src/main/resources/static/styles.css` ≥ 3。
- **S-2 ~ S-5**：`ragKnowledgeBasePage.test.js` 逐条 `assert.ok(css.includes("<契约中的完整规则块>"))`，
  对每个新增 class 断言其 CSS 与契约**逐字一致**（含全部状态选择器）。
- **S-6**：`git diff src/main/resources/static/styles.css` 中除 `:root` 末尾三行与文件末尾追加块外
  **无其他 hunk**；`git diff src/main/resources/static/index.html` 中 `.ai-tab` 相关改动只有
  823 行的 `data-tab` 与文案。
- **G-5**：三处 `?v=` 值相同且等于 `20260902-rag-knowledge-base`；
  `batchSendTaskConsoleVisualFix.test.js:49-51` 三行同步更新。
- **G-8**：`ragKnowledgeBasePage.test.js` 断言 `ragKbList` / `ragKbDetail` / `ragKbFilters`
  三个 id 确实出现在 `index.html` 源文本中。
- 回归：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 新页可用
- 前置条件: 01 已落地，应用已启动。
- 操作步骤:
  1. 侧栏点「AI 回复训练」。
  2. 点子 Tab「RAG 知识库」。
  3. 左侧点「逐字出信」筛选项。
  4. 点列表中的 `KB-FUND-033`。
- 预期结果: 第 2 步面板显示出来（不是按钮高亮但空白）；页头显示 `45 条` 与指纹 `2b29a2152f2671df`；
  第 3 步列表变为 **7 条**；第 4 步右侧编辑面板顶部出现紫色警示条，文案为
  `逐字出信。这段文字会原封不动出现在发给专家的邮件里，模型只拿到占位符、无权改写。改这里 = 改对外话术。`，
  正文框底色为紫色系。
- 覆盖: 需求 observable outcome 1、3；S-1；S-5；G-6

### A-2: 修改并保存
- 前置条件: A-1 已通过。
- 操作步骤:
  1. 选 `KB-ENT-012 合作企业类型`（COMPOSE，非逐字）。
  2. 在正文末尾加一个字符，点「保存」。
  3. 记录页头指纹。
  4. 刷新页面。
  5. 撤销这次修改（把字符删掉再保存）。
- 预期结果: 第 2 步提示保存成功；第 3 步指纹**不再是** `2b29a2152f2671df`；
  第 4 步刷新后正文仍是改后的值；第 5 步指纹恢复为 `2b29a2152f2671df`。
- 覆盖: 需求 observable outcome 2；I-20

### A-3: 保存后生成立刻用新值（跨路径）
- 前置条件: 03 已落地；A-2 的操作可重复。
- 操作步骤:
  1. 把 `KB-ENT-012` 的正文里某个词改成一个可辨识的词（如 `PARTNERTEST`），保存。
  2. 不重启应用，调 `POST /api/rag-reply/compose` 生成一封会用到该事实的回信。
  3. 在返回正文里搜索 `PARTNERTEST`。
  4. 改回原值。
- 预期结果: 第 3 步能搜到；说明保存后快照已重建。
- 覆盖: 现状审计 Interaction point 1；I-20

### A-3b: 改动可追溯到发信存证（跨路径）
- 前置条件: 03b 已落地，库中已有一封 RAG 发出的信。
- 操作步骤:
  1. 记下该信 `mail_record_rag_fact.corpus_fingerprint` 的值。
  2. 在知识库页改一条该信用到的事实的正文并保存，记下新指纹。
  3. 查 `SELECT fact_code, field, fingerprint_before, fingerprint_after, LEFT(old_value,40)
     FROM rag_fact_audit ORDER BY id DESC LIMIT 1;`
- 预期结果: 第 3 步返回的 `fingerprint_before` 等于第 1 步记下的值、
  `fingerprint_after` 等于第 2 步的新指纹、`old_value` 是改动前的完整原文——
  据此可还原「那封信发出时这条事实长什么样」。
- 覆盖: I-21 的闭环要求

### A-4: 停用被强制规则引用的事实不会炸（跨路径）
- 前置条件: 02、03 已落地。
- 操作步骤:
  1. 在管理页把 `KB-FUND-033` 停用。
  2. 调 compose 生成一封「只问报酬」的回信。
  3. 把 `KB-FUND-033` 重新启用。
- 预期结果: 第 2 步**不报错**，正常返回草稿，只是 `usedFacts` 中不含 `KB-FUND-033`；
  应用日志无异常堆栈。第 3 步恢复后再生成，`KB-FUND-033` 重新出现。
- 覆盖: 现状审计 Interaction point 2；I-2（01）

### A-5: 只读字段真的改不了
- 前置条件: A-1 已通过。
- 操作步骤: 用 curl 直接 `PUT /api/rag/facts/KB-ENT-012`，body 里带
  `{"factCode":"KB-XXX-999","area":"ZZZ","legacyRuleId":999,"title":"改个名"}`。
- 预期结果: 返回 200；查库确认 `fact_code` 仍为 `KB-ENT-012`、`area` 仍为 `ENT`、
  `legacy_rule_id` 未变，而 `title` 已改为「改个名」。
- 覆盖: I-22

### A-6: UI 目测 —— 与契约实值一致
- 前置条件: A-1 已通过。
- 操作步骤: 在浏览器开发者工具中依次检查：
  1. `.rag-kb-verbatim-warning` 的 `color` 与 `border-color`。
  2. `.rag-kb-row.active` 的 `background` 与 `box-shadow`。
  3. `.rag-badge.risk-high` 的 `color`。
  4. `.rag-kb-layout` 在窗口宽 1400px 与 1100px 下的列数。
- 预期结果: ① `rgb(124, 58, 237)` 与 `rgba(124, 58, 237, 0.24)`；
  ② `rgba(30, 64, 175, 0.07)` 与 `rgba(30, 64, 175, 1) 2px 0px 0px 0px inset`；
  ③ `rgb(225, 29, 72)`；④ 1400px 下三列、1100px 下一列。
- 覆盖: S-1 ~ S-5

### A-7: 回归 —— 其余四个子 Tab 不受影响
- 前置条件: A-1 已通过。
- 操作步骤: 依次点「对话范例」「AI 提示词与约束」「历史邮件模拟回复」「无依据回答索引」四个子 Tab。
- 预期结果: 四个面板都能正常显示与加载数据，行为与本计划实施前一致。
- 覆盖: What must NOT change 第 1 条；G-6

### A-8: 回归 —— 旧 QA 后端与工作台不受影响
- 前置条件: A-1 已通过。
- 操作步骤:
  1. 用 curl 调 `GET /api/qa/rules`。
  2. 打开「收发件箱」→ 待处理来信 → 可信工作台，生成一次草稿。
- 预期结果: 第 1 步正常返回规则列表（旧端点未动）；第 2 步工作台行为与实施前一致。
- 覆盖: What must NOT change 第 2、3 条

## 已登记的后续项

- **X-1**：设计稿 `docs/mockups/rag-knowledge-base.html` 中的「检索规则」与「试跑」两个页签
  不在本计划范围内，留待后续计划。运营在本轮仍不能自助编辑短语组与强制规则，
  改这些需要改 `rag_phrase_group` 等表的数据。
