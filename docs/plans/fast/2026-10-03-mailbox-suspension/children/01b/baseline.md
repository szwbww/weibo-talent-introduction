# 01b 基线命令结果（控制方记录，基线提交 9d7e389→c486c5c 代码态）

命令（worktree 根，JDK11.0.32；与 migration IT 同批运行，`-DmigrationIt=true` 仅影响 Flyway 测试的启用）：

```sh
DB_URL="jdbc:mysql://localhost:3306/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest,PendingMailOperationServiceTest,UnmatchedInboundTrustWorkbenchTest test
```

| 类 | 结果 |
|---|---|
| PendingMailOperationServiceTest | Tests run: 37, Failures: 0, Errors: 0, Skipped: 0 |
| UnmatchedInboundTrustWorkbenchTest | Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 |
| UnmatchedInboundMarkResolvedIdentityTest（新增，本片创建） | 基线不存在（新文件） |
| FlywayMigrationIntegrationTest | 同批运行中因 Docker/API 环境问题单独记录于 children/01/baseline.md，与本片无关 |

结论：01b 相关的两个既有测试类基线全绿；新测试类尚无基线。
