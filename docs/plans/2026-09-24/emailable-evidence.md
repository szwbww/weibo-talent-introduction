# Emailable 发送前验证：代码审计证据

审计日：2026-09-24。仓库基线 `7c7a9e747e471750ff776e37f7a6cb00f25e4d5c`。以下为当前工作区读取结果，不等同于已部署版本证明。已有 index.html/styles.css/releases.json 未提交改动属于原工作区，本计划不覆盖它们。

## E-1 接入缺口

命令：`rg -n -i 'emailable|email.?verification' src/main src/test pom.xml`

退出码：1

```text
(无匹配)
```

## E-2 配置表全路径候选

命令：`rg -n 'BatchSendTaskConfigRepository|batch_send_task_config' src/main scripts`

退出码：0

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:363: * I-1: `batch_send_task_config.sender_account_codes_json` 的唯一解析点。
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt:7:@Table("batch_send_task_config")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:13:import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:32:    private val repository: BatchSendTaskConfigRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:604:        return "uk_batch_send_task_config_active_name" in messages ||
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:8:import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:34:    private val batchSendTaskConfigRepository: BatchSendTaskConfigRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:7:interface BatchSendTaskConfigRepository : CrudRepository<BatchSendTaskConfig, Long> {
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:13:        SELECT * FROM batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:22:        SELECT * FROM batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:32:        SELECT * FROM batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:44:        SELECT * FROM batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskExecution.kt:23:    /** Source batch_send_task_config id at launch; null for independent manual runs. Soft-delete safe. */
src/main/resources/db/migration/V129__add_material_request_codes.sql:5:-- V128__add_research_direction_filter_to_batch_send_task_config.sql 撞号；后者已于
src/main/resources/db/migration/V93__add_regions_to_batch_send_task_config.sql:1:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V93__add_regions_to_batch_send_task_config.sql:3:UPDATE batch_send_task_config SET regions_json = '[]' WHERE regions_json = '';
src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt:4:import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt:22: * Per-config dynamic cron scheduler (I-2). Each enabled [batch_send_task_config] row gets its own
src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt:28:    private val batchSendTaskConfigRepository: BatchSendTaskConfigRepository,
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:125:     * deleted; a template still referenced by `batch_send_task_config.template_id`
src/main/resources/db/migration/V99__add_gate_filter_enabled_to_batch_send_task_config.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql:6:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql:9:UPDATE batch_send_task_config
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql:13:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V73__add_batch_config_id_to_task_execution.sql:9:        FOREIGN KEY (batch_config_id) REFERENCES batch_send_task_config(id);
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
src/main/resources/db/migration/V92__drop_daily_cap_from_batch_send_task_config.sql:1:ALTER TABLE batch_send_task_config DROP COLUMN daily_cap;
src/main/resources/db/migration/V128__add_research_direction_filter_to_batch_send_task_config.sql:5:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:6:UPDATE batch_send_task_config
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:12:ALTER TABLE batch_send_task_config DROP COLUMN operator_status;
src/main/resources/db/migration/V91__add_rounds_per_run_to_batch_send_task_config.sql:4:ALTER TABLE batch_send_task_config ADD COLUMN rounds_per_run INT NOT NULL DEFAULT 1 AFTER round_size;
src/main/resources/db/migration/V91__add_rounds_per_run_to_batch_send_task_config.sql:6:UPDATE batch_send_task_config SET rounds_per_run = GREATEST(1, CEIL(daily_cap / round_size));
src/main/resources/db/migration/V74__repair_batch_send_task_config_encoding.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V74__repair_batch_send_task_config_encoding.sql:9:UPDATE batch_send_task_config
src/main/resources/db/migration/V74__repair_batch_send_task_config_encoding.sql:14:UPDATE batch_send_task_config
src/main/resources/db/migration/V110__require_expert_types_on_batch_send_task_config.sql:4:UPDATE batch_send_task_config
src/main/resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql:5:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V103__add_reachability_filter_to_batch_send_task_config.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:2:-- TEXT 列不能带 DEFAULT（MySQL 限制），故照 V93__add_regions_to_batch_send_task_config.sql
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:5:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:8:UPDATE batch_send_task_config
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:14:ALTER TABLE batch_send_task_config DROP COLUMN email_domain;
```

## E-3 执行表仓储与直接 SQL 候选

命令：`rg -n 'TaskExecutionRepository|task_execution' src/main/kotlin scripts`

退出码：0

```text
src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskExecution.kt:7:@Table("task_execution")
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskProgressLogRepository.kt:16:    @Query("UPDATE task_progress_log SET task_execution_id = :executionId WHERE task_execution_id = :pendingToken")
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskProgressLogRepository.kt:21:     * **禁止**按 task_execution_id 关联（tryStartWithToken 落的孤儿行 execution_id 为负值，
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:4:import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:13: * - I3-1：`task_progress_log` 按 `created_at` 删，无 JOIN / EXISTS / task_execution_id 关联
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:17: * - I3-3：`task_execution` 按 `started_at` 删（有 idx_te_started）；两表共用同一个
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:19: * - I3-4：先子表 `task_progress_log` 后主表 `task_execution`。
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:28:    private val executionRepository: TaskExecutionRepository,
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:52:            log.warn("purge task_execution failed: {}", e.message)
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:45:interface TaskExecutionRepository : CrudRepository<TaskExecution, Long> {
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:57:    @Query("SELECT * FROM task_execution WHERE task_type = :taskType ORDER BY started_at DESC LIMIT :limit")
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:62:        SELECT COUNT(*) FROM task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:72:        SELECT * FROM task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:87:        FROM task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:96:        SELECT COALESCE(SUM(success_count), 0) FROM task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:111:        UPDATE task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:122:        UPDATE task_execution SET heartbeat_at = :now, updated_at = :now
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:129:        UPDATE task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:141:        UPDATE task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:155:        UPDATE task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:177:    @Query("DELETE FROM task_execution WHERE started_at < :cutoff ORDER BY started_at LIMIT :batchSize")
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:187:        FROM task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:199:        FROM task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:212:        FROM task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:225:        FROM task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:248:        FROM task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:256:    @Query("SELECT COUNT(*) FROM task_execution WHERE status IN ('RUNNING', 'CANCELLING')")
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:259:    @Query("SELECT COUNT(*) FROM task_execution")
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:270:        FROM task_execution
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:276:    @Query("SELECT COUNT(*) FROM task_execution WHERE task_type = :taskType")
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:279:    @Query("SELECT COUNT(*) FROM task_execution WHERE status = :status")
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:282:    @Query("SELECT COUNT(*) FROM task_execution WHERE task_type = :taskType AND status = :status")
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt:8:import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt:17:    private val repository: TaskExecutionRepository,
src/main/kotlin/com/weibo/talentintroduction/task/service/MailAutomationScheduler.kt:55:        // task_execution_id 保持 null（该执行经队列派发，邮件未直接关联）。
src/main/kotlin/com/weibo/talentintroduction/task/service/MailAutomationScheduler.kt:90:        // 走 runAndRecordWithResult 使对账报告（ReconcileReport）落入 task_execution.result_summary，可在任务面板查看
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:13:import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:37:    private val taskExecutionRepository: TaskExecutionRepository,
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskActivityController.kt:5:import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskActivityController.kt:33:    private val taskExecutionRepository: TaskExecutionRepository,
src/main/kotlin/com/weibo/talentintroduction/discovery/domain/ExpertAcademicEnrichmentJob.kt:34:    /** 触发本次入队的 task_execution id；未知为 NULL。 */
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt:9:import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt:33:    private val taskExecutionRepository: TaskExecutionRepository,
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt:73:     * I-3：`pause()` 幂等且无需存在 RUNNING 的 task_execution；随后只为在途窗口发一次进程内取消请求。
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:2333:     * - [taskType] 只决定进度日志归属；`task_execution` 记录由调用方按自己的 triggerType 写入。
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:43:/** I-7（c2）：流水线窗口的 `task_execution.task_type`（与既有深度发现任务历史同一类型）。 */
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:357:     * I-7：幂等持久化 `PAUSED`（无需存在 RUNNING 的 task_execution），返回 `phase` 与在途工作数。
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:472:        // I-4/I-7：先区分「现在就能跑」与「只是被延期」；两者都不该开窗口，更不该建空 task_execution。
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:1179:     * 建一条空 `task_execution`：
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:434:     * 不要求存在 RUNNING 的 task_execution；`phase` 保留在途收尾态（对外 state 优先 PAUSED）。
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:859:     * 不 join task_execution（P3 保留清理删除执行行后悬垂 id 仍正常返回邮件）。
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:183:     * I2b-3（B4）：按 task_execution_id 过滤的收发件箱查询。复用既有行装配
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:185:     * 只在数据来源上多一个 WHERE（仓库派生查询）。不 join task_execution（I2b-4：
```

## E-4 专家标签字段候选

命令：`rg -n '"tags"|\.tags\b|addTag\(|removeTag\(' src/main/kotlin scripts --glob '*.kt' --glob '*.py' --glob '*.sh'`

退出码：0

```text
scripts/test_build_sbir_expert_import.py:287:        self.assertIn("SBIR导入", raw_document["tags"])
scripts/test_build_sbir_expert_import.py:289:        self.assertEqual(raw_document["tags"], candidate_document["tags"])
scripts/test_build_sbir_research_fields_update.py:69:            self.assertNotIn("tags", lines[1]["doc"])
scripts/import_contactout_visible_candidates.py:170:            "tags": [SOURCE_TAG],
scripts/import_contactout_visible_candidates.py:206:        "docs": [{"_id": doc["_id"], "_source": ["email", "researchFields", "funnelLevel", "tags", "operatorStatus", "expertClassification.type"]} for doc in docs]
scripts/import_contactout_visible_candidates.py:214:                or source.get("tags") != [SOURCE_TAG]
scripts/build_sbir_expert_import.py:420:        "tags": [
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:494:            tags = source.path("tags").takeIf { it.isArray }
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:591:            "tags",
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:986:                "tags" to mapOf(
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:988:                        "field" to "tags",
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:1003:            .path("tags")
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:1082:            filters.add(mapOf("term" to mapOf("tags" to tag)))
scripts/build_contactout_enterprise_es_import.py:243:            "tags": tags,
scripts/build_contactout_enterprise_es_import.py:269:        "_source": ["email", "orcidId", "tags"],
scripts/build_contactout_enterprise_es_import.py:282:        "docs": [{"_id": doc["_id"], "_source": ["email", "employment", "tags", "operatorStatus"]} for doc in docs]
scripts/build_contactout_enterprise_es_import.py:285:    requested = {doc["_id"]: doc["_source"]["tags"] for doc in docs}
scripts/build_contactout_enterprise_es_import.py:290:                or not clean(source.get("employment")) or source.get("tags") != expected_tags
scripts/build_contactout_enterprise_es_import.py:303:                  if item["_source"].get("tags") == [SOURCE_TAG, DEBANG_TAG]}
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:633:    fun addTag(docId: String, tag: String, level: ExpertIndexLevel): Boolean {
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:637:                "source" to "if (ctx._source.tags == null) ctx._source.tags = []; if (!ctx._source.tags.contains(params.tag)) ctx._source.tags.add(params.tag)",
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:651:    fun removeTag(docId: String, tag: String, level: ExpertIndexLevel): Boolean {
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:655:                "source" to "if (ctx._source.tags != null) ctx._source.tags.removeIf(t -> t == params.tag)",
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:81:                        val tagged = expertIndexWriterService.addTag(docId, "verified", ExpertIndexLevel.CANDIDATE)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:248:        val existingTags = (rawDoc["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:255:            put("tags", newTags)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:272:    fun addTag(@RequestBody request: ExpertTagMutationRequest): TagMutationResult {
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:278:        val ok = expertIndexWriterService.addTag(docId, request.tag.trim(), request.level)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:286:    fun removeTag(@RequestBody request: ExpertTagMutationRequest): TagMutationResult {
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:292:        val ok = expertIndexWriterService.removeTag(docId, request.tag.trim(), request.level)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:334:            tags = profile?.tags.orEmpty()
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:442:                tags = expert.tags.orEmpty(),
scripts/expert_discovery/enterprise_batch/import_personal_email_candidates.py:141:            "tags": ["企业研发专家", PERSONAL_TAG],
scripts/expert_discovery/enterprise_batch/import_personal_email_candidates.py:173:        "docs": [{"_id": item["_id"], "_source": ["email", "researchFields", "tags", "operatorStatus", "expertClassification.type", "dataSource"]} for item in docs]
scripts/expert_discovery/enterprise_batch/import_personal_email_candidates.py:182:        if source.get("operatorStatus") not in (None, "") or source.get("expertClassification", {}).get("type") != "PRODUCTION_RND" or PERSONAL_TAG not in (source.get("tags") or []):
scripts/expert_discovery/enterprise_batch/import_es_documents.py:131:                    {"terms": {"tags": ["Apollo已验证个人邮箱"]}},
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:347:                        profile.tags
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1855:            "tags" to listOf("discovered")
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1864:            val existingTags = (get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1865:            put("tags", (existingTags + "discovered").distinct())
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:2847:            val existingTags = (get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:2848:            put("tags", (existingTags + "auto_promoted").distinct())
scripts/expert_discovery/enterprise_batch/import_candidate_es.py:156:            "tags": ["企业研发专家", "Apollo已验证工作邮箱", f"高价值-{row.get('high_value_tier', 'B')}"],
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:117:            val expertTags = profile.tags.orEmpty()
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:168:                tags = snapshot.tags,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1225:            mapOf("term" to mapOf("tags" to "承诺回复材料")),
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1247:        val scopeDescription = scope.funnelLevels.joinToString("+") + " + tags=${scope.tags}" +
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1386:        if (scope.tags.isNotEmpty()) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1387:            filters.add(mapOf("terms" to mapOf("tags" to scope.tags)))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:350:        val tags = normalizeTags(fields.tags)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:393:            .associate { it.orcidId to (it.tags.orEmpty()) }
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:395:            .associate { it.orcidId to (it.tags.orEmpty()) }
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:157:    fun addTag(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt:50: * 且确实没有标签。与消息 timeline.tags（邮件标签）完全分离。
```

## E-5 构造器直接调用

命令：`rg -n 'ManualInitialOutreachService\(|BatchSendConfigController\(' src/main src/test`

退出码：0

```text
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:102:    private val service = ManualInitialOutreachService(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:683:        val service = ManualInitialOutreachService(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:61:class ManualInitialOutreachService(
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt:41:    private fun controller() = BatchSendConfigController(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:40:class BatchSendConfigController(
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt:33:    private fun controller() = BatchSendConfigController(
```

## E-6 迁移版本

命令：`rg --files src/main/resources/db/migration`（以下保留数值排序后的末 8 个版本）

退出码：0

```text
src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql
src/main/resources/db/migration/V131__create_expert_academic_enrichment_job.sql
src/main/resources/db/migration/V132__create_openalex_budget.sql
src/main/resources/db/migration/V133__create_discovery_paper_queue.sql
src/main/resources/db/migration/V134__shared_inbox_owner.sql
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql
src/main/resources/db/migration/V136__add_compose_subject_snippet_id.sql
src/main/resources/db/migration/V137__task_execution_interruption_recovery.sql
```

## E-7 现有 JS 字段传播接缝

命令：`rg -n 'gateFilterEnabled|function .*Batch.*Log|function .*Manual.*(Snapshot|Form|Diff|Source)|function deepCloneConfig' src/main/resources/static/app.js`

退出码：0

```text
17549:    if (c.gateFilterEnabled) return '<span class="batch-gate-pill">门禁过滤 · 开</span>';
17724:    if (gateCheckbox) gateCheckbox.checked = Boolean(config && config.gateFilterEnabled);
18513:        gateFilterEnabled: gateToggleChecked("editor"),
18518:function buildManualExecutionSnapshot() {
18536:        gateFilterEnabled: values.gateFilterEnabled,
18572:            body: JSON.stringify(Object.assign({}, snapshot, { gateFilterEnabled: gateOn }))
18657:        gateFilterEnabled: gateToggleChecked("editor"),
18704:function applyBatchManualSource(config) {
18736:function deepCloneConfig(c) {
18750:        gateFilterEnabled: c.gateFilterEnabled === true,
18761:function fillManualFormDefaults() {
18774:        gateFilterEnabled: false,
18786:function fillManualFormFromDraft() {
18808:    if (gateCheckbox) gateCheckbox.checked = Boolean(d.gateFilterEnabled);
18826:function updateManualSourceInfo() {
18841:function clearManualSource() {
18846:function detachBatchManualSourcePreservingDraft() {
18860:function readManualFormValues() {
18887:        gateFilterEnabled: Boolean(gateCheckboxEl && gateCheckboxEl.checked),
18896:function normalizeManualSnapshot(v) {
18908:        gateFilterEnabled: Boolean(v.gateFilterEnabled),
18917:function formatManualDiffValue(key, value) {
18918:    if (key === "gateFilterEnabled") return value ? "开启" : "关闭";
18953:function computeManualDiffs() {
18970:        { key: "gateFilterEnabled", label: "邮件模版门禁过滤" },
19020:        gateFilterEnabled: "manualFieldGateFilter",
19162:function handleManualSourceSearch() {
19175:async function loadBatchManualSourceOptions(query) {
19195:function renderBatchManualSourceDropdown(configs) {
19215:function renderBatchManualSourceEmpty(message) {
19224:function closeBatchManualSourceDropdown() {
19233:function selectBatchManualSource(id) {
19253:function openBatchRecentLogs(executionId) {
19297:function openBatchConfigLogs(configId, executionId) {
19311:function openBatchExecutionLogs(executionId) {
19319:function closeBatchLogDrawer() {
19330:function clearBatchLogRefreshTimer() {
19357:async function loadBatchLogExecutions(configId, executionId) {
19387:async function loadBatchLogDetail(configId, executionId) {
19549:function clearBatchLogDisplay() {
```

## E-8 ES 映射

命令：`rg -n '"dynamic"|"tags"' src/main/resources/es/orcid_info_raw.json src/main/resources/es/orcid_info_candidate.json src/main/resources/es/orcid_info_application.json`

退出码：0

```text
src/main/resources/es/orcid_info_application.json:7:    "dynamic": false,
src/main/resources/es/orcid_info_application.json:48:      "tags": { "type": "keyword" },
src/main/resources/es/orcid_info_raw.json:7:    "dynamic": false,
src/main/resources/es/orcid_info_raw.json:36:      "tags": { "type": "keyword" },
src/main/resources/es/orcid_info_candidate.json:7:    "dynamic": false,
src/main/resources/es/orcid_info_candidate.json:38:      "tags": { "type": "keyword" },
```

## E-9 模板断言及测试入口

命令：`rg -n 'assertEquals\("136"|node --test|migrationIt|mysqlIt' src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt pom.xml`

退出码：0

```text
pom.xml:23:        <migrationIt>false</migrationIt>
pom.xml:24:        <mysqlIt>false</mysqlIt>
pom.xml:176:                        <migrationIt>${migrationIt}</migrationIt>
pom.xml:177:                        <mysqlIt>${mysqlIt}</mysqlIt>
pom.xml:199:                                <argument>node --test src/test/js/*.test.js</argument>
pom.xml:243:                <migrationIt>true</migrationIt>
pom.xml:249:                <mysqlIt>true</mysqlIt>
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:21:@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:62:        assertEquals("136", flyway.migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:81:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:169:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:211:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:308:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:481:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:603:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:647:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:682:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:793:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:900:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:916:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:951:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:997:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:1073:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:1189:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:1334:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:1424:        assertEquals("136", flyway().migrate().targetSchemaVersion)
src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt:1601:        assertEquals("136", flyway().migrate().targetSchemaVersion)
```

## E-10 快照持久化/读取与无恢复路由

命令：`rg -n 'requestPayload|requestSnapshot|runAndRecordWithResult|@.*Mapping|fun .*resume' src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt`

退出码：0

```text
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt:138:    fun <T : Any?> runAndRecordWithResult(
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt:152:                requestPayload = toJson(request),
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt:231:                requestPayload = toJson(request),
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:34:@RequestMapping("/api/task-executions")
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:45:    @GetMapping
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:65:    @GetMapping("/task-types")
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:77:    @GetMapping("/interruption-reasons")
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:80:    @PostMapping("/{id}/interrupt")
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:100:    @GetMapping("/{id}/detail")
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:105:        val (requestRaw, requestTruncated) = truncateRaw(exec.requestPayload)
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:140:     * - MAIL_BY_EXECUTION && count == 0 && requestPayload 含队列标记 → QUEUE_DISPATCHED
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:156:                    isQueueDispatched(exec.requestPayload) -> "QUEUE_DISPATCHED"
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:205:    @GetMapping("/recent-polls")
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:246:    @GetMapping("/recent-polls/{id}/detail")
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:285:    @GetMapping("/{id}")
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:355:    val requestPayload: String?,
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:373: * 列表响应投影（M-1）：刻意不含 requestPayload / resultSummary —— 两个 TEXT 列
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:505:        requestPayload = requestPayload,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:78:            requestPayload = request
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:94:            requestPayload = request,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:132:                requestPayload = request,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:162:                requestPayload = request,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:224:    fun resumeSchedule(sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:266:                requestPayload = request,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:315:        requestPayload: Any,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:354:                    val (_, result) = taskExecutionService.runAndRecordWithResult<ManualOutreachResult>(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:355:                        TASK_TYPE, triggerType, requestPayload,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:573:            requestPayload = mapOf("legacySendType" to sendType.name, "snapshot" to snapshot),
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:39:@RequestMapping("/api/mail/batch-send")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:52:    @GetMapping("/configs")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:56:    @PostMapping("/configs")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:60:    @GetMapping("/configs/{id}")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:64:    @PutMapping("/configs/{id}")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:71:    @PatchMapping("/configs/{id}/enabled")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:78:    @DeleteMapping("/configs/{id}")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:88:    @PostMapping("/cron/preview")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:97:    @PostMapping("/recipients/preview")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:101:    @PostMapping("/configs/{id}/execute")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:105:    @PostMapping("/manual-executions")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:109:    @GetMapping("/configs/{id}/executions")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:119:    @GetMapping("/configs/{id}/executions/{executionId}")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:135:    @GetMapping("/executions")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:146:    @GetMapping("/executions/{executionId}")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:159:    @PostMapping("/executions/{executionId}/cancel")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:180:    @GetMapping("/config")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:184:    @PutMapping("/config")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:194:    @GetMapping("/types/{sendType}/config")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:198:    @PutMapping("/types/{sendType}/config")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:209:    @GetMapping("/types/{sendType}/pending-count")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:213:    @PostMapping("/types/{sendType}/start")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:217:    @PostMapping("/types/{sendType}/pause")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:221:    @PostMapping("/types/{sendType}/manual")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:225:    @PostMapping("/types/{sendType}/start-auto")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:229:    @PostMapping("/types/{sendType}/resume-schedule")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:230:    fun resumeSchedule(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:233:    @PostMapping("/types/{sendType}/pause-schedule")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:237:    @GetMapping("/types/{sendType}/status")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:275:        val requestSnapshot = execution.requestPayload?.let {
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:292:            requestSnapshot = requestSnapshot,
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:461:    val requestSnapshot: com.fasterxml.jackson.databind.JsonNode?,
```

## F-1 快照字段

`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:10` 至 `43`，逐字摘录：

```kt
data class BatchExecutionSnapshot(
    val mailType: String,
    val roundSize: Int,
    val roundsPerRun: Int = 1,
    val perMailIntervalMs: Long,
    val perRoundIntervalMs: Long,
    val selfCheckTtlMinutes: Int,
    val funnelLevel: String? = null,
    val tags: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    val emailDomains: List<String> = emptyList(),
    val discipline: String? = null,
    val operatorStatuses: List<String> = emptyList(),
    val expertTypes: List<String> = emptyList(),
    /**
     * I-1/I-2: 本次执行唯一的发件账号范围快照（逻辑 `mail_sender_account.account_code`）。
     * `[]` = 不限（旧任务/未传字段）；非空 = 严格白名单，两发送循环与选号服务都只在此集合内运作。
     */
    val senderAccountCodes: List<String> = emptyList(),
    val templateId: Long? = null,
    val gateFilterEnabled: Boolean = false,
    /**
     * I-1/I-2: 研究方向三态（[ResearchDirectionFilters]）；默认 [ResearchDirectionFilters.ANY]。
     * 前端手动快照、`toExecutionSnapshot` 与 `RecipientScope.fromSnapshot` 逐字传递。
     */
    val researchDirectionFilter: String = ResearchDirectionFilters.ANY,
    val oneRoundOnly: Boolean = false
)

data class ManualBatchExecutionRequest(
    val sourceConfigId: Long? = null,
    val sourceUpdatedAt: LocalDateTime? = null,
    val snapshot: BatchExecutionSnapshot
)
```

## F-2 插入点

`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:621` 至 `675`，逐字摘录：

```kt

                val email = expert.email
                if (email.isNullOrBlank() || emailSuppressionService.isSuppressed(email)) {
                    accumulator.recordSkipped(BatchOutcomeReasonCodes.SUPPRESSED, "已跳过抑制邮箱：${email ?: ""}")
                    processedTotal++
                    roundSent++
                    roundProcessed++
                    roundRejected++
                    updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                        "RUNNING", "已跳过抑制邮箱：${email ?: ""}", errors, mode, roundNumber, config, runAccountStats,
                        roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                    continue
                }

                // I-3: 任一 expert_contact 行已有绑定（与绑定值是否在选中集合无关）→ 本次批量不发信、
                // 不重选号、不改绑。NEW 重试在目标构造时已过滤；ES 页在此发送前对同一 ORCID 重查。
                if (existingContact?.boundSenderAccountCode != null || hasBoundContact(normOrcid)) {
                    accumulator.recordSkipped(
                        BatchOutcomeReasonCodes.BOUND_SENDER_ALREADY_SET,
                        "专家已绑定发件账号：${expert.orcidId}"
                    )
                    processedTotal++
                    roundProcessed++
                    roundRejected++
                    updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                        "RUNNING", "已跳过已绑定发件账号：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
                        roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                    continue
                }

                val account = try {
                    selectSendAccount(expert, assignments, ignoreWarmup, stock, allowedAccountCodes)
                } catch (e: NoAvailableSenderAccountException) {
                    log.warn("No available sender account mid-round after {} processed, pausing flow", processedTotal)
                    stopReason = "NO_AVAILABLE_ACCOUNT"
                    finalStatus = "PAUSED"
                    midRoundStop = true
                    break
                } catch (e: Exception) {
                    log.error("System error selecting account", e)
                    stopReason = "SYSTEM_ERROR"
                    finalStatus = "FAILED"
                    errors.add("系统错误: ${e.message ?: "Unknown error"}")
                    midRoundStop = true
                    break
                }

                val stat = runAccountStats.getOrPut(account.accountCode) { AccountRunStat() }
                val provider = providerResolver.resolve(expert.email)

                try {
                    // 1. Create or reuse contact (occupy the slot) — I-7
                    val contact = existingContact ?: run {
                        val now = LocalDateTime.now()
                        val (boundCode, boundAt) = senderAccountBindingService
```

## F-3 SMTP 与写结果

`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:731` 至 `779`，逐字摘录：

```kt
                    // 4. Persist attempt as PREPARED (audit trail) — upsert to respect UNIQUE(orcid_id, mail_type) (I-7)
                    val now = LocalDateTime.now()
                    val existingAttempt = mailSendAttemptRepository.findByOrcidIdAndMailType(normOrcid, "INTRODUCTION")
                    val attempt = mailSendAttemptRepository.save(
                        if (existingAttempt != null) {
                            existingAttempt.copy(
                                accountCode = account.accountCode, messageId = messageId,
                                status = MailSendAttemptStatus.PREPARED, errorSummary = null,
                                updatedAt = now
                            )
                        } else {
                            MailSendAttempt(
                                orcidId = normOrcid, mailType = "INTRODUCTION",
                                accountCode = account.accountCode, messageId = messageId,
                                status = MailSendAttemptStatus.PREPARED,
                                createdAt = now, updatedAt = now
                            )
                        }
                    )

                    // 5. Send via SMTP
                    val delivered = mailDeliveryService.send(account, mail)
                    if (delivered.status == "SENT") {
                        accountRateLimiter.recordSuccess(account.accountCode, provider, config.perMailIntervalMs)
                        // 6. Record success atomically (state transition + mail_record + counter + attempt + ES) — I-7
                        txHelper.recordSuccess(
                            contact = contact, accountCode = account.accountCode,
                            deliveredMessageId = messageId, subject = mail.subject,
                            body = mail.text ?: mail.body, attemptId = attempt.id!!,
                            taskExecutionId = executionId
                        )
                        accumulator.recordSuccess()
                        stat.success++
                        roundPassed++
                        taskExecutionService.updateProgressCounts(executionId, accumulator.success, accumulator.failure)
                    } else {
                        val errorSummary = buildSmtpErrorSummary(delivered)
                        when (delivered.errorCategory) {
                            SmtpErrorCategory.PERMANENT -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.text ?: mail.body, attemptId = attempt.id,
                                    taskExecutionId = executionId
                                )
                                expertContactRepository.save(
                                    contact.copy(operatorStatus = "EMAIL_INVALID", updatedAt = LocalDateTime.now())
                                )
                                expertIndexWriterService.syncOperatorStatus(normOrcid, "EMAIL_INVALID")
```

## F-4 执行终态

`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1550` 至 `1574`，逐字摘录：

```kt
data class ManualOutreachResult(
    val total: Int,
    val sent: Int,
    val failed: Int,
    val skippedNoAccount: Int,
    val wasCancelled: Boolean,
    val finalStatus: String? = null,
    val stopReason: String? = null,
    val remaining: Int = 0,
    val skipped: Int = 0,
    val outcome: OutcomeBreakdown? = null
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = sent
    override val taskFailureCount: Int get() = failed
    override val taskFinalStatus: String? get() = when {
        wasCancelled -> "CANCELLED"
        failed > 0 && sent > 0 -> "PARTIAL_SUCCESS"
        failed > 0 -> "FAILED"
        else -> finalStatus?.takeIf { it in setOf("FAILED", "CANCELLED", "PARTIAL_SUCCESS") } ?: "SUCCESS"
    }
}

/** Per-account run stats tracked during a single run (not persisted). */
class AccountRunStat {
    var success: Int = 0
```

## F-5 标签追加

`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:633` 至 `649`，逐字摘录：

```kt
    fun addTag(docId: String, tag: String, level: ExpertIndexLevel): Boolean {
        val index = expertIndexService.indexName(level)
        val script = mapOf(
            "script" to mapOf(
                "source" to "if (ctx._source.tags == null) ctx._source.tags = []; if (!ctx._source.tags.contains(params.tag)) ctx._source.tags.add(params.tag)",
                "params" to mapOf("tag" to tag)
            )
        )
        val url = "${properties.baseUrl}/$index/_update/$docId"
        return try {
            restTemplate.exchange(url, HttpMethod.POST, HttpEntity(script, headers()), JsonNode::class.java)
            true
        } catch (e: Exception) {
            log.warn("Failed to add tag '{}' to {} in {}: {}", tag, docId, level, e.message)
            false
        }
    }
```

## F-6 真实 ID 定位

`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:846` 至 `881`，逐字摘录：

```kt
     * I-1/I-3：按真实 `_id` 批量读取（`_mget`），结果复用 [toExpertProfile]，因此与列表/详情读到同一批字段。
     * `orcidId` 只是文档里的一个字段：搜索式 `findByOrcidId` 不能保证命中真实文档，定位一律以 `_id` 为准。
     * 未命中的 id（`found=false`）不产出条目，也不补空对象；入参为空时不发请求。
     */
    fun findByDocumentIds(level: ExpertIndexLevel, ids: List<String>): List<ExpertProfile> {
        val documentIds = ids.filter { it.isNotBlank() }.distinct()
        if (documentIds.isEmpty()) return emptyList()

        val index = expertIndexService.indexName(level)
        val requestBody = mapOf(
            "docs" to documentIds.map { mapOf("_index" to index, "_id" to it, "_source" to sourceFields()) }
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/_mget",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return emptyList()

        return response.path("docs")
            .filter { it.path("found").asBoolean(false) }
            .map { toExpertProfile(it) }
    }

    fun countByFieldPresence(
        level: ExpertIndexLevel,
        fields: List<String>,
        mode: FieldPresenceMode
    ): Long {
        if (fields.isEmpty()) {
            return countExperts(level, emptyList())
        }
        return countExperts(level, buildFieldPresenceFilters(fields, mode))
    }

```

## F-7 旧接口适配

`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:187` 至 `230`，逐字摘录：

```kt
    fun updateLegacyConfig(sendType: BatchSendType, request: BatchSendConfigUpdateRequest): BatchSendConfig {
        val existing = requireActiveLegacy(sendType)
        val id = existing.id ?: error("Batch send task config id is required")
        val view = update(
            id,
            BatchSendTaskConfigUpdateCommand(
                configName = existing.configName,
                autoEnabled = request.autoEnabled,
                cron = request.cron,
                roundSize = request.roundSize,
                roundsPerRun = existing.roundsPerRun,
                perMailIntervalMs = request.perMailIntervalMs,
                perRoundIntervalMs = request.perRoundIntervalMs,
                selfCheckTtlMinutes = request.selfCheckTtlMinutes,
                funnelLevel = existing.funnelLevel,
                tags = parseTags(existing.tagsJson),
                regions = parseRegions(existing.regionsJson),
                emailDomains = parseEmailDomains(existing.emailDomainsJson),
                discipline = request.discipline.ifBlank { null },
                // M-2: 旧 typed API 不传该字段，必须显式保留现有多值状态（漏写会命中默认值静默重置）。
                operatorStatuses = parseOperatorStatuses(existing.operatorStatusesJson),
                // I2-5: 旧 typed API 不传类型筛选，必须显式保留（漏写会命中默认值静默重置）。
                expertTypes = parseExpertTypes(existing.expertTypesJson),
                // I-1: 旧 typed API 不传发件账号白名单，必须显式保留（漏写会静默重置为不限 = 扩大外发范围）。
                senderAccountCodes = parseSenderAccountCodes(objectMapper, existing.senderAccountCodesJson),
                templateId = request.templateId,
                // I4a-6 (M-2): 旧 typed API 不传门禁开关，必须显式保留存量值（漏写会命中默认值静默重置为 false）。
                gateFilterEnabled = existing.gateFilterEnabled,
                // I-1: 旧 typed API 不传方向三态，必须显式保留存量值（漏写会命中默认值静默重置为 ANY）。
                researchDirectionFilter = existing.researchDirectionFilter,
            )
        )
        return BatchSendConfig(
            sendType = sendType,
            autoEnabled = view.autoEnabled,
            cron = view.cron,
            dailyCap = LEGACY_DAILY_CAP_UNUSED,
            roundSize = view.roundSize,
            perMailIntervalMs = view.perMailIntervalMs,
            perRoundIntervalMs = view.perRoundIntervalMs,
            selfCheckTtlMinutes = view.selfCheckTtlMinutes,
            emailDomain = view.emailDomains.firstOrNull().orEmpty(),
            discipline = view.discipline.orEmpty(),
            templateId = view.templateId
```

## F-8 折叠进度

`src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:164` 至 `175`，逐字摘录：

```kt
    private fun buildProgressRows(executionId: Long): List<ExecutionProgressRow> {
        val logs = progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(executionId)
        val (zeroRows, roundRows) = logs.partition { it.batchNumber == 0 }
        val initRow = zeroRows.firstOrNull()
        val finalRows = zeroRows.drop(1)
        return buildList {
            initRow?.let { add(it to "INIT") }
            addAll(roundRows.groupBy { it.batchNumber }.map { (_, group) -> group.last() to "ROUND" })
            finalRows.forEach { add(it to "FINAL") }
        }
            .sortedBy { (row, _) -> row.id ?: 0L }
            .map { (row, kind) -> toExecutionProgressRow(row, kind) }
```

## F-9 原 DOM 开关

`src/main/resources/static/index.html:1382` 至 `1398`，逐字摘录：

```html
                                </div>
                                <input type="hidden" id="batchConfigEditorExpertTypes" value="">
                                <div id="batchConfigEditorExpertTypesDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
                            </div>
                        </div>
                        <div class="batch-config-field batch-gate-field" id="editorFieldGateFilter">
                            <span class="batch-config-field-label">邮件模版门禁过滤</span>
                            <div class="batch-gate-row">
                                <label class="batch-task-status-toggle batch-gate-toggle">
                                    <input type="checkbox" id="batchConfigEditorGateFilter">
                                    <span class="batch-task-status-switch"></span>
                                    <span class="batch-task-status-label" id="batchConfigEditorGateFilterLabel">已关闭</span>
                                </label>
                                <span class="batch-gate-hint" id="batchConfigEditorGateFilterHint">仅向满足该模板必填字段的专家发送，缺字段的会在发送时被门禁拦下并计入失败。</span>
                            </div>
                            <div class="batch-gate-keys" id="batchConfigEditorGateFilterKeys" hidden></div>
                        </div>
```

## F-10 原 DOM 抽屉

`src/main/resources/static/index.html:1687` 至 `1725`，逐字摘录：

```html
        <aside id="batchExecutionLogDrawer" class="batch-log-drawer" hidden>
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
                <h3 id="batchLogDrawerTitle" style="margin:0;font-size:15px;">执行日志</h3>
                <button class="modal-close-btn" id="batchLogDrawerCloseBtn" type="button" style="font-size:20px;">×</button>
            </div>
            <select id="batchLogExecutionSelect" class="bsc-input bsc-select" style="margin-bottom:12px;">
                <option value="">选择执行记录...</option>
            </select>
            <div id="batchLogLive" class="batch-log-live" hidden>
                <div class="batch-log-live-head">
                    <span class="badge ok" id="batchLogLiveStatus">运行中</span>
                    <span class="batch-log-live-round" id="batchLogLiveRound"></span>
                    <button class="button small danger" id="batchLogLiveCancelBtn" type="button">取消执行</button>
                </div>
                <div class="task-progress-track"><div class="task-progress-fill" id="batchLogLiveFill"></div></div>
                <div class="batch-log-live-counts" id="batchLogLiveCounts"></div>
                <div class="batch-log-live-message" id="batchLogLiveMessage"></div>
                <div class="batch-log-live-accounts" id="batchLogLiveAccounts"></div>
                <div class="batch-log-live-hint">取消后将在当前批次结束时停止，已发出的邮件不会撤回。</div>
            </div>
            <div id="batchLogMetrics" class="batch-log-metrics"></div>
            <div id="batchLogIntegrityWarning" class="batch-log-integrity-warning" hidden></div>
            <div id="batchLogFailureSection">
                <h4 style="margin:14px 0 6px;font-size:13px;color:var(--error);">失败原因</h4>
                <div id="batchLogFailureReasons" class="batch-reason-list"></div>
            </div>
            <div id="batchLogSkippedSection">
                <h4 style="margin:14px 0 6px;font-size:13px;color:var(--warning);">跳过原因</h4>
                <div id="batchLogSkippedReasons" class="batch-reason-list"></div>
            </div>
            <div id="batchLogErrorSamples">
                <h4 style="margin:14px 0 6px;font-size:13px;">错误样例</h4>
                <div id="batchLogErrorSampleList"></div>
            </div>
            <div id="batchLogTimelineSection">
                <h4 style="margin:14px 0 6px;font-size:13px;">批次时间线</h4>
                <div id="batchLogTimeline" class="batch-timeline"></div>
            </div>
            <div id="batchLogStatusInfo" style="margin-top:12px;color:var(--text-muted);font-size:12px;"></div>
```

## F-11 开关 CSS

`src/main/resources/static/styles.css:9354` 至 `9415`，逐字摘录：

```css
.batch-task-status-toggle {
  position: relative;
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  color: var(--text-sidebar);
  font-size: 11px;
  font-weight: 500;
  text-transform: none;
  letter-spacing: 0;
  cursor: pointer;
}

.batch-task-status-toggle input[type="checkbox"] {
  position: absolute;
  width: 1px;
  height: 1px;
  min-height: 1px;
  padding: 0;
  margin: 0;
  opacity: 0;
}

.batch-task-status-switch {
  position: relative;
  display: block;
  width: 36px;
  height: 20px;
  border-radius: 999px;
  background: var(--border-strong);
  transition: background-color .15s ease, box-shadow .15s ease;
}

.batch-task-status-switch::after {
  content: "";
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--panel-bg);
  box-shadow: 0 1px 3px rgba(15, 23, 42, .22);
  transition: transform .15s ease;
}

.batch-task-status-toggle input:checked + .batch-task-status-switch {
  background: var(--primary);
}

.batch-task-status-toggle input:checked + .batch-task-status-switch::after {
  transform: translateX(16px);
}

.batch-task-status-toggle input:focus-visible + .batch-task-status-switch {
  box-shadow: 0 0 0 3px rgba(37, 99, 235, .18);
}

.batch-task-status-toggle input:checked ~ .batch-task-status-label {
  color: var(--primary);
}
```

## F-12 开关排列 CSS

`src/main/resources/static/styles.css:9708` 至 `9736`，逐字摘录：

```css
.batch-gate-field { grid-column: 1 / -1; }

.batch-gate-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.batch-gate-toggle {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.batch-gate-toggle .batch-task-status-label {
  font-size: 12px;
  font-weight: 600;
}

.batch-gate-field.is-disabled { opacity: .6; }
.batch-gate-field.is-disabled .batch-gate-toggle { cursor: not-allowed; }

.batch-gate-hint {
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.5;
}

```

## F-13 汇总 CSS

`src/main/resources/static/styles.css:9843` 至 `9850`，逐字摘录：

```css
.batch-log-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 14px 0; }
.batch-log-metric { padding: 10px; border: 1px solid rgba(15, 23, 42, .08); border-radius: var(--radius-md); background: var(--bg-subtle); }
.batch-log-metric-label { color: var(--text-muted); font-size: 11px; }
.batch-log-metric-value { margin-top: 3px; color: var(--text-main); font-size: 18px; font-weight: 700; }
.batch-log-metric.is-success .batch-log-metric-value { color: var(--success); }
.batch-log-metric.is-failure .batch-log-metric-value { color: var(--error); }
.batch-log-metric.is-skipped .batch-log-metric-value { color: var(--warning); }

```

## F-14 抽屉 CSS

`src/main/resources/static/styles.css:9425` 至 `9439`，逐字摘录：

```css
.batch-log-drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 4;
  width: min(620px, 72%);
  padding: 22px;
  overflow: auto;
  background: rgba(255, 255, 255, .96);
  backdrop-filter: blur(8px);
  border-left: 1px solid rgba(15, 23, 42, .08);
  box-shadow: -12px 0 32px rgba(15, 23, 42, .12);
}

```

## F-15 设计 tokens

`src/main/resources/static/styles.css:1` 至 `77`，逐字摘录：

```css
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
```

## F-16 发信目标

`src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:33` 至 `45`，逐字摘录：

```kt

        val domain = account.senderEmail.substringAfter("@")
        val messageId = "<intro-${expert.orcidId}-${UUID.randomUUID()}@$domain>"

        val plain = rendered.body
        val mail = ComposedMail(
            to = expert.email ?: error("Expert email is required for introduction mail"),
            subject = rendered.subject,
            body = mailContentService.plainTextToHtml(plain, listOfNotNull(variables["unsubscribeUrl"])),
            html = true,
            text = plain,
            messageId = messageId
        )
```

## 审计口径与排除项

- E-2 的 MailComposeTemplateService 是模板外键/解析的注释，V129 是撞号说明；不算配置表写入口。当前业务写入口在配置服务；泛型 CrudRepository.save 也纳入审计。
- E-3 包括 mail_record.task_execution_id、发现队列表外键等同名字段；它们不是 task_execution 的写入口。执行表业务写入由 TaskExecutionService 经 TaskExecutionRepository；归档清理由 TaskAuditRetentionService 调用 deleteOlderThan。列表/详情/活动/进度控制器及邮件、发现关联查询是读方。
- E-4 中邮件 timeline.tags、邮件摘要标签、AI 回复 spike 参数不是专家 ES.tags。专家标签读方：ExpertSearchService（反序列化/投影/过滤/聚合）、ExpertIndexController（详情）、RecipientScope/发送 ES 查询、MailboxConversationService（专家资料）、AiTrainingController（候选/申请资料）、前端专家列表/标签选择器。前端通过既有接口取得这些标签。
- 专家标签写方：ExpertIndexController→addTag/removeTag；ExpertRevalidationService 追加 verified 并合并已有 tags；ExpertDiscoveryService 创建 discovered、晋级合并 discovered/auto_promoted；ExpertIndexWriterService 的全量文档写入/晋级/降级；导入脚本见 E-4。导入整文档替换可能覆盖已有标签是既有语义，本计划不运行或重构导入脚本。验证审计保存在独立表，不依赖当前标签仍然存在。
- E-6 证明最大版本是 V137，E-9 证明迁移测试仍有“最新版本=136”的陈旧断言。新增迁移用 V138/V139，执行前再检查版本占用；只更新 latest-target 断言，保留历史 target(V23/V116/V131/V133/V135) 的断言。
- E-10/F-4 证明请求快照会写入 request_payload；批量发送 PAUSED 不代表可持久化断点续跑，现有通用 interrupt 是结束运行的接口。本计划服务异常用 FAILED/PARTIAL_SUCCESS，不实现恢复队列。
- 没有通过此轮计划审计读取线上数据库或配置密钥；不声称线上 Emailable 已配置、余额充足或现网 ES dynamic 与仓库 JSON 一致。UI 基于此前已登录线上“批量邮件任务控制台”的两标签页+右侧抽屉布局，代码合同以本证据为准。

## 外部 API 事实（2026-09-24 核对）

- [鉴权](https://emailable.com/docs/api/authentication/)：服务端私钥支持 Bearer；test_ 密钥返回模拟结果。
- [单邮箱 API](https://emailable.com/docs/api/emails/)：GET /v1/verify；email、smtp、accept_all、timeout；state/reason/email 是结果字段，四种 state 为 deliverable、undeliverable、risky、unknown。timeout 允许 2–10 秒，249 后短期重试有官方说明。
- [状态码](https://emailable.com/docs/api/status-codes/)：249 尚未完成；402 额度不足；401/403 鉴权失败；429 限流；5xx 服务故障。这些不是“邮箱不存在”。
- [速率](https://emailable.com/docs/api/rate-limits/)：标准单邮箱接口 25 次/秒。本计划串行、间隔至少 100ms 是实现选择，不是声称用户购买的额度。

## F-17 复用控件完整规则

以下从同一styles.css逐字提取；此计划只复用，不修改这些规则。

`src/main/resources/static/styles.css:9331`

```css
.batch-config-field-label {
  display: block;
  margin-bottom: 6px;
  color: var(--text-sidebar);
  font-size: 12px;
  font-weight: 600;
}
```

`src/main/resources/static/styles.css:9779`

```css
.batch-gate-pill {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border: 1px solid var(--primary);
  border-radius: var(--radius-lg);
  background: var(--primary-light);
  color: var(--primary);
  font-size: 11px;
  font-weight: 600;
}
```

`src/main/resources/static/styles.css:9792`

```css
.batch-gate-pill.is-off {
  border-color: var(--border);
  background: transparent;
  color: var(--text-muted);
}
```

`src/main/resources/static/styles.css:1054`

```css
.badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 600;
    font-family: var(--font-body);
    line-height: 1;
    background-color: var(--surface);
    color: var(--text-muted);
    border: 1px solid transparent;
}
```

`src/main/resources/static/styles.css:1068`

```css
.badge.ok {
    background-color: var(--success-bg);
    color: var(--success);
    border-color: var(--success-border);
}
```

`src/main/resources/static/styles.css:1074`

```css
.badge.warn {
    background-color: var(--warning-bg);
    color: var(--warning);
    border-color: var(--warning-border);
}
```

`src/main/resources/static/styles.css:1080`

```css
.badge.error {
    background-color: var(--error-bg);
    color: var(--error);
    border-color: var(--error-border);
}
```

`src/main/resources/static/styles.css:1086`

```css
.badge.info {
    background-color: var(--info-bg);
    color: var(--info);
    border-color: var(--info-border);
}
```

`src/main/resources/static/styles.css:802`

```css
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
```

`src/main/resources/static/styles.css:825`

```css
.button:hover {
    border-color: rgba(15, 23, 42, 0.2);
    background-color: var(--surface);
    transform: translateY(-1px);
    box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
}
```

`src/main/resources/static/styles.css:832`

```css
.button:active {
    transform: translateY(0) scale(0.97);
    box-shadow: none;
    opacity: 0.85;
}
```

`src/main/resources/static/styles.css:852`

```css
.button.secondary {
    background-color: var(--primary-light);
    border-color: rgba(var(--primary-rgb), 0.12);
    color: var(--primary);
}
```

`src/main/resources/static/styles.css:858`

```css
.button.secondary:hover {
    background-color: rgba(var(--primary-rgb), 0.1);
}
```

`src/main/resources/static/styles.css:2482`

```css
.button.small {
    height: 26px;
    min-height: 26px;
    padding: 0 8px;
    font-size: 11px;
}
```

`src/main/resources/static/styles.css:9568`

```css
.batch-config-diff-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  color: var(--error-strong);
  font-size: 11px;
  font-weight: 700;
}
```

`src/main/resources/static/styles.css:9577`

```css
.batch-config-diff-original { margin-top: 6px; color: var(--error-strong); font-size: 12px; }
```

## 规划自查回执

- 三个子计划白名单分别10/8/7项，去重23项；脚本从变更文件清单解析，不把规划文档算业务改动。
- 各子计划必需章节顺序、每条Invariant对应验收项、本地Markdown链接均经脚本检查。
- 知识输入取舍：双入口/legacy保值/身份/进度折叠/缓存键/标签ID/迁移顺序用于合同；旧“无快照持久化”“无EMAIL_INVALID入口”“初始化行全被过滤”已被代码证伪并修正。SMTP预占知识仅用于避免新门禁绕过原流程，不借本次要求重构全局SMTP；材料提醒轮次对称知识用于回归，介绍邮件专用验证不扩到材料提醒。
- 本次命中的知识没有从9跨到10的条目；未发现需要将5条同义知识合并的集合，不做无关知识库重组或CLAUDE扩改。
- 未执行计划中的功能测试/迁移/付费API/生产部署；上文测试命令与验收数值均为开发验收合同，不是已完成结果。
