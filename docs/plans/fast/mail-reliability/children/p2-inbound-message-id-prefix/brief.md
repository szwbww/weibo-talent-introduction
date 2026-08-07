# Fast-P Child Brief — p2-inbound-message-id-prefix

- Master plan (global authority): `docs/plans/2026-08-06/00-main-plan-mail-reliability.md` — 执行顺序 ④（最后）。
- Child plan（唯一权威文本）: `docs/plans/2026-08-06/inbound-message-id-vendor-prefix.md` —— 全部阶段 1-2、验收标准、验证命令、Phase 6 均以该文件为准。
- child_base_sha: 由 controller 在启动本 child 时写入（= 前一 child 的 code head）。
- 工作区：`/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability`（分支 `fast/mail-reliability`）。

## Authorized Files（排他，M-1）

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MessageIdNormalizer.kt`（新增，任务 1.1）
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`（任务 2.1）
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt`（任务 2.2）
4. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MessageIdNormalizerTest.kt`（新增，任务 1.2）
5. `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt`（任务 2.3）
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt`（任务 2.3）

**知识写回（Phase 6，M-5 串行规则 —— 本 child 是后写者）**：
7. `docs/knowledge/mail/K-message-id-fingerprint.md`（修改，见下）
8. `docs/knowledge/mail/K-vendor-message-id-prefix.md`（新增）

**禁止触碰**：`SmtpMailDeliveryService.kt`、`ImapMailReceiveService.kt`、任一 `ComposedMail` 构造点、`MailRecordRepository.kt`、任何 migration、任何前端文件。

## Key Invariants

- **I-1**：`stripVendorPrefix` 仅剥离 `^[0-9A-F]{16}\+`（16 位大写 hex + `+`）；剥离后**不做任何格式假设**；不匹配则原样返回。类内**禁止**出现 `intro-`/`reminder-`/`manual-outreach-`/`manual-rich-` 字面量。
- **I-2**：只允许 `MailRecordRepository.findByMessageId(exactValue)` 精确相等；**禁止**任何 `LIKE`/`Containing`/`EndingWith`/`StartingWith`/新派生方法；前缀兼容靠"有限候选 + 逐个精确查"。
- **I-3**：归一化只作用于读匹配侧；`mail_record.message_id`/`in_reply_to` 落库值逐字不变；grep 确认 `MessageIdNormalizer` 仅出现在 `UnmatchedInboundMailService.kt`、`BounceCollectionService.kt` 及两个测试文件。
- **I-4**：候选顺序 ①原值(trim) ②`canonicalize` ③`stripVendorPrefix(canonicalize)`，过滤空白、`distinct()` 保序去重，首个非 null 命中即返回；`candidatesFor` 顺序与去重须被测试断言。
- **I-5**：`IN_REPLY_TO` confidence=90 且位于候选首位；`NAME_OR_EMAIL_MATCH` 60 及去重逻辑不变；`resolveOriginalContact` 的 `failedRecipient` 兜底分支逐字不变。
- **M-2**：`MessageIdNormalizer` 是读侧专用，**禁止**被写侧（`SmtpMailDeliveryService`/`OutboundMessageIdFactory`）调用；**禁止**识别/断言写侧 `kind`（`meeting-invitation`/`auto-reply`/`meeting-confirmation`）；与 `OutboundMessageIdFactory` 零引用、零共享常量。
- **M-3**：不新增任何 Flyway migration。
- **M-5（后写者复核前写者）**：写回 `K-message-id-fingerprint.md` **之前必须先读当前内容**，确认 P3 的两处修正（缺失数 5→4、`PendingMailOperationService` 域名问题、`ManualExpertMailService` 已修复）**已存在**；若不存在**停止并报告 PLAN_CONFLICT**。然后**追加**本 child 的更正（末段"落库值与实际发出值一致"证伪 → 库内值=交给中继前值、中继加 `[0-9A-F]{16}+` 前缀），bump `created`（re-validated）。新文件 `K-vendor-message-id-prefix.md` 记录观测事实（两个样本）、受影响两个读路径、读侧归一化规则，并 `[[链接]]` `K-outbound-message-id-single-factory.md`（P3 已创建）。

## Required Commands（全部运行并记录退出码）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MessageIdNormalizerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnmatchedInboundMailServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BounceCollectionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0 且含 `Tests run: N, Failures: 0, Errors: 0`；`git diff --check` 无输出。

## Downstream Interfaces

- 无下游（本 child 是批次最后一个）。J-1 联合验收（P3×P2 闭环）由人工在四份全落地后执行，不在本 child 范围。
- 给 P1 的接口：`UnmatchedInboundMailService.suggestCandidates()` 的 `IN_REPLY_TO` 候选形态与置信度不变（P1 验收对象 `TEST-LUKAI-18014905480` 依赖）。

## Deliverables

1. 实现提交：`feat(fast-p): implement p2-inbound-message-id-prefix`（只含上述 6+2 文件；含知识写回）。
2. 执行报告：`docs/plans/fast/mail-reliability/children/p2-inbound-message-id-prefix/execution.md`（不提交）。
3. 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`，commit SHA，命令摘要，报告路径。
