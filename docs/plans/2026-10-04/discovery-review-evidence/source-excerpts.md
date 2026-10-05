# 代码证据摘录
基线 HEAD：e28e53fd898edd62905a0d45a6bf90396b18b1bf；本地工作树 2026-10-04。不是线上部署确认。
行号来自当前文件；执行前以方法名复核，不能仅按行号改代码。

## identity

[expert/domain/DiscoveryIdentity.kt:29](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt:29)
```kotlin
29:         .joinToString("") { "%02x".format(it) }
30:     fun isDiscovery(profile: ExpertProfile) = profile.identityVerification != null ||
31:         profile.emailSource in sources || profile.tags.orEmpty().contains("discovered")
32:     fun validEvidence(evidence: String?): Boolean = evidence != null &&
33:         evidence.matches(Regex("(?:JATS_SHA256|ORCID_RECORD_SHA256|SOURCE_SHA256):[0-9a-f]{64}"))
34:     fun verified(email: String, given: String?, family: String?, evidence: String, orcid: String?, authorId: String?) =
35:         IdentityVerification("VERIFIED", VERSION, normalizedEmail(email), given, family,
36:             evidence.substringBefore(':'), evidence.substringAfter(':'), orcid, authorId)
37: 
38:     fun isDiscoveryMap(source: Map<String, Any?>): Boolean = source["identityVerification"] != null ||
39:         source["emailSource"] in sources || (source["tags"] as? Collection<*>)?.contains("discovered") == true
40: 
41:     fun isDiscoverySource(source: JsonNode): Boolean =
42:         (!source.path("identityVerification").isMissingNode && !source.path("identityVerification").isNull) ||
43:             source.path("emailSource").asText(null) in sources ||
44:             source.path("tags").takeIf { it.isArray }?.any { it.asText() == "discovered" } == true
45: 
46:     /** Source-verified identity; a legacy outreach approval cannot authorize academic author binding. */
47:     fun allowed(profile: ExpertProfile): Boolean {
48:         if (!isDiscovery(profile)) return true
49:         val proof = profile.identityVerification ?: return false
50:         return proof.status == "VERIFIED" && proof.version == VERSION &&
51:             normalizedEmail(proof.email).isNotEmpty() && normalizedEmail(proof.email) == normalizedEmail(profile.email) &&
52:             !proof.givenNames.isNullOrBlank() && !proof.familyNames.isNullOrBlank() &&
```

[expert/domain/DiscoveryIdentity.kt:74](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt:74)
```kotlin
74: 
75:     fun legacyApprovalDigest(profile: ExpertProfile): String = hash(mapper.writeValueAsString(listOf(
76:         LEGACY_APPROVAL_SOURCE, profile.orcidId, normalizedEmail(profile.email),
77:         profile.givenNames, profile.familyNames, profile.institution, profile.country, profile.institutionType
78:     )))
79: 
80:     fun legacyOutreachApproved(profile: ExpertProfile): Boolean {
81:         val proof = profile.identityVerification ?: return false
82:         return proof.status == "LEGACY_APPROVED" && proof.version == VERSION &&
83:             proof.source == LEGACY_APPROVAL_SOURCE && normalizedEmail(profile.email).isNotEmpty() &&
84:             proof.email == normalizedEmail(profile.email) &&
85:             proof.givenNames == profile.givenNames && proof.familyNames == profile.familyNames &&
86:             proof.evidenceHash == legacyApprovalDigest(profile)
87:     }
88: 
89:     // ── 02（I-1/I-2）：机构来源证据 token ──────────────────────────────────────
90:     // 一个字段把「已存身份 + 来源种类 + 显示机构」绑成可重算的校验值：签发与验签共用下面
91:     // 同一份 NUL 分隔、固定顺序的输入；机构/国家/类型或身份 ID 一变，旧 token 立即失效。
```

[expert/domain/DiscoveryIdentity.kt:110](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt:110)
```kotlin
110:     fun institutionEvidence(profile: ExpertProfile, source: String): String? {
111:         if (source !in EVIDENCE_SOURCES) return null
112:         if (!allowed(profile)) return null
113:         val proof = profile.identityVerification ?: return null
114:         val ids = parsedExternalIds(profile.externalIds)
115:         if (!consistentIdentityIds(profile, proof, ids)) return null
116:         val institution = profile.institution?.takeIf { it.isNotBlank() } ?: return null
117:         // JATS 必须有 externalIds.pmcId；OPENALEX 必须有同作者 openAlexAuthorId（且 doi/pmcId 至少一项）；
118:         // ORCID 必须有 externalIds.orcid。
119:         val sourceAuthorId = when (source) {
120:             EVIDENCE_SOURCE_OPENALEX -> ids["openAlexAuthorId"]?.takeIf { it.isNotBlank() } ?: return null
121:             EVIDENCE_SOURCE_ORCID -> ids["orcid"]?.takeIf { it.isNotBlank() } ?: return null
122:             else -> ids["orcid"].orEmpty()
123:         }
124:         if (source == EVIDENCE_SOURCE_JATS && ids["pmcId"].isNullOrBlank()) return null
125:         if (source == EVIDENCE_SOURCE_OPENALEX && ids["doi"].isNullOrBlank() && ids["pmcId"].isNullOrBlank()) return null
126:         return "$source:${hash(tokenDigestInput(source, profile, proof, ids, sourceAuthorId, institution).joinToString("\u0000"))}"
127:     }
128: 
129:     /**
130:      * 唯一验签函数（02）：**只读已存字段重算**（来源种类取自 token 前缀，不依赖任何本次审计备份）。
131:      * 旧文档没有该键 → false；机构/国家/类型或身份 ID 与签发时不一致 → false。
132:      */
133:     fun validInstitutionEvidence(profile: ExpertProfile): Boolean {
134:         val token = profile.institutionEvidence ?: return false
135:         val source = token.substringBefore(':')
136:         if (source !in EVIDENCE_SOURCES) return false
137:         return token == institutionEvidence(profile, source)
138:     }
```

## batch-scope

[campaign/domain/BatchExecutionModels.kt:211](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:211)
```kotlin
211:      * I-1/I-2/I-3: ES 候选页与预估共用的最终谓词（执行取页、预估 scroll、发前兜底）。
212:      *
213:      * 新发现/待确认档案额外按**已证实机构所在地** `country` 判地区 —— ES 的 `regionFilter`
214:      * 只是 country OR nationality 的粗筛，不能让它把国籍当成所在地。非新发现档案在此不收紧
215:      * （既有 ES 粗筛语义逐字保留）。
216:      */
217:     fun matchesEsTarget(profile: ExpertProfile): Boolean {
218:         if (!matchesDiscoveryOutreach(profile)) return false
219:         if (!isDiscoveryOutreach(profile)) return true
220:         return regions.isEmpty() ||
221:             CountryContinentMapping.toRegion(profile.country) in regions
222:     }
223: 
224:     /**
225:      * I4-4: 研发类型判定的**唯一** Kotlin 实现，由 [matchesExpert] 与
226:      * ManualInitialOutreachService 的发送前门禁共同调用，禁止再复刻第二份。
227:      * I4-5: 与 ES 的 expertTypePredicate 同口径 —— `UNCLASSIFIED` = 类型为 null。
228:      * I4-2: 空集合返回 false（fail-closed）。
229:      */
230:     fun matchesExpertType(profile: ExpertProfile): Boolean {
231:         val typeName = profile.expertClassification?.type?.name
232:         return expertTypes.any { if (it == "UNCLASSIFIED") typeName == null else typeName == it }
233:     }
234: 
235:     companion object {
236:         /**
237:          * I-1: 触发新发现首发门禁的档案标签。与 [DiscoveryIdentity.isDiscovery] 取并集 ——
238:          * 由 `discovered` 转为 `待确认` 的存量人群没有身份对象，也必须 fail-closed。
239:          */
240:         const val DISCOVERY_PENDING_TAG = "待确认"
241: 
242:         /** I-3: ES `filterResult` 的合格值（写入侧同值：`PASSED` / `REJECTED`）。 */
243:         private const val FILTER_RESULT_PASSED = "PASSED"
244: 
245:         /**
246:          * I-1: 该档案是否按新发现规则发送 —— 身份凭证（[DiscoveryIdentity.isDiscovery]）
247:          * 或 `待确认` 标签任一命中。**不改变** [DiscoveryIdentity.isDiscovery] 既有语义。
248:          */
249:         fun isDiscoveryOutreach(profile: ExpertProfile): Boolean =
250:             DiscoveryIdentity.isDiscovery(profile) || profile.tags.orEmpty().contains(DISCOVERY_PENDING_TAG)
251: 
252:         /**
253:          * I-1/I-2: 新发现首发的唯一最终谓词（ES 候选页、预估、NEW 重试与旧首发共用）。
254:          *
255:          * 必须同时成立：身份凭证经 [DiscoveryIdentity.allowed]、`institution` 非空、
256:          * `country` 能由 [CountryContinentMapping] 映射（空/未映射值不等于 Other）、
257:          * `institutionEvidence` 经 02 的唯一验签函数重算通过（含来源 ID 一致性）、
258:          * `filterResult == PASSED`。显式历史人工认可可替代身份及机构来源凭证，
259:          * 但不替代机构、国家、PASSED 条件。未获认可的缺字段不通过；非新发现档案一律放行。
260:          */
261:         fun matchesDiscoveryOutreach(profile: ExpertProfile): Boolean {
262:             if (!isDiscoveryOutreach(profile)) return true
263:             val legacyApproved = DiscoveryIdentity.legacyOutreachApproved(profile)
264:             if (!legacyApproved && !DiscoveryIdentity.allowed(profile)) return false
265:             if (profile.institution.isNullOrBlank()) return false
266:             if (profile.country.isNullOrBlank()) return false
267:             if (CountryContinentMapping.toRegion(profile.country) == CountryContinentMapping.REGION_OTHER) return false
268:             if (profile.filterResult != FILTER_RESULT_PASSED) return false
269:             return legacyApproved || DiscoveryIdentity.validInstitutionEvidence(profile)
270:         }
271: 
```

## candidate

[expert/service/CandidateEligibilityService.kt:21](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityService.kt:21)
```kotlin
21:     fun evaluateEligibility(expert: ExpertProfile): EligibilityResult {
22:         val properties = eligibilityFilterService.getCandidateFilter()
23:         val academicProperties = eligibilityFilterService.getAcademicFilter()
24:         val reasons = mutableListOf<String>()
25: 
26:         if (properties.requireOrcid && expert.orcidId.isBlank())
27:             reasons += "MISSING_ORCID"
28: 
29:         if (properties.requireValidEmail && !hasValidEmail(expert.email))
30:             reasons += "INVALID_EMAIL_FORMAT"
31: 
32:         if (properties.requireValidEmail && expert.email != null && emailValidationService.isDisposableEmail(expert.email))
33:             reasons += "DISPOSABLE_EMAIL"
34: 
35:         if (properties.requireDoctoralDegree && !hasDoctoralDegree(expert.degree))
36:             reasons += "NO_DOCTORAL_DEGREE"
37: 
38:         if (properties.enableAgeFilter && !isUnderMaxAge(expert.age, properties.maxAgeExclusive))
39:             reasons += "AGE_EXCEEDED"
40: 
41:         if (properties.excludeChineseNationality && !isNotChineseNationality(nationalityOf(expert)))
42:             reasons += "CHINESE_NATIONALITY"
43: 
44:         if (academicProperties.enableHIndexFilter && (expert.hIndex ?: 0) < academicProperties.minHIndex)
45:             reasons += "H_INDEX_TOO_LOW"
46: 
47:         if (academicProperties.enableCitationFilter && (expert.citationCount ?: 0) < academicProperties.minCitationCount)
48:             reasons += "CITATION_COUNT_TOO_LOW"
49: 
50:         if (academicProperties.enableActivityFilter) {
51:             val cutoff = Year.now().value - academicProperties.recentYearsThreshold
52:             if ((expert.lastPublicationYear ?: 0) < cutoff)
53:                 reasons += "INACTIVE"
54:         }
55:         if (DiscoveryIdentity.isDiscovery(expert)) {
56:             val classification = classificationService.classify(expert)
57:             val professionalReason = when (classification.type) {
58:                 ExpertType.PRODUCTION_RND, ExpertType.ACADEMIC_RND, ExpertType.HYBRID_RND -> null
59:                 ExpertType.UNKNOWN ->
60:                     if ("RND_SCOPE_UNCONFIRMED" in classification.negativeEvidence) "RND_SCOPE_UNCONFIRMED"
61:                     else "RND_EVIDENCE_INSUFFICIENT"
62:                 ExpertType.OUT_OF_SCOPE -> "RND_OUT_OF_SCOPE"
63:                 ExpertType.SERVICE_ONLY -> "RND_SERVICE_ONLY"
64:             }
65:             if (professionalReason != null) reasons += professionalReason
66:         }
67: 
68:         return EligibilityResult(reasons.isEmpty(), reasons)
69:     }
70: 
```

## batch-runtime

[campaign/service/ManualInitialOutreachService.kt:474](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:474)
```kotlin
474:     private fun resolveScope(snapshot: BatchExecutionSnapshot): RecipientScope {
475:         val base = RecipientScope.fromSnapshot(snapshot)
476:         if (!snapshot.gateFilterEnabled) return base
477:         val templateId = snapshot.templateId ?: return base
478:         val required = mailComposeTemplateService.requiredEsFields(templateId)
479:         if (required.isEmpty()) return base
480:         val usable = required.filter { it in ExpertSearchService.ALLOWED_HAS_FIELDS }
481:         val dropped = required - usable.toSet()
482:         if (dropped.isNotEmpty()) {
483:             log.info(
484:                 "Gate filter: {} of template {} cannot be pre-filtered (not in ALLOWED_HAS_FIELDS), dropped: {}",
485:                 dropped.size, templateId, dropped
486:             )
487:         }
488:         return base.copy(gateEsFields = usable)
489:     }
490: 
491:     /**
```

[campaign/service/ManualInitialOutreachService.kt:705](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:705)
```kotlin
705:                         "研发类型不在本次选择范围内：${expert.orcidId}"
706:                     )
707:                     processedTotal++
708:                     roundProcessed++
709:                     roundRejected++
710:                     updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
711:                         "RUNNING", "研发类型不在本次选择范围内：${expert.orcidId}", errors, mode, roundNumber, config, runAccountStats,
712:                         roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
713:                     continue
714:                 }
715: 
716:                 // I-1/I-3: 发送前最后门禁 —— 与 ES 取页、预估共用同一最终谓词（[RecipientScope.matchesEsTarget]）。
717:                 // 目标构造与取页已按该谓词过滤，这里兜住任何绕过查询侧的残余路径：不建联系人、不占名额、不发邮件。
718:                 if (!scope.matchesEsTarget(expert)) {
719:                     accumulator.recordSkipped(
720:                         BatchOutcomeReasonCodes.DISCOVERY_EVIDENCE_MISSING,
721:                         "新发现机构证据不足：${expert.orcidId}"
722:                     )
723:                     processedTotal++
724:                     roundProcessed++
725:                     roundRejected++
726:                     updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
727:                         "RUNNING", "已跳过机构证据不足的新发现：${expert.orcidId}", errors, mode, roundNumber, config, runAccountStats,
728:                         roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
729:                     continue
730:                 }
731: 
732:                 val email = expert.email
733:                 if (email.isNullOrBlank() || emailSuppressionService.isSuppressed(email)) {
734:                     accumulator.recordSkipped(BatchOutcomeReasonCodes.SUPPRESSED, "已跳过抑制邮箱：${email ?: ""}")
735:                     processedTotal++
736:                     roundProcessed++
737:                     roundRejected++
738:                     updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
739:                         "RUNNING", "已跳过抑制邮箱：${email ?: ""}", errors, mode, roundNumber, config, runAccountStats,
740:                         roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
741:                     continue
742:                 }
743: 
744:                 // I-3: 任一 expert_contact 行已有绑定（与绑定值是否在选中集合无关）→ 本次批量不发信、
745:                 // 不重选号、不改绑。NEW 重试在目标构造时已过滤；ES 页在此发送前对同一 ORCID 重查。
746:                 if (existingContact?.boundSenderAccountCode != null || hasBoundContact(normOrcid)) {
747:                     accumulator.recordSkipped(
748:                         BatchOutcomeReasonCodes.BOUND_SENDER_ALREADY_SET,
749:                         "专家已绑定发件账号：${expert.orcidId}"
750:                     )
751:                     processedTotal++
752:                     roundProcessed++
753:                     roundRejected++
754:                     updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
755:                         "RUNNING", "已跳过已绑定发件账号：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
756:                         roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
757:                     continue
758:                 }
```

[campaign/service/ManualInitialOutreachService.kt:895](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:895)
```kotlin
895:                 // ES 页重新出现的同一 ORCID 走同一事实（按 ORCID 读既有行），不会因重试集合排除而绕过。
896:                 // 跳过不新建/绑定 contact、不更新发送尝试、不调 SMTP、不计本次 failed/sent、不占额度。
897:                 if (hasPermanentFirstMailFailure(introductionHistoryContactIds(normOrcid, existingContact))) {
898:                     log.info("Permanent introduction failure already recorded for ORCID {}, blocking automatic resend", normOrcid)
899:                     accumulator.recordSkipped(
900:                         BatchOutcomeReasonCodes.SEND_EXCEPTION,
901:                         "历史首封永久失败，需人工处理：${expert.email}"
902:                     )
903:                     processedTotal++
904:                     roundProcessed++
905:                     // I-6：已 PASS 验证的目标在此跳过时保留 NOT_SENT + 具体原因。
906:                     recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.SEND_EXCEPTION)
907:                     continue
908:                 }
909:                 // I-6：区分「SMTP 前失败（NOT_SENT）」与「SMTP 结果不明（保持 SENDING）」。
910:                 var smtpAttempted = false
```

[campaign/service/ManualInitialOutreachService.kt:929](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:929)
```kotlin
929:                     }
930: 
931:                     // 2. Double-check: skip if SENT introduction already exists (anti-duplicate, I-7)
932:                     if (hasSentIntroduction(contact.id!!)) {
933:                         log.info("SENT introduction already exists for contact {}, skipping", contact.id)
934:                         accumulator.recordSkipped(BatchOutcomeReasonCodes.DEDUP)
935:                         processedTotal++
936:                         roundProcessed++
937:                         // I-6：PASS 后未进 SMTP 的分支保留 NOT_SENT + 具体原因。
938:                         recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.DEDUP)
939:                         continue
940:                     }
941: 
942:                     val messageId = "<manual-outreach-${normOrcid}-${UUID.randomUUID()}@weibo.com>"
943:                     val mail = try {
944:                         introductionMailComposer.compose(account.accountCode, expert, config.templateId)
945:                             .copy(messageId = messageId)
946:                     } catch (e: PersonalizationGateException) {
947:                         log.info("Personalization gate blocked ORCID {}: missing keys {}", normOrcid, e.missingKeys)
948:                         accumulator.recordSkipped(
949:                             BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE,
950:                             "个性化字段缺失（${e.missingKeys.joinToString(",")}）：${expert.email}"
951:                         )
952:                         roundRejected++
953:                         processedTotal++
954:                         roundProcessed++
955:                         recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE)
956:                         updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
957:                             "RUNNING", "个性化字段缺失：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
958:                             roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
959:                         continue
960:                     } catch (e: Exception) {
```

[campaign/service/ManualInitialOutreachService.kt:1460](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1460)
```kotlin
1460:         val seenOrcids = mutableSetOf<String>()
1461:         val targets = mutableListOf<Pair<ExpertContact?, ExpertProfile>>()
1462: 
1463:         val newContacts = expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
1464:         if (newContacts.isNotEmpty()) {
1465:             val retryableContacts = newContacts.filter { contact ->
1466:                 // I-4（03）：同一份邮件列表同时服务「已发首封」去重与「历史首封永久失败」阻断
1467:                 // （复用现有查询，不新增每行第二次读取）——永久失败不自动重发，不依赖 EMAIL_INVALID 副作用。
1468:                 val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)
1469:                 val hasSent = records.any {
1470:                     it.direction == "OUTBOUND" && it.mailType == "INTRODUCTION" && it.sendStatus == "SENT"
1471:                 }
1472:                 !hasSent && !records.any { it.isPermanentIntroductionFailure() } &&
1473:                     contact.operatorStatus != "EMAIL_INVALID"
1474:             }
1475:             val orcidIds = retryableContacts.map { it.orcidId }
1476:             // I-3/I-4: 预估与执行共用本构造函数 —— 同一 ORCID 的任一 campaign 行已绑定即排除。
1477:             val boundOrcids = boundOrcidsOf(orcidIds)
1478:             val profilesByLevel = if (orcidIds.isEmpty()) {
1479:                 emptyMap()
1480:             } else {
1481:                 scope.funnelLevels.associateWith { level ->
1482:                     expertSearchService.searchByOrcidIds(orcidIds, ExpertIndexLevel.valueOf(level))
1483:                         .associateBy { normalizeOrcid(it.orcidId) }
1484:                 }
1485:             }
1486:             for (contact in retryableContacts) {
1487:                 val normOrcid = normalizeOrcid(contact.orcidId)
1488:                 if (normOrcid in boundOrcids) continue
1489:                 val profile = scope.funnelLevels.asSequence()
1490:                     .mapNotNull { level -> profilesByLevel[level]?.get(normOrcid) }
1491:                     .firstOrNull() ?: continue
1492:                 if (!scope.matchesExpert(profile)) continue
1493:                 if (seenOrcids.add(normOrcid)) {
1494:                     targets.add(Pair(contact, profile))
1495:                 }
1496:             }
1497:         }
1498: 
```

[campaign/service/ManualInitialOutreachService.kt:1718](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1718)
```kotlin
1718: 
1719:         for ((normOrcid, expert) in normalizedExperts) {
1720:             if (normOrcid in boundOrcids) continue             // exclude: already bound to a sender account (I-3)
1721:             val contact = contactByNormOrcid[normOrcid] ?: continue  // exclude: no existing contact
1722:             val contactId = contact.id ?: continue
1723:             if (!seenContactIds.add(contactId)) continue              // dedup by contactId
1724:             val email = contact.expertEmail
1725:             if (email.isBlank()) continue                             // exclude: empty email
1726:             if (emailSuppressionService.isSuppressed(email)) continue  // exclude: suppressed
1727:             if (hasSentMaterialReminder(contactId)) continue          // exclude: already SENT (I-6)
1728:             sendableTargets.add(Pair(contact, expert))
1729:         }
1730: 
```

[campaign/service/ManualInitialOutreachService.kt:1839](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1839)
```kotlin
1839:     private fun buildEsFiltersForLevel(scope: RecipientScope, level: String): List<Map<String, Any>> {
1840:         // I3a-4: 判据从「等于 NOT_CONTACTED」变为「是否含非 NOT_CONTACTED 值」。
1841:         // 空集合 或 仅含 NOT_CONTACTED  → 保持 notContacted 基座（N3a-2 逐字不变）。
1842:         val statuses = scope.operatorStatuses
1843:         val onlyNotContacted = statuses.isEmpty() || statuses.all { it == "NOT_CONTACTED" }
1844:         val filters = if (scope.mailType == BatchSendType.INTRODUCTION.name && level == "CANDIDATE" && onlyNotContacted) {
1845:             ExpertSearchService.notContactedWithEmailDomainsFilters(scope.emailDomains, scope.discipline).toMutableList()
1846:         } else {
1847:             // I3a-4: 含任一非 NOT_CONTACTED 状态时必须换成状态无关基座 —— notContacted 基座
1848:             // 自带 must_not exists operatorStatus，与 term 状态并存恒为空。
1849:             val base = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
1850:             ExpertSearchService.emailDomainsFilter(scope.emailDomains)?.let { base.add(it) }
1851:             scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }
1852:             // I3a-3: 空集合返回 null，不追加任何状态 filter。
1853:             ExpertSearchService.operatorStatusesFilter(statuses)?.let { base.add(it) }
1854:             base
1855:         }
1856:         if (scope.tags.isNotEmpty()) {
1857:             filters.add(mapOf("terms" to mapOf("tags" to scope.tags)))
1858:         }
1859:         ExpertSearchService.regionsFilter(scope.regions)?.let { filters.add(it) }
1860:         // I-2: 方向三态 —— ABSENT 是既有 PRESENT 存在性 filter 的 bool.must_not；
1861:         // ANY 不追加任何项（旧任务人群不变）。与模板门禁字段/研发类型平铺为 AND（I-3）。
1862:         when (scope.researchDirectionFilter) {
1863:             ResearchDirectionFilters.PRESENT ->
1864:                 filters.add(ExpertSearchService.fieldPresenceFilter(ResearchDirectionFilters.ES_FIELD))
1865:             ResearchDirectionFilters.ABSENT ->
1866:                 filters.add(
1867:                     mapOf(
1868:                         "bool" to mapOf(
1869:                             "must_not" to listOf(
1870:                                 ExpertSearchService.fieldPresenceFilter(ResearchDirectionFilters.ES_FIELD)
1871:                             )
1872:                         )
1873:                     )
1874:                 )
1875:         }
1876:         // I4a-2: 门禁字段之间 AND —— 平铺进 filter 数组，不用 should。
1877:         // I4a-1: 空集合时 fieldPresenceFilters 返回空列表，不追加任何项。
1878:         filters.addAll(ExpertSearchService.fieldPresenceFilters(scope.gateEsFields))
1879:         // I4-1: INTRODUCTION 的唯一收口点 —— 只按研发类型集合判定，无第二个门禁。
1880:         // I4-2: 空集合 = 发给零个人（fail-closed），不是"不限"。
1881:         if (scope.mailType == BatchSendType.INTRODUCTION.name) {
1882:             filters.add(
1883:                 ExpertSearchService.expertTypesFilter(scope.expertTypes)
1884:                     ?: ExpertSearchService.MATCH_NONE_FILTER
1885:             )
1886:         }
1887:         return filters
```

## composer

[mail/service/IntroductionMailComposer.kt:17](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:17)
```kotlin
17:     fun compose(accountCode: String, expert: ExpertProfile, templateId: Long? = null): ComposedMail {
18:         val account = mailSenderAccountService.getEnabledAccount(accountCode)
19:         val variables = buildVariables(account, expert)
20:         val variantSeed = expert.orcidId.hashCode()
21:         val rendered = if (templateId != null) {
22:             mailComposeTemplateService.render(templateId, variables, variantSeed)
23:         } else {
24:             mailComposeTemplateService.renderByCode(templateCode = "INTRODUCTION", variables = variables, variantSeed = variantSeed)
25:         }
26: 
27:         val gateTemplateId = templateId ?: rendered.templateId
28:         val requiredKeys = gateTemplateId?.let { mailComposeTemplateService.effectiveRequiredKeys(it) }.orEmpty()
29:         val gate = personalizationGateService.evaluate(rendered.rawTexts, variables, requiredKeys)
30:         if (gate.blocked) {
31:             throw PersonalizationGateException(gate.missingKeys)
32:         }
33: 
34:         val domain = account.senderEmail.substringAfter("@")
35:         val messageId = "<intro-${expert.orcidId}-${UUID.randomUUID()}@$domain>"
36: 
37:         val plain = rendered.body
38:         val mail = ComposedMail(
39:             to = expert.email ?: error("Expert email is required for introduction mail"),
40:             subject = rendered.subject,
41:             body = mailContentService.plainTextToHtml(plain, listOfNotNull(variables["unsubscribeUrl"])),
42:             html = true,
43:             text = plain,
44:             messageId = messageId
45:         )
46:         personalizationGateService.requireNoPlaceholderResidue(mail.subject, plain)
47:         return mail
```

## revalidate

[expert/service/ExpertRevalidationService.kt:325](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:325)
```kotlin
325:     fun revalidateDiscovery(docId: String): PromotionOutcome {
326:         val applied = try {
327:             expertIndexWriterService.documentExistsInIndex(ExpertIndexLevel.APPLICATION, docId)
328:         } catch (e: Exception) {
329:             return PromotionOutcome.ExistenceCheckFailed
330:         }
331:         return try {
332:             val snapshot = expertIndexWriterService.readDiscoveryDocument(ExpertIndexLevel.RAW, docId)
333:                 ?: return PromotionOutcome.RawMissing
334:             if (!DiscoveryIdentity.allowedMap(snapshot.source))
335:                 return PromotionOutcome.WriteFailed
336:             val profile = expertIndexWriterService.discoveryProfile(docId, snapshot.source)
337:             val candidateBefore = if (applied) null
338:                 else expertIndexWriterService.readDiscoveryDocument(ExpertIndexLevel.CANDIDATE, docId)
339:             val eligibility = eligibilityService.evaluateEligibility(profile)
340:             val reasons = if (eligibilityFilterService.getCandidateFilter().requireValidEmail) {
341:                 val emailResult = emailValidationService.validate(profile.email.orEmpty())
342:                 if (emailResult.valid) eligibility.rejectReasons
343:                 else eligibility.rejectReasons + "EMAIL:${emailResult.rejectReason}"
344:             } else eligibility.rejectReasons
345:             val classification = expertClassificationService.classify(profile)
346:             if (!expertIndexWriterService.reconcileDiscoveryCandidate(
347:                     docId, snapshot, classification, reasons, preserveApplication = applied
348:                 )) return PromotionOutcome.WriteFailed
349:             if (applied) PromotionOutcome.AlreadyPresent
350:             else if (reasons.isNotEmpty()) PromotionOutcome.Rejected(reasons)
351:             else if (candidateBefore != null) PromotionOutcome.AlreadyPresent
352:             else PromotionOutcome.Promoted
353:         } catch (e: Exception) {
354:             log.warn("Discovery revalidation failed for {}: {}", docId, e.message)
355:             PromotionOutcome.WriteFailed
356:         }
357:     }
358: 
359:     /**
360:      * Single-RAW revalidation: discovery follows the CAS qualification/replica path, while
361:      * non-discovery retains the historical promotion behavior and outcome vocabulary.
```

## writers

[expert/service/ExpertIndexWriterService.kt:715](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:715)
```kotlin
715:     fun discoveryProfile(docId: String, source: Map<String, Any?>): com.weibo.talentintroduction.expert.domain.ExpertProfile {
716:         fun text(key: String) = source[key] as? String
717:         fun number(key: String) = (source[key] as? Number)?.toInt()
718:         fun strings(key: String) = (source[key] as? List<*>)?.filterIsInstance<String>()
719:         return com.weibo.talentintroduction.expert.domain.ExpertProfile(
720:             esDocId = docId, orcidId = text("orcidId").orEmpty(), email = text("email"),
721:             givenNames = text("givenNames"), familyNames = text("familyNames"), country = text("country"),
722:             keyword = text("keyword"), employment = text("employment"), age = number("age"),
723:             degree = text("degree"), nationality = text("nationality"), hIndex = number("hIndex"),
724:             citationCount = number("citationCount"), lastPublicationYear = number("lastPublicationYear"),
725:             researchFields = text("researchFields"), disciplineCategory = text("disciplineCategory"),
726:             institution = text("institution"), emailSource = text("emailSource"),
727:             emailVerifiedLevel = number("emailVerifiedLevel"), dataSource = text("dataSource"),
728:             externalIds = source["externalIds"]?.let { objectMapper.writeValueAsString(it) },
729:             worksCount = number("worksCount"), tags = strings("tags"), updatedAt = text("updatedAt"),
730:             recentWorkTitles = strings("recentWorkTitles"),
731:             patentTitles = strings("patentTitles"), enrichedAt = text("enrichedAt"),
732:             enrichmentSource = text("enrichmentSource"), institutionType = text("institutionType"),
733:             identityVerification = source["identityVerification"]?.let {
734:                 DiscoveryIdentity.read(objectMapper.valueToTree(it))
735:             }, researchFieldIds = strings("researchFieldIds")
736:         )
737:     }
738: 
739: 
740:     /**
```

[expert/service/ExpertIndexWriterService.kt:744](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:744)
```kotlin
744:     fun reconcileDiscoveryCandidate(
745:         docId: String, snapshot: DiscoverySnapshot, classification: ExpertClassification,
746:         reasons: List<String>, preserveApplication: Boolean = false
747:     ): Boolean {
748:         val rawIndex = expertIndexService.indexName(ExpertIndexLevel.RAW)
749:         val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
750:         val current = readDiscoveryDocument(ExpertIndexLevel.RAW, docId) ?: return false
751:         if (current != snapshot || !DiscoveryIdentity.isDiscoveryMap(current.source) ||
752:             !DiscoveryIdentity.allowedMap(current.source)) return false
753:         val candidate = if (preserveApplication) null else readDiscoveryDocument(ExpertIndexLevel.CANDIDATE, docId)
754:         if (candidate != null && !sameDiscoveryIdentity(current.source, candidate.source)) return false
755:         val qualification = mapOf(
756:             "filterResult" to if (reasons.isEmpty()) "PASSED" else "REJECTED",
757:             "filterRejectReason" to reasons.takeIf { it.isNotEmpty() }?.joinToString("; "),
758:             "expertClassification" to classificationNode(classification)
759:         )
760:         val update = restTemplate.exchange(
761:             "${properties.baseUrl}/$rawIndex/_update/$docId?if_seq_no=${snapshot.seqNo}&if_primary_term=${snapshot.primaryTerm}",
762:             HttpMethod.POST, HttpEntity(mapOf("doc" to qualification), headers()), JsonNode::class.java
763:         ).body ?: return false
764:         if (update.path("result").asText() == "noop") return false
765:         val stableRaw = readDiscoveryDocument(ExpertIndexLevel.RAW, docId) ?: return false
766:         if (stableRaw.seqNo != update.path("_seq_no").asLong(-1) ||
767:             stableRaw.primaryTerm != update.path("_primary_term").asLong(-1) ||
768:             !sameDiscoveryIdentity(snapshot.source, stableRaw.source)) return false
769:         if (preserveApplication) return true
770:         if (reasons.isEmpty()) {
771:             if (candidate != null) return true // never overwrite operator-owned candidate fields
772:             val candidateDoc = stableRaw.source.toMutableMap().apply {
773:                 put("candidateValidatedAt", LocalDateTime.now().format(dateFormatter))
774:                 put("tags", ((this["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty() + "auto_promoted").distinct())
775:             }
776:             return writeCandidateDocument(docId, candidateDoc)
777:         }
778:         if (candidate == null) return true
779:         return try {
780:             restTemplate.exchange(
781:                 "${properties.baseUrl}/$candidateIndex/_doc/$docId?if_seq_no=${candidate.seqNo}&if_primary_term=${candidate.primaryTerm}",
782:                 HttpMethod.DELETE, HttpEntity(null, headers()), JsonNode::class.java
783:             )
784:             true
785:         } catch (e: HttpClientErrorException) {
786:             if (e.statusCode == HttpStatus.NOT_FOUND) true else throw e
787:         }
788:     }
789: 
790:     fun writeCandidateDocument(docId: String, doc: Map<String, Any?>): Boolean {
791:         val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
792:         val putUrl = "${properties.baseUrl}/$candidateIndex/_doc/$docId" +
793:             if (doc["identityVerification"] != null) "?op_type=create" else ""
794:         return try {
795:             restTemplate.exchange(
796:                 putUrl,
797:                 HttpMethod.PUT,
798:                 HttpEntity(doc, headers()),
799:                 JsonNode::class.java
800:             )
801:             true
802:         } catch (e: Exception) {
803:             log.warn("Failed to write candidate document for esDocId={}", docId, e)
804:             false
805:         }
806:     }
```

## orcid

[discovery/service/OrcidDataSource.kt:165](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt:165)
```kotlin
165:                 else {
166:                     // I-1：`institution-name` 是**没有主次之分的集合**，只有恰好一家非空机构时才可展示；
167:                     // 零项或多家一律 null，绝不用 `firstOrNull()` 按数组顺序任选第一家当主机构。
168:                     val institutions = node.path("institution-name")
169:                         .mapNotNull { it.asText(null)?.trim()?.takeIf { name -> name.isNotEmpty() } }
170:                         .distinct()
171:                     OrcidRecord(
172:                         orcidId = orcidId,
173:                         givenNames = node.path("given-names").asText(null),
174:                         familyNames = node.path("family-names").asText(null),
175:                         emails = emails,
176:                         institutionName = institutions.singleOrNull(),
177:                         country = null
178:                     )
179:                 }
180:             } catch (e: Exception) {
181:                 log.debug("Failed to parse ORCID record: {}", e.message)
182:                 null
183:             }
```

## discovery

[discovery/service/ExpertDiscoveryService.kt:1513](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1513)
```kotlin
1513:     /** I-1/I-2（01b）：ORCID 的 `institution-name` 只表达来源关联，不证明当前雇主/职位，也不是国籍证据。 */
1514:     private fun buildOrcidProfile(record: OrcidDataSource.OrcidRecord, authorEmail: AuthorEmail, emailVerifiedLevel: Int): ExpertProfile {
1515:         return ExpertProfile(
1516:             orcidId = record.orcidId,
1517:             email = DiscoveryIdentity.normalizedEmail(authorEmail.email),
1518:             givenNames = record.givenNames,
1519:             familyNames = record.familyNames,
1520:             country = null,
1521:             keyword = null, employment = null,
1522:             institution = record.institutionName, lastPublicationYear = null,
1523:             emailSource = "ORCID_PUBLIC", emailVerifiedLevel = emailVerifiedLevel, dataSource = "ORCID",
1524:             externalIds = objectMapper.writeValueAsString(mapOf("orcid" to authorEmail.orcidId)),
1525:             identityVerification = proofFor(authorEmail)
1526:         // 02（I-1/I-2）：ORCID 证据只能基于 01b 的「唯一机构」判定（机构为 null 时本函数原样返回）。
1527:         ).withInstitutionEvidence(DiscoveryIdentity.EVIDENCE_SOURCE_ORCID)
```

[discovery/service/ExpertDiscoveryService.kt:2283](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:2283)
```kotlin
2283:                            filterResult: String, rejectReasons: List<String>): Map<String, Any?> {
2284:         val now = LocalDateTime.now().format(dateFormatter)
2285:         val doc = mutableMapOf<String, Any?>(
2286:             "orcidId" to esDocId, "email" to profile.email,
2287:             "givenNames" to profile.givenNames, "familyNames" to profile.familyNames,
2288:             "country" to profile.country, "keyword" to profile.keyword,
2289:             "employment" to profile.employment, "institution" to profile.institution,
2290:             "institutionType" to profile.institutionType,
2291:             "lastPublicationYear" to profile.lastPublicationYear,
2292:             "emailSource" to profile.emailSource, "emailVerifiedLevel" to profile.emailVerifiedLevel,
2293:             "dataSource" to profile.dataSource,
2294:             "identityVerification" to profile.identityVerification,
2295:             "externalIds" to profile.externalIds?.let { objectMapper.readValue(it, Map::class.java) },
2296:             "discoveredAt" to now, "updatedAt" to now,
2297:             "filterResult" to filterResult,
2298:             "expertClassification" to expertIndexWriterService.classificationNode(expertClassificationService.classify(profile)),
2299:             "filterRejectReason" to rejectReasons.takeIf { it.isNotEmpty() }?.joinToString("; "),
2300:             "tags" to listOf("discovered")
2301:         )
2302:         // 02（I-1）：无证据就不写这个键 —— 绝不写 null / false / UNVERIFIED。
2303:         profile.institutionEvidence?.let { doc["institutionEvidence"] = it }
2304:         return doc
2305:     }
2306: 
2307:     private fun promoteDiscoveredToCandidate(esDocId: String, rawDoc: Map<String, Any?>): Boolean {
2308:         val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
2309:         val now = LocalDateTime.now().format(dateFormatter)
2310:         val candidateDoc = rawDoc.toMutableMap().apply {
2311:             put("candidateValidatedAt", now); put("updatedAt", now)
2312:             val existingTags = (get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
2313:             put("tags", (existingTags + "discovered").distinct())
2314:         }
2315:         val putUrl = "${esProperties.baseUrl}/$candidateIndex/_doc/$esDocId?op_type=create"
2316:         return try {
2317:             restTemplate.exchange(putUrl, HttpMethod.PUT, HttpEntity(candidateDoc, esHeaders()),
2318:                 com.fasterxml.jackson.databind.JsonNode::class.java)
2319:             true
2320:         } catch (e: Exception) {
2321:             log.warn("Failed to promote discovered expert {} to candidate: {}", esDocId, e.message)
2322:             false
2323:         }
2324:     }
2325: 
```

## query

[expert/service/ExpertSearchService.kt:215](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:215)
```kotlin
215:         fun notContactedWithEmailDomainsFilters(
216:             emailDomains: List<String> = emptyList(),
217:             discipline: String? = null
218:         ): List<Map<String, Any>> {
219:             val filters = mutableListOf<Map<String, Any>>(
220:                 mapOf("exists" to mapOf("field" to "email")),
221:                 mapOf("bool" to mapOf(
222:                     "must_not" to listOf(
223:                         mapOf("exists" to mapOf("field" to "operatorStatus")),
224:                         mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
225:                     )
226:                 ))
227:             )
228:             emailDomainsFilter(emailDomains)?.let { filters.add(it) }
229:             if (!discipline.isNullOrBlank()) {
230:                 filters.add(disciplineFilter(discipline))
231:             }
232:             return filters
233:         }
```

[expert/service/ExpertSearchService.kt:587](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:587)
```kotlin
587:     private fun sourceFields(): List<String> =
588:         listOf(
589:             "orcidId", "orcid", "id",
590:             "email", "givenNames", "familyNames",
591:             "country", "keyword", "employment",
592:             "age", "degree", "nationality",
593:             "hIndex", "citationCount", "lastPublicationYear",
594:             "researchFields", "researchFieldIds", "disciplineCategory", "institution", "institutionType",
595:             "filterResult", "institutionEvidence",
596:             "emailSource", "emailVerifiedLevel",
597:             "dataSource", "externalIds", "worksCount", "identityVerification",
598:             "tags",
599:             "updatedAt",
600:             "operatorStatus",
601:             "recentWorkTitles", "patentTitles", "enrichedAt", "enrichmentSource",
602:             "expertClassification"
603:         )
```

[expert/service/ExpertSearchService.kt:720](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:720)
```kotlin
720:     fun scrollExpertsFiltered(
721:         level: ExpertIndexLevel,
722:         filters: List<Map<String, Any>>,
723:         batchSize: Int = 500,
724:         handler: (List<ExpertProfile>) -> Boolean
725:     ) {
726:         val index = expertIndexService.indexName(level)
727:         var scrollId: String? = null
728: 
729:         try {
730:             val initialUrl = "${properties.baseUrl}/$index/_search?scroll=5m"
731:             val query = if (filters.isEmpty()) {
732:                 mapOf("match_all" to emptyMap<String, Any>())
733:             } else {
734:                 mapOf("bool" to mapOf("filter" to filters))
735:             }
736:             val requestBody = mapOf(
737:                 "size" to batchSize,
738:                 "_source" to sourceFields(),
739:                 "query" to query,
740:                 "sort" to listOf(mapOf("_doc" to "asc"))
741:             )
742:             var response = restTemplate.exchange(
743:                 initialUrl,
744:                 HttpMethod.POST,
745:                 HttpEntity(requestBody, headers()),
746:                 JsonNode::class.java
747:             ).body ?: return
748: 
749:             scrollId = response.path("_scroll_id").asText()
750:             val totalHits = response.path("hits").path("total").path("value").asLong(0)
751:             var batchNumber = 0
752: 
753:             do {
754:                 val hits = response.path("hits").path("hits")
755:                 if (hits.isEmpty) break
756: 
757:                 batchNumber++
758:                 val experts = hits.map { hit -> toExpertProfile(hit) }
759:                 val shouldContinue = handler(experts)
760:                 if (!shouldContinue) break
761:                 if (hits.size() < batchSize) break
762: 
763:                 response = restTemplate.exchange(
764:                     "${properties.baseUrl}/_search/scroll",
765:                     HttpMethod.POST,
766:                     HttpEntity(mapOf("scroll" to "5m", "scroll_id" to scrollId), headers()),
767:                     JsonNode::class.java
768:                 ).body ?: break
769: 
770:                 scrollId = response.path("_scroll_id").asText()
771:             } while (true)
772:         } finally {
773:             if (scrollId != null) {
774:                 try {
775:                     restTemplate.exchange(
776:                         "${properties.baseUrl}/_search/scroll",
777:                         HttpMethod.DELETE,
778:                         HttpEntity(mapOf("scroll_id" to scrollId), headers()),
779:                         JsonNode::class.java
780:                     )
781:                 } catch (_: Exception) {}
782:             }
```

## task

[task/service/TaskExecutionService.kt:138](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt:138)
```kotlin
138:     fun <T : Any?> runAndRecordWithResult(
139:         taskType: String,
140:         triggerType: String,
141:         request: Any,
142:         onStarted: ((executionId: Long) -> Unit)? = null,
143:         batchConfigId: Long? = null,
144:         block: () -> T
145:     ): Pair<TaskExecution, T> {
146:         val startedAt = LocalDateTime.now()
147:         val running = repository.save(
148:             TaskExecution(
149:                 taskType = taskType,
150:                 triggerType = triggerType,
151:                 status = "RUNNING",
152:                 requestPayload = toJson(request),
153:                 resultSummary = null,
154:                 startedAt = startedAt,
155:                 createdAt = startedAt,
156:                 updatedAt = startedAt,
157:                 batchConfigId = batchConfigId,
158:                 ownerToken = ownerToken,
159:                 heartbeatAt = startedAt
160:             )
161:         )
162: 
```

## smtp

[mail/service/SmtpMailDeliveryService.kt:17](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt:17)
```kotlin
17:     private val mailOpenTrackingService: MailOpenTrackingService
18: ) : MailDeliveryService {
19:     override fun send(account: MailSenderAccount, mail: ComposedMail): DeliveredMail {
20:         // I-1: 兜底 fail-closed 拦截。必须位于接触任何 SMTP 资源（getSender）之前；
21:         // 命中且未显式 override 时抛异常，绝不返回 DeliveredMail（I-2）。
22:         if (!mail.allowSuppressedRecipient && emailSuppressionService.isSuppressed(mail.to)) {
23:             throw RecipientSuppressedException(mail.to)
24:         }
25:         val cleanedMail = if (mail.html) mail.copy(body = mailContentService.stripOpenTrackingImages(mail.body)) else mail
26:         var openTrackingId: Long? = null
27:         val wireMail = if (!mail.isReply &&
28:             mail.inReplyTo.isNullOrBlank() && mail.references.isNullOrBlank() &&
29:             !REPLY_SUBJECT.containsMatchIn(mail.subject)
```
