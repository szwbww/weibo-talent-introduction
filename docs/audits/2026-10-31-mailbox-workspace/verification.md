# 收发件箱读写工作区修复验证

日期：2026-10-31。直接修改 main；本轮未提交、未部署。工作区原有文档、配置修改保留。

## 实现范围与代码入口

- user1 页面标题修正为收发件箱；列表增加草稿、可读、可写筛选及权限标记。`app.js:21253`、`mailbox-readonly.js:205`。
- FULL 用户使用原 MailboxChat 的可信回复工作台、人工回复、操作日志三个面板。人工回复默认展开，其余默认折叠；保留原卡片样式。`mailbox-chat.js:5703`、`:5745`、`:5805`。
- FULL 普通草稿复用 admin 自动保存、继续编辑、放弃、直接发送路径；仅 READ 使用提交审核流程。保存、增删后刷新外层草稿列表及数量，保留正在编辑的节点。`mailbox-chat.js:2336`、`mailbox-readonly.js:566`。
- admin 普通回复隐藏审核选择栏；有审核草稿时出现小入口，待审核数量使用服务端会话统计，支持待审核草稿位于后续分页。选中审核稿仍禁止编辑；批准前没有发送按钮；对话更新确认继续复用原路径。`mailbox-chat.js:5864`、`:5923`、`:5974`。
- 主题默认折叠，保留真实邮件主题字段；没有草稿时隐藏空编辑控件。`mailbox-chat.js:6293`、`mailbox-readonly.js:347`。
- user1 管理框只保留状态、层级、标签；隐藏完整专家资料入口。READ 标签仍可见但没有编辑入口。admin 权限选项缩短为 `user1: 无 / 读 / 写`。
- 修复 delegated host 缺少高度约束导致编辑区失去可用滚动边界；编辑区最小高度 160px。`mailbox-chat.css` 末尾工作区规则。
- 草稿筛选、分页、数量与导航使用同一当前用户/当前授权 SQL：FULL 仅普通草稿，READ 仅审核草稿，不混入 admin 草稿。UNION 字符列统一 utf8mb4_unicode_ci，兼容两个既有表的不同排序规则。`MailboxConversationRepository.kt:840`。
- 工作台生成按真实 LIVE_INBOUND 所属专家校验 FULL；日志查询必须带 expertContactId，回复框架查询必须带 contactId 并校验 FULL。READ/NONE/撤权拒绝；没有开放日志全库查询或框架写入。`ExpertMailPermissionGuard.kt:116`、`RagReplyController.kt:52`。
- 无数据库迁移；未修改待处理归属规则、审核状态机、真实邮件发送链路。

## 自动回归

1. 全部前端测试：`node --test --test-force-exit --test-timeout=30000 --test-reporter=tap src/test/js/*.test.js`：**2013 通过，0 失败，0 取消**。见 frontend-result.txt。
2. Java 11.0.33 下 `mvn -o test -DskipNodeTests=true -DmysqlIt=true -DargLine=-Dapi.version=1.44 -Dtest=MailboxReadAccessProjectionTest,ExpertMailPermissionGuardTest,MailReplyDraftControllerTest,MailReplyDraftServiceTest,MailReviewDraftIT,DelegatedWorkbenchAccessTest`：**52 通过，0 失败/错误/跳过；BUILD SUCCESS**。见 backend-result.txt。
3. 上述包含完整主代码/测试编译、Testcontainers 独立 MySQL、从空库迁移及 V168 升级验证。草稿查询覆盖所有者、实时权限、分页、计数、撤权；工作台/日志覆盖 READ/FULL/NONE。
4. 最新资源打包：`mvn -o resources:resources war:war -DskipTests` 成功。沿用第 2 步已编译类；不是另一次全量测试。见 package-result.txt。
5. 修改的四个 JS 文件 `node --check` 通过；`git diff --check` 通过。

初次隔离 SQL 测试发现 UNION 排序规则冲突，已修正并重新执行上述完整目标集。早期运行还遇到 Docker 未启动、旧 Java/Testcontainers Docker API 兼容问题；最终使用本机 OrbStack 与 Java 11.0.33。需要外部业务 JDBC 配置的 MailboxConversationControllerTest 未作为成功验证项；未连接业务库。未运行整个 Kotlin 测试库或线上收发邮件测试。

## 浏览器实测

使用当前 main 的真实 index.html、JS、CSS，由 127.0.0.1:8770 提供；接口为本机合成测试数据，写接口返回 405。截图不是线上部署结果。

- user1 只显示收发件箱，标题正确；可读/可写标记可见。
- FULL：人工回复默认展开、主题默认折叠；工作台、日志可打开；普通回复没有审核控件。
- READ：没有直接发送和管理入口；保留草稿历史及新增草稿，邮件标签只读。
- user1 管理框：仅状态、层级、标签，无完整资料面板。
- admin：三个原面板保留，普通人工回复默认展开，无审核选择栏；权限下拉为无/读/写。
- 1280×720 默认浏览器视口：delegated host 与内部工作区高度均为 504px，正文编辑区 160px，内部邮件滚动区受约束。user1 路径未发现 console error/warn。

截图：user-full.jpg、user-management.jpg、admin-manual.jpg。窗口高度有限，人工回复其余内容通过右侧内部滚动访问。
