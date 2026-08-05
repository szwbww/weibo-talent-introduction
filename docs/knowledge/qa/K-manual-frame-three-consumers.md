---
id: K-manual-frame-three-consumers
domain: qa
created: 2026-07-19
last_used: 2026-08-05
hit_count: 18
source: create-p:ai-reply-04-grounded-trust-content-plan
severity: P1
---
经验：QA 可信工作台重构后，旧 `sendManualComposedReply`、`LlmStitchService`、前端 deterministic preview 已删除；`ReplySnippetService.resolveManualFrame()` 的现存消费者集中在 LLM 模块：(1) `AiReplyPointByPointComposer` 为 Grounded LLM 与 Grounded fallback 组装 frame；(2) `AiReplyDraftService.buildMatchedUserContent()` 为兼容 matched prompt 提供 frame；(3) `AiReplyDraftService.buildFrameGuidanceText()/composeFreeFormDeterministicDraft()` 为 FREE_FORM prompt/fallback 提供 frame。
正确做法：修改 Grounded 的称呼/问候/结尾策略时只改 `AiReplyPointByPointComposer` 这一消费边界，不改 snippet 数据和 FREE_FORM/matched 消费者；修改全局 snippet 语义前必须重新 grep 全部 `resolveManualFrame/resolveAck` 调用点。前端已不自行拼 frame。
关联：K-preview-mirrors-pipeline、K-gap-items-compose-only。
