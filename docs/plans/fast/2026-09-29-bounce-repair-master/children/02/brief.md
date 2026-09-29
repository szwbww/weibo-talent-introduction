# Child 02 Brief — DSN 永久退信与收件地址无效分离

- 子计划（唯一权威契约）：`docs/plans/2026-09-29/bounce-address-invalid-separation.md`
- 总计划：`docs/plans/2026-09-29/bounce-repair-master.md`
- 代码审计回执：`docs/plans/2026-09-29/bounce-repair-code-audit.md`
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master`
- Branch：`fast/2026-09-29-bounce-repair-master`
- child_base_sha：见 ledger（`Base` 列，= child 01 的 `Code head`）
- 依赖（执行顺序）：master I-2 有界顺序；与 01 无共享文件。02 与 03 共用 helper、helper 测试、对账、对账测试四文件，必须串行。

实施者必须先完整阅读子计划全文，再按本 brief 执行。

## 目标（详见子计划「需求描述」）

- O-1：5.7.1/5.4.1/5.2.2/5.0.0 等永久退信保留 HARD，但不再仅据 HARD 标专家为 `EMAIL_INVALID`。
- O-2：完整识别 5.1.10 不截断；明确收件地址错误才进入现有状态写入口；只读对账采用同一 DSN 判据，已有回复/材料/邀请事实不被退信遮盖。

## Authorized Files（恰好 8 个，不得增删）

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/RecipientAddressFailureClassifier.kt`（新增） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceDetector.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/RecipientAddressFailureClassifierTest.kt`（新增） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceDetectorTest.kt` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt` |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt` |

`OperatorStatusWriteSeamGuardTest`、`ExpertOperatorStatusServiceTest`、`ManualExpertMaterialUploadFlowTest`、`BounceBackfillServiceTest` 只在命令中运行，**不得编辑**（若非编辑无法通过，返回 `PLAN_CONFLICT`）。

## 关键不变量（子计划逐字要求）

- I-1：永久失败不是地址判据。仍按现有协议分类保留 HARD/SOFT；不得为减少告警把政策/路由/容量的 5xx 改成 SOFT。保存 BounceRecord 之后，仅 HARD 且归因取得 contact 且 I-2 成立时才调用 `markEmailInvalid`；未知码只记录退信。
- I-2：精确、保守的地址证据。唯一允许码集合 `{5.1.1, 5.1.2, 5.1.3, 5.1.10}`（IANA 目的邮箱/目的系统/语法/Null MX）；`5.1.7/5.1.8` 必须拒绝；`5.1.6` 本轮不扩；未知/无增强码/冲突码不自动封禁。
  - 输入契约：`isInvalidDsnStatus(value: String?): Boolean` 只消费 `dsn_status` 字段；允许 trim 后的完整增强码，或既有格式「5xx + 空白/连字符 + 增强码」（如 `5.1.1`、`550-5.1.1`、`550` 后接空白再 `5.1.1`）；必须整字段匹配，不得扫描任意退信正文/reason/URL/多状态/尾随解释/残缺字段 → false。
  - 新 helper 为无依赖 Kotlin object；不新增 Spring 构造参数、不引入供应商规则。
- I-3：完整增强码与解析次序。class 一位，subject/detail 各 1～3 位；禁止把 `5.1.100`、`5.1.1000`、`5.1.10.1`、`15.1.1` 截为 `5.1.1`/`5.1.10`；`STATUS_PATTERN`、`DSN_STATUS_PATTERN`、`HARD_SMTP_CODE_PATTERN` 一并修边界；MIME Status 优先于启发式；inputStream fallback 保持；原 5.1.1/4.2.2 行为保持。历史已截断数据不臆造。
- I-4：状态保护与只读推导。在线继续调用现有 `markEmailInvalid`；REPLIED/MATERIALS_RECEIVED/INVITED/COMPLETED 不回退；已有 `EMAIL_INVALID` 不重复写；不产生 `CHANGE_OPERATOR_STATUS` 审计。对账先计算现有最高通信里程碑；达 REPLIED 及以上优先该里程碑；否则才用合格地址退信（或暂存旧 SMTP 条件）推导 `EMAIL_INVALID`，再回退 CONTACTED/NOT_CONTACTED；不得从 DB 既有 EMAIL_INVALID 反推证据；对账不写 DB/ES。
- I-5：原有数据路径不扩张。保存字段、dedupeKey、账号归因、`originalContact` 选择算法不变；无 contact 仍保存 HARD 并计告警；重复 bounce 不触发第二次状态写入；零历史更新/重新摄取/批量 ES 同步。

## Required Commands（在 worktree 内逐条执行，JDK 11）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest=RecipientAddressFailureClassifierTest,BounceDetectorTest,BounceCollectionServiceTest,BounceBackfillServiceTest,OperatorStatusReconcileServiceTest,ExpertOperatorStatusServiceTest,OperatorStatusWriteSeamGuardTest,ManualExpertMaterialUploadFlowTest test
```

基线（seed commit）已记录：上述既有类均通过（详见 `children/02/baseline.md`）。命令结果不得退化。

## 下游接口（child 03 依赖，必须精确）

- 新 helper 的**路径/类名/方法名/签名**冻结：`src/main/kotlin/com/weibo/talentintroduction/mail/service/RecipientAddressFailureClassifier.kt`，无依赖 Kotlin `object`，方法 `isInvalidDsnStatus(value: String?): Boolean`。03 将在同文件追加 `isInvalidPermanentSummary`，不得改签名或搬移。
- `OperatorStatusReconcileService.deriveExpectedStatus` 的里程碑优先结构（先算里程碑再回退）冻结，供 03 在 SMTP 集合条件上追加 helper 判断。
- `BounceCollectionService.ingest` 的 DSN 判据调用点保留在同一位置（保存退信之后、调用 `markEmailInvalid` 之前的 HARD 分支）。

## 全局约束

- Kotlin + Spring Boot 2.7 / Java 11；不得编辑已应用 Flyway migration；本阶段零 DDL、零新持久化字段、零 ES mapping 变更。
- `BounceDetector` 只改三个 regex 的长度/边界，不重写 MIME 或归因；`parseBounceDetails` MIME 优先次序不动。
- 不得修改 `MailRecordRepository.kt`、`ExpertContactRepository.kt`、`ExpertSearchService.kt`（守卫行号钉，K-line-number-guard-breaks-on-any-insertion）。
- 测试不得把 fixture 全部预写成新正确值来绕过被测路径；须覆盖正反矩阵与既有回归。
- 先补失败测试，再最小实现。

## 交付与证据

1. 本地提交：`feat(fast-p): implement 02`（**不包含** `docs/plans/fast/**`）。
2. 报告写入：`docs/plans/fast/2026-09-29-bounce-repair-master/children/02/execution.md`（commit SHA、逐文件摘要、逐命令退出码/计数、逐条不变量证据 file:line、偏差）。
3. 返回（仅这些）：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
4. 不得 push/merge/rebase/amend/reset；不得顺手修无关问题。
