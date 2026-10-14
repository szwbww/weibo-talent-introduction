# 01：新发现专家的“其他地区”筛选修复

主约束：[MAIN 主计划](batch-filter-main.md)。实施顺序、共享文件、联合验收和交付边界必须同时遵守MAIN；本文件仍为本阶段精确修改范围。

状态：方案完成，未实施。2026-10-14。顺序：01 → 02 → 03；各自验证。范围4文件、1个批量外联子系统、零持久字段。证据见[审计](../../audits/2026-10-14-batch-filter-transparency/report.md)、[检索记录](../../audits/2026-10-14-batch-filter-transparency/grep-evidence.txt)。

## 需求描述

1. 新发现专家国家为空、空白或无法映射时，选择“其他”即可通过地区条件；预估、ES新目标、NEW重试结论一致。
2. 保持明确国家的地区筛选、非新发现规则、准入/发送保护和任务快照不变。
3. 范围外：线上数据写入、真实发信、国家推断、全站地区搜索改造、准入政策放宽、人物合并。通过地区条件不代表已满足全部发送条件。

## 关键不变量

### Invariant I-1: Other 是未知国家的地区桶
- Rule: 对含 discovered 标签的专家，国家 null、空白、Other、未识别值均按 REGION_OTHER 判断；regions 非空时仅在包含 Other 时通过。已识别国家不能因选择 Other 而放行。regions 为空维持不限地区。
- Applies to: RecipientScope.matchesRegion；预估、新目标和重试读取它的路径。无数据写入。
- Violation consequence: 页面已勾选仍静默拒绝，或未选地区被误放行。
- 来源: K-region-constant-not-display-label；本轮 F1。

### Invariant I-2: ES 粗筛不能提前丢掉应通过的专家
- Rule: Other 被选中时，批量入口的现有地区查询增加 discovered 且 country 不存在的分支；最终仍由同一个 RecipientScope 判定。非 discovered 分支、country/nationality 原有语义不变。空白或未识别非空值由既有 Other 查询覆盖。
- Applies to: ManualInitialOutreachService.buildEsFiltersForLevel；countEsTargets；实际ES目标遍历；NEW重试。
- Violation consequence: 内存规则修好但查询结果永远不含目标。
- 来源: K-batch-send-filter-retry-parity。

### Invariant I-3: 本修复不改变准入和发送事实
- Rule: 不写国家/国籍/标签/准入，不变更身份哈希、人工决定、退订、绑定账号、已发送、永久失败、模板和邮箱开关；执行仍使用开始时的配置快照，保持真实docId/ORCID去重。
- Applies to: 两处被修改的读取函数及其调用方。
- Violation consequence: 筛选修复变成误准入、重发或改写专家资料。
- 来源: K-batch-task-config-snapshot-log-identity；K-dual-outreach-paths；原始用户边界。

## 现状审计

### 三层 ES 档案与任务配置
- Mapping：`src/main/resources/es/orcid_info_raw.json`、`orcid_info_candidate.json`、`orcid_info_application.json`；dynamic=false；country、nationality、tags 为 keyword。country 缺失不等于字面值 Other，但本任务地区分组应一致。
- 写路径：`ExpertIndexService` 导入/单项更新；`ExpertIndexWriterService` 单项更新、状态/分类bulk、`promoteToCandidate`、`promoteToApplication`、`projectDiscoveryCandidate`、`reconcileDiscoveryCandidate`、`demoteToRaw`；发现、重验、人工决定及补全服务通过这些写入口更新/投影。历史运维脚本可能直接写ES。本计划不调整任何写入口或 mapping。
- 读路径：`ExpertSearchService.regionFilter/toExpertProfile`；`RecipientScope.matchesRegion/matchesExpert`；`ManualInitialOutreachService.buildEsFiltersForLevel/countEsTargets/buildRetryableTargets`。共享搜索函数仍服务其他标签/页面，不在本计划修改。
- 任务配置：既有 `batch_send_task_config` 由配置服务、旧API适配和迁移写入；快照由 `BatchExecutionSnapshot` 固化，筛选使用英文地区常量。无新增字段、无配置回填。（来源: K-batch-config-legacy-adapter-field-preservation）
- 交互 X1：写入空国家的新发现 → ES候选粗筛 → profile → 内存地区条件。
- 交互 X2：同一个配置快照 → 预估、NEW重试和新ES目标。必须保持同一地区语义。

### 缺陷基线
- `BatchExecutionModels.kt:236` 对 discovered 的空国家和 REGION_OTHER 直接拒绝，覆盖了页面选择。
- `ExpertSearchService.kt:147` 的 Other 分支要求国家/国籍存在；仅修内存匹配仍会漏两者皆空的记录。
- 2026-10-14 审计快照中4,878人的国家已标Other但仍被该分支拒绝；TW/HK/MA四人是明确未选地区，不能借此放行。此数字是冻结证据，不作为每次部署的实时断言。

## 实现方案

1. 修改 `BatchExecutionModels.kt`：对 discovered 使用 country 经现有 CountryContinentMapping 得到地区，再判断 regions；删除空值/Other的无条件拒绝。保留非 discovered 原分支。遵守 I-1、I-3。
2. 修改 `ManualInitialOutreachService.kt`：仅当 Other 被选中，为既有地区粗筛包裹 should 分支：现有地区查询 OR（tags=discovered AND country不存在）；minimum_should_match=1。不扩大其他筛选条件。消费上述既有ES写入数据，不引入写入。遵守 I-2、I-3。
3. 在 `BatchRecipientSelectionServiceTest.kt` 覆盖 null、空白、Other、未知国家、已知国家与 Other 开关/空regions/非discovered矩阵。在 `ManualInitialOutreachServiceTest.kt` 验证ES查询结构、预估/新目标/重试同口径及准入保护。使用本地fixture/mock，不调用SMTP。遵守 I-1～I-3。

## 变更文件清单

|序号|精确路径|变更|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt|地区精确匹配|
|2|src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt|ES粗筛|
|3|src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt|地区矩阵|
|4|src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt|跨入口回归|

## 验收标准

- I-1：矩阵逐条断言，null/空白/Other/未知值仅在 Other选中或regions空时通过；Japan不能因仅选Other而通过。
- I-2：ES查询结构与最终matcher使用相同fixture；null国家、null国籍的discovered不被粗筛丢失；非discovered行为不变。
- I-3：现有身份、人工决定、去重和快照测试通过；mock断言无新增ES/准入写入；非目标标签回归。
- 使用 JDK11 运行上述两个测试类及现有相关地区测试。无需生产发送验证。按X1/X2准备本地数据，验证两条取数路径。01可独立交付，02不能成为01的运行依赖。

## 人工验收清单

### A-1: 未知国家与其他
- 前置条件: 测试环境建4个discovered、MANUAL_APPROVED、未联系且模板字段齐全的专家；国家分别缺失、空白、Other、UnknownPlace，国籍均空；关闭邮箱验证和历史邮箱过滤。
- 操作步骤: 1. 任务仅选Other；2. 查看预估；3. 在禁用SMTP的测试执行器取目标；4. 取消Other并只选Europe重复。
- 预期结果: 前三步地区通过4人；Europe时地区通过0人；档案country原值不变。
- 覆盖: I-1、I-2、I-3、X1。

### A-2: 明确地区与其他标签
- 前置条件: 测试环境准备country=Japan和country=Morocco的discovered，另有国家缺失的非discovered；记录非discovered旧版筛选结果。
- 操作步骤: 1. 只选Other；2. 只选Japan/Korea；3. 清空地区；4. 比对非discovered结果。
- 预期结果: 两个明确国家在步骤1均不通过；步骤2仅Japan通过；步骤3二者地区均通过；非discovered与基线相同。
- 覆盖: I-1、I-3、需求2。

### A-3: 重试、快照和保护
- 前置条件: 测试环境让A处于ES新目标、B处于可重试NEW联系人，二者discovered/Other；另准备人工REJECTED和抑制邮箱各一条；使用不发送邮件的执行器。
- 操作步骤: 1. 选择Other预估；2. 固化执行快照；3. 修改编辑器为Europe；4. 查看该快照的目标和排除结果。
- 预期结果: A/B地区均通过；该执行仍按Other；REJECTED与抑制目标保持不可发送；无新生产邮件或身份更新。
- 覆盖: I-2、I-3、X2、需求2。
