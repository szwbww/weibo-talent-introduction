# Repair Plan: paper-reprocess-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-master.md
Governing identity: sha256 85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e; recorded commit 53c2b0018e239e3c30db25942e99c2cddc69e9d4; AMENDMENT_RECORDED; A1–A13 remain in force.
Approved amendments: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fast/paper-reprocess-master/ledger.md, unchanged.
Verification report: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/machine-verification.md — Epoch 2, 2026-11-03T00:35:13+08:00; reviewer /root/paper_reprocess_aggregate_epoch2; FAIL / DIVERGING; report sha256 570fd89fa1e2e3bd05ee25c561531c45cb29c1b0539c9adc64c966bc47b26442.
Implementation boundary reviewed: 3a425461dbe6088e4d4dda1a53141416a392670c..291fb34b35be496673e28f124d443b9c46ba66f4
Reviewed evidence HEAD: 96da7863acdb8dd9a644048b8f9c99a8aa2aa7e0
Planning evidence HEAD: 3ab2e86c80993929350d1dc40c859fed1a24adbc
Required pre-repair product baseline: 291fb34b35be496673e28f124d443b9c46ba66f4
Selected worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
Selected branch: fast/paper-reprocess-master

## Human Adjudication and Repair Lineage

- Epoch 2 的 FAIL / DIVERGING 及原始失败回执保持不变，不改写为 PROGRESSING 或 PASS。
- 控制器向人类明确说明 DIVERGING 门禁，提出的裁决是：“保留现有修复，仅针对 V-3、V-5 重新设计修复方案”。人类在同一对话明确回复：“确认 继续”。这是保留当前修复、批准不同修复方法的规划授权；原始回复时间未另行记录，不制造时间戳。
- 此裁决解除本次规划的 convergence breaker；仅允许本稿两个已证实根因的重新设计，不授权实施、重新总审、扩大产品范围或自动追加修复轮次。
- 上一执行稿精确路径仍为 /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md；旧内容保存在 Git commit 3ab2e86c80993929350d1dc40c859fed1a24adbc 的同一路径，sha256 47b6bccdb128dd02177fe0d629e251afe781064d75dcbc5f18c07ab24a829110。不建立第二份当前修复稿。
- 上一执行从产品 e213acb50791fe39a0de0a4d9d599bc4e2398cc1 得到产品 291fb34b35be496673e28f124d443b9c46ba66f4；原十文件实施及其人类批准仍属历史 lineage。
- 上次执行交接：/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/repair-execution.md。旧批准只适用于旧稿，不能作为本稿的实施批准。
- 当前修复全部保留。未来执行以产品 291fb34b35be496673e28f124d443b9c46ba66f4 为基线，绝不能回到 e213acb50791fe39a0de0a4d9d599bc4e2398cc1。后续 controller 的 docs-only commit 可更新 HEAD；任何产品漂移、脏 index 或未授权工作区修改必须先停止核对。

## Objective

对真实 reprocess-run 的实际目标邮箱与不可变身份进行交叉验证，阻断假匹配；同时恢复已有重审 child 的候选投影重试，并保留已修复的 ordinary epoch0 读取/同步及其他已解决行为。

## Findings in Scope

| Finding | Severity / classification | Requirement | Evidenced root cause |
|---|---|---|---|
| V-3 | P1; PERSISTENT; REPAIRABLE | Master I-1/I-7/X-0; 00 I-1; 06 I-1/I-2; prior repair R-3 | reconcile_paper_identity_replay.py:107–123 提取 target.email 为 targetEmail，但 evidence():52–75 不校验它；186–198 仅按 candidate 自报稳定键选行。真实 record31372、正确 docId 0000-0002-0178-9985、expected emailHash 1522c43ad4d32fd525a502bb，实际邮箱错误或缺失仍为 BOUND / EXPECTED_MATCH。完整逐例证据在 epoch2 报告及 /tmp/paper-aggregate-epoch2-ei2_5x6s/identity-probe-results.json。 |
| V-5 | P1; REGRESSION; REPAIRABLE | Master M-1/I-7/X-4/X-8/X-9; 04 I-4; prior repair R-5 | DiscoveryRecheckService.kt:367–379 用 isPaperReprocessOwned 代替 run 类型，owned recheck child 被送入只接受 epoch0 的 requirePaperOwnedItem。合法 child30717/item4、COMPLETED、admissionRevision0，公开 syncRetry 返回404；隔离实际投影 ERROR 后仍404。epoch2 SQL/API/DOM/HTTP证据均已持久记录于报告。 |

两个根因、一个当前稿；无 PLAN_AMENDMENT_REQUIRED / NEW_SCOPE_REQUIRED 项。

## Findings Excluded

| Finding / item | Decision preserved |
|---|---|
| V-1 | RESOLVED。保留普通和已有档案 child 显式取消/retry、不重复副作用及旧 epoch/trace 的现有回归；不得重新实施或泛化取消机制。 |
| V-2 | RESOLVED。保留 confirm/detail Escape 仅关当前dialog、host不关闭、焦点归还、cancelPOST0 的现有回归；禁止重做宿主键盘或dialog实现。 |
| V-4 | RESOLVED。保留 summary.targetTotalKnown 真实 DTO、完成提示和服务端统计回归；不得改 DTO 或前端计数算法。 |
| V-5 的已解决部分 | NEW/REPAIRED 实际八步读取、owned ordinary projection-only retry 已通过；保留这些行为，任务仅修 child 分支回归。 |
| V-6 / child05 F-05 | 非阻断 P2：确定性503保留 ENFORCE pending key并锁模式；无修复授权。 |
| V-7 | 非阻断 P2：恢复任务完成后 preview.activeRun 陈旧，reload后才能新建；无修复授权。 |
| O-E2-01 | C10首轮 Trace lease lost、exit1及相同完整C10R exit0均保留；根因未定，不把取消时序观察列为本稿任务，不借本稿改 Worker/Trace/通用并发机制。 |
| 原编号References限制、可选旧294 corpus、generic summaryRule=null、历史rapid-tick投影trace重叠 | 原排除决定继续有效；不扩 parser/catalog/通用投影并发修复，不把相邻未知风险当已确认根因。 |
| Epoch1初始 paperBatchGroups 失败 | 首次8304.735秒尝试根因未证实；旧失败及203/0/0/1完整复跑保留，不做猜测修复。 |
| 旧重复缩放截图、旧run/config标签、global app401重定向证据局限、临时browser fixture设置适配 | 保留epoch2 RECORD_ONLY与Evidence Boundaries；未来按第14项取新鲜必需证据，不改产品以修fixture、不冒称线上设置/API验收。 |
| 历史reconciliation gain标签、summary-null文案、报告行数差异 | 非阻断文档/措辞，禁止顺手改报告、基线或验收口径。 |
| Production #30711 ENFORCE、live ES/ORCID/Apollo、人工验收及发布 | 未执行/范围外；隔离fixture不转换为生产结果，人工状态仍PENDING。 |
| Epoch2报告中其余 RECORD_ONLY | 原逐条映射及关闭/排除决定全部保留。只允许V-3/V-5的已证实变化，不将观察、BLOCKED证据或可选清理混入任务。 |

## Unchanged Contract

- Master I-1..I-9、M-1..M-5、各child完整机器/人工验收与A1–A13不变。本稿为实现修复，不修订基线。
- AUDIT零业务写/论文页零provider；原资格、姓名/邮箱证据、人工/联系人/APPLICATION保护、Apollo免费/付费策略、配置冻结及父许可不放宽。
- 仅原consume、窄CAS repair、Commit准入及既有project写叶子；不新增engine、endpoint、DTO、caller-controlled ownership或绕过权限的入口。
- 取消保留已提交RAW/补名/人物/准入；同父retry不重放成功动作、不认领foreign记录、不重置历史epoch、不删除收据。
- 普通server-owned epoch0与recheck child分别接受原有严格guard；合法admissionRevision=0不能当缺失。同步重试只投影，不触发资格、消费、人物登记或provider外呼。
- 固定manifest、原始PDF/metadata/first、旧replay/runtime输入及旧报告不覆盖；337组合/333历史分组/325历史机会/实际新增准入单位不混用；47未确认候选继续排除。
- S-1..S-6 CSS/DOM保持逐字契约；不改JS/CSS、app.js、全局样式、迁移、mapping、邮件、采集游标、下载范围或并发。
- 本轮规划只更新这一 repair.md；不改产品/测试、review报告/ledger、fast-p证据、主/子计划，不stage或commit。未来执行权限仅见最后的显式批准交接。

## Authorized Files

Exactly four existing product/test files，除此之外无产品文件授权：

| Absolute file | Purpose |
|---|---|
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py | 校验实际target邮箱规范化指纹与不可变manifest稳定身份，保留legacy replay和honest absence语义。 |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt | 在既有CLI测试中增加可区分真实邮箱缺口的控制/反例，修正固定假hash的测试输入。 |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt | 在syncRetry中先识别真实run类型，分别复用既有child与ordinary guard。 |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessEndToEndTest.kt | 增加真实SQL、既有loopback投影故障的ordinary/child同步对照回归，保留原固定12-target及已解决测试。 |

临时输出仅用新 /tmp 目录及正常 target 构建产物。未来执行交接文档的单独授权见末节，不计入这四个产品/测试文件。Repository、QueryService、Worker、前端及其他既有测试不获修改授权；若证明必须修改，先停并提交确切必要性，不自动扩大范围。

## Repair Tasks

### R-1: 从实际目标邮箱验证不可变身份

- Resolves: V-3；一个实际身份验证根因。
- Root cause: run_rows已拿到targetEmail，但evidence不使用；candidate key与docId能自洽，不代表actual target邮箱相同。现有有效控制的emailHash为固定b×24，与a@example.invalid无关，因此漏测。
- Exact files:
  - /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py
  - /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt
- Different approach / change:
  1. 对 --reprocess-run 的实际target，要求email为非空字符串。沿用现有 DiscoveryIdentity.normalizedEmail 与 replay tool 的规则：trim、locale-independent lowercase、UTF-8 SHA-256十六进制前24位；Python脚本内使用同一规则。只核对既有规范化规则，不引入RFC验证器、alias/plus-address推断、casefold/NFKC或新姓名推断。
  2. 实际邮箱指纹须与已验证不可变manifest组合的emailHash相同，同时保留既有recordId/bodySha256/nameHash及manifest/audit身份校验和适用expected docId校验。不能仅将candidate自报emailHash与它自己比较。
  3. 有匹配candidate key但实际邮箱缺失/null/非字符串/空白或指纹冲突，必须actualEvidence=INVALID且不得EXPECTED_MATCH；用现有REGRESSION语义报告。保留无效行，不能过滤成ABSENT/普通EXPECTED_MISSING。外部变化仍独立记录，不得将无效身份变为匹配。
  4. 只有真实run export需要actual target.email；合法 --new-replay 的既有flat rows没有此字段，不据此拒绝。既有receipt完整性/status验证、SOURCE_MISSING、honest absence、EXTERNAL_DATA_CHANGED、extras、duplicate/conflict/outcome/docId行为保持；SKIPPED只有身份合法时可作为绑定证据，仍不能当业务/准入成功。
- Minimal discriminating regression: 复用现有临时manifest/CLI helper，令有效emailHash由同一真实测试邮箱按上述规则计算；一组保持相同合法candidate key与expected docId的valid、wrong-email、missing/null/blank/non-string-email反例。错误与缺失均须INVALID/REGRESSION，有效及仅大小写/首尾空白差异控制须BOUND/EXPECTED_MATCH。保留现有wrong-doc、duplicate、unknown/missing-outcome、SKIPPED、conflicting replay、honest absence回归；合法flat replay无需targetEmail仍成功。断言逐行actualEvidence/classification，不以进程exit0或总数相等作为成功。
- Existing verification: Commands1、3、8、12、13；第1项跑完整现有replay-tool suite，包括CLI反例。复用epoch2真实record31372反例思想，在全新/tmp构造输入，不改冻结manifest或旧/tmp回执。
- Must not change: 原337固定身份和冻结输入、正确绑定增量、既有未知/缺源分类、只读/零网络/零业务服务、旧replay兼容。
- Prohibited: 改Kotlin生产parser/tool、改audit/manifest/报告来让反例通过、伪造匹配hash、仅新增更多outcome黑名单、丢弃无效实际行、扩大邮箱/姓名猜测或新增fixture文件进Git。
- Completion: 当前产品291上valid对照通过且wrong/missing邮箱反例能证明失败；修复后同组逐行判定符合上述要求，现有CLI与337原文重放/归并完整通过；不得改变合法old/new flat receipt语义。

### R-2: 先区分ordinary与child，再复用各自guard

- Resolves: V-5 REGRESSION；一个run类型分派根因。
- Root cause: isPaperReprocessOwned对普通run和重审child都为true，现syncRetry据此统一调用epoch0-only guard；不是child没有准入，也不是revision0无效。
- Exact files:
  - /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt
  - /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessEndToEndTest.kt
- Different approach / change:
  1. 从服务端已存run类型（现有recheckJson）识别ordinary或recheck，不让ownership代替run类型。真实recheck child继续走原requireRecheck及requireRecheckItem，校验ownerExecutionId和reviewEpoch==runId。普通run只有现有server-owned paper-reprocess归属证明成立，才允许原requirePaperOwnedItem的owner/epoch0例外；不存在或无归属的ordinary按原拒绝语义处理。
  2. 不修改QueryService/Repository的guard；已有只读recheckItem的类型分派可作为正确语义参照。保留请求ID/批量去重校验、鉴权、父许可、COMPLETED/admissionRevision非null/projection非NOT_REQUIRED检查，明确0是合法revision。
  3. ERROR/PENDING仅调用现有commits.project；不得重核验/外呼/重consume。PROJECTED重复请求保留幂等，既有recheck及ordinary读八步/返回类型不变。
- Minimal discriminating regression: 在既有EndToEndTest中增加一组真实MySQL+现有严格loopback ES场景：对已准入ordinary epoch0与同父REPAIRED child都制造可恢复候选投影失败，再分别经syncRetry重试；修复前child404且ordinary控制通过，修复后两者均返回对应实际item并PROJECTED。明确覆盖合法child admissionRevision=0，准入行/修订与provider请求列表不变。复用现有failCandidateDocs等故障接缝，不靠mock requirePaperOwnedItem返回值证明；此新场景与独立runtime main接通。原固定12-target测试的新增2/补齐2/准入2及syncFailure1断言保持，不能为新增对照改写其真值。
- Guard regression: 在同组保持foreign run/item组合、无归属ordinary拒绝，拒绝时无投影/外呼；原非paper recheck继续原guard。复用现有保护/未准入/未完成约束测试，不新增无关排列矩阵。不得用真值测试跳过revision0，也不得为通过测试伪造recheck或新增child。
- Existing verification: Commands1、3、4、9、10、11、14；现有Query/Recheck/Commit/Integration suites全部通过。第14项走实际公开HTTP和原八步同步按钮，ordinary及child均包含真实ERROR→PROJECTED，不以只读GET200替代sync验证。
- Must not change: V-1已修复retry/归属/父许可，V-5已修复八步读取，普通非paper行为，原recheck history、鉴权、保护、资格/准入及投影writer。
- Prohibited: 改requireRecheck/requireRecheckItem/requirePaperOwnedItem本身、全局放宽epoch或归属、把所有paper-owned视为ordinary、caller-controlled ownership、重建child、重置资格、直接SQL补准入、新engine/endpoint/DTO或修改其他产品文件。
- Completion: admitted ordinary与owned child均可公开projection-only retry；revision0有效；wrong owner/epoch及无归属ordinary仍拒绝；成功投影不增加任何provider/资格/消费调用，不改变准入；原固定结果、读取与取消回归全部保留。

## Verification Commands

以下为未来明确批准后的执行/独立复验要求；本轮仅规划，不运行。所有命令在 /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master 执行，Maven使用 /opt/homebrew/bin/mvn；所有Java及child Java使用 /Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home。正常需要的本地Docker/target权限沿用已授权隔离验证。每个命令记录完整日志、真实exit及实际tests/failures/errors/skips；Maven和target写操作严格串行，使用有限watchdog并逐条持久回执。先做能区分两个根因的最小回归，再在最终代码状态完整跑下列门禁；已完成且代码未再变的门禁不作无依据重复。

准备全新输出目录：`REPAIR_TMP="$(mktemp -d /tmp/paper-reprocess-master-identity-sync.XXXXXX)"`。保留这个实际生成的绝对路径，所有临时反例、日志、截图及12–13输出在此目录，命令回执记录展开后的真实argv。REPAIR_TMP是可执行初始化产生的目录变量，不是待填写路径占位符。目标输出必须不存在，不覆盖旧回执。

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
14. 新鲜隔离实际浏览器/API验收：真实AUDIT/API/MySQL、跨121篇与重入、空/运行/500/完成状态；confirm及detail Escape保持host、还原焦点、cancelPOST0；完成页使用summary.targetTotalKnown。NEW ordinary与REPAIRED child都打开实际八步，source-reader只读且analysisPOST0。分别让合法已准入ordinary及child出现候选投影ERROR，经现有公开HTTP及原“仅重试候选同步”动作成功PROJECTED，保留真实run/item/epoch/revision（含0）、前后SQL/API状态及provider请求数不变。保留错误归属/无归属ordinary拒绝；不以仅八步GET成功代替同步恢复。S-1..S-6逐字CSS/DOM核对及1440/390、100%/125%等效viewport截图完整保留；不同viewport分别捕获，不能把不变100vw上的CSS zoom声称为原生125%。截图/HTTP/DOM仅存新REPAIR_TMP。fixture适配和provider/ES替身明确披露，不能替代真实已保存配置或人工验收。

第1项的两个既有suite承载两个区分性回归，同时保留V-1/V-2/V-4的现有回归。第3项为完整十五类JDK11门禁；第4项为独立E2E；第5项必须真实MySQL空库及升级；第6项仅JS/WAR，不冒称Kotlin已测；第9–11项三个独立JVM runtime；第12–13项337原始字节生产重放及逐条归并。不得删减以缩短验证。

冻结输入sha256：manifest de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c；/tmp/paper-reprocess-30711-baseline.json e0fe5b1e008fda6188315f73972f72af79b3897b6ca59f1a4f04c8d452b3d91c；/tmp/paper-reprocess-30711-runtime.json 5d00ffbc736f653fe64b93b5e667c8baf776aa33b1320716d212291b45e037c3。原文只读位置 /Users/lukai/.local/share/paper-reprocess-30711，禁止写入或入Git。每次执行前后核对冻结输入；新缺源据实报告，不能改真值凑通过。

保留每次首轮失败及根因未知状态，尤其epoch2 C10 Trace lease lost / C10R通过。若未来必需门禁失败，先记录并定位；无确证且有授权依据，不扩文件/修改排除项或反复重跑至绿。不沿用本报告或上一执行者计数作为新证据。

## Completion Criteria

- V-3实际target邮箱错误/缺失/冲突不再假match；有效规范化控制、合法flat replay及honest absence仍正确。逐条classification与actualEvidence区分，不能仅总数或exit0通过。
- V-5 ordinary/child按真实run类型接受各自原guard；两个公开投影重试均成功，合法revision0保留；不增加provider/资格/consume调用、不改变准入，非法归属继续拒绝。
- V-1/V-2/V-4已解决行为及原V-5八步读取不回归；只作保留验证，不授权重新实施。V-6/V-7、O-E2-01及全部排除项不是修复任务；不得削弱必需门禁或隐去失败。
- Commands1–14全部取最终代码状态的新鲜回执。唯一既有可选旧私有corpus skip明确披露；其他必需NOT_RUN不能算PASS。独立reviewer仍按完整master矩阵判定，执行者自测不等于独立PASS。
- 固定337组合、244原文及各项增量/缺口诚实可复算；无新错绑、无旧正确绑定丢失，所有冻结输入哈希不变。fixture新增/补齐/准入不冒充生产效果。
- 产品/测试变更严格只有四个Authorized Files，不改基线、迁移、前端或其他测试。任何新文件必要性或产品漂移先停并请求确切授权。
- 未来执行前，确认产品基线仍291fb34b35be496673e28f124d443b9c46ba66f4；HEAD较新只允许既有docs-only证据/规划commit。本次规划不改产品/index/HEAD/branch，不stage或commit。
- 明确批准后的提交与交接仅按末节执行；人类验收始终PENDING，未授权生产/发布。不存在自动下一轮修复或平行当前稿。

## Human Approval

当前状态为DRAFT。人类“确认 继续”只批准不同方法的规划，不是批准本稿实施。

执行必须等待随后明确、人类来源、针对本稿现有内容的精确调用：

`$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md`

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md` invocation authorizes:

1. Only the four Authorized Files and required verification commands in this plan. Before execution, record this current artifact SHA-256 and exact human approval. Required pre-repair product baseline is 291fb34b35be496673e28f124d443b9c46ba66f4; record the actual docs-only evidence HEAD separately. The old e213acb50791fe39a0de0a4d9d599bc4e2398cc1 is lineage, not the new baseline.
2. After both repair tasks and all required commands pass, exactly one local product commit before emitting READY_FOR_VERIFICATION, staging only the four Authorized Files, with subject `fix(discovery): validate paper targets and preserve child sync retry`.
3. Appending /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/repair-execution.md with the exact approval source, current repair path and SHA-256, pre/post product SHAs, evidence parent, changed files, exact commands/exits/counts and retained failures, deviations, executor identity when exposed, and clean-state evidence. Preserve every prior execution epoch; do not rewrite old approvals or receipts.
4. Exactly one docs-only evidence commit containing only /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/repair-execution.md, with subject `docs(review-fast-p): record identity and child sync repair execution`.
5. Returning to the already authorized review-fast-p aggregate re-review in the same chat when the user's invocation requests it, using master /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-master.md and aggregate report /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/machine-verification.md. The executor does not perform its own independent aggregate review.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, production write, discarded current repair, baseline amendment, automatic extra repair round or product repair beyond this plan. This planning action itself authorizes none of those future execution steps.

## Planning Self-Review

- 两个任务分别映射V-3/V-5已证实根因；人类裁决明确保留291产品并批准不同规划方法，历史FAIL/DIVERGING保持。
- 四文件已据当前源码核对：规范化/指纹可在现有脚本完成；RecheckService已有findRun/recheckJson与两类query guard，无需改Repository、QueryService或新增接口。
- 缺证据/观察/已关闭项均排除，现有强制回归完整保留；没有把C10未知根因转为修复任务，没有削弱验收或隐含范围扩张。
- 当前唯一稿沿用同一路径；旧稿身份与Git位置已保留。未来产品基线291、四文件、两个提交subject和交接绝对路径全部明确。
- 本次仅规划；未运行机器门禁、浏览器或业务，不修改产品，不stage或commit。
