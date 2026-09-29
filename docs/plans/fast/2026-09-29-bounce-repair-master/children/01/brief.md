# Child 01 Brief — 发件账号硬退告警可核对

- 子计划（唯一权威契约）：`docs/plans/2026-09-29/bounce-alert-observability.md`
- 总计划：`docs/plans/2026-09-29/bounce-repair-master.md`
- 代码审计回执：`docs/plans/2026-09-29/bounce-repair-code-audit.md`
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master`
- Branch：`fast/2026-09-29-bounce-repair-master`
- child_base_sha：见 ledger（`Base` 列）
- 依赖：无（`Depends on: none`）

实施者必须先完整阅读子计划全文，再按本 brief 执行。子计划是完整合同；本 brief 只做边界与命令的收敛，不新增、不削弱任何要求。

## 目标（详见子计划「需求描述」）

- O-1：告警显示近 7 天 HARD 数、成功发信数、百分比，并解释两个时间窗口。
- O-2：低于 20 封显式返回样本不足；前端无偏高徽标，不能把无告警解释成零退信。

## Authorized Files（恰好 8 个，不得增删；第 8 个为 A1 追加）

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountController.kt` |
| 3 | `src/main/resources/static/app.js` |
| 4 | `src/main/resources/static/index.html` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorServiceTest.kt` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountControllerMvcTest.kt` |
| 7 | `src/test/js/senderBindingDisplay.test.js` |
| 8 | `src/test/js/providerUndeliveredColumn.test.js`（A1 追加：守卫断言收敛为服务商分布链路，不得全局否定 `hardBounceCount`） |

禁止修改：`styles.css`、repository、`MailMonitoringService`、migration、`taskActivityCenter.test.js`、其它任何文件。若无授权文件无法完成，返回 `PLAN_CONFLICT`。

补充约束（A1 之后）：
- 落盘后全量 JS 套件必须 0 失败：`node --test src/test/js/*.test.js`。特别是 `taskActivityCenter.test.js` 要求当前缓存键字面量**只能出现在 index.html**；修复 `senderBindingDisplay.test.js` 中的缓存键断言时，从 `index.html` 解析当前键（与 `taskActivityCenter.test.js` 同法），断言 11 个带版本资源共享同一键、旧键 `20260929-discovery-schedule` 不再出现、`task-modal-runtime.js` 保持无版本，但**不要在测试里固化新键字面量**。

## 关键不变量（子计划逐字要求，必须全部满足）

- I-1：一次计算、同一 cutoff。一次 `toResponse` 只调用一次统计方法；两次 COUNT 复用同一 `since = now-minusDays(7)`；分子 HARD+received_at，分母 OUTBOUND+SENT+sent_at；数字与比率来自同一计算结果；不得用 `todaySentCount` 替代。
- I-2：返回值与门槛。`sentCount < 20` → `rate=null`、`sampleSufficient=false`、`high=false`；否则 `rate=hard/sent`、`sampleSufficient=true`、`high=rate>0.05`。1/20 不告警、2/20 告警；比率不封顶 100%。
- I-3：兼容与无副作用。`calculateHardBounceRate` 仍返回 `Double`、低样本仍 `-1.0`；`isHardBounceRateHigh`、`checkAndWarn` 签名/自定义 windowDays/threshold 行为不变；统计/GET 不得调用任何 save/update/pause/resume。
- I-4：接口和展示契约。保留 `hardBounceRateHigh`，追加 `hardBounceCount:Long`、`sentCount:Long`、`hardBounceRate:Double?`、`hardBounceSampleSufficient:Boolean`、`hardBounceWindowDays:Int`；比率为 0～非限定上界小数（9/160 → 0.05625，显示 5.63%）；空/缺字段不得显示 NaN/Infinity/伪造 0/0；旧 API 仅有 `high=true` 时回退原徽标文案。
- I-5：口径与资源版本。tooltip 明确「近 7 天退信事件 / 近 7 天成功发信」「可能不是同一批邮件」；11 个已有版本资源统一换为 `20260929-bounce-alert`，未带版本的 `task-modal-runtime` 不动。
- S-1：状态徽标原位替换。复用现有 `.badge` / `.badge.warn`，禁止修改 CSS；无 inline style、无新 class、无新表格列；DOM 为同一 `span.badge.warn`，数字经数值校验，title 用 `escapeHtml`；前两段「启用/禁用」「自动暂停」不动；低样本无第三段元素；高比率但统计字段缺失时原位使用原徽标且不生成数值。

## Required Commands（在 worktree 内逐条执行，JDK 11）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest=BounceRateMonitorServiceTest,MailSenderAccountControllerMvcTest test
node --test src/test/js/senderBindingDisplay.test.js
node --check src/main/resources/static/app.js
```

基线（seed commit，产品代码 = master base）已记录：Java 12 类通过（详见 `children/01/baseline.md`）；`senderBindingDisplay.test.js` 8 pass / 0 fail；`node --check app.js` exit 0。命令结果不得退化。

## 下游接口（后续子计划/前端消费，必须精确）

- `MailSenderAccountController.toResponse` 响应 JSON 字段名与类型：`hardBounceRateHigh:Boolean`（保留）、`hardBounceCount:Long`、`sentCount:Long`、`hardBounceRate:Double?`（null 表示样本不足）、`hardBounceSampleSufficient:Boolean`、`hardBounceWindowDays:Int`。
- 覆盖 `list/get/create/update/enable/disable/reset/resume` 全部复用 `toResponse` 的响应路径；所有现有 MVC mock 都要更新（不只第一个测试）。
- `BounceRateMonitorService` 的旧公开方法（`calculateHardBounceRate`、`isHardBounceRateHigh`、`checkAndWarn`）保持签名与语义，供 `AutoMailReplyService` 调用。

## 全局约束

- 本项目 Kotlin + Spring Boot 2.7 / Java 11；**不得编辑已应用的 Flyway migration**；本阶段零 DDL、零新持久化字段。
- 新增业务异常（如有）必须继承 `IllegalArgumentException`/`IllegalStateException`（K-custom-exception-http-status-mapping）。
- 前端缓存键三件套同值同日 bump；固定键测试数量以 index.html 当前键用 `rg -l` 反查，不按资源引用写法检索（K-frontend-cache-key-triad）。本阶段已核实：`20260929-discovery-schedule` 在 `src/test` 中 0 处写死；无测试文件需要跟随修改。
- 本阶段不触碰 `OperatorStatusWriteSeamGuardTest` 涉及的行号钉文件。
- 子计划要求「先补失败测试，再最小实现」；测试必须真实覆盖计划验收标准中的断言（9/160、0.05625、门槛矩阵、字段 null、DOM/缓存 key），不得写同义重复或实现细节断言。

## 交付与证据

1. 本地提交：`feat(fast-p): implement 01`（**不包含** `docs/plans/fast/**` 任何文件）。
2. 报告写入：`docs/plans/fast/2026-09-29-bounce-repair-master/children/01/execution.md`，内容包含：最终 commit SHA、逐文件改动摘要、逐条命令与退出码/计数、逐条不变量证据（file:line）、已知偏差。
3. 返回（仅这些）：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
4. 不得 push/merge/rebase/amend/reset；不得顺手修与本阶段无关的问题；不得审查后续子计划。
