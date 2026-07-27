# 批量邮件任务控制台前端：fix-3

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-14/batch-send-task-console-frontend.md`
- 前序复验：`docs/plans/fix/batch-send-task-console-frontend/fix-2.md`
- 复验轮次：3/3

## 约束摘录

- I-3：所有执行按钮必须先做普通必填、数值、模板校验，再打开专用确认弹窗；确认后才 POST。
- I-3：无来源执行也必须保留普通字段校验；确认弹窗不能成为无效参数的入口。
- I-5：执行确认防重复提交，API 失败保留 draft 与确认内容。
- I-2：未选来源不计算、不显示 diff；标签按 trim、去重、排序的规范化值比较。

## 修正记录表

| P1 | 问题 | 触发频率 |
|---|---|---|
| P1-1 | 手动执行数值读取用 `||` 把 `0` 与空值静默替换为系统默认值，`handleManualExecute()` 因而会打开确认框并最终提交与操作员输入不一致的快照，绕过原计划要求的前置数值校验。 | 操作员清空日限额/每轮数量/TTL，或输入 `0` 时稳定复现。 |

## 修复规格

### P1-1：确认前拒绝无效数值，禁止静默回退

文件：

- `src/main/resources/static/app.js`
- `src/test/js/` 中批量任务控制台执行确认契约的测试文件

变更：

1. 将 `readManualFormValues()` 的数值读取与默认值策略分离：保留表单初始默认值，但不得把用户输入的空串或 `0` 经 `||` 改写为 `1000/50/30` 等有效数值。
2. 在 `handleManualExecute()` 调用 `showBatchManualConfirm()` 前，显式校验日限额、每轮数量、自查 TTL 为有限数且至少为 1；每封/每轮间隔为有限数且至少为 0。校验失败时提示字段错误、不得打开确认框、不得 POST。
3. 仅在上述校验通过后构造确认快照和 `confirmManualExecution()` 请求 payload；保持已有来源/独立执行三类确认文案及失败后恢复确认按钮的行为。
4. 补充回归测试：日限额为 `0`、空值、或 TTL 为 `0` 时不显示确认框且不调用 API；合法 `0` 秒间隔仍可进入确认并保留为 `0`。

预期：任何无效数值都在确认弹窗前被拒绝，且不会因前端默认回退扩大发送限额；有效参数仍按输入值提交。

## 当前状态

- 编译：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 完成 Kotlin/Java 编译。
- JVM 测试：FAIL — 20 failures（`ManualInitialOutreachServiceTest` 8、`MailAutomationControllerTest` 12），与 fix-1/fix-2 已记录的既有失败相同，均不在本前端计划修改范围。
- 前端测试：PASS — `node --test src/test/js/*.test.js`，244 passed，0 failed。
- JS 语法：PASS — `node --check src/main/resources/static/app.js`。
- Diff 检查：PASS — `git diff --check`。

## 合规审计

- I-1 tab 状态隔离：✅ `src/main/resources/static/app.js:12074-12102` 关闭时清理确认框、抽屉、timer 与局部状态；`12417-12423` 行“手动”保留完整 `id/updatedAt` 来源；`12133-12136` 仅直接进入无来源手动 tab 时重置。
- I-2 规范化 diff：✅ `src/main/resources/static/app.js:12572-12626` 统一空值、数值、标签 trim/过滤/排序/去重；`12629-12667` 按 diff 即时切换红框和原值；无来源在 `12630-12632` 清除全部标记。（来源：K-batch-console-diff-tag-normalization、K-batch-console-source-identity）
- I-3 独立确认：❌ `src/main/resources/static/app.js:12564-12568` 用 `Number(value) || default` 将 `0`/空值替换为默认值；`12774-12783` 随后仅比较默认后的数字并打开确认框。因此无效输入未在确认前被拒绝，违反前置数值校验。
- I-3 三类确认与来源语义：✅ `src/main/resources/static/app.js:12687-12725` 分别渲染来源+差异、来源无差异、无来源确认；`12754-12767` 确认后才 POST，并把稳定 configId 用于日志。（来源：K-batch-console-source-selection、K-batch-console-source-identity）
- I-4 日志数量守恒与时间线：✅ `src/main/resources/static/app.js:12963-12995` 展示目标/成功/失败/跳过/耗时及剩余并仅警示不守恒；`12997-13019` 原因排序且保留空态；`13048-13064` 渲染批次时间线；`12931-12945` RUNNING 轮询在切换/关闭时清理。（来源：K-batch-console-log-timeline）
- I-5 危险操作与重复提交：✅ `src/main/resources/static/app.js:12245-12254` 删除二次确认并保留失败行；`12734-12771` 确认按钮先禁用，失败后恢复且保留内容。
- 样式契约：✅ `src/main/resources/static/styles.css:7295-7476` 仅新增 `.batch-*` 命名空间规则，未改全局 `.task-modal/.tabs/.data-table`；移动端抽屉全宽。
- 回归测试契约：✅ `src/test/js/expertTagBatchFix.test.js:198-208` 断言新双 tab DOM；`src/test/js/taskModalStateMachine.test.js:1215-1256` 断言新控制台入口和旧执行路径 no-op。（来源：K-batch-console-regression-contract）
- 删除旧 DOM：✅ `src/main/resources/static/index.html` 不含 `#batchSendType`；旧测试契约已移除。
- No extras：✅ 业务实现仅涉及原计划 3 个前端文件及原计划修正记录纳入的 2 个 JS 测试文件；本轮额外变更仅为 fix-v 知识元数据。

### 语义完整性检查

- Accumulation check：✅ N/A（本前端计划不实现时间窗口计数）。
- State machine check：✅ N/A（无新后端状态机；RUNNING 日志轮询有关闭、切换、终态清理路径）。
- Cross-plan check：✅ 配置 CRUD 的稳定 `id/updatedAt` 已在 `12417-12423` 与 `12754-12767` 传入执行、日志；fix-2 的标签集合比较已由前端 244 项测试覆盖。P1-1 仅为确认前输入校验，不改变接口契约。
