# fix-1：AI 回复 P0-P2 总计划整体复验

## 原计划 / 复验范围

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`
- 子计划：01-10 全部子计划及其既有 fix 记录。
- 后续独立修复计划：intent fixture normalization、modality strengthening、review authority fail-closed。
- 实现基线：`7fc1011c fix: reject READY authority with unresolved snapshot`
- 本轮为总计划首次整体复验；P1 数：3。

## 全局约束摘录

- 公司身份问题只返回法定名称与注册地，不混入 trust 长文。
- coverage key + intent catalog 是唯一覆盖判定；研究匹配必须同时具备画像与 programme scope。
- 高风险声明必须引用含对应 phrase family 的 QA 来源；校验失败整次 structured response fallback。
- 内部状态只用于 UI；发送 QA IDs 只能是实际匹配/选择子集。
- 审核以服务端持久化 authority 为准；模拟只读；不得修改已应用迁移。

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 证据 |
|---|---|---|---|---|
| P1-1 | P1 | 只问 `full legal name` / `legal name` 时可稳定触发 | intent catalog 将 `full name/legal name/company name` 识别为 `company.legal_name`，但 V75 公司身份规则没有这些独立关键词。`QaMatchService` 只做 normalized substring 命中，因此该 request 的 candidate rules 为空，已审核公司名称事实被误判缺失。 | `ai-reply-02-p0-company-identity-rule-split.md:36-46,88-90`；`AiReplyIntentCatalog.kt:102-105`；`V75__split_company_identity_from_agency_credentials.sql:26-31`；`QaMatchService.kt:133-158`。 |
| P1-2 | P1 | `research fit` / `does my research` 命中 programme-scope QA candidate 且画像不足时稳定触发 | 研究语义存在两套目录：intent catalog 识别上述别名并声明 `requiresProfile=true`，`AiReplyContextService` 的独立短语表却不识别。随后 DraftService 用“是否存在 warning”推断画像充分、又用旧短语表写 `requiresResearchContext`，可把缺画像的研究匹配意图标成 supported/READY。 | `ai-reply-06-p1-intent-coverage-matrix.md:28-30,56-72,134-135`；`AiReplyIntentCatalog.kt:87-94`；`AiReplyContextService.kt:24-39,57-59`；`AiReplyDraftService.kt:108-119,578-610,631-637`。 |
| P1-3 | P1 | 模型使用 phrase family 的同义成员时可触发 | 高风险校验仅用 map key 检查答案，source 才遍历 family values。答案写 `travel costs covered`、`free of charge`、`employment contract`、`IP ownership` 等已登记同族短语时，不会进入对应校验，可能在无同族来源时通过。 | `ai-reply-07-p1-intent-output-and-claim-validation.md:36-40,85-92`；`AiReplyHighRiskClaimValidator.kt:145-160,239-247`。 |

## 根因

1. V75 只覆盖组合验收短语，没有校对后续 intent catalog 的独立 alias 集。
2. 研究画像需求同时由 intent catalog 与 context service 私有 phrase table 判断，两个写路径发生漂移。
3. 高风险 phrase family 数据结构把 key 当“答案触发词”、values 当“来源词”，没有执行对称的 family membership 校验。

## 修复规格

### P1-1：补齐公司身份 alias 与 QA candidate 匹配

文件：

- 新增 `src/main/resources/db/migration/V77__complete_company_identity_keywords.sql`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

要求：

1. 不修改 V75/V76；以 V77 对 `reply_subject='Company registered identity and location'` 的规则补充 `full legal name`、`legal name`、`full name`、`company name` 等 catalog 独立问法。
2. UPDATE 必须带目标 rule 限定与防重复条件；上线前导出并合并运营 keywords，不能覆盖现有值。
3. 单问 `What is your full legal name?` 必须只命中 company identity rule，不命中 Agency credentials；`company.legal_name` 获得 `company.legal_name` evidence。
4. registration + legitimacy 组合问题仍分别命中两类规则；不改变自动匹配 compose/supersede 逻辑。

### P1-2：由 intent catalog 单点决定画像需求

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 必要时补 `AiReplyContextService` 定向测试。

要求：

1. 删除/停止使用独立 `RESEARCH_PHRASES` 作为业务 authority；画像需求必须从当前 group 的 matched intent `requiresProfile` 得出。
2. ContextService 仍只读现有画像；命中任一 requiresProfile intent 且画像不足时写 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`，不得触发 Scholar/Scopus/外网 enrichment。
3. DraftService 的 `requiresResearchContext` 与 `profileSufficient` 必须基于同一 catalog 语义和实际画像充分性，不能以“warning 未出现”代表充分。
4. `research fit`、`does my research`、`research profile/background` 全部覆盖：缺画像或缺 `programme.scope` 任一项，intent 为 MISSING；两者都足才 SUPPORTED。
5. 单研究请求不得因错误进入 QA_MATCHED 而绕过 dual-evidence matrix；两入口 response/readiness 一致。

### P1-3：高风险 phrase family 双向对称校验

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

要求：

1. 答案触发条件必须检查该 family 的全部登记短语，而非只检查 map key；source 必须含同一 family 的任一短语。
2. 答案与 source 两侧采用同一边界安全、大小写不敏感 matcher，避免 substring 假命中。
3. 至少覆盖负例：`travel costs covered`、`free of charge/at no cost`、`employment contract`、`IP ownership/IP rights`、`NDA`，引用无关来源时拒绝。
4. 对应同族来源正例继续通过；数字/URL/modality/research 校验顺序与 warning code 不变。
5. 集成测试证明任一同义高风险声明校验失败时整次 structured output fallback，违规答案不进入 draft。

## 测试要求

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QaMatchServiceTest,AiReplyDraftServiceTest,AiReplyHighRiskClaimValidatorTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --check src/main/resources/static/app.js
node --test src/test/js/*.test.js
```

必须保留完整 Pracheta Janmeda 原文 fixture 的 7 groups / exact intents 断言，不得用语义改写 fixture 代替。

## 当前状态（修前）

- JVM：PASS — 1757 tests，0 failures，0 errors，4 skipped（1753 passed）。
- JS：PASS — 353 tests，0 failures。
- JS 语法：PASS。
- 功能/约束审计：FAIL — 3 个 P1；测试绿不足以满足总计划发布门。

## 子计划合规审计

| 子计划 | Verdict | Evidence |
|---|---|---|
| 01 readiness/compound coverage | ✅ | readiness 聚合存在；P0 临时 heuristic 已删除。 |
| 02 company identity split | ❌ P1-1 | 独立 legal-name alias 无 candidate rule。 |
| 03 readiness UI | ✅ | 两入口展示后端 readiness；状态未进入正文。 |
| 04 coverage keys backend | ✅ | catalog、管理校验、V76、读写路径与测试齐全。 |
| 05 coverage keys UI | ✅ | 元数据、编辑、保存与展示共用后端目录。 |
| 06 intent matrix | ❌ P1-2 | requiresProfile 与 research warning 存在双 authority 漂移。 |
| 07 structured output/claim validation | ❌ P1-3 | phrase family 的非 key 成员可绕过答案触发。 |
| 08 review authority backend | ✅ | persistent UUID、latest order、snapshot 自洽、投递前 fail closed。 |
| 09 review confirmation UI | ✅ | per-draft identity/readiness、逐项确认、编号闸门、后端 payload 均在。 |
| 10 quality metrics | ✅ | 5 类审计 count、零分母 rate、原 selected association 统计保留。 |

## 语义完整性检查

- Accumulation check：✅ 质量指标按请求时间窗直接 count，无增量重复累计。
- State machine check：✅ authority READY/NEEDS_REVIEW/BLOCKED 与 snapshot 双向一致，拒绝在 delivery 前。
- Cross-plan check：❌ company alias、research intent/profile、high-risk family 三处跨目录语义未闭合。
- Deleted code：✅ `isPartialCoverage/PARTIAL_DETAIL_*` 已删除。
- No extras：✅ 本轮只新增修复计划与知识条目，未改业务代码。

## 发布结论

当前不得判定 P0-P2 总计划整体通过。完成本 fix-1 后重新调用 `fix-v`；要求 P1=0，才可关闭总计划发布门。
