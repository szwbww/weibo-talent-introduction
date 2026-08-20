---
id: K-training-knowledge-inside-profile-text
domain: llm
created: 2026-08-19
last_used: 2026-08-19
hit_count: 0
source: create-p:workbench-repair-03b-source-version-split
severity: P1
---

经验：`AiReplyContext.profileText` **不只是专家画像**——它是「画像 + 命中的 AI 训练知识」的拼接串：

```
AiReplyContextService.kt:40-43
profileText = contextBuilder.appendKnowledgeToProfile(
    contextBuilder.buildExpertProfile(contact, profile),
    trainingKnowledge)                      // = aiTrainingQaService.buildKnowledgeContext(inboundText)
```

后果：任何把 `sha256(profileText)` 拌进身份哈希的地方，**运营在「AI 训练」页编辑一条命中本信的知识就会让身份变化**。工作台的 `sourceVersion()`（`TrustReplyWorkbenchService.kt:1441-1465`）正是这样，而 `sourceVersion` 又进 `requestKey()`（:1875-1888）→ 全量重置。这比「专家又来一封信」频繁得多，是该场景最主要的触发源。

放大器：`buildKnowledgeContext`（`AiTrainingQaService.kt:85-110`）按关键词打分后 `sortedWith(score desc, id asc).take(MAX_KNOWLEDGE_ROWS = 6)`。新增一条高分知识会把尾部那条**挤掉**，即使那条没被碰过。

同族的第二个滑动窗口：`buildMailHistory`（`AiReplyContextBuilder.kt:65`）`takeLast(8)`。给该专家新增任意一条 `mail_record`（IMAP 收信、或运营在别的 tab 发一封材料提醒）都会移动窗口。

好消息（实测）：`AiReplyContextBuilder.kt` 与 `AiReplyContextService.kt` 两个文件 `grep "now()\|Instant\|LocalDateTime.now"` **零命中**，不含 wall clock，放着不动是稳定的，不会自己烂掉。

分层依据（做失效粒度时按此归位）：
- **identity**（进 requestKey）：sourceType/sourceId/contactId/messageId/subject/senderAccountCode/`sha256(inboundText)`
- **evidence**（进 per-request 版本，且**只**对 `requiresResearchContext == true` 的条目）：`sha256(expertProfileText)` + `researchProfileSufficient`。依据 [[K-research-fit-dual-evidence]]，画像是研究匹配的证据侧；硬门禁在 `AiReplyIntentCatalog.kt:567` / `:705`，不得放松。`requiresProfile = true` 的意图**全仓只有 1 个**（`AiReplyIntentCatalog.kt:128`）。注意 `isResearchSufficient`（`AiReplyContextService.kt:83-89`）只判「任一字段非空」，是粗布尔——画像新增论文后布尔不变，故须**另拌内容哈希**。
- **context**（只提示、不进任何哈希）：`sha256(trainingKnowledgeText)` + `sha256(mailHistory)`。依据 [[K-ai-reply-history-continuity-not-authority]]：prompt 里明写 history 非事实权威；训练知识也只进 prompt、不进 `mail_record_qa_rule` 审计（[[K-ai-reply-prompt-vs-send-rule-ids]]）。

降级的前置条件（缺一不可，否则等于把风险转嫁给运营）：提示必须挂在**条目上**而不是全局 banner；必须提供「一键重跑受影响条目」。

改动面提示：`aiReplyContextService.build` 有 **5 个调用点**（AiTrainingController:220、TrustReplyWorkbenchService:1408、UnmatchedInboundMailController:347、PendingMailOperationService:535、GroundedAutoReplyDecisionService:216）。给 `AiReplyContext` **加带默认值的字段**即可让这 5 处全部不用改。
