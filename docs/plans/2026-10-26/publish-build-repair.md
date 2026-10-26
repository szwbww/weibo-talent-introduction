# 生产发布构建阻塞修复

日期：2026-10-26。执行授权：本次用户“修复 继续”，目标为修复失败任务 `job_20261026-103303_publish-production_abb93e88` 的构建阻塞并继续生产发布。使用 fix-v MECHANICAL_REPAIR；不重新实施或改写 discovery-recheck-master 的功能验收。

## 需求描述

让现有发布命令在不跳过测试的情况下构建并部署已提交版本。只修复测试时钟和未应用迁移的重复版本。保持配额、保留期限、重审历史、标签筛选及所有业务行为不变；不修复无关测试、重放历史、付费外调、推送远端或修改已有未提交文件。

## 关键不变量

### I-1：配额断言与样本使用同一时间基准

`store acceptance cases` 中固定于 2026-10-18 的样本及其 Store（含重启对象）使用同一固定 UTC 时钟。仍断言失败样本第三条 QUOTA、成功对照第二条 QUOTA，并保留独立的 8 天过期测试。生产 Store、保留期限和限额不改。

### I-2：迁移版本唯一，已部署 schema 不改写

保留 `V161__discovery_recheck_history.sql`；仅将尚未应用的 `V161__add_batch_exclude_tags.sql` 重命名为 `V162__add_batch_exclude_tags.sql`，SQL 字节不变。禁止 Flyway repair、out-of-order、修改已应用迁移或线上手工 DDL。来源：K-flyway-version-follows-deploy-order。

### I-3：发布版本与工作区隔离

只提交本计划列出的变更，保留所有既有未提交文件。使用发布配置固定的 JDK11.0.32；发布工具自行从提交建立工作树执行完整 `mvn clean package`，不增加 skipTests。新发布仅启动一次，持续每 15 秒查询同一 job 至终态。

## 现状审计

- 失败发布：5633 tests，1 failure，7 errors，24 skipped，`ERROR: WAR build failed`；构建尚未进入部署。
- `DiscoverySampleStoreTest.kt:20,29` 固定样本时间；`:134,137` 默认系统时钟与生产 `DiscoverySampleStore.retained():136-140` 的 7 天过期策略冲突。旧修复计划 `docs/plans/fix/discovery-recheck-master/repair.md:30,98` 明确将此项排除在旧功能修复之外；本次是用户另行授权的发布阻塞修复，不改写旧验收结果。
- 两个 V161 文件均来自 `src/main/resources/db/migration`。只重命名独立标签迁移，因此重审测试现有 `target("161")` 继续指向原 schema；不改历史目标测试。
- 生产只读核查：SSH 加载现有 setenv 后执行 `SHOW COLUMNS FROM batch_send_task_config LIKE "exclude_tags_json"; SHOW COLUMNS FROM discovery_verification_item LIKE "review_epoch";` 均无返回。生产采用 external-mysql，没有 flyway_schema_history，不能据此宣称 Flyway 已校验通过；真实迁移须由本地隔离 MySQL 与发布门禁证明。
- 写/读边界：样本生产 commit/retained 与 quota 均不变；标签迁移仍仅在 batch_send_task_config 增 nullable exclude_tags_json；重审迁移及其所有消费者不变。没有新增业务写路径。
- 检索收据：`grep(pattern='V161|target\\("161"\\)|target\\("160"\\)', path='src;docs/plans/2026-10-24/discovery-recheck-05-history.md;scripts;docs/deploy')` 返回重审测试的161目标及重审05文档引用，没有标签迁移文件名消费者。`glob(src/main/resources/db/migration/V16*)` 返回两个 V161、V160 和历史 V16。不对历史文档中的版本描述做全局替换。
- `FlywayMigrationIntegrationTest` 的无 target 最新版本断言仍为157，是既存相邻问题；本次不改，使用实际 Flyway CLI/隔离 MySQL 冒烟验证完整迁移。

## 实现方案

1. I-1：仅在已有 `store acceptance cases` 内将使用固定样本的 Store 显式注入 `Clock.fixed(now, ZoneOffset.UTC)`；独立过期场景保留各自时钟。
2. I-2：重命名标签迁移，不改 SQL 内容；无测试目标或生产调用方需要迁移。
3. I-1/I-2：运行 DiscoverySampleStoreTest、DiscoveryVerificationReplayToolTest、DiscoveryVerificationWorkerTest；执行隔离 MySQL 的真实完整迁移，检查两个功能 schema 均存在且二次 migrate 无待执行迁移；运行实际 Store 的独立配额/重启/过期烟测。
4. I-3：只提交本清单；启动一次 publish_feature(production, HEAD)，每隔15秒 check_job_status 至成功/失败。完整 Maven 与 WAR 构建以发布工具新鲜运行结果为准。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `docs/knowledge/common/K-flyway-version-follows-deploy-order.md` | 已用探针的使用元数据；原为本地忽略文件，不新增跟踪、不提交 |
| `docs/knowledge/common/K-flyway-latest-version-test-pin.md` | 已用探针的使用元数据；原为本地忽略文件，不新增跟踪、不提交 |
| `docs/knowledge/common/K-flyway-gate-must-pass-on-fresh-db.md` | 已执行全新库迁移探针的使用元数据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleStoreTest.kt` | 接受场景固定时钟，不放宽断言 |
| `src/main/resources/db/migration/V161__add_batch_exclude_tags.sql` → `V162__add_batch_exclude_tags.sql` | 仅文件名顺延，内容不变 |
| `docs/plans/2026-10-26/publish-build-repair.md` | 本次修复范围、授权及验证收据 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryHistoricalReplayTool.kt` | FIX-1：移除CLI配置的扫描stereotype，保留显式register及现有Bean图 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryHistoricalReplayToolTest.kt` | FIX-1：实际Spring扫描的客户端隔离回归 |
| `.multi-ai-kit.yaml` | 后续明确批准：生产健康等待120→600秒；不改健康URL、完整构建、自动回滚或应用恢复 |

## 验收标准

- I-1：样本测试原断言通过；真实 Store 三条失败样本依次 SAVED/SAVED/QUOTA，重启仍QUOTA；第8天清理后无样本。
- I-2：隔离 MySQL 完整迁移无重复版本，history同时存在161/162且success=1，重审字段/轨迹表与排除标签列存在；再次 migrate 执行数0。
- I-3：发布工具完整构建通过、上传/重启/健康检查通过。任何失败保留原始日志，不跳测或重复启动。

## 人工验收清单

### A-1：线上版本
- 前置条件：发布任务终态成功。
- 操作：打开线上登录页面并正常登录，查看专家审核页与批量任务配置页。
- 预期：页面可打开，既有重审入口及批量配置可读取，无迁移启动错误；本修复未触发重审或发信。
- 覆盖：I-1/I-2/I-3；人工验收PENDING，不代替机器检查。

## 修复验证收据

- 授权：本轮用户“修复 继续”；属于独立发布构建修复，不重新打开上一轮功能验收。
- 机械修复尝试1：仅测试固定时钟与未应用标签迁移重命名；未修改任何生产 Kotlin、迁移 SQL 内容、配额断言或跳过配置。
- 定向命令：`env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=DiscoverySampleStoreTest,DiscoveryVerificationReplayToolTest,DiscoveryVerificationWorkerTest`，exit0，31 tests / 0 failures / 0 errors / 0 skipped，BUILD SUCCESS。
- 独立运行烟测：JDK11直接执行临时 Java 程序，使用真实 Flyway、MySQL8.0.36 和生产 Store；初次因临时classpath缺少test-scope flyway-mysql适配器退出1，补入pom已声明的8.5.13适配器后exit0，不改产品或依赖。
- 新库实际迁移目标162，161/162 history success=1；review_epoch、recheck_json、trace表及exclude_tags_json存在；再次migrate执行数0。
- 真实Store：SAVED/SAVED/QUOTA，重启QUOTA，第8天样本清空。临时数据库容器与目录已清理，Java脚本交付前删除。
- fix-v机器检查：I-1/I-2通过；状态机/准入无变更；无新增业务写路径。人工验收PENDING。完整构建与部署以随后的独立发布任务为准，不将定向测试冒称全量通过。
- 已用知识探针仅更新使用元数据；其中原本被忽略且未跟踪的两条保持本地，不强制加入Git。迁移顺序条目达到10次，可供团队后续批准提升共享规则，本轮不修改CLAUDE.md。

## 修正记录

- 2026-10-26，用户再次“修复 继续”批准 `docs/plans/fix/publish-build-repair/fix-1.md`：原构建机械修复已通过，但真实发布暴露CLI配置污染生产扫描，故将范围扩到上表两份Kotlin文件；不改配额、HTTP客户端策略、迁移、归档恢复或健康超时。FIX-1原文件保持不变。
- 第二次发布 `job_20261026-110010_publish-production_7e1e6eab` 已完成构建并应用V161/V162，但因RestTemplate别名冲突启动失败，WAR自动回滚；数据库迁移没有回滚，不得再改写这两份SQL。
- 2026-10-26，用户在启动窗口选项中明确选择“延长至600秒后发布”，追加授权仅修改`.multi-ai-kit.yaml`的`release.production.health_timeout_seconds`（120→600）。证据：旧版曾约6分钟恢复、生产归档27GB而内存约7.5GB，当前旧版HTTP200；CLI冲突已由独立烟测证明修复。此批准覆盖前条“不改健康超时”的局部限制，不跳过归档校验、不改自动回滚、不修改FIX-1原文件。

## FIX-1执行收据

- 生产改动：仅删除HistoricalBindingsConfiguration上的扫描配置注解；`HistoricalBindingsRuntime.open():286-287`仍显式register/refresh，原有@Bean/@Import及显式连接要求不变；`RestTemplateConfig.kt:61-82`未改。
- 回归失败证据：新增实际扫描用例在修复前运行 `mvn test '-Dtest=DiscoveryHistoricalReplayToolTest#production*'`，1 test / 1 error，context.refresh重现线上同一alias覆盖冲突。首次修复后回归夹具缺少必填ES属性，补齐仅指向loopback的隔离配置后重新验证；没有修改正式配置或减弱断言。
- 独立默认入口烟测：JDK11直接运行临时Java程序，实际Spring默认扫描并禁用Bean覆盖；`DEFAULT_SCAN_SMOKE PASS separateProductionClients=true cliGraphAbsent=true overriding=false`，exit0；分别验证正式客户端重试拦截器语义，无CLI analyzer/执行器污染。
- 独立显式CLI烟测：JDK11直接执行 `DiscoveryRecheckFlowIT --archived`（非JUnit入口），exit0。`RECHECK10_RUNTIME isolatedMySQL=true strictFixtureES=true productionParserConsumerWorkerGate=true firstNewRaw=1 repeatNewRaw=0 actualFixtureAdmitted=1 candidateSync=1 partialMissing=1 frozenContext=true providerHttp=0 oldHistoryCheckpointMailUnchanged=true`。完整命令覆盖freeze/compare/report、缺少授权的apply拒绝，以及显式apply两次；没有向生产执行apply。
- 定向命令：`env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=DiscoveryHistoricalReplayToolTest,DiscoveryPaperBackfillServiceTest,RestTemplateConfigTest,DiscoveryRecheckFlowIT -DmysqlIt=true`，exit0；30 tests / 0 failures / 0 errors / 0 skipped，真实MySQL用例已运行。
- 完整构建：同一JDK执行`mvn clean package`，exit0；5634 tests / 0 failures / 0 errors / 24 skipped；WAR已生成。跳过数为默认可选集成用例，定向真实MySQL验证未跳过。不把本地含既有未提交配置的WAR作为发布物，发布仍由工具从固定提交重新构建。
- fix-v复核模式AUDIT_ONLY：FIX-1/P1-1源因RESOLVED；默认扫描与显式CLI均有独立运行收据；正式HTTP策略、显式连接、默认无业务写、准入/状态机/归档恢复保持不变。Accumulation：✅ 重复apply新增0；Cross-plan：✅ 生产扫描隔离且CLI保留；Deleted code：✅ 无扫描stereotype且无兼容别名；Scope：✅ 本次仅授权清单与用户后续批准的部署窗口。人工验收PENDING；部署I-3待新任务终态，不提前记为通过。
- 部署配置烟测：Ruby YAML实际解析`.multi-ai-kit.yaml`，`DEPLOY_CONFIG_SMOKE PASS timeoutSeconds=600 fullBuild=true healthUrlUnchanged=true externalMysql=true`，exit0。仅延长等待，不启用Bean覆盖、不绕过构建/测试/归档校验。工作树身份仍为main及原仓库，FIX-1 SHA256仍为`c2fbbc2b721977a49c48714c5cd38b4b77ff5fd57112c310f287fc81aa70d57a`。

## FIX-1发布终态

- 本轮仅调用一次publish_feature，固定提交`a28748d386b018348638f6ae7a3b90615eb66d8e`；任务`job_20261026-121649_publish-production_7ab6094b`，release_id=`release_20261026-121652_a28748d386b0`。
- 每15秒check_job_status查询同一job，共76次；12:36:12查询终态`completed successfully`，发布结构化结果`status=passed`，没有触发本轮回滚。发布工具使用固定提交的独立工作树执行完整构建，并在结束后清理工作树；终态日志窗口未保留完整测试计数，发布轮count unavailable，不借用本地5634计数。
- V161/V162本轮日志均`already applied`，保持此前迁移不变；上传、Tomcat重启及600秒窗口内的健康检查通过。旧Tomcat未在shutdown等待内退出，工具发送TERM后新实例正常通过检查；不把这一条当作仍未解决的alias冲突。
- 发布产物51,961,795字节，SHA256=`5f78636ff2726775c552b5ce57f7bbb2d559f8dd8b145b87a0cdab9ba8101307`。部署后SSH直接对`webapps/talent.war`执行sha256sum，结果与产物一致；再执行独立curl，`deployed_http=200 total_seconds=0.042`，exit0。
- 机器验证最终PASS：I-1/I-2沿用前次已通过收据，FIX-1/P1-1 RESOLVED，I-3真实部署及健康检查通过；没有修改应用恢复逻辑、已应用迁移或既有未提交用户文件。A-1人工登录和业务页面验收仍PENDING，不将机器健康检查冒称人工验收完成。
