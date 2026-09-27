# 09d：深度发现候选准入与补全复评

状态：计划追加，尚未实施。2026-09-27；create-p；归属 [09](discovery-repair-09-subject-scope.md)。

## 需求描述

统一深度发现记录的自动候选准入：身份和邮箱合格先收 RAW，专业及研发证据明确后才进 CANDIDATE；后续明确不符合的 discovery 候选仅撤下候选副本，保留 RAW/联系历史。前置：09c。

保持：邮箱和身份验证、真实 _id、重复不覆盖、来源暂停/预算/检查点、补全租约重试、APPLICATION 与 MySQL 联系/邮件数据、批量发送配置。范围外：历史全库批量清洗、自动删这75条、人工强制层级操作改造、改首发实现/类型勾选、新建调度/队列/索引。

## 关键不变量

### Invariant I-1：一处专业准入，覆盖自动入口
- Rule：CandidateEligibilityService.evaluateEligibility 保留原基础检查；仅对 DiscoveryIdentity.isDiscovery(profile) 现算 classify。只有 PRODUCTION_RND/ACADEMIC_RND/HYBRID_RND 才通过新增专业资格；UNKNOWN→RND_SCOPE_UNCONFIRMED（无IDs）或 RND_EVIDENCE_INSUFFICIENT（有目标IDs但分数不足）；OUT_OF_SCOPE/SERVICE_ONLY→明确拒绝原因。不得只读陈旧 profile.expertClassification，也不得把 promotionGateEnabled=false 当成跳过本次 discovery 专业资格的开关。非 discovery 历史记录规则不变。
- Applies to：发现 ORCID/论文/队列消费、RAW 自动晋升、RAW-only复评、候选重新验证。
- Violation consequence：直接晋升绕过统一规则，或未知混同范围外。
- 来源：DD-24；K-identity-cache-version-and-admission。

### Invariant I-2：身份合格≠立即候选
- Rule：既有合法身份邮箱仍写 RAW 并入既有补全任务；首次专业未知时 filterResult=REJECTED、filterRejectReason=RND_SCOPE_UNCONFIRMED、candidate新增0，正常推进已完成页，不能因此无限重试论文。补全后的准入检查通过才晋升；计数 promoted 只统计真实候选写成功。toIndexMap 必须带上同输入现算分类，使记录可见UNKNOWN/拒绝原因。
- Applies to：两个直接发现分支、consumeOutcomeInternal、enqueueEnrichmentJob、toIndexMap。
- Violation consequence：先候选后补全形成漏洞，或把待专业确认算系统故障。
- 来源：用户确认的专业目标。

### Invariant I-3：补全和资格复评完成后才报成功
- Rule：enrichProfiles 中三层事实成功写回后，针对 discovery 调统一的 ExpertRevalidationService 定向方法，重新读取真实RAW、现算资格并写回 RAW 的 filterResult/filterRejectReason/分类。允许时已有候选不整文档覆盖；RAW-only 按真实_id create 晋升；拒绝/未确认时如候选存在，仅撤下候选副本。失败/取消/身份CAS no-op 不得导致专家任务SUCCEEDED；沿既有 RetryableError/Partial 和重试机制报告。先做复评，再由 processClaimedEnrichmentJobBatch.complete 写终态；同步人工 enrichProfiles 路径同样覆盖。
- Applies to：自动worker、DISCOVERY_PENDING、人工逐批补全、后续重试；revalidateCandidates 对 discovery 调同一方法。
- Violation consequence：补全成功但资格仍旧，或写失败烧掉重试机会。
- 来源：DD-25；K-enrichment-write-three-layers。

### Invariant I-4：撤下候选是可恢复、可并发校验的副本操作
- Rule：ExpertIndexWriterService 在现有文件新增窄 helper：先确认RAW完整可读且仍为同一身份，候选同一_id/身份；GET取得 _seq_no/_primary_term，局部写RAW资格与DELETE候选均带乐观并发条件。若事实/身份变化、409或读取/写入错误，返回重试；DELETE404表示已经不存在，是幂等成功。禁止 delete-by-query、删除RAW/APPLICATION、改MySQL联系/邮件/运营状态。缺RAW时不删候选，先报告失败。人工强制层级转换保持既有操作行为，不声称本片管控了所有人工override。
- Applies to：补全定向复评、候选重新验证；已有通用删除函数供其他调用方使用，语义不改。
- Violation consequence：删掉唯一档案、覆盖并发人工操作或误删联系人历史。
- 来源：真实_id与局部更新约束。

### Invariant I-5：不恢复隐藏首发门禁
- Rule：不改发信入口、不加sendable/版本拦截/隐藏黑名单；发送按现有可见类型勾选。专业资格是此次用户明确授权的自动候选规则，原因使用已存在的filterResult/filterRejectReason及分类字段展示。不能承诺撤候选会撤销运行中的已快照发送任务；上线验证须在用户控制的停发窗口进行，不由执行agent停发/上线。
- Applies to：所有任务、验收和交付报告。
- Violation consequence：违背用户此前禁止隐藏发信逻辑的要求。
- 来源：会话约束。

## 现状审计

以 [09 共享审计](discovery-repair-09-scope-audit.md)为本节组成部分，包含实际 mapping、DB 约束、全部读写入口、X9-1～X9-6 交互及源文件检索回执。行号为 2026-09-27 审计基线；执行按方法名重新定位，基于前置子计划完成后的代码，不覆盖 01～08 改动。

目前直接晋升 promoteDiscoveredToCandidate 不经过 ExpertRevalidationService 的可选分类门禁；revalidateCandidates只看邮箱和基础资格；enrichProfiles写三层后不调候选复评；processClaimedEnrichmentJobBatch先complete后RAW-only复评会吞复评失败。旧 backfillRawEmailsAndPromote 明确跳过 discovery，不需要改成另一套专业判断。只改变自动 discovery 准入，人工 promoteToCandidate/层级转换与历史非 discovery 不纳入本片自动规则。

## 实现方案

1. **资格（I-1/I-2）**：CandidateEligibilityService.kt 注入/复用 ExpertClassificationService；保持旧构造测试可适配。ExpertDiscoveryService.kt 的两个发现入口和共享消费者继续复用 evaluateEligibility，toIndexMap写同输入分类，正常收RAW及入队。既有filtered计数记录明确专业原因，08原展示直接消费原因码；不新增统计表。
2. **集中复评（I-3/I-4）**：ExpertRevalidationService.kt 新增定向 discovery 复评方法，返回明确允许/拒绝/待确认/已存在/可重试失败的内存结果（不新增持久化状态）。自动RAW晋升、RAW-only复评及候选重新验证的 discovery 分支复用；非 discovery 分支保持。ExpertIndexWriterService.kt 提供RAW资格局部更新、候选条件移除；按真实_id保留其他字段。允许RAW-only时使用既有create写，已有候选不通过普通PUT覆盖运营状态。
3. **所有补全入口（I-3）**：ExpertDiscoveryService.kt 在 enrichProfiles 成功写回后调用复评，并把复评失败变为可重试结果；processClaimedEnrichmentJobBatch 不再在complete成功后单独吞掉复评错误。需要计数时只扩展本文件现有内存 outcome 的默认字段记录 promotion/revalidation，不增加数据库或ES字段。CAS写回no-op必须核对最新RAW再决定，不将no-op当本次旧画像可用于删候选。
4. **回归与边界（I-1～I-5）**：修改清单中的 CandidateEligibilityServiceTest、ExpertRevalidationServiceBehaviorTest、ExpertIndexWriterServiceTest、ExpertDiscoveryServiceTest。09c资源只读复用；验证无身份→不生成、邮箱无效→RAW0、合法身份专业未知→RAW1候选0且入队、成功补齐工程科研证据→候选1、兽医/艺术非目标→候选0、补全后变非目标→只删候选、无RAW→不删、409/ES500→重试、404→幂等成功、旧token/取消→不写任务成功、application/contacts/mail保持、非discovery和人工override不变。
5. **既有测试预期衔接**：01～08完成记录不重写成失败；其后 final consumer 测试中“姓名解析成功立即候选”的预期，在09d更新为先RAW候选0，再用明确标注的模拟可信补全验证候选1。原始具名解析结果/人数真值不得改；不能给缺作者ID的真实论文凭空加ID。只有可信姓名邮箱但无可信OpenAlex/ORCID可补全的记录会留RAW（NO_ID/UNKNOWN），不承诺自动晋升。
6. **守卫/产物**：OperatorStatusWriteSeamGuardTest.kt 仅机械移动 Writer 新增函数导致的行号，旧片段及白名单不可扩大。测试输出09d.json，包含前后层计数、RAW资格原因、job终态/重试、候选冲突处理、保留字段快照；不执行线上修数据脚本。

## 变更文件清单

共 9 个文件；清单外改动须先修订本计划。

| 文件 | 类别 |
| --- | --- |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityService.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | 生产 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityServiceTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 测试 |

## 验收标准

- I-1：ORCID/论文/队列、自动RAW晋升、候选复评均经同一discovery资格逻辑；旧promotionGateEnabled true/false均不能让专业未知的discovery自动晋升。
- I-2：专业待确认导致正常filtered且页完成，不产生系统重试循环；补全队列仍按_id入队；promoted与真实create成功一致。
- I-3：资格写/撤候选失败不产生SUCCEEDED；成功重试可幂等收敛；人工补全与worker同样行为；不增加网络补全调用。
- I-4：404/409/500、RAW缺失、身份改变、application存在、旧任务并发均覆盖；RAW/申请/运营/邮件数据不变；不出现delete-by-query。
- I-5：发送生产文件/配置diff=0、ExpertClassificationVersionGateGuardTest通过；无部署/生产清洗。
- 定向测试：四个修改测试类+OperatorStatusWriteSeamGuardTest；只读运行 CandidateEligibilityServiceEnhancedTest、ExpertRevalidationServiceTest、ExpertAcademicEnrichmentJobServiceTest、ExpertAcademicEnrichmentWorkerTest。若产生确需改动的第十/第十一文件，先修订清单/拆分，不擅自改范围。
- 09全部完成后JDK11 mvn clean package一次；记录实际失败/跳过，不沿用01～08旧统计。

## 人工验收清单

### A-1：新增到补全再到候选
- 前置条件：隔离验收准备合法身份邮箱、无专业字段记录；其可信作者响应含工程field22、lastPublicationYear=2026、大学机构，且无临床词。
- 操作步骤：1. 运行发现；2. 查看RAW和候选数量；3. 运行既有补全入口；4. 查看09d.json。
- 预期结果：首次RAW1、候选0、RND_SCOPE_UNCONFIRMED、补全任务1；补全后RAW1候选1、PASSED、ACADEMIC_RND；姓名邮箱不变。
- 覆盖：需求1、I-1/I-2/I-3、X9-3/X9-4。

### A-2：范围外与故障重试
- 前置条件：隔离验收中复制Gebeyehu既有候选/RAW，以及明确标注的非目标结构化响应；另准备DELETE409/500/404、无RAW和身份变化场景。
- 操作步骤：1. 对各样本运行补全/复评；2. 查看层计数、原因和任务结果；3. 对可重试失败恢复服务后重试。
- 预期结果：正常范围外只剩RAW，候选0，原因明确；409/500不报SUCCEEDED，恢复后收敛；404幂等成功；无RAW/身份变化不删；未知保留UNKNOWN原因而不冒充范围外。
- 覆盖：需求2、I-3/I-4、X9-4/X9-5。

### A-3：历史与发送配置保护
- 前置条件：隔离样本另有APPLICATION、联系人和历史邮件；保存原批量类型勾选，准备非discovery旧导入、人工层级操作及解析具名夹具。
- 操作步骤：1. 运行专业复评与候选查询；2. 比较各层/联系人/邮件与配置快照；3. 查看原parser姓名邮箱结果；4. 运行取消/旧租约回归。
- 预期结果：APPLICATION/联系人/邮件/运营状态内容不变；配置勾选不变，未新增发送门禁；非discovery/人工操作维持原语义；原文身份真值不变；取消和旧租约不记成功。
- 覆盖：全部保持项、I-1～I-5、X9-5/X9-6。

人工验收时再从本节导出同名 `-acceptance.md`；本次不生成通过记录。测试与离线验收输出不得访问生产或发送邮件。
