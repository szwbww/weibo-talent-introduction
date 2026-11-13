# 04 · 异步核验、事务准入及投影恢复

日期2026-10-18；待实施。依赖按[总计划](apollo-verification-master.md)顺序。规范附录：[contracts](apollo-contracts.md)、[代码审计](apollo-code-audit.md)。实施前核对固定HEAD差异。

## 需求描述

O-1：已登记档案可靠完成证据读取/直接或Apollo判定，并可在进程重启后继续；准入及投影错误可追查。保持N-1原采集执行状态和当前进度槽不被改写，N-2人工保护、旧准入selector和发信动作不变。尚不接入实际发现；默认worker关闭。

## 关键不变量

### Invariant I-1: 有限请求，状态准确
- Rule：调度02clients和01lease；先尝试可靠已有证据/绑定ORCID，足够直接通过则不调用Apollo；缺证且开关开才enteredApollo。预算、重试、错误和分页按C-3，成功HTTP不自动意味着本人匹配。
- Applies to：worker、attempt记录。
- Violation consequence：无限调用或错误假装无匹配。
- 来源：总I-1/I-9/I-12。

### Invariant I-2: 原子证据与准入
- Rule：新独立commit bean开启事务，固定锁序并重读admission+身份快照；证据持久化与review.recordAutomatic在同事务。AUDIT只能保存建议不能调用准入/投影。SQL成功才计本次自动准入。
- Applies to：commit service。
- Violation consequence：账本与准入分叉。
- 来源：总I-4/I-6。

### Invariant I-3: 投影与主结果分开恢复
- Rule：提交后事务外读当前RAW并调用共享projectApprovedCandidate；明确枚举判断，NOT_ADMITTED不算成功。PENDING/ERROR投影重试无需再次查Apollo；人工后续改决定则停止投影并展示SUPERSEDED_BY_REVIEW。
- Applies to：projection/recovery。
- Violation consequence：重试重复准入/覆盖人。
- 来源：总I-4/I-5。

### Invariant I-4: 取消与保护竞态
- Rule：采集pause不撤核验队列；workerEnabled关闭停止领取。提交前保护读失败不放行，旧token不能写，源identity变化PENDING，人工决定优先。
- Applies to：scheduler、worker、commit。
- Violation consequence：晚回包覆盖新任务或人工。
- 来源：总I-5/I-6/I-11。



## 现状审计

既有ExpertAcademicEnrichmentWorker是独立OpenAlex队列消费者；本片不改它。Review.recordAutomaticAgainst:874已有sameIdentity+manual/legacy保护及CAS；projectApprovedCandidate:1014的ok范围包含NOT_ADMITTED，因此新worker必须按result枚举判。审计IP-1～IP-4、IP-6均跨此服务；读取联系人/别名/API和APPLICATION只为保护，禁止写mail/contact。

## 实现方案

1. **worker（I-1/I-4）**：通过`ObjectProvider<RegistrationRecovery>`执行已存在恢复实现（无实现时跳过）；独立`@Scheduled(fixedDelayString=...)`只领取有限批次（每轮10、并发1为首版），业务线程不阻塞discovery PDF consumer。使用专用Clock和HTTP client，不复用可带其他服务认证的RestTemplate；守住workerEnabled。每个HTTP请求前领取permit、记录request_key，每页完成持久化候选并集和游标，再安排下一次；重启读取同attempt结果，不把查询从第一页无限重跑。
2. **提交服务（I-2/I-4）**：先在事务外读取新RAW/三层和联系保护，校验factFingerprint；事务中锁run/admission/item，检查lease和expectedRevision，保存结构化evidence，使03gate可在同事务读到；调用Review.recordAutomatic并确认返回的真实持久row/revision与expectedAutomatic一致，再标outcome。禁止依赖Review.insertOutcome的revision=0 fallback把“没有读到行”当成功；新接缝需明确行已存在。保护变化→PROTECTED，identity变化→PENDING，保留理由；不可写人工review_item。
3. **事务外候选同步（I-3）**：只有ENFORCE且AUTO_PASSED事务已提交才同步；同步前后复查current admission的revision/identity；允许PROJECTED/ALREADY_PRESENT/APPLICATION_PRESENT三个可用结果，其他状态单列。现存候选/应用源不覆盖；源缺失/ES读失败重试投影，不重新核验；计数auto是本次提交的事件，candidateCreated只在PROJECTED时计，后续人工改变不改历史事件。
4. **恢复（I-1～I-4）**：RESERVED恢复见05；本片恢复超期RUNNING/RETRY_WAIT与投影PENDING/ERROR；错误达到上限保留ERROR给人工，不新增“重试全部”写API。重启恢复不创建新的execution。新增验证用端到端fixture服务在test包，不生产公开。
5. **与review事务接缝（I-2）**：必要时在列明的DiscoveryReviewService增加`recordAutomaticPersisted`返回实际行，不改变旧recordAutomatic对旧调用者的语义；强制真实DB确认，失败抛异常回滚。新接缝必须只调用现有CAS/保护逻辑，不能第二份UPDATE。

## 变更文件清单

共7个文件；一个发现核验子系统（与既有准入/前端的交互按文中说明）；无清单外授权。

|#|文件|动作|
|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`|新增|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt`|新增|
|3|`src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`|修改实现worker持久方法|
|4|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt`|修改持久结果确认接缝|
|5|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt`|新增|
|6|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitServiceTest.kt`|新增|
|7|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt`|新增隔离MySQL/HTTP fixture驱动|

## 验收标准

I-1：完整/截断分页、429/403/5xx、请求预算、零候选；I-2：SQL事务故障证据与admission一起回滚，AUDIT零业务写入，revision=0假成功禁止；I-3：ES500→auto=1/projectionErrors=1→恢复auto仍1，ALREADY_PRESENT不是新建；I-4：lease过期、暂停、身份/人工竞态及保护读失败。FlowIT必须以真实MySQL事务+Mock HTTP验证，不仅mock repository。

## 人工验收清单

### A-1: 采集结束后核验完成
- 前置条件：FlowIT提供run2001 input_closed=true、item1QUEUED，HTTP先429后200；原task为SUCCESS。
- 操作步骤：启动worker、查看DB/06 API（若06未部署用SELECT）；重启再观察。
- 预期结果：原task仍SUCCESS且不出现新的task记录；核验由RETRY_WAIT到结果；总档案1，Apollo实际请求2，已查询档案1。
- 覆盖：O-1，N-1，I-1/I-4，IP-1/IP-6

### A-2: 准入已写但ES失败
- 前置条件：1条可准入ENFORCE，测试ES首次500随后成功；记录Apollo请求数。
- 操作步骤：运行一次；读取admission与projection；恢复ES并等待投影恢复。
- 预期结果：首次AUTO_PASSED且projection=ERROR；之后PROJECTED，auto一直1，Apollo请求数不增加，未发邮件。
- 覆盖：N-2，I-2/I-3，IP-2

### A-3: 人工先于回包
- 前置条件：worker已请求但测试HTTP阻塞；后台将同身份档案HOLD并提交。
- 操作步骤：释放HTTP回包；查看决定、账本及候选。
- 预期结果：HOLD保持；本次不计自动准入，结果保护跳过；旧worker不新增候选。对AUTO已提交后HOLD的变体，历史自动计数保留且当前决定显示HOLD。
- 覆盖：I-4，IP-4

### A-4: 影子模式
- 前置条件：相同合成输入分别在AUDIT和ENFORCE run；AUDIT先运行。
- 操作步骤：运行AUDIT并比较原准入/候选快照。
- 预期结果：AUDIT建议有值，autoAdmitted=0；原准入与候选不变；不会自动把AUDIT转ENFORCE。
- 覆盖：N-2，I-2
