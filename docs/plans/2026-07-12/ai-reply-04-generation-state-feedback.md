# AI 回复生成状态与降级提示

## 需求描述

Observable outcome：训练页和邮箱明确显示本次是模型生成还是哪一种 fallback；运营不会把确定性草稿误认为 DeepSeek 润色结果。  
What must NOT change：`usedLlm/llmEnabled/mode/selectedModel` 旧字段；loading、反馈与草稿隔离；模型选择与竞态保护。  
Out of scope：暴露供应商原始错误、重试控制台、持久化调用日志、监控看板。

## 关键不变量

### Invariant I-1: 单字段状态枚举
- Rule: 新增 `generationState`，只能为 `LLM_USED`、`FALLBACK_LLM_DISABLED`、`FALLBACK_CLIENT_UNAVAILABLE`、`FALLBACK_NO_RESPONSE`；不得返回异常文本。
- Applies to: result、两 response DTO、前端映射。
- Violation consequence: 运营无法判断扁平草稿原因或泄露内部错误。
- 来源: original

### Invariant I-2: 状态与 usedLlm 一致
- Rule: `LLM_USED` iff usedLlm=true；其余三值 usedLlm=false。动作 policy sanitize/retry 不得虚报 fallback。
- Applies to: generate 全分支。
- Violation consequence: UI 自相矛盾。
- 来源: original

### Invariant I-3: 双入口同构
- Rule: simulate/aiReplyTurn 原样透传同一 result state；前端使用同一个 label renderer。
- Applies to: controllers/app.js。
- Violation consequence: 训练页显示原因，邮箱仍显示笼统提示。
- 来源: K-ai-generate-single-freeform-seam

### Invariant I-4: 状态不进入正文
- Rule: generationState 只进入 feedback/meta；不得拼入 draftText、operatorTurns、adopt editor 或外发正文。
- Applies to: app render/adopt。
- Violation consequence: 邮件正文泄露内部模型状态。
- 来源: K-ai-reply-loading-panel

## 样式契约

### S-1: 生成状态反馈（纯复用）
- 复用：成功 `ai-reply-coverage`（`styles.css:5883-5890`），fallback `ai-reply-warning`（`styles.css:5892-5900`），meta `ai-meta-chip`（`styles.css:6919` 附近）。
- 新增：无 CSS、无新 class、无新 DOM 容器。
- DOM：继续由既有 `#aiTrainingSimulateFeedback` / `#aiReplyFeedback` 内插入一个现有 class 的 `<div>`；messages/draft bubble 结构不改。
- 禁止项：inline style；修改现有 class；把状态文本放进 `.pre`。

## 现状审计

### 生成状态（内存 DTO）
- Write paths: DraftService 目前只写 usedLlm boolean；HTTP client 异常/空均返回 null。
- Read paths: 两 controller；训练 meta；邮箱 toast/feedback。
- Interaction points: 当前邮箱只显示 `DeepSeek 不可用，已用确定性草稿`；训练 meta 仅 `LLM 未使用`，无法区分 disabled/client/no response。

### 前端样式盘点
- 可复用 class：`.ai-reply-coverage`、`.ai-reply-warning`、`.ai-meta-chip`；本计划不改 CSS。
- tokens：warning `#d97706`，warning-bg `rgba(217,119,6,.08)`，radius 7px，font 12px。
- 基线 DOM：feedback 在 draft messages 前；meta 在训练 panel 下；状态不得进入草稿。

## 实现方案

### T1：结果状态赋值（I-1/I-2）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- 新增 enum/result 字段；每个 return 分支显式赋值。
- client 内部异常与空响应统一 `FALLBACK_NO_RESPONSE`，不扩大 client interface。

### T2：双 controller 透传（I-1/I-3）
文件：
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`

- 两 response 增加 generationState，旧字段不改。

### T3：共享前端映射（I-3/I-4/S-1）
文件：`src/main/resources/static/app.js`

- `aiReplyGenerationStateLabel` 固定中文：模型已生成、LLM 已关闭—结构化规则草稿、模型客户端不可用—结构化规则草稿、模型无有效响应—结构化规则草稿。
- `renderAiReplyFeedback` 复用 coverage/warning；训练 meta 同步 chip；邮箱 toast 使用同一 label。
- `UNAUTHORIZED_ACTION_REMOVED` 增加可读中文映射，不再显示 raw code。

### T4：测试（I-1 至 I-4/S-1）
文件：
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`
- `src/test/js/aiReplyLoadingFeedback.test.js`

- 四状态与 usedLlm 真值表；两 API 透传；前端文案和隔离；旧字段兼容。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | generationState |
| `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | simulate response |
| `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | mailbox response |
| `src/main/resources/static/app.js` | 状态映射/反馈 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 分支真值表 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | API 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | API 测试 |
| `src/test/js/aiReplyLoadingFeedback.test.js` | UI 契约 |

## 验收标准

- I-1/I-2：四状态逐分支断言，状态与 usedLlm 无矛盾。
- I-3：两 API 同值；前端仅一套 label function。
- I-4：grep generationState 不进入 draft/adopt/turn payload。
- S-1：无 styles.css/index.html diff，无新 class/inline style。
- 命令：`mvn -Dtest=AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest test`；`node --test src/test/js/aiReplyLoadingFeedback.test.js`。

## 人工验收清单

### A-1: 模型成功提示
- 前置条件: LLM 可用。
- 操作步骤: 两入口各生成一次。
- 预期结果: 显示“模型已生成”；正文不含该文字。
- 覆盖: I-2/I-3/I-4/S-1

### A-2: 模型关闭提示
- 前置条件: LLM disabled。
- 操作步骤: 两入口各生成一次。
- 预期结果: 显示“LLM 已关闭—结构化规则草稿”；仍可查看逐点草稿。
- 覆盖: I-1/I-3

### A-3: 超时/空响应提示
- 前置条件: 测试环境让模型超时或返回空。
- 操作步骤: 生成回复。
- 预期结果: 显示“模型无有效响应—结构化规则草稿”；loading 正常结束，模型选择保持。
- 覆盖: I-1 / must-NOT-change

