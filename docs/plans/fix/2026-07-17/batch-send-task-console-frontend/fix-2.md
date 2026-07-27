# 批量邮件任务控制台前端：fix-2

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-14/batch-send-task-console-frontend.md`
- 前序复验：`docs/plans/fix/batch-send-task-console-frontend/fix-1.md`
- 复验轮次：2/3

## 约束摘录

- I-2：标签比较须 trim、去重、排序后进行；仅表现差异不得标记为配置修改。
- 有来源时 diff、确认差异表均须以规范化值为准。
- 变更只限批量邮件任务控制台前端及其回归测试。

## 修正记录表

| P1 | 问题 | 触发频率 |
|---|---|---|
| P1-1 | `normalizeManualSnapshot()` 对标签 trim、排序但未去重；同一标签仅因重复输入即可被误判为配置差异，确认框也会显示伪变更。 | 操作员输入或粘贴含重复标签时，稳定复现。 |

## 修复规格

### P1-1：按集合语义规范化标签

文件：

- `src/main/resources/static/app.js`
- `src/test/js/` 中覆盖批量任务控制台 diff 契约的测试文件

变更：在 `normalizeManualSnapshot()` 中将标签先 trim、过滤空值、去重、再排序；`computeManualDiffs()` 保持使用该规范化结果。补充回归测试：baseline 为 `['AI']`、draft 为 `[' AI ', 'AI']` 时不产生标签 diff；实际新增不同标签时仍产生 diff。

预期：重复或空白差异不出现红框、原值提示和确认差异行；真实标签集合变化仍被识别。

## 当前状态

- 编译：PASS（`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 已完成编译）。
- JVM 测试：FAIL — 721 tests，12 failures，1 skipped；失败位于既有 `ManualInitialOutreachServiceTest`、`MailAutomationControllerTest`，不属于本计划前端范围。
- 前端测试：PASS — `node --test src/test/js/*.test.js`，241 passed，0 failed。
- JS 语法：PASS（前端测试已加载 `app.js`）；`git diff --check`：PASS。

## 合规审计

- I-1 tab 隔离：✅ `src/main/resources/static/app.js:12417-12423` 行“手动”保留含 `id/updatedAt` 的来源和独立 draft；`12133-12136` 只在直接进入手动 tab 且无来源时重置；`12074-12082` 关闭时清理状态、确认框、抽屉和 timer。
- I-2 规范化 diff：❌ `src/main/resources/static/app.js:12572-12584` 标签只 `trim/filter/sort`，未去重；`12606-12626` 因而把重复标签判为差异。
- I-3 独立确认：✅ `src/main/resources/static/app.js:12687-12726` 三类确认内容独立；`12734-12771` 确认后才 POST，按钮先禁用，失败后恢复；`src/main/resources/static/index.html:1309-1322` 使用专用 `#batchManualConfirmDialog`。
- I-4 日志数量守恒与时间线：✅ `src/main/resources/static/app.js:12963-12995` 展示数量、耗时并警示不守恒；`12997-13019` 原因按数量降序且保留空态；`13048-13064` 渲染批次时间线；`12931-12945` RUNNING 每 3 秒刷新并在切换/关闭时清理 timer。
- I-5 危险操作与重复提交：✅ `src/main/resources/static/app.js:12245-12254` 删除二次确认并保留失败行；`12734-12771` 执行确认防重复提交，失败保留确认内容。
- 样式契约：✅ `src/main/resources/static/styles.css:7295-7476` 使用命名空间规则，未修改全局 `.task-modal/.tabs/.data-table`。
- 回归测试契约：✅ `src/test/js/expertTagBatchFix.test.js:198-212` 和 `src/test/js/taskModalStateMachine.test.js:1215-1260` 已转向任务控制台、移除旧 `batchSendType`/旧启动路径断言。
- No extras：✅ 改动仅涉及计划允许的 3 个前端文件和修正记录纳入的 2 个 JS 测试文件。

### 语义完整性检查

- Accumulation check：✅ N/A（本前端计划未实现时间窗口计数）。
- State machine check：✅ N/A（无新后端状态机；日志轮询对关闭、切换和非 RUNNING 均有清理路径）。
- Cross-plan check：✅ 配置 CRUD 提供的未删除配置、稳定 `id/updatedAt` 已由 `app.js:12417-12423,12752-12775` 保留并传入执行；执行与日志按相同 `configId` 关联。P1-1 仅为前端标签集合比较，未破坏接口契约。
