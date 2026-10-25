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
