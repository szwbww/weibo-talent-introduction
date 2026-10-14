# 02：批量目标统一预检查与可解释结果

主约束：[MAIN 主计划](batch-filter-main.md)。实施顺序、共享响应、共同fixture和联合验收必须同时遵守MAIN；本文件仍为本阶段精确修改范围。

状态：方案完成，未实施。依赖01；10文件、2子系统（批量目标服务及只读持久化/接口）、零新增持久字段。详细证据见[审计](../../audits/2026-10-14-batch-filter-transparency/report.md)与[检索记录](../../audits/2026-10-14-batch-filter-transparency/grep-evidence.txt)。

## 需求描述

1. 预估解释所有处于所选层级/标签母集的专家如何被筛掉；能按指定docId查询实际原因，区分准入、任务筛选和运行状态。
2. 预估与执行共用确定性预检查；历史邮箱结论按当前开关/白名单解释，无有效结果标“待执行验证”。
3. 保持真实docId/ORCID去重、人工准入保护、退订、绑定账号、已发送/永久失败保护、执行快照和真实验证审计；预估零业务写入、零供应商调用。
4. 范围外：放宽上述保护、更换账号、重发授权、Emailable收费调用、线上数据修改、旧cron迁移、材料催办规则改造、schema迁移、准入/人物合并政策。

## 关键不变量

### Invariant I-1: 母集与原因计数可核对
- Rule: 母集只应用所选漏斗层级和标签、保留原真实docId/ORCID去重；不得先应用地区/状态/研究方向/类型/模板再声称全量解释。每个docId保留独立准入事实、任务条件及保护原因。总排除为人数去重；reasonHits可重叠，不相加作为总排除。准入已通过但地区失败不能标NOT_DISCOVERY。
- Applies to: selector、countBySnapshot、ES/NEW目标生成、只读诊断响应；只读，无业务写入。
- Violation consequence: 隐藏过滤继续存在，计数失真或误称准入无效。
- 来源: K-recipient-count-preview-parity；本轮F2。

### Invariant I-2: 共用预检查与快照
- Rule: 预估和执行目标选择调用同一个preflight，使用同一快照；既有发送/抑制/绑定/永久失败保护不放宽。发送前原有实时保护保留，预估后发生事实变化允许产生有明确原因的运行时跳过；账号额度、自检、供应商故障归任务运行状态。
- Applies to: 新ES目标、NEW重试、preview、执行前检查及现有任务日志。
- Violation consequence: 预估通过但已知条件执行时静默跳过，或预估后退订仍外发。
- 来源: K-batch-send-filter-retry-parity；K-batch-task-config-snapshot-log-identity；K-suppression-check-call-sites。

### Invariant I-3: 邮箱历史与实时验证开关分离
- Rule: 实时验证OFF且历史不可用过滤OFF时，不读邮箱历史、不调用供应商。历史过滤ON沿用有效最新undeliverable排除。实时验证ON时，对现有可复用结果按当前allowedStates解释；无结果标AWAITING_VERIFICATION，不能等同供应商unknown或通过。有效期、request_count>0、非reuse原始记录、无error、状态及checkedAt/id排序沿用现有仓储，不能发明第二套缓存有效性。
- Applies to: BatchEmailVerificationService的只读历史解析、preflight、真实verify。
- Violation consequence: 未选择risky仍被计作可发、未验证被伪称unknown、关闭仍收费。
- 来源: 本轮F3；原始用户要求。

### Invariant I-4: 预估不产生业务副作用
- Rule: 预估只SELECT/ES读取，不创建campaign/contact/task、Pending验证、发送尝试或准入，不调用verify()/SMTP/供应商。历史拒绝是预检查排除，不新增SKIP验证记录；真正进入verify的目标沿用原审计写入。只新增响应DTO字段，不增SQL/ES字段。
- Applies to: 新只读repository、preflight、controller及count路径。
- Violation consequence: 看一次预估就改线上、付费或污染验证统计。
- 来源: K-recipient-count-preview-parity。

### Invariant I-5: 规模、隐私和失败显式
- Rule: 按500条批量读事实，不能逐人N+1；原因每类最多20个样本并标truncated；按docId诊断每请求最多200个。响应含asOf、完整母集数量、去重排除数、通过数、待执行验证数、reasonHits与样本。诊断失败/超时返回不可用或明确partial，不把部分结果当总数。任务日志仅存聚合与限量样本，不写整个专家列表。
- Applies to: 新repository/preflight、controller及任务诊断。
- Violation consequence: 大批数据拖垮预估、泄露成批PII、截断被误认全部。
- 来源: original。

### Invariant I-6: 模板开关按实际内容解释
- Rule: 开启时复用effectiveRequiredKeys与现有模板门禁；关闭时个性化缺项不排除。缺模板或模板本身不可用作为任务配置错误。模板排除属于预检查，不算发送失败。
- Applies to: batchTemplateGate、preflight、预估DTO。
- Violation consequence: 模板关闭仍暗中过滤或失败率污染。
- 来源: 本轮F2/F4。

## 现状审计

### ES、准入和配置
- 三层ES mapping dynamic=false，tags/country/researchFields为keyword；原始真实docId可能与ORCID字符串不同，不能换用ORCID作为行主键。写/读路径与交互继承[01现状审计](batch-filter-01-region.md#现状审计)，本计划不新增写入。
- `expert_discovery_admission`（V148）：身份哈希绑定人工/自动决定；`DiscoveryReviewService`自动记录、人工决定/批处理及重验是写方，`resolveAdmissionBatch`与发送selector是读方。同身份MANUAL_APPROVED不被地区条件重标。
- `batch_send_task_config`（V72及后续迁移）：配置服务/旧适配器/迁移写；快照读；无新字段。（来源: K-batch-config-legacy-adapter-field-preservation）
- 当前 `buildEsFiltersForLevel` 在selector之前过滤，selector只加载显式条件通过者的准入；精确模板过滤在selector后，原因漏记。
- X1：ES多入口写入、持久准入与任务快照 → 同一母集/独立原因。

### 联系人、发送事实和抑制
- Schema：既有 `expert_contact` 的docId/email/账号绑定/状态，`mail_record` 的contactId/direction/sendStatus，`mail_send_attempt` 的docId/recipient/status/error摘要，`email_suppression` 的规范邮箱；复用现有列、约束、标准化语义，不迁移。
- 写路径：联系人由 `InitialOutreachService`、`ManualInitialOutreachService`创建，`ExpertContactManagementService`、`ConversationStateService`、层级/操作状态服务save，`SenderAccountBindingService`列级绑定；发送记录经 `ManualOutreachTxHelper.recordSuccess/recordFailure`及其他收发件服务落库；attempt经repository insertIgnore/claimStatus/updateStatusAndError；退订/退信处理经抑制服务/仓储写入。原写方全部保留，不改其归属和生命周期。完整定位见检索记录，读仓储在查询时消费现存事实。
- 读路径：现有NEW重试构造、runIntroductionFromSnapshot执行循环、账号绑定判断、SmtpMailDeliveryService最后抑制检查；新增只读repository使用相同范围/谓词批量查询，不以任意失败等同永久失败、不将预检后新状态忽略。
- X2：成功/永久失败/绑定/抑制写入 → 预估/新ES/NEW重试解释；发送前变化 → 实时复核。
- 绑定已有UI提示；联系人成立即可绑定，失败未发也可能命中。此计划解释它，不授权自动解绑/换号/重发。

### 邮箱验证审计
- Schema：`batch_email_verification`（V138及后续迁移），含normalized email、status、state、request_count、reuse/source、error、checked_at/id；保持其历史写入语义。
- 写路径：`BatchEmailVerificationService.verify`先Pending，再历史复用/真实请求/结果更新；供应商错误仍是原有运行态。
- 读路径：`findReusableByEmails`按最近一年、真实请求、原始非错误记录取最新结果；`findKnownUndeliverableEmails`只看undeliverable；执行verify还按allowedStates解释risky，故预估存在确定性偏差。
- X3：旧有效验证记录 → 当前不同开关/白名单 → 预估与执行；无记录只待验证。

### 模板与日志
- 模板块正文由模板服务写，`effectiveRequiredKeys`从实际正文推导；旧required_keys列不是权威。composer/预览/batchTemplateGate读其实际变量。发送日志、task_execution由现有任务封装与进度更新写，页面读；本计划仅增加聚合解释，避免整批PII进入日志。
- X4：模板内容和gate开关 → exact gate → 可见原因；模板13实际专家字段为expertFamilyName/primaryResearchField，不能按旧列凭空加条件。
- 旧InitialOutreachService的cron当前未启用；共享composer不用修改，避免意外影响第二条外发路径。（来源: K-dual-outreach-paths）

## 实现方案

1. 在 `BatchRecipientPreflightService.kt` 定义只读DTO与纯结果模型：每人准入decision、conditionReasons、protectionReasons、missingTemplateKeys、verificationPhase（DISABLED/CACHED_ALLOWED/CACHED_REJECTED/AWAITING_VERIFICATION），独立eligibleBeforeLiveVerification；聚合以docId集合计数。`BatchRecipientSelectionService.kt` 分离准入事实和显式条件判定，对母集批量解析准入，保留原docId/ORCID去重。遵守 I-1、I-5、I-6。
2. 新建 `BatchRecipientReadRepository.kt`，参数化批量SELECT上述联系人/发送/抑制事实，复用现有判据及查询范围，不给巨型旧repository加方法以免扩大行号守卫范围。500一批，邮箱标准化用当前规则；空入参不发SQL，不能字符串拼接值。`BatchRecipientReadRepositoryIT.kt` 用本地数据库真实表fixture验证成功/暂时失败/永久失败/绑定/抑制及批次数。遵守 I-2、I-4、I-5。
3. `BatchEmailVerificationService.kt` 提取无副作用的历史解释入口，复用现有repository的有效性与排序；`BatchRecipientPreflightService.kt`消费它与只读事实。禁止从预估调verify。允许States、两开关4种组合及risky/unknown/过期/错误/无记录逐项测试。遵守 I-3、I-4。
4. `ManualInitialOutreachService.kt` 将INTRODUCTION预估和目标生成接入preflight：诊断扫描仅层级+tags，按既有身份键合并新ES/NEW重试，重复记录只计一次。将模板判定纳入解释；确定性拒绝不进入发送iterator；原发送前动态检查保留。运行态单独说明，不扩大材料催办/旧cron行为。消费现存所有写方，无新增业务写路径。遵守 I-1～I-6。
5. `BatchSendConfigController.kt` 扩展既有预估响应；在当前 `/api/mail/batch-send` 控制器提供沿用现有认证的 `POST /api/mail/batch-send/recipient-diagnostics`，请求含现有snapshot形状和docIds（1～200）。按当前scope逐个返回原因；母集外显式OUTSIDE_BASELINE，找不到显式NOT_FOUND，不静默吞掉。结果模型放新preflight文件，不新开DTO文件。保留旧响应字段含义和HTTP失败约定；新增字段为兼容扩展。遵守 I-1、I-4、I-5。
6. `BatchRecipientPreflightServiceTest.kt` 验证完整状态矩阵、副作用零及有界计数；`ManualInitialOutreachServiceTest.kt` 验证预估/执行/重试共享调用、预检与执行间抑制变化、快照和日志有界；`BatchRecipientSelectionServiceTest.kt` 验证已准入但地区失败仍保留MANUAL_APPROVED、真实docId/ORCID去重。遵守 I-1～I-6。

## 变更文件清单

|序号|精确路径|变更|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt|共用预检查接入|
|2|src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionService.kt|事实与条件分离|
|3|src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt|只读历史解释|
|4|src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightService.kt|新增服务/DTO|
|5|src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchRecipientReadRepository.kt|新增只读批查|
|6|src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt|诊断响应/入口|
|7|src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightServiceTest.kt|新增单测|
|8|src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchRecipientReadRepositoryIT.kt|新增真实SQL测试|
|9|src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt|路径集成回归|
|10|src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt|准入与去重回归|

## 验收标准

- I-1：10人fixture中2人同时国家/状态不合、1人模板缺项、1人历史risky，唯一排除4、通过6、原因命中6（地区2+状态2+模板1+邮箱1）；通过者3人有效deliverable、3人无缓存待验证，与MAIN的G-2一致；准入事实完整；baseline=excludedUnique+eligibleBeforeLiveVerification；待验证是通过中的子集，不另加总。
- I-2：新ES/NEW重复只算一次；相同快照预估与执行取目标同集合；预估后新增抑制在发送前拦住；额度不足不改准入。
- I-3：四种开关、有效/过期/错误缓存、risky/unknown/undeliverable、无历史全覆盖；全部关闭时邮箱repository零调用，preview全部情况下供应商零调用。
- I-4：对campaign/contact/task/admission/verification/attempt的写mock为零；真实SQL集成测试使用只读事务；真实verify原审计测试继续通过。
- I-5：1,201人fixture验证分批、样本上限20、truncated、指定201个docId拒绝、missing/partial显式返回；无逐人查SQL、无完整列表日志。
- I-6：gate开缺项排除，关缺项通过；实际模板变量优先于旧required_keys；缺模板为配置错误。
- JDK11运行上述测试与现有BatchEmailVerificationService相关测试；IT必须真实执行并说明数据库依赖，不能把跳过当通过。02后端可独立上线，旧UI仍可消费原字段；03负责显示新增解释。

## 人工验收清单

### A-1: 原因独立且人数不重复
- 前置条件: 本地测试库建10名同层级discovered、MANUAL_APPROVED、未联系专家；2人国家/状态均不符、1人模板缺项、1人有效risky，余6人符合，其中3人有效deliverable、3人无有效缓存；邮箱allowedStates=deliverable/unknown，无有效缓存者待验证，完整fixture见MAIN的G-2。
- 操作步骤: 1. 调预估接口；2. 对上述4人调recipient-diagnostics；3. 查看响应准入与原因。
- 预期结果: baseline=10、excludedUnique=4、eligibleBeforeLiveVerification=6、awaitingVerification=3；原因命中总数6；4人准入仍MANUAL_APPROVED；无新验证记录。
- 覆盖: I-1、I-4、I-6、X1、X4、需求1。

### A-2: 邮箱状态与开关
- 前置条件: 测试库有A=有效risky、B=无缓存、C=有效undeliverable，其他条件齐全；供应商连接替换为本地拒绝调用的stub。
- 操作步骤: 1. 开实时验证、允许deliverable/unknown；2. 再允许risky；3. 关闭实时验证但开历史过滤；4. 两者均关。
- 预期结果: 1排除A/C、B待执行验证；2A通过缓存、B仍待验证；3仅C因历史不可投递排除；4三人均不因邮箱历史排除；每步供应商调用0、验证新增行0。
- 覆盖: I-3、I-4、X3、需求2。

### A-3: 发送保护与动态变化
- 前置条件: 测试库分别有已发送、永久失败、绑定账号、抑制、人工拒绝、可发六人；新ES和NEW各放同一个可发docId；SMTP使用本地捕获器。
- 操作步骤: 1. 预估并取诊断；2. 给可发者新增抑制；3. 用相同快照执行；4. 改当前任务配置后再次查看该执行快照。
- 预期结果: 五种保护均有具体原因且不被放宽；可发者只计1人；新增抑制后捕获器收到0封；执行快照未被后续编辑替换。
- 覆盖: I-1、I-2、I-4、X2、需求3。

### A-4: 模板、数量上限和错误
- 前置条件: 测试库准备1,201人，其中30人模板缺项；无邮箱/发送保护；启用本地请求日志。
- 操作步骤: 1. gate开关各预估一次；2. 展开模板原因样本；3. 请求201个docId；4. 临时断开测试ES再预估；5. 检查业务表计数。
- 预期结果: gate开时模板排除30、关时0；样本最多20且truncated=true；201个请求得到明确参数错误；断开时显示不可用/partial而非完整0；业务行数不变，无N+1请求。
- 覆盖: I-4、I-5、I-6、X4、需求1～3。
