---
id: K-manual-send-options-sources
domain: mail
created: 2026-07-04
last_used: 2026-07-09
hit_count: 4
source: create-p:mail-compose-template
---
经验：手动发送邮件选项（`ManualExpertMailService.listSendOptions()`）的数据来源有三类，混在一个扁平列表中返回，前端用 `optionType` 字段区分但未向运营显示分组：
(1) TEMPLATE 系统模板（INTRODUCTION/MEETING_INVITATION/MATERIAL_REMINDER）→ `mail_template` 表 → 通过 `MailTemplateService.render()` 做变量替换。
(2) QA 规则（已在 compose-template 方案中移除）→ `qa_rule` 表 → `findAllEnabledOrdered()`。
(3) COMPOSE_TEMPLATE 邮件模板（新增）→ `mail_compose_template` 表 → 引用 QA 规则/回复片段/自定义文本，实时渲染。
前端 `state.mailSendOptions` 有客户端缓存（`loadMailSendOptions()` 首次加载后不刷新），修改 QA 规则或模板后须清缓存才能同步。
关联：K-view-registration-triad（Tab 注册）、K-composed-reply-order-contract（顺序契约）。
