# Aggregate Machine Verification — apollo-repair-retention-master

## Epoch 1 — 2026-10-21T21:40:08+08:00

- Master plan: docs/plans/2026-10-21/apollo-repair-retention-master.md (sha256 7b0113d6782083d3c86b910f74197bd39e3875f2018a93e68ef6d0f5554cfbda)
- Governing master identity: worktree and fast-p recorded sha256 7b0113d6782083d3c86b910f74197bd39e3875f2018a93e68ef6d0f5554cfbda
- Master identity state: CONSISTENT; approved child06 amendment A1 retained, no master amendment
- Boundary: 962fcfe924ad7562a4ea917d174180df82ac3b5c..462c52385bfe3b248016829a08a5b6ceb2403bb4
- Reviewer: /root/apollo_retention_aggregate_review (fresh fork_turns=none)
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: N/A / BLOCKED — PLAN_AMENDMENT_REQUIRED
- Original complete reviewer report SHA256: 5e148ae4ed020b8188ecdd5b0ad4432f162661288e63cdbbb77634cbd03a3975
- Evidence parent before this commit: 3852a35c4a273fddb85b6d9da354e9d7c9b12abe

## Verification Result: FAIL

Plan: `docs/plans/2026-10-21/apollo-repair-retention-master.md`，phase=`aggregate/master`。
Implementation boundary: `962fcfe924ad7562a4ea917d174180df82ac3b5c..462c52385bfe3b248016829a08a5b6ceb2403bb4`。
Convergence: **INITIAL**。首次聚合复验；无既往聚合报告或聚合修复轮次。
Manual acceptance: **PENDING**（主计划 A-1/A-2/A-3及其子项）。
Reviewer: `/root/apollo_retention_aggregate_review`；未参与 fast-p 实施、修复或轻复验。

结论：全部10组要求的机器命令均已完成；9组通过，总回归C09R因1条版本断言失败。另有已复现的强制隐私契约缺陷V-1。阻断项为V-1（P1）和V-2（显式强制测试失败的P2）；V-2由历史RECORD_ONLY项O-04-01核实升级。当前未部署，未调用真实 Apollo/ORCID，未操作生产数据库、生产样本或执行历史 replay apply。

### Identity and Evidence Boundary

- Repository原目录：`/Users/lukai/IdeaProjects/weibo-talent-introduction`。
- 所有Git/源码审阅/Maven均在选定worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master`。
- Branch：`fast/apollo-repair-retention-master`。
- Master base：`962fcfe924ad7562a4ea917d174180df82ac3b5c`；final_code_head：`462c52385bfe3b248016829a08a5b6ceb2403bb4`；审阅时evidence_head：`3852a35c4a273fddb85b6d9da354e9d7c9b12abe`。
- governing / invoked / fast-recorded主计划SHA256均为`7b0113d6782083d3c86b910f74197bd39e3875f2018a93e68ef6d0f5554cfbda`；状态 **CONSISTENT**；governing diff空。
- Fast ledger：`docs/plans/fast/apollo-repair-retention-master/ledger.md`，SHA256 `7ff79d501ff04d206bd82ae344dc4abc7bcb6172617994d5dd6beafab6c8f15e`。
- Handoff：同目录`human-review-handoff.md`，SHA256 `b2fa58b6a552284c80102552dd1d28cca4fe222e29c54b01e67df03ab3c20d2c`。
- Child证据：同目录`children/01`至`children/06`各`brief.md`、`execution.md`、`verify-log.md`、`fix-log.md`，共24份；只作历史证据，不替代当前命令。
- A1：子06 approved commit `c337ee2aec8a7fc5f36584afeb0d00464ca5103e`，前态`27e5f15b5ee53234c9bcf06b7afa6057dfdee008`；主计划执行约束第2条；`HUMAN:批准 继续; 2026-10-21`。仅增授权`ExpertDiscoveryServiceTest.kt`构造调用迁移/必要mock，原断言保留；没有主计划行为修订。
- Worktree resolution：**DISCOVERED_FROM_GIT_WORKTREES**。本审阅者重新执行`python3 /Users/lukai/.agents/skills/review-fast-p/scripts/discover_fast_p.py --repo /Users/lukai/IdeaProjects/weibo-talent-introduction --master-plan docs/plans/2026-10-21/apollo-repair-retention-master.md`，exit0，唯一SELECTED，6个子计划，匹配上述路径、分支、边界与哈希；选中摘要`/tmp/apollo-aggregate-review/discovery-selected.json`。未把其他worktree的review证据作为输入。
- base→code→HEAD、A1→HEAD祖先检查均exit0；code之后只有fast证据文件；审阅开始和结束产品/index无变更。
- 规范性`apollo-repair-retention-evidence.md`的E-6/E-7全写读链和E-8交互纳入矩阵。其代码回执22项SHA256逐项与base的Git内容核对，22/22一致：`/tmp/apollo-aggregate-review/receipt-hash-audit.json`。
- post-repair evidence mode、executor、approval：**N/A**，本轮尚无聚合修复执行。

子计划身份：

| 顺序 | 路径（docs/plans/2026-10-21/） | SHA256 |
|---|---|---|
| 01 | `apollo-repair-01-error-retention.md` | `290edb938b50bbb8859cc96db2d64fb1e3bacdcbc075186bd12e57448e32bb0b` |
| 02 | `apollo-repair-02-credit-recovery.md` | `13e24fb1c83a1035ba58a902642833947755c7c79320fc5d474ddb7c819b6b16` |
| 03 | `apollo-repair-03-search-trace.md` | `3cfa9fad532edb7e462148b85a84dfa5ac6f6d85ed8641ff2f14835ed370ec53` |
| 04 | `apollo-repair-04-identity-normalization.md` | `1b1fb2670794126f2a7842988319d7b9796a244a4bd74c00e23a6cb111896b2b` |
| 05 | `apollo-repair-05-decision-audit.md` | `de1aba525acca6e45b8ef434360f85fd79715944a46af36b18f33c44e42e1809` |
| 06 | `apollo-repair-06-promotion-reporting.md` | `bc32f14ea0db548fc4b81ea3459777f016da5f317aa1e6831c1365e3ea42e999` |

### Commands

环境：`JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`，其bin前置PATH；`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`；重试与后续统一`JAVA_TOOL_OPTIONS=-Dapi.version=1.44`。JUnit计数格式为tests/failures/errors/skipped。全部Maven串行；计数来自此次命令完整输出及其选中类的XML，不使用目录中其他旧XML。每组XML备份于对应`/tmp/apollo-aggregate-review/Cxx-reports/`。

`DiscoveryVerificationIntegrationTest.kt:41`明确受`mysqlIt=true`控制，因此最终回归显式启用MySQL/API参数，实际运行而非跳过。`CLAUDE.md`的全量`mvn test`和`mvn clean package`是项目默认命令；本轮按已批准计划的明确命令核验（fix-v Phase2：计划有命令时优先），未声称跑过仓库全部JVM测试或WAR打包。

| ID / 对应要求 | 完整命令 | 结果与计数 | 新鲜证据 |
|---|---|---|---|
| C01R / 01 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ApolloPeopleSearchClientTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest test` | PASS，exit0，40/0/0/0 | `/tmp/apollo-aggregate-review/C01R.log`；完成2026-10-21T17:32:08+08:00 |
| C02R / 02 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationReplayToolTest,DiscoveryVerificationQueryServiceTest test` | PASS，exit0，34/0/0/0 | `/tmp/apollo-aggregate-review/C02R.log`；完成2026-10-21T17:37:07+08:00 |
| C03 / 02和03（相同命令去重） | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationRepositoryIT test` | PASS，exit0，17/0/0/0 | `/tmp/apollo-aggregate-review/C03.log`；完成2026-10-21T17:12:28+08:00 |
| C04R / 03 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest test` | PASS，exit0，59/0/0/0 | `/tmp/apollo-aggregate-review/C04R.log`；完成2026-10-21T17:41:19+08:00 |
| C05R / 04 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloMatchingV2Test test` | PASS，exit0，72/0/0/0 | `/tmp/apollo-aggregate-review/C05R.log`；完成2026-10-21T17:45:31+08:00 |
| C06R / 05 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationReplayToolTest test` | PASS，exit0，53/0/0/0 | `/tmp/apollo-aggregate-review/C06R.log`；完成2026-10-21T17:59:57+08:00 |
| C07 / 06 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryPromotionProgressServiceTest test` | PASS，exit0，12/0/0/0 | `/tmp/apollo-aggregate-review/C07.log`；完成2026-10-21T17:22:07+08:00 |
| C08R / 06 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationReportingIT test` | PASS，exit0，12/0/0/0 | `/tmp/apollo-aggregate-review/C08R.log`；完成2026-10-21T21:30:58+08:00 |
| C09R / 主计划最终回归 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationIntegrationTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationAdmissionGateTest,ApolloMatchingV2Test test` | FAIL，exit1，33/1/0/0 | `/tmp/apollo-aggregate-review/C09R.log`；完成2026-10-21T21:34:20+08:00 |
| C10 / 06既有Controller交互与A1 caller | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ExpertDiscoveryServiceTest,TaskProgressControllerTest,TaskExecutionControllerTest test` | PASS，exit0，217/0/0/0 | `/tmp/apollo-aggregate-review/C10.log`；完成2026-10-21T21:37:56+08:00 |

C09R分组：ApolloMatchingV2Test 9/0/0/0，IntegrationTest 12/1/0/0，CommitServiceTest 4/0/0/0，AdmissionGateTest 8/0/0/0；失败仅新run别名版本断言。

上述9组成功命令也均完成Maven绑定前端测试/语法检查；C09R在Surefire失败，未进入该命令后续JS阶段，不伪计成功。每次1733 JS tests，1733 passed，0 failed/skipped。重复执行次数不是新增业务样本或独立人数。

初次环境失败完整保留：C01=exit1，40/0/4/0；C02=exit1，34/0/5/0；C04=exit1，59/0/4/0；C05=exit1，72/0/4/0；C06=exit1，53/0/5/0。它们均为Testcontainers默认Docker API1.32遭server最低1.40拒绝；无业务断言失败。补`JAVA_TOOL_OPTIONS=-Dapi.version=1.44`后分别以R后缀完整重跑成功。原C08执行中断，日志无最终exit/count，不计PASS，C08R完整重跑。已成功C03/C07未重复。原始日志保存在同目录`C01.log`等；没有把环境失败解释成产品缺陷或豁免必要用例。C06R完成17:59:57，C08R完成21:30:58；中间存在长墙钟间隔，恢复检查时原脚本仍存活且已推进C09R，不据此推断系统休眠或产品挂死。

独立诊断D-1：`python3 /tmp/apollo-aggregate-review/run-sanitizer-probe.py`，exit0，3组合成输入：2组带空格引号秘密均残留、1组无引号单词控制正常；这是复现成功，不是契约PASS。源码`SanitizerProbe.java`、类路径`classpath.txt`、输出`sanitizer-probe.log`同目录。使用当前worktree编译类；`VerificationDiagnostics.class` SHA256=`c08fa7e0ac39e4ccef5116b64f74237f19b67fb5f3ca80bafc1235801554b7ab`。仅合成TOPSECRET样本，无用户真实秘密、数据库或HTTP调用。首次诊断类路径误取原工作区XML导致ClassNotFoundException，已更正为选中worktree target/classes；该失败不作行为证据。

### Contract Matrix

所有路径相对于所选 worktree；S=`src/main/kotlin/com/weibo/talentintroduction/discovery/service/`，R=`src/main/kotlin/com/weibo/talentintroduction/discovery/repository/`，TS=`src/test/kotlin/com/weibo/talentintroduction/discovery/service/`，TR=`src/test/kotlin/com/weibo/talentintroduction/discovery/repository/`。每行均为强制契约；人工项单列 PENDING。

| ID / 契约来源 | 强制要求 / 必需证据 | 判定 | 当前实现证据 |
|---|---|---|---|
| M-I1 / 主计划关键不变量及验收 | 分开基线、纯规则、Gate/Commit、真实请求；不把264/14/Nicolas重复算作收益 | PASS | S/DiscoveryVerificationReplayTool.kt:300 冻结存量/旧复算/新复算三列、Gate与当前保护NOT_EVALUATED、admissionWrites=0；TS/DiscoveryVerificationRulesTest.kt Nicolas固定样本；本报告不宣称生产提升 |
| M-I2-a / 主计划原账保护、E-6 | 原attempt不覆盖；恢复不改run/batch/请求账，新增HTTP才增计数 | PASS | R/DiscoveryVerificationRepository.kt:97 恢复独立CAS，:146仅重置状态、保留证据/账；:492真实预约插attempt及request_count；C03与C02R |
| M-I2-b / 主计划准入与保护、E-7.1 | SELF、单姓名/同名拒绝、APOLLO_UNCLEAR、身份/人工/事实保护；所有准入经Gate/Commit | PASS | S/DiscoveryVerificationRules.kt:88、:115、:143、:176；S/DiscoveryVerificationWorker.kt:173；S/DiscoveryVerificationCommitService.kt:49、:55、:61、:87、:96；C05R；C09R相关兼容/保护用例通过，整组V-2失败 |
| M-I3 / 顺序、白名单、单一诊断根 | 01→06，各步≤10文件；仅授权文件；diagnostics唯一新根 | PASS | Git每步差异7/9/9/9/9/5，累计22，越界0；S/DiscoveryVerificationWorker.kt:23、:30；Git提交序列与fast ledger逐步checkpoint |
| M-SCOPE / 主计划非目标、E-7.2–5 | 无schema/ES mapping/frontend/采样策略/发送逻辑新改动，无部署或批量补跑 | PASS | 累计src差异全在22文件；migration、ES、sample、controller、Gate/Commit写路径均无产品diff；本次工具仅本地容器测试及只读诊断 |
| M-FREEZE / 执行约束1 | 实施前冻结Git/生产WAR/任务，不覆盖原工作区 | PASS（历史执行证据） | fast/ledger.md Baseline及baseline-freeze.txt；原始基线SHA、WAR/class摘要、1046项快照已记录。此次不替代生产重新冻结 |
| M-ROLLBACK / 执行约束4–5 | 历史不自动升级；ERROR独立恢复；v2旧二进制失败关闭，不直接降级 | PASS | S/DiscoveryVerificationWorker.kt:202、:211、:218按持久payload优先；旧版本Rules范围0..1可由基线git确认；新版本0..2；无自动恢复调用；旧apply仍DECIDE |
| 01-I1 / 子01分类 | 422只有明确短语可判额度，参数/未知分开；auth/429/5xx不变 | PASS | S/VerificationDiagnostics.kt:112 classify；S/ApolloPeopleSearchClient.kt:76–92；TS/ApolloPeopleSearchClientTest.kt:103；C01R |
| 01-I2-a / 子01有界诊断 | 非2xx最多16385字节，白名单、有限字段，非JSON正文不留原文 | PASS | S/ApolloPeopleSearchClient.kt:77；S/VerificationDiagnostics.kt:125–140；TS/ApolloPeopleSearchClientTest.kt:181；C01R |
| 01-I2-b / 子01秘密脱敏 | message中的key/token/password完整值不可进入SQL或API | **FAIL V-1** | S/VerificationDiagnostics.kt:93–98按空白终止secret匹配；当前类合成复现中双引号password及单引号token的尾部TOPSECRET omega仍存在于fromBytes序列化与read投影；sanitizer-probe.log |
| 01-I3 / 子01哈希与缺失 | 非成功response_sha256=NULL；FULL/PREFIX真实字节；结构失败不伪造大小；历史NULL | PASS | S/DiscoveryVerificationWorker.kt:156–163；S/ApolloPeopleSearchClient.kt:99、:183；S/VerificationDiagnostics.kt:138–140、:142；TS/ApolloPeopleSearchClientTest.kt:194；C01R |
| 01-X / 子01集成、E-8 HTTP→SQL→API | 失败请求仅计1次，错误诊断同事务入账并可读 | PASS（脱敏例外见V-1） | TS/DiscoveryVerificationWorkerTest.kt:278 real MySQL+Mock HTTP+Query；S/DiscoveryVerificationWorker.kt:156；C01R |
| 02-I1 / 子02状态 | DETAIL额度失败RETRY_WAIT，outcome NULL/route NONE；保留游标，不增transient | PASS | S/DiscoveryVerificationWorker.kt:193–196；R/DiscoveryVerificationRepository.kt reschedule；TS/DiscoveryVerificationWorkerTest.kt:330、:365；C02R |
| 02-I2 / 子02跨进程单探测 | 持久账+Apollo锁、1800秒、每窗单预约，旧在途成功不解除，SEARCH/ORCID/投影不阻断，无500/12限制恢复 | PASS | R/DiscoveryVerificationRepository.kt:460–518；S/DiscoveryVerificationWorker.kt:58–63、:132、:443；TR/DiscoveryVerificationRepositoryIT.kt:324、:360；C03/C02R |
| 02-I3 / 子02受限恢复 | settled ERROR422、manifest/整项CAS、身份/人工/投影保护、≤100；只保留有效APOLLO_SEARCH详情游标 | PASS | R/DiscoveryVerificationRepository.kt:100–188；S/DiscoveryVerificationReplayTool.kt:198 不走旧expected；TR/DiscoveryVerificationRepositoryIT.kt:401、:423；TS/DiscoveryVerificationReplayToolTest.kt:158；C03/C02R |
| 02-I4 / 子02历史账 | 不覆旧attempt/请求账/归属；重复apply返回STALE；无HTTP | PASS | R/DiscoveryVerificationRepository.kt:111–151；S/DiscoveryVerificationReplayTool.kt:198–211 queue-only及独立回执目录；C03/C02R |
| 02-CLI / 子02参数与权限 | apply-errors只收绝对manifest/hash，0700/0600；业务拒绝有回执，连接失败退出2且不泄密；旧apply不变 | PASS | S/DiscoveryVerificationReplayTool.kt:50–87、:198；同文件既有main、load、atomicWrite；TS/DiscoveryVerificationReplayToolTest.kt:111、:121、:144、:158；C02R |
| 03-I1 / 子03分页 | 第5页截断后续查，其余空页不洗掉不完整；无第6页/额外DETAIL；PENDING | PASS | S/DiscoveryVerificationWorker.kt:279–342；S/DiscoveryVerificationRules.kt:18–27、:115；TS/DiscoveryVerificationWorkerTest.kt:456；C04R |
| 03-I2 / 子03持久游标 | 固定queries+finishedQueryKeys业务事实，页响应/轨迹/游标同事务；重启不漏页 | PASS | S/DiscoveryVerificationWorker.kt:264–335、:156；R/DiscoveryVerificationRepository.kt completeAttemptAndProgress；C04R运行时断页重启用例 |
| 03-I3 / 子03成功复用 | 仅同run/person成功200/OK解析详情，来源attempt/hash；不造HTTP；冲突仍保留 | PASS | R/DiscoveryVerificationRepository.kt:662–687；S/DiscoveryVerificationWorker.kt:348–406；TR/DiscoveryVerificationRepositoryIT.kt:448；C03/C04R |
| 03-I4 / 子03诊断非authority | 单root，查询32/复用200有total/truncated；缺失NULL；Gate忽略诊断 | PASS | S/VerificationDiagnostics.kt:27–87；S/DiscoveryVerificationQueryService.kt:116；S/DiscoveryVerificationAdmissionGate.kt持久DTO只读取业务字段；C04R/C05R |
| 04-I1 / 子04冻结版本 | 新run rule2/alias2；旧缺setting新payload1、缺payload字段0；未知拒绝；Worker/Gate/Query一致 | PASS | S/DiscoveryVerificationRegistrationService.kt:64–70；S/DiscoveryVerificationWorker.kt:202–218；S/DiscoveryVerificationRules.kt:43–47；TS/DiscoveryVerificationWorkerTest.kt:22；C05R；C09R相关兼容/保护用例通过，整组V-2失败 |
| 04-I2 / 子04机构别名 | 仅TUM/Geneva有来源、版本隔离、全主体对应、明确ID冲突不覆盖、原因正确 | PASS | S/DiscoveryInstitutionNormalizer.kt:280–313；S/DiscoveryVerificationRules.kt:158–169；TS/DiscoveryInstitutionNormalizerTest.kt；C05R |
| 04-I3 / 子04职称 | 精确Assistenzprofessor+明确Assistant Professor前缀、负面排除；原title不改、同名/姓名门禁保持，邮箱路线不扩展 | PASS | S/DiscoveryVerificationRules.kt:318–324、emailNameMatches；Worker:321、:370传冻结版本；TS/DiscoveryVerificationRulesTest.kt Nicolas及负例；C05R |
| 04-I4 / 子04收益分层 | Nicolas两路合并只1；纯规则不直接写admission；Gate/Commit保护另验 | PASS | TS/DiscoveryVerificationRulesTest.kt固定日期独立/组合；TS/DiscoveryVerificationWorkerTest.kt:22真实Gate；S/DiscoveryVerificationReplayTool.kt:300–320；C05R；C09R相关兼容/保护用例通过，整组V-2失败 |
| 05-I1 / 子05真实分支 | trace与同次规则产生；未执行NOT_EVALUATED；无第二匹配器；Worker持久，Query不伪造 | PASS | S/DiscoveryVerificationRules.kt:48–72、:122–155、:230–247、:284–337；Worker:166–174；Query:116；C06R |
| 05-I2 / 子05有界全量 | 候选业务全集判定后取trace≤200，命中优先；total/truncated/version/time/hash/ref；报告0700/0600 | PASS | Rules:48、:61–72、:138–156；VerificationDiagnostics.kt:59–87；ReplayTool secureDirectory/atomicWrite；TS/DiscoveryVerificationRulesTest.kt:35；C06R |
| 05-I3 / 子05证据来源 | 旧trace不补RECORDED；冻结重算RECOMPUTED；缺错误正文NOT_RECORDED；Gate/保护分列 | PASS | Query:116；ReplayTool:306–319、FrozenRulesEvaluator；TS/DiscoveryVerificationQueryServiceTest.kt:22；C06R |
| 05-CLI / 子05显式冻结对照 | mode frozen-rules显式target2/2，旧参数保留；不用Parser/外部，不改before/manifest；固定时间/class摘要 | PASS | ReplayTool:54–87、:100、:261–365；TS/DiscoveryVerificationReplayToolTest.kt:17–98、WorkerTest:22；C06R。跨版本类路径必须与manifest一致，报告不声称实际生产旧类收益已验证 |
| 06-I1 / 子06投影定义 | owner/ENFORCE/activated/admissionRevision/PROJECTED；doc去重、source最新合格事件；其余投影状态0 | PASS | R/DiscoveryVerificationRepository.kt:193–209；TR/DiscoveryVerificationReportingIT.kt:109；C07/C08R |
| 06-I2 / 子06集合并集 | legacy窗口保留，排除managed非PROJECTED，按doc并集，managed source优先；aggregate floor保留且明确 | PASS | S/DiscoveryPromotionProgressService.kt:73–109、:118–120；TS/DiscoveryPromotionProgressServiceTest.kt:125–186；C07。LEGACY_AGGREGATE_FLOOR样例原5、可验证1、显示5，不冒称5均有doc账 |
| 06-I3 / 子06只读双入口 | 两refresh同count，无SQL/ES写，故障保留原进度，真实controllers一致 | PASS | S/DiscoveryPromotionProgressService.kt:37、:60、:135；TR/DiscoveryVerificationReportingIT.kt:137–207真实MySQL→双Controller MockMvc，重复读校验无写；C07/C08R/C10 |
| A1 / 主计划执行约束2+已批准amendment | ExpertDiscoveryServiceTest.kt仅构造迁移/mock；保留原断言 | PASS | TS/ExpertDiscoveryServiceTest.kt:441、:468；Git diff仅import、mock空ProjectionDocuments、构造新参数，未改断言；C10 |
| M-COMMAND / 主计划定向+最终回归 | 每条定向及最终四类实际运行，必要skip0；不得用旧报告代替 | FAIL V-2 | C01R–C10命令表；C03同时映射子02/03相同IT命令；C09R 33/1/0/0，IntegrationTest:235旧版本预期失败 |
| M-A1 / 人工失败解释与恢复 | 额度/未知422/429详情分开；重启冷却期间DETAIL0，单探测200恢复；其他通道继续，旧attempt不变 | PENDING | 主计划A-1及01/02人工项；对应机器证据C01R/C02R/C03不替代人工交互结果 |
| M-A2 / 人工漏查与保护 | 页上限后续查仍保守；Nicolas只计1；姓名/歧义拒绝；SELF/APOLLO_UNCLEAR保持；旧版本不变；读取trace/固定对照 | PENDING | 主计划A-2及03/04/05人工项；对应机器证据C04R/C05R/C06R/C09R不替代人工结果 |
| M-A3 / 人工统计与留档 | PROJECTED+ALREADY_PRESENT：自动准入2、晋升1；两个入口一致；旧缺正文NOT_RECORDED；原sample hash不变；刷新无写 | PENDING | 主计划A-3及03/05/06人工项；对应机器证据C07/C08R/C10不替代人工结果 |
| M-PRODUCTION / 部署后生产对照 | 冻结27254基线→小量恢复→重查额度/错误类型→完整新run；真实sample hash和收益单列 | PENDING | 需后续部署/运维授权；本次未操作生产，未作收益承诺 |

### Cumulative Authorized File Scope

Git按各子边界独立检查：01=7、02=9、03=9、04=9、05=9、06=5；每步均≤10且白名单差集空。累计去重22（主计划原21+A1增加1），如下；无新增SQL migration、ES字段/映射、前端、采样文件或其他产品文件。

- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerification.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClient.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizer.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/VerificationDiagnostics.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationReportingIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClientTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGateTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayToolTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW（本次聚合首次） | 子01 I-2；当前sanitizer对带空格引号值残留，D-1当前类复现 |
| V-2 / O-04-01 | NEW（历史观察本轮确认阻断） | C09R必跑总回归33/1/0/0；新run alias1旧断言与子04默认2冲突；保留O-04-01原ID映射 |
| F-01（子01历史） | RESOLVED，仅指原具体反例 | 旧问题是秘密key后的结束引号不被识别；现有HTTP测试覆盖单词quoted token/key/password并通过。本次V-1是值边界遇空格提前结束的另一个可复现残留，不抹除此前修复记录，也不把轻复验PASS当本反例已满足 |
| F-04-01（子04历史） | RESOLVED | WorkerTest新run fixture显式ENFORCE；C05R实际跑完SQL→Gate→投影→Query及冻结版本矩阵 |

### Findings

#### P1

**V-1 — 引号秘密包含空格时，尾部仍被持久化并返回详情API。**

- 要求：主计划I-2及子01 I-2/实现步骤2，message中的key/token/password必须脱敏；恶意错误正文不得在保存内容或API输出含原秘密值。
- 根因：`src/main/kotlin/com/weibo/talentintroduction/discovery/service/VerificationDiagnostics.kt:93`秘密值匹配以空白/逗号/分号/引号终止，前面的可选引号没有形成完整引号值边界。`sanitize`只移除第一个词。
- 复现（均合成）：provider JSON的message为`invalid parameter {"password":"alpha TOPSECRET omega"}`或`invalid parameter {'token':'alpha TOPSECRET omega'}`；`fromBytes`生成的保存DTO及`read`投影均仍含`TOPSECRET omega`。D-1日志两次`SECRET_RETAINED_STORED=true SECRET_RETAINED_API=true`。无引号`api_key=single-secret`控制值被正常移除。
- 实际写读链：`VerificationDiagnostics.kt:129`提取message→`DiscoveryVerificationWorker.kt:156`序列化诊断入`completeAttemptAndProgress`→`DiscoveryVerificationQueryService.kt:109`调用同一`read`。二次sanitize仍无法移除尾部。
- 影响：当供应方合法错误message引用带空格的密码/token值时，秘密片段进入attempt.response_json并可被详情API返回。该影响由当前代码复现，不推断生产已发生泄露。
- 最小修复范围：现有`VerificationDiagnostics.kt`与既有HTTP/Worker测试；不需规则、表结构、接口或新服务。

#### P2

**V-2（来源O-04-01）— 主计划必跑最终回归仍断言新run机构别名版本1。显式 mandatory，阻断PASS。**

- 要求：主计划验收要求最终IntegrationTest等四类实际运行且通过；子04 I-1明确新run ruleVersion=2/institutionAliasVersion=2，旧run缺setting才回退1。
- 当前证据：`DiscoveryVerificationIntegrationTest.kt:217–235`通过controller/scheduler/pipeline走真实Registration.begin创建四个新run；:235仍要求所有settings.institutionAliasVersion==1。C09R为exit1，33 tests/1 failure/0 errors/0 skipped；失败栈精确指向:235。`DiscoveryVerificationRegistrationService.kt:64–70`按批准契约设置版本2，不能回退产品默认去迎合旧断言。
- 根因与影响：新增默认版本后，必跑综合测试的对应新run预期未迁移，验收命令红灯。没有证据表明本项导致生产业务路径错误，因此定为明确强制的P2测试契约缺陷，而非假称P1生产回归。
- :78/:107的rule1断言属于旧settings缺失fixture，当前通过且必须保留；它们不能证明:235的新run断言正确。
- 最小涉及文件：`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt`，仅迁移已批准新run版本期望并保留原生命周期/账/无HTTP断言。该文件不在六个子计划或A1白名单，修改前需按主计划执行约束2获得子04白名单修订。

#### Observations

- 生产真实新增准入、召回率、264条历史原因、14项续查收益及203条最新投影/API对照：本轮未验证，不作修复效果承诺。
- `LEGACY_AGGREGATE_FLOOR`是批准兼容行为：既存匿名promoted=5、doc级可验证集合=1仍显示5；C07覆盖。这不构成5人均由投影台账证明。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-04-01，children/04/verify-log.md:66，经05/06继承 | 主计划最终IntegrationTest回归、04-I1新run版本2与旧run兼容 | 升级阻断V-2；当前33/1/0/0，exit1 | 历史记录笼统称new payload ruleVersion1；本次定位真实失败是`DiscoveryVerificationIntegrationTest.kt:235`的新Registration run institutionAliasVersion==1。:78/:107旧settings缺失fixture规则1正确并通过。保留原观察身份，不把局部旧run正确误作整个RECORD_ONLY已解决 |

### Evidence Boundaries

- 必需机器证据均已取得；初次Docker API错误经配置纠正、相关失败组完整重跑。C09R仍有真实断言失败，不归因环境、不豁免、不改测试后伪称本轮通过。
- 生产对照、真实部署后样本hash和人工交互验收仍PENDING；按主计划需部署/运维授权后执行，不影响本轮机器证据完整性，也不能被本轮测试替代。
- 只读新规则对照测试使用冻结合成输入；未把相同当前classpath测试冒称真实生产旧二进制收益对照。22个基线源码回执哈希已验证，历史生产class摘要为执行证据；此次未重新运行生产旧class评估。
- 没有产品或测试修改，未stage/commit/切分支。审阅产生的Maven target和`/tmp`诊断为临时输出；本审阅者没有新增任何仓库文件，包括repair.md。控制器review ledger另属控制器，不由本审阅者修改。最终边界检查`/tmp/apollo-aggregate-review/final-boundary-audit.json`：HEAD仍为3852a35，tracked diff/index diff均exit0，7个批准主/子计划哈希全部不变，repair.md不存在；status仅控制器已有review目录未跟踪。

## Repair Planning Result: BLOCKED — PLAN_AMENDMENT_REQUIRED

Baseline plan: `docs/plans/2026-10-21/apollo-repair-retention-master.md`，上列CONSISTENT身份及A1。
Verification result: **FAIL / INITIAL**。
Repair artifact: **N/A（未创建）**。指定唯一路径仍为`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/fix/apollo-repair-retention-master/repair.md`；不得创建替代或分轮计划。

### Included Findings / Handoff Classification

- V-1：**REPAIRABLE**。原批准隐私契约正确；最小根因组是现有脱敏实现及HTTP/Worker回归，涉及VerificationDiagnostics.kt、ApolloPeopleSearchClientTest.kt、DiscoveryVerificationWorkerTest.kt，均属原授权范围。
- V-2 / O-04-01：**PLAN_AMENDMENT_REQUIRED**。生产新run默认2符合子04契约；必跑总回归新run断言迁移需要未授权的DiscoveryVerificationIntegrationTest.kt。没有人类批准扩范围，因此不写含该文件的可执行repair.md，也不把它静默排除后声称已完成修复规划。

### Excluded Findings

- 生产收益/样本hash/人工验收：后续验收事项，不能转成代码修复。
- F-01/F-04-01原具体反例：已修复，保留历史，不重复开任务。

### Required Human Decision / Next Action

目标修订文件：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/2026-10-21/apollo-repair-04-identity-normalization.md`；现有白名单位于:54–68，明确仅9文件，且:45限定其余路径为回归边界。

请求批准最小子04修订：仅将`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt`加入子04白名单（9→10，仍符合主计划≤10）；仅允许把:235新Registration run的机构别名版本断言从1迁移为已批准的2，并保留旧run回退1、生命周期、owner归属、无HTTP等原断言。主计划业务行为与版本兼容不改，不改六子计划其他白名单；记录修订身份/理由/批准后，再针对V-1与V-2生成同一路径的单一修复计划供执行批准。

建议追加的精确授权文本（本轮仅报告，未写入计划）：

> 子04白名单新增第10项：`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt`。用途仅为迁移既有“manual keyword scheduled and continuous freeze options and bind actual positive execution IDs”测试的新run `institutionAliasVersion` 预期，从1改为已批准版本2；保留旧run回退1及其余生命周期、归属、请求、保护断言。文件总数9→10；不授权产品行为变更、测试删除/跳过或其他文件。修订按主计划执行约束第2条记录人工批准后生效。

依据：主计划执行约束第2条明确“每步只修改白名单文件；需要超范围文件时先修订对应子计划”。[repair-p SKILL.md](/Users/lukai/.agents/skills/repair-p/SKILL.md) Phase3要求“quote the conflict, propose the smallest amendment, explain its acceptance impact, and stop for approval.” 此处缺的是精确文件范围授权，非环境许可；本轮不会擅自修改计划、产品或测试。

No implementation was performed.

No product code was modified.


## Epoch 1 planning supplement — 2026-10-21T21:47:46+08:00

This is repair planning after exact human scope approval; it does not replace the immutable machine findings or create a new machine epoch. Product boundary remains `962fcfe924ad7562a4ea917d174180df82ac3b5c..462c52385bfe3b248016829a08a5b6ceb2403bb4`; machine result remains FAIL / INITIAL. No verification commands were repeated.

- Human instruction: 「批准 生成修复  repair」, 2026-10-21, approving the exact minimal child04 amendment proposed above and requesting repair generation.
- Approved A2: commit `17a80ba9f6178ab8c139366743607960277fe2c7`; child04 before SHA256 `1b1fb2670794126f2a7842988319d7b9796a244a4bd74c00e23a6cb111896b2b`, after SHA256 `d343e2ff9164b0f1ba91778f62540c64c47926cf13ee7605bf08967fff71ad48`. Adds IntegrationTest as file10 solely for specified new-run alias expectation1→2, preserving old-run fallback and all other assertions. Master/A1/product are unchanged.
- Evidence parent before next commit: `17a80ba9f6178ab8c139366743607960277fe2c7`.

## Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-10-21/apollo-repair-retention-master.md`, governing SHA256 `7b0113d6782083d3c86b910f74197bd39e3875f2018a93e68ef6d0f5554cfbda`, approved amendments A1 and A2.
Verification result: **FAIL / INITIAL**, Epoch1.
Repair artifact: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/fix/apollo-repair-retention-master/repair.md`.
Repair SHA256: `e208c94bcabe0b4a5a6d57accd5e76134f5f001df0a320b125b47efc217fb6da`.
Planner: `/root/apollo_retention_aggregate_review`.

### Included Findings

- V-1 / P1: REPAIRABLE; complete secret-value redaction plus HTTP→SQL→API regression. Authorized existing files: VerificationDiagnostics.kt, ApolloPeopleSearchClientTest.kt, DiscoveryVerificationWorkerTest.kt.
- V-2 / explicitly mandatory P2 / O-04-01: REPAIRABLE after approved A2; only migrate specified new-run institutionAliasVersion expectation1→2 in DiscoveryVerificationIntegrationTest.kt. Production default2 and old-run assertions remain.

### Excluded Findings

- Resolved F-01/F-04-01; retain lineage without reopening.
- Production gains, historical data operations, human acceptance and unrelated cleanup.

### Self-review

One plan, two root causes, four existing files. Exact environment, executor E1/E2, independent aggregate ten-command gate, concrete product/evidence subjects and durable handoff are specified. No tests run, product edited, or repair executed during planning. Prior PLAN_AMENDMENT_REQUIRED was a historical planning state now resolved by human A2 approval; defects remain unresolved until implementation and independent re-review.

### Required Human Decision

Approve and execute the exact generated repair with execute-p; request automatic same-task return to review-fast-p using committed repair-execution.md after READY_FOR_VERIFICATION. A2 approval and generation alone do not authorize execution of the newly written repair.

No implementation was performed.
No product code was modified.


## Epoch 2 — 2026-10-21T23:02:59+08:00

- Master plan: docs/plans/2026-10-21/apollo-repair-retention-master.md (sha256 7b0113d6782083d3c86b910f74197bd39e3875f2018a93e68ef6d0f5554cfbda)
- Governing master identity: worktree/invoked/fast-recorded same SHA256; CONSISTENT; approved child amendments A1 and A2 retained.
- Boundary: 962fcfe924ad7562a4ea917d174180df82ac3b5c..aba2430dabe1237f114708033b02776f83ee79a6
- Reviewer: /root/apollo_retention_recheck_epoch2 (fresh fork_turns=none, created after repair commit)
- Result: PASS
- Convergence: PROGRESSING
- Repair artifact/result: docs/plans/fix/apollo-repair-retention-master/repair.md / executed prior to this epoch; new repair planning N/A
- Repair evidence mode: DURABLE_HANDOFF
- Original complete reviewer report SHA256: 6bb05c2597746eba5358600932bd498511ed020e01e536b71ee965e97b6e45ba
- Evidence parent before this commit: 24cd7bc7099efaa65dc2e6aa6b157ba7783a73c5

## Verification Result: PASS

Plan: `docs/plans/2026-10-21/apollo-repair-retention-master.md`；phase=`aggregate/master`。
Implementation boundary: `962fcfe924ad7562a4ea917d174180df82ac3b5c..aba2430dabe1237f114708033b02776f83ee79a6`。
Convergence: **PROGRESSING**。
Manual acceptance: **PENDING**（主计划 A-1/A-2/A-3、全部子项及部署后生产对照）。
Reviewer: `/root/apollo_retention_recheck_epoch2`；独立于 fast-p writers/verifiers、Epoch1 reviewer/planner及本次修复执行者。
Mode: `AUDIT_ONLY`；主流程 `review-p`→`verify-p`，并应用项目要求的 `fix-v` 只读审计规则；没有机械修复授权或产品编辑。

10组必跑命令全部新鲜通过，失败/错误/必要跳过均0；V-1和V-2已解决，未发现新增强制违反。机器PASS不代替人类ACCEPT；人工验收仍待执行。

### Identity and Evidence Boundary

- 原仓库：`/Users/lukai/IdeaProjects/weibo-talent-introduction`；全部源码/Git/Maven在保留worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master`；branch=`fast/apollo-repair-retention-master`。
- governing/invoked/fast-recorded master SHA256均为`7b0113d6782083d3c86b910f74197bd39e3875f2018a93e68ef6d0f5554cfbda`，**CONSISTENT**；没有master amendment。
- 旧fast code head=`462c52385bfe3b248016829a08a5b6ceb2403bb4`；修复code head=`aba2430dabe1237f114708033b02776f83ee79a6`；本轮evidence HEAD=`24cd7bc7099efaa65dc2e6aa6b157ba7783a73c5`。完整base→当前code纳入审阅，code之后仅repair-execution.md；不把docs提交当产品边界。
- A1：child06批准commit `c337ee2aec8a7fc5f36584afeb0d00464ca5103e`，前态`27e5f15b5ee53234c9bcf06b7afa6057dfdee008`；主计划执行约束2；HUMAN「批准 继续」，2026-10-21；仅授权ExpertDiscoveryServiceTest构造迁移/mock，保留原断言。
- A2：child04批准commit `17a80ba9f6178ab8c139366743607960277fe2c7`；前hash=`1b1fb2670794126f2a7842988319d7b9796a244a4bd74c00e23a6cb111896b2b`，现hash见下表；主计划执行约束2；HUMAN「批准 生成修复  repair」，2026-10-21。仅新增IntegrationTest file10，指定新run alias断言1→2；旧run及其余断言保留。两个child现有字节均与批准commit完全一致。
- Fast ledger SHA256=`7ff79d501ff04d206bd82ae344dc4abc7bcb6172617994d5dd6beafab6c8f15e`；handoff SHA256=`b2fa58b6a552284c80102552dd1d28cca4fe222e29c54b01e67df03ab3c20d2c`；路径为`docs/plans/fast/apollo-repair-retention-master/{ledger,human-review-handoff}.md`。同目录baseline-freeze.txt及children/01–06各brief/execution/verify-log/fix-log均存在；24份child证据为历史证据，不替代本轮测试。
- Resolution=`DISCOVERED_FROM_GIT_WORKTREES`。控制器在原仓库cwd执行确切命令`python3 /Users/lukai/.agents/skills/review-fast-p/scripts/discover_fast_p.py --repo /Users/lukai/IdeaProjects/weibo-talent-introduction --master-plan docs/plans/2026-10-21/apollo-repair-retention-master.md`，唯一SELECTED，6 terminal children；完整选中头`/tmp/apollo-review-epoch2/controller-discovery-selected.json`。无关rejected列表transport截断，不声称为空。审阅者独立确认选中worktree/branch、全部hash、base/prior/A1/A2/repair→HEAD祖先exit0。审阅者从retained cwd额外误跑同一相对master命令，脚本把master解析为非repo-relative路径而返回NONE；该调用错误完整保存在discovery.json，不作为选中候选失效证据，也未替代正确SELECTED头。
- 规范性evidence E-6/E-7/E-8及完整code receipts已读；22/22基线源码回执hash与base Git内容匹配。当前所有受影响写入/读取路径另行审查。临时证据：boundary.json、scope.json、各Cxx.log、Cxx-reports、results.json、command-summary.json均位于`/tmp/apollo-review-epoch2/`。

| 顺序 | 计划（docs/plans/2026-10-21/） | 批准SHA256 |
|---|---|---|
| 01 | apollo-repair-01-error-retention.md | 290edb938b50bbb8859cc96db2d64fb1e3bacdcbc075186bd12e57448e32bb0b |
| 02 | apollo-repair-02-credit-recovery.md | 13e24fb1c83a1035ba58a902642833947755c7c79320fc5d474ddb7c819b6b16 |
| 03 | apollo-repair-03-search-trace.md | 3cfa9fad532edb7e462148b85a84dfa5ac6f6d85ed8641ff2f14835ed370ec53 |
| 04/A2 | apollo-repair-04-identity-normalization.md | d343e2ff9164b0f1ba91778f62540c64c47926cf13ee7605bf08967fff71ad48 |
| 05 | apollo-repair-05-decision-audit.md | de1aba525acca6e45b8ef434360f85fd79715944a46af36b18f33c44e42e1809 |
| 06/A1 | apollo-repair-06-promotion-reporting.md | bc32f14ea0db548fc4b81ea3459777f016da5f317aa1e6831c1365e3ea42e999 |

### Post-repair Authority and Delta

- Evidence mode: **DURABLE_HANDOFF**，`docs/plans/review/apollo-repair-retention-master/repair-execution.md` Epoch1，executor result=`READY_FOR_VERIFICATION`。
- Repair：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/fix/apollo-repair-retention-master/repair.md`；SHA256=`e208c94bcabe0b4a5a6d57accd5e76134f5f001df0a320b125b47efc217fb6da`，本轮未改。
- Approval source：handoff明确记录human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/fix/apollo-repair-retention-master/repair.md`，批准其当前hash及4个Authorized Files。executor runtime identity=`UNAVAILABLE`（可选元数据，如实保留，不阻断独立机器结论）。没有把规划者/审阅者冒充executor。
- 独立Git核对prior→repair的src delta恰以下4个文件；R-1只既有sanitizer及HTTP/SQL/API断言，R-2唯一hunk是指定断言1→2，无其他IntegrationTest改动。

| Repair Authorized File | 核实边界 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/VerificationDiagnostics.kt | 完整单/双引号秘密值匹配及保留引号替换；既有接口/分类/白名单/上限保留 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClientTest.kt | 既有测试扩展两组反例及无引号控制；存储/投影/历史污染投影、context断言 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt | 既有真实MySQL+Mock HTTP→Query链扩展两组反例；账、hash、分类及历史NULL断言保留 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt | 仅:235指定新run institutionAliasVersion==1→2；:78/:107旧run回退断言及其余内容不变 |

### Commands

新鲜环境检查exit0：JDK Zulu **11.0.32.1**，Docker client/server **29.8.2**，server最低API1.40。全部Maven从上述worktree串行运行，使用：

```text
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home
PATH=$JAVA_HOME/bin:$PATH
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock
JAVA_TOOL_OPTIONS=-Dapi.version=1.44
```

计数为tests/failures/errors/skipped。下列全部为本审阅者本轮执行；没有沿用executor或Epoch1结果。完整日志全部扫描、读取实际suite汇总及绑定JS结尾；XML在各自命令完成后逐类解析mtime/计数并保存快照。C03同时满足子02/03相同RepositoryIT命令；C09显式mysqlIt，避免IntegrationTest被条件跳过。

| ID | 完整命令 | 结果 / counts | 完成时间(+08:00) / 日志 |
|---|---|---|---|
| C01 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ApolloPeopleSearchClientTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest test` | PASS exit0；40/0/0/0 | 2026-10-21T22:26:06.813786+08:00；`/tmp/apollo-review-epoch2/C01.log` |
| C02 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationReplayToolTest,DiscoveryVerificationQueryServiceTest test` | PASS exit0；34/0/0/0 | 2026-10-21T22:30:50.064804+08:00；`/tmp/apollo-review-epoch2/C02.log` |
| C03 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationRepositoryIT test` | PASS exit0；17/0/0/0 | 2026-10-21T22:33:58.013031+08:00；`/tmp/apollo-review-epoch2/C03.log` |
| C04 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest test` | PASS exit0；59/0/0/0 | 2026-10-21T22:38:06.970810+08:00；`/tmp/apollo-review-epoch2/C04.log` |
| C05 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloMatchingV2Test test` | PASS exit0；72/0/0/0 | 2026-10-21T22:42:18.664012+08:00；`/tmp/apollo-review-epoch2/C05.log` |
| C06 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationReplayToolTest test` | PASS exit0；53/0/0/0 | 2026-10-21T22:46:57.008709+08:00；`/tmp/apollo-review-epoch2/C06.log` |
| C07 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryPromotionProgressServiceTest test` | PASS exit0；12/0/0/0 | 2026-10-21T22:49:32.404032+08:00；`/tmp/apollo-review-epoch2/C07.log` |
| C08 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationReportingIT test` | PASS exit0；12/0/0/0 | 2026-10-21T22:53:18.146708+08:00；`/tmp/apollo-review-epoch2/C08.log` |
| C09 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationIntegrationTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationAdmissionGateTest,ApolloMatchingV2Test test` | PASS exit0；33/0/0/0 | 2026-10-21T22:56:31.204976+08:00；`/tmp/apollo-review-epoch2/C09.log` |
| C10 | `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ExpertDiscoveryServiceTest,TaskProgressControllerTest,TaskExecutionControllerTest test` | PASS exit0；217/0/0/0 | 2026-10-21T23:00:20.431138+08:00；`/tmp/apollo-review-epoch2/C10.log` |

每组均完成绑定前端JS：1733 tests / 1733 pass / 0 fail / 0 cancelled / 0 skipped / 0 todo；node-check-app与node-check-task-modal-runtime均通过。完整10组日志哈希、行数及生命周期摘要见command-summary.json。重复运行数量不是新业务样本或独立人数。

| Group | Class | Tests/failures/errors/skipped | XML mtime(+08:00) | fresh |
|---|---|---|---|---|
| C01 | ApolloPeopleSearchClientTest | 13/0/0/0 | 2026-10-21T22:26:01.435463+08:00 | YES |
| C01 | DiscoveryVerificationWorkerTest | 15/0/0/0 | 2026-10-21T22:26:01.373419+08:00 | YES |
| C01 | DiscoveryVerificationQueryServiceTest | 12/0/0/0 | 2026-10-21T22:24:31.770894+08:00 | YES |
| C02 | DiscoveryVerificationWorkerTest | 15/0/0/0 | 2026-10-21T22:30:45.003322+08:00 | YES |
| C02 | DiscoveryVerificationReplayToolTest | 7/0/0/0 | 2026-10-21T22:29:15.145113+08:00 | YES |
| C02 | DiscoveryVerificationQueryServiceTest | 12/0/0/0 | 2026-10-21T22:29:15.319939+08:00 | YES |
| C03 | DiscoveryVerificationRepositoryIT | 17/0/0/0 | 2026-10-21T22:33:53.004821+08:00 | YES |
| C04 | DiscoveryVerificationWorkerTest | 15/0/0/0 | 2026-10-21T22:38:01.485973+08:00 | YES |
| C04 | DiscoveryVerificationRulesTest | 19/0/0/0 | 2026-10-21T22:38:01.717909+08:00 | YES |
| C04 | DiscoveryVerificationQueryServiceTest | 12/0/0/0 | 2026-10-21T22:36:28.536644+08:00 | YES |
| C04 | ApolloPeopleSearchClientTest | 13/0/0/0 | 2026-10-21T22:38:01.773344+08:00 | YES |
| C05 | DiscoveryInstitutionNormalizerTest | 9/0/0/0 | 2026-10-21T22:42:12.398485+08:00 | YES |
| C05 | DiscoveryVerificationRulesTest | 19/0/0/0 | 2026-10-21T22:42:12.379676+08:00 | YES |
| C05 | DiscoveryVerificationWorkerTest | 15/0/0/0 | 2026-10-21T22:42:12.225813+08:00 | YES |
| C05 | DiscoveryVerificationAdmissionGateTest | 8/0/0/0 | 2026-10-21T22:42:13.244081+08:00 | YES |
| C05 | DiscoveryVerificationQueryServiceTest | 12/0/0/0 | 2026-10-21T22:40:37.097072+08:00 | YES |
| C05 | ApolloMatchingV2Test | 9/0/0/0 | 2026-10-21T22:40:36.514410+08:00 | YES |
| C06 | DiscoveryVerificationRulesTest | 19/0/0/0 | 2026-10-21T22:46:50.769402+08:00 | YES |
| C06 | DiscoveryVerificationWorkerTest | 15/0/0/0 | 2026-10-21T22:46:50.534252+08:00 | YES |
| C06 | DiscoveryVerificationQueryServiceTest | 12/0/0/0 | 2026-10-21T22:45:22.571348+08:00 | YES |
| C06 | DiscoveryVerificationReplayToolTest | 7/0/0/0 | 2026-10-21T22:45:22.428604+08:00 | YES |
| C07 | DiscoveryPromotionProgressServiceTest | 12/0/0/0 | 2026-10-21T22:49:27.640261+08:00 | YES |
| C08 | DiscoveryVerificationReportingIT | 12/0/0/0 | 2026-10-21T22:53:11.676241+08:00 | YES |
| C09 | DiscoveryVerificationIntegrationTest | 12/0/0/0 | 2026-10-21T22:56:24.776661+08:00 | YES |
| C09 | DiscoveryVerificationCommitServiceTest | 4/0/0/0 | 2026-10-21T22:56:24.987601+08:00 | YES |
| C09 | DiscoveryVerificationAdmissionGateTest | 8/0/0/0 | 2026-10-21T22:56:25.810420+08:00 | YES |
| C09 | ApolloMatchingV2Test | 9/0/0/0 | 2026-10-21T22:55:51.086735+08:00 | YES |
| C10 | ExpertDiscoveryServiceTest | 193/0/0/0 | 2026-10-21T23:00:12.823919+08:00 | YES |
| C10 | TaskProgressControllerTest | 13/0/0/0 | 2026-10-21T23:00:15.110697+08:00 | YES |
| C10 | TaskExecutionControllerTest | 11/0/0/0 | 2026-10-21T23:00:12.834404+08:00 | YES |

独立当前编译类诊断D-1：JDK11 source-file模式运行`/tmp/apollo-review-epoch2/sanitizer-probe.java`，classpath取本轮C01 XML的java.class.path；exit0。两组原反例`invalid parameter {"password":"alpha TOPSECRET omega"}`、`invalid parameter {'token':'alpha TOPSECRET omega'}`及无引号api_key控制，分别断言新保存DTO、详情read、历史污染输入read均无秘密；context、REQUEST_INVALID、实际字节数及FULL语义均保留。`sanitizer-probe.log`三行全部`STORED_SECRET=false API_SECRET=false HISTORICAL_SECRET=false CONTEXT=true BYTE_COUNT=true CLASSIFICATION=true`。这是当前真实编译类的独立合成探针，不是生产数据或外部请求。

### Contract Matrix

所有路径相对选中worktree：S=`src/main/kotlin/com/weibo/talentintroduction/discovery/service/`；R=`src/main/kotlin/com/weibo/talentintroduction/discovery/repository/`；TS=`src/test/kotlin/com/weibo/talentintroduction/discovery/service/`；TR=`src/test/kotlin/com/weibo/talentintroduction/discovery/repository/`。每个机器行均Mandatory=YES；人工/部署后项按原合同PENDING，不伪装机器证据缺失。

| ID / Source | 强制要求 / 必需证据 | 判定 | 当前源码/新鲜运行证据 |
|---|---|---|---|
| M-I1 / master I-1、验收 | 基线、纯规则、Gate/Commit、真实HTTP分层；264/14不算收益，Nicolas最多1 | PASS | S/DiscoveryVerificationReplayTool.kt:296–320三类outcome、Gate/currentCommitProtection=NOT_EVALUATED、admissionWrites=0；TS/DiscoveryVerificationRulesTest.kt:100同一person去重；C05/C06；本报告无生产收益承诺 |
| M-I2-a / master I-2、E-6 | 原attempt不覆盖，恢复不迁run/batch、不清request_count；仅新HTTP计账 | PASS | R/DiscoveryVerificationRepository.kt:102–153恢复CAS只排队；:492–497真实预约；:529只完成未完成attempt；TR/DiscoveryVerificationRepositoryIT.kt:401；C02/C03 |
| M-I2-b / master I-2、E-7 | SELF、单姓名/同名拒绝、APOLLO_UNCLEAR、人工/身份/事实保护；准入仍Gate/Commit | PASS | S/DiscoveryVerificationRules.kt:88–176；S/DiscoveryVerificationAdmissionGate.kt:46–74；S/DiscoveryVerificationCommitService.kt:49–98；C05/C09 |
| M-I3 / master I-3 | 01→06顺序，各步≤10、白名单、仅一个diagnostics新根 | PASS | Git各child原实施7/9/9/9/9/5，差集0；approved04上限10；repair4文件独立；WorkerEvidence:24–32；scope.json |
| M-SCOPE / master非目标、E-7 | 无schema/ES/frontend/sample策略/发送路径更改；无新cache/log系统；无部署或历史批量操作 | PASS | 完整base..code非docs变更恰23授权src文件；其余路径无diff；本轮仅本地隔离容器/Mock transport/临时文件 |
| M-FREEZE / master执行约束1 | 实施前冻结Git/WAR/生产任务且保留原工作区 | PASS（历史执行证据） | fast ledger Baseline及baseline-freeze.txt记录base、WAR/class hash、27254 revision25790及1046聚合；原始全量冻结输出引用artifact://9，仓库文件含bounded输出；本轮不把此历史证据冒充最新生产状态 |
| M-ROLLBACK / master约束4–5 | 历史不自动升级；ERROR独立恢复；v2旧binary失败关闭，暂停worker保账 | PASS | Worker:202–218持久版本优先；Repository:43与:102独立入口；基线Rules只0..1，新规则0..2；Registration已有run直接返回；无自动apply调用 |
| 01-I1 / child01 | 422仅明确额度短语；参数/未知不同；auth/429/5xx保持 | PASS | S/VerificationDiagnostics.kt:115–130；ApolloPeopleSearchClient.kt:76–92；TS/ApolloPeopleSearchClientTest.kt:103；C01 |
| 01-I2-a / child01 | 非2xx≤16385字节，固定白名单/有界字段，非JSON不留原文 | PASS | ApolloPeopleSearchClient.kt:77；VerificationDiagnostics.kt:131–143；HTTPTest:130/:180；C01 |
| 01-I2-b / child01、V-1 | 秘密完整值和尾部不入SQL/API，保留有用context | PASS | VerificationDiagnostics.kt:93–104；HTTPTest:147；WorkerTest:278–339实际response_json/API；C01及D-1三组合成当前类probe |
| 01-I3 / child01 | 非成功response_sha256=NULL；真实byte hash/FULL/PREFIX；结构失败未知大小；历史NULL | PASS | Worker:156–163；Diagnostics:131–181；ApolloPeopleSearchClient.invalid:183；HTTPTest:180/:193；C01 |
| 01-X / child01、E-8 | Mock HTTP→真实Worker/MySQL attempt→详情，一请求只记1次 | PASS | TS/DiscoveryVerificationWorkerTest.kt:278–339；R/Repository:633同事务；C01 |
| 02-I1 / child02 | DETAIL额度故障RETRY_WAIT、outcome NULL/route NONE；保游标、不增transient | PASS | Worker:193–196；R/Repository:578–590；WorkerTest:379；C02 |
| 02-I2 / child02 | 持久单探测1800秒，重启/双worker/crash窗保持；旧晚到成功不解除；SEARCH/ORCID/投影继续，无500/12恢复 | PASS | R/Repository:461–518同连接GET_LOCK至COMMIT、current locking reads；Worker:58–63/:443；TR/RepositoryIT:324–377；WorkerTest:379；C02/C03 |
| 02-I3 / child02 | settled ERROR422、manifest/整项hash/admission CAS、身份/人工/投影保护、≤100；仅有效APOLLO_SEARCH原游标 | PASS | R/Repository:102–183；ReplayTool:198直接原evidence，未走expected DECIDE；RepositoryIT:401/:423；ReplayToolTest:158；C02/C03 |
| 02-I4 / child02 | 历史attempt/hash/归属/计数不变；重复apply无副作用；工具无HTTP | PASS | Repository:111–153；ReplayTool:198–211 queue-only；C02/C03 |
| 02-CLI / child02 | apply-errors仅绝对manifest/hash；0700/0600；receipt枚举、业务拒绝；错误exit2不泄密，旧apply不变 | PASS | ReplayTool:42–87/:174–211；TS/ReplayToolTest:111–153/:158；C02 |
| 03-I1 / child03 | 页5截断后续查，最后空页不洗掉不完整；无页6/额外DETAIL，PENDING | PASS | Worker:279–342；Rules:18–27/:115；WorkerTest:470三种中断场景；C04 |
| 03-I2 / child03 | 固定queries、finishedQueryKeys为业务事实；页响应/trace/游标原子，重启无漏页 | PASS | Worker:264–335，Repository:633；WorkerTest:470前/后ACK/legacy页6重启；C04 |
| 03-I3 / child03 | 仅同run/person已完成200 OK可解析详情复用；来源attempt/hash、无虚构HTTP，冲突仍拒绝 | PASS | Repository:662–687；Worker:348–406共用mergeDetail；RepositoryIT:448；WorkerTest:580后复用/跨run/错误/冲突；C03/C04 |
| 03-I4 / child03 | diagnostics可空、有界32/200+total/truncated；不作准入authority；samples保持 | PASS | Diagnostics:27–86；Query:116；Gate:65仅业务DTO；WorkerTest移除diagnostics后真实Gate相同；C04/C05；sample源无diff |
| 04-I1 / child04 | 新run rule2/alias2；旧run缺setting首次payload1、缺payload字段0；未知失败关闭，Worker/Gate/Query一致 | PASS | Registration:64–70；Worker:202–218；Rules:43；WorkerTest:22及:101–136版本矩阵；C05/C09 |
| 04-I2 / child04 | 仅有来源TUM/Geneva、版本隔离、完整主体对应、明确ID冲突不覆盖、不误写TUM | PASS | Normalizer:280–313；Rules:158–169；NormalizerTest:17；C05 |
| 04-I3 / child04 | 精确Assistenzprofessor+明确headline前缀/负面保护；原title/等级、姓名唯一性及邮箱路线保持 | PASS | Rules:318–324及emailNameMatches；Worker:322/:370传版本；RulesTest:100/:136；C05 |
| 04-I4 / child04 | Nicolas两路合并只1；规则建议不直接入admission，Gate/Commit保护独立 | PASS | RulesTest:100；WorkerTest:22真实SQL→Gate→Commit→Query；Commit:49–98；C05/C09 |
| 05-I1 / child05 | trace同次真实分支、短路未执行NOT_EVALUATED；Worker持久，Query不重算冒充 | PASS | Rules:48–72/:122–177/:285–337；Worker:166–174；Query:116仅saved RECORDED；C06 |
| 05-I2 / child05 | 业务全集判定后trace≤200、命中优先、total/truncated/version/time/hash/refs；导出有界权限 | PASS | Rules:58–72/:138–155；Diagnostics:53–86；RulesTest:35验证201及chosen index200；ReplayTool:128–170；C06 |
| 05-I3 / child05 | 旧trace不补RECORDED；重算RECOMPUTED；缺正文NOT_RECORDED；规则/Gate/保护分列 | PASS | Query:116；ReplayTool:303–319/:327–367；QueryTest:22；C06 |
| 05-CLI / child05 | 显式frozen-rules target2/2；原CLI兼容；冻结facts不跑Parser/外部；不可变before/manifest、固定time及class hash | PASS | ReplayTool:54–87/:100–113/:262–367；ReplayToolTest:17–98及WorkerTest:22端到端；C06；生产真实旧classpath收益仍未宣称 |
| 06-I1 / child06 | owner+activated+ENFORCE+revision+PROJECTED；doc去重、最新合格source；其余状态0 | PASS | Repository:189–203；TR/ReportingIT:109–136真实SQL条件与重复source；C07/C08 |
| 06-I2 / child06 | legacy ES时间窗保留；排除managed非PROJECTED；按doc union且managed source优先 | PASS | PromotionProgressService:73–109；ServiceTest:136–160；ReportingIT:137–207；C07/C08 |
| 06-FLOOR / child06步骤3、I-1 | 原匿名aggregate floor max保留；显示高于可验证集合必须明示 | PASS | Service:118–120；ServiceTest:183示例verified1、原5、显示5，差4标LEGACY_AGGREGATE_FLOOR；不是额外4人投影证明；C07 |
| 06-I3 / child06、E-7.4 | 两refresh同count；运行中/完成两个HTTP入口一致；重复读0 SQL/ES写，故障保留原结果 | PASS | Service:37–70/:135；ReportingIT:137–207真实MySQL→双Controller及无写快照；C07/C08/C10 |
| A1 / master约束2+human amendment | ExpertDiscoveryServiceTest仅构造迁移/必要mock且保断言 | PASS | Git base..code该文件仅import、empty-ledger mock/stub、构造参数；TS/ExpertDiscoveryServiceTest.kt:441/:468；C10；符合先修订白名单再实施 |
| A2 / master约束2+human amendment | IntegrationTest指定新run alias预期1→2，旧run及其他断言不动，child≤10 | PASS | prior..repair该文件唯一hunk:235；旧:78/:107不变；C09实际执行所有12 Integration tests；符合批准范围 |
| R-SCOPE / approved repair | prior..repair仅4 Authorized Files，无额外接口/规则/恢复/读统计更改 | PASS | boundary.json/scope.json及完整四文件diff；repair hash未变；其余产品delta0 |
| M-COMMAND / master验收+repair复验 | 全部定向/IT/最终交互命令新鲜执行，失败/错误/必要skip0、JS绑定完成 | PASS | 下列C01–C10逐类新鲜XML/日志；C03重复命令去重映射两个child，未跳过任何不同命令 |
| M-A1 / master A-1、child01/02人工项 | 额度/未知422/429可辨；重启冷却DETAIL0、单探测200恢复，其他通道/旧账保持 | PENDING | 必须人类实际操作/记录；本轮C01/C02/C03不替代人工签收 |
| M-A2 / master A-2、child03/04/05人工项 | 续查仍保守，Nicolas只1，姓名/同名保护、SELF/APOLLO_UNCLEAR、旧版本/trace/固定对照 | PENDING | 必须人类实际操作；机器C04/C05/C06/C09不替代人工 |
| M-A3 / master A-3、child03/05/06人工项 | 自动准入2/晋升1，双入口一致；旧缺正文NOT_RECORDED、原sample hash、刷新0写 | PENDING | 必须人类操作/样本实物；机器C07/C08/C10不替代人工 |
| M-PRODUCTION / master部署后对照、child06 A-3 | 新冻结27254→小量恢复→额度/错误→完整新run，真实样本hash及收益分层 | PENDING | 原合同要求部署/运维授权后实施；本轮无生产访问，无真实provider请求；203等仅历史基线 |

### Runtime / Write-Read / Lifecycle Audit

- `Registration.begin`新run冻结rule2/alias2，现有run优先；Worker首次payload读取run并校验，重启从保存payload/queryIndex/page继续。Rules/Gate/Query消费相同冻结业务版本，未知版本失败关闭；diagnostics单独可丢弃。实际旧0/1、新2、未知3矩阵及Nicolas/保护入口由C05/C09验证。
- `reserveAttemptWithBudget`持同连接Apollo GET_LOCK至事务结束；DETAIL冷却读取持久fault/probe/success，1800秒跨进程不清零；HTTP在预约后、事务外执行。`completeAttemptAndProgress`一次提交attempt+evidence+下个游标；失败不推进未确认页，过期lease/token/generation不能写；Worker finally取消heartbeat，close关闭executor。C02/C03/C04/C09包含restart/concurrency/CAS/late-success/前后ACK测试。
- 主要状态：RESERVED→QUEUED→RUNNING→COMPLETED或ERROR；额度/429/瞬态/预算延后RUNNING→RETRY_WAIT→到期claim；崩溃RUNNING→lease过期claim；仅settled/frozen/eligible ERROR422→queue-only CAS→QUEUED。旧PENDING replay仍独立DECIDE，不作为ERROR恢复。投影PENDING/ERROR重试不重做provider；终态PROJECTED/ALREADY_PRESENT/APPLICATION_PRESENT不重复记新事件。
- Commit先读RAW/保护，再SQL锁run/admission/item；核对身份/事实/人工及revision，调用共享Gate后持久真实admission revision，再异步ES投影并CAS状态。新诊断、复用、离线报告不新增admission/ES写入口。诊断read只返回白名单，报告明确纯规则/Gate/真实Commit的证据等级。
- verification三表SQL集中原Repository；新增恢复沿原事务保护，新增lookup/reporting只读。E-7既有admission/ES、enrichment-job生命周期、task writer、发送/重验、sample store、migration/mapping均未改。PromotionProgress从ledger与legacy集合形成doc union，两个read返回copy，读取失败保留原结果；不回写resultSummary/progress/ES。
- Accumulation/state-machine/cross-plan/no-extras/scope：PASS；deleted-code N/A（无删除公共路径）；知识条目只作探针，无写回或共享规则提升。

### Cumulative Authorized File Scope

原六步实际文件数7/9/9/9/9/5，逐步白名单差集0；原去重22，A2新增IntegrationTest后累计**23**，均在批准child白名单内。修复4文件为累计集合子集；没有其他非docs变更。

- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerification.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClient.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizer.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/VerificationDiagnostics.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationReportingIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClientTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGateTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayToolTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`

### Finding Lineage

| Finding | State | 本轮证据 |
|---|---|---|
| V-1 / P1 | RESOLVED | 原双/单引号含空格反例，当前matcher完整消费；C01实际SQL/API及D-1保存/历史投影无秘密尾部，有用context保持 |
| V-2 / mandatory P2 / O-04-01 | RESOLVED | A2精确指定断言迁移；C09新鲜实际IntegrationTest全12及最终四类组；旧run断言保留 |
| F-01（历史child01） | RESOLVED | 原quoted-key单词反例仍在HTTPTest:147，同一C01/C04真实执行；不把V-1另根因抹去历史 |
| F-04-01（历史child04） | RESOLVED | WorkerTest:33显式ENFORCE，production AUDIT默认未改；C05完整SQL→Gate→projection→Query和冻结矩阵 |

V-1的原根因是带引号值按空白截断，完整秘密尾部残留SQL/API；当前完整引号matcher及共享read已消除该具体违反。C01有实际SQL/API反例断言，D-1另行当前编译类确认，原无引号/quoted-key回归保留。V-2仅在批准A2内迁移已明确新run版本，C09从Epoch1的33/1/0/0变为本轮33/0/0/0。原两个阻断均消除且无同级/更高新增回归，故PROGRESSING；不依据单纯finding数判收敛。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-04-01，child04:66；05/06继承 | master最终IntegrationTest、04-I1新run版本2/旧run兼容 | RESOLVED，原阻断V-2关闭 | 历史记录误概括为payload rule1；确切目标是IntegrationTest:235新Registration run alias版本。A2仅该断言1→2，旧:78/:107保留；C09真实执行 |
| O-06-01，child06/verify-log.md:34（handoff索引遗漏，独立扫描发现） | master A-1/A-2/A-3与部署后27254核对、I-1证据等级 | PENDING人工/生产；非本轮机器阻断 | 规范把生产对照限定部署授权后；本轮fixture/Mock ES不能冒充203生产人或人工ACCEPT；必须后续人类验收 |
| O-06-02，child06/verify-log.md:35（handoff索引遗漏） | child06步骤3/master I-1保留历史floor且分开doc证明 | PASS兼容观察保留 | C07实际floor case verified1/display5/difference4；代码max旧aggregate，未冒充额外投影4人 |

### Findings

#### P1

N/A — 当前无未解决P1。V-1保留于谱系并标RESOLVED。

#### P2

N/A — 当前无未解决mandatory P2。V-2/O-04-01保留于谱系并标RESOLVED。

#### Observations

- O-06-01保持待人工/生产验收；O-06-02为批准的匿名聚合下界兼容。未重开已批准决策，也不增加可选清理/硬化任务。
- 真实新增准入/召回率、264条全部错误根因、14条续查收益、27254最新203投影/API、真实论文样本hash：本轮未验证，不能从本地测试推断。

### Evidence Boundaries

- 所有必需机器证据已取得；无未执行的必跑命令、过期XML、必要跳过或未解决基础设施失败。Maven输出完整扫描、suite/JS/syntax结尾均核对；未使用旧report补齐。

- 本轮只按批准的10组命令核验；没有声称执行仓库全部JVM测试、WAR打包、生产旧二进制真实人群收益对照或人工签收。项目默认全量build命令不替代本次明确计划命令。
- 官方发现头来自控制器；审阅者已独立复核身份/hash/Git边界。executor runtime identity未提供为可选UNAVAILABLE；executor自测日志只作历史输入，全部必跑命令本轮另跑。
- 审阅者没有写machine-verification.md、review ledger、repair.md、计划、知识、产品、测试、index或commit。仅Maven瞬时target及/tmp传输证据。最终final-boundary.json确认HEAD/branch未变、产品/index干净；唯一tracked修改仍为控制器既有review ledger。

Repair planning: N/A — verify-p为PASS，按review-p路由不运行repair-p；原repair.md保持既有批准内容，无新修复计划或自动修复轮次。

### Next Action

- 对同一最终产品SHA `aba2430dabe1237f114708033b02776f83ee79a6`执行待完成的人类验收并明确ACCEPT/REJECT；控制器按Epoch2持久化本报告。机器结果不授权merge、push、部署或生产恢复。

No product code was modified.


### Controller Evidence Check — Epoch 2

Complete independent report above preserved without alteration. Controller independently parsed all ten fresh command results, all selected suite counts/mtime flags, lifecycle summaries and log hashes; final product/index clean and unchanged HEAD confirmed. No product review substituted for the independent reviewer.

| Command | Exit | Tests/failures/errors/skipped | Log SHA256 | JS pass/fail/skipped | Syntax checks |
|---|---:|---|---|---|---|
| C01 | 0 | 40/0/0/0 | bed80bc9fc735d781134207218ac78dee2f3fd1075874562359f043e995ef0b8 | 1733/0/0 | app + task-modal-runtime PASS |
| C02 | 0 | 34/0/0/0 | 3774938d80d040a0bbb156f3a6c21c00a898c820270694b3bd8fc3d036b78559 | 1733/0/0 | app + task-modal-runtime PASS |
| C03 | 0 | 17/0/0/0 | 2cfc3a11d0c1ddd6dadab2c669c2d8c975a8b64706814dc2eeae6ff6ea22274f | 1733/0/0 | app + task-modal-runtime PASS |
| C04 | 0 | 59/0/0/0 | be2c54ad5c9ee0a6a17c02c449844290800d383d4c1bc694e3a222d6b04fadd3 | 1733/0/0 | app + task-modal-runtime PASS |
| C05 | 0 | 72/0/0/0 | 6ff426f476369f7b2da2b2d3b29600207af9de602a2a26c6eacbff65f93084a5 | 1733/0/0 | app + task-modal-runtime PASS |
| C06 | 0 | 53/0/0/0 | cb5b55680f0d8369cccb16ccfdfeaa9c9db7bd8764392088bcecd1ddc748e9b0 | 1733/0/0 | app + task-modal-runtime PASS |
| C07 | 0 | 12/0/0/0 | 96e09e06c39453fbffa27dfc74fda0f77627e134c7ab4f989d7b92f0a27d7c12 | 1733/0/0 | app + task-modal-runtime PASS |
| C08 | 0 | 12/0/0/0 | 698242ec8b8cfd05afa50c088361902bb622de8954e99cb58de742ffaf5916db | 1733/0/0 | app + task-modal-runtime PASS |
| C09 | 0 | 33/0/0/0 | 7e14ff4dd1e1dd8350c65380b6aeb6ec794bc84839512f4f72291b27f791dedc | 1733/0/0 | app + task-modal-runtime PASS |
| C10 | 0 | 217/0/0/0 | 72945e7f942351b3b1d0dc1bdb1c09cddfafe1c718bfa771e7ab8f90941817f9 | 1733/0/0 | app + task-modal-runtime PASS |
