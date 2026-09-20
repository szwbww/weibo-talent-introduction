## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-expert-material-upload-main/docs/plans/2026-09-20/manual-expert-material-upload-backend.md`
- Plan SHA-256: `2a5337872a1293b89881ca8a8f1b4cb719c20dd27f988f93406925c9ef123df5`
- Master plan: `docs/plans/2026-09-20/00-manual-expert-material-upload-main.md`，SHA-256 `402676caa56035706601113d0b690d6d5b5d29617aef0d01cabbfb328379b4a5`
- Child brief: `docs/plans/fast/manual-expert-material-upload-main/children/backend/brief.md`，SHA-256 `195a5211373f60e0b9e3989e3f84cc3329a34290e94152e665f2d9c7804d74c7`
- Execution ID: `docs/plans/2026-09-20/manual-expert-material-upload-backend.md@2a5337872a1293b89881ca8a8f1b4cb719c20dd27f988f93406925c9ef123df5`
- Execution epoch: NEW（本 worktree 无同 EXECUTION_ID 的既有执行记录）
- 执行者: `BackendImplementer`（fast-p child `backend` 子代理）
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-expert-material-upload-main`
- Target branch: `fast/manual-expert-material-upload-main`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-expert-material-upload-main@fast/manual-expert-material-upload-main@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-expert-material-upload-main`
- Pre-execution code SHA: `d2a7f65ecbc46b5165863dfcab94ae5972f50605`（= brief 的 `child_base_sha`，已确认是 HEAD 的祖先）
- Post-execution code SHA: `80beb2bddfc77f8f65f8c51446c9a6c14f2e10df`
- Evidence HEAD: N/A（本 child 不产生独立 evidence commit；fast-p 证据由 controller 持有）
- Implementation boundary: `d2a7f65..80beb2b`（10 个文件，+1646 / -32）

---

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| 阶段 1-1 V130 迁移（新表 + unique nullable `manual_upload_id` + 兼容性 CHECK 换三选一） | IMPLEMENTED | `V130__add_manual_expert_material_upload.sql` | IT `V130 adds the manual upload owner table switching the owner check to three-way`（MySQL 8.0.36 容器）PASS；全量版本断言 129→130 |
| 阶段 1-2 `MailAttachment` 末尾追加 `manualUploadId: Long? = null` | IMPLEMENTED | `MailAttachment.kt` | 既有 5 个 `MailAttachment(...)` 构造点全部未改仍编译；全量 Java 3490→3503 测试 0 失败 |
| 阶段 1-3 新实体 + repository | IMPLEMENTED | `ManualExpertMaterialUpload.kt`、`ManualExpertMaterialUploadRepository.kt` | 编译 + flow 测试三 repo 写入断言 + IT 表/索引/外键断言 |
| 阶段 1-4 Flyway IT 版本更新 + V130 专项测试 | IMPLEMENTED | `FlywayMigrationIntegrationTest.kt` | IT：`Tests run: 25, Failures: 0, Errors: 1`（唯一 error 为**既有** `V124` 用例，见下"既有红灯"）；V130 用例单跑 `Tests run: 1 ... BUILD SUCCESS` |
| 阶段 2 有界原子上传服务（I-1～I-4、I-6、I-7） | IMPLEMENTED | `ManualExpertMaterialUploadService.kt` | flow 测试 13 项：201/磁盘字节/三表/无 transfer/0B/100MiB/流失败/第二三写失败/提交失败/跨专家/来源/筛选/yml |
| 阶段 2-5 `application.yml` parser ceiling 100MB/101MB | IMPLEMENTED | `application.yml` | flow 测试 `multipart parser ceiling is one hundred MiB in application yml`（正则断言 100MB/101MB 且旧 10MB/11MB 已消失） |
| 阶段 3-1 `POST /api/expert-contacts/{contactId}/materials/uploads` → 201 | IMPLEMENTED | `ExpertMaterialController.kt` | flow 测试 MockMvc 201 + 401/400/404/413 契约 + 冻结样例 |
| 阶段 3-2 `ExpertMaterialService` manual owner / source / list 投影 | IMPLEMENTED | `ExpertMaterialService.kt` | flow 测试 `same expert list ...`、`manual source filter ...`、`... shared resolver`、`manual attachment does not advance operator status ...`；既有 `ExpertMaterialServiceTest` 33 项 + `ExpertDocumentBrowseServiceTest` 15 项 + `DocumentTextExtractorTest` 6 项全绿 |
| 阶段 3-3 新 flow 测试 | IMPLEMENTED | `ManualExpertMaterialUploadFlowTest.kt` | 13 项全绿（唯一新增测试类；Java 汇总 3490→3503） |
| I-1 三选一 owner | IMPLEMENTED | V130 + 上传服务 | IT：三 owner 组合可写、零/双/三 owner 被 MySQL 8 CHECK 拒绝；服务只写 `manual_upload_id`（两个邮件 owner 显式为 null，flow 测试断言） |
| I-2 文件与三表同成同败 | IMPLEMENTED | 上传服务 + flow 测试 | 流失败→零库写零文件；第二/第三写失败与 commit 失败→`BEGIN/ROLLBACK` + final 删除 + 暂存行作废；成功→manual 目录仅 1 个 UUID final，无 `.tmp-*` |
| I-3 100 MiB 双层边界 | IMPLEMENTED | 服务 + yml | 0B 与恰好 104857600 成功（磁盘 `Files.size` 一致）；104857601 → HTTP 413 `PAYLOAD_TOO_LARGE` 且零文件零元数据；`OutboundAttachmentServiceTest` 28 项（含 10 MiB+1 拒绝）全绿 |
| I-4 成功即 STORED/PENDING_REVIEW 且无 transfer | IMPLEMENTED | 服务 + `ExpertMaterialService.storageStateOf` | flow 测试断言 `storageState=STORED`、`documentStatus=PENDING_REVIEW`、`canFetch=false`、真实 `actualSize`；`Mockito.verify(mailAttachmentTransferRepository, never()).save(...)` |
| I-5 手动来源可解释可筛选 | IMPLEMENTED | `ExpertMaterialService` | flow 测试断言 `type=MANUAL_UPLOAD`、`id=contactId`、`subject=手动上传`、`uploadedBy=会话用户名`、`accountCode=null`、`receivedAt=上传时间`，且 `source/sourceId` 筛选只回手动材料；邮件来源投影回归不变 |
| I-6 会话身份与跨专家拒绝 | IMPLEMENTED | controller + 服务 + resolver | 上传者只取 `AuthSessionKeys.USERNAME`（客户端无该入参）；未知专家 404；manual owner 错专家时 `resolveReadyFile` 抛 `IllegalArgumentException`、列表中 `canDownload=false` + `SOURCE_AMBIGUOUS`；响应体断言不含 `storagePath`/根路径/`.tmp-` |
| I-7 手动材料不成为邮件事件 | IMPLEMENTED | 上传服务（未改邮箱/状态代码） | flow 测试用真实 `OperatorStatusReconcileService` 构造「有来信、有 manual 附件」fixture：`dbVsExpected=0`、无 `MATERIALS_RECEIVED` 样本；批量写路径 grep 确认 `mail_record_id`/`inbound_processing_id` 为 null |
| I-8 MySQL 5.7/8.0 兼容迁移 | IMPLEMENTED（5.7 演练为人工 A-8，本机不可执行） | V130 | MySQL 8.0.36 容器实跑 V130 用例 PASS；5.7 守卫沿用 V129 的 `TABLE_CONSTRAINTS + PREPARE` 样式；本机无法起 5.7（见"未解决风险"） |

### 验收标准逐条对照

- I-1：IT 三 owner 合法/零/双/三 owner 拒绝 PASS；`grep -rn "MailAttachment(" src/main/kotlin --include=*.kt | grep -vE "Transfer|Outbound|Manual"` → 4 处（`MailAttachmentService.kt:237/312/346` 三条旧写路径 + `ManualExpertMaterialUploadService.kt:113`）；前三条只设置旧 owner 且本 diff 未改动，新服务只设置 manual owner。
- I-2：flow 测试逐项注入流失败、attachment 写失败、document 写失败、commit 失败；断言事务结果 `BEGIN/ROLLBACK`、`${basePath}/manual` 无 `.tmp-*`/final、暂存元数据未发布。
- I-3：104857600 成功、104857601 为 413；`OutboundAttachmentServiceTest` 的 10 MiB+1 仍为 413（该文件未改，28 项全绿）。
- I-4：上传后列表项 `STORED`/`PENDING_REVIEW`/`canFetch=false`/`canDownload=true`；无 transfer 行写入。
- I-5：来源与筛选断言见上；`MAIL_RECORD` 筛选回归仍返回邮件来源。
- I-6：匿名 401、缺 file 400、未知专家 404、超限 413；响应 JSON 无 `storagePath`、无绝对根路径、无临时文件名。
- I-7：reconcile fixture 不推 `MATERIALS_RECEIVED`；邮箱/自动回复/transfer 代码零改动，`OperatorStatusReconcileServiceTest` 12 项全绿。
- I-8：`-DmigrationIt=true` 下 MySQL 8 容器迁至 130 且 V130 专项断言全绿（需 `-DargLine="-Dapi.version=1.43"` 规避环境 Docker 客户端版本问题，见"命令与偏差"）；人工 A-8（生产 5.7 预演）不在本 child 范围且本机不可执行。

---

## Commands

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn -Dtest=ManualExpertMaterialUploadFlowTest,ExpertMaterialServiceTest,ExpertDocumentBrowseServiceTest,DocumentTextExtractorTest,OperatorStatusReconcileServiceTest,OutboundAttachmentServiceTest test` | **PASS**（Java 门禁）/ 进程 exit **1** | surefire `Tests run: 107, Failures: 0, Errors: 0, Skipped: 0`（Outbound 28 / Flow 13 / ExpertMaterialService 33 / DocumentTextExtractor 6 / ExpertDocumentBrowse 15 / OperatorStatusReconcile 12）；exit 1 只来自 `node-test` exec 步骤（既有 JS 红灯），surefire 阶段先跑完 |
| 1b | 同 #1 加 `-DskipNodeTests=true`（补充证据，绕开既有 JS 步骤） | **PASS** | exit **0**，`Tests run: 107, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS` |
| 2 | `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | **BLOCKED（环境）** | exit 1；精确错误：testcontainers 1.19.8 内置 docker-java 默认 API 1.32，本机 Docker Engine 服务端要求 ≥1.40 → `Could not find a valid Docker environment ... BadRequestException (Status 400: {"message":"client version 1.32 is too old. Minimum supported API version is 1.40, please upgrade your client to a newer version"})` → `IllegalStateException: Docker is required for Flyway migration tests`（`Tests run: 1, Errors: 1`，未静默跳过） |
| 2b | 同 #2 加 `-DargLine="-Dapi.version=1.43" -DskipNodeTests=true`（环境规避，实际取得 IT 证据） | **PASS（除既有 V124 红灯）** | exit 1；`Tests run: 25, Failures: 0, Errors: 1`，唯一 error = 既有 `V124 allows material attached promotion audit trigger`（FK `fk_eap_contact`，见"既有红灯"）；**V130 专项用例 PASS** |
| 2c | `mvn -DargLine="-Dapi.version=1.43" -DskipNodeTests=true -DmigrationIt=true -Dtest='FlywayMigrationIntegrationTest#V130*' test` | **PASS** | exit **0**，`Tests run: 1, Failures: 0, Errors: 0` + `BUILD SUCCESS`（V130 三选一 owner/索引/外键/unique 断言全绿） |
| 3 | `git diff --check` | **PASS** | exit **0**（无空白错误；另 `git show --check HEAD` 也 exit 0） |
| 4 | `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test` | **PASS（Java）/ 进程 exit 1** | surefire 汇总（`target/surefire-reports/*.txt` 聚合）`tests=3503 failures=0 errors=0 skipped=13`，即 Java 侧 0 红；exit 1 只来自 `node-test` exec 步骤（既有 JS 红灯，03:08 min） |
| 5 | `node --test --test-reporter=tap src/test/js/*.test.js`（复现既有 JS 基线） | **FAIL（既有）** | exit 1，`# tests 1023 / # pass 1006 / # fail 17`，与 brief 记录的基线完全一致；失败集中在 11+1 静态资源缓存键分裂用例 |

### 既有红灯（本 child 不修、不隐藏）

1. `node --test src/test/js/*.test.js`：`1023 tests / 1006 pass / 17 fail`（`--test-reporter=tap` 顶层 `not ok` 计数 = 12 个失败文件），全部是 `index.html` 静态资源缓存键分裂（`?v=20260920-manual-material-upload` vs `20260919-sharepoint-file-card-display`）——属工作树既有 WIP 的既有红灯，与后端 child 无关（本 child 未改任何前端文件）。因此 `mvn test` 的 exit 1（`node-test` exec）在基线即存在，本 child 保持不修。
2. `FlywayMigrationIntegrationTest.V124 allows material attached promotion audit trigger`（仅 `-DmigrationIt=true` 时可见）：该用例在 `clean()` + 全链迁移后插入 `expert_application_promotion(expert_contact_id = 1, ...)`，而 `expert_contact` 在该状态下为空（`grep -rn "INSERT INTO expert_contact" src/main/resources/db/migration/` 零命中），FK `fk_eap_contact` 必然拒绝 → 既有 latent 测试缺陷，与本 diff 无关（本 child 只新增表/列/约束到 `mail_attachment` 与 `manual_expert_material_upload`，不触碰 `expert_contact` / `expert_application_promotion`）。基线不跑该 IT（`migrationIt=false`）故未暴露。

---

## Changed Files

| # | 文件 | 动作 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql` | 新增 | `manual_expert_material_upload` 表 + `mail_attachment.manual_upload_id`（nullable + unique + FK）+ V129 同款守卫换三选一 CHECK；零历史改写 |
| 2 | `src/main/resources/application.yml` | 修改 | multipart parser ceiling `10MB/11MB` → `100MB/101MB`（仅容器前置保护；旧 outbound 10 MiB 业务常量不变） |
| 3 | `src/main/kotlin/.../mail/domain/MailAttachment.kt` | 修改 | 末尾追加 `manualUploadId: Long? = null`（唯一共享字段；5 个既有构造点零改动） |
| 4 | `src/main/kotlin/.../document/domain/ManualExpertMaterialUpload.kt` | 新增 | 上传来源实体（id/expertContactId/uploadedBy/createdAt） |
| 5 | `src/main/kotlin/.../document/repository/ManualExpertMaterialUploadRepository.kt` | 新增 | 纯 `CrudRepository`，无删除 API |
| 6 | `src/main/kotlin/.../document/service/ManualExpertMaterialUploadService.kt` | 新增 | 流式计数落盘 → 原子移动 → 单事务三表写入；413 复用 `OutboundAttachmentException.payloadTooLarge`，文件名/MIME/材料类型复用 04 与收信同一套规则 |
| 7 | `src/main/kotlin/.../document/controller/ExpertMaterialController.kt` | 修改 | 新增 `POST .../materials/uploads`（201），注入上传服务 |
| 8 | `src/main/kotlin/.../document/service/ExpertMaterialService.kt` | 修改 | manual owner 分支、列表 SQL 投影（`a.manual_upload_id` + `manual_expert_material_upload` LEFT JOIN）、`resolveSource`/`SOURCE_TYPES`/`MaterialSource.uploadedBy`；未改 transfer/reconcile/`resolveMessageAttachments` |
| 9 | `src/test/kotlin/.../document/controller/ManualExpertMaterialUploadFlowTest.kt` | 新增 | 13 项贯通测试 + 3 段冻结样例输出 |
| 10 | `src/test/kotlin/.../campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改 | 全量版本断言 129→130（含 fresh DB 用例名）+ 新增 V130 专项用例 + 通用 `columnMeta` 助手 |

工作树中除这 10 个文件外无任何业务改动；`docs/plans/**` 未被提交（`git show --stat HEAD` 证明）。

---

## 冻结跨计划样例（frontend child 只依赖这些字段；均不含 storagePath）

由 `ManualExpertMaterialUploadFlowTest` 的 `frozen response samples carry no storage path` 用例真实打印（MockMvc 使用与生产同配置的 Jackson：Kotlin + JavaTimeModule，`LocalDateTime` 为 ISO-8601 字符串）：

**1) 201 成功响应**（`POST /api/expert-contacts/{contactId}/materials/uploads`，单 part `file`）：

```json
{"attachmentId":2,"documentId":3,"fileName":"cv.pdf","contentType":"application/pdf","fileSize":6,"documentType":"CV","documentStatus":"PENDING_REVIEW","storageState":"STORED"}
```

**2) 413 超限响应**（>104857600 字节；容器解析期或服务读取期同形）：

```json
{"code":"PAYLOAD_TOO_LARGE","message":"单个材料不能超过 104857600 字节","detail":"Payload Too Large"}
```

**3) `GET /api/expert-contacts/{contactId}/materials` 的手动材料 item**：

```json
{"attachmentId":2,"documentId":3,"source":{"type":"MANUAL_UPLOAD","id":1,"subject":"手动上传","receivedAt":"2026-09-20T14:17:40.625193","accountCode":null,"uploadedBy":"op1"},"fileName":"cv.pdf","contentType":"application/pdf","documentType":"CV","documentStatus":"PENDING_REVIEW","actualSize":6,"encodedSize":null,"storageState":"STORED","bytesDownloaded":0,"error":null,"canFetch":false,"canDownload":true,"canPreview":true,"analysisSupported":true,"canAnalyze":true,"downloadUrl":"/api/expert-contacts/1/attachments/2/download","previewUrl":"/api/expert-contacts/1/attachments/2/preview"}
```

补充 HTTP 契约证据（同一测试）：匿名 `401 {"code":"UNAUTHORIZED","message":"未登录",...}`；缺 `file` `400 {"code":"BAD_REQUEST",...}`；未知专家 `404 {"code":"NOT_FOUND","message":"专家不存在",...}`。

---

## Deviations

1. **两个新依赖的可空注入**（`ExpertMaterialController.uploadService: ManualExpertMaterialUploadService? = null`、`ExpertMaterialService.manualUploadRepository: ManualExpertMaterialUploadRepository? = null`）。理由：子计划文件清单是硬边界（I-6），既有 `ExpertMaterialServiceTest`（9 位置参数构造）与 `ExpertMaterialControllerHttpTest`（`@WebMvcTest`，只 mock 4 个 service）不可修改，非空必造成未授权文件编译/上下文失败。Spring 语义：Kotlin 可空构造参数 → `MethodParameter.isOptional()==true` → `DependencyDescriptor.isRequired()==false`（已核对 spring-beans 5.3.29 源码 `MethodParameter.KotlinDelegate.isOptional` / `DependencyDescriptor.isRequired`），故**缺失 bean 时不抛异常、存在 bean 时照常注入**；这与仓内既有先例一致（`OutboundAttachmentController` 的两个 `Repository? = null`、`AttachmentTransferService.transactionTemplate? = null`）。生产为 `@Service` 与 Spring Data repository，按类型唯一可解析；端点入口对 null 显式报错（`IllegalStateException("手动材料上传服务未接线")`），绝不静默放行。[INFERENCE] 生产注入本身未被本次任何"全上下文"测试执行覆盖（见"未解决风险"）。
2. **缺 `file` 的 400 取得方式**：子计划写"沿 Spring binding → 400"，实现改为 `@RequestParam(name = "file", required = false)` + `OutboundAttachmentException.badRequest("缺少 multipart 字段 file")`。理由：`GlobalExceptionHandler` 的 `@ExceptionHandler(Exception)` 会把 `MissingServletRequestPartException` 变成 500（04 的 `OutboundAttachmentController` 注释已记录同一坑），可观察契约仍是 400 `BAD_REQUEST`，flow 测试逐字断言。
3. **`TransactionTemplate` 注入的是仓内唯一的 `attachmentTransferTransactionTemplate` bean**（`AttachmentTransferTransactionConfig` 显式注册，REQUIRES_NEW；Boot 自动配置的 `transactionTemplate` 因 `@ConditionalOnMissingBean(TransactionOperations)` 退让）。不新建配置类（无授权文件）。上传调用点无外层事务，故 `REQUIRES_NEW` 与子计划要求的"一个事务"语义等价。
4. **`MaterialSource.uploadedBy` 与 `MaterialRow` 4 个新字段都带默认值、追加在末尾**：为让未授权的既有测试文件零改动；邮件来源 JSON 多出 `"uploadedBy": null`（null，而非缺键），符合子计划"既有两个来源显式/null 默认，不改其 JSON 字段语义"。
5. **迁移 IT 的环境规避**：精确命令因 testcontainers 1.19.8/docker-java 与 Docker Engine 29 的 API 版本不兼容而无法连接 Docker；实际 IT 证据使用 `-DargLine="-Dapi.version=1.43"`（docker-java 的 `api.version` 配置项，`DefaultDockerClientConfig` 从系统属性读取）。这是环境开关，不改任何被授权文件。
6. **未执行 `spring-boot:run` 活体 smoke**（本为可选加固）：被既有打包缺陷挡住 —— `flyway-mysql` 在 `pom.xml:66-70` 是 **test** scope，运行期类路径没有 MySQL 支持，`mvn spring-boot:run` 直接 `FlywayException: Unsupported Database: MySQL 8.0`；`@SpringBootTest` 全上下文又因既有测试类路径 bean 冲突（`WorkerLifecycleTestConfig` 与 `attachmentTransferWorker` 同名 `BeanDefinitionOverrideException`）无法加载。两者均与本 diff 无关（本 child 未改 pom / 未新增同名 bean）。

---

## Freshness

- Plan identity rechecked: **YES**（执行前后两次 `shasum -a 256` 完全相同：2a533787…、402676ca…）
- Worktree identity rechecked: **YES**（始终为上述 worktree root / branch；`git add` 与 `git commit` 只在该根目录执行）
- Reported commits reachable from target branch: **YES**（`git rev-parse HEAD` == `git rev-parse fast/manual-expert-material-upload-main` == `80beb2bddfc77f8f65f8c51446c9a6c14f2e10df`，其父为 `d2a7f65`）
- Required commands run this invocation: **YES**（#1/#1b/#2/#2b/#2c/#3/#4/#5 全部在最终代码状态上本次执行）
- Historical evidence used only as baseline: **YES**（brief 记录的 3490/0/0/13 与 1023/1006/17 只作基线对比）

## Remaining Blocker

- None（就本 child 的实现与机器门禁而言）。需要人类/后续环节补的两项：
  1. **A-8（MySQL 5.7 迁移演练）**：本机无法执行——`docker pull mysql:5.7` 报 `no matching manifest for linux/arm64/v8`，`--platform linux/amd64` 拉取报 `Get "https://registry-1.docker.io/v2/": EOF`。V130 已按 V129 的 5.7 安全样式（`information_schema.TABLE_CONSTRAINTS` 探测 + `PREPARE` 守卫 DROP CHECK；`ADD CONSTRAINT ... CHECK` 在 5.7 解析后忽略）编写，残余风险为生产 5.7 上三选一 CHECK 不生效，需依赖应用层显式构造（新服务恒只写 `manual_upload_id`）与人工 A-8 演练确认。
  2. **生产 bean 接线的活体验证**：因上述既有环境/打包阻塞（Docker API 版本、`flyway-mysql` test scope、既有测试类路径 bean 冲突），本次没有任何全上下文测试或活体 HTTP smoke 被执行。建议 verify-p 或发布前在可用环境补一次 `spring-boot:run` + `POST /uploads` smoke（或在 A-1 人工验收中覆盖）。

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
