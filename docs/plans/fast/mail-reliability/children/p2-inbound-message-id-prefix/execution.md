# Execution Report — p2-inbound-message-id-prefix

- Status: READY_FOR_VERIFICATION
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability/docs/plans/2026-08-06/inbound-message-id-vendor-prefix.md`
- Plan SHA-256: `614e2adb1049c858df1b870d690ea5cf09ebdf2c579a1c3745e485d855e931b3`（前后两次校验一致，未中途变更）
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability/docs/plans/2026-08-06/inbound-message-id-vendor-prefix.md@614e2adb1049c858df1b870d690ea5cf09ebdf2c579a1c3745e485d855e931b3`
- Execution epoch: NEW
- Executor: ImplP2（fast-p 末位 child）
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability`
- Target branch: `fast/mail-reliability`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability@fast/mail-reliability@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/mail-reliability`
- Pre-execution code SHA: `025b875`（P3 落地 code head）
- Implementation commit: `ef7e471`（`feat(fast-p): implement p2-inbound-message-id-prefix`，8 文件 +366 -4，仅授权文件）
- Evidence HEAD: `ef7e471`（= 分支 HEAD，已校验可到达性）

## 实现摘要

1. **`MessageIdNormalizer.kt`（新增，任务 1.1）**：`object MessageIdNormalizer`，`VENDOR_PREFIX = Regex("^[0-9A-F]{16}\\+")`；`canonicalize(raw)`（trim、空白→null、取第一个 `<...>` 片段否则包裹）；`stripVendorPrefix(bracketed)`（首个 `@` 切 local-part，前缀剥离后重包裹，剥离后为空返回入参）；`candidatesFor(raw)`（①原值 trim ②canonicalize ③strip(canonicalize)，空白过滤 + `distinct()` 保序去重）。类内无任何我方格式字面量（I-1 已验证：grep 无 `intro-`/`reminder-`/`manual-outreach-`/`manual-rich-` 命中）。
2. **`UnmatchedInboundMailService.suggestCandidates()`（任务 2.1）**：`findByMessageId(inReplyTo)` → `MessageIdNormalizer.candidatesFor(inReplyTo).firstNotNullOfOrNull { mailRecordRepository.findByMessageId(it) }`；其后 `IN_REPLY_TO` confidence=90 构造、`NAME_OR_EMAIL_MATCH`/`EMAIL_SIMILARITY` 段落逐字未动（I-5）。
3. **`BounceCollectionService.resolveOriginalContact()`（任务 2.2）**：`originalMessageId?.let` 内改为候选循环；`?: failedRecipient?.let` 兜底分支逐字未动（I-5）。NOID 形态 `candidatesFor` 产出 `["NOID:xxx","<NOID:xxx>"]`，第二个查询落空 → 行为与改动前一致。
4. **测试**：`MessageIdNormalizerTest`（新增，13 用例 = 计划 11 表用例 + canonicalize trim + strip 无尖括号防御路径；含小写 hex/长度 6/17/local-part 含 `+`/剥离后为空 5 个不剥离用例、JavaMail 默认格式剥离用例、多 msg-id 取首段、顺序+去重断言）。`UnmatchedInboundMailServiceTest`（+3：带前缀命中无前缀落库且 IN_REPLY_TO 首位 confidence=90、null inReplyTo 不查库无 IN_REPLY_TO、带前缀无记录落到 NAME_OR_EMAIL_MATCH 60）。`BounceCollectionServiceTest`（+2：带前缀 originalMessageId 命中原联系人、NOID 形态落 failedRecipient 兜底）。
5. **Phase 6 知识写回（M-5 后写者）**：
   - 写前已读 `K-message-id-fingerprint.md` 当前内容，确认 P3 三处修正均在：缺失数 5→4（二次复验修正段）、`PendingMailOperationService` 域名硬编码问题、`ManualExpertMailService` 3bff469 已修复 → **通过，无 PLAN_CONFLICT**。
   - **追加**三次复验修正段：证伪「落库值与实际发出值一致」→ 库内值=交给中继前值、腾讯企业邮投递加 `[0-9A-F]{16}+` 前缀、库内值≠实际投递值，并链接新知识条目。`created` 已是 2026-08-06（同日复验，数值无变化）。
   - **新建** `K-vendor-message-id-prefix.md`：两个观测样本（6136051B41AACA62+ / ED4DEF51D75D746B+，跨月跨集群）、两个受影响读路径、读侧有界剥离 + 精确相等规则、写侧零改动，`[[K-outbound-message-id-single-factory.md]]` 链接（P3 已建条目已反向链接本文件）。
6. **禁止项确认**：未触碰 `SmtpMailDeliveryService.kt`/`ImapMailReceiveService.kt`/任一 `ComposedMail` 构造点/`MailRecordRepository.kt`/`OutboundMessageIdFactory.kt`/migration/前端。未新增任何 Repository 方法；diff 中无 `LIKE`/`Containing`/`EndingWith`/`StartingWith` 新增（唯一命中是 `ExpertContactRepository` 既有调用的上下文行）。grep 确认 `MessageIdNormalizer` 仅出现在 2 个服务 + 2 个测试文件（I-3）。

## 命令摘要（全部 cwd = worktree，JAVA_HOME=zulu-11）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `JAVA_HOME=… mvn test` | 0 | Tests run: 2187, Failures: 0, Errors: 0, Skipped: 4（Skipped 为基线既有）; BUILD SUCCESS |
| `JAVA_HOME=… mvn test -Dtest=MessageIdNormalizerTest` | 0 | Tests run: 13, Failures: 0, Errors: 0; BUILD SUCCESS |
| `JAVA_HOME=… mvn test -Dtest=UnmatchedInboundMailServiceTest` | 0 | Tests run: 10, Failures: 0, Errors: 0; BUILD SUCCESS |
| `JAVA_HOME=… mvn test -Dtest=BounceCollectionServiceTest` | 0 | Tests run: 4, Failures: 0, Errors: 0; BUILD SUCCESS |
| `JAVA_HOME=… mvn clean package` | 0 | Tests run: 2187, Failures: 0, Errors: 0, Skipped: 4; BUILD SUCCESS |
| `git diff --check` | 0 | 无输出 |

> 首轮 `MessageIdNormalizerTest` 曾 1 失败（`stripVendorPrefix on unbracketed input` 用例断言写错期望值：无尖括号输入实现为「剥离并重包裹」输出 `<plain@d.cn>`，测试误写为原样返回），修正测试期望后 13/13 通过；实现本身未再改动。计划验证节的三类合并命令 `-Dtest='MessageIdNormalizerTest,UnmatchedInboundMailServiceTest,BounceCollectionServiceTest'` 未单独执行，但三类已分别全绿且全量 `mvn test`（含三类）通过，覆盖更强。

## 变更文件（提交 ef7e471，8 个授权文件）

- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MessageIdNormalizer.kt` — 新增，读侧归一化工具（I-1/I-2/I-4）
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt` — suggestCandidates 候选循环（I-2/I-4/I-5）
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt` — resolveOriginalContact 候选循环（I-2/I-4/I-5）
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MessageIdNormalizerTest.kt` — 新增，13 用例（I-1/I-4）
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt` — +3 用例（I-4/I-5）
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt` — +2 用例（I-4/I-5）
- `docs/knowledge/mail/K-message-id-fingerprint.md` — Phase 6 追加三次复验修正（M-5）
- `docs/knowledge/mail/K-vendor-message-id-prefix.md` — 新增知识条目

## 偏差

- 无偏差。`docs/plans/fast/mail-reliability/ledger.md` 的未暂存改动为 controller 既有工作，未纳入提交。

## Freshness

- Plan identity rechecked: YES（sha256 一致）
- Worktree identity rechecked: YES（root/branch/git_dir 一致）
- Reported commit reachable from target branch: YES（ef7e471 = 分支 HEAD，父为 68536d8）
- Required commands run this invocation: YES
- Historical evidence used only as baseline: YES

## 剩余阻塞

- 无。J-1 联合验收（P3×P2 闭环）由人工执行，不在本 child 范围。

## Next Action

- 运行 `verify-p`（对照本计划验收标准：I-1..I-5、grep 门禁、回归全绿）。
