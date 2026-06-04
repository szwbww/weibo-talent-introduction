# Phase 1：数据模型和操作日志基础

> 目标：新增运营状态字段和统一操作日志表，提供日志写入与查询能力。此阶段不做页面大改，不改变自动收信业务判断。

## 1. 前置检查

执行前先确认：

```bash
git status --short
rg -n "operator_status|operatorStatus|operator_action_log|OperatorAction" src/main/kotlin src/main/resources/db/migration src/main/resources/static || true
ls src/main/resources/db/migration
```

如果已有同名字段或迁移，必须基于现状调整版本号和字段名，不要重复建表。

## 2. 数据库迁移

新增一个新的 Flyway 迁移，版本号按当前最大版本继续，例如：

```text
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql
```

SQL 文件必须完整包含以下内容，字段注释可以保留中文：

```sql
ALTER TABLE expert_contact
    ADD COLUMN operator_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONTACTED'
        COMMENT '运营视角专家状态: NOT_CONTACTED / CONTACTED / REPLIED / MATERIALS_RECEIVED / INVITED / COMPLETED',
    ADD INDEX idx_expert_contact_operator_status (operator_status, updated_at);

UPDATE expert_contact
   SET operator_status = CASE
       WHEN current_status IN ('MEETING_SCHEDULING', 'MEETING_SCHEDULED', 'MEETING_INVITATION_SENT', 'WAITING_MEETING_CONFIRMATION') THEN 'INVITED'
       WHEN current_status IN ('MATERIALS_PARTIAL', 'MATERIALS_RECEIVED') THEN 'MATERIALS_RECEIVED'
       WHEN last_reply_at IS NOT NULL THEN 'REPLIED'
       WHEN last_mail_at IS NOT NULL THEN 'CONTACTED'
       ELSE 'NOT_CONTACTED'
   END;

CREATE TABLE operator_action_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_type VARCHAR(64) NOT NULL COMMENT 'EXPERT_CONTACT / INBOUND_MAIL_PROCESSING / DOCUMENT',
    target_id BIGINT NOT NULL,
    expert_contact_id BIGINT NULL,
    inbound_processing_id BIGINT NULL,
    action_type VARCHAR(64) NOT NULL COMMENT 'CHANGE_OPERATOR_STATUS / CHANGE_INDEX_LEVEL / SWITCH_REPLY_MODE / BIND_INBOUND_MAIL / SEND_QA_REPLY / SEND_MANUAL_RICH_REPLY / MARK_INBOUND_RESOLVED',
    action_summary VARCHAR(255) NOT NULL,
    before_value TEXT NULL,
    after_value TEXT NULL,
    operator_name VARCHAR(128) NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_operator_action_contact_created (expert_contact_id, created_at),
    KEY idx_operator_action_inbound_created (inbound_processing_id, created_at),
    KEY idx_operator_action_type_created (action_type, created_at),
    KEY idx_operator_action_operator_created (operator_name, created_at),
    CONSTRAINT fk_operator_action_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
    CONSTRAINT fk_operator_action_inbound
        FOREIGN KEY (inbound_processing_id) REFERENCES inbound_mail_processing(id)
);
```

如果当前 Flyway 版本不是 V18 后接 V19，调整文件名版本号，但 SQL 内容仍要完整。

## 3. Kotlin 领域模型

### 3.1 修改 `ExpertContact`

文件：

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/ExpertContact.kt
```

新增字段，放在 `currentIndexLevel` 附近即可：

```kotlin
val operatorStatus: String = "NOT_CONTACTED",
```

注意 Spring Data JDBC 使用字段名映射 `operator_status`，字段名必须是 `operatorStatus`。

### 3.2 新增操作日志 domain

新增文件：

```text
src/main/kotlin/com/weibo/talentintroduction/audit/domain/OperatorActionLog.kt
```

建议内容：

```kotlin
package com.weibo.talentintroduction.audit.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("operator_action_log")
data class OperatorActionLog(
    @Id
    val id: Long? = null,
    val targetType: String,
    val targetId: Long,
    val expertContactId: Long? = null,
    val inboundProcessingId: Long? = null,
    val actionType: String,
    val actionSummary: String,
    val beforeValue: String? = null,
    val afterValue: String? = null,
    val operatorName: String? = null,
    val note: String? = null,
    val createdAt: LocalDateTime? = null
)
```

### 3.3 新增常量枚举

新增：

```text
src/main/kotlin/com/weibo/talentintroduction/audit/domain/OperatorActionType.kt
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/OperatorStatus.kt
```

`OperatorStatus`：

```kotlin
package com.weibo.talentintroduction.campaign.domain

enum class OperatorStatus {
    NOT_CONTACTED,
    CONTACTED,
    REPLIED,
    MATERIALS_RECEIVED,
    INVITED,
    COMPLETED;

    companion object {
        fun fromName(value: String): OperatorStatus =
            entries.firstOrNull { it.name == value }
                ?: error("Unsupported operator status: $value")
    }
}
```

`OperatorActionType`：

```kotlin
package com.weibo.talentintroduction.audit.domain

enum class OperatorActionType(val summary: String) {
    CHANGE_OPERATOR_STATUS("变更专家状态"),
    CHANGE_INDEX_LEVEL("变更专家层级"),
    SWITCH_REPLY_MODE("切换自动/人工回复"),
    BIND_INBOUND_MAIL("绑定待处理邮件"),
    SEND_QA_REPLY("发送 QA 邮件"),
    SEND_MANUAL_RICH_REPLY("人工回复邮件"),
    MARK_INBOUND_RESOLVED("标记待处理邮件已处理")
}
```

## 4. Repository

新增：

```text
src/main/kotlin/com/weibo/talentintroduction/audit/repository/OperatorActionLogRepository.kt
```

建议使用 Spring Data JDBC `CrudRepository` + `@Query`：

```kotlin
package com.weibo.talentintroduction.audit.repository

import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

interface OperatorActionLogRepository : CrudRepository<OperatorActionLog, Long> {
    @Query(
        """
        SELECT * FROM operator_action_log
        WHERE (:expertContactId IS NULL OR expert_contact_id = :expertContactId)
          AND (:inboundProcessingId IS NULL OR inbound_processing_id = :inboundProcessingId)
          AND (:actionType IS NULL OR action_type = :actionType)
          AND (:operatorName IS NULL OR operator_name LIKE CONCAT('%', :operatorName, '%'))
          AND (:start IS NULL OR created_at >= :start)
          AND (:end IS NULL OR created_at < :end)
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun search(
        expertContactId: Long?,
        inboundProcessingId: Long?,
        actionType: String?,
        operatorName: String?,
        start: LocalDateTime?,
        end: LocalDateTime?,
        limit: Int,
        offset: Int
    ): List<OperatorActionLog>

    @Query(
        """
        SELECT COUNT(*) FROM operator_action_log
        WHERE (:expertContactId IS NULL OR expert_contact_id = :expertContactId)
          AND (:inboundProcessingId IS NULL OR inbound_processing_id = :inboundProcessingId)
          AND (:actionType IS NULL OR action_type = :actionType)
          AND (:operatorName IS NULL OR operator_name LIKE CONCAT('%', :operatorName, '%'))
          AND (:start IS NULL OR created_at >= :start)
          AND (:end IS NULL OR created_at < :end)
        """
    )
    fun countSearch(
        expertContactId: Long?,
        inboundProcessingId: Long?,
        actionType: String?,
        operatorName: String?,
        start: LocalDateTime?,
        end: LocalDateTime?
    ): Long
}
```

## 5. Service

新增：

```text
src/main/kotlin/com/weibo/talentintroduction/audit/service/OperatorActionLogService.kt
```

职责：

- 提供 `record(...)` 写日志。
- 使用 Jackson `ObjectMapper` 序列化 before/after。
- 查询分页。
- 控制 `pageSize` 最大值，建议 100。

建议方法：

```kotlin
fun record(
    targetType: String,
    targetId: Long,
    actionType: OperatorActionType,
    expertContactId: Long? = null,
    inboundProcessingId: Long? = null,
    before: Any? = null,
    after: Any? = null,
    operatorName: String? = null,
    note: String? = null,
    summaryOverride: String? = null
): OperatorActionLog
```

序列化失败不要吞掉，应该让测试暴露；但 before/after 为 null 时存 null。

## 6. Controller

新增：

```text
src/main/kotlin/com/weibo/talentintroduction/audit/controller/OperatorActionLogController.kt
```

接口：

```http
GET /api/operator-action-logs?expertContactId=&inboundProcessingId=&actionType=&operatorName=&start=&end=&pageSize=&pageOffset=
```

响应：

```json
{
  "records": [
    {
      "id": 1,
      "targetType": "EXPERT_CONTACT",
      "targetId": 11,
      "expertContactId": 11,
      "inboundProcessingId": null,
      "actionType": "CHANGE_OPERATOR_STATUS",
      "actionSummary": "变更专家状态",
      "beforeValue": "{\"operatorStatus\":\"CONTACTED\"}",
      "afterValue": "{\"operatorStatus\":\"REPLIED\"}",
      "operatorName": "operator",
      "note": "人工确认",
      "createdAt": "2026-06-04T12:00:00"
    }
  ],
  "totalCount": 1
}
```

`start`、`end` 可先用 `LocalDateTime.parse` 处理 ISO 字符串。

## 7. 测试

新增测试建议：

```text
src/test/kotlin/com/weibo/talentintroduction/audit/service/OperatorActionLogServiceTest.kt
```

最低覆盖：

- `record` 能写入 target、actionType、operatorName、note。
- before/after 对象被序列化成 JSON。
- pageSize 超过上限时被限制。

如果当前测试风格更偏 Mockito 单测，按现有风格 mock repository 和 ObjectMapper。

## 8. 验证命令

```bash
node --check src/main/resources/static/app.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

## 9. 验收标准

- `expert_contact` domain 有 `operatorStatus` 字段。
- 新迁移完整创建 `operator_status` 和 `operator_action_log`。
- 后端能查询操作日志。
- 还没有任何页面依赖新日志时，原页面功能不受影响。
- `mvn test` 通过，或记录明确的环境阻塞原因。

## 10. 禁止事项

- 不要在本阶段改自动收信流转逻辑。
- 不要在本阶段大改 `app.js` 页面结构。
- 不要把 `operator_action_log.before_value` / `after_value` 设计成过短字段，JSON 可能较长，使用 `TEXT`。
