# Fast-P Baseline — master: docs/plans/2026-10-04/discovery-review-master.md

代码态：seed 提交 `07beaafc111a1b14ed3c48d514db527c8a13fc31`（= master base `e28e53fd898edd62905a0d45a6bf90396b18b1bf` + docs/plans-only seed）。

环境：JDK11（`/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`，11.0.15）；Node v25.7.0；本地容器 MySQL `ti-mysql-it`（localhost:3306，root/root，库 `talent_introduction`）；Docker = OrbStack；localhost:9200 无本地 ES。

| # | 命令（worktree 根） | 结果 |
|---|---|---|
| B1 | `mvn -DskipTests test-compile` | exit 0，BUILD SUCCESS（1:56） |
| B2 | `node --test src/test/js/*.test.js` | exit 0；tests 1434 / pass 1434 / fail 0（3.6s） |
| B3 | `mvn -Dtest=DiscoveryIdentityTest,LegacyDiscoveryApprovalTest,ExpertDiscoveryServiceTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest,ExpertIndexWriterServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test` | exit 0；Tests run: 465, Failures: 0, Errors: 0 |

原始日志：/tmp/fastp-dr-baseline-testcompile.log、/tmp/fastp-dr-baseline-js.log、/tmp/fastp-dr-baseline-unit.log。

## 基线未运行（各 child 执行时按计划命令 fresh 运行并以本表对照）

- 新增测试类（`DiscoveryAdmissionPolicyTest`、`DiscoveryReviewServiceTest`、`DiscoveryReviewRepositoryIT`、`DiscoveryReviewControllerTest`、`DiscoveryReviewAllPagesTest`、`BatchRecipientSelectionServiceTest`、`BatchTemplateGateParityTest`、`discoveryReview.test.js` 等）：基线不存在。
- `FlywayMigrationIntegrationTest`（opt-in `-DmigrationIt=true`，需 testcontainers）：本 run 未运行；新增 V148 后其 latest-target 断言可能过期，属 RECORD_ONLY 观察项（该文件不在任何 child 授权清单）。
