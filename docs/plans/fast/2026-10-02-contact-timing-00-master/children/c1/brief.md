# Child Brief — c1（子计划 01：人工所在地持久化与国家时区目录）

本 brief 是 c1 的唯一执行契约入口；计划文件是逐字实现契约，本 brief 只补充环境绑定与流程约束。

## 任务与身份

- 工作区（唯一允许改动的位置）：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-02-contact-timing-00-master`
- 分支：`fast/2026-10-02-contact-timing-00-master`
- child_base_sha：`d9b237d7a018e288b87fcbb270e70bcffdcb5917`
- 批准计划：`docs/plans/2026-10-02/contact-timing-01-location.md`（已按 fast-p ledger A1 将迁移号从 V145 改为 V146，以计划文本为准）
- 只读证据（按需查阅，不要修改）：`docs/plans/2026-10-02/contact-timing-evidence/`（`grep-receipts.md`、`code-baseline.md`、`country-timezones.proposed.json` 等）
- 执行报告（你写，**不要提交**）：`docs/plans/fast/2026-10-02-contact-timing-00-master/children/c1/execution.md`
- 必须使用 `execute-p` 流程；只允许一个本地实现提交，提交信息：`feat(fast-p): implement c1`

## 授权文件（9，见计划「变更文件清单」；迁移号为 V146）

1. `src/main/resources/db/migration/V146__create_expert_contact_location.sql`（新增）
2. `src/main/resources/contact-country-timezones.json`（新增，逐字复制证据快照）
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationModels.kt`（新增）
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationCatalog.kt`（新增）
5. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationService.kt`（新增）
6. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationController.kt`（新增）
7. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationServiceTest.kt`（新增）
8. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationControllerTest.kt`（新增）
9. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationServiceIT.kt`（新增）

不得修改列出的既有文件与共享表（含 `ExpertContactRepository`、`MailRecordRepository`、`MailboxService`、`expert_contact` 表本身）；不得新增第十个文件。

## 环境绑定（2026-10-02 基线事实，已记录于 baseline/env.txt）

- **JDK**：本 run 所有计划命令中的 `JAVA_HOME` 使用 `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`（Zulu 11.0.32.1 / tzdb 2026b）。
  - 系统 `/Library/Java/JavaVirtualMachines/zulu-11.jdk`（11.0.15 / tzdb 2021e）缺 `America/Ciudad_Juarez`、`America/Coyhaique`、`Europe/Kyiv`，目录加载必然失败，禁止用它执行本 child 命令。
  - 该 JDK 已验证：418/418 目录 id 可 `ZoneId.of`；2026-07-01 偏移 Mexico_City −06:00、New_York −04:00、London +01:00。
  - 总计划 G-0 的 ≥2026c 版本线仍未满足（可获得的 JDK 11 GA 最新为 tzdb 2026b）；实现照常进行，但**不得宣称时区验收通过**。
- **MySQL IT**：容器 `ti-mysql-it` 已启动（127.0.0.1:3306，root/root，OrbStack）；独立库 `talent_contact_timing_it` 已创建。不得对 `talent_introduction` 或任何业务库执行清理 fixture；只操作 `talent_contact_timing_it` 或测试代码自建的数据。
- **Node**：v25.7.0（本 child 可能不需要）。

## 必需命令（逐字执行，仅 JAVA_HOME 按上文绑定）

1. 单测：
   `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ExpertContactLocationServiceTest,ExpertContactLocationControllerTest,OperatorStatusWriteSeamGuardTest`
2. MySQL IT：
   `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Pmysql-it -Dtest=ExpertContactLocationServiceIT -Dspring.datasource.url='jdbc:mysql://127.0.0.1:3306/talent_contact_timing_it?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'`

不要运行全量 `mvn test`（基线已由 controller 记录；全量由后续阶段统一跑）。

## 基线事实（避免误判）

- 基线 `mvn -B test`、`node --check`、`node --test src/test/js/*.test.js` 结果见 `docs/plans/fast/2026-10-02-contact-timing-00-master/baseline/`。
- `mvn test -Dtest=ExpertContactLocationServiceTest,...` 在基线上因新测试类不存在而无法匹配，属预期；本 child 必须让该命令真实通过。
- `OperatorStatusWriteSeamGuardTest` 以精确行号白名单守护若干文件；本 child 不修改这些文件，但若测试因**本 child 之外**的既有行号漂移失败，记录到 execution.md，不要顺手改无关文件。
- IT 命令中 `-Dspring.datasource.url` 的传播性已由 controller 探测并记录在 baseline（见 `baseline/mvn-it-probe.txt`）；若实际未传播到测试 JVM，IT 应保证自己连接到 `talent_contact_timing_it`（例如测试内用 `@DynamicPropertySource`/`@TestPropertySource` 固定该库），并在报告中说明。

## 必须保持的不变量（计划为准，摘要）

- I-1：`expert_contact_id` 主键、每 contact 至多一行；无行=未配置，不得猜国家。
- I-2：`country_code` 只接受当前目录内大写 ISO 二字码（trim 后大写再校验）；未知 400，不写入。
- I-3：`zone_id IS NULL` 表示用目录 defaultZoneId；显式值必须属于该国 zones；换国家校验/清空旧显式时区；空字符串归一为 null。
- I-4：唯一业务写入口 PUT→service 参数化 upsert；未登录 401、非法 400、不存在 contact 404；校验失败不写任何表。
- I-5 / N-1：不改 `expert_contact.country`、ES、回填、发信、排期、晋级；国家统计口径不变。

## 下游接口（c2/c3 将逐字复用，必须冻结）

- `GET /api/mail/contact-locations/countries`：只读目录。
- `GET /api/mail/contact-locations/{contactId}`：未配置返回 200 `configured:false`，不创建占位行。
- `PUT /api/mail/contact-locations/{contactId}`：body `{countryCode, zoneId:null|string}`；返回已持久化配置。
- `ContactLocationView` 字段冻结：`contactId, configured, countryCode?, countryLabel?, zoneId?, effectiveZoneId?, zoneLabel?, usingDefaultZone`；未配置时后三个 zone 字段为 null 且 `usingDefaultZone=false`；已配置 `usingDefaultZone=(zoneId==null)`。

## 返回

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要（命令→退出码/计数）、报告路径。不要 push、merge、rebase、amend、reset，不要审查后续 child，不要修复计划外行为。
