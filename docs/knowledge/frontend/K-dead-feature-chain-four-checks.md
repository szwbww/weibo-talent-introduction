---
id: K-dead-feature-chain-four-checks
domain: frontend
created: 2026-08-21
last_used: 2026-08-21
hit_count: 0
source: create-p:qa-gate-visibility
severity: P1
---

经验：报「某功能界面上根本没有 / 提示做得不好」时，先判定它到底是**没做好**还是**整条链是死的**。
本仓库出现过完整的死代码链（QA 覆盖键编辑器），四条判据缺一不可，任何一条成立就说明"提示优化"是伪命题：

1. **容器缺失**：`grep '<id>' index.html` 无结果，而 JS 里 `const el = $("#<id>"); if (!el) return;` 静默短路。
2. **函数零调用**：`grep '<fnName>' app.js` 只有 `function <fnName>(` 一处定义。
3. **state 永远空**：`state.<key>` 初始化为 `[]`/`{}` 后全文无第二次赋值；对应接口全文无请求。
4. **payload 缺字段**：`save<X>()` 组装的 body 里没有该字段 —— 后端因此走「未传值则保留库存值」分支，
   运营界面上的改动被静默丢弃。

实例（2026-08-21 实测）：`renderQaCoverageKeyOptions`（app.js:1978）与 `renderQaCoverageKeyLabels`（:2111）
均零调用；`state.qaCoverageKeys` 初始化后再无赋值；`#qaCoverageKeyOptions` 不在 index.html；
`saveQaRule`（:2877）payload 不含 `coverageKeys`。四条同时成立。

**恢复被删功能前，必先 grep 有无「断言其缺席」的契约测试。**
`src/test/js/qaCoverageKeyEditor.test.js:83-123` 有 5 条 `assert.doesNotMatch(...)` 主动锁定该 UI 保持删除
（含 `doesNotMatch(indexHtml, /id="qaCoverageKeyOptions"/)`、`doesNotMatch(saveFn, /coverageKeys/)`）。
不同步反转这些断言，功能一恢复全量测试即红。这是 [[K-ui-removal-retires-obsolete-contract-tests]] 的反向形态。

同一文件里可能同时存在**仍然有效**的死函数用例（该文件 :35-81 的 4 条仍在测零调用的
`renderQaCoverageKeyLabels`），所以"顺手清理死代码"同样会红。恢复功能时对死函数只增不删。

关联：[[K-dom-stub-tests-hide-dangling-refs]]（DOM stub 让容器缺失永远测不出来）、
[[K-ai-reply-modal-helper-scope]]。
