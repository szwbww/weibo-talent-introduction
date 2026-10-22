# Child 04 approved execution brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master
- Branch: fast/batch-demand-master
- Exact approved plan: docs/plans/2026-10-22/batch-demand-04-execution.md
- Plan identity: commit:f684da0a316b8cfb560fe7ebb0a8817d8a2594f2
- Approval: explicit user `/fast-p docs/plans/2026-10-22/batch-demand-master.md`, 2026-10-22. The invocation authorizes these exact bytes despite their pre-approval status wording; do not alter plan text.
- Depends on: 02,03
- Product base: prior child terminal Code head, supplied at dispatch

## Complete child contract

# 04 首封与材料提醒按成功配额拉取

状态：待批准。依赖：03（也依赖02游标）。范围：7文件；批量选人/执行一个子系统。部署本步后真实执行切换按需读取，不依赖05缓存。

## 需求描述

首封和材料提醒均按本次成功额度拉取候选，达到额度立即结束；不为发30封先全量扫描/计数。保持全部安全门禁、NEW重试优先级、失败/跳过继续补人、原邮件事务及取消语义；候选不足允许扫描至耗尽。范围不含收信、SMTP/IMAP实现、单封发送、账号配额算法、定时器或线上补发。

## 关键不变量

### Invariant I-1: 额度独立于估计
- Rule: 两个执行循环只以roundSize、剩余有效轮数、实时账号容量与成功数决定额度；不得调用countEsTargets、countBySnapshot、全量scanBaseCandidates、全量findAll NEW或全量material snapshot。oneRoundOnly=1轮，否则roundsPerRun；达到轮数/成功配额先停止，再考虑hasNext，禁止终止后预读。缓存不存在/0/过期都不能阻止执行。
- Applies to: INTRODUCTION和MATERIAL_REMINDER初始化、每轮quota、轮间sleep及终止条件。
- Violation consequence: 错误估计成为发送上限或再次全扫。
- 来源: K-batch-send-round-loop-symmetry


### Invariant I-2: 原始分页与去重
- Rule: 候选源顺序保留NEW优先再ES；每次只加载1..500条，按min(500,max(1,2×剩余本轮成功额度))取页（乘法使用Long）。原始页游标推进，内存过滤后空页必须继续。按既有身份/邮箱去重规则跨页、跨NEW/ES维护已见集合；不重置ES到from0。保留旧OutreachTargetIterator接口及其既有测试，另增cursor入口。
- Applies to: NEW keyset读取、OutreachTargetIterator新入口、两类发送候选读取。
- Violation consequence: 漏掉一万条以后的专家、空页提前停止或重复投递。
- 来源: K-outreach-raw-page-filter-contract


### Invariant I-3: 实时安全判断
- Rule: PIT只负责枚举；页过滤前严格mget最新profile，实际投递前再次按文档身份严格读取并复核scope/admission/preflight。profile不存在或身份/邮箱与准备候选不一致时记CANDIDATE_CHANGED跳过，不能静默改收件人。候选级NEW状态/绑定、退订、历史、模板校验、邮箱实时验证、账号额度、自检、cancel、attempt幂等和事务写入全部保留。材料收件人仍是contact.expertEmail。读失败是系统失败，不是无候选。
- Applies to: 两个循环发送前、NEW重试、材料提醒；BatchOutcomeReasonCodes新增受控reason。
- Violation consequence: 过期名单越过退订、重复或发错邮箱。
- 来源: K-batch-send-filter-retry-parity


### Invariant I-4: 真实计数与结束
- Rule: 03协议在所有进度与最终结果写入同一execution对象。成功/失败/跳过只统计实际处理目标；扫描丢弃数只记scanned。新OutcomeAccumulator.demand模式target=success+failure+skipped且remaining=0，不调用旧terminal剩余转skip；旧构造/旧消费者不变。候选异常stopReason=CANDIDATE_READ_FAILED，0成功时FAILED、有成功时PARTIAL_SUCCESS；额度停止不是失败，sourceExhausted仅在所有候选源真正耗尽为true。
- Applies to: progress/result写入、异常/取消路径、OutcomeAccumulator。
- Violation consequence: 虚假跳过、正常完成仍显示全库未发或读取故障被当成功。
- 来源: original


### Invariant I-5: 双循环与资源释放
- Rule: 首封及材料每条成功才消耗成功配额，skip/failed继续取下一候选；每次执行finally关闭全部cursor，取消/账号容量0/轮数到达后不再取页。材料统计可以显式遍历同一候选源做计数，但不能复用会投递/绑定/实时付费验证的执行消费者。
- Applies to: INTRODUCTION、MATERIAL_REMINDER、显式材料预估。
- Violation consequence: 材料仍全量加载、统计误发信或泄漏PIT。
- 来源: K-batch-send-round-loop-symmetry



## 现状审计

### 选人读路径与控制依赖
- ManualInitialOutreachService:943 lazy scanBaseCandidates；:959全量NEW；:970 countEsTargets；:1000旧ES页；:1065 estimatedRemaining参与quota。:1681先hasNext后轮限制，可能多扫。
- 材料:276预建全量snapshot，:2235-2320先count+allExperts分页并在10000截断；:334独立quota、:501独立sleep。不是改一个循环即可。
- NEW:1981全量联系人+邮件历史+ES查询；02新增keyset为其替代。OutreachTargetIterator已处理过滤空页，但旧offset/reset语义不能直接嫁接PIT。
- 证据账本E-05及调用点含BatchSendTaskRuntimeIntegrationTest旧桩；本步同步测试桩，不能只跑新单测。
- 诊断接口diagnoseRecipients:784和显式countBySnapshot保留，用于05统计；20000诊断上限不是执行白名单。
- X1：02稳定枚举→实时mget→安全门禁→原事务；X2：NEW更新+ES更新→后页不漏；X3：执行写入→03新旧日志；X4：同候选源→材料只读统计。

### DB/ES写入保持边界
- 结构与全量检索见证据账本E-03、“受影响存储写入路径”“E-06”。不改表/索引schema、unique约束或ES mapping。
- expert_contact：本服务创建、ConversationStateService状态transition、ManualOutreachTxHelper事务结果、SenderAccountAssignmentService绑定；这些时机/方法不迁移。材料仍要求已绑定/未重复提醒。
- mail_record与attempt：原PREPARED/SENT/失败记录、唯一性/事务辅助保留；不因换分页提前写PREPARED，不在统计路径创建记录。
- ES operatorStatus由既有投递结果写回，发现补全/晋升/同步仍按01审计路径运行；新PIT消费者重查实时事实，不假设快照等于许可。
- 安全位置：:1111-1179退订/绑定/验证；:1385-1450联系历史/验证地址/cancel/PREPARED；材料buildSnapshot原规则全部迁入逐页纯过滤，不删除其判断。
- 计数JSON共享store只写03定义的一个execution嵌套字段；没有第二个新store字段。

## 实现方案

1. 清单1/3接入02接口。新增NEW有界source与ES cursor source；NEW批次按orcid匹配当前profile，沿既有身份/人物选择决策，不用全量ES查找。NEW关联查询使用02游标加terms(本批至多500个orcid)并遍历该小集合，不能调用searchByOrcidIds:644这个size=输入数且缺body返回空的非严格入口；多个文档不能因size截断挤掉其它orcid。沿联系人顺序消费、跨源共享去重；保留同一人物后续合格成员机会，重试地址不变。profile缓冲限定当前页/当前NEW批次，只保留选择结果；仅已见ID集合随扫描增长，不能声称总内存绝对O(1)（I-2/3）。
2. 清单1首封删掉执行初始化全量计数、全量preflight；把scope/admission/template/verifiedUnavailability等纯判断作用于当前页，并在发信前复核；保持真正验证和绑定顺序。页内因确定性过滤被丢弃记scanned、不凭空变成投递skip；已进入处理再被实时护栏拦截才recordSkipped（I-1/2/3/4）。
3. 清单1材料将buildMaterialReminderSnapshot拆为可遍历候选source：按页关联已有contact、binding、suppression、hasSentMaterialReminder，真实收件地址仍contact.expertEmail。执行早停；显式预估只迭代累计，不存allExperts/targets全量、不保留10000截断、不执行投递写入（I-3/5）。
4. 清单1两循环统一额度与停止顺序：取消/轮上限→round gate→容量→取候选→发信；失败/跳过补人，轮间等待只在尚有轮预算时执行。不能为了知道“还有没有人”在最后一轮后调用hasNext。sourceExhausted未探明时保持false而不是额外扫尽（I-1/2/5）。
5. 清单2增加demand accumulator及CANDIDATE_CHANGED原因文案“候选信息已变化”；系统终止CANDIDATE_READ_FAILED写既有stopReason，不为其制造假候选。清单1全部progress/result回传execution，包括早退出和异常；计划额度按配置校验、Long计算，既有Int实际计数遇溢出风险受控失败，不静默溢出（I-3/4）。
6. 清单4-7覆盖两发送类型、两来源、页间状态变化、配额早停、异常/取消、相同邮箱、模板变更、脱敏历史fixture；旧constructor/单封调用合同回归（I-1至I-5）。

## 变更文件清单

| # | 文件（相对仓库根目录） |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt` |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchDemandExecutionTest.kt` |

## 验收标准

- I-1：每种sendType分别断言正常30封过程中countBySnapshot/countEsTargets/scanBaseCandidates/fullNEW从执行入口调用次数=0；与统计cache类依赖=0；成功30后source.nextPage、hasNext、sleep无额外调用。5失败+5跳过时继续补到成功30，不按processed30停。
- I-2：首批全被过滤，后批有合格30仍能发30；跨11001条、NEW501条、同sort/同邮箱、跨层重复覆盖；NEW关联同orcid多文档不挤掉其它orcid；内存不包含全量profile列表；批size<=500。
- I-3：候选读取后改退订/状态/绑定/地址，投递spy=0；身份变化reason明确；mget失败不变空集合；材料保留contact地址；门禁关闭不能绕过准入/退订/幂等。模板启动重验不变。
- I-4：计划30/成功30/failed5/skipped5→target40/remaining0、execution.remainingSuccessQuota0；候选读异常0成功→FAILED，10成功→PARTIAL_SUCCESS；取消不新增虚拟skip。running/final同协议，所有路径均有元数据；旧accumulator测试期望不改。
- I-5：首封/材料均覆盖oneRoundOnly、roundsPerRun=2、容量0/小于roundSize、候选耗尽、取消、所有邮箱不可达；显式材料统计11001人不在10000截断且mail/contact/attempt/SMTP写入spy均0；finally close恰好一次，后台续期结束。
- Java11：mvn test -Dtest=ManualInitialOutreachServiceTest,OutreachTargetIteratorTest,BatchSendTaskRuntimeIntegrationTest,BatchDemandExecutionTest,BatchSendExecutionDetailTest；再全量mvn test（环境依赖失败逐项记录，不伪报通过）。
- 性能断言用查询调用/读取条数，不承诺未经压测的毫秒数。极端无合格人仍可能扫全库，属正确行为而非缓存失效。SenderAccountAssignmentService等既有账号绑定库存聚合是安全配额读取，不属于本次待发专家全量枚举，不删改。

## 人工验收清单

### A-1: 足额即停与跨空页
- 前置条件: 隔离测试ES/MySQL与SMTP捕获箱；夹具11001名候选，前500名按规则不合格、后100名合格；配置每轮30、1轮、测试账号容量>=100，关闭真实外发网络。
- 操作步骤: 1. 分别运行首封与材料（材料夹具已有合格contact及binding）；2. 看捕获箱和查询记录；3. 重跑同一任务。
- 预期结果: 每次首轮成功30，过滤空页不提前终止；达到30后不继续取候选，无_count和全量初始化；重跑不对同一首封/同一材料收件人重复投递。
- 覆盖: I-1/2/3/5、X1/X2、按需需求与幂等回归。

### A-2: 跳过补人、容量与取消
- 前置条件: 测试捕获箱；候选足够，注入5个投递失败、5个进入处理后的实时skip，之后至少30名成功候选；另备容量10及暂停页读取的夹具。
- 操作步骤: 1. 运行1轮30；2. 查看详情并刷新；3. 容量10时重跑新任务；4. 在阻塞夹具中点取消后释放读取。
- 预期结果: 第1次成功30、失败5、跳过5、实际处理40、未完成配额0；第3步最多成功10；第4步取消后不新增PREPARED或SMTP，未处理候选不加入skip，PIT关闭。
- 覆盖: I-1/4/5、X3、额度/取消/事务保留。

### A-3: 发送前事实变化与系统异常
- 前置条件: 测试夹具在候选页读取后可暂停；分别准备退订、邮箱改动、绑定改动、模板启动版本变化；SMTP捕获箱。
- 操作步骤: 1. 在暂停后改变相应事实再恢复；2. 单独让下一页ES返回超时；3. 分别在0成功及已成功10封时重现。
- 预期结果: 禁止的地址/绑定不发；邮箱变化显示“候选信息已变化”；模板旧启动令牌继续被拒；ES失败不显示候选耗尽，最终分别FAILED/PARTIAL_SUCCESS。
- 覆盖: I-3/4、X1、must-NOT-change安全护栏。

### A-4: 材料全量统计只读
- 前置条件: 测试环境11001名符合材料提醒的联系人/专家，SMTP捕获箱为空。
- 操作步骤: 1. 调用显式预估接口；2. 查看统计和DB审计计数；3. 再执行只发30的任务。
- 预期结果: 全量目标11001（不是10000），统计阶段SMTP/新增mail_record/attempt/联系人绑定均0；执行阶段只发30；缓存并非前置条件。
- 覆盖: I-5、X4、只读统计。



## Global constraints

Read the master invariants I-1 through I-4 at docs/plans/2026-10-22/batch-demand-master.md. Authorized Files are exclusively the exact table in the child plan above. Preserve date-only compatibility, qualification rules, realtime send safety, old execution interpretation, and sequential interfaces. No production sends, release, migrations, schema changes, or unauthorized changes. Real SMTP prohibited; isolated tests only. Existing worktree application.yml remains committed baseline; unrelated main-worktree mail attachment timeout changes are preserved there and not imported.

Use execute-p against the exact approved child plan. Local implementation commit is explicitly authorized with subject `feat(fast-p): implement 04`. Do not run verify-p/review-p/fix-v; fast-p independent lightweight verifier follows. Exclude fast-p evidence from product commit. Any plan amendment or extra file requires PLAN_CONFLICT. Do not modify knowledge/docs outside fast-p execution report, as authorized-file boundary supersedes general documentation cleanup. Do not run builds/tests/formatters mid-flight; collect required commands after implementation, once final state is established. Smoke must exercise changed path; real converter replay for 01, real cursor containers for 02, browser surface for 03/06. Record exact commands, exits, skips and environment limits, never infer PASS.

## Baseline evidence

Java 11.0.15 Zulu and Docker server 29.8.2 observed available. Initial child 01 Maven baseline is controller-owned and its result will be recorded before dispatch. No whole-system baseline is claimed. Prior child interfaces are only those explicitly approved in their plans, not later product changes.
