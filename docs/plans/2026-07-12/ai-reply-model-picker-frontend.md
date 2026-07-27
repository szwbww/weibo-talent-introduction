# AI 回复双入口模型选择器前端

## 需求描述

Observable outcome：AI 回复训练和收发件箱 AI 回复面板各显示同构下拉框，可选 `DeepSeek V4 Flash`/`DeepSeek V4 Pro`，默认 Flash；请求携带枚举，结果元数据回显模型。生成期间现有 loading 遮罩覆盖面板并禁用下拉框。  
What must NOT change：训练页 mailRecordId 精确选择、两入口 requestSeq 竞态保护、反馈/草稿隔离、采用草稿、现有 loading 样式与文案。  
Out of scope：模型说明弹窗、费用/速度数据、记入 localStorage/DB、同时生成两个模型对比。

## 关键不变量

### Invariant I-1: 双入口同一 value 集合
- Rule: 两个 select options 的 value 只能是 `DEEPSEEK_V4_FLASH`、`DEEPSEEK_V4_PRO`；label 分别为 `DeepSeek V4 Flash`、`DeepSeek V4 Pro`；默认 Flash。
- Applies to: 静态训练 DOM、动态邮箱 DOM、payload。
- Violation consequence: 前后端枚举不一致或入口漂移。
- 来源: original

### Invariant I-2: 模型是本次请求快照
- Rule: 发请求前读取 select value 到局部 `expectedModel`；payload 与 stale-response 判定使用该值；结果 `selectedModel` 与 expectedModel 不同则视为陈旧/协议错误，不渲染草稿。
- Applies to: simulate、ai-reply-turn。
- Violation consequence: UI 显示 Pro，但草稿来自 Flash。
- 来源: original

### Invariant I-3: loading 禁用并恢复 select
- Rule: `setAiReplyLoading` 控制 `button, textarea, select`，保留 `data-ai-reply-was-disabled` 原状态；finally 恢复。overlay 仍挂稳定 `.ai-chat-panel`。
- Applies to: 双入口。
- Violation consequence: 请求中切模型造成竞态，或完成后下拉永久禁用。
- 来源: K-ai-reply-loading-panel

### Invariant I-4: 模型状态不污染邮件状态
- Rule: 训练模型存在 `state.aiTraining.simulateModel`；邮箱模型存在 `aiReplyState.selectedModel`；切邮件/reset turns 不重置用户本次页面选择；不写 contact/mail 数据。
- Applies to: state/reset/render。
- Violation consequence: 每切一封邮件模型跳回或被当作邮件字段发送。
- 来源: original

### Invariant I-5: 反馈与正文隔离
- Rule: 模型 badge 只进入 feedback/meta，绝不拼入 draftText、turn assistantDraft、采用草稿正文。
- Applies to: render result、append bubble、adopt draft。
- Violation consequence: 邮件正文出现内部模型信息。
- 来源: K-ai-reply-loading-panel

## 样式契约

### S-1: 共用模型行与 select
- 复用：`.ai-chat-panel`（`styles.css:5796`）、`.toolbar-label` 的字号/颜色语义仅作 token 参考；不直接套 `.toolbar` 外框。
- 新增：以下 CSS 必须逐字加入 `styles.css`，不得改值：

```css
.ai-reply-model-row {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    margin-bottom: 10px;
}

.ai-reply-model-row label {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--text-muted);
    font-size: 12px;
    font-weight: 500;
    white-space: nowrap;
}

.ai-reply-model-select {
    min-width: 190px;
    height: 32px;
    padding: 0 30px 0 10px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
    background: #fff;
    color: var(--text-main);
}

.ai-reply-model-select:focus {
    outline: none;
    border-color: var(--primary);
    box-shadow: 0 0 0 2px rgba(var(--primary-rgb), 0.12);
}

.ai-reply-model-select:disabled {
    cursor: not-allowed;
    opacity: 0.65;
    background: #f8fafc;
}
```

- DOM 结构：两个入口均使用下列骨架，只有 id 不同：

```html
<div class="ai-reply-model-row">
    <label>生成模型
        <select id="aiTrainingReplyModel" class="ai-reply-model-select">
            <option value="DEEPSEEK_V4_FLASH" selected>DeepSeek V4 Flash</option>
            <option value="DEEPSEEK_V4_PRO">DeepSeek V4 Pro</option>
        </select>
    </label>
</div>
```

- 邮箱 id 固定为 `aiMailboxReplyModel`，不带 `selected` 动态拼接；render 后由 state 赋值。
- 禁止项：inline style；第三个模型；修改现有 overlay CSS；新增未声明 class。

## 现状审计

### 训练模拟 UI
- DOM: `index.html:879-887` 静态 `.ai-chat-panel`，feedback → messages → input row。
- State/write: `state.aiTraining` 保存 selected mail id/requestSeq/result，无 model。
- Request/read: `runAiTrainingSimulate:2901-2953` payload 为 contactId/promptOverride/mailRecordId。
- Interaction points: loading helper当前禁用 button/textarea；模型行应在 feedback 之前，overlay 父容器不变。（来源: K-ai-reply-loading-panel / K-ai-simulate-exact-mail-id）

### 收发件箱 AI UI
- DOM: `renderAiReplyPanelHtml:8607-8627` 动态生成；每个详情只有一个固定 id select。
- State/write: `aiReplyState` reset 记录 recordId/turns/seq/inFlight，无 model。
- Request/read: action handler body 为 turns/qaRuleIds/operatorInstruction。
- Interaction points: reset 每封邮件但 model 需要保持页面会话选择；stale guard 当前校验 seq/recordId/detailId，需增加 model 快照。

### 前端样式盘点
- tokens: primary `#2563eb` / rgb `37,99,235`; panel-border `rgba(15,23,42,.08)`; radius-sm `7px`; text-main `#1e293b`; text-muted `#94a3b8`；body 13px。
- 可复用：`.ai-reply-section .ai-chat-panel`、`.ai-reply-loading-overlay`、`.ai-reply-feedback`、`.button.primary`。
- 改动前基线：两个 panel 均无模型行；loading helper selector 为 `button, textarea`；现有 overlay/feedback CSS 不改。

## 实现方案

### T1：静态训练模型行（I-1/I-4/S-1）
文件：`src/main/resources/static/index.html`

- 按 S-1 在 `aiTrainingSimulateFeedback` 前插入训练 DOM。
- 不改 panel/list/filter 结构。

### T2：动态邮箱模型行与状态（I-1/I-4/I-5/S-1）
文件：`src/main/resources/static/app.js`

- state 默认值均为 `DEEPSEEK_V4_FLASH`。
- `renderAiReplyPanelHtml` 插入 S-1 邮箱 DOM；渲染详情后将 select.value 设为 state。
- 两 select change 只更新各自 state，不触发请求、不落库；reset 不覆盖 selectedModel。

### T3：请求快照、回显与 loading（I-2/I-3/I-5）
文件：`src/main/resources/static/app.js`

- 两 body 增加 `model: expectedModel`。
- stale guard 增加当前 select/state 与 expectedModel；response.selectedModel 必须相等，否则显示 `模型响应与当前选择不一致，请重新生成`，不 append draft。
- `setAiReplyLoading` selector 改为 `button, textarea, select`。
- simulate meta 与 mailbox feedback 增加 `模型：DeepSeek V4 Flash/Pro` badge；映射固定，不直接显示 response 原始字符串。
- draft/turn/adopt 逻辑不读取模型 label。

### T4：逐字样式（S-1）
文件：`src/main/resources/static/styles.css`

- 原样加入 S-1 CSS；不修改现有 class。

### T5：前端契约测试（I-1 至 I-5/S-1）
文件：`src/test/js/aiReplyLoadingFeedback.test.js`

- 两 DOM id、option value/label/default 完全一致。
- 两 payload 均含 model；stale guard/response equality 存在。
- loading selector 包含 select，原状态恢复逻辑仍在。
- CSS 逐字包含 S-1 全部规则；无 inline style/第三 option。
- 断言 model badge 不传入 `appendAiChatDraftBubble`、turns、adopt editor。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/resources/static/index.html` | 训练模型下拉 |
| `src/main/resources/static/app.js` | 邮箱下拉、state、payload、回显、loading |
| `src/main/resources/static/styles.css` | S-1 逐字样式 |
| `src/test/js/aiReplyLoadingFeedback.test.js` | 双入口/model/loading/style 契约 |

## 验收标准

- I-1：两 select 恰好两个相同 option，默认 Flash。
- I-2：请求抓包 model 与选择一致；不一致 response 不渲染。
- I-3：生成中两 select disabled，完成/失败后恢复；overlay 仍可见。
- I-4：切换邮件后模型选择保持；无模型字段写入联系人/邮件 API。
- I-5：复制/采用草稿正文无模型 badge。
- S-1：CSS/DOM 与契约逐字一致；grep 无新 inline style/新 class。
- 命令：`node --test src/test/js/aiReplyLoadingFeedback.test.js`。

## 人工验收清单

### A-1: 训练页模型选择与 loading
- 前置条件: AI enabled；打开历史邮件模拟。
- 操作步骤: 1. 选 Pro；2. 点击生成；3. 观察下拉/遮罩；4. 等待完成。
- 预期结果: 下拉显示 Pro；生成中不可操作且白色半透明遮罩显示 `AI 正在生成回复…`；完成后恢复；meta 显示 `模型：DeepSeek V4 Pro`。
- 覆盖: I-1/I-2/I-3/S-1

### A-2: 邮箱模型选择保持
- 前置条件: 打开一封未匹配来信。
- 操作步骤: 1. 选 Pro 并生成；2. 切另一封邮件；3. 展开 AI 回复。
- 预期结果: 第二封仍显示 Pro；生成结果 badge 为 Pro；旧邮件请求不能写入新详情。
- 覆盖: I-2/I-4

### A-3: Flash 默认与正文隔离
- 前置条件: 刷新页面，不操作下拉。
- 操作步骤: 在两入口分别生成并复制/采用草稿。
- 预期结果: 两入口默认 Flash；请求结果显示 Flash badge；复制/采用的正文不含 `DeepSeek` 或模型名称。
- 覆盖: I-1/I-5

### A-4: 失败恢复
- 前置条件: 临时配置无效 LLM endpoint 或触发 500。
- 操作步骤: 选择 Pro 后生成。
- 预期结果: 显示错误反馈；遮罩消失；select/textarea/button 恢复原 disabled 状态；仍选择 Pro。
- 覆盖: I-3 / must-NOT-change

