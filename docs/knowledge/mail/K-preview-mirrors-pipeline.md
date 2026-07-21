---
id: K-preview-mirrors-pipeline
domain: mail
created: 2026-06-28
last_used: 2026-07-21
hit_count: 26
source: create-p:auto-reply-dry-run-preview
severity: P1
---
经验：任何「预测自动管线会怎么处理」的 dry-run/预览功能，必须复用与真实管线（`AutoMailReplyService.processSingle`）相同的注入 Bean（`InboundIntentClassifier`/`QaMatchService`/`MailTemplateService`/`MailBodyCleaner`）并按相同分支顺序复现，否则预览与实际外发漂移，运营据错误预览调策略。
正确做法：(1) 同源同序复现 `classify→effectiveIntent→when(autoAction)→QA match→gap/handoff` 链；展示 `match.replyBody` 不重排（见 K-composed-reply-order-contract）。(2) 预览是「假如开启自动回复」的反事实：不因 `autoReplyEnabled=false`/`MANUAL_HANDOFF`/退订等运行期闸门隐藏内容，只把闸门作为信息标记。(3) 无法等价复现的部分（如 `effectiveIntent` 的附件意图覆盖）必须显式标注偏差，禁止静默忽略。(4) 预览服务不加 `@Transactional`、无 `save`/`send`，纯只读。
反例：预览自行用 `composeOrder/id` 重排正文、或在 `autoReplyEnabled=false` 时短路隐藏 QA 内容，导致人工队列里的记录（本就关自动回复）预览失效。
关联：K-composed-reply-order-contract、K-overview-gap-supersede、K-plaintext-reply-client-reflow。
