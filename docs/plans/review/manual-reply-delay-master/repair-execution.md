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
