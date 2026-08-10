# Fast-P Child Brief — p1 (sender-binding-01-schema-and-establish)

> 唯一权威契约 = `docs/plans/2026-08-10/sender-binding-01-schema-and-establish.md`（本批次主计划
> `docs/plans/2026-08-10/00-main-plan-sender-binding.md` 的跨计划约束见下）。
> 本 brief 只承载主计划级约束与跨子计划接口，不重述子计划正文；以子计划为准。

## 全局约束（主计划，违反即 LIGHT_FAIL）

- **M-1 串行**：本批次五份计划严格串行 P1→P2→P3→P4→P5，禁止并行；你是唯一 writer，只提交本子计划的实现 commit。
- **M-2 方法级所有权（排他）**：只改下列文件中属于 P1 的方法/字段，禁止"顺手改"任何其他方法/字段，发现需要改动本计划名下之外的内容必须停止并上报：
  - `db/migration/V85__add_expert_contact_sender_binding.sql` — P1 新建（唯一）
  - `campaign/domain/ExpertContact.kt` — P1 只加 `boundSenderAccountCode` / `senderAccountBoundAt`
  - `campaign/repository/ExpertContactRepository.kt` — P1 只加 `updateBindingById`
  - `mail/service/SenderAccountBindingService.kt` — P1 新建：`bindingFieldsFor` / `resolveForSend` / `bindIfAbsent` + 2 异常类
  - `campaign/service/InitialOutreachService.kt` — P1 只改 `:49-62` contact 构造
  - `campaign/service/ManualInitialOutreachService.kt` — P1 只改 `:573-582` 新建分支的构造
  - 测试：`SenderAccountBindingServiceTest.kt` 新建 10 例；`InitialOutreachServiceTest.kt` +1；`ManualInitialOutreachServiceTest.kt` +2
- **M-3 签名演进（关键）**：P1 实现的 `resolveForSend(contact, manual: Boolean)` 是阶段 1 签名。**P1 不得**添加 `ignoreWarmup` 形参（那是 P2 的 M-3 阶段 2 授权）；P3/P4/P5 不得再改。P1 期写的测试必须保证 P2 加 `ignoreWarmup: Boolean = false` 默认形参后零改动通过（用默认值语义编写）。
- **M-4 锁定测试**：`MailSenderAccountServiceTest.kt`、`ManualExpertMailServiceTest.kt`、`MeetingScheduleServiceTest.kt` 本计划**零改动零失败**（含 `MailSenderAccountServiceTest.kt:35-46`/`:48-57`/`:62-74` 三条锁定决策的测试）。
- **M-5 门禁矩阵**：`manual=false` 判 enabled && !autoSendPaused && 额度 && 非模拟器；`manual=true` 只判 enabled && 非模拟器。矩阵唯一权威实现是 P1 的 I-7。
- **M-6 迁移**：本计划只有 V85。执行前先复核 `src/main/resources/db/migration/` 最大版本号（规划为 V84），被占用则顺延并上报（不要自作主张改版本号，先检查再写）。
- **M-7 知识写回（P1 Phase 6）**：向 `docs/knowledge/campaign/K-expert-contact-two-write-sites.md` **追加**（先读当前内容）"新增第 3 个写路径：`updateBindingById` 列级补写"。禁止覆盖式写回。
- **M-8 不做五件事**：存量再平衡、人工超限、ES 索引字段、已变更筛选、DB 路径 tags 缺陷 —— 任何验收标准不得以这些为通过条件。
- **G-1**：绑定只决定新发起主题邮件；回复路径（`PendingMailOperationService` / `AutoMailReplyService`）一律不改。
- **G-2**：绑定 NULL = 未绑定，禁止哨兵值。
- **G-3**：`SIMULATOR_NOOP` 永不入绑定（建立、回填、任何路径）。

## 变更文件清单（P1 的 10 个授权文件；A2 修订新增第 10 项）

1. `src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql`（新增）
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/ExpertContact.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt`（新增）
5. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingServiceTest.kt`（新增 10 例）
8. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt`（**A2 修订授权，仅编译修复**：`:655` 构造实参尾部追加 `Mockito.mock(SenderAccountBindingService::class.java)`，不改任何断言）

另有唯一的知识写回：`docs/knowledge/campaign/K-expert-contact-two-write-sites.md`（追加，Phase 6）。

## 必须验证的命令（JDK 11 zulu；裸 mvn 会失败）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=SenderAccountBindingServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=InitialOutreachServiceTest+ManualInitialOutreachServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0、`Tests run: N, Failures: 0, Errors: 0`、`BUILD SUCCESS`。

## 下流接口（后续子计划依赖，P1 交付物）

- `SenderAccountBindingService.bindingFieldsFor(accountCode: String, now: LocalDateTime): Pair<String, LocalDateTime>`
- `SenderAccountBindingService.resolveForSend(contact: ExpertContact, manual: Boolean): MailSenderAccount`（P2 会加 `ignoreWarmup: Boolean = false` 默认形参）
- `SenderAccountBindingService.bindIfAbsent(contactId: Long, accountCode: String, now: LocalDateTime)`
- 异常：`SenderAccountNotBoundException(contactId)` / `BoundSenderAccountUnavailableException(contactId, accountCode, reason)`，二者继承 `IllegalStateException`（400 映射）。
- `ExpertContactRepository.updateBindingById(id, accountCode: String?, boundAt: LocalDateTime?): Int`（列级 UPDATE，`@Modifying @Query`）
- `expert_contact.bound_sender_account_code` / `sender_account_bound_at`（V85，可空，索引 `idx_expert_contact_bound_sender`）
- 门禁矩阵（I-7）：manual=false → enabled && !autoSendPaused && todaySentCount < effectiveDailyLimit(account) && 非模拟器；manual=true → enabled && 非模拟器；不满足抛 `BoundSenderAccountUnavailableException`，不降级重选。

## 产物与提交

- 实现 commit 消息：`feat(fast-p): implement p1`
- 把完整执行结果写入 `docs/plans/fast/sender-binding/children/p1/execution.md`（追加，不删除既有内容）
- 只提交实现文件 + execution.md；**不提交** ledger/verify-log/fix-log（控制器另行提交证据）。
- 返回格式：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + report 路径。
