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
