# 01 持久化模型、快照和迁移

依赖无。代码基线与统一I编号见 [总计划](manual-reply-delay-master.md)。本阶段不开启入口、不调SMTP。下文新增名称均是拟实现接口。

## 需求描述

建立可在重启后读取的人工回复延迟记录，能够保存已确认发送内容、期限、原操作者、来源、错误及版本。保留所有既有表字段、数据和默认发送行为。范围外为消息展示、真实调度与发送。

## 关键不变量

### Invariant I-1 — 时间与版本
- Rule — epoch毫秒为任务时间存储和HTTP传输权威表示，服务器读数据库 `CURRENT_TIMESTAMP(3)` 转 epoch，dueAt在排队事务内加60000，revision正整数。
- Applies to — repository时钟、insert/CAS、模型codec。
- Violation consequence — 时区差导致提前发、浏览器改时钟影响业务。
- 来源 — original；总I-1/I-3。

### Invariant I-4 — 快照完整性
- Rule — 新快照schemaVersion=1，显式DTO白名单；不直接序列化Spring实体或source.account。恢复普通草稿、审核身份、邮件投递、审计和归档必须都有独立命名字段。未知版本或缺关键字段不可投递。
- Applies to — DelayedManualReplyModels codec、repository读取。
- Violation consequence — 不能重建消息或泄露凭据。
- 来源 — K-manual-rich-final-body-single-seam；总I-4。

### Invariant I-10 — 状态边界
- Rule — 新任务状态WAITING/SENDING/CANCELLED/SENT/FAILED/UNKNOWN，只修改新表，不在既有attempt枚举增加同名值。唯一attempt绑定、索引和schema校验由真实MySQL验证。
- Applies to — migration、row mapper、codec。
- Violation consequence — 破坏批量发送或旧记录读取。
- 来源 — original；总I-10。

## 现状审计

- attempt表 V23/V24 有唯一(orcid_id,mail_type)、message_id；人工body是指纹（ManualReplySendAttemptService:326–336），缺完整HTML，不能直接替代持久队列。完整写读集合见总计划mail_send_attempt与证据01/02。
- `MailReplyDraftModels.kt:15–50` 现有草稿content/context含RAG、会议、附件、跟进锚点，可作为显式恢复DTO。validated ref的internal var不是data class构造字段，Jackson默认行为不能作为持久化保证，必须逐项映射。
- `PendingMailOperationService.kt:1894–1935` 的source含MailSenderAccount和contact/inbound实体，不可整体序列化。`archiveLiveUnsupportedAnswers:1302–1362` 消费verifiedAssembly.response.itemVersions/source/finalParagraphByRequestKey，冻结必须保留这些业务值。
- `MailReplyDraftRepository.kt` 展示项目已有 NamedParameterJdbcTemplate、GeneratedKeyHolder、显式RowMapper、MANDATORY事务模式。直接复用这些技术形态，不引入未验证的Spring Data DTO投影。
- 当前最高迁移为V173。证据source-manifest列全体迁移，执行前重新确认版本占用。`FlywayMigrationIntegrationTest`的targetSchemaVersion回执在06-schema-tests.txt。（来源 K-flyway-latest-version-test-pin）
- 新存储当前没有任何写读路径。新增写者为03的生命周期和结果事务，读者为04worker/API、05会话、06前端。此阶段只提供仓储，入口关闭。

## 实现方案

### T1 表结构（I-1/I-4/I-10）

新增 `V174__create_delayed_manual_reply.sql`。若实施时174已被其他迁移占用，仅重编号本迁移与本计划/测试期望，不修改已应用迁移。表名 `delayed_manual_reply`，ENGINE=InnoDB、utf8mb4_unicode_ci。精确字段设计如下。

| 字段 | SQL 类型/约束 | 语义 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | UI稳定任务ID |
| send_attempt_id | BIGINT NOT NULL UNIQUE | 现有attempt ID，FK RESTRICT |
| expert_contact_id | BIGINT NOT NULL | 当前授权/过滤，不加contact FK以保留删除后的审计 |
| account_code | VARCHAR(100) COLLATE utf8mb4_bin NOT NULL | 已核定发件账号 |
| actor_username | VARCHAR(64) COLLATE utf8mb4_bin NOT NULL | 会话身份 |
| origin_kind | VARCHAR(16) NOT NULL | DRAFT / LEGACY / REVIEW |
| origin_id | BIGINT NULL | 普通/审核稿ID，LEGACY null |
| origin_version | BIGINT NULL | 已验证稿版本，LEGACY null |
| revision | BIGINT NOT NULL | 初值1，状态/排队代次CAS |
| status | VARCHAR(16) NOT NULL | 六态之一 |
| due_at_ms | BIGINT NOT NULL | 当前轮截止时间 |
| dispatch_token | CHAR(36) CHARACTER SET ascii NULL | claim赢家令牌 |
| claimed_at_ms | BIGINT NULL | 进入SENDING时刻 |
| subject | VARCHAR(255) NOT NULL | 由快照subject单向派生供SQL筛选 |
| text_body | LONGTEXT NOT NULL | 由快照finalText派生供消息展示/搜索 |
| snapshot_json | LONGTEXT NOT NULL | schemaVersion=1完整快照 |
| public_error_code | VARCHAR(64) NULL | 固定公开错误码 |
| created_at_ms | BIGINT NOT NULL | 稳定时间线排序键 |
| updated_at_ms | BIGINT NOT NULL | 变更/恢复检查 |

索引 `(status,due_at_ms,id)`、`(expert_contact_id,account_code,created_at_ms,id)`、`(origin_kind,origin_id,status)`。CHECK约束状态集合、revision>=1、due>=created、origin三态与id/version配对、SENDING需要token与claimedAt。应用层重复校验，不能依赖某个MySQL版本是否执行CHECK。SENT/FAILED/UNKNOWN保留最后token，CANCELLED在未claim时token可为空。

`subject/text_body` 只在首次插入从快照派生，不可单独修改；SQL读取必须与快照round-trip一致。重新排队同一任务只更新时间/状态/revision/确认元数据；同一普通源稿更高版本但完整指纹相同的情形按03T1更新origin_version/restore引用，不改冻结正文。

### T2 快照协议（I-4）

新增 `DelayedManualReplyModels.kt`，显式声明以下值对象，均为服务端内部构造。

- `DelayedManualReplySnapshot(schemaVersion=1, payload, source, restore, evidence, confirmation)`。
- payload按SendPayload字段显式映射 orcidId/contactId/inboundProcessingId/accountCode/normalizedRecipient/subject/finalText/finalHtml/inReplyTo/canonicalQaRuleIds/primaryRuleId/sourceAnchor/idempotencyRequestId/calendarAttachment/meetingEvent/outboundAttachments。列表顺序不变。draftSendRef/reviewSendRef通过restore映射恢复，禁止依赖internal var默认序列化。
- source仅含smtpInReplyTo/smtpReferences/anchorMailRecordId/source messageId/inboundProcessingId/accountCode/recipient；不存整个账号实体。worker按id/code重新取实体并比对身份。
- restore为discriminated union。DRAFT保存ValidatedDraftSendRef的owner/id/version/target/content及原绑定；REVIEW保存reviewId/version/actor/confirmationEventId/冻结subject/html/text/account/target/sendRequestId/approvedContextToken；LEGACY保存原来信id与原请求的主题/html/text、QA/RAG/assembly/模板原文、会议/附件，供原详情页恢复，不强塞进不支持legacy QA的MailReplyDraftContext。
- evidence保存carriesQa/canonicalFactIds/serverSuggestedFactIds/edited、ragFactCodes和corpusFingerprint、归档所需assembly.response的source/itemVersions/finalParagraphByRequestKey/diagnostics、templateTextBody、auditNote/bodyPreviewText/操作人。从现有已验证结果映射，不接受客户端声称的verified字段。
- confirmation保存本次已通过的安全确认及强确认结果/已验证内容摘要，重发时不能对改变后的正文复用。
- JSON用带Kotlin/JavaTime模块的显式mapper，限制有效UTF-8快照总量4MiB（新设计上限，超出返回413且不入队），无二进制附件。各字段长度/ID/enum/null组合和calendar/event同有同无均验证。
- 三种restore、RAG、legacy assembly、ICS、附件、空证据分别round-trip；断言其可重建原SendPayload同一fullHex。严格区分指纹与完整快照SHA，若保存快照摘要用运行时计算校验，不新增不必要列。

同文件声明跨阶段共享类型：`DelayedManualReplyStatus`六态、`DelayedManualReplyRow`对应表字段、`DelayedDispatchRef(jobId, revision, dispatchToken)`仅internal、`DelayedManualReplyView`按04T3公开字段、`DelayedManualReplyActionRequest(expectedRevision)`以及受控restore DTO。这些是拟新增名称，先定义不引用后续service/controller类，后续阶段只消费。所有status/identity/time/派生列受I-1/I-4/I-10约束。

### T3 仓储与配置（I-1/I-10）

新增 `DelayedManualReplyRepository.kt`，提供单条/批量读取、按attempt读取、按origin读取、due id扫描、显式行锁、insert/CAS状态操作。修改要求已有事务的方法标MANDATORY并检查实际事务。due扫描只读id、不先领取，LIMIT固定20，按due/id升序；写SQL固定列名，绝不接收任意table/where字符串。

新增 `ManualReplyDelayProperties.kt`，`@ConfigurationProperties(prefix="talent-introduction.manual-reply-delay")` 配合本文件的配置注册。`enabled=false` 仅新请求入队开关；`pollMs=1000`、`unknownAfterMs=600000` 为明确设计值，要求正值。60秒为业务常量不可配置。worker线程数固定1，不复用批量发送executor。后续worker关闭开关仍扫描已存在任务。

### T4 迁移验证（I-1/I-4/I-10）

在既有FlywayMigrationIntegrationTest增加V173→V174与fresh迁移、索引/约束/中文/毫秒、唯一attempt冲突、旧草稿数据不变。修改最新版本期望，历史显式target版本不动。codec/仓储行为测试由02/03覆盖，本阶段使用迁移测试及编译检查。

复核发现另有3处无target却期望172：InboundPendingOwnerIT:424–426的V170→V172测试还固定migrationsExecuted=2；ExpertMailReadAccessIT:484–485的V169升级和:505的fresh测试。它们在当前最高V173下已存在漂移，不能声称本次迁移才引入。将两个历史升级测试的最终Flyway显式target("172")，保留历史期望与迁移数；fresh完整链测试期望改174并更新描述。具体上下文收入证据09-followup-source.md，这两个类都依赖显式mysqlIt开关与配置数据源，不自带Testcontainers；运行时必须设置mysqlIt=true并显式覆盖到临时隔离MySQL，禁止使用默认开发库。

## 变更文件清单

| # | 文件 | 操作 |
|---|---|---|
|1|src/main/resources/db/migration/V174__create_delayed_manual_reply.sql|新增|
|2|src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt|新增|
|3|src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt|新增|
|4|src/main/kotlin/com/weibo/talentintroduction/config/ManualReplyDelayProperties.kt|新增|
|5|src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt|修改|
|6|src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt|历史升级测试显式target172|
|7|src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt|历史target与fresh最高版本|

## 验收标准

- I-1 — SQL毫秒round-trip，唯一/CAS基础约束，dueAt不依赖浏览器时间。
- I-4 — DTO逐项映射审核，无账号凭据/文件字节字段；03补齐真实round-trip测试后才能启用。
- I-10 — 旧表列无变更，新表唯一/索引/状态约束检查。`mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` 输出实际执行用例数而非SKIPPED。
- 执行前后核对迁移编号、文件清单和schema diff。JDK11固定。无生产数据库操作。

## 人工验收清单

### A-1 迁移独立部署
- 前置条件 — 使用隔离MySQL恢复一份V173测试库，记录现有发送attempt/草稿行数和内容。
- 操作步骤 — 1. 应用V174。2. 查看information_schema列/索引。3. 保持enabled=false启动应用并发一封测试人工回复。
- 预期结果 — 旧行数/内容不变，新表为空；人工回复沿原同步链成功，未创建delay记录。
- 覆盖 — I-1/I-4/I-10、既有发送行为。

### A-2 约束
- 前置条件 — 隔离库建一个真实attempt，按表字段准备合法任务行。
- 操作步骤 — 1. 插入合法行。2. 用同attempt再次插入。3. 读回epoch毫秒和中文subject。
- 预期结果 — 第二次插入唯一冲突；毫秒与中文逐字相等；attempt原状态不变。
- 覆盖 — I-1/I-10、新表→读取交互。
