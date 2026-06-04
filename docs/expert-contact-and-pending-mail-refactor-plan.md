# Expert Contact and Pending Mail Refactor Plan

## 目标

重构「专家联系」和「待处理邮件」两个页面，把专家层级、专家业务状态、自动/人工回复模式、邮件处理、附件资料和操作日志拆清楚。

本计划只做计划设计，不修改业务代码。后续实现时，所有 SQL 迁移必须输出完整 SQL 文件内容，不输出局部 SQL。

## 当前基础

- 专家联系页面已经有三层入口：`RAW` 原始、`CANDIDATE` 筛选、`APPLICATION` 有效。
- 专家联系详情已经有自动回复/人工回复切换入口，后端已有：
  - `POST /api/expert-contacts/{contactId}/switch-to-manual`
  - `POST /api/expert-contacts/{contactId}/switch-to-auto`
- 当前状态枚举过多，`ConversationStatus` 包含会议、材料、视频、承诺书、提交等细分状态。
- 附件已会在自动收信流程中保存：
  - 文件存储配置：`talent-introduction.mail-attachment-storage.base-path`
  - 附件表：`mail_attachment`
  - 专家资料表：`expert_document`
- 待处理邮件页面实际查询 `inbound_mail_processing.process_status = 'MANUAL_REVIEW'`。
- 待处理邮件详情已经能展示原始正文和清洗正文，但操作只覆盖绑定专家、标记处理。
- 手动发送邮件能力已有 `ManualExpertMailService`，支持固定模板和 QA 规则邮件，但待处理邮件页没有完整整合。

## 业务口径

### 专家层级

专家层级继续保留三层：

| 层级 | 系统值 | 页面文案 | 含义 |
| --- | --- | --- | --- |
| 原始 | `RAW` | 原始 | 原始专家数据 |
| 筛选 | `CANDIDATE` | 筛选 | 已筛选、可触达专家 |
| 有效 | `APPLICATION` | 有效 | 有有效回复或已进入后续流程的专家 |

页面顶部的层级筛选必须保留，不改成状态筛选。

### 专家状态

页面对运营只展示 6 个精简状态：

| 页面状态 | 建议系统值 | 进入条件 |
| --- | --- | --- |
| 未联系 | `NOT_CONTACTED` | 没有 `expert_contact`，或 contact 仍为 `NEW` |
| 已联系 | `CONTACTED` | 已发介绍邮件，等待专家首次回复 |
| 已回复 | `REPLIED` | 专家已回复，但未明确进入材料/邀约/完成 |
| 已回复材料 | `MATERIALS_RECEIVED` | 专家发送附件或明确发送材料 |
| 已邀约 | `INVITED` | 已发送会议邀约或进入会议排期 |
| 已完成 | `COMPLETED` | 运营确认该专家流程完成 |

实现时不要直接把这 6 个页面状态硬塞进所有自动流转逻辑。建议新增一个运营视角字段：

```text
expert_contact.operator_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONTACTED'
```

原因：

- 原 `current_status` 仍承担自动回复、会议排期、材料处理等内部状态流转。
- 页面状态是运营筛选和手动标注维度，需要稳定、少量、易理解。
- 兼容旧数据时可以从 `current_status`、`lastMailAt`、`lastReplyAt`、`applicationIndexed` 映射初始化。

### 自动进入有效层规则

有效层自动晋级规则调整为：

1. 专家回复次数超过 2 次，自动进入有效层。
2. 专家邮件中包含附件并被识别为发送材料，自动进入有效层。
3. 手动在页面选择层级为「有效」时，进入有效层。
4. 已经在有效层的专家重复触发时只更新投影，不重复创建。

回复次数口径：

- 统计 `mail_record` 中该专家 `direction='INBOUND' AND mail_type='REPLY'` 的数量。
- 在保存本次 inbound mail 后统计，这样第 3 封回复会触发「超过 2 次」。
- 触发原因记录为 `REPLY_COUNT_GT_2`。

材料口径：

- 当前邮件附件非空，或 `MailAttachmentService.inferPrimaryIntentFromAttachments(...)` 返回 `CV_ATTACHED` / `DOCS_ATTACHED` / `PASSPORT_UPDATED`。
- 触发原因记录为 `MATERIAL_ATTACHED`。

## 数据库改造

### 1. 专家状态字段

新增 `operator_status`，用于页面精简状态。

建议默认回填：

| 条件 | operator_status |
| --- | --- |
| `last_mail_at IS NULL AND last_reply_at IS NULL` | `NOT_CONTACTED` |
| `last_mail_at IS NOT NULL AND last_reply_at IS NULL` | `CONTACTED` |
| `current_status IN ('MEETING_SCHEDULING','MEETING_SCHEDULED','MEETING_INVITATION_SENT','WAITING_MEETING_CONFIRMATION')` | `INVITED` |
| `current_status IN ('MATERIALS_PARTIAL','MATERIALS_RECEIVED')` | `MATERIALS_RECEIVED` |
| `last_reply_at IS NOT NULL` | `REPLIED` |

`COMPLETED` 只通过人工选择进入，不做历史自动回填，避免误判。

### 2. 操作日志表

新增统一操作日志表，覆盖专家联系页和待处理邮件页的所有人工操作。

建议表名：`operator_action_log`

核心字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `target_type` | `EXPERT_CONTACT` / `INBOUND_MAIL_PROCESSING` / `DOCUMENT` |
| `target_id` | 被操作对象 ID |
| `expert_contact_id` | 关联专家，可为空 |
| `inbound_processing_id` | 关联待处理邮件，可为空 |
| `action_type` | 操作类型 |
| `action_summary` | 中文摘要 |
| `before_value` | 操作前 JSON |
| `after_value` | 操作后 JSON |
| `operator_name` | 操作人 |
| `note` | 备注 |
| `created_at` | 操作时间 |

必须记录的操作：

- 变更专家状态。
- 变更专家层级。
- 切换自动回复/人工回复。
- 绑定未识别邮件到专家。
- 发送 QA 邮件。
- 人工富文本回复邮件。
- 标记待处理邮件已处理。
- 下载或预览资料文件可选记录；如果不记录，至少后端访问日志要有。

### 3. 待处理邮件处理状态

沿用 `inbound_mail_processing.process_status`：

- 列表只显示 `MANUAL_REVIEW`。
- 点击「标记已处理」后更新为 `PROCESSED`，`process_reason='MANUAL_RESOLVED'`，列表不再显示。
- 同时写 `operator_action_log`。

### 4. 附件存储配置

保留现有配置，并明确用途：

```yaml
talent-introduction:
  mail-attachment-storage:
    base-path: ${MAIL_ATTACHMENT_BASE_PATH:/opt/talent/uploads/mail-attachments}
```

文件保存路径继续按专家和邮件分目录：

```text
{base-path}/{expertContactId}/{mailRecordId}/{uuid}-{safeFileName}
```

后端所有下载/预览接口必须校验：

- `mail_attachment.id` 存在。
- 附件所属 `mail_record` 的 `expert_contact_id` 与请求 contact 一致。
- 最终文件路径必须在 `base-path` 下，防止路径穿越。

## 后端 API 计划

### 专家联系页 API

#### 1. 列表查询

扩展：

```http
GET /api/expert-contacts?campaignId=&operatorStatus=&needsAttention=
```

返回新增字段：

- `operatorStatus`
- `replyCount`
- `documentCount`
- `latestInboundAt`

原层级列表仍由 ES 查询入口负责：

```http
GET /api/experts?level=RAW|CANDIDATE|APPLICATION
```

页面组合显示时要保持：

- 顶部层级选择：原始/筛选/有效。
- 状态筛选：未联系/已联系/已回复/已回复材料/已邀约/已完成。

#### 2. 手动变更专家状态

新增：

```http
POST /api/expert-contacts/{contactId}/operator-status
```

请求：

```json
{
  "operatorStatus": "REPLIED",
  "operatorName": "operator",
  "note": "人工确认专家已回复"
}
```

行为：

- 更新 `expert_contact.operator_status`。
- 写 `operator_action_log`。
- 必要时同步 application index 的业务状态投影。

#### 3. 手动变更专家层级

建议把已有按钮 API 包一层统一接口：

```http
POST /api/expert-contacts/{contactId}/index-level
```

请求：

```json
{
  "targetLevel": "APPLICATION",
  "operatorName": "operator",
  "note": "人工加入有效层"
}
```

行为：

- `RAW -> CANDIDATE` 调用现有 `promoteToCandidate`。
- `CANDIDATE/RAW -> APPLICATION` 调用现有 `promoteToApplication`。
- `CANDIDATE/APPLICATION -> RAW` 调用现有 `demoteToRaw`。
- 写 `operator_action_log`，记录前后层级。

#### 4. 文件浏览、下载、在线浏览

新增：

```http
GET /api/expert-contacts/{contactId}/documents
GET /api/expert-contacts/{contactId}/attachments/{attachmentId}/download
GET /api/expert-contacts/{contactId}/attachments/{attachmentId}/preview
```

返回列表字段：

- `documentId`
- `attachmentId`
- `fileName`
- `contentType`
- `fileSize`
- `documentType`
- `documentStatus`
- `mailRecordId`
- `createdAt`
- `downloadUrl`
- `previewUrl`

在线浏览策略：

- 浏览器可直接预览的类型：`application/pdf`、`image/*`、`text/*`。
- Office 文件先支持下载；在线预览可后续接入转换服务。
- 未知类型只下载，不内嵌展示。

### 待处理邮件页 API

#### 1. 查看未识别邮件内容

沿用并强化：

```http
GET /api/mail/unmatched-inbound/{id}
```

必须返回：

- 原始正文 `body`
- 清洗正文 `cleanedBody`
- 主题、发件人、收件账号、时间
- 关联专家信息
- 推荐绑定专家列表
- 该邮件相关操作日志

#### 2. 待处理邮件内变更专家状态

新增或复用专家接口：

```http
POST /api/mail/unmatched-inbound/{id}/operator-status
```

行为：

- 要求该待处理邮件已有关联 `expertContactId`。
- 更新专家 `operator_status`。
- 写操作日志，`inbound_processing_id` 填当前邮件。

#### 3. 待处理邮件内变更专家层级

新增：

```http
POST /api/mail/unmatched-inbound/{id}/index-level
```

行为：

- 要求该待处理邮件已有关联 `expertContactId`。
- 调用统一层级变更服务。
- 写操作日志。

#### 4. QA 邮件回复下拉框

复用：

```http
GET /api/expert-contacts/mail-send-options
```

页面只筛出 `optionType='QA'` 的选项作为 QA 邮件回复下拉框。

发送：

```http
POST /api/mail/unmatched-inbound/{id}/qa-reply
```

请求：

```json
{
  "qaRuleId": 123,
  "senderAccountCode": "account-1",
  "operatorName": "operator"
}
```

行为：

- 发送 QA 邮件给关联专家邮箱。
- `sourceInboundId` 关联待处理邮件对应的 inbound mail record；如果只有 `inbound_mail_processing.id`，需要在发送记录里额外保存或通过 `source_inbound_id` 扩展支持。
- 写 `mail_record`，`triggered_by='OPERATOR'`。
- 写操作日志。

#### 5. 人工富文本回复

新增：

```http
POST /api/mail/unmatched-inbound/{id}/manual-rich-reply
```

请求：

```json
{
  "senderAccountCode": "account-1",
  "subject": "Re: Talent Program",
  "htmlBody": "<p>...</p>",
  "textBody": "...",
  "operatorName": "operator"
}
```

行为：

- 发送邮件。
- 保存 `mail_record.mail_type='MANUAL_RICH_REPLY'`。
- 保存 HTML 正文时，当前 `mail_record.body` 可先存 HTML；如果要同时保留 text/html，新增字段或约定 JSON 存储。
- 写操作日志，摘要为「人工回复了邮件」。

#### 6. 标记已处理

沿用：

```http
POST /api/mail/unmatched-inbound/{id}/mark-resolved
```

增强：

- 请求中要求 `operatorName`。
- 标记后列表不再显示。
- 写操作日志，摘要为「标记待处理邮件已处理」。

#### 7. 操作日志查询

新增：

```http
GET /api/operator-action-logs?expertContactId=&inboundProcessingId=&actionType=&operatorName=&start=&end=&pageSize=&pageOffset=
```

专家联系详情页按 `expertContactId` 展示日志。

待处理邮件详情页按 `inboundProcessingId` 展示日志。

可查询内容包括：

- 变更了专家层级。
- 变更了专家状态。
- 发送了 QA 邮件。
- 人工回复了邮件。
- 标记已处理。
- 绑定专家。

## 服务拆分计划

### OperatorActionLogService

职责：

- 统一写操作日志。
- 支持查询日志。
- 规范 `action_type` 和中文摘要。
- 保存 before/after JSON，避免每个业务服务自己拼字符串。

建议 action type：

| action_type | 摘要 |
| --- | --- |
| `CHANGE_OPERATOR_STATUS` | 变更专家状态 |
| `CHANGE_INDEX_LEVEL` | 变更专家层级 |
| `SWITCH_REPLY_MODE` | 切换自动/人工回复 |
| `BIND_INBOUND_MAIL` | 绑定待处理邮件 |
| `SEND_QA_REPLY` | 发送 QA 邮件 |
| `SEND_MANUAL_RICH_REPLY` | 人工回复邮件 |
| `MARK_INBOUND_RESOLVED` | 标记待处理邮件已处理 |

### ExpertOperatorStatusService

职责：

- 校验页面状态枚举。
- 手动变更 `operator_status`。
- 自动流转页面状态。
- 写操作日志。

自动状态建议：

- 介绍邮件发送成功：`CONTACTED`。
- 首次收到普通回复：`REPLIED`。
- 收到附件材料：`MATERIALS_RECEIVED`。
- 发送会议邀约：`INVITED`。
- 人工标记完成：`COMPLETED`。

### ExpertIndexLevelOperationService

职责：

- 统一封装专家层级变更。
- 替代页面直接调用多个 promote/demote API。
- 写操作日志。
- 触发 ES 同步。

### ExpertDocumentBrowseService

职责：

- 查询专家资料文件。
- 下载文件。
- 预览文件。
- 做路径安全校验。

### PendingMailOperationService

职责：

- 面向待处理邮件页聚合操作：
  - 变更专家状态。
  - 变更专家层级。
  - 发送 QA 邮件。
  - 发送人工富文本邮件。
  - 标记已处理。
- 每个操作都写 `operator_action_log`。

## 自动收信流程改造点

### 回复次数超过 2 次自动入有效层

在 `AutoMailReplyService.processSingle(...)` 中保存 inbound mail record 后：

1. 统计该专家 inbound reply 数量。
2. 如果数量 > 2 且 `applicationIndexed=false`，调用 promotion。
3. 更新 `currentIndexLevel='APPLICATION'`。
4. 写 promotion audit，触发原因为 `REPLY_COUNT_GT_2`。
5. 更新 `operator_status='REPLIED'`，如果有附件则改为 `MATERIALS_RECEIVED`。

### 发送材料自动入有效层

在 `mailAttachmentService.saveInboundAttachments(...)` 后：

1. 如果保存附件数量 > 0 且 `applicationIndexed=false`，调用 promotion。
2. 更新 `operator_status='MATERIALS_RECEIVED'`。
3. 待处理原因仍可进入人工审核，但层级已进入有效。

### QA 自动回复和人工回复的关系

- 自动回复/人工回复切换按钮保留。
- `autoReplyEnabled=false` 或 `currentStatus=MANUAL_HANDOFF` 时，自动流程只保存来信和附件，不自动发送。
- 待处理邮件页可以手动发送 QA 或人工富文本回复。
- 手动发送后不自动标记已处理，页面提供发送后继续操作或标记已处理，避免漏处理层级/状态。

## 前端重构计划

### 专家联系页面

保留：

- 顶部层级：原始 / 筛选 / 有效。
- 自动回复/人工回复切换按钮。

新增：

- 状态筛选下拉：未联系、已联系、已回复、已回复材料、已邀约、已完成。
- 专家详情头部增加「手动变更状态」下拉框。
- 专家详情头部把层级按钮改成「层级下拉框」：
  - 原始
  - 筛选
  - 有效
- 资料文件区域：
  - 显示文件名、类型、大小、上传时间、来源邮件。
  - 操作：下载、在线浏览。
  - 点击专家时自动加载该专家资料。
- 操作日志区域：
  - 时间倒序。
  - 展示操作人、操作类型、摘要、备注、前后值。

建议详情布局：

1. 顶部：专家姓名、邮箱、层级、状态、回复模式。
2. 操作条：状态下拉、层级下拉、自动/人工切换、发送邮件。
3. 左侧/上方：邮件时间线。
4. 资料文件区。
5. 状态/层级/人工处理元信息。
6. 操作日志。

### 待处理邮件页面

列表保留：

- 只显示未处理记录。
- 标记已处理按钮。

列表增强：

- 操作列增加「查看/处理」按钮，点击后展开详情。
- 对已关联专家的记录，显示当前页面状态和层级。

详情新增：

- 邮件内容查看：
  - 原始正文。
  - 清洗正文。
  - 邮件头信息。
- 关联专家区域：
  - 未匹配时可绑定专家。
  - 已匹配时可跳转专家详情。
- 手动变更专家状态下拉框。
- 手动变更专家层级下拉框。
- QA 邮件回复下拉框。
- 人工富文本回复框：
  - 主题输入。
  - 富文本正文。
  - 发送账号选择。
  - 发送按钮。
- 标记已处理按钮。
- 操作日志查询区。

富文本第一版建议：

- 使用浏览器 `contenteditable` 实现轻量富文本。
- 支持加粗、斜体、列表、链接、换行。
- 提交时取 `innerHTML`。
- 后端发送 HTML 邮件；如果邮件发送实现暂不支持 HTML，需要先扩展 `ComposedMail` 增加 `htmlBody` 或 `contentType`。

## 实施阶段

### Phase 1：数据模型和日志基础

1. 新增迁移：
   - `expert_contact.operator_status`
   - `operator_action_log`
2. 新增枚举/常量：
   - `OperatorStatus`
   - `OperatorActionType`
3. 新增 `OperatorActionLog` domain/repository/service。
4. 给专家详情、待处理邮件详情增加日志查询 API。
5. 单元测试：
   - 日志写入。
   - 日志查询过滤。
   - 历史状态回填 SQL 审查。

### Phase 2：专家状态和层级统一操作

1. 新增 `ExpertOperatorStatusService`。
2. 新增 `ExpertIndexLevelOperationService`。
3. 新增统一 API：
   - `/operator-status`
   - `/index-level`
4. 把现有切换自动/人工回复补写操作日志。
5. 单元测试：
   - 6 个页面状态校验。
   - 层级变更调用正确 ES 方法。
   - before/after 日志正确。

### Phase 3：自动流转规则

1. 在自动收信流程中统计 inbound reply 次数。
2. 实现回复次数 > 2 自动进有效层。
3. 实现收到材料附件自动进有效层。
4. 收到附件时设置 `operator_status='MATERIALS_RECEIVED'`。
5. 普通首次回复设置 `operator_status='REPLIED'`。
6. 发送会议邀约设置 `operator_status='INVITED'`。
7. 单元测试：
   - 第 1/2 封回复不因次数进有效层。
   - 第 3 封回复进有效层。
   - 附件来信进有效层。
   - 已有效层不重复 promotion。

### Phase 4：资料文件浏览

1. 新增 `ExpertDocumentBrowseService`。
2. 新增文档列表、下载、预览 API。
3. 实现路径安全校验。
4. 前端专家详情增加资料文件区。
5. 单元测试：
   - 只能下载该专家自己的附件。
   - 路径穿越被拒绝。
   - PDF/image/text 可预览，其他类型下载。

### Phase 5：待处理邮件操作面板

1. 扩展待处理邮件详情返回专家状态、层级、日志。
2. 新增待处理邮件页状态/层级变更 API。
3. 新增 QA 回复 API。
4. 新增人工富文本回复 API。
5. 增强标记已处理 API 写日志。
6. 前端详情面板加入：
   - 状态下拉。
   - 层级下拉。
   - QA 下拉。
   - 富文本回复框。
   - 日志列表。
7. 单元测试：
   - 未关联专家时不能变更状态/层级/发送邮件。
   - 发送 QA 邮件写 mail_record 和日志。
   - 人工富文本回复写 mail_record 和日志。
   - 标记已处理后不再出现在列表。

### Phase 6：前端专家联系页收口

1. 专家联系列表接入 `operatorStatus` 筛选。
2. 页面状态文案替换为 6 个精简状态。
3. 层级按钮改成下拉框。
4. 状态变更下拉接 API。
5. 自动/人工回复切换按钮保留并补充日志展示。
6. 验证：
   - 原始/筛选/有效三层还在。
   - 自动/人工切换仍可用。
   - 手动变更状态和层级后页面即时刷新。

## 验收清单

### 专家联系页面

- 能按原始/筛选/有效三层切换。
- 自动回复/人工回复切换按钮仍存在且可用。
- 状态只展示：未联系、已联系、已回复、已回复材料、已邀约、已完成。
- 可以手动下拉变更专家状态。
- 可以手动下拉变更专家层级。
- 专家回复第 3 次后自动进入有效层。
- 专家发送附件材料后自动进入有效层。
- 选择专家后能看到其上传文件。
- 文件支持下载。
- PDF/image/text 支持在线浏览。
- 操作日志能看到状态变更、层级变更、邮件发送等记录。

### 待处理邮件页面

- 点击待处理记录能查看未识别邮件内容。
- 可手动变更专家状态。
- 可手动变更专家层级。
- 可选择 QA 邮件并发送。
- 可用富文本框人工回复邮件并发送。
- 标记已处理后，该记录不再出现在待处理列表。
- 所有处理记录都进入操作日志。
- 可以按专家、待处理邮件、操作类型、操作人、时间查询日志。

### 后端验证

- `mvn test` 通过。
- 新迁移可在空库和已有库执行。
- 自动收信相关测试覆盖回复次数和附件晋级。
- 附件下载/预览接口有权限和路径安全测试。

### 前端验证

- `node --check src/main/resources/static/app.js` 通过。
- 手动浏览验证两个页面主要流程。
- 窄屏下详情面板和富文本框不溢出。

## 风险和注意事项

- `current_status` 当前被自动回复和会议流程使用，不建议直接删减到 6 个值；页面 6 状态用 `operator_status` 表达更稳。
- `sourceInboundId` 当前指向 `mail_record.id`，而待处理邮件页主键是 `inbound_mail_processing.id`。实现待处理邮件发送回复时，需要明确二者关联方式，避免日志和邮件来源串不上。
- HTML 邮件发送能力需要确认 `MailDeliveryService` 当前是否支持 HTML；如果不支持，要先扩展邮件模型。
- Office 文件在线预览不要第一版强做，先支持下载，PDF/image/text 预览即可。
- 操作日志要由服务层统一写，不能只靠前端记录。
