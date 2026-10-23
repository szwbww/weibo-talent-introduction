# Aggregate Machine Verification — batch-demand-master

## Epoch 1 — 2026-10-23T02:06:08.945843+08:00

- Master identity state: AMENDMENT_RECORDED (A1–A4)
- Repair evidence mode: N/A (initial aggregate review)
- Reviewer: /root/aggregate_review (fork_turns=none)
- Result: FAIL
- Convergence: INITIAL
- Repair result: DRAFT_READY

## Verification Result: FAIL

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-master.md`
Phase: aggregate/master
Implementation boundary: `d403caeb5dcc4810067293c10600ee57f94e276b..37a76a66a40e1614bec7c1e92324c7beee7c0be0`
Evidence HEAD: `341eed4dfe1d82ae487f08c59a2533ca30f5c978`
Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master`
Branch: `fast/batch-demand-master`
Reviewer: `/root/aggregate_review`；独立新 reviewer，无开发/轻量验证对话继承。
Convergence: **INITIAL**
Manual acceptance: **PENDING**

### Governing identity / authority

- Governing master SHA-256: `c8922f6c7ab011303844f3c8b7dae181eb99eb22d47bdc7db2662eec2f7c16d1`
- Recorded master identity: commit `1ba7003f2719165e4d420132255d071102cd6c3e`
- Invoked master SHA-256: `b0258287e77dcea06f081ee80aea1f38cd7acedfc6a6d9066d76efa6eb5c6e6a`
- State: **AMENDMENT_RECORDED**；采用保留工作树的已批准版本。
- Governing amendment **A4**：
  - Master rule: `Invariant I-4: 逐段范围门禁`
  - Reason: `Synchronize child04 ten-file scope and aggregate manifest with approved A3`
  - Approval: `HUMAN:2026-10-22 selected 批准第10文件并继续; authorize tracking fixture migration, nested material migration, obsolete10000-test deletion, renewed execution and installed Java11.0.32.1; no timezone product change`
- A1/A2 批准同一 PIT 的逐次页大小扩展与相关测试；A3/A4 批准追踪测试夹具迁移及十文件范围。已检查指定 governing diff，没有降低强制不变量。
- 七份计划均与各自记录 identity commit **逐字一致**。
- base、初始计划 identity、修订 identity、final code、evidence HEAD 的 `git merge-base --is-ancestor … HEAD` 均 exit **0**。
- `final_code_head..evidence_head -- src pom.xml` 无产品差异，exit **0**。
- Fast ledger、handoff SHA-256 均与输入一致；六段终态均为 LIGHT_PASS。
- 最终产品、测试、index 无未提交修改；HEAD 未变。未跟踪内容仅控制器 review 目录与本 reviewer 创建的指定 repair artifact。

### Commands

所有测试工作目录均为上述保留工作树。Maven 命令使用 Java **11.0.32.1**；Maven **3.9.11**，Node **25.7.0**，Docker server **29.8.2**。

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test` | PASS，exit 0 | JVM **5478 / failures 0 / errors 0 / skipped 23**；绑定 Node **1805 pass / 0 fail / 0 skip**；BUILD SUCCESS，08:33，2026-10-23 01:47:21 +08:00。日志 `/private/tmp/batch-aggregate-mvn-full.log` |
| `node --test src/test/js/*.test.js` | PASS，exit 0 | **1805 tests / 334 suites / 1805 pass / 0 fail / 0 skip**。日志 `/private/tmp/batch-aggregate-node-full-escalated.log` |
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=ExpertContactCursorRepositoryIT -DmysqlIt=true` | PASS，exit 0 | **3 / 0 / 0 / 0**，真实 MySQL **8.0.36**；绑定 Node1805通过；BUILD SUCCESS，03:01。日志 `/private/tmp/batch-aggregate-mysql.log` |
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=ExpertSearchCursorTest -DesIt=true` | PASS，exit 0 | **10 / 0 / 0 / 0**，真实 ES **7.14.2**；绑定 Node1805通过；BUILD SUCCESS，02:47。日志 `/private/tmp/batch-aggregate-es.log` |
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=ExpertSearchServiceTest,ExpertDiscoveryServiceTest,ClassificationWireFormatTest,ExpertIndexWriterServiceTest,ExpertClassificationServiceTest,OperatorStatusWriteSeamGuardTest` | PASS，exit 0 | 已自然完成的01 focused：**373 / 0 / 0 / 0**，绑定 Node1805通过；BUILD SUCCESS，03:55。日志 `/private/tmp/batch-aggregate-child01.log` |
| `node --test src/test/js/batchManualExecutionLog.test.js src/test/js/batchExecutionLogTimeline.test.js src/test/js/batchDemandProgress.test.js` | PASS | **42 tests / 42 pass / 0 fail / 0 skip**。日志 `/private/tmp/batch-aggregate-node-progress.log` |
| `node --test src/test/js/recipientPreviewDiagnostics.test.js src/test/js/batchEmailVerification.test.js src/test/js/batchSendTaskConsoleInteraction.test.js src/test/js/batchRecipientStatisticsCache.test.js` | PASS | **141 tests / 141 pass / 0 fail / 0 skip**。日志 `/private/tmp/batch-aggregate-node-cache.log` |
| `node --check src/main/resources/static/app.js` | PASS | 无语法错误；与上两条共用 shell invocation 的最终 exit **0** |
| `node /private/tmp/batch-aggregate-key-repro.js` | **V-1 confirmed**，exit 0 | 生产 JS 函数收到 ERROR/key B/summary null 后，打印 displayedKey A、100人、旧时间 |
| `git diff --check d403cae 37a76a6 -- src` | PASS，exit 0 | 产品/测试差异无空白错误 |
| `git diff --check d403cae 37a76a6` | 非阻断文档观察，exit 2 | 仅计划/历史证据文档尾空行、尾空白，见 O-1；日志 `/private/tmp/batch-aggregate-diffcheck.log` |

补充执行边界：

- 独立 Node full 首轮因工作树在可写根外，写 `target/discovery-plan-acceptance` 时 EPERM、exit1；提权重跑后全部通过。不是产品失败。
- 全量 Maven 的23跳过如实保留；其中 real-ES 门禁随后通过显式 `esIt=true` 执行。MySQL IT 不由默认 Surefire 名称集合覆盖，已单独显式执行，**没有将跳过计作通过**。
- aggregate 采用新鲜 full 对普通 focused 子集逐类佐证，避免重复逐段验收。01 focused 已开始，故自然完成并保存 exit0。
- 其余普通 Maven focused **未另行完成**，不声称执行过：02普通 subset、03详情 subset、04 wildcard subset、05缓存 subset。队列在01自然完成后停止；下一条02仅 launcher 起动即终止，未执行测试。队列终止 exit143 不作为产品失败，也不作为测试通过。记录 `/private/tmp/batch-aggregate-queue-stop.json`。
- 所有主计划强制命令均已新鲜完成。

新鲜 full 的受影响测试类计数：

| Class | Tests / Failures / Errors / Skips |
|---|---:|
| ExpertSearchServiceTest | 96 / 0 / 0 / 0 |
| ExpertDiscoveryServiceTest | 194 / 0 / 0 / 0 |
| ClassificationWireFormatTest | 2 / 0 / 0 / 0 |
| ExpertIndexWriterServiceTest | 51 / 0 / 0 / 0 |
| ExpertClassificationServiceTest | 29 / 0 / 0 / 0 |
| OperatorStatusWriteSeamGuardTest | 1 / 0 / 0 / 0 |
| ExpertSearchCursorTest | 10 / 0 / 0 / 1；显式ES重跑为10 / 0 / 0 / 0 |
| BatchSendExecutionDetailTest | 38 / 0 / 0 / 0 |
| ManualInitialOutreachServiceTest，含嵌套材料用例 | 241 / 0 / 0 / 0 |
| OutreachTargetIteratorTest | 28 / 0 / 0 / 0 |
| BatchSendTaskRuntimeIntegrationTest | 23 / 0 / 0 / 0 |
| BatchDemandExecutionTest | 26 / 0 / 0 / 0 |
| MailOpenTrackingPersistenceTest | 6 / 0 / 0 / 0 |
| BatchRecipientStatisticsCacheTest | 18 / 0 / 0 / 0 |
| MailComposeTemplateServiceTest | 73 / 0 / 0 / 0 |
| BatchSendConfigControllerTest | 9 / 0 / 0 / 0 |

关键实际输出：

```text
CURSOR_SMOKE localHTTP readTimeoutMs=20018 releasedPits=1 SMTP=0
CURSOR_SMOKE MySQL=8.0.36 initialNEW=501 firstPage=500 tailPage=1 highIdExcluded=502 readWrites=0 SMTP=0
CURSOR_SMOKE ES=7.14.2 documents=11001 pages=24 searchRequests=24 countRequests=0
CURSOR_SMOKE earlyStopSearchRequests=3 releasedPits=2 openContexts=0 SMTP=0
```

01 focused 再次测得 HTTP timeout **20023ms**、释放 PIT1。

### Contract Matrix

以下源码位置均在指定保留工作树；简称：

- Search：[ExpertSearchService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt)
- Cursor：[ExpertSearchCursor.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchCursor.kt)
- Manual：[ManualInitialOutreachService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt)
- Models：[BatchExecutionModels.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt)
- Cache：[BatchRecipientStatisticsCache.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientStatisticsCache.kt)
- Controller：[BatchSendConfigController.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt)
- Templates：[MailComposeTemplateService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt)
- App：[app.js](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/resources/static/app.js)

所有 I/X/S 与范围行均为强制合同；人工行单独 PENDING，不伪作机器 PASS。

| ID / Requirement | Verdict | Evidence |
|---|---|---|
| Master I-1：日期修复，不放宽资格 | PASS | Search:589 严格数组分支；Discovery:3275 仅替换分类节点赋值；真实 converter/scope replay2用例、分类29、writer51及资格回归全部新鲜通过 |
| Master I-2：统计与执行分离 | PASS | Manual:325、1144仅按额度/实时容量决定轮配额；执行无 Cache 依赖；Demand26用例覆盖两类型足额即停、失败/跳过补取及无全量读取 |
| Master I-3：历史与v2计数共存 | PASS | Models:129、405；Manual:2890、2941；Controller:377、399、539；历史1757/1710及新actual/quota两 reader、JS42全部通过 |
| Master I-4：顺序、范围及共享store限制 | PASS | 分段实际/授权文件数分别 **5/6、7/7、8/9、10/10、8/8、6/6**；累计授权34、改动33、清单外0；两JSON仅增加execution嵌套键；无DDL/CSS/config越界 |
| Master X1：分类writer→wire→Search→类型门禁 | PASS | Discovery:3275→真实 RestTemplate converter→Search:589→RecipientScope；ClassificationWireFormatTest两测试跨三层，非法分类保持拒绝 |
| Master X2：NEW/ES枚举→最新事实→投递事务 | PASS | Manual:1917、1987、2085、1478；NEW501真实MySQL、ES11001真实PIT及capture26通过；既有attempt/transaction/state路径保留 |
| Master X3：progress/result→两reader→新旧UI | PASS | Manual:2941写同一metadata；Controller:481、506；App:25092、25100、25131；详情38与JS42通过 |
| Master X4：模板/引用编辑→指纹→缓存隔离 | **FAIL** | Templates:216、Cache:36后端换键正确；App:24146–24148在新键ERROR时重新展示前键数据，V-1 |
| Master X5：缓存响应→两表单→按需执行 | **FAIL** | 发送不依赖缓存部分通过；两表单共享错误回退函数，服务端换键后可显示旧人数/时间，V-1 |
| 01 I-1：5/6/7项整数数组、旧格式、非法值拒绝 | PASS | Search:589–611；96个Search测试中覆盖三层、纳秒/闰日/浮点/溢出/错长/非法日期；无当前时间兜底 |
| 01 I-2：新写入统一字符串 | PASS | Discovery:3275复用classificationNode；普通doc与discovered params.doc均经实际converter检查字符串与其余字段 |
| 01 I-3：身份、null事实、三层边界保持 | PASS | Discovery仅一处赋值变化；wire测试及Discovery194覆盖HEAD404不新建、identity CAS/noop、无upsert、null不覆盖 |
| 02 I-1：稳定原始sort、无count/from、≤500 | PASS | Cursor:41–79、Search:749；完整raw sort含_shard_doc，空原始页才耗尽；真实11001文档/24页/_count0 |
| 02 I-2：续期、明确故障、3s/20s超时 | PASS | Cursor:86、103、117；Search:47；六次续期不预取、失败不静默重开；实际HTTP20秒超时及PIT释放 |
| 02 I-3：当前层按需打开、关闭及销毁 | PASS | Cursor:54、137、146；Search:782；锁序列化、幂等close、独立游标、shutdown；真实openContexts0 |
| 02 I-4：NEW只读keyset | PASS | ExpertContactRepository:38、155–194；MAX(id)、paired nullable cursor、updated_at/id严格小于、limit1..500；真实MySQL3测试零读取写入 |
| 03 I-1：两个store各一个可选execution字段 | PASS | Models:129；Manual:3102 nullable/NON_NULL；Controller:539严格解析；详情38含精确顶层/嵌套key与Long断言 |
| 03 I-2：actual/quota/历史语义及Long计划额 | PASS | Models:405–492；Manual:1835–1843、2947；运行totalCount0、终态actual；历史原值、计划2.5e9测试通过 |
| 03 I-3：双读取、progress回退、未知版本降级 | PASS | Controller:377、399、481、506、539；详情38覆盖running/final一致、unknown/bad JSON不丢outcome、列表不逐条查progress |
| 03 S-1/S-2：卡片、告警与既有样式合同 | PASS（机器部分） | App:25100、25131、25176复用现有DOM/class、escapeHtml、hidden；JS精确卡片顺序/颜色class/告警断言；无CSS变化；目测仍PENDING |
| 04 I-1：两循环成功配额、终止先于预读 | PASS | Manual:304、325、361及1122、1144、1179；Demand26覆盖30成功、5failed+5skip继续补到30、容量0不取人、最后一轮无额外页 |
| 04 I-2：NEW优先、逐次公式、空页、去重 | PASS | Manual:1843、1917、1972、1987；Iterator:106；实际60→20、NEW501、多文档不挤掉其它ORCID、跨11001过滤及邮箱去重回归通过 |
| 04 I-3：严格最新profile、收件地址与安全护栏 | PASS | Manual:1903、2085、370、437、1188、1478；两类型changed/strict failure/cancel回归；首封验证、绑定、历史、attempt与事务保留；材料仍用contact.expertEmail |
| 04 I-4：真实计数、读失败状态、所有路径metadata | PASS | Manual:541、1805、2890、2941；Demand26逐事件断言与final一致，0成功读失败FAILED、10成功PARTIAL_SUCCESS、取消无虚拟skip |
| 04 I-5：双循环、只读材料统计、finally释放 | PASS | Manual:544、1808、2612；两类型轮次/容量/取消/耗尽覆盖；材料11001统计零业务写入；cursor恰好关闭 |
| 05 I-1：缓存仅展示、统计零投递/业务写入 | PASS | Cache:33仅readonly seams；Controller:112唯一入口；Manual:624、2612；真实服务/cache/MockMvc组合smoke及writer/SMTP/verify spy断言通过 |
| 05 I-2：有效scope规范键、完整指纹、旧token保持 | PASS（后端） | Cache:100–118；Templates:216–266、387–407；集合规范化、门禁/验证策略/地区/类型/hasFields、引用正文/variants/enabled/default切换通过；versionToken回归通过 |
| 05 I-3：LRU64、两槽、单飞、时间/失败原子性 | PASS | Cache:41–98；18测试覆盖20并发同键一次、第三不同key拒绝、65→64 LRU、24h仅stale、失败保留旧时间、restart MISS |
| 05 I-4：MISS/READY/REFRESHING/ERROR与HTTP合同 | PASS | Cache:33–98、129；Controller:112–124；MockMvc覆盖cacheOnly零扫描、显式refresh、400冲突、503旧值/首次无值；不伪造0 |
| 05 I-5：完整目标、20000诊断、失败不提交 | PASS | Manual:673、694、2643；Cache:60–65；30001完整total/20000 partial、NEW去重、null/failed shard/timeout、模板中途变化回归通过 |
| 06 I-1：普通操作cacheOnly、300ms、显式单刷新 | PASS | App:24047–24063、24115–24138；JS141覆盖debounce、真实gate单请求、MISS/ERROR无自动重算、重复按钮禁用/恢复 |
| 06 I-2：MISS/0、cachedAt/asOf、状态/partial | PASS（同键渲染） | App:24065–24112；JS READY0/MISS、两个时间、stale/ERROR/REFRESHING、原因样本/截断/partial与转义通过；跨键问题由06 I-3单列 |
| 06 I-3：seq/snapshot与服务端cacheKey身份 | **FAIL** | App:24143–24149；seq/snapshot防晚响应有效，但键不同时错误回退旧值，复现确认V-1；现有测试:135–137反向锁定该行为 |
| 06 I-4：表单/保存/启动/验证合同保持 | PASS | snapshot构造函数未改选人语义；confirmManualExecution仍读取当前表单；JS141/full1805覆盖门禁/验证payload与缓存无值仍可启动 |
| 06 S-1/S-2：控制区/hint骨架、无新CSS/class | PASS（机器部分） | index:1568–1572、1792–1796；App:22651、24065；骨架与状态文案断言通过，16资源键同值，无styles.css改动；1200/600目测PENDING |
| A1/A3授权路径：ExpertSearchCursor.kt | PASS | 02新增基础游标；04仅增加nextPage(size)与保留无参合同，同一PIT/sort不重开；真实ES及所有cursor回归通过 |
| A1/A3授权路径：ExpertSearchCursorTest.kt | PASS | :98、131覆盖60→20→默认size及非法size；:157续期、:258锁、:301真实11001测试保留并执行 |
| A3授权路径：MailOpenTrackingPersistenceTest.kt | PASS | :193–231仅迁移cursor/strict-read夹具；:253–256仍断言sent1、openTrackingId77、executionId91、原正文；6测试通过 |
| 禁止范围/must-not-change | PASS | 无SMTP/IMAP实现、账号调度算法、mapping/DDL/migration、全局Jackson/HTTP配置、Redis、自动刷新、生产发布/补发变更；资格、历史、启动检查及事务回归通过 |
| Master A-1…A-4；各子计划共21人工场景 | **PENDING** | 合计25项；真实组合环境/捕获箱端到端、生产只读集合对照、宽窄屏目测及人工sign-off尚无人工来源结果，未代验 |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| **V-1** | **NEW** | 首次aggregate确认；05生成新模板指纹键，06错误分支却继续显示前键统计 |
| Child04 **F-1** | **RESOLVED** | 已有修复`5b4bc0a`；当前Manual:2947按RUNNING写totalCount0；新鲜full的BatchDemandExecutionTest26通过，逐事件断言仍在 |
| Prior aggregate findings / repairs | N/A | 本次为首次aggregate，无先前aggregate报告或修复轮次 |

### Findings

#### P1

**V-1：服务端缓存换键后刷新失败，UI仍显示旧键人数与时间。**

要求：06 I-3明确限制旧summary只能用于匹配的当前cacheKey；05 I-2及master X4/X5要求模板引用变化后的缓存隔离。

证据：[app.js:24146](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/resources/static/app.js:24146)。当ERROR响应的cacheKey与`oldEnvelope.cacheKey`不同，`matching=false`，下一行却复制`oldEnvelope`并加上ERROR状态继续渲染。

确定性触发：

1. 当前表单显示模板指纹A对应的100人及缓存时间。
2. 另一操作修改同模板引用片段正文；表单JSON不变，后端fingerprint/key变B。
3. 刷新B时ES失败，后端返回503、ERROR、key B、summary=null、cachedAt=null。
4. UI丢弃B envelope，继续展示A的100人和旧时间。

只读最小复现直接抽取生产函数执行，exit0输出：

```json
{
  "snapshotUnchanged": true,
  "serverKey": "fingerprint-B",
  "serverSummary": null,
  "displayedKey": "fingerprint-A",
  "status": "刷新失败，保留上次缓存"
}
```

HTML仍含“缓存目标估计100”和原缓存时间。后端key行为有新鲜Cache/Template/Controller测试佐证，不是凭字段名推断。

现有[缓存测试:135](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/test/js/batchRecipientStatisticsCache.test.js:135)还断言不同键503后继续显示100，解释了全量绿灯未捕获该缺陷。

影响：把不再适用于当前模板的统计呈现为可保留旧值；发送控制流没有因此读取缓存，但强制展示与隔离合同已违反。最小行为修复为共享错误分支和判别测试；部署资源键按仓库规则同步。

#### P2

N/A。

#### Observations

**O-1：批准计划/历史证据文档存在空白字符问题。** Whole-boundary `git diff --check` exit2；问题在计划、证据及brief文档，产品/测试差异检查exit0。非产品行为，不修改批准合同或历史证据，不纳入修复。

### Evidence Boundaries

- 没有缺失的主计划强制机器命令；MySQL/ES均真实运行、零跳过。
- full的23跳过如实记录；未把其它未启用集测说成通过。
- capture型运行测试使用生产服务/真实cursor状态机与受控transport/delivery seams；不能冒充真实ES+MySQL+SMTP捕获箱组合环境人工验收。
- 生产只读分类差异集合对照、25项人工清单及人工sign-off仍PENDING。它们没有被降格为Observation，也未由机器测试代签。
- 未执行普通focused命令的事实已明确；对应受影响类由新鲜full逐类计数覆盖。
- 未修改产品、测试、批准计划、fast-p证据、review证据、index或HEAD；唯一仓库写入为指定repair artifact。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| children/01 terminal RECORD_ONLY | I-1 / X1 | N/A；无豁免 | 原记录N/A；当前wire与资格链重新验证 |
| children/02 terminal RECORD_ONLY | I-2 / X2 | N/A；无豁免 | 原记录N/A；当前真实MySQL/ES与HTTP超时重新验证 |
| children/03 terminal RECORD_ONLY | I-3 / X3 | N/A；无豁免 | 原记录N/A；当前两reader、历史和v2重新验证 |
| children/04 terminal RECORD_ONLY | I-2/I-3 / X2/X3 | N/A；F-1另行复核并关闭 | terminal N/A；当前26 capture及full证明F-1保持解决 |
| children/05 terminal RECORD_ONLY | X4/X5 | N/A；无豁免 | 后端完整指纹/严格统计重验；跨06调查产生V-1 |
| children/06 terminal RECORD_ONLY | X5 / 06 I-3 | N/A；不沿用LIGHT_PASS | 当前production函数复现推翻跨键回退合规结论，V-1 NEW |
| handoff Deferred Human Review | Master A-1…A-4及子清单 | PENDING | 真实组合E2E、生产集合对照、视觉验收未执行，不属于永久豁免 |

### Verification Next Action

**FAIL + INITIAL → repair-p**。仅规划V-1；不执行修复。

## Repair Planning Result: DRAFT_READY

Baseline plan: 上述精确 master，批准修订A1–A4。
Verification result: **FAIL / INITIAL**。
Repair artifact:

[repair.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/fix/batch-demand-master/repair.md)

SHA-256：

```text
d14f5d2fd10e2d9a73bb29d9d1a80836fd580e38609f4b878681ecf24ef58169
```

### Included Findings

- **V-1 / REPAIRABLE**。一个根因、一个任务R-1。
- 精确授权三文件：
  - `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/resources/static/app.js`
  - `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/test/js/batchRecipientStatisticsCache.test.js`
  - `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/resources/static/index.html`
- index仅同步既有16处资源键，依据`CLAUDE.md:82`的K-frontend-cache-key-triad；不改DOM、CSS或资源顺序。
- 新旧key判别回归覆盖editor/manual；保留同键503、纯网络错误、seq/snapshot、0/MISS、诊断与发送合同。
- 已包含绝对路径one-approval handoff、准确pre-code SHA、本地产品提交主题、独立handoff证据提交主题，以及用户要求时同任务自动aggregate回审。

### Excluded Findings

- Child04 F-1：已解决。
- O-1：非行为文档卫生问题。
- 人工验收/生产只读集合对照：待验收，不作为产品修复任务。
- 无BLOCKED项被当作可修复缺陷。

### Required Human Decision / Next Action

To approve and execute this repair, send:

```text
$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/fix/batch-demand-master/repair.md
After READY_FOR_VERIFICATION, resume $review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/fast/batch-demand-master/human-review-handoff.md using the committed repair-execution.md, in the same task. Do not ask me to relay executor metadata.
```

修复执行需要这一次人工授权：[repair-p/SKILL.md](/Users/lukai/.agents/skills/repair-p/SKILL.md)要求“Execution is prohibited until the human explicitly approves this plan.” 本轮授权范围为独立核验与修复规划。

**No implementation was performed. No product code was modified.**

Aggregate routing: **REPAIR_PLAN_READY**。


## Epoch 2 — 2026-10-23T09:44:09.378649+08:00

- Governing master identity: commit1ba7003f2719165e4d420132255d071102cd6c3e; sha256 c8922f6c7ab011303844f3c8b7dae181eb99eb22d47bdc7db2662eec2f7c16d1
- Master identity state: AMENDMENT_RECORDED; A1–A4 exact approvals retained in fast ledger and Epoch 1
- Repair evidence mode: DURABLE_HANDOFF
- Reviewer: /root/aggregate_review_epoch2 (fresh fork_turns=none)
- Boundary: d403caeb5dcc4810067293c10600ee57f94e276b..147b3eafcdf62b70bb811d2068227277ec999a4a
- Evidence parent: a2a9a854097628d380108844cee861d37f11aacb

## Epoch 2 — 2026-10-23（修复后独立总体验证）

## Verification Result: **PASS**

Plan: [batch-demand-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-master.md)
Requested phase: `aggregate/master`
Implementation boundary: `d403caeb5dcc4810067293c10600ee57f94e276b..147b3eafcdf62b70bb811d2068227277ec999a4a`
Reviewer: `/root/aggregate_review_epoch2`；修复提交后创建，fresh/no inherited implementation context，与全部已记录子段角色及上一 aggregate reviewer 不同。
Convergence: **PROGRESSING**
Manual acceptance: **PENDING**；master 4项＋children 21项，共25项，未代验、未签署。

### Contract / Evidence Identity

- Repository common root: `/Users/lukai/IdeaProjects/weibo-talent-introduction`。
- 本报告全部仓库命令的唯一 cwd: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master`；branch `fast/batch-demand-master`；worktree resolution **EXPLICIT**。`git worktree list`、branch、HEAD 已独立确认，未采用其他工作树的 review ledger。
- Master base: `d403caeb5dcc4810067293c10600ee57f94e276b`；prior code `37a76a66a40e1614bec7c1e92324c7beee7c0be0`；repaired final_code_head `147b3eafcdf62b70bb811d2068227277ec999a4a`；本轮 evidence_head `a2a9a854097628d380108844cee861d37f11aacb`。
- Governing master: commit `1ba7003f2719165e4d420132255d071102cd6c3e`；实读 SHA-256 `c8922f6c7ab011303844f3c8b7dae181eb99eb22d47bdc7db2662eec2f7c16d1`。
- Master identity state: **AMENDMENT_RECORDED**；先前 invoked copy hash `b0258287e77dcea06f081ee80aea1f38cd7acedfc6a6d9066d76efa6eb5c6e6a`。本次精确交接给出 governing 身份；已读 `git diff f684da0a316b8cfb560fe7ebb0a8817d8a2594f2 1ba7003f2719165e4d420132255d071102cd6c3e -- docs/plans/2026-10-22/batch-demand-master.md docs/plans/2026-10-22/batch-demand-04-execution.md`。
- A1/A2: `6c020573bc44e7fbe70208337e5e96b867e641f9`，I-2/I-4；为保持逐次按需公式授权 cursor实现/测试、可变size、无参兼容及回归。Approval: `HUMAN:2026-10-22 selected 扩大两文件授权，保持按需公式`。
- A3/A4: `1ba7003f2719165e4d420132255d071102cd6c3e`，I-4；授权第10个追踪测试、嵌套材料夹具迁移并保留断言，更新Java11验证环境；不改时区产品代码。Approval: `HUMAN:2026-10-22 selected 批准第10文件并继续`。
- Ordered children: `docs/plans/2026-10-22/batch-demand-01-classification.md` → `batch-demand-02-cursors.md` → `batch-demand-03-progress.md` → `batch-demand-04-execution.md` → `batch-demand-05-cache.md` → `batch-demand-06-ui.md`。01/02/03/05/06当前字节逐一等于批准种子 `f684da0a316b8cfb560fe7ebb0a8817d8a2594f2`；04当前字节等于批准修订 `1ba7003f2719165e4d420132255d071102cd6c3e`。
- Fast ledger: `docs/plans/fast/batch-demand-master/ledger.md`，SHA-256 `c05d095c4b4667cede5515db5d51342ac08454ec92aaf49e6ff465961cb66dfc`；handoff同目录 `human-review-handoff.md`，SHA-256 `62124b01ff60edd436f3620fb47d7335f566ebe5b1031ad0360786ce8ebd14c1`。全部24个child evidence路径存在；child结论仅作证据输入，没有替代独立矩阵。
- Prior aggregate: `docs/plans/review/batch-demand-master/machine-verification.md` epoch1：FAIL/INITIAL，V-1；保留其finding ID与child04 F-1/O-1 lineage。
- Post-repair evidence mode: **DURABLE_HANDOFF**。`docs/plans/review/batch-demand-master/repair-execution.md`实读 SHA-256 `6087a9b35d3914dd26a61ed121eaedd04e2e46271e1bb6af695df1d94d2f6c67`；记录人类明确 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/fix/batch-demand-master/repair.md` 调用及READY_FOR_VERIFICATION。
- Executor稳定ID: **UNAVAILABLE**；handoff说明为top-level inline executor、未派executor subagent。此可选元数据不构成blocker；本轮全部必需命令独立重跑。
- Repair artifact: [repair.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/fix/batch-demand-master/repair.md)，SHA-256 `d14f5d2fd10e2d9a73bb29d9d1a80836fd580e38609f4b878681ecf24ef58169`，未改写。
- Repair累计产品/测试delta `37a76a6..147b3ea -- src`恰好为 `src/main/resources/static/app.js`、`src/main/resources/static/index.html`、`src/test/js/batchRecipientStatisticsCache.test.js`，全部获该repair精确授权。单独产品提交147b3ea也只含此三文件；中间docs evidence提交未混作产品变化。

### Commands

全部测试在修复后当前最终代码上新鲜执行；Maven严格串行。保留工作树位于当前可写根之外，测试/本地Docker命令均用`require_escalated`获得test-only执行授权；无自动审批拒绝。

环境：Java `11.0.32.1`（Zulu11.90+205-CA），Maven `3.9.11`，Node `v25.7.0`，Docker server `29.8.2`。以下Maven命令均显式指定该Java11；默认shell的Java25没有用于测试。

| Exact command / check | Result | Fresh evidence |
|---|---|---|
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test` | **PASS**, exit0 | **5478 tests / 0 failures / 0 errors / 23 skips**，即5455未跳过；BUILD SUCCESS，08:43；绑定Node **1807/1807、0skip**，两项绑定JS语法检查成功。日志 `/private/tmp/batch-epoch2-mvn-full.log` |
| `node --test src/test/js/*.test.js` | **PASS**, exit0 | 独立运行 **1807 tests、334 suites、1807pass、0fail/0cancel/0skip**；日志 `/private/tmp/batch-epoch2-node-full.log` |
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=ExpertContactCursorRepositoryIT -DmysqlIt=true` | **PASS**, exit0 | **3 tests / 0 failures / 0 errors / 0 skips**；真实MySQL8.0.36容器，501同秒NEW读500＋1，状态更新后未漏尾页、新高id502排除、readWrites0/SMTP0；绑定Node1807pass/0skip。日志 `/private/tmp/batch-epoch2-mysql-it.log` |
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=ExpertSearchCursorTest -DesIt=true` | **PASS**, exit0 | **10 tests / 0 failures / 0 errors / 0 skips**；真实ES7.14.2遍历11001文档、24页/24search、count0；早停独立游标3search/释放2PIT/openContexts0/SMTP0；绑定Node1807pass/0skip；BUILD SUCCESS，02:47。日志 `/private/tmp/batch-epoch2-es-it.log` |
| `node --check src/main/resources/static/app.js` | **PASS**, exit0 | 修复脚本语法检查，无输出 |
| `node --test src/test/js/batchRecipientStatisticsCache.test.js` | **PASS**, exit0 | **13/13pass、0fail/0skip**；editor/manual同表单A→B/503清值，同键503/网络失败保留、竞态、0/MISS等；日志 `/private/tmp/batch-epoch2-node-focused.log` |
| `python3 /private/tmp/batch-epoch2-mysql-explain.py` | **PASS（仅schema/shape证据）**, exit0 | 在本次新建MySQL容器`a886f6adb005`的`cursor_test`上，只读确认8.0.36、V158、实际表索引，并对ContactRepo:189–194首/续页SQL执行EXPLAIN FORMAT=JSON；两者`ref/uk_campaign_expert`、`campaign_id`、`using_filesort=true`。日志 `/private/tmp/batch-epoch2-mysql-explain.log` |
| Java11临时`BatchEpoch2Explain`编译/执行launcher（精确命令见下） | **PASS**, exit0 | 独立MySQL8.0.36原样迁移至V158，**提交22503行**：本campaign NEW11001＋WAITING_REPLY11001，另一campaign NEW501；首/续页均返回500，ID范围11001→10502、10501→10002。实际EXPLAIN ANALYZE两页扫描22002行，过滤11001/10501，filesort后LIMIT500；本机单次实际约33.9/34.6ms，非SLA。日志 `/private/tmp/batch-epoch2-mysql-seeded-explain.log` |
| `git diff --check d403caeb5dcc4810067293c10600ee57f94e276b 147b3eafcdf62b70bb811d2068227277ec999a4a -- src` | **PASS**, exit0 | 累计产品/测试差异无空白问题 |
| `git diff --check 37a76a66a40e1614bec7c1e92324c7beee7c0be0 147b3eafcdf62b70bb811d2068227277ec999a4a -- src` | **PASS**, exit0 | 修复三文件差异无空白问题 |
| `git diff --check d403caeb5dcc4810067293c10600ee57f94e276b 147b3eafcdf62b70bb811d2068227277ec999a4a` | 非阻断Observation，exit2 | 仍仅批准计划/历史证据的尾空行与尾空白，O-1；日志 `/private/tmp/batch-epoch2-diffcheck.log` |
| 精确清单/commit scope与批准字节对照、`git worktree list`、`git branch --show-current`、`git rev-parse HEAD`、hash检查 | **PASS**, exit0 | 逐段actual/authorized **5/6、7/7、8/9、10/10、8/8、6/6**；累计33改动/34授权、清单外0；唯一未改授权文件为`src/test/js/batchManualExecutionLog.test.js`；16资源键全为`20261023-batch-statistics-key-fix` |

补充有数据EXPLAIN的精确launcher（源文件仅为`/private/tmp/BatchEpoch2Explain.java`；classpath从既有Surefire XML提取到临时文件，未改测试源）：

```bash
python3 - <<'PY' > /private/tmp/batch-epoch2-mysql-seeded-explain.log 2>&1
from pathlib import Path
import subprocess
cp=Path('/private/tmp/batch-epoch2-test-classpath.txt').read_text()
jdk='/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/'
subprocess.run([jdk+'javac','-cp',cp,'/private/tmp/BatchEpoch2Explain.java'],check=True)
subprocess.run([jdk+'java','-cp','/private/tmp:'+cp,'BatchEpoch2Explain'],check=True)
PY
```

该临时测试应用现有迁移文件，未新增或编辑DDL/索引。夹具提交后同一连接验证COUNT=22503，再执行与ContactRepo:189–194相同条件的首/续页SELECT、EXPLAIN FORMAT=JSON及EXPLAIN ANALYZE；finally销毁容器。首游标NULL，续游标`2026-10-22 12:00:00/id10502`、upperId22002、limit500；同秒尾键、非NEW状态及另一campaign均有真实数据。空表observer本身不足以承担性能结论，已由此补证替代。

全量的23跳过保留原貌：AuthFlowIntegrationTest、InboundMailProcessingRepositoryTest、CalendarAttachmentIntegrationTest、MailboxConversationControllerTest、MailReplyDraftSendIntegrationTest、MeetingCalendarSendIntegrationTest、DiscoveryPersonRepositoryTest、DiscoveryVerificationIntegrationTest、EuropePmcDataSourceTest、ExpertMeetingRecordIntegrationTest、RagFactAdminServiceTest、RagKnowledgeBaseTest、RagLetterComposerTest、ReplyIdentityDefaultsIntegrationTest、OperatorActionLogRepositoryTest、FlywayMigrationIntegrationTest、MeetingCalendarControllerMysqlTest、MeetingCalendarServiceMysqlTest、DiscoveryPersonOutreachGuardServiceTest、ExpertSearchCursorTest各1；ExpertMeetingVideoStreamingIntegrationTest为3。未把它们计为通过。主计划要求的MySQL IT另行显式执行；ES默认跳过项以本轮显式esIt命令补验，不能由默认suite绿灯替代。

普通子段focused Maven/JS子集未重复执行；其全部受影响类由本轮full实际覆盖，repair focused另行执行。不存在把未运行命令声称已执行的情况。

| Affected JVM class | Tests / Failures / Errors / Skips（fresh full） |
|---|---:|
| ExpertSearchServiceTest | 96 / 0 / 0 / 0 |
| ExpertDiscoveryServiceTest | 194 / 0 / 0 / 0 |
| ClassificationWireFormatTest | 2 / 0 / 0 / 0 |
| ExpertIndexWriterServiceTest | 51 / 0 / 0 / 0 |
| ExpertClassificationServiceTest | 29 / 0 / 0 / 0 |
| OperatorStatusWriteSeamGuardTest | 1 / 0 / 0 / 0 |
| ExpertSearchCursorTest | 10 / 0 / 0 / 1；显式结果见上表 |
| BatchSendExecutionDetailTest | 38 / 0 / 0 / 0 |
| ManualInitialOutreachServiceTest（含嵌套材料用例） | 241 / 0 / 0 / 0 |
| OutreachTargetIteratorTest | 28 / 0 / 0 / 0 |
| BatchSendTaskRuntimeIntegrationTest | 23 / 0 / 0 / 0 |
| BatchDemandExecutionTest | 26 / 0 / 0 / 0 |
| MailOpenTrackingPersistenceTest | 6 / 0 / 0 / 0 |
| BatchRecipientStatisticsCacheTest | 18 / 0 / 0 / 0 |
| MailComposeTemplateServiceTest | 73 / 0 / 0 / 0 |
| BatchSendConfigControllerTest | 9 / 0 / 0 / 0 |

已读完整累计产品/测试diff，未只看repair差异。新鲜HTTP实测输出：`CURSOR_SMOKE localHTTP readTimeoutMs=20028 releasedPits=1 SMTP=0`。

### Contract Matrix

下表为一个总合同矩阵；ID的Master/01…06即上述精确批准计划的同名I/S条款，X1…X5来自Master现状审计。全部机器条款强制；视觉/端到端条款保持PENDING。

源码简称（均为唯一保留工作树内的绝对路径）：[Search](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt)；[Cursor](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchCursor.kt)；[Discovery](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt)；[ContactRepo](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt)；[Manual](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt)；[Iterator](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt)；[Cache](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientStatisticsCache.kt)；[Models](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt)；[Controller](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt)；[Templates](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt)；[App](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/resources/static/app.js)；[Index](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/resources/static/index.html)。

| ID / Source | Requirement | Mandatory | Verdict | Required evidence / actual proof |
|---|---|---:|---|---|
| Master I-1 | 修日期而不放宽资格；其余准入、地区、退订、历史规则保留 | 是 | PASS | Discovery:3275只换classificationNode；Search:595严格数组分支；真实converter回放2、Search96、分类29、writer51、Discovery194及资格回归通过 |
| Master I-2 | 统计仅参考；两类执行按当前额度取人，允许过滤至耗尽 | 是 | PASS | Manual:325/1144 quota无统计依赖；DemandSource:1849；26个实际run/capture用例与缓存18通过；无缓存依赖注入发送 |
| Master I-3 | 旧target/remaining保留，v2计划/实际/配额独立 | 是 | PASS | Models:129/405；Manual:2898/2941；Controller:377/399/481/506；detail38与JS新旧日志行为通过 |
| Master I-4 / scope | 01→06独立门禁；每段≤10文件/2子系统、每共享store仅1新字段 | 是 | PASS | 6段批准字节、实际commit顺序与24证据路径核对；逐段5/6、7/7、8/9、10/10、8/8、6/6；累计34授权/33改动、外0；两store仅execution |
| Master X1 | writer→converter→ES形状→Search→类型准入闭环 | 是 | PASS | Discovery:3275→真实RestTemplate converter→Search:589→RecipientScope；wire2跨三层包含合法旧数组、非法分类仍拒绝，无读回刷 |
| Master X2 | NEW/ES更新→稳定枚举→最新事实复读→安全投递/事务 | 是 | PASS | ContactRepo:188、Cursor:41、Manual:1903/1987/2085/1478/1609；真实MySQL501及显式ES深分页，capture26；原PREPARED、guard、recordSuccess/Failure路径保持 |
| Master X3 | progress/result同协议→两个reader→历史/v2 UI | 是 | PASS | Manual:2941/2947与2898；Controller:481/506/539；App:25096/25104/25135；detail38、demand26及全量Node通过 |
| Master X4 | 模板/引用变更→完整指纹→缓存隔离 | 是 | PASS | Templates:216–259及375–407；Cache:36/61/100；App:24148–24153新键ERROR清旧值；模板73、cache18、repair JS13通过；V-1 RESOLVED |
| Master X5 | 缓存响应→两表单；任意缓存状态不控制发送 | 是 | PASS | App:24136/24143–24154与24065；Manual无cache依赖；editor/manual A→B/503、MISS/0/stale及实际启动快照回归通过 |
| 01 I-1 | 5/6/7整数数组及三种旧格式；非法值严格拒绝 | 是 | PASS | Search:589–611；96测试覆盖三层、纳秒、闰日、错长、浮点/溢出/非法日期；不加当前时间兜底 |
| 01 I-2 | 普通doc与discovered params.doc统一字符串新写 | 是 | PASS | Discovery:3275与wire2实际HTTP converter；字符串格式与分类其余属性相等 |
| 01 I-3 | 身份CAS、HEAD404、null事实、三层与损坏分类边界保持 | 是 | PASS | Discovery只一处产品赋值变化；wire2、Discovery194涵盖不存在不建层、冲突noop、无upsert、不写身份/空事实 |
| 02 I-1 | 每层PIT、完整raw sort/search_after、≤500、无count/from | 是 | PASS | Cursor:41–80；Search:749–780；显式真实ES11001/24页以及sort数值类型与过滤空页回归 |
| 02 I-2 | 5m/60s续期、同锁、3s/20s超时、明确失败 | 是 | PASS | Cursor:86/103/117；Search:47；六次模拟续期、失败不重开/不伪空；新鲜HTTP20028ms/释放PIT1 |
| 02 I-3 | 按需开层、先关后开、幂等close/销毁、独立游标 | 是 | PASS | Cursor:54/137/146；Search:782；锁/取消/关闭测试及真实openContexts0；无close预读 |
| 02 I-4 | NEW参数绑定只读keyset/maxId/严格尾键，旧接口保留 | 是 | PASS | ContactRepo:38/155–195；真实MySQL3/0skip：同秒500+1、status更新、高id隔离、只读与非法limit/半游标 |
| Master:88 EXPLAIN | 测试环境检查NEW查询计划；不擅加索引/DDL | 是 | PASS | 本轮独立V158 MySQL提交22503行、含11001 NEW，首/续页EXPLAIN ANALYZE exit0；实际读22002→过滤11001/10501→sort/返回500，分页ID正确。满足“测试环境EXPLAIN”证据；无生产SLA/代表性分布结论，不把空表observer当充分证据，无新增DDL/索引 |
| 03 I-1 | 两JSON各只新增可选version=2 execution，旧记录不补造 | 是 | PASS | Models:129；Manual:3102 NON_NULL及2941；detail38精确顶层/嵌套key、未知版本/无值测试通过 |
| 03 I-2 | quota=max(planned-success,0)，actual计数，RUNNING total0，Long计划 | 是 | PASS | Models:405–492；Manual:1835–1843/2947；detail的2.5e9 Long、demand26每事件断言；F-1保持解决 |
| 03 I-3 | summary/detail双读取、progress回退、坏/未知协议仅忽略execution | 是 | PASS | Controller:377/399/481/506/539；38用例涵盖两detail入口、列表不逐条读progress、历史1757/1710与坏JSON |
| 03 S-1 | v2七卡顺序/既有class/escape；v1不变，无CSS新增 | 是 | PASS（机器） | App:25104；batchDemandProgress实际函数测试断言顺序、DOM、颜色class、v1六卡与资源键；宽窄屏目测PENDING |
| 03 S-2 | 复用warning/status/hidden，v2双等式完整性，取消按钮保持 | 是 | PASS（机器） | App:25135/25183；错误文案、正常hidden、新旧降级、live取消/timeline/原因测试通过；CSS无diff |
| 04 I-1 | 成功额度/有效轮数/实时容量，达到额度先停，禁止终止后预读 | 是 | PASS | Manual:304/325/361及1122/1144/1179；capture两类型30成功即停、5fail+5skip补到30、0容量不读、无最终多页/sleep |
| 04 I-2 | NEW先、逐次min(500,max(1,2×剩余额度))、空页继续/跨页去重 | 是 | PASS | Manual:1843/1917/1987；Iterator:106；两类型实际60→20、NEW501、同ORCID多doc不挤掉其他人、11001与同邮箱/人物回归 |
| 04 I-3 | 页前strict mget、发前再次strict复核；地址/身份变化跳过，原护栏保留 | 是 | PASS | Manual:1903/2085/1188/1478、材料370/437；scope/准入/preflight、退订/绑定、验证、SENT历史、cancel、PREPARED、guard、事务链逐点核查；材料仍contact.expertEmail |
| 04 I-4 | 真实outcomes；读故障0成功FAILED/有成功PARTIAL；全路径metadata | 是 | PASS | Manual:541/1805/2875/2941；capture26含0/10成功读失败、取消无虚构skip、每事件running0/final actual及metadata与最终一致；旧accumulator回归 |
| 04 I-5 | 首封/材料对称，成功才占额度，finally关闭，显式材料统计纯读 | 是 | PASS | Manual:544/1808/2612；26 capture、Manual241覆盖轮数、容量、过滤、取消、耗尽与11001材料统计零业务写入；所有cursor关闭 |
| A1/A3 file: ExpertSearchCursor.kt | I-2公式与I-4范围授权：同推进PIT可变size、无参兼容 | 是 | PASS | Cursor:39/41；02基础游标＋04仅扩每次size，续期/锁/sort/close保持；真实ES与10个cursor测试验证 |
| A1/A3 file: ExpertSearchCursorTest.kt | 修订授权的60→20、无参默认、非法size与原回归 | 是 | PASS | 测试98/131/157/258/301：同PIT、invalid0/501、续期/锁/11001，显式esIt无跳过执行 |
| A3 file: MailOpenTrackingPersistenceTest.kt | 第10文件只迁移读取夹具，追踪断言不得放宽 | 是 | PASS | 测试193–231替换cursor/strict-read夹具；253–256的sent1、openTrackingId77、executionId91/正文断言保留；fresh6/6 |
| 05 I-1 | 缓存仅Controller statistics调用，不存白名单，统计零发送/verify/业务写入 | 是 | PASS | Cache:33调用只读seams；Controller:112；Manual:624/2612；cache18、实际cache/controller/strict组合smoke与写入/供应商spy通过 |
| 05 I-2 | effective scope规范key与完整模板指纹；旧versionToken不变 | 是 | PASS | Cache:100–118；Templates:216–259/375–407；集合规范、gate/验证/地区/类型/hasFields、片段/变体/QA/启用/default编辑、旧token回归通过 |
| 05 I-3 | LRU64、2不同key并发、同key单飞、24h stale、失败原子/重启MISS | 是 | PASS | Cache:41–98；18测试涵盖同key20次1算、第三key拒绝、65→64LRU、失败保留cachedAt、24h不重算与重启MISS |
| 05 I-4 | MISS/READY/REFRESHING/ERROR，cacheOnly0扫描、冲突400、错误503 | 是 | PASS | Cache:33–98/129与Controller:112–124；MockMvc真实cache测试9及cache18，不把错误伪装READY0，lookup不等待扫描 |
| 05 I-5 | 全量目标与20000诊断分离；不完整读/模板变更不得发布 | 是 | PASS | Manual:673/694/2643；Cache:60–65；30001 total与20000 partial、NEW跨页去重、null/失败shard/timeout及模板中途变化回归 |
| 06 I-1 | 打开/改条件300ms单cacheOnly，显式单refresh，不自动重算 | 是 | PASS | App:24047–24063/24115–24138；全量Node实际函数涵盖两面板debounce、当前gate单请求、MISS/ERROR无重算、按钮禁用/恢复 |
| 06 I-2 | MISS不冒0；READY0、cachedAt/asOf、stale/error/refreshing/partial区分 | 是 | PASS | App:24065–24112；freshNode的零/缺失、两个时间、旧值状态、诊断样本/重叠/截断/partial/XSS行为断言 |
| 06 I-3 | 独立seq/snapshot与cacheKey、close/reset失效、动态文本安全、缓存不禁发 | 是 | PASS | App:24132–24154及reset；同表单新key ERROR清旧summary/cachedAt，editor/manual repair13通过；同键与无key网络保留、晚成功/失败、关闭和XSS回归；V-1 RESOLVED |
| 06 I-4 | 表单字段/保存/启动/token/验证合同保持；只移除off/on差量 | 是 | PASS | snapshot builders未改选人语义；confirmManualExecution仍直接构造当前表单；全量Node验证gate/验证payload、缓存无值启动及旧日志回归 |
| 06 S-1 | 两控制区原DOM/class/文案、按钮恢复，无新CSS/inline style | 是 | PASS（机器） | Index:1568–1572/1792–1796；App:22651；实际DOM骨架与状态文案测试、16同值资源键；1200/600视觉PENDING |
| 06 S-2 | 既有hint/warning、缓存参考/诊断时间/partial/样本骨架 | 是 | PASS（机器） | App:24083–24112；原因数未相加冒充排除数、缺诊断不补0、样本转义与partial文案；styles.css无变化；目测PENDING |
| Repair R-1 / V-1 | 新服务端键失败清旧人数/时间，最小三文件修复与资源交付 | 是 | PASS | App:24148–24152；JS测试127–166新鲜13/13；Index16个token变化且不改资源顺序/DOM；repair累计src恰好3授权文件 |
| Master non-goals / must-not-change | 无IMAP/SMTP协议、账号算法、schema/migration/mapping/Redis/全库定时刷新/生产发布补发修改 | 是 | PASS | 完整33文件diff与runtime链核对；无config/DDL/CSS/调度/SMTP/IMAP实现变更；原资格、历史、模板启动、事务与幂等回归通过 |
| Master required commands | Java11 full、独立Node、显式真实MySQL/ES，不以跳过作通过 | 是 | PASS | Commands表的新鲜退出码/计数与隔离容器证据；所有必需命令按当前最终代码执行 |
| Master A-1…A-4 | 分类资格、双类型11001/30额度、缓存模板生命周期、历史/取消/UI整体人工验收 | 是 | PENDING | 4项；真实ES+MySQL+SMTP捕获箱组合与人工结果尚未提供，未执行/勾选 |
| 01 A-1…A-3 | 三层旧数组、补全新写、非法/地区/退订人工场景与生产前只读集合对照 | 是 | PENDING | 3项；不把机器wire回放当生产分类差异集合证明；无生产操作 |
| 02 A-1…A-3 | 人工长名单分页、NEW状态变化、续期/故障/释放 | 是 | PENDING | 3项；真实隔离IT提供机器证据，不代人工签署 |
| 03 A-1…A-3 | 历史1757、运行→结果、1200/600卡片目测 | 是 | PENDING | 3项；机器JSON/JS测试通过，人工结果未提供 |
| 04 A-1…A-4 | 双类型捕获箱足额/幂等、补人容量取消、发前变化故障、材料只读统计 | 是 | PENDING | 4项；capture seams测试不冒充真实组合E2E |
| 05 A-1…A-4 | 人工首次/复用/刷新、引用编辑、失败陈旧重启、30001并发 | 是 | PENDING | 4项；自动单飞/LRU/strict测试不代人工验收 |
| 06 A-1…A-4 | 人工网络与刷新、竞态异常、门禁诊断启动、宽窄屏样式安全 | 是 | PENDING | 4项；上一executor fixture-browser记录仅作辅助，未重解释为人工结果 |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| **V-1**（epoch1 P1） | **RESOLVED** | 新分支App:24148–24152将不同服务端key的ERROR切为新key/null summary/null cachedAt；真实生产函数的editor/manual判别回归JS:142–166新鲜通过，13/13及全量1807通过；同键/网络保留语义保持 |
| **Child04 F-1** | **RESOLVED**（保持解决） | Manual:2947 RUNNING totalCount0，终态actual；fresh BatchDemandExecutionTest26逐事件断言及detail38通过 |
| **O-1**（epoch1 Observation） | **PERSISTENT**（非阻断） | whole-boundary diff-check仍exit2，仅批准计划/历史证据的空白；src累计与repair差异均exit0，无产品行为变化 |
| New / Regression | N/A | 本轮未确认新P1、强制P2或回归 |

### Findings

#### P1

N/A；V-1已解决，无开放强制行为缺陷。

#### P2

N/A。

#### Observations

- **O-1**：批准计划/历史证据文档空白问题保持。未修改合同/历史证据，未纳入产品修复。
- EXPLAIN：初次observer为事务外空表，仅证明schema/query shape，单独不足以验证性能；已补独立容器、原样V158、提交22503行和11001 NEW的实际执行分析。两页实际各扫描22002行后filesort，不能把应用层有界500条等同数据库物理只读500行。估计rows1未反映刚批量插入的基数，采用actual rows与已提交COUNT取证；无新增ANALYZE TABLE/索引/DDL。33.9/34.6ms为一次本机测试观察，不证明生产负载、分布、延迟或索引充分性。主计划未设生产SLA；测试环境EXPLAIN必需项已满足。

### Evidence Boundaries

- 没有缺失的主计划必需机器证据；full、独立Node、显式真实MySQL/ES与测试环境有数据EXPLAIN均已新鲜完成，显式IT零跳过。
- full的23跳过已逐项记录；其他opt-in场景未声称通过。真实MySQL/ES与full普通测试的证据边界分开记录。
- 实际服务/真实cursor状态机＋受控delivery/transport的26 capture测试、MockMvc与converter回放，不能代替真实ES+MySQL+SMTP捕获箱组合人工验收。
- 生产只读修前/修后分类差异集合、25项人工场景、视觉实值验收与人工sign-off仍PENDING；没有降格为Observation或由机器代签。
- EXPLAIN只用本次隔离测试容器，未查询生产数据、未打印生产凭据；没有新增索引/DDL。
- 本轮开始/结束HEAD均为`a2a9a854097628d380108844cee861d37f11aacb`；唯一已有tracked dirty项为controller正在维护的review ledger。验证者未改该ledger，也未stage/commit、切分支、修改HEAD。构建仅产生临时target输出；补充脚本/日志/本返回报告仅写`/private/tmp`。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| children/01 terminal RECORD_ONLY | I-1 / X1 | N/A，无豁免 | 原记录N/A；wire/分类/资格链本轮重新检查与执行 |
| children/02 terminal RECORD_ONLY | I-2 / X2 | N/A，无豁免 | 原记录N/A；真实MySQL/ES、HTTP超时及新增测试环境EXPLAIN重新取证 |
| children/03 terminal RECORD_ONLY | I-3 / X3 | N/A，无豁免 | 原记录N/A；两reader、历史1757/v2、progress/result重新验证 |
| children/04 terminal RECORD_ONLY | I-2/I-3 / X2/X3 | N/A；F-1保持RESOLVED | terminal N/A；fresh26 capture/full与Manual:2947重新证明，不沿用旧轻验结论 |
| children/05 terminal RECORD_ONLY | X4/X5 | N/A，无豁免 | 原记录N/A；完整模板指纹、strict统计/cache/controller链本轮复核 |
| children/06 terminal RECORD_ONLY | X5 / 06 I-3 | N/A；V-1已解决 | 原terminal N/A不作为PASS证明；本轮repair13与全量1807直接证明新键错误隔离 |
| handoff Deferred Human Review | Master A-1…A-4＋children21项 | PENDING | 真实组合E2E、生产集合对照、视觉/人工sign-off仍需人类结果；非永久豁免 |

### Next Action

**PASS + PROGRESSING → 进入当前修复代码边界的人工验收。** 由controller将完整本报告追加为machine epoch2，生成/维护25项PENDING清单，等待人类结果与对精确governing master身份及产品SHA `147b3eafcdf62b70bb811d2068227277ec999a4a` 的sign-off；机器PASS不代签、不授权合并/发布。

Repair planning: **N/A**。本轮未调用repair-p、未创建新轮次、未改原repair.md。

**No product code was modified.**
