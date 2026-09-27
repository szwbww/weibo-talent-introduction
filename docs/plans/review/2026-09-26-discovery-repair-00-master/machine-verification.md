## Epoch 1 — 2026-09-27

- Reviewer: `/root/aggregate_design_review`，独立 review-p → verify-p；未参与实施。
- Phase: aggregate/master。
- Governing master: `docs/plans/2026-09-26/discovery-repair-00-master.md`，commit `152028fb4f6adf467a5254ed3627bf84c561f6bc`，SHA256 `0a40f221660ecc0008394126ae029e869c6078e725f2585cd2d55ba938be67fd`。
- Master identity state: CONSISTENT；调用方与保留树哈希一致。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`。
- Branch: `fast/2026-09-26-discovery-repair-00-master`。
- Evidence HEAD: `075425401b1f5d401a7ec2bf8549a2d53033c703`；最终产品代码 `4ad9e79b034798ee78f12c3285faf5882991b3bc`。
- 用户本轮豁免仅限 fast-p 的 READY/证据提交格式前置门槛。台账 BLOCKED_PREFLIGHT、历史三报告不同提交、06 action 格式不阻断本次设计复验；设计、代码、测试、实质证据要求未豁免。未修改全局规则或 fast-p 文件。

## Verification Result: FAIL

Plan: `docs/plans/2026-09-26/discovery-repair-00-master.md`（A1/A2 后合同）
Implementation boundary: `64c0394a940bd79c2ecc04e5c497650f045faa75..4ad9e79b034798ee78f12c3285faf5882991b3bc`
Convergence: INITIAL
Manual acceptance: PENDING

完整构建通过；独立补充探针确认两项 P1，均位于09d新增复评路径。因此不能据全绿测试判定设计达标。以下源码相对路径全部以所列保留工作树为根；`DS` 代表 `src/main/kotlin/com/weibo/talentintroduction/discovery/service/`，`ES` 代表 `src/main/kotlin/com/weibo/talentintroduction/expert/service/`，仅为表格缩写。

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | fresh exit 0；2026-09-27 18:05:00 +08 完成，5:03；JUnit 4,141 tests / 0 failures / 0 errors / 13 skipped，266 XML；Maven-bound Node 1,194 tests / 235 suites / 1,194 pass / 0 fail / 0 skipped；WAR完成。完整输出 `/tmp/discovery-aggregate-mvn.log`。 |
| `python3 /tmp/discovery-aggregate-audit.py` | PASS | exit 0；55实际src变更⊆56授权文件；12片逐片范围均合规；24唯一DD；原PDF7份、HTML4份与原始归档字节相等；内部SHA：05=6、06=9、07=8、04=14；11资源键一致、CSS diff=0、三层keyword/dynamic=false。日志 `/tmp/discovery-aggregate-audit.log`。 |
| JDK11 `javac -cp [fresh Surefire java.class.path] /tmp/DiscoveryAggregateProbe.java`，随后同CP加`:/tmp`执行 `DiscoveryAggregateProbe` | PASS（成功复现缺陷） | exit 0；运行当前编译产物，实际 CandidateEligibilityService/ExpertRevalidationService；外部邮件验证/ES writer替身。输出 V-1 requireValidEmail=true、配置NO_MX_RECORD、实际validate调用0、结果Promoted；V-2真实_id≠历史orcidId、identityAllowed=true、结果WriteFailed。`/tmp/discovery-aggregate-probe.log`。CP精确值保存在 `/tmp/discovery-review-classpath`。 |
| `git diff --name-only 64c0394a940bd79c2ecc04e5c497650f045faa75 4ad9e79b034798ee78f12c3285faf5882991b3bc -- src`；各child base/code-head同式；`git show` A1/A2 | PASS | 完整生产差异及变更测试/资源、审计/契约均核对；25生产、20测试代码、10资源。实际子片文件数2/2/8/10/10/10/10/6/6/9/9/8。未修改守卫测试也未扩大其白名单。 |
| 全量测试内的定向套件与JS语法门禁 | PASS | 每片指定类均由本次clean package运行，未搬用轻验数。没有重复启动十二轮等价Maven。关键类计数见下。`node-check-app`、`node-check-task-modal-runtime`由pom执行。 |

关键 fresh 类计数（均0失败/错误/跳过）：ExpertDiscoveryServiceTest150；DiscoveryCheckpointCodec11；DiscoveryResult7；DiscoveryPipelineService46；OrcidDataSource10；OpenAlexDataSource73；OpenAlexRequestPolicy42；CoreDataSource19；JatsXmlEmailParser35；PdfEmailExtractor39；PlainTextEmailExtractor6；SourceAuthorEmailResolver15；DiscoveryIdentity7；SubjectScopeCatalog8；ExpertClassificationService29；ExpertIndexService10；ExpertSearchService70；ExpertIndexWriterService36；ExpertRevalidationServiceBehavior16；ExpertRevalidationService27；CandidateEligibilityService7；Enhanced18；ExpertAcademicEnrichmentJobService4；Worker10；OperatorStatusWriteSeamGuard1；ExpertClassificationVersionGateGuard2。全量13个跳过是既有集成/条件套件，名称完整保存在audit日志；未声称执行真实MySQL/Docker或线上来源测试。

### Contract Matrix

每个机器项均为 mandatory；人工项独立标PENDING。Source列的子计划编号指本次明确列出的批准计划，审计附件属于相应合同。PASS仅针对所列子要求，不覆盖同组FAIL。

| ID | Requirement / Source | Required evidence | Verdict / Evidence |
|---|---|---|---|
| master-I-1 | 24 DD覆盖；真实/构造分开；DD08/10不关闭 | scope、原字节、原始SHA、真实解析 | PASS；两个scope清单联合24唯一DD；7原PDF/4原HTML逐字比对，XML14哈希；05四对/06五对/04三对/07三论文两邮箱。DD08原文长期存储、DD10上游错配仍未解决，影响人数未知。 |
| master-I-2 | ≤10文件/≤2子系统、共享文件依序 | 各child真实Git边界、授权清单 | PASS；12片2/2/8/10/10/10/10/6/6/9/9/8，无越界；联合55⊆56。09前置/修复提交链及抽取版本递增保留。 |
| master-I-3 | 限定schema/key/version/config变化 | 全量产品diff、三层映射、缓存测试 | PASS；只有researchFieldIds三层keyword、ORCID专属hash、rnd-v3、tailPages；无DB migration；VERSION20260925不变，EXTRACTION_VERSION20261001。 |
| master-I-4 | 真实最终635/6/4/4、629与SEARCH_FAILED；首选方式 | 实际renderer、最终fixture | PASS；`static/app.js:2681`；fresh08.json/08.html；共用实时/历史函数，未重算嵌套失败数。 |
| master-I-5 | 独立记录、全量JDK11、缓存兼容、未上线 | child记录、fresh全量、版本消费 | PASS（机器部分）；本次build事实如上，03–07 versionCases旧缓存拒绝，未自动重抽；人工/发布前清点待办。 |
| S-1/A2 | 八列DOM、复用class、无inline/CSS变更、11键 | 08批准骨架、JS DOM断言、diff | PASS（机器）；app.js2687–2719；index.html11–15/2286–2291，11键同值；styles.css零差异。滚动/字体/details实际人工视觉=PENDING。 |
| M1 | 唯一同源身份、冲突未知、不拼ID | resolver/JATS实际正负例、消费防线 | PASS；DS/SourceAuthorEmailResolver.kt158、JatsXmlEmailParser.kt132、OpenAlexDataSource.kt193；共享/同名/縮写冲突均拒绝；原文真值未改。 |
| M2 | 邮箱验证保持；仅09专业资格变更 | 自动晋升/候选复评实际调用 | **FAIL V-1**；ES/ExpertRevalidationService.kt339–347绕过validate；V-2同时破坏真实_id兼容。初始consumer邮箱验证/重复create保持。 |
| M3 | 暂停、预算、大小、时限 | retry/cancel/queue/limits现有测试 | PASS于规定场景；DS/ExpertDiscoveryService.kt2055、OpenAlexDataSource.kt75、PdfEmailExtractor.kt110/296；不把apiRequests当计费credit。 |
| M4 | 禁生产清洗、部署、发送配置/迁移/新增自动化 | diff和本次操作边界 | PASS；只运行离线构建/探针，未联网业务或改生产；仅报告与后续repair草案写入。 |
| 01-I1 | 两同步循环dedup/RAW/enqueue失败页保留 | cursor矩阵、重放 | PASS；DS/ExpertDiscoveryService.kt810–836/1120–1158；150测试中dedup500、ORCID、组合故障/重放；fresh01.json ACTIVE、DEDUP_INCOMPLETE、PARTIAL_SUCCESS。 |
| 01-I2 | 原因优先级、取消与空结果终态 | stats/checkpoint/outcome | PASS于既定用例；上述路径与DiscoveryResultTest7；RAW>enqueue>dedup，队列dedup原规则。 |
| 01-I3 | 同邮箱重放幂等、真实_id补队列 | consumer+writer/replay | PASS初始重放；DS/ExpertDiscoveryService.ktrecordIdentityDuplicate；ExpertDiscoveryServiceTest5343；复评新增路径的真实_id失败另见V-2。 |
| 01-I4 | 未知/无效/拒绝/重复等原边界 | consumer真实输出、外部替身 | PASS该片；后续09的M2退化见V-1，不把历史轻验改写失败。 |
| 02-I1 | 同游标≤3；暂时网络/HTTP白名单；TLS永久错误拒绝 | 请求次数/异常序列 | PASS；DS/ExpertDiscoveryService.kt664–746/2078；Tests3902–4034。 |
| 02-I2 | 1s/2s+≤200ms，≤100ms取消/deadline；429/额度延期 | 重试等待与policy | PASS；同文件2055–2076、685–706；Tests4035–4143。 |
| 02-I3 | 成功才消费、每尝试reserve、UNKNOWN不退款、队列5m不叠加 | policy+queue+计数 | PASS；OpenAlexDataSource.kt75–89；policy42/pipeline46；02.json两次C1、papers=1、sourceFailureCount=0。 |
| 02-I4 | 原消费/发送边界 | 共享consumer、diff | PASS该片；09退化单列V-1。 |
| 03-I1 | 7brace表达式30展开项、断行、非法/跨段拒绝 | 逐表达式来源与实际extract | PASS已规定夹具；PlainTextEmailExtractor.kt24–55；fresh03.json真实7组30项及kairouz；无名不入RAW。 |
| 03-I2 | 同源规范化、原文本哈希、不开跨段猜姓名 | resolver联系记录与反例 | PASS；SourceAuthorEmailResolver.kt109–155；原/规范化pair保留，Jane两个写法及共享负例。 |
| 03-I3 | 抽取版本/证据版本独立、旧缓存失败 | Git版本链、versionCases | PASS；最终EXTRACTION_VERSION20261001；旧各版20260926–30受拒，pipeline FAILED，无自动清缓存。 |
| 03-I4 | M1–M3 | parser→consumer实际链 | PASS该片；总M2失败见V-1。 |
| 04-I1 | 可信PMC主机/完整路径/冲突拒绝，无新映射请求 | 实际OA metadata→XML | PASS；OpenAlexDataSource.kt264–288；04.json PMC7759461，首XML，一次metadata/一次XML。 |
| 04-I2 | 逗号窄拆分、完整name-alternatives、重复contrib冲突 | 实际adapter/JATS | PASS；OpenAlexDataSource.kt290、CoreDataSource.kt207、JatsXmlEmailParser.kt164–178；Jan Jakubův与合成反例。 |
| 04-I3 | 直接数字label、唯一xref、contrib-info有界 | 实际XML3真联系人/负例 | PASS；JatsXmlEmailParser.kt115–142/196–205；04.json三对逐项吻合；不凭姓名补OA ID。 |
| 04-I4 | 唯一ORCID才补ID、旧缓存不兼容 | OA193、版本用例 | PASS；真实XML三人无可信作者ID，不伪造ID；历史资格替身下consumer数字不等于最终真实资格晋升。 |
| 04-I5 | M1–M3 | 共享consumer边界 | PASS该片；最终M2见V-1。 |
| 05-I1 | 唯一标记/作者锚点、跨栏/同名/共享拒绝 | 原PDFBox与负例 | PASS；PdfAuthorContactLayout.kt38–88/124–168；05.json Klaus/Davide/Shirui/Philip四对，另外四共享仍未知。 |
| 05-I2 | 姓+全部首字母唯一、Email标记、页/原片段哈希 | layout/resolver+真实PDF | PASS；layout71–90/154–179；原三篇PDF字节比对成功。 |
| 05-I3 | claims统一冲突，CORE/HTML不借PDF推断；版本 | actual shared callers、版本case | PASS；SourceAuthorEmailResolver.kt35–46/158–169；单PDDocument生命周期；不重复下载。 |
| 05-I4 | M1–M3 | 共享负例和消费 | PASS该片；09专业准入独立检查。 |
| 06-I1 | 前maxPages∪末1、0/1配置、同deadline、10MiB | 原62页、配置/选页测试 | PASS；PdfExtractionProperties.kt12–23；PdfEmailExtractor.kt290–331；06.json [1,2,62]/[1]/[1,2]/tail0；application.yml272。 |
| 06-I2 | 已选页mailto只线索，query/cc/bcc不增作者 | 实际注释/去重/错配 | PASS；PdfEmailExtractor.kt311–318；11目标入集合，W4292779060两线索身份空；04–07同邮箱去重。 |
| 06-I3 | 明确末页作者块五对、不能正文猜/错配绑 | 原62页PDFBox+元数据 | PASS；PdfAuthorContactLayout.kt95–121；fresh06.json五对匹配，完整原PDF未裁页。 |
| 06-I4 | M1–M3 | 边界与资源预算 | PASS该片；09不抹去原解析真值。 |
| 07-I1 | 对应标题/list具名mailto、唯一metadata、冲突消解 | 三原HTML/URI负例 | PASS；SourceAuthorEmailResolver.kt23–27/80–106；3论文/2邮箱，Vijay/Iqbal，重复计1；+保留、CRLF/多收件人拒绝。 |
| 07-I2 | Anubis标题+script、空可见HTML无效；正常无邮箱成功 | 实际挑战页/回退请求 | PASS；PdfEmailExtractor.kt162–181；fresh07.json与OpenAlex73/Pdf39；普通challenge正文不误拒。 |
| 07-I3 | ≤3URL、共享deadline/计量主机限制，版本 | OA抽取真实回退、缓存 | PASS；OpenAlexDataSource.kt103–190；截止/第三地址后0额外请求。 |
| 07-I4 | M1–M3 | shared callers | PASS该片；总M2失败单列。 |
| 08-I1 | 完整已存原因分区，不相加/缺值不造0 | JS实际renderer、真实快照 | PASS；static/app.js2687–2719；08.json635/6/4/4、629/SEARCH_FAILED/HTTP_403323；有限非负数字。 |
| 08-I2 | 首选方式、两处日志身份未确认文案 | 捕获日志、DOM | PASS；ExpertDiscoveryService.kt909/1172；FULLTEXT_XML仍为默认首选，无新增实际方式统计。 |
| 08-I3 | 实时历史共用、补全分支保留、escape | JS1194/源码 | PASS；app.js2681–2687提前保留补全分支；外部来源/原因/停止文本转义。 |
| 08-I4/S1 | 业务保持和精确样式骨架/A2 | 范围与DOM/CSS | PASS机器；人工A4待验，不代勾。 |
| 09a-I1 | URI一次编码，原q/特殊字符还原 | 实际RestTemplate URI替身 | PASS；OrcidDataSource.kt102；OrcidDataSourceTest10、09a.json actualRequests。 |
| 09a-I2 | 同步/queue ORCID专用新hash、其他源不变 | codec/queueHash/旧offset隔离 | PASS；DiscoveryCheckpointCodec.kt55/77；ExpertDiscoveryService.kt1498；新首次null、第二次自身cursor。 |
| 09a-I3 | 保留旧stream/job，不迁移/清库 | 全局pipeline diff0、旧行断言 | PASS；旧ACTIVE仍可独立运行系明确保留的既有行为，见O-1。 |
| 09b-I1 | 单字段三层keyword，profile尾部null，投影 | mapping/启动补齐/三层读取 | PASS；ExpertProfile.kt42、ExpertSearchService.kt508/591；09b.json；三层mapping动态规则未动。 |
| 09b-I2 | 六类22/31/17/25/21/15唯一目录、保留高校 | catalog与分类正例 | PASS；SubjectScopeCatalog.kt26–32；immutableSet；现有查询集合未改。 |
| 09b-I3 | _source透传，既有operator guard不扩 | writer历史调用/diff+守卫 | PASS该片；guard文件零变更；实际09d新增约束错误另见V-2。 |
| 09c-I1 | 同top5规范field IDs，缺/非法整体null；三层局部写 | OA parser、layer输出 | PASS；OpenAlexDataSource.kt521–553；ExpertDiscoveryService.kt2664/2678；09c-layers/partial，null不擦旧、身份CAS，未新建缺层。 |
| 09c-I2 | 任一六类=相关、全非目标=范围外、无证据未知 | catalog+构造分类矩阵 | PASS；ExpertClassificationService.kt55–61；六类各一高校ACADEMIC_RND，混合保留。 |
| 09c-I3 | clinical优先，分数不替代范围，非discovery旧逻辑 | class29+真实/构造分级 | PASS；同文件52–75/108–112；Gebeyehu真实快照范围外/生产0；无IDs高分UNKNOWN；艺术/商务构造ID样本OUT_OF_SCOPE。 |
| 09c-I4 | rnd-v3、旧JSON可读、无发送版本门禁/自动回填 | guard、scheduler代码、diff | PASS；VERSION233；classification29、versionGate2；O-2发布前清点待办。 |
| 09d-I1 | discovery统一现算专业准入，基础邮箱验证保持 | 所有自动调用路径、独立probe | **FAIL V-1**；CandidateEligibilityService.kt54专业规则已接通；但revalidateDiscovery替代原gate后漏validate。promotionGateEnabled不绕过专业本身。 |
| 09d-I2 | 未知先RAW/入队/不重试页、同输入分类、真实promoted | 初始/worker实际结果 | PASS初始路径；ExpertDiscoveryService.kt1071–1090/1395–1423/1949；09d.json RAW1/CANDIDATE0/UNKNOWN/入队1。后续晋升的邮箱/真实_id缺陷见V-1/2。 |
| 09d-I3 | 补全复评先于Success、失败重试；真实RAW | 共享enrichProfiles→complete、真实probe | **FAIL V-2**（合法历史键永久WriteFailed）；常规路径次序正确：ExpertDiscoveryService.kt2844–2865→2528；09d worker先RetryableError后Success仅覆盖mock复评。 |
| 09d-I4 | 真实_id、身份事实CAS、只条件撤候选、404/409/500 | real writer/captured请求+probe | **FAIL V-2**；Writer.kt751把_id强等于源orcidId；其余snapshot/seq/term条件与候选DELETE正确，404幂等/409和500重试、RAW缺失不删。 |
| 09d-I5 | 不改发送、APPLICATION/历史不清洗、无隐藏门禁 | full diff、版本守卫 | PASS；APPLICATION只沿既有学术补全写事实，本次复评不删申请层/MySQL联系/邮件；未控制发送任务或部署。 |
| X1 | 同步cursor写→下次load→queue seed | 01+09a code/tests | PASS既定失败页与新ORCID key路径。 |
| X2 | parser→版本cache→consumer→pipeline终态 | 03–07/DiscoveryPipeline46 | PASS；旧缓存FAILED且不重抽，身份未知先拒绝。 |
| X3 | source error→5m defer、租约/暂停 | queue46、retry02 | PASS；队列没加同步即时重试。 |
| X4 | 来源身份→邮箱→去重→RAW→资格 | parser真实原文+150 consumer | 初始路径PASS；资格替身下旧解析验收数不可当最终真实专业晋升证据。 |
| X5 | 同邮箱重复不覆盖，真实_id补全 | replay+writer | **FAIL于新增复评接口 V-2**；原duplicate入队仍正确。 |
| X6 | adapter调用→reserve→UNKNOWN/defer | policy42、02实际请求 | PASS；没有退款/新增额度字段。 |
| X7 | SourceStats→实时/历史表/终态 | 08实际fixture+JS | PASS。 |
| X8 | PDF/HTML/CORE共享resolver，PMC/EPMC/OA共享JATS | 各parser回归/原文 | PASS；没有放开名字/邮箱拼写猜ID。 |
| X9-1 | q→集合→同步/queue来源key | 09a真实URI/offset | PASS；O-1不误报为新key继承旧offset。 |
| X9-2 | mapping→投影→profile→三层facts | 09b/09c实际层写 | PASS；缺层不创建。 |
| X9-3 | field IDs→classify→资格→可见类型 | 分类+资格+复评 | **FAIL V-1**于既有完整邮件验证保持；专业判断本身PASS。 |
| X9-4 | facts→资格→job终态/重试收敛 | writer+service+probe | **FAIL V-2**；常规路径顺序PASS，历史真实_id不能收敛。 |
| X9-5 | 条件撤候选→RAW保留→申请/联系/邮件不动 | CAS DELETE精确URL/边界 | **FAIL V-2**导致合法历史记录不能撤候选；未发现删除其他层。 |
| X9-6 | 分类→可见发送配置/候选查询 | guard/diff | PASS；不承诺已快照发送被撤销。 |
| A1–A6 | 主计划人工故障/原文/业务/视觉/缓存/专业闭环 | 用户人工结果 | PENDING；本次不产生签字、不把机器输出充作人工验收。 |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | 独立整体review首次发现；无prior aggregate finding/repair lineage。真实复评探针邮件验证调用0仍晋升。 |
| V-2 | NEW | 独立整体review首次发现；真实_id与源业务键不同即WriteFailed，身份凭证本身allowed。 |

子片历史01/04/09d的F编号不是本次aggregate lineage，未借用其LIGHT_PASS裁决。09d原F-01报告合并问题已通过本次fresh09d.json复查：一个文件含initialAdmission/worker/replica，无测试后手工拼接。

### Findings

#### P1

- **V-1 — discovery重新资格检查绕过既有完整邮箱验证。** master M2与09d需求保持项/I-1明确“保持邮箱验证、保留原基础检查”。`ES/ExpertRevalidationService.kt:339–347`只调用evaluateEligibility然后reconcile/Promoted；`CandidateEligibilityService.kt:27–31`仅格式/一次性域名，无法替代`EmailValidationService.validate`的缓存拒绝/MX检查。旧RAW gate在`ExpertRevalidationService.kt:308–313`且旧候选路径在`:63`调用validate。新`:49/:169`先进入discovery分支并continue，因此直接RAW自动晋升、候选复评、补全后复评均跳过该检查。探针使用当前真实service和真实资格服务，requireValidEmail=true，validator预置`NO_MX_RECORD`，实际validate调用0，仍返回Promoted。影响：此前验证过但当前已无效的发现邮箱仍能进入/留在候选；这超出仅专业准入变化授权。最小范围：ExpertRevalidationService及其行为回归测试，保持现有邮件验证服务实现。

- **V-2 — 把真实ES `_id` 与历史 `orcidId` 强等，阻断合法发现记录的复评和补全收敛。** 09d保持项/I-2/I-3/I-4要求按真实_id读取、create/删除；master M2与X5保留历史关联键。`ExpertRevalidationService.kt:334–335`、`ExpertIndexWriterService.kt:751–752`额外要求source.orcidId==docId；存储定位已是`GET /_doc/{docId}`，业务键无需相等。`DiscoveryIdentity.kt:7/45–59`明确身份凭证绑定学术身份，不绑定业务document ID。当前测试本身在`ExpertDiscoveryServiceTest.kt:4831–4852`使用合法发现画像`esDocId=OLD-DOC`与`orcidId=0000-0002-1825-0097`不同，却mock revalidateDiscovery为Rejected，因此掩盖真实路径。独立探针在合法证据上仅令源orcidId与实际DOC不同，allowedMap=true，实际revalidateDiscovery=WriteFailed。影响：已成功写回facts仍被映射RetryableError，自动晋升/候选撤下无法执行；重复重试不能消除恒定键差异，最终可耗尽任务次数。没有证据量化线上数量。最小范围：revalidation和writer两处守卫、对应service/writer测试；保留snapshot/学术身份/seq_no/primary_term检查，不改历史键。

#### P2

N/A（没有把证据格式豁免重新包装成阻断问题）。

#### Observations

- O-1：原ACTIVE ORCID stream仍可能独立按旧offset采集；`DiscoveryPipelineService.kt:704–708/793–843`遍历保留ACTIVE stream。09a明确保留旧stream/job、只隔离新key，不授权清理/停止历史流。因此为发布说明，非本轮P1；新stream本身从null开始。
- O-2：分类scheduler/backfill使用VERSION和onlyPending；rnd-v3使旧rnd-v2进入pending集合（若启用该既有scheduler）。09c明确需发布前清点，未授权开启/自动回填；没有这次调用线上回填的证据。
- O-3：普通doc资格更新返回noop时writer保守失败，未将这一点认定新P1；它与学术身份CAS noop不同。没有据猜测声称无限重试。repair不包含此项。
- O-4：fresh04–07的解析consumer验收继续使用计划允许的资格替身，故其中候选3/4/5/2是旧解析隔离场景结果，不代表最终真实专业准入的产量。09d另有真实资格初始未知RAW1/CANDIDATE0；缺可信作者ID者仍无法凭姓名补全。报告不把两种证据混算。

### Evidence Boundaries

- 全量构建/离线测试实际执行；13项既有条件集成测试跳过。未访问生产、不发送邮件、不检查线上批次、不运行迁移/清洗/部署。
- 源码完整写读路径追到consumer、writer、revalidation、enrichment job completion和UI。两项独立反例的外部服务为替身，证明控制流，不声称真实DNS/ES服务器故障已发生。
- 生产旧抽取版本活跃缓存、分类onlyPending数量、运行中发送/补全状态，依批准计划留待发布前只读清点。旧缓存不会自动重抽；无缓存删除授权。
- 原文哈希和样本真值不能外推全量恢复数量；DD08/DD10仍延期，DD14/19/20线上影响、DD18本次线上是否发生仍未知。
- 手工视觉/完整A1–A6没有执行者签字，全部PENDING；机器FAIL不能交付为READY_TO_INTEGRATE。
- 本次不改变HEAD/index/产品/测试/批准计划。控制器新建的review/ledger.md为允许证据，不是产品dirty。repair草案是本review-p流程唯一允许的计划写入。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Fresh evidence |
|---|---|---|---|
| 01 | I2/I5 | N/A | 原RECORD_ONLY为空；页边界/重放另行fresh核验。 |
| 02 | I2/I5 | N/A | 无RECORD_ONLY finding；同步retry与queue区别fresh核验。 |
| 03 | I1/I3 | N/A | 原N/A；原表达式/版本消费重新核验。 |
| 04（两epoch） | M1/X4 | N/A | 原N/A；实际XML/metadata原字节和3联系人重新核验。 |
| 05 | M1/I1 | N/A | 原N/A；真实PDF/共享4线索重新核验。 |
| 06 | M3/I3 | N/A | 原N/A；原62页/选页/mailto重新核验，action格式豁免。 |
| 07 | M1/X8 | N/A | 原N/A；3HTML2邮箱与挑战回退重新核验。 |
| 08 | I4/S1/A2 | N/A | 原N/A；最终635/6/4/4与11key重新核验。 |
| 09a O-1 | 09a-I2/I3、M4 | Observation保留 | pipeline旧ACTIVE流未改；新source hash/queue hash隔离已fresh执行。没有变成新游标继承或擅删旧流。 |
| 09b | I3/X9-2 | N/A | 原None；三层映射与投影fresh核验。 |
| 09c release note | 09c-I4/I5 | Observation保留 | 当前VERSION=rnd-v3，scheduler仍既有enabled=false默认与onlyPending版本筛选；发布前清点PENDING。 |
| 09d（两epoch） | M2/X9-4/X9-5 | 原RECORD_ONLY N/A；新增P1 V-1/V-2 | 原轻验结论不替代本次真实复评探针；合并09d.json已fresh生成。 |

### Next Action

FAIL + INITIAL → 按同一批准合同为V-1/V-2生成限定repair-p草案；禁止实现、合并或部署。

### Approved amendments — exact authority and file evidence

| ID | Authority / identity | Approved file/rule effects | Re-evaluation |
|---|---|---|---|
| A1 | HUMAN:我新增了 09 这个子计划 你读取一下 继续，2026-09-27T13:06:04+08:00；b8789cb6062d9110218c08ce8099dba8dddd73e4 → 152028fb4f6adf467a5254ed3627bf84c561f6bc | master第9/16/21行及I2/I3；新增09索引、09a/b/c/d与scope审计。ORCID URI/来源key、researchFieldIds三层事实、rnd-v3与discovery自动专业准入属于明确授权。 | git show实际差异已读；不把这些有意变化误报资格/schema越界；未豁免旧邮箱验证与真实_id。 |
| A2 | HUMAN:Bump all 11 keys，2026-09-27T15:31:33+08:00；b8789cb6062d9110218c08ce8099dba8dddd73e4 → 37e1ed05a5654735f6c763536c7a0a198b637965 | 08计划第64/80/84/108行修订现有11资源统一key；产品index.html11–15与2286–2291，共11处。 | 11键均20260926-discovery-repair；无新DOM/资源顺序变化；CSS diff0。 |

A1对应具体生产文件：OrcidDataSource.kt102；DiscoveryCheckpointCodec.kt55/77；ExpertDiscoveryService.kt1498/1949/2664/2678/2844；SubjectScopeCatalog.kt26；ExpertProfile.kt42；ExpertSearchService.kt508/591；三层mapping的researchFieldIds单行；ExpertClassificationService.kt55/108/233；CandidateEligibilityService.kt54；ExpertRevalidationService.kt49/169/325；ExpertIndexWriterService.kt695–787。相应授权测试/资源见完整逐文件附表。授权允许这些改变，具体实现仍须通过上述矩阵。

### Complete product/test/resource diff inventory

每行是实际Git差异文件、当前HEAD首个差异块行（新增文件为1）、授权计划及运行路径。文档差异另由身份/批准/amendment和既有fast-p证据处理，不把docs加入产品范围。

| File | First changed line | Authorized child | Runtime / evidence category |
|---|---:|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/config/PdfExtractionProperties.kt` | 12 | 06-pdf-coverage | PDF budget config |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` | 26 | 09b-scope-facts | discovery acquisition/parsing/queue |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt` | 196 | 04-xml-route | discovery acquisition/parsing/queue |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodec.kt` | 55 | 09a-orcid-query | discovery acquisition/parsing/queue |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 61 | 01-page-replay,02-search-retry,08-source-report,09a-orcid-query,09c-scope-classification,09d-scope-admission | discovery acquisition/parsing/queue |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParser.kt` | 44 | 04-xml-route | discovery acquisition/parsing/queue |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 28 | 04-xml-route,09c-scope-classification | discovery acquisition/parsing/queue |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt` | 15 | 09a-orcid-query | discovery acquisition/parsing/queue |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` | 1 | discovery-source-contact-recall,06-pdf-coverage | discovery acquisition/parsing/queue |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | 11 | discovery-source-contact-recall,06-pdf-coverage,07-html-contact | discovery acquisition/parsing/queue |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractor.kt` | 5 | 03-email-text | discovery acquisition/parsing/queue |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` | 7 | 03-email-text,discovery-source-contact-recall,07-html-contact | discovery acquisition/parsing/queue |
| `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` | 24 | 03-email-text,04-xml-route,discovery-source-contact-recall,06-pdf-coverage,07-html-contact | expert profile/admission/CAS |
| `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt` | 41 | 09b-scope-facts | expert profile/admission/CAS |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityService.kt` | 3 | 09d-scope-admission | expert profile/admission/CAS |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationService.kt` | 3 | 09c-scope-classification | expert profile/admission/CAS |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | 692 | 09d-scope-admission | expert profile/admission/CAS |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` | 4 | 09d-scope-admission | expert profile/admission/CAS |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 507 | 09b-scope-facts | expert profile/admission/CAS |
| `src/main/resources/application.yml` | 272 | 06-pdf-coverage | mapping / config |
| `src/main/resources/es/orcid_info_application.json` | 37 | 09b-scope-facts | mapping / config |
| `src/main/resources/es/orcid_info_candidate.json` | 26 | 09b-scope-facts | mapping / config |
| `src/main/resources/es/orcid_info_raw.json` | 25 | 09b-scope-facts | mapping / config |
| `src/main/resources/static/app.js` | 2690 | 08-source-report | UI shared renderer/cache |
| `src/main/resources/static/index.html` | 11 | 08-source-report | UI shared renderer/cache |
| `src/test/js/taskRecordsSemantics.test.js` | 275 | 08-source-report | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt` | 59 | 09b-scope-facts | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt` | 328 | 04-xml-route | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodecTest.kt` | 74 | 09a-orcid-query | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 52 | 01-page-replay,02-search-retry,03-email-text,04-xml-route,discovery-source-contact-recall,06-pdf-coverage,07-html-contact,08-source-report,09a-orcid-query,09c-scope-classification,09d-scope-admission | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParserTest.kt` | 577 | 04-xml-route | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 119 | 04-xml-route,07-html-contact,09c-scope-classification | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSourceTest.kt` | 16 | 09a-orcid-query | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | 31 | discovery-source-contact-recall,06-pdf-coverage,07-html-contact | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractorTest.kt` | 3 | 03-email-text | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | 8 | 03-email-text,discovery-source-contact-recall,07-html-contact | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt` | 14 | 09c-scope-classification | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` | 51 | 03-email-text,04-xml-route,discovery-source-contact-recall,06-pdf-coverage,07-html-contact | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityServiceTest.kt` | 9 | 09d-scope-admission | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationSchedulerTest.kt` | 103 | 09c-scope-classification | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationServiceTest.kt` | 5 | 09c-scope-classification | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt` | 169 | 09b-scope-facts | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` | 1058 | 09d-scope-admission | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt` | 28 | 09d-scope-admission | fresh assertions / acceptance output |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 2337 | 09b-scope-facts | fresh assertions / acceptance output |
| `src/test/resources/discovery/email-text-recall.json` | 1 | 03-email-text | fixture / SHA / provenance |
| `src/test/resources/discovery/html-contact-recall.md` | 1 | 07-html-contact | fixture / SHA / provenance |
| `src/test/resources/discovery/html-contact-recall.zip` | binary | 07-html-contact | fixture / SHA / provenance |
| `src/test/resources/discovery/pdf-contact-coverage.md` | 1 | 06-pdf-coverage | fixture / SHA / provenance |
| `src/test/resources/discovery/pdf-contact-coverage.zip` | binary | 06-pdf-coverage | fixture / SHA / provenance |
| `src/test/resources/discovery/rnd-scope-evidence.json` | 1 | 09c-scope-classification | fixture / SHA / provenance |
| `src/test/resources/discovery/source-contact-recall.md` | 1 | discovery-source-contact-recall | fixture / SHA / provenance |
| `src/test/resources/discovery/source-contact-recall.zip` | binary | discovery-source-contact-recall | fixture / SHA / provenance |
| `src/test/resources/discovery/task-20240-by-source.json` | 1 | 08-source-report | fixture / SHA / provenance |
| `src/test/resources/discovery/xml-route-recall.zip` | binary | 04-xml-route | fixture / SHA / provenance |

### Durable independent probe reproduction

下面探针只是/tmp离线诊断，未添加/修改仓库测试。它调用当前编译后的既有测试装配来获得真实service/eligibility，并只替换外部邮箱验证结果；既有断言确认第二次实际复评返回Promoted。V-2沿同一实例仅变源业务键、保留合法学术身份。writer为隔离替身，因此它证明service入口拒绝；writer第二个同类守卫由源码751行独立证实。

先执行报告的fresh全量构建，然后在指定保留工作树：

```python
from pathlib import Path
import xml.etree.ElementTree as ET
import subprocess
report = next(Path("target/surefire-reports").glob("TEST-*.xml"))
cp = next(p.attrib["value"] for p in ET.parse(report).findall(".//property")
          if p.attrib["name"] == "java.class.path")
jdk = "/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/"
# 将下方Java逐字保存为 /tmp/DiscoveryAggregateProbe.java。
subprocess.run([jdk + "javac", "-cp", cp, "/tmp/DiscoveryAggregateProbe.java"], check=True)
subprocess.run([jdk + "java", "-cp", cp + ":/tmp", "DiscoveryAggregateProbe"], check=True)
```

```java
import java.lang.reflect.*;
import org.mockito.Mockito;
import com.weibo.talentintroduction.expert.service.*;
import com.weibo.talentintroduction.expert.domain.*;
public class DiscoveryAggregateProbe {
 static Object field(Object obj,String name)throws Exception{ Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj); }
 public static void main(String[] args)throws Exception {
  Object test=Class.forName("com.weibo.talentintroduction.expert.service.ExpertRevalidationServiceBehaviorTest").getConstructor().newInstance();
  EmailValidationService email=(EmailValidationService)field(test,"emailValidationService");
  Mockito.when(email.validate("researcher@example.org")).thenReturn(new EmailValidationResult(0,false,"NO_MX_RECORD"));
  Method target=test.getClass().getDeclaredMethod("discovery revalidation admits only after current RAW has target research evidence");
  target.invoke(test);
  long validations=Mockito.mockingDetails(email).getInvocations().stream().filter(i->i.getMethod().getName().equals("validate")).count();
  Object filters=field(test,"filterService");
  boolean required=((EligibilityFilterService)filters).getCandidateFilter().getRequireValidEmail();
  System.out.println("V-1 requireValidEmail="+required+", configuredValidation=INVALID/NO_MX_RECORD, actualValidationCalls="+validations+", realRevalidationOutcome=Promoted (existing assertion passed)");
  ExpertIndexWriterService writer=(ExpertIndexWriterService)field(test,"writerService");
  ExpertIndexWriterService.DiscoverySnapshot existing=writer.readDiscoveryDocument(ExpertIndexLevel.RAW,"DOC");
  java.util.Map<String,Object> historical=new java.util.HashMap<>(existing.getSource());
  historical.put("orcidId","0000-0002-1240-1405");
  boolean proofAllowed=DiscoveryIdentity.INSTANCE.allowedMap(historical);
  Mockito.when(writer.readDiscoveryDocument(ExpertIndexLevel.RAW,"DOC")).thenReturn(new ExpertIndexWriterService.DiscoverySnapshot(historical,4,1));
  Object result=((ExpertRevalidationService)field(test,"service")).revalidateDiscovery("DOC");
  System.out.println("V-2 esDocId=DOC, storedOrcidId=0000-0002-1240-1405, identityAllowed="+proofAllowed+", outcome="+result.getClass().getSimpleName());
  if(!proofAllowed || !(result instanceof PromotionOutcome.WriteFailed))throw new AssertionError("V2 not reproduced");
  if(!required||validations!=0) throw new AssertionError("Probe preconditions did not reproduce");
 }
}
```

实际输出（exit 0，探针断言成功复现两个缺陷，不是产品验收PASS）：

```text
V-1 requireValidEmail=true, configuredValidation=INVALID/NO_MX_RECORD, actualValidationCalls=0, realRevalidationOutcome=Promoted (existing assertion passed)
V-2 esDocId=DOC, storedOrcidId=0000-0002-1240-1405, identityAllowed=true, outcome=WriteFailed
```

### Durable audit receipts

fresh脚本输出如下；没有以旧轻验输出替代：

```text
SCOPE authorized=56 changed=55 extra=0 DD=24
CHILD 01 2 within authorized scope
CHILD 02 2 within authorized scope
CHILD 03 8 within authorized scope
CHILD 04 10 within authorized scope
CHILD 05 10 within authorized scope
CHILD 06 10 within authorized scope
CHILD 07 10 within authorized scope
CHILD 08 6 within authorized scope
CHILD 09a 6 within authorized scope
CHILD 09b 9 within authorized scope
CHILD 09c 9 within authorized scope
CHILD 09d 8 within authorized scope
FIXTURE source-contact-recall.zip internalHashes 6 byteEqualOriginals 3
FIXTURE pdf-contact-coverage.zip internalHashes 9 byteEqualOriginals 4
FIXTURE html-contact-recall.zip internalHashes 8 byteEqualOriginals 4
FIXTURE XML hashes 14
ACCEPTANCE
01.json f41acd1ae2b2621e60e3c514026c3c8a15ded0f9997935b6ff5b01c23d74ea0e
02.json 183fc5a3376e026052407b49a2c3a7599c5e92de4b8cca174af582f9a6a79b30
03.json 241703261facf60718acecac265b487d86c9e47ec8ae189557cd2e87bd6d3e15
04.json 0f1beed6b7803161cfd6b003cc523c4c91c52bd50fe89d22769238e6122eba55
05.json 22619a9dd78ed4d9641a52637f84f8d36fe9c6cbb284d091d593e0baec98532e
06.json 2bdae13746b30f3dd493eb86bf9551ed0bf7773821f763c2135a14081be20dff
07.json 60d1e59687847c03d60e882f9d38229e41198d024e19364ae99cf22d40086935
08.html 9ea0edcca6719a7596bec659d0dcc389070f8cb4795e4dd264fb8f20866bc51d
08.json 7bf2121ddd8a3db883abfb5dba70c97edec2e5818ef518a6d7deb60e4cb3d1b4
09a.json ebefb8ba5c9a28844dd5a4bb949da961f71f2266a1cc9ae9d7aabadac4f09f26
09b.json 2bb9f7eefa42ab841298da550f011088f3d994f24ad221ef990449bd20bcd6ad
09c-classification.json 81aa878749ab21087d85b5a0edb0d9a06e1ff2d9d8f54105329564c98b5d9b2a
09c-layers.json 01bc49f5450d32c9de1bc234b3fe09764fb2483dc973601b9a30856be02f050c
09c-partial.json 3ba5e87d86f9379eb6abe0ca53af8eadf5ba081be671b8f8b98ed4578f7c0b25
09d.json 8be3ae23b972c38f0a6d894489992bc2b997edf3e2e4fbd1b5f5d471d1b440f4
SKIPPED com.weibo.talentintroduction.rag.RagFactAdminServiceTest 1
SKIPPED com.weibo.talentintroduction.auth.AuthFlowIntegrationTest 1
SKIPPED com.weibo.talentintroduction.rag.RagKnowledgeBaseTest 1
SKIPPED com.weibo.talentintroduction.mail.service.MeetingCalendarSendIntegrationTest 1
SKIPPED com.weibo.talentintroduction.campaign.service.MeetingCalendarServiceMysqlTest 1
SKIPPED com.weibo.talentintroduction.mail.controller.MailboxConversationControllerTest 1
SKIPPED com.weibo.talentintroduction.campaign.controller.MeetingCalendarControllerMysqlTest 1
SKIPPED com.weibo.talentintroduction.rag.RagLetterComposerTest 1
SKIPPED com.weibo.talentintroduction.discovery.service.EuropePmcDataSourceTest 1
SKIPPED com.weibo.talentintroduction.campaign.repository.FlywayMigrationIntegrationTest 1
SKIPPED com.weibo.talentintroduction.audit.repository.OperatorActionLogRepositoryTest 1
SKIPPED com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepositoryTest 1
SKIPPED com.weibo.talentintroduction.mail.controller.CalendarAttachmentIntegrationTest 1
JUNIT 266 {'tests': 4141, 'failures': 0, 'errors': 0, 'skipped': 13}
CSS_DIFF 0
CACHE_KEYS 11 {'20260926-discovery-repair'}
MAPPINGS 3 keyword/dynamic=false
```

## Repair Planning Result: DRAFT_READY

Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/2026-09-26/discovery-repair-00-master.md`
Verification result: FAIL / INITIAL
Repair artifact: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/fix/discovery-repair-00-master/repair.md`

### Included Findings

- V-1：恢复discovery完整邮箱验证。
- V-2：按真实_id复评并保留历史业务键。
- 合计4授权文件；两个根因任务；未扩大专业/发送/解析合同；附完整one-approval execution handoff与具体产品/docs提交subject。

### Excluded Findings

- O-1旧stream、O-2发布前清点、O-3未定性noop；DD08/DD10延期；流程格式豁免不纳入修复。

### Required Human Decision

- 批准当前限定repair草案；修复实施仍需明确人类授权。依据repair-p的“Execution is prohibited until the human explicitly approves this plan.”，本轮review授权只创建草案，不自动实施。

No implementation was performed.

To approve and execute this repair, send:
$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/fix/discovery-repair-00-master/repair.md

No product code was modified.

---

## Epoch 2 — post-repair aggregate/master

- Reviewer: `/root/aggregate_rereview_epoch2`；独立且无实施/轻验上下文，创建于修复代码提交之后；review-fast-p → review-p → verify-p。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`。
- Branch: `fast/2026-09-26-discovery-repair-00-master`。
- Governing master: `docs/plans/2026-09-26/discovery-repair-00-master.md`，记录提交 `152028fb4f6adf467a5254ed3627bf84c561f6bc`，SHA256 `0a40f221660ecc0008394126ae029e869c6078e725f2585cd2d55ba938be67fd`；CONSISTENT。
- Worktree resolution: DISCOVERED_FROM_GIT_WORKTREES；控制器提供的注册树定位已核对 branch/path/ancestry。历史 NONE 仅因 BLOCKED_PREFLIGHT / 非 READY，用户此次明确豁免 READY、三报告同提交和06 action格式；实质合同未豁免。未改 fast-p 或全局规则。
- Prior code: `4ad9e79b034798ee78f12c3285faf5882991b3bc`；new final_code_head: `8051fa894d96f065b8b9ef2b39463262b2cf8c50`；evidence HEAD: `039ac9bfc527b90659a3928dc82094f183301ab4`。
- Post-repair evidence mode: DURABLE_HANDOFF；`docs/plans/review/2026-09-26-discovery-repair-00-master/repair-execution.md`，READY_FOR_VERIFICATION。Executor: Main (`execute-p` invocation)。记录明确 human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/fix/discovery-repair-00-master/repair.md`。
- Exact repair identity: `docs/plans/fix/discovery-repair-00-master/repair.md` SHA256 `e2a4a5dbeb94037791020193c77d2a01b3a9ebe303bcd796db054696a21c9c86`；本轮未修改。
- 独立核对：base→prior→new→evidence祖先关系有效；new..HEAD只有repair-execution.md；repair累计产品差异仅4个获批文件；index/产品干净。控制器修改review/ledger.md是允许证据dirty。

## Verification Result: PASS

Plan: `docs/plans/2026-09-26/discovery-repair-00-master.md`，A1/A2后合同，phase aggregate/master。
Implementation boundary: `64c0394a940bd79c2ecc04e5c497650f045faa75..8051fa894d96f065b8b9ef2b39463262b2cf8c50`
Convergence: PROGRESSING
Manual acceptance: PENDING

V-1、V-2均RESOLVED；未发现新必修缺陷。已重新读取主计划、12份有序子计划、主/09审计、全部组合生产diff、测试变更与48份child证据；机器裁决来自本Epoch新执行结果，未以executor或child通过声明代替测试。

以下 `DS` 为 `src/main/kotlin/com/weibo/talentintroduction/discovery/service/`，`ES` 为 `src/main/kotlin/com/weibo/talentintroduction/expert/service/`；源码/测试相对路径均以本报告保留工作树为根。

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | fresh exit0；2026-09-27T19:24:01+08:00完成，04:54；JUnit4149 tests /0 failures /0 errors /13 skipped，266 XML；Maven-bound Node1194 tests /235 suites /1194 pass /0 fail /0 skipped；两个JS语法门禁通过、WAR完成。完整日志 `/tmp/discovery-aggregate-epoch2-build.log`。 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertRevalidationServiceBehaviorTest,ExpertIndexWriterServiceTest,ExpertRevalidationServiceTest,CandidateEligibilityServiceTest,CandidateEligibilityServiceEnhancedTest,ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentJobServiceTest,ExpertAcademicEnrichmentWorkerTest,OperatorStatusWriteSeamGuardTest,ExpertClassificationVersionGateGuardTest` | PASS | fresh exit0；2026-09-27T19:31:42+08:00完成，02:57；JUnit279 tests /0 failures /0 errors /0 skipped，10指定类；Maven-bound Node1194 tests /235 suites /1194 pass /0 fail /0 skipped，两个JS语法门禁通过。日志 `/tmp/discovery-aggregate-epoch2-targeted-resume.log`，退出码回执 `/tmp/discovery-aggregate-epoch2-targeted.exit`。 |
| `git diff --check`；`git diff --check 64c0394a940bd79c2ecc04e5c497650f045faa75 8051fa894d96f065b8b9ef2b39463262b2cf8c50 -- src` | PASS | exit0，无空白错误；scope审计55个src变更均获批，repair仅4文件。 |
| `python3 /tmp/discovery-aggregate-epoch2-audit.py` | PASS | fresh exit0；55⊆56，24唯一DD；12片各自范围合规；7原PDF/4原HTML逐字等于诊断归档；XML14哈希；CSS diff0；11cache key同值；三层keyword/dynamic=false。源码及回执见附录。 |
| JDK11 `javac -cp [fresh Surefire java.class.path] /tmp/DiscoveryAggregateEpoch2Probe.java`；同CP加`:/tmp`执行该类 | PASS | compile exit0 / run exit0；9个独立场景，实际revalidator+eligibility+writer，外部HTTP/邮箱替身；NO_MX拒绝、合法历史键create/保留、异常/409/500重试、404幂等、身份变化不删、缺RAW不删。完整源码及输出见附录。 |
| JDK11 同CP编译/执行 `DiscoveryAggregateCancellationProbe` | PASS | compile exit0 / run exit0；取消后succeeded0/complete调用0；旧token及CAS失效均拒绝完成。源码/输出见附录。 |
| `git merge-base --is-ancestor`（base→new、prior→new、new→HEAD）；SHA256复算；`git diff --name-only`（全边界、repair、new..HEAD）；12child base/code差异 | PASS | exit0；身份及范围与本报告头部一致，无产品/index修改。 |

定向首次会话在usage limit中断时止于test-compile，没有成功终态；恢复后原session已不存在，提升权限的只读ps确认保留树无Maven，故仅恢复这一未完成命令。全量构建/已完成探针未重复，不将中断日志算通过。主树其他Maven使用不同target，不是本评审证据。

关键fresh全量类计数（除注明项均0失败/错误/跳过）：ExpertDiscoveryService150；DiscoveryCheckpointCodec11；DiscoveryResult7；DiscoveryPipeline46；Orcid10；OpenAlex73；OpenAlexRequestPolicy42；Core19；Jats35；Pdf39；PlainText6；SourceAuthorResolver15；DiscoveryIdentity7；SubjectScope8；Classification29；IndexService10；Search70；IndexWriter39；RevalidationBehavior21；Revalidation27；Eligibility7+Enhanced18；EnrichmentJob4；EnrichmentWorker10；OperatorGuard1；VersionGuard2。EuropePmc30中1条件跳过。13跳过套件全部列在审计回执，不声称真实生产服务/数据库已验收。

### Contract Matrix

机器项均mandatory；每行列明冻结要求、需要的证据、fresh结论。相同源码行未变不代表沿用旧PASS；本Epoch重新检查调用路径并执行全量。人工仅PENDING。

| ID | Requirement / Source | Required evidence | Verdict / Evidence |
|---|---|---|---|
| master-I-1 | 24 DD覆盖；真实/构造分开；DD08/10不关闭 | scope、原字节、原始SHA、真实解析 | PASS；两个scope清单联合24唯一DD；7原PDF/4原HTML逐字比对，XML14哈希；05四对/06五对/04三对/07三论文两邮箱。DD08原文长期存储、DD10上游错配仍未解决，影响人数未知。 |
| master-I-2 | ≤10文件/≤2子系统、共享文件依序 | 各child真实Git边界、授权清单 | PASS；12片2/2/8/10/10/10/10/6/6/9/9/8，无越界；联合55⊆56。09前置/修复提交链及抽取版本递增保留。 |
| master-I-3 | 限定schema/key/version/config变化 | 全量产品diff、三层映射、缓存测试 | PASS；只有researchFieldIds三层keyword、ORCID专属hash、rnd-v3、tailPages；无DB migration；VERSION20260925不变，EXTRACTION_VERSION20261001。 |
| master-I-4 | 真实最终635/6/4/4、629与SEARCH_FAILED；首选方式 | 实际renderer、最终fixture | PASS；`static/app.js:2681`；fresh08.json/08.html；共用实时/历史函数，未重算嵌套失败数。 |
| master-I-5 | 独立记录、全量JDK11、缓存兼容、未上线 | child记录、fresh全量、版本消费 | PASS（机器部分）；本次build事实如上，03–07 versionCases旧缓存拒绝，未自动重抽；人工/发布前清点待办。 |
| S-1/A2 | 八列DOM、复用class、无inline/CSS变更、11键 | 08批准骨架、JS DOM断言、diff | PASS（机器）；app.js2687–2719；index.html11–15/2286–2291，11键同值；styles.css零差异。滚动/字体/details实际人工视觉=PENDING。 |
| M1 | 唯一同源身份、冲突未知、不拼ID | resolver/JATS实际正负例、消费防线 | PASS；DS/SourceAuthorEmailResolver.kt158、JatsXmlEmailParser.kt132、OpenAlexDataSource.kt193；共享/同名/縮写冲突均拒绝；原文真值未改。 |
| M2 | 邮箱验证保持；仅09专业资格变更 | 自动晋升/候选复评实际调用 | PASS；完整validate恢复于ES/ExpertRevalidationService.kt341–345；BehaviorTest286–399两自动入口/关闭/异常；独立9场景探针实际validate=1，NO_MX不create且已有候选CAS撤下。初始consumer原验证/create/重复保持。 |
| M3 | 暂停、预算、大小、时限 | retry/cancel/queue/limits现有测试 | PASS于规定场景；DS/ExpertDiscoveryService.kt2055、OpenAlexDataSource.kt75、PdfEmailExtractor.kt110/296；不把apiRequests当计费credit。 |
| M4 | 禁生产清洗、部署、发送配置/迁移/新增自动化 | diff和本次操作边界 | PASS；只运行离线构建/探针，未联网业务或改生产；仅写临时评审报告；本轮未改repair。 |
| 01-I1 | 两同步循环dedup/RAW/enqueue失败页保留 | cursor矩阵、重放 | PASS；DS/ExpertDiscoveryService.kt810–836/1120–1158；150测试中dedup500、ORCID、组合故障/重放；fresh01.json ACTIVE、DEDUP_INCOMPLETE、PARTIAL_SUCCESS。 |
| 01-I2 | 原因优先级、取消与空结果终态 | stats/checkpoint/outcome | PASS于既定用例；上述路径与DiscoveryResultTest7；RAW>enqueue>dedup，队列dedup原规则。 |
| 01-I3 | 同邮箱重放幂等、真实_id补队列 | consumer+writer/replay | PASS；DS/ExpertDiscoveryService.kt recordIdentityDuplicate与Test5343；重放create不覆写；修复后历史真实_id复评由独立探针确认。 |
| 01-I4 | 未知/无效/拒绝/重复等原边界 | consumer真实输出、外部替身 | PASS；实际unknown验证0/RAW0，invalid RAW0，ineligible RAW1/CANDIDATE0；同名/双邮箱独立记录；09未知RAW另行复验。 |
| 02-I1 | 同游标≤3；暂时网络/HTTP白名单；TLS永久错误拒绝 | 请求次数/异常序列 | PASS；DS/ExpertDiscoveryService.kt664–746/2078；Tests3902–4034。 |
| 02-I2 | 1s/2s+≤200ms，≤100ms取消/deadline；429/额度延期 | 重试等待与policy | PASS；同文件2055–2076、685–706；Tests4035–4143。 |
| 02-I3 | 成功才消费、每尝试reserve、UNKNOWN不退款、队列5m不叠加 | policy+queue+计数 | PASS；OpenAlexDataSource.kt75–89；policy42/pipeline46；02.json两次C1、papers=1、sourceFailureCount=0。 |
| 02-I4 | 原消费/发送边界 | 共享consumer、diff | PASS；初始consumer与修复后自动复评保持邮箱/身份/去重语义；发送diff0。 |
| 03-I1 | 7brace表达式30展开项、断行、非法/跨段拒绝 | 逐表达式来源与实际extract | PASS已规定夹具；PlainTextEmailExtractor.kt24–55；fresh03.json真实7组30项及kairouz；无名不入RAW。 |
| 03-I2 | 同源规范化、原文本哈希、不开跨段猜姓名 | resolver联系记录与反例 | PASS；SourceAuthorEmailResolver.kt109–155；原/规范化pair保留，Jane两个写法及共享负例。 |
| 03-I3 | 抽取版本/证据版本独立、旧缓存失败 | Git版本链、versionCases | PASS；最终EXTRACTION_VERSION20261001；旧各版20260926–30受拒，pipeline FAILED，无自动清缓存。 |
| 03-I4 | M1–M3 | parser→consumer实际链 | PASS；parser→consumer边界、完整邮箱验证及发送/暂停约束由fresh全量与独立探针共同核验。 |
| 04-I1 | 可信PMC主机/完整路径/冲突拒绝，无新映射请求 | 实际OA metadata→XML | PASS；OpenAlexDataSource.kt264–288；04.json PMC7759461，首XML，一次metadata/一次XML。 |
| 04-I2 | 逗号窄拆分、完整name-alternatives、重复contrib冲突 | 实际adapter/JATS | PASS；OpenAlexDataSource.kt290、CoreDataSource.kt207、JatsXmlEmailParser.kt164–178；Jan Jakubův与合成反例。 |
| 04-I3 | 直接数字label、唯一xref、contrib-info有界 | 实际XML3真联系人/负例 | PASS；JatsXmlEmailParser.kt115–142/196–205；04.json三对逐项吻合；不凭姓名补OA ID。 |
| 04-I4 | 唯一ORCID才补ID、旧缓存不兼容 | OA193、版本用例 | PASS；真实XML三人无可信作者ID，不伪造ID；历史资格替身下consumer数字不等于最终真实资格晋升。 |
| 04-I5 | M1–M3 | 共享consumer边界 | PASS；初始consumer、复评完整邮箱、create/重复不覆盖；无新增发送/迁移。 |
| 05-I1 | 唯一标记/作者锚点、跨栏/同名/共享拒绝 | 原PDFBox与负例 | PASS；PdfAuthorContactLayout.kt38–88/124–168；05.json Klaus/Davide/Shirui/Philip四对，另外四共享仍未知。 |
| 05-I2 | 姓+全部首字母唯一、Email标记、页/原片段哈希 | layout/resolver+真实PDF | PASS；layout71–90/154–179；原三篇PDF字节比对成功。 |
| 05-I3 | claims统一冲突，CORE/HTML不借PDF推断；版本 | actual shared callers、版本case | PASS；SourceAuthorEmailResolver.kt35–46/158–169；单PDDocument生命周期；不重复下载。 |
| 05-I4 | M1–M3 | 共享负例和消费 | PASS该片；09专业准入及完整邮箱独立检查。 |
| 06-I1 | 前maxPages∪末1、0/1配置、同deadline、10MiB | 原62页、配置/选页测试 | PASS；PdfExtractionProperties.kt12–23；PdfEmailExtractor.kt290–331；06.json [1,2,62]/[1]/[1,2]/tail0；application.yml272。 |
| 06-I2 | 已选页mailto只线索，query/cc/bcc不增作者 | 实际注释/去重/错配 | PASS；PdfEmailExtractor.kt311–318；11目标入集合，W4292779060两线索身份空；04–07同邮箱去重。 |
| 06-I3 | 明确末页作者块五对、不能正文猜/错配绑 | 原62页PDFBox+元数据 | PASS；PdfAuthorContactLayout.kt95–121；fresh06.json五对匹配，完整原PDF未裁页。 |
| 06-I4 | M1–M3 | 边界与资源预算 | PASS该片；09不抹去原解析真值。 |
| 07-I1 | 对应标题/list具名mailto、唯一metadata、冲突消解 | 三原HTML/URI负例 | PASS；SourceAuthorEmailResolver.kt23–27/80–106；3论文/2邮箱，Vijay/Iqbal，重复计1；+保留、CRLF/多收件人拒绝。 |
| 07-I2 | Anubis标题+script、空可见HTML无效；正常无邮箱成功 | 实际挑战页/回退请求 | PASS；PdfEmailExtractor.kt162–181；fresh07.json与OpenAlex73/Pdf39；普通challenge正文不误拒。 |
| 07-I3 | ≤3URL、共享deadline/计量主机限制，版本 | OA抽取真实回退、缓存 | PASS；OpenAlexDataSource.kt103–190；截止/第三地址后0额外请求。 |
| 07-I4 | M1–M3 | shared callers | PASS；共享consumer边界与修复后完整邮箱验证；无新增身份猜测/发信逻辑。 |
| 08-I1 | 完整已存原因分区，不相加/缺值不造0 | JS实际renderer、真实快照 | PASS；static/app.js2687–2719；08.json635/6/4/4、629/SEARCH_FAILED/HTTP_403323；有限非负数字。 |
| 08-I2 | 首选方式、两处日志身份未确认文案 | 捕获日志、DOM | PASS；ExpertDiscoveryService.kt909/1172；FULLTEXT_XML仍为默认首选，无新增实际方式统计。 |
| 08-I3 | 实时历史共用、补全分支保留、escape | JS1194/源码 | PASS；app.js2681–2687提前保留补全分支；外部来源/原因/停止文本转义。 |
| 08-I4/S1 | 业务保持和精确样式骨架/A2 | 范围与DOM/CSS | PASS机器；人工A4待验，不代勾。 |
| 09a-I1 | URI一次编码，原q/特殊字符还原 | 实际RestTemplate URI替身 | PASS；OrcidDataSource.kt102；OrcidDataSourceTest10、09a.json actualRequests。 |
| 09a-I2 | 同步/queue ORCID专用新hash、其他源不变 | codec/queueHash/旧offset隔离 | PASS；DiscoveryCheckpointCodec.kt55/77；ExpertDiscoveryService.kt1498；新首次null、第二次自身cursor。 |
| 09a-I3 | 保留旧stream/job，不迁移/清库 | 全局pipeline diff0、旧行断言 | PASS；旧ACTIVE仍可独立运行系明确保留的既有行为，见O-1。 |
| 09b-I1 | 单字段三层keyword，profile尾部null，投影 | mapping/启动补齐/三层读取 | PASS；ExpertProfile.kt42、ExpertSearchService.kt508/591；09b.json；三层mapping动态规则未动。 |
| 09b-I2 | 六类22/31/17/25/21/15唯一目录、保留高校 | catalog与分类正例 | PASS；SubjectScopeCatalog.kt26–32；immutableSet；现有查询集合未改。 |
| 09b-I3 | _source透传，既有operator guard不扩 | writer历史调用/diff+守卫 | PASS；_source透传；OperatorStatusWriteSeamGuardTest文件diff0且本次通过；历史_id不再与业务orcidId强等。 |
| 09c-I1 | 同top5规范field IDs，缺/非法整体null；三层局部写 | OA parser、layer输出 | PASS；OpenAlexDataSource.kt521–553；ExpertDiscoveryService.kt2664/2678；09c-layers/partial，null不擦旧、身份CAS，未新建缺层。 |
| 09c-I2 | 任一六类=相关、全非目标=范围外、无证据未知 | catalog+构造分类矩阵 | PASS；ExpertClassificationService.kt55–61；六类各一高校ACADEMIC_RND，混合保留。 |
| 09c-I3 | clinical优先，分数不替代范围，非discovery旧逻辑 | class29+真实/构造分级 | PASS；同文件52–75/108–112；Gebeyehu真实快照范围外/生产0；无IDs高分UNKNOWN；艺术/商务构造ID样本OUT_OF_SCOPE。 |
| 09c-I4 | rnd-v3、旧JSON可读、无发送版本门禁/自动回填 | guard、scheduler代码、diff | PASS；VERSION233；classification29、versionGate2；O-2发布前清点待办。 |
| 09d-I1 | discovery统一现算专业准入，基础邮箱验证保持 | 所有自动调用路径、独立probe | PASS；CandidateEligibilityService.kt54现算专业；Revalidation.kt49/169/341统一入口保留完整邮箱验证。独立NO_MX拒绝及关闭语义测试通过，promotionGateEnabled不绕专业。 |
| 09d-I2 | 未知先RAW/入队/不重试页、同输入分类、真实promoted | 初始/worker实际结果 | PASS；DS1071–1090/1395–1423/1949；fresh09d初始RAW1/CANDIDATE0/UNKNOWN/入队1；独立真实writer成功create仅1次，已有候选put0。 |
| 09d-I3 | 补全复评先于Success、失败重试；真实RAW | 共享enrichProfiles→complete、真实probe | PASS；DS2844–2865→2526先复评再complete；历史OLD-DOC正确完成；异常/409/500为WriteFailed→RetryableError；取消probe complete0；JobService80与Repository153 token CAS拒旧任务。 |
| 09d-I4 | 真实_id、身份事实CAS、只条件撤候选、404/409/500 | real writer/captured请求+probe | PASS；Writer.kt749–787 RAW快照/身份、candidate身份、两CAS、post-update RAW seq/term；独立probe验证OLD-DOC create/delete与历史orcidId不变、404幂等、409/500重试、身份变化/缺RAW不删。 |
| 09d-I5 | 不改发送、APPLICATION/历史不清洗、无隐藏门禁 | full diff、版本守卫 | PASS；APPLICATION只沿既有学术补全写事实，本次复评不删申请层/MySQL联系/邮件；未控制发送任务或部署。 |
| X1 | 同步cursor写→下次load→queue seed | 01+09a code/tests | PASS既定失败页与新ORCID key路径。 |
| X2 | parser→版本cache→consumer→pipeline终态 | 03–07/DiscoveryPipeline46 | PASS；旧缓存FAILED且不重抽，身份未知先拒绝。 |
| X3 | source error→5m defer、租约/暂停 | queue46、retry02 | PASS；队列没加同步即时重试。 |
| X4 | 来源身份→邮箱→去重→RAW→资格 | parser真实原文+150 consumer | PASS；真实原文parser→身份→完整邮箱→去重→RAW→资格；解析隔离资格替身计数不等于最终真实专业产量；09未知与补全分别有真实资格证据。 |
| X5 | 同邮箱重复不覆盖，真实_id补全 | replay+writer | PASS；重放不覆盖；真实OLD-DOC进入复评、RAW局部update、候选create/CAS delete；独立probe证明历史业务键保留。 |
| X6 | adapter调用→reserve→UNKNOWN/defer | policy42、02实际请求 | PASS；没有退款/新增额度字段。 |
| X7 | SourceStats→实时/历史表/终态 | 08实际fixture+JS | PASS。 |
| X8 | PDF/HTML/CORE共享resolver，PMC/EPMC/OA共享JATS | 各parser回归/原文 | PASS；没有放开名字/邮箱拼写猜ID。 |
| X9-1 | q→集合→同步/queue来源key | 09a真实URI/offset | PASS；O-1不误报为新key继承旧offset。 |
| X9-2 | mapping→投影→profile→三层facts | 09b/09c实际层写 | PASS；缺层不创建。 |
| X9-3 | field IDs→classify→资格→可见类型 | 分类+资格+复评 | PASS；可信field IDs→实时分类→专业资格+完整邮箱→RAW资格/候选；Classification29、Eligibility7+18、Behavior21、probe9。 |
| X9-4 | facts→资格→job终态/重试收敛 | writer+service+probe | PASS；三层事实→资格写→完成次序DS2844/2526；fresh worker RetryableError→Success及真实复评失败探针；取消/旧token不成功。 |
| X9-5 | 条件撤候选→RAW保留→申请/联系/邮件不动 | CAS DELETE精确URL/边界 | PASS；Writer749–787仅候选CAS删除，RAW只3资格键，APPLICATION保留；独立HTTP记录与existing application test证明，不触MySQL。 |
| X9-6 | 分类→可见发送配置/候选查询 | guard/diff | PASS；不承诺已快照发送被撤销。 |
| A1–A6 | 主计划人工故障/原文/业务/视觉/缓存/专业闭环 | 用户人工结果 | PENDING；本次不产生签字、不把机器输出充作人工验收。 |
| R-1/V-1 | 恢复完整邮箱，拒绝/成功/异常/关闭配置 | 真实复评、自动入口、原因/候选写请求 | PASS；Revalidation341–345；Behavior286–399；Probe NO_MX validate1/put0，Rejected并写EMAIL:NO_MX_RECORD；exception WriteFailed无候选；关闭不调用validate。 |
| R-2/V-2 | 真实_id与历史业务键解耦，保持身份/CAS | actual writer+revalidator、历史键/运营字段快照 | PASS；Revalidation334取消业务键等同；Writer749不再要求orcidId==docId；Probe OLD-DOC create/delete成功，HISTORICAL-ORCID/proof/PAUSED保留；409/500/变化/缺RAW保护。 |
| Repair-scope | 仅4获批文件，无隐含扩项 | prior..new产品逐文件diff | PASS；2生产+2测试；完整清单见下。 |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | 原绕过完整邮箱根因被341–345消除；真实资格/复评/writer探针NO_MX必须validate1，返回Rejected、RAW原因准确、put0；自动RAW/候选入口、异常和关闭测试本轮通过。 |
| V-2 | RESOLVED | 两处source.orcidId==docId约束移除；OLD-DOC实际HTTP create/CAS delete均成功，历史业务键和proof保持；身份变化/缺RAW不删，409/500不可成功。 |

Convergence=PROGRESSING：阻断集合由{V-1,V-2}变为空，没有同级新回归；不是仅改变行号或重复同一失败证据。

### Findings

#### P1

N/A。

#### P2

N/A。

#### Observations

- O-1保留：DS/DiscoveryPipelineService.kt704–708/793–813仍迭代保留ACTIVE stream；09a-I3明确保留旧stream/job，修的是新key隔离，不授权停/删历史流。新ORCID同步/queue hash首次不继承旧offset，fresh09a实际URI/请求证明。
- O-2保留：ExpertClassificationScheduler.kt34/52–57默认关闭；VERSION=rnd-v3使旧版进入onlyPending选择。依09c-I4/I5留待发布前清点；本次未启用或触发回填。
- O-3保留：Writer资格普通doc update noop保守返回false；与学术身份CAS noop不同。本轮未证实必修缺陷，也未扩项改变该语义。
- O-4保留：04–07解析consumer产物使用计划许可的邮箱/资格替身；3/4/5/2是隔离解析写入结果，不能当最终专业准入产量。09d另验真实资格未知先RAW；有可信ID才可补全，不能拼姓名猜作者ID。
- DD-08原文长期存储、DD-10上游错配质量仍未解决；DD14/19/20真实影响、DD18线上发生与恢复人数不外推。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Mandatory contract mapping | Fresh decision/evidence |
|---|---|---|
| 01/02 | I2/I5、X1/X3/X6 | 原记录无实质RECORD_ONLY；fresh页回放/有限重试/队列延期通过。 |
| 03/04/05/06/07 | I1/I3、M1/M3、X2/X4/X8 | 原记录N/A；原字节/解析/旧cache拒绝重新检查；06格式豁免仅流程。 |
| 08 | I4/S1、A2、X7 | 原N/A；真实最终635/6/4/4、629/SEARCH_FAILED、11键/CSS0通过。 |
| 09a O-1 | 09a-I2/I3、M4 | Observation；旧ACTIVE独立运行是明确保留，未当新key继承；pipeline无改动。 |
| 09b | I3/X9-2 | 原None；三层mapping/projection/null/source透传fresh通过。 |
| 09c release | 09c-I4/I5 | Observation；版本onlyPending影响存在，发布前只读清点PENDING；本次无回填。 |
| 09d | M2、X9-4/X9-5 | 原RECORD_ONLY N/A；Epoch1 V-1/V-2本Epoch独立实证RESOLVED，不沿用轻验裁决。 |

### Approved Amendments and Per-file Scope

A1：HUMAN:我新增了 09 这个子计划 你读取一下 继续，2026-09-27T13:06:04+08:00；b8789cb6062d9110218c08ce8099dba8dddd73e4→152028fb4f6adf467a5254ed3627bf84c561f6bc。主规则I-2/I-3和09索引，授权09a→09d ORCID专用查询key、单一researchFieldIds事实、rnd-v3及discovery专业准入。A2：HUMAN:Bump all 11 keys，2026-09-27T15:31:33+08:00；08计划37e1ed05a5654735f6c763536c7a0a198b637965；S-1/I-2，11现有资源key统一，不新增DOM或CSS。

以下每个实际变化文件均独立列出授权来源；09文件以A1对应规则判定，index.html以A2判定。没有未批准追认文件。

| File | Authorized plan/amendment | Verdict / evidence role |
|---|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/config/PdfExtractionProperties.kt` | discovery-repair-06-pdf-coverage | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` | discovery-repair-09b-scope-facts (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt` | discovery-repair-04-xml-route | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodec.kt` | discovery-repair-09a-orcid-query (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | discovery-repair-01-page-replay, discovery-repair-02-search-retry, discovery-repair-08-source-report, discovery-repair-09a-orcid-query, discovery-repair-09c-scope-classification, discovery-repair-09d-scope-admission (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParser.kt` | discovery-repair-04-xml-route | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | discovery-repair-04-xml-route, discovery-repair-09c-scope-classification (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt` | discovery-repair-09a-orcid-query (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` | discovery-source-contact-recall, discovery-repair-06-pdf-coverage | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | discovery-source-contact-recall, discovery-repair-06-pdf-coverage, discovery-repair-07-html-contact | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractor.kt` | discovery-repair-03-email-text | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` | discovery-repair-03-email-text, discovery-source-contact-recall, discovery-repair-07-html-contact | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` | discovery-repair-03-email-text, discovery-repair-04-xml-route, discovery-source-contact-recall, discovery-repair-06-pdf-coverage, discovery-repair-07-html-contact | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt` | discovery-repair-09b-scope-facts (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityService.kt` | discovery-repair-09d-scope-admission (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationService.kt` | discovery-repair-09c-scope-classification (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | discovery-repair-09d-scope-admission (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` | discovery-repair-09d-scope-admission (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | discovery-repair-09b-scope-facts (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/resources/application.yml` | discovery-repair-06-pdf-coverage | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/resources/es/orcid_info_application.json` | discovery-repair-09b-scope-facts (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/resources/es/orcid_info_candidate.json` | discovery-repair-09b-scope-facts (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/resources/es/orcid_info_raw.json` | discovery-repair-09b-scope-facts (A1 I2/I3) | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/resources/static/app.js` | discovery-repair-08-source-report | PASS；source runtime inspected; fresh mandatory suites |
| `src/main/resources/static/index.html` | discovery-repair-08-source-report (A2 S1/I2) | PASS；source runtime inspected; fresh mandatory suites |
| `src/test/js/taskRecordsSemantics.test.js` | discovery-repair-08-source-report | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt` | discovery-repair-09b-scope-facts (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt` | discovery-repair-04-xml-route | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodecTest.kt` | discovery-repair-09a-orcid-query (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | discovery-repair-01-page-replay, discovery-repair-02-search-retry, discovery-repair-03-email-text, discovery-repair-04-xml-route, discovery-source-contact-recall, discovery-repair-06-pdf-coverage, discovery-repair-07-html-contact, discovery-repair-08-source-report, discovery-repair-09a-orcid-query, discovery-repair-09c-scope-classification, discovery-repair-09d-scope-admission (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParserTest.kt` | discovery-repair-04-xml-route | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | discovery-repair-04-xml-route, discovery-repair-07-html-contact, discovery-repair-09c-scope-classification (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSourceTest.kt` | discovery-repair-09a-orcid-query (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | discovery-source-contact-recall, discovery-repair-06-pdf-coverage, discovery-repair-07-html-contact | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractorTest.kt` | discovery-repair-03-email-text | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | discovery-repair-03-email-text, discovery-source-contact-recall, discovery-repair-07-html-contact | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt` | discovery-repair-09c-scope-classification (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` | discovery-repair-03-email-text, discovery-repair-04-xml-route, discovery-source-contact-recall, discovery-repair-06-pdf-coverage, discovery-repair-07-html-contact | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityServiceTest.kt` | discovery-repair-09d-scope-admission (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationSchedulerTest.kt` | discovery-repair-09c-scope-classification (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationServiceTest.kt` | discovery-repair-09c-scope-classification (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt` | discovery-repair-09b-scope-facts (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` | discovery-repair-09d-scope-admission (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt` | discovery-repair-09d-scope-admission (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | discovery-repair-09b-scope-facts (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/resources/discovery/email-text-recall.json` | discovery-repair-03-email-text | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/resources/discovery/html-contact-recall.md` | discovery-repair-07-html-contact | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/resources/discovery/html-contact-recall.zip` | discovery-repair-07-html-contact | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/resources/discovery/pdf-contact-coverage.md` | discovery-repair-06-pdf-coverage | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/resources/discovery/pdf-contact-coverage.zip` | discovery-repair-06-pdf-coverage | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/resources/discovery/rnd-scope-evidence.json` | discovery-repair-09c-scope-classification (A1 I2/I3) | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/resources/discovery/source-contact-recall.md` | discovery-source-contact-recall | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/resources/discovery/source-contact-recall.zip` | discovery-source-contact-recall | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/resources/discovery/task-20240-by-source.json` | discovery-repair-08-source-report | PASS；assertions/fixture read; fresh build/manifest checks |
| `src/test/resources/discovery/xml-route-recall.zip` | discovery-repair-04-xml-route | PASS；assertions/fixture read; fresh build/manifest checks |

Repair Authorized Files穷尽：`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt`、`ExpertIndexWriterService.kt`；对应 `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt`、`ExpertIndexWriterServiceTest.kt`。prior..new除此之外只有review/repair证据docs；未扩大产品范围。

### Evidence Boundaries

- 全量build、定向测试与离线探针为本Epoch实际执行；13项条件测试跳过，未声称真实MySQL/Docker/ES/DNS或线上来源全部执行。外部替身证明控制流/请求语义，未访问生产或发送邮件。
- 取消probe证明job不会SUCCEEDED，不声称能撤销已经完成的事实写入；源码先enrichProfiles再按任务取消检查的既有行为有披露，未引入强制跨系统事务要求。
- 原文夹具经原始归档与内部hash复核；Java/PDFBox和实际resolver已由fresh测试读取，未拿重排PDF替代。只证明这些正负例，不外推线上恢复数量。
- 生产旧抽取活跃结果/onlyPending数量/运行中发送和补全状态仅待发布前只读清点；未清空旧cache、迁移、部署、推送、合并、新建自动化或操作生产。
- A1–A6全部PENDING：故障恢复、原文关系、业务保护、浏览器视觉、发布缓存边界、六类专业闭环及最终SHA人工签字，均不能从机器PASS推定。Machine PASS仅允许进入AWAITING_HUMAN_ACCEPTANCE。
- 本reviewer只写/tmp临时报告、诊断源码/输出及Maven瞬态target；未写review evidence、未改repair.md/产品/测试/批准计划/fast-p，未stage或commit。HEAD/index保持。

### Durable Fresh Audit Receipts

```text
SCOPE authorized=56 changed=55 extra=0 DD=24
CHILD 01 2 within authorized scope
CHILD 02 2 within authorized scope
CHILD 03 8 within authorized scope
CHILD 04 10 within authorized scope
CHILD 05 10 within authorized scope
CHILD 06 10 within authorized scope
CHILD 07 10 within authorized scope
CHILD 08 6 within authorized scope
CHILD 09a 6 within authorized scope
CHILD 09b 9 within authorized scope
CHILD 09c 9 within authorized scope
CHILD 09d 8 within authorized scope
FIXTURE source-contact-recall.zip internalHashes 6 byteEqualOriginals 3
FIXTURE pdf-contact-coverage.zip internalHashes 9 byteEqualOriginals 4
FIXTURE html-contact-recall.zip internalHashes 8 byteEqualOriginals 4
FIXTURE XML hashes 14
ACCEPTANCE
01.json f41acd1ae2b2621e60e3c514026c3c8a15ded0f9997935b6ff5b01c23d74ea0e
02.json 183fc5a3376e026052407b49a2c3a7599c5e92de4b8cca174af582f9a6a79b30
03.json 241703261facf60718acecac265b487d86c9e47ec8ae189557cd2e87bd6d3e15
04.json 0f1beed6b7803161cfd6b003cc523c4c91c52bd50fe89d22769238e6122eba55
05.json 22619a9dd78ed4d9641a52637f84f8d36fe9c6cbb284d091d593e0baec98532e
06.json 2bdae13746b30f3dd493eb86bf9551ed0bf7773821f763c2135a14081be20dff
07.json 60d1e59687847c03d60e882f9d38229e41198d024e19364ae99cf22d40086935
08.html 9ea0edcca6719a7596bec659d0dcc389070f8cb4795e4dd264fb8f20866bc51d
08.json 7bf2121ddd8a3db883abfb5dba70c97edec2e5818ef518a6d7deb60e4cb3d1b4
09a.json ebefb8ba5c9a28844dd5a4bb949da961f71f2266a1cc9ae9d7aabadac4f09f26
09b.json 2bb9f7eefa42ab841298da550f011088f3d994f24ad221ef990449bd20bcd6ad
09c-classification.json 81aa878749ab21087d85b5a0edb0d9a06e1ff2d9d8f54105329564c98b5d9b2a
09c-layers.json 01bc49f5450d32c9de1bc234b3fe09764fb2483dc973601b9a30856be02f050c
09c-partial.json 3ba5e87d86f9379eb6abe0ca53af8eadf5ba081be671b8f8b98ed4578f7c0b25
09d.json 8be3ae23b972c38f0a6d894489992bc2b997edf3e2e4fbd1b5f5d471d1b440f4
SKIPPED com.weibo.talentintroduction.rag.RagFactAdminServiceTest 1
SKIPPED com.weibo.talentintroduction.auth.AuthFlowIntegrationTest 1
SKIPPED com.weibo.talentintroduction.rag.RagKnowledgeBaseTest 1
SKIPPED com.weibo.talentintroduction.mail.service.MeetingCalendarSendIntegrationTest 1
SKIPPED com.weibo.talentintroduction.campaign.service.MeetingCalendarServiceMysqlTest 1
SKIPPED com.weibo.talentintroduction.mail.controller.MailboxConversationControllerTest 1
SKIPPED com.weibo.talentintroduction.campaign.controller.MeetingCalendarControllerMysqlTest 1
SKIPPED com.weibo.talentintroduction.rag.RagLetterComposerTest 1
SKIPPED com.weibo.talentintroduction.discovery.service.EuropePmcDataSourceTest 1
SKIPPED com.weibo.talentintroduction.campaign.repository.FlywayMigrationIntegrationTest 1
SKIPPED com.weibo.talentintroduction.audit.repository.OperatorActionLogRepositoryTest 1
SKIPPED com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepositoryTest 1
SKIPPED com.weibo.talentintroduction.mail.controller.CalendarAttachmentIntegrationTest 1
JUNIT 266 {'tests': 4149, 'failures': 0, 'errors': 0, 'skipped': 13}
CSS_DIFF 0
CACHE_KEYS 11 {'20260926-discovery-repair'}
MAPPINGS 3 keyword/dynamic=false
```

### Reproducible independent regression probes

以下诊断仅临时文件；直接使用本边界fresh编译产物和真实资格/复评/writer，HTTP与邮箱为隔离替身。装配读取既有测试fixture，不mock revalidateDiscovery。步骤：先本报告完整构建；将两段Java分别保存为同名/tmp文件；执行下方Python。probe源码/输出内嵌，结果不依赖临时链接保存。

```python
from pathlib import Path
import subprocess, xml.etree.ElementTree as E
report = next(Path('target/surefire-reports').glob('TEST-*.xml'))
cp = next(p.attrib['value'] for p in E.parse(report).findall('.//property') if p.attrib['name']=='java.class.path')
jdk='/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/'
for name in ['DiscoveryAggregateEpoch2Probe','DiscoveryAggregateCancellationProbe']:
    subprocess.run([jdk+'javac','-cp',cp,'/tmp/'+name+'.java'],check=True)
    subprocess.run([jdk+'java','-cp',cp+':/tmp',name],check=True)
```

```java
import java.lang.reflect.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.mockito.Mockito;
import org.springframework.http.*;
import org.springframework.web.client.*;
import com.weibo.talentintroduction.expert.domain.*;
import com.weibo.talentintroduction.expert.service.*;
public class DiscoveryAggregateEpoch2Probe {
 static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static class ES {
  final ObjectMapper mapper=new ObjectMapper(); Map<String,Object> raw,candidate; int gets,puts,deletes; long seq=3; String mode; List<String> requests=new ArrayList<>();
  ES(Map<String,Object> s,String m,boolean candidateExists){raw=new HashMap<>(s);candidate=candidateExists?new HashMap<>(s):null;mode=m;}
  ResponseEntity<JsonNode> response(Map<String,Object>s,long seq){return ResponseEntity.ok(mapper.valueToTree(Map.of("_source",s,"_seq_no",seq,"_primary_term",2)));}
  Object answer(org.mockito.invocation.InvocationOnMock i) throws Throwable{
   if(!i.getMethod().getName().equals("exchange")) return Mockito.RETURNS_DEFAULTS.answer(i);
   String u=(String)i.getArgument(0);HttpMethod m=i.getArgument(1);requests.add(m+" "+u);
   if(u.contains("application"))throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
   boolean cand=u.contains("candidate");
   if(m==HttpMethod.GET){
    if(cand){if(candidate==null)throw new HttpClientErrorException(HttpStatus.NOT_FOUND);return response(candidate,8);}
    gets++;if(mode.equals("missing"))throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
    if(mode.equals("identity-race")&&gets>=2)raw.put("email","changed@example.org");return response(raw,seq);
   }
   if(m==HttpMethod.POST){
    check(u.endsWith("?if_seq_no=3&if_primary_term=2"),"RAW CAS absent");
    Map body=(Map)((HttpEntity)i.getArgument(2)).getBody(); Map<String,Object> doc=(Map)body.get("doc");
    check(doc.keySet().equals(Set.of("filterResult","filterRejectReason","expertClassification")),"extra RAW write");
    raw.putAll(doc);seq=4;return ResponseEntity.ok(mapper.valueToTree(Map.of("result","updated","_seq_no",4,"_primary_term",2)));
   }
   if(m==HttpMethod.PUT){check(cand&&u.endsWith("/OLD-DOC?op_type=create"),"wrong create locator");puts++;candidate=new HashMap<>((Map)((HttpEntity)i.getArgument(2)).getBody());return ResponseEntity.status(201).body(mapper.valueToTree(Map.of("result","created")));}
   if(m==HttpMethod.DELETE){check(cand&&u.endsWith("/OLD-DOC?if_seq_no=8&if_primary_term=2"),"wrong delete/CAS");deletes++;if(mode.equals("409"))throw new HttpClientErrorException(HttpStatus.CONFLICT);if(mode.equals("500"))throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);candidate=null;if(mode.equals("404"))throw new HttpClientErrorException(HttpStatus.NOT_FOUND);return ResponseEntity.ok(mapper.valueToTree(Map.of("result","deleted")));}
   throw new AssertionError("unexpected request "+m+u);
  }
 }
 static void scenario(String mode,boolean valid,boolean existing)throws Exception{
  Object b=Class.forName("com.weibo.talentintroduction.expert.service.ExpertRevalidationServiceBehaviorTest").getConstructor().newInstance();
  Method sf=b.getClass().getDeclaredMethod("discoverySource",String.class);sf.setAccessible(true);
  Map<String,Object> source=(Map)sf.invoke(b,"HISTORICAL-ORCID");source=new HashMap<>(source);source.put("operatorStatus","PAUSED");source.put("customOperatorNote","retained");
  check(DiscoveryIdentity.INSTANCE.allowedMap(source),"invalid fixture identity");
  ES es=new ES(source,mode,existing);RestTemplate http=Mockito.mock(RestTemplate.class,es::answer);
  Object w=Class.forName("com.weibo.talentintroduction.expert.service.ExpertIndexWriterServiceTest").getConstructor().newInstance();ExpertIndexWriterService writer=(ExpertIndexWriterService)get(w,"service");set(writer,"restTemplate",http);
  ExpertRevalidationService service=(ExpertRevalidationService)get(b,"service");set(service,"expertIndexWriterService",writer);
  EmailValidationService email=(EmailValidationService)get(b,"emailValidationService");
  if(mode.equals("exception"))Mockito.when(email.validate("researcher@example.org")).thenThrow(new IllegalStateException("validation unavailable"));
  else Mockito.when(email.validate("researcher@example.org")).thenReturn(new EmailValidationResult(2,valid,valid?null:"NO_MX_RECORD"));
  PromotionOutcome out=service.revalidateDiscovery("OLD-DOC");
  boolean fail=Set.of("exception","409","500","identity-race").contains(mode);
  if(mode.equals("missing"))check(out instanceof PromotionOutcome.RawMissing,"missing RAW not retryable");
  else if(fail)check(out instanceof PromotionOutcome.WriteFailed,"failure reported as success: "+out);
  else if(!valid)check(out instanceof PromotionOutcome.Rejected,"invalid email admitted");
  else check(existing?out instanceof PromotionOutcome.AlreadyPresent:out instanceof PromotionOutcome.Promoted,"valid historical ID failed");
  if(!valid||fail||mode.equals("missing"))check(es.puts==0,"rejected profile created");
  if(mode.equals("identity-race")||mode.equals("missing")||mode.equals("exception"))check(es.deletes==0,"unsafe delete");
  if(mode.equals("ok")&&valid){check("HISTORICAL-ORCID".equals(es.candidate.get("orcidId")),"business key changed");check("PAUSED".equals(es.candidate.get("operatorStatus")),"operator changed");check(source.get("identityVerification").equals(es.candidate.get("identityVerification")),"proof changed");}
  if(!valid&&mode.equals("ok")){check(es.candidate==null,"invalid candidate retained");check("EMAIL:NO_MX_RECORD".equals(es.raw.get("filterRejectReason")),"reason missing");}
  long validations=Mockito.mockingDetails(email).getInvocations().stream().filter(i->i.getMethod().getName().equals("validate")).count();
  if(!mode.equals("missing"))check(validations==1,"full validate bypass");
  System.out.println("CASE "+mode+" emailValid="+valid+" existing="+existing+" outcome="+out+" validate="+validations+" put="+es.puts+" delete="+es.deletes+" rawBusinessKey="+es.raw.get("orcidId"));
 }
 public static void main(String[]x)throws Exception{
  scenario("ok",false,true);scenario("ok",true,false);scenario("ok",true,true);scenario("exception",true,false);scenario("409",false,true);scenario("500",false,true);scenario("404",false,true);scenario("identity-race",false,true);scenario("missing",false,true);
  System.out.println("PROBE PASS 9 scenarios; real revalidator + eligibility + writer; HTTP/email isolation only");
 }
}
```

```text
CASE ok emailValid=false existing=true outcome=Rejected(reasons=[EMAIL:NO_MX_RECORD]) validate=1 put=0 delete=1 rawBusinessKey=HISTORICAL-ORCID
CASE ok emailValid=true existing=false outcome=com.weibo.talentintroduction.expert.service.PromotionOutcome$Promoted@7792d851 validate=1 put=1 delete=0 rawBusinessKey=HISTORICAL-ORCID
CASE ok emailValid=true existing=true outcome=com.weibo.talentintroduction.expert.service.PromotionOutcome$AlreadyPresent@7e1ffe70 validate=1 put=0 delete=0 rawBusinessKey=HISTORICAL-ORCID
2026-09-27 19:24:20.187 [main] WARN  c.w.t.e.s.ExpertRevalidationService - Discovery revalidation failed for OLD-DOC: validation unavailable
CASE exception emailValid=true existing=false outcome=com.weibo.talentintroduction.expert.service.PromotionOutcome$WriteFailed@2a8a3ada validate=1 put=0 delete=0 rawBusinessKey=HISTORICAL-ORCID
2026-09-27 19:24:20.197 [main] WARN  c.w.t.e.s.ExpertRevalidationService - Discovery revalidation failed for OLD-DOC: 409 CONFLICT
CASE 409 emailValid=false existing=true outcome=com.weibo.talentintroduction.expert.service.PromotionOutcome$WriteFailed@2a8a3ada validate=1 put=0 delete=1 rawBusinessKey=HISTORICAL-ORCID
2026-09-27 19:24:20.205 [main] WARN  c.w.t.e.s.ExpertRevalidationService - Discovery revalidation failed for OLD-DOC: 500 INTERNAL_SERVER_ERROR
CASE 500 emailValid=false existing=true outcome=com.weibo.talentintroduction.expert.service.PromotionOutcome$WriteFailed@2a8a3ada validate=1 put=0 delete=1 rawBusinessKey=HISTORICAL-ORCID
CASE 404 emailValid=false existing=true outcome=Rejected(reasons=[EMAIL:NO_MX_RECORD]) validate=1 put=0 delete=1 rawBusinessKey=HISTORICAL-ORCID
CASE identity-race emailValid=false existing=true outcome=com.weibo.talentintroduction.expert.service.PromotionOutcome$WriteFailed@2a8a3ada validate=1 put=0 delete=0 rawBusinessKey=HISTORICAL-ORCID
CASE missing emailValid=false existing=true outcome=com.weibo.talentintroduction.expert.service.PromotionOutcome$RawMissing@3c952a33 validate=0 put=0 delete=0 rawBusinessKey=HISTORICAL-ORCID
PROBE PASS 9 scenarios; real revalidator + eligibility + writer; HTTP/email isolation only
```

```java
import java.lang.reflect.*;
import java.util.*;
import org.mockito.Mockito;
import com.weibo.talentintroduction.discovery.service.*;
import com.weibo.talentintroduction.discovery.domain.*;
import com.weibo.talentintroduction.config.*;
import com.weibo.talentintroduction.task.service.*;
public class DiscoveryAggregateCancellationProbe {
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 public static void main(String[]args)throws Exception{
  Object t=Class.forName("com.weibo.talentintroduction.discovery.service.ExpertDiscoveryServiceTest").getConstructor().newInstance();
  t.getClass().getMethod("setUp").invoke(t);
  Method retry=Arrays.stream(t.getClass().getDeclaredMethods()).filter(m->m.getName().startsWith("workerRetryEvidence")).findFirst().get();retry.setAccessible(true);retry.invoke(t);
  Method create=Arrays.stream(t.getClass().getDeclaredMethods()).filter(m->m.getName().equals("createService$default")).findFirst().get();create.setAccessible(true);
  ExpertDiscoveryService svc=(ExpertDiscoveryService)create.invoke(null,t,null,null,null,null,15,null);
  Method make=t.getClass().getDeclaredMethod("enrichmentJob",long.class,String.class,String.class,String.class,int.class);make.setAccessible(true);
  ExpertAcademicEnrichmentJob job=(ExpertAcademicEnrichmentJob)make.invoke(t,10L,"DISCOVERY-DOC","ORCID","lease-11",1);
  TaskProgressStore progress=(TaskProgressStore)field(t,"progressStore");
  ExpertAcademicEnrichmentJobService jobs=(ExpertAcademicEnrichmentJobService)field(t,"enrichmentJobService");
  Mockito.clearInvocations(jobs);Mockito.when(progress.isCancelled("EXPERT_ENRICHMENT")).thenReturn(true);
  AutoEnrichmentBatchResult out=svc.processClaimedEnrichmentJobBatch(List.of(job),RequestKind.HISTORY_ENRICHMENT,"EXPERT_ENRICHMENT");
  long writes=Mockito.mockingDetails(jobs).getInvocations().stream().filter(i->i.getMethod().getName().equals("complete")).count();
  if(!out.getCancelled()||out.getSucceeded()!=0||writes!=0)throw new AssertionError("cancelled job completed");
  System.out.println("CANCELLED=true succeeded=0 completeCalls=0; completed enrichment may have side effects, but cancelled job never commits SUCCEEDED");
  for(String name:List.of("complete refuses a stale token or a non running row without writing (I-2)","complete reports failure when the token matched CAS no longer applies (I-2)")){
   Object j=Class.forName("com.weibo.talentintroduction.discovery.service.ExpertAcademicEnrichmentJobServiceTest").getConstructor().newInstance();
   j.getClass().getMethod(name).invoke(j);System.out.println("LEASE PASS "+name);
  }
 }
}
```

```text
2026-09-27 19:30:13.815 [main] INFO  c.w.t.d.s.ExpertDiscoveryService - 补全批次已取消，剩余 1 条任务保持租约未完成
CANCELLED=true succeeded=0 completeCalls=0; completed enrichment may have side effects, but cancelled job never commits SUCCEEDED
LEASE PASS complete refuses a stale token or a non running row without writing (I-2)
LEASE PASS complete reports failure when the token matched CAS no longer applies (I-2)
```

范围/原文审计源码：

```python
import pathlib,json,subprocess,hashlib,zipfile,xml.etree.ElementTree as E,re
root=pathlib.Path.cwd(); base='64c0394a940bd79c2ecc04e5c497650f045faa75';head='8051fa894d96f065b8b9ef2b39463262b2cf8c50'
plans=sum((json.loads((root/p).read_text()) for p in ['docs/plans/2026-09-26/discovery-repair-evidence/plan-scope.json','docs/plans/2026-09-26/discovery-repair-evidence/scope-09/plan-scope.json']),[])
allowed=set(f for p in plans for f in p['files']);changed=subprocess.check_output(['git','diff','--name-only',base,head,'--','src'],text=True).splitlines()
assert not set(changed)-allowed
print('SCOPE authorized=%d changed=%d extra=0 DD=%d'%(len(allowed),len(changed),len(set(d for p in plans for d in p['findings']))))
ledger=(root/'docs/plans/fast/2026-09-26-discovery-repair-00-master/ledger.md').read_text()
for line in ledger.splitlines():
 c=[x.strip() for x in line.split('|')]
 if len(c)>14 and c[1] in ['01','02','03','04','05','06','07','08','09a','09b','09c','09d']:
  diff=subprocess.check_output(['git','diff','--name-only',c[7],c[11],'--','src'],text=True).splitlines();p=next(p for p in plans if p['plan']==pathlib.Path(c[2]).name)
  assert len(diff)<=10 and not set(diff)-set(p['files']);print('CHILD',c[1],len(diff),'within authorized scope')
original=pathlib.Path('/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/audits/2026-09-26-deep-discovery-diagnosis/evidence/original-sources.zip')
orig=zipfile.ZipFile(original)
assert hashlib.sha256(original.read_bytes()).hexdigest()=='0607b402b9ffdeadcc8726710d3031277fbc7ee05f1989caf2c7da25bc0f4205'
for name in ['source-contact-recall.zip','pdf-contact-coverage.zip','html-contact-recall.zip']:
 z=zipfile.ZipFile(root/'src/test/resources/discovery'/name);manifest=json.loads(z.read('manifest.json'));count=[0];originals=0
 def walk(v):
  if isinstance(v,dict):
   if 'path' in v and 'sha256'in v: assert hashlib.sha256(z.read(v['path'])).hexdigest()==v['sha256'];count[0]+=1
   for x in v.values():walk(x)
  elif isinstance(v,list):
   for x in v:walk(x)
 walk(manifest)
 for p,m in manifest.get('members',{}).items():assert hashlib.sha256(z.read(p)).hexdigest()==m['sha256'];count[0]+=1
 for p in z.namelist():
  if p.endswith(('/source.pdf','/source.html')):assert z.read(p)==orig.read(p);originals+=1
 print('FIXTURE',name,'internalHashes',count[0],'byteEqualOriginals',originals)
z=zipfile.ZipFile(root/'src/test/resources/discovery/xml-route-recall.zip')
for line in z.read('manifest.sha').decode().splitlines():
 h,p=line.split(None,1);assert hashlib.sha256(z.read(p.strip())).hexdigest()==h
print('FIXTURE XML hashes',len(z.read('manifest.sha').decode().splitlines()))
print('ACCEPTANCE')
for p in sorted((root/'target/discovery-plan-acceptance').glob('*')):
 print(p.name,hashlib.sha256(p.read_bytes()).hexdigest())
s={k:0 for k in ['tests','failures','errors','skipped']};reports=list((root/'target/surefire-reports').glob('TEST-*.xml'))
for p in reports:
 r=E.parse(p).getroot()
 for k in s:s[k]+=int(r.attrib.get(k,0))
 if int(r.attrib.get('skipped',0)):print('SKIPPED',r.attrib['name'],r.attrib['skipped'])
print('JUNIT',len(reports),s)
print('CSS_DIFF',subprocess.check_output(['git','diff','--numstat',base,head,'--','src/main/resources/static/styles.css'],text=True).strip() or '0')
index=(root/'src/main/resources/static/index.html').read_text();keys=re.findall(r'(?:src|href)="[^"?]+\?v=([^"&]+)"',index);assert len(keys)==11 and set(keys)=={'20260926-discovery-repair'};print('CACHE_KEYS',len(keys),set(keys))
for p in ['raw','candidate','application']:
 j=json.loads((root/f'src/main/resources/es/orcid_info_{p}.json').read_text());assert j['mappings']['properties']['researchFieldIds']['type']=='keyword' and j['mappings']['dynamic']==False
print('MAPPINGS 3 keyword/dynamic=false')
```

Repair planning: N/A

### Next Action

AWAITING_HUMAN_ACCEPTANCE：控制器将本Epoch报告追加到精确review evidence并生成新边界人工清单；用户完成主计划A1–A6并明确签认 `8051fa894d96f065b8b9ef2b39463262b2cf8c50`。不自动合并或部署。

No product code was modified.
