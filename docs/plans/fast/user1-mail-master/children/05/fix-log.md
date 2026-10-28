# Child 05 Fix Log

## Epoch 1 — Round 1/3
- Findings: F-1
- Before: c03ece5900ce45835da8c449a8fb5aef69d47ffd
- Fix commit: 7750a720b6fa276eb6e79ee468e40b7be98c7fdc
- Authorized files changed: `src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt`; evidence `docs/plans/fast/user1-mail-master/children/05/execution.md`（行 #5/#6、I-4、D-1/D-2 计数，未进 fix 提交）
- Commands:
  - `DOCKER_API_VERSION=1.40 JAVA_HOME=<zulu-11> mvn -B -Pmigration-it -Dtest=AuthFlowIntegrationTest test` → exit 1；`Tests run: 8, Failures: 0, Errors: 8, Skipped: 0`；8/8 根因 `BeanDefinitionOverrideException: 'attachmentTransferWorker' ... WorkerLifecycleTestConfig`（预存上下文失败已可复现）
  - `DOCKER_API_VERSION=1.40 JAVA_HOME=<zulu-11> mvn -B -Pmigration-it -Dtest=AuthFlowIntegrationTest -Dspring.main.allow-bean-definition-overriding=true test` → exit 0；`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`；真实 Testcontainers `mysql:8.0.36` 容器启动
  - `JAVA_HOME=<zulu-11> mvn -B -Dtest=ReadOnlyUserBootstrapTest,AdminUserBootstrapTest,AuthControllerMvcTest,AuthInterceptorTest test` → exit 0；`Tests run: 43, Failures: 0, Errors: 0, Skipped: 0`（6/4/14/19）
  - `git diff --check` → exit 0（无输出）
- Result: FIXED
- Notes: 仅删除 `@TestPropertySource` 内联的 `spring.main.allow-bean-definition-overriding=true` 及 A5 注释；A5/A8 `@Primary` DataSource/JdbcTemplate/NamedParameterJdbcTemplate/transactionManager 与 `@MockBean ExpertContactLocationCatalog` 原样保留，产品代码未动。实际起点 HEAD 为 c03ece5（`docs(fast-p): broaden 05 AuthFlow test wiring to complete set`），非 context 所记 2553a10；两者仅差该文档提交。原命令行 #5 的旧计数 `2 errors` 系新增 6 例之前的口径，已按本次观测改为 `8 errors`。
