# AI 回复双入口 Loading、资料提示与竞态保护（前端）

> 日期：2026-07-12  
> 顺序：计划 2/3；依赖 `ai-reply-grounded-parity-backend.md` 的新增响应字段。  
> 兼容：代码必须在后端尚未返回新字段时按空数组/0安全降级，因此本计划可先部署但完整效果需计划 1。

## 需求描述

Observable outcomes:

1. 点击历史邮件“生成模拟回复”或收发件箱“生成/继续修改”后，AI chat panel 立即出现同款遮罩、spinner 和“AI 正在生成回复…”；输入框及按钮在请求期间不可操作，成功/失败均恢复。
2. 两入口统一展示后端 `contextWarnings`、`unsupportedRequests` 与“事实覆盖 X/Y 项”；资料不足是操作提示，不自动写入/污染邮件正文。
3. 历史邮件模拟发送选中行的 `mailRecordId`；兼容阶段同时保留 `expertContactId`。
4. 请求期间切换邮件或详情时，旧请求结果不得写入新邮件面板；重复点击不得发起第二个请求。
5. 失败在 panel 内展示明确错误，并保留重新生成能力；收发件箱不再只弹 `alert`。

What must NOT change:

- 草稿复制、继续修改、采用到人工富文本、qaRuleIds 审计上下文的既有行为不变。
- 模拟回复依旧只读，不新增发送按钮。
- 不复用 `.tag-editor-loading*`；标签编辑器 loading 行为不变。
- 不修改全局 `.ai-chat-messages` 310px 规则；训练页仍用自身 `height:auto/max-height:460px` 覆盖。
- 不新增全页 modal；遮罩仅覆盖对应 `.ai-chat-panel`。

Out of scope:

- 改造其他按钮/任务的 loading。
- 在浏览器端判断回复事实正确性。
- 编辑/启停对话范例（计划 3 只治理内容和说明）。

## 关键不变量

### Invariant I-1: 两入口共享同一 loading helper
- Rule: loading DOM、禁用/恢复、aria 属性只能由 `setAiReplyLoading(panel, loading, message)` 管理；模拟和收发件箱不得各复制一套。
- Applies to: `runAiTrainingSimulate`、`ai-reply-turn` action。
- Violation consequence: 一个入口修复后另一个继续无反馈或卡死。
- 来源: original

### Invariant I-2: 控件原状态精确恢复
- Rule: loading 前把每个 button/textarea 的原 `disabled` 写入 `data-ai-reply-was-disabled`;结束后恢复原值并删除标记，不能无条件启用原本禁用的控件。
- Applies to: `setAiReplyLoading`。
- Violation consequence: 请求结束后错误启用本应不可用的操作。
- 来源: original

### Invariant I-3: 旧响应禁止越界渲染
- Rule: 每个入口维护单调 `requestSeq`；发起时捕获 seq + mailRecordId/recordId，完成时只有二者仍匹配当前 state 才可 render。选择变化和 `resetAiReplyState` 必须递增 seq 使在途请求失效。
- Applies to: AI training state、`aiReplyState`、两个请求函数。
- Violation consequence: A 邮件草稿覆盖 B 邮件详情。
- 来源: original

### Invariant I-4: 提示与正文隔离
- Rule: warnings/unsupported/coverage 只渲染到 `.ai-reply-feedback`；`state.*.lastDraft`、复制文本、采用文本、人工富文本正文只能取 `draftText`。
- Applies to: `renderAiReplyFeedback`、`renderAiTrainingSimulateResult`、`appendAiChatDraftBubble`、`ai-adopt-draft`。
- Violation consequence: 内部警告被发送给专家。
- 来源: original

### Invariant I-5: 覆盖文案保持事实语义
- Rule: UI 只能显示“事实覆盖 X/Y 项”，不得显示“已回答 X/Y 项”；unsupported 列表表示缺少审核依据，不表示 AI 一定漏答。
- Applies to: `renderAiReplyFeedback`。
- Violation consequence: UI 对模型输出做未经验证的完成声明。
- 来源: 对齐后端 I-7

### Invariant I-6: 精确邮件 id 优先
- Rule: 模拟 payload 有 `selectedSimulateMail.mailRecordId` 时必须传它；兼容字段 `expertContactId` 同时保留。没有 mailRecordId 时才只传 contactId。
- Applies to: `selectSimulateMail`、`runAiTrainingSimulate`。
- Violation consequence: 历史邮件模拟实际使用最新邮件。
- 来源: 对齐后端 I-5

## 样式契约

### S-1: AI chat panel loading 遮罩
- 复用：`.ai-reply-section .ai-chat-panel` 现有边框/圆角/白底/padding（`styles.css:5796-5801`）；只向该规则逐字追加 `position: relative;`，不改现有属性。
- 新增：在 `styles.css` 的 AI chat 段后逐字加入：

```css
.ai-reply-loading-overlay {
    position: absolute;
    inset: 0;
    z-index: 6;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    border-radius: var(--radius-md);
    background: rgba(255, 255, 255, 0.84);
    color: var(--primary);
    font-size: 13px;
    font-weight: 600;
    backdrop-filter: blur(2px);
}

.ai-reply-loading-spinner {
    width: 24px;
    height: 24px;
    border: 3px solid rgba(var(--primary-rgb), 0.2);
    border-top-color: var(--primary);
    border-radius: 50%;
    animation: ai-reply-spin 0.8s linear infinite;
}

@keyframes ai-reply-spin {
    to {
        transform: rotate(360deg);
    }
}
```

- DOM 结构：helper 只能动态挂载以下结构为 `.ai-chat-panel` 直属末子元素：

```html
<div class="ai-reply-loading-overlay" role="status" aria-live="polite">
    <span class="ai-reply-loading-spinner" aria-hidden="true"></span>
    <span class="ai-reply-loading-text">AI 正在生成回复…</span>
</div>
```

- 禁止项：inline style；`.tag-editor-loading*`；全页遮罩；其他 spinner class；修改既有 `@keyframes tag-editor-spin/ai-analysis-spin`。

### S-2: 资料与事实覆盖提示
- 复用：颜色 token 必须使用 `--warning/--warning-bg/--warning-border/--text-muted/--line`（`styles.css:21-45`）；不得写近似 hex。
- 新增：逐字加入：

```css
.ai-reply-feedback {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-bottom: 10px;
}

.ai-reply-coverage {
    padding: 7px 9px;
    border: 1px solid var(--line);
    border-radius: var(--radius-sm);
    background: var(--surface);
    color: var(--text-muted);
    font-size: 12px;
}

.ai-reply-warning {
    padding: 8px 10px;
    border: 1px solid var(--warning-border);
    border-radius: var(--radius-sm);
    background: var(--warning-bg);
    color: var(--warning);
    font-size: 12px;
    line-height: 1.5;
}

.ai-reply-error {
    padding: 8px 10px;
    border: 1px solid var(--error-border);
    border-radius: var(--radius-sm);
    background: var(--error-bg);
    color: var(--error);
    font-size: 12px;
    line-height: 1.5;
}
```

- DOM 结构：

```html
<div class="ai-reply-feedback" role="status" aria-live="polite" hidden>
    <div class="ai-reply-coverage">事实覆盖 6/7 项</div>
    <div class="ai-reply-warning">现有专家研究资料不足，匹配度问题需要人工确认或先使用已有资料补充功能。</div>
</div>
```

- 禁止项：把 feedback 拼入 `.pre`、`draftText`、clipboard 或富文本 editor；新增未声明 warning/error class；用“已回答”替代“事实覆盖”。

## 现状审计

### 历史邮件模拟 UI
- DOM: `index.html:879-888` 为 `.ai-reply-section > .ai-chat-panel > #aiTrainingSimulateMessages + .ai-chat-input-row`，目前没有 feedback 容器。
- Read/write state: `state.aiTraining.selectedSimulateMailContactId/selectedSimulateMail/simulateResult`; `selectSimulateMail` 清空结果；`runAiTrainingSimulate` 读 contactId 并写 result。
- Current loading: `runAiTrainingSimulate:2889-2903` 调用 `setTagEditorLoading(messages, true, "AI 生成中...")`。目标容器又被 `#view-ai-training .ai-chat-messages:empty { display:none }` 控制，且 helper 名称/样式属于标签编辑器；用户已实测看不到可靠遮罩。
- Interaction points: `renderAiTrainingSimulateResult` 会替换 messages.innerHTML，可能删除在 messages 内的 overlay；新 overlay 必须挂在父 `.ai-chat-panel`。

### 收发件箱 AI 回复 UI
- DOM: `renderAiReplyPanelHtml:8469-8488` 动态生成同构 `.ai-chat-panel`，无 feedback 容器。
- State: `aiReplyState` 保存 recordId/turns/lastDraft/lastQaRuleIds/mode；当前无 in-flight/seq。
- Request: `ai-reply-turn:9003-9053` 直接 await；按钮和 textarea 不禁用；失败只 `alert`；切详情时旧 Promise 仍可向新 DOM append bubble。
- Interaction points: `ai-adopt-draft` 只应采用 `draftText`；新增 feedback 必须不进入 `manualReplyQaContext.baselineText`。

### 前端样式盘点
- 可复用 class:
  - `.ai-reply-section .ai-chat-panel` — `styles.css:5796-5801` — panel 基线。
  - `.ai-chat-messages` — `styles.css:5803-5811` — 邮箱聊天区。
  - `#view-ai-training .ai-chat-messages` — `styles.css:6791-6800` — 训练页覆盖和 empty 隐藏。
  - `.reply-workflow-status` — `styles.css:1885-1892` — 邮箱 section 状态胶囊；本计划不修改。
- 设计 token: primary `#2563eb`; warning `#d97706`; error `#e11d48`; radius-sm `7px`; radius-md `10px`; transition `0.15s`。
- DOM 约定: 静态训练 panel 与动态邮箱 panel 都以 `.ai-chat-panel` 为 loading 定位容器；feedback 位于 messages 之前，input-row 之后不得插入状态文案。
- 改动前基线: 见本节两个 DOM 描述；当前 AI 回复相关 loading 没有专用 CSS。

## 实现方案

### T1：增加共享 helper 与反馈 renderer（I-1/I-2/I-4/I-5，S-1/S-2）
文件：`src/main/resources/static/app.js`

- 新增 `setAiReplyLoading(panel, loading, message = "AI 正在生成回复…")`：挂载/移除 S-1 DOM；设置 `panel.ariaBusy`; 保存并恢复 panel 内 button/textarea 原 disabled。
- 新增 warning code 映射：
  - `EXPERT_PROFILE_NOT_FOUND` → `未找到现有专家画像，本次回复未引用研究资料。`
  - `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT` → `现有专家研究资料不足，匹配度问题需要人工确认或先使用已有资料补充功能。`
- 新增 `renderAiReplyFeedback(container, result, error = null)`：error 优先；其余显示事实覆盖和 warning/unsupported。所有动态文本走 `escapeHtml`。
- unsupported 文案固定前缀：`以下请求缺少已审核依据：`，各项以 `；` 连接，最长显示 3 项，其余显示 `另 N 项`。

### T2：模拟入口精确 id、loading 与竞态保护（I-1 至 I-6，S-1/S-2）
文件：`src/main/resources/static/app.js`、`src/main/resources/static/index.html`

- state 新增 `selectedSimulateMailRecordId:null`、`simulateRequestSeq:0`。
- `selectSimulateMail` 保存 `mail.mailRecordId` 并 `simulateRequestSeq += 1` 使旧请求失效。
- `runAiTrainingSimulate`：若已 loading 直接 return；payload 同时带 `mailRecordId` 和 `expertContactId`;请求前清空旧 error，调用 helper；完成时校验 seq + current mailRecordId 后再 render；catch 在 feedback 显示错误并继续 throw 给全局 toast；finally 只清当前 panel loading。
- S-2 feedback 容器逐字插入 `#aiTrainingSimulateMessages` 前。
- `renderAiTrainingSimulateResult` 调 `renderAiReplyFeedback`，meta chip 增加 `事实覆盖 X/Y`，但 warnings 不进入草稿 bubble。

### T3：邮箱入口 loading、错误和竞态保护（I-1 至 I-5，S-1/S-2）
文件：`src/main/resources/static/app.js`

- `aiReplyState` 新增 `requestSeq:0/inFlight:false`；`resetAiReplyState` 先递增 seq 再清状态。
- `renderAiReplyPanelHtml` 按 S-2 加 `#aiReplyFeedback`。
- `ai-reply-turn` action 在请求前捕获 seq/recordId、设 `inFlight=true` 和 loading；重复点击 return。
- 返回后先检查 `aiReplyState.recordId`、`state.mailbox.detailContext.id`、seq；不匹配则静默丢弃，不能 append operator/draft bubble。
- 成功调用 feedback；错误不用 `alert`，改为 feedback + ``showStatus(`AI 生成失败：${e.message || "未知错误"}`, "error")``；finally 恢复当前 panel，`inFlight=false`。
- mode 文案增加 `QA_GROUNDED` → `已基于 N 条 QA 事实综合多项请求`；既有 QA_MATCHED/FREE_FORM 文案保留。

### T4：落地样式契约（S-1/S-2）
文件：`src/main/resources/static/styles.css`

- 向既有 panel 规则仅追加 `position:relative`。
- 原样复制 S-1/S-2 CSS；不修改任何其他规则。
- grep `.ai-reply-loading-*` 和 `.ai-reply-feedback/.ai-reply-*`，确保使用点仅本计划声明的位置。

## 变更文件清单

| # | 文件 | 操作 | 任务 |
|---|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改 | T1/T2/T3 |
| 2 | `src/main/resources/static/index.html` | 修改 | T2 |
| 3 | `src/main/resources/static/styles.css` | 修改 | T4 |

文件数：3；子系统：静态 AI 训练 UI、动态收发件 AI 回复 UI，共 2。

## 验收标准

- I-1：grep 两个请求入口均只调用 `setAiReplyLoading`；`runAiTrainingSimulate` 不再调用 `setTagEditorLoading`。
- I-2：代码审计/DOM 测试脚本断言原 disabled=true 的控件结束后仍 true，普通控件恢复 false。
- I-3：静态检查两个 state 均有 requestSeq；人工延迟请求场景旧结果不渲染。
- I-4：grep `contextWarnings|unsupportedRequests` 的渲染点不在 `appendAiChatDraftBubble` 的 draft HTML、clipboard、editor 赋值内。
- I-5：所有用户文案 grep 不存在 `已回答 .*\/`；存在 `事实覆盖`。
- I-6：模拟请求 JSON 在 mailRecordId 可用时同时包含精确 id 和兼容 contactId。
- S-1/S-2：`styles.css` 新增规则与契约代码块逐字一致；DOM 与契约骨架一致；无 inline style 和未声明 class。
- 回归命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`，并在浏览器执行 A-1 至 A-7。

## 人工验收清单

### A-1：模拟生成 loading
- 前置条件：LLM 请求可人为延迟至少 2 秒；选择一封模拟邮件。
- 操作步骤：点击“生成模拟回复”，连续再次点击按钮。
- 预期结果：整个 AI chat panel 出现 84% 白色遮罩、24px 蓝色 spinner、“AI 正在生成回复…”；textarea/按钮禁用；Network 只有 1 个 POST；完成后全部恢复。
- 覆盖: outcome 1，I-1/I-2，S-1

### A-2：邮箱 AI loading
- 前置条件：收发件箱打开一封待处理来信，LLM 延迟至少 2 秒。
- 操作步骤：展开 AI 生成回复，点击“生成/继续修改”。
- 预期结果：与 A-1 同款遮罩和禁用行为；成功后生成草稿 bubble，可继续修改。
- 覆盖: outcome 1，I-1，S-1

### A-3：失败恢复
- 前置条件：临时配置不可达 LLM。
- 操作步骤：分别从两个入口发起生成。
- 预期结果：遮罩必定消失；控件恢复；panel 内出现红色错误卡；toast 显示失败；邮箱不弹阻塞 alert；可再次点击。
- 覆盖: outcome 5，I-2，S-2

### A-4：资料不足提示与正文隔离
- 前置条件：后端返回 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`、事实覆盖 6/7 和一条 unsupported。
- 操作步骤：生成后复制模拟草稿；邮箱采用草稿到人工富文本。
- 预期结果：panel 显示黄色提示和“事实覆盖 6/7 项”；复制/富文本中只有 draftText，不含黄色提示、warning code 或 unsupported 前缀。
- 覆盖: outcome 2，I-4/I-5，S-2

### A-5：模拟切信竞态
- 前置条件：A、B 两封邮件，A 请求延迟 3 秒。
- 操作步骤：A 点击生成后立即选择 B；等待 A 返回。
- 预期结果：B 面板不出现 A 草稿/提示；B 可独立生成；控制台无 DOM 异常。
- 覆盖: outcome 4，I-3

### A-6：邮箱切详情竞态
- 前置条件：邮箱 A、B 两封来信，A AI 请求延迟。
- 操作步骤：A 发起后切换 B 详情，等待 A 返回。
- 预期结果：B 不出现 A 草稿；B 的按钮、输入框未被 A 的 finally 错误修改。
- 覆盖: outcome 4，I-2/I-3

### A-7：精确邮件 payload
- 前置条件：模拟列表已显示来信 A；列表不刷新时让同联系人收到新来信 B；浏览器 DevTools Network 打开。
- 操作步骤：对列表仍显示的 A 生成。
- 预期结果：POST body 的 `mailRecordId` 等于 A 的列表项 id；后端响应 inboundSubject 属于 A，而不是数据库最新的 B。
- 覆盖: outcome 3，I-6

### A-8：采用与续轮回归
- 前置条件：邮箱生成 QA_GROUNDED 草稿。
- 操作步骤：输入修改要求继续一轮，再采用第二版到人工富文本。
- 预期结果：续轮正常；采用正文仅为第二版草稿；`manualReplyQaContext.qaRuleIds` 仍为后端真实匹配子集。
- 覆盖: must-NOT-change 1，I-4

### A-9：模拟仍然只读
- 前置条件：记录生成前 `mail_record`、`mail_record_qa_rule` 行数及联系人状态。
- 操作步骤：在模拟页生成两次回复，包括一次失败重试。
- 预期结果：三项数据库状态均不变化；页面仍只有复制按钮，没有发送/采用按钮。
- 覆盖: must-NOT-change 2，outcome 1

### A-10：标签编辑器 loading 与聊天布局回归
- 前置条件：存在可添加标签的专家；训练页和邮箱 AI panel 均可打开。
- 操作步骤：① 执行一次专家标签添加，观察既有 tag-editor loading；② 分别生成长草稿，观察聊天区高度和滚动。
- 预期结果：① 既有 16px 标签 spinner/遮罩仍工作；② 邮箱 messages 保持 310px，训练 messages 自适应且最大 460px；AI loading 不改变两者完成态布局。
- 覆盖: must-NOT-change 3/4/5，I-1，S-1

## 修正记录

（执行或复验期间的决策在此追加。）
