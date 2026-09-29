# Child 03 Brief — SMTP 即时失败：地址判据与状态写入口收口

- 子计划（唯一权威契约）：`docs/plans/2026-09-29/bounce-smtp-invalid-separation.md`
- 总计划：`docs/plans/2026-09-29/bounce-repair-master.md`
- 代码审计回执：`docs/plans/2026-09-29/bounce-repair-code-audit.md`
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master`
- Branch：`fast/2026-09-29-bounce-repair-master`
- child_base_sha：见 ledger（`Base` 列，= child 02 的 `Code head`）
- 依赖：严格依赖 02 已验证；与 02 共用 helper、helper 测试、对账、对账测试四文件（02 完成后才可编辑）。

实施者必须先完整阅读子计划全文，再按本 brief 执行。

## 目标（详见子计划「需求描述」）

- O-1：首封 SMTP 永久失败继续记录 FAILED，但只有明确收件地址错误才写 `EMAIL_INVALID`；实时与对账使用相同持久化摘要作判断。
- O-2：保留原本「永久失败不自动再发」的效果，不能因去掉 `EMAIL_INVALID` 而让历史未绑定 NEW 联系人反复发送。

## Authorized Files（恰好 8 个，不得增删）

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/RecipientAddressFailureClassifier.kt`（追加 SMTP 摘要判据） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt` |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/RecipientAddressFailureClassifierTest.kt` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailOpenTrackingPersistenceTest.kt` |

不得修改：`ManualOutreachTxHelper`、`MailRecordRepository`、错误枚举、ES mapping、`ExpertOperatorStatusService`、`OperatorStatusWriteSeamGuardTest`（只运行）。

## 关键不变量（子计划逐字要求）

- I-1：使用相同、可重放的证据。在线先取得 `buildSmtpErrorSummary` 已有结果；判定与 `recordFailure` 传入同一个 errorSummary。对账读取已存 `mail_record.error_summary`，必须同时满足 OUTBOUND+INTRODUCTION+FAILED，再用同一 helper。不得在线读 500 字符 errorDetail 而对账只读 200 字符摘要。
- I-2：SMTP 解析保守且有界。helper 新增 `isInvalidPermanentSummary(summary: String?): Boolean`；必须匹配 `PERMANENT:<500..599>:` 前缀；详情中只接受行首可带空白的完整增强码，或行首三位 SMTP 码+空白/连字符+完整增强码；若带 SMTP 码必须与摘要三位码一致；不得从任意正文/URL/异常说明搜索嵌入码。
  - 多行契约：提取可信协议行；不同增强码冲突、4xx 增强码、无可信码 → false；重复同一码允许；不含协议码的说明行不作证据。
  - 截断契约：详情达到现有 200 字符截断上限时保守返回 false（允许漏标，不允许凭截断推断）；不改变摘要存储格式/字段宽度。
  - 典型值：`PERMANENT:550:550 5.1.1 User unknown` → true；`PERMANENT:550:550 5.7.1 Blocked` → false；`PERMANENT:550:user unknown` → false。
  - 白名单复用 02：`{5.1.1, 5.1.2, 5.1.3, 5.1.10}`。
- I-3：公共状态入口与失败事实独立。PERMANENT 分支始终先 `recordFailure`，再按 I-1/I-2 决定是否调用 `ExpertOperatorStatusService.markEmailInvalid`；删除该分支直接 `save(operatorStatus=EMAIL_INVALID)` 与直接 ES sync。其余失败计数、验证记录 FAILED、`stat.failed`、`roundRejected` 不变；公共入口 REPLIED 及以上保护、无人工审计、ES 同步语义不变。
- I-4：永久首封不自动重发。对引擎当前处理的 contact，只要已有 OUTBOUND+INTRODUCTION+FAILED 且 errorSummary 以 `PERMANENT:` 开头，无论能否证明地址无效都不重新尝试首封；NEW 重试构造与最终 SMTP 前检查共用同一事实谓词；UNKNOWN/TRANSIENT 不冒充 PERMANENT；保留已绑定/已发首封门禁；仅约束本引擎首封，不替用户发起新的显式人工重发。
  - 存储规则：复用现有 `findAllByExpertContactIdOrderByCreatedAtAsc`，不新增查询/表/状态；尽量一次读取列表供 SENT 与 PERMANENT 检查。
  - 结果规则：retry 集合排除；最终门禁用既有 `SEND_EXCEPTION` 原因码 + 「历史首封永久失败，需人工处理」说明记录 skipped，不计本次 failed/sent；已 PASS 验证则记录 `NOT_SENT`；不更新发送尝试、不调用 SMTP、不增加额度；原有进度计数分支成对维护。
- I-5：只读对账与历史边界。只改 `permanentFailureContactIds` 集合条件，保留 02 的 DSN 白名单和里程碑优先级；人工覆盖与 COMPLETED 豁免不变；不写 DB/ES、不执行历史恢复。

## Required Commands（在 worktree 内逐条执行，JDK 11）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest=RecipientAddressFailureClassifierTest,ManualInitialOutreachServiceTest,OperatorStatusReconcileServiceTest,BatchSendTaskRuntimeIntegrationTest,MailOpenTrackingPersistenceTest,ExpertOperatorStatusServiceTest,OperatorStatusWriteSeamGuardTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

基线（seed commit）已记录：既有类通过（详见 `children/03/baseline.md`）。外部依赖测试只能使用隔离资源；缺环境须在报告中显式标注未验证，不得用生产补跑。

## 上游接口（child 02 产出，必须原样复用）

- `RecipientAddressFailureClassifier.isInvalidDsnStatus(value: String?): Boolean`：02 已实现，本阶段只追加 `isInvalidPermanentSummary`，不得改其行为。
- `OperatorStatusReconcileService` 里程碑优先结构：02 已实现，本阶段只收紧 `permanentFailureContactIds`。
- `ManualOutreachTxHelper.recordFailure`（`mail_record` OUTBOUND/INTRODUCTION/FAILED + attempt FAILED）：不改签名，只调用。
- `TrustReplyWorkbench`/其它路径无关，不改。

## 全局约束

- Kotlin + Spring Boot 2.7 / Java 11；不得编辑已应用 migration；零新表/字段/枚举/队列。
- 新增业务异常（如有）须继承 `IllegalArgumentException`/`IllegalStateException`。
- `OperatorStatusWriteSeamGuardTest` 白名单按文件计数：`ManualInitialOutreachService.kt` 因保留 `NOT_CONTACTED` 初始化仍是合法命中文件，不得改守卫白名单；不得新增文件级写入点。
- 生产由 Spring 注入的构造签名变更只影响显式构造测试点：`ManualInitialOutreachServiceTest:122`、`BatchSendTaskRuntimeIntegrationTest:701`、`MailOpenTrackingPersistenceTest:180`（执行时按符号重新定位）。
- 先补失败测试，再最小实现；双次 run 测试必须让 repo 第二次返回第一次实际捕获的失败行和 contact，不得手工伪造 `EMAIL_INVALID` 来「证明」不会重发。

## 交付与证据

1. 本地提交：`feat(fast-p): implement 03`（**不包含** `docs/plans/fast/**`）。
2. 报告写入：`docs/plans/fast/2026-09-29-bounce-repair-master/children/03/execution.md`（commit SHA、逐文件摘要、逐命令退出码/计数、逐条不变量证据 file:line、偏差）。
3. 返回（仅这些）：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
4. 不得 push/merge/rebase/amend/reset；不得顺手修无关问题。
