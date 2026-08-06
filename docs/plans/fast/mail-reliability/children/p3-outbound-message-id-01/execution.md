# Execution Report — p3-outbound-message-id-01

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability/docs/plans/2026-08-06/outbound-message-id-01-fill-missing.md`
- Plan SHA-256: `34cad950a97209be6a5a7eacef5ad103367326bf3bccecb7dab96050aee49916`
- Execution ID: `.../outbound-message-id-01-fill-missing.md@34cad950a97209be6a5a7eacef5ad103367326bf3bccecb7dab96050aee49916`
- Execution epoch: NEW
- Executor: ImplP3
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability`
- Target branch: `fast/mail-reliability`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability@fast/mail-reliability@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/mail-reliability`
- Pre-execution code SHA: `33e1ffb` (P1 code head; working HEAD at start `92b13aa` = ledger record)
- Post-execution code SHA: `025b875` (implementation commit)
- Evidence HEAD: `025b875`
- Implementation boundary: `33e1ffb..025b875`

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| 1.1 OutboundMessageIdFactory (newId, format, fail-fast, no domain literal/hostname/reverse-parse) | IMPLEMENTED | `src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundMessageIdFactory.kt` | FactoryTest 7/0; grep 无 weibo.com/szwebotech/hostname/InetAddress |
| 1.2 Factory 单测（7 cases: 形态/1000 唯一/无@/空域名/空 kind/空 discriminator/≤255） | IMPLEMENTED | `src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundMessageIdFactoryTest.kt` | `mvn test -Dtest=OutboundMessageIdFactoryTest` → 7/0 |
| 2.1 MeetingInvitationMailComposer.kt:22（meeting-invitation, expert.orcidId） | IMPLEMENTED | `MeetingInvitationMailComposer.kt` | ComposerTest 1/0 |
| 2.2 AutoMailReplyService.kt:567（auto-reply, contactId.toString()） | IMPLEMENTED | `AutoMailReplyService.kt`（仅该构造行） | AutoMailReplyServiceTest 40/0 |
| 2.3 AutoMailReplyService.kt:958（meeting-invitation, contact.orcidId；与 2.1 同 kind） | IMPLEMENTED | `AutoMailReplyService.kt`（仅该构造行） | IP-4 断言 kind 一致 |
| 3.1 MeetingScheduleService.kt:125（meeting-confirmation, contact.orcidId） | IMPLEMENTED | `MeetingScheduleService.kt` | MeetingScheduleServiceTest 5/0 |
| 4.1-4.3 调用点测试 | IMPLEMENTED | 3 个测试文件 | 分别 1/0、40/0、5/0 |
| Phase 6 知识写回（M-5 首写） | IMPLEMENTED | `K-message-id-fingerprint.md`（追加+created bump）、`K-outbound-message-id-single-factory.md`（新增，含 [[K-vendor-message-id-prefix.md]] 链接） | 见 diff |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test -Dtest=OutboundMessageIdFactoryTest` | PASS | exit 0；Tests run: 7, Failures: 0, Errors: 0 |
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test -Dtest=MeetingInvitationMailComposerTest` | PASS | exit 0；Tests run: 1, Failures: 0, Errors: 0 |
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test -Dtest=AutoMailReplyServiceTest` | PASS | exit 0；Tests run: 40, Failures: 0, Errors: 0（首次运行 1 失败，见 Deviations） |
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test -Dtest=MeetingScheduleServiceTest` | PASS | exit 0；Tests run: 5, Failures: 0, Errors: 0（首次运行 3 错误，见 Deviations） |
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0；Tests run: 2169, Failures: 0, Errors: 0, Skipped: 4（既有 skip）；JS 466/0 |
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0；BUILD SUCCESS；Tests run: 2169, Failures: 0, Errors: 0 |
| `git diff --check` | PASS | exit 0，无输出 |

## Changed Files（commit 025b875，10 files +200 -8）

- `src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundMessageIdFactory.kt` — 新增，任务 1.1
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposer.kt` — :22 加 messageId
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` — 仅 :567 与 :958 两处构造加 messageId
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt` — import + :125 加 messageId
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundMessageIdFactoryTest.kt` — 新增，7 用例
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposerTest.kt` — messageId 形态断言
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` — 两分支形态断言 + 重复发送 messageId 互异 + IP-4 kind 一致
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt` — messageId 形态 + 域名取自 stub 账号 senderEmail
- `docs/knowledge/mail/K-message-id-fingerprint.md` — 追加 2026-08-06 二次复验修正（缺失数 5→4、PendingMailOperationService 域名问题、ManualExpertMailService 由 3bff469 修复），created/last_used bump 至 2026-08-06
- `docs/knowledge/mail/K-outbound-message-id-single-factory.md` — 新增，含 [[K-vendor-message-id-prefix.md]] 链接（P2 创建）

## Invariant Checks

- I-1：4 个构造点均显式 `messageId = OutboundMessageIdFactory.newId(...)` ✓
- I-2：工厂无域名字面量/hostname/InetAddress/配置（grep 无命中）；4 个构造点域名参数均为 `account.senderEmail` ✓
- I-3：工厂无反向解析方法；1000 次唯一性通过；主代码无 kind 前缀解析（grep 无命中）；≤255 用例通过 ✓
- I-4：`ManualReplySendAttemptService.kt` 不在 diff ✓
- I-5：`IntroductionMailComposer.kt`、`ManualExpertMailService.kt` 零改动 ✓
- IP-4：`AutoMailReplyServiceTest` 断言两处 meeting-invitation kind 同为 `meeting-invitation` ✓
- M-4：`AutoMailReplyService.mailTemplateVariables()` 零改动（diff 仅两处构造）✓
- M-5：知识文件先读后追加；只写本 child 的修正，P2 的证伪更正未代写 ✓
- 禁止项：`SmtpMailDeliveryService.kt`、`PendingMailOperationService.kt`、任何 migration 零改动 ✓
- 未提交 `docs/plans/fast/**`（含本报告与 ledger.md，控制器自行提交）✓

## Deviations

1. `AutoMailReplyServiceTest` 首次运行 1 失败：IP-4 kind 提取用 `substringBefore("-")` 得到 `meeting`；改为 `Regex("^<([a-z-]+)-").find(...).groupValues[1]` 后通过（测试代码修复，产品代码未变）。
2. `MeetingScheduleServiceTest` 首次运行 3 错误：`Mockito.verify(...).send(eqValue(account), mailCaptor.capture())` 在 Mockito 2.21 + Kotlin 非空参数下抛 `NullPointerException: capture(...) must not be null`（capture() 返回 null 被 Kotlin 非空检查拦截）。改为本仓库既有模式 `thenAnswer { sentMails.add(invocation.getArgument(1)) }`（AutoMailReplyServiceTest 同款）后通过。
3. JS 测试数 459→466（基线后其他 child 已落地，非本批引入）；全量套件仍全绿。
4. `git diff --check` 在提交前运行一次（exit 0，无输出）；提交后工作树仅剩控制器文件 `docs/plans/fast/mail-reliability/ledger.md` 未暂存（未触碰）。

## Freshness

- Plan identity rechecked: YES（同 sha256）
- Worktree identity rechecked: YES（commit 前 --expect-root/--expect-branch/--expect-git-dir 通过）
- Reported commits reachable from target branch: YES（025b875 为分支 HEAD）
- Required commands run this invocation: YES（全部 7 条）
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
