# 批量邮件修复：代码证据快照

仅本地源码证据；未查询生产数据、未调用 Emailable、未发送邮件。基线后续可能变化，以方法名重新定位。

## E-00 基线

命令：
```sh
git rev-parse HEAD; git branch --show-current; git status --short
```
退出码：0

```text
d6f54c25b228ee2e9e0317d053957ae3f56984b5
main
 M docs/knowledge/campaign/K-initial-outreach-four-gate-paths.md
 M docs/knowledge/expert/K-expert-classification-one-object-three-layers.md
 M docs/knowledge/mail/K-inbound-seen-not-processed-marker.md
 M docs/knowledge/mail/K-process-single-all-callers.md
 M docs/releases.json
 M src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailInboxCursorRepository.kt
 M src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt
 M src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt
 M src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt
 M src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAccountConnectivityService.kt
 M src/main/kotlin/com/weibo/talentintroduction/mail/service/MailInboxCursorService.kt
 M src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReceiveService.kt
 M src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt
 M src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpSenderFactory.kt
 M src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt
 M src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt
 M src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMetadataFetchIT.kt
 M src/test/kotlin/com/weibo/talentintroduction/mail/service/MailInboxCursorServiceTest.kt
 M src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt
?? docs/audits/2026-09-25-deep-discovery-duplicate-names/
?? docs/audits/2026-09-25-deep-discovery-recheck-2688/
?? docs/audits/2026-09-25-delete-unresolved-experts/
?? docs/audits/2026-09-25-expert-identity/
?? docs/audits/2026-09-26-mail-open-tracking-diagnosis.md
?? docs/deploy/2026-09-25-emailable-policy-pagination.md
?? docs/deploy/2026-09-25-new-mailbox-skip-history.md
?? docs/introduction-mail-copy-neutral-2026-09-25-applied.json
?? docs/introduction-mail-copy-neutral-2026-09-25.json
?? docs/introduction-mail-copy-neutral-2026-09-25.md
?? docs/mockups/open-tracking-preview/
?? docs/mockups/task-center-preview/
?? docs/plans/2026-09-25/new-mailbox-skip-history.md
?? docs/plans/2026-09-26/
?? scripts/build_contactout_enterprise_es_import.py
?? scripts/build_qiandeng_contactout_configs.py
?? scripts/update_sbir_employment.py
```

## E-01 存储直接引用

命令：
```sh
rg -n "batch_send_task_config|batch_email_verification" src/main scripts --glob "!*.json" --glob "!app.js" --glob "!*.html"
```
退出码：0

```text
src/main/resources/db/migration/V139__add_batch_email_verification_enabled.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V138__create_batch_email_verification.sql:39:CREATE TABLE batch_email_verification (
src/main/resources/db/migration/V138__create_batch_email_verification.sql:59:    UNIQUE KEY uk_batch_email_verification_target (task_execution_id, orcid_id, email),
src/main/resources/db/migration/V138__create_batch_email_verification.sql:60:    KEY idx_batch_email_verification_execution (task_execution_id, id),
src/main/resources/db/migration/V138__create_batch_email_verification.sql:61:    CONSTRAINT fk_batch_email_verification_execution
src/main/resources/db/migration/V129__add_material_request_codes.sql:5:-- V128__add_research_direction_filter_to_batch_send_task_config.sql 撞号；后者已于
src/main/resources/db/migration/V93__add_regions_to_batch_send_task_config.sql:1:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V93__add_regions_to_batch_send_task_config.sql:3:UPDATE batch_send_task_config SET regions_json = '[]' WHERE regions_json = '';
src/main/resources/db/migration/V99__add_gate_filter_enabled_to_batch_send_task_config.sql:3:ALTER TABLE batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:378: * I-1: `batch_send_task_config.sender_account_codes_json` 的唯一解析点。
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql:6:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql:9:UPDATE batch_send_task_config
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql:13:ALTER TABLE batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt:7:@Table("batch_send_task_config")
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:1:CREATE TABLE batch_send_task_config (
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:24:    UNIQUE KEY uk_batch_send_task_config_legacy_code (legacy_code),
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:25:    UNIQUE KEY uk_batch_send_task_config_active_name (active_config_name),
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:26:    KEY idx_batch_send_task_config_deleted_updated (deleted_at, updated_at),
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:27:    KEY idx_batch_send_task_config_auto_deleted (auto_enabled, deleted_at),
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:28:    KEY idx_batch_send_task_config_template (template_id),
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:29:    CONSTRAINT fk_batch_send_task_config_template
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:34:INSERT INTO batch_send_task_config (
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:83:    SELECT 1 FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION'
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:87:INSERT INTO batch_send_task_config (
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:139:    SELECT 1 FROM batch_send_task_config WHERE legacy_code = 'MATERIAL_REMINDER'
src/main/resources/db/migration/V108__add_expert_types_to_batch_send_task_config.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V108__add_expert_types_to_batch_send_task_config.sql:6:UPDATE batch_send_task_config SET expert_types_json = '[]';
src/main/resources/db/migration/V73__add_batch_config_id_to_task_execution.sql:9:        FOREIGN KEY (batch_config_id) REFERENCES batch_send_task_config(id);
src/main/resources/db/migration/V92__drop_daily_cap_from_batch_send_task_config.sql:1:ALTER TABLE batch_send_task_config DROP COLUMN daily_cap;
src/main/resources/db/migration/V140__reuse_batch_email_verification.sql:3:ALTER TABLE batch_email_verification
src/main/resources/db/migration/V140__reuse_batch_email_verification.sql:5:    ADD INDEX idx_batch_email_verification_email_time (email, checked_at, id);
src/main/resources/db/migration/V128__add_research_direction_filter_to_batch_send_task_config.sql:5:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:2:-- TEXT 列不能带 DEFAULT（MySQL 限制），故照 V93__add_regions_to_batch_send_task_config.sql
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:5:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:8:UPDATE batch_send_task_config
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:14:ALTER TABLE batch_send_task_config DROP COLUMN email_domain;
src/main/resources/db/migration/V103__add_reachability_filter_to_batch_send_task_config.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V74__repair_batch_send_task_config_encoding.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V74__repair_batch_send_task_config_encoding.sql:9:UPDATE batch_send_task_config
src/main/resources/db/migration/V74__repair_batch_send_task_config_encoding.sql:14:UPDATE batch_send_task_config
src/main/resources/db/migration/V91__add_rounds_per_run_to_batch_send_task_config.sql:4:ALTER TABLE batch_send_task_config ADD COLUMN rounds_per_run INT NOT NULL DEFAULT 1 AFTER round_size;
src/main/resources/db/migration/V91__add_rounds_per_run_to_batch_send_task_config.sql:6:UPDATE batch_send_task_config SET rounds_per_run = GREATEST(1, CEIL(daily_cap / round_size));
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:6:UPDATE batch_send_task_config
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:12:ALTER TABLE batch_send_task_config DROP COLUMN operator_status;
src/main/resources/db/migration/V110__require_expert_types_on_batch_send_task_config.sql:4:UPDATE batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:623:        return "uk_batch_send_task_config_active_name" in messages ||
src/main/resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql:5:ALTER TABLE batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:125:     * deleted; a template still referenced by `batch_send_task_config.template_id`
src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskExecution.kt:23:    /** Source batch_send_task_config id at launch; null for independent manual runs. Soft-delete safe. */
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:183:               SELECT 1 FROM batch_email_verification v
src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt:22: * Per-config dynamic cron scheduler (I-2). Each enabled [batch_send_task_config] row gets its own
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:12: * `batch_email_verification`（V138）的**唯一**业务写方与只读查询方（子计划 01 T1 / I-6 / I-9）。
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:50:        return requireNotNull(keyHolder.key) { "batch_email_verification insert returned no generated id" }.toLong()
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:177:            INSERT INTO batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:184:            UPDATE batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:191:            SELECT * FROM batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:200:            UPDATE batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:207:            UPDATE batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:213:            UPDATE batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:219:            UPDATE batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:228:              FROM batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:249:              FROM batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:13:        SELECT * FROM batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:22:        SELECT * FROM batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:32:        SELECT * FROM batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:44:        SELECT * FROM batch_send_task_config
```

## E-02 仓储调用与快照入口

命令：
```sh
rg -n "BatchSendTaskConfigRepository|BatchEmailVerificationRepository|toExecutionSnapshot|BatchExecutionSnapshot\(" src/main/kotlin
```
退出码：0

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:10:data class BatchExecutionSnapshot(
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:33:     * 前端手动快照、`toExecutionSnapshot` 与 `RecipientScope.fromSnapshot` 逐字传递。
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:303:fun BatchSendTaskConfig.toExecutionSnapshot(
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:353:    return BatchExecutionSnapshot(
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:26:class BatchEmailVerificationRepository(private val jdbcTemplate: JdbcTemplate) {
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:7:interface BatchSendTaskConfigRepository : CrudRepository<BatchSendTaskConfig, Long> {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:13:import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:32:    private val repository: BatchSendTaskConfigRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:7:import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:53:    private val repository: BatchEmailVerificationRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:7:import com.weibo.talentintroduction.campaign.domain.toExecutionSnapshot
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:8:import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:34:    private val batchSendTaskConfigRepository: BatchSendTaskConfigRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:65:        val snapshot = config.toExecutionSnapshot(objectMapper)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:106:            snapshot = config.toExecutionSnapshot(objectMapper)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:123:            val snapshot = config.toExecutionSnapshot(objectMapper)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:153:            val snapshot = config.toExecutionSnapshot(objectMapper)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:257:            val snapshot = config.toExecutionSnapshot(objectMapper, oneRoundOnly = true)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:593:        BatchExecutionSnapshot(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1628:        BatchExecutionSnapshot(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:9:import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationRepository
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:50:    private val batchEmailVerificationRepository: BatchEmailVerificationRepository
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:187:        require(limit in 1..BatchEmailVerificationRepository.MAX_PAGE_SIZE) {
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:188:            "limit must be between 1 and ${BatchEmailVerificationRepository.MAX_PAGE_SIZE}"
src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt:4:import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt:28:    private val batchSendTaskConfigRepository: BatchSendTaskConfigRepository,
```

## E-03 配置读写全集

命令：
```sh
rg -n "fun |repository\.|emailVerificationEnabled" src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt
```
退出码：0

```text
13:import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
46:    fun list(query: String?): List<BatchSendTaskConfigView> {
49:            repository.findAllActiveOrderByUpdatedAtDescIdDesc()
51:            repository.findAllActiveByConfigNameContainingOrderByUpdatedAtDescIdDesc(trimmed)
59:    fun get(id: Long): BatchSendTaskConfigView {
60:        val row = repository.findByIdAndDeletedAtIsNull(id)
66:    fun create(cmd: BatchSendTaskConfigCreateCommand): BatchSendTaskConfigView {
91:                emailVerificationEnabled = normalized.emailVerificationEnabled,
102:    fun update(id: Long, cmd: BatchSendTaskConfigUpdateCommand): BatchSendTaskConfigView {
103:        val existing = repository.findByIdAndDeletedAtIsNull(id)
109:                mergedEmailVerificationEnabled = cmd.emailVerificationEnabled ?: existing.emailVerificationEnabled
136:                emailVerificationEnabled = normalized.emailVerificationEnabled,
146:    fun setEnabled(id: Long, enabled: Boolean): BatchSendTaskConfigView {
147:        val existing = repository.findByIdAndDeletedAtIsNull(id)
159:        val saved = repository.save(
170:    fun softDelete(id: Long) {
171:        val existing = repository.findByIdAndDeletedAtIsNull(id)
174:        repository.save(
188:    fun getLegacyConfig(sendType: BatchSendType): BatchSendConfig =
196:    fun updateLegacyConfig(sendType: BatchSendType, request: BatchSendConfigUpdateRequest): BatchSendConfig {
228:                emailVerificationEnabled = existing.emailVerificationEnabled,
246:    private fun requireActiveLegacy(sendType: BatchSendType): BatchSendTaskConfig {
247:        val row = repository.findByLegacyCode(sendType.name)
257:    private fun toLegacyConfig(row: BatchSendTaskConfig, sendType: BatchSendType): BatchSendConfig =
272:    private fun normalizeAndValidate(fields: ConfigFields, excludeId: Long?): NormalizedConfig {
277:        val duplicate = repository.findByConfigNameAndDeletedAtIsNull(configName)
369:        require(mailType == BatchSendType.INTRODUCTION.name || !fields.emailVerificationEnabled) {
402:            emailVerificationEnabled = fields.emailVerificationEnabled
410:    private fun resolveMailType(templateId: Long?): String {
433:    private fun normalizeFunnelLevel(raw: String?): String? {
442:    private fun normalizeOptionalFilter(raw: String?): String? {
448:    private fun normalizeTags(tags: List<String>): List<String> =
454:    private fun parseTags(tagsJson: String): List<String> =
469:    private fun normalizeRegions(regions: List<String>): List<String> {
479:    private fun parseRegions(regionsJson: String): List<String> =
490:    private fun parseEmailDomains(json: String?): List<String> {
502:    private fun parseOperatorStatuses(json: String?): List<String> {
515:    private fun parseExpertTypes(json: String?): List<String> {
527:    private fun toView(row: BatchSendTaskConfig, lastExecutedAt: LocalDateTime? = null): BatchSendTaskConfigView {
551:            emailVerificationEnabled = row.emailVerificationEnabled,
565:    private fun computeNextFireTime(cron: String): LocalDateTime? {
570:    fun previewCron(cron: String, count: Int = 5): CronPreviewResult {
594:    private fun publishReload(cron: String) {
602:    private fun saveConfig(entity: BatchSendTaskConfig, configName: String): BatchSendTaskConfig =
604:            repository.save(entity)
611:    private fun throwActiveNameConflictOrRethrow(configName: String, e: DataIntegrityViolationException): Nothing {
618:    private fun isActiveNameUniqueViolation(e: DataIntegrityViolationException): Boolean {
648:        val emailVerificationEnabled: Boolean = false
673:        val emailVerificationEnabled: Boolean = false
676:    private fun BatchSendTaskConfigCreateCommand.toFields() = ConfigFields(
696:        emailVerificationEnabled = emailVerificationEnabled
703:    private fun BatchSendTaskConfigUpdateCommand.toFields(
725:        emailVerificationEnabled = mergedEmailVerificationEnabled
728:    private fun BatchSendTaskConfig.toFields() = ConfigFields(
748:        emailVerificationEnabled = emailVerificationEnabled
```

## E-04 验证读写与有效性

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt`；读取命令等价于 `nl -ba src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt` 后按列出的行段截取。

```text
26: class BatchEmailVerificationRepository(private val jdbcTemplate: JdbcTemplate) {
27: 
28:     /** I-6：先插 PENDING 行，返回其自增 id；唯一键冲突（同执行同 ORCID 同邮箱）由 DB 拒绝。 */
29:     fun insertPending(
30:         taskExecutionId: Long,
31:         expertDocId: String?,
32:         orcidId: String,
33:         expertName: String?,
34:         email: String,
35:         now: LocalDateTime
36:     ): Long {
37:         require(taskExecutionId > 0) { "taskExecutionId must be positive" }
38:         val keyHolder = GeneratedKeyHolder()
39:         jdbcTemplate.update({ connection ->
40:             val statement = connection.prepareStatement(INSERT_PENDING_SQL, arrayOf("id"))
41:             statement.setLong(1, taskExecutionId)
42:             statement.setString(2, boundIdentity(expertDocId, COLUMN_EXPERT_DOC_ID, MAX_EXPERT_DOC_ID))
43:             statement.setString(3, requireIdentity(orcidId, COLUMN_ORCID_ID, MAX_ORCID_ID))
44:             statement.setString(4, truncate(expertName, MAX_EXPERT_NAME))
45:             statement.setString(5, requireIdentity(email, COLUMN_EMAIL, MAX_EMAIL))
46:             statement.setObject(6, now)
47:             statement.setObject(7, now)
48:             statement
49:         }, keyHolder)
50:         return requireNotNull(keyHolder.key) { "batch_email_verification insert returned no generated id" }.toLong()
51:     }
52: 
53:     /**
54:      * 写入验证结论（PENDING → PASS/SKIP/ERROR）。只此一次：`decision = 'PENDING'` 为前置条件，
55:      * 返回受影响行数；调用方必须要求 1 行，否则说明重复目标或状态被外力改动。
56:      */
57:     fun recordDecision(
58:         id: Long,
59:         decision: String,
60:         providerState: String?,
61:         providerReason: String?,
62:         errorCode: String?,
63:         requestCount: Int,
64:         checkedAt: LocalDateTime?,
65:         now: LocalDateTime
66:     ): Int = jdbcTemplate.update(
67:         RECORD_DECISION_SQL,
68:         decision,
69:         truncate(providerState, MAX_PROVIDER_STATE),
70:         truncate(providerReason, MAX_PROVIDER_REASON),
71:         truncate(errorCode, MAX_ERROR_CODE),
72:         requestCount,
73:         checkedAt,
74:         now,
75:         id
76:     )
77: 
78:     /** 最近一年内的原始已完成结果；按邮箱跨执行复用，不拿复用行延长有效期。 */
79:     fun findReusable(email: String, now: LocalDateTime): BatchEmailVerificationRow? =
80:         jdbcTemplate.query(FIND_REUSABLE_SQL, ROW_MAPPER, email, now.minusYears(1), now).firstOrNull()
81: 
82:     /** 复制验证结论及原始时间，不复制其它专家的发送/标签结果。 */
83:     fun recordReusedDecision(
84:         id: Long, sourceId: Long, decision: String, providerState: String?,
85:         providerReason: String?, checkedAt: LocalDateTime, now: LocalDateTime
86:     ): Int = jdbcTemplate.update(
87:         RECORD_REUSED_DECISION_SQL, decision, providerState, providerReason,
88:         checkedAt, sourceId, now, id
89:     )
90: 
91:     /** 标签处理结果；只写 tag_status / tag_error，不触碰验证结论。 */
92:     fun recordTag(id: Long, tagStatus: String, tagError: String?, now: LocalDateTime): Int =
93:         jdbcTemplate.update(RECORD_TAG_SQL, tagStatus, truncate(tagError, MAX_TAG_ERROR), now, id)
94: 
95:     /** 发送结果；只写 send_status / send_reason，不触碰验证结论。 */
96:     fun recordSend(id: Long, sendStatus: String, sendReason: String?, now: LocalDateTime): Int =
97:         jdbcTemplate.update(RECORD_SEND_SQL, sendStatus, truncate(sendReason, MAX_SEND_REASON), now, id)
98: 
99:     /**
100:      * I-6：SMTP 前的条件预占。只有 `decision='PASS' AND send_status='NOT_SENT'` 的行能变成 SENDING，
101:      * 返回是否恰好影响 1 行。其它结果（重复目标、已 SENT/FAILED/SENDING/SKIPPED、非 PASS）返回 false，
102:      * 调用方必须停止该次执行并报告状态冲突，不得继续发信。
103:      */
104:     fun markSending(id: Long, now: LocalDateTime): Boolean =
105:         jdbcTemplate.update(MARK_SENDING_SQL, now, id) == 1
106: 
107:     /** 分页游标查询（不外带 +1；`hasMore` 由调用方传 limit+1 判定，见 [readPage]）。 */
108:     fun listAfter(executionId: Long, afterId: Long, limit: Int): List<BatchEmailVerificationRow> {
109:         require(limit > 0) { "limit must be positive" }
110:         return jdbcTemplate.query(LIST_AFTER_SQL, ROW_MAPPER, executionId, afterId, limit)
111:     }
112: 
113:     /** 单个执行的结果汇总（状态/结果计数一律来自本表，而非 errorSamples）。 */
114:     fun aggregate(executionId: Long): BatchEmailVerificationAggregate =
115:         jdbcTemplate.queryForObject(AGGREGATE_SQL, AGGREGATE_MAPPER, executionId)
116:             ?: BatchEmailVerificationAggregate.EMPTY
117: 
118:     /**
119:      * 只读事务内组合「一页明细 + 全量汇总」：分页与汇总必须来自同一快照，否则页与汇总会互相矛盾。
120:      * limit 缺省 50、上限 100；`hasMore` 由 limit+1 判定（多取一行即说明后面还有）。
121:      */
122:     @Transactional(readOnly = true)
123:     fun readPage(
124:         executionId: Long,
125:         afterId: Long = 0L,
126:         limit: Int = DEFAULT_PAGE_SIZE
127:     ): BatchEmailVerificationPage {
128:         val effective = limit.coerceIn(1, MAX_PAGE_SIZE)
129:         val rows = listAfter(executionId, afterId, effective + 1)
130:         return BatchEmailVerificationPage(
131:             rows = rows.take(effective),
132:             hasMore = rows.size > effective,
133:             aggregate = aggregate(executionId)
134:         )
135:     }
136: 
137:     private fun requireIdentity(value: String, column: String, max: Int): String {
138:         val trimmed = value.trim()
139:         if (trimmed.isEmpty()) throw IllegalArgumentException("$column 不能为空")
140:         if (trimmed.length > max) {
141:             throw IllegalArgumentException("$column 超长（${trimmed.length} > $max），拒绝截断写入")
142:         }
143:         return trimmed
144:     }
145: 
146:     private fun boundIdentity(value: String?, column: String, max: Int): String? {
147:         val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
148:         if (trimmed.length > max) {
149:             throw IllegalArgumentException("$column 超长（${trimmed.length} > $max），拒绝截断写入")
150:         }
151:         return trimmed
152:     }
153: 
154:     private fun truncate(value: String?, max: Int): String? =
155:         value?.takeIf { it.isNotEmpty() }?.let { if (it.length <= max) it else it.substring(0, max) }
156: 
157:     companion object {
158:         const val DEFAULT_PAGE_SIZE = 50
174:         const val MAX_TAG_ERROR = 256
175: 
176:         private const val INSERT_PENDING_SQL = """
177:             INSERT INTO batch_email_verification
178:                 (task_execution_id, expert_doc_id, orcid_id, expert_name, email,
179:                  decision, request_count, send_status, tag_status, created_at, updated_at)
180:             VALUES (?, ?, ?, ?, ?, 'PENDING', 0, 'NOT_SENT', 'NOT_REQUIRED', ?, ?)
181:         """
182: 
183:         private const val RECORD_DECISION_SQL = """
184:             UPDATE batch_email_verification
185:                SET decision = ?, provider_state = ?, provider_reason = ?, error_code = ?,
186:                    request_count = ?, checked_at = ?, updated_at = ?
187:              WHERE id = ? AND decision = 'PENDING'
188:         """
189: 
190:         private const val FIND_REUSABLE_SQL = """
191:             SELECT * FROM batch_email_verification
192:              WHERE email = ? AND checked_at > ? AND checked_at <= ?
193:                AND request_count > 0 AND reused_from_id IS NULL AND error_code IS NULL
194:                AND ((decision = 'PASS' AND provider_state IN ('deliverable', 'risky', 'unknown'))
195:                  OR (decision = 'SKIP' AND provider_state IN ('undeliverable', 'risky', 'unknown')))
196:              ORDER BY checked_at DESC, id DESC LIMIT 1
197:         """
198: 
199:         private const val RECORD_REUSED_DECISION_SQL = """
200:             UPDATE batch_email_verification
201:                SET decision = ?, provider_state = ?, provider_reason = ?, error_code = NULL,
202:                    request_count = 0, checked_at = ?, reused_from_id = ?, updated_at = ?
203:              WHERE id = ? AND decision = 'PENDING'
204:         """
205: 
206:         private const val RECORD_TAG_SQL = """
207:             UPDATE batch_email_verification
208:                SET tag_status = ?, tag_error = ?, updated_at = ?
209:              WHERE id = ?
210:         """
211: 
212:         private const val RECORD_SEND_SQL = """
213:             UPDATE batch_email_verification
214:                SET send_status = ?, send_reason = ?, updated_at = ?
215:              WHERE id = ?
216:         """
217: 
218:         private const val MARK_SENDING_SQL = """
219:             UPDATE batch_email_verification
220:                SET send_status = 'SENDING', send_reason = NULL, updated_at = ?
221:              WHERE id = ? AND decision = 'PASS' AND send_status = 'NOT_SENT'
222:         """
223: 
224:         private const val LIST_AFTER_SQL = """
225:             SELECT id, task_execution_id, expert_doc_id, orcid_id, expert_name, email, decision,
226:                    provider_state, provider_reason, error_code, request_count, checked_at,
227:                    send_status, send_reason, tag_status, tag_error, created_at, updated_at, reused_from_id
228:               FROM batch_email_verification
229:              WHERE task_execution_id = ? AND id > ?
230:              ORDER BY id ASC
231:              LIMIT ?
232:         """
233: 
234:         private const val AGGREGATE_SQL = """
235:             SELECT COUNT(*)                                      AS total_rows,
236:                    COALESCE(SUM(decision = 'PENDING'), 0)        AS pending_rows,
237:                    COALESCE(SUM(decision = 'PASS'), 0)           AS pass_rows,
238:                    COALESCE(SUM(decision = 'SKIP'), 0)           AS skip_rows,
239:                    COALESCE(SUM(decision = 'ERROR'), 0)          AS error_rows,
240:                    COALESCE(SUM(send_status = 'NOT_SENT'), 0)    AS not_sent_rows,
241:                    COALESCE(SUM(send_status = 'SENDING'), 0)     AS sending_rows,
242:                    COALESCE(SUM(send_status = 'SENT'), 0)        AS sent_rows,
243:                    COALESCE(SUM(send_status = 'FAILED'), 0)      AS send_failed_rows,
244:                    COALESCE(SUM(send_status = 'SKIPPED'), 0)     AS send_skipped_rows,
245:                    COALESCE(SUM(tag_status = 'NOT_REQUIRED'), 0) AS tag_not_required_rows,
246:                    COALESCE(SUM(tag_status = 'PENDING'), 0)      AS tag_pending_rows,
247:                    COALESCE(SUM(tag_status = 'APPLIED'), 0)      AS tag_applied_rows,
248:                    COALESCE(SUM(tag_status = 'FAILED'), 0)       AS tag_failed_rows
249:               FROM batch_email_verification
250:              WHERE task_execution_id = ?
251:         """
252: 
253:         private val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int -> rs.toRow() }
254: 
255:         private val AGGREGATE_MAPPER = RowMapper { rs: ResultSet, _: Int ->
256:             BatchEmailVerificationAggregate(
257:                 total = rs.getInt("total_rows"),
```

## E-05 原始响应分类及重试

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt`；读取命令等价于 `nl -ba src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt` 后按列出的行段截取。

```text
76:     /**
77:      * 验证一个目标：先落 PENDING 明细，再出结论，SKIP 追加标签并写标签结果。
78:      * 审计写入失败抛 [EmailVerificationAuditException]；调用方据此停止本次执行且不得发信。
79:      */
80:     fun verify(context: ExecutionVerificationContext, target: EmailVerificationTarget): VerificationResult {
81:         val normalizedEmail = normalizeVerificationEmail(target.email)
82:         val normalizedOrcid = ExpertIdNormalizer.normalize(target.orcidId)
83:         val rowId = audit("插入验证明细") {
84:             repository.insertPending(
85:                 taskExecutionId = context.executionId,
86:                 expertDocId = target.expertDocId,
87:                 orcidId = normalizedOrcid,
88:                 expertName = target.expertName,
89:                 email = normalizedEmail,
90:                 now = verificationNow()
91:             )
92:         }
93:         if (context.isCancelled()) return VerificationResult.Cancelled(rowId)
94: 
95:         val now = verificationNow()
96:         val reused = context.reuseOf(normalizedEmail)?.takeIf {
97:             it.checkedAt > now.minusYears(1) && it.checkedAt <= now
98:         } ?: audit("查询历史验证") {
99:             repository.findReusable(normalizedEmail, now)?.let {
100:                 // 复用供应商事实，不沿用旧发送策略的 SKIP；原行与原检查时间保持不变。
101:                 ReusedProviderResult(requireNotNull(decisionForState(it.providerState)), it.providerState,
102:                     it.providerReason, requireNotNull(it.checkedAt), it.id)
103:             }
104:         }
105:         if (context.isCancelled()) return VerificationResult.Cancelled(rowId)
106:         if (reused != null) {
107:             audit("记录历史验证复用") {
108:                 require(repository.recordReusedDecision(
109:                     rowId, reused.sourceRowId, reused.decision, reused.providerState,
110:                     reused.providerReason, reused.checkedAt, verificationNow()
111:                 ) == 1) { "复用验证结论未落库：id=$rowId" }
112:             }
113:             context.remember(normalizedEmail, reused)
114:             return conclude(context, target.copy(orcidId = normalizedOrcid), normalizedEmail, rowId, reused.decision, null)
115:         }
116: 
117:         val outcome = requestNewResult(context, normalizedEmail) ?: return VerificationResult.Cancelled(rowId)
118:         val checkedAt = verificationNow()
119:         persistDecision(
120:             rowId, outcome.decision, outcome.providerState, outcome.providerReason,
121:             outcome.errorCode, outcome.requestCount, checkedAt
122:         )
123:         if (outcome.decision in REUSABLE_DECISIONS) {
124:             context.remember(
125:                 normalizedEmail,
126:                 ReusedProviderResult(outcome.decision, outcome.providerState, outcome.providerReason, checkedAt, rowId)
127:             )
128:         }
129:         return conclude(
130:             context,
131:             target.copy(orcidId = normalizedOrcid),
132:             normalizedEmail,
133:             rowId,
134:             outcome.decision,
135:             outcome.errorCode
136:         )
137:     }
138: 
139:     /**
140:      * I-6：SMTP 前的条件预占。必须恰好影响 1 行（PASS + NOT_SENT → SENDING）才允许发信；
141:      * false 表示重复目标或状态冲突，调用方必须停止并报告，不得继续发信。
142:      */
143:     fun markSending(rowId: Long): Boolean = audit("发送前预占 SENDING") {
144:         repository.markSending(rowId, verificationNow())
145:     }
146: 
147:     /**
148:      * I-6：写发送结果（SENT / FAILED / NOT_SENT）。只更新发送列，不覆盖验证结论。
149:      * 发送结果无法落库时抛 [EmailVerificationAuditException]：已发出的邮件保留原计数，
150:      * 本行留在 SENDING（对外「结果未确认」），禁止据此自动重发。
151:      */
152:     fun recordSend(rowId: Long, sendStatus: String, sendReason: String?) {
153:         audit("记录发送结果") {
154:             require(repository.recordSend(rowId, sendStatus, sendReason, verificationNow()) == 1) {
155:                 "发送结果未落库（受影响 0 行）：id=$rowId"
156:             }
157:         }
163:         normalizedEmail: String,
164:         rowId: Long,
165:         decision: String,
166:         errorCode: String?
167:     ): VerificationResult = when (decision) {
168:         BatchEmailVerificationDecision.PASS -> VerificationResult.Passed(rowId, normalizedEmail)
169:         BatchEmailVerificationDecision.SKIP -> {
170:             // 标签处理是 SKIP 的独立结果：先 PENDING（崩溃后可见未完成），ES 处理完再写 APPLIED/FAILED。
171:             audit("记录标签待处理") {
172:                 require(repository.recordTag(rowId, BatchEmailVerificationTagStatus.PENDING, null, verificationNow()) == 1) {
173:                     "标签待处理未落库（受影响 0 行）：id=$rowId"
174:                 }
175:             }
176:             val tag = appendEmailAbnormalTag(target, normalizedEmail)
177:             audit("记录标签结果") {
178:                 require(repository.recordTag(rowId, tag.status, tag.error, verificationNow()) == 1) {
179:                     "标签结果未落库（受影响 0 行）：id=$rowId"
180:                 }
181:             }
182:             // SKIP 行不进 SMTP：send_status=SKIPPED + 跳过码（≠ 发送成功）。
183:             recordSend(rowId, BatchEmailVerificationSendStatus.SKIPPED, BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED)
184:             VerificationResult.Rejected(rowId, tag.status)
185:         }
186:         else -> VerificationResult.ServiceFailure(rowId, errorCode ?: BatchEmailVerificationErrorCodes.SERVICE_ERROR)
187:     }
188: 
189:     private fun persistDecision(
190:         rowId: Long,
191:         decision: String,
192:         providerState: String?,
193:         providerReason: String?,
194:         errorCode: String?,
195:         requestCount: Int,
196:         checkedAt: LocalDateTime
197:     ) {
198:         audit("写入验证结论") {
199:             require(repository.recordDecision(rowId, decision, providerState, providerReason, errorCode, requestCount, checkedAt, verificationNow()) == 1) {
200:                 "验证结论未落库（受影响 0 行，疑似重复目标）：id=$rowId"
201:             }
202:         }
203:     }
204: 
205:     /**
206:      * 物理请求循环：249（供应商明确「未完成」）只再试一次，间隔 500ms；其它失败不重试。
207:      * 返回 null 表示等待期间被取消（未验证者不标记）。requestCount 记物理请求次数。
208:      */
209:     private fun requestNewResult(context: ExecutionVerificationContext, normalizedEmail: String): ProviderOutcome? {
210:         var attempt = 0
211:         while (true) {
212:             attempt++
213:             if (!context.beginPhysicalRequest()) return null
214:             val response = try {
215:                 verifyClient.postVerify(EmailableVerifyRequest(normalizedEmail, apiKey.trim()))
216:             } catch (e: HttpTimeoutException) {
217:                 log.warn("Email verification timed out for {}: {}", normalizedEmail, e.message)
218:                 return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.TIMEOUT, attempt)
219:             } catch (e: IOException) {
220:                 log.warn("Email verification transport failure for {}: {}", normalizedEmail, e.message)
221:                 return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.SERVICE_ERROR, attempt)
222:             }
223:             val outcome = classifyResponse(response, normalizedEmail, attempt)
224:             if (!outcome.incomplete || attempt >= MAX_ATTEMPTS_PER_EMAIL) {
225:                 return outcome.copy(incomplete = false)
226:             }
227:             if (!context.awaitRetryDelay()) return null
228:         }
229:     }
230: 
231:     private fun classifyResponse(
232:         response: EmailableHttpResponse,
233:         normalizedEmail: String,
234:         requestCount: Int
235:     ): ProviderOutcome = when (response.statusCode) {
236:         HTTP_OK -> classifyBody(response.body, normalizedEmail, requestCount)
237:         HTTP_INCOMPLETE -> ProviderOutcome(
238:             decision = BatchEmailVerificationDecision.ERROR,
239:             errorCode = BatchEmailVerificationErrorCodes.INCOMPLETE,
240:             requestCount = requestCount,
241:             incomplete = requestCount < MAX_ATTEMPTS_PER_EMAIL
242:         )
243:         HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> ProviderOutcome.failed(BatchEmailVerificationErrorCodes.AUTH_ERROR, requestCount)
244:         HTTP_PAYMENT_REQUIRED -> ProviderOutcome.failed(BatchEmailVerificationErrorCodes.NO_CREDITS, requestCount)
245:         HTTP_TOO_MANY_REQUESTS -> ProviderOutcome.failed(BatchEmailVerificationErrorCodes.RATE_LIMITED, requestCount)
246:         in 400..499 -> ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount)
247:         else -> ProviderOutcome.failed(BatchEmailVerificationErrorCodes.SERVICE_ERROR, requestCount)
248:     }
249: 
250:     /** 200 响应必须同时给出可确认的 state、匹配的 email，才可能 PASS；否则是受控 ERROR。 */
251:     private fun classifyBody(body: String, normalizedEmail: String, requestCount: Int): ProviderOutcome {
252:         val node = try {
253:             objectMapper.readTree(body)
254:         } catch (e: Exception) {
255:             log.warn("Email verification returned unparsable body for {}: {}", normalizedEmail, e.message)
256:             return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount)
257:         }
258:         if (node == null || !node.isObject) {
259:             return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount)
260:         }
261:         val state = node.path("state").textOrNull()
262:         val reason = node.path("reason").textOrNull()
263:         val returnedEmail = node.path("email").textOrNull()
264:         if (state == null) {
265:             return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount)
266:         }
267:         if (returnedEmail == null || normalizeVerificationEmail(returnedEmail) != normalizedEmail) {
268:             log.warn("Email verification response does not echo the requested address for {}", normalizedEmail)
269:             return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount, state, reason)
270:         }
271:         val decision = decisionForState(state)
272:             ?: return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount, state, reason)
273:         return ProviderOutcome(decision, null, requestCount, state, reason)
274:     }
275: 
276:     /** 当前发送策略；未知协议值不是供应商明确的 unknown 结果。 */
277:     private fun decisionForState(state: String?): String? = when (state?.lowercase(Locale.ROOT)) {
278:         STATE_DELIVERABLE, STATE_RISKY, STATE_UNKNOWN -> BatchEmailVerificationDecision.PASS
279:         STATE_UNDELIVERABLE -> BatchEmailVerificationDecision.SKIP
280:         else -> null
281:     }
282: 
283:     /**
284:      * I-5：只在 RAW/CANDIDATE/APPLICATION 中**已存在**且真实 ID、ORCID、当前邮箱三者都匹配的副本上
356:         const val EMAIL_ABNORMAL_TAG = "邮箱异常"
357: 
358:         const val TEST_KEY_PREFIX = "test_"
359: 
360:         const val HTTP_OK = 200
361:         const val HTTP_INCOMPLETE = 249
362:         const val HTTP_UNAUTHORIZED = 401
363:         const val HTTP_FORBIDDEN = 403
364:         const val HTTP_PAYMENT_REQUIRED = 402
365:         const val HTTP_TOO_MANY_REQUESTS = 429
366: 
367:         const val STATE_DELIVERABLE = "deliverable"
368:         const val STATE_UNDELIVERABLE = "undeliverable"
369:         const val STATE_RISKY = "risky"
370:         const val STATE_UNKNOWN = "unknown"
371: 
372:         const val MAX_ATTEMPTS_PER_EMAIL = 2
373: 
374:         private val REUSABLE_DECISIONS = setOf(
375:             BatchEmailVerificationDecision.PASS,
376:             BatchEmailVerificationDecision.SKIP
377:         )
378:     }
379: }
380: 
381: /** I-2：验证键 = 最终收件地址的规范化形式（trim + lowercase(Locale.ROOT)；不删 `+tag`、不合并点号）。 */
382: fun normalizeVerificationEmail(email: String?): String = email?.trim()?.lowercase(Locale.ROOT) ?: ""
391: 
392: /** 单个目标的验证结论；列表顺序即引擎的处理顺序。 */
393: sealed class VerificationResult {
394:     /** 通过：只有放行目标会带到这里，行内决策已是 PASS。 */
395:     data class Passed(val rowId: Long, val normalizedEmail: String) : VerificationResult()
396: 
397:     /** 明确非通过：行内决策已是 SKIP，邮件不发，标签结果为 [tagStatus]。 */
398:     data class Rejected(val rowId: Long, val tagStatus: String) : VerificationResult()
399: 
400:     /** 服务故障：行内决策已是 ERROR + [errorCode]，调用方必须停止本次执行且不追加「邮箱异常」。 */
401:     data class ServiceFailure(val rowId: Long, val errorCode: String) : VerificationResult()
402: 
403:     /** 等待中取消：未验证者不标记；[rowId] 为已插入的明细行（可为空）。 */
404:     data class Cancelled(val rowId: Long?) : VerificationResult()
405: }
462:         private const val NANOS_PER_MILLI = 1_000_000L
463:         private const val CANCEL_POLL_MILLIS = 50L
464: 
465:         /** I-7：相邻物理请求开始间隔至少 100ms（无并发池时的成本/速率保护）。 */
466:         const val MIN_REQUEST_SPACING_MILLIS = 100L
467: 
468:         /** I-4：249 只再试一次，间隔 500ms。 */
469:         const val RETRY_DELAY_MILLIS = 500L
470: 
471:         private const val MIN_REQUEST_SPACING_NANOS = MIN_REQUEST_SPACING_MILLIS * NANOS_PER_MILLI
472:         private const val RETRY_DELAY_NANOS = RETRY_DELAY_MILLIS * NANOS_PER_MILLI
473:     }
474: }
475: 
476: /** HTTP 测试接缝：单次物理请求；网络/超时异常原样抛出，由服务按受控码分类。 */
477: interface EmailableVerifyClient {
481: /** 单次请求的全部输入；[apiKey] 只允许进 Authorization 头（I-8）。 */
482: data class EmailableVerifyRequest(
483:     val normalizedEmail: String,
484:     val apiKey: String
485: )
486: 
487: /** 单次响应：状态码 + 原始正文（正文只用于解析受控字段，不落库、不整体进日志）。 */
488: data class EmailableHttpResponse(
489:     val statusCode: Int,
490:     val body: String
491: )
492: 
493: /**
494:  * I-8：目标地址与鉴权头构造的唯一位置 —— 固定 `https://api.emailable.com/v1/verify`，
495:  * 邮箱按 RFC 3986 百分号编码（`+` 不会变成空格），密钥只进 Authorization。
496:  * 目标地址不可由前端或配置改写，故这里是常量而非配置项。
497:  */
498: object EmailableRequestFactory {
499:     const val BASE_URL = "https://api.emailable.com"
500:     const val VERIFY_PATH = "/v1/verify"
501:     const val PARAM_SMTP = "smtp=true"
502:     const val PARAM_ACCEPT_ALL = "accept_all=true"
503:     const val PARAM_TIMEOUT = "timeout=10"
504: 
505:     fun verifyUri(normalizedEmail: String): URI =
506:         URI.create("$BASE_URL$VERIFY_PATH?email=${percentEncode(normalizedEmail)}&$PARAM_SMTP&$PARAM_ACCEPT_ALL&$PARAM_TIMEOUT")
507: 
508:     fun authorizationHeader(apiKey: String): String = "Bearer $apiKey"
509: 
510:     /** 只保留 RFC 3986 unreserved 字符，其余按 UTF-8 字节百分号编码。 */
511:     internal fun percentEncode(value: String): String = buildString {
512:         for (byte in value.toByteArray(StandardCharsets.UTF_8)) {
513:             val code = byte.toInt() and 0xFF
514:             val char = code.toChar()
515:             if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '-' || char == '.' || char == '_' || char == '~') {
516:                 append(char)
517:             } else {
518:                 append('%')
519:                 append(HEX_DIGITS[code shr 4])
520:                 append(HEX_DIGITS[code and 0x0F])
521:             }
522:         }
523:     }
524: 
525:     private const val HEX_DIGITS = "0123456789ABCDEF"
526: }
527: 
528: /** 生产 HTTP 客户端：JDK11 HttpClient，连接超时 3s、单请求超时 12s，禁止重定向。 */
529: @Component
530: class EmailableHttpVerifyClient : EmailableVerifyClient {
531:     private val httpClient: HttpClient = HttpClient.newBuilder()
532:         .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS))
533:         .followRedirects(HttpClient.Redirect.NEVER)
534:         .build()
535: 
536:     override fun postVerify(request: EmailableVerifyRequest): EmailableHttpResponse {
537:         val httpRequest = HttpRequest.newBuilder()
538:             .uri(EmailableRequestFactory.verifyUri(request.normalizedEmail))
539:             .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MILLIS))
540:             .header("Authorization", EmailableRequestFactory.authorizationHeader(request.apiKey))
541:             .header("Accept", "application/json")
542:             .GET()
543:             .build()
544:         val response = try {
545:             httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
546:         } catch (e: InterruptedException) {
547:             Thread.currentThread().interrupt()
548:             throw IOException("email verification request interrupted", e)
549:         }
550:         return EmailableHttpResponse(response.statusCode(), response.body().orEmpty())
551:     }
552: 
553:     companion object {
554:         const val CONNECT_TIMEOUT_MILLIS = 3_000L
555:         const val REQUEST_TIMEOUT_MILLIS = 12_000L
556:     }
557: }
558: 
559: /**
560:  * I-6：审计不可用（仓储失败、无法写入的输入）必须显式让调用方停止发送。
561:  * 继承 IllegalStateException：业务异常按项目约定映射，不会被误当 SMTP 故障统计。
562:  */
563: class EmailVerificationAuditException(message: String, cause: Throwable? = null) :
564:     IllegalStateException(message, cause)
565: 
566: private fun verificationNow(): LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
```

## E-06 预估执行及停止

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`；读取命令等价于 `nl -ba src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` 后按列出的行段截取。

```text
479:     fun countBySnapshot(snapshot: BatchExecutionSnapshot): PendingOutreachSummary = when (snapshot.mailType) {
480:         BatchSendType.MATERIAL_REMINDER.name -> {
481:             val config = snapshot.toBatchSendConfig(BatchSendType.MATERIAL_REMINDER)
482:             val scope = resolveScope(snapshot)
483:             val materialSnapshot = buildMaterialReminderSnapshot(scope, config)
484:             PendingOutreachSummary(pending = materialSnapshot.targets.size, retryable = 0, totalSendable = materialSnapshot.targets.size)
485:         }
486:         else -> {
487:             val scope = resolveScope(snapshot)
488:             var retryable = 0
489:             val campaign = campaignRepository.findByCampaignCode("MANUAL_OUTREACH")
490:             if (campaign != null) {
491:                 val campaignId = campaign.id ?: error("Campaign ID is null")
492:                 val (retryableTargets, _) = buildRetryableTargets(campaignId, scope)
493:                 retryable = retryableTargets.size
494:             }
495:             val esEstimate = countEsTargets(scope)
496:             PendingOutreachSummary(pending = esEstimate, retryable = retryable, totalSendable = esEstimate + retryable)
497:         }
498:     }
499: 
530:         mode: ExecutionMode,
531:         oneRoundOnly: Boolean
532:     ): ManualOutreachResult {
533:         log.info("Starting scheduled batch outreach, executionId={}, mode={}, oneRoundOnly={}", executionId, mode, oneRoundOnly)
534:         val ignoreWarmup = mode == ExecutionMode.MANUAL
535:         val campaign = getOrCreateManualCampaign()
536:         val campaignId = campaign.id ?: error("Campaign ID is null")
537:         val config = snapshot.toBatchSendConfig(BatchSendType.INTRODUCTION)
538:         val scope = resolveScope(snapshot)
539:         // I-1/I-2: 本次执行的唯一发件账号范围快照。
540:         val allowedAccountCodes = allowedAccountCodesOf(snapshot)
541:         // I-1/I-2/I-7：验证开关与逐次执行上下文。关闭时不建立上下文 —— 零 HTTP、零验证明细仓储调用。
542:         val emailVerificationEnabled = snapshot.emailVerificationEnabled
543:         val verificationContext = if (emailVerificationEnabled) {
544:             batchEmailVerificationService.beginExecution(executionId) {
545:                 progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)
546:             }
547:         } else {
548:             null
549:         }
550:         val (retryableTargets, seenOrcids) = buildRetryableTargets(campaignId, scope)
551:         val esEstimate = countEsTargets(scope)
552:         val totalEstimate = retryableTargets.size + esEstimate
553:         log.info("Outreach targets: {} retryable, {} ES estimate, {} total estimate; config: roundSize={}, perMailMs={}, perRoundMs={}",
554:             retryableTargets.size, esEstimate, totalEstimate,
555:             config.roundSize, config.perMailIntervalMs, config.perRoundIntervalMs)
556: 
557:         if (totalEstimate == 0) {
558:             val emptyFinal = if (oneRoundOnly) "PAUSED" else "COMPLETED"
559:             val emptyReason = if (oneRoundOnly) "EMPTY_SNAPSHOT" else null
560:             val accumulator = OutcomeAccumulator(0)
561:             updateProgressWithAccumulator(executionId, accumulator, 0, 0,
562:                 emptyFinal, "没有需要发送的专家", emptyList(), mode, 0, config, emptyMap(),
563:                 stopReason = emptyReason, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
564:             return emptyResult(emptyFinal, emptyReason)
565:         }
566: 
567:         val targetIterator = OutreachTargetIterator(
568:             retryableTargets = retryableTargets,
569:             pageSize = config.roundSize * 2,
570:             seenOrcids = seenOrcids,
571:             fetchNextPage = { offset, size ->
572:                 fetchEsPage(scope, offset, size)
573:             }
574:         )
575: 
576:         val accumulator = OutcomeAccumulator(totalEstimate)
577:         var wasCancelled = false
578:         val errors = mutableListOf<String>()
579:         val assignments = mutableListOf<SenderExpertAssignment>()
637:             var roundRejected = 0
638:             var midRoundStop = false
639:             while (roundPassed < roundQuota && targetIterator.hasNext()) {
640:                 if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
641:                     wasCancelled = true
642:                     stopReason = "CANCELLED"
643:                     midRoundStop = true
644:                     break
645:                 }
646:                 val (existingContact, expert) = targetIterator.next()
647:                 val normOrcid = normalizeOrcid(expert.orcidId)
648: 
649:                 // I4-1/I4-4: 发送前最后门禁 —— 与 ES 查询、内存重试过滤共用同一份类型判定。
650:                 // 查询/缓存/未来重构错误可能绕过 ES 侧，创建 contact 前再判一次。
651:                 if (!scope.matchesExpertType(expert)) {
652:                     accumulator.recordSkipped(
653:                         BatchOutcomeReasonCodes.EXPERT_NOT_SENDABLE,
654:                         "研发类型不在本次选择范围内：${expert.orcidId}"
655:                     )
656:                     processedTotal++
657:                     roundProcessed++
658:                     roundRejected++
659:                     updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
660:                         "RUNNING", "研发类型不在本次选择范围内：${expert.orcidId}", errors, mode, roundNumber, config, runAccountStats,
661:                         roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
662:                     continue
663:                 }
664: 
665:                 val email = expert.email
666:                 if (email.isNullOrBlank() || emailSuppressionService.isSuppressed(email)) {
667:                     accumulator.recordSkipped(BatchOutcomeReasonCodes.SUPPRESSED, "已跳过抑制邮箱：${email ?: ""}")
668:                     processedTotal++
669:                     roundProcessed++
670:                     roundRejected++
671:                     updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
672:                         "RUNNING", "已跳过抑制邮箱：${email ?: ""}", errors, mode, roundNumber, config, runAccountStats,
673:                         roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
674:                     continue
675:                 }
676: 
677:                 // I-3: 任一 expert_contact 行已有绑定（与绑定值是否在选中集合无关）→ 本次批量不发信、
678:                 // 不重选号、不改绑。NEW 重试在目标构造时已过滤；ES 页在此发送前对同一 ORCID 重查。
679:                 if (existingContact?.boundSenderAccountCode != null || hasBoundContact(normOrcid)) {
680:                     accumulator.recordSkipped(
681:                         BatchOutcomeReasonCodes.BOUND_SENDER_ALREADY_SET,
682:                         "专家已绑定发件账号：${expert.orcidId}"
683:                     )
684:                     processedTotal++
685:                     roundProcessed++
686:                     roundRejected++
687:                     updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
688:                         "RUNNING", "已跳过已绑定发件账号：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
689:                         roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
690:                     continue
691:                 }
692: 
693:                 // ── I-2/I-3/I-6：发送前邮箱验证 ──
694:                 // 位于类型/抑制/已绑定门禁之后、选号之前：非通过目标不新建/绑定 contact、
695:                 // 不写 PREPARED、不调用 SMTP、不计账号发送量；通过目标仍走原全部门禁。
696:                 var verified: VerificationResult.Passed? = null
697:                 if (emailVerificationEnabled) {
698:                     // I-3：验证前检查取消 —— 取消后不再插入明细、不发请求、不建行。
699:                     if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
700:                         wasCancelled = true
701:                         stopReason = "CANCELLED"
702:                         midRoundStop = true
703:                         break
704:                     }
705:                     val outcome = try {
706:                         batchEmailVerificationService.verify(
707:                             verificationContext!!,
708:                             EmailVerificationTarget(
709:                                 expertDocId = expert.esDocId,
710:                                 orcidId = normOrcid,
711:                                 expertName = expert.displayName,
712:                                 email = email
713:                             )
714:                         )
715:                     } catch (e: EmailVerificationAuditException) {
716:                         log.error("Email verification audit unavailable for ORCID {}: {}", normOrcid, e.message)
717:                         errors.add("验证审计写入失败：${e.message.orEmpty().take(120)}")
718:                         stopReason = STOP_EMAIL_VERIFY_AUDIT_FAILED
719:                         finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
720:                         midRoundStop = true
721:                         break
722:                     }
723:                     when (outcome) {
724:                         is VerificationResult.Rejected -> {
725:                             accumulator.recordSkipped(
726:                                 BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED,
727:                                 "邮箱验证未通过：$email"
728:                             )
729:                             processedTotal++
730:                             roundProcessed++
731:                             roundRejected++
732:                             updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
733:                                 "RUNNING", "已跳过邮箱验证未通过：$email", errors, mode, roundNumber, config, runAccountStats,
734:                                 roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
735:                             continue
736:                         }
737:                         is VerificationResult.ServiceFailure -> {
738:                             // I-4：服务故障停止本次执行，不批量标坏邮箱、不伪增 SMTP 失败/跳过。
739:                             log.warn("Email verification service failure for ORCID {}: {}", normOrcid, outcome.errorCode)
740:                             errors.add("邮箱验证服务故障：${outcome.errorCode}（$email）")
741:                             stopReason = outcome.errorCode
742:                             finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
743:                             midRoundStop = true
744:                             break
745:                         }
746:                         is VerificationResult.Cancelled -> {
747:                             outcome.rowId?.let {
748:                                 recordVerificationSendQuietly(it, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.CANCELLED)
749:                             }
750:                             wasCancelled = true
751:                             stopReason = "CANCELLED"
752:                             midRoundStop = true
753:                             break
754:                         }
755:                         is VerificationResult.Passed -> verified = outcome
756:                     }
1039:                 if (intervalMs > 0 && roundPassed < roundQuota && targetIterator.hasNext()) {
1040:                     try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
1041:                 }
1042:             }
1043: 
1044:             if (midRoundStop) break
1045: 
1046:             // 5. Round end progress (I-8)
1047:             updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
1048:                 "RUNNING", "第${roundNumber}轮完成，已发送 ${accumulator.success} 封", errors, mode, roundNumber, config, runAccountStats,
1049:                 roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
1050: 
1051:             // 6. oneRoundOnly (manual button) — return after one round (L3-2: back to PAUSED)
1052:             if (oneRoundOnly) {
1053:                 log.info("oneRoundOnly=true, returning after round {}", roundNumber)
1054:                 stopReason = "ONE_ROUND_DONE"
1055:                 finalStatus = "PAUSED"
1056:                 break
1057:             }
1058: 
1059:             // 7. Round interval (I-6) — skip when the roundsPerRun budget is already spent
1060:             if (config.perRoundIntervalMs > 0 && targetIterator.hasNext() && roundNumber < snapshot.roundsPerRun) {
1061:                 try { Thread.sleep(config.perRoundIntervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
1062:             }
1063:         }
1064: 
1065:         // Resolve final status
1066:         val resolvedFinalStatus = when {
1067:             wasCancelled -> "CANCELLED"
1068:             finalStatus != null -> finalStatus
1069:             else -> "COMPLETED"
1070:         }
1071:         val finalMessage = stopReasonMessage(resolvedFinalStatus, stopReason, ignoreWarmup, allowedAccountCodes)
1072:         updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
1073:             resolvedFinalStatus, finalMessage, errors, mode, roundNumber, config, runAccountStats,
1074:             stopReason = stopReason, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
1075: 
1076:         return buildResult(totalEstimate, accumulator, wasCancelled, resolvedFinalStatus, stopReason)
1077:     }
1118:         "WARMUP_LIMIT_REACHED" -> "已达到预热上限，今日暂停发送"
1119:         "DAILY_LIMIT_REACHED" -> if (hasWarmupLimitedAccounts(ignoreWarmup, allowedAccountCodes)) {
1120:             "已达到今日发送上限（含预热账号）"
1121:         } else {
1122:             "已达到今日发送上限"
1123:         }
1124:         "NO_AVAILABLE_ACCOUNT" -> "批量发送已暂停：无可用邮箱账号，请检查并恢复账号。"
1125:         "ROUNDS_PER_RUN_REACHED" -> "本次调度轮次已用完"
1126:         "ONE_ROUND_DONE" -> "手动单轮发送已完成"
1127:         "CANCELLED" -> "发送任务已被取消"
1128:         // I-4/I-6：发送前验证的受控停止原因（服务故障/审计不可用/状态冲突），文案按受控错误码给出。
1129:         BatchEmailVerificationErrorCodes.AUTH_ERROR -> "邮箱验证服务鉴权失败，已停止本次发送"
1130:         BatchEmailVerificationErrorCodes.NO_CREDITS -> "邮箱验证服务额度不足，已停止本次发送"
1131:         BatchEmailVerificationErrorCodes.RATE_LIMITED -> "邮箱验证服务限流，已停止本次发送"
1132:         BatchEmailVerificationErrorCodes.TIMEOUT -> "邮箱验证服务超时，已停止本次发送"
1133:         BatchEmailVerificationErrorCodes.INCOMPLETE -> "邮箱验证服务未返回结果，已停止本次发送"
1134:         BatchEmailVerificationErrorCodes.BAD_RESPONSE -> "邮箱验证服务返回非法响应，已停止本次发送"
1135:         BatchEmailVerificationErrorCodes.SERVICE_ERROR -> "邮箱验证服务故障，已停止本次发送"
1136:         STOP_EMAIL_VERIFY_AUDIT_FAILED -> "邮箱验证明细写入失败，已停止本次发送"
1137:         STOP_EMAIL_VERIFY_SEND_STATE_CONFLICT -> "邮箱验证明细状态冲突，已停止本次发送"
1138:         SEND_REASON_EMAIL_CHANGED -> "收件地址在验证后发生变化，已停止本次发送"
1139:         else -> when (finalStatus) {
1140:             "PAUSED" -> "流程已暂停: ${stopReason ?: ""}"
1141:             "FAILED" -> "发送任务失败: ${stopReason ?: ""}"
1142:             "COMPLETED" -> "发送任务已完成"
1143:             else -> "发送任务结束"
1144:         }
1145:     }
1146: 
1147:     private fun hasWarmupLimitedAccounts(
1164:     private fun recordVerificationSend(verified: VerificationResult.Passed?, sendStatus: String, sendReason: String?) {
1165:         val passed = verified ?: return
1166:         batchEmailVerificationService.recordSend(passed.rowId, sendStatus, sendReason)
1167:     }
1168: 
1169:     /** I-6：调用方已经在停止时的收尾（取消/无可用账号/系统错误）；写失败只记日志，行保持默认 NOT_SENT。 */
1170:     private fun recordVerificationSendQuietly(rowId: Long, sendStatus: String, sendReason: String?) {
1171:         try {
1172:             batchEmailVerificationService.recordSend(rowId, sendStatus, sendReason)
1173:         } catch (e: EmailVerificationAuditException) {
1174:             log.warn("Email verification trailing audit write failed for row {}: {}", rowId, e.message)
1175:         }
1176:     }
1177: 
1178:     private fun buildSmtpErrorSummary(delivered: DeliveredMail): String =
1179:         buildString {
1180:             append(delivered.errorCategory.name)
1181:             delivered.smtpResponseCode?.let { append(":$it") }
1182:             delivered.errorDetail?.let { append(":${it.take(200)}") }
1183:         }
1751: data class PendingOutreachSummary(
1752:     val pending: Int,
1753:     val retryable: Int,
1754:     val totalSendable: Int
1755: )
1756: 
1757: data class ManualOutreachResult(
1758:     val total: Int,
1759:     val sent: Int,
1760:     val failed: Int,
1761:     val skippedNoAccount: Int,
1762:     val wasCancelled: Boolean,
1763:     val finalStatus: String? = null,
1764:     val stopReason: String? = null,
1765:     val remaining: Int = 0,
1766:     val skipped: Int = 0,
1767:     val outcome: OutcomeBreakdown? = null
1768: ) : TaskExecutionSummaryProvider {
1769:     override val taskSuccessCount: Int get() = sent
1770:     override val taskFailureCount: Int get() = failed
1771:     override val taskFinalStatus: String? get() = when {
1772:         wasCancelled -> "CANCELLED"
1773:         failed > 0 && sent > 0 -> "PARTIAL_SUCCESS"
1774:         failed > 0 -> "FAILED"
1775:         else -> finalStatus?.takeIf { it in setOf("FAILED", "CANCELLED", "PARTIAL_SUCCESS") } ?: "SUCCESS"
1776:     }
1777: }
1778: 
1779: /** Per-account run stats tracked during a single run (not persisted). */
1780: class AccountRunStat {
```

## E-07 双来源与材料邮箱

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`；读取命令等价于 `nl -ba src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` 后按列出的行段截取。

```text
1270:      * Retry path applies the same [RecipientScope] as ES (I-3 / K-batch-send-filter-retry-parity).
1271:      * Profiles are loaded from every funnel level in scope (not hard-coded CANDIDATE).
1272:      */
1273:     private fun buildRetryableTargets(
1274:         campaignId: Long,
1275:         scope: RecipientScope
1276:     ): Pair<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>> {
1277:         val seenOrcids = mutableSetOf<String>()
1278:         val targets = mutableListOf<Pair<ExpertContact?, ExpertProfile>>()
1279: 
1280:         val newContacts = expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
1281:         if (newContacts.isNotEmpty()) {
1282:             val retryableContacts = newContacts.filter {
1283:                 !hasSentIntroduction(it.id!!) && it.operatorStatus != "EMAIL_INVALID"
1284:             }
1285:             val orcidIds = retryableContacts.map { it.orcidId }
1286:             // I-3/I-4: 预估与执行共用本构造函数 —— 同一 ORCID 的任一 campaign 行已绑定即排除。
1287:             val boundOrcids = boundOrcidsOf(orcidIds)
1288:             val profilesByLevel = if (orcidIds.isEmpty()) {
1289:                 emptyMap()
1290:             } else {
1291:                 scope.funnelLevels.associateWith { level ->
1292:                     expertSearchService.searchByOrcidIds(orcidIds, ExpertIndexLevel.valueOf(level))
1293:                         .associateBy { normalizeOrcid(it.orcidId) }
1294:                 }
1295:             }
1296:             for (contact in retryableContacts) {
1297:                 val normOrcid = normalizeOrcid(contact.orcidId)
1298:                 if (normOrcid in boundOrcids) continue
1299:                 val profile = scope.funnelLevels.asSequence()
1300:                     .mapNotNull { level -> profilesByLevel[level]?.get(normOrcid) }
1301:                     .firstOrNull() ?: continue
1302:                 if (!scope.matchesExpert(profile)) continue
1303:                 if (seenOrcids.add(normOrcid)) {
1304:                     targets.add(Pair(contact, profile))
1305:                 }
1306:             }
1307:         }
1308: 
1309:         log.info("Retryable targets: {} (funnelLevels={})", targets.size, scope.funnelLevels)
1310:         return Pair(targets, seenOrcids)
1311:     }
1312: 
1451:     private fun buildMaterialReminderSnapshot(scope: RecipientScope, config: BatchSendConfig): MaterialReminderSnapshot {
1452:         val scopeDescription = scope.funnelLevels.joinToString("+") + " + tags=${scope.tags}" +
1453:             (scope.emailDomains.takeIf { it.isNotEmpty() }?.let { " + domains=" + it.joinToString(",") } ?: "") +
1454:             (scope.discipline?.let { " + discipline=$it" } ?: "")
1455:         return buildMaterialReminderSnapshotFromScope(scope, scopeDescription)
1456:     }
1457: 
1458:     private fun buildMaterialReminderSnapshot(config: BatchSendConfig): MaterialReminderSnapshot {
1459:         val scope = RecipientScope(
1460:             mailType = BatchSendType.MATERIAL_REMINDER.name,
1461:             funnelLevels = setOf("APPLICATION"),
1462:             tags = listOf("承诺回复材料"),
1463:             // 统计路径输入为 BatchSendConfig（KV 层，无地区维度），故不携带地区；发送路径经 fromSnapshot 携带
1464:             regions = emptyList(),
1465:             emailDomains = config.emailDomain.ifBlank { null }?.let { listOf(it) } ?: emptyList(),
1466:             discipline = config.discipline.ifBlank { null }
1467:         )
1468:         val scopeDescription = "APPLICATION + tag=承诺回复材料 + email" +
1469:             (scope.emailDomains.takeIf { it.isNotEmpty() }?.let { " + domains=" + it.joinToString(",") } ?: "") +
1470:             (if (config.discipline.isNotBlank()) " + discipline=${config.discipline}" else "")
1471:         return buildMaterialReminderSnapshotFromScope(scope, scopeDescription)
1472:     }
1473: 
1474:     /** Material targets honor [RecipientScope.funnelLevels] (I-3), not a hard-coded APPLICATION-only search. */
1475:     private fun buildMaterialReminderSnapshotFromScope(
1476:         scope: RecipientScope,
1477:         scopeDescription: String
1478:     ): MaterialReminderSnapshot {
1479:         var totalHits = 0L
1480:         val allExperts = mutableListOf<ExpertProfile>()
1481:         val pageSize = 1000
1482:         for (level in scope.funnelLevels) {
1483:             val filters = buildEsFiltersForLevel(scope, level)
1484:             val levelHits = expertSearchService.countExperts(ExpertIndexLevel.valueOf(level), filters)
1485:             totalHits += levelHits
1486:             if (totalHits > 10000) {
1487:                 throw IllegalStateException(
1488:                     "材料提醒目标数 ($totalHits) 超过 10000 上限，请缩小过滤范围后再发送"
1489:                 )
1490:             }
1491:             var offset = 0
1492:             while (offset < levelHits) {
1493:                 val page = expertSearchService.searchExpertsFiltered(
1494:                     level = ExpertIndexLevel.valueOf(level),
1495:                     filters = filters,
1496:                     from = offset,
1497:                     size = pageSize
1498:                 )
1499:                 if (page.isEmpty()) break
1500:                 allExperts.addAll(page)
1501:                 offset += page.size
1502:             }
1503:         }
1504:         if (totalHits > 10000) {
1505:             throw IllegalStateException(
1506:                 "材料提醒目标数 ($totalHits) 超过 10000 上限，请缩小过滤范围后再发送"
1507:             )
1508:         }
1509: 
1510:         // Step 3: normalize ORCIDs and bulk-load contacts (K-es-tag-to-mail-cross-store-join)
1511:         val normalizedExperts = allExperts.map { normalizeOrcid(it.orcidId) to it }
1512:         val normOrcidList = normalizedExperts.map { it.first }.distinct()
1513:         val contacts = if (normOrcidList.isNotEmpty()) expertContactRepository.findByOrcidIdIn(normOrcidList) else emptyList()
1514:         val contactByNormOrcid = contacts.associateBy { normalizeOrcid(it.orcidId) }
1515:         // I-3/I-4: 预估与执行共用本构造函数 —— 同一 ORCID 的任一行已绑定即排除（材料目标全绑定时可为 0）。
1516:         val boundOrcids = contacts
1517:             .filter { !it.boundSenderAccountCode.isNullOrBlank() }
1518:             .map { normalizeOrcid(it.orcidId) }
1519:             .toSet()
1520: 
1521:         // Step 4: apply exclusion rules, dedup by contactId
1522:         val seenContactIds = mutableSetOf<Long>()
1523:         val sendableTargets = mutableListOf<Pair<ExpertContact, ExpertProfile>>()
1524: 
1525:         for ((normOrcid, expert) in normalizedExperts) {
1526:             if (normOrcid in boundOrcids) continue             // exclude: already bound to a sender account (I-3)
1527:             val contact = contactByNormOrcid[normOrcid] ?: continue  // exclude: no existing contact
1528:             val contactId = contact.id ?: continue
1529:             if (!seenContactIds.add(contactId)) continue              // dedup by contactId
1530:             val email = contact.expertEmail
1531:             if (email.isBlank()) continue                             // exclude: empty email
1532:             if (emailSuppressionService.isSuppressed(email)) continue  // exclude: suppressed
1533:             if (hasSentMaterialReminder(contactId)) continue          // exclude: already SENT (I-6)
1534:             sendableTargets.add(Pair(contact, expert))
1535:         }
1536: 
1537:         return MaterialReminderSnapshot(targets = sendableTargets, totalEsHits = totalHits, scopeDescription = scopeDescription)
1538:     }
1539: 
1540:     private fun countEsTargets(scope: RecipientScope): Int {
1541:         var total = 0
1542:         for (level in scope.funnelLevels) {
1543:             val filters = buildEsFiltersForLevel(scope, level)
1544:             total += expertSearchService.countExperts(ExpertIndexLevel.valueOf(level), filters).toInt()
1545:         }
1546:         return total
1547:     }
1548: 
1549:     private fun fetchEsPage(scope: RecipientScope, offset: Int, size: Int): List<ExpertProfile> {
1550:         val results = mutableListOf<ExpertProfile>()
1551:         var remaining = size
1552:         var pageOffset = offset
1553:         for (level in scope.funnelLevels) {
1554:             if (remaining <= 0) break
1555:             val filters = buildEsFiltersForLevel(scope, level)
1556:             val levelCount = expertSearchService.countExperts(ExpertIndexLevel.valueOf(level), filters).toInt()
1557:             if (pageOffset >= levelCount) {
1558:                 pageOffset -= levelCount
1559:                 continue
1560:             }
1561:             val page = expertSearchService.searchExpertsFiltered(
1562:                 level = ExpertIndexLevel.valueOf(level),
1563:                 filters = filters,
1564:                 from = pageOffset,
1565:                 size = remaining
1566:             )
1567:             // 必须返回原始页：迭代器按原始长度判断末页，并统一处理 seenOrcids 去重。
1568:             // 先去重会把仍含后续候选的完整页误判成末页，导致未补足轮次额度就停止。
1569:             results.addAll(page)
1570:             remaining -= page.size
1571:             pageOffset = 0
1572:         }
1573:         return results
1574:     }
1575: 
1576:     private fun buildEsFiltersForLevel(scope: RecipientScope, level: String): List<Map<String, Any>> {
1577:         // I3a-4: 判据从「等于 NOT_CONTACTED」变为「是否含非 NOT_CONTACTED 值」。
1578:         // 空集合 或 仅含 NOT_CONTACTED  → 保持 notContacted 基座（N3a-2 逐字不变）。
1579:         val statuses = scope.operatorStatuses
1580:         val onlyNotContacted = statuses.isEmpty() || statuses.all { it == "NOT_CONTACTED" }
1581:         val filters = if (scope.mailType == BatchSendType.INTRODUCTION.name && level == "CANDIDATE" && onlyNotContacted) {
1582:             ExpertSearchService.notContactedWithEmailDomainsFilters(scope.emailDomains, scope.discipline).toMutableList()
1583:         } else {
1584:             // I3a-4: 含任一非 NOT_CONTACTED 状态时必须换成状态无关基座 —— notContacted 基座
1585:             // 自带 must_not exists operatorStatus，与 term 状态并存恒为空。
1586:             val base = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
1587:             ExpertSearchService.emailDomainsFilter(scope.emailDomains)?.let { base.add(it) }
1588:             scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }
1589:             // I3a-3: 空集合返回 null，不追加任何状态 filter。
1590:             ExpertSearchService.operatorStatusesFilter(statuses)?.let { base.add(it) }
1591:             base
1592:         }
1593:         if (scope.tags.isNotEmpty()) {
1594:             filters.add(mapOf("terms" to mapOf("tags" to scope.tags)))
1595:         }
1596:         ExpertSearchService.regionsFilter(scope.regions)?.let { filters.add(it) }
1597:         // I-2: 方向三态 —— ABSENT 是既有 PRESENT 存在性 filter 的 bool.must_not；
1598:         // ANY 不追加任何项（旧任务人群不变）。与模板门禁字段/研发类型平铺为 AND（I-3）。
1599:         when (scope.researchDirectionFilter) {
1600:             ResearchDirectionFilters.PRESENT ->
1601:                 filters.add(ExpertSearchService.fieldPresenceFilter(ResearchDirectionFilters.ES_FIELD))
1602:             ResearchDirectionFilters.ABSENT ->
1603:                 filters.add(
1604:                     mapOf(
1605:                         "bool" to mapOf(
1606:                             "must_not" to listOf(
1607:                                 ExpertSearchService.fieldPresenceFilter(ResearchDirectionFilters.ES_FIELD)
1608:                             )
1609:                         )
1610:                     )
1611:                 )
1612:         }
1613:         // I4a-2: 门禁字段之间 AND —— 平铺进 filter 数组，不用 should。
1614:         // I4a-1: 空集合时 fieldPresenceFilters 返回空列表，不追加任何项。
1615:         filters.addAll(ExpertSearchService.fieldPresenceFilters(scope.gateEsFields))
1616:         // I4-1: INTRODUCTION 的唯一收口点 —— 只按研发类型集合判定，无第二个门禁。
1617:         // I4-2: 空集合 = 发给零个人（fail-closed），不是"不限"。
1618:         if (scope.mailType == BatchSendType.INTRODUCTION.name) {
1619:             filters.add(
1620:                 ExpertSearchService.expertTypesFilter(scope.expertTypes)
1621:                     ?: ExpertSearchService.MATCH_NONE_FILTER
1622:             )
1623:         }
1624:         return filters
1625:     }
1626: 
288:                 val (contact, expert) = targets[targetIndex]
289:                 targetIndex++
290: 
291:                 val contactId = contact.id!!
292:                 val email = contact.expertEmail
293:                 val normOrcid = normalizeOrcid(expert.orcidId)
294: 
295:                 if (email.isBlank() || emailSuppressionService.isSuppressed(email)) {
296:                     accumulator.recordSkipped(BatchOutcomeReasonCodes.SUPPRESSED, "已跳过抑制邮箱：$email")
```

## E-08 分页迭代器

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt`；读取命令等价于 `nl -ba src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt` 后按列出的行段截取。

```text
1: package com.weibo.talentintroduction.campaign.service
2: 
3: import com.weibo.talentintroduction.campaign.domain.ExpertContact
4: import com.weibo.talentintroduction.expert.domain.ExpertProfile
5: import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
6: 
7: /**
8:  * Lazy iterator: retryable contacts first, then ES candidates fetched page by page.
9:  * [seenOrcids] is shared for the full iteration lifecycle (R-3 dedup equivalence).
10:  */
11: class OutreachTargetIterator(
12:     retryableTargets: List<Pair<ExpertContact?, ExpertProfile>>,
13:     private val pageSize: Int,
14:     private val seenOrcids: MutableSet<String>,
15:     private val fetchNextPage: (offset: Int, size: Int) -> List<ExpertProfile>
16: ) : Iterator<Pair<ExpertContact?, ExpertProfile>> {
17: 
18:     private val retryableIterator = retryableTargets.iterator()
19:     private var esBuffer: MutableList<Pair<ExpertContact?, ExpertProfile>> = mutableListOf()
20:     private var esBufferIndex = 0
21:     private var esOffset = 0
22:     private var esExhausted = false
23: 
24:     override fun hasNext(): Boolean {
25:         if (retryableIterator.hasNext()) return true
26:         if (esBufferIndex < esBuffer.size) return true
27:         if (esExhausted) return false
28:         loadNextEsPage()
29:         return esBufferIndex < esBuffer.size
30:     }
31: 
32:     override fun next(): Pair<ExpertContact?, ExpertProfile> {
33:         if (retryableIterator.hasNext()) return retryableIterator.next()
34:         if (esBufferIndex >= esBuffer.size && !esExhausted) loadNextEsPage()
35:         if (esBufferIndex >= esBuffer.size) throw NoSuchElementException()
36:         return esBuffer[esBufferIndex++]
37:     }
38: 
39:     private fun loadNextEsPage() {
40:         while (!esExhausted) {
41:             val page = fetchNextPage(esOffset, pageSize)
42:             if (page.size < pageSize) esExhausted = true
43:             if (page.isEmpty()) return
44: 
45:             esBuffer = mutableListOf()
46:             esBufferIndex = 0
47:             for (expert in page) {
48:                 val normOrcid = ExpertIdNormalizer.normalize(expert.orcidId)
49:                 if (seenOrcids.add(normOrcid)) {
50:                     esBuffer.add(Pair(null, expert))
51:                 }
52:             }
53: 
54:             if (esBuffer.isNotEmpty()) {
55:                 esOffset = 0
56:                 return
57:             }
58: 
59:             esOffset += page.size
60:         }
61:     }
62: }
```

## E-09 ES已有查询工具

文件：`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`；读取命令等价于 `nl -ba src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` 后按列出的行段截取。

```text
660:     fun searchExpertsFiltered(
661:         level: ExpertIndexLevel,
662:         filters: List<Map<String, Any>>,
663:         from: Int,
664:         size: Int
665:     ): List<ExpertProfile> {
666:         require(size in 1..1000) { "size must be between 1 and 1000" }
667:         require(from >= 0) { "from must be >= 0" }
668: 
669:         val query = if (filters.isEmpty()) {
670:             mapOf("match_all" to emptyMap<String, Any>())
671:         } else {
672:             mapOf("bool" to mapOf("filter" to filters))
673:         }
674: 
675:         val requestBody = mapOf(
676:             "from" to from,
677:             "size" to size,
678:             "_source" to sourceFields(),
679:             "query" to query,
680:             "sort" to sortFields(level)
681:         )
682: 
683:         val response = restTemplate.exchange(
684:             "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
685:             HttpMethod.POST,
686:             HttpEntity(requestBody, headers()),
687:             JsonNode::class.java
688:         ).body ?: return emptyList()
689: 
690:         return response.path("hits")
691:             .path("hits")
692:             .map { hit -> toExpertProfile(hit) }
693:     }
694: 
695:     fun countExperts(
696:         level: ExpertIndexLevel,
697:         filters: List<Map<String, Any>> = emptyList()
698:     ): Long {
699:         val query = if (filters.isEmpty()) {
700:             mapOf("match_all" to emptyMap<String, Any>())
701:         } else {
702:             mapOf("bool" to mapOf("filter" to filters))
703:         }
704:         val requestBody = mapOf("query" to query)
705:         val index = expertIndexService.indexName(level)
706:         val response = restTemplate.exchange(
707:             "${properties.baseUrl}/$index/_count",
708:             HttpMethod.POST,
709:             HttpEntity(requestBody, headers()),
710:             JsonNode::class.java
711:         ).body
712:         return response?.path("count")?.asLong(0L) ?: 0L
713:     }
714: 
715:     fun scrollExpertsFiltered(
716:         level: ExpertIndexLevel,
717:         filters: List<Map<String, Any>>,
718:         batchSize: Int = 500,
719:         handler: (List<ExpertProfile>) -> Boolean
720:     ) {
721:         val index = expertIndexService.indexName(level)
722:         var scrollId: String? = null
723: 
724:         try {
725:             val initialUrl = "${properties.baseUrl}/$index/_search?scroll=5m"
726:             val query = if (filters.isEmpty()) {
727:                 mapOf("match_all" to emptyMap<String, Any>())
728:             } else {
729:                 mapOf("bool" to mapOf("filter" to filters))
730:             }
731:             val requestBody = mapOf(
732:                 "size" to batchSize,
733:                 "_source" to sourceFields(),
734:                 "query" to query,
735:                 "sort" to listOf(mapOf("_doc" to "asc"))
736:             )
737:             var response = restTemplate.exchange(
738:                 initialUrl,
739:                 HttpMethod.POST,
740:                 HttpEntity(requestBody, headers()),
741:                 JsonNode::class.java
742:             ).body ?: return
743: 
744:             scrollId = response.path("_scroll_id").asText()
745:             val totalHits = response.path("hits").path("total").path("value").asLong(0)
746:             var batchNumber = 0
747: 
748:             do {
749:                 val hits = response.path("hits").path("hits")
750:                 if (hits.isEmpty) break
751: 
752:                 batchNumber++
753:                 val experts = hits.map { hit -> toExpertProfile(hit) }
754:                 val shouldContinue = handler(experts)
755:                 if (!shouldContinue) break
756:                 if (hits.size() < batchSize) break
757: 
758:                 response = restTemplate.exchange(
759:                     "${properties.baseUrl}/_search/scroll",
760:                     HttpMethod.POST,
761:                     HttpEntity(mapOf("scroll" to "5m", "scroll_id" to scrollId), headers()),
762:                     JsonNode::class.java
763:                 ).body ?: break
764: 
765:                 scrollId = response.path("_scroll_id").asText()
766:             } while (true)
767:         } finally {
768:             if (scrollId != null) {
769:                 try {
770:                     restTemplate.exchange(
771:                         "${properties.baseUrl}/_search/scroll",
772:                         HttpMethod.DELETE,
773:                         HttpEntity(mapOf("scroll_id" to scrollId), headers()),
774:                         JsonNode::class.java
775:                     )
776:                 } catch (_: Exception) {}
777:             }
778:         }
779:     }
780: 
781:     fun searchAfterExpertsFiltered(
782:         level: ExpertIndexLevel,
783:         filters: List<Map<String, Any>>,
784:         batchSize: Int = 500,
785:         handler: (List<ExpertProfile>) -> Boolean
786:     ) {
787:         val index = expertIndexService.indexName(level)
788:         var searchAfter: String? = null
789: 
790:         while (true) {
791:             val query = if (filters.isEmpty()) {
792:                 mapOf("match_all" to emptyMap<String, Any>())
793:             } else {
794:                 mapOf("bool" to mapOf("filter" to filters))
795:             }
796:             val requestBody = mutableMapOf<String, Any>(
797:                 "size" to batchSize,
798:                 "_source" to sourceFields(),
799:                 "query" to query,
800:                 "sort" to listOf(mapOf("orcidId" to "asc"))
801:             )
802:             if (searchAfter != null) {
803:                 requestBody["search_after"] = listOf(searchAfter)
804:             }
805: 
806:             val response = restTemplate.exchange(
807:                 "${properties.baseUrl}/$index/_search",
808:                 HttpMethod.POST,
809:                 HttpEntity(requestBody, headers()),
810:                 JsonNode::class.java
811:             ).body ?: break
812: 
813:             val hits = response.path("hits").path("hits")
814:             if (hits.isEmpty) break
815: 
816:             val experts = hits.map { hit -> toExpertProfile(hit) }
817:             if (!handler(experts)) break
818:             if (hits.size() < batchSize) break
819: 
820:             searchAfter = hits[hits.size() - 1].path("sort").get(0).asText()
821:         }
822:     }
823: 
824:     fun findByOrcidId(orcidId: String, level: ExpertIndexLevel): ExpertProfile? {
825:         require(orcidId.isNotBlank()) { "orcidId must not be blank" }
826: 
827:         val requestBody = mapOf(
828:             "size" to 1,
```

## E-10 快照和结果模型

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`；读取命令等价于 `nl -ba src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` 后按列出的行段截取。

```text
1: package com.weibo.talentintroduction.campaign.domain
2: 
3: import com.fasterxml.jackson.core.type.TypeReference
4: import com.fasterxml.jackson.databind.ObjectMapper
5: import com.weibo.talentintroduction.campaign.service.BatchSendType
6: import com.weibo.talentintroduction.expert.domain.ExpertProfile
7: import java.time.LocalDateTime
8: 
9: /** Immutable launch snapshot consumed once per execution (I-1). */
10: data class BatchExecutionSnapshot(
11:     val mailType: String,
12:     val roundSize: Int,
13:     val roundsPerRun: Int = 1,
14:     val perMailIntervalMs: Long,
15:     val perRoundIntervalMs: Long,
16:     val selfCheckTtlMinutes: Int,
17:     val funnelLevel: String? = null,
18:     val tags: List<String> = emptyList(),
19:     val regions: List<String> = emptyList(),
20:     val emailDomains: List<String> = emptyList(),
21:     val discipline: String? = null,
22:     val operatorStatuses: List<String> = emptyList(),
23:     val expertTypes: List<String> = emptyList(),
24:     /**
25:      * I-1/I-2: 本次执行唯一的发件账号范围快照（逻辑 `mail_sender_account.account_code`）。
26:      * `[]` = 不限（旧任务/未传字段）；非空 = 严格白名单，两发送循环与选号服务都只在此集合内运作。
27:      */
28:     val senderAccountCodes: List<String> = emptyList(),
29:     val templateId: Long? = null,
30:     val gateFilterEnabled: Boolean = false,
31:     /**
32:      * I-1/I-2: 研究方向三态（[ResearchDirectionFilters]）；默认 [ResearchDirectionFilters.ANY]。
33:      * 前端手动快照、`toExecutionSnapshot` 与 `RecipientScope.fromSnapshot` 逐字传递。
34:      */
35:     val researchDirectionFilter: String = ResearchDirectionFilters.ANY,
36:     val oneRoundOnly: Boolean = false,
37:     /**
38:      * I-1（快照）：发送前邮箱验证开关。旧 `request_payload` JSON 缺字段 = false（默认关闭）；
39:      * 只有 INTRODUCTION 允许 true；MATERIAL_REMINDER + true 在任何业务写入前拒绝。
40:      * 关闭时不调用验证 HTTP / 验证明细仓储，也不要求密钥。
41:      */
42:     val emailVerificationEnabled: Boolean = false
43: )
44: 
45: data class ManualBatchExecutionRequest(
46:     val sourceConfigId: Long? = null,
47:     val sourceUpdatedAt: LocalDateTime? = null,
48:     val snapshot: BatchExecutionSnapshot
49: )
50: 
51: data class ReasonCount(
52:     val label: String,
53:     val count: Int
54: )
55: 
56: data class OutcomeBreakdown(
57:     val target: Int,
58:     val success: Int,
59:     val failure: Int,
60:     val skipped: Int,
61:     val remaining: Int,
62:     val failureReasons: Map<String, ReasonCount> = emptyMap(),
63:     val skippedReasons: Map<String, ReasonCount> = emptyMap(),
64:     val errorSamples: List<String> = emptyList()
65: )
66: 
67: /** Unified recipient filter applied to ES and MySQL retry paths (I-3). */
68: data class RecipientScope(
69:     val mailType: String,
70:     val funnelLevels: Set<String>,
71:     val tags: List<String>,
72:     val regions: List<String>,
73:     val emailDomains: List<String>,
74:     val discipline: String?,
75:     val operatorStatuses: List<String> = emptyList(),
76:     /** I4-2: 研发类型收窄（INTRODUCTION 专属；空集合 = 发给零个人，fail-closed，见 [matchesExpertType]）。 */
77:     val expertTypes: List<String> = emptyList(),
78:     /** I4a-4: 已解析的门禁 ES 字段（ALLOWED_HAS_FIELDS 交集）；解析只发生在 resolveScope。 */
79:     val gateEsFields: List<String> = emptyList(),
80:     /**
81:      * I-2/I-3: 研究方向三态（[ResearchDirectionFilters]）。与 ES 侧同口径：
82:      * `PRESENT` 命中 `fieldPresenceFilter("researchFields")`（`exists AND NOT term ""`），
83:      * `ABSENT` 为其补集，`ANY` 不判定。与研发类型（`UNKNOWN`/`UNCLASSIFIED`）和模板门禁
84:      * 是彼此独立的 AND 维度（I-3）。
85:      */
86:     val researchDirectionFilter: String = ResearchDirectionFilters.ANY
87: ) {
88:     fun matchesExpert(profile: com.weibo.talentintroduction.expert.domain.ExpertProfile): Boolean {
89:         // I3a-5：与 ES 的 operatorStatusesFilter 同口径 —— 多状态取 OR；
90:         // NOT_CONTACTED = ES 文档无该字段（I3a-1）；空集合不判定（I3a-3）。
91:         if (operatorStatuses.isNotEmpty()) {
92:             val matched = operatorStatuses.any {
93:                 if (it == "NOT_CONTACTED") profile.operatorStatus.isNullOrBlank()
164:         fun fromSnapshot(snapshot: BatchExecutionSnapshot): RecipientScope {
165:             val levels = when (snapshot.funnelLevel?.trim()?.takeIf { it.isNotEmpty() }) {
166:                 null -> setOf("CANDIDATE", "APPLICATION")
167:                 "CANDIDATE" -> setOf("CANDIDATE")
168:                 "APPLICATION" -> setOf("APPLICATION")
169:                 else -> setOf(snapshot.funnelLevel)
170:             }
171:             return RecipientScope(
172:                 mailType = snapshot.mailType,
173:                 funnelLevels = levels,
174:                 tags = snapshot.tags,
175:                 regions = snapshot.regions,
176:                 // I2a-2 / I2a-5：trim、丢空、去重保序；空集合 = 不限。
177:                 emailDomains = snapshot.emailDomains.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
178:                 discipline = snapshot.discipline?.trim()?.takeIf { it.isNotEmpty() },
179:                 // I3a-3：trim、丢空、去重保序；空集合 = 不限。
180:                 operatorStatuses = snapshot.operatorStatuses.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
181:                 // I4-2：trim、丢空、去重保序；空集合在发信判定中 fail-closed（发给零个人）。
182:                 expertTypes = snapshot.expertTypes.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
183:                 // I-1/I-2：三态原样传递（空白/未传值归一为 ANY）；非法值已在校验层被拒。
184:                 researchDirectionFilter = ResearchDirectionFilters.normalize(snapshot.researchDirectionFilter)
185:             )
186:         }
187:     }
188: }
189: 
190: object BatchOutcomeReasonCodes {
191:     const val SEND_EXCEPTION = "SEND_EXCEPTION"
192:     const val TEMPLATE_RENDER_FAILED = "TEMPLATE_RENDER_FAILED"
193:     const val ACCOUNT_UNAVAILABLE = "ACCOUNT_UNAVAILABLE"
194:     const val SUPPRESSED = "SUPPRESSED"
195:     const val NO_CONTACT = "NO_CONTACT"
196:     const val DEDUP = "DEDUP"
197:     const val DAILY_CAP_EXCEEDED = "DAILY_CAP_EXCEEDED"
198:     const val CANCELLED = "CANCELLED"
199:     const val PERSONALIZATION_INCOMPLETE = "PERSONALIZATION_INCOMPLETE"
200:     const val EXPERT_NOT_SENDABLE = "EXPERT_NOT_SENDABLE"
201:     /**
202:      * I-3/I-4: 目标专家已有任一 `expert_contact.bound_sender_account_code`（与绑定值是否在
203:      * 本次选中集合无关）→ 本次批量任务跳过，不发信、不重选号、不改绑。
204:      */
205:     const val BOUND_SENDER_ALREADY_SET = "BOUND_SENDER_ALREADY_SET"
206:     /**
207:      * I-2/I-6: 发送前验证明确不通过（undeliverable/risky/unknown）。
208:      * 该目标跳过并占本轮处理槽，但不计 success、不计发送失败、不占账号发送量；
209:      * 明细行的 send_status=SKIPPED 使用同一码。
210:      */
211:     const val EMAIL_VERIFICATION_REJECTED = "EMAIL_VERIFICATION_REJECTED"
212: 
213:     val LABELS = mapOf(
214:         SEND_EXCEPTION to "发送异常",
215:         TEMPLATE_RENDER_FAILED to "模板渲染失败",
216:         ACCOUNT_UNAVAILABLE to "邮箱账号不可用",
217:         SUPPRESSED to "退订/抑制",
218:         NO_CONTACT to "无联系人账号",
219:         DEDUP to "去重跳过",
220:         DAILY_CAP_EXCEEDED to "超日限额",
221:         CANCELLED to "被取消",
222:         PERSONALIZATION_INCOMPLETE to "个性化字段缺失",
223:         EXPERT_NOT_SENDABLE to "研发类型不在本次选择范围内",
224:         BOUND_SENDER_ALREADY_SET to "专家已绑定发件账号",
225:         EMAIL_VERIFICATION_REJECTED to "邮箱验证未通过"
226:     )
227: 
228:     fun label(code: String): String = LABELS[code] ?: code
229: }
230: 
303: fun BatchSendTaskConfig.toExecutionSnapshot(
304:     objectMapper: ObjectMapper,
305:     oneRoundOnly: Boolean = false
306: ): BatchExecutionSnapshot {
307:     val tags = try {
308:         objectMapper.readValue(tagsJson, object : TypeReference<List<String>>() {})
309:             .map { it.trim() }
310:             .filter { it.isNotEmpty() }
311:             .distinct()
312:     } catch (_: Exception) {
313:         emptyList()
314:     }
315:     val regions = try {
316:         objectMapper.readValue(regionsJson, object : TypeReference<List<String>>() {})
317:             .map { it.trim() }
318:             .filter { it.isNotEmpty() }
319:             .distinct()
320:     } catch (_: Exception) {
321:         emptyList()
322:     }
323:     // I2a-1/I2a-2: email_domains_json 是唯一事实源；解析失败按不限（空集合）处理。
324:     val emailDomains = try {
325:         objectMapper.readValue(emailDomainsJson, object : TypeReference<List<String>>() {})
326:             .map { it.trim() }
327:             .filter { it.isNotEmpty() }
328:             .distinct()
329:     } catch (_: Exception) {
330:         emptyList()
331:     }
332:     // I3a-1/I3a-3: operator_statuses_json 是唯一事实源；解析失败按不限（空集合）处理。
333:     val operatorStatuses = try {
334:         objectMapper.readValue(operatorStatusesJson, object : TypeReference<List<String>>() {})
335:             .map { it.trim() }
336:             .filter { it.isNotEmpty() }
337:             .distinct()
338:     } catch (_: Exception) {
339:         emptyList()
340:     }
341:     // I2-4: expert_types_json 是唯一事实源；解析失败按不限（空集合）处理。
342:     val expertTypes = try {
343:         objectMapper.readValue(expertTypesJson, object : TypeReference<List<String>>() {})
344:             .map { it.trim() }
345:             .filter { it.isNotEmpty() }
346:             .distinct()
347:     } catch (_: Exception) {
348:         emptyList()
349:     }
350:     // I-1: sender_account_codes_json 是唯一事实源，且是唯一**严格**解析的范围字段 ——
351:     // 坏 JSON 拒绝启动/读取，绝不降级为 []（否则白名单会被静默放宽成全池）。
352:     val senderAccountCodes = parseSenderAccountCodes(objectMapper, senderAccountCodesJson)
353:     return BatchExecutionSnapshot(
354:         mailType = mailType,
355:         roundSize = roundSize,
356:         roundsPerRun = roundsPerRun,
357:         perMailIntervalMs = perMailIntervalMs,
358:         perRoundIntervalMs = perRoundIntervalMs,
359:         selfCheckTtlMinutes = selfCheckTtlMinutes,
360:         funnelLevel = funnelLevel,
361:         tags = tags,
362:         regions = regions,
363:         emailDomains = emailDomains,
364:         discipline = discipline,
365:         operatorStatuses = operatorStatuses,
366:         expertTypes = expertTypes,
367:         senderAccountCodes = senderAccountCodes,
368:         templateId = templateId,
369:         gateFilterEnabled = gateFilterEnabled,
370:         researchDirectionFilter = researchDirectionFilter,
371:         oneRoundOnly = oneRoundOnly,
372:         // I-1/I-4: 配置实体是快照的唯一来源；启动时逐字复制，运行中改配置/软删不改本次快照。
373:         emailVerificationEnabled = emailVerificationEnabled
374:     )
375: }
```

## E-11 启动及runtime映射

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`；读取命令等价于 `nl -ba src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` 后按列出的行段截取。

```text
56:     /** Scheduled auto run for one config entity (I-2). */
57:     fun startScheduled(configId: Long): ResponseEntity<Map<String, Any>> {
58:         val config = batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(configId)
59:             ?: return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
60:                 .body(mapOf("message" to "配置不存在或已删除: $configId"))
61:         if (!config.autoEnabled) {
62:             return ResponseEntity.status(HttpStatus.CONFLICT)
63:                 .body(mapOf("message" to "自动定时发送未启用"))
64:         }
65:         val snapshot = config.toExecutionSnapshot(objectMapper)
66:         validateSnapshotFields(snapshot)?.let { return it }
67:         validateTemplateAtLaunch(snapshot.mailType, snapshot.templateId)?.let { return it }
68:         val request = ManualBatchExecutionRequest(
69:             sourceConfigId = configId,
70:             sourceUpdatedAt = config.updatedAt,
71:             snapshot = snapshot
72:         )
73:         return launchFromSnapshot(
74:             snapshot = snapshot,
75:             batchConfigId = configId,
76:             triggerType = "SCHEDULED",
77:             mode = ExecutionMode.AUTO,
78:             requestPayload = request
79:         )
80:     }
81: 
82:     /** Manual run from a full snapshot request (I-1). */
83:     fun startManual(request: ManualBatchExecutionRequest): ResponseEntity<Map<String, Any>> {
84:         validateSnapshotFields(request.snapshot)?.let { return it }
85:         validateTemplateAtLaunch(request.snapshot.mailType, request.snapshot.templateId)?.let { return it }
86:         val batchConfigId = request.sourceConfigId
87:         val capacityError = checkRemainingAccountCapacity()
88:         if (capacityError != null) return capacityError
89:         return launchFromSnapshot(
90:             snapshot = request.snapshot,
91:             batchConfigId = batchConfigId,
92:             triggerType = "MANUAL",
93:             mode = ExecutionMode.MANUAL,
94:             requestPayload = request,
95:             oneRoundOnly = request.snapshot.oneRoundOnly
96:         )
97:     }
98: 
99:     /** Manual run using the current persisted config row as snapshot (config list "手动"). */
100:     fun startManualFromConfig(configId: Long): ResponseEntity<Map<String, Any>> {
101:         val config = batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(configId)
102:             ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Batch send task config not found: $configId")
103:         val request = ManualBatchExecutionRequest(
104:             sourceConfigId = configId,
105:             sourceUpdatedAt = config.updatedAt,
106:             snapshot = config.toExecutionSnapshot(objectMapper)
107:         )
108:         return startManual(request)
109:     }
110: 
312:         batchConfigId: Long?,
313:         triggerType: String,
314:         mode: ExecutionMode,
315:         requestPayload: Any,
316:         oneRoundOnly: Boolean = snapshot.oneRoundOnly,
317:         legacySendType: BatchSendType? = null,
318:         manageRuntimeStatus: Boolean = false,
319:         returnToPausedAfterOneRound: Boolean = false
320:     ): ResponseEntity<Map<String, Any>> {
321:         val initialProgress = TaskProgress(
322:             taskType = TASK_TYPE,
323:             status = "RUNNING",
324:             batchNumber = 0,
325:             processedCount = 0,
326:             totalCount = 0,
327:             message = "正在初始化发送队列...",
328:             details = mapOf(
329:                 "executionMode" to mode.name,
330:                 "sendType" to snapshot.mailType,
331:                 "batchConfigId" to (batchConfigId ?: ""),
332:                 "sent" to 0,
333:                 "failed" to 0,
334:                 "accounts" to emptyList<AccountStatRow>()
335:             )
336:         )
337:         val (started, pendingToken) = progressStore.tryStartWithToken(TASK_TYPE, initialProgress)
338:         if (!started) {
339:             return ResponseEntity.status(HttpStatus.CONFLICT)
340:                 .body(mapOf("message" to "已有批量任务执行中"))
341:         }
342: 
343:         if (manageRuntimeStatus && legacySendType != null) {
344:             setRuntimeStatusInternal("RUNNING", mode.name, "", legacySendType)
345:         }
346: 
347:         val taskDescription = "batch-send-${snapshot.mailType}-${mode.name}${if (oneRoundOnly) "-one-round" else ""}"
348:         val executionIdFuture = CompletableFuture<Long>()
349: 
350:         try {
351:             manualOutreachExecutor.execute {
352:                 var executionId: Long? = null
353:                 try {
354:                     val (_, result) = taskExecutionService.runAndRecordWithResult<ManualOutreachResult>(
355:                         TASK_TYPE, triggerType, requestPayload,
356:                         onStarted = { id ->
357:                             executionId = id
358:                             executionIdFuture.complete(id)
359:                             progressStore.bindExecutionId(TASK_TYPE, pendingToken, id)
360:                         },
361:                         batchConfigId = batchConfigId
362:                     ) {
363:                         manualInitialOutreachService.run(
364:                             snapshot = snapshot,
365:                             executionId = executionId!!,
366:                             mode = mode,
367:                             oneRoundOnly = oneRoundOnly
368:                         )
369:                     }
370:                     if (manageRuntimeStatus && legacySendType != null) {
371:                         applyResultToRuntimeStatus(mode, result, returnToPausedAfterOneRound, legacySendType)
372:                     }
373:                 } catch (ex: Exception) {
374:                     executionIdFuture.completeExceptionally(ex)
375:                     log.error("Batch send execution failed for mailType={}", snapshot.mailType, ex)
376:                     if (manageRuntimeStatus && legacySendType != null) {
377:                         setRuntimeStatusInternal("PAUSED", mode.name, "EXECUTION_ERROR:${ex.message?.take(200)}", legacySendType)
378:                     }
379:                     progressStore.update(TASK_TYPE, TaskProgress(
380:                         taskType = TASK_TYPE, status = "FAILED",
381:                         batchNumber = 0, processedCount = 0, totalCount = 0,
382:                         message = ex.message ?: "初始化失败"
383:                     ), executionId)
384:                 } finally {
385:                     val execId = executionId
386:                     if (execId != null) {
387:                         progressStore.clearExecutionContext(TASK_TYPE, execId)
413:             log.warn("Timed out waiting for executionId after launch: {}", e.message)
414:         }
415:         return ResponseEntity.accepted().body(body)
416:     }
417: 
418:     private fun validateSnapshotFields(snapshot: BatchExecutionSnapshot): ResponseEntity<Map<String, Any>>? {
419:         // I-3: 直接手动快照（c3 手动草稿走同一入口）绕过配置服务，类型守卫必须在此独立成立。
420:         // 显式 400：与配置保存路径的 `require` → GlobalExceptionHandler(BAD_REQUEST) 同码。
421:         if (snapshot.emailVerificationEnabled && snapshot.mailType != BatchSendType.INTRODUCTION.name) {
422:             return ResponseEntity.status(HttpStatus.BAD_REQUEST)
423:                 .body(mapOf("message" to
424:                     "发送前邮箱验证只支持介绍邮件（${BatchSendType.INTRODUCTION.name}），" +
425:                         "当前快照类型为 ${snapshot.mailType}"
426:                 ))
427:         }
428:         return try {
429:             require(snapshot.roundSize > 0) { "roundSize must be > 0" }
430:             require(snapshot.roundsPerRun >= 1) { "roundsPerRun must be >= 1" }
431:             require(snapshot.perMailIntervalMs >= 0) { "perMailIntervalMs must be >= 0" }
432:             require(snapshot.perRoundIntervalMs >= 0) { "perRoundIntervalMs must be >= 0" }
433:             require(snapshot.selfCheckTtlMinutes >= 1) { "selfCheckTtlMinutes must be >= 1" }
434:             snapshot.funnelLevel?.let { level ->
435:                 require(level in setOf("CANDIDATE", "APPLICATION")) {
436:                     "funnelLevel must be CANDIDATE, APPLICATION, or empty"
437:                 }
438:             }
439:             snapshot.regions.forEach { region ->
440:                 require(region in CountryContinentMapping.allRegions()) { "Invalid region: $region" }
441:             }
442:             // I-1: 手动路径的快照直接来自请求体，不经配置服务，三态白名单必须在此独立校验。
443:             ResearchDirectionFilters.requireAllowed(snapshot.researchDirectionFilter)
444:             // I-1/I-2/I-5: 发件账号白名单与配置保存同口径 —— trim/去重后每项必须存在且非模拟器；
445:             // 未知 code 一律 422，绝不当作 []（否则伪造的未知 code 会被静默放宽成全池）。
446:             val senderAccountCodes = snapshot.senderAccountCodes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
447:             if (senderAccountCodes.isNotEmpty()) {
448:                 val known = mailSenderAccountService.listAccounts().map { it.accountCode }.toSet()
449:                 senderAccountCodes.forEach { code ->
450:                     require(code != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE) {
499:     private fun applyResultToRuntimeStatus(
500:         mode: ExecutionMode,
501:         result: ManualOutreachResult,
502:         returnToPausedAfterOneRound: Boolean,
503:         sendType: BatchSendType
504:     ) {
505:         val finalStatus = result.finalStatus ?: if (result.wasCancelled) "CANCELLED" else "COMPLETED"
506:         val current = getRuntimeStatusInternal(sendType)
507:         if (current.status != "RUNNING") {
508:             log.info("Runtime status is {} (not RUNNING) after execution for {}; skipping transition (finalStatus={})", current.status, sendType, finalStatus)
509:             return
510:         }
511:         if (!returnToPausedAfterOneRound && finalStatus == "PAUSED" && result.stopReason in idleSafeOneRoundStopReasons) {
512:             setRuntimeStatusInternal("IDLE", mode.name, "", sendType)
513:             log.info("Batch send {} transitioned to IDLE after one-round manual execution: reason={}", sendType, result.stopReason)
514:             return
515:         }
516:         when (finalStatus) {
517:             "PAUSED", "CANCELLED", "FAILED" -> {
518:                 val reason = when (finalStatus) {
519:                     "PAUSED" -> result.stopReason ?: "PAUSED"
520:                     "CANCELLED" -> result.stopReason ?: "CANCELLED"
521:                     "FAILED" -> result.stopReason ?: "FAILED"
522:                     else -> ""
523:                 }
524:                 setRuntimeStatusInternal("PAUSED", mode.name, reason, sendType)
525:                 log.info("Batch send {} transitioned to PAUSED after execution: reason={}", sendType, reason)
526:             }
527:             else -> {
528:                 setRuntimeStatusInternal("IDLE", mode.name, "", sendType)
529:                 log.info("Batch send {} transitioned to IDLE after execution (finalStatus={})", sendType, finalStatus)
530:             }
531:         }
532:     }
533: 
534:     // ── Legacy KV fallback when legacy_code row missing ─────────────────────────
535: 
```

## E-12 保留策略

文件：`src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt`；读取命令等价于 `nl -ba src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt` 后按列出的行段截取。

```text
170:     ): Int
171: 
172:     /**
173:      * B5 保留清理（I3-3）：按 `started_at` 删（该列有 idx_te_started，created_at 无索引）。
174:      * 含一年内原始邮箱验证结果的执行暂留，防FK级联在90天时删掉可复用历史；复用行不延长保留。
175:      * 时间取北京时间（固定偏移无需MySQL时区表）；与验证服务 now.minusYears(1) 同口径。
176:      * `ORDER BY ... LIMIT` 使删除沿索引顺序分批进行，减少锁范围（I3-2）。返回受影响行数。
177:      */
178:     @Modifying
179:     @Query("""
180:         DELETE FROM task_execution
181:          WHERE started_at < :cutoff
182:            AND NOT EXISTS (
183:                SELECT 1 FROM batch_email_verification v
184:                 WHERE v.task_execution_id = task_execution.id
185:                   AND v.request_count > 0 AND v.reused_from_id IS NULL AND v.error_code IS NULL
186:                   AND v.checked_at > DATE_SUB(CONVERT_TZ(UTC_TIMESTAMP(3), '+00:00', '+08:00'), INTERVAL 1 YEAR)
187:                   AND v.checked_at <= CONVERT_TZ(UTC_TIMESTAMP(3), '+00:00', '+08:00')
188:                   AND ((v.decision = 'PASS' AND v.provider_state = 'deliverable')
189:                     OR (v.decision = 'SKIP' AND v.provider_state IN ('undeliverable', 'risky', 'unknown')))
190:            )
191:          ORDER BY started_at LIMIT :batchSize
192:     """)
193:     fun deleteOlderThan(cutoff: LocalDateTime, batchSize: Int): Int
```

## E-13 任务快照和清理调用

命令：
```sh
rg -n "repository\.|requestPayload|taskFinalStatus" src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt; rg -n "deleteOlderThan|taskExecutionRepository|executionRepository" src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt
```
退出码：0

```text
7:import com.weibo.talentintroduction.task.repository.TaskExecutionListItem
8:import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
26:        repository.heartbeatOwned(ownerToken, now)
27:        return repository.interruptExpired(now.minusMinutes(5), now)
39:        val changed = repository.interruptManually(
43:            val current = repository.findById(id).orElseThrow {
52:        return repository.findById(id).orElseThrow { NoSuchElementException("任务记录不存在：$id") }
58:                items = repository.findPageByTaskTypeAndStatus(taskType, status, size, offset),
59:                total = repository.countByTaskTypeAndStatus(taskType, status)
63:                items = repository.findPageByTaskType(taskType, size, offset),
64:                total = repository.countByTaskType(taskType)
68:                items = repository.findPageByStatus(status, size, offset),
69:                total = repository.countByStatus(status)
73:                items = repository.findPage(size, offset),
74:                total = repository.countAll()
80:        repository.findById(id)
85:        return repository.findRecentByBatchConfigId(batchConfigId, limit)
90:        return repository.findRecentByTaskType(taskType, limit)
100:        return repository.findLastStartedAtByBatchConfigIds(batchConfigIds)
111:        return repository.sumSuccessCountByBatchConfigIdBetween(batchConfigId, dayStart, nextDayStart).toInt()
116:        repository.updateProgressCounts(executionId, successCount, failureCount, LocalDateTime.now())
121:        return repository.findRecentByTaskType("AUTO_REPLY_ALL", limit)
125:        repository.countActiveSince(taskType, "SCHEDULED", since)
147:        val running = repository.save(
152:                requestPayload = toJson(request),
171:                    val finalStatus = resultValue.taskFinalStatus
226:        val running = repository.save(
231:                requestPayload = toJson(request),
250:                    val finalStatus = resultValue.taskFinalStatus
295:        val changed = repository.finishOwned(
301:        return if (changed == 1) next else repository.findById(id).orElse(next)
28:    private val executionRepository: TaskExecutionRepository,
42:            progressDeleted = purgeLoop { limit -> progressLogRepository.deleteOlderThan(cutoff, limit) }
49:            executionDeleted = purgeLoop { limit -> executionRepository.deleteOlderThan(cutoff, limit) }
```

## E-14 API与历史明细

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt`；读取命令等价于 `nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` 后按列出的行段截取。

```text
53:     // ── New multi-config CRUD ──────────────────────────────────────────────────
54: 
55:     @GetMapping("/configs")
56:     fun listConfigs(@RequestParam(required = false) q: String?): ResponseEntity<List<BatchSendTaskConfigView>> =
57:         ResponseEntity.ok(batchSendTaskConfigService.list(q))
58: 
59:     @PostMapping("/configs")
60:     fun createConfig(@RequestBody request: BatchSendTaskConfigCreateCommand): ResponseEntity<BatchSendTaskConfigView> =
61:         ResponseEntity.status(HttpStatus.CREATED).body(batchSendTaskConfigService.create(request))
62: 
63:     @GetMapping("/configs/{id}")
64:     fun getConfigById(@PathVariable id: Long): ResponseEntity<BatchSendTaskConfigView> =
65:         ResponseEntity.ok(batchSendTaskConfigService.get(id))
66: 
67:     @PutMapping("/configs/{id}")
68:     fun updateConfigById(
69:         @PathVariable id: Long,
70:         @RequestBody request: BatchSendTaskConfigUpdateCommand
71:     ): ResponseEntity<BatchSendTaskConfigView> =
72:         ResponseEntity.ok(batchSendTaskConfigService.update(id, request))
73: 
74:     @PatchMapping("/configs/{id}/enabled")
75:     fun setConfigEnabled(
76:         @PathVariable id: Long,
77:         @RequestBody request: BatchSendTaskConfigEnabledRequest
78:     ): ResponseEntity<BatchSendTaskConfigView> =
79:         ResponseEntity.ok(batchSendTaskConfigService.setEnabled(id, request.enabled))
80: 
81:     @DeleteMapping("/configs/{id}")
82:     fun deleteConfig(@PathVariable id: Long): ResponseEntity<Void> {
83:         batchSendTaskConfigService.softDelete(id)
84:         return ResponseEntity.noContent().build()
85:     }
86: 
87:     /**
88:      * POST (not GET) so cron expressions with '?' '*' do not need query-string escaping.
89:      * Always 200: invalid cron is a normal editor state, not an error (I-3).
90:      */
91:     @PostMapping("/cron/preview")
92:     fun previewCron(@RequestBody request: CronPreviewRequest): ResponseEntity<CronPreviewResult> =
93:         ResponseEntity.ok(batchSendTaskConfigService.previewCron(request.cron, request.count ?: 5))
94: 
95:     /**
96:      * Recipient-count preview (P-F / 06): input is the launch snapshot itself (I-2), computed
97:      * with the exact execution-path target code (I-1) and no side effects (I-3).
98:      * POST (not GET) — the snapshot carries tags/regions arrays that do not fit a query string.
99:      */
100:     @PostMapping("/recipients/preview")
101:     fun previewRecipients(@RequestBody snapshot: BatchExecutionSnapshot): ResponseEntity<PendingOutreachSummary> =
102:         ResponseEntity.ok(manualInitialOutreachService.countBySnapshot(snapshot))
103: 
104:     @PostMapping("/configs/{id}/execute")
105:     fun executeConfig(@PathVariable id: Long): ResponseEntity<Map<String, Any>> =
106:         batchSendControlService.startManualFromConfig(id)
107: 
108:     @PostMapping("/manual-executions")
109:     fun executeManual(@RequestBody request: ManualBatchExecutionRequest): ResponseEntity<Map<String, Any>> =
110:         batchSendControlService.startManual(request)
111: 
172:      * - 开关只读该次执行保存的 `requestSnapshot`：历史 payload 缺字段 = false；快照 JSON 无法解析时
173:      *   明确报错，绝不冒充「关闭」。
174:      * - 分页由仓储在同一只读事务内组合（`limit+1` 判 `hasMore`，游标取本页最后 id）；
175:      *   `summary` 是整次执行而不是本页，`passed/rejected/errors` 互斥，`tagFailed` 是附加维度。
176:      * - 本接口只有读操作：不触发验证、不发送、无密钥字段。仓储异常直接向上抛（服务错误），
177:      *   不返回空数组掩盖故障。
178:      */
179:     @GetMapping("/executions/{executionId}/email-verifications")
180:     fun getExecutionEmailVerifications(
181:         @PathVariable executionId: Long,
182:         @RequestParam(defaultValue = "0") afterId: Long,
183:         @RequestParam(defaultValue = "50") limit: Int,
184:         @RequestParam(required = false) configId: Long?
185:     ): ResponseEntity<BatchEmailVerificationDetail> {
186:         require(afterId >= 0) { "afterId must be >= 0" }
187:         require(limit in 1..BatchEmailVerificationRepository.MAX_PAGE_SIZE) {
188:             "limit must be between 1 and ${BatchEmailVerificationRepository.MAX_PAGE_SIZE}"
189:         }
190:         val execution = try {
191:             taskExecutionService.getExecution(executionId)
192:         } catch (_: IllegalStateException) {
193:             // 与其它执行级接口同码：不存在的执行按 404，不按参数错误。
194:             return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
195:         }
196:         if (execution.taskType != BatchSendControlService.TASK_TYPE) {
197:             return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
198:         }
199:         if (configId != null && configId != execution.batchConfigId) {
200:             return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
201:         }
202:         val enabled = readEmailVerificationEnabled(execution)
203:         val page = batchEmailVerificationRepository.readPage(executionId, afterId, limit)
204:         return ResponseEntity.ok(
205:             BatchEmailVerificationDetail(
206:                 executionId = executionId,
207:                 enabled = enabled,
208:                 summary = BatchEmailVerificationSummary(
209:                     total = page.aggregate.total,
210:                     pending = page.aggregate.pending,
211:                     passed = page.aggregate.passed,
212:                     rejected = page.aggregate.rejected,
213:                     errors = page.aggregate.serviceError,
214:                     tagFailed = page.aggregate.tagFailed
215:                 ),
216:                 items = page.rows.map { it.toVerificationItem() },
217:                 nextAfterId = if (page.hasMore) page.rows.lastOrNull()?.id else null,
218:                 hasMore = page.hasMore
219:             )
220:         )
221:     }
222: 
223:     /**
224:      * 开关以该次执行保存的 `requestSnapshot` 为准（I-2），不读当前配置推断。
225:      * 无 payload / 无 snapshot 节点 / 缺字段一律 false；payload 不是合法 JSON 时明确报错。
226:      */
227:     private fun readEmailVerificationEnabled(
228:         execution: com.weibo.talentintroduction.task.domain.TaskExecution
229:     ): Boolean {
230:         val payload = execution.requestPayload ?: return false
231:         val root = try {
232:             objectMapper.readTree(payload)
233:         } catch (_: Exception) {
234:             throw IllegalStateException("历史执行快照无法解析（executionId=${execution.id}）")
235:         }
236:         val snapshot = root.path("snapshot")
237:         if (snapshot.isMissingNode || !snapshot.isObject) return false
238:         val flag = snapshot.path("emailVerificationEnabled")
239:         return !flag.isMissingNode && !flag.isNull && flag.asBoolean(false)
240:     }
241: 
242:     /** 展示白名单：不含 key/HTTP 原文，也不回传 taskExecutionId、createdAt、updatedAt。 */
243:     private fun BatchEmailVerificationRow.toVerificationItem(): BatchEmailVerificationItem =
244:         BatchEmailVerificationItem(
245:             id = id,
246:             expertDocId = expertDocId,
247:             orcidId = orcidId,
248:             expertName = expertName,
249:             email = email,
250:             decision = decision,
251:             providerState = providerState,
252:             providerReason = providerReason,
253:             errorCode = errorCode,
254:             checkedAt = checkedAt,
255:             requestCount = requestCount,
256:             reusedFromId = reusedFromId,
257:             sendStatus = sendStatus,
258:             sendReason = sendReason,
259:             tagStatus = tagStatus,
260:             tagError = tagError
261:         )
363:     private fun toDetail(
364:         execution: com.weibo.talentintroduction.task.domain.TaskExecution,
365:         progressRows: List<ExecutionProgressRow>,
366:         live: ExecutionLiveView? = null
367:     ): BatchConfigExecutionDetail {
368:         val outcome = parseOutcome(execution.resultSummary)
369:         val runningFallback = if (outcome == null) {
370:             execution.id?.let {
371:                 parseProgressLogOutcome(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(it))
372:             }
373:         } else null
374:         val requestSnapshot = execution.requestPayload?.let {
375:             runCatching { objectMapper.readTree(it) }.getOrNull()
376:         }
377:         return BatchConfigExecutionDetail(
378:             executionId = execution.id,
379:             triggerType = execution.triggerType,
380:             status = execution.status,
381:             target = outcome?.target ?: runningFallback?.target ?: 0,
382:             success = outcome?.success ?: runningFallback?.success ?: execution.successCount,
383:             failure = outcome?.failure ?: runningFallback?.failure ?: execution.failureCount,
384:             skipped = outcome?.skipped ?: runningFallback?.skipped ?: 0,
385:             remaining = outcome?.remaining ?: runningFallback?.remaining ?: 0,
386:             startedAt = execution.startedAt,
387:             finishedAt = execution.finishedAt,
388:             durationMs = if (execution.finishedAt != null) {
389:                 Duration.between(execution.startedAt, execution.finishedAt).toMillis()
390:             } else null,
391:             requestSnapshot = requestSnapshot,
392:             failureReasons = outcome?.failureReasons ?: runningFallback?.failureReasons ?: emptyMap(),
393:             skippedReasons = outcome?.skippedReasons ?: runningFallback?.skippedReasons ?: emptyMap(),
394:             errorSamples = outcome?.errorSamples ?: runningFallback?.errorSamples ?: emptyList(),
395:             progressRows = progressRows,
396:             live = live
584: data class BatchEmailVerificationDetail(
585:     val executionId: Long,
586:     val enabled: Boolean,
587:     val summary: BatchEmailVerificationSummary,
588:     val items: List<BatchEmailVerificationItem>,
589:     val nextAfterId: Long?,
590:     val hasMore: Boolean
591: )
592: 
593: /**
594:  * 整次执行的验证汇总（不是本页）：`passed + rejected + errors + pending = total`；
595:  * `tagFailed` 是标签处理的附加维度，不参与该合计。
596:  */
597: data class BatchEmailVerificationSummary(
598:     val total: Int,
599:     val pending: Int,
600:     val passed: Int,
601:     val rejected: Int,
602:     val errors: Int,
603:     val tagFailed: Int
604: )
605: 
606: /** 明细行展示白名单（无供应商密钥、无 HTTP 原文、无内部时间戳）。 */
607: data class BatchEmailVerificationItem(
608:     val id: Long,
609:     val expertDocId: String?,
610:     val orcidId: String,
611:     val expertName: String?,
612:     val email: String,
613:     val decision: String,
614:     val providerState: String?,
615:     val providerReason: String?,
616:     val errorCode: String?,
617:     val checkedAt: java.time.LocalDateTime?,
618:     val requestCount: Int,
619:     val sendStatus: String,
620:     val sendReason: String?,
621:     val tagStatus: String,
622:     val tagError: String?,
623:     val reusedFromId: Long? = null
624: )
625: 
```

## E-15 数据定义

命令：
```sh
cat src/main/resources/db/migration/V138__create_batch_email_verification.sql src/main/resources/db/migration/V139__add_batch_email_verification_enabled.sql src/main/resources/db/migration/V140__reuse_batch_email_verification.sql; rg -n "dynamic|normalizer|email.*keyword" src/main/resources/es/orcid_info_*.json; rg --files src/main/resources/db/migration | sort -V | tail -8
```
退出码：0

```text
-- ============================================================================
-- V138 介绍邮件发送前邮箱验证明细（fast-p c1：Emailable 验证 / 标签 / 审计收尾）
--
-- 一次执行一行 = 一个专家（真实身份键 + 实际收件地址）的验证、发送与标签结果。
-- 表存在的原因（子计划 01 I-6/I-9）：现有 task_progress_log 是折叠日志，错误样本最多 20 条，
-- 不能作为「逐邮箱可追溯」的持久化事实源；把每个邮箱数组塞进 progress.details_json 只会让
-- 进度日志膨胀。故新增独立明细表，状态/结果汇总一律来自本表。
--
-- 关键不变量：
--   I-6 task_execution_id + 规范化 ORCID + 规范化邮箱唯一；先插 PENDING，再请求，再写
--       PASS/SKIP/ERROR，随后才允许发送；发送前把 decision='PASS' 且 send_status='NOT_SENT'
--       的行条件更新为 SENDING（必须影响 1 行才允许 SMTP），结果写 SENT/FAILED；
--       崩溃或已发后写库失败保留 SENDING（对外解释「结果未确认」），禁止据此自动重发。
--   I-9 外键 task_execution(id) ON DELETE CASCADE，跟随现有 90 天默认保留期清理，不加新清理任务；
--       分页严格按 (task_execution_id, id) 游标查询，默认 50、最大 100。
--
-- 列语义（下游 03 只读接口依赖，不得改名/改语义）：
--   expert_doc_id 真实 ES `_id`，可缺失（缺失时标签记 FAILED/MISSING_DOC_ID，禁止用 ORCID 冒充）
--   orcid_id      规范化业务 ID，非空（不虚构 _id）
--   expert_name   展示快照；超长只截断名字，绝不截断身份键
--   email         规范化的实际收件地址（trim + lowercase(Locale.ROOT)，保留 +tag、不合并点号）；
--                 超长（> 320）明确拒绝，不截断
--   decision      PENDING / PASS / SKIP / ERROR
--   provider_state / provider_reason  供应商明确结果的状态与原因（原始 reason 只作数据）
--   error_code    受控错误码（EMAIL_VERIFY_*），不保存完整 HTTP 请求/响应
--   request_count 物理请求次数；同执行内同邮箱复用为 0；不是积分余额
--   checked_at    返回明确结果/服务错误的北京时间；复用行沿原结果时间
--   send_status   NOT_SENT / SENDING / SENT / FAILED / SKIPPED
--   send_reason   既有跳过/失败码或 CANCELLED / ACCOUNT_UNAVAILABLE / RESULT_UNCONFIRMED
--   tag_status    NOT_REQUIRED / PENDING / APPLIED / FAILED
--   tag_error     只记层名与受控原因，不记正文
--
-- 排序规则：身份键（orcid_id / email / expert_doc_id）用 utf8mb4_bin 严格比较，
-- 避免 utf8mb4_general_ci 把大小写不同的地址判为同一目标，破坏唯一键语义。
-- 键长：唯一键 (task_execution_id, orcid_id, email) = 8 + (128 + 320) * 4 = 1800 字节 < 3072。
-- 不建 contact 外键：验证拒绝的目标不得创建/绑定 expert_contact。
-- MySQL 兼容性：DATETIME(3)/utf8mb4_bin/命名外键在 5.7 与 8.0 均可解析。
-- ============================================================================
CREATE TABLE batch_email_verification (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    task_execution_id BIGINT       NOT NULL,
    expert_doc_id     VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    orcid_id          VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    expert_name       VARCHAR(256) NULL,
    email             VARCHAR(320) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    decision          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    provider_state    VARCHAR(32)  NULL,
    provider_reason   VARCHAR(128) NULL,
    error_code        VARCHAR(64)  NULL,
    request_count     INT          NOT NULL DEFAULT 0,
    checked_at        DATETIME(3)  NULL,
    send_status       VARCHAR(16)  NOT NULL DEFAULT 'NOT_SENT',
    send_reason       VARCHAR(64)  NULL,
    tag_status        VARCHAR(16)  NOT NULL DEFAULT 'NOT_REQUIRED',
    tag_error         VARCHAR(256) NULL,
    created_at        DATETIME(3)  NOT NULL,
    updated_at        DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_email_verification_target (task_execution_id, orcid_id, email),
    KEY idx_batch_email_verification_execution (task_execution_id, id),
    CONSTRAINT fk_batch_email_verification_execution
        FOREIGN KEY (task_execution_id) REFERENCES task_execution (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '介绍邮件发送前邮箱验证逐专家明细：验证结论/发送结果/标签处理，随执行级联清理';
-- I-1: 发送前邮箱验证开关。存量任务一律回填 FALSE（不暗中开启已验证策略），
-- 新列 NOT NULL 且带默认值，旧客户端不传该字段时写入默认关闭。
ALTER TABLE batch_send_task_config
    ADD COLUMN email_verification_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER research_direction_filter;
-- 跨执行按邮箱复用一年内的原始结果；旧原始结果无需回填。
-- 关联用于审计，不设自引用FK：原始结果到期清理后，本次明细仍保留已复制的结果与时间。
ALTER TABLE batch_email_verification
    ADD COLUMN reused_from_id BIGINT NULL COMMENT '复用的原始验证明细ID，实际HTTP验证为空',
    ADD INDEX idx_batch_email_verification_email_time (email, checked_at, id);
src/main/resources/es/orcid_info_raw.json:7:    "dynamic": false,
src/main/resources/es/orcid_info_raw.json:12:      "email": { "type": "keyword" },
src/main/resources/es/orcid_info_raw.json:28:      "emailSource": { "type": "keyword" },
src/main/resources/es/orcid_info_raw.json:31:      "identityVerification": { "type": "object", "dynamic": false, "properties": {
src/main/resources/es/orcid_info_raw.json:33:        "email": { "type": "keyword", "index": false }, "givenNames": { "type": "keyword", "index": false },
src/main/resources/es/orcid_info_candidate.json:7:    "dynamic": false,
src/main/resources/es/orcid_info_candidate.json:12:      "email": { "type": "keyword" },
src/main/resources/es/orcid_info_candidate.json:29:      "emailSource": { "type": "keyword" },
src/main/resources/es/orcid_info_candidate.json:32:      "identityVerification": { "type": "object", "dynamic": false, "properties": {
src/main/resources/es/orcid_info_candidate.json:34:        "email": { "type": "keyword", "index": false }, "givenNames": { "type": "keyword", "index": false },
src/main/resources/es/orcid_info_application.json:7:    "dynamic": false,
src/main/resources/es/orcid_info_application.json:12:      "email": { "type": "keyword" },
src/main/resources/es/orcid_info_application.json:40:      "emailSource": { "type": "keyword" },
src/main/resources/es/orcid_info_application.json:43:      "identityVerification": { "type": "object", "dynamic": false, "properties": {
src/main/resources/es/orcid_info_application.json:45:        "email": { "type": "keyword", "index": false }, "givenNames": { "type": "keyword", "index": false },
src/main/resources/db/migration/V134__shared_inbox_owner.sql
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql
src/main/resources/db/migration/V136__add_compose_subject_snippet_id.sql
src/main/resources/db/migration/V137__task_execution_interruption_recovery.sql
src/main/resources/db/migration/V138__create_batch_email_verification.sql
src/main/resources/db/migration/V139__add_batch_email_verification_enabled.sql
src/main/resources/db/migration/V140__reuse_batch_email_verification.sql
src/main/resources/db/migration/V141__create_mail_open_tracking.sql
```

## E-16 DOM基线

文件：`src/main/resources/static/index.html`；读取命令等价于 `nl -ba src/main/resources/static/index.html` 后按列出的行段截取。

```text
1410:                         </div>
1411:                         <div class="batch-config-field batch-gate-field" id="editorFieldGateFilter">
1412:                             <span class="batch-config-field-label">邮件模版门禁过滤</span>
1413:                             <div class="batch-gate-row">
1414:                                 <label class="batch-task-status-toggle batch-gate-toggle">
1415:                                     <input type="checkbox" id="batchConfigEditorGateFilter">
1416:                                     <span class="batch-task-status-switch"></span>
1417:                                     <span class="batch-task-status-label" id="batchConfigEditorGateFilterLabel">已关闭</span>
1418:                                 </label>
1419:                                 <span class="batch-gate-hint" id="batchConfigEditorGateFilterHint">仅向满足该模板必填字段的专家发送，缺字段的会在发送时被门禁拦下并计入失败。</span>
1420:                             </div>
1421:                             <div class="batch-gate-keys" id="batchConfigEditorGateFilterKeys" hidden></div>
1422:                         </div>
1423:                     </div>
1424:                 </section>
1425: 
1426:                 <section class="batch-config-editor-section">
1427:                     <div class="batch-config-editor-section-heading">
1428:                         <h4>发送控制</h4>
1429:                         <span>限制发送节奏，避免单次任务占用过多资源</span>
1430:                     </div>
1431:                     <div class="batch-config-editor-grid batch-config-editor-grid-controls">
1432:                         <div class="batch-config-field batch-gate-field" id="editorFieldEmailVerification">
1433:                             <span class="batch-config-field-label">发送前验证邮箱（Emailable）</span>
1434:                             <div class="batch-gate-row">
1435:                                 <label class="batch-task-status-toggle batch-gate-toggle">
1436:                                     <input type="checkbox" id="batchConfigEditorEmailVerification" aria-describedby="batchConfigEditorEmailVerificationHint">
1437:                                     <span class="batch-task-status-switch" aria-hidden="true"></span>
1438:                                     <span class="batch-task-status-label" id="batchConfigEditorEmailVerificationLabel">已关闭</span>
1439:                                 </label>
1440:                                 <span class="batch-gate-hint" id="batchConfigEditorEmailVerificationHint">仅不可投递（undeliverable）跳过并标记邮箱异常；risky / unknown 按策略放行。服务异常停止本次执行。会消耗 Emailable 额度。</span>
1441:                             </div>
1442:                         </div>
1657:                     <div class="batch-config-field batch-gate-field" id="manualFieldGateFilter">
1658:                         <span class="batch-config-field-label">邮件模版门禁过滤</span>
1659:                         <div class="batch-gate-row">
1660:                             <label class="batch-task-status-toggle batch-gate-toggle">
1661:                                 <input type="checkbox" id="batchManualGateFilter">
1662:                                 <span class="batch-task-status-switch"></span>
1663:                                 <span class="batch-task-status-label" id="batchManualGateFilterLabel">已关闭</span>
1664:                             </label>
1665:                             <span class="batch-gate-hint" id="batchManualGateFilterHint">仅影响本次执行，不修改原定时任务。</span>
1666:                         </div>
1667:                         <div class="batch-gate-keys" id="batchManualGateFilterKeys" hidden></div>
1668:                         <span class="batch-config-diff-badge" hidden>已修改</span>
1669:                         <div class="batch-config-diff-original" hidden></div>
1670:                     </div>
1671:                 </div>
1672:                 <div class="batch-config-editor-hint" id="batchManualRecipientHint"></div>
1673:             </section>
1674: 
1675:             <section class="batch-manual-section">
1676:                 <div class="batch-manual-section-heading">
1677:                     <h4>发送控制</h4>
1678:                     <span>仅影响本次执行，不修改原定时任务</span>
1679:                 </div>
1680:                 <div class="batch-config-editor-grid batch-config-editor-grid-controls">
1681:                     <div class="batch-config-field batch-gate-field" id="manualFieldEmailVerification">
1682:                         <span class="batch-config-field-label">发送前验证邮箱（Emailable）</span>
1683:                         <div class="batch-gate-row">
1684:                             <label class="batch-task-status-toggle batch-gate-toggle">
1685:                                 <input type="checkbox" id="batchManualEmailVerification" aria-describedby="batchManualEmailVerificationHint">
1686:                                 <span class="batch-task-status-switch" aria-hidden="true"></span>
1687:                                 <span class="batch-task-status-label" id="batchManualEmailVerificationLabel">已关闭</span>
1688:                             </label>
1689:                             <span class="batch-gate-hint" id="batchManualEmailVerificationHint">仅不可投递（undeliverable）跳过并标记邮箱异常；risky / unknown 按策略放行。服务异常停止本次执行。会消耗 Emailable 额度。仅影响本次执行。</span>
1690:                         </div>
1691:                         <span class="batch-config-diff-badge" hidden>已修改</span>
1692:                         <div class="batch-config-diff-original" hidden></div>
1693:                     </div>
```

## E-17 样式基线

文件：`src/main/resources/static/styles.css`；读取命令等价于 `nl -ba src/main/resources/static/styles.css` 后按列出的行段截取。

```text
1: :root {
2:     /* Brand — modern business blue */
3:     --primary: #1e40af;
4:     --primary-hover: #1e3a8a;
5:     --primary-active: #172554;
6:     --primary-rgb: 30, 64, 175;
7:     --primary-light: rgba(var(--primary-rgb), 0.07);
8:     --primary-tint: rgba(var(--primary-rgb), 0.1);
9: 
10:     --bg-main: #f5f7fb;
11:     --bg-sidebar: #ffffff;
12:     --bg-sidebar-hover: rgba(var(--primary-rgb), 0.06);
13:     --bg-sidebar-active: rgba(var(--primary-rgb), 0.09);
14: 
15:     --panel-bg: rgba(255, 255, 255, 0.55);
16:     --panel-border: rgba(15, 23, 42, 0.08);
17:     --line: rgba(15, 23, 42, 0.055);
18:     --border: rgba(15, 23, 42, 0.11);
19:     --surface: rgba(15, 23, 42, 0.022);
20: 
21:     --text-main: #1e293b;
22:     --text-muted: #94a3b8;
23:     --text-sidebar: #64748b;
24:     --text-sidebar-active: #1e293b;
25:     --text-secondary: #475569;
26:     --text-strong: #334155;
27:     --ink: #1e293b;
28:     --bg-subtle: #f8fafc;
29:     --border-strong: #cbd5e1;
30:     --primary-bright: #3b82f6;
31: 
32:     --success: #059669;
33:     --success-rgb: 5, 150, 105;
34:     --success-bg: rgba(var(--success-rgb), 0.08);
35:     --success-border: rgba(var(--success-rgb), 0.18);
36:     --green: var(--success);
37: 
38:     --error: #e11d48;
39:     --error-rgb: 225, 29, 72;
40:     --error-bg: rgba(var(--error-rgb), 0.07);
41:     --error-border: rgba(var(--error-rgb), 0.16);
42:     --error-strong: #be123c;
43:     --red: var(--error);
44: 
45:     --warning: #d97706;
46:     --warning-rgb: 217, 119, 6;
47:     --warning-bg: rgba(var(--warning-rgb), 0.08);
48:     --warning-border: rgba(var(--warning-rgb), 0.2);
49:     --warning-strong: #b45309;
50:     --warning-bright: #f59e0b;
51:     --amber: var(--warning);
52: 
53:     --info: #0ea5e9;
54:     --info-rgb: 14, 165, 233;
55:     --info-bg: rgba(var(--info-rgb), 0.08);
56:     --info-border: rgba(var(--info-rgb), 0.2);
57: 
58:     --z-sticky: 10;
59:     --z-dropdown: 20;
60:     --z-overlay: 50;
61:     --z-drawer: 60;
62:     --z-modal: 1000;
63:     --z-confirm: 1200;
64:     --z-toast: 9999;
65: 
66:     --glass-border: rgba(255, 255, 255, 0.5);
67:     --glass-shadow: 0 8px 32px rgba(var(--primary-rgb), 0.1);
68:     --glass-blur: blur(16px);
69: 
70:     --radius-sm: 7px;
71:     --radius-md: 10px;
72:     --radius-lg: 18px;
73: 
74:     --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
75:     --shadow-md: 0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.03);
76:     --shadow-lg: 0 10px 28px -8px rgba(15, 23, 42, 0.14), 0 2px 6px rgba(15, 23, 42, 0.05);
9257: .batch-config-editor-hint {
9258:   margin-top: 10px;
9259:   padding: 8px 12px;
9260:   border-radius: var(--radius-md);
9261:   background: rgba(37, 99, 235, .06);
9262:   color: var(--text-secondary);
9263:   font-size: 12px;
9264:   line-height: 1.6;
9265: }
9266: 
9267: .batch-config-editor-hint strong {
9268:   color: var(--primary);
9269:   font-weight: 600;
9270: }
9271: 
9320: .batch-config-editor .batch-config-field {
9321:   min-width: 0;
9322:   padding: 0;
9323:   border: 0;
9324:   border-radius: 0;
9325: }
9326: 
9327: .batch-config-editor .bsc-input {
9328:   width: 100%;
9329: }
9330: 
9331: .batch-config-field-label {
9332:   display: block;
9333:   margin-bottom: 6px;
9334:   color: var(--text-sidebar);
9335:   font-size: 12px;
9336:   font-weight: 600;
9337: }
9338: 
9339: .batch-config-editor-actions {
9340:   position: sticky;
9341:   z-index: 2;
9342:   bottom: -28px;
9343:   display: flex;
9344:   justify-content: flex-end;
9345:   gap: 10px;
9346:   margin: 16px -28px -28px;
9347:   padding: 14px 28px;
9348:   border-top: 1px solid rgba(15, 23, 42, .08);
9349:   background: rgba(255, 255, 255, .96);
9350:   box-shadow: 0 -8px 20px rgba(15, 23, 42, .04);
9351:   backdrop-filter: blur(8px);
9352: }
9353: 
9354: .batch-task-status-toggle {
9355:   position: relative;
9356:   display: inline-flex;
9357:   flex-direction: column;
9358:   align-items: flex-start;
9359:   gap: 5px;
9360:   color: var(--text-sidebar);
9361:   font-size: 11px;
9362:   font-weight: 500;
9363:   text-transform: none;
9364:   letter-spacing: 0;
9365:   cursor: pointer;
9366: }
9367: 
9368: .batch-task-status-toggle input[type="checkbox"] {
9369:   position: absolute;
9370:   width: 1px;
9371:   height: 1px;
9372:   min-height: 1px;
9373:   padding: 0;
9374:   margin: 0;
9375:   opacity: 0;
9376: }
9377: 
9378: .batch-task-status-switch {
9379:   position: relative;
9380:   display: block;
9381:   width: 36px;
9382:   height: 20px;
9383:   border-radius: 999px;
9384:   background: var(--border-strong);
9385:   transition: background-color .15s ease, box-shadow .15s ease;
9386: }
9387: 
9388: .batch-task-status-switch::after {
9389:   content: "";
9390:   position: absolute;
9391:   top: 2px;
9392:   left: 2px;
9393:   width: 16px;
9394:   height: 16px;
9395:   border-radius: 50%;
9396:   background: var(--panel-bg);
9397:   box-shadow: 0 1px 3px rgba(15, 23, 42, .22);
9398:   transition: transform .15s ease;
9399: }
9400: 
9401: .batch-task-status-toggle input:checked + .batch-task-status-switch {
9402:   background: var(--primary);
9403: }
9404: 
9405: .batch-task-status-toggle input:checked + .batch-task-status-switch::after {
9406:   transform: translateX(16px);
9407: }
9408: 
9409: .batch-task-status-toggle input:focus-visible + .batch-task-status-switch {
9410:   box-shadow: 0 0 0 3px rgba(37, 99, 235, .18);
9411: }
9412: 
9413: .batch-task-status-toggle input:checked ~ .batch-task-status-label {
9414:   color: var(--primary);
9415: }
9416: 
9417: .batch-send-task-body {
9418:   position: relative;
9419:   display: flex;
9420:   flex: 1;
9555:   border-radius: 0;
9556: }
9557: 
9558: .batch-manual-section .batch-config-field.is-config-diff {
9559:   padding: 10px;
9560:   border: 1px solid var(--error);
9561:   border-radius: var(--radius-md);
9562: }
9563: 
9564: .batch-manual-section .bsc-input {
9565:   width: 100%;
9566: }
9567: 
9568: .batch-config-diff-badge {
9569:   position: absolute;
9570:   top: 10px;
9571:   right: 10px;
9572:   color: var(--error-strong);
9573:   font-size: 11px;
9574:   font-weight: 700;
9575: }
9576: 
9577: .batch-config-diff-original { margin-top: 6px; color: var(--error-strong); font-size: 12px; }
9578: 
9579: .batch-tag-picker {
9708: .batch-gate-field { grid-column: 1 / -1; }
9709: 
9710: .batch-gate-row {
9711:   display: flex;
9712:   align-items: center;
9713:   flex-wrap: wrap;
9714:   gap: 12px;
9715: }
9716: 
9717: .batch-gate-toggle {
9718:   flex-direction: row;
9719:   align-items: center;
9720:   gap: 8px;
9721: }
9722: 
9723: .batch-gate-toggle .batch-task-status-label {
9724:   font-size: 12px;
9725:   font-weight: 600;
9726: }
9727: 
9728: .batch-gate-field.is-disabled { opacity: .6; }
9729: .batch-gate-field.is-disabled .batch-gate-toggle { cursor: not-allowed; }
9730: 
9731: .batch-gate-hint {
9732:   color: var(--text-muted);
9733:   font-size: 11px;
9734:   line-height: 1.5;
9735: }
9736: 
9737: .batch-gate-hint.is-warn { color: var(--warning-strong); }
9738: 
9739: .batch-gate-keys {
9740:   display: flex;
9741:   align-items: center;
9742:   flex-wrap: wrap;
9743:   gap: 6px;
9744:   margin-top: 10px;
9745: }
9746: 
9747: .batch-gate-keys[hidden] { display: none; }
9748: 
9749: .batch-gate-keys-label {
9750:   color: var(--text-sidebar);
9751:   font-size: 11px;
9752:   font-weight: 600;
9753: }
9754: 
9755: .batch-gate-keys .tag-chip {
9756:   cursor: default;
9757:   pointer-events: none;
9758: }
9759: 
9760: .batch-gate-keys-dropped {
9761:   flex-basis: 100%;
9762:   margin-top: 4px;
9763:   color: var(--text-muted);
9764:   font-size: 11px;
9765:   line-height: 1.5;
9766: }
9767: 
9768: .batch-config-editor-hint .batch-gate-excluded {
9769:   color: var(--error-strong);
9770:   font-weight: 600;
9771: }
9772: 
9773: .batch-config-editor-hint .batch-gate-warnline {
9774:   display: block;
9775:   margin-top: 4px;
9776:   color: var(--warning-strong);
9777: }
9778: 
9779: .batch-gate-pill {
9780:   display: inline-flex;
9781:   align-items: center;
9782:   height: 20px;
9783:   padding: 0 8px;
9784:   border: 1px solid var(--primary);
9785:   border-radius: var(--radius-lg);
9786:   background: var(--primary-light);
9787:   color: var(--primary);
9788:   font-size: 11px;
9789:   font-weight: 600;
9790: }
9791: 
9792: .batch-gate-pill.is-off {
9793:   border-color: var(--border);
9794:   background: transparent;
9795:   color: var(--text-muted);
9796: }
9797: 
9798: .batch-gate-pill.is-na {
9799:   border-style: dashed;
9800:   border-color: var(--border);
9801:   background: transparent;
9802:   color: var(--text-muted);
9803: }
11799: .batch-email-verification { margin: 14px 0; }
11800: .batch-email-verification h4 { margin: 0 0 8px; font-size: 13px; color: var(--text-main); }
11801: .batch-email-verification-note { margin: 6px 0; color: var(--text-sidebar); font-size: 12px; line-height: 1.6; }
11802: .batch-email-verification-note.is-error { color: var(--error-strong); }
11803: .batch-email-verification-table-wrap { max-width: 100%; overflow-x: auto; border: 1px solid var(--panel-border); border-radius: var(--radius-md); }
11804: .batch-email-verification-table { width: 100%; min-width: 560px; border-collapse: collapse; font-size: 12px; line-height: 1.5; }
11805: .batch-email-verification-table th, .batch-email-verification-table td { padding: 8px 10px; text-align: left; vertical-align: top; border-bottom: 1px solid var(--panel-border); overflow-wrap: anywhere; }
11806: .batch-email-verification-table th { color: var(--text-sidebar); background: var(--bg-subtle); font-weight: 600; }
11807: .batch-email-verification-table tbody tr:last-child td { border-bottom: 0; }
11808: .batch-email-verification-table details { margin-top: 4px; color: var(--text-secondary); }
11809: .batch-email-verification-table summary { cursor: pointer; color: var(--primary); }
11810: .batch-email-verification-table summary:hover { color: var(--primary-hover); }
11811: .batch-email-verification-table summary:active { color: var(--primary-active); }
11812: .batch-email-verification-table summary:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; border-radius: var(--radius-sm); }
11813: .batch-email-verification-pager { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
11814: .batch-email-verification-pager .button:disabled { opacity: .5; cursor: not-allowed; pointer-events: none; }
```

## E-18 前端所有字段与展示入口

命令：
```sh
rg -n "emailVerificationEnabled|EmailVerification|function baseHintHtml|function refreshRecipientPreview|manualFieldGateFilter" src/main/resources/static/app.js
```
退出码：0

```text
17782:    if (typeof resetBatchEmailVerification === "function") resetBatchEmailVerification();
17913:    scopeHtml += '<span class="batch-task-scope-line">' + (c.emailVerificationEnabled === true
18061:    // I-1：编辑从 View 回填，新建默认关闭；材料提醒的禁用/置回由 refreshEmailVerificationState 收口。
18062:    var emailVerificationCheckbox = document.getElementById("batchConfigEditorEmailVerification");
18064:        emailVerificationCheckbox.checked = Boolean(config && config.emailVerificationEnabled === true);
18094:    if (typeof refreshEmailVerificationState === "function") refreshEmailVerificationState("editor");
18136:        if (batchTaskState.editorMode && typeof refreshEmailVerificationState === "function") {
18137:            refreshEmailVerificationState("editor");
18734:    var field = document.getElementById(kind === "editor" ? "editorFieldGateFilter" : "manualFieldGateFilter");
18841:    return kind === "editor" ? "editorFieldEmailVerification" : "manualFieldEmailVerification";
18845:    return kind === "editor" ? "batchConfigEditorEmailVerification" : "batchManualEmailVerification";
18849:    return kind === "editor" ? "batchConfigEditorEmailVerificationLabel" : "batchManualEmailVerificationLabel";
18853:    return kind === "editor" ? "batchConfigEditorEmailVerificationHint" : "batchManualEmailVerificationHint";
18861:function updateEmailVerificationToggleLabel(kind) {
18869:function refreshEmailVerificationState(kind) {
18883:        updateEmailVerificationToggleLabel(kind);
18889:    updateEmailVerificationToggleLabel(kind);
18904:    // I-1：生效值 = 勾选状态（材料提醒已被 refreshEmailVerificationState 禁用并置回 false）。
18905:    var emailVerificationEl = document.getElementById("batchConfigEditorEmailVerification");
18923:        emailVerificationEnabled: Boolean(emailVerificationEl && emailVerificationEl.checked),
18948:        emailVerificationEnabled: values.emailVerificationEnabled,
18966:function baseHintHtml(total, res) {
18973:function refreshRecipientPreview(kind) {
19051:    var emailVerificationEl = document.getElementById("batchConfigEditorEmailVerification");
19072:        emailVerificationEnabled: Boolean(emailVerificationEl && emailVerificationEl.checked),
19166:        emailVerificationEnabled: c.emailVerificationEnabled === true,
19191:        emailVerificationEnabled: false,
19228:    var emailVerificationCheckbox = document.getElementById("batchManualEmailVerification");
19229:    if (emailVerificationCheckbox) emailVerificationCheckbox.checked = Boolean(d.emailVerificationEnabled);
19232:    if (typeof refreshEmailVerificationState === "function") refreshEmailVerificationState("manual");
19296:    var emailVerificationEl = document.getElementById("batchManualEmailVerification");
19310:        emailVerificationEnabled: Boolean(emailVerificationEl && emailVerificationEl.checked),
19332:        emailVerificationEnabled: Boolean(v.emailVerificationEnabled),
19343:    if (key === "emailVerificationEnabled") return value ? "开启" : "关闭";
19396:        { key: "emailVerificationEnabled", label: "发送前验证邮箱" },
19446:        gateFilterEnabled: "manualFieldGateFilter",
19447:        emailVerificationEnabled: "manualFieldEmailVerification",
19477:        "manualFieldDiscipline", "manualFieldResearchDirectionFilter", "manualFieldOperatorStatus", "manualFieldExpertTypes", "manualFieldSenderAccounts", "manualFieldGateFilter", "manualFieldEmailVerification", "manualFieldRoundsPerRun", "manualFieldRoundSize",
19501:    var emailVerificationEl = document.getElementById("batchManualEmailVerification");
19697:    if (typeof resetBatchEmailVerification === "function") resetBatchEmailVerification();
19742:    if (typeof resetBatchEmailVerification === "function") resetBatchEmailVerification();
19758:    if (typeof resetBatchEmailVerification === "function") resetBatchEmailVerification();
19833:        if (typeof loadBatchEmailVerification === "function") {
19834:            loadBatchEmailVerification(configId, executionId, detail.status, { poll: true });
19992:    if (typeof resetBatchEmailVerification === "function") resetBatchEmailVerification();
20022:   BatchEmailVerificationErrorCodes / BatchOutcomeReasonCodes / appendEmailAbnormalTag。 */
20123:function batchEmailVerificationRowHtml(row) {
20150:function batchEmailVerificationMetricsHtml(summary) {
20164:/* note 四类：关闭 / 尚未进入 / 正常分页说明 / 加载失败（is-error，见 renderBatchEmailVerificationFailure）。 */
20165:function batchEmailVerificationNoteText(payload, running) {
20179:function setBatchEmailVerificationPagerLoading(loading) {
20182:    var prevBtn = document.getElementById("batchLogEmailVerificationPrev");
20183:    var nextBtn = document.getElementById("batchLogEmailVerificationNext");
20188:function clearBatchEmailVerificationDisplay() {
20189:    var note = document.getElementById("batchLogEmailVerificationNote");
20194:    var metrics = document.getElementById("batchLogEmailVerificationMetrics");
20196:    var tbody = document.getElementById("batchLogEmailVerificationRows");
20198:    var tableWrap = document.getElementById("batchLogEmailVerificationTableWrap");
20200:    var pager = document.getElementById("batchLogEmailVerificationPager");
20202:    var pageInfo = document.getElementById("batchLogEmailVerificationPage");
20206:    setBatchEmailVerificationPagerLoading(false);
20210:function resetBatchEmailVerification() {
20220:    clearBatchEmailVerificationDisplay();
20223:function renderBatchEmailVerification(payload, executionStatus) {
20227:    var note = document.getElementById("batchLogEmailVerificationNote");
20228:    var metricsEl = document.getElementById("batchLogEmailVerificationMetrics");
20229:    var tbody = document.getElementById("batchLogEmailVerificationRows");
20230:    var tableWrap = document.getElementById("batchLogEmailVerificationTableWrap");
20231:    var pager = document.getElementById("batchLogEmailVerificationPager");
20232:    var pageInfo = document.getElementById("batchLogEmailVerificationPage");
20242:        note.textContent = batchEmailVerificationNoteText(payload, running);
20246:            ? batchEmailVerificationMetricsHtml(summary)
20249:    if (tbody) tbody.innerHTML = items.map(batchEmailVerificationRowHtml).join("");
20254:    setBatchEmailVerificationPagerLoading(false);
20258:function renderBatchEmailVerificationFailure(error) {
20259:    var note = document.getElementById("batchLogEmailVerificationNote");
20274:async function loadBatchEmailVerification(configId, executionId, executionStatus, options) {
20286:        clearBatchEmailVerificationDisplay();
20292:    setBatchEmailVerificationPagerLoading(true);
20301:        renderBatchEmailVerification(payload, executionStatus);
20307:        renderBatchEmailVerificationFailure(error);
20309:        if (seq === s.verificationRequestSeq) setBatchEmailVerificationPagerLoading(false);
20313:function batchEmailVerificationNextPage() {
20319:    loadBatchEmailVerification(s.logConfigId, s.logExecutionId, s.verificationExecutionStatus);
20322:function batchEmailVerificationPrevPage() {
20328:    loadBatchEmailVerification(s.logConfigId, s.logExecutionId, s.verificationExecutionStatus);
20332:function batchEmailVerificationTrackExpanded(id, open) {
20342:function bindBatchEmailVerificationEvents() {
20343:    var prevBtn = document.getElementById("batchLogEmailVerificationPrev");
20344:    if (prevBtn) prevBtn.addEventListener("click", batchEmailVerificationPrevPage);
20345:    var nextBtn = document.getElementById("batchLogEmailVerificationNext");
20346:    if (nextBtn) nextBtn.addEventListener("click", batchEmailVerificationNextPage);
20347:    var tbody = document.getElementById("batchLogEmailVerificationRows");
20355:            batchEmailVerificationTrackExpanded(id, Boolean(target.open));
20501:        if (typeof refreshEmailVerificationState === "function") refreshEmailVerificationState("editor");
20506:        if (typeof refreshEmailVerificationState === "function") refreshEmailVerificationState("manual");
20513:    var editorEmailVerificationToggle = document.getElementById("batchConfigEditorEmailVerification");
20514:    if (editorEmailVerificationToggle) editorEmailVerificationToggle.addEventListener("change", function() { updateEmailVerificationToggleLabel("editor"); });
20515:    var manualEmailVerificationToggle = document.getElementById("batchManualEmailVerification");
20516:    if (manualEmailVerificationToggle) manualEmailVerificationToggle.addEventListener("change", function() { updateEmailVerificationToggleLabel("manual"); });
20573:    if (typeof bindBatchEmailVerificationEvents === "function") bindBatchEmailVerificationEvents();
```

## E-19 预估渲染

文件：`src/main/resources/static/app.js`；读取命令等价于 `nl -ba src/main/resources/static/app.js` 后按列出的行段截取。

```text
18962:     }, 500);
18963: }
18964: 
18965: /* baseHintHtml：与改动前逐字一致的既有命中行（P4b T4b-4）。 */
18966: function baseHintHtml(total, res) {
18967:     var pending = Number(res.pending || 0);
18968:     var retryable = Number(res.retryable || 0);
18969:     return "当前条件命中 <strong>" + total + "</strong> 位专家（其中未联系 " + pending +
18970:         "、可重试 " + retryable + "）";
18971: }
18972: 
18973: function refreshRecipientPreview(kind) {
18974:     if (kind !== "editor" && kind !== "manual") return;
18975:     var hint = document.getElementById(recipientPreviewHintId(kind));
18976:     if (!hint) return;
18977:     var seq = ++recipientPreviewRequestSeq[kind];          // I4b-2：两次请求共用同一 seq
18978:     var snapshot = kind === "editor" ? buildConfigEditorRecipientSnapshot() : buildManualExecutionSnapshot();
18979:     hint.innerHTML = "当前条件命中 <strong>计算中…</strong>";
18980: 
18981:     var post = function(gateOn) {
18982:         return api("/api/mail/batch-send/recipients/preview", {
18983:             method: "POST",
18984:             body: JSON.stringify(Object.assign({}, snapshot, { gateFilterEnabled: gateOn }))
18985:         });
18986:     };
18987:     // I4b-6：不可用态只发一次（当前全库门禁字段为空，双发会让 ES 计数量翻倍且结果恒等）
18988:     var gateAvailable = batchGateState[kind].available;
18989:     var requests = gateAvailable ? [post(false), post(true)] : [post(false)];
18990: 
18991:     Promise.all(requests).then(function(results) {
18992:         if (seq !== recipientPreviewRequestSeq[kind]) return;   // I4b-2
18993:         var totalOf = function(r) {
18994:             var p = Number(r.pending || 0), t = Number(r.retryable || 0);
18995:             return Number(r.totalSendable != null ? r.totalSendable : (p + t));
18996:         };
18997:         var off = results[0];
18998:         var offTotal = totalOf(off);
18999:         var gateOn = gateAvailable && document.getElementById(gateToggleId(kind)).checked;
19000:         if (!gateAvailable) {
19001:             hint.innerHTML = baseHintHtml(offTotal, off);
19002:             return;
19003:         }
19004:         var onTotal = totalOf(results[1]);
19005:         var excluded = Math.max(0, offTotal - onTotal);        // I4b-1
19006:         if (gateOn) {
19007:             hint.innerHTML = baseHintHtml(onTotal, results[1]) +
19008:                 "；门禁过滤已排除 <span class=\"batch-gate-excluded\">" + excluded + "</span> 位";
19009:         } else {
19010:             hint.innerHTML = baseHintHtml(offTotal, off) +
19011:                 (excluded > 0
19012:                     ? "<span class=\"batch-gate-warnline\">其中 " + excluded +
19013:                       " 位缺少该模板必填字段，发送时会被门禁拦下并计入失败。</span>"
19014:                     : "");
19015:         }
19016:     }).catch(function(error) {
19017:         if (seq !== recipientPreviewRequestSeq[kind]) return;   // I4b-2：失败也要比 seq
19018:         var message = error && error.message ? String(error.message) : "请求失败";
19019:         console.warn("Recipient preview failed", error);
19020:         hint.textContent = "预估失败：" + message;
19021:     });
19022: }
19023: 
20020: 
20021: /* 受控码 → 中文解释（其余一律原样展示，不猜含义）。来源：
20022:    BatchEmailVerificationErrorCodes / BatchOutcomeReasonCodes / appendEmailAbnormalTag。 */
20023: var BATCH_EMAIL_VERIFICATION_ERROR_LABELS = {
20024:     EMAIL_VERIFY_AUTH_ERROR: "验证服务鉴权失败",
20025:     EMAIL_VERIFY_NO_CREDITS: "验证服务额度不足",
20026:     EMAIL_VERIFY_RATE_LIMITED: "验证服务限流",
20027:     EMAIL_VERIFY_TIMEOUT: "验证服务超时",
20028:     EMAIL_VERIFY_INCOMPLETE: "验证服务未返回结果",
20029:     EMAIL_VERIFY_BAD_RESPONSE: "验证服务返回非法响应",
20030:     EMAIL_VERIFY_SERVICE_ERROR: "验证服务故障"
20031: };
20032: var BATCH_EMAIL_VERIFICATION_SEND_REASON_LABELS = {
20033:     EMAIL_VERIFICATION_REJECTED: "验证未通过，未发送",
20034:     SEND_EXCEPTION: "发送异常",
20035:     TEMPLATE_RENDER_FAILED: "模板渲染失败",
20036:     PERSONALIZATION_INCOMPLETE: "个性化字段缺失",
20037:     ACCOUNT_UNAVAILABLE: "邮箱账号不可用",
20038:     DEDUP: "重复目标已跳过",
20039:     CANCELLED: "执行已取消",
20040:     SUPPRESSED: "退订/抑制",
20041:     DAILY_CAP_EXCEEDED: "超日限额",
20042:     EMAIL_CHANGED: "验证后收件地址发生变化"
20043: };
20044: var BATCH_EMAIL_VERIFICATION_TAG_ERROR_LABELS = {
20045:     MISSING_DOC_ID: "缺少专家文档 ID",
20046:     NO_MATCHING_DOC: "未匹配到专家文档",
20047:     ES_READ_FAILED: "读取专家文档失败",
20048:     ES_WRITE_FAILED: "写入标签失败"
20049: };
20050: 
20051: function emailVerificationDecisionText(row, running) {
20052:     if (row.decision === "PASS") return "按策略放行";
20053:     if (row.decision === "SKIP") return "未通过";
20054:     if (row.decision === "ERROR") return "验证服务异常";
20055:     if (row.decision === "PENDING") return running ? "验证中" : "验证未完成";
20056:     return String(row.decision || "—");
20057: }
20058: 
20059: function emailVerificationDecisionBadgeClass(row) {
20060:     if (row.decision === "PASS") return "ok";
20061:     if (row.decision === "SKIP") return "warn";
20062:     if (row.decision === "ERROR") return "error";
20063:     return "info";
20064: }
20065: 
20066: /* 验证原因：ERROR 走受控错误码解释；其余保留 provider 原 state/reason，未知值原样展示。 */
20067: function emailVerificationReasonText(row) {
20068:     var provider = [row.providerState, row.providerReason]
20069:         .filter(function(v) { return v != null && String(v) !== ""; })
20070:         .map(String)
20071:         .join(" / ");
20072:     if (row.decision === "ERROR") {
20073:         var code = row.errorCode ? String(row.errorCode) : "";
20074:         var label = code ? BATCH_EMAIL_VERIFICATION_ERROR_LABELS[code] : "";
20075:         var codeText = label ? label + "（" + code + "）" : (code || "验证服务异常");
20076:         return provider ? codeText + "；供应商： " + provider : codeText;
20077:     }
20078:     return provider || "—";
20079: }
20080: 
20081: function emailVerificationSendReasonText(raw) {
20082:     var code = String(raw || "");
20083:     var label = BATCH_EMAIL_VERIFICATION_SEND_REASON_LABELS[code];
20084:     return label ? label + "（" + code + "）" : code;
20085: }
20086: 
20087: /* PASS≠发送成功：发送结果只由 send_status 决定；SENDING 在执行终态时是「结果未确认」。 */
20088: function emailVerificationSendText(row, running) {
20089:     var reason = row.sendReason ? emailVerificationSendReasonText(row.sendReason) : "";
20090:     var suffix = reason ? "（" + reason + "）" : "";
20091:     if (row.sendStatus === "SENT") return "已发送";
20092:     if (row.sendStatus === "FAILED") return "发送失败" + suffix;
20093:     if (row.sendStatus === "SKIPPED") return "已跳过" + suffix;
20094:     if (row.sendStatus === "SENDING") return running ? "发送中" : "结果未确认";
20095:     if (row.sendStatus === "NOT_SENT") return "未发送" + suffix;
20096:     return String(row.sendStatus || "—") + suffix;
20097: }
20098: 
20099: function emailVerificationTagErrorText(raw) {
20100:     var text = String(raw || "");
20150: function batchEmailVerificationMetricsHtml(summary) {
20151:     var cells = [
20152:         { label: "策略放行", value: summary.passed, cls: "is-success" },
20153:         { label: "未通过", value: summary.rejected, cls: "is-skipped" },
20154:         { label: "服务异常", value: summary.errors, cls: "is-failure" }
20155:     ];
20156:     return cells.map(function(cell) {
20157:         return '<div class="batch-log-metric ' + cell.cls + '">' +
20158:             '<div class="batch-log-metric-label">' + escapeHtml(cell.label) + '</div>' +
20159:             '<div class="batch-log-metric-value">' + escapeHtml(String(Number(cell.value || 0))) + '</div>' +
20160:             '</div>';
20161:     }).join("");
20162: }
20163: 
20164: /* note 四类：关闭 / 尚未进入 / 正常分页说明 / 加载失败（is-error，见 renderBatchEmailVerificationFailure）。 */
20504:     if (manualGateTemplate) manualGateTemplate.addEventListener("change", function() {
20505:         refreshBatchGateState("manual");
20506:         if (typeof refreshEmailVerificationState === "function") refreshEmailVerificationState("manual");
20507:     });
20508:     var editorGateToggle = document.getElementById("batchConfigEditorGateFilter");
20509:     if (editorGateToggle) editorGateToggle.addEventListener("change", function() { updateGateToggleLabel("editor"); scheduleRecipientPreview("editor"); });
20510:     var manualGateToggle = document.getElementById("batchManualGateFilter");
20511:     if (manualGateToggle) manualGateToggle.addEventListener("change", function() { updateGateToggleLabel("manual"); scheduleRecipientPreview("manual"); });
20512:     // 03 T2：开关 label 同步（手动面板的差异标记由既有 input/change 监听负责）。
20513:     var editorEmailVerificationToggle = document.getElementById("batchConfigEditorEmailVerification");
20514:     if (editorEmailVerificationToggle) editorEmailVerificationToggle.addEventListener("change", function() { updateEmailVerificationToggleLabel("editor"); });
20515:     var manualEmailVerificationToggle = document.getElementById("batchManualEmailVerification");
20516:     if (manualEmailVerificationToggle) manualEmailVerificationToggle.addEventListener("change", function() { updateEmailVerificationToggleLabel("manual"); });
20517: 
20518:     // Manual source search — autocomplete
20519:     var sourceQuery = document.getElementById("batchManualSourceQuery");
20520:     if (sourceQuery) {
20521:         sourceQuery.addEventListener("focus", function() {
20522:             loadBatchManualSourceOptions("");
20523:         });
20524:         sourceQuery.addEventListener("input", function() {
20525:             handleManualSourceSearch();
20526:         });
20527:         sourceQuery.addEventListener("keydown", function(event) {
20528:             if (event.key === "Escape") closeBatchManualSourceDropdown();
20529:         });
20530:         sourceQuery.addEventListener("blur", function() {
20531:             setTimeout(function() {
20532:                 if (!document.querySelector(".batch-manual-source-dropdown-item:hover")) {
20533:                     closeBatchManualSourceDropdown();
20534:                 }
20535:             }, 150);
20536:         });
20537:     }
20538: 
20539:     // Clear source
20540:     var clearSourceBtn = document.getElementById("batchManualClearSourceBtn");
20541:     if (clearSourceBtn) clearSourceBtn.addEventListener("click", clearManualSource);
20542: 
20543:     // Manual form diff detection
20544:     var manualInputs = document.querySelectorAll("#batchManualPanel input, #batchManualPanel select");
20545:     manualInputs.forEach(function(input) {
20546:         input.addEventListener("input", function() {
20547:             computeAndRenderDiffs();
20548:             scheduleRecipientPreview("manual");
20549:         });
20550:         input.addEventListener("change", function() {
20551:             var v = readManualFormValues();
20552:             if (!batchTaskState.manualDraft) batchTaskState.manualDraft = {};
20553:             Object.assign(batchTaskState.manualDraft, v);
20554:             computeAndRenderDiffs();
20555:             scheduleRecipientPreview("manual");
20556:         });
20557:     });
20558: 
20559:     // Execute button
20560:     var executeBtn = document.getElementById("batchManualExecuteBtn");
20561:     if (executeBtn) executeBtn.addEventListener("click", handleManualExecute);
```

## E-20 测试框架和现有覆盖

命令：
```sh
rg -n "mysqlIt|migrationIt|surefire|node --test|maven.compiler.release" pom.xml; rg -n "fun .*verification|fun .*249|fun .*reusable|fun .*reuse|fun .*shrinks|fun .*filtered|fun .*legacy|fun .*snapshot|fun .*email" src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt
```
退出码：0

```text
22:        <maven.compiler.release>11</maven.compiler.release>
23:        <migrationIt>false</migrationIt>
24:        <mysqlIt>false</mysqlIt>
173:                <artifactId>maven-surefire-plugin</artifactId>
176:                        <migrationIt>${migrationIt}</migrationIt>
177:                        <mysqlIt>${mysqlIt}</mysqlIt>
199:                                <argument>node --test src/test/js/*.test.js</argument>
243:                <migrationIt>true</migrationIt>
249:                <mysqlIt>true</mysqlIt>
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt:84:    fun `deduplicates generated email ids without uppercasing ES id`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt:104:    fun `loads next ES page when entire page is filtered by seenOrcids`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt:127:    fun `does not skip candidates when ES result set shrinks between pages`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:104:    fun `historical risky and unknown skips are reclassified for database and memory reuse`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:159:    fun `the verification key is the normalized address with tag and dot preserved`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:175:    fun `a single 249 is retried once and the second answer decides`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:198:    fun `two consecutive 249 answers are an incomplete error and never a tag`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:267:    fun `the same address in one execution is reused with zero extra requests and the original time`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:293:    fun `a new execution reuses the same address within a year`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:312:    fun `service errors are never reused inside one execution`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:344:    fun `cancellation during verification returns cancelled without a physical request`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:375:    fun `database and memory reuse keep the original id without chaining or extending time`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:401:    fun `historical reuse audit failure does not fall back to the provider`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:436:    fun `only copies with matching real id orcid and current email are tagged`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:616:    private fun document(orcidId: String, email: String, docId: String = DOC_ID): ExpertProfile = ExpertProfile(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt:639:    private fun ok(email: String, state: String, reason: String? = null): EmailableHttpResponse {
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt:323:    fun `new policy risky and unknown passes are reusable originals`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt:336:    fun `one calendar year is a strict boundary and a reuse cannot renew it`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt:345:    fun `retention preserves original results for a year but not ordinary or reused execution rows`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:314:    fun `create reuses name after soft delete`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:527:    fun `getLegacyConfig reads active legacy_code entity as BatchSendConfig`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:548:    fun `getLegacyConfig returns 404 when legacy entity missing or soft-deleted`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:813:    fun `create persists emailDomains multi-value and get returns them in order (I2a-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:832:    fun `create normalizes whitespace and duplicate emailDomains (I2a-5)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:849:    fun `create rejects emailDomain containing comma (I2a-5)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:860:    fun `create with empty emailDomains persists empty json and view returns empty (I2a-2)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:875:    fun `updateLegacyConfig preserves existing emailDomainsJson entity value (I2a-6)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:919:    fun `getLegacyConfig degrades multi emailDomains to first (I2a-6)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:1517:    fun `invalid senderAccountCodesJson is rejected on read and snapshot instead of degrading to unrestricted (I-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:1528:    fun `create round-trips senderAccountCodes into the launch snapshot (I-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:1607:    fun `legacy row without the column still reads as an unrestricted empty list (I-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:1620:    fun `create persists emailVerificationEnabled true into entity view and launch snapshot (I-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:1637:    fun `create without emailVerificationEnabled defaults to false in entity view and snapshot (I-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:1750:    fun `updateLegacyConfig preserves existing emailVerificationEnabled entity value (I-1 I-2)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:1794:    fun `create rejects emailVerificationEnabled true with a MATERIAL_REMINDER template without saving (I-3)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:1808:    fun `update rejects emailVerificationEnabled true on an existing MATERIAL_REMINDER config without saving (I-3)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:1831:    fun `legacy row without the column reads switch false in view and snapshot (I-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:275:    fun `countBySnapshot matches execution path totalEstimate for same snapshot (I-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:348:    fun `countBySnapshot MATERIAL_REMINDER reuses material snapshot targets (I-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:851:    fun `run splits snapshot into multiple rounds`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:988:    fun `run marks EMAIL_INVALID on PERMANENT SMTP error and excludes from next snapshot`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1236:    fun `countPending reads emailDomain from configuration`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1251:    fun `run passes configured emailDomain to ES filter`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1631:    fun `runScheduledBatch legacy config without expertTypes sends nobody (I4-2)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3053:    fun `retryable contact filtered when country region outside scope regions`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3327:    fun `buildEsFiltersForLevel produces exactly one should OR for multi emailDomains (I2a-3)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3352:    fun `buildEsFiltersForLevel emits no email wildcard for empty emailDomains (I2a-2)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3368:    fun `matchesExpert applies emailDomains any-OR and skips judgment when empty (I2a-2 I2a-4)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3393:    fun `matchesExpert agrees with emailDomainsFilter semantics per profile (I2a-4)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3533:    fun `operatorStatusPredicate is a pure predicate without email or EMAIL_INVALID terms (I3a-2)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3966:    fun `preview and execution resolve identical gateEsFields for same snapshot (I4a-4)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4415:    fun `preview and execution use identical direction filters for the same snapshot (I-2)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4479:    fun `persisted config carries the direction state into the snapshot and ES filters (I-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4727:    fun `startManual accepts a snapshot whose senderAccountCodes exist (I-2)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4916:    fun `run with empty senderAccountCodes keeps the legacy selection path (I-2)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5025:    fun `verification disabled never touches the verification service (I-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5036:    fun `material reminder with verification enabled is rejected before any business write (I-1)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5058:    fun `verification enabled requires a configured api key before any business write (I-8)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5103:    fun `a verification service failure stops the run with remaining preserved and no abnormal tag (I-4 I-7)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5132:    fun `a failing verification on the first target reports FAILED (I-4)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5184:    fun `an smtp failure records FAILED on the verification row (I-6)`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5278:    fun `twelve verification skips still fill twenty successful sends`() {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5460:    private fun verificationTarget() = EmailVerificationTarget(null, "", null, "")
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:5462:    private fun expert(orcidId: String, email: String): ExpertProfile =
```

## E-21 实际MySQL测试环境

文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt`；读取命令等价于 `nl -ba src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt` 后按列出的行段截取。

```text
1: package com.weibo.talentintroduction.campaign.repository
2: 
3: import org.junit.jupiter.api.AfterAll
4: import org.junit.jupiter.api.Assertions.assertEquals
5: import org.junit.jupiter.api.Assertions.assertFalse
6: import org.junit.jupiter.api.Assertions.assertNull
7: import org.junit.jupiter.api.Assertions.assertThrows
8: import org.junit.jupiter.api.Assertions.assertTrue
9: import org.junit.jupiter.api.BeforeAll
10: import org.junit.jupiter.api.BeforeEach
11: import org.junit.jupiter.api.Test
12: import org.junit.jupiter.api.condition.EnabledIfSystemProperty
13: import org.springframework.beans.factory.annotation.Autowired
14: import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
15: import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
16: import org.springframework.context.annotation.Import
17: import org.springframework.dao.DataIntegrityViolationException
18: import org.springframework.jdbc.core.JdbcTemplate
19: import org.springframework.test.context.DynamicPropertyRegistry
20: import org.springframework.test.context.DynamicPropertySource
21: import org.springframework.test.context.TestPropertySource
22: import org.testcontainers.DockerClientFactory
23: import org.testcontainers.containers.MySQLContainer
24: import java.time.LocalDateTime
25: 
26: /**
27:  * 验证明细仓储的真实 MySQL 集成测试（`-DmysqlIt=true`，testcontainers MySQL 8.0.36）。
28:  *
29:  * 表由真实 V138 迁移建立（不是手写 DDL），因此同时验证迁移契约：H2 不能替代
30:  * utf8mb4_bin 身份键比较、唯一约束、条件 UPDATE 的受影响行数与 ON DELETE CASCADE。
31:  *
32:  * 覆盖子计划 01 的验收：
33:  * - **I-9**：唯一键 (task_execution_id, orcid_id, email)、索引 (task_execution_id, id) 游标分页
34:  *   （默认 50 / 最大 100 / hasMore 由 limit+1 判定）、汇总来自本表、执行删除级联清理。
35:  * - **I-6**：先插 PENDING（列缺省值）；recordDecision 只作用于 PENDING 行，结果固定后只更新发送/标签列；
36:  *   markSending 条件预占必须影响 1 行；超长身份键明确拒绝而不截断。
37:  */
38: @EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
39: @DataJdbcTest
40: @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
41: @TestPropertySource(
42:     properties = [
43:         "spring.flyway.placeholder-replacement=false"
44:     ]
45: )
46: @Import(BatchEmailVerificationRepository::class)
47: class BatchEmailVerificationRepositoryIT {
48: 
49:     companion object {
50:         private const val EXECUTION_ID = 9001L
51:         private const val OTHER_EXECUTION_ID = 9002L
52:         private val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 24, 10, 15, 30, 123_000_000)
53: 
54:         private class KotlinMySqlContainer(image: String) :
55:             MySQLContainer<KotlinMySqlContainer>(image)
56: 
57:         private val mysql = KotlinMySqlContainer("mysql:8.0.36")
58:             .withDatabaseName("talent_introduction")
59:             .withUsername("test")
60:             .withPassword("test")
61: 
62:         @JvmStatic
63:         @BeforeAll
64:         fun startMysql() {
65:             check(DockerClientFactory.instance().isDockerAvailable) {
66:                 "Docker is required for the batch email verification tests"
67:             }
68:             mysql.start()
69:         }
70: 
71:         @JvmStatic
72:         @AfterAll
73:         fun stopMysql() {
74:             if (mysql.isRunning) mysql.stop()
75:         }
76: 
77:         @JvmStatic
78:         @DynamicPropertySource
79:         fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
80:             registry.add("spring.datasource.url", mysql::getJdbcUrl)
81:             registry.add("spring.datasource.username", mysql::getUsername)
82:             registry.add("spring.datasource.password", mysql::getPassword)
83:         }
84:     }
85: 
86:     @Autowired
87:     private lateinit var repository: BatchEmailVerificationRepository
88: 
89:     @Autowired
90:     private lateinit var jdbcTemplate: JdbcTemplate
91: 
92:     @BeforeEach
93:     fun cleanDetails() {
94:         jdbcTemplate.update("DELETE FROM batch_email_verification")
95:         jdbcTemplate.update("DELETE FROM task_execution")
96:         seedExecution(EXECUTION_ID)
97:         seedExecution(OTHER_EXECUTION_ID)
98:     }
```

## E-22 批量配置所有迁移

命令：
```sh
rg -l "batch_send_task_config" src/main/resources/db/migration | sort -V | xargs cat
```
退出码：0

```text
CREATE TABLE batch_send_task_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(120) NOT NULL,
    mail_type VARCHAR(32) NOT NULL,
    auto_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    cron VARCHAR(64) NOT NULL,
    daily_cap INT NOT NULL,
    round_size INT NOT NULL,
    per_mail_interval_ms BIGINT NOT NULL,
    per_round_interval_ms BIGINT NOT NULL,
    self_check_ttl_minutes INT NOT NULL,
    funnel_level VARCHAR(32) NULL,
    tags_json TEXT NOT NULL,
    email_domain VARCHAR(120) NULL,
    discipline VARCHAR(120) NULL,
    template_id BIGINT NULL,
    legacy_code VARCHAR(64) NULL,
    deleted_at DATETIME NULL,
    -- Active-name uniqueness: NULL when soft-deleted so names can be reused.
    active_config_name VARCHAR(120)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, config_name, NULL)) STORED,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_send_task_config_legacy_code (legacy_code),
    UNIQUE KEY uk_batch_send_task_config_active_name (active_config_name),
    KEY idx_batch_send_task_config_deleted_updated (deleted_at, updated_at),
    KEY idx_batch_send_task_config_auto_deleted (auto_enabled, deleted_at),
    KEY idx_batch_send_task_config_template (template_id),
    CONSTRAINT fk_batch_send_task_config_template
        FOREIGN KEY (template_id) REFERENCES mail_compose_template(id)
);

-- Seed INTRODUCTION default from old KV (idempotent via legacy_code).
INSERT INTO batch_send_task_config (
    config_name,
    mail_type,
    auto_enabled,
    cron,
    daily_cap,
    round_size,
    per_mail_interval_ms,
    per_round_interval_ms,
    self_check_ttl_minutes,
    funnel_level,
    tags_json,
    email_domain,
    discipline,
    template_id,
    legacy_code,
    deleted_at,
    created_at,
    updated_at
)
SELECT
    '默认介绍邮件任务',
    'INTRODUCTION',
    COALESCE((SELECT CASE WHEN setting_value = 'true' THEN 1 ELSE 0 END FROM batch_send_setting WHERE setting_key = 'batchSend.autoEnabled' LIMIT 1), 0),
    COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.cron' LIMIT 1), '0 0 0 * * ?'),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.dailyCap' LIMIT 1), 1000),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.roundSize' LIMIT 1), 50),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.perMailIntervalMs' LIMIT 1), 1000),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.perRoundIntervalMs' LIMIT 1), 60000),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.selfCheckTtlMinutes' LIMIT 1), 30),
    NULL,
    '[]',
    NULLIF(COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.emailDomain' LIMIT 1), ''), ''),
    NULLIF(COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.discipline' LIMIT 1), ''), ''),
    (
        SELECT CASE
            WHEN setting_value IS NULL OR setting_value = '' THEN NULL
            ELSE CAST(setting_value AS UNSIGNED)
        END
        FROM batch_send_setting
        WHERE setting_key = 'batchSend.templateId'
        LIMIT 1
    ),
    'INTRODUCTION',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION'
);

-- Seed MATERIAL_REMINDER default from old KV (idempotent via legacy_code).
INSERT INTO batch_send_task_config (
    config_name,
    mail_type,
    auto_enabled,
    cron,
    daily_cap,
    round_size,
    per_mail_interval_ms,
    per_round_interval_ms,
    self_check_ttl_minutes,
    funnel_level,
    tags_json,
    email_domain,
    discipline,
    template_id,
    legacy_code,
    deleted_at,
    created_at,
    updated_at
)
SELECT
    '材料提醒任务',
    'MATERIAL_REMINDER',
    COALESCE((SELECT CASE WHEN setting_value = 'true' THEN 1 ELSE 0 END FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.autoEnabled' LIMIT 1), 0),
    COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.cron' LIMIT 1), '0 0 8 * * ?'),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.dailyCap' LIMIT 1), 60),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.roundSize' LIMIT 1), 30),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.perMailIntervalMs' LIMIT 1), 3000),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.perRoundIntervalMs' LIMIT 1), 120000),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.selfCheckTtlMinutes' LIMIT 1), 30),
    NULL,
    '[]',
    NULLIF(COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.emailDomain' LIMIT 1), ''), ''),
    NULLIF(COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.discipline' LIMIT 1), ''), ''),
    COALESCE(
        (
            SELECT CASE
                WHEN setting_value IS NULL OR setting_value = '' THEN NULL
                ELSE CAST(setting_value AS UNSIGNED)
            END
            FROM batch_send_setting
            WHERE setting_key = 'batchSend.materialReminder.templateId'
            LIMIT 1
        ),
        (SELECT id FROM mail_compose_template WHERE template_code = 'MATERIAL_REMINDER' LIMIT 1)
    ),
    'MATERIAL_REMINDER',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM batch_send_task_config WHERE legacy_code = 'MATERIAL_REMINDER'
);
ALTER TABLE task_execution
    ADD COLUMN batch_config_id BIGINT NULL;

CREATE INDEX idx_task_execution_batch_config_started
    ON task_execution (batch_config_id, started_at);

ALTER TABLE task_execution
    ADD CONSTRAINT fk_task_execution_batch_config
        FOREIGN KEY (batch_config_id) REFERENCES batch_send_task_config(id);
-- Keep this migration ASCII-only. Some deployment paths previously decoded
-- non-ASCII Flyway literals with the connection charset before persistence.
ALTER TABLE batch_send_task_config
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Repair only the two mojibake legacy seed names. HEX prefixes C3A9/C3A6 are
-- the UTF-8 encodings of the first mojibake characters, so operator renames
-- are not overwritten.
UPDATE batch_send_task_config
SET config_name = CONVERT(UNHEX('E9BB98E8AEA4E4BB8BE7BB8DE982AEE4BBB6E4BBBBE58AA1') USING utf8mb4)
WHERE legacy_code = 'INTRODUCTION'
  AND HEX(config_name) LIKE 'C3A9%';

UPDATE batch_send_task_config
SET config_name = CONVERT(UNHEX('E69D90E69699E68F90E98692E4BBBBE58AA1') USING utf8mb4)
WHERE legacy_code = 'MATERIAL_REMINDER'
  AND HEX(config_name) LIKE 'C3A6%';
-- I-5: rounds_per_run is NOT NULL with DEFAULT 1; backfill keeps existing rows at their
-- prior effective round budget (I-6): CEIL(daily_cap / round_size) is exactly the number
-- of rounds the dailyCap gate allowed to start, so the per-run send volume is unchanged.
ALTER TABLE batch_send_task_config ADD COLUMN rounds_per_run INT NOT NULL DEFAULT 1 AFTER round_size;

UPDATE batch_send_task_config SET rounds_per_run = GREATEST(1, CEIL(daily_cap / round_size));
ALTER TABLE batch_send_task_config DROP COLUMN daily_cap;
ALTER TABLE batch_send_task_config
    ADD COLUMN regions_json TEXT NOT NULL AFTER funnel_level;
UPDATE batch_send_task_config SET regions_json = '[]' WHERE regions_json = '';
-- P-E T-1: operator_status 可空，默认 NULL = 不限（与 expert_contact.operator_status 的
-- NOT NULL DEFAULT 'NOT_CONTACTED' 语义相反——这里是无筛选的"全部"，不是事实状态）。
-- 列宽沿用 V19 的 VARCHAR(32) 约定；值域由 OperatorStatus.entries 校验（service 层），
-- 不加 CHECK 约束以便枚举演进（与 expert_contact 同款立场）。
ALTER TABLE batch_send_task_config
    ADD COLUMN operator_status VARCHAR(32) NULL AFTER discipline;
-- I2a-1: email_domains_json 成为唯一事实源；email_domain 单值列在本迁移中删除，避免双事实源。
-- TEXT 列不能带 DEFAULT（MySQL 限制），故照 V93__add_regions_to_batch_send_task_config.sql
-- 的两步范式：先 ADD NOT NULL，再 UPDATE 兜底。
-- I2a-2: 空数组 [] = 不限（与旧 email_domain IS NULL / '' 等价）。
ALTER TABLE batch_send_task_config
    ADD COLUMN email_domains_json TEXT NOT NULL AFTER regions_json;

UPDATE batch_send_task_config
SET email_domains_json = CASE
        WHEN email_domain IS NULL OR email_domain = '' THEN '[]'
        ELSE CONCAT('["', email_domain, '"]')
    END;

ALTER TABLE batch_send_task_config DROP COLUMN email_domain;
-- I3a-7: operator_statuses_json 成为唯一事实源；operator_status 单值列在本迁移中删除。
-- 照 V93 的两步范式（TEXT 不能带 DEFAULT）。空数组 [] = 不限（与旧 operator_status IS NULL 等价）。
ALTER TABLE batch_send_task_config
    ADD COLUMN operator_statuses_json TEXT NOT NULL AFTER discipline;

UPDATE batch_send_task_config
SET operator_statuses_json = CASE
        WHEN operator_status IS NULL OR operator_status = '' THEN '[]'
        ELSE CONCAT('["', operator_status, '"]')
    END;

ALTER TABLE batch_send_task_config DROP COLUMN operator_status;
-- I4a-1: 存量配置一律回填 FALSE，保证行为零漂移。
-- BOOLEAN 列可带 DEFAULT（与 TEXT 不同），故无需 V93 的两步范式。
ALTER TABLE batch_send_task_config
    ADD COLUMN gate_filter_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER template_id;
-- I-6-5: 新列默认「不过滤」（NULL），存量配置升级后投放范围零漂移。
-- VARCHAR 可带 NULL 默认值，无需两步范式；存量行自动为 NULL = 不过滤。
ALTER TABLE batch_send_task_config
    ADD COLUMN reachability_filter VARCHAR(32) NULL COMMENT '可达性过滤模式：NULL=不过滤';
-- I2-4: expert_types_json 是唯一事实源；空数组 [] = 不限（与「不追加 filter」等价）。
-- 照 V98 两步范式：TEXT 列不能带 DEFAULT。
ALTER TABLE batch_send_task_config
    ADD COLUMN expert_types_json TEXT NOT NULL AFTER operator_statuses_json;

UPDATE batch_send_task_config SET expert_types_json = '[]';
-- I3-3: 三个值与 ExpertClassification.SENDABLE_TYPES（枚举前三值）逐字等价，
--       迁移后线上发信人群零变化。
-- I3-4: 只覆盖当前为空的行，不抹掉运营已手工勾选的配置。
UPDATE batch_send_task_config
SET expert_types_json = '["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]'
WHERE expert_types_json IS NULL
   OR expert_types_json = ''
   OR expert_types_json = '[]';
-- I-1/I-3: 批量任务研究方向三态（ANY/PRESENT/ABSENT）。
-- 存量任务一律回填默认值 ANY（不限），升级不改动旧任务收件范围（M-3）。
-- VARCHAR 列可带 DEFAULT（无需 V98/V108 的两步范式）；三态白名单的权威在
-- BatchSendTaskConfigService（ResearchDirectionFilters.ALLOWED），此处不建 CHECK 约束。
ALTER TABLE batch_send_task_config
    ADD COLUMN research_direction_filter VARCHAR(16) NOT NULL DEFAULT 'ANY' AFTER gate_filter_enabled;
-- ============================================================================
-- V129 expert_material_status 目录代码域扩展（fast-p 02 材料索取五项）
--
-- 版本号说明：本迁移原为 V128，与并行合并的 batch-research-direction-filter 分支的
-- V128__add_research_direction_filter_to_batch_send_task_config.sql 撞号；后者已于
-- 2026-09-18 10:57 应用到生产（multi_ai_kit_schema_history V128 = 4521c9c8…），
-- 故本文件改号 V129；SQL 语义不变，只把 DROP 改为存在性守卫（见下）。
--
-- 只替换 chk_expert_material_code：
--   * 原样保留 V111 的旧 7 代码（CV / PASSPORT / DEGREE / EMPLOYMENT /
--     PUBLICATIONS / PATENTS / RESEARCH）—— 旧 7 项目录、${pendingExpertMaterials}
--     与 RAG 的 CV 读取依赖这些行继续合法；
--   * 追加 02 独立目录 5 代码（REQ_PUBLICATIONS / REQ_PROJECTS / REQ_PATENTS /
--     REQ_AWARDS / REQ_DEGREES）。
-- 不改唯一键 uk_expert_material_contact_code、不改状态 CHECK chk_expert_material_status
-- （存储态仍只有 PROVIDED/DECLINED，缺行仍是唯一 PENDING 形态），
-- 不 INSERT/UPDATE/DELETE 任何既有行，也不做新旧目录互相推断（I-1、I-2）。
--
-- MySQL 兼容性（本次发布实测）：生产库为 MySQL 5.7.41，命名 CHECK 约束根本不落地
-- （V111 的 CHECK 被解析后忽略，生产 information_schema.TABLE_CONSTRAINTS 全库零 CHECK 行），
-- 而 `ALTER TABLE ... DROP CHECK` 是 8.0.16+ 语法，在 5.7 上直接 E1064 语法错误并中止发布。
-- 因此这里先用 TABLE_CONSTRAINTS（5.7/8.0 都有该视图）探测约束是否真实存在，
-- 仅在存在时动态执行 DROP；ADD CONSTRAINT ... CHECK 沿用 V36 的写法
-- （5.7 解析并忽略，8.0.16+ 真实生效）。
-- ============================================================================

SET @has_material_code_check := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'expert_material_status'
      AND CONSTRAINT_NAME = 'chk_expert_material_code'
      AND CONSTRAINT_TYPE = 'CHECK'
);

SET @drop_material_code_check := IF(
    @has_material_code_check > 0,
    'ALTER TABLE expert_material_status DROP CHECK chk_expert_material_code',
    'SELECT 1'
);

PREPARE drop_material_code_check_stmt FROM @drop_material_code_check;
EXECUTE drop_material_code_check_stmt;
DEALLOCATE PREPARE drop_material_code_check_stmt;

ALTER TABLE expert_material_status
    ADD CONSTRAINT chk_expert_material_code
        CHECK (material_code IN (
            'CV', 'PASSPORT', 'DEGREE', 'EMPLOYMENT', 'PUBLICATIONS', 'PATENTS', 'RESEARCH',
            'REQ_PUBLICATIONS', 'REQ_PROJECTS', 'REQ_PATENTS', 'REQ_AWARDS', 'REQ_DEGREES'
        ));
-- I-1: 批量任务的发件账号白名单（逻辑 mail_sender_account.account_code 的 JSON 数组）。
-- 空数组 [] = 不限制（旧任务与未传字段同义），非空 = 严格白名单；绝不按 inbound_mailbox_code
-- 合并共享 IMAP 的兄弟别名（LuKai / LuKai_QF 是两个独立 code）。
-- TEXT 列不能带 DEFAULT（MySQL 限制），故照 V93/V97/V98 的两步范式，另显式 MODIFY 收紧 NOT NULL：
-- 先加可空列，再把存量行回填 '[]'，最后收紧为 NOT NULL —— 存量任务行为零漂移。
ALTER TABLE batch_send_task_config
    ADD COLUMN sender_account_codes_json TEXT NULL AFTER expert_types_json;

UPDATE batch_send_task_config
SET sender_account_codes_json = '[]'
WHERE sender_account_codes_json IS NULL;

ALTER TABLE batch_send_task_config
    MODIFY COLUMN sender_account_codes_json TEXT NOT NULL;
-- I-1: 发送前邮箱验证开关。存量任务一律回填 FALSE（不暗中开启已验证策略），
-- 新列 NOT NULL 且带默认值，旧客户端不传该字段时写入默认关闭。
ALTER TABLE batch_send_task_config
    ADD COLUMN email_verification_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER research_direction_filter;
```

## E-23 配置DTO

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`；读取命令等价于 `nl -ba src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` 后按列出的行段截取。

```text
1: package com.weibo.talentintroduction.campaign.domain
2: 
3: import org.springframework.data.annotation.Id
4: import org.springframework.data.relational.core.mapping.Table
5: import java.time.LocalDateTime
6: 
7: @Table("batch_send_task_config")
8: data class BatchSendTaskConfig(
9:     @Id
10:     val id: Long? = null,
11:     val configName: String,
12:     val mailType: String,
13:     val autoEnabled: Boolean = false,
14:     val cron: String,
15:     val roundSize: Int,
16:     val roundsPerRun: Int = 1,
17:     val perMailIntervalMs: Long,
18:     val perRoundIntervalMs: Long,
19:     val selfCheckTtlMinutes: Int,
20:     val funnelLevel: String? = null,
21:     val tagsJson: String = "[]",
22:     val regionsJson: String = "[]",
23:     val emailDomainsJson: String = "[]",
24:     val discipline: String? = null,
25:     val operatorStatusesJson: String = "[]",
26:     val expertTypesJson: String = "[]",
27:     /**
28:      * I-1: 本次任务的逻辑发件账号白名单（`mail_sender_account.account_code` 的 JSON 数组）。
29:      * `[]` = 不限（旧行/未传字段同义）；坏 JSON 由 [parseSenderAccountCodes] 拒绝，不降级为 `[]`。
30:      */
31:     val senderAccountCodesJson: String = "[]",
32:     val templateId: Long? = null,
33:     val gateFilterEnabled: Boolean = false,
34:     /** I-1: 研究方向三态（[ResearchDirectionFilters]）；旧行与默认 = ANY（不限）。 */
35:     val researchDirectionFilter: String = ResearchDirectionFilters.ANY,
36:     /**
37:      * I-1: 发送前邮箱验证开关（迁移 V139 `email_verification_enabled BOOLEAN NOT NULL DEFAULT FALSE`）。
38:      * 旧行/未传字段 = false；只有 INTRODUCTION 允许 true；启动时逐字固定进执行快照。
39:      */
40:     val emailVerificationEnabled: Boolean = false,
41:     val legacyCode: String? = null,
42:     val deletedAt: LocalDateTime? = null,
43:     val createdAt: LocalDateTime? = null,
44:     val updatedAt: LocalDateTime? = null
45: )
46: 
47: data class BatchSendTaskConfigView(
48:     val id: Long,
49:     val configName: String,
50:     val mailType: String,
51:     val autoEnabled: Boolean,
52:     val cron: String,
53:     val roundSize: Int,
54:     val roundsPerRun: Int = 1,
55:     val perMailIntervalMs: Long,
56:     val perRoundIntervalMs: Long,
57:     val selfCheckTtlMinutes: Int,
58:     val funnelLevel: String?,
59:     val tags: List<String>,
60:     val regions: List<String> = emptyList(),
61:     val emailDomains: List<String> = emptyList(),
62:     val discipline: String?,
63:     val operatorStatuses: List<String> = emptyList(),
64:     val expertTypes: List<String> = emptyList(),
65:     /** I-1: 发件账号白名单回显；`[]` = 不限。 */
66:     val senderAccountCodes: List<String> = emptyList(),
67:     val templateId: Long?,
68:     val gateFilterEnabled: Boolean = false,
69:     /** I-1: 研究方向三态，永远回显权威值（旧任务 = ANY）。 */
70:     val researchDirectionFilter: String = ResearchDirectionFilters.ANY,
71:     /**
72:      * I-1: 发送前邮箱验证开关回显（c3 列表 pill / 编辑回填的读取面）；旧任务恒 false。
73:      */
74:     val emailVerificationEnabled: Boolean = false,
75:     val createdAt: LocalDateTime?,
76:     val updatedAt: LocalDateTime?,
77:     /** Next planned trigger time; null when the cron is invalid (I-1/I-2/I-3). */
78:     val nextFireTime: LocalDateTime? = null,
79:     /** Most recent execution start time (started_at), MANUAL or SCHEDULED; null when never executed (I-5). */
80:     val lastExecutedAt: LocalDateTime? = null
81: )
82: 
83: data class BatchSendTaskConfigCreateCommand(
84:     val configName: String,
85:     val autoEnabled: Boolean = false,
86:     val cron: String,
87:     val roundSize: Int,
88:     val roundsPerRun: Int = 1,
89:     val perMailIntervalMs: Long,
90:     val perRoundIntervalMs: Long,
91:     val selfCheckTtlMinutes: Int,
92:     val funnelLevel: String? = null,
93:     val tags: List<String> = emptyList(),
94:     val regions: List<String> = emptyList(),
95:     val emailDomains: List<String> = emptyList(),
96:     val discipline: String? = null,
97:     val operatorStatuses: List<String> = emptyList(),
98:     val expertTypes: List<String> = emptyList(),
99:     /** I-1: 未传值 = `[]`（不限）；非空为严格白名单，由配置服务 trim/去重并校验存在与非模拟器。 */
100:     val senderAccountCodes: List<String> = emptyList(),
101:     val templateId: Long? = null,
102:     val gateFilterEnabled: Boolean = false,
103:     /** I-1: 未传值 = ANY（不限）；非法值由配置服务拒绝。 */
104:     val researchDirectionFilter: String = ResearchDirectionFilters.ANY,
105:     /**
106:      * I-1: 发送前邮箱验证开关；未传值 = false（默认关闭，旧客户端同义）。
107:      * 仅 INTRODUCTION 允许 true，配置服务在模板解析后拒绝 MATERIAL_REMINDER + true。
108:      */
109:     val emailVerificationEnabled: Boolean = false
110: )
111: 
112: data class BatchSendTaskConfigUpdateCommand(
113:     val configName: String,
114:     val autoEnabled: Boolean,
115:     val cron: String,
116:     val roundSize: Int,
117:     val roundsPerRun: Int = 1,
118:     val perMailIntervalMs: Long,
119:     val perRoundIntervalMs: Long,
120:     val selfCheckTtlMinutes: Int,
121:     val funnelLevel: String? = null,
122:     val tags: List<String> = emptyList(),
123:     val regions: List<String> = emptyList(),
124:     val emailDomains: List<String> = emptyList(),
125:     val discipline: String? = null,
126:     val operatorStatuses: List<String> = emptyList(),
127:     val expertTypes: List<String> = emptyList(),
128:     /** I-1: 未传值 = `[]`（不限）；非空为严格白名单，由配置服务 trim/去重并校验存在与非模拟器。 */
129:     val senderAccountCodes: List<String> = emptyList(),
130:     val templateId: Long? = null,
131:     val gateFilterEnabled: Boolean = false,
132:     /** I-1: 未传值 = ANY（不限）；非法值由配置服务拒绝。 */
133:     val researchDirectionFilter: String = ResearchDirectionFilters.ANY,
134:     /**
135:      * I-1: 缺省或 null = 保留现值（旧客户端与旧 typed API 不清空开启状态）；显式 false = 关闭。
136:      * 合并由 `BatchSendTaskConfigService.update` 显式完成（`cmd.x ?: existing.x`），不走空值清空逻辑。
137:      */
138:     val emailVerificationEnabled: Boolean? = null
139: )
```

## E-24 runtime存储调用

命令：
```sh
rg -n "getRuntimeStatus|setRuntimeStatus|runtimeStatus|runtimeMode|pauseReason" src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt
```
退出码：0

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:121:    fun getRuntimeStatus(): BatchSendRuntimeState = getRuntimeStatus(BatchSendType.INTRODUCTION)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:123:    fun getRuntimeStatus(sendType: BatchSendType): BatchSendRuntimeState {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:126:            status = strValue(values, k(sendType, "runtimeStatus"), DEFAULT_RUNTIME_STATUS),
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:127:            mode = strValue(values, k(sendType, "runtimeMode"), DEFAULT_RUNTIME_MODE),
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:128:            pauseReason = strValue(values, k(sendType, "pauseReason"), DEFAULT_PAUSE_REASON)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:133:    fun setRuntimeStatus(status: String, mode: String, pauseReason: String) =
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:134:        setRuntimeStatus(status, mode, pauseReason, BatchSendType.INTRODUCTION)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:136:    fun setRuntimeStatus(status: String, mode: String, pauseReason: String, sendType: BatchSendType) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:137:        upsert(k(sendType, "runtimeStatus"), status)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:138:        upsert(k(sendType, "runtimeMode"), mode)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:139:        upsert(k(sendType, "pauseReason"), pauseReason)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt:270:    val pauseReason: String
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:49:        val state = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:52:            setRuntimeStatusInternal("PAUSED", state.mode, "INTERRUPTED", sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:112:        val state = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:143:        val state = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:214:        val state = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:219:        setRuntimeStatusInternal("PAUSED", state.mode, reason, sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:225:        val state = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:230:        setRuntimeStatusInternal("IDLE", state.mode, "", sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:236:        val state = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:241:        setRuntimeStatusInternal("PAUSED", "AUTO", "OPERATOR", sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:247:        val state = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:279:        val state = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:293:            pauseReason = state.pauseReason,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:344:            setRuntimeStatusInternal("RUNNING", mode.name, "", legacySendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:377:                        setRuntimeStatusInternal("PAUSED", mode.name, "EXECUTION_ERROR:${ex.message?.take(200)}", legacySendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:396:                setRuntimeStatusInternal("PAUSED", mode.name, "EXECUTOR_REJECTED", legacySendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:506:        val current = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:512:            setRuntimeStatusInternal("IDLE", mode.name, "", sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:524:                setRuntimeStatusInternal("PAUSED", mode.name, reason, sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:528:                setRuntimeStatusInternal("IDLE", mode.name, "", sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:537:        val state = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:545:        val state = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:552:        val state = getRuntimeStatusInternal(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:638:    private fun getRuntimeStatusInternal(sendType: BatchSendType): BatchSendRuntimeState =
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:639:        if (sendType == BatchSendType.INTRODUCTION) batchSendSettingService.getRuntimeStatus()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:640:        else batchSendSettingService.getRuntimeStatus(sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:642:    private fun setRuntimeStatusInternal(status: String, mode: String, reason: String, sendType: BatchSendType) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:643:        if (sendType == BatchSendType.INTRODUCTION) batchSendSettingService.setRuntimeStatus(status, mode, reason)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:644:        else batchSendSettingService.setRuntimeStatus(status, mode, reason, sendType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:676:                            pauseReason = item["pauseReason"] as? String,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:707:    val pauseReason: String,
```

## E-25 回归指令与工具可用性

命令：
```sh
command -v java; command -v mvn; command -v node; test -d /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home; rg -n "@EnabledIfSystemProperty|migrationIt|mysqlIt" src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt
```
退出码：0

```text
/Users/lukai/.jenv/shims/java
/opt/homebrew/bin/mvn
/opt/homebrew/bin/node
21:@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
```

## E-26 badge与metric完整规则

文件：`src/main/resources/static/styles.css`
```text
1052: 
1053: /* Badges */
1054: .badge {
1055:     display: inline-flex;
1056:     align-items: center;
1057:     padding: 2px 8px;
1058:     border-radius: 999px;
1059:     font-size: 11px;
1060:     font-weight: 600;
1061:     font-family: var(--font-body);
1062:     line-height: 1;
1063:     background-color: var(--surface);
1064:     color: var(--text-muted);
1065:     border: 1px solid transparent;
1066: }
1067: 
1068: .badge.ok {
1069:     background-color: var(--success-bg);
1070:     color: var(--success);
1071:     border-color: var(--success-border);
1072: }
1073: 
1074: .badge.warn {
1075:     background-color: var(--warning-bg);
1076:     color: var(--warning);
1077:     border-color: var(--warning-border);
1078: }
1079: 
1080: .badge.error {
1081:     background-color: var(--error-bg);
1082:     color: var(--error);
1083:     border-color: var(--error-border);
1084: }
1085: 
9820:   position: sticky;
9821:   z-index: 2;
9822:   bottom: -28px;
9823:   margin-right: -28px;
9824:   margin-bottom: -28px;
9825:   margin-left: -28px;
9826:   padding: 14px 28px;
9827:   border-top: 1px solid rgba(15, 23, 42, .08);
9828:   background: rgba(255, 255, 255, .96);
9829:   box-shadow: 0 -8px 20px rgba(15, 23, 42, .04);
9830:   backdrop-filter: blur(8px);
9831: }
9832: 
9833: .batch-manual-confirm-overlay { z-index: var(--z-confirm); }
9834: .batch-manual-confirm-dialog { width: min(620px, calc(100vw - 32px)); max-height: min(720px, calc(100vh - 40px)); overflow: auto; }
9835: .batch-manual-confirm-summary { padding: 12px; border-radius: var(--radius-md); background: var(--bg-subtle); color: var(--text-secondary); }
9836: .batch-manual-confirm-warning { margin-top: 12px; color: var(--error-strong); font-size: 12px; }
9837: .batch-manual-confirm-table { width: 100%; margin-top: 12px; border-collapse: collapse; }
9838: .batch-manual-confirm-table th,
9839: .batch-manual-confirm-table td { padding: 9px 10px; border-bottom: 1px solid rgba(15, 23, 42, .08); text-align: left; }
9840: .batch-manual-confirm-old { color: var(--text-muted); text-decoration: line-through; }
9841: .batch-manual-confirm-new { color: var(--error-strong); font-weight: 600; }
9842: 
9843: .batch-log-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 14px 0; }
9844: .batch-log-metric { padding: 10px; border: 1px solid rgba(15, 23, 42, .08); border-radius: var(--radius-md); background: var(--bg-subtle); }
9845: .batch-log-metric-label { color: var(--text-muted); font-size: 11px; }
9846: .batch-log-metric-value { margin-top: 3px; color: var(--text-main); font-size: 18px; font-weight: 700; }
9847: .batch-log-metric.is-success .batch-log-metric-value { color: var(--success); }
9848: .batch-log-metric.is-failure .batch-log-metric-value { color: var(--error); }
9849: .batch-log-metric.is-skipped .batch-log-metric-value { color: var(--warning); }
9850: 
9851: .batch-reason-list { margin: 8px 0 16px; border: 1px solid rgba(15, 23, 42, .08); border-radius: var(--radius-md); overflow: hidden; }
9852: .batch-reason-row { display: flex; justify-content: space-between; gap: 12px; padding: 10px 12px; color: var(--text-secondary); }
9853: .batch-reason-row + .batch-reason-row { border-top: 1px solid rgba(15, 23, 42, .08); }
9854: .batch-reason-count { color: var(--text-main); font-weight: 700; }
9855: .batch-log-integrity-warning { padding: 10px 12px; border-radius: var(--radius-md); background: var(--warning-bg); color: #c2410c; }
```

## E-27 复用class的已有使用点

```sh
rg -n "batch-gate-hint|batch-gate-field|batch-task-status-switch|batch-config-diff-badge|batch-task-scope-line|batch-config-editor-hint" src/main/resources/static/index.html src/main/resources/static/app.js
```
退出码：0
```text
src/main/resources/static/index.html:1411:                        <div class="batch-config-field batch-gate-field" id="editorFieldGateFilter">
src/main/resources/static/index.html:1416:                                    <span class="batch-task-status-switch"></span>
src/main/resources/static/index.html:1419:                                <span class="batch-gate-hint" id="batchConfigEditorGateFilterHint">仅向满足该模板必填字段的专家发送，缺字段的会在发送时被门禁拦下并计入失败。</span>
src/main/resources/static/index.html:1432:                        <div class="batch-config-field batch-gate-field" id="editorFieldEmailVerification">
src/main/resources/static/index.html:1437:                                    <span class="batch-task-status-switch" aria-hidden="true"></span>
src/main/resources/static/index.html:1440:                                <span class="batch-gate-hint" id="batchConfigEditorEmailVerificationHint">仅不可投递（undeliverable）跳过并标记邮箱异常；risky / unknown 按策略放行。服务异常停止本次执行。会消耗 Emailable 额度。</span>
src/main/resources/static/index.html:1464:                    <div class="batch-config-editor-hint" id="batchConfigEditorVolumeHint"></div>
src/main/resources/static/index.html:1465:                    <div class="batch-config-editor-hint" id="batchConfigEditorRecipientHint"></div>
src/main/resources/static/index.html:1539:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1549:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1563:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1577:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1591:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1605:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1616:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1626:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1640:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1654:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1657:                    <div class="batch-config-field batch-gate-field" id="manualFieldGateFilter">
src/main/resources/static/index.html:1662:                                <span class="batch-task-status-switch"></span>
src/main/resources/static/index.html:1665:                            <span class="batch-gate-hint" id="batchManualGateFilterHint">仅影响本次执行，不修改原定时任务。</span>
src/main/resources/static/index.html:1668:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1672:                <div class="batch-config-editor-hint" id="batchManualRecipientHint"></div>
src/main/resources/static/index.html:1681:                    <div class="batch-config-field batch-gate-field" id="manualFieldEmailVerification">
src/main/resources/static/index.html:1686:                                <span class="batch-task-status-switch" aria-hidden="true"></span>
src/main/resources/static/index.html:1689:                            <span class="batch-gate-hint" id="batchManualEmailVerificationHint">仅不可投递（undeliverable）跳过并标记邮箱异常；risky / unknown 按策略放行。服务异常停止本次执行。会消耗 Emailable 额度。仅影响本次执行。</span>
src/main/resources/static/index.html:1691:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1697:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1703:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1709:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1715:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1721:                        <span class="batch-config-diff-badge" hidden>已修改</span>
src/main/resources/static/index.html:1768:                    <span id="batchLogEmailVerificationPage" class="batch-gate-hint"></span>
src/main/resources/static/app.js:17897:            return '<span class="batch-task-scope-line">' + s + '</span>';
src/main/resources/static/app.js:17899:        : '<span class="batch-task-scope-line muted">无限制</span>';
src/main/resources/static/app.js:17905:                return '<span class="batch-task-scope-line">' + s + '</span>';
src/main/resources/static/app.js:17911:    scopeHtml += '<span class="batch-task-scope-line">' + batchGatePillHtml(c) + '</span>';
src/main/resources/static/app.js:17913:    scopeHtml += '<span class="batch-task-scope-line">' + (c.emailVerificationEnabled === true
src/main/resources/static/app.js:17926:            '<span class="batch-task-scope-line">下次 ' + (c.nextFireTime ? formatDateTime(c.nextFireTime) : "—") + '</span>' +
src/main/resources/static/app.js:17927:            '<span class="batch-task-scope-line">最近 ' + (c.lastExecutedAt ? formatDateTime(c.lastExecutedAt) : "—") + '</span>' +
src/main/resources/static/app.js:17943:        '<span class="batch-task-status-switch" aria-hidden="true"></span>' +
src/main/resources/static/app.js:18831:// 与门禁开关同构：复用 .batch-gate-field/.batch-gate-row/.batch-gate-toggle/.batch-gate-hint，
src/main/resources/static/app.js:19458:        var badge = el.querySelector(".batch-config-diff-badge");
src/main/resources/static/app.js:19483:        var badge = el.querySelector(".batch-config-diff-badge");
```

## E-28 真实前端映射和审计边界

文件：`src/main/resources/static/app.js`
```text
18028: function showBatchConfigEditor(config) {
18029:     var editor = document.getElementById("batchConfigEditor");
18030:     if (!editor) return;
18031:     var panel = document.getElementById("batchScheduledPanel");
18032:     if (panel) {
18033:         panel.classList.add("is-editing");
18034:         panel.scrollTop = 0;
18035:     }
18036:     var manualTab = document.getElementById("batchManualTab");
18037:     if (manualTab) manualTab.hidden = true;
18038:     editor.hidden = false;
18039:     var title = document.getElementById("batchConfigEditorTitle");
18040:     if (title) title.textContent = config ? "编辑定时任务" : "新增定时任务";
18041: 
18042:     // fill form
18043:     var setVal = function(id, val) { var el = document.getElementById(id); if (el) el.value = val || ""; };
18044:     setVal("batchConfigEditorName", config ? config.configName : "");
18045:     setVal("batchConfigEditorFunnelLevel", config ? (config.funnelLevel || "") : "");
18046:     setBatchTagPickerValue("batchConfigEditorTags", config && Array.isArray(config.tags) ? config.tags : []);
18047:     setVal("batchConfigEditorDiscipline", config ? (config.discipline || "") : "");
18048:     // I-1: 旧任务（无该字段）与新建任务都回显 ANY（不限）；不把上一条任务的值留在框里。
18049:     setVal("batchConfigEditorResearchDirectionFilter", config ? (config.researchDirectionFilter || "ANY") : "ANY");
18050:     setBatchMultiPickerValue("batchConfigEditorOperatorStatuses", config && Array.isArray(config.operatorStatuses) ? config.operatorStatuses : []);
18051:     setBatchMultiPickerValue("batchConfigEditorExpertTypes", config && Array.isArray(config.expertTypes) ? config.expertTypes : ["PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"]);
18052:     setVal("batchConfigEditorRoundsPerRun", config ? config.roundsPerRun : "1");
18053:     setVal("batchConfigEditorRoundSize", config ? config.roundSize : "50");
18054:     setBatchRegionPickerValue("batchConfigEditorRegions", config && Array.isArray(config.regions) ? config.regions : []);
18055:     setVal("batchConfigEditorPerMailIntervalSec", config ? Math.round((config.perMailIntervalMs || 1000) / 1000) : "1");
18056:     setVal("batchConfigEditorPerRoundIntervalSec", config ? Math.round((config.perRoundIntervalMs || 60000) / 1000) : "60");
18057:     setVal("batchConfigEditorSelfCheckTtlMin", config ? config.selfCheckTtlMinutes : "30");
18058:     batchTaskState.editorAutoEnabled = config ? Boolean(config.autoEnabled) : false;
18059:     var gateCheckbox = document.getElementById("batchConfigEditorGateFilter");
18060:     if (gateCheckbox) gateCheckbox.checked = Boolean(config && config.gateFilterEnabled);
18061:     // I-1：编辑从 View 回填，新建默认关闭；材料提醒的禁用/置回由 refreshEmailVerificationState 收口。
18062:     var emailVerificationCheckbox = document.getElementById("batchConfigEditorEmailVerification");
18063:     if (emailVerificationCheckbox) {
18064:         emailVerificationCheckbox.checked = Boolean(config && config.emailVerificationEnabled === true);
18065:     }
18066: 
18067:     // Cron 回显走白名单反解（I1-1）：只有完全匹配预设格式的表达式才映射到
18068:     // 每小时 / 每天 / 每周一；其余（范围、列表、步长、工作日、月/日限制…）
18069:     // 一律 custom 并原样回填，避免有损反解在保存时把表达式改写掉。
18070:     var freq = "daily", time = "09:00", customCron = "";
18900:     if (rawTemplate) {
18901:         var n = Number(rawTemplate);
18902:         if (Number.isFinite(n) && n > 0) templateId = n;
18903:     }
18904:     // I-1：生效值 = 勾选状态（材料提醒已被 refreshEmailVerificationState 禁用并置回 false）。
18905:     var emailVerificationEl = document.getElementById("batchConfigEditorEmailVerification");
18906:     return {
18907:         mailType: resolveBatchTemplateMailType(templateId),
18908:         roundSize: Number(val("batchConfigEditorRoundSize")) || 50,
18909:         roundsPerRun: Number(val("batchConfigEditorRoundsPerRun")) || 1,
18910:         perMailIntervalMs: (Number(val("batchConfigEditorPerMailIntervalSec")) || 0) * 1000,
18911:         perRoundIntervalMs: (Number(val("batchConfigEditorPerRoundIntervalSec")) || 0) * 1000,
18912:         selfCheckTtlMinutes: Number(val("batchConfigEditorSelfCheckTtlMin")) || 30,
18913:         funnelLevel: val("batchConfigEditorFunnelLevel") || null,
18914:         tags: readBatchTagPickerValue("batchConfigEditorTags"),
18915:         regions: readBatchRegionPickerValue("batchConfigEditorRegions"),
18916:         emailDomains: readBatchMultiPickerValue("batchConfigEditorEmailDomains"),
18917:         discipline: val("batchConfigEditorDiscipline") || null,
18918:         operatorStatuses: readBatchMultiPickerValue("batchConfigEditorOperatorStatuses"),
18919:         expertTypes: readBatchMultiPickerValue("batchConfigEditorExpertTypes"),
18920:         senderAccountCodes: readBatchMultiPickerValue("batchConfigEditorSenderAccounts"),
18921:         researchDirectionFilter: val("batchConfigEditorResearchDirectionFilter") || "ANY",
18922:         gateFilterEnabled: gateToggleChecked("editor"),
18923:         emailVerificationEnabled: Boolean(emailVerificationEl && emailVerificationEl.checked),
18924:         templateId: templateId
18925:     };
18926: }
18927: 
18928: function buildManualExecutionSnapshot() {
18929:     var values = readManualFormValues();
18930:     return {
18931:         mailType: values.mailType,
18932:         roundsPerRun: Number.isFinite(values.roundsPerRun) ? values.roundsPerRun : 1,
18933:         roundSize: Number.isFinite(values.roundSize) ? values.roundSize : 50,
18934:         perMailIntervalMs: Number.isFinite(values.perMailIntervalMs) ? values.perMailIntervalMs : 1000,
18935:         perRoundIntervalMs: Number.isFinite(values.perRoundIntervalMs) ? values.perRoundIntervalMs : 60000,
18936:         selfCheckTtlMinutes: Number.isFinite(values.selfCheckTtlMinutes) ? values.selfCheckTtlMinutes : 30,
18937:         funnelLevel: values.funnelLevel,
18938:         tags: values.tags,
18939:         regions: values.regions,
18940:         emailDomains: values.emailDomains,
18941:         discipline: values.discipline,
18942:         operatorStatuses: values.operatorStatuses,
18943:         expertTypes: values.expertTypes,
18944:         senderAccountCodes: values.senderAccountCodes,
18945:         researchDirectionFilter: values.researchDirectionFilter || "ANY",
18946:         gateFilterEnabled: values.gateFilterEnabled,
18947:         // I-1：与预估/执行共用同一完整快照，手动覆盖不回写原配置。
18948:         emailVerificationEnabled: values.emailVerificationEnabled,
18949:         templateId: values.templateId
18950:     };
18951: }
18952: 
19112: function openManualTabFromConfig(id) {
19113:     var config = batchTaskState.configs.find(function(c) { return Number(c.id) === Number(id); });
19114:     if (!config) return;
19115:     applyBatchManualSource(config);
19116:     switchBatchSendTab("manual");
19117: }
19118: 
19119: function applyBatchManualSource(config) {
19120:     if (!config) return;
19121:     batchTaskState.manualSource = deepCloneConfig(config);
19122:     batchTaskState.manualDraft = deepCloneConfig(config);
19123: 
19124:     var input = document.getElementById("batchManualSourceQuery");
19125:     if (input) input.value = config.configName || "";
19126:     var sourceId = document.getElementById("batchManualSourceId");
19127:     if (sourceId) sourceId.value = config.id == null ? "" : String(config.id);
19128:     var sourceUpdatedAt = document.getElementById("batchManualSourceUpdatedAt");
19129:     if (sourceUpdatedAt) sourceUpdatedAt.value = config.updatedAt || "";
19130: 
19131:     updateManualSourceInfo();
19132:     fillManualFormFromDraft();
19133: }
19134: 
19135: function resetManualExecution(opts) {
19136:     if (opts && opts.preserveSource === false) {
19137:         batchTaskState.manualSource = null;
19138:         batchTaskState.manualDraft = null;
19139:     }
19140:     var sourceQuery = document.getElementById("batchManualSourceQuery");
19141:     if (sourceQuery) sourceQuery.value = "";
19142:     var sourceId = document.getElementById("batchManualSourceId");
19143:     if (sourceId) sourceId.value = "";
19144:     var sourceUpdatedAt = document.getElementById("batchManualSourceUpdatedAt");
19145:     if (sourceUpdatedAt) sourceUpdatedAt.value = "";
19146: 
19147:     updateManualSourceInfo();
19148:     fillManualFormDefaults();
19149: }
19150: 
19151: function deepCloneConfig(c) {
19152:     return {
19153:         id: c.id || null,
19154:         templateId: c.templateId || null,
19155:         mailType: c.mailType || "INTRODUCTION",
19156:         funnelLevel: c.funnelLevel || "",
19157:         tags: Array.isArray(c.tags) ? c.tags.slice() : [],
19158:         regions: Array.isArray(c.regions) ? c.regions.slice() : [],
19159:         emailDomains: Array.isArray(c.emailDomains) ? c.emailDomains.slice() : [],
19160:         discipline: c.discipline || "",
19161:         operatorStatuses: Array.isArray(c.operatorStatuses) ? c.operatorStatuses.slice() : [],
19162:         expertTypes: Array.isArray(c.expertTypes) ? c.expertTypes.slice() : [],
19163:         senderAccountCodes: Array.isArray(c.senderAccountCodes) ? c.senderAccountCodes.slice() : [],
19164:         researchDirectionFilter: c.researchDirectionFilter || "ANY",
19165:         gateFilterEnabled: c.gateFilterEnabled === true,
19166:         emailVerificationEnabled: c.emailVerificationEnabled === true,
19167:         roundSize: c.roundSize || 50,
19168:         roundsPerRun: c.roundsPerRun || 1,
19169:         perMailIntervalMs: c.perMailIntervalMs || 1000,
19170:         perRoundIntervalMs: c.perRoundIntervalMs || 60000,
19171:         selfCheckTtlMinutes: c.selfCheckTtlMinutes || 30,
19172:         configName: c.configName || "",
19173:         updatedAt: c.updatedAt || null
19174:     };
19175: }
19176: 
19177: function fillManualFormDefaults() {
19178:     batchTaskState.manualDraft = {
19179:         templateId: null,
19180:         mailType: "INTRODUCTION",
19181:         funnelLevel: "",
19182:         tags: [],
19183:         regions: [],
19184:         emailDomains: [],
19185:         discipline: "",
19186:         operatorStatuses: [],
19187:         expertTypes: ["PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"],
19188:         senderAccountCodes: [],
19189:         researchDirectionFilter: "ANY",
19190:         gateFilterEnabled: false,
19191:         emailVerificationEnabled: false,
19192:         roundSize: 50,
19193:         roundsPerRun: 1,
19194:         perMailIntervalMs: 1000,
19195:         perRoundIntervalMs: 60000,
19196:         selfCheckTtlMinutes: 30,
19197:         configName: "",
19198:         updatedAt: null
19199:     };
19200:     fillManualFormFromDraft();
19201: }
19202: 
19203: function fillManualFormFromDraft() {
19204:     var d = batchTaskState.manualDraft;
19205:     if (!d) { fillManualFormDefaults(); return; }
19206: 
19207:     var setVal = function(id, v) { var el = document.getElementById(id); if (el) el.value = v || ""; };
19208:     setVal("batchManualTemplateId", d.templateId ? String(d.templateId) : "");
19209:     setVal("batchManualFunnelLevel", d.funnelLevel || "");
19210:     setBatchTagPickerValue("batchManualTags", Array.isArray(d.tags) ? d.tags : []);
19211:     setBatchRegionPickerValue("batchManualRegions", Array.isArray(d.regions) ? d.regions : []);
19212:     setBatchMultiPickerValue("batchManualEmailDomains", Array.isArray(d.emailDomains) ? d.emailDomains : []);
19213:     setVal("batchManualDiscipline", d.discipline || "");
19214:     setVal("batchManualResearchDirectionFilter", d.researchDirectionFilter || "ANY");
19215:     setBatchMultiPickerValue("batchManualOperatorStatuses", Array.isArray(d.operatorStatuses) ? d.operatorStatuses : []);
19216:     setBatchMultiPickerValue("batchManualExpertTypes", Array.isArray(d.expertTypes) ? d.expertTypes : []);
19217:     setBatchMultiPickerValue("batchManualSenderAccounts", Array.isArray(d.senderAccountCodes) ? d.senderAccountCodes : []);
19218:     setVal("batchManualRoundSize", d.roundSize);
19219:     setVal("batchManualRoundsPerRun", d.roundsPerRun);
19220:     setVal("batchManualPerMailIntervalSec", Math.round((d.perMailIntervalMs || 1000) / 1000));
19221:     setVal("batchManualPerRoundIntervalSec", Math.round((d.perRoundIntervalMs || 60000) / 1000));
19222:     setVal("batchManualSelfCheckTtlMin", d.selfCheckTtlMinutes);
19223: 
19224:     var gateCheckbox = document.getElementById("batchManualGateFilter");
19225:     if (gateCheckbox) gateCheckbox.checked = Boolean(d.gateFilterEnabled);
19226:     if (typeof updateGateToggleLabel === "function") updateGateToggleLabel("manual");
19227:     // I-1：手动草稿回填后由同一处收口材料提醒的禁用/置回 false（选源、还原、清空都走这里）。
19228:     var emailVerificationCheckbox = document.getElementById("batchManualEmailVerification");
19229:     if (emailVerificationCheckbox) emailVerificationCheckbox.checked = Boolean(d.emailVerificationEnabled);
19230: 
19231:     fillBatchManualTemplateSelector(d.templateId);
19232:     if (typeof refreshEmailVerificationState === "function") refreshEmailVerificationState("manual");
19233: 
19234:     computeAndRenderDiffs();
19235:     scheduleRecipientPreview("manual");
19236: }
19237: 
19238: function fillBatchManualTemplateSelector(selectedId) {
19239:     var select = document.getElementById("batchManualTemplateId");
19240:     if (!select) return;
19241:     var html = '<option value="">系统默认介绍邮件模板</option>';
19242:     html += supportedBatchComposeTemplates().map(function(t) { return '<option value="' + t.id + '">' + escapeHtml(t.templateName) + '</option>'; }).join("");
19243:     select.innerHTML = html;
19244:     select.value = selectedId ? String(selectedId) : "";
19245: }
19246: 
19247: function updateManualSourceInfo() {
19248:     var info = document.getElementById("batchManualSourceInfo");
19249:     var clearBtn = document.getElementById("batchManualClearSourceBtn");
19250:     var source = batchTaskState.manualSource;
19251: 
19252:     if (!info) return;
19253:     if (source) {
19254:         info.textContent = "来源：" + source.configName + " | 更新于 " + (source.updatedAt ? formatDateTime(source.updatedAt) : "—");
19255:         if (clearBtn) clearBtn.hidden = false;
19256:     } else {
19257:         info.textContent = "当前：独立手动执行（未关联定时配置）";
19258:         if (clearBtn) clearBtn.hidden = true;
19259:     }
19260: }
19261: 
19262: function clearManualSource() {
19263:     resetManualExecution({ preserveSource: false });
19264:     updateManualSourceInfo();
19265: }
19266: 
19267: function detachBatchManualSourcePreservingDraft() {
19268:     if (!batchTaskState.manualSource) return;
19269:     batchTaskState.manualDraft = Object.assign({}, batchTaskState.manualDraft || {}, readManualFormValues());
19270:     batchTaskState.manualSource = null;
19271:     var sourceId = document.getElementById("batchManualSourceId");
19272:     if (sourceId) sourceId.value = "";
19273:     var sourceUpdatedAt = document.getElementById("batchManualSourceUpdatedAt");
19274:     if (sourceUpdatedAt) sourceUpdatedAt.value = "";
19275:     updateManualSourceInfo();
19276:     clearAllDiffMarkers();
19277: }
19278: 
19279: // ── Diff Detection ──────────────────────────────────────────────────────────────────
19280: 
19319: function normalizeManualSnapshot(v) {
19320:     return {
19321:         templateId: v.templateId || null,
19322:         funnelLevel: (v.funnelLevel || "").trim() || null,
19323:         tags: (Array.isArray(v.tags) ? v.tags.slice() : []).map(function(t) { return t.trim(); }).filter(function(t) { return t.length > 0; }).sort().filter(function(t, i, arr) { return arr.indexOf(t) === i; }),
19324:         regions: (Array.isArray(v.regions) ? v.regions.slice() : []).map(function(r) { return r.trim(); }).filter(function(r) { return r.length > 0; }).sort().filter(function(r, i, arr) { return arr.indexOf(r) === i; }),
19325:         emailDomains: (Array.isArray(v.emailDomains) ? v.emailDomains : []).map(function(s) { return String(s).trim(); }).filter(Boolean).slice().sort(),
19326:         discipline: (v.discipline || "").trim() || null,
19327:         researchDirectionFilter: (v.researchDirectionFilter || "ANY").trim() || "ANY",
19328:         operatorStatuses: (Array.isArray(v.operatorStatuses) ? v.operatorStatuses : []).map(function(s){return String(s).trim();}).filter(Boolean).slice().sort(),
19329:         expertTypes: (Array.isArray(v.expertTypes) ? v.expertTypes : []).map(function(s){return String(s).trim();}).filter(Boolean).slice().sort(),
19330:         senderAccountCodes: (Array.isArray(v.senderAccountCodes) ? v.senderAccountCodes : []).map(function(s){return String(s).trim();}).filter(Boolean).slice().sort(),
19331:         gateFilterEnabled: Boolean(v.gateFilterEnabled),
19332:         emailVerificationEnabled: Boolean(v.emailVerificationEnabled),
19333:         roundSize: Number.isFinite(v.roundSize) ? v.roundSize : null,
19334:         roundsPerRun: Number.isFinite(v.roundsPerRun) ? v.roundsPerRun : null,
19335:         perMailIntervalMs: Number.isFinite(v.perMailIntervalMs) ? v.perMailIntervalMs : null,
19336:         perRoundIntervalMs: Number.isFinite(v.perRoundIntervalMs) ? v.perRoundIntervalMs : null,
19337:         selfCheckTtlMinutes: Number.isFinite(v.selfCheckTtlMinutes) ? v.selfCheckTtlMinutes : null
19338:     };
19339: }
19340: 
19341: function formatManualDiffValue(key, value) {
19342:     if (key === "gateFilterEnabled") return value ? "开启" : "关闭";
19343:     if (key === "emailVerificationEnabled") return value ? "开启" : "关闭";
19344:     if (key === "templateId") {
19345:         if (!value) return "系统默认介绍邮件模板";
19346:         var template = supportedBatchComposeTemplates().find(function(item) {
19347:             return Number(item.id) === Number(value);
19348:         });
19349:         return template ? template.templateName : "模板 #" + value;
19350:     }
19351:     if (key === "funnelLevel") return value || "全部层级";
19352:     if (key === "researchDirectionFilter") {
19353:         if (value === "PRESENT") return "有研究方向";
19354:         if (value === "ABSENT") return "无研究方向";
19355:         return "不限";
19356:     }
19357:     if (key === "emailDomains") return (Array.isArray(value) && value.length > 0) ? value.join("、") : "全部服务商";
19358:     if (key === "discipline") {
19359:         if (!value) return "全部学科";
19360:         if (value === "STEM") return "仅理工科";
19361:         if (value === "HUMANITIES") return "仅文社科";
19362:         if (value === "UNCLASSIFIED") return "未分类";
19363:         return String(value);
19364:     }
19365:     if (key === "operatorStatuses") return (Array.isArray(value) && value.length > 0) ? value.map(operatorStatusLabel).join("、") : "全部状态";
19366:     if (key === "expertTypes") {
19367:         var labels = batchExpertTypeOptions().reduce(function(acc, o) { acc[o.value] = o.label; return acc; }, {});
19368:         return (Array.isArray(value) && value.length > 0) ? value.map(function(v) { return labels[v] || v; }).join("、") : "全部类型";
19369:     }
19370:     if (key === "senderAccountCodes") return (Array.isArray(value) && value.length > 0) ? value.join("、") : "全部可发送账号";
19371:     if (key === "tags") {
19372:         var tags = Array.isArray(value) ? value : [];
19373:         return tags.length > 0 ? tags.join(", ") : "(无)";
19374:     }
19375:     return value == null || value === "" ? "未设置" : String(value);
19376: }
19377: 
19378: function computeManualDiffs() {
19379:     if (!batchTaskState.manualSource) return [];
19380:     var base = normalizeManualSnapshot(batchTaskState.manualSource);
19381:     var draft = readManualFormValues();
19382:     var dn = normalizeManualSnapshot(draft);
19383: 
19384:     var fieldDefs = [
19385:         { key: "templateId", label: "模板" },
19386:         { key: "funnelLevel", label: "漏斗层级" },
19387:         { key: "tags", label: "标签" },
19388:         { key: "regions", label: "地区" },
19389:         { key: "emailDomains", label: "邮箱服务商" },
19390:         { key: "discipline", label: "学科" },
19391:         { key: "researchDirectionFilter", label: "研究方向" },
19392:         { key: "operatorStatuses", label: "专家状态" },
19393:         { key: "expertTypes", label: "研发类型" },
19394:         { key: "senderAccountCodes", label: "发件邮箱" },
19395:         { key: "gateFilterEnabled", label: "邮件模版门禁过滤" },
19396:         { key: "emailVerificationEnabled", label: "发送前验证邮箱" },
19397:         { key: "roundsPerRun", label: "执行轮次" },
19398:         { key: "roundSize", label: "每轮数量" },
19399:         { key: "perMailIntervalMs", label: "每封间隔" },
19400:         { key: "perRoundIntervalMs", label: "每轮间隔" },
19401:         { key: "selfCheckTtlMinutes", label: "自检 TTL" }
19402:     ];
19403: 
19404:     var diffs = [];
19405:     fieldDefs.forEach(function(fd) {
19406:         var oldVal = base[fd.key];
19407:         var newVal = dn[fd.key];
19408:         if (fd.key === "tags") {
19409:             var oldArr = (oldVal || []).join(", ");
19410:             var newArr = (newVal || []).join(", ");
19411:             if (oldArr !== newArr) {
19412:                 diffs.push({ key: fd.key, label: fd.label, oldDisplay: formatManualDiffValue(fd.key, oldVal), newDisplay: formatManualDiffValue(fd.key, newVal) });
19413:             }
19414:         } else if (fd.key === "templateId") {
19415:             if (String(oldVal || "") !== String(newVal || "")) {
19416:                 diffs.push({ key: fd.key, label: fd.label, oldDisplay: formatManualDiffValue(fd.key, oldVal), newDisplay: formatManualDiffValue(fd.key, newVal) });
19417:             }
19418:         } else {
19419:             if (String(oldVal || "") !== String(newVal || "")) {
19420:                 diffs.push({ key: fd.key, label: fd.label, oldDisplay: formatManualDiffValue(fd.key, oldVal), newDisplay: formatManualDiffValue(fd.key, newVal) });
19421:             }
19422:         }
19423:     });
19424:     return diffs;
19425: }
19426: 
19427: function computeAndRenderDiffs() {
19428:     if (!batchTaskState.manualSource) {
19429:         clearAllDiffMarkers();
19430:         return;
19431:     }
19432:     var diffs = computeManualDiffs();
19433:     var diffKeys = {};
19434:     diffs.forEach(function(d) { diffKeys[d.key] = d; });
19435:     var fieldMap = {
19436:         templateId: "manualFieldTemplate",
19437:         funnelLevel: "manualFieldFunnelLevel",
19438:         tags: "manualFieldTags",
19439:         regions: "manualFieldRegions",
19440:         emailDomains: "manualFieldEmailDomain",
19441:         discipline: "manualFieldDiscipline",
19442:         researchDirectionFilter: "manualFieldResearchDirectionFilter",
19443:         operatorStatuses: "manualFieldOperatorStatus",
19444:         expertTypes: "manualFieldExpertTypes",
19445:         senderAccountCodes: "manualFieldSenderAccounts",
19446:         gateFilterEnabled: "manualFieldGateFilter",
19447:         emailVerificationEnabled: "manualFieldEmailVerification",
19448:         roundsPerRun: "manualFieldRoundsPerRun",
19449:         roundSize: "manualFieldRoundSize",
19450:         perMailIntervalMs: "manualFieldPerMailIntervalSec",
19451:         perRoundIntervalMs: "manualFieldPerRoundIntervalSec",
19452:         selfCheckTtlMinutes: "manualFieldSelfCheckTtlMin"
19453:     };
19454: 
19455:     Object.keys(fieldMap).forEach(function(key) {
19456:         var el = document.getElementById(fieldMap[key]);
19457:         if (!el) return;
19458:         var badge = el.querySelector(".batch-config-diff-badge");
19459:         var original = el.querySelector(".batch-config-diff-original");
19460:         if (diffKeys[key]) {
19461:             el.classList.add("is-config-diff");
19462:             if (badge) badge.hidden = false;
19463:             if (original) {
19464:                 original.hidden = false;
19465:                 original.textContent = "原：" + diffKeys[key].oldDisplay;
19466:             }
19467:         } else {
19468:             el.classList.remove("is-config-diff");
19469:             if (badge) badge.hidden = true;
19470:             if (original) original.hidden = true;
19471:         }
19472:     });
19473: }
19474: 
19475: function clearAllDiffMarkers() {
19476:     var fields = ["manualFieldTemplate", "manualFieldFunnelLevel", "manualFieldTags", "manualFieldRegions", "manualFieldEmailDomain",
19477:         "manualFieldDiscipline", "manualFieldResearchDirectionFilter", "manualFieldOperatorStatus", "manualFieldExpertTypes", "manualFieldSenderAccounts", "manualFieldGateFilter", "manualFieldEmailVerification", "manualFieldRoundsPerRun", "manualFieldRoundSize",
19478:         "manualFieldPerMailIntervalSec", "manualFieldPerRoundIntervalSec", "manualFieldSelfCheckTtlMin"];
19479:     fields.forEach(function(id) {
19480:         var el = document.getElementById(id);
19481:         if (!el) return;
19482:         el.classList.remove("is-config-diff");
19483:         var badge = el.querySelector(".batch-config-diff-badge");
19484:         var original = el.querySelector(".batch-config-diff-original");
19485:         if (badge) badge.hidden = true;
19486:         if (original) original.hidden = true;
19487:     });
19488: }
19489: 
19490: // ── Confirm Dialog ──────────────────────────────────────────────────────────────────
19491: 
19492: function showBatchManualConfirm() {
19493:     var source = batchTaskState.manualSource;
19494:     var diffs = source ? computeManualDiffs() : [];
19495:     var title = document.getElementById("batchManualConfirmTitle");
19496:     var body = document.getElementById("batchManualConfirmBody");
19497:     var dialog = document.getElementById("batchManualConfirmDialog");
19498:     if (!title || !body || !dialog) return;
19499: 
19500:     // I-1：确认页显示本次执行的开关值（同源读取勾选状态，不回写原配置）。
19501:     var emailVerificationEl = document.getElementById("batchManualEmailVerification");
19502:     var emailVerificationText = emailVerificationEl && emailVerificationEl.checked ? "开启" : "关闭";
19503: 
19504:     if (source && diffs.length > 0) {
19505:         title.textContent = "确认按修改后的配置执行？";
19506:         var tableRows = diffs.map(function(d) {
19507:             return '<tr><td>' + escapeHtml(d.label) + '</td>' +
19508:                 '<td class="batch-manual-confirm-old">' + escapeHtml(d.oldDisplay) + '</td>' +
19509:                 '<td class="batch-manual-confirm-new">' + escapeHtml(d.newDisplay) + '</td></tr>';
19510:         }).join("");
19511:         body.innerHTML =
19512:             '<p class="batch-manual-confirm-warning">以下参数与定时配置存在差异，执行不影响定时配置。</p>' +
19513:             '<table class="batch-manual-confirm-table">' +
19514:             '<thead><tr><th>字段</th><th>原值</th><th>新值</th></tr></thead>' +
19515:             '<tbody>' + tableRows + '</tbody></table>';
19516:     } else if (source) {
19517:         title.textContent = "确认执行该配置？";
19518:         body.innerHTML =
19519:             '<div class="batch-manual-confirm-summary">' +
19520:             '<strong>' + escapeHtml(source.configName) + '</strong><br>' +
19521:             '轮次: ' + source.roundsPerRun + ' 轮 · 每轮: ' + source.roundSize + ' 封<br>' +
19522:             '发送前验证邮箱: ' + emailVerificationText + '<br>' +
19523:             '来源配置: ' + escapeHtml(source.configName) +
19524:             '</div>';
19525:     } else {
19526:         title.textContent = "确认独立手动执行？";
19527:         body.innerHTML =
19528:             '<div class="batch-manual-confirm-summary">' +
19529:             '未关联定时配置，本次参数不会保存。<br>' +
19530:             '每轮: ' + escapeHtml(String(document.getElementById("batchManualRoundSize")?.value || "50")) + ' 封<br>' +
19531:             '发送前验证邮箱: ' + emailVerificationText + '<br>' +
19532:             '</div>' +
19533:             '<p class="batch-manual-confirm-warning">此为独立执行，不关联任何定时配置。</p>';
19534:     }
19535:     dialog.hidden = false;
12343:  * S1-4：无结构化 renderer 时的原始 JSON 兜底视图。
12344:  * 逐字结构：请求参数/执行结果两个 `.pre` 块；`rawTruncated` 时在对应 `.pre` 之后
12345:  * 追加截断标注（I1-6）。
12346:  */
12347: function renderTaskDetailRawBlocks(detail) {
12348:     let html = "";
12349:     if (detail.status === "INTERRUPTED" && detail.errorMessage) {
12350:         html += '<div class="text-muted">中断原因</div>';
12351:         html += `<div class="pre">${escapeHtml(detail.errorMessage)}</div>`;
12352:         if (detail.handledBy) {
12353:             html += `<div class="text-muted">处理人：${escapeHtml(detail.handledBy)}${detail.handledAt ? ` · 处理时间：${escapeHtml(detail.handledAt)}` : ""}</div>`;
12354:         }
12355:     }
12356:     if (detail.rawRequestPayload != null) {
12357:         html += '<div class="text-muted">请求参数</div>';
12358:         html += `<div class="pre">${escapeHtml(detail.rawRequestPayload)}</div>`;
12359:         if (detail.rawTruncated) {
12360:             html += '<div class="text-muted">内容过长已截断，完整内容见服务端日志</div>';
12361:         }
12362:     }
12363:     if (detail.rawResultSummary != null) {
12364:         html += '<div class="text-muted">执行结果</div>';
12365:         html += `<div class="pre">${escapeHtml(detail.rawResultSummary)}</div>`;
12366:         if (detail.rawTruncated) {
12367:             html += '<div class="text-muted">内容过长已截断，完整内容见服务端日志</div>';
12368:         }
12369:     }
12370:     return html;
```

## E-29 审计错误常量和收尾

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
```text
60: enum class ExecutionMode { AUTO, MANUAL }
61: 
62: /** I-6：验证明细审计不可用（写入失败）时的停止原因码 —— 停止本次执行，绝不静默降级继续发信。 */
63: private const val STOP_EMAIL_VERIFY_AUDIT_FAILED = "EMAIL_VERIFY_AUDIT_FAILED"
64: 
65: /** I-6：PASS 行无法条件预占 SENDING（重复目标/状态冲突）时的停止原因码。 */
1161:      * I-6：验证明细的发送结果收尾。审计不可用时抛 [EmailVerificationAuditException]，
1162:      * 由发送循环的专用边界处理（停止本次执行，不进入 SMTP 故障统计）。
1163:      */
1164:     private fun recordVerificationSend(verified: VerificationResult.Passed?, sendStatus: String, sendReason: String?) {
1165:         val passed = verified ?: return
1166:         batchEmailVerificationService.recordSend(passed.rowId, sendStatus, sendReason)
1167:     }
1168: 
1169:     /** I-6：调用方已经在停止时的收尾（取消/无可用账号/系统错误）；写失败只记日志，行保持默认 NOT_SENT。 */
1170:     private fun recordVerificationSendQuietly(rowId: Long, sendStatus: String, sendReason: String?) {
1171:         try {
1172:             batchEmailVerificationService.recordSend(rowId, sendStatus, sendReason)
1173:         } catch (e: EmailVerificationAuditException) {
1174:             log.warn("Email verification trailing audit write failed for row {}: {}", rowId, e.message)
1175:         }
1176:     }
1177: 
1178:     private fun buildSmtpErrorSummary(delivered: DeliveredMail): String =
1179:         buildString {
1180:             append(delivered.errorCategory.name)
1181:             delivered.smtpResponseCode?.let { append(":$it") }
1182:             delivered.errorDetail?.let { append(":${it.take(200)}") }
```

## E-30 迁移测试版本硬编码及快照已有测试

```sh
rg -n "assertEquals\("141"|@EnabledIfSystemProperty|withDatabaseName|mysql:8|fun flyway|targetSchemaVersion" src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt; rg -n "toExecutionSnapshot|emailVerificationEnabled" src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt; cat src/main/resources/db/migration/V4__create_task_execution.sql src/main/resources/db/migration/V137__task_execution_interruption_recovery.sql
```
退出码：0
```text
21:@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
27:        private val mysql = KotlinMySqlContainer("mysql:8.0.36")
28:            .withDatabaseName("talent_introduction")
59:        assertEquals("141", flyway().migrate().targetSchemaVersion)
81:        assertEquals("141", flyway.migrate().targetSchemaVersion)
87:        assertEquals("140", flyway(MigrationVersion.fromVersion("140")).migrate().targetSchemaVersion)
91:        assertEquals("141", flyway().migrate().targetSchemaVersion)
111:        assertEquals("135", v135.migrate().targetSchemaVersion)
124:        assertEquals("141", flyway().migrate().targetSchemaVersion)
212:        assertEquals("141", flyway().migrate().targetSchemaVersion)
254:        assertEquals("141", flyway().migrate().targetSchemaVersion)
331:        assertEquals("36", flyway(MigrationVersion.fromVersion("36")).migrate().targetSchemaVersion)
351:        assertEquals("141", flyway().migrate().targetSchemaVersion)
511:        assertEquals("130", flyway(MigrationVersion.fromVersion("130")).migrate().targetSchemaVersion)
519:        assertEquals("131", flyway(MigrationVersion.fromVersion("131")).migrate().targetSchemaVersion)
524:        assertEquals("141", flyway().migrate().targetSchemaVersion)
639:        assertEquals("133", v133.migrate().targetSchemaVersion)
646:        assertEquals("141", flyway().migrate().targetSchemaVersion)
680:        assertEquals("137", v137.migrate().targetSchemaVersion)
685:        assertEquals("141", flyway().migrate().targetSchemaVersion)
728:        assertEquals("138", v138.migrate().targetSchemaVersion)
747:        assertEquals("141", flyway().migrate().targetSchemaVersion)
847:        assertEquals("141", flyway().migrate().targetSchemaVersion)
882:        assertEquals("141", flyway().migrate().targetSchemaVersion)
941:        assertEquals("116", v116Flyway.migrate().targetSchemaVersion)
993:        assertEquals("141", flyway().migrate().targetSchemaVersion)
1096:        assertEquals("23", v23Flyway.migrate().targetSchemaVersion)
1100:        assertEquals("141", flyway().migrate().targetSchemaVersion)
1112:        assertEquals("24", v24Flyway.migrate().targetSchemaVersion)
1116:        assertEquals("141", flyway().migrate().targetSchemaVersion)
1151:        assertEquals("141", flyway().migrate().targetSchemaVersion)
1197:        assertEquals("141", flyway().migrate().targetSchemaVersion)
1273:        assertEquals("141", flyway().migrate().targetSchemaVersion)
1389:        assertEquals("141", flyway().migrate().targetSchemaVersion)
1534:        assertEquals("141", flyway().migrate().targetSchemaVersion)
1624:        assertEquals("141", flyway().migrate().targetSchemaVersion)
1801:        assertEquals("141", flyway().migrate().targetSchemaVersion)
1988:    private fun flyway(target: MigrationVersion? = null): Flyway {
7:import com.weibo.talentintroduction.campaign.domain.toExecutionSnapshot
105:        emailVerificationEnabled: Boolean = false
125:        emailVerificationEnabled = emailVerificationEnabled
147:        emailVerificationEnabled: Boolean? = null
167:        emailVerificationEnabled = emailVerificationEnabled
186:        emailVerificationEnabled: Boolean = false,
209:        emailVerificationEnabled = emailVerificationEnabled,
1523:        // 启动快照（toExecutionSnapshot）同样必须拒绝。
1524:        assertThrows(IllegalStateException::class.java) { corrupt.toExecutionSnapshot(objectMapper) }
1541:            captor.value.toExecutionSnapshot(objectMapper).senderAccountCodes
1613:            row(id = 1L).toExecutionSnapshot(objectMapper).senderAccountCodes
1620:    fun `create persists emailVerificationEnabled true into entity view and launch snapshot (I-1)`() {
1627:        val view = service().create(createCmd(name = "开启验证", emailVerificationEnabled = true))
1630:        assertTrue(captor.value.emailVerificationEnabled)
1631:        assertTrue(view.emailVerificationEnabled)
1633:        assertTrue(captor.value.toExecutionSnapshot(objectMapper).emailVerificationEnabled)
1637:    fun `create without emailVerificationEnabled defaults to false in entity view and snapshot (I-1)`() {
1647:        assertFalse(captor.value.emailVerificationEnabled)
1648:        assertFalse(view.emailVerificationEnabled)
1649:        assertFalse(captor.value.toExecutionSnapshot(objectMapper).emailVerificationEnabled)
1654:        val existing = row(id = 5L, name = "每日介绍", emailVerificationEnabled = true)
1666:        assertTrue(captor.value.emailVerificationEnabled)
1667:        assertTrue(view.emailVerificationEnabled)
1672:        val existing = row(id = 5L, name = "每日介绍", emailVerificationEnabled = true)
1680:        val view = service().update(5L, updateCmd(emailVerificationEnabled = false))
1683:        assertFalse(captor.value.emailVerificationEnabled)
1684:        assertFalse(view.emailVerificationEnabled)
1693:            emailVerificationEnabled = true
1708:        assertTrue(disabledRow.emailVerificationEnabled)
1710:        assertTrue(disabled.emailVerificationEnabled)
1711:        assertTrue(enabledRow.emailVerificationEnabled)
1713:        assertTrue(enabled.emailVerificationEnabled)
1723:            emailVerificationEnabled = true
1736:        val existing = row(id = 5L, name = "每日介绍", emailVerificationEnabled = true)
1744:        assertTrue(captor.value.emailVerificationEnabled)
1750:    fun `updateLegacyConfig preserves existing emailVerificationEnabled entity value (I-1 I-2)`() {
1760:            emailVerificationEnabled = true,
1790:        assertTrue(captor.value.emailVerificationEnabled)
1794:    fun `create rejects emailVerificationEnabled true with a MATERIAL_REMINDER template without saving (I-3)`() {
1799:            service().create(createCmd(name = "材料验证", templateId = 42L, emailVerificationEnabled = true))
1808:    fun `update rejects emailVerificationEnabled true on an existing MATERIAL_REMINDER config without saving (I-3)`() {
1821:                    emailVerificationEnabled = true
1834:        assertFalse(service().get(1L).emailVerificationEnabled)
1835:        assertFalse(row(id = 1L).toExecutionSnapshot(objectMapper).emailVerificationEnabled)
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
ALTER TABLE task_execution
    ADD COLUMN owner_token VARCHAR(64) NULL,
    ADD COLUMN heartbeat_at DATETIME NULL,
    ADD COLUMN interruption_reason_code VARCHAR(64) NULL,
    ADD COLUMN interruption_reason_detail VARCHAR(1000) NULL,
    ADD COLUMN handled_by VARCHAR(128) NULL,
    ADD COLUMN handled_at DATETIME NULL;

CREATE INDEX idx_te_active_heartbeat ON task_execution (status, heartbeat_at);
```
