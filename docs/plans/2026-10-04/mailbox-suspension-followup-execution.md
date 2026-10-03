# 执行证据

计划SHA256：1731571f698cde225d97a8d0c2fc33738f32c04f7b325cef86f87c3080f16e34。
基线：2d17f935619e8d03fa65c75a65983786a11d7266；分支：codex/mailbox-suspension-followup。

F1/F2/F3已实现，文件严格限于计划10项及计划/证据文档。无迁移，无生产业务数据写入。
- node --check app.js、mailbox-chat.js通过。
- 新测试先失败后修复；聚焦JS49/49通过。
- 独立MySQL库 mailbox_followup_20261004：mvn -DmysqlIt=true -Dtest=MailboxSuspensionServiceIT,MailboxConversationControllerTest test：42通过，0失败。
- JAVA_HOME=Zulu11.0.32.1 mvn clean package：BUILD SUCCESS；后端4542测试，0失败/错误，13跳过；JS1434/1434通过。
- 构建与生产JVM tzdb探针：418目录无缺失，532可选时区全部覆盖，10/10偏移断言通过。
- git diff --check通过。
- 浏览器独立数据实测：桌面grid首列380px；六Tab右边界365px，全部可见；添加原因后行内保存成功，显示原因与编辑按钮；空原因无占位/引导文案。窄屏flex-wrap为wrap；未改原移动导航。
- SQL及写入审阅：当前Session隔离；真实账号含禁用账号，排除模拟与孤儿账号；未匹配保留；PATCH与DELETE共享contact锁，不重建挂起；500上限/空转null。
- 仅修正角标读取与原因写入，不更改旧待匹配队列、已回复/关注规则或结束挂起确认。

构建WAR SHA256：d702b44dbb52c4679ac52ddb948bf333ab1ab1a48db83fcec33d7a12219fca08。
原生产WAR SHA256：8b5a3219b13a5cc60f1dbe41c10640a0551b995814e4795f2e7ad596c2c169aa。
发布前工作区需干净；备份、健康检查、线上浏览器证据另记发布文档。
测试日志：/tmp/mailbox-followup-{red,focused,mysql,package}.log。
