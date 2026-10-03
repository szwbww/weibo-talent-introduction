# 01 — 挂起状态存储、查询与接口

> 2026-10-03 v6：挂起存储与显式结束接口保持本方案；UI改为消息下方行内提示，见02。人工处理Session身份另由01b限定两文件调整，正式代码尚未实施。

状态：待评审。依赖：无。实施范围：9 文件、邮箱后端 1 子系统；没有现有共享表新增业务字段。

## 需求描述

提供按登录用户、专家会话持久化的挂起与取消接口，原因选填；列表按真实数据库分页显示已挂起，0 条待处理也保留。处理完成不会自动取消，供 02 读取后提示。

必须保持：①来信处理/发信路径和专家业务状态；②关注、已回复水位语义；③真实账号口径和全历史聚合后分页；④会话正文、附件、排期与 ES 查询。

范围外：后台自动结束、全局多人工作流、原因历史与编辑、定时任务、改写已应用迁移、改现有邮件处理服务。

## 关键不变量

### Invariant I-1: 行存在就是挂起
- Rule: 新表 `expert_mailbox_suspension` 主键 `(username, expert_contact_id)`。有行=suspended=true；无行=false。reason=null 是未填写原因，绝不表示未挂起。没有第二个 suspended 布尔列、完成状态或墓碑。
- Applies to: 新服务 PUT/GET/DELETE；仓库列表与批量摘要读取。
- Violation consequence: 原因空时挂起丢失、两个状态源互相矛盾。
- 来源: 原创；参考 V121 的现有关注持久化模式。

### Invariant I-2: 无自动结束写路径
- Rule: 只允许用户 PUT 创建、DELETE 结束；查询、markResolved、自动收信、手动发送、cancelResolved 均不删除/更新挂起行。处理完成后挂起行仍存在；新来信也不改变其存在性。
- Applies to: 所有新 SQL，现有处理路径回归测试。
- Violation consequence: 与用户“提示是否结束”要求直接冲突。
- 来源: 用户最新澄清；K-inbound-processing-write-paths。

### Invariant I-3: 账号与计数口径
- Rule: pending = 真实 `inbound_mail_processing.process_status='MANUAL_REVIEW'`，关联同 contact。生命周期提示计数跨该专家所有真实账号，排除 `SIMULATOR_NOOP` 和不存在账号，包含 enabled=false 的真实账号；不受日期、主题、分页/时间线窗口影响。列表原 pendingCount 仍服从原账号筛选。额外返回 `suspensionPendingCount` 明确是跨账号计数。
- Applies to: 创建校验、GET/DELETE 回包、列表批量状态摘要。
- Violation consequence: 选中账号已处理而另一账号尚未处理时误提示结束。
- 来源: K-sender-account-enabled-scope；K-mailbox-inbound-source-authority。

### Invariant I-4: 查询归类及分页
- Rule: 待处理 = 原 pending 条件 AND 无当前用户挂起行；已挂起 = 有行（不加 pending>0）；已回复 = 原全部条件 AND 无挂起行；全部和关注保留挂起专家。SQL 先筛选/分组再 LIMIT，count/page/explain 同口径。
- Applies to: repository countConversations/pageConversations/explainConversationsPage，service DTO。
- Violation consequence: 空页、总数错误、已挂起零待处理会话消失。
- 来源: K-group-before-pagination；用户确认保留现有已回复规则。

### Invariant I-5: 用户身份、幂等与原因
- Rule: username 只取 Session，不能由 body/query 指定。不存在专家 404；匿名 401；首次挂起时全球 pending=0 则 409；已有挂起重复 PUT 返回原原因，不覆盖。reason 先 trim，空串转 null；最多 500 个 UTF-16 code units（与 textarea maxlength 一致），超长 400；作为纯文本存储/返回。DELETE 可重复，第二次也返回 suspended=false。
- Applies to: controller/service/SQL。
- Violation consequence: 串用户、网络重试覆盖原因、XSS 或错误成功。
- 来源: ExpertFollowService 身份与幂等模式；原创原因约束。

### Invariant I-6: 纯附加能力
- Rule: 不写 expert_contact、expert_follow、expert_replied_dismissal、mail_record、inbound_mail_processing、ES。GET 不访问 IMAP、SMTP。新 DTO 属性有兼容默认值；现有 service 构造参数保持不变。
- Applies to: 全部 01 文件。
- Violation consequence: 旧页面/旧测试构造失效或发信状态改变。
- 来源: K-mailbox-inbound-source-authority；源码审计。

## 现状审计

路径前缀：`src/main/kotlin/com/weibo/talentintroduction/`。行号为工作区审计时锚点，以方法名和证据哈希复核。

### 新挂起存储

- 目前不存在：对 mail 后端和 mailbox-chat.js 执行 `rg 'suspension|suspended'` 无匹配；预览的 edits Map 不是数据库。
- 拟定 schema 在 T1；唯一业务载荷是 nullable reason，主键沿 V121 用户+专家方式。无需实体、CrudRepository、ES 同步、事件总线或缓存。
- 全部写路径：迁移创建空表；新服务 `suspend` INSERT IGNORE；新服务 `resume` DELETE。没有其他运行期写者。
- 全部读路径：新服务 GET/PUT/DELETE 状态回读；仓库三种列表 SQL 的 EXISTS；仓库批量状态摘要。02 消费 API。
- 交互点 X1：PUT/DELETE→列表/count/摘要；X2：邮件处理→只读 pendingCount→02 提示；X3：关注/水位→取消后既有归类。

### 只读依赖与完整写路径核验

- `inbound_mail_processing` schema：V5（status、contact FK、账号 UID），V10（正文/resolved 字段）、V14（reasonType）、V120（uid_validity 逻辑唯一键）、V134（物理 owner 唯一键）。新功能只读 contact/account/status，不动这些约束。
- 运行期写者：AutoMailReplyService.confirmManualReviewWithBody:1308、confirmProcessed:1364 新建；UnmatchedInboundMailService.bindToContact:193、markResolved:238 修改；PendingMailOperationService.markResolved:1524 修改，cancelResolved:1595 调用 InboundMailProcessingRepository.reopenManualResolved:50 条件 UPDATE。V14/V15 历史 UPDATE 不修改。原始完整引用清单见 evidence/write-read-paths.txt。（来源: K-inbound-processing-write-paths，已重新 grep）
- `ManualReplySendAttemptService.finalizeSuccess:340–440` 写成功 mail_record/attempt/排期，**不把 inbound 改已处理**。不能把“发信成功”当作“全部处理”。
- 既有读取：MailboxConversationRepository 的 rangeUnionSql:440–511、latestInboundByContacts:295、timeline:388、expertPredicates:583；InboundMailProcessingRepository 的 find/count/待匹配队列；来信处理服务读实体。全部调用引用记录在原始 grep，不基于前端可见的 50 条时间线推测总数。
- `mail_sender_account`：V1 account_code 唯一；MailboxConversationService.activeAccountCodes:529 调用 findAllByAccountCodeNot，没有 enabled=true 限制。新 SQL JOIN 同一表，账号过滤绑定 `MailSenderAccountService.SIMULATOR_ACCOUNT_CODE`，不复制错误的“已激活=enabled”推断。（来源: K-sender-account-enabled-scope）
- `expert_follow`：V121 主键用户+专家；ExpertFollowService.setFollowed 是 INSERT IGNORE/DELETE 写者；仓库投影 followed，并在 repliedOnly:596–603 排除当前用户关注。新功能只读，不取消关注。
- `expert_replied_dismissal`：V143 主键用户+专家，last_inbound_id 水位；ExpertRepliedDismissalService.dismiss 用 MAX(id) + GREATEST UPSERT；仓库:614–623 按新来信 id>水位读。保留原表达式和参数，不将取消挂起视作新来信。
- `mail_record`：rangeUnionSql 仅从 OUTBOUND 汇总发送；repliedOnly 另要求真实账号 SENT。新功能不新增发送、不补发记录。
- `expert_contact`：新表只通过 FK 与 SELECT 存在性依赖 id，不加入大实体的新字段，不修改通用 save，也不迁移 operatorStatus。（来源: K-operator-status-write-seam-guard 的边界原则；本次不改该域）

### 列表/API 与测试

- MailboxConversationRepository:31 ConversationFilter；:123 count；:144 page；:186 explain；:231 排序；:583 predicates；:632 HAVING；:645 params；:659 mapper。给共享 predicates 增加 EXISTS 后三查询自然一致，不能仅 page 追加。
- MailboxConversationService:92 listConversations，:149 分页，:154–159 批量映射，:173 pendingCount。在现有 repository 上增加批量方法，无需新增构造依赖。
- MailboxConversationController:182 构造与 :185 list；:238 follow 与 :248 replied-dismissal 展示 Session 防御校验模式。新独立 service 注入此 controller，检查同文件测试的两个 WebMvc context。
- `constructor-sites.txt`：OutboundAttachmentFlowTest:238 手动创建 MailboxConversationService；保持其构造不变，不扩大该测试范围。
- RepositoryIT:744、ControllerTest:1253/:1876 cleanup 当前先删 dismissal/follow；新增表须在删 contact 前清理。
- FlywayMigrationIntegrationTest 多处 `flyway().migrate().targetSchemaVersion` 写死145，实际仓库已有 V146。证据有25处 latest-target 145；本片统一改为147，仅无 target 的最新断言，历史 target=xx 断言不动。为新表增加 schema/升级空表/唯一性/FK 测试。
- 仓库最高迁移 V146；本计划预留 V147。生产库当前 Flyway max 本次未读取，**不得声称已验证可发布**。执行建迁移前重查仓库与已应用版本；若147已被占用，只修订本计划中准确文件名与最新版本期望，不覆盖已有迁移。（来源: K-flyway-version-follows-deploy-order）

## 实现方案

### T1 — 新增最小表与服务（I-1/I-2/I-3/I-5/I-6）

文件：清单 1/2。迁移完整 SQL：

```sql
CREATE TABLE expert_mailbox_suspension (
    username VARCHAR(64) NOT NULL,
    expert_contact_id BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    PRIMARY KEY (username, expert_contact_id),
    CONSTRAINT fk_mailbox_suspension_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`MailboxSuspensionService` 使用项目已有 NamedParameterJdbcTemplate 模式，状态 DTO 与请求 DTO 放该文件或现 controller 文件，不拆额外模型层。PUT/DELETE `@Transactional`；SELECT contact `FOR UPDATE` 串行化同专家挂起/取消的用户操作；不存在404。PUT先校验原因长度/trim，再检查已有行，已有行原样返回；无行再校验 pending、trim原因并 INSERT IGNORE。DELETE 只按 Session+contact 删除，并回读实时状态。SQL 参数绑定，禁止拼接用户输入。

计数查询按 `inbound_mail_processing imp JOIN mail_sender_account msa ON msa.account_code=imp.sender_account_code`，WHERE contact/status 和 `msa.account_code <> :simulatorCode`；不带 enabled、当前账号/date/q。不要锁所有邮件或修改收信事务；并发新来信只影响回读计数，不改变挂起行。

### T2 — 状态 API（I-1/I-2/I-3/I-5/I-6）

文件：清单 2/5。

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| GET | `/api/mail/mailbox/conversations/{contactId}/suspension` | 无 | 状态对象 |
| PUT | 同上 | `{ "reason": null }` 或字符串；缺省按null | 创建或幂等回读状态 |
| DELETE | 同上 | 无body | 取消后的状态对象 |

状态对象固定：`{ contactId: Long, suspended: Boolean, suspendReason: String?, suspensionPendingCount: Long, followed: Boolean }`。不在回包中伪造 replied=true，不回写关注。HTTP非法输入400、不存在404、未登录401、无未处理不能新挂起409。异常沿现有统一异常机制/ResponseStatusException，不能把未登录返回200。

显式取消允许仍有未处理；结束行内确认期间又收到信，DELETE仍正常结束，回包计数>0时前端导航待处理。这是用户明确取消，不是后台自动决策。

### T3 — 列表与批量状态（I-1/I-3/I-4/I-6）

文件：清单 3/4/5。

1. controller追加 `suspendedOnly=false`；service 的新增参数放末尾并默认false，保留原具名/位置调用兼容；ConversationFilter 同样默认false。
2. 共用 expertPredicates 增加当前用户 suspension EXISTS。suspendedOnly=true 用 EXISTS；pendingOnly/repliedOnly 用 NOT EXISTS；同时传相互冲突条件按 AND 得空集，不隐藏改写参数。
3. `orderByClause` 将 suspendedOnly 纳入已选队列分支，按现有最新来信时间排序（避免挂起页再按pending优先）；其他排序原样。count、page、explain使用相同筛选。
4. 新增 repository `suspensionStatesByContacts(username, contactIds)`，一次批量读取当前页状态与跨账号pending聚合。使用分组派生表或相关聚合，MySQL5.7兼容；禁止窗口函数/逐专家 N+1。WHERE id IN 当前页真实 id，空集合直接返回。
5. DTO 末尾新增 `suspended=false`、`suspendReason=null`、`suspensionPendingCount=0`；service 逐项填真实返回。新表无行时也必须拿到真实跨账号pending（不能把未挂起当作0）。GET单体与批量共用仓库状态读取，避免两套pending定义。service可依赖既有repository加方法；不改其现有构造。
6. 原 pendingCount/latestMessage/latestInbound/accountCodes/materials/tags/时间线 DTO 不改变。

### T4 — 验证（I-1–I-6）

文件：清单6–9。真实 MySQL验证表存在性/事务/隔离用户/SQL筛选/分页；不只 mock HTTP成功。新测试使用独立库、唯一数据并 finally cleanup。两条处理入口回归可用现有HTTP标记接口与repo测试SQL完成状态写入，断言挂起表保持；不要为测试新增生产自动结束hook。

## 变更文件清单

前缀 `src/main/kotlin/com/weibo/talentintroduction/` 用于下列第2–5项；测试项给相对完整路径。此表列名为路径缩写，执行按此唯一展开。

| # | 文件 | 变化 |
|---|---|---|
|1|src/main/resources/db/migration/V147__create_expert_mailbox_suspension.sql|新增空表|
|2|mail/service/MailboxSuspensionService.kt|新增显式挂起/取消、状态DTO|
|3|mail/repository/MailboxConversationRepository.kt|共享筛选和批量状态读取|
|4|mail/service/MailboxConversationService.kt|透传筛选与DTO填充|
|5|mail/controller/MailboxConversationController.kt|三个端点、DTO兼容字段|
|6|src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt|分页/账号/状态测试、清理|
|7|src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt|两个context注入、HTTP身份、回归|
|8|src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt|新真实MySQL服务测试|
|9|src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt|新表验证和latest版本同步|

## 验收标准

- I-1：nullable/空原因仍suspended=true；删除后false；重复PUT单行。
- I-2：两条消息2→1→0仍单行，GET/列表/发信成功没有DELETE；撤销已处理0→1仍保持原挂起。
- I-3：A账号0、B账号1时 global=1；disabled真实账号计入；模拟器/无账号不计；超过50条时间线仍使用DB全量count。
- I-4：至少45位专家分3页，挂起部分并处理其中至0；count=全部匹配数，无前端过滤导致空洞；followed挂起在关注可见；replied保持原3条件；单用户隔离、count/page/explain同谓词。
- I-5：401/404/409/400覆盖；原因500单位可存、501拒绝；空白转null；并发重复PUT/DELETE幂等，SQL不可注入。
- I-6：相同fixture执行前后 expert_contact/follow/dismissal/邮件状态不变；旧summary/timeline测试仍通过。

执行使用 `.multi-ai-kit.yaml` 指定 JDK11.0.32；测试库必须是**独立本地库**，不可指向线上/日常数据库。先设置实际本地 `DB_URL/DB_USERNAME/DB_PASSWORD`，不在计划保存凭据。示例：

```sh
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -DskipTests test-compile
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=MailboxConversationRepositoryIT,MailboxConversationRepositorySqlCompatTest,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailboxSuspensionServiceIT test
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test
```

Migration IT需要Docker；缺失时记录未运行，不以skip算通过。现有其它 MySQL fixture 清理差异若构成新增表回归，先报告确切文件并修订范围，不能悄悄扩表到10以上。

## 人工验收清单

### A-1: 构造可复验会话
- 前置条件: 独立测试环境；用该环境的测试seed建立用户op1/op2、真实账号A/B、已关联专家E、两条MANUAL_REVIEW来信；另建已关注专家F和未关注且有SENT/新来信的G。邮箱均使用example.test，不配置真实发送。
- 操作步骤: 1. 以op1对E PUT原因“等待材料”。2. GET状态。3. 刷新并重新登录GET。4. 以op2 GET E。
- 预期结果: op1始终suspended=true、reason“等待材料”、count2；op2=false；不发送邮件。
- 覆盖: I-1/I-5/I-6，X1。

### A-2: 处理至零仍挂起
- 前置条件: A-1的E已挂起。
- 操作步骤: 1. 用现有标记已处理接口逐条处理2条消息。2. 每次GET。3. 调已挂起列表。4. DELETE后再DELETE。
- 预期结果: count2→1→0但前3步suspended始终true，零待处理仍在挂起列表；显式DELETE后false，第二次仍false。
- 覆盖: I-1/I-2/I-3，X1/X2。

### A-3: 已关注与移出已回复回归
- 前置条件: F已关注；G未关注、有成功发件与来信，先在已回复可见。
- 操作步骤: 1. 分别挂起F/G并处理至零。2. 取消两者。3. GET关注/已回复。4. 将G移出已回复，再GET。
- 预期结果: F只因原规则不进已回复且followed=true；G恢复原已回复资格，移出后不可见；挂起操作不更改任何关注/水位。
- 覆盖: I-4/I-6，X3。

### A-4: 跨账号与分页
- 前置条件: E在A账号0条待处理、B账号1条，已挂起；另45位专家按测试seed建好。
- 操作步骤: 1. 列表选择A账号看E。2. GET E挂起状态。3. 分页读取挂起/待处理页。4. 将B禁用发送，再GET。
- 预期结果: E列表pendingCount=0，suspensionPendingCount=1；禁用后仍1；不提示所有消息处理完成；每页计数和总数符合真实专家数。
- 覆盖: I-3/I-4，X1/X2；保持账号/聚合规则。

### A-5: 错误、旧业务与无副作用
- 前置条件: 独立环境存在无来信专家H；保留一条已处理来信和一条有材料/标签/排期的会话。
- 操作步骤: 1. 匿名PUT。2. 登录后对未知id、H、超长原因分别PUT。3. 在旧页面查看材料/标签/排期/最新回复时间。4. 对已处理来信执行现有撤销，再GET其状态。
- 预期结果: 分别401/404/409/400；旧详情与计数仍可读取；撤销后MANUAL_REVIEW，挂起与否不被自动切换，专家业务状态未由挂起接口改动。
- 覆盖: I-2/I-5/I-6，必须保持①④，X2。
