# 01：服务端草稿持久化与 API

状态：待评审，未实施。研究日期：2026-10-08。代码基线：`7c86599f85f6462e00a3fcd2c5a74f1ca57f013d`。

本文中的“现状”有仓库证据；表结构、API、800ms 防抖、1MiB 文本上限为本计划提出的设计值，不能当作已经存在的功能。证据目录：[源码摘录](mailbox-server-drafts-evidence/source-excerpts.md)、[文件 SHA256](mailbox-server-drafts-evidence/source-manifest.json)。

## 需求描述

O-1：登录用户可保存、列出、恢复自己的人工回复草稿；数据库持久化，不依赖浏览器缓存。
O-2：显式放弃才清除正文；普通保存、空正文保存、多设备冲突、迟到请求均不能隐式删除或复活草稿。

必须保留 N-1：草稿 CRUD 不写 mail_record、mail_send_attempt、inbound_mail_processing、expert_contact、ES 或附件文件；不发邮件。N-2：既有认证与附件归属校验不放宽。

不做：发送接入（02 负责）、UI（03 负责）、定时清理、草稿历史、跨用户共享、迁移预览 localStorage。

## 关键不变量

### Invariant I-1: 身份与目标唯一
- Rule: username 只取 AUTH_USERNAME Session。id 为服务端生成 BIGINT；唯一键为 (username, expert_contact_id, target_kind, inbound_processing_id, account_scope)。INBOUND 必须使用真实 processingId>0 与该来信真实账号；OUTBOUND 的 processingId 固定 0，scope 为已验证账号或空串表示全部。禁止伪造来信 ID；同用户同目标最多一行。
- Applies to: 全部 GET/PUT/DELETE 与 repository 查询
- Violation consequence: 串账号、串专家、同目标重复稿。
- 来源: K-mailbox-draft-cache-owner-capture

### Invariant I-2: 版本与状态
- Rule: version 从 1 开始，任何内容保存/重开/关闭均 +1，不重置。state 仅 ACTIVE/SENT/DISCARDED。ACTIVE 即有效稿，包括用户已主动编辑后清空的稿；SENT/DISCARDED 是无正文防重放标记，subject/html_body/text_body/context_json 置 NULL。无行与终态不能混为一谈。初次只看默认主题不建稿；接口允许用户真实编辑后保存空内容。
- Applies to: 创建、CAS 更新、放弃、后续发送关闭、显式重开
- Violation consequence: 旧自动保存把已放弃/发送的稿复活，或误删空稿。
- 来源: original

### Invariant I-3: 仅两种清理
- Rule: 只有用户 DELETE 放弃与 02 的已确认发送成功可清除内容。不因时间、退出、切换、收到来信、联系人的业务状态改变清稿；无 TTL、删除任务、contact ON DELETE CASCADE。原专家不存在时保留且可读/放弃，禁止发送。
- Applies to: repository 全写点、列表 LEFT JOIN、discard
- Violation consequence: 违反持久保存承诺。
- 来源: original

### Invariant I-4: CAS 与保存响应
- Rule: PUT 必须带 expectedVersion，0 只创建缺失行；ACTIVE 更新匹配 id/owner/version。终态仅允许显式 reopen=true 且精确匹配当前终态版本；普通自动保存永不设置 reopen。竞争/迟到更新返回 409，不 last-write-wins，不自动重试覆盖。响应返回服务端 version/updatedAt/state。
- Applies to: 初次创建唯一键竞争、保存、重开、放弃
- Violation consequence: 多窗口互相覆盖或迟到保存复活。
- 来源: original

### Invariant I-5: 内容快照边界
- Rule: subject 上限 255 字符；subject+html+text+序列化 context 的 UTF-8 合计≤1MiB（设计值），超限 413 不截断。context_json 固定 schemaVersion=1，仅 typed 字段：qa、requestId、meeting、meetingAccountCode、followUpAnchorMailRecordId、outboundAttachmentDraft。禁止持久化 File/Blob/DOM/控制器/任意 downloadUrl。读取不执行 HTML，03 清洗后显示。
- Applies to: 保存校验、JSON 编解码、详情读取
- Violation consequence: 恢复失败、XSS、隐形丢格式或超大数据。
- 来源: original

### Invariant I-6: 发送关联
- Rule: send_attempt_id/send_version 必须同为空或同非空，send_version 是绑定时的内容版本；保存较新内容不能改掉绑定。关联只由 02 在发送事务中写；读取返回现有 attempt 状态，不复制一套新状态机。created_at 首创后不变，updated_at 取服务端时间；发送状态更新不得伪装成内容保存。
- Applies to: 新表字段、内部事务接口、查询返回
- Violation consequence: 刷新后失去发送状态，未知结果被重复发送。
- 来源: original

### Invariant I-7: 只写草稿表
- Rule: 草稿保存允许未完成正文/缺失可恢复上下文，不能要求达到发送标准；仍验证存在目标归属、附件 id 的专家/用户归属。ready 附件通过现有 loadSnapshots 取得元数据；失败/上传中只存可序列化描述，不读文件字节、不重传。实际发送由原发送验证。
- Applies to: CRUD、附件元数据、目标读取
- Violation consequence: 输入时误发、改变业务状态或扩大附件访问权限。
- 来源: K-inbound-processing-write-paths

## 现状审计

### 新草稿表
- Schema/mapping：当前没有 `mailbox_reply_draft` 或 `MailReplyDraft`，见 [检索退出码 1](mailbox-server-drafts-evidence/new-store-search.txt)；这是新增存储，不是 mail_record 增字段。
- 当前写/读路径：上述检索范围 `src/main`、`src/test` 无命中。拟新增写点仅 create/update/reopen/discard/bind/close；拟新增读点为 target/detail/list/summaries 与 02 发送校验。
- IP-1：保存 → 列表/详情/第二设备恢复；IP-2：放弃/成功终态 → 迟到保存；IP-3：目标与附件写入现有存储 → 新草稿引用恢复。

### 现有身份、目标与附件（只读依赖）
- 认证：`auth/config/AuthSessionKeys.kt` 的 USERNAME；`AuthWebConfig.kt` 保护 /api/**；`V25__create_admin_user.sql` username VARCHAR(64)。controller 必须像 `MailboxConversationController.kt:310` 一样无 Session 返回 401，不能信 operatorName。
- 联系人与来信：已有 `expert_contact` / `inbound_mail_processing` 主键及所属专家、accountCode。读现有 repository 校验目标；只读，禁止追加处理状态写点。（来源: K-inbound-processing-write-paths）
- 附件 schema：`V126__create_outbound_mail_attachment.sql`，id CHAR(36)、expert_contact_id、created_by、元数据及磁盘路径；附件上传后内容不可变。
- 附件写点：`OutboundAttachmentService.upload`（临时文件→原子移动→repository.insert，失败清理自身新文件）；repository 无常规更新/删除 API。完整引用与迁移见 [附件取证](mailbox-server-drafts-evidence/attachment-store-references.txt)。
- 附件读点：同 service 的 `resolveDraftDownload`、`resolveForSend`、`loadSnapshots`、`resolveForMessageDownload`。草稿只复用 `loadSnapshots` 的身份/元数据规则；send 仍调用 resolveForSend 校验真实文件、数量与体积。既有限制 10 个、单个 10MiB、总计 20MiB，来自 `OutboundAttachmentModels.kt`。
- IP-3 特别规则：草稿移除附件只移除引用；不做附件文件清理项目。联系人被删除的草稿用 LEFT JOIN 显示“原专家已不存在”，保留内容、禁发送、允许显式放弃。此无级联设计是用户清理规则的实现选择。

### 邮件存储与数据库门禁
- 本步不写 mail_record/mail_send_attempt。其引用收据见 [mail-store-references](mailbox-server-drafts-evidence/mail-store-references.txt)，02 单独审计成功写点。
- `FlywayMigrationIntegrationTest.kt` 实际使用 mysql:8.0.36，存在多个 targetSchemaVersion="150"；当前 migration 目录已有 V151。只更新“迁移到最新”的断言，历史 target("旧号") 用例保留其历史语义。
- 历史生产 MySQL5.7 兼容事实来自 K-mailbox-groupwise-latest-mysql-compat；不用 Testcontainers8 的通过推断生产可用窗口函数。

## 实现方案

### T-1：新增专用表和 JDBC repository（I-1～I-7）
文件：`src/main/resources/db/migration/V152__create_mailbox_reply_draft.sql`、`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt`、`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftModels.kt`。

新增表字段如下；这是完整 schema 合同，不对既有表加列：

| 字段 | 类型/约束 | 含义/不变量 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT PK | 服务端 ID，I-1 |
| username | VARCHAR(64) NOT NULL | Session owner，I-1 |
| expert_contact_id | BIGINT NOT NULL | 真实联系人 id，I-1/I-3 |
| target_kind | VARCHAR(8) NOT NULL | INBOUND / OUTBOUND，I-1 |
| inbound_processing_id | BIGINT NOT NULL | OUTBOUND=0，INBOUND>0，I-1 |
| account_scope | VARCHAR(100) NOT NULL DEFAULT '' | 规范化目标 scope，I-1 |
| version | BIGINT NOT NULL | 单调 CAS，I-2/I-4 |
| state | VARCHAR(16) NOT NULL | ACTIVE/SENT/DISCARDED，I-2 |
| subject | VARCHAR(255) NULL | ACTIVE 可为空字符串；终态 NULL，I-2/I-5 |
| html_body / text_body | LONGTEXT NULL | 未渲染正文与纯文本，I-2/I-5 |
| context_json | LONGTEXT NULL | schemaVersion=1 typed JSON，I-5 |
| send_attempt_id / send_version | BIGINT NULL | 发送关联，I-6 |
| created_at / updated_at | DATETIME(3) NOT NULL | 服务端时间，I-6 |

唯一键采用 I-1 五列；另建 `(username,state,updated_at,id)` 列表索引。目标键字符串用区分大小写 collation，username 按 Session 规范值；禁止依赖 MySQL5.7 不执行的 CHECK 约束，枚举/组合字段在 service 校验。表用项目现有 InnoDB/utf8mb4 风格；不加 contact 删除级联或阻断原删除操作的外键。列表无窗口函数/CTE；LIKE 参数转义 `%`、`_`，参数化 SQL，不拼接用户输入。

显式重开终态时清空旧 send_attempt_id/send_version，建立新编辑生命周期；这是用户已明确重新编辑后的操作，绝不由迟到保存触发。SENT/DISCARDED 的无正文标记不在草稿列表显示。

版本实现：创建时 INSERT（冲突映射409），更新/关闭用 `WHERE id=? AND username=? AND version=? AND state=?`；更新命中 0 行后按 owner 重读区分 404/409。不提供 deleteAll 或按 contact 的清稿函数。

Models 同步定义 `MailReplyDraftRef(id:Long,version:Long)` 与内部 `ValidatedDraftSendRef`（owner/id/version/target/原始发送投影）；这些类型不是新增表字段，不接受客户端传 owner。

为 02 预留内部事务方法：锁定 owner 草稿、绑定 attempt、按 send_version 关闭、释放完成绑定；要求调用方已有事务（MANDATORY），不对外开放 SENT 写接口。方法实现与测试限于草稿表行为；02 决定何时调用。

### T-2：保存/读取/放弃 API（I-1～I-7）
文件：`MailReplyDraftModels.kt`、`MailReplyDraftService.kt`、`MailReplyDraftController.kt`（完整包路径见文件表）。

统一前缀 `/api/mail/mailbox/drafts`：

| API | 请求/返回合同 |
|---|---|
| GET /target | `contactId,kind,processingId,accountScope`；缺失返回 `{id:null,version:0,state:null}`；存在返回详情，包括终态的 id/version，不泄露正文给其他 owner |
| PUT /target | target + `{expectedVersion,reopen:false,content:{subject,html,text,context}}`；缺失只能 expectedVersion=0；成功返回完整规范快照/id/version/state/updatedAt |
| GET /{id} | 当前 owner 详情；不存在或他人 id 统一404；终态返回标记而非伪造404 |
| DELETE /{id}?expectedVersion=N | 明确放弃 CAS；ACTIVE→DISCARDED，清正文并 version+1；同终态同版本可返回该终态；不允许删除别人的稿 |
| GET / | `search,accountScope,page=0,size=20`；size 1..100；ACTIVE，按 updated_at DESC,id DESC 分页，返回 items/total/page/size；LEFT JOIN 联系人，搜索姓名/邮箱/主题；不返回 HTML 正文，只返回纯文本 preview≤120字符 |
| GET /summaries | `contactIds` 去重后≤100，`accountScope`；返回各 contact 的 ACTIVE 数量与该 scope 下用户草稿总数；不依赖当前邮件列表分页计算总数 |

scope 筛选：传账号时，INBOUND 匹配目标账号、OUTBOUND 匹配保存 scope；未传时取该用户全部稿。OUTBOUND 的空 scope 草稿只出现在全部账号视图；点草稿按保存的 scope 打开，不能套用当前 UI scope。草稿入口不继承“待处理/挂起/收发方向/日期”等邮件筛选，保留搜索和账号范围，并在 UI 提示（03）。

context 字段精确来源：`saveDraftFromInputs:5979`、`snapshotQa:6034`、`writeDraftWithMeeting:7641`。qa 包含 ragFactCodes/ragCorpusFingerprint/baselineText；requestId 保留已有 conversation UUID；meeting 保存 input、preview（htmlBody/textBody/attachment 的 filename/contentType/icsText/byteLength/sha256/semanticSha256、startUtc/endUtc/meetingTime/chinaTime/durationMinutes）、blockHtml/blockText/state/revision；meetingAccountCode 单列在 context；跟进 anchor 可空。未知 schemaVersion 返回422，不悄悄丢字段。JSON 以数据库 LONGTEXT+显式 codec 储存，避免依赖数据库 JSON 类型行为。

附件 context 只含 items 的本地唯一 key、state、id（若已上传）、filename/contentType/byteLength/sha256、error；外层保留 revision，字段来源为 `uploadOutboundFile:5819` / `failOutboundItem:5890`，mapper 显式列出字段，不 spread 任意客户端对象。服务端重新生成 ready 附件下载地址；uploading 恢复为 failed，文案“上传未完成，请重新选择文件”。不存 File、blob URL、绝对路径或授权信息。首次提交不存在/越权附件 id 拒绝；既有稿恢复时附件后来缺失，保留正文与缺失条目，标为不可发送，不丢整份稿。

常规错误：401 未登录；404 非本人/不存在；409 `DRAFT_VERSION_CONFLICT` 或 `DRAFT_CLOSED`；422 非法目标/快照；413 超限。controller 内用 ResponseStatusException/既有响应结构，不新建全局异常体系。409 返回当前版本/状态但不自动覆盖本地；接口响应是否 envelope 按项目现有 api() 读取契约实测固定。

### T-3：测试与迁移门禁（I-1～I-7）
文件：文件表中的四个测试文件。service/controller 测试负责身份、快照、边界；repository IT 用真实 MySQL 覆盖并发 CAS/唯一键/关闭后迟到保存；Flyway 测试覆盖新表及最新版本，不改历史迁移。读者为 03 UI 与 02 发送逻辑；不修改现有邮件列表聚合 SQL。

## 变更文件清单

| 序号 | 精确路径 | 改动 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V152__create_mailbox_reply_draft.sql` | 新增表与索引 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftModels.kt` | 快照/请求/响应/状态定义 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt` | 参数化查询与 CAS/事务内部操作 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt` | 目标、身份、快照、版本校验 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailReplyDraftController.kt` | 上述 API |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftServiceTest.kt` | 保存/终态/归属单测 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailReplyDraftControllerTest.kt` | Session 与 API 合同测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt` | 真实 MySQL 并发与状态测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 最新迁移与 schema 断言 |

共 9 文件，1 子系统；现有共享表加列 0。不得为本步改动附件 service、认证配置、邮件聚合或发送指纹。

## 验收标准

- I-1：用户 A/B、同专家不同来信/账号/OUTBOUND scope 分离；伪造 owner 被忽略/拒绝；并发首次保存只有一行，另一方409。
- I-2：首次 version=1，保存=2，放弃=3；终态正文四列为 NULL；普通 PUT(expectedVersion=2) 不复活；显式 reopen(expectedVersion=3)=4；已存在稿保存空正文仍 ACTIVE。
- I-3：模拟 updated_at 早于一年、收到新来信、删除原专家后，原稿仍可 GET；业务无自动草稿 DELETE/CASCADE/TTL；放弃仅当前 id。
- I-4：两个事务同 expectedVersion 更新只能一方成功；终态旧请求409。丢失成功响应后重读比对相同快照可确认已保存，但不得直接换新版本重放不同本地数据。
- I-5：Unicode UTF-8 上限、未知 schemaVersion、主题255/256、富文本、会议/RAG/跟进上下文往返；超限不部分写。
- I-6：createdAt 不变；绑定字段成对且客户端不可赋值；较新内容保存保留原绑定；内部更新必须已有事务。
- I-7：mock 验证业务写方法/SMTP 从未调用；真实 MySQL 前后业务记录数与状态不变；附件别人的 id 被拒绝，缺失附件不吞正文。
- 运行：使用项目 JDK11；`mvn -Dtest=MailReplyDraftServiceTest,MailReplyDraftControllerTest test`；`mvn -Pmysql-it -Dtest=MailReplyDraftRepositoryIT test`；`mvn -Pmigration-it -Dtest=FlywayMigrationIntegrationTest test`。现有 pom 的 node 检查亦需保留；没有 MySQL/Docker 时记录 BLOCKED，不能把 skipped 记为 PASS。

## 人工验收清单

### A-1: 跨会话服务端恢复
- 前置条件: 测试环境新建两个账号 A/B；选择已有真实来信的测试专家，记录 contactId/processingId/accountCode。可用浏览器开发者工具带当前 Session 调用上述 API。
- 操作步骤: 1. A PUT 主题“草稿01”、正文“跨设备保留”，expectedVersion=0。2. 记下返回 id。3. A 退出再登录，在另一浏览器 GET id。4. B GET 同 id。
- 预期结果: A 返回 ACTIVE/version=1 与原文；B 为404；邮件时间线不新增记录。
- 覆盖: O-1、N-1/N-2、I-1/I-5/I-7、IP-1/IP-3

### A-2: 冲突与终态防复活
- 前置条件: A-1 的 ACTIVE version=1；两个窗口均已 GET 该稿。
- 操作步骤: 1. 窗口一以 version=1 改正文“版本二”。2. 窗口二以 version=1 改“旧窗口”。3. DELETE expectedVersion=2。4. 重放步骤二。5. GET 终态后显式 PUT reopen=true/expectedVersion=3。
- 预期结果: 步骤1返回version=2；步骤2为409；放弃后DISCARDED/version=3、正文空；旧请求仍409；步骤5为ACTIVE/version=4。
- 覆盖: O-2、I-2/I-3/I-4、IP-2

### A-3: 空稿与附件归属
- 前置条件: 准备本人上传附件 ID 和用户 B 上传的同专家附件 ID；记录邮件数量及专家处理状态。
- 操作步骤: 1. 保存本人附件与主题/正文。2. 再把主题/正文清空保存。3. 重读详情。4. 尝试保存 B 的附件。5. 将测试稿 updated_at 调为一年前后重新读取。
- 预期结果: 空内容稿仍 ACTIVE，附件仍在；越权附件拒绝且原稿不变；一年前稿仍返回；邮件数量和专家处理状态不变。
- 覆盖: O-1/O-2、N-1/N-2、I-3/I-5/I-7、IP-3

### A-4: 原目标失效仍保留
- 前置条件: 测试专用专家具有可清理的数据，保存一份草稿；在数据库维护窗口记录草稿id后按测试夹具删除专家及其业务依赖，不删除草稿。
- 操作步骤: 1. GET 草稿列表和详情。2. 尝试发送（02 后验）。3. 明确 DELETE 草稿。
- 预期结果: 列表显示“原专家已不存在”；正文仍可读；不能发送；明确放弃后才变 DISCARDED。
- 覆盖: O-2、I-1/I-3/I-7、IP-3

