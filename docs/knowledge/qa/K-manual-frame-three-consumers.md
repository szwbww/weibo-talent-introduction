---
id: K-manual-frame-three-consumers
domain: qa
created: 2026-06-28
last_used: 2026-07-12
hit_count: 13
source: create-p:reply-snippet-backend
severity: P1
---
经验：人工拼装回复的「正文 frame（问候/结束语/未来的尊语/致谢）」有三个必须同源同序的消费者，改其一必须同改其三，否则预览/外发/润色漂移：(1) 外发 `PendingMailOperationService.sendManualComposedReply`→`QaReplyComposer.composeInOperatorOrder`；(2) 润色确定性回退 `LlmStitchService.composeDeterministic`（同样调 `composeInOperatorOrder`）；(3) 前端预览 `app.js:buildDeterministicComposedPreview`。
关键区分：`QaReplyComposer.compose`（按 categoryComposeOrder 的自动序）与 `composeInOperatorOrder`（运营序）是两条独立链；二者当前共享 `GREETING/CLOSING` 常量。仅人工路径的 frame 改动必须只改 `composeInOperatorOrder` 调用方，不得动 `compose`（否则自动回复管线被波及）。
另：`LlmStitchService.buildRuleSegments`（喂 LLM prompt 的段落）有意不含 frame，与 `composeDeterministic` 不同，勿混。
关联：K-composed-reply-order-contract、K-preview-mirrors-pipeline、K-gap-items-compose-only。
