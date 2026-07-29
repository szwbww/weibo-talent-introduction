# 可信回复工作台无依据回答 V1－04：训练评估合格后入索引

日期：2026-07-29
状态：待批准、未执行
前置：[01 后端逐项语义与版本合同](./trust-reply-unsupported-answer-v1-01-backend-item-semantics.md)、[03 ES 索引与训练只读列表](./trust-reply-unsupported-answer-v1-03-es-index-training-list.md) 已通过
后续：[05 正式发送成功后入索引](./trust-reply-unsupported-answer-v1-05-live-send-qualified-archive.md)

## 需求描述

把训练评估作为无依据回答进入 ES 的资格门：只有 authoritative assemble 成功、训练评估日志已成功保存且评分为 `MEETS_EXPECTATION` 时，才把其中 `ANSWER_FROM_OPERATOR_INPUT` 版本写入新索引，状态为 `CANDIDATE`。

ES 写入失败不能让已经保存的训练评估变成失败；前端必须明确区分“评估已保存”和“索引归档结果”。

必须不改变：

1. 训练评估 source/rating/note/operator 校验、authoritative assemble 和 append-only action log。
2. action log snapshot 的有界、无正文合同及其他 action type 的查询/指标。
3. `NEEDS_IMPROVEMENT/UNUSABLE` 的保存行为；它们仍保存评估，只是不入 ES。
4. 训练页面评分控件、保存一次语义和其他 AI Training Tab。

明确不纳入：真实发送归档、自动重试/outbox、手工补写、索引复用、评估历史 UI、CSS 改造。

## 关键不变量

### Invariant I-1: 训练 action log 成功先于 ES

- Rule: 必须按校验/assemble/action-log/archive 串行执行；log 失败零 ES，ES 失败不回滚 log。
- Applies to: `AiTrainingEvaluationService.save` 的唯一训练评估写路径。
- Violation consequence: 未形成评估资格的数据提前入库，或 ES 故障丢失已完成评估。
- 来源: original；K-training-evaluation-bounded-action-log。

固定顺序：

```text
校验输入 -> authoritative assemble -> resolve current source
-> operator_action_log.record 成功并取得 evaluationId
-> 尝试 ES archive -> 返回 evaluation + archive status
```

- action log 失败时不写 ES。
- ES 失败时不删除/回滚 action log、不抛出使 HTTP 变失败的异常。
- 不使用并行 Promise/线程打乱资格顺序。

### Invariant I-2: 只有符合预期的 operator-directed 版本入库

- Rule: rating 必须 MEETS_EXPECTATION，且版本必须是 canonical ANSWER_FROM_OPERATOR_INPUT + AI_GENERATED + 三段非空文本。
- Applies to: post-log filter、document factory、ES create。
- Violation consequence: 低质量、ACK、OMIT 或有据回答污染无据回答索引。
- 来源: original。

- rating=`MEETS_EXPECTATION` 才尝试 archive。
- `NEEDS_IMPROVEMENT`、`UNUSABLE` 固定 `NOT_APPLICABLE`，即使版本存在也不写。
- 从服务端 `workbenchService.assemble()` 返回的 canonical `itemVersions` 过滤，条件必须同时为：
  - handling=`ANSWER_FROM_OPERATOR_INPUT`
  - generationKind=`AI_GENERATED`
  - requestText/operatorInstruction/answerText 非空
  - source 为 `TRAINING_MAIL`
- ACK、SAFE_TEMPLATE、OMIT、有据/部分有据版本永不写。

### Invariant I-3: 训练文档身份和资格字段由服务端固定

- Rule: TRAINING/CANDIDATE/TRAINING_EVALUATION/evaluationId/contact/campaign/operator 只能来自本次 canonical source/log。
- Applies to: document construction、deterministic ID、list reader。
- Violation consequence: 错误来源、审批人或资格 ID 被展示/用于未来治理。
- 来源: original。

- `sourceMode=TRAINING`、`status=CANDIDATE`、`qualificationType=TRAINING_EVALUATION`。
- `qualificationId=evaluationId`；`approvedBy` 使用已规范化 operatorName。
- `expertContactId/campaignId` 从本次已解析 canonical source contact 获取，不接受浏览器值。
- `createdAt` 为 evaluation log 成功后的 UTC 时间；同一 canonical version 重复评估由 ES deterministic ID 幂等去重。
- create-only 文档保留首次成功资格的 qualificationId/createdAt；后续同版本评估得到 ALREADY_EXISTS，不更新已有 `_source`。

### Invariant I-4: 操作日志继续有界且无正文

- Rule: snapshot 只保存既有 hashes/metadata/counts，禁止新增问题、说明、回答或预览文本。
- Applies to: `buildSnapshot`、operator_action_log.after_value、audit readers。
- Violation consequence: 审计表膨胀、隐私边界破坏、下游指标解析改变。
- 来源: K-training-evaluation-bounded-action-log。

- `AiTrainingEvaluationService.buildSnapshot` 继续只保存 requestKey、handling、versionId、answerHash、model、generationKind、计数和 source/evidence/draft hash。
- 不把 `requestText/operatorInstruction/answerText` 或其截断预览加入 `operator_action_log`。（来源：K-training-evaluation-bounded-action-log）
- 完整正文只进入专用 ES 文档，职责边界清晰。

### Invariant I-5: 归档状态可观察但不是评估 gate

- Rule: 四态/count 精确汇总；created/already-exists 都成功；异常只变 archive 字段，不变 evaluation HTTP 成功。
- Applies to: gateway result、evaluation response、frontend status。
- Violation consequence: 用户重复评估或误认为评估未保存。
- 来源: original。

响应追加带默认值的字段：

```text
unsupportedAnswerArchiveStatus = NOT_APPLICABLE | SAVED | PARTIAL | FAILED
unsupportedAnswerArchivedCount
unsupportedAnswerArchiveFailedCount
```

- CREATED 与 ALREADY_EXISTS 都计为 archived success。
- 多条中部分失败为 PARTIAL；全部失败为 FAILED；无 eligible item 为 NOT_APPLICABLE。
- 响应不包含 ES 异常正文、URL、凭据或失败文档内容。

### Invariant I-6: 前端保存状态不可因归档失败反转

- Rule: 2xx evaluation response 立即永久标 saved；archive warning 不重新启用按钮、不再次 POST。
- Applies to: `saveAiTrainingEvaluation` state/status/toast。
- Violation consequence: 重复 action log、重复资格事件或误操作。
- 来源: original。

- HTTP 成功返回后，前端 `context.saved=true`，保存按钮保持禁用；即使 archive status=FAILED 也不能让用户重复提交评估制造多条 action log。
- SAVED 显示已归档数量；PARTIAL/FAILED 显示非阻塞警告并提示到索引 Tab 检查。
- 网络/评估本身失败仍走现有失败逻辑，不伪装为 archive warning。

## 样式契约

### S-1: 复用训练评估现有 status 与按钮

- 复用: `section.compose-panel.trust-training-evaluation`、`.muted`、`.trust-training-evaluation-status`、`.button.primary`、现有 `showStatus` ok/warn。
- 新增: 无 DOM、无 class、无 CSS、无 inline style；只改变现有 status 的 textContent 与 toast type。
- DOM 结构: 保持下列既有结构，不增加 archive 按钮或第二个状态容器：

```html
<div class="trust-training-evaluation-actions">
  <span class="trust-training-evaluation-status" data-role="training-evaluation-status"></span>
  <button type="button" class="button primary" data-action="save-training-evaluation">保存评估</button>
</div>
```

- 禁止项: 修改 `styles.css`；用 error/red 表示 ES 失败；重新启用已保存按钮；modal；inline style；未声明 class。

- 复用 `#aiTrainingEvaluationPanel [data-role='training-evaluation-status']`（`app.js` 当前 `renderAiTrainingEvaluationPanel`）。
- 成功用普通 status 文本和现有 `showStatus(...,"ok")`。
- PARTIAL/FAILED 用现有 warning 语义 `showStatus(...,"warn")`；不新增颜色、图标、modal 或 inline style。
- 保存按钮仍为 `.button.primary`，成功后 disabled + “已保存”。
- 不修改 `styles.css`。

## 现状审计

### `operator_action_log` store

- Schema/mapping: V19 创建 append-only 表，`action_type VARCHAR(64)`、`after_value TEXT`、`operator_name VARCHAR(128)`、`created_at DATETIME`，按 contact/inbound/type/operator+created 建索引；本计划不改 schema。
- Write paths: 全部通用写入经 `OperatorActionLogService.record -> OperatorActionLogRepository.save`；调用方包括 `ExpertContactManagementService`、`ExpertIndexLevelOperationService`、`ExpertOperatorStatusService`、`AiReplyReviewAuditService`、`AiTrainingEvaluationService`、`BounceController`、`ManualReplySendAttemptService`、`PendingMailOperationService`、`UnmatchedInboundMailService`。本计划只改 `AiTrainingEvaluationService` 调用后的流程，不改其他 caller。
- Read paths: `OperatorActionLogService.search`/controller 分页；repository `findLatestAiDraftByInboundProcessingId` 只读三个 AI draft action；`QaRuleAuditService.aggregateRuleUsage` 只读 SEND_MANUAL_COMPOSED_REPLY。训练 action 不进入后两者精确过滤。
- Interaction points: action log 返回 id/createdAt 为 ES qualification；ES archive 不能改变 after_value schema或其他 reader 的过滤结果。

### `trust_reply_unsupported_answer_v1` store

- Schema/mapping: 03 阶段 strict mapping；训练 writer 使用其中全部 canonical/qualification 字段，不新增 field。
- Write paths: 03 gateway `create` 是唯一低层 writer；本计划新增 `AiTrainingEvaluationService.save` 资格 caller；05 后续增加 live caller。
- Read paths: 03 `list` service/controller/AI Training Tab 读取 TRAINING/CANDIDATE 文档；无其他 consumer。
- Interaction points: evaluation writer 的 source/status/qualification/time 字段必须被 list mapper 正确显示；正文只进 ES，不进 action log。

### 训练评估写路径

- `AiTrainingEvaluationService.save` 当前顺序为：检查 `TRAINING_MAIL`、解析 rating/note/operator、authoritative assemble、resolve source、构建 snapshot、`operatorActionLogService.record`、返回 response。
- 该 service 是唯一训练评估写入口，适合在 `record` 成功后追加 best-effort archive；controller 无需复制逻辑。
- `AiTrainingEvaluationResponse` 当前只有 evaluationId/rating/createdAt，追加带默认值字段可保持既有消费者兼容。

### 快照安全边界

- `buildSnapshot` 当前只保存 `answerHash`，不保存正文；handlingCounts 会自然纳入新 enum。
- `MAX_ITEM_SNAPSHOTS=50`、字符串/模型数量限制已经存在，必须保留。

### 前端保存路径

- `app.js` 的 `saveAiTrainingEvaluation` 当前 POST 后直接设置 `context.saved=true`，状态显示 evaluation ID/时间，并调用成功 toast。
- 只需要解释新增 archive 字段；不修改 assembly payload、评分控件或评估 DOM。

### 前端样式盘点与改动前基线

- 可复用 class/token: S-1；button 32px/12px、primary `#2563eb`、warning `#d97706`、success `#059669`、radius 7px。
- 改动前 DOM 即 S-1 代码块；`saveAiTrainingEvaluation` 当前成功文案为 ``已保存评估 #${result.evaluationId}``，button 变 `已保存` 并 disabled。
- 本计划只扩展 textContent/toast，不新增/移动元素、不修改 class。

### ES gateway

- 03 阶段提供 canonical document DTO 和 `create` 结果，不应在 evaluation service 复制 HTTP、鉴权、hash 或 `_id` 逻辑。

## 实现方案

### T0：执行前研究检查点

- Governs：I-1～I-6、S-1。
- Exact files: 本计划清单 1～8。
- 重新 `rg` 所有 `AI_TRAINING_REPLY_EVALUATED` 写/读、`operatorActionLogService.record` 调用、`AiTrainingEvaluationResponse` 构造、ES gateway create caller、前端 save handler 和 asset-version 断言。
- 若发现其他训练评估写入口、同步返回前必须写 ES 的既有合同、或 action-log reader 依赖新增正文，停止并修订计划。
- 运行 03 阶段和现有 evaluation 测试作为基线后再写 T1 失败测试。

### T1：先增加训练归档失败测试

- Governs：I-1～I-5、S-1。
- Exact files: `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationServiceTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt`。
- 在 `AiTrainingEvaluationServiceTest.kt` 覆盖：
  1. action log 保存后才调用 ES；捕获 evaluationId 作为 qualificationId。
  2. MEETS_EXPECTATION 只归档 operator-directed items；有据、ACK、OMIT 被过滤。
  3. 其他两个 rating 和无 eligible item 不调用 ES。
  4. CREATED/ALREADY_EXISTS -> SAVED；混合成功失败 -> PARTIAL；全失败 -> FAILED。
  5. ES 抛异常时 service 仍返回原 evaluationId/rating/createdAt。
  6. action log 抛异常时 ES 零调用。
  7. snapshot 中不存在 requestText/operatorInstruction/answerText。

### T2：在 ES gateway 增加批次归档辅助结果

- Governs：I-2、I-3、I-5。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt`。
- 在 `UnsupportedAnswerIndexService.kt` 增加小型 `archiveCanonicalVersions(...)` 或由 evaluation service 循环 `create`；无论选择哪种，document construction/hash/validation 必须集中在 gateway 的一个方法。
- 输入为 canonical source/contact/version + qualification metadata；禁止 controller/client document 直传。
- 每项独立捕获结果，返回 archived/failed counts；日志只含 document ID、source type/id、错误类别。
- `UnsupportedAnswerIndexApiTest.kt` 补过滤前置校验、qualification 字段和部分失败汇总测试。

### T3：连接训练评估资格门

- Governs：I-1～I-5。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationServiceTest.kt`。
- 给 `AiTrainingEvaluationService` 注入 gateway。
- `operatorActionLogService.record` 成功后：
  - rating 非 MEETS 直接 NOT_APPLICABLE。
  - 从 `assembled.itemVersions` 过滤 eligible canonical versions。
  - 用已解析 contact id/campaign id、evaluationId、operatorName 组文档并调用 gateway。
  - catch 所有归档异常，记录安全日志并返回 FAILED；不得影响已保存评估。
- response 新字段给默认值，避免旧单元测试/调用构造器大面积变化。

### T4：前端显示双结果

- Governs：I-5、I-6、S-1。
- Exact files: `src/main/resources/static/app.js`、`src/test/js/aiTrainingUnsupportedAnswers.test.js`。
- `saveAiTrainingEvaluation` 在 HTTP 成功后始终先标记已保存。
- 文案合同：
  - `SAVED`：`已保存评估 #123 · 已归档 2 条无依据回答`。
  - `PARTIAL`：`评估已保存 #123；无依据回答仅归档 1/2 条`。
  - `FAILED`：`评估已保存 #123；无依据回答索引写入失败`。
  - `NOT_APPLICABLE`：保持现有已保存文案。
- PARTIAL/FAILED 触发 warn toast，不重新启用按钮。
- `aiTrainingUnsupportedAnswers.test.js` 增加评估响应 UI 测试；不在保存后强制切 Tab或自动刷新列表。

### T5：静态资源与回归

- Governs：I-1～I-6、S-1。
- Exact files: `src/main/resources/static/index.html`、`src/test/js/batchSendTaskConsoleVisualFix.test.js`，以及本计划其余清单文件仅用于测试执行。

- 更新 `index.html` cache-buster 和对应静态测试。
- 执行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q \
  -Dtest=AiTrainingEvaluationServiceTest,UnsupportedAnswerIndexApiTest test
node --check src/main/resources/static/app.js
node --test src/test/js/aiTrainingUnsupportedAnswers.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --test src/test/js/*.test.js
git diff --check
```

## 变更文件清单

| # | 文件 | 动作 | 目的 |
|---:|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt` | 修改 | canonical 文档构建、批次归档结果 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt` | 修改 | 评估成功后的资格过滤与 best-effort 写入 |
| 3 | `src/main/resources/static/app.js` | 修改 | 显示评估与归档双结果 |
| 4 | `src/main/resources/static/index.html` | 修改 | 静态资源 cache-buster |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt` | 修改 | 文档构建与批次结果测试 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationServiceTest.kt` | 修改 | 资格顺序、过滤、失败隔离测试 |
| 7 | `src/test/js/aiTrainingUnsupportedAnswers.test.js` | 修改 | 评估 archive status 显示测试 |
| 8 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | 静态资源版本合同 |

文件数：8；子系统：训练评估/ES 后端、训练前端，共 2 个。

## 验收标准

- I-1: Mockito/InOrder 断言 assemble→record→archive；record 异常时 archive 零调用，archive 异常时 response 仍含持久化 evaluationId。
- I-2: 参数化 rating/version-kind 测试精确断言只有 MEETS + operator-directed 进入 gateway。
- I-3: 捕获 document 断言 TRAINING/CANDIDATE/TRAINING_EVALUATION、evaluationId、canonical contact/campaign/operator 和 deterministic ID；重复同版本不覆盖首次 qualificationId。
- I-4: 反序列化 action-log after map，断言 key whitelist、条数/字符串上限且无 requestText/operatorInstruction/answerText/preview。
- I-5: CREATED/ALREADY_EXISTS/混合失败/全失败/无 eligible 分别断言 SAVED/PARTIAL/FAILED/NOT_APPLICABLE 与 count；HTTP 始终保留评估成功。
- I-6: JS 测试断言所有 2xx 都 `saved=true`、button disabled；PARTIAL/FAILED 只触发 warn，不发第二次 POST。
- S-1: DOM snapshot 与契约代码块相同；`git diff -- styles.css` 为空；status 仅 textContent 变化，无新节点/class/inline style。
- Interaction: 保存符合预期后调用 list API，读到同 qualificationId/content；action-log search 仍读到无正文 snapshot。
- Regression: 其他 action-log writer/reader、三种 rating 保存、四个训练 Tab、后端/JS 全测通过。

## 人工验收清单

### A-1: 符合预期后入 CANDIDATE
- 前置条件: 训练工作台整合结果含 1 个 operator-directed 无据版本；ES 可用。
- 操作步骤: 1. 选择“符合预期”；2. 保存评估；3. 记下 evaluationId；4. 到索引 Tab 刷新并过滤 TRAINING。
- 预期结果: status 显示“已保存评估 #ID · 已归档 1 条无依据回答”；按钮为“已保存”且禁用；列表新增 1 条 CANDIDATE/TRAINING，qualificationId 等于该 ID。
- 覆盖: I-1～I-3、I-5、I-6、S-1、需求主结果。

### A-2: 非合格评分只保存评估
- 前置条件: 两个不同训练邮件都含 operator-directed 版本；记录当前索引总数 N。
- 操作步骤: 第一封选“需要改进”保存，第二封选“不可用”保存，再刷新索引。
- 预期结果: 两次各生成一个 evaluationId 且按钮禁用；archiveStatus 均 NOT_APPLICABLE；索引总数仍 N。
- 覆盖: I-1、I-2、I-6、must-not-change 3。

### A-3: 混合版本过滤
- 前置条件: 一次 assembly 含有据、ACK、OMIT、两个 operator-directed 项。
- 操作步骤: 评分“符合预期”并保存，刷新索引。
- 预期结果: archive status SAVED、count=2；只新增两个 operator-directed 文档，其他三类为 0。
- 覆盖: I-2、I-3。

### A-4: ES 故障不反转评估
- 前置条件: 临时让 ES create 返回 503；准备可评估 assembly。
- 操作步骤: 评分“符合预期”并保存；观察状态、按钮、action log；不要重复点击。
- 预期结果: HTTP/UI 显示 evaluationId 和“评估已保存；无依据回答索引写入失败”；按钮保持“已保存”禁用；action log 有 1 条评估；没有第二次 POST。
- 覆盖: I-1、I-5、I-6、S-1。

### A-5: action log 无正文回归
- 前置条件: 已完成 A-1，拥有 evaluationId。
- 操作步骤: 通过轮询日志/DB 查询该 `AI_TRAINING_REPLY_EVALUATED` 记录的 after_value。
- 预期结果: 只有 schema/source/evidence/draft hash、rating、counts、models 和有界 item snapshots；不存在原问题、操作员说明、AI回答正文。
- 覆盖: I-4、must-not-change 1/2。

### A-6: 重复评估幂等文档
- 前置条件: 同一 source/version 可再次创建相同 canonical version；记录索引总数 N。
- 操作步骤: 再保存一次“符合预期”评估并刷新列表。
- 预期结果: action log 多 1 条新 evaluation；ES 总数仍 N，archive 视为成功而非失败。
- 覆盖: I-3、I-5。

### A-7: UI 与其他训练 Tab 回归
- 前置条件: 1440px 与 390px 浏览器；ES 正常/异常各一次。
- 操作步骤: 检查评估 status/button，并切换 QA、对话、提示词、模拟、索引 Tab。
- 预期结果: status 仍在原按钮左侧，无新增按钮/红色发送失败态/布局错位；现有四 Tab 操作不变，新 Tab 故障独立。
- 覆盖: S-1、must-not-change 4。
