# 修复计划：AI 回复失败可见性与信任闭环（fix-1）

## 验证元数据

- 总计划：`docs/plans/2026-07-21/ai-reply-failure-trust-closure-master-plan.md`
- 子计划：
  - `docs/plans/2026-07-21/ai-reply-08-llm-failure-workbench-contract.md`
  - `docs/plans/2026-07-21/ai-reply-09-fallback-reference-intent-parity.md`
  - `docs/plans/2026-07-21/ai-reply-10-history-context-recipient-identity.md`
- 模式：`WORKFLOW_ARTIFACTS`
- 复验轮次：初次复验，`fix-1`，1/3
- 既往同目标 fix plan：无
- 结果：`FIX-1`
- 说明：本轮只写验证/修复工作流产物及知识命中记录，未修改业务实现。

## 修复前构建与测试

| 项目 | 状态 | 证据 |
|---|---|---|
| 目标 Kotlin 测试组 | PASS | 251 passed，0 failed，0 skipped；命令覆盖三个子计划列出的 9 个测试类 |
| 目标 JS 测试 | PASS | 48 passed，0 failed，0 skipped |
| `mvn clean test` | PASS | JVM/Kotlin 1,798 tests，0 failures，0 errors，4 skipped；Maven 内嵌 JS 336 passed |
| `npm test` | N/A | 仓库无 `package.json`，命令实际返回 `Missing script: test`；仓库真实 JS runner 已由 Maven 和 `node --test` 执行 |
| `git diff --check` | PASS | 无空白错误 |
| 人工验收 | PENDING | 三个子计划全部 A-n；尤其 V81 线上 pre/post、超时页面、长线程、真实称呼与发送回归 |

自动测试通过不能签发：现有测试没有覆盖多项强制契约，且其中一条 JS 测试明确接受旧 fallback 展示。

## 约束摘录

- Phase 08 I-1/I-3：失败必须稳定分类；日志不得记录消息、异常文本、URL query 或凭据；`CLIENT_UNAVAILABLE` 不得坍缩为普通无响应。
- Phase 08 I-2：首次 transient failure 仅重试一次；结构修复仅一次；单操作最多 3 次 provider call。
- Phase 08 I-4/I-6/S-2：凡 `usedLlm!=true || generationState!=LLM_USED`，必须先显示失败 banner，两个采用入口原生 disabled，handler 二次拒绝，成功会话状态不推进。
- Phase 09 I-2：七问必须严格得到 `GROUNDED, UNSUPPORTED, PARTIAL, UNSUPPORTED, GROUNDED, GROUNDED, GROUNDED`，总计 `4/1/2`。
- Phase 09 I-5/I-6：fallback 是内部参考，只能读取当前 `GroundedContentPlan` 的 claims/paragraphs/missingFacts 与 sourceIds，不得读取旧草稿、历史或扁平命中池。
- Phase 10 I-1～I-6：历史仅保留真实 INBOUND/SENT OUTBOUND，精确排除当前 messageId，最近 8 封，完整 block 总长不超过 5000，只作 continuity。
- Phase 10 I-7/I-8：称呼只来自真人 given/family；技术 ID、邮箱、ORCID、esDocId 必须为空；preview/plain/HTML/AI profile 共用同一策略。
- 总计划：失败参考不得采用或发送；页面无“未命名事实”/内部 ID；自动发送门禁不放宽；纯人工发送不新增 AI 状态门禁。

## P1 收敛与回归边界

| ID | Lineage | 回归边界 | 结论 |
|---|---|---|---|
| P1-1 | NEW_IN_SCOPE | `INTRODUCED` + 既有 seam 被本计划明确纳入 | transport 日志、CLIENT_UNAVAILABLE 状态和强制分类测试未闭环 |
| P1-2 | NEW_IN_SCOPE | `INTRODUCED` | 前端仅凭 warning 判失败，disabled/client fallback 仍显示成旧规则草稿且按钮视觉可用 |
| P1-3 | NEW_IN_SCOPE | `INTRODUCED` | fallback 未消费 content plan，仍直接遍历 requestFacts/factRuleIds |
| P1-4 | NEW_IN_SCOPE | `INTRODUCED` | 七问、负向分配、V81 与 fallback 的强制测试合同未实现，并出现放宽断言 |
| P1-5 | NEW_IN_SCOPE | `INTRODUCED` | messageId 归一化、完整 block 总预算和固定历史格式不符合合同 |
| P1-6 | NEW_IN_SCOPE | `INTRODUCED` | 收件人姓名策略未统一，仍读取 displayName，且未与技术标识集合比较 |
| P1-7 | NEW_IN_SCOPE | `INTRODUCED` | 修改了三个子计划范围外的测试文件；测试命令还引用不存在的 npm 工程 |

## P1 证据与最小修复

### P1-1：transport seam 未形成稳定、安全的单源失败合同

- 约束：Phase 08 I-1、I-2、I-3、I-8。
- 证据：
  - `HttpLlmDraftClient.kt:189-195` 新日志增加了合同未允许的 `statusCode`。
  - `HttpLlmDraftClient.kt:231-233` 旧 `executeChat()` 仍记录 `ex.message`，可能包含 URL/query 等异常细节；并且旧 seam 与 observed seam 是两套 HTTP 实现。
  - `HttpLlmDraftClient.kt:140-142` 空 URL 返回 `CLIENT_UNAVAILABLE`，但 `AiReplyDraftService.kt:336-356` 固定生成 `FALLBACK_NO_RESPONSE`。
  - `AiReplyDraftService.kt:1391-1398` 对 `CLIENT_UNAVAILABLE` 返回空 warning；该结果既无专属 generationState，也无可供 UI 消费的原因。
  - `HttpLlmDraftClientTest.kt:46-81` 仅有 2 个 model mapping 测试，没有 timeout/429/5xx/network/blank/空 URL/日志边界。
- 触发频率：每次旧 seam 异常；以及 enabled=true 但 client 配置不可用时的每次回复生成。
- 影响：敏感异常文本进入日志；页面无法区分 client unavailable；响应/audit/UI 的失败原因漂移。
- 最小修复文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClientTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`
- 修复规格：
  1. `executeChat()` 只投影 observed 结果的 `.content`，不得保留第二套 HTTP/catch/log 实现；避免递归调用默认 seam。
  2. 日志字段只保留 model、attempt、messageCount、总字符数、elapsedMs、failureType；移除 exception message、statusCode、body、URL、Authorization/API key。
  3. DraftService 将最终 `CLIENT_UNAVAILABLE` 映射为 `FALLBACK_CLIENT_UNAVAILABLE`；其他 transport failure 保持 `FALLBACK_NO_RESPONSE + 唯一 warning`。
  4. 首次 retry 成功不得保留瞬态 warning；correction 不做 transport retry；测试精确断言 1/2/3 次上限。
  5. controller response 与 audit 对最终 warning 同源；audit 失败继续 best-effort，不改变 HTTP 结果。
- 禁止：新增 provider-specific generationState、修改 controller DTO、记录 prompt/邮件正文、为 correction 增加第二次 retry。

### P1-2：失败 UI 与采用门禁错误依赖 warning 是否存在

- 约束：Phase 08 I-4、I-5、I-6、S-1、S-2、S-3。
- 证据：
  - `app.js:3956-3963` 只把 `contextWarnings` 传给 reason resolver；没有按 generationState 做最终 fallback。
  - `app.js:8615-8646` 虽计算 `generationFailed`，但只有 `failureCode` 非空才 disabled/aria/title/标题切换。
  - `aiReplyLoadingFeedback.test.js:107-116` 对 `FALLBACK_LLM_DISABLED + 空 warnings` 仍断言旧 `.ai-reply-warning` 和“LLM 已关闭—结构化规则草稿”，直接证明失败 banner 合同未执行。
  - `app.js:9600-9602` continuation 失败后仍清空修改要求，违背重试保留输入的实现合同。
- 触发频率：LLM disabled、bean/client unavailable、或任何未附 transport warning 的 fallback；配置/故障期间为每次操作。
- 影响：运营先看到普通草稿状态；采用按钮可能未原生禁用；当前问题可被误认为已生成可信草稿。
- 最小修复文件：
  - `src/main/resources/static/app.js`
  - `src/main/resources/static/styles.css`（只校验既有逐字合同，预计无需改值）
  - `src/test/js/aiReplyLoadingFeedback.test.js`
  - `src/test/js/trustReplyWorkbench.test.js`
  - `src/test/js/aiReplyReviewConfirmation.test.js`
- 修复规格：
  1. reason resolver 接收完整 result，固定按“最终 transport warning → trust repair warning → generationState”解析；所有 fallback 均得到原因。
  2. `!isAiReplyGenerationSuccess(result)` 本身就是门禁；不得以 reason code 是否存在决定 disabled。
  3. trust 按钮失败时同时具备 `disabled`、`aria-disabled=true`、固定 title；标题固定为“QA 规则参考内容”，生成按钮为“重试生成”。
  4. 旧 bubble 使用草稿自身 result 同样 disabled，两个 handler 均以 success predicate 二次拒绝。
  5. 失败不更新 success state，也不清空 operator instruction；成功才清空/推进。
  6. 重写过时测试：逐项覆盖四个 generationState、五种 transport warning、trust exhausted、success 恢复、前后历史草稿、人工编辑器独立发送。
- 禁止：新增按钮/wrapper、修改三栏结构、修改 S-1/S-2 CSS 数值、把 AI 状态传入纯人工发送 gate。

### P1-3：fallback 绕过 `GroundedContentPlan`

- 约束：Phase 09 I-5、I-6、I-7；Phase 10 I-6。
- 证据：
  - `AiReplyDraftService.kt:633` 接收 `plan`，但 `638-640` 只传 `resolved.requestFacts`；编译器同时报告 `plan` 未使用。
  - `AiReplyPointByPointComposer.kt:83-122` `composeFallbackReference()` 直接遍历 requestFacts/factRuleIds/status/intents，不读取 `plan.claims/paragraphs/missingFacts`。
  - `AiReplyPointByPointComposer.kt:58-80` 旧发送式 `composeFallback()` 仍保留；对应测试仍全部覆盖旧 API，新 reference 没有专用测试。
- 触发频率：每次 LLM disabled、超时、空响应、transport failure 或 trust repair exhaustion。
- 影响：当前参考区的事实集合不是已批准 content plan 的严格投影；后续 planner 过滤/排序变化可被 fallback 绕过。
- 最小修复文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 修复规格：
  1. API 固定为 `composeFallbackReference(plan, requestFacts)`。
  2. 按 `plan.paragraphs.claimKeys` 顺序查 `plan.claims`；仅读取这些 claim 的 sourceIds，并按 requestIndex 分组去重。
  3. 缺失只来自 `plan.missingFacts`；显示标题必须从 requestFacts 中对应 intent 的 title 解析，不输出 intent key。
  4. repository 只查询 plan sourceIds；answerBody 原文输出；来源名保持四级 fallback。
  5. 删除已无生产引用的发送式 `composeFallback()`、`fallbackDraftText()` 和 FREE_FORM 邮件拼装死代码；不得删除仍被生产调用的方法。
  6. 测试断言 sourceIds 精确等于 plan claims 并集、paragraph/request 顺序稳定、历史/lastDraft/operatorTurns A/B 不影响文本、FREE_FORM 固定句、readiness 恒 BLOCKED。
- 禁止：扫描全部 candidate/sendQaRuleIds、回退 replyBody、加入称呼/closing/CTA、用 history/lastDraft 填参考。

### P1-4：七问与 V81 的强制机器合同未落地

- 约束：Phase 09 I-1～I-4 及验收标准。
- 证据：
  - `AiReplyIntentCatalogTest.kt`、`QaFactSelectionServiceTest.kt`、`QaRuleManagementServiceTest.kt` 本次均未增加计划要求的七问/负向/V81 合同。
  - `AiReplyDraftServiceTest.kt:2563-2583` 将原本确定状态放宽为 `PARTIAL || GROUNDED`，与“逐项状态完全一致、不能只断言非空/宽范围”冲突。
  - 当前 `HttpLlmDraftClientTest` 2 条、Context 18 条等全量通过，证明现有 suite 无法发现上述契约缺口。
- 触发频率：每次 aliases、keywords、alignment 或 migration 变动；当前版本无法由自动化证明 `4/1/2`。
- 影响：七问可能再次被判完整而硬答无依据问题；部署前没有可重复的迁移写边界证明。
- 最小修复文件：
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`
  - 仅当新增精确 fixture 失败时，最小调整 `AiReplyIntentCatalog.kt` / `QaFactSelectionService.kt`。
- 修复规格：
  1. 逐字保存用户七问，逐问断言精确 intent keys，负向断言无 `general.answer/application.next_stages` 误入。
  2. 使用 V81 三条事实 fixture，严格断言状态序列和总计 `4/1/2`。
  3. 负向断言：duration 不支持 time commitment；availability 不支持 structure；matching 不支持 enterprise examples；一个 rule ID 不重复分配。
  4. V81 静态断言仅 3 个 UPDATE、每短语独立 CASE/NOT LIKE、`updated_at=updated_at`、无 unsupported 词、无其他列写入。
  5. 将 `PARTIAL || GROUNDED` 改回单一精确预期；不得为了当前实现继续放宽。
- 禁止：修改已应用 V81；增加 remuneration structure/time commitment/enterprise examples 数据库关键词；用 alias 命中直接赋予事实。

### P1-5：历史 messageId 与 5000 字符完整 block 合同不成立

- 约束：Phase 10 I-1、I-2、I-3、I-4。
- 证据：
  - `AiReplyContextBuilder.kt:44-53` 先 `removeSurrounding()` 再 trim；`" <id> "` 无法与 `"<id>"` 归一化为同一 messageId。
  - `AiReplyContextBuilder.kt:80-87` subject/body 为空时删除固定字段行，输出不再是统一 block 格式。
  - `AiReplyContextBuilder.kt:90-100` 预算只累加 block.length，未计 `joinToString("\n\n")` 的分隔符，最终字符串可超过 5000。
  - `AiReplyContextServiceTest.kt` 本次仅把一个旧 OUTBOUND fixture 改为 SENT，没有计划要求的 12 封、乱序、tie-break、messageId、过滤和总预算矩阵。
- 触发频率：带空白/尖括号 messageId；长线程逼近 5000 字符；空 subject/body 邮件。
- 影响：当前来信可在 prompt 重复；历史超预算增加超时概率；格式快照不确定。
- 最小修复文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`
- 修复规格：
  1. 单一 helper 按 `trim → removeSurrounding(<,>) → trim` 归一化 current/record messageId，大小写无关精确比较。
  2. block 永远输出 role、`Subject:`、`Body:` 三段，值可为空；只保留固定标签。
  3. 加入一个 block 前同时计算新增 `\n\n` 分隔符；最终 `history.length <= 5000`，不足时只丢最旧完整 block。
  4. 测试覆盖 eligibility、当前 ID、有同正文不同 ID、最近 8、effective time/id tie、160/800、5000、cleanedBody 和 metadata 负向。
  5. controller 集成捕获 DraftService history，证明收件箱当前邮件被排除、旧 inbound/SENT outbound 保留。
- 禁止：正文模糊去重、总字符串 `take(5000)`、加入 messageId/sendStatus 等 metadata、持久化摘要。

### P1-6：收件人姓名策略不统一，技术标识仍可成为称呼

- 约束：Phase 10 I-7、I-8。
- 证据：
  - `MailVariableService.kt:187-205` 在 given/family 为空后继续读取 `expert.displayName`，明确违反“不读取 displayName fallback”。
  - `AiReplyContextBuilder.kt:117-158` 把 policy 放在 ContextBuilder 内，只做模式匹配；没有与 profile/contact 的 orcidId/email/esDocId 值集合比较。
  - `AiReplyContextBuilder.kt:126-139` 先拼接 given+family 后只校验整体字符串；技术 ID 与另一个字段拼接后可能绕过 ORCID/EMAIL 模式。
  - `MailVariableServiceTest.kt` 本次未修改，没有 EMAIL/ORCID/email/esDocId 与 preview/plain/HTML 矩阵。
- 触发频率：无真人姓名、索引字段被技术 ID 污染或 email-only 专家；这些正是信任场景的常见边界。
- 影响：可再次生成 `Dear EMAIL-*`、ORCID 或 ES 主键称呼；preview、发送与 AI profile 可能不一致。
- 最小修复文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextServiceTest.kt`
- 修复规格：
  1. 按原计划在 `MailVariableService.kt` 定义 module-internal `ExpertRecipientNamePolicy`，ContextBuilder 直接复用。
  2. `expertName` 只由有效 givenNames/familyNames 组成；`expertFamilyName` 只来自有效 familyNames；MailVariable 不读取 displayName/contactName。
  3. 候选除 pattern 外，还要与 profile/contact 的 orcidId、email、esDocId 做 trim 后比较；任一技术候选输出空串。
  4. ContextBuilder 只允许同一 policy 认可的 profile full name 或 contact candidate；非法时省略 `Name:`。
  5. 测试正常 full/given/family、EMAIL-*、ORCID、邮箱、含@、esDocId、等于 contact 技术字段、混合字段与首尾空白；preview/plain/HTML 均触发 `Professor` fallback。
- 禁止：修改 `ExpertProfile.displayName`、清洗/回写 ES 或 expert_contact、在前端单独隐藏、创建第二套过滤逻辑。

### P1-7：实现范围与执行命令需要两项窄修订

- 约束：三个子计划的 10 文件边界与必跑命令。
- 证据：
  - `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:331-376` 已修改，但不在任一子计划文件清单。
  - 该测试修改直接由 Phase 09 的“所有 fallback BLOCKED、FREE_FORM 固定提示”引起，属于真实耦合，不是无关扩展。
  - 仓库无 `package.json`，`npm test` 返回 `Missing script: test`；真实 JS suite 是 Maven exec 与 `node --test`。
- 触发频率：每次按计划复验或尝试严格做 scope audit。
- 影响：当前实现无法取得 scope compliance；后续 verifier 会重复遇到无效命令。
- 最小处理：见下方“待批准计划修订”。不新增产品文件，不引入 npm 工程。

## 待批准计划修订

### Amendment A-1：补入训练模拟回归测试文件（待批准）

- 将 `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` 加入 Phase 09 文件范围。
- 理由：Phase 09 改变所有 fallback 的正文/readiness，该既有 controller 测试必然需要同步确定性断言；不改变生产范围。
- 批准后当前该文件的变更可纳入 scope；未批准则必须回退，但回退会与已批准 Phase 09 行为及全量测试冲突。

### Amendment A-2：修正无效 JS 命令（待批准）

- 将三个子计划中的 `npm test` 替换为 `node --test src/test/js/*.test.js`。
- `mvn clean test` 中现有 JS exec 仍保留为第二条全量证据。
- 不新增 `package.json`，不安装 npm 依赖。

## 修复任务顺序

1. 先明确批准/拒绝 A-1、A-2；未批准不得声称 scope/命令闭环。
2. T1：修复 P1-1 transport seam，并先跑 client/Draft/controller 目标测试。
3. T2：修复 P1-2 UI gate，跑 48 条目标 JS 及新增 success/failure matrix。
4. T3：修复 P1-3 plan-driven fallback；随后补 P1-4 七问/V81 精确测试。只有 fixture 实际失败才最小改 intent/selection 实现。
5. T4：修复 P1-5 历史 formatter/messageId；补 controller history 集成。
6. T5：修复 P1-6 共享姓名 policy；补 preview/plain/HTML/AI profile 矩阵。
7. T6：跑全部目标组、全量 Maven、全量 Node、diff check；人工 A-n 仍保持 PENDING。

各任务可由不同 agent 实现，但 `AiReplyDraftService.kt`、`AiReplyContextBuilder.kt`、共享测试文件存在交叉写入，禁止并行修改同一文件；合并顺序固定 T1→T2→T3→T4→T5。

## 机器验收

### Phase 08

- client 对 success/blank/read timeout/429/5xx/network/空 URL 返回唯一分类；日志捕获或源码负向断言不存在 exception message、statusCode、body、URL、Authorization、apiKey。
- 首次成功 1 call；瞬态失败后成功 2 calls 且无 failure warning；二次失败 2 calls；transport retry 成功但 JSON invalid 后 correction 总计 3；correction 失败不重试。
- response/audit 只含最终 transport warning；重试恢复无 warning；audit exception 不影响响应。
- 每个非成功 result 都先渲染固定 banner；trust/legacy 采用按钮原生 disabled；handler 二次拒绝；失败前后 success session state 与 instruction 均正确。
- 事实名称五级场景按固定优先级；可见文本无“未命名事实”、rule/intent/coverage/warning code。
- S-1/S-2 CSS 与批准文本逐字一致；S-3 只增加 heading id，布局不变。

### Phase 09

- 七问 intent 与状态逐项严格相等，总计 `4/1/2`；三个负向 evidence 分配全部为空。
- V81 静态合同通过；真实库 pre/post 仍由人工 A-1/A-2 完成，且迁移已应用后不得修改原 SQL。
- fallback 首行、FREE_FORM 固定句、无邮件 frame/CTA；sourceIds 等于 plan claims 并集；history/lastDraft/turns A/B 完全相同。
- 所有 usedLlm=false readiness=BLOCKED；成功 LLM readiness 仍按既有规则；auto decision 继续拒绝 fallback。

### Phase 10

- history 仅 INBOUND/SENT OUTBOUND；current messageId 精确排除；最近 8、subject<=160、body<=800、total<=5000、完整 block、确定排序。
- 历史只输出 `[EXPERT]/[OUR_TEAM]`、`Subject:`、`Body:`；prompt 两路径含逐字 continuity marker；fallback A/B 与 history 无关。
- 正常姓名按 given/family；EMAIL/ORCID/email/esDocId/含@全部为空；preview/plain/HTML 与 AI profile 使用同一 policy。

### 必跑命令

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=HttpLlmDraftClientTest,AiReplyDraftServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest,AiReplyIntentCatalogTest,QaFactSelectionServiceTest,AiReplyPointByPointComposerTest,QaRuleManagementServiceTest,AiReplyContextServiceTest,MailVariableServiceTest,AiTrainingSimulateTest
node --test src/test/js/aiReplyLoadingFeedback.test.js src/test/js/trustReplyWorkbench.test.js src/test/js/aiReplyReviewConfirmation.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
node --test src/test/js/*.test.js
git diff --check
```

## 完整合规审计

### Phase 08

| 约束 | 结果 | 证据 |
|---|---|---|
| I-1 分类且不泄密 | ❌ | `HttpLlmDraftClient.kt:189-195,231-233`；P1-1 |
| I-2 有界重试 | ✅ | `AiReplyDraftService.kt:463-489` 首次最多 2；`384-390` correction 单次，无 transport retry |
| I-3 warning 单源/四状态 | ❌ | `AiReplyDraftService.kt:336-356,1391-1398` CLIENT_UNAVAILABLE 坍缩；P1-1 |
| I-4 失败不可采用/不推进 | ❌ | handler/state 主体已隔离，但 `app.js:8615-8646` 按 failureCode 而非 failure 判 disabled；P1-2 |
| I-5 人工发送独立 | ✅ | `aiReplyReviewConfirmation.test.js:39-47` 与 send handler 不携带 AI generation gate |
| I-6 banner 优先 | ❌ | `app.js:3956-3963` 不读 generationState；旧断言见测试 `107-116`；P1-2 |
| I-7 可读事实名称 | ✅ | `app.js:3806-3825,8560-8588` 固定优先级与最终“事实名称缺失” |
| I-8 最终审计 | ✅（测试需补） | `UnmatchedInboundMailController.kt:326-335` 记录最终 result；`AiReplyReviewAuditService.kt:72-105,109-163` warning snapshot/best-effort |
| S-1 banner 样式/DOM | ✅ | `styles.css:6001-6025` 与批准 CSS 一致；`app.js:3968-3972` DOM 一致 |
| S-2 disabled 样式/属性 | ❌ | CSS `6027-6034` 一致；属性只在 failureCode 非空写入，P1-2 |
| S-3 DOM/标题 | ✅ | `app.js:8863` 只给 h4 增加 id，原三栏层级未改 |

### Phase 09

| 约束 | 结果 | 证据 |
|---|---|---|
| I-1 识别/事实分离 | ✅ | `QaFactSelectionService.kt:115-129` keyword candidate 后再 assignment/evidence |
| I-2 原子 intent 与 4/1/2 | ❌ | definitions 已新增，但精确 fixture 缺失且 `AiReplyDraftServiceTest.kt:2579-2582` 放宽；P1-4 |
| I-3 语义相交单分配 | ✅ | `AiReplyIntentCatalog.kt:366-419` score>0、单 target、catalog tie-break |
| I-4 V81 写边界 | ✅（人工 pre/post PENDING） | `V81...sql:5-51` 3 UPDATE、独立 CASE/NOT LIKE、`updated_at=updated_at`，未写 unsupported 词 |
| I-5 内部参考 | ✅ | `AiReplyPointByPointComposer.kt:83-122` 固定首行；DraftService FREE_FORM 固定句 |
| I-6 只读 content plan | ❌ | DraftService 收到 plan 却未使用；composer 只读 requestFacts，P1-3 |
| I-7 可读来源、不泄键 | ✅ | `AiReplyPointByPointComposer.kt:124-131` 四级来源名 |
| I-8 fallback BLOCKED | ✅ | `AiReplyDraftService.kt:658-674` usedLlm=false/readiness=BLOCKED |

### Phase 10

| 约束 | 结果 | 证据 |
|---|---|---|
| I-1 真实往来集合 | ✅ | `AiReplyContextBuilder.kt:37-43` 仅 INBOUND 与 SENT OUTBOUND |
| I-2 当前 messageId 精确排除 | ❌ | controllers 已传精确 ID，但 Builder `44-53` 归一化顺序错误；P1-5 |
| I-3 最近 8/完整 5000 | ❌ | 排序/160/800 已有；`90-100` 漏计 block 分隔符；P1-5 |
| I-4 稳定角色格式 | ❌ | role 正确且无 metadata，但 `80-87` 会省略 Subject/Body 固定行；P1-5 |
| I-5 continuity-only | ✅ | `AiReplyDraftService.kt:1203,1282` 两路径逐字 marker |
| I-6 history 不进 fallback | ✅ | fallback 路径不读 mailHistory；但需 A/B 测试补强 |
| I-7 真人姓名 | ❌ | `MailVariableService.kt:200-202` 仍读 displayName；无技术 ID 集合比较，P1-6 |
| I-8 全入口同策略 | ❌ | MailVariable 与 ContextBuilder 分别拼接/过滤；共享 policy 未实现，P1-6 |

### 汇总检查

- Accumulation check：✅ provider 调用不存在跨请求累计；单操作 source path 最多 3 次。
- State-machine check：❌ disabled/client fallback 的前端按钮/标题状态由 warning 偶然决定，见 P1-2。
- Cross-plan check：❌ Phase 09 依赖 Phase 08 失败门禁，但该门禁对无 warning fallback 不完整；fallback 又未消费 Phase 09 plan。
- Deleted code：❌ 旧发送式 `composeFallback()`、未使用 `fallbackDraftText()`/FREE_FORM deterministic method 仍在，仅旧测试引用。
- No extras：❌ `AiTrainingSimulateTest.kt` 超出三计划清单；A-1 待批准。
- Scope compliance：❌ 同上；其余业务实现文件均在三个计划并集内。
- Plan quality gate：✅ 不需要拆分；发现的是一个真实耦合测试漏列和一个无效命令，可用 A-1/A-2 两项窄修订解决。
- Manual acceptance：PENDING；不得用机器测试替代 V81 线上 pre/post、浏览器视觉、真实姓名与发送回归。

## 非阻塞观察

- `freeFormCallCount`、`callCount`、`asksAdvisoryDuration` 未使用；只在对应 P1 文件已被修改时删除或用于批准的日志/断言，不单独扩范围。
- 多处嵌套 `return@forEach` 有歧义编译 warning；P1-3 重写 composer 时可改为显式循环，不做全仓清理。
- 现有全量测试通过说明回归面可运行，不代表新增契约已被测试；禁止以绿色 suite 直接签发部署。

## 修复后签发条件

1. A-1/A-2 有明确人工批准记录。
2. P1-1～P1-7 全部 `RESOLVED`，完整 checklist 重新复验，不只跑新增测试。
3. 必跑机器命令全部 PASS；无 `BLOCKED` 项。
4. V81 线上 A-1/A-2 完成前，只能签发代码机器验证，不能签发正式数据库部署。
5. 人工验收仍为 PENDING 时，报告必须明确“机器通过不等于最终人工验收”。
