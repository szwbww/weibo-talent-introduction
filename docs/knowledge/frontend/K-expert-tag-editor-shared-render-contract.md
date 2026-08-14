---
id: K-expert-tag-editor-shared-render-contract
domain: frontend
created: 2026-08-14
last_used: 2026-08-14
hit_count: 0
source: create-p:expert-detail-head
severity: P1
---

经验：`renderExpertTagEditor()` `app.js:3964-3992` 是**跨三个视图共用**的渲染函数，且其输出被一份
**逐字契约测试**锁死。想改它的 DOM 形态，只能加参数派生新分支，不能改默认输出。

**调用点全集（grep `renderExpertTagEditor`，4 处）**

| 调用点 | 视图 | 备注 |
|---|---|---|
| `app.js:4080` `updateExpertTagEditor` | 加删标签后 `editor.outerHTML = ...` 重渲染 | **不传第 5、6 参** |
| `app.js:4477` `renderMailboxExpertTagEditor` | 收发件箱（`:9031`、`:9600` 两处） | 有专门 CSS `styles.css:2132/2183/2246` |
| `app.js:6661` `showExpertDetail` | 专家详情（无 contact） | |
| `app.js:7035` `loadContactDetail` | 联系人详情 | |

**逐字契约**：`src/test/js/expertProfileAbsence.test.js:46-67` 的 `S1_EXPECTED`（无画像态）与
`S2_EXPECTED`（正常态）经 `normalizeWhitespace` 后与函数输出 `strictEqual` 比对（`:77 / :93 / :114`）。
改一个空格都会红。

**三个连带依赖**（改结构时容易漏）

1. `handleContactAction` 的 `expert-add-tag-open` `:8596` 与 `expert-remove-tag` `:8625` 用
   `element.closest(".expert-tag-editor")` 定位，再读 `dataset.orcid` / `dataset.level` / `editor.id` —— 三者必须保留。
2. `setTagEditorLoading()` `:4083-4102` 给编辑器加 `.tag-editor-loading`，该类带 `min-height: 72px`
   （`styles.css:3715-3718`）。任何**内联/行内**形态都必须覆盖回 `0`，否则加删标签时行高瞬间跳到 72px。
3. **`updateExpertTagEditor` 的形态丢失陷阱**：它调用时不传第 5、6 参，所以任何靠参数区分的形态在
   "加完标签重渲染"后会**退回默认形态**。必须把形态写进 `data-*` 并在重渲染时读回透传。
   这个 bug 只在"加删标签之后"复现，是最难自测发现的一类回归。

**`extractFn` 正则约束**：`expertProfileAbsence.test.js:13` 用 `function\s+NAME\s*\([^)]*\)` 抽取源码，
参数表内**不得出现右括号**（如默认值写成 `layout = f()` 会让整个测试文件报 `Could not find ...`）。

关联：[[K-ui-removal-retires-obsolete-contract-tests]]、[[K-detail-es-backed-fields-need-authoritative-read]]
