# 专家联系页默认宽列表布局

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction` (`main`)

## 需求描述

专家联系页在没有保存过个人布局宽度时，默认使用截图中间预设的宽列表布局：左侧专家列表 500px。首屏 CSS 与运行时默认同步，避免先显示窄列表再跳变。

- 不变：已有 `contacts-list-width` 本地偏好、拖拽、三个预设按钮、200px–`min(800px, viewport*60%)` 边界及 1024px 以下单栏布局。
- 范围外：不删除重复的宽列表按钮、不改详情内容、不改服务端、筛选或专家数据。

## 关键不变量

### Invariant I-1: 保存的个人宽度优先
- Rule: 有效 `localStorage["contacts-list-width"]` 时，初始化继续应用该值且不覆写；只有键缺失时采用 500px 默认。
- Applies to: `initLayoutResizer` 初始化分支。
- Violation consequence: 用户拖拽后的布局被默认值覆盖。
- 来源: K-contacts-layout-width-preference

### Invariant I-2: 默认与重置均为中间预设
- Rule: `resetToDefault`、无保存值初始化、默认按钮和中缝双击均应用 500px；默认按钮 title 必须写 `默认分栏 (500px)`。
- Applies to: `initLayoutResizer` 与 `#btnLayoutDefault`。
- Violation consequence: 页面默认和重置行为不一致。
- 来源: K-contacts-layout-width-preference

### Invariant I-3: 首屏 CSS 不回退到窄列表
- Rule: `.contacts-layout` 的基础与 `max-width:1280px` 直接规则均为 `500px 6px minmax(0, 1fr)`；`max-width:1024px` 单栏规则不变。
- Applies to: `styles.css`。
- Violation consequence: JS 加载前或禁用 JS 时仍显示旧 260/280px 布局。
- 来源: K-contacts-layout-width-preference

### Invariant I-4: 静态资源缓存键同值
- Rule: `styles.css`、`trust-reply-workbench.js`、`app.js` 三键同步更新为 `20260814-v8-expert-layout-default-01`。
- Applies to: `index.html`。
- Violation consequence: 浏览器混用旧布局资源。
- 来源: K-frontend-cache-key-triad

## 样式契约

### S-1: 专家联系页桌面分栏
- 复用：`.contacts-layout`，`src/main/resources/static/styles.css:759` 和 `:3980`；唯一 DOM 使用点为 `src/main/resources/static/index.html:645`。就地修改两个规则。
- 新增：无新 class。两个规则中的列声明均替换为：

```css
    grid-template-columns: 500px 6px minmax(0, 1fr);
```

- DOM 结构：保持不变。

```html
<div class="split-layout contacts-layout">
```

- 禁止项：不改 `@media (max-width: 1024px)` 的单栏规则，不改 `.layout-resizer` 或 `.layout-preset-btn` 样式。

### S-2: 默认布局按钮说明
- 复用：`.layout-preset-btn`，`src/main/resources/static/styles.css:1173`；仅改 `#btnLayoutDefault` 的 `title` 属性，不新增 CSS 或 class。
- DOM 结构：

```html
<button class="layout-preset-btn" id="btnLayoutDefault" title="默认分栏 (500px)">▏</button>
```

- 禁止项：不改按钮 id、图标、宽列表/等宽按钮或 inline style。

## 现状审计

### 本地布局状态
- Store: 浏览器 `localStorage["contacts-list-width"]`。
- Write paths: `setListWidth(width, true)` 在拖拽、默认按钮、宽列表按钮与等宽按钮后写入。
- Read path: `initLayoutResizer` 读取后优先 `setListWidth(parseInt(savedWidth), false)`；缺失时 `resetToDefault()`。
- Interaction points: `resetToDefault` 当前设 260px；CSS 基础为 260px、1280px 以下为 280px，均与中间 500px 预设不一致。

### 前端样式盘点
- 可复用 class: `.contacts-layout`（`styles.css:759/:3980`）与 `.layout-preset-btn`（`:1173`）。
- 设计基准: 中间预设事件固定调用 `setListWidth(500)`；中缝为 `6px`；右栏为 `minmax(0, 1fr)`。
- 改动前基线: 默认按钮 title 是 `默认分栏 (260px)`；无保存值时重置到 260px。

### 缓存键
- 当前待发布三键均为 `20260814-v7-batch-preview-repair-01`，必须作为同一组改为 v8。

## 实现方案

### 1. 统一运行时默认 [I-1, I-2]
- 修改 `src/main/resources/static/app.js`：将 `resetToDefault` 的宽度改为 500；保留已保存宽度分支及边界逻辑。
- 修改 `src/main/resources/static/index.html`：默认按钮 title 改为 500px。
- 修改 `src/test/js/contactsLayoutDefault.test.js`：覆盖无保存值初始化/双击重置为 500 与保存值优先。

### 2. 同步首屏样式 [I-3, S-1, S-2]
- 修改 `src/main/resources/static/styles.css`：按 S-1 的逐字声明替换两个桌面 `.contacts-layout` 规则。
- 修改 `src/test/js/contactsLayoutDefault.test.js`：断言两个直接 CSS 规则为 500，1024px 单栏规则仍存在。

### 3. 缓存键 [I-4]
- 修改 `src/main/resources/static/index.html`：三个资源 URL 同步改为 v8。
- 修改 `src/test/js/batchSendTaskConsoleVisualFix.test.js`：更新三键精确断言，保留三键同值契约。

## 变更文件清单

| 文件 | 变更 |
| --- | --- |
| `src/main/resources/static/app.js` | 默认/重置宽度 |
| `src/main/resources/static/styles.css` | 两个桌面首屏宽度 |
| `src/main/resources/static/index.html` | 默认按钮说明、缓存三键 |
| `src/test/js/contactsLayoutDefault.test.js` | 布局持久化与样式测试 |
| `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 缓存键断言 |

## 验收标准

- I-1: 保存 `360` 时初始化左栏为 360px 且不重写该值；无键时为 500px。
- I-2: 默认按钮和中缝双击后为 500px，并保存该值；title 精确为 `默认分栏 (500px)`。
- I-3 / S-1: 两条桌面 `.contacts-layout` 规则均为指定声明；1024px 单栏规则存在。
- I-4: 三个 URL 精确为 `20260814-v8-expert-layout-default-01` 且相同。
- 命令: `node --test src/test/js/contactsLayoutDefault.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js`；`git diff --check`。

## 人工验收清单

### A-1: 新默认布局
- 前置条件: DevTools 清除本站点 `contacts-list-width`。
- 操作步骤: 刷新并进入“专家联系”。
- 预期结果: 左栏宽 500px，右侧详情栏仍可见；布局与截图中间预设一致。
- 覆盖: I-1、I-2、I-3。

### A-2: 个人布局保留
- 前置条件: 已拖拽左栏并保存 360px。
- 操作步骤: 刷新页面。
- 预期结果: 左栏仍为 360px，不变成 500px。
- 覆盖: I-1。

### A-3: 重置操作
- 前置条件: 左栏为 360px。
- 操作步骤: 单击默认图标或双击中缝。
- 预期结果: 左栏变为 500px；再次刷新仍为 500px。
- 覆盖: I-2。

### A-4: 窄屏
- 前置条件: 浏览器宽度不大于 1024px。
- 操作步骤: 进入“专家联系”。
- 预期结果: 专家列表与详情为单栏；无可见中缝和布局预设按钮。
- 覆盖: I-3。
