# P2-9：逐项缺口确认弹窗与不可绕过发送流程

## 需求描述

采用 NEEDS_REVIEW/BLOCKED AI 草稿后，点击发送必须进入逐项审核弹窗。操作员逐条确认已人工补充/核验并填写说明后才提交；正文任意编辑不再自动解除缺口状态。

Out of scope：自动判断人工新增句子是否真实、修改 QA 管理、质量报表。

## 关键不变量

### I-1：每 draft/adopt 独立快照
- draft entry 保存完整 nested intent review items、readiness、requestCount、mode、model。
- adopt 后使用该草稿快照；切换邮件、新生成并采用、清空 editor 时重置 confirmation。（K-ai-draft-review-state-per-draft）

### I-2：编辑不是确认
- 删除现有“text/html 与 baseline 不同即可发送”的缺口绕过。
- 任何 NEEDS_REVIEW/BLOCKED 均需弹窗确认；富文本修改只决定 raw template 是否仍可用。（K-ai-preview-raw-adoption-boundary）

### I-3：逐项完整
- 每个 missing/partial intent 对应唯一 checkbox；必须全选。
- BLOCKED/统一策略要求 note；关闭弹窗不发送、不丢 editor。

### I-4：编号连续性
- QA_GROUNDED 多请求草稿发送前扫描行首 `N.` section heading，必须恰好包含 1..requestCount、无重复/越界。
- 不做语义自动认证；完整性最终由人工 checkbox + note 承担。

### I-5：后端最终权威
- payload 明确 `replySource=AI_DRAFT` 与 aiReviewConfirmation；前端通过不代表已发送，后端仍校验。
- send blocked event 上报 best-effort，审计接口失败不能卡死编辑器，但不能绕过 confirmation。

## 前端样式契约

- 新 modal 使用现有：`.modal-shell`、`.modal-backdrop`、`.panel.editor-panel.modal-panel`、`.panel-head.modal-head`。
- 内容使用 `.form-grid.single`，gap 列表 `.compose-gap-list`，每项 checkbox `.checkbox-row`。
- note 使用现有 textarea；按钮 `.button.primary/.button.secondary` 与 `.form-actions.modal-actions`。
- modal 宽 `min(900px,100%)`、max-height 85vh、padding 24px、z-index 50 全由现有 CSS 提供。
- 不新增颜色/阴影/圆角/动画；不修改 styles.css。
- 窄屏沿用 modal padding/form-grid media rules；列表不得固定高度。

## 实现任务

### T1：review modal DOM
文件：`src/main/resources/static/index.html`

- 新增 `#aiReplyReviewModal`、关闭按钮、说明、`#aiReplyReviewList`、`#aiReplyReviewNote`、确认/取消按钮。
- 文案明确：勾选表示已在正文中人工补充或已核验，不会把该信息写回 QA 知识库。

### T2：coverage summary 升级到 intent items
文件：`src/main/resources/static/app.js`

- `summarizeAiReplyCoverage()` 优先遍历 requestCoverage[].intents；生成 `reviewKey=requestIndex:intentKey`、title、status、missingEvidenceKeys。
- 兼容旧 request-level response，使用 `requestIndex:legacy`。
- `appendAiChatDraftBubble()` 与 adoptContext 保存完整 review snapshot。

### T3：弹窗状态机
文件：同上。

- 新增 `aiReplyReviewState`：recordId、pendingRequestBody、reviewItems、readiness、resolve/reject 或显式 callback。
- open 时 escape 所有服务端文本；checkbox change 控制确认按钮。
- BLOCKED note trim 长度不足时提示；cancel 清理 modal state，不清 editor/adopt。
- 新采用草稿/切换 detail/发送成功时关闭并清理。

### T4：重构发送流程
文件：同上。

- 把实际 API 提交抽成单一 `submitManualRichReply()`，避免确认前后复制 payload 逻辑。
- READY 直接提交。
- 非 READY：记录 SEND_BLOCKED event → 打开 modal → 全部确认后给原 requestBody 加：
  - `replySource: "AI_DRAFT"`
  - `aiReviewConfirmation: {draftReadiness,unresolvedItems,confirmedReviewKeys,note,model}`
- QA ids、variants、raw template、HTML/text、subject/operatorName 现有字段全部保留。
- send 前运行 section numbering validation；缺 1 或跳号时不打开最终提交，提示修正正文标题。

### T5：测试
文件：
- `src/test/js/aiReplyReviewConfirmation.test.js`
- `src/test/js/aiReplyLoadingFeedback.test.js`

覆盖：nested intent keys、旧响应兼容、任意编辑仍弹窗、全选/note、cancel、switch reset、READY 直发、payload、编号 2 开始拦截、raw template text+HTML 边界、audit event best-effort。

## 变更文件清单（4）

1. `src/main/resources/static/index.html`
2. `src/main/resources/static/app.js`
3. `src/test/js/aiReplyReviewConfirmation.test.js`
4. `src/test/js/aiReplyLoadingFeedback.test.js`

## 工作区冲突保护

- 先审查 app.js/index.html 未提交 batch-send diff；只在 modal 尾部与 AI reply functions 窄改。
- 不修改 styles.css，不全文件格式化。
- 回归 batch-send visual test。

## 验收标准

- 缺口草稿无论是否编辑都必须确认。
- 正文从 2 开始或编号跳号不能提交。
- 所有 missing intent 均在 modal 可见，payload 与 checkbox 完整一致。
- READY 草稿流程不增加点击。
- 定向测试：

```bash
node --test src/test/js/aiReplyReviewConfirmation.test.js src/test/js/aiReplyLoadingFeedback.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js
```

## 人工验收清单

### A-1：BLOCKED 任意编辑
- 采用后加一个空格/改粗体，点击发送。
- 预期：仍弹 review modal，不可绕过。

### A-2：未全选
- 只勾部分或 note 为空。
- 预期：确认按钮不可用/提示；无发送 API。

### A-3：完整确认
- 人工补写、1..7 标题完整、全选并备注。
- 预期：payload 带 AI_DRAFT review，后端通过并发送。

### A-4：READY
- 预期：不弹窗，现有发送流程不变。
