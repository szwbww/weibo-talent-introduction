# Child 04 Execution Report — 全部收信分支的元数据登记与幂等

- Plan: docs/plans/2026-09-07/04-inbound-metadata-persistence.md
- Plan SHA-256: 5c94faa16aaf6ee314b99acbe2955dd22db9cd62842b927bb61405aa9ff21cde
- Execution ID: 04-inbound-metadata-persistence.md@5c94faa16aaf6ee314b99acbe2955dd22db9cd62842b927bb61405aa9ff21cde
- Epoch: NEW（本 invocation 前无 04 实现证据）
- Executor: Impl04 (fast-p mailbox-materials child 04, sole writer)
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials
- Target branch: fast/mailbox-materials
- Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials@fast/mailbox-materials@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-materials
- Base SHA (child 03 code head): 95d8661ed3d9e0557fe69cce6f6dde24dbea5e79
- Pre-execution HEAD: e0db1ea5e67d285a6688430ec670a5a34ab22aba (03 evidence commit, docs-only)
- Implementation commit: **5f5623dc4df04b701aa37e0904ec07579d6ba8cb** `feat(fast-p): implement 04`
- Fix round 1 commit: **c9f8090** `fix(fast-p): repair 04 round 1`（A-1，见 fix-log.md）
- Post-execution code SHA: c9f8090

## Changed files (exactly the brief's 10 authorized files + round-1 repair files)

| # | Path | Change |
|---|---|---|
| 1 | src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt | 修改：processSingle 拆为事务包装器 + core；dedupe 改真实代际；LEGACY_UID_UNVERIFIABLE / BODY_TRUNCATED 人工路由；confirm 统一在事务提交后单点 markSeen；构造纳入 AttachmentTransferService + TransactionTemplate；新 outcome 枚举 x2 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt | 修改：两保存入口按 content 分流；metadata 登记（transfer 源唯一锁、零文件 I/O）；bridgeInboundProcessing；ensureDocumentsForProcessingAttachments（开关门控） |
| 3 | src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt | 修改：bindToContact 内幂等补 ExpertDocument（mailAttachmentService 依赖） |
| 4 | src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt | 修改：uidValidity BIGINT=0 字段 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt | 修改：新增 account/uid_validity/uid finder；旧 finder 保留（兼容读取/代际认领核验注释） |
| 6 | src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql | 新增：uid_validity 列 DEFAULT 0；唯一键换 (account,uid_validity,uid)；历史不回填 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt | 修改：mock 全部真实 UIDVALIDITY（reply 默认 uidValidity=1L 与 fetch 对齐）；新增 7 个覆盖（见下） |
| 8 | src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt | 修改：新增 metadata 39/1000 索引、同名不合并、20 件失败中止、桥接幂等/缺行失败、processing-owner 登记、开关门控测试（2→11） |
| 9 | src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt | 修改：构造依赖 + 绑定补文档断言（10→11） |
| 10 | src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt | 修改：迁移目标 119→120（fresh/V23/V24/V117/V118/V119 测试）；新增 V120 契约断言测试（14→15） |

无其他生产/测试文件改动；实现提交 git show --stat = 10 文件（+1321/−133）；修复提交 = 4 文件（MailAttachmentService.kt、AutoMailReplyService.kt、MailAttachmentServiceTest.kt、AutoMailReplyServiceTest.kt，+2 回归测试）。

## Round-1 AUTO_FIX A-1（bridge 只约束 metadata 附件）

child-03 fetch 在两种模式都无条件填 ReceivedMailAttachment.source；旧 content 模式附件经旧路径完整落库但不建 transfer 行，而 bridge 原按 source!=null 判 metadata → 默认 metadataOnly=false 下已匹配来信带附件在 confirm 回滚。修复：`MailAttachmentService.bridgeInboundProcessing` 先跳过 `content != null` 附件（`if (received.content != null) return@forEach`），完整性约束只适用于 metadata（content==null）附件；混合信内 metadata 附件仍严格缺行即确认失败。回归测试：MailAttachmentServiceTest `bridge skips legacy content attachments with remote source and stays strict for metadata`（真实旧路径文件落库、bridge 零 transfer 交互、混合严格性）；AutoMailReplyServiceTest `matched content mode message with attachments confirms through the default bridge path`（content+source 附件走默认 bridgeMetadata=true 正常确认 + markSeen + 无自动回复）。

## Required commands（修复后最终状态重新运行）

### 1) 定向测试
`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AutoMailReplyServiceTest,MailAttachmentServiceTest,UnmatchedInboundMailServiceTest`
→ **PASS**（exit 0，BUILD SUCCESS）。surefire：
- AutoMailReplyServiceTest：50 run / 0 F / 0 E（42→50，+8）
- MailAttachmentServiceTest：12 run / 0 F / 0 E（2→12，+10）
- UnmatchedInboundMailServiceTest：11 run / 0 F / 0 E（10→11，+1）
小计 73/0/0；exec 插件 Node 671/671 pass。

### 2) Flyway 迁移 IT（环境 workaround）
`JAVA_HOME=… DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test`
→ **PASS**（exit 0，BUILD SUCCESS）。FlywayMigrationIntegrationTest：**15 run / 0 F / 0 E**（113s，mysql:8.0.36 testcontainer），含新增 `V120 scopes inbound uid uniqueness by uid validity without backfilling history`。

### 3) 全量套件
`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
→ **PASS**（exit 0，BUILD SUCCESS）。fresh surefire reports（本次运行 mtime 内）：**3152 run / 0 fail / 0 err / 8 skipped**（8 skipped = migrationIt/mysqlIt 容器级门控类）。排除早前 -Pmysql-it 运行的陈旧 report（AttachmentTransferWorkerIT 12、ImapMetadataFetchIT 6）。Node：671/671 pass。

## Invariant checks

- **I-1（UID 代际）**：V120 加 uid_validity NOT NULL DEFAULT 0 且不 backfill；新收信 require(uidValidity>0) fail-closed（未知 source 不落库不 markSeen）；dedupe 走 (account,uid_validity,uid) finder（测试钉死查询参数）；旧 finder 仅用于 0 代际行「非空 Message-ID+from+秒级 receivedAt 全吻合才认领」；不吻合 → MANUAL_REVIEW/LEGACY_UID_UNVERIFIABLE（旧行绝不改写/反写未知 source）。测试：`same uid under a new uid validity…`、`legacy zero validity row with corroborated identity…`、`legacy zero validity row with unverifiable identity…`、`receipt without positive uid validity fails closed…`、Flyway V120 契约。
- **I-2（索引提交与确认）**：processSingle 实际执行主体在显式 TransactionTemplate（复用 worker 的 REQUIRES_NEW bean）内完成全部 DB 写（含附件/transfer/桥接/状态/审计），事务提交成功后单一确认点 markSeen（skipImapAck 生效）；登记失败抛错回滚 → UID 未确认、游标不推进；metadata 登记 39 件第 20 件失败 → 无部分索引（`failure on the 20th…` + AutoMailReplyService `attachment registration failure aborts before smtp…`：processing 未 save、markSeen never、SMTP never）；桥接缺行即确认失败（`bridgeInboundProcessing fails the confirmation…`）；已确认 UID 重复只 DUPLICATE_IMAP_UID（`processByUids returns duplicate…` + `legacy…corroborated…`），不重跑自动回复。
- **I-3（owner XOR 与桥接）**：V36 XOR 未动；已匹配分支保持 record owner、未匹配/无首信/存疑 processing owner；transfer.inbound_processing_id 在 confirm 桥接（record owner）或登记即带（processing owner）；`bridgeInboundProcessing links all registered transfers…`（幂等：二次调用零改写）；绑定只 `ensureDocumentsForProcessingAttachments` 幂等补同 attachmentId 文档（MailAttachmentServiceTest + UnmatchedInboundMailServiceTest：never saveUnmatched/saveInbound、零文件 I/O）；无 owner 搬迁、无 review 状态改写；历史已绑定未建档不回填（06 入口）。
- **I-4（分支与审核独立）**：附件名 CV/DOCS 意图路由未改（inferPrimaryIntentFromAttachments 原样）；bodyTruncated → 仅 MANUAL_REVIEW/BODY_TRUNCATED 且不触自动回复/状态迁移（`truncated inbound body routes manual BODY_TRUNCATED and never auto replies`）；下载/登记不写 document_status（文档默认 PENDING_REVIEW，已存在文档不触碰）；content 路径保持旧写法（两个旧单元测试逐字段验证 storagePath/fileSize/mkdir/Files.write 行为原样，且 `saves attachment file…` 断言 verifyNoInteractions(transferRepository)）；global off/专家 off/重复正文/会议/QA 全部分支回归绿（全量套件 + 49 个 AutoMailReplyServiceTest 场景）。

## Deviations / recorded notes

- 无计划外文件、无范围外行为修复。attachmentTransferService 已按计划纳入 AutoMailReplyService 构造（plan 实现方案 6；05 机器报告登记复用该依赖、不再加构造参数）；04 的「最终 bridge 核验」由 MailAttachmentService.bridgeInboundProcessing 在 confirm 确认点执行（严格 require 每个 metadata 附件已有登记行）——attachmentTransferService 在 04 中暂无直接调用点，属按计划预留接线（已在代码注释说明）。
- 旧模式 INTRODUCTION_NOT_SENT 分支此前漏存附件（audit E3 已标注缺陷），现按「所有分支最终登记一次」以旧 content 路径补登记（文件 + 已知专家 doc）；未匹配旧模式行为逐字节不变。四个已匹配旧调用点的 content 路径语义不变。
- SMTP-success/DB-failure 的事务外副作用历史风险按计划仅记录（代码注释 + 本报告），不声称消除；不引入 DB 异常后的自动 SMTP 重试。
- 新 outcome 枚举值 LEGACY_UID_UNVERIFIABLE / BODY_TRUNCATED 已加入 MANUAL_REVIEW_OUTCOMES（batch manualReview 计数一致）；无 exhaustive when 消费者受影响。
- 迁移编号核实：V119 为最高（child 02），V120 新建、未覆盖任何已应用迁移。

## Remaining concerns

- 0 代际「同 UID 不可核验」目前一律转人工；若未来能拿到可靠的历史 UIDVALIDITY 证据，可再收紧（不属本计划）。
- bridge 完整性以「metadata 附件登记行必须存在」为界；登记与桥接同事务，事务外不补跑。
- 生产 metadataOnly 仍默认 false（03/11 控制启用）；本实现按「content 缺失 ⇒ metadata 登记」逐附件自动分流，开关翻转无需改码。

## Freshness

- Plan identity rechecked: YES（sha256 不变 5c94faa1…，实现与修复两个 invocation 均复核）
- Worktree identity rechecked: YES（--expect-root/--expect-branch/--expect-git-dir 全部通过）
- Commits reachable from target branch: YES（5f5623dc…、c9f8090…，HEAD = c9f8090 of fast/mailbox-materials）
- Required commands run this invocation: YES（三条全部 fresh，修复后最终状态）
- Historical evidence used only as baseline: YES
- Tracked tree clean post-commit（仅 controller 证据 untracked：docs/plans/fast/**）

## Result

**READY_FOR_VERIFICATION** — 实现 5f5623dc4df04b701aa37e0904ec07579d6ba8cb + 修复 c9f8090（round 1/3，A-1 FIXED）；命令摘要：1) 73 tests exit 0；2) Flyway IT 15 tests exit 0；3) full suite fresh 3152/0/0/8 exit 0。完整 round-1 记录见 fix-log.md。
