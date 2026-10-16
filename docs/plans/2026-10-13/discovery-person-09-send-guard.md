# 09 人物级首发原子保护

状态：设计，未实施。依赖 04/08；与历史映射准备共同开启。上级：[总计划](discovery-identity-recovery-master.md)。

## 需求描述

同人不同邮箱、不同批次或不同介绍邮件入口竞争时，至多一个首发调用获准进入 SMTP。保持原 mail_record/contact/attempt 引用、配额、发件人绑定、材料提醒/回复流程及范围外发送；本轮不发送邮件。

## 关键不变量

### Invariant I-1：所有介绍邮件入口同一原子门禁
- Rule：新增 `discovery_person_outreach` 唯一键 `(person_id, mail_type)`，mail_type 本期仅 INTRODUCTION；不以 campaign/account/email 切开首发唯一性。旧定时、新批量含重试、详情手动模板发送均在实际 mailType=INTRODUCTION 且目标为 discovered 时使用同一保护。人物映射未就绪/版本变化先阻断。范围外保持原路径。
- Applies to：三发送 service、首发保护服务、事务完成记录。
- Violation consequence：多个入口/邮箱绕过并发约束。
- 来源：V23 仅约束 orcid_id+mail_type；ManualExpertMailService 直接调用 MailDeliveryService。

### Invariant I-2：发送状态保守推进
- Rule：状态为 RESERVED（未交付）、SENDING（已取得唯一交付权）、SENT、UNKNOWN、SAFE_RETRY。以事务插入和 expected revision/owner token CAS 获得权限；网络请求前持久化 SENDING。SMTP 成功但后续落库失败、异常且无法证明未交付、进程在 SENDING 中断均保持不可重发并归 UNKNOWN。只在明确未调用 SMTP 或明确未接受时允许 SAFE_RETRY；不得因锁过期、超时或换邮箱重新取得首发许可。SAFE_RETRY 仍绑定原 recipient，既有明确永久失败策略保留。
- Applies to：占位、成功、失败、中断及人工后续核对。
- Violation consequence：SMTP 已接收但系统重试造成重复邀请。
- 来源：现有 MailSendAttemptStatus 的不确定交付处理经验；总计划 I-8。

### Invariant I-3：历史保护及邮箱资格不可绕过
- Rule：占位前只读检查全部成员的原联系人、邮箱别名、mail_record/mail_send_attempt、人工准入和抑制。已有成功或不确定发送不再首发；人工拒绝/暂停/明确退出不能靠换邮箱绕过。选址来自 08，但发送前重新核验当前标签、人物 revision、具体邮箱资格和状态。原历史 ID、内容和地址不重写。
- Applies to：保护服务、10 历史保护导入、实际发送前检查。
- Violation consequence：历史已联系者换邮箱被当新人，或旧决定失效。
- 来源：K-dual-outreach-paths、K-recipient-count-preview-parity、V10/V148。

## 现状审计

- V23 `mail_send_attempt` 的 `UNIQUE(orcid_id,mail_type)` 和 message_id 唯一，原 orcidId 常为 EMAIL-*。新批量 save PREPARED 后有邮箱验证审计 markSending，但不是跨邮箱人物锁；ManualOutreachTxHelper.recordSuccess/recordFailure 写 contact、mail_record、attempt。
- `InitialOutreachService.sendInitialBatch` 先按 campaign+orcid 查存在，再新建 contact 并直接 send，未共用新批量 attempt 占位；必须在新建联系人前取得人物许可。
- `ManualInitialOutreachService` runIntroductionFromSnapshot 及重试共用最终发送循环；保留其配额、取消和 markSending 流程。
- `ManualExpertMailService.sendManualMailInternal` 在 compose 后直接 send；入口含 sendManualMail/sendManualMailFromSnapshot/sendBatchMail，compose 才得到实际 mailType，不能仅按按钮/模板 ID 判定是否介绍邮件。
- 新 guard 表只由保护服务写，10 通过同一服务导入历史禁止状态；08 可只读，预估不能创建占位。现有业务表 schema 均不改，手动回复草稿占位不混入本表。
- 交互：两个进程竞争；已发成功但数据库未完成；取消发生在占位前/后；人工单发绕过批量；旧历史别名指向其他范围对象。

## 实现方案

1. **新表与门禁（I-1— I-3）**：V157 新建 `discovery_person_outreach(person_id FK, mail_type, state, revision, owner_token, selected_doc_id, recipient, message_id, attempt_id nullable, history_reference_json, created_at, updated_at)`，复合主键。DiscoveryPersonOutreachGuardService 内聚 JDBC 条件更新和历史只读检查；每次调用需 expected 人物版本/实际地址，依靠数据库锁和唯一键，不能用 JVM synchronized 代替多实例互斥。锁持有期间复核已登记成员 revision；ES/准入变化在发送前重读，承认跨库与 SMTP 无分布式事务。
2. **三入口接入（I-1— I-3）**：旧/新首发在建 contact 或修改 attempt 前 reserve；完成所有原门禁后 CAS 到 SENDING 才 send。详情手动发送仅对实际 INTRODUCTION 执行相同检查，其余类型原样。未获许可返回可解释重复/未知/不合格结果，不能先建另一 contact 再跳过。
3. **结果与中断（I-2/I-3）**：ManualOutreachTxHelper 在原成功事务中联动已有 guard 的 SENT；详情自己的成功事务作等效处理。无法同时完成时不释放占位，保留 UNKNOWN 待核。占位后取消且证明未交付可标 SAFE_RETRY；SENDING 不允许定时超时解锁。上线回滚也保留此保护，不能恢复旧逐邮箱首发。
4. **四份定向测试（I-1— I-3）**：真实数据库并发两个 worker/不同入口，假 SMTP 记录调用数；注入成功后数据库失败、异常、取消及明确未交付重试；验证非 INTRODUCTION、范围外及原配额行为。V157 实施前检查是否已被占用，禁止改既有迁移。A14：同步授权 FlywayMigrationIntegrationTest.kt（全量迁移最新版本断言 31 处 156→157，并加 V157 表存在/唯一键断言）。

## 变更文件清单

|文件|动作|
|---|---|
|src/main/resources/db/migration/V157__create_discovery_person_outreach.sql|新增|
|src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt|新增|
|src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt|在 08 后修改|
|src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt|在 08 后修改|
|src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt|修改|
|src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt|修改|
|src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt|新增|
|src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt|在 08 后修改|
|src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt|在 08 后修改|
|src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt|修改|
|src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt|A14：31 处最新版本断言 156→157 + V157 表存在/唯一键断言|

共 11 文件（A14：第 11 个为迁移集成测试）；首发保护与发送接入两个子系统，既有共享存储新增字段 0。

## 验收标准

- I-1：跨三入口并发，对同人 SMTP 调用≤1；不同人不互锁；非 discovered 不接新门禁。
- I-2：成功后数据库失败、SENDING 中断与未知 SMTP 均不再次发送；SAFE_RETRY 仅在可证明未交付时获得同地址许可。
- I-3：历史成功/不确定/人工保护阻断换址首发；具体地址不合格即使人物合格仍拒绝；原联系人/邮件历史未重写。

## 人工验收清单

### A-1：三入口竞争
- 前置条件：隔离环境一个人两个合格邮箱、没有历史；假 SMTP 可记录请求；两个应用进程。
- 操作步骤：同时触发旧首发、新批量和详情介绍邮件；再触发另一人的介绍邮件及普通回复。
- 预期结果：同人首发 SMTP 共 1 次，其他两个请求提示已占用/已发送；另一人和普通回复按原逻辑执行；未中选邮箱无新联系人。
- 覆盖：I-1/I-3、三入口及范围保持。

### A-2：未知结果和历史
- 前置条件：注入 SMTP 接收成功后 DB 异常；另有历史已发组和人工 HOLD 组，均有备用合格邮箱。
- 操作步骤：执行一次后重试、换邮箱、重启进程；查看原邮件及 guard。
- 预期结果：异常组始终不自动重发，guard 为 UNKNOWN 或尚未核清的 SENDING；历史/HOLD 组调用 SMTP 0 次；原历史记录未改写。
- 覆盖：I-2/I-3、中断与历史交互。

### A-3：明确未发送及正常回归
- 前置条件：在 reserve 后、SENDING 前取消；另准备范围外专家、材料提醒与现有配额上限。
- 操作步骤：取消后重试原地址；执行三个回归目标。
- 预期结果：取消时 SMTP 0 次，可安全重试且只发原地址；范围外与提醒遵守原逻辑，配额不得超额。
- 覆盖：I-1/I-2、取消/重试/配额保持项。
