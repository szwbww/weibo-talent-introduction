---
id: K-operator-status-single-writer
domain: campaign
created: 2026-08-13
last_used: 2026-08-13
hit_count: 0
source: create-p:operator-status-single-writer
---
经验：`expert_contact.operator_status` 的**自动**写入全仓只有 1 个出口——`ExpertOperatorStatusService.updateAutomatically`（`campaign.service`）。任何"发送成功要改状态"的路径（手动发信 `ManualExpertMailService.sendManualMail`、批量 `ManualOutreachTxHelper.recordSuccess`）都必须在 `ConversationStateService.transition(...)` **之后**调用它，并传入 transition 的返回值（含最新 `lastMailAt`），禁止自持字符串实现或直接 `expertIndexWriterService.syncCandidateOperatorStatus`。顺序不可颠倒：transition 内部用旧快照 save，先调用会被覆盖回去（Spring Data JDBC 无实体跟踪）。

推论：
- **I-1 单调不回退**：`updateAutomatically` 只沿 `OperatorStatus` ordinal 正向推进；目标 ordinal ≤ 当前时返回入参、零 DB/ES 交互。给 REPLIED/INVITED 专家手动发信不会被打回 CONTACTED 重新进待发送池。
- **I-2 EMAIL_INVALID 旁路终态**：`contact.operatorStatus == "EMAIL_INVALID"` 无条件短路。该值**不进**枚举（前端 `operatorStatusLabels` 仅 6 键），所有 ordinal 保护对它落空，必须单独短路。
- **I-4 mailType 白名单**：手动发信仅 `INTRODUCTION → CONTACTED`、`MEETING_INVITATION → INVITED` 推进；`MATERIAL_REMINDER` / `COMPOSE_TEMPLATE` 返回 null 零调用。
- **I-5 ES 语义**：ES 侧"未联系" = `operatorStatus` 字段缺失（`ExpertIndexWriterService:76` 的 `ctx._source.remove('operatorStatus')`），禁止写成字符串。
- 全仓 `operatorStatus = ` DB 写入点恰 4 处：`changeStatus:30`（人工，写日志）、`updateAutomatically:61`（自动，不写日志）、`ManualInitialOutreachService:611`（建行初始化 NOT_CONTACTED）、`:706`（退信标记 EMAIL_INVALID）。`operator_action_log.CHANGE_OPERATOR_STATUS` 是人工覆盖判别器。

关联：批量发送目标集按构造必为 `NOT_CONTACTED`（重试路径筛 `currentStatus == "NEW"` 且非 EMAIL_INVALID、ES 路径筛 `must_not exists operatorStatus`），故 I-1 单调判断对批量路径恒真，行为等价。存量修复用 SQL migration 回补（V94，条件与 `ManualInitialOutreachService.hasSentIntroduction():895` 逐字一致，幂等）。
