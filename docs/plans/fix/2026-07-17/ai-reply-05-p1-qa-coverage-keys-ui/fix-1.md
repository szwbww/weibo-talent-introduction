# P1-5 QA 覆盖能力标签管理界面：修复计划 1

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 5 / P1）
- 子计划：`docs/plans/2026-07-15/ai-reply-05-p1-qa-coverage-keys-ui.md`

## 约束摘录

- 页面只能以 `/api/qa/coverage-keys` 为 coverage key 目录单源；不得在前端硬编码 key 表。
- `loadQa()` 必须并行取得 categories、rules、coverage metadata；任一失败时 QA 编辑不能进入半加载状态。
- 既有空标签规则仍可编辑/保存；表单必须回显、提交及在关闭/新建时清理 coverage key 选择。
- 不回归 category、keywords、variants、占位符预览、enable/handoff；不改 `styles.css`，保留批量发送控制台现有改动。

## 修正记录

| P1 | 触发频率 | 问题 | 证据 |
|---|---|---|---|
| P1-1 | `/api/qa/coverage-keys` 暂时失败、权限/部署配置错误时 | `loadQa()` 吞掉 metadata 请求异常并以空数组继续渲染。打开既有有标签规则后选项区为空，保存会提交 `coverageKeys: []`，从而无提示清空原有审核能力标签。 | `src/main/resources/static/app.js:1640-1648,2457-2466,2487-2503` |

## 修复规格

### P1-1：metadata 失败时原子失败，禁止清空既有标签

文件：

- `src/main/resources/static/app.js`
- `src/test/js/qaCoverageKeyEditor.test.js`

变更：移除 coverage metadata 请求的 `catch(() => [])` 降级；三个 API 必须作为同一 `Promise.all` 成功后，才更新 `state.categories`、`state.qaRules`、`state.qaCoverageKeys` 并渲染 QA 页面。请求失败应沿用既有调用方错误提示，不得让编辑弹窗获得空 options 后可保存。新增运行时测试，模拟 coverage metadata reject，断言不会写入三项 QA state、不会渲染可编辑的半加载表单；成功路径仍校验 metadata 目录渲染。

预期行为：metadata 服务不可用时，运营不能通过 QA 管理界面保存并意外抹除任何 `coverageKeys`；服务恢复后，现有 keys 正常回显和提交。

## 当前状态

- 编译：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`；Surefire 1634 passed、0 failed、0 error、3 skipped。
- 测试：PASS — `node --test src/test/js/*.test.js`，300 passed、0 failed、0 skipped。
- 定向测试：PASS — `node --test src/test/js/qaCoverageKeyEditor.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js`，29 passed、0 failed。
- 未改实现；仅生成本修复计划。main 既有改动保持不动。

## 合规审计

- I-1 后端目录单源：✅ `src/main/resources/static/app.js:1614,1644` 从 state metadata 渲染，未硬编码 key；但 metadata 异常被吞掉，见 P1-1。
- I-2 空标签兼容：✅ `src/main/resources/static/app.js:1717-1726,1635-1637`；空列表显示 warn，编辑时不强制选择。
- I-3 表单 round trip：✅ `src/main/resources/static/app.js:2436-2444,2447-2466,2487-2503`；编辑回显、关闭清理、新建为空、提交 checked keys。
- I-4 现有 QA 管理：✅ `src/main/resources/static/app.js:2482-2500` 保留 variants 与现有 payload 字段；`src/main/resources/static/index.html:1566-1585` 保留原表单布局和占位符编辑器。
- T1 原子加载：❌ `src/main/resources/static/app.js:1641-1648` 对 coverage 请求 `catch(() => [])`，失败后仍写 state/render，造成可保存的半加载表单。
- T2 DOM/table：✅ `src/main/resources/static/index.html:340-346,1573-1578`；覆盖能力列、options、warning 均存在。
- T3 列表与样式契约：✅ `src/main/resources/static/app.js:1717-1757` 最多三项、空标签 warn、colspan 9；`src/main/resources/static/index.html:1573-1578` 复用 `.span-2`、`.metadata-grid`、`.badge.warn`，`styles.css` 无变更。
- T4 测试：⚠️ `src/test/js/qaCoverageKeyEditor.test.js:1-183` 主要为静态源码断言；未模拟 metadata 失败或实际 DOM round trip。metadata 失败的生产缺陷已列 P1；其余测试深度为 P2 观察。
- Deleted code：✅ 无本子计划要求删除的实现。
- No extras：✅ 本轮仅审计子计划列出的三份前端文件；未处理 main 的其他当前改动。

## 语义完整性检查

- Accumulation：✅ N/A，无时间窗口累计。
- State machine：✅ N/A，无状态机。
- Cross-plan：❌ Phase 4 API metadata 是 Phase 5 表单的目录契约；当前失败降级会将 Phase 4 已保存的 `coverageKeys` 通过 Phase 5 保存链路清空。

## 观察（非阻断）

- `row.title = escapeHtml(entry.description || "")`（`src/main/resources/static/app.js:1627-1631`）写入 DOM property 时会把正常的 `&` 等显示成实体文本；不构成注入或数据错误，可在 P1 修复时顺带用原始 description 赋值并以 DOM property 保持安全，但不单列任务。
