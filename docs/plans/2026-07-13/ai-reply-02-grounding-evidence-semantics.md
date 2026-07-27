# AI 回复依据矩阵：真实依据与研究双边判断

## 需求描述

Observable outcome：每个请求的 `GROUNDED/PARTIAL/UNSUPPORTED` 由该请求实际可读取的审核 QA 正文确定；研究匹配问题必须同时具备现有专家研究画像和项目范围 QA 依据，不能仅因画像存在就宣称可回答。

What must NOT change：`sendQaRuleIds/promptRuleIds` 分离、QA 自动匹配、训练知识定向注入、画像只读查询、两个 response 的现有字段名称、PARTIAL 词表的既有细节覆盖语义。

Out of scope：LLM JSON 输出、正文内部状态清理、前端警告文案、数据库迁移。

## 关键不变量

### Invariant I-1: factRuleIds 代表可读取审核正文
- Rule: `factRuleIds` 只能包含 `candidateRuleIds ∩ promptRuleIds` 中 repository 可找到且 `replyBody` 非空的 id；不存在/空正文的 id 不能让状态变为 GROUNDED。
- Applies to: 所有非研究与研究 RequestFactItem。
- Violation consequence: response 宣称“有依据”，生成阶段却拿不到事实。
- 来源: original

### Invariant I-2: 研究匹配必须双边有据
- Rule: `requiresResearchContext=true` 的请求，只有“无 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT` warning”且至少一条有效 fact rule 同时满足时，才允许 GROUNDED/PARTIAL；任一侧缺失均 UNSUPPORTED。
- Applies to: `areas of expertise fall within scope`、research profile/background、Scholar/Scopus 语境。
- Violation consequence: 系统只复述专家画像，未比较项目范围却确认适配。
- 来源: K-ai-reply-profile-absence-warning

### Invariant I-3: 研究请求不丢 candidate QA
- Rule: 研究请求与普通请求使用同一 candidate∩prompt 计算；禁止研究分支强制 `factRuleIds=emptyList()`。
- Applies to: `resolveQaRules()`。
- Violation consequence: 项目范围/企业方向依据被丢弃，模型只能借其他问题事实。
- 来源: K-request-facts-not-flat-pool

### Invariant I-4: 状态完全确定性
- Rule: 判定顺序固定：研究画像不足 → UNSUPPORTED；有效 fact ids 为空 → UNSUPPORTED；命中 `PARTIAL_DETAIL_PHRASES` 且事实未覆盖该细节 → PARTIAL；否则 GROUNDED。模型、few-shot、mailHistory 不参与状态判定。
- Applies to: `RequestGroundingStatus` 与所有派生计数。
- Violation consequence: 同一邮件因模型不同得到不同“依据充足”结果。
- 来源: original

### Invariant I-5: send 审计范围隔离
- Rule: 过滤无效 fact id 不得改写 `sendQaRuleIds` 或 `promptRuleIds`；前者仍由真实 suggested/explicit ids 产生，后者仍可使用现有 fallback 语义。
- Applies to: `ResolvedQaRules`。
- Violation consequence: AI 诊断修复扩大或缩小外发 QA 审计。
- 来源: K-ai-reply-prompt-vs-send-rule-ids

### Invariant I-6: 研究标记随 item 保存
- Rule: `RequestFactItem` 新增加法字段 `requiresResearchContext:Boolean=false`；它只作为生成期内部语义，不进入 controller `RequestCoverageItem`、数据库或发送 payload。
- Applies to: matrix 构建与计划 3 materializer。
- Violation consequence: 后续 composer 重新用不同规则猜测研究请求。
- 来源: original

### Invariant I-7: 覆盖派生兼容
- Rule: `unsupportedRequests` 只含 UNSUPPORTED；`groundedRequestCount` 暂保持 GROUNDED+PARTIAL 的既有“有部分事实”计数，PARTIAL 精确状态由 `requestCoverage.status` 表达。
- Applies to: 两个 API response。
- Violation consequence: 现有覆盖比例与前端兼容性在本计划被无意改变。
- 来源: original

## 现状审计

### QA rule 只读事实源
- Schema/mapping: 本计划不改数据库；事实由 `QaRuleRepository.findById()` 读取 `qa_rule.reply_body`。没有写路径。
- Write paths: 无。本计划只构建请求内存矩阵。
- Read paths:
  1. `AiReplyDraftService.resolveQaRules()`（当前 397-444）读取 composition、prompt ids、QA body。
  2. `buildGroundedUserContent()` 依 requestFacts 再读 QA body进入 prompt。
  3. `AiReplyPointByPointComposer.joinFacts()` 依 requestFacts 再读 QA body进入 fallback。
  4. 两个 controller 把 index/text/status/factRuleIds 映射到 `requestCoverage`。
- Interaction points: matrix id 必须在 prompt/fallback 二次读取时仍存在；发送审计 ids 与事实 ids 是不同语义。（来源: K-ai-reply-prompt-vs-send-rule-ids）

### 专家研究画像只读源
- Schema/mapping: `AiReplyContextService` 从现有 ES profile 的 researchFields/keyword/disciplineCategory/recentWorkTitles 判断研究信息是否充足；缺失/异常写 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`，不触发 enrichment。
- Write paths: 无。
- Read paths: 训练模拟与收发件 controller 均先 build context，再把 `profileText/contextWarnings` 传入 `generate()`。
- Interaction points: warning 是 matrix 的画像侧门槛；QA candidate 是项目侧门槛，两者缺一不可。（来源: K-ai-reply-profile-absence-warning）

### 当前缺陷证据
- `AiReplyDraftService.kt:414-428` 的研究分支直接构造 `factRuleIds=emptyList()`，即使 `GapItem.candidateRuleIds` 有项目/企业规则。
- `isPartialCoverage()` 在 repository 缺 id 时返回 false，调用者随后把该 item 判为 GROUNDED。
- 现有测试 `research request uses warning only and never invents factRuleIds` 固化了错误语义，必须改为双边依据契约。

## 实现方案

### T1：扩展 RequestFactItem 内部语义（I-2/I-3/I-6）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- 给 `RequestFactItem` 增加 `requiresResearchContext:Boolean=false`，默认值保证现有测试 fixture/调用方源码兼容。
- 不扩展 `RequestCoverageItem`；controller 映射字段保持原样。

### T2：先解析真实规则，再判定状态（I-1/I-2/I-3/I-4/I-5/I-7）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- 每项先计算 candidate∩prompt，保持原次序并去重。
- 对交集逐 id 读取 rule；只保留存在且 replyBody 非空的 id 为 `validFactRuleIds`。
- 研究/普通请求都把 `validFactRuleIds` 写入 item；研究 flag 只增加画像门槛，不清空 facts。
- 按 I-4 顺序判定状态；`isPartialCoverage()` 改为只接收已验证存在的 rules 或 ids，移除“缺 rule 当完整覆盖”的分支。
- `sendQaRuleIds/promptRuleIds` 的赋值块不改。

### T3：矩阵语义测试（I-1 至 I-7）
文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

- 研究画像充足 + 项目 QA 存在：factRuleIds 保留，GROUNDED，requiresResearchContext=true。
- 研究画像充足 + 项目 QA 缺失：UNSUPPORTED。
- 项目 QA 存在 + 画像不足 warning：UNSUPPORTED，但 factRuleIds 仍保留供前端诊断，生成层不得使用 unsupported facts。
- repository missing/blank body：从 factRuleIds 排除并 UNSUPPORTED。
- deliverables 泛化规则仍 PARTIAL；明确包含 deliverables 的规则仍 GROUNDED。
- 显式 qaRuleIds、prompt fallback、send ids 断言与改动前一致。

### T4：双入口 response 回归（I-6/I-7）
文件：
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

- 使用相同 `AiReplyDraftResult.requestFacts`，断言两个 response 继续只返回 index/requestText/status/factRuleIds。
- 研究 flag 不出现在 JSON；PARTIAL/UNSUPPORTED 保持逐项状态。
- 不新增 save/更新调用。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 研究 flag、有效事实过滤、双边状态判定 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 矩阵状态与审计隔离测试 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | 训练响应兼容回归 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | 收发件响应兼容回归 |

## 验收标准

- I-1：不存在/空 replyBody id 不出现在 factRuleIds，状态不能 GROUNDED。
- I-2：研究画像与项目 QA 四种组合按真值表返回预期状态。
- I-3：研究 item 在 QA 存在时保留 candidate∩prompt ids。
- I-4：状态判定不调用 LLM；三状态单测齐全。
- I-5：sendQaRuleIds/promptRuleIds 回归断言逐字不变。
- I-6：requiresResearchContext 不出现在两个 JSON response。
- I-7：PARTIAL 仍计入 groundedRequestCount，unsupportedRequests 只含 UNSUPPORTED。
- 定向命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest test`。

## 人工验收清单

### A-1: 研究双边依据完整
- 前置条件: 专家 ES 画像含 researchFields；启用一条命中 `within the scope/enterprise projects` 且正文非空的 QA rule。
- 操作步骤: 1. 生成本次邮件模拟回复；2. 查看 requestCoverage 第 1 项。
- 预期结果: 第 1 项 status=`GROUNDED` 或按细节词为 `PARTIAL`；factRuleIds 至少 1 个；正文可在后续计划中用于匹配度回答。
- 覆盖: I-1/I-2/I-3

### A-2: 缺画像时不确认匹配
- 前置条件: 选择没有 researchFields/keyword/discipline/recentWorkTitles 的专家；QA rule 保持启用。
- 操作步骤: 1. 生成模拟回复；2. 查看反馈和 requestCoverage。
- 预期结果: 第 1 项 status=`UNSUPPORTED`；contextWarnings 含 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`；系统不触发资料抓取任务。
- 覆盖: I-2/I-4 / 画像只读 interaction point

### A-3: 发送审计范围回归
- 前置条件: 邮件真实命中公司与项目两条规则，prompt fallback 可见更多启用规则。
- 操作步骤: 1. 在收发件生成并采用草稿；2. 人工补充后发送；3. 查看 mail_record_qa_rule。
- 预期结果: 只关联真实命中的公司与项目规则，不关联 prompt fallback 全集。
- 覆盖: I-5 / must-NOT-change

### A-4: Response 与 PARTIAL 兼容
- 前置条件: 职责 rule 只有高层描述，不含 `deliverables`；分别准备训练模拟和收发件同一入站邮件。
- 操作步骤: 1. 调用两个 AI 回复接口；2. 对比 requestCoverage 与顶层计数字段。
- 预期结果: 职责项 status=`PARTIAL`；仍计入 groundedRequestCount；两个 requestCoverage item 都只含 `index/requestText/status/factRuleIds`；顶层现有字段名称不变。
- 覆盖: I-4/I-6/I-7 / response、PARTIAL must-NOT-change

### A-5: 自动匹配与训练知识回归
- 前置条件: 配置一条与当前邮件关键词相关的训练知识和一条无关训练知识；准备已知 QA 自动匹配邮件。
- 操作步骤: 1. 调用自动匹配并记录 matchedRuleIds/replyBody；2. 生成训练模拟；3. 查看 injected context 的测试日志或 stub captured prompt。
- 预期结果: 自动 matchedRuleIds/replyBody 与改动前相同；prompt 只包含相关训练知识，不包含无关训练知识；不产生画像 enrichment 写任务。
- 覆盖: I-4/I-5 / 自动匹配、训练知识定向注入、画像只读 must-NOT-change
