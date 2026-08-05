# 可信回复原子事实与重复 Claim 防线开发计划

> 使用 `create-p` 编写。执行顺序：本计划先于 `trust-reply-durable-locks-and-assembly-generation.md`。

## 需求描述

### 可观察结果

可信回复面对同时询问“IP/作者署名/研究保密、正式合同、参与费用”的邮件时，每项只使用与该问题语义对应的原子事实：IP 项不夹带合同全文或费用，合同项不重复 IP 回答，费用项只回答费用政策。运行时 QA 管理不得为这四类原子 coverage 写入任意正文，只能使用 V82 定义的对应规范事实正文。若两个已锁定项仍引用同一 intent + 同一事实，服务端整合拒绝并返回稳定错误码，不静默删改用户已锁定正文。

### 必须保持不变

- `QaRequestExtractor` 的原邮件顺序、offset 与逐问题拆分行为不变。
- request → intents → factRuleIds → groundingStatus 矩阵不扁平化。
- keywords 仍只负责候选召回；LLM、fallback、自动回复和可信回复仍消费同一事实正文。
- `composeLockedItems` 继续逐字、按 canonical request 顺序拼接已锁定答案；不得 `distinct()`、模糊文本删重或再次调用 LLM。
- `sourceVersion`、`evidenceSetVersion`、versionId、claims 与服务端重物化校验不变。
- 人工回复、自动回复、训练评估和发送权限不扩大。
- 历史审计记录及已发送邮件不回写、不重算。
- 既有非原子复合规则（包括 Program overview）不会因本次运行时门禁被重分类、改写、禁用或阻止启用；其拆分另行计划。

### 范围外

- 不实现基于向量/LLM 的模糊语义去重。
- 不自动决定删除哪一个用户已锁定答案；冲突由用户重新生成或省略。
- 不修改前端 UI；错误码的人性化展示放入后续工作台计划。1
- 不拆分 Program overview 等其他复合规则；本轮只处理已确认的合同/IP/保密/费用链路。
- 不在本轮为线上其余 14 条空 `coverage_keys` 规则做全量语义回填；另行计划后才能取消兼容路径。
- 不改 `QaRequestExtractor`，不合并用户提出的不同问题。
- 不引入向量、LLM、关键词猜测或任意自然语言分类来判定正文语义；四类原子正文之外的运营可编辑事实模板另行计划。

## 关键不变量

### Invariant I-1：可发送事实必须原子化

- Rule：材料保密、费用政策、合同安排、签约前 IP 边界必须是四个独立规则；不得再次把“不收费”放入保密规则，或把完整合同条款放入 IP 规则。运行时以 `QaCoverageKeyCatalog.normalizeAndValidate` 的规范顺序识别以下四个受控 coverage 集合：`[confidentiality.materials]`、`[fees.policy]`、`[contract.party,contract.terms]`、`[ip.arrangements]`。每个集合只允许与 V82 对应的**完整规范 `answerBody` 字面量**；service 始终令 `replyBody=answerBody`。create 或 update 显式提交的 coverage 只要包含任一受控 key，就必须恰为上述一个集合且正文必须匹配；已存 coverage 恰为上述集合的 update（即使 `coverageKeys=null`）和 enable 也必须重验。任何其他已存复合 coverage 不做本轮语义分类或回写。
- Applies to：V82 迁移新增规则、`QaRuleManagementService.createRule/updateRule/setRuleEnabled`。
- Violation consequence：某项回答携带未被提问的句子，并在后续专门问题中再次出现。
- 来源：original

### Invariant I-2：keywords 召回，高风险 intent 由 coverage_keys 授权

- Rule：规则先按关键词召回；非空 `coverage_keys` 必须与当前 intent 的 required/alternative coverage 相交。对 `contract.terms`、`finance.compensation_structure`、`ip.arrangements`、`publication.authorship`、`confidentiality.research`、`confidentiality.materials`、`fees.policy`，空 coverage 也必须拒绝，不能标为 SUPPORTED。其他 legacy intent 暂保留空 coverage 的旧分配行为，直到后续完成全量回填。
- Applies to：`QaFactSelectionService.buildRequestFact`、`AiReplyIntentCatalog.assignRulesToIntents/resolveIntentEvidence`。
- Violation consequence：材料保密事实被误当作研究保密，或“不收费”被误当作薪酬结构。
- 来源：`K-qa-coverage-keys-management-write-boundary`

### Invariant I-3：费用意图与薪酬意图分离

- Rule：新增 `fees.policy` intent，aliases 包含 `fees/cost/costs/any fees/any costs/charge/charges`；这些 aliases 必须从 `finance.arrangements` 删除。`obligations` 单独出现不得自动归为薪酬或费用。
- Applies to：`AiReplyIntentCatalog.matchIntents`。
- Violation consequence：“是否收费”错误命中政府资金/企业薪酬规则。
- 来源：original

### Invariant I-4：保密意图不越权

- Rule：新增 `confidentiality.materials` intent 只回答申请材料保密；现有 `confidentiality.research` 继续表示研究数据/研究过程保密。材料保密规则不得满足 `confidentiality.research`；缺少专门依据时后者必须保持 MISSING。
- Applies to：intent catalog、coverage assignment、V82 coverage_keys。
- Violation consequence：把“我们妥善保管申请材料”误表述成研究合作保密制度。
- 来源：original

### Invariant I-5：运行时 QA 管理必须真实维护 coverage_keys

- Rule：create command 的 `coverageKeys=null` 写空，非空列表经 `QaCoverageKeyCatalog.normalizeAndValidate` 后写入；update command 的 null 保留旧值，空列表显式清空，非空列表校验后替换。I-1 的受控 coverage 校验在 save 前发生；enable 也须重验已存的受控规则。`answerBody` 与 `replyBody` 继续写同一规范正文。
- Applies to：`QaRuleManagementService.createRule/updateRule/setRuleEnabled`。
- Violation consequence：运营修改规则后 coverage 与正文漂移，硬门禁失效。
- 来源：`K-qa-coverage-keys-management-write-boundary`

### Invariant I-6：迁移不覆盖未知线上改动

- Rule：不修改 V38/V68 等历史迁移；新增 V82。部署前必须核对线上规则 17/34 的 subject、keywords、updatedAt 与 answer SHA-256。与计划基线不一致立即停止部署并人工合并；迁移只禁用基线匹配的旧复合规则，并用 `NOT EXISTS(reply_subject)` 插入四个原子规则。
- Applies to：V82、发布前检查。
- Violation consequence：覆盖运营后台在计划后新增的规则内容。
- 来源：`K-qa-rule-runtime-vs-migration-writes`

### Invariant I-7：跨项重复 Claim 必须拒绝，不得自动删句

- Rule：assemble 在 composer 前检查所有非 OMIT grounded claims。若不同 requestIndex 出现相同 `(intentKey, sourceRuleId)`，或两个非省略 `answerText` 经仅大小写/空白规范化后完全相同，返回 422 `TRUST_REPLY_DUPLICATE_CLAIM`；不产生 raw/rendered assembly。
- Applies to：`TrustReplyWorkbenchService.assemble`。
- Violation consequence：同一事实被重复回复；若直接删除，则破坏用户锁定版本和 versionId 语义。
- 来源：original + `K-locked-item-assembly-list-not-set`

### Invariant I-8：不同请求、不同 Claim 仍逐字保留

- Rule：未触发 I-7 的 locked items 必须继续按 canonical request list 全量输出；相似但 source/intent 不同的内容不得由 composer 猜测删除。
- Applies to：`TrustReplyWorkbenchService.assemble`、`AiReplyPointByPointComposer.composeLockedItems`。
- Violation consequence：合法的不同问题答案被误删。
- 来源：`K-locked-item-assembly-list-not-set`

## 现状审计

### `qa_rule` 表

- Schema：MySQL 5.7.41；`keywords TEXT NOT NULL`、`answer_body/reply_body TEXT NOT NULL`、`reply_policy VARCHAR(16)`、`enabled`、`coverage_keys VARCHAR(2000) NOT NULL DEFAULT ''`、`updated_at ON UPDATE CURRENT_TIMESTAMP`；无 reply_subject 唯一约束。
- 线上基线（2026-08-04 只读核对）：
  - id=17，subject=`Document confidentiality and no fees`，coverage 为空，AUTO/enabled，updatedAt=`2026-06-26 22:14:06`，answer SHA-256=`04027e0b2046f72f4bcc736a7436299f7880bdef74e321744c61bafebcbb0a37`。
  - id=34，subject=`Contract and IP arrangements`，coverage=`contract.party,contract.terms,ip.arrangements`，AUTO/enabled，updatedAt=`2026-07-16 18:03:00`，answer SHA-256=`3f142b13e0274db4d5b218f522ffe7071de7a501f6b5ab6324ccade424448f16`。
  - enabled 且非 NEVER 共 28 条，其中 14 条 coverage 为空；因此本轮禁止全局 blank→MISSING，只对 I-2 明列的高风险 intents 强制。
- Write paths：
  1. Flyway V3/V38/V40/V41/V44/V45/V46/V52/V57/V63/V65/V68/V70/V75/V76/V77/V79/V80/V81 — insert/update/delete/backfill。
  2. `QaRuleManagementService.createRule` — repository save，当前强制 `coverageKeys=""`，只执行通用 `QaFactBodyPolicy`。
  3. `QaRuleManagementService.updateRule` — repository save，当前保留旧 coverage，忽略 command，且只执行通用 `QaFactBodyPolicy`。
  4. `setRuleEnabled` — 直接 repository save 切换 enabled，不复验 coverage/body；这是 I-1 的第三条运行时绕过路径。
- Read paths：
  1. `QaFactSelectionService` — enabled/matchable rules、keywords、answerBody，构建 request-fact matrix。
  2. `QaMatchService` — 自动回复规则匹配与拼装。
  3. `AiReplyDraftService`、`AiReplyHighRiskClaimValidator`、`AiReplyPointByPointComposer`、`TrustReplyWorkbenchService` — evidence snapshot、生成、claims 验证和整合。
  4. `GroundedAutoReplyDecisionService`、`PendingMailOperationService`、`AutoMailReplyService` —预览/自动/人工回复决策。
  5. `QaRuleManagementService/listRules`、`InboundMailTagService`、`MailMonitoringService`、`MailComposeTemplateService` — 管理页、标签、监控及兼容读取。
- Interaction points：迁移/管理写入的 coverage 与正文被 selection、LLM、自动回复、可信回复共同读取；任何写读不一致都会跨四条回复路径扩散。

### 请求事实矩阵

- `QaRequestExtractor` 对样本邮件产生 7 个 request；同一复合问句可含多个 intent。
- `QaFactSelectionService` 对每个 request 独立筛 candidate rules，再通过 `assignRulesToIntents` 分配；当前 `resolveIntentEvidence` 只看 assignedRuleIds 是否非空，未执行 coverage hard gate。
- 线上样本 inbound 105 最新审计：request 5（IP/作者署名/研究保密）为 PARTIAL，evidence `[17,34]`；request 6（正式合同）再次用 `[34]`；request 7（费用）为 UNSUPPORTED。该矩阵直接解释最终三段交叉重复。
- 来源：`K-request-facts-not-flat-pool` 已复核，矩阵必须保留，修复点是规则授权而非扁平化或整段生成。

### 整合路径

- `TrustReplyWorkbenchService.assemble` 校验完整 request keys、逐项重物化 version、canonicalize claims、验证高风险 claims。
- 当前随后把所有非 OMIT `answerText` 交给 `composeLockedItems`；composer 使用 `joinToString("\n\n")`，明确不去重。
- `AiReplyPointByPointComposerTest` 当前专门断言完全重复答案保留两次。该 composer 契约不改；新增重复防线位于验证后、composer 前。

## 实现方案

### Task 1：新增 V82 原子规则迁移（I-1、I-3、I-4、I-6）

文件：`src/main/resources/db/migration/V82__split_trust_reply_atomic_facts.sql`

1. 以线上基线为发布 gate；迁移内禁用旧 subject `Document confidentiality and no fees`、`Contract and IP arrangements`，不删除历史行。
2. 用 `INSERT ... SELECT ... WHERE NOT EXISTS` 新增：
   - `Application material confidentiality`：仅材料保密、用途限制和可脱敏；coverage=`confidentiality.materials`。
   - `Participant fee policy`：仅“任何阶段不向参与者收费”；coverage=`fees.policy`。
   - `Contract arrangements`：仅签约主体、书面合同与签署前可审阅；coverage=`contract.party,contract.terms`。
   - `Pre-contract IP boundary`：仅签约前不发生权利转移、最终 IP 条款以未来书面协议为准；coverage=`ip.arrangements`。
3. 每条 `reply_body=answer_body`、keywords 互不交叉、reply_policy 继承 AUTO、category 从旧规则对应 category 获取。
4. 不给 `publication.authorship` 或 `confidentiality.research` 编造事实。

### Task 2：建立费用/材料保密意图并执行 coverage hard gate（I-2～I-4）

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt`

1. 新增 `fees.policy`、`confidentiality.materials` 定义；从 `finance.arrangements` 移除费用 aliases。
2. `assignRulesToIntents` 对非空 coverage 先用 `QaCoverageKeyCatalog.parseStored(rule.coverageKeys)` 过滤 eligible intents，再用既有 alias score 决定唯一目标 intent。
3. 新增固定 `COVERAGE_REQUIRED_INTENT_KEYS`；其中任一 intent 遇到 blank/non-intersecting coverage 均不得进入 evidence。其他 legacy intent 的 blank coverage 暂走旧分配逻辑，避免误伤 14 条线上规则。
4. `general.answer` 与未列入高风险集合的旧 intent 本轮不改变 blank coverage 行为；不得用 displayName/正文为高风险 intent 推断覆盖。
5. `factRuleIds`、`sendQaRuleIds` 仍从 request-intent evidence 顺序派生。

### Task 3：补齐运行时 coverage_keys 写路径与原子正文门禁（I-1、I-2、I-5）

文件：

- `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt`
- `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt`

1. create：`normalizeAndValidate(command.coverageKeys)` 后 serialize；null→空。
2. update：null→保留 existing；空 list→清空；非空→校验并替换。
3. `QaCoverageKeyCatalog` 定义四组 I-1 受控 coverage 与其 V82 规范正文。提供纯确定性校验：显式写入中包含任一受控 key 必须恰为一组且正文完全相等；已存 coverage 恰为一组时，正文也必须完全相等。非受控 legacy coverage 直接通过，不推断、不改写。
4. create/update 在 save 前执行该校验；`setRuleEnabled(..., true)` 对已存的受控 coverage 重验。违反时抛出稳定 `IllegalArgumentException`，不调用 repository save；disable 不增加新校验。
5. 保留 answerBody/replyBody 同源、reply policy、variants 禁止及 category 校验。

### Task 4：整合前增加窄重复防线（I-7、I-8）

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`

1. 在所有 locked item 完成现有身份/claims 校验后、调用 composer 前构建 `(requestIndex,intentKey,sourceRuleId,answerText)` 视图。
2. 仅拦截：
   - 跨 requestIndex 的同 `(intentKey,sourceRuleId)`；
   - 跨 requestIndex 的 answerText 经 `trim + whitespace collapse + lowercase` 后完全相同。
3. 命中返回 422 `TRUST_REPLY_DUPLICATE_CLAIM`；不返回半成品 assembly，不修改版本。
4. 不做 substring、Jaccard、embedding 或 LLM 去重。

### Task 5：测试（I-1～I-8）

文件：

- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`

覆盖：

1. 样本七问映射：IP request 只获 IP rule；合同 request 只获 contract rule；费用 request 获 fees rule；publication/research confidentiality 保持 missing。
2. 高风险 intent 的 keyword 命中但 coverage 不相交/为空时不得 SUPPORTED；legacy 非高风险 blank coverage 保持原行为。
3. create/update/null/empty/invalid coverage 的持久化语义；四类受控 coverage 的规范正文接受、正文错配/混合受控 coverage 拒绝、以及 disable→错误正文→enable 的无写入拒绝。
4. 同 intent+source 跨项与完全相同答案返回 duplicate error；不同 intent/source 的相似答案仍完整保留。
5. V82 静态断言：旧规则 disabled、四条 INSERT 有 NOT EXISTS、body/coverage 原子且不含跨主题句子。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V82__split_trust_reply_atomic_facts.sql` | 新增 | 禁用两条复合规则并新增四条原子规则 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | 修改 | 新增费用/材料保密 intent，修正 aliases 与 coverage 分配 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 修改 | coverage_keys 成为 evidence 硬门禁 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt` | 修改 | coverage 注册表、受控集合与规范正文门禁 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt` | 修改 | create/update/enable 维护 coverage 并执行原子门禁 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改 | assemble 前重复 claim/完全重复答案校验 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt` | 修改 | 费用 aliases 不再归入薪酬 intent |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 修改 | 原子规则与 coverage 选择测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt` | 修改 | coverage 写路径与原子正文门禁测试 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 修改 | 重复防线与 composer 保真回归 |

范围：10 文件；QA 事实选择 + 可信回复整合两个子系统。无前端/CSS/新表。

## 验收标准

- I-1：V82 中四条启用规则的正文与 coverage 一一对应；保密正文不含 fee，费用正文不含 confidentiality，合同正文不含 IP boundary，IP 正文不复制合同段。管理 service 只接受四个受控 coverage→V82 规范正文的精确映射；混合受控 coverage、正文错配、以及将错误已存受控规则 enable 均抛出异常且 repository `save` 未被调用；Program overview 等非受控 legacy 规则不被该门禁拒绝。
- I-2：高风险 intent 测试断言 keyword hit + coverage mismatch/blank → MISSING；coverage match → SUPPORTED；至少一个 legacy blank-coverage fixture 维持现有结果。
- I-3：`any costs or fees` 只命中 `fees.policy`，不产生 `finance.arrangements`。
- I-4：`research confidentiality managed` 在无专用 rule 时保持 MISSING；`keep my application materials confidential` 命中 `confidentiality.materials`。
- I-5：管理 service 对 create null/non-empty、update null/empty/non-empty/invalid、enable 受控规则复验全覆盖。
- I-6：发布前 SQL 查询的 id17/id34 baseline 必须与审计值一致；V82 不修改历史 migration，INSERT 全部幂等。
- I-7：重复 claim 或完全相同 locked answer 返回 HTTP 422 code；composer 未被调用。
- I-8：不同 claim 的相似答案仍按 canonical 顺序逐字出现；现有 composer duplicate preservation test 不删除。
- 回归：`mvn test`、`git diff --check` 通过；`AiReplyPointByPointComposer.kt` 无 diff。

## 人工验收清单

### A-1：发布前线上规则基线
- 前置条件：可只读查询生产 `qa_rule`。
- 操作步骤：查询 id 17/34 的 subject、keywords、updated_at、SHA2(answer_body,256)。
- 预期结果：分别等于 I-6 记录的两组实值；任一不同则停止部署。
- 覆盖：I-6、必须保持不变第 7 项。

### A-2：样本邮件事实矩阵
- 前置条件：使用 inbound 105 的 cleaned body 或复制同样七问到 AI 训练。
- 操作步骤：打开可信回复工作台，检查第 5、6、7 项依据与状态。
- 预期结果：第 5 项只显示 IP 依据且 publication/research confidentiality 为缺失；第 6 项只显示合同依据；第 7 项显示费用政策依据，不再 UNSUPPORTED。
- 覆盖：I-1～I-4、observable outcome。

### A-3：样本邮件整合正文
- 前置条件：A-2 已完成，逐项处理缺失内容并服务端整合。
- 操作步骤：阅读 IP、合同、费用三个连续段落。
- 预期结果：合同安排只出现一次；不收费只出现一次；IP 段不包含“不收费”；正文仍按原问题顺序。
- 覆盖：I-1、I-7、I-8、observable outcome。

### A-4：后台创建/编辑 coverage 与原子正文
- 前置条件：QA 管理页新建测试规则权限。
- 操作步骤：创建 coverage=`fees.policy` 且正文等于 V82 费用规范正文的规则；尝试将正文改为该正文加一条合同句；最后保持规范正文并显式清空该规则的 coverage。
- 预期结果：首条保存 coverage 为 `fees.policy`；错配正文请求被拒绝且原规则未变；显式清空后 coverage 为空，费用请求不再由该规则获得 SUPPORTED 证据。
- 覆盖：I-1、I-5、interaction qa_rule write→selection read。

### A-5：重复 Claim 拦截
- 前置条件：测试数据中两个 request 锁定相同 intent/source rule。
- 操作步骤：点击服务端整合。
- 预期结果：返回 `TRUST_REPLY_DUPLICATE_CLAIM`，无整合预览、无采用/发送；两个锁定版本原样保留。
- 覆盖：I-7、必须保持不变第 4/6 项。

### A-6：合法相似回答保留
- 前置条件：两个 request 使用不同 intent/source，但措辞相似。
- 操作步骤：锁定并整合。
- 预期结果：两段都出现且顺序与问题一致；服务端不进行模糊删重。
- 覆盖：I-8、必须保持不变第 1～5 项。

### A-7：自动/人工发送回归
- 前置条件：分别准备 QA 自动回复与人工可信回复测试邮件。
- 操作步骤：执行 dry-run/预览；再走人工采用但不点击发送。
- 预期结果：事实选择采用新 coverage；预览不写库不发送；可信回复采用后仍需人工发送。
- 覆盖：必须保持不变第 3/6 项、所有 interaction points。
