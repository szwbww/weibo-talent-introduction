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


## Epoch 2 — 2026-11-03T00:35:13+08:00

## Verification Result: FAIL

Plan：[paper-reprocess-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-master.md)  
Requested phase：`aggregate/master`  
Implementation boundary：`3a425461dbe6088e4d4dda1a53141416a392670c..291fb34b35be496673e28f124d443b9c46ba66f4`  
Evidence HEAD：`96da7863acdb8dd9a644048b8f9c99a8aa2aa7e0`  
Reviewer：`/root/paper_reprocess_aggregate_epoch2`；同一 epoch，usage-limit 后恢复 attempt 2。  
Convergence：**DIVERGING**  
Manual acceptance：**PENDING**  
Repair planning：**N/A**

确认两项阻断：**V-3 持续；V-5 出现同级回归**。V-1、V-2、V-4 已解决。必需命令全部取得新鲜完整回执；C10 首轮失败、完整复跑通过，失败记录保留。

本报告中的仓库根目录 **W**：

`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`

本轮临时证据根目录 **D**：

`/tmp/paper-aggregate-epoch2-ei2_5x6s`

表内相对源码路径均相对于 W；临时回执均相对于 D。

### 身份与审查边界

| 项目 | 独立核对结果 |
|---|---|
| Worktree / branch | W；`fast/paper-reprocess-master`；解析方式 `EXPLICIT`，对应已注册 worktree |
| Governing master | SHA-256 `85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e`；逐字等于 commit `53c2b0018e239e3c30db25942e99c2cddc69e9d4` |
| Current invoked master | `SAME` |
| Historical invoked copy | SHA-256 `d1e17452a5484eae56b37bca5671e8c767fd3f744594232e4b57dac4826b1aa6`；已读完整 governing diff |
| Master identity state | `AMENDMENT_RECORDED`，A1–A13 |
| Fast-p ledger | `docs/plans/fast/paper-reprocess-master/ledger.md`；SHA-256 `dc2d86b0f225fbcec9f86a2bfa5f089cc3075fe541881dd5a8ceb88689274e09` |
| Fast-p handoff | 同目录 `human-review-handoff.md`；SHA-256 `ee33dc68d01e4682dfec0fc0edd3607b42587832811c08d453414371cc2ab155` |
| Prior aggregate report | `docs/plans/review/paper-reprocess-master/machine-verification.md`，epoch 1；SHA-256 `338e26bab7c2bfe1790144b85db617742744b1b226678230fb80241a27d014eb` |
| Ancestry | base、prior code、candidate、recorded master 均在所声明祖先链 |
| Final state | HEAD、branch 不变；index 空；唯一 tracked diff 为控制器的 review `ledger.md` |
| Reviewer writes | 仅临时诊断、测试及构建输出；未写 aggregate report、review ledger 或修复稿 |

有序子计划均位于 `W/docs/plans/2026-11-01/`：

| Child | 精确计划 | 身份 |
|---|---|---|
| 00 | `paper-reprocess-00-replay.md` | SHA-256 `f9cb78a3c967c67fbbeab0c272a0894cb4928cd6a650e05ae419853dee6a2132` |
| 01 | `paper-reprocess-01-parser.md` | SHA-256 `508fd62d84283bd223aca0970c9e265944762f6c48f36ffaf127ba2b88260608` |
| 02 | `paper-reprocess-02-identity.md` | 逐字等于 commit `9ce385c729c4b3a4c6fd0fff3aa06c126773cec3` |
| 03 | `paper-reprocess-03-batch.md` | 逐字等于 commit `a6e14b63448fecfa0cd1319074c8d0b5bb2026be` |
| 04 | `paper-reprocess-04-apply.md` | 逐字等于 commit `5a31faaebf28001fb05d34c6b2aa195cc2a0e01d` |
| 05 | `paper-reprocess-05-frontend.md` | 逐字等于 commit `d678780155fc4fac5ecc4896135806729efaee2f` |
| 06 | `paper-reprocess-06-verification.md` | SHA-256 `ac6a7663526a7a5c3a74716a3be3b54db97b8afaa6b5576672d8b65f655a6bb2` |

已核对各 child 的 `brief.md`、`execution.md`、`fix-log.md`、`verify-log.md` 及相关引用证据；各 child 均为终态。LIGHT_PASS 和执行者测试数未充当本轮验证结果。

### Post-repair evidence

Mode：**DURABLE_HANDOFF**。

- Repair：[repair.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md)，SHA-256 `47b6bccdb128dd02177fe0d629e251afe781064d75dcbc5f18c07ab24a829110`。
- Approval：durable handoff 记录人类明确 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md`。
- Executor：`PaperRepairExecutor`，与本 reviewer 不同。
- Handoff：[repair-execution.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/repair-execution.md)，SHA-256 `6d96f63786c56d0a5b6b36937431db3f372ae4270e4ccdcb324725281bf6424e`。
- Prior code：`e213acb50791fe39a0de0a4d9d599bc4e2398cc1`。
- Pre-execution parent：`54a7d14648ec7c2580c01c3cef26bbabaf0c5021`；prior code 至此仅 docs。
- Repair code：`291fb34b35be496673e28f124d443b9c46ba66f4`。
- Repair code 至 evidence HEAD 仅执行交接文档。
- 可选精确 executor status token：`NOT_RECORDED`。完整命令、clean/commit 回执已存在；未据可选叙述缺失阻断。
- 修复累计 delta 恰为以下十个 Authorized Files，无额外文件：

| 文件，均相对于 W | Authorized |
|---|---:|
| `scripts/discovery_admission_probe/reconcile_paper_identity_replay.py` | YES |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt` | YES |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` | YES |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyService.kt` | YES |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt` | YES |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` | YES |
| `src/main/resources/static/discovery-paper-reprocess.js` | YES |
| `src/test/js/discoveryPaperReprocess.test.js` | YES |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt` | YES |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessEndToEndTest.kt` | YES |

### Recorded Approved Amendments

共同批准原文 H1：

> HUMAN:继续 后续授权都继续 直到所有子计划都完成且可以运行review-fast-p的阶段 不要执行review-fast-p，我会交给其他agent执行 — 2026-11-01T23:28:37+08:00

| ID | Master rule | Recorded reason | Approval |
|---|---|---|---|
| A1 | I-4/I-6/X-4 and 顺序与门禁 | Approve child 02 eleven-file exception for existing persisted-admission seam without reducing tests | H1 |
| A2 | I-4/I-6/X-4 | Authorize narrow DiscoveryReviewService controlled changed-identity seam and existing-test regressions | H1 |
| A3 | I-2/I-5/X-1/X-5 and 顺序与门禁 | Allocate unused V173 because existing V172 is occupied; preserve applied migration, schema, scope count and acceptance | H1 |
| A4 | I-4/I-5/X-1/X-5 and 顺序与门禁 | Authorize child03 cap13 for existing parser-chain page cancellation/budget checkpoints only | H1 |
| A5 | I-4/I-5/X-1/X-5 | Add three proven parser checkpoint seams, preserve default behavior/rules/versions, retain all acceptance | H1 |
| A6 | I-5/I-7/X-6 and 顺序与门禁 | Authorize child03 cap14 for directly broken task catalog contract tests, no acceptance reduction | H1 |
| A7 | I-5/I-7/X-6 | Add affected TaskExecutionSummaryExtractorTest and covering command; retire incidental fixed lists/count instead of re-pinning | H1 |
| A8 | I-4/I-5/I-6/X-3/X-4/X-7/X-8 and 顺序与门禁 | Child04 cap13 for durable receipts and original consume/ordinary-worker parent permission seams | H1 |
| A9 | I-4/I-5/I-6/X-3/X-4/X-7/X-8 | Add three evidenced necessary receipt/owned-consume/epoch0 worker fences; preserve ordinary defaults and all acceptance | H1 |
| A10 | I-5/I-7/I-9/X-5/X-6/X-8/X-9 and 顺序与门禁 | Child05 cap19 for required existing readonly opener, authoritative summary/preview and directly broken resource tests | H1 |
| A11 | I-5/I-7/I-9/X-5/X-6/X-8/X-9 | Add readonly API totals/source-missing preview and original8-step opener; retire incidental resource inventories, retain exact CSS/DOM/browser acceptance | H1 |
| A12 | I-1/I-3/I-6 and 顺序与门禁 | Child06 fifth-file exception for existing DiscoveryVerificationWorkerTest Flyway fixture target V161→V173 | H1 |
| A13 | I-1/I-3/I-6 | Raise existing migration fixture target only; assertions/business behavior/12-target/migration/report acceptance unchanged | H1 |

A12 master identity：`2de159efc6b04e4f8dc3542890009eba15f004cf` → `53c2b0018e239e3c30db25942e99c2cddc69e9d4`。  
A13 child06 SHA：`ea13bb67c166e8cb541cc068d1367432497ed0fb3f303b61c3ba52d2f4493791` → `ac6a7663526a7a5c3a74716a3be3b54db97b8afaa6b5576672d8b65f655a6bb2`。

Master 顺序表 06 仍写 4；第五文件按明确 A12/A13 处理。全部 amendment 均作为已记录契约逐项核对，未用作行为缺陷豁免。

### Commands

执行目录全部为 W。所有 Maven/Java/子 Java：

`JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`

实际 Maven 为 `/opt/homebrew/bin/mvn`；XML 记录 Java `11.0.32.1`。MySQL 为隔离 Testcontainers `8.0.36`。Maven/target 写操作串行；runner 每条命令有限 watchdog，恢复后的 runner 逐条持久化退出码。

| ID | 精确命令 | 结果及新鲜回执 |
|---|---|---|
| C1 | `/opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | **PASS**，exit 0，281.36s；12 tests，0 failure/error/skip。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C1.log) |
| C2 | `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js` | **PASS**，exit 0，0.72s；46/46，0 skip。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C2.log) |
| C3 | `/opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,SourceAuthorEmailResolverTest,DiscoveryPaperLayoutRegressionTest,DiscoveryPaperAnalysisServiceTest,RetainedPaperIdentityServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest,TaskExecutionSummaryExtractorTest,DiscoveryPaperReprocessApplyServiceTest,DiscoveryPaperBackfillServiceTest,DiscoveryRecheckServiceTest,DiscoveryVerificationIntegrationTest -DmysqlIt=true -DpaperReprocessIt=true` | **PASS**，exit 0，914.25s；205 tests，0 failures，0 errors，1 optional corpus skip。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C3.log)、[逐类 XML 摘要](/tmp/paper-aggregate-epoch2-ei2_5x6s/C3-summary.json) |
| C4 | `/opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | **PASS**，exit 0，272.96s；4/0/0/0。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C4.log) |
| C5 | `/opt/homebrew/bin/mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 初次日志记录 48/0/0/0、BUILD SUCCESS；usage-limit 断开导致 OS exit **NOT_RECORDED**。保留[原日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C5.log)及 XML，未补造退出码 |
| C5R | 同 C5 精确命令 | **PASS**，exit 0，796.92s；48/0/0/0；真实空库及 V170→V173；94 次 schema clean、93 次成功迁移调用。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C5R.log) |
| C6 | `/opt/homebrew/bin/mvn package -DskipTests` | **PASS**，exit 0，221.87s；Maven-bound JS 2037/2037、354 suites；WAR 成功。此命令未运行 Kotlin 测试。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C6.log) |
| C7 | `node --test src/test/js/*.test.js` | **PASS**，exit 0，5.74s；2037 pass，354 suites，0 failure/skip。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C7.log) |
| C8 | `python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py` | **PASS**，exit 0，0.07s；PERSON 333、PAPER 244、PAPER_BINDINGS 337、prior_known 7、other_same_name 1。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C8.log) |
| C9 | `/opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.Paper03RuntimeSmoke'` | **PASS**，exit 0，73.09s；121 records/7 pages、幂等、401、零 provider/归档写；404/409 修正路径及 durable error 回读。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C9.log) |
| C10 | `/opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --paper-reprocess-smoke'` | **首轮 FAIL**，exit **1**，94.83s；取消时序测试抛 `Trace lease lost`。[失败日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C10.log) |
| C10R | 同 C10 精确命令，仅一次完整重跑 | **PASS**，exit 0，360.54s；`PAPER04_RUNTIME_OK`，取消、恢复、100+1 分组、原 CLI、保护及投影场景完成。未据此宣称首轮问题已修复。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C10R.log)、[真实退出码](/tmp/paper-aggregate-epoch2-ei2_5x6s/C10R-result.json) |
| C11 | `/opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke'` | **PASS**，exit 0，69.7s；`PAPER06_RUNTIME_OK`，真实 SQL、loopback ES CAS、真实服务、paidCalls=0、productionWrites=0。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C11.log) |
| C12 | `/usr/bin/time -l /Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json /tmp/paper-aggregate-epoch2-ei2_5x6s/replay.json` | **PASS**，exit 0，101.73s；337 rows，244 records，networkCalls=0、businessWriteServicesConstructed=0；最大 RSS 1,354,530,816 bytes。[日志](/tmp/paper-aggregate-epoch2-ei2_5x6s/C12.log) |
| C13 | `python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json --old-replay /tmp/paper-reprocess-30711-baseline.json --new-replay /tmp/paper-aggregate-epoch2-ei2_5x6s/replay.json --audit-directory docs/audits/2026-11-01-discovery-30711-pdf --csv /tmp/paper-aggregate-epoch2-ei2_5x6s/reconciled.csv --report /tmp/paper-aggregate-epoch2-ei2_5x6s/reconciled.md --runtime-evidence /tmp/paper-reprocess-30711-runtime.json` | 正常输入命令 **PASS**，exit 0，0.13s；337 rows、extras=0、51 EXPECTED_MATCH/286 EXPECTED_MISSING。验证器语义仍有 V-3。[CSV](/tmp/paper-aggregate-epoch2-ei2_5x6s/reconciled.csv) |
| C14 | 实际 CUA 浏览器操作：隔离 AUDIT/API/MySQL、四种 viewport、Escape、500、完成、NEW/REPAIRED 八步、只读原文、公开 sync-retry | **混合结果**：布局/Escape/目标总量/八步读取通过；REPAIRED child sync-retry 实际 **404**，见 V-5。浏览器工具操作无 shell exit，未捏造统一退出码 |
| C15 | `node --check src/main/resources/static/discovery-paper-reprocess.js`；`git diff --check 3a425461dbe6088e4d4dda1a53141416a392670c..291fb34b35be496673e28f124d443b9c46ba66f4 -- src scripts docs/audits` | 均 exit 0；scope、CSS/DOM、Git/index 最终核对通过 |
| D-V3 | 七组真实固定行 CLI 诊断；精确 argv 保存在 `identity-probe-results.json` | 各 CLI exit 0；valid 正确接受，四种无效证据正确拒绝；**wrong-email、missing-email 错误接受**。[逐例命令/结果](/tmp/paper-aggregate-epoch2-ei2_5x6s/identity-probe-results.json) |
| D-V5 | 隔离当前 fixture MySQL 故障注入 + 实际公开 API/页面按钮 | SQL 保持 COMPLETED/admissionRevision=0，仅投影 PROJECTED→ERROR；真实 API 与 UI POST 均404。[SQL](/tmp/paper-aggregate-epoch2-ei2_5x6s/child-projection-fault-sql.log)、[API](/tmp/paper-aggregate-epoch2-ei2_5x6s/child-sync-error-api.json)、[HTTP](/tmp/paper-aggregate-epoch2-ei2_5x6s/child-sync-error-http.json) |

C3 逐类测试数，顺序对应命令：

`8, 30, 8, 10, 7, 24, 7, 22, 6, 12, 19, 2, 4, 10, 36`。

唯一 skip：`DiscoveryPaperLayoutRegressionTest` 的可选 `DISCOVERY_LAYOUT_FROZEN_SAMPLES` 旧 corpus；本计划必需 #30711 原始字节重放未跳过。

构建 WAR：53,149,381 bytes；SHA-256：

`5243ee8fc0f5eee2b78b5a2496e3c892fdce33236d9b1ad19d962bfcea289596`

修复 Verification Commands 1–14 已逐项覆盖；master/children 的必需业务测试、Node、迁移、打包、重算、重放、三个独立 JVM runtime 与实际浏览器要求均有对应新鲜证据。未复用执行者或 epoch1 计数。

### 实际效果与输入保护

| 指标 | 本轮结果 |
|---|---|
| 固定 manifest | 337 组合、244 record |
| 可读取原文 | 244；SOURCE_MISSING=0 |
| 旧 replay | REPRODUCED 330、NOT_REPRODUCED 6、ALREADY_RECOVERED 1 |
| 新 replay | ALREADY_RECOVERED 51、NOT_REPRODUCED 284、REPRODUCED 2 |
| 正确新增绑定增量 | 50 组合、49 email hashes、39 records |
| 已有正确绑定丢失 | 0 |
| 仍缺口 | NO_PRODUCTION_LAYOUT_CONTACT 280；NAME_NOT_LITERAL_IN_PDFBOX_PAGE 4；EMAIL_NOT_LITERAL_IN_PDFBOX_PAGE 1；AUDITED_PAGE_OUTSIDE_PRODUCTION_SELECTION 1 |
| 原始 47 未确认分栏候选 | 未自动修复，继续排除 |
| 固定 12-target ENFORCE | 15 papers/12 targets；新增2、补齐2、准入2、冲突2、保护2、错误2、同步失败1 |
| 同批第二轮 | 新增0、补齐0、准入0；原已准入2单列 |
| 普通核验取消后 | NEW1、admitted0、NOT_EVALUATED1 |
| 同父显式 retry 后 | NEW1、admitted1、NOT_EVALUATED0 |
| 已有档案 child retry | 同一 child 完成实际核验，UNKNOWN 已评价；旧取消 trace、完成 peers 保留 |
| 生产业务执行 | NOT_RUN；上述新增/补齐/准入数字仅隔离固定 fixture |

输入及冻结回执末次 SHA 检查无变化：

| 输入 | SHA-256 |
|---|---|
| `production-replay-manifest.json` | `de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c` |
| `/tmp/paper-reprocess-30711-baseline.json` | `e0fe5b1e008fda6188315f73972f72af79b3897b6ca59f1a4f04c8d452b3d91c` |
| `/tmp/paper-reprocess-30711-runtime.json` | `5d00ffbc736f653fe64b93b5e667c8baf776aa33b1320716d212291b45e037c3` |

### Contract Matrix

除人工条目、显式非目标及 Observation 外，下列均为必需项。来源 `master`、`00..06` 指上述精确批准计划；证据 C/D 编号指本报告命令及诊断。

| ID / Source | 必需要求 | Verdict | Evidence |
|---|---|---|---|
| I-1 / master | 原文与生产重放真值；数量不夸大 | **FAIL** | 新鲜337正常重放通过；实际身份校验仍可接受错误/缺失邮箱，V-3 |
| I-2 / master | 原文、metadata、first、历史不覆写；版本/页码/原句可追溯 | PASS | C3/C9/C10R/C11/C12；冻结哈希；retained identity / item immutable input 路径 |
| I-3 / master | 身份事实不直接准入；现行资格门禁 | PASS | C3/C10R/C11；UNKNOWN、PARTIAL 不自动合格 |
| I-4 / master | 保护、AUDIT、ENFORCE、取消隔离 | PASS | C9零业务/provider；C10R保护、late response、取消；C11 |
| I-5 / master | 固定跨页 scope、持久 lease/幂等/恢复 | PASS | C3/C9 121/7页；C10R分组/恢复；C11 retry；另保留 C10 不稳定观察 |
| I-6 / master | 单一消费、窄修复、原 Commit/project、旧 CLI | PASS | 源码叶子追踪；C10R consumer/legacyCLI/project；无第二套 writer |
| I-7 / master | 计数、身份、准入、同步与 trace 可核对 | **FAIL** | 统计及 V-4 已修复；V-3 可误报身份一致，V-5 同步重试失败 |
| I-8 / master | 父级冻结 Apollo；不扩收费策略 | PASS | C10R search=true/detail=false；C11；paidCalls=0；原设置规则未改 |
| I-9 / master | S1–S6、全局样式、可访问状态 | PASS | C2/C7/C14/C15；完整新增 CSS 5515 bytes 一致；原 CSS 前缀不变 |
| M-1 / master | 保持原重审及原单篇入口 | **FAIL** | 普通及 child 八步读取200；原 child sync-retry 被新分支拒绝，V-5 |
| M-2 / master | 保护及现行资格/Apollo规则 | PASS | C3/C10R/C11；保护与冲突零业务写 |
| M-3 / master | 原文件、first、历史、源计数不变 | PASS | C9/C10R/C11、冻结哈希、写路径 diff |
| M-4 / master | 邮件及采集游标不变 | PASS | 无相关 writer diff；C11隔离快照；重放零业务服务 |
| M-5 / master | 取消保留完成补录/准入 | PASS | C10R late response/child取消；C11取消及显式retry |
| X-0 / master | 真值→生产 old/new→逐条核对 | **FAIL** | 337原文链通过；V-3 实际 target identity 未完整校验 |
| X-1 / master | 首次归档→新解析差异 | PASS | C3/C9/C12/C13；原 first 保留、50组合真实增量 |
| X-2 / master | 新身份→本轮有效身份→验证 | PASS | C3 retained/worker/query/commit；C10R/C11 |
| X-3 / master | ES补齐→人物登记/保护/来源 | PASS | C10R RAW/PERSON故障续接；保护零写；C11 |
| X-4 / master | identity guard→准入→候选同步 | **FAIL** | ordinary 投影重试通过；owned child syncRetry404，V-5 |
| X-5 / master | 原文lease→删除/保护→新UI读取 | PASS | C3/C9；C14 reader200、analysisPOST0；原入口回归 |
| X-6 / master | 持久任务→活动/重入 | PASS | C9相同runId/幂等；C2/C7 lifecycle；V-7仅后续新任务按钮陈旧 |
| X-7 / master | 新作者→旧门禁→档案/人物/核验 | PASS | C10R真实consume、归属收据；C11新增2及重复0 |
| X-8 / master | 父→自动child→准入汇总 | **FAIL** | 自动启动/准入汇总通过；child 同步失败后的既有恢复入口被阻断，V-5 |
| X-9 / master | 原页面→新DOM/API→旧详情回跳 | **FAIL** | NEW/REPAIRED均可读8步；其中既有同步动作404，V-5 |
| 00-I-1 / 00 | record/body/email/name/page单位，逐条身份核对 | **FAIL** | key统计正确；V-3 仅信 candidate key，忽略实际 targetEmail |
| 00-I-2 / 00 | 同原始 bytes/metadata/配置/first 生产重放 | PASS | C1/C3/C12；244原文337组合；输入哈希不变 |
| 00-I-3 / 00 | 离线零业务副作用 | PASS | C12 networkCalls0、writeServicesConstructed0 |
| 01-I-1 / 01 | 明确归属；歧义不猜 | PASS | C3 resolver30/layout8；真实新增绑定50组合；冲突负例 |
| 01-I-2 / 01 | 同页同栏联系块及终止边界 | PASS | C3生产布局反例；C12；编号References既存观察单列 |
| 01-I-3 / 01 | parser版本独立；旧缓存/历史资格/first兼容 | PASS | 源码版本与兼容分支；C3 analysis10/retained7；C10R |
| 02-I-1 / 02 | propose只读、确认后apply | PASS | C3/C10R AUDIT与ENFORCE对照 |
| 02-I-2 / 02 | 跨层保护、窄CAS、PARTIAL不准入 | PASS | C3/C10R保护与RAW/PERSON续接 |
| 02-I-3 / 02 | 当前epoch/lease/parent有效身份；历史基线保留 | PASS | Worker/Repository/Commit路径；C3/C10R |
| 02-I-4 / 02 | persisted receipt限定提交，不泛化guard | PASS | Commit受限身份变化路径；C3 commit7/query22/integration36 |
| 02-I-5 / 02 | 有限phase、恢复、旧外部证据失效 | PASS | retained identity/worker路径及C3/C10R |
| 03-I-1 / 03 | 已终态source、keyset、maxRecordId固定跨页 | PASS | C9 121records/7pages、sourceFrozen、404/409 |
| 03-I-2 / 03 | 合法状态、request-key、active-key单例 | PASS | C3 repository12/service6；C9重复runId及冲突 |
| 03-I-3 / 03 | PAPER/TARGET、immutable input、schema1、六步、lease | PASS | C3 repository；C9 schema/trace；C10R/C11 |
| 03-I-4 / 03 | record与target单位分开；AUDIT actuals0 | PASS | C9、C14 121/121及actuals0、proposal1/1 |
| 03-I-5 / 03 | lease释放、默认单并发、AUDIT无业务/外呼、取消保存 | PASS | C3/C9/C10R；C10首轮取消时序异常保留为未归因观察 |
| 04-I-1 / 04 | 原consume/受限repair、无第二套覆盖 | PASS | Apply→consumeArchivedPaper→原consume；C10R/C11 |
| 04-I-2 / 04 | durable副作用收据、归属续接、重复增量 | PASS | C10R AFTER_RAW/PREPARE/START/ADMISSION及PERSON失败/STALE；C11 |
| 04-I-3 / 04 | 父冻结配置、稳定分组、服务端自动start、租约 | PASS | C10R 100+1稳定分组/冻结/late prepare围栏 |
| 04-I-4 / 04 | 互斥结果、真实核验终态、现有syncRetry、取消范围 | **FAIL** | V-1已修复；V-5 child候选同步失败无法沿用原syncRetry |
| 04-I-5 / 04 | 原bytes生成permit；identity与qualification分离；CLI原义 | PASS | C10R PARTIAL与legacyCLI；C11 |
| 05-I-1 / 05 | API唯一状态、requestKey恢复、source scope | PASS | C2/C7/C9/C14；V-7作为非阻断陈旧preview保留 |
| 05-I-2 / 05 | 默认AUDIT、明确ENFORCE、失败disabled、关闭不取消 | PASS | C2/C7；C14 Escape/关闭 cancelPOST0；V-6为非必需模式锁定缺陷 |
| 05-I-3 / 05 | 绑定/档案/准入分开；中文原因；不误称未分析 | PASS | C14真实AUDIT完成、proposal1/1、actuals0、长中文500原因；V-4已解决 |
| 05-I-4 / 05 | DOM/CSS、安全textContent、分页/旧响应/权限 | PASS | C2/C7/C15；C9真实401；全局401重定向边界另列 |
| S-1 / 05 | 整批入口结构/字号/边距 | PASS | C15逐字CSS/DOM；C14实际页面 |
| S-2 / 05 | 确认弹窗结构、状态、按钮可达 | PASS | C14四viewport；Escape仅关当前dialog |
| S-3 / 05 | 服务端统计、六卡/手机三列、分页 | PASS | C14 121/121、四viewport；targetTotalKnown来自summary |
| S-4 / 05 | 逐项中文结果、六步与既有八步入口 | PASS | NEW及REPAIRED八步GET200；六步详情；原文只读。同步动作另由V-5阻断 |
| S-5 / 05 | 完整新增CSS逐字一致，全局不改 | PASS | 含terminal LF 5515 bytes一致，原CSS完整前缀保留 |
| S-6 / 05 | 三块DOM、动态节点、安全及生命周期 | PASS | 三块骨架核对；无新inline style/innerHTML；真实Escape/focus |
| 06-I-1 / 06 | 固定输入与逐条实际身份；外部变化单列 | **FAIL** | 正常固定输入通过；V-3错误实际邮箱仍EXPECTED_MATCH |
| 06-I-2 / 06 | 逐条标识与真实增量，缺口原因，不拿总数代替身份 | **FAIL** | 完整计数已报告；V-3可使同key错误实际target被当正确 |
| 06-I-3 / 06 | 隔离验证、真实门禁、生产/发布另批 | PASS | C3/C5R/C9–14隔离；初始失败及NOT_RECORDED如实保留；未生产执行 |
| SCOPE / master+amendments | 逐child白名单、数量、≤2子系统、共享文件串行 | PASS | 各child6/7/11/14/13/19/5，未授权0；repair十文件；scope.json |
| MIGRATION / master+03+06 | 不改已应用迁移，真实MySQL升级 | PASS | 新V173；C5R48 tests空库/升级成功 |
| BUILD / master+06 | JDK11、Node、WAR分开验证 | PASS | C1–7；C5初始退出码缺失由C5R真实回执补齐 |
| RUNTIME / master+06 | 三独立JVM、原bytes、实际浏览器 | PASS，有失败历史 | C9/C10R/C11/C12/C14；C10首轮FAIL未删除 |
| NON-GOALS / master | 不做OCR/猜名/扩下载/提并发/放宽资格/47候选/整批删除重构/403422修复/生产发版补跑 | PASS | 累计diff及写路径检查，无范围外业务实现 |
| RELEASE / master+06 | 发布前人工与独立发布授权 | PENDING | 本轮无发布、push、merge、生产补跑 |
| MANUAL / master+children | 人工验收及签字 | PENDING | 见下表；机器浏览器不代替人工 |

### 扩展文件授权矩阵

每行独立核对 amendment 原规则。这里的 PASS 仅指扩展改动符合批准范围；不抵销 V-3/V-5 的行为 FAIL。

下列源码路径均相对于 W。

| 文件 | 授权 | Verdict / 独立核对 |
|---|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` | A1/A2；I-4/I-6/X-4 | PASS：仅受控 changed-identity persisted-admission seam；保护与revision条件保留 |
| `src/main/resources/db/migration/V173__create_discovery_paper_reprocess.sql` | A3；I-2/I-5/X-1/X-5 | PASS：使用未占用V173；未改已应用V172；真实迁移通过 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperBodyAnalyzer.kt` | A4/A5；I-4/I-5/X-1/X-5 | PASS：可选checkpoint及取消窄传播；默认调用/规则不变 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt` | A4/A5；同上 | PASS：传递checkpoint；不改资格事实规则 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | A4/A5；同上 | PASS：按页checkpoint；原默认及版本兼容 |
| `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt` | A6/A7；I-5/I-7/X-6 | PASS：移除incidental固定清单/数量；真实摘要断言保留，19 tests |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt`，child04 delta | A8/A9；I-4/I-5/I-6/X-3/X-4/X-7/X-8 | PASS：durable receipt及REVIEW；不新增替代业务writer |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | A8/A9；同上 | PASS：原consume链父许可/逐副作用围栏/归属续接；普通默认不改 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`，child04 delta | A8/A9；同上 | PASS：paper-owned epoch0 dispatch/late-response围栏；无全局放宽 |
| `src/main/resources/static/discovery-recheck.js` | A10/A11；I-5/I-7/I-9/X-5/X-6/X-8/X-9 | PASS：公开原八步只读opener；保留原DOM/动作 |
| `src/test/js/discoveryRecheck.test.js` | A10/A11；同上 | PASS：opener回归；相关行为断言保留 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPaperReprocess.kt`，child05 delta | A10/A11；同上 | PASS：只读summary/preview字段 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt`，child05 delta | A10/A11；同上 | PASS：持久记录权威汇总，未让前端抓全页计算 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt`，child05 delta | A10/A11；同上 | PASS：只读summary/缺源preview组合 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepositoryIntegrationTest.kt`，child05 delta | A10/A11；同上 | PASS：权威计数/preview回归；未减少验收 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessServiceTest.kt`，child05 delta | A10/A11；同上 | PASS：只读DTO/preview回归 |
| `src/test/js/discoveryReview.test.js` | A10/A11；同上 | PASS：资源清单适配；行为断言保留 |
| `src/test/js/mailboxChatBehavior.test.js` | A10/A11；同上 | PASS：仅直接受影响资源断言；邮件行为不改 |
| `src/test/js/mailboxGroupPush.test.js` | A10/A11；同上 | PASS：同上，未重钉新资源总数 |
| `src/test/js/meetingConfirmationAssets.test.js` | A10/A11；同上 | PASS：同上 |
| `src/test/js/trustReplyWorkbenchSharedMount.test.js` | A10/A11；同上 | PASS：同上，共享挂载行为保留 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt`，child06 delta | A12/A13；I-1/I-3/I-6 | PASS：恰为七处Flyway fixture target V161→V173；无业务断言削弱 |

完整 base..candidate 共123变更文件；产品/测试/脚本/审计并集61个，未授权0。逐 child 数量及边界：

| Child | 边界 | 实际/批准 | 未授权 |
|---|---|---:|---:|
| 00 | `3a425461..1f730373` | 6/6 | 0 |
| 01 | `1f730373..1979768f` | 7/7 | 0 |
| 02 | `1979768f..47393695` | 11/11 | 0 |
| 03 | `47393695..ff7e6750` | 14/14 | 0 |
| 04 | `ff7e6750..736535ce` | 13/13 | 0 |
| 05 | `736535ce..46bcb8a3` | 19/19 | 0 |
| 06 | `46bcb8a3..e213acb5` | 5/5 | 0 |

回执：[累计 scope](/tmp/paper-aggregate-epoch2-ei2_5x6s/scope.json)、[逐 child 文件](/tmp/paper-aggregate-epoch2-ei2_5x6s/child-scope.json)。

### Finding Lineage

| Finding | Severity / State | 本轮证据 |
|---|---|---|
| V-1 | P1 — **RESOLVED** | 普通与已有档案child取消后，同父显式retry继续实际资格；成功副作用及历史保留；C1/C4/C11 |
| V-2 | P1 — **RESOLVED** | 使用实际宿主Escape handler；confirm/detail只关闭当前dialog，hostCloses0、focus回触发器、cancelPOST0 |
| V-3 | P1 — **PERSISTENT** | doc/outcome/duplicate等反例已补；实际target邮箱与稳定emailHash仍未校验，错误/缺失邮箱被接受 |
| V-4 | P1 — **RESOLVED** | 读取真实`summary.targetTotalKnown`；完成AUDIT显示目标总量固定 |
| V-5 | P1 — **REGRESSION** | 原NEW八步读取及ordinary sync已修；新分支却把既有owned recheck child路由到epoch0-only guard，公开sync404 |
| V-6 | P2 — **PERSISTENT / EXCLUDED** | 确定性503后pending key仍锁模式；未证明违反明确必需验收 |
| V-7 | P2 — **PERSISTENT / EXCLUDED** | 完成后preview.activeRun陈旧，需reload再开新任务；原任务恢复/显示正常 |

### Findings

#### P1

**V-3 — 实际 target 邮箱未验证，错误实际身份仍被报告为匹配。**

要求：master I-1、I-7、X-0；00-I-1；06-I-1/I-2；repair R-3 明确要求验证稳定键及 actual target identity。

源码：

- [reconcile_paper_identity_replay.py:107](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py:107)：展开真实run target，把 `target.email` 存为 `targetEmail`。
- [同文件:52](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py:52)：`evidence()` 对run仅检查outcome、doc存在及已知expected doc；不检查实际邮箱。
- 同文件186–198：使用candidate提供的稳定key选行，没有核验targetEmail与candidate emailHash一致。

新鲜真实行对照：

| 项目 | 有效控制 | 错误邮箱 | 缺失邮箱 |
|---|---|---|---|
| recordId | 31372 | 31372 | 31372 |
| candidate expected emailHash | `1522c43ad4d32fd525a502bb` | 同左 | 同左 |
| 实际target邮箱hash | `1522c43ad4d32fd525a502bb` | `ba944dee8305ef588a9d7bfc` | null |
| actual docId | `0000-0002-0178-9985` | 同左 | 同左 |
| CLI exit | 0 | 0 | 0 |
| 输出 | BOUND / EXPECTED_MATCH | **BOUND / EXPECTED_MATCH** | **BOUND / EXPECTED_MATCH** |

错误邮箱为 `different-target@wrong.example`。有效控制邮箱从不可变原输入取得并核对hash。三个输入仅改变实际target邮箱；稳定candidate键和docId保持一致。

wrong-doc、unknown-outcome、duplicate、SKIPPED-wrong-doc 本轮已能输出 INVALID/REGRESSION，因此此前修复有实质效果；邮箱缺口仍是同一身份校验根因，保留 V-3。现有合成有效测试使用与实际邮箱无关的固定emailHash，不能区分此缺陷。

影响：报告可能把错误实际target算成正确绑定；相同总数及candidate自报key不能证明同一身份。未宣称本次正常337-row报告已经发生该错误。

最小涉及范围：归并脚本的实际身份验证及现有 replay-tool 回归测试。

证据：[全部精确CLI与结果](/tmp/paper-aggregate-epoch2-ei2_5x6s/identity-probe-results.json)、[错误邮箱输入](/tmp/paper-aggregate-epoch2-ei2_5x6s/identity-wrong-email-run.json)、[错误邮箱输出](/tmp/paper-aggregate-epoch2-ei2_5x6s/identity-wrong-email.csv)。

**V-5 — ordinary例外误覆盖已有recheck child，候选同步重试回归为404。**

要求：master M-1、I-7、X-4/X-8/X-9；04-I-4；repair R-5 要求新增ordinary例外时保留原recheck行为及guard。

源码：

- [DiscoveryRecheckService.kt:367](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt:367)：`syncRetry` 用 `isPaperReprocessOwned(id)` 判分支。
- 同文件373：所有paper-owned run均调用 `requirePaperOwnedItem`。
- [DiscoveryVerificationQueryService.kt:188](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt:188)：该函数只接受 `reviewEpoch == 0`。
- 归属查询同时覆盖ordinary run和本父拥有的recheck child；后者合法epoch等于child runId，因而被拒绝。

隔离实际结果：

| 路径 | 本轮结果 |
|---|---|
| ordinary parent30716/item1八步读取 | 200，8真实步骤 |
| child30717/item4八步读取 | 200，8真实步骤 |
| ordinary公开sync-retry | 200；ERROR→PROJECTED；admissionRevision1保留；provider19→19 |
| child item4 | COMPLETED；admissionRevision0，合法非null；actualAdmitted1 |
| child公开sync-retry | **404 RECHECK_ITEM_NOT_FOUND** |
| 当前临时MySQL将child投影单独改为ERROR后 | 状态及准入不变；公开API和实际页面“仅重试候选同步”仍 **404** |

故障仅注入当前临时fixture容器 `ec1b46de10e4a17837a3c5127ea4a3417c864faeaa1876033d74a0e2eb93fddd`、数据库 `paper_reprocess_e2e`；未操作生产。SQL前后证明投影 PROJECTED→ERROR，state仍COMPLETED、revision仍0。页面DOM及HTTP分别证明实际按钮错误。API文件中的缓存target receipt仍显示原PROJECTED；当前投影以SQL为准。

修复前 `syncRetry` 对此child走原 `requireRecheck/requireRecheckItem`，当前repair新分支引入拒绝。原V-5读取缺陷已修，但同一修复路径损坏既有child同步恢复，故标记 **REGRESSION**。

最小涉及范围：`DiscoveryRecheckService.syncRetry` 的ordinary/child判定与现有回归测试；不能全局放宽epoch或归属guard。

证据：[SQL](/tmp/paper-aggregate-epoch2-ei2_5x6s/child-projection-fault-sql.log)、[真实HTTP](/tmp/paper-aggregate-epoch2-ei2_5x6s/child-sync-error-http.json)、[页面DOM](/tmp/paper-aggregate-epoch2-ei2_5x6s/child-sync-error-ui.txt)、[ordinary同步控制](/tmp/paper-aggregate-epoch2-ei2_5x6s/ordinary-sync-api.json)。

#### P2

- **V-6，持续、非阻断、排除于本轮repair授权**：确定性503保留pending key并锁模式。既有明确验收要求请求不确定时复用key；未额外推导“任意失败后必须切换模式”的强制需求。
- **V-7，持续、非阻断、排除于本轮repair授权**：恢复任务完成后preview.activeRun未即时刷新，reload后可新开。未将此提升为任务恢复失败。

#### Observations

**O-E2-01 — C10取消时序首轮 `Trace lease lost`，根因未定。**

首轮主线程在 `paperBatchCancellation` 的AFTER_PREPARE/AFTER_START循环中，经 `Worker.process → fail → endStep` 抛错；`endStep` 对失效trace lease抛异常，generic catch里的fail可能再次抛错并遮蔽先前异常。随后fixture teardown的连接关闭/rollback异常属于后续清理，不能据此认定最初错误由数据库环境引起。

相关通用endStep/Trace路径并非本次repair新增；但也未证明首轮错误为既存或环境问题。C3该36-test类通过；一次相同完整C10R执行亦通过所有取消窗口及最终runtime标记。保留首轮FAIL及不稳定性，不声明修复，不凭单次失败新增未经归因P1，也不声称全部运行一次即绿。

其他此前排除项见 RECORD_ONLY 映射；未重开无关清理。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | 本轮重新判断 |
|---|---|---|---|
| 00 RECORD_ONLY | I-1/I-2/X-0 | N/A | 无新增条目；其固定输入要求已在C12/C13重验 |
| 01 O-01：可选旧294 corpus | I-1/X-0 | Observation，非阻断 | 原可选目录缺失，C3唯一skip；必需30711原文244/337全部新鲜重放 |
| 01 O-02：编号References | 01-I-2、I-1 | 既存Observation | 原路径行为未由本实现引入；不扩猜测规则，47候选仍排除 |
| 02：无新增RECORD_ONLY；跨存储边界 | I-4/I-6/X-3/X-4 | Evidence boundary | 窄CAS与收据通过；没有宣称MySQL+ES原子提交 |
| 03 O-01：generic summaryRule=null | I-7/X-6 | 既存Observation | 通用raw fallback未改；本功能SQL/summary API权威统计已验证 |
| 04 O-01：loopback ES/provider/protection fixture | I-3/I-4/X-3/X-4 | Evidence boundary | 真实SQL和服务链；ES为loopback CAS，provider固定响应；不等于线上引擎验收 |
| 04 O-02：generic summary | I-7/X-6 | 同03 O-01 | 未要求新增通用task summary shape |
| 04 O-03：历史rapid-tick投影trace重复键 | I-5/I-7/X-4 | 既存/未归因Observation保留 | 未将C10R通过当通用并发修复；本轮C10取消Trace lease lost另立O-E2-01，未混同 |
| 05 F-05 | 05-I-1/I-2 | V-6 PERSISTENT，非阻断 | 503后模式锁定仍在；保持原排除决定 |
| 05 O-07：旧重复100/125截图 | I-9/S-1..S-6 | 新证据补齐 | 本轮四个不同CSS viewport单独实测；未复用重复截图 |
| 05 O-08：旧run/config标签 | I-8/I-9 | 历史说明保留 | 本轮AUDIT9002冻结revision0、search/detail=false；浏览器settings适配局限明确；C10R/C11另测true/false |
| 05 O-09：proposal正数 | I-7/05-I-3 | PASS；V-4已解决 | 真实API/SQL/UI proposal1/1，actuals0；summary.targetTotalKnown正确 |
| 05 O-10：summary-null文案 | I-7 | 非阻断Observation | 文案问题未证明业务计数错误，不扩修 |
| 05 O-11：401/JDK/旧八步证据 | I-7/I-9/X-9 | 必需证据补齐，局限保留 | 真实401 runtime、组件权限测试、Java11.0.32.1；NEW/REPAIRED实际八步均200。全局app401重定向未全链宣称；V-5同步404单列 |
| 06 O-01：gain标签语义 | I-1/I-7 | 历史措辞Observation | 本轮明确50组合/49邮箱/39记录，不称新增/准入人数；V-3属另一实际校验问题 |
| 06 O-02：V171升级措辞 | I-2/06-I-3 | PASS，历史说明保留 | C5R实际覆盖更早V170→V173及空库，48 tests |
| 06 O-03：生产30711 ENFORCE/live ES/ORCID/Apollo | I-3/I-4/06-I-3 | NOT_RUN，明确范围外 | 未进行生产补跑或真实付费外呼，不冒充隔离测试结果 |
| 06 O-04：package测试口径 | BUILD | PASS，分开记录 | C6仅JS/WAR；Kotlin与MySQL分别C1/C3/C4/C5R |
| 06 O-05：报告历史行数634/627 | I-1 | 非阻断文档Observation | 不影响固定337组合；本轮独立CSV逐条337 |
| 06 F-01：报告身份/门禁措辞修正 | I-1/06-I-3 | RESOLVED，保留历史 | 已读`3f042fc`与当前ac6计划；没有把历史NOT_RUN改报PASS |

### 浏览器证据与限制

实际AUDIT run9002处理121/121；六卡为121、121、0、0、0、0；proposal1/1，actuals0。完成页显示“目标总量已固定”。

| CSS viewport | 对应检查 | dialog宽度 | 主按钮bottom | 结果 |
|---|---|---:|---:|---|
| 1440×900 | 1440，100% | 700 | 747.125 | 可达 |
| 1152×720 | 1440，125%等效viewport | 700 | 657.125 | 可达 |
| 390×900 | 390，100% | 358 | 826.8671875 | 可达 |
| 312×720 | 390，125%等效viewport | 280 | 671 | 可达 |

手机三列、无根页面横向越界；1152保留六列。使用CSS viewport等效缩放，**未宣称操作原生浏览器zoom**，未使用CSS zoom伪造。

关键证据：

- [四viewport指标](/tmp/paper-aggregate-epoch2-ei2_5x6s/audit-layouts.json)
- [确认弹窗Escape](/tmp/paper-aggregate-epoch2-ei2_5x6s/audit-confirm-escape.json)
- [完成/500/汇总证据](/tmp/paper-aggregate-epoch2-ei2_5x6s/audit-complete-evidence.json)
- [1440确认](/tmp/paper-aggregate-epoch2-ei2_5x6s/audit-confirm-1440-100-final.jpg)
- [312等效viewport确认](/tmp/paper-aggregate-epoch2-ei2_5x6s/audit-confirm-390-125-equivalent.jpg)
- [手机完成](/tmp/paper-aggregate-epoch2-ei2_5x6s/audit-complete-390-100.jpg)
- [真实500及长中文原因](/tmp/paper-aggregate-epoch2-ei2_5x6s/audit-500-long-reason.jpg)
- [NEW八步](/tmp/paper-aggregate-epoch2-ei2_5x6s/cross-new-eightsteps.jpg)
- [REPAIRED八步](/tmp/paper-aggregate-epoch2-ei2_5x6s/cross-repaired-eightsteps.jpg)
- [只读原文](/tmp/paper-aggregate-epoch2-ei2_5x6s/cross-reader.jpg)
- [实际跨路径HTTP/DOM](/tmp/paper-aggregate-epoch2-ei2_5x6s/cross-evidence.json)

限制如实保留：

- Paper03临时fixture只stub `freeze()`，`resolve()`为空，初始确认页因此失败；初始回执保留。临时UI adapter供应同一冻结DTO：revision0、search=false/detail=false。故此浏览器实验不证明真实保存Apollo设置endpoint；冻结业务另外由C10R/C11验证。
- 初始child诊断用truthiness选择admissionRevision，漏掉合法0；只修正临时诊断selector为非null后才发实际child POST，未改产品。
- 浏览器cross fixture未注入固定12-target测试的RAW故障，所以显示新增3/补齐2/准入3；不能冒充C11要求的2/2/2。固定要求由独立C11验证。
- cross第二tab使用默认1280×720；未把其截图标为1440。
- child错误截图仅展示对话框滚动区域，不能单靠图片证明错误banner；404由实际DOM文本、HTTP及SQL证明。
- 500由明确临时loopback proxy故障产生，真实HTTP500；恢复后实际任务完成，保留最后快照。不是生产故障实验。
- 使用实际app Escape handler，hash `2829faf1a2ab4a988c5ea7d772f50ed0432b5f6b37f0ab2a4d044c4f429c4596`；没有用独立模拟handler替代。
- 临时Java/browser fixture不写生产；浏览器机测不构成人工验收。fixture停止标记已发，未补造其OS退出回执。
- V-3诊断仅针对选中真实固定行；部分run export缺少其他行，不作为完整业务效果报告。
- 原始PDF仍位于批准安全目录，未入Git；报告未输出原文邮箱。
- C10首轮异常根因仍未证实；一次完整复跑成功不证明稳定性问题消失。

### 人工验收

全部仅记 **PENDING**：

| 来源 | 项目 |
|---|---|
| Master | A-1 原按钮补绑；A-2 漏建档与自动衔接；A-3 只读对照与历史；A-4 保护/冲突/取消；A-5 恢复与样式；A-6 对照结论 |
| Child00 | A-1、A-2 |
| Child01 | A-1、A-2、A-3 |
| Child02 | A-1、A-2、A-3、A-4 |
| Child03 | A-1、A-2、A-3 |
| Child04 | A-1、A-2、A-3 |
| Child05 | A-1、A-2、A-3、A-4 |
| Child06 | A-1、A-2、A-3 |

无人工reporter、ACCEPT、签字或发布结论被制造。

### Convergence

**DIVERGING**。

V-1、V-2、V-4已有真实解决证据；V-3的根因有所收窄，但仍违反强制身份验收。V-5在补入ordinary例外时引入同级P1回归，损坏此前可用的owned child同步路径，后续工作已必须恢复该既有行为。

因此不满足 PROGRESSING 的“无 equal-or-higher new regression”条件，也不是相同blocking set反复出现的 STALLED。判断依据是新增受损行为及修复范围变化，未按“5项降为2项”计数判进展。

### Repair planning: N/A

本轮未调用repair-p设计新轮次，未覆盖已有修复稿。现有精确repair SHA仍为：

`47b6bccdb128dd02177fe0d629e251afe781064d75dcbc5f18c07ab24a829110`

### Next Action

停止自动修复，交人类裁决：保留当前修复并批准针对V-3/V-5的不同修复方法，或决定撤销最新修复/调整范围。当前不应重新执行旧repair artifact。

暂停依据：[review-p/SKILL.md](/Users/lukai/.agents/skills/review-p/SKILL.md) 路由要求：`FAIL | STALLED or DIVERGING | Stop and request human adjudication.`

No product code was modified.


## Epoch 3 — 2026-11-03T15:30:19+08:00

- Master plan: docs/plans/2026-11-01/paper-reprocess-master.md (sha256 85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e)
- Governing master identity: same sha256; recorded commit 53c2b0018e239e3c30db25942e99c2cddc69e9d4
- Master identity state: AMENDMENT_RECORDED; A1–A13/master rules/reasons/human approval and per-file matrix carried below
- Boundary: 3a425461dbe6088e4d4dda1a53141416a392670c..7e131474f403d9b28178148909180ac8f1d7d1bb
- Evidence HEAD at review: 30b2b46bbf925673a5463990ad1474c16da84c5b
- Reviewer: /root/paper_reprocess_aggregate_epoch3; fork_turns=none; attempt 3 after capacity/usage-limit interruptions
- Result: FAIL
- Convergence: PROGRESSING
- Repair artifact/result: docs/plans/fix/paper-reprocess-master/repair.md; sha256 50cc5a2fceedd09541a1efb080894347e7714262285fcea77f0acf52d7bfbfc9; DRAFT_READY
- Repair evidence mode: DURABLE_HANDOFF for executed ca1e7a97 candidate; new 50cc5a2f draft NOT_APPROVED
- Manual status: PENDING

### Complete Independent review-p Output

## Verification Result: FAIL

Plan：[paper-reprocess-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-master.md)
Phase：aggregate/master
Reviewer：/root/paper_reprocess_aggregate_epoch3
Implementation boundary：3a425461dbe6088e4d4dda1a53141416a392670c..7e131474f403d9b28178148909180ac8f1d7d1bb
Evidence HEAD：30b2b46bbf925673a5463990ad1474c16da84c5b
Convergence：**PROGRESSING**
Manual acceptance：**PENDING**

V-5已解决；V-3仍可通过候选字段混淆证据来源；V-2仍遗漏提交期间自然BODY焦点的Escape。全部必需命令已完成，未重复已通过门禁。

### 身份、权限与边界

- Worktree：/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
- Branch：fast/paper-reprocess-master
- Resolution：EXPLICIT；已通过注册worktree、分支、HEAD及祖先关系核对；无需发现脚本。
- Governing master SHA-256：85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e；当前字节等于记录commit 53c2b0018e239e3c30db25942e99c2cddc69e9d4。
- Invoked identity：SAME；master identity state：AMENDMENT_RECORDED。已读选定ledger的历史Exact Governing Diff Against Invoked Copy；本次没有新差异或未批准修订。
- 七份child-plan SHA均匹配输入bundle；28份brief/execution/fix-log/verify-log存在；fast ledger中的36个完整commit身份均存在且为当前证据HEAD祖先。
- 七个child均为终态LIGHT_PASS或LIGHT_PASS_WITH_NOTES。计划里的“待执行”旧描述未被当作执行状态。
- 已检查完整累计产品边界与受影响运行路径。57个src/、scripts/变更文件全部落入七份修订后child清单的并集，越界文件0；清单文件数为6/7/11/14/13/19/5。06第五文件由A12/A13明确授权。
- base→7e131474→30b2b46及291fb34→7e131474祖先关系成立。产品HEAD后只有repair-execution.md证据变化。
- 累计修复291fb34..7e131474的产品/测试变化严格为下列四文件，无其他产品变化：

| 文件 | 授权核对 |
|---|---|
| [reconcile_paper_identity_replay.py](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py) | PASS |
| [DiscoveryRecheckService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt) | PASS |
| [DiscoveryPaperIdentityReplayToolTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt) | PASS |
| [DiscoveryPaperReprocessEndToEndTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessEndToEndTest.kt) | PASS |

Post-repair evidence mode：**DURABLE_HANDOFF**。

- 已执行repair身份：ca1e7a97965282cd3bd91354d5cbf7cd3e1e3aa8a39ef2bd77a4dbed8681a3aa。
- [repair-execution.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/repair-execution.md) SHA：05f9293af71f5767a8c4e047b2773953deb4171e24fd6c2265254c7d3ec91099；记录针对该稿的人类明确$execute-p调用、Executor PaperRepairExecutor2、提交边界及READY_FOR_VERIFICATION。
- 旧稿47b6…的批准单独保留，未借用于新稿。
- 本reviewer在产品提交后独立获得，身份不同于所有记录writer/verifier及两位repair executor；未继承开发或轻量核验对话。
- 本epoch因容量/usage-limit中断后继续，保留全部已有回执和同一reviewer；未重启已完成门禁。进程结束后旧session已关闭，不视作门禁失败。

### Governing Amendments

以下每行的批准均为 **H**：

> HUMAN：继续 后续授权都继续 直到所有子计划都完成且可以运行review-fast-p的阶段 不要执行review-fast-p，我会交给其他agent执行 — 2026-11-01T23:28:37+08:00

批准及before/after身份来源：[fast-p ledger](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fast/paper-reprocess-master/ledger.md)，SHA dc2d86b0f225fbcec9f86a2bfa5f089cc3075fe541881dd5a8ceb88689274e09。

| ID | Master rule | 原因及治理内容 | Approval |
|---|---|---|---|
| A1 | I-4/I-6/X-4；顺序与门禁 | 02扩至11文件，覆盖既有persisted-admission窄接缝，不减少测试 | H |
| A2 | I-4/I-6/X-4 | 授权DiscoveryReviewService受控身份变化及原测试回归 | H |
| A3 | I-2/I-5/X-1/X-5；顺序与门禁 | V172已占用，新增迁移改用V173；保留已应用迁移与验收 | H |
| A4 | I-4/I-5/X-1/X-5；顺序与门禁 | 03扩至13文件，仅补解析链按页取消/预算checkpoint | H |
| A5 | I-4/I-5/X-1/X-5 | 三处parser checkpoint接缝；默认行为、规则、版本及验收保留 | H |
| A6 | I-5/I-7/X-6；顺序与门禁 | 03扩至14文件，覆盖直接受影响的task catalog测试 | H |
| A7 | I-5/I-7/X-6 | 更新TaskExecutionSummaryExtractorTest；移除过时固定全集/数量，不重钉新数量 | H |
| A8 | I-4/I-5/I-6/X-3/X-4/X-7/X-8；顺序与门禁 | 04扩至13文件，补持久收据、原consume及ordinary worker父许可接缝 | H |
| A9 | I-4/I-5/I-6/X-3/X-4/X-7/X-8 | 授权receipt、owned-consume及epoch0围栏；普通默认行为、验收保留 | H |
| A10 | I-5/I-7/I-9/X-5/X-6/X-8/X-9；顺序与门禁 | 05扩至19文件，覆盖只读opener、权威summary/preview及直接受影响测试 | H |
| A11 | I-5/I-7/I-9/X-5/X-6/X-8/X-9 | 只读统计、缺源preview、原八步入口；保留精确CSS/DOM及浏览器验收 | H |
| A12 | I-1/I-3/I-6；顺序与门禁 | 06第五文件例外：WorkerTest Flyway fixture目标V161→V173 | H |
| A13 | I-1/I-3/I-6 | 仅提升既有migration fixture目标；断言、业务、12目标及迁移/报告验收不减 | H |

### Commands

全部在上述retained worktree执行。所有Maven/Java均使用：

    JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home
    Maven=/opt/homebrew/bin/mvn

JVM XML确认Java 11.0.32.1。Maven/target操作串行，每条1200秒watchdog，未触发。

完整、不省略的C1–C13实际命令与退出码见[receipts.json](/tmp/paper-aggregate-epoch3-zztjjl1a/receipts.json)；补充命令及展开后的classpath argv见[supplement-receipts.json](/tmp/paper-aggregate-epoch3-zztjjl1a/supplement-receipts.json)。

| ID | 实际命令/入口 | Result | 新鲜证据 |
|---|---|---|---|
| C1 | /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true，上述JAVA_HOME | PASS；exit0，283.29s | 14 tests：Replay9、E2E5；0 failures/errors/skips；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd01.log) |
| C2 | node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js | PASS；exit0，0.67s | 46 tests/pass，0 fail；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd02.log) |
| C3 | 下方完整十五类命令 | PASS；exit0，840.33s | 206 tests，0 failures/errors，1明确可选skip；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd03.log)、[逐类XML统计](/tmp/paper-aggregate-epoch3-zztjjl1a/c3-counts.json) |
| C4 | /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true，上述JAVA_HOME | PASS；exit0，266.28s | 5/0/0/0；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd04.log) |
| C5 | /opt/homebrew/bin/mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true，上述JAVA_HOME | PASS；exit0，754.88s | 48/0/0/0；实际MySQL空库→V173、V170→V173；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd05.log) |
| C6 | /opt/homebrew/bin/mvn package -DskipTests，上述JAVA_HOME | PASS；exit0，178.04s | WAR **53,149,457 bytes**；bound JS2037/2037；仅JS/WAR，不替代Kotlin；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd06.log) |
| C7 | node --test src/test/js/*.test.js | PASS；exit0，4.10s | 2037 tests/pass；0 fail/skip；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd07.log) |
| C8 | python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py | PASS；exit0，0.08s | PERSON333、PAPER244、PAPER_BINDINGS337、prior_known7、other_same_name1；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd08.log) |
| C9 | Maven exec:exec，JDK11独立JVM，Paper03RuntimeSmoke；完整命令见下方 | PASS；exit0，62.64s | real HTTP/MySQL；121/7页、同run9002、actuals/provider/archive mutations均0；错误状态/中文/持久结果正向断言；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd09.log) |
| C10 | Maven exec:exec，独立JVM，DiscoveryVerificationIntegrationTest --paper-reprocess-smoke | PASS；exit0，319.49s | PAPER04_RUNTIME_OK；真实SQL/服务/原文、故障恢复/取消/父许可/旧CLI；paidCalls0；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd10.log) |
| C11 | Maven exec:exec，独立JVM，DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke | PASS；exit0，77.42s | 固定12目标2/2/2；取消/retry及ordinary/child sync；PAPER06_RUNTIME_OK；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd11.log) |
| C12 | 下方原始字节replay命令 | PASS；exit0，93.82s | 337行、244原文记录、network0；time93.77 real/134.20 user/3.66 sys；RSS1,259,929,600；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd12.log) |
| C13 | 下方reconcile命令 | PASS，仅正常flat输入路径；exit0，0.14s | 337行、extras0、51 EXPECTED_MATCH/286 EXPECTED_MISSING；不消除V-3反例；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd13.log) |
| C14 | 新鲜Java/Tomcat/MySQL fixture；node /tmp/paper-aggregate-epoch3-zztjjl1a/browser-audit.js及browser-cross.js | **FAIL：合同结果** | 两browser进程exit0，两个server均exit0；pending Escape的hostCloses=1违反S-6。其余检查通过；[合同结果](/tmp/paper-aggregate-epoch3-zztjjl1a/c14-contract-checks.json) |
| C15 | /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperReprocessRuntimeScenarios -DpaperReprocessIt=true -DmysqlIt=true -DtrimStackTrace=false -Dstyle.color=never，上述JAVA_HOME | PASS；exit0，240.88s | 6/0/0/0；补齐child03明确要求；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd15-runtime-scenarios.log) |
| C16 | JDK11 DiscoveryVerificationIntegrationTest --retained-identity-smoke，fresh test classpath | PASS；exit0，56.19s | 补齐child02独立分支；AUDIT虚拟身份零写、ENFORCE窄补名、旧名单/归档不变；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/cmd16-retained-runtime.log) |
| C17 | node --check src/main/resources/static/discovery-paper-reprocess.js | PASS；exit0 | 补齐child05语法门禁；[log](/tmp/paper-aggregate-epoch3-zztjjl1a/node-check-reprocess.log) |
| D1 | 真实固定record31372，七次既有CLI调用 | PASS，逐行判定 | valid/大小写空白→BOUND/MATCH；wrong/missing/null/blank/non-string→INVALID/REGRESSION；[完整argv/结果](/tmp/paper-aggregate-epoch3-zztjjl1a/identity-probe-results.json) |
| D2 | 同一真实record，candidate添加status=ALREADY_RECOVERED，三次既有CLI调用 | **FAIL**，CLI均exit0 | wrong/missing/null实际邮箱均BOUND/EXPECTED_MATCH；[完整argv/结果](/tmp/paper-aggregate-epoch3-zztjjl1a/identity-status-probe-results.json) |
| D3 | node /tmp/paper-aggregate-epoch3-zztjjl1a/static-contract.js | PASS；exit0 | CSS5515 bytes逐字一致、旧CSS前缀不变、DOM齐全、版本三件套同步；[JSON](/tmp/paper-aggregate-epoch3-zztjjl1a/static-contract.json) |
| D4 | 全边界scope/ancestry/hash及git diff --check | PASS | [scope](/tmp/paper-aggregate-epoch3-zztjjl1a/scope.json)、[身份](/tmp/paper-aggregate-epoch3-zztjjl1a/identities.json)；产品/index无修改 |

C3完整命令：

    JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,SourceAuthorEmailResolverTest,DiscoveryPaperLayoutRegressionTest,DiscoveryPaperAnalysisServiceTest,RetainedPaperIdentityServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest,TaskExecutionSummaryExtractorTest,DiscoveryPaperReprocessApplyServiceTest,DiscoveryPaperBackfillServiceTest,DiscoveryRecheckServiceTest,DiscoveryVerificationIntegrationTest -DmysqlIt=true -DpaperReprocessIt=true

C9–C11完整命令：

    JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.Paper03RuntimeSmoke'
    JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --paper-reprocess-smoke'
    JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke'

C12–C13完整命令：

    /usr/bin/time -l /Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json "/tmp/paper-aggregate-epoch3-zztjjl1a/replay.json"
    python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json --old-replay /tmp/paper-reprocess-30711-baseline.json --new-replay "/tmp/paper-aggregate-epoch3-zztjjl1a/replay.json" --audit-directory docs/audits/2026-11-01-discovery-30711-pdf --csv "/tmp/paper-aggregate-epoch3-zztjjl1a/reconciled.csv" --report "/tmp/paper-aggregate-epoch3-zztjjl1a/reconciled.md" --runtime-evidence /tmp/paper-reprocess-30711-runtime.json

C16使用下列fresh dependency命令解析classpath，再由Java直接运行；完整展开argv保存在supplement receipt：

    JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=/tmp/paper-aggregate-epoch3-zztjjl1a/classpath.txt
    /Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -DmysqlIt=true -cp "target/test-classes:target/classes:$(cat /tmp/paper-aggregate-epoch3-zztjjl1a/classpath.txt)" com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --retained-identity-smoke

C14临时harness编译exit0；其Java源码、HTML、browser脚本、HTTP/SQL JSON及截图全部保留在同一新目录。没有修改产品来适配fixture。

C3逐类新鲜计数：

| Class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| ReplayTool | 9 | 0 | 0 | 0 |
| Resolver | 30 | 0 | 0 | 0 |
| LayoutRegression | 8 | 0 | 0 | 1 |
| Analysis | 10 | 0 | 0 | 0 |
| RetainedIdentity | 7 | 0 | 0 | 0 |
| Worker | 24 | 0 | 0 | 0 |
| Commit | 7 | 0 | 0 | 0 |
| Query | 22 | 0 | 0 | 0 |
| ReprocessService | 6 | 0 | 0 | 0 |
| ReprocessRepositoryIntegration | 12 | 0 | 0 | 0 |
| TaskSummary | 19 | 0 | 0 | 0 |
| ApplyService | 2 | 0 | 0 | 0 |
| Backfill | 4 | 0 | 0 | 0 |
| Recheck | 10 | 0 | 0 | 0 |
| VerificationIntegration | 36 | 0 | 0 | 0 |
| **Total** | **206** | **0** | **0** | **1** |

唯一skip为private original corpus replay explicitly skips when unavailable，属于可选旧294语料；必需30711原始字节测试已执行。

本epoch没有Maven失败、watchdog超时或Trace lease lost。较长C3间隔已检查真实进程和[线程栈](/tmp/paper-aggregate-epoch3-zztjjl1a/c3-thread-1115.txt)，最终完整通过；未据此猜测或宣称修复历史不稳定性。C14进程exit0与语义FAIL分别记录，未把浏览器退出成功当作合同通过。

### Fresh Replay结果

- 原文记录244；组合337；全部读取并校验。
- 当前：51 ALREADY_RECOVERED、284 NOT_REPRODUCED、2 REPRODUCED。
- 相比冻结baseline，新增正确绑定50组合，涉及49邮箱指纹、39记录；不是新增档案或准入人数。
- 正常flat reconciliation：51 EXPECTED_MATCH、286 EXPECTED_MISSING、extras0。
- 仍缺绑定原因：NO_PRODUCTION_LAYOUT_CONTACT280、NAME_NOT_LITERAL_IN_PDFBOX_PAGE4、EMAIL_NOT_LITERAL_IN_PDFBOX_PAGE1、AUDITED_PAGE_OUTSIDE_PRODUCTION_SELECTION1。
- 未发现正常337-row replay中的新错绑或旧正确绑定丢失；V-3证明的是实际run验证器仍有拒绝能力缺口。

前后冻结哈希均保持：

| 输入 | SHA-256 |
|---|---|
| Manifest | de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c |
| Baseline | e0fe5b1e008fda6188315f73972f72af79b3897b6ca59f1a4f04c8d452b3d91c |
| Runtime inventory | 5d00ffbc736f653fe64b93b5e667c8baf776aa33b1320716d212291b45e037c3 |

### Contract Matrix

以下机器项均mandatory。源码简称链接：

- [ReprocessRepository](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt)
- [ReprocessService](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt)
- [ApplyService](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyService.kt)
- [VerificationRepository](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt)
- [RetainedIdentity](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/RetainedPaperIdentityService.kt)
- [Commit](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt)
- [Recheck](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt)
- [ReprocessJS](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-paper-reprocess.js)
- [Reconciler](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py)

| ID / Source | Requirement | Verdict | Required evidence / actual evidence |
|---|---|---|---|
| Master I-1 | 原文与生产重放真值决定，325不冒充效果 | **FAIL** | C8/C12/C13固定集合可复算；D2仍接受错误实际目标，V-3 |
| I-2 | SHA/metadata/first/history不覆盖，新结果可追溯 | PASS | C3/C9/C11/C12/C16；owned result、beforeSnapshot、source refs及版本保留 |
| I-3 | 身份事实不直接准入，原资格/邮箱/人物门禁 | PASS | C10/C11真实消费、资格、Commit/project；固定12目标2/2/2；C16原文补名仍PENDING |
| I-4 | AUDIT/保护/确认/取消边界 | PASS | C3/C9/C10/C11；论文AUDIT actual/provider0；保护/迟到响应/取消围栏；原专家模式选项保留 |
| I-5 | DB固定scope、幂等/lease/generation、恢复 | PASS | ReprocessRepository:37/198/270/291/425；C3/C9/C15；ordinary/child显式retry完成 |
| I-6 | 单一consume、窄CAS、原Commit/project，旧CLI只prepare | PASS | ApplyService:141；RetainedIdentity:119；Commit:188；C10 legacy AUDIT/REUSE READY |
| I-7 | 单位分明、六/八步、失败不伪造成功 | **FAIL** | C3/C11计数/trace通过；实际run错误身份仍可标MATCH，V-3 |
| I-8 | Apollo已保存/冻结配置与免费付费策略保留 | PASS | C10/C11 frozenSearch=true/detail=false，paid0；C14真实SQL设置与冻结值一致 |
| I-9 | 新CSS/DOM、文字/可访问操作符合完整契约 | **FAIL** | D3精确样式通过；pending BODY焦点Escape违反S-6，V-2 |
| M-1 | 原重审、单篇解析/下载/删除/保护保持 | PASS | C2/C7/C16；C14现有reader、普通/child八步均实际打开 |
| M-2 | 人工/联系/资格/Apollo保护保持 | PASS | C3/C10/C11；读异常fail-closed、APPLICATION/人工/联系人/CAS反例 |
| M-3 | 原文件/first/旧轮次/源采集计数保持 | PASS | C9/C11/C16原快照及roster hash对照；新增独立execution |
| M-4 | 邮件/收信与采集游标不变 | PASS | 累计diff/写路径无新增相关writer；隔离runtime未装配这些业务写链 |
| M-5 | 取消不撤销已完成补录/准入 | PASS | C1/C11取消保留NEW=1；C10实际已提交准入保持 |
| X-0 | 原文→旧新parser→逐标识对照 | **FAIL** | C12/C13正常集合通过；D2实际run证据协议可被candidate.status替换 |
| X-1 | first→当前解析/差异、版本/缓存一致 | PASS | C3/C12；统一parser；first不覆盖；旧版本及cache失效回归 |
| X-2 | 新绑定→本轮有效身份→资格 | PASS | VerificationRepository:1138/1170；C3/C16；有效hash与profile同步、旧缓存重绑 |
| X-3 | ES窄补齐→人物登记→保护/来源 | PASS | RetainedIdentity:119/134；C3/C10/C11；PARTIAL恢复与原人物登记 |
| X-4 | 身份guard→准入→候选 | PASS | Commit受控authority及原persisted seam；C3/C10/C11；C14两种run同步恢复 |
| X-5 | 显式lease→删除/保护→原文UI | PASS | C3/C9/C15显式attempt/hash及删除竞争；C14 reader只读 |
| X-6 | 持久任务→活动/重入恢复 | PASS | C9 activity同execution；C14运行中第二tab恢复9002；C15中断/retry |
| X-7 | 新作者→原消费→RAW/person/verification | PASS | C10/C11；跨文档唯一邮箱、原消费writer、二次任务新增0 |
| X-8 | 父任务→ordinary/child→准入汇总/详情 | PASS | C1/C10/C11/C14；自动prepare/start；合法revision0；两个实际8步及同步200 |
| X-9 | 原挂载/生命周期→新UI→旧详情 | **FAIL** | 原文及八步接缝通过；提交中Escape执行宿主关闭，V-2 |
| 00-I-1 | 稳定证据组合，不按同名合并邮箱 | **FAIL** | 原337组合正确；实际run候选字段可冒充flat身份验证，D2 |
| 00-I-2 | 同原bytes/metadata/first/config生产PDFBox重放 | PASS | C12逐文件校验；244/337；固定输入哈希不变 |
| 00-I-3 | 离线、零业务服务/网络 | PASS | C1 traps；C12 network0/writeServices0；无Spring写服务图 |
| 01-I-1 | 明确唯一联系证据，歧义/共享/同缩写拒绝 | PASS | C3 resolver30/layout8/analysis10；C12固定真实样本与负例 |
| 01-I-2 | 同页/栏/联系块边界，未读页单列 | PASS | C3字节级版面正负例；C12未扫描页1条显列；不扩大扫描配置 |
| 01-I-3 | 独立parser版本、旧身份/抽取兼容、历史事实不直接当前化 | PASS | parser20261101；identityExtraction20261004保持；C3/C12/C16 |
| 02-I-1 | propose只读，ENFORCE apply，AUDIT虚拟身份 | PASS | RetainedIdentity:40/119；C16 PROPOSED零identity写，ENFORCE APPLIED |
| 02-I-2 | 唯一邮箱、保护/CAS、PARTIAL不晋升 | PASS | C3单元/SQL及多层恢复、保护异常；C10真实叶子路径 |
| 02-I-3 | 当前lease/epoch有效身份，原baseline/hash保留 | PASS | VerificationRepository:1138/1170；C3 SQL原子重绑、epoch0/stale token拒绝 |
| 02-I-4 | receipt受控提交，不泛化放宽guard | PASS | C3 persisted-admission/rollback/manual/history/revision/cancel反例；C16 trace事务回滚 |
| 02-I-5 | phase/重启/旧缓存及快照不膨胀 | PASS | C3/C16相同proposal恢复、旧缓存hash拒绝、beforeSnapshot不嵌套 |
| 03-I-1 | 终态source、全量固定、上界/水位不扩 | PASS | ReprocessRepository:37/198；C3/C9跨121/7页，额外q/page不缩scope |
| 03-I-2 | requestKey/active singleton及生命周期 | PASS | C3真实SQL唯一约束、并发同异key；C9 duplicateRunId9002 |
| 03-I-3 | PAPER/TARGET分离、不可变输入、schema/trace/大小 | PASS | C3真实MySQL枚举/4MiB/unknown保留拒绝；C9 durable错误及6步 |
| 03-I-4 | 按record/邮箱计数，AUDIT actuals0 | PASS | C3同hash多记录/重叠分类/未知；C9/C14 actuals0 |
| 03-I-5 | lease/checkpoint/资源/取消及显式恢复 | PASS | ReprocessService:115/139；C3/C15 page cancellation、budget、executor rejection/shutdown、格式/缺源retry |
| 04-I-1 | 原consume/受限修复唯一写入口 | PASS | ApplyService:141；C10/C11真实consumer/person/enrichment/Commit/project |
| 04-I-2 | durable receipts及崩溃/归属/重复计数 | PASS | C10 RAW/prepare/start/admission观察故障、person FAILED/STALE retry；C11成功收据不重放 |
| 04-I-3 | 父许可、冻结配置、稳定分组、prepare后start | PASS | C10 100/1分组、WAITING槽、迟到派发拒绝、frozen true/false |
| 04-I-4 | 真终态、只取消owned、projection-only retry | PASS | ApplyService:253/301；C1/C11取消retry已评估；C14 ordinary/child同步不变准入/provider |
| 04-I-5 | 服务端byte permit、身份/资格分开、旧CLI不变 | PASS | C3/C10伪造author/source/hash拒绝；完整identity+PARTIAL资格不直接准入；legacy仍严格 |
| 05-I-1 | API唯一状态/计数、scope、requestKey及旧响应 | PASS | C2/C7/C9/C14；服务端summary；V-7保留为非阻断后续入口陈旧 |
| 05-I-2 | 默认AUDIT、明确ENFORCE、关闭不取消 | **FAIL** | 模式/请求语义及cancelPOST0通过；pending Escape关闭宿主，V-2 |
| 05-I-3 | first/current、绑定/档案/准入及原因分明 | PASS | C2/C14真实summary与六/八步；targetTotalKnown已修；新旧计数分开 |
| 05-I-4 | 安全DOM、分页/陈旧响应、权限/错误/焦点 | **FAIL** | textContent、分页/401/500通过；BODY焦点事件越过dialog边界，V-2 |
| S-1 | 精确整批入口/状态/范围 | PASS | D3 DOM/CSS；C14空态、真实121篇及saved设置 |
| S-2 | 确认弹窗布局、头脚/按钮可达 | PASS | C14四viewport，按钮均在视口内；保留正常disabled状态 |
| S-3 | 六列/手机三列、独立分页/容器横滚 | PASS | C2/C3/C14；1440/1152六列、390/312三列 |
| S-4 | 六步状态真实并链接原八步 | PASS | C1/C11 trace；C14 ordinary与child各8步、GET200 |
| S-5 | CSS逐字与旧全局样式不变 | PASS | D3：5515 bytes相等，base CSS前缀不变 |
| S-6 | 精确DOM、安全节点、只读接缝、Escape仅dialog | **FAIL** | D3 DOM通过；C14 pending自然BODY Escape hostCloses1 |
| 06-I-1 | 固定输入/时间/策略/provider，同身份比较 | **FAIL** | 固定parser/runtime证据通过；实际不同邮箱仍可冒充同一证据，V-3 |
| 06-I-2 | 逐目标身份/doc核对，真实增量，NOT_RUN诚实 | **FAIL** | C11固定目标正确；D2仍可将错误/缺失实际邮箱标MATCH |
| 06-I-3 | 真迁移/隔离，发布生产另行批准 | PASS | C5空库/升级；C9–C16隔离；无生产写或发布声明 |
| BUILD | JDK11、JS、真实MySQL、WAR、独立runtime分开 | PASS | C1–C17全列；无Kotlin被package替代；唯一skip披露 |
| SCOPE | 修订清单/文件数/顺序/shared schema边界 | PASS | D4及下表；57产品文件均授权；仅新增V173两张owned表、既有mapping未改 |
| NG | 不加OCR/猜测/资格放宽/下载并发/邮件游标改造/生产补跑 | PASS | 累计diff与实际调用链；生产操作NOT_RUN |

### 扩展及追认文件矩阵

每行按对应amendment的master rule核对；授权不是行为豁免。路径均位于上述retained worktree。

| Amendment | 精确扩展文件 | Verdict / evidence |
|---|---|---|
| A1/A2 | [DiscoveryReviewService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt:870) | PASS；11th文件，仅受控persisted-admission接缝；C3 |
| A3 | [V173 migration](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/db/migration/V173__create_discovery_paper_reprocess.sql:1) | PASS；新增两表，旧迁移不改；C3/C5 |
| A4/A5 | [PaperBodyAnalyzer.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperBodyAnalyzer.kt:27) | PASS；可选checkpoint透传；默认规则/异常保留；C3/C15 |
| A4/A5 | [PaperQualificationEvidenceExtractor.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt:25) | PASS；按页checkpoint及取消异常窄传播 |
| A4/A5 | [PdfEmailExtractor.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt:327) | PASS；beforePage透传，不借此扩大规则/读取范围 |
| A6/A7 | [TaskExecutionSummaryExtractorTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt:210) | PASS；去旧固定全集/数量，保留真实摘要/进度断言；C3 |
| A8/A9 | [DiscoveryPaperReprocessRepository.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt:146) | PASS；receipt/REVIEW/父许可、取消retry收据；C3/C10/C11 |
| A8/A9 | [ExpertDiscoveryService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1869) | PASS；原consume逐副作用父许可及确证归属恢复；无第二writer |
| A8/A9 | [DiscoveryVerificationWorker.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt:137) | PASS；paper-owned epoch0 dispatch/late-response围栏；C3/C10 |
| A10/A11 | [discovery-recheck.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-recheck.js) | PASS；原8步只读opener/lifecycle；C2/C14 |
| A10/A11 | [discoveryRecheck.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/discoveryRecheck.test.js) | PASS；公开入口/上下文回归；C2/C7 |
| A10/A11 | [DiscoveryPaperReprocess.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPaperReprocess.kt:52) | PASS；只读单位/summary/未知语义 |
| A10/A11 | [DiscoveryPaperReprocessRepository.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt:516) | PASS；权威SQL汇总/缺源查询；C3 |
| A10/A11 | [DiscoveryPaperReprocessService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt:42) | PASS；只读preview/detail映射 |
| A10/A11 | [RepositoryIntegrationTest](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepositoryIntegrationTest.kt:243) | PASS；121目标、分类重叠、未知、缺源真实SQL |
| A10/A11 | [ServiceTest](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessServiceTest.kt:28) | PASS；preview/summary只读契约 |
| A10/A11 | [discoveryReview.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/discoveryReview.test.js) | PASS；移除incidental资源总数，保留相关行为 |
| A10/A11 | [mailboxChatBehavior.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/mailboxChatBehavior.test.js) | PASS；保留组件唯一性/顺序及版本三件套 |
| A10/A11 | [mailboxGroupPush.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/mailboxGroupPush.test.js) | PASS；同上，未改业务断言 |
| A10/A11 | [meetingConfirmationAssets.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/meetingConfirmationAssets.test.js) | PASS；保留实际资源注册/位置/顺序 |
| A10/A11 | [trustReplyWorkbenchSharedMount.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/trustReplyWorkbenchSharedMount.test.js) | PASS；保留真实mount/unmount及顺序 |
| A12/A13 | [DiscoveryVerificationWorkerTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:43) | PASS；06仅七处fixture V161→V173；断言未减；C3 Worker24通过 |

### Finding Lineage

Epoch1/2报告不改写，稳定ID保留。

| Finding | Epoch3 state | Evidence |
|---|---|---|
| V-1 | **RESOLVED** | C1/C4/C11 ordinary与owned child取消后显式retry；同item/epoch、历史及成功副作用保持 |
| V-2 | **PERSISTENT，重新打开** | 正常焦点已修；pending自然BODY焦点仍调用host handler。相关JS/app在291→7e13完全未变，属于历史修复不完整，非最新提交引入的回归 |
| V-3 | **PERSISTENT，根因收窄** | 普通错误/缺失邮箱已拒绝；candidate.status仍可切换flat分支 |
| V-4 | **RESOLVED** | 真实DTO的summary.targetTotalKnown；C14完成121/121并显示“目标总量已固定” |
| V-5 | **RESOLVED** | C14 ordinary和child均8步200、原同步动作/公开HTTP ERROR→PROJECTED，revision1/0、providers/准入不变 |
| V-6 | **PERSISTENT，非阻断P2** | 5xx继续保留pending key/模式；未新推导任意失败必须允许换模式的要求 |
| V-7 | **PERSISTENT，非阻断P2** | 结束后preview.activeRun未即时刷新；原任务重入/完成正常，reload可重开 |

### Findings

#### P1

**V-3 — 实际run可被候选status伪装成flat replay，绕过目标身份校验。**

[Reconciler:132](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py:132)展开candidate全部字段。[evidence:65](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py:65)只要发现status便走flat分支，在实际邮箱与docId检查前返回BOUND。

真实固定record31372、合法稳定candidate键、预期docId 0000-0002-0178-9985：

| 实际target.email | 无candidate.status | 添加status=ALREADY_RECOVERED |
|---|---|---|
| 错误邮箱 | INVALID / REGRESSION | **BOUND / EXPECTED_MATCH** |
| 缺失 | INVALID / REGRESSION | **BOUND / EXPECTED_MATCH** |
| null | INVALID / REGRESSION | **BOUND / EXPECTED_MATCH** |

CLI输入已经明确选用--reprocess-run，候选字段不能改变其协议。该反例违反实际目标必须与不可变身份交叉核对的既定要求；不是要求新增格式或猜测规则。

影响：错误实际target仍可能被报告为正确绑定，损坏06逐目标验收依据。未据此宣称正常337-row replay已经造假。

最小范围：既有reconciler和ReplayToolTest。证据：[实际argv/结果](/tmp/paper-aggregate-epoch3-zztjjl1a/identity-status-probe-results.json)、[wrong输入](/tmp/paper-aggregate-epoch3-zztjjl1a/identity-status-wrong.json)、[wrong输出](/tmp/paper-aggregate-epoch3-zztjjl1a/identity-status-wrong.csv)。

**V-2 — 提交禁用按钮后，BODY来源Escape仍关闭任务宿主。**

[ReprocessJS:188](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-paper-reprocess.js:188)禁用当前提交按钮；Chrome焦点自然变为BODY。[dialog listener:312](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-paper-reprocess.js:312)仅拦截经过dialog的冒泡事件，因此BODY事件到达[真实app host handler:20352](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/app.js:20352)。

新鲜实际浏览器/API/MySQL场景：隔离服务端仅延迟一次真实create请求3秒，未伪造设置或业务响应。点击确认后：

    before Escape:
    activeTag=BODY
    confirmOpen=true
    startDisabled=true
    hostCloses=0

    after native Escape:
    confirmOpen=false
    hostCloses=1
    cancelPosts=0

随后实际后台同run9002完成121篇，说明问题是宿主/上下文关闭，不是任务取消。正常启用状态的confirm Escape通过；这个控制不能覆盖提交中状态。

违反S-6及05事件/焦点边界。相关JS/app在最近产品修复delta中未变；本轮证明旧修复尚未完整覆盖原要求，不把它归因于7e13新引入的代码。

最小范围：新组件JS及其现有测试文件；无需改app全局guard或CSS/DOM。

证据：[完整JSON](/tmp/paper-aggregate-epoch3-zztjjl1a/audit-evidence.json)、[浏览器log](/tmp/paper-aggregate-epoch3-zztjjl1a/c14-audit-browser.log)、[pending Escape截图](/tmp/paper-aggregate-epoch3-zztjjl1a/audit-pending-escape.png)、[合同检查](/tmp/paper-aggregate-epoch3-zztjjl1a/c14-contract-checks.json)。

#### P2

- **V-6**：确定性503后ENFORCE pending key/模式保留；继续非阻断，未纳入修复。
- **V-7**：完成后preview.activeRun陈旧，需reload才能再次开启；继续非阻断，未纳入修复。

#### Observations

- **O-E2-01**：历史C10取消时序Trace lease lost根因仍未证明。本轮C3/C10完整通过、未出现该错误；不能据此宣称通用并发问题已解决。
- 历史rapid-tick projection trace重复、编号References、旧294可选语料及文档措辞限制，按下表逐项保留，不混入新修复。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Epoch3判断 | Evidence |
|---|---|---|---|
| 00无新增RECORD_ONLY | I-1/I-2/X-0 | 固定输入重新验证；无永久豁免 | C8/C12/C13及冻结hash |
| 01 O-01旧294 corpus | I-1/X-0 | 非阻断可选证据缺口 | C3唯一明确skip；必需30711的244/337已运行 |
| 01 O-02编号References | 01-I-2/I-1 | 行为未变的既存观察 | 基线/当前路径对照；本次规则的明确reference负例C3通过，不扩大编号heading硬化 |
| 02跨SQL/ES边界 | I-4/I-6/X-3/X-4 | 证据边界保留 | 窄CAS、PARTIAL与持久收据通过；未声称SQL+ES原子事务 |
| 03 O-01 summaryRule=null | I-7/X-6 | 非阻断既有通用fallback限制 | catalog原声明、C3摘要19项、C9活动及owned summary权威统计 |
| 04 O-01 loopback ES/provider/protection fixture | I-3/I-4/X-3/X-4 | 证据边界保留 | 真MySQL/事务/服务；ES为严格loopback CAS，provider固定；非线上准确性验收 |
| 04 O-02 generic summary | I-7/X-6 | 同03 O-01 | 无新增通用summary shape要求 |
| 04 O-03 rapid-tick trace重复 | I-5/I-7/X-4 | 既存/未归因观察保留 | 本轮有限tick及projection通过不证明一般并发已修 |
| 05 F-05 | 05-I-1/I-2 | V-6持续、非阻断 | 现有5xx pending语义；未推导新强制换模式要求 |
| 05 O-07旧重复缩放截图 | I-9/S-1..S-6 | 新证据补齐 | 本轮四个不同CSS viewport独立截图/指标，未复用旧图片 |
| 05 O-08旧run/config标签 | I-8/I-9 | 历史说明保留，新设置证据补齐 | C14真实SQL-backed设置revision1/SAVED/search=true/detail=false与run冻结一致；AUDIT fixture effective=false如实披露 |
| 05 O-09 proposal正数 | I-7/05-I-3 | 机测证据补齐 | 实际API summary与完成主截图；AUDIT actuals0、候选proposal分列 |
| 05 O-10 summary-null措辞 | I-7 | 非阻断文档观察 | 未证明业务计数错误，未扩清理 |
| 05 O-11 401/JDK/八步限制 | I-7/I-9/X-9 | 强制证据补齐，局限保留 | C3/C9认证401、Java11.0.32.1；C14 NEW/REPAIRED八步均200；未声称全app登录重定向完整验收 |
| 06 O-01 gain标签 | I-1/I-7 | 历史措辞观察 | 本轮独立区分50组合/49邮箱/39记录，不称新建或准入 |
| 06 O-02 V171升级措辞 | I-2/06-I-3 | PASS，保留历史说明 | C5真实更早V170→V173及空库，48 tests |
| 06 O-03生产/live服务 | I-3/I-4/06-I-3 | 明确NOT_RUN、范围外 | 无生产30711 ENFORCE、live ES/ORCID/Apollo或付费详情 |
| 06 O-04 package口径 | BUILD | PASS，分开计证 | C6只JS/WAR；Kotlin/MySQL另有C1/C3/C4/C5/C15 |
| 06 O-05报告历史行数 | I-1 | 非阻断文档观察 | 当前独立CSV逐条337，不依赖历史行数 |
| 06 F-01报告身份/gate修正 | I-1/06-I-3 | RESOLVED，历史保持 | 当前ac6a计划/报告身份一致；未将旧NOT_RUN改成PASS |

### 浏览器证据与限制

C14使用真实组件、真实API/controller及隔离MySQL。AUDIT设置接口为真实DiscoveryApolloSettingsController及SQL repository，不再用旧设置响应替身；保存revision1、search=true、detail=false，run.scope冻结一致。AUDIT fixture服务/key/worker关闭，故effectiveSearch=false，这不冒充启用provider的生产设置；实际启用冻结业务由C10/C11覆盖。

运行中第二tab恢复同run9002；完成121/121。实际服务端500后保留最后面板并显示“更新失败，显示上次结果”，随后恢复完成。

| CSS viewport | 对应尺度 | Dialog宽 | 按钮bottom | 布局 |
|---|---|---:|---:|---|
| 1440×900 | 1440/100% | 700 | 747.109 | 六列，可达 |
| 1152×720 | 1440/125%等效 | 700 | 657.109 | 六列，可达 |
| 390×900 | 390/100% | 358 | 826.828 | 三列，可达 |
| 312×720 | 390/125%等效 | 280 | 671 | 三列，可达 |

这些是不同CSS viewport的缩放等效检查，未声称操作原生browser zoom，未使用固定100vw加CSS zoom替代。

跨路径实际结果：

- ordinary：run30716/item1/epoch0/revision1，8步GET200，原同步按钮使ERROR→PROJECTED。
- owned child：run30717/item4/epoch30717/revision0，8步GET200，原按钮及公开HTTP同步均成功。
- provider请求19→19；RAW写3→3；准入SQL行及revision不变。
- ordinary-run+child-item、child-run+ordinary-item、无归属ordinary均404，之后provider/准入不变。
- source reader实际打开，analysis POST0。
- browser cross fixture没有固定12目标用例的RAW写故障，因此其新增/准入为3；**不冒充固定2/2/2**。固定真值由C1/C4/C11独立验证。

主要证据：

- [AUDIT完成实际数字截图](/tmp/paper-aggregate-epoch3-zztjjl1a/audit-complete-1440-1.png)
- [390/125%等效确认截图](/tmp/paper-aggregate-epoch3-zztjjl1a/audit-confirm-390-125.png)
- [500恢复截图](/tmp/paper-aggregate-epoch3-zztjjl1a/audit-500-long-reason.png)
- [运行中重入](/tmp/paper-aggregate-epoch3-zztjjl1a/audit-reentry.png)
- [child八步/revision0](/tmp/paper-aggregate-epoch3-zztjjl1a/cross-eightstep-REPAIRED.png)
- [跨路径HTTP/SQL状态](/tmp/paper-aggregate-epoch3-zztjjl1a/cross-evidence.json)

补充audit-complete-*-100/125.png部分捕获了selectRun初始summary尚未取得的“—”过渡帧；未用它们证明最终数字。最终数字由主完成截图、真实API JSON及明确121/121断言证明。确认布局截图则等真实scope/settings加载后取得。

宿主Escape handler与真实app源码白空归一后完全一致。fixture只提供局部宿主，不声称完整部署app；认证拦截器另由C3/C9实际验证。全部截图/机测不构成人工验收。

### Evidence Boundaries

- 必需机器证据无缺失项；当前是两个已确认mandatory violation，故FAIL而非BLOCKED。
- 真实MySQL和生产服务链已测；ES是严格loopback CAS fixture，provider/email-validation等受控替身，未证明live服务准确性或部署环境兼容性。
- 旧294私有语料可选skip保留；30711必需原文完整运行。
- 生产30711 ENFORCE、发布、push/merge、线上业务写及live付费请求均NOT_RUN。
- 所有人类验收仍PENDING；没有制造reporter、时间戳、ACCEPT或签字。
- 历史首次失败及未知根因保持；本epoch通过不改写其历史。

### Manual Acceptance

| Source | Pending items |
|---|---|
| Master | A-1、A-2、A-3、A-4、A-5、A-6 |
| Child00 | A-1、A-2 |
| Child01 | A-1、A-2、A-3 |
| Child02 | A-1、A-2、A-3、A-4 |
| Child03 | A-1、A-2、A-3 |
| Child04 | A-1、A-2、A-3 |
| Child05 | A-1、A-2、A-3、A-4 |
| Child06 | A-1、A-2、A-3 |
| Human sign-off | 当前code boundary及governing master identity：PENDING |

### Convergence

**PROGRESSING**。

V-5的真实child同步404已经解决，合法revision0及ordinary例外都通过实际HTTP/按钮验证。V-3普通错/缺邮箱已被拒绝，剩余原因明确收窄为candidate字段选择错误证据协议。V-2的BODY路径证明历史修复不完整；相关JS/app在本次修复delta完全未变，不是最新7e13引入的同级代码回归。

因此存在实质行为修复及根因收窄；不是同一修复/证据重复，也没有证明最新修复引入新的同级回归。本判断不按发现数量或绿测次数计算。Epoch2的FAIL/DIVERGING保持不变。

## Repair Planning Result: DRAFT_READY

Baseline：[master](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-master.md)
Verification：Epoch3 **FAIL / PROGRESSING**
Artifact：[repair.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md)
Status：**DRAFT — HUMAN APPROVAL REQUIRED**

新稿SHA-256：

    50cc5a2fceedd09541a1efb080894347e7714262285fcea77f0acf52d7bfbfc9

### Included Findings

- **V-3**：由真实CLI处理来源决定run/flat协议，candidate.status不能绕过实际邮箱/doc/outcome/duplicate门禁。
- **V-2**：本组件Escape边界覆盖pending提交时的BODY事件；保留native关闭、可用焦点、宿主和唯一请求。

四个既有Authorized Files：

1. [reconciler](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py)
2. [ReplayToolTest](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt)
3. [reprocess JS](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-paper-reprocess.js)
4. [reprocess JS test](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/discoveryPaperReprocess.test.js)

两个根因、两个子系统；没有PLAN_AMENDMENT_REQUIRED或NEW_SCOPE_REQUIRED项。

### Excluded Findings

V-1/V-4/V-5维持RESOLVED，仅保留回归；V-6/V-7及全部观察不纳入修复。禁止顺带修改app全局guard、共享测试库、Recheck/Query/Repository、Worker/Trace、迁移、CSS/DOM、报告或基线。

### Planning Self-Review

- 旧47b6…及已执行ca1e…精确身份/Git位置、批准和产品提交lineage保留。
- 新产品基线明确为7e131474…，不撤销已有修复。
- 原Commands1–13逐条字符串完全保留；14保留并补强pending真实焦点；15–17补齐原child明确门禁。
- 每个任务包含来源要求、已证实根因、最小区分性测试、已有回归、禁止变化及机器完成标准。
- one-approval handoff使用真实绝对路径和具体产品/证据commit subjects。
- git diff --check通过；index空、HEAD仍30b2b46…。
- 本reviewer唯一tracked write是上述repair.md；另一个dirty文件是controller-owned review ledger。机器报告、产品、测试、fast证据及批准计划未改；未stage/commit。

No implementation was performed.

### Required Human Decision / Next Action

依据[repair-p](/Users/lukai/.agents/skills/repair-p/SKILL.md)：“Execution is prohibited until the human explicitly approves this plan.” 旧稿批准不适用于新SHA。

To approve and execute this repair, send:

    $execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md

No product code was modified.


## Epoch 4 — 2026-11-03T21:32:29+08:00

- Master plan: docs/plans/2026-11-01/paper-reprocess-master.md; sha256 85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e
- Governing master identity: recorded commit 53c2b0018e239e3c30db25942e99c2cddc69e9d4; AMENDMENT_RECORDED A1–A13
- Invoked identity: SAME; no unapproved amendment or retroactively unauthorized file
- Boundary: 3a425461dbe6088e4d4dda1a53141416a392670c..0b3c707147e8afa8ce4fe4002cb83752f28da0c8
- Reviewer: /root/paper_reprocess_aggregate_epoch4; fork_turns=none
- Result: PASS
- Convergence: PROGRESSING
- Repair artifact/result: executed 50cc5a2fceedd09541a1efb080894347e7714262285fcea77f0acf52d7bfbfc9; new repair planning N/A

## Verification Result: PASS

Plan：[paper-reprocess-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-master.md)
Phase：`aggregate/master`
Reviewer：`/root/paper_reprocess_aggregate_epoch4`
Implementation boundary：`3a425461dbe6088e4d4dda1a53141416a392670c..0b3c707147e8afa8ce4fe4002cb83752f28da0c8`
Convergence：**PROGRESSING**
Manual acceptance：**PENDING**

V-2、V-3已解决；V-1、V-4、V-5保持解决。无新增已确认强制违约。V-6/V-7仍为非阻断P2。

### 身份、权限与边界

- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`
- Branch：`fast/paper-reprocess-master`
- Resolution：`EXPLICIT`；注册worktree、分支、祖先关系均核对通过。
- Governing master：SHA-256 `85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e`；字节等于记录commit `53c2b0018e239e3c30db25942e99c2cddc69e9d4`。
- Invoked identity：`SAME`。历史治理状态：`AMENDMENT_RECORDED`，A1–A13继续有效；没有新master修订。已读选定review ledger中的完整历史governing diff。
- Fast ledger SHA：`dc2d86b0f225fbcec9f86a2bfa5f089cc3075fe541881dd5a8ceb88689274e09`。
- Fast handoff SHA：`ee33dc68d01e4682dfec0fc0edd3607b42587832811c08d453414371cc2ab155`。
- 七个child均为终态；28份brief/execution/fix-log/verify-log已读取。Fast ledger内39个完整commit身份均存在且为HEAD祖先。
- 本reviewer在修复commit后独立获得，未继承开发/轻量核验对话，身份不同于已记录writer/verifier和repair executor。
- 最终证据HEAD仍为 `f76a623d5e36185faab86db4e55f42d57d37666a`；与产品head分开。
- 最终产品、测试和index干净；仅控制器的review `ledger.md`修改存在。`git diff --check`退出0。未stage、commit或修改HEAD。

完整核对：[boundary.json](/tmp/paper-aggregate-epoch4-xm8c523s/boundary.json)、[child-scope.json](/tmp/paper-aggregate-epoch4-xm8c523s/child-scope.json)、[lineage.json](/tmp/paper-aggregate-epoch4-xm8c523s/lineage.json)、[final-boundary.json](/tmp/paper-aggregate-epoch4-xm8c523s/final-boundary.json)。

| Child，顺序不变 | Governing SHA-256 | 实际/授权文件数 | 越界 |
|---|---|---:|---:|
| 00 replay | `f9cb78a3c967c67fbbeab0c272a0894cb4928cd6a650e05ae419853dee6a2132` | 6/6 | 0 |
| 01 parser | `508fd62d84283bd223aca0970c9e265944762f6c48f36ffaf127ba2b88260608` | 7/7 | 0 |
| 02 identity | `81395262dedd2de0ac8a4c01c71467e4f9faf4d0a06222d88c1690b93a564997` | 11/11 | 0 |
| 03 batch | `6385eb47ee2e37d4887293f77a6aedc2d15502fd36d9b169dda0f241a72caed4` | 14/14 | 0 |
| 04 apply | `9d2899342bc0d9cbb6336930864a7c23220224f310453e95b339b7cbcc45bb61` | 13/13 | 0 |
| 05 frontend | `b329427077b2011fd2b8d1b828698351eb86c362d91d5dd519e354f47657bd5b` | 19/19 | 0 |
| 06 verification | `ac6a7663526a7a5c3a74716a3be3b54db97b8afaa6b5576672d8b65f655a6bb2` | 5/5 | 0 |

七份计划均位于选定worktree的 `docs/plans/2026-11-01/`，按上述顺序审查。完整累计57个`src/`、`scripts/`文件均在修订后清单并集内；逐child提交边界也分别通过。

### Post-repair handoff

Evidence mode：**DURABLE_HANDOFF**。

- 已执行repair SHA：`50cc5a2fceedd09541a1efb080894347e7714262285fcea77f0acf52d7bfbfc9`，本轮保持不变。
- 批准来源：[repair-execution.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/repair-execution.md) Epoch 3 execution，记录人类明确 `$execute-p` 调用。
- Executor：`PaperRepairExecutor3`；结果：`READY_FOR_VERIFICATION`。
- Handoff SHA：`cec0e01cdb3c0ef0160a0e6f702eaa46216c4a4ba0ba01998000e1e1fc1862ec`。
- 修复产品边界：`7e131474f403d9b28178148909180ac8f1d7d1bb..0b3c707147e8afa8ce4fe4002cb83752f28da0c8`。
- 累计修复产品/测试delta严格为以下四文件，没有额外产品文件：

| Authorized File | 判定 |
|---|---|
| [reconcile_paper_identity_replay.py](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py) | PASS |
| [DiscoveryPaperIdentityReplayToolTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt) | PASS |
| [discovery-paper-reprocess.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-paper-reprocess.js) | PASS |
| [discoveryPaperReprocess.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/discoveryPaperReprocess.test.js) | PASS |

历史报告SHA仍为 `857bd0f7cd44116f420f18e9c5762f7f9ada3df974a9ad0ba31687854fb906fc`。Epochs 1–3、旧首次失败及Epoch 2 `FAIL/DIVERGING`保留；未重写旧结论。

### Governing Amendments

各行批准均为 **H**：

> HUMAN：继续 后续授权都继续 直到所有子计划都完成且可以运行review-fast-p的阶段 不要执行review-fast-p，我会交给其他agent执行 — 2026-11-01T23:28:37+08:00

精确before/after身份及原始记录见[fast ledger](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fast/paper-reprocess-master/ledger.md)。

| ID | Master rule | 原因/治理内容 | Approval |
|---|---|---|---|
| A1 | I-4/I-6/X-4；顺序与门禁 | 02扩至11文件，覆盖persisted-admission窄接缝 | H |
| A2 | I-4/I-6/X-4 | 授权DiscoveryReviewService受控身份变化；保留普通guard | H |
| A3 | I-2/I-5/X-1/X-5 | V172已占用，使用首次空闲V173；不改旧迁移 | H |
| A4 | I-4/I-5/X-1/X-5 | 03扩至13文件，补按页取消/预算checkpoint | H |
| A5 | I-4/I-5/X-1/X-5 | 三处parser checkpoint接缝；保留默认规则/版本 | H |
| A6 | I-5/I-7/X-6 | 03扩至14文件，覆盖受影响catalog测试 | H |
| A7 | I-5/I-7/X-6 | 退役旧固定全集/数量；保留实际摘要/进度断言 | H |
| A8 | I-4/I-5/I-6/X-3/X-4/X-7/X-8 | 04扩至13文件，补收据、consume及父许可 | H |
| A9 | 同A8 | receipt、owned consume、epoch0围栏；保留普通默认 | H |
| A10 | I-5/I-7/I-9/X-5/X-6/X-8/X-9 | 05扩至19文件，补只读opener/summary/preview及受影响测试 | H |
| A11 | 同A10 | 权威只读统计、缺源preview、原八步入口；CSS/DOM验收不减 | H |
| A12 | I-1/I-3/I-6 | 06第五文件例外：WorkerTest fixture升至V173 | H |
| A13 | I-1/I-3/I-6 | 仅提升迁移fixture目标；断言及完整验收不减 | H |

### Commands

全部新鲜运行。目录：`/tmp/paper-aggregate-epoch4-xm8c523s`。

所有Maven命令均设置：

```text
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home
```

JDK实际为11.0.32.1，Node为25.7.0。Maven/target访问串行；必需进程1200秒watchdog均未触发。完整literal命令、退出码和日志路径：[commands.json](/tmp/paper-aggregate-epoch4-xm8c523s/commands.json)；完整补充argv（包括展开的classpath）：[supplement-commands.json](/tmp/paper-aggregate-epoch4-xm8c523s/supplement-commands.json)。

下表Maven均为 `/opt/homebrew/bin/mvn`，没有省略测试名或opt-in flags。

| ID | 命令/入口 | Result、exit、耗时 | 新鲜证据 |
|---|---|---|---|
| C1 | `mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | PASS，0，272.02s | Replay10 + E2E5；15/0/0/0；绑定JS2038通过；`cmd1.log` |
| C2 | `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js` | PASS，0，0.68s | 47/47，fail0；`cmd2.log` |
| C3 | 下方完整十五类命令 | PASS，0，850.61s | 207 tests，failure0/error0/skip1；绑定JS2038通过；`cmd3.log` |
| C4 | `mvn test -Dtest=DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | PASS，0，260.92s | 5/0/0/0；`cmd4.log` |
| C5 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | PASS，0，798.91s | 48/0/0/0；真实空库→V173、V170→V173；`cmd5.log` |
| C6 | `mvn package -DskipTests` | PASS，0，196.18s | WAR 53,149,648 bytes；绑定JS2038；Kotlin未计为执行；`cmd6.log` |
| C7 | `node --test src/test/js/*.test.js` | PASS，0，4.49s | 2038 tests/354 suites，fail0/skip0；`cmd7.log` |
| C8 | `python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py` | PASS，0，0.08s | PERSON333/PAPER244/PAPER_BINDINGS337/prior_known7/other_same_name1；`cmd8.log` |
| C9 | Maven exec，`Paper03RuntimeSmoke` | PASS，0，68.85s | HTTP/MySQL 121记录、7页、固定scope、重复run9002；actual/provider/archiveMutation均0；`cmd9.log` |
| C10 | Maven exec，`DiscoveryVerificationIntegrationTest --paper-reprocess-smoke` | PASS，0，323.57s | `PAPER04_RUNTIME_OK`，真实消费/重审/worker/commit/project/旧CLI；paid0；`cmd10.log` |
| C11 | Maven exec，`DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke` | PASS，0，83.28s | `PAPER06_RUNTIME_OK`；固定12目标2/2/2、重复、取消/retry、同步；`cmd11.log` |
| C12 | 下方计时原字节replay命令 | PASS，0，99.21s | 244记录/337行；network0/writeServices0；RSS1,364,279,296；`cmd12.log` |
| C13 | 下方reconcile命令 | PASS，0，0.14s | 337行、extras0；51 MATCH/286 MISSING；`cmd13.log` |
| C14 | `python3 /tmp/paper-aggregate-epoch4-xm8c523s/supplement-runner.py` 的浏览器阶段 | PASS；audit browser/server 0；cross browser/server 0 | browser 40.92s/5.89s；server 81.40s/55.97s；详见下方 |
| C15 | `mvn test -Dtest=DiscoveryPaperReprocessRuntimeScenarios -DpaperReprocessIt=true -DmysqlIt=true -DtrimStackTrace=false -Dstyle.color=never` | PASS，0，256.38s | 6/0/0/0；绑定JS2038；`cmd15.log` |
| C16 | Maven exec，`DiscoveryVerificationIntegrationTest --retained-identity-smoke` | PASS，0，61.35s | AUDIT/ENFORCE原controller链；providers0、roster/archive不变；`cmd16.log` |
| C17 | `node --check src/main/resources/static/discovery-paper-reprocess.js` | PASS，0，0.18s | `cmd17.log` |

C3完整命令：

```text
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,SourceAuthorEmailResolverTest,DiscoveryPaperLayoutRegressionTest,DiscoveryPaperAnalysisServiceTest,RetainedPaperIdentityServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest,TaskExecutionSummaryExtractorTest,DiscoveryPaperReprocessApplyServiceTest,DiscoveryPaperBackfillServiceTest,DiscoveryRecheckServiceTest,DiscoveryVerificationIntegrationTest -DmysqlIt=true -DpaperReprocessIt=true
```

C3逐类tests：Replay10、Resolver30、Layout8、Analysis10、Retained7、Worker24、Commit7、Query22、Service6、Repository12、TaskSummary19、Apply2、Backfill4、Recheck10、Integration36；合计207。所有failure/error为0。唯一skip为明确可选的旧私有294原始语料；必需30711原文已执行。[逐类回执](/tmp/paper-aggregate-epoch4-xm8c523s/c3-counts.json)。

C9–C11、C16完整命令：

```text
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.Paper03RuntimeSmoke'

JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --paper-reprocess-smoke'

JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke'

JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --retained-identity-smoke'
```

C12–C13实际输出路径：

```text
/usr/bin/time -l /Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json "/tmp/paper-aggregate-epoch4-xm8c523s/replay.json"

python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json --old-replay /tmp/paper-reprocess-30711-baseline.json --new-replay "/tmp/paper-aggregate-epoch4-xm8c523s/replay.json" --audit-directory docs/audits/2026-11-01-discovery-30711-pdf --csv "/tmp/paper-aggregate-epoch4-xm8c523s/reconciled.csv" --report "/tmp/paper-aggregate-epoch4-xm8c523s/reconciled.md" --runtime-evidence /tmp/paper-reprocess-30711-runtime.json
```

补充独立诊断：

| ID | 命令/证据 | 结果 |
|---|---|---|
| D1/D2 | 十次真实reconciler CLI；[完整argv](/tmp/paper-aggregate-epoch4-xm8c523s/identity-command-map.json)、[逐行结果](/tmp/paper-aggregate-epoch4-xm8c523s/identity-probe-results.json) | 全部exit0；valid/规范化地址BOUND/MATCH；wrong/missing/null/blank/non-string及三项candidate.status混淆均INVALID/REGRESSION |
| D3 | `node /tmp/paper-aggregate-epoch4-xm8c523s/static-contract.js` | exit0；CSS5,515 bytes逐字一致；旧CSS前缀、四块DOM、五个ID、资源三件套/顺序均通过 |
| D4 | 完整范围、逐child范围、祖先/hash、`git diff --check` | 全部通过；无越界产品文件 |
| D5 | 新依赖classpath + JDK11 `PaperReprocessParserSmoke`，完整argv见supplement receipt | exit0，3.71s；原始31375、named3、真实PAPER/RECORD consumer；emailGate=REJECT，indexed/promoted/network/businessWrites0 |
| D6 | `python3 /tmp/paper-aggregate-epoch4-xm8c523s/focus-context/run.py` | browser/server均exit0；实际整页/宿主DOM下复核disabled触发器后的键盘上下文，详见下方 |

Fresh dependency解析退出0（2.37s）；125个classpath项全部存在。临时服务重新javac编译退出0（0.99s），使用当前target。未复用执行者结果。

Required Commands覆盖审计：

| 来源 | 本轮覆盖 |
|---|---|
| 00 | C1/C8/C12/C13 |
| 01 | C3/C12及独立D5 PAPER/RECORD smoke |
| 02 | C3/C16 |
| 03 | C3/C9/C15 |
| 04 | C3/C10 |
| 05 | C2/C3/C7/C14/C17 |
| 06/master总门禁 | C3/C4/C5/C6/C11/C12/C13及完整矩阵 |

所有必需命令均有本轮退出码；无必需`NOT_RUN`。故障注入503/投影失败记录保留在日志中，相关用例最终断言通过；没有失败后重复跑到绿。

### Fresh replay与业务结果

[replay-counts.json](/tmp/paper-aggregate-epoch4-xm8c523s/replay-counts.json)：

- 244原文记录、337稳定组合全部有行。
- 当前51 `ALREADY_RECOVERED`、284 `NOT_REPRODUCED`、2 `REPRODUCED`。
- 相比冻结baseline，新增正确绑定50组合，涉及49邮箱指纹、39记录；不称新建专家或准入人数。
- 剩余286组合：`NO_PRODUCTION_LAYOUT_CONTACT`280、姓名不在PDFBox原句4、邮箱不在原句1、生产未扫描页1。
- Reconcile：51 `EXPECTED_MATCH`、286 `EXPECTED_MISSING`；extras、REGRESSION、UNEXPECTED_MATCH、SOURCE_MISSING均0。
- 固定12目标业务fixture：首轮新增2/补齐2/准入2；冲突2/保护2/错误2。第二任务新增0/补齐0，原已准入2单列。取消保留已生效NEW，普通/child显式retry完成。
- C12：99.16 real /137.13 user/4.76 sys；RSS1,364,279,296 bytes。network/writeServices均0。

前后冻结输入SHA均不变：

| 输入 | SHA-256 |
|---|---|
| Manifest | `de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c` |
| Baseline | `e0fe5b1e008fda6188315f73972f72af79b3897b6ca59f1a4f04c8d452b3d91c` |
| Runtime inventory | `5d00ffbc736f653fe64b93b5e667c8baf776aa33b1320716d212291b45e037c3` |

### 浏览器/API/MySQL合同

[C14 AUDIT证据](/tmp/paper-aggregate-epoch4-xm8c523s/audit-evidence.json)、[跨路径证据](/tmp/paper-aggregate-epoch4-xm8c523s/cross-evidence.json)。

- 真实Chrome、真实controller/service和隔离MySQL；AUDIT运行9002，121/121完成。运行中第二tab恢复同run。
- 空态禁用；实际HTTP500保留最后结果并显示中文错误，随后恢复完成。
- 真实SQL-backed Apollo settings：revision1/SAVED/search=true/detail=false，与run冻结值一致。AUDIT fixture的service/key/worker关闭，effectiveSearch/details=false如实披露；启用业务冻结另由C10/C11覆盖。
- Native confirm/detail Escape均`hostCloses=0`，可用触发按钮/行焦点恢复。pending真实延迟create 3秒，前置BODY/startDisabled/confirmOpen均成立；Escape后dialog关闭、宿主保留、cancelPOST0，同run继续。无dialog及dispose后的Escape分别到达原host handler。
- 唯一create POST1，runId9002；没有重复业务请求。
- ordinary：run30716/item1/epoch0/revision1；child：run30717/item4/epoch30717/revision0。两者真实8步GET200；原同步按钮/公开HTTP使ERROR→PROJECTED。
- provider19→19→19、RAW写3→3；含identity_hash的准入SQL、run/item/epoch/revision保持。三项错归属均404。
- 原文reader打开，analysis POST0。Cross fixture未注入固定用例的RAW故障，故其业务计数不代替12目标2/2/2真值。

| CSS viewport | 对应尺度 | Dialog宽 | 按钮bottom | 布局/可达 |
|---|---|---:|---:|---|
| 1440×900 | 1440/100% | 700 | 747.109 | 六列，通过 |
| 1152×720 | 1440/125%等效 | 700 | 657.109 | 六列，通过 |
| 390×900 | 390/100% | 358 | 826.828 | 三列，通过 |
| 312×720 | 390/125%等效 | 280 | 671 | 三列，通过 |

不同CSS viewport的缩放等效检查；未声称操作原生browser zoom。21张本轮截图已生成。主完成截图的数字及明细已加载并视觉核对；部分四viewport完成图仍处于既有表格入场动画，不用其透明行证明最终明细。

- [主完成截图](/tmp/paper-aggregate-epoch4-xm8c523s/audit-complete-1440-1.png)
- [390/125%等效确认](/tmp/paper-aggregate-epoch4-xm8c523s/audit-confirm-390-125.png)
- [child同步后八步](/tmp/paper-aggregate-epoch4-xm8c523s/cross-child-projected.png)
- [500状态](/tmp/paper-aggregate-epoch4-xm8c523s/audit-500-long-reason.png)

D6明确保留焦点测量：[focus-evidence.json](/tmp/paper-aggregate-epoch4-xm8c523s/focus-context/focus-evidence.json)。pending Escape后即时activeElement仍为BODY；实际宿主关闭按钮可用，下一Tab进入该宿主按钮，source9001和后续run9002保留，create1/cancel0。原始诊断字段`contractFocus=false`测量的是“关闭瞬间activeElement是否在host内”；批准合同要求native关闭及可用键盘上下文，不规定disabled触发器关闭后的瞬间BODY禁止条件。该诊断没有证明合同违约，未扩成新要求。

### Contract Matrix

以下机器项均mandatory。源码简称均指选定worktree：

- [RP Repository](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt)
- [RP Service](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt)
- [Apply](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyService.kt)
- [Verification Repository](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt)
- [Retained Identity](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/RetainedPaperIdentityService.kt)
- [Commit](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt)
- [Recheck](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt)
- [RP JS](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-paper-reprocess.js)
- [Reconciler](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py)

| ID/source | Requirement | Verdict | 新鲜证据/运行路径 |
|---|---|---|---|
| Master I-1 | 原文真值决定效果；325不冒充人数 | PASS | C8/C12/C13/D1/D2；实际run身份门禁恢复 |
| I-2 | SHA/metadata/first/history不覆盖，结果可追溯 | PASS | C3/C9/C11/C12/C16；owned结果、beforeSnapshot、source refs/版本 |
| I-3 | 身份不直接准入，保留资格/邮箱/人物门禁 | PASS | C10/C11原消费→Worker→Commit；固定2/2/2；C16仍PENDING |
| I-4 | AUDIT/保护/确认/取消隔离 | PASS | C3/C9/C10/C11；AUDIT零业务/外呼；取消及迟到围栏 |
| I-5 | DB固定scope、幂等/lease/generation/恢复 | PASS | RP Repository:37/198/270/291/425；C3/C9/C15 |
| I-6 | 单一consume、窄CAS、原Commit/project；旧CLI | PASS | Apply:141、Retained Identity:119；C10旧CLI AUDIT/REUSE READY |
| I-7 | 单位分明，六/八步真实，失败不伪装成功 | PASS | C3/C11/C14；D1/D2无效身份INVALID/REGRESSION |
| I-8 | Apollo已保存/冻结配置与免费付费策略 | PASS | C10/C11冻结true/false、paid0；C14真实settings SQL |
| I-9 | CSS/DOM/可访问操作完整契约 | PASS | D3逐字；C14四viewport/Escape；D6键盘上下文 |
| M-1 | 原重审、单篇解析/下载/删除/保护保持 | PASS | C2/C7/C16；C14原reader及两类八步 |
| M-2 | 人工/联系/资格/Apollo保护保持 | PASS | C3/C10/C11；fail-closed、APPLICATION/人工/联系人/CAS |
| M-3 | 原文件/first/旧轮次/源计数保持 | PASS | C9/C11/C16快照/hash对照；独立新execution |
| M-4 | 邮件/收信/采集游标不变 | PASS | 累计diff及写路径无新增相关writer；隔离运行链 |
| M-5 | 取消不撤销已生效补录/准入 | PASS | C1/C4/C11取消保留NEW；C10保留准入 |
| X-0 | 原文→旧新parser→逐标识对照 | PASS | C12/C13；D1/D2实际地址/doc/outcome/重复校验 |
| X-1 | first→当前解析/差异，版本/缓存一致 | PASS | C3/C12/D5；共享parser、first保留、缓存失效 |
| X-2 | 新绑定→本轮有效身份→资格 | PASS | Verification Repository:1138/1170；C3/C16有效hash/profile重绑 |
| X-3 | ES窄修复→人物→保护/来源 | PASS | Retained Identity:119/134；C3/C10/C11 |
| X-4 | 身份guard→准入→候选 | PASS | Commit受控authority及persisted seam；C3/C10/C11/C14 |
| X-5 | 显式lease→删除/保护→原文UI | PASS | C3/C9/C15显式attempt/hash及竞争；C14只读reader |
| X-6 | 持久任务→活动/重入恢复 | PASS | C9 execution匹配；C14第二tab同9002；C15恢复 |
| X-7 | 新作者→原消费→RAW/person/verification | PASS | C10/C11跨文档唯一邮箱、原writer、第二轮新增0 |
| X-8 | 父任务→ordinary/child→准入汇总/详情 | PASS | C1/C10/C11/C14；自动prepare/start，两类8步/sync200 |
| X-9 | 原挂载/生命周期→新UI→旧详情 | PASS | C2/C7/C14/D6；读回、焦点、dispose/无关Escape |
| 00-I-1 | 稳定组合；不同邮箱不按同名合并 | PASS | C8/C12/C13/D1/D2；337稳定键 |
| 00-I-2 | 同bytes/metadata/first/config，生产PDFBox | PASS | C12逐文件hash/244记录；冻结输入不变 |
| 00-I-3 | 离线、零网络/业务图 | PASS | C1 traps；C12 network0/writeServices0 |
| 01-I-1 | 明确唯一联系；歧义/共享/同缩写拒绝 | PASS | C3 Resolver30/Layout8/Analysis10；原字节正负例 |
| 01-I-2 | 页/栏/联系块边界，未读页单列 | PASS | C3版面负例；C12未扫描1条显列；无扫描配置扩张 |
| 01-I-3 | 独立parser版本，旧身份/缓存/历史事实兼容 | PASS | C3/C12/D5；parser20261101、identity20260925/extraction20261004 |
| 02-I-1 | propose只读，ENFORCE apply，AUDIT虚拟身份 | PASS | Retained Identity:40/119；C16 PROPOSED写0/APPLIED写1 |
| 02-I-2 | 唯一邮箱、保护/CAS、PARTIAL不晋升 | PASS | C3/C10多层/保护异常、PARTIAL恢复 |
| 02-I-3 | 当前lease/epoch有效身份，原baseline/hash保留 | PASS | Verification Repository:1138/1170；C3 stale token/epoch0反例 |
| 02-I-4 | receipt受控提交，不放宽普通guard | PASS | C3 persisted/manual/history/revision/cancel/rollback；C16 |
| 02-I-5 | phase/重启/旧缓存，快照不膨胀 | PASS | C3/C16原proposal恢复、旧缓存拒绝、before不嵌套 |
| 03-I-1 | 终态source、全量固定、上界不扩 | PASS | RP Repository:37/198；C3/C9 121/7页及筛选不缩scope |
| 03-I-2 | requestKey/active单例/生命周期 | PASS | C3真SQL同异key并发；C9 duplicateRunId9002 |
| 03-I-3 | PAPER/TARGET、不可变输入、schema/trace/大小 | PASS | C3 MySQL枚举/4MiB/unknown保留拒绝；C9六步 |
| 03-I-4 | record/邮箱单位；AUDIT actuals0 | PASS | C3同hash多record/重叠分类/未知；C9/C14 |
| 03-I-5 | checkpoint/资源/取消/显式恢复 | PASS | RP Service:115/139；C3/C15取消/budget/拒绝投递/shutdown/缺源 |
| 04-I-1 | 原consume/受限修复唯一写入口 | PASS | Apply:141；C10/C11 consumer/person/enrichment/Commit/project |
| 04-I-2 | durable收据、崩溃归属/重复计数 | PASS | C10 RAW/prepare/start/admission故障；FAILED/STALE人物重试；C11 |
| 04-I-3 | 父许可、冻结设置、稳定分组、实际start | PASS | C10 100/1分组、WAITING、迟到拒绝及冻结true/false |
| 04-I-4 | 真终态、仅取消owned、仅投影重试 | PASS | Apply:253/301；C1/C11取消retry；C14 provider/准入不变 |
| 04-I-5 | byte permit、身份/资格隔离、旧CLI保持 | PASS | C3/C10伪造author/source/hash拒绝、PARTIAL资格及旧CLI |
| 05-I-1 | API唯一状态/计数，scope/key/旧响应 | PASS | C2/C7/C9/C14；服务端summary；V-7非阻断保留 |
| 05-I-2 | 默认AUDIT、明确ENFORCE、关闭不取消 | PASS | C2/C14；pending BODY Escape宿主保留、cancel0 |
| 05-I-3 | first/current、绑定/业务/准入/原因分明 | PASS | C2/C14 summary实际值、六/八步；targetTotalKnown正确 |
| 05-I-4 | 安全DOM、分页/旧响应、权限/错误/焦点 | PASS | C2/C3/C14/D6；textContent、401/500、键盘上下文 |
| S-1 | 精确入口/状态/整批范围 | PASS | D3；C14空态、121篇、已保存设置 |
| S-2 | 确认弹窗头脚/按钮可达 | PASS | 四viewport均在视口内；正常disabled状态保留 |
| S-3 | 六列/手机三列、独立分页/横滚 | PASS | C2/C3/C14；1440/1152六列、390/312三列 |
| S-4 | 六步真实，链接原八步 | PASS | C1/C11 trace；C14两类各8步、GET200 |
| S-5 | CSS逐字，旧全局样式不变 | PASS | D3 5,515 bytes相等、base前缀不变 |
| S-6 | DOM、安全节点、只读接缝、Escape语义 | PASS | D3/C14/D6；正常/详情焦点、pending宿主保留、reader只读 |
| 06-I-1 | 固定输入/时间/策略/provider，同身份比较 | PASS | C11固定fixture；C12；D1/D2来源混淆被拒绝 |
| 06-I-2 | 逐目标email/doc，真实增量，NOT_RUN诚实 | PASS | C11逐12目标；D1/D2；C13逐337行 |
| 06-I-3 | 真迁移/隔离，生产另批 | PASS | C5空库/升级；C9–C16隔离；生产未执行 |
| BUILD | JDK11、JS、MySQL、WAR、独立runtime分计 | PASS | C1–C17/D5全列；唯一可选skip披露 |
| SCOPE | 修订清单/文件数/顺序/shared-store边界 | PASS | D4；57产品文件、逐child越界0；仅新增V173两owned表 |
| NG | 无OCR/猜测/资格放宽/并发/邮件游标/生产补跑 | PASS | 完整累计文件及运行写路径审计；生产操作NOT_RUN |

### 扩展/追认文件矩阵

每行按对应amendment的master rule审查；同文件的不同扩展分别列出。

| Amendment | 精确文件 | Verdict / evidence |
|---|---|---|
| A1/A2 | [DiscoveryReviewService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt:870) | PASS；受控persisted-admission，普通guard保持；C3 |
| A3 | [V173 migration](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/db/migration/V173__create_discovery_paper_reprocess.sql:1) | PASS；仅新两表；C3/C5 |
| A4/A5 | [PaperBodyAnalyzer.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperBodyAnalyzer.kt:27) | PASS；可选checkpoint、默认兼容；C3/C15 |
| A4/A5 | [PaperQualificationEvidenceExtractor.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt:25) | PASS；逐页checkpoint、窄异常传播 |
| A4/A5 | [PdfEmailExtractor.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt:327) | PASS；beforePage透传，未借此扩规则/扫描 |
| A6/A7 | [TaskExecutionSummaryExtractorTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt:210) | PASS；退役固定全集/数量，保留行为；C3 |
| A8/A9 | [DiscoveryPaperReprocessRepository.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt:146) | PASS；收据/REVIEW/父许可、retry保留；C3/C10/C11 |
| A8/A9 | [ExpertDiscoveryService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1869) | PASS；原consume逐副作用许可及确证归属，无第二writer |
| A8/A9 | [DiscoveryVerificationWorker.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt:137) | PASS；paper-owned epoch0围栏；C3/C10 |
| A10/A11 | [discovery-recheck.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-recheck.js) | PASS；原8步只读opener/lifecycle；C2/C14 |
| A10/A11 | [discoveryRecheck.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/discoveryRecheck.test.js) | PASS；公开入口/上下文回归；C2/C7 |
| A10/A11 | [DiscoveryPaperReprocess.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPaperReprocess.kt:52) | PASS；只读单位/summary/未知语义 |
| A10/A11 | [DiscoveryPaperReprocessRepository.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt:516) | PASS；权威SQL汇总/缺源；C3 |
| A10/A11 | [DiscoveryPaperReprocessService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt:42) | PASS；只读preview/detail映射 |
| A10/A11 | [RepositoryIntegrationTest](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepositoryIntegrationTest.kt:243) | PASS；121目标/重叠/未知/缺源SQL |
| A10/A11 | [ServiceTest](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessServiceTest.kt:28) | PASS；preview/summary只读契约 |
| A10/A11 | [discoveryReview.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/discoveryReview.test.js) | PASS；仅资源incidental约束调整 |
| A10/A11 | [mailboxChatBehavior.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/mailboxChatBehavior.test.js) | PASS；组件唯一/顺序/三件套保留 |
| A10/A11 | [mailboxGroupPush.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/mailboxGroupPush.test.js) | PASS；同上，业务断言保留 |
| A10/A11 | [meetingConfirmationAssets.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/meetingConfirmationAssets.test.js) | PASS；真实注册/位置/顺序保留 |
| A10/A11 | [trustReplyWorkbenchSharedMount.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/trustReplyWorkbenchSharedMount.test.js) | PASS；实际mount/unmount及顺序保留 |
| A12/A13 | [DiscoveryVerificationWorkerTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:43) | PASS；child06 delta仅七处fixture V161→V173；C3 Worker24 |

### Finding Lineage

| Finding | State | 当前证据 |
|---|---|---|
| V-1 | RESOLVED | C1/C4/C11普通/child取消后显式retry，同item/epoch、收据及已提交业务保留 |
| V-2 | RESOLVED | RP JS:23/319 document capture；C14真实pending BODY Escape host0/cancel0；正常/详情焦点恢复，D6真实宿主键盘上下文 |
| V-3 | RESOLVED | Reconciler:74/93/212按CLI来源选协议；D1/D2错误/缺失/null地址及candidate.status混淆均拒绝；合法flat/run保持 |
| V-4 | RESOLVED | RP JS:72读取nested summary.targetTotalKnown；C14实际121/121、“目标总量已固定” |
| V-5 | RESOLVED | C14 ordinary/child各8步及ERROR→PROJECTED，revision1/0、provider/RAW/准入不变 |
| V-6 | PERSISTENT，非阻断P2 | 5xx保留pending key/模式；C2及现有代码，原排除决定保持 |
| V-7 | PERSISTENT，非阻断P2 | 完成后旧preview.activeRun需reload；恢复/完成正常，原排除决定保持 |

#### P1

N/A；无未解决强制违约。

#### P2

- V-6：确定性503后pending模式锁；非强制项，未扩大修复。
- V-7：完成后的入口preview陈旧；非强制项，reload可重开。

#### Observations

- O-E2-01：历史Trace lease lost与rapid-tick投影trace竞态的根因仍未证明。本轮C3/C10通过且未出现该错误，不据此宣称通用并发问题已修复。
- D6即时BODY测量及既有表格入场动画截图限制保留，按实际批准合同判断，未新增可选要求。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | 本轮判定 | 新鲜证据/限制 |
|---|---|---|---|
| 00无新增RECORD_ONLY | I-1/I-2/X-0 | PASS | C8/C12/C13及冻结hash |
| 01 O-01旧294 corpus | I-1/X-0 | 非阻断可选证据缺口 | C3唯一明确skip；必需244/337已执行 |
| 01 O-02编号References | 01-I-1/I-2 | 既存观察保留 | 当前明确reference负例C3通过；未扩大编号heading硬化 |
| 02无额外RECORD_ONLY；SQL/ES边界 | I-4/I-6/X-3/X-4 | 证据边界保留 | C3/C10/C16窄CAS/PARTIAL/收据；未声称跨SQL/ES原子事务 |
| 03 O-01 summaryRule=null | I-7/X-6 | 非阻断通用fallback限制 | C3 TaskSummary19、C9活动、owned SQL summary |
| 04 O-01 loopback/provider/protection fixture | I-3/I-4/X-3/X-4 | 证据边界保留 | 真MySQL/事务/服务；ES严格loopback CAS，provider固定 |
| 04 O-02 generic summary | I-7/X-6 | 同03 O-01 | 未增加通用summary shape要求 |
| 04 O-03 rapid-tick trace重复 | I-5/I-7/X-4 | 未归因观察保留 | 本轮有限tick/同步通过不证明一般并发修复 |
| 05 F-05 | 05-I-1/I-2 | V-6持续，非阻断 | 原5xx pending语义及排除决定保持 |
| 05 O-07旧缩放截图重复 | I-9/S-1..S-6 | 新证据补齐 | 四个不同CSS viewport、独立截图/指标；明确等效缩放 |
| 05 O-08旧run/config标签 | I-8/I-9 | 新证据补齐，历史说明保留 | C14真实SAVED revision1与scope一致；effective=false明确披露 |
| 05 O-09 proposal正数 | I-7/05-I-3 | PASS | C14实际summary与主完成截图：proposal1/1、actuals0 |
| 05 O-10 summary-null措辞 | I-7 | 非阻断文档观察 | 未证明业务计数错误 |
| 05 O-11 401/JDK/八步限制 | I-7/I-9/X-9 | 强制证据补齐；局限保留 | C3/C9认证、JDK11.0.32.1；C14 NEW/REPAIRED八步均200；全app登录重定向未宣称 |
| 06 O-01 gain标签 | I-1/I-7 | 历史措辞观察 | 当前明确50组合/49邮箱/39记录，业务增量另计 |
| 06 O-02 V171升级措辞 | I-2/06-I-3 | PASS，历史说明保留 | C5真实更早V170→V173及空库，48 tests |
| 06 O-03生产/live服务 | I-3/I-4/06-I-3 | NOT_RUN，范围外 | 无生产30711 ENFORCE或live付费调用 |
| 06 O-04 package口径 | BUILD | PASS，分计 | C6仅JS/WAR；Kotlin/MySQL有独立C1/C3/C4/C5/C15 |
| 06 O-05历史报告行数 | I-1 | 非阻断文档观察 | 当前独立CSV逐条337，不依赖历史行数 |
| 06 O-06逐条/汇总正向确认 | I-1/I-7 | PASS | C12/C13及replay-counts逐行复算 |
| 06 F-01报告身份/gate修正 | I-1/06-I-3 | RESOLVED，历史保留 | 当前ac6a计划身份一致，旧NOT_RUN未转PASS |

### Evidence Boundaries

- 必需机器证据无缺失项；没有`BLOCKED`项。
- 真实MySQL、生产服务实现和真实Chrome已运行；ES为loopback CAS fixture，provider/email-validation等受控替身。未证明live服务准确性或部署环境兼容性。
- C14为局部真实组件/API宿主及补充实际DOM诊断；未宣称完整部署app验收。
- 可选旧294私有语料skip保留；必需30711原文已完整运行。
- 生产30711 ENFORCE、live ES/ORCID/Apollo、付费详情、发布/push/merge/部署均NOT_RUN。
- 未修改产品、测试、计划、fast-p证据或review报告；未改当前repair。仅生成新/tmp诊断及正常target产物。

### Manual Acceptance

全部仍为人类待验；机测不填人类结果。

| 来源 | PENDING项 |
|---|---|
| Master | A-1..A-6 |
| Child00 | A-1、A-2 |
| Child01 | A-1..A-3 |
| Child02 | A-1..A-4 |
| Child03 | A-1..A-3 |
| Child04 | A-1..A-3 |
| Child05 | A-1..A-4 |
| Child06 | A-1..A-3 |
| Human sign-off | 当前产品边界及governing master identity |

### Convergence

**PROGRESSING**。V-3通过CLI输入来源分离消除候选字段绕过；V-2通过document capture消除真实BODY Escape宿主关闭。两项都有区分性新鲜反例及合法控制；此前取消、计数、ordinary/child同步修复保持。无同级新回归。历史Epoch 2 `DIVERGING`不变。

Repair planning：**N/A**。

### Next Action

控制器持久化本轮完整报告，进入`AWAITING_HUMAN_ACCEPTANCE`；完成上述人类验收，并明确签字接受产品head `0b3c707147e8afa8ce4fe4002cb83752f28da0c8`及governing master SHA `85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e`。

**No product code was modified.**
