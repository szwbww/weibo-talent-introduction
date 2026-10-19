# 发布测试阻塞修复

## 需求描述
用户授权：修复发布失败、Docker 测试设为忽略后继续发布。默认构建不运行 Docker 集成测试；修复发现服务测试的失效 mock，然后发布包含修复的已提交版本。不得跳过普通测试、修改生产准入逻辑或迁移；不得提交工作区已有的无关改动。范围外：Apollo 功能设计调整、生产数据修复。

## 关键不变量
### I-1：只跳过显式可选的 Docker 测试
默认跳过 DiscoveryVerificationIntegrationTest，使用既有 mysqlIt 开关显式启用；保留 Docker 检查和全部断言。普通 JVM/JS 测试继续运行。来源：本次用户授权与 pom.xml 既有 mysqlIt 约定。
### I-2：mock 与当前生产调用一致
ExpertDiscoveryServiceTest 的默认准入桩及局部拒绝桩使用 evaluate(docId, profile, eligibility)，eligibility 参数位置为 2；不修改准入规则、发现逻辑或原有行为断言。来源：当前签名与失败证据。
### I-3：发布固定提交
只提交本计划与本次测试修复；不混入现有未提交文件。新发布任务只启动一次，以同一 job id 每隔 15 秒查询到终态。来源：用户发布请求。

## 现状审计
不新增或修改业务存储读写路径；只有测试与本计划变更。相关知识目录已检查，未采用 ES/前端/任务状态知识作为本次测试修复的新要求。
- 上次发布 job_20261019-123044_publish-production_c960d84b：WAR build failed；JVM 5214 tests、54 errors、20 skipped。
- 定向诊断命令：`JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test '-Dtest=ExpertDiscoveryServiceTest#discover processes papers and extracts emails' -DskipNodeTests=true`。结果：1 error，recordAutomaticConclusion 的 automatic 参数为 null。
- DiscoveryReviewService.kt:844-849 调用 policy.evaluate(docId, profile, eligibility)。ExpertDiscoveryServiceTest.kt:190-206 默认 mock 旧重载，:2364-2366 局部拒绝 mock 同样过期。
- grep 回执：`pattern=admissionPolicy|recordAutomatic\(`，目标 ExpertDiscoveryServiceTest.kt 与 ExpertDiscoveryService.kt；输出默认 mock :202/:206、局部 mock :2364、生产调用 :2535。
- DiscoveryVerificationIntegrationTest.kt:40 未加显式启用条件，:48 在无 Docker 环境主动失败。FlywayMigrationIntegrationTest.kt:51 使用 EnabledIfSystemProperty；pom.xml:24/:177 已默认 mysqlIt=false 并传给 Surefire。
- 无 Kotlin LSP 服务可用，xd://lsp status 返回 No language servers configured。

## 实现方案
1. I-1：在 Docker 集成测试类上增加 EnabledIfSystemProperty(named="mysqlIt", matches="true")，保留现有断言与启用后的 fail-fast。
2. I-2：默认与局部 mock 改为当前三参数重载，读取 eligibility 的正确索引；删除本 fixture 不再消费的单参数默认桩。不修改生产文件。
3. I-1/I-2：执行定向测试、完整 clean package（含普通 JVM 与 JS 测试）；通过后在本计划记载结果。
4. I-3：显式路径提交修复，用 publish_feature 发布固定提交；每隔 15 秒 check_job_status，成功或失败即停止。

## 变更文件清单
| 文件 | 改动 |
| --- | --- |
| src/test/js/batchEmailVerification.test.js | 补齐抽取函数的真实 preview helper 依赖 |
| src/test/js/recipientPreviewDiagnostics.test.js | 补齐 preview 状态 helper 依赖 |
| src/test/js/batchSendTaskConsoleInteraction.test.js | 对齐编辑器/预估 VM sandbox |
| src/test/js/batchSenderFilter.test.js | 对齐编辑器 VM sandbox |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt | 对齐准入 mock 重载 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt | mysqlIt 显式启用 |
| docs/plans/2026-10-19/publish-test-repair.md | 修复依据、运行说明与结果 |

## 验收标准
- I-1：默认定向执行 DiscoveryVerificationIntegrationTest 显示 skipped，无 Docker 启动失败；显式运行命令为 `mvn test -Dtest=DiscoveryVerificationIntegrationTest -DmysqlIt=true`，需 Docker。本次不声称 Docker 集成测试通过。
- I-2：ExpertDiscoveryServiceTest 全类通过；原拒绝准入用例继续断言不投影；完整 clean package 通过，不使用 skipTests。
- I-3：发布固定新提交，任务终态日志作为构建/部署/健康验证证据。若发布失败，不隐瞒或绕过失败。

## 人工验收清单
### A-1：发布结果
前置：机器构建与发布成功。操作：打开线上 `/talent/`。预期：页面可访问；发布任务记录目标提交与健康检查成功。覆盖 I-3。
### A-2：Docker 测试保留
前置：本机 Docker 可用。操作：执行上述 mysqlIt=true 定向命令。预期：集成用例被执行而非跳过，保留原有断言。覆盖 I-1；本次人工验收待执行。

## 执行结果
- fix-v 模式：MECHANICAL_REPAIR；构建 PASS。命令：`JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn clean package`，2026-10-19 13:16:47 完成，耗时 06:07，BUILD SUCCESS。
- JVM：5214 tests、0 failures、0 errors、21 skipped；其中 ExpertDiscoveryServiceTest 193 tests 全通过，DiscoveryVerificationIntegrationTest 明确 skipped（未声称 Docker 集成验证通过）。
- JS：1724 tests、1724 passed、0 failed、0 skipped；app.js 与 task-modal-runtime.js 语法检查通过。已删除与当前手动统计行为冲突的旧 debounce 用例，其他行为断言保留。
- 本地构建 JVM 与正在运行的生产 JVM 都是 Zulu 11.0.32.1；对两者执行相同 Java 时区探针并用仓库目录校验：地区目录缺失 0、会议目录缺失 0、偏移断言 10/10。生产探针临时 Java 文件由 EXIT trap 清理。
- I-1/I-2 机器验证通过；没有生产代码或迁移变更。I-3 由后续固定提交发布任务记录验证；人工验收待执行。

## 修正记录
- 首轮机械验证：JVM 5214 tests、5 failures、0 errors、21 skipped；NPE 已清零，Docker 默认门禁已生效。
- 同一报错文件的直接 fixture 修复：isolatedCachedVersionCases 补齐当前 bindExecutionId 成功返回及 executionId 快照；入队重放用例改为显式传入其断言的 77L，不再 mock 旧进度槽。保持原有业务断言，不改生产代码。该范围属于本次用户已授权的测试依赖修复，作为第二次机械尝试执行。
- 第二轮机械验证：JVM 5214 tests、0 failures、0 errors、21 skipped；JS 1725 tests、40 failures。失败由测试 VM 未加载现有 resetRecipientPreview/setRecipientPreviewState 引起，不是生产函数缺失。第三次机械尝试仅补齐上述 JS 报错文件内的真实 helper 与 sandbox 状态/计时器依赖，保留原有行为断言；不改 app.js、index.html 或样式，也不跳过 JS 测试。
- 人工授权追加修复：用户在次数上限确认中选择「继续修复并发布」，允许完成剩余两处 JS 测试修复并继续发布。当前 JS 1725 tests、1723 passed、2 failed；根因分别为 DOM stub 将提示和按钮映射为同一对象，以及旧用例仍要求已移除的 500ms 自动预估。后者依照删除过期实现契约测试的工程规则删除，不修改生产行为；前者按真实 DOM id 隔离对象。追加修复后重新完整构建。
