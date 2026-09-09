# fast-p child 02 execution — MIME附件、防重和邮件存档

## Execution Result: READY_FOR_VERIFICATION

- Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation/docs/plans/2026-09-09/02-meeting-confirmation-delivery.md
- Plan SHA-256: `6f87732ccdbeb8f4b0b3199987a3e32f15fa7d062ec63b7f085a5c1a43c54109`（执行前与执行后一致，未变更）
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation/docs/plans/2026-09-09/02-meeting-confirmation-delivery.md@6f87732ccdbeb8f4b0b3199987a3e32f15fa7d062ec63b7f085a5c1a43c54109`
- Execution epoch: NEW（本分支无先前 02 执行证据）
- Approval basis: current invocation（brief + child plan，brief 在 docs/plans/fast/meeting-confirmation/children/02/brief.md；child plan 与本报告同 commit 6d05499 种子）
- Executor: ImplementChild02
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation
- Target branch: fast/meeting-confirmation
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation@fast/meeting-confirmation@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-meeting-confirmation`
- Pre-execution code SHA: `73c53fe6689f14ddbab48d9f8724b651c036a469`（child base = child 01 terminal code head）
- Post-execution code SHA: `6a1b54697778bdea64002516d5c63fbabedd0f13`（`feat(fast-p): implement 02`，8 授权文件）
- Implementation boundary: `73c53fe..6a1b546`

## 与计划的偏差

- 计划/简报对最新版本断言写「10 处 122→123」；实际该测试文件中 `flyway`/`flyway()` 迁移目标断言为 **11 处**（`grep -c '"122", flyway` = 11），已全部改到 123；V23/V24 定向断言与 V116 定向断言未动，V23 文件 checksum 断言未动。非偏差的计数精确化，不改任何验收语义。
- 其余实现与 T1..T4 / I-1..I-4 / IP-3/4/5 一致，无方案外改动；8 文件白名单外零改动。

## Task Status

| 计划任务 | 状态 | 文件 | 证据 |
|---|---|---|---|
| T1 V123 列 + MailRecord 尾字段 + Flyway 最新断言 | IMPLEMENTED | `V123__add_mail_record_calendar_attachment.sql`（新）、`mail/domain/MailRecord.kt`、`FlywayMigrationIntegrationTest.kt` | 迁移 IT `V123 adds nullable calendar attachment json column leaving history null` PASS：`calendar_attachment_json` LONGTEXT、`IS_NULLABLE=YES`、历史行(201/202)迁移后 NULL、库内无任何非 NULL 行；11 处最新版本断言 123 |
| T2 SendPayload 尾字段 + 语义指纹 + 四支快照存档 | IMPLEMENTED | `mail/service/ManualReplySendAttemptService.kt` | golden 断言 `fa838d…becda` PASS（无 calendar 字节流逐字不变）；`calendar attempt dedup sent / unknown / claims safe retry` PASS；new/copy 成功失败四支快照断言 PASS（见下） |
| T3 ComposedMail 尾字段 + SMTP mixed 分支 | IMPLEMENTED | `mail/service/IntroductionMailComposer.kt`、`mail/service/SmtpMailDeliveryService.kt` | SMTP MIME 回归 5 个新用例 PASS（mixed 恰好 2 part / alternative(plain,html) 恰 2 part / ICS 字节 writeTo→reparse 往返完全相等 / 纯文+附件 / .eml fixture 保存）；旧 24 个无附件用例逐字保留全部 PASS |
| T4 两测试扩展 + Flyway 测试 | IMPLEMENTED | `SmtpMailDeliveryServiceTest.kt`、`ManualReplySendAttemptServiceTest.kt`、`FlywayMigrationIntegrationTest.kt` | 见下方命令与验收标准逐条 |

## Commands

| 命令 | 结果 | 证据 |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualReplySendAttemptServiceTest,SmtpMailDeliveryServiceTest` | PASS | exit 0；Tests run: 62（33 + 29）, Failures: 0, Errors: 0, Skipped: 0 |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=… mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40` | PASS | exit 0；Tests run: 19, Failures: 0, Errors: 0, Skipped: 0（含 V123 新用例）；node 766/0 |
| `JAVA_HOME=… mvn test`（全量，不加 migrationIt） | PASS | exit 0；Java Tests run: 3300, Failures: 0, Errors: 0, Skipped: 9（migration IT 门控跳过）；node tests 766, pass 766, fail 0 |

对比基线（见简报）：seed 3234 run/0 fail/9 skipped + node 766/0；child-01 head 3283 + node 766/0；本 child 增加 17 个 Java 用例 → 3300，node 766 不变，与期望一致。

## 验收标准逐条证据

- I-1（只有一列和一种 absence）：迁移后旧行 NULL、库内无非 NULL 行（V123 IT）；new/copy 成功/失败四支均持久化 `CalendarAttachmentCodec.serialize(同一快照)` 且 `parseOrNull` 往返相等（`finalizeSuccess new branch persists…` / `finalizeFailure new branch persists…` / `finalizeSuccess copy branch overwrites old snapshot…` / `calendar attempt claims safe retry…`）；无 calendar 的 new 成功/失败（既有两用例扩展）与 copy 成功/失败（`copy branch without calendar clears prior…` / `finalizeFailure copy branch clears…`）均显式 null，不沿用安全失败旧附件。
- I-2（发件身份含日历语义）：golden 无日历全量哈希冻结 `fa838dbceec46871ec1eae26e9ba4666d6f1ac0fda1bd36f872110efd38becda` 逐字断言 PASS；日历字段加入后 shortKey 仍 `MANUAL_RICH:`+32、fullHex 64；same-semantics 不同 generatedAt（ICS 字节不同）指纹相同；改 zoomUrl 指纹不同；有/无日历指纹不同。
- I-3（投递内容与快照相同）：01 真实 preview 产物 → 真实 MIME 构造 → Mockito 捕获真实 MimeMessage → writeTo → 重新 `MimeMessage` 解析；mixed 恰 2 part、首 part alternative 恰 plain/html 2 part（或纯文 1 part）、ICS part `text/calendar; charset=UTF-8` + attachment + `meeting-2026-09-11-Professor-Basdogan.ics`，解码字节与 snapshot.icsText UTF-8 字节 `assertArrayEquals` 完全相等；Message-ID / From 显示名 / In-Reply-To / References / List-Unsubscribe 位置不变；无附件分支形状回归（multipart/alternative 单结构）PASS。
- I-4（状态机不分叉）：SENT→DEDUP_SENT 不重发；DELIVERY_UNKNOWN→UNKNOWN fail-closed（verify claimStatus never）；FAILED_SAFE_TO_RETRY→SAFE_RETRY_CLAIMED 可认领、重试成功在同一 record（copy 分支）收敛为 SENT；失败 `sentAt=null`（finalizeFailure new + copy 断言）。
- IP-3/4/5：01 生成器真实产物（preview → `MeetingPreviewResponse.attachment` → `CalendarAttachmentSnapshot`）直接送入指纹/MIME/finalize 测试；未手写任何成对假 ICS 串；不新增 mail_attachment/expert_document 写入，材料数量路径未触碰。
- A-1：SMTP 测试真实产出 `.eml` 已保存 `target/meeting-confirmation.eml`（2118 字节，SHA-256 `dc969bcf07ba4cb48445b210eae9f1c21c37153c097a8bc07ed87ab10caf3689`）；附件名 `meeting-2026-09-11-Professor-Basdogan.ics`、chinaTime 前缀 `2026/09/11`（15:00–15:30 中国，断言于 `realMeetingSnapshot`），附件解码字节 = snapshot icsText UTF-8 字节（与 01 样例同配置可比对）。
- A-2：无 calendar/外联/QA/RAG 回归 —— 旧 21→33 用例外其余全量 3300 全绿（含 InitialOutreach/ManualInitialOutreach/QA/RAG 套件），材料数量断言未触碰；`fingerprint without calendar stays byte identical to pre-02 golden` 证明普通人工回复发送身份不变。

## Changed Files

- `src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql` — 新增一列 `calendar_attachment_json LONGTEXT NULL`（无回填/无约束/无 `${}`）
- `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt` — 末尾 `calendarAttachmentJson: String? = null`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt` — `ComposedMail` 末尾默认 `calendarAttachment: CalendarAttachmentSnapshot? = null`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt` — 无附件分支逐字保留；calendar 分支 multipart/mixed（javax.mail MIME API + ByteArrayDataSource，禁自拼）
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` — SendPayload 尾字段、指纹两段追加、四支快照 JSON 持久化/显式清空
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt` — MIME 回归 5 用例 + 01 真实 preview 快照 helper + .eml 保存
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt` — golden/语义指纹/四支/认领路径用例 + 01 真实生成器 helper
- `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` — 11 处最新断言 123 + V123 列/null 测试

## 交付物

- 实现提交：`6a1b54697778bdea64002516d5c63fbabedd0f13` `feat(fast-p): implement 02`（8 文件，+863/−30；docs/plans/fast/** 未纳入）
- `.eml` fixture：`target/meeting-confirmation.eml`（A-1；target 属构建产物不入库）
- 未 push / 未 merge / 未改历史；执行后工作树干净

## Freshness

- Plan identity rechecked: YES（SHA-256 前后一致 `6f87732c…`）
- Worktree identity rechecked: YES（branch/HEAD/根一致）
- Reported commit reachable from target branch: YES（HEAD = 6a1b546）
- Required commands run this invocation: YES（三条全部新鲜执行）
- Historical evidence used only as baseline: YES（seed/01 计数仅作对比）
