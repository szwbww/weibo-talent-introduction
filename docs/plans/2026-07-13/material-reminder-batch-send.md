# 材料提醒整合进统一批量发送开发计划

## 需求描述

专家联系页只保留蓝色「批量发送」入口。点击后继续使用现有任务弹窗，在同一套配置界面内切换「介绍邮件」与「材料提醒邮件」；两类任务分别保存模板、定时、限额、节流、邮箱服务商和学科分类配置，并由同一批量发送控制面板启动、暂停和手动执行。

材料提醒任务固定读取 APPLICATION 层带精确标签 `承诺回复材料` 的已建联专家；邮箱服务商、学科分类、模板、待发送数量和运行状态随发送类型联动。提醒邮件发送成功后保留标签、保持专家业务状态，并排除已经成功发送过 `MATERIAL_REMINDER` 的专家，避免定时任务因标签保留而每天重复发送。

必须保持：

- 介绍邮件继续使用现有 CANDIDATE 未联系专家 + NEW 失败重试两条目标来源、现有 `INTRODUCTION` 组装器和反重复机制。
- 介绍邮件现有 `batchSend.*` KV 配置原值继续生效，不迁移、不重置。
- 两类任务共享现有发件账号额度、自检、轮次、节流和单任务互斥约束；不得并发抢占同一批账号。
- 材料提醒成功、失败、取消、暂停均不得新增、移除或替换任何 ES 标签。
- 材料提醒发送后 `currentStatus`、`operatorStatus`、索引层级、自动回复和人工接管状态不变；允许现有手动模板发送链路更新 `lastMailAt`。
- 已上线的 `POST /api/expert-contacts/batch-mail` 及现有无类型参数的介绍邮件 config/control/status API 保留兼容，但不再把 `/batch-mail` 作为专家联系页工具栏入口。
- `Material Reminder Email` 继续使用已上线的 `mail_compose_template` / V71 内容，不新增模板迁移。

明确不在本计划范围：

- 自动识别承诺内容、自动打 `承诺回复材料` 标签。
- 自动删除标签、自动判断材料是否收到。
- 多轮催办、可配置冷却天数、第二封/第三封提醒。
- 新建任务表、修改 ES mapping、修改 `expert_contact` 或 `mail_record` schema。
- 重构 `ManualInitialOutreachService` 为全新的通用工作流框架。
- 删除兼容批量邮件 API 或其他页面的手动模板发送能力。

## 修正记录

| 编号 | 日期 | 修正 |
|---|---|---|
| R1 | 2026-07-13 | `dailyCap` 明确为发送类型的自然日成功发送总量，必须从持久化成功记录初始化并跨 scheduler 重触发、手动执行与进程重启累计；失败、取消和未送达不计入。 |

## 关键不变量

### Invariant I-1：前端只有一个批量发送入口

- Rule：专家联系页保留 `#bulkOutreachBtn`，删除 `#batchTagMailBtn`；统一弹窗标题为 `批量发送邮件`。不得新增材料提醒按钮、页面或第二个弹窗。
- Applies to：`index.html` 工具栏与 `#taskProgressModal`；`app.js:handleBulkOutreach/openTaskLaunchModal`。
- Violation consequence：再次形成两套入口、两套配置和不同的收件人语义。
- 来源：original。

### Invariant I-2：两类配置独立，介绍邮件旧配置原位兼容

- Rule：`INTRODUCTION` 继续读写现有 `batchSend.*` key；`MATERIAL_REMINDER` 只读写 `batchSend.materialReminder.*` key。两类配置均独立保存 `autoEnabled/cron/dailyCap/roundSize/perMailIntervalMs/perRoundIntervalMs/selfCheckTtlMinutes/emailDomain/discipline/templateId/runtimeStatus/runtimeMode/pauseReason`。禁止把一种类型的值覆盖到另一种类型。
- Applies to：`BatchSendSettingService.getConfig/updateConfig/setAutoEnabled/getRuntimeStatus/setRuntimeStatus`、配置 API、前端类型切换。
- Violation consequence：切换发送类型会改坏线上介绍邮件定时配置，或两个定时器互相覆盖。
- 来源：K-batch-send-setting-kv。

### Invariant I-3：发送类型决定完整目标范围

- Rule：`INTRODUCTION` 仍使用 CANDIDATE + `notContactedWithEmailFilters(emailDomain, discipline)`，并保留同时符合 emailDomain/discipline 的 MySQL NEW 重试联系人；`MATERIAL_REMINDER` 固定使用 APPLICATION + `tag=承诺回复材料` + 有邮箱 + 当前类型配置中的 `emailDomain/discipline`，再通过 `orcidId` 连接已有 `expert_contact`。前端页面当前层级、列表 checkbox 和普通列表筛选不得改变定时任务范围。
- Applies to：`ManualInitialOutreachService.countPending/runScheduledBatch/runMaterialReminderBatch`、前端收件范围说明和 provider 聚合请求。
- Violation consequence：提醒发给未承诺专家，或介绍邮件漏掉重试联系人。（来源：K-batch-send-filter-retry-parity、K-es-tag-to-mail-cross-store-join）
- 来源：original + K-batch-send-filter-retry-parity + K-es-tag-to-mail-cross-store-join。

### Invariant I-4：邮箱服务商、学科、数量随类型联动

- Rule：切换发送类型必须重新加载该类型已保存的 `emailDomain/discipline`、匹配类型的 provider 选项和准确待发送数量。介绍邮件 provider 选项使用 `CANDIDATE + operatorStatus=NOT_CONTACTED`；材料提醒 provider 选项使用 `APPLICATION + tag=承诺回复材料`。provider 或 discipline 改变后保存当前类型配置，再刷新当前类型数量；不得复用另一类型残留值或异步响应。
- Applies to：`app.js` 类型切换、provider 加载、配置填充、pending-count 请求。
- Violation consequence：界面显示提醒任务，实际却按介绍邮件的服务商/学科条件发送。
- 来源：K-agg-filter-source-of-truth、K-filter-option-scope-parity。

### Invariant I-5：标签只读且发送后保留

- Rule：材料提醒路径仅以 ES `tags` 的精确 term `承诺回复材料` 选目标；任何发送结果都不得调用 `/api/experts/tags/add`、`/api/experts/tags/remove` 或 `ExpertIndexWriterService.addTag/removeTag`，也不得写入新的标签替代值。
- Applies to：`runMaterialReminderBatch`、控制服务、前端完成/失败刷新路径。
- Violation consequence：专家从运营筛选中消失，后续人工跟进断链。
- 来源：original。

### Invariant I-6：材料提醒成功记录形成反重复闸门

- Rule：构建材料提醒快照和每封发送前都必须排除已有 `direction=OUTBOUND AND mailType=MATERIAL_REMINDER AND sendStatus=SENT` 的 `expert_contact`。失败记录不得永久排除，允许下一次定时重试。目标总数超过 10000 时必须在发送第一封前整体拒绝，不得部分发送。
- Applies to：`countPending(MATERIAL_REMINDER)`、`runMaterialReminderBatch`、`mail_record` 读取。
- Violation consequence：标签保留导致每天向同一专家重复催办，或超上限时产生不可预测的部分发送。
- 来源：original。

### Invariant I-7：模板必须由发送类型做后端闸门

- Rule：`INTRODUCTION` 的显式 `templateId` 只能指向 enabled 且 `mailType=INTRODUCTION` 的 compose template，允许 `null` 继续使用默认 INTRODUCTION；`MATERIAL_REMINDER` 必须指向 enabled 且 `mailType=MATERIAL_REMINDER` 的 compose template，不允许 `null`。配置写入和任务启动均需校验，禁止只靠前端名称、ID 或 option 顺序判断。
- Applies to：`BatchSendConfigController.updateConfig`、`BatchSendControlService.startAuto/startManual/runManualOnce`、`app.js:fillBatchSendTemplateSelector/readBatchSendConfigForm`。
- Violation consequence：直接 API 或旧配置可让提醒任务发送介绍邮件，或反向污染介绍任务。
- 来源：K-batch-send-template-type-gate、K-introduction-compose-hardcode。

### Invariant I-8：两类定时器独立，执行流全局互斥

- Rule：INTRODUCTION 与 MATERIAL_REMINDER 可分别启停、分别计算 cron；启停或 cron 变化均触发 scheduler 重建对应 future。实际执行继续共享 `TaskProgressStore` 的 `MANUAL_INITIAL_OUTREACH` 互斥键和单线程 executor，任一类型运行时另一类型必须返回 409，不得排队后静默执行。
- Applies to：`BatchSendScheduler`、`BatchSendControlService.launchExecution`、类型化 start/manual/pause/status API。
- Violation consequence：两类任务同时使用账号、额度和进度，造成超额或状态串线。
- 来源：K-dual-outreach-paths。

### Invariant I-9：限额、轮次、自检和节流语义一致

- Rule：材料提醒必须复用所选类型的 `dailyCap/roundSize/perMailIntervalMs/perRoundIntervalMs/selfCheckTtlMinutes`、账号 self-check、剩余额度和 `AccountRateLimiter`；手动执行一轮仍只跑一轮，定时执行可跑到快照耗尽或达到上限。
- Applies to：`runMaterialReminderBatch`、`BatchSendControlService`、进度详情。
- Violation consequence：提醒任务绕过现有限流保护，影响账号信誉和介绍邮件额度。
- 来源：K-dual-outreach-paths、K-self-check-ttl-type-scope。

### Invariant I-10：提醒邮件不推进业务状态

- Rule：材料提醒调用现有 `ManualExpertMailService.sendManualMail` 的 compose-template 路径，写入 `OUTBOUND/MATERIAL_REMINDER`；`nextStatus()` 保持原 `currentStatus`。成功可更新 `lastMailAt` 和账号发送计数，但不得改变 `operatorStatus`、索引层级、自动回复/人工接管状态或标签。
- Applies to：`runMaterialReminderBatch`、`ManualExpertMailService` 既有行为、`mail_record` 与 `expert_contact`。
- Violation consequence：催材料错误推进专家漏斗或破坏回复处理模式。
- 来源：original。

### Invariant I-11：类型切换异步结果不得串线

- Rule：前端每次类型切换生成递增 request token；config/status/provider/count/template preview 的迟到响应仅在 token 和当前 `batchSendType` 同时匹配时落 DOM。切换期间禁用保存、开始、手动执行按钮；完整加载后再恢复。
- Applies to：`app.js` 统一弹窗 preload、类型 change、预览和轮询。
- Violation consequence：界面显示材料提醒，但提交的是介绍邮件配置或反之。
- 来源：original。

## 样式契约

### S-1：工具栏唯一入口

- 复用：`#bulkOutreachBtn.button.primary`，基线 `src/main/resources/static/index.html:580`；`.button` 规则 `styles.css:509-543`；`.button.primary` 规则 `styles.css:545-557`。
- 新增：无新 class、无新 CSS。
- DOM 结构必须为：

```html
<button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>
<button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>
```

- 禁止项：保留 `#batchTagMailBtn`；新增提醒按钮；改变 `#bulkOutreachBtn` 文案、颜色或位置；修改 `.button*` 规则。

### S-2：发送任务与收件范围分组

- 复用：
  - `.batch-send-config-panel`：`styles.css:5032-5043`。
  - `.batch-send-config-panel-hint`：`styles.css:5044-5049`。
  - `.bsc-group/.bsc-group-legend`：`styles.css:5069-5083`。
  - `.bsc-field/.bsc-field-label`：`styles.css:5107-5116`。
  - `.bsc-input/.bsc-select`：`styles.css:5117-5140`。
  - `.bsc-row-2col`：`styles.css:5158-5162`。
  - `.badge`：`styles.css:751-764`；`.muted`：`styles.css:2682-2684`，只复用、不修改规则块。
- 新增：无新 class、无新 CSS。
- DOM 结构必须按下列顺序插入 `#batchSendConfigPanel`，并把 provider/discipline 从「节流控制」迁出：

```html
<fieldset class="bsc-group">
    <legend class="bsc-group-legend">发送任务</legend>
    <div class="bsc-field">
        <span class="bsc-field-label">发送类型</span>
        <select id="batchSendType" class="bsc-input bsc-select">
            <option value="INTRODUCTION">介绍邮件</option>
            <option value="MATERIAL_REMINDER">材料提醒邮件</option>
        </select>
    </div>
</fieldset>

<fieldset class="bsc-group">
    <legend class="bsc-group-legend">收件范围</legend>
    <div class="bsc-row-2col">
        <div class="bsc-field">
            <span class="bsc-field-label">邮箱服务商</span>
            <select id="batchSendEmailDomain" class="bsc-input bsc-select"></select>
        </div>
        <div class="bsc-field">
            <span class="bsc-field-label">学科分类</span>
            <select id="batchSendDiscipline" class="bsc-input bsc-select">
                <option value="">全部</option>
                <option value="STEM">仅理工科</option>
                <option value="HUMANITIES">仅文社科</option>
            </select>
        </div>
    </div>
    <p id="batchSendRecipientSummary" class="batch-send-config-panel-hint"></p>
</fieldset>
```

- 固定联动文案：
  - INTRODUCTION：`范围：CANDIDATE 未联系专家及失败待补发专家`
  - MATERIAL_REMINDER：`范围：APPLICATION 层“承诺回复材料”标签专家；发送成功后保留标签`
- 禁止项：把 provider/discipline 留在「节流控制」；inline style；新增卡片、tab、胶囊按钮或自造网格 class。

### S-3：模板、调度、限额、节流与控制栏保持现有骨架

- 复用：
  - `.batch-send-template-preview`：`styles.css:5051-5066`。
  - `.bsc-row-3col/.bsc-input-wrap/.bsc-input-suffix`：`styles.css:5141-5167`。
  - `.batch-send-config-actions`：`styles.css:5170-5174`。
  - `.batch-send-control-bar` 及子元素：`styles.css:5177-5260`。
- 新增：无新 class、无新 CSS。
- DOM 修改：`#batchSendTemplateId`、`#batchSendTemplatePreview`、定时/限额/节流所有原 ID 保持；仅给模板 label 增加 `id="batchSendTemplateLabel"`，默认文字 `介绍邮件模板`；保存按钮文字改为 `保存当前类型配置`。
- 状态显示：既有「模式/状态」badge 前增加当前发送类型的文字内容，复用现有 `.batch-send-badge-label` 和 `.badge`，不得新造颜色。
- 禁止项：修改 `styles.css`；增加 inline style；改变弹窗最大宽度、section 间距、输入框高度、圆角、阴影或响应式断点。

## 现状审计

### `batch_send_setting` KV 表

- Schema：`V27__create_batch_send_setting.sql` 创建 `setting_key VARCHAR(64) UNIQUE NOT NULL`、`setting_value VARCHAR(255) NOT NULL`；不是列式配置表，因此新增 MATERIAL_REMINDER key 不需要 DDL。（来源：K-batch-send-setting-kv）
- Write paths：
  1. `BatchSendSettingService.upsert()` — 所有批量配置与运行状态的唯一代码写入点。
  2. `V27` — 初始化 `batchSend.*` 介绍邮件默认值。
  3. `V50` — 同表写入全局自动回复设置，与本计划 key 空间无关。
- Read paths：
  1. `getConfig()` — 控制服务、发送服务、scheduler、配置 API。
  2. `getRuntimeStatus()` — 控制按钮、暂停/恢复、重启恢复、状态 API。
  3. `BatchSendScheduler.DynamicCronTrigger` — 当前只读取单个介绍邮件 cron。
- Interaction points：配置 API 写入 → scheduler 重排 future；控制服务读同一类型配置 → 发送服务构造快照。必须通过 sendType 选择同一 key namespace。

### ES APPLICATION `tags` 与专家筛选

- Mapping：`tags` 是专家文档数组字段；`ExpertSearchService.buildExpertFilters()` 使用 `term tags=<exact>`，emailDomain 使用 wildcard，discipline 使用学科 filter。本计划不改 mapping。
- Write paths（完整 grep）：
  1. `ExpertIndexController.addTag/removeTag` → `ExpertIndexWriterService.addTag/removeTag`。
  2. `ExpertRevalidationService.promoteRawToCandidate` — 晋升时复制并补 `auto_promoted`，不涉及 `承诺回复材料`。
- Read paths：
  1. `ExpertSearchService.searchExperts()` — 本计划材料提醒快照的权威 ES 查询。
  2. `aggregateEmailDomains()` → `/api/experts/email-providers` — 前端 provider 选项。
  3. 专家列表与标签聚合 — 继续供运营筛选。
- Interaction points：APPLICATION `tag=承诺回复材料` → `orcidId` → `ExpertContactRepository.findByOrcidIdIn()` → reminder send；不得把标签条件改为 MySQL 字段。（来源：K-es-tag-to-mail-cross-store-join、K-filter-option-scope-parity）

### `expert_contact` 与 `mail_record`

- Schema：本计划不新增字段。`expert_contact.orcid_id` 是 ES/MySQL 桥；`mail_record` 已有 `expert_contact_id/direction/mail_type/send_status/sent_at/triggered_by`。
- `expert_contact` relevant write paths：
  1. `ManualExpertMailService.sendManualMail()` 经 `ConversationStateService.transition()` 更新 `lastMailAt`；对 MATERIAL_REMINDER 返回原状态。
  2. `ManualOutreachTxHelper.recordSuccess()` 仅介绍邮件更新 `INTRO_SENT/CONTACTED`，提醒路径不得调用。
  3. 专家状态、层级、人工接管服务继续独立维护其他字段。
- `mail_record` write paths（完整 grep）：
  1. `ManualExpertMailService.sendManualMail()` — 本计划提醒邮件写入点。
  2. `ManualOutreachTxHelper` — 介绍邮件成功/失败。
  3. `MeetingScheduleService` — 会议邮件。
  4. `AutoMailReplyService` — 入站与系统自动外发。
  5. `PendingMailOperationService` — 人工 QA/富文本外发。
- Read paths：
  1. `MailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc()` — reminder SENT 反重复检查。
  2. 专家详情、邮箱、监控统计读取并显示新增记录。
  3. `ManualInitialOutreachService.hasSentIntroduction()` — 介绍邮件现有反重复，保持不变。
- Interaction points：ES 目标快照 → contact join → 发送前 mail_record 双检 → `ManualExpertMailService` 写 SENT/FAILED → 下一次快照只排除 SENT；标签始终不写。

### `mail_compose_template`

- Schema：V61/V62 已统一 compose template；V71 已更新 enabled `MATERIAL_REMINDER` 的主题、正文和唯一 CUSTOM_TEXT block。本计划无迁移。
- Write paths：`MailComposeTemplateService.create/update/setEnabled/delete` 与既有 Flyway 迁移。
- Read paths：
  1. `/api/compose-templates` — 弹窗加载模板列表。
  2. `/api/compose-templates/{id}/preview` — 前端预览。
  3. `ManualExpertMailService.composeComposeTemplate()` — MATERIAL_REMINDER 实际渲染。
  4. `IntroductionMailComposer.compose()` — INTRODUCTION 默认/选定模板。（来源：K-introduction-compose-hardcode）
- Interaction points：配置保存时 mailType 闸门 → 类型专属 templateId → preview/render 同一 ID。

### 调度、控制和进度

- `BatchSendScheduler` 当前只有一个 `ScheduledFuture` 和一个动态 cron；`BatchSendCronChangedEvent` 仅含 old/new cron。
- `BatchSendControlService` 当前所有控制方法无 sendType，运行状态和任务类型固定 `MANUAL_INITIAL_OUTREACH`；共享单线程 `manualOutreachExecutor`。
- `BatchSendConfigController` 当前只提供无类型的 `/api/mail/batch-send/config`；`MailAutomationController` 提供现有介绍邮件 pending/control/status API。本计划保留后者不变，在前者下新增不冲突的 `/types/{sendType}/...` 统一 API。
- `TaskProgressStore.tryStartWithToken("MANUAL_INITIAL_OUTREACH", ...)` 已提供原子互斥；计划继续使用同一 key，让两类任务不能并发。
- `ManualInitialOutreachService.runScheduledBatch()` 已完整实现账号 gate、额度、轮次、节流、暂停和进度；当前只组装 INTRODUCTION。
- `SenderAccountSelfCheckService.checkSendable(account)` 当前固定读取无参 INTRODUCTION config 的 TTL；类型化提醒路径必须显式传 MATERIAL_REMINDER TTL。（来源：K-self-check-ttl-type-scope）
- Interaction points：类型专属 scheduler future → 类型参数控制 API → 同一进度互斥 → 根据类型 dispatch 到介绍或提醒执行方法。

### 前端样式盘点

- 改动前工具栏：`index.html:580` 蓝色 `#bulkOutreachBtn`；`index.html:581` 白色 `#batchTagMailBtn`。
- 改动前批量配置：`index.html:996-1101`；模板、定时、限额、节流分组，provider/discipline 错放在节流组尾部。
- 旧材料提醒入口：`app.js:3772-4024` 的筛选摘要、分页收集、共用 actionDialog 和同步 `/batch-mail`；这些函数无其他调用点，可整段删除，兼容 API 保留。
- 现有统一任务注册：`taskLaunchConfigs.MANUAL_INITIAL_OUTREACH`、`handleBulkOutreach()`、`openTaskLaunchModal()`、`BatchSendControlBar`。（来源：K-task-launch-config-registration）
- 可复用 class、token、DOM 基线均已逐项写入 S-1～S-3；本计划不修改 `styles.css`。

## 实现方案

### Task 1：把批量配置扩展为类型命名空间

- Governing invariants：I-2、I-7、I-8。
- Files：
  - `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt`
- 实施：
  1. 在 `BatchSendSettingService.kt` 定义 `BatchSendType { INTRODUCTION, MATERIAL_REMINDER }`；保留无参 `getConfig()/setAutoEnabled()/getRuntimeStatus()/setRuntimeStatus()` 作为 INTRODUCTION 兼容入口，新增显式 sendType overload。
  2. INTRODUCTION key 原样使用 `batchSend.*`；MATERIAL_REMINDER 通过统一 key resolver 使用 `batchSend.materialReminder.*`，禁止复制散落常量。
  3. reminder 默认值固定为：`autoEnabled=false`、`cron="0 0 8 * * ?"`、`dailyCap=60`、`roundSize=30`、`perMailIntervalMs=3000`、`perRoundIntervalMs=120000`、`selfCheckTtlMinutes=30`、空 provider/discipline/templateId、`runtimeStatus=IDLE`。
  4. `BatchSendConfig` 响应增加 `sendType`；`BatchSendConfigUpdateRequest` 保持原字段，类型化接口的 sendType 由 path variable 决定。
  5. 保留现有 `GET/PUT /api/mail/batch-send/config` 为 INTRODUCTION 兼容接口；新增 `GET/PUT /api/mail/batch-send/types/{sendType}/config`。两条 PUT 在任何 upsert 前加载模板并执行 I-7：提醒必须配置 enabled MATERIAL_REMINDER，介绍显式模板必须为 enabled INTRODUCTION。
  6. `updateConfig` 在 cron 或 autoEnabled 变化时发布现有 `BatchSendCronChangedEvent`；`setAutoEnabled` 状态变化也发布事件，确保 reminder 首次启用即注册 future。
  7. 不新增 migration；缺少 reminder key 时始终走上述默认值。

### Task 2：类型化控制 API 与独立 scheduler future

- Governing invariants：I-2、I-7、I-8、I-9、I-11。
- Files：
  - `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt`
- 实施：
  1. 控制服务 public 方法增加默认 INTRODUCTION 的 sendType 参数；状态读写始终带同一 sendType。
  2. `launchExecution` 继续只对 `TASK_TYPE=MANUAL_INITIAL_OUTREACH` 调用 `tryStartWithToken`，并在 progress details、task description、status view 中记录 sendType；INTRODUCTION dispatch 原 `runScheduledBatch`，REMINDER dispatch 新 `runMaterialReminderBatch`。
  3. 启动前再次校验 I-7，避免模板保存后被禁用/改类型仍继续发送。
  4. `pause/resumeSchedule/pauseSchedule/getStatus/restartRecovery` 按 sendType 操作独立 runtime key；getStatus 只合并 progress 中 sendType 与请求类型相同的数据，并返回共享执行中的 `activeSendType`，供前端锁定类型选择器。
  5. scheduler 用 `Map<BatchSendType, ScheduledFuture<*>>` 管理 future：INTRODUCTION 保持当前常驻动态 trigger；MATERIAL_REMINDER 仅在 autoEnabled=true 时注册，禁用时取消。事件触发时重算两者，取消使用 `cancel(false)`。
  6. 两个 runnable 分别读取对应 config 并调用 `startAuto(sendType)`；共享互斥冲突返回 409，不改变另一类型状态，也不把任务放入 executor 队列。
  7. 在 `BatchSendConfigController` 增加 `/api/mail/batch-send/types/{sendType}/pending-count|start|pause|manual|start-auto|resume-schedule|pause-schedule|status`；前端统一使用这些路径。`MailAutomationController` 现有 INTRODUCTION API 不改文件、不改路径、不改响应。

### Task 3：实现材料提醒的定时目标快照和发送循环

- Governing invariants：I-3、I-5、I-6、I-7、I-8、I-9、I-10。
- Files：
  - `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountSelfCheckService.kt`
- 实施：
  1. 注入 `ManualExpertMailService`；保留介绍邮件组装、状态、反重复、轮次和调用入口不变；仅把 `emailDomain` 补入 NEW retry 目标过滤，使重试路径与 ES 新目标路径遵守同一配置。
  2. `countPending(sendType)` 对 INTRODUCTION 调用原逻辑；MATERIAL_REMINDER 调用共享 `buildMaterialReminderSnapshot(config)`，返回 sendable 数量和固定 scope 描述。
  3. reminder snapshot 固定查询 `ExpertIndexLevel.APPLICATION`、精确 tag `承诺回复材料`、`hasField=email`、config emailDomain/discipline；第一页读取 totalHits，若 >10000 立即抛错，随后按 1000 分页完整拉取后才允许发送。
  4. 规范化 ORCID、批量 `findByOrcidIdIn`、按 contactId 去重；排除无 contact、空邮箱、抑制邮箱以及已有 SENT MATERIAL_REMINDER 的 contact。不得创建新 contact。
  5. 给 `SenderAccountSelfCheckService` 增加 `checkSendable(account, ttlMinutes)` overload；原 `checkSendable(account)` 继续读取 INTRODUCTION TTL 兼容旧调用。`runRoundGate` 显式传当前类型 config 的 TTL。`runMaterialReminderBatch(executionId, mode, oneRoundOnly)` 复用现有 round gate、账号分配、dailyCap、roundSize、per-mail/per-round interval、取消检查和 account stats；所有 config 来自 MATERIAL_REMINDER namespace。
  6. 每封发送前再次读取该 contact 的 mail records 做 I-6 双检；调用 `ManualExpertMailService.sendManualMail(contactId, COMPOSE_TEMPLATE/templateId/senderAccountCode)`。仅 `sendStatus=SENT` 计 success 并递增账号今日计数；其他状态计 failed，保留失败记录供下轮重试。
  7. 不调用 `ManualOutreachTxHelper`、`mail_send_attempt`、专家标签或索引状态写路径；progress details 固定包含 `sendType=MATERIAL_REMINDER`。
  8. oneRoundOnly、额度耗尽、无账号、取消、异常的 finalStatus/stopReason 与现有介绍邮件控制语义保持一致。

### Task 4：重构统一弹窗并实现完整联动

- Governing invariants：I-1、I-2、I-4、I-7、I-11；style contracts：S-1、S-2、S-3。
- Files：
  - `src/main/resources/static/index.html`
  - `src/main/resources/static/app.js`
- 实施：
  1. 删除 `#batchTagMailBtn`；删除仅服务该入口的 `buildContactFilterSummary/collectBatchMailContactIds/openBatchTagMailDialog/handleBatchTagMail` 和常量，不删除后端兼容 API。
  2. 按 S-2 给现有 `#batchSendConfigPanel` 增加「发送任务」「收件范围」，移动 provider/discipline；模板、调度、限额、节流、控制栏保持原 DOM ID。
  3. 弹窗标题和 task title 改为 `批量发送邮件`。默认类型：当前列表标签精确等于 `承诺回复材料` 时选 MATERIAL_REMINDER，否则选 INTRODUCTION。
  4. 新增当前 `batchSendType` 状态和 request token；类型变化并行加载 config/status/templates/provider/pending count，迟到响应按 I-11 丢弃。
  5. provider 请求：INTRODUCTION 使用 `level=CANDIDATE&operatorStatus=NOT_CONTACTED`；MATERIAL_REMINDER 使用 `level=APPLICATION&tag=承诺回复材料`。切换后恢复该类型已保存的 provider；保存值不在新 options 时增加一个 `当前配置（无匹配）` option，禁止静默改为空。
  6. discipline 下拉选项固定，value 从当前类型 config 回填；provider/discipline change 都保存当前类型配置并刷新当前类型 pending count。
  7. template selector 按 `template.mailType === batchSendType` 过滤；INTRODUCTION 保留 `默认 (INTRODUCTION)`，MATERIAL_REMINDER 不显示默认空项。预览继续使用现有 preview endpoint。
  8. 所有 config/status/control/pending 请求使用 `/api/mail/batch-send/types/{sendType}/...`；保存/开始/暂停/手动执行/轮询只操作当前类型。切换期间与请求失败时禁用控制按钮并显示明确错误；共享执行处于 RUNNING/CANCELLING 时，强制显示 `activeSendType` 并禁用发送类型下拉，直到执行结束。
  9. 保存按钮文案改为 `保存当前类型配置`；收件摘要显示 S-2 固定范围文案 + 实际待发送数量；进度汇总显示当前发送类型。
  10. `refreshBatchSendBanner` 分别查询两个类型；若任一异常暂停，文案必须带 `介绍邮件` 或 `材料提醒邮件` 前缀，避免运营处理错任务。

### Task 5：自动化测试与旧链路清理回归

- Governing invariants：I-1～I-11；style contracts：S-1～S-3。
- Files：
  - `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
  - `src/test/js/expertTagBatchFix.test.js`
- 实施：
  1. Kotlin 测试补齐新增 `ManualExpertMailService` mock，并覆盖：APPLICATION+精确 tag 查询、provider/discipline 传递、ORCID→contact join、无 contact 排除、SENT 反重复、FAILED 可重试、>10000 第一封前拒绝、状态/标签无写、one-round/daily cap/节流/cancel；同时断言 INTRODUCTION NEW retry 同时服从 emailDomain/discipline。
  2. 同一 Kotlin 文件增加 scoped test class 覆盖 type KV key 隔离、旧 INTRODUCTION key 兼容、reminder 默认值、模板类型闸门、两类 scheduler future 启停、共享 progress 互斥；避免新增第 11 个文件。
  3. JS 测试删除旧 actionDialog/collectBatchMailContactIds 断言，新增：工具栏唯一入口、S-2 DOM 顺序、类型默认、config/provider/discipline/template/count/status/control API 使用同一 `/types/{sendType}` path segment、迟到响应丢弃、保存值无匹配时保留、无新增 CSS/inline style。
  4. 现有 `BatchSendSettingServiceTest/BatchSendControlServiceTest/BatchSendSchedulerTest` 不改文件；通过默认 INTRODUCTION overload 保持其源码可编译且断言继续通过。

## 变更文件清单

| # | 文件 | 类型 | 变更 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt` | 修改 | sendType、KV namespace、独立默认值/运行状态 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 修改 | 类型化控制、模板二次闸门、共享互斥 dispatch |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt` | 修改 | 两类 future 与启停重排 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 | reminder 快照、反重复、限流发送循环 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` | 修改 | sendType 配置 API 与模板写入闸门 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountSelfCheckService.kt` | 修改 | 显式接收当前发送类型 TTL，保留旧 overload |
| 7 | `src/main/resources/static/index.html` | 修改 | 删除旧按钮，重排统一配置 DOM |
| 8 | `src/main/resources/static/app.js` | 修改 | 删除旧弹窗链路，类型化联动与控制 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 | reminder + type config/scheduler 集成测试 |
| 10 | `src/test/js/expertTagBatchFix.test.js` | 修改 | 统一入口、DOM、联动、竞态回归测试 |

文件数：10；子系统：批量发送后端 + 专家联系前端，共 2；共享 store 新字段：0。

## 验收标准

- I-1：`rg 'batchTagMailBtn|handleBatchTagMail|openBatchTagMailDialog|collectBatchMailContactIds' index.html app.js` 无结果；`#bulkOutreachBtn` 恰好一个，标题为 `批量发送邮件`。
- I-2：测试先写 intro config A、reminder config B，再分别读取，字段逐项保持 A/B；intro repository key 仍为 `batchSend.*`，reminder key 全为 `batchSend.materialReminder.*`。
- I-3：Kotlin 测试断言 INTRODUCTION 仍同时覆盖 ES 新目标与符合 emailDomain/discipline 的 NEW retry；REMINDER 仅查询 APPLICATION + `承诺回复材料` 并只发送已 join contact。
- I-4：JS 测试切换两次类型，断言 provider URL、discipline value、pending URL 和最终 DOM 均对应当前类型；旧响应晚返回不得覆盖。
- I-5：实现 diff 不含任何 tag add/remove 调用；发送前后测试 expert profile tags fixture 逐字相同。
- I-6：已有 SENT reminder 被排除；FAILED reminder 下轮仍在；同一 contact 快照后被并发补入 SENT 时发送前双检跳过；10001 命中发送调用次数为 0。
- I-7：配置 API 对 null reminder、disabled template、mailType 不匹配均返回 400；INTRODUCTION null 仍成功；任务启动时模板被禁用返回 409/400 且不创建 execution。
- I-8：两类 autoEnabled/cron 独立；启用 reminder 注册其 future，禁用只 cancel reminder；同时 start 第二类返回 409，executor 调用总数为 1；status 返回 activeSendType，运行中类型下拉不可切换。
- I-9：测试断言 reminder roundSize/dailyCap/两种 interval/self-check/cancel 均读取 reminder config；manual endpoint 只处理一轮。
- I-10：发送结果为 `OUTBOUND/MATERIAL_REMINDER`；currentStatus/operatorStatus/indexLevel/autoReply/manualHandoff 不变；标签无写。
- I-11：JS 测试模拟 config、provider、preview、status 乱序返回，最终 select、摘要、模板、按钮和提交 query 参数全部属于最后选择类型。
- S-1：`index.html` 工具栏 DOM 与 S-1 逐字结构一致；无第二批量发送按钮。
- S-2：新增 DOM 仅使用契约 class；provider/discipline 只出现在收件范围；`styles.css` diff 为空；计划涉及节点无新增 inline style。
- S-3：原模板/定时/限额/节流/控制栏 ID 全部存在且各一次；保存按钮和类型 badge 文案符合契约；640px 断点仍由原规则生效。
- 兼容回归：现有 `/api/mail/batch-send/config`、`/manual-outreach/pending-count`、control/status API 路径和 INTRODUCTION 语义不变；原 Kotlin/JS 测试全部通过。
- 验证命令：

```bash
node --check src/main/resources/static/app.js
node --test src/test/js/expertTagBatchFix.test.js
mvn -Dtest=ManualInitialOutreachServiceTest,BatchSendSettingServiceTest,BatchSendControlServiceTest,BatchSendSchedulerTest test
mvn test
```

## 人工验收清单

### A-1：工具栏只有统一入口

- 前置条件：部署本计划版本，登录专家联系页。
- 操作步骤：查看工具栏；点击蓝色 `批量发送`。
- 预期结果：不存在白色 `批量发送邮件` 或提醒专用按钮；打开标题为 `批量发送邮件` 的现有任务弹窗。
- 覆盖：I-1、S-1。

### A-2：当前标签自动选择提醒类型

- 前置条件：专家列表标签筛选选择 `承诺回复材料`。
- 操作步骤：点击 `批量发送`。
- 预期结果：发送类型默认 `材料提醒邮件`；收件范围显示 `APPLICATION 层“承诺回复材料”标签专家；发送成功后保留标签`。
- 覆盖：I-1、I-3、S-2。

### A-3：无标签时保持介绍邮件默认行为

- 前置条件：清空专家标签筛选。
- 操作步骤：点击 `批量发送`。
- 预期结果：发送类型默认 `介绍邮件`；介绍邮件原模板、定时、限额、节流配置值与升级前一致。
- 覆盖：I-2、I-11、must-not-change 介绍邮件配置。

### A-4：服务商和学科随类型切换

- 前置条件：INTRODUCTION 保存 provider=`gmail.com`、discipline=`STEM`；MATERIAL_REMINDER 保存 provider=全部、discipline=`HUMANITIES`。
- 操作步骤：在弹窗中依次切换 `介绍邮件 → 材料提醒邮件 → 介绍邮件`。
- 预期结果：每次切换后 provider、discipline、候选数量和范围文案恢复该类型值；不存在上一类型残留。
- 覆盖：I-2、I-4、I-11、S-2。

### A-5：模板随类型切换并做预览

- 前置条件：enabled INTRODUCTION 与 MATERIAL_REMINDER 模板各至少一个。
- 操作步骤：切换两种发送类型并展开模板下拉。
- 预期结果：介绍类型只显示 INTRODUCTION 和默认项；提醒类型只显示 MATERIAL_REMINDER 且无默认空项；提醒预览主题为 `Gentle Follow-up on the Requested Materials`。
- 覆盖：I-7、S-3。

### A-6：两类定时配置独立保存

- 前置条件：无运行中批量任务。
- 操作步骤：介绍邮件设每天 09:00 并保存；提醒邮件设每天 08:00 并保存；关闭后重新打开逐类查看。
- 预期结果：两组时间分别保持 09:00 和 08:00；修改提醒不改变介绍邮件任何字段。
- 覆盖：I-2、I-8。

### A-7：提醒任务只发送正确目标

- 前置条件：APPLICATION 中两位已建联专家有 `承诺回复材料`，一位无该标签；另有一位带标签但无 expert_contact。
- 操作步骤：提醒类型选择全部服务商/全部学科；点击手动执行一轮。
- 预期结果：待发送数为 2；只向两位有标签且已建联专家发送；无标签和无 contact 专家不产生 mail_record。
- 覆盖：I-3、I-5，ES→contact interaction point。

### A-8：成功后保留标签和状态

- 前置条件：A-7 两封均可正常送达；记录发送前 currentStatus/operatorStatus/indexLevel/autoReply 状态。
- 操作步骤：完成提醒发送；刷新列表并打开专家详情。
- 预期结果：两位仍有 `承诺回复材料`；上述状态值与发送前逐项相同；邮件历史新增 `OUTBOUND/MATERIAL_REMINDER/SENT`。
- 覆盖：I-5、I-10，send→mail_record/detail interaction point。

### A-9：下一次定时不重复提醒

- 前置条件：A-8 已成功；标签仍保留。
- 操作步骤：再次执行提醒任务或等待下一次 cron。
- 预期结果：待发送数为 0；不新增第二条 SENT MATERIAL_REMINDER，不再次投递。
- 覆盖：I-6。

### A-10：失败专家可在下轮重试

- 前置条件：一位带标签专家首次发送返回 FAILED。
- 操作步骤：查看失败记录；恢复发送条件；再次手动执行一轮。
- 预期结果：首次计失败且标签保留；第二次仍在待发送范围，成功后产生 SENT，后续不再发送。
- 覆盖：I-5、I-6、I-9。

### A-11：两类任务不能并发

- 前置条件：介绍邮件任务正在运行。
- 操作步骤：切换到提醒类型并点击手动执行一轮。
- 预期结果：页面提示已有批量任务运行，提醒任务未启动、无邮件发送；介绍任务继续按原进度运行。
- 覆盖：I-8、must-not-change 账号额度安全。

### A-12：暂停和状态按类型隔离

- 前置条件：介绍邮件定时已启用；提醒邮件定时已启用且当前空闲。
- 操作步骤：切到提醒类型点击暂停；再切到介绍类型查看状态。
- 预期结果：提醒显示已暂停；介绍仍显示定时中；页面 banner 明确写 `材料提醒邮件`，不误报介绍任务。
- 覆盖：I-2、I-8、I-11。

### A-13：介绍邮件完整回归

- 前置条件：存在未联系 CANDIDATE、NEW 失败重试联系人和可用发件账号。
- 操作步骤：介绍类型手动执行一轮。
- 预期结果：两类来源均参与且同时服从所选邮箱服务商/学科；邮件类型为 INTRODUCTION；成功专家进入 INTRO_SENT/CONTACTED；原反重复、额度和节流行为不变。
- 覆盖：I-3、I-9、must-not-change 介绍邮件链路。

### A-14：样式与响应式目测

- 前置条件：桌面宽度和 ≤640px 窄屏各打开一次弹窗。
- 操作步骤：检查分组顺序、输入框、按钮、滚动和窄屏排列。
- 预期结果：顺序为发送任务→收件范围→邮件模板→定时调度→发送限额→节流控制；桌面两/三列，窄屏单列；无重叠、横向滚动、新颜色或新卡片。
- 覆盖：S-1、S-2、S-3。

### A-15：旧 API 默认行为兼容

- 前置条件：记录升级前介绍邮件 config/status；准备一个测试 contact 和 enabled compose template。
- 操作步骤：分别调用不带 `sendType` 的 `/api/mail/batch-send/config`、`/api/mail/manual-outreach/pending-count`、`/api/mail/batch-send/status`；再调用既有 `/api/expert-contacts/batch-mail` 给测试 contact 发送模板邮件。
- 预期结果：前三个接口返回 INTRODUCTION 配置/数量/状态且与升级前一致；兼容 `/batch-mail` 仍能发送并返回 `total=1`，但专家联系页无该独立入口。
- 覆盖：I-1、I-2、must-not-change 兼容 API 与手动模板能力。

人工验收开始时，从本节导出 `docs/plans/2026-07-13/material-reminder-batch-send-acceptance.md`；此文件现在不创建。
