# 可信回复工作台无依据回答 V1－01：后端逐项语义与版本合同

日期：2026-07-29
状态：待批准、未执行
前置：无
后续：[02 共享工作台交互与翻译](./trust-reply-unsupported-answer-v1-02-shared-workbench-ux.md)、[03 ES 索引与训练只读列表](./trust-reply-unsupported-answer-v1-03-es-index-training-list.md)

## 需求描述

扩展公共可信回复工作台后端，使 `UNSUPPORTED` 项可以把操作员填写的“回答说明”作为人工授权内容，由 AI 改写成面向收件人的回答；同时修复当前 assemble 容易因客户端混装 handling 与版本正文而报 `TRUST_REPLY_LOCKED_ITEM_INVALID` 的合同缺陷。

本计划只改公共工作台后端和对应测试，不改前端、不建 ES、不改变发送链路。

必须不改变：

1. GROUNDED/PARTIAL 的 QA `answerBody` 事实来源、claim/trust/action 校验和 source/evidence stale 语义。
2. `ACKNOWLEDGE_PENDING` 的安全确认语与 safe-template fallback。
3. locked composer 的原序、全量、逐字、不去重、不截断合同。
4. 训练与真实来源解析、完整生成 SSE/取消/TTL、自动回复和纯人工发送行为。

明确不纳入：前端交互、翻译、ES、训练归档、正式发送归档、旧 API 清理。

## 关键不变量

### Invariant I-1: 处理矩阵由服务端唯一决定

- Rule: 允许/推荐 handling 必须精确等于下表；新值只允许 UNSUPPORTED。
- Applies to: bootstrap coverage、`AiReplyDraftService.validateItemHandling`、`TrustReplyWorkbenchService.adjustItem/validateLockedItem`。
- Violation consequence: 无据内容被冒充有据回答，或前后端对同一项采用不同语义。
- 来源: original；K-grounding-status-ui-only。

| grounding status | allowed handlings | recommended |
|---|---|---|
| `GROUNDED` | `ANSWER_WITH_EVIDENCE`, `OMIT` | `ANSWER_WITH_EVIDENCE` |
| `PARTIAL` | `ANSWER_SUPPORTED_PART`, `ACKNOWLEDGE_PENDING`, `OMIT` | `ANSWER_SUPPORTED_PART` |
| `UNSUPPORTED` | `ANSWER_FROM_OPERATOR_INPUT`, `ACKNOWLEDGE_PENDING`, `OMIT` | `ACKNOWLEDGE_PENDING`（01 兼容期；02 与新 UI 同批切换为 `ANSWER_FROM_OPERATOR_INPUT`） |

- 新枚举 `ANSWER_FROM_OPERATOR_INPUT` 只能用于 `UNSUPPORTED`；其他组合返回 422 `TRUST_REPLY_HANDLING_INVALID`。
- 01 单独部署时保留旧推荐值，避免旧静态资源把未知枚举默认选中；02 上线新 label/空说明 gate 时再切产品默认。
- `ACKNOWLEDGE_PENDING` 继续表示“尚待核实”的安全确认语，不等同于根据操作员说明作答。
- coverage/status 仍为服务端分析结果；操作员描述不能把 `UNSUPPORTED` 提升成 `GROUNDED`。（来源：K-grounding-status-ui-only）

### Invariant I-2: 操作员说明的 authority 随 handling 固定

- Rule: 只有 `ANSWER_FROM_OPERATOR_INPUT` 把非空说明作为内容依据；其他 handling 下说明仅能调表达。
- Applies to: item request validation、LLM prompt、fallback、version materialization、assemble validation。
- Violation consequence: 操作员要求被忽略，或风格指令被错误升级为事实。
- 来源: original；K-ai-generate-single-freeform-seam。

- `ANSWER_FROM_OPERATOR_INPUT`：trim 后必须非空、最多 500 字符；它是该项回答内容的人工依据。prompt 必须明确“只可重述/组织这段说明，不得补充说明之外的机构、项目、资金、合同、时间或身份事实”。
- `ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART` / `ACKNOWLEDGE_PENDING`：`operatorInstruction` 仍只可调整语气、语言、长度、结构，不得成为事实来源。
- 输出仍执行现有内部标记清理、文本结构和 action policy；但不伪装成 QA-grounded claim，也不要求生成 claims。
- LLM 不可用、超时、取消、fallback 或输出为空时不产生可采用的 `ANSWER_FROM_OPERATOR_INPUT` 版本，返回既有稳定失败码。

### Invariant I-3: 无依据生成复用唯一单项生成 seam

- Rule: 新分支必须位于 `AiReplyDraftService.generateItem`，复用同一 model/TTL/cancel/progress/client，且只处理目标 request。
- Applies to: `ADJUST_ITEM` 服务调用和 AI result mapping。
- Violation consequence: controller prompt 分叉、超时/取消失效，或调整一项污染其他项。
- 来源: K-ai-generate-single-freeform-seam。

- 在 `AiReplyDraftService.generateItem` 内新增窄分支，不在 controller 或 workbench service 复制 LLM 调用、prompt、超时、取消、进度或 model 选择。
- 只传目标 `requestKey/requestText` 与操作员说明；不得把其他 request 的说明或版本带入 prompt。（来源：K-ai-generate-single-freeform-seam）
- 新分支返回 `generationKind=AI_GENERATED`、非空 `answerText`、空 `claims`；handling 本身表达它是 operator-directed，不新增含混的 generation kind。

### Invariant I-4: canonical 版本是不可拆分的语义元组

- Rule: 下列字段必须由一次 materialization 产生；说明 hash 和 versionId 可确定性复算，assemble 返回服务端 canonical 版本和完整 requestedFactIds。
- Applies to: 初始版本、单项版本、locked request、assemble response、后续资格归档。
- Violation consequence: 版本字段混装触发 invalid，或错误正文被归档为另一处理方式。
- 来源: original；K-trust-reply-resolved-version-single-source。

`TrustReplyItemVersion` 的权威字段固定为：

```text
requestKey + requestIndex + requestText + handling + answerText + claims
+ model + generationKind + evidenceSetVersion + sourceVersion
+ operatorInstruction + operatorInstructionHash + versionId
```

- `requestIndex/requestText` 由当前 `RequestFactItem` 填充，不能信任客户端。
- `operatorInstructionHash=sha256(trimmed operatorInstruction)`；空说明继续固定为既有 `sha256("")`，不改变旧版本 ID 规则。
- `versionId` 继续由规范化语义字段确定性计算；包含 `operatorInstructionHash`，不包含时间戳。
- `assemble` 返回的 `itemVersions` 必须是服务端重新 materialize 的 canonical 版本，供后续资格归档使用。
- `TrustReplyAssembleResponse` 追加服务端 canonical `requestedFactIds=selection.sendQaRuleIds`；后续重放必须使用它，不能用“实际进入回答的 canonicalFactIds”代替完整选择集。
- DTO 新字段追加在末尾并带安全默认值，避免破坏既有 Kotlin 构造调用。

### Invariant I-5: assemble 对锁定项 fail closed

- Rule: assemble 只接受覆盖全部 request 的完整 canonical locked tuple；任何缺项、重复、混装、篡改或 hash 不符均拒绝。
- Applies to: `TrustReplyWorkbenchService.assemble/validateLockedItem/materializeVersion`。
- Violation consequence: 服务端替客户端猜测语义，产生错误正文或错误存档。
- 来源: original。

- 客户端每个 request 只能提交一个完整 locked item；缺项、重复、未知项仍 fail closed。
- 服务端以 locked item 自身 handling 选择校验分支，并重算其完整 versionId；绝不使用另一个 UI 字段替换 handling 或正文。
- `ANSWER_FROM_OPERATOR_INPUT` 必须同时满足：当前 item 为 `UNSUPPORTED`、说明非空且 hash 一致、回答非空、claims 为空、generationKind 为 `AI_GENERATED`。
- 任一字段来自不同版本或被篡改，返回 422；不自动改成 ACK、不自动生成安全模板。

### Invariant I-6: FULL_DRAFT 不自动决定无据项

- Rule: 完整生成只物化真实生成成功的 GROUNDED/PARTIAL 初始版本；UNSUPPORTED 无默认版本。
- Applies to: `buildInitialItemVersions`、FULL_DRAFT response。
- Violation consequence: 无据项被安全模板预占，操作员说明无法成为实际回答。
- 来源: original。

- `buildInitialItemVersions` 只物化成功生成的 `GROUNDED/PARTIAL` item answer。
- `UNSUPPORTED` 不再自动产生 `SAFE_TEMPLATE` 首版本；它必须由操作员选择 `ANSWER_FROM_OPERATOR_INPUT`、`ACKNOWLEDGE_PENDING` 或 `OMIT` 后单项生成。
- 完整生成仍可报告 unsupported request，但不能把整封 fallback 或确认语伪装成已处理版本。

### Invariant I-7: OMIT 是 canonical 零输出

- Rule: OMIT 固定空 answer/claims、OMITTED、不调用 LLM，并从最终有序答案列表完全过滤。
- Applies to: adjustItem、version validation、assemble、locked composer。
- Violation consequence: 邮件出现省略占位、空段或不必要 LLM 调用。
- 来源: K-locked-item-assembly-list-not-set。

- `OMIT` 不调用 LLM；固定空说明或忽略说明、空正文、空 claims、`OMITTED`。
- assemble composer 继续使用有序 List，过滤 OMIT 后逐项保留；不得 Set 去重、take 截断、再次 LLM 改写或插入占位文本。（来源：K-locked-item-assembly-list-not-set）

### Invariant I-8: 既有 grounded 安全合同不回退

- Rule: operator-directed 版本不进入 claims/canonicalFactIds；既有有据版本仍完整执行 QA、claim、trust、action、stale 校验。
- Applies to: generateItem、validateLockedItem、groundedSections、canonicalFactIds。
- Violation consequence: 人工描述污染 QA 证据，或有据发送安全性下降。
- 来源: K-grounding-status-ui-only；K-compound-request-coverage-intent-atomic。

- 有据/部分有据版本继续使用 canonical claims、当前 QA answerBody、source/evidence version、claim/trust/action 校验。
- 新 operator-directed 分支不得进入 groundedSections、canonicalFactIds 或 QA association。
- source/evidence stale 仍返回 409；新处理方式不能绕过。

### Invariant I-9: 公共工作台没有发送或存储 authority

- Rule: 本阶段所有 API 只读上下文并返回内存结果，不写 DB/ES、不发送邮件。
- Applies to: workbench service/controller 及新增分支。
- Violation consequence: 绕过人工确认、评估或最终发送校验产生副作用。
- 来源: K-ai-generation-observability-not-send-gate。

- 本阶段不写 DB/ES、不调用 SMTP、不写 `mail_record` 或 `operator_action_log`。
- 生成状态或版本状态不成为纯人工发送 gate。（来源：K-ai-generation-observability-not-send-gate）

## 现状审计

### 本阶段读取的数据存储

- Schema/mapping: `mail_record`、`inbound_mail_processing`、`expert_contact` 和 `qa_rule` 均不改 schema；`qa_rule.answer_body` 继续是有据正文唯一来源。
- Write paths: 本阶段不新增或修改任何上述 store 的 insert/update/delete；工作台 generate/adjust/assemble 当前均为无持久化计算。
- Read paths:
  1. `TrustReplyWorkbenchService.resolveTrainingMail` — 按精确 `mail_record.id` 读取训练来源、联系人和历史上下文。
  2. `TrustReplyWorkbenchService.resolveLiveInbound` — 按精确 `inbound_mail_processing.id` 读取真实来源与联系人。
  3. `QaFactSelectionService.select` — 读取当前可用 QA 并构造 requestFacts/sendQaRuleIds。
  4. `AiReplyDraftService.buildEvidenceSnapshotForSelection` 与 assemble validators — 重读当前 enabled/nonblank `qa_rule.answerBody` 并生成 evidence version。
  5. `ReplySnippetService.resolveManualFrame`（经 locked composer）— 读取当前固定邮件 frame。
- Interaction points: controller locked DTO → workbench assemble；QA selection → initial/single version → locked claims；canonical assemble response → 后续训练/live 资格写路径。新说明字段必须穿过两套 controller mapping，但不得进入 QA store。

### `TrustReplyWorkbenchService.kt`

- 枚举 `TrustReplyItemHandling` 位于当前 21～26 行，只含有据、部分有据、待确认、省略；没有“按人工说明作答”。
- `TrustReplyItemVersion` 当前 147～158 行只保存说明 hash，不保存 request 原文、顺序或说明明文；后续无法安全归档操作员说明。
- `adjustItem` 当前 433 行起：除 OMIT 外统一调用 `AiReplyDraftService.generateItem`，说明最多 500 字符。
- `assemble` 当前 523 行起：重新解析 source/evidence/request matrix 并校验完整 key 集，这一 authority 应保留。
- `validateLockedItem` 当前 621 行起只有三类分支；无据项只能 ACK/OMIT。
- `materializeVersion` 当前 710 行起负责规范化和确定性 versionId，是新增字段的唯一写入点。
- `buildInitialItemVersions` 当前 921 行起会为所有 UNSUPPORTED 创建安全 ACK 初始版本，与“无据项默认由操作员决定”冲突。
- `allowedHandlings/recommendedHandling` 当前 1025/1041 行需要同步扩展，不能只改 enum。

### `AiReplyDraftService.kt`

- `generateItem` 当前 384 行起已统一处理 model、500 字符限制、取消、进度、超时和结果结构，是正确扩展 seam。
- 当前 414 行的 ACK 分支将说明标为“expression only”，失败后固定返回安全模板；这正是用户描述未变成实际回答的原因。
- 当前 620 行起的 `validateItemHandling` 复制了 handling 矩阵；必须与 workbench 的允许矩阵同一提交更新并由测试固定。
- 有据路径使用 grounded planner/materializer/validator；新 operator-directed 分支不能假装产生 QA claims。

### Controller DTO 写/读路径

- `TrustReplyWorkbenchController.kt:131-151` 手工把 HTTP locked item 映射为 domain；新增说明字段必须显式映射。
- `AiTrainingController.kt:286-324` 对训练评估再次手工映射 locked item；若漏映射，会在训练评估二次 assemble 时丢失说明并触发版本校验失败。
- `TrustReplyWorkbenchController.kt:199-209` 的 HTTP DTO 要追加 `operatorInstruction`；`requestIndex/requestText` 不接受客户端 authority，由 assemble 后的 response version 提供。

### 现有测试覆盖

- `AiReplyDraftServiceTest.kt` 已覆盖 generateItem 的 ACK、OMIT/有据行为，可增加 prompt 与 failure 测试。
- `TrustReplyWorkbenchItemFlowTest.kt` 已覆盖 handling matrix、versionId、OMIT、assemble、tamper 和 ACK，是本阶段主合同测试。
- `TrustReplyWorkbenchControllerTest.kt` 固定公共 API JSON 与错误映射。
- `AiTrainingSimulateTest.kt` 可固定训练评估 HTTP locked item 的说明 round-trip。

## 实现方案

### T0：执行前研究检查点

- Governs：I-1～I-9。
- Exact files: 本计划清单 1～8。
- 执行前重新 `rg` 全部 `TrustReplyItemHandling` when/valueOf、`TrustReplyItemVersion(`、`TrustReplyLockedItemRequest(`、`buildInitialItemVersions`、`operatorInstructionHash` 调用点。
- 若发现清单外生产构造器、第三套 locked DTO 或新的持久化/发送调用方，停止实施并修订计划；不得靠默认值静默越过未知写路径。
- 重新确认旧测试基线通过后，才写 T1 的失败测试。

### T1：先写失败测试固定新矩阵和版本元组

- Governs：I-1、I-4、I-5、I-6、I-7。
- Exact files: `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`。
- 在 `TrustReplyWorkbenchItemFlowTest.kt` 增加：
  - UNSUPPORTED allowed/recommended 精确值；其他 status 拒绝新 handling。
  - `ANSWER_FROM_OPERATOR_INPUT` 缺说明、超长说明、空回答、非空 claims、错误 generationKind、hash 不符、versionId 混装均返回稳定 422。
  - canonical version 返回 requestIndex/requestText/说明/hash；同输入 versionId 稳定，说明改变 versionId 改变。
  - FULL_DRAFT 不再产生 UNSUPPORTED safe version。
  - OMIT 最终正文中没有占位，重复非 OMIT 回答仍各出现一次。
- 测试先失败，再进入生产代码。

### T2：在单项生成服务新增 operator-directed 分支

- Governs：I-2、I-3、I-8、I-9。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`。
- 在 `AiReplyDraftService.generateItem` 的 OMIT/ACK 与 grounded 路径之间加入专用分支：
  1. 校验 status/handling 和非空说明。
  2. 构造只含目标问题、收件人上下文和人工说明的专用 prompt；将说明标识为 `operator-provided answer basis`，禁止模型使用自身知识补全。
  3. 复用现有 model、timeout、cancel、progress 与 LLM client。
  4. 规范化为收件人可见纯文本；拒绝空文本、内部标记和现有 action policy 违规。
  5. 成功返回 lockable `AI_GENERATED` + 空 claims；任何 fallback 状态返回 non-lockable。
- 在 `AiReplyDraftServiceTest.kt` 捕获发送给 fake client 的 messages，证明完整说明进入目标 prompt 且不再被标为“expression only”；同时证明其他 request 文本/说明不混入。

### T3：扩展 canonical version 与锁定 DTO

- Governs：I-4、I-5。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`、`src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`、`src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`。
- 在 domain version 末尾追加默认字段：

```kotlin
val requestIndex: Int = -1
val requestText: String = ""
val operatorInstruction: String = ""
```

- 在 locked request / 两个 HTTP DTO 追加 `operatorInstruction`，所有 controller mapping 显式传递。
- 在 assemble response 末尾追加默认 `requestedFactIds`，值取本次服务端 `selection.sendQaRuleIds`，用于训练评估与正式发送后的 authoritative replay。
- `materializeVersion` 从服务端 item 写 requestIndex/requestText；说明先 trim，再计算 hash，再参与 versionId。
- 对于从客户端回传的版本，assemble 忽略自报 requestIndex/requestText，重新以当前 item 生成 canonical response。

### T4：扩展 validate/assemble 语义

- Governs：I-1、I-4、I-5、I-7、I-8。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`。
- 为新 handling 增加独立校验分支；不调用 `canonicalizeClaims`，不加入 `groundedSections/canonicalFactIds`。
- `materializeVersion` 对新 handling 仅 trim 说明与回答，不生成 claims；重算 hash/versionId 后与客户端 versionId 精确比较。
- assemble 的 ordered answers 仍按 request index 取 canonical versions；OMIT 过滤，新 handling 正文与有据/ACK 正文一样逐字进入 composer。

### T5：停止初始化无据安全版本

- Governs：I-6。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`。
- `buildInitialItemVersions` 只遍历完整生成结果中真实存在的 `itemAnswers`；删除 UNSUPPORTED 自动 safe-template 分支。
- 保留 `ACKNOWLEDGE_PENDING` 单项生成及其安全 fallback，作为操作员主动选择的选项。
- 回归断言完整生成的 `unsupportedRequests`、coverage、readiness 不变，只是 `itemVersions` 不含无据默认版本。

### T6：更新两个 HTTP 写路径与合同测试

- Governs：I-4、I-5、I-9。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`、`src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`。
- `TrustReplyWorkbenchController` 的 assemble DTO 映射说明明文。
- `AiTrainingController` 的 evaluation DTO 映射同一字段，避免训练二次 assemble 丢失。
- Controller 测试固定新 enum、字段、422 code；禁止响应暴露 prompt、完整邮件 history 或 QA `answerBody`。

### T7：本阶段回归

- Governs：I-1～I-9。
- Exact files: 本计划清单 1～8，仅执行测试和 diff 检查。

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q \
  -Dtest=AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchControllerTest,AiTrainingSimulateTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

## 变更文件清单

| # | 文件 | 动作 | 目的 |
|---:|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 修改 | 新增按操作员说明生成的单项分支 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改 | 新 handling、canonical version、assemble 校验、移除无据初始版本 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | 修改 | 公共 locked item 说明字段 round-trip |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | 修改 | 训练评估 locked item 说明字段 round-trip |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改 | prompt、输出、失败与隔离测试 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 修改 | 新矩阵、版本、assemble、OMIT 主合同测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt` | 修改 | 公共 API JSON/错误合同 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | 修改 | 训练 evaluation DTO 映射合同 |

文件数：8；子系统：可信回复后端 1 个。超出清单的改动必须另立计划或先修改本计划并重新批准。

## 验收标准

- I-1: 参数化测试精确断言三种 status 的 allowed 集合；01 兼容期 UNSUPPORTED recommended 为 ACK，逐一拒绝非法新 handling；02 另测最终默认切换。
- I-2: 捕获 fake LLM messages，断言 operator-directed prompt 把完整说明标为内容依据；其他 handling 仍标为 expression only。
- I-3: 单项测试断言只返回目标 requestKey，沿用 model/TTL/cancel/progress；取消和 fallback 不产生 lockable version。
- I-4: 相同元组 versionId 稳定；任一说明/回答/handling/kind 变化均改变或拒绝；response 返回 canonical request 字段和 requestedFactIds。
- I-5: 缺项、重复、混装、hash 错误、说明丢失均返回预期 422 code，服务端不改写请求。
- I-6: FULL_DRAFT fixture 含 UNSUPPORTED 时 itemVersions 无该 key；GROUNDED/PARTIAL 仍有首版本。
- I-7: mock verify OMIT 不调用 LLM；raw/rendered 中不存在 request 文本、“省略”或占位。
- I-8: 既有 claim/trust/action/source/evidence stale 测试全部通过；operator-directed item 不贡献 canonicalFactIds。
- I-9: 测试依赖只验证 repository read；无 mail delivery、DB save、action log 或 ES 调用。
- Integration: evaluation HTTP DTO round-trip operatorInstruction 后 authoritative assemble 成功。

## 人工验收清单

### A-1: 无据说明生成真实回答
- 前置条件: 准备一封只有一个 UNSUPPORTED 问题的训练邮件；LLM 可用；通过 API 显式选择新 handling。
- 操作步骤: 1. 调 bootstrap；2. 用 `ANSWER_FROM_OPERATOR_INPUT` 和说明“回复对方：目前合作机构包括 A 大学和 B 研究院”调用 ADJUST_ITEM；3. 查看 response。
- 预期结果: allowedHandlings 含三个约定值，01 兼容期 recommended 仍为 ACK；显式新 handling 的 version.answerText 面向收件人表达 A 大学和 B 研究院，不是“我会核实后回复”；claims 为 `[]`。
- 覆盖: I-1、I-2、I-3、需求第 1 段。

### A-2: 版本完整性与防混装
- 前置条件: 使用 A-1 返回的 canonical version。
- 操作步骤: 1. 原样 assemble；2. 仅把 handling 改为 ACK 再 assemble；3. 仅删掉 operatorInstruction 再 assemble。
- 预期结果: 第 1 次 2xx 且正文逐字含 answerText；后两次均 422，错误 code 为 locked/version/ack 对应稳定业务码，无 500。
- 覆盖: I-4、I-5。

### A-3: FULL_DRAFT 不替无据项做决定
- 前置条件: 一封同时含 1 个 GROUNDED、1 个 PARTIAL、1 个 UNSUPPORTED 问题的邮件。
- 操作步骤: 调 FULL_DRAFT 并查看 itemVersions。
- 预期结果: 有据和部分有据各有 AI_GENERATED 首版本；UNSUPPORTED 的 requestKey 没有首版本，coverage 仍为 UNSUPPORTED。
- 覆盖: I-6、I-8。

### A-4: 省略零输出
- 前置条件: A-1 同类邮件。
- 操作步骤: 对唯一项以 OMIT 调整，再用返回版本 assemble。
- 预期结果: 调整不产生 LLM 请求；rawDraftText/renderedDraftText 中无问题原文、无“省略此项”、无占位段落。
- 覆盖: I-7。

### A-5: 失败与既有行为回归
- 前置条件: 可临时禁用 LLM；另准备既有 grounded/ACK fixture。
- 操作步骤: 1. 禁用 LLM 调 operator-directed；2. 恢复后分别生成 grounded 和 ACK；3. 修改 source/evidence 后重放旧版本。
- 预期结果: 第 1 步无可采用版本；grounded/ACK 行为与改前一致；旧版本在 stale 场景返回 409；全程无 SMTP、DB 或 ES 写入。
- 覆盖: I-3、I-8、I-9、must-not-change 1～4。
