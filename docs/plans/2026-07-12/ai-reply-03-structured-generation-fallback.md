# AI 回复逐点生成与结构化降级

## 需求描述

Observable outcome：多问题邮件按原顺序生成编号小节；模型不可用时使用相同问题矩阵输出结构化草稿；单问题邮件保持自然简洁。  
What must NOT change：事实边界、CTA policy、frame 来源、FREE_FORM 非空 fallback、QA_MATCHED 单问题 verbatim 事实。  
Out of scope：前端降级标签、变量替换、HTML 富文本编辑器、修改 QA 数据。

## 关键不变量

### Invariant I-1: 多问题输出契约
- Rule: requestFacts.size>=2 时正文顺序固定为 salutation → short acknowledgement → `1..N` 小节 → closing；每个请求恰好一个编号，不得合并、跳过、调序。
- Applies to: QA_GROUNDED LLM prompt、deterministic fallback。
- Violation consequence: 专家无法对应自己的问题。
- 来源: original

### Invariant I-2: 单项只用自身事实
- Rule: 每个小节只可使用该 RequestFactItem.factRuleIds、已有研究画像和明确 training knowledge；UNSUPPORTED 必须在该小节写待确认，不得从其他小节借事实。
- Applies to: grounded user content、fallback composer。
- Violation consequence: 看似完整但事实错配。
- 来源: K-training-knowledge-injection-points

### Invariant I-3: 多问题不受四段限制
- Rule: 单问题最多 4 段；多问题允许 acknowledgement + N 个短编号 + closing。每节建议 1–3 句，禁止为了“4 段”压成一堵墙。
- Applies to: base/grounded system prompt。
- Violation consequence: 提问多时模型合并段落。
- 来源: K-prompt-config-effective-default

### Invariant I-4: fallback 与 LLM 同构
- Rule: fallback 必须消费同一 requestFacts；禁止 `composeDeterministicDraft(promptRuleIds)` 扁平拼接多问题事实。单问题和 FREE_FORM 现有 fallback 保留。
- Applies to: `fallbackDraftText`。
- Violation consequence: 模型失败即退化成资料堆叠。
- 来源: K-free-form-fallback-nonempty / K-llm-timeout-fallback

### Invariant I-5: frame 单源
- Rule: salutation/greeting/closing 继续来自 `ReplySnippetService.resolveManualFrame`；新 composer 不硬编码第二套签名，raw 模板变量保留到计划 5 渲染。
- Applies to: LLM guidance、fallback composer。
- Violation consequence: 训练/邮箱/人工外发 frame 漂移。
- 来源: K-manual-frame-three-consumers

### Invariant I-6: CTA 与事实边界最后执行
- Rule: structured composer 输出仍经过现有 action policy；不得把“next stages”误解为授权索要 CV/会议。
- Applies to: generate success/fallback。
- Violation consequence: 逐点回复重新引入未授权动作。
- 来源: K-ai-reply-action-cta-variant-coverage

## 现状审计

### AI prompt / fallback（内存）
- Write paths:
  1. `buildGroundedUserContent` 当前输出扁平 `Matched QA answers` + request checklist。
  2. `FreeFormPromptDefaults.baseSystemPrompt` 固定最多 4 段。
  3. `fallbackDraftText` 只要 promptRuleIds 非空即调用 `LlmStitchService.composeDeterministicDraft`。
- Read paths: LLM client；两个 controller response。
- Interaction points: `LlmStitchService.composeDeterministicDraft` 还服务 manual polish fallback，不能改成 request-aware 语义；应新建 AI 专用 composer。（来源: K-manual-frame-three-consumers / K-qa-replybody-outbound-sites）

## 实现方案

### T1：新增 AI 专用逐点 fallback composer（I-1/I-2/I-4/I-5）
文件：新增 `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`

- 输入：requestFacts、rule lookup、manual frame。
- heading 使用清理 bullet/问号后的原始 requestText，最长 160 字符，不翻译、不发明主题。
- GROUNDED/PARTIAL 拼接该项 facts，按 rule id 去重；PARTIAL 末句固定说明仍有细节待确认。
- UNSUPPORTED 固定：`This point is not covered by the approved information currently available, so it requires confirmation before we provide a definitive answer.`
- 不添加 CV/meeting/next-step CTA。

### T2：Grounded prompt 改为逐项事实块（I-1/I-2/I-3/I-5/I-6）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- user content 逐项输出 `REQUEST n / STATUS / APPROVED FACTS FOR REQUEST n`。
- system contract 明确编号、顺序、每项必答、无依据就地说明、plain-text email、禁止 Markdown 表格。
- 多问题不得复述同一事实；需要引用前项时用一句简短交叉说明。
- fallback 多问题调用新 composer；单问题沿用既有 deterministic 路径。

### T3：修正默认段落约束（I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiPromptConfigService.kt`

- 将固定 `Keep the reply to at most 4 paragraphs` 改为单/多问题条件规则。
- effective custom prompt 仍优先；additional constraints 追加语义不变。（来源: K-prompt-config-effective-default）

### T4：测试（I-1 至 I-6）
文件：
- 新增 `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiPromptConfigServiceTest.kt`

- 本次专家邮件 7 项：prompt/fallback 均按序 1..7；每项只含其 facts；unsupported 就地出现。
- LLM disabled/client null/no response 三条路径 fallback 逐字同构。
- 单问题 QA_MATCHED/FREE_FORM 既有测试保持。
- action policy 最终 gate 与 frame 模板变量保持。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt` | AI 专用 fallback（新增） |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 逐项 prompt/fallback 路由 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiPromptConfigService.kt` | 条件段落规则 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt` | composer 测试（新增） |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | LLM/fallback 集成 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiPromptConfigServiceTest.kt` | prompt 回归 |

## 验收标准

- I-1：7 项输入恰好输出 1..7，顺序一致。
- I-2：每节不含其他请求独有 rule body；unsupported 固定文案。
- I-3：默认 prompt 明确单/多条件且无全局四段冲突。
- I-4：多问题 fallback 不调用旧 flat composer；三种失败路径一致。
- I-5：frame 只从 ReplySnippetService 获取。
- I-6：最终 action policy 测试通过，无未授权 CTA。
- 命令：`mvn -Dtest=AiReplyPointByPointComposerTest,AiReplyDraftServiceTest,AiPromptConfigServiceTest test`。

## 人工验收清单

### A-1: Pro 模型逐点回复
- 前置条件: 使用本次专家邮件，选择 DeepSeek V4 Pro，LLM 可用。
- 操作步骤: 生成模拟回复。
- 预期结果: 称呼后出现 7 个按原问题顺序的编号；每项 1–3 句；无 CV/会议 CTA；签名单独分段。
- 覆盖: I-1/I-2/I-3/I-6

### A-2: 模型关闭仍逐点
- 前置条件: `LLM_ENABLED=false`，同一邮件。
- 操作步骤: 生成模拟回复。
- 预期结果: 仍为 7 个编号，不出现 QA 段落平铺；无依据项在对应编号下显示固定待确认句。
- 覆盖: I-1/I-4/I-5

### A-3: 单问题不回归
- 前置条件: 来信只问公司注册地址。
- 操作步骤: 生成回复。
- 预期结果: 自然短邮件，不强制生成多余编号；只回答公司信息。
- 覆盖: I-3 / must-NOT-change

