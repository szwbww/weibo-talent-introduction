# 全部待审核重审修复 — 本地实现记录

授权：用户要求直接修改 main，不发布；允许直接更新线上索引。基线为本次对话批准的修改范围，见 scope.md。实施基线 HEAD：`9f29232489860a2931bc0fc832d77eb5110daea8`。本记录保留提交前的核验收据；用户随后授权仅提交本地 main。

## 实现

| 目标 | 写入及读取路径 | 证据 |
| --- | --- | --- |
| 不依赖列表加载，重新审核全部待审核 | ALL_PENDING 由服务端覆盖为 RAW/discovered/NEEDS_REVIEW；默认 AUDIT/REUSE，仍需固定名单并确认 | DiscoveryRecheckService.kt:130；index.html:1304 |
| 避免慢论文查询撑过 ES scroll 有效期 | SCANNING 只存 profile/准入基线，scroll 关闭后 EVIDENCE 每 50 人准备证据，完整结束才 READY；中断保留输入但不可启动 | DiscoveryRecheckService.kt:143、221；DiscoveryVerificationRepository.kt:449 |
| 论文查询使用索引且保留匹配语义 | 三个生成哈希列、四个索引；各标识分支索引检索再 UNION 去重；原执行来源仍优先，历史查找仍保留 20 条上限及超限报错 | V163；DiscoveryPaperArchiveRepository.kt:73；PaperVerificationEvidenceService.kt:27 |
| 防重复准备 | 既有 MySQL 命名锁事务内检查当前操作者活动重审；相同配置复用、不同配置明确 409；其他操作者准备中拒绝并行准备，不隐藏排队 | DiscoveryVerificationRepository.kt:398；DiscoveryExecutorConfig.kt |
| 页面退出恢复 | GET /active 从会话取操作者；重新打开恢复服务端 READY/执行中状态及原名单，确认仍使用冻结 hash/revision；不自动开始或取消 | DiscoveryRecheckController.kt:49；discovery-recheck.js:53、124 |
| 筛选列表不再每页同步全量查询 | 有 decision/issue 筛选走有界 SQL 列表快照；后台 scanAll，READY 后按 ordinal 分页；未完成/失败 total=null，前端不显示为零人 | DiscoveryReviewController.kt:58；DiscoveryReviewQuerySnapshotService.kt:36、74；app.js:3154 |
| 状态与进度真实 | 保留 scanned 与已生成可执行项数量；阶段 QUEUED/SCANNING/EVIDENCE；TaskProgressStore 更新绑定真实 executionId | DiscoveryRecheckService.kt:143、287；DiscoveryVerificationQueryService.kt:146 |

列表快照：最多 32 份，10 分钟过期；单执行线程、队列 8。同条件运行中复用，READY 翻页不重算，显式刷新重建。重启后 READY 可读，未完成快照明确标记失败。列表快照不作为准入写入依据。

准入规则、Apollo 免费/付费策略、论文解析与资格算法、人工/联系保护保持现有逻辑。全部待审核不包含已批准、暂缓或拒绝者。重审输入留存，不删除论文或旧执行记录；未实现失败输入自动续跑，失败需用户重新准备。

## 线上操作（已完成）

只应用 V163 的生成列和四个索引，无 WAR 上传、无服务重启、无专家/准入/任务状态修改。生产 MySQL 5.7.41；本地 MySQL 5.7.44 验证 V163/V164/V165 建表、索引与查询语法。

- 原文档案：25,348 条，前后数量一致。
- 对 id、metadata_json、outcome_json、analysis_json 计算 CRC 汇总：前后均为 54,340,932,242,315。这是操作前后内容一致性检查，不是密码学全文件校验。
- 添加生成列成功；首次索引添加因元数据锁等待 5 秒超时，未终止现有查询。只重试索引语句，锁等待预算 30 秒，成功耗时 37.522 秒。
- 四个只读 SELECT-id 探针，新旧结果全部一致：三条真实 DOI/来源键和一条不存在键。旧查询总计 9,143.12 ms，新查询 97.11 ms；包含 MySQL CLI 启动/连接耗时。不能推算整批重审时长。
- 已存在原文检查：11,932 份 AVAILABLE 对象的路径及文件大小正确，总计 28,120,040,700 字节；未逐文件重算 SHA，不宣称内容完整校验。

详细数据：production-index-evidence.json。生产仍运行旧 SQL，新查询收益需后续发布应用才能由业务使用。

## 后续发布注意（本次不执行）

生产未发现 flyway_schema_history，现有环境采用手动 SQL。V163 已直接应用，不得再次盲目执行。部署新应用前需核对 V163 的三个生成列/四个索引，应用尚未执行的 V164、V165；不得擅自启用 Flyway 或对历史迁移进行补跑。新建隔离数据库正常按迁移序列执行。

V164 保存准备输入；V165 保存可过期列表快照。旧应用兼容这三项追加结构，回退应用时不需要删表或删索引。

## 测试

最终构建：PASS；定向 JVM 回归按类取末次结果，共 100 tests，0 failures，0 errors，0 skipped。全量 JS：1,851 passed，0 failed，0 skipped。未运行全仓 JVM 测试；不能据此声称全仓 JVM 全绿。详细逐类结果见 final-test-results.json。

- JS 核心回归：99 passed，0 failed，0 skipped（重审、审核列表、任务活动中心）。
- 相关资源/页面结构回归：40 passed，0 failed，0 skipped。
- Node 语法检查 app.js / discovery-recheck.js：通过。
- MySQL 5.7.44：V163 索引查询及 EXPLAIN、V164/V165 结构成功。
- 初次阶段转换测试暴露 PREPARING 进度更新前置条件缺失，已补齐同阶段单调更新约束；随后千人运行完成，旧“扫描失败仍生成 500 项”断言按新双阶段合同改为“scanned=500、可执行项=0”。新增 controller 快照依赖后，旧 issue=ANY 同步服务测试更新为异步 READY 快照合同。保留失败历史，不把中间失败当最终通过。

## 待人工验收

后续发布后检查：全部待审核入口、固定名单阶段和人数、退出重进恢复、重复点击不新增任务、显式取消、超 10,000 人分页、失败不显示零人、快照刷新时间、两种执行模式/证据来源文案。

本次未部署，线上 UI 人工验收待后续进行。保留已有未提交的 CLAUDE.md、旧计划、releases.json、application.yml 等其他工作；fix-v 使用的四篇知识条目仅追加本次使用元数据，保留原正文与已有修改。未提交/推送仓库。

## fix-v 核验合同与检查表

模式：MECHANICAL_REPAIR（用户已直接授权修复）；本节以本次对话和 scope.md 为基线，不把先前 discovery-recheck-master/repair.md 的旧五文件修复范围当成本次范围。旧计划中的原句展示、严格 IDS 参数、AUDIT 业务写 0、人工保护继续保留；本次显式批准增加 ALL_PENDING、名单恢复、索引和列表快照，冻结名单不因外层筛选改变而被丢弃。

| ID | 检查 | 证据 |
| --- | --- | --- |
| R1 | ALL_PENDING 参数服务端固定；不要求列表成功 | RecheckService:130；ServiceTest；FlowIT 实际 ALL_PENDING HTTP→READY |
| R2 | 论文查询不发生在 scroll 回调内；完整扫描后才生成执行项 | RecheckService:221–259；FlowIT partialFreeze=FAILED/scanned500/items0/notClosed |
| R3 | 相同配置复用，活动任务配置冲突拒绝；会话隔离恢复入口 | VerificationRepository:398–446；FlowIT；RecheckControllerTest |
| R4 | 页面离开/重新进入恢复原任务/名单/hash；拒绝重复点击 | discovery-recheck.js:53、124、137；discoveryRecheck.test.js |
| R5 | 跨 10,000 人分页完整、不重复扫描；未完成和失败不伪装零人 | ReviewQuerySnapshotServiceIT 的 15,340 人完整扫描/分页、失败/重启用例；discoveryReview.test.js |
| R6 | 旧写入无需认识生成列；匹配结果、原来源优先和查找限额不变 | ArchiveRepositoryIT；PaperVerificationEvidenceServiceTest；MySQL5.7DDL/EXPLAIN；四组生产 SELECT-id 新旧一致 |
| R7 | AUDIT不外调、不写业务；ENFORCE仍原提交保护；取消不撤销已提交 | FlowIT 1001 人 AUDIT、人工保护、取消/提交双顺序、projectionOnlyRetry、实际 parser→HTTP 原句回归 |
| R8 | 仅线上追加索引、无部署；文件保留 | production-index-evidence.json；操作命令与原文文件数量/大小检查 |
| R9 | 有界缓存/明确过期；进度按 executionId；18 资源键一致且 DOM 存在 | SnapshotService:36–100；RecheckService:143、287；前端全量测试；四篇知识审计探针 |

状态机：PREPARING 内 QUEUED→SCANNING→EVIDENCE，完整后 READY→RUNNING→COMPLETED；失败 FAILED；重启中断 INTERRUPTED；取消经 CANCELLING→CANCELLED。READY恢复继续等待用户确认；失败输入不自动重跑。列表快照 QUEUED→RUNNING→READY/FAILED，READY重启可读，未完成重启显示失败，用户刷新才重建。

写路径：论文派生索引由 MySQL 随既有 metadata 写入维护；名单输入/执行项/任务进度分别保存；列表快照只写新读缓存表。原准入和候选写入仍由既有 worker/commit 路径控制。候选同步重试仍只修投影。

Accumulation check ✅；State-machine check ✅；Cross-plan check ✅；Deleted code ✅；No extras ✅；Scope compliance ✅。R1–R9 机器核验均通过，无 P1/BLOCKED 遗留；人工验收 PENDING。本次为初次核验，无旧 P1 修复轮次；未生成新修复计划。机器结论 PASS 不等于线上人工验收完成。

## 最终命令与产物

环境：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`，`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`。

1. `mvn -q test -Dtest=DiscoveryRecheckServiceTest,DiscoveryReviewCountsServiceTest,DiscoveryReviewQuerySnapshotServiceIT,DiscoveryPaperArchiveRepositoryIT,PaperVerificationEvidenceServiceTest,DiscoveryRecheckFlowIT,DiscoveryReviewControllerTest,DiscoveryRecheckControllerTest`：中间执行 exit 1，59 项中仅旧 issue=ANY 控制器转发断言失败。千人完整 FlowIT 等其余通过。原始日志 `/tmp/recheck-tests-final.log`，保留逐类结果 jvm-runtime-results.json。
2. 更新上述旧断言并完成最终新增 auth/时区检查后，`mvn -q package -Dtest=DiscoveryReviewControllerTest,DiscoveryRecheckControllerTest,DiscoveryReviewQuerySnapshotServiceIT,DiscoveryVerificationQueryServiceTest,TaskTypeCatalogTest,TaskActivityControllerTest -DskipNodeTests=true`：exit 0。日志 `/tmp/recheck-package-final.log`。末次结果覆盖旧失败类，其他未变更链路沿用上一次实际成功结果，去重共 100 项 JVM 用例；不重复计算重跑项。
3. `node --test src/test/js/*.test.js`：exit 0，1,851 项全过。日志 `/tmp/recheck-js-all.log`。此后未修改 JS 产品/测试源码；package 跳过重复 Node 执行。
4. `node --check src/main/resources/static/app.js`、`node --check src/main/resources/static/discovery-recheck.js`、`git diff --check -- src docs/audits/2026-10-26-recheck-repair`：exit 0。
5. 本地 WAR 的四个本次静态资源及三项迁移逐字匹配当前源文件；产物 SHA 和大小见 build-artifact.json。临时 MySQL 5.7 容器已清理。

核验期间模式为 MECHANICAL_REPAIR；没有新增行为修复轮次。四篇被使用的知识条目只更新 hit_count/last_used/last_source。人工/线上验收保留 PENDING；应用未部署，未重启，未提交/推送。

## 本地提交范围

按后续用户授权，提交本次产品代码、迁移、测试及本目录核验记录。四篇知识条目含先前未提交改动，为避免夹带，知识元数据仍留工作区；其他既有未提交内容同样保留。没有推送或发布。
