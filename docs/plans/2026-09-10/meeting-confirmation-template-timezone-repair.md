# 专家会议确认：复用邀请模板与时区点击修复

## 需求描述

收发件箱的“专家会议确认”弹窗固定复用已启用的 `MEETING_INVITATION` 邮件模板。模板正文必须按通用模板链路渲染，保留其回复片段、自定义文本以及既有 `${...}` 变量；本流程只额外提供 `${meeting_time}` 与 `${zoom_url}` 两个值。操作者手工在现有邀请模板中配置这两个变量。

弹窗移除邮件模板选择、模板正文/变量展开区、专家称呼和发件签名输入，只保留时区、开始/结束时间、Zoom 链接、正文预览和 ICS 操作。鼠标点击时区候选项必须能选中候选项。

必须保留：

- 人工回复的原线程主题不被模板主题覆盖；确认操作仍只写当前草稿和编辑器。
- 已有邀请模板中的 `${expertFamilyName|Colleague}`、回复片段和自定义文本继续由通用链路处理。
- 会议时间、正文预览、ICS、附件 SHA-256、发送前重建与正文一致性校验保持同源。
- 时区搜索、键盘 `ArrowUp`/`ArrowDown`/`Enter`/`Esc`、日期变更时重载偏移的既有行为保持。

范围外：修改邮件模板管理页、向全局“插入变量”菜单注册会议变量、修改通用 `${...}` 解析器、改动数据库表或既有 Flyway 迁移、重新设计弹窗 CSS、修改自动回复的会议邀请流程。

## 关键不变量

### Invariant I-1：会议正文只能走通用邀请模板链路

- Rule：`MeetingConfirmationService` 以固定 `templateCode = "MEETING_INVITATION"` 调用 `MailComposeTemplateService.renderByCode`，只采用其 `body`；不得再按 `MANUAL_MEETING_CONFIRMATION` 筛选模板、拼接 `CUSTOM_TEXT`，或自建 `{{...}}` 替换器。
- Applies to：`options`、`preview`、`validateAndBuild`、发送前 `validateAndBuild` 重建。
- Violation consequence：回复片段和既有模板变量丢失，或再次出现模板名乱码/专用模板不可用提示。
- 来源：用户要求；`MeetingConfirmationService.kt:212-224` 当前只读取 `CUSTOM_TEXT`；`MailComposeTemplateService.kt:116-131,459-552` 已完整解析模板内容块。

### Invariant I-2：新增变量仅是会议渲染调用的两个值

- Rule：传给 `renderByCode` 的变量 map 先由 `MailVariableService.resolveExpertProfileFor` 与 `buildVariables(account, expert, contact.expertEmail, false, contact)` 构成，再只覆盖加入 `meeting_time`、`zoom_url`。模板配置采用现有语法 `${meeting_time}`、`${zoom_url}`；不得引入或保留 `{{meeting_time}}`、`{{zoom_url}}`。
- Applies to：会议预览与发送前重建。
- Violation consequence：现有专家/发件人变量无法渲染，或会议变量泄漏为未解析文本。
- 来源：用户要求；`MailVariableService.kt:119-163,280-297`；`MailComposeTemplateService.kt:611-619` 对传入 map 的任意 key 替换 `${key}`。

### Invariant I-3：会议输入与发送安全边界不放宽

- Rule：新弹窗请求只发送时区、起止本地时间、Zoom 链接和 `generatedAt`；旧 DTO 中的模板正文、专用模板 ID、专家称呼、签名字段仅保留为带默认值的兼容字段，服务端完全忽略。`PendingMailOperationService` 继续在 SMTP 前调用 `validateAndBuild`，校验预览附件 SHA-256，并要求最终人工正文同时含重建后的完整文本和 HTML 纯文本。
- Applies to：`MeetingInput` 反序列化、preview、manual-rich-reply 发送。
- Violation consequence：旧草稿/API 请求被不必要地反序列化拒绝，或会议正文与 ICS 附件失配后仍发送。
- 来源：`MeetingConfirmationModels.kt:31-49`；`PendingMailOperationService.kt:501-545`。

### Invariant I-4：时区只能由明确候选选择改变

- Rule：点击候选项的选择处理必须先于搜索框 `blur` 关闭下拉列表执行；成功后仍调用同一 `selectZoneById`/`selectZone` 路径。搜索输入、失焦恢复标签、键盘选择和服务端时区目录不改。
- Applies to：`meetingZoneOptions` 鼠标事件与 `meetingZoneSearch` blur 事件。
- Violation consequence：鼠标点击 Istanbul 等候选项时列表先关闭，selected zone 保持旧值。
- 来源：`meeting-confirmation.js:1154-1157` 同步关闭列表，`1217-1225` 才监听 `click`；用户线上复现截图。

### Invariant I-5：弹窗只移除已废弃的配置区域

- Rule：弹窗 DOM 移除 `meetingTemplate`、`templateDetails`、`templateText`、`resetTemplate`、`meetingName`、`meetingSignature` 及其事件、字段校验、草稿读写；保留 `meetingZone*`、日期/时间、`meetingUrl`、插入模式、预览和 ICS 节点。没有可见“模板不可用”状态依赖专用模板目录。
- Applies to：`DIALOG_HTML`、字段装载、表单收集、状态控制、测试 DOM 契约。
- Violation consequence：用户仍会看到已删除专用模板的 UI，或旧节点引用导致弹窗运行时异常。
- 来源：用户截图与要求；`meeting-confirmation.js:1528-1587`；`meetingConfirmationStyle.test.js:104-114`。

## 样式契约

### S-1：只删 DOM，不改 CSS

- 复用：`.meeting-dialog`、`.meeting-grid`、`.meeting-form`、`.meeting-zone-field`、`.meeting-fields`、`.meeting-clock`、`.meeting-preview`、`.meeting-file`、`.meeting-bottom` 的现有规则，见 `meeting-confirmation.css:6-58,76-103`。
- 新增：无。`meeting-confirmation.css` 不在本计划变更清单中。
- DOM 结构：左栏删除以下三段，其他层级原位保留：

```html
<label>邮件模板<select id="meetingTemplate"></select></label>
<details id="templateDetails" class="meeting-template">…</details>
<label>专家称呼 <input id="meetingName" …></label>
<label>发件签名<textarea id="meetingSignature" …></textarea></label>
```

删除后，`meetingLoadStatus` 紧接 `meeting-zone-field`；`meeting-zone-field` 后依次为两组 `meeting-fields`、`meetingClock`、Zoom 链接和现有隐藏的 `insertModeLabel`。
- 禁止项：新增 class、inline style、改动 CSS token、改动预览栏/ICS 卡片/底部按钮布局。

## 现状审计

### 邮件模板存储与渲染

- Schema/mapping：正文 SSOT 是 `mail_compose_template` + `mail_compose_template_block`；`V62__unify_mail_templates.sql:25-46` 已将 `MEETING_INVITATION` 纳入该模型。`V122__seed_manual_meeting_confirmation_template.sql:27-67` 是当前错误专用模板种子；`V123__add_mail_record_calendar_attachment.sql` 是当前最新迁移。
- Write paths：模板管理通过 `MailComposeTemplateService.create/update/delete/setEnabled` 写模板和内容块（`MailComposeTemplateService.kt:92-113`）；本修复不写模板、不写数据库。
- Read paths：`MeetingConfirmationService.specialTemplateOptions` 以专用 type 读取并只拼接 `CUSTOM_TEXT`（`:212-224`）；通用 `renderByCode` 读模板与全部有序块（`MailComposeTemplateService.kt:122-131,446-552`）；自动会议邀请也以 `MEETING_INVITATION` 调用该入口（`MeetingInvitationMailComposer.kt:12-19`）。
- Interaction points：会议预览与发送前重建都走 `MeetingConfirmationService.validateAndBuild`；因此必须在同一方法切换为通用渲染，不能只改预览。

### 会议输入、草稿与发送

- Schema/mapping：`MeetingInput` 是请求/草稿快照 DTO，不对应新表（`MeetingConfirmationModels.kt:30-50`）；日历附件快照写入既有 `mail_record.calendar_attachment_json`，本修复不改。
- Write paths：前端确认把 `meeting.input` 与 preview SHA 放入人工回复草稿；发送请求由 `UnmatchedInboundMailController` 透传到 `PendingMailOperationService`（`UnmatchedInboundMailController.kt:271-273`）。
- Read paths：`PendingMailOperationService.kt:501-545` 使用真实 processing/contact/account 重建并校验正文与附件；所有发送安全门禁随后继续执行。
- Interaction points：DTO 去除非默认必填字段会影响历史草稿及测试中的 `MeetingInput` 构造；本计划以默认兼容字段避免该破坏。

### 前端样式与交互

- 可复用 class：现有弹窗和时区组合框规则见 `meeting-confirmation.css:6-58,76-103`；没有新增视觉元素。
- 改动前基线：`DIALOG_HTML` 在 `meeting-confirmation.js:1543-1557,1579-1581` 生成模板、变量、专家称呼和签名区域；`localIssues` 在 `:525-545` 将模板/称呼视为必填；监听器在 `:1252-1257` 绑定其事件。
- 时区事件：搜索框 `blur` 同步 `closeZoneList(true)`（`:1154-1157`），候选项只在冒泡 `click` 后选择（`:1215-1225`）；这与鼠标事件顺序冲突。
- 测试：`meetingConfirmationIntegration.test.js:1280-1319,1434-1455,1510-1519` 仍驱动旧字段与点击选择；`meetingConfirmationStyle.test.js:104-114` 将被删的 id 固化为必需。

## 实现方案

### T1：以通用邀请模板生成会议正文

- Files：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt`
- I-1/I-2/I-3：将 `MeetingInput.templateId`、`templateBody`、`expertSalutation`、`senderSignature` 改为有默认值的兼容字段；新请求可省略，服务端不读取它们。保留 `zoneId`、`startLocal`、`endLocal`、`zoomUrl`、`generatedAt` 的校验。
- I-1/I-2：在 `MeetingConfirmationService` 注入现有 `MailVariableService`；按 `ManualExpertMailService.kt:215-220` 的已验证顺序解析专家画像、构建通用变量，再加入这两个会议值。调用 `mailComposeTemplateService.renderByCode("MEETING_INVITATION", variables, MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail))`，只取 `body` 进入现有 HTML/ICS/长度保护链。
- I-1：`renderByCode` 在模板不存在或禁用时抛出的 `IllegalStateException` 必须在会议服务转换为现有的 `IllegalArgumentException(MSG_TEMPLATE_UNAVAILABLE)`，使 preview 维持 400 而非泄露为 500。
- I-1/I-3：删除 `specialTemplateOptions`、`validateTemplateSnapshot`、`validateSalutation`、`validateSignature`、`buildDefaultSignature` 与专用 `renderTemplate` 的调用；`options` 不再读取/返回专用模板目录。保留 `MeetingConfirmationDomain.MANUAL_MEETING_CONFIRMATION` 作为旧模板/普通单发隔离常量，不迁移或删除用户数据。
- I-3：不改 `PendingMailOperationService` 逻辑；更新其测试 stub，使重新构建的服务返回使用通用模板正文，继续证明 SHA 与正文双重核验。
- Tests：服务测试证明回复片段/自定义文本、`${expertFamilyName|Colleague}`、`${meeting_time}`、`${zoom_url}` 在预览与重建中均被替换；模板缺失或禁用时 400；controller 测试证明新 JSON 不含四个旧字段仍可绑定；Pending 测试覆盖真实重建、SHA 不符和正文被编辑三类既有拒绝。

### T2：收敛弹窗字段并修复鼠标选时区

- Files：
  - `src/main/resources/static/meeting-confirmation.js`
  - `src/test/js/meetingConfirmation.test.js`
  - `src/test/js/meetingConfirmationIntegration.test.js`
  - `src/test/js/meetingConfirmationStyle.test.js`
- I-4/I-5/S-1：按 S-1 从 `DIALOG_HTML` 删除四块废弃 UI；同步删除模板目录状态、模板正文草稿、称呼/签名字段收集、局部校验和监听器。options 加载成功后只初始化默认时区和 `generatedAt`；模板实际可用性由 preview 的服务端 400 报错呈现。
- I-1/I-2/I-3：`buildMeetingInput` 只序列化 `zoneId`、`startLocal`、`endLocal`、`zoomUrl`、`generatedAt`。预览响应仍通过现有 `textBody/htmlBody/attachment` 写入草稿；确认/取消/下载/发送 payload 行为不改。
- I-4：将候选项监听从迟到的 `click` 改为在 `mousedown` 阶段执行 `preventDefault()` 后调用既有 `selectZoneById`；不复制选择逻辑，不改 `blur`、键盘或服务端目录。测试用真实 `mousedown` 事件覆盖 Istanbul，从已选 Shanghai 切换后断言 input 标签、`selectedZoneId` 及预览 payload 都是 `Europe/Istanbul`。
- I-5/S-1：更新样式测试 id 白名单，移除四个已删除 id；增加断言不存在模板选择/变量展开/称呼/签名节点，保留时区与 ICS 节点及 CSS 文件未改。

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt` | 兼容性默认字段与 options DTO 收敛 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt` | 通用 `MEETING_INVITATION` 渲染、两项会议变量、移除专用模板读取 |
| `src/main/resources/static/meeting-confirmation.js` | 删除废弃表单区/状态，修复时区鼠标选择 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt` | 通用模板渲染与两项变量测试 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 发送前重建测试适配 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt` | 新最小会议请求 JSON 绑定测试 |
| `src/test/js/meetingConfirmation.test.js` | 组件字段与预览请求测试适配 |
| `src/test/js/meetingConfirmationIntegration.test.js` | 真实鼠标时区选择与发送 payload 测试 |
| `src/test/js/meetingConfirmationStyle.test.js` | DOM 移除与 S-1 样式边界测试 |

明确不修改：`MailComposeTemplateService.kt`、`MailPlaceholderService.kt`、`MailVariableService.kt`、`PendingMailOperationService.kt`、`meeting-confirmation.css`、所有 Flyway 迁移、邮件模板数据库记录。

## 验收标准

- I-1：`MEETING_INVITATION` 的 `REPLY_SNIPPET` 与 `CUSTOM_TEXT` 均进入预览正文；源代码与测试不再要求 `MANUAL_MEETING_CONFIRMATION`、`{{expert_salutation}}`、`{{sender_signature}}`。
- I-2：配置 `${expertFamilyName|Colleague}`、`${meeting_time}`、`${zoom_url}` 的邀请模板，预览和发送前重建正文均无 `${...}` 残留；两个会议值分别为服务端格式化时间和输入 Zoom URL。
- I-3：旧完整 `MeetingInput` JSON 和新最小 JSON 都能反序列化；SHA 不匹配、最终正文不含重建内容仍返回 400；无 meeting payload 的普通人工回复路径不变。
- I-4：鼠标按下 Istanbul 候选项后，时区输入显示 Istanbul，列表关闭，preview payload 的 `zoneId` 为 `Europe/Istanbul`；Arrow/Enter 与 Esc 回退现有测试继续通过。
- I-5：页面源和 DOM 测试都不含 `meetingTemplate`、`templateDetails`、`templateText`、`resetTemplate`、`meetingName`、`meetingSignature`；保留时区、时间、Zoom、预览、ICS 下载和确认按钮。
- S-1：`git diff -- src/main/resources/static/meeting-confirmation.css` 为空；新增 DOM class 为零，变更仅删除 S-1 列出的节点。
- 自动验证：
  - `node --check src/main/resources/static/meeting-confirmation.js`
  - `node --test src/test/js/meetingConfirmation.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/meetingConfirmationStyle.test.js`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MeetingConfirmationServiceTest,MeetingConfirmationControllerTest,PendingMailOperationServiceTest`
  - `node --test src/test/js/*.test.js`

## 人工验收清单

### A-1：现有邀请模板生成会议确认正文

- 前置条件：已启用 `MEETING_INVITATION`；该模板包含既有回复片段/`${expertFamilyName|Colleague}`，并由操作者手工加入 `${meeting_time}` 与 `${zoom_url}`。
- 操作步骤：1. 打开一封已匹配专家的人工回复。2. 点击“会议确认”。3. 选择 Türkiye / Istanbul，填写 2026-09-11 10:00 至 10:30 和 Zoom 链接。4. 等待预览。
- 预期结果：左栏没有模板、变量、专家称呼、发件签名区域；正文保留邀请模板的内容，专家姓氏、会议时间、Zoom 链接均为实际值；不出现 `${...}` 或 `{{...}}`。
- 覆盖：I-1、I-2、I-5、S-1。

### A-2：鼠标选择时区并写入人工回复

- 前置条件：沿用 A-1。
- 操作步骤：1. 先选择中国/上海。2. 用鼠标点击 Istanbul 候选项。3. 下载 ICS 并导入本地日历。4. 点击“确认并填入回复”。
- 预期结果：时区立即显示 Istanbul；ICS 事件为 Türkiye Time 10:00–10:30，含完整 Zoom URL；人工回复正文插入预览内容和待发送 ICS，邮件主题仍为原 `Re:` 主题。
- 覆盖：I-3、I-4、I-5。

### A-3：模板缺失时不误写草稿

- 前置条件：在隔离环境禁用 `MEETING_INVITATION`。
- 操作步骤：填写完整会议表单，等待预览，再尝试确认。
- 预期结果：显示服务端模板不可用错误；人工回复正文、附件和发送状态均不改变。
- 覆盖：I-1、I-3。
