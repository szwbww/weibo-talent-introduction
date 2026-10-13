# 深度发现准入放宽方案

状态：仅方案，未批准实施。用户选择方案 1，并明确要求“不直接修改”。本次只新增审计输出与方案；不修改代码、线上配置、业务数据、知识库计数，不启动发送。

## 需求描述

邮箱与姓名有可信绑定的深度发现专家，允许在研究方向、国家、机构或机构来源凭证缺失时进入首发候选；资料缺失显示为提示。明确不符合目标、身份冲突、邮箱不可用者仍排除。存量与新发现使用相同准入口径。

保留：人工批准/暂缓/拒绝、有效历史认可；身份与来源真实性；既有邮箱验证/抑制/发送去重/发件账号限制；显式选择的收件人条件；非发现专家、人工单封回复、姓名/邮箱/机构原始事实及运营状态。

范围外：自动猜姓名、按邮箱前缀造作者、关闭退信或退订限制、自动认定所有历史档案可信、重写研发分类器、批量发信、UI 重设计。审核页超过一万条的统计窗口问题另案处理，不以不完整统计冒充准确人数。本方案的存量预览通过后台 scroll 完整扫描完成。

### 已确认现状与预期收益

线上 2026-10-13 20:57—21:00（北京时间）只读快照，任务运行中，人数随新增变化：

|口径|数量|解释|
|---|---:|---|
|全部发现相关档案，三层按真实专家 ID 去重|62,904|包含 discovered、待确认、发现来源/身份对象|
|其中未初始化准入|58,228|没有当前准入记录；批量选择器会排除|
|其中 NEEDS_REVIEW|897|已初始化，但当前准入仍待审核|
|其中已准入|3,779|593 AUTO_PASSED + 3,186 LEGACY_APPROVED；不等于最终可发送|
|明确带“待确认”标签|10,686|10,652 未初始化，34 历史认可|
|“待确认”标签中身份对象缺失|10,671|15 个仅有 VERIFIED 状态，仍需校验完整凭证；不能全部自动放行|
|“待确认”标签中缺 givenNames / familyNames 字段|4,980 / 4,973|两项可重叠；字段存在也不保证非空或可信|

对 897 条已初始化待审记录，以线上完整档案和实际生产校验类重算：全部有可信身份；410 条仅卡国家/机构资料；按本方案软化原因码后，786 条预计可通过准入，107 条研发范围明确不符、4 条来源 ID 冲突仍阻断。786 只针对这 897 条，不是对全部 10,686 或 62,904 条的承诺，也不是最终发送量。

原因人数可重叠：国家缺失 800、机构凭证缺失 782、机构缺失 742、研发范围未知 372、研发范围不符 107、研发证据不足 4、来源冲突 4 人（8 次原因命中）。897 条中 646 条补全任务已成功、251 条未匹配，单纯等待不能解决全部阻断。

另外，审核扫描少读 worksCount、recentWorkTitles、patentTitles、employment、keyword。4,642 条已初始化 discovered 档案对照中，1 条额外出现“研发证据不足”；该条仍因机构凭证缺失待审。此缺陷不是当前积压的主要数量来源。

证据：仓库 `output/discovery-review-audit-2026-10-13/` 下 scope-counts.json、summary.json、proposed-impact.json、pending-identity.json。BatchRecipientSelectionService、DiscoveryReviewScanService、DiscoveryAdmissionPolicy 的线上 class SHA256 与本地 target/classes 相同；结论来自生产类重算，不只凭本地源码猜测。

## 关键不变量

### Invariant I-1：区分缺资料与明确不合格

- Rule：仅将以下原因从 blockingReasons 移入 hints：COUNTRY_MISSING、COUNTRY_UNMAPPED、INSTITUTION_MISSING、INSTITUTION_EVIDENCE_MISSING、RND_SCOPE_UNCONFIRMED、RND_EVIDENCE_INSUFFICIENT。RESEARCH_DIRECTION_MISSING 保持提示。原始原因码与事实保留，提示不得标成数据已补齐。
- Applies to：DiscoveryAdmissionPolicy.evaluate；新发现、重验、列表解释、存量重新判定的全部调用。
- Violation consequence：只改展示不改准入，或把明确不合格混为未知。
- 来源：本次用户选择及线上审计。

### Invariant I-2：可信身份仍为前提

- Rule：自动放宽必须具有有效 VERIFIED 姓名—邮箱绑定；缺身份对象、凭证无效/版本不支持/姓名邮箱不一致、SOURCE_ID_CONFLICT、机构凭证 INVALID、RND_OUT_OF_SCOPE、RND_SERVICE_ONLY、非法/一次性邮箱保留阻断。未列入 I-1 的原因不自动软化；启用的既有资格配置仍按配置执行。
- Rule：仅带“待确认”且 DiscoveryIdentity.isDiscovery=false 的记录也必须检查身份对象存在，不能因 allowed 对非发现档案返回 true 而绕过。不得伪造 discovered 标签、VERIFIED 对象或作者 ID。
- Rule：已存在且身份一致的 MANUAL_APPROVED/LEGACY_APPROVED 按原授权生效；HOLD/REJECTED 维持。历史缺凭证记录只登记“身份待核验”，后续需原始来源核实或明确人工认可，不包含在本次自动放宽承诺中。
- Applies to：自动 policy、recordAutomatic、存量初始化、重验。
- Violation consequence：将旧脏数据或同名错误作者批量放行。
- 来源：本次用户“邮箱与姓名可信”前提。

### Invariant I-3：准入结论唯一，补全不会重新收紧

- Rule：首发准入只消费 expert_discovery_admission；不修改 CandidateEligibilityService/研发分类器的事实口径，不把 UNKNOWN 改成 ACADEMIC_RND，不为通过发送改写旧 filterResult。发现路径重验必须先形成新 policy 结论，再按该结论决定候选投影；不得因 I-1 的旧 eligibility 原因删除已准入候选。
- Rule：邮箱独立验证失败仍按既有流程阻断；本次不改变实际发送邮箱校验。人工与历史认可的保护分支保持。
- Applies to：admitDiscoveryExpert→recordAutomatic→projectApprovedCandidate；enrichIdentityGroups→revalidateDiscovery；统一批量 selector。
- Violation consequence：本轮通过、下一轮补全又退回，或准入通过但一直没有候选。
- 来源：K-batch-send-filter-retry-parity、K-enrichment-write-three-layers，已重新检索实际路径。

### Invariant I-4：存量覆盖完整，人工决定和并发修改不丢失

- Rule：扫描 RAW→CANDIDATE→APPLICATION，范围取 isDiscoveryOutreach 的并集，而不是只看 discovered 标签。按真实 _id 去重；同 ID 跨层姓名/邮箱不一致时报告冲突，禁止任取一层覆盖。RAW 不存在可读 CANDIDATE/APPLICATION，禁止把缺 RAW 当成已删除所有来源。
- Rule：只初始化无记录者、重算自动状态者；使用 recordAutomatic 的身份哈希与 revision CAS，保护同身份人工/历史认可。旧政策的 NEEDS_REVIEW 必须允许刷新，不能沿用 initializeAdmission 的 INSERT IGNORE 而跳过全部已有自动记录。
- Rule：CAS 冲突必须复读当前结论，不能使用竞争前已准入的旧返回值去投影候选。
- Applies to：扩展后的存量 worker、recordAutomatic 自动更新/初始化冲突返回。
- Violation consequence：大量旧记录继续被 UNINITIALIZED 拦截，或覆盖运营刚作出的拒绝。
- 来源：V148 实际 schema、recordAutomatic 源码审计。

### Invariant I-5：投影与实际发信分开

- Rule：准入成功后只补缺失候选，复用 projectApprovedCandidate/projectDiscoveryCandidate；既有候选不整行覆盖，APPLICATION 不反向复制；失败单列，可重试。全部判定与迁移过程发信数为零，不创建联系人/邮件。
- Rule：姓名、邮箱、来源凭证、学术分类、运营状态不因软化条件改写。已有人工降级记录按原系统规则保护；如现有投影不能辨认人工降级，应先在实施前核实其保护路径，不擅自重建。
- Applies to：重验与存量准入后的候选 create；迁移预览/执行。
- Violation consequence：将“已准入”误报为“已发送”，或覆盖运营信息。
- 来源：K-promotion-source-passthrough、K-expert-classification-one-object-three-layers。

### Invariant I-6：发送显式条件继续生效

- Rule：准入允许不等于最终发送。面向这批人的既有发送配置，需要显式包含 UNKNOWN、UNCLASSIFIED 和目标研发类型，研究方向选 ANY，地区选不限时才接纳国家缺失者；明确选择具体国家/类型时仍按选择排除。模板需要的字段不得用虚构值填充，应选不依赖缺失资料的模板。
- Rule：不修改所有批量任务默认值；仅在后续实施获准后配置本次指定任务。UNINITIALIZED 不在发送 selector 里临时放行，必须先形成实际准入结论。
- Applies to：预估、ES 目标、NEW 重试、旧首发任务配置与读取。
- Violation consequence：后台放宽无效果，或悄悄扩大发信对象。
- 来源：K-batch-send-filter-retry-parity、BatchExecutionModels.mismatchKeys。

### Invariant I-7：事实读取与新旧口径一致

- Rule：扫描/初始化提供 policy 和分类器消费的完整字段，至少补齐 employment、keyword、worksCount、recentWorkTitles、patentTitles。完整档案与审核读取对同一人产出的 blockingReasons/hints 一致。新政策版本使用独立值 2026-10-13.1。
- Rule：不新增平行 eligible 布尔，不新增 DB/ES 字段；dryRun/mode 仅为请求 DTO 与既有任务 JSON 的选项，不改变持久准入枚举。
- Applies to：DiscoveryReviewScanService、DiscoveryAdmissionPolicy、存量任务请求/结果。
- Violation consequence：完整数据合格却在审核页被误判，或变更后历史结论未刷新。
- 来源：本次生产类对照。

## 现状审计

### expert_discovery_admission / expert_discovery_review_item

- Schema：V148；admission 以区分大小写的 expert_doc_id 为主键，identity_hash、decision、revision、decision_item_id、policy_version；review_item 对 batch_key+expert_doc_id 唯一。无新增表/列。
- 全部当前结论写入：Repository.initializeAdmission（INSERT IGNORE）；Repository.applyItem（人工 CAS）；Repository.revokeCurrent（撤销后自动结果）；Service.updateAutomaticAdmission（revision+identity_hash CAS）。initializeAdmission 的调用包括 prepare、runPrepareWorker、recordAutomatic、runInitializeWorker。
- 审核明细写入：Repository.insertItem、markBatchReady、claimBatchItems、cancelUnappliedItems、applyItem、revokeCurrent、markItemFailed/Applied/Stale；Service.markCandidateSyncFailed/clearCandidateSyncFailure 仅更新明细投影错误。
- 读取：Repository.findAdmission/findAdmissions；ScanService.enrich 合并列表；Service.resolveAdmission/Batch；BatchRecipientSelectionService.select；ExpertRevalidationService.revalidateDiscovery；人工确认/撤销复读。
- 问题：无记录者在列表可计算 automaticStatus，但发送侧明确按 UNINITIALIZED 排除。现有初始化只插不存在者，已有 NEEDS_REVIEW 不刷新；初始化还未在 worker 层限定 isDiscoveryOutreach。
- 交互 P-1：自动新结论→候选投影→批量预估/执行。P-2：人工 CAS→存量任务/补全重验。

### ES RAW / CANDIDATE / APPLICATION

- Schema：三份 orcid_info*.json 均 dynamic:false；已有身份、机构凭证、学术字段、classification 与 tags，externalIds enabled:false。无需改 mapping。
- 相关事实写入全集：ExpertDiscoveryService.toIndexMap/indexToRaw 新发现；updateExpertAcademicFields 对已存在三层 partial update；ExpertIndexWriterService.indexToRaw、reconcileDiscoveryCandidate（RAW资格/分类、候选创建或删除）、projectDiscoveryCandidate/writeCandidateDocument、promoteToCandidate/promoteToApplication、demoteToRaw/removeFromCandidateIndex、bulkUpdateExpertClassifications；标签 addTag/removeTag、运营状态 syncOperatorStatus/Batch、application status 同步为相邻运营写入，全部保持。历史直接脚本（如 apply_legacy_discovery_approval.py）是证据来源，不能在本次重跑签发历史认可。
- 读取：ScanService.searchPage/scanAll/enrich；Writer.readDiscoveryDocument/discoveryProfile；ExpertSearchService 的查询/scroll/按 ID 读取与 toExpertProfile；发现去重与补全查人；各晋升/降级复读。
- 旧 revalidateDiscovery 先用完整 eligibility.reasons 调 reconcileDiscoveryCandidate，再 recordAutomatic；这会把“范围未知”重新变成删除候选的理由，必须在放宽时同时收口。
- 新发现 buildProfile 不带 researchFieldIds，因此通常先未知，再由补全更新；“首轮 promoted=0”不能推断补全永远没有晋级。线上补全确有 promoted=3 的后续批次。
- 审核 SOURCE_FIELDS 漏掉分类器输入，须补齐；过滤列表仍有 10,000 窗口与20秒预算，另案解决，不用本次结果声称该缺陷已经修复。
- 交互 P-3：补全写事实→重验→当前准入/候选；P-4：完整档案与扫描投影→policy 一致；P-5：旧记录无 admission→发送侧阻断。

### task_execution / batch_send_task_config（既有运行载体）

- task_execution：V4 request_payload/result_summary 为 TEXT，TaskExecutionService.runAndRecordWithResult 写入/终结，Repository 有 owner token/heartbeat/中断更新与清理；Service.status/findTask、任务中心查询读取。复用 DISCOVERY_REVIEW_INITIALIZE 的实际既有任务类型常量，不新增任务类型或改目录。
- 存量预览/执行仅以既有任务 JSON 记录 mode、dryRun、各结论计数、同步失败计数及至多100条样本；summary UTF-8 总长限制为48 KiB，不把六万人的名单塞进 TEXT。
- batch_send_task_config：V72+V93/V99/V108/V110/V128；已有地区、类型、模板门禁、研究方向条件。配置经 BatchSendTaskConfigService 的 create/update/enable 等既有 repository.save 写入；执行快照/调度/预估读取。本计划不改这些代码或配置，不因早期迁移注释“空类型不限”而误用当前“空类型发零人”语义。
- 交互 P-6：后台准入变化→已有任务同条件预估→实际目标一致；只在模拟/禁 SMTP 的测试环境验证执行。

### 知识使用与权限

已只读加载并复核四条 K 知识。用户要求仅方案，且这些文件已有其他工作改动，本轮不增加 hit_count、不归档、不改 CLAUDE.md；新发现仅记在本方案。未涉及前端文件，不新增 DOM/CSS，样式契约不适用。

## 实现方案

### 阶段 1：规则与增量链路（I-1/I-2/I-3/I-7）

1. DiscoveryAdmissionPolicy.kt：保留现有原始检查，将 I-1 六类原因移至 hints，硬原因留在 blockingReasons；加入 pending-tag-only 缺身份的显式阻断；版本改为2026-10-13.1。分类器和全局资格服务不修改。
2. DiscoveryReviewScanService.kt：补齐分类输入字段，使完整源与扫描投影一致；不顺便重构分页。
3. ExpertRevalidationService.kt：发现分支先判当前人工结论和新自动准入；有效准入走共享候选投影。缺资料不再走旧原因删候选；硬否决与邮箱验证失败保留。非发现分支不改。
4. DiscoveryReviewService.kt：修正 recordAutomatic 在初始化竞争、CAS失败时返回旧快照的问题，复读当前身份/决策并有限重试一次；仍冲突则显式报告，不投影。
5. 在相应 policy/revalidation/service/scan 测试中验证；完成后增量链路即可独立验证，但不自动执行生产迁移。

### 阶段 2：存量预览与补准入（I-2/I-4/I-5/I-7）

1. DiscoveryReviewService.kt：扩展既有 InitializeRequest 增加 mode 与 dryRun；mode 默认 INITIALIZE_MISSING 以保留已有契约，新增 REFRESH_DISCOVERY。新模式的 dryRun 默认 true；只有显式 false 才进入写分支。旧接口模式继续保留原行为，不改变已有调用的幂等键语义。
2. DiscoveryReviewController.kt：复用 /initialize 与 /initialize/{key}，登录 actor 保持从会话取得；同 requestKey 的 mode/dryRun/范围不同返回409，非法 mode 返回400。
3. REFRESH_DISCOVERY 使用 scroll 批次500遍历三层完整事实、先判 isDiscoveryOutreach 后去重；同 ID 跨层身份冲突单列。dryRun 对业务表/ES 零写入，允许既有 task_execution 执行日志；输出按人去重的预计准入/硬阻断/缺身份/人工保留/冲突人数。
4. apply 复读当前档案与准入；缺记录初始化、自动记录按新政策刷新，人工记录按I-4保护。只对 admitted 且需要候选者调用现有共享投影，身份已变化/并发冲突者跳过报告。不存在 RAW 的候选/有效档案可以初始化准入，但不得为其反造 RAW。
5. 准入写入和候选写入的成功数分别记录。失败后重复运行新requestKey只补未完成工作，不制造人工批准、不重写既有候选、不清空失败计数冒充成功。
6. 回滚预案：上线前导出本次影响的 admission 原值与候选是否存在、身份/版本快照；回退代码/政策时按新版本和未被人工改动的 CAS 条件恢复自动结论；只删除本次新建且版本/身份未变化的候选，禁止删原有候选或覆盖新的运营修改。准备迁移不等于执行迁移。

### 阶段 3：既有发送配置与灰度验收（I-5/I-6）

后续实施时，为明确指定的待确认首发任务使用现有配置入口：目标研发类型 + UNKNOWN + UNCLASSIFIED，研究方向ANY；若希望纳入国家缺失者，则地区不限。保留明确不符目标类型的排除，选择不依赖缺失机构/方向的邀请模板。不得自动扩张全部旧任务。

先看预估逐项排除原因，再在不接真实SMTP的环境核对目标数；准入放宽、资料补齐、可投递邮箱、最终发送数分别报告。生产发送是独立动作，本方案不执行。

## 变更文件清单

最大10个实施文件；本次均未修改。共享文件按阶段顺序编辑。

|#|文件（相对仓库根目录）|目的|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt|缺资料软化/可信身份/版本|
|2|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewScanService.kt|完整字段读取|
|3|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt|CAS竞争返回、存量新模式、计数|
|4|src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewController.kt|请求校验与现有接口衔接|
|5|src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt|补全重验与准入一致|
|6|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicyTest.kt|软硬原因矩阵|
|7|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewServiceTest.kt|存量/CAS/投影/不发信|
|8|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt|完整字段与跨层读取|
|9|src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt|mode/dryRun/actor/幂等|
|10|src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt|补全后不重新收紧|

不新增数据库/ES字段；不改模板、发送selector、分类器、前端、账号配置源码。如实施发现必须改第11个文件，先拆分或修订方案，不能暗增范围。

## 验收标准

- I-1：六类原因分别与组合出现，身份可信且无硬原因时 AUTO_PASSED，原因完整保留在 hints；软硬并存仍 NEEDS_REVIEW。
- I-2：VERIFIED完整、缺对象、错姓名、错邮箱、旧版本、源码ID冲突、仅待确认标签、明确OUT_OF_SCOPE/SERVICE_ONLY、无效邮箱逐项测试；人工历史例外按已有授权不变。
- I-3：发现入库→自动通过→候选，随后补全→重验，候选不因缺资料删除；硬失败仍阻断；非发现重验结果不变。
- I-4：RAW/CANDIDATE/APPLICATION同一ID只算一次，跨层身份冲突不写；未初始化/旧自动结论均处理，人工 HOLD/REJECTED/MANUAL_APPROVED/LEGACY_APPROVED 不被覆写；模拟CAS竞争不能投影旧批准。
- I-5：dryRun业务写0、apply邮件/联系人写0；已有候选/有效层运营字段逐值不变；候选失败后幂等补投影，成功人数与失败人数守恒。
- I-6：使用既有 BatchRecipientSelectionServiceTest、ManualInitialOutreachServiceTest、InitialOutreachServiceTest 覆盖预估/ES/NEW/旧首发；未知类型只有显式包含才匹配，选地区后国家缺失仍排除，邮箱抑制/验证和去重不变。测试文件仅执行，不列为可修改文件。
- I-7：完整source与扫描source同一档案原因/提示一致；政策版本落库；任务JSON≤48KiB；旧模式契约测试通过。
- 使用JDK11跑上述新增与受影响测试；先确认项目实际测试类名称再给 Maven -Dtest，禁止用缺失测试被跳过冒充通过。本次不执行开发测试，尚无实现。
- 冻结审计样本回放：897条中786条预计新准入，107+4条仍硬阻断；未来在线人数变化不得强行改数据凑数。

## 人工验收清单

### A-1：缺资料允许首发候选
- 前置条件：测试环境导入可信姓名邮箱、机构/国家/研究方向均缺失的模拟档案；SMTP禁用。
- 操作步骤：触发发现准入，读审核接口，再以未知类型包含、地区不限、方向ANY进行批量预估。
- 预期结果：decision=AUTO_PASSED；缺资料仅在hints；存在候选；该人被纳入目标；邮件写入0。
- 覆盖：I-1/I-2/I-3/I-5/I-6，P-1。

### A-2：明确不合格与身份不可信
- 前置条件：分别准备OUT_OF_SCOPE、SERVICE_ONLY、姓名邮箱冲突、缺身份对象且仅待确认标签的四个档案。
- 操作步骤：执行新模式dryRun；查看逐项样本；在测试环境apply。
- 预期结果：四个均不自动准入，不生成新的可发送候选；提示明确原因，邮件0。
- 覆盖：I-2/I-4，保留身份及明确负面限制。

### A-3：存量去重及人工决定保护
- 前置条件：三层重复同ID、仅候选存在、未初始化、旧待审、人工拒绝各一组；另准备跨层身份冲突样本。
- 操作步骤：dryRun读取去重数；apply后再运行；执行过程中给一条旧待审做人工HOLD。
- 预期结果：重复ID只计1人；可读取候选独有记录；旧待审按新规则刷新；人工拒绝/HOLD保留；冲突不覆盖；重复运行无重复候选；发信0。
- 覆盖：I-4/I-5，P-2/P-5，人工决定保留。

### A-4：补全不把已放宽者重新排除
- 前置条件：A-1已准入专家，仍无国家/机构/方向。
- 操作步骤：触发一次模拟学术补全与重验，再查询候选、准入和批量预估。
- 预期结果：仍AUTO_PASSED，候选仍存在，预估仍包含；原身份/机构/运营字段未改写。
- 覆盖：I-3/I-5，P-3，事实与运营状态保留。

### A-5：发送条件与数量解释
- 前置条件：一名已准入UNKNOWN国家缺失者、一名已准入目标类型且有国家者；使用不依赖机构的模板。
- 操作步骤：地区不限且包含UNKNOWN预估；改为特定地区或排除UNKNOWN再预估；禁SMTP执行同快照核对目标。
- 预期结果：前者只在满足所选条件时进入；预估与执行目标一致；排除原因是REGION或EXPERT_TYPE；没有声称“准入通过即发送成功”。
- 覆盖：I-6，P-6，显式条件保留。

### A-6：既有行为回归与资料显示
- 前置条件：非发现专家、有效人工批准/历史认可专家、已退订/不可用邮箱样本和已有邮件会话；准备本次误读worksCount的脱敏样本。
- 操作步骤：测试环境查询、预估与模拟发信；对照完整档案与审核接口；人工单封回复使用既有预览流程。
- 预期结果：既有人工授权仍有效，非发现与人工回复行为保持；抑制/邮箱限制不放开；原始事实不变；完整档案和审核原因一致；实际邮件0。
- 覆盖：I-2/I-5/I-6/I-7，P-4，以及全部保留项。

## 方案自检

- 规则、存量、投影、发送配置四层闭环；未将786等同最终可发人数。
- 实施文件10；一个准入子系统加既有批量发送配置验收；无新增持久字段，无前端改动。
- 关键写入/读取与竞态、原始数据保护、逐项人工验收已列明。
- 回滚依赖备份与CAS；执行前需具体化生产批次和运维窗口。
- 尚未执行实现、发布、存量迁移或发信。方案只是供用户审阅。
