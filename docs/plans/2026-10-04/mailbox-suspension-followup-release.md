# 生产发布记录

2026-10-04，用户明确授权「直接修改 并上线」。代码提交 aa233a6；构建来自干净隔离工作区。本文件为发布后证据，不改变构建代码。

- 生产：http://150.158.92.103/talent/
- WAR SHA256：d702b44dbb52c4679ac52ddb948bf333ab1ab1a48db83fcec33d7a12219fca08；上传、部署文件一致。
- 备份目录：/opt/apache-tomcat-9.0.71/deploy/backups/mailbox-followup-aa233a6-20261004；包含旧WAR、旧展开目录、关闭/启动日志。
- 关闭监听后旧Java PID 7121未自行退出；TERM正常终止后继续发布。无强杀、无DB迁移或业务数据写入。
- HTTP首页200，包含11个20261004-mailbox-suspension-followup资源版本；公网styles.css、mailbox-chat.js与构建源cmp一致。
- 新pending-badge匿名访问401，认证保护正常。浏览器到达生产登录页，当前无登录Session，未执行真实业务变更验收。
- 同一代码独立测试页面完成六Tab布局、原因添加/保存验收；截图/tmp/mailbox-suspension-followup-verified.png（测试数据）。接口认证/用户隔离/计数及编辑已由42项真实本地MySQL测试覆盖。
- 代码已快进同步主工作区，原55个已修改跟踪文件逐字校验保持不变；未推送远端。
- 发布日志：/tmp/mailbox-followup-deploy.log、/tmp/mailbox-followup-deploy-resume.log。

本次无遗留实施项；线上登录后实际业务场景需用户验收。
