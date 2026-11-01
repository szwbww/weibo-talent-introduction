# 04 — 整批补录、补齐后自动进入现有重审

状态：待执行；2026-11-01。仅计划，不表示已实施或发布。

## 需求描述

接通“补录 / 补齐并自动重审”：新作者经既有采集消费入库，旧专家安全补证后重审；用户无需再手动点击第二次。纯对照仍只留差异。

保持不变：①邮箱有效性、去重、人物登记、准入/资格/Apollo免费付费规则；②旧运维CLI的AUDIT/REUSE待确认语义；③不覆盖人工/联系保护；④原采集任务/原论文记录不回写成新结果；⑤取消不回滚已完成补档/准入。

不做：直接SQL标AUTO_PASSED、直接晋升、绕过邮箱校验、变相开启付费详情。两个子系统：补录编排、既有核验接缝。

## 关键不变量

### Invariant I-1: 单一写入口
- Rule: 新邮箱仅通过 ExpertDiscoveryService.consumeArchivedPaper→consumeOutcomeInternal；现存doc只走02的受限修复/现有重审；不走整文档覆盖，不写原sourceExecution计数。三层查询多命中或ID冲突要单列。
- Applies to: ApplyService
- Violation consequence: 批量增重、直接晋升或覆盖历史。
- 来源: K-discovery-reentry-keeps-run-accounting

### Invariant I-2: 幂等副作用收据
- Rule: TARGET在业务动作前持久化proposalHash/source refs及before doc版本；每次动作后持久化可验证docId、registration itemId、childRunId、admissionRevision、projection。ES写成功SQL未回执时仅接受同父run注册拥有的身份一致档案续接；无归属证明不得认领。重复任务同样重新查当前业务，只计新增实际增量。
- Applies to: 新建、修复、登记、子重审准备/启动、恢复
- Violation consequence: 重启重复建档/重复外呼/把其他任务新增记本任务。
- 来源: original

### Invariant I-3: 父子任务与配置
- Rule: 父ENFORCE开启时冻结已保存Apollo search/detail/revision、策略版本与evaluationAt；内部创建普通核验run及旧档案重审都使用此冻结值，不把detailEnabled=false变true。子run请求键由parentRun+排序docId分组确定；prepare成功后由服务调用start(hash,revision)，不只建READY。只有父任务当前租约有效才可派发。
- Applies to: registration内部方法、recheck内部方法、ApplyService
- Violation consequence: 任务显示完成但从未重审，或付费设置漂移。
- 来源: original

### Invariant I-4: 统计和终态
- Rule: 新增档案/已有补齐/原已完整/冲突/保护/错误按TARGET互斥记录，准入是关联verification的子统计而非新增别名。所有本任务发出的核验终态才结束REVIEW；候选同步失败单列并沿用现有syncRetry，不重新资格外呼。取消只取消本任务拥有的子run/未完成普通核验，不触碰用户独立任务。targetOutcome只允许WOULD_CREATE/WOULD_REPAIR/NEW/REPAIRED/UNCHANGED/CONFLICT/PROTECTED/SKIPPED/ERROR；它是result_json中的业务结果，不能与item.state混用。
- Applies to: 汇总、取消、恢复
- Violation consequence: 把排队当准入、子任务孤儿或错误回滚。
- 来源: original

### Invariant I-5: permit与资格解耦
- Rule: web调用的ParsedArchivedPaper只能由受lease保护原bytes+metadata+sourceHash经当前parser生成，不接受前端AuthorEmail。identity解析完整且证据唯一才可补录；qualification不完整单列，不导致已完整identity无故丢失，更不能自动推定合格。旧CLI parse维持原SOURCE_PARSE_PARTIAL约束。
- Applies to: 新factory、consumeArchivedPaper、旧CLI
- Violation consequence: 用伪造姓名绕过原文证据或意外改旧工具。
- 来源: original

## 现状审计

完整源码回执：[paper-reprocess-evidence/README.md](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/README.md)、[paper-reprocess-evidence/paths.json](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/paths.json)。共享存储全部生产读写路径及交互编号见主计划「现状审计」；本子计划只改变下述接缝，其他匹配路径为回归边界。当前本地基线 `b758eb0`，工作区存在先前改动；不得当作已核实的生产部署版本。
- `ExpertDiscoveryService.kt:1871–1882` 已有受permit限制的 consumeArchivedPaper；共享消费链先 identity→emailValidation→dedup→reserve→RAW→enrichment→activate/person（X-7），不应复制第二套实现。
- `ExpertDiscoveryService.kt:2545–2550` 对空名与新全名不相等先判IDENTITY_EXISTING_CONFLICT，因此现存空名必须交02，不能期待重喂消费自然补齐。
- `DiscoveryPaperBackfillService.kt:12–31` 的permit绑定CLI文件路径且把qualification PARTIAL也整体拒绝；网页需新受控factory，不能暴露path输入。
- `DiscoveryHistoricalReplayTool.kt:330–386` 的begin强制apolloAssist=false，recheckExisting只prepare AUDIT/REUSE，finish直接结束父任务。此工具不能直接当成网页正式任务（X-8）。
- `DiscoveryVerificationRepository.kt:737–750` cancelRecheckItem仅允许review_epoch>0；claimDue:853–880对epoch0不按task.status过滤，故需要上述本功能限定取消接缝，不能假定取消父task自然停止普通核验。
- `DiscoveryVerificationRegistrationService.kt:82–125`、`DiscoveryRecheckService.kt:64–100` 冻结设置与创建run；扩展只供服务内部，公开HTTP不得接收客户端自定义settings JSON。

## 实现方案

1. **permit（I-1/I-5）**：在 `DiscoveryPaperBackfillService.kt` 内给现有私有构造permit增加archiveLease工厂；内部保存metadata/hash/refs和重新校验callback，requireMatches仍验证作者是parser输出子集。不得接受任意路径/前端名单。CLI旧parse维持不变；新factory只将identity相关reason判为阻断，qualification覆盖不足保留到核验阶段。
2. **先归并后应用（I-1/I-2）**：`DiscoveryPaperReprocessApplyService.kt` 读取03已封口的TARGET集合。每目标复查三层唯一邮箱。不存在→每次consumeArchivedPaper仅传当前一个已明确作者，保存ConsumeOutcomeResult.failureReasons及所有写失败flag；精确查询验证本父run注册的docId再记NEW。存在且姓名/凭证缺失→进入02已有档案路径；已有完整→UNCHANGED但允许本轮现行资格重审；存在冲突→CONFLICT零覆盖。身份与邮箱门禁返回无资格邮箱时SKIPPED并显示具体原因，不算系统ERROR。
3. **新档案核验（I-1/I-3）**：registration内部增加以服务端已验证父run冻结settings初始化的入口；要求父run存在、mode=ENFORCE、版本支持。新档案继续普通reserve/activate，以父executionId为本次归属并携带原paper refs；**不再为这些新档案创建重复recheck**。待TARGET新建阶段封口后closeInput。properties验证或worker未启用时不得走legacy直接准入兜底，报VERIFICATION_UNAVAILABLE。
4. **旧档案重审（I-2/I-3）**：recheck新增内部prepareFromPaperReprocess(parentRun,orderedDocIds,verifiedPaperRefs,frozenSettings)；公开prepare DTO不新增可伪造settings权限。docIds按排序每100人一组，与源论文关联写入reviewInput.paperSourceRefs；新来源必须为本任务明确绑定此邮箱的原文。顺序prepare→等READY→start，childRunId先持久化；在READY后崩溃用同requestKey恢复start，不能再次prepare新任务。02在worker确认运行后补名，ApplyService不另写一遍。现有单用户活动名单限制导致冲突时父run维持RUNNING/REVIEW并显示reason=WAITING_RECHECK_SLOT（reason不是新state/phase），等待而非强制取消别人的任务。
5. **冻结配置（I-3）**：新增内部入口复用现有设置序列化/校验方法，不复制资格规则。冻结settings、policy/code/parser/evaluationAt写入scope_json；新普通run与child recheck读取此值；REUSE/REFRESH按照本功能选择：AUDIT仅复用原文；ENFORCE自动重审按REFRESH且严格遵从search/detail设置。配置停用/鉴权异常作为错误，不偷偷降级后宣布通过。
6. **观察与终态（I-4）**：定期从registration items/child rechecks读取终态、admissionRevision和projection；按本轮有效提交计actualAdmitted，已批准旧记录为alreadyAdmitted另列。总处理日志6步第5步链接现有8步逐人核验，不能改V161步骤范围。候选同步ERROR展示“已准入，候选同步失败”；只沿用syncRetry，不能重走Apollo。
7. **取消与重试（I-2/I-4）**：先CAS父取消，再停止新补录/新child派发；cancel已登记为本父拥有的child。**现有cancelRecheckItem要求reviewEpoch>0，不能用于普通新注册。** 在DiscoveryVerificationRepository新增仅允许已登记DISCOVERY_PAPER_REPROCESS父任务的cancelPaperReprocessOrdinaryRun：父许可→verification run→item固定锁序；封口输入，取消未提交准入的RESERVED/QUEUED/RUNNING/RETRY_WAIT项并使租约失效、结束未完成trace/attempt，保留已有admissionRevision和终态。Registration的该内部入口reserve/activate必须在同一父许可检查下提交，取消后不能迟到入队。严禁变更所有epoch0任务的默认语义。已提交准入与补名保留；重试只处理明确可重试步骤。所有side-effect前复查父run许可，dispatch与cancel竞态需补偿取消新建立的本任务child。
8. **旧CLI回归（I-5）**：原 `HistoricalBindingsRuntime.open` 不在Web调用；旧工具结果仍AUDIT/REUSE READY，不被新参数默认值变成ENFORCE。

## 变更文件清单

| 文件（仓库根目录相对路径） | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyService.kt` | 新增补录/补齐/重审编排适配器；复用业务门禁 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt` | 开放ENFORCE和补录、核验、结果汇总阶段 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperBackfillService.kt` | ParsedArchivedPaper增加受控原文字节factory；不改旧CLI行为 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` | 内部受控冻结设置入口，供新档案普通注册使用 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt` | 内部受控父任务设置+已验证来源refs入口，prepare后自动start |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` | 本功能普通核验取消及入队许可围栏；不影响其他epoch0任务 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyServiceTest.kt` | 逐人补录、零重复、策略冻结与跨阶段恢复 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperBackfillServiceTest.kt` | 旧CLI语义与新permit隔离 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckServiceTest.kt` | 受控内部入口/外部参数隔离 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt` | 新建/旧档案→准入→投影端到端 |

共 10 个文件。未列文件禁止扩改；新增问题先记录证据并修订范围。

## 验收标准

- I-1：一次同email跨3论文只1个RAW档案；第二次执行新增=0；现存空名不是直接丢为duplicate且不整文档覆盖。
- I-2：在RAW写后、子prepare后、start后、准入提交后分别注入崩溃，恢复不能新增重复run/人物/准入事件；不能把未经本任务注册的并发新档案归己。
- I-3：免费search=true/detail=false时所有子run detail=false；同批中改线上设置不改已冻结任务；prepare后实际start，子run不滞留READY。
- I-4：所有计数从持久化结果推导；两个目标准入一个待核验时actualAdmitted=1；取消仅本任务子run/ordinary item；普通核验取消后迟到reserve/activate必须被拒绝；候选同步失败不减准入不增Apollo请求。
- I-5：partial资格+完整identity允许进入验证但不凭此自动通过；旧CLI仍原约束；伪造authors/source/hash的permit一律拒绝。
- X-3/X-4/X-7/X-8：来源→RAW/person→verification→admission→candidate→查询全部串联；无手写“批准SQL”。

## 人工验收清单

### A-1: 补录后自动审核
- 前置条件：测试2名真实身份明确作者：A无档案且满足现行资格，B有空名档案但资格不足；Apollo已保存免费模式。
- 操作步骤：1. 论文页选源任务；2. 选择ENFORCE启动；3. 等核验终态；4. 查看两人流程。
- 预期结果：新增档案1、补齐已有1；A准入1、B待核验1；无需再点专家重审；detail外呼0；原任务统计不变。
- 覆盖：I-1/I-3/I-4，X-7/X-8，保持不变①④

### A-2: 重复与保护
- 前置条件：沿用A-1结果；另加人工保护和同邮箱不同姓名各1名。
- 操作步骤：1. 再跑同批；2. 查看结果；3. 运行旧CLI fixture。
- 预期结果：本轮新增0；保护与冲突均零覆盖；原CLI仍AUDIT/REUSE待确认，未自动start。
- 覆盖：I-1/I-2/I-5，保持不变②③

### A-3: 崩溃、取消与同步错误
- 前置条件：故障环境设置准入成功但候选同步失败；另有用户独立重审在跑。
- 操作步骤：1. 看失败详情；2. 取消本任务；3. 重试同步；4. 刷新独立重审。
- 预期结果：已准入数保留1；同步重试不增加Apollo请求；独立重审不被取消；已落档案不回滚。
- 覆盖：I-2/I-4，X-4/X-8，保持不变⑤


人工验收开始时才从此处导出同前缀 `-acceptance.md`；本次不生成衍生勾选文件。
