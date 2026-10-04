# Fast-P Child Brief — 01（收发件箱三态标记：存储与API）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-10-04/mailbox-progress-master.md`，identity `commit:9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4`。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-10-04/mailbox-progress-01-backend.md`，identity `commit:9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4`。「需求描述」「关键不变量」I-1～I-6、「实现方案」T-1～T-5、「变更文件清单」「验收标准」逐条生效；本 brief 是执行摘要，冲突时以计划原文为准。
- 执行授权：用户显式 `/fast-p docs/plans/2026-10-04/mailbox-progress-master.md`（2026-10-04）。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master`；branch `fast/2026-10-04-mailbox-progress-master`；`child_base_sha` 见派发消息（= seed `9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4`）。
- 依赖：none。下游：child 02（消费本片的 PUT 端点、`providedOnly` 参数与 `progressStatus` 字段，逐字见「下游接口」）。
- 取证材料（worktree 内只读）：`docs/plans/2026-10-04/mailbox-progress-evidence/`（source-manifest.json 基线哈希、grep-audit.txt 读写路径全集）。
- 基线命令结果（实施前先读对照）：`docs/plans/fast/2026-10-04-mailbox-progress-master/children/01/baseline.md`。

## 全局约束

1. 只允许修改「Authorized Files」表内 10 个文件；不得新建白名单外文件（含 fixture、工具脚本、静态资源）。其余 Kotlin/SQL/迁移/前端/文档全部只读。
2. 不得修改 `docs/plans/**`（计划与证据由控制方提交）；不得修改 `docs/plans/fast/**`；执行报告写到本 child 目录 `children/01/execution.md`（fast-p 报告/日志不进入产品提交）。
3. 不得 push、merge、rebase、squash、amend、reset；不得改写已有提交。产品代码只提交一次：`feat(fast-p): implement 01`。
4. 计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不要自行扩范围或改计划。
5. 禁止联网抓取、连线上 MySQL/ES、发信、部署；不得新增依赖；不得改 `pom.xml`。
6. 测试库必须是独立本地库：本机容器 `ti-mysql-it`（MySQL 8.0，`localhost:3306`，root/root，库 `talent_introduction`）。连接串需带 `allowPublicKeyRetrieval=true`。禁止指向线上/日常数据库。JDK 使用 `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
7. Migration IT 需要 Docker。本机为 OrbStack，既有 workaround：`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` + `-Dapi.version=1.40`。Docker 不可用或报告 skipped 不能记 PASS。
8. 迁移编号：worktree 内现有最高 `V147__create_expert_mailbox_suspension.sql`，V148 由本 worktree 之外的并行计划占用（`discovery-review-02-review.md:47,63`，其分支已建 `V148__create_expert_discovery_review.sql`），本片使用计划既定 `V149__add_expert_follow_progress_status.sql`。建迁移前复核 V149 未被占用；若已被占用或你判断 V148/V149 存在本片内的冲突 → 返回报告，不覆盖已有迁移、不改已应用迁移、不打开 outOfOrder。
9. 已知事实：`MailboxConversationRepositorySqlCompatTest` 是 `MailboxConversationRepositoryIT.kt` 内的第二个类；`CalendarAttachmentIntegrationTest` 是 `MailboxConversationControllerTest.kt` 内的第二个类。计划命令里的这两个名字是"同文件内类"，不是缺失文件。
10. 值来源纪律：三态唯一存储为 `expert_follow.progress_status`；不得引入第二张表/布尔列、不得同步 ES、不得写 `expert_contact`/`inbound_mail_processing`/`mail_record`。
11. `ExpertFollowService` 保持唯一业务写者；不新增 repository/service 层、不改构造函数签名。
12. `FlywayMigrationIntegrationTest.kt` 内 "147" 共 27 处命中：只更新表达"最新版本"的断言为 149（计划 T-1），明确测试 V147 历史行为的 target/断言不动。
13. 一切计数/结论必须来自真实 MySQL 测试实值；不得用 H2 模拟证明 MySQL，不得以基线已知失败当作通过。

## Authorized Files（10）

| # | 精确路径（相对 worktree 根） | 改动 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V149__add_expert_follow_progress_status.sql` | 单列兼容迁移（计划 T-1 的完整 SQL） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertFollowService.kt` | 三态枚举/结果/唯一写者 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | 新 PUT 端点、请求/响应 DTO、列表参数 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` | 投影/筛选/分页/排序（page+explain 同源） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt` | filter 参数与 summary 映射 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt` | 挂起读取返回 progressStatus |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 旧数据升级 + 新库最新版本断言 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 写入/身份/兼容/错误 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt` | 筛选/分页/排序/EXPLAIN |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt` | 跨路径读取一致性 |

## 关键不变量（计划 I-1～I-6）

- I-1：`expert_follow` 只加 `progress_status VARCHAR(16) NOT NULL DEFAULT 'FOLLOWING'`；行值 FOLLOWING/PROVIDED；无行=NONE；禁止 NONE 行、禁止 provided 布尔列。复合主键/FK 不变。
- I-2：username 只取 Session；非空校验；写前校验 expert_contact 存在；FOLLOWING/PROVIDED 单条 upsert（`ON DUPLICATE KEY UPDATE progress_status=VALUES(progress_status)`，不刷新 created_at）；NONE 单条按用户+contact 删除；重复相同状态不重置时间。
- I-3：旧 PUT follow 显式转 FOLLOWING；旧 DELETE 仅 `WHERE progress_status='FOLLOWING'`，不动 PROVIDED；旧回包 `{followed:boolean}` 保持；新 PUT NONE 可删任意标记行。
- I-4：summary/挂起响应 `progressStatus` 取 DB 真实值，`followed=(progressStatus==FOLLOWING)`；filter/count/page/explain 共用谓词；`followed=true AND providedOnly=true` 得空集；已提供沿用现有关注排序（最近真实来信倒序、空来信置底、contactId DESC）。
- I-5：不写其它业务表；已回复保持 `NOT EXISTS expert_follow` 整行排除（FOLLOWING 和 PROVIDED 都排除）；两个标记页允许显示挂起/待处理专家。
- I-6：GET/PUT/PATCH/DELETE suspension 经 `stateOf` 统一返回 progressStatus 与一致 followed；挂起状态/原因语义不变。

错误语义：非法/缺失 status → 400（既有机制）；未登录/空白用户 → 401；未知专家 → 404；失败不得写表。

## 实现要点（计划 T-1～T-5 摘要）

- T-1：迁移逐字为计划 SQL（VARCHAR(16) NOT NULL DEFAULT 'FOLLOWING' + COMMENT）；迁移测试先迁到上一版本、插入 ≥2 用户名旧关注行与固定 created_at，再升级，比对行数/key/created_at 全一致且状态全 FOLLOWING；新库全量迁移通过。
- T-2：`MailboxProgressStatus { NONE, FOLLOWING, PROVIDED }`、`MailboxProgressResult(contactId, progressStatus, followed)`、请求 DTO `MailboxProgressRequest(status)` 放现有文件；新 `setProgressStatus(username, contactId, status)`；新 PUT `/{contactId}/progress-status`，200 回包见「下游接口」；旧 setFollowed(true) 复用显式 FOLLOWING 写入，旧 false 条件删除。
- T-3：page/explain 行存在性投影改同源 `COALESCE((SELECT ef.progress_status FROM expert_follow ef WHERE ef.username=:username AND ef.expert_contact_id=u.expert_contact_id),'NONE') AS progress_status`；SqlRow/mapper 一次读取再派生 followed；`ConversationItemResponse` 尾部加 `progressStatus`；followed 筛选加 `progress_status='FOLLOWING'`；providedOnly EXISTS 置于消息 OR 括号外、参数化；orderByClause 把 providedOnly 归入非 pending-first 分支。
- T-4：`MailboxSuspensionService.isFollowed` 改一次读取 progress_status；stateOf 返回 progressStatus + 派生 followed；挂起响应 DTO 尾部加字段；不改挂起写入/锁/原因/计数。
- T-5：按「验收标准」在 4 个真实 DB 测试文件中覆盖六转换/幂等/用户隔离/400-401-404/分页冲突/排序/已回复水位/挂起矩阵；所有查询参数化。

## 下游接口（child 02 依赖，必须逐字实现）

- `PUT /api/mail/mailbox/conversations/{contactId}/progress-status`，body `{ "status": "NONE|FOLLOWING|PROVIDED" }` → 200 `{ "contactId": <Long>, "progressStatus": "<枚举>", "followed": <boolean> }`。
- 列表 `GET /api/mail/mailbox/conversations` 支持 `providedOnly=true`；`followed=true` 仍表示跟进中。
- 列表项与挂起响应新增 `progressStatus`；`followed` 始终等于 `progressStatus==FOLLOWING`。
- 旧 PUT/DELETE follow 端点与回包保持兼容。

## 必需命令（fresh 运行，逐条记录 exit code 与计数；与 baseline.md 对照）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests test-compile

DB_URL="jdbc:mysql://localhost:3306/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmysqlIt=true -DskipNodeTests=true -Dtest=MailboxConversationControllerTest,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT test

DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dapi.version=1.40 -DskipNodeTests=true -Dtest=FlywayMigrationIntegrationTest test
```

- 计划原文的两条 `mvn test` 命令即上表第 2、3 条；第 1 条对应「全量构建继续运行」。
- 若某命令因"指定的类不存在"而不执行，先用 grep 证明该类名是授权文件内的第二个类或确不存在，记录实际执行方式与真实计数；不得静默扩大测试范围。
- 每条命令的日志、exit code、tests/failures/errors 计数必须写入执行报告；与 baseline.md 逐条对照说明增量（新增用例通过数、基线既有失败是否不变）。

## 交付物

- 一个本地实现提交：`feat(fast-p): implement 01`（仅 10 个授权文件）。
- 执行报告：`docs/plans/fast/2026-10-04-mailbox-progress-master/children/01/execution.md`（命令与 exit/计数、基线对照、文件变更、验收标准逐条对照、DP 决策与偏差、未运行项）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
