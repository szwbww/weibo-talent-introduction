# 收发件箱三态标记 01：存储与API

日期：2026-10-04；待审阅。前置：主计划代码基线及迁移编号协调完成。本子计划10个文件，不改前端。

## 需求描述

提供按登录用户持久化的 NONE / FOLLOWING / PROVIDED 三态；原关注自动成为跟进中；支持独立跟进中、已提供筛选和真实分页。给前端提供列表、写入回包、挂起返回三处一致的状态事实。

必须保留：用户名隔离、未知专家404、旧关注接口幂等、首次created_at；原消息数据/处理状态/发件/专家资料；账号及其他筛选、排序、已回复水位、挂起原因/处理数。
范围外：UI、权限体系、状态历史、自动提供识别、批量操作、同步ES、重命名表、新索引、接口乐观锁。并发以数据库最后执行的显式状态写入为准，不承诺跨浏览器冲突检测。

## 关键不变量

### Invariant I-1: 三态唯一表示
- Rule：`expert_follow`新增且只新增 `progress_status VARCHAR(16) NOT NULL DEFAULT 'FOLLOWING'`。存在行只写FOLLOWING或PROVIDED；无行才是NONE；禁止存NONE行或另加provided布尔列。API枚举包含三个值。现有复合主键和FK不变。
- Applies to：V149、ExpertFollowService全部写入、新旧端点、三个读取投影。
- Violation consequence：双态共存、旧记录迁移丢失、已回复误排除。
- 来源：original；V121与服务源码。

### Invariant I-2: 原子、幂等、用户隔离
- Rule：用户名只取Session AUTH_USERNAME，不接受body/query用户名。非空校验；每次写入验证expert_contact存在。FOLLOWING/PROVIDED用单条upsert，NONE用单条删除；重复相同状态不重置created_at。无行→新标记记录当前created_at，取消后再次标记按新行时间。
- Applies to：新PUT progress-status、旧PUT/DELETE follow、服务方法。
- Violation consequence：并发插入两行、串用户、首次时间变化、未知ID悬挂。
- 来源：original，沿用ExpertFollowService现有验证。

### Invariant I-3: 旧接口只有一个写者
- Rule：继续使用ExpertFollowService；旧PUT follow显式转FOLLOWING，旧DELETE follow仅删除FOLLOWING行，对PROVIDED不做删除；返回旧 `{followed:boolean}`，不新增第二套表/同步器。新PUT NONE删除当前用户任意标记行。
- Applies to：controller旧方法、setFollowed、新setProgressStatus。
- Violation consequence：旧网页取消关注误删已提供，双写分叉。
- 来源：original；生产唯一写者审计。

### Invariant I-4: 读写同义、分页前筛选
- Rule：summary及挂起响应的progressStatus取DB真实值；followed=(progressStatus==FOLLOWING)。跟进中与已提供的filter、total、page、explain共用谓词；followed=true AND providedOnly=true得到空集，不悄悄取其一。已提供按现有关注排序：最近真实来信倒序、空来信置底、contactId DESC，不因标记或发送时间提升。
- Applies to：Repository两份SELECT、expertPredicates、params、orderByClause、ResultSet mapper、Service、Controller。
- Violation consequence：两Tab重复、total错、第一页正确第二页漏人、EXPLAIN不是实际SQL。
- 来源：K-group-before-pagination。

### Invariant I-5: 独立于消息处理；已回复保持存在性排除
- Rule：不写expert_contact、ES、inbound_mail_processing、expert_replied_dismissal、expert_mailbox_suspension或mail_record。已回复现有NOT EXISTS expert_follow保留：FOLLOWING和PROVIDED均排除，NONE还须满足真实OUTBOUND/SENT、来信水位、未挂起等条件才入已回复。两个标记页允许显示挂起/待处理专家，不把这些队列扩成全局互斥。
- Applies to：所有状态写入、expertPredicates、挂起读模型。
- Violation consequence：取消标记自动变已处理、伪造已回复、破坏挂起。
- 来源：K-mailbox-replied-membership；现有SQL的NOT EXISTS整行判断。

### Invariant I-6: 挂起响应可完成归类
- Rule：GET/PUT/PATCH/DELETE suspension经stateOf统一返回progressStatus及一致followed；挂起状态及原因只由既有挂起逻辑管理。新的已提供状态不能因为旧isFollowed仅判断行存在而被说成跟进中。
- Applies to：MailboxSuspensionService.stateOf/readState及其全部调用端点。
- Violation consequence：已提供专家结束挂起后误跳跟进中，详情缓存失真。
- 来源：original；跨模块读取实证。

## 现状审计

### expert_follow：唯一改变的持久存储

Schema：`src/main/resources/db/migration/V121__create_expert_follow.sql:10`。列username VARCHAR(64) NOT NULL、expert_contact_id BIGINT NOT NULL、created_at DATETIME NOT NULL；PK(username,expert_contact_id)，FK指向expert_contact(id)，InnoDB/utf8mb4。不存在provided或progress字段。

生产写路径（已对src/main/scripts/ops/config反查，完整命中见证据grep）：
1. `mail/service/ExpertFollowService.kt:27` setFollowed：40 INSERT IGNORE创建，51 DELETE删除；只有这一个业务写者。
2. `mail/controller/MailboxConversationController.kt:253,364`旧PUT/DELETE follow调用上述服务；不是独立SQL写者。
3. `V121`建表；`V147`仅注释提到expert_follow，无写入。未找到脚本、提升、回填、同步钩子写此表。FK未定义ON DELETE CASCADE，不新增删除语义。

完整生产读路径：
1. `mail/repository/MailboxConversationRepository.kt:179` pageConversations：EXISTS行即followed。
2. 同文件220 explainConversationsPage：复制上述投影，必须同步改。
3. 同文件674 expertPredicates：followed过滤，count/page/explain均消费。
4. 同文件699 repliedOnly：NOT EXISTS标记行，保持整行排除。
5. `mail/service/MailboxSuspensionService.kt:196` isFollowed：COUNT行存在，stateOf:174消费；GET、创建、改原因、删除挂起共享stateOf。
6. `mail/service/MailboxConversationService.kt:129,165`传filter与summary；`mail/controller/MailboxConversationController.kt:57,201`公开followed字段及参数。

测试夹具读写（不是生产写者）：`FlywayMigrationIntegrationTest.kt` V121测试1706起；`MailboxConversationControllerTest.kt` 关注测试231起与cleanup；`MailboxConversationRepositoryIT.kt` 关注归属312起、已回复217起、挂起700起、cleanup；`MailboxSuspensionServiceIT.kt:169`插入及cleanup。已有省略新列的INSERT依靠DEFAULT FOLLOWING继续有效。完整命中收录 `mailbox-progress-evidence/grep-audit.txt`。

### 只读依赖与交互点

- expert_contact：ExpertFollowService:29校验存在；不改表。邮件与账号：ConversationService:123只取激活真实账号；Repository现有聚合/过滤不改。
- P-1 旧关注写入→迁移→列表：默认FOLLOWING必须保留原用户名、contact、created_at及归属。
- P-2 新标记写入→count/page/explain→两个Tab：同一DB状态、过滤分页前执行。
- P-3 标记写入→挂起响应→前端结束挂起：progressStatus需进入挂起统一响应。
- P-4 标记写入→已回复NOT EXISTS：取消不会改水位，不保证必然出现在已回复。
- P-5 旧接口与新接口交替：同一表同一写者，旧DELETE不删除PROVIDED。

### 接口、错误与测试设施

- Controller:374从Session取用户名；写端点缺失用户401。新端点另显式拒绝空白用户，不引入新auth路径。
- `common/controller/GlobalExceptionHandler.kt:18,26,60`：IllegalArgumentException与无效JSON400，NoSuchElementException404。新DTO用enum使非法状态和缺失status由既有机制拒绝，不为此增改异常处理器。
- 当前三份邮件DB测试均有 `mysqlIt=true` 门禁；测试application.yml默认连本地库且cleanup会删除夹具表数据。**必须显式指定独立可清空测试库，不使用生产连接或开发业务库。**
- FlywayMigrationIntegrationTest:21以migrationIt门禁，mysql:8.0.36 Testcontainers；多处把最新版本写死147，文件需同步本次新最新版本。SQL不用窗口函数、CHECK强制生效或MySQL8专属upsert语法。
- 当前实体迁移最高147，另一个计划已预留148；本计划文件149待发布前协调，禁止覆盖或启用outOfOrder绕过。

## 实现方案

### T-1：兼容迁移（I-1/I-2）
文件：`src/main/resources/db/migration/V149__add_expert_follow_progress_status.sql`、`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`。

```sql
ALTER TABLE expert_follow
    ADD COLUMN progress_status VARCHAR(16) NOT NULL DEFAULT 'FOLLOWING'
    COMMENT 'FOLLOWING=跟进中；PROVIDED=已提供；无行=未标记';
```

不改V121，不加索引/新表。服务限制写入域为两个值；不宣称MySQL5.7会强制CHECK。迁移测试先迁移到上一已协调版本，插入至少两个用户名的旧关注行和固定created_at，再升级149；比对行数、key、时间全部一致，状态全FOLLOWING。新库全量迁移也通过。该测试文件只更新表达“最新版本”的147断言，不改明确测试V147历史行为的target/断言。

执行前重新列出全部版本及并行计划：149若已占用、148仍可能晚于149交付，先修订本计划编号/发布顺序；不得静默改文件名或自行部署。

### T-2：单一服务与显式接口（I-1/I-2/I-3/I-5）
文件：`mail/service/ExpertFollowService.kt`、`mail/controller/MailboxConversationController.kt`。

- 在现有服务文件中定义 `MailboxProgressStatus { NONE, FOLLOWING, PROVIDED }` 与 `MailboxProgressResult(contactId, progressStatus, followed)`；不拆新repository/service层。请求DTO `MailboxProgressRequest(status: MailboxProgressStatus)` 放现有controller文件。
- 新 `setProgressStatus(username,contactId,status)` 沿现有存在性检查。有效标记使用 `INSERT ... (username,expert_contact_id,created_at,progress_status) VALUES (...) ON DUPLICATE KEY UPDATE progress_status=VALUES(progress_status)`；重复或转态不刷新created_at。NONE按用户+contact删除。
- 新PUT `/{contactId}/progress-status`，session身份、防御性空白校验、200回包如主计划；非法/缺失status400、未知专家404、未登录401，失败不得写表。
- 旧setFollowed(true)复用新显式FOLLOWING写入；旧false条件DELETE WHERE progress_status='FOLLOWING'，FollowResult(false)。不把旧DELETE改成通用取消，避免删提供。
- 返回本次已提交指令状态，后续列表再读权威状态；并发最后执行的写入获胜，不添加版本号、日志、后台任务。未知状态不自动当NONE。

### T-3：分页查询与summary（I-1/I-4/I-5）
文件：`mail/repository/MailboxConversationRepository.kt`、`mail/service/MailboxConversationService.kt`、`mail/controller/MailboxConversationController.kt`。

- Filter、Service方法、Controller参数末尾加 `providedOnly:Boolean=false`，保持旧具名/位置调用兼容。
- page/explain将行存在性投影改为同源 `COALESCE((SELECT ef.progress_status FROM expert_follow ef WHERE ef.username=:username AND ef.expert_contact_id=u.expert_contact_id),'NONE') AS progress_status`。保留summary对外followed；在SqlRow用progressStatus推导followed getter，或mapper从一次枚举结果赋值，禁止两次独立查询取状态与布尔。
- `ConversationItemResponse`添加progressStatus字段，Service逐行映射；旧followed布尔为FOLLOWING时才true。新增字段放参数尾部，默认从followed派生只为旧构造兼容，真实SQL路径必须显式赋真实状态。
- followed筛选增加progress_status='FOLLOWING'；providedOnly新增同层EXISTS且限定progress_status='PROVIDED'，置于消息OR括号外；参数化绑定。两筛选一起传得到空集。
- orderByClause把providedOnly归入非pending-first分支；count/page/explain共同使用expertPredicates，保留消息聚合、最新来信、标签批量与账号过滤全部现有逻辑。
- 已回复NOT EXISTS expert_follow保持原SQL整行排除，不替换成仅排除FOLLOWING。未标记不新增专用Tab或列表参数。

### T-4：挂起读取同步（I-4/I-5/I-6）
文件：`mail/service/MailboxSuspensionService.kt`。

- 将isFollowed的行存在读取改为一次读取progress_status，无行NONE。stateOf构造progressStatus与派生followed，挂起响应DTO字段尾部增加progressStatus。
- 不引入新的service构造依赖，不改挂起写入/锁/原因/计数/权限。消费者是02的挂起结束归类和提示；整个状态生命周期由同一响应补齐，不能只补GET。

### T-5：实证验证（I-1～I-6）
文件：ControllerTest、RepositoryIT、SuspensionServiceIT及T-1的MigrationTest（精确路径见下表）。

- Controller真实JDBC：六条转换、重复提交、两用户、匿名/空白身份/非法状态/未知专家、旧PUT/DELETE与新接口交替、SQL行与响应一致、created_at保留。请求体伪造username不改变Session归属。
- Repository：两用户名各自状态、21位专家分页20+1、total与页合集一致、两筛选冲突0、q/账号/日期/主题/标签与状态AND、最近来信稳定排序、已回复标记排除与取消水位恢复、EXPLAIN可执行。
- Suspension：所有挂起响应状态正确；PROVIDED仍followed=false；切换标记不改suspended/reason/pending；标记期间消息被处理不改progressStatus。

## 变更文件清单

| # | 文件 | 目的 |
|---:|---|---|
| 1 | src/main/resources/db/migration/V149__add_expert_follow_progress_status.sql | 单列兼容迁移 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertFollowService.kt | 唯一状态写者与枚举 |
| 3 | src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt | 请求/响应/参数及端点 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt | SQL过滤/分页/投影 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt | 参数与summary传递 |
| 6 | src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt | 挂起状态读取 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt | 旧数据与新库迁移 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt | 写入及身份/兼容 |
| 9 | src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt | 筛选分页排序 |
| 10 | src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt | 跨路径读取 |

共10文件，1子系统，1张已有表增加1字段。未列文件不得顺带改造。

## 验收标准

- I-1：迁移测试行数/key/created_at保持；FOLLOWING/PROVIDED只能占同一主键一行，NONE确实无行；旧省略列INSERT仍FOLLOWING。
- I-2：六转换+重复调用数据库实值、created_at及用户隔离；同一key并发upsert无重复，最终等于某个完整提交状态；400/401/404不改记录。
- I-3：先新PUT PROVIDED，再旧DELETE follow仍PROVIDED；再旧PUT follow变FOLLOWING；重复旧PUT不重置时间。
- I-4：21行20+1、total=21、无重复；同传followed/providedOnly得0；其他筛选AND；summary.followed与progressStatus一致；EXPLAIN与page投影相同。
- I-5：前后比对邮件处理状态、发件条数、专家信息、水位、挂起原因均不变；已回复取消后仅按原资格返回。
- I-6：新标记在GET/PUT/PATCH/DELETE suspension均可读到；挂起其他字段语义不变。

命令（JDK11，运行测试前确认独立测试库）：
```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -DskipNodeTests=true
# DB_URL / DB_USERNAME / DB_PASSWORD 必须由测试环境显式设置为隔离库；不可依赖默认业务库。
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -DmysqlIt=true -DskipNodeTests=true -Dtest=MailboxConversationControllerTest,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT
```
门禁未启用、Docker/数据库不可用或报告skipped不能记PASS。已有SqlCompat源码用例与全量构建继续运行，不使用H2模拟证明MySQL。

## 人工验收清单

以下使用隔离验收库与已登录测试会话，API可由浏览器网络工具/接口客户端调用；不得在生产构造或删除夹具。

### A-1: 旧关注保留
- 前置条件：隔离环境升级前，用户A关注2位真实测试专家；记录其列表和创建时间，用户B不关注这两位。
- 操作步骤：1.执行迁移。2.A请求列表followed=true。3.B请求同一列表。4.查询升级后的两条表记录。
- 预期结果：A仍2位、状态FOLLOWING、followed=true；B为0；两条created_at与升级前相同。
- 覆盖：I-1/I-2，P-1。

### A-2: 全状态与旧接口兼容
- 前置条件：A登录，专家X有会话且未标记；B未标记X。
- 操作步骤：1.新PUT依次FOLLOWING、PROVIDED、NONE、PROVIDED、FOLLOWING、NONE，每次GET全部搜索X。2.新PUT PROVIDED后调用旧DELETE follow。3.调用旧PUT follow两次。4.换B查看X。
- 预期结果：每步对应枚举，NONE时followed=false且表无行；旧DELETE保留PROVIDED；旧PUT变FOLLOWING且重复不改created_at；B始终NONE。
- 覆盖：I-1/I-2/I-3/I-4，P-2/P-5。

### A-3: 过滤、分页与错误
- 前置条件：隔离库准备21位属于A的PROVIDED专家，均有激活账号往来；其中仅X姓名包含唯一字符串progress-qa，B另有标记。
- 操作步骤：1.providedOnly=true,size=20翻两页。2.加q=progress-qa。3.再加followed=true。4.分别提交非法状态、未知contactId、未登录请求。
- 预期结果：total21，页20+1且无重复；q结果1；冲突参数0；错误分别400/404/401且数据不变。B数据不混入。
- 覆盖：I-2/I-4，P-2。

### A-4: 挂起与已回复不被标记改写
- 前置条件：X有成功发件和水位之后真实来信且无待处理；Y有1条MANUAL_REVIEW并挂起，原因“等待补充”；二者未标记。
- 操作步骤：1.X标记PROVIDED后查看已回复，再取消。2.Y标记PROVIDED，GET suspension；改原因后GET，再取消挂起；检查邮件处理状态与发件数。
- 预期结果：X标记时不在已回复，取消后恢复；Y挂起响应progressStatus=PROVIDED、followed=false，取消挂起后仍PROVIDED且pending=1；消息仍MANUAL_REVIEW，无新增发件。
- 覆盖：I-4/I-5/I-6，P-3/P-4，全部业务边界回归。

验收开始时导出 `mailbox-progress-01-backend-acceptance.md`；当前不生成。
