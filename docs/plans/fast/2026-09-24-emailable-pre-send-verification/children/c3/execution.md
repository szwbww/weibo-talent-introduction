# c3 执行报告：生产控制台开关与逐邮箱日志

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-24-emailable-pre-send-verification/docs/plans/2026-09-24/emailable-03-console-logs.md`
Plan identity（批准版）: `commit:99aa2ed7e9c93cfc5552f47889670fae96816f01`（字节冻结，执行期间未修改）
Master plan: `docs/plans/2026-09-24/emailable-pre-send-verification.md`（identity 同上，未修改）
Child brief（完整合同）: `docs/plans/fast/2026-09-24-emailable-pre-send-verification/children/c3/brief.md`
Execution epoch: NEW（本 child 无先前执行记录）
Executor: C3Impl
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-24-emailable-pre-send-verification`
Target branch: `fast/2026-09-24-emailable-pre-send-verification`
Pre-execution code SHA（child 起点）: `1968f01d0d07afda1f0d2de571cf3dc3bf72e776`（c2 Code head）
Pre-execution HEAD（本次执行起点）: `daeb98ee6e18af654c336ebdcdafd00c83d69e2d`（c3 派发前 controller 已推进 ledger，非产品代码）
Post-execution code SHA: `b74ef51`（`feat(fast-p): implement c3`，仅含 7 个授权文件；`git show --stat` 无 docs 变更）
Evidence HEAD: N/A（执行报告由 controller 单独提交，不进实现提交）
Implementation boundary: `daeb98e..b74ef51`（7 个授权文件）

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 只读验证明细接口（I-2/I-3/I-4/I-5） | IMPLEMENTED | `BatchSendConfigController.kt`、`BatchSendExecutionDetailTest.kt`、`BatchSendConfigControllerTest.kt` | `:180-241` 接口+身份校验+快照读取+白名单映射；`:583-621` DTO；13 条新控制器测试 |
| T2 开关与手动传播（I-1/I-5，S-1） | IMPLEMENTED | `index.html`、`app.js`、`batchEmailVerification.test.js` | `index.html:1408-1418`、`:1657-1669`；`app.js:18516-18576`（开关规则）及其余 E-7 接缝；JS 用例 V1–V7 |
| T3 日志读取与展示（I-2/I-3/I-4/I-5，S-2） | IMPLEMENTED | `index.html`、`app.js`、`styles.css`、`batchEmailVerification.test.js` | `index.html:1732-1747`；`app.js:19701-20041`；`styles.css:11790-11806`；JS 用例 V8–V21 |
| T4 验证与版本 | IMPLEMENTED | 3 个测试文件 + `index.html` | 必需命令全绿；`index.html` 11 处资源统一 `?v=20260924-email-verification` |

## Commands（本次执行内 fresh 运行）

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q test -Dtest=BatchSendConfigControllerTest,BatchSendExecutionDetailTest` | PASS | exit 0；`BatchSendConfigControllerTest: Tests run 8, Failures 0, Errors 0, Skipped 0`；`BatchSendExecutionDetailTest: Tests run 30, Failures 0, Errors 0, Skipped 0`（合计 38 / 0 fail；基线 25 / 0 fail，新增 13 条）。同一次 `mvn test` 也跑了 exec-plugin 绑定的 JS 套件：`tests 1178 / pass 1178 / fail 0` |
| `node --test src/test/js/*.test.js` | PASS | exit 0；`tests 1178`、`suites 233`、`pass 1178`、`fail 0`（基线 1152 pass，新增 26 条，无回归） |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |

首次 Kotlin 运行暴露了 2 处**测试自身**的期望错误（实现无关），已修正后重跑：
1. `verification detail accepts the matching configId and reads that page` 断言写成了 `1L`，实际路径执行的 `executionId` 是 `10L` → 改为 `10L`。
2. `items expose only the display whitelist` 用裸 `ObjectMapper()` 序列化 `LocalDateTime`，缺 `JavaTimeModule` → 该断言改用注册了 `JavaTimeModule` 的 mapper（与 Spring Boot 运行时一致；生产 ObjectMapper 由 Boot 自动装配，已注册该模块，故属测试夹具问题）。

## Changed Files（与计划「变更文件清单」1:1）

| # | 计划文件 | 实际改动 |
|---|---|---|
| 1 | `src/main/kotlin/.../mail/controller/BatchSendConfigController.kt` | 新增非 nullable 依赖 `batchEmailVerificationRepository`（`:50`）、`GET /executions/{executionId}/email-verifications`（`:180`）、快照开关读取（`:227`）、展示白名单映射（`:243`）、3 个响应 DTO（`:583-621`） |
| 2 | `src/test/kotlin/.../BatchSendExecutionDetailTest.kt` | 构造器补 1 个 mock；新增 13 条测试（`:443-653`）；`execution()` 助手新增 `requestPayload` 参数；补 `JavaTimeModule` 序列化 mapper |
| 3 | `src/test/kotlin/.../BatchSendConfigControllerTest.kt` | 构造器补 `batchEmailVerificationRepository` mock（`:49`），旧接口 8 条回归不变 |
| 4 | `src/main/resources/static/index.html` | 两处 S-1 开关（`:1408-1418`、`:1657-1669`）、抽屉 S-2 区域（`:1732-1747`）、11 处资源版本键统一 bump |
| 5 | `src/main/resources/static/app.js` | 开关状态与污染收口（`:18516-18576`）、E-7 全部接缝的 bool 传播/差异/确认页、任务行 pill（`:17603-17606`）、明细区完整实现（`:19701-20041`）、轮询/打开/切换/关闭接线（`:19392`、`:19437`、`:19453`、`:19524-19527`、`:19687`） |
| 6 | `src/main/resources/static/styles.css` | 仅插入 S-2 的 17 行 CSS（`:11790-11806`）；既有规则字节不变（diff 为纯新增 18 行） |
| 7 | `src/test/js/batchEmailVerification.test.js` | 新增 26 条 `node:test` 用例（传播/差异/确认/状态映射/转义/分页/竞态/失败/静态契约） |

无第 8 个文件；`git status` 仅上述 7 项。

## 逐不变量证据（file:line）

### I-1 UI 字段全链路（S-1）

| 接缝 | 位置 | 证据 |
|---|---|---|
| 新建默认 false | `app.js:18882`（`fillManualFormDefaults`） | 手动草稿默认 `emailVerificationEnabled: false` |
| 编辑回填（View→勾选） | `app.js:17756-17760` | `Boolean(config && config.emailVerificationEnabled === true)` |
| 编辑保存提交 | `app.js:18742-18743`、`:18764` | 只读勾选状态，不二次推断类型 |
| 预估快照 | `app.js:18596-18615` | 与保存同源，同一个勾选读值 |
| 手动选源/还原/深拷贝 | `app.js:18857`（`deepCloneConfig`） | `c.emailVerificationEnabled === true` |
| 手动清空 | `app.js:18882` → `resetManualExecution`→`fillManualFormDefaults` | 清空来源回到 false |
| 回填草稿 | `app.js:18919-18921`、`:18924` | 先写勾选，再由 `refreshEmailVerificationState` 收口材料提醒 |
| 读取（表单值） | `app.js:18982`、`:18996` | `Boolean(emailVerificationEl && emailVerificationEl.checked)` |
| 规范化/差异比较 | `app.js:19018`、`:19088`、`:19035`、`:19139` | 参与 normalize、fieldDefs、formatter（开启/关闭）、fieldMap |
| 差异清单渲染 | `app.js:19137-19139`、`:19168` | `manualFieldEmailVerification` 进入 fieldMap 与 clearAllDiffMarkers |
| 确认页显示本次值 | `app.js:19192-19194`、`:19214`、`:19223` | 两个非差异分支都显式打印“发送前验证邮箱: 开启/关闭” |
| 提交（手动） | `app.js:18639`（`buildManualExecutionSnapshot`） | 与预估共用同一完整快照 |
| 材料提醒禁用+显式 false | `app.js:18560-18576` | `is-disabled` + `checkbox.disabled=true` + `checked=false` + hint「仅介绍邮件支持发送前验证」 |
| 切回介绍邮件不偷偷开启 | `app.js:18570-18574` | 恢复可操作只解除禁用，不写 `checked` |
| 手动差异不回写原配置 | `app.js` 无 PUT/PATCH 到 `/configs/*`，手动仅 POST `/manual-executions`（`:19172-19185`） | 测试 V4/V5 断言 source 未被修改、仅 1 次 `/manual-executions` 请求 |

### I-2 明细来源、历史和隔离（T1）

| 规则 | 证据 |
|---|---|
| 按 `executionId` 读新表 | `BatchSendConfigController.kt:203` → `batchEmailVerificationRepository.readPage(executionId, afterId, limit)`（01 的只读事务方法） |
| 开关以该次 `requestSnapshot` 为准 | `:202` → `:227-241`（只读 `requestPayload.snapshot.emailVerificationEnabled`，不读当前配置） |
| 无字段/false → 未启用 | `:236-240`（missing/null → false）；前端 note「未启用邮箱验证」`app.js:19852-19856` |
| true 且无行 → 尚未进入 | `app.js:19857`（`summary.total === 0`） |
| `taskType` 必须 `MANUAL_INITIAL_OUTREACH` | `:185-188`（与 `BatchSendControlService.TASK_TYPE` 比对 → 404） |
| 执行不存在 → 404 | `:182-184`（捕获 `getExecution` 的 `IllegalStateException` → 404，与其它执行级接口同码） |
| `configId` 必须等于 `execution.batchConfigId` | `:189-191` → 404 |
| 软删配置的历史执行仍可按 executionId 读 | 只校验 `execution.batchConfigId` 相等，不查询配置行 |
| 坏 JSON 明确报错，不冒充关闭 | `:230-234` → `IllegalStateException("历史执行快照无法解析…")`（GlobalExceptionHandler 映射为 400 带消息，非 200/false），且有测试 `:539` |
| 先校验身份再读表 | 测试 `:443/:455/:467` 断言 404 分支 `readPage` 从未被调用 |

### I-3 状态表达不混淆

| 规则 | 证据 |
|---|---|
| PASS≠发送成功 | `app.js:19779-19790`：发送结果只由 `send_status` 决定；测试 V11 断言 PASS+NOT_SENT 渲染「未发送（模板渲染失败（TEMPLATE_RENDER_FAILED））」 |
| SKIP 显示验证未通过 + provider 原 state/reason | `app.js:19797-19812`（详情「原因：」行原样拼接 state / reason）+ 测试 V11 断言 `undeliverable / rejected_email` 逐字保留 |
| ERROR = 服务异常（无需标签） | `app.js:19797-19805` 受控码中文解释保留原 code；测试 V11 断言「验证服务鉴权失败（EMAIL_VERIFY_AUTH_ERROR）」+「无需处理」 |
| tagStatus FAILED → 「标签写入失败」 | `app.js:19797-19812` + `badge error`；测试 V11 |
| 终态 SENDING → 「结果未确认」 | `app.js:19786`（`running ? 发送中 : 结果未确认`）+ 测试 V12 |
| 原发送失败不混入验证拒绝 | 表格四列独立：验证结果 / 发送结果 / 标签处理；测试 V11 四行样例 |
| 新汇总独立显示 | 新区域 metric 仅 3 格（通过/未通过/服务异常），`app.js:19821-19833`；原六指标 `renderOutcomeMetrics` 未改 |

### I-4 分页、轮询与安全

| 规则 | 证据 |
|---|---|
| `afterId` 默认 0、`limit` 默认 50、最大 100，后端严格校验 | `BatchSendConfigController.kt:181-184`（`require(afterId >= 0)`、`require(limit in 1..MAX_PAGE_SIZE)` → 400）；测试 `:491` |
| id 升序 limit+1 判 `hasMore`，游标取本页最后 id | 01 仓储 `readPage` + `:217`（`nextAfterId = if (page.hasMore) page.rows.lastOrNull()?.id`）；测试 `:556` |
| summary 为整次执行 | `:204-215`（用同一 `readPage` 返回的 aggregate，不是本页） |
| `passed + rejected + errors + pending = total`，`tagFailed` 不参与合计 | 常量来自 01 仓储聚合 SQL；测试 `:575` 断言分区等式与 `tagFailed` 独立 |
| 前端请求序号隔离旧响应 | `app.js:19976`（每次请求 `++verificationRequestSeq`）+ `:19981/:19987` 双重判定（序号 + 执行身份）；测试 V17 |
| 轮询刷新当前页，不重置游标与展开行 | `app.js:19524-19527`（detail 回包后以 `poll:true` 复用既有 timer）；游标只在切换执行时清空 `:19968-19974`；测试 V16 |
| 不另开 timer | 未新增任何 `setInterval`：明细区仅被现有 1500/3000ms 循环驱动 |
| 同一页请求未完成不重复发起 | `app.js:19966`（`poll && verificationLoading` 直接返回）；测试 V16 断言 API 只被调用 1 次 |
| 分页按钮 loading 期间禁用 | `app.js:19866-19873`；测试 V20 |
| 所有文本转义 | 行渲染全部经 `escapeHtml`（`app.js:19814-19838`），note/错误用 `textContent`；测试 V14 注入 `<img onerror>` 与 `</td><script>` |
| 接口失败显示「验证明细加载失败」，不冒充 0 行 | `app.js:19945-19954`（首次失败不渲染任何计数，已加载则保留旧数据并标注可能过期）；测试 V18 |
| 关闭/打开/切换递增序号 | `app.js:19897`（`resetBatchEmailVerification` 递增）由 `:19392`、`:19437`、`:19453`、`:19687` 调用；测试 V21 |

### I-5 保留旧链路且局部展示

| 规则 | 证据 |
|---|---|
| 原日志 DTO/折叠时间线/六指标口径不变 | `BatchConfigExecutionDetail`、`ExecutionProgressRow`、`renderOutcomeMetrics`、`renderBatchTimeline` 均未改动（`git diff` 无相关 hunk） |
| 新区域在 `batchLogMetrics` 之后、`integrityWarning` 之前 | `index.html:1731` → `:1732-1747` → `:1748`；静态用例断言三者索引顺序 |
| 资源版本统一更新 | `index.html` 11 处 `?v=20260924-email-verification`（含 styles/trust-reply-workbench/app 三元组） |
| 不编辑其它已有未提交样式 | `styles.css` diff 为纯新增 18 行，无删除/修改行 |
| GET 绝不触发验证或发送 | 接口只调用 `taskExecutionService.getExecution` + `batchEmailVerificationRepository.readPage`；测试 `:649` 用 `verifyNoMoreInteractions` 断言唯一交互是读；JS 侧静态用例断言区域内无任何写方法 |
| 无密钥字段 | DTO 白名单测试 `:604` 断言 JSON 键集合恰为 15 个展示字段且不含 `apiKey`；JS 区域无 `apiKey|secret` 引用 |

## S-1 / S-2 一致性证据

### S-1（两处开关）

- 骨架逐字：`index.html:1408-1418`（定时任务）与 `:1657-1669`（手动）与计划 S-1 目标骨架一致；class 仅用计划声明的复用类（`batch-config-field batch-gate-field`、`batch-config-field-label`、`batch-gate-row`、`batch-task-status-toggle batch-gate-toggle`、`batch-task-status-switch`、`batch-task-status-label`、`batch-gate-hint`）。
- 手动版额外差异 DOM（`batch-config-diff-badge` / `batch-config-diff-original`）位于外层末尾，与既有手动字段一致；手动 hint 末尾为「仅影响本次执行。」。
- 无新增 CSS；禁用套 `.batch-gate-field.is-disabled`（透明度 .6）由 `refreshEmailVerificationState` 加类，键盘焦点沿用现有 3px 蓝色环（`.batch-task-status-toggle input:focus-visible + .batch-task-status-switch`，未改）。
- 任务 pill：`app.js:17603-17606`，复用 `.batch-gate-pill` 与 `.batch-gate-pill.is-off`，文案「邮箱验证 · 开 / 关」，紧随门禁 pill 之后。
- 新 DOM 无 inline style（静态用例断言两处 checkbox 标签无 `style=`）。

### S-2（执行日志验证明细区）

- CSS 逐字追加：`styles.css:11790-11806`，与计划 S-2 代码块逐字一致（测试 S2 用例用整块字符串 `includes` 断言）；既有规则零改动（`.batch-log-metrics`、`.batch-log-drawer`、`.batch-task-status-toggle input:checked + .batch-task-status-switch` 三处逐字断言未变）。
- DOM：`index.html:1732-1747`，与计划目标 DOM 一致（section/h4/note/metrics/table-wrap/table/pager id 与列头逐字）；动态 metric 固定 3 格复用 `.batch-log-metric`（`app.js:19821-19833`）。
- 徽章复用 `.badge ok|warn|error|info`（`:19816-19821`），tag FAILED 用 `badge error` 并带文字，不靠颜色单独传意。
- 行结构固定 4 个 td：①专家名+`<br>`+邮箱；②badge + `<details><summary>验证详情</summary>`（原因/验证时间/请求次数）；③发送结果+原因；④标签结果。
- 时间复用站点既有 `formatDateTime`（无新时区算法）。
- `aria-live="polite"` 的 note 承载四类状态（关闭/尚未进入/正常分页说明/加载失败加 `is-error`），失败可经原刷新或重开重试，无新按钮。

## Deviations

1. **S-2 CSS 插入点**：计划写「追加到 styles.css」，实际插入在 `/* task-center-contract:start */` 之前（即文件尾部标记块之前），而非文件最末行。
   原因：既有 `src/test/js/taskActivityCenter.test.js:577-583`（不在本 child 授权文件内）断言 `/* task-center-contract:end */` 之后必须为空（`assert.strictEqual(after.trim(), "")`），把该标记块钉死为文件尾。CSS 块逐字未改、既有规则零改动，规则顺序对本次新增类无影响（selector 无重叠）。仓库既有先例：`5072dc9` 同样把新规则插在该标记块内而不是文件尾。
2. 新增 CSS 块后保留一个空行再接标记块（与计划代码块末的空行一致）。

## Remaining Risks / 未覆盖项

1. **真机人工验收（A-1～A-4）未执行**：本 child 只做实现与自动化证据；浏览器视觉/交互（开关 36×20、窄屏表格横滚、轮询保留当前页与展开行）需 controller/人工按计划 A-1～A-4 在隔离验收站执行。
2. **后端分页真表行为**：本 child 用 mock 验证控制器边界；真实 50/50/1 分页与 `hasMore` 由 01 的仓储 IT 覆盖（`readPage` 未在本 child 修改）。
3. **材料提醒的二次守卫在 UI 层**：`saveBatchConfigEditor` 只读勾选状态，不再二次推断邮件类型（沿用门禁开关同款设计，也避免破坏既有 `saveBatchConfigEditor` 沙箱测试）。若模板列表未加载导致类型不可知，用户理论上可能在材料提醒上勾选；后端 `BatchSendTaskConfigService:369` 与 `BatchSendControlService:421` 的同口径守卫会拒绝/返回 400，数据不会落成非法配置。
4. **`verificationExecutionStatus` 依赖主详情回包**：明细行在终态与运行中的措辞（「发送中」vs「结果未确认」）由最近一次 detail 的 `status` 决定；若主详情接口持续失败，措辞会沿用上一次已知状态（不伪造确认结果）。

## Freshness

- Plan identity rechecked: YES（`docs/plans/2026-09-24/emailable-03-console-logs.md` 与 master plan 均未修改，`git status` 无 docs/plans 变更）
- Worktree identity rechecked: YES（`fast/2026-09-24-emailable-pre-send-verification`；HEAD `b74ef51` 为该分支 HEAD，工作区仅剩未跟踪的执行报告）
- Reported commits reachable from target branch: YES（`b74ef51` 为当前分支 HEAD）
- Required commands run this invocation: YES（三条命令均在最终实现状态之后 fresh 运行；提交后再复跑 `node --check` 与 `node --test` 结果一致）
- Historical evidence used only as baseline: YES（仅用于比对 25 tests / 1152 pass 基线；`BatchSendExecutionDetailTest` 原有 17 条 INIT/ROUND/FINAL/剩余逻辑用例全部保留并通过）

## 人工验收清单（未执行，交 controller/人工）

按计划 A-1～A-4 在隔离验收站执行（开关外观与键盘操作、窄屏表格横滚、真实 402/门禁样例、101 条分页与竞态、恶意 reason 渲染）。

## Next Action

- READY_FOR_VERIFICATION → 运行 `verify-p`（独立验证），随后 controller 提交本报告并推进 ledger。

