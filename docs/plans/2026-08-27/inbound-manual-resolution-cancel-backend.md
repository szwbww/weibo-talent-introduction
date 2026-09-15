# 收件“取消处理”后端计划

> 顺序计划 1/2。先交付可独立调用、可独立验证的取消处理 API；前端按钮见 `inbound-manual-resolution-cancel-frontend.md`。后端先部署，前端后部署。

## 需求描述

### 可观察结果

1. 对一条当前确为人工标记已处理的收件记录，调用 `POST /api/mail/unmatched-inbound/{id}/cancel-resolved` 后，记录重新进入人工待处理队列，并可再次执行现有“标记已处理”流程。
2. 取消处理写入独立操作日志；有关联专家时，该专家重新显示“需要人工处理”。
3. 缺失记录返回 HTTP 404；非人工处理完成态、重复取消或并发输掉条件更新的请求返回 HTTP 409，且不产生部分写入。

### 必须保持不变

- 不删除 `inbound_mail_processing` 行，不改 `sender_account_code`、`imap_uid`、邮件正文、标签或附件关联。
- 不调用 IMAP `markSeen`，不改变邮件服务器 Seen 状态；数据库行继续作为处理状态真相源。（来源: K-inbound-seen-not-processed-marker）
- 不允许取消 `MANUAL_BOUND`、自动处理完成或任意非 `PROCESSED/MANUAL_RESOLVED/MANUAL_RESOLVED` 记录。
- 不修改专家的 `currentStatus`、`operatorStatus`、`currentIndexLevel`、`autoReplyEnabled`、`manualHandoffRequired`。
- 不改变现有 `markResolved`、`bindToContact`、自动收件建档逻辑。

### 明确不做

- 本计划不改 `app.js`、`index.html` 或 CSS；按钮接入由顺序计划 2 完成。
- 不从历史日志推测或恢复原 `processReason/reasonType`。取消后的原因是新的、明确的“重新打开”事实。
- 不新增数据库列、不执行历史数据回填、不恢复 IMAP 未读状态。
- 不增加权限模型、批量取消、取消绑定、自动恢复自动回复。

## 关键不变量

### Invariant I-1: 严格且并发安全的可取消条件

- Rule: 只有同一行同时满足 `process_status='PROCESSED'`、`process_reason='MANUAL_RESOLVED'`、`reason_type='MANUAL_RESOLVED'` 才能取消。条件必须同时存在于服务前置校验和单条条件 `UPDATE` 的 `WHERE` 子句；更新计数不是 1 时抛 HTTP 409。
- Applies to: `PendingMailOperationService.cancelResolved`、`InboundMailProcessingRepository.reopenManualResolved`。
- Violation consequence: 自动处理、人工绑定记录被误开，或并发请求产生重复日志/重复副作用。
- 来源: original；当前正向写入三元组见 `PendingMailOperationService.kt:960-968`，绑定写入不同原因见 `UnmatchedInboundMailService.kt:180-188`。

### Invariant I-2: 取消写入新的确定事实，不猜历史原因

- Rule: 成功取消后字段必须精确为：`process_status='MANUAL_REVIEW'`、`process_reason='MANUAL_REOPENED'`、`reason_type=NULL`、`resolved_at=NULL`、`resolved_by=NULL`，并更新 `updated_at`。禁止从缺失日志或当前 `MANUAL_RESOLVED` 值推测原原因。
- Applies to: `InboundMailProcessingRepository.reopenManualResolved`，以及所有读取这五个字段的队列、邮箱、监控和详情路径。
- Violation consequence: 队列原因被伪造，或记录仍携带“已解决人/时间”而与待处理状态冲突。
- 来源: original；`process_reason` 非空、`reason_type` 可空见 `V5__create_inbound_mail_processing.sql:9-10`、`V14__contact_index_level_and_reason_type_and_qa_display_name.sql:36-40`；旧 `UnmatchedInboundMailService.markResolved` 未写操作日志见 `UnmatchedInboundMailService.kt:214-240`，因此不能假定历史快照存在。

### Invariant I-3: 专家人工关注与待处理记录一致

- Rule: 取消成功且 `expert_contact_id` 非空时，只把该专家的 `needsManualAttention` 置为 `true`；已为 `true` 时不重复保存；其他专家字段逐值保持原值。无关联专家时不写 `expert_contact`。
- Applies to: `PendingMailOperationService.cancelResolved`。
- Violation consequence: 待处理收件存在但专家列表无红条，或取消邮件意外改变专家业务状态。
- 来源: K-inbound-processing-write-paths；字段是红条/badge 依据见 `V14__contact_index_level_and_reason_type_and_qa_display_name.sql:6-8`，自动转人工也写 `true` 见 `AutoMailReplyService.kt:201-206,882-903`。

### Invariant I-4: 状态、专家标记、审计日志同事务

- Rule: 条件更新成功后写一条且仅一条 `CANCEL_INBOUND_RESOLVED` 日志；`before` 记录旧三元组和旧 `resolvedBy/resolvedAt`，`after` 记录 I-2 的精确值；日志的 `targetType` 为 `INBOUND_MAIL_PROCESSING`，两个 ID 均为入站处理 ID，`expertContactId` 原样传递。任一后续写失败时整个事务回滚。
- Applies to: `OperatorActionType`、`PendingMailOperationService.cancelResolved`、`OperatorActionLogService.record` 既有写入口。
- Violation consequence: 状态已取消但无审计，或重复请求写出多条取消日志。
- 来源: original；现有正向日志字段结构见 `PendingMailOperationService.kt:985-1003`，统一日志落库见 `OperatorActionLogService.kt:19-45`。

### Invariant I-5: 收件身份和 Seen 状态不可变

- Rule: 取消只更新 I-2 指定的六列；不得删除行、改唯一键 `(sender_account_code, imap_uid)`、调用 `MailReceiveService.markSeen` 或触碰附件/标签表。
- Applies to: 新增条件更新 SQL 与服务方法。
- Violation consequence: 同一邮件被重复拉取/重复建档，或取消业务处理被误解释为“未读”。
- 来源: K-inbound-seen-not-processed-marker；唯一键见 `V5__create_inbound_mail_processing.sql:16`，收件建档后单独 `markSeen` 见 `AutoMailReplyService.kt:1046-1077,1093-1125`。

## 现状审计

### Store: `inbound_mail_processing`

- Schema/mapping:
  - 主表定义：`process_status VARCHAR(32) NOT NULL`、`process_reason VARCHAR(64) NOT NULL`，唯一键 `(sender_account_code, imap_uid)`，状态/收件时间索引见 `V5__create_inbound_mail_processing.sql:1-20`。
  - `resolved_at`、`resolved_by` 明确可空，见 `V10__create_expert_email_alias_and_extend_unmatched_mail.sql:112-120`。
  - `reason_type VARCHAR(32) NULL` 且有 `(reason_type, process_status)` 索引，见 `V14__contact_index_level_and_reason_type_and_qa_display_name.sql:36-50`；V15 仍保持可空，见 `V15__add_mail_monitoring_columns_and_promotion_audit.sql:17-25`。
  - Kotlin 映射完整包含本计划字段，见 `InboundMailProcessing.kt:7-29`。
- 全部生产写路径（grep 证据：`rg "inboundMailProcessingRepository\\.save" src/main/kotlin`）：
  1. `AutoMailReplyService.confirmManualReviewWithBody` — 新建 `MANUAL_REVIEW` 行，见 `AutoMailReplyService.kt:1046-1077`。
  2. `AutoMailReplyService.confirmProcessed` — 新建通用处理结果行，见 `AutoMailReplyService.kt:1093-1125`。
  3. `UnmatchedInboundMailService.bindToContact` — 把未匹配收件写为 `PROCESSED/MANUAL_BOUND`，见 `UnmatchedInboundMailService.kt:134-212`。
  4. `UnmatchedInboundMailService.markResolved` — 旧服务路径写 `PROCESSED/MANUAL_RESOLVED/MANUAL_RESOLVED`，但不写操作日志，见 `UnmatchedInboundMailService.kt:214-240`。
  5. `PendingMailOperationService.markResolved` — 当前控制器调用的人工处理完成路径，写相同三元组及解决人/时间，见 `PendingMailOperationService.kt:947-1004`。（来源: K-inbound-processing-write-paths）
  6. 迁移写入：V14 对历史人工记录回填 `reason_type`，V15 对历史 processed/null 回填 `AUTO_NOOP`，见 `V14...sql:42-50`、`V15...sql:22-25`。
  7. 本计划新增：`InboundMailProcessingRepository.reopenManualResolved` 条件 `UPDATE`；这是唯一取消写路径。
- 全部生产读路径：
  1. `InboundMailProcessingRepository.kt:19-169` — 去重、按状态/专家查询、人工队列、原因分组、监控列表、分页回填；人工队列与计数只认 `process_status='MANUAL_REVIEW'`（`:43-113`）。
  2. `InboundMailProcessingRepository.kt:171-217` — 收件汇总及计数。
  3. `MailRecordRepository.listMailbox/countMailbox` — 投影 `process_status/reason_type/inbound_processing_id`，`onlyPending` 只认 `MANUAL_REVIEW`，见 `MailRecordRepository.kt:405-529`。
  4. `MailRecordRepository.listMailboxExpertSummaries/countMailboxExperts/listMailboxByExpertContactIds` — 专家分组待办数、待处理过滤和卡片投影均只认 `MANUAL_REVIEW`，见 `MailRecordRepository.kt:531-751`。
  5. `UnmatchedInboundMailService.kt:29-70` — 人工队列、总数和原因分组；`:68` 读取详情。
  6. `MailboxService.kt:30-167,228-267,324-386` — 平铺/专家邮箱、详情和“待处理”标签；标签条件是 `MANUAL_REVIEW`。
  7. `MailMonitoringService.kt:62-76,168-207,238` — 每日待处理数、活动状态/原因、账号最后收件时间。
  8. `InboundMailSummaryController.kt:35-95,144-153` — 汇总、线程和标签来源。
  9. `ExpertContactManagementService.kt:69-91` — 读取专家最新 `MANUAL_REVIEW.reasonType` 供人工关注提示。
  10. `PendingMailOperationService.kt:102,122,154,508,524,954,1025` — 各人工处理/回复操作读取同一行；既有 `markResolved` 只要求 `MANUAL_REVIEW`，因此 I-2 状态可再次处理。
  11. `TrustReplyWorkbenchService.kt:1772-1787`、`AutoReplyPreviewService.kt:49-61` — 按 ID 读取正文/上下文；取消不改其依赖字段。
  12. `AiTrainingController.kt:410`、`BounceBackfillService.kt:16-42`、`InboundMailTagService.kt:242` — 按专家回退读取、分页回填、存在性检查；取消不改身份字段。
  13. `InboundMailTagRepository.kt:46-75`、`MailRecordRepository.kt:68-107` — 通过入站 ID 连接标签；取消不改 ID 或标签。
- Interaction points:
  - 新条件写路径把状态写为 `MANUAL_REVIEW` → 人工队列、邮箱 `onlyPending`、专家分组 `pending_count`、监控数、`待处理` 标签均自动读到，无需改读 SQL。
  - `reason_type=NULL` → 原因分组查询（明确要求非空）不新增伪造分类，但 `manualReviewTotal` 仍按状态计入；前端普通优先级数用总数减高优先级数，顺序计划 2 验证。
  - `resolved_at/resolved_by=NULL` → 详情响应通过现有实体映射读到未解决态，无需 DTO 变更。

### Store: `expert_contact.needs_manual_attention`

- Schema/mapping: `BOOLEAN NOT NULL DEFAULT FALSE`，语义为红条/badge 显隐依据，见 `V14...sql:6-8`；Kotlin 字段见 `ExpertContact.kt:7-34`。
- 全部生产写路径（grep 证据：`rg "needsManualAttention\\s*=" src/main/kotlin`）：
  1. `AutoMailReplyService.kt:201-206,882-903` — 自动流程转人工时写 `true`。
  2. `PendingMailOperationService.kt:971-983` — 最后一条人工待办被标记完成时写 `false`。
  3. `UnmatchedInboundMailService.kt:173-177,229-237` — 绑定/旧完成路径可能写 `false`。
  4. `ExpertContactManagementService.kt:430` — 切回自动回复时写 `false`。
  5. 本计划新增 `cancelResolved` — 成功重新打开有关联收件时只写 `true`。
- 全部生产读路径:
  1. `ExpertContactRepository.kt:43-64` — 联系人列表筛选。
  2. `ExpertIndexController.kt:75-92,389-439` — 索引列表 DTO。
  3. `ExpertContactManagementController.kt:428-446,535-555` — 联系人响应。
  4. `MailMonitoringService.kt:168-207` — 监控响应。
  5. `app.js:4972-5048,5130,7337-7352` — 联系人加载、行标记、人工关注红条。
- Interaction points: 取消写 `true` → 联系人 API/列表/红条读到；`ExpertContactManagementService.kt:75-91` 同时从最新 `MANUAL_REVIEW` 行取可空原因，I-2 将产生既有通用提示，不伪造原原因。

### Store: `operator_action_log`

- Schema/mapping: `action_type VARCHAR(64) NOT NULL`，before/after 为 TEXT，按入站 ID/时间有索引；数据库没有 action type 枚举约束，见 `V19__add_operator_status_and_action_log.sql:32-52`。现有 Kotlin enum 已包含多项 V19 注释未列出的 AI action，见 `OperatorActionType.kt:3-20`，所以新增 enum 不需要迁移。
- 全部生产写路径:
  - 所有日志统一进入 `OperatorActionLogService.record` 并由 `operatorActionLogRepository.save` 落库，见 `OperatorActionLogService.kt:19-45`。
  - 调用点 grep 共分布于 `ExpertContactManagementService`、`ExpertIndexLevelOperationService`、`ExpertOperatorStatusService`、`AiReplyReviewAuditService`、`AiTrainingEvaluationService`、`BounceController`、`ManualReplySendAttemptService`、`PendingMailOperationService`、`SenderAccountBindingService`、`UnmatchedInboundMailService`；本计划只新增 `PendingMailOperationService.cancelResolved` 调用，不改其余调用。
- 全部生产读路径:
  1. `OperatorActionLogRepository.kt:9-76`、`OperatorActionLogService.kt:47-68` — 条件搜索与特定审计查询。
  2. `OperatorActionLogController.kt:11-37` — 通用日志 API。
  3. `UnmatchedInboundMailController.kt:125-148` — 收件详情嵌入最近 50 条日志。
  4. `QaRuleAuditService.kt:19` — QA 日志搜索。
  5. `app.js:7951-8080` — 操作日志渲染；新 action 的中文标签/转移展示由顺序计划 2 接入。
- Interaction points: 新日志写入 → 现有通用搜索和收件详情立刻返回；后端计划不改变读协议。

## 实现方案

### 阶段 1：先固化仓储条件更新

1. 在 `InboundMailProcessingRepository.kt` 引入 Spring Data JDBC `@Modifying`，新增 `reopenManualResolved(id, now): Int`。
2. SQL 必须逐项满足 I-1、I-2、I-5：

   ```sql
   UPDATE inbound_mail_processing
      SET process_status = 'MANUAL_REVIEW',
          process_reason = 'MANUAL_REOPENED',
          reason_type = NULL,
          resolved_at = NULL,
          resolved_by = NULL,
          updated_at = :now
    WHERE id = :id
      AND process_status = 'PROCESSED'
      AND process_reason = 'MANUAL_RESOLVED'
      AND reason_type = 'MANUAL_RESOLVED'
   ```

3. 新建 `InboundMailProcessingRepositoryTest.kt`，以 MySQL/Flyway `@DataJdbcTest`（沿用 `OperatorActionLogRepositoryTest.kt:21-51` 套路）验证命中、三种不命中、字段清空和更新计数。覆盖 I-1、I-2、I-5。

### 阶段 2：服务事务与审计

1. 在 `OperatorActionType.kt` 增加 `CANCEL_INBOUND_RESOLVED("取消待处理邮件已处理状态")`。覆盖 I-4。
2. 在 `PendingMailOperationService.kt` 新增 `@Transactional cancelResolved(inboundProcessingId, operatorName, note)`：
   - `findById` 不存在抛 `ResponseStatusException(HttpStatus.NOT_FOUND, ...)`；
   - 读取后严格验证 I-1 三元组，失败抛 409；
   - `actualOperator = operatorName?.takeIf { it.isNotBlank() } ?: "UNKNOWN"`，沿用现有 markResolved 的操作人回退规则（`PendingMailOperationService.kt:958`）；
   - 调用条件更新，返回值不是 1 立即抛 409；
   - 有关联专家时仅在 `needsManualAttention=false` 时保存 `copy(needsManualAttention=true)`；
   - 记录 I-4 定义的 before/after 日志；不读历史日志，不调用 IMAP 服务。
3. 扩展 `PendingMailOperationServiceTrustWorkbenchTest.kt`：成功有关联专家、成功无关联专家、三元组每项不匹配、条件更新返回 0、缺失 ID、空操作人回退、审计字段精确值。每个失败用例验证仓储后续写、专家保存、审计均未发生。覆盖 I-1 至 I-5。

### 阶段 3：HTTP 入口

1. 在 `UnmatchedInboundMailController.kt` 增加：
   - `CancelResolvedRequest(operatorName: String?, note: String?)`；
   - `POST /unmatched-inbound/{id}/cancel-resolved`，仅转发 ID、operatorName、note 给 `PendingMailOperationService.cancelResolved`，成功为 HTTP 200 空 body。
2. 不复用 `MarkResolvedRequest.resolvedBy`，避免“取消”请求继续携带语义相反字段；不改现有 mark endpoint。覆盖 I-1、I-4。

### 阶段 4：验证

1. 定向单测：`mvn -Dtest=PendingMailOperationServiceTrustWorkbenchTest test -DskipNodeTests=true`。
2. MySQL 仓储测试：`mvn -DmigrationIt=true -Dtest=InboundMailProcessingRepositoryTest test -DskipNodeTests=true`（需要 Docker；仓库既有 migrationIt 门禁见 `OperatorActionLogRepositoryTest.kt:21-51`）。
3. 全量：`mvn test package`；Maven test phase 同时运行 Node 测试，证据见 `pom.xml:171-214`。
4. `git diff --check`。

## 变更文件清单

| # | 文件 | 变更 | 所属子系统 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/audit/domain/OperatorActionType.kt` | 新增取消 action type | 审计 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt` | 新增严格条件更新 | 收件处理 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 新增事务性取消服务 | 收件处理/审计交互 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 新增请求 DTO 与 endpoint | 收件处理 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | 服务状态、副作用、审计测试 | 收件处理 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepositoryTest.kt` | 条件 SQL 的 MySQL 集成测试 | 收件处理 |

文件数：6；独立子系统：2（收件处理、审计）；共享 store 新字段数：0。

## 验收标准

- I-1: 仓储集成测试证明只有精确三元组更新计数为 1；状态、processReason、reasonType 任一不匹配均为 0。服务测试证明前置不匹配和 CAS=0 都返回 409，且无专家/日志写入。
- I-2: 仓储测试重新查询并逐值断言 `MANUAL_REVIEW`、`MANUAL_REOPENED`、`NULL`、`NULL`、`NULL` 和新 `updated_at`；代码/测试中不存在历史 reason 恢复查询。
- I-3: 服务测试捕获 `ExpertContactRepository.save` 参数，只允许 `needsManualAttention: false -> true`，其余字段与 fixture 相等；已为 true 与 contactId=null 均不 save。
- I-4: 服务测试精确断言 action type、target IDs、expertContactId、before/after、operatorName、note；失败路径 `verifyNoInteractions(operatorActionLogService)`。方法保留 `@Transactional`。
- I-5: 新 SQL 的 SET 列集合精确为 I-2 六列；测试断言 sender account、UID、正文、expertContactId 不变；服务测试证明无 `MailReceiveService` 依赖/调用。
- 集成场景: API 取消成功后，`findManualReviewQueue`、邮箱 `onlyPending`、专家分组 `pending_count`、`countManualReviewBetween` 均增加/出现该记录；再次调用既有 mark endpoint 可回到 `PROCESSED/MANUAL_RESOLVED/MANUAL_RESOLVED`。
- 构建: 定向单测、migrationIt 仓储测试、`mvn test package`、`git diff --check` 全通过。

## 人工验收清单

### A-1: API 成功取消并重新入队

- 前置条件: 通过现有 UI 把一条收件标记为已处理；记录数据库三元组为 `PROCESSED/MANUAL_RESOLVED/MANUAL_RESOLVED`。
- 操作步骤: 1. POST `/api/mail/unmatched-inbound/{id}/cancel-resolved`，body 为 `{"operatorName":"验收员","note":"误操作"}`。2. 查询该 ID。3. 打开“仅待处理”邮箱/人工队列。
- 预期结果: HTTP 200；数据库为 `MANUAL_REVIEW/MANUAL_REOPENED/NULL`，`resolved_at/resolved_by` 均为 NULL；同一 ID 出现在待处理列表并带“待处理”。
- 覆盖: I-1、I-2、I-5、可观察结果 1。

### A-2: 可再次处理

- 前置条件: A-1 已完成。
- 操作步骤: 对同一 ID 调用既有 `/mark-resolved`，操作人为“验收员2”。
- 预期结果: HTTP 200；同一行回到 `PROCESSED/MANUAL_RESOLVED/MANUAL_RESOLVED`，解决人为“验收员2”，从仅待处理列表消失。
- 覆盖: I-2、必须保持不变第 5 项、可观察结果 1。

### A-3: 非人工完成记录不可取消

- 前置条件: 各准备一条 `PROCESSED/MANUAL_BOUND/*`、自动 `PROCESSED/*/AUTO_NOOP`、`MANUAL_REVIEW/*/*` 记录。
- 操作步骤: 分别调用 cancel endpoint。
- 预期结果: 每次 HTTP 409；四条记录字段不变；无 `CANCEL_INBOUND_RESOLVED` 日志。
- 覆盖: I-1、必须保持不变第 3 项。

### A-4: 重复与并发取消只有一次成功

- 前置条件: 一条符合 I-1 的记录。
- 操作步骤: 同时发送两个相同 cancel 请求；完成后再发送第三次。
- 预期结果: 三次中仅一次 HTTP 200，其余 HTTP 409；仅一条取消日志；最终字段符合 I-2。
- 覆盖: I-1、I-4、可观察结果 3。

### A-5: 关联专家恢复人工关注且其他字段不变

- 前置条件: 符合 I-1 且有关联专家的记录；记录专家取消前 `needsManualAttention=false`，抄录其 currentStatus/operatorStatus/currentIndexLevel/autoReplyEnabled/manualHandoffRequired。
- 操作步骤: 调用取消 API；刷新专家列表和详情。
- 预期结果: `needsManualAttention=true`，显示“⚠ 需要人工处理”；五个抄录字段逐值不变。
- 覆盖: I-3、可观察结果 2、必须保持不变第 4 项。

### A-6: 无关联收件可取消

- 前置条件: 一条 `expert_contact_id=NULL` 且符合 I-1 的记录。
- 操作步骤: 调用取消 API并打开人工队列。
- 预期结果: HTTP 200；记录符合 I-2 并重新出现；无专家行被修改。
- 覆盖: I-2、I-3。

### A-7: 审计日志字段可追溯

- 前置条件: A-1 已完成。
- 操作步骤: 调用 `/api/operator-action-logs?inboundProcessingId={id}` 或打开该收件操作日志。
- 预期结果: 存在且仅存在一条 `CANCEL_INBOUND_RESOLVED`；操作人“验收员”、备注“误操作”；before 为已处理三元组及旧解决信息，after 为 I-2 精确值。
- 覆盖: I-4、可观察结果 2。

### A-8: 收件身份、内容、Seen、标签不变

- 前置条件: 符合 I-1 的收件；记录 sender_account_code、imap_uid、message_id、正文、标签、附件数量，并在邮箱服务端确认 Seen=true。
- 操作步骤: 调用取消 API后重新核对。
- 预期结果: 上述数据库值和数量逐项不变；邮箱服务端仍为 Seen=true；表中仍是原 ID 的单行记录。
- 覆盖: I-5、必须保持不变第 1、2 项。

