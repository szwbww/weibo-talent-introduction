---
id: K-extract-body-multipart-subtype
domain: mail
created: 2026-08-18
last_used: 2026-08-18
hit_count: 0
source: create-p:bounce-dsn-classification-and-email-invalid-writeback
severity: P1
---
经验：`ImapMailReceiveService.extractBody()`（`:151`，private）是**所有入站邮件**正文提取的
唯一实现点，经 `toReceivedMail()` → `fetchInboundSince()` / `fetchByUids()` 产出
`ReceivedMail.body`。下游消费者至少 4 处：`AutoMailReplyService:666` 内联退信判定、
`:1021`/`:1070` 写 `inbound_mail_processing.body`、QA 关键词匹配、`AiReplyDraftService` prompt。

改它必须按 multipart **子类型分流**，不能一刀切：
- `multipart/alternative` —— 各分段是**同一内容的多种表现**（text/plain + text/html）→
  取首个非空。改成拼接会让正文**重复两遍**，污染 inbound 落库、QA 匹配与 LLM prompt。
- `multipart/report` / `mixed` / … —— 各分段是**不同内容** → 必须拼接。
  原实现无差别 `.firstOrNull { it.isNotBlank() }`，导致 `multipart/report` 的第 2 分段
  （`message/delivery-status`）永远读不到，内联退信路径拿不到 `Status:` 行。

改动该方法时，回归护栏必须同时覆盖两侧：DSN 分段可读 **且** alternative 不重复。
