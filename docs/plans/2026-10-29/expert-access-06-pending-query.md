# 专家分级权限 · 个人待处理查询、计数与筛选

状态：DRAFT，待批准实施。基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`。仅计划，不代表实现/测试已通过。

证据：[审计总表](expert-access-evidence/audit.md)、[逐字源码E01–E37](expert-access-evidence/source-excerpts.md)、[schema迁移链](expert-access-evidence/schema-chain.md)、[检索收据](expert-access-evidence/store-usage.txt)、[SHA256](expert-access-evidence/sha256.json)。

前置：05；既有draft-review-02-query落地且可复用pendingReviewOnly查询。本文件是可执行边界，先读[总计划](expert-access-master.md)的协议/最终UI与依赖修正。

## 需求描述
管理员待处理不再包含user1的新待办；两端角标、个人队列、列表计数一致；服务端支持可写/待审专家筛选。

必须保持：All邮件时间线、全局监控/来信汇总、禁用真实账号可见、原挂起/已提供状态语义保持。

不做：不改全球监控统计，不按前端当前页计算总数，不存冗余pending计数。

## 关键不变量
### Invariant I-2: 精确接口与资源权限同时成立
- Rule: user1仍是user1，仅邮箱导航。认证开启时默认拒绝未列出的HandlerMethod+HTTP method；允许handler后仍查目标专家当前权限。READ只能读授权邮件/标签与审核草稿；FULL只操作该专家。不得按URL前缀、按钮可见性或请求体operatorName授权。管理员授权接口永不向user1放行。
- Applies to: AuthInterceptor/Policy、目标守卫、body专用校验、app请求闸门
- Violation consequence: 横向越权或变相全局管理员
- 来源: K-session-auth-does-not-imply-mail-authorization

### Invariant I-7: 计数按处理人、数量单位明确
- Rule: 我的待处理=MANUAL_REVIEW且pending_owner_username=session，排除SIMULATOR_NOOP/不存在账号，保留禁用真实邮箱；user1再受可读/FULL准入约束。图标角标复用当前账号pendingBadge.manualReviewTotal（邮件条数，含既有挂起排除），不受列表搜索/分页影响；0隐藏，失败显示不可用而非0。待处理tab数字仍为专家数，待审核是SUBMITTED草稿数。count/page/explain共用条件，All保留历史邮件。监控、来信汇总不变。
- Applies to: 会话聚合/排序/分页、挂起、旧队列、顶栏及更多图标
- Violation consequence: 列表与角标不一致或user1邮件仍进admin待处理
- 来源: K-mailbox-inbound-source-authority

### Invariant I-12: 存储写路径不覆盖归属
- Rule: 新归属列只由新建分配、撤权兜底、撤销完成兜底写；其余四个已有copy-save改用保持其原字段集合的窄UPDATE，禁止陈旧实体save覆盖归属。新写读由同一MySQL事务处理，不挂best-effort。contact.needsManualAttention仍按专家所有待处理数维护。
- Applies to: AutoMailReplyService重分类、Unmatched绑定/完成、Pending完成、Repository重开
- Violation consequence: 并发撤权后旧save恢复user1导致待办丢失
- 来源: K-inbound-processing-write-paths

### Invariant I-13: 不扩大成全局管理
- Rule: FULL范围为当前专家人工邮件/附件资料/人工档案/标签/备注位置/排期/现有单专家状态管理；全局邮箱账号配置、批量发送、任务/AI训练、模板配置、全局专家搜索、人工接管分派、会议视频库不开放。自动收发/晋级/标签流程不受user1交互授权守卫影响。READ附件仍不可查看下载，邮件正文邮箱不做脱敏承诺。
- Applies to: 精确路由表、用户宿主菜单、后台任务
- Violation consequence: 无关功能扩权或后台任务中断
- 来源: original

## 现状审计
E05/E06/E09与审计§2、§5。pending_flag→SUM→排序/HAVING/total同链；suspensionStatesByContacts与pendingBadge是独立SQL必须一起补。旧队列controller原无session参数，不能默认全局。X3/X4/X5/X11。

全量读写清单是本计划的组成部分：[审计§1–§5](expert-access-evidence/audit.md)。其中每张表列明schema、显式写者、FK/后台写入、读取与X交互点；未列为本步变更的writer保持原逻辑，不能因grant扩权改变后台任务。

## 实现方案
1. MailboxConversationRepository把owner条件作用于pending_flag，不删rangeUnion中的其他来信；count/page/explain一致注入session参数，All仍含原timeline。suspensionStatesByContacts亦按本人owner统计；保留当前账号筛选对列表pendingCount的影响，跨账号suspensionPendingCount仍跨账号但只本人（I-7）。
2. MailboxSuspensionService.pendingBadge保留原真实邮箱join与用户挂起排除，再加owner；pendingCount/contact准入按当前用户owner，不能挂起只有另一人的pending。旧队列Repository list/count/reason totals增加actor参数，Service/Controller从session显式传入，内部旧调用默认admin只作兼容；不把user1无contact来信开放（I-2/I-7）。
3. conversation list新增expertScope=ALL/USER1_FULL/PENDING_REVIEW，严格枚举；仅admin可请求后两项（user1用现有权限过滤+个人pending）。USER1_FULL用grant EXISTS；PENDING_REVIEW复用既有review-only SQL，不能查出来后内存过滤；所有筛选AND，count/page/explain同一谓词（I-7）。
4. 为更多筛选提供管理员计数`user1FullExpertCount`（拥有真实会话的FULL专家数）及`pendingReviewDraftCount`（可见SUBMITTED草稿数），计数不受当前页/q/普通筛选影响，沿全部非SIMULATOR真实账号口径；必须由SQL/既有审核Service真实读取。放在conversation list响应增加可选filterCounts，空页仍返回；user1不返回管理员统计。全局pendingBadge.manualReviewTotal仍由既有独立端点，和草稿数分开（I-7）。
5. 三个现有IT测试扩大数据矩阵：同专家双方owner、两页、多账号/disabled、挂起、provided、reopen、同日期排序；真实EXPLAIN记录归属索引与count/page结果，验证全局来信汇总未改变（I-7/I-12/I-13）。
6. 仅在个人owner SQL/挂起准入完成后，接续03暂缓的精确key：pendingBadge GET只取session；suspension GET、suspend PUT、updateSuspensionReason PATCH、resumeSuspension DELETE均先验FULL path contact并由本步服务限制本人pending。只改policy登记/guard固定规则，不新增flag/schema探测/宽泛前缀；既有InboundPendingOwnerIT加载真实AuthWebConfig证明准入与跨专家/READ拒绝。

仅以下文件可变更；每个任务同时受其引用的不变量/样式契约约束。共享文件按本步前置版本接续，不覆盖上一阶段改动。

## 变更文件清单

| # | 路径 | 工作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` | union pending_flag及count/page/explain，FULL筛选与批量counts |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt` | session作用域参数与筛选校验 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | expertScope参数/筛选计数响应、reader pendingBadge |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt` | pendingBadge及个人挂起count按owner |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt` | 旧队列list/count/reason totals按owner |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt` | 显式actor传入队列查询 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 从session传actor，禁止query伪造 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt` | 混合归属/组合分页/EXPLAIN |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt` | 挂起计数/邮件badge口径 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt` | 旧队列与新列表交叉验证 |
| 11 | `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt` | 仅激活03暂缓的owner依赖精确handler/verb |
| 12 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt` | 仅接续上述精确key及现有session/FULL资源规则 |
| 13 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 真实JDBC owner fixture及个人pending行为迁移，退役op1/op2共享全局待处理pin，保留隔离/幂等/账号/无写副作用 |
| 14 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt` | 必需actor参数matcher/调用接线，原账号/查询/分页行为保留，不加转发pin |
| 15 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 仅原unmatched-controller noise-site行号，路径/上下文/规则不变 |
| 16 | `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt` | 精确矩阵只加已完成5个owner路由；现有READ挂起403及其他default-deny不变 |

合计 **16 文件**；I-14例外仅为原10加03延迟路由的2个精确激活文件及4个已证明fixture消费者；产品能力/字段不扩大，auth/mail紧耦合边界不变。新增共享store字段0。

## 验收标准
- I-2：真实AuthWebConfig+MVC逐个运行路由矩阵：NONE/READ/FULL/跨专家/admin；403之前SMTP、ES和文件流读取调用为0；未列端点仍403。
- I-7：真实SQL验证count/page/explain同口径；两人混合来信、禁用真实邮箱、SIMULATOR、挂起、跨页；admin/user1角标分别等于各自未挂起邮件数，专家数另算。
- I-12：并发撤权与四个窄UPDATE/重开；owner不被陈旧copy覆盖，其他字段原语义保留；另一处理人仍有pending时needsManualAttention不得清零。
- I-13：管理员全路径回归；user1无法打开其他tab及全局端点。定时收信与自动标签仍执行，READ附件响应仍空且直链403。

门禁：JDK11 mvn test -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,InboundPendingOwnerIT -DmysqlIt=true；原监控/来信汇总测试回归；不得跳过DB用内存假结果替代。
追加MailboxConversationControllerTest、UnmatchedInboundMailServiceTest、OperatorStatusWriteSeamGuardTest、AuthInterceptorTest及真实InboundMailProcessingRepositoryTest原回归，按实际mysqlIt/migrationIt门禁显式启用、目标零skip。过滤计数复用原repository/JDBC/reviewPendingPredicate，无新增构造依赖或第二计数约定；全局历史统计与原监控验收不减。

测试环境使用隔离MySQL/测试SMTP与临时附件目录；不得把mysqlIt连生产库。跳过/未运行必须记NOT_RUN，不能写PASS。

## 人工验收清单
### A-3: 新旧待处理分开
- 前置条件: 甲有2封旧MANUAL_REVIEW归admin；乙有1封归admin。用测试IMAP/测试入口导入，不向真实专家发送。
- 操作步骤:
  1. admin授予甲FULL。
  2. 给甲新增3封测试来信并重复补收其中1封。
  3. 两账号看待处理与全部。
- 预期结果: admin待处理仍3封；user1为3封；admin All可看甲全部5封；重复补收不增行/不改归属；待处理专家数admin=2、user1=1。
- 覆盖: I-5/I-7/I-12/X3

### A-4: 降级、撤权和撤销完成
- 前置条件: 甲FULL且有2封归user1的未处理、1封已完成；留一封历史admin待处理。
- 操作步骤:
  1. admin把甲降READ。
  2. 刷新两端；admin撤销那封已完成邮件的完成状态。
  3. 再升FULL。
- 预期结果: 降级后user1待处理0，admin新增2；重开再增1；旧admin待办始终保留；再升FULL不把已有admin待办迁给user1。READ仍能读甲邮件和标签。
- 覆盖: I-6/I-7/I-12/X4/X5

### A-8: 原更多筛选完整保留
- 前置条件: 有不同账号、标签、日期和方向的测试邮件，甲FULL；至少一份SUBMITTED审核草稿。
- 操作步骤:
  1. admin点搜索右侧⋯。
  2. 核对专家筛选位于最上。
  3. 选择user1可写并叠加账号/方向/标签/邮箱/关键词/日期。
  4. 应用、Reset、切tab再返回。
- 预期结果: 原七字段均存在；Apply只有一个；筛选AND并回第1页；Reset恢复全部专家范围并保留原tab/q语义；刷新旁无新下拉。
- 覆盖: I-9/S-2/S-3/X11

### A-9: 角标不是筛选数或草稿数
- 前置条件: admin有3封未挂起待处理邮件、2位待处理专家、5份待审草稿；user1有1封待处理。
- 操作步骤:
  1. admin看⋯角标，user1看待处理入口。
  2. admin设置两个普通筛选，切页。
  3. admin处理完3封后刷新。
- 预期结果: admin图标3；user1待处理显示1位专家；admin待处理tab2、待审选项5条；设置两个筛选后图标仍3；清零后角标隐藏。
- 覆盖: I-7/I-9/S-3/X3/X11

### A-13: 管理员与自动流程不回归
- 前置条件: admin现有人工草稿/挂起/筛选与测试自动收信可用；准备禁用真实邮箱和SIMULATOR。
- 操作步骤:
  1. admin按原流程保存并发测试草稿、查看附件、筛选挂起。
  2. 执行测试收信、自动标签、UID补收。
  3. 查看全局来信汇总及个人待处理。
- 预期结果: 旧人工草稿/幂等/附件保持；自动流程不因无user1 session失败；禁用真实邮箱邮件仍可见、SIMULATOR不计；全局汇总保留所有人，个人待处理分归属。
- 覆盖: I-5/I-7/I-8/I-12/I-13/X3/X4/X7

### A-15: 并发归属不被旧保存覆盖
- 前置条件: 测试库准备甲FULL、user1待处理及可重开的完成行；用测试屏障延迟状态更新事务。
- 操作步骤:
  1. 并发执行甲降级与标完成/绑定/重开/自动响应重分类。
  2. 查看两账号待处理，重试失败事务。
- 预期结果: 无owner被陈旧save恢复user1；每封尚未完成且已失权邮件都能在admin待处理找到；另一人仍有pending时专家needsManualAttention保持true。
- 覆盖: I-5/I-6/I-12/X4/X5

本节为权威清单；进入人工验收时才导出同名前缀-acceptance.md，本次不生成衍生表。涉及尚未到本步的UI，使用同会话API执行等价动作并在最终08再次通过UI验证。

跨阶段人工项引用的其他I/S条款，以总计划同编号定义为准；本步实现仍严格限定本文件清单。

## 修正记录
- 2026-10-30：按持续授权及03实际无owner SQL收据（fast-p children/03/execution.md Epoch1），将pendingBadge/suspension准入推迟至本步既定个人owner SQL后。补入policy/guard两个直接激活文件，10→12的I-14有界例外；既有owner IT增加真实AuthWebConfig准入回归，不以03暂拒变为最终缺失，不改最终UI/人工验收。
- 2026-10-30：持续授权下补4个owner消费/参数/精确矩阵/行号fixture，12→16有界例外；完整grep、真实JDBC旧globalpending断言、matcher参数和handler精确集合/lineguard收据见fast-p children/06/execution.md Epoch1及addendum。只退役与owner=session直接冲突的全局pending pin，稳定READ/NONE/跨资源/账号/分页/幂等/拒绝副作用保持；不新增产品依赖，复用原SQL/JDBC/review谓词，所有新增fixture实际执行。
- 2026-10-30：Epoch2真实DTO/app/完整grep证明既有字段manualReviewTotal而非total；持续授权下规划命名对齐，不加alias/shim或wire切换，所有计数/权限/失败行为不减。旧epoch部分授权实现保持未验证，按新身份epoch3完成已有16文件与全部fresh门禁/runtime。
