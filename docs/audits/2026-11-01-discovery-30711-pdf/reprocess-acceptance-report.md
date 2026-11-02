# #30711 paper reprocess — child 06 acceptance evidence

生成时间：2026-11-02。执行状态：**PLAN_CONFLICT**（四份授权交付已完成；一个跨子计划的既有测试 fixture 仍红，详见第 6 节）。本文件是执行证据，不是 PASS、不是独立复核、不是发布批准。

- Plan：`docs/plans/2026-11-01/paper-reprocess-06-verification.md`
- Plan SHA-256：`ea13bb67c166e8cb541cc068d1367432497ed0fb3f303b61c3ba52d2f4493791`
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`
- Branch：`fast/paper-reprocess-master`
- Plan base：`46bcb8a3875a6957ed18494200d158de09dd0f38`
- 元数据 HEAD（执行前）：`d7f6ab4`
- 实施边界：四个授权文件；产品提交 subject `feat(fast-p): implement 06`，SHA 记录在 `docs/plans/fast/paper-reprocess-master/children/06/execution.md`。
- 未执行：生产写入、Apollo 请求、发布/推送/合并、原文删除、review-fast-p / review-p / verify-p / repair-p。

## 0. 结论摘要（真实计数）

| 项 | 实测 | 判定 |
|---|---:|---|
| 00 冻结组合 | 337（333 保守组 / 325 历史机会数） | 逐条核对，不是人数 |
| 原文记录 / 组合 | 244 / 337；本轮 `SOURCE_MISSING=0` | 244 份本地原文均被读取并校验 |
| 旧基线条目 | 330 REPRODUCED / 1 ALREADY_RECOVERED / 6 NOT_REPRODUCED | 00 历史口径 |
| 当前规则正确绑定 | 51 条确认组合（基线 1 条 + 新增 50 条） | 0 条新错绑、0 条回归 |
| 仍无法绑定 | 286 条确认组合 | 逐条原因见 1.4；不得写成“325 全修复” |
| 12 目标业务任务（第一次） | new 2 / repair 2 / admitted 2 / conflict 2 / protected 2 / target error 2 | 12 条逐条见 2.1 |
| 同任务重试 | 成功项 0 重做；new/repair/admitted 仍 2/2/2 | 见 3.1 |
| 第二个任务 | new 0 / repair 0 / admitted 0；already-admitted 2 | 见 3.2 |
| 取消 | CANCELLED；已提交 NEW=1 保留 | 见 3.3 |
| 写故障恢复 | 失败目标恢复后 new=2；成功目标收据逐字不变 | 见 3.4 |
| 迁移 | 空库→V173 通过；V170→V173 通过 | 48 个迁移测试全绿，见 4 |
| 跨子计划回归 | `DiscoveryVerificationWorkerTest` 24 跑 7 错 | 未授权 fixture 修复，PLAN_CONFLICT，见 6 |

## 1. 00 manifest 逐条对照（I-1 / I-2）

### 1.1 固定输入

- Manifest SHA-256：`de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c`。
- 原文目录（Git 外）：`/Users/lukai/.local/share/paper-reprocess-30711`。
- 244 条 record / 337 条组合；47 条未证实列候选原样保留，未计入修复真值。
- 本轮真实执行：

```text
/usr/bin/time -l /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Xmx1536m \
  -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' \
  com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool \
  /Users/lukai/.local/share/paper-reprocess-30711 \
  docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json \
  /tmp/paper-reprocess-30711-child06-final.json
```

结果：exit 0；337 行；`networkCalls=0`；`businessWriteServicesConstructed=0`；93.39 real / 134.54 user / 4.37 system 秒；最大 RSS 1,207,648,256 bytes；并发 1。输出 `/tmp/paper-reprocess-30711-child06-final.json`。

### 1.2 逐条分类

脚本：

```text
python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py \
  --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json \
  --old-replay /tmp/paper-reprocess-30711-baseline.json \
  --new-replay /tmp/paper-reprocess-30711-child06-final.json \
  --audit-directory docs/audits/2026-11-01-discovery-30711-pdf \
  --csv /tmp/paper-reprocess-30711-child06-reconciled.csv \
  --report /tmp/paper-reprocess-30711-child06-reconciled.md \
  --runtime-evidence /tmp/paper-reprocess-30711-child06-runtime.json
```

exit 0；输出：

```json
{"classifications": {"EXPECTED_MATCH": 51, "EXPECTED_MISSING": 286}, "expected": {"EXPECTED_MATCH": 1, "EXPECTED_MISSING": 336}, "extras": 0, "reproducedCategories": 2, "rows": 337}
```

| 状态 | 条数 | 含义 |
|---|---:|---|
| EXPECTED_MATCH | 51 | 实际解析产出与人工确认的 email/name 指纹一致 |
| EXPECTED_MISSING | 286 | 确认组合仍未被当前解析绑定；不是“新错绑”，也不是“已修复” |
| UNEXPECTED_MATCH | 0 | 没有出现确认集合之外的新绑定 |
| REGRESSION | 0 | 基线已恢复的 1 条（record 31863）本轮仍保持 |
| SOURCE_MISSING | 0 | 337 条组合对应的 244 份原文均可读 |

**新增正确绑定 50 条**：49 条来自基线 `REPRODUCED` 组合、1 条来自基线 `NOT_REPRODUCED` 组合；加上基线既有 1 条，共 51 条确认匹配。child 01 执行日志把其中 1 条称为 incidental gain；本报告按逐条指纹把它计为确认集合内的正确绑定，不扩大为“325 全修复”。

### 1.3 生产旧重现数与新规则增量

- 生产旧重现（00 口径）：330 条 `REPRODUCED` + 1 条 `ALREADY_RECOVERED`，合计 331 条有旧结果证据；其中只有 1 条是真实姓名绑定。
- 新规则新增正确绑定：50 条。
- 保留未知/未修：286 条。
- 回归/新错绑：0 条。

### 1.4 未修复原因（286 条）

| 当前分类原因 | 条数 |
|---|---:|
| `NO_PRODUCTION_LAYOUT_CONTACT` | 280 |
| `NAME_NOT_LITERAL_IN_PDFBOX_PAGE` | 4 |
| `EMAIL_NOT_LITERAL_IN_PDFBOX_PAGE` | 1 |
| `AUDITED_PAGE_OUTSIDE_PRODUCTION_SELECTION` | 1 |

`AUDITED_PAGE_OUTSIDE_PRODUCTION_SELECTION` 与本轮固定页选择有关；其余为当前解析仍未产出确认的姓名/联系方式，不是“可由总数推断已修”。

### 1.5 逐条差异

`/tmp/paper-reprocess-30711-child06-reconciled.csv` 与 `/tmp/paper-reprocess-30711-child06-reconciled.md` 保存 337 条逐条记录（recordId / page / emailHash / nameHash / baseline status / current status / classification / reason）。本文件附录 A 内嵌同一 337 条表，避免只看总数。

### 1.6 实际重处理 run 对照（负向控制，不是生产结论）

脚本新增 `--reprocess-run` 模式；以 12 目标合成 fixture 的 run 导出对同一 00 manifest 运行：

```text
python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py \
  --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json \
  --old-replay /tmp/paper-reprocess-30711-baseline.json \
  --reprocess-run target/paper-reprocess-e2e/run-30714-FIRST.json \
  --audit-directory docs/audits/2026-11-01-discovery-30711-pdf \
  --csv /tmp/paper-reprocess-30711-child06-run-compare.csv \
  --report /tmp/paper-reprocess-30711-child06-run-compare.md \
  --runtime-evidence /tmp/paper-reprocess-30711-child06-runtime.json
```

结果：337 条 manifest 组合中 336 条 `EXPECTED_MISSING`、1 条 `REGRESSION`（生产 record 31863 的基线绑定不在合成 fixture 覆盖范围）；另有 14 条 fixture 候选行被列为 manifest 外 `UNEXPECTED_MATCH`（两个冲突目标各含 2 个候选，故 12 目标产生 14 行）。

**这不是生产 run 结论**：fixture 的记录/正文与生产 manifest 不同，脚本正确地没有把它们伪装成生产匹配。生产 `sourceExecutionId=30711` 的 ENFORCE run 在本轮 **NOT_RUN**（未授权发布/生产任务）；实际 run 对照须在发布后按 runId 重跑。

## 2. 固定 12 目标业务 fixture（I-1 / I-3）

测试：`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessEndToEndTest.kt`。
环境：真实 MySQL 8.0.36（Flyway 全量到 V173）+ 严格 loopback ES（支持 `_search` / GET / CAS `_update` / create PUT）+ 确定性 ORCID/Apollo HTTP；使用既有 `ExpertDiscoveryService.consumeArchivedPaper`、registration、worker、commit、projection，不另建业务 writer。未向生产写数据。

### 2.1 逐目标结果（第一次 run 30714）

`papers=15`（含 1 篇无邮箱触发论文，不生成 target），`targets=12`。

| # | 类别 | 目标邮箱（合成） | 预期 | 实际 outcome | new | repair | admitted | 原因 |
|---:|---|---|---|---|---:|---:|---:|---|
| 1 | 新建，资格合格 | n1.new@fixture.edu | NEW + admitted | NEW | 1 | 0 | 1 | — |
| 2 | 新建，资格不足 | n2.new@fixture.edu | NEW，不准入 | NEW | 1 | 0 | 0 | — |
| 3 | 现有空名，资格合格 | e1.empty@fixture.edu | REPAIRED + admitted | REPAIRED | 0 | 1 | 1 | — |
| 4 | 现有空名，资格不足 | e2.empty@fixture.edu | REPAIRED，不准入 | REPAIRED | 0 | 1 | 0 | — |
| 5 | 现有完整 | c1.complete@fixture.edu | UNCHANGED | UNCHANGED | 0 | 0 | 0 | — |
| 6 | 现有完整 | c2.complete@fixture.edu | UNCHANGED | UNCHANGED | 0 | 0 | 0 | — |
| 7 | 身份冲突（同名邮箱不同姓名） | x1.conflict@fixture.edu | CONFLICT | CONFLICT | 0 | 0 | 0 | `SOURCE_IDENTITY_CONFLICT` |
| 8 | 身份冲突（同名邮箱不同 ORCID） | x2.conflict@fixture.edu | CONFLICT | CONFLICT | 0 | 0 | 0 | `SOURCE_IDENTITY_CONFLICT` |
| 9 | 联系保护 | p1.contact@fixture.edu | PROTECTED | PROTECTED | 0 | 0 | 0 | `BUSINESS_PROTECTED` |
| 10 | 人工/审核保护 | p2.manual@fixture.edu | PROTECTED | PROTECTED | 0 | 0 | 0 | `BUSINESS_PROTECTED` |
| 11 | 缺源 | m.missing@fixture.edu | ERROR，SOURCE_MISSING | ERROR | 0 | 0 | 0 | `SOURCE_MISSING` |
| 12 | 写故障 | w.write@fixture.edu | ERROR，不计准入 | ERROR | 0 | 0 | 0 | `APPLY_FAILED`（RAW PUT 注入 503） |

汇总（`PAPER06_SUMMARY run=30714 label=FIRST`）：

```text
state=COMPLETED_WITH_ERRORS papers=15 targets=12 new=2 updated=2 admitted=2
conflict=2 protected=2 errors=2 sync=1
qualification: met=2 unknown=4 notMet=0 notEvaluated=1 unavailable=5
```

**冲突/保护/缺源/错误均未被计准入**：上表 7–12 的 admitted 全为 0；汇总 admitted=2 只来自 #1 与 #3。

### 2.2 准入成功但投影失败

- #1 `n1.new@fixture.edu` 的 candidate PUT 第一次注入 503：verification item 已取得 `admissionRevision`，`projectionState=ERROR`，目标 `syncFailure=1`。
- 清除注入后调用既有 `commits.project(item)`：`projectionState=PROJECTED`，`admissionRevision` 保持；provider URI 列表前后完全一致（0 次资格/外部重放）。
- 没有新增 retry API、资格规则或第二写路径。

### 2.3 写路径故障与恢复

- `w.write@fixture.edu`：RAW `op_type=create` PUT 返回 503，目标 ERROR，real service 保留 reservation/收据；同一 run 重试仍失败，不产生新专家/准入。
- `m.missing@fixture.edu`：论文解析完成后、APPLY 前把该 attempt 置为 `DELETED`；apply 的现有 `analysisLease` 返回 `SOURCE_MISSING`，目标 ERROR，不创建替代原文。
- 恢复场景见 3.4；既有 04 套件继续覆盖 reservation/RAW/person/projection 故障，本报告不把未在本 fixture 注入的路径写成已测。

## 3. 重试、第二任务、取消与恢复（I-2 / I-3 / I-4）

### 3.1 同 run 重试（成功项 0 重做）

对 run 30714 执行 `service.retry` 后：

- 仅非终态 target（M、W）被重置；10 个 COMPLETE/SKIPPED target 的 `result_json` 逐字保持不变。
- ES RAW 写入次数保持 2；`expert_discovery_admission` 行集合保持不变；汇总仍为 new=2 / updated=2 / admitted=2。
- 重试后 run 仍为 `COMPLETED_WITH_ERRORS`（M/W 仍为错误；测试以最终 state 断言）。

### 3.2 第二个任务（同源同 scope）

run 30716：

```text
state=COMPLETED_WITH_ERRORS papers=15 targets=12 new=0 updated=0 admitted=0
conflict=2 protected=2 errors=2 sync=0
qualification: met=2 unknown=4 notEvaluated=0 unavailable=6
```

- `alreadyAdmitted=1` 恰好出现在 `n1.new` 与 `e1.empty` 两个 target 上，独立展示为 2；不计入 `actualAdmitted`。
- 现有完整、空名、冲突、保护、缺源、写故障逐条与第一次相同；没有重复新建或重复补名。

### 3.3 取消保留已提交结果

run 30718（独立 source 30712，1 个新目标）：

```text
state=CANCELLED papers=1 targets=1 new=1 conflict=0 protected=0 errors=0
target c=cancel.new@fixture.edu state=CANCELLED outcome=NEW new=1
```

- 取消发生在 `AFTER_CONSUME`；N1 的 NEW 收据已持久化，RAW 文档存在，verification item 数量为 1。
- 取消不回滚已提交业务；未开始项按现有 `finish(CANCELLED)` 规则处理。

### 3.4 瞬时写故障恢复（成功项 0 重做）

run 30719（source 30713，2 个新目标；第二个 RAW PUT 注入 503）：

- 第一次：new=1，第二个 ERROR。
- 清除注入后 `retry`：第二个恢复为 NEW；汇总 new=2、admitted=1、state=COMPLETED。
- 第一个成功目标的 `result_json` 在重试前后逐字相同；成功项未重跑。

## 4. 迁移空库与 V170→V173 升级

文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`。未修改任何已应用迁移。

新增 2 个真实 MySQL 用例：

1. **空库**：`flyway.clean()` 后迁移到最新，`targetSchemaVersion="173"`；`discovery_paper_reprocess_run` / `_item` 存在；`inbound_mail_processing.pending_owner_username` 默认 `admin`；`idx_inbound_pending_owner` 存在；两表 run/item 全部 CHECK 约束存在；两表 0 行。
2. **V170→V173**：先迁到 V170，插入 `expert_contact`、`user1 FULL` / `user2 READ` 授权、三条 inbound：
   - `MANUAL_REVIEW + REAL_ACCOUNT` → V172 转 `user1`；
   - `MANUAL_REVIEW + SIMULATOR_NOOP` → 保持 `admin`；
   - `RESOLVED + REAL_ACCOUNT` → 保持 `admin`；
   - 升级后新增行默认 `admin`；contact/access 行保留；V173 两表为空。
   迁移后 `discovery_paper_reprocess_run` 存在且无业务数据。

命令与结果：

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -DtrimStackTrace=false -Dstyle.color=never
```

exit 0；48 tests，0 failures，0 errors，0 skipped；耗时 547.467 s。新增两用例在 Surefire XML 中按名称确认执行。

## 5. 命令与运行证据

| # | 命令 | 结果 |
|---:|---|---|
| 1 | `mvn test -Dtest=DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | exit 0；2 tests，0 failures/errors/skipped；63.636 s |
| 2 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | exit 0；48 tests，全绿；547.467 s |
| 3 | 00–05 指定 Kotlin 测试合并命令（15 个类，含 `-DmysqlIt=true -DpaperReprocessIt=true`） | exit 1；203 tests，0 failures，**7 errors**，1 skipped；仅 `DiscoveryVerificationWorkerTest` 7 错，其他 14 类全绿；见第 6 节 |
| 4 | 单独 `DiscoveryVerificationWorkerTest` | exit 1；24 tests，7 errors；可复现，见第 6 节 |
| 5 | `mvn clean package -DskipTests` | exit 0；WAR 生成 `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war`；Maven 绑定 JS 2035 tests 全绿 |
| 6 | 独立 JVM runtime smoke `DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke` | exit 0；`PAPER06_RUNTIME_OK realMySQL=true loopbackEsCAS=true realServices=true paidCalls=0 productionWrites=0`；65.18 s |
| 7 | 00 manifest 原始字节 replay | exit 0；337 行、network 0、93.39 s |
| 8 | 00 manifest 逐条 reconciliation | exit 0；51/286/0/0/0 |
| 9 | 合成 run 对 manifest 负向控制 | exit 0；14 extras、336 expected missing、1 条生产组合未覆盖 |

合并命令（第 3 项）实际执行类：

`DiscoveryPaperIdentityReplayToolTest, SourceAuthorEmailResolverTest, DiscoveryPaperLayoutRegressionTest, DiscoveryPaperAnalysisServiceTest, RetainedPaperIdentityServiceTest, DiscoveryVerificationWorkerTest, DiscoveryVerificationCommitServiceTest, DiscoveryVerificationQueryServiceTest, DiscoveryPaperReprocessServiceTest, DiscoveryPaperReprocessRepositoryIntegrationTest, TaskExecutionSummaryExtractorTest, DiscoveryPaperReprocessApplyServiceTest, DiscoveryPaperBackfillServiceTest, DiscoveryRecheckServiceTest, DiscoveryVerificationIntegrationTest`。

除 WorkerTest 外的逐类结果（Surefire）：RepositoryIntegration 12、AnalysisService 10、ReprocessService 6、VerificationIntegration 36、Backfill 4、Query 22、Commit 7、SourceAuthorEmailResolver 30、Retained 7、ReplayTool 6、Recheck 10、LayoutRegression 8（1 skipped）、Apply 2、TaskSummary 19；全部 0 failures/errors。

## 6. 跨子计划阻断：DiscoveryVerificationWorkerTest fixture 未到 V173

现象（可复现，单独跑同一结果）：

```text
Tests run: 24, Failures: 0, Errors: 7, Skipped: 0
BadSqlGrammarException: Table '<db>.discovery_paper_reprocess_run' doesn't exist
  at DiscoveryVerificationRepository.claimDue(DiscoveryVerificationRepository.kt:977)
```

失败用例：

1. `interrupted paper cache follower keeps lease resumable and owner completes`
2. `child07 real pipeline traces frozen REUSE refresh cancellation and projection only retry`
3. `runtime search trace survives interrupted pages and reuses only same run successes`
4. `frozen paid access gates cache and permits while preserving legacy and channel accounting`
5. `runtime credit recovery smoke restarts resumes remaining details and commits through normal gate`
6. `mock HTTP failures persist atomically in MySQL and appear in item details once per request`
7. `runtime version three registration worker SQL gate query smoke and frozen legacy runs`

根因：该测试文件在多处硬编码 `Flyway.configure()...target("161")`（例如 `:43`、`:214`、`:398`、`:659`、`:760`、`:856`、`:1050`）。child 04 的 `DiscoveryVerificationRepository.claimDue` 已引用 V173 的 `discovery_paper_reprocess_run`，因此这些 V161 fixture 必然缺表。生产迁移链包含 V173，空库/升级用例已通过；这是**测试 fixture 过期**，不是生产 schema 缺陷。

为何未修：`DiscoveryVerificationWorkerTest.kt` 不在 child 06 的四个授权文件内；修改它属于新授权范围。最小修复需要在测试内把相关 fixture 迁移目标提升到 V173（并核对既有断言/清理），或提供等价 V173 schema 初始化；这需要控制器修订范围后才能实施。未伪造 PASS，也未改产品 SQL 绕过。

影响：第 3 项“所有 child 计划点名的 Kotlin 测试”未全绿，因此本次执行结论为 **PLAN_CONFLICT**，不是 READY_FOR_VERIFICATION。

## 7. 诚实边界 / NOT_RUN / SOURCE_MISSING

- 生产 `sourceExecutionId=30711` 的 ENFORCE run：**NOT_RUN**（未授权发布/生产任务）。本报告没有生产新增/补齐/准入数字。
- 真实 Elasticsearch 集群 / 真实 ORCID / 真实 Apollo / 浏览器：**NOT_RUN**；本 fixture 使用严格 loopback ES 与确定性 provider HTTP。ES CAS 路由覆盖本功能实际调用的 `_search`、GET、`_update`、create PUT；不声称完整 ES 兼容性。
- 生产 provider 准确率、真实全文解析差异、线上批次时长：**NOT_RUN**，不作承诺。
- 47 条未证实列候选：仍未确认，不进入 EXPECTED_MATCH；本轮 `UNEXPECTED_MATCH=0` 只针对 337 条人工确认组合。
- 6 条基线 `NOT_REPRODUCED`：没有一条被写成已修复；其中 1 条本轮变成 ALREADY_RECOVERED，按确认指纹计入 50 条新增正确绑定之一。
- 中间页未覆盖：`AUDITED_PAGE_OUTSIDE_PRODUCTION_SELECTION=1`、`NAME_NOT_LITERAL_IN_PDFBOX_PAGE=4`、`EMAIL_NOT_LITERAL_IN_PDFBOX_PAGE=1` 单列，不混入“已修复”。
- 真实生产数据/邮件/采集游标/Apollo 配置：本轮未读写生产；fixture 只使用一次性 MySQL 容器与本地临时目录。
- 迁移、保护、错绑：迁移两路径通过；12 目标保护/冲突/缺源/错误均未准入；337 条无新错绑、无回归。但第 6 节测试门未全绿，不能给出“全部验证成功”。

## 附录 A：337 条逐条差异

`/tmp/paper-reprocess-30711-child06-reconciled.csv` 为机器可读原件；下表为同内容摘要。`baseline` 是 00 冻结基线状态，`current` 是本轮 replay 状态，`classification` 是逐条判定。

| record | page | emailHash | nameHash | baseline | current | classification | reason |
|---:|---:|---|---|---|---|---|---|
| 31372 | 1 | 1522c43ad4d32fd525a502bb | 8d3e044827567d8e14865c48 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 31375 | 1 | b99e79a68d1f712d9a730bbb | 412ae0a86f523dc8376b9f04 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 31375 | 1 | bde337de503351616b19869e | c98565d2a2cafbfee5075d88 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 31398 | 1 | 37e167f68fe2e9427ee6dd85 | b7d3d6d1dc8ede906c85bc2d | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 31398 | 1 | 651e641109407b1254e320a3 | d27858b6cf26bf4b3bd30cfb | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 31413 | 1 | edeb25be5a420ba870b84d7f | ad75b966bb1043e736cffa73 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31479 | 13 | 10262bce15ca549141262956 | 1ec90489ce6a9821c13d0f07 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 31494 | 1 | 652c1ff996e54508acd077b2 | ad36bdf567c17d4b221a12c6 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 31509 | 1 | a54dcb63b31f70b7c3d83527 | 2a3e13302d8223970afa41fa | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31510 | 1 | 266c15a02476526f8ce1eb63 | 107f4da6ae0692fa1ec8432a | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31510 | 1 | a8102e1e61e80742f22dc16b | cb5f3573ce8e696c8b735659 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31520 | 1 | 38deccdc8e864d63f1c7319a | 19820e8a51fa199cde8156af | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31584 | 1 | 5fe55089220bba6d7b9aa88e | 80ec850c61f7019eb709325a | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31605 | 1 | 28f689cc4565b85d860848da | 07ef72936e0fe8ac7e26265f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31627 | 1 | 869c4f80e05ad8f165dfb197 | 4b216fa9f1435017d205358b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31639 | 1 | 64e3b3b257a590bfc87f1614 | 07bec8a5f92c166c2445739f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31646 | 1 | b72bf58c4d9726691c8b5ee3 | d21565f99fea188e7cf0fcb2 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31672 | 1 | d12618f51b8ca82ef7f275e0 | 2c3ce19f4bc0477955df073a | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31706 | 1 | 1550b8aeb6ea2b4a25e90b14 | d0a7322ab58c35dccdd310d1 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31715 | 1 | 4fa8e85e8d5ae11b40a30a1b | c4492256786ebe480793ff04 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31715 | 1 | adb11481936f06933e49dfdd | c4492256786ebe480793ff04 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31742 | 1 | 0662e287db0e0d61dc6d883d | c6ab9845036f90a06213aac0 | NOT_REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | EMAIL_NOT_LITERAL_IN_PDFBOX_PAGE |
| 31750 | 1 | 8005b16c1c3a109fc5c77766 | 6f8550401ac22c460b1967a9 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31751 | 1 | 872c10ab94085f36f388ceb6 | b335f369406682514c157d25 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31768 | 1 | a25c3d7239cde674787e581b | 2111606803b212bd293bd08e | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31791 | 1 | bf380a76f545d67490d644e5 | 1839668006fb74fe11443a0f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31829 | 1 | 6797b16a858686a4dd9bd366 | ce6c2cce28f6b310401c5b00 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31848 | 1 | aa7a754ff27c9afe497c858a | 36a397fe1d58b93aec0fb191 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31862 | 1 | 1a0bce55033b9a80c5f80763 | 03d7482958c457a91bab3544 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31863 | 1 | 36aca746a0f3fea1daf33827 | e906800d19dd80e90ecd0293 | ALREADY_RECOVERED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 31914 | 2 | 7660ee49fda486c03e90c1f3 | 18f90fd6d55e1f831f70cd98 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31926 | 1 | 116783813304191b5e013b98 | ff3192af08dc198a75ba0eb8 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31926 | 1 | e2fec58b41597cc875fec513 | f083d90fc8ded7f972402463 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31949 | 1 | a6047a50e5fef6ba9ac369c9 | 7f976f32772d2087d5d63674 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31961 | 1 | 0f3714aa9c7a6c46c6d74827 | 79ca79d7194dfd31b770e5b0 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31961 | 1 | 404b08f1922f5f3934d39868 | aa17d159f0bd714375a8bdb4 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31961 | 1 | 54b70cebcd8d57097c484fcd | 1159ed2dc7ea4b8f7e0b165e | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31961 | 1 | de537034453a80967e182401 | 1fa303e7d93109db41d2132d | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31961 | 1 | e80600538568864bae985c66 | 43bd9fda7b03a7fecf675dd1 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31988 | 1 | efe5735e975af26f8f97888e | 5395afc020c96de0e68daafb | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 31989 | 1 | 830c00dfbc769730f924355f | 06c476f24884ce01dd7ac9f6 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 31989 | 1 | f724a652329b36ea3f94898c | 8ffee1099cb0dff0c3f85601 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 31992 | 1 | 1b188405be73a9179d0abf41 | 3c0f909507f1fad836970b9a | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 32016 | 1 | 131d66b4165a896814bcae5e | bf799f14ea0e094a3e37b11f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32016 | 1 | e5e0cfc655e48e43422eb1c6 | 407e233610d722be88f3c5d6 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32017 | 1 | 1b42485c71be185178edb6d8 | e7266f6d48d993b3184b9158 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32083 | 1 | 8197293e49016f06a8e9e11d | 84851e32e03a5059e60f9590 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32091 | 1 | 0fe4312f3d8d2a17be7490c3 | 81effdf32483cb5176c92746 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32126 | 1 | 1b34c72dd1702d696d44f208 | f7f55a9a168ed5fb7ea29c6b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32136 | 1 | f4f8f3d6553e9cc128d153db | 7f3b6acf8519f914d5fe07f9 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32137 | 1 | 1cb9e85da59581715a61f338 | 3ea8f1371784bf51a1683079 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 32218 | 1 | 0c9ddb03db435197a7a5c769 | ae4bf69a7573460686d9af16 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32218 | 1 | 2a57e4b650e4ba03b23c202f | 7fa85b53155965970b858b3b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32218 | 1 | 3214cb3fc72da983e3706c40 | f2a47ca27ad904d676062583 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32218 | 1 | 9f70b852fa144c10f8632226 | e340ea6f3a19a9d16e5a3110 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32259 | 1 | 7f7d7fc77d75ff259c7e4069 | e1fd661985d6e81d3da53900 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32325 | 2 | 54208eccef45069f273076d4 | 1a27d0c96b76d17c669dfb20 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32339 | 1 | 430283304ac4231b49f081cc | cf59a8572c24c7a2c21ff8c7 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32378 | 1 | 670850e5ac0f3f146016fc2e | 782d629db53d1aaa5a9cea30 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32403 | 1 | c1c2a67346bb33814b2fa948 | 951748db3de39b7dfe634c00 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32485 | 1 | 71aa6c3e8455e96f3f8fb3d0 | 8707e06a63b3d39b11e3d67c | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32490 | 1 | 99690804cac8c390b25dd31a | fa6ea9807c1fe9b817a02f6c | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32594 | 1 | 9dd52ed410da6c58466cdbc9 | 7eab04377d25b533e3fe70a5 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32665 | 1 | 3bb16cb720b017d4232e7c1a | 25f44a33aa995b5edc5b2336 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32686 | 2 | 48b921e0f31e6dc80c09dc0d | 32ef36e2ce86fa6d49d252a6 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32689 | 1 | 72b2dfe83d5a035d73ace1e0 | 9e3947ef0ad7d3130c235548 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 32741 | 1 | 307ba573a40e8ecd515e112f | f190373d8c4df71810aa6b33 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 32741 | 1 | 3d09a9552b98fe053d945957 | 065cf3c94b15f1c2649fa3a0 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32741 | 1 | 99a6917345d09e73ca1f0fc8 | a30447d9e51007fc51896f4c | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32741 | 1 | a1a9b888fdf5bb8ad1374eee | 42f076a9c783cd816b7c4fcd | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 32752 | 1 | bce3e9b416508a153ab7b5d7 | 3ce6de6b78f35cc4b87cefef | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 32817 | 1 | 13a7336d6da59f3ed7f04bd5 | 344a9c23ba5bf8b5a510fed1 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32817 | 1 | 3b82cf9d50c0358b697ee951 | e9d3b9f7d4f23488390a12e8 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32822 | 1 | 9c5ea9ccb0ed312bec557c79 | 1577305cdc1cc6bfc4e36380 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32825 | 1 | 0fb31303a8acad64471f271f | b5860ccbf9de04d85c36daeb | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32832 | 1 | bff19f1149ccbcf723c39df3 | 6164662c3953bdc80871d15b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32847 | 1 | 133fe5738b828195c0ae273c | 9e4b80ebf572d694db7c6784 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 32872 | 1 | 569fa4d8206fdf0c959293d5 | fa04a6644fa3e0163119d134 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32876 | 1 | b267933f7ba4f8a63ad65cae | 3093e655ff70e4606304231b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32926 | 1 | 999ad0436fb4ee1775f6acaf | 91722587ce162c95ea450ede | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 32984 | 1 | 5a3ccb5fcb8660a57bd937a1 | 153ba1737666dc881973065d | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 32984 | 1 | 7ee6596e46a319b44f35b495 | b61319d19df1b0aafe7aea7c | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 32984 | 1 | b45e1527dbf715476840d2a7 | 13e044af48ab2153071f55fc | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 33047 | 1 | fc4608c19a816adf3eb5f62c | b6f53b3c17dfaa29b1653c15 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33078 | 1 | 824516ead1eb1368b847c039 | 7f1a0128e42462c9a9089c42 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 33096 | 1 | b5c99554fbee2126b95953b1 | 8852da35288820100f0d60db | REPRODUCED | REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33097 | 1 | 907adbe332eb21df5efb48e0 | 2de9c77657469b0768589bf2 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33115 | 2 | 84c0ee3dc2df9c09dd719e40 | fb1bd0f035e23d0aca5cfee3 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33137 | 1 | 7b0a60ad83b3b7b742cb3852 | 4ecc3ece321d89a5e448cc5b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33141 | 1 | 280462b1664bdd3c09f87cb2 | a5311a509c98c3cbba765bcd | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33141 | 1 | 2c3e811278a3f273b2185d9e | a251a570323e91fc2fbeff03 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33154 | 1 | 3ea676f88e22911cb0b71cf6 | e804617ea899ecea59220552 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 33177 | 1 | b78e9aba68bc7186a2bed226 | 7046251fbe22c9048f4aa298 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33252 | 1 | 512219a64c1875792c546a13 | 92ae832d9a3cf9e9e3d10f3d | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33266 | 1 | 81bad12c95d416883fa124fc | 6153babf6b65846e6c454111 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33306 | 1 | 0ddb11835c80359068063226 | 68fa1117717b0fb25a29cc5e | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33306 | 1 | 246361281652f0d6b0f3e109 | ce3bbc264d9597d02a545c49 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33306 | 1 | 749364071cd3ffeeaa5a43eb | a32df0e93d9512d216935aa5 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33306 | 1 | 7d21c867be4d899281114271 | c09f6e7bd76c0c2d1cc5935f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33306 | 1 | a992a569b60f2df76cfcbea4 | de5968e927577b6a2e3c0b90 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33365 | 1 | 57dbb7c4c4d2c7dc3daa1865 | f901f6d84cc01451bc064a70 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33533 | 1 | 27f1f5957095e7087573a3c7 | 3ebfa7799557d6c45c843582 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33550 | 1 | fcf95a56bbf3c0cbd45ff182 | 4988e3e2875a8156a6c2f083 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 33618 | 1 | 54f1b0153c7bbe6564ea8827 | cc8bdc1e3eb82fa37bdc554c | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33620 | 1 | c13db12b9abdeb00f7f84de1 | bcd5875891d76fe1a60b5edf | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33643 | 1 | f86dac48cfd3aae85314a92b | 20d463c515c49de4aa5d6099 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33661 | 1 | ae4c38f458ee739518f7818d | 02a8361c9fbe5308c60640b2 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33688 | 1 | 8b27e896502f1383cf00b41f | 6a5a4705ffdaad9dd308719e | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33719 | 1 | 0619ded30d3c645c935b0e7b | 818bf8a2977c2361dc518199 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33742 | 1 | 2595d4d8b6fcfe7820b5dc1a | 476a8ae2892b2edb3072fbb4 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33742 | 1 | 43bc46cf97ae2dcc582e18bb | 7ae3bac72363ae2a2536d120 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33742 | 1 | f8c1aa5e6766245df86723e1 | 179c9b7b5e30ec93b4dacbc4 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33816 | 1 | 153fd7476453c0cc281adeb2 | 24fc6f8ce2802aa9f71c6113 | NOT_REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 33819 | 1 | b5c6aadaedae297eec6b639d | 764d536d9d27b6479e906d0b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33838 | 1 | 7d46a0559bc0d7dd2289b54b | e468ea3012f47314d9322bf8 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33840 | 1 | 79f02f6f8f66b6d31e93b056 | bdafb5b604ef7d500b745571 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33864 | 1 | d3ba68ab9fd606756c1aa3ab | e51ed7b996a6179d265a4a3a | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33866 | 1 | 5c72f763af589bd99d4ee519 | 30963e26b1d87b0e99259a93 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33895 | 1 | aec1f75e307a44c11d02d4e2 | 1c6c1e76df451caee7ded225 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33901 | 1 | da050c4ca3da3ef20e63b86c | a7dcb03269cd7e16ebfd1b3b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33942 | 1 | 875f9391eb4dfa3237648a7e | ff12a13aaf02c423496f0b04 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33980 | 1 | 7442f5ddb5afc13f9e484fe4 | 8c361cdb9071c41569a3e435 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33980 | 1 | 78870a9b2c11994eab38379d | f3d947fed137390fb0802267 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33980 | 1 | c9a5e0ecded0a58858169db4 | 12dd4e8782f01e7bfd7dcfa0 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 33999 | 1 | 3d1a22217d6e8b62a40f74fa | 21574ff722b6da3146096946 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34039 | 1 | 401596987099fb1f51d2fcb7 | 14492ac7e0722f2ebd85ed37 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34039 | 1 | 57dd716c210a5a102dd79769 | 5e85749a11e9f51c5dfd01fa | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34059 | 1 | b8f64c6608996de6f5cf40f9 | 4fb014bd140d1d45997808e9 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34062 | 1 | a69ae7c776660c625f4975a5 | d93f34c61d497bffc148d527 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34062 | 1 | b809dd1fc6970d68bd77d04b | 374a464dd2c69ad425646281 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34070 | 1 | 9b5cb6705871e912c3f6fc67 | dc11e5acc5a839e6618b32a6 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34076 | 1 | d5452da11adb5f3b14a61e50 | 186424e44f49b8c968679e5d | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34120 | 1 | 4b04c10ab2c85a8a635ec7ab | 106f422e411ecc7658b7720b | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34120 | 1 | c05a1cefb8af719a93f27151 | 41f282011f5c77a595620a00 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34133 | 1 | 7d86a5763936f1743984fd21 | 2e13bf7426733862b48c9d58 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34153 | 1 | fe1cde81b435d120b0fe8d37 | 8c15f2fbbec48317ffe375cd | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34194 | 1 | 14e6cb6e2a72414db370cb2e | c6497415417fd3cbe6948e3d | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34257 | 15 | f21c4bc29bc87c95b91f57f0 | c5c6342f00f76e1b59c60213 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | AUDITED_PAGE_OUTSIDE_PRODUCTION_SELECTION |
| 34274 | 1 | 09d3fb8cd5331ad726137588 | e698410d240693564d8567f1 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34274 | 1 | 46dd51df5a59fc26342209d8 | 8146044cfd60699ae9040685 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34274 | 1 | 91cff7a09e40b0354d672e67 | bcaf85da604e9926ba0f85d9 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34274 | 1 | ad3c6c85cf848d313615933f | ec980cfa38bf5676b4c9c67b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34274 | 1 | c4042b720b9faa19de47262a | ac1332aadab583ed91ee14e2 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34282 | 32 | 15130896f6aa2967bdda94e2 | 567097c273df431207b1c936 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34282 | 32 | 1e3d4dad9cc90be9e695fff7 | f3ea2bc3b5859b7e4c0a3a77 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34282 | 32 | 3f31a1decefea55f5567bbf6 | e16027c1fd03982146e3cca4 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34310 | 1 | 9e294de1fb38345e327cd4e4 | 30fbeb13df37086cd745765d | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34321 | 1 | af7db7859b2133ce8ffe1db5 | c34939113d7e6560047c412c | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34371 | 1 | 96d232f073d06795afd58db6 | c3d8e734428aadf20b41dcaa | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34384 | 1 | 06bcc12759b96ab66fa35cd6 | ce327cdf8eb9ad88c21dd8ab | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34396 | 1 | 447a8dc44ceff656033195f4 | 7e9fb8873fc1c52767354faf | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34399 | 32 | ff805d578d3b3ef9c7857cfa | ec1c9588e752f44893058ec0 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34426 | 1 | 664db5e45c9deb8dec9014de | 1d79239fae0a2485ef02a8c4 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34426 | 1 | aea2364a3d7248072f2be99c | 2a8a3bcc8a371e75dea54507 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34462 | 1 | 074a1bbab156fa69a4c9ecc2 | 471b32b0b775c0df774883b4 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34493 | 1 | 43af69c7c4393799c3279b44 | 8688bb9b5c96c14b96373e5b | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34502 | 1 | 8099caabffe1feddd8127bb3 | 7b7bc45e4062e4482acbbb84 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34502 | 1 | 9e47d9756f072e693117cdba | 90018b9b39ad95436af113c0 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34508 | 1 | 3d0f8f2c5e0b940722ee333e | 0fb10967630da7976a287f2a | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34508 | 1 | aea7f5fbdf7414c46bdb1e6c | a2e75e803a9b6f3e12529607 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34508 | 1 | c9a8c4ec614fa14fd1a3839c | 37408a1750f0137469ae2044 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34508 | 1 | e7c044f10ca78e716097b9a6 | 4b97747f4f76b25f354e6379 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34548 | 1 | c92a26595952614115ad7294 | ecad9466ec45f544833140fe | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34563 | 1 | 51de8a688b22eb8bedbc874f | 0b011d55d1c633cbade70586 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34563 | 1 | dabd66476966c3906fcabffb | 2a683524a8a84a86bc491eda | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34572 | 1 | 3f640cd792e0c525cbd2eff8 | f09bb2d779d117931178f7e7 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34572 | 1 | 78e8b6de8dca8f6b21d63a7e | 4f1830c9e69a607237b9ed42 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34572 | 1 | e20e27e3484ed86ecf4c54af | eca679ec5d7a49b3f438988f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34589 | 1 | ebce0fa6c3ba8c7e2d68874e | 1a4d381a3d85ab9a50579310 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34637 | 1 | c485b9acf5f92a2189d9eea3 | 8eb2aac707a4c617bd1b02e3 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34640 | 1 | 650ebe911c2ea07803eb98d4 | 34167596c697940aafe20cf8 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34651 | 1 | a8423f85dd08cf46529d32cd | 19e26cb02e58e52956d2f7bc | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34651 | 1 | ed33d1ec24321e011913b35f | 3f4ee39dea51dc336c05d346 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34683 | 1 | 1412398699c95d2f95b66b02 | 2cb4d4d7b09ccfbeafce7776 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34683 | 1 | ff2ab531037dade2c7fe2c10 | 11cadf9bed723aa955874aea | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34771 | 1 | 308f8e5335f20238f5857a06 | 21deca72bc3099f3ee9d3b6b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34771 | 1 | cbe6de2a3023f5d387948f3d | a65c06eb5c5da15e531812c4 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34771 | 1 | d6e3e4fe8868f68a63f439be | 58584fd12e39fd57eb9476f1 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34775 | 2 | fc860be98df39fe3207b41d4 | 350c960e443583b3753814d8 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34820 | 1 | 23c96eaed49293c3da840c2a | 87016f461628af95cc0114b7 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34851 | 1 | 1522c43ad4d32fd525a502bb | 8d3e044827567d8e14865c48 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 34873 | 1 | 2886a61ca1092a27aadaad6f | fc692bdd368505532e569863 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34929 | 1 | cbc49616ff093f0daf4d4ffb | f27e3f184600d4504e2c1638 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34936 | 2 | fc2d0d7d33d8d45718295ff1 | ab9121f73517485c2c38a5dc | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 34955 | 2 | 45adba8a60f340af0d58b3ec | 012ac95496b3eea83cf05c75 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35054 | 1 | 2e693d4e4ba7520775047896 | 5e0266de1c84e0cf9ecc5afc | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35054 | 1 | 3aa8b87a0c96e4453195975a | b21c7ebd492d3d2ac54ab692 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35054 | 1 | 749a99cb23637dc81021fe66 | 2fcae1b0cd83f8c9ff578a67 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35054 | 1 | a128792275cd697a128dd297 | 8c9145a3da70ecb45d883958 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35126 | 1 | ddc3b66f47b27f30a382331a | 88d6d1ff21085cb2d9ec6232 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35128 | 2 | 2faaf67e83dd17aebd073d40 | de3264a2d38a55dc25bf2c75 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35138 | 1 | 96798e5d436cdc05f3756da9 | 94cebb3dfd3978551fb342f7 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35173 | 1 | 1253d1f56fe5f09e01d5091c | d89c9d267f228d57c8cd6882 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35173 | 1 | 20087cd7ee3a566dbe9024eb | 4a35d6bfaf4e45499ce0580f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35173 | 1 | 3a16d43b9cb8e340276e6fbb | 6e5394b602f0de71b3c48d9c | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35173 | 1 | 5751a33277aeaa5267340842 | d7cf5dc6ec7625dd89dd601b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35208 | 1 | 26d5ab589a4e260fae8eacd6 | 87b74529972abc1b8de3550f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35208 | 1 | 9ae79fd58b0926af3e4c7166 | fca58cb37884f873daf11242 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35208 | 1 | e2e6bc38c0c346659aa5a586 | 1c75e57a730dad2c427e430b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35231 | 1 | 58d53c9258cc75b38b566d95 | 3df42992bc47081c4703c317 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35236 | 1 | f68e35eaa3e5f38f1c7fb7c0 | 89ff35d1efa4f1ff2dea4af1 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35238 | 1 | 14bf49f448f3e55ed930c690 | 29beecb21ec92298d1960e36 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35252 | 1 | 10413dd6831b163b5e132244 | dd11de50914e60e05e119580 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35253 | 1 | 47f7648dc6e032c8a03ca979 | 65e9cb70492d99c2c06eac75 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35253 | 1 | 5c1ccf62c766513e682a40cd | 65e9cb70492d99c2c06eac75 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35289 | 1 | 301b1b5ebcef65b964ebdc0c | 60d407e8d90661e846ab33f9 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35289 | 1 | 758dbfc42fdbd5755b693e8c | 13f03b0dc9093df4dc4f020f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35289 | 1 | 9bd11b43a0e7ddac7e1a9cc3 | 422f8591b954ec89554e84b9 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35315 | 1 | 59501cec10702f9bbc2b42f8 | e449495996eda43acbd3b229 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 35325 | 1 | 74b70e6a81923e5a05689cb1 | 0dfe78e61e226b65bf711caa | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35333 | 1 | 7dc47f27472cf14d496df4d8 | 572b3af08c865a43db9f85cb | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35334 | 1 | 0eadeb05a996a33b49846b3c | b960d7fdca36fa9bb7ef0644 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35348 | 1 | 706db807698583ffbc66f1c1 | ac2d724b483ec83478991261 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35394 | 1 | 9c25e6d60fc4e9ba7197fee3 | 95499a0a9f5748f41c6c503e | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35462 | 1 | fa4f45950ff77c6321a795fb | 64d3dee05a923e09770c2cd3 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35463 | 1 | 8ac0496e175f8de0d06b15ec | 7b105934df77ef618c8fe913 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35466 | 1 | 030fc0f2e49304a22cf4bbb0 | 5e04195827e4a57c9fe65c38 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35510 | 1 | 55e4ae3d8340191639febf43 | fa9518d59a7dfa1a2d296702 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35539 | 1 | 56a7dd76b7ba544779b41dd6 | 643d243c6f226eb01d1b3f6b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35539 | 1 | 73973b3bf096afe19adee70a | 04180c70afe81c29b08b9af3 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35539 | 1 | 8450b17d2fa860de4ce5690d | 17190762bf4aa9b5dffa8745 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35545 | 1 | 783c9e4a95ba024b97848e7c | 6b08e5ebf6c465307e99cba1 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 35554 | 1 | d6760a8000d662da2233ae47 | e016129ddacde0477749295b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35595 | 1 | 9afe332b70ed8021e1cff0a8 | 73ee0b9e224328be01e3b5be | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35595 | 1 | aab0089b2fd1426c64e21b7f | 82387d8b1f36012dbbbcf00d | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35609 | 1 | 8a060e7726b15bfc1c52c0d0 | 36a397fe1d58b93aec0fb191 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35630 | 1 | c1a235b346171c3911cc5c00 | a1f9498b9867c8d21a4cad0d | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35652 | 1 | 2ed4b8cc9faa494c0e3d8cfd | aeda77ff582b157351942a20 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35671 | 1 | 080976a285410c33ef3ad6d6 | 7891159ba326789144299132 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35671 | 1 | 80df4161aba163de7c26ec21 | 9e97912cd653d3f5a87d3d6e | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35691 | 1 | 14d2004c1c7c4fed5cbdcbed | fa7c9402da28adeea60b0203 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35715 | 1 | 48e00d7fac33ad8983619328 | f62ef546908af835b8e1d281 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35715 | 1 | 605a9cb6ba9b713e13d02dcb | 7e7b1313dac21538d84a6119 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35715 | 1 | 87f445b50ccbfddb2382aab2 | a9d43ead76aeaf8095b860c0 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35781 | 1 | 8baa68d2ea35d159fa63b66b | d2ccec72e2702244d36417f1 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35802 | 1 | c344f556949815852c128d94 | fbaa992948dc512adf3ef533 | NOT_REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NAME_NOT_LITERAL_IN_PDFBOX_PAGE |
| 35868 | 1 | 2a221d1d3ffcc21222ef0c2d | 0a653bb446944da7ff765e41 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 35946 | 1 | c087311971561800f219cfe3 | ba6e095b82ec6f5abd209cd0 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 35946 | 1 | f6fd76b457f1f106f1f5a4e0 | 4a618b3461a2760078abdeac | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 35948 | 1 | 5be5b1903c7754ab0ae6306f | c9636bc080ac81b5b4852a71 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36006 | 1 | ef67b89a8ba78c621f160e58 | 25a9f4449e493de058dd31f6 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36021 | 2 | 2fb1da8ef5d75e97bcd4f9bb | 217bba421f3cff897167d5c1 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36022 | 1 | 683bf932ad74bfca9d46f01a | a76e69676d3dad8bbf5c00c8 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 36031 | 2 | 5355707c72f20a6ea72f01c2 | 9fd82f20839e771142c893ff | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36031 | 2 | 76b1c2ebc72318aefc25b8f7 | d2e286a2ee04db6d91f84dcc | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36045 | 1 | 6740dd0fd2149e9e7d3b1921 | d8a7d1de78150078410fccbd | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36076 | 1 | 3760cc6a3db7df4e2ebe41f2 | 7cf93369c40290a6a957f56a | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 36082 | 2 | ee0aca9bbaa437274cfd18a0 | 31f87135da233bfcd1760255 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36085 | 1 | 6a2ee9181a1440cdff8c9079 | 577f68cd90b005242e8bc07e | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36107 | 2 | 757fbcb18095e2d4321b57db | 12b343d5a9fb474ccbb9dd9b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36107 | 2 | 8c71f8062ea1af10088b5120 | 0e38b072e986529a609c8278 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36158 | 1 | c6d6852dec03e401f9e849c6 | 49128df42c54e28af186c2fc | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36175 | 113 | daf2934bc0c2d20fd46f09d1 | 14bc02a9fd95f87b86e2b7a6 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36181 | 1 | 68ecaa9feffc5d234e8b8c3c | b3c950044a8f6aa0d4cc6985 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36208 | 1 | 54f14c094ff12e4cf007ef56 | 4b6275e4ca70eea91346f21a | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36208 | 1 | a5c16009ce4123c2a5b1fbb0 | c076c3131768bb9ec732b982 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36236 | 1 | 377132835d9a490799219e59 | a915ce86be9184f5173acb86 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36236 | 1 | c77b35fbfbcefb11c9d151de | 25a7429c27bc974568dd95d2 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36315 | 1 | adef42f09a0fb977a69e624d | c202d1a655ff48faa1606633 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36336 | 1 | d6e61334d3f54ea042438785 | ef505e442de87faa2085f558 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36344 | 1 | 4ab61ec36aba79c6414db29d | e3f907a79aade5e1ec789db2 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 36418 | 1 | 67bbc7bd3baf2a3a78aa9610 | 064b2139470182c80fa5b503 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36435 | 1 | 43d824ac08c739b71ce22ede | 02f99f8c4ad5098453dbb94d | NOT_REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NAME_NOT_LITERAL_IN_PDFBOX_PAGE |
| 36523 | 1 | 5c1ec8cfa55d1e41e6f09b34 | 9ddd84013b12443e0c45cd29 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36523 | 1 | 8a7d07721314881f83d9c666 | d0dd1eb468f1cbdd5d4d3c4a | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36523 | 1 | b0a09f2a21cfbe53bbaa7c36 | 2867b225cd889a46c5051e06 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36523 | 1 | cc3c690b0725ddc0ed9c8d46 | 8a04428370446ec1df61956a | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36539 | 1 | 0491ea853b7f56a5ef440050 | bd3212b864db6deec307a3a4 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36586 | 1 | 203cc851c58dc09249b63aec | d3919659be22c030f6b8af1f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36634 | 1 | a6c8eaa3504cfa135dae36a3 | 7f290b612b514ca1b941709f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36675 | 2 | ebf96c7d30e56bbeb3828113 | 1e4ae82d4b1363523ef4b099 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36680 | 1 | 317bdabea2a085f2bcd1f2e9 | f152aa7a5f6bbaf18a5e525d | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36680 | 1 | d85406fd08e062f0d727ef22 | 0a2ca342478946a0752c2623 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36695 | 1 | 45c13f59f243514c3e30bfde | 18ce82b14046ecb9bf6c1f64 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36721 | 1 | 7cff741031c2f373920f86d0 | 880171dd4e223188afe70e55 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36739 | 1 | 6b6c64ea13fb6a24779cb407 | 85093b9c9bf6852984608cc3 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36739 | 1 | 7a7b53bae2e8748abb4656cd | fdd9e30a67035b437ad9a204 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36739 | 1 | bfa81ee600f7a42c528b21b9 | f18d97d9af6b602d630eedcb | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36780 | 2 | 4c8b69010503a4507d81c575 | c22e69047e6c49b010f246e7 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 36795 | 1 | 850e06e8e24ebd41b4746d13 | b14e33e1422faba1d5f736e6 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36798 | 1 | d9908002ab93264ebbc3fd7b | 4db9ce64609b78509ae59417 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36834 | 1 | 41763982c9bbd0319d81039e | cffca062579f9e47c017aace | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36834 | 1 | 6a2a0f735e69bbc5d0841a93 | bffb3eadc799b208d7fccf5b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36857 | 1 | b82269012a15b376d56929b8 | 67e00dfe561dd3124fb2cdf5 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36864 | 1 | 1291c0057c0e6d2cdc72b7a7 | 9be3c09dd533bce33aec4cde | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36869 | 1 | 7d98e9c3167ae6e691571164 | f711dd99d3a0feada38f423d | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 36869 | 1 | adb6134d2ebeaa615370d567 | b5301ba2e7f746929cd6465c | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36932 | 1 | 2b3fd01b21c556ea152bdde9 | d83e7801fc813a9264d2671a | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 36964 | 13 | 8003bb38f99cb17fcf28e89d | 0879d8755c4f6d7d0d4ed2fd | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 36964 | 13 | 832ce508bee2c8b909a0862c | a1c60db804cf63685d60f837 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 37042 | 1 | 7186f1a7c0f82068768be044 | 30c8a307856778baf2b08d64 | REPRODUCED | ALREADY_RECOVERED | EXPECTED_MATCH | CURRENT_BASELINE_RECOVERS_BINDING |
| 37050 | 1 | 97fa9f2f7de83fbf23d76afe | 19652db94ed23d0acf27ac8c | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37066 | 1 | f6f95cf61098aba0277f1b50 | b7951878c3363f6c0aba8b22 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37140 | 1 | 46faaae06487a8f6280b8011 | 1ae52c825454b1e9103c1864 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37140 | 1 | af45a5dec4e81e9626371346 | b720741fd025cef744d77c38 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37167 | 1 | 162ce1fe2e8d0141b3b2e2f2 | 36198a2f2e74af4cf66909e1 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37190 | 1 | faf218bfc209857aee853dcd | 71d6c0a52adb35b7981aae46 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37226 | 1 | 837b3a0c49bf02bccd2826bd | 38e37b7c2234424ce9bfc62f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37226 | 1 | 85ac53a3f3baf0ed008ee7de | de3500dadd877c89adc2366e | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37226 | 1 | a7905563bdf858aa2cc366db | 6635ebd2e55f4bf647be9768 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37241 | 1 | 456e90e5f666dc52e0fffa27 | a2d37ed42de56c59aae1f85e | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37245 | 1 | e329da6289fb8a95301c86e1 | d702b6d562a1ad6db50b5e20 | NOT_REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NAME_NOT_LITERAL_IN_PDFBOX_PAGE |
| 37254 | 2 | 668780217df9b60b39be165c | 615ca63bcbeab129668a520d | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37288 | 2 | ce80a1cd09d448110a0ec40a | 92214e3f751bac53c264a5cc | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37293 | 1 | 440183a7071200aeb1271859 | c8b6b57f4bcebc0bd39df8d3 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37301 | 1 | 7a428db4afb4b9c892c5c985 | 5d488a13d1d5886a7b93f8b6 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37303 | 1 | 0fa04b703fc9cc5d7778a1ec | baa3cbe54988fd4e802488eb | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37328 | 1 | 943091f45c5385e4ac7fc0ce | 30f3e895e3a5bd55781312bf | NOT_REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NAME_NOT_LITERAL_IN_PDFBOX_PAGE |
| 37359 | 1 | ba806603ca8af999d1683a51 | d476fe1a90180457b48c4afe | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37395 | 1 | 9eddfaa56512ef64170c52fd | 7810e312d1ef56f0ce5fe3f5 | REPRODUCED | REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37516 | 1 | 28a79978ee9a1b12159f8ce2 | 6290d405e7587906ad5a5a94 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37546 | 1 | f92a1349748738cae2385bb7 | a5a000729fdd0d73bff435b0 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37580 | 1 | 2200e40f4f93cc35b0748a84 | 2d68ef1c40dc66310dff8bcb | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37647 | 1 | ab265f9994b64058dd6d96f9 | f25728444199a8b64aeaa533 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37651 | 1 | 18254cf09ccd8d95e1dd349a | 9bc865aed20aa9ad77792fa9 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37684 | 1 | c435ce0831cf69cd3c12c4da | 0c97ced7ee6d68eff07b9bfe | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37694 | 1 | ffc4fb76380c1025a79deef1 | 3782cb548dd513c78b67cc50 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37708 | 1 | 46e8d7a8e69a4c877a4b8493 | 90e4b1744ff759e7e32facf5 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37708 | 1 | 95d6a00cffa5bc3b47067889 | 5e11e2a6ebdbd59bf91da560 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37708 | 1 | f87e6f967c05e1e3f99bf186 | c0816016052891c12d207cb2 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37785 | 1 | 9e7449d85398d4b0bc4c0515 | 56222db5fa0082c2aa5be65c | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37785 | 1 | ff79b1fe9655516ebda7fcb3 | d9049c4523946ba158791db0 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37802 | 1 | 92843fccde49e85a79ee4796 | 8005a1ef4bffa1245f705dd1 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37805 | 2 | 86ab9be0a2f8d41465a491fe | ecc7319656f88c95360ee4ca | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37849 | 1 | 9a5fd47149e28b07adedfcc0 | 0b613c0a3d4a526401960753 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37849 | 1 | dd5bb05ab151ef74a24c6dad | f18c460f89b9742ba070bdc9 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37857 | 2 | c670f2fc545bd00e34e75259 | 74642ce3ab6bbec9add90be3 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37886 | 1 | 26bd1bccbc34ff24d86e57e2 | 3aac9d8d2df0869553890831 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37886 | 1 | 35f7a2d7db1fdb6a0b419be6 | e04dd8ae497aa2f9a5585bcc | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37886 | 1 | 3a290349c5dcb488219496e2 | b6e90711a9325fbaee0dc2d2 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37886 | 1 | 53f1a2bef2a75ae9407f8790 | 14bc6728c1859ce1d016c60b | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37886 | 1 | 97f14371dfcceda72fbaca94 | 855803c80fb709dbce797fd7 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37886 | 1 | fa6f44f9a258d877bcaf3915 | 743f7f40292d138653c47190 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37916 | 1 | 3c0d46afdd7bf92799e96d3c | 53ae8bcfefa7460ba2cd4e97 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37949 | 1 | 148c3678fe753dc91ce4b975 | 231309221d46043552aa7c1f | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37975 | 1 | bcb9e0dc8efad1698ce6e31b | c1fc5af20af78dffba406e70 | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
| 37983 | 1 | 9790632d1d058f40d42d1683 | 779a4471653d2cb0ad275d9e | REPRODUCED | NOT_REPRODUCED | EXPECTED_MISSING | NO_PRODUCTION_LAYOUT_CONTACT |
