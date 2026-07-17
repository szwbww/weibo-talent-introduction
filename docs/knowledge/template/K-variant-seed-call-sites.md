---
id: K-variant-seed-call-sites
domain: template
created: 2026-07-08
last_used: 2026-07-17
hit_count: 9
source: create-p:variant-pool-2-seed-rollout
---

`MailComposeTemplateService.render/renderByCode` 带 `variantSeed: Int = 0` 缺省参——**缺省参使"漏传 seed"编译期不可见**，任何变体相关需求必须逐点核对全部调用点：

1. `IntroductionMailComposer.compose` — 传 `expert.orcidId.hashCode()`（首个正确接种子的路径）
2. `ManualExpertMailService.composeComposeTemplate` (:162)
3. `AutoMailReplyService.sendMeetingInvitation` (:978，调用方 :470 有 effectiveContact)
4. `MeetingInvitationMailComposer.compose` (:14)
5. `MeetingScheduleService` 确认信 (:109)
6. `AutoReplyPreviewService` (:86) — 预览镜像，seed 必须与 #3 同源同值（K-preview-mirrors-pipeline）

2026-07-08 变体池计划（variant-pool-2-seed-rollout）将 #2-#6 统一为 `MailComposeTemplateService.variantSeedFor(orcidId, email)`。此后新增渲染调用点必须同样使用该 helper，禁止手写 hashCode 派生。

关联：[[K-renderText-all-callers]]、[[K-preview-mirrors-pipeline]]、[[K-positive-hash-index]]
