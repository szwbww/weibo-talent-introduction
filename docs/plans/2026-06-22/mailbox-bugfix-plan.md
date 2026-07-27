# 收发件箱页面 Bug 修复计划

## 需求描述

- **可观测结果**：收发件箱页面能正常加载邮件记录列表；日期筛选器首次加载时默认选中近 7 天。
- **不得变更**：`listMailbox` / `countMailbox` SQL 查询逻辑、分页逻辑、其余筛选项行为。
- **不在范围内**：其他页面的日期筛选默认值；MailboxRow 增减字段；API 参数签名变更。

## 关键不变量

### Invariant I-1: hasAttachment 字段必须由 SQL CAST 为数值型，在 MailboxRow 中以 Long 接收
- Rule：MySQL `EXISTS(...)` 返回 `BIGINT(1/0)`，Spring Data JDBC 无法自动从 `java.lang.Long` 转换为 `boolean`。因此 SQL 中需用 `CAST(EXISTS(...) AS SIGNED)` 保留 Long 语义，DTO 中字段改为 `Long`，在 Service 层转 Boolean。
- Applies to：`MailRecordRepository.listMailbox()`、`MailboxRow` data class、`MailboxService.listMailbox()`
- Violation consequence：查询报 "No converter found capable of converting from type [java.lang.Long] to type [boolean]"，整个收发件箱页面无法加载。

### Invariant I-2: 前端日期筛选器在 loadMailbox() 首次调用前必须已设默认值
- Rule：`mailboxFilterStartDate` 默认值 = 今天 - 7 天，`mailboxFilterEndDate` 默认值 = 今天。格式 `yyyy-MM-dd`。仅在两个 input 均为空时设置（用户手动清空后再查询不强制覆盖）。
- Applies to：`app.js` 中 `loadMailbox()` 函数
- Violation consequence：首次打开页面时无日期范围约束，全量查询可能很慢或返回过多数据。

## 现状审计

### MailRecordRepository.listMailbox SQL
- 关键片段：`EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS has_attachment`
- 返回类型：MySQL `EXISTS` → `BIGINT`
- Spring Data JDBC 映射目标：`MailboxRow.hasAttachment: Boolean` → **类型不匹配**

### MailboxRow (data class)
- 定义位置：`MailRecordRepository.kt:277`
- `hasAttachment: Boolean` — 是唯一的 Boolean 字段，也是唯一由子查询计算的字段

### MailboxService.listMailbox
- 读取 `row.hasAttachment` 传入 `MailboxItemResponse`
- 如果 `MailboxRow` 字段类型改为 `Long`，此处需 `row.hasAttachment != 0L`

### 前端 app.js loadMailbox()
- L5734-5735：直接读 `$("#mailboxFilterStartDate").value` / `$("#mailboxFilterEndDate").value`
- 无任何默认值设置逻辑
- `state.mailbox` 初始化（L37-43）无日期相关 state

### 写路径（不涉及修改）
- `listMailbox` / `countMailbox` 是只读查询，无写路径受影响

## 实现方案

### 任务 1：修复 hasAttachment 类型转换错误 [I-1]

**文件：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt`**

1a. `listMailbox` SQL 中将：
```sql
EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS has_attachment
```
改为：
```sql
CAST(EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS SIGNED) AS has_attachment
```

1b. `MailboxRow` data class 中将 `hasAttachment: Boolean` 改为 `hasAttachment: Long`。

**文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt`**

1c. `MailboxService.listMailbox()` 中将 `hasAttachment = row.hasAttachment` 改为 `hasAttachment = row.hasAttachment != 0L`。

### 任务 2：日期筛选添加默认值（近 7 天）[I-2]

**文件：`src/main/resources/static/app.js`**

2a. 在 `loadMailbox()` 函数中，读取 startDate / endDate 之前，添加默认值设置逻辑：

```javascript
// 设置默认日期范围：近 7 天
const startInput = $("#mailboxFilterStartDate");
const endInput = $("#mailboxFilterEndDate");
if (!startInput.value && !endInput.value) {
    const today = new Date();
    const weekAgo = new Date();
    weekAgo.setDate(today.getDate() - 7);
    endInput.value = today.toISOString().slice(0, 10);
    startInput.value = weekAgo.toISOString().slice(0, 10);
}
```

此逻辑放在 `await loadMailboxAccounts()` 之后、`const startDate = ...` 之前。仅在两个 input 都为空时生效（首次加载），用户清空日期手动搜索时不覆盖。

### 任务 3：修复测试中 MailboxRow 类型 [I-1]

**文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt`**

3a. `mockRow` 的 `hasAttachment = true` 改为 `hasAttachment = 1L`，`false` 对应改为 `0L`。

## 变更文件清单

| # | 文件 | 变更内容 |
|---|------|----------|
| 1 | `src/main/kotlin/.../mail/repository/MailRecordRepository.kt` | SQL CAST + MailboxRow 字段类型 Boolean→Long |
| 2 | `src/main/kotlin/.../mail/service/MailboxService.kt` | `row.hasAttachment != 0L` |
| 3 | `src/main/resources/static/app.js` | loadMailbox() 添加 7 天默认日期 |
| 4 | `src/test/kotlin/.../mail/service/MailboxServiceTest.kt` | mock 数据 Boolean→Long |

共 4 个文件，2 个子系统（后端类型修复 + 前端默认值），均在限制内。

## 验收标准

- **I-1**：启动应用后访问收发件箱页面，API `/api/mail/mailbox` 正常返回 JSON（`hasAttachment` 为 boolean），无 500 错误。`mvn test` 中 `MailboxServiceTest` 全部通过。
- **I-2**：首次打开收发件箱页面时，日期筛选器自动填入近 7 天范围（startDate = 今天-7，endDate = 今天）。手动清空日期后点击查询，不会被强制覆盖。
- **集成**：页面正常显示邮件列表，附件列显示正确的📎图标，分页功能正常。
