# 对话范例 UI：AI 训练新增「对话范例」Tab + 模拟回复注入标记

> 前置依赖：`ai-training-dialogue-fewshot.md`（同目录）必须先执行完成。本计划消费其产出：
> `ai_training_dialogue` 表、`AiTrainingDialogueService`、`AiReplyDraftResult.fewShotDialogRefs`。
> 视觉基准：会话中已确认的前端预览（对话范例表格 + 蓝色注入标记 pill），本文用现有类名逐点固化，
> **执行时不得自创 CSS 类或新样式**，全部复用 styles.css 既有类。

## 需求描述

**可观察结果**：
1. 「AI 训练」视图新增第 4 个子 Tab「对话范例」（位于「QA 知识库」之后），只读表格列出全部
   对话范例：编号(sourceRef)/标题/关键词/轮数/状态。
2. 「历史邮件模拟回复」生成后，meta 行在现有「模式 · LLM」文本后追加本次注入的对话范例
   badge（如 `注入范例 DIALOG_2143`），无注入时不显示。

**不得改变**：
- 现有 3 个子 Tab 的行为与默认激活 Tab（simulate）
- 模拟/生成的后端语义（只透传展示字段）
- `sendQaRuleIds` / 审计（注入标记纯展示）

**Out of scope**（显式推迟）：
- 对话范例的增删改（编辑仍走 dialogue-seed.json + 迁移，见主计划 I-2）
- 人工工作台（未匹配来信 AI 草稿）的注入标记展示
- turns 正文的展开查看

## 关键不变量

### Invariant U-1: ai-tab 三点同步注册
- Rule: 新子 Tab 必须同步三处：① index.html `.ai-tab` 按钮（`data-tab="dialogues"`）
  ② index.html `<div class="ai-tab-content" id="aiTabDialogues">` 面板
  ③ app.js `switchAiTrainingTab()` 的显式 `||` 映射链加
  `|| (tab === "dialogues" && panelId === "aiTabDialogues")`。
  Tab 点击绑定是通配的（app.js:9394 `querySelectorAll(".ai-tab")`），无需第四处。
- Applies to: index.html, app.js
- Violation consequence: 漏 ③ 时按钮高亮但面板永不显示（映射链是白名单）。
- 来源: original（类比 K-view-registration-triad，但这是 ai-tab 子级，非侧栏视图）

### Invariant U-2: 注入标记纯展示
- Rule: `injectedDialogRefs` 只出现在 simulate 响应与前端渲染；不进入任何发送 payload、
  `mail_record_qa_rule` 或组装台状态。
- Applies to: AiTrainingController, app.js `renderAiTrainingSimulateResult`
- Violation consequence: 复现 K-ai-reply-prompt-vs-send-rule-ids 的审计污染。
- 来源: K-ai-reply-prompt-vs-send-rule-ids

### Invariant U-3: 只复用既有样式类
- Rule: 表格用现有 `.table-wrap > table` 结构（同 QA 知识库 Tab）；badge 用现有
  `badge(label, kind)` 帮助函数——sourceRef 列用 `<code>`、状态列 `badge("启用","ok")`、
  注入标记 `badge("注入范例 " + ref, "info")`。styles.css 零改动。
- Applies to: index.html, app.js
- Violation consequence: 样式漂移、与预览不一致、暗色模式破裂。
- 来源: original

### Invariant U-4: meta 渲染改 innerHTML 须转义
- Rule: `renderAiTrainingSimulateResult` 的 meta 从 `textContent` 改为 `innerHTML` 后，
  所有动态值（mode、refs）必须过 `escapeHtml`（badge() 内部已转义则直接用）。
- Applies to: app.js `renderAiTrainingSimulateResult`
- Violation consequence: XSS 面扩大。
- 来源: original

## 现状审计

### 前端 AI 训练视图
- Tab 机制：index.html:758-761 三个 `.ai-tab`（qa/prompts/simulate，simulate 默认 active）；
  app.js:2210-2222 `switchAiTrainingTab` 用显式 `||` 链映射 panelId（白名单式）；
  app.js:9394-9396 通配绑定点击。
- 表格模式：`renderAiTrainingQaTable`（app.js:2239-2258）——行模板字符串 + `escapeHtml` +
  `badge()` + 空态 `<tr><td colspan>` 兜底。对话范例表复用该模式。
- 模拟 meta：`#aiTrainingSimulateMeta`（index.html:858），三处写入：app.js:2503、2528（清空）、
  2549-2551（渲染，现为 textContent）。
- 预加载：`loadAiTraining()`（app.js:2586-2592）Promise.all 四项，需加第五项。

### 后端 simulate 链路
- `AiTrainingController.simulate`（:170-202）→ `aiReplyDraftService.generate(simulateOnly=true)`
  → `AiTrainingSimulateResponse`。主计划完成后 `AiReplyDraftResult.fewShotDialogRefs` 可用，
  本计划将其透传为响应字段 `injectedDialogRefs`。
- 对话数据读取：`AiTrainingDialogueService`（主计划 T5）需补一个只读 list 方法。
- Interaction points: simulate 响应新字段 ↔ 前端 meta 渲染；GET dialogues ↔ 新表格。

## 实现方案

### T1 后端：AiTrainingController.kt 〔U-2〕
- 新增 `GET /api/ai-training/dialogues` → `List<AiTrainingDialogueView>`
  （`sourceRef, title, keywords, turnCount, enabled`；turnCount 由 turns_json 解析长度）。
- `AiTrainingSimulateResponse` 加 `injectedDialogRefs: List<String>`，
  从 `result.fewShotDialogRefs` 透传。

### T2 后端：AiTrainingDialogueService.kt
补 `listViews(): List<AiTrainingDialogueView>`（复用已有解析，解析失败行 turnCount=0 并 warn）。

### T3 前端结构：index.html 〔U-1, U-3〕
- :759 后插入：`<button type="button" class="ai-tab" data-tab="dialogues">对话范例</button>`
  （紧跟「QA 知识库」之后）。
- `#aiTabQa` 面板之后插入面板（复用 QA Tab 的 panel 结构）：

```html
<div class="ai-tab-content" id="aiTabDialogues">
    <section class="panel ai-training-panel">
        <div class="panel-head">
            <h2>对话范例（few-shot）</h2>
            <div class="toolbar-inline">
                <span class="muted">来源：聊天记录5.27 · 启动自动播种</span>
                <button class="button" id="reloadAiTrainingDialoguesBtn">刷新</button>
            </div>
        </div>
        <div class="table-wrap">
            <table>
                <thead>
                <tr><th>编号</th><th>标题</th><th>关键词</th><th>轮数</th><th>状态</th></tr>
                </thead>
                <tbody id="aiTrainingDialogueTable"></tbody>
            </table>
        </div>
    </section>
</div>
```

### T4 前端逻辑：app.js 〔U-1, U-3, U-4〕
1. `switchAiTrainingTab` 映射链加 `|| (tab === "dialogues" && panelId === "aiTabDialogues")`。
2. 新增 `loadAiTrainingDialogues()`：`api("/api/ai-training/dialogues")` → 渲染行模板：
   `<td><code>${escapeHtml(d.sourceRef)}</code></td>` ·
   `<td><strong>${escapeHtml(d.title)}</strong></td>` ·
   `<td class="muted-cell">${escapeHtml(d.keywords || "-")}</td>` ·
   `<td class="muted-cell">${d.turnCount}</td>` ·
   `<td>${badge(d.enabled ? "启用" : "停用", d.enabled ? "ok" : "warn")}</td>`；
   空态兜底同 `renderAiTrainingQaTable`（colspan=5）。
3. `loadAiTraining()` Promise.all 追加 `loadAiTrainingDialogues()`。
4. `#reloadAiTrainingDialoguesBtn` 绑定刷新（放在 :9388 区域的既有监听器集中处）。
5. `renderAiTrainingSimulateResult` meta 渲染改为：
   `meta.innerHTML = escapeHtml(现有文本) + (result.injectedDialogRefs || []).map(r => badge("注入范例 " + r, "info")).join("")`
   （badge 前补一个空格分隔；refs 为空数组时输出与现状一致〔U-2〕）。
   :2503 与 :2528 两处清空逻辑改 `innerHTML = ""`。

### T5 测试：AiTrainingSimulateTest.kt
- simulate 响应含 `injectedDialogRefs` 字段；llm 关闭走 fallback 时为空数组。
- GET /api/ai-training/dialogues 返回播种的 10 条且字段完整。

## 变更文件清单

| # | 文件 | 动作 |
|---|------|------|
| 1 | src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt | 修改 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingDialogueService.kt | 修改 |
| 3 | src/main/resources/static/index.html | 修改 |
| 4 | src/main/resources/static/app.js | 修改 |
| 5 | src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt | 修改 |

styles.css 不在清单内（U-3：零新增样式）。

## 验收标准

- U-1: 点击「对话范例」Tab 面板正确显隐；其余 3 Tab 行为不变；默认激活仍是 simulate。
- U-2: 模拟一封命中对话关键词的来信 → meta 出现 info badge；检查发送相关代码路径无
  injectedDialogRefs 引用；`mail_record_qa_rule` 无新增写入。
- U-3: 新 Tab 表格与「QA 知识库」表格视觉一致（同类名）；styles.css git diff 为空。
- U-4: 构造含 `<script>` 的 mode/ref 值渲染 meta，断言被转义（单测或人工验证）。
- 全量：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 通过。

## 自检清单

- [x] 文件数 5 ≤ 10；子系统 2（前端 static / llm 后端）≤ 2；新增共享存储字段 0
- [x] 每个交互点有不变量（Tab 注册 U-1、审计隔离 U-2、样式 U-3、转义 U-4）
- [x] 现状审计含全部写入/读取点（meta 三处写入、Tab 白名单链均已列行号）
- [x] 依赖顺序显式：本计划 blockedBy 主计划
