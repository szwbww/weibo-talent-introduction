---
id: K-flyway-placeholder-replacement
domain: common
created: 2026-08-11
last_used: 2026-08-11
hit_count: 0
source: create-p:unsubscribe-01-body-link
severity: P1
---

经验：本仓库的迁移文件里存在**大量 `${...}`，它们是邮件模板变量（数据），不是 Flyway 占位符**。而 `src/main/resources/application.yml` 的 `spring.flyway` 段**没有**设置 `placeholder-replacement`，Spring Boot 2.7.18 默认为 `true`。

证据（不对称是关键）：

- 含 `${}` 的迁移：`grep -l '\${' src/main/resources/db/migration/*.sql` → `V2`（`${senderEmail}` 等）、`V9`、`V56`、`V71`。
- 所有**真正执行 Flyway** 的测试都显式关闭替换：
  - `src/test/kotlin/.../auth/AuthFlowIntegrationTest.kt:53` — `"spring.flyway.placeholder-replacement=false"`
  - `src/test/kotlin/.../campaign/repository/FlywayMigrationIntegrationTest.kt:343` — `.placeholderReplacement(false)`
  - `src/test/kotlin/.../monitoring/repository/MailRecordRepositoryMonitoringIT.kt:30`
  - `src/test/kotlin/.../llm/service/AiQaExtractionServiceTest.kt:203`
- 生产 `application.yml` 无此设置。

**为什么生产至今没炸**：存量库早已过了 `V2`/`V9`/`V56`/`V71`，Flyway 不会重新解析已应用的迁移。这是"侥幸"不是"安全"。

**后果与正确做法**：任何**新增**含 `${...}` 的迁移（例如往模板正文里塞 `${unsubscribeUrl}`）会在生产启动时抛
`No value provided for placeholder expressions: ${...}`，属**部署即挂**。因此：

1. 新增此类迁移时，**必须在同一次提交内**给生产 `application.yml` 加 `placeholder-replacement: false`。
2. 该配置变更对存量迁移无行为影响 —— 已核实无任何迁移依赖 Flyway 占位符做真实替换。
3. 加完后须有回归测试断言该配置存在（否则后人清理 yml 时删掉它，问题会以"部署即挂"的形式回归）。
4. 空库全量迁移的验证命令：
   `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`
   （`FlywayMigrationIntegrationTest.kt:21` 的 `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")` 使其默认跳过，需本机 Docker。）

不需要 Docker 的替代验证范式：`QaSeedEncodingRepairMigrationTest.kt` 用 `Files.readString(Path.of("src/main/resources/db/migration/V44__....sql"))` 对迁移做**文本断言**。内容类迁移（正文、关键词、种子数据）优先用这个范式，成本低且能进全量 `mvn test`。

关联：[[K-qa-rule-runtime-vs-migration-writes]]（迁移不得覆盖运营运行时改动，CONCAT + NOT LIKE 守卫）。
