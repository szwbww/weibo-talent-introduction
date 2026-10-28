# 05：内置账号、开放登录与最终联调

状态：待审阅。依赖：01–04各自机器验证通过。子系统：认证/启动（1）；实施文件5个。此步为user1实际能力开放点。

## 需求描述

首次启动补建`user1 / qfuser1`，允许其正常登录并进入03只读页面；admin可用04授权。保留已有admin账号/hash/首改密码与原用户数据、现有grant。不开用户管理或user1改密页，不在迁移文件中写明文密码/hash常量，不默认授予任何专家。

## 关键不变量

### Invariant I-1: 仅首次创建固定账号
- Rule: username精确user1；密码qfuser1由现有PasswordEncoder现场BCrypt编码；mustChangePassword=false；createdAt/updatedAt创建时初始化，lastLoginAt初始null。已有user1记录绝不重置密码/标志/时间；并发唯一键冲突后重查存在才忽略，否则抛原异常。
- Applies to: 新ReadOnlyUserBootstrap.run、重复启动、多实例启动。
- Violation consequence: 重启重置密码、密码明文落库、账号被迫进入不支持的改密流程。
- 来源: original；沿AdminUserBootstrap:23–39现有模式。

### Invariant I-2: 登录精确白名单与真实身份元数据
- Rule: AuthController仅允许trim后的admin/user1，其他用户名仍拒绝；密码验证继续走AuthService/BCrypt；sessionID仍轮换，保存DB返回的canonical username；mailReadOnly由服务端policy派生。错误密码不能创建新认证session；qfuser1只用于首次种子。
- Applies to: AuthController.login、me、AuthService现有登录写路径。
- Violation consequence: 任意DB用户被开放登录，或前端靠填写username自称admin。
- 来源: original。

### Invariant I-3: 账号开放不产生授权
- Rule: bootstrap不写grant表；初始无grant只能看到空列表，已有grant则按现状生效。user1无改密/发送/附件权限；admin原首改密码与正常业务不变。
- Applies to: bootstrap与最终联调；01–04全链路。
- Violation consequence: 创建账号即看到全部邮件，或开启不完整功能。
- 来源: original。

### Invariant I-4: 发布依赖可证明
- Rule: 只有01–04通过且auth.enabled=true时才允许发布含本步的版本。机器测试通过不代替人工验收；默认跳过MySQL IT不算通过；不把静态预览当真实服务验证。
- Applies to: 构建、验证记录、最终发布验收。
- Violation consequence: user1先被开放而权限尚未安全闭环。
- 来源: K-master-plan-shared-file-sequential-gates；K-js-test-invocation-surface。

## 现状审计

共同审计认证章节及证据01/08/12/13/17。AdminUser是已有账户实体，V25 username唯一；AdminUserBootstrap只建admin；AuthService.login已支持数据库查用户名但Controller:31挡住非admin；changePassword仍只允许admin且最短8字符。

- **admin_user schema**：不加字段。新写者ReadOnlyUserBootstrap仅insert；原AdminUserBootstrap/AuthService.login/changePassword全部保留。所有读路径见01：登录验证、me、拦截器、bootstrap重查。user1.login会沿用最后登录时间更新，这不是邮件写权限。
- **session**：原login写username并轮换ID，logout/无效用户时失效；本步无新session字段。03消费01的mailReadOnly。
- **grant**：无新增写者。本步只使01/02已具备的user1权限可经真实登录访问。
- **测试清理**：AuthFlowIntegrationTest:114每例deleteAll；:149的“1个用户”仅对应手动执行admin bootstrap的场景，不能机械改成2。新双账号场景必须显式运行两个bootstrap，断言exact set={admin,user1}。
- **实际环境未知**：13源码无生产user1种子，不证明数据库无user1。执行前只读核对是否已存在以及canonical用户名/mustChangePassword；有冲突不能覆盖，应单独记录处理决定。

## 实现方案

1. **新增启动器（I-1/I-3）**：`B/auth/service/ReadOnlyUserBootstrap.kt`，与AdminUserBootstrap同条件`@ConditionalOnProperty("talent-introduction.auth.enabled",havingValue="true",matchIfMissing=true)`，使用现有AdminUserRepository/PasswordEncoder；不改原admin启动器。不存在时创建固定user1；已有则skip。唯一键并发处理复用现有模式；不要catch所有异常都当已存在成功。禁止日志打印密码/hash。若既有canonical名不是精确user1或首改密码标志冲突，发布前核对并停止账号验收，不自动修复数据。
2. **开放登录（I-2/I-3）**：AuthController.login白名单从admin改为admin/user1；不放开所有用户。01响应字段及原异常文案/session轮换保持。AuthService.changePassword不改，user1 POST被01拒绝；不因7字符初始密码降低admin改密规则。
3. **测试（I-1–I-4）**：新增ReadOnlyUserBootstrapTest覆盖初建/重复/并发/异常；AuthFlowIntegrationTest新增明确运行两个bootstrap的真实登录、匿名/错误密码、me、logout、重启不重置、user1权限负例；AuthControllerMvcTest 原“user1登录仍拒绝”断言在本步失效，须改为真实接受 user1 登录并保留“其它用户名仍拒绝”。把01/02/03/04测试一同纳入最终门禁，不靠本步重写其测试。既有 `AuthFlowIntegrationTest` 在基线因测试类路径上的顶层 `@Configuration` 泄漏而无法加载上下文（`AttachmentTransferWorker` 覆盖冲突，随后 `mailboxConversationNamedJdbc`/`expertMailReadAccessNamedJdbc` 二义）；为执行本步要求的真实登录门禁，允许仅在该授权测试文件内加入最小测试上下文接线：`spring.main.allow-bean-definition-overriding=true`，`@Primary` 的 `DataSource`/`JdbcTemplate`/`NamedParameterJdbcTemplate`/`transactionManager` 测试 bean（后三项分别解决 DataSource 二义、Spring Data JDBC 固定名事务管理器缺失），以及对与本门禁无关、但会因 JDK 11 tzdb 缺少 `America/Coyhaique` 而阻塞上下文启动的 `ExpertContactLocationCatalog` 使用 `@MockBean`。该接线不改变产品代码与产品缺陷；原命令的基线与修复后结果均须如实记录。
4. **最终联调（I-3/I-4）**：隔离测试环境执行真实登录→admin UI授权→user1查看→admin撤销→user1失权；浏览器Network校验无越权业务请求、无附件内容。核对生产构建包含03脚本、04版本号；不要修改配置隐藏失败。完成机器门禁后导出master及子计划人工清单，由人确认；本轮不发布。

## 变更文件清单

|#|路径|动作|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/auth/service/ReadOnlyUserBootstrap.kt|新增固定账号启动器|
|2|src/main/kotlin/com/weibo/talentintroduction/auth/controller/AuthController.kt|最终开放user1登录|
|3|src/test/kotlin/com/weibo/talentintroduction/auth/service/ReadOnlyUserBootstrapTest.kt|种子幂等/并发验证|
|4|src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt|双账号真实登录及权限门禁|
|5|src/test/kotlin/com/weibo/talentintroduction/auth/controller/AuthControllerMvcTest.kt|删除失效的 user1 登录拒绝断言，改为接受登录与非白名单拒绝|

## 验收标准

- I-1：PasswordEncoder.matches("qfuser1",storedHash)=true且hash!=明文；mustChange=false；重复启动不改变已修改hash/时间；并发重复键重查成功不报错，其他DB错误仍失败；auth=false不创建账号。日志不包含明文密码。
- I-2：admin及user1正确密码成功，错误密码/其它用户名失败；user1 mailReadOnly=true，admin=false；sessionID轮换；logout后受保护API401、me authenticated=false。原admin改密测试通过。
- I-3：新建账号后grant行数不变；空grant无邮件；授权A只读A、B详情403；全部附件/发送/草稿/其它业务接口403，已有admin请求不受影响；重启保留grant和现有密码。
- I-4：记录01–04各项结果、真实MySQL运行结果及浏览器真实数据联调；与master人工A-1–A-4逐项对应。必须核对API返回和数据库事实，不能只截图按钮隐藏。
- Java11下先定向 `mvn -Dtest=ReadOnlyUserBootstrapTest,AdminUserBootstrapTest,AuthControllerMvcTest,AuthInterceptorTest test`；最终一次 `mvn test`（包含pom的全量JS）与`mvn -DskipTests package`确认构建。认证真实登录另执行`mvn -Pmigration-it -Dtest=AuthFlowIntegrationTest test`（该类使用MySQL 8.0.36 Testcontainers，必须有可用Docker）；真实SQL另执行`mvn -Pmysql-it -Dtest=ExpertMailReadAccessIT,MailboxConversationRepositoryIT,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailReplyDraftSendIntegrationTest test`，显式配置隔离测试库。数据库不可用则记录未通过，不改为skip后宣布完成。
- 如当前HEAD/工作区已有无关失败，记录失败及基线复现；不得在本计划外顺手修复，也不得声称全部验证通过。

## 人工验收清单

### A-1: 内置账号与幂等
- 前置条件: 隔离测试库已迁移，admin已完成首改密码，尚无user1；01–04通过。
- 操作步骤: 1. 启动完整应用。2. 用user1/qfuser1登录。3. 退出后重启。4. 再登录。5. admin登录。
- 预期结果: user1无需改密，进入仅收发件箱且初始空授权；第二次同样可登录；admin密码和权限保持原状；无自动新增grant。
- 覆盖: I-1/I-2/I-3。

### A-2: 真正授权/撤销闭环
- 前置条件: 两浏览器分别admin/user1；A/B有往来信，A有附件；user1初始无grant。
- 操作步骤: 1. admin UI给A授权。2. user1刷新查看A正文。3. user1直接请求B及A附件。4. admin撤销。5. user1翻更早页并刷新。
- 预期结果: A正文可见，B/附件403；撤销后新请求403，旧正文清空；列表不再有A。截图和Network来自真实运行服务，不是模拟页面。
- 覆盖: I-2/I-3/I-4；账号→session→grant→查询→前端。

### A-3: 管理员和后台回归
- 前置条件: admin已有受控发送任务及草稿；测试收件箱可观察。
- 操作步骤: 1. admin改密并重新登录。2. 下载附件、保存草稿和发信。3. 触发原收信/自动任务。4. 用user1检查授权专家新邮件。
- 预期结果: admin改密规则不变；原功能成功；后台任务不因user1策略失败；新邮件按授权可读；user1发送/改密请求403。
- 覆盖: I-2/I-3/I-4；所有必须保留项。
