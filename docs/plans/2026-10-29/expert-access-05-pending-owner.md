# 专家分级权限 · 来信归属写入与撤权兜底

状态：DRAFT，待批准实施。基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`。仅计划，不代表实现/测试已通过。

证据：[审计总表](expert-access-evidence/audit.md)、[逐字源码E01–E37](expert-access-evidence/source-excerpts.md)、[schema迁移链](expert-access-evidence/schema-chain.md)、[检索收据](expert-access-evidence/store-usage.txt)、[SHA256](expert-access-evidence/sha256.json)。

前置：04b；V170已落地；阶段05仅记录归属，06同步个人队列后才能对外启用FULL授权UI。本文件是可执行边界，先读[总计划](expert-access-master.md)的协议/最终UI与依赖修正。

## 需求描述
新来信在FULL授权生效后归user1；历史admin待处理保留；降级/撤权不遗失待办。

必须保持：原IMAP物理唯一键、自动分类、手工绑定/完成/撤销、专家整体needsManualAttention保持。

不做：不新增派单系统/可选处理人，不批量迁移历史待办，不修改自动回复配置。

## 关键不变量
### Invariant I-1: 三态单一事实
- Rule: 无授权行=NONE；有行access_level仅READ/FULL，NOT NULL默认READ。旧授权迁移为READ。canRead由等级派生，不增send/attachment/tag等冗余布尔列；既有无body PUT只确保可读，不把FULL降为READ；显式新body {accessLevel:"READ"|"FULL"}才切等级。非法值400。
- Applies to: ExpertMailReadAccessService全部读写、列表批量投影、授权controller
- Violation consequence: 旧授权被意外升级或旧页面把FULL降级
- 来源: original

### Invariant I-5: 来信归属一次分配
- Rule: 新增inbound_mail_processing.pending_owner_username VARCHAR(64) NOT NULL DEFAULT 'admin'；历史行全部admin，不用receivedAt猜归属。两个新建sink在同一事务锁contact并读取当前FULL：FULL→user1，否则admin；无contact→admin。以首次持久化时权限为准，重试、UID补收重复命中不重分配。物理邮箱mailbox_owner_code原语义不变。
- Applies to: 两个new sink、迁移、grant/revoke contact锁
- Violation consequence: 历史待处理被搬动或物理邮箱错乱
- 来源: K-inbound-processing-write-paths

### Invariant I-6: 撤权不产生孤儿待办
- Rule: FULL→READ/NONE时，持同一contact锁，在同一事务把仍MANUAL_REVIEW且owner=user1的行移给admin；READ授权、升FULL、重复FULL不搬历史admin待办。已完成行暂不改，撤销完成时如果owner=user1而当前不再FULL，回admin。这是本计划明确提出的撤权兜底策略，待整份计划批准；非声称线上现状。
- Applies to: 等级变更/revoke、reopenManualResolved、队列读取
- Violation consequence: 失权后无人可处理或重新授权偷走旧待办
- 来源: original

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

### Invariant I-14: 阶段边界与证据
- Rule: 顺序实施、默认每步≤10文件/≤2子系统/已有表每步≤1新增字段；本步仅为已证明的3个窄UPDATE/锁消费者fixture授予9→12文件有界例外，产品范围不扩大。核对SHA/迁移号/已有脏改；真实MySQL目标skip不能算PASS；授权实施不授权部署。
- Applies to: 实施/验证/发布报告
- Violation consequence: 范围漂移、迁移冲突、无证据的完成声明
- 来源: K-master-plan-shared-file-sequential-gates

## 现状审计
E07–E11及constructors/repository-calls收据列明六save+一个窄UPDATE。processSingle已有事务，必须在当前事务内使用contact行锁；不能private @Transactional假装开新事务。V167物理唯一键保持。X3/X4/X5，审计§2。

全量读写清单是本计划的组成部分：[审计§1–§5](expert-access-evidence/audit.md)。其中每张表列明schema、显式写者、FK/后台写入、读取与X交互点；未列为本步变更的writer保持原逻辑，不能因grant扩权改变后台任务。

## 实现方案
1. V171新增pending_owner_username默认admin，只允许代码写admin/user1。新增组合索引`(pending_owner_username,process_status,expert_contact_id,sender_account_code)`；06用EXPLAIN确认用途，不能未经依据加多套冗余索引（I-5）。
2. ExpertMailReadAccessService所有等级变更与两个new sink共用`SELECT id FROM expert_contact WHERE id=:id FOR UPDATE`。锁顺序contact→grant/processing；processSingle已处事务；新sink锁后读取等级，构造新行一次分配。未匹配来信admin，手工绑定时继续admin（I-1/I-5）。
3. 四个copy-save点（Auto重分类、Unmatched绑定/完成、Pending完成）改用Repository中明确原字段列表的参数化UPDATE，不SET pending_owner_username；需要返回实体时回读。保留原状态前置条件、审计、附件建档与needsManualAttention逻辑。禁止删字段或把所有列重新save（I-12/I-13）。
4. 同一事务中FULL→READ/NONE先锁contact，再更新/删grant，把该contact尚MANUAL_REVIEW、owner=user1的行改admin；READ/FULL重复及升FULL不搬旧行。SQL完成/重开使用固定锁顺序避免并发覆盖。重开原owner=user1但失去FULL时，条件UPDATE将owner改admin；保留原只允许MANUAL_RESOLVED重开的谓词（I-6/I-12）。
5. InboundPendingOwnerIT用线程屏障覆盖grant/revoke对两个sink、四窄更新、重开；测试旧行默认admin、绑定不重新分派、分类成PROCESSED不计pending、另一用户尚pending不清needsManualAttention。原ExpertMailReadAccessIT增加回滚/幂等用例（I-5/I-6/I-12/I-14）。

仅以下文件可变更；每个任务同时受其引用的不变量/样式契约约束。共享文件按本步前置版本接续，不覆盖上一阶段改动。

## 变更文件清单

| # | 路径 | 工作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V171__add_inbound_pending_owner.sql` | 新增pending_owner_username一列与查询索引 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt` | 映射归属默认admin |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt` | 原copy-save窄UPDATE和重开兜底 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 两新建sink锁contact并分配、重分类窄UPDATE |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 完成/重开保留归属与整体needsManualAttention |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt` | 绑定/完成窄UPDATE，历史admin不搬 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt` | 等级变更锁contact、撤权事务回收待办 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt` | 新增事务并发、旧库升级、六save路径覆盖 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt` | 等级降级与队列兜底联动 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 退役仅save次数/第二实体副本pin；迁移实际重分类失败注入，保留状态、物理源/UID/ack/通知/标签/无SMTP行为 |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt` | narrow UPDATE后真实结果fixture/reread，保留绑定/alias/材料桥接/完成/整体attention；不重新钉mock转发或save次数 |
| 12 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | linked-contact非空锁fixture接线；保留重开triplet、0行409、missing404、null-contact/审计/attention |

合计 **12 文件**；I-14例外仅为上述3个现有必需写路径/锁消费者fixture，产品仍原范围。新增共享store字段：pending_owner_username一列。

## 验收标准
- I-1：真实MySQL升级旧grant，断言均READ；并发重复PUT仅一行；旧PUT不降FULL；非法等级400且无写入；非admin管理403。
- I-5：旧库upgrade、两个新建sink、UID重复、无contact、并发grant/新建，断言只产生确定的admin或user1归属；同物理邮件重复不改owner。
- I-6：FULL→READ/NONE与收信并发，未完成user1行回admin；完成行重开时失权回admin；升FULL不转历史admin行。事务失败完整回滚。
- I-12：并发撤权与四个窄UPDATE/重开；owner不被陈旧copy覆盖，其他字段原语义保留；另一处理人仍有pending时needsManualAttention不得清零。
- I-13：管理员全路径回归；user1无法打开其他tab及全局端点。定时收信与自动标签仍执行，READ附件响应仍空且直链403。
- I-14：逐项核对文件清单/迁移号/SHA/测试exit及skip；不改变application.yml、部署脚本与现有未提交改动。

门禁：JDK11 mvn test -Dtest=InboundPendingOwnerIT,ExpertMailReadAccessIT -DmysqlIt=true；FlywayMigrationIntegrationTest -DmigrationIt=true；AutoMailReplyService/Unmatched/Pending现有相关测试。
上述3个fixture的原回归必须fresh执行、相关opt-in显式启用、目标零skip。实现细节save次数/转发副本断言退役，不改成新窄UPDATE调用次数pin；consumer状态/失败恢复/ack/审计/归属不覆盖以行为断言和真实MySQL并发证明。

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
- 2026-10-30：持续授权下补3个已证明写路径/锁消费者fixture，9→12的I-14有界例外；完整save/mock/failure注入/重开锁收据见fast-p children/05/execution.md Epoch1。产品/构造/端点不扩大，既定I-12窄UPDATE不能保留旧两save和陈旧回读fixture；退役实现次数/副本pin，不重新钉窄调用，保留全部状态/失败无ack/物理邮箱/审计/attention安全行为。原门禁不减，新增fixture必须执行。
