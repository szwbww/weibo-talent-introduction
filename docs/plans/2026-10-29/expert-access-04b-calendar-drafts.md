# 专家分级权限 · 日历、个人草稿与审核权限联动

状态：DRAFT，待批准实施。基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`。仅计划，不代表实现/测试已通过。

证据：[审计总表](expert-access-evidence/audit.md)、[逐字源码E01–E37](expert-access-evidence/source-excerpts.md)、[schema迁移链](expert-access-evidence/schema-chain.md)、[检索收据](expert-access-evidence/store-usage.txt)、[SHA256](expert-access-evidence/sha256.json)。

前置：04a；既有draft-review-01/02/03后端已完成并通过各自验证。本文件是可执行边界，先读[总计划](expert-access-master.md)的协议/最终UI与依赖修正。

## 需求描述
FULL可维护当前专家真实日历排期并使用个人人工草稿；旧审核稿升级后仍按原状态锁定。两个子系统：日历、邮件草稿/回复。

必须保持：admin原接口、其他专家无写权限、标签投影、版本冲突校验、READ附件禁止；审核锁规则仍有效。

不做：其他子计划的改动；不加业务表/ES字段；不开放全局搜索、账户与批量操作。

## 关键不变量
### Invariant I-2: 精确接口与资源权限同时成立
- Rule: user1仍是user1，仅邮箱导航。认证开启时默认拒绝未列出的HandlerMethod+HTTP method；允许handler后仍查目标专家当前权限。READ只能读授权邮件/标签与审核草稿；FULL只操作该专家。不得按URL前缀、按钮可见性或请求体operatorName授权。管理员授权接口永不向user1放行。
- Applies to: AuthInterceptor/Policy、目标守卫、body专用校验、app请求闸门
- Violation consequence: 横向越权或变相全局管理员
- 来源: K-session-auth-does-not-imply-mail-authorization

### Invariant I-3: 标签可见不等于可写
- Rule: READ显示summary.expertTags和真实来信tags；空数组不显示占位，expertTags=null显示“标签暂不可用”。全部标签文字转义。READ无新增/删除/自动打标控件，手工调用写API返回403且DB/ES不变；FULL才可改当前专家标签。
- Applies to: 用户列表、头部、邮件标签、ExpertIndexController、InboundMailSummaryController
- Violation consequence: 只读用户修改数据或标签丢失
- 来源: K-expert-tag-editor-shared-render-contract

### Invariant I-4: 由真实实体解析授权对象
- Rule: processing→contact、mailRecord→contact、tag→processing→contact、attachment三种owner→contact、schedule→contact、草稿owner+target均由DB解析。请求contactId必须与解析结果一致。expertId经现有Normalizer/IdentityResolver规范化后匹配FULL contact的orcid与当前层级；无匹配拒绝。同一专家共享档案/标签不是新增contact邮箱授权。
- Applies to: 所有新放行详情、编辑、文件与发送入口
- Violation consequence: 换id访问别人的邮件、附件或资料
- 来源: K-expert-document-ownership-chain

### Invariant I-8: FULL发送复用现有事务链
- Rule: FULL可直接人工发送，无需草稿审核；仍复用两个现有rich-reply入口及attempt幂等、附件/锚点归属、安全确认。鉴权位于业务副作用前，服务入口用authenticatedUsername再次校验；请求体operatorName不代表身份，user1审计写session名。已获准开始的SMTP请求不承诺被后续撤权召回；撤权后的新请求403。
- Applies to: PendingMailOperationService两个入口、旧草稿绑定、下载
- Violation consequence: 重复发送或绕过安全/资源校验
- 来源: K-manual-send-success-transaction-and-dedup

### Invariant I-11: 审核是明确依赖，不伪造已实现
- Rule: READ审核流程沿既有draft-review计划；FULL新人工稿走原mailbox_reply_draft。已提交审核稿不因升级自动发送/自动撤回；已审核稿对user1始终不可改（即使FULL），要发送新内容需新建人工稿。管理员仍在同一富文本框审核/人工回复，审核通过不自动发，批准后对话更新须确认。待审入口只搬进原popover顶部，原admin草稿tab含义不变。
- Applies to: MailReviewDraftService、两端宿主、pendingReviewOnly查询
- Violation consequence: 绕过审批锁或把原型当生产功能
- 来源: original

### Invariant I-13: 不扩大成全局管理
- Rule: FULL范围为当前专家人工邮件/附件资料/人工档案/标签/备注位置/排期/现有单专家状态管理；全局邮箱账号配置、批量发送、任务/AI训练、模板配置、全局专家搜索、人工接管分派、会议视频库不开放。自动收发/晋级/标签流程不受user1交互授权守卫影响。READ附件仍不可查看下载，邮件正文邮箱不做脱敏承诺。
- Applies to: 精确路由表、用户宿主菜单、后台任务
- Violation consequence: 无关功能扩权或后台任务中断
- 来源: original

## 现状审计
E13/E14/E16/E30–E34、审计§3/§4/§5。ExpertManualProfileController有catch(Exception)会把权限异常映射500，须在同文件保留明确403。MeetingCalendar新建contactId在body、events查询可省contactId，必须修正user1准入；旧MeetingSchedule不替代本次入口。原mailbox_reply_draft仅owner隔离还不够；审核store依赖原draft-review方案，不能自行新造另一套。X2/X6/X8/X9/X12。

全量读写清单是本计划的组成部分：[审计§1–§5](expert-access-evidence/audit.md)。其中每张表列明schema、显式写者、FK/后台写入、读取与X交互点；未列为本步变更的writer保持原逻辑，不能因grant扩权改变后台任务。

## 实现方案
1. MeetingCalendarController对user1 events要求正contactId且FULL；summaries每个id必须FULL，非空数组；create从DTO取contactId验FULL，update/cancel/event从event.id查真实contact。保留expectedUpdatedAt冲突，不能直接放行旧meeting-schedules替代（I-2/I-4/I-13）。
2. MailReplyDraftService所有target/id/save/discard/send入口校验owner+当前FULL+真实target；MailReplyDraftRepository的list/count/summaries在SQL内加FULL EXISTS，保留owner/分页，不先取全表再过滤；管理员不加用户FULL条件（I-4/I-8）。
3. UnmatchedInboundMailController.getUnmatchedDetail只对user1已绑定FULL专家返回本条record/contact/logs，不调用suggestCandidates，candidates=[]。mark/cancel-resolved的操作者来自session（I-2/I-4）。
4. MailReviewDraftService沿既有前置计划，READ/FULL均可读自己的审核稿；APPROVED不可改删优先于FULL；升级不自动改SUBMITTED，也不发送。FULL新人工稿走mailbox_reply_draft，不能用reviewDraftRef绕过审核（I-11）。
5. 上述守卫完备后，MailReadOnlyPolicy登记精确handler；ExpertScopedOperationsIT追加日历跨id/日期版本、草稿SQL分页/撤权、审核四态/升级用例；保留04a测试（I-2/I-4/I-8/I-11/I-13）。
6. 复用03固定ExpertMailPermissionGuard的PROCESSING解析，将本步已去候选的getUnmatchedDetail按精确GET登记；不复制权限查询、不扩URL前缀。新增非空guard依赖时迁移下列已证明构造/Spring消费者；旧已开始SMTP的finalize不得被新增发送后权限复查打断。

本步仅修改以下文件；与其他子计划共享的Auth策略/测试顺序累积，不能覆盖前一步。

## 变更文件清单

| # | 路径 | 工作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarController.kt` | 本步任务明确的目标守卫/验证；其余原行为保留 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt` | 本步任务明确的目标守卫/验证；其余原行为保留 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt` | 本步任务明确的目标守卫/验证；其余原行为保留 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 本步任务明确的目标守卫/验证；其余原行为保留 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt` | 本步任务明确的目标守卫/验证；其余原行为保留 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt` | 本步任务明确的目标守卫/验证；其余原行为保留 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertScopedOperationsIT.kt` | 本步任务明确的目标守卫/验证；其余原行为保留 |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt` | 仅接续本步完成的精确handler/真实资源解析，复用既有规则 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt` | 非空guard fixture接线，保留admin/CAS/日期与真实MySQL行为 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt` | 日历直构造接线，保留归属/拒绝零副作用矩阵 |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt` | 真实JDBC构造guard/access；原owner/CAS/admin/运行期契约不减 |
| 12 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftServiceTest.kt` | 非空guard接线及本步用户权限行为，原admin语义保留 |
| 13 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 仅draft service构造接线，保留原断言 |
| 14 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt` | 原共享support导入真实guard/access供已有发送IT，不新建fixture类 |
| 15 | `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt` | 精确已完成方法矩阵；退役临时personal-drafts全局拒绝pin，保留稳定安全/default-deny |
| 16 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 只改原unmatched-controller noise-site行号，原片段/规则不变 |

共16文件；I-14有界例外仅为原7加既有中央guard登记及8个已证明构造/Spring/安全矩阵/行号fixture；不增加本步产品能力，共享store新增字段0。

## 验收标准
- I-2：真实AuthWebConfig+MVC逐个运行路由矩阵：NONE/READ/FULL/跨专家/admin；403之前SMTP、ES和文件流读取调用为0；未列端点仍403。
- I-3：同一专家含ES标签和QA/CUSTOM邮件标签；断言READ可见且不存在编辑/删除节点，直接POST/DELETE均403；空/null/恶意HTML分别验证。
- I-4：用A的路径搭配B的processing/tag/attachment/schedule/draft/expertId；均无副作用。三种附件owner各测一次，保留原件路径和快照校验。
- I-8：SMTP mock证明FULL一次发送、READ/NONE/换专家0次；重试/DEDUP_SENT/DELIVERY_UNKNOWN维持原语义；user1审计不是body伪造的admin。
- I-11：审核四态/评论/历史/退回/撤销与旧方案回归；升级FULL不能改APPROVED；新人工稿可发送；审核通过与确认对话更新均不自动SMTP。
- I-13：管理员全路径回归；user1无法打开其他tab及全局端点。定时收信与自动标签仍执行，READ附件响应仍空且直链403。

门禁：JDK11 mvn test -Dtest=ExpertScopedOperationsIT -DmysqlIt=true；ExpertManualProfileControllerTest/ServiceTest、原MeetingCalendar测试、MailReviewDraftIT回归。

测试环境使用隔离MySQL/测试SMTP与临时附件目录；不得把mysqlIt连生产库。跳过/未运行必须记NOT_RUN，不能写PASS。

## 人工验收清单
### A-1: READ标签可见且不可写
- 前置条件: 测试库有授权READ专家甲，ES标签“新发现”“瑞典”，一封来信带QA标签“薪资”和CUSTOM标签“补充材料”。
- 操作步骤:
  1. user1登录，选甲。
  2. 查看列表/头部与来信。
  3. 用同会话请求新增/删除专家标签和邮件标签。
- 预期结果: 上述四个标签出现在对应区域；无新增/×控件；两类写接口403，admin刷新标签未变；无附件下载按钮。
- 覆盖: I-2/I-3/I-13/S-1/X2/X8

### A-5: 同专家直接发送及越权拒绝
- 前置条件: 甲FULL、乙READ、丙未授权；测试SMTP收集器启用；准备各自附件/排期/来信id。
- 操作步骤:
  1. user1对甲写人工回复并显式发送。
  2. 把请求中的来信/附件/锚点/排期id分别换成乙或丙。
  3. 对乙尝试发送。
- 预期结果: 甲收到一次测试发送记录；审计user1；越权请求403或既有资源不匹配错误，无新增SMTP/文件内容；乙没有发送能力。
- 覆盖: I-2/I-4/I-8/X6/X7

### A-7: 资料、标签、排期管理
- 前置条件: 甲FULL且有currentIndexLevel和有效ORCID；丙未授权；甲人工档案version已记录。
- 操作步骤:
  1. user1修改甲人工资料与标签，新增/改期/取消一个测试排期。
  2. admin回读。
  3. 用旧version保存，换丙expertId或scheduleId请求。
- 预期结果: admin看到同一甲资料/标签/排期；旧version409；跨专家无修改；普通保存/改期/取消不发送邮件；user1仍只有收发件箱tab。
- 覆盖: I-2/I-3/I-4/I-13/X8/X9

### A-11: FULL升级不解锁旧审核稿
- 前置条件: 甲READ，有UNSUBMITTED/SUBMITTED/RETURNED/APPROVED审核稿各一份。
- 操作步骤:
  1. admin升级甲FULL。
  2. user1打开旧APPROVED稿尝试改删。
  3. 新建人工稿并在测试SMTP显式发送。
- 预期结果: APPROVED仍不可改删；SUBMITTED仍待审，不自动发送；FULL新人工稿可以直接发送；降READ后旧人工稿不可再通过发送API绕过审核。
- 覆盖: I-8/I-11/X7/X12

### A-13: 管理员与自动流程不回归
- 前置条件: admin现有人工草稿/挂起/筛选与测试自动收信可用；准备禁用真实邮箱和SIMULATOR。
- 操作步骤:
  1. admin按原流程保存并发测试草稿、查看附件、筛选挂起。
  2. 执行测试收信、自动标签、UID补收。
  3. 查看全局来信汇总及个人待处理。
- 预期结果: 旧人工草稿/幂等/附件保持；自动流程不因无user1 session失败；禁用真实邮箱邮件仍可见、SIMULATOR不计；全局汇总保留所有人，个人待处理分归属。
- 覆盖: I-5/I-7/I-8/I-12/I-13/X3/X4/X7

本节为权威清单；进入人工验收时才导出同名前缀-acceptance.md，本次不生成衍生表。涉及尚未到本步的UI，使用同会话API执行等价动作并在最终08再次通过UI验证。

跨阶段人工项引用的其他I/S条款，以总计划同编号定义为准；本步实现仍严格限定本文件清单。

阶段验收说明：人工条目保留跨阶段最终行为；本步先执行其覆盖到的API路径，所有完整UI场景在08再验一次。

## 修正记录
- 2026-10-30：按持续授权，03真实中央guard登记、非空draft/calendar guard依赖与现有消费者需要补9个文件（完整构造/import/直调/精确矩阵/lineguard收据见fast-p children/04b/execution.md Epoch1），7→16授予I-14有界例外；保留原admin/owner/CAS/发送finalize/拒绝副作用，临时全局draft禁用pin退役。新增fixture原门禁均需执行，含MeetingCalendarControllerMysqlTest、MeetingCalendarServiceMysqlTest、ExpertMailPermissionGuardTest、MailReplyDraftRepositoryIT、MailReplyDraftSendIntegrationTest、MailReviewSendIT、MeetingCalendarSendIntegrationTest、MailReplyDraftServiceTest、PendingMailOperationServiceTest、AuthInterceptorTest、OperatorStatusWriteSeamGuardTest，显式mysqlIt/migrationIt、目标零skip；原必需门禁不减。
