---
id: K-sender-account-selection-sites
domain: mail
created: 2026-08-10
last_used: 2026-08-10
hit_count: 0
source: create-p:sender-binding-01..05
severity: P1
---

经验：「这封邮件用哪个发件账号」在全仓有 **7 个互不相同的决策点**，且分属三种语义。
改任何与账号归属相关的行为前必须逐点核对，只改其中几处必然产生"同一专家换号发信"。

**A. 分发型选号（按 country 分散 + strategyWeight 打分）** —— `SenderAccountAssignmentService.selectAccount`
1. `InitialOutreachService.kt:48` —— 自动首封，**新建 contact**
2. `ManualInitialOutreachService.kt:552` —— 批量首封轮，contact 可能已存在
3. `ManualInitialOutreachService.kt:272` —— **材料提醒轮，targets 全是已有 contact**
   （该轮把选出的 code 显式塞进 `ManualMailSendCommand.senderAccountCode`，见 `:300-305`）

**B. 全局打分选号（无国别分散）** —— `MailSenderAccountService`
4. `ManualExpertMailService.kt:55-58` —— `selectAccountForManualSending()`，
   谓词 `isManualSendable():227-228` **只排除 SIMULATOR_NOOP**
5. `MeetingScheduleService.kt:109` —— `selectAccountForSending()`，
   每次会议确认都重新选号，与该专家首封账号无关

**C. 线程归属（用收信账号，不选号）**
6. `PendingMailOperationService.kt:642-647` —— `requestedCode ?: record.senderAccountCode`
7. `AutoMailReplyService.processSingle(account, ...)` —— IMAP 收信账号

关键事实：`expert_contact`（`V1__create_business_tables.sql:79-95`）**原本没有任何 sender 归属列**，
账号归属只散落在 `mail_record.sender_account_code`；因此 1/2/3/4/5 每次都独立重选，
"同一专家先后由不同账号发信"是系统默认行为，不是异常。

正确做法：给专家引入账号归属时，A/B 两类必须统一收口到同一解析 seam；
C 类**不得**跟着改——回复必须留在原线程的收信账号，否则 `In-Reply-To` 与 `From` 域不一致。

关联：[[K-dual-outreach-paths]]（1 与 2 共用 composer 与 assignment）、
[[K-operator-send-quota-paths]]（B 类的额度判定差异）、
[[K-sender-account-enabled-scope]]（enabled 的四类路径语义）。
计划：`docs/plans/2026-08-10/00-main-plan-sender-binding.md`

> 2026-08-10（sender-binding-02-send-path-consistency，P2 落地后）A/B 两类**已有 contact 的发送路径**已收口到唯一解析 seam
> `SenderAccountBindingService.resolveForSend(contact, manual, ignoreWarmup=false)`：A 类 2/3 与 B 类 4/5 均改为
> 「绑定优先 → `SenderAccountNotBoundException` 才走原选号兜底（兜底成功后 `bindIfAbsent` 补写绑定）」；
> A 类 1（新建 contact）仍在建行时固化绑定；C 类（回复线程）保持原样（G-1），选号兜底入口不变
> （`selectAccount` / `selectAccountForManualSending` / `selectAccountForSending`）。
