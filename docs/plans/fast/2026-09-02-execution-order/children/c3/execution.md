# c3 Execution Report — 03 整封生成：两次 LLM 调用 + 令牌逐字替换 + 未识别提问

- Result: **READY_FOR_VERIFICATION**
- Plan: `docs/plans/2026-09-02/03-rag-letter-composer.md`
- Plan identity: `commit:46cc5c46395814b1ef03e52ab8b8bfb5197f372c`（`git diff 46cc5c4 -- <plan>` 为空，已复核）
- Plan sha: `46cc5c46395814b1ef03e52ab8b8bfb5197f372c`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Master plan: `docs/plans/2026-09-02/00-execution-order.md`（identity `92b0519a18a3a46989f8733259af4649f7748a72`；G-1..G-4 逐一遵守）
- Child base (product boundary) SHA: `af8fb5fad2bb28ebf18324242e2959d11d297aad`（c2 代码头；`git merge-base --is-ancestor af8fb5f HEAD` 通过）
- Implementation commit: `10a38bb6457280f7104a333faa46fad6f7cb078f`（`feat(fast-p): implement c3`，10 个授权文件，+2253 行；docs/plans/fast/** 未纳入）
- Task status: COMPLETE

## 变更文件（10，全部为计划 `## 变更文件清单` 授权文件）

| # | 文件 | 动作 |
|---|---|---|
| 0 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt` | 修改（T0：四参重载 + `max_tokens` + 非流式 usage 解析） |
| 1 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPromptConstraints.kt` | 新增（T1：检索/生成提示词常量，22 条规则含 I-18 第 12 条、派生下标、D-6 第 22 条） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPromptBuilder.kt` | 新增（T2：buildRetrievalPrompt / buildGenerationPrompt / 派生规则现算） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagProcessContextResolver.kt` | 新增（T3；I-19 映射） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagVerbatimRenderer.kt` | 新增（T4；I-15 去重+三级插入、I-14 violations/missingTokens） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagLetterComposer.kt` | 新增（T5 编排；RagComposeException + RagComposeResult 等 DTO） |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt` | 新增（T6：`POST /api/rag-reply/compose` + 400/422/502 映射 + 来源解析两仓储路径） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagLetterComposerTest.kt` | 新增（T7；14 用例含 1 条 I-46 @Disabled 登记） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagVerbatimRendererTest.kt` | 新增（T7；6 用例） |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagProcessContextResolverTest.kt` | 新增（T7；4 用例，mock 仓储零 DB） |

未触碰：`qa_rule`/`qa_category`、既有迁移、c1/c2 已提交 rag 文件（只读消费 `RagKnowledgeBase.snapshot()` /
`RagProperties` / `RagPrefilterService` / `RagMandatoryResolver`）、`ReplySnippetService`（只读第 4 消费者，零改动）、
`TrustReplyWorkbenchService`（未复用 resolveSource）、任何发送/持久化路径、`docs/plans/fast/**`。

## 关键实现

- **T0 / I-45**：`interface LlmDraftClient` 新增**带默认实现**的四参重载
  `chatWithModelObservedJson(messages, temperature, providerModel, maxTokens)`（默认委托三参）；`HttpLlmDraftClient`
  单独覆写，透传 `executeChatObserved(..., maxTokens)`，`maxTokens != null` 时请求体追加 `body["max_tokens"]`；
  三参路径不追加（行为逐字不变）。`chatWithModelObserved` / `chat` / `chatWithModel` 三个既有方法签名零改动
  （22 处测试桩 override 计数不变，实测复核 = 22）。响应体另解析 DeepSeek `usage` 块
  （`LlmTokenUsage`，非流式路径；流式 SSE 不解析，字段恒 null）——计划 T5.6 的
  `retrievalUsage/generationUsage` 返回字段的数据来源（见 §偏离 3）。
- **I-13 / G-3**：生成记录 VERBATIM 删 `answer`，追加 `render_token = {{FACT:<fact_code>}}` + `render_instruction`；
  COMPOSE 保留 answer。生成记录**无 title / retrieval_text**（G-3 生成侧禁中文）；检索记录逐字同
  `retrieval_record()`（fact_id=fact_code / title / category / coverage_keys / reply_policy / status /
  risk_level / render_mode / retrieval_text）。生成提示词全量 CJK 检测用例通过。
- **I-15 / I-14**：`RagVerbatimRenderer.render()` 照抄脚本 `render_verbatim_facts()`（去重保留首次 → 缺失三级
  回退：前一个在正文令牌之后 / 后一个在正文令牌之前 / 首段后（无空行插最前），两侧 `\n\n` → 逐字替换）。
  I-14 双层：渲染前「原稿零 VERBATIM 令牌 = 整体改写」→ 422（见 §偏离 1）；渲染后逐条断言 answer ∈ 最终正文，
  缺失列 fact_code → `RagComposeException(422, "RAG_VERBATIM_MISSING")`。不降级、不 fallback。
- **I-16**：模型 fact_ids 逐个校验（非法丢弃 + warn）→ 强制（resolver + 请求 forced，I-9 去重保留首次）前置合并
  → 覆盖键与 requested 相交的未选中候选尾部追加 → 截断 retrievalLimit 14；模型空/全非法且无强制无覆盖命中时
  回落候选前 12 条。检索缓存键 `sha256(inbound) + ":" + corpusFingerprint`（缓存模型原始 fact_ids，命中后仍走
  完整服务端校验回补）。
- **I-17**：unaddressed 校验复用 InboundAskEnumerator 判定形状（foldWhitespace 双方折叠 → 子串；折叠长 < 8 丢弃；
  重复丢弃），不匹配条目静默丢弃，不报错不影响草稿。
- **I-18 / D-5**：`GENERATION_RULES[11]`（第 12 条）改写为「Do not write a salutation, greeting, thank-you,
  or signature; the reply frame supplies them.」；`RagComposeResult(frame, bodyParagraphs, …)` 框架四段与模型正文
  分开返回；bodyParagraphs 拼接结果不含 `Wu Wei`（用例断言），`frame.closing` 含之（mock 框架验证 + A-4 语义）。
- **I-19**：`RagProcessContextResolver` — `expert_material_status` 的 CV 行 PROVIDED→RECEIVED / DECLINED→UNKNOWN /
  缺行→MISSING（PENDING 防御性同 MISSING，未知值 warn→UNKNOWN）；expertReplyCount = mail_record 中
  direction=INBOUND 计数；expertTags 空。
- **I-10**：`modelCoverage` 只赋值、不参与任何判定（grep `coverage\[` rag 目录无输出 exit 1；`modelCoverage` 仅
  DTO 字段 + 构造赋值）。unaddressed 仅做 I-17 输出过滤后透传。
- **派生三条（T2）**：`renderDerivedRules(mandatoryRules)` 按 `rag_mandatory_rule` 行（match_groups 全集精确匹配，
  `GOVERNMENT_ORG` 归一 `GOVERNMENT_ORGANIZATION`）现算第 18/19/21 条：DETAIL_INQUIRY 行令牌、名→机构→证据行
  令牌、IP 行令牌；行缺失时该条留空并从拼好的系统提示词剔除（数据驱动，06「派生 · 只读」与 A-3 联动）。
- **端点（T6）**：`POST /api/rag-reply/compose`；请求 `{sourceType, sourceId, model?, forcedFactCodes?,
  excludedFactCodes?, frameSelection?}`；来源解析按计划两条仓储路径（mail_record INBOUND / inbound_mail_processing
  + expert_contact），只取 contact/inboundText/subject/senderAccountCode 四项；forced/excluded 校验存在且 enabled
  的 fact_code 否则 400 `RAG_FACT_CODE_INVALID`；异常映射 422 `RAG_VERBATIM_MISSING` / 502 `RAG_LLM_UNAVAILABLE`。
  不持久化草稿、不接发送路径。

## 命令与结果（JDK 11 zulu-11；最终代码态新鲜执行）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `mvn test -Dtest=RagLetterComposerTest` | 0 | **Tests run: 14, Failures: 0, Errors: 0, Skipped: 1**（1 skip = I-46 登记占位），BUILD SUCCESS |
| 2 | `mvn test -Dtest=RagVerbatimRendererTest` | 0 | **Tests run: 6, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS |
| 3 | `mvn test -Dtest=RagProcessContextResolverTest` | 0 | **Tests run: 4, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS |
| 4 | `mvn test -Dtest=RagLetterComposerTest#verbatimMissingFailsWholeCompose` | 0 | **Tests run: 1, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS |
| 5 | `mvn test -Dtest=RagLetterComposerTest#generationPromptHidesVerbatimAnswers` | 0 | **Tests run: 1, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS |
| 6 | `grep -rc "override fun chatWithModelObserved\b" src/main src/test \| awk -F: '{s+=$2} END {print s}'` | 0 | **22**（I-45 覆写计数与改动前相同） |
| 7 | `mvn clean package` | 0 | **Tests run: 3097, Failures: 0, Errors: 0, Skipped: 7**，BUILD SUCCESS（c2 基线 3073 → +24 本计划用例；skip 6→7 = I-46 登记） |
| 8 | `mvn test`（全量回归） | 0 | **Tests run: 3097, Failures: 0, Errors: 0, Skipped: 7**，BUILD SUCCESS |
| 9 | `git diff --check` | 0 | 无输出 |
| — | `grep -rn 'coverage\[' src/main/kotlin/com/weibo/talentintroduction/rag/` | 1 | 无输出（I-10 验收门禁） |
| — | `grep -n modelCoverage src/main/kotlin/.../RagLetterComposer.kt` | — | 仅 DTO 声明 + 构造赋值（无 if 读取） |

全绿：无 docker / 无真实 LLM / 无网络（stub LlmDraftClient + Mockito mock 仓储/模板）。

## 偏离与登记（全部为计划验收驱动的实现语义，已登记）

1. **I-14 的 422 触发语义（D-2 平价边界，按计划验收落地）**：脚本 `render_verbatim_facts()` 对缺失令牌**总会**
   自动插入，故「渲染后 answer 缺失」在脚本语义下几乎不可达（计划 I-15 风险注记也写明「插入后 I-14 的检查必然
   通过」）；而计划验收 `verbatimMissingFailsWholeCompose` 要求「原稿既无令牌、又把原文改写」→ 422。实现按验收
   语义折衷并登记：编排层在渲染**前**判定 —— 检索集含 VERBATIM 事实且模型原稿**零** VERBATIM 令牌（整体改写）
   → 直接 422 RAG_VERBATIM_MISSING（列出全部缺失 fact_code）；原稿仍有 ≥1 个令牌时，其余缺失项照 I-15 插入救回
   （`verbatimMissingFailsWholeCompose` 与「少写一个令牌 → 插入成功」两条用例同时满足）。渲染后 I-14 子串校验
   作为最终网照常执行（病理场景兜底）。
2. **I-45 的「流式路径」不追加 max_tokens（登记说明）**：`chatWithModelObservedStream` 请求体（`"stream" to true`）
   无法接收 maxTokens —— 其签名被 18 处测试桩 override 冻结（与 I-45 的 22 处同理），且 RAG 两条调用都走非流式
   四参 JSON 重载（`executeChatObserved`）。max_tokens 只在该（非流式）请求体按 maxTokens 非空追加；3 参路径逐字
   不变。验收的请求体断言（900 → `"max_tokens":900`；null / 三参 → 无该键）全绿。
3. **`LlmChatResult` 增加 `usage: LlmTokenUsage? = null`（T0 授权文件内部扩展）**：计划 T5.6 的
   `RagComposeResult(retrievalUsage, generationUsage)` 需要 usage 数据源，而既有 `LlmChatResult` 不含 usage。
   在授权文件 `HttpLlmDraftClient.kt` 内新增 `LlmTokenUsage` 数据类 + `LlmChatResult` 尾部可空字段（默认 null，
   所有既有构造点 ≤2 位置参数零改动），仅非流式路径解析响应 `usage` 节点；流式返回恒 null。不影响任何既有行为
   （全量 3097 用例绿）。
4. **I-46 已登记（只登记，不实现）**：脚本请求体的 `"thinking": {"type": "disabled"}` 与 `"stream": false` 与
   生产 `HttpLlmDraftClient`（恒流式、不发 thinking）的偏离登记在 `RagLetterComposerTest` 类注释 + `@Disabled`
   占位用例，不作为失败项。
5. **派生第 18/19/21 条的现算措辞（实现细节）**：计划只规定「由 rag_mandatory_rule 现算成自然语言」，未固定
   模板。实现按行语义（DETAIL_INQUIRY / 名+机构+证据 / IP）生成英文指令并嵌入 `{{FACT:…}}` 令牌（无中文 title，
   G-3 用例覆盖）；行缺失留空剔除。06 的 A-3（改规则行 → 第 19 条文案变）随数据驱动自动成立。
6. **RagReplyController 来源错误的补充错误码（端点设计细节）**：计划只钉 400/422/502 三码；来源解析失败按语义
   给 404 `RAG_REPLY_SOURCE_NOT_FOUND` / `RAG_REPLY_SOURCE_CONTACT_NOT_FOUND`、422 `RAG_REPLY_SOURCE_NOT_INBOUND`
   / `RAG_REPLY_SOURCE_CONTACT_REQUIRED`、400 `RAG_REPLY_SOURCE_INVALID`，均走同一 `RagComposeException` 通道。

## 新鲜度

- Plan identity 复算: YES（46cc5c4 diff 为空；master 92b0519 未变）
- Worktree identity 复算: YES（branch `fast/2026-09-02-execution-order`；HEAD 自 163bd43 演进；base af8fb5f 为 ancestor）
- 必需命令最终代码态新鲜执行: YES（命令 1-9 + 两条 grep 门禁，全部在最终态后本轮重跑）
- 提交不含 fast-p 证据: YES（仅 10 个授权文件；docs/plans/fast/** 未纳入提交）
- 环境副作用: 无容器、无库、无真实网络调用；main checkout 曾因编辑工具相对路径误写一文件，已 `git checkout --`
  还原并复核（grep 计数 0）
