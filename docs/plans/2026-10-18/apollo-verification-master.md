# 深度发现 Apollo 辅助核验与日志开发总计划

日期：2026-10-18。状态：**待审阅；本轮只产出计划，未实现、未部署业务功能。**
代码基线：`e7c3422c176be971d823e2b82b1bca63ebb04741`。批准的视觉基线：[线上弹窗预览](http://150.158.92.103/talent/apollo-verification-preview-20261018.html?v=2)，[冻结 HTML](apollo-evidence/approved-preview.html)，[配置截图](apollo-evidence/apollo-online-modal-config.jpg)，[日志截图](apollo-evidence/apollo-online-modal-logs.jpg)。截图中的统计数是演示值。

## 需求描述

O-1：深度发现新收录档案，在既有自动准入处增加资格核验；已有可靠证据足够者直接准入，其余按开关进入 Apollo 辅助核验。确认本人且资格符合记“已明确”；确认 Apollo 本人但资格缺证可记“未明确”准入；身份未闭环不准入。

O-2：同一线上深度发现弹窗，清楚展示“自动准入、直接准入、Apollo 准入、已明确、未明确、待核验、错误、排队”，可下钻批次、逐条证据和完整原因。数据持久化，重启、重试、完成后异步补核不重计、不串执行。

必须保持：N-1 七个平台、关键词、高级选项、定时/连续运行入口及原任务状态；N-2 专家审核和审核记录可用，人工决定优先；N-3 SBIR、已有联系/发信/回复、APPLICATION、旧准入档案不被这次新流程改写；N-4 邮箱、原始身份凭证、运营字段和邮件发送流程不被 Apollo 改写；N-5 旧任务历史不编造 Apollo 统计，其他任务弹窗保持原列与行为。

不在本轮：P2 抽取失败/IDENTITY_UNRESOLVED 召回优化、PDF 下载量优化、来源改造；历史 41,844/48,867 档案恢复或全库重跑；收费 Apollo enrich/complete-person/邮箱手机号解锁；新增通用搜索引擎、爬全网或用 LLM 猜学历年龄；新发信动作；重做专家列表/仪表盘。

**能力边界必须向使用者如实展示。** 代码目前没有 Apollo 生产接入。现有 Apollo 响应包含遮蔽姓氏，既有 ORCID 采集对象没有学历/当前职称。首版新增 Apollo 搜索 + 有绑定依据的 ORCID 公开履历补证，已有可核对证据可复用；没有可靠来源时保留待核验。不会承诺 55% 自动准入率；历史 100 份的 36+19 是人工复核建议结果，不能当自动模型准确率。扩展其他官网抓取器需要另案、带标注样本验收。

## 关键不变量

### Invariant I-1: 新口径优先，身份与资格分开
- Rule：本计划采用 2026-10-18 用户已确认的放宽口径；不是“博士或年龄缺失一律拒绝”。决策矩阵如下；Apollo 候选≠确认本人，未明确≠待核验。
- Applies to：规则引擎、自动结论写入、重验、审核只读投影、所有日志。
- Violation consequence：同名误准入，或已准入的人被错误列为未通过。
- 来源：原始需求及 `2026-10-17/apollo-historical-expert-verification.md` 最新准入口径覆盖。

|可靠身份|可靠资格|Apollo 本人匹配|结果|来源/明确性|
|---|---|---|---|---|
|已闭环|符合|不需要|自动准入|DIRECT / CLEAR|
|已闭环|符合|已确认，且确实用到 Apollo 核验|自动准入|APOLLO / CLEAR|
|已闭环|缺证|已确认|自动准入|APOLLO / UNCLEAR|
|已闭环|缺证|未确认或未启用|待核验|NONE / UNKNOWN|
|任何|确凿不符合或证据冲突|任意|待核验，不自动准入|NONE / NOT_MET 或 CONFLICT|
|未闭环|任何|只返回候选/同名|待核验|NONE / UNKNOWN|
|命中保护|任意|任意|保护跳过|NONE / NOT_EVALUATED|

这里“确凿不符合”不能由缺字段推得；人工仍可走现有审核机制。直接准入没有“未明确”分支。

### Invariant I-2: 资格事实不能推断
- Rule：当前本人 Professor/Prof./教授类职称按用户放宽规则认可（含 Assistant/Associate/Teaching/Adjunct Professor）；无当前教授类职称，须已取得博士且可靠年龄 <40；≥40 还须副教授及以上的可靠当前职称。恰好 40 按 ≥40。博士生/候选人/在读、历史教授、访学于教授课题组均不能误判。无可靠生日/年龄来源不从毕业年、照片推算。`employment` 任意自由文本及旧 `degree/age` 无来源时只作线索。
- Applies to：规则、证据 DTO、逐条详情。
- Violation consequence：把错误学历/年龄当已明确。
- 来源：原始需求；K-paper-affiliation-is-not-employment。

### Invariant I-3: 只作用新批次
- Rule：只有新账本中 `mode=ENFORCE` 的档案适用新资格门槛；无账本、AUDIT 模式、旧档案沿用原行为。不修改现有全局 `POLICY_VERSION` 使存量自动触发重算；新路径独立版本 `APV_20261018_V1`。功能关闭只停止新登记/新查询，不撤销已提交准入，不使已纳管待核验档案回落旧宽松门禁。
- Applies to：接入点、policy、重验、回滚。
- Violation consequence：全库历史准入改变，或关开关绕过门槛。
- 来源：K-discovery-scope-query-and-admission。

### Invariant I-4: 准入真值与 ES 投影分开
- Rule：`expert_discovery_admission` 仍是准入真值。新账本存证据、来源和状态，不另造 `AUTO_PASSED` 以外可发送准入枚举。MySQL 准入提交成功记“自动准入”；候选投影是独立状态 `PENDING/PROJECTED/ALREADY_PRESENT/APPLICATION_PRESENT/ERROR`。`sync.ok=true` 不等于新增候选；`NOT_ADMITTED` 绝不记投影完成。同步失败必须显示“候选同步待处理”，不冒充已进入候选索引。
- Applies to：提交服务、共享 writer、汇总 API、重启恢复。
- Violation consequence：写失败仍报成功、重复晋升、准入与候选数无法解释。
- 来源：K-promotion-source-passthrough；审计 `DiscoveryReviewService:1014`。

### Invariant I-5: 人工与业务保护优先
- Rule：提交前重读身份/RAW、当前 admission revision、三层状态及联系保护。相同身份 MANUAL_APPROVED/LEGACY_APPROVED/HOLD/REJECTED 不覆盖；身份变化使旧证据失效，不能复用。SBIR、已有联系人（含邮箱别名）、APPLICATION、已有自动准入的旧档案跳过本轮新登记；读取保护失败=ERROR，不能当作无保护。准入服务不得修改 contact/mail、运营标签或原始身份邮箱字段。
- Applies to：登记、worker、事务提交、重验、人工撤销竞态。
- Violation consequence：覆盖人工拒绝、污染已联系人员或跨人准入。
- 来源：K-author-identity-needs-email-evidence、K-sbir-direct-discovered-import。

### Invariant I-6: 幂等账本与事务边界
- Rule：档案主键是实际 ES `_id`；身份哈希复用 DiscoveryReviewIdentity，证据指纹另含 ORCID/机构/当前事实。账本唯一键 `(expert_doc_id, identity_hash, policy_version)`，首次归属 execution 不漂移。MySQL 状态写与准入更新同事务；网络/ES 不在 MySQL 持锁事务中。领取、完成、重试均校验 lease_token + generation；旧 worker 的回包影响 0 行。
- Applies to：repository、worker、自动提交、跨运行重复。
- Violation consequence：重复计数、晚到结果覆盖新决定。
- 来源：K-discovery-reentry-keeps-run-accounting、K-task-activity-execution-identity。

### Invariant I-7: RAW 与账本断点可恢复
- Rule：对确定为新档案、已有 docId 的输入，先写 RESERVED 意向再写 RAW；RAW 成功后置 QUEUED。意向失败禁止写 RAW/调用旧准入；RAW 失败不纳入档案分母。崩溃后从意向按 docId+identityHash 核对 RAW；存在且一致才激活，缺失仍等待来源重放；不重新搜索全库。重复分支只能补登记本账本已保留意向，不能把所有历史重复档案装进本轮。
- Applies to：论文分支、ORCID 分支、队列消费、重复恢复。
- Violation consequence：RAW 已写但漏核验，或后续重验绕过。
- 来源：K-page-commit-must-cover-consumer-failures。

### Invariant I-8: 统计单位及互斥分桶
- Rule：页面统一写“档案数”，不是论文数、请求数或自然人数。每条成功激活账本只有一个本次核验结果桶；自动准入是本次成功提交事件，后续人工变化另显示当前决定，不改写历史计数。自动准入 = DIRECT_CLEAR + APOLLO_CLEAR + APOLLO_UNCLEAR；已明确 = DIRECT_CLEAR + APOLLO_CLEAR；未明确 = APOLLO_UNCLEAR；Apollo 准入 = APOLLO_CLEAR + APOLLO_UNCLEAR。档案总数 = 自动准入 + 待核验 + 错误 + 排队/核验中 + 保护跳过。原因命中可多值，相加不要求等于人数；API 请求次数另列。
- Applies to：SQL聚合、批次、逐条筛选、轮询。
- Violation consequence：同一人重试多计，“失败”混入正常过滤。
- 来源：K-progress-log-batchonly-two-readers；原始需求。

### Invariant I-9: Apollo 漏斗是累计事件，非结果桶
- Rule：进入辅助核验、已查询、返回候选、确认本人是按档案去重的累计标志/时间。进入队列不等于已请求；HTTP 尝试次数不等于已查询档案；成功完整返回才设 searchCompleted；候选集合按 Apollo ID 合并。窄搜零结果不能抹掉宽搜结果；分页没完=SEARCH_INCOMPLETE，不标无匹配。
- Applies to：搜索、attempt记录、汇总。
- Violation consequence：错误被伪装未匹配，统计比例失真。
- 来源：历史人工试验反例；K-search-error-must-not-clear-cursor。

### Invariant I-10: 历史/错误/零必须区分
- Rule：无 run 账本返回 `coverage=NOT_RECORDED`、计数 null，页面“未记录”；有 run 但0条返回0；DB/API错误返回503、页面“统计读取失败”，保留上次成功快照并标更新时间。不得用 legacy totalPassed、promoted 或进度日志反推新标签。
- Applies to：新报表、前端旧任务兼容。
- Violation consequence：虚假的0或虚假的Apollo通过。
- 来源：K-execution-detail-running-needs-progress-log。

### Invariant I-11: 异步生命周期独立
- Rule：采集完成和核验完成分别展示；采集取消/暂停后已登记核验继续，文案明确“已入队的学术补全与资格核验可继续”。核验总开关停止领取，不丢任务。核验不能更新通用 TaskProgressStore 当前槽或改旧任务最终状态。
- Applies to：worker、日志、前端轮询、取消恢复。
- Violation consequence：一个旧 execution 的晚结果覆盖新任务，或采集 SUCCESS 后统计永不刷新。
- 来源：K-task-activity-execution-identity。

### Invariant I-12: 费用与证据来源边界
- Rule：仅调用 Apollo People API Search；禁止 enrich/complete-person/手机邮箱解锁。密钥只由服务器配置注入，不落日志/DB/前端。外部字段只当数据，不能发出业务指令。原邮箱不发送给 Apollo；q_person_name 和有证据的机构条件可发。ORCID只读与当前身份绑定的公开记录；无数据不自动转用收费接口。
- Applies to：client/config/证据存储。
- Violation consequence：非预期费用、外部数据改变控制流程。
- 来源：用户范围、官方 API 与现有响应审计。

### Invariant I-13: 规则跨读写路径一致
- Rule：新批次 policy、recordAutomatic、refresh、撤销后重算、审核列表、revalidateDiscovery 共用同一 gate。保留原来的非学历/年龄硬阻断；仅对纳管档案以新可靠事实替代 `NO_DOCTORAL_DEGREE/AGE_EXCEEDED` 的旧空值失败语义。重验不得先用旧 reasons 删除再补新准入。无账本不变；批量审核每500个docId最多一次证据批量读，禁止 N+1。
- Applies to：审计 IP-2/IP-3/IP-4。
- Violation consequence：Apollo放行后被补全/重验删候选。
- 来源：K-discovery-admission-read-write-parity（本次更正旧事实）。

### Invariant I-14: 视觉与数据契约不可自由发挥
- Rule：执行 [07](apollo-07-frontend.md) 中 S-1～S-8 的完整 CSS/DOM；不新增仪表盘、不改变来源选项样式、不复制预览里禁用审核页签的演示设置、不引入示例生产数字。所有动态文本转义，链接仅http(s)，分页筛选在服务端。
- Applies to：前端。
- Violation consequence：偏离批准的线上样式或错误展示他人的运行数据。
- 来源：K-panel-bg-token-is-translucent、K-batch-checkbox-global-input-height、用户明确要求。

## 现状审计

完整逐项审计见 [代码与存储审计](apollo-code-audit.md)。所有“现有/没有/只有”判断限于固定 HEAD、`src/main`/`src/test`/`scripts` 搜索范围；[原始检索回执](apollo-evidence/manifest.json)保留命令与输出。线上当前运行配置、账户实际限额、本次真实成功率没有读取证明，不作确定陈述。

关键证据：

|发现|代码证明|计划处理|
|---|---|---|
|现有资格只含可选博士/年龄过滤，没有固定40岁职称矩阵|CandidateEligibilityService:18–81；DiscoveryAdmissionPolicy:50–98、216|新 gate，旧批次不变|
|新收录统一准入点在 RAW 后|ExpertDiscoveryService:1450–1456、1779–1792、2452–2485|在此登记/接入；失败不推进|
|缺姓名在 RAW 前拒绝|ExpertDiscoveryService:1746、2270|不宣称 Apollo 能修复该失败；P2另做|
|“本批通过”是 indexed 差值|ExpertDiscoveryService:982、1466；app.js:4384|单列采集历史；新账本统计准入|
|重验仍独立读旧 eligibility reasons|ExpertRevalidationService:340–418|纳管路径必须 gate 统一|
|批次日志只截取前3原因并截断显示|app.js:4368–4390|新日志完整展开；旧采集原因也完整展开|
|业务代码没有 Apollo|`rg -n -i apollo src/main src/test` exit1，[回执](apollo-evidence/apollo-backend.txt)|明确新增，不假设有后端接口|
|旧异步晋升统计只读ES是否存在|DiscoveryPromotionProgressService:73–110|保留采集含义，不能作Apollo统计来源|

## 实现方案

按下列顺序逐片执行、验证；每片可在总开关关闭时独立部署。前片不能引用尚未创建的后片类。新表允许完整建模；**现有共享表/ES索引新增列/字段为0**，criteria JSON仅新增一个 `apolloAssist` 属性。各片具体文件和验收只以该片清单为准。实施范围为七片文件清单的并集；05本次仅增加现有调用方测试授权，不扩大生产代码范围。

|顺序|子计划|文件上限|独立交付|
|---|---|---:|---|
|01|[账本、状态、租约](apollo-01-ledger.md)|6|新增表及持久化契约；不接业务写入|
|02|[Apollo 搜索、证据与规则](apollo-02-evidence.md)|9|离线回执可重放；不改准入|
|03|[统一准入门禁](apollo-03-admission.md)|10|新批次 gate；旧批次回归|
|04|[异步核验与可靠提交](apollo-04-worker.md)|9|已登记任务完整处理/恢复；默认停用|
|05|[深度发现接入与启动快照](apollo-05-integration.md)|13（测试专项例外）|真实新任务登记；手动/定时/连续一致|
|06|[只读日志 API](apollo-06-reporting.md)|7|唯一统计口径及逐条分页|
|07|[线上弹窗与完整样式](apollo-07-frontend.md)|7|批准的布局、真实接口、交互完整|

数据契约：[表、状态、接口与样本](apollo-contracts.md)。07内嵌完整CSS，不以预览文件里的零散样式代替。

执行门槛：先复核 HEAD 差异、V158 是否仍空闲、目标环境新配置是否满足。发现基线变更影响结论则更新对应计划，不让执行agent临场改语义或扩大文件表。不需要为常规只读检查再问授权。

上线顺序：01～07代码通过各自验证 → AUDIT影子运行（不写准入/候选） → 固定样本人工核对 → 同一套代码启用ENFORCE新任务。AUDIT任务永不原地升级为ENFORCE，避免悄悄修改旧结果；正式输入重新登记新执行，AUDIT账本不占ENFORCE唯一键空间（见契约 `policy_version` mode后缀）。

验证效果：记录输入覆盖率、Apollo查询完成/候选/本人匹配/已明确/未明确、异常及费用相关请求数；人工复核所有灰度自动准入，误匹配必须为0才开放范围。报告样本规模和分母，不承诺未知人群准确率。P2必须另设同输入前后回放，当前计划不把“改名失败日志”当采集成功率提升。

回滚：停新登记、停止领取，保留账本与已有准入。**已有纳管档案时禁止直接回滚到不识别账本的旧 WAR**，否则旧重验可绕过或撤回；保留03兼容gate的回滚构建。不得DROP新表或批量删候选。

## 变更文件清单

本文件是编排计划，实施文件数0；允许实施文件逐条列在01～07。公共契约/审计/证据为计划材料，不是额外实施授权。[结构自检回执](apollo-evidence/plan-self-review.json)记录原始计划文件并集、CSS摘要与逐片条目数；这些是原始计划检查，不是修订后范围或应用测试通过。总范围按当前子计划清单并集计算；每片≤10，05因三个现有调用方测试迁移获批上限13的专项例外；其他超出仍需修订对应子计划。

## 验收标准

- I-1/I-2：矩阵每行和边界39/40/未知、博士生、历史教授、同名不同人均有独立 fixture 测试。
- I-3/I-5：无账本、AUDIT、SBIR、人工HOLD/REJECT、联系人、APPLICATION 的前后快照一致。
- I-4/I-6/I-7：真实MySQL并发/CAS及RAW/SQL/ES各断点故障注入；重启后同docId只计一次，候选写失败单独可见。
- I-8/I-9/I-10：123样本各维度对账，旧数据null，故障503；采集数字不能流入新统计。
- I-11/I-13：采集已SUCCESS后仍更新核验；重验/人工撤销/刷新和日志一致；无邮件发送调用。
- I-12：Mock HTTP断言仅允许Search，密钥/邮箱不出现在输出；401/403/429/5xx/超时语义覆盖。
- I-14：07 CSS逐字比对、DOM与实时浏览器检查；16个已有缓存版本资源保持数量/顺序，统一升级key。
- 逐片机器验证后做本节与各子计划 A-n。未实现时不得把这些测试标为已通过。

## 人工验收清单

### A-1: 123档案账本对账
- 前置条件：测试环境导入 contracts 中 F123 fixture；关闭外部真实网络；只读日志 API可用。
- 操作步骤：打开深度发现执行记录→展开 fixture任务→准入批次→逐条核验。
- 预期结果：档案123；自动55=直接15+Apollo40；已明确36、未明确19；待核验45、错误5、排队18。按“未明确”筛选19条，三批合计40/40/43；点击理由可看到完整CODE及中文。
- 覆盖：O-2，I-8/I-9/I-14，IP-1/IP-5。

### A-2: 准入口径与重验
- 前置条件：测试数据F-RULE，包括已绑定当前教授、Apollo本人且年龄未知、同名不同机构、Apollo身份尚未闭环的博士生；均无联系保护。
- 操作步骤：启动ENFORCE核验；查逐条结果；再执行既有学术补全、RAW晋升扫描和候选重验。
- 预期结果：前两者分别“已明确/未明确”自动准入；后两者待核验；重验后前两者仍AUTO_PASSED且候选存在；无新增邮件。
- 覆盖：O-1，N-4，I-1/I-2/I-13，IP-2/IP-3。

### A-3: 人工与历史保护
- 前置条件：独立测试实例中准备手工HOLD、REJECTED、MANUAL_APPROVED、LEGACY_APPROVED、SBIR、已有联系人、APPLICATION各1条；另有无账本旧任务。
- 操作步骤：跑核验及原人工审核/撤销；打开旧任务及其他类型任务。
- 预期结果：保护档案原决定与运营信息不变；审核页签可操作；旧任务Apollo栏“未记录”；其他任务仍原列；没有自动恢复历史档案。
- 覆盖：N-2/N-3/N-5，I-3/I-5/I-10，IP-3/IP-4。

### A-4: 中断、失败与真实效果
- 前置条件：测试HTTP可切换429/500，测试ES一次写失败；已登记至少3条；记录原采集执行ID。
- 操作步骤：暂停采集、重启服务、恢复网络；在采集已完成后重新打开同执行。
- 预期结果：采集状态不被核验worker改写；核验继续并归属原执行；错误单列、投影失败显示候选同步待处理；重试不增加档案分母/准入人数；无任何声称“错误减少=召回提升”的结论。
- 覆盖：N-1，I-4/I-6/I-7/I-11，IP-1/IP-5/IP-6。

### A-5: 线上结构与可读性
- 前置条件：07已部署到测试环境，浏览器1366×900、100%缩放；打开F123。
- 操作步骤：对照冻结截图；切换原3个页签；展开批次/下载量/证据；缩至390px宽；键盘Tab操作；关闭后换另一任务。
- 预期结果：保留原弹窗和来源控件；主色#1e40af、正文#1e293b；弹窗最大1180px、圆角18px；表头#f8fafc不重叠；只有弹窗纵向滚动，表格横向滚动；无卡片仪表盘；无串任务响应。
- 覆盖：N-1/N-2/N-5，I-14，S-1～S-8。

## 修正记录
- 2026-10-18，用户“批准该计划并执行”：批准03增加DiscoveryVerificationRepository.kt批量证据读取授权，文件上限同步9→10；该文件已在原并集中，实施文件并集不增加。依据I-6/I-13，保持真实docId证据一致及每500档案一次读取。修订身份和批准依据见 `docs/plans/fast/apollo-verification-master/ledger.md` Amendments。
- 2026-10-18，用户“批准该计划并执行”：批准04纳入既有OrcidVerificationEvidenceClient及其测试，文件上限同步7→9，补齐逐端点持久化/恢复接缝；原总计划I-6/I-9与请求许可、费用边界不变。修订身份和批准依据见 `docs/plans/fast/apollo-verification-master/ledger.md` Amendments。
- 用户“批准该计划并执行”批准05的C05-EXECUTION-CALLERS：仅增加ExpertDiscoveryControllerMvcTest、ExpertDiscoveryControllerTest、ExpertDiscoverySchedulerTest的真实execution/context调用迁移，文件上限10→13作为测试专项例外；生产文件清单、执行绑定与其他不变量不变。精确身份及批准依据见fast-p ledger Amendments。
