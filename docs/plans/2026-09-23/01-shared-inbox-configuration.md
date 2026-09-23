# 共享收件箱配置与兼容模式开发计划（1/4）

> 上位约束：[MAIN 总计划](00-shared-inbox-main.md)。执行前须读本组 02、03、04；本计划只建立配置能力和兼容表结构，不在生产设置 `LuKai_QF.inbound_mailbox_code`。采用测试先行；实施后独立验证，再进入下一计划。

**目标**：管理员可将任意逻辑发件账号显式关联到一个物理收件箱主账号；未关联账号行为不变。当前 `LuKai_QF` 的实际切换留到计划 04，待收信/退信路由完成后执行。

**架构**：`mail_sender_account.inbound_mailbox_code` 为可空主账号代码；`NULL` 表示本账号独立收件。`inbound_mail_processing.mailbox_owner_code` 为可空物理收件箱代码；旧行保持 `NULL`，新行由计划 02 写入。数据库唯一键仅约束非空的新物理 UID 身份。

**技术栈**：Kotlin、Spring Data JDBC、MySQL、Flyway、原生 HTML/JS/CSS。

**顺序**：01 表结构/配置（映射保持 NULL）→ 02 收信路由 → 03 退信归属 → 04 上线配置与历史修复。所有生产变更须单独审批；本计划本身不授权上线执行。

## 需求描述

- 可观察结果：账号编辑页可选“独立收件箱”或现有主账号；保存后再打开能看到原选择；以后新增别名也用此配置，不按域名猜测。
- 不得改变：既有 SMTP 身份、发信限额、启停状态、独立账号收信和历史 `uid_validity=0` 数据。
- 不在范围：本阶段不处理收信路由、不清理历史重复行、不改 `mailbox-chat.css`、不自动发现别名关系。

## 关键不变量

### I-1：关联是显式单层关系
- 规则：`inbound_mailbox_code=NULL` 表示独立物理邮箱；非空必须指向一个存在、非模拟器、自己字段为 `NULL` 的主账号，且不得指向自己。不能形成链或环。关联不修改 `sender_email`、SMTP/IMAP 凭据、`enabled` 或发信计数。
- 适用写路径：`MailSenderAccountService.createAccount/updateAccount/deleteAccount`、计划 04 的单次配置 UPDATE。
- 违反后果：多次抓取、错误发信账号或悬空收件箱。
- 来源：代码审计；`K-sender-account-enabled-scope`。

### I-2：旧行不可伪造物理代际
- 规则：新增 `inbound_mail_processing.mailbox_owner_code` 默认为 `NULL`；旧行保持 `NULL`，尤其 `uid_validity=0` 不得用当前 UIDVALIDITY 回填。非空新行的 `(mailbox_owner_code, uid_validity, imap_uid)` 唯一。
- 适用写路径：V134 DDL、计划 02 新写、计划 04 经证据核实的逐行修复。
- 违反后果：UID 重用时吞信，或历史错误归属。
- 来源：`K-inbound-seen-not-processed-marker`、V120。

### I-3：启用开关不是收信开关
- 规则：`enabled` 保持现有发信语义；关联配置不得通过启停状态来阻止/开启物理收信。页面文案不得暗示 `enabled=false` 会停止轮询。
- 适用写路径：账号服务、API、账号表单。
- 违反后果：当前 QF 虽禁用却继续重复拉取。
- 来源：`K-sender-account-enabled-scope`。

## 样式契约

### S-1：共享收件箱选择器
- 复用：`account-form-body`（`styles.css:2579`）、`form-section`（2588）、`form-section-title`（2597）、`form-grid-1`（2619）；`label`（1117）、`input, select, textarea`（1129）及其 hover/focus/disabled 规则（1143、1147、1154）。无新增 CSS、无 inline style。
- DOM 结构：在 `index.html:1772-1792` 的 SMTP/IMAP 并排容器之后、`<!-- 发送策略 -->` 之前，原样放置：
  ```html
  <fieldset class="form-section">
      <legend class="form-section-title">收件箱归属</legend>
      <div class="form-grid-1">
          <label>共享收件箱主账号<select name="inboundMailboxCode"><option value="">独立收件箱（本账号）</option></select></label>
      </div>
  </fieldset>
  ```
- 状态：`view` 模式由 `fillAccountForm` 的现有 `form.elements` 循环禁用；新增选项只以 DOM option/textContent 构造，不用 `innerHTML` 拼账号代码。没有自造 class。
- 禁止项：改动已有 class 定义、对整个账号页重排、改 `mailbox-chat.css`。

### S-2：启用文案
- 复用：`checkbox-row`（`styles.css:1166`，不改规则）。`index.html:1806-1808` 保持 `<label class="checkbox-row"><input name="enabled" type="checkbox" checked> ...</label>` 层级，仅把文字改为“启用此账号发信”；不加样式。

## 现状审计

### `mail_sender_account`
- Schema：`V1__create_business_tables.sql:1-25` 定义唯一 `account_code`、独立 SMTP/IMAP 凭据和 `enabled`，没有物理邮箱归属字段；`MailSenderAccount.kt:7-38` 映射此表。
- 写路径：`MailSenderAccountService.kt:74-174` 创建/更新/启停/删除/重置；`MailSenderAccountRepository.kt:23-90` 发送计数、自停/恢复和每日重置的 SQL 更新。迁移文件可写结构；计划 04 是唯一历史配置写入。
- 读路径：`MailSenderAccountController.kt:23-125` 列表/详情/API；`MailSenderAccountService.kt:43-60` 收信列表/单账号获取；`BatchAutoMailReplyService.kt:24,57-79` 两类轮询账号选择；发信调用 `getEnabledAccount/getManualSendAccount` 不应改。
- 交互点：API 保存新字段→编辑页回显；配置字段→计划 02/03 的物理轮询路由；禁用状态→收信列表仍现有语义，直到计划 02 按归属筛选。(来源: `K-sender-account-selection-sites`)

### `inbound_mail_processing`
- Schema：`V5__create_inbound_mail_processing.sql:1-20` 原唯一 `(sender_account_code, imap_uid)`；`V120__scope_inbound_uid_by_validity.sql:17-22` 已改为 `(sender_account_code, uid_validity, imap_uid)`。`InboundMailProcessing.kt:7-31` 当前无物理邮箱列。
- 写路径：`AutoMailReplyService.kt:1231,1284` 新建；`UnmatchedInboundMailService.kt:193,238`、`PendingMailOperationService.kt:1522` 的 copy/save；`InboundMailProcessingRepository.kt:35-47` 重开状态；已应用的 V14/V15 migration 曾做历史 `reason_type` UPDATE，本计划不重跑；计划 04 历史修复。(来源: `K-inbound-processing-write-paths`)
- 读路径：`AutoMailReplyService.kt:123-140` 按逻辑账号+代际+UID 去重；`MailboxConversationRepository.kt:433-495` 对话收件数/账号；`MailboxConversationService.kt:151-193` 列表；待处理队列和附件传输读取 processing ID。(来源: `K-mailbox-inbound-source-authority`)
- 交互点：新物理列由计划 02 的唯一收信写链填充；旧状态写路径必须 copy 原值；对话统计仍按 `sender_account_code` 而非物理字段。

### 前端样式盘点
- 当前 DOM 基线：`index.html:1774-1792` 为 `<div class="form-section-pair">` 内两个 `<fieldset class="form-section">`（SMTP / IMAP）；`index.html:1794-1809` 紧接 `<fieldset class="form-section">` 发信策略和 `label.checkbox-row`；上方 S-1/S-2 引文为允许的唯一结构差异。
- 样式实值：`styles.css:3` `--primary:#1e40af`、`:4` hover `#1e3a8a`、`:5` active `#172554`；`:15` `--panel-bg:rgba(255,255,255,0.55)`、`:18` `--border:rgba(15,23,42,0.11)`、`:17` `--line:rgba(15,23,42,0.055)`、`:19` `--surface:rgba(15,23,42,0.022)`、`:21` `--text-main:#1e293b`、`:22` `--text-muted:#94a3b8`、`:70-71` 圆角 7px/10px；`form-section` border 1px、padding `12px 14px 14px`；`form-grid-1` gap 10px；通用 select 13px/高 34px/内边距 `6px 10px`、hover 边框 `rgba(15,23,42,0.2)`、focus 3px 主色淡阴影、disabled 使用 `--surface` 与 `--text-muted`（`styles.css:1129-1159`）；`checkbox-row` min-height 32px。
- 注册/缓存：`app.js:3046` 账号列表载入 `state.accounts`；`:3190-3292` 表单填充/保存；`index.html:11-15,2168-2173` 共 11 个资源键当前同为 `20260922-discovery-continuous`，改 JS 后须统一更新，执行前重新 grep。(来源: `K-frontend-cache-key-triad`、`K-mailbox-chat-css-byte-contract`)
- 数量凭据：`rg -n '20260922-discovery-continuous' src/main/resources/static/index.html` 在调查时返回 11 行（11～15、2168～2173）；实施前按最新文件重查，不沿用固定数。

## 实现方案

### 阶段 1：先写失败测试，再补表结构（I-1、I-2）
- 文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`、`src/main/resources/db/migration/V134__shared_inbox_owner.sql`、两个 domain 文件。
- 测试先断言：两列存在、旧行两列 `NULL`、物理唯一键拒绝相同非空 owner/代际/UID、不同 owner 可同 UID、`uid_validity=0` 不回填。再加 DDL/实体。当前本地最高 V133，执行前重扫版本；若 V134 已占用，先修订本计划及文件名，不能抢号。该测试目前多处仍钉 131（见 `FlywayMigrationIntegrationTest.kt:62,136,178...`），将与本改动相关的最新版本断言更新到实际新版本；历史定点测试保持原目标。(来源: `K-flyway-version-follows-deploy-order`、`K-flyway-latest-version-test-pin`)
- `mail_sender_account.inbound_mailbox_code VARCHAR(64) NULL` 加指向 `mail_sender_account(account_code)` 的 FK；`inbound_mail_processing.mailbox_owner_code VARCHAR(64) NULL` 加唯一键 `(mailbox_owner_code, uid_validity, imap_uid)`。不回填旧行。

### 阶段 2：API 配置校验（I-1、I-3）
- 文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt`、`src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountController.kt`、各自同名测试。
- Create/Update command、request、response、domain 增加一个可空字段。Service 在保存前校验主账号存在、非自己、非模拟器、主账号未指向别人；有子账号的主账号只禁止删除或变成子账号，其他资料仍可编辑，不自动级联。`enabled` 逻辑原样。测试覆盖 200/400、保存后详情回显、独立账号旧 payload 兼容；非所有账号 `sender_email` 唯一的假设需由服务对同一归属组执行大小写无关的邮件地址冲突校验。
- 新字段被 `MailSenderAccountService.getAccount/listAccounts` 及计划 02/03 消费；不修改任何发信读取路径。

### 阶段 3：管理员表单（I-1、I-3、S-1、S-2）
- 文件：`src/main/resources/static/index.html`、`src/main/resources/static/app.js`。
- 从 `state.accounts` 生成可选择的主账号（排除当前账号、模拟器和已有关联的子账号）；保存 `null` 或选定代码，编辑页按响应回填。修改启用文案。统一更新 11 个缓存键；不编辑 CSS。
- UI 保存→API 写库→API 列表→UI 再回显为跨路径闭环。

## 变更文件清单

| # | 文件 | 作用 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V134__shared_inbox_owner.sql` | 两表各一列及非空物理 UID 唯一键 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailSenderAccount.kt` | 配置映射 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt` | 物理 UID 映射 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt` | 命令与单层关系校验 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountController.kt` | 请求/响应字段 |
| 6 | `src/main/resources/static/index.html` | 选择器、启用文案、缓存键 |
| 7 | `src/main/resources/static/app.js` | 选项/回显/保存 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | DDL 断言 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt` | 关系和兼容测试 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountControllerMvcTest.kt` | API 往返测试 |

## 验收标准

- I-1：单元/MVC 测试证明独立、合法子账号、自己指向自己、指向子账号、指向不存在/模拟器、父账号删除/改归属各场景；SMTP、限额、`enabled` 值保存前后不变。
- I-2：Flyway 集成测试验证旧行 `NULL`、两个不同 owner 同 UID 可写、同 owner 同 `(uid_validity,uid)` 第二行违反唯一键；0 历史值不回填。
- I-3：`MailSenderAccountServiceTest` 证明 `enabled=false` 对 `listAutoReceiveAccounts` 的旧行为不因本阶段改变；页面文字精确为“启用此账号发信”。
- S-1/S-2：DOM 与契约片段/文字一致；无新 class/inline style/CSS 改动，11 个资源键全相同；浏览器编辑/查看/新建三个状态目测。运行 `mvn -Dtest=FlywayMigrationIntegrationTest,MailSenderAccountServiceTest,MailSenderAccountControllerMvcTest test`、`node --check src/main/resources/static/app.js` 及 `node --test src/test/js/*.test.js`。
- 发布闸门：生产上只允许添加 nullable 列；字段值全部保持 `NULL`，不得在 02/03 完成前配置当前 QF。

## 人工验收清单

### A-1：新别名配置回显
- 前置条件：测试环境存在独立主账号 `Owner` 和新账号 `Alias`，且二者发信邮箱不同。
- 操作步骤：打开账号页→编辑 `Alias`→“共享收件箱主账号”选 `Owner`→保存→重新打开 `Alias`。
- 预期结果：下拉仍显示 `Owner`；`Alias` 的发信邮箱、SMTP 用户名、发信限额和启用状态均保持保存前值。
- 覆盖：I-1、S-1、UI/API/DB 闭环。

### A-2：非法关系与独立回归
- 前置条件：沿用 A-1 数据。
- 操作步骤：尝试 `Owner→Alias`、`Alias→Alias`；随后把 `Alias` 选回“独立收件箱（本账号）”并保存。
- 预期结果：两个非法配置均被拒绝；最终重新打开 `Alias` 显示“独立收件箱（本账号）”。
- 覆盖：I-1、独立账号不变。

### A-3：启用状态与视觉
- 前置条件：测试环境存在一个 `enabled=false` 账号。
- 操作步骤：分别打开新建、编辑、只读页面，目测新增区块与邻近 SMTP/IMAP 区块；检查 `enabled` 文案；运行旧的收信检查入口。
- 预期结果：标题为“收件箱归属”，主选项为“独立收件箱（本账号）”，文案为“启用此账号发信”；禁用账号的收信旧行为未被本阶段改变；只读页选择器不可编辑；卡片边框 1px、圆角 10px、网格间距 10px。
- 覆盖：I-3、S-1、S-2、旧行为回归。
