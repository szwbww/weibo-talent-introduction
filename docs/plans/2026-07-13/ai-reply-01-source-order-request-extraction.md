# AI 回复请求提取：来源顺序与跨行完整性

## 需求描述

Observable outcome：邮件中的问句与列表请求按原文出现位置输出；软换行拆开的问句合并为完整请求，Google Scholar/Scopus URL 查询参数不产生伪请求。本次邮件得到 7 项，研究匹配问题排第 1，不能变成末尾的 `enterprise projects your team manages`。

What must NOT change：QA keyword 匹配规则、`applySupersede`、内容变体、`GapItem` API 字段、URL-only 过滤、自动 `match()` 的回复正文与审计 rule id。

Out of scope：请求语义分类、QA 依据状态、LLM prompt、邮件正文排版、前端显示。

## 关键不变量

### Invariant I-1: 单一来源顺序
- Rule: 每个提取单元携带原文 `startOffset/endOffset`；bullet 与问句候选合并后按 `startOffset` 升序，禁止“先全部 bullet、后全部问句”。
- Applies to: `suggestComposition()` 请求列表、自动 gap 计数。
- Violation consequence: 专家最先提出的研究匹配问题被移动到最后。
- 来源: original

### Invariant I-2: 跨行问句保持完整
- Rule: 同一段落内的单换行视为软换行；问句匹配可跨单换行但不得跨空白段落。返回文本把软换行折叠为一个空格，不得只返回问号所在最后一行。
- Applies to: 普通问句、研究匹配问句。
- Violation consequence: `Based on my research profile...` 被截断成 `enterprise projects your team manages`。
- 来源: original

### Invariant I-3: URL 掩码保持 offset
- Rule: `http(s)://` URL 在定位阶段按等长字符掩码；候选文本从原文 range 恢复；URL 内 `?`、`.` 不得成为问句边界，文本+URL bullet 仍保留一次。
- Applies to: Scholar、Scopus、普通 URL。
- Violation consequence: `com/citations?`、`detail.uri?` 再次进入缺口列表。
- 来源: K-url-query-question-tokenizer

### Invariant I-4: overlap 与去重确定性
- Rule: question range 与 bullet range 重叠时保留 bullet 单元、丢弃重叠 question 单元；非重叠候选按归一化文本首次出现去重；归一化不得改变返回原文内容。
- Applies to: `- What is funding?`、重复问题、带编号列表。
- Violation consequence: 同一问题生成两节回复。
- 来源: original

### Invariant I-5: 两条消费路径共用提取结果
- Rule: `suggestComposition()` 与 `detectGap()` 必须调用同一个 extractor；自动路径只消费单元数量，不读取 `candidateRuleIds` 或 AI matrix。
- Applies to: AI suggestion、人工组装 gap、自动 gap count。
- Violation consequence: UI 显示 7 项而自动路径按另一套 tokenizer 计数。
- 来源: K-gap-items-compose-only

### Invariant I-6: 空输入降级不变
- Rule: 无 bullet/问号且含普通文字时返回整个 trim body 一项；空白或 URL-only body 返回 0 项。
- Applies to: 感谢类普通回复与 URL-only 邮件。
- Violation consequence: FREE_FORM 模式选择回归。
- 来源: K-url-query-question-tokenizer

## 现状审计

### 请求单元内存结构
- Schema/mapping: 无数据库；当前 `GapItem(text,candidateRuleIds)` 位于 `QaMatchService.kt:269-272`，没有 offset 字段，对外响应只返回现有两个字段。
- Write paths:
  1. `QaMatchService.suggestComposition()`（17-59）— 私有 `extractRequestItems()` 产出文本，再为每项计算 candidate ids。
  2. `QaMatchService.detectGap()`（117-128）— 私有 `extractGapTexts()` 单独计算 question unit 数量。
- Read paths:
  1. `AiReplyDraftService.resolveQaRules()` — 按 `gapItems` 顺序创建 `RequestFactItem.index`。
  2. `UnmatchedInboundMailController.toResponse()` — 将 `gapItems` 原序返回组装台。
  3. `app.js:8206-8260` — 组装台按数组 index 显示并选择 gap。
  4. `QaMatchService.detectGap()` — 只读取数量，决定自动回复 gapDetected。
- Interaction points: extractor 顺序直接成为 AI 编号顺序和组装台顺序；自动路径只共享 tokenizer/count，不共享 AI 状态。（来源: K-gap-items-compose-only / K-request-facts-not-flat-pool）

### 当前缺陷证据
- `QaMatchService.kt:143-173` 明确执行 `bullets + uncoveredQuestions`，现有测试也断言研究问句位于第 7。
- `QUESTION_SENTENCE_PATTERN=[^?.!\n]*\?` 禁止跨换行；真实邮件的研究问题跨三行，只保留最后一行。
- URL 等长掩码已存在，必须迁移到 extractor，不能回退为删 URL 后重新计算 offset。

## 实现方案

### T1：新增 offset-aware extractor（I-1/I-2/I-3/I-4/I-6）
文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractor.kt`

- 新增 pure internal object `QaRequestExtractor` 与 `ExtractedRequest(text,startOffset,endOffset,kind)`；`kind` 仅 `BULLET/QUESTION/FALLBACK`，不进入 controller DTO，也不增加 Spring constructor 依赖。
- 统一 `\r\n/\r` 处理但保留 offset 映射；段落内将单换行作为空格建立 searchable view，同时保留 searchable index → 原文 index 映射。
- bullet range：识别 `-/*/•/数字点/数字右括号` 起始行；连续非空、非下一 bullet 的缩进行作为 continuation。
- question range：在单段 searchable view 内定位 `?`/`？`；起点为该段上一终止符后的首个非空字符；URL range 先等长掩码。
- overlap 规则严格按 I-4；最后按原文 offset 排序、归一化去重。

### T2：QaMatchService 单源接入（I-1/I-5/I-6）
文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt`

- `suggestComposition()` 调用 `QaRequestExtractor.extract(...).map { it.text }`。
- `countQuestionUnits()` 使用同一结果数量；删除 `extractGapTexts/extractRequestItems/extractQuestionSentences` 与重复 URL/bullet regex。
- `matchRule/normalize/applySupersede/resolveMatchVariant` 不改。

### T3：extractor 单元测试（I-1/I-2/I-3/I-4/I-6）
文件：`src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractorTest.kt`

- 完整真实邮件断言 7 项逐字顺序；第 1 项包含 `Based on my research profile`、`areas of expertise`、`enterprise projects your team manages?`，内部无 `\n`。
- 问句在 bullet 前、bullet 在问句后仍按 offset；同一 bullet 问句只出现一次。
- Scholar/Scopus URL-only 为 0；文本+URL bullet 为 1 且保留 URL。
- 两个同行问句为 2；段落之间不跨界；普通无问号正文 fallback 为 1。

### T4：QaMatchService 集成回归（I-1/I-3/I-5/I-6）
文件：`src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt`

- 更新旧“bullets first”测试为“source offset order”。
- 修改 URL 专家邮件断言：研究问题为 index 0，六个 bullet 为 index 1..6。
- 自动 `match()`：supersede、variant、matchedRuleIds、replyBody 断言保持；新增 extractor 前后普通两问 gap count 等价测试。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractor.kt` | 新增统一 offset-aware 请求提取器 |
| `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt` | suggestion 与 gap count 接入单一提取器 |
| `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractorTest.kt` | 新增跨行、offset、URL、overlap 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt` | 顺序与自动路径集成回归 |

## 验收标准

- I-1：真实邮件 `gapItems[0]` 为研究匹配完整问句，后续六项保持邮件顺序。
- I-2：跨三行问句返回一项且三段关键词完整；不含软换行。
- I-3：结果不含 `com/citations?`、`detail.uri?`、`authorId` 伪项。
- I-4：bullet 问句不重复；重复文本只保留第一次。
- I-5：测试 spy/断言证明 suggestion 与 gap count 调用同一 extractor；自动回复正文和 matched ids 回归通过。
- I-6：plain fallback=1、blank=0、URL-only=0。
- 定向命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QaRequestExtractorTest,QaMatchServiceTest test`。

## 人工验收清单

### A-1: 真实邮件请求顺序
- 前置条件: 在 AI 训练模拟中选择本次 Pracheta Janmeda 入站邮件。
- 操作步骤: 1. 点击生成模拟回复；2. 在浏览器 Network 查看 `/api/ai-training/simulate` 响应；3. 展开 `requestCoverage`。
- 预期结果: 数组长度为 7；第 1 项是完整研究匹配问句；第 2–7 项依次为公司、项目、匹配、职责、合同/IP、流程；不存在 URL 片段。
- 覆盖: I-1/I-2/I-3

### A-2: 普通单问题回归
- 前置条件: 选一封仅询问 `What funding is available?` 的入站邮件。
- 操作步骤: 1. 打开收发件 AI 回复；2. 生成草稿；3. 查看反馈覆盖数。
- 预期结果: 覆盖总数为 1；模式不因 extractor 变为多问题；草稿正文与现有单问题路径一致。
- 覆盖: I-5/I-6 / must-NOT-change

### A-3: 自动 gap 回归
- 前置条件: 一封命中 overview supersede 的多问测试邮件。
- 操作步骤: 1. 运行自动匹配预览接口；2. 查看 `matchedRuleIds` 与 `gapDetected`。
- 预期结果: overview supersede 的 `matchedRuleIds` 与改动前相同；`gapDetected=false`。
- 覆盖: I-5 / interaction point / must-NOT-change

### A-4: GapItem API 与内容变体回归
- 前置条件: 一条 QA rule 配置 main body 与一个 enabled variant；准备包含普通问题与 URL-only 行的 suggestion 邮件。
- 操作步骤: 1. 以固定 variantSeed 调用自动匹配；2. 调用 suggestion 接口并查看 gapItems JSON。
- 预期结果: 自动回复正文仍选中改动前相同 variant；每个 gap item 仍只有 `text` 与 `candidateRuleIds` 两个字段；URL-only 行不出现。
- 覆盖: I-3/I-5/I-6 / content variant、GapItem API、URL 过滤 must-NOT-change
