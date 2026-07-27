# AI 回复无授权动作硬拦截

## 需求描述

Observable outcome：草稿在返回前经过确定性动作策略；专家未提出且运营未明确授权时，禁止索要 CV/材料、提议会议/通话。一次纠偏重试仍违规时，移除违规直接请求并返回 warning，绝不把违规 CTA 展示给运营。  
What must NOT change：描述客观申请流程不等于直接请求；明确材料/会议意图仍可生成对应动作；QA ids、mode、覆盖元数据不变。  
Out of scope：自动外发规则、通用内容审核平台、用第二个 AI 判定动作。

## 关键不变量

### Invariant I-1: 默认允许动作集合为空
- Rule: `allowedActions` 初始为空；只有 inbound 明确材料意图/已提供材料或明确会议意图/同意会议，或 operatorInstruction 明确要求，才分别加入 `REQUEST_MATERIALS`/`PROPOSE_MEETING`。
- Applies to: 首轮与续轮、两个入口。
- Violation consequence: 无关 CTA 再次出现。
- 来源: original

### Invariant I-2: 动作判定为确定性代码
- Rule: 只用受测短语/regex 判定允许和违规；禁止调用 LLM、URL、enrichment 进行策略判断。
- Applies to: action policy derive/validate/sanitize。
- Violation consequence: 禁止规则随模型随机性漂移。
- 来源: original

### Invariant I-3: 描述事实与直接请求区分
- Rule: `The process requires applicants to submit materials` 不算 REQUEST_MATERIALS；`please send/reply with/share your CV` 算。`Meetings may be arranged` 不算 PROPOSE_MEETING；`let us schedule/please share a convenient time` 算。
- Applies to: 英文/中文直接请求 pattern。
- Violation consequence: 合法流程答案被误删，或真正 CTA 漏过。
- 来源: original

### Invariant I-4: 最终返回绝不含未授权动作
- Rule: 初次 LLM 违规 → 同模型最多纠偏重试 1 次；仍违规或 fallback 违规 → 逐句删除命中直接请求的句子/段落，复验为零后返回，并追加 `UNAUTHORIZED_ACTION_REMOVED`。禁止返回原违规文本。
- Applies to: LLM success、exception/disabled fallback、所有 mode。
- Violation consequence: prompt 只是软约束，用户“禁止”要求未落实。
- 来源: original

### Invariant I-5: 纠偏不改变审计语义
- Rule: retry/sanitize 只改变 draftText/usedLlm/contextWarnings；qaRuleIds/mode/requestCount/groundedRequestCount/unsupportedRequests/fewShot refs 保持原判定。
- Applies to: `AiReplyDraftResult`。
- Violation consequence: 采用草稿后 QA 审计错误。
- 来源: K-ai-reply-prompt-vs-send-rule-ids

## 现状审计

### AI draft 运行时文本
- Store: 无持久化；controller 返回 DTO，运营采用后才走既有发送链。
- Write paths:
  1. `AiReplyDraftService.generate` 使用 client.chat 成功文本。
  2. 同方法在 disabled/client null/error/blank 时走 fallback。
- Read paths: 训练页 result renderer；邮箱 draft bubble/采用草稿。
- Interaction points: 当前只依赖 prompt，无 output validator；client.chat 仅调用一次；fallback 也未做动作检查。（来源: K-ai-generate-single-freeform-seam）

## 实现方案

### T1：新增动作策略与单测（I-1/I-2/I-3/I-4）
文件：
- 新增 `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`
- 新增 `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt`

- enum 仅两个值：`REQUEST_MATERIALS`、`PROPOSE_MEETING`。
- `deriveAllowed(inboundText, operatorInstruction, operatorTurns)`；续轮使用本次 operatorInstruction + 历史指令，不能从旧 assistant draft 反向授权。
- 英文/中文正反例逐字测试，包括本次专家邮件（allowed empty）。
- `findViolations(text, allowed)` 返回动作+句子；`sanitize` 只删除违规直接请求句/所在独立段，不删除流程描述。

### T2：在单一 generate seam 接入（I-1 至 I-5）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- 构造 messages 前派生 allowedActions，并把枚举列表或 `NONE` 加入 grounded/free-form/matched system boundary。
- 首次 chat 返回后 validate；违规则在原 messages 末尾加入纠偏 user message，明确违规句和 allowed set，同一 temperature/selected model 重试一次。
- 对重试文本或 fallback 最终执行 sanitize+复验。
- 被移除时 `contextWarnings` 去重追加 code；若文本被删空，返回不含 CTA 的现有安全事实段；若无安全事实则固定返回 `The available approved information is not sufficient for a reliable reply, so this item should be confirmed manually.`。
- retry 不增加 fewShot refs、不重算 QA 匹配。

### T3：服务集成测试（I-1/I-4/I-5）
文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

- 本次专家邮件 + 首次 `reply with your CV` + 第二次安全文本：chat=2，最终安全，无 warning。
- 两次都违规：最终无 CTA，warning 存在，chat=2。
- disabled fallback 含 CTA：不调用 client，最终被清理。
- 明确 `Please arrange a meeting`：meeting action允许，不重试。
- 断言 retry 前后 qaRuleIds/mode/coverage/fewShot refs 不变。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt` | 动作授权/验证/清理（新增） |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | prompt 边界、一次 retry、最终硬拦截 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt` | 策略单测（新增） |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 两入口共享 seam 集成测试 |

## 验收标准

- I-1：本次专家邮件 allowedActions=`NONE`。
- I-2/I-3：策略测试覆盖中英文 direct request 与流程描述反例；policy 无外部依赖。
- I-4：所有返回分支调用最终 validate；两次违规测试最终无 CV/meeting CTA。
- I-5：结果元数据逐字段相等，仅 warning/draftText 可变。
- 命令：`mvn -Dtest=AiReplyActionPolicyTest,AiReplyDraftServiceTest test`。

## 人工验收清单

### A-1: 未授权 CV/会议被禁止
- 前置条件: 本次专家邮件；运营补充要求为空。
- 操作步骤: 连续使用 Flash/Pro 各生成 3 次。
- 预期结果: 6 个草稿均不出现索要 CV/材料、询问方便时间、Zoom/Teams/Webex 邀请；若发生拦截，显示可读 warning。
- 覆盖: I-1/I-4

### A-2: 明确会议请求被允许
- 前置条件: 来信 `Can we arrange a meeting next week?`。
- 操作步骤: 生成草稿。
- 预期结果: 草稿可回应会议安排，不显示 unauthorized warning。
- 覆盖: I-1/I-3 / must-NOT-change

### A-3: 流程事实不被删
- 前置条件: 来信询问 application process；QA 事实含 `Applicants submit materials for review.`。
- 操作步骤: 生成草稿。
- 预期结果: 该客观流程句保留；不得追加 `please send your CV`。
- 覆盖: I-3/I-4

