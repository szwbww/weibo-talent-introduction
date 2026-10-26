# Aggregate Machine Verification — discovery-recheck-master

## Epoch 1 — 2026-10-25T23:08:50.000+08:00

- Master plan: docs/plans/2026-10-24/discovery-recheck-master.md; commit da8940153f4ad22a5ac3a094f444a7ac4858f695; SHA256 5279a76a70aa84729ec849f881c97a31cd768e623b8da07c0c3e216a04a4cd99
- Governing master identity: worktree SHA256 above equals recorded commit bytes
- Master identity state: AMENDMENT_RECORDED; A17, under I-M3/I-M4/10 I-1/4, explicit bounded readonly recomputation; A7 human continuing authority, full A1–A18 lineage below
- Boundary: b161e87464d42b9fa2cd076ef9255b0e3b2b29ab..0d60b43e586584319b78c4f3f9ca86b4820dd24f
- Reviewer: /root/aggregate_reviewer, fresh fork_turns=none
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/discovery-recheck-master/repair.md; SHA256 1e62fa911bb0130b8c7ab31f76cc175608da02af84454d3edb2729abb14932ae; DRAFT_READY
- Manual acceptance: PENDING
- Evidence parent before report commit: ee65822ed51caf012e487d82cc04e9d74c0b125a

The following is the independent review-p aggregate/master return, persisted by the orchestration-only controller. No product changes were made.

## Verification Result: FAIL

- Phase：`aggregate/master`；Review epoch：1。
- Reviewer：`/root/aggregate_reviewer`，无实施/轻量核验上下文，未充当任何已记录实现者。
- Convergence：**INITIAL**。
- Findings：**V-1、V-2，均 P1 / NEW / REPAIRABLE**。
- Manual acceptance：**PENDING**；未执行或代签人工验收。
- Repair planning：**DRAFT_READY**。
- Aggregate outcome：**REPAIR_PLAN_READY**。
- 当前不存在尚未解决的必需环境或远端授权阻塞。

### 精确身份与边界

| 项目 | 已核实值 |
|---|---|
| Worktree | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master` |
| Branch | `fast/discovery-recheck-master` |
| Master base | `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab` |
| Final code head | `0d60b43e586584319b78c4f3f9ca86b4820dd24f` |
| Implementation boundary | `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab..0d60b43e586584319b78c4f3f9ca86b4820dd24f` |
| Evidence/current HEAD | `ee65822ed51caf012e487d82cc04e9d74c0b125a` |
| Governing master commit | `da8940153f4ad22a5ac3a094f444a7ac4858f695` |
| Governing master SHA256 | `5279a76a70aa84729ec849f881c97a31cd768e623b8da07c0c3e216a04a4cd99` |
| Invoked master SHA256 | `554c841cde657ee6bb8de6df467e77a1032b9a93d54b0adce283220433ea3792` |
| Master identity state | `AMENDMENT_RECORDED` |
| Resolution | `DISCOVERED_FROM_GIT_WORKTREES`，控制器给定唯一 SELECTED 结果；本轮在所选 worktree 独立重核身份、祖先关系、scope及clean state |
| Prior aggregate / post-repair | N/A |

[总计划](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/2026-10-24/discovery-recheck-master.md)与记录 commit 的字节完全一致。10份子计划逐一按 ledger 的 `commit:` 或 `sha256:` 重新核对，全部一致。40份 child evidence 均存在；仅作证据来源，未重新接受各 child verdict。

Fast ledger SHA256：

`610315d099ce9d10c7d90999436829def499bd4511b2bdc1ccc11d5ee7efc6c3`

Handoff SHA256：

`7897304ff920ce75ca7b5660caa437e42b2130ae93f7a88a2c792aa9e119aea5`

Git 证明 base→code→evidence 祖先关系成立。Code→evidence 仅五份已知 fast-p 文档。累计变更162文件，其中产品/测试/script **104文件**，修订后10份清单联合恰为104，未授权产品文件 **0**。各清单计数依次为：

`11 / 10 / 9 / 9 / 15 / 9 / 14 / 9 / 40 / 9`

最终 tracked diff、index diff 均为空；HEAD/branch未改变。新增仅本 reviewer 的唯一 repair 草案及控制器拥有的 review 目录。

### 修订治理

A1–A18 的路径、before/after身份、规则、原因、human approval均以已验证 fast ledger 原行治理；不是由本 reviewer 推定授权。

| 修订 | governing rule / 原因 | 已记录批准 |
|---|---|---|
| A1/A2 | I-M4；01允许11文件，修正 `discoveryApolloSettings.test.js` 过期资源键契约，保留Apollo及独立控制器回归 | HUMAN：授权 继续，2026-10-25 |
| A3/A4 | I-M4；03六→九文件，同步 Capture、PdfEmailExtractor、ExpertDiscovery 三个已有回归；保留真正歧义负例 | HUMAN：授权 继续，2026-10-25 |
| A5/A6 | I-M4；05九→十五文件，六个直接/共享消费者测试同步V161；不增加生产兼容分支 | HUMAN：授权 继续，2026-10-25 |
| A7 | I-M4；必要直接依赖/回归及清单例外的持续授权；仍须逐次记录/提交修订，保留独立核验及安全边界 | HUMAN：后续修改都授权 把所有子计划都跑完，2026-10-25 |
| A8/A9 | I-M4、07 I-5；07十→十四文件，为OpenAlex author/works/patent及重试逐次dispatch取消guard | A7持续授权 |
| A10/A11 | I-M4、09 I-1/3；09八→十二文件，为原reader窄桥接及真实pending/processing/protected只读统计 | A7持续授权 |
| A12/A13 | I-M4、09方案1；09十二→四十文件，仅28个已证直接资源回归；保留功能/key/order断言 | A7持续授权 |
| A14 | I-M4、09方案1；澄清已列 `mailboxGroupPush.test.js` 的第二份完整脚本列表同样须补新script；机械插入先于精确澄清，保留真实时序 | A7持续授权；不得倒填成“修改前已精确修订” |
| A15/A16 | I-M4、冻结 evaluationAt/paperSourceRefs；10七→八文件，Registration新增default-null原文上下文，无reserve后快照覆写 | A7持续授权 |
| A17/A18 | I-M3/I-M4、10 I-1/4；10八→九文件，Tool compare独用显式确定性factory，保留100页/2MiB/100事实；普通10秒clock不变，禁止缓存首报冒充重复解析 | A7持续授权 |

A17 精确身份：

`aa66b4b68e938c8bf827c4bd9914e18fd5e2af58`  
→ `da8940153f4ad22a5ac3a094f444a7ac4858f695`

其余精确身份均重新读自已验证 ledger 原行及相应 amended manifest；本报告未用简称推导文件名。

**逐文件追溯/追认矩阵：**

| 文件 | 修订及时间边界 | 机器判定 | 人工边界 |
|---|---|---|---|
| [mailboxGroupPush.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/test/js/mailboxGroupPush.test.js) | A13已列文件；A14精确脚本列表澄清晚于机械插入，文件数仍40 | PASS：diff仅过期17数量断言及 `discovery-recheck.js` 在app前的脚本列表同步；全量1840 JS通过，未发现该文件业务扩张 | 保留A14 chronology；最终sign-off应明确接受该文件及governing master身份，不能用机器PASS抹去时序 |

其他扩清单修订记录为实施前批准，无证据把它们另标为“未批准后追认”。全部新增文件名由相应 amended manifest提取，并纳入104/104核账。

### 新鲜命令与计数

所有本地命令均在唯一所选 worktree 执行。主suite/package/聚合MySQL使用：

`JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`

实际版本 **Zulu11.0.32.1**。旧 `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` 实际为 **11.0.15**；未混称同一JDK构建。

私有日志根目录：[discovery-aggregate-review-20261025](/private/tmp/discovery-aggregate-review-20261025)。

| ID | 精确命令/操作 | 结果 |
|---|---|---|
| C1 | `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test` | **Exit1**；11:38；**5622 tests / 1 failure / 0 errors / 24 skipped**。完整suite非绿；唯一失败为O-10-1。Surefire失败使本命令后续绑定JS未执行。 |
| C2 | `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn package -DskipTests` | **Exit0**；4:33；JVM按显式flag跳过；绑定JS **1840/1840、0失败、0skip**；两个绑定syntax阶段执行。 |
| C3 | 下列15类 `mvn test ... -DmysqlIt=true` | **Exit0**；16:38；**238 tests / 0 failure / 0 error / 0 skip**；实际Docker MySQL/JDBC。 |
| C4 | `/opt/homebrew/bin/node --test src/test/js/*.test.js` | 获精确target写升级后 **Exit0，1840/1840，0skip**。首轮sandbox因既有target验收页写EPERM失败，未当产品失败或PASS。 |
| C5 | `/opt/homebrew/bin/node --test src/test/js/discoveryRecheck.test.js src/test/js/discoveryReview.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js` | **Exit0，56/56，0skip**。 |
| C6 | `/opt/homebrew/bin/node --test src/test/js/discoveryPapers.test.js` | **Exit0，13/13，0skip**。 |
| C7 | 分别 `node --check`：discovery-recheck.js、app.js、discovery-papers.js | 三条均 **Exit0**。 |
| C8 | `bash -n scripts/discovery_recheck_replay/run.sh` | **Exit0**。 |
| C9 | 独立JDK11进程运行 `com.weibo.talentintroduction.discovery.service.DiscoveryRecheckFlowIT` | **Exit0**；真实Servlet HTTP **599次**，1001名单完整处理；取消/commit两种顺序、重启、保护、投影及原文consumer全链通过。 |
| C10 | 同一独立JDK11启动器运行 `DiscoveryRecheckFlowIT --historical <固定本地输入> <固定archive> <新私有输出>` | **Exit0**；9run，897/897完成，897兼容原缓存，准入0/provider0，业务、旧items/attempts不变。 |
| C11 | 私有 `RuntimeEvidenceProbe.kt` 编译及真实原文pipeline诊断 | 编译/执行均 **Exit0**；实际已存paper excerpts2，真实重审DTO excerpts0；V-1。 |
| C12 | 实际浏览器捕获两份prepare JSON，真实Controller/Service/GlobalExceptionHandler MVC诊断 | 编译/执行均 **Exit0**；两份请求均实际 **HTTP400**；V-2。该exit0表示成功复现缺陷。 |
| C13 | 实际Chromium，1280×720及640×900，完整生产HTML/JS/CSS | 四张新截图；布局及生命周期检查通过，真实DTO的原句缺失被复现。不是人工验收。 |
| C14 | 首次远端串行两条真实compare | SSH整体 **255**，连接被remote关闭；首轮新报告生成，第二Java已启动，依 `set -e` 可证明首轮Java退出0。第二轮退出码未采集，**不冒称有原始退出收据**。 |
| C15 | 远端耐断线补证compare，独立log/exit/started/finished | **原始exit0**；UTC **14:29:24→14:54:15**；真实重解析4487 attempts；REPORT hash与本次首轮新产物完全相同。 |
| C16 | 额外精确七类selector尝试 | 控制器要求不重复已通过superset后，在compile阶段停止，**Exit130，未进入测试**；不计通过，不再重复。 |

C3精确selector：

```text
env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperBackfillServiceTest,DiscoveryHistoricalReplayToolTest,DiscoveryRecheckFlowIT,DiscoveryCountryRegistrationTest,DiscoveryCountrySupplementFlowIT,DiscoveryVerificationIntegrationTest,PaperQualificationEvidenceExtractorTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationTraceRepositoryIT,DiscoveryPaperArchiveRepositoryIT,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryRecheckAcademicEvidenceServiceTest,OpenAlexDataSourceTest -DmysqlIt=true
```

C3逐类计数，均失败/错误/skip为0：

| 类 | Tests |
|---|---:|
| DiscoveryPaperBackfillServiceTest | 3 |
| DiscoveryHistoricalReplayToolTest | 6 |
| DiscoveryRecheckFlowIT | 2 |
| DiscoveryCountryRegistrationTest | 19 |
| DiscoveryCountrySupplementFlowIT | 8 |
| DiscoveryVerificationIntegrationTest | 14 |
| PaperQualificationEvidenceExtractorTest | 15 |
| DiscoveryVerificationRepositoryIT | 29 |
| DiscoveryVerificationTraceRepositoryIT | 4 |
| DiscoveryPaperArchiveRepositoryIT | 5 |
| DiscoveryVerificationAdmissionGateTest | 10 |
| DiscoveryVerificationCommitServiceTest | 6 |
| DiscoveryVerificationQueryServiceTest | 19 |
| DiscoveryRecheckAcademicEvidenceServiceTest | 8 |
| OpenAlexDataSourceTest | 90 |
| **合计** | **238** |

10合同七类子集为 **67/0/0/0**；未把15类命令冒称另一条selector已经完成。

C9/C10的classpath为所选 `target/test-classes:target/classes` 在前，再读取既有受保护：

`/private/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/discovery-child10-he59d110/runtime-classpath`

C10精确输入/输出：

- 输入：`.../discovery-child10-he59d110/pending-audit-input.json`
- 原文根：`.../discovery-child10-he59d110/pending-archive`
- 输出：`/private/tmp/discovery-aggregate-review-20261025/historical-audit`
- 输入SHA：`fff225116cfb7f2672ad1c593265e623658b79f4af30fa6c698e32b9f2e534cc`

本次WAR：52,011,947 bytes，SHA256：

`4464b1cf7a8ad32b7cd5ce1646e9fc6f00d6523c085e4ed82b1762e2f303db35`

#### 24个skip，逐项保留

| 原因 | 数量 | 类/用例 |
|---|---:|---|
| mysqlIt=false | 11 | ExpertMeetingRecordIntegrationTest；DiscoveryPersonOutreachGuardServiceTest；MeetingCalendarSendIntegrationTest；ReplyIdentityDefaultsIntegrationTest；MeetingCalendarServiceMysqlTest；MailboxConversationControllerTest；DiscoveryPersonRepositoryTest；MeetingCalendarControllerMysqlTest；MailReplyDraftSendIntegrationTest；CalendarAttachmentIntegrationTest；DiscoveryVerificationIntegrationTest |
| migrationIt=false | 6 | RagFactAdminServiceTest；AuthFlowIntegrationTest；RagKnowledgeBaseTest；FlywayMigrationIntegrationTest；OperatorActionLogRepositoryTest；InboundMailProcessingRepositoryTest |
| exact original294缺失 | 1 | DiscoveryPaperLayoutRegressionTest 的 private original corpus replay |
| 手动网络烟测关闭 | 1 | EuropePmcDataSourceTest real Europe PMC smoke |
| esIt不存在 | 1 | ExpertSearchCursorTest real ES 7.14 /11001 cursor |
| 已登记I-46偏离 | 1 | RagLetterComposerTest thinking/stream case |
| EnabledIf=false | 3 | ExpertMeetingVideoStreamingIntegrationTest：100MiB+1、1GiB+1、1GiB upload |

全量XML先按本次日志的 **354个 Running class** 过滤，精确得到5622/1/0/24；没有将target中131例旧XML混入。本次C3已把必需的 DiscoveryVerificationIntegrationTest 真正执行为14/14、0skip；其他skip仍是skip。

### 实际跨链证据

C9证明：

- 1001人，500/500/1三批固定；之后新增RAW不进入旧roster。
- 全部1001完成AUDIT；prepare/provider HTTP0、AUDIT业务写0。
- 部分固定失败保持FAILED/500/notClosed；重启PREPARING→INTERRUPTED。
- PREPARING/READY取消、跨run404、未登录及非法参数路径通过。
- 原人工HTTP仍产生MANUAL_APPROVED，重审保持REVIEW_PROTECTED。
- `commit-first`：COMPLETED、已提交准入保留，仅重试投影。
- `cancel-first`：CANCELLED、无准入；provider0。
- 实际parser→原consumer→Registration→Worker→Gate→Commit→projection通过。
- CLI freeze/compare/compare/report退出0；错误hash apply退出2；显式隔离apply两次退出0/0；新RAW **1→0**，合成fixture实际准入1/候选1。
- 旧task/item/checkpoint/archive/mail对照不变。以上应用只发生于可抛弃MySQL及严格fixture ES，未应用生产数据。

C10证明：

- 897完成、897兼容原留存缓存。
- 882人有固定原文ref，15人无ref，未补造来源。
- `PENDING→PENDING` 876；`PENDING→DIRECT_CLEAR` **17**；旧 `DIRECT_CLEAR→DIRECT_CLEAR` **4**。
- 因此21个wouldAdmit不能说成21个新增收益。
- Qualification：UNKNOWN844、MET21、CONFLICT32。
- 全部 admissionRevision=null；实际准入0、provider0、业务及旧history/attempts不变。
- 新报告SHA：`c5863b43791606018df29a75433d25552e00464df6474ce04cbc972ddb4fe4f1`，0600。未输出rows。

#### 远端真实重算与授权恢复

首次require_escalated被自动审批拒绝，理由是远端root连接及历史隐私读取/传输范围缺少具体授权。未绕过。

用户随后明确：

> 授权上述远端只读核验及临时证据写入

控制器转交完整范围后，升级获准执行同一核验。该授权缺口已解决。

本轮先验证 **当前本地8类hash=冻结manifest**；每次Tool.load又校验远端实际加载的8类hash，之后才比较。固定manifest：

`973f2c38092625501d94298489fbf19039d06d4da7f10637138c3c83e5b4b76e`

运行使用：

```text
/usr/lib/jvm/zulu11.90.205-ca-jdk11.0.32.1-linux_x64/bin/java -Xmx1800m
-cp /tmp/discovery-child10-readonly/fixed-classes-v2.jar:/opt/apache-tomcat-9.0.71/webapps/talent/WEB-INF/classes:/opt/apache-tomcat-9.0.71/webapps/talent/WEB-INF/lib/*
com.weibo.talentintroduction.discovery.service.DiscoveryHistoricalReplayTool compare
--manifest /tmp/discovery-aggregate-review-20261025/manifest.json
--manifest-hash 973f2c38092625501d94298489fbf19039d06d4da7f10637138c3c83e5b4b76e
```

本次新目录最初仅复制manifest，未复制旧报告。首轮创建了新报告；原SSH255丢失stdout。原脚本 `set -e` 串行执行、第二进程5261被实际观察到，因果证明第一Java返回0；第二进程退出码未恢复，不计原始receipt通过。新增耐断线wrapper先等5261退出，再启动独立补证轮，没有并发重复占资源。

补证轮具原始退出码0和REPORT。Tool在每次调用中先重新读SHA/parse/assemble，之后才比较已存在报告字节；不是返缓存报告。

两次可采信新鲜重算的报告均为：

`de0f3414bf791bed320a11bccd46223bdce868024933d94e1014ab33924096a9`

新鲜聚合：

| 项目 | 结果 |
|---|---:|
| Papers / attempts / readAttempts | 4472 /4487 /4487 |
| Complete attempts | 3907 |
| Historical / pending experts | 1608 /897 |
| Coverage | **PARTIAL** |
| BASELINE_BODIES_MISSING membership | 564 |
| PAGE_LIMIT | 17 |
| EMAIL_PARSE_ERROR / PARSE_ERROR | 1 /1 |
| SOFT_TIME_LIMIT | 0 |
| Baseline / reparsed emails | 6840 /6815 |
| Baseline / proof bindings | 1277 /1823 |
| Observed added/removed emails | 5 /30 |
| Observed added/removed bindings | 548 /2 |
| Existing-person evidence intersection | 39 |
| Bindings with existing email | 50 |
| Potential-new-RAW observed intersection | 498 |
| Conflicting emails | 0 |
| Qualification facts | 37 |
| Complete gain fields / wouldAdmit | **null** |
| New RAW / actual admission / sync | 0 /0 /0 |
| Business writes / provider HTTP | 0 /0 |

观察交集不是已认证全批收益。旧部署same-SHA负例证据只作为既有历史基线，不冒称本轮重新执行了部署负例probe。

远端目录0700；manifest/report/log/exit/start/finish/aggregate-proof七文件均0600。只取聚合收据及hash到本地；没有下载原始manifest、完整报告rows、名单或原文。

[最终聚合收据](/private/tmp/discovery-aggregate-review-20261025/remote-receipt-final.json) SHA：

`a9164cd2d1174730d38369d3c29ac54d9f03a1d6708d44a38f81695cc436e8cf`

### Master Contract Matrix

这是一次累计合同矩阵；下列child编号仅定位合同来源，不构成逐child重新接受。

| ID / 来源 | 必需合同 | Verdict / 新鲜证据 |
|---|---|---|
| M-IDENTITY | exact master/amendment/child/handoff身份和可靠边界 | PASS；上述逐byte/hash、祖先关系、五份evidence-only delta |
| I-M1 | 唯一准入链；AUDIT业务/ES写0；ENFORCE原CAS；投影独立 | PASS；C3/C9/C10，真实parser/consumer/Worker/Gate/Commit；两种cancel/commit顺序 |
| I-M2 | 新epoch、旧业务不旁观改变、原计数/请求历史保留 | PASS；RepositoryIT29、C9多轮/重启、C10旧items/attempts及业务快照 |
| I-M3 | 固定输入/版本/SHA、收益分层、刷新另列 | PASS；C10及两次新鲜全源报告；完整收益null；17新增建议与4旧正例分开 |
| I-M4-scope | amended文件scope/共享store列数/顺序 | PASS；104/104；run仅recheck_json、item仅review_epoch；独立trace表；共享文件串行历史 |
| I-M4-verification | 必需完整JDK11 test/package、真实MySQL、不以skip为PASS | PASS（执行/如实记录）；C1原样exit1，C2exit0，C3真实238/0skip；不声称全suite green |
| M-PROTECT | 人工/联系/SBIR/APPLICATION保护 | PASS；Commit检查及C3/C9保护/人工HTTP回归 |
| M-RULES | SELF、身份/机构/资格/hard blockers保留；研究方向/论文任职不新增硬阻断 | PASS；完整Rules23、Gate10、Worker17；C3/C9；PAPER employment仍HISTORICAL |
| M-APOLLO | 保存设置；免费search不detail；不恢复系统500上限 | PASS；Worker/预算源码；C3 OpenAlex90/Academic8、完整Worker17及JS；原配置回归保留 |
| M-OLD-FEATURES | 原文保护/取消保护/删除；取消不撤已准入；候选独立；邮件/联系人/外联不受影响 | PASS；archive/fullJS/C9/C10；新鲜实际mail/checkpoint快照不变 |
| 01 I-1 | 一次RAW/discovered scanAll、500批、initialized-only六decision→四组 | PASS；完整CountsService9/Controller5及源码；15501/32批oracle、20并发single-flight回归 |
| 01 I-2 | 成功才完整值；失败码/旧asOf/未知非0 | PASS；Counts源码、C5/C13；O-01终态进度不扩成要求 |
| 01 I-3 | 仅task审计、GET不启动、滚动finally、独立池 | PASS；ScanService完整性/finally、CountsService及Executor；上述新鲜回归 |
| 02 I-1 | first数组真实计数；姓名非proof；analysis缺失只表示未运行 | PASS；ArchiveService及完整archive回归、C6 |
| 02 I-2 | PDF字节优先；DOCTYPE兼容且XXE禁用；错误不伪NOT_FOUND | PASS；PaperContentFormat、安全XML设置；Extractor15及archive/fullsuite |
| 02 I-3 | SHA/first不改、版本缓存、预览不写、lease关闭 | PASS；Archive/Analysis路径；新鲜archive、paper桥接及reader13 |
| 03 I-1/2 | 明确唯一作者归属；坐标/页底不硬裁；相邻/共享负例保持 | PASS；布局/Resolver源码及新鲜Layout结构、Resolver27、Pdf/consumer回归；original294例明确skip |
| 03 I-3/4 | 不制造邮箱；实现版本独立；不破坏旧identity/queue | PASS；PlainText8、Resolver27、完整consumer/Worker及C3；identity兼容常量未改 |
| 04 I-1/2 | 本人原句、否定/学生/相邻隔离；PUBLICATION_TIME及完整生日 | PASS；Extractor15、PaperEvidence10、Rules23；C9真实解析事实 |
| 04 I-3/4 | 仅绑定ORCID锚定姓名兼容；rule3复合职称；旧0/1/2不重算 | PASS；Rules锚定路径及23例、Worker17、Gate10、C3；冻结时刻贯穿Gate |
| 05 I-1 | V158→V161保留旧id/owner/evidence/request_count，ordinary去重、新轮独立 | PASS；RepositoryIT29、TraceIT4、C9/C10 |
| 05 I-2 | 未提交AUDIT/queued/pending epoch不进入业务Gate | PASS；BUSINESS_ITEMS及Gate显式staged路径；Gate10、Repo29、C9 |
| 05 I-3 | spec固定/control CAS、READY hash及revision门禁 | PASS；Repository/Service锁及C3/C9真实SQL |
| 05 I-4/5 | 八步attempt终态不可改、取消语义、claim/renew/reserve/requeue隔离 | PASS；TraceIT4、Repo29、C9竞争；旧CLI仅epoch0 |
| 06 I-1 | compute纯计算/commitAnalysis显式分离 | PASS；PaperBodyAnalyzer/AnalysisService及完整bridge10/analysis测试；C9/C10业务快照 |
| 06 I-2/3 | 固定来源SHA+本人绑定；PAPER事实SELF/HISTORICAL；真实出处 | PASS（存储/规则层）；PaperEvidence源码152–155、10例及C11真实事实。**展示交界另见V-1** |
| 06 I-4/5 | REUSE零HTTP；REFRESH不写资料；可信学术ID，不猜名 | PASS；Academic8、OpenAlex90、Worker17、C9/C10 provider0 |
| 07 I-1 | 共用Worker/Rules/Commit，建议与应用分离 | PASS；C3/C9/C10实际链及Gate复算 |
| 07 I-2 | 固定八步、真实卡点/跳过/旧史；步骤证据可解释 | **FAIL V-1**；八槽/状态/旧recorded=false通过，但本轮已存原句未到重审DTO/UI |
| 07 I-3 | evaluationAt/hash/context固定；旧缺context语义保留 | PASS；Rules23、Gate10、Worker17、C3/C9/C10 |
| 07 I-4/5 | 冻结Apollo配置/续跑；逐HTTP取消；迟到诊断一次；同步不重查 | PASS；OpenAlex逐dispatch guard、TraceIT4及真实C9两种竞争/投影重试 |
| 08 I-1 | 服务端固定IDS/ALL_MATCHING、幂等、hash/revision | PASS（后端）；C9真实1001/三批及spec/hash/ownership；**前端wire另见V-2** |
| 08 I-2/3 | 默认AUDIT/REUSE、只读准备；部分失败不READY；重启/取消恢复 | PASS（后端）；C3/C9/C10；准备失败500未关闭输入 |
| 08 I-4 | 会话/400/404、分页、建议/实际/同步互斥定义 | PASS（状态/权限）；实际HTTP599、SQLcounts；**08→09默认请求互通FAIL V-2** |
| 08方案5 / 09 I-3 | 真实本轮证据、原句/来源/已有定位到逐步详情 | **FAIL V-1**；C11实际2条paper excerpt→API0；C13 quoteHidden=true |
| 09 I-1 | 真实ID、late Promise隔离、unmount清timer/listener、单poll | PASS；C4/C5生命周期回归、生产宿主/组件源码 |
| 09 I-2 | 默认范围能准备、READY才启用、变化使hash失效、Esc不cancel | **FAIL V-2**；默认值/禁用/失效/Esc机器检查通过，但两个默认实际报文被真实MVC400拒绝 |
| 09 I-3 | 八步/原因/尝试/版本、建议实际同步分列、旧史未知 | PASS（其余）；C9/C13及56例；原句/来源展示失败独列V-1，不用手写quote fixture替代 |
| 09 I-4/S-1/S-2 | 完整DOM/CSS逐字、允许class、无新增inline style、两视口真实浏览器 | PASS；完整root匹配、CSS suffix逐字匹配；C4/C5/C13；白底、#475569正文、235px宽屏步骤、窄屏双列、局部表滚动 |
| 10 I-1 | manifest固定来源/身份/fact/revision/配置/版本/hash；同源重复核账 | PASS；8类hash预检、C15原始exit0与首轮新产物相同；不认证PARTIAL完整收益 |
| 10 I-2 | 真实parser permit→原consumer/登记，不另建准入旁路；重复RAW1→0 | PASS；C3/C9实际CLI及生产consumer链；原checkpoint/mail不变 |
| 10 I-3 | 默认compare只读、明确hash apply、当前身份/来源重核、私有权限 | PASS；错hash2、隔离apply0/0；C10/C15业务/provider0；远端700/600 |
| 10 I-4 | 缺失/部分分类、取消可续、receipt幂等、旧attempt不清 | PASS；Backfill3、Tool6、C9/C10/C15；15无ref及PARTIAL原因明确 |
| A14-file | mailboxGroupPush真实chronology、限定脚本列表变更 | PASS（功能/scope）；保留上方逐文件人工追认项 |
| A-M1…A-M5 | master五项人工验收及明确sign-off | **PENDING**；本 reviewer未代做/代签。机器浏览器检查不填人工结果 |

### Findings

#### P1 — V-1：本轮证据原句未贯通重审详情

**需求**：master需求2、I-M3；07 I-2/方案1–2；08方案5；09 I-3/方案5–6。

[PaperVerificationEvidenceService](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/PaperVerificationEvidenceService.kt:152) 已保存本人事实的excerpt、sourceUrl、SHA、jsonPointer。

[Worker](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt:387) 步骤4只记录count/内部引用。[QueryService](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt:180) 的重审DTO不投影该item的实际evidence_json；sourceRefs来自冻结输入，只有record/attempt/SHA等基础字段。[UI](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/main/resources/static/discovery-recheck.js:257) 却只找 `step.output.quote`，实际Worker不产生该字段。

真实parser→consumer→Worker→Gate→Commit后：

```text
ACTUAL_WORKER_PAPER_FACTS=2
PERSISTED_EXCERPTS=2
API_EXCERPTS=0
API_LOCATOR_FIELDS=0
PIPELINE=REAL
PROVIDER_HTTP=0
```

随后真实Chromium消费该实际DTO：步骤4显示“完成”和evidenceCount，quote隐藏、原句空，只有无法展开的内部引用与基础来源。

**边界**：真实样本为XML，本来无页码；不把“页未记录”本身当错。已证缺陷是本人原句、来源URL、已有段落/年份定位未到API/UI。正页码合成probe也证明DTO不携带，但不冒充真实XML拥有PDF页码。

**影响**：用户无法从新八步详情核查本轮资格为何恢复/通过。

**最小修复scope**：Query DTO只读投影、现有UI消费及三个既有回归文件；不改Worker/规则/schema。

#### P1 — V-2：默认prepare报文与服务端合同不兼容

**需求**：master需求2及08–09联合发布门禁；08 I-1/2/4、09 I-1/2。

[宿主](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/main/resources/static/app.js:2462) 传入默认 `{q:"",issue:"",decision:""}`。[组件prepare](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/main/resources/static/discovery-recheck.js:75) 对IDS和ALL_MATCHING都原样发送filter。

[服务端严格校验](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt:114) 要求IDS无有效filter，ALL_MATCHING的issue/decision若存在则是合法非空值。

实际生产页捕获的两份原始POST，经真实Controller/Service/GlobalExceptionHandler：

```text
IDS:
filter={"q":"","issue":"","decision":""}
HTTP400 BAD_REQUEST
message="IDS does not accept a filter"

ALL_MATCHING:
filter={"q":"","issue":"","decision":""}
HTTP400 BAD_REQUEST
message="Failed requirement."
```

**影响**：默认所选、默认全部页均不能固定名单；页面核心入口不能到READY。JS stub对任意body返回READY，后端fixture省略filter，两个单独green门禁未发现交界失败。

**最小修复scope**：组件按既有合同序列化wire；保留服务端严格负例，不通过放宽校验掩盖问题。

#### P2

N/A。

### Finding Lineage

| Finding | State | 结论 |
|---|---|---|
| V-1 | NEW | 已由真实持久化→DTO→实际浏览器链确认；REPAIRABLE |
| V-2 | NEW | 已由实际浏览器wire→真实MVC确认；REPAIRABLE |

首次aggregate，没有上一轮aggregate findings需要继承或比较。子计划曾解决的轻量finding未改名冒充本轮进展。

### Fast-P RECORD_ONLY Re-evaluation

| Source | Master映射 | 本轮结论及证据 |
|---|---|---|
| O-01 | 01 I-2；状态/失败透明 | RECORD_ONLY保留。真实SQL失败后条件更新不保证保留terminal scanned；合同只明确运行中progress/失败码/旧成功asOf，不新增“失败终态保留5000”要求。未以fixture证明生产terminal进度。 |
| O-02 | I-M4命令执行 | 编译warning存在，C2/C3成功；无本scope已证功能缺陷，不修无关warning。 |
| O-03-01 | 03 I-4、I-M3真实性 | original294及手动EuropePMC仍明确skip，不计通过；未豁免必需MySQL/队列。全批新鲜核验已由本次C15独立完成，仍PARTIAL。 |
| O-03-02 | 03解析/执行证据 | 字体、fixture/provider及classpath warning未构成新产品违约；target类优先，C3/C9/真实全源核验通过相应门禁。 |
| O-04-01 | 04规则/证据、I-M1 | 原compiler/Jackson/MySQL/Flyway及fixture环境限制保留；Rules/Extractor/Gate新鲜覆盖及真实parser链已补足，不能泛称真实生产环境验证。 |
| O-05-01 | 05持久化/取消 | 弃用、故意无效配置/注入错误与成功receipt对应；Repo29/Trace4及真实竞争通过，无额外产品finding。 |
| O-06-01 | 06纯计算/证据桥接 | source-root/compiler warning不阻止命令；纯计算及业务写0由新鲜测试/实际链证明。 |
| O-08-E1-01 | 08真实运行/生命周期 | Tomcat9/Java11 shutdown反射warning仍为环境观察；C9实际HTTP退出0，599请求与清理完成。 |
| O-10-1 | master完整mvn test | **本轮复现，仍非绿**。sample test/store/properties与整个master base的blob相同；固定10月18 capturedAt遇当前系统clock及7天retention，先删过期包再计quota，得到SAVED。直接因果证明无关，不以“未授权文件”代替因果判断。排除本修复scope。 |
| O-10-2 | 必需JDK环境 | 本次不复现：contact timezone类19/19通过；相关test/catalog/resource/pom与总base相同。新JDK11.0.32.1 vs旧11.0.15解释环境差异，不称代码已修19错误。 |
| O-10-3 | I-M3、真实MySQL及不完整范围 | 24skip逐项保留；C3选定必需SQL238零skip。新全源仍PARTIAL；15待审无固定ref，未补造。 |
| O-10-4 | I-M3/证据隐私边界 | 原writer私人email-array会话输出事件保留；未删除、未宣称redaction，未引用原始事故artifact。不是已证产品根因，不放入功能repair。 |
| 本轮新增证据处理事件 | 同上 | 定位临时浏览器harness时，我误对私有目录宽rg，排除.json但漏.jsonl，输出了历史论文metadata作者数组/原文片段。未写Git/产品、未发现凭据输出；不保证原始片段全不含联系字段，不再重读/扩散，不声称擦除。后续均精确文件/聚合/hash输出。必须保留这一限制。 |

### Evidence Boundaries

- 生产发布、生产ENFORCE/补录、付费provider、邮件/联系人创建：**未执行**。
- 全批历史结果仍PARTIAL；没有“519/13必须通过”断言，没有将548观察绑定当新增专家/准入。
- 旧部署same-SHA负例作为已存在历史基线，未冒称本轮fresh probe。
- 远端原SSH255及第二轮退出码丢失保留；最终重复一致性由本次新首报+耐断线补证原始exit0建立。未假造原始第二轮receipt。
- 第一次ps格式失败、远端Python3.6不支持`text`的监控失败已修正私有monitor；最终监控exit0。均不修产品。
- 浏览器使用完整生产资源，额外私有bootstrap仅打开原宿主；HTTP为严格loopback fixture。详情DTO来自真实本地pipeline。配置截图的READY响应仅用于布局检查，**不证明后端接受**；后端接受性已由C12实际wire诊断为失败。
- 四张新截图：[详情宽屏](/private/tmp/discovery-aggregate-review-20261025/detail-wide.png)、[详情窄屏](/private/tmp/discovery-aggregate-review-20261025/detail-narrow.png)、[配置宽屏](/private/tmp/discovery-aggregate-review-20261025/config-wide.png)、[配置窄屏](/private/tmp/discovery-aggregate-review-20261025/config-narrow.png)。均合成对象；tab/server已关闭，viewport已reset。
- 人工A-M1数量、A-M2统一入口/隔离、A-M3取消/旧史/保护、A-M4收益抽查、A-M5真实页面/Apollo配置，全部PENDING。最终边界/主计划/A14文件sign-off未代签。

### 核心日志hash

| 私有文件 | SHA256 |
|---|---|
| full-test.log | `54bb64c7a71d65428051596c5f29cdf72d77ba593f7f4c84c53a399b0371210a` |
| package.log | `a232f5f7c5261c62e94ed01decf1559a6d995be75006bcfbffc512962c381d2c` |
| aggregate-mysql.log | `0e046876bff8e0d8f0edb41f214d71c7d818feeea5a48268abed9765be30ce1c` |
| standalone-all.log | `c0498e8c222718917ea2bf4d39f04c0033d95fc01b78ff4fb9e40e00e42be52c` |
| runtime-evidence-probe.log | `7eaeab2348de75585868da8994d2e5279735d205d94d05d92120e4541b39c0f8` |
| browser-prepare-probe.log | `df010af825c96089faadf829148d912e0af4594868b530e977422b6f773bc400` |
| historical-audit.log | `cee1c9b9201e6c22b7592c3a8bbe87cdc68af74cf445b7fe3007815c6239993e` |
| remote receipt-compare.log | `50c4ae41fbacea9be3b470f14dca225d743df02d66cdb34ea402657b50dde312` |
| remote-receipt-final.json | `a9164cd2d1174730d38369d3c29ac54d9f03a1d6708d44a38f81695cc436e8cf` |

## Repair Planning Result: DRAFT_READY

Baseline：上述governing master及完整boundary。  
Verification：**FAIL / INITIAL**。

唯一修复文件：

[repair.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fix/discovery-recheck-master/repair.md)

SHA256：

`1e62fa911bb0130b8c7ab31f76cc175608da02af84454d3edb2729abb14932ae`

Included：V-1、V-2，均REPAIRABLE，无plan amendment或新产品scope需求。两个根因共享UI/集成文件，保持一份plan。

Authorized Files，恰5个：

1. [DiscoveryVerificationQueryService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt)
2. [discovery-recheck.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/main/resources/static/discovery-recheck.js)
3. [DiscoveryVerificationQueryServiceTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt)
4. [discoveryRecheck.test.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/test/js/discoveryRecheck.test.js)
5. [DiscoveryRecheckFlowIT.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt)

R-1贯通已存证据→DTO→现有UI/reader；R-2只修producer wire，保留server严格校验。计划含区分回归、真实HTTP/SQL与两视口、must-not-change及完整命令级准则。

排除所有观察、无关sample failure、旧JDK问题、隐私会话处理、人工验收/生产应用。完整suite须重跑并原样保留唯一已证无关O-10-1 exit1，不能为全绿改授权外文件；新/不同失败阻塞。该准则对应master“完整执行并记录”，不伪称命令exit0。

计划已自检：5文件均存在；无占位路径；无产品实施；无review evidence写入、stage或commit。一次批准handoff已写入，明确一份产品commit、一份execution-handoff evidence commit及同任务自动返回aggregate review的具体路径/subject。

No implementation was performed.

### Next Action

To approve and execute this repair, send:

```text
$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fix/discovery-recheck-master/repair.md
After READY_FOR_VERIFICATION, resume $review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fast/discovery-recheck-master/human-review-handoff.md using the committed repair-execution.md. Do not ask me to relay executor metadata.
```

显式批准要求来自 [repair-p/SKILL.md](/Users/lukai/.agents/skills/repair-p/SKILL.md)：“Execution is prohibited until the human explicitly approves this plan.” 当前远端只读授权不等于批准执行新修复草案。

**No product code was modified.**

## Epoch 2 — 2026-10-26T10:15:51.780479+08:00

- Master plan: docs/plans/2026-10-24/discovery-recheck-master.md; commit da8940153f4ad22a5ac3a094f444a7ac4858f695; SHA256 5279a76a70aa84729ec849f881c97a31cd768e623b8da07c0c3e216a04a4cd99
- Governing master identity: worktree bytes equal recorded commit; AMENDMENT_RECORDED A1–A18, A17 under I-M3/I-M4/10 I-1/4 and A7 human continuing authority
- Invoked master identity: SAME; explicit retained-worktree handoff
- Boundary: b161e87464d42b9fa2cd076ef9255b0e3b2b29ab..50f8484ed2c30d51adf781c8b07a16920feab75e
- Reviewer: /root/aggregate_reviewer_epoch2; fresh fork_turns=none after candidate commit
- Result: PASS
- Convergence: PROGRESSING
- Repair artifact/result: existing docs/plans/fix/discovery-recheck-master/repair.md unchanged; repair planning N/A
- Repair evidence mode: DURABLE_HANDOFF
- Manual acceptance: PENDING
- Evidence parent before report commit: 52853e03d3081d8ab4bed49a53c25e0202c7516f
- Returned report SHA256: 80d6e26d1c8adc9d3e1114c09e4bc84db4777720549de36a63c901fde417690f

The complete independent review-p output follows verbatim. Epoch 1 remains immutable.

## Verification Result: PASS

- Phase：aggregate/master；Epoch：2；Reviewer：`/root/aggregate_reviewer_epoch2`。
- Convergence：**PROGRESSING**。V-1、V-2均RESOLVED；无新增必需违约。
- Manual acceptance：**PENDING**，A-M1–A-M5及最终sign-off未代签。
- **完整 Maven test 仍是 exit1，不是全绿**：唯一失败为已批准保留、且根因与总基线相同的O-10-1。PASS指本次治理合同及修复的机器审查，不抹去该失败、24个skip或历史PARTIAL。
- Repair planning：N/A。本轮未运行repair-p、未更新现有repair.md。

### 精确身份与实现边界

| 项目 | 独立核实值 |
|---|---|
| Repository common root | `/Users/lukai/IdeaProjects/weibo-talent-introduction` |
| Selected worktree | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master` |
| Branch | `fast/discovery-recheck-master` |
| Resolution | EXPLICIT；`git worktree list --porcelain`精确匹配给定root、branch及HEAD；未选择其他工作树 |
| Master base | `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab` |
| Final code head | `50f8484ed2c30d51adf781c8b07a16920feab75e` |
| 完整累计审查边界 | `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab..50f8484ed2c30d51adf781c8b07a16920feab75e` |
| Evidence/current HEAD | `52853e03d3081d8ab4bed49a53c25e0202c7516f` |
| Governing master | `docs/plans/2026-10-24/discovery-recheck-master.md`，commit `da8940153f4ad22a5ac3a094f444a7ac4858f695` |
| Master SHA256 | `5279a76a70aa84729ec849f881c97a31cd768e623b8da07c0c3e216a04a4cd99`；工作树字节=记录commit字节 |
| Invoked identity | SAME：本次精确保留工作树handoff；Epoch1原invoked-copy差异仍保留在选定ledger/旧报告 |
| Identity state | AMENDMENT_RECORDED；A1–A18；A17治理I-M3/I-M4/10 I-1/4，普通10秒不变；A7 HUMAN持续授权 |
| Fast ledger SHA256 | `610315d099ce9d10c7d90999436829def499bd4511b2bdc1ccc11d5ee7efc6c3` |
| Fast handoff SHA256 | `7897304ff920ce75ca7b5660caa437e42b2130ae93f7a88a2c792aa9e119aea5` |
| Repair artifact | `docs/plans/fix/discovery-recheck-master/repair.md`，SHA256 `1e62fa911bb0130b8c7ab31f76cc175608da02af84454d3edb2729abb14932ae`，本轮未改 |
| Post-repair mode | DURABLE_HANDOFF；`repair-execution.md`已在evidence HEAD提交 |
| Approval | 提交handoff记录human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fix/discovery-recheck-master/repair.md`，精确上述hash |
| Executor / result | UNAVAILABLE（无独立task identity）/ READY_FOR_VERIFICATION；非阻塞，未用自测叙述替代新鲜命令 |
| Pre/post repair code | `0d60b43e586584319b78c4f3f9ca86b4820dd24f` → `50f8484ed2c30d51adf781c8b07a16920feab75e` |
| Product repair parent | `b6959cd47f1ff9ff9288401b0325362c14b9b54e`；该parent→candidate仅下列5个授权产品/测试文件 |

10份子计划按canonical Children table的commit/sha256逐一重核，均一致；40份child artifacts全部存在。01→10每一base精确等于前一完成code head，祖先关系全部exit0，累计边界无merge commit；证据仅作来源，不承袭child结论。

累计168个变更路径中，产品/测试/script **104**，修订后清单联合恰104；未授权产品路径0、未用授权路径0。各子清单为11/10/9/9/15/9/14/9/40/9。candidate→evidence HEAD仅`repair-execution.md`。累计pre/post repair差异中的其他文件均为已知计划/证据，产品增量精确为：

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt`
2. `src/main/resources/static/discovery-recheck.js`
3. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt`
4. `src/test/js/discoveryRecheck.test.js`
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt`

A1–A18每条的**精确路径及before/after Git对象**均重新读取并解析，非从名称推导；完整核对表见私有`amendment-identities.json`，规范authority仍是上列hash绑定的fast ledger Amendments。A1/2=01资源键例外；A3/4=03确证归属回归；A5/6=05六个V161消费者；A7=持续授权；A8/9=逐HTTP取消；A10/11=冻结reader及只读计数；A12/13=28个资源回归；A14=同一已列文件的事后精确澄清；A15/16=冻结登记上下文；A17/18=Tool独用确定性工作限factory。所有修订按其原master rule判断，不将A7解释为无关范围或生产操作授权。

| 追认文件 | 修订/真实时序 | 机器结论 | 必需人工边界 |
|---|---|---|---|
| `src/test/js/mailboxGroupPush.test.js` | A13已列；A14精确脚本列表澄清晚于机械插入；09清单仍40。A14 `cb825484c1e8642ac992a3533b08c5ff78666501` → `47150fa045e7109ba60fad0a8ef19d7ae54d51a5` | PASS：累计diff仅过期17数量断言/新script在app前的列表同步；本轮全JS1843通过；修复未再次触及 | 保留该chronology；最终人类sign-off须明确接受此文件及governing master身份，机器PASS不代替追认 |

### 新鲜命令

全部仓库命令在selected worktree。固定JDK：`/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`，实际11.0.32.1；Maven `/opt/homebrew/bin/mvn`；Node `/opt/homebrew/bin/node`。旧11.0.15路径没有冒充本轮JDK。

| ID | 精确命令/操作 | 实际结果 |
|---|---|---|
| C1 | `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test` | **exit1**；11:55；5624 tests /1 failure /0 errors /24 skipped。唯一O-10-1；Surefire失败后绑定JS未到达，未称PASS |
| C2 | `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryRecheckControllerTest,DiscoveryRecheckServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryRecheckFlowIT -DmysqlIt=true` | **exit0**；12:17；33/0/0/0；真实Docker MySQL；绑定JS1843/1843、0skip；两个绑定syntax阶段通过 |
| C3 | 下列17类完整selector、`-DmysqlIt=true` | **exit0**；08:09；276/0/0/0；真实Docker MySQL；绑定JS1843/1843、0skip；两个绑定syntax阶段通过 |
| C4 | `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn package -DskipTests` | **exit0**；03:02 min；WAR构建/重打包完成；JVM按显式flag跳过，未冒称JVM运行；绑定JS1843/1843及syntax另行执行 |
| C5 | `/opt/homebrew/bin/node --test src/test/js/discoveryRecheck.test.js src/test/js/discoveryReview.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js` | exit0；59/59、0skip |
| C6 | `/opt/homebrew/bin/node --test src/test/js/discoveryPapers.test.js` | exit0；13/13、0skip |
| C7 | `/opt/homebrew/bin/node --test src/test/js/*.test.js` | exit0；1843/1843、0skip |
| C8 | 分别 `/opt/homebrew/bin/node --check src/main/resources/static/{discovery-recheck.js,discovery-papers.js,app.js}` | 三条逐命令exit0，stdout/stderr空；精确展开见syntax-summary.json |
| C9 | `bash -n scripts/discovery_recheck_replay/run.sh` | exit0 |
| C10 | 临时`BrowserRuntime.kt`编译；独立 `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Xmx1500m -Dapi.version=1.40 -cp /private/tmp/discovery-aggregate-review-epoch2/browser-classes:$CP BrowserRuntimeKt` | 编译exit0、后端最终exit0；当前真实parser/consumer/Worker/Gate/Commit、持久化→Query→HTTP桥接通过；`CP`精确为私有runtime-cp.txt文件内容，当前target优先 |
| C11 | CUA实际Chromium，1280×720及640×900，生产HTML/JS/CSS | 四个真实prepare POST均202，四次READY；原句可见、reader确切record1/attempt1、两次content200；8张实际截图；无整页横溢、白底、Esc/focus通过；非人工验收 |
| C12 | 同JDK独立`/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Xmx1500m -Dapi.version=1.40 -cp $CP com.weibo.talentintroduction.discovery.service.DiscoveryRecheckFlowIT --historical /private/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/discovery-child10-he59d110/pending-audit-input.json /private/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/discovery-child10-he59d110/pending-archive /private/tmp/discovery-aggregate-review-epoch2/historical-audit` | exit0；897/897完成、9run；准入0/provider0/业务及旧items/attempts不变；CP同C10 |
| C13 | `ssh -o BatchMode=yes -o ConnectTimeout=15 -o ServerAliveInterval=15 root@150.158.92.103 bash -s`；UTF-8 Python只读逐份SHA+旧manifest/report/exit收据复核 | exit0；4487读取/0missing/0mismatch，manifest/report仍同hash，权限700/600；**不是本轮全源重新解析** |
| C14 | Git身份/祖先/104清单联合/5文件repair差异、01/09全文CSS及静态root比较；`git diff --check` | exit0；CSS01 578字、09 7832字逐字存在；09静态root4606字逐字存在；动态节点按允许class及生产组件/浏览器核对 |
| C15 | 首次同C13的默认编码远端读取 | **exit1**；远端Python3.6默认ASCII解析manifest发生UnicodeDecodeError；未解析/应用业务数据；显式UTF-8重试C13成功，失败收据保留 |

C3精确命令：

```text
env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperBackfillServiceTest,DiscoveryHistoricalReplayToolTest,DiscoveryCountryRegistrationTest,DiscoveryCountrySupplementFlowIT,DiscoveryVerificationIntegrationTest,PaperQualificationEvidenceExtractorTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationTraceRepositoryIT,DiscoveryPaperArchiveRepositoryIT,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationCommitServiceTest,DiscoveryRecheckAcademicEvidenceServiceTest,OpenAlexDataSourceTest,DiscoveryVerificationContractTest,DiscoveryVerificationReportingIT,DiscoveryVerificationFlowIT,DiscoveryVerificationReplayToolTest -DmysqlIt=true
```

C2+C3共同新鲜覆盖相关child mandatory selectors：已经由C2通过的FlowIT/Query不在C3重复；未冒称另跑过一个七类或Epoch1十五类selector。C1还覆盖Counts9/Controller5、ArchiveService17/Capture11/Analysis9、Rules23、Worker17、PaperEvidence10、Resolver27、PlainText8等。所有计数按本条命令Running classes过滤其新鲜XML，未把target旧报告加总。

| 命令/类 | tests | failures/errors/skips |
|---|---:|---|
| C2 / DiscoveryRecheckControllerTest | 5 | 0/0/0 |
| C2 / DiscoveryVerificationQueryServiceTest | 21 | 0/0/0 |
| C2 / DiscoveryRecheckFlowIT | 2 | 0/0/0 |
| C2 / DiscoveryRecheckServiceTest | 5 | 0/0/0 |
| C3 / DiscoveryPaperBackfillServiceTest | 3 | 0/0/0 |
| C3 / DiscoveryVerificationCommitServiceTest | 6 | 0/0/0 |
| C3 / DiscoveryVerificationReportingIT | 12 | 0/0/0 |
| C3 / DiscoveryHistoricalReplayToolTest | 6 | 0/0/0 |
| C3 / OpenAlexDataSourceTest | 90 | 0/0/0 |
| C3 / DiscoveryVerificationRepositoryIT | 29 | 0/0/0 |
| C3 / DiscoveryVerificationFlowIT | 32 | 0/0/0 |
| C3 / DiscoveryVerificationReplayToolTest | 7 | 0/0/0 |
| C3 / DiscoveryCountryRegistrationTest | 19 | 0/0/0 |
| C3 / DiscoveryRecheckAcademicEvidenceServiceTest | 8 | 0/0/0 |
| C3 / DiscoveryCountrySupplementFlowIT | 8 | 0/0/0 |
| C3 / DiscoveryVerificationContractTest | 8 | 0/0/0 |
| C3 / DiscoveryVerificationTraceRepositoryIT | 4 | 0/0/0 |
| C3 / DiscoveryVerificationAdmissionGateTest | 10 | 0/0/0 |
| C3 / DiscoveryPaperArchiveRepositoryIT | 5 | 0/0/0 |
| C3 / DiscoveryVerificationIntegrationTest | 14 | 0/0/0 |
| C3 / PaperQualificationEvidenceExtractorTest | 15 | 0/0/0 |

#### C1的24个skip（全部保留，不算通过）

| 原因 | 数量 | 精确类/用例 |
|---|---:|---|
| mysqlIt=false | 11 | ExpertMeetingRecordIntegrationTest；DiscoveryPersonOutreachGuardServiceTest；MeetingCalendarSendIntegrationTest；ReplyIdentityDefaultsIntegrationTest；MeetingCalendarServiceMysqlTest；MailboxConversationControllerTest；DiscoveryPersonRepositoryTest；MeetingCalendarControllerMysqlTest；MailReplyDraftSendIntegrationTest；CalendarAttachmentIntegrationTest；DiscoveryVerificationIntegrationTest |
| migrationIt=false | 6 | RagFactAdminServiceTest；AuthFlowIntegrationTest；RagKnowledgeBaseTest；FlywayMigrationIntegrationTest；OperatorActionLogRepositoryTest；InboundMailProcessingRepositoryTest |
| original294确切语料不可用 | 1 | DiscoveryPaperLayoutRegressionTest / private original corpus replay explicitly skips when unavailable |
| 手动网络烟测关闭 | 1 | EuropePmcDataSourceTest / smoke test real Europe PMC API returns papers |
| esIt不存在 | 1 | ExpertSearchCursorTest / real ES 7_14 cursor traverses 11001 documents and early stop frees independent contexts |
| 已登记I-46偏离 | 1 | RagLetterComposerTest / registeredDeviationsThinkingAndStreamAreNotImplemented |
| EnabledIf=false | 3 | ExpertMeetingVideoStreamingIntegrationTest / hundred mebibytes plus one byte uploads over real http；one gibibyte plus one byte is rejected without leaving files or rows；one gibibyte uploads over real http without buffering and serves ranges |

C3中的DiscoveryVerificationIntegrationTest已实际14/14、0skip；其他默认skip未被虚构为已执行。完整354类/5624/1/0/24提取见full-summary.json。

#### 非绿基线的因果复核

C1唯一失败仍`DiscoverySampleStoreTest.store acceptance cases:135`，QUOTA期望、SAVED实际。测试固定capturedAt=2026-10-18，但quota段使用默认系统Clock；当前2026-10-26、retentionDays7。Store.retained():136–141先删除过期包，再计桶额度，故前两包被清理，第三包仍SAVED。与总base逐blob重核：test `6e83b72fd3c07cabbc4ad1da299f5e0635719045`，store `0ebbdbee67a3bb91f54c6fb916ec26583e08bffa`，properties `edc2c100fe1fbec1476dc4231e295637a0f42a5e`，均完全一致。不是仅以“未授权文件”推断无关；未改clock、store或测试。按已明确批准的repair命令级排除项保留，绝不称C1绿。

O-10-2本轮未复现：ExpertContactLocationServiceTest 19/19、0错误；本轮实际11.0.32.1，不声称产品修复旧11.0.15的America/Coyhaique时区错误。

### 实际跨链、历史与浏览器证据

C2真实Servlet **825次HTTP**：1001人按500/500/1固定并处理；固定后新增RAW不进入原roster；prepare/provider0、AUDIT业务/ES写0；同spec幂等、不同spec409、非法报文400、未登录401、跨run404；部分失败FAILED/500/notClosed；重启准备INTERRUPTED；PREPARING/READY取消；运行恢复保留hash/requestCount。原人工HTTP仍MANUAL_APPROVED、重审REVIEW_PROTECTED；commit-first保留已提交并只重试投影，cancel-first无准入。没有测试层手写proof替代生产parser。

同一实际原文pipeline：首次显式合成apply创建RAW1，重复0；真实准入1/候选1，均限可抛弃MySQL/严格fixture ES。旧task/item/checkpoint/archive/mail不变。CLI freeze/compare/compare/report各exit0；错误hash apply期望exit2；显式合成apply两次exit0/0；provider0。

V-1新鲜证据：current paperFacts2，savedFacts4（包含两条原留存观察，不冒称4条新收益）；真实持久化excerpt/sourceURL/SHA/jsonPointer/fetchedAt/temporalScope逐字段=Query=实际HTTP。Query读取前后item业务行相同、queryWrites0。XMLpage=null；正页码、错误SHA/attempt、identity/fact不符、100条截断及missing/malformed/empty由21例Query和59例前端定向覆盖。实际UI步骤4显示原句及HISTORICAL/SELF、不同原fetchedAt；点击旧reader精确1/1与SHA，未猜XML页码。

V-2新鲜证据：生产组件将IDS filter完全省略，ALL_MATCHING docIds=[]且默认filter={}；有效筛选语义保留。真实服务端现有严格校验未改，非法IDS夹filter、非法docId、空/未知decision仍400。实际两视口各IDS/ALL_MATCHING共4份原始POST，均202并READY；不会由api stub无条件READY掩盖交界。

浏览器使用完整生产index/app/recheck/papers/styles。重审API代理转发到真实本地认证Servlet/SQL；其他宿主读API为隔离fixture。原文reader的detail/content来自同一次真实ArchiveService导出，以严格本地fixture响应，**没有冒称生产ArchiveController在线验收**。宽1280×720：页面宽1280、步骤栏235；窄640×900：页面宽640、双列274/274、审核表client561/scroll1000/overflow auto；dialog背景rgb(255,255,255)。Esc关配置后焦点回“批量重新审核”，没有cancel请求。8张截图已逐张查看；browser-summary.json记录hash/尺寸；最终console error列表空。初始宿主账号fixture曾显示state.accounts.map错误，与审核无关，未称账号服务通过。临时tab关闭、viewport reset、proxy停止（SIGINT exit130为有意清理）、后端/MySQL正常exit0。

C12本轮重新执行固定897输入（SHA `fff225116cfb7f2672ad1c593265e623658b79f4af30fa6c698e32b9f2e534cc`）：897完成、897兼容原缓存；882有固定ref、15无ref；876 PENDING→PENDING、17 PENDING→DIRECT_CLEAR、4 DIRECT_CLEAR→DIRECT_CLEAR；UNKNOWN844/MET21/CONFLICT32；所有admissionRevision=null，actual0/provider0/业务及旧history/attempts不变。21建议不能记为21新增收益。新报告SHA `32a88871403966c35e28eb1de41adef35997533ca12a2e05ef4586e1946c1dbe`，0600。

#### 全源证据复用及新鲜度的独立判断

4472论文/4487尝试的**完整重新解析仍引用Epoch1实际重复计算证据，不称本轮fresh全源重算**。本轮另做了三重一致性验证：

1. 当前重编译的8个manifest绑定实现类逐一SHA=冻结manifest；全部解析/Rules/Tool及依赖源文件在repair前后无变化。修复只改Query、前端及其测试。
2. manifest SHA仍`973f2c38092625501d94298489fbf19039d06d4da7f10637138c3c83e5b4b76e`，本地既有report SHA仍`de0f3414bf791bed320a11bccd46223bdce868024933d94e1014ab33924096a9`，已知remote-receipt-final.json SHA仍`a9164cd2d1174730d38369d3c29ac54d9f03a1d6708d44a38f81695cc436e8cf`。
3. 用户既有远端只读授权下，本轮远端重新读4487份原文，4487 SHA全部匹配、missing0/mismatch0；原report及耐断线exit0收据仍在、目录700/report600。只取聚合hash/计数，未下载原文/名单/报告rows。

对应master I-M3/10 I-1/4要求同manifest同算法真实重复重算、收益可核账，并未规定每个仅展示修复epoch必须再次重算未变的4472全文。保留的证据本来是真实逐源读取/解析，Tool.writeComparison():157先compare再检查既有字节，非缓存首报返回；显式工作限factory与输入全部一致。本轮C1/C3重新验证确定性factory/工作上限/普通deadline，C2/C10又通过真实CLI两次compare，C12重新跑897人端到端AUDIT，故未变解析合同有充分机器证据；不因耗时豁免缺失证据。

历史全源数值保持：3907/4487 COMPLETE attempts，整体**PARTIAL**；BASELINE_BODIES_MISSING membership564、PAGE_LIMIT17、EMAIL_PARSE_ERROR1、PARSE_ERROR1、SOFT_TIME_LIMIT0；baseline/reparsed emails6840/6815、bindings1277/1823；observed added/removed emails5/30、bindings548/2；既有人证据交集39、既有邮箱绑定50、potential new RAW观察交集498、conflicts0、qualificationFacts37。完整gain/wouldAdmit字段仍null，newRAW/admission/sync/business/provider均0；这些交集不是认证完整收益。

原Epoch1 SSH255及原第二轮退出码未恢复继续保留；原首轮新产物+后续耐断线exit0同hash支持重复，未补造丢失退出收据。旧部署same-SHA负例仍只作历史基线，未称本轮重新probe。original294仍不可用；既有私人会话输出事件未擦除、未声称redaction，不引用/扩散其raw内容。

### Master Contract Matrix

下列均按master及A1–A18冻结合同；child编号仅定位来源。PENDING人工项不当作机器PASS。

| ID / 来源 | 必需合同 | Verdict及当前证据 |
|---|---|---|
| M-IDENTITY | 精确计划、修订、子身份及可靠base→candidate边界 | PASS；上述独立Git/字节/哈希核对；40制品存在 |
| I-M1 | 唯一Worker→Rules→Policy/Gate→Commit→projection；AUDIT业务0，ENFORCE原CAS | PASS；C1 Worker17/Rules23、C2真实原文consumer/825HTTP及C3 Gate10/Commit6；C12业务/provider0 |
| I-M2 | 新epoch、不重置旧item/owner/request_count、未提交不抢业务Gate | PASS；Repository.kt:608/739/758/1098，C3 Repo29/旧Flow32/Reporting12，C2多轮/恢复及C12旧history快照 |
| I-M3 | 固定输入/时刻/版本/SHA，建议/实际/同步分层，未完整明确 | PASS；C12新897、C13源SHA与未变解析实现、保留真实同manifest重算；17新建议与4旧正例分开，PARTIAL gain=null |
| I-M4 scope | amended104范围、共享store≤1新列、共享文件串行 | PASS；104/104、10段base连续；V161仅run.recheck_json/item.review_epoch，独立step表 |
| I-M4 verification | 完整JDK11 test/package、绑定JS、必需MySQL零skip与实际浏览器 | PASS（执行及如实记录）；C1保留exit1/O-10-1/24skip；C2/C3零skip，C4/C5–11；未称全套绿 |
| M-PROTECT | 人工/联系/SBIR/APPLICATION保护 | PASS；Commit.kt:51–104，C1/2/3原保护和原人工HTTP；未放宽Gate |
| M-RULES | SELF、身份/机构/资格/hard blockers、旧Apollo准入保持；研究方向/论文任职不新加硬阻断 | PASS；Rules.kt:85–97/390/451、C1 Rules23/Worker17、C3 Gate10；PAPER employment仍HISTORICAL |
| M-APOLLO | 保存配置、免费search不detail、预算/429/旧游标、无系统500上限恢复 | PASS；C1 Worker17及原Apollo设置JS，C3 Academic8/OpenAlex90，C2/12 REUSE provider0；OpenAlex逐dispatch guard保留RequestKind |
| M-OLD-FEATURES | 原文保留/保护/取消保护/删除；取消不撤准入；候选独立；邮件/联系人/外联 | PASS；C1 archive/JS、C2真实竞争/投影、C12业务/旧history不变；无邮件产品文件变更；未冒称默认关闭的邮件IT均已跑 |
| M-ROLLBACK | 向前兼容迁移；旧schema/rule兼容读取；无删除列/历史回滚 | PASS；V161新增，C3真实V158→V161及旧Flow/Replay；Worker/Gate支持旧0/1/2及新3；未执行部署/回滚 |
| 01 I-1 | RAW/discovered一次scanAll、500批、仅initialized六decision→四组 | PASS；CountsService.kt:92–128、C1 Counts9/Controller5含15501/32批oracle及20并发；非SQL GROUP BY替代 |
| 01 I-2 | 成功才完整值；失败码/旧asOf/未知非0 | PASS；CountsService.status及app counts渲染，C1/C5；O-01不扩大成失败终态scanned保存要求 |
| 01 I-3 | 只写task审计、GET不启动、scroll finally/完整性、独立池 | PASS；ScanService.kt:146–204及Executor，C1 failure/并发回归；无verification/admission/ES写入口 |
| 02 I-1 | first.emails真实计数，姓名非proof，analysis缺失为未运行 | PASS；ArchiveService.kt:340–358，C1 ArchiveService17、C6文本/旧行为 |
| 02 I-2 | PDF magic优先；合法DOCTYPE且禁XXE；错误不伪NOT_FOUND | PASS；PaperContentFormat及Extractor.kt:161安全设置，C1/C3 Extractor15/Archive17/Analysis9 |
| 02 I-3/S-1 | first/SHA不改、缓存版本、预览不写、lease关闭，旧保护/删除不变 | PASS；AnalysisService.compute→显式commit、Archive.withContent，C1/C6及C11原reader |
| 03 I-1/2 | 明确唯一归属/缩写及坐标脚注恢复；相邻/共享/未知负例 | PASS；Layout/Resolver累计diff，C1 Resolver27/Layout结构/Pdf/consumer；original294明确skip，真实全源历史同SHA证据保留 |
| 03 I-3/4 | 不制造邮箱；独立layout版本、旧identity/cache/queue兼容 | PASS；PlainText8、Capture11、原consumer/队列及Worker；Identity常量无diff；全源源SHA/实现一致 |
| 04 I-1/2 | 本人简介原句、否定/学生/相邻隔离；发表时职位、完整出生日期 | PASS；Extractor.kt:23–153、C1/C3 Extractor15、PaperEvidence10及C2真实paperFacts2 |
| 04 I-3/4 | 绑定ORCID锚定姓名、rule3复合职称、旧0/1/2不重算、固定评估时刻 | PASS；Rules.kt:390/451，C1 Rules23/Worker17、C3 Gate10；无context旧clock语义保留 |
| 05 I-1 | V158迁移保留旧id/owner/evidence/request_count，普通去重/新轮独立 | PASS；C3 Repo29、旧Flow32/Reporting12/Integration14、C2/C12历史不变 |
| 05 I-2 | queued/AUDIT/pending epoch不进入业务Gate；只提交receipt生效 | PASS；Repository BUSINESS_ITEMS:1098、Gate过滤；C3 Repo29/Gate10及C2 AUDIT/保护 |
| 05 I-3 | spec固定、control CAS、READY hash/revision | PASS；Repository.kt:367–608、Service.kt:109/233，C2/C3真实SQL和HTTP幂等/冲突 |
| 05 I-4/5 | 八步attempt终态不可改；cancel/claim/renew/reserve/requeue隔离 | PASS；TraceRepository及Repository.kt:652/769/805，C3 Trace4/Repo29/Replay7，C2取消/恢复及旧CLI隔离 |
| 06 I-1 | compute纯函数、commitAnalysis显式分离、不写first/ES | PASS；PaperBodyAnalyzer.kt、AnalysisService，C1 Analysis9/PaperEvidence10、C2真实原文读取快照 |
| 06 I-2/3 | 冻结record/attempt/SHA及本人proof；真实来源，SELF/HISTORICAL原句 | PASS；PaperEvidenceService.kt:54–157；C1 PaperEvidence10、C2 paper2→saved4→Query/HTTP、C11 reader |
| 06 I-4/5 | REUSE无HTTP、REFRESH不写三层、只可信绑定学术ID | PASS；AcademicService.kt、C3 Academic8/OpenAlex90、C1 Worker17及C12；无姓名猜配 |
| 07 I-1 | 共用Worker/Rules/Commit，建议与应用分离 | PASS；C1 Worker17/C2/C3/C12真实链及Gate复算；无第二worker/准入旁路 |
| 07 I-2 | 固定八步、真实卡点/跳过/旧史、证据可解释 | PASS；C2八槽/旧recorded=false/直接跳6，C1/3 Trace；V-1已由真实存储→HTTP→UI闭环解决 |
| 07 I-3 | evaluationAt/hash/context固定、旧缺context语义 | PASS；Worker.kt:298–367/Gate.kt:69，C1 Worker17/Rules23、C3 Gate10及C12冻结输入 |
| 07 I-4/5 | 冻结配置/续跑、逐HTTP取消、迟到诊断一次、仅重试同步 | PASS；OpenAlex.kt:77及取消传播；C1 Worker17、C3 Academic8/OpenAlex90/Trace4、C2 commit/cancel两序/投影provider0 |
| 08 I-1 | 服务端固定IDS/ALL_MATCHING、请求幂等、hash/revision、无全页截断 | PASS；Service.kt:109–199/Repo.startRecheck:492；C2真实1001/三批、C11两个scope真202/READY |
| 08 I-2/3 | 默认AUDIT/REUSE、只读准备、部分失败不READY、重启/取消 | PASS；C2 partialFreeze FAILED/500/notClosed，PREPARING→INTERRUPTED、READY取消、准备provider/business0 |
| 08 I-4 | 会话401/非法400/跨run404、分页、任务成功≠核验完成、指标分列 | PASS；Controller及Query，C2 825HTTP/C3 Reporting12；C11实际prepare合同互通 |
| 08方案5 / 09 I-3 | 本轮已存原句/来源/SHA/已有定位到详情 | PASS；QueryService.kt:180/210/415、recheck.js:262/275，C2/C5/C11；V-1 RESOLVED |
| 09 I-1 | 真实docId、迟到Promise隔离、unmount清timer/listener、单poll | PASS；app宿主差异/模块生命周期，C5/C7现有回归及C11真实切历史/reader/Esc |
| 09 I-2 | 默认两scope能准备、READY前禁用、选项/筛选变化失效、Esc不cancel | PASS；recheck.js:75–120及102 wire；C2真实非法负例/C5签名回归/C11四次202/READY；V-2 RESOLVED |
| 09 I-3 | 八步/原因/尝试/版本、before/proposed/current/applied、同步独立、旧史未知 | PASS；Query及renderStep路径，C2/C3/C5/C11；saved证据保留原时间，XML未知页不造页码 |
| 09 I-4/S-1/S-2 | 静态DOM/CSS全文、允许class/textContent/safeURL、18资源/顺序、两视口 | PASS；C14全文匹配、C5/C7资源/动态节点、C11真Chromium 1280/640；不以弱DOM stub替代截图 |
| 10 I-1 | 固定来源/身份/fact/revision/config/版本/hash；同manifest真实重复核账 | PASS；未变8类hash+本轮4487源SHA、既有真实同源重复重算收据；C2/C10 fresh CLI双compare；C12新897；复用边界见上 |
| 10 I-2 | 生产parser permit→原consumer/登记/门禁；RAW首次1重复0 | PASS；ExpertDiscoveryService.kt:1745、BackfillService，C2/C10真实隔离apply/consumer→Worker/Gate→candidate；旧checkpoint/mail不变 |
| 10 I-3 | compare默认业务/provider0；显式hash应用、当前身份/来源重核、700/600 | PASS；Tool纯值路径及load/readBody，C2/C3错hash2/身份漂移、C12/C13及私有权限 |
| 10 I-4 | 缺失/PARTIAL/取消/receipt幂等、普通10秒与回放工作限分开、旧attempt不清 | PASS；C3 Backfill3/Tool6/Extractor15、C2/C12；历史PARTIAL/15无ref如实保留，factory仅Tool，普通deadline通过 |
| A14-file | mailboxGroupPush限定资源同步与真实chronology | PASS（机器功能/scope）；上方逐文件矩阵及本轮1843JS；人类追认PENDING |
| A-M1–A-M5 | 人工数量、统一入口/对照隔离、取消旧史保护、收益原句抽查、页面/Apollo配置及最终sign-off | PENDING；机器浏览器不代替人类结果；必须绑定当前candidate、governing master及A14文件 |

### Finding Lineage

| Finding | State | 当前证据 |
|---|---|---|
| V-1（原P1） | RESOLVED | Query只读投影本轮identity/fact匹配saved事实；真实paper2/saved4→Query/HTTP逐字段一致、queryWrites0；真浏览器步骤4可见并打开同源reader；missing/malformed/empty/截断/错误ref负例通过 |
| V-2（原P1） | RESOLVED | IDS filter省略、ALL_MATCHING空keys规范化；真组件非空/空筛选到真实Servlet全部202/READY；原严格400负例继续通过；两视口真实4请求验证 |

阻塞集2→0，无同级或更高新增回归，故PROGRESSING。没有用更多重叠测试或改名制造收敛。

### Findings

P1：N/A。P2：N/A。

Observations：O-10-1非绿完整测试、O-10-2 JDK差异、未执行的默认skip、历史PARTIAL/15无ref、既有私有会话证据限制全部保留。它们没有被另写成无关repair任务。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master映射 | 本轮结论/证据 |
|---|---|---|
| O-01 | 01 I-2失败透明 | RECORD_ONLY；合同要求运行中进度/失败码/旧asOf，未要求FAILED终态保留scanned。源码条件update差异仍在，不把fixture视为真实终态计数证明；C1 Counts9/C5状态行为通过 |
| O-02 | I-M4命令执行 | compiler/source-root等warning存在；C2/C3/C4可核exit；无已证本scope功能违约 |
| O-03-01 | 03 I-4/I-M3真实性 | original294/EuropePMC继续明确skip；未豁免必需真实SQL；全源同SHA重算证据复用及本轮4487SHA复核如上，不称新全源解析 |
| O-03-02 | 03解析/环境 | 字体/fixture/classpath warning保留，当前target类在CP前；C1结构解析及C2实际parser、未变全源证据覆盖对应合同 |
| O-04-01 | 04规则/I-M1 | Jackson/compiler/MySQL/Flyway及fixture限制保留；Rules23/Extractor15/Gate10及实际原文链补足；未称真实生产服务验收 |
| O-05-01 | 05持久化/取消 | 既有弃用/故障注入warning不成新finding；Repo29/Trace4/Reporting12/旧Flow32及C2真实竞争通过 |
| O-06-01 | 06纯计算/证据 | source-root/compiler warning非功能失败；C1 Analysis9/PaperEvidence10，C2 queryWrites0及C12业务不变 |
| O-08-E1-01 | 08真实运行/生命周期 | Tomcat9/Java11 shutdown反射/acceptor warning仍见；825HTTP实际完成，私有后端exit0；不修无关容器warning |
| O-10-1 | master完整mvn test | 本轮5624/1/0/24仍非绿；已逐blob及固定capturedAt/7天retention/系统Clock因果复核，无新故障；按已批准排除项保留，不称PASS命令 |
| O-10-2 | 必需JDK | 新JDK11.0.32.1下ExpertContactLocationServiceTest19/19；没有改产品或宣称旧JDK错误已修 |
| O-10-3 | I-M3完整性/真实MySQL | 24skip、历史PARTIAL3907/4487、15待审无ref保留；本轮C2 33及C3 276全部0skip，C12实际897完成 |
| O-10-4 | I-M3/证据隐私 | 原writer私人会话email数组输出事件仍保留，未删除/声称擦除，不引用raw；非已证本授权5文件产品根因 |
| Epoch1 reviewer证据处理事件 | 同上 | 旧报告已记私人目录宽rg误输出metadata/原文片段，仍保留限制；本轮只精确文件/聚合/hash，无新增历史rows输出 |
| 07、09无RECORD_ONLY条目 | 无新增要求 | N/A；未制造新条目或以缺条目代替合同审查 |

没有RECORD_ONLY证明新的必需产品违约，未提升为阻塞finding。必需证据无缺失；可选历史语料/网络/生产/人工未执行部分仍有界。

### Evidence Boundaries / 失败尝试与清理

- C13前首次远端默认ASCII失败exit1，显式UTF-8后exit0；原SSH255/第二轮退出码缺失继续记在复用历史证据内。本轮远端授权审批获准，无新auto-review拒绝。
- 浏览器读取能力文档时一次误用`browser.documentation.get`返回method不存在；随后读取正确capability.documentation，精确尺寸、截图和UI动作全部完成；不是产品错误或缺失门禁。
- 临时Kotlin编译有一个已不存在旧probe-classes classpath warning；当前target/test-classes及target/classes在前、实际新字段及新回归真实运行，未加载旧probe冒充修复。
- 预期fixtureHTTP500、wrong-hash exit2及Tomcat清理warning均保留，不当作测试失败隐藏；proxy SIGINT130为完成后的有意清理。旧执行handoff中的编译/测试失败尝试仍作为历史保留，不重写。
- 原文全源仍PARTIAL，旧部署负例只作旧证据；未执行本轮4472全源再次解析，已说明对应未变合同的证据复用理由。没有把548观察绑定、37事实或21建议当新增专家/实际准入。
- 无生产数据库/ES应用、发布、push/merge、付费provider、邮件/联系人创建；合成ENFORCE/apply仅测试隔离环境。
- 浏览器其他宿主读API为fixture；重审实际MVC/SQL、原文reader服务导出数据的具体边界如上；人工A-M1–A-M5与最终governing-master/candidate/A14-file sign-off仍PENDING。
- 本reviewer只写私有临时源码/日志/报告及忽略构建输出；未编辑产品、测试、任何仓库review/plan文件，未stage/commit/改HEAD或branch。初始及收尾`git status`仅控制器已有review ledger变更；产品及index干净，不虚称整个worktree全clean。

### 制品与核心收据

本轮WAR `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/target/weibo-talent-introduction-1.0.0-SNAPSHOT.war`：52018915 bytes，SHA256 `68c86a358917874a884aba4e2f6c1b73cc748e2315b1997a9607a478930c0e70`。WAR中的QueryService.class、Extractor.class与当前target类逐byte一致；WAR recheck.js与当前源文件逐byte一致。仅构建，未部署。

私有根：`/private/tmp/discovery-aggregate-review-epoch2`；目录0700。

| 文件 | SHA256 |
|---|---|
| full.log | `f7fa85063b308d655e544f4a16b35c304f07e1a062fc51e35b4f38916135333a` |
| full-summary.json | `5fb95b72eadab6df790eb881b1ec4d74b77ca767ca5d46fe2a7f85b66ea25ced` |
| repair-targeted.log | `a79fe760b2c31fc2cc0494aac855bc20ad8169766309e597c4580099be1d11af` |
| repair-targeted-summary.json | `9fb03d6cf1805fb201cbb2aecb05f9d1d6d4718d008df85e118b6404ec763fd7` |
| aggregate-mysql.log | `38204e8023d92ffec8a3f365243365d0a826f084d7465de986b5af997f2db73e` |
| aggregate-mysql-summary.json | `f98f93b09ca4f556ed4957f2e281822d01bc4567b7b57859cb7a2027b48277a1` |
| package.log | `b9ce2887dbe260541e7356aa2e81f805c17b5ee6cf6aaab2bad49cbaa6427eb9` |
| focused.log | `62b57dc66f7adb79d05a7b4129dbb678b832c9c17da7a1338cccd58b15948ab8` |
| reader.log | `c710ac5c17da841e342ede62eb4e624403cf24d85d51c4a0cb469cb44b642782` |
| js.log | `9532f0f7448a1391fe7d4b40f9aaa99b4118aaa831ab076561e01c15cd11c1ad` |
| historical-audit.log | `73ef8bcb366f7abf1b3bb028b249875ecf6541826cea9c8c9e8b4b5a4998764a` |
| historical-summary.json | `8ea722a065f4aab1dd5879786d52e14651bf95478bb82cad43108ea26705f982` |
| remote-source-recheck.log | `60d9df28be5d5d3b9d2071fc5acec6abd9f30df010ab64506696bef5b5954774` |
| remote-source-recheck-utf8.log | `ba30b5fcada2363faaad0a942ac9d017cd56932f77f1911e54cf4ddc434e8413` |
| browser-backend.log | `fab0b315212ca28f12005d36fbff134ce319167fa3ac331dd59079846c2381e4` |
| browser-summary.json | `9015887dee519ae55795183437f834d2ccbeb86a8e27f784b99ef8828aca6eff` |
| browser-wire.jsonl | `ba6d093738cb9c93e00cdc61a3209e23eb566277fe52047a3c89039d8e7f9671` |
| current-implementation-hashes.json | `437cc2d30164d814eedace5090e8699872fd60999d932f620ff3a5c7bf9b722e` |
| amendment-identities.json | `11828f27f7d11a1d2045d24ad9df367a6358f9a265695a8e3b1541171c62f9f1` |
| syntax-summary.json | `1e1df724c92525ff2a0499cbfc09ecfe88931b8522b840c16ca0f10cb6fb386f` |
| runtime-cp.txt | `d9a38d5fb4a585f08e9f33cf5085446e43630913a050bbf0de3ea45fe5134da2` |
| BrowserRuntime.kt | `dfa599adfdb2eff3ec3c5fde213b3de768fb740db9f4dc1582dc0a218cbb85a7` |
| browser_proxy.py | `1ae9c1633d2543218cb047a14db767109eebfde3406393d26949328ed5f17533` |

截图：私有根目录下`wide-ids-ready.png`、`wide-all-ready.png`、`wide-evidence.png`、`wide-reader.png`、`narrow-ids-ready.png`、`narrow-all-ready.png`、`narrow-evidence.png`、`narrow-reader.png`；逐文件hash/像素尺寸见browser-summary.json。全部为合成对象。

### Repair planning

N/A：verification PASS，未触发repair-p。既有repair.md保持上述批准身份与历史lineage，没有新修复草案或自动执行。

### Next Action

由控制器持久化本Epoch2完整报告，再进行当前`50f8484ed2c30d51adf781c8b07a16920feab75e`边界的A-M1–A-M5人工验收；人类明确接受governing master `da8940153f4ad22a5ac3a094f444a7ac4858f695`/SHA256及A14所列`mailboxGroupPush.test.js`后，才能判定后续集成状态。本报告不授权merge/push/deploy或生产应用。

No product code was modified.
