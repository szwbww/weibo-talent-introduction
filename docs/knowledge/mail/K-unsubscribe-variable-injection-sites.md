---
id: K-unsubscribe-variable-injection-sites
domain: mail
created: 2026-08-11
last_used: 2026-08-12
hit_count: 1
source: create-p:unsubscribe-01-body-link
severity: P1
---

经验：`unsubscribeUrl` 只有一个产出点，但**不是所有模板渲染路径都注入它**；缺失时的失败形态是**字面量泄漏到外发正文**，不是空串。

唯一产出点：`MailVariableService.buildVariables()`（`MailVariableService.kt:117-159`，`:155-157` 的 `unsubscribeVars`）。

**能拿到 `unsubscribeUrl` 的渲染路径：**

1. `IntroductionMailComposer.compose()` `:18` → `:51-52`（INTRODUCTION，`unsubscribeEmail` 默认取 `expert?.email`）
2. `ManualExpertMailService.composeComposeTemplate()` `:197-199`（COMPOSE_TEMPLATE，含 MATERIAL_REMINDER）
3. `MailVariableService.renderContact()` `:187` / `renderHtmlForContact()` `:202`（人工富文本、AI 草稿）
4. `AutoMailReplyService.kt:562-565` QA/AI 自动回复（经 `renderForContact` 间接命中 #3）

**拿不到的路径（全部是会议邮件族）：**

1. `AutoMailReplyService.sendMeetingInvitation()` `:953-956` → `mailTemplateVariables()` `:990-998`，仅 6 个 sender 变量
2. `AutoReplyPreviewService` `:93-96` → `mailTemplateVariables()` `:205-213`，仅 6 个
3. `MeetingInvitationMailComposer.compose()` `:16-20`，仅 `senderDisplayName`
4. `MeetingScheduleService` MEETING_CONFIRMATION `:118-131`，会议变量 + 6 个 sender 变量
5. `ManualExpertMailService` 测试兜底分支 `:203-205`（`variableService == null` 时）

**失败形态**：`MailComposeTemplateService.renderText()` `:594-596` 是
`variables.entries.fold(withFallback) { rendered, (key, value) -> rendered.replace("\${$key}", value) }`
—— fold 只替换 map 中**存在**的 key。map 里没有 `unsubscribeUrl` 时，`${unsubscribeUrl}` 六个字符**原样外发**。

**How to apply**：往任何模板正文加 `${unsubscribeUrl}`（或任何变量）之前，先确认该模板的**全部**渲染入口都在"能拿到"列表里。会议模板要加，必须先补上面 4+1 个注入点。反之，`${key|fallback}` 形态由 `FALLBACK_PLACEHOLDER_REGEX`（`:600`）先处理，缺 key 时会落到 fallback，不会泄漏字面量 —— 但空 fallback 会留下悬空文案。

顺带：个性化门禁不会救你。`PersonalizationGateService.evaluate()` `:44-53` 在 `requiredKeys` 为空时直接放行，而 `required_keys`（`V84` 加列）当前所有模板都未填值。

关联：[[K-renderText-all-callers]]（5 个 variables 注入入口的全集）、[[K-manual-expert-mail-sender-only-variables]]、[[K-preview-mirrors-pipeline]]（预览与发送须同源，改 `AutoMailReplyService` 的注入必须同步改 `AutoReplyPreviewService`）。
