# 03 · 新批次统一资格门禁与重验一致性

日期2026-10-18；待实施。依赖按[总计划](apollo-verification-master.md)顺序。规范附录：[contracts](apollo-contracts.md)、[代码审计](apollo-code-audit.md)。实施前核对固定HEAD差异。

## 需求描述

O-1：新ENFORCE档案统一使用证据门禁；补全、重验、人工撤销后重算和审核列表不再互相矛盾。保持N-1无账本/AUDIT/非发现旧行为，N-2人工/legacy优先，N-3不改邮件selector或ES结构。此片仍没有生产登记者。

## 关键不变量

### Invariant I-1: 读上下文有管理态
- Rule：同docId任一ENFORCE账本代表已纳管，不能在identity变了/证据没完成时当UNMANAGED；原始DB异常必须失败，不准fallback旧自动。批量500一次读取。
- Applies to：gate、policy、scan。
- Violation consequence：绕过资格门槛。
- 来源：总I-3/I-10/I-13。

### Invariant I-2: 矩阵覆盖替代旧学历年龄
- Rule：纳管档案先保留其他硬阻断，再以02资格结果替代NO_DOCTORAL_DEGREE/AGE_EXCEEDED；未核验待核验；有效Apollo未知可准入。旧policyVersion不全局升级，新版仅纳管结果返回。
- Applies to：policy、revalidation。
- Violation consequence：Apollo后被旧逻辑撤回。
- 来源：总I-1/I-2/I-13。

### Invariant I-3: 读写使用真实docId与同一事实
- Rule：recordAutomatic(docId,...)显式传真实docId/context；scan按hit._id；revalidation按RAW真实_id。所有policy调用（含撤销/initialize/refresh/recovery）可解析同一gate；不以profile.orcidId冒充实际_id。
- Applies to：review、scan、revalidation、gate。
- Violation consequence：查错证据或N+1。
- 来源：总I-6/I-13。

### Invariant I-4: 保护与非发现回归
- Rule：同身份manual/legacy原优先级不变，HOLD/REJECT不自动晋升；候选投影仍共享writer。不改identityVerification来换通过。
- Applies to：所有gate调用者。
- Violation consequence：覆盖人工与身份污染。
- 来源：总I-5/I-12。



## 现状审计

audit§1/2列全部自动写入及reader。ReviewService:844/855/874负责自动CAS，但:800撤销、:1197初始化、:1335刷新也直接policy.evaluate。ReviewScan:244读取理由；Revalidation:363旧hardBlockingReasons与:367currentPolicy并存，必须在任何reconcile前合成统一纳管结果。Writer.discoveryProfile已有institutionEvidence/filterResult；无需再改Writer。发送BatchRecipientSelection仍只读原admission，可用新的AUTO_PASSED，不增枚举。IP-2/IP-3/IP-4。

## 实现方案

1. **独立gate（I-1/I-2/I-3）**：DiscoveryVerificationAdmissionGate只依赖01repository和02纯rules，不依赖DiscoveryReviewService，避免policy→gate→review循环。提供findContext(docId,profile)、findContexts(keys)、evaluateManaged(baseAutomatic,evidenceContext)。输入上下文区分“未加载”与“已加载但无账本”，支持批量加载后纯计算。managed但证据未完成返回NEEDS_REVIEW/VERIFICATION_PENDING；证据不匹配FACT_CHANGED/IDENTITY_CHANGED。
2. **policy入口（I-1～I-4）**：保留原evaluate签名给旧调用者；增加显式docId/context重载；现有入口只在能获得真实esDocId时读gate，缺真实ID的纳管调用必须由上层补齐而不是猜ORCID。基于纯旧结论调用overlay，不重复构建有副作用的新profile。保留6类soft和其他配置硬否决；新学历/年龄资格不靠旧rejectCodes推断“确定不符合”。
3. **review/readers（I-3/I-4）**：recordAutomatic和撤销/initialize/refresh传入实际docId；ScanService一次findContexts并逐人计算。initialize遇旧无账本保持旧策略，不自动拉Apollo；GET审核列表不触发网络。输入ledger evidence已COMPLETED或处于本事务可用阶段，未提交的其他事务不可见。
4. **重验（I-2/I-4）**：在reconcile前选择managed/nonmanaged分支。managed分支使用gate的当前硬阻断；通过走projectAdmission；未完成或缺资格NEEDS_REVIEW不得新建候选。已有效Apollo未明确不因旧NO_DOCTORAL/AGE_EXCEEDED删除；真实新增硬阻断仍按当前policy处理，人工路径先返回。不得修改非发现分支或通用CandidateEligibilityService。
5. **测试（I-1～I-4）**：用测试helper建ENFORCE/AUDIT/无账本，断言policy、自动存储、列表reasons、refresh、revalidation结果一致；500条列表证据读取1次；DB异常不转无账本。

## 变更文件清单

共9个文件；一个发现核验子系统（与既有准入/前端的交互按文中说明）；无清单外授权。

|#|文件|动作|
|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGate.kt`|新增|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt`|修改|
|3|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt`|修改|
|4|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewScanService.kt`|修改|
|5|`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt`|修改|
|6|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGateTest.kt`|新增|
|7|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewServiceTest.kt`|修改|
|8|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt`|修改|
|9|`src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt`|修改|

## 验收标准

I-1：缺失/损坏/identity变更/DB失败/500批读断言；I-2：总矩阵全覆盖并跑DiscoveryAdmissionPolicyTest原回归；I-3：真实_id≠orcidId的fixture在review/scan/revalidation三入口同结论；I-4：人工四态及legacy、AUDIT旧路径保持快照；跑既有Writer和BatchRecipientSelection相关测试（不改其文件）。需断言candidate删除HTTP未发生于APOLLO_UNCLEAR。

## 人工验收清单

### A-1: 准入后重验不撤回
- 前置条件：测试helper提供2个有有效ENFORCE证据档案，DIRECT_CLEAR及APOLLO_UNCLEAR；已有候选；旧degree/age为空。
- 操作步骤：执行原候选重验→原审核列表→刷新自动结论。
- 预期结果：两档案仍AUTO_PASSED，候选仍在；明确性分别已明确/未明确；无学历缺失误删。
- 覆盖：O-1，I-1/I-2/I-3，IP-2/IP-3

### A-2: 人工和旧路径
- 前置条件：各准备HOLD/REJECTED/MANUAL_APPROVED/LEGACY_APPROVED、无账本旧档案、AUDIT档案，记录原结论。
- 操作步骤：运行相同重验；人工撤销其中一个已批条目，再查看自动结果。
- 预期结果：保护原决定不被自动覆盖；旧/AUDIT与改前一致；撤销后的纳管档案按新证据返回对应自动结论。
- 覆盖：N-1/N-2/N-3，I-4，IP-3/IP-4

### A-3: 身份变更和故障
- 前置条件：将一个纳管档案邮箱改为另一合成地址，另一次关闭测试DB。
- 操作步骤：执行重验/审核查询。
- 预期结果：身份变更不能复用旧Apollo准入；DB故障返回明确失败，没有新候选写入，没有把该档案当旧未纳管放行。
- 覆盖：I-1/I-3，IP-4
