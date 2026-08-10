---
id: K-compose-template-html-after-render
domain: mail
created: 2026-08-10
last_used: 2026-08-10
hit_count: 0
source: create-p:compose-template-html-formatting
---

`MailComposeTemplateService.render()` 返回的 `ComposeTemplateRenderResult.body` 已完成变量替换，但仍是纯文本。`ManualExpertMailService.composeComposeTemplate()` 必须在该结果上调用 `MailContentService.plainTextToHtml()`，并把原结果放入 `ComposedMail.text`；`MailVariableService.renderHtmlForContact()` 只适用于输入本来就是 HTML 的模板，不能把纯文本换行转换成 HTML 标签。

测试陷阱：`ManualExpertMailService` 的旧单测默认 `mailVariableService=null`，只覆盖测试 fallback；涉及生产分支时必须使用非空/真实 `MailVariableService` 装配，否则会漏掉线上独有分支。
