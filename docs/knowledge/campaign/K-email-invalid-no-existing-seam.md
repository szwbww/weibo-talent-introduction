---
id: K-email-invalid-no-existing-seam
domain: campaign
created: 2026-08-18
last_used: 2026-08-18
hit_count: 0
source: create-p:bounce-dsn-classification-and-email-invalid-writeback
---
经验：`EMAIL_INVALID` 是 `OperatorStatus` **枚举外**的旁路终态，
`ExpertOperatorStatusService` 现有两个出口**都无法表达它**：

- `updateAutomatically(contact, targetStatus: OperatorStatus, reason)` —— 形参是枚举类型，
  字面无法传入 EMAIL_INVALID。
- `changeStatus(contactId, targetStatus: String, …)` —— 内部 `OperatorStatus.fromName()`
  对 EMAIL_INVALID 直接 `error("Unsupported operator status")`；且它会写
  `OperatorActionType.CHANGE_OPERATOR_STATUS` 审计，而该审计是对账作业的
  **人工覆盖判别器**（`OperatorStatusReconcileService:60-65` 据此单列 `HUMAN_OVERRIDE`
  且不计入异常）——自动路径写它会让退信标记被误分类。

正确做法：新增标记 EMAIL_INVALID 的能力时，**把方法加进 `ExpertOperatorStatusService.kt`**，
而不是在调用方直接 `contact.copy(operatorStatus = ...)`——后者会让
`OperatorStatusWriteSeamGuardTest` 的「命中文件集合恰好等于 ALLOWED_WRITE_SITES」断言失败。
写在服务内则白名单闭包天然保持不变，无需登记新文件。

两条必守语义：
- **不回退**：当前状态 ordinal ≥ `REPLIED`（REPLIED / MATERIALS_RECEIVED / INVITED /
  COMPLETED）时必须零交互返回。专家已回信即证明地址可达；且因
  `updateAutomatically():53` 对 EMAIL_INVALID 无条件短路，一旦误标将**永久**无法自动恢复。
- **不写审计**：自动路径禁止写 CHANGE_OPERATOR_STATUS（对齐 `updateAutomatically` 全程不写审计）。

关联：K-operator-status-single-writer、K-operator-status-write-seam-guard、K-operator-status-reconcile。
