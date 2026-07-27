# AI 回复前端：逐项依据提示与未修改草稿保护

## 需求描述

Observable outcome：AI 训练模拟与收发件 AI 回复都按 `requestCoverage` 显示完整/部分/缺失数量，并逐项提示 PARTIAL/UNSUPPORTED；这些提示永远位于草稿外。收发件中可采用缺口草稿进入人工编辑器，但未做任何编辑时禁止直接发送。

What must NOT change：loading 遮罩、模型下拉、陈旧响应丢弃、草稿显示/复制使用 rendered、续轮使用 raw、采用后 raw/rendered baseline、人工编辑后正常发送、QA 审计 payload。

Out of scope：新增 CSS、修改 index.html、后端 send API 强制校验、阻止训练模拟复制、判断人工编辑是否真的补全事实。

## 关键不变量

### Invariant I-1: requestCoverage 是提示主源
- Rule: `requestCoverage` 存在时按其 index/requestText/status 渲染；`unsupportedRequests` 只作为旧响应兼容 fallback。禁止用 groundedRequestCount 推断具体缺口。
- Applies to: 训练模拟与收发件 feedback。
- Violation consequence: PARTIAL 不可见，运营只看到模糊比例。
- 来源: K-request-facts-not-flat-pool

### Invariant I-2: 状态提示不进入草稿
- Rule: coverage/warning 只写入 `.ai-reply-feedback`；不得拼进 `draftText/renderedDraftText`、聊天 bubble、clipboard text、续轮 assistantDraft、manual editor body 或发送 payload。
- Applies to: render/copy/adopt/turn/send。
- Violation consequence: 中文内部警告或状态码外发。
- 来源: K-ai-preview-raw-adoption-boundary

### Invariant I-3: 逐项文案固定
- Rule: PARTIAL=`第 {index} 项仅部分有已审核依据：{requestText}；请人工补充后再发送。`；UNSUPPORTED=`第 {index} 项缺少已审核依据：{requestText}；草稿未回答该项。`。requestText 折叠空白、最长 240 字符并 HTML escape。
- Applies to: 两个 feedback 容器。
- Violation consequence: 操作员无法定位缺哪一问。
- 来源: original

### Invariant I-4: 覆盖汇总固定
- Rule: coverage 非空时显示 `依据覆盖：完整 {G} 项 · 部分 {P} 项 · 缺失 {U} 项`；未知 status 不计入三类并追加通用警告，不把 raw status 当正文显示。
- Applies to: 两个 feedback 容器。
- Violation consequence: PARTIAL 被计作完整覆盖。
- 来源: original

### Invariant I-5: 缺口状态跟随具体 draft
- Rule: 每个聊天 draft entry 保存生成当次的 `needsGroundingReview` 与 reviewItems；采用历史 draft 时必须使用该 draft 自身状态，不能使用最后一次生成结果。
- Applies to: `aiReplyState.drafts[draftId]`、adoptContext。
- Violation consequence: 采用旧缺口草稿却被新完整草稿状态覆盖。
- 来源: K-ai-preview-raw-adoption-boundary

### Invariant I-6: 未修改缺口草稿不得直发
- Rule: adopted draft 有 PARTIAL/UNSUPPORTED，且 editor innerText/innerHTML 仍与采用 baseline 完全相等时，send handler 必须 return 并显示 `草稿仍有未完整覆盖的问题，请先人工补充或修改正文`；任一文本或富文本编辑后恢复现有发送流程。
- Applies to: 收发件 `send-manual-rich-reply` 前端入口。
- Violation consequence: 后端安全省略的请求仍能被一键发成不完整邮件。
- 来源: K-ai-preview-raw-adoption-boundary

### Invariant I-7: 双表示与审计不变
- Rule: display/copy/adopt 继续用 rendered；continuation/templateTextBody 继续用 raw；coverage 不进入 `manualReplyQaContext.qaRuleIds/suggestedRuleIds/freeTextPreview`。
- Applies to: AI chat state与 manual-rich payload。
- Violation consequence: sender 变量切换失效或 QA 审计污染。
- 来源: K-ai-preview-raw-adoption-boundary / K-ai-reply-prompt-vs-send-rule-ids

## 样式契约

### S-1: AI 回复反馈区域
- 复用：`.ai-reply-feedback`（`styles.css:5876-5881`）、`.ai-reply-coverage`（5883-5890）、`.ai-reply-warning`（5892-5900）、`.ai-reply-error`（5902-5910）。`styles.css` 不在变更清单，规则不得修改。
- 新增：无新 class、无新 CSS。
- DOM 结构：两个既有容器保持：

```html
<div id="aiTrainingSimulateFeedback" class="ai-reply-feedback" role="status" aria-live="polite" hidden></div>
<div id="aiReplyFeedback" class="ai-reply-feedback" role="status" aria-live="polite" hidden></div>
```

  内部只允许现有结构：

```html
<div class="ai-reply-coverage">依据覆盖：完整 4 项 · 部分 1 项 · 缺失 2 项</div>
<div class="ai-reply-warning">第 5 项仅部分有已审核依据：...；请人工补充后再发送。</div>
```

- 禁止项：inline style；新增 class；把 warning 放进 `.ai-chat-bubble/.pre`；修改 feedback 容器 role/aria-live。

### S-2: 草稿与 loading 基线
- 复用：`.ai-chat-bubble.ai-chat-assistant`（`styles.css:5935-5938`）、`.ai-chat-bubble .pre`（5946-5950）、训练 draft 样式（6844-6897）、现有 `.ai-reply-loading-overlay`。
- 新增：无新 class、无新 CSS、无 DOM 改动。
- DOM 结构：训练草稿仍为 `.ai-chat-bubble.ai-chat-assistant.ai-draft-bubble`；收发件草稿仍由 `appendAiChatDraftBubble()` 创建并带现有采用按钮。
- 禁止项：把 coverage 文案插入 draft head/body；修改 loading overlay 挂载点；修改 model select DOM。

## 现状审计

### 前端 AI response 内存状态
- Schema/mapping: 后端两个 response 已有 `requestCoverage[index,requestText,status,factRuleIds]`；无数据库/LocalStorage 写入。
- Write paths:
  1. `runAiTrainingSimulate()` 将 response 写 `state.aiTraining.simulateResult`。
  2. 收发件 `ai-reply-turn` handler 将 raw/rendered/qa ids 写 `aiReplyState`，并把文本写 `aiReplyState.drafts[draftId]`。
  3. `ai-adopt-draft` 将 raw/rendered baseline 写 `adoptContext`。
- Read paths:
  1. `renderAiReplyFeedback()` 当前读 requestCount/groundedRequestCount/contextWarnings/unsupportedRequests，不读 requestCoverage。
  2. 训练 copy 读 `simulateResult.renderedDraftText || draftText`。
  3. 收发件 continuation 读 lastDraftTemplate；adopt 读 drafts entry；send 读 adoptContext baseline。
- Interaction points: coverage 必须在 append draft 时与该 draft 绑定；send guard 必须先于 requestBody/API 调用，同时不能破坏 raw template baseline。（来源: K-ai-preview-raw-adoption-boundary）

### 前端样式盘点
- 可复用 class：见 S-1/S-2；本计划不修改任何 CSS。
- 设计基准 token：coverage 使用 `var(--line)/var(--surface)/var(--text-muted)/12px`；warning 使用 `var(--warning-border)/var(--warning-bg)/var(--warning)/12px/1.5`；容器 gap=6px、margin-bottom=10px。
- DOM 结构约定：训练 feedback 位于 `index.html:889`，收发件 feedback 由 `renderAiReplyPanelHtml()` 创建；两入口共用 `renderAiReplyFeedback()`。
- 改动前基线：当前 helper 只生成 `.ai-reply-coverage/.ai-reply-warning`；draft 内容由 `translatableBody(renderedDraftText || draftText)` 单独渲染。

### 当前缺陷证据
- `app.js:3656-3684` 忽略 `requestCoverage`，PARTIAL 没有专门提示。
- `formatUnsupportedRequests()` 最多展示 3 项且没有 index/status。
- `appendAiChatDraftBubble()` 只保存 raw/rendered，采用后无法知道该历史草稿是否有缺口。
- send handler 只校验主题/正文与 raw baseline，没有依据完整性提示。

## 实现方案

### T1：新增 coverage 纯函数并统一反馈（I-1/I-2/I-3/I-4，S-1/S-2）
文件：`src/main/resources/static/app.js`

- 用 `summarizeAiReplyCoverage(requestCoverage)` 计算 G/P/U 与 reviewItems。
- 用 `formatAiReplyReviewWarnings(summary)` 生成 I-3 固定文案数组；每项一个 `.ai-reply-warning`，不截断为 3 项。
- `renderAiReplyFeedback()` 优先使用 requestCoverage；缺字段才调用现有 unsupportedRequests 兼容逻辑。
- 映射 `AI_REPLY_STRUCTURED_RESPONSE_INVALID` 为 `模型返回格式无效，已使用审核依据生成结构化草稿。`。

### T2：coverage 与 draft entry 绑定（I-2/I-5/I-7，S-2）
文件：`src/main/resources/static/app.js`

- `appendAiChatDraftBubble(rawText,renderedText,requestCoverage)` 把 derived review state 保存到该 draft entry；不把 coverage 写入 HTML bubble。
- `ai-reply-turn` 调用传 `result.requestCoverage || []`。
- `ai-adopt-draft` 把 entry 的 `needsGroundingReview/reviewItems` 复制到 adoptContext；editor 仍只写 rendered。

### T3：发送前原样草稿保护（I-5/I-6/I-7，S-2）
文件：`src/main/resources/static/app.js`

- 在构造 manual-rich API request 前检查：当前 record 的 adoptContext 有 review flag，且 text+HTML 与 baseline 同值。
- 命中时 `showStatus()` 固定错误文案并 return；不调用 API、不清空 state。
- editor 任一变化后走现有 requestBody/templateTextBody/qaRuleIds 逻辑；不新增后端字段。

### T4：前端行为与样式契约测试（I-1 至 I-7，S-1/S-2）
文件：`src/test/js/aiReplyLoadingFeedback.test.js`

- VM 单测：4/1/2 汇总；PARTIAL/UNSUPPORTED 逐项文案、index、HTML escape、240 字截断；未知 status 不原样泄漏。
- requestCoverage 存在时不重复渲染 unsupportedRequests；旧 response fallback 保持。
- source contract：coverage 只进入 feedback/state，不进入 translatableBody/clipboard/assistantDraft/requestBody。
- 历史 draft 各自 review state；采用缺口 draft后原样 send 被 return，编辑 text 或 HTML 后允许进入 API 块。
- grep 断言 styles.css/index.html 无 diff 所需的新 class；S-1/S-2 既有 class/DOM 仍存在。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/resources/static/app.js` | coverage 汇总、逐项警告、draft review state、发送前提示 |
| `src/test/js/aiReplyLoadingFeedback.test.js` | helper、状态隔离、采用/发送与样式契约测试 |

## 验收标准

- I-1：requestCoverage 优先，旧 unsupportedRequests fallback 测试通过。
- I-2：draft/copy/turn/adopt/send payload 均不包含 warning/status 文案。
- I-3：PARTIAL/UNSUPPORTED 固定中文逐字断言，文本安全 escape。
- I-4：4/1/2 汇总逐字为 `依据覆盖：完整 4 项 · 部分 1 项 · 缺失 2 项`。
- I-5：两个历史 draft 的 review state 互不覆盖。
- I-6：原样缺口草稿不调用 api；任一 text/HTML 编辑后不触发该 guard。
- I-7：raw/rendered/templateTextBody 与 qaRuleIds 现有 source contract 全通过。
- S-1：无新增 class/inline style；feedback DOM 与契约一致。
- S-2：draft/loading/model DOM 与 CSS 文件无修改。
- 定向命令：`node --test src/test/js/aiReplyLoadingFeedback.test.js`。

## 人工验收清单

### A-1: 训练模拟逐项提示
- 前置条件: 本次邮件 coverage 为 4 GROUNDED、1 PARTIAL、2 UNSUPPORTED。
- 操作步骤: 1. 在 AI 回复训练点击生成模拟回复；2. 查看草稿上方反馈；3. 复制草稿到文本编辑器。
- 预期结果: 反馈显示 `依据覆盖：完整 4 项 · 部分 1 项 · 缺失 2 项`，并显示第 5/6/7 对应逐项提示；复制文本不含这些中文提示或状态码。
- 覆盖: I-1/I-2/I-3/I-4/S-1

### A-2: 收发件采用缺口草稿
- 前置条件: 生成一份含 PARTIAL 或 UNSUPPORTED 的收发件 AI 草稿。
- 操作步骤: 1. 点击采用草稿；2. 不修改人工富文本正文；3. 填主题后点击发送。
- 预期结果: 页面显示 `草稿仍有未完整覆盖的问题，请先人工补充或修改正文`；邮件未发送；编辑器内容和已选 QA ids 保留。
- 覆盖: I-5/I-6/I-7

### A-3: 人工补充后发送
- 前置条件: 延续 A-2。
- 操作步骤: 1. 在编辑器补充已人工确认内容；2. 再次点击发送。
- 预期结果: 进入现有人工富文本发送流程；变量按最终 sender 渲染；QA 审计 ids 与生成时真实匹配 ids 相同；coverage 警告不进入邮件正文。
- 覆盖: I-2/I-6/I-7 / adopt→send interaction point

### A-4: 完整草稿回归
- 前置条件: 所有 requestCoverage status 都为 GROUNDED。
- 操作步骤: 1. 生成并采用；2. 不修改正文；3. 点击发送。
- 预期结果: feedback 显示完整 N、部分 0、缺失 0；不出现逐项 warning；发送不被 review guard 拦截。
- 覆盖: I-4/I-6 / must-NOT-change

### A-5: Loading 与模型选择回归
- 前置条件: 两入口可用，分别选择 Flash 与 Pro。
- 操作步骤: 1. 分别生成；2. 生成中观察遮罩和 disabled 控件；3. 完成后再次切换模型。
- 预期结果: 遮罩覆盖稳定 panel；按钮/textarea/select 完成后恢复原状态；响应模型与选择一致；feedback 新提示不覆盖 draft。
- 覆盖: S-1/S-2 / must-NOT-change / K-ai-reply-loading-panel
