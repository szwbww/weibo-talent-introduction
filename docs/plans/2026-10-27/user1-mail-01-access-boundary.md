# 01：固定用户策略、授权关系与接口边界

状态：待审阅。依赖：无。后续：02。业务子系统：认证边界、授权关系（2）。精确实施文件数：10。

## 需求描述

admin 能通过接口授予/撤销某专家给 user1 的邮件查看权；后端具备固定 user1 默认拒绝策略。此步仍保持 **仅 admin 可登录**，不创建 user1，不改邮件查询。admin 登录改密、原业务 API 和后台任务不变；不做通用角色/权限系统。

## 关键不变量

### Invariant I-1: 固定身份与默认拒绝
- Rule: `MailReadOnlyPolicy.isReader(username)` 只对精确 `user1` 返回 true；登录白名单本步仍 admin。已认证 user1 的实际 MVC handler 只允许 GET ConversationController.list/messages、POST AuthController.logout；login/me沿用原排除规则，OPTIONS沿用原行为。其余已映射 `/api/**` 请求403。未经登录仍401；不存在的路由可404、方法不支持可405，不能要求所有未知URL均403。
- Applies to: 新 policy、AuthInterceptor、权限 MVC 测试。
- Violation consequence: 新增接口默认开放，附件或发送旁路。
- 来源: original。

### Invariant I-2: grant 的唯一表示与全部新字段
- Rule: 主键(username,expert_contact_id)；username 固定user1，expert_contact_id正数且FK存在；行存在=允许看，不存在=禁止，不增加 enabled/role/read/send冗余字段。granted_by只取admin session；created_at首次授予写入，重复PUT不刷新，DELETE后再授予产生新时间。
- Applies to: migration、GrantService.PUT/DELETE/GET。
- Violation consequence: 重复行、伪造操作者、默认允许或撤销不生效。
- 来源: original；K-no-cache-framework-in-repo（不引入权限缓存）。

### Invariant I-3: 管理接口只给 admin
- Rule: GET/PUT/DELETE授权接口均在读contact/grant前检查session精确admin；匿名401，其他身份403；target不从body接收用户、grantedBy或权限枚举。参数错误400，admin查询不存在专家404。只写新表，不写关注、挂起、邮件状态或ES。
- Applies to: 新Controller/Service；所有grant写路径。
- Violation consequence: user1自授权、非admin查权限、授权改变业务状态。
- 来源: original。

### Invariant I-4: 元数据与错误稳定
- Rule: login/me响应增加派生`mailReadOnly:Boolean=false`；仅精确user1为true，匿名false，不写session派生role/DB字段。权限异常明确映射403，通用Exception handler不吞成500。保留旧响应字段与session轮换。
- Applies to: AuthController DTO/组装、GlobalExceptionHandler、policy中权限异常。
- Violation consequence: 前端误判身份，权限拒绝变500或改坏admin登录。
- 来源: original。

## 现状审计

[共同审计](user1-mail-audit.md)的认证、session、新表闭环章节为本节组成部分；证据01/03/06/08/17。原AuthInterceptor没有角色校验；AuthWebConfig排除login/me；AuthController:31只允许admin；V25有唯一username且无role。

- **admin_user**：本步只沿用现有login最后登录时间写入；不新增写者。写者AdminUserBootstrap/AuthService.login/changePassword，读者AuthService.findUser/login/changePassword、bootstrap和me/interceptor；完整检索01。
- **session**：登录轮换ID/保存AUTH_USERNAME、logout/me/interceptor失效路径不变；只读策略读取已认证username，不写新的持久状态。
- **新增expert_mail_read_access**：06证明源码中尚无表/类型。唯一业务写者新Service；唯一新读者其GET，后续02的EXISTS/准入；不用缓存/ES副本。
- **相互影响**：admin grant写→02列表/详情读；AuthController.me元数据→03宿主；新403异常→前端清空状态。手工构造AuthInterceptor的既有测试见08，因此保留其构造参数。

## 实现方案

1. **策略及错误（I-1/I-4）**：新增 `B/auth/config/MailReadOnlyPolicy.kt`，集中固定用户名、isReader及异常`MailReadAccessDeniedException(code,message)`。在原AuthInterceptor完成账号存在校验后、放行业务前检查reader；使用 `HandlerMethod.method.declaringClass + method.name + HTTP method` 精确白名单，类名/方法与真实映射测试绑定，不用字符串前缀 `/api/mail` 放行。handler非HandlerMethod则reader拒绝（OPTIONS除外）。保留原首改密码规则；reader策略不得因此开放change-password。
2. **显式403（I-4）**：`GlobalExceptionHandler.kt` 增加该异常handler，返回已有ApiErrorResponse。业务默认拒绝码`READ_ONLY_FORBIDDEN`；详情失权码预留`MAIL_READ_FORBIDDEN`；管理权限码`ADMIN_REQUIRED`。状态均403，不返回SQL/文件路径。
3. **迁移（I-2）**：新增 `V166__create_expert_mail_read_access.sql`，执行前重新确认编号未占用，不修改旧迁移。schema冻结如下（来源K-flyway-gate-must-pass-on-fresh-db：验收包含全新空库迁移完整链，不用真实生产ID作为前提）：

```sql
CREATE TABLE expert_mail_read_access (
    username VARCHAR(64) NOT NULL,
    expert_contact_id BIGINT NOT NULL,
    granted_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (username, expert_contact_id),
    CONSTRAINT fk_expert_mail_read_access_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

新表整条关系为一项新状态，不向已有共享表增加字段。FK级联是本次设计。无user FK、无初始grant、无全员授权回填。

4. **grant服务与接口（I-2/I-3）**：新增 `B/mail/service/ExpertMailReadAccessService.kt`（参数化NamedParameterJdbcTemplate）与 `B/mail/controller/ExpertMailReadAccessController.kt`（DTO同文件）。路径固定：

|HTTP|路径|成功|语义|
|---|---|---|---|
|GET|`/api/mail/mailbox/conversations/{contactId}/read-access/user1`|200 `{username:"user1",contactId,canRead}`|无grant返回false；不存在专家404|
|PUT|同上|200 同DTO，canRead=true|幂等插入，仅首次设置granted_by/created_at|
|DELETE|同上|200 同DTO，canRead=false|无行也成功；不存在专家404|

Controller取AUTH_USERNAME、明确验证admin；Service同样要求传入的actor为admin，禁止用可伪造请求body代替。校验contact存在后写；FK处理并发删除为404而非泄露SQL。采用事务及数据库唯一约束处理并发重复授予；禁止“先查后插不处理冲突”。此处不新增audit_log副作用；granted_by/created_at只保留当前grant信息，撤销不承诺历史审计。

5. **身份响应（I-4）**：AuthController.login/me组装新增mailReadOnly，DTO尾部默认false兼容旧构造；本步不要改`username != "admin"`条件。
6. **测试（I-1–I-4）**：扩展AuthInterceptorTest、AuthControllerMvcTest；新增ExpertMailReadAccessIT，在隔离MySQL验证真实迁移/FK/幂等/并发/撤销及MVC 401/403/404。既有构造器均不改，权限异常测试带GlobalExceptionHandler。

## 变更文件清单

`B/T`前缀按共同审计展开；表中每行均为一个完整路径。

|#|路径|动作|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt|新增策略/权限异常|
|2|src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptor.kt|reader默认拒绝|
|3|src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt|明确403|
|4|src/main/kotlin/com/weibo/talentintroduction/auth/controller/AuthController.kt|响应元数据；不开放登录|
|5|src/main/resources/db/migration/V166__create_expert_mail_read_access.sql|新增表|
|6|src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt|唯一grant写者|
|7|src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertMailReadAccessController.kt|管理API|
|8|src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt|允许/拒绝边界|
|9|src/test/kotlin/com/weibo/talentintroduction/auth/controller/AuthControllerMvcTest.kt|元数据及admin回归|
|10|src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt|MySQL/MVC grant闭环|

## 验收标准

- I-1：取真实HandlerMethod测试GET list/messages允许，POST同方法不允许；GET旧mailbox、草稿、各类附件、sender-accounts、导出/监控及所有非白名单映射拒绝；代表性发信/关注/备注/标签/挂起/重试请求403且业务mock零交互。不可只测URL正则。匿名401、admin既有规则、普通非admin测试身份既有行为保留。
- I-2：无grant=false；PUT两次1行且created_at不变；并发PUT后1行；DELETE两次0行；删除contact后grant级联清理；服务不能写其它表，grant SQL参数化。
- I-3：匿名/reader/其他session对GET/PUT/DELETE分别401/403/403；body伪造admin不影响权限；非法contactId400、admin不存在contact404。
- I-4：admin登录/me mailReadOnly=false、reader测试session me=true、匿名false；reader登录仍拒绝。异常响应403及固定code，非500；admin改密与sessionID轮换回归。
- 执行：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AuthInterceptorTest,AuthControllerMvcTest test`；隔离MySQL下 `mvn -Pmysql-it -Dtest=ExpertMailReadAccessIT test`（同Java11环境，明确连接独立测试库）。记录运行/跳过数量；不允许skip当PASS。

## 人工验收清单

### A-1: 管理授权写读
- 前置条件: 隔离环境迁移完成，admin已改密；准备有邮件的contactId=A和不存在的Z。
- 操作步骤: 1. admin GET A授权。2. 连续PUT两次。3. GET。4. 连续DELETE两次。5. GET。6. GET Z。
- 预期结果: 依次false、true、true、false；只有一条grant，重复授予时间不变；Z为404。邮件与关注状态不变化。
- 覆盖: I-2/I-3；grant写→GET读。

### A-2: 默认拒绝与admin回归
- 前置条件: 测试fixture建立user1并创建测试session（本步不开放其真实登录）；admin已有session。
- 操作步骤: 1. user1请求附件、发送和grant PUT。2. admin重复对应合法请求至受控测试资源。3. 尝试user1登录，查看admin me。
- 预期结果: user1受保护请求403；admin未被reader策略阻断；user1登录仍提示用户名或密码错误；admin me的mailReadOnly=false。原admin改密仍要求至少8字符。
- 覆盖: I-1/I-3/I-4；admin及后台业务不变边界。
