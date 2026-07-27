# AI 回复动作过滤格式保真

## 需求描述

Observable outcome：无违规动作时草稿逐字不变；有违规动作时仅移除命中句，保留其余段落、编号、空行、签名。  
What must NOT change：疑问式 CV/résumé 与会议 CTA 仍被拦截；明确授权动作仍允许；最终 hard gate 保留。  
Out of scope：问题—事实映射、prompt 风格、前端 CSS、变量渲染。

## 关键不变量

### Invariant I-1: 无违规时字节级保真
- Rule: `sanitize(text, allowed)` 若未删除任何 unit，必须返回原始 `text`，包括 `\n`、`\r\n`、连续空行和尾部换行；禁止全局 whitespace normalization。
- Applies to: LLM success、retry success、deterministic fallback 最终 gate。
- Violation consequence: 所有邮件被压成一行。
- 来源: original

### Invariant I-2: 删除时保留原分隔符
- Rule: 检测可按句 unit 工作，但清理必须基于原文 span 删除；未命中区域和分隔符原样保留，仅将删除后超过 3 个连续空行收敛为 2 个。
- Applies to: `AiReplyActionPolicy.sanitize`。
- Violation consequence: 编号/签名被重排，或删句后出现巨大空洞。
- 来源: K-ai-reply-action-cta-variant-coverage

### Invariant I-3: 检测与清理同一边界
- Rule: `findViolations` 和 `sanitize` 必须调用同一 unit/span tokenizer 与 `detectDirectRequest`；不得复制 regex 或句子边界。
- Applies to: policy 全部入口。
- Violation consequence: 检测报警但清不掉，或清除未报警内容。
- 来源: K-ai-reply-action-cta-variant-coverage

### Invariant I-4: 授权语义不变
- Rule: `deriveAllowed`、动作 enum、疑问式 CV/résumé 覆盖和流程描述豁免不变。
- Applies to: 当前 fix-1 已关闭决策。
- Violation consequence: 修格式时重新放开 CTA。
- 来源: K-ai-reply-action-cta-variant-coverage

## 现状审计

### 运行时草稿文本（内存，无持久化）
- Write paths:
  1. `AiReplyDraftService.enforceActionPolicy` 对 LLM/fallback 文本调用 sanitize 两次。
  2. `AiReplyActionPolicy.sanitize` 当前 `splitUnits → kept.joinToString(" ") → \s+ 压缩`。
- Read paths: 两 controller response、训练草稿 bubble、邮箱草稿 bubble/采用。
- Interaction points: 即使 `removed=false`，当前 sanitize 仍重建文本；前端 `.pre { white-space:pre-wrap }` 无法恢复已丢失换行。

## 实现方案

### T1：先补失败测试（I-1/I-2/I-3/I-4）
文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt`

- 安全多段邮件含称呼、6 个编号、签名，断言 sanitize 输出逐字相等。
- 中间含 `Could you share your CV?`，断言仅该句消失，前后 `\n\n`、编号和签名保留。
- 覆盖 CRLF、bullet、尾部换行。
- 现有 interrogative résumé、授权会议、流程描述测试原样保留。

### T2：改为 span-preserving sanitizer（I-1/I-2/I-3/I-4）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`

- tokenizer 返回 `text/start/end`，检测仍消费 text。
- `removed=false` 直接 `return text to false`。
- `removed=true` 按原 offset 拼接未删除区间；不调用 `trim()`/`\s+` 全局压缩。
- 仅对删除接缝执行 `\n{3,} → \n\n`；不改变句内空格。

### T3：运行时回归（I-1/I-2）
文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

- LLM 安全多段 draft 最终逐字保留。
- retry 后仍违规时，最终 draft 保留其他段落并包含 `UNAUTHORIZED_ACTION_REMOVED`。
- qaRuleIds/mode/coverage/fewShot refs 不变。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt` | span tokenizer/保真清理 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt` | 多段/删除接缝测试 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 最终 gate 格式回归 |

## 验收标准

- I-1：安全邮件 `assertEquals(input, sanitized)`。
- I-2：只删除违规句；称呼、6 个编号、签名位置不变。
- I-3：grep 只有一套 tokenizer/detectDirectRequest；检测与清理变体矩阵一致。
- I-4：既有 action policy 全测通过。
- 命令：`mvn -Dtest=AiReplyActionPolicyTest,AiReplyDraftServiceTest test`。

## 人工验收清单

### A-1: 多段安全邮件保持格式
- 前置条件: 模型返回含称呼、3 个编号和签名的草稿。
- 操作步骤: 在训练页和邮箱各生成一次。
- 预期结果: `Dear...`、每个编号、`Best regards` 均独占预期行，段落间有 1 个空行。
- 覆盖: I-1

### A-2: 只删除未授权 CTA
- 前置条件: 模型草稿第 2 项末尾含 `Could you share your CV?`。
- 操作步骤: 生成并查看 warning。
- 预期结果: 该句不显示；第 1/2/3 项与签名仍分段；显示动作移除 warning。
- 覆盖: I-2/I-4

