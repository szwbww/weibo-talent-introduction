# Fast-P Child Brief — p3-outbound-message-id-01

- Master plan (global authority): `docs/plans/2026-08-06/00-main-plan-mail-reliability.md` — 执行顺序 ③。
- Child plan（唯一权威文本）: `docs/plans/2026-08-06/outbound-message-id-01-fill-missing.md` —— 全部阶段 1-4、验收标准、验证命令、Phase 6 均以该文件为准。
- child_base_sha: 由 controller 在启动本 child 时写入（= 前一 child 的 code head）。
- 工作区：`/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability`（分支 `fast/mail-reliability`）。

## Authorized Files（排他，M-1）

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundMessageIdFactory.kt`（新增，任务 1.1）
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposer.kt`（任务 2.1）
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`（任务 2.2/2.3，**仅 `:567` 与 `:958` 两处 `ComposedMail(` 构造**）
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt`（任务 3.1）
5. `src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundMessageIdFactoryTest.kt`（新增，任务 1.2）
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposerTest.kt`（任务 4.1）
7. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`（任务 4.2）
8. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt`（任务 4.3）

**知识写回（Phase 6，M-5 串行规则）**：
9. `docs/knowledge/mail/K-message-id-fingerprint.md`（修改，见下）
10. `docs/knowledge/mail/K-outbound-message-id-single-factory.md`（新增）

**禁止触碰**：`IntroductionMailComposer.kt`（除 ComposedMail 外零改动；本批不改 ComposedMail）、`ManualExpertMailService.kt`、`ManualInitialOutreachService.kt`、`ManualReplySendAttemptService.kt`（I-4：一个字节都不许动）、`SmtpMailDeliveryService.kt`、`PendingMailOperationService.kt`、任何 migration。

## Key Invariants

- **I-1**：4 个构造点必须显式设置 `messageId`，由 `OutboundMessageIdFactory` 产出；禁止依赖 JavaMail 默认。
- **I-2**：域名 = `account.senderEmail.substringAfter("@")`（本次投递实际账号）；工厂内**禁止**任何域名字面量/hostname/配置；`senderEmail` 无 `@` 或域名为空 → `IllegalArgumentException`（fail-fast）。
- **I-3**：格式 `<{kind}-{discriminator}-{uuid}@{domain}>`，唯一性只依赖 `UUID.randomUUID()`；工厂**禁止**提供反向解析方法；无人 grep 前缀做逻辑判断。
- **I-4**：`ManualReplySendAttemptService.kt` 零改动（diff 证明不在变更中）。
- **I-5**：`IntroductionMailComposer.kt:28` 与 `ManualExpertMailService.kt:192` **零改动**（默认不改；若要接入 helper 必须逐字节等价 + 等价性测试，否则视为违反）。
- **IP-4**：`MeetingInvitationMailComposer` 与 `AutoMailReplyService:958` 两处 `kind` 必须完全相同（`"meeting-invitation"`），discriminator 均为 ORCID。
- **M-2**：`OutboundMessageIdFactory` 是写侧专用；**P2 的 `MessageIdNormalizer` 不得被本批调用**、不得互相引用、不得共享常量；`kind` 值（`meeting-invitation`/`auto-reply`/`meeting-confirmation`）不得进入 P2 的代码/测试断言（本 child 只需保证自己侧干净）。
- **M-3**：不新增任何 Flyway migration。
- **M-4**：改 `AutoMailReplyService.kt` 仅限 `:567` 与 `:958` 两处构造；`mailTemplateVariables()` 方法体**零改动**（P4 J-4 以方法为粒度断言）。
- **M-5（知识写回，本批次 P3 先于 P2）**：写回 `K-message-id-fingerprint.md` 前**先读当前内容**；P2 的更正（库内值≠实际投递值）此刻**尚不存在**，本 child 只追加自己的两处修正（缺失数 5→4、`PendingMailOperationService` 实为域名问题非缺失、`ManualExpertMailService` 已由 `3bff469` 修复），**追加而非覆盖**，并 bump `created`（re-validated）。新文件 `K-outbound-message-id-single-factory.md` 必须含 `[[链接]]` 指向 `K-vendor-message-id-prefix.md`（P2 将创建并链接回来）—— 同一事实的写侧与读侧。

## Required Commands（全部运行并记录退出码）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OutboundMessageIdFactoryTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MeetingInvitationMailComposerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AutoMailReplyServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MeetingScheduleServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0 且含 `Tests run: N, Failures: 0, Errors: 0`；`git diff --check` 无输出。

## Downstream Interfaces

- 给 P2 的接口：`MessageIdNormalizer` 必须对 `meeting-invitation-`/`auto-reply-`/`meeting-confirmation-` 格式**格式无关**（P2 的 I-1 已保证；本 child 不需配合，但知识文件须互相链接）。
- 给 P4 其余阶段（推迟）的接口：`AutoMailReplyService.mailTemplateVariables()` 保持零改动（M-4）；`SmtpMailDeliveryService.kt` 零改动（M-1）。
- `K-message-id-fingerprint.md` 的 P2 更正（末段证伪）由 P2 在后续追加 —— 本 child 不得替 P2 写。

## Deliverables

1. 实现提交：`feat(fast-p): implement p3-outbound-message-id-01`（只含上述 8+2 文件；含知识写回）。
2. 执行报告：`docs/plans/fast/mail-reliability/children/p3-outbound-message-id-01/execution.md`（不提交）。
3. 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`，commit SHA，命令摘要，报告路径。
