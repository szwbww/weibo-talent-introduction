# Fast-P Child Brief — 01（收发件箱挂起：状态存储、查询与接口）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-10-03/mailbox-suspension.md`，identity `commit:c486c5c44806b5b4c4db654606c358efb94fec5a`。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-10-03/mailbox-suspension-01-backend.md`，identity `commit:c486c5c44806b5b4c4db654606c358efb94fec5a`。「需求描述」「关键不变量」I-1～I-6、「实现方案」T1～T4、「变更文件清单」「验收标准」逐条生效。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension`；branch `fast/2026-10-03-mailbox-suspension`；`child_base_sha` 见派发消息。
- 依赖：none。下游：child 01b（仅收紧原 mark-resolved 身份，不改本片写入链）、child 02（消费本片三个端点与列表挂起摘要，见「下游接口」）。
- 取证材料（worktree 内只读）：`docs/plans/2026-10-03/mailbox-suspension-evidence/`（write-read-paths.txt、frontend-usage.txt、constructor-sites.txt、migration-latest-target.txt、revision-v6-sha256.txt、revision-v6-grep.txt、revision-v6-source.md、baseline-mailbox-chat.css/js、cache-key.txt 等）。
- 基线命令结果（本 run 控制方已记录，实施前先读）：`docs/plans/fast/2026-10-03-mailbox-suspension/children/01/baseline.md`。

## 全局约束

1. 只允许修改「Authorized Files」表内 9 个文件；不得新建白名单外文件（含 fixture、工具脚本、静态资源）。其余 Kotlin/SQL/迁移/前端/文档全部只读。
2. 不得修改 `docs/plans/**`（计划与证据由控制方提交）；不得修改 `docs/plans/fast/**` 内的其它 child 目录；你的执行报告写到本 child 目录 `execution.md`，不进入产品提交。
3. 不得 push、merge、rebase、squash、amend、reset；不得改写已有提交。产品代码只提交一次：`feat(fast-p): implement 01`（fast-p 报告/日志排除在外）。
4. 计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不要自行扩范围或改计划。
5. 禁止联网抓取、连线上 MySQL/ES、发信、部署；不得新增依赖；不得改 `pom.xml`。
6. 测试库必须是独立本地库（本机容器 `ti-mysql-it`，MySQL 8.0，`localhost:3306`，root/root，库 `talent_introduction`）。连接串需带 `allowPublicKeyRetrieval=true`（connector-j 8.0.33）。禁止指向线上/日常数据库。示例：
   `DB_URL="jdbc:mysql://localhost:3306/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root`
   Docker 已运行，可用于 `FlywayMigrationIntegrationTest`（testcontainers）。
7. 迁移版本：仓库现有最高 `V146__create_expert_contact_location.sql`；本片新增 `V147__create_expert_mailbox_suspension.sql`。建迁移前复核 V147 未被占用；若被占用只修订计划中准确文件名/版本期望（返回报告），不覆盖已有迁移、不改已应用迁移。
8. 已知事实：`MailboxConversationRepositorySqlCompatTest` 是 `MailboxConversationRepositoryIT.kt` 内的第二个类（同文件）；`CalendarAttachmentIntegrationTest` 是 `MailboxConversationControllerTest.kt` 内的第二个类（同文件）。计划命令里的这两个名字都是"同文件内类"，不是缺失文件。
9. `OutboundAttachmentFlowTest:238` 手工构造 `MailboxConversationService`；本片保持其构造不变、不扩该测试。
10. 值来源纪律：一切状态/计数只来自真实表 `expert_mailbox_suspension`、`inbound_mail_processing`（真实账号口径）；不得用前端可见 50 条时间线、`mail_record` INBOUND 历史行或预览内存模型推断计数。

## Authorized Files（9）

前缀 `src/main/` 与 `src/test/` 均为相对 worktree 根路径。

| # | 精确路径 | 改动 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V147__create_expert_mailbox_suspension.sql` | 新增空表（计划 T1 的完整 SQL） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt` | 新增显式挂起/取消服务与状态/请求 DTO |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` | 共享筛选 predicates 的 suspension EXISTS/NOT EXISTS、批量状态读取方法 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt` | 透传 `suspendedOnly` 与 DTO 填真实挂起状态 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | 三个端点、列表参数与 DTO 兼容字段 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt` | 分页/账号/状态测试与清理（含同文件 SqlCompat 类，如计划要求） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 两个 context 注入、HTTP 身份、回归 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt` | 新真实 MySQL 服务测试（新文件） |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 新表验证 + 25 处 latest-target 断言 145→147（仅无 target 的最新断言；历史 target=xx 断言不动） |

## 关键不变量（计划 I-1～I-6；冲突时以计划原文为准）

- I-1 行存在就是挂起：新表 `expert_mailbox_suspension` 主键 `(username, expert_contact_id)`。有行=suspended=true；无行=false。reason=null 是未填写原因，绝不表示未挂起。没有第二个 suspended 布尔列、完成状态或墓碑。
- I-2 无自动结束写路径：只允许用户 PUT 创建、DELETE 结束；查询、markResolved、自动收信、手动发送、cancelResolved 均不删除/更新挂起行。处理完成后挂起行仍存在；新来信也不改变其存在性。
- I-3 账号与计数口径：pending = 真实 `inbound_mail_processing.process_status='MANUAL_REVIEW'`，关联同 contact。生命周期提示计数跨该专家所有真实账号，排除 `SIMULATOR_NOOP` 和不存在账号，包含 enabled=false 的真实账号；不受日期、主题、分页/时间线窗口影响。列表原 pendingCount 仍服从原账号筛选；额外返回 `suspensionPendingCount` 明确是跨账号计数。
- I-4 查询归类及分页：待处理 = 原 pending 条件 AND 无当前用户挂起行；已挂起 = 有行（不加 pending>0）；已回复 = 原全部条件 AND 无挂起行；全部和关注保留挂起专家。SQL 先筛选/分组再 LIMIT，count/page/explain 同口径。
- I-5 用户身份、幂等与原因：username 只取 Session，不能由 body/query 指定。不存在专家 404；匿名 401；首次挂起时全球 pending=0 则 409；已有挂起重复 PUT 返回原原因，不覆盖。reason 先 trim，空串转 null；最多 500 个 UTF-16 code units（与 textarea maxlength 一致），超长 400；作为纯文本存储/返回。DELETE 可重复，第二次也返回 suspended=false。
- I-6 纯附加能力：不写 expert_contact、expert_follow、expert_replied_dismissal、mail_record、inbound_mail_processing、ES。GET 不访问 IMAP、SMTP。新 DTO 属性有兼容默认值；现有 service 构造参数保持不变。

## 实现要点（计划 T1～T4 摘要）

- T1：迁移完整 SQL 见计划 T1（逐字：PK(username, expert_contact_id)、nullable reason VARCHAR(500)、FK→expert_contact(id)、ENGINE=InnoDB CHARSET=utf8mb4）。`MailboxSuspensionService` 用项目既有 NamedParameterJdbcTemplate 模式；PUT/DELETE `@Transactional`；SELECT contact `FOR UPDATE` 串行化同专家操作；PUT 先校验原因长度/trim → 已有行原样返回 → 无行再校验 pending → INSERT IGNORE；DELETE 只按 Session+contact 删除并回读实时状态；SQL 参数绑定。计数查询 `inbound_mail_processing imp JOIN mail_sender_account msa ON msa.account_code=imp.sender_account_code`，WHERE contact/status 且 `msa.account_code <> :simulatorCode`（不 enabled、不 date）。
- T2：GET/PUT/DELETE `/api/mail/mailbox/conversations/{contactId}/suspension`，状态对象固定 `{ contactId: Long, suspended: Boolean, suspendReason: String?, suspensionPendingCount: Long, followed: Boolean }`。HTTP 非法输入 400、不存在 404、未登录 401、无未处理不能新挂起 409。显式取消允许仍有未处理。
- T3：controller 追加 `suspendedOnly=false`（service/ConversationFilter 新增参数放末尾、默认 false，保留原调用兼容）；共用 expertPredicates 增加当前用户 suspension EXISTS（suspendedOnly 用 EXISTS；pendingOnly/repliedOnly 用 NOT EXISTS；冲突条件按 AND 得空集）；orderByClause 把 suspendedOnly 纳入已选队列分支；count/page/explain 同筛选。新增 repository `suspensionStatesByContacts(username, contactIds)` 一次批量读取（MySQL 5.7 兼容，禁止窗口函数/N+1；空 id 集合直接返回）。DTO 末尾新增 `suspended=false`、`suspendReason=null`、`suspensionPendingCount=0`，逐项填真实值（新表无行时也要真实跨账号 pending，不能当 0 展示为已挂起）。
- T4：真实 MySQL 验证表存在性/事务/隔离用户/SQL 筛选/分页；新测试独立库、唯一数据、finally cleanup。两条处理入口回归断言挂起表保持；不要为测试新增生产 hook。

## 下游接口（child 02 依赖，必须逐字实现）

- `GET /api/mail/mailbox/conversations/{contactId}/suspension` → 状态对象（字段名/类型如上）。
- `PUT` 同路径，body `{ "reason": null }` 或字符串 → 创建/幂等回读状态对象。
- `DELETE` 同路径 → 取消后的状态对象。
- 列表 `GET /api/mail/mailbox/conversations` 支持 `suspendedOnly=true`；返回项新增 `suspended`、`suspendReason`、`suspensionPendingCount`（旧字段/旧行为不变）。
- 错误语义：400/401/404/409（不许未登录返回 200）。

## 必需命令（fresh 运行，逐条记录 exit code 与计数；与 baseline.md 对照）

```sh
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -DskipTests test-compile
DB_URL="jdbc:mysql://localhost:3306/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=MailboxConversationRepositoryIT,MailboxConversationRepositorySqlCompatTest,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailboxSuspensionServiceIT test
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -DmigrationIt=true -Dapi.version=1.40 -Dtest=FlywayMigrationIntegrationTest test
```

- Migration IT 需要 Docker。本机为 OrbStack，docker-java 固定 client API 1.32 会报 "client version 1.32 is too old"；仓库既有的 documented workaround 是 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` + `-Dapi.version=1.40`（见 docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c7/execution.md）。缺失 Docker 时记录未运行，不以 skip 算通过。
- 若某命令因"指定的类不存在"而不执行，先用 `grep` 证明该类名是授权文件内的第二个类或确不存在，记录实际执行方式（例如去掉不存在项）与真实计数；不得静默扩大测试范围。
- 不得以基线已知失败当作本次通过；所有计数与 baseline.md 对照说明增量。

## 交付物

- 一个本地实现提交：`feat(fast-p): implement 01`。
- 执行报告：`docs/plans/fast/2026-10-03-mailbox-suspension/children/01/execution.md`（记录：命令与 exit code/计数、基线对照、文件变更、验收标准逐条对照、DP 决策与偏差、未运行项；不进入实现提交）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
