# Fast-P Child Brief — 04（自动准入、人工批准与数据写入衔接）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-10-04/discovery-review-master.md`，identity `commit:8853573efcfc82a84d75264a53923709e94b4702`（A2 修正后）。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-10-04/discovery-review-04-admission-writes.md`，identity `commit:07beaafc111a1b14ed3c48d514db527c8a13fc31`。全部章节逐条生效；本 brief 摘要与计划原文冲突时以计划原文为准。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`；branch `fast/2026-10-04-discovery-review-master`；`child_base_sha = 6043a678fe7736d133c9b2f25c1e139ad1130985`（= child 03 code head）。
- 依赖：01–03。下游：05（批量读取持久准入结论）、06（初始化/同步状态 UI）。本片仍不切换发送逻辑，不先删除旧发信门禁。
- 取证材料（worktree 内只读）：`docs/plans/2026-10-04/discovery-review-audit.md`（E1/E2/E5、X1–X4）、`docs/plans/2026-10-04/discovery-review-evidence/`（es-write-paths.txt 等）。
- 基线命令结果：`docs/plans/fast/2026-10-04-discovery-review-master/baseline.md`。

## 全局约束

1. 只允许修改「Authorized Files」表内 10 个文件；不得新建白名单外文件。其余 Kotlin/SQL/迁移/前端/文档全部只读。
2. 不得修改 `docs/plans/**` 内的计划与其他证据；本 child 唯一可写非产品文件是执行报告 `docs/plans/fast/2026-10-04-discovery-review-master/children/04/execution.md`。fast-p 报告不进入产品提交（控制方单独提交）。
3. 不得 push、merge、rebase、squash、amend、reset；不得改写已有提交。产品代码只提交一次：`feat(fast-p): implement 04`。
4. 计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不得自行扩范围或改计划。
5. 禁止联网抓取、连线上 MySQL/ES、发信、部署；不得新增依赖；不得改 `pom.xml`。
6. 测试库必须是本机容器 `ti-mysql-it` 内的**独立库 `talent_introduction_fastp`**（已创建，utf8mb4；同容器 `talent_introduction` 已被外部工作流漂移到 V149、缺 V148，禁止使用）。连接串：`jdbc:mysql://localhost:3306/talent_introduction_fastp?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true`，`DB_USERNAME=root DB_PASSWORD=root`；禁止线上/日常库。Docker = OrbStack（testcontainers 需 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` + `-Dapi.version=1.40`）。localhost:9200 无本地 ES，ES 交互用既有 mock/隔离测试方式并如实记录。
7. 不新增迁移、不改 ES mapping/字段；既有共享表新增字段 0；admission 表写入口收敛到 `ReviewService.recordAutomatic/applyManual`，其他 service 不直接 SQL。
8. D1 未定案：不得删除/绕过旧发信门禁、不得接发送切换、不得声称"所有黑盒已清除"。
9. 保持来信晋升、人工降级、标签与联系人状态语义；审核不写 `identityVerification/institutionEvidence/机构/国家/filterResult` 伪装自动通过；人工批准不开放作者绑定。
10. 不得为凑通过删除既有防错测试；岗位行号守卫类测试（`OperatorStatusWriteSeamGuardTest` 等）如因授权文件外改动被触发，先确认不在本片文件范围。
11. 人工预授权（2026-10-04，适用 04–06）：若既有测试的**精确计数/集合断言**仅因本计划合法新增/变更的枚举、目录或 taskType 条目而失败，你可以在本 child 内对该测试文件做**最小重同步**（只改计数/集合/样例字面量；不弱化、不删除断言、不改无关语义），并在执行报告中逐条列出文件与旧/新断言；控制器据此记录 amendment 行并同步主计划文件数上限。超出该类别（行为断言、产品语义、其他文件）仍必须返回 PLAN_CONFLICT。

## Authorized Files（10）

| # | 精确路径 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` | `recordAutomatic/resolveAdmissionBatch/initializeExistingAdmissions` + 候选投影方法 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewController.kt` | 存量初始化与同步重试端点 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 两个初次收录分支（旧 ORCID、consumeOutcomeInternal）RAW 成功后共用统一准入 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` | 重验尊重有效人工准入；发现分支不按旧基础失败删候选 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | discovery 晋升接缝：真实 docId/源快照/显式准入结果的 create/CAS |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewServiceTest.kt` | 初始化/投影覆盖 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 两初次写路径 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt` | 自动/人工重验 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt` | 非发现/降级回归 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` | CAS/create/409 |

## 关键不变量（计划 I-1～I-4；逐字以计划为准）

- I-1：RAW 成功后持久 AUTO_PASSED/NEEDS_REVIEW；同身份人工结论（MANUAL/LEGACY/HOLD/REJECTED）不被自动任务覆盖；身份变化生成新自动结论并保留旧历史；补全可更新 AUTO 但不抹人工决定。
- I-2：批准以 MySQL 事务为权威；RAW-only 批准后按真实 `_id` create 候选；已存在候选/有效不覆盖、不新建 contact；409 复读身份一致才算已存在；ES 故障记 CANDIDATE_SYNC_FAILED、state 仍 APPLIED，可重试投影，不重签审核。
- I-3：重验先读当前准入；有效人工批准跳过基础重新拒绝，仅更新有据事实；人工暂缓/拒绝不自动晋升；学术来源绑定仍必须 `DiscoveryIdentity.allowed`。
- I-4：存量初始化固定批次扫描 RAW 与候选/有效层发现/待确认数据、真实 `_id` 去重；仅有效 legacy 回执写 LEGACY_APPROVED；其余按自动判定；不删文档、不发邮件、不擅自取消人工降级；缺 RAW 用现存层 source 并记录 level。

## 必需命令（fresh 运行，逐条记录 exit code 与计数；与 baseline.md 对照）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests test-compile
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryReviewServiceTest,ExpertDiscoveryServiceTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest,ExpertIndexWriterServiceTest test
```

## 下游接口（05/06 依赖）

- `ReviewService.recordAutomatic` / `resolveAdmissionBatch` / `initializeExistingAdmissions` 与候选投影方法（供人工批准与自动晋升共用；投影失败状态独立可见、可重试）。
- admission 表读路径保持 02/03 的批量读取语义（05 消费）；列表对无 admission 的存量显示"尚未初始化"。

## 上游产出（01–03 已交付；code heads：01=209315103a89c5ea7807e4707bb87967d8579525，02=df9cad44ea77f37ebdb0e4af1b2d555573b6ffe5，03=6043a678fe7736d133c9b2f25c1e139ad1130985）

- 01：`DiscoveryAdmissionPolicy`（`evaluate(profile, eligibility)` / `evaluate(profile)`）+ `DiscoveryAdmissionModels`（状态/原因/快照）+ `DiscoveryIdentity.explainIdentity/explainInstitutionEvidence`。自动判定只搬现有规则；有效 LEGACY_APPROVED 单独返回。04 用 policy 生成自动结论，不要另写判定。
- 02：V148 两表（`expert_discovery_admission` 当前结论/CAS；`expert_discovery_review_item` 快照名单/历史/应用状态）；身份键 = SHA-256(docId ␀ normalizedEmail ␀ given ␀ family)；`DiscoveryReviewRepository` 的 `findAdmission/findAdmissions/initializeAdmission/insertItem/applyItem/revokeCurrent/findHistory/findItemsByBatch/batchStateCounts/findItemsByIds`；API 契约（experts/prepare/confirm/batch detail/history/revoke）。
- 03：`DiscoveryReviewScanService.listPage/scanAll`（统一 source 读取与筛选，每批一次 admission 读取，scroll 批 500 + finally 清理）——04 的存量初始化复用它，不另写条件；批处理状态枚举 `DiscoveryReviewBatchPhase`=PREPARING/READY/PREPARE_FAILED/APPLYING/APPLIED/CANCELLED，item 状态 STAGED/READY/APPLYING/APPLIED/STALE/FAILED/CANCELLED；端点新增 `POST /batches/{key}/retry`、`POST /batches/{key}/cancel`、`GET /batches/{key}?afterId&limit`；task type `DISCOVERY_REVIEW_PREPARE`（"发现审核名单固定"）与 `DISCOVERY_REVIEW_APPLY`（"发现审核应用"）已登记（中文名 + hasProgressUi）；计数：prepare success=inserted.size、apply success=applied / failure=failed+stale。
- 02/03 的关键冻结（04 必须遵守）：服务构造只允许追加**带默认值的尾参数**（02 测试按位置构造）；repository 方法只允许**重载**不得改签名（`applyItem`/`insertItem`/`revokeCurrent` 被 02/03 测试按参数个数调用）；`DiscoveryReviewPrepareRequest` 无 tag/q/issue/decision 字段（全页筛选走 query/内部 filter DTO）。
- 待办继承：`FlywayMigrationIntegrationTest` latest-target 仍写 147（RECORD_ONLY O-1，未授权文件，不得改）；03 的 O-2（`derivePhase` 无 INTERRUPTED）为信息项。
- 验证：01/02/03 均 `LIGHT_PASS_WITH_NOTES`；命令全绿（01: 35；02: 20 + IT 8；03: 35 + IT 13 + 目录 20）。

## 交付物

- 产品提交：`feat(fast-p): implement 04`（只含授权产品/测试文件）。
- 执行报告：`docs/plans/fast/2026-10-04-discovery-review-master/children/04/execution.md`，包含 execute-p 规定字段。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。不得声明验证通过。
