# 05 · 深度发现新档案接入及启动配置快照

日期2026-10-18；待实施。依赖按[总计划](apollo-verification-master.md)顺序。规范附录：[contracts](apollo-contracts.md)、[代码审计](apollo-code-audit.md)。实施前核对固定HEAD差异。

## 需求描述

O-1：手动、关键词、定时、连续窗口的新发现档案进入同一核验账本；选择Apollo与否可追溯，崩溃可恢复。保持N-1七个平台/学术查询/游标策略不变，N-2旧档案重复、SBIR/联系保护不自动重新纳管，N-3原任务暂停/其他任务并存行为。此片可通过既有启动API+新账本SQL验收。

## 关键不变量

### Invariant I-1: 配置先冻结
- Rule：PaperSearchCriteria仅新增apolloAssist:Boolean?=null；请求解析时null用服务器apolloDefault并冻结成true/false，run.settings另存mode/policy/limits。关键词API/无body/定时均走同默认归一；连续跨窗口用持久criteria，不从当前UI读。
- Applies to：controller/scheduler/pipeline。
- Violation consequence：不同入口漏选项。
- 来源：总I-3/I-11。

### Invariant I-2: 先意向再RAW
- Rule：两个RAW收录分支及consumeQueuedItem共同调用registration；失败禁止走旧自动准入。new managed profile对已有admit入口只生成NEEDS_REVIEW待worker，direct判断也由worker统一，不重复抢跑。所有phase均gate可见。
- Applies to：ExpertDiscoveryService、RegistrationService。
- Violation consequence：漏核验/半写错误放行。
- 来源：总I-6/I-7。

### Invariant I-3: 重复不变新档案
- Rule：本轮去重命中只能恢复已有RESERVED且doc/hash/来源键相符的意向，不能把历史重复纳入；跨run不迁移owner。登记批次按该run第一次激活序列每100档案一批，与旧论文批次分开。
- Applies to：登记/replay/duplicates。
- Violation consequence：分母虚增/补历史。
- 来源：总I-5/I-8。

### Invariant I-4: 选项变化不重置学术查询
- Rule：Apollo开关不写入canonicalCriteria hash；同pipeline有活跃/积压且apolloAssist变更必须在repository.launch事务内409。完成或无活跃时可更新下一次启动快照；source cursor不重置。暂停只停采集，已登记核验继续。
- Applies to：pipeline/repository/scheduler。
- Violation consequence：重复抓取或用户选择失效。
- 来源：总I-9/I-11。



## 现状审计

ExpertDiscoveryService:1450/1779写RAW，:1456/1790统一admit，:2286重复处理；缺名:2270在此前丢弃，不计新核验分母。Pipeline.processJob:1074调用consumeQueuedItem只传executionId，需显式传冻结选项上下文，不能ThreadLocal跨线程。Controller:102/179两手动入口，scheduler:395定时criteria，pipeline:314序列化criteria。新run创建使用正executionId，不能临时负token。V133及所有读写路径见audit§4；旧游标不加字段。IP-1/IP-6。

## 实现方案

1. **启动快照（I-1/I-4）**：PaperSearchCriteria一个可空字段；普通/run从criteria JSON取值；/run/by-keyword在现有参数末尾新增`@RequestParam(required=false) apolloAssist:Boolean?=null`并写入criteria。controller归一/检查capability，不允许显式true被静默丢弃；scheduler用同registration.resolveOptions默认值。pipeline将归一criteria保存现有JSON；Repository.launch在原事务中比较apolloAssist，不新增queryHash字段；在已列DiscoveryPipelineService/Repository文件内扩展Conflict原因及控制层映射，返回HTTP409和code=VERIFICATION_OPTIONS_CONFLICT，保持原QUERY_CONFLICT路径。旧JSON无字段按该run已保存模式或历史OFF处理，不用今天配置重解释历史。
2. **run绑定（I-1/I-2）**：同步discover拿正execId后createRun，连续窗口绑定正execution后同样createRun，把冻结context作为普通不可变参数传到consumeQueuedItem/consumeOutcome/ORCID；不读全局“最近一次execution”。没有正ID时enabled新流程直接报错并不收录新RAW。window/run关闭finally closeInput，失败关闭也保留已登记任务。
3. **预登记（I-2/I-3）**：新RegistrationService核验新来源与保护、算实际docId/identityHash、reserve→原RAWwrite→activate；原enrichment enqueue/person registration保持原顺序契约，任何登记失败进入可重试的消费错误且不推进页checkpoint；原source统计保留含义，新增登记错误码供采集日志。不得把旧filtered计数当新pending。
4. **断点恢复（I-2/I-3）**：同RegistrationService提供恢复RESERVED方法供04worker调用（本片可给worker注入接口已有默认空实现，避免修改未列worker：01定义RegistrationRecovery接口，05实现，04用ObjectProvider读取）；按docId精确RAW GET、identity比对激活，缺失不构造RAW。重复分支发现本feature意向则激活，不重算当前run新增数。输入关闭/来源终结未写RAW项进入登记异常。
5. **批次（I-3）**：run行锁内分配首次激活序号，batch_number按`floor(activatedCountBefore/100)+1`；为不新增列，在run锁下COUNT activated项并分配。RESERVED阶段batch_number=0，激活时只设一次；不依赖并行来源batchNumber或论文数。若某次新激活先提交，按真实激活顺序归批；重试不换批。
6. **回归（I-1～I-4）**：手动/keyword/scheduled/continuous四种上下文，每个断点重试、暂停resume选项不丢；保留核心query hash输出与之前完全一致。测试直接调用API，不要求07先完成。

## 变更文件清单

共13个文件；原10个文件加3个现有调用方测试的执行上下文契约迁移授权，作为总计划每片≤10的测试专项例外；生产文件范围不变，无清单外授权。

|#|文件|动作|
|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperSearchCriteria.kt`|修改新增一个JSON属性|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt`|修改|
|3|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt`|修改|
|4|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`|修改|
|5|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt`|修改|
|6|`src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt`|修改launch事务选项校验|
|7|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt`|新增|
|8|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt`|新增|
|9|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt`|修改|
|10|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`|修改|
|11|`src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerMvcTest.kt`|修改真实execution/context调用契约及相关断言|
|12|`src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt`|修改真实execution/context调用契约及相关断言|
|13|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt`|修改真实execution/context调用契约及相关断言|

## 验收标准

I-1：四入口冻结、旧JSON/默认/显式false、正ID；I-2：reserve失败零RAW，RAW成功activate前宕机恢复1项，不推进checkpoint；I-3：同源跨源重复、历史重复不计新批、101条→100/1，重放不增；I-4：开关变化409、查询hash不变、原暂停/恢复及cron回归。运行新增IntegrationTest及已有ControllerMvc、Controller、Scheduler、Pipeline、ExpertDiscoveryService测试。

## 人工验收清单

### A-1: 四启动入口
- 前置条件：测试HTTP与模拟数据源每轮只返回不同的1个合成新档案；enabled=true。
- 操作步骤：分别POST普通发现、关键词发现、触发定时入口、连续模式启动；查询各自run。
- 预期结果：4个正执行ID各1个run；每条item归属真实执行；apolloAssist显式false的run无Apollo请求；其他按默认true。
- 覆盖：O-1，I-1/I-2，IP-1/IP-6

### A-2: RAW后崩溃恢复
- 前置条件：测试fixture在RAW成功后activate前故障，重启；另放1条没有本feature意向的历史重复。
- 操作步骤：重新消费同来源记录；查看RAW及item数。
- 预期结果：新档案激活1条，归属原执行，历史重复不进入账本；原学术补全/人物登记仍完成；姓名缺失依旧留采集历史失败，不冒充已被修好。
- 覆盖：N-1/N-2，I-2/I-3，IP-1

### A-3: 暂停和选项冲突
- 前置条件：连续pipeline已有积压，启动apolloAssist=true。
- 操作步骤：再次以false启动；暂停并恢复；同时打开另一种允许并存的任务。
- 预期结果：变更请求409 VERIFICATION_OPTIONS_CONFLICT；原游标未重置；resume仍true；暂停文案说明已入队核验可继续；其他任务不受影响。
- 覆盖：N-1/N-3，I-4，IP-6

### A-4: 保护
- 前置条件：来源返回SBIR/有联系人/已在APPLICATION各1条以及正常新档案1条。
- 操作步骤：运行接入；看账本与原业务快照。
- 预期结果：仅正常新档案激活进入新资格流程；保护项不覆盖旧字段/准入；不是把存量全库装进本轮。
- 覆盖：N-2，I-3

## 修正记录
- 用户“批准该计划并执行”批准 C05-EXECUTION-CALLERS：增加上述三个现有调用方测试，仅用于显式真实execution/context调用迁移及相关回归；文件上限10→13为测试专项例外，生产代码范围不变，不保留旧接口兼容壳或放松执行绑定。授权及Before/After身份见fast-p ledger Amendments。
