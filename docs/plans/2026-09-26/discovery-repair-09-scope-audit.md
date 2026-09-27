# 09 专业范围修复：共享审计

2026-09-27，规划基线 `64c0394a940bd79c2ecc04e5c497650f045faa75`。用户说明另一 agent 正执行 01～08；本地未取得其完成台账，不推断其完成状态。09 执行时以 08 验证通过的代码为基线重新定位方法。本次只改计划/审计/知识，不修改 src、不通知或中断执行 agent。

## 已确认事实与设计选择

- [生产诊断](../../audits/2026-09-27-deep-discovery-scope/README.md)：ORCID 双重编码、75条 OUT_OF_SCOPE 仍在候选；7个相关部署 class 与本地相同。Gebeyehu 两条记录先03:04:46晋升、后03:04:58判范围外。
- DD-23：查询重复编码，最终 URI 与公开 API 对照可复现。09a。
- DD-24：发现直接晋升仅基础资格，没有研发专业资格。09d。
- DD-25：补全分类/候选资格不同步，且补全队列先记成功再RAW-only复评。09d。
- DD-26：科研分/笼统STEM不能证明六类专业；结构化 field.id 被丢弃。09b/09c。
- 用户明确：工程、材料、计算机、化工、能源、物理；保留相关高校科研人员。
- 本次设计口径：沿用当前用于展示研究方向的 top5 作者主题，至少一个属于六类即具备“相关”证据，不另猜主要专业占比。需要既有科研/生产阈值分型通过才能自动晋升。没有结构化专业证据为UNKNOWN；暂不新增别名词库、模型或论文分类回填。
- **代价明确**：缺可信 OpenAlex 作者 ID/ORCID 或补全无学科字段的合法姓名邮箱会留RAW；尤其现有部分论文来源只有姓名邮箱，不能承诺修复后仍立即晋升。不得为保产量按姓名接作者ID。

## Schema 与读写闭包

### S9-1：ORCID 请求与恢复位置

- 请求唯一构造：OrcidDataSource.searchOrcidPage:96-101；searchOrcidRecords委托它；发现同步、collectQueuePage、历史ORCID补邮箱均会调用。只修URI重载，不改变queryShards的显式关键词/主题分片语言。
- discovery_source_cursor：V32，source_name varchar(50) UNIQUE、cursor_value TEXT；ExpertDiscoveryService.loadSourceCheckpoint/persistSourceCheckpoint→repository.findBySourceName/save；queueLegacySeedCursor另读旧表。
- DiscoveryCheckpointCodec.sourceKey使用canonicalCriteria；**queueQueryHash:1433-1438另外直接对canonicalCriteria作SHA256，并未调用sourceKey**。09a必须同时改用新增来源专用规范化函数，不能只改sourceKey就宣称覆盖队列。
- discovery_pipeline/collection_stream/paper_job：实读V133，stream UNIQUE(query_hash,source,epoch)，job租约/配额/抽取版本不变；pipeline.queryHashOf仍用全局canonicalCriteria。09a只隔离ORCID来源key/stream，不改流水线全局hash，不清理旧stream/job。
- 旧队列内容不会因为查询修复自动重取，09d专业准入继续约束其消费结果。
- **X9-1**：URI→查询集合→两套来源恢复key→下一页，09a A-1覆盖。

### S9-2：三层专家 ES

- 实读 `src/main/resources/es/orcid_info_raw.json`、candidate、application：根dynamic=false；researchFields/disciplineCategory keyword；externalIds enabled=false；identityVerification dynamic=false；expertClassification对象中type/version/positiveEvidence/negativeEvidence/fingerprint可持久化。旧mapping中的sendable遗留声明不代表可恢复业务读取。
- 唯一新增事实为researchFieldIds keyword数组；模型末尾默认null。ExpertIndexService.bootstrapIndices/updateMappingIfNeeded已对三层补齐mapping；不改mapping启动机制。
- 发现新建：ExpertDiscoveryService ORCID循环与consumeOutcomeInternal→buildProfile/buildOrcidProfile→toIndexMap→Writer.indexToRaw(create)；直接候选经promoteDiscoveredToCandidate(create)。09d统一资格；初始资料不够时只RAW并入队。
- 结构化补全：OpenAlexDataSource.parseAuthorBase→AuthorEnrichment→ExpertDiscoveryService.updateExpertAcademicFields。按真实_id、三层HEAD存在后partial update；发现身份CAS保护保留。新增字段只能在此明确写入，不通过无来源字符串填充。
- 自动层级：ExpertRevalidationService.promoteEligibleRawExperts/revalidateEnrichedRaw→evaluateRawPromotionGate→promoteRawToCandidate→Writer.writeCandidateDocument。09d discovery分支进入统一复评；候选revalidateCandidates同样复用。
- 历史邮箱回填：ExpertDiscoveryService.backfillRawEmailsAndPromote→updateRawDocumentEmail/promoteRawToCandidateWithEmail，已有isDiscovery跳过，09不改变其历史非discovery语义。
- 手动层级：campaign/ExpertIndexLevelOperationService、ExpertContactManagementService→Writer.promoteToCandidate/demoteToRaw；回复晋升Writer.promoteToApplication及重试记录。均复制_source，自动透传新字段；人工override不纳入本轮自动专业准入，申请记录不自动撤销。
- 分类回填：ExpertClassificationBackfillService.run→Writer.bulkUpdateExpertClassifications，只局部写分类、无upsert，不新增事实字段。09c使用同一classify，但它不会自动驱动候选删除；09d的补全/候选复评才维护层资格。不能把“分类回填完成”称为“候选清理完成”。
- 其他局部写：Writer.syncOperatorStatus/Batch、syncApplicationStatus、addTag/removeTag、markApplicationClosed；09不改变这些字段。现有removeFromCandidateIndex语义保留，新专业复评使用独立窄CAS helper；不能将通用函数的404语义改掉影响回复晋升统计。
- 读取：ExpertSearchService.toExpertProfile/sourceFields/分页/findByDocumentIds；classification读取/前端画像/API DTO；CandidateEligibilityService；ExpertRevalidationService；发送RecipientScope/ExpertSearchService.expertTypesFilter按type筛选。新字段只供内部画像判断，不增加页面/DTO必填项。
- scripts和直接索引访问另见主审计C3与本次grep回执，09不执行修数据脚本。修改store不意味着授权改所有读写入口。
- **X9-2**：mapping→投影→profile→三层透传，09b A-1、09c A-2覆盖。
- **X9-3**：补全field IDs→classify→资格→可见类型，09c A-1、09d A-1覆盖。
- **X9-5**：候选条件撤下→RAW留存→application/联系人/邮件不动，09d A-2/A-3覆盖。

### S9-3：学术补全任务与资格设置

- V131 expert_academic_enrichment_job：UNIQUE(expert_doc_id)，任务状态PENDING/RUNNING/RETRY_WAIT/SUCCEEDED/UNMATCHED/FAILED，lease_token条件完成。JobService.enqueue/claimDue/complete/reopenFailed→Repository条件INSERT/UPDATE是任务写入口；worker/人工DISCOVERY_PENDING同用processClaimedEnrichmentJobBatch。
- 当前processClaimedEnrichmentJobBatch:2404先complete，2418后RAW-only复评；复评错误只log。09d必须把资格完成纳入Success之前，沿RetryableError/Partial映射既有RETRY_WAIT；拒绝/待专业确认是业务结果，不是基础设施错误，不无限重试。
- enrichProfiles亦被普通人工批量补全调用，所以不能只改worker外层。调用新统一复评放在该共享方法成功写层之后。
- V26 eligibility_filter_setting：setting_key UNIQUE、setting_value VARCHAR；EligibilityFilterService DB读/一分钟缓存、update保存，CandidateEligibilityService读配置。09不新增设置、不改DB默认值、不迁移缓存。新增discovery专业约束是用户此次明确的自动候选业务规则，复用已有分类/拒绝字段展示。
- **X9-4**：事实写入→资格持久化/层调整→job终态→可重试恢复，09c A-2、09d A-1/A-2覆盖。

### S9-4：批量发送与历史边界

- 首发存在两条实现；当前未读取classification.version/sendable做隐藏过滤，ExpertClassificationVersionGateGuardTest白名单为空。
- 批量任务expert_types_json/RecipientScope/类型查询仍是可见筛选来源。09不写BatchSendTaskConfig、不新增首发黑名单；用户选择UNKNOWN仍具有其原配置含义。
- 本次候选自动资格仅覆盖DiscoveryIdentity.isDiscovery（身份凭证、发现emailSource或discovered标签），非discovery旧导入与手动override保留原规则。不能声称整个历史库从此全满足六类。
- 既有已快照/进行中的发送不会被本计划撤销。部署时机/停发由用户控制；计划执行不自动发布、不停任务、不发邮件。
- **X9-6**：classification类型→可见发送配置与候选查询；撤候选不等于删除contact/mail，09d A-3覆盖。

## 研究检查点

- CP9-1 已完成：默认RestTemplate URI handler真实复现双重编码；公开ORCID正确/错误/全库对照存档。测试改用MockRestServiceServer截获wire URI。
- CP9-2 已完成：[OpenAlex作者官方契约](https://help.openalex.org/data/authors/)说明topics带count、field、subfield、domain；[Fields官方契约](https://help.openalex.org/data/fields/)区分field与topic层级。现有代码已读取topics，只是丢掉field，无需新增API请求。
- CP9-3 已完成：六类ID来自既有SubjectScopeCatalog，用户确认不扩行业、保留高校科研。
- CP9-4 已完成：审计三层mapping、两套游标hash、补全任务完成顺序和人工override路径；09a/09d专门覆盖，不能照最初诊断仅改两个if。
- CP9-5 发布前检查（不是实现阻塞）：自动分类调度是否开启、新版本待重算量、未补专业/无可信ID数量；运行中补全/发送状态。只报告，不替用户开关任务或批量迁移。旧分类JSON可读、版本不参与发送已由守卫代码证明。

## 回执与知识

[write-read-sites](discovery-repair-evidence/scope-09/write-read-sites.txt)、[field-sites](discovery-repair-evidence/scope-09/field-sites.txt)为实际检索输出。七条相关知识已加载；旧知识中“enrichment从不写最近年份”“升分类版本必然清空发送池”已按当前代码纠正，不能再用于设计。没有因时间衰减归档本次仍使用的条目；不存在五条同题重叠需要合并。已有三层补全规则已在CLAUDE推广，不重复追加。
