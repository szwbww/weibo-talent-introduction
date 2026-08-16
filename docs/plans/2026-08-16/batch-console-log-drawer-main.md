# 批量邮件任务控制台修复 —— 主计划（零代码改动）

> 编号：本族为 **A1–A3**，是全链前 3 份。全链（A1–A3 + B1–B5）执行顺序见 `00-execution-order.md`。

> 本文件不含任务，只定义拆分理由、执行顺序、共享不变量与共享审计。
> 三份子计划各自独立可发布、独立验证。

## 拆分理由

原始诉求含三块互不依赖的改动，合并后为 **13 个文件 / 3 个子系统**，超出 create-p 的
硬上限（≤10 文件、≤2 子系统）。按「最小可独立交付切片」拆为三份：

| 子计划 | 交付物 | 文件数 | 子系统数 |
|---|---|---|---|
| A1 `a1-batch-list-row-and-drawer-visual.md` | 列表行不再错位；日志抽屉不透明、不再压住弹窗关闭按钮 | 5 | 1（前端静态资源） |
| A2 `a2-batch-manual-log-reachability.md` | 手动执行日志随时可回看，独立执行也能查 | 8 | 2（前端 + 后端） |
| A3 `a3-expert-list-rename-and-entry-move.md` | 「专家联系」→「专家列表」；批量发送入口迁到收发件箱 | 5 | 1（前端静态资源） |

## 执行顺序（强制）

**A1 → A2 → A3**，不可并行、不可乱序。全链顺序见 `00-execution-order.md`。理由不是保守：

1. 三份都要 bump `index.html` 的 3 个缓存键，并同步 `batchSendTaskConsoleVisualFix.test.js:48-52`
   的**具体字符串**断言（来源: K-frontend-cache-key-triad）。并行执行必然三方冲突且测试红。
   各计划分配到的缓存键值互不相同，见各计划 `## 样式契约`。
2. A1 与 A2 都改 `app.js` 的 `renderBatchExecutionDetail` / 抽屉族函数；P1 先落地结构，
   A2 只加入口与状态机分支。
3. A3 移动 `#bulkOutreachBtn` 会碰 `expertTagBatchFix.test.js:188-191`（断言全文件恰好一处），
   与前两份无交集，放最后减少 rebase 面。

## 共享不变量

### Invariant M-1: HTML 串禁止按字符截断
- Rule: 任何已拼接完成的 HTML 字符串，禁止使用 `substring` / `slice` / `substr` 按字符数截断。
  需要限量时只能在**结构化数据层**（数组元素、DOM 节点）截断。
- Applies to: `renderBatchConfigRow`（P1 修复现存违例）、以及后续任何列表行渲染函数。
- Violation consequence: 截点落在标签或属性内 → 后续 `</td><td>` 被吞进属性值 →
  整行 `<td>` 数量少一个 → 全部后续列左移一格。本次实测：5 个筛选条件的行只剩 6 个 `<td>`。
- 来源: original（本轮实测发现）

### Invariant M-2: 三缓存键同值同时 bump
- Rule: `index.html` 的 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=`
  三个值必须完全相同，且与 `batchSendTaskConsoleVisualFix.test.js` 中的断言字符串一致。
- Applies to: A1 / A2 / A3 各自的 index.html（B 系列的 B1/B2/B4 同样受约束，见 `00-execution-order.md`） 与该测试文件。
- Violation consequence: 构建期 node 测试失败，WAR 构建中止（2026-08-13 eda4853 实测踩坑）。
- 来源: K-frontend-cache-key-triad

### Invariant M-3: 抽屉状态身份先写后请求
- Rule: 打开/切换执行日志时，必须先写入 `batchTaskState.logConfigId` 与
  `batchTaskState.logExecutionId`，再发详情请求；异步响应落地前必须确认目标仍是当前抽屉。
- Applies to: `openBatchConfigLogs` / `openBatchExecutionLogs` / `loadBatchLogExecutions` /
  `loadBatchLogDetail`（现有实现已满足，P2 新增入口必须沿用）。
- Violation consequence: 迟到响应覆盖当前抽屉；轮询静默失效。
- 来源: K-batch-console-default-log-selection

### Invariant M-4: `#bulkOutreachBtn` 全仓恰好一处
- Rule: `id="bulkOutreachBtn"` 在 `index.html` 中出现且仅出现一次；移动位置时只能剪切，
  不能复制。`taskButtonMapping.MANUAL_INITIAL_OUTREACH.btnId`（app.js:682）、
  `taskButtonOriginalTexts.bulkOutreachBtn`（app.js:674）、
  `taskLaunchConfigs.MANUAL_INITIAL_OUTREACH.btnId`（app.js:5124）、
  `openTaskModal(..., "bulkOutreachBtn", ...)`（app.js:5626）四处引用保持不变。
- Applies to: A3。
- Violation consequence: `expertTagBatchFix.test.js:188-191` 直接失败；或任务按钮状态还原打在错误元素上。
- 来源: original（grep 实证，见 A3 现状审计）

## 共享审计（三份计划共用，不再各自重复）

### X-1 前端 JS 用例的两条执行入口不等价
- `mvn test`：`exec-maven-plugin` 把 `bash -lc 'node --test src/test/js/*.test.js'`
  绑定在 `test` phase（`pom.xml:188-203`），另有 `node --check app.js`、
  `node --check task-modal-runtime.js` 两条。三者都带 `<skip>${skipNodeTests}</skip>`，
  而 `skipNodeTests` 在 `pom.xml:19-25` 的 `<properties>` 中**未定义**（那里只有
  `migrationIt`、`mysqlIt`），故默认不跳过。
- `verify.sh`：**只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件**，
  **不可作为前端计划的回归门禁**。
- 来源: K-js-test-invocation-surface（本轮已重新核对 pom.xml:185-235，结论未变）

### X-2 直接断言批量控制台 DOM/CSS 的现存测试
以下文件对本次改动区域有硬断言，任一计划改到对应区域必须同步：

| 文件 | 断言点 | 内容 |
|---|---|---|
| `src/test/js/batchSendTaskConsoleVisualFix.test.js` | :36-46 | colgroup 七列 class、`table-layout: fixed`、`.batch-task-column-actions { width: 170px }` |
| 同上 | :48-52 | 三缓存键的**具体字符串** |
| 同上 | :54-61 | `.task-modal.batch-send-task-modal` 规则块内**不得出现** `background-color:` |
| `src/test/js/batchSendTaskConsoleInteraction.test.js` | :1363 V9 / :1657 W9 / :2084 G13 | `renderBatchConfigRow` 的服务商行、状态行、门禁 pill 三态 |
| `src/test/js/expertTagBatchFix.test.js` | :188-191 | `id="bulkOutreachBtn"` 恰好一处 |
| `src/test/js/batchExecutionLogTimeline.test.js` | 全文 16 条 | 时间线渲染与空态 |
| `src/test/js/batchManualExecutionLog.test.js` | 全文 | `confirmManualExecution` 的两条日志入口分派 |
- 来源: K-batch-console-regression-contract、K-ui-removal-retires-obsolete-contract-tests

### X-3 验证命令（三份计划共用，权威文本）

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。路径取自项目根 `CLAUDE.md` 的 Commands 章节。

```bash
# 全量回归（含上述 node --test 与两条 node --check）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# app.js 语法检查（pom 也跑这条，改 app.js 后先跑它最省时间）
node --check src/main/resources/static/app.js

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 输出 `Tests run: N, Failures: 0, Errors: 0` 且退出码 0；
输出中能看到 `node --test` 的执行记录（若看不到，说明 `skipNodeTests` 被外部注入为 true，
此时前端用例未跑，必须改用单跑命令补验）。
来源：CLAUDE.md 项目元信息 `test_command` / `build_command` + pom.xml:185-235 实读。

各计划的 `## 验证命令` 只补充**本计划新增/涉及测试文件的单跑命令**，全量与构建一律引用本节。

## 本轮排查的取证记录

以下结论全部在 Chromium 中用仓库真实 `index.html` + `styles.css` + `app.js` 的
`renderBatchConfigRow` 输出复现，非读码推断：

- `renderBatchConfigRow` 输出实测：5 个筛选条件的行截断成
  `...<span class="batch-task-sc</td>`，`tr.children.length` = **6**（正常 7）。
  阈值实测：1~3 个条件 = 151/200/257 字符（正常）；第 4 个 = 308 字符（截断）。
- `getComputedStyle('.batch-log-drawer').backgroundColor` = `rgba(255, 255, 255, 0.55)`。
- `elementFromPoint` 打在 `.batch-send-close-btn` 中心，返回 `#batchLogDrawerCloseBtn`
  —— 实测 rect 分别为 `[1233,205,28,28]` 与 `[1239,205,28,28]`。
- `#batchLogMetrics` 六项时第 6 张卡换行独占一行（tiles y 坐标 307/307/307/307/307/383）。
- 手动执行后自动开抽屉的两条路径（有来源配置 / 独立执行）在浏览器中**均正常渲染**，
  问题不在开抽屉，而在关掉后回不去 —— 详见 A2 现状审计。
