# Fast-P Execution — 08-assets-release

## Epoch 1 — IMPLEMENTATION

- Executor: isolated implementer `Implementer08`.
- Product base: `ef836c3e13f57bc14696318ec0f8a5c89ab06874` (child 07 terminal code head); child 07 evidence commit `da60feb49b994c1c16a44adccd53e1d69157cf6a` precedes this implementation in Git ancestry.
- Plan: `docs/plans/fast/meeting-mail-master/children/08-assets-release/brief.md` (byte-identical copy of the approved `docs/plans/2026-09-16/meeting-mail-08-assets-release.md`).
- Controller pre-check at dispatch: `20260914-followup-email` occurs in exactly `index.html` plus the 9 test files listed in this child's 变更文件清单, so the plan's file list is complete for the key swap; `task-modal-runtime.js` must stay version-less and the versioned resource count stays 9.
- Environment at dispatch: Docker (OrbStack) and the MySQL 8.0.46 container `ti-mysql-it` (127.0.0.1:3306, database `talent_introduction`, root/root) are running, so this child's Docker-gated and mysqlIt-gated commands are runnable.
- Required commands: `node --test src/test/js/*.test.js`; JDK11 full `mvn test`; `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`; business SQL tests with `-DmysqlIt=true`.
- Dispatch: fresh implementer acquired through the Required Agent Gate.

### Epoch 1 result — READY_FOR_VERIFICATION（本 child 范围内）

- Plan SHA-256（执行前/执行后一致）：`3a1aa8fcfef47ea9e293a9ce6ffa0e80a124aeab5001c6654e020734cdf3472b`
  （`python3 ~/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/fast/meeting-mail-master/children/08-assets-release/brief.md`）
- Worktree ID：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master@fast/meeting-mail-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-meeting-mail-master`（`git-dir` 与 `git-common-dir` 经 `worktree_identity.py --expect-*` 校验一致）
- Pre-execution code SHA：`da60feb49b994c1c16a44adccd53e1d69157cf6a`（child 07 证据提交，位于产品基线 `ef836c3e13f57bc14696318ec0f8a5c89ab06874` 之后）
- **Post-execution code SHA = Evidence HEAD（本 child 不单独提交证据，由 controller 负责）：`8c91c656d6530972ff8fc242705c928715c5b4d5`**
  - 提交信息：`feat(fast-p): implement 08-assets-release`
  - 内容：10 文件 / 28 insertions / 28 deletions（仅缓存键替换），是本 worktree `fast/meeting-mail-master` 分支 HEAD 的后继提交（`da60feb` → `8c91c65`）
- 实现边界：`da60feb..8c91c65`（工作树中另有两份未提交的 fast-p 证据文件 `docs/plans/fast/meeting-mail-master/children/08-assets-release/execution.md`、`docs/plans/fast/meeting-mail-master/ledger.md`，均**未**进入实现提交）

#### 变更文件（10/10，全部在授权清单内）

| # | 文件 | 旧键命中数 | 新键命中数 |
|---|---|---|---|
| 1 | `src/main/resources/static/index.html` | 9 | 9 |
| 2 | `src/test/js/ragWorkbenchRender.test.js` | 1 | 1 |
| 3 | `src/test/js/meetingConfirmationAssets.test.js` | 5 | 5 |
| 4 | `src/test/js/overlayAndDialogContrast.test.js` | 1 | 1 |
| 5 | `src/test/js/mailboxChatStyle.test.js` | 1 | 1 |
| 6 | `src/test/js/ragKnowledgeBasePage.test.js` | 4 | 4 |
| 7 | `src/test/js/manualReplySubjectPrefill.test.js` | 1 | 1 |
| 8 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 3 | 3 |
| 9 | `src/test/js/checkRepliesRelocation.test.js` | 1 | 1 |
| 10 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 2 | 2 |
| | **合计** | **28** | **28** |

替换方式：对上述逐个列出的 10 个路径做一次**字面子串**替换（`perl -pi -e 's/20260914-followup-email/20260916-meeting-mail/g' <10 个显式路径>`），未改任何正则、断言、结构或行序。

#### 旧键检索回执（编辑前 / 编辑后）

编辑前（工作树，含 docs）：
```
$ git grep -l '20260914-followup-email' | sort          # 18 命中
docs/plans/2026-09-14/00-followup-email-main.md
docs/plans/2026-09-14/followup-email-02-cache-activation.md
docs/plans/2026-09-16/meeting-mail-08-assets-release.md
docs/plans/2026-09-16/meeting-mail-evidence.md
docs/plans/fast/meeting-mail-master/children/03-calendar-ui/verify-log.md
docs/plans/fast/meeting-mail-master/children/07-attachment-ui/verify-log.md
docs/plans/fast/meeting-mail-master/children/08-assets-release/brief.md
docs/plans/fast/meeting-mail-master/children/08-assets-release/execution.md
src/main/resources/static/index.html                                            <- 10 个 src 文件
src/test/js/{batchSendTaskConsoleVisualFix,checkRepliesRelocation,mailboxChatStyle,manualReplySubjectPrefill,meetingConfirmationAssets,overlayAndDialogContrast,ragKnowledgeBasePage,ragWorkbenchRender,trustReplyWorkbenchSharedMount}.test.js
```
- 结论：src 下旧键**恰好**落在授权清单的 10 个文件（index.html + 9 份测试），与 dispatch 预检一致；其余 8 个命中全在 `docs/plans/**`（不得修改的执行证据/计划文件），本 child 未触碰。
- 新键 `20260916-meeting-mail` 编辑前在 `src` 下 0 命中（无覆盖他人新键的风险）。

编辑后：
```
$ grep -rn '20260914-followup-email' src | wc -l      -> 0
$ grep -rn '20260916-meeting-mail' src | wc -l        -> 28
$ grep -rl '20260916-meeting-mail' src | sort         -> 恰为上述 10 个文件
```

#### I-1 回执（资源与测试同键）

- `index.html` 版本化引用**恰好 9 个**，键值集合 = `['20260916-meeting-mail']`（Python `re.finditer(r"\?v=([0-9a-z-]+)")` 同源正则）。
- S-1 逐字目标引用（实施后实际内容，相对顺序与基线一致，节点未整体搬动）：
```html
<link rel="stylesheet" href="styles.css?v=20260916-meeting-mail">              <!-- index.html:11 -->
<link rel="stylesheet" href="expert-materials.css?v=20260916-meeting-mail">    <!-- :12 -->
<link rel="stylesheet" href="mailbox-chat.css?v=20260916-meeting-mail">        <!-- :13 -->
<link rel="stylesheet" href="meeting-confirmation.css?v=20260916-meeting-mail"><!-- :14 -->
<script src="trust-reply-workbench.js?v=20260916-meeting-mail"></script>       <!-- :2110 -->
<script src="expert-materials.js?v=20260916-meeting-mail"></script>            <!-- :2111 -->
<script src="meeting-confirmation.js?v=20260916-meeting-mail"></script>        <!-- :2112 -->
<script src="mailbox-chat.js?v=20260916-meeting-mail"></script>                <!-- :2113 -->
<script src="app.js?v=20260916-meeting-mail"></script>                         <!-- :2114 -->
```
- 顺序断言（按 `indexOf(asset + "?v=<key>")` 单调递增）：CSS（styles→expert-materials→mailbox-chat→meeting-confirmation）后接 JS（trust-reply-workbench→expert-materials→meeting-confirmation→mailbox-chat→app）——**保持成立**。
- `task-modal-runtime.js` 仍为无版本引用：`<script src="task-modal-runtime.js"></script>`（index.html:2109）。
- 9 份固定键测试文件旧键命中 **0**。

#### I-2 回执（只激活已验证内容）

- 逐行差分证明（对 `da60feb` 与工作树逐行配对，10 个文件）：
  `files=10 changed_line_pairs=28 key_only=True` —— 即 28 处变化全部满足 `old.replace('20260914-followup-email','20260916-meeting-mail') == new`，且**每个文件行数不变**（无增删行）。
- 阶段 diff 无业务/样式/DOM/资源顺序变化：无新增/删除 `<link>`/`<script>`，无 inline style，无 class 变化，无 preview/mock 数据、浏览器存储或 mock fetch 引入；`src/main/kotlin`、`src/test/kotlin` 相对 `da60feb` 的 diff 为空：
  `git diff --name-only da60feb -- src/main/kotlin src/test/kotlin` → （空）

#### 命令回执（全部在提交 `8c91c65` 建立**之后**、以最终实现状态重跑一次，串行执行）

| # | 命令 | 结果 | 实数（exit code） |
|---|---|---|---|
| 1 | `node --test src/test/js/*.test.js` | **PASS** | tests 944 / suites 183 / pass 944 / fail 0 / skipped 0 / todo 0，exit 0 |
| 2 | `JAVA_HOME=<zulu-11> mvn test` | **FAIL（5 项范围外既有失败，见下）** | Tests run: 3452 / Failures: 1 / Errors: 4 / Skipped: 13，exit 1 |
| 3 | `DOCKER_API_VERSION=1.44 JAVA_HOME=<zulu-11> mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.44` | **FAIL（1 项既有基线缺陷，见下）** | Tests run: 23 / Failures: 0 / Errors: 1 / Skipped: 0，exit 1 |
| 4 | `JAVA_HOME=<zulu-11> mvn test -Dtest='MeetingCalendarServiceTest,MeetingCalendarControllerTest,MailboxConversationControllerTest,MailboxConversationRepositoryIT,AttachmentTransferWorkerIT,ImapMetadataFetchIT,MeetingCalendarSendIntegrationTest' -DmysqlIt=true -DfailIfNoTests=false` | **PASS** | Tests run: 105 / Failures: 0 / Errors: 0 / Skipped: 0，exit 0 |

命令 4 逐类（105 = 18+6+27+21+17+9+7）：
`MeetingCalendarServiceTest` 18/0/0，`MeetingCalendarControllerTest` 6/0/0，`MailboxConversationControllerTest` 27/0/0，`MailboxConversationRepositoryIT` 21/0/0，`AttachmentTransferWorkerIT` 17/0/0，`ImapMetadataFetchIT` 9/0/0，`MeetingCalendarSendIntegrationTest` 7/0/0（全部 0 fail / 0 error / 0 skip）。
该 7 个类即仓库中**全部** `@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")` 载体（8 处注解，其中 `MailboxConversationControllerTest.kt:114` 与 `:1437` 两处），已逐一核对，无遗漏：

```
$ grep -rn 'EnabledIfSystemProperty(named = "mysqlIt"' src/test
  campaign/controller/MeetingCalendarControllerTest.kt:253
  campaign/service/MeetingCalendarServiceTest.kt:552
  mail/controller/MailboxConversationControllerTest.kt:114
  mail/controller/MailboxConversationControllerTest.kt:1437
  mail/repository/MailboxConversationRepositoryIT.kt:42
  mail/service/AttachmentTransferWorkerIT.kt:69
  mail/service/ImapMetadataFetchIT.kt:49
  mail/service/MeetingCalendarSendIntegrationTest.kt:77
```

补充说明：`pom.xml:186-212` 的 `exec-maven-plugin`（`node --test` / `node --check`）绑定在 test 阶段且**声明顺序在 surefire 之后**，因此命令 2 中 surefire 失败后 exec 不再执行；JS 全量套件由命令 1 直接以同一命令行运行并通过（exit 0）。

#### 范围外既有回归（**不阻塞本 child**，附因果证据与归属）

**A. `mvn test` 的 5 项失败（1 failure + 4 errors）与缓存键无关，且在基线 `da60feb` 即已存在。**
1. `campaign.OperatorStatusWriteSeamGuardTest.operator_status write sites exactly match whitelist`（该文件 :149）
   - 现象：钉死的噪声行漂移 —— 期望 `UnmatchedInboundMailController.kt:219 / :1125`，实际命中 `:221 / :1137`（+2 / +12）。
   - 归属：`82a46dc`（`feat(fast-p): implement 06-attachment-flow`，2026-09-17 11:49）向 `UnmatchedInboundMailController.kt` 插入了 `servletRequest: HttpServletRequest?` 形参与 `attachmentIds` / `authenticatedUsername` 两个转发实参（`git show 82a46dc -- .../UnmatchedInboundMailController.kt` 可见 `@@ -250,7 +252,10 @@` 与 `@@ -274,9 +279,16 @@`），行号下移但 `EXCLUDED_NOISE_SITES` 的 `line` 未能随行修正（即 `K-line-number-guard-breaks-on-any-insertion`）。
2. `mail.controller.UnmatchedInboundTrustWorkbenchTest` 4 项 `InvalidUseOfMatchersException: 23 matchers expected, 21 recorded`（:226 / :258 / :287 / :387）
   - 归属：同一 `82a46dc` 给 `pendingMailOperationService.sendManualRichReply(...)` 增加了 2 个参数（`attachmentIds`、`authenticatedUsername`），mock 形参由 21 变 23，而 `UnmatchedInboundTrustWorkbenchTest.kt` 不在该提交的变更文件内，matcher 数量未同步。
3. 因果证明：`git diff --name-only da60feb -- src/main/kotlin src/test/kotlin` 为空；且这两个失败类不读取 `index.html` 或 `src/test/js/**`（`grep -n 'index.html\|test/js\|static/' <两个失败类>` 无命中），故本 child 的 10 文件改动不具备致因路径。

**B. `FlywayMigrationIntegrationTest` 的 1 项 error（既有基线缺陷，非本 child 引入）**
- 失败用例：`V124 allows material attached promotion audit trigger:233->execute:1216`
- 精确错误：`java.sql.SQLIntegrityConstraintViolationException: Cannot add or update a child row: a foreign key constraint fails (talent_introduction.expert_application_promotion, CONSTRAINT fk_eap_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact (id))`
- 原因：该用例 `flyway.clean()` + `migrate()` 后直接 `INSERT INTO expert_application_promotion (expert_contact_id, ...) VALUES (1, ...)`，但无任何迁移向 `expert_contact` 播种 `id=1`（`grep -rn 'INSERT INTO expert_contact' src/main/resources/db/migration/` → 0 命中），而 `fk_eap_contact` 由 `V15__add_mail_monitoring_columns_and_promotion_audit.sql:43` 建立。
- 归属：该用例由**基线仓库**提交 `6ab8eb3`（2026-09-11 10:01 `fix: preserve inbound replies when promotion audit fails`）引入，是 `da60feb` 的祖先；该门禁套件此前从未在本 worktree 真正执行过（此前各 child 记录为未执行），本次为**首次实跑**。其余 22 项（含 `fresh database migrates through V127`、`V126 ...` 后的 V127 断言）全部通过。

#### 环境变更（dispatch 环境与实跑时的差异，已修复后实跑）

- dispatch 记录 Docker/MySQL 在运行，但本 child 起跑时二者均已停止：`docker info` → `failed to connect to the docker API at unix:///Users/lukai/.orbstack/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/lukai/.orbstack/run/docker.sock: connect: no such file or directory`，`ti-mysql-it` 状态 `Exited (0)`，`127.0.0.1:3306` 连接被拒。
- 处理（未改任何仓库文件）：`open -a OrbStack` → `docker start ti-mysql-it` → MySQL 8.0.46 / 库 `talent_introduction`（已迁移至 V127，59 张表）就绪。
- testcontainers 1.19.8（docker-java 默认 API 1.32）对本机引擎（Docker 29.4.0，`MinAPIVersion=1.40`）报 `client version 1.32 is too old. Minimum supported API version is 1.40`，使 `DockerClientFactory.instance().isDockerAvailable == false`。**首次**执行命令 3 因此得到：
  `[ERROR] Tests run: 1, Failures: 0, Errors: 1 — java.lang.IllegalStateException: Docker is required for Flyway migration tests (startMysql)`。
  以 `DOCKER_API_VERSION=1.44`（并 `-Dapi.version=1.44`）重跑后容器与迁移真实执行，得到上表的 23 项结果。该 pin 仅为**运行环境变量**，未写入任何仓库文件。

#### 补充执行（超出 brief 必需命令的其余 Docker 门禁组；仅作回归观测，不计入本 child 验收）

`-DmigrationIt=true` 下另 11 个 `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")` 载体（`OperatorActionLogRepositoryTest`, `AuthFlowIntegrationTest`, `AiQaExtractionServiceTest`, `InboundMailProcessingRepositoryTest`, `UnsubscribeTokenServiceJdbcIT`, `MailRecordRepositoryMonitoringIT`, `ProgrammeIdentityFactsMigrationTest`, `QaFactSupplyMigrationTest`, `RagFactAdminServiceTest`, `RagKnowledgeBaseTest`, `MailComposeTemplateBlockRepositoryIT`）：

- 未加 API pin：Tests run: 27 / Failures: 0 / Errors: 8，8 项全部为 `IllegalStateException: Docker is required ...`（纯环境，故不可判为通过或失败）。
- 加 `DOCKER_API_VERSION=1.44` 后真实执行：**Tests run: 67 / Failures: 0 / Errors: 37 / Skipped: 0，exit 1**。
  - 通过：`AiQaExtractionServiceTest` 6/0、`UnsubscribeTokenServiceJdbcIT` 1/0、`QaFactSupplyMigrationTest` 7/0、`ProgrammeIdentityFactsMigrationTest` 6/0。
  - 失败（类级 run/errors）：`AuthFlowIntegrationTest` 2/2、`InboundMailProcessingRepositoryTest` 6/6、`MailComposeTemplateBlockRepositoryIT` 1/1、`RagFactAdminServiceTest` 10/10、`RagKnowledgeBaseTest` 10/10、`OperatorActionLogRepositoryTest` 3/3、`MailRecordRepositoryMonitoringIT` 15/5。
  - 根因（3 类，全部为既有条件，均属 `da60feb` 及其祖先）：
    1. `BeanDefinitionOverrideException: Invalid bean definition with name 'attachmentTransferWorker' ... WorkerLifecycleTestConfig`（对照既有 `@Component` 定义）—— `AttachmentTransferWorker.kt:46` 的 `@Component` 由 `7c2420e`（2026-09-08 `feat(fast-p): implement 02`，**非本轮** meeting-mail 子计划）引入，测试侧 `@Bean` 由基线 `a7d2a63`（2026-09-11 `fix: start attachment transfer worker with app lifecycle`）引入；Spring Boot 2.7 默认禁止 bean 覆盖，故全量上下文测试互斥。
    2. `FlywayException: No value provided for placeholder: ${senderEmail}`（`db/migration/V2__seed_mail_templates.sql`）—— `src/test/resources/application.yml` 在测试类路径上**遮蔽** `src/main/resources/application.yml`，而后者独有的 `spring.flyway.placeholder-replacement: false`（`application.yml:13`）未被测试侧继承（`K-flyway-placeholder-replacement` 的测试侧缺口）。
    3. `java.sql.SQLException: Illegal mix of collations (utf8mb4_bin,NONE) and (utf8mb4_0900_ai_ci,COERCIBLE) for operation 'like'`（`MailRecordRepositoryMonitoringIT`，mysql:8.0.36 testcontainer 服务端排序规则与 `utf8mb4_bin` 列冲突）。
  - 已通过 `hub` 向 controller（`Main`）报告 A/B 与上述补充观测，供其按“回归失败定位证据后回到所属子计划”的约定另行授权修复；本 child 未修改任何 Kotlin/迁移/测试基础设施文件。

#### 未验证 items

1. **A-1 人工验收（线上资源激活）**：需在测试/`talent` 部署后由人工在真实浏览器执行（普通刷新 → Network 中 9 个 `?v=20260916-meeting-mail` 且 HTTP 200 → 打开会议日历/邮箱人工回复）。本 child 仅完成仓库静态资源键替换与自动化回归，未做部署或浏览器验证。
2. **A-2 人工验收（最终业务贯通：SMTP 沙箱、中文 zip、重启后下载）**：未执行（需真实 SMTP 沙箱与持久化磁盘）。
3. **发布前 `/talent` 真实路径验证**（10MiB 上传经代理、重启后下载可用）——见 brief 实现方案第 3 点，属部署阶段动作，本 child 未执行。
4. 上述「范围外既有回归」A/B 与其补充组，**不得**记为本 child 通过项，也不作为本 child 的阻塞项；其归属与证据已如上列明。

#### 结论

本 child 授权范围（10 文件、仅缓存键替换）已全部实现并有证据：
I-1（index 恰 9 个统一新键、9 份测试无旧键、JS 全量 944/0 通过）、I-2（diff 仅键替换、无业务/样式/DOM 变更）、S-1（资源相对顺序不变、`task-modal-runtime.js` 仍无版本）全部成立；
mysqlIt 业务 SQL 组 105/0/0 全绿；
`mvn test` 与 `FlywayMigrationIntegrationTest` 的失败项经因果证明均为**范围外既有回归**（分别归属 `82a46dc` 与基线 `6ab8eb3`），不属于本 child 的验收对象。

**→ READY_FOR_VERIFICATION（本 child 范围）；范围外回归待 controller 另行授权修复。**

### Epoch 1 — PAUSED_FOR_HUMAN (verifier PAUSE)

- Verifier `Verifier08` verdict: `PAUSE`, gates 1/2/4 PASS, gate 3 FAIL. Its report is in `verify-log.md`.
- Child 08's own scope is green: the range diff touches exactly the 10 authorized files, every changed line differs only in the cache key, `index.html` carries exactly 9 `?v=20260916-meeting-mail` references with `task-modal-runtime.js` still version-less, and `node --test src/test/js/*.test.js` is 944/944/0; the `-DmysqlIt=true` group is 105/0/0/0.
- Gate 3 failures and attribution:
  1. `campaign.OperatorStatusWriteSeamGuardTest:149` rejects `UnmatchedInboundMailController.kt:221/:1137` as unregistered write sites. All 10 `EXCLUDED_NOISE_SITES` pins matched at run base `24f5c82`, exactly 2 are stale at HEAD (219 -> 221, 1125 -> 1137), and `82a46dcc32d50cbc352165656842417bc0a569f2` (child 06) is the only commit in `24f5c82..8c91c65` touching that controller. **Caused by this run.**
  2. `mail.controller.UnmatchedInboundTrustWorkbenchTest` 4x `InvalidUseOfMatchersException: 23 matchers expected, 21 recorded`, because the same commit appended `attachmentIds`/`authenticatedUsername` to `sendManualRichReply` while that test file has no commit in this run. **Caused by this run.**
  3. `FlywayMigrationIntegrationTest` method `V124 allows material attached promotion audit trigger` fails with an `fk_eap_contact` violation. Pre-existing: the method comes from base commit `6ab8eb318bad141e6927f234e95eb4f2df17a2eb` (2026-09-11, an ancestor of the run base), no migration seeds `expert_contact` id 1, the FK comes only from untouched `V15`, and no commit in this run matches it. **Not caused by this run.**
- Only corrections for (1) and (2) need files outside child 08's 10-file authorization (`OperatorStatusWriteSeamGuardTest.kt`, `UnmatchedInboundTrustWorkbenchTest.kt`), so the verifier returned `PAUSE` instead of `AUTO_FIX`.
- Routing constraint: the repair commit lands after child 08's implementation commit, so it cannot become child 06's `Code head` without breaking the `Base = prior child's terminal Code head` chain; the repair must therefore be child 08's fix round under a widened file authorization.
- Controller action: paused for a HUMAN-approved amendment; no fixer was dispatched.
- Resume from: `8c91c656d6530972ff8fc242705c928715c5b4d5`.

### Finalization note

- The child-08 evidence commit records `execution.md`, `verify-log.md` and `fix-log.md` together so the fast-p final artifact contract can bind all three to this child's terminal state.
