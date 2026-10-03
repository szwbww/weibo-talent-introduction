# 挂起后续三项调整
授权：2026-10-04用户同意本会话三项方案，并明确“直接修改 并上线”；覆盖旧计划原因不可编辑及旧banner文案。
TARGET_WORKTREE: /Users/lukai/.codex/worktrees/mailbox-suspension-followup/weibo-talent-introduction
基线2d17f93；生产WAR SHA256=8b5a3219b13a5cc60f1dbe41c10640a0551b995814e4795f2e7ad596c2c169aa。

## 任务
F1 顶部角标仍按待处理邮件数分高优先/普通计数，只排除当前用户挂起专家；未匹配及禁用真实账号仍计入。新增GET conversations/pending-badge一次SQL分组取总数和原因分类；旧队列不改。挂起/取消成功即时刷新，零隐藏，过期响应丢弃。
F2 桌面左栏380px，六Tab完整显示；窄屏换行，保留原移动导航。仅styles.css追加规则，旧冻结CSS不改。
F3 banner删除未填写原因与引导行；非空才显示原因；添加/编辑原因按钮使用原位表单，预填、取消/保存、失败保留。新增PATCH conversations/{id}/suspension/reason只更新当前Session已有挂起行reason；无挂起409，不创建或取消；trim空转null，500单位上限。PUT幂等不变，零待处理亦可编辑；保留异步会话守卫、最后消息下方结束确认。

## 审计锚点
app.js refreshUnmatchedBadge使用旧全量队列；MailboxSuspensionService.suspend遇到已有行原样返回；mailbox-chat.js suspensionBannerHtml固定两行small；mailbox-chat.css默认306px/窄桌面275px。挂起表仅username/contact主键及reason，本次无新增字段/迁移。

## 文件范围（10）
- src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt
- src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt
- src/main/resources/static/app.js
- src/main/resources/static/mailbox-chat.js
- src/main/resources/static/styles.css
- src/main/resources/static/index.html
- src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt
- src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt
- src/test/js/mailboxSuspension.test.js
- src/test/js/mailboxSuspensionFollowup.test.js

## 验收/发布
新增测试先失败后修复：身份隔离、未匹配计数、原因空值/长度/取消后编辑拒绝、行内取消/保存/失败/迟到响应。node --check检查两个JS；node --test src/test/js/mailboxSuspension.test.js src/test/js/mailboxSuspensionFollowup.test.js src/test/js/mailboxSuspensionStyle.test.js。
JDK=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home；独立本地测试库执行mvn -DmysqlIt=true -Dtest=MailboxSuspensionServiceIT,MailboxConversationControllerTest test；mvn clean package包含全量JS；git diff --check；构建/生产tzdb探针。
按用户发布授权，为满足require_clean_worktree可提交本隔离分支本任务文件（不推远端）；备份、SHA校验、Tomcat发布、HTTP/浏览器验证，不使用真实业务写入测试。不提交原工作区其它改动。记录执行/发布证据到本目录；完成后同步本任务代码回主工作区，保留其它改动。
