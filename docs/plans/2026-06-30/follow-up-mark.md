# 待跟进标记功能

## 需求描述

**可观测结果**：运营在专家详情页可以标记/取消"待跟进"，列表页可按"待跟进"筛选并按标记时间排序，方便定期人工催促。

**不可变更的现有行为**：
- 状态机不受影响（不新增状态）
- 现有列表的 ES 路径 / DB 路径切换逻辑不变
- `autoReplyEnabled`、`needsManualAttention` 语义不变

**不在范围内**：
- 自动定时发送提醒邮件（后续独立计划）
- 提醒邮件模板管理
- 前端批量标记

---

## 关键不变量

### Invariant I-1: followUpMarked 是纯标记字段，不影响任何自动化流程
- Rule: `followUpMarked` 字段仅用于运营筛选和展示，不作为 `AutoMailReplyService`、`ConversationStateService` 或任何自动流程的条件判断
- Applies to: 所有 `expertContactRepository.save(contact.copy(...))` 路径
- Violation consequence: 标记/取消标记可能意外阻断自动回复或改变状态机行为
- 来源: original

### Invariant I-2: followUpMarked 筛选强制走 DB 路径
- Rule: 当 `followUpMarked=true` 作为筛选条件时，必须走 MySQL `/api/expert-contacts` 路径（与 `needsAttention`、`replyMode` 同组），禁用 ES-only 的标签/地区筛选
- Applies to: 前端 `loadContacts()`, Controller `listContacts`, Repository `findFilteredContacts`
- Violation consequence: ES 不持有此字段，走 ES 路径会静默返回全量无过滤结果
- 来源: K-contact-list-dual-query-path

### Invariant I-3: copy(...) 路径自动保留 followUpMarked
- Rule: `ExpertContact` 的 `followUpMarked` 字段默认值为 `false`，`followUpMarkedAt` 默认值为 `null`；所有 `contact.copy(...)` 不显式传递这两个字段时自动保留原值
- Applies to: 所有 `contact.copy(...)` 调用点（不需要逐处修改）
- Violation consequence: 无（data class copy 天然满足）
- 来源: K-expert-contact-two-write-sites

---

## 现状审计

### expert_contact 表 (MySQL)

- Schema: 见 `ExpertContact.kt` — 28 个字段，当前最大迁移 V50
- Write paths (构造):
  1. `InitialOutreachService.kt` — 自动首发创建
  2. `ManualInitialOutreachService.kt` — 手动/调度批量创建
- Write paths (更新 via copy):
  1. `ConversationStateService.transition()` — 状态转换
  2. `ExpertContactManagementService` — pauseAutoReply, resumeAutoReply, switchToManual, switchToAuto, promoteToApplication, promoteToCandidate, demoteToRaw
  3. `AutoMailReplyService` — 收信后更新 lastReplyAt/firstReplyAt
  4. `ExpertOperatorStatusService` — 更新 operatorStatus
  5. `ContactCountryBackfillService` — 回填 country
- Read paths (列表):
  1. `ExpertContactRepository.findFilteredContacts(...)` — DB 路径
  2. ES 路径 (不持有 MySQL-only 字段)
- Interaction points:
  - 新增 `followUpMarked` 筛选 → 必须并入 DB 路径触发条件（同 `needsAttention`/`replyMode`）

### 前端 loadContacts() (app.js)

- 双路径: `needsAttention || replyMode` → DB 路径，否则 ES 路径
- DB 路径走 `/api/expert-contacts?params`
- 新增 `followUpMarked` 筛选时需并入 DB 路径条件

---

## 实现方案

### Phase 1: 数据层

**Task 1.1: DB Migration (V51)**
- 文件: `src/main/resources/db/migration/V51__add_follow_up_marked.sql`
- 内容: `ALTER TABLE expert_contact ADD COLUMN follow_up_marked BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN follow_up_marked_at DATETIME NULL;`
- 遵循: I-3 (默认值 false/null)

**Task 1.2: Domain 对象**
- 文件: `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/ExpertContact.kt`
- 添加: `val followUpMarked: Boolean = false`, `val followUpMarkedAt: LocalDateTime? = null`
- 遵循: I-1, I-3

### Phase 2: 查询层

**Task 2.1: Repository 过滤**
- 文件: `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt`
- 修改 `findFilteredContacts` SQL 添加: `AND (:followUpMarked IS NULL OR follow_up_marked = :followUpMarked)`
- 方法签名添加 `followUpMarked: Boolean? = null`
- 遵循: I-2

**Task 2.2: Service 透传**
- 文件: `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt`
- `listContacts` 方法添加 `followUpMarked: Boolean? = null` 参数并透传
- 添加 `markFollowUp(contactId: Long)` 和 `unmarkFollowUp(contactId: Long)` 方法
- 遵循: I-1 (标记/取消仅修改这两个字段，不触发状态转换)

### Phase 3: API 层

**Task 3.1: Controller**
- 文件: `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt`
- `listContacts` 添加 `@RequestParam(required = false) followUpMarked: Boolean?`
- 新增 `POST /{contactId}/mark-follow-up` endpoint
- 新增 `DELETE /{contactId}/mark-follow-up` endpoint (或 `POST /{contactId}/unmark-follow-up`)
- Response DTO `ExpertContactResponse` 添加 `followUpMarked: Boolean`, `followUpMarkedAt: String?`
- 遵循: I-2

### Phase 4: 前端

**Task 4.1: 筛选**
- 文件: `src/main/resources/static/app.js`
- 在筛选栏添加"待跟进"下拉选项
- 将 `followUpMarked` 并入 `needsAttention || replyMode || followUpMarked` 的 DB 路径条件
- 遵循: I-2

**Task 4.2: 列表展示**
- 文件: `src/main/resources/static/app.js`
- 列表项中 `followUpMarked=true` 的显示一个小标记图标
- 遵循: I-1 (纯展示)

**Task 4.3: 详情页按钮**
- 文件: `src/main/resources/static/app.js`
- 详情页添加"标记待跟进" / "取消待跟进" toggle 按钮
- 调用 `POST /api/expert-contacts/{id}/mark-follow-up` 或 unmark
- 遵循: I-1

### Phase 5: 测试

**Task 5.1: Service 单测**
- 文件: `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementServiceTest.kt`
- 测试 markFollowUp / unmarkFollowUp 只修改标记字段
- 测试 listContacts followUpMarked 参数透传

**Task 5.2: Controller 单测**
- 文件: `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt`
- 测试新 endpoint

---

## 变更文件清单

| # | 文件 | 变更类型 |
|---|------|---------|
| 1 | `src/main/resources/db/migration/V51__add_follow_up_marked.sql` | 新增 |
| 2 | `src/main/kotlin/.../campaign/domain/ExpertContact.kt` | 修改 |
| 3 | `src/main/kotlin/.../campaign/repository/ExpertContactRepository.kt` | 修改 |
| 4 | `src/main/kotlin/.../campaign/service/ExpertContactManagementService.kt` | 修改 |
| 5 | `src/main/kotlin/.../campaign/controller/ExpertContactManagementController.kt` | 修改 |
| 6 | `src/main/resources/static/app.js` | 修改 |
| 7 | `src/test/kotlin/.../campaign/service/ExpertContactManagementServiceTest.kt` | 修改 |
| 8 | `src/test/kotlin/.../campaign/controller/ExpertContactManagementControllerTest.kt` | 修改 |

共 8 个文件，≤ 10 限制内。

---

## 验收标准

- **I-1**: `markFollowUp` / `unmarkFollowUp` 只调用 `expertContactRepository.save(contact.copy(followUpMarked=..., followUpMarkedAt=...))` — 不调用 `conversationStateService.transition`，不修改任何其他字段
- **I-2**: 前端 `loadContacts` 当 `followUpMarked` 筛选启用时走 DB 路径（`/api/expert-contacts?followUpMarked=true`），ES 路径不传此参数
- **I-3**: 现有测试全部通过，domain 对象新字段有默认值，不影响任何既有 `copy(...)` 调用
- **集成场景**: 标记一个专家待跟进 → 列表筛选"待跟进"能看到 → 取消标记后筛选不再出现 → 期间自动回复等流程正常不受影响
