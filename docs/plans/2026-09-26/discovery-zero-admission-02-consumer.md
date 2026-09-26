# 深度发现零入库修复 02：撤除额外准入条件与纠正统计

状态：修复建议，尚未实施。依赖 01 真实原文解析完成；同一工作树、本地 main。用户已暂停本次任务；本计划不启动生产任务、不恢复发送、不恢复已删除数据。

## 需求描述

原文能够明确对应姓名与邮箱的正常线索，应按既有邮箱验证、去重、用户资格配置入库；不能因为缺少某种内部凭证前缀而整源拒绝。归属不明与邮箱无效分开计数。保持姓名正确、业务键稳定、原子去重、补全不串人、批量配置和发送暂停。

范围外：新增首发/晋升规则、静态黑名单、UI/数据库结构改造、重建 938 条已删档案、全库身份迁移、调整 OpenAlex 额度与搜索重试。

## 关键不变量

### I-1：准入按实际解析结果
- 规则：01 的解析结果中有明确归属姓名且邮箱通过既有验证，就进入既有去重/资格流程；不额外要求 identityVerification 状态、证明前缀、摘要或日期版本。无法确定作者的邮箱仅作为抽取线索，不能填猜测姓名或生成无名专家。身份检查是输入完整性，不新增批量任务配置外的联系资格。
- 适用：同步 ORCID、consumeOutcomeInternal、通用 RAW 写入及新发现候选写入。违反会再次整源 0 产出。来源：K-identity-cache-version-and-admission（2026-09-26复核）。
### I-2：去重及学术身份不倒退
- 规则：深度发现 RAW/CANDIDATE 使用邮箱业务键原子 create，与是否附带审计凭证无关；不覆盖存量姓名/邮箱、重命名业务 ID。外部作者 ID 只由已明确匹配的同一作者产生；缺 ID 可入库，但不发学术作者查询。旧业务主键不回退成 ORCID。补全回写继续比对当前身份快照，缺失层不重建。
- 适用：indexToRaw、promoteDiscoveredToCandidate、重复入队和 updateExpertAcademicFields。违反会串人或覆盖清理结果。来源：K-historical-identity-orcid-fallback。
### I-3：拒绝原因真实、可区分
- 规则：邮箱验证器真正判无效才增加 emailsRejected / EMAIL_INVALID。解析无法确定归属计入 filtered 和独立 filterReasons[IDENTITY_UNRESOLVED]，不重复增加 EMAIL_INVALID。每条线索总失败计数只增加一次；作者/邮箱线索数不冒充去重后的专家数。
- 适用：两条消费循环、snapshotRejectReasons、bySource、最终 DiscoveryStats/任务日志。违反会把未验证邮箱报成无效。
### I-4：缓存版本只保护抽取兼容
- 规则：新增独立抽取规则版本，与存量身份证明 VERSION 分离。旧抽取 JSON/旧在途结果不得被宽松入口重新消费；走现有失败结算并明确报版本不兼容。不直接清空 SQL、重置全库检查点或破坏租约/配额。既有来源审计字段保留，不成为首发条件。
- 适用：extractQueuedItem、consumeQueuedItem 和 DiscoveryPipelineService 重用路径。违反会回灌旧猜名结果。来源：K-identity-cache-version-and-admission。

## 现状审计

### ES RAW/CANDIDATE/APPLICATION
三层 mapping 为 dynamic:false；identityVerification 已有显式对象，externalIds 为 enabled:false；不增加字段或迁移。此次涉及的身份写入链：同步 ORCID / consumeOutcomeInternal → toIndexMap → ExpertIndexWriterService.indexToRaw；同两入口 → promoteDiscoveredToCandidate。RAW 写入仍有 allowedMap，直接晋升甚至重复调用两次 allowedMap，不能只删第一个 identityRejection。

其他晋升读写已核对：ExpertRevalidationService→writeCandidateDocument、通用 RAW→CANDIDATE、CANDIDATE→APPLICATION 和补邮箱晋升的额外门禁此前已移除，本阶段保持；ExpertSearchService/ExpertProfile 读取证明只用于审计。InitialOutreachService、ManualInitialOutreachService、RecipientScope 已无 DiscoveryIdentity 资格判断，禁止重新加入。

学术补全：trustedOpenAlexAuthorId/trustedOrcid 当前仅信已绑定的证明 ID；updateExpertAcademicFields 通过三个 updateAcademicFieldsInLayer 按当前身份快照更新，不 upsert。01 正确解析的新 SOURCE_SHA256 可以留审计与已绑定 ID，不能因其与 JATS 前缀不同导致补全永久 NoId；保留对存量未知外部 ID 的保护，不全局信任历史 externalIds。重复邮箱先比较姓名/邮箱/已有真实 ID，冲突不覆盖；已完成 RAW 但入队失败的重试必须可恢复。

### 抽取队列
V133 discovery_paper_job.extraction_json 与 reserved_result_bytes、lease_token、generation 相关联；Repository.saveExtraction 负责 CAS 与配额，PipelineService 非空缓存重用，ExpertDiscoveryService 为最终消费边界。只改消费版本契约，不增加队列写入口，不操作在线队列表。旧结果终态由现有消费者结算；不谎称本次会重抽全部旧失败项。

### 任务统计与日志
SourceStats/DiscoveryStats 为运行中计数，buildBySourceDetails→TaskProgressStore.persistProgressLog→TaskProgressLogRepository.save 写 JSON；结束由 TaskExecutionService 保存 result_summary。TaskProgressController/TaskExecutionSummaryExtractor/前端现有表格读取这些字段。当前归属拒绝先增 emailsRejected，snapshotRejectReasons 再统一标 EMAIL_INVALID，造成误报。本次用已有 filtered/filterReasons 及 failureReasons 字段区分，不新增共享存储字段、不追改历史日志。日志保留任务的既有 rebind 和 retention 删除路径，不新增清理行为。

## 实现方案

1. 两条消费入口的 identityRejection 仅判断解析出的作者归属是否缺失，删除 validEvidence 前缀资格条件。01 已保证所有生产者不猜名；必须一起交付，不能单独放宽旧 parser（I-1）。
2. 移除 indexToRaw、promoteDiscoveredToCandidate 的 allowedMap 入库/晋升门禁；RAW 的 op_type=create 按发现来源识别，不再依赖 proof 非空，保留邮箱去重与三层比对（I-1/I-2）。
3. identityEvidence/identityVerification 保留为审计和绑定真实作者 ID 的数据。SOURCE_SHA256 与已有原文来源在审计消费中统一处理；不得恢复其作为入库、候选资格、发送资格的前提。更新 proofFor 与存量独立证明校验，保证明确 PDF/HTML 来源可以补全，有缺失 ID 的来源仍可正常入库；不为未知旧记录批量补“已验证”（I-2）。
4. 归属不明改用已有 filtered/filterReasons；snapshotRejectReasons 合并明确分类，EMAIL_INVALID 仅来自真实邮箱验证失败。保持最终 taskFailureCount 通过已有统计合计，每条失败不重复记（I-3）。
5. DiscoveryIdentity 增加独立 EXTRACTION_VERSION，extractQueuedItem 写新值，consumeQueuedItem 验新值；原有审计 VERSION 不随其改变。测试覆盖旧版 JSON、新版原文产物、暂停/租约语义（I-4）。
6. 从 01 原文夹具经真实 parser→consumer→writer 跑成功和失败两类；外部邮箱验证及 ES/任务存储允许使用受控测试替身，禁止替身代替作者归属解析。明确四条来源应产生正确入库记录；缺归属不入库，真实同名和多邮箱不误合并。补充无 proof 但合法解析结果通过 writer、原子冲突不覆盖以及同一解析结果的重试（I-1/I-2/I-3）。

## 变更文件清单

完整前缀：`src/main/kotlin/com/weibo/talentintroduction/`、`src/test/kotlin/com/weibo/talentintroduction/`。共 7 个文件，无其他隐含修改。

| 文件 | 动作 |
|---|---|
| main discovery/service/ExpertDiscoveryService.kt | 消费准入、直接晋升、证明审计、计数、抽取版本兼容 |
| main expert/service/ExpertIndexWriterService.kt | 去 RAW 凭证门禁，原子创建不依赖凭证存在 |
| main expert/domain/DiscoveryIdentity.kt | 来源审计适配、独立抽取版本，保留绑定 ID 保护 |
| test discovery/service/ExpertDiscoveryServiceTest.kt | 真实解析链、准入/统计/重复恢复/补全回归 |
| test discovery/service/DiscoveryPipelineServiceTest.kt | 队列新旧结果、租约、暂停和入队重试回归 |
| test expert/service/ExpertIndexWriterServiceTest.kt | 无凭证 RAW 正常写入、原子冲突不覆盖 |
| test expert/domain/DiscoveryIdentityTest.kt | 审计来源、缓存版本与身份证明版本分离 |

## 验收标准

- I-1：PDF、HTML、CORE、JATS、ORCID 各有实际来源正例；至少四条人工确认原文经真实链路产生正确 RAW/CANDIDATE。不得靠手工给 DTO 填证明或把全部输入拒绝来通过。
- I-2：合法同名和一人多邮箱保留；同邮箱不同身份不覆盖；无 proof 的新发现仍使用 create；只查真实已绑定作者 ID，旧业务键不用于 ORCID；无 ID 允许入库。
- I-3：有归属且邮箱有效→有效+1；归属不明→filtered+1、IDENTITY_UNRESOLVED+1、EMAIL_INVALID 不变；真实邮箱失败→emailsRejected+1、EMAIL_INVALID+1。按最新批次去重汇总，不能累加历史累计快照。
- I-4：旧缓存不能重新写专家，新版明确结果可处理；重复任务只补缺失任务，不重建已删层、不覆盖后来修正。
- JDK11 运行 `mvn test -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,ExpertIndexWriterServiceTest,DiscoveryIdentityTest -Dexec.skip=true`，最终 01+02 完整 `mvn clean package`。计数、解析、真实链路缺一不可；旧测试与新契约冲突时明确改写测试目的，不批量放宽断言。

## 人工验收清单

### A-1：已明确的四条来源
- 前置：隔离环境关闭发送，使用 01 四条人工核定身份，邮箱验证替身均返回有效、候选既有配置允许通过。
- 操作：由真实 PDF/HTML 入口发起发现，查询专家列表及任务明细。
- 预期：4 条正确 RAW、4 条候选，0 条 EMAIL_INVALID；两个 Hang Zhao 不按名字合并；不发邮件。覆盖 I-1/I-2/I-3。
### A-2：归属未知与邮箱无效
- 前置：一条无明确作者的邮箱、一条有明确作者但邮箱验证失败；此前四条正常记录存在。
- 操作：运行同一来源消费，查看原因和专家列表。
- 预期：filtered=1、IDENTITY_UNRESOLVED=1、emailsRejected=1、EMAIL_INVALID=1；新专家 0；原四条不变。覆盖 I-1/I-3。
### A-3：重复、旧缓存、补全
- 前置：一条正确 RAW 已写但任务入队失败；一条同邮箱不同姓名；一份旧版抽取 JSON；一条没有真实作者 ID 的正确来源。
- 操作：依次重试，检查专家、任务、作者查询目标和三层文档。
- 预期：第一条仅补任务；第二条不覆盖；第三条版本不兼容且入库0；第四条可入库且作者 API 查询0；缺失层不重建。覆盖 I-2/I-4。
### A-4：配置与上线后人工小批量
- 前置：01+02 测试通过，明确批准发布；保持自动发送暂停，保留原批量配置，记录发布前 WAR 与任务状态。
- 操作：核对 main 提交与线上类哈希；由用户手动启动小批量并通知复核，逐条对照新产生记录的原文。
- 预期：无额外首发/晋升门禁或静态黑名单；正常解析来源能入库；错误对应0；配置/历史邮件不变。新一批数量由真实来源决定，不承诺 673 条均可恢复；不自行安排定时验证。覆盖全部不变量。
