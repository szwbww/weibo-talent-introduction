# 01 · 审核草稿、权限与完整历史

状态：DRAFT / 待用户批准实施。仅开发计划，不代表代码已实现或测试已通过。
基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`，2026-10-29 工作树；原有脏文件见 [状态快照](draft-review-evidence/worktree-status.txt)，不得覆盖。
证据：[逐字源码 E01–E31](draft-review-evidence/source-excerpts.md)、[数据库迁移链](draft-review-evidence/schema-chain.md)、[读写调用全集](draft-review-evidence/06-repository-call-sites.txt)、[SQL/存储引用全集](draft-review-evidence/05-all-store-read-write.txt)、[来源 SHA256](draft-review-evidence/sha256.json)。文中“新增/拟”都是设计，不声称已存在。

## 需求描述
1. user1 为授权专家维护多条草稿；四态、撤回、退回重提、审核前删除、双方评论与变动历史均有服务端 API。
2. admin 查看已提交过的草稿并审核/退回；批准仅冻结正文，绝不发送。
必须保持：现有专家授权边界、user1 无发送/附件权限；管理员原草稿和人工发送不变。
不做：用户管理页面、通用 RBAC、审批流引擎、附件草稿、恢复已删除草稿、自动提交、自动发送。
本步 11 文件（2026-10-29用户“授权”的迁移测试基线修复例外）、2 子系统（邮件审核 + 认证白名单）；新增已有共享表字段 0。可以独立部署：只有 API，无 UI 入口、不调用 SMTP。

## 关键不变量
### Invariant I-1: 身份与专家授权
- Rule: actor 只从 Session 获取，精确 admin/user1；user1 每次读、保存、提交、撤回、删除、评论、历史查询都必须拥有当前专家授权，且只能访问自己草稿。admin 只见 user1 已提交过的草稿；从未提交的正文/历史不暴露给 admin；曾提交后撤回的草稿admin仍可看最新内容及历史，但不能批准DRAFT。发送/审核/翻译/确认仅 admin。
- Applies to: 所有新 API、Service/Repository 条件查询。
- Violation consequence: 越权读取或扩权。
- 来源: K-mailbox-readonly-needs-lifecycle-audit；E01/E02/E07

### Invariant I-2: 四态及锁定
- Rule: DRAFT→SUBMITTED；SUBMITTED→DRAFT（撤回）/RETURNED/APPROVED；RETURNED→SUBMITTED。仅 user1 在 DRAFT/RETURNED 可改主题正文，SUBMITTED 先撤回；已审核主题正文永久冻结，user1 不得编辑/删除/撤回/评论。admin 不直接改用户正文，只能退回；admin 可在已审核后追加评论。删除为 deleted_at 非空，不是第五状态。
- Applies to: create/save/submit/withdraw/return/approve/delete/comment。
- Violation consequence: 已审正文被更改或错误发送。
- 来源: original

### Invariant I-3: 版本、快照与审计同事务
- Rule: 内容/状态/删除变更需要 expectedVersion 精确非负整数，UPDATE 按 id/version/state CAS；成功 version+1；失败 409 不写事件。新草稿 version=1。每次成功变动追加 event；CREATE/SAVE/SUBMIT/WITHDRAW/RETURN/APPROVE/DELETE 保存当时完整 canonical subject/html/text/state 快照。COMMENT 只追加事件、不改业务 version；与审批共用草稿行锁，审批先完成则 user1 评论拒绝。没有更改的重复保存返回原版、不制造历史。
- Applies to: 所有写入路径。
- Violation consequence: 历史缺失、并发覆盖或评论破坏已审核版本。
- 来源: original

### Invariant I-4: 数据模型最小化
- Rule: 仅新建 mailbox_review_draft 与 mailbox_review_draft_event；评论即 COMMENT 事件，不新建评论表/版本表。不扩展既有共享表，不把审核草稿写入 mailbox_reply_draft。删除保留正文/历史；新表不级联删除历史，不阻塞既有联系人删除。
- Applies to: migration/repository 所有读写。
- Violation consequence: 污染管理员草稿或丢审计。
- 来源: original

### Invariant I-5: 富文本与输入边界
- Rule: 服务端 Safelist 清洗后再持久化、回显、记录快照；只收 subject/html，不信客户端 text/owner/state/target/attachments。持久化前按现有Manual换行规范化，text从canonical html派生；提交时沿MailPlaceholderService.placeholderKeysIn/unknownPlaceholderTokens/requireValidPlaceholders拒绝所有变量或非法占位符，并按现有buildFinalValidationText口径限制20,000字符（03复用）。subject≤255，原始+清洗 HTML 各≤1MiB UTF-8；评论/退回原因各≤2000字符且非空；提交时清洗后主题正文必须非空。只允许 p/div/br/b/strong/i/em/u/ul/ol/li/blockquote/a；链接仅 http/https/mailto，移除所有事件属性、style、图片和附件。text 由清洗后的 html 服务端生成。
- Applies to: create/save/submit/comment/return，前端全部正文与历史展示。
- Violation consequence: XSS、审核与发送内容不同或附件权限绕过。
- 来源: original

### Invariant I-6: 真实对话上下文
- Rule: contextToken=SHA256(版本前缀+contactId+按 source_rank/id 排序的全部可见邮件上下文行)，每值使用 UTF-8 长度前缀并区别 null/空串。范围为该专家所有现存非 SIMULATOR_NOOP 账号，不受 UI 分页/日期/chip 限制。逐行含 source,id,account,direction,event_at,message_id,in_reply_to,subject,body,cleaned_body,send_status；不含处理状态/标签/备注。approval 保存服务端当次 token；新消息、旧消息正文修订、失败→成功、归属/账号变化都会变，不只比较最新一封或时间戳。
- Applies to: reviewContext、approve、context/confirm-context、发送前复查。
- Violation consequence: 漏掉相同时间戳/旧信修订或拿模拟版本授权发送。
- 来源: K-mail-record-source-inbound-id；K-sender-account-enabled-scope；E06

### Invariant I-7: 目标与字段归属
- Rule: id/owner/contact/client_request_id/created_at 创建后不变。target_kind,target_id,account_code 创建时由服务端解析：同专家最新真实 processing 优先；没有则最新成功 OUTBOUND mail_record；不能用 source_inbound_id 当 processingId。无真实目标则 422 不新建。首次提交 submitted_at 保留；reviewed_by/reviewed_at/approved_context_token/send_request_id 只在 APPROVE 写且全部成组非空；return_reason 仅 RETURN 写，重提清空。发送绑定 send_attempt_id 仅发送事务写；不存在绑定不是已发送。
- Applies to: 创建/提交/退回/批准/发送；所有 DTO 读者。
- Violation consequence: 跨专家/账号回复或模糊字段语义。
- 来源: K-mail-record-source-inbound-id；E06/E09

## 现状审计
### 既有存储与交互边界（所有子计划共用的审计收据）
- `expert_mail_read_access`：V166 主键 `(username,expert_contact_id)`，contact 删除级联删授权。`ExpertMailReadAccessService.grant/revoke` 唯一业务写者；`getAccess/grantCount` 与 `MailboxConversationRepository.hasReadAccess/expertPredicates` 读取。新审核读写也实时检查授权，不新增授权缓存。E01/E02/E07。
- `mailbox_reply_draft`：V152 每个 owner/contact/kind/processing/scope 唯一；ACTIVE/SENT/DISCARDED。全部数据库写方法是 `MailReplyDraftRepository.insert/save/discard/bindAttempt/closeSent/releaseCompletedBinding`；全部读方法是 `findTarget/findOwned/list/summaries/lockOwned`，由 `MailReplyDraftService`、人工发送两入口及 `ManualReplySendAttemptService` 使用。`discard/closeSent` 清空正文，不能充当审核历史。E03/E04/E05；本功能不改此表及其状态。
- `mail_record`：V1 基表，V15 来源列，V23/V24 attempt 关联及唯一约束；当前映射见 `mail/domain/MailRecord.kt:8`，完整后续 DDL 见迁移链。`inbound_mail_processing`：V5 基表，V11 正文/处理列、V167 folder 唯一键；映射见 `mail/domain/InboundMailProcessing.kt:8`。会话唯一读取来源为 OUTBOUND mail_record + 已关联 contact 的 inbound_mail_processing，排除模拟账号，允许 disabled 账号；不能读 mail_record INBOUND 来替代 processing。E06；来源 K-mail-record-source-inbound-id、K-sender-account-enabled-scope。
- 上述两表全部业务写入口逐条列在 [写路径表](draft-review-evidence/write-paths.md)：AutoMailReplyService 的 processSingleCore/saveMailRecord/sendMeetingInvitation/confirmManualReviewWithBody/confirmProcessed/reclassifyFirstReplyAutoResponse；UnmatchedInboundMailService 的 bindToContact/markResolved；PendingMailOperationService.markResolved/cancelResolved；ManualExpertMailService.sendManualMailInternal；MeetingScheduleService.confirmMeetingAndEmail；ManualOutreachTxHelper.recordSuccess/recordFailure；ManualReplySendAttemptService.finalizeSuccess/finalizeFailure。直接 SQL 还包括 `InboundMailProcessingRepository.reopenManualResolved:71`、`scripts/reclassify-auto-responses.py:435,580`；历史迁移回填见迁移链。所有入口保持原语义，不为“对话版本”逐个加写钩子。
- 读入口全集按源码依赖与 SQL 扫描列在上方两份收据：会话/邮箱/监控、任务记录、人工/自动回复、AI/可信工作台/RAG、材料/附件/通知、退信、联系人管理/外联/排期/运营状态核对均消费这些表。新增上下文摘要只读同一会话 UNION；不改变上述读者。不要把这项新摘要当作全局邮件版本或更改 process_status 的依据。
- `mail_send_attempt`：V23/V24，唯一 `(orcid_id,mail_type)`、message_id；mail_record 的 attempt 外键唯一。写入口为 ManualInitialOutreachService.runIntroductionFromSnapshot、ManualOutreachTxHelper.recordSuccess/recordFailure、ManualReplySendAttemptService.prepareAndClaim/finalizeSuccess/finalizeFailure；repository insertIgnore/claimStatus/updateStatusAndError，加 CrudRepository.save。读取是上述发送服务及 MailReplyDraftRepository 关联读取；详见收据。审核发送只扩展既有人工发送 seam，不改公共 attempt schema。
- 无现成审核表/类/查询参数：`03-new-feature-absence.txt` 中 `mailbox_review_draft|MailReviewDraft|reviewPendingOnly` 0 命中。原型 localStorage、模拟 revision 不作数据库或权限证据。

### 本步直接证据与新增存储的完整入口
- E01：user1 服务端目前只有两条邮件 GET + logout。前端另有 E13 白名单；此步只开放后端，前端在 04 跟进。
- E03/E04：原草稿唯一目标键，discard/closeSent 清正文。因此新建审核 store 有明确代码原因，不扩展原状态枚举。
- E21 `MailContentService.htmlToPlainText` 是转换器，不是 HTML 安全清洗器；E19 是客户端清洗器，不能替代服务端。拟只新增 jsoup 依赖；[官方安装说明](https://jsoup.org/download)确认 1.23.2 支持 Java 8+，[Safelist/clean 文档](https://jsoup.org/apidocs/org/jsoup/safety/Cleaner)支持白名单清洗。执行时用 JDK11编译验证，不把浏览结果当已构建证明。
- 新 store 全写入口：create、save、submit、withdraw、returnDraft、approve、softDelete、comment、confirmContext；后续 03 的 claim/finalize 绑定与发送事件。全读入口：list、detail、events、admin translate/context；02 的待审 count/page；03 的发送校验；04/05 的两端 UI。没有定时清理/后台同步/自动审批写者。
- X1：grant/revoke → 全部审核 API 准入；X2：user1 CAS 内容写 → admin 审核读 → 用户历史读；X3：现有全部邮件写入口 → reviewContext → 批准快照/确认；X4：新 store 与原管理员 store 并存，原自动保存/发送不得命中新表。

## 实现方案
### T1：建两表（I-3/I-4/I-7）
文件：`V169__create_mailbox_review_draft.sql`、`MailReviewDraftModels.kt`、`MailReviewDraftRepository.kt`（完整路径见白名单）。

`mailbox_review_draft` 固定列：

| 列 | 类型 / 空值 | 语义与写者 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 唯一草稿，多个草稿可同专家/同目标 |
| owner_username | VARCHAR(64) utf8mb4_bin NOT NULL | 本版恒 user1；Service Session |
| expert_contact_id | BIGINT NOT NULL | 创建时已授权真实联系人；不设级联外键，保留审计 |
| client_request_id | CHAR(36) ascii_bin NOT NULL | 创建请求 UUID；UNIQUE(owner_username,client_request_id)，重试返回原草稿；不同内容重用 UUID=409 |
| target_kind / target_id | VARCHAR(8) / BIGINT NOT NULL | INBOUND→processing.id；OUTBOUND→mail_record.id |
| account_code | VARCHAR(100) utf8mb4_bin NOT NULL | 服务端解析目标账号，之后固定 |
| version | BIGINT NOT NULL | 从1递增，所有业务 CAS 的唯一版本 |
| state | VARCHAR(16) NOT NULL | DRAFT/SUBMITTED/RETURNED/APPROVED；Kotlin枚举和Service双重守卫 |
| subject / html_body / text_body | VARCHAR(255) / LONGTEXT / LONGTEXT NOT NULL | canonical 正文；DRAFT 允许空 |
| submitted_at | DATETIME(3) NULL | 首次提交时间，仅用于 admin 可见性；撤回不清空 |
| return_reason | VARCHAR(2000) NULL | 退回原因；重提清空，历史仍保留 |
| reviewed_by / reviewed_at | VARCHAR(64) / DATETIME(3) NULL | 批准者与批准时间 |
| approved_context_token | CHAR(64) ascii_bin NULL | 批准时对话摘要 |
| send_request_id | CHAR(36) ascii_bin NULL | 批准时生成一次，供无来信发送幂等使用；永不因重试重置 |
| send_attempt_id | BIGINT NULL | 03在已有 attempt claim 事务内绑定；不新增发送状态副本 |
| deleted_at | DATETIME(3) NULL | 软删除；已审核不允许写 |
| created_at / updated_at | DATETIME(3) NOT NULL | 服务端时间；评论不刷新业务版本/updated_at |

索引：`(owner_username,expert_contact_id,deleted_at,updated_at,id)`；`(state,deleted_at,account_code,expert_contact_id)`；UNIQUE创建UUID。不要加目标唯一键。

`mailbox_review_draft_event`：id BIGINT PK auto；draft_id BIGINT NOT NULL（FK→draft，RESTRICT）；draft_version BIGINT NOT NULL；event_type VARCHAR(24) NOT NULL；actor_username VARCHAR(64) binary NOT NULL；payload_json LONGTEXT NOT NULL；created_at DATETIME(3) NOT NULL；索引 `(draft_id,id)`。COMMENT payload={text}；内容/状态事件 payload={subject,html,text,state,reason?}；CONTEXT_CONFIRMED={contextToken}；03发送事件={attemptId,mailRecordId?,result}。不存邮箱；事件只能 INSERT，禁止 UPDATE/DELETE。严格解析 payload schema，未知 event_type 不渲染为 HTML。

### T2：权限与状态 API（I-1/I-2/I-3/I-5/I-7）
文件：`MailReviewDraftService.kt`、`MailReviewDraftController.kt`、`MailReadOnlyPolicy.kt`、`pom.xml`。
基路径 **`/api/mail/mailbox/review-drafts`**。每个 handler 单独命名，禁止通用 `/action` 通过 action 字符串决定角色。

| HTTP / 后缀 | handler | user1 | admin | 输入 / 返回 |
|---|---|---|---|---|
| POST / | create | 是 | 否 | {contactId,clientRequestId,subject,html} → Detail |
| GET /?contactId=&page=&size=&includeDeleted=false | list | 自己、授权 | 仅 submitted_at 非空 | {items,total,page,size}，page≥0，size1–50，默认20 |
| GET /{id} | detail | 自己、授权 | 已提交过 | Detail；已软删仅 {id,deleted:true}，历史单独读取 |
| PUT /{id} | save | DRAFT/RETURNED | 否 | {expectedVersion,subject,html} → Detail |
| POST /{id}/submit | submit | DRAFT/RETURNED | 否 | {expectedVersion} |
| POST /{id}/withdraw | withdraw | SUBMITTED | 否 | {expectedVersion} |
| DELETE /{id}?expectedVersion= | delete | 非 APPROVED | 否 | 软删；再次同请求返回删除标识，不复活 |
| POST /{id}/approve | approve | 否 | SUBMITTED | {expectedVersion,observedContextToken} |
| POST /{id}/return | returnDraft | 否 | SUBMITTED | {expectedVersion,reason} |
| POST /{id}/comments | comment | 非 APPROVED、非deleted | 已提交过、非deleted | {expectedVersion,text} → event；行锁重新检查状态 |
| GET /{id}/events?beforeId=&limit= | events | 自己、授权 | 已提交过 | 全事件倒序游标，默认20最大100，软删也可读 |
| GET /{id}/context | context | 否 | 已提交过 | {currentToken,approvedToken,changed,observedAt} |
| POST /{id}/confirm-context | confirmContext | 否 | APPROVED | {expectedVersion,observedContextToken} → {confirmationEventId,currentToken} |
| POST /{id}/translate | translate | 否 | 已提交过 | {expectedVersion}；服务端从该版本 text 调 MailTranslationService，返回现有 {ok,translatedText,reason}；不存翻译、不变正文 |

列表默认排除deleted；includeDeleted=true仅供草稿历史，仍逐项执行同身份/授权规则，返回已删除元数据以便events可达。非本人或未提交过admin不可见。

Detail 固定：id/contactId/expertName/ownerUsername/version/state/subject/html/text/accountCode/targetKind/targetId/submittedAt/returnReason/reviewedAt/deleted/createdAt/updatedAt/commentCount；admin另含 currentContextToken/approvedContextToken，03后另含发送结果。**不返回 expertEmail/recipientEmail**；用户只读邮箱历史原接口不变。

异常固定 controller-local：401 UNAUTHORIZED；403 MAIL_READ_FORBIDDEN/ADMIN_REQUIRED/READ_ONLY_FORBIDDEN；404 REVIEW_DRAFT_NOT_FOUND（非本人也404）；409 REVIEW_VERSION_CONFLICT/REVIEW_STATE_CONFLICT/REVIEW_CONTEXT_CHANGED；422 REVIEW_INVALID_CONTENT/REVIEW_TARGET_NOT_FOUND；413 REVIEW_BODY_TOO_LARGE。先身份/授权，再输出版本/正文，失权响应不附带 Detail。严格验证 JSON 整数，禁止 Jackson 把 1.2 转成 expectedVersion=1。

授权撤销并发：user1 写事务先对授权行做锁定读取再锁草稿，保证 revoke 与成功写有确定先后；不存在则403。admin/comment/approve锁草稿；顺序不能反过来再锁grant。读接口按查询时授权，不承诺已经下载到浏览器的旧内容可远程抹除；前端下次403立即清空。

提交长度预检（E29/E30/E31）：调用MailPlaceholderService.placeholderKeysIn(subject,html,text)要求为空，同时requireValidPlaceholders拒绝非法输入；canonical text从已清洗HTML转换。最终校验长度精确为非空subject、canonical text、htmlToPlainText(canonical html)、按出现顺序用空格连接的全部href，再以单空格join；长度≤20000。PendingMailOperationService的buildFinalValidationText当前是private，不得误调用；本新Service按上述小公式预检，03最终seam继续权威校验，测试保持两处相同。

### T3：上下文快照（I-6/I-7）
文件：`MailboxConversationRepository.kt`、`MailReviewDraftService.kt`。
新增只读 `reviewContext(contactId, accountCodes)`，在 **一条 SQL** 中复用 `rangeUnionSql(includeBody=true)`、指定contact、accountCode=null，按 source_rank/id稳定排序；Jdbc callback流式喂 SHA256，禁止 GROUP_CONCAT（截断风险）、禁止只读最近50封。邮件正文不写日志、不回浏览器token之外的摘要。当前账号名单沿 MailboxConversationService.activeAccountCodes 的同一 repository方法。
批准需校验管理员打开草稿时拿到的 observedContextToken 仍等于当前摘要；不等返回409提示先看最新对话，不能把未看过的新对话静默记为已审。确认只追加 event，绝不调用发送；03送信时再次验证确认 event 与当前摘要。
严格界限：摘要保障“检查时数据库已提交且能看到的邮件更新”；不能阻止检查后 SMTP 在途期间新来信。该极小窗口不靠锁住全部邮件写路径解决，不宣称全局串行一致性。

### T4：真实测试（I-1–I-7）
文件：`MailReviewDraftIT.kt`、`AuthFlowIntegrationTest.kt`。
新 IT 采用已有 `@EnabledIfSystemProperty(named="mysqlIt",matches="true")` + MySQL8.0.36/Flyway方式；实测CAS双事务、授权撤销/评论与审批并发、事件失败回滚业务更新、软删历史、无效HTML清洗、context同时间戳插入/旧信修订/跨账号。auth测试用登录cookie请求所有handler，禁止只Mockito Controller当权限证明。
基线修复（用户已授权）：FlywayMigrationIntegrationTest.kt移除过期的“最新版本=157”字面量断言，不替换为169之类的新钉死值；V120用例在完整迁移后的schema/唯一性校验须与已应用V167的folder维度索引契约一致，同时保留uid-validity隔离与历史数据保留验证。AuthFlowIntegrationTest.kt在测试局部隔离WorkerLifecycleTestConfig造成的attachmentTransferWorker重复定义；不允许全局Bean覆盖、跳过测试或修改已应用迁移。修复后仍须完整运行下列原定门禁；新建空库与V168升级验证仍是必需。

## 变更文件清单
| # | 文件（相对仓库根目录） | 操作 / 边界 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V169__create_mailbox_review_draft.sql` | 新增两表；执行前若 V169 已被占用，先修订本白名单，不改已应用迁移 |
| 2 | `pom.xml` | 新增 org.jsoup:jsoup:1.23.2 单依赖；不更改测试跳过开关 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt` | 新增状态、DTO、服务内引用与局部异常 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt` | 新增审核表唯一写者及基础读取（02会话repo额外只读） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt` | 新增权限、状态机、历史、正文清洗、审核上下文 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailReviewDraftController.kt` | 新增精确 API；controller-local 错误映射 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt` | 精确允许 user1 草稿操作 handler，不放开发送/附件 |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` | 只新增复用 rangeUnionSql 的 reviewContext 摘要读取；旧查询不变 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftIT.kt` | 新增 MySQL 真实事务、状态、存储、上下文集成测试 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt` | 扩充真实登录/白名单/失权回归 |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 用户授权的基线修复：移除过期版本字面量，并对齐V167后folder索引/唯一性契约；保留uid-validity、历史数据与实质schema覆盖及迁移执行 |

## 验收标准
- I-1：两身份、未登录、无授权、他人ID、已撤权、未提交admin读取矩阵全测；发送/附件/任意通用POST仍403。
- I-2：四态动作笛卡尔组合；未列转换全部409；已审user1评论/删除也拒绝；批准SMTP调用数0。
- I-3：双会话保存同version只有一次成功；事件插入失败业务回滚；事件快照可恢复每次保存；comment不修改version，批准先赢则评论403/409。
- I-4：迁移新空库+已有V168库均通过；旧表行与旧草稿正文不变；软删后GET events可读，联系人删除不触发审核事件级联清除。
- I-5：恶意img/onerror/script/javascript链接清洗，粗体/列表/安全链接保留；原始与清洗后字节大小都验；回包与历史 canonical相同。
- I-6：同timestamp新增、旧body更新、FAILED→SENT、跨真实账号消息改变token；标签/备注/process_status变更不变token；读取分页不影响token。
- I-7：真实processing与mail_record的同数值id不混；无锚点422；批准字段全有，其它状态全null；反序/重复create不串draft。

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailReviewDraftIT -DmysqlIt=true
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmigrationIt=true
```
必须检查 surefire tests>0 且目标用例非 skipped；Docker不可用=门禁未完成，不算PASS。OrbStack如需 api.version=1.41，沿现有 K-flyway-gate-must-pass-on-fresh-db 环境配置，记录实际命令。

## 人工验收清单
本步可通过接口客户端验收，后续UI重复。统一准备：测试环境认证开启；admin在收发邮件给测试专家甲授权、不给乙授权；甲存在真实收信记录；只用测试邮件账号。浏览器登录可复制cookie到接口客户端，不把cookie写文档。
### A-1: 权限与多草稿
- 前置条件: 按上方准备甲/乙；以user1登录。
- 操作步骤:
  1. 向POST /api/mail/mailbox/review-drafts给甲创建两份不同clientRequestId的草稿，再给乙创建。
  2. 以admin GET尚未提交草稿。
  3. 以user1请求原人工发送和附件下载接口。
- 预期结果: 甲得到两个不同id、均未提交；乙403；admin未提交详情404；user1发送与附件403。
- 覆盖: 需求1、保持项；I-1/I-4/I-7；X1/X4。
### A-2: 状态、完整历史与并发
- 前置条件: 甲草稿主题测试A、正文第一版；复制详情version给两份接口请求。
- 操作步骤:
  1. 先保存第二版，再用旧version保存第三版。
  2. 提交→撤回→保存→重提→admin填原因“补充合作范围”退回→user1重提→admin批准。
  3. 批准后user1再次保存、撤回、删除、评论；查询events。
- 预期结果: 旧version409；成功链依次显示未提交/已提交/未提交/已提交/已退回/已提交/已审核；历史含第一版及第二版；批准后四种user写均拒绝；无新发件。
- 覆盖: 需求1/2；I-2/I-3/I-5；X2。
### A-3: 评论、软删、撤权
- 前置条件: 甲另建未审核草稿并提交。
- 操作步骤:
  1. 两端各发表评论；user1删除；打开变动历史。
  2. admin撤销甲授权，user1再次请求历史与评论。
- 预期结果: 删除不显示在普通列表；历史含双方评论及删除事件；撤权后两接口403且不返回正文。
- 覆盖: 需求1；I-1/I-3/I-4；X1/X2。
### A-4: 批准时的真实新对话
- 前置条件: admin打开已提交草稿取得currentContextToken。
- 操作步骤:
  1. 用测试邮箱向甲对应账号发送一封新邮件，执行既有检查回复，直到时间线出现。
  2. 带旧token调用approve；刷新上下文后再次批准。
- 预期结果: 第一次409 REVIEW_CONTEXT_CHANGED；第二次APPROVED；批准两次均不会新增发送邮件。
- 覆盖: 需求2；I-6/I-7；X3。

人工验收开始时才导出本文件同前缀 `-acceptance.md`，当前不生成。

## 修正记录
- 2026-10-29：用户“授权”解除01实施前的基线失败暂停。新增上表第11文件，主计划I-23同步例外允许01共11文件；仅清理过期迁移版本断言及在已授权AuthFlowIntegrationTest.kt局部隔离重复Bean配置，原有权限/事务/迁移验收不降低。证据：docs/plans/fast/draft-review-master/children/01/baseline-reports.txt；审批与身份登记：fast-p ledger A1/A2。

- 2026-10-29：用户“授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p”批准剩余V120测试与V167契约对齐；不改已应用迁移、不跳过门禁、不新增产品行为。01在epoch3继续既有实现，只修复新授权的剩余项并重跑原定门禁，再进行独立轻验；fast-p ledger A3/A4记录身份与授权。

