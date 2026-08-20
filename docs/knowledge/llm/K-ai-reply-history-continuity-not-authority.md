---
id: K-ai-reply-history-continuity-not-authority
domain: llm
created: 2026-07-21
last_used: 2026-08-19
hit_count: 4
source: create-p:ai-reply-10-history-context-recipient-identity
last_source: fix-v:ai-reply-failure-trust-closure-master-plan:blocked-after-fix-1
severity: P1
---
经验：历史邮件能提升回复连续性，但旧出站内容可能过时、失败或未经审核，不能升级成当前事实来源。
正确做法：只取真实入站与 SENT 出站，精确排除当前入站并设置条数/单封/总字符预算；prompt 明示 history 仅用于语气、旧质疑和已提下一步。requestFacts、claims 与 sourceIds 仍只能来自当前审核事实/允许画像，fallback 不消费历史。
写路径边界：`mail_record` 的生产 writer 包括 ManualOutreachTxHelper、MeetingScheduleService、ManualExpertMailService、ManualReplySendAttemptService、AutoMailReplyService、MailboxService；history reader 必须按 direction/sendStatus 解释这些 writer 的结果，不能假设列表中的 OUTBOUND 都已发送。
反例：把整个联系人邮件列表直接放进 prompt，或从历史里的资金、合同承诺生成无 sourceId claim。
