---
id: K-manual-send-safety-gate-first-hit-only
domain: mail
created: 2026-08-20
last_used: 2026-08-20
hit_count: 0
source: create-p:manual-send-safety-confirm
severity: P1
---

# 人工发送门禁只报首命中，且预检会静默丢弃动作违规

`PendingMailOperationService.performFinalBlockingCheck()`（`:691-775`，生产调用点恰 1 处 `:218`）
的每个分支都是 `return <code>`，**一次请求只能产出 1 个码**。运营改完第一条再发，第二条才冒出来。
任何"把门禁改成提示/确认"的需求，第一步必须先把它改成收集全集，否则前端确认流只能覆盖一条。

## ACTION 违规的 `code == null` 语义

`AiReplyActionPolicy.findViolations()`（`AiReplyActionPolicy.kt:123-152`）返回的
`ActionViolation.code` 只在**动作已被 allowed 授权**时才有值——`detectCvConditionViolation()`
首行就是 `if (REQUEST_MATERIALS !in allowed) return null`（`:329-331`），
且 `PROPOSE_MEETING` 分支从不赋码（`:140-143` 的 `when` 只处理 `REQUEST_MATERIALS`）。

因此 **`code == null` 的唯一含义是「该动作不在 allowed 集合里」**，是最常见的一类违规，
却是唯一没有稳定标识的一类。两个后果：

1. `PendingMailOperationService.kt:770` 输出裸 `ACTION_VIOLATION`，运营看不出改哪句。
2. **预检 `:1024` 的 `if (code != null && code !in warningCodes)` 把这类违规整个丢掉** ——
   预检面板显示"未发现新增风险"，点发送却被拦。两者对同一封信给出相反结论。

## 其余 8 个调用点不读 code

`findViolations` 全仓 10 个调用点（`AiReplyHighRiskClaimValidator:47`、
`AiReplyDraftService:708/1414/1452/1770/1793`、`TrustReplyWorkbenchService:1353/1367`、
`PendingMailOperationService:768/1022`）中，除 `PendingMailOperationService` 两处外
**全部只消费 `isNotEmpty()` 或违规条目本身，没有一处读 `.code`**。
给 `code` 补兜底值是安全的，不会波及 AI 生成路径。

## 两处不可达的防御性分支（不要当缺陷报）

- `:706` 的 `QA_FACTS_ALL_INVALID`：`:173-178` 已提前抛 422「所选的QA事实已全部失效」。
- `:703` 的 `?: "CLAIM_VALIDATION_FAILED"`：`validatePlainText`（`AiReplyHighRiskClaimValidator.kt:56-78`）
  两条 `valid=false` 出口都带非空 `warningCodes`。

关联：[[K-preview-mirrors-pipeline]]、[[K-ai-reply-action-cta-variant-coverage]]、[[K-sensitive-material-cta-not-mention]]
