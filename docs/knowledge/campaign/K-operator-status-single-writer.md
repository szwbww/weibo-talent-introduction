---
id: K-operator-status-single-writer
domain: campaign
created: 2026-09-24
last_used: 2026-09-24
hit_count: 3
source: create-p:operator-status-single-writer
---
经验：`expert_contact.operator_status` 的**正常枚举自动推进**集中入口是`ExpertOperatorStatusService.updateAutomatically`（`campaign.service`）。任何"发送成功要改状态"的路径（手动发信 `ManualExpertMailService.sendManualMail`、批量 `ManualOutreachTxHelper.recordSuccess`）都必须在 `ConversationStateService.transition(...)` **之后**调用它，并传入 transition 的返回值（含最新 `lastMailAt`），禁止自持字符串实现或直接 `expertIndexWriterService.syncCandidateOperatorStatus`。顺序不可颠倒：transition 内部用旧快照 save，先调用会被覆盖回去（Spring Data JDBC 无实体跟踪）。

推论：
- **I-1 单调不回退**：`updateAutomatically` 只沿 `OperatorStatus` ordinal 正向推进；目标 ordinal ≤ 当前时返回入参、零 DB/ES 交互。给 REPLIED/INVITED 专家手动发信不会被打回 CONTACTED 重新进待发送池。
- **I-2 EMAIL_INVALID 旁路终态**：`contact.operatorStatus == "EMAIL_INVALID"` 无条件短路。该值**不进**枚举（前端 `operatorStatusLabels` 仅 6 键），所有 ordinal 保护对它落空，必须单独短路。
- **I-4 mailType 白名单**：手动发信仅 `INTRODUCTION → CONTACTED`、`MEETING_INVITATION → INVITED` 推进；`MATERIAL_REMINDER` / `COMPOSE_TEMPLATE` 返回 null 零调用。
- **I-5 ES 语义**：ES 侧"未联系" = `operatorStatus` 字段缺失（`ExpertIndexWriterService:76` 的 `ctx._source.remove('operatorStatus')`），禁止写成字符串。
- 历史“恰4处”计数过时。当前已存在 ExpertOperatorStatusService.markEmailInvalid:78/88；ManualInitialOutreachService:680初始化、:777永久SMTP失败直接写仍存在。新增自动写路径须逐一审计，不把updateAutomatically说成所有特殊状态的唯一写方。`operator_action_log.CHANGE_OPERATOR_STATUS` 仍是人工覆盖判别器。

关联：批量发送目标集按构造必为 `NOT_CONTACTED`（重试路径筛 `currentStatus == "NEW"` 且非 EMAIL_INVALID、ES 路径筛 `must_not exists operatorStatus`），故 I-1 单调判断对批量路径恒真，行为等价。存量修复用 SQL migration 回补（V94，条件与 `ManualInitialOutreachService.hasSentIntroduction():895` 逐字一致，幂等）。

2026-09-24补充：用户要求追加tags值与本条operator_status合同不同，不应自动扩展为状态迁移。
