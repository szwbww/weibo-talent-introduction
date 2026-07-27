# 收发件箱 Tab 开发计划

> 日期: 2026-06-22
> 状态: 待实施

---

## 需求描述

**可观察结果**：管理后台新增"收发件箱"Tab，展示所有已激活邮箱账号的收件和发件记录，支持按收件人、内容关键词过滤，按时间排序，系统自动发送的邮件需标明来源。

**不可变更项**：
- 不修改 `mail_record` 表结构（现有字段已满足需求）
- 不修改 `mail_sender_account` 表结构
- 不修改任何现有写入路径（邮件发送/接收流程）
- 不修改任何现有 API 端点的行为
- 不修改已有 Flyway 迁移文件

**不在范围内**：
- 邮件正文内联编辑或从该页面发送邮件
- 附件的下载/预览功能（仅显示"有附件"标记）
- `inbound_mail_processing` 表中未绑定到 `expert_contact` 的孤立收件记录（这些属于"待处理邮件"Tab 的职责）
- 新增数据库索引优化（现有索引足以支撑初期查询量，后续视性能再评估）

---

## 关键不变量

### Invariant I-1: 纯只读查询，不引入新的写路径
- Rule: 本功能涉及的所有后端代码只做 SELECT 查询，不执行任何 INSERT / UPDATE / DELETE。Service 层不注入任何会产生写操作的依赖。
- Applies to: `MailboxController`, `MailboxService`, `MailRecordRepository` 新增查询方法
- Violation consequence: 意外修改邮件状态会破坏邮件管道的数据完整性

### Invariant I-2: 查询范围限定为已激活账号
- Rule: 收发件箱查询必须限定 `sender_account_code IN (所有 enabled=true 的账号 code)`，不暴露已禁用账号的邮件。账号筛选下拉也仅展示已激活账号。
- Applies to: `MailboxService.listMailbox()`, 前端账号下拉填充逻辑
- Violation consequence: 暴露已停用账号的邮件记录，信息泄露

### Invariant I-3: 系统发送标记取自 `triggered_by` 字段
- Rule: OUTBOUND 邮件的来源标记直接取 `mail_record.triggered_by` 字段值（SYSTEM / OPERATOR / MANUAL）。INBOUND 邮件该字段为 NULL，前端不显示来源标签。不引入任何新的来源判断逻辑。
- Applies to: `MailboxController` 响应 DTO 构建, 前端渲染逻辑
- Violation consequence: 来源标签与系统其他地方（如监控页）不一致

### Invariant I-4: 分页查询必须同时返回 totalCount
- Rule: 列表 API 返回 `{items: [...], totalCount: Long}` 结构。`totalCount` 通过独立的 `COUNT(*)` 查询获取（与数据查询使用相同的 WHERE 条件）。前端依赖 `totalCount` 渲染分页器。
- Applies to: `MailRecordRepository` 的 count 查询, `MailboxService`, `MailboxController` 响应
- Violation consequence: 前端分页器无法正确计算总页数

### Invariant I-5: body 字段不在列表接口中返回完整内容
- Rule: 列表接口对 `cleaned_body`（优先）或 `body` 截取前 200 字符作为 `bodyPreview` 返回。完整正文仅在点击查看详情时通过已有的 `findByIdOrNull` 方法获取。
- Applies to: `MailboxService` DTO 组装逻辑
- Violation consequence: 列表接口返回大量 LONGTEXT 数据，响应体过大，前端渲染卡顿

---

## 现状审计

### mail_record 表

**Schema 关键字段**：
- `id` BIGINT PK, `expert_contact_id` BIGINT NOT NULL FK, `direction` VARCHAR(16) (INBOUND/OUTBOUND)
- `mail_type` VARCHAR(64) (INTRODUCTION / REPLY / QA_REPLY / MANUAL_QA_REPLY / MEETING_INVITATION / MEETING_CONFIRMATION)
- `sender_account_code` VARCHAR(64), `triggered_by` VARCHAR(16) (SYSTEM / OPERATOR / MANUAL, 仅 OUTBOUND)
- `subject` VARCHAR(255), `body` LONGTEXT, `cleaned_body` LONGTEXT
- `send_status` VARCHAR(32) (SENT / FAILED), `sent_at` DATETIME, `received_at` DATETIME, `created_at` DATETIME

**现有索引**：
1. `idx_mail_record_dir_type_sent` (direction, mail_type, sent_at)
2. `idx_mail_record_dir_received` (direction, received_at)
3. `idx_mail_record_sender_sent` (sender_account_code, sent_at)
4. `idx_mail_record_triggered_sent` (triggered_by, sent_at)
5. `idx_mail_record_source_inbound` (source_inbound_id)
6. `idx_mail_record_status_created` (direction, send_status, created_at)

**写路径**（本计划不修改任何写路径）：
1. `InitialOutreachService` — 发送首封介绍邮件，INSERT OUTBOUND/INTRODUCTION 记录
2. `AutoMailReplyService.processSingle()` — INSERT INBOUND/REPLY 记录 + INSERT OUTBOUND 自动回复记录
3. `PendingMailOperationService` — INSERT OUTBOUND 人工回复/QA 回复记录
4. `ExpertContactManagementController` 相关 service — INSERT OUTBOUND 手动邮件记录

**读路径**（本计划新增一条读路径）：
1. `findAllByExpertContactIdOrderByCreatedAtAsc` — 专家联系详情页加载对话时间线
2. `listIntroductions` / `countIntroductions` — 监控页首发邮件子 Tab
3. `listOutboundReplies` / `countOutboundReplies` — 监控页发信回复子 Tab
4. `aggregateSenderAccountStats` — 监控页账号统计
5. **【新增】** `listMailbox` / `countMailbox` — 收发件箱 Tab 分页查询

**交互点**：
- 新增读路径 `listMailbox` 消费 `sender_account_code` 和 `triggered_by` 字段，这两个字段由写路径 1-4 写入。本计划不改变写入逻辑，仅读取，无冲突。

### expert_contact 表

**本计划仅通过 JOIN 读取以下字段**：`expert_email` VARCHAR(255), `expert_name` VARCHAR(255)

**写路径**：不涉及，本计划不修改 expert_contact 表。

**读路径**：本计划通过 SQL JOIN 在 `mail_record` 查询中关联获取 `expert_email` 和 `expert_name`，不引入新的 Repository 方法。

### mail_sender_account 表

**本计划仅读取**：`account_code`, `sender_email`, `enabled` 字段。

**读路径**：复用已有 `findAllByEnabledTrue()` 获取活跃账号列表。

### mail_attachment 表

**本计划仅做 EXISTS 子查询判断是否有附件**，不读取附件内容。

---

## 实现方案

### Phase 1: 后端 — Repository 查询（遵循 I-1, I-4, I-5）

**Task 1.1**: 在 `MailRecordRepository` 中新增两个 `@Query` 方法

文件: `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt`

```kotlin
// 数据查询 — JOIN expert_contact 获取收件人信息，子查询判断附件
@Query("""
    SELECT mr.id, mr.expert_contact_id, mr.direction, mr.mail_type,
           mr.sender_account_code, mr.triggered_by, mr.subject,
           SUBSTRING(COALESCE(mr.cleaned_body, mr.body), 1, 200) AS body_preview,
           mr.send_status, mr.sent_at, mr.received_at, mr.created_at,
           ec.expert_email, ec.expert_name,
           EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS has_attachment
      FROM mail_record mr
      LEFT JOIN expert_contact ec ON mr.expert_contact_id = ec.id
     WHERE mr.sender_account_code IN (:accountCodes)
       AND (:direction IS NULL OR mr.direction = :direction)
       AND (:accountCode IS NULL OR mr.sender_account_code = :accountCode)
       AND (:keyword IS NULL OR mr.subject LIKE CONCAT('%', :keyword, '%')
                              OR mr.cleaned_body LIKE CONCAT('%', :keyword, '%'))
       AND (:recipientEmail IS NULL OR ec.expert_email LIKE CONCAT('%', :recipientEmail, '%'))
       AND (:startTime IS NULL OR COALESCE(mr.sent_at, mr.received_at) >= :startTime)
       AND (:endTime IS NULL OR COALESCE(mr.sent_at, mr.received_at) < :endTime)
     ORDER BY COALESCE(mr.sent_at, mr.received_at) DESC
     LIMIT :limit OFFSET :offset
""")
fun listMailbox(...): List<MailboxRow>

// 计数查询 — 相同 WHERE 条件
@Query("""...""")  // 同上 WHERE，SELECT COUNT(*)
fun countMailbox(...): Long
```

> 注意：Spring Data JDBC 的 `@Query` 返回自定义投影类型时需定义一个 `data class MailboxRow`，字段名与 SQL 列别名对应（使用下划线命名映射）。

**Task 1.2**: 定义 `MailboxRow` 投影类和 `MailboxItemDto` 响应 DTO

文件: `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxController.kt`（DTO 与 Controller 放同一文件，复用项目现有模式）

```kotlin
// Repository 投影
data class MailboxRow(
    val id: Long,
    val expertContactId: Long,
    val direction: String,
    val mailType: String,
    val senderAccountCode: String?,
    val triggeredBy: String?,
    val subject: String?,
    val bodyPreview: String?,    // SUBSTRING 截取后的预览
    val sendStatus: String?,
    val sentAt: LocalDateTime?,
    val receivedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val expertEmail: String?,
    val expertName: String?,
    val hasAttachment: Boolean
)

// API 响应 DTO
data class MailboxItemResponse(
    val id: Long,
    val expertContactId: Long,
    val direction: String,
    val mailType: String,
    val senderAccountCode: String?,
    val triggeredBy: String?,       // I-3: 直接传透
    val isSystemSent: Boolean,      // I-3: triggeredBy == "SYSTEM"
    val expertEmail: String?,
    val expertName: String?,
    val subject: String?,
    val bodyPreview: String?,       // I-5: 截取后
    val hasAttachment: Boolean,
    val sendStatus: String?,
    val timestamp: String?          // ISO 格式, COALESCE(sentAt, receivedAt)
)

data class MailboxListResponse(
    val items: List<MailboxItemResponse>,
    val totalCount: Long            // I-4
)
```

### Phase 2: 后端 — Service + Controller（遵循 I-1, I-2, I-3, I-4）

**Task 2.1**: 新建 `MailboxService`

文件: `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt`

职责：
1. 调用 `MailSenderAccountRepository.findAllByEnabledTrue()` 获取活跃账号 code 列表 → **I-2**
2. 如果指定了 `accountCode` 过滤，验证该 code 在活跃列表中 → **I-2**
3. 调用 `MailRecordRepository.listMailbox(accountCodes, ...)` + `countMailbox(...)` → **I-4**
4. 将 `MailboxRow` 映射为 `MailboxItemResponse`，设置 `isSystemSent = (triggeredBy == "SYSTEM")` → **I-3**
5. 返回 `MailboxListResponse` → **I-1**（全流程无写操作）

```kotlin
@Service
class MailboxService(
    private val mailRecordRepository: MailRecordRepository,
    private val senderAccountRepository: MailSenderAccountRepository
) {
    fun listMailbox(
        direction: String?,
        accountCode: String?,
        keyword: String?,
        recipientEmail: String?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        page: Int,
        size: Int
    ): MailboxListResponse {
        val activeAccounts = senderAccountRepository.findAllByEnabledTrue()
        val activeCodes = activeAccounts.map { it.accountCode }
        if (activeCodes.isEmpty()) return MailboxListResponse(emptyList(), 0)

        // I-2: 如指定 accountCode，必须在活跃列表中
        if (accountCode != null && accountCode !in activeCodes) {
            return MailboxListResponse(emptyList(), 0)
        }

        val offset = page * size
        val rows = mailRecordRepository.listMailbox(
            accountCodes = activeCodes,
            direction = direction,
            accountCode = accountCode,
            keyword = keyword,
            recipientEmail = recipientEmail,
            startTime = startTime,
            endTime = endTime,
            limit = size,
            offset = offset
        )
        val total = mailRecordRepository.countMailbox(
            accountCodes = activeCodes,
            direction = direction,
            accountCode = accountCode,
            keyword = keyword,
            recipientEmail = recipientEmail,
            startTime = startTime,
            endTime = endTime
        )
        return MailboxListResponse(
            items = rows.map { it.toResponse() },
            totalCount = total
        )
    }
}
```

**Task 2.2**: 新建 `MailboxController`

文件: `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxController.kt`

```kotlin
@RestController
@RequestMapping("/api/mail/mailbox")
class MailboxController(private val mailboxService: MailboxService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false) accountCode: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) recipientEmail: String?,
        @RequestParam(required = false) startDate: String?,   // yyyy-MM-dd
        @RequestParam(required = false) endDate: String?,     // yyyy-MM-dd
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): MailboxListResponse {
        val startTime = startDate?.let { LocalDate.parse(it).atStartOfDay() }
        val endTime = endDate?.let { LocalDate.parse(it).plusDays(1).atStartOfDay() }
        return mailboxService.listMailbox(
            direction, accountCode, keyword, recipientEmail,
            startTime, endTime, page, size.coerceIn(1, 100)
        )
    }
}
```

### Phase 3: 前端（遵循 I-3, I-4, I-5）

**Task 3.1**: `index.html` — 新增侧边栏 Tab 和视图骨架

文件: `src/main/resources/static/index.html`

在"任务记录" Tab 之前插入：
```html
<button class="nav-tab" data-view="mailbox">
    <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor"
         stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
        <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
        <polyline points="22,6 12,13 2,6"/>
    </svg>
    <span>收发件箱</span>
</button>
```

在 `view-unmatched` section 之后插入新 view section，包含：
- 过滤工具栏：账号下拉（动态填充活跃账号）、方向下拉（全部/收件/发件）、收件人输入框、关键词输入框、起止日期、查询按钮
- 数据表格：时间、方向、账号、专家邮箱、专家姓名、主题、内容预览、类型、来源、附件、状态
- 分页控件

**Task 3.2**: `app.js` — 新增状态、加载、渲染逻辑

文件: `src/main/resources/static/app.js`

修改点：
1. `state` 对象新增 `mailbox` 子状态：`{ items: [], page: 0, totalCount: 0, pageSize: 20 }`
2. `viewMeta` 新增条目：`mailbox: ["收发件箱", "查看所有已激活邮箱的收发记录，按时间、收件人、内容筛选。"]`
3. `refreshCurrentView()` 新增：`if (state.view === "mailbox") await loadMailbox();`
4. 新增 `loadMailbox()` 函数：
   - 首次进入时调用 `/api/mail/sender-accounts` 填充账号下拉（过滤 `enabled === true`）
   - 读取过滤条件，拼装 query params，调用 `GET /api/mail/mailbox?...`
   - 渲染表格行，`triggeredBy` 列渲染规则 → **I-3**：
     - `SYSTEM` → `<span class="badge warn">系统自动</span>`
     - `OPERATOR` → `<span class="badge">人工</span>`
     - `MANUAL` → `<span class="badge">手动</span>`
     - `null`（INBOUND）→ 不显示
   - `bodyPreview` 直接展示 → **I-5**
   - 分页器基于 `totalCount` 和 `pageSize` 计算 → **I-4**
5. 新增 `renderMailboxTable()` 函数渲染表格
6. 新增 `renderMailboxPagination()` 函数渲染分页
7. 事件委托中增加对 mailbox 区域的 click 处理（查询按钮、分页按钮）

---

## 变更文件清单

| # | 文件路径 | 操作 | 说明 |
|---|---------|------|------|
| 1 | `src/main/kotlin/.../mail/repository/MailRecordRepository.kt` | 修改 | 新增 `MailboxRow` data class + `listMailbox()` / `countMailbox()` 查询方法 |
| 2 | `src/main/kotlin/.../mail/service/MailboxService.kt` | **新增** | 收发件箱查询服务，纯只读 |
| 3 | `src/main/kotlin/.../mail/controller/MailboxController.kt` | **新增** | REST Controller + DTO 定义 |
| 4 | `src/main/resources/static/index.html` | 修改 | 新增 nav-tab + view section HTML |
| 5 | `src/main/resources/static/app.js` | 修改 | 新增 state/viewMeta/loadMailbox/render 逻辑 |

**总计 5 个文件**（2 新增 + 3 修改），符合 ≤10 限制。

**涉及子系统**: 1 个（mail 模块的只读查询 + 前端展示），符合 ≤2 限制。

**新增数据字段**: 0 个（不修改任何表结构），符合 ≤1 限制。

---

## 验收标准

### 按不变量逐项验证

- **I-1**: 检查 `MailboxService` 和 `MailboxController` 中不存在任何 `save()`、`update()`、`delete()` 调用。`MailRecordRepository` 新增方法均为 `@Query` SELECT 语句。
- **I-2**: 测试场景 — 禁用一个账号后刷新收发件箱，该账号的邮件记录不出现在列表中。账号下拉也不包含该禁用账号。
- **I-3**: 测试场景 — 系统自动回复的邮件显示"系统自动"标签；人工回复显示"人工"标签；收件方向无来源标签。与监控页的 `triggeredBy` 展示一致。
- **I-4**: 测试场景 — 设置 `pageSize=5`，验证返回的 `totalCount` 与数据库 `SELECT COUNT(*)` 结果一致，分页器页数 = `ceil(totalCount / pageSize)`。
- **I-5**: 测试场景 — 列表接口返回的 `bodyPreview` 不超过 200 字符。对包含长正文的邮件，验证截断正确。

### 集成场景

1. **全量加载**: 不带任何过滤条件，验证返回所有已激活账号的收发邮件，按时间倒序。
2. **方向过滤**: direction=INBOUND 只返回收件; direction=OUTBOUND 只返回发件。
3. **账号过滤**: 指定 accountCode，只返回该账号的记录。
4. **关键词搜索**: keyword 匹配 subject 和 cleaned_body 中的内容。
5. **收件人过滤**: recipientEmail 模糊匹配 expert_contact.expert_email。
6. **日期范围**: startDate + endDate 正确筛选，endDate 采用 `< 次日 00:00` 语义（闭-开区间）。
7. **分页**: 翻页后数据连续、不重复、不遗漏。
8. **空结果**: 无已激活账号时返回空列表，不报错。

---

## 自检清单

- [x] 关键不变量 section 存在，有 5 个不变量（无新增字段/状态，但覆盖了所有关键语义）
- [x] 现状审计列出了 mail_record 的所有写路径（4 个）和读路径（4 个已有 + 1 个新增）
- [x] 无任务引入新的写路径（I-1 明确禁止）
- [x] 文件数 = 5 ≤ 10
- [x] 子系统数 = 1 ≤ 2
- [x] 每个 Task 引用了所遵循的不变量编号
- [x] 验收标准每个不变量均有对应检查
- [x] 文件清单中无 "and related files" 或 "etc."
- [x] Out-of-scope 明确排除了附件预览、孤立收件记录、索引优化
