# 08 · 静态资源缓存激活与最终回归

状态：待审批；依赖03/07，后端01/02/04/05/06应已完成。一个前端发布子系统，12文件（A3 将授权由 10 扩至 12）；无新字段/接口。

## 需求描述

使线上浏览器加载日历和图标附件的新前端资源。保持全部旧资源顺序和功能断言。只统一静态资源版本，不在此阶段改业务、样式或运维配置；上线动作待整个实现完成后再按既有发布流程执行。

## 关键不变量

### Invariant I-1: 资源与测试同键
- Rule: 当前9个?v=资源全部从20260914-followup-email变为20260916-meeting-mail；9个固定键测试文件同步。资源数保持9，因为03将日历实现放在已有app.js，不引入额外script；task-modal-runtime.js仍不加版本。执行前若基线键已变，先重新检索并更新计划证据，不覆盖别人的新键。
- Applies to: index资源引用、测试常量/正则/文案。
- Violation consequence: 浏览器继续使用旧按钮/脚本，或发布测试失败。
- 来源: K-frontend-cache-key-triad。

### Invariant I-2: 只激活已验证内容
- Rule: 此阶段只改缓存键；不能把预览页的模拟数据、浏览器存储、mock fetch复制到生产。原资源顺序、DOM、CSS合同保持03/07落地结果。回归失败定位具体证据后回到所属子计划，不在缓存计划顺手改业务。
- Applies to: 发布diff、全量测试、浏览器检查。
- Violation consequence: 预览假数据进入业务或范围失控。
- 来源: original。

## 样式契约

### S-1：无视觉结构变更
- 复用index.html:10-14的4个link与:2109-2114附近5个script；既有styles.css和mailbox-chat.css各规则不改。
- 逐字目标引用（按基线已有相对顺序放回原位，不能整体搬动节点）：

```html
<link rel="stylesheet" href="styles.css?v=20260916-meeting-mail">
<link rel="stylesheet" href="expert-materials.css?v=20260916-meeting-mail">
<link rel="stylesheet" href="mailbox-chat.css?v=20260916-meeting-mail">
<link rel="stylesheet" href="meeting-confirmation.css?v=20260916-meeting-mail">
<script src="trust-reply-workbench.js?v=20260916-meeting-mail"></script>
<script src="expert-materials.js?v=20260916-meeting-mail"></script>
<script src="meeting-confirmation.js?v=20260916-meeting-mail"></script>
<script src="mailbox-chat.js?v=20260916-meeting-mail"></script>
<script src="app.js?v=20260916-meeting-mail"></script>
```
- 新CSS：无。新增DOM：无。禁止inline style或其他class变动；视觉合同由03/07继续约束。

## 现状审计

- 当前缓存键真实为20260914-followup-email，不能沿用知识库9月14旧快照20260910-meeting-generic-template。本轮精确rg -l命中下表9份测试，回执[evidence.cache_tests/assets](meeting-mail-evidence.md#完整检索回执)。
- 资源写路径：index.html静态link/script；读取路径：浏览器、9份固定键测试。本次只覆盖已审计的仓库静态资源，不宣称线上CDN缓存配置已验证。
- 前端样式盘点：只改?v=字符串；标签结构见S-1；实际前后HTML基线见证据assets。按钮颜色/字号/尺寸由03/07合同提供，此阶段无token变更。
- IP-1：部署的新index→浏览器资源URL→新脚本/样式。IP-2：index版本字符串→现有9份测试。

## 实现方案

1. 仅在清单10文件中替换当前缓存键为I-1新值（I-1/I-2、S-1）；保留既有资源数量断言9及全部功能断言，不通过放宽正则/删测试绕过失败。
2. 比较此阶段diff只含key替换；rg旧键在index/这9份测试0命中；新键9资源一致。按01～07各验收完成基础上跑全量node/JDK11 Maven测试；Flyway/MySQL门禁必须明确启用，报告通过/失败/跳过实数（I-1/I-2）。
3. 发布前在测试环境真实/talent路径验证上传10MiB经代理可通过、重启后已发下载可用、两端CRUD持久。前端预览用生产接口和测试数据；若仅做独立视觉预览必须清楚标为演示，不能冒充SMTP成功或DB持久（I-2）。

## 变更文件清单

|序号|文件|操作|内容|
|---|---|---|---|
|1|`src/main/resources/static/index.html`|修改|9个已有资源统一新缓存键|
|2|`src/test/js/ragWorkbenchRender.test.js`|修改|同步当前键字面量；保留功能断言|
|3|`src/test/js/meetingConfirmationAssets.test.js`|修改|同步当前键字面量；保留功能断言|
|4|`src/test/js/overlayAndDialogContrast.test.js`|修改|同步当前键字面量；保留功能断言|
|5|`src/test/js/mailboxChatStyle.test.js`|修改|同步当前键字面量；保留功能断言|
|6|`src/test/js/ragKnowledgeBasePage.test.js`|修改|同步当前键字面量；保留功能断言|
|7|`src/test/js/manualReplySubjectPrefill.test.js`|修改|同步当前键字面量；保留功能断言|
|8|`src/test/js/trustReplyWorkbenchSharedMount.test.js`|修改|同步当前键字面量；保留功能断言|
|9|`src/test/js/checkRepliesRelocation.test.js`|修改|同步当前键字面量；保留功能断言|
|10|`src/test/js/batchSendTaskConsoleVisualFix.test.js`|修改|同步当前键字面量；保留功能断言|
|11|`src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`|修改|A3：`EXCLUDED_NOISE_SITES` 中 `UnmatchedInboundMailController.kt` 的行号随 06 的插入平移（219→221、1125→1137），只改行号与注释，不改片段|
|12|`src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt`|修改|A3：`sendManualRichReply` 新增 `attachmentIds`/`authenticatedUsername` 两参数后，四处 Mockito matcher 计数由 21 补到 23|

## 验收标准

- I-1：index恰9个统一新键；9份测试无旧键；node --test src/test/js/*.test.js全通过。
- I-2：缓存阶段diff无业务变化；JDK11 mvn test；mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true；业务SQL测试显式-DmysqlIt=true；未运行的环境测试不得记通过。
- S-1：资源相对顺序与基线一致，task-modal-runtime.js仍无?v=；样式逐字块继续由03/07测试锁定。

## 人工验收清单

### A-1：线上资源激活
- 前置条件：完整实现已在测试/talent部署；浏览器曾访问旧版且缓存启用。
- 操作步骤：1.普通刷新。2.查看Network中的9资源URL。3.打开会议日历与邮箱人工回复。
- 预期结果：9资源?v=20260916-meeting-mail且HTTP200；新增Tab可用；链接旁回形针没有文字；会议确认/跟进仍可用，无控制台异常。
- 覆盖：I-1/I-2/S-1；IP-1/IP-2。

### A-2：最终业务贯通
- 前置条件：测试专家、SMTP沙箱、中文zip；完整后端与持久磁盘已配置，记录原文件SHA。
- 操作步骤：1.会议邀请附zip发送。2.日历改期。3.邮箱确认改期后取消。4.查看取消历史与对话附件。5.重启应用，再打开日历和下载。
- 预期结果：SMTP只1封；有效排期1→0，取消历史仍1；两边同北京时间；ICS与zip始终是原邮件原件；zip SHA一致；专家材料数量不变。
- 覆盖：I-2/S-1；IP-1；两需求最终交付。
