---
id: K-inbound-processing-write-paths
domain: mail
created: 2026-07-01
last_used: 2026-07-01
hit_count: 1
source: create-p:inbound-mail-tag-backend
severity: P2
---
经验：`inbound_mail_processing` 的写路径分「新建行」与「copy 改状态」两类，任何要在「来信落库时」挂副作用（打标签、发通知、索引等）的功能，只需挂在两个**新建 sink**，不要挂在状态更新路径。
新建 sink（grep `InboundMailProcessing(`）：
① `AutoMailReplyService.confirmProcessed()`（~:1045）——所有 PROCESSED/MANUAL_REVIEW 来信主落库点；`confirmManualReview()` 委派于此。注意 QA 自动回复路径（~:618）调用它时**不传 cleanedBody（存 null）**，取正文须 `cleanedBody ?: body ?: received.body` 回退（见 K-cleanedbody-inbound-only）。
② `AutoMailReplyService.confirmManualReviewWithBody()`（~:1010）——带 cleanedBody，QA_NO_MATCH/QA_GAP/退订等。
copy 改状态（非新建，勿挂新建副作用）：`UnmatchedInboundMailService.bindToContact()`(:177)/`markResolved()`(:217)、`PendingMailOperationService.markResolved()`(:459)。
配合 K-process-single-all-callers：6 个 processSingle 调用方最终都汇入上述两 sink，故挂一次即全覆盖；副作用须 best-effort（runCatching）避免阻断收信主流程。
