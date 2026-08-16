# Execution Report — b4 (任务明细跳转: 读取路径 + UI)

- Child: b4 — Plan: `docs/plans/2026-08-16/b4-task-drilldown-frontend.md`（全链第 7 份）
- Brief: `docs/plans/fast/2026-08-16-execution-order/children/b4/brief.md`
- Plan identity (recomputed at handoff): `82d31b664dc8b699c5e1e7b8dd4aeda2944a11fe85da3050c5fac1108c3144ea`（执行前后一致）
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast` @ `fast/2026-08-16-execution-order`
- Worktree git_dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast`
- Pre-execution code SHA (b3 terminal Code head): `eb27b8d84a4286ce3ef92ca40acf98d761168121`
- Implementation commit: **`d32ccb282d88a6e6182bb579acbc0b65d74995eb`** — `feat(fast-p): implement b4`（10 文件，+826/-19）
- Result: **READY_FOR_VERIFICATION**

## 1. Verify-first 逐项决议（执行前完成）

### VF-1: MailboxService 列表查询方法逐字基线（计划审计段补全）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt`，`listMailbox(...)`（:40-82）：

```kotlin
fun listMailbox(
    direction: String?, accountCode: String?, keyword: String?, recipientEmail: String?,
    startTime: LocalDateTime?, endTime: LocalDateTime?, pending: Boolean, page: Int, size: Int
): MailboxListResponse {
    val activeAccounts = senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
    val activeCodes = activeAccounts.map { it.accountCode }
    if (activeCodes.isEmpty()) return MailboxListResponse(emptyList(), 0)
    if (accountCode != null && accountCode !in activeCodes) return MailboxListResponse(emptyList(), 0)

    val onlyPending = if (pending) 1 else 0
    val offset = page.toLong() * size
    val rows = mailRecordRepository.listMailbox(
        accountCodes = activeCodes, direction = direction, accountCode = accountCode,
        keyword = keyword, recipientEmail = recipientEmail, startTime = startTime, endTime = endTime,
        onlyPending = onlyPending, limit = size, offset = offset)
    val total = mailRecordRepository.countMailbox(
        accountCodes = activeCodes, direction = direction, accountCode = accountCode,
        keyword = keyword, recipientEmail = recipientEmail, startTime = startTime, endTime = endTime,
        onlyPending = onlyPending)
    val inboundIds = rows.mapNotNull { it.inboundProcessingId }
    val inboundTagsById = inboundMailTagService.listTagsBatch(inboundIds)
    val items = rows.map { row -> toMailboxItemResponse(row, inboundTagsById) }
    return MailboxListResponse(items, total)
}
```

审计结论：

- **① 列表查询与详情查询是否共用装配**：**不共用**。列表路径的装配点是 `toMailboxItemResponse(row: MailboxRow, inboundTagsById)`（:96-120），输入是 `MailRecordRepository.listMailbox` 的 UNION ALL SQL 投影 `MailboxRow`；详情路径（`getMailboxDetail` → `toDetailFromMailRecord(record: MailRecord)` :241-265 / `toDetailFromInbound`）输入是实体。两条路径各自独立但**列表路径的 DTO 与标签计算是唯一的**（`MailboxItemResponse` + `computeTags`）——I2b-3 的「复用装配」即复用 `toMailboxItemResponse` + `computeTags`，本实现以 `MailRecord → MailboxRow`（逐字段对齐 UNION 投影）再走同一装配达成。
- **② 标签在哪一层计算**：标签字符串在 `MailboxService.computeTags(row: MailboxRow)`（:306-325，`internal`）中计算，由 `toMailboxItemResponse` 内部调用（`tags = computeTags(row)`）。`MAILBOX_TAG_BADGE_CLASS`（app.js ~:9092）是前端徽章色 class 映射，不是标签来源。过滤路径直接复用 `computeTags`，两条路径产出相同 tags。

### VF-2: 第 5 号文件 controller 定位（grep 回执）

- 收发件箱列表端点归属：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxController.kt`（`@RestController @RequestMapping("/api/mail/mailbox")`，方法 `list(...)` `@GetMapping`）。
- 改动范围核查：列表端点 + 可选参数只涉及 1 个 controller 文件（+ 已授权的 repository/service）。**未触发「须改 2+ 后端文件即停止」的 PLAN_CONFLICT 条件**。

### VF-3: QUEUE 派发标记的确切 JSON 字段名（不得凭印象 — 已源码确认）

- `TaskDispatchRequest.dispatchMode`（`task/service/TaskExecutionService.kt:269-274`，`val dispatchMode: String`）。
- `request_payload` 由 `TaskExecutionService.toJson(request)` = `objectMapper.writeValueAsString(...)`（Jackson 默认字段名）写入。
- `MailAutomationScheduler.dispatchMode()`（`MailAutomationScheduler.kt:100-101`）：`if (mailQueuePublisherProvider.getIfAvailable() == null) "SYNC" else "QUEUE"`。
- 结论：request_payload JSON 中的字段名**逐字为 `dispatchMode`**，队列模式值为 `"QUEUE"`。实现用 `objectMapper.readTree(payload).path("dispatchMode").asText() == "QUEUE"` 判定（解析失败/缺失 → false）。

### VF-4: A3 落地后的收发件箱 DOM 实况（S2b-3 定位）

- `index.html` `view-mailbox` 段：`<div class="toolbar">…</div>`（:689-717，含刷新/视图切换/账号/方向/标签/收件人/关键词/日期/查询）→ `<section class="panel">` → `<div class="panel-head"><h2>已激活账号收发邮件记录</h2><button id="bulkOutreachBtn" …>批量发送</button></div>`（:728-732，A3 迁入位置）。
- 提示条插在既有 `.toolbar` 闭合 `</div>`（:717）之后、`<section class="panel">`（:719）之前——**位于标题栏之下、既有 .toolbar 之后，与 `#bulkOutreachBtn` 不同行**（A2b-10 共存）。

### VF-5: `.link-btn` margin-left:auto 副作用

- `styles.css:2517-2534`：`.link-btn { margin-left: auto; … }`（为其原用场景——flex 容器设计）。
- 既有使用点 grep 回执：`index.html:1647`（`<legend class="form-section-title">` 内「同步 SMTP」按钮，flex 标题行）。
- 本计划入口落在任务明细 `<td colspan="7" style="padding:12px 16px;background:var(--surface);">`（`toggleTaskDetail` 生成）——**块级非 flex 的 table-cell**，`margin-left:auto` 对内联级元素计算为 0，不会右推。**未修改 `.link-btn` 规则块**（styles.css 零改动）。

### VF-6: N2b-2 其他入口清空批次过滤

- 清空集中在 `setMailboxPendingOnly(pending)` 内（`state.mailbox.taskExecutionId = null; state.mailbox.taskExecutionLabel = null;`）——覆盖三个「待处理」入口：`view-unmatched`/`open-pending`（app.js ~:10897）、`goto-manual-queue`（~:11608）、标签下拉「待处理」（~:12090）。计划点名两个调用点，实现为超集（第三条路径同样属于「待处理入口」，K-cross-view-drilldown-pattern 要求同步清空）。

### VF-7: 链检查（M-7）

- 编辑前 `index.html` 三处 `?v=` 均为 `20260817-v5-task-type-catalog`（b2 值）——链检查通过。
- 编辑后三处均改为 `20260817-v6-task-drilldown`；`batchSendTaskConsoleVisualFix.test.js` 三条 literal 断言同 commit 同步为同值。

## 2. 按文件变更明细（10 个授权文件）

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalog.kt` | `MANUAL_INITIAL_OUTREACH`/`INITIAL_OUTREACH` → `drilldown = Drilldown.MAIL_BY_EXECUTION`；`AUTO_REPLY_ALL`/`CHECK_REPLIES` → `Drilldown.EXPERT_BY_POLL_DETAIL`；其余 12 项保持 null（含全部 ES 类，M-4）。KDoc 同步更新。 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt` | `/{id}/detail` 追加 4 字段：`drilldown: String?`（枚举名）、`drilldownState: String`（AVAILABLE/NONE/PRE_FEATURE/QUEUE_DISPATCHED）、`drilldownCount: Int`、`experts: List<PollRepliedExpert>?`。判定逻辑 `computeDrilldown`；专家解析复用私有 `PollDetailRaw`/`PollDetailAccountRaw`/`PollDetailExpertRaw`；队列判定 `isQueueDispatched`（`dispatchMode == "QUEUE"`）。构造器新增第 5 参 `mailRecordRepository: MailRecordRepository? = null`（Kotlin 默认值；Spring 5.3 对默认值参数按可选注入处理——`TaskExecutionControllerMvcTest` @WebMvcTest 实测通过；既有 4 参直构测试零改动编译通过）。N2b-4：既有字段一字未改。 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt` | 派生查询 ×2：`findAllByTaskExecutionIdOrderByIdAsc(taskExecutionId: Long): List<MailRecord>`（I2b-3/I2b-4）、`countByTaskExecutionId(taskExecutionId: Long): Long`（T2b-4 计数）。无 `@Query`、无 `IN`（不涉及 K-empty-list-in-query-guard）。**刻意追加在接口末尾**：`OperatorStatusWriteSeamGuardTest` 按行号钉死 `ec.operator_status AS operator_status`（:537）与 `ec.operator_status, ec.current_index_level`（:585）两条只读噪声排除项，前置插入会位移行号导致守卫误报（见 §4 偏差说明）。 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt` | 新增 `listByTaskExecution(taskExecutionId: Long): MailboxListResponse`：`findAllByTaskExecutionIdOrderByIdAsc` → 私有 `toMailboxRow(record)`（逐字段对齐 UNION 投影 MAIL_RECORD 分支：source/id/expert_contact_id/direction/mail_type/sender_account_code/triggered_by/matched_qa_rule_id/subject/bodyPreview=COALESCE(cleaned_body,body) 前 200/sent_at/NULL 列/expert_contact LEFT JOIN/EXISTS(mail_attachment)）→ **复用 `toMailboxItemResponse` + `computeTags`**（I2b-3 同一 DTO、同一标签、同一排序 id ASC）；不 join `task_execution`（I2b-4）。**刻意置于 `listByExpert` 之后**：避免位移行号钉死的 `operatorStatus = summary.operatorStatus`（:165）噪声排除项（见 §4）。 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxController.kt` | 列表端点追加 `@RequestParam(required = false) taskExecutionId: Long?`；非 null → `mailboxService.listByTaskExecution(id)`；null → 原 `listMailbox(...)` 一行不改（N2b-1）。 |
| 6 | `src/main/resources/static/app.js` | ① `state.mailbox` 加 `taskExecutionId: null`、`taskExecutionLabel: null`；② `toggleTaskDetail` 明细顶部按 `drilldownState` 内联渲染入口（NONE/PRE_FEATURE/QUEUE_DISPATCHED → 三种 `<span class="text-muted">` 逐字文案，不渲染 data-action/href/button；AVAILABLE+MAIL_BY_EXECUTION → S2b-1 按钮逐字；AVAILABLE+EXPERT_BY_POLL_DETAIL → 每位专家一个 `task-drilldown-contact` 按钮，姓名+`<email>` 转义）；③ 新顶层函数 `handleTaskDrilldownMail(element)`（设 `state.mailbox.taskExecutionId`/`taskExecutionLabel`、`setView("mailbox")`、`page=0`、`loadMailbox`，label 从 `state.taskTypeOptions` 按行 `data-task-type` 反查）与 `handleTaskDrilldownContact(element)`（复用 `openContactInList`，N2b-3/I2b-5），挂在既有 click 监听器（与 `goto-manual-queue` 同处）；④ `loadMailbox`：有 `taskExecutionId` 时加入 query、强制平铺列表端点（`expertMode && taskExecutionId == null` 才走 by-expert）、内联渲染提示条（两种文案逐字）；⑤ `#mailboxExecutionFilterClear` 点击 → 置 null/隐藏/`page=0`/重载；⑥ `setMailboxPendingOnly` 清空过滤态（N2b-2，见 VF-6）。 |
| 7 | `src/main/resources/static/index.html` | S2b-3 提示条逐字插入（既有 `.toolbar` 之后、panel 之前）；三处缓存键 → `20260817-v6-task-drilldown`（I2b-6）。 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt`（NEW） | 5 用例：N2b-1（controller：null 走原查询、新方法未被调）；taskExecutionId 路由到 `listByTaskExecution`（原方法未被调）；I2b-3（同一 MailRecord 走 listMailbox 与 listByTaskExecution 两条路径，DTO `equals`）；I2b-4（悬垂 id 999 正常返回不抛异常）；共享装配标签断言（专家/发件/手动回复/首发）。 |
| 9 | `src/test/js/taskDrilldown.test.js`（NEW） | 14 用例：NONE 输出逐字 span 且不含 data-action/href/`<button`（I2b-1）；三种禁用文案各一条 + 三条互异（I2b-2）；AVAILABLE 邮件按钮逐字骨架（S2b-1）；专家按钮逐字（含 `&lt;` 转义）；点击邮件入口设置 taskExecutionId 且 page===0（T2b-5）；N2b-2 `setMailboxPendingOnly(true)` 清空过滤态；提示条两种文案逐字 + 无过滤隐藏（S2b-3）；过滤时 query 携带 taskExecutionId；index.html 提示条骨架逐字 + 位于 toolbar 之后、bulkOutreachBtn 之前（A2b-10）。 |
| 10 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | **仅**「bumps the stylesheet cache key」三条 literal 断言 → `20260817-v6-task-drilldown`（I2b-6，同 commit）；其余用例一行不动。 |

未触碰：`styles.css`（0 diff）、`src/main/resources/db/migration/`（无迁移）、`docs/plans/fast/`（不入实现 commit）、其余任何文件。

## 3. 验证命令（全部实跑，exit code 均为 0）

| 命令（cwd = worktree 根） | 结果 | 证据 |
|---|---|---|
| `node --check src/main/resources/static/app.js` | PASS | `SYNTAX OK` |
| `node --test src/test/js/taskDrilldown.test.js` | PASS | 14 tests, pass 14, fail 0（首跑 1 用例因测试桩 closest 结构错误失败，修正测试桩后通过；产品代码未动） |
| `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js` | PASS | 17 tests, pass 17, fail 0 |
| `node --test src/test/js/*.test.js`（全量 JS 保护性回归） | PASS | 630 tests, pass 630, fail 0 |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=MailboxTaskExecutionFilterTest` | PASS | Tests run: 5, Failures: 0, Errors: 0; BUILD SUCCESS |
| `JAVA_HOME=… mvn test -Dtest=TaskExecutionControllerMvcTest,TaskExecutionControllerTest,MailboxControllerTest,MailboxServiceTest` | PASS | Tests run: 38, Failures: 0, Errors: 0（含 @WebMvcTest 上下文） |
| `JAVA_HOME=… mvn test -Dtest=OperatorStatusWriteSeamGuardTest,…`（行号守卫修复后） | PASS | Tests run: 33, Failures: 0, Errors: 0 |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test`（全量回归） | PASS | **Tests run: 2498, Failures: 0, Errors: 0, Skipped: 4**; JS `fail 0`; BUILD SUCCESS |
| `git diff --check` | PASS | 无输出 |

验收点核对：
- I2b-1：JS 用例断言 NONE 输出不含 data-action/href/`<button` ✓
- I2b-2：三条禁用文案逐字且互异 ✓
- I2b-3：Kotlin 用例同一 MailRecord 双路径 DTO equals ✓
- I2b-4：悬垂 id 用例不抛异常且返回非空 ✓
- I2b-5：index.html diff 无 `.nav-tab`（0）/无 `class="view"`（0）；app.js diff `viewMeta` 新增 0 行 ✓
- S2b-1/2/3：JS 用例逐字断言三段 DOM；本 commit `styles.css` 0 规则块变更 ✓
- S2b-4/I2b-6：`grep -c "20260817-v6-task-drilldown" index.html` = **3**；同值在 `batchSendTaskConsoleVisualFix.test.js` = **3**；该文件通过 ✓
- N2b-1：Kotlin 用例断言 null 时新查询未被调用 ✓
- N2b-6：`/recent-polls/{id}/detail` 一行未改 ✓（N-1/N-2/N-3/N-4/N-5/N-7 均未触碰）

## 4. 偏差与说明

1. **`listByTaskExecution` 返回类型**：计划文本写 `List<MailboxRow>`，实际实现返回 `MailboxListResponse`。理由：DTO 级装配（`toMailboxItemResponse`/`computeTags`）在 MailboxService 内且为 private，controller 无法自行装配；返回已装配响应是「内部复用既有装配」（I2b-3）的唯一自洽形态，controller 分支因此极简。行为与计划一致（同一 DTO、同一标签、同一排序，I2b-3 测试即按 DTO 断言）。
2. **行号钉死的守卫测试**（非授权文件，不可改）：`OperatorStatusWriteSeamGuardTest` 用「文件+行号+上下文」排除只读噪声位。本实现**不修改该测试**（不在 10 个授权文件内），而是将 MailRecordRepository 的两个新派生查询追加在接口末尾、MailboxService 的两个新方法置于 `listByExpert` 之后，使钉死行号（Repository :537/:585、Service :165）零位移。首轮全量回归曾因此守卫误报 1 失败，重排后通过（证据见 §3 守卫专项跑）。
3. **N2b-2 清空点**：计划要求「两个既有调用点显式清空」，实现把清空集中在 `setMailboxPendingOnly` 内——两个点名调用点（view-unmatched/open-pending、goto-manual-queue）与第三个同类入口（标签下拉「待处理」，:12090）全部覆盖，为计划要求的超集，且与计划自身 JS 用例「setMailboxPendingOnly(true) 后 taskExecutionId 被清空」直接吻合。
4. **`handleTaskDrilldownMail`/`handleTaskDrilldownContact` 抽为顶层函数**：计划给出内联代码块；为可测性（JS 用例须断言点击后 state 变化）抽为命名函数，仍挂在计划指定的同一 click 监听器、与 `goto-manual-queue` 同处，行为逐字等价（含 `preventDefault` 与 `.catch`）。
5. **toggleTaskDetail / loadMailbox 的入口与提示条渲染保持内联**：既有 JS 测试（taskRecordsSemantics / mailboxDateDefault / mailboxExpertGrouping）以函数提取 + vm 沙箱方式执行这两个函数，引用新顶层函数会 ReferenceError（且这些测试文件不在授权范围）；内联 + 空值守卫后既有 630 条 JS 测试全部通过，新功能行为由 taskDrilldown.test.js 覆盖。
6. **知识回写（Phase 6）**：计划要求新增 3 条 K-* 并续期若干——知识库位于主 worktree（`/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/knowledge/`），不在本 worktree 授权文件内，未触碰（由 controller 按惯例统一回写或另行授权）。
7. **未做 URL 参数播种**：计划 T2b-5 未授权从地址栏读取 taskExecutionId 播种过滤态；直接带参访问的保留期文案（A2b-5）由「label 缺失 → 保留期形式」的渲染路径覆盖（后端悬垂 id 行为由 Kotlin 用例 I2b-4 覆盖）。

## 5. 交付物

- 实现 commit：`d32ccb282d88a6e6182bb579acbc0b65d74995eb`（`feat(fast-p): implement b4`，10 授权文件；`docs/plans/fast/` 未含；未 push/merge/rebase/amend）
- 本报告路径：`docs/plans/fast/2026-08-16-execution-order/children/b4/execution.md`（未入实现 commit，controller 另行提交证据）
- 工作树：clean；HEAD = d32ccb2（目标分支 `fast/2026-08-16-execution-order`）
