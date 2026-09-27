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
