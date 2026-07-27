# AI 回复补强第 9 步：Fallback 参考边界与尽调问题意图对齐

## 需求描述

- 可观察结果：LLM 不可用或校验失败时，系统不再拼接一封貌似完整的英文邮件；只显示带固定标题的内部 `QA 规则参考内容（LLM 未生成，不能直接发送）`，列出当前问题、可引用事实、事实来源和缺失项。
- 可观察结果：用户给出的 7 个尽调问题稳定得到 `完整 4 / 部分 1 / 缺失 2`：角色是否有报酬、IP、正式合同、费用完整；投入时间与项目周期部分；报酬结构、企业/机构实例缺失。
- 可观察结果：新增识别能力不靠给 QA 规则添加抽象标签；只在事实正文确实能回答时，幂等追加少量可读关键词。QA 管理页的字段、匹配方式和使用方式保持不变。

必须保持不变：

1. QA 规则仍只承担“问题关键词 + 已审核事实正文 + AUTO/REVIEW/NEVER”职责，不新增信任标签、意图标签或运行时分析结果字段。（来源：K-qa-rule-runtime-vs-migration-writes）
2. `answerBody` 是事实正文唯一来源；`replyBody/templateBody` 不回流到 grounded、fallback 或审核证据。（来源：K-answerbody-source-exclusive、K-grounded-answerbody-no-legacy-fallback）
3. 成功 LLM 草稿仍由 `GroundedContentPlan` 限定 claims/sourceIds/missingFacts，自然化只改语言，不增加事实。（来源：K-grounded-json-materialize-before-policy、K-grounded-natural-structure-server-gate）
4. 子计划 8 的失败采用门禁必须已上线；本计划不会让 fallback 获得采用或发送资格。
5. 历史邮件不进入 fallback 事实来源；本计划不修改历史上下文或称呼逻辑。

不在本计划范围：

- 不修改 QA 管理页布局，不新增标签、多选框、变体或组装台字段。
- 不修改 LLM HTTP 重试、失败 banner、最终发送复验或自动发送规则。
- 不承诺具体薪资结构、每周投入小时数、企业名称/类型实例；这些内容没有审核事实，必须继续缺失。
- 不修改现有事实正文、reply policy、规则启用状态、优先级或人工编辑内容。

## 关键不变量

### Invariant I-1：识别能力与事实能力分离
- Rule：intent alias 可以识别“用户问了什么”，但只有匹配到非空、启用、可出站的 `answerBody` 才能把 intent 判为 SUPPORTED。不得因为 alias 命中就把问题判完整。
- Applies to：`AiReplyIntentCatalog.matchIntents/assignRulesToIntents`、`QaFactSelectionService.buildRequestFact`。
- Violation consequence：系统把“识别到了报酬结构问题”错误显示为“已有依据”，模型据此编造细节。
- 来源：K-compound-request-coverage-intent-atomic、original。

### Invariant I-2：复合问题按原子意图计算
- Rule：报酬“是否存在”与“结构/金额细节”是两个 intent；“时间投入”与“项目周期”是两个 intent。一个原子 intent 有依据、另一个缺失时，请求状态只能是 PARTIAL。
- Applies to：intent definitions、rule-to-intent assignment、request status 汇总、content plan missingFacts。
- Violation consequence：一条宽泛 compensation/duration 事实吞掉相邻问题，错误生成完整回答。
- 来源：K-compound-request-coverage-intent-atomic、original。

### Invariant I-3：规则只能分配给有语义交集的 intent
- Rule：`scoreRuleIntentAlignment()` 得分必须大于 0 才能分配；并列按 catalog 固定顺序，但同一规则一次只能支持一个原子 intent。`general.answer` 仅在没有专门 intent 时接收规则。
- Applies to：`AiReplyIntentCatalog.assignRulesToIntents` 与 `QaFactSelectionService` candidate rules。
- Violation consequence：一条“2–3 years”规则同时证明 duration 与 time commitment，或一个宽关键词被重复当多份证据。
- 来源：K-ai-reply-intent-alias-fixture-fidelity、original。

### Invariant I-4：迁移只追加被正文支持的关键词
- Rule：V81 只给既有三条事实追加：报酬存在、项目周期、正式合同的问法；不得追加 `remuneration structure`、`time commitment`、`types/examples of enterprises/institutions`。更新必须按稳定 `reply_subject` 定位，每个短语分别用 `LOWER(keywords) NOT LIKE` 决定是否追加，且 `updated_at = updated_at`，不得覆盖运营编辑。
- Applies to：V81 migration 与迁移契约测试。
- Violation consequence：缺失问题被错误匹配为完整，或部署迁移污染人工事实正文/审计时间。
- 来源：K-qa-migration-preserve-auto-updated-timestamp、K-company-identity-keyword-intent-parity、K-qa-coverage-keys-management-write-boundary。

### Invariant I-5：Fallback 是内部参考，不是邮件
- Rule：fallback 首行固定为 `QA 规则参考内容（LLM 未生成，不能直接发送）`；按请求顺序展示可引用事实与缺失 intent；不得生成称呼、问候、致谢、结束语、CTA 或完整邮件结构。FREE_FORM 无 QA 事实时只显示“LLM 未生成，且当前来信没有可用于确定性回复的审核事实。请人工撰写。”。
- Applies to：`AiReplyPointByPointComposer`、`AiReplyDraftService.groundedFallbackResult/fallbackDraftText`。
- Violation consequence：规则正文拼接再次被运营误认成 AI 已自然化成稿。
- 来源：original；子计划 8 I-4/I-6。

### Invariant I-6：Fallback 只读取当前 content plan
- Rule：参考内容只遍历 `GroundedContentPlan.claims/paragraphs/missingFacts` 对应的当前 `requestFacts` 和 sourceIds；不得扫描全部命中规则、历史邮件、`lastDraft` 或 operator turn。继续修改时 LLM 失败，也重新显示当前 plan 参考，不回显旧成稿冒充本次结果。
- Applies to：两个 fallback 入口及 composer repository reads。
- Violation consequence：参考区出现未被本次问题授权的事实，或旧草稿掩盖本次 LLM 失败。
- 来源：K-request-facts-not-flat-pool、K-grounded-paragraph-cap-never-drop-claims、original。

### Invariant I-7：来源名称可读且不泄漏内部键
- Rule：参考内容的事实名顺序固定为 `displayName → sectionTitle → replySubject → 事实名称缺失`；`未命名事实`、空串视为无效。不得显示 rule ID、coverage key、intent key、hash 或数据库时间戳。
- Applies to：composer 的 source label resolver 与 fallback 文本测试。
- Violation consequence：运营看到技术标识而非可核验来源，且内部 schema 泄漏到邮件工作台。
- 来源：K-ai-evidence-ui-no-rule-id-fallback。

### Invariant I-8：所有 fallback readiness 固定 BLOCKED
- Rule：`usedLlm=false` 的 fallback 无论事实覆盖是否完整，`draftReadiness` 固定为 BLOCKED；原始 requestFacts、coverage 统计和 evidence snapshot 保留供诊断。不得通过“事实完整”把 fallback 提升为 READY。
- Applies to：`AiReplyDraftService.groundedFallbackResult`、旧 `fallbackDraftText` 调用链、auto gate。
- Violation consequence：内部参考获得自动发送或运营采用资格。
- 来源：original；K-validation-exhaustion-must-block-readiness。

## 现状审计

### Intent 与 requestFact 内存模型
- Schema/mapping：`RequestIntentDefinition` 定义 key/title/aliases；`RequestIntentCoverage` 保存每个原子 intent 的 evidenceRuleIds/status；`RequestFactItem` 汇总为 GROUNDED/PARTIAL/UNSUPPORTED。无需新增 DTO 字段或数据库列。
- Write paths（全部）：
  1. `AiReplyIntentCatalog.matchIntents()` 从当前 requestText 产生 intent definitions。
  2. `AiReplyIntentCatalog.assignRulesToIntents()` 把当前候选规则单分配到原子 intent。
  3. `QaFactSelectionService.buildRequestFact()` 写 intent coverages、factRuleIds、request status。
  4. `AiReplyGroundedContentPlanner.buildPlan()` 读取 requestFacts 写 claims/paragraphs/missingFacts；本计划不修改 planner。
- Read paths（全部）：DraftService prompt、Grounded materializer、trust validator、前端“问题与依据”、audit、auto reply decision、fallback composer。
- Interaction points：alias 只影响识别；DB keyword 决定候选；alignment 决定候选归属；三个阶段缺一不可。

### `qa_rule` 持久化
- Schema/mapping：V1 创建；V14 `display_name`；V40 `section_title`；V41 supersedes；V76 coverage keys；V79 `answer_body`；V80 `reply_policy`。本计划只写既有 `keywords`。
- Runtime write paths（全部）：`QaRuleManagementService.create/update/delete/setEnabled` 及 repository save/delete；运营可随时修改 keywords、answerBody、policy 等。V81 不得覆盖这些字段。
- Migration write paths（全部）：Flyway V1–V80 的 seed/alter/update；V81 是新增唯一写路径，只对三条稳定 `reply_subject` 做条件追加。
- Read paths（全部）：`QaFactSelectionService.findAllEnabledOrdered/findById`、QA 管理 controller、审计/发布门禁、evidence snapshot、fallback composer。
- Interaction points：线上人工修改可能与仓库 seed 不同，部署前必须先只读导出目标三条的 id/reply_subject/keywords/answer_body/enabled/reply_policy/updated_at；若 `reply_subject` 不唯一或正文语义已变化，停止迁移并修订计划。

### 当前 7 问偏差根因
- `finance.arrangements` 同时覆盖 compensation/funding/payment/how much，无法区分“是否有报酬”与“报酬结构”。
- catalog 没有独立 time commitment/duration；`application.next_stages` 的 `timeline` 是申请流程时间，不应证明顾问工作周期。
- enterprise project types 识别词不足；即使识别，现有“后续按研究方向匹配企业”也不能证明企业/机构实例。
- contract 只识别 contract terms/labor contract，未覆盖“合作前是否提供正式协议”的原问法。
- 当前 `composeFallback()` 直接 join 完整 answerBody，再加称呼与 closing；`groundedFallbackResult()` 在 continuation 失败时还可能返回 lastDraft，均违反内部参考边界。

## 实现方案

### T1：把尽调问题拆成稳定原子 intent
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt`
- 保留 `finance.arrangements` 表示报酬/资金是否存在；新增 `finance.compensation_structure`，aliases 仅含 remuneration/compensation/salary structure 与 amount breakdown 类问法。（I-1、I-2）
- 新增 `work.time_commitment`（weekly/monthly hours、time commitment、level of involvement）和 `work.advisory_duration`（typical duration、duration of advisory projects、how long advisory project）；不得复用 `application.next_stages`。（I-2）
- 扩展 `enterprise.project_types` 的显式问法为 types/examples of Chinese enterprises/institutions involved；扩展 `contract.terms` 为 formal agreement/formal contract/before collaboration begins；扩展 `finance.arrangements` 为 advisory role compensated。（I-1）
- 更新 group title 与 explicit project-type disambiguation；测试逐字使用用户 7 问，断言每问 intent keys 且无 `general.answer/application.next_stages` 误入。

### T2：保证原子证据分配与完整/部分/缺失统计
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt`
- 保持 candidate rule 先经过关键词命中；显式收口 request status helper：all supported=GROUNDED、some supported=PARTIAL、none=UNSUPPORTED。不得用 `candidateRules.isNotEmpty()` 覆盖 intent 结果。（I-1、I-2、I-3）
- 以仓库现有事实 fixture 加 V81 目标关键词，断言 7 问逐项为 `GROUNDED, UNSUPPORTED, PARTIAL, UNSUPPORTED, GROUNDED, GROUNDED, GROUNDED`，总计 `4/1/2`。（I-2）
- 负向断言 duration 事实不支持 time commitment、compensation availability 不支持 compensation structure、matching 事实不支持 enterprise examples。（I-3、I-4）

### T3：将 fallback 改成 plan 驱动的内部参考
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt`
- 用 `composeFallbackReference(plan, requestFacts)` 替换发送式 `composeFallback(requestFacts)`；按 requestIndex 和 paragraph claim 顺序读取 sourceIds，每条规则只出现一次。（I-5、I-6）
- 每项结构固定为：`问题 N：<原问题>`、`可引用事实：<answerBody>`、`来源：<可读事实名>`；缺失项固定为 `缺失：暂无已审核事实，需人工补充。`。PARTIAL 同时显示已有事实和缺失行。（I-5、I-7）
- 开头固定警示；不得调用 `ReplySnippetService.resolveManualFrame/resolveAck`。answerBody 保持原文，不自动补语气、承诺或行动建议。（I-5）
- FREE_FORM 空参考使用 I-5 固定句；测试断言无 `Dear/Best regards/Thank you/Please let`、无 ID/intent key、source 去重和顺序稳定。

### T4：收口 DraftService fallback 状态
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- `groundedFallbackResult()` 与旧兼容 `fallbackDraftText()` 均传入本次 plan/requestFacts；删除 continuation 返回 `lastDraft` 和 FREE_FORM 邮件拼装分支。（I-5、I-6）
- 所有 `usedLlm=false` 结果固定 readiness BLOCKED；保留 mode/requestFacts/unsupported/evidence snapshot/warnings，供子计划 8 UI 和审计读取。（I-8）
- 成功 LLM 路径不变；增加回归断言成功仍按 plan 自然化，fallback 不进入 `composeFromPlan()`，auto gate 永远拒绝 fallback。

### T5：V81 只补有依据问法并建立发布门禁
- 文件：
  - `src/main/resources/db/migration/V81__ai_reply_due_diligence_keyword_parity.sql`
  - `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`
- 对 `reply_subject='Funding support'` 幂等追加 `advisory role compensated,is the advisory role compensated`；对 `reply_subject='Program overview'` 追加 `typical duration,duration of advisory projects,advisory project duration`；对 `reply_subject='Contract and IP arrangements'` 追加 `formal agreement,formal contract,before any collaboration begins`。（I-4）
- 每个目标行只有一个 UPDATE；`keywords = CONCAT(keywords, CASE WHEN LOWER(keywords) NOT LIKE ... THEN ',<phrase>' ELSE '' END, ...)` 对组内每个短语独立防重，WHERE 只在任一短语缺失时命中，并显式 `updated_at = updated_at`。不得更新 answer_body/display_name/section_title/coverage_keys/reply_policy/enabled/priority。（I-4）
- migration 静态测试断言仅三个 UPDATE、只写 keywords/updated_at、包含三组正向词且不含三组无依据词。
- 部署前导出目标行；V81 后再次导出并逐字段比较，只允许 keywords 增加预期片段。V81 一旦进入目标库，不允许修改原文件；异常通过新迁移修正。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | 尽调原子 intents、aliases 与 disambiguation |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 原子 coverage 状态收口 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt` | plan 驱动的不可发送参考内容 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | fallback 内容与 BLOCKED 状态收口 |
| 5 | `src/main/resources/db/migration/V81__ai_reply_due_diligence_keyword_parity.sql` | 三条已证实问法的幂等关键词迁移 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt` | 7 问 intent 精确识别测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 4/1/2 coverage 与负向边界测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt` | fallback 文本、来源、顺序与负向测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | fallback BLOCKED 与成功路径回归 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt` | V81 静态写边界合同 |

边界：10 个文件，2 个子系统（意图/事实选择、fallback/迁移），0 个 schema/共享 store 新字段。执行中若需新增 QA 表字段、前端标签或第 11 个文件，必须停下修订计划。

## 验收标准

- I-1/I-2：7 问 catalog 与 selection fixture 逐项状态完全一致；总数严格 `grounded=4/partial=1/unsupported=2`，不是仅断言“非空”。
- I-3：三个负向分配测试均无 evidenceRuleIds；同一 rule ID 不出现在同一请求两个原子 intent 中。
- I-4：V81 静态测试与真实 pre/post diff 都只改变三条 keywords；`updated_at`、answer_body、policy、enabled、priority 不变；无依据词不存在于 SQL。
- I-5：所有 fallback 首行固定，QA 模式按问题展示，FREE_FORM 固定人工提示；全文无邮件称呼/closing/CTA。
- I-6：fallback sourceIds 等于 plan claims 的并集；operatorTurns/lastDraft/mailHistory 改变不影响参考内容。
- I-7：来源标签四级 fallback 全覆盖；输出无 rule ID、intent key、coverage key、hash 和“未命名事实”。
- I-8：所有 fallback fixture readiness=BLOCKED；成功 LLM readiness 仍由既有规则计算；auto decision 对 fallback 仍拒绝。
- 交互集成：`request alias → DB keyword → candidate rule → intent assignment → request status → content plan → LLM/fallback reference` 每段均有至少一条正向和负向断言。
- 运行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyIntentCatalogTest,QaFactSelectionServiceTest,AiReplyPointByPointComposerTest,AiReplyDraftServiceTest,QaRuleManagementServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
npm test
git diff --check
```

## 人工验收清单

### A-1：线上迁移前基线
- 前置条件：连接目标库；确认 `flyway_schema_history` 无 V81；准备只读 SQL 权限。
- 操作步骤：按 `reply_subject` 导出三条目标规则的 id/reply_subject/keywords/answer_body/display_name/section_title/enabled/reply_policy/priority/updated_at；检查每个 subject 恰好一行。
- 预期结果：三条唯一且正文仍分别支持报酬存在、2–3 年项目周期、选中后正式合同；任一不符立即停止部署，不执行 V81。
- 覆盖：I-4；`qa_rule` migration/runtime interaction point。

### A-2：V81 迁移后核验
- 前置条件：A-1 通过并保存 pre 导出；正常部署执行 V81。
- 操作步骤：重复导出；对 pre/post 做字段级 diff；再执行一次三条 UPDATE 的等价校验查询确认无重复片段。
- 预期结果：只新增计划中的三组 keywords；其他字段逐字/逐值一致，updated_at 不变；每个新增短语只出现一次。
- 覆盖：I-4。

### A-3：7 问成功生成
- 前置条件：LLM stub/测试模型返回合法 Grounded JSON；使用用户提供的 7 问原文，QA 库为 V81 后状态。
- 操作步骤：打开“问题与依据”并生成草稿；逐项查看状态、依据与草稿回答。
- 预期结果：第 1/5/6/7 项完整，第 3 项部分，第 2/4 项缺失；顶部 `完整 4 项 · 部分 1 项 · 缺失 2 项`；草稿只回答已有依据部分，不编造金额、投入小时或企业实例。
- 覆盖：I-1、I-2、I-3；需求第 2 条。

### A-4：LLM 超时 fallback
- 前置条件：子计划 8 已上线；同一 7 问场景让 LLM 连续超时。
- 操作步骤：点击生成；阅读参考区全文，检查标题、按钮与 Network response。
- 预期结果：正文首行是固定内部参考警示；按 7 个问题列出已有事实与缺失；无 Dear/Best regards/CTA；readiness=BLOCKED，采用 disabled。
- 覆盖：I-5、I-6、I-8；需求第 1 条。

### A-5：继续修改时 LLM 失败
- 前置条件：先生成一版成功稿，再输入操作指令；下一次调用强制超时。
- 操作步骤：点击继续生成；比较成功旧稿与本次结果。
- 预期结果：本次明确显示 plan 参考内容和失败 banner，不把旧稿回显为新结果；旧成功草稿仍可在自己的历史 bubble 中识别，但本次失败不可采用。
- 覆盖：I-6；与子计划 8 I-4 的交互。

### A-6：无依据词负向验证
- 前置条件：在 QA 管理页搜索三条目标规则。
- 操作步骤：检查关键词；分别只发送 remuneration structure、weekly time commitment、enterprise examples 三个问题。
- 预期结果：关键词中不存在无依据短语；三个问题均显示缺失，不被宽泛 programme/funding/matching 规则误判完整。
- 覆盖：I-1、I-3、I-4。

### A-7：QA 管理与组装台回归
- 前置条件：打开 QA 管理页和组装台，记录 V81 前界面。
- 操作步骤：编辑一条非目标规则并保存；选择 QA 建议；生成成功稿和失败参考各一次。
- 预期结果：管理字段、变体/组装台交互无新增标签；非目标规则正常保存；成功稿仍自然化，失败时才显示内部参考。
- 覆盖：必须保持不变第 1、3 条。

### A-8：自动发送回归
- 前置条件：分别准备 `LLM_USED/READY` 与 `FALLBACK_NO_RESPONSE/BLOCKED` 结果。
- 操作步骤：运行自动回复 decision/preview。
- 预期结果：成功项按既有策略处理；fallback 一律拒绝，QA 覆盖完整也不能提升。
- 覆盖：I-8；必须保持不变第 4 条。
