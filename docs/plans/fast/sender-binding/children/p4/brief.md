# Fast-P Child Brief — p4 (sender-binding-04-rebind-api-and-audit)

> 唯一权威契约 = `docs/plans/2026-08-10/sender-binding-04-rebind-api-and-audit.md`（已含 A6 修订：变更文件清单 8 行）。
> 本 brief 只承载主计划级约束与跨子计划接口，不重述子计划正文；以子计划为准。
> P1/P2/P3 已落地；上游接口见下。

## 上游交付（P1/P2/P3，已验）

- `expert_contact.bound_sender_account_code` / `sender_account_bound_at`（V85）+ `ExpertContactRepository.updateBindingById`（列级）
- `SenderAccountBindingService.resolveForSend(contact, manual, ignoreWarmup=false)` + `bindingFieldsFor` / `bindIfAbsent` + 2 异常类（`IllegalStateException` 子类 → 400）
- `ExpertContactRepository.countBindingsByAccount()`（P3，P5 消费）
- 发送路径已绑定优先；P4 的换绑**下一次**新发起主题即生效（IP-1，无缓存）

## 全局约束（主计划，违反即 LIGHT_FAIL）

- **M-1**：本批次严格串行；你是唯一 writer。
- **M-2 方法级所有权（P4 范围，排他）**：
  - `db/migration/V86__add_expert_contact_sender_change_mark.sql` — P4 **新建**（唯一）
  - `campaign/domain/ExpertContact.kt` — P4 只加 `senderAccountChanged` / `senderAccountChangedAt`
  - `campaign/repository/ExpertContactRepository.kt` — P4 只加 `rebindSenderAccountById` / `migrateBindingByAccount` / `clearSenderChangeMarkById` / `findAllByBoundSenderAccountCode`
  - `audit/domain/OperatorActionType.kt` — P4 尾部加 3 个枚举（不改既有顺序/语义）
  - `mail/service/SenderAccountBindingService.kt` — P4 只加 `rebind` / `migrateAccount` / `clearChangeMark` + 命令/结果类型
  - `campaign/controller/ExpertContactManagementController.kt` — P4 只加 3 个端点 + 3 个请求体（DTO 不加字段，P5 负责）
  - 测试：`SenderAccountBindingServiceTest`（+12 例）、`ExpertContactManagementControllerTest`（A6：仅构造实参 +1）
  - 发现需要改本计划名下之外的方法/字段 → 停止并上报
- **M-3**：`resolveForSend` 签名不得再改。
- **M-6**：本计划只有 V86。执行前复核 `src/main/resources/db/migration/` 最大版本号（P1 后为 V85），V86 若被占用则顺延并上报。
- **M-7**：本计划无知识写回（K-custom-exception-http-status-mapping 已含）。
- **M-8**：不做五件事（尤其：换绑不阻断活跃会话、不做自动迁移）。
- **G-1**：换绑不改任何已存在邮件线程归属；**I-7 diff 红线**：不得包含 `PendingMailOperationService.kt` / `AutoMailReplyService.kt` / `MailRecordRepository.kt` / `MailboxService.kt`。
- **G-2**：NULL = 未绑定。**G-3**：SIMULATOR_NOOP 永不入绑定（目标账号校验也要排除）。

## 变更文件清单（P4 的 8 个授权文件；A6 修订新增第 8 项）

1. `src/main/resources/db/migration/V86__add_expert_contact_sender_change_mark.sql`（新增，无回填）
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/ExpertContact.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/audit/domain/OperatorActionType.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingServiceTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt`（**A6 修订授权，仅编译修复**：`:17` 命名参数构造补 `senderAccountBindingService = Mockito.mock(SenderAccountBindingService::class.java)`，不改断言）

> 构造点预查（已确认无其他越界风险）：`SenderAccountBindingService` 只被 `SenderAccountBindingServiceTest`（已授权）构造；`ExpertContactManagementController` 只被 `ExpertContactManagementControllerTest`（A6 已授权）构造。

## 关键实现要点（详见计划正文）

- **I-1**：三个 action type 语义严格分离：`CHANGE_SENDER_ACCOUNT`（置标 + `sender_account_changed_at`）、`MIGRATE_SENDER_ACCOUNT`（**禁碰**标记列）、`CLEAR_SENDER_CHANGE_MARK`（只清标，禁碰绑定列）。
- **I-2**：三个写路径各自 `@Modifying @Query` 列级 UPDATE；`SenderAccountBindingService` 三个新方法体内**不得出现** `expertContactRepository.save(`。
- **I-3**：目标账号 `requireEnabledTarget`：`require(...)`（400 而非 500），enabled && 非模拟器；源账号无要求。
- **I-4**：迁移逐专家一条审计（`findAllByBoundSenderAccountCode` 取快照再写 N 条）；`before`/`after` JSON 只含 `boundSenderAccountCode`（+可选 `senderAccountChanged`）；note ≤ 500 截断 + `…(truncated)`。
- **I-5**：目标 == 当前绑定 → 直接返回，不写库不写审计；迁移源 == 目标 → `IllegalArgumentException`。
- **I-6**：`migrateBindingByAccount` WHERE 恰为 `bound_sender_account_code = :fromAccountCode`。
- **I-7**：活跃会话只提示（`activeThreadHint`：`currentStatus` ∉ {NEW, MANUAL_HANDOFF} 时 note 追加提示；以 `ConversationStatus` 枚举实际成员为准），不阻断不改数据。
- 异常类、请求体形状照抄既有范式（`ChangeOperatorStatusRequest` 等）；`toResponse` 复用既有映射，**不加 DTO 字段**。
- `rebind` / `clearChangeMark` 返回 `ExpertContact`（重新 `findById`）。

## 必须验证的命令（JDK 11 zulu；裸 mvn 会失败）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=SenderAccountBindingServiceTest,ExpertContactManagementControllerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='SenderAccountBindingServiceTest#migrate does not touch change mark'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

> 已知怪癖：`-Dtest=A+B` 的 `+` 分隔在 surefire 2.22.2 下报 "No tests were executed"（exit 1）；**用逗号分隔**。计划正文的 `+` 写法是文档瑕疵。
> 通过判据：退出码 0、`Tests run: N, Failures: 0, Errors: 0`、`BUILD SUCCESS`。基线：P3 后 2264 tests / 0 F / 0 E / 4 skipped、node 479/0。

## 下流接口（后续子计划依赖）

- `SenderAccountBindingService.rebind(contactId, command: RebindCommand): ExpertContact` / `migrateAccount(command: MigrateCommand): MigrateResult` / `clearChangeMark(contactId, operatorName, note): ExpertContact`
- 命令类型：`RebindCommand(senderAccountCode, operatorName?, note?)` / `MigrateCommand(fromAccountCode, toAccountCode, operatorName?, reason?)` / `MigrateResult(migrated, fromAccountCode, toAccountCode)`
- 端点：`POST /api/expert-contacts/{contactId}/sender-account`、`POST /api/expert-contacts/{contactId}/sender-account/clear-change-mark`、`POST /api/expert-contacts/sender-account/migrate`（**P5 的详情换绑 UI 与迁移后列表刷新直接消费**）
- `expert_contact.sender_account_changed` / `sender_account_changed_at`（V86；P5 列表徽标与详情徽标读取）
- 审计 action types：`CHANGE_SENDER_ACCOUNT` / `MIGRATE_SENDER_ACCOUNT` / `CLEAR_SENDER_CHANGE_MARK`

## 产物与提交

- 实现 commit 消息：`feat(fast-p): implement p4`
- 完整执行结果追加到 `docs/plans/fast/sender-binding/children/p4/execution.md`
- 只提交实现文件 + execution.md；**不提交** ledger/verify-log/fix-log。
- 返回格式：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + report 路径。
