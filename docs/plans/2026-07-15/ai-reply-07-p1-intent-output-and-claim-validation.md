# P1-7：Intent 级结构化输出、固定邮件样式与高风险声明校验

## 需求描述

将 grounded 模型协议从“每 request 一个答案”升级为“每 supported intent 一个答案 + 来源规则引用”。后端验证后统一生成固定标题、连续 request section、称呼、结尾和签名；高风险数字、网址、保证性措辞、政府/合同/IP 声明必须可追溯到引用来源。

Out of scope：证明任意自然语言逻辑真伪、外部事实核验、人工 review UI、修改 QA 事实本身。

## 关键不变量

### I-1：严格 intent JSON
模型仅返回：

```json
{"sections":[{"requestIndex":1,"answers":[{"intentKey":"expertise.programme_fit","answer":"...","sourceRuleIds":[24]}]}]}
```

- 每个 supported intent 恰好一次；missing intent 禁止出现。
- requestIndex/intentKey/sourceRuleIds 组合必须属于后端 matrix。
- 顶层/子项不允许额外字段、Markdown fence、空答案。

### I-2：来源引用不是模型自由选择
- sourceRuleIds 非空且必须是该 intent.evidenceRuleIds 子集。
- 不允许引用其他 request 的 rule。
- research fit 可使用 profile，但必须由后端已确认 dual evidence；模型不通过伪 rule id 表示 profile。

### I-3：后端拥有样式
- 固定 section title 来自 intent catalog，不再直接显示专家长问题。
- 编号按原 requestIndex 1..N；完全 missing group 仍保留空 section heading，使缺口可见且不再从 2 开始。
- salutation/greeting/ack/closing/signature 只来自 ReplySnippetService；模型不生成。

### I-4：内部状态不外发
- 空 section 不写“pending/unsupported/needs confirmation”；状态只在 response coverage。
- 草稿 readiness 非 READY 时由 UI/发送闸门处理。

### I-5：高风险声明 fail closed
- 答案中的金额、数字区间、年/月时长、访问频次、URL 必须逐 token 出现在引用 QA 正文（研究领域词可来自 profile）。
- 来源为 may/can/depends/after selection 时，不得强化成 guaranteed/will definitely/unconditional entitlement。
- government、all travel expenses covered、no fees、labor contract、IP ownership/confidentiality 等声明必须引用含对应 phrase family 的 rule。
- 校验失败整次 structured response 无效，走 deterministic fallback。

### I-6：fallback 同矩阵
- 按 supported intent 的 evidence rules 组装；missing intent 仅保留空标题。
- 不跨 intent 借事实，不把 profile 原文直接当匹配结论。

### I-7：动作重试仍用同 schema
- CTA correction response 必须再次 strict parse + source/claim validate，再进入 layout-preserving sanitizer。（K-grounded-json-materialize-before-policy）

## 固定样式契约

正文结构：

```text
Dear ...,

[ack]

1. Research fit and enterprise projects

[supported intent answers]

2. Company details

[answer]

...

[closing/signature]
```

- 标题无 Markdown `**`、无尾部分号/and、最长由 catalog 固定文本控制。
- 每段空一行；sanitizer 无违规时逐字保留布局。（K-action-sanitizer-preserve-layout）
- 同一 normalized answer 后续 request 使用后端生成的 `Please see point N above.`，引用 N 必须存在。

## 实现任务

### T1：intent JSON parser/materializer
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt`

- DTO 改为 sections/answers/intentKey/sourceRuleIds。
- 验证完整 supported intent 集、禁止 missing、重复/未知 key、跨 request source。
- 内部 marker/状态词继续拒绝。
- 把 validated intent answers 交给 composer，不返回 raw JSON。

### T2：高风险 claim validator
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`

- 规范化引用 rule subject/body，按答案执行 token 与 phrase-family 校验。
- 数字/币种/URL exact-normalized match。
- modality 检测至少覆盖 may/can/could/depends/typically/after selection 与 will/guaranteed/entitled。
- research fit 仅在 intent dual evidence 成立时允许“align/within scope”比较性表述；画像内容作为受限 context，不允许衍生资金/合同事实。
- 返回 machine warning code，不生成对外文案。

### T3：固定标题与完整 section composer
文件：
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt`

- catalog 提供 group title 解析；精确组合使用固定 7 类标题，未知 general 使用现有 cleanHeading fallback。
- composer 总是按全部 request groups 建 heading；supported intent answer 按 intent 顺序合并。
- fully missing group 输出标题+空 body，不输出状态句。
- fallback 按 intent evidence 组装；去重/cross-reference 使用原 request index。

### T4：DraftService prompt、materialize、retry
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- grounded prompt 只暴露每 intent 的 allowed source ids + approved facts + support status。
- 禁止模型输出 salutation/title/closing/status。
- 首轮与 correction retry 都调用同一 validator。
- invalid source/claim：`usedLlm=false`、`FALLBACK_NO_RESPONSE`、warning=`AI_REPLY_CLAIM_VALIDATION_FAILED` 或 structured invalid。
- QA_MATCHED/FREE_FORM 不走 intent parser。

### T5：测试
文件：
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

覆盖：schema 正负例、source subset、跨 request、数字/URL、modality 强化、government/contract/IP phrase、研究 dual evidence、固定标题、空第 1 section、连续编号、无 Markdown、签名换行、fallback、CTA retry。

## 变更文件清单（9）

1. `AiReplyGroundedDraftMaterializer.kt`
2. `AiReplyHighRiskClaimValidator.kt`
3. `AiReplyPointByPointComposer.kt`
4. `AiReplyIntentCatalog.kt`
5. `AiReplyDraftService.kt`
6. `AiReplyGroundedDraftMaterializerTest.kt`
7. `AiReplyHighRiskClaimValidatorTest.kt`
8. `AiReplyPointByPointComposerTest.kt`
9. `AiReplyDraftServiceTest.kt`

## 验收标准

- 最新邮件正文标题固定、段落换行正常，不再使用原长问题做标题。
- 第一项无据时仍显示 `1. Research fit and enterprise projects` 空 section，整体 BLOCKED，不从 2 开始。
- 第 4/5/6 只输出 supported intent 的事实，不能把 matching 当 selection、responsibility 当 deliverables。
- 任意新增 RMB/年限/URL/guarantee 的模型回答均被拒绝并 fallback。
- 定向测试：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyGroundedDraftMaterializerTest,AiReplyHighRiskClaimValidatorTest,AiReplyPointByPointComposerTest,AiReplyDraftServiceTest test
```

## 人工验收清单

### A-1：正常 Pro 输出
- 预期：固定 1-7 标题、自然段、无粗体、签名独立换行；每个回答可在 response intent sourceRuleIds 找到依据。

### A-2：模型虚构金额
- stub 在 company answer 添加 RMB 12 million。
- 预期：structured response invalid/fallback，虚构金额不进入 draft。

### A-3：条件强化
- 来源写 may receive，模型写 will receive。
- 预期：claim validator 拒绝。

### A-4：第一项缺失
- 预期：正文仍有第 1 固定标题但无状态提示；feedback 标 BLOCKED；后续编号连续。
