# Fix Plan 1: cv-5-fe-split-preview

## 原计划 / 子计划引用

- 原计划: `docs/plans/2026-07-09/cv-5-fe-split-preview.md`
- 复验对象: `cv-5-fe-split-preview`
- 复验日期: 2026-07-09

## 约束摘录

- I-1 分屏几何: `body.preview-docked` 驱动 460px 停靠层，modal 右 padding 让出空间，窄屏 `<1200px` 回退 overlay。
- I-2 插入变量唯一入口: 删除平铺 chips；变量插入走 `.var-insert-btn` 浮层；插入位置为光标处，输入框无焦点时追加末尾。
- I-3 预览同步: 左侧 input debounce 600ms 触发预览；模板请求带 `variantIndex`；响应用 requestId 丢弃过期结果。
- I-4 变体组合切换器: 停靠层头部展示；上限来自 `variantPoolSize`；切换只改 `variantIndex` 并重发预览，不污染保存 payload。
- I-5 useVariants 勾选贯通: 人工回复面板默认不勾；suggest query 与 send body 均传 `useVariants`；切换后立即重拉 suggest。
- S-1/S-2/S-3: CSS/DOM 按计划删除旧入口并迁移模板预览到停靠层。

## 修正记录表

| ID | Severity | 触发频率 | 问题 |
|---|---|---:|---|
| P1-1 | P1 | 常见：用户打开变量菜单前未聚焦目标输入框时触发 | I-2 要求“无焦点时追加末尾”，但当前 `insertVarAtCursor` 直接使用 `selectionStart`，未聚焦新输入默认位置通常为 0，变量会插到开头。 |

## 修复规格

### P1-1: 未聚焦变量插入必须追加末尾

- 文件: `src/main/resources/static/app.js`
- 位置: `insertVarAtCursor()` 与 `.var-chip` click 处理链。
- 现状证据:
  - `src/main/resources/static/app.js:1874-1880` 直接读取 `textarea.selectionStart` / `selectionEnd` 并切片插入。
  - `src/main/resources/static/app.js:1914-1923` 点击变量 chip 后直接调用 `insertVarAtCursor(textarea, insertText, cursorOffset)`，未传入“目标是否当前聚焦”或最后光标状态。
- 期望行为:
  - 若目标输入框是当前 activeElement，继续按当前 selection 插入。
  - 若目标输入框不是当前 activeElement，但此前记录过该字段最后一次 selection，可按最后 selection 插入。
  - 若目标输入框从未聚焦或没有有效 selection，插入点必须为 `textarea.value.length`，即追加末尾。
  - 修复不得引入第二套变量元数据，不得恢复 `var-chip-bar` / `var-preview-btn`。
- 建议实现:
  - 在 `initVarEditorForTextarea` 中记录 `focus` / `keyup` / `mouseup` / `select` 时的 `{start,end}` 到 `textarea.dataset` 或弱映射。
  - `insertVarAtCursor` 内判断 `document.activeElement === textarea`；否则优先用记录值，没有记录则使用末尾。
  - 补一条 JS 单测或最小函数测试覆盖“未聚焦目标追加末尾”。

## 当前状态

- Build: PASS
- Tests:
  - `node --test src/test/js/*.test.js`: PASS, 198 passed, 0 failed, 0 skipped.
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`: PASS, 1321 tests, 0 failures, 0 errors, 3 skipped.
- Diff hygiene:
  - `git diff --check`: PASS
  - `git diff --cached --check`: PASS

## 合规审计

- I-1 分屏几何: ✅ `src/main/resources/static/app.js:2094-2144` 关闭/打开切 `preview-docked`; `src/main/resources/static/styles.css:6663-6705` 460px 停靠、无 backdrop、无 transform、窄屏 overlay 回退。
- I-2 插入变量唯一入口: ❌ `src/main/resources/static/index.html:1354-1356`, `1423-1425`, `1476-1478`, `src/main/resources/static/app.js:6829` 已改为 `.var-insert-btn`; `rg var-chip-bar|compose-template-variable-row|var-preview-btn|compose-template-side` 零命中；但 `src/main/resources/static/app.js:1874-1880` 未满足“无焦点追加末尾”。
- I-3 预览同步: ✅ `src/main/resources/static/app.js:1735-1740` debounce 600ms; `src/main/resources/static/app.js:6938-6960` requestId 防旧响应; `src/main/resources/static/app.js:9530-9534` 绑定模板输入/专家/发件邮箱变更。
- I-4 变体组合切换器: ✅ `src/main/resources/static/index.html:1562-1566` 停靠层头部切换器; `src/main/resources/static/app.js:1751-1778` `variantPoolSize` 控制显示与循环; `src/main/resources/static/app.js:6949` 请求带 `variantIndex`; 保存 payload `src/main/resources/static/app.js:7017-7038` 不含 `variantIndex`。
- I-5 useVariants 勾选贯通: ✅ `src/main/resources/static/app.js:7992-7997` 默认 false 并 change 重拉; `src/main/resources/static/app.js:8004-8009` suggest query 传 `useVariants`; `src/main/resources/static/app.js:8803` send body 传 `useVariants`。
- Semantic accumulation check: ✅ no time-window counters.
- State machine check: ✅ no new state machine.
- Cross-plan check: ✅ CV-1/CV-3 contract present for `variantPoolSize` / `useVariants`; frontend reads both at cited lines.
- Deleted code: ✅ old chip/side/preview button selectors grep zero.
- No extras: ⚠ 当前 worktree 有大量 CV-5 范围外未提交文件；未作为本 P1 阻断，但提交前需按对应 CV-1..CV-4 计划分别复验或拆分。
