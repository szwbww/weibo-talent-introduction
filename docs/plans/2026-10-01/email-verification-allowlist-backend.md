# Emailable 放行结果：执行与审计（第 1/3 计划）

顺序：本计划 → email-verification-allowlist-config.md → email-verification-allowlist-frontend.md。当前仅写计划，未改业务代码。源码基线：2026-10-01。逐字源码、检索回执见 [证据附件](email-verification-allowlist-evidence.md)。

## 需求描述

手动执行 API 的介绍邮件快照可指定允许发送的 Emailable 结果：deliverable、risky、unknown 独立多选，空数组表示全部跳过；undeliverable 永远不能放行。缺字段的旧快照仍维持当前三态放行。验证明细保留供应商原状态，跨执行复用时按本次选择重新决策。

必须保持：关闭验证时零验证请求；undeliverable 跳过并打「邮箱异常」标签；验证异常/取消/发送前预占/发送配额与节奏不变；历史不可投递预筛选只按最近有效供应商状态工作。范围外：配置表持久化（第 2 计划）、前端（第 3 计划）、材料提醒、回复邮件、供应商接入方式、历史明细回填。

## 关键不变量

### Invariant I-1：快照严格白名单
- Rule：BatchExecutionSnapshot.emailVerificationAllowedStates: List<String>? = null。null 仅代表旧请求的 deliverable/risky/unknown 全放行；非 null 数组可以为空，但元素仅可精确为上述三值；undeliverable、未知值、非字符串元素在启动前拒绝。快照写入 task_execution.request_payload 后，本次不读取可变配置。
- Applies to：BatchExecutionSnapshot 构造/JSON 绑定、BatchSendControlService.startManual/startScheduled 的既有快照入口、ManualInitialOutreachService.run。
- Violation consequence：伪造请求放行不可投递地址或在途任务行为漂移。
- 来源：K-batch-task-config-snapshot-log-identity；BatchExecutionModels.kt:12-53。

### Invariant I-2：供应商事实不是放行决定
- Rule：明确供应商结果仍原样存 provider_state/provider_reason/checked_at；每次按本次快照决定 PASS/SKIP。内存复用与跨执行复用都只复用供应商事实，不沿用旧 decision。undeliverable 恒 SKIP；未选中的其他三态为 SKIP；非法供应商状态仍 ERROR。
- Applies to：BatchEmailVerificationService.verify 的新请求、同执行复用、仓储复用与 conclude；BatchEmailVerificationRepository.recordDecision/recordReusedDecision。
- Violation consequence：改策略后复用旧 PASS 导致错发，或复用旧 SKIP 导致误跳。
- 来源：BatchEmailVerificationService.kt:117-156,282-303；K-batch-verification-latest-result-retention。

### Invariant I-3：策略跳过不打异常标签
- Rule：未选中结果写 decision=SKIP、send_status=SKIPPED、send_reason=EMAIL_VERIFICATION_POLICY_SKIP、tag_status=NOT_REQUIRED；不调用 appendEmailAbnormalTag，不建/绑定 contact，不调 SMTP，不占成功数/发件账号量，仅增加 processed/skipped，继续扫描补足本轮成功数。undeliverable 继续写 EMAIL_VERIFICATION_REJECTED 并打标签；审计写失败继续停止整次执行。
- Applies to：BatchEmailVerificationService.conclude、ManualInitialOutreachService.runIntroductionFromSnapshot、BatchOutcomeReasonCodes。
- Violation consequence：正常邮箱被永久标记异常，或跳过结果越过发信门禁。
- 来源：BatchEmailVerificationService.kt:182-208；ManualInitialOutreachService.kt:757-845。

### Invariant I-4：明确结果一年内可正确复用
- Rule：原始 SKIP deliverable/risky/unknown/undeliverable 与原始 PASS deliverable/risky/unknown 都是有效供应商事实；查询先选同地址最新有效原始行再按本次策略决策。90 天执行清理不得级联删除一年有效窗口内这些原始行；复用行不延长保留。
- Applies to：BatchEmailVerificationRepository.FIND_REUSABLE_BY_EMAILS_SQL、TaskExecutionRepository.deleteOlderThan、BatchEmailVerificationService.findKnownUndeliverableEmails。
- Violation consequence：取消 deliverable 后它的验证结果无法复用或提前丢失，旧 undeliverable 重新成为最新。
- 来源：K-batch-verification-latest-result-retention；BatchEmailVerificationRepository.kt:209-228；TaskExecutionRepository.kt:178-193。

### Invariant I-5：既有发信守卫不变
- Rule：emailVerificationEnabled=false 时不创建验证上下文，不写明细、不请求供应商；材料提醒开启验证仍拒绝；未完成/超时仍按单邮箱暂缓，鉴权/额度/限流/服务故障仍停止；发送前 PASS+NOT_SENT → SENDING 的条件预占保持。
- Applies to：ManualInitialOutreachService.run/runIntroductionFromSnapshot、BatchEmailVerificationService.verify、BatchEmailVerificationRepository.markSending。
- Violation consequence：额外消耗额度、误发或错误重试。
- 来源：ManualInitialOutreachService.kt:171-185,571-579,801-845；BatchEmailVerificationRepository.kt:115-121。

## 现状审计

### task_execution.request_payload / 执行快照
- Schema：V4__create_task_execution.sql:6 的 request_payload 为 TEXT；TaskExecution.kt:23 与 V73 允许 batch_config_id 为 null；BatchExecutionModels.kt:12-53 定义快照/手动请求，当前仅有 emailVerificationEnabled 开关。
- Write paths：BatchSendControlService.kt:57-96,100-109,112-167,258,356、585-591 将定时、按配置手动、独立手动、旧入口快照交给 TaskExecutionService；TaskExecutionService.kt:152,231 的 toJson(request) 写 request_payload。检索回执：rg -n 'requestPayload|request_payload|toExecutionSnapshot' src/main/kotlin/com/weibo/talentintroduction/{campaign,task,mail}。
- Read paths：ManualInitialOutreachService.run 消费启动快照；BatchSendConfigController.kt:223-240 仅从历史 payload 读 emailVerificationEnabled 供明细显示；TaskExecutionService.getExecution 返回历史 payload。
- Interaction points：手动请求写快照 → 执行服务读；旧无字段 payload → 默认旧策略。（来源：K-batch-task-config-snapshot-log-identity）

### batch_email_verification
- Schema：V138__create_batch_email_verification.sql:39-63 规定唯一键、decision/provider_state/send_reason/tag_status 及执行外键级联；V140__reuse_batch_email_verification.sql 有 reused_from_id 与邮箱时间索引。本计划不改表。
- Write paths：BatchEmailVerificationRepository.kt:29-76,99-121 的 insertPending、recordDecision、recordReusedDecision、recordTag、recordSend、markSending；调用者 BatchEmailVerificationService.kt:102-225、ManualInitialOutreachService.kt:805,1002,1285-1293；TaskExecutionRepository.kt:178-193 删除旧执行时外键级联。检索回执：rg -n 'batch_email_verification|insertPending|recordDecision|recordReusedDecision|recordTag|recordSend|markSending' src/main/kotlin/com/weibo/talentintroduction/{campaign,task,mail}。
- Read paths：BatchEmailVerificationRepository.kt:78-96,123-150,209-282 查复用/分页/汇总；BatchEmailVerificationService.kt:80-96,117-125 查历史供应商状态；BatchSendConfigController.kt:180-261 读执行明细；TaskExecutionRepository.kt:183-189 读原始行作为清理保留条件。
- 现有缺口：FIND_REUSABLE_BY_EMAILS_SQL 仅接受 SKIP undeliverable/risky/unknown；deleteOlderThan 保留谓词同样漏 SKIP deliverable。必须两处对称增补，不能只改决策服务。（来源：K-batch-verification-latest-result-retention）
- Interaction points：验证行写入 → 同次/跨次复用；新 SKIP deliverable 写入 → 清理保留 → 一年内最新结果；明细行写入 → 现有详情接口/汇总。

### ES 标签副作用边界（不改 schema/标签 API）
- mapping：src/main/resources/es/orcid_info_raw.json:45、orcid_info_candidate.json:47、orcid_info_application.json:57 的 tags 均为 keyword。
- 该流程现有写路径：BatchEmailVerificationService.appendEmailAbnormalTag:310-334 校验真实 docId/ORCID/当前邮箱后，调用 ExpertIndexWriterService.addTag:637-652，脚本仅在不存在时追加。读路径：同方法 findByDocumentIds；专家页面/查询读取既有 tags。此计划仅限制调用条件，保持 undeliverable 原写法，策略 skip 零调用；不新增标签、不删除历史标签、不修改其它索引写入路径。
- Interaction point：策略决定→是否调用标签 writer→专家详情；A-2/A-3 覆盖。

### 研发边界
- Material reminder 有独立循环（ManualInitialOutreachService.kt:179-184,187-447），本次只改 introduction 验证分支，不改它的节奏。（来源：K-batch-send-round-loop-symmetry）
- 历史不可投递预筛选取 providerState==undeliverable（BatchEmailVerificationService.kt:80-96），与本次 PASS/SKIP 无关；增加 SKIP deliverable 后仍须如此。
- 计数事实：ManualInitialOutreachService.kt:683-687 内层是 roundPassed < roundQuota，:788-799 的拒绝只加 processed/rejected；测试 :6141-6175 已证明跳过三个仍补足成功两封。注释 BatchExecutionModels.kt:268-270 的“占处理槽”不能当限额依据；本次修正该误导注释，不改循环。
- 当前测试：BatchEmailVerificationServiceTest.kt 的 FakeVerifyClient 可无网络模拟四态；ManualInitialOutreachServiceTest.kt:5827+,6333+ 有发送循环与跳过 fixture；BatchEmailVerificationRepositoryIT.kt:295-429 有 MySQL 复用/清理集成；TaskRetentionMigrationTest.kt:59-75 断言清理 SQL。

## 实现方案

### T-1：快照和原因码（I-1、I-3、I-5）
- 文件：src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt。
- 在同文件定义允许值与严格校验；快照 init 调用校验，确保直接构造/JSON 绑定都在启动前拒绝非法值；run 入口防御性复核且将有效集合复制给本次执行，不读取配置服务。快照新增 nullable 列表，默认 null 兼容旧 JSON。非字符串 JSON 元素需经请求绑定或校验拒绝，不能被静默转成允许值。增加 EMAIL_VERIFICATION_POLICY_SKIP 及中文标签。手动 API 通过既有 ManualBatchExecutionRequest.snapshot 消费；TaskExecutionService 不需新写路径。

### T-2：按本次策略作决定（I-2、I-3、I-5）
- 文件：src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt。
- verify 增加第三个可选参数 allowedStates: List<String>? = null；现有直接调用不传时兼容三态全放行。引擎显式传 snapshot.emailVerificationAllowedStates；相关 Mockito stub/verify 必须同步第三参。请求/复用均从 providerState 重新算 decision，复用行保留 sourceRowId/原 checkedAt。仅在供应商返回已通过协议/邮箱回显校验的明确结果时应用列表；HTTP/超时/坏响应的 ERROR 不得按其残留 providerState 改为 PASS/SKIP。比较 state 使用现有 lowercase(Locale.ROOT)，审计保留原值。conclude 接收供应商 state 或等价明确分类来区分拒绝来源。将 PolicySkipped 与 Rejected(undeliverable) 分开；前者只写 SKIPPED/POLICY_SKIP，后者维持标签处理。引擎将 PolicySkipped 计为 skipped/本轮 processed（不消耗 roundPassed 配额），且不走 contact/选号/SMTP。旧未传列表按三态全放行。

### T-3：复用及清理对称（I-2、I-4）
- 文件：src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt；src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt。
- 两处有效原始行谓词的 SKIP 集合增加 deliverable；仍要求 request_count>0、reused_from_id IS NULL、error_code IS NULL 和一年窗口。不得把 ERROR/复用行当原始事实。

### T-4：针对性测试（I-1～I-5）
- 文件：src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt；src/test/kotlin/com/weibo/talentintroduction/task/service/TaskRetentionMigrationTest.kt。
- 覆盖四态 × null/空/指定列表、旧 PASS→本次 SKIP 与旧 SKIP→本次 PASS、同次与跨次复用、错误/取消、无标签/无 SMTP、SKIP deliverable 最近行和 90 天清理保护。测试只断言可观察状态，不复制内部 decision helper 实现。

## 变更文件清单

| 序号 | 文件 | 用途 |
|---|---|---|
| 1 | src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt | 快照/校验/原因码 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt | 本次决策 |
| 3 | src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt | 策略跳过 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt | 复用谓词 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt | 保留谓词 |
| 6 | src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt | 决策测试 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | 发信隔离测试 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt | 快照/非法列表测试 |
| 9 | src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt | 复用/清理集成 |
| 10 | src/test/kotlin/com/weibo/talentintroduction/task/service/TaskRetentionMigrationTest.kt | 清理 SQL 契约 |

本计划 10 文件；两个子系统：campaign 执行/审计、task 清理。完成后手动 API 已可独立使用；第 2 计划才加可保存任务配置。

## 验收标准

- I-1：对快照 null、[]、[deliverable]、[risky,unknown]、含 undeliverable/未知值/非字符串逐项断言；非法请求在 task_execution/验证明细写入前 4xx。
- I-2：新请求与两种复用路径分别验证旧 PASS→新 SKIP、旧 SKIP→新 PASS；providerState、checkedAt、reusedFromId 不失真；同地址不增加物理请求。
- I-3：策略跳过行的四个实值为 SKIP/SKIPPED/EMAIL_VERIFICATION_POLICY_SKIP/NOT_REQUIRED，ES 加标签、contact、SMTP 均零调用；undeliverable 原路径不变。
- I-4：MySQL IT 构造较旧 undeliverable 与较新 SKIP deliverable，findReusable 必须返回后者；90 天删除保留一年内原始 SKIP deliverable，过期行可删。对清理 SQL 文本断言同步；同秒并列按 id 较大者胜出，ERROR/未来时间/复用行不取。
- I-5：验证关闭/材料提醒/超时/全局故障/取消/markSending 回归，计数与原测试一致。
- 命令：JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest,TaskRetentionMigrationTest test；MySQL IT 命令：JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=BatchEmailVerificationRepositoryIT test（Testcontainers 需要 Docker；pom.xml:173-177,246-250 已核对）；最后 mvn test。若 MySQL 未提供，明确记录 IT 未验证，不声称通过。

## 人工验收清单

### A-1：手动快照放行
- 前置条件：隔离预发环境；准备一条未联系的介绍邮件候选人及安全收件邮箱，Emailable 返回 deliverable，SMTP 指向测试收件箱；使用现有 POST /api/mail/batch-send/manual-executions。
- 操作步骤：1. 快照设 emailVerificationEnabled=true、emailVerificationAllowedStates=[deliverable] 并启动。2. 查询 GET /api/mail/batch-send/executions/{executionId}/email-verifications。
- 预期结果：该行 providerState=deliverable、decision=PASS；收到且仅收到 1 封测试邮件。
- 覆盖：I-1、I-2、I-5；手动请求→快照→执行→明细。

### A-2：策略跳过与标签
- 前置条件：隔离预发环境准备 1 条 Emailable 返回 risky 的未联系候选人；关闭其他候选人，SMTP 指向测试箱；能查看专家标签/验证明细。
- 操作步骤：1. 用列表 [deliverable] 启动介绍邮件。2. 打开该次邮箱验证明细和专家标签。
- 预期结果：decision=SKIP、sendStatus=SKIPPED、sendReason=EMAIL_VERIFICATION_POLICY_SKIP、tagStatus=NOT_REQUIRED；收件箱 0 封；专家没有新增「邮箱异常」。
- 覆盖：I-2、I-3；验证写入→详情读取。

### A-3：不可投递固定拒绝
- 前置条件：隔离预发环境准备 1 条 Emailable 返回 undeliverable 的未联系候选人；可查看专家标签。
- 操作步骤：1. 用列表 [deliverable,risky,unknown] 启动。2. 查验证明细及标签。
- 预期结果：decision=SKIP、sendReason=EMAIL_VERIFICATION_REJECTED、收件箱 0 封；专家新增「邮箱异常」（若 ES 写入失败则明细 tagStatus=FAILED 且仍不发）。
- 覆盖：I-1、I-3；原有 undeliverable 回归。

### A-4：关闭验证与异常回归
- 前置条件：隔离预发环境准备 1 条可发送候选人；另用测试替身模拟一次 Emailable 超时；可看请求计数/验证明细。
- 操作步骤：1. 关闭 emailVerificationEnabled 执行。2. 再开启并让验证超时执行。
- 预期结果：第 1 次验证明细 0 条且供应商请求 0 次；第 2 次该邮箱未发送，明细 errorCode=EMAIL_VERIFY_TIMEOUT、sendReason=EMAIL_VERIFICATION_DEFERRED；没有新增「邮箱异常」。
- 覆盖：I-5；关闭/异常回归。

### A-5：策略变更后的复用与保留
- 前置条件：隔离预发 MySQL 中同一测试邮箱已有一年内有效原始 PASS risky 记录；准备新执行且测试收件箱可见。
- 操作步骤：1. 新快照列表 [deliverable] 启动。2. 查新明细 reusedFromId、requestCount、checkedAt；运行现有 90 天清理任务，再查原行。
- 预期结果：新行 providerState=risky、decision=SKIP、requestCount=0、reusedFromId 指向原行、checkedAt 与原行一致；新执行 0 封；一年内原行仍存在。
- 覆盖：I-2、I-4；历史读取→本次写入→清理读取。


### A-6：空列表与旧请求兼容
- 前置条件：隔离预发一条有一年内 deliverable 原始记录的未联系候选人，验证开启，SMTP 指向测试箱；其他发送门禁可通过。
- 操作步骤：1. 手动快照显式 [] 执行 X。2. 对相同未联系候选人，用省略该字段的旧格式请求执行 Y。
- 预期结果：X 的 decision=SKIP、sendReason=EMAIL_VERIFICATION_POLICY_SKIP、成功 0；Y 的 decision=PASS、复用原检查时间、成功 1；X 不新增邮箱异常标签。
- 覆盖：I-1、I-2、I-3；空/缺省与跨次复用。

### A-7：非法选择与材料提醒边界
- 前置条件：隔离预发可使用现有手动 API；记录当前执行数量；准备合法材料提醒快照。
- 操作步骤：1. 请求列表 [undeliverable]。2. 请求未知列表值 [invalid]。3. 材料提醒快照开启 emailVerificationEnabled=true 请求执行。
- 预期结果：均返回 4xx，task_execution 未新增，SMTP 0 次；允许选择 unknown 与非法协议值 invalid 不能混为一类。
- 覆盖：I-1、I-5；启动前拒绝。
