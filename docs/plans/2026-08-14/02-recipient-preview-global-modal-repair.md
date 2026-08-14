# 收件范围预估与全局弹框不透明修复

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction` (`main`)

## 需求描述

地区 picker 的每次选择在 500ms 后按当前地区显示专家预估数；预估失败显示接口返回的原因。所有继承 `.modal-content` 的弹框主体不透明，背景页面仍保留遮罩。

- 不变：预估只读、筛选含义、500ms 防抖、过期响应丢弃、发信与数据写入均不变。
- 不变：`.modal-overlay` 的 `rgba(15,23,42,.4)` 遮罩不变；非弹框面板继续使用 `--panel-bg` 玻璃效果；深色模式使用 `--bg-sidebar` 深色值。
- 范围外：不改预估接口、ES、数据库、服务端异常处理、发信路径。

## 关键不变量

### Invariant I-1: 地区切换触发对应预估
- Rule: `batchConfigEditorRegions` 调度 `editor`，`batchManualRegions` 调度 `manual`；后者仍保存 `manualDraft.regions`。
- Applies to: `notifyBatchRegionPickerChanged`。
- Violation consequence: 单独改地区不会刷新人数。
- 来源: K-recipient-count-preview-parity

### Invariant I-2: 手动预估与执行使用同一快照
- Rule: 预估和 `manual-executions` 共享一个完整 `BatchExecutionSnapshot` 构造函数，含 `roundsPerRun`、`regions` 与全部既有筛选/发送字段。
- Applies to: 手动快照构造与 `confirmManualExecution`。
- Violation consequence: 预估与实际收件人漂移。
- 来源: K-recipient-count-preview-parity

### Invariant I-3: 当前预估错误可见
- Rule: 当前序号的失败用 `textContent` 显示“预估失败：”和 `Error.message`，并 `console.warn`；过期响应不渲染、不弹窗。
- Applies to: `refreshRecipientPreview`。
- Violation consequence: HTTP 400 被笼统隐藏。
- 来源: original

### Invariant I-4: 所有标准弹框主体不透明
- Rule: `.modal-content` 使用 `background-color: var(--bg-sidebar)`；`.modal-overlay`、`--panel-bg`、批量专用 class 不改。
- Applies to: 任务进度、批量任务控制台、手动确认三个 `.modal-content` DOM 使用点。
- Violation consequence: 任何标准弹框都会二次透出被遮罩的背景。
- 来源: original

### Invariant I-5: 静态资源版本三键同步
- Rule: 样式、共享 runtime、app 三个 URL 使用同一个 `20260814-v7-batch-preview-repair-01`。
- Applies to: `index.html`。
- Violation consequence: 新旧 JS/CSS 混用。
- 来源: K-frontend-cache-key-triad

## 样式契约

### S-1: 全局标准弹框主体
- 复用：`.modal-content`，`src/main/resources/static/styles.css:4255`。全部 DOM 使用点：`src/main/resources/static/index.html:982`、`:1094`、`:1490`。就地修改此共享规则。
- 新增：无新 class。在该规则块将背景声明替换为且仅为：

```css
    background-color: var(--bg-sidebar);
```

- DOM 结构：保持三个使用点的既有 `class="modal-content ..."` 不变。
- 禁止项：不改 `.modal-overlay`、`--panel-bg`、`.task-modal.batch-send-task-modal`；不新增 inline style 或 class。

## 现状审计

### 预估与执行数据路径
- Schema: 后端 `BatchExecutionSnapshot` 读取 `regions`、`roundsPerRun`；`RecipientScope.fromSnapshot` 使用 `regions`。
- Write path: `confirmManualExecution` 提交 `ManualBatchExecutionRequest.snapshot`。
- Read path: `refreshRecipientPreview` POST 同一快照到 `recipients/preview`，后端只读 `countBySnapshot`。
- Interaction: 现有预估快照漏 `regions`、`roundsPerRun`；地区 picker 回调也未调度预估。

### 错误路径
- `api` 将非 2xx JSON `message` 转为 `Error.message`；预估 catch 当前丢弃该消息。
- Interaction: 当前 400 不能从页面诊断，但不应影响编辑或执行。

### 前端样式盘点
- `.modal-overlay`（`styles.css:4244`）是统一背景遮罩，颜色 `rgba(15,23,42,.4)`。
- `.modal-content`（`styles.css:4255`）使用半透明 `--panel-bg`，三个标准弹框均继承它。
- token：亮色 `--bg-sidebar: #ffffff`，深色 `--bg-sidebar: #111a2b`；`--panel-bg` 在两种主题均为 `.55` alpha。
- 基线：任务进度、批量控制台、手动确认的主体均透出遮罩背景；批量底栏单独是 `.96` 白底。

### 缓存键
- 当前三键均为 `20260813-v6-topnav-glass-01`；两个 JS 测试要求三键同值。

## 实现方案

### 1. 收件范围与同源快照 [I-1, I-2]
- 修改 `src/main/resources/static/app.js`：修正地区 picker 调度；提取共享手动快照并由预估、执行共同调用。
- 修改 `src/test/js/batchSendTaskConsoleInteraction.test.js`：覆盖编辑器/手动地区调度与完整共享快照。

### 2. 保留预估错误 [I-3]
- 修改 `src/main/resources/static/app.js`：当前失败写入纯文本错误并记录警告，保留序号 guard。
- 修改 `src/test/js/batchSendTaskConsoleInteraction.test.js`：覆盖当前失败和过期失败。

### 3. 全局标准弹框表面 [I-4, S-1]
- 修改 `src/main/resources/static/styles.css`：按 S-1 替换 `.modal-content` 背景声明。
- 修改 `src/test/js/batchSendTaskConsoleVisualFix.test.js`：断言通用规则为 `var(--bg-sidebar)`，遮罩仍为既有 rgba，批量专用 class 无单独背景覆盖。

### 4. 缓存隔离 [I-5]
- 修改 `src/main/resources/static/index.html`：三个版本键同步更新。
- 修改 `src/test/js/batchSendTaskConsoleVisualFix.test.js`：更新精确键断言。

## 变更文件清单

| 文件 | 变更 |
| --- | --- |
| `src/main/resources/static/app.js` | 地区调度、共享快照、错误提示 |
| `src/main/resources/static/styles.css` | 全局标准弹框不透明背景 |
| `src/main/resources/static/index.html` | 三资源缓存键 |
| `src/test/js/batchSendTaskConsoleInteraction.test.js` | 预估交互测试 |
| `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 样式与缓存测试 |

## 验收标准

- I-1: 两种地区 picker 各调度正确预估面板。
- I-2: 共享构造结果含 `regions`、`roundsPerRun`；预估和执行调用它。
- I-3: 当前 400 的消息显示；过期失败不覆盖后续状态。
- I-4 / S-1: `.modal-content` 精确使用 `var(--bg-sidebar)`；`.modal-overlay` 仍为 `rgba(15,23,42,.4)`；批量专用规则无背景声明。
- I-5: 三 URL 精确使用 `20260814-v7-batch-preview-repair-01` 且相等。
- 命令: `node --test src/test/js/batchSendTaskConsoleInteraction.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js`；`git diff --check`。

## 人工验收清单

### A-1: 仅选地区
- 前置条件: 登录，打开“批量邮件任务控制台 > 手动执行”。
- 操作步骤: 选择“中国”，等待 500ms。
- 预期结果: 依次显示“计算中…”和“当前条件命中 N 位专家（其中未联系 P、可重试 R）”；数字按中国筛选。
- 覆盖: I-1、I-2。

### A-2: 预估错误
- 前置条件: 构造使预估接口返回 400 的筛选参数。
- 操作步骤: 修改筛选并等待响应。
- 预期结果: 显示“预估失败：”及接口错误；无弹窗、无执行记录。
- 覆盖: I-3。

### A-3: 三种标准弹框
- 前置条件: 可打开任务进度、批量任务控制台、手动确认弹框。
- 操作步骤: 逐一打开。
- 预期结果: 三个主体均不透出背景页面；背景页面仍有深色遮罩；深色系统主题下主体为深色。
- 覆盖: I-4、S-1。

### A-4: 强刷
- 前置条件: 修复已发布。
- 操作步骤: 硬刷新后重复 A-1/A-3。
- 预期结果: 同时得到新 JS 和 CSS；无旧预估或半透明弹框。
- 覆盖: I-5。
