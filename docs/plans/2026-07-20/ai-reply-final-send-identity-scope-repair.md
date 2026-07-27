# AI 回复最终发送：幂等身份与 API 范围独立修复

> 独立目标：承接 `verification-terminal-after-fix-3.md` 的剩余 P1；本计划不是 `fix-4`，不延续原计划轮次。

## 需求描述

- 可观察结果：人工富文本发送的幂等指纹必须包含服务端 `contactId`；仅 contact 不同的最终 payload 不得复用发送尝试、Message-ID 或发送结果。
- 可观察结果：恢复高风险事实校验器的模块内可见性，不扩大公开 API，同时保持当前最终发送安全判定不变。

必须保持不变：

1. 相同 contact、相同 inbound、相同最终 payload 的双击、并发和网络重试仍只产生一次 SMTP 调用，并复用首次持久化的 UUID-based Message-ID。（来源：K-manual-send-fingerprint-complete-identity、K-message-id-fingerprint）
2. 指纹中现有 schemaVersion、inboundProcessingId、orcidId、accountCode、normalizedRecipient、subject、finalText、finalHtml、inReplyTo 和有序 canonical QA IDs 的顺序、编码和语义不变；只在 inboundProcessingId 后追加 contactId。
3. `AiReplyHighRiskClaimValidator` 两个方法的检测算法、事实来源、warning code 和调用结果不变；只恢复 `internal` 可见性。（来源：K-answerbody-source-exclusive）
4. `mail_send_attempt` 的唯一键、状态机、CAS、Message-ID、完整 hash/短 key 碰撞校验不变；INTRODUCTION、自动回复、会议邮件、配额和账号计数不变。

不在范围：

- 不修改数据库 schema、Flyway、历史 attempt 数据或 `SCHEMA_VERSION`。
- 不修改 SMTP 分类、重试/UNKNOWN 语义、controller、前端、QA 选择、最终内容校验或审计格式。
- 不新增公共 API、状态、字段、migration、清理脚本或兼容回退。

## 关键不变量

### Invariant I-1：contact 是幂等身份的一部分
- Rule：`computeFingerprint()` 使用 UTF-8 长度前缀依次编码现有字段，并在 `inboundProcessingId` 后编码 `contactId.toString()`；仅 contactId 改变时 full hash 与 `MANUAL_RICH:` short key 都必须改变。
- Applies to：`ManualReplySendAttemptService.computeFingerprint()`、`prepareAndClaim()` 使用该指纹创建/读取 reservation 的路径。
- Violation consequence：不同 contact 可能错误复用 attempt、Message-ID、mail record 或发送结果。
- 来源：K-manual-send-fingerprint-complete-identity。

### Invariant I-2：同 payload 幂等与 Message-ID 不变
- Rule：除新增 contactId 字段外，不得删除、排序或重排现有指纹字段；同一 payload 多次计算的 full hash/short key 相同，首次 reservation 生成 UUID-based Message-ID，后续相同 attempt 读取并复用持久化值。
- Applies to：`computeFingerprint()`、`prepareAndClaim()`、SENT/SAFE_RETRY/IN_PROGRESS/UNKNOWN 分支。
- Violation consequence：同一请求失去幂等性，或重试产生新的可识别/重复邮件。
- 来源：K-manual-send-fingerprint-complete-identity、K-message-id-fingerprint。

### Invariant I-3：修复只适用于发布前算法
- Rule：执行前必须确认目标环境不存在已投入使用的 `mail_type LIKE 'MANUAL_RICH:%'` attempt；若存在，不得继续实施或删除数据，必须暂停并申请兼容性决策。当前计划不提供旧/新指纹双读或数据迁移。
- Applies to：实现开始前的发布检查、部署决策。
- Violation consequence：已存在的旧指纹无法命中新指纹，同一邮件可能被当作新 attempt 再次发送。
- 来源：original。

### Invariant I-4：校验器仅恢复模块内可见性
- Rule：`containsHallucinatedNumberOrUrl()` 与 `containsUnbackedHighRiskDeclarations()` 必须声明为 `internal fun`；方法体、参数、返回值、正则、事实读取与 warning code 均不得改变。单一 Maven/Kotlin 模块内的 mail service 与测试仍可调用。
- Applies to：`AiReplyHighRiskClaimValidator`、`PendingMailOperationService` 当前两处调用、现有 validator tests。
- Violation consequence：继续扩大未批准的公开 API，或在收口范围时意外改变最终发送安全规则。
- 来源：K-answerbody-source-exclusive；original。

### Invariant I-5：共享发送表和其他发送路径零变化
- Rule：不得修改 `MailSendAttemptRepository`、`MailSendAttemptStatus`、Flyway、`ManualInitialOutreachService` 或 `ManualOutreachTxHelper`；INTRODUCTION 继续使用 `(orcid_id, INTRODUCTION)` 与原 PREPARED/SENT/FAILED、配额及 mail record 语义。
- Applies to：共享 `mail_send_attempt` 的全部写读路径。
- Violation consequence：窄修复破坏初次触达或共享状态机。
- 来源：original。

## 现状审计

### `mail_send_attempt`

- Schema/mapping：`V23__create_mail_send_attempt_and_add_mail_record_error.sql:1-17` 建表，唯一键为 `(orcid_id, mail_type)` 和 `message_id`；`mail_type VARCHAR(50)`。`V24__extend_mail_send_attempt_state_and_link_mail_record.sql:18-24` 增加 recipient/subject/body/contentType/quota 字段；`mail_record.mail_send_attempt_id` 为唯一外键。`MailSendAttempt.kt:8-25` 映射全部字段。本计划不改 schema/mapping。
- Write paths：
  1. `ManualReplySendAttemptService.prepareAndClaim()` — `insertIgnore` 写人工 reservation，full hash 存 `body`，短 key 存 `mailType`，随后 CAS 为 DELIVERY_IN_PROGRESS。
  2. `ManualReplySendAttemptService.finalizeSuccess()/finalizeFailure()` — 更新人工 attempt 最终状态和错误摘要。
  3. `ManualInitialOutreachService:605-623` — 创建/恢复 INTRODUCTION PREPARED。
  4. `ManualOutreachTxHelper:74-80,124-131` — 写 INTRODUCTION SENT/FAILED。
  5. V23/V24 migrations — 建表、补列、历史关联和 quota backfill；不在本计划执行范围。
- Read paths：
  1. `ManualReplySendAttemptService.prepareAndClaim():114-197` — 按 `(orcidId, shortKey)` 加锁读取并按状态 dedup/fail closed。
  2. `ManualReplySendAttemptService.finalizeSuccess()/finalizeFailure()` — 按 attemptId 读取当前状态。
  3. `PendingMailOperationService:359-368` — DEDUP_SENT 后按 attemptId 读取原 mail record 结果。
  4. `ManualInitialOutreachService:607` — 按 `(orcidId, INTRODUCTION)` 读取旧 attempt。
  5. `ManualOutreachTxHelper:75,125` — 按 attemptId 读取 INTRODUCTION 状态。
  6. `MailSendAttemptRepository.findByMessageId()` 当前无生产 caller；本计划不改。
- Interaction points：`PendingMailOperationService` 构造含 contactId 的 canonical payload → `computeFingerprint()` 生成 short/full identity → repository 唯一 reservation/CAS → finalize 关联唯一 mail record。只允许修改 identity 输入；store、状态和 readers 不变。（来源：K-manual-send-fingerprint-complete-identity）

### `AiReplyHighRiskClaimValidator` 可见性

- 当前差异：`AiReplyHighRiskClaimValidator.kt:180,249` 两个方法从原 `internal` 扩大为 public。
- 生产 callers：类内校验路径，以及 `PendingMailOperationService:575,578` 的纯人工最终内容检查。
- 测试 callers：`AiReplyHighRiskClaimValidatorTest` 的数字/URL和高风险声明用例。
- 模块边界：`pom.xml` 仅一个 `weibo-talent-introduction` Maven/Kotlin 模块；`internal` 对当前 production/test callers 可见。
- Interaction points：可见性恢复后，main compile 证明跨 package、同模块的 mail service 仍可调用；validator target tests 证明算法未变。（来源：K-answerbody-source-exclusive）

### 发布前检查点

- 执行前对目标环境只读查询：
  ```sql
  SELECT COUNT(*)
  FROM mail_send_attempt
  WHERE mail_type LIKE 'MANUAL_RICH:%';
  ```
- 期望：所有已发布环境均为 `0`，因为本功能尚未发布。
- 若任一环境大于 `0`：停止本计划；不得清表、迁移或上线。由人决定旧/新指纹兼容策略后另立计划。

## 实现方案

### T1：补全 contactId 指纹

- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt`
- 在 `appendLengthPrefix(data, payload.inboundProcessingId.toString())` 后立即增加 `appendLengthPrefix(data, payload.contactId.toString())`。
- 不修改 `SCHEMA_VERSION`、其他字段顺序、short key 长度、Message-ID 生成、reservation/CAS/finalize。
- 新增精确回归：同 payload hash 稳定；仅 `contactId` 从 `1L` 改为 `2L` 时 full hash 和 short key 均不同；改回 `1L` 后恢复原 hash；仅 QA ID 顺序变化时 full hash 和 short key 均不同。
- 遵守：I-1、I-2、I-3、I-5。

### T2：恢复模块内 API 边界

- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`
- 将 `containsHallucinatedNumberOrUrl()`、`containsUnbackedHighRiskDeclarations()` 的声明恢复为 `internal fun`；方法体逐字不动。
- 不修改 `PendingMailOperationService` 或测试 caller；main/test compile 必须直接证明当前单模块调用合法。
- 遵守：I-4、I-5。

### T3：验证与发布门禁

- 先完成 I-3 的只读环境查询；任何非零结果立即停止。
- 运行：
  ```bash
  JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualReplySendAttemptServiceTest,AiReplyHighRiskClaimValidatorTest,PendingMailOperationServiceTrustWorkbenchTest,ManualInitialOutreachServiceTest,ManualOutreachTxHelperTest
  JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
  git diff --check
  ```
- 全量 Maven 必须在能完成 Kotlin compile/surefire 的环境运行；没有新的 surefire 结果不得声明完成。
- 遵守：I-1 至 I-5。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` | 指纹加入 contactId 长度前缀 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt` | contact identity、稳定性与有序 QA 回归 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt` | 两个方法恢复 internal 可见性 |

边界：3 个文件、2 个子系统、0 个新字段、0 个数据库/前端文件。任何其他产品代码改动都必须停止并请求计划修订。

## 验收标准

- I-1：`ManualReplySendAttemptServiceTest` 断言仅 contactId 变化时 full hash/short key 均变化；源码证据显示 contactId 紧跟 inboundProcessingId 进入长度前缀。
- I-2：同 payload 重算 hash/short key 相同；现有字段顺序、canonical QA 顺序、UUID Message-ID、dedup/SAFE_RETRY/UNKNOWN tests 全部通过。
- I-3：记录目标环境只读查询结果为 0；任何非零结果视为 BLOCKED，不实施、不发布。
- I-4：源码签名精确为两个 `internal fun`；方法体 diff 除可见性外为零；`AiReplyHighRiskClaimValidatorTest` 与 `PendingMailOperationServiceTrustWorkbenchTest` 通过。
- I-5：`ManualInitialOutreachServiceTest`、`ManualOutreachTxHelperTest` 通过；git diff 中不存在 repository、状态、migration、controller、前端或其他发送 service 文件。
- 交互集成：以同一 inbound、相同 orcid/email/account/subject/body/QA，只切换 contactId 的 fixture 调用发送，必须创建不同 MANUAL_RICH attempt；同一 contact 的 exact retry 仍 dedup 到原 attempt。
- 构建门禁：目标 Maven PASS；`mvn clean test` PASS 且报告计数可用；`git diff --check` PASS。

## 人工验收清单

### A-1：不同 contact 不得复用发送结果
- 前置条件：测试环境使用可计数 SMTP stub（固定返回 250）；在两个不同 campaign 下各建一个 contact，二者使用相同 ORCID 和邮箱；建一条 inbound 并先绑定 contact A；准备固定账号、主题、正文和空 QA 的安全请求；确认 `mail_send_attempt` 中无该请求对应记录。
- 操作步骤：1. 调用现有 `send-manual-rich-reply` endpoint 一次；2. 仅把同一 inbound 的 `expert_contact_id` 改绑为 contact B；3. 原样提交同一请求；4. 查询 SMTP、attempt 和 mail record。
- 预期结果：HTTP 两次均成功；SMTP 调用数增加 2；产生两个不同 `MANUAL_RICH:` short key、两个不同 attempt 和两个 UUID-based Message-ID；两条 mail record 分别关联 contact A/B，不返回第一次的 dedup 结果。
- 覆盖：I-1、I-2；需求描述第 1 条；payload → attempt → mail record interaction。

### A-2：同 contact 重试仍只发送一次
- 前置条件：新建独立 inbound/contact fixture；SMTP stub 返回 250；准备一份安全 exact request。
- 操作步骤：1. 连续提交完全相同 request 两次；2. 查询 SMTP、attempt、mail record 和响应 Message-ID。
- 预期结果：两次 HTTP 均成功且 Message-ID 完全相同；SMTP 仅调用 1 次；仅 1 个 attempt、1 条 mail record；第二次为 dedup 返回。
- 覆盖：I-2、I-5；必须保持不变第 1、4 项。

### A-3：最终高风险校验语义不变
- 前置条件：新建已绑定 inbound；SMTP stub 调用数记为 N；不选择 QA。
- 操作步骤：1. 发送包含无依据金额或外部 URL 的正文；2. 再发送一份无数字、URL、承诺或敏感动作的安全正文。
- 预期结果：第 1 次 HTTP 422 且 SMTP 仍为 N；第 2 次成功且 SMTP 为 N+1；没有校验器可见性错误或 500。
- 覆盖：I-4；需求描述第 2 条；必须保持不变第 3 项。

### A-4：INTRODUCTION 共享表回归
- 前置条件：准备一名从未初次触达的联系人、可发送账号和 SMTP 250；记录账号 todaySentCount。
- 操作步骤：1. 执行现有 INTRODUCTION 发送；2. 查询 attempt、mail record 和账号计数。
- 预期结果：attempt 的 `mail_type=INTRODUCTION`、status=SENT；mail record 为 SENT 且关联该 attempt；todaySentCount 精确增加 1；无 `MANUAL_RICH:` 记录覆盖 INTRODUCTION。
- 覆盖：I-5；必须保持不变第 4 项；INTRODUCTION writer → shared store → reader interaction。
