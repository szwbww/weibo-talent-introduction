# Verify Log — c1（contact-timing-01-location）

轻量验证报告逐次追加于此；每次报告以 `## Light Verification: <VERDICT>` 开头。

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: c1（contact-timing-01-location）— docs/plans/2026-10-02/contact-timing-01-location.md；brief: docs/plans/fast/2026-10-02-contact-timing-00-master/children/c1/brief.md
Boundary: 9b7c04c98f669e5c3b3e27fe09502a5e90e3eafe..dc5546a6e914c23249a8b7dbadcc977e5df1b7e3（dc5546a "feat(fast-p): implement c1"）
Verifier: C1Verify

### Four Gates

|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git diff --name-status 9b7c04c..dc5546a` = 9 条，全部 `A`（1533 insertions / 0 deletions），路径与 brief 授权文件 1–9 逐条一致（V146 迁移、JSON 资源、Models/Catalog/Service/Controller、3 个测试）；`git status --porcelain -- src/ pom.xml` 为空（无第十个文件、无既有文件改动）；仅 docs 下 ledger.md 被 controller 修改|
|Plan and invariants|PASS|见下「计划与不变量证据」（逐条 I-1～I-5 / T-1～T-4 代码与测试引用）|
|Required commands|PASS|见下「命令」：两条均本次全新执行，退出码 0；基线失败项均已消失|
|Downstream interfaces|PASS|见下「下游接口」：路由、DTO 字段与未配置/默认时区语义与 brief 逐字一致|

计划与不变量证据：

- I-1（身份与存在性）：`ExpertContactLocationService.kt:30-46`（GET 无行返回 `configured=false`，不建占位行、不猜国家）；`ExpertContactLocationService.kt:122-128`（`expert_contact_id` 主键 upsert）；IT 用例 `migration creates...`（:89）、`unconfigured contact reads configured false...`（:133，未配置 0 行 / 两次保存仍 1 行 / DB `zone_id` 为 NULL）、`missing contact is not found and dangling foreign key is rejected`（:229，404 + 悬空 FK 被拒）、`deleting the contact cascades the location row`（:245）。
- I-2（国家代码目录权威）：`ExpertContactLocationService.kt:52-54`（trim→uppercase 后查目录，未知抛 `IllegalArgumentException`）；`ExpertContactLocationCatalog.kt:31-73`（仅从 classpath JSON 加载；code 大写二字码/唯一、zones 非空不重复、default∈zones、逐 id `ZoneId.of`，失败抛确定配置错误；无 UTC 回退、无联网、无第三方库、不解析 `expert_contact.country`）；单测 `catalog matches the frozen evidence snapshot byte for byte`（247 国 / 418 关系 / 资源 SHA-256）、`unknown country and foreign zone are rejected before any write`。资源与 `docs/plans/2026-10-02/contact-timing-evidence/country-timezones.proposed.json` 经 `cmp` 逐字节相同，SHA-256 `c27ad26d63ccc151a82844d757b6494bfa73ba69c772d4f45dc627c6fa6b5480`；jq 独立复核 247 国 / 418 关系 / BR.defaultZoneId=America/Sao_Paulo / BR.zones=16。
- I-3（null 与显式时区不同）：`ExpertContactLocationService.kt:55`（空白→null）、`:93`（`effectiveZoneId = zoneId ?: default`）、`:104`（`usingDefaultZone = zoneId == null`）、`:122-128`（换配置两列一起覆盖，旧显式时区不残留）；IT `unconfigured contact reads...`（:133，DB 存 NULL、Manaus→空白切回默认清空旧显式时区）、`rejected writes leave the previous configuration untouched`（:199，BR/Asia/Tokyo 400 且前值保持）；单测 `blank zone is normalized...`、`country code is trimmed...null zone stays null`（null 不回写展开默认值）。
- I-4（写入闭包与身份）：唯一业务写入口 `ExpertContactLocationController.kt:44-52 → ExpertContactLocationService.kt:50-67`；`Controller.kt:50,54-56` 显式从 Session 取 username（body 中 `username/operatorName` 被忽略，controller 测试断言真实调用参数 `("admin", 42L, …)`）；未登录 401（AuthInterceptor + `Controller.kt:58-60`）；`GlobalExceptionHandler.kt:18-28` 400/404 映射；顺序为 参数校验→list 校验→contact 存在性→单条 `update`（`:52-64`），单测 `blank session username is rejected before validation` 与 `location writes touch exactly the location table` 断言失败分支零写语句。
- I-5 / N-1（既有国家字段与旧写链不动）：单测断言唯一写语句只针对 `expert_contact_location`、参数化、无字面量拼接、不含 `expert_contact`/`operator_status`；IT `manual location and expert_contact country stay independent`（:259，人工配置不改 `expert_contact.country`，模拟画像回填后新配置不变、`mail_record` 行数不变）；`OperatorStatusWriteSeamGuardTest` 1/0/0，被其行号白名单守护的文件 diff 为空。
- T-1 迁移：`V146__create_expert_contact_location.sql:1-9` 与计划 SQL 逐字一致（3 列，无回填/updated_at）；`SHOW CREATE TABLE` 确认 `bigint PK` / `char(2) NOT NULL` / `varchar(64) NULL` / `fk_expert_contact_location_contact … ON DELETE CASCADE` / InnoDB utf8mb4 + COMMENT；IT `migration creates the location table...`（:89）断言列、PK、FK 与 flyway `version=146 success=1`。未编辑 V145。
- T-2/T-3 其余条款：目录顺序即资源顺序（无排序/按偏移去重）；`ContactCountryTimezoneCatalog` 索引属性不参与序列化；PUT 不做乐观锁、回读后返回。

命令（本次全新执行，JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home，Zulu 11.0.32.1 / tzdb 2026b）：

|#|命令|退出码|计数|基线对照|
|---|---|---|---|---|
|1|`mvn test -Dtest=ExpertContactLocationServiceTest,ExpertContactLocationControllerTest,OperatorStatusWriteSeamGuardTest`|0（BUILD SUCCESS）|18 tests / 0 F / 0 E / 0 S（Service 10、Controller 7、Guard 1；surefire 时间戳 2026-10-02 01:36:29）|`baseline/mvn-targeted.txt` exit 0 且仅 Guard 1 项（新测试类当时不存在）；本次无新增失败|
|2|`mvn test -Pmysql-it -Dtest=ExpertContactLocationServiceIT -Dspring.datasource.url='jdbc:mysql://127.0.0.1:3306/talent_contact_timing_it?...'`|0（BUILD SUCCESS）|7 tests / 0 F / 0 E / 0 S（surefire 时间戳 01:39:04）|`baseline/mvn-it-c1.txt` exit 1 "No tests were executed"（实现前预期）|

- 隔离库与参数传播的独立证据：本次运行前删除 `talent_contact_timing_it.expert_contact_location` 及其 flyway `version=146` 行，强制 Flyway 在本命令中重新应用 V146 —— 运行后该行 `success=1`、`installed_on`（UTC 17:39:03）落在本命令时间窗内（NOW 17:39:14）；同期 `talent_introduction.flyway_schema_history` version=146 计数仍 0 且 `talent_introduction.expert_contact_location` 不存在，证明 `-Dspring.datasource.url` 真实传播到测试 JVM 且未触碰业务库。
- 运行后状态：隔离表 0 行，seed contact/campaign/sender 已清理；容器 `ti-mysql-it` Up。

下游接口（c2/c3 逐字复用）：

- `GET /api/mail/contact-locations/countries` → 200 固定目录；controller 测试以 strict JSON 断言字段集合 `{sourceVersion, sourceUrl, defaultPolicy, countries:[{code,labelZh,defaultZoneId,zones:[{id,labelZh}]}]}`（`Controller.kt:38-39`、`Models.kt:55-66`）。
- `GET /api/mail/contact-locations/{contactId}` → 未配置 200 `configured:false`、三个 zone 字段全 null、`usingDefaultZone=false`，不创建占位行；不存在 contact 404 `NOT_FOUND`（controller 测试；IT:133/:229）。
- `PUT /api/mail/contact-locations/{contactId}` body `{countryCode, zoneId:null|string}` → 200 返回已持久化配置；未登录 401 / 非法 400 / 不存在 404（controller 测试）。
- `ContactLocationView` 字段与顺序逐字匹配 brief：`contactId, configured, countryCode?, countryLabel?, zoneId?, effectiveZoneId?, zoneLabel?, usingDefaultZone`（`Models.kt:23-32`），语义 `usingDefaultZone=(zoneId==null)`、`effectiveZoneId=zoneId?:国家默认`。c2 计划复用点（`docs/plans/2026-10-02/contact-timing-02-recommendation.md:126,143`：`location: ContactLocationView`、`location.effectiveZoneId`）与实现一致。

### AUTO_FIX
- N/A

### RECORD_ONLY
- R-1（可选边界，非缺陷）：`configured=true` 且使用国家默认时区的形态（`zoneId=null, effectiveZoneId=<默认>, usingDefaultZone=true`）仅在 service 单测/IT 层直接断言，HTTP 层未直接断言该 JSON 形态；可由 HTTP 层已断言的未配置用例（null 字段会被序列化、DTO 无 `@JsonInclude(NON_NULL)`）与共享 DTO 推断。为 c2/c3 取证可补的可选覆盖，不构成四门禁违反。
- R-2（跨计划已声明的延后项）：目录资源 `sourceVersion="2026c"` 且单测钉住该字符串，而运行 JDK tzdb 为 2026b，故总计划 G-0（≥2026c）仍未满足；brief 与 `baseline/env.txt` 已声明该延后并要求不得宣称时区验收通过，execution.md 亦按此声明。仅记录以备追溯，非本 child 发现。

### Required Action
- COMPLETE_CHILD
