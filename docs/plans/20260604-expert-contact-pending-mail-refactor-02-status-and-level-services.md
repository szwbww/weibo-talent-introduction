# Phase 2：专家运营状态和层级统一服务/API

> 目标：把专家状态变更、专家层级变更、自动/人工回复切换统一收口到后端服务，并写操作日志。

## 1. 前置依赖

必须先完成 Phase 1：

- `expert_contact.operator_status` 已存在。
- `OperatorStatus` 已存在。
- `OperatorActionLogService` 已存在。
- `/api/operator-action-logs` 可查询。

执行前检查：

```bash
rg -n "operatorStatus|OperatorActionLogService|OperatorStatus" src/main/kotlin
sed -n '1,260p' src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt
sed -n '1,240p' src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt
```

## 2. 修改列表查询

文件：

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt
src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt
```

### 2.1 Repository

扩展 `findFilteredContacts` 增加 `operatorStatus`：

```sql
AND (:operatorStatus IS NULL OR operator_status = :operatorStatus)
```

保留现有 `status` 参数一段时间，避免已有页面或监控调用断掉。推荐新增参数，不删除旧参数：

```kotlin
fun findFilteredContacts(
    campaignId: Long?,
    status: String?,
    operatorStatus: String?,
    needsAttention: Boolean?
): List<ExpertContact>
```

### 2.2 Service

`listContacts(...)` 增加 `operatorStatus` 入参。

如果同时传 `status` 和 `operatorStatus`，都要生效；这样兼容老筛选和新筛选。

### 2.3 Controller

`GET /api/expert-contacts` 增加：

```kotlin
@RequestParam(required = false) operatorStatus: String?
```

返回 `ExpertContactResponse` 增加：

```kotlin
val operatorStatus: String
```

## 3. 新增 ExpertOperatorStatusService

新增：

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt
```

职责：

- 校验 `OperatorStatus.fromName(...)`。
- 手动变更 `expert_contact.operatorStatus`。
- 写 `OperatorActionLogService`。
- 自动状态更新给 Phase 3 调用。

建议方法：

```kotlin
@Transactional
fun changeStatus(
    contactId: Long,
    targetStatus: String,
    operatorName: String?,
    note: String?
): ExpertContact

@Transactional
fun updateAutomatically(
    contact: ExpertContact,
    targetStatus: OperatorStatus,
    reason: String
): ExpertContact
```

手动变更日志：

- `targetType = "EXPERT_CONTACT"`
- `targetId = contactId`
- `expertContactId = contactId`
- `actionType = CHANGE_OPERATOR_STATUS`
- `before = mapOf("operatorStatus" to oldStatus)`
- `after = mapOf("operatorStatus" to newStatus)`

自动状态更新是否写日志：

- 建议不写 `operator_action_log`，因为它是人工操作日志。
- 可在 `expert_contact_status_history` 或业务日志中体现。
- 如果产品要求所有自动也查到，另开 `SYSTEM_ACTION_LOG`，不要混入本阶段。

## 4. 新增统一层级变更服务

新增：

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt
```

职责：

- 统一封装 RAW/CANDIDATE/APPLICATION 变更。
- 复用现有 `ExpertContactManagementService` 内的 promotion/demotion 逻辑，或把相关逻辑迁移到本服务。
- 写操作日志。

建议签名：

```kotlin
@Transactional
fun changeLevel(
    contactId: Long,
    targetLevel: String,
    operatorName: String?,
    note: String?
): ExpertContact
```

规则：

| 当前层级 | 目标层级 | 行为 |
| --- | --- | --- |
| `RAW` | `RAW` | 直接返回，不写 ES |
| `RAW` | `CANDIDATE` | 调用 `ExpertIndexWriterService.promoteToCandidate`，保存 `currentIndexLevel='CANDIDATE'` |
| `RAW` | `APPLICATION` | 允许直接进有效层：先确保候选投影需要时可跳过，调用 `promoteToApplication`，保存 `applicationIndexed=true,currentIndexLevel='APPLICATION'` |
| `CANDIDATE` | `RAW` | 调用 `demoteToRaw` |
| `CANDIDATE` | `APPLICATION` | 调用 `promoteToApplication` |
| `APPLICATION` | `RAW` | 调用 `demoteToRaw`，保存 `applicationIndexed=false,currentIndexLevel='RAW'` |
| `APPLICATION` | `CANDIDATE` | 不建议第一版支持；如果必须支持，需要从 application index 删除但保留 candidate index。否则返回 400：`APPLICATION can only be demoted to RAW` |

日志：

- `actionType = CHANGE_INDEX_LEVEL`
- before/after 记录 `currentIndexLevel` 和 `applicationIndexed`。

注意：

- 不要破坏现有 `/promote-to-application`、`/promote-to-candidate`、`/demote-to-raw` API；可以保留并内部调用新服务，便于兼容旧前端。
- `currentIndexLevel == "APPLICATION"` 必须和 `applicationIndexed == true` 保持一致。

## 5. Controller API

在 `ExpertContactManagementController` 新增：

```http
POST /api/expert-contacts/{contactId}/operator-status
POST /api/expert-contacts/{contactId}/index-level
```

请求 DTO：

```kotlin
data class ChangeOperatorStatusRequest(
    val operatorStatus: String,
    val operatorName: String?,
    val note: String?
)

data class ChangeIndexLevelRequest(
    val targetLevel: String,
    val operatorName: String?,
    val note: String?
)
```

响应复用 `ExpertContactResponse`。

`ExpertContactResponse` 必须包含：

- `operatorStatus`
- `currentIndexLevel`
- `autoReplyEnabled`
- `manualHandoffRequired`
- `needsManualAttention`

## 6. 给自动/人工回复切换补日志

修改：

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt
```

方法：

- `switchToManual(contactId, reason, note)`
- `switchToAuto(contactId, note)`
- `pauseAutoReply(contactId)`
- `resumeAutoReply(contactId)`

要求：

- 注入 `OperatorActionLogService`。
- 在操作成功后记录日志。
- 因现有请求没有 `operatorName`，本阶段建议把 controller DTO 扩展为：

```kotlin
data class SwitchToManualRequest(
    val reason: String?,
    val note: String?,
    val operatorName: String? = null
)

data class SwitchToAutoRequest(
    val note: String?,
    val operatorName: String? = null
)
```

Service 方法同步加 `operatorName`，旧调用传 null。

日志：

- `actionType = SWITCH_REPLY_MODE`
- before/after 记录：
  - `currentStatus`
  - `autoReplyEnabled`
  - `manualHandoffRequired`
  - `needsManualAttention`

## 7. 测试

新增或修改：

```text
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementServiceTest.kt
```

最低覆盖：

- 6 个合法 `OperatorStatus` 可变更。
- 非法状态抛错。
- 手动变更状态写日志。
- `CANDIDATE -> APPLICATION` 调用 ES promotion，保存 `applicationIndexed=true,currentIndexLevel='APPLICATION'`。
- `APPLICATION -> RAW` 调用 ES demotion，保存 `applicationIndexed=false,currentIndexLevel='RAW'`。
- 切换人工回复写 `SWITCH_REPLY_MODE` 日志。
- 切换自动回复写 `SWITCH_REPLY_MODE` 日志。

## 8. 验证命令

```bash
node --check src/main/resources/static/app.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

## 9. 验收标准

- `/api/expert-contacts` 支持 `operatorStatus` 筛选。
- `/api/expert-contacts/{id}/operator-status` 可以手动改状态。
- `/api/expert-contacts/{id}/index-level` 可以手动改层级。
- 自动/人工回复切换仍可用。
- 上述人工操作都能在 `/api/operator-action-logs` 查到。

## 10. 禁止事项

- 不要删除旧的 promote/demote API。
- 不要让前端直接写 `currentStatus` 来模拟 6 个页面状态。
- 不要在层级变更失败时仍保存 MySQL 状态；ES 和 MySQL 要么一起成功，要么抛错。
