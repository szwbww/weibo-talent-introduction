# 材料索取前置：静态资源版本断言收敛

执行顺序：本计划 → `02-material-request-status-api.md` → `03-material-request-ui.md`。本计划仅维护测试，生产文件不变。

## 需求描述

- 可观察结果：11 个带 `?v=` 的静态资源仍必须使用同一版本值；后续 UI 发布只需更新 `index.html`，不会因九份测试各自写死旧值而失败。
- 必须保持：现有 11 个资源的名称、数量、相对顺序及「版本值全相等」断言。
- 不做：修改生产 UI、缓存策略、资源清单、邮件功能。

## 关键不变量

### Invariant I-1：同键且有效
- Rule：测试从 `index.html` 的 `styles.css?v=` 精确提取非空版本键，断言 11 个已注册资源各出现一次且都等于它；不能把「任意键都通过」当作测试结果。
- Applies to：本计划的九份 JS 测试中的静态资源断言。
- Violation consequence：页面可能混用新旧 JS/CSS 而测试仍绿。
- 来源：K-frontend-cache-key-triad（已用当前代码复核）。

### Invariant I-2：测试意图不丢
- Rule：保留现有顺序、数量、单资源存在性、CSS 缓存版本与语法检查等原断言；只替换版本字面量的来源。
- Applies to：九份 JS 测试。
- Violation consequence：测试改造成为削弱门禁。
- 来源：original。

## 现状审计

### 静态资源版本配置
- Schema/mapping：`src/main/resources/static/index.html:11-15,2110-2115` 注册 5 CSS + 6 JS，当前共同键为 `20260917-calendar-layout-align`。
- Write paths：项目内对版本键的显式写入点为 `index.html` 上述 11 个 `?v=` 属性；发布时人工修改这些字面值。此计划不修改该写路径。
- Read paths：`rg -l -F '20260917-calendar-layout-align' src/test/js` 精确命中下表九份测试；`meetingConfirmationAssets.test.js:24-62` 还断言 11 项总数与全等；`mailboxChatStyle.test.js:95` 检查单 CSS 键；`ragKnowledgeBasePage.test.js:333-347` 检查全集与顺序。
- Interaction points：`index.html` 写新键 → 九份测试读取。来源：K-frontend-cache-key-triad。
- 知识复核：`K-js-test-invocation-surface` 指明前端完整测试为 `node --test src/test/js/*.test.js`，`verify.sh` 只跑单文件。`K-mailbox-chat-css-byte-contract` 与本计划无样式修改关系，不用于变更判断。

## 实现方案

### T1：替换固定键读取（I-1、I-2）
- 只修改下表九份测试。每份仍检查其原有资源集合、顺序、数量和状态；将固定字符串/正则中的版本部分改为由 `index.html` 中 `styles.css?v=` 捕获的键。正则需要对捕获键转义，不能放宽资源名或匹配范围。
- `meetingConfirmationAssets.test.js` 继续断言恰好 11 项；`mailboxChatStyle.test.js` 继续断言 `mailbox-chat.css` 独立版本等于共同键。
- 执行前检查：重新从 `index.html` 读当前键，再 `rg -l -F '<当前键>' src/test/js`；若已非下表九份，应先修订本计划文件清单，不默默略过新命中。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 两处版本断言 |
| 2 | `src/test/js/checkRepliesRelocation.test.js` | `CACHE_KEY` 来源 |
| 3 | `src/test/js/mailboxChatStyle.test.js` | CSS 版本断言 |
| 4 | `src/test/js/manualReplySubjectPrefill.test.js` | `CACHE_KEY` 来源 |
| 5 | `src/test/js/meetingConfirmationAssets.test.js` | 11 项键值断言及正则 |
| 6 | `src/test/js/overlayAndDialogContrast.test.js` | `CACHE_KEY` 来源 |
| 7 | `src/test/js/ragKnowledgeBasePage.test.js` | 全集键值断言 |
| 8 | `src/test/js/ragWorkbenchRender.test.js` | `CACHE_KEY` 来源 |
| 9 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | `CACHE_KEY` 来源 |

共 9 文件，1 个测试子系统；生产代码与样式零文件。当前工作树中部分目标测试已有用户改动，执行时须在现有内容上最小修改，不覆盖或清理其它差异。

## 验收标准

- I-1：`node --test` 上述九份测试全部通过；临时将某一个 `index.html` 资源键改成不同值时，资源同键断言应失败，复原后再通过。
- I-2：diff 显示资源名/顺序/数量断言保留；`node --test src/test/js/*.test.js` 全量通过。
- `git diff --check` 通过；不修改生产资源或产品行为。

## 人工验收清单

### A-1：版本一致
- 前置条件：在测试环境部署原页面，不改动产品文件；可打开浏览器网络面板。
- 操作步骤：1. 刷新管理后台；2. 查看 5 个 CSS 和 6 个 JS 请求 URL。
- 预期结果：11 个 URL 均携带相同的 `v=20260917-calendar-layout-align`；页面原有「会议确认」按钮和人工回复编辑区可见。
- 覆盖：I-1、I-2；必须保持项。

人工验收开始时再由本节导出同名前缀的 `-acceptance.md`；现在不创建。
