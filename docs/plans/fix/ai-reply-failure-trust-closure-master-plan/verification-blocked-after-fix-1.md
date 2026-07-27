# AI 回复失败可见性与信任闭环：fix-1 后复验阻塞报告

## 验证元数据

- 日期：2026-07-21
- 总计划：`docs/plans/2026-07-21/ai-reply-failure-trust-closure-master-plan.md`
- 子计划：
  - `docs/plans/2026-07-21/ai-reply-08-llm-failure-workbench-contract.md`
  - `docs/plans/2026-07-21/ai-reply-09-fallback-reference-intent-parity.md`
  - `docs/plans/2026-07-21/ai-reply-10-history-context-recipient-identity.md`
- 上一轮：`docs/plans/fix/ai-reply-failure-trust-closure-master-plan/fix-1.md`
- 授权模式：`WORKFLOW_ARTIFACTS`
- 当前轮次：尝试实施 `fix-1` 后复验
- 结果：`EARLY_BLOCKED`
- 代码基线：`ba8e63fc`（工作区存在三个 phase 的未提交实现）
- 本轮写入：仅本报告与实际命中的知识记录；未修改产品代码、产品测试或迁移。

## 为什么没有生成 fix-2

`fix-v` 规定：上一轮 P1 的同一根因和同一修复规格仍存在时，必须早停，不能消耗下一设计轮次重复创建 `fix-2`。

当前 `P1-1` 至 `P1-6` 均未完整关闭；其中 `P1-2/P1-4` 仍保留上一轮逐字指出的代码/测试，`P1-3` 的修复还引入了“纯缺失问题不展示”的回归。正确动作是继续完成现有 `fix-1`，不是另建同内容的 `fix-2`。

另有三项范围/命令修订尚无明确批准记录，当前也不能签署 scope compliance。

## 构建与测试证据

| 项目 | 状态 | 证据 |
|---|---|---|
| 目标 Kotlin 测试组 | PASS | 273 passed，0 failed，0 skipped；覆盖三个子计划列出的目标类及 `AiTrainingSimulateTest` |
| 目标 JS 测试 | PASS | 48 passed，0 failed，0 skipped |
| `mvn clean test` | PASS | `BUILD SUCCESS`；JVM/Kotlin 1,798 tests，0 failures，0 errors，4 skipped；Maven 内嵌 Node 336 passed |
| `node --test src/test/js/*.test.js` | PASS | 336 passed，0 failed |
| 原计划 `npm test` | FAIL | exit 1：`Missing script: "test"`；仓库无 npm test script |
| `git diff --check` | PASS | 写入本报告前无空白错误；产物写入后再次复核 |
| Phase 09 V81 真实库 pre/post | BLOCKED | 本轮未连接目标库；人工 A-1/A-2 仍待执行 |
| 全部人工验收 | PENDING | 机器测试不能替代页面、真实 provider、目标库和实发链验收 |

绿色 suite 不能证明完成：多项强制契约测试尚未写入，且现有测试仍明确接受被计划禁止的旧行为。

## P1 lineage 汇总

| ID | Lineage | 当前状态 | 核心阻塞 |
|---|---|---|---|
| P1-1 | PERSISTENT | ❌ | transport 源码大体收口，但 timeout 分类和完整分类/retry/audit 测试未闭环 |
| P1-2 | PERSISTENT | ❌ | 失败仍清空修改要求；缺失状态 fail-open；旧 toast/“未命名事实”契约仍在 |
| P1-3 | PERSISTENT | ❌ | fallback 已接 plan，但不是严格投影；混合 coverage 会遗漏纯 missing 请求，旧发送式 API 未删 |
| P1-4 | PERSISTENT | ❌ | 七问 `4/1/2`、负向 evidence、V81 静态合同均无测试；宽断言仍在 |
| P1-5 | PERSISTENT | ❌ | history 运行时主体已修，但强制 formatter/controller 矩阵未实现 |
| P1-6 | PERSISTENT | ❌ | 姓名过滤仍是两套策略，familyName 未比较 contact 技术 ID；完整渲染矩阵缺失 |
| P1-7 | PERSISTENT / AUTHORITY | BLOCKED | A-1/A-2 未批准，另发现 `IntroductionMailComposerTest` 的真实范围耦合需 A-3 |

## 详细剩余修复计划（继续执行 fix-1）

### T0：先批准三项窄修订

#### Amendment A-1：训练模拟测试范围

- 将 `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` 加入 Phase 09 范围。
- 原因：fallback 正文/readiness 改动必然影响训练模拟 controller 契约。
- 不批准：回退该文件的计划外修改，但 Phase 09 controller 回归将没有直接覆盖。

#### Amendment A-2：JS 全量命令

- 将三个子计划的无效 `npm test` 替换为 `node --test src/test/js/*.test.js`。
- 保留 Maven 内嵌 Node suite 作为第二份全量证据。
- 不新增 `package.json` 或 npm 依赖。
- 不批准：原计划必跑命令持续 FAIL，机器签发不可能通过。

#### Amendment A-3：首次介绍邮件调用方测试范围

- 将 `src/test/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposerTest.kt` 加入 Phase 10 范围。
- 原因：`IntroductionMailComposer` 直接消费 `MailVariableService.buildVariables()`；姓名来源由 `displayName` 改为真人字段后，该既有测试必须同步验证变量值。
- 仅增加测试范围，不增加生产文件、状态或持久化写路径。
- 不批准：必须回退该测试变更，且首次介绍邮件的技术 ID 称呼回归无直接契约。

最小人工决定：明确回复“批准 A-1、A-2、A-3”或逐项拒绝。批准前不得宣称范围和命令闭环。

### T1：完成 P1-1 transport 单源失败合同

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClientTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

当前证据：

- 七分类、单 HTTP seam、安全日志主体已存在：`HttpLlmDraftClient.kt:47-87,129-209`。
- timeout 仍读取 `ResourceAccessException.message` 判型：`HttpLlmDraftClient.kt:173-178`；异常文案变化会导致不稳定分类。
- `HttpLlmDraftClientTest.kt:46-81` 仍只有 2 个 model mapping 测试。
- DraftService 已有首次最多重试一次、结构修复一次的代码，但没有 1/2/3 provider-call 强制矩阵。

剩余规格：

1. timeout 依据稳定异常类型/cause chain 分类，不依赖 provider/URL/异常文案；日志继续禁止 exception message、status、body、URL、Authorization 和 key。
2. `HttpLlmDraftClientTest` 覆盖 success、blank、read timeout、429、5xx、network、空 URL及日志负向边界。
3. `AiReplyDraftServiceTest` 精确覆盖：首次成功 1 call；瞬态失败后成功 2 calls且无失败 warning；两次失败 2 calls；retry 成功但 JSON 非法后 correction 总计 3 calls；correction 失败不再 transport retry。
4. controller/audit 断言只记录最终 warning；重试恢复不残留瞬态 warning；audit 写失败仍返回页面结果。
5. `CLIENT_UNAVAILABLE` 必须保持 `FALLBACK_CLIENT_UNAVAILABLE`；其他失败为 `FALLBACK_NO_RESPONSE + 唯一主 warning`。

### T2：完成 P1-2 前端 fail-closed 与事实名称合同

文件：

- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`（只复核批准的现有数值，预计不改）
- `src/test/js/aiReplyLoadingFeedback.test.js`
- `src/test/js/trustReplyWorkbench.test.js`
- `src/test/js/aiReplyReviewConfirmation.test.js`

当前证据：

- banner 已先于 readiness 渲染，批准 CSS 已存在：`app.js:3970-3980`、`styles.css:6001-6031`。
- legacy 生成失败仍无条件清空输入：`app.js:9767`。
- trust 采用在 `draftResult` 缺失时 fail-open：`app.js:8623-8625,9641-9645`；legacy entry/字段缺失也可走 fallback state：`app.js:9800-9814`。
- failure reason 按 warnings 输入顺序取首项，未实现固定“transport → trust repair → generationState”优先级：`app.js:3790-3811`。
- 旧 toast 仍显示“结构化规则草稿”：`app.js:3742-3751,9769-9774`。
- 计划要求删除的 `UNNAMED_FACT_LABEL` 仍在 `app.js:145`；`trustReplyWorkbench.test.js:184-238` 反而断言该旧常量必须存在。
- 生产 `findSuggestRule()` 只查 `rulesByCategory`，测试 fake 却额外查 `suggestedRules`，导致生成前名称回查不真实。

剩余规格：

1. 所有采用入口严格要求 `result` 存在且 `usedLlm===true && generationState==="LLM_USED"`；缺失/损坏状态一律拒绝。
2. 失败不清空 operator instruction，不推进 `firstTurnDone/turns/lockedFactIds/lastDraft*`；成功才清空和推进。
3. reason resolver 使用固定优先级；fallback toast 与标题明确写“LLM 生成失败 / QA 规则参考内容”，不得再伪装成普通规则草稿。
4. 统一生产事实名称 resolver，真实查 `evidenceSources → suggestedRules/rulesByCategory displayName → sectionTitle → replySubject → 事实名称缺失`。
5. 删除 `UNNAMED_FACT_LABEL` 及过时测试；源码/DOM 不出现“未命名事实”、规则编号或内部 ID。
6. 用行为测试覆盖四 generationState、五 transport warning、trust exhausted、失败/成功按钮属性、前后历史草稿、input 保留、人工编辑器独立发送、DOM/CSS 逐字合同。

### T3：完成 P1-3 plan-only fallback

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

当前证据：

- `composeFallbackReference()` 的 source 分组遍历全部 `plan.claims`：`AiReplyPointByPointComposer.kt:92-96`，不是 paragraph 引用 claim 的严格投影。
- `orderedIndices` 只取有 claim 的 paragraph 请求；只要存在任一 claim，纯 `missingFacts` 请求就被丢弃：`AiReplyPointByPointComposer.kt:98-117`。七问 mixed coverage 下第 2/4 问不会展示。
- fallback 整体仍经过 action sanitizer：`AiReplyDraftService.kt:654-656`，无法保证 answerBody 原文和 A/B 不变。
- 旧发送式 `composeFallback()` 与仅服务旧测试的 helper 仍存在：`AiReplyPointByPointComposer.kt:58-80,178-193,221-233`。
- `AiReplyPointByPointComposerTest` 仍只覆盖旧 API；新 reference 无 sourceIds/missing/order/A-B 专用测试。

剩余规格：

1. 先按 `plan.paragraphs.claimKeys` 顺序解析被引用 claims；只使用这些 claims 的 sourceIds。
2. 展示请求集合必须是“paragraph-referenced claims 对应请求”与 `plan.missingFacts.requestIndex` 的并集，并按原始请求顺序输出；PARTIAL 同时显示事实和缺失行。
3. repository 查询 ID 必须精确等于被引用 sourceIds 并集；全局稳定去重；不扫描 candidate/sendQaRuleIds。
4. reference composer 直接产出最终内部参考，不再把完整 reference 交给发送正文 sanitizer 静默改写；旧数据含 CTA 时应暴露为数据/测试问题，不能把改写后的文本伪称 answerBody 原文。
5. 删除旧 `composeFallback()`、仅供其使用的发送式组装/helper；不得删除仍被成功 LLM 路径调用的方法。
6. 测试覆盖 GROUNDED/PARTIAL/UNSUPPORTED 混合、纯 missing、source 顺序/去重、来源四级 fallback、无邮件 frame/CTA/内部键、history/lastDraft/operatorTurns A/B、FREE_FORM 固定句、readiness 恒 BLOCKED。

### T4：完成 P1-4 七问与 V81 可执行合同

文件：

- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`
- 仅当精确 fixture 失败时，窄改 `AiReplyIntentCatalog.kt` / `QaFactSelectionService.kt`。

当前证据：

- 三个新原子 intent 已存在，但四个要求的测试文件没有七问/V81/new-reference 合同。
- `AiReplyDraftServiceTest.kt:2563-2583` 仍允许 `PARTIAL || GROUNDED`，违反单一精确预期。
- `scoreRuleIntentAlignment()` 只做 `phrase.contains(keyword)`：`AiReplyIntentCatalog.kt:386-419`。既有 Funding 规则含 `salary/funding/compensation`，matching 规则含 `enterprise projects`；复合问题中可能分别误支持 compensation structure 和 enterprise examples。
- V81 本体当前静态看满足 3 UPDATE、独立防重、只写 keywords/updated_at，但 `QaRuleManagementServiceTest` 无 V81 引用。

固定七问 fixture：

1. `Before I submit my CV for the preliminary assessment, I would appreciate some additional information regarding the collaboration: Is the research advisory role compensated?`
2. `If so, could you please provide information about the remuneration structure?`
3. `What is the expected time commitment and typical duration of advisory projects?`
4. `Could you share examples of the types of Chinese enterprises or institutions involved in the program?`
5. `How are intellectual property rights, publication authorship, and research confidentiality managed?`
6. `Will a formal agreement or contract be provided before any collaboration begins?`
7. `Are there any costs or obligations for participants at any stage of the process?`

剩余规格：

1. 先新增精确 intent 与 selection fixture，严格断言状态序列 `GROUNDED, UNSUPPORTED, PARTIAL, UNSUPPORTED, GROUNDED, GROUNDED, GROUNDED`，总计 `4/1/2`。
2. 负向断言：compensation availability 不支持 compensation structure；duration 不支持 time commitment；matching 不支持 enterprise examples；一个 rule ID 不分配给两个原子 intent。
3. fixture 若失败，只在 Catalog/Selection 内做最小语义分配修复；不得给 QA 规则新增 intent/信任标签或 schema 字段，不得添加无依据数据库关键词。
4. 把宽断言改为单一精确状态。
5. V81 静态测试断言只有 3 个 UPDATE、每个短语独立 CASE/NOT LIKE、`updated_at=updated_at`、无 unsupported 词、无其他列写入。
6. 目标库执行人工 pre/post；V81 一旦应用不得修改原文件，异常只能新建后续迁移。

### T5：完成 P1-5 history 回归合同

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`
- A-1 批准后：`src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`

当前证据：

- eligibility、trim/尖括号 messageId、确定排序、8/160/800/5000、固定 role/Subject/Body 的运行时主体已实现：`AiReplyContextBuilder.kt:37-100`。
- 两个 controller 已传当前精确 messageId。
- 现有 Context 测试只把一个旧 OUTBOUND fixture 改成 SENT；controller fixture 的 messageId 基本为 null。

剩余规格：

1. 抽取 current/record 共用的 messageId normalization helper，避免两处逻辑以后漂移。
2. 测试覆盖 12 封、乱序、同时间 id tie-break、最近 8、160/800、总长 5000、完整 block、cleanedBody 优先、空字段固定行和 metadata 负向。
3. 测试 eligibility：INBOUND、SENT OUTBOUND 保留；FAILED/PENDING/UNKNOWN/null/其他 direction 排除。
4. 测试当前 `<id>` 归一化排除、current ID 为空不做正文猜测、正文相同但 ID 不同的历史保留。
5. 收件箱与训练 controller 捕获传给 DraftService 的 history，证明所选当前邮件排除且旧真实往来保留。
6. 两个 prompt 的逐字 continuity marker、高风险 history 无 authority、fallback history A/B 不变均需专用测试。

### T6：完成 P1-6 单一真人姓名策略

文件：

- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextServiceTest.kt`
- A-3 批准后：`src/test/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposerTest.kt`

当前证据：

- `displayName` fallback 已移除。
- AI profile policy 位于 `AiReplyContextBuilder.kt:121-180`，邮件 full-name policy 位于 `MailVariableService.kt:188-216`，仍是两套逻辑。
- `expertFamilyName` 调 `resolveFamilyName(expert)`，不接收 contact：`MailVariableService.kt:37-41`；familyName 只比较 profile IDs，无法完整执行 contact 技术 ID 集合合同。
- `MailVariableServiceTest` 与 `AiReplyContextServiceTest` 没有技术 ID/三渲染入口矩阵。

剩余规格：

1. 按原计划在 `MailVariableService.kt` 定义 module-internal `ExpertRecipientNamePolicy`；MailVariable 与 ContextBuilder 直接复用，不保留第二套拼接/过滤。
2. `expertName` 只由有效 `givenNames + familyNames` 组成；`expertFamilyName` 只来自有效 familyNames；不读取 `displayName`。
3. 每个候选按 trim 后检查空值、`@`、`EMAIL-*`、ORCID pattern，并与 profile/contact 的 orcidId/email/esDocId 比较；技术候选输出空串。
4. preview/plain/HTML/首次介绍邮件与 AI profile 对同一 profile 得到一致结果；非法时 placeholder fallback 为 `Professor`，AI profile 不写非法 `Name:`。
5. 测试正常 full/given/family、EMAIL-*、ORCID、email、含@、esDocId、contact 技术字段、混合字段、首尾空白，以及 preview `usedFallback=true`。

### T7：统一复验，不按“测试绿”提前结束

批准 A-1/A-2/A-3 并完成 T1-T6 后，依次运行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=HttpLlmDraftClientTest,AiReplyDraftServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest,AiReplyIntentCatalogTest,QaFactSelectionServiceTest,AiReplyPointByPointComposerTest,QaRuleManagementServiceTest,AiReplyContextServiceTest,MailVariableServiceTest,AiTrainingSimulateTest,IntroductionMailComposerTest

node --test src/test/js/aiReplyLoadingFeedback.test.js \
  src/test/js/trustReplyWorkbench.test.js \
  src/test/js/aiReplyReviewConfirmation.test.js

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
node --test src/test/js/*.test.js
git diff --check
```

随后重新运行完整 `fix-v` checklist；不得只复验本轮新增测试。

## 完整合规审计

### Phase 08

| 约束 | 状态 | 摘要 |
|---|---|---|
| I-1 分类/不泄密 | ❌ | seam/log 主体已修；timeout 稳定分类与强制 client 测试缺失 |
| I-2 有界重试 | ❌ | 运行时代码最多 1/2/3，但机器矩阵缺失 |
| I-3 warning/四状态 | ❌ | mapping 已有；唯一 warning、恢复无 warning、audit 同源测试缺失 |
| I-4 失败不可采用/不推进 | ❌ | legacy input 清空且缺失状态 fail-open |
| I-5 人工发送独立 | ✅ | 纯人工 handler 未新增 AI gate |
| I-6 banner 优先 | ❌ | banner 主体已实现；reason 优先级、旧 toast、完整行为测试未闭环 |
| I-7 可读事实名称 | ❌ | 旧常量仍在；生产 lookup 与测试 fake 不一致 |
| I-8 最终审计 | ❌ | 保存链存在；最终 warning/恢复/audit exception 合同测试缺失 |
| S-1/S-2/S-3 | ❌ | CSS/DOM 主体存在，但逐字、首 child、success restore、层级快照测试不完整 |

### Phase 09

| 约束 | 状态 | 摘要 |
|---|---|---|
| I-1 识别/事实分离 | ❌ | 主链存在；精确 fixture 未证明且词面分配仍可误授权 |
| I-2 原子 intent / 4-1-2 | ❌ | definitions 已增；宽断言与七问测试缺口仍在 |
| I-3 单规则语义分配 | ❌ | 单 target 存在，但 broad keyword 可能分给结构/实例 intent |
| I-4 V81 写边界 | ❌ | SQL 静态看正确；静态测试与真实 pre/post 未完成 |
| I-5 内部参考 | ❌ | 首行/固定句存在；纯 missing 会消失且 reference 仍被 sanitizer 改写 |
| I-6 只读当前 plan | ❌ | 已接 plan，但 source/missing 不是严格投影 |
| I-7 可读来源 | ✅ | 四级 fallback 存在；专用回归测试仍需补 |
| I-8 fallback BLOCKED | ✅ | `usedLlm=false` 固定 BLOCKED；自动门禁保持 |

### Phase 10

| 约束 | 状态 | 摘要 |
|---|---|---|
| I-1 真实往来集合 | ❌ | 运行时已实现；完整 eligibility 测试缺失 |
| I-2 当前 messageId | ❌ | 两入口已传且 formatter 已排除；helper/controller 测试缺失 |
| I-3 最近 8 / 完整 5000 | ❌ | 运行时主体已实现；边界矩阵缺失 |
| I-4 固定角色格式 | ❌ | 运行时主体已实现；snapshot/metadata 负向测试缺失 |
| I-5 continuity-only | ❌ | 两 prompt marker 已有；authority 测试缺失 |
| I-6 history 不进 fallback | ❌ | 运行时不读取 history；A/B 测试缺失 |
| I-7 真人姓名 | ❌ | displayName 已移除；contact family 技术 ID 仍可漏过 |
| I-8 全入口同策略 | ❌ | 三渲染入口汇入 buildVariables，但 AI/mail policy 尚未统一 |

## 汇总检查

```text
Accumulation check: ✅
State-machine check: ❌
Cross-plan check: ❌
Deleted code: ❌
No extras: ❌
Scope compliance: BLOCKED
Manual acceptance: PENDING
```

- Accumulation：当前 provider 主生成最多 2 次，后续 correction 1 次，总上限 3；未发现跨操作累计状态。
- State machine：失败输入清空、缺失 result/entry fail-open，仍可破坏不可采用合同。
- Cross-plan：Phase 09 mixed fallback 漏缺失项；Phase 08 前端门禁仍不完全；Phase 10 history/fallback 边界无 A/B 机器证明。
- Deleted code：旧 `composeFallback()` 及其发送式 helper 未删除。
- No extras：`AiTrainingSimulateTest.kt` 与 `IntroductionMailComposerTest.kt` 超出当前批准并集。
- Scope：A-1/A-2/A-3 未批准。
- Plan quality gate：三个子计划无需重新拆分；问题可由现有 fix-1 加三项窄 amendment 收敛。

## 人工验收与部署门禁

- Phase 08 A-1～A-8：`PENDING`。
- Phase 09 A-1～A-8：`PENDING`；尤其 V81 目标库 pre/post 未执行。
- Phase 10 A-1～A-8：`PENDING`。
- 当前不得签发部署。即使 T1-T7 机器复验全部通过，真实 provider failure UI、V81 pre/post、长线程、技术 ID 称呼、成功/失败/纯人工发送仍需按原计划人工验收。

## 解除阻塞条件

1. 人工明确批准或拒绝 A-1/A-2/A-3。
2. 继续完成现有 `fix-1` 的 T1-T7；不得创建内容重复的 fix-2。
3. P1-1～P1-7 全部复验为 `RESOLVED`。
4. 所有批准后的必跑命令 PASS；无 `BLOCKED` 机器项。
5. V81 目标库 pre/post 与其余人工 A-n 保留真实证据后，才评估正式部署。
