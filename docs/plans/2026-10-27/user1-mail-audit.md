# user1 邮件只读权限：代码审计与证据索引

审计日期：2026-10-27。基线 HEAD：`90250fa5d736772048db7b18259e7c26a722b9c7`；工作区已有未提交修改，记录于 [git-baseline](user1-mail-evidence/git-baseline.txt)。以下是本地工作区代码事实，不代表已核对线上部署 commit。此前页面是线上 DOM 快照加权限模拟，不能作为后端已实现的证据。

本文是主计划及五个子计划共同引用的现状审计附件。`B` = `src/main/kotlin/com/weibo/talentintroduction/`，`F` = `src/main/resources/static/`，`T` = `src/test/kotlin/com/weibo/talentintroduction/`。文中行号对应本次基线，执行前按符号复核。

## 证据回执

每份文本保存原始命令、退出码、完整输出；退出码 1 是该命令范围内无命中，不能扩张成全仓不存在。

|编号|范围与作用|
|---|---|
|[01](user1-mail-evidence/01-auth-store.txt)|`AdminUser/admin_user/AUTH_USERNAME` 生产代码与认证测试读写索引|
|[02](user1-mail-evidence/02-mail-reads.txt)|会话 count/page/timeline、附件解析调用者|
|[03](user1-mail-evidence/03-api-routes.txt)|Kotlin MVC 映射声明；419 条匹配行，**不是 419 个接口**|
|[04](user1-mail-evidence/04-fe-lifecycle.txt)|app/chat 登录、路由、挂载、缓存与销毁位置|
|[05](user1-mail-evidence/05-cache-version.txt)|当前资源版本与前端版本断言，不能照搬历史条目数量|
|[06](user1-mail-evidence/06-new-store-absent.txt)|拟新增表/类型/模块名在 src/main、src/test 内无命中，exit=1|
|[07](user1-mail-evidence/07-contact-delete.txt)、[15](user1-mail-evidence/15-contact-delete-corrected.txt)|07 仅 expert 目录，不能代表联系人模块；15 补查 campaign/discovery，命中材料状态、邮箱别名删除，非联系人删除|
|[08](user1-mail-evidence/08-constructor-callers.txt)|构造器显式调用点；包含 OpenAlexAuthInterceptor 同名子串噪声|
|[09](user1-mail-evidence/09-mail-fe-io.txt)|chat 内网络请求与宿主适配器调用位置|
|[10](user1-mail-evidence/10-mail-store-access.txt)、[11](user1-mail-evidence/11-contact-store-access.txt)|邮件、来信、联系人表及 Repository 的迁移、读取、写入调用清单；保留噪声，不能把匹配行数量当写路径数量|
|[12](user1-mail-evidence/12-guards-tests.txt)|测试开关、JS 执行入口、行号守卫、认证测试|
|[13](user1-mail-evidence/13-user1-existing.txt)|user1 子串只命中邮箱/IMAP 测试；不能据此推断真实数据库没有 user1|
|[14](user1-mail-evidence/14-verbatim-baseline.md)|前端 HTML/JS 生成 DOM、CSS 完整规则、认证 schema 的逐字摘录|
|[16](user1-mail-evidence/16-frontend-css-use.txt)|复用 class 的当前使用点；本轮禁止修改这些既有规则|
|[17](user1-mail-evidence/17-auth-role-absent.txt)|auth 目录 role/permission/权限/授权/user1 无命中，exit=1|
|[源码摘要](user1-mail-evidence/source-sha256.json)|本次 src/main Kotlin/JS/CSS/HTML/SQL 文件 SHA-256；用于发现计划后漂移，不能用 HEAD 代替脏工作区基线|

后端关键原文另见[逐字代码证据21](user1-mail-evidence/21-backend-verbatim.md)，覆盖硬编码登录限制、拦截器、详情无身份、SQL分页和附件投影；便于直接核对上述结论。

## 认证与 session

1. `B/auth/controller/AuthController.kt:31`：`if (username != "admin")` 直接拒绝其他登录名。账号表能存用户，不等于 HTTP 登录开放多用户。
2. `B/auth/domain/AdminUser.kt:8` 与 `V25__create_admin_user.sql:1`：id、username、password_hash、must_change_password、last_login_at、created_at、updated_at；username 唯一，无 role。新设计无需给已有共享表加权限字段。
3. 写路径：`AdminUserBootstrap.run:23–36` 首次插入 admin/BCrypt；`AuthService.login:25–36` 回写最后登录及更新时间；`AuthService.changePassword:40–74` 改 hash/mustChangePassword/updatedAt。相关迁移与所有匹配调用见 01。新 bootstrap 必须只补不存在的 user1，不重置已有记录。
4. 读路径：bootstrap 重复/并发存在性检查、AuthService.login/changePassword/findUser、AuthController.me、AuthInterceptor.preHandle；身份来自 `AuthSessionKeys.USERNAME`。登录写 session 并 `changeSessionId()`，logout invalidate；me/interceptor 对已删除用户 invalidate。
5. `B/auth/config/AuthInterceptor.kt:14–59` 只验证 session、账号存在、首次改密码；不是业务授权。`AuthWebConfig.kt:25–27` 仅覆盖 `/api/**`，排除 login/me；OPTIONS 直接放行。不得以“隐藏按钮”代替后端权限。
6. `AuthService.kt:45` 改密仍限定 admin，且最短 8 字符；本次用户指定初始密码为 7 字符，新增账号必须 `mustChangePassword=false`，本版不给 user1 改密入口。保留 admin 改密规则。
7. 现有测试以 alpha/beta/operator 构造 session；本版固定 **user1** 限制，不推导“所有非 admin 都是只读”。新增授权管理接口单独精确要求 admin；登录最终仅 admin/user1。
8. `GlobalExceptionHandler.kt:83–85` 存在 Exception 兜底 500；本次显式定义权限异常及 403 handler，不能猜测 ResponseStatusException 会穿透通用 advice。
9. 配置边界：AuthWebConfig 带 `talent-introduction.auth.enabled` 条件。验收/上线必须确认 true；本计划不声称 auth=false 提供权限安全。

## 邮件读取与附件路径

|现状证据|影响|
|---|---|
|ConversationController.list:223–262 已传 sessionUsername；messages:265–275 未传身份|列表有可复用参数，详情必须补身份，不取 query/body username|
|ConversationRepository.countConversations:150、pageConversations:171、explainConversationsPage:213 共用 expertPredicates:678|ACL 应放共同 WHERE 的 AND 条件，先过滤再聚合/计数/分页；不可应用层删页内行|
|Repository.timelineMessages:482–533 只按 contactId/account/cursor；params 当前 username 为空|详情必须检查 grant；直接换 contactId 不能绕过列表|
|Service.listConversations:149–166 先 count/page，再按当前页 ids 取最新消息、邮箱、材料数量、标签|权限过滤必须发生在这些派生读取前；仅对已授权页做后续查询|
|Service.listMessages:227、236、253–299 读联系人/时间线/发件快照/附件|reader 在接触这些数据前做 grant 检查；不调用附件解析、不投影日历/附件 URL|
|Service.activeAccountCodes:543–546 排除 SIMULATOR_NOOP，**不排除 disabled**|不能顺手改变已有历史邮件范围（来源 K-sender-account-enabled-scope）|
|Service.decodeKeyset:485 已绑定 contactId/accountCode|保留游标绑定，禁止以别人的 cursor 跨专家读取|

邮件聚合只读主存为 `mail_record`、`inbound_mail_processing`、`expert_contact`，schema 起点 `V1__create_business_tables.sql`，后续字段迁移与全部检索结果见 10/11。现有生产者包括发信/回复服务、收信处理与关联、联系人创建更新、状态更新、任务/批量发送；本版不修改它们。新 ACL 过滤这些既有写路径生成的数据，**不得新增读操作引起的状态更新、收信处理、发送或 ES 回写**。ES 标签为既有当前页只读查询，本版不改 mapping、写入或补齐流程。

附件并非单一入口（映射声明见 03）：

- `MailboxAttachmentController:22,28`：附件列表及无联系人参数下载。
- `ExpertDocumentBrowseController:26,44`：专家附件 download/preview。
- `OutboundAttachmentController:65,80,118`：上传、草稿附件下载、已发附件下载。
- `CalendarAttachmentController:45`：日历附件。
- `ExpertMaterialController:47`：材料打包下载；会议视频 preview/download 也有独立接口。
- `MailboxController` 的旧 list/by-expert/detail、`MailReplyDraftController` 的草稿 GET/PUT/DELETE、邮件日志/统计/搜索/发件账号等不纳入 reader 白名单。

`document/service/ExpertMaterialService.resolveMessageAttachments:707` 从附件仓库及历史桥接关系读取；返回附件对象。reader 分支完全跳过它。禁止附件接口与禁止返回附件入口同时成立，不能只隐藏下载按钮。公共 `/u` 退订等非 `/api` 外部收件人协议不是本次 ACL 改造对象；只读正文以纯文本展示，不自动生成内部附件/退订链接。

## 新共享表的写读闭环（设计，尚不存在）

`expert_mail_read_access(username, expert_contact_id, granted_by, created_at)` 是新增的授权关系存储，不给现有表加字段。唯一写者为新 ExpertMailReadAccessService：admin PUT 幂等授予，DELETE 幂等撤销；迁移建表。读者为授权状态 GET、会话 SQL EXISTS、详情准入检查。无后台作业、回填、ES 副本或权限缓存；空表即全部不授权。按 expert_contact.id 授权，跨真实邮箱账号生效。

FK 设计采用联系人删除级联清理，**这是本次设计，不是 V121 已有事实**。现有 V121 关注表 FK 未写 ON DELETE CASCADE。user 字段不设 admin_user FK，允许 admin 在第 05 步账号开放之前预先授权。实际数据库是否已有 user1 必须执行前查证。

## 前端样式盘点与生命周期

逐字改动前基线见 14；不得用此前线上邮件正文快照入库，证据附件只存仓库源码。

- `F/index.html:79–152` 桌面 `.nav-tab[data-view]` 与手机 `#mobileCoreView` 双入口；`:750–810` 现有 `#view-mailbox/#mailboxLegacyToolbar/#mailboxList`。只有移除桌面标签会留下手机入口。
- `F/app.js:5111` 从桌面 tab 生成 mobile options；`:5210` setView；`:5283` mailbox refresh 还调用自动回复/群推摘要；`:21843` loadMailbox **先**读 sender-accounts，再尝试聊天模式，失败会回退旧表格。
- `app.js:21051–21079` 登录后还加载变量、未匹配徽标、任务恢复、群发 banner。reader 应在这些调用前分支，不能等 403 再藏界面。
- `app.js:20739` 的群推visibilitychange会绕过refreshCurrentView单独发请求，03必须一起阻断；原文在14。
- `app.js:21081` stop 目前没有邮件只读视图；`:21211` logout 会先 flush 草稿。reader 要独立销毁且跳过 flush。
- `F/mailbox-chat.js:3460–3557` selectExpert 同时读备注、挂起、位置、联系人详情、messages，并在失败时恢复缓存；`:4790–4855` message 带附件/回复等入口；`:9777–9810` attach 注册工作流事件。不能把现有组件的 draft `rec.readOnly` 误当用户权限。
- `mailbox-chat.js` 开头契约及 04/09：会话缓存按 user/account/contact 隔离，但 reader 撤销权限不应继续恢复正文缓存。新只读模块仅保留当前页面内存，切专家重新请求，失败清空。（来源 K-mailbox-draft-cache-owner-capture）
- `app.js:4849–4880` api 错误已有 status/data，可区分 401/403，且超时可选；不能 `.catch(()=>null)` 后当成功旧缓存。
- `F/world-clock.js:656–670` 只有打开面板才 loadCatalog；`:1048–1086` 自动挂载。reader 隐藏触发器/面板，网络白名单仍在服务器；不开放 sender-account/location 查询。

样式必须按 CSS 后置覆盖后的有效值判断，不只复制文件开头：

|组件|精确来源与值|
|---|---|
|品牌|styles.css:3–5：#1e40af / hover #1e3a8a / active #172554；:15 panel-bg rgba(255,255,255,.55)，不能用于新增不透明弹窗|
|双栏|mailbox-chat.css:1：306px + minmax(0,1fr)，gap16，height calc(100dvh - 216px)；:150 在 ≤1100px 左栏275px/gap12；:70,151 在 ≤760px 上下排列|
|面板|:5：border 1px rgba(15,23,42,.11)、radius14、#f8faff|
|列表卡片|:16–22：radius10；hover #eff5ff；active 边框#c2d3f2/左边#3c65cd/背景#eaf1ff|
|头部|:34、:102、:104：最终白底、padding17px 22px 14px、标题17px/600、letter-spacing -.25px；移动断点见:150|
|气泡|:43–47、:113–115：最终 width min(92%,820px)、padding14px17px、radius11、shadow 0 2px 6px #334b7210；发件底色#eff5ff；正文13px/1.55/#465974|
|按钮|styles.css 中 .button 规则原文见14；mailbox-chat.css:65–68 disabled opacity .45、focus outline2px #3b82f6；文本按钮:109–112含 hover/active/disabled|
|空态/错误|:58 mc-empty padding40px20px/#64748b；:59 mc-error #fff1f2 边框#fecdd3/文字#be123c|

本版复用现有 class，禁止修改这些规则；03 只添加明确的 reader 隐藏规则，04 不加 CSS。无需新增模态框，因此 K-panel-bg-token-is-translucent 作为禁止误用事实保留，不新增弹窗。

## 测试与已知限制

- 补充回执[18](user1-mail-evidence/18-static-resource-mapping.txt)：addResourceHandler/static-locations/ResourceHttpRequestHandler/字面file:配置检索在声明范围无命中（exit=1），不据此推断线上反向代理配置。发布前核对线上未把附件目录直接公开。
- 补充回执[19](user1-mail-evidence/19-test-gates.txt)：`MailboxConversationControllerTest:121`及同文件`CalendarAttachmentIntegrationTest:1969`、`MailReplyDraftSendIntegrationTest:59`均受mysqlIt门控；`AuthFlowIntegrationTest:46`受**migrationIt**门控且创建MySQL 8.0.36 Testcontainer。前者用`-Pmysql-it`，后者用`-Pmigration-it`；默认mvn test不代表这些类运行。
- 新迁移必须随全新库完整迁移链成功，不能只验证已有库增量；本轮不执行任何flyway repair。来源K-flyway-gate-must-pass-on-fresh-db。历史知识中的Docker socket/API参数不是本次已证实运行环境，执行前检查当前Docker连通性。

- `pom.xml:193–202` Maven test 阶段已有 `node --test src/test/js/*.test.js`；不能只跑某个包装脚本宣称全覆盖。（来源 K-js-test-invocation-surface）
- `MailboxConversationRepositoryIT.kt:54` 受 mysqlIt 门控；`pom.xml:250–252` 的 `-Pmysql-it` 才开启。必须显式选中 IT 类并用隔离 MySQL，禁止连接线上。默认 mvn test 通过不证明 SQL ACL 通过。
- `AuthFlowIntegrationTest.kt:114,149` 每例清空用户，并在特定测试只运行 admin bootstrap 后断言 1 个用户；不能为了新账号机械改成 2。新增双 bootstrap 场景应自行准备数据。
- 08 显示 mail/reply/attachment 测试直接构造现有 Controller/Service；本计划保留它们构造参数，新增方法参数放末尾并给默认值以保留内部调用兼容。HTTP 边界仍必须传实际 session。
- `OperatorStatusWriteSeamGuardTest` 钉住部分旧仓库/服务行号；本版不改 MailRecordRepository/ExpertContactRepository/MailboxService，避免无关行号变更。（来源 K-line-number-guard-breaks-on-any-insertion）
- DOM stub 会伪造不存在元素；03 要用真实 index.html 做 DOM 契约检查和浏览器行为验收。（来源 K-dom-stub-tests-hide-dangling-refs）
- 未执行开发测试，未查线上数据，未证明线上版本与本地一致。执行阶段先核对源码摘要、最新迁移号与真实环境配置；发生漂移更新计划证据，不能拿本次行号硬套。
