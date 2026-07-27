# AI 回复 URL 安全请求抽取

## 需求描述

Observable outcome：带 Google Scholar `citations?user=...`、Scopus `detail.uri?authorId=...` 的邮件中，URL 不计为问题；`requestItems/unsupportedRequests` 只包含完整、可读的专家请求。  
What must NOT change：普通问号句、项目符号列表、无问号无列表 fallback；`QaMatchService.match()` 自动回复逻辑。  
Out of scope：URL 内容抓取、链接可用性验证、前端文案格式、QA 关键词调整。

## 关键不变量

### Invariant I-1: URL 标点不参与句法抽取
- Rule: 在问号句提取前，将 `http://`/`https://` URL 替换为不含 `? . !` 的等长或哨兵文本；URL 整行不得成为 request item。
- Applies to: `extractRequestItems`、`extractGapTexts/countQuestionUnits`。
- Violation consequence: `com/citations?`、`uri?` 成为伪缺口并展示给运营。
- 来源: original

### Invariant I-2: 人类问题保持原文
- Rule: 掩码只用于定位；最终 request item 必须从原文对应区间还原并 trim，不能把 URL 或句子改写后返回。
- Applies to: suggestion gapItems、grounded unsupportedRequests。
- Violation consequence: 前端提示不可读，QA 关键词匹配失真。
- 来源: original

### Invariant I-3: 两个抽取消费者同源
- Rule: `extractGapTexts` 与 `extractRequestItems` 必须调用同一 URL-safe question extractor，禁止复制两套 regex。
- Applies to: 自动 gap 计数、人工/AI suggestion。
- Violation consequence: 自动路径与 AI 草稿对同一邮件算出不同请求数。
- 来源: K-gap-items-compose-only（重新核对：展示结构与自动判定不同，但底层 question tokenizer 必须同源）

## 现状审计

### `QaMatchService` 内存派生数据
- Schema/mapping: 无持久化；`CompositionSuggestResult.gapItems` 进入 AI draft coverage，`QaMatchResult.gapDetected` 进入自动路径。
- Write paths:
  1. `suggestComposition` → `extractRequestItems` 写入 `gapItems`。
  2. `match` → `detectGap` → `extractGapTexts` 写入 `gapDetected`。
- Read paths:
  1. `AiReplyDraftService.resolveQaRules` 读取 gap text/candidate ids。
  2. QA 组装前端读取 gapItems。
  3. 自动收信处理读取 gapDetected。
- Interaction points: 两个 extractor 当前都直接使用 `QUESTION_SENTENCE_PATTERN = [^?.!\n]*\?`；URL query delimiter 会同时污染请求计数与 UI unsupported 文案。

## 实现方案

### T1：先写回归测试（I-1/I-2/I-3）
文件：`src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt`

- 使用本次专家完整邮件（保留两条 URL、6 个 bullet、研究匹配问句）。
- 断言 requestCount/gapItems 不含 `com/citations?`、`detail.uri?`、`authorId`。
- 断言两个 URL 自身不产生 candidate/gap item。
- 断言 `Could you please confirm whether ...?` 仍完整保留。
- 回归：普通 `What funding is available? Can we arrange a meeting?` 仍为 2 项；bullet 中含 URL 但有说明文字时只保留整条 bullet 一次。

### T2：实现单一 URL-safe tokenizer（I-1/I-2/I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt`

- 新增私有 `extractQuestionSentences(messageBody): List<String>`。
- URL regex 固定支持 `https?://[^\s<>]+`；扫描时把 URL 区间替换为空格后执行问号句定位，再按原始 offset 截取原句。
- trim 句末空白；过滤归一化后只剩 URL/domain/path/query/punctuation 的项。
- `extractGapTexts`、`extractRequestItems` 统一调用该函数。
- bullet 优先、normalized dedup、whole-body fallback 保持原样。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt` | URL-safe tokenizer 与双消费者复用 |
| `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt` | URL/普通问句/bullet 回归 |

## 验收标准

- I-1：完整专家邮件结果中无 URL 片段 request item。
- I-2：研究匹配问题和 6 个 bullet 原文可读、顺序稳定。
- I-3：`extractGapTexts`、`extractRequestItems` 源码只调用同一 question extractor；定向测试通过。
- 命令：`mvn -Dtest=QaMatchServiceTest test`。

## 人工验收清单

### A-1: Scholar/Scopus 邮件提示可读
- 前置条件: 在 AI 回复训练选择包含两条示例 URL 的专家来信。
- 操作步骤: 1. 点击生成模拟回复；2. 查看事实覆盖与缺少依据提示。
- 预期结果: 提示不得出现 `com/citations?`、`uri?`、`authorId`；只显示完整自然语言请求。
- 覆盖: I-1/I-2

### A-2: 普通多问邮件不回归
- 前置条件: 来信正文为 `What funding is available? Can we arrange a meeting?`。
- 操作步骤: 生成模拟回复。
- 预期结果: 事实覆盖分母为 2，不是 0/1/3。
- 覆盖: I-3 / must-NOT-change

