# Repair Plan: paper-reprocess-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-master.md
Governing identity: sha256 85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e; recorded commit 53c2b0018e239e3c30db25942e99c2cddc69e9d4; AMENDMENT_RECORDED; A1–A13 remain governing.
Approved amendments: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fast/paper-reprocess-master/ledger.md (sha256 dc2d86b0f225fbcec9f86a2bfa5f089cc3075fe541881dd5a8ceb88689274e09).
Verification report: Epoch 3, independent reviewer /root/paper_reprocess_aggregate_epoch3, 2026-11-03; FAIL / PROGRESSING. Complete report returned to the controller for append-only persistence at /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/machine-verification.md. At drafting time that file still contains the immutable prior epochs (sha256 570fd89fa1e2e3bd05ee25c561531c45cb29c1b0539c9adc64c966bc47b26442); do not misrepresent those bytes as the new report.
Implementation boundary reviewed: 3a425461dbe6088e4d4dda1a53141416a392670c..7e131474f403d9b28178148909180ac8f1d7d1bb
Evidence HEAD at review: 30b2b46bbf925673a5463990ad1474c16da84c5b
Required pre-repair product baseline: 7e131474f403d9b28178148909180ac8f1d7d1bb
Selected worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
Selected branch: fast/paper-reprocess-master

## Objective

真实 run 对照始终校验实际目标身份，候选字段不能把 run 冒充 flat replay；提交期间焦点落到 BODY 时，Escape 仍只关闭本组件弹窗，保留任务宿主与后台任务。

## Repair Lineage and Convergence

- Epoch1旧稿 sha256 47b6bccdb128dd02177fe0d629e251afe781064d75dcbc5f18c07ab24a829110 保留于 Git 3ab2e86c80993929350d1dc40c859fed1a24adbc 的同一路径。第一次产品修复 e213acb50791fe39a0de0a4d9d599bc4e2398cc1→291fb34b35be496673e28f124d443b9c46ba66f4 不撤销。
- 上一稿 sha256 ca1e7a97965282cd3bd91354d5cbf7cd3e1e3aa8a39ef2bd77a4dbed8681a3aa 的确切内容保留于 Git 30b2b46bbf925673a5463990ad1474c16da84c5b 的同一路径。其人类明确 execute-p 批准、PaperRepairExecutor2 及291fb34→7e131474执行回执保留在 /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/repair-execution.md；不复用为本稿实施批准。
- Epoch2 FAIL / DIVERGING 及随后人类“确认 继续”的不同方法规划裁决保持原样。本轮是在已批准且已提交7e131474后的独立完整复验，不改写旧结论。
- Epoch3 PROGRESSING：V-5 的真实 child 同步404已解决，普通 run 及合法 revision0 均通过；V-3 普通错误/缺失邮箱已拒绝，剩余根因明确收窄为输入来源判别。V-2 新 BODY焦点反例证明旧弹窗修复不完整；相关JS/app在291fb34→7e131474完全未变，不冒称为最新产品提交引入的回归。此判断依据根因与行为变化，不按发现数量或重复绿测判断进展。
- 此路径是唯一当前修复稿。保留全部现有修复，以7e131474为产品基线；HEAD可包含后来docs-only证据提交，任何额外产品漂移须先停止核对。

## Findings in Scope

| Finding | Severity / state | Requirement | Proven root cause |
|---|---|---|---|
| V-3 | P1 / PERSISTENT / REPAIRABLE | Master I-1/I-7/X-0；00-I-1；06-I-1/I-2；上一稿R-1 | reconcile_paper_identity_replay.py:132展开candidate的status；:65–74据字段存在先走flat-replay分支，在:80–88实际邮箱/docId校验前返回BOUND。真实固定record31372，候选添加status=ALREADY_RECOVERED后，wrong/missing/null实际target.email均为BOUND/EXPECTED_MATCH。 |
| V-2 | P1 / PERSISTENT（重新打开，不新增ID） / REPAIRABLE | Master I-9/X-9；05-I-2/I-4；S-6；历史R-2 | reprocess JS:188禁用当前提交按钮使真实Chrome焦点自然落到BODY；:312仅dialog冒泡keydown，无法接住BODY事件；实际app.js:20352的宿主handler被调用。启用按钮时Escape正常，提交中hostCloses从0变1、confirm关闭、cancelPOST仍0。 |

原始诊断仅在 /tmp/paper-aggregate-epoch3-zztjjl1a：identity-probe-results.json（七项正常地址校验）、identity-status-probe-results.json（三项来源混淆）、audit-evidence.json、audit-pending-escape.png、c14-contract-checks.json。输入/CSV逐项保留；不将这些部分run反例当生产效果报告。

## Findings Excluded

| Item | Preserved decision |
|---|---|
| V-1 | RESOLVED；保留同父ordinary/child显式取消后retry、旧epoch/收据/已提交业务及不重复副作用回归；不重新实施取消机制。 |
| V-4 | RESOLVED；summary.targetTotalKnown和完成文案保留；不改DTO、SQL汇总或前端业务计数。 |
| V-5 | RESOLVED；普通epoch0/真实child八步及ERROR→PROJECTED、revision1/0、providers19不变、错归属404已有新鲜证明；严禁修改Recheck/Query/Repository guard或投影实现。 |
| V-6 / V-7 | 非阻断P2，仍排除：503 pending模式锁；完成后preview.activeRun陈旧需reload。不是本稿任务。 |
| O-E2-01 / 历史rapid-tick trace重复 | 旧失败与根因未定记录保留；本轮C3/C10通过不能证明通用并发已修复。不修改Worker/Trace。 |
| 编号References、旧私有294 corpus、generic summaryRule=null、gain标签/报告行数/旧截图 | 原逐项RECORD_ONLY判断及证据局限保留；不扩parser、catalog、文档清理或其他测试。 |
| Production30711 ENFORCE、live ES/ORCID/Apollo、人工验收/发布 | NOT_RUN/PENDING；不把隔离机测当作生产效果或人类签字。 |

## Unchanged Contract

- Master I-1..I-9、M-1..M-5、X-0..X-9、全部child强制验收、S-1..S-6和A1–A13不变。无计划修订、规则放宽或新功能。
- 原337组合、333历史分组、325历史机会及47未确认负例不改；原PDF/metadata/first、冻结manifest/baseline/runtime与历史报告不覆盖。
- AUDIT零业务/零外呼、原资格/邮箱/人物保护、Apollo已保存免费/付费与冻结策略、父许可、唯一consume/CAS/Commit/project及旧CLI只prepare语义保持。
- CSS/DOM逐字、API/DTO/数据库/mapping/邮件/游标/下载范围/并发保持；不复制八步或改宿主全局键盘处理。
- 本轮只规划当前repair.md，不实施、不stage/commit、不写review证据。下一次执行需针对本稿现有内容的人类明确授权。

## Authorized Files

| Exact file | Purpose |
|---|---|
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py | 明确实际输入来源后验证run或flat证据；不由candidate字段选择分支。 |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt | 在既有CLI helper中加入来源混淆区分性回归；保留地址/doc/outcome/duplicate/flat全部控制。 |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-paper-reprocess.js | BODY来源Escape仍受当前本组件dialog边界保护；生命周期清理和native关闭保留。 |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/discoveryPaperReprocess.test.js | 既有真实app handler组装测试加入pending/BODY路径及无关Escape控制。 |

仅四个既有产品/测试文件、两个子系统。app.js、共享DOM测试库、其他JS/测试、Recheck/Query/Repository、迁移、报告与基线均不获授权。临时fixture/截图/回执只用新/tmp及正常target；若确证必须扩大文件，先停并提交确切必要性。

## Repair Tasks

### R-1: 从CLI的真实输入来源决定证据协议

- Resolves: V-3；一个来源判别根因。
- Files: Authorized Files前两项。
- Change: evidence/classify必须知道当前行来自真正的flat --new-replay还是run flattening。这个来源由已选CLI处理路径确定，不由用户candidate的status、reason或类似字段决定。保留原CLI优先级/接口，不新增CLI开关或改变冻结manifest。对每条run实际行，无论候选是否带status=ALREADY_RECOVERED或其他flat字段，都执行现有run outcome、实际target.email规范化指纹、docId及duplicate/conflict验证。无效实际行继续保留并输出INVALID/REGRESSION，不过滤为ABSENT/EXPECTED_MISSING，也不改成flat成功。保留现有external change独立记录与SOURCE_MISSING语义。
- Discriminating regression: 复用现有临时manifest/runExport/reconcile helper；有效控制、wrong-email、missing/null-email保持同一合法candidate键和预期docId。对wrong/missing反例在candidate添加status=ALREADY_RECOVERED（并保留有效flat replay无targetEmail控制）。未修当前代码应重现BOUND/EXPECTED_MATCH，修复后实际行必须INVALID/REGRESSION；正常run仍BOUND/EXPECTED_MATCH，合法flat仍成功。只增加这一根因的最小组，不新增Git fixture文件或广泛字段fuzz。
- Existing verification: Commands1/3/8/12/13及现有wrong-doc、duplicate、unknown/missing-outcome、SKIPPED、conflicting replay、honest absence、大小写/空白控制全部保留。
- Must not change: 地址trim/lowercase/UTF-8 SHA256 prefix24、已有稳定键、正确绑定增量、业务/准入计数隔离、flat receipt完整性/status检查、零网络/业务服务写。
- Prohibited: 只再补status/outcome否定名单，信任candidate自报来源，丢无效行、改原文/manifest/审计/报告凑结果、扩姓名/邮箱猜测、改变Kotlin生产parser/tool。
- Completion: 来源字段不能绕过任何现有run身份门禁；真实record31372同组反例及既有CLI回归通过，337原文重放/逐条归并不回归。

### R-2: 弹窗的Escape边界覆盖提交中BODY焦点

- Resolves: V-2；一个事件目标/传播阶段根因。
- Files: Authorized Files后两项。
- Change: 在本组件现有mount/lifecycle内处理当前confirm/detail弹窗拥有的Escape，即使提交禁用按钮后的事件target为BODY，也必须在宿主handler执行前保持dialog-only语义。保留native默认关闭和可用焦点归还；触发按钮暂disabled时不得为聚焦而允许重复提交，仍保留宿主内可访问上下文。不以preventDefault让Escape失效；不改app.js、不重建对话框、不增加CSS/DOM或全局长期状态。两个弹窗都未打开或组件dispose后，无关Escape仍交给原宿主。suspend/dispose不留会拦截无关事件的处理器。
- Discriminating regression: 扩展同一测试文件内既有真实app Escape handler组装场景。create Promise保持pending，确认按钮禁用，按实测自然BODY焦点作为前置发Escape；须关闭confirm且hostCloses=0、cancelPOST0，原source上下文保留、可用焦点不离开宿主语境；请求随后完成仍按原UUID/run恢复，不重发业务或换key。保留正常confirm/detail Escape及两dialog关闭/组件dispose后原host Escape会执行一次的对照。若轻量DOM需表示document/capture，只在本测试文件内最小补足相关事件模型；不得改共享测试库或让测试把BODY事件伪装成dialog事件。
- Existing verification: Commands2/7/14/17，原解析/下载/删除/保护/只读reader及8步接缝回归保留。
- Must not change: 关闭/原生Escape不POST取消，不停止后台任务；原请求pending key/模式与认证、轮询、分页、统计、正常焦点恢复和无关宿主键盘行为。
- Prohibited: 只给dialog再加冒泡监听；强制把真实BODY事件改写成dialog target；删app真实handler测试；修改全局app guard、CSS/DOM或以页面刷新绕过；顺带修V-6/V-7。
- Completion: 真实Chrome延迟实际create响应时，activeElement=BODY、startDisabled=true、confirmOpen=true后Escape仍hostCloses0/cancelPOST0；启用与pending路径均通过，后台同一个run继续，正常外部Escape不被吞。

## Verification Commands

未来明确批准后执行及独立复验均在/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master运行。JDK11与Maven路径如下；环境/源码不变的已通过门禁不无依据重跑。先用最小新增回归证明当前两个残留，再修复；最后在最终代码状态完整执行本节。每条保留完整日志、真实exit、JVM/JS counts/skips和首次失败；不得靠重复跑到绿掩盖未知根因。Maven/target操作严格串行，每进程有限watchdog（本轮使用1200s）。

准备全新目录：`REPAIR_TMP="$(mktemp -d /tmp/paper-reprocess-master-source-escape.XXXXXX)"`。这是可执行初始化，不是占位符。所有新输出必须不存在，不覆盖旧/tmp证据。

1. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true`
2. `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js`
3. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,SourceAuthorEmailResolverTest,DiscoveryPaperLayoutRegressionTest,DiscoveryPaperAnalysisServiceTest,RetainedPaperIdentityServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest,TaskExecutionSummaryExtractorTest,DiscoveryPaperReprocessApplyServiceTest,DiscoveryPaperBackfillServiceTest,DiscoveryRecheckServiceTest,DiscoveryVerificationIntegrationTest -DmysqlIt=true -DpaperReprocessIt=true`
4. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true`
5. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`
6. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn package -DskipTests`
7. `node --test src/test/js/*.test.js`
8. `python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py`
9. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.Paper03RuntimeSmoke'`
10. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --paper-reprocess-smoke'`
11. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke'`
12. `/usr/bin/time -l /Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json "$REPAIR_TMP/replay.json"`
13. `python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json --old-replay /tmp/paper-reprocess-30711-baseline.json --new-replay "$REPAIR_TMP/replay.json" --audit-directory docs/audits/2026-11-01-discovery-30711-pdf --csv "$REPAIR_TMP/reconciled.csv" --report "$REPAIR_TMP/reconciled.md" --runtime-evidence /tmp/paper-reprocess-30711-runtime.json`
14. 新鲜隔离真实Chrome/API/MySQL：121篇跨页、运行中第二tab重入同run、空/运行/实际HTTP500/完成；已保存Apollo配置由真实SQL-backed设置接口读取并与run冻结值对照，fixture属性/effective状态明确披露。confirm正常焦点和提交中BODY焦点分别按native Escape；pending可在隔离服务端将一次真实create请求延迟3秒，须记录activeElement=BODY/startDisabled=true/confirmOpen=true、hostCloses0/cancelPOST0、后续后台同run。detail Escape/焦点、summary.targetTotalKnown、只读source reader/analysisPOST0及NEW ordinary、REPAIRED child实际8步均复核。ordinary与child都注入候选ERROR并经原按钮/公开HTTP仅投影恢复PROJECTED；保留run/item/epoch和revision（包括合法0）、前后准入SQL和provider请求/RAW写计数不变；错归属和无归属ordinary拒绝。S-1..S-6精确CSS/DOM及1440/390、100%/125%等效CSS viewport各自截图；不以不变100vw上的CSS zoom冒称原生125%。确认截图可见真实范围/设置，完成数字截图须等实际summary与明细已加载，不把selectRun临时“—”帧作为最终数字证据。截图/HTTP/DOM只落新REPAIR_TMP；机测必须断言合同结果，浏览器进程exit0不能掩盖hostCloses1。fixture/provider/ES替身明确披露，不替代生产或人工验收。
15. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperReprocessRuntimeScenarios -DpaperReprocessIt=true -DmysqlIt=true -DtrimStackTrace=false -Dstyle.color=never`
16. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --retained-identity-smoke'`
17. `node --check src/main/resources/static/discovery-paper-reprocess.js`

Commands1–13逐条保留上一稿实际可执行命令，不减少十五类JVM、独立E2E、真实Flyway空库+V170升级、WAR/全JS、三个独立runtime及337原始字节重放/归并。15/16/17补齐既有child03 brief:139、child02 brief:129–133和child05语法门禁，不是新产品要求；此前只执行别的runtime分支不能代替它们。合法唯一旧私有294 corpus skip需披露，其他必需NOT_RUN不能算PASS。package只证明JS/WAR，不冒称Kotlin已测。

冻结输入sha256：manifest de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c；/tmp/paper-reprocess-30711-baseline.json e0fe5b1e008fda6188315f73972f72af79b3897b6ca59f1a4f04c8d452b3d91c；/tmp/paper-reprocess-30711-runtime.json 5d00ffbc736f653fe64b93b5e667c8baf776aa33b1320716d212291b45e037c3。原文只读 /Users/lukai/.local/share/paper-reprocess-30711；执行前后核对，禁止写入/入Git或更换样本。

Epoch3已有全部新鲜回执保留在/tmp/paper-aggregate-epoch3-zztjjl1a；它们证明当前7e131474，不替代未来修复后完整复验。旧epoch1首次paperBatchGroups失败、epoch2 C10 Trace lease lost/C10R通过及所有根因未知记录均保留；本轮通过不宣称那些通用问题已修复。

## Completion Criteria

- V-3：实际run的candidate.status不能改变证据来源或绕过实际邮箱/doc/outcome/duplicate校验；无效行INVALID/REGRESSION，正常run、flat和honest absence保持。
- V-2：pending真实BODY焦点的native Escape只关本组件dialog、hostCloses0/cancelPOST0，正常详情焦点归还、同源/同UUID/同run保留；正常及无关Escape/lifecycle均不回归。
- V-1/V-4/V-5及全部既有业务/权限/取消/历史/投影回归保留，不重新实施已解决行为；V-6/V-7及观察不成为任务。
- 全部强制机器门禁完成且按结果判定，不以exit0或相同总数替代逐项身份/浏览器合同；独立reviewer仍重跑全master矩阵。人工状态始终PENDING，生产30711 ENFORCE/发布未授权。
- 仅四个Authorized Files；无产品漂移、无基线/迁移/全局样式/宿主/报告修改。任何确证必要的额外文件先停、提交必要性，不自动扩大。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.

之前对ca1e7a97稿的批准不适用于本稿。批准现有内容需明确、人类来源的精确调用：

`$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md`

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md` invocation authorizes:

1. Only the four Authorized Files and required verification commands in this plan. Record this current artifact SHA-256 and exact approval before execution. Required product baseline is 7e131474f403d9b28178148909180ac8f1d7d1bb; later docs-only HEAD is separate. No discard of either prior product repair.
2. After both tasks and all mandatory commands pass, exactly one local product commit before READY_FOR_VERIFICATION, staging only Authorized Files, subject `fix(discovery): bind evidence source and isolate pending dialog Escape`.
3. Appending /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/repair-execution.md with exact approval, repair identity, pre/post code SHAs, changed files, commands/exits/counts/first failures, deviations, executor identity and clean-state evidence. Preserve previous execution epochs unchanged.
4. Exactly one docs-only evidence commit containing only that handoff, subject `docs(review-fast-p): record source and pending escape repair execution`.
5. Returning to the already authorized review-fast-p aggregate re-review in this same chat when the user's invocation requests it, using /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-master.md and /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/machine-verification.md. Executor does not self-review or ask the human to relay durable metadata.

This authorizes no extra files, amend/history rewrite, push/merge/deployment/production write, baseline amendment or automatic further repair round. This planning action authorizes no implementation or commit.

## Planning Self-Review

- Two tasks map only to verified V-3/V-2 residuals and original mandatory contracts; no observations/BLOCKED/optional cleanup included.
- Exact four existing paths can address each root: evidence origin is available at CLI branch; component owns document/lifecycle and existing test file owns its DOM model. No app/guard/shared test-library expansion assumed.
- Existing completed V-1/V-4/V-5 and old failure/approval/report lineage are preserved; neither old epoch nor approval is rewritten.
- All original14 gates retained; child-derived15/16/17 added with resolved executable paths. No unresolved path placeholders, new schema/API or weakened test expectation.
- One current artifact, exact product/evidence subjects and resolved handoff paths; new human approval remains required. No implementation performed.
