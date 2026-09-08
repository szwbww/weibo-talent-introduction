# Child 07 执行报告 — 专家会话查询与关注持久化

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/docs/plans/2026-09-07/07-expert-conversations-follow.md`
- Plan SHA-256: `e8a76f6927663f12ce84ab44181281c25e13a33190d39013b2ea080460038122`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/docs/plans/2026-09-07/07-expert-conversations-follow.md@e8a76f6927663f12ce84ab44181281c25e13a33190d39013b2ea080460038122`
- Execution epoch: NEW
- Executor: Impl07
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials` @ branch `fast/mailbox-materials` @ git-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-materials`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials@fast/mailbox-materials@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-materials`
- Base SHA（child 06 code head）: `fb913c58f375f51eb6284d8c3b449134258811f4`；执行前 HEAD `a2521a7bce7325e05a2950c2ce7cb8a0f096127b`（06 验证记录，docs-only）
- Result: **READY_FOR_VERIFICATION**
- Commit: `feat(fast-p): implement 07`（10 个授权文件；docs/plans/fast/** 未纳入提交）

## 变更文件（= 计划 10 文件清单，逐字核对无增删）

| # | 文件 | 操作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V121__create_expert_follow.sql` | 新增（expert_follow：复合 PK (username, expert_contact_id)、contact FK、created_at DATETIME NOT NULL；仅当前 admin，无用户表） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertFollowService.kt` | 新增（含 FollowResult DTO；全部参数化 JDBC：INSERT IGNORE / DELETE；contact 存在性 JDBC 校验；无 Spring Data 依赖） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` | 新增（归一化 UNION SQL 基础 + count/page/latestMessage/latestInbound/accountCodes/materialCount/timeline + EXPLAIN 诊断 + ConversationFilter/Keyset/行 DTO） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt` | 新增（列表/单专家 timeline 装配；(time,source,id) 游标编码/校验并绑定 contact/account scope；参数白名单/校验） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | 新增（GET conversations / GET messages / PUT / DELETE follow + API DTO；Session AUTH_USERNAME；缺登录 401 防御） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt` | 新增（mysqlIt G-1 真实 MySQL 门禁：12 用例） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 新增（mysqlIt；@WebMvcTest slice + 真实 ExpertFollowService/真实仓库 + 真实 MySQL/Flyway：10 用例） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改（全链目标 120→121（9 处）+ 测试名 + 新增 `V121 creates expert_follow with composite ownership key and contact FK`） |
| 9 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt` | 修改（resolveAttachments 委托 06 `ExpertMaterialService.resolveMessageAttachments`；停用无账号/专家/方向限定的 `findFirstByMessageIdOrderByCreatedAtDesc` 回退） |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt` | 修改（delegate mock 注入；4 个附件契约用例：exact bridge 透传 / 严格唯一旧关系 / 跨账号歧义拒绝 / hasAttachment；fallback 永不触达 verify） |

未触碰：MailboxController / MailRecordRepository / ExpertContactRepository / GlobalExceptionHandler / 任何既有迁移 / 前端 / docs/plans/fast/**（controller-owned，未提交）。

## 命令证据（全部在工作树根，JAVA_HOME=zulu-11，最终代码状态）

| # | 命令 | 结果 | 证据 |
|---|---|---|---|
| 1 | `mvn -Pmysql-it -Dtest=MailboxConversationRepositoryIT test` | PASS（exit 0，BUILD SUCCESS） | **12 run / 0 fail / 0 err / 0 skip**（真实 MySQL 127.0.0.1:3306/talent_introduction；Flyway 启动迁移至 V121；无 mock 证明 GROUP BY） |
| 2 | `mvn test -Dtest=MailboxConversationControllerTest,MailboxServiceTest` | PASS（exit 0，BUILD SUCCESS） | MailboxServiceTest **20 run / 0 fail**；MailboxConversationControllerTest 按 mysqlIt 门禁跳过（0 run/1 skipped，仓库约定：普通 mvn test 不依赖 DB）。同命令带 `-Pmysql-it` 显式启用后：MailboxConversationControllerTest **10 run / 0 fail / 0 err**（真实服务 + 真实 JDBC + 真实 MySQL）——按总计划「数据库 IT 必须显式启用，不用默认跳过当证据」记录 |
| 3 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test` | PASS（exit 0，BUILD SUCCESS） | **16 run / 0 fail / 0 err**（含新增 V121 契约用例；裸调用 Docker API 版本不匹配属 baseline 已知环境问题，见下） |
| 4 | `mvn test`（全量，含 Node exec） | PASS（exit 0，BUILD SUCCESS，02:58） | surefire 汇总 **3247 run / 0 fail / 0 err / 9 skipped**（skipped = mysqlIt/migrationIt 门禁 IT 与既有方法级 skip，按设计跳过；plain 全程无 Docker/DB 依赖）；node tests **671 pass / 0 fail / 0 skipped**；node --check app.js / task-modal-runtime.js 通过 |

补充运行（同一最终状态）：`mvn -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxConversationControllerTest test` → RepositoryIT 12 + ControllerTest 10 全绿（22 run / 0 fail）；`mvn test -Dtest=OperatorStatusWriteSeamGuardTest` → 1 run / 0 fail（MailboxService.kt:168 行号钉点未移位）。

## 不变量核对

### I-1 会话权威来源（同信只计 1；DB 先聚合专家再分页；稳定事件键）
- 归一化 UNION = OUTBOUND mail_record UNION ALL linked inbound_mail_processing（排除未匹配/机器信：expert_contact_id 非空 + JOIN expert_contact）；INBOUND mail_record 永不进入计数。RepositoryIT `mixed record kinds...`：A 专家 2 封 SENT + 2 封历史 INBOUND mail_record → receivedCount=0（INBOUND record 不计入），sent=2；latestInbound 只取 processing（真实 id 断言）。
- 先 DB 聚合再分页：page SQL 内 GROUP BY expert + ORDER BY latest_event_at DESC, expert_contact_id DESC + LIMIT/OFFSET（无内存分组）。
- 事件时间 = received_at / COALESCE(sent_at, created_at)；稳定键 (event_at, source_rank, id)。
- 同 timestamp 分页：RepositoryIT `same-timestamp pagination...`：60+4 位专家同秒事件，size=17 翻页 → 64 位全部出现、无重复/缺失、页内与全局顺序均严格 contactId DESC（真实 MySQL）。

### I-2 等待与待处理
- 聚合在账号范围全历史；方向/日期/主题/标签只经 EXISTS 限定 membership，不改计数。RepositoryIT `date filter only gates membership...`：C（10 天前来信 + 今日发件）在最近 7 天筛选下仍收到 received=1（不因过滤变「未回复」）；窗口外专家不出现；direction=INBOUND 只按来信 membership。
- sent 只计 SENT、FAILED 单列 failedCount：`waiting and pending...`：A（2 SENT）waitingReply 命中；B（FAILED-only）不命中；C（received>0）永不 waiting。SENT 计发/FAILED 不计的 SQL 谓词 + IT 双证据。
- pendingCount/pendingOnly 复用现有 processing 谓词 `process_status='MANUAL_REVIEW'`（与 MailRecordRepository 现有谓词逐字一致）。
- 账号范围收窄：`account range narrows...` 断言 accountCode=acc-a 只计 acc-a 全历史。
- 多标签：`multi-label membership never duplicates rows`：同专家两个 CUSTOM label（跟进/材料）各自筛选都只返回一行（label 在 EXISTS 内 join，绝无重复行）；direction=OUTBOUND + label → 空集（单消息合取语义，标签只存在 inbound 侧）。

### I-3 关注所有者
- expert_follow 复合主键 (username, expert_contact_id) + contact FK（FlywayMigrationIntegrationTest V121 用例：列类型/主键列序/重复拒绝/跨用户允许/FK 拒绝/created_at 无默认）。
- 身份只从 Session AUTH_USERNAME；body 永不携带 username。ControllerTest：PUT 10 次仅 1 行且 created_at 不刷新；8 线程真实服务并发 PUT 仍 1 行；body `{"username":"attacker"}` 被忽略（owner 仍是 session 用户）；DELETE 两次幂等；匿名 PUT/DELETE 401（真实 AuthInterceptor）；会话用户名不存在 401 且零写入；op1/op2 关注互相不可见（列表 followed 标志与 followed=true 筛选）。
- 写者 = 唯一 ExpertFollowService（参数化 JDBC，无 toggle）。

### I-4 详情加载与既有入口
- summary 只返回聚合 + latestMessage（source/id/direction/subject/preview/time/sendStatus 投影）+ latestInbound{真实 processingId, accountCode, messageId, receivedAt}|null + materialCount（expert_document COUNT），**绝不携带正文**：ControllerTest 断言 item 无 body/cleanedBody 键、latestMessage 只有 7 个投影键。timeline 消息 DTO（含 body/cleanedBody、attachmentCount、firstAttachmentNames≤3、messageId/inReplyTo、sendStatus/processStatus、eventAt）只对当前专家加载。
- 附件元数据复用 06 精确来源解析（MailboxService.resolveAttachments → ExpertMaterialService.resolveMessageAttachments：bridge/直接 owner/严格唯一旧关系/歧义拒绝；无账号专家方向限定的 findFirstByMessageId 回退停用）。MailboxServiceTest 4 用例 + verify(never) 旧回退。
- timeline 默认 50/上限 100、正序最新窗口；(time,source,id) 游标编码/校验并绑定 contact/account scope：ControllerTest 跨专家复用 400、换账号 scope 复用 400、乱码 400；RepositoryIT 同 timestamp 两来源 keyset 分页无重复/缺失且严格单调。
- GET 从不调用 IMAP；legacy task-execution drill-down 与未匹配来信入口未改动（MailboxController/MailRecordRepository/UnmatchedInboundMail* 零触碰；MailboxTaskExecutionFilterTest 原样通过）。
- 未匹配来信不伪造「未知专家」进会话列表：UNION 只含 expert_contact_id IS NOT NULL 行。

### G-1 真实 MySQL 证据（本 child 的 SQL 门禁）
- MailboxConversationRepositoryIT（mysqlIt）在 127.0.0.1:3306/talent_introduction 上真实执行全部 SQL（Flyway 启动迁移含 V121）：multi-label 无重复行、混合记录种类、同 timestamp 稳定分页（64 专家跨 4+ 页无重复/缺失）、EXPLAIN 计划可执行且 expert_contact 不走全表扫描、latestInbound 真实 processing id——不依赖内存 mock。

## 关键设计决策/偏差（写入执行记录，供 verify-p 对照）

1. **@WebMvcTest slice 承载 controller 集成测试（必要偏差）**：全量 @SpringBootTest 会触发 **child 06 之前就存在**的跨 controller 映射冲突（`document/controller/ExpertMaterialController` GET /api/expert-contacts/{contactId}/materials 与 `campaign/controller/ExpertContactManagementController#listMaterials` 同路径；06 未做全量 context 启动验证，普通 mvn test 又全部跳过全量 boot 测试，故此前未暴露）。该冲突不在本 child 授权文件清单内（需改 06/既有 campaign controller），按「编译/测试证明需要未列文件 = PLAN_CONFLICT」规则不能顺手修复；已在剩余担忧上报。ControllerTest 改用 @WebMvcTest + @Import 真实 ExpertFollowService / MailboxConversationRepository / MailboxConversationService + @TestConfiguration 提供真实 MySQL DataSource + Flyway（迁移至 V121）+ JdbcTemplate；外围 Spring Data 仓库（AuthService/ExpertContactRepository/MailSenderAccountRepository）与 06 附件解析用 @MockBean。**关注幂等/身份断言全部走真实服务 + 真实 JDBC 落库**（不 mock 成功返回）；GET summary/timeline 的真实 SQL/分组/游标逻辑同样在真实 MySQL 上执行。
2. **ExpertFollowService 去 Spring Data 依赖（必要偏差）**：contact 存在性校验由 `expertContactRepository.existsById` 改为同文件参数化 JDBC `SELECT COUNT(*) FROM expert_contact WHERE id=:contactId`（404 语义不变）。原因：测试要求「真实 ExpertFollowService 配测试数据库/受控 JDBC」，纯 JDBC 服务可在 web slice 内以真实 DataSource 直连测试库，无需整包 Spring Data context；也符合计划「参数化 JDBC 写方法」的表述。
3. **MailboxService 新依赖注入采用「可空默认参 + `) {` 收行合并」零行号方案（必要偏差）**：OperatorStatusWriteSeamGuardTest 把 `MailboxService.kt:168`（`operatorStatus = summary.operatorStatus`）钉死且不在本 child 授权文件清单（K-line-number-guard-breaks-on-any-insertion 要求把该测试列入变更清单才能改行号）。为不越界修改未授权守卫测试，构造函数追加第 7 参 `expertMaterialService: com.weibo.talentintroduction.document.service.ExpertMaterialService? = null`（全限定名、不新增 import 行），并把 `) {` 合并进末参行——168 行上下文零位移（守卫测试实测通过）。Spring 容器注入真实 bean；null 只在既有 6 参构造的单元测试中出现（它们不触达 resolveAttachments）；MailboxServiceTest 显式传 mock。语义上 06 已有同型依赖先例（MailboxAttachmentService 构造注入同 bean）。
4. **summary 排序键取「账号范围全历史最新事件」**：列表最新活动排序与每行 latestMessage/latestInbound/计数口径一致（同一次账号范围全历史聚合），方向/日期/主题/标签筛选只决定 membership（I-2：不能据过滤后计数推断回复状态）。已在 RepositoryIT/ControllerTest 用 C 专家（7 天窗口外来信 + 窗口内发件）证明。
5. **label 语义 = 单消息合取 + 只 join 对应来源**：label 只存在于 inbound_mail_tag（inbound 侧）；direction=OUTBOUND + label 无消息可满足 → 空集；direction=null + label → 仅 inbound 侧 EXISTS（含日期/主题条件）。避免 outbound 行绕过 label 限制。
6. **cursor scope = 请求的 contact + accountCode 参数（非消息自身账号）**：encode 以请求 scope 写入，decode 与当前请求 scope 比对，跨专家/换账号复用一律 400。边界 = 正序窗口最旧一条（items.first()，RepositoryIT 的 DESC 行集用 rows.last()，两者一致）。
7. **summary item 的 `institution` 恒 null（必要偏差/数据诚实）**：expert_contact 无机构列、MySQL 无 institution 存储（live schema 核对）；master summary 字段表列了 institution 但没有任何授权数据源（ES 画像不属 DB SQL 契约且逐行取 ES 违反「先 DB 聚合」）。实现保留 `institution: String? = null` 键（字段存在、值为 null），由前端从专家资料（ES 画像）流程补充展示，不伪造 DB 事实。若验收认为该字段必须真实数据，需要上层决策（追加 ES 富化或从契约删除），见剩余担忧。
8. **待处理/等待互斥为语义互斥（服务端 AND）**：waitingReply/pendingOnly 都基于账号范围全历史计数/谓词；两者同时置 true 无匹配行（语义上互斥），UI 层单选项由 child 10 控制。
9. **RepositoryIT EXPLAIN 断言为结构性 sanity**：断言 EXPLAIN 可执行（参数化 EXPLAIN 在 MySQL 8.0.36 预编译通过）、产出行非空、expert_contact 访问类型非 ALL、两次执行结果一致。不做脆弱的 key/rows 精确断言（数据量小时优化器计划会变化）。
10. **accountCodes 输出去重排序**：GROUP_CONCAT(DISTINCT account_code ORDER BY account_code)；范围收窄（accountCode 参数）时只列该账号。

## 剩余担忧

1. **06/campaign materials 映射冲突（生产启动阻断，07 之前已存在）**：`document/controller/ExpertMaterialController`（fast-p 06 新增）与 `campaign/controller/ExpertContactManagementController` 都映射 GET `/api/expert-contacts/{contactId}/materials`；任何全量 Spring context 启动（生产 boot、全量 @SpringBootTest）都会抛 Ambiguous mapping。06 验证只跑 slice/单元测试未暴露。修复需要改 06 或 campaign controller（非本 child 授权文件）；建议协调方对 child 06 出具修复项（或 11 资源注册前统一收口），并在任何真机启动验证前处理。
2. **summary `institution` 恒 null**：无 MySQL 数据源；若验收要求非空，需上层决策（引入 ES 画像富化或修订契约字段）。
3. **本 child 未验证真实前端**（child 10 范围内）；GET 端点的字段契约按 master API 契约表落地，最终以 child 10/11 消费为准。
4. Flyway IT 的 Docker API 版本基线问题（docker-java 1.32 vs OrbStack daemon min 1.40）已用 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock -Dapi.version=1.40` 绕过，属 baseline-reproduced，非本 child 引入。

## Freshness

- Plan identity rechecked: YES（SHA 未变，执行前后两次核对一致）
- Worktree identity rechecked: YES（branch/HEAD/git-dir 未变）
- Reported commit reachable from target branch: YES（提交后核对，见提交输出）
- Required commands run this invocation at final state: YES（四条命令均在最终代码状态最新跑过，exit 0）
- Historical evidence used only as baseline: YES

## 提交后核对

- 提交 SHA：见提交输出；提交文件 = 上表 10 个（`git show --stat` 核对），docs/plans/fast/** 未包含。
- 未 push / merge / amend / rebase；HEAD 仍指向新提交且仅含本实现。

---

## Epoch 2（Amendment A1，2026-09-08 HUMAN 批准；fix_round 重置为 0）

- Amended plan SHA-256: `b133c54ae0138be80b4ce7a67d7a489905baa22ecbfb22d33c2e53d749981337`
- Execution ID (epoch 2): `…/07-expert-conversations-follow.md@b133c54ae0138be80b4ce7a67d7a489905baa22ecbfb22d33c2e53d749981337`
- Epoch-1 commit `e0fa706`（10 文件）stands in ancestry；epoch-2 HEAD 起点 `06bea85`（docs-only）
- Result: **READY_FOR_VERIFICATION**；Commit: `feat(fast-p): implement 07 epoch 2`

### A1 变更（epoch-2 提交 = 2 个授权文件）

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` | A1：退役 campaign 旧 feed 的 `@GetMapping("/{contactId}/materials")` 映射（与 child 06 `document/ExpertMaterialController` class-level `/api/expert-contacts/{contactId}/materials` + `@GetMapping` 完全同模板 → Spring 启动 Ambiguous mapping，生产无法启动）。删除的只有该 GET 映射注解行（净零行号：`OperatorStatusWriteSeamGuardTest` 钉死本文件 :564 `operatorStatus = operatorStatus`，A1 未授权该守卫测试，行号不可位移）。PUT `/materials/{materialCode}`、detail 与其余端点全部不动。 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 追加第二顶层类 `MailboxMaterialsDualControllerMappingTest`（@WebMvcTest 同挂 document ExpertMaterialController + campaign ExpertContactManagementController，构造依赖全 @MockBean；**不挂 mysqlIt**，普通全量即回归）。修复前：context 加载即抛 Ambiguous mapping（实测 ERROR）；修复后：1 run / 0 fail，GET materials 唯一由 06 新材料 API 提供、campaign PUT updateMaterialStatus 仍在。 |

### A1 执行中的边界决策（偏差记录）

1. **只删除 GET 映射注解、保留无路由委托方法**：A1 授权文件 = 原 10 文件 + `ExpertContactManagementController.kt`；未授权文件 `campaign/controller/ExpertContactManagementControllerTest.kt` 直接以 `controller.listMaterials(1L)` 调用该方法（单元级）。整方法删除会使未授权测试编译失败（越界修改禁止）；campaign `ExpertMaterialService.listMaterials` 另有服务内部调用方且其文件不在 A1 清单，不能动。故保留不带 `@GetMapping` 的委托方法（不参与路由、HTTP GET materials 已退役并 404→由 06 controller 提供），代码注释注明后续子计划把 `ExpertContactManagementControllerTest` 列入变更清单后可删除方法与对应用例。生产影响与整方法删除完全一致（路由层退役是本缺陷的唯一生产影响）。
2. **净零行号守卫**：A1 改动落在守卫测试钉死的 `ExpertContactManagementController.kt:564` 之上；以「1 行注释替换 1 行注解」保持净零位移，守卫测试通过。
3. **前置失败证据**：临时恢复 `@GetMapping` 后运行 `MailboxMaterialsDualControllerMappingTest` → 1 run / 1 ERROR（Ambiguous mapping，context 加载失败），随后还原修复态并复跑 1 run / 0 fail——回归测试满足「pre-fix 失败、post-fix 通过」。

### Epoch-2 命令证据（最终状态重新全跑）

| 命令 | 结果 |
|---|---|
| `mvn test -Dtest=MailboxMaterialsDualControllerMappingTest` | PASS — 1 run / 0 fail（post-fix）；pre-fix 实测 1 ERROR（Ambiguous mapping） |
| `mvn -Pmysql-it -Dtest=MailboxConversationRepositoryIT test` | PASS — 12 run / 0 fail / 0 err |
| `mvn -Pmysql-it -Dtest=MailboxConversationControllerTest,MailboxServiceTest test` | PASS — 30 run / 0 fail / 0 err（ControllerTest 10 + MailboxServiceTest 20） |
| `mvn test -Dtest=MailboxConversationControllerTest,MailboxServiceTest`（plain） | PASS — MailboxServiceTest 20/0；ControllerTest 门禁跳过（1 skipped） |
| `DOCKER_HOST=…orbstack… mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test` | PASS — 16 run / 0 fail / 0 err |
| `mvn test`（全量） | PASS — **3248 run / 0 fail / 0 err / 9 skipped**（+1 = 双控制器映射回归）；node 671/671；BUILD SUCCESS 03:04 |

### Epoch-2 剩余担忧

- campaign `ExpertContactManagementController.listMaterials` 无路由委托方法与 `ExpertContactManagementControllerTest.listMaterials delegates…` 用例成为待清理残留（A1 文件边界外）；建议后续 child 把该测试文件列入变更清单后一并删除。
- epoch-1 剩余担忧（summary `institution` 恒 null）不变。
