# 可信回复工作台 03：训练评估留存

日期：2026-07-27  
状态：待批准、待执行  
前置：[02 逐项 AI、版本锁定与无改写整合](./trusted-reply-shared-workbench-02-item-lock-assembly.md) 已通过  
后续：[04 单一前端工作台](./trusted-reply-shared-workbench-04-single-frontend-workbench.md)

## 需求描述

允许模拟模式在完成服务端整合后保存一次人工训练评估。评估复用既有 `operator_action_log` 作为 append-only 留存，只记录评分、备注和有界哈希元数据，不保存来信/回复/指令/事实正文。

必须不改变：

1. `operator_action_log` 表结构、既有 action type、搜索接口和 live 详情日志。
2. `QaRuleAuditService` 当前 AI reply quality 指标口径；训练评估不得计入 ready/partial/blocked/send/review 指标。
3. 模拟模式不外发、不写 outbound `mail_record`、不改变 inbound processing 状态。
4. 同一历史邮件允许多次独立评估；不覆盖旧记录。

本计划不包含：评估历史 UI、统计报表、评估导出、自动训练/回灌 QA、草稿正文持久化、数据库新表。

## 关键不变量

### Invariant I-1: 评估只接受精确训练来源
- Rule: 评估 source 必须为 `TRAINING_MAIL`，sourceId 必须是当前精确 INBOUND `mail_record.id`；`LIVE_INBOUND`、联系人 ID、OUTBOUND、缺联系人或不存在记录一律拒绝。
- Applies to: `AiTrainingEvaluationService.save`、`AiTrainingController` evaluation endpoint。
- Violation consequence: 真实回复或错误邮件被当作训练样本留存。
- 来源: K-ai-simulate-exact-mail-id

### Invariant I-2: 只评估服务端重新验证的整合结果
- Rule: 请求不得只上传 draftHash 或自由文本；必须上传 02 的完整 assemble input。服务端调用 `TrustReplyWorkbenchService.assemble` 重新验证 source/evidence/request/version/handling，并以重新生成的 rawDraftText 计算 SHA-256。stale 或非法 assembly 不写日志。
- Applies to: `AiTrainingEvaluationService.save`。
- Violation consequence: 任意文本或过期证据被伪装成可信训练结果。
- 来源: K-ai-preflight-stale-response-draft-identity, K-ai-reply-evidence-version-deterministic

### Invariant I-3: 评分枚举与备注边界固定
- Rule: 新评估枚举只允许 `MEETS_EXPECTATION|NEEDS_IMPROVEMENT|UNUSABLE`；note trim 后可空、最大 1000 字符；operatorName trim 后空值写 `UNKNOWN`、最大 128 字符。未知评分或超限输入在写前返回 422。
- Applies to: evaluation request DTO、service validation、audit write。
- Violation consequence: 无法稳定聚合、日志被超长文本撑大。
- 来源: original

### Invariant I-4: audit payload 有界且禁止正文
- Rule: action type 固定 `AI_TRAINING_REPLY_EVALUATED`，targetType=`MAIL_RECORD`，targetId=精确 mail ID，expertContactId=来源联系人，inboundProcessingId=null。after 只含稳定 key：`schemaVersion/sourceVersion/draftHash/evidenceSetVersion/rating/requestCount/handlingCounts/models/itemSnapshots/itemTotal/itemTruncated`。itemSnapshots 最多 50 项；每项只含 `requestKey/handling/versionId/answerHash/model/generationKind`，每字符串最多 200 字符。禁止保存 inboundText、subject、requestText、answerText、claims text、operatorInstruction、rendered/raw draft、QA answerBody 或可替代正文的 preview。
- Applies to: `AiTrainingEvaluationService.buildSnapshot`、`OperatorActionLogService.record` 调用。
- Violation consequence: 审计日志泄露完整邮件/事实，payload 无界增长。
- 来源: K-review-event-audit-payload-bounds, K-ai-draft-audit-version-hash-not-replay

### Invariant I-5: 写入 append-only 且原子
- Rule: 每次成功保存恰好新增一条 operator log；不 update/upsert/delete 旧评估。assemble/validation/serialization/record 任一步失败时不得留下部分记录。响应只返回 `evaluationId/rating/createdAt`。
- Applies to: `AiTrainingEvaluationService.save`、controller。
- Violation consequence: 重跑训练覆盖历史或出现半条评估。
- 来源: original

### Invariant I-6: 新 action type 不改变既有质量指标
- Rule: `QaRuleAuditService.aggregateAiReplyQualityMetrics` 仍只按现有 5 个精确 action type 计数；不得把 `AI_TRAINING_REPLY_EVALUATED` 加入任何 ready/partial/blocked/send/review 指标或 `SEND_MANUAL_COMPOSED_REPLY` 规则使用统计。
- Applies to: `OperatorActionType`、现有 audit readers 回归。
- Violation consequence: 训练点击量污染线上回复质量指标。
- 来源: original

### Invariant I-7: 保存评估没有发送副作用
- Rule: 评估 service/controller 不注入或调用 `PendingMailOperationService`、`MailDeliveryService`、`MailRecordRepository.save`、`InboundMailProcessingRepository.save`；唯一写路径是 `OperatorActionLogService.record`。
- Applies to: 本计划全部新增/修改代码。
- Violation consequence: 模拟评估外发或改变生产处理状态。
- 来源: K-manual-rich-render-before-send

## 现状审计

### `operator_action_log`
- Schema/mapping: `V19__add_operator_status_and_action_log.sql:32-52` 定义 PK、`target_type VARCHAR(64)`、`target_id`、contact/inbound 外键、`action_type VARCHAR(64)`、summary、before/after TEXT、operator、note、created_at 与四组索引；`OperatorActionLog.kt:7-21` 完整映射。无需 migration。
- ALL write paths:
  1. 唯一 repository save：`OperatorActionLogService.record:19-44`；所有业务 action 都通过此方法 JSON 序列化 before/after 后写入。
  2. 本计划新增 `AiTrainingEvaluationService.save`，也只能调用上述 method；不直接使用 repository。
- ALL read paths:
  1. `OperatorActionLogService.search:47-67` → `OperatorActionLogController /api/operator-action-logs`，支持按 contact/inbound/action/operator/time 搜索。
  2. `UnmatchedInboundMailController.getUnmatchedDetail` 与 live 页面按 `inboundProcessingId` 读取；新评估 `inboundProcessingId=null`，不会混入 live 详情。
  3. `OperatorActionLogRepository.findLatestAiDraftByInboundProcessingId` 只筛 3 个 AI draft action；新评估不会命中。
  4. `QaRuleAuditService.aggregateRuleUsage/aggregateAiReplyQualityMetrics:17-153` 按 `SEND_MANUAL_COMPOSED_REPLY` 和 5 个精确 AI action type 查询；新 action 默认不计入。
- Interaction points: 新 writer → 通用搜索可按 `actionType=AI_TRAINING_REPLY_EVALUATED` 读到；live 详情/latest/QA metrics 必须因精确 filter 保持不变。

### `mail_record`（评估目标，只读）
- Schema/mapping: `V1:97-115`、后续 mail migrations、`MailRecord.kt:7-28`。
- Write paths: `AutoMailReplyService`、`ManualOutreachTxHelper`、`MeetingScheduleService`、`ManualExpertMailService`、`ManualReplySendAttemptService`；本计划不调用。
- Read paths: `AiTrainingController` 列表/模拟、公共 `TrustReplyWorkbenchService` 来源解析、mailbox/monitoring/document services。
- Interaction points: evaluation 通过公共 assemble 再次读取 exact record；删除、改方向、改正文或改联系人都会导致拒绝或 sourceVersion stale。

### 当前评估能力缺口
- 仓库无 training evaluation entity/repository/table/service/endpoint；搜索 `evaluation|rating|feedback` 无持久化实现。
- `AiTrainingController:33-357` 当前含 QA、dialogue、prompt、历史邮件列表和 simulate；最接近的写接口为 QA/prompt 配置，不适合承载一次性评估。
- 选用既有操作日志的理由：评估是操作事件、允许多次 append、已有按 actionType 查询能力；使用 bounded hash snapshot 可满足留存且不扩 schema。

### 目标 API 合同

`POST /api/ai-training/simulate/evaluations`

```json
{
  "source": {"sourceType": "TRAINING_MAIL", "sourceId": 123},
  "expectedSourceVersion": "...",
  "expectedEvidenceSetVersion": "...",
  "lockedItems": [],
  "rating": "NEEDS_IMPROVEMENT",
  "note": "第二项措辞仍偏长",
  "operatorName": "operator-a"
}
```

响应：

```json
{
  "evaluationId": 456,
  "rating": "NEEDS_IMPROVEMENT",
  "createdAt": "2026-07-27T18:00:00"
}
```

## 实现方案

### T1：先固定 validation、payload 和无副作用测试
- Governs: I-1～I-7。
- Files: `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationServiceTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`。
- 失败测试覆盖：三种合法 rating；未知 rating、note 1001、operator 129、live/outbound/missing source 拒绝；assemble stale/invalid 时 record 0 次；成功 record 1 次；第二次成功追加第二条；snapshot 50 截断；禁止字段名/正文 substring 不出现在 JSON；response 仅 3 字段。
- 验证 mock 中 `TrustReplyWorkbenchService.assemble` 返回值是 draftHash 的唯一来源，request 自报 hash 不存在。

### T2：增加 action type 与评估 service
- Governs: I-2～I-7。
- Files: `src/main/kotlin/com/weibo/talentintroduction/audit/domain/OperatorActionType.kt`、`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationServiceTest.kt`。
- `OperatorActionType` 只追加 `AI_TRAINING_REPLY_EVALUATED("AI 训练回复评估")`，不改旧枚举名/summary。
- service 验证输入后调用公共 assemble；对 raw 计算/使用服务端 draftHash；按 request 原序构造最多 50 个 metadata snapshot，并给 total/truncated。
- `handlingCounts` 使用固定三类/四类 handling 名的计数 map；`models` 去重、保留首次出现、最多 5 个并截 64 字符。
- 调用 `OperatorActionLogService.record` 一次；note 使用已裁定长度的原始评语，不放入 after。

### T3：在训练 controller 暴露保存接口
- Governs: I-1、I-3、I-5、I-7。
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`。
- 新 DTO 精确承载 assemble input + rating/note/operator，不接受 draft/raw/rendered/hash 字段。
- controller 只调用 evaluation service；业务校验映射 4xx，不吞异常伪装成功。
- 不新增 list/delete/update endpoint。

### T4：验证所有 operator log readers 不受污染
- Governs: I-4、I-6。
- Files: 本计划 5 个文件；不得修改 `QaRuleAuditService`、repository SQL 或 live 日志 UI。
- 用现有 `QaRuleAuditServiceTest` 和 operator action log tests 证明精确 action filter；通过通用 `/api/operator-action-logs?actionType=AI_TRAINING_REPLY_EVALUATED` 可读取新记录。

## 变更文件清单

| # | 文件 | 动作 | 目的 |
|---:|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/audit/domain/OperatorActionType.kt` | 修改 | 新增训练评估 action type |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt` | 新增 | 重验 assembly、构造有界 snapshot、写日志 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | 修改 | 新增保存评估 endpoint/DTO |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationServiceTest.kt` | 新增 | validation、bounded payload、append/no-side-effect |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | 修改 | endpoint 合同与旧 simulate 回归 |

文件数：5。子系统数：2（训练评估业务/API；既有 audit action taxonomy）。数据库新字段：0；新表：0。

## 验收标准

- I-1: controller/service tests 对 TRAINING_MAIL exact INBOUND 成功；LIVE_INBOUND、OUTBOUND、contact fallback 和不存在 ID 全部 4xx 且 0 writes。
- I-2: 修改 body/QA 后使用旧 assembly 输入返回 stale；mock 证明写入 draftHash 等于服务端 assemble raw hash，request 不存在 hash 字段。
- I-3: 三枚举成功；未知值、1001 note、129 operator 返回 422；空 operator 落 `UNKNOWN`。
- I-4: snapshot 序列化测试断言允许 key 精确集合；51 items 时 snapshots=50、itemTotal=51、itemTruncated=true；禁用字段名与 fixture 正文均不出现。
- I-5: 每次成功 repository save 恰好 1 次且 ID 不同；任一前置失败 0 次；响应 JSON 只有 evaluationId/rating/createdAt。
- I-6: `QaRuleAuditServiceTest` 原指标断言不变；新 action 记录后 ready/partial/blocked/send/review 计数不变。
- I-7: 新 service/controller 源码 grep 无 send/mail/inbound save 调用；唯一写调用为 `operatorActionLogService.record`。
- 定向：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiTrainingEvaluationServiceTest,AiTrainingSimulateTest,OperatorActionLogServiceTest,QaRuleAuditServiceTest,TrustReplyWorkbenchItemFlowTest test
```

- 全量：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`。

## 人工验收清单

### A-1: 保存符合预期评估
- 前置条件: 用 TRAINING_MAIL 完成一个仍为 current 的 assemble；记下该 mail 的 operator log 数量。
- 操作步骤: 1. rating 选 `MEETS_EXPECTATION`、note 填“可直接作为参考”；2. POST 保存；3. 按 actionType 查询通用日志。
- 预期结果: 返回一个 evaluationId、rating=`MEETS_EXPECTATION` 和 createdAt；新增恰好 1 条 `AI_TRAINING_REPLY_EVALUATED`，targetType=`MAIL_RECORD`、targetId 为精确 mail ID。
- 覆盖: I-1、I-3、I-5；需求可观察结果。

### A-2: 同一邮件多次评估追加
- 前置条件: A-1 已完成。
- 操作步骤: 对同一 assembly 再保存 `NEEDS_IMPROVEMENT` 和不同备注，再查询日志。
- 预期结果: 出现 2 条不同 ID 的评估记录，第一条仍为 `MEETS_EXPECTATION`，没有 update/覆盖。
- 覆盖: I-5、must-not-change 4。

### A-3: stale 评估不落日志
- 前置条件: 已有 assemble input；随后修改/禁用其 QA 事实。
- 操作步骤: 用旧 expectedEvidenceSetVersion 保存 `UNUSABLE`，再查询 action log。
- 预期结果: API 返回 409 `TRUST_REPLY_EVIDENCE_STALE`；没有新增评估日志。
- 覆盖: I-2；interaction point `QaRule write → assemble re-read → audit write`。

### A-4: 日志不含正文或指令
- 前置条件: 来信、逐项答案、AI 指令分别放入唯一可搜索标记，如 `SECRET-INBOUND-27`、`SECRET-ANSWER-27`、`SECRET-INSTRUCTION-27`。
- 操作步骤: 保存一次合法评估，读取其 beforeValue/afterValue/note。
- 预期结果: 三个 SECRET 标记均不在 before/after；note 只含人工评语；item snapshot 只见 key、handling、version/hash、model、kind。
- 覆盖: I-4。

### A-5: 训练评估不污染质量指标
- 前置条件: 记录 QA 审计页 ready/partial/blocked/send/review 五个数字。
- 操作步骤: 连续保存 2 次训练评估并刷新 QA 审计页。
- 预期结果: 五个数字全部不变；通用操作日志可按新 actionType 查到 2 条。
- 覆盖: I-6；interaction point `new log write → QaRuleAuditService read`。

### A-6: 模拟评估无外发副作用
- 前置条件: 记下 outbound `mail_record` 数、SMTP 测试桩调用数、关联 inbound processing 状态。
- 操作步骤: 完成一次模拟 assembly 并保存评估。
- 预期结果: 仅 operator_action_log +1；outbound 数、SMTP 调用数和 inbound processing 状态均不变。
- 覆盖: I-7、must-not-change 3。

### A-7: live 与旧日志回归
- 前置条件: 一条 live 来信。
- 操作步骤: 生成 live 初稿、采用并人工发送，再查看 live 操作日志与 QA 质量指标。
- 预期结果: 既有 AI draft/send action 与指标照旧；live 日志列表不出现其他训练邮件的 evaluation。
- 覆盖: I-6、must-not-change 1～2。

