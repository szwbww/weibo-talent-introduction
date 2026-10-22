# Child 03 approved execution brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master
- Branch: fast/batch-demand-master
- Exact approved plan: docs/plans/2026-10-22/batch-demand-03-progress.md
- Plan identity: commit:f684da0a316b8cfb560fe7ebb0a8817d8a2594f2
- Approval: explicit user `/fast-p docs/plans/2026-10-22/batch-demand-master.md`, 2026-10-22. The invocation authorizes these exact bytes despite their pre-approval status wording; do not alter plan text.
- Depends on: 02
- Product base: prior child terminal Code head, supplied at dispatch

## Complete child contract

# 03 按需执行计数协议与历史日志兼容

状态：待批准。依赖：02。范围：9文件；批量执行数据契约与日志UI两个子系统。本步只增加可选协议和消费者，不切换实际发信；旧记录不迁移。

## 需求描述

为按需发信区分“本次计划成功数”“实际处理数”“未完成配额”，禁止再把全库估计混作本次目标。必须保持旧任务记录原数字、运行中/结束后两个读取入口、取消按钮与原因明细；不改通用任务状态枚举、DB表结构、邮件投递。

## 关键不变量

### Invariant I-1: 一个版本化嵌套字段
- Rule: 在details_json与result_summary各只新增一个顶层execution对象：version=2、plannedSuccess:Long、remainingSuccessQuota:Long、scanned:Long、sourceExhausted:Boolean。缺失/未知版本走旧渲染；不得给旧历史补造v2。scanned是已读候选条目数，不是全库人数。
- Applies to: BatchExecutionModels、ManualOutreachResult声明、Controller两个JSON解析入口。
- Violation consequence: 新旧目标语义混淆或破坏历史。
- 来源: original


### Invariant I-2: 两种计数不混用
- Rule: v2的remainingSuccessQuota=max(plannedSuccess-success,0)，实际处理=success+failure+skipped；outcome.target只表示实际处理且remaining=0。运行中generic totalCount=0表示未知全量，结束后totalCount=实际处理。v1原target/remaining等式不变。plannedSuccess按roundSize×有效轮数使用Long，禁止Int溢出。
- Applies to: 可选Execution元数据、v2读取/展示；实际生产写入在04启用。
- Violation consequence: 出现已完成但剩余数千、虚假跳过或大于100%的进度。
- 来源: original


### Invariant I-3: 双读取与协议降级
- Rule: toSummary/toDetail及ParsedOutcome传递可选execution；resultSummary为空时从最新progress details读取同一对象。v2运行中target由sentTotal+failedTotal+skippedTotal推导而非totalCount=0。未知版本仅忽略execution，不吞掉已有outcome。
- Applies to: BatchSendConfigController所有批量日志DTO与解析；app.js日志卡片。
- Violation consequence: 重启/运行中丢计数或读到另一任务信息。
- 来源: K-progress-log-batchonly-two-readers



## 样式契约

### S-1: 指标卡片
- 复用：styles.css:9926-9934的.batch-log-metrics、.batch-log-metric、.batch-log-metric-label、.batch-log-metric-value及.is-success/.is-failure/.is-skipped；无新增CSS/class。网格3列、gap8px、卡片padding10px、radius10px、bg#f8fafc、标签11px、值18px/700；窄屏两列规则:10025保持。
- DOM结构：既有id=batchLogMetrics容器内重复以下骨架，依次为“本次计划成功”“成功”“失败”“跳过”“实际处理”“未完成配额”“耗时”。最后一项显示sourceExhausted时可在既有状态说明文本追加“候选已耗尽”；不增加新元素。
```html
<div class="batch-log-metric">
  <div class="batch-log-metric-label">本次计划成功</div>
  <div class="batch-log-metric-value">30</div>
</div>
```
- 成功/失败/跳过只追加已有状态class，其余不加状态class。所有动态文本escapeHtml。
- 禁止项：新增inline style、新class、改styles.css、改旧v1文案与卡片顺序。

### S-2: 完整性与状态说明
- 复用：既有batchLogIntegrityWarning、batchLogStatusInfo节点及其原class、hidden语义；只改变v2分支文本，不新建DOM。
- v2完整性检查同时核对processed等式与remainingSuccessQuota等式；错误文案“统计待核对：本次执行计数不一致”。正常hidden=true；未知/缺失execution仍执行旧检查。
- 禁止项：改变全局告警颜色、行高、间距；修改取消按钮；复制样式近似替代。

## 现状审计

### task_progress_log / task_execution JSON
- 表结构与完整写读检索见[证据账本](batch-demand-evidence.md) E-03、“受影响存储写入路径”。TaskProgressStore:204-209序列化details；TaskExecutionService:193/272/296/305序列化结果。它们是通用JSON存储，不需新增列或更改通用writer。
- 批量生产者ManualInitialOutreachService两个循环写sentTotal、failedTotal、skippedTotal、pending与outcome。其他任务writer保持原合同。
- BatchSendConfigController:358读resultSummary供列表；:383读详情；:456在运行中读progress；:475读结束outcome；两类读取都必须兼容，不能只改detail。（来源: K-progress-log-batchonly-two-readers）
- OutcomeAccumulator:396-445固定target且terminal annotation可制造未处理skip；本步不更改行为，04另加demand模式。
- X1：执行progress写入→运行中详情；X2：最终结果写入→列表/详情/刷新后；X3：旧JSON→兼容UI，均列人工验收。

### 前端样式盘点
- app.js:25116现有五卡和remaining>0时第六卡；:25136检查target等式。既有DOM/CSS逐字基线及所有相关调用检索已收录证据账本“前端逐字基线”“旧UI契约测试”。
- token：正文#1e293b、muted#94a3b8、success#059669、error#e11d48、warning#d97706。本步不改class规则，因此不影响它们的其他使用点。
- index.html资源16处同值20261021-reply-identity；现有JS测试中该token零命中。更新为20261022-batch-demand-progress，全部资源引用同步。（来源: K-frontend-cache-key-triad）

## 实现方案

1. 清单1/2增加Execution元数据类型和ManualOutreachResult可空默认null字段，不更改现有constructor调用语义，不生成新持久化数据（I-1/2）。
2. 清单3在summary/detail/ParsedOutcome加入可选execution，两个reader严格识别version=2及非负数；不完整对象不用于v2显示，但已有outcome保留；运行中detail从现有latest log读取，不另建存储（I-1/3）。列表未完成时只读取已有result，不额外逐条查latest log，允许列表execution=null，打开详情读取进度；此限制须测试明确。
3. 清单4按execution.version选择计数卡片和完整性检查，既有live accounts、取消、错误样本、timeline保持；状态说明追加耗尽文本使用既有节点（I-2/3，S-1/2）。
4. 清单5同步资源key；清单6-9补充v1、v2、未知版本、坏JSON、RUNNING无result、完成后刷新场景。JS测试必须调用实际函数，不以仅字符串包含代替行为（I-1/2/3，S-1/2）。

## 变更文件清单

| # | 文件（相对仓库根目录） |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` |
| 4 | `src/main/resources/static/app.js` |
| 5 | `src/main/resources/static/index.html` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt` |
| 7 | `src/test/js/batchManualExecutionLog.test.js` |
| 8 | `src/test/js/batchExecutionLogTimeline.test.js` |
| 9 | `src/test/js/batchDemandProgress.test.js` |

## 验收标准

- I-1：两个JSON每个只新增execution一个顶层key；null/未知version读取不抛异常，旧outcome未丢。
- I-2：计划30、成功30、失败2、跳过17时actual=49、remainingQuota=0；计划30、成功20且耗尽时actual按真实计数、remainingQuota=10；不显示“全库剩余”。
- I-3：同一v2 fixture分别写入progress及result后，运行详情/完成详情数字一致；旧target1757/success30/skipped17/remaining1710保持原义。未知协议测试单独覆盖。
- S-1/2：diff无CSS变化、新class、inline style；v2七项文案/颜色class、v1六项与hidden检查均断言；index资源key全部相同。
- Java11执行：mvn test -Dtest=BatchSendExecutionDetailTest。
- JS执行：node --test src/test/js/batchManualExecutionLog.test.js src/test/js/batchExecutionLogTimeline.test.js src/test/js/batchDemandProgress.test.js。
- 本步生产执行尚不写v2；v2用测试夹具验证，不能宣称已完成按需发送。

## 人工验收清单

### A-1: 历史记录不变
- 前置条件: 测试环境导入脱敏历史结果fixture：target1757、success30、failure0、skipped17、remaining1710，不含execution。
- 操作步骤: 1. 打开批量任务执行记录；2. 切换列表/详情；3. 刷新页面。
- 预期结果: “目标1757、成功30、失败0、跳过17、剩余1710”保留；无统计警告；历史数据不被改写。
- 覆盖: I-1/3、X3、历史兼容。

### A-2: 运行日志到最终结果
- 前置条件: 测试fixture先建立RUNNING记录，details含v2计划30、成功20、失败2、跳过3、remainingQuota10、scanned50；再可用测试fixture将相同结果写入resultSummary并标完成。
- 操作步骤: 1. 打开运行中详情；2. 查看取消按钮、账号和timeline；3. 写入最终fixture后刷新详情。
- 预期结果: 两阶段均显示实际处理25、计划30、未完成配额10；原因/账号/timeline仍可见；运行中取消按钮存在，完成后隐藏；无全库剩余。
- 覆盖: I-2/3、X1/X2、must-NOT-change。

### A-3: 卡片目测
- 前置条件: A-2测试页面，窗口宽1200px和600px。
- 操作步骤: 1. 分别观察v1/v2；2. 对照卡片与告警。
- 预期结果: 宽屏3列、窄屏2列；padding10px、gap8px、圆角10px；成功绿、失败红、跳过橙；不新增别的面板样式。
- 覆盖: S-1/2、可观察新计数。



## Global constraints

Read the master invariants I-1 through I-4 at docs/plans/2026-10-22/batch-demand-master.md. Authorized Files are exclusively the exact table in the child plan above. Preserve date-only compatibility, qualification rules, realtime send safety, old execution interpretation, and sequential interfaces. No production sends, release, migrations, schema changes, or unauthorized changes. Real SMTP prohibited; isolated tests only. Existing worktree application.yml remains committed baseline; unrelated main-worktree mail attachment timeout changes are preserved there and not imported.

Use execute-p against the exact approved child plan. Local implementation commit is explicitly authorized with subject `feat(fast-p): implement 03`. Do not run verify-p/review-p/fix-v; fast-p independent lightweight verifier follows. Exclude fast-p evidence from product commit. Any plan amendment or extra file requires PLAN_CONFLICT. Do not modify knowledge/docs outside fast-p execution report, as authorized-file boundary supersedes general documentation cleanup. Do not run builds/tests/formatters mid-flight; collect required commands after implementation, once final state is established. Smoke must exercise changed path; real converter replay for 01, real cursor containers for 02, browser surface for 03/06. Record exact commands, exits, skips and environment limits, never infer PASS.

## Baseline evidence

Java 11.0.15 Zulu and Docker server 29.8.2 observed available. Initial child 01 Maven baseline is controller-owned and its result will be recorded before dispatch. No whole-system baseline is claimed. Prior child interfaces are only those explicitly approved in their plans, not later product changes.
