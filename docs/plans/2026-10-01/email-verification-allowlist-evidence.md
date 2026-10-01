# Emailable 放行配置：源码证据

采集日期：2026-10-01；对象：当前工作树，不能据此声称线上已部署。

HEAD：2b036ccce7e9956f6ea27420d8bc9e057a011da2

路径相对仓库根目录。片段逐字提取并标行；运行期行为按实现计划验收，未在本轮执行业务测试。

## E-1 配置存储与调用面

命令：`rg -n batch_send_task_config|BatchSendTaskConfigRepository|toExecutionSnapshot\( src/main/kotlin src/main/resources/db/migration`

退出码：0

```text
src/main/resources/db/migration/V139__add_batch_email_verification_enabled.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V73__add_batch_config_id_to_task_execution.sql:9:        FOREIGN KEY (batch_config_id) REFERENCES batch_send_task_config(id);
src/main/resources/db/migration/V129__add_material_request_codes.sql:5:-- V128__add_research_direction_filter_to_batch_send_task_config.sql 撞号；后者已于
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
src/main/resources/db/migration/V103__add_reachability_filter_to_batch_send_task_config.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:6:UPDATE batch_send_task_config
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:12:ALTER TABLE batch_send_task_config DROP COLUMN operator_status;
src/main/resources/db/migration/V128__add_research_direction_filter_to_batch_send_task_config.sql:5:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V108__add_expert_types_to_batch_send_task_config.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V108__add_expert_types_to_batch_send_task_config.sql:6:UPDATE batch_send_task_config SET expert_types_json = '[]';
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql:6:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql:9:UPDATE batch_send_task_config
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql:13:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V74__repair_batch_send_task_config_encoding.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V74__repair_batch_send_task_config_encoding.sql:9:UPDATE batch_send_task_config
src/main/resources/db/migration/V74__repair_batch_send_task_config_encoding.sql:14:UPDATE batch_send_task_config
src/main/resources/db/migration/V142__add_exclude_verified_unavailable_emails.sql:1:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V91__add_rounds_per_run_to_batch_send_task_config.sql:4:ALTER TABLE batch_send_task_config ADD COLUMN rounds_per_run INT NOT NULL DEFAULT 1 AFTER round_size;
src/main/resources/db/migration/V91__add_rounds_per_run_to_batch_send_task_config.sql:6:UPDATE batch_send_task_config SET rounds_per_run = GREATEST(1, CEIL(daily_cap / round_size));
src/main/resources/db/migration/V99__add_gate_filter_enabled_to_batch_send_task_config.sql:3:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V110__require_expert_types_on_batch_send_task_config.sql:4:UPDATE batch_send_task_config
src/main/resources/db/migration/V93__add_regions_to_batch_send_task_config.sql:1:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V93__add_regions_to_batch_send_task_config.sql:3:UPDATE batch_send_task_config SET regions_json = '[]' WHERE regions_json = '';
src/main/resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql:5:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:2:-- TEXT 列不能带 DEFAULT（MySQL 限制），故照 V93__add_regions_to_batch_send_task_config.sql
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:5:ALTER TABLE batch_send_task_config
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:8:UPDATE batch_send_task_config
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql:14:ALTER TABLE batch_send_task_config DROP COLUMN email_domain;
src/main/resources/db/migration/V92__drop_daily_cap_from_batch_send_task_config.sql:1:ALTER TABLE batch_send_task_config DROP COLUMN daily_cap;
src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskExecution.kt:23:    /** Source batch_send_task_config id at launch; null for independent manual runs. Soft-delete safe. */
src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt:4:import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt:22: * Per-config dynamic cron scheduler (I-2). Each enabled [batch_send_task_config] row gets its own
src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt:28:    private val batchSendTaskConfigRepository: BatchSendTaskConfigRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:367:fun BatchSendTaskConfig.toExecutionSnapshot(
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:443: * I-1: `batch_send_task_config.sender_account_codes_json` 的唯一解析点。
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt:7:@Table("batch_send_task_config")
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:7:interface BatchSendTaskConfigRepository : CrudRepository<BatchSendTaskConfig, Long> {
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:13:        SELECT * FROM batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:22:        SELECT * FROM batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:32:        SELECT * FROM batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendTaskConfigRepository.kt:44:        SELECT * FROM batch_send_task_config
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:13:import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:32:    private val repository: BatchSendTaskConfigRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:629:        return "uk_batch_send_task_config_active_name" in messages ||
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:125:     * deleted; a template still referenced by `batch_send_task_config.template_id`
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:8:import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:35:    private val batchSendTaskConfigRepository: BatchSendTaskConfigRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:66:        val snapshot = config.toExecutionSnapshot(objectMapper)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:107:            snapshot = config.toExecutionSnapshot(objectMapper)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:124:            val snapshot = config.toExecutionSnapshot(objectMapper)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:154:            val snapshot = config.toExecutionSnapshot(objectMapper)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:258:            val snapshot = config.toExecutionSnapshot(objectMapper, oneRoundOnly = true)
```

## E-2 配置写入入口

命令：`rg -n fun (create|update|setEnabled|softDelete|updateLegacyConfig)|repository.save\( src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`

退出码：0

```text
66:    fun create(cmd: BatchSendTaskConfigCreateCommand): BatchSendTaskConfigView {
103:    fun update(id: Long, cmd: BatchSendTaskConfigUpdateCommand): BatchSendTaskConfigView {
149:    fun setEnabled(id: Long, enabled: Boolean): BatchSendTaskConfigView {
162:        val saved = repository.save(
173:    fun softDelete(id: Long) {
177:        repository.save(
199:    fun updateLegacyConfig(sendType: BatchSendType, request: BatchSendConfigUpdateRequest): BatchSendConfig {
610:            repository.save(entity)
```

## E-3 验证存储及清理

命令：`rg -n batch_email_verification|recordReusedDecision|recordDecision|recordTag|recordSend|markSending src/main/kotlin/com/weibo/talentintroduction/campaign src/main/kotlin/com/weibo/talentintroduction/task src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt`

退出码：0

```text
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:183:               SELECT 1 FROM batch_email_verification v
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:805:                                    batchEmailVerificationService.recordSend(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1002:                    if (verifiedTarget != null && !batchEmailVerificationService.markSending(verifiedTarget.rowId)) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1287:        batchEmailVerificationService.recordSend(passed.rowId, sendStatus, sendReason)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1293:            batchEmailVerificationService.recordSend(rowId, sendStatus, sendReason)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:130:                require(repository.recordReusedDecision(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:165:    fun markSending(rowId: Long): Boolean = audit("发送前预占 SENDING") {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:166:        repository.markSending(rowId, verificationNow())
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:174:    fun recordSend(rowId: Long, sendStatus: String, sendReason: String?) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:176:            require(repository.recordSend(rowId, sendStatus, sendReason, verificationNow()) == 1) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:194:                require(repository.recordTag(rowId, BatchEmailVerificationTagStatus.PENDING, null, verificationNow()) == 1) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:200:                require(repository.recordTag(rowId, tag.status, tag.error, verificationNow()) == 1) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:205:            recordSend(rowId, BatchEmailVerificationSendStatus.SKIPPED, BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:221:            require(repository.recordDecision(rowId, decision, providerState, providerReason, errorCode, requestCount, checkedAt, verificationNow()) == 1) {
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:12: * `batch_email_verification`（V138）的**唯一**业务写方与只读查询方（子计划 01 T1 / I-6 / I-9）。
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:16: * - 结果一旦固定（decision != PENDING），本类只允许 [recordSend] / [recordTag] 更新发送与标签列，
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:18: * - 发送前用 [markSending] 条件更新（PASS + NOT_SENT → SENDING），必须影响 1 行才允许 SMTP；
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:50:        return requireNotNull(keyHolder.key) { "batch_email_verification insert returned no generated id" }.toLong()
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:57:    fun recordDecision(
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:99:    fun recordReusedDecision(
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:108:    fun recordTag(id: Long, tagStatus: String, tagError: String?, now: LocalDateTime): Int =
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:112:    fun recordSend(id: Long, sendStatus: String, sendReason: String?, now: LocalDateTime): Int =
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:120:    fun markSending(id: Long, now: LocalDateTime): Boolean =
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:196:            INSERT INTO batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:203:            UPDATE batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:211:              FROM batch_email_verification v
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:219:                      FROM batch_email_verification n
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:231:            UPDATE batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:238:            UPDATE batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:244:            UPDATE batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:250:            UPDATE batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:259:              FROM batch_email_verification
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:280:              FROM batch_email_verification
```

## E-4 修改前 DOM（逐字）

### src/main/resources/static/index.html:1456

```text
 1456                         <div class="batch-config-field batch-gate-field" id="editorFieldEmailVerification">
 1457                             <span class="batch-config-field-label">发送前验证邮箱（Emailable）</span>
 1458                             <div class="batch-gate-row">
 1459                                 <label class="batch-task-status-toggle batch-gate-toggle">
 1460                                     <input type="checkbox" id="batchConfigEditorEmailVerification" aria-describedby="batchConfigEditorEmailVerificationHint">
 1461                                     <span class="batch-task-status-switch" aria-hidden="true"></span>
 1462                                     <span class="batch-task-status-label" id="batchConfigEditorEmailVerificationLabel">已关闭</span>
 1463                                 </label>
 1464                                 <span class="batch-gate-hint" id="batchConfigEditorEmailVerificationHint">仅不可投递（undeliverable）跳过并标记邮箱异常；risky / unknown 按策略放行。单邮箱验证未完成、超时或响应异常时暂缓该邮箱；鉴权、额度、限流或服务故障停止本次执行。会消耗 Emailable 额度。</span>
 1465                             </div>
 1466                         </div>
```

### src/main/resources/static/index.html:1718

```text
 1718                     <div class="batch-config-field batch-gate-field" id="manualFieldEmailVerification">
 1719                         <span class="batch-config-field-label">发送前验证邮箱（Emailable）</span>
 1720                         <div class="batch-gate-row">
 1721                             <label class="batch-task-status-toggle batch-gate-toggle">
 1722                                 <input type="checkbox" id="batchManualEmailVerification" aria-describedby="batchManualEmailVerificationHint">
 1723                                 <span class="batch-task-status-switch" aria-hidden="true"></span>
 1724                                 <span class="batch-task-status-label" id="batchManualEmailVerificationLabel">已关闭</span>
 1725                             </label>
 1726                             <span class="batch-gate-hint" id="batchManualEmailVerificationHint">仅不可投递（undeliverable）跳过并标记邮箱异常；risky / unknown 按策略放行。单邮箱验证未完成、超时或响应异常时暂缓该邮箱；鉴权、额度、限流或服务故障停止本次执行。会消耗 Emailable 额度。仅影响本次执行。</span>
 1727                         </div>
 1728                         <span class="batch-config-diff-badge" hidden>已修改</span>
 1729                         <div class="batch-config-diff-original" hidden></div>
 1730                     </div>
```

## E-5 现有样式基线（完整规则块）

### src/main/resources/static/styles.css:1

```text
    1 :root {
    2     /* Brand — modern business blue */
    3     --primary: #1e40af;
    4     --primary-hover: #1e3a8a;
    5     --primary-active: #172554;
    6     --primary-rgb: 30, 64, 175;
    7     --primary-light: rgba(var(--primary-rgb), 0.07);
    8     --primary-tint: rgba(var(--primary-rgb), 0.1);
    9 
   10     --bg-main: #f5f7fb;
   11     --bg-sidebar: #ffffff;
   12     --bg-sidebar-hover: rgba(var(--primary-rgb), 0.06);
   13     --bg-sidebar-active: rgba(var(--primary-rgb), 0.09);
   14 
   15     --panel-bg: rgba(255, 255, 255, 0.55);
   16     --panel-border: rgba(15, 23, 42, 0.08);
   17     --line: rgba(15, 23, 42, 0.055);
   18     --border: rgba(15, 23, 42, 0.11);
   19     --surface: rgba(15, 23, 42, 0.022);
   20 
   21     --text-main: #1e293b;
   22     --text-muted: #94a3b8;
   23     --text-sidebar: #64748b;
   24     --text-sidebar-active: #1e293b;
   25     --text-secondary: #475569;
   26     --text-strong: #334155;
   27     --ink: #1e293b;
   28     --bg-subtle: #f8fafc;
   29     --border-strong: #cbd5e1;
   30     --primary-bright: #3b82f6;
   31 
   32     --success: #059669;
   33     --success-rgb: 5, 150, 105;
   34     --success-bg: rgba(var(--success-rgb), 0.08);
   35     --success-border: rgba(var(--success-rgb), 0.18);
   36     --green: var(--success);
   37 
   38     --error: #e11d48;
   39     --error-rgb: 225, 29, 72;
   40     --error-bg: rgba(var(--error-rgb), 0.07);
   41     --error-border: rgba(var(--error-rgb), 0.16);
   42     --error-strong: #be123c;
   43     --red: var(--error);
   44 
   45     --warning: #d97706;
   46     --warning-rgb: 217, 119, 6;
   47     --warning-bg: rgba(var(--warning-rgb), 0.08);
   48     --warning-border: rgba(var(--warning-rgb), 0.2);
   49     --warning-strong: #b45309;
   50     --warning-bright: #f59e0b;
   51     --amber: var(--warning);
   52 
   53     --info: #0ea5e9;
   54     --info-rgb: 14, 165, 233;
   55     --info-bg: rgba(var(--info-rgb), 0.08);
   56     --info-border: rgba(var(--info-rgb), 0.2);
   57 
   58     --z-sticky: 10;
   59     --z-dropdown: 20;
   60     --z-overlay: 50;
   61     --z-drawer: 60;
   62     --z-modal: 1000;
   63     --z-confirm: 1200;
   64     --z-toast: 9999;
   65 
   66     --glass-border: rgba(255, 255, 255, 0.5);
   67     --glass-shadow: 0 8px 32px rgba(var(--primary-rgb), 0.1);
   68     --glass-blur: blur(16px);
   69 
   70     --radius-sm: 7px;
   71     --radius-md: 10px;
   72     --radius-lg: 18px;
   73 
   74     --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
   75     --shadow-md: 0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.03);
   76     --shadow-lg: 0 10px 28px -8px rgba(15, 23, 42, 0.14), 0 2px 6px rgba(15, 23, 42, 0.05);
   77     --shadow-xl: 0 20px 48px -12px rgba(15, 23, 42, 0.2), 0 4px 12px rgba(15, 23, 42, 0.06);
```

### src/main/resources/static/styles.css:1117

```text
 1117 label {
 1118     display: flex;
 1119     flex-direction: column;
 1120     gap: 4px;
 1121     color: var(--text-muted);
 1122     font-size: 11px;
 1123     font-weight: 600;
 1124     font-family: var(--font-body);
 1125     text-transform: uppercase;
 1126     letter-spacing: 0.3px;
 1127 }
 1128 
 1129 input, select, textarea {
 1130     width: 100%;
 1131     height: 34px;
 1132     min-height: 34px;
 1133     border: 1px solid var(--border);
 1134     border-radius: var(--radius-sm);
 1135     padding: 6px 10px;
 1136     color: var(--text-main);
 1137     background-color: var(--panel-bg);
 1138     transition: var(--transition);
 1139     outline: none;
 1140     font-size: 13px;
 1141 }
 1142 
 1143 input:hover, select:hover, textarea:hover {
 1144     border-color: rgba(15, 23, 42, 0.2);
 1145 }
 1146 
 1147 input:focus, select:focus, textarea:focus {
 1148     border-color: var(--primary);
 1149     background-color: var(--panel-bg);
 1150     box-shadow: 0 0 0 3px rgba(var(--primary-rgb), 0.1), 0 0 12px rgba(var(--primary-rgb), 0.06);
 1151     transition: border-color 0.2s ease, box-shadow 0.25s ease;
 1152 }
 1153 
 1154 input:disabled, select:disabled, textarea:disabled {
 1155     background-color: var(--surface);
 1156     color: var(--text-muted);
 1157     cursor: not-allowed;
 1158     border-color: var(--border);
 1159 }
 1160 
 1161 textarea {
 1162     resize: vertical;
 1163     height: auto;
 1164 }
 1165 
 1166 .checkbox-row {
 1167     flex-direction: row;
 1168     align-items: center;
 1169     gap: 8px;
 1170     min-height: 32px;
 1171     cursor: pointer;
 1172     font-weight: 500;
 1173     color: var(--text-main);
 1174     font-family: var(--font-body);
 1175     text-transform: none;
 1176     letter-spacing: 0;
 1177 }
 1178 
 1179 .checkbox-row input[type="checkbox"] {
 1180     width: 14px;
 1181     height: 14px;
 1182     min-height: auto;
 1183     border-radius: 2px;
 1184     accent-color: var(--primary);
 1185     cursor: pointer;
 1186 }
```

### src/main/resources/static/styles.css:9257

```text
 9257 .batch-config-editor-section {
 9258   padding: 18px;
 9259   border: 1px solid rgba(15, 23, 42, .08);
 9260   border-radius: var(--radius-lg);
 9261   background: var(--panel-bg);
 9262   box-shadow: 0 1px 2px rgba(15, 23, 42, .03);
 9263 }
 9264 
 9265 .batch-config-editor-section + .batch-config-editor-section {
 9266   margin-top: 14px;
 9267 }
 9268 
 9269 .batch-config-editor-section-heading {
 9270   display: flex;
 9271   align-items: baseline;
 9272   gap: 10px;
 9273   margin-bottom: 14px;
 9274 }
 9275 
 9276 .batch-config-editor-section-heading h4 {
 9277   margin: 0;
 9278   color: var(--primary);
 9279   font-size: 14px;
 9280   font-weight: 700;
 9281 }
 9282 
 9283 .batch-config-editor-section-heading span {
 9284   color: var(--text-muted);
 9285   font-size: 11px;
 9286 }
 9287 
 9288 .batch-config-editor-grid {
 9289   display: grid;
 9290   grid-template-columns: repeat(2, minmax(0, 1fr));
 9291   gap: 14px 16px;
 9292 }
 9293 
 9294 .batch-config-editor-grid-controls {
 9295   grid-template-columns: repeat(3, minmax(0, 1fr));
 9296 }
 9297 
 9298 .batch-config-editor-hint {
 9299   margin-top: 10px;
 9300   padding: 8px 12px;
 9301   border-radius: var(--radius-md);
 9302   background: rgba(37, 99, 235, .06);
 9303   color: var(--text-secondary);
 9304   font-size: 12px;
 9305   line-height: 1.6;
 9306 }
```

### src/main/resources/static/styles.css:9361

```text
 9361 .batch-config-editor .batch-config-field {
 9362   min-width: 0;
 9363   padding: 0;
 9364   border: 0;
 9365   border-radius: 0;
 9366 }
 9367 
 9368 .batch-config-editor .bsc-input {
 9369   width: 100%;
 9370 }
 9371 
 9372 .batch-config-field-label {
 9373   display: block;
 9374   margin-bottom: 6px;
 9375   color: var(--text-sidebar);
 9376   font-size: 12px;
 9377   font-weight: 600;
 9378 }
 9379 
 9380 .batch-config-editor-actions {
 9381   position: sticky;
 9382   z-index: 2;
 9383   bottom: -28px;
 9384   display: flex;
 9385   justify-content: flex-end;
 9386   gap: 10px;
 9387   margin: 16px -28px -28px;
 9388   padding: 14px 28px;
 9389   border-top: 1px solid rgba(15, 23, 42, .08);
 9390   background: rgba(255, 255, 255, .96);
 9391   box-shadow: 0 -8px 20px rgba(15, 23, 42, .04);
 9392   backdrop-filter: blur(8px);
 9393 }
 9394 
 9395 .batch-task-status-toggle {
 9396   position: relative;
 9397   display: inline-flex;
 9398   flex-direction: column;
 9399   align-items: flex-start;
 9400   gap: 5px;
 9401   color: var(--text-sidebar);
 9402   font-size: 11px;
 9403   font-weight: 500;
 9404   text-transform: none;
 9405   letter-spacing: 0;
 9406   cursor: pointer;
 9407 }
 9408 
 9409 .batch-task-status-toggle input[type="checkbox"] {
 9410   position: absolute;
 9411   width: 1px;
 9412   height: 1px;
 9413   min-height: 1px;
 9414   padding: 0;
 9415   margin: 0;
 9416   opacity: 0;
 9417 }
 9418 
 9419 .batch-task-status-switch {
 9420   position: relative;
 9421   display: block;
 9422   width: 36px;
 9423   height: 20px;
 9424   border-radius: 999px;
 9425   background: var(--border-strong);
 9426   transition: background-color .15s ease, box-shadow .15s ease;
 9427 }
 9428 
 9429 .batch-task-status-switch::after {
 9430   content: "";
 9431   position: absolute;
 9432   top: 2px;
 9433   left: 2px;
 9434   width: 16px;
 9435   height: 16px;
 9436   border-radius: 50%;
 9437   background: var(--panel-bg);
 9438   box-shadow: 0 1px 3px rgba(15, 23, 42, .22);
 9439   transition: transform .15s ease;
 9440 }
 9441 
 9442 .batch-task-status-toggle input:checked + .batch-task-status-switch {
 9443   background: var(--primary);
 9444 }
 9445 
 9446 .batch-task-status-toggle input:checked + .batch-task-status-switch::after {
 9447   transform: translateX(16px);
 9448 }
 9449 
 9450 .batch-task-status-toggle input:focus-visible + .batch-task-status-switch {
 9451   box-shadow: 0 0 0 3px rgba(37, 99, 235, .18);
 9452 }
 9453 
 9454 .batch-task-status-toggle input:checked ~ .batch-task-status-label {
 9455   color: var(--primary);
 9456 }
```

### src/main/resources/static/styles.css:9578

```text
 9578 .batch-config-field {
 9579   position: relative;
 9580   padding: 12px;
 9581   border: 1px solid rgba(15, 23, 42, .08);
 9582   border-radius: var(--radius-md);
 9583   background: var(--panel-bg);
 9584 }
 9585 
 9586 .batch-config-field.is-config-diff {
 9587   border-color: var(--error);
 9588   background: #fff7f8;
 9589   box-shadow: 0 0 0 1px rgba(225, 29, 72, .08);
 9590 }
 9591 
 9592 .batch-manual-section .batch-config-field {
 9593   min-width: 0;
 9594   padding: 0;
 9595   border: 0;
 9596   border-radius: 0;
 9597 }
 9598 
 9599 .batch-manual-section .batch-config-field.is-config-diff {
 9600   padding: 10px;
 9601   border: 1px solid var(--error);
 9602   border-radius: var(--radius-md);
 9603 }
 9604 
 9605 .batch-manual-section .bsc-input {
 9606   width: 100%;
 9607 }
 9608 
 9609 .batch-config-diff-badge {
 9610   position: absolute;
 9611   top: 10px;
 9612   right: 10px;
 9613   color: var(--error-strong);
 9614   font-size: 11px;
 9615   font-weight: 700;
 9616 }
 9617 
 9618 .batch-config-diff-original { margin-top: 6px; color: var(--error-strong); font-size: 12px; }
```

### src/main/resources/static/styles.css:9748

```text
 9748 /* ── 邮件模版门禁过滤（P4b / S4b-1、S4b-2） ─────────────────────── */
 9749 .batch-gate-field { grid-column: 1 / -1; }
 9750 
 9751 .batch-gate-row {
 9752   display: flex;
 9753   align-items: center;
 9754   flex-wrap: wrap;
 9755   gap: 12px;
 9756 }
 9757 
 9758 .batch-gate-toggle {
 9759   flex-direction: row;
 9760   align-items: center;
 9761   gap: 8px;
 9762 }
 9763 
 9764 .batch-gate-toggle .batch-task-status-label {
 9765   font-size: 12px;
 9766   font-weight: 600;
 9767 }
 9768 
 9769 .batch-gate-field.is-disabled { opacity: .6; }
 9770 .batch-gate-field.is-disabled .batch-gate-toggle { cursor: not-allowed; }
 9771 
 9772 .batch-gate-hint {
 9773   color: var(--text-muted);
 9774   font-size: 11px;
 9775   line-height: 1.5;
 9776 }
 9777 
 9778 .batch-gate-hint.is-warn { color: var(--warning-strong); }
```

### src/main/resources/static/styles.css:9874

```text
 9874 .batch-manual-confirm-overlay { z-index: var(--z-confirm); }
 9875 .batch-manual-confirm-dialog { width: min(620px, calc(100vw - 32px)); max-height: min(720px, calc(100vh - 40px)); overflow: auto; }
 9876 .batch-manual-confirm-summary { padding: 12px; border-radius: var(--radius-md); background: var(--bg-subtle); color: var(--text-secondary); }
 9877 .batch-manual-confirm-warning { margin-top: 12px; color: var(--error-strong); font-size: 12px; }
 9878 .batch-manual-confirm-table { width: 100%; margin-top: 12px; border-collapse: collapse; }
 9879 .batch-manual-confirm-table th,
 9880 .batch-manual-confirm-table td { padding: 9px 10px; border-bottom: 1px solid rgba(15, 23, 42, .08); text-align: left; }
 9881 .batch-manual-confirm-old { color: var(--text-muted); text-decoration: line-through; }
 9882 .batch-manual-confirm-new { color: var(--error-strong); font-weight: 600; }
```

### src/main/resources/static/styles.css:11840

```text
11840 .batch-email-verification { margin: 14px 0; }
11841 .batch-email-verification h4 { margin: 0 0 8px; font-size: 13px; color: var(--text-main); }
11842 .batch-email-verification-note { margin: 6px 0; color: var(--text-sidebar); font-size: 12px; line-height: 1.6; }
11843 .batch-email-verification-note.is-error { color: var(--error-strong); }
11844 .batch-email-verification-table-wrap { max-width: 100%; overflow-x: auto; border: 1px solid var(--panel-border); border-radius: var(--radius-md); }
11845 .batch-email-verification-table { width: 100%; min-width: 560px; border-collapse: collapse; font-size: 12px; line-height: 1.5; }
11846 .batch-email-verification-table th, .batch-email-verification-table td { padding: 8px 10px; text-align: left; vertical-align: top; border-bottom: 1px solid var(--panel-border); overflow-wrap: anywhere; }
11847 .batch-email-verification-table th { color: var(--text-sidebar); background: var(--bg-subtle); font-weight: 600; }
11848 .batch-email-verification-table tbody tr:last-child td { border-bottom: 0; }
11849 .batch-email-verification-table details { margin-top: 4px; color: var(--text-secondary); }
11850 .batch-email-verification-table summary { cursor: pointer; color: var(--primary); }
11851 .batch-email-verification-table summary:hover { color: var(--primary-hover); }
11852 .batch-email-verification-table summary:active { color: var(--primary-active); }
11853 .batch-email-verification-table summary:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; border-radius: var(--radius-sm); }
11854 .batch-email-verification-pager { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
11855 .batch-email-verification-pager .button:disabled { opacity: .5; cursor: not-allowed; pointer-events: none; }
```

## E-6 当前缓存键（测试零命中，输出仅 index）

命令：`rg -n 20260930-manual-template-reference src/test src/main/resources/static/index.html`

退出码：0

```text
src/main/resources/static/index.html:11:    <link rel="stylesheet" href="styles.css?v=20260930-manual-template-reference">
src/main/resources/static/index.html:12:    <link rel="stylesheet" href="expert-materials.css?v=20260930-manual-template-reference">
src/main/resources/static/index.html:13:    <link rel="stylesheet" href="mailbox-chat.css?v=20260930-manual-template-reference">
src/main/resources/static/index.html:14:    <link rel="stylesheet" href="meeting-confirmation.css?v=20260930-manual-template-reference">
src/main/resources/static/index.html:15:    <link rel="stylesheet" href="world-clock.css?v=20260930-manual-template-reference">
src/main/resources/static/index.html:2323:<script src="trust-reply-workbench.js?v=20260930-manual-template-reference"></script>
src/main/resources/static/index.html:2324:<script src="expert-materials.js?v=20260930-manual-template-reference"></script>
src/main/resources/static/index.html:2325:<script src="meeting-confirmation.js?v=20260930-manual-template-reference"></script>
src/main/resources/static/index.html:2326:<script src="mailbox-chat.js?v=20260930-manual-template-reference"></script>
src/main/resources/static/index.html:2327:<script src="app.js?v=20260930-manual-template-reference"></script>
src/main/resources/static/index.html:2328:<script src="world-clock.js?v=20260930-manual-template-reference"></script>
```

## E-7 前端测试读取改动函数的文件全集

命令：`rg -l deepCloneConfig|fillManualFormDefaults|fillManualFormFromDraft|readManualFormValues|buildManualExecutionSnapshot|buildConfigEditorRecipientSnapshot|saveBatchConfigEditor|showBatchConfigEditor|formatManualDiffValue|showBatchManualConfirm|refreshEmailVerificationState|updateEmailVerificationToggleLabel src/test/js`

退出码：0

```text
src/test/js/batchExpertTypeFilter.test.js
src/test/js/batchSenderFilter.test.js
src/test/js/batchSendTaskConsoleInteraction.test.js
src/test/js/batchEmailVerification.test.js
src/test/js/batchManualExecutionLog.test.js
src/test/js/expertTagBatchFix.test.js
```

## E-8 核心行为片段

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:117

```text
  117         val now = verificationNow()
  118         val reused = context.reuseOf(normalizedEmail)?.takeIf {
  119             it.checkedAt > now.minusYears(1) && it.checkedAt <= now
  120         } ?: audit("查询历史验证") {
  121             repository.findReusable(normalizedEmail, now)?.let {
  122                 // 复用供应商事实，不沿用旧发送策略的 SKIP；原行与原检查时间保持不变。
  123                 ReusedProviderResult(requireNotNull(decisionForState(it.providerState)), it.providerState,
  124                     it.providerReason, requireNotNull(it.checkedAt), it.id)
  125             }
  126         }
  127         if (context.isCancelled()) return VerificationResult.Cancelled(rowId)
  128         if (reused != null) {
  129             audit("记录历史验证复用") {
  130                 require(repository.recordReusedDecision(
  131                     rowId, reused.sourceRowId, reused.decision, reused.providerState,
  132                     reused.providerReason, reused.checkedAt, verificationNow()
  133                 ) == 1) { "复用验证结论未落库：id=$rowId" }
  134             }
  135             context.remember(normalizedEmail, reused)
  136             return conclude(context, target.copy(orcidId = normalizedOrcid), normalizedEmail, rowId, reused.decision, null)
  137         }
  138 
  139         val outcome = requestNewResult(context, normalizedEmail) ?: return VerificationResult.Cancelled(rowId)
  140         val checkedAt = verificationNow()
  141         persistDecision(
  142             rowId, outcome.decision, outcome.providerState, outcome.providerReason,
  143             outcome.errorCode, outcome.requestCount, checkedAt
  144         )
  145         if (outcome.decision in REUSABLE_DECISIONS) {
  146             context.remember(
  147                 normalizedEmail,
  148                 ReusedProviderResult(outcome.decision, outcome.providerState, outcome.providerReason, checkedAt, rowId)
  149             )
  150         }
  151         return conclude(
  152             context,
  153             target.copy(orcidId = normalizedOrcid),
  154             normalizedEmail,
  155             rowId,
  156             outcome.decision,
  157             outcome.errorCode
  158         )
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:182

```text
  182     private fun conclude(
  183         context: ExecutionVerificationContext,
  184         target: EmailVerificationTarget,
  185         normalizedEmail: String,
  186         rowId: Long,
  187         decision: String,
  188         errorCode: String?
  189     ): VerificationResult = when (decision) {
  190         BatchEmailVerificationDecision.PASS -> VerificationResult.Passed(rowId, normalizedEmail)
  191         BatchEmailVerificationDecision.SKIP -> {
  192             // 标签处理是 SKIP 的独立结果：先 PENDING（崩溃后可见未完成），ES 处理完再写 APPLIED/FAILED。
  193             audit("记录标签待处理") {
  194                 require(repository.recordTag(rowId, BatchEmailVerificationTagStatus.PENDING, null, verificationNow()) == 1) {
  195                     "标签待处理未落库（受影响 0 行）：id=$rowId"
  196                 }
  197             }
  198             val tag = appendEmailAbnormalTag(target, normalizedEmail)
  199             audit("记录标签结果") {
  200                 require(repository.recordTag(rowId, tag.status, tag.error, verificationNow()) == 1) {
  201                     "标签结果未落库（受影响 0 行）：id=$rowId"
  202                 }
  203             }
  204             // SKIP 行不进 SMTP：send_status=SKIPPED + 跳过码（≠ 发送成功）。
  205             recordSend(rowId, BatchEmailVerificationSendStatus.SKIPPED, BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED)
  206             VerificationResult.Rejected(rowId, tag.status)
  207         }
  208         else -> VerificationResult.ServiceFailure(rowId, errorCode ?: BatchEmailVerificationErrorCodes.SERVICE_ERROR)
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:282

```text
  282         }
  283         val state = node.path("state").textOrNull()
  284         val reason = node.path("reason").textOrNull()
  285         val returnedEmail = node.path("email").textOrNull()
  286         if (state == null) {
  287             return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount)
  288         }
  289         if (returnedEmail == null || normalizeVerificationEmail(returnedEmail) != normalizedEmail) {
  290             log.warn("Email verification response does not echo the requested address for {}", normalizedEmail)
  291             return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount, state, reason)
  292         }
  293         val decision = decisionForState(state)
  294             ?: return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount, state, reason)
  295         return ProviderOutcome(decision, null, requestCount, state, reason)
  296     }
  297 
  298     /** 当前发送策略；未知协议值不是供应商明确的 unknown 结果。 */
  299     private fun decisionForState(state: String?): String? = when (state?.lowercase(Locale.ROOT)) {
  300         STATE_DELIVERABLE, STATE_RISKY, STATE_UNKNOWN -> BatchEmailVerificationDecision.PASS
  301         STATE_UNDELIVERABLE -> BatchEmailVerificationDecision.SKIP
  302         else -> null
  303     }
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt:310

```text
  310         val docId = target.expertDocId?.trim().orEmpty()
  311         if (docId.isEmpty()) return TagOutcome(BatchEmailVerificationTagStatus.FAILED, "MISSING_DOC_ID")
  312 
  313         val matchedLevels = mutableListOf<ExpertIndexLevel>()
  314         try {
  315             for (level in ExpertIndexLevel.values()) {
  316                 val document = expertSearchService.findByDocumentIds(level, listOf(docId)).firstOrNull() ?: continue
  317                 if (document.esDocId != docId) continue
  318                 if (ExpertIdNormalizer.normalize(document.orcidId) != target.orcidId) continue
  319                 if (normalizeVerificationEmail(document.email) != normalizedEmail) continue
  320                 matchedLevels += level
  321             }
  322         } catch (e: Exception) {
  323             log.warn("Failed to read expert copies for tagging docId={}: {}", docId, e.message)
  324             return TagOutcome(BatchEmailVerificationTagStatus.FAILED, "ES_READ_FAILED:${e.message.orEmpty().take(120)}")
  325         }
  326         if (matchedLevels.isEmpty()) return TagOutcome(BatchEmailVerificationTagStatus.FAILED, "NO_MATCHING_DOC")
  327 
  328         val failedLevels = matchedLevels.filterNot { level ->
  329             expertIndexWriterService.addTag(docId, EMAIL_ABNORMAL_TAG, level)
  330         }
  331         return if (failedLevels.isEmpty()) {
  332             TagOutcome(BatchEmailVerificationTagStatus.APPLIED, null)
  333         } else {
  334             TagOutcome(BatchEmailVerificationTagStatus.FAILED, "ES_WRITE_FAILED:" + failedLevels.joinToString(","))
  335         }
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt:209

```text
  209         private const val FIND_REUSABLE_BY_EMAILS_SQL = """
  210             SELECT v.*
  211               FROM batch_email_verification v
  212              WHERE v.email IN (/* EMAILS */)
  213                AND v.checked_at > ? AND v.checked_at <= ?
  214                AND v.request_count > 0 AND v.reused_from_id IS NULL AND v.error_code IS NULL
  215                AND ((v.decision = 'PASS' AND v.provider_state IN ('deliverable', 'risky', 'unknown'))
  216                  OR (v.decision = 'SKIP' AND v.provider_state IN ('undeliverable', 'risky', 'unknown')))
  217                AND NOT EXISTS (
  218                     SELECT 1
  219                       FROM batch_email_verification n
  220                      WHERE n.email = v.email
  221                        AND n.checked_at > ? AND n.checked_at <= ?
  222                        AND n.request_count > 0 AND n.reused_from_id IS NULL AND n.error_code IS NULL
  223                        AND ((n.decision = 'PASS' AND n.provider_state IN ('deliverable', 'risky', 'unknown'))
  224                          OR (n.decision = 'SKIP' AND n.provider_state IN ('undeliverable', 'risky', 'unknown')))
  225                        AND (n.checked_at > v.checked_at OR (n.checked_at = v.checked_at AND n.id > v.id))
  226                )
  227              ORDER BY v.email
  228         """
```

### src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt:178

```text
  178     @Modifying
  179     @Query("""
  180         DELETE FROM task_execution
  181          WHERE started_at < :cutoff
  182            AND NOT EXISTS (
  183                SELECT 1 FROM batch_email_verification v
  184                 WHERE v.task_execution_id = task_execution.id
  185                   AND v.request_count > 0 AND v.reused_from_id IS NULL AND v.error_code IS NULL
  186                   AND v.checked_at > DATE_SUB(CONVERT_TZ(UTC_TIMESTAMP(3), '+00:00', '+08:00'), INTERVAL 1 YEAR)
  187                   AND v.checked_at <= CONVERT_TZ(UTC_TIMESTAMP(3), '+00:00', '+08:00')
  188                   AND ((v.decision = 'PASS' AND v.provider_state IN ('deliverable', 'risky', 'unknown'))
  189                     OR (v.decision = 'SKIP' AND v.provider_state IN ('undeliverable', 'risky', 'unknown')))
  190            )
  191          ORDER BY started_at LIMIT :batchSize
  192     """)
  193     fun deleteOlderThan(cutoff: LocalDateTime, batchSize: Int): Int
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:683

```text
  683             var roundProcessed = 0
  684             var roundPassed = 0
  685             var roundRejected = 0
  686             var midRoundStop = false
  687             while (roundPassed < roundQuota && targetIterator.hasNext()) {
  688                 if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
  689                     wasCancelled = true
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:787

```text
  787                     when (outcome) {
  788                         is VerificationResult.Rejected -> {
  789                             accumulator.recordSkipped(
  790                                 BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED,
  791                                 "邮箱验证未通过：$email"
  792                             )
  793                             processedTotal++
  794                             roundProcessed++
  795                             roundRejected++
  796                             updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
  797                                 "RUNNING", "已跳过邮箱验证未通过：$email", errors, mode, roundNumber, config, runAccountStats,
  798                                 roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
  799                             continue
  800                         }
  801                         is VerificationResult.ServiceFailure -> {
  802                             if (BatchEmailVerificationErrorCodes.isRecipientFailure(outcome.errorCode)) {
  803                                 log.warn("Email verification deferred for ORCID {}: {}", normOrcid, outcome.errorCode)
  804                                 try {
  805                                     batchEmailVerificationService.recordSend(
  806                                         outcome.rowId, BatchEmailVerificationSendStatus.SKIPPED,
  807                                         BatchOutcomeReasonCodes.EMAIL_VERIFICATION_DEFERRED
  808                                     )
  809                                 } catch (e: EmailVerificationAuditException) {
  810                                     errors.add("验证审计写入失败：${e.message.orEmpty().take(120)}")
  811                                     stopReason = STOP_EMAIL_VERIFY_AUDIT_FAILED
  812                                     finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
  813                                     midRoundStop = true
  814                                     break
  815                                 }
  816                                 accumulator.recordSkipped(
  817                                     BatchOutcomeReasonCodes.EMAIL_VERIFICATION_DEFERRED,
  818                                     "邮箱验证暂缓：${outcome.errorCode}（$email）"
  819                                 )
  820                                 processedTotal++
  821                                 roundProcessed++
  822                                 roundRejected++
  823                                 updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
  824                                     "RUNNING", "邮箱验证暂缓：${outcome.errorCode}（$email）", errors, mode, roundNumber, config, runAccountStats,
  825                                     roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
  826                                 continue
  827                             }
  828                             log.warn("Email verification service failure for ORCID {}: {}", normOrcid, outcome.errorCode)
  829                             errors.add("邮箱验证服务故障：${outcome.errorCode}（$email）")
  830                             stopReason = outcome.errorCode
  831                             finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
  832                             midRoundStop = true
  833                             break
  834                         }
  835                         is VerificationResult.Cancelled -> {
  836                             outcome.rowId?.let {
  837                                 recordVerificationSendQuietly(it, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.CANCELLED)
  838                             }
  839                             wasCancelled = true
  840                             stopReason = "CANCELLED"
  841                             midRoundStop = true
  842                             break
  843                         }
  844                         is VerificationResult.Passed -> verified = outcome
  845                     }
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:103

```text
  103     fun update(id: Long, cmd: BatchSendTaskConfigUpdateCommand): BatchSendTaskConfigView {
  104         val existing = repository.findByIdAndDeletedAtIsNull(id)
  105             ?: throw NoSuchElementException("Batch send task config not found: $id")
  106         // I-1: 缺省/null 保留现值 —— Update 命令的 nullable 字段在这里与实体显式合并，
  107         // 绝不走「空值即清空」逻辑（旧客户端不传该字段时必须保住开启状态）。
  108         val normalized = normalizeAndValidate(
  109             cmd.toFields(
  110                 mergedEmailVerificationEnabled = cmd.emailVerificationEnabled ?: existing.emailVerificationEnabled,
  111                 mergedExcludeVerifiedUnavailableEmails = cmd.excludeVerifiedUnavailableEmails ?: existing.excludeVerifiedUnavailableEmails
  112             ),
  113             excludeId = id
  114         )
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:199

```text
  199     fun updateLegacyConfig(sendType: BatchSendType, request: BatchSendConfigUpdateRequest): BatchSendConfig {
  200         val existing = requireActiveLegacy(sendType)
  201         val id = existing.id ?: error("Batch send task config id is required")
  202         val view = update(
  203             id,
  204             BatchSendTaskConfigUpdateCommand(
  205                 configName = existing.configName,
  206                 autoEnabled = request.autoEnabled,
  207                 cron = request.cron,
  208                 roundSize = request.roundSize,
  209                 roundsPerRun = existing.roundsPerRun,
  210                 perMailIntervalMs = request.perMailIntervalMs,
  211                 perRoundIntervalMs = request.perRoundIntervalMs,
  212                 selfCheckTtlMinutes = request.selfCheckTtlMinutes,
  213                 funnelLevel = existing.funnelLevel,
  214                 tags = parseTags(existing.tagsJson),
  215                 regions = parseRegions(existing.regionsJson),
  216                 emailDomains = parseEmailDomains(existing.emailDomainsJson),
  217                 discipline = request.discipline.ifBlank { null },
  218                 // M-2: 旧 typed API 不传该字段，必须显式保留现有多值状态（漏写会命中默认值静默重置）。
  219                 operatorStatuses = parseOperatorStatuses(existing.operatorStatusesJson),
  220                 // I2-5: 旧 typed API 不传类型筛选，必须显式保留（漏写会命中默认值静默重置）。
  221                 expertTypes = parseExpertTypes(existing.expertTypesJson),
  222                 // I-1: 旧 typed API 不传发件账号白名单，必须显式保留（漏写会静默重置为不限 = 扩大外发范围）。
  223                 senderAccountCodes = parseSenderAccountCodes(objectMapper, existing.senderAccountCodesJson),
  224                 templateId = request.templateId,
  225                 // I4a-6 (M-2): 旧 typed API 不传门禁开关，必须显式保留存量值（漏写会命中默认值静默重置为 false）。
  226                 gateFilterEnabled = existing.gateFilterEnabled,
  227                 // I-1: 旧 typed API 不传方向三态，必须显式保留存量值（漏写会命中默认值静默重置为 ANY）。
  228                 researchDirectionFilter = existing.researchDirectionFilter,
  229                 // I-1: 旧 typed API 不传邮箱验证开关，必须显式保留存量值（漏写会命中 null→现值合并之外
  230                 // 的默认值，把已开启的验证策略静默关掉）。
  231                 emailVerificationEnabled = existing.emailVerificationEnabled,
  232                 excludeVerifiedUnavailableEmails = existing.excludeVerifiedUnavailableEmails,
  233             )
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:370

```text
  370         val mailType = resolveMailType(fields.templateId)
  371         // I-3: 发送前邮箱验证只支持 INTRODUCTION —— 在模板解析后判定，用户显式开启+材料提醒模板
  372         // 一律 400 拒绝，绝不暗中把开关关掉（暗中关闭会让运营以为策略已生效）。
  373         require(mailType == BatchSendType.INTRODUCTION.name || !fields.emailVerificationEnabled) {
  374             "发送前邮箱验证只支持介绍邮件（${BatchSendType.INTRODUCTION.name}），当前配置类型为 $mailType"
  375         }
  376         // I-1: 三态白名单是权威 —— 非法值在此拒绝（未传值/空白归一为 ANY）。
  377         val researchDirectionFilter = ResearchDirectionFilters.requireAllowed(fields.researchDirectionFilter)
  378 
  379         // I3-1/I3-2: INTRODUCTION 的研发类型必填非空 —— 空集合在子计划 04 之后
  380         // 等价于「发给零个人」，必须在保存时就拒绝，不能留到运行时。
  381         if (mailType == BatchSendType.INTRODUCTION.name) {
  382             require(expertTypes.isNotEmpty()) { "研发类型至少选择一个" }
  383         }
  384 
  385         return NormalizedConfig(
  386             configName = configName,
  387             mailType = mailType,
  388             autoEnabled = fields.autoEnabled,
  389             cron = fields.cron.trim(),
  390             roundSize = fields.roundSize,
  391             roundsPerRun = fields.roundsPerRun,
  392             perMailIntervalMs = fields.perMailIntervalMs,
  393             perRoundIntervalMs = fields.perRoundIntervalMs,
  394             selfCheckTtlMinutes = fields.selfCheckTtlMinutes,
  395             funnelLevel = funnelLevel,
  396             tagsJson = tagsJson,
  397             regionsJson = regionsJson,
  398             emailDomainsJson = emailDomainsJson,
  399             discipline = discipline,
  400             operatorStatusesJson = operatorStatusesJson,
  401             expertTypesJson = expertTypesJson,
  402             senderAccountCodesJson = senderAccountCodesJson,
  403             templateId = fields.templateId,
  404             gateFilterEnabled = fields.gateFilterEnabled,
  405             researchDirectionFilter = researchDirectionFilter,
  406             emailVerificationEnabled = fields.emailVerificationEnabled,
  407             excludeVerifiedUnavailableEmails = fields.excludeVerifiedUnavailableEmails
```

## E-9 旧版本断言与实际 Flyway 入口

命令：`rg -n assertEquals\("142"|private fun flyway|EnabledIfSystemProperty src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`

退出码：0

```text
12:import org.junit.jupiter.api.condition.EnabledIfSystemProperty
21:@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
59:        assertEquals("142", flyway().migrate().targetSchemaVersion)
87:        assertEquals("142", flyway().migrate().targetSchemaVersion)
123:        assertEquals("142", flyway.migrate().targetSchemaVersion)
133:        assertEquals("142", flyway().migrate().targetSchemaVersion)
166:        assertEquals("142", flyway().migrate().targetSchemaVersion)
254:        assertEquals("142", flyway().migrate().targetSchemaVersion)
296:        assertEquals("142", flyway().migrate().targetSchemaVersion)
393:        assertEquals("142", flyway().migrate().targetSchemaVersion)
566:        assertEquals("142", flyway().migrate().targetSchemaVersion)
688:        assertEquals("142", flyway().migrate().targetSchemaVersion)
727:        assertEquals("142", flyway().migrate().targetSchemaVersion)
789:        assertEquals("142", flyway().migrate().targetSchemaVersion)
889:        assertEquals("142", flyway().migrate().targetSchemaVersion)
924:        assertEquals("142", flyway().migrate().targetSchemaVersion)
1035:        assertEquals("142", flyway().migrate().targetSchemaVersion)
1142:        assertEquals("142", flyway().migrate().targetSchemaVersion)
1158:        assertEquals("142", flyway().migrate().targetSchemaVersion)
1193:        assertEquals("142", flyway().migrate().targetSchemaVersion)
1239:        assertEquals("142", flyway().migrate().targetSchemaVersion)
1315:        assertEquals("142", flyway().migrate().targetSchemaVersion)
1431:        assertEquals("142", flyway().migrate().targetSchemaVersion)
1576:        assertEquals("142", flyway().migrate().targetSchemaVersion)
1666:        assertEquals("142", flyway().migrate().targetSchemaVersion)
1843:        assertEquals("142", flyway().migrate().targetSchemaVersion)
2030:    private fun flyway(target: MigrationVersion? = null): Flyway {
```

## E-10 当前迁移版本

命令：`rg --files src/main/resources/db/migration`

退出码：0

```text
src/main/resources/db/migration/V80__add_qa_reply_policy.sql
src/main/resources/db/migration/V143__create_expert_replied_dismissal.sql
src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql
src/main/resources/db/migration/V139__add_batch_email_verification_enabled.sql
src/main/resources/db/migration/V138__create_batch_email_verification.sql
src/main/resources/db/migration/V3__seed_qa_rules.sql
src/main/resources/db/migration/V45__repair_qa_reply_body_encoding.sql
src/main/resources/db/migration/V17__seed_qa_rule_display_names.sql
src/main/resources/db/migration/V46__qa_reply_body_paragraphs.sql
src/main/resources/db/migration/V22__create_task_progress_log.sql
src/main/resources/db/migration/V115__create_rag_prompt_config.sql
src/main/resources/db/migration/V21__add_email_validation_cache.sql
src/main/resources/db/migration/V116__repair_rag_fact_encoding.sql
src/main/resources/db/migration/V30__create_email_suppression.sql
src/main/resources/db/migration/V25__create_admin_user.sql
src/main/resources/db/migration/V35__add_task_progress_batch_reject_reasons.sql
src/main/resources/db/migration/V107__strip_controlled_keys_from_program_overview.sql
src/main/resources/db/migration/V132__create_openalex_budget.sql
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql
src/main/resources/db/migration/V12__expert_contact_first_reply.sql
src/main/resources/db/migration/V112__create_rag_knowledge_base.sql
src/main/resources/db/migration/V39__qa_category_compose_order.sql
src/main/resources/db/migration/V4__create_task_execution.sql
src/main/resources/db/migration/V109__qa_fact_supply_and_controlled_key_repair.sql
src/main/resources/db/migration/V9__create_meeting_schedule_and_template.sql
src/main/resources/db/migration/V118__allow_attachment_metadata_only.sql
src/main/resources/db/migration/V48__add_country_to_expert_contact.sql
src/main/resources/db/migration/V122__seed_manual_meeting_confirmation_template.sql
src/main/resources/db/migration/V75__split_company_identity_from_agency_credentials.sql
src/main/resources/db/migration/V6__add_inbound_cleaning_and_intent.sql
src/main/resources/db/migration/V41__qa_overview_supersede.sql
src/main/resources/db/migration/V72__create_batch_send_task_config.sql
src/main/resources/db/migration/V108__add_expert_types_to_batch_send_task_config.sql
src/main/resources/db/migration/V18__repair_qa_rule_display_names_encoding.sql
src/main/resources/db/migration/V142__add_exclude_verified_unavailable_emails.sql
src/main/resources/db/migration/V20__disable_orphan_simulator_mail_account.sql
src/main/resources/db/migration/V140__reuse_batch_email_verification.sql
src/main/resources/db/migration/V117__convert_bounce_rate_pause_to_warning.sql
src/main/resources/db/migration/V53__inbound_mail_tag.sql
src/main/resources/db/migration/V68__qa_keyword_gap_and_contract_ip_rule.sql
src/main/resources/db/migration/V127__add_outbound_attachments_snapshot.sql
src/main/resources/db/migration/V64__add_subject_variants_and_snippet_variant_group.sql
src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql
src/main/resources/db/migration/V60__create_domain_reputation_history.sql
src/main/resources/db/migration/V56__material_reminder_template.sql
src/main/resources/db/migration/V91__add_rounds_per_run_to_batch_send_task_config.sql
src/main/resources/db/migration/V110__require_expert_types_on_batch_send_task_config.sql
src/main/resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql
src/main/resources/db/migration/V79__add_qa_answer_body.sql
src/main/resources/db/migration/V44__repair_qa_new_rule_display_names_encoding.sql
src/main/resources/db/migration/V50__add_global_auto_reply_setting.sql
src/main/resources/db/migration/V59__create_expert_analysis_result.sql
src/main/resources/db/migration/V119__create_mail_attachment_transfer.sql
src/main/resources/db/migration/V87__append_unsubscribe_line_to_cold_outreach_templates.sql
src/main/resources/db/migration/V55__create_ai_prompt_config.sql
src/main/resources/db/migration/V40__qa_rule_section_title.sql
src/main/resources/db/migration/V96__add_name_to_reply_snippet.sql
src/main/resources/db/migration/V43__add_bounce_record_failed_recipient.sql
src/main/resources/db/migration/V137__task_execution_interruption_recovery.sql
src/main/resources/db/migration/V128__add_research_direction_filter_to_batch_send_task_config.sql
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql
src/main/resources/db/migration/V74__repair_batch_send_task_config_encoding.sql
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql
src/main/resources/db/migration/V134__shared_inbox_owner.sql
src/main/resources/db/migration/V65__qa_rule_dedup_keyword_fix_and_overview.sql
src/main/resources/db/migration/V104__create_auto_reply_confidence_log.sql
src/main/resources/db/migration/V103__add_reachability_filter_to_batch_send_task_config.sql
src/main/resources/db/migration/V42__mail_record_qa_rule.sql
src/main/resources/db/migration/V125__create_meeting_calendar_event.sql
src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql
src/main/resources/db/migration/V62__unify_mail_templates.sql
src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql
src/main/resources/db/migration/V33__seed_require_orcid_setting.sql
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql
src/main/resources/db/migration/V136__add_compose_subject_snippet_id.sql
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql
src/main/resources/db/migration/V58__update_ai_training_qa_material_tiering.sql
src/main/resources/db/migration/V111__create_expert_material_status.sql
src/main/resources/db/migration/V47__create_reply_snippet.sql
src/main/resources/db/migration/V63__qa_more_details_overview_keyword.sql
src/main/resources/db/migration/V29__create_bounce_record.sql
src/main/resources/db/migration/V114__create_rag_fact_audit.sql
src/main/resources/db/migration/V66__create_ai_training_dialogue.sql
src/main/resources/db/migration/V82__split_trust_reply_atomic_facts.sql
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql
src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql
src/main/resources/db/migration/V86__add_expert_contact_sender_change_mark.sql
src/main/resources/db/migration/V81__ai_reply_due_diligence_keyword_parity.sql
src/main/resources/db/migration/V70__tighten_ai_reply_action_boundaries.sql
src/main/resources/db/migration/V144__create_discovery_schedule_setting.sql
src/main/resources/db/migration/V99__add_gate_filter_enabled_to_batch_send_task_config.sql
src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql
src/main/resources/db/migration/V28__add_sender_account_auto_pause.sql
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql
src/main/resources/db/migration/V16__simulator_campaign.sql
src/main/resources/db/migration/V83__create_trust_reply_workbench_state.sql
src/main/resources/db/migration/V141__create_mail_open_tracking.sql
src/main/resources/db/migration/V71__update_material_reminder_template.sql
src/main/resources/db/migration/V2__seed_mail_templates.sql
src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql
src/main/resources/db/migration/V92__drop_daily_cap_from_batch_send_task_config.sql
src/main/resources/db/migration/V31__add_mail_record_created_at_index.sql
src/main/resources/db/migration/V8__add_expert_contact_status_history.sql
src/main/resources/db/migration/V32__create_discovery_source_cursor.sql
src/main/resources/db/migration/V89__create_unsubscribe_token.sql
src/main/resources/db/migration/V26__eligibility_filter_settings.sql
src/main/resources/db/migration/V84__add_required_keys_to_compose_template.sql
src/main/resources/db/migration/V105__add_programme_identity_facts.sql
src/main/resources/db/migration/V37__create_dmarc_report.sql
src/main/resources/db/migration/V77__complete_company_identity_keywords.sql
src/main/resources/db/migration/V27__create_batch_send_setting.sql
src/main/resources/db/migration/V11__expert_contact_auto_reply_flag.sql
src/main/resources/db/migration/V129__add_material_request_codes.sql
src/main/resources/db/migration/V88__rewrite_unsubscribe_line_wording.sql
src/main/resources/db/migration/V102__add_task_progress_log_created_at_index.sql
src/main/resources/db/migration/V131__create_expert_academic_enrichment_job.sql
src/main/resources/db/migration/V106__add_remuneration_keyword_to_funding_support.sql
src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql
src/main/resources/db/migration/V49__create_mail_inbox_cursor.sql
src/main/resources/db/migration/V61__create_mail_compose_template.sql
src/main/resources/db/migration/V54__create_ai_training_qa.sql
src/main/resources/db/migration/V93__add_regions_to_batch_send_task_config.sql
src/main/resources/db/migration/V52__qa_trust_and_gap_rule_optimization.sql
src/main/resources/db/migration/V121__create_expert_follow.sql
src/main/resources/db/migration/V5__create_inbound_mail_processing.sql
src/main/resources/db/migration/V57__qa_material_tiering_and_funding.sql
src/main/resources/db/migration/V133__create_discovery_paper_queue.sql
src/main/resources/db/migration/V124__widen_expert_application_promotion_triggered_by.sql
src/main/resources/db/migration/V73__add_batch_config_id_to_task_execution.sql
src/main/resources/db/migration/V38__restructure_qa_categories_and_seed_new_rules.sql
src/main/resources/db/migration/V94__backfill_operator_status_for_manual_sends.sql
src/main/resources/db/migration/V1__create_business_tables.sql
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql
src/main/resources/db/migration/V135__add_batch_sender_account_codes.sql
src/main/resources/db/migration/V69__curate_ai_training_dialogue_styles.sql
src/main/resources/db/migration/V51__add_follow_up_marked.sql
src/main/resources/db/migration/V67__create_content_variant.sql
src/main/resources/db/migration/V34__add_sender_account_warmup_fields.sql
src/main/resources/db/migration/V100__add_task_execution_indexes.sql
src/main/resources/db/migration/V78__decouple_compose_templates_from_qa_rule.sql
```

## E-11 关键源码 SHA-256

```text
cfb1c4a8a25661d6024825f22a5eb87e61d248d3b15c740d0cb95802c6b91d97  src/main/resources/static/index.html
2dd28a74b38544639908da56d1d66ca4058a926b5d16dda23e3de759dca0dc3e  src/main/resources/static/app.js
c3c5a7fd9151664a77572d2276e269c81a311054a73e2f7cdd84511385b70f66  src/main/resources/static/styles.css
edf3b8d766ec3ed8860b2e995157e8e86870d2f41d428a0520007a2150a389b2  src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt
f1b6d2dd8a8a663a97928ecca698ce54b784682d8218e24b207b39c29afbe78f  src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt
bdbe3cad92d4b42e77ad0b9032a7bcf77cd817ef40fedf440d260b511674c0f0  src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt
85abf2120871f2090b3a7bc1f68f3011bf5935a56dc394bb212ec6bce66528c7  src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt
354594b8cb1a6b49ea35a6205099cce69e16009547a1e5ad5458a8f84e752baa  src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt
081460b4b13f21f1a884a8545f8cebe30751109031a4177159100503502f7aaa  src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt
aa86eaec1f97221aaa3caf007379511682e84c3b2e4b9c44516b2a00fb4a93b5  src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt
```
