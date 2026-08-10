# 组合模板邮件 HTML 段落格式修复计划

## 需求描述

- 可观察结果：通过组合模板发送的邮件（含 `MATERIAL_REMINDER`）在 Gmail 等优先展示 HTML 的客户端中保留空行段落与单换行；`text/plain` 备用部分仍保持原始纯文本。
- 不得改变：模板变量取值、个性化门禁、收件人、主题、Message-ID、`In-Reply-To`/`References`、退订头、发送状态与会话状态流转。
- 范围外：不修改模板数据库内容，不修改 `mail_record.body` 的既有存储选择，不改前端邮件正文展示，不重构 `MailVariableService.renderHtmlForContact()` 的富文本用途。

## 关键不变量

### Invariant I-1: 纯文本模板单源
- Rule: `ComposeTemplateRenderResult.body` 是已完成变量替换的纯文本；SMTP 的 `text/plain` 必须逐字使用它，`text/html` 必须由 `MailContentService.plainTextToHtml()` 对它做一次安全转换，禁止把纯文本直接标记为 HTML。
- Applies to: `ManualExpertMailService.composeComposeTemplate()` 的所有组合模板发送。
- Violation consequence: Gmail/Outlook 优先选中 HTML 部分后折叠换行；未经转义的 `<`、`&` 还可能改变 HTML 含义。
- 来源: K-plaintext-reply-client-reflow

### Invariant I-2: 两个 MIME 部分语义一致
- Rule: 空行转换为相邻 `<p>...</p>`，单换行转换为 `<br>`，HTML 特殊字符被转义；转换不得修改纯文本 fallback。
- Applies to: `ManualExpertMailService` → `SmtpMailDeliveryService` 的 `ComposedMail(html=true, text=...)` 接口。
- Violation consequence: HTML 与纯文本客户端看到不同正文，或 HTML 内容不安全。
- 来源: K-plaintext-reply-client-reflow

### Invariant I-3: 提醒发送入口与线程语义不变
- Rule: 手动单发、批量 API 和定时批量提醒继续汇入 `ManualExpertMailService.sendManualMail()`；`MATERIAL_REMINDER` 的主题与线程头逻辑不得变化。
- Applies to: `ExpertContactManagementController`、`ManualInitialOutreachService.runMaterialReminderBatch()`、`ManualExpertMailService`。
- Violation consequence: 不同入口格式不一致，或提醒脱离原邮件线程。
- 来源: K-material-reminder-single-compose-seam

## 现状审计

### 组合模板正文与 SMTP MIME
- 模板契约：`MailComposeTemplateService.renderTemplate()` 在 `MailComposeTemplateService.kt:170-184` 先替换变量，再以 `\n\n` 连接文本块，返回纯文本 `body`。
- HTML 转换器：`MailContentService.plainTextToHtml()` 在 `MailContentService.kt:7-15` 已实现段落、`<br>` 与 HTML 转义。
- SMTP 读取路径：`SmtpMailDeliveryService.kt:35-45` 在 `html=true` 时写 `multipart/alternative`；`mail.text` 写入 `text/plain`，`mail.body` 原样写入 `text/html`。
- 缺陷路径：`ManualExpertMailService.kt:217-229` 在线上存在 `MailVariableService` 时调用 `renderHtmlForContact(rendered.body, ...)`。该方法只做变量替换，不把纯文本换行变成标签；而变量已在 `render()` 中替换完，因此线上 HTML 部分与纯文本完全相同。
- 回归来源：提交 `07a77f3` 将原来的无条件 `plainTextToHtml(rendered.body)` 改成了生产/测试分支。旧 `ManualExpertMailServiceTest` 使用默认 `mailVariableService=null`，只覆盖正确的测试 fallback；`ManualExpertMailServiceGateTest` 反而断言生产 HTML 正文以普通文本开头，固化了错误行为。

### `mail_record`
- Schema/mapping：`V1__create_business_tables.sql:97-115`；`body LONGTEXT`、可空，无格式标识或内容约束；映射为 `MailRecord.body: String?`。
- Write paths（全量 grep）：`ManualOutreachTxHelper.kt:50/102`（首次触达成功/失败）、`MeetingScheduleService.kt:135`（会议邮件）、`AutoMailReplyService.kt:264/578/768/967`（入站、自动回复及其他出站）、`ManualExpertMailService.kt:68`（组合模板）、`ManualReplySendAttemptService.kt:248/327`（人工富文本发送尝试落库）。本计划只改变 `ManualExpertMailService` 传入的 HTML 字符串内容，不改变字段或其他写路径。
- Read paths：`ExpertContactManagementService.getContactDetail()` 读取完整会话邮件；`MailboxService` 读取列表/详情与预览；`MailMonitoringService` 读取监控正文；`PendingMailOperationService`、AI 上下文/训练服务读取会话记录。前端 `translatableBody()` 对正文转义后以 `.pre` 展示。本计划保持既有“组合模板路径把 SMTP HTML body 写入 `mail_record.body`”行为，不扩展前端修复。
- Interaction points：模板纯文本 → HTML 转换 → SMTP `text/html`；同一 `ComposedMail.body` → `mail_record.body`。只允许前者恢复正确标签，后者的写入位置与类型不变。

### 发送入口
- 手动单发：`POST /api/expert-contacts/{contactId}/manual-mail` → `ManualExpertMailService.sendManualMail()`。
- 批量 API：`POST /api/expert-contacts/batch-mail` → `sendBatchMail()` → `sendManualMail()`。
- 定时/手动批任务提醒：`ManualInitialOutreachService.kt:300-305` → `sendManualMail()`。
- 三者最终只经过 `composeComposeTemplate()` 一个组装点（来源: K-material-reminder-single-compose-seam）。

## 实现方案

### Task 1：恢复统一的纯文本转 HTML 路径
- 遵循：I-1、I-2、I-3。
- 修改：`src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt`。
- 将 `composeComposeTemplate()` 中按 `mailVariableService` 分支产生 HTML 的代码替换为：

```kotlin
val html = mailContentService.plainTextToHtml(rendered.body)
```

- 保留上游 `mailComposeTemplateService.render(templateId, variables, seed)`，所以变量仍只由最终账号/联系人数据渲染；保留 `text = rendered.body`、`html = true` 及全部线程头逻辑。

### Task 2：让生产装配测试覆盖段落转换
- 遵循：I-1、I-2、I-3。
- 修改：`src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt`。
- 调整使用真实 `MailVariableService` 的发送测试，使渲染正文包含空行及需要转义的字符；断言 `text` 保留原文，`body` 包含准确的 `<p>`/`<br>` 和实体转义，不再接受以裸文本开头的 HTML。
- 保留现有线程、个性化门禁和发送状态测试；现有 `ManualExpertMailServiceTest` 继续作为无变量服务兼容分支回归。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 对已渲染纯文本统一调用 `plainTextToHtml()` |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt` | 覆盖生产装配的 HTML 段落与转义契约 |

## 验收标准

- I-1：生产装配测试捕获 `ComposedMail`，断言 `html=true`、`text` 等于模板渲染纯文本、`body` 不等于裸纯文本。
- I-2：断言 `First\n\nSecond\nThird & <Lab>` 精确转换为 `<p>First</p><p>Second<br>Third &amp; &lt;Lab&gt;</p>`；`SmtpMailDeliveryServiceTest` 继续通过，证明两个 MIME part 顺序和 Content-Type 未变。
- I-3：`ManualExpertMailServiceTest` 与 `ManualExpertMailServiceGateTest` 全部通过；现有 `MATERIAL_REMINDER` 线程头测试继续断言 `In-Reply-To`、`References`、`Re:` 主题不变。
- 运行：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=ManualExpertMailServiceTest,ManualExpertMailServiceGateTest,MailContentServiceTest,SmtpMailDeliveryServiceTest test`。
- 基线证据：2026-08-10 上述命令退出码为 0，说明现有测试未发现线上分支格式缺陷；新增断言必须能在修复前失败、修复后通过。

## 人工验收清单

### A-1: 定时/手动批任务提醒的 Gmail 格式
- 前置条件: 存在一名可发送提醒的联系人；模板正文至少含两个空行分隔段落和一个单换行；使用测试收件箱。
- 操作步骤: 1. 从批任务控制台执行一次 `MATERIAL_REMINDER`；2. 在 Gmail 打开邮件；3. 查看“显示原始邮件”。
- 预期结果: Gmail 正文按模板显示多个段落，签名内部单换行保留；原始邮件是 `multipart/alternative`，HTML part 含 `<p>`/`<br>`，plain part 仍含原始换行。
- 覆盖: I-1、I-2、I-3。

### A-2: 专家详情手动单发格式一致
- 前置条件: 同一组合模板可在专家详情中选择。
- 操作步骤: 1. 在专家详情选择该模板并发送到测试收件箱；2. 在 Gmail 打开邮件。
- 预期结果: 段落与 A-1 一致，不出现正文挤成一段；变量值正常显示，不出现 `${...}`。
- 覆盖: I-1、I-2、I-3。

### A-3: 提醒线程回归
- 前置条件: 联系人存在一封带有效 Message-ID 的入站邮件。
- 操作步骤: 1. 发送 `MATERIAL_REMINDER`；2. 查看 Gmail 会话与原始邮件头。
- 预期结果: 邮件进入原会话；主题为单个 `Re: <原主题>`；`In-Reply-To` 等于最新入站 Message-ID；`References` 仍包含该 Message-ID。
- 覆盖: I-3、需求描述“不改变线程语义”。

### A-4: 纯文本客户端回归
- 前置条件: 使用仅展示 `text/plain` 的邮件客户端或从原始邮件单独提取 plain part。
- 操作步骤: 1. 打开与 A-1 同一封邮件的 plain part；2. 对照模板正文。
- 预期结果: 文案、空行和单换行与模板渲染结果逐字一致，无 HTML 标签。
- 覆盖: I-1、I-2、需求描述“不改变纯文本 fallback”。
