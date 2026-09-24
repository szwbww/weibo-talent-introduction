# 01：介绍邮件发送前验证与持久化明细

状态：待开发批准。依赖：无。后续：02 → 03。仅此步部署时，通过现有手动快照 API 显式开启；原界面与定时任务默认关闭。

## 需求描述

1. 手动批量介绍邮件快照可以选择发送前验证；实际进入验证的每个专家/邮箱留下持久化结果。仅通过后进入原发送流程。
2. 明确不通过的邮箱跳过，并追加“邮箱异常”标签；接口故障停止本次执行，区分发送失败、验证失败和标签写入失败。

必须不变：开关关闭时原发送行为；材料提醒行为；退订/抑制、研发类型、绑定账号、模板门禁、发送去重和账号限额；原成功/失败/跳过/剩余的含义；已有专家标签和联系状态。

不做：全库验证、独立验证队列、批量异步回调、跨执行验证缓存、积分预算系统、自动清除异常标签、把 Emailable 结果写入旧 emailVerifiedLevel、全局 SMTP exactly-once 重构、暂停恢复/定时重试系统。

## 关键不变量

### Invariant I-1: 开关范围与缺省
- Rule: BatchExecutionSnapshot 新增 emailVerificationEnabled:Boolean=false。旧 JSON 缺字段=false；只有 INTRODUCTION 可为 true。run 入口先验证类型和配置；false 时不调用新 HTTP/仓储，不要求密钥；MATERIAL_REMINDER+true 在任何业务写入前拒绝。
- Applies to: 手动快照→ManualInitialOutreachService.run；旧入口默认快照
- Violation consequence: 旧任务意外收费、材料提醒走错发送策略。
- 来源: K-batch-snapshot-two-write-entrances（旧结论已修正）

### Invariant I-2: 验证对象等于最终收件地址
- Rule: 邮箱 trim+lowercase(Locale.ROOT) 后作为验证键；不删除 +tag、不合并点号。只有 HTTP 200、返回 email 匹配、state=deliverable 放行。undeliverable/risky/unknown 都跳过；unknown 是供应商明确结果，不等于 HTTP 超时。SMTP 前再断言 normalize(mail.to)==已验证邮箱，地址变化则终止该次执行，不发未验证地址。
- Applies to: 共享介绍邮件引擎；验证 HTTP；compose→SMTP
- Violation consequence: 验证 A 却发送 B，或宽松放行未确认邮箱。
- 来源: original

### Invariant I-3: 发送副作用必须晚于验证
- Rule: 验证位于现有类型/抑制/已绑定检查后，selectSendAccount 前；失败邮箱不新建/绑定 contact、不写 PREPARED、不调用 SMTP、不计账号发送量。已有 contact 保持原值。验证前后及 SMTP 前检查取消。验证通过也仍须经过现有去重/门禁/限额。
- Applies to: ES 新目标与 MySQL NEW 重试目标汇合后的同一循环
- Violation consequence: 验证失败仍占绑定名额，重试目标绕过验证。
- 来源: K-batch-send-filter-retry-parity；K-smtp-idempotency-reservation-before-delivery

### Invariant I-4: 服务异常绝不当邮箱异常
- Rule: 249 最多两次物理请求，第二次仍未完成记 ERROR；402、401/403、429、网络/超时、5xx、非法 JSON/state、邮箱不匹配均记 ERROR，停止当前执行。已成功发送>0 用 PARTIAL_SUCCESS，否则 FAILED；不伪增 SMTP failure/skipped，当前未发及后续目标保留 remaining。不使用 PAUSED。
- Applies to: HTTP 分类→审计→ManualOutreachResult→TaskExecutionService
- Violation consequence: 服务故障批量误标专家，或前端把失败执行显示成功。
- 来源: original

### Invariant I-5: 标签追加、真实 ID 与分层一致
- Rule: 明确非通过结果只追加 tags 中的“邮箱异常”，不改 operatorStatus，不删除其它标签。用 ExpertProfile.esDocId 调用 findByDocumentIds，在 RAW/CANDIDATE/APPLICATION 已存在且真实 ID、ORCID、当前邮箱均匹配的副本上调用现有 addTag；不把 ORCID 假定为 _id，不创建缺失层。无匹配文档/读取异常/任一应写层返回 false 记 tagStatus=FAILED，邮箱仍跳过；全部成功才 APPLIED。异 ID 的其它文档不猜测关联。deliverable 不自动清标签。
- Applies to: 新协调服务→ExpertSearchService/ExpertIndexWriterService；原标签读取接口
- Violation consequence: 错人标签、覆盖标签、标签失败却宣称成功。
- 来源: K-email-invalid-no-existing-seam；K-expert-tag-editor-shared-render-contract

### Invariant I-6: 审计先落库、结果可追溯
- Rule: 新表每个 executionId+规范化ORCID+规范化邮箱唯一；保存邮箱、专家名、结果与时间快照。先插 PENDING，再请求，再写 PASS/SKIP/ERROR，随后才允许发送。审计不可用则停止发送，不静默降级；发送前持久化 SENDING，结果写 SENT/FAILED。崩溃或已发后写库失败保留 SENDING，页面解释“结果未确认”，禁止据此自动重发。取消/门禁/去重/账号不可用等不进 SMTP 的分支保留 NOT_SENT 并写 sendReason。
- Applies to: 新增仓储；共享引擎全部 continue/break/catch/成功分支
- Violation consequence: 漏记录、伪造已发送/未发送、异常被当 SMTP 故障反复发信。
- 来源: K-task-execution-id-is-batch-cohort-key

### Invariant I-7: 成本与统计边界
- Rule: 只验证实际走到验证门禁的目标，预估人数不调用 Emailable。同一 execution 内同邮箱的最终结果可内存复用，但每个专家单独留明细，复用行 requestCount=0；下一次执行重新验证。单邮箱连续249最多2次，物理请求间至少100ms，无并发池。验证跳过计 skipped、processed、roundProcessed/roundRejected，并占现有 roundSent 处理槽；不把它计 success 或账号发送量。remaining=target-success-failure-skipped。
- Applies to: 循环/OutcomeAccumulator/验证上下文
- Violation consequence: 关联系统全量扫描收费、跳过假装已发、为了凑足发信数无限验证。
- 来源: K-batch-send-round-loop-symmetry

### Invariant I-8: 密钥与日志安全
- Rule: EMAILABLE_API_KEY 仅后端环境变量读取，发送 Bearer 到固定 api.emailable.com；空值或 test_ 密钥在开启执行时明确拒绝。不得进前端、请求快照、异常日志、数据库。仅保存允许字段与受控错误码，不保存完整 HTTP 请求/响应。原始供应商 reason 作为数据，不作为 HTML/SQL。
- Applies to: 新服务启动检查、HTTP、审计
- Violation consequence: 密钥泄漏、测试结果被用于真实发信。
- 来源: original

### Invariant I-9: 审计存储与保留
- Rule: 不修改 task_execution/task_progress_log schema，不把每邮箱数组反复塞入 progress.details_json。新表 FK task_execution(id) ON DELETE CASCADE，跟随现有保留期；分页严格按 executionId 与 id 游标查询，默认50、最大100。状态/结果汇总来自新表，而非 errorSamples。
- Applies to: 新迁移/仓储；现有归档清理；后续只读接口
- Violation consequence: 进度日志膨胀、历史明细缺失、跨执行串数据。
- 来源: K-progress-log-per-mail-write-amplification；K-progress-log-batchonly-two-readers

## 现状审计

证据原文与命令见 [emailable-evidence.md](emailable-evidence.md) E-1～E-10/F-1～F-8/F-16。以下路径均以 `src/main/kotlin/com/weibo/talentintroduction/` 为根。

| 存储/边界 | schema 与读写路径 | 交互点 |
|---|---|---|
| 执行快照 | `campaign/domain/BatchExecutionModels.kt:10`；`BatchSendControlService:354` 交 TaskExecutionService 序列化 request_payload。TaskExecutionService:147/226 建执行、仓储 finishOwned 更新结果；TaskExecutionController/Activity/Progress 与 BatchSendConfigController 读取；TaskAuditRetentionService 清理。字段不是“只在内存”。 | X1：输入快照→执行历史，旧 JSON 必须默认关闭。 |
| 进度/汇总 | V4 task_execution 的 request_payload/result_summary 为 TEXT；V22/V35/V102 进度表。TaskProgressStore 写进度；BatchSendConfigController:164 同批只取末条，OutcomeAccumulator:283 错误样本最多20。 | X2：逐邮箱数据不能依靠现有折叠日志；需新表供03读取。 |
| 发送主链 | ManualInitialOutreachService:498 共享介绍邮件循环；1068 retry，1335/1344 ES 目标，602 汇合。621类型之后、623抑制、637绑定、651选号、673contact、689去重、699compose、731attempt、752SMTP、756txHelper；IntroductionMailComposer:39 取 expert.email 作 mail.to。 | X3：新目标/重试→同门禁；每条提前退出也要审计收尾。 |
| 发送现有表 | expert_contact 由主链/txHelper 管理；mail_send_attempt 在发送前 PREPARED；成功沿 txHelper 写 mail_record、contact、账号计数并同步 ES；原失败分支含 EMAIL_INVALID。此计划不增加这些表字段或独立写路径。 | X4：新验证拒绝不调用现有成功/永久 SMTP 失败的状态迁移。 |
| 标签 | 仓库三份 orcid_info_*.json 均 tags:keyword、dynamic:false；这不是现网 mapping 的读取证明。writer:633/651 脚本增删；403/529/599/688 晋级/全量写入；Revalidation:248/255 保留并合并；Discovery:1855/1865/2848 建立并合并。全部导入脚本及读方见E-4分类。 | X5：已存在 copies 的真实_id/邮箱必须对应；查询/标签聚合沿原接口显示；原 import 覆盖语义不扩改。 |
| 旧邮箱检查 | expert/service/EmailValidationService.kt:26 仅格式/临时邮箱/MX；email_validation_cache 为此服务写读。无 Emailable 集成（E-1）。 | 不复用此缓存判定“可投递”，不触碰该表。 |
| 新验证表 | 当前不存在，新建明细表。唯一业务写方新 service/repository；只读方03控制器；删除由执行表 FK 级联。 | X6：保留期删除执行时无孤儿明细。新表不外键关联contact，因验证拒绝不应创建contact。 |

已有 PAUSED 最终常被 ManualOutreachResult.taskFinalStatus:1564 转 SUCCESS（F-4），因此新服务故障不用 PAUSED；保留旧暂停分支。现有通用中断记录不提供该批量发送目标游标恢复。

## 实现方案

### T1 新增明细表及仓储（I-6/I-9）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt`、`src/main/resources/db/migration/V138__create_batch_email_verification.sql`、`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`。V138 当前可用，实施前检查占号。新表 `batch_email_verification`：

| 列 | 类型/缺省 | 合同 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 游标 |
| task_execution_id | BIGINT NOT NULL FK CASCADE | 已持久化的执行ID |
| expert_doc_id / orcid_id | VARCHAR(256) NULL / VARCHAR(128) NOT NULL | 真实文档ID可缺失；规范化业务ID非空；不虚构_id |
| expert_name | VARCHAR(256) NULL | 展示快照；超长可截断名字，不截断身份键 |
| email | VARCHAR(320) NOT NULL | 规范化实际收件地址；超长明确拒绝，不截断 |
| decision | VARCHAR(16) NOT NULL DEFAULT 'PENDING' | PENDING/PASS/SKIP/ERROR |
| provider_state / provider_reason | VARCHAR(32)/VARCHAR(128) NULL | 正常响应状态与原因；未知reason保留为数据 |
| error_code | VARCHAR(64) NULL | 服务/审计错误受控码 |
| request_count | INT NOT NULL DEFAULT 0 | 物理请求次数；复用为0；不是积分余额 |
| checked_at | DATETIME(3) NULL | 返回明确结果/服务错误的北京时间；复用沿原结果时间 |
| send_status | VARCHAR(16) NOT NULL DEFAULT 'NOT_SENT' | NOT_SENT/SENDING/SENT/FAILED/SKIPPED |
| send_reason | VARCHAR(64) NULL | 既有跳过/失败码或 CANCELLED/ACCOUNT_UNAVAILABLE/RESULT_UNCONFIRMED |
| tag_status / tag_error | VARCHAR(16) DEFAULT 'NOT_REQUIRED' NOT NULL / VARCHAR(256) NULL | NOT_REQUIRED/PENDING/APPLIED/FAILED；错误只记层名与受控原因 |
| created_at / updated_at | DATETIME(3) NOT NULL | 与执行系统 Asia/Shanghai 同口径 |

使用 utf8mb4_bin（身份键严格比较）；唯一键 `(task_execution_id, orcid_id, email)`；索引 `(task_execution_id,id)`。MySQL8 单键448字符×4+8=1800字节，小于3072；真实MySQL测试创建和写入边界，不靠H2。查询必须带executionId。仓储同文件声明 DTO；提供 insertPending、recordDecision、recordTag、recordSend、listAfter(limit+1)、aggregate，以及在一个readOnly事务内组合分页与汇总的readPage。result固定后只更新发送/标签，不覆盖先前验证结论；重复同一唯一键不触发第二次发送。发送前用条件UPDATE把decision=PASS且send_status=NOT_SENT的行改为SENDING，必须影响1行才允许SMTP；其它结果停止并报告重复/状态冲突。这只约束本execution审计行，不宣称解决跨执行SMTP幂等。外键级联随现有90天默认保留期清理，无新清理任务。

### T2 专用验证服务（I-2/I-4/I-5/I-7/I-8）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt`。使用 JDK11 HttpClient（现有Java11，无SDK依赖），固定 HTTPS 地址 `/v1/verify`，禁止重定向；Bearer 私钥；email 用 URI 正确编码（尤其 `+`）；smtp=true、accept_all=true、timeout=10。连接超时3秒、单请求超时12秒；这些值是本功能选择，非现有代码事实。249 仅再试一次，间隔500ms；其它服务故障无自动重试。相邻物理请求开始间隔至少100ms，可取消等待。测试用注入的非空 HTTP 测试接缝，生产固定真实客户端，不增加可从前端改目标地址的配置。

结果矩阵：deliverable→PASS/NOT_REQUIRED；undeliverable/risky/unknown→SKIP/标签PENDING，完成标签后APPLIED或FAILED；其它→ERROR/NOT_REQUIRED。错误码明确区分 EMAIL_VERIFY_AUTH_ERROR、EMAIL_VERIFY_NO_CREDITS、EMAIL_VERIFY_RATE_LIMITED、EMAIL_VERIFY_TIMEOUT、EMAIL_VERIFY_INCOMPLETE、EMAIL_VERIFY_BAD_RESPONSE、EMAIL_VERIFY_SERVICE_ERROR。复用按“本次执行+规范化邮箱”，仅复用已完成供应商结果，不缓存服务异常；同一邮箱不同专家仍各自标签和审计，不改变现有按专家发送去重规则。

ES 标签：每层 `_mget` 当前 expert_doc_id；只给身份/当前邮箱匹配的文档追加，检查 addTag 布尔返回。任何应写层失败在明细中可见；不因标签失败放行邮件。非通过目标缺失真实专家ID时保留SKIP并记tagStatus=FAILED/MISSING_DOC_ID，不退化成使用ORCID当ID；通过目标仍按实际邮箱继续旧发送链。标签的邮箱/身份检查以写前读取快照为准，沿用现有addTag接口，不声称跨ES读取和更新具备事务隔离。

### T3 在共享引擎接入与完整收尾（I-1～I-9）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`、`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt`。加入必需服务构造参数，同步两处显式测试构造器（E-5），禁止 nullable/default-null 来绕过接入。

处理顺序：入口检查开关/类型/密钥 → 原目标过滤 → 取消检查 → insertPending → verify/recordDecision → 若SKIP追加标签、记 EMAIL_VERIFICATION_REJECTED 跳过并占本轮处理槽 → 若ERROR设置停止原因并退出 → 若PASS继续旧选号/contact/去重/compose → 再核对 mail.to 与取消 → 原attempt PREPARED → 审计SENDING持久化 → SMTP → 旧txHelper与统计 → 审计SENT/FAILED。

原选号失败、已发送去重、模板门禁continue、模板错误、发送成功、SMTP永久/临时失败、广义catch、取消/break必须逐项处理已有验证行。PASS后没走SMTP保持NOT_SENT+具体原因，不能把“验证通过”展示成“发送成功”。SKIP行 send_status=SKIPPED。结果不明保持SENDING，停止本次执行。审计数据库异常使用专用异常边界先于当前广义catch处理；不得进入 pauseAccount/SMTP故障统计。若 SMTP已返回SENT且txHelper已提交但最后审计更新失败，保持原成功计数，审计结果未确认；不覆盖成发送失败。

新增跳过码 `EMAIL_VERIFICATION_REJECTED` → “邮箱验证未通过”。本次停止原因根据服务错误码持久化；错误不是所有剩余对象的批量跳过。收到ERROR后若sent>0终态PARTIAL_SUCCESS，否则FAILED（只针对新原因，不重写旧任务全局状态规则）。后续定时触发仍按原配置；人工重新执行是新executionId并重新验证剩余可选目标，非从旧记录恢复。

### T4 测试与迁移（I-1～I-9）

文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`。真实 MySQL IT 按现有 OpenAlexBudgetRepositoryIT 的 `@EnabledIfSystemProperty(mysqlIt=true)` + MySQL8.0.36 + 真实Flyway迁移；新测试类以IT结尾，在命令中显式指定。修改 FlywayMigrationIntegrationTest 所有“migrate到最新”预期为138，保留显式历史target预期；V137已有且旧测试仍写136，此事实不能漏掉。

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 快照布尔开关；跳过原因码 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 在介绍邮件共享引擎接入验证与审计收尾 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt` | 新增：HTTP 验证、逐次执行上下文、标签与审计协调 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt` | 新增：JdbcTemplate 仓储及同文件小型 DTO |
| 5 | `src/main/resources/db/migration/V138__create_batch_email_verification.sql` | 新增明细表、索引和级联外键 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt` | 新增：HTTP/标签/异常分类测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt` | 新增：真实 MySQL 约束、分页、聚合、清理测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 发送边界与计数；补必需依赖 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | 共享引擎回归；补必需依赖 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 新迁移与 latest 版本断言 |

合计10文件。两个紧密关联实施单元：批量发送验证链；验证明细持久化。没有新增现有ES字段，现有快照仅增加一个布尔字段。

## 验收标准

- I-1：false/旧JSON时verify与新仓储调用均0；MATERIAL_REMINDER+true无业务写入并明确失败，false原测试通过。
- I-2：四state、缺state、未知state、错email、`name+lab@example.test`编码、compose.to变动测试；只有正确PASS调用SMTP。
- I-3：新ES目标和NEW重试各覆盖；非通过时contact.save/attempt.save/send/recordSuccess均0；取消前后各测一次；原抑制、绑定、去重、模板与限额回归。
- I-4：249→200与249→249、402、403、429、网络timeout/5xx逐项；服务错误不加邮箱异常；成功0→FAILED，成功1后故障→PARTIAL_SUCCESS且remaining准确。
- I-5：真实_id不等于ORCID、三个层存在、只有候选层、其它层邮箱已变、重复标签、写方false/读异常；旧标签保留；operatorStatus无改动；通过不清标签。
- I-6：insert/decision/SENDING/最终更新分别故障；均验证无未记账的继续发送；发送完成后审计失败不得重复SMTP；所有continue/break分支有NOT_SENT原因。
- I-7：同邮箱两专家仅请求一次但两明细；不同执行再请求；roundSize=5有3个拒绝2个通过，仅发2、skip3、无第6次目标验证；预估API请求0。
- I-8：假私钥只出现在 Authorization；不进入URL/结果JSON/日志；缺key/test_开关开启停止；关开关无影响；provider文本不可执行。
- I-9：MySQL唯一约束、游标隔离/50+1分页、SUM汇总、级联清理；100封运行的每条progress.details_json不含累计邮箱数组。

实施后命令（本计划阶段未执行）：
```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchEmailVerificationRepositoryIT,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true
```

## 人工验收清单

统一准备方式：仅在隔离验收数据库/ES与捕获SMTP的环境操作。开发交付本子计划测试夹具：基于现有 RuntimeIntegrationTest 的对象构造方式，新建候选 A～F，真实ID为 `verify-a`～`verify-f`、邮箱 `a@example.test`～`f@example.test`，tags含 `验证验收`、研发类型PRODUCTION_RND、状态未联系、未绑定，模板无缺项，发件额度≥10；SMTP交给测试捕获器，不连接真实外部收件人。通过新服务测试接缝预置HTTP响应；不使用 test_ 假结果去驱动线上真实发信。人工可经测试应用已有POST `/api/mail/batch-send/manual-executions` 提交与前端相同的快照；后端断言测试由前述命令复现。验收人拿到已准备好的环境和基准请求JSON，无需阅读代码。

### A-1: 通过/不通过与原标签
- 前置条件：使用上述A～E，响应依次deliverable、risky、undeliverable、unknown、deliverable；每轮5、1轮；保存当前A～E标签和contact截图。
- 操作步骤：
  1. 提交包含 emailVerificationEnabled=true 的INTRODUCTION快照。
  2. 读取执行详情；查询该executionId的batch_email_verification行；在专家列表筛选“邮箱异常”。
  3. 查看SMTP捕获记录和专家联系状态。
- 预期结果：发送2、跳过3、失败0、剩余0；5行明细；B/C/D标签含原标签与邮箱异常，无新增绑定，无SMTP；A/E有发送结果。
- 覆盖：I-2/I-3/I-5/I-6/I-7；需求1/2；X2/X3/X4/X5

### A-2: 服务故障停止
- 前置条件：A设deliverable，B设402，C设deliverable；新建独立3人夹具，每轮3，1轮。
- 操作步骤：
  1. 开启验证提交快照。
  2. 查执行终态、B明细、SMTP捕获和A～C标签。
- 预期结果：只发送A；PARTIAL_SUCCESS；B为ERROR+EMAIL_VERIFY_NO_CREDITS，C未请求；剩余2、跳过0、发送失败0；B/C无邮箱异常。单独B再执行为FAILED。
- 覆盖：I-4/I-6/I-8；需求2；X1/X4

### A-3: 关闭与既有门禁回归
- 前置条件：重新建立6个独立候选：退订、已有绑定、已发送、模板缺必填项、普通可发、普通可发；另准备一条可用材料提醒。Emailable响应固定500。
- 操作步骤：
  1. 分别运行验证关闭的介绍邮件和材料提醒。
  2. 把相同规则的全新数据开启验证再执行。
  3. 读取原跳过原因/发送记录和HTTP捕获。
- 预期结果：关闭时Emailable调用0；材料提醒与原基线一致；开启时退订/绑定目标不调用Emailable且不发，已有去重/模板门禁仍阻止发送，服务异常不绕过旧限制。
- 覆盖：I-1/I-3/I-7/I-8；全部必须不变项；X3/X4

### A-4: 标签失败、取消与审计故障
- 前置条件：用单候选B risky；将该ES更新端点设失败。另用A deliverable并配置响应延迟2秒；仓储故障由验收夹具开关注入。
- 操作步骤：
  1. 执行B，查看tagStatus。
  2. 执行延迟A，验证中点击现有取消按钮。
  3. 分别注入验证前落库失败和SMTP成功后最终审计失败再执行独立A。
- 预期结果：B仍跳过，tagStatus=FAILED，不伪称已打标；取消A无SMTP且审计NOT_SENT/CANCELLED；验证前落库失败无HTTP/SMTP；已SMTP后落库失败不重发，日志保留SENDING/结果未确认。
- 覆盖：I-3/I-5/I-6；X2/X4/X5

### A-5: 快照历史与保留期
- 前置条件：完成A-1并记executionId；验收库以SQL读取request_payload。准备另一条过保留期的执行及明细，执行表started_at设为当前日期减91天，清理配置为默认90天。
- 操作步骤：
  1. 读取A-1请求快照确认开关=true。
  2. 通过现有清理任务入口执行归档；查询过期执行及其明细。
- 预期结果：快照包含true；过期执行及其关联明细均0行；A-1近期数据仍保留；没有孤儿明细。
- 覆盖：I-1/I-9；X1/X6
