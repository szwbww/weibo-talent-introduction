# 01 邮件摘抄拆分与 ask 坐标修正

> 执行顺序：第 1 份。后续计划依赖本计划产出的稳定 request 集合与 requestKey。

## 需求描述

修复线上 `LIVE_INBOUND:124`（对应 `inbound_mail_processing.id=124`、`mail_record.id=2559`）暴露的两项确定性缺陷：Markdown 强调式签名被拆成 5 条 `BULLET`；真实问题中的已知意图全部被记为 `unrecognized`。不改 LLM ask 枚举策略，不引入签名模型，不改自动回复判定。

线上同源复现结果：当前 `cleanedBody` 经 `QaRequestExtractor.extract` 得 6 条，只有 1 条 `QUESTION` 是真实问题，其余 5 条均为以 `*` 开头的签名/联系方式行；日志为 `enumerated=4 claimed=0 unrecognized=4 kind=BULLET,QUESTION`。计划中的回归 fixture 必须脱敏，只保留 `*Name*`、`*Title*`、`*Institution*`、`*Phone*`、`*Address*` 结构。

关联知识：[[K-request-extractor-offset-order]]、[[K-markdown-emphasis-not-request-bullet]]、[[K-ask-enum-span-coordinate-system]]、[[K-request-key-includes-intent-keys]]。

## 关键不变量

### Invariant I-1：返回 offset 始终指向原始整封文本

`QaRequestExtractor.ExtractedRequest.startOffset/endOffset` 继续满足 `messageBody.substring(startOffset, endOffset)` 可还原该条摘抄；CRLF、CR、软换行、排序、去重契约不变。

### Invariant I-2：项目符号必须是明确 list marker

仅识别 `- `、`* `、`• `、`1. `、`1) ` 及其后续内容；`*Name*`、`*Title*`、`-not a list` 不得识别为 bullet。缩进续行规则保持现状。

### Invariant I-3：claim 比较只使用一种坐标系

`EnumeratedAsk.originalRange` 是整封 `inboundText` 绝对坐标；`matchIntentsWithSpans(requestText)` 返回值是调用参数 `requestText` 的局部坐标。进入 `claimed()` 前必须把 intent ranges 加上 `RequestUnit.startOffset`，统一为整封邮件绝对坐标。

### Invariant I-4：ask 枚举仍是影子信号

修正只影响 `unrecognizedAsks`、`unrecognizedAskCount`、`enumeratorClaimed` 和日志；不得改变 status、intent 定义、fact evidence、sendQaRuleIds、promptRuleIds、自动回复开关。

### Invariant I-5：旧快照失败关闭，不迁移猜测

摘抄集合变化会改变 requestKey。`trust_reply_workbench_state` 中旧矩阵若无法对应新 request 集合，沿用现有 bootstrap fallback：返回默认选择并把存量快照标为 `STALE`，不得猜测把旧事实重新绑定到新 request。

## 现状审计（代码证据）

### 摘抄器

- `QaRequestExtractor.kt:5-8` 明确 offset 应指向原始 `messageBody`；`:26-41` 合并、排序、去重。
- `extractBullets():62-113` 在 `:69/:86` 共用 `BULLET_LINE_PATTERN`；`:89-94` 只把缩进行作为续行。
- 当前正则 `:337` 为 `^(?:[-*•]|\d+[.)]\s)`：`*`、`-`、`•` 分支没有空白要求，因此 `*Name*` 必然命中。
- `QaRequestExtractorTest.kt:10-51` 只覆盖 `- ` bullet；`:165-211` 已钉住 CRLF/CR 原始 offset，未覆盖 Markdown 强调行和其他合法 marker。

### ask/span 坐标

- `InboundAskEnumerator.kt:12-21`、`:131-143`：ask range 从整封 `inboundText` 映射回原文绝对 offset。
- `AiReplyIntentCatalog.matchIntentsWithSpans(requestText):376-425`：range 从传入的单条 `requestText` 计算，实际是该字符串局部 offset；文件头注释当前误称 raw inbound，需同步纠正。
- `QaFactSelectionService.buildRequestFact():451` 对单条 `requestText` 求局部 spans；`:506-510` 却把它直接与 ask 绝对 range 交给 `claimed()`。
- `RequestUnit:593-610` 已携带 `startOffset/endOffset/range`，无需新数据源。
- 现有 `QaFactSelectionServiceTest.kt:1367-1391` 使用 `requestRange=0 until fullText.length`，因此遮蔽非零起点；`:1450-1476` 的首条请求从 offset 0 开始，也未覆盖根因。

### `trust_reply_workbench_state` 读写路径

- 表定义 `V83__create_trust_reply_workbench_state.sql:13-23`：每个 `(source_type,source_id)` 一行，payload 为 LONGTEXT。
- `TrustReplyWorkbenchStateStore.load/save/delete/deleteBySource/pruneExpired` 是全部物理读写入口；`:194-204` 固定 256 KiB、30 天。
- `TrustReplyWorkbenchService.bootstrap():432-483` 对隐式存量矩阵解析失败会在 `:456-470` 回退默认选择并返回 `STALE`；这是本计划复用的兼容行为，不新增 migration、不改 store。

## 实现方案

### 阶段 1：收紧 bullet marker

在 `QaRequestExtractor` 将 marker 契约改为：符号 marker 和数字 marker 后均至少有一个空白，例如 `^(?:[-*•]\s+|\d+[.)]\s+)`。不改 URL 屏蔽、question 拆分、重叠去重、续行算法。

在 `QaRequestExtractorTest` 增加两组测试：

1. 脱敏线上结构：真实复合 question + 5 条 `*...*` 签名，断言仅返回 1 条 `QUESTION`，offset 可回切原文。
2. marker 边界表：合法五种 marker 均为 `BULLET`；`*Name*`、`-not a list` 不命中；缩进续行、源顺序不变。

### 阶段 2：统一 ask/span 绝对坐标

在 `QaFactSelectionService.buildRequestFact` 内保留局部 `matchedSpans` 供 intent/status 使用；仅为 shadow claiming 派生 `absoluteMatchedSpans`：每个 range 的 `first/last` 加 `requestRange.first`。`requestRange == null` 时保持现有“不归属 ask”的行为。`claimed()` 自身继续只接收同坐标系 range，不改枚举器。

同时修正 `AiReplyIntentCatalog.MatchedIntentSpan` 注释：range 属于传给 `matchIntentsWithSpans` 的字符串坐标，调用者若传 request slice 必须负责 rebase。

### 阶段 3：存量快照回归

在 `TrustReplyWorkbenchServiceTest` 构造“旧矩阵含签名 requestKey、新 extractor 只剩真实 question”的隐式 saved payload，断言 bootstrap 不抛 422：返回当前默认矩阵、`savedState.status=STALE`、锁定项为空。显式传入错误矩阵仍保持 `TRUST_REPLY_FACT_SELECTION_INVALID`。

## 变更文件清单

| 文件 | 修改 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractor.kt` | 收紧 bullet marker |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | 修正 span 坐标注释 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | claim 前把局部 span rebase 为绝对坐标 |
| `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractorTest.kt` | 脱敏线上结构、marker、offset 回归 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 非零 request offset 的 claimed/unrecognized 回归 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | requestKey 漂移后的 saved-state STALE 回归 |

范围：6 个文件；2 个子系统（QA 摘抄、LLM 工作台）。无 DB schema、API、前端、CSS 变更。

## 验收标准

- 脱敏线上 fixture：`requestCount=1`、kind 仅 `QUESTION`，5 条 `*...*` 签名均不存在于结果。
- `- `、`* `、`• `、`1. `、`1) ` 均保留；无空白 marker 均拒绝；CRLF/CR offset 测试继续通过。
- 带前置正文、真实 request 起点 `>0` 的枚举测试中：eligibility/application process/timeline 被 claimed；未知 technical-role ask 保持 unrecognized；计数非负且总数守恒。
- 同一输入关闭/失败 ask enumerator 后，status、intents、factRuleIds、sendQaRuleIds 与修复前行为一致。
- 旧 saved matrix 无法匹配新 requestKey 时返回 `STALE` 而非工作台 422；显式脏矩阵仍 422。
- 定向测试：
  - `mvn -q -Dtest=QaRequestExtractorTest,QaFactSelectionServiceTest,TrustReplyWorkbenchServiceTest test`
  - `git diff --check`

## 人工验收清单

1. 打开 `LIVE_INBOUND:124` 对应线上邮件的工作台。
2. 确认只出现真实问题摘抄，姓名/职位/机构/电话/地址签名不再生成摘要卡片。
3. 检查服务日志：真实已知 asks 的 `claimed` 增加，未知 ask 仍计入 `unrecognized`。
4. 若该邮件已有旧工作台快照，确认界面提示状态过期并允许重新选择，不出现 bootstrap 422。

