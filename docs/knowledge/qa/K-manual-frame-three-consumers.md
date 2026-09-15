---
id: K-manual-frame-three-consumers
domain: qa
created: 2026-09-02  # 2026-09-02 重新校验：整封式 RAG 落地后新增第 4 个消费者
last_used: 2026-09-02
hit_count: 19
source: create-p:ai-reply-04-grounded-trust-content-plan
severity: P1
---
经验：QA 可信工作台重构后，旧 `sendManualComposedReply`、`LlmStitchService`、前端 deterministic preview 已删除；`ReplySnippetService.resolveManualFrame()` 的现存消费者集中在 LLM 模块：(1) `AiReplyPointByPointComposer` 为 Grounded LLM 与 Grounded fallback 组装 frame；(2) `AiReplyDraftService.buildMatchedUserContent()` 为兼容 matched prompt 提供 frame；(3) `AiReplyDraftService.buildFrameGuidanceText()/composeFreeFormDeterministicDraft()` 为 FREE_FORM prompt/fallback 提供 frame。
（4）**2026-09-02 起**：整封式 RAG 链路的 `rag/service/RagLetterComposer` 也消费 frame——它把「尊语 + 开场白 + 模型正文 + 致谢语 + 结束语」拼成最终正文，且生成提示词第 12 条明确禁止模型自写称呼与署名（`docs/plans/2026-09-02/03-rag-letter-composer.md` I-18）。新链路只调 snippet 解析，不改 `ReplySnippetService` 本身。
正确做法：修改 Grounded 的称呼/问候/结尾策略时只改 `AiReplyPointByPointComposer` 这一消费边界，不改 snippet 数据和 FREE_FORM/matched 消费者；修改全局 snippet 语义前必须重新 grep 全部 `resolveManualFrame/resolveAck` 调用点。前端已不自行拼 frame。
关联：K-preview-mirrors-pipeline、K-gap-items-compose-only。
