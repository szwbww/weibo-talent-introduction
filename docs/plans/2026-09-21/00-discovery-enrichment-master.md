# 深度发现扩量与自动学术补全修改方案

状态：待用户审阅；本次仅生成方案，不修改业务代码、生产配置、数据库或运行中的任务。
日期：2026-09-21。技术栈：Kotlin / Spring Boot 2.7 / Java 11 / MySQL JDBC / Elasticsearch。
执行方式：顺序执行每个子计划；每个子计划独立验证，通过后才进入依赖它的下一项。当前工作区有用户未提交改动，实施须使用隔离工作区。
计划依据：[生产排查报告](../../investigations/2026-09-21-deep-discovery.md)及本次源码/接口核查。
本页同时是需求与整体设计基线，后续子计划不得私自扩大范围。

## 需求描述

R-1：将已申请的免费 OpenAlex API Key 接入发现、作者学术补全、论文查询，按账号真实额度运行。
R-2：恢复多来源有效抓取，故障不丢进度，避免每天重复首批，任务状态反映真实失败。
R-3：新增专家自动补学术数据，单次批量最多100人；100只是一批，不是每日总量；尾批也处理。
R-4：跨源统一使用可靠作者身份补全，覆盖有OpenAlex作者ID但没有ORCID的人；缺可靠身份明确未匹配。
R-5：抓取量独立可配置，各来源保留份额，逐步提升唯一新增专家数量。
R-6：提高公开全文获取率，显示抓取、全文、邮箱、去重、补全的准确阶段统计。

必须保持：
- M-1：邮箱校验、去重和现有资格/分类门禁，真实专家ID及联系人关联不变；本轮不触发邮件发送。
- M-2：保留人工“补充学术数据”和现有三个补采scope；历史回填、失败重试仍可用。
- M-3：已有专家姓名、邮箱、署名机构、运营状态不被补全覆盖；已申请专家不回建候选库。
- M-4：默认研发领域范围保持；EuropePMC/PMC OA继续按原范围排除。
- M-5：不新增付费API使用、不存储或展示Key、不改已应用迁移。

范围外：购买数据服务、姓名模糊自动合并、公开邮箱缺失时猜邮箱、扩展医学领域、专利数据接入、前端重设计、全库存量无条件重扫、承诺每天固定人数或拉完整个平台。

## 关键不变量

### Invariant I-1：分离论文、作者、API请求、批次和日额度
- Rule：论文页100、作者补全批100分别计量；来源cap、全局cap、作者cap、账号预算、执行时间是不同约束。任何一个停止都给出原因，不称为其他约束耗尽。
- Applies to：来源循环、补全worker、任务汇总。
- Violation consequence：错误限量/无限任务/指标误导。
- 来源：本次审计。

### Invariant I-2：先持久化，再推进
- Rule：RAW写入与补全意图持久化完成后才推进论文页；半页/故障/取消保留可重放检查点。重放对已发现专家补缺失任务但不重复计新增。
- Applies to：论文源、ORCID、任务表、游标保存。
- Violation consequence：跨ES/MySQL故障丢专家或补全任务。
- 来源：本次生产故障。

### Invariant I-3：真实身份与真实文档定位
- Rule：OpenAlex ID、ORCID、ES _id分别保存和使用；同名或姓名首字母不能作为可信学术身份。externalIds子键仅从_source读取。
- Applies to：全文作者关联、身份传播、学术写入。
- Violation consequence：错误作者指标、错误文档更新。
- 来源：K-enrichment-excludes-email-id-experts / K-expert-profile-source-sync。

### Invariant I-4：学术补全保持写入契约
- Rule：复用updateExpertAcademicFields唯一学术写入点，对现存层局部更新，按层记录失败；不把candidateUpdated当整体成功。RAW-only复评走原门禁，APPLICATION存在时不建候选。
- Applies to：自动/人工补全及补全后复评。
- Violation consequence：误报成功、绕过规则或重复外联。
- 来源：K-enrichment-write-three-layers / K-openalex-institution-two-sources。

### Invariant I-5：额度和任务生命周期诚实
- Rule：日额度不足延期到实际UTC reset；网络失败可重试；无身份/查无资料单列；发现计数不叠加补全计数。进度与执行记录终态语义一致。
- Applies to：HTTP policy、job状态、TaskExecutionSummaryProvider。
- Violation consequence：额度耗尽却无限重试、失败显示成功。
- 来源：K-circuit-breaker-terminal-status。

### Invariant I-6：默认范围和成本不扩张
- Rule：仅公开来源、免费预算；默认研发领域不扩大；不猜邮箱、不模糊合并、不更改邮件流程。
- Applies to：所有子计划。
- Violation consequence：任务偏离已确认目的。
- 来源：原始需求及本次边界。

## 现状审计

### 生产与接口检查
- 9月15–18日每天新增411/451/391/368人；19–20日TLS失败后清空OpenAlex游标；21日1148个有效邮箱、1147个重复、新增1人。
- Crossref同服务器单次编码200，双次编码400；线上Spring arXiv HTTP=301/空体，HTTPS=200/有entry。
- CORE返回searchId而非scrollId；本次offset=0得到297310403、275449547，offset=2得到297241067、297273325，无交集。
- OpenAlex authors?filter=openalex:A5023888391|A5086928770&per_page=100返回200与两个目标ID。
- 线上无OPENALEX_API_KEY，配置只带mailto；Key实际接入后须验证账号额度，不能把官方理论记录量当作生产可下载全文量。

### 存储与全部相关读写路径
1. **discovery_source_cursor**：V32，source_name VARCHAR(50) UNIQUE，cursor_value TEXT。生产写入仅ExpertDiscoveryService.saveSourceCursor；读取loadSourceCursor；调用覆盖手动、定时、关键词，ORCID单独循环。02增加查询key和检查点编码，04让CORE加入持久化。papers_processed_total含重扫，不能倒算完整恢复位置。
2. **RAW/CANDIDATE/APPLICATION ES**：三份mapping dynamic:false；externalIds enabled:false。字段写入路径：ExpertDiscoveryService.toIndexMap/indexToRaw、promoteDiscoveredToCandidate、updateExpertAcademicFields、updateRawDocumentEmail/邮件回填晋升；ExpertIndexWriterService的晋升/降级、operator状态同步、application状态、分类bulk、标签、writeCandidateDocument；ExpertRevalidationService晋升；ContactOut/Apollo/SBIR导入脚本整文档写入。身份子键随完整_source复制，无需新增mapping。来源脚本仍写自身externalIds，不由本轮重写。
3. **学术字段读者**：ExpertSearchService.sourceFields/toExpertProfile、ExpertIndexController DTO、CandidateEligibilityService、ExpertClassificationService、批量发信筛选及AiReplyContextBuilder。新的后台流程必须让这些原读者看见相同事实；不能另造一套补全字段。
4. **enrichedAt/enrichmentSource**：除OpenAlex外，ContactOut/Apollo导入也写这两个字段；不能单凭enrichedAt判断OpenAlex任务完成。mapping实际声明RAW/CANDIDATE为date，APPLICATION为keyword；既有知识条目声称全部keyword已与当前源码不符。本轮不迁移此类型，沿用现有定宽时间格式，新任务时间存MySQL。
5. **task_execution/task_progress_log**：schema见V4/V22/V35；TaskExecutionService负责执行记录，TaskProgressStore负责日志与恢复，Repository/任务接口/现有app.js读取；附加明细用既有JSON/message，不加列。原前端已支持PARTIAL_SUCCESS及来源明细，首期不改HTML/CSS/JS。
6. **expert_academic_enrichment_job（新）**：07定义唯一生命周期存储，08发现入队/worker领取/人工重试经同一service；worker复用已有补全API客户端和学术写入代码，仅新增调度持久化层。
7. **配置/内存**：application.yml+环境变量只读；OpenAlex专用客户端注入Key，通用客户端保持原行为。请求policy和任务互斥在进程内，队列claim用DB租约跨进程防重。

交互重点：
- 论文页推进与ES写入、MySQL入队不是一个事务，靠幂等重放恢复。
- CANDIDATE晋升APPLICATION后原候选被删除；补全复评不能重新创建该候选。
- 姓名首字母宽松匹配若直接携带OpenAlex ID，会把错误关联放大为可信学术画像。
- RAW中因缺学术指标未晋升的人也要能补全，不能只扫候选。
- 发现与历史回填共享API额度；历史回填不抢占新增专家补全的预留预算。

### 调研检查点
- CP-1 已核对：[OpenAlex认证/每批限制](https://help.openalex.org/api/authentication/)、[费用与免费额度](https://help.openalex.org/access/example-costs/)。官方支持Bearer、每页/OR值最多100；当前新账号使用实际额度须部署时验证。
- CP-2 已核对：[Crossref过滤语法](https://www.crossref.org/documentation/retrieve-metadata/rest-api/rest-api-filters/)与线上编码对照。
- CP-3 已核对：[arXiv API](https://info.arxiv.org/help/api/user-manual.html)与同Java/Spring请求对照。
- CP-4 CORE仅证明offset分页有效，未证明全量快照或任意深度分页。04采用每主题/每年分片、offset9000保守防护并显式记录尾部未覆盖，不冒充完整覆盖。
- CP-5 上线前：核对Key额度、目标MySQL/Flyway最大版本、ES真实mapping及完整可恢复游标；缺少历史游标则明确重扫期，不用截断日志猜测游标。

## 实现方案

推荐两段发布：先完成01–04恢复可靠抓取；05–08实现自动补全；09扩量；10提升全文收益。每项可独立验证和部署，自动worker默认关闭，依赖全部就绪后开启。实际执行按依赖顺序，禁止并发修改共享ExpertDiscoveryService。

主流程：
```text
多来源分页 → 提取邮箱/可靠作者身份 → 校验和去重 → RAW保存 → 幂等补全入队
                                                       ↓
                                                原有资格判断/晋升
补全worker每30秒领取≤100人 → 作者指标/最近论文 → 更新已有三层 → RAW-only复评
                   ↓失败/额度不足
               持久化等待，下次继续
```

方案取舍：
- 同步逐人补全：实现较小，但一个限流作者会阻塞整页发现，不选。
- **持久化批量补全（推荐）**：持续发现、每100人批量补全、尾批也跑，支持重启和额度恢复；新增的队列表只管生命周期。
- 每次发现后全库扫描：重复消耗且无法准确归属本次新增，不选。

建议参数（待验收，不是已上线值）：
| 项目 | 建议值 |
|---|---:|
| 论文分页 / 作者补全批量 | 100 / 100 |
| OpenAlex单次论文上限 | 10000，先从2500→5000逐档 |
| 全部论文源单次上限 | 15000 |
| Crossref / CORE / arXiv 单源上限 | 1000 / 1000 / 2000 |
| ORCID记录上限 / 总作者防护 | 1000 / 20000 |
| 全文并发 | 维持4 |
| 单次发现时间预算 | 4小时 |
| 新专家学术补全优先预算 | 20% |
| worker检查间隔 | 30秒 |
| 最近论文标题 | 开启最近3篇；专利仍关闭 |

达到任务cap、时间预算或账号额度会保存进度并明确停止原因；不会把免费100万条元数据理论量设置成每天全文下载目标。
若需要之后跑满更大预算，调参数并比较实际有效新增，不改每批100的语义。

## 变更文件清单

本主计划仅编排以下10份子计划；不直接授权业务代码修改。每份子计划各自列出≤10个具体文件、≤2个子系统，独立验收；总体文件并集不套用单子计划上限。

| 顺序 | 子计划 | 文件数 | 依赖 |
|---|---|---:|---|
| 01 | [OpenAlex 认证与共享额度](01-openalex-auth-budget.md) | 8 | 无 |
| 02 | [发现进度保护与真实任务状态](02-discovery-checkpoint.md) | 8 | 01 |
| 03 | [Crossref 与 arXiv 入口修复](03-crossref-arxiv.md) | 8 | 02 |
| 04 | [CORE、ORCID 分页与研发范围](04-core-orcid-scope.md) | 10 | 02、03 |
| 05 | [保留可信作者身份](05-author-identity.md) | 10 | 01 |
| 06 | [定向补全与三层结果契约](06-targeted-enrichment.md) | 9 | 01、05 |
| 07 | [可恢复的补全任务存储](07-enrichment-job-store.md) | 7 | 06 |
| 08 | [发现后自动补全与人工补偿](08-auto-enrichment.md) | 9 | 02、05、06、07 |
| 09 | [公平额度与逐步扩量](09-discovery-throughput.md) | 8 | 01、02、03、04、08 |
| 10 | [开放全文回退与漏斗准确性](10-fulltext-yield.md) | 10 | 02、05 |

## 验收标准

- I-1：101位专家补全为100+1，两批全部完成；来源上限、全局上限、作者上限分开测试。
- I-2：首请求失败、第二页失败、半页取消、RAW成功后入队失败、重启重放均不跳页；唯一新增不翻倍。
- I-3：无ORCID但有可信A ID可补；同名/首字母冲突不补错人；所有写入以真实_id定位。
- I-4：RAW-only补全成功可复评；已存在三层只局部更新；APPLICATION-only不回建候选；运营字段保持。
- I-5：全部源失败为FAILED，部分失败为PARTIAL_SUCCESS；额度等待、无匹配、网络失败可区分，恢复后可继续。
- I-6：默认研发范围、邮件流程、免费预算与原人工入口回归通过。
- 核心回归使用Java11；子计划定向测试通过后再跑全量mvn test；07须MySQL/Flyway集成，不用mock冒充持久化恢复。
- 在线小样本必须覆盖两个来源、一个无ORCID作者、一次故障续跑；生产大任务开始前检查邮件调度不会因测试新候选自动外发，验收优先使用独立测试环境。

## 人工验收清单

### A-1：自动完成一条新专家链路
- 前置条件：测试环境，邮件自动任务关闭，测试源提供101位唯一专家，有效Key，自动补全开启。
- 操作步骤：运行一次深度发现；不点击人工补全；等待两个补全批次；打开专家详情与任务记录。
- 预期结果：发现成功101；补全按100+1处理；可匹配者显示学术指标；两类任务计数不叠加；无邮件发出。
- 覆盖：R-1/R-3、M-1、I-1/I-4。

### A-2：故障/重启恢复
- 前置条件：测试源两页各2人，第二页首次503，另设置一次RAW已写入后任务入队失败。
- 操作步骤：执行任务；重启；恢复接口与DB；再次执行相同条件。
- 预期结果：唯一新增共4人、补全任务每人1条；不存在跳页；全失败/部分失败状态与事实一致。
- 覆盖：R-2、I-2/I-5，游标→RAW→job交互。

### A-3：身份和运营数据回归
- 前置条件：无ORCID有A ID专家、两位同名歧义作者、已有APPLICATION专家各一例。
- 操作步骤：发现并补全，比较处理前后详情和三层记录。
- 预期结果：A ID专家可补；歧义作者标未匹配；APPLICATION专家不新增候选；姓名、邮箱、机构、运营状态不变。
- 覆盖：R-4、M-3、I-3/I-4。

### A-4：多来源与公平额度
- 前置条件：四个论文源各至少100条，globalCap=400，page=100；默认研发scope。
- 操作步骤：运行发现；检查来源统计和请求条件；再分档验证5000/10000。
- 预期结果：四源均搜索100条，总数400；EuropePMC/PMC OA不参与；真实公网验收只要求恢复来源有论文，不强制每源新增>0。
- 覆盖：R-2/R-5、M-4、I-1/I-6。

### A-5：人工入口及配额
- 前置条件：旧候选专家、自动补全失败任务；测试代理可模拟remaining=0，Key仅由环境变量提供。
- 操作步骤：运行原补全三个scope及待补重试；模拟额度耗尽再恢复。
- 预期结果：原scope保留；失败可恢复；未付费；Key不出现在日志或任务详情；额度耗尽期间不密集重试。
- 覆盖：R-1/R-3、M-2/M-5、I-5/I-6。

### A-6：全文回退和统计
- 前置条件：首选PDF404、备用PDF含唯一邮箱；另有HTML无邮箱及11MB PDF。
- 操作步骤：运行该测试论文集合，查看来源任务明细与专家数。
- 预期结果：备用PDF成功案例仅新增1人；HTML归无邮箱；超限PDF仍拒绝；分别显示下载失败类别。
- 覆盖：R-6、I-3/I-6。

人工验收开始时才导出勾选版，不在计划阶段生成第二套权威清单。

