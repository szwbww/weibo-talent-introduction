# 可信回复工作台 01：摘要—事实唯一分配开发计划

> 使用 `create-p` 编写。总纲：`trust-reply-configurable-workbench-00-master.md`。执行顺序：本计划完成并独立验证后，再执行 `trust-reply-configurable-workbench-02-selectable-reply-frame.md`，最后执行 `trust-reply-configurable-workbench-03-two-page-workbench-ui.md`。

## 需求描述

### 可观察结果

可信回复工作台不再把事实作为整封回复的全局勾选池。服务端为原邮件中每个摘要/request 返回独立的事实列表；调用方可以按 `requestKey` 为每个摘要增删事实。一个事实 ID 在同一封回复的完整配置中最多出现一次：分配给摘要 1 后，不得同时分配给摘要 2。所有生成、锁定状态恢复、服务端整合、训练评估复验和正式发送复验都使用同一份摘要—事实矩阵。

### 必须保持不变

- `QaRequestExtractor` 继续按原邮件顺序提取 request；request 的 index、正文、intent 顺序和 `requestKey` 计算规则不改。
- `requestCoverage.factRuleIds` 继续表示该 request 的真实证据；`sendQaRuleIds`/`canonicalFactIds` 只表示审计并集，不得重新成为生成 authority。
- QA 事实有效性仍要求规则存在、enabled、非 `NEVER`、`answerBody` 非空、关键词命中当前 request，并能被当前 request 的 intent evidence 分配接受。
- `answerBody` 仍是唯一事实正文；`displayName` 及新增的前端展示字段不得参与 claim 校验。
- 已锁定版本仍由 `sourceVersion + evidenceSetVersion + requestKey + handling + answerText + claims + model + generationKind + operatorInstructionHash` 确定；客户端不得自行生成 versionId。
- `composeLockedItems` 的 canonical request 顺序和逐字保真行为不改。
- `trust_reply_workbench_state` 继续一 source 一行、乐观并发、30 天过期、256 KiB 上限；不写 active-only version、翻译、DOM 状态或 assembly 预览。
- `QaFactSelectionService.select(inboundText, selectedRuleIds, ...)` 的现有非工作台消费者行为不改；新矩阵入口必须独立，避免改变自动回复、旧预览和其他人工回复路径。
- 权限、外发门禁、审计记录及历史已发送邮件不改写。

### 范围外

- 本计划不实现前端双页 UI；仅提供服务端契约和兼容能力。
- 不实现回复片段选择；由计划 02 负责。
- 不做拖拽排序、事实搜索、事实推荐算法或跨邮件事实占用。
- 不允许通过 displayName、正文相似度、向量或 LLM 猜测事实应分配给哪个摘要。
- 不修改 `qa_rule`、`trust_reply_workbench_state` 的物理表结构，不新增 Flyway migration。
- 不做逐摘要局部保留旧 version；任何事实矩阵变化都会产生新的全局 `evidenceSetVersion`，旧锁定版本整体失效。
- 不删除 `requestedFactIds` 兼容字段；其退役需单独版本化计划。

## 关键不变量

### Invariant I-1：摘要—事实矩阵是选择 authority

- Rule：新契约 `requestFactSelections` 必须以 canonical `requestKey` 为键，完整表达每个 request 的事实 ID 列表；`requestCoverage.factRuleIds` 必须由服务端重算后的矩阵产生。`requestedFactIds` 和 `canonicalFactIds` 只能作为兼容输入或有序审计并集，不能在生成/整合时覆盖矩阵。
- Applies to：bootstrap、ADJUST_ITEM generation、state save/restore、assemble 及所有响应 DTO。
- Violation consequence：事实重新退化为扁平池，模型可跨摘要借用或重复同一事实。
- 来源：`K-request-facts-not-flat-pool`

### Invariant I-2：同一事实在一封回复内最多分配一次

- Rule：`requestFactSelections` 中所有 `factRuleIds` 的全局出现次数必须不超过 1；客户端重复、重复 requestKey 或服务端 canonicalization 后重复均返回 HTTP 422 `TRUST_REPLY_FACT_ALREADY_ASSIGNED`，不生成、不保存、不整合。
- Applies to：HTTP DTO 转换后的统一 selection resolver、状态恢复重验、assemble 最终重验。
- Violation consequence：同一批准事实在多个摘要中形成重复 claim 或相互矛盾的上下文。
- 来源：original + `K-locked-item-assembly-list-not-set`

### Invariant I-3：显式分配必须匹配指定摘要

- Rule：每个显式事实必须存在、enabled、非 `NEVER`、`answerBody` 非空，关键词必须命中被指定的 request，且最终出现在该 request 的 supported intent evidence/factRuleIds 中；只匹配邮件中其他 request 也视为无效。任一事实未被消费时整份配置返回 HTTP 422 `TRUST_REPLY_FACT_SELECTION_INVALID`。
- Applies to：工作台矩阵 selection、旧 flat selection 的兼容归一化。
- Violation consequence：浏览器注入有效但无关的 QA ID，把无关事实提升为当前问题的依据。
- 来源：`K-explicit-fact-selection-must-match-request`、`K-answerbody-source-exclusive`

### Invariant I-4：兼容 flat 输入也必须归一化为唯一矩阵

- Rule：仅收到旧 `requestedFactIds` 时，服务端按 canonical request 顺序，把每个事实分配给第一个“关键词命中且 intent evidence 接受”的 request；一旦消费即从剩余池移除。所有显式 ID 必须恰好消费一次。新旧字段同时出现返回 HTTP 422 `TRUST_REPLY_FACT_SELECTION_AMBIGUOUS`。
- Applies to：bootstrap、generation、state、assemble 的兼容入口。
- Violation consequence：旧客户端可绕过唯一分配，或新旧字段冲突时选择结果依赖参数优先级。
- 来源：original

### Invariant I-5：事实矩阵必须进入确定性 evidence 身份

- Rule：`evidenceSetVersion` 在既有事实状态版本之外，必须加入 canonical `requestKey -> ordered factRuleIds` 序列；相同事实并集但分配到不同摘要时 version 必须不同。输入只允许 rule ID、可用性、updatedAt、`answerBody` SHA-256 和 canonical mapping；不得加入 observed time。
- Applies to：bootstrap、adjustItem、state save/restore、assemble、locked version materialization。
- Violation consequence：事实重新分配后旧 versionId 仍被接受，claim 与摘要的绑定身份失真。
- 来源：`K-ai-reply-evidence-version-deterministic`

### Invariant I-6：工作台状态 schema 升级可兼容读取

- Rule：持久化 payload 升级为 `trust-reply-workbench-state-v2`，新增且仅新增一个共享 store 字段 `requestFactSelections`；写入只写 v2。读取 v1 时用其 `requestedFactIds` 经 I-4 归一化并重验，不能直接信任旧 flat 列表。未知 schema 返回 `INVALID`，source/evidence/mapping 漂移返回 `STALE`。
- Applies to：`TrustReplyWorkbenchStateStore.encodePayload/decodePayload`、bootstrap restore、PUT state。
- Violation consequence：升级后旧锁定状态全部不可读，或旧状态绕过新唯一性边界。
- 来源：`K-workbench-lock-replay-needs-dedicated-state-store`

### Invariant I-7：所有最终可采用路径携带同一矩阵

- Rule：工作台新路径只用逐项 `ADJUST_ITEM` 生成；其 bootstrap/generation/state/assemble 必须携带同一 canonical matrix。带 `requestFactSelections` 的 `FULL_DRAFT` 请求返回 HTTP 422 `TRUST_REPLY_OPERATION_INVALID`，避免把矩阵压平后生成可误用的整体版本。旧 flat `FULL_DRAFT` 暂保留兼容，但不能由计划 03 的新客户端调用。
- Applies to：`TrustReplyWorkbenchController.generateStream`、`TrustReplyWorkbenchService.generate/adjustItem/assemble`。
- Violation consequence：单项路径安全，整体生成路径却绕过矩阵并重新使用同一事实。
- 来源：`K-aggregate-generation-reuse-item-path`

## 现状审计

### `qa_rule` 数据源

- Schema：`id`、`category_id`、`keywords`、`match_mode`、`priority`、`reply_subject`、`reply_body`、`auto_reply_enabled`、`handoff_required`、`enabled`、`display_name`、`section_title`、`supersedes_children`、`coverage_keys`、`answer_body`、`reply_policy` 及 timestamps；本计划只读。
- Write paths：历史 Flyway V3/V17/V18/V38/V40/V41/V44/V45/V46/V52/V57/V63/V65/V68/V70/V75/V76/V77/V79/V80/V81/V82；运行时 `QaRuleManagementService.createRule/updateRule/deleteRule/setRuleEnabled`。本计划不修改这些路径。
- Read paths：`QaRuleManagementService`、`QaMatchService`、`MailComposeTemplateService`、`MailMonitoringService`、`GroundedAutoReplyDecisionService`、`PendingMailOperationService`、`InboundMailTagService`、`QaFactSelectionService`、`TrustReplyWorkbenchService`、`AiReplyPointByPointComposer`、`AiReplyDraftService`、`AiReplyHighRiskClaimValidator`。
- 本计划只改变 `TrustReplyWorkbenchService` 使用的新矩阵 selection 入口；其他读者继续使用原契约。

### 当前事实选择与 identity

- `QaFactSelectionService.select` 先提取全部 request，再让每个 request 从同一个 `promptPool` 筛 candidate；同一 rule 可进入多个 request。
- 显式 `selectedRuleIds` 只验证“至少匹配邮件中的某个 request”，未验证被指定摘要，因为当前没有指定摘要的输入结构。
- `TrustReplyWorkbenchService` 的 bootstrap/generation/state/assemble 均只接受 `requestedFactIds`；bootstrap 响应虽已有 `requestCoverage[].factRuleIds`，但请求端无法把它作为下一次调用的 authority。
- 当前 evidence snapshot 只由事实并集生成；把相同事实从 request A 移到 request B 不会改变 `evidenceSetVersion`。
- `requestKey` 由 sourceVersion、index、规范化 requestText、intentKeys 产生；fact assignment 不参与 requestKey，因此适合作为矩阵稳定键。

### `trust_reply_workbench_state` JSON store

- Physical schema：一 source 一行，`state_version`、`payload_json LONGTEXT`、`expires_at`、timestamps；unique `(source_type,source_id)`。
- Write paths：`TrustReplyWorkbenchStateStore.save` 的 insert/update、`delete`、`pruneExpired`；业务唯一入口为 `TrustReplyWorkbenchService.saveState`。
- Read paths：bootstrap 调 `load/decodePayload`，随后重验 source/evidence/locked item。
- 当前 v1 payload：`schemaVersion/sourceVersion/evidenceSetVersion/requestedFactIds/selectedModel/lockedItems`。
- Interaction：事实选择决定 evidence version；evidence version进入 locked versionId；state restore 与 assemble 再用 flat IDs 重算选择。任何一处不改都会形成双 authority。

### HTTP 与下游

- `TrustReplyWorkbenchController` 的 bootstrap/generation/assemble/state HTTP DTO 均只含 `requestedFactIds`。
- `AiTrainingController`、`app.js` 的训练评估和正式发送 assembly snapshot 也读取 flat 字段；这些在计划 03 统一透传新矩阵，计划 01 先保持 domain 默认值兼容以便后端先部署。
- `PendingMailOperationService` 与 `UnmatchedInboundMailController` 直接携带 `TrustReplyAssembleRequest`；新增字段必须有默认值，避免服务端先部署时破坏现有构造点。

## 实现方案

### Task 1：定义矩阵 domain/HTTP 契约（I-1、I-2、I-4）

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`

1. 新增 `TrustReplyRequestFactSelection(requestKey: String, factRuleIds: List<Long>)` 与对应 HTTP DTO。
2. 在 bootstrap/generation/save-state/assemble request 增加 nullable `requestFactSelections`；在 bootstrap response、saved state、assemble response 增加 canonical `requestFactSelections`。所有 domain 新字段提供默认值，保持现有 Kotlin 构造点可编译。
3. `TrustReplyRequestCoverage.factRuleIds` 保留；`suggestedFactIds/canonicalFactIds/requestedFactIds` 保留为有序并集兼容字段，并在 KDoc/计划中明确非 authority。
4. `TrustReplyRuleMetadata` 增加只读 `answerBody`，供计划 03 的事实选择器展示；该字段不得进入匹配、identity 或 claim 校验。
5. 统一校验空/重复/未知 requestKey、非正数 rule ID、新旧字段并存；映射到稳定错误码，不把 `IllegalArgumentException` 文本暴露给前端。

### Task 2：新增工作台专用唯一分配算法（I-1～I-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt`

1. 保留现有 `select` 公共行为；新增工作台专用入口，输入为按 canonical request index 的显式 rule IDs，或 legacy flat IDs，或 null 自动选择。
2. 显式矩阵模式：
   - 先全局检查 rule ID 唯一；
   - 每个 request 只以自己分配的 rules 作为 `promptPool`；
   - 调用既有 `buildRequestFact` 完成 keyword、intent、coverage、research context 计算；
   - 结果 `factRuleIds` 必须与 canonicalized 显式列表一致，否则整份选择无效。
3. legacy flat/自动模式：按 request 原始顺序处理 remaining rules；当前 request 成功消费的 `factRuleIds` 从 remaining 移除，后续 request 不再可见。显式 flat 模式结束后 remaining 必须为空。
4. `sendQaRuleIds` 从 canonical requestFacts 的事实列表生成有序并集；不得把并集再次传回每个 request。
5. 空列表允许表示该摘要无事实；整个矩阵可以全部为空，状态按既有 intent 聚合得到 UNSUPPORTED，不伪造 fallback 事实。

### Task 3：建立统一 canonical resolver 与 mapping-sensitive version（I-1、I-5、I-7）

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`

1. 把当前两套 `resolveCanonicalSelection*` 收口为同一输入归一化层：先解析 canonical requestKeys，再解析 matrix/legacy/auto，最后返回 `ResolvedQaRules + canonical requestFactSelections + evidenceSetVersion`。
2. evidence version 计算：
   - 先复用 `AiReplyDraftService.buildEvidenceSnapshotForSelection(sendQaRuleIds)` 的事实状态版本；
   - 再对 canonical `requestKey + ordered factRuleIds` 序列编码；
   - `SHA-256(baseEvidenceVersion + NUL + mappingCanonical)` 作为工作台最终 `evidenceSetVersion`。
3. bootstrap、adjustItem、saveState、restoreSavedState、assemble 全部调用同一 resolver；OMIT 快速路径的本地事实版本算法也必须加入同一 mapping canonical，保证与正常路径一致。
4. adjustItem 只把目标 request 的 `RequestFactItem` 传给 `generateItem`；claims 不得引用其他 request 的 rule。
5. 带矩阵的 `FULL_DRAFT` fail closed；计划 03 的批量“生成并整合”继续复用逐项生成与冻结 allowlist。
6. 事实矩阵发生任何变化时新 evidence version 使全部旧 versionId/state/assembly 失效；返回既有 `TRUST_REPLY_EVIDENCE_STALE`。

### Task 4：升级 durable state 为 v2（I-2、I-5、I-6）

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt`

1. `SCHEMA_VERSION` 升为 `trust-reply-workbench-state-v2`；`TrustReplySavedStatePayload` 新增唯一共享字段 `requestFactSelections`，保留 `requestedFactIds` 作为兼容审计并集。
2. encode 只写 v2；decode 明确区分：
   - v2：反序列化后交业务 resolver 重验；
   - v1：反序列化 legacy payload，由业务层按 I-4 归一化；
   - 未知/破损：返回 null，bootstrap 标 `INVALID`。
3. PUT state 可临时接受 request schema v1/v2，但服务端写盘和响应一律为 v2 canonical 语义；计划 03 上线后客户端只发 v2。
4. restore 比较 sourceVersion、mapping-sensitive evidence version、canonical mapping 和 locked snapshots；任何事实失效/重分配返回 `STALE`，不得部分接受。
5. 物理 SQL、乐观锁、delete、prune、payload size 行为不改。

### Task 5：测试全部读写路径与兼容边界（I-1～I-7）

文件：

- `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStoreTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt`

覆盖：

1. 同一规则同时命中两个 request 时，auto/legacy 只分配给第一个可接受 request；显式重复跨摘要返回 `TRUST_REPLY_FACT_ALREADY_ASSIGNED`。
2. 事实只匹配其他 request、disabled、NEVER、空 answerBody、未进入 supported evidence 时返回 `TRUST_REPLY_FACT_SELECTION_INVALID`。
3. 缺失/重复/未知 requestKey、矩阵不完整、新旧字段并存、空摘要列表的契约。
4. 相同事实并集换绑 request 后 evidence version 与 versionId 改变；完全相同输入重复计算不变。
5. bootstrap→adjust→save→restore→assemble 全程返回并使用相同 canonical matrix；assemble 不接受被篡改的 flat 并集。
6. v1 state 可迁移恢复、v2 可恢复、未知 schema INVALID、映射变化 STALE、并发和 payload 上限回归。
7. 带矩阵的 FULL_DRAFT fail closed；ADJUST_ITEM 正常。
8. metadata 的 answerBody 仅出现在展示响应，不参与 displayName-based evidence acceptance。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 修改 | 新增工作台专用矩阵选择与唯一消费 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改 | 新 domain 契约、统一 resolver、mapping-sensitive identity |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt` | 修改 | v1/v2 decode 与 v2 canonical 写入 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | 修改 | HTTP 矩阵 DTO 与稳定错误边界 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 修改 | 唯一分配、显式匹配、空列表测试 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | 修改 | bootstrap/state/identity/兼容测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 修改 | adjust/assemble 全链路矩阵测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStoreTest.kt` | 修改 | v1/v2 codec、并发和限制回归 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt` | 修改 | HTTP 转换、互斥字段、错误码测试 |

范围：9 个文件；QA 选择 + 可信回复后端两个子系统；`trust_reply_workbench_state` 每计划仅新增一个 JSON 字段；无数据库 migration、无前端。

## 验收标准

- I-1：bootstrap 对每个 canonical request 返回事实列表；后续 generation/state/assemble 的 canonical mapping 完全一致，扁平并集只用于响应/审计。
- I-2：任一 rule ID 跨 request 重复时所有写/生成/整合入口均返回 422 `TRUST_REPLY_FACT_ALREADY_ASSIGNED`，stateStore/composer/LLM 均未调用。
- I-3：显式事实只有匹配指定 request 且进入 supported evidence 才可接受；仅匹配其他 request 必须拒绝。
- I-4：旧 flat 输入确定性归一化且每条只消费一次；新旧字段并存返回 `TRUST_REPLY_FACT_SELECTION_AMBIGUOUS`。
- I-5：同并集不同绑定产生不同 evidenceSetVersion；相同绑定与未变事实重复 bootstrap 版本相同。
- I-6：v1 durable state 经重验可恢复为 v2 语义；未知 schema INVALID；stale mapping 不恢复 locked items。
- I-7：新矩阵客户端无 FULL_DRAFT 路径；逐项生成/补齐/整合均使用同一矩阵。
- 回归：`QaFactSelectionService.select` 的既有非工作台测试不变；`node --test src/test/js/*.test.js` 在计划 01 后仍通过旧前端；`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`、`git diff --check` 通过。

## 人工验收清单

### A-1：每个摘要展示独立事实响应

- 前置条件：选择一封包含至少 3 个问题、4 条可匹配事实的训练邮件。
- 操作步骤：调用 bootstrap，检查 `requestCoverage` 与 `requestFactSelections`。
- 预期结果：每个 requestKey 只对应自己的 facts；全部事实 ID 全局无重复；canonicalFactIds 等于按摘要顺序的并集。
- 覆盖：I-1、I-2。

### A-2：同一事实重复分配被拒绝

- 前置条件：取一个可匹配两个摘要的事实 ID。
- 操作步骤：在两个 requestKey 的 `factRuleIds` 中同时提交该 ID 到 bootstrap、state 和 assemble。
- 预期结果：均返回 422 `TRUST_REPLY_FACT_ALREADY_ASSIGNED`；无状态写入、无生成、无整合正文。
- 覆盖：I-2。

### A-3：事实分配给错误摘要被拒绝

- 前置条件：合同事实和费用摘要同时存在。
- 操作步骤：把合同事实只分配给费用摘要。
- 预期结果：返回 `TRUST_REPLY_FACT_SELECTION_INVALID`；不会把合同事实显示成费用依据。
- 覆盖：I-3。

### A-4：换绑使旧版本失效

- 前置条件：摘要 1 已生成并锁定一个版本。
- 操作步骤：释放其事实并分配到摘要 2，再用旧 evidence/version 请求 state 或 assemble。
- 预期结果：新 bootstrap 的 evidenceSetVersion 已变化；旧请求返回 `TRUST_REPLY_EVIDENCE_STALE`，旧锁定版本不恢复。
- 覆盖：I-5、I-6。

### A-5：旧客户端兼容

- 前置条件：使用只发送 `requestedFactIds` 和 state schema v1 的旧请求 fixture。
- 操作步骤：bootstrap、锁定、保存、刷新恢复、assemble。
- 预期结果：服务端确定性归一化为唯一矩阵；响应包含 canonical matrix；最终事实不重复；写盘 payload 为 v2。
- 覆盖：I-4、I-6。

### A-6：既有非工作台路径回归

- 前置条件：自动回复预览、旧 AI 草稿、邮件模板各准备一条基线。
- 操作步骤：执行既有预览/生成，不使用工作台矩阵字段。
- 预期结果：`QaFactSelectionService.select` 的事实选择、reply policy、正文和 readiness 与变更前一致。
- 覆盖：必须保持不变第 8 项。

### A-7：request identity、locked 顺序、权限与历史回归

- 前置条件：固定一封两问邮件、两个 canonical locked answers；准备无工作台权限账号和一条历史已发送邮件记录。
- 操作步骤：重复 bootstrap 两次并比对 request index/key；用两项 locked answers assemble；用无权限账号请求接口；查询历史发送记录和审计。
- 预期结果：两次 request 顺序/key 完全相同；raw 中两个 locked answer 按原问题顺序逐字各出现一次；无权限请求仍返回原 401/403；历史正文、关联 QA IDs 和审计行数/内容无变化。
- 覆盖：必须保持不变第 1、5、6、9 项。
