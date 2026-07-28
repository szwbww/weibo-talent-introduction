---
id: K-ai-generate-single-freeform-seam
domain: qa
created: 2026-07-19
last_used: 2026-07-27
hit_count: 29
source: create-p:ai-reply-04-grounded-trust-content-plan
last_source: fix-v:ai-reply-streaming-dual-ttl-cancel-plan:blocked-after-fix-1
---
经验：`AiReplyDraftService.generate()` 当前有三个生产入口：`UnmatchedInboundMailController.aiReplyTurn`（人工工作台）、`AiTrainingController.simulate`（训练模拟）和 `GroundedAutoReplyDecisionService.decide`（自动实发/预览共享 decision）。任何生成 prompt、结构协议、claim/action gate 改动必须收口在 service 内，不能在 controller 或自动服务各复制一份。
正确做法：FREE_FORM 只改 `buildFreeFormMessages`；QA_GROUNDED 同时覆盖首轮、operator continuation 和动作纠正重试的 build→materialize→validate 链；deterministic fallback 是独立文本源。三入口共享 `AiReplyDraftResult`，自动入口另有 fail-closed 发送门禁。
