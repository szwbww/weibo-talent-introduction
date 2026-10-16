# 01：重复发现的同身份补证

状态：方案，未实施。前置：既有准入方案的 CAS 和重验一致性阶段已完成验证。

## 需求描述

同一专家重复被可靠来源发现时，可以补回缺失身份证据，随后复用学术任务及准入重验。缺姓名、现有姓名冲突先给出独立原因，交给 02 定向恢复。

保持：原 `_id`、邮箱、已填姓名、作者编号、机构、学术字段、标签、运营字段、联系人及邮件；正常发现去重、新增计数和人工审核保护。范围外：改名、纠错作者编号、解析器扩展、来源强弱自动覆盖、发信。

## 关键不变量

### Invariant I-1：只补证，不推测身份
- Rule：incoming 只能来自现有提取器；必须有完整姓名及有效 identityEvidence。规范邮箱及已填姓名精确一致；已有 proof 若存在但无效/冲突，不自动覆盖；LEGACY_APPROVED 不改。空姓名不是已证实冲突，返回 MISSING_NAME_NEEDS_RECOVERY，不自动补名。
- Applies to：同步/队列/ORCID 公共消费入口、repair service。
- Violation consequence：新来源覆盖旧人、将人工认可伪装为来源证据。
- 来源：DiscoveryIdentity、当前 recordIdentityDuplicate。

### Invariant I-2：同邮箱须唯一业务 ID，三层版本保护
- Rule：原 size=1 去重结果只能用于发现重复，不能作为修复全集。补证前按规范邮箱查全量命中并按 `_id` 去重；不同 ID 同邮箱一律冲突。读取该 ID 三层快照；非空作者编号任意相冲突即不写。每层只写 identityVerification，用 seq_no/primary_term；缺层不创建、运营字段不重写。
- Applies to：writer 查询/补丁、repair coordinator。
- Violation consequence：任取一个档案或丢失并发编辑。
- 来源：K-expert-classification-one-object-three-layers。

### Invariant I-3：成功状态与重试真实
- Rule：只有所有已有层一致且回读匹配才为 REPAIRED；否则 PARTIAL/CONFLICT/FAILED。部分失败让消费保留可重试错误，不能推进为修复成功。重复调用对已落目标 proof 的层视为已完成；不自动回滚另一人的并发改动。没有可信作者编号时补证可以成功，学术补全仍可 NO_TRUSTED_ID，不偷用旧编号。
- Applies to：消费返回值、入队、准入重验。
- Violation consequence：假成功、恢复停滞、错用学术指标。
- 来源：K-enrichment-write-three-layers。

## 现状审计

ES：三份 `src/main/resources/es/orcid_info_*.json` 均 dynamic:false，identityVerification 已有 status/version/email/names/source/hash/orcid/openAlexAuthorId；externalIds enabled:false，无新增字段。

身份写入路径全集：ExpertDiscoveryService.orcidIdentityFactory/buildProfile → proofFor → toIndexMap → indexToRaw；writer 的候选/有效层晋升、降级和候选投影全源复制；updateExpertAcademicFields 只更新学术且有身份比较；updateRawDocumentEmail 是相邻邮箱更新入口，签名与邮箱不符应保持失效；历史维护脚本有直接补丁，不在本计划重跑。标签/运营状态/classification partial update 不改身份，但会导致版本竞争，应重读。

读取路径：DiscoveryIdentity.allowed/explainIdentity/explainSourceIdentity、DiscoveryAdmissionPolicy、发送 RecipientScope 与当前 admission；ExpertSearchService/ReviewScan/Writer.discoveryProfile；发现去重、trustedOpenAlexAuthorId/trustedOrcid。晋升透传 proof 不需改（来源：K-promotion-source-passthrough）。

V131 job 全部写入为 insertIfAbsent/reopenUnmatchedOrStaleSuccess、claim/renew/completeWithToken/reopenFailed；本计划只调用原 enqueue，消费需保留失败语义。V148 全部准入写入 initializeAdmission/applyItem/revokeCurrent/updateAutomaticAdmission，调用既有 recordAutomatic，不直写表。

交互 X1：同步/队列消费→补证；X2：三层补证→学术入队/重验；X3：准入→候选投影→发送预估；X4：运营局部更新→ES 版本竞争。当前 recordIdentityDuplicate 为缺证只入队、缺名直接 mismatch；这是本份有意改变的范围。知识只读，不修改其计数。

## 实现方案

1. I-1/I-2：新增 DiscoveryIdentityRepairService 统一校验与分层结果；Writer 增加“邮箱全部命中”和“按版本仅写 proof”的方法。先完整读取验证，再写；不得把查询失败当不存在。
2. I-1/I-3：ExpertDiscoveryService.recordIdentityDuplicate 接入 service；仍记 duplicate，不计 indexed。处理结果分别统计 REPAIRED、ALREADY_VERIFIED、MISSING_NAME_NEEDS_RECOVERY、CONFLICT、PARTIAL、FAILED。该文件内 ConsumeOutcomeResult/QueuedItemConsumption 增加默认 false 的 identityRepairFailed（仅内存返回值），succeeded 必须包含此项；DiscoveryPipelineService 将其记为 IDENTITY_REPAIR_FAILED 并沿原重试/租约处理。同步页完成也检查该失败，不推进失败页。合法且原 proof 已存在的重复流程沿用原入队行为。
3. I-3：成功后入队并调用现有准入/重验接缝；禁止在 repair service 里复制政策。既有原始库缺失者可完成已有层补证，但不伪造 RAW；准入投影交给先决方案的跨层流程。
4. I-2/I-3：回滚只针对本次新增 proof、且当前 proof 等于本次目标且版本仍匹配的层，条件删除该字段；不动后续人工操作。保存前值和结果供 02 报告消费。

## 变更文件清单

以下相对仓库根，共 8 个，无前端/迁移：

|文件|用途|
|---|---|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt|重复路径接入与消费错误传播|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt|新增补证协调器及结果类型|
|src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt|三层读取/唯一 ID 检查/条件 proof 更新|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt|身份修复失败的明确重试原因|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt|同步/队列接缝与计数|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairServiceTest.kt|新增：证据矩阵及部分失败|
|src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt|新条件写入与运营字段保护|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt|部分失败不终结成功、租约重试|

## 验收标准

- I-1：同姓名缺证可补；空姓名独立原因；明确姓名/ID 冲突、无效 incoming、历史认可均不改。
- I-2：三层同 ID 计 1 人；两个 ID 同邮箱、跨层冲突写 0；409 不盲重试旧快照；缺层不创建；仅 proof 改变。
- I-3：某层 500 不标整体成功，恢复后只补缺层；入队失败/准入失败仍可恢复；重复次数不变、新增人数 0；邮件/联系人写入 0。
- JDK 11 定向执行四份改动测试及既有 DiscoveryIdentityTest、DiscoveryReviewServiceTest；后面两份仅执行，不在可修改范围。

## 人工验收清单

### A-1：重复发现补证
- 前置条件：测试三层同 ID 同姓名同邮箱、无 proof，准备原文唯一关系；禁 SMTP。
- 操作步骤：执行一次发现消费，再执行第二次；查看三层与任务/准入状态。
- 预期结果：第一次三层获得同一 proof；第二次不覆盖；indexed=0、duplicates=1/次；准入依当前政策，邮件 0。
- 覆盖：I-1/I-2/I-3，X1/X2/X3。

### A-2：冲突与人工状态
- 前置条件：分别准备缺名、不同姓名、同邮箱双 ID、LEGACY_APPROVED、HOLD、REJECTED；给每份档案填写运营备注和学术字段。
- 操作步骤：执行消费并读取结果；对照前后字段与准入。
- 预期结果：缺名为 MISSING_NAME_NEEDS_RECOVERY；冲突不写；历史 proof 不改；HOLD/REJECTED 不变；原姓名/邮箱/学术/运营/历史邮件逐值不变。
- 覆盖：I-1/I-2/I-3及所有保持项。

### A-3：中途失败与并发
- 前置条件：第二层更新首次返回 500；另一轮在读取后人工修改某层运营字段以产生 409。
- 操作步骤：消费、读取报告、去掉故障后重试；检查逐层前后值。
- 预期结果：首次 PARTIAL/CONFLICT，不报 REPAIRED；续跑仅补缺失 proof，保留人工字段；新增文档 0，联系人/邮件写入 0。
- 覆盖：I-2/I-3，X4。
