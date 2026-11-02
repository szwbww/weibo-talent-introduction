# Aggregate Machine Verification — paper-reprocess-master

## Epoch 1 — 2026-11-02T21:05:48+08:00

- Master plan: docs/plans/2026-11-01/paper-reprocess-master.md
- Governing master identity: sha256 85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e; recorded commit 53c2b0018e239e3c30db25942e99c2cddc69e9d4
- Master identity state: AMENDMENT_RECORDED; A1–A13 (exact rule/reason/approval and full invoked/governing diff retained in ledger)
- Boundary: 3a425461dbe6088e4d4dda1a53141416a392670c..e213acb50791fe39a0de0a4d9d599bc4e2398cc1
- Reviewer: /root/paper_reprocess_aggregate_epoch1; fork_turns=none; resumed attempt 2 in same epoch
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/paper-reprocess-master/repair.md; DRAFT_READY; sha256 47b6bccdb128dd02177fe0d629e251afe781064d75dcbc5f18c07ab24a829110

Complete independent review-p output follows without omission.

## Verification Result: FAIL

Plan: [paper-reprocess-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-master.md)  
Requested phase: aggregate/master  
Implementation boundary: `3a425461dbe6088e4d4dda1a53141416a392670c..e213acb50791fe39a0de0a4d9d599bc4e2398cc1`  
Evidence HEAD: `73f77441f45f6b1c949a167a60e7b155d58dd025`  
Reviewer: `/root/paper_reprocess_aggregate_epoch1`，同一 epoch，usage-limit 后恢复 attempt 2。  
Convergence: **INITIAL**  
Manual acceptance: **PENDING**

必跑门禁最终通过，但独立源码、SQL、API、浏览器探针确认 **5 项 P1**。子计划 LIGHT_PASS 未作为整体验收结论。

### 身份与授权

- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`
- Branch：`fast/paper-reprocess-master`
- Governing master：SHA-256 `85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e`，记录 commit `53c2b0018e239e3c30db25942e99c2cddc69e9d4`。
- Invoked master：SHA-256 `d1e17452a5484eae56b37bca5671e8c767fd3f744594232e4b57dac4826b1aa6`。
- Master identity state：**AMENDMENT_RECORDED**。已独立读取两份 master 的完整 diff。
- Fast-p ledger SHA-256：`dc2d86b0f225fbcec9f86a2bfa5f089cc3075fe541881dd5a8ceb88689274e09`。
- Handoff SHA-256：`ee33dc68d01e4682dfec0fc0edd3607b42587832811c08d453414371cc2ab155`。
- 00/01/06 当前计划 SHA 与输入一致；02/03/04/05 当前文件分别逐字等于记录的 `9ce385c`、`a6e14b6`、`5a31faa`、`d678780` 版本。
- base、final code、recorded master 均为当前 HEAD 祖先。
- `3f042fc` 为报告身份/门禁措辞修正，不计入产品边界。

A1–A13 共同记录的批准原文：

> HUMAN:继续 后续授权都继续 直到所有子计划都完成且可以运行review-fast-p的阶段 不要执行review-fast-p，我会交给其他agent执行 — 2026-11-01T23:28:37+08:00

以下保留各 amendment 的规则与原因；批准均为上述同一原文。

| Amendment | Master rule | Recorded reason |
|---|---|---|
| A1 | I-4/I-6/X-4 and 顺序与门禁 | Approve child 02 eleven-file exception for existing persisted-admission seam without reducing tests |
| A2 | I-4/I-6/X-4 | Authorize narrow DiscoveryReviewService controlled changed-identity seam and existing-test regressions |
| A3 | I-2/I-5/X-1/X-5 and 顺序与门禁 | Allocate unused V173 because existing V172 is occupied; preserve applied migration, schema, scope count and acceptance |
| A4 | I-4/I-5/X-1/X-5 and 顺序与门禁 | Authorize child03 cap13 for existing parser-chain page cancellation/budget checkpoints only |
| A5 | I-4/I-5/X-1/X-5 | Add three proven parser checkpoint seams, preserve default behavior/rules/versions, retain all acceptance |
| A6 | I-5/I-7/X-6 and 顺序与门禁 | Authorize child03 cap14 for directly broken task catalog contract tests, no acceptance reduction |
| A7 | I-5/I-7/X-6 | Add affected TaskExecutionSummaryExtractorTest and covering command; retire incidental fixed lists/count instead of re-pinning |
| A8 | I-4/I-5/I-6/X-3/X-4/X-7/X-8 and 顺序与门禁 | Child04 cap13 for durable receipts and original consume/ordinary-worker parent permission seams |
| A9 | I-4/I-5/I-6/X-3/X-4/X-7/X-8 | Add three evidenced necessary receipt/owned-consume/epoch0 worker fences; preserve ordinary defaults and all acceptance |
| A10 | I-5/I-7/I-9/X-5/X-6/X-8/X-9 and 顺序与门禁 | Child05 cap19 for required existing readonly opener, authoritative summary/preview and directly broken resource tests |
| A11 | I-5/I-7/I-9/X-5/X-6/X-8/X-9 | Add readonly API totals/source-missing preview and original8-step opener; retire incidental resource inventories, retain exact CSS/DOM/browser acceptance |
| A12 | I-1/I-3/I-6 and 顺序与门禁 | Child06 fifth-file exception for existing DiscoveryVerificationWorkerTest Flyway fixture target V161→V173 |
| A13 | I-1/I-3/I-6 | Raise existing migration fixture target only; assertions/business behavior/12-target/migration/report acceptance unchanged |

A12 governing identity：`2de159efc6b04e4f8dc3542890009eba15f004cf` → `53c2b0018e239e3c30db25942e99c2cddc69e9d4`。A13 的 child06 SHA 为 `ea13bb67c166e8cb541cc068d1367432497ed0fb3f303b61c3ba52d2f4493791` → `ac6a7663526a7a5c3a74716a3be3b54db97b8afaa6b5576672d8b65f655a6bb2`。Master 顺序表 06 仍写 4；其第五文件按明确 A12/A13 批准处理，未静默豁免。

完整累计边界共 120 个变更文件；其中产品、测试、脚本和审计产物 61 个，均在批准集合。逐 child 实际数量为 **6/7/11/14/13/19/5**，各自无未授权文件。

### Commands

所有 Maven/Java 命令使用：

`JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`

Maven executable：`/opt/homebrew/bin/mvn`。真实 MySQL 为独立 Testcontainers MySQL 8.0.36；需要 Docker/所选 worktree 临时输出时使用已授权 escalation。

| ID | Command | Result | Evidence |
|---|---|---|---|
| C1 | `mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,SourceAuthorEmailResolverTest,DiscoveryPaperLayoutRegressionTest,DiscoveryPaperAnalysisServiceTest,RetainedPaperIdentityServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest,TaskExecutionSummaryExtractorTest,DiscoveryPaperReprocessApplyServiceTest,DiscoveryPaperBackfillServiceTest,DiscoveryRecheckServiceTest,DiscoveryVerificationIntegrationTest -DmysqlIt=true -DpaperReprocessIt=true` | **最终 PASS** | 首轮 exit **1**：203/0 failures/**1 error**/1 skip；完整重跑 exit **0**：203/0/0/1，981.23 秒。日志 [首轮](/tmp/paper-aggregate-kotlin.log)、[完整重跑](/tmp/paper-aggregate-kotlin-rerun.log)。 |
| C2 | `mvn test -Dtest=DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | PASS | exit 0；2/0/0/0；279.29 秒。[日志](/tmp/paper-aggregate-e2e.log) |
| C3 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | PASS | exit 0；48/0/0/0；836.91 秒。真实空库及 V170→V173；94 次 schema clean、93 次成功迁移调用。[日志](/tmp/paper-aggregate-flyway.log) |
| C4 | `mvn package -DskipTests` | PASS | exit 0；209.31 秒；WAR 构建成功，Maven-bound JS 2035/2035。此命令未运行 Kotlin 测试。[日志](/tmp/paper-aggregate-package.log) |
| C5 | `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js` | PASS | exit 0；44/44。[日志](/tmp/paper-aggregate-js-focused.log) |
| C6 | `node --test src/test/js/*.test.js` | 最终 PASS | 普通 sandbox 首轮 exit 1，2034 pass/1 fail：写 `target/discovery-plan-acceptance/08.html` 被 EPERM 拒绝。escalated 重跑 exit 0，2035/2035、354 suites。[首轮](/tmp/paper-aggregate-js-all.log)、[重跑](/tmp/paper-aggregate-js-all-escalated.log) |
| C7 | `python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py` | PASS | exit 0；PERSON 333、PAPER 244、PAPER_BINDINGS 337、prior_known 7、other_same_name 1；脚本无输出文件写入。 |
| C8 | `/usr/bin/time -l /Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json /tmp/paper-aggregate-replay-measured.json` | PASS | exit 0；337 行，networkCalls=0、businessWriteServicesConstructed=0；最大 RSS 1,007,583,232 bytes。首轮 Java 已完成，但 `time -l` 的 sandbox sysctl 被拒导致 command exit 1，已保留；测量重跑为 exit 0。[测量日志](/tmp/paper-aggregate-replay-measured.log) |
| C9 | `python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json --old-replay /tmp/paper-reprocess-30711-baseline.json --new-replay /tmp/paper-aggregate-replay-measured.json --audit-directory docs/audits/2026-11-01-discovery-30711-pdf --csv /tmp/paper-aggregate-reconciled.csv --report /tmp/paper-aggregate-reconciled.md --runtime-evidence /tmp/paper-reprocess-30711-runtime.json` | 正常输入命令 PASS；验证器存在 V-3 | exit 0；337 行、extras=0；51 EXPECTED_MATCH、286 EXPECTED_MISSING；当前固定输入无丢失或新冲突。[CSV](/tmp/paper-aggregate-reconciled.csv) |
| C10 | JDK11 独立 JVM：`-DpaperReprocessIt=true -DmysqlIt=true -cp` 实际完整 [classpath](/tmp/paper-aggregate-classpath.txt)，主类 `com.weibo.talentintroduction.discovery.service.Paper03RuntimeSmoke` | PASS | exit 0；82.30 秒；真实 Tomcat/API/MySQL，121 记录、7 页、幂等、401、零 provider/归档变更。[日志](/tmp/paper-aggregate-runtime03.log) |
| C11 | 相同 JDK/classpath，主类 `com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --paper-reprocess-smoke` | PASS | exit 0；484.82 秒；`PAPER04_RUNTIME_OK`，consumer/recheck/worker/commit/projection/旧 CLI；paidCalls=0。[日志](/tmp/paper-aggregate-runtime04.log) |
| C12 | 相同 JDK/classpath，主类 `com.weibo.talentintroduction.discovery.service.DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke` | PASS | exit 0；81.17 秒；`PAPER06_RUNTIME_OK`，真实 SQL、loopback ES CAS、真实服务、无生产写。[日志](/tmp/paper-aggregate-runtime06.log) |
| C13 | 独立 SQL 诊断 `AggregateCancelProbe`，源码 [诊断类](/tmp/AggregateCancelProbe.java) | **FAIL 证据：V-1** | 诊断进程 exit 0；取消后同父任务 retry 得 COMPLETED，NEW=1、admitted=0、NOT_EVALUATED=1、pending=0。[结果](/tmp/paper-aggregate-cancel-probe.log) |
| C14 | `node /tmp/paper-aggregate-ui-probes.js` | **FAIL 证据：V-2/V-7** | exit 0；真实组件 DOM + 提取的实际 app Escape handler：hostCloseCalls=1；完成任务仍 startNewDisabled=true。 |
| C15 | reconciliation 独立反例诊断 | **FAIL 证据：V-3** | wrong-doc、AMBIGUOUS_TARGETS、缺 outcome、带 wrong-doc 的 SKIPPED 均 EXPECTED_MATCH；冲突 replay 名称被当普通 EXPECTED_MISSING。[逐例结果](/tmp/paper-aggregate-reconciler-probes.json) |
| C16 | `node /tmp/paper-aggregate-browser-final.js`，修正临时 servlet UTF-8 后执行 | **混合：布局/实际运行通过；V-2/V-4 失败** | exit 0；真实 HTTP/MySQL AUDIT 121/121、actuals=0、候选 1/1；空态、运行、500、完成、六步截图；真实 Chrome Escape 关闭宿主，完成后仍显示目标形成中。[日志](/tmp/paper-aggregate-browser-utf8-final.log) |
| C17 | `node /tmp/paper-aggregate-browser-zoom.js` | PASS | exit 0；1440/390 physical width，100%/125% 对应 CSS viewport 1440/1152/390/312，按钮均可达、dialog 不越界。[指标](/tmp/paper-aggregate-browser-zoom-metrics.json) |
| C18 | `node /tmp/paper-aggregate-crosspath-browser.js`，真实服务/控制器/SQL与原 DOM/模块 | **FAIL 证据：V-5；其余路径通过** | exit 0；NEW 八步 GET404/0 步；REPAIRED GET200/8 步；原文 GET200，analysis POST=0。[日志](/tmp/paper-aggregate-crosspath-browser.log)、[HTTP](/tmp/paper-aggregate-crosspath-http.json) |
| C19 | 批准文件集合、逐 child diff、CSS/DOM、`git diff --check ... -- src scripts docs/audits`、最终 Git 检查 | PASS | 61 个产品/测试/审计文件全部授权；CSS 原前缀不变、S-5 字节一致（5515 bytes）；产品 diff/check、index 均空，HEAD 不变。 |

C1 完整重跑逐类计数：

| Class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| DiscoveryPaperIdentityReplayToolTest | 6 | 0 | 0 | 0 |
| SourceAuthorEmailResolverTest | 30 | 0 | 0 | 0 |
| DiscoveryPaperLayoutRegressionTest | 8 | 0 | 0 | 1 |
| DiscoveryPaperAnalysisServiceTest | 10 | 0 | 0 | 0 |
| RetainedPaperIdentityServiceTest | 7 | 0 | 0 | 0 |
| DiscoveryVerificationWorkerTest | 24 | 0 | 0 | 0 |
| DiscoveryVerificationCommitServiceTest | 7 | 0 | 0 | 0 |
| DiscoveryVerificationQueryServiceTest | 22 | 0 | 0 | 0 |
| DiscoveryPaperReprocessServiceTest | 6 | 0 | 0 | 0 |
| DiscoveryPaperReprocessRepositoryIntegrationTest | 12 | 0 | 0 | 0 |
| TaskExecutionSummaryExtractorTest | 19 | 0 | 0 | 0 |
| DiscoveryPaperReprocessApplyServiceTest | 2 | 0 | 0 | 0 |
| DiscoveryPaperBackfillServiceTest | 4 | 0 | 0 | 0 |
| DiscoveryRecheckServiceTest | 10 | 0 | 0 | 0 |
| DiscoveryVerificationIntegrationTest | 36 | 0 | 0 | 0 |

XML 确认 `java.version=11.0.32.1`、两个 opt-in 均 true。[重跑 Surefire](/tmp/paper-aggregate-kotlin-rerun-surefire)

首轮 `paperBatchGroups` 在 **8304.735 秒**后报 `recheck slot: null targets=null`；保留在[首轮 Surefire](/tmp/paper-aggregate-first-surefire)。日志存在较长时间间隔，但具体原因未证明，未归因为已修复产品问题。完整同命令重跑通过。后续 Maven 每进程有 20 分钟 watchdog，未触发。

### Contract Matrix

全部以下项目均为 mandatory，除明确标记 manual/非目标。

| ID | Requirement | Verdict | Required evidence / actual evidence | Source |
|---|---|---|---|---|
| I-1 | 原文与生产重放决定真值；325 不作新增/准入人数 | **FAIL** | 固定语料本身正确；实际核对工具可接受错误目标，V-3 | Master I-1；06 I-1/I-2；C8/C9/C15 |
| I-2 | SHA/metadata/first/history 保留；新结果版本和来源可追溯 | PASS | 244 原文逐项校验；beforeSnapshot、source refs、receipt；SQL/运行时断言未覆写源结果 | C1/C8/C10–C12 |
| I-3 | 身份事实不直接准入，继续原资格/人物/邮箱门禁 | PASS | 实际 12 目标 fixture：new=2、repaired=2、admitted=2，冲突/保护/错误不准入；旧 CLI AUDIT/REUSE READY | C1/C2/C11/C12 |
| I-4 | AUDIT 零业务/外呼；保护、确认、取消边界 | PASS | AUDIT 121/121 actuals=0/provider=0；人工/联系/APPLICATION/CAS/迟到 dispatch 拒绝；取消保留 RAW | C1/C10–C13/C16 |
| I-5 | 固定整批、DB 幂等/lease/generation、可恢复 | **FAIL** | 原始冻结/幂等/fence 均通过；取消后的显式重试遗漏核验，V-1 | C1/C10/C13 |
| I-6 | 唯一 consume、窄身份 CAS、现有 Commit/project；旧 CLI 只 prepare | PASS | 新写调用链与全边界 diff；真实 consumer/person/commit/project 与旧 CLI | C1/C11/C12 |
| I-7 | 论文/attempt/目标/doc/person/admission/sync 分单位且日志真实 | **FAIL** | V-1 将取消核验记成功；V-3 错目标误判；V-4 总量文本错误；V-5 普通核验详情不可达 | C13/C15/C16/C18 |
| I-8 | 父任务冻结 Apollo；detail=false 不变；无内部 500 限制 | PASS | ordinary/child settings 一致；frozenSearch=true、frozenDetail=false，paidCalls=0 | C1/C11/C12 |
| I-9 | 全部新 CSS/DOM 受 S-1..S-6 约束、文字/可访问操作 | **FAIL** | CSS/DOM 精确及布局通过；Escape 违反 S-6，V-2 | C5/C16/C17/C19 |
| M-1 | 原专家重审、单篇原文/下载/删除/保护不改 | PASS | 旧子重审详情仍 8 步；原 reader GET200、零 analysis POST；原行为 Node 回归 | C5/C6/C18 |
| M-2 | 人工/联系人/资格/Apollo 保护保留 | PASS | 多层保护、读异常 fail-closed、准入原 revision/identity guard | C1/C2/C11/C12 |
| M-3 | 原文件、first、旧轮次和源采集计数不改 | PASS | source/archive 快照相等；新 execution 计数；beforeSnapshot/rosterHash 不变 | C1/C10–C12 |
| M-4 | 邮件发送/收信及采集游标不改 | PASS | 无相关 writer 新调用或文件修改；运行时零相关业务作用 | 全累计 diff；C10–C12 |
| M-5 | 取消不撤销已完成补录/准入 | PASS | 取消后实际 RAW/new=1 保留；已准入保留，外来任务不取消 | C1/C12/C13 |
| X-0 | 原文真值→旧新 parser→逐标识对照 | **FAIL** | 当前回放集合可复算；但 invalid actual evidence 被误接纳，V-3 | C7–C9/C15 |
| X-1 | 首次归档→新解析/差异；统一版本/缓存 | PASS | 新采集/分析/replay 共享 parser，first 未覆盖，版本失效/兼容测试 | C1/C8 |
| X-2 | 新绑定→本轮有效身份→资格 | PASS | stage/complete CAS、同事务 hash/profile、身份变更缓存重绑、REUSE/REFRESH | C1/C11 |
| X-3 | ES 窄补齐→人物登记→保护/来源 | PASS | 三层 CAS/部分恢复；实际 person FAILED/STALE 原动作重试成功；外国档案不认领 | C1/C11 |
| X-4 | 身份变化→guard→准入→候选 | PASS | 真实 persisted-admission 严格接缝、事务回滚、人工/联系/revision/取消拒绝 | C1/C11/C12 |
| X-5 | lease→删除/保护→原文 UI | PASS | 显式 attempt/hash lease、删除竞争、真实 reader/hash 路径 | C1/C5/C10/C18 |
| X-6 | 持久任务→活动卡片→重入恢复 | **FAIL** | 121/7 页、同 execution 活动、DB counts 成立；取消 retry 不恢复核验，V-1 | C1/C10/C13 |
| X-7 | 新作者→原消费门禁→RAW/person/verification | PASS | 真 consumer，唯一邮箱跨文档归并，归属 receipts，第二轮 new=0 | C1/C2/C11/C12 |
| X-8 | 父任务→ordinary/child→准入与实际详情 | **FAIL** | 首轮业务自动衔接通过；ordinary 八步接口拒绝新作者，V-5；retry 有 V-1 | C2/C11–C13/C18 |
| X-9 | 原挂载/生命周期→新组件→旧详情回跳 | **FAIL** | 原 reader、旧 child 八步通过；新 ordinary 404、Escape 关闭宿主、完成文本错误 | C5/C16/C18 |
| 00 I-1 | 稳定组合键、不按姓名合并邮箱、复算 337/333/325 | PASS | manifest 337 唯一组合、244 records、47 negative candidates 保留 | C7–C9 |
| 00 I-2 | 原 bytes/metadata/first/config，经生产 PDFBox 重放 | PASS | 原文/metadata/first hashes 校验；244 可读；全部 337 有行 | C1/C8 |
| 00 I-3 | 离线、无 Spring 写服务图、零网络/业务写 | PASS | networkCalls=0、writeServices=0；trap 测试；单进程 | C1/C8 |
| 01 I-1 | 明确唯一联系证据；矛盾/共享/同缩写不绑定 | PASS | 30 resolver、8 layout、10 analysis；固定集无冲突绑定；合成正负例 | C1/C8 |
| 01 I-2 | 同页/栏/块边界；选定 tail；未读页单列 | PASS | PDFBox 布局负例；当前 1 行 AUDITED_PAGE_OUTSIDE_PRODUCTION_SELECTION 明列 | C1/C8 |
| 01 I-3 | 独立 parser 版本；旧身份/缓存版本兼容；历史资格不直接当前化 | PASS | RULE_VERSION=20261101；identity/extraction 旧版本未改；缓存/历史资格回归 | C1/累计 diff |
| 02 I-1 | propose 只读；ENFORCE apply；AUDIT 虚拟 profile | PASS | prepare/AUDIT 零 ES/person/admission writer；原按钮运行才 apply | C1/C11 |
| 02 I-2 | 唯一邮箱、多层保护/CAS、PARTIAL 不晋升 | PASS | 当前保护读异常、冲突、多层部分成功与余层恢复 | C1 |
| 02 I-3 | 本轮 lease/epoch rebind；原 baseline/hash 可重建 | PASS | SQL 当前 root/item 同事务；epoch0 rebase 拒绝；原 roster/旧项保留 | C1 |
| 02 I-4 | receipt 严格受控准入，普通调用保护不放宽 | PASS | 缺 receipt/manual/history/revision/取消均拒绝；受控原 identity 行提交成功 | C1/C11 |
| 02 I-5 | phase/重启/旧 token/旧缓存/快照不膨胀 | PASS | 同 proposal 恢复、旧 token 拒绝、identity changed cache 不复用 | C1 |
| 03 I-1 | 终态 source、全量固定、上界/水位不扩 scope | PASS | 121 records/7 页、额外筛选不缩范围、scope changed 拒绝 | C1/C10/C16 |
| 03 I-2 | requestKey/active singleton/状态/任务生命周期 | PASS | 并发同异 key、真实 SQL 唯一约束、状态/CAS/owner | C1/C3/C10 |
| 03 I-3 | PAPER/TARGET 分开、不可变输入、schema/trace/大小边界 | PASS | SQL enums、4MiB、6 步、unknown 保留拒绝；逐 attempt/hash 结果 | C1/C10 |
| 03 I-4 | 论文按 record；目标按邮箱；AUDIT actual=0 | PASS | 同 hash 多记录、121 权威 SQL 总量、候选分类重叠有声明 | C1/C10/C16 |
| 03 I-5 | lease/checkpoint/线程池/取消/显式恢复 | **FAIL** | 资源与旧 token/checkpoint 通过；正式取消后未完成核验不恢复，V-1 | C1/C10/C13 |
| 04 I-1 | 单一 consume/repair 写入口、既有档案不整文档覆盖 | PASS | 全 write-path diff 与实际新旧作者链 | C1/C11/C12 |
| 04 I-2 | 动作前后 durable receipts、崩溃/归属/重复计数 | **FAIL** | RAW/prepare/start/admission/person 恢复均通过；cancelled verification receipt 续接缺口 V-1 | C1/C11/C13 |
| 04 I-3 | 父许可、冻结配置、确定性分组、prepare 后 start | PASS | 100 分组、外来 slot 等待、prepare/start crash、late fences | C1/C11 |
| 04 I-4 | 真终态/统计、只取消 owned、projection-only retry | **FAIL** | 原 projection leaf 不外呼；取消核验被当成功 V-1；ordinary 公共详情/sync seam V-5 | C1/C11/C13/C18 |
| 04 I-5 | lease parser permit；身份/资格完整度分开；旧 CLI 不变 | PASS | 假 authors/hash/source 拒绝；完整 identity+partial qualification 可登记；旧 CLI 仍拒 partial | C1/C11 |
| 05 I-1 | API 状态/run/count 唯一权威、scope/超时/旧响应 | **FAIL** | key/分页/epoch 边界通过；targetTotalKnown 读错层 V-4 | C5/C16 |
| 05 I-2 | 默认/确认模式、关闭不取消、明确 cancel | **FAIL** | 没有关闭导致 cancel POST；但 Escape 关闭整个宿主而非 dialog，V-2 | C5/C14/C16 |
| 05 I-3 | first/current/绑定/档案/准入/原因可解释 | **FAIL** | 正确实际计数通过；V-4 完成文本假形成、V-5 新作者不可看 8 步 | C16/C18 |
| 05 I-4 | 安全 DOM、分页/陈旧响应、401/403/错误恢复、焦点 | **FAIL** | textContent/无 innerHTML、请求隔离、错误保留通过；Escape/focus 边界 V-2 | C5/C6/C16 |
| S-1/S-2/S-3 | 精确入口/弹窗/六列与手机三列/分页 | PASS | 完整 CSS/DOM；真实 API 状态；1440/390、100/125 对应布局可达 | C5/C16/C17/C19 |
| S-4 | 六步真实状态并链接现有八步 | **FAIL** | 六个节点完整；取消核验被记 SUCCESS V-1；ordinary 链接404 V-5 | C13/C18 |
| S-5 | 新 CSS 逐字，旧全局规则不改 | PASS | 当前批准块 5515 bytes 完全相等；base CSS 为当前前缀 | C19 |
| S-6 | 精确 DOM、原文/八步接缝、Escape 仅关闭 dialog | **FAIL** | DOM 精确；原文和旧 child 正常；V-2/V-5 | C5/C16/C18/C19 |
| 06 I-1 | 同 bytes/metadata/时间/策略/provider 对照 | **FAIL** | 当前固定输入一致，外部 fixture 确定；实际错 identity 对照遗漏 V-3 | C8/C9/C12/C15 |
| 06 I-2 | 逐目标身份/doc 校验；真实增量；未执行不冒充成功 | **FAIL** | 正常 12 目标逐人记录正确；wrong doc/重复实际目标被误接受，V-3 | C2/C12/C15 |
| 06 I-3 | 真迁移/边界门禁与隔离；生产另行授权 | PASS | C1–C12 最终通过；生产明确 NOT_RUN；无生产写 | 全命令及边界 |
| NG | 不做 OCR、资格放宽、下载范围/并发扩张、邮件/游标/主题改造、生产补跑 | PASS | 全边界授权文件检查、调用链检查与零外部/生产写证据 | C8/C10–C12/C19 |

### 扩展文件授权矩阵

以下每行对应明确扩展/追认文件；均通过范围核对，不作为行为豁免。

| Amendment | Expanded file | Verdict / evidence |
|---|---|---|
| A1/A2 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` | PASS；child02 第11文件，受控 persisted-admission seam |
| A3 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/db/migration/V173__create_discovery_paper_reprocess.sql` | PASS；替代已被占用的拟定 V172；旧迁移未修改 |
| A4/A5 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperBodyAnalyzer.kt` | PASS；03 checkpoint 透传；默认行为保留 |
| A4/A5 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt` | PASS；按页 checkpoint/窄异常传播 |
| A4/A5 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | PASS；可选 callback，普通规则版本不由此扩改 |
| A6/A7 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt` | PASS；移除 incidental 集合/数量断言，保留摘要/进度测试 |
| A8/A9 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt` | PASS；04 receipts/REVIEW/父许可范围 |
| A8/A9 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | PASS；原 archived consume 的 owned permission/续接 |
| A8/A9 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | PASS；仅 paper-owned epoch0 围栏 |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-recheck.js` | PASS；原八步只读 opener/lifecycle |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/discoveryRecheck.test.js` | PASS；只读 opener 行为测试 |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPaperReprocess.kt` | PASS；权威只读 DTO 字段 |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt` | PASS；05 权威汇总/缺 descriptor 查询 |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt` | PASS；05 read-only 映射 |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepositoryIntegrationTest.kt` | PASS；实际 SQL 汇总 |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessServiceTest.kt` | PASS；preview/summary 契约 |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/discoveryReview.test.js` | PASS；直接受资源注册影响的 incidental 断言调整 |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/mailboxChatBehavior.test.js` | PASS；同上，保留实际组件顺序/唯一性 |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/mailboxGroupPush.test.js` | PASS；同上 |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/meetingConfirmationAssets.test.js` | PASS；同上，未重钉新总数 |
| A10/A11 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/trustReplyWorkbenchSharedMount.test.js` | PASS；同上，真实 lifecycle 仍测 |
| A12/A13 | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | PASS；child06 仅七处 fixture V161→V173，断言未削减 |

### Finding Lineage

无先前 aggregate verification/repair。以下为 epoch1 初次整体验证发现；usage-limit 恢复未创建新轮次。

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | 实际 SQL 取消/显式 retry 探针 |
| V-2 | NEW | 实际 host handler + 真实 Chrome Escape |
| V-3 | NEW | 固定已知 docId 的 reconciliation 反例 |
| V-4 | NEW | 真 API DTO + 完成后浏览器文字 |
| V-5 | NEW | 实际新作者 GET404 与旧作者 GET200/8步对照 |
| V-6 | NEW，非阻断 P2 | child05 F-05 的 aggregate 映射；未视为豁免 |
| V-7 | NEW，非阻断 P2 | resumed active run 完成后 preview 状态陈旧 |

### Findings

#### P1

**V-1 — 取消后显式重试，把未完成资格核验当作成功。**

[ReprocessRepository](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt:425) 的 retry 重开 TARGET，但已取消 ordinary verification 保持 CANCELLED；[ApplyService.observe](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyService.kt:272) 把 CANCELLED 纳入成功终态。

真实探针：取消时已建 RAW，NEW=1；同父任务 retry 后 run=COMPLETED、target=COMPLETE，admitted=0、qualification.NOT_EVALUATED=1、pending=0。没有完成资格核验，界面却可结束为成功。违反 I-5/I-7、03 retry、04 I-4。最小范围：owned retry/续接与终态观察，保留已生效收据和旧历史。

**V-2 — 新弹窗 Escape 关闭整个任务宿主。**

[新组件](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-paper-reprocess.js:308) 只监听 cancel/close，未隔离 keydown。已有 [app handler](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/app.js:20352) 只保护旧论文弹窗和 recheck dialog，因而调用 `closeTaskModal()`。

真实 Chrome 与组装探针均得到 `hostCloses:1, confirmOpen:false`。违反 S-6“Escape 只关闭 dialog”、焦点及上下文保持要求。最小范围：新组件 dialog 事件边界及回归测试。

**V-3 — 对照工具不能可靠阻断错误目标。**

[actual_binding/classify](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py:38) 采用否定名单接受 outcome；docId 只在旧状态 ALREADY_RECOVERED 时检查。[重复目标分支](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py:174) 的 `AMBIGUOUS_TARGETS` 又被当作 binding。

固定 record 31372 的已知 docId 是 `0000-0002-0178-9985`。实际传入 `wrong-doc`，REPAIRED、重复目标、缺 outcome、带此错误 docId 的 SKIPPED 都被判 EXPECTED_MATCH；明确 `CONFLICTING_PRODUCTION_BINDING` 则降为普通 EXPECTED_MISSING。违反06“同数不同 email/docId 必须 FAIL”和错绑阻断要求。当前正常 337 行没有因此被证明造假；缺陷在验证器的拒绝能力。最小范围：脚本验证及既有 replay suite 中的 CLI 反例。

**V-4 — 已固定目标总量仍显示“形成中”。**

[render 第72行](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-paper-reprocess.js:72) 读取 `snapshot.targetTotalKnown`；实际 [DTO](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPaperReprocess.kt:66) 将字段放在 `summary.targetTotalKnown`，ReprocessDetail 无根字段。

真实完成 AUDIT 已有 121/121、2 个 TARGET，API summary 为 true；浏览器仍写“目标总量仍在形成”。违反 I-7、05 明确的未知/固定总量语义。最小范围：读取正确嵌套字段及实际 DTO 形状测试。

**V-5 — 新作者普通核验不能打开原八步，公共同步重试接缝亦不匹配。**

新作者保存的是 epoch0 ordinary item，前端却统一打开 `/api/discovery/rechecks/{runId}/items/{itemId}/steps`。[requireRecheck](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt:126) 明确拒绝 `recheckJson=null`；item guard 又要求 `reviewEpoch==runId`。

实际浏览器：

- NEW：parent 30714/item 1 → **404 RECHECK_NOT_FOUND，0步**。
- REPAIRED：child 30715/item 4 → **200，8步**。
- 原文 reader → 200，未发送 analysis POST。

[公共 syncRetry](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt:367) 同样首先要求 recheck，无法服务该 ordinary 所属关系。违反 X-8/X-9、05 S-4 及04 projection-only retry 接缝。最小范围：仅对服务端证明属于 paper-reprocess 的 ordinary item 补齐读取/既有投影重试，保留通用 recheck guards。

#### P2

- **V-6 / child05 F-05**：确定性 503（APPLY_NOT_AVAILABLE/VERIFICATION_UNAVAILABLE）仍保留 ENFORCE pending key 并锁定模式；恢复 AUDIT 需要刷新。无业务误写；非明确必需项，未纳入修复。
- **V-7**：恢复已有 active run 后，完成轮询未更新 `preview.activeRun`，入口保持 disabled，重入后恢复。独立组件探针复现；非明确必需项，未纳入修复。

#### Observations

- 原有编号 References heading 的相邻作者绑定限制在基线已有；对应语法路径未由本次新增，未升级为 P1。
- 可选 `DISCOVERY_LAYOUT_FROZEN_SAMPLES` 旧语料缺失导致一个 skip；本次必需 #30711 语料完整执行。
- 通用任务 catalog 的 `summaryRule=null` 继续走既有 raw fallback；owned detail、SQL 和 activity 承担本功能权威计数。
- 历史 rapid-tick projection trace duplicate 已保留。完整边界中 `project`/TraceRepository 的该竞争路径未改变；实际 `startProjection` 有事务和 item lock，既有 fixture 的代理/并发边界亦须区别。当前 fresh runtime 通过，但不宣称通用并发风险已修复；未证明本次引入/暴露的强制失败。
- 旧首轮 Maven 超时保留，完整重跑通过；无代码修复或隐瞒失败。
- 旧报告 CSS 5444-byte 数字不作为当前证明；本次独立比较批准块为 5515 bytes，逐字相等。
- `git diff --check` 全边界仅旧 fast-p 文档两处 EOF 空行；产品/脚本/审计范围检查通过，无修复授权。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Fresh evidence / disposition |
|---|---|---|---|
| 01 O-01 旧私有语料缺失 | I-1/I-2 | Observation | 当前仅可选旧 corpus skip；必需244原文/337组合均已执行 |
| 01 O-02 编号 References | I-1/I-3 | Observation | 当前与 base 的相同 start-name 路径比对；确认为未受影响的既有问题，不作本次正确性豁免 |
| 03 O-01 generic structured summary | I-5/I-7/X-6 | 非阻断 | 当前 catalog/raw fallback/owned SQL/runtime activity 联查；无要求的新 generic shape，不扩生产 extractor |
| 04 O-01 loopback/provider/保护 mocks 边界 | I-3/I-4/I-8 | 证据边界保留 | fresh real MySQL/真实服务/确定性 provider/loopback ES；无 live-provider 结论 |
| 04 O-02 generic summary | I-7 | 同03 O-01 | owned API/SQL与任务活动为权威 |
| 04 O-03 rapid-tick projection duplicate | I-4/I-6 | Observation | base..code 的 project/trace 路径比对、事务锁检查和 fresh runtime；没有将其视为已修复或全并发 PASS |
| 05 F-05 | I-4/I-5/I-9 | **V-6，P2非阻断** | 当前503分支仍保留 pending ENFORCE；无写/audit 隔离违反 |
| 05 O-07 重复缩放截图 | I-9 | 旧证据不足已补证 | fresh Chrome 1440/390及 viewport-equivalent 125% 截图/可达性指标；未采用失真的 CSS zoom 结果判缺陷 |
| 05 O-08 旧运行/配置描述不一致 | I-1/I-7/I-8 | 已更正的历史证据 | 本次真实 AUDIT9002与固定 provider 配置分开记录，不继承9502或 saved-search=true 断言 |
| 05 O-09 正候选数无截图证明 | I-7 | 当前补证 | fresh UI/真实SQL显示 wouldCreate=1/wouldRepair=1、actuals=0；另发现 V-4 |
| 05 O-10 summary文档措辞 | I-7 | 非阻断措辞债 | 实际候选可重叠且 UI 明示；不据注释推断业务写入 |
| 05 O-11 401/JDK/合成运行边界 | I-4/I-9 | 边界保留并补证 | JDK11.0.32.1；real HTTP401通过，组件401保留路径由测试验证；全 app 401 仍可能先全局登出。新旧八步已用真实数据补测，V-5未被原合成证据覆盖 |
| 06 O-01 improvement标签 | I-1/I-7 | 非阻断标签观察；与V-3分开 | 50条正确 gain/49个邮箱/39篇；当前51/286可复算。没有因此豁免错误实际目标拒绝缺陷 |
| 06 O-02 V171措辞/V170测试 | I-2/I-6 | 非阻断 | fresh V170→V173跨越V171/V172/V173，验收未减少 |
| 06 O-03 live/production NOT_RUN | I-3/I-4 | 明确 NOT_RUN | 本轮无生产30711 ENFORCE/live ES/live ORCID/Apollo；未填生产业务增量 |
| 06 O-04 package仅JS/WAR | I-1/I-3 | 正确分离 | fresh package2035 JS+WAR；Kotlin来自C1/C2/C3 |
| 06 O-05 历史报告行数 | I-1/I-7 | 非阻断历史文本 | 当前报告/337附录按实际内容核对，不继承634行旧说法 |
| 06 F-01 文档身份/门禁措辞 | I-1/I-3 | 当前无该缺陷 | docs-only `3f042fc` 修正内容核对；未混入产品边界 |

### 效果与人工验收

固定语料：244 原文可读，337 组合，47 未确认分栏候选保持排除。旧 replay：330 REPRODUCED、6 NOT_REPRODUCED、1 ALREADY_RECOVERED。当前：51 ALREADY_RECOVERED、284 NOT_REPRODUCED、2 REPRODUCED。

正确 gain **50 个组合 / 49 个邮箱指纹 / 39 篇论文**；旧已恢复组合 loss=0；当前 SOURCE_MISSING=0、AMBIGUOUS=0。剩余具体原因：NO_PRODUCTION_LAYOUT_CONTACT 280、NAME_NOT_LITERAL 4、EMAIL_NOT_LITERAL 1、AUDITED_PAGE_OUTSIDE_SELECTION 1。该数量不等于新增专家、人物或准入人数。

| Manual checklist | Status |
|---|---|
| Master A-1 原按钮补绑 | PENDING |
| Master A-2 漏建档/自动衔接 | PENDING |
| Master A-3 只读/历史 | PENDING |
| Master A-4 保护/冲突/取消 | PENDING |
| Master A-5 恢复/样式 | PENDING |
| Master A-6 逐条效果核对 | PENDING |
| 00 A-1/A-2 | PENDING |
| 01 A-1/A-2/A-3 | PENDING |
| 02 A-1/A-2/A-3/A-4 | PENDING |
| 03 A-1/A-2/A-3 | PENDING |
| 04 A-1/A-2/A-3 | PENDING |
| 05 A-1/A-2/A-3/A-4 | PENDING |
| 06 A-1/A-2/A-3 | PENDING |

机器浏览器/SQL证据不替代人工签字；未生成 acceptance 衍生文件。

### Evidence Boundaries

- 没有生产发布/补跑或真实 provider 准确率证明；本轮未被授权。
- ES 为严格 loopback HTTP/CAS fixture，非部署 Elasticsearch。
- 浏览器使用真实相关模块/DOM、控制器和独立 SQL fixture；配置/provider 确定，非生产全应用验收。
- `/usr/bin/time` 实测含较长 wall-clock 间隔，不据此承诺批次吞吐。
- 当前必需机器证据已取得；上述生产/人工事项保持 NOT_RUN/PENDING。

## Repair Planning Result: DRAFT_READY

Baseline plan：[paper-reprocess-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-master.md)  
Verification result：**FAIL / INITIAL**  
Repair artifact：[repair.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md)  
SHA-256：`47b6bccdb128dd02177fe0d629e251afe781064d75dcbc5f18c07ab24a829110`

### Included Findings

- V-1：取消后 owned qualification 续接。
- V-2：新 dialog Escape 边界。
- V-3：实际对照身份验证。
- V-4：nested targetTotalKnown。
- V-5：ordinary paper-owned 八步/投影重试接缝。

### Excluded Findings

- V-6/V-7：非必需 P2，未获修复批准。
- 所有 Observations、生产/live-provider/人工未测项。
- 首轮测试失败的未证明根因；不做猜测修复。

计划精确授权 **10 个既有产品/测试文件**，5 个独立根因任务。没有新增 endpoint/DTO/schema、全局 recheck 放宽、重复核验或额外 writer。包含完整门禁、真实 API/浏览器回归，以及已解析路径和具体两个 commit subjects 的 one-approval execution handoff。

最终 Git：HEAD 不变，tracked diff/index 均空；仅新增上述 repair 目录，保留 controller 原未跟踪 review 目录。未写 machine-verification、review ledger 或其他证据文件。

### Next Action

依 [review-p](/Users/lukai/.agents/skills/review-p/SKILL.md) 的明确要求——“A later, explicit, human-originated request to use `$execute-p` with the exact existing `repair.md` path constitutes approval”——本轮停在修复计划审批，不执行。

To approve and execute this repair, send:

```text
$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md
```

No implementation was performed.  
No product code was modified.
