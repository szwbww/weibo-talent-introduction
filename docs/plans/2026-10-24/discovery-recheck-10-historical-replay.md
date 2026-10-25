# 10 · 历史原文补录与同批效果验证

日期：2026-10-24。状态：待执行；本文件不证明修复已上线。前置及发布门禁见 [总计划](discovery-recheck-master.md)。基线 HEAD `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`，须保留工作区已有修改。

## 需求描述

结果：用相同留存原文和名单做修复前后对照；已有897名单走统一重审，未建档的新绑定走单独补录入口并复用现有消费门禁。输出真实新增绑定/新建RAW/准入/同步数量及失败清单。默认只读dry-run，不因代码发布自动回写全批。保持普通发现预算/去重/队列/邮件数据；不承诺519个新增人或13人直接准入。

## 关键不变量

### I-1：冻结真值与收益分层
- 规则：manifest固定原文record/attempt/SHA、baseline emails、专家docId/identity/fact/revision、配置/版本/评估时刻；收益至少分新增邮箱、新绑定、已有人补证、新建RAW、建议可准入、实际准入、同步成功。不同快照不能算代码收益。
- 适用：回放工具/报告/新补录入口
- 违反后果：用519绑定伪装519准入。
- 来源：需求及下列源码审计

### I-2：补录共享门禁
- 规则：新绑定必须来自03生产parser有效proof；补录通过consumeOutcomeInternal，先邮箱校验/真实去重/身份约束，再RAW写入/原核验登记/补全入队；不直接写candidate或人工approve。普通来源预算不重置，不推进生产采集checkpoint。
- 适用：ExpertDiscoveryService新增薄入口及BackfillService
- 违反后果：第二条入库旁路、重复专家。
- 来源：需求及下列源码审计

### I-3：默认零业务写与应用授权边界
- 规则：工具默认compare；仅写受权限保护的回放报告。应用要求明确mode与此前manifestHash，重核当前来源hash/身份/准入revision；只对固定范围执行。报告字段不含API key/凭据，全量私人名单只在服务器本地保存，不自动上传仓库。
- 适用：回放CLI/BackfillService
- 违反后果：验证变成误批量准入。
- 来源：需求及下列源码审计

### I-4：失败可续且不编造
- 规则：部分原文缺失/扫描超限/邮箱无效/去重错误分别记录，不计完整成功。逐人/逐paper已完成receipt重复apply幂等，不清旧原文/trace/请求计数；取消不回滚既有成果。
- 适用：回放报告/补录/统一核验
- 违反后果：漏数据却报全量完成或重复收费。
- 来源：需求及下列源码审计

## 现状审计

ExpertDiscoveryService:1715 consumeOutcome与:2162 consumeQueuedItem最终均调用:1751 consumeOutcomeInternal；:2356 identityRejection、:2361 proofFor；:2371 duplicate修复/登记；:2482 buildProfile、:2546 admitDiscoveryExpert。因此可新增窄入口复用同一方法，不能从审计脚本手写ES。
参考审计：执行28671，4472有原文/4487保存尝试/897当前待审核/519新绑定/13人正向事实。它们是冻结时点事实，实施时须以manifest核对，不能硬编码断言现在线上数量必等。
本子计划不加表；对照输出为服务器本地JSONL manifest/report，0700目录/0600文件；应用审计沿已有task/verification/trace，原文写者不新增。RAW、verification、enrichment现有全写链及下游读取见共享审计。

完整检索回执、共享存储全集及交互矩阵：[证据目录](discovery-recheck-evidence/README.md)。以下列出的写入边界为准，不因引用共享审计而扩大文件范围。

## 实现方案

1. 新DiscoveryHistoricalReplayTool命令`freeze/compare/apply-bindings/report`，类内隔离只读依赖，compare绝不可加载会启动scheduler的Spring应用。新增脚本仅调用工具/记录版本和hash，不直接执行准入SQL。freeze把当时服务器源清单与样本事实存manifest，文件权限0600。新专家名单与旧待审核名单分文件；已有CLI运维requeue不改造成此入口。（I-1/3）
2. compare使用03/04/06生产解析器，先按相同原文SHA对照首提取，再按原文级/批内邮箱+姓名/全历史专家去重逐级报数；只在完整baseline+原文coverage时计算新增/移除，PARTIAL不作零增益结论。资格恢复单列，旧当前准入不改。只读冻结回放通过原PaperQualificationEvidenceExtractor显式factory固定nanoTime，保留100页/2MiB/100事实工作上限；仅Tool compare的独立analyzer使用，原analyzer/Backfill/Worker/@Component仍为System clock与10秒软限。manifest/报告绑定预算模式、实现hash及新CLI版本，禁止缓存第一份报告冒充重复解析；旧时限PARTIAL保留历史，新模式必须真实重新解析且独立标版本，不静默重分类。（I-1/4）
3. ExpertDiscoveryService新增consumeArchivedPaper(paper,authorEmails,sourceName,originalPaperKey,newExecutionContext)：只接受BackfillService解析出的、原文SHA仍匹配的完整proof；调用原consumeOutcomeInternal和paperIdentityFactory。不得篡改原execution归属；新补录有新task execution且source仍原论文source，来源key/原archive id进入step2引用。明确httpRequests=0是复用原文，不谎报新下载；业务结果单独记补录统计，不污染原批采集计数。现有DiscoveryVerificationExecution仅增加可空冻结原文上下文（同Registration文件），携带manifest evaluationAt/确切paperSourceRefs；reserve校验sourceName/sourceItemKey后直接采用固定输入，不再按新execution/DOI重新选来源。默认null保持普通登记原clock/locate语义，禁止reserve后重写快照。（I-2）
4. 新DiscoveryPaperBackfillService负责冻结paper列表、逐篇租约、parse→dedup/consume、收据；仅apply-bindings manifestHash一致才调用。重复命中走既有recordIdentityDuplicate/核验恢复与补全幂等，不覆盖整人profile；已有人需要重新资格核验则送08新重审run，不重置旧item。每paper失败receipt含可重试原因；重试不得重新纳入后续新文件。（I-2/3/4）
5. 历史验证顺序固定：结构fixture→部署类/修复类同SHA抽样（确认全部新绑定负例）→4472原文完整对照→897冻结名单AUDIT/REUSE→人工阅读具体diff→仅对批准范围ENFORCE/补录。刷新外部数据另建REFRESH轮，不混算代码修复收益。结果生成审计报告，生产真实名单不提交仓库。（I-1/3）

## 变更文件清单

仅下列 9 个文件。新增文件明确标注；其余文件不得顺带修改。第8项依A7持续授权为固定原文/评估时刻传播直接依赖；第9项为确定性只读解析的显式窄入口，不改普通发现/补录默认路径。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 增加薄补录入口，不复制门禁 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperBackfillService.kt` | 新增原文补录协调/receipt |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryHistoricalReplayTool.kt` | 新增隔离freeze/compare/report及显式apply |
| `scripts/discovery_recheck_replay/run.sh` | 新增可执行回放命令封装 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperBackfillServiceTest.kt` | 新增去重/无写/部分失败 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryHistoricalReplayToolTest.kt` | 新增manifest/差异核账 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt` | 扩展08新建的FlowIT：原文→consumer→RAW→核验→候选端到端 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` | 现有Execution可空冻结原文上下文及reserve来源校验/固定输入；普通默认null行为不变 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt` | 显式只读冻结回放factory，保留工作上限；普通默认10秒clock不变 |

## 验收标准

- I-1：报告提供分母、各阶段交集/差集与完整性；同manifest两次compare相同（运行耗时除外）；不能以生产当前数字强凑519/13/897。
- I-2：原文实际parser输出穿过真实consumer，mock不能预填正确proof；重复apply仅首次创建RAW，旧checkpoint和旧批统计不变；现有无效邮箱/身份冲突拒绝。
- I-3：compare数据库/ES业务写0、provider HTTP0；apply错误manifestHash拒绝；本地脚本不输出连接密码/key。
- I-4：缺失文件和部分解析分别计数，不能以0关联当完整失败；中途取消后重启只续固定未完成项；重试不清原attempt；不得触发发信/联系人/邮件创建。
- 必需新鲜覆盖命令扩展为七类（替代此前六类命令，非再并列运行）：指定JDK11执行`mvn test -Dtest=DiscoveryPaperBackfillServiceTest,DiscoveryHistoricalReplayToolTest,DiscoveryRecheckFlowIT,DiscoveryCountryRegistrationTest,DiscoveryCountrySupplementFlowIT,DiscoveryVerificationIntegrationTest,PaperQualificationEvidenceExtractorTest -DmysqlIt=true`；真实MySQL不得以skip通过。原直接登记/补全/集成及extractor deadline测试保持不改，授权FlowIT断言真实consumer注册后的固定evaluationAt和精确record/attempt/SHA，授权ToolTest用真实超100页原文验证重复解析一致/PAGE_LIMIT且不混入SOFT_TIME_LIMIT。保留master完整`mvn test`、`mvn package -DskipTests`及实际CLI/历史只读验证。

## 人工验收清单

### A-1：可复现修复收益
- 前置条件：用freeze生成原文与897名单的实际存在快照；仅服务器本地保留私人明细。
- 操作步骤：compare两次并对比report除耗时字段；抽查新增绑定对应原文页。
- 预期结果：两次各阶段计数相同；所有收益带SHA/页/规则，PARTIAL单列；没有宣称519全部准入。
- 覆盖：I-1/3

### A-2：补录幂等
- 前置条件：测试manifest有新绑定1、已有人1、歧义1、缺文件1。
- 操作步骤：先compare，再显式apply-bindings两次。
- 预期结果：第一次最多新建RAW1；第二次新建0；歧义/缺文件不入库；已有人的历史与人工决定不变。
- 覆盖：I-2/4

### A-3：固定名单新旧对照
- 前置条件：同一待审核manifest，先旧规则留档，再新代码AUDIT/REUSE。
- 操作步骤：查看逐人before/proposed/原因及统一8步；选择确认的一小批ENFORCE。
- 预期结果：AUDIT实际准入0；ENFORCE实际仅成功提交者；候选同步独立；没有发信或联系人记录。
- 覆盖：I-1/3/4

人工验收开始后，才从本节导出同名前缀 `-acceptance.md`；机器通过不代表人工验收完成。

## 修正记录
- 2026-10-25，依A7持续授权增加现有Registration文件（7→8）与上述覆盖命令。原reserve自行取clock并locate来源，与本计划冻结manifest输入矛盾；仅新增默认null的窄上下文传播/来源校验，保留普通旧行为、共用consumer/登记门禁，禁止事后改快照。检索回执与前后身份见10 execution及ledger的Amendments。
- 2026-10-25，依A7持续授权增加现有原文extractor（8→9）及七类覆盖命令。实际同批4472/4487/3904 COMPLETE报告受10秒软限出现PARTIAL，重复运行不可用首份缓存伪造确定性；仅只读compare改用显式工作限模式并绑定版本/hash，普通运行与Backfill/Worker/既有deadline不改。旧报告保留，新模式真重算，具体回执与前后身份见10 execution及ledger。
