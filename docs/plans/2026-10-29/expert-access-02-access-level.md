# 专家分级权限 · 授权等级与能力投影

状态：DRAFT，待批准实施。基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`。仅计划，不代表实现/测试已通过。

证据：[审计总表](expert-access-evidence/audit.md)、[逐字源码E01–E37](expert-access-evidence/source-excerpts.md)、[schema迁移链](expert-access-evidence/schema-chain.md)、[检索收据](expert-access-evidence/store-usage.txt)、[SHA256](expert-access-evidence/sha256.json)。

前置：01；既有draft-review-01-workflow先落地V169；没有它则先修订迁移顺序，不跳号后补低版本。本文件是可执行边界，先读[总计划](expert-access-master.md)的协议/最终UI与依赖修正。

## 需求描述
管理员可为指定专家设READ/FULL，旧授权保持READ；用户接口得到当前专家等级。

必须保持：无授权仍不可见；既有旧PUT/DELETE兼容；账号池、正文、标签投影与分页不变。

不做：不新增角色表/用户页/权限布尔矩阵，不创建第二套账号；本步不启用用户写端点。

## 关键不变量
### Invariant I-1: 三态单一事实
- Rule: 无授权行=NONE；有行access_level仅READ/FULL，NOT NULL默认READ。旧授权迁移为READ。canRead由等级派生，不增send/attachment/tag等冗余布尔列；既有无body PUT只确保可读，不把FULL降为READ；显式新body {accessLevel:"READ"|"FULL"}才切等级。非法值400。
- Applies to: ExpertMailReadAccessService全部读写、列表批量投影、授权controller
- Violation consequence: 旧授权被意外升级或旧页面把FULL降级
- 来源: original

### Invariant I-2: 精确接口与资源权限同时成立
- Rule: user1仍是user1，仅邮箱导航。认证开启时默认拒绝未列出的HandlerMethod+HTTP method；允许handler后仍查目标专家当前权限。READ只能读授权邮件/标签与审核草稿；FULL只操作该专家。不得按URL前缀、按钮可见性或请求体operatorName授权。管理员授权接口永不向user1放行。
- Applies to: AuthInterceptor/Policy、目标守卫、body专用校验、app请求闸门
- Violation consequence: 横向越权或变相全局管理员
- 来源: K-session-auth-does-not-imply-mail-authorization

### Invariant I-4: 由真实实体解析授权对象
- Rule: processing→contact、mailRecord→contact、tag→processing→contact、attachment三种owner→contact、schedule→contact、草稿owner+target均由DB解析。请求contactId必须与解析结果一致。expertId经现有Normalizer/IdentityResolver规范化后匹配FULL contact的orcid与当前层级；无匹配拒绝。同一专家共享档案/标签不是新增contact邮箱授权。
- Applies to: 所有新放行详情、编辑、文件与发送入口
- Violation consequence: 换id访问别人的邮件、附件或资料
- 来源: K-expert-document-ownership-chain

### Invariant I-14: 阶段边界与证据
- Rule: 按子计划顺序实施；每步≤10文件、≤2子系统、每张已有表每步≤1新增字段。新增表/字段/API是拟实现而非现状；先核对SHA/迁移号/已有脏改。没有通过真实MySQL门禁不能把跳过算PASS；本次只写计划及知识，不上线。
- Applies to: 实施/验证/发布报告
- Violation consequence: 范围漂移、迁移冲突、无证据的完成声明
- 来源: K-master-plan-shared-file-sequential-gates

## 现状审计
E01–E05及审计§1：V166只有行存在性；writer只有grant/revoke（另FK清理），reader为service+会话repo。新增一列后既有hasReadAccess保留行存在性；FULL守卫读取本列。X1，来源K-session-auth-does-not-imply-mail-authorization。

全量读写清单是本计划的组成部分：[审计§1–§5](expert-access-evidence/audit.md)。其中每张表列明schema、显式写者、FK/后台写入、读取与X交互点；未列为本步变更的writer保持原逻辑，不能因grant扩权改变后台任务。

## 实现方案
1. V170只加`access_level VARCHAR(8) NOT NULL DEFAULT 'READ'`（I-1）；服务拒绝未知值。V169由既有审核计划先落地，执行前核对真实Flyway迁移，不能改已应用脚本。
2. ExpertMailReadAccessService增加单个/批量等级与requireFull；NONE=无行。保留旧Boolean方法适配既有调用，新PUT以可选body区别旧调用和显式等级。授予/撤销仍只admin且正contactId（I-1/I-2/I-4）。取得等级不通过N+1，详情每请求实时重查。
3. controller响应保留username/contactId/canRead，并增加accessLevel；list items及message wrapper增加同名等级（admin视角是user1在此专家的等级）。MailboxConversationService附件门控从全局reader改为`reader && level!=FULL`，保留原件feature flag/账号约束（I-1/I-4）；后续下载权限03另行控制。
4. 两个测试文件覆盖升级、幂等、无body PUT、不存在专家、非法等级、READ零附件服务调用、FULL沿原投影（I-1/I-2/I-4/I-14）。

仅以下文件可变更；每个任务同时受其引用的不变量/样式契约约束。共享文件按本步前置版本接续，不覆盖上一阶段改动。

## 变更文件清单

| # | 路径 | 工作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V170__add_expert_mail_access_level.sql` | 新增access_level一列，READ默认 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt` | 等级读写/requireRead/requireFull及批量读取；不引入缓存 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertMailReadAccessController.kt` | 兼容旧PUT与新显式等级body |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | list/message DTO增加accessLevel |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt` | 批量等级投影；详情本次请求等级；FULL附件投影 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt` | 真实迁移/兼容/并发/身份 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxReadAccessProjectionTest.kt` | READ与FULL附件投影及批量标签 |

合计 **7 文件**；最多2个紧耦合子系统。新增共享store字段：access_level一列。

## 验收标准
- I-1：真实MySQL升级旧grant，断言均READ；并发重复PUT仅一行；旧PUT不降FULL；非法等级400且无写入；非admin管理403。
- I-2：真实AuthWebConfig+MVC逐个运行路由矩阵：NONE/READ/FULL/跨专家/admin；403之前SMTP、ES和文件流读取调用为0；未列端点仍403。
- I-4：用A的路径搭配B的processing/tag/attachment/schedule/draft/expertId；均无副作用。三种附件owner各测一次，保留原件路径和快照校验。
- I-14：逐项核对文件清单/迁移号/SHA/测试exit及skip；不改变application.yml、部署脚本与现有未提交改动。

门禁：JDK11 mvn test -Dtest=ExpertMailReadAccessIT -DmysqlIt=true；MailboxReadAccessProjectionTest；FlywayMigrationIntegrationTest -DmigrationIt=true，分别保留全新库/到V169旧库升级证据。

测试环境使用隔离MySQL/测试SMTP与临时附件目录；不得把mysqlIt连生产库。跳过/未运行必须记NOT_RUN，不能写PASS。

## 人工验收清单
### A-2: 等级切换与旧授权兼容
- 前置条件: 升级前已有甲READ授权；管理员与user1各开一浏览器。
- 操作步骤:
  1. admin选甲的权限select为可写。
  2. 刷新user1。
  3. 发送一次旧无body PUT，再刷新。
  4. 非admin请求授权接口。
- 预期结果: 旧授权初始READ；设置后FULL；旧PUT后仍FULL；user1改授权403。加载中select禁用，保存失败恢复上一个确认值。
- 覆盖: I-1/I-2/I-9/S-2/X1

### A-5: 同专家直接发送及越权拒绝
- 前置条件: 甲FULL、乙READ、丙未授权；测试SMTP收集器启用；准备各自附件/排期/来信id。
- 操作步骤:
  1. user1对甲写人工回复并显式发送。
  2. 把请求中的来信/附件/锚点/排期id分别换成乙或丙。
  3. 对乙尝试发送。
- 预期结果: 甲收到一次测试发送记录；审计user1；越权请求403或既有资源不匹配错误，无新增SMTP/文件内容；乙没有发送能力。
- 覆盖: I-2/I-4/I-8/X6/X7

本节为权威清单；进入人工验收时才导出同名前缀-acceptance.md，本次不生成衍生表。涉及尚未到本步的UI，使用同会话API执行等价动作并在最终08再次通过UI验证。

跨阶段人工项引用的其他I/S条款，以总计划同编号定义为准；本步实现仍严格限定本文件清单。
