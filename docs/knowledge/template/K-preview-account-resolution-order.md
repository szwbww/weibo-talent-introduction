---
id: K-preview-account-resolution-order
domain: template
created: 2026-08-14
last_used: 2026-08-14
hit_count: 0
source: create-p:expert-detail-head
severity: P2
---

经验：`preview-draft` 的账号解析必须是「显式请求值 > contact 已绑定账号 > null」三级，且刻意**不**做
enabled 门禁。三条都有反例。

- **优先级不能倒**：模板编辑器抽屉（`app.js:8347 renderServerComposeTemplatePreview`）会显式传
  `senderAccountCode`（来自 `#previewComposeAccountInput`）。若让 contact 绑定优先，抽屉的账号选择器直接失效。
- **必须有绑定兜底**：专家详情的邮件预览（`app.js:8086 renderExpertMailPreview`）不传账号码
  —— 因为前端"当前选中值"不是权威（见 [[K-manual-send-explicit-account-must-match-binding]]）。
  没有兜底则 `MailVariableService.buildVariables` 的 5 个 sender 变量（`MailVariableService.kt:124-129`）
  全渲染成空串并进 `fallbackKeys`，签名区空白。
- **用 `getAccount` 不是 `getEnabledAccount`**：`mail_sender_account.enabled=false` 的现行语义是
  "禁止自动外发"而非"账号不可用"（[[K-sender-account-enabled-scope]]）。绑定到禁用账号的专家，
  预览必须仍能渲染签名 —— 否则预览与发送反而更不同源。
  发送侧 `SenderAccountBindingService.resolveForSend` `:37` 同样先 `getAccount` 再 `requireAvailable`。

**传 contactId 的副作用**：`resolvePreviewContact()` `MailComposeTemplateService.kt:279-284` 在
`contactId != null` 时**提前 return 库中 contact，忽略 orcidId / expertEmail**。所以
① 收件人会从合成的 `preview@local` 变成真实邮箱（通常是想要的）；
② 传了不存在的 contactId 会让 `findById(...).orElse(null)` 返回 null → `previewDraft` 走
`contact == null` 分支（`:229-239`）**返回未渲染的原始模板文本**，面板满屏 `${}` 且不报错，属静默降级。
因此 contactId 只能从确有该条目的缓存里取，取不到就传 null。

关联：[[K-compose-template-preview-endpoint-split]]、[[K-preview-draft-raw-before-render]]
