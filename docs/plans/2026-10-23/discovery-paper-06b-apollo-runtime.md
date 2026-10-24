# 06b — Apollo 运行快照、权限分流与分渠道日志

状态：待执行。依赖06a持久配置；本切片接入运行与报表，必须同时更新已核对的旧构造器/迁移测试。

## 需求描述

分别保存“免费搜索”和“允许付费详情”两个设置，刷新、重启后保留；所有新手动/定时/队列任务读取保存值，正在运行的任务继续使用自己的快照。日志返回本次实际开关及两类已登记请求尝试数。

必须保持：当前直接准入/教授/SELF/Apollo明确与未明确规则、身份门禁、人工保护、国家补全、采样留存；现有429/重试/页数/ORCID预算不变；不恢复已移除的Apollo每日500次限制。范围外：此前只读实验的FREE_APOLLO_CLEAR/FREE_APOLLO_UNCLEAR/POSSIBLE_UNCLEAR新准入策略、购买额度、API Key管理页面、换用people/match接口、保存时测试扣费。

“免费/付费”指接口调用权限，而非用户账号套餐。沿用服务器已有Key；前端只读configured布尔值，不返回或编辑Key。

## 关键不变量

### Invariant I-1: 独立持久开关
- Rule: singleton id=1保存searchEnabled/detailEnabled/revision；PUT完整两布尔值+expectedRevision。关闭搜索不抹掉付费开关已保存值，但effectiveDetails=false。首次无行时searchEnabled沿用现有enabled/key/apolloDefault可用判断，detailEnabled=false；该默认只影响新任务。DB错误503，不能当无行或静默开启详情。
- Applies to: GET/PUT、capabilities、所有新run登记。
- Violation consequence: 刷新丢设置，开免费顺带调用付费。
- 来源: original。

### Invariant I-2: 新任务快照，旧任务不变
- Rule: 新run只在既有settings_json中增加一个顶层字段apolloAccess，内含schemaVersion=1、settingRevision、searchEnabled、detailEnabled、source；begin先findRun，已有run直接复用。新任务requested=searchEnabled && criteria.apolloAssist!=false &&服务可用。旧run没有apolloAccess按LEGACY解释：search=run.apolloRequested，detail=run.apolloRequested，保留旧任务原行为；不得在恢复时读最新全局值覆盖。
- Applies to: resolveOptions/begin、手动/定时/队列恢复、Worker、查询。
- Violation consequence: 中途更改条件/付费权限、无法重放。
- 来源: K-discovery-reentry-keeps-run-accounting；K-task-launch-config-registration。

### Invariant I-3: 免费路径绝不进入详情调用
- Rule: frozen detailEnabled=false 时，不创建DETAIL RequestPermit，不调用apollo.detail，不复用收费详情替代“免费效果”。现有搜索信息已足够通过原规则时可正常准入；确实需要详情而禁用时，完成该条为PENDING，保留search.detailsComplete=false与原因PAID_DETAILS_DISABLED，不伪造NO_MATCH/不合格或反复排队等额度。
- Applies to: Worker每次DETAIL分配之前、缓存复用之前、异常恢复。
- Violation consequence: 免费模式扣费或掩盖缺失证据。
- 来源: original；K-provider-error-evidence-and-channel-recovery。

### Invariant I-4: 日志不等于账单
- Rule: 搜索尝试按provider=APOLLO且query_kind∈BROAD/NARROW/VARIANT；详情尝试按DETAIL；其余分other。DISPATCH_RESERVED单列尚未确认完成。数字为已登记尝试，不声称每条收费成功或实际消耗credits。原准入/已明确/未明确计数口径不变。
- Applies to: SQL聚合、summary DTO、前端07。
- Violation consequence: 用户无法区分搜索、收费详情和实际扣费。
- 来源: K-admission-event-and-projection-counts。

## 现状审计

### 当前接口代码及官方定义
- `ApolloPeopleSearchClient.kt:150` POST `/api/v1/mixed_people/api_search`；官方[People API Search](https://docs.apollo.io/reference/people-api-search)标明0 credits。
- 同文件`:123` GET `/api/v1/people/{id}`；官方[Get Complete Person Info](https://docs.apollo.io/reference/get-complete-person-info)列为会消耗credits的详情接口。实际账户账单受套餐影响，不在此推算。
- 当前不会调用POST people/match；不能因叫“付费”就替换现有详情协议。保存配置不调用任何Apollo接口。

### 现有配置与 run
- `DiscoveryVerificationProperties.kt:11` 只有apolloDefault等部署属性；registration.resolveOptions:46将每次启动apolloAssist解析成bool；begin:53先findRun、:64写settingsJson，暂无两种持久化开关。
- 所有resolveOptions调用点已rg核对：ExpertDiscoveryController:400，DiscoveryPipelineService:319，ExpertDiscoveryScheduler:401，Registration.begin:63。统一在registration中接入，不能只修手动入口。
- V158的discovery_verification_run主键execution_id，settings_json TEXT；唯一写者Repository.createRun:342（INSERT IGNORE并断言不可变），其余更新只改revision/inputClosed；读者findRun/mapper、Worker:207–214。查询增加读取同一JSON，无新增列。
- 新discovery_apollo_setting表当前不存在；唯一写者新SettingsRepository；读者新SettingsService→registration/capabilities/controller。读失败不是未设置。

### 尝试账与执行
- V158 attempt含provider/query_kind/result_code/request_json/response_json；写口Repository.reserveAttemptWithBudget、completeAttempt及恢复流程，Worker通过RequestPermit进入客户端。现有只读脚本probe.py也读该表，不写产品设置。
- Worker:344–415先search完整再needingDetails，先复用同run详情、再reserve DETAIL；**关闭付费要在复用前截断**，不只加在HTTP客户端。
- Worker retry对429按retryAt、credits不足+1800秒；Repository:485仅对非Apollo执行item budget，Apollo DETAIL单独等待credits恢复。保留这些路径；不把paid-disabled写成credits-exhausted。
- 准入复算Gate:65比较outcome/qualification/route/personId，不比较额外reason文案；PENDING使用同一纯规则结果，保留search.detailsComplete=false，只有reason附加PAID_DETAILS_DISABLED。
- QueryService:31读取properties/capabilities；summary由Repository聚合attempts。新增只读channel统计，不改VerificationCounts已存在指标。
- 真实免费放宽实验在`scripts/discovery_admission_probe/README.md`明确“不是生产修复”；本计划不把实验369/1636人转换成产品规则。

## 实现方案

### T-1 复用06a配置服务（I-1/I-2）

06a已创建V160、新SettingsRepository/SettingsService和独立SettingsController；本切片不得再添加配置表、重复API或第二个默认值算法。Registration显式注入06a的SettingsService（必需依赖，不用null/测试特例静默回退）。Worker只解析已持久化run快照，不注入实时设置服务。QueryService保持原构造器，仅增加run快照/尝试读取；capabilities里的旧apolloDefault字段保留兼容，新前端只从06a设置API读开关，不能把旧部署默认当保存值。

已grep确认需要更新Registration直接构造器的测试：DiscoveryCountrySupplementFlowIT、DiscoveryCountryRegistrationTest、DiscoveryVerificationIntegrationTest、DiscoveryVerificationWorkerTest。它们必须提供真实/明确stub SettingsService；SQL集成测试target从158升160，旧需详情用例显式保存true/true，不依赖新的默认false误改原意。DiscoveryVerificationFlowIT提供共享fixture，需要补可复用设置seed及160迁移；纯旧run用例仍使用无apolloAccess的历史JSON验证LEGACY。

### T-2 任务登记与兼容（I-1/I-2）

修改registration.resolveOptions：保留null直到begin真正冻结，不用前一次读取值把null改写为true/false；显式false仍是本次opt-out。显式true但持久搜索关闭返回409 APOLLO_SEARCH_DISABLED；服务/key不可用仍为原503。手动/定时/队列统一经过此路径，不能在不同入口复制设置判断。

begin在已有run复用分支之后、createRun之前读取一份settings，计算requested和`apolloAccess`；所有字段来自同一次读取。历史legacy条件`historical && criteria.apolloAssist==null`仍requested=false，不因新默认自动历史补跑。为区分队列中的新任务与历史任务，PipelineService新启动持久化criteriaVersion=2，读取同时接受旧version=1与新version=2；version=1保持historical=true及历史null→false，version=2按新任务语义冻结保存配置，不能再一律historical=true。省略/null仍原样持久化，不提前改写bool；既有execution/run及快照不重写，不用时间戳或内存状态猜来源。除新增顶层apolloAccess外，run快照其余版本和预算字段不改；无需新增schema或领域字段。

新快照示例：

```json
{"apolloAccess":{"schemaVersion":1,"settingRevision":3,"searchEnabled":true,"detailEnabled":false,"source":"SAVED"}}
```

这只冻结本次新增的两项访问开关；不宣称旧代码中的maxPages/timeout等所有属性已经改成冻结读取。该额外修复不在本范围。Key继续从部署配置读，不把秘密拷入快照。

### T-3 Worker分流与统计（I-2/I-3/I-4）

Worker从run.settingsJson解析同一FrozenApolloAccess；缺字段走LEGACY，不可解析/未知schema报CONFIG_INVALID且不发请求；绝不用最新settings补缺。

needingDetails为空时，原规则路径正常finish；非空且detailEnabled=false时，在findSuccessfulDetails和reserve之前直接`finish(evaluate(snapshot).copy(reasonCodes=原原因+PAID_DETAILS_DISABLED))`并return；不得把detailsComplete改true，不改变outcome/qualification/route/personId。已保存的同run证据不清空；升级前旧任务继续LEGACY行为，新任务不读取其他任务的收费详情。

在VerificationRepository新增按executionIds（最多50）聚合函数，从attempt join item读search/detail/other/pendingDispatch，不做额外写；QueryService.summary新增可选`apolloAccess`与`apolloAttempts`两个DTO属性（不是store列）；历史缺数据返回source=LEGACY而不是未知免费。reason映射增加“付费详情未启用，证据仍不完整”。capabilities沿用原ready定义（服务/key/auth可用性），旧apolloDefault只作旧客户端兼容提示；新版配置/开关以06a GET为准，关闭搜索不等同服务故障。

## 变更文件清单

11文件；子系统1（核验运行及报表）；既有共享store仅settings_json新增1个顶层apolloAccess字段，现有队列criteriaVersion区分新旧启动。依赖06a，但不回改其文件。本次明确批准10→11文件的一次性例外，仅增加PipelineService持久化来源兼容，不泛化放宽其他切片上限。

| 文件 | 动作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt | 注入设置、全部入口冻结 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt | 付费关闭分流 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt | 快照/日志DTO/原因 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt | 只读渠道聚合 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt | 新criteriaVersion=2；接受旧1/新2，登记时区分历史和新任务 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt | 新旧run与分流，构造器/160迁移 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementFlowIT.kt | 设置依赖/160迁移，国家补全回归 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountryRegistrationTest.kt | 设置依赖stub，保护/恢复回归 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt | 设置依赖/160迁移，全部启动边界 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt | 共享fixture/160迁移，旧准入回归 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt | 快照/渠道/未知数据回归 |

## 验收标准

- I-1：无行、保存重启、同值保存、CAS并发、无key、DB503；四组合(search/detail)均测试。false/true保留detail=true但effectiveDetails=false。
- I-2：三个启动入口以及同execution重入；runA快照true/true，保存true/false后runA仍true/true、runB=true/false；缺apolloAccess老run保持原行为；新字段格式非法不发请求。队列version=1的历史null仍requested=false，新version=2的null采用保存默认；新任务持久化null而非预解析bool，既有run不重写。已授权IntegrationTest覆盖两种版本及实际执行id。
- I-3：免费模式DETAIL分配0/HTTP0/收费复用0，必要证据不足PENDING+PAID_DETAILS_DISABLED，非NO_MATCH/ERROR；原直接准入、无需详情可匹配、SELF/教授、人工保护回归不变；Gate复算一致。
- I-4：合成3条搜索、2条详情、1条其他，其中1条DISPATCH_RESERVED→统计3/2/1/pending1，总尝试6；不给出“消耗2credits”；取消/超时后原尝试账保留。
- 既有客户端/Rules/AdmissionGate/Repository预算测试通过；超过500次合成Apollo尝试不因系统日额度阻断，429/credits/ORCID限制仍按原测试。
- 测试只用mock HTTP，不能真实消耗Apollo；查到官方文档不等于授权本轮付费实验。

## 人工验收清单

### A-1: 独立保存和重启
- 前置条件：隔离实例连接测试MySQL，配置假Apollo服务与测试Key，清空新settings表。
- 操作步骤：1. GET设置。2. PUT search=true/detail=false。3. 重启并GET。4. PUT search=false/detail=true。5. 再GET capabilities。
- 预期结果：首次detail=false；保存重启后true/false保留；步骤4存false/true，effectiveSearch=false/effectiveDetails=false，未自动将detail抹成false；没有Apollo请求。
- 覆盖：I-1；存储→API→重启。

### A-2: 新旧任务配置边界
- 前置条件：本计划WorkerTest的SQL fixture建runA新快照true/true和legacy runL；让A停在详情前。随后保存true/false。
- 操作步骤：1. 恢复A、L。2. 分别手动/定时/队列启动B/C/D。3. 查看任务summary和假HTTP请求日志。
- 预期结果：A/L继续原详情路径；B/C/D搜索允许、详情请求0，必要详情缺失为待核验；summary明确A/L与B/C/D开关不同；配置不改人工已审核状态。
- 覆盖：I-2/I-3/I-4；各启动入口→Worker→summary。

### A-3: 配置失败、日志及现有门禁
- 前置条件：上述3搜索/2详情/1其他账目fixture；原直接教授/SELF成功fixture、仅姓名歧义fixture、人工拒绝fixture；两会话读取同revision。
- 操作步骤：1. 两会话相继保存不同值。2. 暂停DB再GET。3. 恢复DB，查看尝试统计并运行资格fixtures。
- 预期结果：后保存409、数据库不可用503，不静默付费；统计3/2/1/pending1；原能准入者结果不变，歧义与人工拒绝未放开；不产生500日上限。
- 覆盖：I-1～I-4；设置/报表与must-NOT-change回归。

## 修正记录

- 用户“批准 继续”批准06b暂停C-1的最小修订：加入DiscoveryPipelineService.kt，10→11文件明确一次性例外；新队列持久化criteriaVersion=2，旧version=1保留历史null→false，新版本按保存配置冻结；不改已有run、不新增迁移/领域字段/测试文件。保留原有准入/预算/缓存边界；七项FlowIT失败仍须定位并给出有证据的处理，不以批准视为豁免。批准及提交身份见 `../fast/discovery-paper-master/ledger.md` Amendments。
- 用户“继续 修复 直到可以人工复验”，并澄清目标为“可以执行 review-fast-p”，批准前述七项测试契约修订，保持现行生产行为而非恢复旧规则；不新增文件。下节是本次获批精确替代契约，不是跳过失败的授权。

### 获批测试契约修订：七项历史 FlowIT 场景

仅修改既有DiscoveryVerificationFlowIT.kt中的对应场景及seed辅助函数；精确来源、原断言及保留清单见 `../fast/discovery-paper-master/children/06b/execution.md:451–516`。不得删除/跳过测试、改变Rules/Gate/client/生产预算或查询恢复行为；其余断言保持。

1. seed尾部可增加settingsJson默认"{}"，仅三项历史SELF场景（小写HTTP SELF、公开别名/候选保留、SELF身份负例）显式传{"ruleVersion":0}，保持无apolloAccess，并断言版本0/LEGACY；原结果、身份负例、别名及记账断言全部保留。其余seed不改默认，不覆盖现行SELF直接准入测试。
2. 预算复合场景保留403/invalid/pages/transient断言；将旧budget分为Apollo豁免与ORCID限制两场景。Apollo保留原3次ORCID及有效搜索输入，验证后续Apollo搜索、总次数>3且等于实际HTTP/attempt账、无ITEM_REQUEST_LIMIT、完整搜索/原规则复算/准入一致。ORCID上限2验证仅两次ORCID、无Apollo、COMPLETED/PENDING、NONE/无准入/无person、EVIDENCE_INCOMPLETE+ITEM_REQUEST_LIMIT。429/credits/Repository回归仍必跑。
3. A3 incomplete-search仅把narrow期望0→1；保留全局不完整、无准入，增加COMPLETED/PENDING/NONE、SEARCH_PAGE_LIMIT、search.complete=false、全部page1、恰好一broad一narrow及HTTP/attempt记账一致。不得通过生产提前停止查询来迎合旧计数。
4. 缓存别名恢复两个分支保留原payload（rule0、queryIndex1/page2、缺finishedQueryKeys），不伪造完成键；旧APOLLO_UNCLEAR预期替换为PENDING，并断言COMPLETED/NONE/无person/无准入、SEARCH_INCOMPLETE+SEARCH_PAGE_LIMIT、complete=false且缺"0"完成键。保留恰好一次page2/Frozen Alias、无ORCID、冻结证据及候选并集，核对新增attempt账。
5. A4旧在途缓存保留全部原payload及一次page2/Example University/无institutionNormalization/PENDING；旧APOLLO_NO_MATCH预期替换为SEARCH_INCOMPLETE+SEARCH_PAGE_LIMIT且不得含NO_MATCH，增加complete=false/COMPLETED/NONE/无person/无准入及单次attempt账。缺失每查询完成记录时不能声称完整无匹配。

上述修订不豁免必跑命令：完成后全量既定JDK11 selector及两个IT开关、绑定Node/语法和独立SQL/Worker/queue烟测均须重新成功，才可提交实施并交由独立轻量复验。
