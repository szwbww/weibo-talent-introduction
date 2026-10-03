# 01 基线命令结果（控制方记录；代码态 = 基线提交 9d7e389→seed c486c5c，与 revision-v6-sha256.txt 全部一致）

环境：JDK11.0.32（zulu）；本地容器 MySQL `ti-mysql-it`（localhost:3306，root/root，库 `talent_introduction`）；Docker = OrbStack。

| # | 命令（worktree 根） | 结果 |
|---|---|---|
| B1 | `mvn -DskipTests test-compile` | exit 0，BUILD SUCCESS（1:54） |
| B2 | `DB_URL="jdbc:mysql://localhost:3306/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root mvn -DmysqlIt=true -Dtest=MailboxConversationRepositoryIT,MailboxConversationRepositorySqlCompatTest,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest test` | exit 1；Tests run: 57, Failures: 0, Errors: 6 |
| B3 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -DmigrationIt=true -Dapi.version=1.40 -Dtest=FlywayMigrationIntegrationTest test` | exit 1；Tests run: 34, Failures: 25, Errors: 0 |

## 已知基线失败（实施后必须对照；不是本次引入）

### B2（6 errors）

- `MailboxConversationRepositoryIT.replied filter excludes followed and dismissed experts until a new inbound arrives`（`MailboxConversationRepositoryIT.kt:225`）：`DataIntegrityViolationException: Field 'dismissed_at' doesn't have a default value`。V143 表 `expert_replied_dismissal.dismissed_at DATETIME NOT NULL` 无默认值，测试 INSERT 省略该列（MySQL 严格模式）；与挂起功能无关。
- `CalendarAttachmentIntegrationTest` 全部 5 个用例：`Failed to load ApplicationContext` → `No qualifying bean of type PendingMailOperationService`（同文件第二个类的 WebMvc context 未 mock 该依赖；`MailboxConversationControllerTest` 已 mock 且 28/0/0 全绿）。既有问题。

（对照：MailboxConversationRepositoryIT 22 run/1 error、SqlCompat 2/0/0、MailboxConversationControllerTest 28/0/0。）

### B3（25 failures，全部 `expected: <145> but was: <146>`）

`FlywayMigrationIntegrationTest` 25 处 latest-target 断言写死 `"145"`，仓库真实最高迁移 V146 → migrate 后 `targetSchemaVersion=146`。本片按计划 T4 统一改为 `147`（V147 新增后）；历史 `target=xx` 断言不动。失败用例：

| # | 用例（行） |
|---|---|
| 1 | V140 adds nullable reuse origin without changing existing verification results (59) |
| 2 | V142 preserves old config as false and stores enabled historical exclusion (87) |
| 3 | V145 adds a nullable allow list column without backfilling old configs (129) |
| 4 | fresh database migrates through the latest version (169) |
| 5 | V141 upgrades V140 history without backfilling and enforces unique association (179) |
| 6 | V136 preserves existing template subject and initializes reference as null (212) |
| 7 | V127 adds nullable outbound attachments json column leaving history null (300) |
| 8 | V129 widens the material code check to twelve codes without touching stored rows (342) |
| 9 | V130 adds the manual upload owner table switching the owner check to three-way (439) |
| 10 | V131 creates the enrichment job table on a V130 database leaving existing contacts untouched (612) |
| 11 | V135 adds the sender account codes column backfilling existing rows with an empty array (734) |
| 12 | V138 creates the batch email verification table with its keys and cascade (773) |
| 13 | V139 adds the email verification switch backfilling existing configs with false (835) |
| 14 | V123 adds nullable calendar attachment json column leaving history null (935) |
| 15 | V124 allows material attached promotion audit trigger (970) |
| 16 | V117 clears only BOUNCE_RATE_HIGH pauses and keeps other pause reasons (1081) |
| 17 | database at original V23 upgrades to V122 without repair (1188) |
| 18 | database at original V24 upgrades to V122 without repair (1204) |
| 19 | V23 historical records are linked and SENT quota is backfilled (1239) |
| 20 | ambiguous V23 data fails before persistent V24 DDL and can rerun after repair (1285) |
| 21 | V118 allows metadata-only attachments preserving historical values and owner XOR (1361) |
| 22 | V119 creates mail_attachment_transfer with the persistence contract (1477) |
| 23 | V120 scopes inbound uid uniqueness by uid validity without backfilling history (1622) |
| 24 | V121 creates expert_follow with composite ownership key and contact FK (1712) |
| 25 | V122 does not overwrite a pre-existing manual meeting template configuration (1889) |

环境注记：第一次 B3（不带 workaround）在容器探测阶段报 `client version 1.32 is too old`（docker-java vs OrbStack API ≥1.40）；按仓库既有 documented workaround（`docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c7/execution.md`）加 `DOCKER_HOST`+`-Dapi.version=1.40` 后可正常启动 testcontainers。原始日志：/tmp/fastp-baseline-testcompile.log、/tmp/fastp-baseline-it.log、/tmp/fastp-baseline-migration3.log。

## 基线未运行

- `MailboxSuspensionServiceIT`（本片新增，基线不存在）。
- 变更后的三条命令（B2 增加新测试类；B3 期望迁移到 V147）。
