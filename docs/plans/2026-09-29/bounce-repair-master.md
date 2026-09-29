# 硬退告警与地址无效误判：修复总计划

状态：待人工批准；仅规划，未执行修复、部署或历史数据更新。
审计日期：2026-09-29。代码基线：`ca55f0e37ca2d61cdcf362d4d64c5658e4dc34b3`（main）。
所有源码路径相对仓库根目录；行号为本次本地审计坐标，执行前按符号重新定位。未据此宣称生产包与本地 HEAD 完全一致。

## 需求描述

1. 用户能从账号池直接核对告警的退信数、成功发信数、百分比及统计口径。
2. 修复“所有永久失败都把专家判为邮箱无效”的代码条件；永久退信事实保留。
3. 只修证实的问题；历史疑似误判先审计，不猜测、不自动恢复。

必须不变：7 天窗口、至少 20 封、严格大于 5% 才告警；告警不暂停自动发送；已有账号管理、绑定、失败审计、发送额度、退信去重和人工覆盖不变。
不做：调高阈值消除告警、5xx 改 SOFT、同批次送达率系统、供应商专有规则库、新表/新持久化字段、新队列/缓存、发送器重构、DNS/信誉修复、自动恢复历史专家。

## 关键不变量

### Invariant I-1: 数字与语义分开
- Rule: HARD 是永久投递失败，不等于收件地址无效；事件比率不是同批邮件失败概率。
- Applies to: 三个子计划的分类、统计、文案。
- Violation consequence: 为消除告警扭曲事实，或继续误标专家。
- 来源: K-permanent-failure-not-invalid-address；代码证据 E-1～E-4。

### Invariant I-2: 有界顺序
- Rule: 01 → 验证 → 02 → 验证 → 03 → 验证 → 整体验收；每阶段只改该阶段白名单。02/03 共用对账文件必须串行。每个阶段可独立部署，但只有全部通过才能称本次修复完成。
- Applies to: 实施、验证、上线。
- Violation consequence: 对账共享文件互相覆盖，或半修复被宣称完成。
- 来源: original。

### Invariant I-3: 不自动修数据
- Rule: 新代码只影响后续处理及只读对账推导；不回填旧 DSN，不重新摄取重复退信，不修改历史 EMAIL_INVALID，不调用全量 ES 状态同步。
- Applies to: 实施、上线、回滚。
- Violation consequence: 没有证据地恢复真实无效地址，或覆盖人工决定。
- 来源: K-operator-status-single-writer / K-operator-status-reconcile。

## 现状审计

### 证据与结论

| 编号 | 明确代码位置 | 已证实事实 | 本次处理 |
|---|---|---|---|
| E-1 | `mail/service/BounceRateMonitorService.kt:16-27,46-49` | 分子 HARD、分母 SENT；<20 返回 -1；阈值 >0.05 | 01 只暴露数字，保留条件 |
| E-2 | `mail/repository/BounceRecordRepository.kt:11-19`；`MailRecordRepository.kt:474-483` | 分别用 received_at 与 sent_at；没有同邮件 JOIN | 01 明说事件窗口，不另造同批率 |
| E-3 | `mail/controller/MailSenderAccountController.kt:96-126,228`；`static/app.js:3279-3290` | DTO 只有 hardBounceRateHigh；徽标只有“硬退率过高” | 01 增数字及 tooltip |
| E-4 | `mail/service/BounceCollectionService.kt:149-169` | 保存退信后，只要 HARD 且有 contact 就 markEmailInvalid | 02 增地址证据门槛 |
| E-5 | `mail/service/BounceDetector.kt:172-174,245-247` | STATUS_PATTERN 为单字符三段；5.1.10 会被截为5.1.1 | 02 保留完整增强码 |
| E-6 | `campaign/service/ManualInitialOutreachService.kt:1001-1013` | 所有 PERMANENT 直接 save EMAIL_INVALID + ES sync，绕过公共保护 | 03 仅明确地址错误调用公共入口 |
| E-7 | `campaign/service/OperatorStatusReconcileService.kt:73-86,185-187` | 任意 HARD 或 PERMANENT 首封均优先推导 EMAIL_INVALID | 02 修 DSN、里程碑优先级；03 修 SMTP |
| E-8 | `campaign/service/ExpertOperatorStatusService.kt:78-91` | REPLIED 及更高状态不允许 markEmailInvalid；自动路径不写人工审计 | 02 保持；03 复用 |
| E-9 | `campaign/service/ManualInitialOutreachService.kt:727,862-879,1380-1397` | 只有新建contact固化绑定；复用历史NEW可无绑定，现有重试仅排除SENT/EMAIL_INVALID/绑定 | 03以已有PERMANENT失败记录补窄门禁，防取消误标后重发 |
| E-10 | `static/index.html:11-15,2323-2328` | 11 个带版本资源共用旧 key | 01 原位统一更新；不修改其它资源内容 |

上表 Kotlin 路径均以 `src/main/kotlin/com/weibo/talentintroduction/` 为前缀；static 路径以 `src/main/resources/` 为前缀。
完整 grep 回执：[bounce-repair-code-audit.md](bounce-repair-code-audit.md)。结论是源码条件证明，不是声称每一条线上拒信都被误判。

### 关键原文

```kotlin
// BounceCollectionService.kt:164
if (signal.bounceType == "HARD" && originalContact != null) {
// ManualInitialOutreachService.kt:1009
contact.copy(operatorStatus = "EMAIL_INVALID", updatedAt = LocalDateTime.now())
// OperatorStatusReconcileService.kt:185
if (hardBounceContactIds.contains(contactId) || permanentFailureContactIds.contains(contactId)) {
    return "EMAIL_INVALID"
}
// BounceDetector.kt:245
private val STATUS_PATTERN = Regex("""\d\.\d\.\d""")
```

前两处条件没有收件地址证据检查；第三处重复该推导；最后一处可以直接证明多位 detail 截断。

### 存储审计索引（完整调用点见回执）

- `bounce_record`：V29 定义，V43 增 failed_recipient；dsn_status VARCHAR(20)，bounce_message_id 唯一，无外键。唯一生产 save 在 BounceCollectionService.ingest。collectBounces、AutoMailReplyService、BounceBackfillService 汇入该入口。读者：BounceController、BounceRateMonitorService、MailMonitoringService、OperatorStatusReconcileService，以及 MailRecordRepository 的监控联合查询。只改变状态副作用条件/未来码解析，不删孤儿退信。（来源: K-bounce-record-has-no-foreign-key / K-bounce-collection-ingest-entrypoints）
- `mail_record`：V1 direction/mail_type/send_status/sent_at；V15 sender_account_code；V23 error_summary VARCHAR(1024)；V24 mail_send_attempt_id 唯一及外键。写入：ManualOutreachTxHelper 成功/失败、ManualExpertMailService、ManualReplySendAttemptService 两个发送路径、AutoMailReplyService 的入站/出站/失败/日志路径、MeetingScheduleService。全量读取入口见回执 repository-access，受影响读者仅账号计数和对账；其它邮件列表、附件、材料、AI/RAG、会话、任务读取保持原样。
- `mail_send_attempt`：V23 uq_orcid_mail_type、message_id 唯一；V24 增快照/额度列。批量 ManualInitialOutreachService 预占，ManualOutreachTxHelper 终态，ManualReplySendAttemptService 自己的状态机；本次不改该表及任何失败重试判据。
- `expert_contact`：V1 campaign+orcid 唯一；V19 operator_status 默认 NOT_CONTACTED、索引；V85 绑定、V86 换绑标记。显式状态写入在 ExpertOperatorStatusService 三方法及 ManualInitialOutreachService 初始化/永久失败分支；其余完整 contact 保存、局部 repository UPDATE、历史 V19/V94 更新均见回执。03 消除永久失败旁路，不动初始化。（来源: K-operator-status-single-writer / K-operator-status-write-seam-guard）
- ES：三个文件 `src/main/resources/es/orcid_info_{raw,candidate,application}.json` 均 dynamic=false；candidate:48、application:58 显式 operatorStatus keyword；RAW 文件没有该字段声明，不能假定现场 mapping 相同。ExpertIndexWriterService.syncOperatorStatus / syncOperatorStatusBatch 写三层；NOT_CONTACTED 删除字段。CandidateOperatorStatusSyncService 从最新 contact 批量同步；promotion/整文档复制和导入路径不在本次改动。读者 ExpertSearchService、ExpertIndexController、MailboxService、OperatorStatusReconcileService 及离线 shortlist/import 前置检查见回执。上线前只读核对实际 mapping；若缺字段，单独报阻塞，不静默扩项改 mapping。
- `mail_sender_account`：V1 account_code 唯一、额度/启用字段，V28 自动暂停，V117 取消退信自动暂停。MailSenderAccountService 管理/额度/暂停，ManualOutreachTxHelper/ManualInitialOutreachService 成功计数，ManualExpertMailService/MeetingScheduleService 最后发送时间；01 仅 DTO 读路径变化。
- `operator_action_log`：V19，CHANGE_OPERATOR_STATUS 用于人工覆盖；公共自动无效写入不产出此类日志。不修改审计 schema/writer。
- `mail_attachment`：V7，mail_record_id 外键；MailAttachmentService 写入，对账据 INBOUND 附件推导 MATERIALS_RECEIVED。本次只保留该里程碑，schema/写入不动。

### 必须诚实保留的边界

1. 早先线上只读快照中三账号分别为 LuKai 9/160=5.625%、WuWei_QF 3/59≈5.0847%、WuWei_WB 13/211≈6.1611%。这是当时滚动窗口，不是目前实时值；执行/验收须固定同一 cutoff 重查。
2. 早先快照 EMAIL_INVALID 87 人；其中41人有所查地址码证据，46人缺此类证据。46只是复核候选，不是确认误判人数。计划不以该数量作为执行更新目标。
3. 原消息 ID 直接等值 JOIN 无匹配不能证明应用无法归因：代码有 normalize/variants。resolveOriginalContact:292 用 firstOrNull，再走地址/alias；不声称“唯一归因已保证”，不在本次重构归因。
4. 对账的 dbVsExpected/esVsDb 两计数可以重叠（119/126），consistent 的减法并非互斥分桶。属于已有独立问题，本次不改报告结构；验收逐样本，不用总和证明零误判。
5. 失败文本可能拿不到嵌套异常增强码（SmtpErrorClassifier 返回 e.message）；缺证据不自动封禁，不在本次重写异常链。

## 实现方案

| 顺序 | 子计划 | 实施白名单文件数 | 交付 |
|---|---|---:|---|
| 01 | [告警可核对](bounce-alert-observability.md) | 7 | 数字、口径、缓存版本 |
| 02 | [DSN 与地址无效分离](bounce-address-invalid-separation.md) | 8 | 完整增强码、共享判据、退信状态门槛、对账 DSN/里程碑 |
| 03 | [SMTP 失败状态收口](bounce-smtp-invalid-separation.md) | 8 | SMTP 与对账使用同一持久化证据、公共状态入口、永久失败不重发 |

总计19个不同产品/测试文件（9个生产文件、10个测试文件）；02与03重叠的helper、helper测试、对账、对账测试四文件只能顺序修改。每阶段 ≤10文件、≤2子系统；没有共享存储新字段。计划及审计文档不计入产品文件白名单。

阶段门禁：
1. 执行前保留现有 dirty changes，核对 HEAD 与三个子计划符号；若范围漂移，先修计划，不修改未列文件。
2. 每阶段先补失败测试，再最小实现，再跑列出的测试。验证报告保留命令/退出码；本次规划没有宣称测试已通过。
3. 02上线后 DSN已收紧，但 SMTP旧分支要到03才收紧；不得把中间版本宣传为完整修复。
4. 全部机器验证通过，再导出各子计划的 acceptance 勾选表进行人工验收。
5. 部署另行授权；先测试环境，再生产。回滚只回退本次代码版本，不把已发生的新业务写入全表恢复。

历史复核（后续单独授权的数据工作，不包含 UPDATE）：
- 固定审计时间，导出 contactId、DB/ES状态、关联全部退信码及时间、首封永久失败摘要、人工变更、回信/材料/邀请证据和归因可靠性。
- 明确地址证据、非地址永久失败、人工覆盖、关联不明分别列清单；有任一不明则不建议自动恢复。
- 逐人得到可解释目标状态后，再提出带 before/after、乐观校验及回滚清单的数据修复方案；不直接把46人批量变成 CONTACTED。

## 变更文件清单

本总计划本身不授权直接改源码；实施白名单由三个子计划穷举，禁止使用总计划扩张子计划范围。
计划产物：本文件、三个子计划、代码审计回执；知识条目仅更新本次核实事实及使用记录。

## 验收标准

- I-1：01返回9/160且显示5.63%；02/03对5.7.1仍保留 HARD/PERMANENT，不新增 EMAIL_INVALID。
- I-2：逐子计划测试通过，改动文件属于白名单；三个阶段各有验证记录。
- I-3：无数据迁移、无生产更新、无全量ES同步；历史仅只读审计。
- 标准依据：地址码白名单含5.1.1/5.1.2/5.1.3/5.1.10，分别对应收件邮箱、目标系统、语法、Null MX错误；5.7类是安全/策略，不自动等同地址无效。依据 [IANA SMTP增强状态码注册表](https://www.iana.org/assignments/smtp-enhanced-status-codes/)，不是从阈值或供应商名称猜测。

## 人工验收清单

### A-1: 完整用户路径
- 前置条件: 测试环境已完成三个子计划，按各子计划A项构造专用测试邮箱/账号，不向真实专家发信。
- 操作步骤: 1. 完成01 A-1～A-4；2. 完成02 A-1～A-4；3. 完成03 A-1～A-4。
- 预期结果: 账号池可核对9/160（5.63%）；政策拒信保留失败但不标EMAIL_INVALID；地址无效才标记；失败不触发新重发；人工覆盖、启停按钮及绑定不变。
- 覆盖: I-1/I-2、需求1/2、原有行为。

### A-2: 历史无隐式更新
- 前置条件: 测试库保存一名仅有5.7.1旧退信的EMAIL_INVALID联系人，记录其DB/ES值；一名有人工CHANGE_OPERATOR_STATUS记录的联系人。
- 操作步骤: 1. 升级测试应用；2. 运行只读对账；3. 查看两人详情及ES文档。
- 预期结果: 两人的原DB/ES状态均不因升级或对账改变；前者允许报告差异，后者归HUMAN_OVERRIDE；不自动发信。
- 覆盖: I-3、需求3。
