# 批量发信按需取人与分类时间修复：证据账本

日期：2026-10-22。证据对应当前 dirty 工作树；既有改动不属于本计划。以下命令均为读取，未修改业务代码或线上数据。新设计与既有事实必须分开阅读；此文件不是实施授权。

## E-01 分类读写缺陷

生产源码定位：`ExpertDiscoveryService.kt:3275` 将含 LocalDateTime 的领域对象放进 doc；`:3276-3286` 同时喂给普通 doc 和 discovered 条件脚本的 params.doc，再对已存在的三层发送局部更新。`RestTemplateConfig.kt:62` 创建默认 RestTemplate；`ExpertIndexWriterService.kt:361-370` 已有显式字符串格式化方法。

检索回执：
```text
scripts/import_contactout_visible_candidates.py:173:            "expertClassification": {
scripts/import_contactout_visible_candidates.py:181:                "classifiedAt": now,
scripts/import_contactout_visible_candidates.py:206:        "docs": [{"_id": doc["_id"], "_source": ["email", "researchFields", "funnelLevel", "tags", "operatorStatus", "expertClassification.type"]} for doc in docs]
scripts/import_contactout_visible_candidates.py:216:                or (source.get("expertClassification") or {}).get("type") not in ALLOWED_TYPES):
scripts/build_contactout_enterprise_es_import.py:246:            "expertClassification": {
scripts/build_contactout_enterprise_es_import.py:254:                "classifiedAt": now,
scripts/apply_legacy_discovery_approval.py:23:FIELDS = list(BINDING_FIELDS) + ['identityVerification', 'operatorStatus', 'tags', 'expertClassification', 'filterResult']
scripts/apply_legacy_discovery_approval.py:43:            (s.get('expertClassification') or {}).get('type', '') in
src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertClassification.kt:30:    val classifiedAt: LocalDateTime
src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt:33:    val expertClassification: ExpertClassification? = null,
scripts/expert_discovery/enterprise_batch/import_personal_email_candidates.py:144:            "expertClassification": {
scripts/expert_discovery/enterprise_batch/import_personal_email_candidates.py:157:                "classifiedAt": now,
scripts/expert_discovery/enterprise_batch/import_personal_email_candidates.py:173:        "docs": [{"_id": item["_id"], "_source": ["email", "researchFields", "tags", "operatorStatus", "expertClassification.type", "dataSource"]} for item in docs]
scripts/expert_discovery/enterprise_batch/import_personal_email_candidates.py:182:        if source.get("operatorStatus") not in (None, "") or source.get("expertClassification", {}).get("type") != "PRODUCTION_RND" or PERSONAL_TAG not in (source.get("tags") or []):
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt:25: *   与同一 `expertClassificationExecutor`：抢锁失败只记 skip，不排队第二个任务（I4-3）。
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt:42:    @Qualifier("expertClassificationExecutor")
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt:43:    private val expertClassificationExecutor: Executor
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt:88:            expertClassificationExecutor.execute {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewScanService.kt:341:            "identityVerification", "institutionEvidence", "filterResult", "tags", "expertClassification",
scripts/expert_discovery/enterprise_batch/import_es_documents.py:132:                    {"terms": {"expertClassification.type": ["PRODUCTION_RND", "HYBRID_RND", "ACADEMIC_RND"]}},
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:103:         * 语义为 `expertClassification.type` 字段**不存在**（见 [expertTypePredicate]）。
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:137:         * （K-batch-multi-value-filter-seams）。`UNCLASSIFIED` = `expertClassification.type`
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:144:                        "must_not" to listOf(mapOf("exists" to mapOf("field" to "expertClassification.type")))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:148:                mapOf("term" to mapOf("expertClassification.type" to type))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:512:            expertClassification = parseExpertClassification(source.path("expertClassification")),
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:522:     * 显式解析 ES 中的 `expertClassification` 对象（I1-5）。缺失/null → null；
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:533:                "Unknown expertClassification type '{}'; treating whole classification as null (fail closed)",
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:544:        val classifiedAt = parseClassifiedAt(node.path("classifiedAt")) ?: return null
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:553:            classifiedAt = classifiedAt
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:608:            "expertClassification"
scripts/expert_discovery/enterprise_batch/import_candidate_es.py:159:            "expertClassification": {
scripts/expert_discovery/enterprise_batch/import_candidate_es.py:167:                "classifiedAt": now,
scripts/expert_discovery/enterprise_batch/import_candidate_es.py:251:            {"term": {"expertClassification.type": "PRODUCTION_RND"}},
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:30:    private val expertClassificationService: ExpertClassificationService = ExpertClassificationService(),
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:31:    private val expertClassificationProperties: ExpertClassificationProperties = ExpertClassificationProperties(),
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:296:            put("expertClassification", expertIndexWriterService.classificationNode(classification))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:312:        // I3-3: 现算，不读 profile.expertClassification（RAW 层几乎恒为 null）。
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:313:        val classification = expertClassificationService.classify(profile)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:315:        if (expertClassificationProperties.promotionGateEnabled &&
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:395:                        docId, snapshot, expertClassificationService.classify(profile), managedReasons, preserveApplication = applied
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:406:                val classification = expertClassificationService.classify(profile)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:421:            val classification = expertClassificationService.classify(profile)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:98:    private val expertClassificationService: ExpertClassificationService,
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:2505:            "expertClassification" to expertIndexWriterService.classificationNode(expertClassificationService.classify(profile)),
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:3275:        doc["expertClassification"] = expertClassificationService.classify(enrichedProfile)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt:73: * - onlyPending 过滤 = `must_not exists expertClassification.version OR must_not term version=rnd-v2-2026`
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt:93:            val result = failImmediately(request, executionId, "分类 mapping 缺失：索引缺少 expertClassification 字段（type/sendable/version），已停止")
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt:182:                            "must_not" to listOf(mapOf("exists" to mapOf("field" to "expertClassification.version")))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt:188:                                mapOf("term" to mapOf("expertClassification.version" to ExpertClassificationService.VERSION))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:236:     * `{"doc":{"expertClassification":{...}},"doc_as_upsert":false}` —— 只写分类对象，
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:257:                    "doc" to mapOf("expertClassification" to classificationNode(item.classification)),
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:330:     * `expertClassification.type`（keyword）。缺失/异常 → false，调用方应在扫描前停止。
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:345:                    .path("expertClassification")
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:356:            log.warn("Failed to check expertClassification mapping for index {}", index, e)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:370:            put("classifiedAt", classification.classifiedAt.format(dateFormatter))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:817:            "expertClassification" to classificationNode(classification)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationService.kt:20: * [ExpertClassification.classifiedAt] 来自注入的 [clock]。
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationService.kt:75:            classifiedAt = LocalDateTime.now(clock)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt:27: * 提交单线程 `expertClassificationExecutor` 异步执行 → 立即返回 202；任务运行中 409；
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt:37:    @Qualifier("expertClassificationExecutor")
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt:38:    private val expertClassificationExecutor: Executor
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt:82:            expertClassificationExecutor.execute {
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:495:                expertType = expert.expertClassification?.type?.name,
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:496:                expertProductionScore = expert.expertClassification?.productionScore,
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:497:                expertResearchScore = expert.expertClassification?.researchScore
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt:256:            RND_SCOPE_UNCONFIRMED to CandidateFact("研发范围未确认", "expertClassification.type", RND_TYPES) {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt:257:                it.expertClassification?.type?.name
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt:259:            RND_EVIDENCE_INSUFFICIENT to CandidateFact("研发证据不足", "expertClassification.type", RND_TYPES) {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt:260:                it.expertClassification?.type?.name
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt:262:            "RND_OUT_OF_SCOPE" to CandidateFact("不在研发范围内", "expertClassification.type", RND_TYPES) {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt:263:                it.expertClassification?.type?.name
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt:265:            "RND_SERVICE_ONLY" to CandidateFact("仅有服务性工作", "expertClassification.type", RND_TYPES) {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt:266:                it.expertClassification?.type?.name
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:270:        val typeName = profile.expertClassification?.type?.name
src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:68:            val typeName = expert.expertClassification?.type?.name
src/main/kotlin/com/weibo/talentintroduction/config/ExpertClassificationProperties.kt:14: * 两类证据充分的专家；**不控制分类写入**——无论开关状态，被晋升的文档都会带上 `expertClassification`。
src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt:60:    @Bean("expertClassificationExecutor")
src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt:61:    fun expertClassificationExecutor(): Executor {

```

关键代码逐字摘录：
```text
  3260	        // I1-3: null 时不写入该键，避免覆盖发现时的真实值；I1-4: 非 null 时无条件覆盖。
  3261	        enrichment.lastPublicationYear?.let { doc["lastPublicationYear"] = it }
  3262	        val enrichedProfile = profile.copy(
  3263	            hIndex = enrichment.hIndex ?: profile.hIndex,
  3264	            citationCount = enrichment.citationCount ?: profile.citationCount,
  3265	            worksCount = enrichment.worksCount ?: profile.worksCount,
  3266	            researchFields = enrichment.topics?.takeIf { it.isNotEmpty() }?.joinToString(", ")
  3267	                ?: profile.researchFields,
  3268	            researchFieldIds = enrichment.researchFieldIds ?: profile.researchFieldIds,
  3269	            recentWorkTitles = enrichment.recentWorkTitles?.takeIf { it.isNotEmpty() }
  3270	                ?: profile.recentWorkTitles,
  3271	            patentTitles = enrichment.patentTitles?.takeIf { it.isNotEmpty() } ?: profile.patentTitles,
  3272	            disciplineCategory = enrichment.disciplineCategory ?: profile.disciplineCategory,
  3273	            lastPublicationYear = enrichment.lastPublicationYear ?: profile.lastPublicationYear
  3274	        )
  3275	        doc["expertClassification"] = expertClassificationService.classify(enrichedProfile)
  3276	        val updateBody: Map<String, Any> = if (DiscoveryIdentity.isDiscovery(profile)) {
  3277	            mapOf("script" to mapOf("lang" to "painless", "source" to
  3278	                "if (ctx._source.identityVerification != params.identity || ctx._source.email != params.email || ctx._source.givenNames != params.given || ctx._source.familyNames != params.family || ctx._source.externalIds != params.externalIds) { ctx.op = 'none'; } else { for (entry in params.doc.entrySet()) { ctx._source[entry.getKey()] = entry.getValue(); } }",
  3279	                "params" to mapOf("identity" to profile.identityVerification, "email" to profile.email,
  3280	                    "given" to profile.givenNames, "family" to profile.familyNames,
  3281	                    "externalIds" to profile.externalIds?.let { objectMapper.readValue(it, Map::class.java) }, "doc" to doc)))
  3282	        } else mapOf("doc" to doc)
  3283	        return LayerUpdateResult(
  3284	            raw = updateAcademicFieldsInLayer(ExpertIndexLevel.RAW, docId, updateBody),
  3285	            candidate = updateAcademicFieldsInLayer(ExpertIndexLevel.CANDIDATE, docId, updateBody),
  3286	            application = updateAcademicFieldsInLayer(ExpertIndexLevel.APPLICATION, docId, updateBody)
  3287	        )
  3288	    }
  3289	
  3290	    /**
   526	    private fun parseExpertClassification(node: JsonNode): ExpertClassification? {
   527	        if (node.isMissingNode || node.isNull) return null
   528	        val typeName = node.nullableText("type") ?: return null
   529	        val type = try {
   530	            ExpertType.valueOf(typeName)
   531	        } catch (e: IllegalArgumentException) {
   532	            log.warn(
   533	                "Unknown expertClassification type '{}'; treating whole classification as null (fail closed)",
   534	                typeName
   535	            )
   536	            return null
   537	        }
   538	        val productionScore = node.path("productionScore").takeIf { it.isIntegralNumber }?.asInt() ?: return null
   539	        val researchScore = node.path("researchScore").takeIf { it.isIntegralNumber }?.asInt() ?: return null
   540	        val positiveEvidence = stringArrayOrNull(node, "positiveEvidence") ?: return null
   541	        val negativeEvidence = stringArrayOrNull(node, "negativeEvidence") ?: return null
   542	        val version = node.nullableText("version") ?: return null
   543	        val sourceFingerprint = node.nullableText("sourceFingerprint") ?: return null
   544	        val classifiedAt = parseClassifiedAt(node.path("classifiedAt")) ?: return null
   545	        return ExpertClassification(
   546	            type = type,
   547	            productionScore = productionScore,
   548	            researchScore = researchScore,
   549	            positiveEvidence = positiveEvidence,
   550	            negativeEvidence = negativeEvidence,
   551	            version = version,
   552	            sourceFingerprint = sourceFingerprint,
   553	            classifiedAt = classifiedAt
   554	        )
   555	    }
   556	
   557	    private fun stringArrayOrNull(node: JsonNode, field: String): List<String>? {
   558	        val array = node.path(field)
   559	        if (array.isMissingNode || array.isNull) return null
   560	        if (!array.isArray) return null
   561	        if (!array.all(JsonNode::isTextual)) return null
   562	        return array.map { it.asText() }
   563	    }
   564	
   565	    /** 支持 mapping 声明的三种格式：`yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd`、epoch_millis。 */
   566	    private fun parseClassifiedAt(node: JsonNode): LocalDateTime? {
   567	        if (node.isMissingNode || node.isNull) return null
   568	        if (node.isIntegralNumber) {
   569	            return try {
   570	                LocalDateTime.ofInstant(Instant.ofEpochMilli(node.asLong()), ZoneId.systemDefault())
   571	            } catch (e: DateTimeException) {
   572	                null
   573	            }
   574	        }
   575	        if (!node.isTextual) return null
   576	        val raw = node.asText()
   577	        return try {
   578	            LocalDateTime.parse(raw, CLASSIFIED_AT_TIME_FORMATTER)
   579	        } catch (e: DateTimeParseException) {
   580	            try {
   581	                LocalDate.parse(raw, CLASSIFIED_AT_DATE_FORMATTER).atStartOfDay()
   582	            } catch (e2: DateTimeParseException) {
   583	                null
   584	            }
   585	        }
   586	    }
   587	
   588	    private fun JsonNode.nullableText(field: String): String? {
   228	    private fun versionTokenOf(
   229	        template: MailComposeTemplate,
   230	        blocks: List<MailComposeTemplateBlock>
   231	    ): String {
   232	        val canonical = buildString {
   233	            append(template.id).append('|')
   234	            append(template.subject).append('|')
   235	            append(template.subjectSnippetId).append('|')
   236	            append(template.mailType).append('|')
   237	            append(template.enabled).append('\n')
   238	            blocks.sortedBy { it.blockOrder }.forEach { block ->
   239	                append(block.blockOrder).append(':')
   240	                    .append(block.blockType).append(':')
   241	                    .append(block.refId).append(':')
   242	                    .append(block.customText).append('\n')
   243	            }
   244	        }
   245	        return MessageDigest.getInstance("SHA-256")
   246	            .digest(canonical.toByteArray(Charsets.UTF_8))
   396	class OutcomeAccumulator(private val target: Int) {
   397	    private val failureReasons = mutableMapOf<String, Int>()
   398	    private val skippedReasons = mutableMapOf<String, Int>()
   399	    private val errorSamples = mutableListOf<String>()
   400	
   401	    var success: Int = 0
   402	        private set
   403	    var failure: Int = 0
   404	        private set
   405	    var skipped: Int = 0
   406	        private set
   407	
   408	    fun recordSuccess() {
   409	        success++
   410	    }
   411	
   412	    fun recordFailure(code: String, sample: String? = null) {
   413	        failure++
   414	        failureReasons.merge(code, 1) { a, b -> a + b }
   415	        addSample(sample)
   416	    }
   417	
   418	    fun recordSkipped(code: String, sample: String? = null) {
   419	        skipped++
   420	        skippedReasons.merge(code, 1) { a, b -> a + b }
   421	        addSample(sample)
   422	    }
   423	
   424	    fun remaining(): Int = (target - success - failure - skipped).coerceAtLeast(0)
   425	
   426	    /**
   427	     * Move unprocessed slots into skipped reasons for terminal stop codes (I-6 coverage).
   428	     * Leaves remaining untouched for soft stops like ONE_ROUND_DONE.
   429	     */
   430	    fun annotateTerminalRemaining(stopReason: String?) {
   431	        val left = remaining()
   432	        if (left <= 0) return
   433	        val code = when (stopReason) {
   434	            "CANCELLED" -> BatchOutcomeReasonCodes.CANCELLED
   435	            "DAILY_CAP_REACHED" -> BatchOutcomeReasonCodes.DAILY_CAP_EXCEEDED
   436	            "NO_AVAILABLE_ACCOUNT", "NO_SENDABLE_ACCOUNT",
   437	            "WARMUP_LIMIT_REACHED", "DAILY_LIMIT_REACHED" -> BatchOutcomeReasonCodes.ACCOUNT_UNAVAILABLE
   438	            else -> return
   439	        }
   440	        repeat(left) { recordSkipped(code) }
   441	    }
   442	
   443	    fun toBreakdown(): OutcomeBreakdown = OutcomeBreakdown(
   444	        target = target,
   445	        success = success,

```

### 本地 wire-format 复现（已执行，非推测）

使用 target WAR 依赖中的 Spring/Jackson，Java 11 source launcher；不启动应用、不联网。
```java
RestTemplate rest = new RestTemplate();
for (Object converter : rest.getMessageConverters()) {
  if (converter instanceof MappingJackson2HttpMessageConverter) {
    var json = (MappingJackson2HttpMessageConverter) converter;
    System.out.println(json.getObjectMapper().writeValueAsString(
      Map.of("expertClassification", Map.of("classifiedAt",
        LocalDateTime.of(2026,10,22,2,47,14,390627000)))));
  }
}
```
实测命令：`/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java --class-path 'target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' /tmp/ClassificationDateProbe.java`
输出：
```json
{"expertClassification":{"classifiedAt":[2026,10,22,2,47,14,390627000]}}
```
此实验验证默认 HTTP converter 的 LocalDateTime 形状；全链路补全调用的回归测试由 01 增补，不能把本实验说成全链路测试已通过。

## E-02 线上只读核验

凭据仅在远端进程内读取并用于其配置的腾讯云 ES；未输出、未保存。请求：GET /、三层 GET /_mapping、打开 candidate PIT、size=0/match_none 搜索、DELETE /_pit 释放自己的查询上下文。无业务数据更新。
```text
version 7.14.2
orcid_info {"dynamic": null, "orcidId": {"type": "keyword"}, "classification": {"properties": {"classifiedAt": {"type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"}, "negativeEvidence": {"type": "keyword"}, "positiveEvidence": {"type": "keyword"}, "productionScore": {"type": "integer"}, "researchScore": {"type": "integer"}, "sendable": {"type": "boolean"}, "sourceFingerprint": {"type": "keyword"}, "type": {"type": "keyword"}, "version": {"type": "keyword"}}}}
orcid_info_candidate {"dynamic": null, "orcidId": {"type": "keyword"}, "classification": {"properties": {"classifiedAt": {"type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"}, "negativeEvidence": {"type": "keyword"}, "positiveEvidence": {"type": "keyword"}, "primaryResearchFieldSource": {"type": "keyword"}, "productionScore": {"type": "integer"}, "researchScore": {"type": "integer"}, "sendable": {"type": "boolean"}, "sourceFingerprint": {"type": "keyword"}, "type": {"type": "keyword"}, "version": {"type": "keyword"}}}}
orcid_info_application {"dynamic": "false", "orcidId": {"type": "keyword"}, "classification": {"properties": {"classifiedAt": {"type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"}, "negativeEvidence": {"type": "keyword"}, "positiveEvidence": {"type": "keyword"}, "productionScore": {"type": "integer"}, "researchScore": {"type": "integer"}, "sendable": {"type": "boolean"}, "sourceFingerprint": {"type": "keyword"}, "type": {"type": "keyword"}, "version": {"type": "keyword"}}}}
pit_probe {"timed_out": false, "failed_shards": 0, "hits": 0}
pit_closed True

```
已证实：7.14.2，PIT 具有权限，零失败分片，查询上下文已关闭。未声称已验证深翻页、并发续期、SMTP 期间续期；这些列入 02/04 验收。

前序同日诊断快照（不是本轮重新统计、不是上线验收固定数字）：30013→地区23500→classifiedAt解析失败排除21738→1762→准入缺失27→1735→姓氏缺失4→1731→历史不可达5→1726；17点任务日志目标1726。数组样例 `[2026,10,22,2,47,14,390627000]`。16点任务1757、成功30、明确不可达1与后续目标差31一致；不可由当前快照反推历史每层精确人数。

## E-03 索引与数据库结构

```text
src/main/resources/es/orcid_info_raw.json-47-      "patentTitles": { "type": "keyword" },
src/main/resources/es/orcid_info_raw.json-48-      "enrichedAt": { "type": "keyword" },
src/main/resources/es/orcid_info_raw.json-49-      "enrichmentSource": { "type": "keyword" },
src/main/resources/es/orcid_info_raw.json:50:      "expertClassification": {
src/main/resources/es/orcid_info_raw.json-51-        "type": "object",
src/main/resources/es/orcid_info_raw.json-52-        "properties": {
src/main/resources/es/orcid_info_raw.json-53-          "type": { "type": "keyword" },
src/main/resources/es/orcid_info_raw.json-54-          "sendable": { "type": "boolean" },
src/main/resources/es/orcid_info_raw.json-55-          "productionScore": { "type": "integer" },
src/main/resources/es/orcid_info_raw.json-56-          "researchScore": { "type": "integer" },
src/main/resources/es/orcid_info_raw.json-57-          "positiveEvidence": { "type": "keyword" },
src/main/resources/es/orcid_info_raw.json-58-          "negativeEvidence": { "type": "keyword" },
src/main/resources/es/orcid_info_raw.json-59-          "version": { "type": "keyword" },
src/main/resources/es/orcid_info_raw.json-60-          "sourceFingerprint": { "type": "keyword" },
src/main/resources/es/orcid_info_raw.json-61-          "classifiedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" }
src/main/resources/es/orcid_info_raw.json-62-        }
src/main/resources/es/orcid_info_raw.json-63-      },
src/main/resources/es/orcid_info_raw.json-64-      "updatedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" }
src/main/resources/es/orcid_info_raw.json-65-    }
src/main/resources/es/orcid_info_raw.json-66-  }
src/main/resources/es/orcid_info_raw.json-67-}
--
src/main/resources/es/orcid_info_candidate.json-50-      "patentTitles": { "type": "keyword" },
src/main/resources/es/orcid_info_candidate.json-51-      "enrichedAt": { "type": "keyword" },
src/main/resources/es/orcid_info_candidate.json-52-      "enrichmentSource": { "type": "keyword" },
src/main/resources/es/orcid_info_candidate.json:53:      "expertClassification": {
src/main/resources/es/orcid_info_candidate.json-54-        "type": "object",
src/main/resources/es/orcid_info_candidate.json-55-        "properties": {
src/main/resources/es/orcid_info_candidate.json-56-          "type": { "type": "keyword" },
src/main/resources/es/orcid_info_candidate.json-57-          "sendable": { "type": "boolean" },
src/main/resources/es/orcid_info_candidate.json-58-          "productionScore": { "type": "integer" },
src/main/resources/es/orcid_info_candidate.json-59-          "researchScore": { "type": "integer" },
src/main/resources/es/orcid_info_candidate.json-60-          "positiveEvidence": { "type": "keyword" },
src/main/resources/es/orcid_info_candidate.json-61-          "negativeEvidence": { "type": "keyword" },
src/main/resources/es/orcid_info_candidate.json-62-          "version": { "type": "keyword" },
src/main/resources/es/orcid_info_candidate.json-63-          "sourceFingerprint": { "type": "keyword" },
src/main/resources/es/orcid_info_candidate.json-64-          "classifiedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" }
src/main/resources/es/orcid_info_candidate.json-65-        }
src/main/resources/es/orcid_info_candidate.json-66-      }
src/main/resources/es/orcid_info_candidate.json-67-    }
src/main/resources/es/orcid_info_candidate.json-68-  }
src/main/resources/es/orcid_info_candidate.json-69-}
--
src/main/resources/es/orcid_info_application.json-60-      "patentTitles": { "type": "keyword" },
src/main/resources/es/orcid_info_application.json-61-      "enrichedAt": { "type": "keyword" },
src/main/resources/es/orcid_info_application.json-62-      "enrichmentSource": { "type": "keyword" },
src/main/resources/es/orcid_info_application.json:63:      "expertClassification": {
src/main/resources/es/orcid_info_application.json-64-        "type": "object",
src/main/resources/es/orcid_info_application.json-65-        "properties": {
src/main/resources/es/orcid_info_application.json-66-          "type": { "type": "keyword" },
src/main/resources/es/orcid_info_application.json-67-          "sendable": { "type": "boolean" },
src/main/resources/es/orcid_info_application.json-68-          "productionScore": { "type": "integer" },
src/main/resources/es/orcid_info_application.json-69-          "researchScore": { "type": "integer" },
src/main/resources/es/orcid_info_application.json-70-          "positiveEvidence": { "type": "keyword" },
src/main/resources/es/orcid_info_application.json-71-          "negativeEvidence": { "type": "keyword" },
src/main/resources/es/orcid_info_application.json-72-          "version": { "type": "keyword" },
src/main/resources/es/orcid_info_application.json-73-          "sourceFingerprint": { "type": "keyword" },
src/main/resources/es/orcid_info_application.json-74-          "classifiedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" }
src/main/resources/es/orcid_info_application.json-75-        }
src/main/resources/es/orcid_info_application.json-76-      }
src/main/resources/es/orcid_info_application.json-77-    }
src/main/resources/es/orcid_info_application.json-78-  }
src/main/resources/es/orcid_info_application.json-79-}
7:    "dynamic": false,
9:      "orcidId": { "type": "keyword" },
34:      "identityVerification": { "type": "object", "dynamic": false, "properties": {
src/main/resources/db/migration/V48__add_country_to_expert_contact.sql:1:ALTER TABLE expert_contact ADD COLUMN country VARCHAR(128) NULL;
src/main/resources/db/migration/V146__create_expert_contact_location.sql:1:CREATE TABLE expert_contact_location (
src/main/resources/db/migration/V11__expert_contact_auto_reply_flag.sql:1:ALTER TABLE expert_contact
src/main/resources/db/migration/V138__create_batch_email_verification.sql:39:CREATE TABLE batch_email_verification (
src/main/resources/db/migration/V35__add_task_progress_batch_reject_reasons.sql:1:ALTER TABLE task_progress_log
src/main/resources/db/migration/V51__add_follow_up_marked.sql:1:ALTER TABLE expert_contact
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:4:ALTER TABLE expert_contact
src/main/resources/db/migration/V86__add_expert_contact_sender_change_mark.sql:3:ALTER TABLE expert_contact
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:2:ALTER TABLE expert_contact
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:6:ALTER TABLE expert_contact
src/main/resources/db/migration/V22__create_task_progress_log.sql:1:CREATE TABLE task_progress_log (
src/main/resources/db/migration/V73__add_batch_config_id_to_task_execution.sql:1:ALTER TABLE task_execution
src/main/resources/db/migration/V73__add_batch_config_id_to_task_execution.sql:7:ALTER TABLE task_execution
src/main/resources/db/migration/V12__expert_contact_first_reply.sql:1:ALTER TABLE expert_contact
src/main/resources/db/migration/V1__create_business_tables.sql:79:CREATE TABLE expert_contact (
src/main/resources/db/migration/V137__task_execution_interruption_recovery.sql:1:ALTER TABLE task_execution
src/main/resources/db/migration/V150__create_expert_contact_note.sql:1:CREATE TABLE expert_contact_note (
src/main/resources/db/migration/V4__create_task_execution.sql:1:CREATE TABLE task_execution (
src/main/resources/db/migration/V8__add_expert_contact_status_history.sql:1:CREATE TABLE expert_contact_status_history (
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:4:SET @add_col = 'ALTER TABLE expert_contact ADD COLUMN operator_status VARCHAR(32) NOT NULL DEFAULT ''NOT_CONTACTED'' COMMENT ''运营视角专家状态: NOT_CONTACTED / CONTACTED / REPLIED / MATERIALS_RECEIVED / INVITED / COMPLETED''';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:15:SET @add_idx = 'ALTER TABLE expert_contact ADD INDEX idx_expert_contact_operator_status (operator_status, updated_at)';

```

## DB DDL

检索命令：
```bash
cat src/main/resources/db/migration/V4__create_task_execution.sql src/main/resources/db/migration/V22__create_task_progress_log.sql src/main/resources/db/migration/V61__create_mail_compose_template.sql src/main/resources/db/migration/V47__create_reply_snippet.sql src/main/resources/db/migration/V67__create_content_variant.sql; sed -n '79,102p' src/main/resources/db/migration/V1__create_business_tables.sql; cat src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql
```
回执：
```text
CREATE TABLE task_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_type VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_payload TEXT,
    result_summary TEXT,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at DATETIME NOT NULL,
    finished_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE task_progress_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_type VARCHAR(64) NOT NULL,
    task_execution_id BIGINT,
    batch_number INT NOT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'RUNNING, COMPLETED, FAILED, CANCELLED',
    processed_count BIGINT NOT NULL DEFAULT 0,
    total_count BIGINT NOT NULL DEFAULT 0,
    batch_processed INT NOT NULL DEFAULT 0 COMMENT '本批次处理数量',
    batch_passed INT NOT NULL DEFAULT 0 COMMENT '本批次通过/成功数量',
    batch_rejected INT NOT NULL DEFAULT 0 COMMENT '本批次拒绝/降级数量',
    message TEXT,
    details_json TEXT COMMENT 'JSON 格式的详细统计',
    errors_json TEXT COMMENT 'JSON 格式的错误列表',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tpl_task_type ON task_progress_log(task_type);
CREATE INDEX idx_tpl_execution_id ON task_progress_log(task_execution_id);
CREATE TABLE mail_compose_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    subject VARCHAR(255) NOT NULL COMMENT '邮件主题',
    description VARCHAR(500) NULL COMMENT '模板用途描述',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE mail_compose_template_block (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    block_order INT NOT NULL COMMENT '块排序（ASC）',
    block_type VARCHAR(30) NOT NULL COMMENT 'QA_RULE | REPLY_SNIPPET | CUSTOM_TEXT',
    ref_id BIGINT NULL COMMENT 'qa_rule.id 或 reply_snippet.id，CUSTOM_TEXT 时为 NULL',
    custom_text TEXT NULL COMMENT 'CUSTOM_TEXT 时存正文',
    FOREIGN KEY (template_id) REFERENCES mail_compose_template(id) ON DELETE CASCADE
);
CREATE TABLE reply_snippet (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    snippet_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 100,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO reply_snippet (snippet_type, content, display_order, is_default, enabled)
VALUES
    ('SALUTATION', 'Dear Professor,', 10, 1, 1),
    ('GREETING', 'Thank you for your email. Please find our answers below.', 10, 1, 1),
    ('CLOSING', 'Please let us know if you have any further questions.

Best regards,
Talent Introduction Team', 10, 1, 1),
    ('ACK', 'Thank you for sharing your CV.', 10, 0, 1),
    ('ACK', 'Thank you for sharing your materials.', 20, 0, 1),
    ('ACK', 'Thank you for your prompt reply.', 30, 0, 1);
CREATE TABLE content_variant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_type VARCHAR(32) NOT NULL COMMENT 'QA_RULE | REPLY_SNIPPET',
    owner_id BIGINT NOT NULL,
    variant_order INT NOT NULL DEFAULT 100,
    content TEXT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_owner (owner_type, owner_id)
);
CREATE TABLE expert_contact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    orcid_id VARCHAR(64) NOT NULL,
    expert_email VARCHAR(255) NOT NULL,
    expert_name VARCHAR(255),
    current_status VARCHAR(64) NOT NULL DEFAULT 'NEW',
    last_mail_at DATETIME,
    last_reply_at DATETIME,
    manual_handoff_required TINYINT(1) NOT NULL DEFAULT 0,
    closed_reason VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_campaign_expert (campaign_id, orcid_id),
    CONSTRAINT fk_expert_contact_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaign(id)
);

CREATE TABLE mail_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    mail_type VARCHAR(64) NOT NULL,
    message_id VARCHAR(255),
-- V85: 专家—发送账号绑定基座。
-- 绑定语义 = 主题发起权归属（回复仍由 mail_record.sender_account_code 决定）。
-- NULL 表示未绑定，禁止空串/哨兵值。
ALTER TABLE expert_contact
    ADD COLUMN bound_sender_account_code VARCHAR(64) NULL
    COMMENT '绑定的发件账号 code；NULL=未绑定。决定新发起主题邮件的发件账号',
    ADD COLUMN sender_account_bound_at DATETIME NULL
    COMMENT '绑定建立时间';

CREATE INDEX idx_expert_contact_bound_sender
    ON expert_contact (bound_sender_account_code);

-- 回填：取每个 contact 最早一封 OUTBOUND INTRODUCTION 的发件账号（IP-1 口径）。
-- 排除 SIMULATOR_NOOP（I-5）。WHERE ... IS NULL 保证重跑幂等（I-3）。
UPDATE expert_contact ec
JOIN (
    SELECT mr.expert_contact_id,
           SUBSTRING_INDEX(GROUP_CONCAT(mr.sender_account_code
                           ORDER BY mr.created_at ASC, mr.id ASC), ',', 1) AS first_code,
           MIN(mr.created_at) AS first_at
      FROM mail_record mr
     WHERE mr.direction = 'OUTBOUND'
       AND mr.mail_type = 'INTRODUCTION'
       AND mr.sender_account_code IS NOT NULL
       AND mr.sender_account_code <> ''
       AND mr.sender_account_code <> 'SIMULATOR_NOOP'
     GROUP BY mr.expert_contact_id
) f ON f.expert_contact_id = ec.id
SET ec.bound_sender_account_code = f.first_code,
    ec.sender_account_bound_at   = f.first_at
WHERE ec.bound_sender_account_code IS NULL;

```

## 实际调用点（含测试桩）

检索命令：
```bash
rg -n 'countBySnapshot\(|countEsTargets\(|scanBaseCandidates\(|fetchEsPage\(|buildFilteredRetryableTargets\(|buildMaterialReminderSnapshotFromScope\(' src/main/kotlin; rg -n 'findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc' src/main src/test; rg -n 'OutcomeAccumulator\(' src/main src/test; rg -n 'executionEligibility|roundQuota =|roundPassed <|roundNumber >=|perRoundIntervalMs >' src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt
```
回执：
```text
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:566:    fun countBySnapshot(snapshot: BatchExecutionSnapshot): PendingOutreachSummary = when (snapshot.mailType) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:598:                val retry = buildFilteredRetryableTargets(campaignId, scope, now, seenDocIds, gate, accumulator::record)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:604:            val (esEstimate, esExcluded) = countEsTargets(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:647:        val scan = scanBaseCandidates(scope, shouldStop)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:678:    private fun scanBaseCandidates(scope: RecipientScope, shouldStop: () -> Boolean = { false }): BaseScan {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:784:        val scan = scanBaseCandidates(scope)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:943:                val scan = scanBaseCandidates(scope) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:959:        val retry = buildFilteredRetryableTargets(campaignId, scope, filterNow, seenDocIds, gate)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:970:        val (esEstimate, _) = countEsTargets(scope, filterNow, {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1000:            fetchNextPage = { offset, size -> fetchEsPage(scope, offset, size) },
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1924:    private fun buildFilteredRetryableTargets(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:2215:        return buildMaterialReminderSnapshotFromScope(scope, scopeDescription, now, onSelection)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:2231:        return buildMaterialReminderSnapshotFromScope(scope, scopeDescription, verificationFilterNow())
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:2235:    private fun buildMaterialReminderSnapshotFromScope(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:2333:    private fun countEsTargets(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:2447:    private fun fetchEsPage(scope: RecipientScope, offset: Int, size: Int): List<ExpertProfile> {
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:103:        ResponseEntity.ok(manualInitialOutreachService.countBySnapshot(snapshot))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:757:            .findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1981:        val newContacts = expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:27:    fun findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:270:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:321:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:363:            Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:475:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:526:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:729:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:752:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:788:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(retry)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:927:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:995:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1035:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1095:            .findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(Mockito.anyLong(), Mockito.anyString())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1160:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1197:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1229:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1249:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1270:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1289:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1315:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1337:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1360:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1386:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1423:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1444:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1465:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1492:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1525:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1561:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1593:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1622:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1662:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1703:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1803:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1858:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1887:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1922:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1957:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1987:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2022:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2069:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2207:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2238:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2293:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2335:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2362:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2391:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2414:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2446:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2480:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2503:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2535:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2657:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2684:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2742:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3113:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4261:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4353:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4392:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4418:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4439:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4471:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4499:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4529:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4571:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4646:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4674:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5166:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5372:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5941:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:6004:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:6037:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:6153:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:6195:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:6561:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:6597:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:6633:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:6682:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:7547:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:7730:        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:730:        Mockito.`when`(outreach.expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW"))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:388:        val acc = OutcomeAccumulator(target = 10)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:398:        val acc = OutcomeAccumulator(target = 5)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:409:        val acc = OutcomeAccumulator(target = 8)
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:396:class OutcomeAccumulator(private val target: Int) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:292:        val accumulator = OutcomeAccumulator(totalEstimate)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:984:            val accumulator = OutcomeAccumulator(0)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1023:        val accumulator = OutcomeAccumulator(totalEstimate)
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt:346:        val accumulator = OutcomeAccumulator(target = 3)
313:            if (roundNumber >= snapshot.roundsPerRun) {
334:            val roundQuota = minOf(config.roundSize, estimatedRemaining, remainingAccountCapacity)
354:            while (roundPassed < roundQuota && targetIndex < targets.size) {
481:                if (intervalMs > 0 && roundPassed < roundQuota && targetIndex < targets.size) {
501:            if (config.perRoundIntervalMs > 0 && targetIndex < targets.size && roundNumber < snapshot.roundsPerRun) {
939:        val executionEligibility: Lazy<ExecutionPreflightEligibility>? = if (preflightService == null) {
964:            executionEligibility,
1008:                    executionEligibility,
1044:            if (roundNumber >= snapshot.roundsPerRun) {
1065:            val roundQuota = minOf(config.roundSize, estimatedRemaining, remainingAccountCapacity)
1086:            while (roundPassed < roundQuota && targetIterator.hasNext()) {
1660:                if (intervalMs > 0 && roundPassed < roundQuota && targetIterator.hasNext()) {
1681:            if (config.perRoundIntervalMs > 0 && targetIterator.hasNext() && roundNumber < snapshot.roundsPerRun) {

```

## 受影响存储写入路径

检索命令：
```bash
rg -n 'expertContactRepository\.(save|delete|update|rebind)|contactRepository\.(save|delete|update)|UPDATE expert_contact|INSERT INTO expert_contact|DELETE FROM expert_contact' src/main/kotlin; rg -n '(templateRepository|blockRepository|replySnippetRepository|contentVariantRepository|repository)\.(save|delete)|UPDATE (mail_compose_template|reply_snippet|content_variant)' src/main/kotlin/com/weibo/talentintroduction/{template,reply}/service src/main/kotlin/com/weibo/talentintroduction/template/repository; rg -n 'progressLogRepository\.(save|delete|bind)|UPDATE task_execution|DELETE FROM task_execution|taskExecutionRepository.save' src/main/kotlin
```
回执：
```text
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ContactCountryBackfillService.kt:75:            expertContactRepository.updateCountryById(contactId, country)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:118:                expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:134:                expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:57:        return expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:64:        return expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:277:        val updated = expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:287:        return expertContactRepository.save(contact.copy(currentIndexLevel = "CANDIDATE"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:296:        return expertContactRepository.save(contact.copy(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:316:                expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:30:        val updated = expertContactRepository.save(contact.copy(operatorStatus = target.name))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:64:        val updated = expertContactRepository.save(contact.copy(operatorStatus = targetStatus.name))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:88:        val updated = expertContactRepository.save(contact.copy(operatorStatus = EMAIL_INVALID))
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1637:                        expertContactRepository.save(contact.copy(needsManualAttention = false))
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1701:                    expertContactRepository.save(contact.copy(needsManualAttention = true))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1385:                        expertContactRepository.save(ExpertContact(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:69:        return expertContactRepository.save(contact.copy(currentIndexLevel = "CANDIDATE"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:86:        return expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:103:        return expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:110:        return expertContactRepository.save(contact.copy(currentIndexLevel = "RAW", applicationIndexed = false))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ConversationStateService.kt:26:        val saved = expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionService.kt:83:                val saved = expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:190:        expertContactRepository.save(currentContact)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:253:                        expertContactRepository.save(contact.copy(needsManualAttention = false))
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:67:    @Query("UPDATE expert_contact SET country = :country WHERE id = :id")
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:72:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:81:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:92:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:105:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:436:                    expertContactRepository.save(disabledContact.copy(needsManualAttention = true))
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationService.kt:278:            INSERT INTO expert_contact_location (expert_contact_id, country_code, zone_id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactNoteService.kt:74:        private const val DELETE_SQL = "DELETE FROM expert_contact_note WHERE expert_contact_id=:contactId"
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactNoteService.kt:76:            INSERT INTO expert_contact_note (expert_contact_id, note, updated_by, updated_at)
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:45:        expertContactRepository.updateBindingById(contactId, code, at)
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:58:        expertContactRepository.rebindSenderAccountById(contactId, target.accountCode, now)  // I-2
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:60:                replacement != null -> repository.save(replacement.copy(updatedAt = now))
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:62:                    repository.save(row.copy(isDefault = false, updatedAt = now))
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:268:        val saved = repository.save(
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:307:        val updated = repository.save(
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:333:        return toDetail(repository.save(existing.copy(enabled = enabled)))
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:346:            .forEach { repository.save(it.copy(isDefault = false)) }
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:348:        return toDetail(repository.save(existing.copy(isDefault = true)))
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:356:        repository.deleteById(id)
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:377:            .forEach { repository.save(it.copy(isDefault = false)) }
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:63:        val saved = templateRepository.save(
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:90:        templateRepository.save(
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:106:        blockRepository.deleteAllByTemplateId(id)
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:115:        templateRepository.save(
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:135:            templateRepository.deleteById(id)
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:142:        blockRepository.deleteAllByTemplateId(id)
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:599:            blockRepository.save(
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:111:        UPDATE task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:122:        UPDATE task_execution SET heartbeat_at = :now, updated_at = :now
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:129:        UPDATE task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:141:        UPDATE task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:155:        UPDATE task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:180:        DELETE FROM task_execution
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStore.kt:209:            progressLogRepository.save(logEntry)
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:42:            progressDeleted = purgeLoop { limit -> progressLogRepository.deleteOlderThan(cutoff, limit) }

```

## 前端调用与资源引用

检索命令：
```bash
rg -l 'refreshRecipientPreview|baseHintHtml|recipientPreview|统计命中人数|当前条件命中|renderOutcomeMetrics|renderIntegrityWarning|renderExecutionLive|batchLogMetrics' src/test/js; rg -n '\?v=' src/main/resources/static/index.html; rg -n 'batch-config-editor-hint|batch-recipient-preview-controls|batch-log-metric|batch-log-live-counts' src/main/resources/static/styles.css
```
回执：
```text
src/test/js/batchExecutionLogTimeline.test.js
src/test/js/batchSendTaskConsoleInteraction.test.js
src/test/js/batchSenderFilter.test.js
src/test/js/recipientPreviewDiagnostics.test.js
src/test/js/batchEmailVerification.test.js
src/test/js/batchLogDrawerLayout.test.js
src/test/js/batchManualExecutionLog.test.js
11:    <link rel="stylesheet" href="styles.css?v=20261021-reply-identity">
12:    <link rel="stylesheet" href="expert-materials.css?v=20261021-reply-identity">
13:    <link rel="stylesheet" href="mailbox-chat.css?v=20261021-reply-identity">
14:    <link rel="stylesheet" href="meeting-confirmation.css?v=20261021-reply-identity">
15:    <link rel="stylesheet" href="world-clock.css?v=20261021-reply-identity">
16:<link rel="stylesheet" href="meeting-records.css?v=20261021-reply-identity">
17:<link rel="stylesheet" href="contact-details.css?v=20261021-reply-identity">
2424:<script src="trust-reply-workbench.js?v=20261021-reply-identity"></script>
2425:<script src="expert-materials.js?v=20261021-reply-identity"></script>
2426:<script src="meeting-confirmation.js?v=20261021-reply-identity"></script>
2427:<script src="mailbox-chat.js?v=20261021-reply-identity"></script>
2428:<script src="contact-history.js?v=20261021-reply-identity"></script>
2429:<script src="meeting-records.js?v=20261021-reply-identity"></script>
2430:<script src="contact-details.js?v=20261021-reply-identity"></script>
2431:<script src="app.js?v=20261021-reply-identity"></script>
2432:<script src="world-clock.js?v=20261021-reply-identity"></script>
9298:.batch-config-editor-hint {
9308:.batch-config-editor-hint strong {
9313:.batch-recipient-preview-controls {
9327:.batch-recipient-preview-controls > div {
9334:.batch-recipient-preview-controls strong {
9339:.batch-recipient-preview-controls .button {
9343:.batch-recipient-preview-controls .button:disabled {
9349:  .batch-recipient-preview-controls {
9851:.batch-config-editor-hint .batch-gate-excluded {
9856:.batch-config-editor-hint .batch-gate-warnline {
9926:.batch-log-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 14px 0; }
9927:.batch-log-metric { padding: 10px; border: 1px solid rgba(15, 23, 42, .08); border-radius: var(--radius-md); background: var(--bg-subtle); }
9928:.batch-log-metric-label { color: var(--text-muted); font-size: 11px; }
9929:.batch-log-metric-value { margin-top: 3px; color: var(--text-main); font-size: 18px; font-weight: 700; }
9930:.batch-log-metric.is-success .batch-log-metric-value { color: var(--success); }
9931:.batch-log-metric.is-failure .batch-log-metric-value { color: var(--error); }
9932:.batch-log-metric.is-skipped .batch-log-metric-value { color: var(--warning); }
9944:.batch-log-live-counts { margin-top: 8px; color: var(--text-main); font-size: 12px; font-weight: 600; }
10025:  .batch-log-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }

```

## 前端逐字基线

检索命令：
```bash
sed -n '1565,1577p' src/main/resources/static/index.html; sed -n '1787,1801p' src/main/resources/static/index.html; sed -n '9288,9355p' src/main/resources/static/styles.css; sed -n '9848,9862p' src/main/resources/static/styles.css; sed -n '9926,9946p' src/main/resources/static/styles.css; sed -n '802,852p' src/main/resources/static/styles.css; sed -n '1,78p' src/main/resources/static/styles.css; rg -n 'batch-recipient-preview-controls|batchLogMetrics|batchLogLiveCounts|batchLogLiveFill' src/main/resources/static/index.html
```
回执：
```text
                        </label>
                    </div>
                    <div class="batch-config-editor-hint" id="batchConfigEditorVolumeHint"></div>
                    <div class="batch-recipient-preview-controls">
                        <div><strong id="batchConfigEditorRecipientStatus">尚未统计</strong><span>进入页面不会自动计算；点击按钮后按当前条件统计。</span></div>
                        <button class="button primary" type="button" id="batchConfigEditorRecipientPreviewBtn">统计命中人数</button>
                    </div>
                    <div class="batch-config-editor-hint" id="batchConfigEditorRecipientHint"></div>
                </section>

                <section class="batch-config-editor-section">
                    <div class="batch-config-editor-section-heading">
                        <h4>定时调度</h4>
                        </div>
                        <span class="batch-config-diff-badge" hidden>已修改</span>
                        <div class="batch-config-diff-original" hidden></div>
                    </div>
                </div>
                <div class="batch-recipient-preview-controls">
                    <div><strong id="batchManualRecipientStatus">尚未统计</strong><span>进入页面不会自动计算；点击按钮后按当前条件统计。</span></div>
                    <button class="button primary" type="button" id="batchManualRecipientPreviewBtn">统计命中人数</button>
                </div>
                <div class="batch-config-editor-hint" id="batchManualRecipientHint"></div>
            </section>

            <section class="batch-manual-section">
                <div class="batch-manual-section-heading">
                    <h4>发送控制</h4>
.batch-config-editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 16px;
}

.batch-config-editor-grid-controls {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.batch-config-editor-hint {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: rgba(37, 99, 235, .06);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.batch-config-editor-hint strong {
  color: var(--primary);
  font-weight: 600;
}

.batch-recipient-preview-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid rgba(37, 99, 235, .1);
  border-radius: var(--radius-md);
  background: rgba(37, 99, 235, .035);
  color: var(--text-secondary);
  font-size: 12px;
}

.batch-recipient-preview-controls > div {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.batch-recipient-preview-controls strong {
  color: var(--text-main);
  font-weight: 600;
}

.batch-recipient-preview-controls .button {
  flex: 0 0 auto;
}

.batch-recipient-preview-controls .button:disabled {
  cursor: wait;
  opacity: .7;
}

@media (max-width: 640px) {
  .batch-recipient-preview-controls {
    align-items: flex-start;
    flex-direction: column;
  }
}

.batch-cron-row {
  line-height: 1.5;
}

.batch-config-editor-hint .batch-gate-excluded {
  color: var(--error-strong);
  font-weight: 600;
}

.batch-config-editor-hint .batch-gate-warnline {
  display: block;
  margin-top: 4px;
  color: var(--warning-strong);
}

.batch-gate-pill {
.batch-log-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 14px 0; }
.batch-log-metric { padding: 10px; border: 1px solid rgba(15, 23, 42, .08); border-radius: var(--radius-md); background: var(--bg-subtle); }
.batch-log-metric-label { color: var(--text-muted); font-size: 11px; }
.batch-log-metric-value { margin-top: 3px; color: var(--text-main); font-size: 18px; font-weight: 700; }
.batch-log-metric.is-success .batch-log-metric-value { color: var(--success); }
.batch-log-metric.is-failure .batch-log-metric-value { color: var(--error); }
.batch-log-metric.is-skipped .batch-log-metric-value { color: var(--warning); }

.batch-reason-list { margin: 8px 0 16px; border: 1px solid rgba(15, 23, 42, .08); border-radius: var(--radius-md); overflow: hidden; }
.batch-reason-row { display: flex; justify-content: space-between; gap: 12px; padding: 10px 12px; color: var(--text-secondary); }
.batch-reason-row + .batch-reason-row { border-top: 1px solid rgba(15, 23, 42, .08); }
.batch-reason-count { color: var(--text-main); font-weight: 700; }
.batch-log-integrity-warning { padding: 10px 12px; border-radius: var(--radius-md); background: var(--warning-bg); color: #c2410c; }

/* 执行日志抽屉：运行中实时区 */
.batch-log-live { margin: 0 0 14px; padding: 12px; border: 1px solid rgba(37, 99, 235, .28); border-radius: var(--radius-md); background: var(--bg-subtle); }
.batch-log-live-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.batch-log-live-round { flex: 1; min-width: 0; color: var(--text-sidebar); font-size: 12px; }
.batch-log-live-counts { margin-top: 8px; color: var(--text-main); font-size: 12px; font-weight: 600; }
.batch-log-live-message { margin-top: 6px; color: var(--text-secondary); font-size: 12px; word-break: break-word; }
.batch-log-live-accounts { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    min-height: 32px;
    height: 32px;
    padding: 0 12px;
    border-radius: var(--radius-sm);
    font-weight: 500;
    font-size: 12px;
    cursor: pointer;
    border: 1px solid var(--border);
    background-color: transparent;
    color: var(--text-main);
    transition: transform 0.12s ease, box-shadow 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, opacity 0.1s ease;
    outline: none;
    user-select: none;
    font-family: var(--font-body);
    position: relative;
    overflow: hidden;
}

.button:hover {
    border-color: rgba(15, 23, 42, 0.2);
    background-color: var(--surface);
    transform: translateY(-1px);
    box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
}

.button:active {
    transform: translateY(0) scale(0.97);
    box-shadow: none;
    opacity: 0.85;
}

.button.primary {
    background-image: linear-gradient(180deg, var(--primary-bright), var(--primary));
    background-color: var(--primary);
    border-color: transparent;
    color: #ffffff;
    font-weight: 600;
    box-shadow: 0 1px 2px rgba(var(--primary-rgb), 0.4), inset 0 1px 0 rgba(255,255,255,0.18);
}

.button.primary:hover {
    background-image: linear-gradient(180deg, #2f7bff, var(--primary-hover));
    box-shadow: 0 4px 14px rgba(var(--primary-rgb), 0.35), inset 0 1px 0 rgba(255,255,255,0.18);
}

.button.secondary {
:root {
    /* Brand — modern business blue */
    --primary: #1e40af;
    --primary-hover: #1e3a8a;
    --primary-active: #172554;
    --primary-rgb: 30, 64, 175;
    --primary-light: rgba(var(--primary-rgb), 0.07);
    --primary-tint: rgba(var(--primary-rgb), 0.1);

    --bg-main: #f5f7fb;
    --bg-sidebar: #ffffff;
    --bg-sidebar-hover: rgba(var(--primary-rgb), 0.06);
    --bg-sidebar-active: rgba(var(--primary-rgb), 0.09);

    --panel-bg: rgba(255, 255, 255, 0.55);
    --panel-border: rgba(15, 23, 42, 0.08);
    --line: rgba(15, 23, 42, 0.055);
    --border: rgba(15, 23, 42, 0.11);
    --surface: rgba(15, 23, 42, 0.022);

    --text-main: #1e293b;
    --text-muted: #94a3b8;
    --text-sidebar: #64748b;
    --text-sidebar-active: #1e293b;
    --text-secondary: #475569;
    --text-strong: #334155;
    --ink: #1e293b;
    --bg-subtle: #f8fafc;
    --border-strong: #cbd5e1;
    --primary-bright: #3b82f6;

    --success: #059669;
    --success-rgb: 5, 150, 105;
    --success-bg: rgba(var(--success-rgb), 0.08);
    --success-border: rgba(var(--success-rgb), 0.18);
    --green: var(--success);

    --error: #e11d48;
    --error-rgb: 225, 29, 72;
    --error-bg: rgba(var(--error-rgb), 0.07);
    --error-border: rgba(var(--error-rgb), 0.16);
    --error-strong: #be123c;
    --red: var(--error);

    --warning: #d97706;
    --warning-rgb: 217, 119, 6;
    --warning-bg: rgba(var(--warning-rgb), 0.08);
    --warning-border: rgba(var(--warning-rgb), 0.2);
    --warning-strong: #b45309;
    --warning-bright: #f59e0b;
    --amber: var(--warning);

    --info: #0ea5e9;
    --info-rgb: 14, 165, 233;
    --info-bg: rgba(var(--info-rgb), 0.08);
    --info-border: rgba(var(--info-rgb), 0.2);

    --z-sticky: 10;
    --z-dropdown: 20;
    --z-overlay: 50;
    --z-drawer: 60;
    --z-modal: 1000;
    --z-confirm: 1200;
    --z-toast: 9999;

    --glass-border: rgba(255, 255, 255, 0.5);
    --glass-shadow: 0 8px 32px rgba(var(--primary-rgb), 0.1);
    --glass-blur: blur(16px);

    --radius-sm: 7px;
    --radius-md: 10px;
    --radius-lg: 18px;

    --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
    --shadow-md: 0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.03);
    --shadow-lg: 0 10px 28px -8px rgba(15, 23, 42, 0.14), 0 2px 6px rgba(15, 23, 42, 0.05);
    --shadow-xl: 0 20px 48px -12px rgba(15, 23, 42, 0.2), 0 4px 12px rgba(15, 23, 42, 0.06);
    --shadow: var(--shadow-md);
1568:                    <div class="batch-recipient-preview-controls">
1792:                <div class="batch-recipient-preview-controls">
1885:                <div class="task-progress-track"><div class="task-progress-fill" id="batchLogLiveFill"></div></div>
1886:                <div class="batch-log-live-counts" id="batchLogLiveCounts"></div>
1891:            <div id="batchLogMetrics" class="batch-log-metrics"></div>

```

## 旧 UI 契约测试

检索命令：
```bash
rg -n 'refreshRecipientPreview|post\(false\)|post\(true\)|gateAvailable|statistic|统计命中人数|当前条件命中|renderOutcomeMetrics|renderIntegrityWarning|renderExecutionLive' src/test/js/{batchExecutionLogTimeline,batchSendTaskConsoleInteraction,batchSenderFilter,recipientPreviewDiagnostics,batchEmailVerification,batchLogDrawerLayout,batchManualExecutionLog}.test.js; rg -n -F '20261021-reply-identity' src/test
```
回执：
```text
src/test/js/batchExecutionLogTimeline.test.js:38:    vm.runInContext(extractFn("renderIntegrityWarning"), sandbox);
src/test/js/batchExecutionLogTimeline.test.js:177:describe("renderIntegrityWarning (I-4)", () => {
src/test/js/batchExecutionLogTimeline.test.js:181:        sandbox.renderIntegrityWarning(d);
src/test/js/batchExecutionLogTimeline.test.js:185:        sandbox.renderIntegrityWarning(d);
src/test/js/batchExecutionLogTimeline.test.js:191:        sandbox.renderIntegrityWarning({ status: "SUCCESS", target: 0, success: 3, failure: 0, skipped: 0, remaining: 0 });
src/test/js/batchLogDrawerLayout.test.js:167:            renderOutcomeMetrics: () => {},
src/test/js/batchLogDrawerLayout.test.js:168:            renderIntegrityWarning: () => {},
src/test/js/batchLogDrawerLayout.test.js:204:            renderOutcomeMetrics: () => {},
src/test/js/batchLogDrawerLayout.test.js:205:            renderIntegrityWarning: () => {},
src/test/js/recipientPreviewDiagnostics.test.js:45:    "setRecipientPreviewState", "resetRecipientPreview", "scheduleRecipientPreview", "refreshRecipientPreview"];
src/test/js/recipientPreviewDiagnostics.test.js:59:            editor: { available: options.gateAvailable !== false },
src/test/js/recipientPreviewDiagnostics.test.js:60:            manual: { available: options.gateAvailable !== false }
src/test/js/recipientPreviewDiagnostics.test.js:113:        const { sandbox, hint } = previewSandbox({ kind: "editor", gateAvailable: false });
src/test/js/recipientPreviewDiagnostics.test.js:115:        sandbox.refreshRecipientPreview("editor");
src/test/js/recipientPreviewDiagnostics.test.js:142:    it("marks truncated samples and partial statistics explicitly", async () => {
src/test/js/recipientPreviewDiagnostics.test.js:143:        const { sandbox, hint } = previewSandbox({ kind: "manual", gateAvailable: false });
src/test/js/recipientPreviewDiagnostics.test.js:152:        sandbox.refreshRecipientPreview("manual");
src/test/js/recipientPreviewDiagnostics.test.js:157:        assert.ok(hint.innerHTML.includes("统计未完成"), "partial statistics must say 统计未完成");
src/test/js/recipientPreviewDiagnostics.test.js:161:        const { sandbox, hint } = previewSandbox({ kind: "editor", gateAvailable: false });
src/test/js/recipientPreviewDiagnostics.test.js:163:        sandbox.refreshRecipientPreview("editor");
src/test/js/recipientPreviewDiagnostics.test.js:171:        const { sandbox, hint } = previewSandbox({ kind: "manual", gateAvailable: false });
src/test/js/recipientPreviewDiagnostics.test.js:179:        sandbox.refreshRecipientPreview("manual");
src/test/js/recipientPreviewDiagnostics.test.js:190:            const { sandbox, hint } = previewSandbox({ kind, gateAvailable: false });
src/test/js/recipientPreviewDiagnostics.test.js:192:            sandbox.refreshRecipientPreview(kind);
src/test/js/recipientPreviewDiagnostics.test.js:203:            gateAvailable: false,
src/test/js/recipientPreviewDiagnostics.test.js:207:        sandbox.refreshRecipientPreview("editor");
src/test/js/recipientPreviewDiagnostics.test.js:211:        assert.ok(hint.innerHTML.includes("当前条件命中 <strong>6</strong>"), "existing counts must still render");
src/test/js/recipientPreviewDiagnostics.test.js:217:        const { sandbox, hint } = previewSandbox({ kind: "editor", gateAvailable: false });
src/test/js/recipientPreviewDiagnostics.test.js:221:        sandbox.refreshRecipientPreview("editor");
src/test/js/recipientPreviewDiagnostics.test.js:230:        const { sandbox, hint } = previewSandbox({ kind: "manual", gateAvailable: false });
src/test/js/recipientPreviewDiagnostics.test.js:232:        sandbox.refreshRecipientPreview("manual");
src/test/js/recipientPreviewDiagnostics.test.js:240:        const { sandbox, hint } = previewSandbox({ kind: "editor", gateAvailable: false });
src/test/js/recipientPreviewDiagnostics.test.js:244:        sandbox.refreshRecipientPreview("editor");   // seq 1
src/test/js/recipientPreviewDiagnostics.test.js:245:        sandbox.refreshRecipientPreview("editor");   // seq 2
src/test/js/batchEmailVerification.test.js:1009:        "refreshRecipientPreview"
src/test/js/batchEmailVerification.test.js:1132:        vm.runInContext(extractFn("refreshRecipientPreview"), sandbox);
src/test/js/batchEmailVerification.test.js:1134:        sandbox.refreshRecipientPreview("editor");
src/test/js/batchEmailVerification.test.js:1163:        vm.runInContext(extractFn("refreshRecipientPreview"), sandbox);
src/test/js/batchEmailVerification.test.js:1164:        sandbox.refreshRecipientPreview("manual");
src/test/js/batchEmailVerification.test.js:1191:        vm.runInContext(extractFn("refreshRecipientPreview"), sandbox);
src/test/js/batchEmailVerification.test.js:1194:        sandbox.refreshRecipientPreview("editor");
src/test/js/batchEmailVerification.test.js:1201:        sandbox.refreshRecipientPreview("editor");
src/test/js/batchEmailVerification.test.js:1204:        sandbox.refreshRecipientPreview("editor");
src/test/js/batchEmailVerification.test.js:1220:        sandbox.refreshRecipientPreview("editor");
src/test/js/batchEmailVerification.test.js:1244:            ["baseHintHtml", "escapeHtml", "refreshRecipientPreview"]
src/test/js/batchEmailVerification.test.js:1246:            sandbox.refreshRecipientPreview("editor");
src/test/js/batchSendTaskConsoleInteraction.test.js:1166:        const refresh = extractFn("refreshRecipientPreview");
src/test/js/batchSendTaskConsoleInteraction.test.js:1167:        assert.ok(refresh, "refreshRecipientPreview must exist");
src/test/js/batchSendTaskConsoleInteraction.test.js:1185:        sandbox.refreshRecipientPreview("manual");
src/test/js/batchSendTaskConsoleInteraction.test.js:1192:        sandbox.refreshRecipientPreview("manual");
src/test/js/batchSendTaskConsoleInteraction.test.js:1937:        const refresh = extractFn("refreshRecipientPreview");
src/test/js/batchSendTaskConsoleInteraction.test.js:1959:        sandbox.refreshRecipientPreview("manual");
src/test/js/batchSendTaskConsoleInteraction.test.js:1964:        assert.ok(hint.innerHTML.includes("当前条件命中 <strong>8</strong>"),
src/test/js/batchSendTaskConsoleInteraction.test.js:1971:        const refresh = extractFn("refreshRecipientPreview");
src/test/js/batchSendTaskConsoleInteraction.test.js:1998:        sandbox.refreshRecipientPreview("manual");
src/test/js/batchSendTaskConsoleInteraction.test.js:2011:        const refresh = extractFn("refreshRecipientPreview");
src/test/js/batchSendTaskConsoleInteraction.test.js:2038:        sandbox.refreshRecipientPreview("manual");
src/test/js/batchSendTaskConsoleInteraction.test.js:2045:        assert.ok(hint.innerHTML.includes("当前条件命中 <strong>60</strong>"),
src/test/js/batchSendTaskConsoleInteraction.test.js:2051:        const refresh = extractFn("refreshRecipientPreview");
src/test/js/batchSendTaskConsoleInteraction.test.js:2075:        sandbox.refreshRecipientPreview("manual");   // round 1 (seq 1): pending[0] off, pending[1] on
src/test/js/batchSendTaskConsoleInteraction.test.js:2076:        sandbox.refreshRecipientPreview("manual");   // round 2 (seq 2): pending[2] off, pending[3] on
src/test/js/batchSendTaskConsoleInteraction.test.js:2094:        const refresh = extractFn("refreshRecipientPreview");
src/test/js/batchSendTaskConsoleInteraction.test.js:2118:        sandbox.refreshRecipientPreview("manual");

```

## 行号守卫、脚本时间与能力检索

检索命令：
```bash
rg -n 'ExpertSearchService.kt|ExpertContactRepository.kt' src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt; rg -n 'WRITE_DATES_AS_TIMESTAMPS|Cacheable|EnableCaching|CacheManager|Caffeine|starter-cache' src/main pom.xml; rg -n 'now =|classifiedAt' scripts/import_contactout_visible_candidates.py scripts/build_contactout_enterprise_es_import.py scripts/expert_discovery/enterprise_batch/{import_candidate_es,import_personal_email_candidates}.py
```
回执：
```text
98:        NoiseSite("com/weibo/talentintroduction/expert/service/ExpertSearchService.kt", 505, "operatorStatus = source.nullableText"),
105:        NoiseSite("com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt", 47, "operator_status = :operatorStatus"),
scripts/build_contactout_enterprise_es_import.py:200:    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
scripts/build_contactout_enterprise_es_import.py:254:                "classifiedAt": now,
scripts/expert_discovery/enterprise_batch/import_candidate_es.py:94:    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
scripts/expert_discovery/enterprise_batch/import_candidate_es.py:167:                "classifiedAt": now,
scripts/expert_discovery/enterprise_batch/import_personal_email_candidates.py:89:    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
scripts/expert_discovery/enterprise_batch/import_personal_email_candidates.py:157:                "classifiedAt": now,
scripts/import_contactout_visible_candidates.py:119:    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
scripts/import_contactout_visible_candidates.py:181:                "classifiedAt": now,

```

## E-04 ES 7.14 官方能力依据

- [PIT API（7.14）](https://www.elastic.co/guide/en/elasticsearch/reference/7.14/point-in-time-api.html)：查询要使用最新 PIT ID，keep_alive 只覆盖下一次请求间隔；关闭时释放上下文，保留上下文有资源成本。
- [分页（7.14）](https://www.elastic.co/guide/en/elasticsearch/reference/7.14/paginate-search-results.html)：PIT 配合 search_after 避免刷新引起翻页漂移，_shard_doc 提供同一 PIT 内唯一的末尾排序键；from/size 默认有一万窗口约束。

新设计使用上述已存在接口；不改变 ES mapping、不新增持久排序字段。

## E-05 已证实的性能与契约问题

- `ManualInitialOutreachService.kt:970` 执行前调用 `countEsTargets` 全扫描；`:943` 运行期 lazy 首次求值另扫最多20000母集。
- `:1981` 重试先全量 NEW 列表，逐联系人取历史，随后整组 ES 回查。仅删除 countEsTargets 不足以兑现按需。
- `:2235-2320` 材料提醒先 count、全量装入列表并限制10000，再发送；为独立循环，须另改。
- `:1065` 与 `:334` roundQuota 依赖 estimatedRemaining；`:1681` 的 hasNext 在轮次预算判断之前。必须同时改控制流，不能用缓存总数代替实时额度或在额度耗尽后额外取页。
- `OutreachTargetIterator.kt:54-76` 当前 from/size、过滤后重置offset；整页过滤继续推进，不能把过滤空页当源耗尽。
- `BatchExecutionModels.kt:396-445` target固定且终止会把未处理槽位计作跳过；按需模式必须独立计量，保留历史模式。
- `app.js:24136-24139` 当前门禁可用时并行预估 off/on 两次；`:25143` 对目标等式强校验。需为新语义分别修改，不能静默重解释历史记录。
- `MailComposeTemplateService.kt:228-246` 旧versionToken未包含被引用片段及变体正文；缓存使用独立统计指纹，不能只用此token。
- `app.js:24080` 已显示asOf，但它来自当前preflight时刻，不是缓存命中时间。

## E-06 不改动的安全写入边界

候选读取只是读取计划，不授权 SMTP。`ManualInitialOutreachService.kt:1111-1179` 保留抑制、绑定、取消、邮箱验证；`:1385-1450` 联系人创建、发送历史、验证地址一致性、PREPARED 幂等尝试；`ManualOutreachTxHelper.recordSuccess/recordFailure` 负责投递结果落库，`ConversationStateService.transition` 负责会话状态。改取数不得迁移/绕过这些业务写入点。

`TaskProgressStore.kt:204-209` 序列化details并save；`TaskExecutionService.kt:193/272/296/305` 序列化最终result并条件完成；新增execution嵌套对象各占其JSON一个顶层键，不增加DDL列。`BatchSendConfigController.kt:358/383/456/475` 读取历史/运行中结果。通用任务读取不强行改为批量发送专用计量。

## E-07 计划自审补充：严格统计、引用内容与测试设施

- ExpertSearchService.searchByOrcidIds:644-668的size=orcidIds.size且body=null返回空，不保证同orcid多文档时返回全部关联。04的NEW有界关联因此使用terms限定本批的严格游标，不照搬旧非严格接口；不修改其其他消费者。
- ExpertSearchService.scrollExpertsFiltered:749-810在首响应缺body时return、后续缺body时break，未要求完整分片；因此05不能把原countBySnapshot直接包缓存就宣称完整。新严格分支必须使用02带完整响应校验的PIT游标；故障不发布缓存。
- app.js:4701-4734的api已把响应JSON赋给error.data，06可读取503中的旧缓存envelope，不需改全局HTTP合同。
- 内容变体writer为ContentVariantService:68/74/92；QA规则writer为QaRuleManagementService:78/111/131/143。统计fingerprint只新增reader，原投递/匹配/工作台reader不改。
- 已核实pom的mysqlIt属性/Testcontainers依赖及现有真实MySQL测试结构，02不需扩pom。ES真实夹具将用已有GenericContainer基础能力或隔离测试ES，不能指向线上写入。

引用内容读写回执：
```bash
rg -n '(contentVariantRepository|qaRuleRepository|ruleRepository)\.|(FROM|UPDATE|INSERT INTO|DELETE FROM) (content_variant|qa_rule)' src/main/kotlin; rg -n '(save|delete|repository\.)' src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt; rg -n 'class .*Rule|fun (create|update|delete)' src/main/kotlin/com/weibo/talentintroduction/qa
```
```text
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:338:                val rule = block.refId?.let { qaRuleRepository.findById(it).orElse(null) }
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:568:                qaRuleRepository.findById(refId).orElse(null)?.let { rule ->
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:717:                    val rule = qaRuleRepository.findById(refId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:141:            ?.let { qaRuleRepository.findAllById(it).associateBy { rule -> rule.id } }
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:56:        contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(ownerType, ownerId)
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:68:        contentVariantRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId)
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:74:            contentVariantRepository.save(
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:92:        contentVariantRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt:107:            val rule = qaRuleRepository.findById(ruleId).orElse(null) ?: return@mapNotNull null
src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt:227:            qaRuleRepository.findById(ruleId).orElse(null)?.replyPolicyEnum() == QaReplyPolicy.REVIEW
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1181:        val matchableRules = qaRuleRepository.findAllEnabledOrdered()
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1796:                    qaRuleRepository.findById(ruleId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundMailTagService.kt:57:            val rule = qaRuleRepository.findById(ruleId).orElse(null) ?: continue
src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundMailTagService.kt:76:        val rule = qaRuleRepository.findById(qaRuleId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundMailTagService.kt:132:            qaRuleRepository.findAllById(ruleIds).associateBy { requireNotNull(it.id) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundMailTagService.kt:155:            qaRuleRepository.findAllById(ruleIds).associateBy { requireNotNull(it.id) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundMailTagService.kt:190:            qaRuleRepository.findAllById(ruleIds).associateBy { requireNotNull(it.id) }
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:743:            qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:901:            qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:1066:            qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2210:            qaRuleRepository.findById(ruleId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2407:            qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2461:                    qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2527:                qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2553:                qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2671:                qaRuleRepository.findById(ruleId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt:33:        val matchableRules = qaRuleRepository.findAllEnabledOrdered()
src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt:149:            val rule = qaRuleRepository.findById(ruleId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt:214:                val matchableRules = qaRuleRepository.findAllEnabledOrdered()
src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt:469:            val rule = qaRuleRepository.findById(ruleId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/qa/repository/QaRuleRepository.kt:15:        FROM qa_rule
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagKnowledgeBase.kt:127:        val rules = ruleRepository.loadAll()
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt:101:        qaRuleRepository.findAllEnabledOrdered().filter { it.isMatchable() }
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt:136:            val rule = qaRuleRepository.findById(ruleId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:51:            ruleRepository.findAllByOrderByPriorityAscIdAsc()
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:53:            ruleRepository.findAllByCategoryIdOrderByPriorityAscIdAsc(categoryId)
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:78:        val saved = ruleRepository.save(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:93:        val existing = ruleRepository.findById(ruleId)
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:111:        val saved = ruleRepository.save(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:129:        ruleRepository.findById(ruleId).orElseThrow { error("QA rule not found: $ruleId") }
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:131:        ruleRepository.deleteById(ruleId)
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:135:        val existing = ruleRepository.findById(ruleId)
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:143:        val saved = ruleRepository.save(existing.copy(enabled = enabled))
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:150:        ruleRepository.findAllEnabledOrdered().forEach { rule ->
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2597:                qaRuleRepository.findById(ruleId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2684:        qaRuleRepository.findAllEnabledOrdered()
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2783:                qaRuleRepository.findAllById(adjacentIds)
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt:227:            val rule = qaRuleRepository.findById(ruleId).orElse(null) ?: return null
6:import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
68:        contentVariantRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId)
74:            contentVariantRepository.save(
88:    fun deleteForOwner(ownerType: String, ownerId: Long) {
92:        contentVariantRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId)
src/main/kotlin/com/weibo/talentintroduction/qa/domain/QaRule.kt:40:data class QaRule(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaReplyComposer.kt:51:data class QaRuleMatch(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt:217:data class CategoryRulesGroup(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt:225:data class SuggestQaRule(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleAuditService.kt:12:class QaRuleAuditService(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleAuditService.kt:168:data class QaRuleUsageAuditReport(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleAuditService.kt:179:data class RuleUsageCount(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:15:class QaRuleManagementService(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:24:    fun createCategory(command: QaCategoryCreateCommand): QaCategory {
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:67:    fun createRule(command: QaRuleCreateCommand): QaRuleDetail {
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:92:    fun updateRule(ruleId: Long, command: QaRuleUpdateCommand): QaRuleDetail {
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:128:    fun deleteRule(ruleId: Long) {
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:179:data class QaRuleWithCategory(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:185:data class QaRuleDetail(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:197:data class QaRuleCreateCommand(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:224:data class QaRuleUpdateCommand(
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:40:class QaRuleManagementController(
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:106:    fun createCategory(@RequestBody request: QaCategoryCreateRequest): QaCategoryResponse =
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:122:    fun createRule(@RequestBody request: QaRuleCreateRequest): QaRuleResponse =
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:126:    fun updateRule(
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:291:data class QaRuleCreateRequest(
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:323:data class QaRuleUpdateRequest(
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:364:data class QaRuleResponse(
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:401:data class AuthorityRuleResponse(

```

测试设施回执：
```bash
rg -n 'mysqlIt|Testcontainers|testcontainers|MYSQL_IT|@EnabledIf|mysql-it' pom.xml src/test/kotlin/com/weibo/talentintroduction/campaign src/test/kotlin/com/weibo/talentintroduction/task | head -90; sed -n '1,135p' src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt; rg -n 'CREATE TABLE (content_variant|qa_rule)|CREATE TABLE IF NOT EXISTS (content_variant|qa_rule)' src/main/resources/db/migration
```
```text
pom.xml:24:        <mysqlIt>false</mysqlIt>
pom.xml:83:            <groupId>org.testcontainers</groupId>
pom.xml:180:                        <mysqlIt>${mysqlIt}</mysqlIt>
pom.xml:250:            <id>mysql-it</id>
pom.xml:252:                <mysqlIt>true</mysqlIt>
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchRecipientReadRepositoryIT.kt:18:import org.testcontainers.DockerClientFactory
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchRecipientReadRepositoryIT.kt:19:import org.testcontainers.containers.MySQLContainer
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchRecipientReadRepositoryIT.kt:22: * 批量目标只读仓储的真实 MySQL 集成测试（`-DmysqlIt=true`，testcontainers MySQL 8.0.36）。
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchRecipientReadRepositoryIT.kt:32:@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt:49: * [MeetingCalendarControllerMysqlTest]（mysqlIt 门禁）。这两个文件共同承担验收标准
src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt:245: * 01（I-1～I-5）排期接口真实认证 + 真实 MySQL 端到端（mysqlIt 门禁）。
src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt:253:@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt:24:import org.testcontainers.DockerClientFactory
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt:25:import org.testcontainers.containers.MySQLContainer
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt:29: * 验证明细仓储的真实 MySQL 集成测试（`-DmysqlIt=true`，testcontainers MySQL 8.0.36）。
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt:40:@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt:57:import org.testcontainers.DockerClientFactory
src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt:58:import org.testcontainers.containers.MySQLContainer
src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt:66: * Testcontainers 引导与 `DiscoveryPersonRepositoryTest` 同款（`@DataJdbcTest` + `Replace.NONE` +
src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt:67: * `MySQLContainer` + `@DynamicPropertySource` + `-DmysqlIt=true`）。真实表/唯一键/行锁与 CAS SQL，
src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt:70:@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarServiceTest.kt:49: * 邮件与专家行不被改动）由同文件 [MeetingCalendarServiceMysqlTest] 在 -DmysqlIt=true
src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarServiceTest.kt:545: * 01（I-1～I-5）真实 MySQL 语义（mysqlIt 门禁，`-Pmysql-it` / `-DmysqlIt=true`）：
src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarServiceTest.kt:552:@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:29:import org.testcontainers.DockerClientFactory
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:30:import org.testcontainers.containers.MySQLContainer
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:51:@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

interface ExpertContactRepository : CrudRepository<ExpertContact, Long> {
    fun existsByCampaignIdAndOrcidId(campaignId: Long, orcidId: String): Boolean
    fun existsByOrcidId(orcidId: String): Boolean
    fun findAllByCurrentStatus(currentStatus: String): List<ExpertContact>
    fun findByCampaignIdAndOrcidId(campaignId: Long, orcidId: String): ExpertContact?

    fun findFirstByExpertEmailOrderByUpdatedAtDesc(expertEmail: String): ExpertContact?

    fun findFirstByOrcidIdOrderByUpdatedAtDesc(orcidId: String): ExpertContact?

    fun findByOrcidIdIn(orcidIds: List<String>): List<ExpertContact>

    fun findAllByOrderByUpdatedAtDesc(): List<ExpertContact>

    fun findAllByCurrentStatusOrderByUpdatedAtDesc(currentStatus: String): List<ExpertContact>

    fun findAllByCampaignIdOrderByUpdatedAtDesc(campaignId: Long): List<ExpertContact>

    fun findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(
        campaignId: Long,
        currentStatus: String
    ): List<ExpertContact>

    fun findAllByExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(
        expertName: String,
        expertEmail: String
    ): List<ExpertContact>

    fun findAllByOrcidIdContainingIgnoreCaseOrExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(
        orcidId: String,
        expertName: String,
        expertEmail: String
    ): List<ExpertContact>

    @Query("""
        SELECT * FROM expert_contact
        WHERE (:campaignId IS NULL OR campaign_id = :campaignId)
          AND (:status IS NULL OR current_status = :status)
          AND (:operatorStatus IS NULL OR operator_status = :operatorStatus)
          AND (:needsAttention IS NULL OR needs_manual_attention = :needsAttention)
          AND (:replyMode IS NULL
               OR (:replyMode = 'MANUAL' AND (auto_reply_enabled = false OR current_status = 'MANUAL_HANDOFF'))
               OR (:replyMode = 'AUTO'   AND auto_reply_enabled = true AND current_status <> 'MANUAL_HANDOFF'))
          AND (:followUpMarked IS NULL OR follow_up_marked = :followUpMarked)
        ORDER BY
          CASE WHEN :followUpMarked = true THEN follow_up_marked_at END DESC,
          updated_at DESC
    """)
    fun findFilteredContacts(
        campaignId: Long?,
        status: String?,
        operatorStatus: String?,
        needsAttention: Boolean?,
        replyMode: String? = null,
        followUpMarked: Boolean? = null
    ): List<ExpertContact>

    @Modifying
    @Query("UPDATE expert_contact SET country = :country WHERE id = :id")
    fun updateCountryById(id: Long, country: String?): Int

    @Modifying
    @Query("""
        UPDATE expert_contact
           SET bound_sender_account_code = :accountCode,
               sender_account_bound_at = :boundAt
         WHERE id = :id
    """)
    fun updateBindingById(id: Long, accountCode: String?, boundAt: LocalDateTime?): Int

    @Modifying
    @Query("""
        UPDATE expert_contact
           SET bound_sender_account_code = :accountCode,
               sender_account_bound_at = :changedAt,
               sender_account_changed = true,
               sender_account_changed_at = :changedAt
         WHERE id = :id
    """)
    fun rebindSenderAccountById(id: Long, accountCode: String, changedAt: LocalDateTime): Int

    @Modifying
    @Query("""
        UPDATE expert_contact
           SET bound_sender_account_code = :toAccountCode,
               sender_account_bound_at = :migratedAt
         WHERE bound_sender_account_code = :fromAccountCode
    """)
    fun migrateBindingByAccount(
        fromAccountCode: String,
        toAccountCode: String,
        migratedAt: LocalDateTime
    ): Int

    @Modifying
    @Query("""
        UPDATE expert_contact
           SET sender_account_changed = false,
               sender_account_changed_at = NULL
         WHERE id = :id
    """)
    fun clearSenderChangeMarkById(id: Long): Int

    fun findAllByBoundSenderAccountCode(boundSenderAccountCode: String): List<ExpertContact>

    @Query("""
        SELECT bound_sender_account_code AS account_code, COUNT(*) AS bound_count
          FROM expert_contact
         WHERE bound_sender_account_code IS NOT NULL
           AND bound_sender_account_code <> ''
           AND bound_sender_account_code <> 'SIMULATOR_NOOP'
         GROUP BY bound_sender_account_code
    """)
    fun countBindingsByAccount(): List<AccountBindingCount>

    @Query("""
        SELECT bound_sender_account_code AS account_code,
               COALESCE(LOWER(TRIM(country)), '') AS distribution_key,
               COUNT(*) AS bound_count
          FROM expert_contact
         WHERE bound_sender_account_code IS NOT NULL
           AND bound_sender_account_code <> ''
           AND bound_sender_account_code <> 'SIMULATOR_NOOP'
         GROUP BY bound_sender_account_code, COALESCE(LOWER(TRIM(country)), '')
    """)
    fun countBindingsByAccountAndCountry(): List<AccountCountryBindingCount>
}
src/main/resources/db/migration/V67__create_content_variant.sql:1:CREATE TABLE content_variant (
src/main/resources/db/migration/V1__create_business_tables.sql:49:CREATE TABLE qa_rule (

```


