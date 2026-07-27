# P0-3：训练模拟与收发件草稿就绪状态提示

## 需求描述

两个 AI 回复入口使用后端 `draftReadiness` 明确区分可发送草稿、需人工补充草稿和缺依据草稿。P0 保持现有“原样缺口草稿不可发送”闸门，P2 再升级为逐项确认与后端审计。

Out of scope：新增弹窗、后端审计、QA coverage key 编辑、改变模拟复制行为。

## 关键不变量

### I-1：后端状态优先
- 有 `draftReadiness` 时直接使用；旧响应缺字段时才根据 requestCoverage 兼容推导。
- 未知状态且存在 reviewItems 时 fail closed 为 NEEDS_REVIEW。

### I-2：每个草稿独立保存状态
- mail AI chat 的每个 draft entry 保存自己的 readiness/reviewItems/requestCount/mode；采用旧草稿不能继承最新草稿状态。（K-ai-draft-review-state-per-draft）

### I-3：控制信息不进正文/payload
- readiness、缺口文案只显示在 feedback/badge；不得拼入 draftText、editor 内容、templateTextBody。

### I-4：P0 发送语义
- NEEDS_REVIEW/BLOCKED 且正文与 adopted baseline 完全相同（text+HTML）时阻止发送。
- 任意编辑暂时沿用既有行为；计划 9 将替换为显式逐项确认，不在本计划提前复制逻辑。

### I-5：模拟仍只读
- BLOCKED 模拟草稿允许复制以便人工参考，但必须醒目标注“缺依据/不可直接外发”。

## 前端样式契约

- 不新增/修改 CSS、尺寸、颜色 token、modal 或布局。
- 复用 `.ai-reply-feedback`、`.ai-reply-warning`、`.ai-reply-coverage`、`.ai-draft-badge`、`.ai-chat-label`。
- READY 文案使用现有 coverage 普通样式；NEEDS_REVIEW/BLOCKED 使用现有 warning 样式。
- 不新增 index.html DOM。
- 响应式：沿用现有 feedback 流式块，不增加固定宽高。

## 实现任务

### T1：统一 readiness helper
文件：`src/main/resources/static/app.js`

- 新增 `resolveAiDraftReadiness(result, coverageSummary)`：后端字段优先，兼容旧响应。
- `renderAiReplyFeedback()` 增加：
  - READY：`草稿状态：依据完整`
  - NEEDS_REVIEW：`草稿状态：部分问题需人工补充`
  - BLOCKED：`草稿状态：存在缺少审核依据的问题，不可原样发送`
- 保留逐项 PARTIAL/UNSUPPORTED 提示和模型/generationState 信息。

### T2：草稿 entry 与 adoptContext 保存权威状态
文件：同上。

- `appendAiChatDraftBubble()` 接收完整 result，保存 readiness、reviewItems、requestCount、mode，而非只传 requestCoverage。
- bubble 标签按 readiness 显示“AI 草稿 / 需补充草稿 / 缺依据草稿”；采用按钮在非 READY 时改为“采用并人工补充”。
- `ai-adopt-draft` 把这些字段复制到 adoptContext。

### T3：发送闸门改用 readiness
文件：同上。

- 兼容判断改为 `adopt.draftReadiness !== READY && baseline 未变`。
- BLOCKED 与 NEEDS_REVIEW 提示分别说明缺失/部分覆盖。
- 继续同时比较 innerText 与 innerHTML，保持 raw adoption 边界。（K-ai-preview-raw-adoption-boundary）

### T4：JS 回归测试
文件：`src/test/js/aiReplyLoadingFeedback.test.js`

- 后端 readiness 覆盖本地推导。
- 旧 response fallback 推导。
- 每 draft/adopt 保存状态。
- BLOCKED 原样发送被拦截；READY 不拦截。
- readiness 文案不出现在 requestBody/templateTextBody。
- 模拟复制仍复制 rendered draft。

## 变更文件清单（2）

1. `src/main/resources/static/app.js`
2. `src/test/js/aiReplyLoadingFeedback.test.js`

## 工作区冲突保护

- 修改前查看 `git diff -- app.js`，保留当前批量发送控制台代码。
- 只改 AI helper、模拟结果渲染、mail draft/adopt/send guard 的窄函数。
- 不运行全文件 formatter，不修改 `index.html/styles.css`。

## 验收标准

- 两入口同一 response 显示相同 readiness。
- BLOCKED 七项邮件明确展示第 1 项缺失，用户不会把从 2 开始的片段误认为完整邮件。
- loading 遮罩、模型选择、陈旧响应丢弃逻辑无回归。
- 定向测试：

```bash
node --test src/test/js/aiReplyLoadingFeedback.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js
```

## 人工验收清单

### A-1：训练模拟 BLOCKED
- 预期：feedback 显示 BLOCKED 与具体缺口；草稿仍可复制，页面标注只读不外发。

### A-2：收发件原样发送
- 操作：采用 BLOCKED 草稿，不编辑，点击发送。
- 预期：前端阻止，不发 API。

### A-3：READY 回归
- 操作：采用完整草稿发送。
- 预期：无额外确认，现有 raw/rendered 与富文本行为保持。
