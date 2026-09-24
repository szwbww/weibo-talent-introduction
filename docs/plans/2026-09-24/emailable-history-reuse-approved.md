# 已授权补充：跨执行复用邮箱验证一年

授权依据：用户确认 main 已合并原功能，批准“过期时间设置为一年，你开始修改吧”。本文件记录本轮会话方案与该明确修订，不修改已执行的原计划。工作树 `/Users/lukai/IdeaProjects/weibo-talent-introduction`，分支 main；不提交、不部署。

## 合同

- R1：同一规范化邮箱跨任务/执行/进程复用最近一年内最新的原始 PASS/SKIP；只有供应商已完成且状态匹配的真实请求结果有效。ERROR/PENDING、未来时间、过期、缺时间不复用。当前时间按 Asia/Shanghai，以 now.minusYears(1) 为严格下界。旧结果无新增关联字段亦可用。
- R2：每个目标仍留本次审计。新增 reused_from_id 指原始结果ID，复用 requestCount=0、原checkedAt不变。不复制发送/标签结果，继续既有发送门禁和当前专家标签处理。同执行复用也指原始ID，不能产生链式续期。
- R3：缓存未命中才请求；查询或复用审计失败则停止，不靠重复付费请求掩盖DB故障。保留原密钥/取消/发送预占边界。
- R4：现有90天清理存在FK级联；仅保护仍含一年有效原始验证结果的执行记录。其它执行/进度清理不变；到期可正常回收，复用行不会延长原结果期限。不新建表、Redis、调度或ES字段。
- R5：现有日志详情新增原始结果关联及“复用历史验证”文字，保留原时间和请求次数0；所有新文本转义。仅复用已有details/div，无新CSS或布局。历史requestCount=0但无关联字段显示“复用验证结果（原始记录未关联）”，不误称新请求。

## 已授权实现范围

1. src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt
2. src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt
3. src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt
4. src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt（满足一年保存所需的清理排除条件）
5. src/main/resources/db/migration/V140__reuse_batch_email_verification.sql
6. src/main/resources/static/app.js（保留既有未提交改动）
7. src/main/resources/static/index.html（统一缓存版本）
8. src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt
9. src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt
10. src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt
11. src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt
12. src/test/js/batchEmailVerification.test.js

## 任务与验证

- T1/R1-R3：仓储邮箱+checked_at+id查询索引，nullable reused_from_id（不加自引用级联），复用决定原子写入，服务内存→历史→HTTP。改旧“跨执行必须请求”测试，覆盖PASS/SKIP/ERROR/取消/查询故障及来源ID与原时间。
- T2/R4：保留清理只保护有效原始验证记录的父执行；真实MySQL验证90天以外仍可查、满一年删除、无结果/只有ERROR/只有复用行仍按原规则删除，分页展示不变。
- T3/R5：DTO/日志显示原验证关联；旧数据兼容、XSS回归；资源统一bump，无CSS修改。

执行命令（项目Java11）：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchEmailVerificationServiceTest,BatchSendExecutionDetailTest,TaskAuditRetentionServiceTest,TaskRetentionMigrationTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchEmailVerificationRepositoryIT,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests package
```

人工验收：同一邮箱新执行显示复用历史验证、原时间、请求0；PASS继续旧发送判断，SKIP不发并给当前专家补标签。测试库旧原始记录改为一年以前后再次执行须调用一次HTTP；改为100天前并跑清理，仍应复用。全程只用测试HTTP/SMTP桩，生产收费验证和邮件不由本轮执行。

交付执行证据到同目录 emailable-history-reuse-execution.md，含plan/worktree identity、命令实际结果、相对本轮基线差异。不将独立验证PASS与执行自查混同。
