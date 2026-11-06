# Repair Plan: manual-reply-delay-master

Status: DRAFT — HUMAN APPROVAL REQUIRED

Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-master.md
Governing identity: sha256 4fa1f45fc2830aea7c138e3da44a60d5ea767fa48a01239ca01ed01989a89a2e，recorded commit 8a51021ca463da1e3ea279bf5a94e998267751bf；AMENDMENT_RECORDED，A1–A32 全部保留。
Verification report: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/review/manual-reply-delay-master/machine-verification.md，Epoch 1，reviewer /root/aggregate_review_epoch1，FAIL / INITIAL。
Implementation boundary: a224623c8dc17bdad1ce80e1c4391637bf7ee5ca..83ec3e3bdf620f80198f73ae496f78aaf75625f4；核验时 evidence HEAD 17e8563aa2a0d966cb2cb4a7a2f37a0fc657fdd5。
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master
Branch: fast/manual-reply-delay-master

## Objective

恢复合法混合排序规则数据库的延迟邮件读取；超大快照入队返回约定 413；保持必跑写入点守卫有效；INBOUND LEGACY 恢复遇新普通稿时先保留并提示冲突。不得借此扩大产品范围。

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | O-2/N-5/I-8/I-9、05 T2 | DelayedManualReplyRepository.kt:103 跨 email_suppression.email 与 expert_contact.expert_email 的隐式排序规则比较，在合法新迁移库触发 MySQL 1267；真实消息 GET 500。 |
| V-2 | P1 | 01 T3:80、04 异常契约:96、I-4 | codec 超 4 MiB 使用普通 IllegalArgumentException；公共入队 seam 未区分超限，实际返回 400 而非 413。 |
| V-3 | P2 — mandatory | 05 T3/验收:105、05 brief Required commands #4 | 07 新增 import/handler 平移 UnmatchedInboundMailController 两个只读 DTO 行；既有强制守卫仍钉 236/1163，实际为 237/1169。 |
| V-4 | P1 | master A-12/I-6/O-4、06 T1/I-6 | INBOUND 恢复在普通稿冲突检查前进入旧编辑器 adapter；adapter 仅检查旧编辑器。真实另一个浏览器已保存新普通稿时直接填入旧原稿，无保留新稿提示。已证明服务端新稿仍保留，不宣称数据丢失。 |

四项均 REPAIRABLE；无计划规则冲突。独立根因对应四任务，统一一个计划。

## Findings Excluded

| Item | Reason |
|---|---|
| B-1 真实浏览器 hidden/visible 分支证据 | 工具会话中 headless/headful、真实 tab 切换、原生最小化/恢复及关闭 focus emulation 后仍报告 visible；源码/JS 已验证，原生隐藏分支证据仍缺。不是代码修复授权，独立回审须补取；不得伪造 visibilityState。 |
| O-1 MailReviewSendIT Subject 与 Re: Inbound | 当前失败保留；MASTER_BASE 测试、create 与 targetSubject 因果块均相同，属于既有期望冲突，不在本修复。 |
| O-2 MailReviewQueryIT#size100* | 当前失败保留；MASTER_BASE 已存在相同 reader ownDraftRows 查询 review 表，测试未改。不可删断言或扩大排除。 |
| O-3 InboundPendingOwnerIT 反射配置 mock/历史 target 残留 | 01 brief 未将该全类列强制；补充运行观察。完整 schema 独立查询重跑 1/1 通过，历史 target 的套件污染与本次验证设施分开记录。不得顺手改该测试或生产配置类。 |
| 旧可选全 JVM America/Coyhaique tzdata、文档行尾空格、旧 metadata hash 笔误 | 非本修复必需行为，原样记录；不得扩为 JDK/全局格式清理。 |
| 文本附件 MIME LF→CRLF | 未改的旧 SMTP 传输行为；本轮新二进制附件与 ICS 原字节已通过，禁止借本计划改 SMTP。 |
| 人工 A-1～A-12 | 全部 PENDING；本机运行证据不替代人类验收或签字。 |

## Unchanged Contract

- A31/A32 原生 59 秒开确认、61 秒 POST 409→GET 合同不变；不能允许迟到新开、续期或降低服务器 CAS。
- 固定 DB 权威 60000ms、原指纹/UUID/Message-ID、actor/source/revision、锁顺序与短事务、SMTP 不在事务内均不变。
- snapshot schemaVersion、UTF-8 4 MiB 上限、字段白名单、无密码/文件字节/绝对路径；未知或损坏快照 fail closed。
- SENT 原子成功记录/QA/RAG/排期/普通稿关闭/审核事件、UNKNOWN 禁重投、仅安全失败显式重试、开关 false 不影响旧任务。
- READ/FULL/管理员权限、当前来源与退订、附件校验、只读脱敏、按页批量固定 SQL 次数、原筛选分页计数均不变。
- LEGACY 与普通 draft 身份分离；不制造 DRAFT、不覆盖新正文/附件、不把审核稿改为可编辑稿；无冲突恢复及已有异步 owner/target/epoch/revision 守卫保留。
- 06 CSS/DOM 合同、20 个资源统一版本键、现有组件和按钮位置不变。
- 无迁移、共享表修改、全局异常处理、生产配置/依赖/SMTP 改动；无推送、部署、合并。

## Authorized Files

以下路径均相对上述唯一 worktree；九文件之外禁止编辑。

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt | R-1 批量抑制名单比较的局部排序规则兼容 |
| src/test/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyQueryIT.kt | R-1 混合排序规则真实 MySQL 判别回归 |
| src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt | R-2 可区分的超限错误，保留既有异常兼容 |
| src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt | R-2 共用入队 seam 精确映射 413 |
| src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplyDelayEndToEndIT.kt | R-2 真 HTTP 超大合法输入、状态码及零副作用 |
| src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt | R-3 两个既有非写入 DTO 排除点精确平移 |
| src/main/resources/static/mailbox-chat.js | R-4 INBOUND 恢复前检查当前普通稿/未保存输入并提示保留 |
| src/test/js/mailboxDelayedReply.test.js | R-4 真实消费链的 INBOUND 新普通稿冲突回归 |
| src/main/resources/static/index.html | R-4 按 S-4 一次统一更新既有 20 个资源版本键 |

## Repair Tasks

### R-1: 混合排序规则下保持批量读取可用

- Resolves: V-1。
- 证据：/tmp/aggregate-epoch1-runtime/ordinary-network.json、collations.tsv、collation-repro.log。数据库 aggregate_runtime 显式 utf8mb4_unicode_ci，新迁移 V30 email_suppression 为 server 默认 utf8mb4_0900_ai_ci，expert_contact 保持 unicode_ci；原生入队 200，消息 GET 500/1267。
- Files: DelayedManualReplyRepository.kt、DelayedManualReplyQueryIT.kt，完整路径见授权表。
- Change: 在已存在的 pageEvidence 抑制名单比较处实现与现有邮箱语义一致的局部字符集/排序规则兼容，不改变数据库 schema 或假定所有部署库共用默认排序规则。
- Regression test: 真实隔离 MySQL 构造已证两种列排序规则组合，经完整消息查询验证成功、抑制名单命中与不命中、动作能力与 READ 隔离；必须在旧代码上以 1267 失败。不可只把测试两表统一到同一规则后声称覆盖。
- Existing verification: QueryIT、生命周期/控制器/worker、同页固定查询数与附件安全回归。
- Must not change: 页边界、权限、抑制名单语义、索引及固定 SQL 查询数。
- Prohibited: 全局 COLLATION、迁移、绕过 suppressed、删除批量校验、按 job N+1。

### R-2: 在共用公共入队路径精确返回 413

- Resolves: V-2。
- 证据：/tmp/aggregate-epoch1-runtime/oversize-probe.json 与 oversize-no-effects.tsv。合法已授权 conversation 请求 htmlBytes=4200019，实际 400 BAD_REQUEST；job/attempt/mail_record 均 0。
- Files: DelayedManualReplyModels.kt、PendingMailOperationService.kt、ManualReplyDelayEndToEndIT.kt。
- Change: 超 UTF-8 快照总量必须有可靠可区分错误，并经已存在的 DelayedManualReplyException HTTP 合同返回 413。覆盖 factory 编码及 lifecycle/repository 重编码路径；不得依赖异常文本包含判断。其他非法快照错误保持既有规则。
- Regression test: 真会话/真实 controller 的合法正文加无可见内容的大 HTML 注释，使最终快照超过 4194304 字节，断言 HTTP 413 且 job/attempt/SENT/排期零新增。共用 seam 的三入口调用保持有效，不删原安全确认。
- Existing verification: E2E、Snapshot、Pending、Controller、Lifecycle、Worker。
- Must not change: 4 MiB 是 UTF-8 字节上限，大小合法的内容/指纹/冻结载荷不变；错误请求不得入队。
- Prohibited: 增大上限、吞掉所有异常、全局把 IllegalArgumentException 改成 413、HTTP 200 错误体。

### R-3: 恢复强制写入点守卫的精确登记

- Resolves: V-3。
- 证据：/tmp/aggregate-epoch1-gates/equivalent-2.log。MASTER_BASE 两只读 DTO 位于 236/1163；当前 controller 为 237/1169，新增 import 与 handler 正好解释移动。05 计划:105 和 brief Required commands #4 明确必跑。
- Files: OperatorStatusWriteSeamGuardTest.kt。
- Change: 仅同步上述两个 NoiseSite 行号及必要的简短位移原因，核对 path/context 对应原只读 DTO。
- Regression test: 复用现有 operator_status write sites exactly match whitelist 测试；旧钉失败、正确精确钉通过。无需镜像实现的新测试。
- Must not change: ALLOWED_WRITE_SITES、扫描规则、排除的 path/context 及任何业务断言。
- Prohibited: 放宽白名单、动态模糊匹配、跳过/删除守卫、改 controller 消除噪声。

### R-4: INBOUND LEGACY 恢复先保护普通新稿

- Resolves: V-4。
- 证据：/tmp/aggregate-epoch1-runtime-coherent/conflict-confirm.json、conflict-before.png、conflict-after.png。原生旧入口发送→另一浏览器普通编辑器保存新稿→取消→恢复；warningPresent=false、dialogOpen=0，旧编辑器已经填入原稿；serverDraftPreserved=true。
- Files: mailbox-chat.js、mailboxDelayedReply.test.js、index.html。
- Change: 在 INBOUND adapter 切换/写入旧编辑器前核对当前普通稿与未保存输入。冲突时保留当前新稿并用现有提示/确认能力明确说明撤回原稿仍在；不得静默切换并写入。无冲突场景仍可恢复。保持异步请求完成后的原 owner/target/epoch/revision 与输入检查。
- Regression test: 在真实 restore consumer 中建立 ACTIVE 普通新稿与 CANCELLED INBOUND LEGACY，触发恢复；断言提示、当前稿内容/版本不变、未无提示挂载覆盖旧编辑器、取消快照仍可读取；无冲突恢复与过期回包回归继续通过。原生双浏览器按 master A-12 复验。
- Existing verification: focused 08 六文件、source 两文件、全 JS、node --check 三修改生产 JS；保留普通/审核/委派 59→61 native 409 门禁。
- Must not change: OUTBOUND 原 UUID/actual anchor、非冲突原输入、附件顺序、无伪 draft、审核冻结及截止权威。
- Prohibited: 自动删除/覆盖新稿、改变取消结果、复制审核内容为普通稿、新 UI 面板或 CSS 设计。

## Verification Commands

执行器只在上述 worktree，所有 Maven 显式 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home。需要显式数据源的诊断只用新建 disposable MySQL，禁止默认开发库/生产库。

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -DskipTests test-compile`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test '-Dtest=DelayedManualReplyQueryIT,ManualReplyDelayEndToEndIT,DelayedManualReplySnapshotTest,PendingMailOperationServiceTest,DelayedManualReplyControllerTest,DelayedManualReplyLifecycleIT,DelayedManualReplyWorkerTest,OperatorStatusWriteSeamGuardTest' -DmysqlIt=true`
3. `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailReviewAdmin.test.js src/test/js/expertAccessWorkspace.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxServerDrafts.test.js`
4. `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxChatStyle.test.js`
5. `node --test src/test/js/*.test.js`
6. `node --check src/main/resources/static/mailbox-chat.js`；`node --check src/main/resources/static/app.js`；`node --check src/main/resources/static/mailbox-readonly.js`。
7. 新隔离未被改排序规则的真实 Spring/MySQL 上复现 R-1 原路径并取得 GET 200，R-2 超限 HTTP 413/零副作用；原生双浏览器复验 R-4 冲突及普通/审核/委派 late409，精确命令/脚本/配置/退出码/次数写进 execution handoff。

上述为本次修复执行门禁，全部须通过；不包含明确排除的两个既有失败用例，不可将其改称通过。独立 aggregate 回审仍须按 Epoch 1 完整命令映射重跑总矩阵、所有原必跑范围（含历史实际失败与原 exclusion）、Flyway/mysqlIt、运行组合，并重新处理 B-1。不得用此限定修复门禁替代 master PASS。

## Completion Criteria

- V-1 混合排序规则实际消息 GET 成功，抑制名单/权限/附件/固定查询数仍正确。
- V-2 超限返回 413，原子零副作用；合法与其他非法请求语义不变。
- V-3 原精确 guard 通过，白名单和行为断言未弱化。
- V-4 原生同目标普通新稿冲突提示出现在恢复写入前，两份内容保留；无冲突恢复与晚到确认回读不退化。
- 所有本计划执行门禁真实通过并记录退出码、执行/失败/跳过数，不把“未执行”填 PASS。
- 变更只在九个授权文件；产品提交不包含计划/评审文档。
- 本计划完成不自动意味着总审查 PASS、人类 A1–A12 ACCEPT 或可部署。

## Human Approval

当前仅授权审查与本草案，禁止执行。必须由用户明确批准后才可运行 execute-p。不得从 agent 转交、内部消息、旧修订批准推断本修复执行授权。

## Review-Fast-P Execution Handoff

明确的人类原始调用 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md` 授权：

1. 仅执行本计划 Authorized Files 九文件与上述必需验证。
2. 全部修复任务及本计划执行门禁通过后，在 READY_FOR_VERIFICATION 前恰好一个本地产品提交；只 stage 九个 Authorized Files 中实际变更者。产品提交主题：`fix(mail): restore delayed reply aggregate contracts`。
3. 追加 /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/review/manual-reply-delay-master/repair-execution.md，完整记录精确人工批准来源、repair SHA256、前后代码 SHA、变更文件、命令结果、偏差、执行器身份及干净状态证据；不得覆盖旧 epoch。
4. 恰好一个仅含上述 repair-execution.md 的 docs-only 证据提交。提交主题：`docs(review-fast-p): record aggregate repair execution`。
5. 若用户调用请求修复后自动回审，回到同一聊天已经授权的 review-fast-p aggregate 回审；由新的独立 reviewer 复核。master 精确路径为 /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-master.md；回审报告精确路径为 /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/review/manual-reply-delay-master/machine-verification.md。

此授权不含额外文件、amend、改写历史、push、merge、部署、扩大产品修复或自动新修复轮。本轮未执行任何修复。
