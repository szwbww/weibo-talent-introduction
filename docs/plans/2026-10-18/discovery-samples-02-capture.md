# 02 · OPENALEX 同步与队列采集接入

待实施；依赖01。8文件、两个子系统（发现入口、全文采集）。与Apollo05共享4文件，合并顺序见总I-5；先复核最新签名，不能覆盖Apollo更改。契约：[contracts](discovery-samples-contracts.md)；审计：[audit§3～7](discovery-samples-code-audit.md)。

## 需求描述

O-1：同步及队列fresh抽取都可留下关联真实execution的OPENALEX样本，记录已取得的PDF/HTML/XML字节或下载失败证据。
必须保持：N-1 原HTTP顺序、重试/限额/超时、解析结果及其他来源行为；N-2 队列缓存、租约、暂停/恢复、身份版本及检查点；N-3 RAW/Apollo准入流程及业务统计；N-4 采样异常不替代业务异常。
范围外：解析规则修复、全局HTTP改造、Unpaywall响应抓包、queue DTO/schema修改、日志前端、学历/职称核验。

## 关键不变量

### Invariant I-1: 正执行ID显式传递
- Rule：按C-2建立context，SYNC从discoverFromSource已有execId传到parallelExtractOutcomes的实际worker；QUEUE从processJob持有state.executionId及job.id传入fresh提取。无正ID不采样；不能读global latest。scope不扩到consume/RAW/registration。
- Applies to：ExpertDiscoveryService、Pipeline。
- Violation consequence：并行串任务或把Apollo异常当样本失败吞掉。
- 来源：总I-2/I-5；K-task-activity-execution-identity。

### Invariant I-2: 字节来自既有下载
- Rule：C-5 attempt/body字段按实际值；只在原完整byte[]已读取后引用/保存。所有请求、回退、重试、deadline、HttpHeaders callback和业务异常传播不变；采样不得另读response stream、下载URL或请求Unpaywall。无scope所有钩子no-op。
- Applies to：PdfEmailExtractor、EuropePmcDataSource。
- Violation consequence：增加流量、消费一次性流、丢预算、改变原结果。
- 来源：K-source-recall-needs-original-layout；总I-3。

### Invariant I-3: 抽取与消费隔离
- Rule：样本仅EXTRACTION；页预抽取、暂停尾部、队列saveExtraction失败或尚未消费均不算RAW/准入。缓存非空不触发采样或下载，不改identityRuleVersion。配额/IO失败只影响诊断，不进入任务failureReasons；HostBusy沿原延期分支。
- Applies to：两个入口、任务统计、队列缓存。
- Violation consequence：统计混淆、重复下载、消费失效仍宣称成功。
- 来源：K-progress-log-batchonly-two-readers；K-identity-cache-version-and-admission；K-discovery-reentry-keeps-run-accounting。

### Invariant I-4: 不干预Apollo必要写入
- Rule：保留Apollo05冻结context、reserve→RAW→activate、异常处理和checkpoint顺序；采样catch边界只包含诊断动作。Apollo登记失败仍走原可重试业务错误，不因为样本成功写入而推进页游标。
- Applies to：合并后的ExpertDiscoveryService、Pipeline及测试。
- Violation consequence：漏核验、绕过门槛或丢论文。
- 来源：apollo-05-integration.md；K-page-commit-must-cover-consumer-failures。

## 现状审计

源文件/行号节选与全部调用点已保存在evidence；本片不新增/修改任何数据库或ES字段，只有通过01 Store新写文件。

- 同步：ExpertDiscoveryService:928调用parallelExtractOutcomes；:1638实际worker；:1573现有catch；:1746身份过滤；:1779 RAW写入。
- 队列：Pipeline:1005检查缓存、:1014 fresh提取、:1021 CAS存结果、:1059暂停分支、:1075消费；ExpertDiscovery:2008四参数extractQueuedItem、:2031请求门禁、:2044 PAPER分支。新增末尾可空context默认null，不为没有context的旧调用造执行ID。现有Mockito四参数桩要同步改成五参数调用匹配，不能只编译生产代码。
- 全文：Pdf:79 FetchRetry→:224下载→:267完整bytes；EuropePMC:101 FetchRetry→:110 getForObject bytes→:193 JATS解析。仅加采样钩子，不重构解析流程。
- IP-1 同步context跨线程写包；IP-2 队列context/缓存/暂停；IP-3 完整响应到文件；IP-5 与Apollo登记消费共享入口。读者仍是01Store扫描与03CLI，无新业务reader。

## 实现方案

1. **同步接入（I-1/I-3/I-4）**：清单1新增末尾可选采集器依赖（默认null，兼容现有手工构造）；parallelExtractOutcomes新增不可变context参数，discoverFromSource传execId与当前sourceBatchNumber。在每个worker调用extractOutcome内，只有OPENALEX+有效context才通过capture包装source.extractAuthorEmails；原extractOutcome外层业务catch不移位。无需修改PaperMetadata/outcome，不触动private processPaper行为。
2. **队列接入（I-1/I-3/I-4）**：清单2从state显式构造context，新增参数传给清单1 extractQueuedItem。只包PAPER/OPENALEX的真实source.extractAuthorEmails调用，放在原FulltextRequestGate scope内；保留HostBusy特殊分支和旧结果stamping/限长。没有context时沿旧流程；缓存命中不经过该入口，绝不回补样本。不得在consumeQueuedItem重新开启采样。
3. **PDF/HTML钩子（I-2）**：清单3，保持构造器与方法签名不变，通过01当前scope静态入口no-op调用。每次实际boundedHttp尝试记录开始/结束；原headers callback之后记录实际status/contentType；在完整bytes形成后交给scope保留引用并记录实际PdfExtractionProperties。异常只记类名/实际status，重抛原对象；外层原catch和重试逻辑不变。没有完整bytes不写半截；不额外读error response body。
4. **XML钩子（I-2）**：清单4，在FetchRetry每次getForObject调用周围记录attempt，返回完整payload时留存；成功status/contentType=null；异常原样抛回既有catch。外部独立EUROPE_PMC调用无OPENALEX scope而不采样。公共fetchFullTextXml的其它调用不因钩子改变语义。
5. **测试（I-1～I-4）**：清单5～8扩展既有测试，更新extractQueuedItem所有Mockito桩/verify；添加scope接入、重复线程池复用、多个执行、缓存、暂停尾部、HTTP403/超限/XML/PDF/HTML、钩子异常与body限额；不调整既有身份fixture真值以追求通过。Apollo05合并后在两个服务现有测试文件补共同场景，不修改Apollo专属文件。

## 变更文件清单

|#|文件|动作|
|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`|修改|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt`|修改|
|3|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt`|修改|
|4|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt`|修改|
|5|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`|修改|
|6|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt`|修改|
|7|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt`|修改|
|8|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSourceTest.kt`|修改|

## 验收标准

- I-1：正ID、空/负ID不采、单线程/线程池/不同执行、SYNC/QUEUE、非OPENALEX全部断言；scope finally退出不串包。context不依赖TaskProgressStore恢复状态。
- I-2：开关两态HTTP请求序列/次数、callback、重试、解析结果深相等；403没有body；超限无半截；PDF/HTML/XML SHA等于模拟响应bytes；XML成功status=null；无额外网络/stream读取。原deadline相关测试继续通过。
- I-3：cached extraction新增下载0、样本0；saveExtraction失效/暂停只留EXTRACTION；任务原counter与开关关闭一致；identityRuleVersion未改。多author样本1、缺名记录计数按输入值。
- I-4：模拟采样IO失败+Apolloreserve失败，仍无RAW/activate且不提交页checkpoint；RAW成功activate失败仍保留Apollo可恢复意向。不能通过扩大catch吞掉原异常。
- 运行：`mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,PdfEmailExtractorTest,EuropePmcDataSourceTest,OpenAlexDataSourceTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DskipNodeTests=true test`；合并后再跑Apollo05指定的已有ControllerMvc/Scheduler及新增DiscoveryVerificationIntegrationTest。不为本计划改OpenAlexDataSourceTest代码；其已有断言是回归保护。

## 人工验收清单

### A-1: 同步、队列与实际字节
- 前置条件：01/02完成；测试在内存模拟数据源/HTTP，无真实外网；分别构造正executionId=9001的同步与9002的队列OPENALEX；使用既有PDF资源、合成HTML、JATS与HTTP403。
- 操作步骤：1. 执行本节验收标准命令并加 `-Dsample.acceptanceDir=/tmp/discovery-sample-acceptance`。2. 查看 `capture/cases.json` 与 `capture/samples` 下的包。
- 预期结果：sync的executionId=9001，queue=9002；crossExecutionMixups=0；PDF/HTML/XML各有SAVED字节且bodyHashEqual=true；403的bodyStatus=NOT_AVAILABLE、httpStatus=403；每包stage=EXTRACTION；新增网络请求0。
- 覆盖：O-1、N-1、I-1/I-2、IP-1/IP-2/IP-3。

### A-2: 缓存、暂停与其他来源
- 前置条件：同一测试命令构造cache-hit、pause-after-extraction、other-source；缓存已有extractionJson，暂停发生在预抽取结束与消费之间。
- 操作步骤：1. 查看 `capture/cases.json`。2. 对照case所列样本目录、网络计数与业务结果。
- 预期结果：cache-hit newDownloads=0、newSamples=0；pause-after-extraction retainedStage=EXTRACTION、consumed=0；other-source newSamples=0；所有case业务对照 equal=true，身份缓存版本未变。
- 覆盖：N-1/N-2/N-3、I-1/I-3、IP-1/IP-2。

### A-3: 与Apollo共同故障
- 前置条件：Apollo05已合并；服务测试提供store不可写、Apollo reserve失败及activate失败场景；使用mock事务/ES，不访问生产。
- 操作步骤：1. 执行服务测试并输出 `capture/apollo.json`。2. 查看reserve-failure与activate-failure结果。3. 查看对应采样IO日志。
- 预期结果：reserve-failure rawWrites=0、activated=0、checkpointCommitted=false；activate-failure reservedRecoverable=true、checkpointCommitted=false；采样错误decision=IO_ERROR，不能改变上述结果。旧任务业务计数对照equal=true。
- 覆盖：N-2/N-3/N-4、I-3/I-4、IP-5。

## 修正记录

### 2026-10-18 · Epoch2 冻结Apollo05前置导入
- 批准依据：用户选择「授权本地集成并继续」，含固定05及必要依赖的保留历史线性导入、计划范围/台账同步；总I-5。该授权不允许改main、推送、部署、重写历史或引入06/07。
- 本次新增开发仍只限原清单八文件。另允许先按 [apollo05-import-manifest.json](discovery-samples-evidence/apollo05-import-manifest.json) 的精确源提交顺序原样迁入冻结上游代码；每个迁入提交保留原始来源SHA。这是既有批准实现的机械导入，不是重新开发上游或扩展采样设计。源代码上限 `1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848`，其他上游变更禁止导入。
- 导入前核对目标工作树/分支与干净product index；导入后在采样改动前核对manifest各路径与源blob一致，并记录实际本地SHA映射。Apollo05及依赖的原始源证据可只读参考，但不得将旧测试当成本次测试。
- 导入完成、采样改动前运行基线：`mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryContinuousMvcTest,ExpertDiscoveryPipelineUnavailableMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,DiscoveryVerificationIntegrationTest -DskipNodeTests=true -Dapi.version=1.44 test`。使用JDK11及隔离MySQL Testcontainers，Docker socket可设 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`；不使用共享/生产DB或真实Apollo。
- 最终必须新运行原02采样回归命令（加 `-Dsample.acceptanceDir=/tmp/discovery-sample-acceptance` 输出验收产物），以及上述Apollo集成回归命令。新02行为必须通过；原有fixture错误只可逐项记录/归类，不得虚报整体命令成功或偷偷修复无关代码。
- 入口参数、Mockito stub与catch边界按冻结05实际签名核对后接入；保留reserve→RAW→activate、真实execution、重试/检查点语义。原A-1/A-2/A-3完整保留，两个原服务测试文件内完成采样+Apollo共同故障场景，不修改冻结导入的Apollo专属实现/测试。
- 02仍以01末态代码为产品Base，所有机械导入在单独提交中，最终新采样实现提交仍用 `feat(fast-p): implement 02`；导入不是fix round。独立轻量验证应同时核对来源blob和新八文件改动，不将源代码当作额外新功能开发。

