# 深度发现解析修复、统一重审与日志：总开发计划

日期：2026-10-24。状态：**待执行**。本轮只形成开发计划，未改业务代码、未迁移/发布/回写历史数据。

## 需求描述

1. 修复已证实的审核数量503，以及原文姓名假0、格式/正文/姓名关联和资格原句漏解析。
2. 在真实“专家审核”增加统一批量重新审核：选中/全部筛选页、默认仅对照、可应用、每人固定8步、具体卡点、证据、前后差异及历史。
3. 同批原文和名单验证代码更新收益；已有专家重审与新绑定补录分开计数。

必须保持：现有身份/机构/资格/硬阻断规则；SELF可用和已接受的Apollo准入分支；保存型免费search/付费detail配置；人工/联系/SBIR/APPLICATION保护；原文保留/保护/批量取消保护/删除；取消不撤销已准入；候选同步独立；既有邮件/联系人/外联不受影响。

不做：泛化工作流引擎、缓存框架、全文爬虫或OCR平台、按姓名猜身份、任意降低准入条件；不恢复系统500次Apollo上限；不把尚未证实的422问题当本次可修；不承诺新增准入人数。研究方向和论文职位不足不会被本方案新加成硬阻断。

## 关键不变量

### I-M1：只有一套准入提交链
规则/证据经现有Worker→Rules→Policy/Gate→Commit→Candidate projection。AUDIT可以保存核验审计，但准入/ES业务写0；ENFORCE仅原CAS写。适用全部子计划；违反会造成对照与生产不一致。来源：K-discovery-admission-read-write-parity。

### I-M2：每轮独立且旧业务不被旁观改变
新的重审item独立review_epoch；原任务/item/request_count不覆盖；只有已实际提交的新重审结果进入业务Gate。适用05–10；违反会让“重审中”抢占旧准入。来源：K-discovery-recheck-history-not-item-reset。

### I-M3：证据收益不能跨层冒认
绑定≠新专家≠资格证据≠可准入≠已准入≠候选同步。每份报告说明固定范围、版本、原文SHA、外部刷新、未完整部分。适用全部子计划；违反会虚报收益。来源：本批原文对照。

### I-M4：范围受子计划约束
每子计划默认≤10文件、≤2独立子系统；每既有共享store最多1新列。01经用户于2026-10-25授权，仅为同步修正`discoveryApolloSettings.test.js`的过期全局资源键断言，允许11文件。05经用户于2026-10-25授权，仅为六个直接/共享核验消费者测试同步V161 schema，允许15文件，保留原回归行为。用户随后明确“后续修改都授权 把所有子计划都跑完”：为完整实现既定目标、关键不变量与验收所必需的后续修改（包括直接依赖/回归文件、清单扩展及必要的文件数例外）已获持续授权；控制器须先记录具体证据、逐次单独提交相应计划修订并追加ledger的Amendments，再实施，不再为同类范围变更重复等待授权。此授权不扩大无关业务，不取消独立核验、三轮自动修复上限及不可达前提/安全边界的阻塞处理，也不授权生产发布、付费操作、推送或改写历史。共享文件必须串行按前一产物实施，每步独立机器复验。来源：K-master-plan-shared-file-sequential-gates及本次用户持续授权。

## 现状审计

基线HEAD `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`；本地已有多人工作/未提交文件，禁止重置。代码哈希、SQL写者/读取者、调用链：[审计证据](discovery-recheck-evidence/README.md)。原始rg回执保留，不能把知识条目当现状证明。

| 已证实的问题 | 代码及实测 | 处理计划 |
|---|---|---|
| 四卡分类计数503 | app.js:2995；ScanService:109/310；部署同路径21.23秒超时 | 01 |
| 姓名显示0与“未分析”混淆 | Capture写emails，ArchiveService:348读resolved；无analysis仅未运行对照 | 02 |
| 14份PDF误MIME、38份DOCTYPE XML拒绝 | ArchiveService:242/383；AnalysisService:64；safeText:108 | 02 |
| 页底/左字符/显式作者标签漏解析、97处伪邮箱 | Layout:78/286/344；PlainTextExtractor全局at替换 | 03 |
| 10份简介13人资格漏提取 | PaperQualificationEvidenceExtractor:58–77，现有均NOT_FOUND | 04/06 |
| 已绑定姓名顺序/复合职位 | Rules:85–87/375–385/426；3条固定样本反事实 | 04 |
| 现有人工retry不重新采集证据 | ReviewService.retryBatch vs Worker；V158唯一键 | 05–09 |
| 旧数据不随parser升级自动修复 | queue缓存版本校验、archive analysis独立保存 | 10 |

既有“带decision的同步专家列表”也有全量扫描限制；01修四卡统计，不宣称所有筛选列表已修。新重审的ALL_MATCHING走现有scroll，不依赖那个同步列表分页拿全名单。若要另修同步分类列表，应独立定界，不能混入本计划增加缓存系统。

## 实现方案

严格按01→10顺序；每个子计划只执行其表内文件。此前独立预览[线上页面](https://qingfei.szwbww.com/previews/discovery-rereview-20261024.html)只作为布局验收参照，正式页面必须使用09完整CSS/DOM及08真实API。预览不能证明后端已完成。

| 子计划 | 内容 | 文件数 | 子系统 | 发布门禁 |
|---|---|---:|---|---|
| [01](discovery-recheck-01-counts.md) | 一次异步统计、准确错误提示 | 11（授权测试例外） | 审核计数+前端 | 可独立发布 |
| [02](discovery-recheck-02-archive.md) | 原文格式、姓名显示、DOCTYPE | 10 | 原文分析+前端 | 可独立发布 |
| [03](discovery-recheck-03-identity-parser.md) | 明确邮箱姓名归属、伪邮箱 | 9 | 来源解析 | 可独立发布；不自动回填 |
| [04](discovery-recheck-04-qualification.md) | 简介事实、绑定姓名、复合职称 | 9 | 原文事实+核验规则 | 可独立发布；规则版本化 |
| [05](discovery-recheck-05-history.md) | 独立轮次和轨迹schema/读隔离 | 15（授权测试例外） | 核验持久化 | 兼容部署，无新启动入口 |
| [06](discovery-recheck-06-evidence.md) | 共享只读原文/学术采集 | 9 | 原文分析+核验证据 | 兼容部署，无新入口 |
| [07](discovery-recheck-07-pipeline.md) | 统一Worker与八步 | 14（A7授权必需依赖） | 核验执行+审计 | 原发现回归通过；新入口仍未开放 |
| [08](discovery-recheck-08-api.md) | 冻结/启动/取消/历史API | 9 | 重审协调/API | 与09作为完整用户功能发布单元 |
| [09](discovery-recheck-09-frontend.md) | 真实审核页、完整CSS、原文桥接及只读统计 | 12（A7授权必需依赖） | 审核前端+只读统计 | 依赖08并联合验收后发布 |
| [10](discovery-recheck-10-historical-replay.md) | 历史同批验证和补录 | 7 | 原文补录+隔离回放 | 默认只读；生产应用单独按manifest明确执行 |

### 八步与不短路的边界

| 步骤 | 可观察结果 | 继续规则 |
|---:|---|---|
| 1 名单快照与保护 | docId/hash/revision/人工联系保护 | 保护可以保留建议，但禁止应用 |
| 2 来源论文与原文 | 来源ID/SHA/缺失/部分覆盖 | 缺原文不自动判不合格 |
| 3 姓名/邮箱身份 | 唯一绑定/既有可靠身份证据/冲突 | 无可靠身份停止个人外调，保留卡点 |
| 4 学术与资格补证 | 本人学历/出生/当前或历史任职、研究方向 | 不足可进入现有Apollo分支 |
| 5 直接准入判定 | 纯规则结论和缺口 | 足够则6跳过，不浪费请求 |
| 6 Apollo辅助核验 | 查询/候选/匹配、免费/付费与错误码 | REUSE不HTTP；免费不detail |
| 7 综合准入结论 | 所有hard blockers+资格、拟变更 | 建议与当前准入分开 |
| 8 结果记录与同步 | 仅对照/提交revision/同步状态 | 同步失败只重试同步 |

普通深度发现与重审都用原Worker，不增加第二套策略。旧采集阶段已完成的邮箱/来源证据在新轨迹中写真实输入摘要；没有历史时间不能补造。原独立学术补全仍负责三层字段写入，新重审采集仅本轮补证/展示，避免触发旧复评旁路。

### 跨计划共享接口锁定

- 05：run.recheck_json仅新1列；item.review_epoch仅新1列；独立step表。spec冻结、control CAS；普通run为null/epoch0。
- 08冻结逐人snapshot_json.reviewInput：前态revision（缺记录为null）、固定来源/SHA和缓存证据；只用这份输入做对照。应用时再次比较初始revision，不能以完成时新读值替换基线。
- 06：compute纯函数与commitAnalysis分离；论文事实保留PUBLICATION_TIME，桥接为HISTORICAL job、SELF；原文SHA通过lease读取验证。
- 07：payload.executionContext（evaluationAt/paperSourceRefs）是单个派生证据JSON协议成员，旧schema默认可读；Worker与Gate同时支持。新规则版本3，旧0/1/2不被重算成3。
- 08：run为业务流程权威；prepare task成功仅代表名单准备成功；详情必须返回before/proposed/applied/current四层含义。列表GET `/runs`还支持docId过滤；GET `/latest?docIds=...`限制100并批量查询，为09每页按钮显示最新轮。不得每个专家独立请求全历史。
- 09：CSS全文是合同；不得把preview.js静态例子接成真实结果。
- 10：新绑定用原consumeOutcomeInternal；已有专家送08，不直接覆写历史item。

### 共享文件与顺序

- app.js/index.html/styles.css：01→02（仅index）→09；02 papers文本不改新重审组件。
- PaperQualificationEvidenceExtractor/AnalysisService：02→04→06。
- Worker/Registration/Rules：04→07；05的新schema必须先于07新轮使用。
- Repository/Gate：05→07→08；QueryService：05→08；不能从同一旧HEAD并行改后机械合并。
- 同一个测试文件按前一子计划产物继续补用例，不能用新stub掩盖旧回归。

### 发布与回退

本计划不是发布指令。实施完成后按原发布流程构建WAR、校验部署类/资源版本并验证登录页真实API。05迁移向前兼容，禁止直接删新列/表回滚；新轮已产生时，回退Worker/Gate必须保留对新schema及rule3的兼容读取，否则会导致VERIFICATION_EVIDENCE_INVALID。回退优先关闭新重审启动入口并保留旧记录，不能撤销已提交准入或删除原文。

### 历史验证顺序

先03/04结构fixture，再同SHA真实样本，再全批原文只读对照；随后已有名单AUDIT+REUSE。输出实际差异后，才可由用户选择应用的固定manifest。原批519绑定、13人资格事实、897待审核仅为研究基线，不作为期望必须达到的通过数；冻结时丢失/已变动样本必须列出来。

## 变更文件清单

总计划仅治理，不授权代码改动；每个链接子计划的文件清单是实施唯一边界（通常≤10，01仅按I-M4授权测试例外为11）。总计划制品：本文件、10份子计划、discovery-recheck-evidence/README.md与四份rg回执、一份ES路径回执、一份SHA回执及计划自检结果；知识条目仅记录可复用发现。无需生成验收勾选导出文件。

## 验收标准

- I-M1：从真实原文parser穿过Worker/Gate/Commit的集成测试；AUDIT业务写0；应用不绕hard blockers；投影失败不回滚准入。
- I-M2：两次同人重审两个独立item；普通发现仍原去重；新排队项不改变已批准Gate；旧批次数量/请求历史不改。
- I-M3：固定输入重复得到相同判定，外部刷新另列；分层报告可核账；无519/13“必通过”的测试。
- I-M4：每子计划测试通过并完成机器核验，未列文件0修改；前端CSS/DOM逐字合同、真实浏览器截图；真实MySQL IT不能标为跳过即通过。
- 收尾执行完整JDK11 `mvn test`（含绑定JS测试）及`mvn package -DskipTests`，并记录有无跳过的IT。配置、日志或凭据不进入plan/截图；未证实项只列待验证。

## 人工验收清单

### A-M1：数量故障消除
- 前置条件：01的15501测试输入或重新冻结的生产实际范围。
- 操作步骤：打开审核并刷新；切页、手工暂缓一人，再刷新。
- 预期结果：单一后台扫描，四卡同轮完整值；失败显示明确码而非假0；人工操作仍有效。
- 覆盖：需求1、I-M1/3。

### A-M2：统一入口与对照隔离
- 前置条件：两名待审核、一名人工通过、一名候选同步失败。
- 操作步骤：先默认仅对照；再对前两名应用；打开每人8步。
- 预期结果：对照不改当前准入；应用只计真正提交；人工保护不覆盖；同步失败单独展示。
- 覆盖：需求2、I-M1/2、must-not-change保护/门槛/同步。

### A-M3：取消、旧史与原文保护
- 前置条件：运行中重审和已准入项，保护原文与旧无轨迹记录各一。
- 操作步骤：取消任务；看原准入；预览及批量取消保护；看旧核验详情。
- 预期结果：已准入不回滚；原文保护/取消保护仍可用；旧详情写“历史未记录”，不补造8个成功时间。
- 覆盖：I-M2、保留原文/取消语义/旧功能。

### A-M4：历史收益可复核
- 前置条件：10生成的固定原文和待审核manifest。
- 操作步骤：两次REUSE对照，抽查新增绑定与资格证据原句；单独REFRESH一轮。
- 预期结果：两次固定对照计数一致；绑定/新RAW/准入/同步分列；外部刷新不计代码收益；未自动触发发信。
- 覆盖：需求3、I-M3/4、邮件不受影响。

### A-M5：真实页面与CSS
- 前置条件：08–09已联调，1280×720、640×900。
- 操作步骤：核对09的配置、记录、8步详情；操作免费Apollo配置后准备一轮REFRESH；查看全程请求类型。
- 预期结果：白底不透、正文清晰、窄屏表格局部滚动；免费detail请求0；原Apollo设置可保存且新轮冻结值可见；原人工按钮可用。
- 覆盖：I-M4、所有前端样式合同、Apollo设置保持。

人工验收开始后再导出master-acceptance.md。机器复验通过不等于用户验收完成。

## 修正记录

- 2026-10-25，用户“授权 继续”：批准01增加`src/test/js/discoveryApolloSettings.test.js`并允许11文件，保留独立控制器测试；原断言从旧计划动态读取全局资源键，字面量检索无法发现，导致01必需的资源键升级与全量JS回归冲突。仅修正过期断言，不修改历史计划、不扩大产品行为；授权与提交身份详见`../fast/discovery-recheck-master/ledger.md`的Amendments。
- 2026-10-25，用户再次“授权 继续”：批准03增加`DiscoveryPaperArchiveCaptureTest.kt`、`PdfEmailExtractorTest.kt`、`ExpertDiscoveryServiceTest.kt`，清单6→9、未突破I-M4上限。只同步已证实的layout版本及明确唯一来源归属正例，不放宽共享/未知/相邻/错误所有者保护，不改变消费或准入行为；授权与提交身份详见同一ledger的Amendments。
- 2026-10-25，用户“授权 继续”：批准05增加六个已由检索回执证明消费修改后Repository/Gate/Query的旧schema测试：`DiscoveryVerificationReportingIT.kt`、`DiscoveryCountrySupplementFlowIT.kt`、`DiscoveryVerificationFlowIT.kt`、`DiscoveryVerificationIntegrationTest.kt`、`DiscoveryVerificationReplayToolTest.kt`、`DiscoveryVerificationWorkerTest.kt`。清单9→15，仅05获得文件数上限例外；仅同步测试schema至V161，保留原断言及故意测试迁移前后的旧schema阶段，不加生产schema探测兼容分支、不增加生产行为。确切检索请求/输出见`../fast/discovery-recheck-master/children/05/execution.md`的Scope/precondition evidence addendum，授权与提交身份见同一ledger的Amendments。
- 2026-10-25，用户在05独立核验期间明确“后续修改都授权 把所有子计划都跑完”：批准在原定目标/不变量/验收内继续完成全部子计划，并为必要的后续修改、直接依赖/回归清单扩展及文件数例外提供持续授权。每次仍须以具体证据界定、单独提交计划修订、追加Amendments并保留独立实现/核验和证据门禁；不因同类范围问题再次等待用户确认。未授权无关业务、生产发布、付费操作或Git历史改写；不可达前提与安全冲突仍如实阻塞，不能伪造证据。
- 2026-10-25，依用户持续授权“后续修改都授权 把所有子计划都跑完”（A7），07增加`DiscoveryRecheckAcademicEvidenceService.kt`、`OpenAlexDataSource.kt`及各自现有测试，清单10→14并记录必要文件数例外。07 I-5要求每次外调前检查取消；现有OpenAlex批次内部会继续发author/works/patent请求，Worker只在collect入口检查不能满足此合同。仅增加该调用链的逐次dispatch取消guard、传播及回归，保留普通补全API/行为、RequestKind/预算与原有免费付费规则；不新增客户端或第二执行引擎。具体调用链与检索回执归档于07 execution日志，提交身份详见ledger的Amendments。
- 2026-10-25，依用户持续授权“后续修改都授权 把所有子计划都跑完”（A7），09增加`discovery-papers.js`、`discoveryPapers.test.js`、`DiscoveryVerificationRepository.kt`、`DiscoveryVerificationRepositoryIT.kt`，清单8→12并记录必要文件数例外。原文reader仅私有且默认首个可读attempt，宿主无法按冻结record/attempt/SHA复用；08统计仅有聚合queued，缺09已要求的pending/processing和独立protected。仅增加原reader窄公开桥接与只读真实SQL计数及直接回归，不改准入写链、旧人工行为、迁移或全站样式；保留旧API字段，protected为重叠诊断子计数不得加总为互斥类别。具体检索/源码回执归档于09 execution日志，提交身份详见ledger的Amendments。
