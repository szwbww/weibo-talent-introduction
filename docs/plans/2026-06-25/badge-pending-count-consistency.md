# 开发计划：收发件箱徽标与「仅待处理」列表口径一致

## 需求描述

**可观察结果**
- 左侧导航「收发件箱」徽标显示的数字 = **启用账号下、`inbound_mail_processing.process_status='MANUAL_REVIEW'` 的全量条数**（覆盖所有 `reason_type`，含 `RECIPIENT_UNSUBSCRIBED`、`NULL` 等）。
- 该徽标值是稳定的"待处理总数"，**不随**收发件箱列表的方向 / 关键词 / 收件人 / 日期 / 标签等筛选条件变化；只有当待处理邮件被实际处理（绑定 / 标记已处理）后才减少。
- 勾选「仅待处理」复选框时：列表查询**全量未处理**（忽略日期范围），且前端**置灰禁用**开始/结束日期输入框；取消勾选时恢复。
- 最终效果：勾选「仅待处理」且无其他筛选时，列表底部「共 N 条」与徽标数字一致。

**必须不变（NOT change）**
- 「仅待处理」未勾选时，列表的默认最近 7 天日期窗口、各筛选行为保持现状。
- 徽标的双色分级显示（高优先级红 `nav-badge-high` / 普通 `nav-badge-normal`）保留。
- `/api/mail/unmatched-inbound` 现有的 `reasonType` / `email` / `subject` / 分页过滤语义（供「待匹配」管理面板使用）保持不变。
- `/api/mail/mailbox` 在非 pending 场景下的日期、账号、其余过滤行为保持现状。
- 监控页（MailMonitoringService）使用的 `countManualReviewBetween` / `countUnmatchedBetween` 等带日期统计不改动。

**超出范围（Out of scope）**
- 不调整 `reason_type` → 颜色的归类规则（仅新增"其它 type 归入普通桶"的兜底）。
- 不改动「待匹配」管理面板（unmatched-inbound 详情页 / 绑定流程）的业务逻辑。
- 不重构收发件箱筛选 UI 布局。
- 不引入新的 DB 字段或迁移（本计划不触碰 schema）。

---

## 关键不变量

### Invariant I-1: 徽标总数 = 启用账号下 MANUAL_REVIEW 全量
- Rule：徽标显示的总数严格等于 `SELECT COUNT(*) FROM inbound_mail_processing WHERE process_status='MANUAL_REVIEW' AND sender_account_code IN (启用账号码)`。**不带** `reason_type IS NOT NULL` 限制，**不带**任何日期 / 关键词 / 方向过滤。
- Applies to：后端新增统计查询 `countManualReviewByAccounts`；前端 `updateUnmatchedBadge` 的总数来源。
- Violation consequence：徽标与「仅待处理」列表「共 N 条」不一致（重现当前 12 vs 8 的 bug）。

### Invariant I-2: 徽标分色之和 = 徽标总数
- Rule：`high + normal = 徽标总数`。`high = NOT_INTERESTED + QA_NO_MATCH`（启用账号过滤后的分组计数）；`normal = 总数 − high`，即所有非 high 的 `reason_type`（含 `UNMATCHED_CONTACT`、`UNCLEAR_INTENT`、`RECIPIENT_UNSUBSCRIBED`、`NULL` 等）都计入 normal 桶。
- Applies to：前端 `updateUnmatchedBadge`。
- Violation consequence：两个彩色徽标数字相加 < 真实待处理数，用户误判积压量。

### Invariant I-3: 徽标账号过滤口径 = 列表账号过滤口径
- Rule：徽标统计使用的"启用账号"集合与 `MailboxService.listMailbox` 中 `senderAccountRepository.findAllByEnabledTrue().map{it.accountCode}` 完全一致。启用账号为空时徽标返回 0。
- Applies to：`UnmatchedInboundMailService.listManualReviewQueue`（计算 activeCodes 的位置）。
- Violation consequence：停用 / 启用账号后徽标与列表口径漂移。

### Invariant I-4: 徽标独立于列表筛选
- Rule：刷新徽标的请求 `/api/mail/unmatched-inbound`（用于徽标的调用）**不得**携带收发件箱列表的 direction / keyword / recipient / 日期 / tag 等筛选参数。徽标只接受"账号过滤"这一维度（由后端按启用账号自动决定，前端不传筛选）。
- Applies to：前端 `refreshUnmatchedBadge`。
- Violation consequence：徽标随列表筛选跳变，违背"稳定待处理总数"语义。

### Invariant I-5: 勾选仅待处理 ⇒ 列表无日期过滤
- Rule：当 `state.mailbox.onlyPending === true` 时，`loadMailbox` 不应用默认日期窗口、不向 `/api/mail/mailbox` 发送 `startDate` / `endDate` 参数；日期输入框 `disabled`。取消勾选恢复默认窗口与可编辑状态。
- Applies to：前端 `loadMailbox`、「仅待处理」change 处理器、`mailboxFilterTag`「待处理」分支。
- Violation consequence：列表仍按 7 天截断，"共 N 条" < 徽标，无法对齐。

### Invariant I-6: 处理待处理邮件后徽标刷新
- Rule：任一使 `process_status` 从 `MANUAL_REVIEW` 变为 `PROCESSED` 的前端操作成功后，必须触发 `refreshUnmatchedBadge`（直接调用或经由 `loadMailbox` 间接调用，因 `loadMailbox` 末尾已调用它）。
- Applies to：收发件箱列表内的处理动作、待匹配面板内的绑定 / 标记已处理动作。
- Violation consequence：邮件已处理但徽标不下降，数字陈旧。

---

## 现状审计

### 数据存储：`inbound_mail_processing`（MySQL，Spring Data JDBC）
- 关键字段：`process_status`（`MANUAL_REVIEW` / `PROCESSED`）、`reason_type`（可空）、`sender_account_code`、`received_at`、`expert_contact_id`。
- 产生 `MANUAL_REVIEW` 的写路径（reason_type 取值来源）：
  1. `AutoMailReplyService.kt:76` — `UNMATCHED_CONTACT`
  2. `AutoMailReplyService.kt:137,146,231,296` — `UNCLEAR_INTENT`
  3. `AutoMailReplyService.kt:257-264` — `NOT_INTERESTED`（或其它意图）
  4. `AutoMailReplyService.kt:320,412` — `RECIPIENT_UNSUBSCRIBED` ← **不在当前徽标累加的 4 个 type 内**
  5. `AutoMailReplyService.kt:786,810-846` — `confirmProcessed(...,"MANUAL_REVIEW", reasonType,...)`，reasonType 可能为附件/意图类（`InboundIntentClassifier` 多处走 `AutoIntentAction.MANUAL_REVIEW`，可能落 `NULL` 或其它 type）
- 使 `MANUAL_REVIEW` → `PROCESSED` 的写路径：
  1. `UnmatchedInboundMailService.bindToContact`（:167）
  2. `UnmatchedInboundMailService.markResolved`（:207）
  3. `PendingMailOperationService.kt:240-248`（标记已处理，reason_type→`MANUAL_RESOLVED`）

### 读路径 A：徽标
- 后端：`InboundMailProcessingRepository.countGroupedByReasonType()`（:71-77）
  - 现状 SQL：`WHERE process_status='MANUAL_REVIEW' AND reason_type IS NOT NULL GROUP BY reason_type`
  - **无账号过滤；排除了 reason_type IS NULL；不返回总数。**
- Service：`UnmatchedInboundMailService.listManualReviewQueue`（:46-52）将分组结果放入 `countsByReasonType`。
- Controller：`UnmatchedInboundMailController.list`（:34-68）返回 `InboundMailProcessingListResponse(records,totalCount,countsByReasonType)`。
  - 注意：响应里的 `totalCount` 来自 `countManualReviewQueue(reasonType,email,subject)`（:65），该查询**也无账号过滤**，且服务于「待匹配」面板分页，非徽标专用。
- 前端：`refreshUnmatchedBadge`（app.js:4336-4342）GET `/api/mail/unmatched-inbound?pageSize=1&pageOffset=0` → `updateUnmatchedBadge(data.countsByReasonType)`（:4344-4355）
  - `high = NOT_INTERESTED + QA_NO_MATCH`；`normal = UNMATCHED_CONTACT + UNCLEAR_INTENT`。
  - **缺陷**：`RECIPIENT_UNSUBSCRIBED`、`NULL`、其它意图 type 都不计入 → 徽标可能偏大或偏小于真实待处理数（取决于哪些 type 存在）；且无账号过滤。

### 读路径 B：「仅待处理」列表
- 后端：`MailRecordRepository.listMailbox` / `countMailbox`（:230-341），INBOUND 分支：
  `WHERE imp.sender_account_code IN (:accountCodes) AND (:onlyPending=0 OR imp.process_status='MANUAL_REVIEW') AND (日期/关键词/收件人过滤...)`
- Service：`MailboxService.listMailbox`（:26-96）`activeCodes = findAllByEnabledTrue().map{accountCode}`；`onlyPending = if(pending)1 else 0`。
- 前端：`loadMailbox`（app.js:6092-6137）
  - :6100-6106 — 首次进入设默认最近 7 天 `startDate/endDate`（`dateDefaultsApplied` 仅判一次，**未区分 onlyPending**）。
  - :6116-6121 — 始终把 `startDate/endDate` 写入 query 参数。
  - :6122 — `if(onlyPending) params.set("pending","true")`。
  - :6133 — 末尾 `await refreshUnmatchedBadge()`。
- 「仅待处理」交互：
  - 复选框 change（:5759-5767）切换 `onlyPending` 后 `loadMailbox`。
  - 标签选「待处理」（:5747-5758）会自动勾选 onlyPending 并 `loadMailbox`。
- 日期输入框 DOM：`index.html:536` `#mailboxFilterStartDate`、`:538` `#mailboxFilterEndDate`；复选框 `:533` `#mailboxFilterOnlyPending`。

### 交互点（Interaction points）
- IP-1：写路径"产生 MANUAL_REVIEW（任意 reason_type）" × 读路径 A（徽标）—— 徽标必须覆盖**所有** reason_type，否则 RECIPIENT_UNSUBSCRIBED / NULL 漏计。→ I-1, I-2。
- IP-2：读路径 A（徽标账号集）× 读路径 B（列表 `activeCodes`）—— 两者须用同一"启用账号"定义。→ I-3。
- IP-3：写路径"MANUAL_REVIEW→PROCESSED"（bind/markResolved/PendingMailOperationService）× 读路径 A —— 处理后徽标须刷新。→ I-6。
- IP-4：前端列表筛选 × 徽标刷新调用 —— 徽标调用不得被列表筛选污染。→ I-4。

---

## 实现方案

### 阶段 1：后端 — 徽标统计加账号过滤并覆盖全量（I-1, I-2, I-3）

**Task 1.1**（遵守 I-1）在 `InboundMailProcessingRepository.kt` 新增账号过滤的总数查询：
```kotlin
@Query("""
    SELECT COUNT(*) FROM inbound_mail_processing
    WHERE process_status = 'MANUAL_REVIEW'
      AND sender_account_code IN (:accountCodes)
""")
fun countManualReviewByAccounts(accountCodes: List<String>): Long
```

**Task 1.2**（遵守 I-2, I-3）在同文件新增/改造分组查询，使其支持账号过滤（保留原 `countGroupedByReasonType()` 不动，避免影响其它调用方；新增带账号参数的重载）：
```kotlin
@Query("""
    SELECT reason_type, COUNT(*) as count
    FROM inbound_mail_processing
    WHERE process_status = 'MANUAL_REVIEW' AND reason_type IS NOT NULL
      AND sender_account_code IN (:accountCodes)
    GROUP BY reason_type
""")
fun countGroupedByReasonTypeForAccounts(accountCodes: List<String>): List<ReasonTypeCount>
```
> 说明：分组查询仅用于 high 分色；NULL / 其它 type 不必出现在分组里，因为前端按 I-2 用 `normal = 总数 − high` 兜底。

**Task 1.3**（遵守 I-1, I-3）改造 `UnmatchedInboundMailService`：
- 构造注入 `MailSenderAccountRepository`（参照 `MailboxService` 的用法）。
- 在 `listManualReviewQueue` 内计算 `activeCodes = senderAccountRepository.findAllByEnabledTrue().map{it.accountCode}`。
- 调用 `countManualReviewByAccounts(activeCodes)` 得到 `manualReviewTotal`；`activeCodes` 为空时直接置 0 且分组返回空（避免 `IN ()` SQL 问题）。
- 调用 `countGroupedByReasonTypeForAccounts(activeCodes)` 得到分色用计数。
- 在 `ManualReviewQueueResult` 增加字段 `manualReviewTotal: Long`，与 `countsByReasonType` 一并返回。
- **保持** `totalCount`（`countManualReviewQueue`）与 records 分页逻辑不变（待匹配面板仍用）。

**Task 1.4** 在 `UnmatchedInboundMailController.list` 的响应 DTO `InboundMailProcessingListResponse` 增加字段 `manualReviewTotal: Long`，透传 `result.manualReviewTotal`。

### 阶段 2：前端 — 徽标取全量总数并与列表筛选解耦（I-2, I-4, I-6）

**Task 2.1**（遵守 I-4）确认 `refreshUnmatchedBadge`（app.js:4336）调用的 URL 仍为固定 `/api/mail/unmatched-inbound?pageSize=1&pageOffset=0`，不拼接任何列表筛选参数。改为读取新字段：
```js
async function refreshUnmatchedBadge() {
    try {
        const data = await api("/api/mail/unmatched-inbound?pageSize=1&pageOffset=0");
        updateUnmatchedBadge(data.countsByReasonType, data.manualReviewTotal);
    } catch (_) {}
}
```

**Task 2.2**（遵守 I-2）改造 `updateUnmatchedBadge`（app.js:4344）：
```js
function updateUnmatchedBadge(counts, total) {
    if (!counts) {
        api("/api/mail/unmatched-inbound?pageSize=1&pageOffset=0").then(data => {
            updateUnmatchedBadge(data.countsByReasonType, data.manualReviewTotal);
        }).catch(() => {});
        return;
    }
    const high = Array.from(HIGH_PRIORITY_REASON_TYPES).reduce((s, k) => s + (counts[k] || 0), 0);
    const t = typeof total === "number" ? total : Object.values(counts).reduce((s, v) => s + (v || 0), 0);
    const normal = Math.max(0, t - high);
    setBadge("#unmatchedBadgeHigh", high);
    setBadge("#unmatchedBadgeNormal", normal);
}
```
> 注意：第 5933 行 `updateUnmatchedBadge()` 无参调用走 fallback 分支，保持可用；fallback 分支同样补 `manualReviewTotal`。

**Task 2.3**（遵守 I-6）核对处理待处理邮件的前端动作（收发件箱列表内处理、待匹配面板 bind / markResolved）成功后均会触发 `loadMailbox`（其末尾 6133 调用 `refreshUnmatchedBadge`）或直接调用 `refreshUnmatchedBadge`。若待匹配面板的处理动作未触发，补一行 `await refreshUnmatchedBadge()`。仅在缺失处补，不新增机制。

### 阶段 3：前端 — 勾选仅待处理时查全量并置灰日期（I-5）

**Task 3.1**（遵守 I-5）`loadMailbox`（app.js:6092）日期默认窗口加 onlyPending 守卫：
```js
if (!state.mailbox.onlyPending && !state.mailbox.dateDefaultsApplied && !startInput.value && !endInput.value) {
    // ...设默认最近 7 天...
}
state.mailbox.dateDefaultsApplied = true;
```

**Task 3.2**（遵守 I-5）日期参数仅在非 onlyPending 时发送（app.js:6116-6121 区域）：
```js
if (!state.mailbox.onlyPending) {
    if (startDate) params.set("startDate", startDate);
    if (endDate) params.set("endDate", endDate);
}
```

**Task 3.3**（遵守 I-5）新增置灰逻辑：在 `loadMailbox` 读取 onlyPending 后，同步禁用/启用日期框，并加视觉灰态 class：
```js
const disabled = state.mailbox.onlyPending;
startInput.disabled = disabled;
endInput.disabled = disabled;
startInput.classList.toggle("input-disabled", disabled);
endInput.classList.toggle("input-disabled", disabled);
```
在 `styles.css` 增加 `.input-disabled { opacity:.5; cursor:not-allowed; background:#f1f1f1; }`（若已有等价禁用样式则复用，不重复定义）。

**Task 3.4** 在「仅待处理」change 处理器（app.js:5759）与「待处理」标签分支（:5747-5758）切换后，依赖 `loadMailbox` 统一处理置灰（无需各自重复），确保取消勾选时恢复可编辑。

---

## 变更文件清单

| # | 文件 | 改动 | 阶段 |
|---|------|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt` | 新增 `countManualReviewByAccounts`、`countGroupedByReasonTypeForAccounts` 两个查询 | 1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt` | 注入账号仓库；计算 activeCodes；返回 `manualReviewTotal`；`ManualReviewQueueResult` 加字段 | 1 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | `InboundMailProcessingListResponse` 加 `manualReviewTotal` 并透传 | 1 |
| 4 | `src/main/resources/static/app.js` | `refreshUnmatchedBadge` / `updateUnmatchedBadge` 取全量总数；`loadMailbox` 日期守卫+参数条件+置灰；处理后刷新核对 | 2,3 |
| 5 | `src/main/resources/static/styles.css` | 新增 `.input-disabled` 禁用灰态（若无等价样式） | 3 |

文件数：5 ≤ 10 ✅；子系统：后端 mail 统计 + 前端收发件箱 = 2 ≤ 2 ✅；无新增共享存储字段 ✅。

---

## 验收标准

- **I-1**：构造数据：启用账号下有 N 条 `MANUAL_REVIEW`（含 1 条 `RECIPIENT_UNSUBSCRIBED`、1 条 `reason_type=NULL`），停用账号下另有 M 条 `MANUAL_REVIEW`。断言 `GET /api/mail/unmatched-inbound?pageSize=1` 返回 `manualReviewTotal == N`（不含 M、含 unsubscribed 与 null）。
- **I-2**：前端用上述响应渲染后，`#unmatchedBadgeHigh` + `#unmatchedBadgeNormal` 文本相加 == N；NULL / RECIPIENT_UNSUBSCRIBED 条目计入 normal。
- **I-3**：停用一个原本有待处理邮件的账号后重新拉取，徽标 `manualReviewTotal` 相应减少；与 `GET /api/mail/mailbox?pending=true&size=1` 的 `totalCount`（无其它筛选时）相等。
- **I-4**：在列表输入关键词 / 改方向 / 改日期后触发 `loadMailbox`，徽标数字不变（仅当邮件被处理时才变）。验证 `refreshUnmatchedBadge` 请求 URL 不含 keyword/direction/date 参数。
- **I-5**：勾选「仅待处理」→ `#mailboxFilterStartDate`、`#mailboxFilterEndDate` 变为 `disabled` 且呈灰态；`/api/mail/mailbox` 请求不含 `startDate`/`endDate`；列表「共 N 条」== 徽标 N。取消勾选 → 日期框恢复可编辑且重新应用默认 7 天窗口。
- **I-6**：在列表/待匹配面板将一条 `MANUAL_REVIEW` 标记已处理后，徽标数字 −1，列表「共 N 条」−1，二者保持相等。
- **集成场景（IP-1..IP-4）**：勾选「仅待处理」、清空所有其它筛选时，列表底部「共 N 条」与左侧徽标 high+normal 之和三者完全一致；处理任意一条后三者同步下降并仍一致。

---

## 备注（交付执行 agent）

- 本项目为 Kotlin + Spring Boot 2.7 / JDK 11，构建测试须用 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
- Spring Data JDBC 的 `IN (:accountCodes)` 在列表为空时会生成非法 SQL，务必在 service 层对空集合短路返回 0（参照 `MailboxService.listMailbox` 对 `activeCodes.isEmpty()` 的处理）。
- 不要修改已应用的 Flyway 迁移；本计划无 schema 变更。
- 验证完成后按项目工作流交 `fix-v` 复验。
