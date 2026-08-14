# 批量手动执行：收件范围预估与弹窗遮罩修复

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction` (`main`)

## 需求描述

手动执行中切换收件范围后，500ms 防抖刷新并按该地区显示匹配专家数；预估失败时显示服务端已返回的安全错误文本。批量邮件任务控制台主体必须不再透出背景内容。

- 不变：预估继续只读，不创建执行、联系人或 campaign；筛选语义、500ms 防抖与过期响应丢弃不变。
- 不变：其它 `.modal-content` 继续使用玻璃样式；深色模式仍使用深色不透明面板。
- 范围外：不改预估接口、ES 查询、数据库、发信路径或全局异常处理。

## 关键不变量

### Invariant I-1: 地区选择触发预估
- Rule: `batchConfigEditorRegions` 和 `batchManualRegions` 的每次用户切换都必须调用各自面板的 `scheduleRecipientPreview`；手动面板仍更新 `manualDraft.regions`。
- Applies to: `notifyBatchRegionPickerChanged`。
- Violation consequence: 仅修改地区时不会刷新人数。
- 来源: K-recipient-count-preview-parity

### Invariant I-2: 预估与执行快照同源
- Rule: 手动预估和 `POST /manual-executions` 必须使用同一份完整 `BatchExecutionSnapshot` 形状，至少含 `roundsPerRun`、`regions`、模板、漏斗、标签、服务商、学科、专家状态及全部发送控制字段。
- Applies to: `buildManualRecipientSnapshot`、`confirmManualExecution`。
- Violation consequence: 预估人数与最终任务收件范围不一致。
- 来源: K-recipient-count-preview-parity

### Invariant I-3: 预估失败可诊断且不打断编辑
- Rule: 当前请求失败时提示文本使用 `Error.message` 的纯文本；保留序号检查，不弹窗、不执行任务。
- Applies to: `refreshRecipientPreview`。
- Violation consequence: HTTP 400 被错误归类为“预估不可用”，无法定位参数问题。
- 来源: original

### Invariant I-4: 仅批量控制台不透明
- Rule: 仅 `.task-modal.batch-send-task-modal` 使用不透明 `var(--bg-sidebar)`；全局 `--panel-bg` 和 `.modal-content` 不改。
- Applies to: 批量任务弹窗。
- Violation consequence: 正文被半透明面板与遮罩重复叠加，或其它弹窗样式回归。
- 来源: original

### Invariant I-5: 静态资源缓存键一致
- Rule: `styles.css`、`trust-reply-workbench.js`、`app.js` 的 `?v=` 取同一个新值。
- Applies to: `index.html`。
- Violation consequence: 浏览器混用新旧 CSS/JS。
- 来源: K-frontend-cache-key-triad

## 样式契约

### S-1: 批量邮件任务控制台主体
- 复用：`.task-modal.batch-send-task-modal`，`src/main/resources/static/styles.css:8401`；其唯一 DOM 使用点为 `src/main/resources/static/index.html:1094`。就地修改该专用 class。
- 新增：无新 class。在该规则块增加且仅增加下列声明：

```css
  background-color: var(--bg-sidebar);
```

- DOM 结构：保持不变。

```html
<div id="batchSendTaskModal" class="modal-overlay" hidden>
    <div class="modal-content task-modal batch-send-task-modal">
```

- 禁止项：不改 `.modal-overlay`、`.modal-content`、`--panel-bg`；不新增 inline style 或 class。

## 现状审计

### 手动预估快照与执行快照
- Schema: 后端 `BatchExecutionSnapshot` 的 `regions` 默认空数组、`roundsPerRun` 默认 1，预估接口直接接收该类型；`RecipientScope.fromSnapshot` 读取 `regions`。
- Write paths: `confirmManualExecution` 组装并提交 `ManualBatchExecutionRequest.snapshot`。
- Read paths: `refreshRecipientPreview` 提交手动预估快照；后端 `previewRecipients` 调用 `countBySnapshot`。
- Interaction points: 现有预估快照漏 `regions`、`roundsPerRun`，而执行快照带这两项。

### 前端事件与错误路径
- 地区切换: `notifyBatchRegionPickerChanged` 仅保存手动草稿，未调用 `scheduleRecipientPreview`。
- 错误路径: `api` 已把非 2xx 的 `data.message` 包装为 `Error.message`；`refreshRecipientPreview` 的 catch 丢弃该值。
- 交互点: 地区自定义选择器不属于手动面板的原生 input/select 事件集合，必须在 picker 回调中显式触发。

### 前端样式盘点
- 可复用 class: `.modal-overlay`（`styles.css:4244`，深色遮罩）、`.modal-content`（`styles.css:4255`，半透明 `--panel-bg`）、`.task-modal.batch-send-task-modal`（`styles.css:8401`，批量控制台专用）、`.batch-manual-actions-sticky`（`styles.css:9036`，`.96` 操作栏）。
- 设计基准 token: `--bg-sidebar: #ffffff`；深色模式 `--bg-sidebar: #111a2b`；`--panel-bg: rgba(255,255,255,0.55)`。
- 改动前基线: 批量控制台没有背景覆盖，继承 `.modal-content` 的半透明 `--panel-bg`；操作栏是 `rgba(255,255,255,.96)`。

### 缓存键
- 三个资源当前均为 `20260813-v6-topnav-glass-01`；相关测试断言该具体值且断言三者相等。

## 实现方案

### 1. 同步地区事件与快照 [I-1, I-2]
- 修改 `src/main/resources/static/app.js`：地区 picker 回调为编辑器和手动面板分别安排预估；提取一个完整手动执行快照构造函数，供预估与最终执行复用。
- 修改 `src/test/js/batchSendTaskConsoleInteraction.test.js`：测试两类地区 picker 都安排正确面板的预估；测试预估/执行共享快照且携带 `regions`、`roundsPerRun`。

### 2. 保留预估错误原因 [I-3]
- 修改 `src/main/resources/static/app.js`：catch 接收 `error`，经 `textContent` 显示 `预估失败：` 加错误消息，并记录 `console.warn`；保留现有序号 guard。
- 修改 `src/test/js/batchSendTaskConsoleInteraction.test.js`：测试过期失败不渲染，当前失败写入错误消息而非固定“预估不可用”。

### 3. 修正批量弹窗表面 [I-4, S-1]
- 修改 `src/main/resources/static/styles.css`：只在批量控制台专用规则加入 S-1 声明。
- 修改 `src/test/js/batchSendTaskConsoleVisualFix.test.js`：断言专用规则使用 `var(--bg-sidebar)`，且全局 `.modal-content` 未被修改为该值。

### 4. 发布缓存隔离 [I-5]
- 修改 `src/main/resources/static/index.html`：三个缓存键同时改为 `20260814-v7-batch-preview-repair-01`。
- 修改 `src/test/js/batchSendTaskConsoleVisualFix.test.js`：更新精确缓存键断言，保留三键相等断言。

## 变更文件清单

| 文件 | 变更 |
| --- | --- |
| `src/main/resources/static/app.js` | 地区事件、同源快照、失败提示 |
| `src/main/resources/static/styles.css` | 专用不透明批量弹窗 |
| `src/main/resources/static/index.html` | 三资源缓存键 |
| `src/test/js/batchSendTaskConsoleInteraction.test.js` | 事件、快照、错误路径测试 |
| `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 样式与缓存键测试 |

## 验收标准

- I-1: 两个地区 picker 的切换均调用正确面板的 `scheduleRecipientPreview`。
- I-2: 手动预估和手动执行使用同一构造函数；其结果含 `regions` 与 `roundsPerRun`。
- I-3: HTTP 400 的 `Error.message` 显示在预估提示；过期失败不覆盖后续请求。
- I-4 / S-1: 批量控制台专用 class 有精确 `background-color: var(--bg-sidebar)`；全局 modal 规则不变。
- I-5: 三个资源的查询键精确为 `20260814-v7-batch-preview-repair-01` 且相等。
- 命令: `node --test src/test/js/batchSendTaskConsoleInteraction.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js`、`git diff --check`。

## 人工验收清单

### A-1: 仅切换收件范围
- 前置条件: 登录后台，打开“批量邮件任务控制台 > 手动执行”。
- 操作步骤: 1. 选择“中国”。2. 等待至少 500ms。
- 预期结果: 提示先为“当前条件命中 计算中…”，后为“当前条件命中 N 位专家（其中未联系 P、可重试 R）”；N/P/R 是数字，且只计中国。
- 覆盖: I-1、I-2。

### A-2: 预估服务错误
- 前置条件: 使用会使预估接口返回 400 的筛选请求。
- 操作步骤: 修改筛选并等待响应。
- 预期结果: 提示以“预估失败：”开头并含接口错误原因；页面不弹对话框、不创建执行记录。
- 覆盖: I-3。

### A-3: 弹窗视觉
- 前置条件: 打开批量邮件任务控制台。
- 操作步骤: 滚动手动执行面板至“发送控制”。
- 预期结果: 控制台正文为不透明面板，背景专家列表不可透出；背景页面仍被深色遮罩；底部“取消 / 保存任务”操作栏可见。
- 覆盖: I-4、S-1。

### A-4: 强制刷新
- 前置条件: 修复已发布。
- 操作步骤: 浏览器硬刷新后重复 A-1。
- 预期结果: 控制台和预估同时为修复后版本；不存在旧遮罩或旧预估行为。
- 覆盖: I-5。
