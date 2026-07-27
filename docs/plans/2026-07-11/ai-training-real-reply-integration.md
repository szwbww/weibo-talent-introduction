# 计划 2:AI 训练逻辑打通真实生成 + 模拟按钮 loading 遮罩

> 日期:2026-07-11
> 前置:计划 1(`qa-keyword-gap-v68.md`)先行合并——训练模拟走真实匹配后,需要新关键词才能命中演示。
> 背景:AI 训练模拟与真实收发件 AI 生成(`aiReplyTurn`)共用 `AiReplyDraftService.generate()`,但注入不对齐:训练知识库(`ai_training_qa`)只进模拟、不进真实路径;模拟被 `simulateOnly` 强制 FREE_FORM,永远不体现 QA_MATCHED 真实行为;FREE_FORM 下 `promptRuleIds` 回退全集实际无人消费。另:「生成模拟回复」按钮无 loading 态。

## 需求描述

Observable outcomes:

1. 真实收发件工作台的「AI 生成回复」在 FREE_FORM 模式下,prompt 中携带训练知识库(`ai_training_qa`)内容——训练页维护的知识对真实草稿生效。
2. AI 训练模拟不再强制 FREE_FORM:来信命中 QA 规则时,模拟结果 mode 显示 `QA_MATCHED`,草稿为规则段落 verbatim 拼接——训练页真实反映线上行为。
3. FREE_FORM 生成的 prompt 中携带 QA 规则正文(命中集;无命中回退全集),并附「事实只能来自所给知识」约束——自由生成不再脱离规则口径。
4. 点击「生成模拟回复」后,消息区出现遮罩(半透明白底 + 旋转 spinner + 文案「AI 生成中…」),按钮置灰;完成或失败后遮罩消失、按钮恢复。

What must NOT change:

- `mail_record_qa_rule` 只关联真实匹配子集:`aiReplyTurn` 返回的 `qaRuleIds` 语义不变;模拟响应(`AiTrainingSimulateResponse`)一如既往不携带 qaRuleIds、不落审计。
- QA_MATCHED 的 verbatim 拼接契约:`buildMatchedMessages` / `buildMatchedSystemPrompt` / `buildMatchedUserContent` 的 prompt 内容不变。
- fallback / `composeSimulateDeterministicDraft` 确定性文本源不注入任何新内容。
- LLM 超时行为:仍走既有 `llmRestTemplate`(connect/read timeout 已接 `talent-introduction.llm.timeout-ms`),不新增 HTTP client。
- 前端零新增 CSS,零新增 class。

Out of scope:

- prompt-config constraints 的具体内容属运营配置,不写死代码(见 A-6 的配置步骤)。
- QA_MATCHED 模式注入 few-shot / 知识(违反 verbatim 契约,明确不做)。
- 训练模拟落库任何生成记录。

## 关键不变量

### Invariant I-1: 双语义 qaRuleIds
- Rule: `promptQaRuleIds`(给 LLM 的知识范围,可回退全集)与 `sendQaRuleIds`(发送/审计用真实匹配子集)语义分离;任何进入 `mail_record_qa_rule` 的 id 集只能来自 `QaMatchService.suggestComposition().suggestedRuleIds` 或运营显式勾选。模拟路径产物永不进入审计。
- Applies to: `AiReplyDraftService.resolveQaRules` / `generate`;`UnmatchedInboundMailController.aiReplyTurn`;`AiTrainingController.simulate`。
- Violation consequence: 审计表关联无关全集规则(历史 P1,K-ai-reply-prompt-vs-send-rule-ids 反例)。
- 来源: K-ai-reply-prompt-vs-send-rule-ids

### Invariant I-2: 单一 seam 注入
- Rule: 所有 FREE_FORM prompt 增强(知识库、QA 规则正文、约束)只改 `buildFreeFormMessages` / `buildFreeFormUserContent` 这一个 seam;不在 controller 层各改一份;不触碰 `buildMatchedMessages`。
- Applies to: `AiReplyDraftService.kt`。
- Violation consequence: 两个调用方(工作台/训练)行为漂移,QA_MATCHED verbatim 契约被污染。
- 来源: K-ai-generate-single-freeform-seam

### Invariant I-3: 模拟只读
- Rule: `simulateOnly=true` 路径不产生任何持久化写入(不落 mail_record、不落审计、不落 pending);去掉强制 FREE_FORM 后该性质必须保持——模拟仅改变 prompt 构造,不新增写路径。
- Applies to: `AiReplyDraftService.generate`;`AiTrainingController.simulate`。
- Violation consequence: 训练操作污染业务数据。
- 来源: original

### Invariant I-4: prompt 尺寸上限
- Rule: 注入 FREE_FORM 的 QA 规则知识段整体 `take(12000)` 字符截断(与 `buildKnowledgeContext` 同标准);全集回退时按 `findAllEnabledOrdered` 顺序截断。
- Applies to: `buildFreeFormUserContent` 新增 KNOWLEDGE 段。
- Violation consequence: prompt 超限导致 LLM 请求失败,触发 fallback,用户看到确定性兜底而非 AI 草稿。
- 来源: original(对齐 AiTrainingQaService.buildKnowledgeContext:97)

### Invariant I-5: 知识注入点唯一
- Rule: `ai_training_qa` 知识经 `AiReplyContextBuilder.appendKnowledgeToProfile` 拼入 expertProfile,仅此一个注入通道;`aiReplyTurn` 与 `simulate` 均走它,不得在 prompt 其他位置重复注入。
- Applies to: `UnmatchedInboundMailController.aiReplyTurn`;`AiTrainingController.simulate`(已有)。
- Violation consequence: 同一知识在 prompt 中出现两份,挤占 I-4 预算。
- 来源: original

## 样式契约

### S-1: 模拟生成 loading 遮罩(纯复用,零新增)
- 复用: 整体复用既有函数 `setTagEditorLoading(editor, loading, message)`(app.js:3462-3480),它自包含地挂载/移除 `.tag-editor-loading`(styles.css:3344)、`.tag-editor-loading-overlay`(styles.css:3349)、`.tag-editor-spinner`(styles.css:3365)并托管 `aria-busy` 与内部按钮 disabled。禁止自造近似遮罩样式。
- 新增: 无任何新 class、无任何 styles.css 改动。
- DOM 结构: 遮罩容器 = `#aiTrainingSimulateMessages`(index.html:872,`.ai-chat-messages`,由 `.tag-editor-loading` 提供 `position:relative; min-height:72px`)。遮罩由函数动态挂载:
```html
<div id="aiTrainingSimulateMessages" class="ai-chat-messages tag-editor-loading" aria-busy="true">
    <div class="tag-editor-loading-overlay"><span class="tag-editor-spinner"></span><span class="tag-editor-loading-text">AI 生成中...</span></div>
</div>
```
- 附加行为(JS,非样式): `#aiTrainingSimulateBtn` 在请求期间 `disabled = true`(`setTagEditorLoading` 只管容器内按钮,该按钮在容器外,需显式置灰),`finally` 中恢复。
- 禁止项: inline style;新 class;修改 `.tag-editor-*` 既有规则块。`.tag-editor-*` 三个 class 的既有使用点仅 `setTagEditorLoading` 一处(app.js grep 核实),本计划为纯新增调用方,非修改。

## 现状审计

### `AiReplyDraftService.generate()`(llm/service/AiReplyDraftService.kt)
- 关键行为: `:58-63` `simulateOnly=true` 短路为 `ResolvedQaRules(emptyList(), emptyList())` → mode 永远 FREE_FORM;`:139-150` `resolveQaRules` 无匹配时 promptRuleIds 回退全集,但 `:91-101` FREE_FORM 分支调 `buildFreeFormMessages` **不传** promptRuleIds → 全集回退实际未消费;`:63` mode 由 `sendQaRuleIds.isNotEmpty()` 决定。
- Write paths: 无(纯生成,返回 `AiReplyDraftResult`)。
- Read paths(调用方,全库仅两个,K-ai-generate-single-freeform-seam 核实):
  1. `UnmatchedInboundMailController.aiReplyTurn`(:274-309)—— expertProfile 仅 `buildExpertProfile`,**无知识注入**;`qaRuleIds` 透传前端勾选。
  2. `AiTrainingController.simulate`(:176-209)—— expertProfile 走 `appendKnowledgeToProfile(profile, aiTrainingQaService.buildKnowledgeContext())`;`simulateOnly=true`;响应 `AiTrainingSimulateResponse` **不含 qaRuleIds**(:345-355 核实)。
- Interaction points:
  - C2(去掉强制 FREE_FORM)× simulate 响应:`result.qaRuleIds` 会变为非空,但响应体不映射该字段 → 无泄漏(I-1 保持)。
  - C2 × `fallback(simulateOnly=...)`:fallback 分支读 simulateOnly 决定确定性兜底文案,参数保留不动(I-3)。
  - C3(FREE_FORM 注入规则)× `LlmProperties.freeFormTemperature`:仅 prompt 内容变化,温度/超时不动。

### `ai_training_qa` 知识链
- `AiTrainingQaService.buildKnowledgeContext()`(:85-98):enabled 条目拼 Topic/Question/Answer,`take(12000)`。
- `AiReplyContextBuilder.appendKnowledgeToProfile`(:24-35):拼入 profile 尾部 "Training knowledge base:" 段。
- 消费点:目前仅 simulate。C1 使 aiReplyTurn 同样消费(I-5)。

### 前端(static/app.js, index.html, styles.css)
- `runAiTrainingSimulate()`(app.js:2880-2894):无 loading 态;`await api(...)` 期间可重复点击;绑定于 app.js:10026(catch 已有 showStatus)。
- 按钮 `#aiTrainingSimulateBtn`(index.html:876);消息区 `#aiTrainingSimulateMessages`(index.html:872)。
- 前端样式盘点:
  - 可复用:`.tag-editor-loading`(styles.css:3344,`position:relative;min-height:72px`)、`.tag-editor-loading-overlay`(styles.css:3349,`position:absolute;inset:0;z-index:4;flex 居中;gap:8px;background:rgba(255,255,255,0.76);color:var(--primary);font-size:13px;font-weight:600;backdrop-filter:blur(2px)`)、`.tag-editor-spinner`(styles.css:3365,16px 圆环,`border:2px solid rgba(var(--primary-rgb),0.22);border-top-color:var(--primary);animation:tag-editor-spin 0.7s`)。
  - 挂载函数:`setTagEditorLoading(editor, loading, message)`(app.js:3462-3480,通用容器,自建/自清 overlay)。
  - 改动前基线:`#aiTrainingSimulateMessages` 当前无 loading 相关 class/子元素;按钮无 disabled 逻辑。

## 实现方案

### 任务 T1(C1):aiReplyTurn 注入训练知识(I-1/I-5)
文件:`mail/controller/UnmatchedInboundMailController.kt`
- 构造器新增 `aiTrainingQaService: AiTrainingQaService` 依赖。
- `aiReplyTurn` 中 expertProfile 改为:`aiReplyContextBuilder.appendKnowledgeToProfile(baseProfile ?: "", aiTrainingQaService.buildKnowledgeContext())`(baseProfile 为现有 buildExpertProfile 结果,contact 缺失时用空串)。
- 其余(qaRuleIds 透传、响应结构)不动。

### 任务 T2(C2):模拟走真实匹配(I-1/I-3)
文件:`llm/service/AiReplyDraftService.kt`
- `generate()` 删除 `simulateOnly` 对 resolved 的短路:统一 `resolved = resolveQaRules(inboundText, qaRuleIds)`。
- `simulateOnly` 参数保留,仅继续传给 `fallback(...)`(确定性兜底文案分支语义不变)。
- mode 判定逻辑(`sendQaRuleIds.isNotEmpty()`)不动——匹配时模拟自然进 QA_MATCHED。

### 任务 T3(C3):FREE_FORM 注入 QA 规则知识(I-2/I-4)
文件:`llm/service/AiReplyDraftService.kt`
- `buildFreeFormMessages` 增参 `promptRuleIds: List<Long> = emptyList()`;`generate()` FREE_FORM 分支传 `resolved.promptRuleIds`。
- `buildFreeFormUserContent` 增 KNOWLEDGE 段(在 Expert profile 之前):
  - 标题行 `QA rule knowledge (authoritative facts):`,逐条 `[reply_subject]\n[reply_body]`,`joinToString("\n\n")` 后整体 `take(12000)`(I-4)。
  - 末尾追加约束行:`Facts (figures, names, links, commitments) must come from the QA rule knowledge or training knowledge base above; do not invent specifics.`
- 仅改这一个 seam;`buildMatchedMessages` 零改动(I-2)。

### 任务 T4(B):模拟按钮 loading 遮罩(S-1)
文件:`static/app.js`
- `runAiTrainingSimulate()` 改为:
```js
async function runAiTrainingSimulate() {
    const contactId = state.aiTraining.selectedSimulateMailContactId;
    if (!contactId) {
        showStatus("请先选择邮件", "warn");
        return;
    }
    const promptOverride = $("#aiTrainingPromptOverride").value.trim();
    const btn = $("#aiTrainingSimulateBtn");
    const messages = $("#aiTrainingSimulateMessages");
    btn.disabled = true;
    setTagEditorLoading(messages, true, "AI 生成中...");
    try {
        const result = await api("/api/ai-training/simulate", {
            method: "POST",
            body: JSON.stringify({
                expertContactId: contactId,
                promptOverride: promptOverride || null
            })
        });
        renderAiTrainingSimulateResult(result);
        showStatus("模拟回复已生成（未外发）", "ok");
    } finally {
        setTagEditorLoading(messages, false);
        btn.disabled = false;
    }
}
```
- 注:`renderAiTrainingSimulateResult` 会重写 `messages.innerHTML`,遮罩清理放 `finally` 且在 render 之后执行仍安全(`setTagEditorLoading(false)` 找不到 overlay 时为无操作,并会移除容器上的 `tag-editor-loading` class 与 `aria-busy`)。错误路径由既有绑定处 `.catch(showStatus)` 兜底,`finally` 保证遮罩必收。

### 任务 T5:测试(I-1..I-5)
文件:`llm/service/AiReplyDraftServiceTest.kt`、`llm/controller/AiTrainingSimulateTest.kt`
- T2 断言:simulateOnly + 命中规则 → `mode == QA_MATCHED` 且 prompt 含 SEGMENT;simulateOnly + 无匹配 → FREE_FORM 且 promptRuleIds 回退全集进入 KNOWLEDGE 段。
- T3 断言:FREE_FORM user content 含 `QA rule knowledge` 标题与约束行;超长规则集被截断至 ≤12000。
- I-2 回归断言:同输入下 `buildMatchedMessages` 输出与改动前完全一致(锁定 verbatim 契约)。
- T1 断言:aiReplyTurn 构造的 expertProfile 含 `Training knowledge base:` 段(controller 测试或抽取可测函数)。

## 变更文件清单

| # | 文件 | 操作 | 任务 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 修改 | T2/T3 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 修改 | T1 |
| 3 | `src/main/resources/static/app.js` | 修改 | T4 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改 | T5 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | 修改 | T5 |

子系统:后端 LLM 链路 + 前端 AI 训练页 = 2。

## 验收标准

- I-1: 测试断言模拟响应 JSON 无 `qaRuleIds` 字段;grep `mail_record_qa_rule` 写路径无新增调用方;`resolveQaRules` 的 sendQaRuleIds 仍仅来自 suggestedRuleIds/显式勾选。
- I-2: diff 确认 `buildMatchedMessages`/`buildMatchedSystemPrompt`/`buildMatchedUserContent` 函数体零改动;T5 verbatim 回归测试通过。
- I-3: grep simulate 调用链无新增 repository.save/insert;`AiTrainingController.simulate` 除 prompt 构造外零 diff。
- I-4: 测试构造 >12000 字符规则集,断言 KNOWLEDGE 段长度 ≤12000。
- I-5: grep `buildKnowledgeContext()` 调用点 == {AiTrainingController.simulate, UnmatchedInboundMailController.aiReplyTurn} 恰两处。
- S-1: diff 确认 styles.css 零改动;app.js 新增代码与契约代码块逐字一致;grep app.js 无新 class 字符串、无 inline style 拼接。

## 人工验收清单

### A-1: loading 遮罩(UI 目测)
- 前置条件: 本地起服务,LLM 开启(`talent-introduction.llm.enabled=true`)或用慢网模拟;AI 训练页有可选来信。
- 操作步骤: ① 进「AI 训练」→ 模拟子页,选一封来信;② 点「生成模拟回复」;③ 请求期间观察消息区与按钮;④ 等待完成。
- 预期结果: ③ 消息区出现半透明白色遮罩(rgba(255,255,255,0.76))+ 16px 主色旋转圆环 + 文案「AI 生成中...」,按钮置灰不可点、连点无重复请求;④ 遮罩消失、按钮恢复、草稿气泡渲染。
- 覆盖: outcome 4 / S-1

### A-2: 遮罩失败路径回收
- 前置条件: 同 A-1,临时把 LLM base-url 配错(或断网)。
- 操作步骤: 点「生成模拟回复」,等待失败。
- 预期结果: 出现错误 toast(既有 showStatus);遮罩消失,按钮恢复可点(无卡死遮罩)。
- 覆盖: outcome 4 / S-1

### A-3: 模拟反映真实 QA_MATCHED
- 前置条件: 计划 1 已上线(V68);选一封命中 QA 规则的来信(如问 "what is the application process?")。
- 操作步骤: 生成模拟回复,查看结果 meta 区 mode chip 与草稿正文。
- 预期结果: chip 显示 `模式 QA_MATCHED`;草稿含对应规则段落原文(如 "First, you submit the required materials.");无「注入范例」badge(QA_MATCHED 不走 few-shot)。
- 覆盖: outcome 2 / I-1/I-2

### A-4: FREE_FORM 携带规则口径
- 前置条件: 选一封不命中任何规则的闲聊类来信。
- 操作步骤: 生成模拟回复,查看 mode 与草稿。
- 预期结果: chip 显示 `模式 FREE_FORM`;草稿中出现的项目事实(两轨制、免费、保密等)与 QA 规则口径一致,无编造金额/机构名。
- 覆盖: outcome 3 / I-4

### A-5: 真实工作台吃到训练知识
- 前置条件: 训练页知识库新增一条唯一性强的条目(如 Topic: "office mascot", Answer 含独特词 "QINGFEI-PANDA");未匹配工作台有一封 FREE_FORM 来信。
- 操作步骤: ① 在未匹配来信详情用「AI 生成回复」,指令栏输入「请顺带提到我们的 office mascot」;② 查看草稿。
- 预期结果: 草稿包含 "QINGFEI-PANDA"(证明 buildKnowledgeContext 已进真实路径);发送侧 qaRuleIds 仍为空(组装台不因 AI 草稿关联规则)。
- 覆盖: outcome 1 / I-1/I-5

### A-6: 护栏 constraints 配置(运营操作,非代码)
- 前置条件: A-1..A-5 通过。
- 操作步骤: 训练页 prompt-config 的 constraints 栏填入四条并保存:金额只出现一次且只在资金段;被问公司资质必须给官网+LinkedIn+证书;专家要求邮件沟通则不提议会议;不得用"会后再谈"回避来信已明确提出的问题。再对 A-4 的来信重新生成。
- 预期结果: 保存后 `GET /api/ai-training/prompt-config/effective` 返回 `isCustom=true` 且含四条;重新生成的草稿遵守四条(抽查金额与会议提议)。
- 覆盖: 需求 D(护栏)/ K-prompt-config-effective-default

### A-7: 回归——工作台 QA_MATCHED 行为不变
- 前置条件: 未匹配工作台一封命中规则的来信。
- 操作步骤: 勾选建议规则走「AI 生成回复」,对比改动前草稿结构(SEGMENT 原文保留、仅衔接句差异);发送组装回复。
- 预期结果: 草稿保留规则段落原文;发送后 `mail_record_qa_rule` 仅含勾选的规则 id(数据库抽查)。
- 覆盖: must-NOT-change 1/2 / I-1/I-2

## 修正记录

(执行/复验期间的决策在此追加)
