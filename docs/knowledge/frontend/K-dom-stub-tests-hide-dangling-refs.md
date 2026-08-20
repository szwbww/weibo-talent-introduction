---
id: K-dom-stub-tests-hide-dangling-refs
domain: frontend
created: 2026-08-06
last_used: 2026-08-19
hit_count: 5
source: create-p:batch-manual-execution-observability-p2
severity: P1
---

经验：仓库的前端测试用 `extractFn` + DOM stub（`sb.__store`）在 vm sandbox 里运行函数，
`document.getElementById` 永远返回 stub 元素。因此**即使真实 `index.html` 里的 DOM 已被删除**，
测试仍然全绿，函数在生产中却因 `if (!el) return;` 静默短路。

实例：`renderBatchSendAccountTable`（`app.js:5917`）依赖 `#batchSendProgressPanel` /
`#batchSendAccountTable`，这两个 id 在 `index.html` 中**已不存在**（随旧批量发送对话框移除），
但 `src/test/js/batchSendControls.test.js:238/244` 仍以 stub 断言它们被写入，测试通过。
结果：通用任务进度弹窗里的账号统计（`app.js:1142` 的调用点）实际从不渲染。

正确做法：删除或替换 UI 时，除了同步 JS 测试（[[K-batch-console-regression-contract]]），
还要对"按 id 取元素再写入"的渲染函数做一次 `grep <id> index.html` 存在性核对；
新增此类函数时，在测试中额外断言该 id 确实出现在 `index.html` 源文本里，
而不只是断言 stub 被写入。
