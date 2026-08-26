---
id: K-manual-send-fact-gate-is-the-only-seam
domain: mail
created: 2026-08-21
last_used: 2026-08-24
hit_count: 2
source: create-p:04-manual-send-fact-gate-downgrade
severity: P1
---

# 放宽人工发送的 QA 事实门禁，唯一合法接缝是 `canonicalizeFactRuleIds`

`PendingMailOperationService.canonicalizeFactRuleIds`（`:552-559`）全仓**只有一个调用者**
（`grep -rn "canonicalizeFactRuleIds(" src/main/kotlin/` → 定义 `:552` + 调用 `:165`，恰 2 行），
而它包住的 `QaFactSelectionService.select(inboundText, explicitIds, ...)` 有 **11 个生产调用点**
（`AiReplyDraftService.kt:2112`；`PendingMailOperationService.kt:170/474/490/491/557/759/761/946/949/952`）。

因此：**任何"只放宽人工发送"的需求都必须落在这个接缝上，绝不能改 `select()` 本体。**
`select()` 的显式选择校验有 4 个既有单测钉死：`QaFactSelectionServiceTest:92/128/142/160`
（NEVER 规则被拒 / 必须匹配至少一条 request / 不得跳过逐 request 关键词匹配 / 混合命中被拒），
改它会同时打破 [[K-explicit-fact-selection-must-match-request]] 与自动回复取证。

## 预检与发送早就分叉了

`preflightEditedAiReply:944-950` **已经**把同一个 `select(explicit)` 的异常降级成警告：

```kotlin
try { qaFactSelectionService.select(inboundText, factRuleIds, researchProfileSufficient) }
catch (ex: Exception) {
    warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
    qaFactSelectionService.select(inboundText, null, researchProfileSufficient)
}
```

而 `sendManualRichReply:165` 让它直接抛 → `GlobalExceptionHandler:16-17` → 400 → `app.js:10716` 裸 `alert`。
**预检说"有风险但能发"，发送直接失败**——违反 [[K-preview-mirrors-pipeline]]，也是运营看到
"橙条 + alert 同时出现"的成因。

两个不能照抄预检的地方：
1. 预检 `catch (Exception)` 是只读路径的宽容；**发送路径只能 `catch (IllegalArgumentException)`**，
   否则 DB/IO 故障会被吞成"可确认的风险"，运营点一下就把半损坏状态发出去。
2. 预检的回退**换掉了 `canonicalFactIds`**（改用自动全集）；发送侧绝不可以——
   `canonicalFactIds` 直通 `mail_record_qa_rule`，用全集兜底会关联运营根本没选的规则
   （[[K-ai-reply-prompt-vs-send-rule-ids]]、[[K-rich-reply-qa-audit-reuse]]）。

## 子集为空时不要再调 `select`

`validateExplicitSelection:357` 首行是 `require(ruleIds.isNotEmpty())`。
可用子集为空时必须直接用 `emptyList()`，调 `select(inboundText, emptyList(), ...)` 会抛
`IllegalArgumentException` → 500，需求完全落空。

## `carriesQa` 的判据不要跟着改

`:163` `val carriesQa = !qaRuleIds.isNullOrEmpty()`。降级后会首次出现
`carriesQa == true && canonicalFactIds.isEmpty()` 这个**过去不可达**的状态，链路是安全的
（`ManualReplySendAttemptService.kt:250` 的 `isNotEmpty()` 守卫使关联表零行，
`matchedQaRuleId = null`），但**不能**把判据改成 `canonicalFactIds.isNotEmpty()`——
那会让这类发送记成 `SEND_MANUAL_RICH_REPLY`，`QaRuleAuditService.aggregateRuleUsage`
（只查 `SEND_MANUAL_COMPOSED_REPLY`）从此看不见它。

关联：[[K-explicit-fact-selection-must-match-request]]、[[K-manual-send-safety-gate-first-hit-only]]、[[K-rich-reply-qa-audit-reuse]]、[[K-preview-mirrors-pipeline]]、[[K-suppression-check-call-sites]]

## 2026-08-24 边界修订：可信 workbench assembly 是独立权威路径

“唯一接缝”只适用于 `trustReplyAssembly == null` 的普通人工发送。携带 workbench assembly 时，发送服务必须在 SMTP claim 前调用服务端 assembly 重算，以其 canonical matrix 为最终事实，并禁止再进入 `canonicalizeFactRuleIds` 做关键词/intent 语义重筛；规则存在/enabled/policy/answerBody、版本、locked item 和安全校验仍硬拦。实现计划见 `03-manual-fact-authority-live-send`。
