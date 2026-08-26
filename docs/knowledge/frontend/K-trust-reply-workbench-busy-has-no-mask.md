---
id: K-trust-reply-workbench-busy-has-no-mask
domain: frontend
created: 2026-08-21
last_used: 2026-08-21
hit_count: 0
source: create-p:ui-tweaks-02-overlay-and-dialog-contrast
severity: P2
---

经验：可信回复工作台（`trust-reply-workbench.js`）共有 **6 种**并行的忙碌状态，
但**一种都不出遮罩** —— 运营只能从零散的 `disabled` 和一行状态文字判断「是不是在转」，
所以反馈「操作遮罩不明显」。6 种忙碌位与置位点：

| 状态位 | 置位点 | 今天的全部可见反馈 |
|---|---|---|
| `state.generation.pending` | `:919`, `:1275` | 状态条 + 工具栏「取消生成」+ 控件 disabled |
| `request.pending`（逐条） | `:1014` | 该条按钮文案「生成中…」 |
| `state.factChangePending` | `:1602` | 仅事实按钮 tooltip |
| `state.stateSavePending` | `:964`, `:1235` | 按钮文案「保存中…」 |
| `state.frameSavePending` | 框架保存链路 | 仅 tooltip |
| `state.completePending` | `:2005` | 「完成」按钮 disabled |

补遮罩时的三条硬约束：

1. **必须写进 `renderMarkup()`**（`:2052`）。`render()`（`:2036`）每次都
   `host.innerHTML = renderMarkup()` 全量重建，任何 `createElement + appendChild`
   挂上去的遮罩下一次状态变更就被冲掉。这正是 [[K-ai-reply-loading-panel]] 记录过的同类坑，
   只是 `app.js` 侧的 `.ai-chat-panel` 容器不会被重写，工作台侧会。
2. **锚点是 `.trust-reply-workbench .reply-workflow-content`（`styles.css:7226`），
   不是 `.trust-reply-workbench`**。后者虽然已有 `position: relative`（`styles.css:7223`，
   一个没有绝对定位后代的空钩子），但它包住 `<summary>` —— 遮罩会盖住折叠标题，
   生成期间连折叠都点不了。全仓 169 处 `trust-reply` 选择器里 `position:` 声明只有
   `relative`（`:7223`）与 `.trust-reply-summary{static}`（`:7542`）两处，**无 absolute**，
   故给内容区加 `position: relative` 不会改动任何既有元素。
3. **遮罩里的按钮只能用既有委托 action**（`data-action="cancel-generation"`，
   由 `onClick`/`:2325` 的 `closest("[data-action]")` 接管）。遮罩一旦盖住工具栏，
   工具栏里那枚「取消生成」（`:2063`）就点不到，遮罩必须自带同 action 的按钮，
   否则「补遮罩」会顺手弄丢取消能力。

另：`src/test/js/trustReplyWorkbench.test.js:573` 断言 `!/style=/.test(host.innerHTML)`，
遮罩标记不得带 inline style。

关联：[[K-ai-reply-loading-panel]]、[[K-ai-reply-modal-helper-scope]]
