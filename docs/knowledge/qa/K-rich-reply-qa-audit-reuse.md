---
id: K-rich-reply-qa-audit-reuse
domain: qa
created: 2026-06-30
last_used: 2026-07-17
hit_count: 20
source: fix-v:ai-reply-review-authority-fail-closed:fix-1
severity: P1
---
经验：人工富文本回复（`sendManualRichReply`）若承接了组装台/AI 的 QA 规则子集，要让现有审计零改动地继续统计，必须用 `SEND_MANUAL_COMPOSED_REPLY` 这个 action type 记日志（而非 `SEND_MANUAL_RICH_REPLY`），并同时写 `mail_record_qa_rule`。原因：`QaRuleAuditService.aggregateRuleUsage` 只查 `SEND_MANUAL_COMPOSED_REPLY` 单一 action，并经 `resolveSelectedRuleIds` 从 `mail_record_qa_rule` 取 selected。
判定开关：`qaRuleIds` 非空 = 携带 QA（记 COMPOSED action + 写关联表 + matchedQaRuleId=primary）；为空 = 纯人工（记 RICH_REPLY action + 不写关联表 + matchedQaRuleId=null），空集严禁全集兜底（见 K-ai-reply-prompt-vs-send-rule-ids）。
注意：日志 action 语义会出现「rich reply 记成 composed」的偏差，这是为保持审计 action 单查询而有意为之；若日后要让 action 语义诚实，需改 `QaRuleAuditService` 同时查两种 action 并过滤无 qaRuleIds 的纯人工记录。
关联：K-audit-selected-source、K-audit-free-text-topic、K-composed-reply-order-contract。
