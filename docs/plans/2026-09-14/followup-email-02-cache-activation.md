# 跟进邮件：静态资源缓存激活

> 状态：待执行。必须在 `followup-email-01-manual-anchor.md` 实现并验证后执行；本计划只激活该功能的浏览器资源版本。
>
> 上位约束：[`00-followup-email-main.md`](00-followup-email-main.md)。如有冲突，以总计划的顺序、文件边界和发布门禁为准；缓存键细节仍以本计划为准。

## 需求描述

把 `index.html` 中 9 个带版本号的静态资源缓存键从 `20260910-meeting-generic-template` 统一更新为 `20260914-followup-email`，并同步当前反向检索得到的 9 个固定键测试文件，使普通刷新能够加载前一计划修改的 `styles.css` 与 `mailbox-chat.js`。

必须保持：

- 4 个 CSS、5 个 JS 的文件名、数量和加载顺序不变。
- `task-modal-runtime.js` 继续不带 `?v=`，位置仍在 5 个版本化 JS 之前。
- 所有版本化资源使用同一个新键；仓库相关范围内旧键归零。
- 固定值断言继续保留，不得改成弱断言或删除测试。

范围外：

- 不修改任何 CSS、JavaScript、Kotlin、数据库或业务行为。
- 不增加资源、预加载、构建插件或运行时缓存管理。
- 不处理 `followup-email-01-manual-anchor.md` 尚未通过验证的情况。

## 关键不变量

### Invariant I-1: 九个资源必须使用同一新键
- Rule: `index.html` 中恰好 9 个 `?v=`，值全部为 `20260914-followup-email`；`20260910-meeting-generic-template` 在本计划 10 个文件中必须零命中。
- Applies to: `index.html`、9 个固定键测试。
- Violation consequence: 浏览器混用新旧 CSS/JS，或构建测试与生产入口不一致。
- 来源: K-frontend-cache-key-triad

### Invariant I-2: 资源集合与顺序不变
- Rule: CSS 顺序固定为 `styles.css`、`expert-materials.css`、`mailbox-chat.css`、`meeting-confirmation.css`；JS 顺序固定为无版本号的 `task-modal-runtime.js`，再依次为 `trust-reply-workbench.js`、`expert-materials.js`、`meeting-confirmation.js`、`mailbox-chat.js`、`app.js`。只替换查询参数值。
- Applies to: `index.html:11-14,2109-2114`。
- Violation consequence: 样式层叠或脚本初始化顺序改变。
- 来源: original；K-frontend-cache-key-triad

### Invariant I-3: 测试锁与入口同步
- Rule: 必须按旧键精确反查得到当前 9 个测试文件并逐一替换；测试名称、注释、常量、正则和 includes 中的旧键都必须同步，新键反查集合必须恰好等于 `index.html + 9 个测试文件`。
- Applies to: 变更文件清单中的 10 个文件。
- Violation consequence: 漏改导致测试失败；弱化测试则失去缓存版本发布保护。
- 来源: K-frontend-cache-key-triad

### Invariant I-4: 只做前一计划的发布激活
- Rule: 本计划不得改资源正文、DOM、样式规则、接口或状态；执行前必须确认前一计划自动验证已通过。
- Applies to: 全部改动。
- Violation consequence: 缓存修改与功能修改混合，无法独立审查或回退。
- 来源: original

## 样式契约

### S-1: 页面资源注册
- 复用：现有 `<link>` 与 `<script>` 标签、文件名和顺序。
- 新增 CSS class：无。
- DOM 结构只允许查询参数替换为以下结果：

```html
<link rel="stylesheet" href="styles.css?v=20260914-followup-email">
<link rel="stylesheet" href="expert-materials.css?v=20260914-followup-email">
<link rel="stylesheet" href="mailbox-chat.css?v=20260914-followup-email">
<link rel="stylesheet" href="meeting-confirmation.css?v=20260914-followup-email">
<!-- 页面正文不变 -->
<script src="task-modal-runtime.js"></script>
<script src="trust-reply-workbench.js?v=20260914-followup-email"></script>
<script src="expert-materials.js?v=20260914-followup-email"></script>
<script src="meeting-confirmation.js?v=20260914-followup-email"></script>
<script src="mailbox-chat.js?v=20260914-followup-email"></script>
<script src="app.js?v=20260914-followup-email"></script>
```

- 禁止项：新增或删除资源；改变标签属性、文件名或顺序；修改 CSS；inline style。

## 现状审计

### 静态资源注册
- Schema/mapping: `src/main/resources/static/index.html:11-14` 注册 4 个版本化 CSS；`:2109-2114` 注册 1 个无版本 JS 和 5 个版本化 JS。当前 9 个版本化资源的键均为 `20260910-meeting-generic-template`。
- Write path: 本计划唯一生产写点是上述 9 个查询参数；无运行时 store、localStorage、数据库或服务端写入。
- Read paths: 浏览器把查询参数作为资源 URL 的一部分并据此命中或绕过缓存；资源内容仍从相同文件读取。
- Interaction points: 前一计划修改 `styles.css/mailbox-chat.js` → 本计划的新查询参数产生新 URL → 普通刷新获取新资源。

### 固定缓存键测试
- 当前键 `20260910-meeting-generic-template` 的精确反向检索除 `index.html` 外命中：
  1. `src/test/js/batchSendTaskConsoleVisualFix.test.js:53,59`
  2. `src/test/js/checkRepliesRelocation.test.js:11`
  3. `src/test/js/mailboxChatStyle.test.js:95`
  4. `src/test/js/manualReplySubjectPrefill.test.js:13`
  5. `src/test/js/meetingConfirmationAssets.test.js:5,21,28,57-58`
  6. `src/test/js/overlayAndDialogContrast.test.js:22`
  7. `src/test/js/ragKnowledgeBasePage.test.js:333,337,341,346`
  8. `src/test/js/ragWorkbenchRender.test.js:20`
  9. `src/test/js/trustReplyWorkbenchSharedMount.test.js:5,22,175`
- Write paths: 这些文件中的常量、字符串、正则、注释和测试名称是测试夹具；逐字替换键值，不改断言结构。
- Read paths: Node 测试读取 `index.html`，验证键值、资源数量与顺序；Maven 测试阶段通过 `exec-maven-plugin` 运行 JS 测试。（来源: K-js-tests-run-via-exec-plugin）
- Interaction points: `index.html` 新键必须与每个测试夹具同步，否则定向 Node 测试或 Maven 构建失败。

### 前端样式盘点
- 可复用 class：本计划不触碰 class。
- 设计 token：本计划不触碰 `styles.css` 或任何设计 token。
- DOM 结构：仅按 S-1 替换现有标签的 `?v=` 值；标签本身不变。
- 改动前基线：4 CSS + 1 无版本 JS + 5 版本化 JS；改动后仍完全相同。

## 实现方案

### 阶段 1：确认依赖计划和反向检索基线
- 确认 `followup-email-01-manual-anchor.md` 的自动验证已经通过，尤其是 Kotlin、全部 JS 测试与 Maven 测试。
- 执行 `rg -l "20260910-meeting-generic-template" src/main/resources/static/index.html src/test/js | sort`，输出必须恰好是变更文件清单中的 10 个文件。遵守 I-3、I-4。

### 阶段 2：同步替换缓存键
- 在 `src/main/resources/static/index.html` 的 9 个版本化资源 URL 中逐字替换为 `20260914-followup-email`，不改其余字符。遵守 I-1、I-2。
- 在 9 个测试文件中逐字替换所有旧键，包括测试名称、注释、常量、正则和 includes。不得重写断言。遵守 I-1、I-3。

### 阶段 3：验证发布激活
- 先验证旧键零命中、新键反查集合恰好为 10 个文件，再运行 9 个定向 JS 测试、全部 JS 测试和 Maven 测试。
- 人工普通刷新真实页面，按人工验收清单确认请求 URL、资源顺序和跟进入口。

## 变更文件清单

共 10 个文件，1 个子系统（前端静态入口与其测试），无新增文件：

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | 9 个资源统一替换为新缓存键 |
| 2 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 同步固定键字符串 |
| 3 | `src/test/js/checkRepliesRelocation.test.js` | 同步 `CACHE_KEY` |
| 4 | `src/test/js/mailboxChatStyle.test.js` | 同步 mailbox CSS URL 正则 |
| 5 | `src/test/js/manualReplySubjectPrefill.test.js` | 同步 `CACHE_KEY` |
| 6 | `src/test/js/meetingConfirmationAssets.test.js` | 同步注释、常量、名称与正则 |
| 7 | `src/test/js/overlayAndDialogContrast.test.js` | 同步 `CACHE_KEY` |
| 8 | `src/test/js/ragKnowledgeBasePage.test.js` | 同步名称、includes 与键值断言 |
| 9 | `src/test/js/ragWorkbenchRender.test.js` | 同步 `CACHE_KEY` |
| 10 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 同步注释、常量与测试名称 |

## 验收标准

自动验证命令：

```bash
test -z "$(rg -l '20260910-meeting-generic-template' src/main/resources/static/index.html src/test/js || true)"
rg -l '20260914-followup-email' src/main/resources/static/index.html src/test/js | sort
node --test \
  src/test/js/batchSendTaskConsoleVisualFix.test.js \
  src/test/js/checkRepliesRelocation.test.js \
  src/test/js/mailboxChatStyle.test.js \
  src/test/js/manualReplySubjectPrefill.test.js \
  src/test/js/meetingConfirmationAssets.test.js \
  src/test/js/overlayAndDialogContrast.test.js \
  src/test/js/ragKnowledgeBasePage.test.js \
  src/test/js/ragWorkbenchRender.test.js \
  src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/*.test.js
mvn test
git diff --check
```

判定：

- 旧键命令无输出；新键命令恰好输出变更文件清单中的 10 个文件。
- 9 个定向 JS 测试、全量 JS 测试、Maven 测试和 `git diff --check` 全部退出码为 0。
- `index.html` 中恰好 9 个 `?v=20260914-followup-email`；资源文件名、数量与顺序满足 I-2。
- diff 中没有资源正文、业务代码或断言结构变化。

## 人工验收清单

### A-1: 旧缓存下普通刷新
- 前置条件: 两个计划均已部署；浏览器已经加载旧缓存版本。
- 操作步骤: 1. 不清缓存；2. 普通刷新收发件箱；3. 展开任一已联系专家的人工回复区；4. 点击“跟进邮件”。
- 预期结果: 工具栏显示“跟进邮件”；点击后出现标题“生成跟进邮件”的弹窗、未选中的邮件列表和禁用的“填入人工回复”按钮。
- 覆盖: I-1、I-4、需求描述 observable outcome

### A-2: 九个版本化资源
- 前置条件: 打开浏览器网络面板，保留全部 CSS/JS 请求。
- 操作步骤: 1. 普通刷新页面；2. 按 URL 查询参数筛选 `20260914-followup-email`；3. 分别统计 CSS 与 JS。
- 预期结果: 恰好 4 个 CSS 和 5 个 JS 请求带 `v=20260914-followup-email`；没有请求携带 `20260910-meeting-generic-template`。
- 覆盖: I-1、S-1、需求描述“必须保持”第 1、3 项

### A-3: 加载顺序保持
- 前置条件: 网络面板已记录一次完整页面刷新。
- 操作步骤: 1. 按开始时间查看 CSS 与 JS；2. 核对文件名及顺序。
- 预期结果: CSS 依次为 `styles.css`、`expert-materials.css`、`mailbox-chat.css`、`meeting-confirmation.css`；JS 依次为无版本号的 `task-modal-runtime.js`、`trust-reply-workbench.js`、`expert-materials.js`、`meeting-confirmation.js`、`mailbox-chat.js`、`app.js`。
- 覆盖: I-2、S-1、需求描述“必须保持”第 1、2 项

### A-4: 既有页面回归
- 前置条件: 保存部署前相同视口下的收发件箱、可信回复工作台和会议确认截图。
- 操作步骤: 1. 部署后使用相同视口打开三个区域；2. 切换会话；3. 打开并取消会议确认；4. 打开可信回复工作台。
- 预期结果: 除人工回复工具栏新增“跟进邮件”及其弹窗外，三个区域的元素数量、文案、位置和交互与部署前截图相同。
- 覆盖: I-4、需求描述“必须保持”第 1 项、S-1

### A-5: 固定键测试仍生效
- 前置条件: 部署候选代码位于验收工作区，Node.js 可用。
- 操作步骤: 1. 运行验收标准中的 9 文件 `node --test` 命令；2. 查看进程退出码和失败数。
- 预期结果: 退出码为 0，失败数为 0；输出包含 9 个测试文件且没有 skip。
- 覆盖: I-3、需求描述“必须保持”第 4 项
