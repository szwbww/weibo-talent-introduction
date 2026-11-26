# 专家姓名编辑与模板变量一致性

状态：原方案待修订，不可直接据此实施。2026-11-17 用户已调整交互：复用“身份基本信息 → 编辑”，姓名与人才编号、中文译名、出生日期、证件号、来源国家在同一弹窗保存；删除新增的顶部/姓名卡独立入口。预览已调整，生产功能未实施。

后端修订要求：不能在前端串联两次保存冒充原子成功。统一服务事务保存人工档案与expertName覆盖，同时校验双方版本；任一冲突整体回滚。仍复用expert_template_override作为模板修正来源，并保留其他覆盖key。下方独立PATCH与弹窗设计为旧版记录，待重新审计文件范围后替换。

## 需求描述

在专家详情顶部和身份基本信息提供“编辑姓名”，保存后两处展示和 `${expertName}` 使用相同人工修正值，刷新后仍生效；模板变量编辑页也反映相同值。

必须保持：专家身份键、邮箱、核验事实与 ES 原始画像不变；其他模板变量不丢失；历史邮件正文和已人工编辑的草稿不自动重写；权限仍沿用管理员写权限。

不在本轮：ES 原始姓名清洗、全局列表/搜索索引改名、历史联系人快照回填、根据全名推断姓氏。联系人 `expert_contact.expert_name` 继续作为原始业务快照；本轮详情明确使用人工修正值覆盖展示，不宣称全系统原始姓名已修改。

## 关键不变量

### I-1：姓名修正单源
- Rule：复用 `expert_template_override.values_json.expertName`，不新增姓名字段。详情优先取该 key（按键存在性，不能用 truthy 判断），无 key 时恢复现有详情姓名；模板无 key 时继续按 ES givenNames/familyNames 生成。显式空覆盖来自旧变量编辑器时，详情显示“未填写”，不能把旧姓名当有效新值。
- Applies to：姓名编辑、变量编辑、详情初始化、模板 buildVariables。
- Violation consequence：页面改名但外发仍用旧名，或不同编辑入口互相覆盖。
- 来源：本次代码审计。

### I-2：原子局部更新
- Rule：新增 PATCH 姓名接口，携带 expertId、level、expectedVersion、name；name=null 表示撤销覆盖，字符串必须 trim 后非空、单行、最多200个 Unicode code points，拒绝控制字符和 `${`。服务端读取完整覆盖 map 后，只修改/删除 expertName，再通过现有 CAS save 提交；expectedVersion 不符返回409，不自动覆盖重试。姓名校验不追溯改变旧全量变量 PUT 的空值契约。
- Applies to：Controller PATCH、Service patchName；原 PUT 继续共享 version。
- Violation consequence：其他变量丢失、并发编辑静默覆盖。
- 来源：本次代码审计。

### I-3：身份和历史隔离
- Rule：不写 ES、不写 expert_contact，不改 expertFamilyName。原有审计记录只存 key/version，不记姓名正文。已发送正文及已实体化草稿不回写。后续渲染保留 buildVariables 的 original + overrides 顺序。
- Applies to：所有写入与渲染。
- Violation consequence：污染核验身份或历史正文。
- 来源：本次代码审计；K-renderText-all-callers 提醒核对各消费者，本方案不改 renderText。

### I-4：前端异步上下文
- Rule：读取、编辑、保存捕获 expertId、level、详情 generation、宿主；每次 await 后核验，切专家后不得回写旧响应。保存成功才更新显示并失效当前专家预览；409 保留输入并提示重载。取消不写入。模板变量页保存/恢复 expertName 也触发当前详情姓名刷新。
- Applies to：有联系人详情、无联系人详情、delegated 资料宿主、变量编辑器。
- Violation consequence：串专家或刷新后旧名复现。
- 来源：现有 createExpertManualProfileSession 与 ExpertMailSettings owner 模式。

## 样式契约

### S-1：入口与姓名弹窗
复用 styles.css `.button` :802、`.button:hover` :825、`.button:active` :832、`.button.primary` :838，以及 :12973–12989 的 ec 规则。按钮32px高、字号12px、水平padding12px；ec-edit-grid gap12px，错误色#be123c，焦点#3b82f6 2px，禁用opacity .45。不得新增 CSS、inline style 或改动既有规则。

顶部 h2 增加稳定 data-role；姓名卡片增加同名入口；原身份资料编辑按钮及其字段不变。新增节点使用如下骨架，姓名只经 textContent/value：

```html
<button type="button" class="button" data-edit-expert-name>编辑姓名</button>
<dialog class="em-upload-dialog ec-editor" data-expert-name-editor aria-label="编辑姓名">
  <form>
    <header class="em-upload-head"><div class="em-upload-title"><h3>编辑姓名</h3><span class="em-upload-subtitle">同时用于后续邮件的 expertName 变量</span></div><button class="modal-close-btn" type="button" aria-label="关闭">×</button></header>
    <div class="em-upload-body"><div class="ec-edit-grid"><label class="ec-span">姓名<input name="expertName"></label></div><span class="ec-error" role="alert" hidden></span></div>
    <footer class="em-upload-actions"><span class="em-upload-summary" role="status"></span><button class="button" type="button" data-name-restore>恢复原始值</button><button class="button" type="button" data-name-cancel>取消</button><button class="button primary" type="submit">保存</button></footer>
  </form>
</dialog>
```
恢复只改变待提交草稿，点击保存才发 name=null；取消放弃恢复操作。使用现有dialog开关方式。无权限隐藏入口，服务端继续校验。

## 现状审计

### expert_template_override
DDL：V175，expert_key VARCHAR(64) utf8mb4_bin 主键、version BIGINT、values_json LONGTEXT、updated_by VARCHAR(64)、updated_at DATETIME(3)。不是 ES mapping，不涉及dynamic或迁移。
读写：ExpertTemplateOverrideService.read/save 使用规范化key；save CAS UPDATE、首次INSERT、冲突409，并记录 EDIT_EXPERT_TEMPLATE_VARIABLES。Controller GET/PUT 校验身份和管理员。前端 expert-mail-settings.js 全量 PUT map。
读者：MailVariableService.buildVariables 将 original + overrides 合并；Controller GET 返回覆盖与原始变量。此链覆盖首封、手动邮件、自动回复、预览和会议模板消费者（证据见末尾）。
交互点：新增姓名 PATCH 与旧变量 PUT 共享版本；详情展示与模板 buildVariables 共用覆盖值。

### 现有姓名来源（只读）
ExpertProfile.displayName 拼接 givenNames/familyNames；MailVariableService.resolveExpertName 使用 resolveExpertFullName，同样读取 ES 原始名，并非 contact.expertName。详情顶部优先 contact.expertName。app.js 的 EXPERT_MANUAL_PROFILE_READONLY_FIELDS 把姓名列为只读，人工资料PATCH身份组不接受姓名。
不扩展人工档案PATCH：避免让一次身份资料保存跨人工档案/变量覆盖两个版本体系，出现部分成功。

### 前端样式盘点
现有姓名卡片：
```html
<div class="metadata-card" data-ec-readonly="${field.name}"><div class="metadata-card-header">${field.label}</div><div class="metadata-card-value ec-text" data-ec-value="${field.name}"></div></div>
```
现有样式：
```css
.ec-edit-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.ec-span { grid-column: 1 / -1; }
.ec-error { display: block; color: #be123c; font-size: 12px; line-height: 1.5; }
```
详情有 contact 与 no-contact 渲染，资料另有delegated挂载；接入共享姓名逻辑，避免仅更新截图主入口。当前资源键20261116-archive-capacity；src/test 精确搜索无命中。发布时统一 index 所有已版本化资源键，实施前重新反查（来源 K-frontend-cache-key-triad）。

## 实现方案

1. I-1/I-2/I-3：在现有Service加 patchName，Controller加PATCH；只改name局部，不建表，不碰现有正在修改的模板composer。返回最新完整override快照。复用身份校验、管理员检查、审计及CAS。
2. I-1/I-4/S-1：expert-mail-settings.js 提供共享姓名读取/编辑组件及保存回调；app.js 为详情顶部、身份姓名卡、delegated宿主接入。初始与重开读取覆盖；模板变量保存回调重新加载当前姓名并失效模板/邮件预览。保存时禁用重复提交。失败不显示成功、不改内存姓名。
3. I-1/I-3：验证 MailVariableService 原有覆盖路径已经让新姓名影响 plain/HTML/preview、无contact的首封、无ES profile但有contact key的渲染；无需修改生产变量解析代码。姓氏保留原值。
4. I-1–I-4/S-1：新增后端及DOM行为测试；更新index缓存键并执行相关JS/Kotlin回归。不得对其他工作树改动做回滚或格式化。

## 变更文件清单

|文件|用途|
|---|---|
|src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertTemplateOverrideService.kt|局部改名与验证|
|src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertTemplateOverrideController.kt|PATCH入口|
|src/main/resources/static/expert-mail-settings.js|姓名组件、变量保存通知|
|src/main/resources/static/app.js|详情宿主接入|
|src/main/resources/static/index.html|资源缓存键|
|src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertTemplateOverrideServiceTest.kt|新建，局部修改/CAS/恢复|
|src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertTemplateOverrideControllerTest.kt|新建，权限/参数/身份/冲突|
|src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt|覆盖值plain/HTML/preview契约|
|src/test/js/expertMailSettings.test.js|姓名编辑/旧变量页互通/迟到响应|
|src/test/js/expertManualProfile.test.js|详情挂载与姓名卡DOM行为|

本清单不包含计划及知识文件。范围是姓名覆盖后端与详情前端，未改schema。

## 验收标准

- I-1：原始Old Name，保存Correct Name；详情顶部/姓名卡/变量页为Correct Name；变量映射与plain/HTML/preview输出Correct Name。重开仍同值。无contact首封取同覆盖；无profile有contact key仍取覆盖。
- I-2：其他institution覆盖保留；版本从v到v+1；旧v的姓名PATCH与变量PUT均409；首次并发冲突；姓名空/换行/超长/占位符拒绝，姓名null移除key而非存null。
- I-3：没有ES/contact写入；expertFamilyName未改变；已发正文未改；审计不含姓名；旧全量PUT空字符串语义保留。
- I-4：A保存未返回切B，迟到响应不改B；取消零写请求；500和409保留输入；恢复保存刷新预览，变量页改名/恢复也更新详情。管理员权限覆盖接口与UI。
- S-1：DOM有编辑入口、独立dialog；仅复用指定class；无样式文件修改；focus、disabled、错误态可见。
- 执行 node --test src/test/js/*.test.js，以及修改JS的 node --check；JDK11执行 mvn test -Dtest=ExpertTemplateOverrideServiceTest,ExpertTemplateOverrideControllerTest,MailVariableServiceTest。不调用真实SMTP。

## 人工验收清单

### A-1：纠错及模板联动
- 前置：管理员打开含Old Name的测试专家，有可预览`${expertName}`的模板。
- 操作：姓名旁编辑为Correct Name并保存；切模板预览，刷新详情。
- 预期：顶部、姓名卡、变量页均Correct Name；预览正文含Correct Name；其他专家不变。
- 覆盖：I-1/I-4/S-1。

### A-2：两个入口与并发
- 前置：同专家另有institution自定义University A；两个浏览器页同时打开。
- 操作：页1改名保存；页2用旧版本保存；重载页2在模板变量编辑器改expertName为Second Name。
- 预期：旧版本提示冲突、不覆盖；最终详情Second Name，institution仍University A。
- 覆盖：I-1/I-2/I-4。

### A-3：恢复、取消和历史
- 前置：已有人工修正与历史邮件/人工草稿。
- 操作：点恢复后取消；再点恢复并保存；查看原画像、历史邮件及expertFamilyName。
- 预期：取消保留修正；保存恢复后详情用原业务姓名，模板用原ES姓名；邮箱、身份键、姓氏、历史正文及人工草稿均保持原值。
- 覆盖：I-1/I-2/I-3。

### A-4：切换与权限/样式
- 前置：管理员开A详情，网络限速；另有只读账号。
- 操作：保存A时切B；用只读账号打开详情；管理员检查弹窗焦点、错误及保存态。
- 预期：B不出现A姓名；只读无编辑入口且写API拒绝；按钮32px、错误#be123c、焦点蓝色2px、禁用opacity .45，无透明遮挡；原身份资料编辑仍可保存中文译名。
- 覆盖：I-3/I-4/S-1。

## 审计命令与输出

以下为计划时工作树证据；并行修改可能改变行号，实施前复核。

```text
$ rg -n 'expert_template_override' src
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertTemplateOverrideService.kt:24:        "SELECT version, values_json FROM expert_template_override WHERE expert_key = ?",
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertTemplateOverrideService.kt:40:        val updated = jdbc.update("""UPDATE expert_template_override SET values_json=?, version=version+1,
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertTemplateOverrideService.kt:46:                jdbc.update("INSERT INTO expert_template_override VALUES (?,1,?,?,CURRENT_TIMESTAMP(3))", key, json, actor)
src/main/resources/db/migration/V175__expert_template_overrides.sql:1:CREATE TABLE expert_template_override (
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:209:            assertTrue(c.tableExists("expert_template_override"))
[exit=0]
```

```text
$ rg -n 'buildVariables\(|renderForContact\(|renderHtmlForContact\(' src/main/kotlin/com/weibo/talentintroduction/{mail,campaign,template,expert}
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:334:            variableService.buildVariables(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:842:        val plainBody = mailVariableService.renderForContact(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1393:            variables = mailVariableService.buildVariables(account, mailVariableService.resolveExpertProfileFor(contact), contact.expertEmail, contact = contact) + mailTemplateVariables(account),
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposer.kt:17:            variables = mailVariableService?.buildVariables(account, expert).orEmpty() + mapOf(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:667:        val renderedSubject = mailVariableService.renderForContact(trimmedSubject, source.account, contact)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:682:        val renderedText = mailVariableService.renderForContact(rawText, source.account, contact)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:690:                mailVariableService.renderHtmlForContact(rawHtmlFromTemplate, source.account, contact)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:694:                mailVariableService.renderHtmlForContact(htmlBody, source.account, contact)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt:120:    fun buildVariables(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt:202:    fun renderForContact(text: String, account: MailSenderAccount?, contact: ExpertContact): String =
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt:205:    fun renderHtmlForContact(html: String, account: MailSenderAccount?, contact: ExpertContact): String {
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt:207:        val variables = buildVariables(account, expert, contact.expertEmail, previewFallbacks = false, contact = contact)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt:222:        val variables = buildVariables(account, expert, contact.expertEmail, previewFallbacks, contact = contact)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:303:        val base = mailVariableService.buildVariables(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ReplyIdentityService.kt:54:        return variables.buildVariables(source.account, profile, unsubscribeEmail = null,
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:96:                    variables = mailVariableService.buildVariables(account, contactForSeed?.let { mailVariableService.resolveExpertProfileFor(it) },
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:124:                            mailVariableService.renderForContact(decision.rawDraftText, account, previewContact)
src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:56:        val variables = buildVariables(account, expert)
src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:100:        val variables = buildVariables(account, expert)
src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:119:                snapshot.selectRawTexts(), buildVariables(null, expert), snapshot.requiredKeys
src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:137:        return toTemplateVariableItems(buildVariables(account, expert))
src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:140:    fun buildVariables(account: MailSenderAccount?, expert: ExpertProfile): Map<String, String> =
src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:141:        mailVariableService.buildVariables(account, expert)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:122:            variables = mailVariableService?.let { it.buildVariables(account, it.resolveExpertProfileFor(contact), contact.expertEmail, contact = contact) }.orEmpty() + mapOf(
[exit=0]
```

```text
$ rg -n '20261116-archive-capacity' src/test
[exit=1]
```

```text
$ git status --short
 M src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightService.kt
 M src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientStatisticsCache.kt
 M src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt
 M src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt
 M src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt
 M src/main/resources/static/app.js
 M src/main/resources/static/index.html
 M src/test/js/batchRecipientStatisticsCache.test.js
 M src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientStatisticsCacheTest.kt
 M src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt
 M src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt
[exit=0]
```
