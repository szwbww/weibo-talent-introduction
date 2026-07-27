# AI 回复问题—依据矩阵

## 需求描述

Observable outcome：每个专家请求按原顺序携带自身 QA 依据和 `GROUNDED/PARTIAL/UNSUPPORTED` 状态；两入口响应提供只读 requestCoverage 供验收，生成层不再面对扁平事实池。  
What must NOT change：自动 `match()`；现有 gapItems API 字段；sendQaRuleIds 审计；研究依据仍只读现有画像。  
Out of scope：正文排版、fallback 文案、前端展示矩阵、数据库字段。

## 关键不变量

### Invariant I-1: 一请求一记录且顺序稳定
- Rule: `RequestFactItem` 与 `CompositionSuggestResult.gapItems` 一一对应、索引从 1 开始、保持专家邮件顺序；URL-only 项已由现有 tokenizer 排除。
- Applies to: `resolveQaRules`。
- Violation consequence: 回复错序或漏项。
- 来源: K-gap-items-compose-only / K-url-query-question-tokenizer

### Invariant I-2: 依据只来自该请求候选
- Rule: 非研究项 factRuleIds=`gapItem.candidateRuleIds ∩ promptRuleIds`；不得把全局匹配全集复制到每项。研究项依据由 context warning 判定，不伪造 QA id。
- Applies to: matrix 构建、prompt consumer。
- Violation consequence: 合同事实被用于公司地址问题。
- 来源: original

### Invariant I-3: 状态语义固定
- Rule: `UNSUPPORTED`=factRuleIds 为空或研究画像不足；`PARTIAL`=factRuleIds 非空，且请求命中固定细节词 `deliverables/full name/registered location/exact/full terms/financial arrangements` 中至少一项，而所有对应 rule 的 subject+body 均不含该词；其余有依据项为 `GROUNDED`。不得用模型判断状态。
- Applies to: internal enum/coverage metadata。
- Violation consequence: “有一条泛化规则”被误报成完整覆盖。
- 来源: original

### Invariant I-4: 发送审计隔离
- Rule: matrix 可重复引用同一 rule；`sendQaRuleIds` 仍只取真实 suggested/explicit ids，去重与顺序语义不改；无匹配 prompt fallback 全集不得进入 send ids。
- Applies to: `ResolvedQaRules`、controller response。
- Violation consequence: mail_record_qa_rule 关联无关规则。
- 来源: K-ai-reply-prompt-vs-send-rule-ids

### Invariant I-5: 不影响自动路径
- Rule: 只扩展 suggestion/AI 内存结构；`detectGap/match/applySupersede` 不读取 matrix。
- Applies to: QaMatchService 自动入口。
- Violation consequence: AI 结构需求改变自动转人工。
- 来源: K-gap-items-compose-only / K-draft-supersede-separate-auto

### Invariant I-6: 覆盖元数据只读且双入口一致
- Rule: 两 response 以加法字段 `requestCoverage` 返回 index/requestText/status/factRuleIds；它只用于诊断，不写库、不进入发送 payload，两个入口同值。
- Applies to: simulate/aiReplyTurn response。
- Violation consequence: matrix 无法黑盒验收或影响外发审计。
- 来源: original

## 现状审计

### `CompositionSuggestResult` / `ResolvedQaRules`（内存 DTO）
- Write paths: `QaMatchService.suggestComposition` 写 `GapItem(text,candidateRuleIds)`；`AiReplyDraftService.resolveQaRules` 只抽 `requestItems`，丢失逐项 candidate ids。
- Read paths: AI mode/coverage/prompt；前端组装台读取 gapItems。
- Interaction points: promptRuleIds 与 sendQaRuleIds 已分离；matrix 必须消费前者但不得回写后者。（来源: K-ai-reply-prompt-vs-send-rule-ids）

## 实现方案

### T1：定义内部 matrix 契约（I-1 至 I-4）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- 新增 internal enum `RequestGroundingStatus` 三值。
- 新增 internal data class `RequestFactItem(index, requestText, factRuleIds, status)`。
- `ResolvedQaRules` 用 `requestFacts` 替代孤立 `requestItems`；兼容 coverage 字段由 matrix 派生。

### T2：按 gap item 构建（I-1/I-2/I-3/I-4/I-5）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- 非研究项交集计算；研究项只看 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`。
- PARTIAL 严格按 I-3 固定细节词与对应 rule subject+body 的词面覆盖计算；常量和大小写/空白归一化受测。
- requestCount/groundedRequestCount/unsupportedRequests 从 matrix 统一派生。

### T3：双入口增加只读 coverage（I-4/I-6）
文件：
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`

- 两 response 增加 requestCoverage；DTO 映射只读 result.requestFacts。
- 不修改 request DTO、发送接口或数据库。

### T4：矩阵测试（I-1 至 I-6）
文件：
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

- 使用本次专家邮件，断言 7 个自然语言请求顺序、各自 QA ids、研究项 warning、无 URL 片段。
- 一个 rule 可服务两项但 send ids 只出现一次/保持原序。
- 显式 qaRuleIds 时 matrix 只使用交集。
- 自动 match 回归逐字段不变。
- 两 endpoint requestCoverage 逐字段相同且不写库。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | matrix DTO/构建/coverage 派生 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | simulate requestCoverage |
| `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | mailbox requestCoverage |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 逐项映射测试 |
| `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt` | suggestion/自动回归 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | simulate DTO 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | mailbox DTO 测试 |

## 验收标准

- I-1：完整专家邮件 matrix 顺序与邮件一致，无 URL 项。
- I-2：每项 fact ids 是候选与 prompt ids 交集。
- I-3：三状态测试逐一覆盖，完全确定性。
- I-4：sendQaRuleIds 与改动前断言一致。
- I-5：自动 match 测试通过且相关方法 diff 无语义改动。
- I-6：两 API coverage 相同，模拟/邮箱无新增 save。
- 命令：`mvn -Dtest=AiReplyDraftServiceTest,QaMatchServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest test`。

## 人工验收清单

### A-1: 多问题覆盖顺序
- 前置条件: 使用本次专家完整邮件。
- 操作步骤: 分别调用训练模拟与邮箱 AI 回复接口，查看 JSON 的 requestCoverage。
- 预期结果: 公司、项目、匹配、职责、合同/财务/IP、流程按原顺序出现；URL 不出现；两个数组逐字段相同。
- 覆盖: I-1/I-2/I-6

### A-2: 审计不扩大
- 前置条件: 草稿仅命中公司/项目两类规则。
- 操作步骤: 采用草稿并发送测试邮件，查看 mail_record_qa_rule。
- 预期结果: 只关联真实命中规则，不关联 prompt fallback 全集。
- 覆盖: I-4/I-5

## 修正记录

| 日期 | 修正 | 理由 | 来源 |
|---|---|---|---|
| 2026-07-12 | I-2「prompt consumer」与 Observable「生成层不再面对扁平事实池」延后到 phase 3 | 总索引跨计划接口：计划 3 是唯一正文消费者；本计划 Out of scope 含正文排版/fallback | fix-1 |
| 2026-07-12 | RequestGroundingStatus/RequestFactItem 改为 public | 需挂在 AiReplyDraftResult 供双入口 DTO 映射 | fix-1 |
