# Fast-P Child Brief — 02（持久审核与当前页操作）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-10-04/discovery-review-master.md`，identity `commit:07beaafc111a1b14ed3c48d514db527c8a13fc31`。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-10-04/discovery-review-02-review.md`，identity `commit:07beaafc111a1b14ed3c48d514db527c8a13fc31`。「需求描述」「关键不变量」I-1～I-4、「实现方案」1～6、「变更文件清单」「验收标准」逐条生效；本 brief 摘要与计划原文冲突时以计划原文为准。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`；branch `fast/2026-10-04-discovery-review-master`；`child_base_sha = <见派发消息>`。
- 依赖：01（解释器与模型已交付，见下方「上游产出」）。下游：03（抽出同一 source 读取/筛选为 ScanService、扩展 ALL_MATCHING 与任务恢复）、04（消费 admission 表与 applyManual/recordAutomatic 接缝）、05（批量读取准入结论）、06（前端消费 02 API 契约）。
- 取证材料（worktree 内只读）：`docs/plans/2026-10-04/discovery-review-audit.md`（E5/X2/X3/X5）、`docs/plans/2026-10-04/discovery-review-evidence/`。
- 基线命令结果：`docs/plans/fast/2026-10-04-discovery-review-master/baseline.md`。

## 全局约束

1. 只允许修改「Authorized Files」表内 9 个文件；不得新建白名单外文件。其余 Kotlin/SQL/迁移/前端/文档全部只读。
2. 不得修改 `docs/plans/**` 内的计划与其他证据；本 child 唯一可写非产品文件是执行报告 `docs/plans/fast/2026-10-04-discovery-review-master/children/02/execution.md`。fast-p 报告不进入产品提交（控制方单独提交）。
3. 不得 push、merge、rebase、squash、amend、reset；不得改写已有提交。产品代码只提交一次：`feat(fast-p): implement 02`。
4. 计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不得自行扩范围或改计划。
5. 禁止联网抓取、连线上 MySQL/ES、发信、部署；不得新增依赖；不得改 `pom.xml`。
6. 测试库必须是本机容器 `ti-mysql-it`（localhost:3306，root/root，库 `talent_introduction`，连接串带 `allowPublicKeyRetrieval=true`）；禁止指向线上/日常数据库。Docker = OrbStack（testcontainers 需要 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` + `-Dapi.version=1.40`）。
7. 迁移：新增 `V148__create_expert_discovery_review.sql`（执行前复核 V148 未被占用；若被占用只修订文件名并报告，禁止覆盖/改动已应用迁移）。`FlywayMigrationIntegrationTest` 不在授权清单：如新迁移使其 latest-target 断言过期，记录为 RECORD_ONLY 观察项，不得修改该测试文件。
8. D1（历史发送政策）未定案：本片不得改动任何发送行为、不得接发送切换；不得声称"所有黑盒已清除"。
9. 保持专家事实、已有联系人状态与既有发件账号绑定；无 SMTP 调用；actor 只来自登录 session。

## Authorized Files（9）

| # | 精确路径 | 改动 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V148__create_expert_discovery_review.sql` | 新增两张相关表（字段/索引/唯一键按计划 1 逐字） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryReview.kt` | 新增实体/DTO/枚举 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryReviewRepository.kt` | 新增参数化 SQL（事务、revision CAS、分页/历史/幂等） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` | 新增查询/准备/确认/撤销；完整 source 读取与分批准入查询 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewController.kt` | 新增登录态 API（GET/POST 按计划 4） |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | 补完整 profile 读取（institutionEvidence/filterResult 装载）；不改 ES 写语义 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewServiceTest.kt` | 新增 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryReviewRepositoryIT.kt` | 新增 MySQL 事务集成 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt` | 新增 |

## 关键不变量（计划 I-1～I-4；逐字以计划为准）

- I-1：两张表各司其职（`expert_discovery_admission` 当前结论/版本/行锁/CAS；`expert_discovery_review_item` 快照名单+决策+应用状态+历史）；不另建任务表/事件总线/ES 字段；复用 TaskExecution 作任务头。
- I-2：身份键 = 真实 docId + 规范化邮箱 + givenNames/familyNames；研究方向/机构/国家/指标/分类变化不使人工批准失效；身份改变显示"身份已变化，原审核不适用"；有效人工批准优先于自动问题；HOLD/REJECTED 不被自动覆盖；REVOKE 重跑自动校验。
- I-3：prepare 提交真实 docIds + 所见版本（≤1000），服务端 STAGED 快照返回 batchKey/hash；confirm 只接受固定快照；actor 取 session；reject 备注 1–1000 必填；所有写 API 复用登录保护；无邮件发送。
- I-4：STAGED→READY→APPLYING→APPLIED，可 STALE/FAILED/CANCELLED；DB 内更新当前结论与标记 APPLIED 同事务；提交前比较 admission revision 与身份；重复 confirm 返回原结果；02 不写 ES 晋升（04 接投影）。

## 必需命令（fresh 运行，逐条记录 exit code 与计数；与 baseline.md 对照）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests test-compile
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryReviewServiceTest,DiscoveryReviewControllerTest test
DB_URL="jdbc:mysql://localhost:3306/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=DiscoveryReviewRepositoryIT test
```

（IT 门控以仓库既有 `@EnabledIfSystemProperty`/profile 事实为准；若实际门控不同，按真实门控运行并在报告中记录实际命令与原因，不得跳过或删除测试。）

## 下游接口（03/04/05/06 依赖，必须逐字实现）

- `GET /api/discovery/review/experts`（level 默认 RAW、tag 默认 discovered、from/size/q/issue/decision；服务端过滤，不只过滤当前页；返回真实事实、原因、自动/人工结果、版本、已审核人/时间）。
- `POST /api/discovery/review/batches/prepare`（scope=IDS，action、docIds、expectedRevisions、note；返回 batchKey/hash）。
- `POST /api/discovery/review/batches/{batchKey}/confirm`（仅 batchHash；幂等）。
- `GET /api/discovery/review/batches/{batchKey}`（明细计数）；`GET /api/discovery/review/history?docId=...`；`POST /api/discovery/review/items/{id}/revoke`（仅撤销当前有效决策，否则 409）。
- 表结构/状态枚举按计划 1 逐字；`DiscoveryReviewService` 的 source 读取与分批准入查询将被 03 抽为 ScanService，不得另写第二套条件。

## 上游产出（01，由控制方在派发消息中补全）

- 见派发消息中的 01 code head 与 execution.md 摘要；01 的 `DiscoveryAdmissionPolicy/DiscoveryAdmissionModels/DiscoveryIdentity.explain*` 为只读依赖。

## 交付物

- 产品提交：`feat(fast-p): implement 02`（只含授权产品/测试文件）。
- 执行报告：`docs/plans/fast/2026-10-04-discovery-review-master/children/02/execution.md`，包含 execute-p 规定字段。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。不得声明验证通过。
