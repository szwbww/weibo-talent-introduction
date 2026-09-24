# Fast-P Child Brief — c3（生产控制台开关与逐邮箱日志）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-09-24/emailable-pre-send-verification.md`，identity `commit:99aa2ed7e9c93cfc5552f47889670fae96816f01`。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-09-24/emailable-03-console-logs.md`，identity `commit:99aa2ed7e9c93cfc5552f47889670fae96816f01`。
- 只读证据附件：`docs/plans/2026-09-24/emailable-evidence.md`（F-9～F-15 为 HTML/CSS 逐字基线）。
- Worktree / branch / `child_base_sha`：见派发消息。
- 依赖：c1、c2（均已 LIGHT_PASS；其接口见下）。

## 全局约束

1. JDK 11 固定：Maven 命令必须 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
2. 只允许修改「Authorized Files」表内的文件；不得改动 Kotlin 主代码之外的既有前端文件之外的任何路径；不得新增第 8 个文件。
3. `styles.css` 只允许**追加**计划 S-2 给出的那段 CSS（逐字，不得增删属性或改数值），既有规则零改动；`index.html` 的 inline 仅限既有 DOM，新 DOM 禁止 inline style。
4. 不得修改 `docs/plans/**`、不得写 `docs/plans/fast/**`、不得编辑 `docs/releases.json`、不得触碰迁移与后端发送链路（c1/c2 已完成）。
5. 缓存键：实施前用 `grep -n "?v=" src/main/resources/static/index.html` 复核当前值（本 worktree 基线为 `20260924-account-editor`，共 11 处）；把所有已有版本化 styles/app/模块资源统一改为本次 release 串（`K-frontend-cache-key-triad`：styles.css / trust-reply-workbench.js / app.js 等必须同值同时 bump，固定键测试数量会变化，须用当前键 `rg -l` 反查全部测试文件）。
6. 产品代码提交格式：`feat(fast-p): implement c3`；fast-p 报告/日志不进该提交。
7. JS 测试约定：`node --test src/test/js/<x>.test.js` 单文件；新增测试沿现有 `node:test` + `vm` + DOM stub 方式；DOM stub 会让 `getElementById` 永远返回元素，因此新增「按 id 取元素再写入」的渲染函数时，测试必须额外断言该 id 出现在 `index.html` 源文本里（`K-dom-stub-tests-hide-dangling-refs`）。

## Authorized Files（7）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` | 新增验证明细只读接口 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt` | 明细隔离/分页/历史/错误读取 |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt` | 新增必需仓储依赖；旧接口回归 |
| 4 | `src/main/resources/static/index.html` | 两处开关、抽屉验证区、资产版本同步 |
| 5 | `src/main/resources/static/app.js` | 字段传播/差异/日志请求及渲染 |
| 6 | `src/main/resources/static/styles.css` | 仅追加 S-2 的局部 CSS |
| 7 | `src/test/js/batchEmailVerification.test.js` | 新增：快照/竞态/分页/转义测试 |

计划 T1～T4 给出逐条契约（接口形状、状态映射、分页与竞态、S-1/S-2 的 DOM/CSS 全文）。以计划文本为准。

## 关键不变量（计划 I-1..I-5）

- I-1：UI 字段全链路保值（新建默认 false、编辑回填、选源/还原/清空/深拷贝/规范化/差异/确认/预估/提交）；材料提醒时禁用并显式 false；手动差异不回写原配置。
- I-2：明细按 `executionId` 读新表，开关以该次 `requestSnapshot` 为准；`taskType` 必须为 `MANUAL_INITIAL_OUTREACH`；`configId` 传入时必须等于 `execution.batchConfigId`；无字段/false → “未启用邮箱验证”；true 且无行 → “尚未进入邮箱验证”；坏 JSON 明确报错，不冒充关闭。
- I-3：PASS≠发送成功；SKIP 显示 provider 原 state/reason；ERROR 显示服务异常且标签无需处理；tagStatus FAILED 显示“标签写入失败”；终态 SENDING 显示“结果未确认”；原发送失败不混入验证拒绝。
- I-4：`afterId` 默认 0、`limit` 默认 50 最大 100，后端严格校验；id 升序 limit+1 判 hasMore；前端请求序号隔离旧响应；复用既有 1500/3000ms 轮询，不新增 timer；所有文本 escapeHtml/textContent；接口失败显示“验证明细加载失败”，不得显示假 0。
- I-5：原日志 DTO/折叠时间线/六个指标口径不变；新区域在 `batchLogMetrics` 之后、`integrityWarning` 之前；GET 绝不触发验证或发送；无密钥字段。

## 上游依赖（c1/c2 已交付，直接复用，不要重复实现）

c1 终态：`LIGHT_PASS_WITH_NOTES`，Code head `0965a037d94e198f6ce8b13900149a09d35683b4`；实现/验证报告见 `children/c1/execution.md`、`children/c1/verify-log.md`。可直接复用的只读接口（`campaign/repository/BatchEmailVerificationRepository.kt`）：

- `readPage(executionId, afterId = 0, limit = 50): BatchEmailVerificationPage`（readOnly 事务内组合分页与全量汇总；`limit` 被 `coerceIn(1, MAX_PAGE_SIZE=100)`）。
- `BatchEmailVerificationPage(rows: List<BatchEmailVerificationRow>, hasMore: Boolean, aggregate: BatchEmailVerificationAggregate)`。
- `BatchEmailVerificationRow(id, taskExecutionId, expertDocId, orcidId, expertName, email, decision, providerState, providerReason, errorCode, requestCount, checkedAt, sendStatus, sendReason, tagStatus, tagError, createdAt, updatedAt)`。
- `BatchEmailVerificationAggregate(total, pending, passed, rejected, serviceError, notSent, sending, sent, sendFailed, sendSkipped, tagNotRequired, tagPending, tagApplied, tagFailed)`（注意字段名是 `serviceError`，不是 `errors`）。
- 取值集合常量：`BatchEmailVerificationDecision`（PENDING/PASS/SKIP/ERROR）、`BatchEmailVerificationSendStatus`（NOT_SENT/SENDING/SENT/FAILED/SKIPPED）、以及同文件内的 tag_status 常量。
- `BatchExecutionSnapshot.emailVerificationEnabled: Boolean = false`（c1 落在 `campaign/domain/BatchExecutionModels.kt`）。
- 错误码：`EMAIL_VERIFY_AUTH_ERROR / EMAIL_VERIFY_NO_CREDITS / EMAIL_VERIFY_RATE_LIMITED / EMAIL_VERIFY_TIMEOUT / EMAIL_VERIFY_INCOMPLETE / EMAIL_VERIFY_BAD_RESPONSE / EMAIL_VERIFY_SERVICE_ERROR`，另有 c1 附加的受控码 `EMAIL_CHANGED`、`EMAIL_VERIFY_AUDIT_FAILED`、`EMAIL_VERIFY_SEND_STATE_CONFLICT`（展示时按受控原因处理，不要当作未知字符串丢弃）。

c2 终态：Code head `1968f01d0d07afda1f0d2de571cf3dc3bf72e776`，报告 `children/c2/execution.md`。配置侧接口实测：

- `BatchSendTaskConfig.kt`：实体 `:40`、View `:74`、Create 命令 `:109` 均为 `emailVerificationEnabled: Boolean = false`；Update 命令 `:138` 为 `Boolean? = null`（缺省/null 保留现值）。
- `BatchSendTaskConfigService.kt`：`:369` 的 `require(mailType == INTRODUCTION || !emailVerificationEnabled)`；`:109` 的合并语义 `cmd.emailVerificationEnabled ?: existing.emailVerificationEnabled`；`:228` legacy adapter 显式带 existing 值。
- `BatchExecutionModels.kt:373` 的 `toExecutionSnapshot` 复制该字段；`BatchSendControlService.kt:421` 对直接手动快照做 `INTRODUCTION` 类型守卫（显式 400）。
- `V139__add_batch_email_verification_enabled.sql`：`ALTER TABLE batch_send_task_config ADD COLUMN email_verification_enabled BOOLEAN NOT NULL DEFAULT FALSE`（不回填 true）。

实施前请直接读工作区代码确认签名与行号，不要凭计划里的行号猜测。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendConfigControllerTest,BatchSendExecutionDetailTest
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
```

## 交付

- 执行报告写入 `docs/plans/fast/2026-09-24-emailable-pre-send-verification/children/c3/execution.md`（报告本身不进实现提交）。
- 只返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不得修复白名单外问题、不得重构相邻代码、不得 push/merge/amend/squash。
