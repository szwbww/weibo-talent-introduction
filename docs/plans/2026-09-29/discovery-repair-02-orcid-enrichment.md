# 02：ORCID 多作者禁止任取一人补全

状态：待审阅，未实施。承接01；7个实施文件，2个紧密相关子系统（OpenAlex 查询、补全消费/队列）；无新增 DB/ES 字段。

## 需求描述

同一 ORCID 在 OpenAlex 对应多个作者 ID 时，补全明确返回身份歧义，停止写入该次学术信息；不再取首条或末条。单条人工查询、历史批量补全与发现后的自动补全使用同一判定。

保持：可信 OpenAlex 作者 ID 优先；唯一作者正常补全；已有三层局部更新与身份 CAS；邮箱/机构原有规则；429/额度延期/网络失败口径与租约；一人多个邮箱分别对应真实 ES 文档。范围外：凭名字选人、自动合并 OpenAlex 作者、删除现有学术数据、重跑全库、增加新状态列或新审核系统。

## 关键不变量

### Invariant I-1：先分组判身份，再读取学术事实
- Rule：ORCID 完整响应中不同有效作者 ID 数量为0→NotFound，1→Success 候选，超过1→AmbiguousIdentity。按规范化作者 ID 去重；顺序、相同姓名、引用量均不改变歧义结果。
- Applies to：enrichAuthorByOrcidWithReason、batchEnrichByOrcids。
- Violation consequence：Nobuhiro 再次收到天体物理/法律学主题。
- 来源：本轮真实 OpenAlex 响应。

### Invariant I-2：不完整响应不能证明唯一
- Rule：ORCID 查询按每页200条请求；只有 results 为数组、meta.count 为非负整数且等于返回数组长度时才能作唯一/未找到判定。截断、缺少 count、结构损坏返回 ApiError，不返回部分 Success/NotFound。明确查到多个有效 ID 也可统一等完整性检查后再分类，避免不同入口口径漂移。
- Applies to：单条/批量 ORCID 列表请求。
- Violation consequence：按输入人数截断，恰好只见同 ORCID 的一个作者后误判。
- 来源：batchEnrichByOrcids 当前 per_page=orcids.size 与实际25输入返回31作者。

### Invariant I-3：身份歧义为未匹配，不写事实
- Rule：新增 EnrichmentOutcome.AmbiguousIdentity 与 ProfileEnrichmentOutcome.AmbiguousIdentity；语义只表示本次无法唯一选择作者。worker 复用 UNMATCHED，last_error=AUTHOR_IDENTITY_AMBIGUOUS，result_json.outcome=AMBIGUOUS_IDENTITY；故障 attempts 不增加。该结果不调用学术写入、再资格核验或标题请求，也不把现有事实清空。
- Applies to：查询结果→enrichIdentityGroups→人工结果汇总/自动批次计数→job classify/resultJson。
- Violation consequence：歧义仍被当成功、无限即时重试或空值覆盖已有资料。
- 来源：K-enrichment-write-three-layers；现有 UNMATCHED 状态契约。

## 现状审计

### OpenAlex 响应（内存，非数据库）
- `OpenAlexDataSource.kt:442-454` 单条读取 results[0]；`:475-482` 批量 per_page 等于请求 ORCID 数；`:510-571` 共享批量函数逐节点赋值，重复 ORCID 由最后节点覆盖。
- 写：单条请求选 authorId 后调用详情；批量构造 EnrichmentOutcome，enrichmentOutcome 还可调用最近论文接口。
- 读：enrichAuthorByOrcid 的兼容包装返回 Success.data 或 null；ExpertDiscoveryService.enrichProfiles 按可信作者 ID 优先、否则 ORCID 分组，enrichIdentityGroups 消费结果。
- 实证：ORCID 0000-0002-2132-1327 实际同时返回 A5021048066、A5132247741、A5131605084；三者主题不同，两个完整姓名甚至相同。现有两个 Nobuhiro 档案的 top-5 与错误 A5132247741 相同。[核查摘录](discovery-repair-evidence/openalex-orcid-collision.json)。这不是原始 API JSON，已在源文件注明主题缩减；测试不能伪称逐字响应。
- 交互 P-1：列表分组→选作者→学术事实/标题；必须在调用后两者之前拒绝歧义。

### ES RAW/CANDIDATE/APPLICATION
- Schema：`orcid_info_{raw,candidate,application}.json` dynamic=false；researchFields/topic ID/学术计数/最近论文标题/分类及 enrichedAt 为现有字段；不新增身份状态字段。（来源：K-openalex-author-full-object）
- 受影响写入口：ExpertDiscoveryService.updateExpertAcademicFields 使用真实 docId，逐现存层部分更新并做身份 CAS；enrichIdentityGroups 成功后 revalidateDiscovery，可触发已有资格再核验/晋升。其他写入口包括发现 create-only RAW/直接候选、ExpertRevalidationService 晋升、ExpertIndexService 晋升与专家编辑/导入；本计划不改变这些写入口或字段来源，仅使歧义结果无法进入补全写入口。
- 读：ExpertDiscoveryService 历史扫描 enrichedAt/身份字段；enrichProfiles 按文档身份分组；ExpertSearchService 列表详情读取研究方向/学术计数；ExpertIndexController 导出；资格及发送筛选消费研究领域和分类。读者 schema 不变。
- 交互 P-2：多个相同 ORCID 的邮箱档案共享查询结果，但写入/跳过仍按真实 docId；任何歧义分组的全部文档均跳过，不能只跳过第一份。（来源：K-enrichment-write-three-layers）

### expert_academic_enrichment_job
- Schema：V131，expert_doc_id 唯一，status 现有 PENDING/RUNNING/RETRY_WAIT/SUCCEEDED/UNMATCHED/FAILED；result_json/last_error 已有。无需 migration。
- 全部写：repository.insertIfAbsent、reopenUnmatchedOrStaleSuccess、claimById、renewLeaseById、completeWithToken、reopenFailedById。继承 CrudRepository 写接口不新增调用。
- 全部读：findByExpertDocId、findDueCandidates、findDiscoveryDocuments 和继承按ID读取；OpenAlexBudgetRepository 统计待补全；ExpertDiscoveryService worker/自动批次汇总读取任务状态；人工补全使用逐人结果。
- 本次只有 completeWithToken 的输入语义增加；仍使用既有租约 CAS。UNMATCHED 不被 due 查询领取，但再次显式入队可依现有规则重开，本计划不额外添加永久封禁。
- 交互 P-3：ProfileEnrichmentOutcome→classifyBatchOutcome 与 JobService.classify/resultJson；均需补 exhaustive when。检索回执见 [enrichment-paths.txt](discovery-repair-evidence/enrichment-paths.txt)、[store-paths.txt](discovery-repair-evidence/store-paths.txt)。

## 实现方案

### T-1：ORCID 查询完整性与唯一性（I-1、I-2）

修改 `OpenAlexDataSource.kt`：给 ORCID 单条/批量复用一个文件内解析函数，规范化入参 ORCID/作者 ID，先完成响应校验与分组，再运行既有 enrichmentOutcome。批量每个入参必须有一个结果；不能返回额外身份。

使用 per_page=200，并核对 meta.count/数组长度。第一版不增加无界翻页或第二套限额重试框架；超过一页的批次整体 ApiError("ORCID_RESPONSE_INCOMPLETE")，按已有最多5次故障尝试机制可见失败；人工入口记录失败。此处是明确的保守边界，不能让“扩大页大小”取代完整性校验。

组内两个不同规范 ID 即 AmbiguousIdentity；完全相同节点重复不算两个人；同 ID 节点学术内容冲突时 ApiError，不靠覆盖顺序挑内容。关联到请求 ORCID 的节点若无有效作者 ID，返回该身份 ApiError。结构损坏不能当 NotFound。单条查询复用同一分组判定，唯一后仍可调用既有详情；兼容可空包装对歧义返回 null。

按明确 A ID 查询的分支保持语义，不为了 ORCID 去重修改作者 ID 的查询优先级。额度异常原样向上传递，429/503仍 RateLimited；不在本轮给 AuthorEnrichment 加姓名匹配字段。

### T-2：贯穿结果而不写学术字段（I-3）

修改 `ExpertDiscoveryService.kt` 中结果类型、enrichIdentityGroups、历史补全汇总与 classifyBatchOutcome：歧义映射 ProfileEnrichmentOutcome.AmbiguousIdentity；手动任务 failureReasons 记录 AUTHOR_IDENTITY_AMBIGUOUS，不计 enriched；自动汇总进 unmatched。跳过 updateExpertAcademicFields 和 revalidationService 调用。

修改 `ExpertAcademicEnrichmentJobService.kt` 的 classify/resultJson：复用 UNMATCHED + 固定原因码 + 固定 outcome。保留租约、重开与 attempts 语义。不要把它映射 NotFound，因为“有多个作者”不同于“查无作者”。

### T-3：可复核案例（I-1～I-3）

新增 `src/test/resources/discovery/orcid-collision-20260929.json`：保留证据内真实三作者 ID/ORCID/姓名/主题及出处；按 OpenAlex 字段格式转成传输测试输入时注明“由真实摘录构造的测试响应”，补入的 meta.count/summary_stats 不能冒充在线抓取值。

修改三个测试文件，覆盖单条与批量重复 ORCID、三个节点所有排列、同名不同 ID、相同 ID 重复、唯一/未找到、200条截断/缺少 count/坏节点、429/预算延期与详情失败。Nobuhiro 两档案同时断言三层写入0、再核验0、标题请求0；唯一作者正常补全与真实 docId 三层 CAS 沿用旧测试。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt | 唯一性、完整性、查询结果 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt | 结果贯穿与汇总 |
| 3 | src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobService.kt | UNMATCHED 原因/审计 |
| 4 | src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt | 传输输入回归 |
| 5 | src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt | 按文档跳过与保留写路径 |
| 6 | src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobServiceTest.kt | 终态/CAS/次数 |
| 7 | src/test/resources/discovery/orcid-collision-20260929.json | 真实摘录测试材料 |

## 验收标准

- I-1：三作者全部排列均歧义；单条和批量一致；唯一作者正常，重复同 ID 不误判多人。
- I-2：meta.count>results.size、缺失或不合法均0学术写入；不把未见作者标 NotFound；预算/429传递保持；不能仅改 per_page 而保留 last-wins。
- I-3：两 Nobuhiro 文档的任何层写入/晋升/标题请求均0；原有字段保持；队列 UNMATCHED、原因与 result_json 精确匹配；attempts 不增加；自动计数 unmatched=2、succeeded=0；过期租约不能提交结果。
- Java11：`mvn -Dtest=OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentJobServiceTest test`。同时 rg 全部 EnrichmentOutcome/ProfileEnrichmentOutcome 使用点，确保没有隐藏 else 将歧义误当成功。

## 人工验收清单

### A-1：两个真实档案均停止错误补全
- 前置条件：隔离环境用 fixture 构造同 ORCID 的两个邮箱档案及三作者响应；测试报告列出调用数和任务结果。
- 操作步骤：1. 分别运行人工单条、历史批量和发现后 worker 案例；2. 查看两档案前后学术字段；3. 查看队列结果。
- 预期结果：三个入口均身份歧义；原字段逐字不变；写入和再核验0；worker 两条 UNMATCHED、AUTHOR_IDENTITY_AMBIGUOUS，成功0；任务不即时自循环重试。
- 覆盖：I-1、I-3；P-1～P-3；需求结果。

### A-2：唯一作者与重试语义保留
- 前置条件：测试报告包含唯一作者三层存在、RAW-only、直接 A ID 优先、429、额度延期、过期 lease 六类案例。
- 操作步骤：1. 查看唯一作者更新层数；2. 查看 A ID 查询地址；3. 查看429/额度延期状态与 attempts；4. 查看过期 lease 更新行数。
- 预期结果：三层存在更新3层，RAW-only只更新1层；不凭空创建层；有可信 A ID 时不走 ORCID 猜人；429/额度延期进入 RETRY_WAIT且 attempts 不增加；过期 lease 更新0行；邮箱与机构不受歧义结果清空。
- 覆盖：I-3；P-2、P-3；保留行为。

### A-3：响应不完整
- 前置条件：隔离 API stub 返回 meta.count=201、results=200，以及缺失meta的响应。
- 操作步骤：1. 对两种响应运行 ORCID 批量补全；2. 查看结果与 ES 更新计数；3. 查看固定时钟的五次故障尝试报告。
- 预期结果：均无 Success/NotFound、ES更新0；最终按既有故障上限 FAILED，原因可查；没有无界翻页或无限即时请求。
- 覆盖：I-2；P-1、P-3。
