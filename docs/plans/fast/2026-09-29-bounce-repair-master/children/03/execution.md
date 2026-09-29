# Child 03 Execution Report — SMTP 即时失败：地址判据与状态写入口收口

- Child: 03 / master plan `docs/plans/2026-09-29/bounce-repair-master.md`
- Authoritative child plan: `docs/plans/2026-09-29/bounce-smtp-invalid-separation.md`（plan identity `commit:4dccc7404dad92fc3a1dfe3224e2e6fe03feb331`）
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master`
- Branch: `fast/2026-09-29-bounce-repair-master`
- child_base_sha: `9ab0b519bd7d0991a02b92c04746b78d14ce7d3b`（= child 02 Code head；`git merge-base --is-ancestor` 复核通过）
- Pre-execution code SHA (HEAD at start): `ec5c60d7d3102541c3462fa6029771c7274c4f22`
- **Implementation commit: `46d7e17ddb4b71093a1faca156300335a442ddc4` — `feat(fast-p): implement 03`**（8 个授权文件，551 insertions / 42 deletions，无 `docs/plans/**`）
- Executor: `ImplBounce03`
- Status: READY_FOR_VERIFICATION

## 逐文件摘要（授权清单 8/8，无清单外文件）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `mail/service/RecipientAddressFailureClassifier.kt` | 追加 `isInvalidPermanentSummary(summary: String?)` 及三个正则 + 截断常量；`isInvalidDsnStatus` 逐字未动 |
| 2 | `campaign/service/ManualInitialOutreachService.kt` | 新增构造依赖 `expertOperatorStatusService`；永久失败分支改为「先 recordFailure → 有地址证据才走公共入口」并删除直接 save/ES sync；新增 I-4 永久首封事实谓词 + 重试筛选 + 发送前门禁 |
| 3 | `campaign/service/OperatorStatusReconcileService.kt` | `permanentFailureContactIds` 条件由「`PERMANENT:` 前缀」收紧为 `isInvalidPermanentSummary(error_summary)`；里程碑优先与 02 的 DSN 白名单未动 |
| 4 | `test/mail/service/RecipientAddressFailureClassifierTest.kt` | +31 用例：白名单/前缀/SMTP 码一致性/冲突/4xx/无码/嵌入码/199-200 截断边界 |
| 5 | `test/campaign/service/ManualInitialOutreachServiceTest.kt` | 构造适配（真实 `ExpertOperatorStatusService` + mock repo/audit/ES）；重写单个旧用例为 4 个 + 新增 4 状态不降级参数化用例（165 total，+7） |
| 6 | `test/campaign/service/OperatorStatusReconcileServiceTest.kt` | 正例改为真实摘要格式；+8 参数化反例 + 截断/里程碑/回复行三例（38 total） |
| 7 | `test/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | 仅构造适配（+1 实参） |
| 8 | `test/mail/service/MailOpenTrackingPersistenceTest.kt` | 仅构造适配（+1 实参） |

未触碰：`ManualOutreachTxHelper`、`MailRecordRepository`、`ExpertOperatorStatusService`、`OperatorStatusWriteSeamGuardTest`、错误枚举、migration、ES mapping、`docs/plans/2026-09-29/**`、`docs/plans/fast/**`（仅本报告文件为新建，不提交）。

## 命令证据（均在最终实现状态下重跑）

### 命令 1（目标类）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true \
 -Dtest=RecipientAddressFailureClassifierTest,ManualInitialOutreachServiceTest,OperatorStatusReconcileServiceTest,BatchSendTaskRuntimeIntegrationTest,MailOpenTrackingPersistenceTest,ExpertOperatorStatusServiceTest,OperatorStatusWriteSeamGuardTest test
```

- 最终运行 exit code **0**，`BUILD SUCCESS`，`Tests run: 308, Failures: 0, Errors: 0, Skipped: 0`（日志 `/tmp/bg03-accept-final.log`）
- 逐类：RecipientAddressFailureClassifierTest 61；ManualInitialOutreachServiceTest 165；OperatorStatusReconcileServiceTest 38；BatchSendTaskRuntimeIntegrationTest 22；MailOpenTrackingPersistenceTest 6；ExpertOperatorStatusServiceTest 15；OperatorStatusWriteSeamGuardTest 1
- 基线（`children/03/baseline.md`）：同类既有 214 通过（158/12/22/6/15/1）；本 child 净增 94 用例（+31 classifier、+26 reconcile、+7 engine）

### 命令 2（全量）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

- exit code **0**，`BUILD SUCCESS`（275.97 s，日志 `/tmp/bg03-fulltest-final.log`）
- surefire：`Tests run: 4444, Failures: 0, Errors: 0, Skipped: 13`（基线 4320/0/0/13；+124 = child 01/02/03 合计，其中 13 skipped 为既有 opt-in/Docker 门控，无新增 skip）
- exec 绑定 Node 套件：`pass 1233 / fail 0`（与 child 01 后基线 1233 一致）；`node-check-app` 与 `node-check-task-modal-runtime` 均执行且未报错

### 过程运行（如实记录，均为本人新增测试的夹具缺陷，非产品缺陷）

1. 首次 7 类运行（21:07）：`Tests run: 304, Failures: 2` — 两处均在 `OperatorStatusReconcileServiceTest` 新增用例：截断夹具长度写成 199（应为 200）；「回复行不构成首封证据」用例的 INBOUND 行合法推出 REPLIED 而现在只有 NOT_CONTACTED。已修正夹具。
2. 单类复跑：`Tests run: 38, Failures: 1` — 上述用例未 stub ES 状态导致 esVsDb=1。已补 `stubEs`。
3. 命令 1 中间绿跑（304 全绿）后，为补齐 I-3「REPLIED 及以上不降级」的调用点证据新增 1 个参数化用例（4 状态），随后命令 1 / 命令 2 均在最终状态下重跑（308 / 4444 全绿）。

## 逐条不变量证据（file:line 均为提交 `46d7e17` 内容）

### I-1 使用相同、可重放的证据

- 在线：`ManualInitialOutreachService.kt:1013` 取得 `buildSmtpErrorSummary(delivered)`（200 字符上限仍在 `:1305-1310`），`:1016-1021` 同一变量传入 `txHelper.recordFailure`，`:1044` 用同一变量判定；未读取 500 字符 `errorDetail`。
- 在线与对账同源：`OperatorStatusReconcileService.kt:85-93` 只读持久化 `mail_record.error_summary`，且严格限定 `OUTBOUND + INTRODUCTION + FAILED`。
- 证据：`ManualInitialOutreachServiceTest.kt:1407`（`errorSummary = eqValue("PERMANENT:550:550 5.1.1 User unknown")` 断言传给 recordFailure 的正是判定所用摘要）、`OperatorStatusReconcileServiceTest.kt:235`（同一字符串作为 fixture 输入，期望一致）、`:311`（同文本落在 INBOUND/OUTBOUND+REPLY 时不成立）。

### I-2 SMTP 解析保守且有界

- 实现：`RecipientAddressFailureClassifier.kt:28`（`PERMANENT:(5\d\d):` 前缀）、`:34` / `:39`（仅行首、可带空白、完整增强码；SMTP 码 + 空白/连字符）、`:45`（200 截断常量）、`:75-93`（算法：前缀匹配 → 截断保守 false → 逐行协议行 → 三位码一致性 `:85` → 去重集合唯一 `:91` → 白名单 `:92`）。
- 典型值：`PERMANENT:550:550 5.1.1 …` true；`PERMANENT:550:550 5.7.1 …` false；`PERMANENT:550:user unknown` false。
- 白名单复用 02，未扩：`RecipientAddressFailureClassifier.kt:21`。
- `isInvalidDsnStatus` 行为不变：`git diff` 中该方法无任何 hunk（`:52-61` 与基线一致）。
- 证据：`RecipientAddressFailureClassifierTest.kt:84`（true 矩阵 8：含重复同码多行、缩进行、554-5.1.2、5.1.10）、`:121`（false 矩阵 21：5.7.1/5.1.7/5.1.6/5.2.2、4xx、裸 550、无码、`reason:`/URL 嵌入、摘要码与协议码冲突、`5.1.1`↔`5.1.2` 冲突、`5.1.100`/`5.1.10.1`、非 PERMANENT 前缀）、`:126`（null/空/空白）、`:133`（199 字符完整协议行 true；200 字符 false；尾部恰好截断在 `5.1.1` 处 false）。

### I-3 公共状态入口与失败事实独立

- `ManualInitialOutreachService.kt:1014-1049`：`recordFailure` 恒先执行（`:1016`），仅当地址证据成立才调 `expertOperatorStatusService.markEmailInvalid(contact, …)`（`:1044-1048`）；原 `expertContactRepository.save(operatorStatus="EMAIL_INVALID")` 与 `expertIndexWriterService.syncOperatorStatus(…, "EMAIL_INVALID")` 已删除（diff 中为 `-` 行）。
- 失败计数分支逐字保留：`stat.failed++` / `roundRejected++` / `accumulator.recordFailure(SEND_EXCEPTION, "永久发送失败 …")` / `recordVerificationSend(FAILED)`（`:1050-1055`）；TRANSIENT/INFRASTRUCTURE/else 分支未改。
- 公共入口语义（未改该文件）：`ExpertOperatorStatusService.kt:88` 写库 + ES 同步；`:80-83` REPLIED 及以上保护；`:90` 不写审计。构造依赖新增于 `ManualInitialOutreachService.kt:116`。
- 证据：`ManualInitialOutreachServiceTest.kt:1407`（5.1.1：failed=1/sent=0，一次 `recordFailure`、公共入口写 EMAIL_INVALID + ES 同步一次、`verifyNoInteractions(operatorActionLogService)`）、`:1465`（5.7.1：failed=1/sent=0、`never()` 写 EMAIL_INVALID、`verifyNoInteractions(expertIndexWriterService, operatorActionLogService)`）、`:1526`（REPLIED/MATERIALS_RECEIVED/INVITED/COMPLETED 四状态 live 调用点均 failed=1 且零 EMAIL_INVALID / 零 ES / 零审计——公共保护在真实 `ExpertOperatorStatusService` 上生效）、`ExpertOperatorStatusServiceTest`（15 例：`markEmailInvalid does not downgrade REPLIED/MATERIALS_RECEIVED/INVITED`、`never writes CHANGE_OPERATOR_STATUS audit`）。

### I-4 永久首封不自动重发

- 判据：`ManualInitialOutreachService.kt:1317-1319`（`OUTBOUND + INTRODUCTION + FAILED` 且摘要以 `PERMANENT:` 开头；`PERMANENT_SUMMARY_PREFIX` 定义于 `:78`）——与地址证据**解耦**，UNKNOWN/TRANSIENT 不命中（`:1443-1454` 仅用该谓词）。
- 存储规则：`:1326-1330` 复用既有 `expertContactRepository.findByOrcidIdIn`（ES 页同一 ORCID 的既有行），`:1333-1337` 复用既有 `findAllByExpertContactIdOrderByCreatedAtAsc`，每个 contact 行一次读取；未新增查询方法/表/状态。
- 重试构造：`:1443-1454` 一次列表读取同时服务 SENT 与 PERMANENT（原 `hasSentIntroduction(it.id)` 读取被合并），并保留 `operatorStatus != "EMAIL_INVALID"`。
- 最终门禁：`:873-888`，位置在写 PREPARED（`:960+`）与 SMTP（`:1000+`）之前，且在 contact 建行/绑定之前；`SEND_EXCEPTION` + 「历史首封永久失败，需人工处理：…」记录 skipped，`processedTotal++/roundProcessed++`（与同段 DEDUP 分支成对一致），`recordVerificationSend(verified, NOT_SENT, SEND_EXCEPTION)`；不调 SMTP、不写发送尝试、不占额度。
- 证据：`ManualInitialOutreachServiceTest.kt:1465`（两次 run：第二次仓储返回第一次由 `recordFailure` 捕获的失败行与 contact——`captureIntroductionFailures():1372`——断言累计 `send` 1 次、`recordFailure` 1 次、`mailSendAttemptRepository.save` 1 次、`incrementTodaySentCount` never、`total=0`；未手工伪造 EMAIL_INVALID）、`:1559`（NEW 重试为空但 ES 页重新返回该 ORCID：skipped=1、`SEND_EXCEPTION` 计数 1、`send` never、`expertContactRepository.save` never、`mailSendAttemptRepository.save` never、ES sync never、`recordSend(555, NOT_SENT, SEND_EXCEPTION)`）、`:1596`（只含 `TRANSIENT:421` 失败行的同一 contact 仍被重试并发出 1 封，证明瞬态不冒充永久；421/452 限流与 INFRASTRUCTURE 既有用例在 165 例中全绿）、`:1407` 第二段（已 PASS 无效化的联系人下一次快照 `total=0`）。

### I-5 只读对账与历史边界

- 唯一改动：`OperatorStatusReconcileService.kt:85-93` 集合条件收紧（`:90`）；`deriveExpectedStatus` 的里程碑优先结构（`:218-225`）与 02 的 DSN 白名单（`:77-84`）未动；本服务仍不注入任何 writer（零写入用例仍绿）。
- 证据：`OperatorStatusReconcileServiceTest.kt:235`（5.1.1 → EMAIL_INVALID）、`:264`（8 个无证据摘要含 5.7.1/裸 550/码冲突/`reason:` → NOT_CONTACTED，非 EMAIL_INVALID）、`:275`（200 字符截断 → NOT_CONTACTED）、`:295`（有 INBOUND 里程碑时仍为 REPLIED）、`:311`（回复/INBOUND 失败行不构成首封证据）、既有 `qualifying address evidence does not override the replied/materials/invited milestone`、`human override…`、`COMPLETED…`、`reconcile performs zero writes` 全部保持绿。

## 偏差与观察

1. **门禁位置（实现细节，语义等价且更严）**：I-4 的最终门禁放在「验证 + 选号之后、建行/绑定与写 PREPARED 之前」，而计划文字写的是「contact 确定后的原 SENT 去重检查旁」。这样 ES 页重新出现的未绑定 ORCID 不会先被插入重复 contact 行/绑定，I-4 的全部结果规则（skipped/不计 failed+sent/不写尝试/不呼叫 SMTP/不占额度/已 PASS 记 NOT_SENT）仍逐条满足；`SEND_EXCEPTION` 原因码与「历史首封永久失败，需人工处理」说明逐字使用。
2. **`expertIndexWriterService` 变为未使用的构造依赖（与本 child 计划的一处陈述冲突）**：计划称「不能删 expertIndexWriterService 整个依赖：服务还有其它既有调用」，但实测 `grep -n expertIndexWriterService src/main/kotlin/.../ManualInitialOutreachService.kt` 只有「声明 + 原永久失败分支 `:1011` 同步调用」两处；删除该调用后该属性在本文件再无读取方。按计划明确要求**保留**该依赖（不做清单外扩展改动），仅记录观察：Kotlin 只会给出未使用告警。若人工判定应删除，需要同时改 3 个显式构造测试点，属后续授权范围。
3. 计划中两处行号引用已随本轮改动漂移（`ManualInitialOutreachService:697,706`、`hasSentIntroduction():895`），代码内注释已同步为方法名而非行号。
4. 未运行/未覆盖：需要 SMTP 服务器、真实 MySQL/ES、Docker 的 opt-in 用例（与既有 13 skipped 同集合）不在本 child 验证范围，未用生产环境补跑；人工验收清单 A-1..A-4 需在隔离环境由人执行。

## 人工验收提示（对应清单 A-1..A-4）

- A-1（5.7.1）：failed=1/sent=0、邮件 FAILED + `PERMANENT:550:` 摘要 + attempt FAILED、专家仍 NOT_CONTACTED、对账 expected=NOT_CONTACTED、无人工状态审计。
- A-2（5.1.1 vs 裸 550）：前者 EMAIL_INVALID、后者 NOT_CONTACTED，两者都保留永久失败记录；对账同值。
- A-3（未绑定 NEW + 已绑定新建 contact，两轮）：第二轮新增 SMTP=0、FAILED 行=0、PREPARED=0、额度不变；ES 页再次出现也不能绕过（`:1559` 用例已用 mock 覆盖同构场景）。
- A-4（421/认证失败、已 REPLIED、旧 EMAIL_INVALID、人工覆盖）：421 仍限流、基础设施失败仍走原处理、REPLIED 不降级、历史 EMAIL_INVALID 实际值不变、人工对象归 HUMAN_OVERRIDE、对账零写入。
