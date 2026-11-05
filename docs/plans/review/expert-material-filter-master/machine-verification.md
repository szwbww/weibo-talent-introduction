# Aggregate Machine Verification — expert-material-filter-master

## Epoch 1 — 2026-11-05 (Asia/Shanghai)

- Master plan: docs/plans/2026-11-05/expert-material-filter-master.md
- Governing master identity: sha256 344158c4a2d30f05e7141544a7d4de64d3b33435752f322651ca861ec0f801fd; recorded same sha256
- Master identity state: CONSISTENT; amendments N/A
- Boundary: 71bb4879c77c9fcb69f1688e7192ad9aea5c0c4b..e73f902a06e45f42722e39682e20a8742ba9d388
- Reviewer: /root/aggregate_material_review; fresh fork_turns=none
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A

以下为独立审查员完整返回报告。

## Verification Result: PASS

- Phase：`aggregate/master`
- Reviewer：`/root/aggregate_material_review`；独立于全部已登记实现者、修复者、子计划验证者。
- Plan：[expert-material-filter-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-master.md)
- Governing / invoked / recorded SHA-256：`344158c4a2d30f05e7141544a7d4de64d3b33435752f322651ca861ec0f801fd`
- Master identity：`CONSISTENT`；amendments：`N/A`
- Implementation boundary：`71bb4879c77c9fcb69f1688e7192ad9aea5c0c4b..e73f902a06e45f42722e39682e20a8742ba9d388`
- Evidence HEAD：`faef84501e31a58ee3d033abe407a03eddd16025`
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master`
- Branch：`fast/expert-material-filter-master`
- Resolution：`DISCOVERED_FROM_GIT_WORKTREES`
- Convergence：`INITIAL`
- Prior aggregate report / findings / repair：`N/A`
- Manual acceptance：`PENDING`

### 身份与边界

重新计算的身份全部与输入一致：

| 工件 | SHA-256 |
|---|---|
| 子计划 01 | `96f08c602083fe57d1c20815a37589014499cdca6bc39553e4d77e960717278b` |
| 子计划 02 | `1d1792fabfafe46b3be0f5bc728d8b24c712eb0690a713d4f75a34140b5460e1` |
| 子计划 03 | `ef85056d991448e846a729ca2fdff8d8ab83869cfc645ee36181d5d72abca2ed` |
| Fast ledger | `84501580b47ae306d06ec7fe48a5377c31cdb6367b67642d4241d9aad0c9e32b` |
| Human handoff | `4b65f672ba2c6e1934d464944e07df23d9241974391f47f2aee6b128dac759ed` |

已读取主计划、全部子计划及每个子计划的 brief、execution、verify-log、fix-log。子报告仅作历史证据，未替代本轮验证。

新鲜 Git 检查：

- base 是 final_code_head 的祖先；final_code_head 是 HEAD 的祖先：均 exit `0`。
- 累计产品/测试变更精确匹配批准清单：`27` 文件，`13` 生产、`14` 测试；未授权文件 `[]`，遗漏文件 `[]`。
- `final_code_head..HEAD` 只有 fast-p 证据文档。
- 当前产品内容与 final_code_head 完全一致；index 空。
- 最终 HEAD、branch 未变；工作树仅存在 controller 创建的未跟踪审查目录。
- 产品累计 `git diff --check`：exit `0`。

### Commands

全部在上述 retained worktree 顺序执行，使用批准的 JDK 11。最终 Surefire XML 独立复核：`360` tests，`0` failures，`0` errors，`0` skipped。

| ID | 完整命令 | 结果与完成时间 |
|---|---|---|
| C1 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ExpertMaterialEligibilityTest,ExpertMaterialServiceTest,ExpertMaterialArchiveServiceTest,ExpertMaterialControllerHttpTest -DskipNodeTests=true` | PASS；exit `0`；`60/0/0/0`；2026-11-05 12:22:23 +08:00 |
| C2 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ExpertMaterialEligibilityReadIT -DmysqlIt=true -DskipNodeTests=true` | PASS；exit `0`；`2/0/0/0`；12:26:16 +08:00 |
| C3 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ImapMailReceiveServiceTest,AutoMailReplyServiceTest,MailAttachmentServiceTest,ManualExpertMaterialUploadFlowTest,BatchAutoMailReplyServiceTest -DskipNodeTests=true` | PASS；exit `0`；`197/0/0/0`；12:29:28 +08:00 |
| C4 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ImapMetadataFetchIT -DmysqlIt=true -DskipNodeTests=true` | PASS；exit `0`；`16/0/0/0`；12:34:12 +08:00 |
| C5 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=MailboxServiceTest,AutoReplyPreviewServiceTest,OperatorStatusReconcileServiceTest,OperatorStatusWriteSeamGuardTest -DskipNodeTests=true` | PASS；exit `0`；`83/0/0/0`；12:38:24 +08:00 |
| C6 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=MailboxSignatureProjectionIT -DmysqlIt=true -DskipNodeTests=true` | PASS；exit `0`；`2/0/0/0`；12:43:00 +08:00 |

计数格式：tests / failures / errors / skipped。

类级计数：

- C1：Eligibility `2`、MaterialService `39`、Archive `11`、HTTP `8`。
- C3：ImapReceive `38`、AutoMailReply `109`、MailAttachment `14`、ManualUpload `15`、BatchAutoReply `21`。
- C5：Mailbox `21`、Preview `22`、Reconcile `39`、WriteSeamGuard `1`。
- C2、C4、C6 的 XML 均确认 `mysqlIt=true`；全部 XML 的 `java.home` 为批准 JDK 路径。

首次 C1 默认沙箱尝试在 resources 阶段 exit `1`：无法写 retained worktree 的 `target/classes/application.yml`，`Operation not permitted`，未执行测试。获准升级沙箱后原命令完整重跑成功；后续命令同样使用授权升级。此环境限制已解决，无产品修复。

完整本轮日志：

[C1](/tmp/aggregate-material-review-1.log)、[C2](/tmp/aggregate-material-review-2.log)、[C3](/tmp/aggregate-material-review-3.log)、[C4](/tmp/aggregate-material-review-4.log)、[C5](/tmp/aggregate-material-review-5.log)、[C6](/tmp/aggregate-material-review-6.log)。

#### 实际集成环境回执

| 命令 | 实际运行证据 |
|---|---|
| C2 / MySQL 5.7 | `SELECT VERSION()=5.7.44`；隔离端口 `33533`；容器 `4fd1879a58bc1d3e4ff31ab75b6c27230c9e3cd80f913c856b28ed4014f295de` |
| C2 / MySQL 8.0 | `SELECT VERSION()=8.0.36`；隔离端口 `33534`；容器 `beddf6eeb6a11b85e8c52ec441e2603b2b71fafead926c357db861c87f07e895` |
| C4 | 真实本地 socket IMAP fixture；并非 MySQL；签名与 PDF 的附件字节 FETCH 均未发生 |
| C6 / MySQL 5.7 | 实际版本断言 `5.7.44`；隔离端口 `33540`；容器 `c32c0540ad944959f08548700b6553bf5bd8353144c17df95ce26490ac2e0c6f` |
| C6 / MySQL 8.0 | 实际版本断言 `8.0.36`；隔离端口 `33541`；容器 `e22fc0423aad327137dc82fbd45207872ff75a5c155c1b1bd2beff0785c92163` |

C2 两版本均输出：

```text
matrix=17, owners=3, transferStates=6+legacy, pagination=10+3, reconcile=1/0
```

C6 两版本均输出：

```text
proxy=true branches=4 signature=false pdf=true image=true empty=false
account/contact isolation=true pagination=4 total=10/12 readOnly=true
```

Docker 实际回执：`29.8.2`、OrbStack。SQL fixture 使用容器 JDBC URL、自管合成 schema、finally-stop；未连接默认开发库或生产库。C6 执行生产 Spring Data JDBC repository 代理，未用 SQL 字符串断言替代执行。

历史 baseline 的 focused `315` 与 IMAP `15` 仅用于基线说明。新增 SQL 类在 base 不存在；未声称其有基线通过记录。

### Contract Matrix

以下均为强制合同；人工项单列保持待验收。

| ID | 要求及计划来源 | Verdict | 新鲜证据 |
|---|---|---|---|
| M-01 | 唯一规则；ASCII 空格、Locale.ROOT、两种 MIME 或 `.p7s`；主 I-1、01 T1 | PASS | [ExpertMaterialEligibility.kt:6](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialEligibility.kt:6) 为无状态 object，固定错误消息与 alias `a` SQL const；C1/C2 同组 17 输入，包括 null、大小写、参数、重音、tab/newline、图片、`.p7m`。 |
| M-02 | 历史 5→2；分页前过滤；summary 全体有效材料；O-1、I-2、01 T2 | PASS | [ExpertMaterialService.kt:317](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:317) 在 resolve/filter/sort/page 前过滤；C1 验证 5→2、stored=2、metadataOnly=0；C1/C2 验证 25→13、两页10+3、搜索不改变 summary。 |
| M-03 | 三 owner、六 transfer 状态、legacy 无 transfer、审核保持；主 I-2 | PASS | [ExpertMaterialEligibilityReadIT.kt:129](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialEligibilityReadIT.kt:129) 的真实 SQL fixture；C2 两版本验证全部 owner/state、APPROVED 保持与零字节材料。 |
| M-04 | 会话 count 与材料页一致；COUNT 行语义、空集合不变；IP-1、01 T3 | PASS | [MailboxConversationRepository.kt:516](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:516) JOIN attachment 并使用共享谓词，保留 COUNT(*)/GROUP BY；C2 实际执行 count；调用者继续缺省补0。 |
| M-05 | 旧文档及联系人详情分别过滤；不丢 processing/manual；01 T3 | PASS | [ExpertDocumentBrowseService.kt:46](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:46)、[ExpertContactManagementService.kt:73](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:73)；独立加载 documents 的 attachment 元数据；C2 三 owner 读取、损坏关联明确失败。 |
| M-06 | 历史行/原件/审核/transfer 保留；GET 零业务 DML、零 IMAP；N-2、I-2 | PASS | 全部生产 diff 无历史删除或状态回写；C2/C6 持久快照不变；C1/C2 的无 transfer-service 交互及源码只读调用链；无 GET→reconcile/下载入队调用。 |
| M-07 | scoped/unscoped ID 类型拦截；权限优先、ready/path 保持；I-4、01 T2 | PASS | [ExpertMaterialService.kt:107](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:107) 保持 scoped doc/owner 校验后判类型，unscoped 查原始 attachment 后判类型，再进入原 ready/path 检验；C1 覆盖本地/缺失签名、越权优先及普通未就绪。 |
| M-08 | 混选整批拒绝、队列零部分操作、固定400消息；I-4、IP-2 | PASS | [ExpertMaterialService.kt:602](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:602) 全批 doc/contact→attachment 类型检查→countQueued/enqueue；C1 正反顺序均拒绝，零队列交互；HTTP JSON `BAD_REQUEST` 固定文案通过。 |
| M-09 | ZIP/preview/download/新AI提取无旁路、无部分输出；I-4、01 T4 | PASS | [ExpertMaterialArchiveService.kt:28](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialArchiveService.kt:28)、[DocumentTextExtractor.kt:31](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt:31) 均全批 resolve 后才读内容；C1 真实 resolver 正反混选拒绝、ZIP factory/consumer 未调用、原件未变；旧预览/下载与 mailbox 下载委托同一 guard。 |
| M-10 | raw direct→bridge→唯一 legacy 后过滤；无来源串出；I-5 | PASS | [ExpertMaterialService.kt:722](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:722) 公共包装器过滤原 resolver 的最终结果；C1 direct-only-signature 不查 bridge，bridge-only-signature 不查 legacy；普通 direct、legacy 歧义/账号/OUTBOUND 回归保持。 |
| M-11 | reconcile dryRun/apply 相同候选、FOR UPDATE/存在性检查保持；IP-3、01 I-5 | PASS | [ExpertMaterialService.kt:658](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:658)，共享谓词位于两 SQL 的887/900行；C2 两版本 dryRun 只含普通件、apply=1/0、历史签名行/document 保留。 |
| M-12 | MIME 签名在读流前排除；signed 容器正文/CV与partPath保留；I-3、02 T1 | PASS | [ImapMailReceiveService.kt:530](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt:530) 先递归 multipart，555行在叶注册/text fallback前判规则；C3 两模式 throwing-stream fixture、命名/无名/伪装text签名通过；C4 实际协议保留正文、PDF partPath=3、零签名/PDF bytes FETCH。 |
| M-13 | 单一业务有效集合覆盖直接/批量/关闭/人工/未匹配等分支；I-3、02 T2 | PASS | [AutoMailReplyService.kt:122](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:122) 合并 MIME+linked 后过滤、清空 linked、保留顺序/source/recovery；176行传统一 businessMail；C3 覆盖正常、全局/专家关闭、人工、未匹配、无首信、来源存疑、重复正文和UID。 |
| M-14 | 保存服务独立兜底；绑定不复活签名；有效材料缺 transfer 仍失败；I-3、IP-3 | PASS | [MailAttachmentService.kt:47](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:47) 两 save 入口先过滤；133/167/364行覆盖绑定、bridge、intent；C3 metadata/legacy×record/processing×已知/未知contact、零文件/仓储、副作用反例、普通件幂等及缺transfer失败通过。 |
| M-15 | 手动上传 normalize 后、落盘/事务前拒绝；图片/0B仍正常；02 T4、IP-5 | PASS | [ManualExpertMaterialUploadService.kt:89](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/document/service/ManualExpertMaterialUploadService.kt:89)；C3 两类签名400与固定消息、未读 throwing stream、零三表/目录/transfer；PNG/JPG/0B均201、字节/大小与PENDING_REVIEW保持；既有413/回滚/权限用例通过。 |
| M-16 | 收信事务、UID去重、游标、异常不确认、取消边界保持；N-3 | PASS | [AutoMailReplyService.kt:155](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:155) 原物理键、锁、事务、commit后ack路径未变；C3 重复UID零写入/SMTP、失败不markSeen、不跨失败UID推进游标及批量取消回归通过。 |
| M-17 | DMARC/退信/自检、Drive合并阶段及来源保持；N-3、IP-8 | PASS | [AutoMailReplyService.kt:1010](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1010) 机器信路由在 processSingle 前；C3 DMARC独立purpose/无attachment、Drive不提前交DMARC、来源顺序和recovery回归通过；C4协议回归通过。 |
| M-18 | 新签名不作为材料promotion/附件意图；正文分类不被强制改变；O-3、I-6 | PASS | C3 三条promotion分支均捕获count=0，UNKNOWN签名正文仍UNKNOWN；[AutomaticApplicationPromotionService.kt:40](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/AutomaticApplicationPromotionService.kt:40) 原count<=0直接返回；普通CV保存/意图仍有效。 |
| M-19 | 四处 SQL EXISTS 与任务实体共享规则；原范围/排序/分页保持；03 T1、IP-6 | PASS | [MailRecordRepository.kt:515](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:515) 及549/784/826行使用固定常量；[MailboxService.kt:207](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:207) 使用any；C5真值矩阵、C6两生产查询四分支、账号/contact范围、total10/12、排序/分页及只读快照通过。 |
| M-20 | preview只改附件忽略提示，不改分类/动作或发送；03 T2 | PASS | [AutoReplyPreviewService.kt:56](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:56) 仅谓词变化；C5同正文签名-only/混合/空集合为false/true/false，其余响应全等，无新增写入/SMTP/transfer依赖。 |
| M-21 | 对账仅报告；人工/邀请/终态优先级、DB/ES权威保持；I-6、03 T3、IP-7 | PASS | [OperatorStatusReconcileService.kt:70](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:70) 仅过滤事件输入；C5实际结果total=5、consistent=3、dbVsExpected=1、esVsDb=0、humanOverride=1，签名历史expected=REPLIED；仓储仅读、ES仅_search闭包通过。 |
| M-22 | 写入守卫只机械同步指定行号；03 T4 | PASS | [OperatorStatusWriteSeamGuardTest.kt:71](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt:71) diff仅MailboxService168→169、MailRecordRepository628→630/685→687及说明；path/context/ALLOWED_WRITE_SITES不变；C5守卫通过。 |
| M-23 | 正常DOCX/PDF、证书扫描图、命名图片、manual、未知类型保留；N-1 | PASS | C1/C2规则与真实SQL矩阵、C2旧读者三owner、C3普通附件与上传精确字节；新增规则不使用大小、image前缀、CID或image MIME作为排除条件。 |
| M-24 | 不历史降级、不删旧意图/AI结果、不改人工审核/材料目录；N-4 | PASS | 累计生产diff未触及状态writer、AI历史管理或campaign材料目录；C2审核/历史快照保持，C5对账只读；新AI请求在提取/LLM/历史删除前被guard拒绝。 |
| M-25 | 明确非目标：不加图片识别/其他扩展名黑名单/解密验签、出站或日历改造 | PASS | 共享规则仅两MIME与.p7s；完整27文件diff无上述实现；`.p7m`明确保留；底层原始repository语义不变。 |
| M-26 | 不取消已有队列、不删原件、不改schema/UI/DTO/依赖/配置，不新增缓存字段 | PASS | 授权清单比对精确相等；worker、transfer状态机、迁移、资源、DTO、pom/config均无变更；历史队列仍由原worker收敛，读取与新请求独立过滤。 |
| M-27 | 联合可见性：签名-only材料total/count/message附件为0，旧hasAttachment/preview为false；03联合合同 | PASS | C1/C2材料及count、C1 raw resolver、C5实体/preview、C6生产SQL；[MailboxConversationService.kt:350](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:350) 从同一过滤结果计算attachmentCount；历史MATERIALS_RECEIVED允许保留，C5报告差异。 |
| M-28 | 全部必需机器命令、真实MySQL双版本、真实IMAP，无默认库清理 | PASS | C1～C6全部新鲜执行，360/0/0/0；生产SQL/代理实际运行；未执行旧默认库RepositoryIT；测试隔离与资源finally关闭代码已审查。 |
| M-29 | 累计授权范围、无计划擅改 | PASS | 27文件与三个子计划/change-manifest精确一致；所有计划hash一致；无amendment、无额外产品文件。 |
| M-30 | 主A-1～A-3；01 A-1～A-5；02 A-1～A-7；03 A-1～A-4 | N/A：人工PENDING | 未部署、未进行live-mailbox/UI人工验收，未替人填写结果；不阻断本轮机器判定。 |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| N/A | N/A | 首次聚合审查；无既往 V-n；本轮无已证实强制合同违反或必需证据缺失。 |

### Findings

#### P1

N/A。

#### P2

N/A。

#### Observations

N/A。

### Fast-P RECORD_ONLY Re-evaluation

| 来源 | 主合同映射 | 结果 | 证据 |
|---|---|---|---|
| 子01当前verify-log | I-1/I-2/I-4/I-5、IP-1～IP-3 | N/A | RECORD_ONLY明确N/A；其合同由本轮C1/C2与源码重新核验。 |
| 子02verify-log | I-1/I-3/I-6、IP-3～IP-5/IP-8 | N/A | RECORD_ONLY明确N/A；本轮C3/C4重新核验。 |
| 子03verify-log | I-1/I-2/I-6、IP-6/IP-7 | N/A | RECORD_ONLY明确N/A；本轮C5/C6重新核验。 |
| Final handoff索引 | 全部主合同 | N/A | 索引无条目，与三个当前子报告一致。 |

子01 epoch1 的工作树登记暂停属于已解决前置条件；其后无未完成产品修复，不转为产品发现。未将缺失的强制证据以“既有问题”豁免。

### Evidence Boundaries

- 机器强制证据完整；未发现当前阻断。
- 验证范围为批准主合同及累计产品边界；未运行全仓测试、WAR打包或Node/frontend门禁。Node按六条批准命令显式关闭；JUnit跳过数为0。
- SQL IT使用缩减的隔离合成schema与生产查询；未声称执行全套Flyway迁移或线上数据验收。
- 未访问生产数据库、真实邮箱或外发真实邮件；未声明部署/发布效果。
- retained中的ignored固定PDF离线脚本与source-manifest SHA-256 `450ba2fd6c6d55e72172f81c12f67e7120c9d1043d2ce9c18ca9e9e4f7493102`一致。三输入与MIME均PDF，无本次规则排除项；未执行、未改写。
- Post-repair evidence mode / executor / approval：`N/A`，本轮不是修复后审查。
- 未写聚合报告目标；由controller持久化本完整输出。

### Repair planning

`N/A`：机器结果PASS，不调用repair-p。

未创建或修改默认修复工件：

`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/fix/expert-material-filter-master/repair.md`

### Next Action

对代码 `e73f902a06e45f42722e39682e20a8742ba9d388` 执行并记录待完成人工验收；人工接受前不标记READY_TO_INTEGRATE。

No product code was modified. 未修改测试、计划、审查证据、index、HEAD或branch；未stage、commit。
