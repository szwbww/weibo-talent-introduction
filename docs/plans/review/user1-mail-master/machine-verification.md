# Aggregate Machine Verification — user1-mail-master

## Epoch 1 — 2026-10-28 Asia/Shanghai

- Master plan: docs/plans/2026-10-27/user1-mail-master.md
- Governing master identity: sha256 b4b07118053d570c3987f62602aff80df554a7f6700de8e0ede74a591bfae69a; commit 258f435c8faefb15c187c6cd34669ac0ad92a717
- Master identity state: CONSISTENT; approved A1–A8 carried in full below
- Boundary: 128488eeac26781fbf7c6892ff9d3d4a3f5c7c10..7750a720b6fa276eb6e79ee468e40b7be98c7fdc
- Reviewer: /root/aggregate_reviewer, fork_turns=none
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A
- Repair evidence mode: N/A

Complete independent review-p output follows.

## Verification Result: PASS

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master/docs/plans/2026-10-27/user1-mail-master.md`  
Phase: aggregate/master；review epoch 1  
Reviewer: `/root/aggregate_reviewer`，独立新上下文  
Implementation boundary: `128488eeac26781fbf7c6892ff9d3d4a3f5c7c10..7750a720b6fa276eb6e79ee468e40b7be98c7fdc`  
Evidence HEAD: `d26746b881ef6cbad861a965adddbef7fb74db08`  
Branch: `fast/user1-mail-master`  
Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master`  
Convergence: **INITIAL**  
Manual acceptance: **PENDING**

PASS 针对批准范围内的机器契约。全量 Maven、SQL 组合和原始 AuthFlow 命令仍存在下述已核实的基线错误；不声称全部测试通过，也不授权上线。

### 身份与证据边界

- Governing/invoked master identity 一致：commit `258f435c8faefb15c187c6cd34669ac0ad92a717`；SHA-256 `b4b07118053d570c3987f62602aff80df554a7f6700de8e0ede74a591bfae69a`。
- `master_identity_state=CONSISTENT`；invoked identity 到当前 master diff 为空。
- Governing amendment：A7；master「变更文件清单 / I-5 / 执行前检查第5项」；child05 cap 4→5、记录的 dedup 35→36；人工批准 `Approve 5th test file (Recommended)`，2026-10-28。
- Retroactively authorized unapproved files：N/A。
- Fast ledger SHA-256：`7447eedc9b1639c825fc9bb7101910c9fdfe3bf9878a9bb6044368e73d350b11`。
- Fast handoff SHA-256：`e84ea2cfe665b6fc52af266b14f34775cc0a30a1e91c29b21dfd9428ebcbe15c`。
- Worktree resolution：`DISCOVERED_FROM_GIT_WORKTREES`，采用控制器已选定的唯一 candidate；独立复核了路径、branch、HEAD、ancestry 和 hashes。未读取其他 worktree 的 review ledger。
- base→code→evidence ancestry 均成立；code→evidence 的 `src/` diff 为空。
- 已读 master、五份 child plan、共同审计、ledger/handoff，以及五个 child 的全部 `brief/execution/fix-log/verify-log`，共20份 child evidence。
- 完整合并源码 diff：`/private/tmp/user1-review-combined-src.diff`，346142 bytes。
- 最终复核：HEAD、branch、三个 governing hashes 均未变化；index clean；产品/测试源码与 final code head 完全一致。唯一 untracked 路径为控制器所属 `docs/plans/review/user1-mail-master/`。

以下路径缩写均相对于该 worktree：

- `P`：`docs/plans/2026-10-27/`
- `B`：`src/main/kotlin/com/weibo/talentintroduction/`
- `T`：`src/test/kotlin/com/weibo/talentintroduction/`
- `F`：`src/main/resources/static/`
- `J`：`src/test/js/`

### 批准修订核对

Identity bindings：

| 标识 | 精确 commit |
|---|---|
| S | `4d1698bb7c51120e98cfe8f6a412a4589fc9defd` |
| A12 | `d66f54251af163f105c6244a6d06c4b28042518e` |
| A34 | `d52463bb0c530b6ab5ac83c27cd34c321b5b651f` |
| A5 | `9b1fd16c2ebd19d2899be2c850e7445f44bb2909` |
| A67 | `258f435c8faefb15c187c6cd34669ac0ad92a717` |
| A8 | `c03ece5900ce45835da8c449a8fb5aef69d47ffd` |

全部批准日期均为2026-10-28。

| ID | Plan；before→after | Governing rule、原因、人工批准 | 核对结果 |
|---|---|---|---|
| A1 | `P/user1-mail-02-mail-queries.md`；S→A12 | Master I-2；child02 I-2/step5；允许第7文件 `AuthInterceptorTest.kt` 同步 listMessages mock 4→5参数；`Approve 7th file (Recommended)` | PASS；该 child 的文件 diff 仅新增身份参数和说明，断言语义保留 |
| A2 | `P/user1-mail-master.md`；S→A12 | 变更文件清单/I-5/执行前检查5；同步02 cap6→7、记录 dedup28→29；同上批准 | PASS，授权清单明确；总数计算另见非阻断 V-1 |
| A3 | `P/user1-mail-03-readonly-ui.md`；S→A34 | Master I-5；child03 I-5/K-frontend-cache-key-triad；允许6个测试仅同步18→19及 app 前插入只读资源；`Approve 6 test files (Recommended)` | PASS；六文件均为规定的机械同步 |
| A4 | `P/user1-mail-master.md`；A12→A34 | 变更文件清单/I-5/执行前检查5；同步03 cap7→13、记录 dedup29→35；同上批准 | PASS；13文件属于明确批准的例外 |
| A5 | `P/user1-mail-05-account-activation.md`；S→A5 | Master I-4；child05 I-4/step3；允许 AuthFlow 测试 override 与 primary NamedParameterJdbcTemplate 接线；`Enable test-only wiring (Recommended)` | PASS；按 A8 的最终范围审查；原命令和 wired 命令分别执行 |
| A6 | `P/user1-mail-05-account-activation.md`；A5→A67 | Master I-4；child05 I-2/step3；允许第5文件 `AuthControllerMvcTest.kt` 替换失效的 user1 登录拒绝断言；`Approve 5th test file (Recommended)` | PASS；保留其他断言，新增接受 user1 及拒绝非白名单 |
| A7 | `P/user1-mail-master.md`；A34→A67 | 变更文件清单/I-5/执行前检查5；同步05 cap4→5、记录 dedup35→36；同上批准 | PASS，当前 governing master；总数问题见 V-1 |
| A8 | `P/user1-mail-05-account-activation.md`；A67→A8 | Master I-4；child05 I-4/step3；扩大测试接线至 primary DataSource/JdbcTemplate/NamedParameterJdbcTemplate/transactionManager 与 catalog mock；`Approve full wiring (Recommended)` | PASS；局限于获批 AuthFlow 文件，未进入产品 WAR |

当前 child05 governing SHA-256：`f91e0df5504e06e0229ae0f07476344bf2ef861cc39bdb2d795bf8801fee9ebb`。

### Commands

所有 Maven 命令均在选定 worktree 顺序运行，使用：

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
```

SQL 命令 #02/#04 另使用：

```text
DB_URL=jdbc:mysql://127.0.0.1:3307/talent_introduction_user1_it?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=root
```

AuthFlow #12a/#12b 另使用 `DOCKER_API_VERSION=1.40`，由该类创建独立 disposable MySQL 8.0.36。未将 SQL 组合指向其他持久数据库。

| ID | 精确命令 | 结果与计数 | 原始证据 |
|---|---|---|---|
| 01 | `mvn -B -Dtest=AuthInterceptorTest,AuthControllerMvcTest test` | PASS，exit0；33 JVM，0F/0E/0S；19 interceptor +14 MVC；绑定 JS1911/1911 | `/private/tmp/user1-review-command-01.log:522`；BUILD SUCCESS:3155 |
| 02 | `mvn -B -Pmysql-it -Dtest=ExpertMailReadAccessIT test` | PASS，exit0；9/0F/0E/0S；绑定 JS1911/1911 | `/private/tmp/user1-review-command-02.log:506`；BUILD SUCCESS:3139 |
| 03 | `mvn -B -Dtest=MailboxReadAccessProjectionTest,OutboundAttachmentFlowTest,AuthInterceptorTest test` | PASS，exit0；34/0F/0E/0S：6 projection、9 outbound、19 interceptor；JS1911/1911 | `/private/tmp/user1-review-command-03.log:530`；BUILD SUCCESS:3163 |
| 04 | `mvn -B -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailReplyDraftSendIntegrationTest,ExpertMailReadAccessIT test` | **FAIL，exit1；127/0F/7E/0S**，错误集合与核实的基线一致；见下 | `/private/tmp/user1-review-command-04.log:1900` |
| 05a | `node --check src/main/resources/static/mailbox-readonly.js` | PASS，exit0 | `/private/tmp/user1-review-command-05a.log` |
| 05b | `node --check src/main/resources/static/app.js` | PASS，exit0 | `/private/tmp/user1-review-command-05b.log` |
| 05c | `node --check src/main/resources/static/mailbox-chat.js` | PASS，exit0 | `/private/tmp/user1-review-command-05c.log` |
| 06 | `node --test src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailboxOutboundAttachments.test.js` | PASS，exit0；335/335，0fail/0skip | `/private/tmp/user1-review-command-06.log:458` |
| 07 | `node --test src/test/js/mailboxMailReadAccess.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxServerDrafts.test.js` | PASS，exit0；285/285，0fail/0skip | `/private/tmp/user1-review-command-07.log:388` |
| 08 | `node --test src/test/js/*.test.js` | PASS，授权 escalation 后 exit0；1911/1911，0fail/0skip | `/private/tmp/user1-review-command-08.log:2617` |
| 09 | `mvn -B -Dtest=ReadOnlyUserBootstrapTest,AdminUserBootstrapTest,AuthControllerMvcTest,AuthInterceptorTest test` | PASS，exit0；43/0F/0E/0S：6/4/14/19；JS1911/1911 | `/private/tmp/user1-review-command-09.log:533` |
| 10 | `mvn -B test` | **FAIL，exit1；5672/0F/19E/24S**；357 class reports；19错误全部为既有 timezone 类 | `/private/tmp/user1-review-command-10.log:12261` |
| 11 | `mvn -B -DskipTests package` | PASS，exit0；WAR52060382 bytes；绑定 JS1911/1911；不将跳过 JVM 测试视作验收通过 | `/private/tmp/user1-review-command-11.log:3109` |
| 12a | `mvn -B -Pmigration-it -Dtest=AuthFlowIntegrationTest test` | **FAIL，exit1；8/0F/8E/0S**；8例均为原有 bean override 首因 | `/private/tmp/user1-review-command-12a.log:1481` |
| 12b | `mvn -B -Pmigration-it -Dtest=AuthFlowIntegrationTest -Dspring.main.allow-bean-definition-overriding=true test` | PASS，exit0；8/0F/0E/0S；真实 MySQL8.0.36；JS1911/1911 | `/private/tmp/user1-review-command-12b.log:795`；BUILD SUCCESS:3428 |
| G1 | `git diff --check` | PASS，exit0 | 最终 clean-state 命令输出 |
| G2 | `git diff --cached --check` | PASS，exit0 | 最终 index 核对 |
| G3 | `git diff --check 4d1698bb7c51120e98cfe8f6a412a4589fc9defd 7750a720b6fa276eb6e79ee468e40b7be98c7fdc -- src/` | PASS，exit0 | 产品/测试源码差异无 whitespace 错误 |
| G4 | `git diff --exit-code 7750a720b6fa276eb6e79ee468e40b7be98c7fdc -- src/` | PASS，exit0 | 当前源码与 final code head 一致 |
| G5 | `git diff --check 128488eeac26781fbf7c6892ff9d3d4a3f5c7c10 7750a720b6fa276eb6e79ee468e40b7be98c7fdc` | exit2；仅 seeded verbatim 规划附件 whitespace，不是产品源码错误 | `P/user1-mail-evidence/14-verbatim-baseline.md:11`、`21-backend-verbatim.md:11` 等 |

#04 覆盖 child02 和 child05 要求的同一完整 SQL 类组合；采用控制器明确允许的一次 fresh full group，无 mandatory IT 被 skip 冒充通过。

初次未 escalation 的 #08 因 sandbox `EPERM` 无法写 `target/discovery-plan-acceptance/08.html`，exit1；初次详细总数未保留。随后获准完整重跑，1911/1911。最终 PASS 不依据失败尝试。

完整顺序 Maven 结果索引：`/private/tmp/user1-review-command-results.jsonl`。

### 补充 fresh 诊断

| 命令/检查 | 结果 | 证明 |
|---|---|---|
| `node /private/tmp/user1-review-lifecycle-probe.js` | exit0，5项 PASS；同名 `.log` | 真实只读模块 HTTP500 后清正文/cursor；15秒 timeout 确实 abort 并清空；真实 `handleAuthResponse(401)`→真实 `stopAuthenticatedApp`→unmount/登录页/监听清理；身份切换先 hard reload；reader logout 仅 auth POST、跳过 draft flush |
| `node /private/tmp/user1-review-grant-identity-probe.js` | exit0，1项 PASS；同名 `.log` | 真实 MailboxChat 的 admin→user1 身份变化使 pending grant GET 失效；晚回包不创建 reader 授权节点、无额外 grant 请求 |
| WAR ZIP 内容断言 | PASS | readonly/app/chat JS、chat CSS、index、V166 与源码字节一致；bootstrap class 存在；AuthFlow/test JDBC configuration 未打包 |
| `node /private/tmp/user1-review-order-probe.js` | exit0，诊断输出 | 同 eventAt 跨页合并显示较新 ID 后追加较旧 ID；见非阻断 observation，未将其标为顺序测试通过 |

补充 probe 仅在 `/private/tmp`，未改仓库测试。

### 基线异常裁定

1. **全量 Maven 19 errors**：全部 `ExpertContactLocationServiceTest.<init>:38`，`Unknown time-zone ID: America/Coyhaique`。测试、相关产品 catalog、JDK11条件未因本实现变化；与 master base ledger 的19例同源。child05 验收明确允许记录、复现无关既有失败，禁止范围外修复。本轮不声称全量 Maven 绿。

2. **SQL 组合7 errors**：
   - Repository40例中1 error：`replied filter excludes followed and dismissed experts until a new inbound arrives`，`expert_replied_dismissal.dismissed_at` 无默认值。
   - Calendar6例全部 context errors：缺 `PendingMailOperationService`。
   - 其余 MVC45、grant9、draft-send27全部通过；新 ACL、分页、投影用例通过。
   - 独立比较 master base 与 final：上述 replied 测试体 SHA-256 均为 `2769ee55bf47b6508d8e7161f26e78465039bd7f11f51cf7cd63c397634272b8`；整个 Calendar 类体均为 `409930211fc5a21c65e8fbd5b333659bbff516f67d19403428e176db39323ccc`。Controller constructor 未改变，故不是本次新增依赖造成的失败。child02 的原基线文字不完整，不能沿用“其他IT全绿”。

3. **原始 AuthFlow 8 errors**：全部 `attachmentTransferWorker` 的 `BeanDefinitionOverrideException`，首因来自 master base 已有且未变的 `WorkerLifecycleTestConfig`。新增6例使错误数由2变8，不能把案例数增长当新根因。
   - 后续 JDBC ambiguity 中包含 child01 新测试 configuration 的影响；它是 child05 前已存在的问题，不能笼统称为全部早于 master base。
   - A5/A8 明确批准在 AuthFlow 文件内接线。fresh wired8/8证明认证/授权实际执行。
   - F-1 修复后的源码没有内联 override flag；原命令仍失败、wired 命令通过，分别如实保留。

上述异常没有被 skip、改产品配置或修改无关代码隐藏；按批准的基线比较契约不构成当前 blocking finding。

### Contract Matrix

下列机器项均 mandatory；人工项另表保留 PENDING。源码证据和 fresh 命令联合裁定，不以 child LIGHT_PASS 直接替代总验收。

| ID | 批准要求 | Verdict | 精确证据 |
|---|---|---|---|
| M-I1 | session/DB 决定权限，精确 admin/user1，reader 默认拒绝 | PASS | `B/auth/config/MailReadOnlyPolicy.kt:49`、`:55`、`:73`；`AuthInterceptor.kt:19`、`:52`；#01/#09/#12b |
| M-I2 | 无 grant 无正文；SQL count/page 同 ACL；详情每次重查；撤销后的新请求拒绝 | PASS | `B/mail/repository/MailboxConversationRepository.kt:159`、`:197`、`:238`、`:484`、`:784`；`MailboxConversationService.kt:238`；#02/#04/#12b、reader probes |
| M-I3 | 无发送、草稿、附件能力；响应附件为空；reader 无工具入口 | PASS | policy 三 handler allowlist；`MailboxConversationService.kt:273`、`:297`、`:325`；`F/mailbox-readonly.js:19`、`:318`；#01/#03/#04/#06/#08/#12b |
| M-I4 | admin、原邮件/草稿/附件、后台流程不改语义；disabled历史邮箱保留，simulator排除 | PASS，基线异常见上 | `MailboxConversationService.kt:577` 未改语义；非reader分支保留；AuthService、生产者、行号锁定文件和配置无 diff；#03/#04/#08/#09/#10/#12b |
| M-I5 | 01→05；共享文件串行；最后开放账号；最终机器与人工分开 | PASS，人工未完成 | 四个中间 code head 均 admin-only且无 reader bootstrap；05才开放；五个 child证据、fresh aggregate commands；manual PENDING |
| SCOPE | 每个 child 只改批准清单；累计 scope 准确核对 | PASS | 独立 Git比较01=10、02=7、03=13、04=3、05=5，均无 extra/missing；去重实际34，combined src diff恰34；V-1不扩大授权 |
| 01-I1 | 精确身份、真实 HandlerMethod+verb allowlist、匿名401、其余 mapped deny、OPTIONS原行为 | PASS | `MailReadOnlyPolicy.kt:49-83`；`T/auth/config/AuthInterceptorTest.kt:405`、`:451`、`:494`、`:523`、`:537`；#01/#09 |
| 01-I2 | 四列新表、复合PK/FK、row存在即授权、幂等/并发、首次时间保留 | PASS | V166:18；`ExpertMailReadAccessService.kt:36`、`:45`、`:55`、`:77`；`T/mail/service/ExpertMailReadAccessIT.kt:205`、`:256`、`:281`；#02/#12b |
| 01-I3 | grant三方法在读写前精确admin校验；固定target；400/404；只写新表 | PASS | `ExpertMailReadAccessController.kt:30`、`:42`、`:54`；service二次检查与唯一 INSERT/DELETE；IT:121、135、152、174、189、293；#02 |
| 01-I4 | 派生 mailReadOnly、匿名false、403固定code、session轮换/admin规则保留 | PASS | `AuthController.kt:39`、`:46`、`:74`、`:104`；`GlobalExceptionHandler.kt:36`；#01/#09/#12b。01历史 login关闭已由05批准替代 |
| 01-MIG | V166未改旧迁移；全新空库完整链、真实FK | PASS | V166唯一新增迁移；#12b于14:56:41实际应用165 migrations至v166；#02 FK级联与真实DB grant测试 |
| 02-I1 | ACL在分页前、OR之外，count/page/explain共用；空授权零；45/23交错分页与筛选 | PASS | repository:159/197/238/704/776/784；`RepositoryIT.kt:1165`起五个reader用例；#04 新用例实际执行，20+3、total23、alias `emra` |
| 02-I2 | HTTP显式session；详情grant先于联系人/正文/标签；missing统一403；cursor保留 | PASS | `MailboxConversationController.kt:265-279`；service:238-254；唯一HTTP listMessages调用已检索；projection测试:102/124/140/162；#03/#04/#12b |
| 02-I3 | reader固定0/[]/null/[]，不解析附件或读取附件快照 | PASS | service:273、297、325；`MailboxReadAccessProjectionTest.kt:195`；MVC reader投影用例；#03/#04 |
| 02-I4 | 非reader SQL/投影/排序不变；尾部默认身份参数；无新持久写、无后台policy扩散 | PASS | combined diff；service:224、577；timeline repository旧签名/排序保留；生产者无diff；#03/#04/#10 |
| 03-I1 | login/me先身份分流；缺metadata fail closed；桌面/手机/setView仅mailbox | PASS | `F/app.js:5128`、`:5222`、`:21116`、`:21205`、`:21297`、`:21337`；`J/authFlow.test.js:757-860`；#06/#08 |
| 03-I2 | 独立模块只GET列表/详情；不mount chat/工作台；无draft/accounts/material/groupPush等请求；缺模块不fallback | PASS | `mailbox-readonly.js:19`、`:235`、`:241`；app:4849、5297、17182、20686、20769、21159、22008、22026；#06/#08、logout probe |
| 03-I3 | 清正文/cursor，401回登录，错误不恢复；late-response隔离；destroy abort/listener清理；focus合并；15秒 | PASS | readonly:200、382、412、443、475、484、558；app:21180、21215、21255；task-runtime:311；#06/#08及5项 lifecycle probe |
| 03-I4 | 文本渲染、cleanedBody fallback、无HTML/外链/远程资源；仅q搜索；source:id去重 | PASS | readonly:39、69、76、95、235、318；`J/mailboxReadOnly.test.js:402`、`:436`、`:634`、`:660`、`:757`；#06/#08 |
| 03-I5 | admin启动/导航/chat不变；脚本注册和版本一致 | PASS | reader early return app:21230早于原admin链；#06/#08；最终版本由04批准替代为`20261027-user1-mail-access` |
| 03-S1 | 既有双栏/气泡/classes/固定文案/声明DOM结构 | PASS机器部分；视觉PENDING | readonly DOM与真实 index/CSS；`J/mailboxReadOnly.test.js:277`、`:342`；style新增断言；#06。真实1440/390截图不作已通过 |
| 03-S2 | 只追加逐字 reader CSS；旧规则不动；无新inline style/classes | PASS | `F/mailbox-chat.css:209-223`；Git仅追加15行；style test逐字比对；#06/#08 |
| 03-S3 | 原壳、body标记、单一mobile option、只读标题、既有挂载点 | PASS机器部分；键盘/视口PENDING | `app.js:21116`；`index.html:79`、`:152`及mailbox面板；#06/#08 |
| 04-I1 | 精确admin入口；固定user1；点击时捕获contact；不接受DOM actor | PASS | `mailbox-chat.js:3685`、`:3695`、`:3729`、`:3818`；#07/#08；后端#02 |
| 04-I2 | GET后才决定状态；loading/saving禁用；非乐观；校验payload；失败仅GET重试 | PASS | chat:3711、3773、3783、3818、3836；`J/mailboxMailReadAccess.test.js:977-1114`；#07/#08 |
| 04-I3 | 专家切换/unmount/身份变化使旧回包失效；写仍固定旧目标；回旧专家重GET | PASS | chat:2759、3341、3502、3761、9934；#07/#08及fresh grant identity probe |
| 04-I4 | 原actions/click保留；无草稿/邮件副作用；reader零grant；最终缓存键统一 | PASS | chat:3664、9246；index:11-17、2518-2529；`mailbox-readonly.js`无read-access；#07/#08 |
| 04-S1 | 两节点末尾追加、既有button/badge、五态文案；无新CSS/inline | PASS机器部分；真实布局PENDING | chat:829、3729、3740；grant tests:850-936；#07。不把生成DOM副本视为真实app浏览器证据 |
| 05-I1 | 首次BCrypt seed、mustChange=false、初始时间；已有行不重置；并发重查；auth=false不创建；无密码日志 | PASS | `ReadOnlyUserBootstrap.kt:26-60`；`ReadOnlyUserBootstrapTest.kt:48-152`；#09真实BCrypt与条件装配；#12b真实DB重启幂等 |
| 05-I2 | trim后精确双账号；密码仍AuthService；canonical session与ID轮换；错误凭据不认证；metadata派生 | PASS | `AuthController.kt:26-46`、`:56-74`；AuthService未改；#01/#09/#12b |
| 05-I3 | bootstrap不写grant；空授权空列表；授权A/B403/撤销；无发送/附件/改密；admin保持 | PASS | bootstrap仅admin_user写；`AuthFlowIntegrationTest.kt:366-663`；#02/#04/#09/#12b |
| 05-I4 | 全部最终命令真实执行；MySQL不skip；如实记录基线；不以静态预览代替真实登录；发布留待人工 | PASS机器部分；发布/人工PENDING | #01–#12b；AuthFlow真实MySQL8/8；全量错误保留；本次未部署、未执行人工验收 |
| BUILD | 最终构建包含03脚本/04版本/V166与账号；测试接线不进产品 | PASS | #11，WAR52060382 bytes；六个资源逐字相等；bootstrap class存在；AuthFlow/test配置不存在 |
| NON-GOALS | 无泛化RBAC、用户管理、发送/附件权限开关、ES/schema扩展、缓存/推送、翻译备注材料功能扩张 | PASS | 全部34文件diff和runtime调用审计；只新增grant表、固定策略、只读模块、header授权与bootstrap；未改生产收发后台写路径 |
| AMENDMENTS | A1–A8 exact authority、受限测试变更、原始/wired门禁分别记录 | PASS | 上述八行修订矩阵、逐commit diff、#12a/#12b；无未批准新增文件 |

### 人工验收矩阵

没有执行或制造人工结果、截图、报告人、签名。

| 来源 | 项目 | 状态 |
|---|---|---|
| Master | A-1 从零授权可读；A-2 越权/附件/撤销；A-3 admin/任务回归；A-4 真实前端/身份切换 | 全部 **PENDING** |
| Child01 | A-1 管理授权读写；A-2 默认拒绝/admin回归 | 全部 **PENDING** |
| Child02 | A-1 SQL分页/新邮件；A-2 详情/附件；A-3 历史邮箱/后台 | 全部 **PENDING** |
| Child03 | A-1 真实页面/权限菜单；A-2 撤销/失败；A-3 admin/身份切换 | 全部 **PENDING** |
| Child04 | A-1 UI授予撤销；A-2 快切/失败；A-3 原管理行为/手机布局 | 全部 **PENDING** |
| Child05 | A-1 内置账号/幂等；A-2 真实浏览器闭环；A-3 admin/后台回归 | 全部 **PENDING** |

发布环境 `auth.enabled=true`、既有 user1 canonical 名称/首改密码标志、真实服务1440/390布局与键盘焦点、受控邮件/后台任务仍由这些人工项核对。

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW，非阻断P2 | governing master:61记36个去重文件，实际批准清单并集和源码diff均34 |
| Child05:F-1 | RESOLVED | `7750a72`删除内联override；#12a原命令8 context errors，#12b wired8/8；历史 child finding，不冒充既有aggregate finding |
| Prior aggregate findings | N/A | 首次aggregate review，epoch1 |

### Findings

#### P1

N/A。未确认批准范围内的 mandatory 行为违例。

#### P2

- **V-1 — 非阻断文档计数错误。** `P/user1-mail-master.md:61` 记去重36；五份权威清单为10/7/13/3/5，合计38，扣除四个共享文件后为34。A1新增的 `AuthInterceptorTest.kt`、A6新增的 `AuthControllerMvcTest.kt` 早已列入01，不能再增加累计去重数。实际34个源码/测试路径全部恰好获批，无越界或漏实现。该错误不改变精确授权清单，不违反 mandatory 功能验收；未修改 governing plan，也不触发 repair。

#### Observations

- `mailbox-readonly.js:95-109` 对同 eventAt 的跨页消息保留“已有窗口在前、较早窗口在后”的稳定拼接顺序。fresh probe 输出 `[newer-id-2, older-id-1]`。已批准 reader 契约明确时间正序/去重，但未明确同时间戳的 reader tie-break；eventAt仍非降序，backend原 `(time,source_rank,id)` 排序未改。故不升级为 mandatory 缺陷。
- `mailboxChatStyle.test.js` 读取 plan markdown 及放宽 selector行解析，属于测试耐久性观察；现有产品CSS规则没有被改写。
- 整个 master-base diff 的 whitespace问题仅来自原样seed的规划源码摘录；当前产品/测试diff和index检查均干净。
- 全量 Maven、Calendar slice、原始 AuthFlow 的失败具有上述明确边界；保留在报告，不作为“测试全绿”的证据。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | 独立证据与裁定 |
|---|---|---|---|
| 01 verify O-1：dispatch brief空 | I-5/契约身份 | 非阻断，已核对 | 现存brief、child/master及10文件实际diff一致；历史调度问题未造成合同漂移 |
| 01 verify O-2：ConversationController名称 | I-1 | PASS | 真实类为 `MailboxConversationController`，policy绑定真实declaringClass+method+verb；#01/#09 |
| 01 verify O-3：无405断言 | I-1 | 非阻断 | 契约只说未知路由/不支持方法“可”404/405；未要求统一405；真实handler verb mismatch拒绝已验证。既有generic advice行为未改 |
| 01 verify O-4：Flyway no-op | I-2/I-5 | **本轮关闭证据缺口** | #02确为version166 no-op；#12b disposable空库实际应用165迁移至166，不能再仅依赖旧history行 |
| 01 verify O-5：mustChangePassword可能绕过reader gate | I-1/I-3 | **原观察事实不成立** | interceptor:33-50的豁免只跳过PASSWORD_CHANGE_REQUIRED拒绝，不提前return；仍继续:52 reader gate。change-password仍拒绝，AuthService也限定admin；无所述旁路 |
| 01 verify O-6：当时列表未过滤 | I-2/I-5 | 后续02已关闭 | aggregate repository共享EXISTS、45/23分页、空授权、详情撤销均fresh通过；#04/#12b |
| 02 verify O-1：基线“其他IT全绿”不实 | I-4/I-5 | 已纠正裁定 | fresh127/0F/7E/0S；Calendar整类及replied失败体与master base相同；不能继续声称只有1个组合错误 |
| 02 verify O-2：EXPLAIN alias emra | I-2 | PASS | SQL semijoin本身使用该alias；PK schema与共享EXISTS源路径存在；实际MySQL测试通过。不宣称测试断言了未记录的全部optimizer细节 |
| 03 verify O-1：无真实browser/viewport | I-3/I-5；S-1–3 | **PENDING human** | fresh DOM/CSS/行为机器证据通过；未产生真实app1440/390截图或keyboard acceptance |
| 03 verify O-2：style测试依赖plan/放宽selector | I-4；S-2 | 非阻断测试质量观察 | 真实CSS只追加批准S-2；旧CSS不变；完整JS1911/1911 |
| 04 verify O-1：身份切换未直接测试 | I-1/I-3 | **本轮机器诊断补足** | `/private/tmp/user1-review-grant-identity-probe.js` exit0；pending admin GET在身份变化后无效，晚响应无reader节点/额外grant请求 |
| 04 verify O-2：task-modal-runtime无?v | I-4/I-5 | 非阻断、基线不变 | index:2517的该脚本注册未改；批准要求限定“已有?v资源”，19个版本化资源一致 |
| 04 verify O-3：1440/390只是header副本probe | I-4；04 S-1 | **PENDING human** | 明确不把该副本当真实应用浏览器证据；本轮也未冒称完成真实布局验收 |
| 05 initial verify O-1 / handoff index O-1：5672 vs5645 | I-4/I-5 | 基线计数差异已确认 | fresh总数5672、0F/19E/24S；错误类别/根因与基线一致。新增用例数不构成错误增长 |
| 05 reverify O-1：execution仍列f73旧plan hash | I-5/修订身份 | 历史证据已注明校正 | 当前A8 child05为f91e0df5…；execution控制器附录已解释f73属258执行时版本。ledger A8与当前内容吻合 |
| 05 reverify O-2 / handoff index O-2：plain AuthFlow仍失败 | I-4/I-5 | 预期分列结果已fresh复核 | #12a exit1/8context errors；#12b exit0/8pass；源码无内联override。未把plain失败涂成PASS |

Source/index discrepancy 已保留：handoff 的05 O-1指“用例数差异”，最新 reverify 的05 O-1则指“旧plan hash”；两项分别审查，没有仅按相同编号合并丢弃。

### Evidence Boundaries

- 真实浏览器、受控发信/收信任务、生产配置与既有账号数据未执行人工验收；全部18组人工项保持 PENDING。
- 机器失败项及原始 exits/counts 全部列出。无跳过的 mandatory MySQL/AuthFlow被计作通过。
- WAR可构建并不证明生产环境启动/布局/任务验收完成。
- 第一轮 sandbox JS详细计数未保留；最终授权fresh完整重跑证据齐全，不影响当前机器判定。
- 未写 machine report、review ledger、manual evidence 或 repair artifact；控制器负责保存本报告。

### Next Action

**PASS → 由控制器持久化本报告，进入同一 code boundary 的人工验收。**

不进入 repair-p，不执行产品修改、merge、push 或 deployment。人工未 ACCEPT 前，不标记 READY_TO_INTEGRATE。

Repair planning: **N/A**。无 eligible blocking finding；默认 `docs/plans/fix/user1-mail-master/repair.md` 不存在，本轮未创建。

**No product code was modified.**
