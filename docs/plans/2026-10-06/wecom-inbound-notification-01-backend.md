# 企业微信群通知 01：开关、收信接入、异步通知

日期：2026-10-06；状态：待实施。上级：[总方案](wecom-inbound-notification.md)。本步 10 个文件、一个通知子系统。

## 需求描述

提供持久化全局开关以及专家新来信群通知。默认关闭，开启后由普通收信触发；关闭期间不积压，重新开启不补发。

必须保持：现有入库、IMAP 确认、自动回复决策及 SMTP、专家状态/标签/附件/人工处理语义；前端和现有自动回复配置不变。

不包含：前端、ES 字段、历史补发、未匹配来信通知、消息互动、完整邮件转发、通用消息平台、通知日志页面。按独立收信通知设计，不承诺绕过现有收信轮询实时到达。

## 关键不变量

### I-1：配置与全局作用域
- Rule：独立表 `expert_inbound_notification_setting` 单例 id=1；`enabled=false` 初始值，`generation=0` 初始代次。无记录视为关闭；DB 读取失败禁止推送，GET 返回错误而非假装关闭。仅 enabled 真正变化才增加 generation、写 updated_at/updated_by；同值 PUT 幂等。所有账号共用开关。
- Applies to：迁移、Repository、Service、Controller。
- Violation consequence：重启丢开关、并发重复切换、错误触发外发。
- 来源：original。与 K-batch-send-setting-kv 现有共享 KV 分离，不扩展旧任务配置。

### I-2：只为新来信创建通知
- Rule：`processSingle` 末尾增加参数 `notifyGroups: Boolean = false`。普通 `receiveAndAutoReply` 调用显式 true；`processByUids` 显式 false；skipImapAck=true、模拟账号、重复结果不通知。真实收信事务内查本次物理身份对应 processing 行，要求 `expertContactId != null`、真实 owner/UIDVALIDITY/UID 有效；全局自动回复关闭、人工处理、正文截断等已匹配来信均可通知。不用 `result.recorded` 判定资格（该字段是 mail_record 语义）。绑定/恢复历史工单不通知。
- Applies to：AutoMailReplyService 事务接缝、通知服务 enqueue。
- Violation consequence：漏掉手动处理、补录刷屏、模拟数据外发。
- 来源：K-inbound-processing-write-paths、K-process-single-all-callers，当前代码重新核验。

### I-3：与收信事务的边界
- Rule：`processSingleCore` 返回后、现有 `transactionTemplate.execute` 结束前，登记 outbox。调用独立通知 Repository 的 NESTED 保存点事务（从 PlatformTransactionManager 新建专用 TransactionTemplate，禁止复用已有 REQUIRES_NEW 模板）。outbox 成功随收信一起提交；核心回滚时 outbox 消失。登记失败回滚保存点并记录无凭据错误，保留主收信流程；真实 MySQL 测试必须证明失败不污染主事务。不得以直接 HTTP、@Async 内存事件或先提交独立 outbox 替代。
- Applies to：AutoMailReplyService、Repository、Service。
- Violation consequence：回滚的信仍推送、网络卡住收信、通知故障影响邮件。登记本身失败时本封通知可能遗漏，这是明确的 best-effort 边界，不宣称零丢失。
- 来源：original；现有 AttachmentTransferWorker.kt:724–735 的模板为 REQUIRES_NEW，不能按变量名误以为可复用。

### I-4：关停、并发与代次
- Rule：enqueue/切换开关/领取通知按相同顺序先锁 setting 单例；enqueue 仅 enabled=true 时写当前 generation。关闭后 PENDING 全部 CANCELLED；已领取任务发 HTTP 前短事务再检查 enabled 和 generation，过代次取消；取消不恢复。设定开关事务不得等待外部 HTTP。检查通过到实际 HTTP 之间以及已在途请求存在关闭竞态，最多允许当前一条已领取消息继续完成，不承诺撤回。
- Applies to：Repository.setEnabled/enqueue/claim/authorizeSend，worker。
- Violation consequence：关掉仍持续推送、重新开启重放旧队列。
- 来源：original。

### I-5：通知身份、状态与失败
- Rule：outbox 对 `(mailbox_owner_code,uid_validity,imap_uid)` 唯一；每个物理邮件只登记一次。状态仅 PENDING、SENDING、SENT、FAILED、CANCELLED。claim 使用条件更新、lease_token、lease_until；完成必须匹配 token，旧 worker 不得覆盖新 worker。SENT/CANCELLED/FAILED 为终态；attempts 最多 3（初次 + 2 次重试，30s/120s 退避）。超时、5xx、明确可重试频限错误可重试；凭据/参数等确定错误直接 FAILED；HTTP 200 仍需解析并验证业务成功码。结果未知重试可能在群中重复：Webhook 无法保证 exactly-once，在消息带稳定「通知编号」。
- Applies to：迁移、Repository、Service。
- Violation consequence：重复登记、无限重试、将失败标成功或丢失崩溃恢复。
- 来源：original。

### I-6：异步、限速与资源
- Rule：通知服务自有单线程 scheduled executor，生命周期关闭时 shutdown；不在共享 @Scheduled 线程里执行 HTTP。单个配置目标每 5 秒最多一次发送尝试。setting 中以租约持久化 worker_owner/worker_lease_until 以及 next_send_at，多实例仅一个有效发送者；HTTP connect/read 超时 3s/5s，租约 30s；到期处理卡住的 SENDING，受 attempts 上限约束。每次只取一条，索引扫描，不加载历史全集。此 worker 独立于原邮件 scheduling.enabled、自动回复开关和 RabbitMQ 开关。
- Applies to：Service 生命周期和 worker、Repository claim。
- Violation consequence：邮件检查阻塞、多副本突破限速、服务重启遗留无主通知。
- 来源：original。

### I-7：接口与凭据
- Rule：`GET/PUT /api/expert-inbound-notifications/settings`，PUT 严格 JSON boolean `enabled`。响应 `{enabled, configured, generation, updatedAt}`；`configured` 仅表示本机有合法格式凭据，不证明远端有效。配置空/非法时开启返回 409 +「未配置企业微信机器人」；关闭始终允许。复用 session 认证边界，updatedBy 从 AuthSessionKeys.USERNAME 读取，不接受前端身份。GET 不写数据、不测试 Webhook；配置启用值不由环境变量覆盖 DB。
- Rule：`WECOM_EXPERT_INBOUND_WEBHOOK` 仅服务端环境变量，YAML 空默认。限制 HTTPS、主机 qyapi.weixin.qq.com、路径 /cgi-bin/webhook/send、无 userinfo、key 非空、不跟随重定向；日志、异常响应、数据库不得包含 Webhook/key。不要把完整 HTTP 异常对象直接打日志。
- Applies to：Controller、Service、application.yml。
- Violation consequence：暴露密钥、未登录修改设置或前端虚假成功。
- 来源：original。

### I-8：消息内容
- Rule：发送 `msgtype=text`，纯文本：固定标题「专家来信」、通知编号、专家姓名（缺失回退发件邮箱）、发件邮箱、账号、主题（缺失「无主题」）、原收信时间、摘要。摘要使用 MailBodyCleaner 清理 `cleanedBody ?: body`，去控制字符并截 160 Unicode code points；主题最多 200、姓名 80，其余动态字段也有长度上限，总内容限制 1800 UTF-8 bytes。最终裁剪不能切断字符。无 @all/mentioned_list、无正文附件、无未实现的跳转链接；不调用 LLM 翻译。
- Applies to：Service payload 构造。
- Violation consequence：长信拒收、引用链泄露、乱码、邮件内容意外变成提醒指令。
- 来源：original；1800 bytes 是本系统设计上限，实施时需复核外部接口文档。

## 现状审计

### inbound_mail_processing：不变更 schema，仅在既有事务内读取本次新行

- Schema：`V5__create_inbound_mail_processing.sql`；后续 V6/V10 增正文/意图相关字段；V120 加 UIDVALIDITY 并替换逻辑唯一键；V134 加 mailbox_owner_code + 物理唯一键。历史 owner NULL/UIDVALIDITY=0 不回填。实体 `mail/domain/InboundMailProcessing.kt` 已读。
- 写路径：AutoMailReplyService.confirmManualReviewWithBody:1308 / confirmProcessed:1364 新建；confirmManualReview 委派后者。UnmatchedInboundMailService.bindToContact:193、markResolved:238；PendingMailOperationService.markResolved:1524 / reopen:1595→Repository.reopenManualResolved:62 修改既有行；后四条不增加副作用。
- 读路径盘点（`rg -l 'InboundMailProcessingRepository|inbound_mail_processing' src/main/kotlin`）：RagReplyController、AiTrainingController、TrustReplyWorkbenchService、MailMonitoringService、ExpertMaterialService、ExpertContactManagementService；BounceBackfillService、UnmatchedInboundMailService、MailboxService、MeetingConfirmationService、AutoReplyPreviewService、PendingMailOperationService、ManualReplySendAttemptService、MailboxSuspensionService、AutoMailReplyService、ExpertContactLocationService、ExpertRepliedDismissalService、InboundMailTagService；InboundMailTagRepository、InboundMailProcessingRepository、MailRecordRepository、MailboxConversationRepository；InboundMailSummaryController、MailboxConversationController。依赖身份、专家归属、正文/主题、收信时间、处理状态、理由及人工解决字段；本步这些列全部不改。
- 本步只用已有物理身份查询读取 id、expertContactId、主题/正文/时间，读已存在 expert_contact 的 id/expertName/expertEmail（实体已核验，不写专家表）。其他读者继续消费相同记录，不改现有状态。
- 入口核验：`rg -n 'processSingle\(|processByUids\(' src/main/kotlin` 当前直接调用 processSingle 是 receiveAndAutoReply:907 与 processByUids:982；MailAutomationController:52 调 processByUids。BatchAutoMailReplyService→receiveAndAutoReply 由手动检查/队列/调度进入。知识条目里的“6 个调用方”是链路分类，不能当直接调用点数量。
- 注意：普通收信在 processSingle 前处理退信/DMARC；UID 回填绕过部分外层分类，故本方案明确其 notifyGroups=false。自检探针在 processSingle 最前端过滤。重复物理 UID 与历史重复结果不得登记通知。
- Interaction X1：收信事务写入 → 同事务登记 → 提交后 worker 可见。X2：全局自动回复关闭 → 来信仍写入 → 通知照常。

### 新增通知存储：不借用 batch_send_setting

现状已有 AutoReplySettingService 写 autoReply.globalEnabled；BatchSendSettingService 写旧 batchSend.*；MailOpenTrackingRepository 写 mailOpenTracking.enabled。此功能建立独立表，避免与旧配置相互覆盖。以上 KV 不修改。

拟新增 `expert_inbound_notification_setting`：

| 列 | 契约 |
|---|---|
| id | TINYINT PK；初始化唯一行 1 |
| enabled | BOOLEAN NOT NULL DEFAULT FALSE |
| generation | BIGINT NOT NULL DEFAULT 0 |
| updated_at / updated_by | DATETIME(3) / VARCHAR(100)，允许初始化 NULL |
| worker_owner / worker_lease_until | VARCHAR(64) / DATETIME(3)，NULL=无持有者 |
| next_send_at | DATETIME(3)，NULL=可立即尝试 |

拟新增 `expert_inbound_notification_outbox`：

| 列 | 契约 |
|---|---|
| id | BIGINT AUTO_INCREMENT PK，作为通知编号 |
| inbound_processing_id / expert_contact_id | BIGINT NOT NULL，只存引用、不增加删除级联 |
| mailbox_owner_code / uid_validity / imap_uid | VARCHAR(64)/BIGINT/BIGINT NOT NULL；组合 UNIQUE |
| generation | BIGINT NOT NULL，登记时开关代次 |
| payload | TEXT NOT NULL，已清理最小通知内容；不存凭据/完整正文 |
| status | VARCHAR(16) NOT NULL DEFAULT 'PENDING'，应用严格枚举 |
| attempts | INT NOT NULL DEFAULT 0 |
| next_attempt_at / created_at / updated_at | DATETIME(3) NOT NULL，统一 UTC 墙钟 |
| lease_token / lease_until | VARCHAR(64)/DATETIME(3)，非领取状态为空 |
| sent_at / last_error_code | DATETIME(3)/VARCHAR(64)，可 NULL，仅存脱敏码 |

索引 `(status,next_attempt_at,id)` 与 `(status,lease_until)`；CHECK 只辅助，不依赖 MySQL 是否执行。新表所有写路径都在新 Repository：迁移 seed；setEnabled；enqueue；claim/租约续期；finish/retry/cancel/recover。读路径同样封装此 Repository：settings API、enqueue 闸门、worker 闸门与领取。无别的脚本/回填写入路径，不新增历史扫描任务。worker 可周期性分批删除超过 30 天的 SENT/FAILED/CANCELLED 行，每批最多 100（该新通知辅助表的清理，不碰邮件数据）；终态清理不导致旧信重新登记，依赖既有收信物理判重。

X3：API 设置写入 → enqueue/worker 读取；X4：登记 generation → 关停/再开/租约恢复读取；X5：worker finish → 下次领取跳过终态。

### 鉴权、配置与技术事实

AuthWebConfig:28 覆盖 /api/**；AuthInterceptor 校验 session、用户存在及强制改密，不存在现成细粒度角色模型；按现有控制台登录权限接入，不发明 admin 字符串校验。Controller 自身也要求 session USERNAME，禁止从正文伪造 updatedBy。

TalentIntroductionApplication 已启用 Scheduling；AttachmentTransferTransactionConfig 的 TransactionTemplate 配置为 REQUIRES_NEW，通知嵌套登记必须新建 NESTED 模板。MailOpenTrackingRepositoryIT 是实际 MySQL/Testcontainers 的已有范式。计划时最新迁移 V149；预留 V150，执行前重查冲突，遇占用须同步更新本文件与清单。

## 实现方案

### T1：持久化与 API（I-1/I-4/I-5/I-7）

新增迁移与 Repository；DTO 可放 Repository 文件中，遵循本仓 MailOpenTrackingRepository 的组织方式。Service 验证配置与 payload；Controller 提供 GET/PUT。`application.yml` 加 `talent-introduction.wecom-inbound.webhook: ${WECOM_EXPERT_INBOUND_WEBHOOK:}`。setting PUT 加行锁，真切换才增代次；禁用与取消队列在同一事务。GET 返回 Cache-Control:no-store。

文件：清单 1/2/3/4/6。新写数据由相同服务的 API 与 worker 消费；不读取旧 KV。

### T2：接入收信（I-2/I-3/I-8）

文件：清单 3/5。在 processSingle 的现有事务 execute 内保存 result，若 notifyGroups 且非 skipImapAck 且非重复，则读取新 processing 并调用 `enqueueBestEffort`。在普通收信调用处显式 true，UID 回填 false。通知服务通过可选的 setter injection 接入 AutoMailReplyService（独立的属性/方法，不改已有巨大构造函数与所有历史测试桩；生产缺服务视为配置错误，不静默当永久关闭）。单元测试显式注入 mock 验证连接。

enqueue 需要外层真实事务，否则拒绝登记并脱敏报告；嵌套保存点失败的 catch 放在保存点事务之外。不修改两个确认 sink、不移动已有 markSeen。已匹配真实专家信仅登记简短通知。

### T3：后台发送（I-4/I-5/I-6/I-7/I-8）

文件：清单 2/3。Service 自有单线程 scheduler/HTTP client，启停由 bean 生命周期控制，调度回调顶层捕获异常避免 executor 以后不再运行；无 caller-runs。跨实例 setting lease + 条件领取 outbox；短事务结束后才 HTTP；网络结束按 token 写结果。配置缺失时不发、可关闭；每次发送先重读代次，重试同样经过闸门。清理终态按上文期限、限批执行。

### T4：验证（I-1～I-8）

文件：清单 7/8/9/10。本地 mock HTTP，不使用真实群凭据。MySQL IT 验证锁/保存点/回滚/抢占/恢复；Controller 测非法布尔值、未认证、配置缺失；已有 AutoMailReplyServiceTest 增通知接缝、自动回复关闭、UID 回填与 skipImapAck 场景。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
|1|src/main/resources/db/migration/V150__create_expert_inbound_notification.sql|新增|
|2|src/main/kotlin/com/weibo/talentintroduction/mail/repository/ExpertInboundNotificationRepository.kt|新增|
|3|src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertInboundNotificationService.kt|新增|
|4|src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertInboundNotificationController.kt|新增|
|5|src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt|修改|
|6|src/main/resources/application.yml|修改|
|7|src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertInboundNotificationServiceTest.kt|新增|
|8|src/test/kotlin/com/weibo/talentintroduction/mail/repository/ExpertInboundNotificationRepositoryIT.kt|新增|
|9|src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertInboundNotificationControllerTest.kt|新增|
|10|src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt|修改|

## 验收标准

- I-1：首次 GET disabled；同值 PUT 不增代次；重建 Service/重启读值一致；DB 异常不发送。
- I-2：普通真实已匹配信一条；全局自动回复关闭仍一条；未匹配/回填/模拟/重复/skipImapAck 零条；后续工单标记无新增。
- I-3：MySQL 外事务回滚后 outbox 零行；未提交另一连接不可见；通知 SQL 人为失败后收信行正常提交且 markSeen 仍按原流程执行。必须真实保存点测试，不能仅 Mockito 证明。
- I-4：两线程 enqueue/disable、disable→enable、领取前后禁用、租约恢复均不会复活旧 generation。只允许文档明确的一条在途例外。
- I-5：并发重复登记只有一行；lease token 旧完成拒绝；崩溃恢复、超时重试上限、200 业务失败、永久错误、终态清理都验证。
- I-6：多 worker 全局尝试间隔至少 5s；HTTP 慢时不占收信线程/共享 scheduler；stop/shutdown 可回收；关闭邮件总调度不关闭通知 worker。
- I-7：严格 boolean/未登录/强制改密、非法主机与跳转、日志和响应脱敏、未配置禁止开启；关闭可用；GET 无写/无 HTTP。
- I-8：纯文本、主题/姓名回退、Emoji/中文字节边界、160 字摘要、1800 bytes 上限、无邮件附件、无 @all、无真实凭据。

命令：JDK 11；`mvn test -Dtest=ExpertInboundNotificationServiceTest,ExpertInboundNotificationControllerTest,AutoMailReplyServiceTest`；`mvn test -Dtest=ExpertInboundNotificationRepositoryIT -DmysqlIt=true`；`git diff --check`。发布前跑项目 `mvn test`/`mvn clean package`。MySQL IT 需 Docker；环境缺失时明确标未验证，不能以跳过代替通过。接口文档待核验项见总方案，真实群测试须后续授权。

## 人工验收清单

### A-1：默认关闭与持久化
- 前置条件：测试库应用新迁移；测试环境配置测试机器人；已登录控制台。
- 操作步骤：GET 设置；PUT enabled=true；重启；GET；重复 PUT true。
- 预期结果：依次 false、true、true；最后 generation 不再增加。
- 覆盖：I-1/I-7，X3。

### A-2：人工处理来信
- 前置条件：测试专家联系人与已发介绍信存在；全局自动回复关闭；通知开启。
- 操作步骤：测试专家发信；控制台「检查回复」；检查收发件箱、群消息和自动回复开关。
- 预期结果：收信记录与原人工流程一致；群出现一条带稳定编号通知；没有因通知功能新增 SMTP 回复；自动回复仍关闭。
- 覆盖：I-2/I-3/I-8，X1/X2；收信、人工处理及自动回复回归。

### A-3：关闭与再开
- 前置条件：测试环境通知开启，通过 mock endpoint 暂停发送准备 PENDING。
- 操作步骤：PUT false；再收一封信；恢复 mock；PUT true；再收另一封信。
- 预期结果：原 PENDING 取消，关闭期间来信不补发，仅再次开启后的新信通知。若关闭前已提交请求，最多一条在途通知允许完成。
- 覆盖：I-1/I-4/I-6，X3/X4。

### A-4：网络故障与重启
- 前置条件：测试环境使用可控 HTTP stub，返回 5xx；通知开启。
- 操作步骤：收信；观察邮件已入库；重启通知服务；再让 stub 成功。
- 预期结果：邮件及 IMAP 确认不因 Webhook 故障失败；不超过 3 次发送尝试；成功写 SENT 后不再尝试；attempts/代次不因重启丢失。
- 覆盖：I-3/I-5/I-6，X1/X4/X5。

### A-5：排除历史与重复
- 前置条件：已有一封已处理专家信、一个未匹配来信；另准备测试 UID 回填和 skipImapAck 测试夹具。
- 操作步骤：重复「检查回复」、回填 UID、绑定未匹配来信、完成/重新打开工单，运行模拟夹具。
- 预期结果：上述操作群消息增量为 0；已有专家状态、标签、附件、人工操作仍按原功能生效。
- 覆盖：I-2/I-5；状态、标签、附件、工单回归。

### A-6：配置与认证失败
- 前置条件：测试部署移除 Webhook 环境变量并重启；另用无 session 客户端。
- 操作步骤：已登录 GET/PUT true/PUT false；未登录 PUT true。
- 预期结果：configured=false；开启 409「未配置企业微信机器人」；关闭成功；未登录 401；响应和日志不含完整 Webhook。
- 覆盖：I-7。

### A-7：双实例与既有操作回归
- 前置条件：两实例连接同一测试库及记录请求时刻的 HTTP stub，准备多封新信。
- 操作步骤：连续收信；执行既有刷新、批量发送测试流程；结束持有通知租约的实例。
- 预期结果：通知请求间隔至少 5 秒，无并发重复领取；另一实例可在租约到期后恢复；刷新、批量发送的原结果不变。
- 覆盖：I-5/I-6，X5；既有按钮与发送流程回归。
