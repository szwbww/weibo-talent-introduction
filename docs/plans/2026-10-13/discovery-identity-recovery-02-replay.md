# 02：旧名单回源预览、受控恢复与留痕

状态：方案，未实施。依赖 01 和既有准入方案已验证。对应 R2/R4。

## 需求描述

按固定专家 ID 重新读取公开原文，生成可核对的恢复清单；确认进入实施后才应用其中安全子集。每条记录可解释来源、是否补证、是否准入和未处理原因。

保持：来源证据要求、人工批准/拒绝/暂缓、所有已填身份、业务主键、邮箱、联系人和邮件；没有来源者不猜填。范围外：自动纠正明确姓名/作者 ID 冲突、修复旧学术污染、绕访问限制、全量证据档案系统、改生产队列版本或清空队列、自动发送。

## 关键不变量

### Invariant I-1：重新取证，旧摘要不当证明
- Rule：仅接受真实 docId；来源 ID 从服务器存量 externalIds 读取。按 PMCID 的公开 JATS 或 DOI/PMID 精确获取论文元数据，再用现有提取器取邮箱归属。无精确来源标识者报 SOURCE_LOCATOR_MISSING；不按姓名搜索拼接。调用方不能提交 proof、作者名、任意下载 URL 或模拟原文。旧 extraction_json 不改版本重用。
- Applies to：preview/apply、来源查询。
- Violation consequence：用户输入或旧缓存伪装为新证据。
- 来源：当前 identityRuleVersion 门禁。

### Invariant I-2：有限应用范围
- Rule：同姓名缺证走 01。空姓名仅允许在原来源唯一确认、已有非空姓名部分相符、三层无身份/作者 ID 冲突、没有 existing proof、没有人工/历史准入决定、没有 APPLICATION、联系人按 docId/orcidId/email 都未命中、没有旧学术字段时补空字段及 proof；已填姓名从不覆盖。否则仅建议人工处理，不能自动清学术字段或改联系人。
- Rule：学术字段检查包括 hIndex/citationCount/worksCount/researchFields/researchFieldIds/recentWorkTitles/patentTitles/disciplineCategory/enrichedAt；不能只看是否有 researchFields。姓名补齐会改变 admission identity_hash，必须按既有规则重算，不能沿用旧人工决定。
- Applies to：恢复候选判定、条件补丁。
- Violation consequence：保留另一作者的画像或使旧批准跨身份生效。
- 来源：K-named-fixture-must-use-real-row、历史错绑审计。

### Invariant I-3：预览不写专家，应用复核来源及并发
- Rule：每次 API 只处理 1 个 docId，脚本顺序调度名单，避免一个 HTTP 请求遍历一万人。preview 无 ES/准入/联系人/邮件写；外部查询可产生既有请求额度台账。返回 snapshotHash、proposalHash、规则版本及逐层摘要。apply 只接受 docId 和两项 hash，在服务端重新取证重算；不同则 409，不能依赖客户端携带的证据。再以各层版本 CAS 应用。
- Rule：部分写入回报 PARTIAL，不刷新自动准入、不算完整成功；脚本保留前值及已完成目标供重试核对。重试按“前值或本次目标值”确认，遇第三种值停止；不跨层宣称事务。补证完成后原有准入重验失败同样单列，可重跑。
- Applies to：维护入口、repair service、报告脚本。
- Violation consequence：预览后数据变动被覆盖，部分写入被误报成功。
- 来源：01 I-2/I-3。

### Invariant I-4：来源、预算与报告守恒
- Rule：OpenAlex 调用必须走既有 getJson/requestPolicy，声明既有 RequestKind.DISCOVERY + Operation.SINGLETON（A10：/works/{id} 单实体读取在既有 policy 中补全为 SINGLETON 派生，0 credit；LIST/SEARCH/CONTENT 计价不变）；全文共用既有每篇时限、并发许可及地址数。429/预算延期记录 DEFERRED，不算不合格。仅公开来源，不解验证码。
- Rule：脚本每人一行 JSONL，记录 docId、观察时间、来源 ID/URL、元数据摘要、提取版本、proof source/hash、前后差异、各层结果、准入结果、失败原因；来源 URL/摘要不能声称是原文副本。输出目录 0700、文件 0600，文件名不含姓名邮箱；不记录认证头。报告失败则该条停止进入 apply。
- Applies to：所有查询及维护文件写路径。
- Violation consequence：越预算、无法复核、恢复人数重复计算。
- 来源：既有请求策略、task JSON 不承载全量名单的原则。

## 现状审计

ES schema/身份写读全集继承并重新核对 01：本份新增的唯一事实写入口仍是该 repair service + writer，不直接 REST 覆盖整文档。ExpertSearchService.findByDocumentIds/Writer.readDiscoveryDocument 可读三层；必须使用包含所有事实的后者。旧 tags 保留，不能靠改标签表达已准入。

2026-10-13 历史处置审计补充：I-2 联系人保护须同时调用既有 ExpertEmailAliasService.findContactByEmailOrAlias 检查别名引用；主邮箱/ID 无记录不能直接视为无历史关系。新增 RecoveryServiceTest 中必须有“仅别名命中”的禁止补名场景，不修改别名 service/repository。

V148 准入写读全集及 CAS 前提参见既有准入方案；本份只调用其 recordAutomatic/重验接缝。V131 仅调用 enqueue。expert_contact 读取复用 existsByOrcidId/findFirstByExpertEmailOrderByUpdatedAtDesc；对真实 docId、业务 orcidId 两项分别查，不把看似 ORCID 的键视为真实作者。联系人及邮件表写入 0。

公开来源：EuropePmcDataSource.extractAuthorEmails 接受 PMCID；OpenAlexDataSource 当前只有列表入口、parseResponse 内解析 work，需提取复用 parseWork 并补精确查询入口，不复制作者解析规则。OpenAlex 官方支持 DOI/PMID 单篇查询，已核对 [官方说明](https://help.openalex.org/api/get-single-entities/)；本方案不改 API 计价或额度策略（A10 例外：仅补全 /works/{id} 单实体读取的既有 SINGLETON 派生；LIST/SEARCH/CONTENT 成本不变）。

队列：V133 job 的 payload_json/extraction_json 由收集、saveExtraction、消费完成、clearTerminalPayloads/deleteTerminalJobs 写入；DiscoveryPipelineService.processJob 复用旧结果，consumeQueuedItem 不兼容则失败。本份不改上述队列，不依赖旧缓存一定存在。

新增维护文件 schema：manifest.json 固定 ID、观察时间和 digest；preview.jsonl/apply.jsonl/backup.jsonl 按 docId 记录。脚本先原子写备份再 apply，输出 append+flush；恢复重读日志按 ID 去重。CLI tests 和人工审核是读者；不是应用后台新的业务事实源。

交互 X1：存量来源 ID→精确元数据→现有提取器；X2：预览→原文/档案变更→apply CAS；X3：身份补齐→准入哈希变化→候选/发送预估；X4：联系人/旧学术字段→自动修复排除；X5：失败日志→断点恢复。无前端和 schema 变更；知识仅只读。

## 实现方案

1. I-1/I-4：OpenAlexDataSource 增加精确 DOI/PMID work 读取方法，抽出 parseWork 供原列表复用；核验返回来源 ID 一致，无匹配不做模糊后备。元数据 lookup 不等于已确认邮箱归属。PMCID 可直接经 JATS 回源；多个旧标识若相互冲突则停止。
2. I-1/I-2：新增 DiscoveryIdentityRecoveryService，按服务器文档定位论文，用现有来源提取器生成不可由客户端提供的候选；仅处理目标规范邮箱，要求唯一作者和真实证据。完整保存阻断原因，不把所有失败叫“资格不符”。
3. I-2/I-3：扩展 01 repair service 与 writer 支持受限“只填空姓名 + proof”模式；自动增量入口不启用此模式。存量明确冲突、有学术字段或联系人者只输出待人工处理，不假装本计划解决。
4. I-3：新增登录保护维护 API `/api/expert-discovery/identity-recovery/preview` 与 `/apply`，DTO 可同文件，actor 从会话取得；客户端不得指定 actor。preview 请求 docId，apply 增加两项 hash；401/400/409/503 分别保持明确。每调用只处理一人，禁调发信服务。
5. I-3/I-4：新增 Python 维护 CLI，默认 preview；读取冻结 ID manifest，调用 API、保存审计。apply 必须显式参数且引用 preview manifest；每条服务器重新取证。旧 10,686 清单首次只预览 200 人分层样本，随后全量预览；不自行运行生产 apply。
6. I-3：应用前备份、成功后回读逐字段；失败逐条续跑。回滚预案先停止应用并回退新入口代码；CLI 仅生成逐条事实回滚提案，不提供接受任意旧 proof/姓名的 HTTP 写接口。后续事实回滚需对当前值仍等于本次目标、版本和准入未变更者另行执行受控补丁；不能直接恢复旧哈希批准。本份不自动撤除已核实事实。

## 变更文件清单

相对仓库根，共 11 个（A10：第 11 个为既有计价策略的单实体派生补全）。无新的 DB/ES 字段。

|文件|用途|
|---|---|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt|新增：来源复查/预览/apply及DTO|
|src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryIdentityRecoveryController.kt|新增：登录及参数保护|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt|补空身份的受限模式|
|src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt|条件更新空姓名与proof|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt|精确来源读取及复用解析|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt|新增：跨路径恢复/冲突/旧学术保护/条件写入|
|src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryIdentityRecoveryControllerTest.kt|新增：认证/校验/hash变化|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt|精确查询/额度/来源校验|
|tools/discovery-identity-recovery/replay.py|新增：名单与本地留痕、应用调度、回滚提案|
|tools/discovery-identity-recovery/test_replay.py|新增：默认预览/日志恢复/备份失败保护|
|src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt|A10：为 /works/{id} 增加 SINGLETON(0 credit) 派生；LIST/SEARCH/CONTENT 计价不变|

## 验收标准

- I-1：真实样本原文回放、不 mock 身份解析；来源 ID 不符/无定位/原文无目标邮箱不签证；旧缓存摘要不能充当证明。
- I-2：补空成功用无旧学术/联系人样本；任一风险字段、已有非空冲突、APPLICATION/联系人、人工或历史 proof 均不自动改身份。
- I-3：preview 业务写 0；修改源/档案使 apply 返回 409；第二层失败不计整体成功；恢复不重复更改；人工状态/业务 ID/邮箱/运营数据不变。
- I-4：请求仍走额度控制；延期不转拒绝；人数与 manifest 去重总数守恒；报告保存失败不进入 apply；无秘密进入输出。
- JDK 11 执行本份三项 Kotlin 测试及 01 四项既有测试；Python unittest 测试脚本。旧测试文件仅运行，不暗增修改范围。本份 RecoveryServiceTest 须通过真实 writer + HTTP 替身覆盖新增条件写入，不能 mock writer 后宣称 ES 补丁已验收。样本原始证据引用既有审计资源；需新增大夹具时先拆计划，不临时加第 12 个文件（第 11 个已按 A10 授权）。

## 人工验收清单

### A-1：只读预览
- 前置条件：隔离 ES 导入 5 条：同名缺证、纯缺名、来源不可得、归属歧义、明确冲突；配置模拟来源及禁 SMTP。
- 操作步骤：CLI 默认模式跑 5 条；查看 JSONL 来源与差异；复读业务表/ES。
- 预期结果：最终结果 5 条、专家/准入/联系人/邮件写入 0；可恢复 2 条只是提案，不是已恢复。
- 覆盖：I-1/I-2/I-3/I-4，X1。

### A-2：安全恢复与身份哈希
- 前置条件：A-1 两条提案仍未变化；纯缺名记录无学术字段/联系人/有效层/人工决定。
- 操作步骤：显式 apply 两条；读取三层身份、准入和预估；再跑同清单。
- 预期结果：只补证/空姓名，docId/email 不变；哈希按当前完整姓名重算；重复不增专家；自动准入仍取决于硬条件；邮件 0。
- 覆盖：I-2/I-3，X3。

### A-3：来源/并发变化与保护范围
- 前置条件：preview 后改变源作者关系；另准备已有 hIndex、已联系、人工 HOLD/REJECTED、明确作者 ID 冲突的记录。
- 操作步骤：调用 apply；核对 HTTP 结果、报告及前后字段。
- 预期结果：源变化返回 409；风险记录不补姓名/不清学术/不改联系人；人工决定、邮件、原业务字段保持。
- 覆盖：I-1/I-2/I-3及所有保持项，X2/X4。

### A-4：断点、备份与预算
- 前置条件：模拟第二层故障、脚本中断、报告磁盘写失败、来源 429 四种情形。
- 操作步骤：读取日志后恢复；生成回滚提案并核对备份/当前值。
- 预期结果：部分状态保留、不假报成功；磁盘写失败无 apply；429 为 DEFERRED；复跑不重算已成功条为新增；回滚提案排除后来编辑，业务写入 0。
- 覆盖：I-3/I-4，X5。
