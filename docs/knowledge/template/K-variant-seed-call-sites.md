---
id: K-variant-seed-call-sites
domain: template
created: 2026-09-23
last_used: 2026-09-23
hit_count: 10
source: create-p:variant-pool-2-seed-rollout
---

模板随机规则变更必须分别审计真实生成和预览，不能仅核对首封邮件入口。

2026-09-23 代码现状（本次仅计划、尚未实施）：
- `ContentVariantService.resolveBody:27` 使用 `floorMod(seed + ownerId, pool.size)`；QA 与模板回复片段当前共享这一确定性算法。等长池的索引为固定偏移，不是各引用独立随机。
- 正式模板调用：IntroductionMailComposer:22/24、ManualExpertMailService:227、MeetingInvitationMailComposer:14、AutoMailReplyService:1156、MeetingScheduleService:119、MeetingConfirmationService:247。
- AutoReplyPreviewService:93 也调用 renderByCode；模板编辑 preview-draft 和专家详情 app.js:11181 还有 variantIndex，后者来自 ORCID hash。
- `render/renderByCode` 的缺省 variantSeed 使漏传种子不会编译失败。旧规则采用 seed 时应统一核对来源；若需求改为每次生成随机，则不能继续要求所有新路径使用固定专家 seed。QA 的确定性规则必须单独保护。

拟议的新随机入口与边界见 `docs/plans/2026-09-23/01-reply-snippet-variants-backend.md`；该计划不等于当前已实现。未来复核应重新 grep `mailComposeTemplateService.render`、`contentVariantService.resolveBody` 和前端 `variantIndex`，不要依赖旧行号。
