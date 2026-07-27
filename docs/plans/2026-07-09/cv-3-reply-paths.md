# 计划 CV-3：QA 回复路径接变体与人工 useVariants（cv-3-reply-paths）

> 系列：变体机制二次重构 3/6。**依赖 CV-1**（resolveBody）；建议在 CV-2 后合入（有变体数据可验）。
> 决策：自动回复路径默认启用变体轮换；人工回复整封 `useVariants` 开关默认 false（主体）。

## 需求描述

可观测结果：
1. 自动 QA 回复（QaMatchService 组装）按专家 seed 启用变体轮换，AutoReplyPreviewService 预览与实发同变体。
2. 人工回复（未匹配来信 qa-reply / manual-rich-reply / suggest 组装建议）新增 `useVariants: Boolean = false`：false 恒主体；true 按该专家 seed 解析，且 suggest 预览文本与最终发送文本一致（所见即所发）。

不得改变：
- `qaRuleIds` 顺序契约与 `composeInOperatorOrder` 的骨架拼接顺序（salutation/ack/greeting/sections/closing）。(来源: CLAUDE.md K-composed-reply-order-contract)
- `overrideTextBody` 优先级（运营改过正文以运营版为准，PendingMailOperationService 现行为）。
- AI 草稿路径（LlmStitchService / AiReplyDraftService）恒用主体——AI 草稿是给人改的底稿，不参与变体。
- 模板路径 seed 派生（K-variant-seed-call-sites 6 调用点）。
- 复合覆盖/缺口检测语义（K-overview-gap-supersede）。

超出范围（明确不做）：
- 前端 useVariants 勾选 UI（CV-5）；模板路径（CV-1 已接）；变体 CRUD（CV-2）。

## 关键不变量

### Invariant I-1: 自动路径默认启用
- Rule: QaMatchService 组装回复前，每条命中 rule 的 replyBody 经 `contentVariantService.resolveBody(QA_RULE, rule.id, rule.replyBody, seed)` 解析；seed = `MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)` 由调用方（AutoMailReplyService）传入；QaMatchService 的组装方法增 `variantSeed: Int = 0` 参数（缺省 0 保持其他调用方兼容）。
- Applies to: QaMatchService 组装路径、AutoMailReplyService QA 自动回复调用点。
- Violation consequence: 自动路径仍全网同文，反垃圾目标落空。
- 来源: original（决策 5）+ K-variant-seed-call-sites

### Invariant I-2: 预览镜像
- Rule: AutoReplyPreviewService 对 QA 回复的预览必须与 AutoMailReplyService 同 seed、同 resolveBody、同组装顺序；无 contact 时 seed=0 并注释标注偏差。
- Applies to: AutoReplyPreviewService QA 分支。
- Violation consequence: 运营据错误预览调策略（P1 级）。
- 来源: K-preview-mirrors-pipeline

### Invariant I-3: 人工路径所见即所发
- Rule: `useVariants` 从请求进入后，suggest（组装建议）与 send（sendQaReply / sendManualRichReply 的重建）使用**相同 useVariants + 相同 seed**解析 rule bodies；变体解析只发生在文本进入运营视野之前——发送时对运营已见文本不做二次替换；`overrideTextBody` 非空时最终正文以其为准（现行为）。
- Applies to: PendingMailOperationService.sendQaReply/sendManualRichReply/suggest 链、UnmatchedInboundMailController 对应端点与 Request DTO。
- Violation consequence: 运营预览 A 发出 B。
- 来源: original（决策 5）

### Invariant I-4: useVariants 默认 false
- Rule: 三个人工端点 Request DTO 的 `useVariants: Boolean = false`；不传视为 false（恒主体）。自动路径无此开关（恒启用）。
- Applies to: UnmatchedInboundMailController PendingQaReplyRequest / PendingManualRichReplyRequest / suggest 参数。
- Violation consequence: 旧客户端行为突变。
- 来源: original

### Invariant I-5: 人工框架片段同规则
- Rule: 人工组装的骨架片段（resolveManualFrame 的尊语/开场白/结束语、resolveAck）在 useVariants=true 时同样经 resolveBody(REPLY_SNIPPET, snippet.id, content, seed) 解析，false 时主体；解析位置在 frame 返回给前端/组装之前（服从 I-3 所见即所发）。
- Applies to: PendingMailOperationService 组装调用处（不改 ReplySnippetService.resolveManualFrame 本体，在消费点解析）。
- Violation consequence: 正文轮换而骨架恒定，指纹分散不完整。
- 来源: original

## 现状审计

### QA replyBody 出站组装点（grep 全集）
1. `QaMatchService:67` — `QaReplyComposer.compose(matches, categoryComposeOrder)`（自动路径唯一组装点）；消费方 AutoMailReplyService（实发）与 AutoReplyPreviewService（预览）。
2. `PendingMailOperationService:336` — `composeInOperatorOrder(...)`（人工 rich-reply 重建）；:320-350 上下文含 resolveManualFrame/resolveAck/overrideTextBody 优先级。
3. `PendingMailOperationService.sendQaReply` — 单规则人工发送（Controller :185）。
4. `LlmStitchService:61` — AI 拼接（**恒主体，不动**）。
5. `MailComposeTemplateService.resolveBlocks` — 模板块（CV-1 已接，不动）。
6. `QaRuleManagementService/Controller`、`UnmatchedInboundMailController` 详情回显 — 展示主体，不解析变体（运营编辑的是主体）。
- Interaction points: ① CV-2 写入的变体 × 本计划 3 条出站路径；② suggest（写运营视野）× send（重建比对 edited 标记）——I-3 同参数保证 edited 判定不被变体解析污染（suggest 与重建同文，运营未改则 edited=false 不变）。

### 人工端点请求链（UnmatchedInboundMailController）
- `:185 sendQaReply(PendingQaReplyRequest{qaRuleId, senderAccountCode, operatorName})`
- `:197 sendManualRichReply(PendingManualRichReplyRequest{senderAccountCode, subject, htmlBody, textBody, operatorName, qaRuleIds, suggestedRuleIds, ackSnippetId, edited, freeTextPreview})`
- `:221 suggestComposedReply(id)`（GET，需增 useVariants query 参数）
- contact 获取：PendingMailOperationService 内 `expertContactRepository.findById(contactId)`（:333 上下文可见）→ seed 材料现成。

### 测试基线
`QaMatchServiceTest`、`AutoMailReplyServiceTest`、`AutoReplyPreviewServiceTest` 已存在；`PendingMailOperationServiceTest` 执行时确认（无则新建）。mock 需按 I-1 显式匹配 variantSeed（禁 relaxed 放宽，规则同 variant-pool-2 计划 I-3）。

## 实现方案

### T1 — QaMatchService 接 seed（I-1）
组装方法增 `variantSeed: Int = 0`；命中 rules map 为 `rule.copy(replyBody = resolveBody(QA_RULE, rule.id, rule.replyBody, variantSeed))` 后再进 QaReplyComposer（composer 本体零改动）。

### T2 — 自动路径与预览（I-1, I-2）
`AutoMailReplyService`：QA 自动回复调用点传 `variantSeedFor(contact.orcidId, contact.expertEmail)`；`AutoReplyPreviewService`：同式同源传入（并排 diff 一致）。

### T3 — 人工路径（I-3, I-4, I-5）
`UnmatchedInboundMailController`：两个 Request DTO 增 `useVariants: Boolean = false`，suggest 端点增 `@RequestParam(defaultValue = "false") useVariants`；`PendingMailOperationService`：sendQaReply/sendManualRichReply/suggest 链贯通 useVariants + seed，rule bodies 与骨架片段按 I-3/I-5 解析。

### T4 — 测试（全不变量）
四个测试文件：自动路径 seed slot 断言（=variantSeedFor 期望值）；预览=实发同文（同 mock 数据双跑对比）；useVariants=false 恒主体 / true 时 suggest 与 send 同文；LlmStitchService 输入仍为主体（AutoMailReplyServiceTest 或独立断言）；既有用例全绿。

## 变更文件清单

| # | 文件 | 变更 |
|---|------|------|
| 1 | src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt | T1 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt | T2 |
| 3 | src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt | T2 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt | T3 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt | T3 |
| 6 | src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt | T4 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt | T4 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt | T4 |
| 9 | src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt | T4（无则新建） |

文件数 9 ≤ 10；子系统 2（qa、mail）；新增字段 0（仅请求 DTO 参数）。

## 验收标准

- I-1: QaMatchServiceTest 断言 seed 传导与 rule.copy 解析；grep AutoMailReplyService QA 调用点含 variantSeedFor。
- I-2: 并排 diff 预览与实发的 seed 派生表达式逐字一致；同数据双跑输出相等的用例。
- I-3: useVariants=true 时 suggest 文本 == send 重建文本（用例断言）；overrideTextBody 优先用例保持通过。
- I-4: 不带 useVariants 的旧请求 JSON 反序列化后为 false（用例）。
- I-5: frame 片段在 useVariants=true 用例中输出变体、false 输出主体；`git diff` 确认 resolveManualFrame/resolveAck 本体零改动。
- composer 零改动：`git diff` QaReplyComposer.kt 空。

## 人工验收清单

### A-1: 自动 QA 回复轮换（覆盖需求第 1 条、I-1）
- 前置条件: 某高频 QA 规则带 2 条变体（CV-2 API 建）；两名不同专家的来信各命中该规则（可用测试邮箱发信触发自动回复流程）。
- 操作步骤: 1) 触发两名专家的自动回复；2) 查发件箱两封回复正文该段。
- 预期结果: 同一专家重复触发恒同文；两名专家间大概率不同文（主体/变体之一）；骨架顺序（问候/正文/结尾）不变。

### A-2: 自动预览=实发（覆盖 I-2）
- 前置条件: A-1 中专家甲的来信处于待处理。
- 操作步骤: 1) 未匹配/来信详情点「自动回复预览」记录正文；2) 触发实际自动回复；3) 对比。
- 预期结果: 预览正文与实发正文逐字相同（含变体选择）。

### A-3: 人工回复默认主体（覆盖需求第 2 条、I-4，回归）
- 前置条件: 同规则带变体；一封未匹配来信。
- 操作步骤: 人工回复界面（不勾任何新选项，本阶段无 UI 则 curl 不带 useVariants）走 suggest → 发送。
- 预期结果: 建议与发出的正文均为规则主体 replyBody，无任何变体文案。

### A-4: useVariants=true 所见即所发（覆盖 I-3/I-5）
- 前置条件: 同 A-3。
- 操作步骤: 1) GET suggest 加 `?useVariants=true` 记录正文；2) POST manual-rich-reply body 加 `"useVariants": true`（不改正文）；3) 比对发出邮件。
- 预期结果: 发出正文与 suggest 逐字一致；骨架片段与规则段落均为该专家 seed 对应变体；响应中 edited=false。

### A-5: AI 草稿回归（覆盖 must-NOT-change 第 3 条）
- 前置条件: 同规则带变体。
- 操作步骤: 人工回复界面用 AI 拼接/润色生成草稿。
- 预期结果: 草稿中规则段落为主体文本（不含变体文案）。

### A-6: 运营改稿优先级回归（覆盖 must-NOT-change 第 2 条）
- 前置条件: 同 A-4。
- 操作步骤: suggest(useVariants=true) 后手工把正文改成自定义文本再发送。
- 预期结果: 发出的是运营改后的文本，edited=true，变体不覆盖运营修改。
