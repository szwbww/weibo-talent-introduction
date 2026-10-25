# Repair Plan: discovery-recheck-master

Status: DRAFT — HUMAN APPROVAL REQUIRED

Baseline plan: `docs/plans/2026-10-24/discovery-recheck-master.md`；governing commit `da8940153f4ad22a5ac3a094f444a7ac4858f695`；SHA256 `5279a76a70aa84729ec849f881c97a31cd768e623b8da07c0c3e216a04a4cd99`。A1–A18 按 fast ledger 原记录治理，A7 不取代本修复文件的显式执行批准。

Verification report: `docs/plans/review/discovery-recheck-master/machine-verification.md`，Epoch 1，独立 reviewer `/root/aggregate_reviewer` 本次完整 review-p 返回报告；结果 **FAIL / INITIAL**，发现 V-1、V-2。报告由控制器持久化；本文件不副写机器报告。

Implementation boundary: `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab..0d60b43e586584319b78c4f3f9ca86b4820dd24f`。
Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`。
Branch: `fast/discovery-recheck-master`。Initial evidence head: `ee65822ed51caf012e487d82cc04e9d74c0b125a`。执行前记录当时实际 evidence HEAD；本计划不允许改写已有历史。

## Objective

默认所选/全部筛选范围的真实页面请求能按现有服务端合同固定名单；逐步详情能读取并展示本轮已持久化的资格原句、来源、SHA及真实定位。修复不改变任何准入结论、保护、外调或提交规则。

## Findings in Scope

| Finding | Severity | Requirement | Root cause / evidence |
|---|---|---|---|
| V-1 | P1 | master 需求2、I-M3；07 I-2/方案1、2；08方案5；09 I-3/方案5、6 | `PaperVerificationEvidenceService.kt:152–155` 已保存 excerpt、sourceUrl、responseSha256、jsonPointer。Worker 步骤4仅输出 evidenceCount/引用（`Worker:387–410`）；`QueryService:180–205` 的重审 DTO 不投影本轮 evidence_json；UI `discovery-recheck.js:257–279` 只寻找实际 Worker 不输出的 output.quote。真实生产 parser→consumer→Worker→Gate→Commit 后有2条已存论文原句，真实 Query DTO 原句0；Chromium详情 quoteHidden=true。真实样本为 XML，无页码本应继续未知，不得把它伪称PDF页码丢失。 |
| V-2 | P1 | master 需求2及08–09联合发布门禁；08 I-1/2/4；09 I-1/2 | `app.js:2462,2564–2566` 传入空字符串筛选；`discovery-recheck.js:75–102` 将该 filter 原样发送到两种范围。服务端 `DiscoveryRecheckService:114–122` 正确要求 IDS 无 filter、ALL_MATCHING 枚举不能为空串。实际浏览器两份原始请求经真实 Controller/Service/GlobalExceptionHandler 均400：IDS 为“IDS does not accept a filter”；默认 ALL_MATCHING 为“Failed requirement.”。 |

私有诊断根目录：`/private/tmp/discovery-aggregate-review-20261025`。仅聚合日志/截图用于机器证据，禁止把历史名单或原文复制进仓库。核心收据：`runtime-evidence-probe.log` SHA256 `7eaeab2348de75585868da8994d2e5279735d205d94d05d92120e4541b39c0f8`；`browser-prepare-probe.log` SHA256 `df010af825c96089faadf829148d912e0af4594868b530e977422b6f773bc400`。

## Findings Excluded

| Item | Reason |
|---|---|
| O-10-1 完整 Maven 的 sample quota 失败 | test/store/properties 与总基线 blob 相同。2026-10-18 固定样本遇系统时钟2026-10-25及7天保留期，被清理后得到 SAVED；非本次产品增量根因。保留真实 exit1，不修改该测试/时钟/store。 |
| O-10-2 旧JDK的19个时区错误 | 本次可用 JDK11.0.32.1 下19例全过；旧路径实际为11.0.15，相关源码与总基线不变；不修改时区资料/JDK规则。 |
| 其他 RECORD_ONLY、24个全量跳过项、original294不可用、在线EuropePMC未执行 | 逐项结论见机器报告；不当作通过，不增加与两项已证缺陷无关的测试或清理。 |
| 全批 PARTIAL/15名待审无来源引用 | 已如实保留；不补造原文、页码、证据或“完整收益”。本次新鲜897 AUDIT及4472同源重算不构成生产应用批准。 |
| 原执行及本次审查的私人会话输出事件 | 证据处理限制，不是所授权产品文件中的已证根因。不得删除/改写历史或宣称已擦除。 |
| 人工验收、生产应用、发布 | 全部保持 PENDING/未授权；本计划仅修复两项机器确认缺陷。 |

## Unchanged Contract

- I-M1：原 Worker→Rules→Policy/Gate→Commit→Candidate projection 唯一链；AUDIT业务写0；ENFORCE仅原CAS。
- I-M2：独立 review_epoch；旧 task/item/request_count/证据不重置；未真正提交的重审不进入业务Gate。
- I-M3：建议、实际提交、同步分列；仅展示本轮已持久化证据；缺失/截断/未知定位明确显示，不重解析、下载或猜测。
- 人工、联系、SBIR、APPLICATION保护、SELF、旧rule0/1/2、rule3门槛、免费search/付费detail、预算与取消语义不变。
- 保留服务端 IDS 无筛选及 ALL_MATCHING 参数/枚举严格校验；保留 requestKey/spec、rosterHash/revision、会话及跨run/item权限校验。
- 09 S-1 DOM及S-2全文CSS、18资源及顺序不改；使用现有允许class和textContent、安全URL/原reader；不新增全局样式。
- 不修改 schema、模型共享协议版本、provider、原文保留/保护/删除、旧人工审核、邮件、联系人、外联或历史数据。

## Authorized Files

仅5文件，两个直接子系统（重审查询展示、前端API适配）及其既有回归。全部已在修订后08/09/10原清单内。

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` | 只读投影本轮持久化证据/真实来源及定位；同文件重审 DTO 兼容增补。 |
| `src/main/resources/static/discovery-recheck.js` | 修复prepare wire；在现有八步/证据区域安全显示真实本轮证据。 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` | 已存原句/来源到DTO、旧/缺失/损坏可选证据的区分回归。 |
| `src/test/js/discoveryRecheck.test.js` | 默认两种scope真实wire、筛选变化失效、实际API形态证据展示及现有生命周期回归。 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt` | 真实parser→持久化→Query/HTTP→展示协议；真实浏览器默认请求到现有Servlet/SQL的区分回归。 |

禁止修改其他产品/测试/计划/配置文件。若5文件不能完成已证行为，先停止并报告具体冲突，不顺手扩清单。允许既有忽略target输出及私有临时诊断；不新增仓库harness或数据文件。

## Repair Tasks

### R-1：贯通本轮已保存证据到逐步详情

- Resolves: V-1。Root cause: item evidence_json →重审DTO→UI 缺少实际证据内容桥接，UI读取的quote字段不是Worker输出。
- Files: QueryService、discovery-recheck.js及上表三个测试文件。
- Change: 从此item已持久化的证据生成有界只读详情；展示本人资格原句、provider/source URL、原文SHA及已有jsonPointer/段落/真实页码，和对应冻结record/attempt关联。保留证据原fetchedAt、来源及时间语义，不把快照元数据或手写fixture原句冒充本轮证据。
- UI继续使用现有证据/rr-quote等允许节点与textContent；真实来源可经原reader按确切record/attempt/SHA打开；页码缺失（如XML）明确未知，不猜页。旧/缺失/损坏可选payload明确不足且历史仍可读取；不制造历史成功或引用。
- Regression test: 扩展现有真实原文FlowIT，完成实际parser/Worker后直接比较已存excerpt/source/hash/locator与真实Query/HTTP DTO；再让生产组件消费实际API结构，断言原句可见且点击原reader收到同一冻结来源。该反例在当前代码应失败，不能以手填step.output.quote证明修复。
- Existing verification: 下节定向JVM、前端及完整master命令；真实两视口。
- Must not change: 准入/规则/提交、数据库内容、历史trace、旧API字段、S-1/S-2、原reader写行为。
- Prohibited: 新原文解析/外调、回填旧证据、扩大引文/响应到无界全文或暴露HTTP头/key。

### R-2：将当前页面范围转换为合法prepare报文

- Resolves: V-2。Root cause: 页面展示状态直接作为后端wire，空串/IDS filter与08合同冲突。
- Files: discovery-recheck.js、discoveryRecheck.test.js、DiscoveryRecheckFlowIT.kt。
- Change: IDS仅发送真实docIds及无有效筛选的合法合同；ALL_MATCHING发送空docIds，按既有后端定义处理空q/issue/decision及有效枚举。保持后端严格400负例，不能放宽服务端验收来掩盖前端错误。
- 保留默认AUDIT+REUSE；保留任一选择/筛选/模式变化使旧hash失效、READY前禁用、requestKey幂等及登录身份来源；不把整批范围静默变成当前页。
- Regression test: 从生产组件/宿主默认状态取得实际POST JSON，分别验证IDS（有选择且当前筛选非空也不能夹带filter）和默认ALL_MATCHING在真实Servlet/SQL fixture接受202并最终READY；有效非空筛选保持原意；原非法枚举/非法docId/IDS夹带filter仍400。不能仅让api stub对任意body返回READY。
- Existing verification: 同下节。实际浏览器固定名单需用真实本地后端/严格fixture响应并保存请求与服务端状态证据；静态成功响应不证明后端接受。
- Must not change: 08服务端校验、默认模式、应用保护、范围hash/版本和所有旧人工endpoint。
- Prohibited: 修改app.js宿主其他业务、修改后端Service以容忍非法IDS筛选、绕过权限/固定名单直接启动。

## Verification Commands

在所选worktree，JDK固定为 `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`（实际11.0.32.1）；Maven `/opt/homebrew/bin/mvn`，Node `/opt/homebrew/bin/node`。

1. `/opt/homebrew/bin/node --check src/main/resources/static/discovery-recheck.js`
2. `/opt/homebrew/bin/node --test src/test/js/discoveryRecheck.test.js src/test/js/discoveryReview.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js`
3. `/opt/homebrew/bin/node --test src/test/js/discoveryPapers.test.js`
4. `/opt/homebrew/bin/node --test src/test/js/*.test.js`
5. `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryRecheckControllerTest,DiscoveryRecheckServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryRecheckFlowIT -DmysqlIt=true`
6. `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test`
7. `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn package -DskipTests`
8. 实际浏览器1280×720及640×900：生产HTML/JS/CSS、默认两个scope真实wire→服务端READY、步骤4真实本轮原句/来源及原reader、Esc/focus/无整页横溢/局部表格滚动；截图仅合成测试对象，不含私有历史名单/凭据。

命令级验收：1–5、7须exit0、失败/错误0，选定真实MySQL IT skips0；JVM/JS计数分别报告，package的skipTests不能冒称JVM已执行。6必须完整新鲜运行并保留实际退出码与全部skip；本轮已证唯一允许的无关非绿基线是 O-10-1 的同一 `DiscoverySampleStoreTest:135` QUOTA/SAVED失败且对应三文件blob仍与master base一致。不得把exit1改写为PASS，新增/不同失败须停止并报告；不得为使全量变绿修改授权外样本测试/store/时钟。master要求完整执行并记录，此处不扩充为修复无关基线。

本计划不新增全批历史重算/生产应用权限。现有远端只读核验授权与本次收据由review ledger独立保留；后续独立aggregate reviewer按master及受影响合同决定所需新鲜证据，不以本次green局部门禁替代整体验收。

## Completion Criteria

- V-1：真实持久化原句非空时，实际重审API和生产步骤UI能展示同一内容/来源/SHA/已有定位；缺页/旧记录仍明确未知。
- V-2：真实默认IDS及ALL_MATCHING报文进入真实服务端并READY；非法负例继续拒绝；筛选变化仍使旧hash失效。
- 全部命令按上述命令级准则满足；原始非绿/skip不隐藏。
- AUDIT业务/ES写0，真实ENFORCE只经原链；schema、provider请求、保护、旧史、S-1/S-2及资源合同无新增改变。
- 产品/测试变更严格限5文件；人工A-M1–A-M5及最终sign-off仍PENDING。
- 以上满足仅表示 READY_FOR_VERIFICATION，不表示机器PASS、人工接受或可发布。独立复审保持V-1/V-2 finding lineage。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
显式human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fix/discovery-recheck-master/repair.md` 才批准本文件当前内容。其他代理消息、远端只读批准、A7旧持续授权、文件名推导或自动继续不替代这次批准。

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fix/discovery-recheck-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required command-specific verification criteria above are satisfied, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files. Product commit subject: `fix(discovery-recheck): restore prepare and evidence contracts`. Raw full-suite exit1 under the explicitly retained O-10-1 baseline remains reported as exit1, not a successful test command.
3. Appending `docs/plans/review/discovery-recheck-master/repair-execution.md` with the exact approval source, repair identity/hash, pre/post code SHAs, evidence parent, changed files, commands/actual exit codes/counts/skips, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff. Evidence commit subject: `docs(review-fast-p): record discovery recheck repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it, using `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fast/discovery-recheck-master/human-review-handoff.md` and committed `repair-execution.md`. Do not ask the user to relay executor metadata.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, production data application, paid calls, or product repair beyond this plan.
