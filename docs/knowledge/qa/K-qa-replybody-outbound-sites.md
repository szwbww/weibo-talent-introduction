---
id: K-qa-replybody-outbound-sites
domain: qa
created: 2026-07-09
last_used: 2026-07-17
hit_count: 7
source: create-p:cv-3-reply-paths
---

QA 规则 `replyBody` 的**出站组装点全集**（任何"改变规则正文出站形态"的需求——变体、签名、免责声明等——必须逐点覆盖）：

1. `QaMatchService:67` — `QaReplyComposer.compose(...)`，自动路径唯一组装点；消费方 AutoMailReplyService（实发）+ AutoReplyPreviewService（预览，须镜像同参）
2. `PendingMailOperationService:336` — `composeInOperatorOrder(...)`，人工 rich-reply 重建；同文件 sendQaReply 单规则人工发送
3. `LlmStitchService:61` — AI 拼接草稿（2026-07-09 决策：恒用主体，不参与变体）
4. `MailComposeTemplateService.resolveBlocks` — 模板 QA 内容块

仅展示不出站（不需处理）：QaRuleManagementService/Controller CRUD 回显、UnmatchedInboundMailController 详情。

`QaReplyComposer` 本体是纯函数拼接器——正文改写应在调用方 `rule.copy(replyBody = ...)` 完成，composer 保持零改动（qaRuleIds 顺序契约依赖它）。

关联：[[K-composed-reply-order-contract]]、[[K-preview-mirrors-pipeline]]、[[K-variant-seed-call-sites]]、[[K-pending-qa-reply-rule-source]]
