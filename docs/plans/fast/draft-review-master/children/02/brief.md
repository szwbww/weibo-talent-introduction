# Child 02 — Approved Execution Brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master
- Branch: fast/draft-review-master
- Exact plan: docs/plans/2026-10-29/draft-review-02-query.md
- Plan identity: sha256:b9bbae9442d052c1ae1a122b25e42ac2020bf1973f0aff76d3bcd823adf79d91
- Approval: User invocation `/fast-p docs/plans/2026-10-29/draft-review-master.md` on 2026-10-29 authorizes execution of these exact plan bytes despite historical DRAFT headings. Do not amend plan bytes.
- Dependencies: 01; dispatch only after predecessor checkpoint.
- Product base: controller supplies the exact child_base_sha at dispatch; evidence HEAD may be later.
- Execution report: docs/plans/fast/draft-review-master/children/02/execution.md

## Global Constraints
Read the exact child plan completely from disk and use execute-p. Its authorized file table is exhaustive; no changes outside that table. Read docs/design.md before mail/conversation changes and applicable repository guidance. Master invariants I-23/I-24 apply: sequential incremental work; existing shared tables gain zero columns; preserve existing mail, admin drafts, restricted reader access, and unrelated user changes. No deployment, push, merge, history rewriting, or extra worktrees. No plan amendments without human approval. Evidence docs are separately authorized only in the designated fast-p report files; exclude them from product commits.

One writer at a time. Do not run build/lint/tests/formatters mid-flight; collect all required fresh commands and smoke proof once implementation is complete. Do not fix unlisted files or silently narrow requirements. Report PLAN_CONFLICT/BLOCKED with exact evidence if necessary. Full verification is deferred; do not invoke verify-p, review-p, repair-p or fix-v inside this loop. The controller will dispatch an isolated four-gate verifier.

## Runtime and Commands
JDK11: /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. Docker daemon is available via OrbStack. For MySQL/Flyway ITs set DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock and pass -DargLine=-Dapi.version=1.41 when needed. Run every command from the child plan freshly, all mandatory ITs enabled and non-skipped. Product smoke must exercise the changed API/UI, not only tests; use throwaway scenarios and clean those afterward. Browser evidence for 04/05 must use actual UI; do not substitute source inspection or fake localStorage. Test SMTP only; never production recipients.

## Authorized Requirements and Downstream Interface
The exact plan below defines all tasks, invariants, authorized files, public DTOs/methods, tests, styles, and acceptance. Its quoted historical source line numbers are pointers, not permission to replace newer predecessor code. Preserve exact planned DTO and API names for downstream children. Necessary predecessor execution evidence is available in earlier child artifact directories; read only the dependency outputs needed by this child, not later plans. Required source evidence is in docs/plans/2026-10-29/draft-review-evidence/.

# 02 · 待审核计数与专家筛选

状态：DRAFT / 待用户批准实施。仅开发计划，不代表代码已实现或测试已通过。
基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`，2026-10-29 工作树；原有脏文件见 [状态快照](draft-review-evidence/worktree-status.txt)，不得覆盖。
证据：[逐字源码 E01–E31](draft-review-evidence/source-excerpts.md)、[数据库迁移链](draft-review-evidence/schema-chain.md)、[读写调用全集](draft-review-evidence/06-repository-call-sites.txt)、[SQL/存储引用全集](draft-review-evidence/05-all-store-read-write.txt)、[来源 SHA256](draft-review-evidence/sha256.json)。文中“新增/拟”都是设计，不声称已存在。

前置：01机器门禁通过，基于01产物执行。

## 需求描述
1. admin 会话接口返回待审核草稿总数、每位专家待审核草稿数，并可只返回有待审草稿的专家。
必须保持：既有会话筛选/分页/邮件计数与 user1授权过滤；原“草稿”chip继续指管理员自己的草稿。
不做：前端弹窗、审核中心页面、草稿作为左侧主列表、通知系统、轮询服务。
本步5文件、1子系统（邮箱查询）、已有共享表新增字段0；可独立部署，默认参数保持旧客户端行为。

## 关键不变量
### Invariant I-8: 数的是草稿，列表仍是专家
- Rule: 仅 state=SUBMITTED AND deleted_at IS NULL 计待审。toolbar reviewPendingTotal是草稿数；conversation.total是专家数；item.reviewPendingCount是该专家草稿数。count/page/explain使用同一个reviewPendingOnly成员EXISTS，不能先分页再前端筛。
- Applies to: listConversations/countConversations/pageConversations/explainConversationsPage/summary。
- Violation consequence: 3份草稿显示成3位专家，或翻页漏人。
- 来源: original

### Invariant I-9: 筛选范围与权限一致
- Rule: reviewPendingOnly默认false；仅admin可置true。草稿按account_code与UI accountCode相交，并要求该账号存在且非模拟器。保留q/日期/方向/主题/标签等已有过滤。待审核计数忽略互斥chip条件（followed/provided/replied/pending/waitingReply/suspended），沿其余筛选条件计算。reader始终计数0、不额外查审核摘要；原授权EXISTS不得移除。
- Applies to: controller/service/filter SQL与badge查询。
- Violation consequence: 泄露待审数量或切换筛选后数字失真。
- 来源: K-sender-account-enabled-scope；E07

### Invariant I-10: 状态变化即时影响查询
- Rule: 提交→下一次list增加；撤回/删除/退回/批准→下一次list减少。撤销user1授权保留其已经提交的admin审核记录；只要专家/目标账号仍存在admin仍可审，user1不可读写。联系人/目标账号不存在则从普通待审集合移除；保留事件历史供已知id管理员查看。无缓存、定时器或异步索引来存待审真值。
- Applies to: 新草稿状态写→现有会话读。
- Violation consequence: 授权撤销后记录丢失或提示卡死。
- 来源: original

## 现状审计
### 既有存储与交互边界（所有子计划共用的审计收据）
- `expert_mail_read_access`：V166 主键 `(username,expert_contact_id)`，contact 删除级联删授权。`ExpertMailReadAccessService.grant/revoke` 唯一业务写者；`getAccess/grantCount` 与 `MailboxConversationRepository.hasReadAccess/expertPredicates` 读取。新审核读写也实时检查授权，不新增授权缓存。E01/E02/E07。
- `mailbox_reply_draft`：V152 每个 owner/contact/kind/processing/scope 唯一；ACTIVE/SENT/DISCARDED。全部数据库写方法是 `MailReplyDraftRepository.insert/save/discard/bindAttempt/closeSent/releaseCompletedBinding`；全部读方法是 `findTarget/findOwned/list/summaries/lockOwned`，由 `MailReplyDraftService`、人工发送两入口及 `ManualReplySendAttemptService` 使用。`discard/closeSent` 清空正文，不能充当审核历史。E03/E04/E05；本功能不改此表及其状态。
- `mail_record`：V1 基表，V15 来源列，V23/V24 attempt 关联及唯一约束；当前映射见 `mail/domain/MailRecord.kt:8`，完整后续 DDL 见迁移链。`inbound_mail_processing`：V5 基表，V11 正文/处理列、V167 folder 唯一键；映射见 `mail/domain/InboundMailProcessing.kt:8`。会话唯一读取来源为 OUTBOUND mail_record + 已关联 contact 的 inbound_mail_processing，排除模拟账号，允许 disabled 账号；不能读 mail_record INBOUND 来替代 processing。E06；来源 K-mail-record-source-inbound-id、K-sender-account-enabled-scope。
- 上述两表全部业务写入口逐条列在 [写路径表](draft-review-evidence/write-paths.md)：AutoMailReplyService 的 processSingleCore/saveMailRecord/sendMeetingInvitation/confirmManualReviewWithBody/confirmProcessed/reclassifyFirstReplyAutoResponse；UnmatchedInboundMailService 的 bindToContact/markResolved；PendingMailOperationService.markResolved/cancelResolved；ManualExpertMailService.sendManualMailInternal；MeetingScheduleService.confirmMeetingAndEmail；ManualOutreachTxHelper.recordSuccess/recordFailure；ManualReplySendAttemptService.finalizeSuccess/finalizeFailure。直接 SQL 还包括 `InboundMailProcessingRepository.reopenManualResolved:71`、`scripts/reclassify-auto-responses.py:435,580`；历史迁移回填见迁移链。所有入口保持原语义，不为“对话版本”逐个加写钩子。
- 读入口全集按源码依赖与 SQL 扫描列在上方两份收据：会话/邮箱/监控、任务记录、人工/自动回复、AI/可信工作台/RAG、材料/附件/通知、退信、联系人管理/外联/排期/运营状态核对均消费这些表。新增上下文摘要只读同一会话 UNION；不改变上述读者。不要把这项新摘要当作全局邮件版本或更改 process_status 的依据。
- `mail_send_attempt`：V23/V24，唯一 `(orcid_id,mail_type)`、message_id；mail_record 的 attempt 外键唯一。写入口为 ManualInitialOutreachService.runIntroductionFromSnapshot、ManualOutreachTxHelper.recordSuccess/recordFailure、ManualReplySendAttemptService.prepareAndClaim/finalizeSuccess/finalizeFailure；repository insertIgnore/claimStatus/updateStatusAndError，加 CrudRepository.save。读取是上述发送服务及 MailReplyDraftRepository 关联读取；详见收据。审核发送只扩展既有人工发送 seam，不改公共 attempt schema。
- 无现成审核表/类/查询参数：`03-new-feature-absence.txt` 中 `mailbox_review_draft|MailReviewDraft|reviewPendingOnly` 0 命中。原型 localStorage、模拟 revision 不作数据库或权限证据。

### 本步定位
- `MailboxConversationRepository.kt:151/172/214` 是 count/page/explain 三入口，`expertPredicates:704` 共用专家成员条件；`havingClause:795` 实现待处理/等待回复；全部必须一致。
- `MailboxConversationController.kt:60/134/248` 是 item/list DTO与list handler；`MailboxConversationService.kt:94` 负责账号与分页规范化，现有 reader授权来自E07，不可改为前端过滤。
- 原前端 `mailbox-chat.js:2234/2257/2292` 直接读 `/mailbox/drafts`，其“草稿”不是审核状态。此步不改该入口。
- X5：01 SUBMIT/APPROVE/RETURN/WITHDRAW/DELETE 写新表 → count/page/badge 读取；X6：旧会话筛选与账号可见性 → 新待审成员查询；X7：旧reader会话接口 → 同一扩展DTO但不得暴露admin工作量。

## 实现方案
### T1：加可选参数和投影（I-8/I-9）
文件：`MailboxConversationController.kt`、`MailboxConversationService.kt`。
list新增 `reviewPendingOnly: Boolean=false`；item尾部 `reviewPendingCount:Long=0`；list尾部 `reviewPendingTotal:Long=0`。admin通过Service组装，reader返回0且reader传true=403。保持原待处理 `pendingCount` / `pendingOnly` 含义，不复用这些字段。

### T2：一套成员条件（I-8/I-9/I-10）
文件：`MailboxConversationRepository.kt`。
ConversationFilter新增reviewPendingOnly默认false。`expertPredicates` 增加相关EXISTS，匹配 d.expert_contact_id=ec.id、SUBMITTED、未deleted、真实目标账号、accountCode范围。count/page/explain自然复用，禁止各自写不同条件。
批量读取当前页contactIds的待审counts，空页不发IN()。`reviewPendingTotal` 用同一候选专家子查询（复用membership/expertPredicates；清除chip条件）JOIN待审草稿SUM/COUNT，不只对当前页求和；不在每个expert循环查数据库，不读取草稿正文。新读路径只消费01新表，01写路径无需调整。
排序继续既有会话排序，不按草稿id重复专家。默认false仍可投影同页counts给admin，普通邮件received/sent/pending字段不改。

### T3：MySQL计数与兼容测试（I-8–I-10）
文件：`MailReviewQueryIT.kt`、`MailboxConversationControllerTest.kt`。
数据至少：专家甲2待审、乙1待审、丙仅未提交/退回/已审核；甲跨两个账号，其中一个disabled但非模拟器；另有模拟器和无授权专家；分页size=1。逐状态变更后直接重查，覆盖count/page/explain共享条件。

## 变更文件清单
| # | 文件（相对仓库根目录） | 操作 / 边界 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | 新增reviewPendingOnly参数与计数字段 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt` | 同范围批量投影与reader隔离 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` | 复用成员条件，新增EXISTS和批量计数 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt` | 新增真实MySQL过滤/分页/计数测试 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 默认参数和reader回归 |

## 验收标准
- I-8：全范围 reviewPendingTotal=3，total=2；size=1翻两页分别是甲/乙且counts=2/1；不会出现一页空但后面还有待审的前端过滤错误。
- I-9：accountCode/q/chip切换各自口径断言；disabled真实账号计入、模拟器不计；reader正常查询count字段0、true参数403；旧未传参数响应原字段不变。
- I-10：批准甲一条后总数2、甲count1；再退回其另一条，total1且仅乙；撤权不删除admin队列但user1详情403；联系人删除无孤儿卡片。

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailReviewQueryIT,MailboxConversationRepositoryIT -DmysqlIt=true
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailboxConversationControllerTest
```
目标IT不得skipped；记录EXPLAIN与size100下查询条数，不写没有实测的性能承诺。

## 人工验收清单
### A-1: 三份草稿两位专家
- 前置条件: 按01接口创建甲2条/乙1条并提交，丙创建未提交草稿。
- 操作步骤:
  1. admin GET conversations?reviewPendingOnly=true&page=0&size=1，再取page=1。
  2. GET普通列表，再带user1会话GET同一URL。
- 预期结果: 总待审3、专家总数2，两页甲/乙每人各一次，counts2/1；普通列表不丢丙；user1指定待审参数403。
- 覆盖: 需求1；I-8/I-9；X5/X6/X7。
### A-2: 计数随审批及账号范围变化
- 前置条件: 甲2待审、乙1待审；甲两个真实账号，其中一个后台关闭自动发信。
- 操作步骤:
  1. admin批准甲一条后查列表，再退回另一条后查列表。
  2. 按甲两个accountCode分别查；user1查普通列表。
  3. 比较普通列表原receivedCount/sentCount/pendingCount与变更前记录。
- 预期结果: 总待审3→2→1；甲最后不在待审核列表；关闭自动发信的真实账号仍可读；user1新增计数为0；原邮件计数不因审核动作变化。
- 覆盖: 需求1、保持项；I-8/I-9/I-10；X5/X6/X7。

人工验收开始时才导出同前缀 `-acceptance.md`。

## Dispatch Boundary and Predecessor Evidence

Child product base: `c9454b6d5dd193c73b78dfbc99dbe58c8c70ae26` (child01 terminal Code head). Child01 evidence commit `72f87715e85df55ccf95678171cf597be7e78af5`; clean checkpoint `86f809eb9f1f6ae42eb676e426da2993ead7ae8c` validated complete manifest through01 with result VALID / CHILD_CHECKPOINT. Child01 LIGHT_PASS, zero repairs; implementation/context DTO/API contract is available in its committed sources and execution/verify-log. Preserve its streaming context reader in the shared MailboxConversationRepository.

Governing master is at `87a4ba94b070afebeb83d1c6b9fe885ed66a2aaa`, with A1–A4; historical source-baseline text does not roll back child01. User instruction “授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p” preauthorizes necessary in-goal bounded amendments: if you discover required unlisted files or actual contract drift, message controller with exact evidence/paths so the controller records plan changes before you touch them. Do not silently widen scope.


## Epoch 2 — Required Fixture Compatibility Amendment

Current exact child plan is `docs/plans/2026-10-29/draft-review-02-query.md` at `commit:2af9b9ca1025c5741086558365f92793357a307e`, superseding the original embedded identity; master at the same commit, ledger A5/A6. Scope is now six files, adding MailboxConversationRepositoryIT.kt only for the proven fixture INSERT missing dismissed_at/NOW(). Preserve all semantic assertions; use repository-default timezone. New query-count instrumentation is measured evidence, not a permanent fixed statement-count assertion. Required commands/I-8–I-10 unchanged.

Resume/reconcile existing uncommitted authorized implementation against this new execute-p identity; do not replay completed work or claim prior commands as current final verification. Preserve discovery/baseline failure history, append current execution epoch2, run all mandatory commands freshly after final changes and actual API/EXPLAIN/size100 smoke, then commit `feat(fast-p): implement 02` excluding fast-p docs.

