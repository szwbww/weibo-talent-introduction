# 批量邮件任务控制台前端：fix-1

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-14/batch-send-task-console-frontend.md`
- 复验轮次：1/3

## 约束摘录

- I-1：从定时任务行进入手动页必须携带配置、建立 baseline；直接点手动 tab 才清空来源。
- I-2：未选择配置不计算 diff；来源选择和 draft 不可互相污染。
- I-3：有来源的确认和 POST 必须携带配置上下文；确认不可绕过。
- I-4：配置日志须展示执行明细、原因、错误样例和批次时间线。
- 变更只限前端任务控制台；所有回归测试须与删除旧 `batchSendType`/旧 task-modal 路径后的契约一致。

## 修正记录表

| P1 | 问题 | 触发频率 |
|---|---|---|
| P1-1 | 计划删除旧 DOM/入口却漏改其回归测试，前端测试必然失败。 | 每次 CI，100%。 |
| P1-2 | 行“手动”切 tab 后清空来源，且 clone 丢失 `id`，导致配置快照、差异确认、配置级日志全部失效。 | 每次点击行“手动”，100%。 |
| P1-3 | 来源搜索只取首个模糊匹配、无 autocomplete，且会无确认覆盖已编辑 draft。 | 多匹配或已编辑后换来源时，稳定复现。 |
| P1-4 | 日志抽屉未实现计划要求的批次时间线。 | 每次查看配置级日志，100%。 |

## 修复规格

### P1-1：同步回归测试契约

文件：

- `src/test/js/expertTagBatchFix.test.js`
- `src/test/js/taskModalStateMachine.test.js`

变更：移除对已删除 `#batchSendType`、旧 `handleBulkOutreach()` task-modal 及 `executeManualOutreach()` watcher 的断言；改测 `handleBulkOutreach()` 打开 `#batchSendTaskModal`，并新增/替换任务控制台的 tab、来源、确认、关闭清理契约测试。

预期：`node --test src/test/js/*.test.js` 全绿；旧路径不再被要求存在。

### P1-2：保留行来源的身份和 baseline

文件：`src/main/resources/static/app.js`

变更：将“用户直接点击手动 tab”的 reset 与 `openManualTabFromConfig()` 分离。后者须保留完整深拷贝（含 `id`、`updatedAt`）作为 `manualSource/manualBaseline`，再填充 draft；不得由 `switchBatchSendTab("manual")` 清空。确认 POST 和 `openBatchConfigLogs()` 必须使用该稳定 `id`。

预期：行“手动”后可显示来源、diff、来源配置确认和该 configId 的日志；直接点击 tab 仍为空来源。

### P1-3：显式选择来源且防止静默覆盖

文件：

- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`

变更：为 `#batchManualSourceQuery` 渲染可点击的匹配列表，仅在用户明确选择一项后建立来源。切换来源前若当前 draft 相对 baseline 有 diff，先确认放弃修改；取消则保留原来源和 draft。候选仅含未删除配置。

预期：不再按数组首项隐式选择；已编辑表单不会被静默覆盖。

### P1-4：补齐批次时间线

文件：

- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`

变更：在日志抽屉增加批次时间线容器；以详情中已返回的批次/进度数据渲染批次序号、时间、状态与计数。无批次时显示明确空态；文本经 `textContent` 或 escape helper 写入。

预期：配置级日志完整呈现执行摘要、原因、错误样例和批次时间线。

## 当前状态

- 编译：PASS（`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 完成编译）。默认 JDK 25 会触发 Kotlin 1.9.25 的 `IllegalArgumentException: 25.0.1`，属环境问题。
- JVM 测试：FAIL — 1585 tests，20 failures，3 skipped；失败位于既有 `ManualInitialOutreachServiceTest`、`MailAutomationControllerTest`，不在本计划范围，记录为观察。
- 前端测试：FAIL — 241 tests，238 passed，3 failed；失败均由 P1-1 的旧断言造成。
- JS 语法：PASS — `node --check src/main/resources/static/app.js`。
- Diff 检查：PASS — `git diff --check` 无输出。

## 合规审计

- I-1 tab 隔离：❌ `src/main/resources/static/app.js:12415-12421` 先写来源，`12133-12135` 又以 `preserveSource:false` 重置；行“手动”必丢来源。
- I-2 规范化 diff：❌ `src/main/resources/static/app.js:12569-12624` 的规范化/比较本身存在，但 P1-2 使行来源 baseline 不可达；`12785-12815` 还会静默替换 draft。
- I-3 独立确认：❌ `src/main/resources/static/app.js:12751-12764` 需要 `source.id`，而 `deepCloneConfig()`（12440-12455）未复制 id；来源关联无法正确提交或打开日志。
- I-4 日志数量守恒：✅ `src/main/resources/static/app.js:12903-12934` 展示指标并只警示不守恒；`12937-12959` 原因按 count 降序且有空态。❌ 批次时间线在 `index.html`、`app.js` 中均不存在。
- I-5 操作反馈：✅ `src/main/resources/static/app.js:12731-12768` 确认按钮先禁用、失败后恢复；`12243-12252` 删除有二次确认。
- 样式契约：✅ `src/main/resources/static/styles.css:7292-7443` 为命名空间规则，未改全局 `.task-modal/.tabs/.data-table`。
- 删除旧 DOM：✅ `src/main/resources/static/index.html` 无 `batchSendType`；但 P1-1 表明测试仍依赖它。
- No extras：✅ 实现变更仅为原计划列出的 `index.html`、`app.js`、`styles.css`。

### 语义完整性检查

- Accumulation check：✅ N/A（本前端计划不实现时间窗口计数）。
- State machine check：✅ N/A（本计划无新的后端状态机；RUNNING 日志轮询在 `12877-12885` 有关闭/切换清理）。
- Cross-plan check：❌ P1-2 破坏配置 CRUD 子计划写入的稳定 configId 与本计划读取/提交的契约；行“手动”场景无法带着 configId 进入执行和日志。
