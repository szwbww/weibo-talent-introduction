# Fast-P Child Brief — 03（审核所有页）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-10-04/discovery-review-master.md`，identity `commit:07beaafc111a1b14ed3c48d514db527c8a13fc31`。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-10-04/discovery-review-03-all-pages.md`，identity `commit:07beaafc111a1b14ed3c48d514db527c8a13fc31`。全部章节逐条生效；本 brief 摘要与计划原文冲突时以计划原文为准。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`；branch `fast/2026-10-04-discovery-review-master`；`child_base_sha = <见派发消息>`。
- 依赖：02（两张表、prepare/confirm/apply、查询与筛选）。下游：04（复用 03 任务/scroll 与 fail-items 语义做存量初始化）、05/06（任务状态与重试契约）。
- 取证材料（worktree 内只读）：`docs/plans/2026-10-04/discovery-review-audit.md`（E5 TaskExecution/TaskProgress、X5/X9）、`docs/plans/2026-10-04/discovery-review-evidence/`。
- 基线命令结果：`docs/plans/fast/2026-10-04-discovery-review-master/baseline.md`。

## 全局约束

1. 只允许修改「Authorized Files」表内 8 个文件；不得新建白名单外文件。其余 Kotlin/SQL/迁移/前端/文档全部只读。
2. 不得修改 `docs/plans/**` 内的计划与其他证据；本 child 唯一可写非产品文件是执行报告 `docs/plans/fast/2026-10-04-discovery-review-master/children/03/execution.md`。fast-p 报告不进入产品提交（控制方单独提交）。
3. 不得 push、merge、rebase、squash、amend、reset；不得改写已有提交。产品代码只提交一次：`feat(fast-p): implement 03`。
4. 计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不得自行扩范围或改计划。
5. 禁止联网抓取、连线上 MySQL/ES、发信、部署；不得新增依赖；不得改 `pom.xml`。
6. 测试库必须是本机容器 `ti-mysql-it`（localhost:3306，root/root，库 `talent_introduction`，带 `allowPublicKeyRetrieval=true`）；禁止线上/日常库。Docker = OrbStack（testcontainers 需 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` + `-Dapi.version=1.40`）。
7. 不新增迁移；不改既有表结构；名单不塞进 `task_execution.TEXT`；TaskProgressStore 不得作为审核持久状态。
8. D1 未定案：不得改动任何发送行为、不得接发送切换；不得声称"所有黑盒已清除"。
9. 保持既有任务框架语义（token/心跳/中断/interrupted 恢复），TaskTypeCatalog 只加中文名/进度白名单，不硬写前端字符串。

## Authorized Files（8）

| # | 精确路径 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewScanService.kt` | 新增批量读取/服务端筛选（统一列表与全页条件，每批一次 admission 读取） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` | 扩展全页 prepare、apply、恢复、retry/cancel |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryReviewRepository.kt` | 批次 CAS/进度聚合/逐 500 领取 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewController.kt` | 全页/重试/取消端点 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalog.kt` | DISCOVERY_REVIEW_PREPARE / DISCOVERY_REVIEW_APPLY 中文名与计数 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt` | 新增 10005 人、并发与恢复 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryReviewRepositoryIT.kt` | 持久幂等 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt` | 快照状态/API |

## 关键不变量（计划 I-1～I-4；逐字以计划为准）

- I-1：ALL_MATCHING 先服务端 ES scroll 每批 500 完整扫描，完成后才 READY 并返回总数/hash；名单存 review_item；失败快照不可 confirm；不得对部分名单假称所有页。
- I-2：只收当前筛选内 NEEDS_REVIEW；已通过/HOLD/REJECTED 不自动覆盖；confirm 消费已存 batchKey+hash，不再执行筛选扩张范围；新发现不加入。
- I-3：total=APPLIED+STALE+FAILED+CANCELLED+未处理；失败原因逐人保存；重启不重复已完成项；失败/未处理只有明确重试才继续；不得仅凭 202 设置成功总数。
- I-4：快照 24h 未确认过期；每项按 expected_revision+snapshot 身份 CAS，冲突 STALE；重复 confirm 返回同一任务/结果；取消只影响未应用项。

## 必需命令（fresh 运行，逐条记录 exit code 与计数；与 baseline.md 对照）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests test-compile
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryReviewAllPagesTest,DiscoveryReviewServiceTest,DiscoveryReviewControllerTest test
DB_URL="jdbc:mysql://localhost:3306/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=DiscoveryReviewRepositoryIT test
```

（IT 门控以仓库既有事实为准；不同则按真实门控运行并记录原因。10005 人 fixture 如用隔离 ES 不可得，改用可注入的 scan 层测试并如实记录替代方式，不得伪造大样本结论。）

## 下游接口（04/05/06 依赖）

- 全页 prepare/confirm/retry/cancel 端点与 batch 状态枚举（STAGED/READY/APPLYING/APPLIED/STALE/FAILED/CANCELLED、PREPARE_FAILED、24h 过期）。
- 两个 task type 名称与计数语义；重试只重领 FAILED/未处理项；STALE 需重新 prepare。
- `DiscoveryReviewScanService` 的完整 source 读取与统一筛选（04 存量初始化复用，不另写条件）。

## 上游产出（02，由控制方在派发消息中补全）

- 见派发消息中的 02 code head 与 execution.md 摘要。

## 交付物

- 产品提交：`feat(fast-p): implement 03`（只含授权产品/测试文件）。
- 执行报告：`docs/plans/fast/2026-10-04-discovery-review-master/children/03/execution.md`，包含 execute-p 规定字段。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。不得声明验证通过。
