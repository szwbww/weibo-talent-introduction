---
id: K-operator-status-reconcile
domain: campaign
created: 2026-08-13
last_used: 2026-08-18
hit_count: 1
source: fast-p:batch-send-status-consistency:04
severity: P1
---
经验：operator_status 对账作业（`POST /api/experts/reconcile-operator-status`，手动；
`talent-introduction.scheduling.operator-status-reconcile-cron`，默认 `"-"` 关闭；taskType `OPERATOR_STATUS_RECONCILE`）
**首版只读不报告不自动修**（I-1）：`OperatorStatusReconcileService` 不注入任何 writer，
只从 `mail_record` / `bounce_record` / `mail_attachment` 反推期望状态，与 DB 实际值、ES 三层实际值三方比对，
报告落入 `task_execution.result_summary`（`runAndRecordWithResult`）。

## 期望值映射（I-4，逐条对应既有自动推进实现，不得自创）
| 期望状态 | 事件判据 | 出处 |
|---|---|---|
| `CONTACTED` | OUTBOUND + `INTRODUCTION` + `SENT` 的 mail_record | `ManualInitialOutreachService.hasSentIntroduction():895` 逐字 |
| `INVITED` | OUTBOUND + `MEETING_INVITATION` + `SENT` | `AutoMailReplyService:484,816` |
| `REPLIED` | 存在 INBOUND mail_record | `AutoMailReplyService:802` |
| `MATERIALS_RECEIVED` | INBOUND 邮件有材料附件（`mail_attachment.mail_record_id` 指向 INBOUND 记录） | `AutomaticApplicationPromotionService:50,57`；附件落库 `MailAttachmentService.saveInboundAttachments` |
| `EMAIL_INVALID` | HARD 退信记录（`bounce_record.bounce_type='HARD'`）或首封外发 `PERMANENT:` 失败（mail_record FAILED + errorSummary 前缀） | `BounceCollectionService:105-107`；`ManualInitialOutreachService:697,706` |
| `COMPLETED` | —— 不可派生（I-3） | —— |

多条判据同时成立取最大 ordinal（与 `updateAutomatically` 单调不回退一致）。EMAIL_INVALID 是旁路终态，优先于一切枚举推进。

## 分类规则
- **I-2 人工覆盖**：存在 `action_type='CHANGE_OPERATOR_STATUS'` 的 `operator_action_log`（`changeStatus` 写审计、`updateAutomatically` 不写）→ 单列 `HUMAN_OVERRIDE`，不计入异常；样本仍展示三方取值。
- **I-3 COMPLETED**：人工终态，不参与期望值差异判定；但 ES 与 DB 的**事实**比对仍参与（与期望值无关）。
- 报告四类互斥且总和=总数：一致 / DB 与期望不符 / ES 与 DB 不符 / 人工覆盖；每类差异前 20 条样本（contactId + orcid + 期望/DB/ES 三方取值）。

## ES 侧读取约定
- 三层（RAW/CANDIDATE/APPLICATION）各按 500 条一批 `terms` 查询（与 `syncOperatorStatusBatch` 同款分批）；
  值优先级 CANDIDATE > APPLICATION > RAW；文档存在但 `operatorStatus` 字段缺失视为 `NOT_CONTACTED`
  （`syncOperatorStatus` 对 NOT_CONTACTED 走字段移除脚本）。
- 对账只能发 `_search`；任何 `_update`/`_bulk`/`_doc` 写端点都违反 I-1。

## 关键坑
- 全表扫描 + 内存比对（2062 行/2157 行量级），不要为此引入分页/流式。
- `OperatorActionLogRepository.findContactIdsWithChangeOperatorStatusLogs` 入参必须非空（`IN ()` 非法）。
- 对账判定逻辑一旦放开写权限即可能批量污染数据；改「自动修」前必须先在报告阶段验证识别准确率。
