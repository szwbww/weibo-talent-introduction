# AI 回复双模型选择后端契约

## 需求描述

Observable outcome：AI 训练 simulate 与收发件箱 aiReplyTurn 请求均可选择 `DEEPSEEK_V4_FLASH` 或 `DEEPSEEK_V4_PRO`；未传默认 Flash；响应回显实际选择。两个入口经过同一 DraftService/LLM client 映射。  
What must NOT change：文档分析、QA 提炼、人工 polish 等其他 LLM 调用仍使用现有 `talent-introduction.llm.model`；LLM disabled/fallback；API key/url/temperature。  
Out of scope：按用户持久化默认模型、模型计费展示、自动路由、并行比较两个模型。

## 关键不变量

### Invariant I-1: API 使用稳定枚举
- Rule: request 只接受 `DEEPSEEK_V4_FLASH`、`DEEPSEEK_V4_PRO` 或 null；null=Flash；未知值返回 400，不静默降级。
- Applies to: 两个 request DTO、DraftService。
- Violation consequence: UI 选 Pro 实际跑 Flash，结果不可审计。
- 来源: original

### Invariant I-2: provider id 只在服务端配置
- Rule: Flash/Pro 各映射一个 `LlmProperties` 配置字段；浏览器和响应不得暴露 provider id/apiUrl/apiKey。
- Applies to: application.yml、HttpLlmDraftClient request body。
- Violation consequence: 前端与供应商命名耦合或泄露配置。
- 来源: original

### Invariant I-3: 只影响 AI 回复链路
- Rule: 新增 `chatWithModel` 默认方法；现有 `chat/stitchDraft` 签名和行为不变。只有 `AiReplyDraftService.generate` 调 `chatWithModel`。
- Applies to: 文档分析、QA extraction、LlmStitchService、所有测试 fake client。
- Violation consequence: unrelated LLM 工作流模型被切换或大量实现破坏。
- 来源: original

### Invariant I-4: retry/fallback 模型语义稳定
- Rule: 动作纠偏 retry 必须使用首轮相同模型；fallback 不调用任何模型，但响应 selectedModel 仍回显请求枚举，usedLlm=false。
- Applies to: action-policy 计划完成后的 generate 全分支。
- Violation consequence: 一次操作混用两个模型，无法解释输出。
- 来源: original

### Invariant I-5: 双入口完全对齐
- Rule: 两 controller 只负责解析/传递枚举；默认值、验证、provider 映射只在共享层实现一次。
- Applies to: simulate、aiReplyTurn。
- Violation consequence: 训练页与邮箱可选项/默认模型不一致。
- 来源: K-ai-generate-single-freeform-seam

## 现状审计

### `LlmProperties` 配置
- Schema: `enabled/autoReplyEnabled/apiUrl/apiKey/model/timeoutMs/temperature/freeFormTemperature`；application.yml 使用 `LLM_MODEL`。
- Write path: 部署环境变量/Spring config；无 DB。
- Read paths: `HttpLlmDraftClient` request body 使用唯一 `properties.model`；其他 LLM service 共用 client。
- Interaction points: 直接修改 `chat` 参数会迫使全部 fake clients 和非回复消费者改签名；应新增带默认实现的窄 seam。

### AI 回复请求 DTO
- Write paths: 前端 POST `/api/ai-training/simulate`、`/api/mail/unmatched-inbound/{id}/ai-reply/turn`。
- Read paths: 两 controller → `AiReplyDraftService.generate` → `LlmDraftClient.chat`。
- Interaction points: 两入口 DTO/response 分处两个 controller；需同时新增字段，但验证逻辑不得复制。

## 实现方案

### T0：部署配置研究检查点（I-2）

- 在实施前确认当前 OpenAI-compatible provider 对 Flash/Pro 的真实 model id。
- 确认环境变量名：`LLM_REPLY_MODEL_FLASH`、`LLM_REPLY_MODEL_PRO`。
- 若真实 id 不是默认 `deepseek-v4-flash/pro`，只改部署变量，不改 API enum/UI value。

### T1：配置与 client 窄扩展（I-2/I-3）
文件：
- `src/main/kotlin/com/weibo/talentintroduction/config/LlmProperties.kt`
- `src/main/resources/application.yml`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt`

- `LlmProperties` 新增 `replyFlashModel`、`replyProModel`，分别绑定两个 env；保留 `model`。
- 在 client 文件定义 `AiReplyModel` 枚举（API name、resolve(properties)）；`fromNullable` null→Flash、unknown throw IllegalArgumentException。
- interface 新增 `chatWithModel(messages, temperature, providerModel)` 默认实现调用旧 `chat`，保证既有 fake/消费者不改。
- `HttpLlmDraftClient` override 新方法，共用私有 `executeChat(..., model)`；旧 `chat` 继续传 properties.model。

### T2：DraftService 贯通选择（I-1/I-3/I-4/I-5）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- `generate` 新增末尾可选参数 `replyModel: String? = null`，入口统一解析为 enum。
- 所有 LLM 首轮/retry 调 `chatWithModel`，传解析后的 provider id。
- `AiReplyDraftResult` 增加 `selectedModel: String`，所有 success/fallback 返回稳定枚举 name。

### T3：双 controller DTO/response 对齐（I-1/I-5）
文件：
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`

- 两 request DTO 增加 `model: String? = null`；传给 generate。
- 两 response DTO 增加 `selectedModel`，取 result；不返回 provider id。
- unknown enum 由现有 IllegalArgumentException → 400 处理。

### T4：测试（I-1 至 I-5）
文件：
- 新增 `src/test/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClientTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

- 捕获 HTTP JSON：Flash/Pro 映射各自 provider id；旧 chat 仍使用 properties.model。
- DraftService null→Flash、Pro→Pro、unknown throw；retry 两次 provider id 相同；fallback 回显选择且 client zero interaction。
- 两端接口各测 Flash/Pro/null/unknown；响应只含 enum。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/config/LlmProperties.kt` | 回复模型配置 |
| `src/main/resources/application.yml` | 两个 env 映射 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt` | enum、窄 client seam |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | model 解析/传递/回显 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | simulate DTO |
| `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | mailbox DTO |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClientTest.kt` | HTTP 映射（新增） |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 默认/retry/fallback |
| `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | simulate API |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | mailbox API |

## 验收标准

- I-1：两 endpoint 的 null/Flash/Pro/unknown 契约测试通过。
- I-2：HTTP payload model 为服务端配置值；响应/静态资源 grep 不含 provider id。
- I-3：`ExpertDocumentAnalysisService/AiQaExtractionService/LlmStitchService` diff 为零且测试通过。
- I-4：retry 两次捕获相同 model；fallback zero HTTP。
- I-5：两 response 的 `selectedModel` 一致。
- 命令：`mvn -Dtest=HttpLlmDraftClientTest,AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest test`。

## 人工验收清单

### A-1: Flash/Pro 实际分流
- 前置条件: 两个 env 配置为可区分的 provider model id；LLM enabled。
- 操作步骤: 在两个 endpoint 分别发送 Flash/Pro 请求；查看应用脱敏请求日志/测试代理。
- 预期结果: Flash 只发 flash id，Pro 只发 pro id；response 分别回显稳定枚举。
- 覆盖: I-1/I-2/I-5

### A-2: 旧客户端兼容
- 前置条件: 不传 model。
- 操作步骤: 调用两个 endpoint，并执行一次文档 AI 分析。
- 预期结果: 两个回复 endpoint 回显 Flash；文档分析仍使用原 `LLM_MODEL`。
- 覆盖: I-1/I-3 / must-NOT-change

### A-3: 非法模型拒绝
- 前置条件: 无。
- 操作步骤: POST `model=DEEPSEEK_UNKNOWN`。
- 预期结果: HTTP 400；无 LLM HTTP 请求；无草稿生成。
- 覆盖: I-1

