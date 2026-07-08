---
id: K-qa-outbound-render-seams
domain: qa
created: 2026-07-08
last_used: 2026-07-08
hit_count: 2
source: create-p:qa-reply-personalization-backend
---
经验：QA 回复的外发 seam 有且仅有三个，任何"对 QA 外发正文统一加能力"（变量渲染、脱敏、签名等）的需求必须三点全覆盖，漏一个即该路径行为漂移：
1. `AutoMailReplyService`（≈592 行，自动 QA 回复，单规则与 compose 聚合共用此 seam）；
2. `PendingMailOperationService.sendQaReply()`（83 行，人工单规则 QA 回复）；
3. `PendingMailOperationService.sendManualComposedReply()`（303 行，组装台运营序回复）。
关键语义：`${...}` 变量替换的唯一实现是 `MailComposeTemplateService.renderText()`（未知 key 原样保留 → 拼错的占位符会字面外发）；变量 map 单源为 `MailVariableService.buildVariables`（Plan 2026-07-08 建立），变量键集必须恒定、档案缺失降级空串，否则 ES 抖动时 `${key}` 字面量外发。
另：`ExpertContact` 表只有 orcidId/expertEmail/expertName/country，专家画像变量（researchFields/institution/familyNames…）必须回 ES `searchByOrcidId` 取，这是数据所有权分界（ES 管画像、MySQL 管状态），不要冗余进 contact 表。
关联：K-plaintext-reply-client-reflow（同三点的 HTML 化）、K-renderText-all-callers、K-composed-reply-order-contract。
