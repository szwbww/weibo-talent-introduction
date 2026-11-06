# Aggregate repair execution — NEW epoch

## Execution Result: READY_FOR_VERIFICATION

本记录是批准后的 R-1–R-4 执行自检，不是独立 aggregate PASS。最终交接必须完成本记录的单独证据提交，并再次确认身份、提交可达性及干净状态；没有自动执行 review-fast-p / verify-p。

### Identity and authority

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md`
- Plan SHA-256: `2faeacade4c57d7a03c3cfc211edc85be0da0f3beae00ba904fe1d6d64145e83`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md@2faeacade4c57d7a03c3cfc211edc85be0da0f3beae00ba904fe1d6d64145e83`
- Execution epoch: **NEW**。开始时该执行报告不存在，没有覆盖旧 epoch。
- Approval basis: 当前用户原始调用 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md`。它精确匹配 repair 的 Execution Handoff 授权；不是从旧 fast-p 批准或代理消息推断授权。
- Executor: 当前 omp coding assistant；并行实现切片为 `RepairMixedCollation`（R-1）和 `RepairInboundRestore`（R-4及原生场景），父执行器负责 R-2/R-3、全部命令门禁、真实后端场景、集成及提交。执行自检使用 fix-v 的受约束 AUDIT_ONLY 纪律；行为修复授权来自当前 execute-p repair，而非验证器自行授权。
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`
- Target branch: `fast/manual-reply-delay-master`
- Git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master@fast/manual-reply-delay-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`
- Pre-execution code SHA: `83ec3e3bdf620f80198f73ae496f78aaf75625f4`
- Invocation starting HEAD: `5253550d21fc5c404ded279587a422dbc330bdcd`；其相对上述 code SHA 的 `src` 差异为空。
- Post-execution code SHA: `fc2750cd7ad4ae5200195036d874aeeb8b5ae400`
- Product subject: `fix(mail): restore delayed reply aggregate contracts`
- Implementation boundary: `5253550d21fc5c404ded279587a422dbc330bdcd..fc2750cd7ad4ae5200195036d874aeeb8b5ae400`，仅以下九个产品/测试文件。
- Evidence HEAD: **写入本文件时尚未生成**；必须是产品 SHA 之后、主题为 `docs(review-fast-p): record aggregate repair execution`、仅包含本文件的独立提交。其实际 SHA 由提交后的最终交接与 `commit-receipts.json` 记录，不能把产品 SHA 冒充 Evidence HEAD，也不能为自嵌提交哈希 amend 或增加第三个提交。

### Task status

| Requirement | Status | Implementation and evidence |
|---|---|---|
| R-1 / V-1 | IMPLEMENTED | `pageEvidence` 使用 `JSON_UNQUOTE(JSON_QUOTE(LOWER(TRIM(c.expert_email))))` 形成无损、可强制转换的比较值，让索引列自身排序规则生效；没有指定全局 COLLATION、包裹索引列、迁移或 N+1。两种相反列排序规则的完整消息路径回归均先以 1267 失败、后通过。真实 Spring/MySQL GET 200，抑制命中/不命中与 READ 能力仍成立。SQL probe 的 EXPLAIN 为 PRIMARY / eq_ref / rows=1。 |
| R-2 / V-2 | IMPLEMENTED | 在现有 `DelayedManualReplySnapshotException` 增加默认 false 的 `sizeLimitExceeded`，UTF-8 大小检查仅在超限时置 true；共用 `enqueuePrepared` 包围 factory 和 lifecycle/repository 编码，只将该错误映射为现有公共 413 / SNAPSHOT_INVALID。没有异常文本匹配、全局异常改写或提高 4194304 字节上限。真实控制器 INBOUND/OUTBOUND 超限回归与实际 HTTP 413/零新增均通过。 |
| R-3 / V-3 | IMPLEMENTED | 仅把既有 UnmatchedInboundMailController 只读 DTO NoiseSite 的 236→237、1163→1169；path/context、ALLOWED_WRITE_SITES、扫描器与业务断言不变。精确守卫通过。 |
| R-4 / V-4 | IMPLEMENTED | 在 flush 之前保护本地/未保存输入，在 INBOUND LEGACY adapter 挂载/写入之前读取并检查当前普通稿。共用现有冲突提示，保留 owner/target/epoch/revision/输入陈旧保护与 OUTBOUND 恢复语义。双独立浏览器确认提示先出现、legacy editor 未挂载、新稿内容/版本/有序附件不变、撤回快照仍可读取；无冲突仍恢复且不制造普通 draft。 |

### Changed files

1. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt` — R-1 比较表达式与必要注释。
2. `src/test/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyQueryIT.kt` — 两种真实 MySQL 排序规则组合、完整消息查询/抑制/权限/附件/固定查询数回归；finally 恢复原列排序规则。
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt` — 精确区分快照超字节错误。
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` — 共用入队 seam 的窄 413 映射。
5. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplyDelayEndToEndIT.kt` — 合法正文加大 HTML 注释的真实控制器超限与零副作用回归。
6. `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` — 仅两个精确行号。
7. `src/main/resources/static/mailbox-chat.js` — INBOUND adapter 之前的新稿冲突检查。
8. `src/test/js/mailboxDelayedReply.test.js` — 真实 restore consumer 的服务端新稿、本地稿、未保存输入、无冲突与陈旧回包行为回归。
9. `src/main/resources/static/index.html` — 现有资源缓存键统一为 `20261106-manual-reply-repair`；不新增 DOM/CSS 设计。

产品提交已核对仅以上九文件，275 insertions / 35 deletions。本报告是单独的授权证据文件；没有改 repair、master、ledger、machine-verification、知识文件、迁移或其他产品文件。

### Fresh required commands

所有命令 cwd 均为 target worktree。Maven 均显式使用 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`，并设置 `DB_URL=jdbc:mysql://127.0.0.1:33471/repair_runtime?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`、`DB_USERNAME=root`、隔离临时密码 `DB_PASSWORD=repair-local-only`，没有连接默认开发库或生产库。命令经 `/tmp/manual-reply-delay-repair-2faeacade/run-command.py <receipt-name> <command...>` 保存 argv/cwd/environment/起止时间/exit/stdout/stderr；以下为实际子命令。

| Command | Result | Receipt / counts |
|---|---|---|
| `mvn -q -DskipTests test-compile` | PASS, exit 0 | `required-compile.*`；213.56 秒 |
| `mvn test '-Dtest=DelayedManualReplyQueryIT,ManualReplyDelayEndToEndIT,DelayedManualReplySnapshotTest,PendingMailOperationServiceTest,DelayedManualReplyControllerTest,DelayedManualReplyLifecycleIT,DelayedManualReplyWorkerTest,OperatorStatusWriteSeamGuardTest' -DmysqlIt=true` | PASS, exit 0 | `required-jvm.*`；173 run / 0 failures / 0 errors / 0 skipped；740.00 秒。Maven exec hook 另外运行全 JS 2115/0/0。 |
| `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailReviewAdmin.test.js src/test/js/expertAccessWorkspace.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxServerDrafts.test.js` | PASS, exit 0 | `required-js-focused.*`；250 tests / 0 failures / 0 skipped |
| `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxChatStyle.test.js` | PASS, exit 0 | `required-js-source.*`；118 tests / 0 failures / 0 skipped |
| `/bin/zsh -c 'node --test src/test/js/*.test.js'` | PASS, exit 0 | `required-js-full.*`；2115 tests / 0 failures / 0 skipped |
| `node --check src/main/resources/static/mailbox-chat.js` | PASS, exit 0 | `required-syntax-chat.*` |
| `node --check src/main/resources/static/app.js` | PASS, exit 0 | `required-syntax-app.*` |
| `node --check src/main/resources/static/mailbox-readonly.js` | PASS, exit 0 | `required-syntax-readonly.*` |
| `git diff --check` | PASS, exit 0 | 最终产品状态及提交范围检查均通过 |

JVM XML 的实际分项如下；没有把历史残留 `DelayedManualReplySnapshotCodecScratchTest.xml` 算进本次结果。

| Class | Run | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| DelayedManualReplyQueryIT | 10 | 0 | 0 | 0 |
| ManualReplyDelayEndToEndIT | 30 | 0 | 0 | 0 |
| DelayedManualReplySnapshotTest | 14 | 0 | 0 | 0 |
| PendingMailOperationServiceTest | 49 | 0 | 0 | 0 |
| DelayedManualReplyControllerTest | 16 | 0 | 0 | 0 |
| DelayedManualReplyLifecycleIT | 44 | 0 | 0 | 0 |
| DelayedManualReplyWorkerTest | 9 | 0 | 0 | 0 |
| OperatorStatusWriteSeamGuardTest | 1 | 0 | 0 | 0 |

### Real runtime evidence

- Owned MySQL: `mysql:8.0.36`，container `manual-reply-repair-2faeacade`；仅映射 `127.0.0.1:33471`。数据库 `repair_runtime` / `repair_native` 从空库正常启动迁移；真实运行场景没有 ALTER 列排序规则。
- 两个真实 Spring 进程：backend `18171`、native `18172`。本轮最后源码编译后启动；Java executable、完整 classpath、启动参数和环境见各自 `starts.json`。static location 指向当前 worktree。隔离配置启用 delay，禁用 discovery/scheduling/queue/LLM/translation；没有改生产默认配置。
- 两套实际 SMTP 协议接收器：backend `25471/25472`、native `25473/25474`；仅接受 `@example.test`，写真实 MIME，不替换业务服务 bean。
- 精确启动子命令：`python3 /tmp/manual-reply-delay-repair-2faeacade/start-runtime.py backend`、同脚本 `native`，均 exit 0；各执行一次。
- 精确后端场景命令：`python3 /tmp/manual-reply-delay-repair-2faeacade/backend-smoke.py`，执行一次、exit 0。`expert_contact.expert_email=utf8mb4_unicode_ci`，`email_suppression.email=utf8mb4_0900_ai_ci`，场景前后均未改变。真实消息 GET 200；无抑制和不相关抑制时 canCancel=true，命中抑制及 READ 时 false。合法排队的 due-created=60000，随后在期限内真实取消。
- 同一后端场景的合法 HTML 为 4200019 UTF-8 bytes，HTTP 413 / SNAPSHOT_INVALID。前后 jobs=0、attempts=0、SENT=1（种子锚点）、calendar=0；SMTP control 实际 events=[]。没有把种子 SENT 行误算为新发信。
- 原生入口：managed `browser.open` + `tab.run(async({page},opts)=>require(managed-runner.cjs)(page,opts), {args:[opts], timeout:150000})`，Chrome `157.0.8103.39`；实际 pointer/keyboard/file-input 操作，不伪造 DOM、业务状态或时钟。`native/managed-runner.cjs` SHA-256=`9ecf7b32c8a28b3561211deaa5684f8bee5343394b6abf066dcb25e4a914ab06`。这是 managed API 执行，没有可虚构的 OS exit code；最终返回 `ASSIGNED_NATIVE_CHECKS_COMPLETE`，五个指定场景通过。
- 双浏览器使用不同 Chrome 进程及 user-data-dir，不是共享 localStorage 的两个 tab。冲突场景普通稿 version 2→2、正文及两附件顺序不变；明确提示出现、legacy editor count=0；另一浏览器的新稿可见，CANCELLED 原快照仍可读。无冲突场景 legacy editor count=1、原正文恢复，普通 draft 前后均不存在。

| Native late-confirm surface | Open relative to due | Confirm relative to due | Observed result |
|---|---:|---:|---|
| Ordinary DRAFT / admin | -947 ms | +1149 ms | 单次 POST 409 → 单次权威 GET 200 / SENT；无假撤回、无重复 POST |
| APPROVED REVIEW / admin | -902 ms | +1147 ms | 同上；审核冻结字段不变 |
| DRAFT / delegated FULL user1 | -922 ms | +1155 ms | 同上；390×1100 真实界面，附件保留 |

成功 late runs 的点击 `isTrusted=true`、expectedRevision 保留、page errors=[]。对应 Message-ID 与 attempt/mail_record/MIME 一致；subject/plain/html 与冻结载荷逐字相等，SMTP headers 按冻结 `source.smtpInReplyTo/smtpReferences` 比较一致。委派二进制附件 12 bytes 完全一致，SHA-256=`e9ba8826225a9bf5c301b12b5eabed5fbbd95266ff600c3f62879f5a1f5d1da6`。浏览器实际收到的 index/chat/CSS hashes 与当前 worktree 一致。截图、请求/响应、GET/POST 顺序、冻结审核对照及 MIME 均见 `native/native-manifest.json`。

### Red evidence, harness corrections and deviations

- 产品范围及设计约束：无偏离。60 秒、UUID/fingerprint/Message-ID、短事务/锁/CAS、SMTP 事务外、快照白名单/上限、UNKNOWN 不重发、重试规则、权限、审核冻结及异步上下文边界未扩改。
- 新后端回归旧代码执行：`mvn test '-Dtest=DelayedManualReplyQueryIT#unicodeContactAnd0900SuppressionKeepMessagesReadable+contact0900AndUnicodeSuppressionKeepMessagesReadable,ManualReplyDelayEndToEndIT#oversizedSnapshotReturns413WithoutSideEffects' -DmysqlIt=true`，显式相同 JDK11；exit 1，3 run / 1 failure / 2 errors / 0 skipped。两种真实列组合均报 1267；超限 expected 413 / actual 400。`backend-red.*` 原输出完整保留。
- 新前端回归旧代码：`node --test --test-name-pattern='^inbound restore consumer protects (latest server draft|saved local draft|unsaved input) before legacy adapter mount$' src/test/js/mailboxDelayedReply.test.js`。首轮 exit 1 是 extractFn 不识别 async adapter 的测试提取问题；改用仓库现有 adapter 提取方式后，同命令仍 exit 1、3 run / 3 failures / 0 skipped，精确失败于“进入 legacy editor 前必须提示”。只有后者是产品 RED；分别保留 `r4-red.*`、`r4-red-fixture-corrected.*`。
- MySQL coercibility probe 首轮候选比较已成功，后续同一临时表重复引用造成 1137；拆成独立 literal probes 后成功。保留 `r1-coercion-probe.json` / `r1-coercion-plan-probe.json`，未修改 runtime 数据库列规则。
- 原生 runner 先前五次失败及其他浏览器启动/选择器/上传/工具问题全部保留，见 manifest。包括真实附件 autosave 尚未结束的版本比较、导航后监听器未安装、已发送稿需要原生“继续编辑”；修正 harness 后再取得完整证据，没有据此改产品。初次普通场景 job3 确实发送，其 MIME 单独保留，不冒充后三个完整 late gate。
- MIME 首次比较错误地把持久化字段 `payload.inReplyTo` 当 SMTP header authority；读取既有发送合同后改用冻结 `source.smtpInReplyTo/smtpReferences`，原值为 null、实际 wire 一致。失败比较保留；没有 SMTP 产品缺陷或额外修复。
- Maven 输出中测试故障注入与 Testcontainers 关闭后的 Hikari/attachment-worker 日志保留；实际 Surefire failures/errors/skipped 均为零。隔离 ES=127.0.0.1:9 会让可选画像请求出现 connection-refused/500，包括无冲突截图中的提示；没有将它描述成“无任何网络错误”。
- Kotlin LSP references 返回 no language server；JS 项目也未配置 language server；没有虚报 LSP 成功。
- 归档首个 `tar` 命令把 `local://` 作为字面路径而 exit 1，改用实际 `/tmp` 绝对路径后 exit 0。清理脚本与命令包装器同名 `runtime-cleanup.json` 导致中间操作列表被最终命令 receipt 替换；原执行脚本、PID 文件、最终 stdout `CLEANED`/七端口关闭及 exit 0 保留，没有重建或伪造丢失时间戳。

### Evidence and cleanup

- Raw root: `/tmp/manual-reply-delay-repair-2faeacade/`。`gate-results.json` 汇总当前门禁，`final-source-hashes.json` 绑定最终九文件；每个 shell receipt 保存精确 argv/cwd/environment/exit；selected Surefire XML 随归档保留。
- Frozen archive: `/tmp/manual-reply-delay-aggregate-repair-2faeacade-evidence.tar.gz`
- Archive SHA-256: `566063c986d68b6f054270d3fd22dbe790fb6a437ba5e60678c7a43e047bea24`
- 归档包含源码 hash、全部 RED/final logs、实际脚本/配置/seed、HTTP/SQL/网络响应、截图、MIME、数据库最终 dump 和所选 XML；不含浏览器 profile cache。归档在产品提交前冻结；之后的 Git receipt 单独保存在 raw root 的 `commit-receipts.json` / `product-commit-proof.json` / 最终交接证明，不能声称这些后来文件在该固定 archive hash 内。
- `python3 /tmp/manual-reply-delay-repair-2faeacade/finish-runtime.py`，一次执行、exit 0：先 dump 两个隔离库，再核对 PID 与所属监听器，停止自有 app/SMTP，停止并移除自有 MySQL container；18171/18172/25471–25474/33471 均关闭。浏览器 tab 全部关闭，自有 Chrome 进程在核对 profile args 后停止；未触碰其他进程或数据库。
- 临时 harness/fixture/输出均在 `/tmp`，不进入产品提交；报告归档保留用于后续独立复验。

### Commit and freshness evidence

- 产品 add/commit 前分别运行 `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py <Plan> --worktree <Target worktree> --expect-root <Target worktree> --expect-branch fast/manual-reply-delay-master --expect-git-dir <Git dir>`，均 exit 0，绑定相同起始 HEAD。
- 产品提交后：HEAD 与目标 branch 均为 `fc2750cd7ad4ae5200195036d874aeeb8b5ae400`；`git merge-base --is-ancestor` exit 0；subject 和 `diff-tree` 文件集合精确匹配；`git status --porcelain` 为空，index/worktree 干净。
- Plan identity rechecked: YES，仍为本记录 SHA-256。
- Worktree identity rechecked: YES。
- Required commands run this invocation: YES，在最终产品源码之后执行；不是历史输出替代。
- Historical evidence used only as baseline: YES。
- 源码 hash 在命令/真实场景后及产品提交前再次一致。证据提交前仍须分别执行 identity guard；提交后须证明该提交是产品提交的直接后继，仅含本文件、主题精确，两个 SHA 均可达，index/worktree 干净。实际结果与 literal Evidence HEAD 在最终交接中记录，避免自引用提交哈希。

### Remaining blocker and next action

- 本限定 repair 没有剩余实现或执行门禁 blocker。
- **不是 master PASS，不是部署许可。** B-1 hidden/visible 原生证据仍留给独立 aggregate 回审；人类 A1–A12 全部 PENDING。
- 原计划明确排除的 subject / size100 基线失败，以及 timezone、旧反射/target 污染等历史边界没有修复、没有重跑来“证明失败”、也没有改称通过。
- 独立验证仍须依据 master / Epoch 1 完整矩阵覆盖原必跑范围、历史失败与 exclusion、Flyway/mysqlIt、运行组合，并重新处理 B-1；本轮限定门禁不能替代该矩阵。
- 下一动作：由新的独立验证者运行 verify-p／授权的 aggregate review-fast-p。当前 execute-p 仅交接 `READY_FOR_VERIFICATION`，不自动启动复审、push、merge 或部署。

---

## Hidden-confirmation repair — NEW epoch — 2026-11-06

### Execution Result: READY_FOR_VERIFICATION

本节仅记录当前 R-1 / V-5 的批准后执行证据，不是独立 verify-p、aggregate/master PASS、人工接受或部署许可。上面的旧 epoch 完整保留，不能用于替代本次命令或运行证据。

### Identity and approval

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md`
- Plan SHA-256: `f96501759344c97dd27cc6e7bce2cbc3f842c703ddd376a03f7996e15652ef03`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md@f96501759344c97dd27cc6e7bce2cbc3f842c703ddd376a03f7996e15652ef03`
- Execution epoch: **NEW**。同路径旧 SHA `2faeacade4c57d7a03c3cfc211edc85be0da0f3beae00ba904fe1d6d64145e83` 属于历史修复，不转移其完成状态。
- Approval basis: 当前人类原始调用 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md`，精确匹配当前计划第 103 条；不是控制器、审查代理、旧批准或自动继续。
- Executor: 当前 omp coding assistant，父执行器独占集成、必跑命令、提交与本证据追加；`HiddenConfirmRegression` 仅新增授权回归，`NativeVisibilityRepair` 仅负责 `/tmp` 隔离运行与原生 Chrome 证据。执行自检采用 fix-v 的受约束纪律；行为修复权限来自本次 execute-p，不来自验证器自行批准。
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`
- Target branch: `fast/manual-reply-delay-master`
- Git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master@fast/manual-reply-delay-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`
- Master identity: SHA-256 `4fa1f45fc2830aea7c138e3da44a60d5ea767fa48a01239ca01ed01989a89a2e`；A1–A32 保持。
- Pre-execution code SHA: `fc2750cd7ad4ae5200195036d874aeeb8b5ae400`
- Invocation starting HEAD: `283fd18fd23eefed53f3b98fa0381d2f538a34a3`；起始 index/worktree 干净，`git diff fc2750cd7ad4ae5200195036d874aeeb8b5ae400 HEAD -- src` 为空。
- Post-execution code SHA: `60f4a5389e032aac50d1a8f0d8eab0519edc3ad7`
- Product subject: `fix(mail): clear delayed confirmation on hidden`
- Implementation boundary: `283fd18fd23eefed53f3b98fa0381d2f538a34a3..60f4a5389e032aac50d1a8f0d8eab0519edc3ad7`，恰好下面三个产品/测试文件。
- Evidence commit: 产品提交的唯一后继，主题 `docs(review-fast-p): record hidden confirmation repair`，仅包含本文件。其 literal Evidence HEAD 由提交后的最终交接及 `/tmp/manual-reply-visibility-exec-f9650175/final-handoff.json` 记录，不自嵌本提交哈希，不 amend、不增加第三个提交。

### Task status and changed files

| Requirement | Status | File | Evidence |
|---|---|---|---|
| R-1 / V-5 | IMPLEMENTED | `src/main/resources/static/mailbox-chat.js` | `stopDelayPolling()` 仅在已有 confirm 时清空并调用现有 `renderDelayActions()`；同步 footer，不重挂载 timeline/editor，不改变请求、期限或权限。 |
| R-1 discriminating regression | IMPLEMENTED | `src/test/js/mailboxDelayedReply.test.js` | 一个回归内覆盖 cancel/retry：合法打开、hidden 后旧 group/两个按钮移除、隐藏零轮询、同任务仅 serverNow 变化的实际回读、旧引用零 POST、新入口显式确认一次/current revision、正文与卡片节点不变。 |
| R-1 cache cutover | IMPLEMENTED | `src/main/resources/static/index.html` | 仅既有 20 个 version 值改为 `20261106-manual-reply-visibility`；整个文件与起始版的差异严格等于这 20 次替换，集合/顺序/URL 形状、无版本 task runtime 保持。 |

产品提交为 113 insertions / 21 deletions；没有修改 CSS、app/readonly、后端、迁移、其他测试、批准计划、历史审查证据或知识库。当前文件是计划单独授权的 append-only 证据，不计入产品三文件。

### Fresh required commands

所有 cwd 均为 target worktree。命令经 `/tmp/manual-reply-visibility-exec-f9650175/run-command.py <receipt-name> <command...>` 保存精确 argv/cwd、起止时间、exit、完整 stdout/stderr 及最终三文件 SHA；以下都是所有产品/测试编辑结束后的新鲜执行。

| Exact command | Result | Receipt |
|---|---|---|
| `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailReviewAdmin.test.js src/test/js/expertAccessWorkspace.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxServerDrafts.test.js` | PASS, exit 0; 251 tests / 251 pass / 0 fail / 0 skipped | `required-js-focused-final.*` |
| `node --test src/test/js/*.test.js`（由 `/bin/zsh -c` 展开） | PASS, exit 0; 2116 tests / 2116 pass / 0 fail / 0 skipped | `required-js-full-final.*` |
| `node --check src/main/resources/static/mailbox-chat.js` | PASS, exit 0 | `required-syntax-final.*` |
| `git diff --check -- src/main/resources/static/mailbox-chat.js src/test/js/mailboxDelayedReply.test.js src/main/resources/static/index.html` | PASS, exit 0 | `required-diff-final.*` |
| `python3 /tmp/manual-reply-visibility-exec-f9650175/source-contract.py` | PASS, exit 0; 20 资源集合/顺序/同键、index 仅版本替换、CSS 逐字不变 | `source-contract-final.*`、`source-contract.json` |

未运行可选全 JVM、mysqlIt/migrationIt 或无关 Kotlin 再编译；这是当前 repair 明确限定的执行门禁，不冒充后续 aggregate/master 必跑范围通过。

### Actual Spring/MySQL/SMTP/native Chrome

- 自有运行根：`/tmp/manual-reply-visibility-f9650175/`。`python3 /tmp/manual-reply-visibility-f9650175/start-runtime.py` 和 `extra-fixtures.py` 实际 exit 0。MySQL 8.0.36 container `manual-reply-visibility-f9650175`、隔离库 `visibility_f9650175`、仅本机端口 33491；Spring 18191；实际 SMTP 25491/control 25492，只接收 `@example.test`。
- 运行 JVM 为 Zulu 11.0.32；使用 target 已编译且本次未改动的后端，当前 worktree 的静态 file location。完整启动 argv/classpath/config、后端源码与 class 身份在 `starts.json`、`startup-commands.json`、`backend-identity.json`；没有把旧 target 静态资源当当前源码。
- 真实 `browser.open` Google Chrome 原生有头窗口，`tab.run` 中原生 locator 输入；实际切同窗口后台 tab，两个 target 的 Chrome windowId 相同。每个最终 hidden 场景均实际采样 32 次 `document.hidden=true`，未覆盖 visibility、clock、业务状态或服务端响应。只在既有 late409 场景延迟真实 GET，之后原样释放。
- 精确实际 API 调用在 `actual-native-api-calls.json`，明确为该工作代理执行 transcript 的事后重建，不是自动原始 trace，不伪造时间戳/OS exit。原始 request/response、点击 `trusted=true`、截图及结果分别保留。`replay-commands.json`/`replay.cjs` 为可重放整合配方，wrapper **未单独运行**；实际执行的是对应独立阶段。

| Native surface | Hidden conversation requests | Automatic action POSTs | Resume GET latency | Stale confirmation/submit/dismiss | Fresh explicit POST / captured revision |
|---|---:|---:|---:|---|---|
| Ordinary/admin, job 4 | 0 | 0 | 170 ms | 全部消失 | 1 / 1 |
| Delegated FULL, job 5 | 0 | 0 | 16 ms | 全部消失 | 1 / 1 |
| Delegated READ, real WAITING job 10 | 0 | 0 | 实际恢复 GET 2 次 | 从无可执行控件 | 0；另行显式 cancel/retry 探针各 403 |

普通和 FULL 的恢复响应均实证除 `serverNowMs` 外同 job/revision/status/capability，仍 WAITING 且未到期；没有依靠状态变化触发强制重绘。前后 editor 身份、正文/格式、版本、有序两个附件相等。相关证据为 `ordinary-visibility-final-v3-result.json`、`delegated-FULL-visibility-result.json`、`delegated-READ-visibility-result.json` 及各自 `*-network.json`。

| Native late-confirm surface | Open relative to due | Confirm relative to due | Observed explicit action |
|---|---:|---:|---|
| Ordinary/admin, job 8 | -949 ms | +1130 ms | 唯一 POST 409 → 动作归属 GET 200 |
| Delegated FULL, job 7 | -970 ms | +1130 ms | 唯一 POST 409 → 动作归属 GET 200 |
| APPROVED REVIEW, job 9 | -980 ms | +1120 ms | 唯一 POST 409 → 动作归属 GET 200 |

三宿主均保留 captured expectedRevision=1、公开 `DELAYED_REPLY_TOO_LATE` /「已超过撤回期限」，回读实际 SENT，不假报已撤回、不自动重试。REVIEW version/state/subject/html/text 不变；发送结果和当前会话 context 的正常变化没有冒充审核内容修改。对应 `ordinary-admin-late-result.json`、`delegated-FULL-late-complete-result.json`、`APPROVED-REVIEW-late-completed-result.json`。

### Source, resource and visual proof

| Current served source | SHA-256 |
|---|---|
| `index.html` | `29f20cf6ee25d9edd3c155dc4785b08c09f378fa8ebf5505cacfbcbcfd018d45` |
| `mailbox-chat.js` | `a9a112eeaef082280837949292cb083e9d9f8c935e38bbeafb19dd98d36be07d` |
| `mailbox-chat.css` | `9ac18fb00fcf5d565cb05489c4c77bde963f45a016ff614191f82569de38df24` |
| `styles.css` | `464ebaf5206af9e699a850758568e1f776f5fc785564bbb596e6b02a13b122b7` |

`source-proof.json` 记录实际 HTTP 200 响应 bytes SHA 与磁盘相等；两份 CSS 与起始 HEAD 逐字相同。最终 test SHA 为 `1e0a27c8b5c2072b4605025d64884765f9850562aec53a28b0118aabf85f6d5f`，与 final 命令和产品提交 receipt 相等。

资源搜索回执为工具 `grep(pattern="\\?v=|task-modal-runtime.js", path="<target>/src/main/resources/static/index.html")`：11–17 的 CSS 7 条、2547–2559 的 JS 13 条；2546 的 task runtime 无版本。旧 key 的 `grep(pattern="20261106-manual-reply-repair", path="<target>/src/test", gitignore=false)` 返回 No matches found。最终 20 条完整有序清单及字节替换比较保存在 `source-contract.json` 和 `source-proof.json`，没有在产品 JS/测试硬钉新日期。

1440/390 的隐藏前和重开后 footer computed style 相等、既有 class/aria 不变；移动确认两个按钮均 44px。父执行器实际查看 `ordinary-visibility-final-v3-reopened-1440.png` 和 `delegated-FULL-visibility-reopened-390.png`；全部 before/after、三宿主 late409、READ 截图保留在 runtime 根。没有新增 DOM 约定、CSS、overlay 或页面 reload。

### Failed probes and disclosed limits

- 新增回归先在未修复 chat SHA `0e9c5782132aaa729464acb96944f8dece7d6ab87e8eecf086ae8ae411969eb5` 执行。首轮 RED 只产生文件级 test failed，原样保留 `regression-red.*`，不虚构具体内部失败。将 DOM 整对象比较改为等价 boolean 可见性断言后，`regression-red-boolean.*` exit 1，1 test / 0 pass / 1 fail / 0 skipped，精确失败于 `cancel: hidden must remove visible confirmation DOM`，true !== false。
- 首轮 focused 为 251/250 pass/1 fail，full 为 2116/2115 pass/1 fail，均 0 skipped。唯一失败是新增测试发现共享 MiniDOM `innerHTML` setter 仅清 childNodes、不清旧 parentNode，导致 contains/冒泡语义与原生 DOM 不同。在授权测试内只给当前 actions 节点补齐 native replacement 的 detach 语义；不改共享夹具、不手动重绘、不删业务断言。最终命令全部重新执行通过，初次失败 `required-js-focused.*` / `required-js-full.*` 保留。
- 原生 8 个失败探针完整列于 `probe-ledger.json`：失效空页；新文档 click observer 未出现；旧 CJS 缓存；新 CJS resolver；13 次真实 hidden 后意外前台（原因未知）；tab.run 默认 30 秒超时；REVIEW late 动作成功后不慎仍 hold 住 invariant GET；text/plain MIME 换行强比较。全部已有 raw failure/network/screenshot 保留，没有改产品来适配 harness。
- REVIEW 原始动作、409→GET 和截图断言已经完成，随后被错误 hold 的审核 invariant GET 使那次完整工具调用超时，不能称原调用整体成功。释放后另行真实读取完成冻结字段核对，没有重复已经完成的动作；原失败与 follow-on 在 actual API receipt 中分别标注。
- 原始附件各 38 bytes/LF，冻结快照保持原始哈希和 a→b 顺序；SMTP text/plain 解码为 39 bytes/CRLF。首次逐字比较失败保留；最终仅明确按 CRLF normalization 比较 wire 内容/顺序，**不声称 wire 原字节相同**。这是计划排除的既有 O-6，未修复、未掩盖。共接收 6 封实际测试 SMTP；visibility 已取消 jobs 4/5 不发，late jobs 7/8/9 SENT；其他探针产生的发送独立保留。
- 隔离 ES 不可用造成已有 profile lookup 500，完整保留，不声称所有网络请求均成功。无生产连接或真实收件人。
- 没有重开 V-1–V-4/B-1、修复 O-1/O-2/O-3 或执行可选全 JVM；未修改人工 A1–A12 的 PENDING 状态。

### Evidence, cleanup and commit checks

- Parent raw root: `/tmp/manual-reply-visibility-exec-f9650175/`；runtime raw root: `/tmp/manual-reply-visibility-f9650175/`。保留实际脚本、命令/浏览器调用、全部失败探针、HTTP/SQL/MIME、截图、最终数据库 dump 和 cleanup。
- Frozen archive: `/tmp/manual-reply-visibility-f9650175-evidence.tar.gz`
- Archive SHA-256: `05bf5e358cc38fb36a8f2cbf395885fbfabaa08a6f6ce9546f5557bd4505f0c6`
- 实际归档命令：`tar --exclude='chrome-final-profile' --exclude='chrome-profile' -czf /tmp/manual-reply-visibility-f9650175-evidence.tar.gz -C /tmp manual-reply-visibility-exec-f9650175 manual-reply-visibility-f9650175`，exit 0；浏览器 profile cache 未打包。冻结发生在产品提交及其 proof 之后、本证据追加之前；后来的证据提交/最终身份 receipt 不属于此固定 archive hash。
- 实际 `python3 /tmp/manual-reply-visibility-f9650175/cleanup.py` exit 0：先 dump 342934 bytes 自有库，再核对并终止自有 app 18442 / SMTP 18441，移除唯一自有 container 和 volume。浏览器调用 `browser.close({name,kill:true})` 仅关闭本轮两个 owned Chrome；随后 `ps -p 20199,22371 -o pid=,command=` exit 1/无输出。没有清理共享服务、其他容器或用户进程。
- 产品 add 与 commit 前分别运行 `worktree_identity.py` 的 `--expect-root` / `--expect-branch` / `--expect-git-dir`，均 exit 0，详见 `product-add-identity.*` / `product-commit-identity.*`。产品提交后 HEAD 和目标 branch 均为 `60f4a5389e032aac50d1a8f0d8eab0519edc3ad7`；ancestor 检查 exit 0；主题/三个文件精确匹配；本证据追加前 `git status --porcelain=v1` 为空。
- 当前 repair SHA 与 master SHA 在提交前重核一致。证据 add/commit 前再次逐项身份守卫，提交后核对父提交、唯一路径/主题、两个提交可达性及 index/worktree 干净；实际证据 HEAD 与最终结果保存于最终交接，不为自引用额外提交。

### Deviations, remaining blocker and next action

- 产品范围/设计偏离：无。无剩余限定 repair 实现或执行门禁 blocker；失败 harness 探针与排除观察如上披露，不作为伪 PASS 删除。
- Required commands run this invocation: YES；historical evidence used only as baseline: YES。
- 本修复不宣称独立核验通过。下一动作由新的独立验证者运行 `verify-p`；若另有人类明确授权，再恢复同一 master 的 aggregate/review-fast-p 完整矩阵。
- 人工 A1–A12 仍 PENDING；没有 push、merge、部署或自动下一轮修复授权。
- 证据追加自身的首次 `git diff --check -- docs/plans/review/manual-reply-delay-master/repair-execution.md` 报告新增 EOF 空行、exit 2；仅删本次新增空行，历史正文不变。原始诊断另记 `evidence-whitespace-probe.json`，属于归档冻结后的证据提交 receipt，不虚称包含在前述固定归档内。
