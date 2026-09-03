# 06 「AI 提示词与约束」页改为可编辑约束清单

> 顺序权威：`00-execution-order.md`。**依赖 03**（与 03b / 05 无依赖，可并行）。
> 全局不变量 G-1 ~ G-8 适用，本文不重复定义。
> 界面基准：`docs/mockups/ai-prompt-console.html`。

## 需求描述

**Observable outcome**

1. 「AI 提示词与约束」页展示新链路两次模型调用的全部约束：检索调用 5 条、生成调用 22 条，
   逐条编号列出，与线上实际生效的内容一致。
2. 运营可以点任意一条直接改；hover 出「撤销」与「删除」；每段底部可「添加一条约束」；
   有改动时底部保存栏亮起并显示「已修改 N 处 · 未保存」。
3. 生成调用的第 18、19、21 条为**只读**，标「派生 · 只读」，说明它们由强制事实规则表自动生成。

**What must NOT change**

1. 旧的自由回复提示词配置（`ai_prompt_config.free_form_system_prompt` 与 `constraints`）
   及其对旧链路的作用——旧链路在 07 之前仍在跑。
2. 「AI 回复训练」视图下其余四个子 Tab 的行为。
3. 03 的两次调用在**未做任何自定义**时的提示词内容——即默认值必须与 03 的
   `RagPromptConstraints` 常量逐字相同。

**Out of scope**

- 用户提示词骨架、参数（temperature / max_tokens / 预筛上限）、JSON 输出结构、
  「用真实来信预览最终 prompt」：本轮**一律不做**，页面只是约束清单。
- 约束的拖动排序：不做。新增的约束一律追加到该段末尾。
- **旧「自由回复系统提示词 + 约束项」表单：本轮不删，07 也不删**（D-14）。
  它配置的是 `AiReplyDraftService` 的 FREE_FORM 兜底路径，而该路径在 D-10 / X-4 之前仍在跑
  （`UnmatchedInboundMailController.kt:366,378` 与 `AiTrainingController.kt:222` 仍调用
  `aiReplyDraftService.generate`）。删表单会让一条仍在运行的路径失去配置入口。
  新旧两套配置在同一页面并存，靠卡片标题区分；随 X-4 一起处置。

## 关键不变量

### I-30: 数据库为空即回落到代码默认值
- Rule: `rag_prompt_config` 的 `retrieval_constraints` / `generation_constraints` 两列为 `NULL` 时，
  服务端返回 03 的 `RagPromptConstraints` 常量。「全部恢复默认」= 把这两列置 `NULL`，
  **不得**把默认值复制一份存进库。
- Applies to: `RagPromptConfigService.effective()` / `resetToDefault()`。
- Violation consequence: 库里存了一份快照后，03 改常量再也不会生效，两处默认值悄悄分叉。
- 来源: original（同构 `llm/service/AiPromptConfigService.kt` 的
  `AiPromptConfigDto`(可空) / `AiPromptConfigEffectiveDto`(不可空 + isCustom) 两层结构）

### I-31: 派生约束不入库、不可编辑
- Rule: 生成调用的第 18、19、21 条由 `rag_mandatory_rule` 现算生成（03 的 `renderDerivedRules`），
  **不写入** `generation_constraints`，服务端对这三条的入参一律忽略。
  下发给前端时带 `derived: true` 标记。
- Applies to: `RagPromptConfigService.save()`、`RagPromptConfigController` 的 DTO。
- Violation consequence: 同一件事在提示词与规则表两处各存一份，改了规则表提示词不跟着变。
- 来源: original（04 X-1 之后运营可改规则表，届时这个分叉会立刻显形）

### I-32: 约束顺序即数组顺序，编号是渲染产物
- Rule: 存储结构是有序数组，条目本身**不存编号**；页面上的 1..22 由渲染时按下标生成。
  删除中间一条后，其后各条编号自动前移。
- Applies to: 存储 DTO、渲染函数、03 的提示词拼接。
- Violation consequence: 存了编号就会与数组下标分叉；且约束正文里若出现「见第 7 条」这类引用，
  删除后会错位——因此**约束正文中禁止引用编号**，本条一并约束。
- 来源: original

### I-33: 保存必须留痕
- Rule: 每次保存写审计：改了哪几条（下标 + 改前值 + 改后值）、新增哪几条、删除哪几条、操作人、时间。
- Applies to: `RagPromptConfigService.save()`。
- Violation consequence: 提示词直接决定对外话术，出问题查不到是谁改的。
- 来源: original（对齐 04 的 I-21）

### I-34: 保存后立即生效，无需重启
- Rule: 03 的 `RagPromptBuilder` 每次构建提示词时从 `RagPromptConfigService.effective()` 取当前值，
  不在进程启动时固化。
- Applies to: `RagPromptBuilder`（03 产出，本计划修改其取值来源）。
- Violation consequence: 运营改完看不到效果，误以为没保存上。
- 来源: original

## 现状审计

### 现有「AI 提示词与约束」页（本计划在其之上新增，不删旧表单）
- 面板：`src/main/resources/static/index.html:886-909` `<div class="ai-tab-content" id="aiTabPrompts">`
  内含 `<section class="panel ai-training-panel">`，两个 `<label class="field">`：
  - `:896-899` `自由回复系统提示词` → `<textarea id="aiTrainingFreeFormPrompt" rows="12">`
  - `:900-903` `约束项（每行一条）` → `<textarea id="aiTrainingConstraints" rows="12">`
  - `:904-908` 工具条：`保存配置` / `恢复默认`(`#aiTrainingRestoreDefaultBtn`) / `#aiTrainingPromptUpdatedAt`
  - `:890` `#aiTrainingPromptStatus`、`:893` `#aiTrainingPromptInfo`
- 前端逻辑：`src/main/resources/static/app.js:141735-141930` 渲染状态文案；
  `:142586` `restoreAiTrainingPromptDefault()` → `PUT /api/ai-training/prompt-config`
  body `{freeFormSystemPrompt:null, constraints:null}`；
  `:158633` 保存 → 同一端点。
- 后端：`llm/service/AiPromptConfigService.kt:8-19` 两个 DTO；
  `:21-52` `object FreeFormPromptDefaults`（`baseSystemPrompt()` / `defaultFreeFormSystemPrompt()`）；
  `llm/domain/AiPromptConfig.kt` `@Table("ai_prompt_config")`，字段
  `id=1 / freeFormSystemPrompt / constraints / updatedAt`；
  端点在 `llm/controller/AiTrainingController.kt`。
- **关键事实**：这套配置只作用于 **FREE_FORM 兜底路径**。真正在跑的 grounded 提示词硬编码在
  `llm/service/AiReplyDraftService.kt:2364` `buildGroundedSystemPrompt()` 与
  `:2417` `buildGroundedUserContent()` 中，页面上一个字都看不到。本计划解决的正是这一点——
  但只针对**新链路**；旧链路的黑盒随 07 一并退场。

### 新链路提示词来源（03 产出）
- `rag/service/RagPromptConstraints.kt`：
  `RETRIEVAL_RULES`（5 条）、`GENERATION_RULES`（22 条，其中第 12 条按 03 的 I-18 改写、
  第 22 条为新增、第 18/19/21 条标 `derived`）。
- `rag/service/RagPromptBuilder.kt`：拼接提示词的唯一入口。

### `rag_prompt_config` 表（本计划新建）
- Write paths（本计划新增两个）：
  1. `V115` 迁移插入单行（两列均为 `NULL`）
  2. `RagPromptConfigService.save()`
  3. `RagPromptConfigService.resetToDefault()`
- Read paths:
  1. `RagPromptConfigService.effective()` — 被 `RagPromptBuilder`（03）与本页 `GET` 端点调用
- **Interaction point 1**：`RagPromptConfigService.save()`（写）× `RagPromptBuilder`（读）。
  保存后下一次 compose 必须用新约束。由 I-34 约束，A-3 验收。
- **Interaction point 2**：`rag_mandatory_rule`（01 的种子 / 未来 04 X-1 的编辑）×
  `RagPromptConfigService.effective()` 的派生三条。规则表变了，页面上第 18/19/21 条的文案必须跟着变。
  由 I-31 约束，A-4 验收。

### 前端样式盘点

**可复用 class**
| class | 位置 | 用途 |
|---|---|---|
| `.panel` | `styles.css:932-939` | 面板容器 |
| `.panel-head` | `styles.css:946-953` | 面板头 |
| `.panel-head h2` | `styles.css:955-957` | 面板标题 |
| `.button` | `styles.css:786-800` | 通用按钮 |
| `.button.primary` | `styles.css:822-830` | 主按钮 |
| `.muted` | `styles.css:2967-2970` | 灰色小字 |
| `#view-ai-training .ai-training-panel .panel-head` | `styles.css:6977-6984` | AI 训练面板头覆写 |
| `.rag-badge` / `.rag-badge.verbatim` | 04 S-4 | 徽章基类（本页用其变体） |
| `--verbatim` 系列 | 04 S-1 | 本页**不用**（约束页无逐字概念） |

**设计基准 token**：见 04 `## 现状审计` 的 token 表。

**DOM 结构约定**：沿用 `index.html:886-892` 的子 Tab 面板骨架（`.ai-tab-content` >
`section.panel.ai-training-panel` > `.panel-head`）。

**改动前基线**：执行前留档
`git show HEAD:src/main/resources/static/index.html | sed -n '886,909p'`。
本计划**不删**该基线中的两个 textarea 表单，只在其**上方**插入新的两张约束清单卡片。
旧表单**长期保留**至 X-4（D-14），07 也不删它——它配置的 FREE_FORM 路径届时仍在运行。
为避免运营混淆，在旧表单的 `<section>` 的 `panel-head` 中把标题改为
「自由回复提示词（旧链路 · 兜底路径）」，并在其下方加一行 `.muted` 说明
「本节只作用于未走 RAG 的兜底回复；RAG 链路的约束见上方两张清单」。
这是本计划对既有 DOM 的**唯一**改动，且只改文案不改结构。

## 样式契约

### S-1: 约束清单卡片
新增 class，逐字复制：

```css
.rag-prompt-card {
    margin-bottom: 14px;
}

.rag-prompt-card-head {
    display: flex;
    align-items: center;
    gap: 9px;
    padding: 11px 15px;
    border-bottom: 1px solid var(--line);
    font-size: 13px;
    font-weight: 600;
    color: var(--text-strong);
}

.rag-prompt-callno {
    width: 19px;
    height: 19px;
    border-radius: 50%;
    background: var(--primary);
    color: #fff;
    font-size: 11px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 600;
    flex: none;
}

.rag-prompt-count {
    margin-left: auto;
    font-weight: 400;
    font-size: 11.5px;
    color: var(--text-muted);
}

.rag-prompt-body {
    padding: 6px 15px 8px;
}

.rag-prompt-add {
    padding: 0 15px 12px;
}

.rag-prompt-add button {
    border: 1px dashed var(--border);
    background: none;
    color: var(--text-secondary);
    border-radius: var(--radius-sm);
    padding: 5px 12px;
    font-size: 12px;
    cursor: pointer;
    font-family: inherit;
}

.rag-prompt-add button:hover {
    border-color: var(--primary);
    color: var(--primary);
}

.rag-prompt-foot {
    font-size: 11.5px;
    color: var(--text-muted);
    padding: 10px 15px;
    border-top: 1px solid var(--line);
    background: var(--surface);
}
```

### S-2: 单条约束行
```css
.rag-prompt-rule {
    display: grid;
    grid-template-columns: 24px 1fr 62px;
    gap: 10px;
    padding: 8px 0;
    border-bottom: 1px dashed var(--line);
    align-items: start;
}

.rag-prompt-rule:last-child {
    border-bottom: none;
}

.rag-prompt-rule-no {
    font-size: 11px;
    color: var(--text-muted);
    text-align: right;
    padding-top: 5px;
}

.rag-prompt-rule-text {
    font-size: 12.7px;
    line-height: 1.72;
    border: 1px solid transparent;
    border-radius: 6px;
    padding: 3px 7px;
    margin-left: -7px;
    outline: none;
    cursor: text;
}

.rag-prompt-rule-text:hover {
    border-color: var(--border);
    background: var(--surface);
}

.rag-prompt-rule-text:focus {
    border-color: var(--primary);
    background: #fff;
    box-shadow: 0 0 0 2px var(--primary-light);
}

.rag-prompt-rule.readonly .rag-prompt-rule-text {
    color: var(--text-muted);
    background: var(--surface);
    border-color: var(--border);
    cursor: default;
}

.rag-prompt-rule-cat {
    font-size: 10.5px;
    color: var(--text-muted);
}

.rag-prompt-rule-actions {
    display: flex;
    gap: 2px;
    justify-content: flex-end;
    opacity: 0;
    transition: opacity 0.12s;
    padding-top: 4px;
}

.rag-prompt-rule:hover .rag-prompt-rule-actions {
    opacity: 1;
}

.rag-prompt-rule-actions button {
    border: none;
    background: none;
    cursor: pointer;
    font-size: 11px;
    color: var(--text-muted);
    padding: 2px 4px;
    border-radius: 4px;
    font-family: inherit;
}

.rag-prompt-rule-actions button:hover {
    background: var(--surface);
    color: var(--error);
}

.rag-prompt-rule-actions button.undo:hover {
    color: var(--primary);
}
```

- DOM 骨架：
```html
<div class="rag-prompt-rule" data-call="2" data-index="11">
    <span class="rag-prompt-rule-no">12.</span>
    <div>
        <div class="rag-prompt-rule-text" contenteditable="true">…</div>
        <div><span class="rag-prompt-rule-cat">框架与落款</span>
             <span class="rag-badge changed">本次改动</span></div>
    </div>
    <span class="rag-prompt-rule-actions">
        <button class="undo" data-act="undo">撤销</button>
        <button data-act="del">删除</button>
    </span>
</div>
```
- 只读行（第 18/19/21 条）加 `class="rag-prompt-rule readonly"`，去掉 `contenteditable`
  与 actions 里的两个按钮，并加 `<span class="rag-badge readonly">派生 · 只读</span>`。

### S-3: 徽章变体（扩展 04 的 `.rag-badge`）
```css
.rag-badge.dirty {
    color: var(--primary);
    background: var(--primary-light);
    border-color: rgba(30, 64, 175, 0.25);
}

.rag-badge.changed {
    color: var(--warning);
    background: var(--warning-bg);
    border-color: var(--warning-border);
    font-weight: 600;
}

.rag-badge.added {
    color: var(--success);
    background: var(--success-bg);
    border-color: var(--success-border);
    font-weight: 600;
}

.rag-badge.readonly {
    color: var(--text-muted);
    background: var(--surface);
    border-color: var(--border);
}
```
- 复用：`.rag-badge` 基类来自 04 S-4，本计划**不重复定义**它。

### S-4: 保存栏
```css
.rag-prompt-savebar {
    position: sticky;
    bottom: 12px;
    background: rgba(255, 255, 255, .96);
    backdrop-filter: blur(8px);
    border: 1px solid var(--border);
    border-top-width: 2px;
    border-radius: var(--radius-md);
    padding: 11px 15px;
    display: flex;
    align-items: center;
    gap: 10px;
    box-shadow: var(--shadow-lg);
    margin-top: 14px;
}

.rag-prompt-savebar-status {
    font-size: 12px;
    color: var(--text-muted);
}

.rag-prompt-savebar-status.dirty {
    color: var(--primary);
}
```
- **注意**：与 05 的 S-4 同理，sticky 浮层的 background 写死
  `rgba(255, 255, 255, .96)` + `backdrop-filter: blur(8px)`，**禁止**用 `var(--panel-bg)`
  （实值 `rgba(255,255,255,0.55)`，会透出底下内容）。
  先例：`.batch-manual-actions-sticky`（`styles.css:9166-9178`）、
  `.batch-config-editor-actions`（`styles.css:8684-8697`）。（来源: K-panel-bg-token-is-translucent）

### S-5: 既有 class 的处置声明
本计划**不修改任何既有 class 的规则块**。对 `index.html:886-909` 的既有表单 DOM，
唯一改动是把 `panel-head` 里的标题文案改为「自由回复提示词（旧链路 · 兜底路径）」
并追加一行 `<p class="muted">`（复用 `styles.css:2967-2970`），**不改任何结构与 class**。
新内容只在 `#aiTabPrompts` 的既有 `<section>` **之前**插入：两张新卡片 + 一个保存栏。
`.rag-badge` 基类由 04 提供，本计划只加 4 个变体（S-3）。

## 实现方案

### T1 — V115 迁移
新建 `src/main/resources/db/migration/V115__create_rag_prompt_config.sql`：
```
CREATE TABLE rag_prompt_config (
    id BIGINT PRIMARY KEY,
    retrieval_constraints TEXT NULL,     -- JSON 数组，NULL = 用代码默认值（I-30）
    generation_constraints TEXT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NULL
);
INSERT INTO rag_prompt_config (id) VALUES (1);
```
表注释写明：**两列为 NULL 时回落到 `RagPromptConstraints` 常量；「恢复默认」是置 NULL，
不是写入一份默认快照**（I-30）。

### T2 — 后端服务与端点
新建 `rag/service/RagPromptConfigService.kt`。
**不建 `@Table` 领域类**——单行配置表用 `NamedParameterJdbcTemplate` 直读直写即可，
先例是 `llm/service/TrustReplyWorkbenchStateStore.kt:22-60`（同样是窄用途的单行/少行表 +
JSON 载荷 + 手写 SQL）。服务内容：
- `effective(): RagPromptConfigEffective` — 两段约束的最终值 + `isCustom` 标记 +
  按 I-31 插入派生三条（带 `derived=true`）。
- `save(dto, operator)` — 忽略 `derived` 条目（I-31），写审计（I-33）。
- `resetToDefault(operator)` — 两列置 NULL（I-30）。
新建 `rag/controller/RagPromptConfigController.kt`：`@RequestMapping("/api/rag/prompt-config")`，
`GET ""` / `PUT ""` / `POST "/reset"`。

### T3 — 03 的取值来源改造
修改 `rag/service/RagPromptBuilder.kt`：把对 `RagPromptConstraints` 常量的直接引用改为
注入 `RagPromptConfigService` 并每次调 `effective()`（I-34）。
`RagPromptConstraints` 保留为默认值来源，不删。

### T4 — 前端（G-6 已由 04 处理，本计划不动子 Tab 注册）
在 `index.html` 的 `#aiTabPrompts` 内、既有 `<section>` **之前**插入：
两张 `.panel.ai-training-panel.rag-prompt-card`（id `ragPromptRetrieval` / `ragPromptGeneration`）
+ 一个 `.rag-prompt-savebar`（id `ragPromptSaveBar`）。
在 `app.js` 新增 `loadRagPromptConfig()` / `renderRagPromptRules(call)` / `markRagPromptDirty()` /
`saveRagPromptConfig()` / `resetRagPromptConfig()`；
把 `loadRagPromptConfig()` 加入 `loadAiTraining()` 的 `Promise.all`。

### T5 — 样式
按 S-1 ~ S-4 逐字追加到 `styles.css` 末尾。

### T6 — 缓存键（G-5）
先跑复核命令，把 `index.html` 三处与
`src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 同步 bump 为 `20260902-rag-prompt-console`。

### T7 — 测试
新建 `src/test/js/ragPromptConsole.test.js`：
- 断言 `index.html` 源文本含 `id="ragPromptRetrieval"`、`id="ragPromptGeneration"`、
  `id="ragPromptSaveBar"`（G-8）
- 断言渲染 22 条时第 18/19/21 行 class 含 `readonly` 且无 `contenteditable`（I-31）
- 断言改一条后 `markRagPromptDirty()` 使保存按钮 `disabled` 变 false、状态文案含「已修改 1 处」
- 断言删除第 3 条后第 4 条的显示编号变为 `3.`（I-32）
- 断言 `styles.css` 中 `.rag-prompt-savebar` 的 background 不含 `var(--panel-bg)`（S-4）
新建 `src/test/kotlin/.../rag/RagPromptConfigServiceTest.kt`：I-30 / I-31 / I-33 各一条。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V115__create_rag_prompt_config.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPromptConfigService.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagPromptConfigController.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPromptBuilder.kt` | 修改（取值来源，T3） |
| 5 | `src/main/resources/static/index.html` | 修改（`#aiTabPrompts` 插入 + 三处缓存键） |
| 6 | `src/main/resources/static/app.js` | 修改（新增 5 个函数 + `loadAiTraining` 追加） |
| 7 | `src/main/resources/static/styles.css` | 修改（末尾追加 S-1~S-4） |
| 8 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改（缓存键 49-51） |
| 9 | `src/test/js/ragPromptConsole.test.js` | 新增 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagPromptConfigServiceTest.kt` | 新增 |

文件数 10，子系统 2（前端静态页 + 后端配置服务）。

## 验证命令

> 本项目必须用 JDK 11（zulu-11）。前端 JS 用例由 `exec-maven-plugin` 绑在 `mvn test` 的 test 阶段
> （`pom.xml:186-232`）。

```bash
# 缓存键复核（改 index.html 之前先跑）
grep -rn "v=$(grep -o 'styles.css?v=[^"]*' src/main/resources/static/index.html | cut -d= -f3)" src/test/js/

# 全量测试（回归门禁，含前端 JS 用例）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的测试
node --test src/test/js/ragPromptConsole.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagPromptConfigServiceTest

# 迁移集成测试（需本地 Docker）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

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

- **I-30**：`RagPromptConfigServiceTest` 断言两列为 NULL 时 `effective()` 返回的两段约束
  与 `RagPromptConstraints.RETRIEVAL_RULES` / `GENERATION_RULES` 逐字相同、`isCustom=false`；
  断言 `resetToDefault()` 后库中两列为 NULL（不是 JSON 字符串）。
- **I-31**：断言 `effective()` 返回的生成约束中，下标 17、18、20（第 18/19/21 条）
  `derived=true`；断言 `save()` 传入被改写的这三条后，库中 `generation_constraints`
  的数组长度为 19（22 − 3），且再次 `effective()` 时这三条仍是现算值。
- **I-32**：断言存储 JSON 中每个条目**不含** `no` / `index` 字段；
  前端测试断言删除第 3 条后第 4 条渲染编号为 `3.`。
- **I-33**：断言保存后审计表新增一行，含改动条目的下标与新旧值。
- **I-34**：`RagPromptBuilder` 中 `grep -n "RagPromptConstraints\." src/main/kotlin/.../RagPromptBuilder.kt`
  无输出（改为经 `RagPromptConfigService` 取值）。
- **S-1 ~ S-4**：`ragPromptConsole.test.js` 对每个新增 class 断言 CSS 与契约**逐字一致**；
  单独断言 `.rag-prompt-savebar` 含 `rgba(255, 255, 255, .96)` 与 `backdrop-filter: blur(8px)`
  且不含 `var(--panel-bg)`。
- **S-5 / D-14**：`git diff src/main/resources/static/index.html` 中 `#aiTabPrompts` 段落
  除新卡片的插入外，只有一处标题文案改动与一行 `.muted` 追加；
  两个 `<textarea>` 的 id、rows、placeholder 与所在 `<label class="field">` 结构**无改动**；
  `grep -n "aiTrainingFreeFormPrompt\|aiTrainingConstraints" src/main/resources/static/index.html`
  仍各命中 1 次。
- **G-5**：三处 `?v=` 同值；固定值测试同步更新（或按变更清单注记挪到 07）。
- **G-8**：断言三个新 id 出现在 `index.html` 源文本中。
- 回归：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 约束清单可见且条数正确
- 前置条件: 03 已落地，应用已启动。
- 操作步骤:
  1. 进「AI 回复训练 → AI 提示词与约束」。
  2. 数两张卡片的条数。
  3. 找到生成调用的第 12 条与第 22 条。
  4. 找到第 18、19、21 条。
- 预期结果: 第 2 步检索调用 `5 条`、生成调用 `22 条`；
  第 3 步第 12 条带橙色「本次改动」徽章、第 22 条带绿色「新增」徽章；
  第 4 步这三条为灰底、点不进去、带「派生 · 只读」徽章。
- 覆盖: 需求 observable outcome 1、3；I-31

### A-2: 编辑、撤销、删除、添加
- 前置条件: A-1 已通过。
- 操作步骤:
  1. 点生成调用第 5 条，在末尾加一个词。
  2. 观察底部保存栏与该条徽章。
  3. hover 该条，点「撤销」。
  4. 点该段底部「+ 添加一条约束」，输入一句话。
  5. 点保存栏「保存并生效」。
  6. 刷新页面。
- 预期结果: 第 2 步保存栏由「未修改」变蓝色「已修改 1 处 · 未保存」，该条出现蓝色「已改」徽章；
  第 3 步文字恢复原值、徽章消失、保存栏回到「未修改」；
  第 4 步新条追加到末尾、编号为 23、带绿色「已添加」徽章；
  第 5 步提示保存成功；第 6 步刷新后新条仍在、编号仍为 23。
- 覆盖: 需求 observable outcome 2

### A-3: 保存后立刻生效（跨路径）
- 前置条件: A-1 已通过；03 的 compose 可调用。
- 操作步骤:
  1. 在生成调用末尾添加一条约束：
     `Always end the last body paragraph with the exact word PROMPTTEST.`
  2. 保存。
  3. **不重启应用**，调 `POST /api/rag-reply/compose` 生成一封回信。
  4. 在返回的 `bodyParagraphs` 里搜索 `PROMPTTEST`。
  5. 删掉这条约束并保存。
- 预期结果: 第 4 步能搜到；第 5 步之后再生成搜不到。
- 覆盖: 现状审计 Interaction point 1；I-34

### A-4: 派生三条跟着规则表变（跨路径）
- 前置条件: A-1 已通过；可直接改库。
- 操作步骤:
  1. 记录页面上第 19 条的文案。
  2. 在库里执行
     `UPDATE rag_mandatory_rule SET fact_codes='KB-GOV-004' WHERE sort_order=30;`
     （原值即 `KB-GOV-004`，改成一个可辨识的差异，例如追加另一个已存在的 fact_code）。
  3. 重启应用（`rag_mandatory_rule` 由 01 的快照持有，改库需 reload）。
  4. 刷新页面看第 19 条。
  5. 改回原值并重启。
- 预期结果: 第 4 步第 19 条文案随规则表变化；期间**没有**去编辑过任何提示词。
- 覆盖: 现状审计 Interaction point 2；I-31

### A-5: 恢复默认是置空不是写快照
- 前置条件: A-2 已做过一次保存（库中两列非 NULL）。
- 操作步骤:
  1. 点「全部恢复默认」。
  2. 查库 `SELECT retrieval_constraints, generation_constraints FROM rag_prompt_config WHERE id=1;`
  3. 刷新页面。
- 预期结果: 第 2 步两列都是 `NULL`（不是一段 JSON）；第 3 步页面显示回默认的 5 条 / 22 条。
- 覆盖: I-30

### A-6: 编号是渲染产物
- 前置条件: A-1 已通过。
- 操作步骤: 删除生成调用的第 3 条，观察原第 4 条的编号。
- 预期结果: 原第 4 条显示为 `3.`；保存后刷新仍为 `3.`。
- 覆盖: I-32

### A-7: UI 目测 —— 与契约实值一致
- 前置条件: A-1 已通过。
- 操作步骤: 开发者工具检查：
  1. `.rag-prompt-rule-text:focus` 的 `box-shadow`。
  2. `.rag-prompt-callno` 的 `background-color` 与尺寸。
  3. `.rag-prompt-savebar` 的 `background-color` alpha。
  4. 未 hover 时 `.rag-prompt-rule-actions` 的 `opacity`。
- 预期结果: ① `rgba(30, 64, 175, 0.07) 0px 0px 0px 2px`；② `rgb(30, 64, 175)`、19×19px；
  ③ alpha ≥ 0.96；④ `0`，hover 后变 `1`。
- 覆盖: S-1；S-2；S-4

### A-8: 回归 —— 旧自由回复提示词表单仍可用
- 前置条件: A-1 已通过。
- 操作步骤:
  1. 在同一页面下滑到既有的「自由回复系统提示词」与「约束项（每行一条）」两个输入框。
  2. 改一个字，点「保存配置」。
  3. 刷新页面确认保存生效。
  4. 点「恢复默认」。
- 预期结果: 四步行为与本计划实施前完全一致；两套配置互不干扰。
- 覆盖: What must NOT change 第 1 条

### A-9: 回归 —— 其余子 Tab 与工作台
- 前置条件: A-1 已通过。
- 操作步骤: 依次点「RAG 知识库」「对话范例」「历史邮件模拟回复」「无依据回答索引」；
  再打开一封来信的可信工作台生成一次。
- 预期结果: 全部正常，行为与本计划实施前一致。
- 覆盖: What must NOT change 第 2 条
