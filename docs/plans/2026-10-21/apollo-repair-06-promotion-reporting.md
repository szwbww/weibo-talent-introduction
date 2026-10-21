# 06 · 晋升读侧统计对齐已持久投影

状态：可交付执行，尚未实施。修订：r2（2026-10-21）。基线：`962fcfe924ad7562a4ea917d174180df82ac3b5c`。依赖：05 完成并复验通过。

## 需求描述

可观察结果：运行中与结束后的“晋升”都纳入本运行已经PROJECTED的专家，避免已自动准入且投影成功仍显示0。

必须保留：自动准入不等于新建候选；投影待处理/失败不计新晋升，已存在候选/申请不计本次新建；传统非核验路径统计继续；不写回采集进度。

不做：把所有自动准入数硬塞进晋升；修改候选/申请/RAW文档；前端样式重做。

## 关键不变量

### Invariant I-1: 晋升按真实投影事件去重
- Rule：managed贡献仅ownerExecutionId匹配、activated、ENFORCE、admissionRevision非空且projectionState=PROJECTED的expertDocId；ALREADY_PRESENT/APPLICATION_PRESENT/PENDING/ERROR贡献0。source使用item.source_name，同doc同run计1。
- Applies to：Repository新只读查询→PromotionProgress.counts
- Violation consequence：已存在人物被重复当新晋升，或统计他人任务。
- 来源：K-admission-event-and-projection-counts

### Invariant I-2: 两路径按集合合并
- Rule：保留旧enrichment+ES候选存在/时间窗检查，返回docId→source集合；与managed已投影集合按docId做union，重叠只计1，source冲突优先本run verification ledger。不能max两个分组计数代替集合并集。
- Applies to：PromotionProgress.counts/merge
- Violation consequence：漏计互不重叠来源或双算同一人。
- 来源：K-admission-event-and-projection-counts

### Invariant I-3: 读侧不反写
- Rule：刷新TaskProgress与TaskExecution都复用同一count；不调用进度写服务，不持久修改resultSummary，不发Apollo/ES写。查询失败保留原结果并记录可诊断日志。
- Applies to：两个refresh重载/控制器读路径
- Violation consequence：异步覆盖采集进度或读请求改变业务状态。
- 来源：K-progress-log-batchonly-two-readers

## 现状审计

PromotionProgressService 已存在异步补统计，不能宣称完全没有。counts:74只通过 enrichmentJob.findDiscoveryDocuments 找本run doc，再检查ES discoveredAt在采集startedAt/finishedAt之间；findDiscoveryDocuments:31来自唯一expert_doc_id任务，insertIfAbsent:45对已存在记录不更新所属run。另已核实ExpertIndexWriterService.projectDiscoveryCandidate:903–908只更新candidateValidatedAt/updatedAt，保留RAW的discoveredAt。因此不能声称“投影发生在finishedAt之后就被时间窗漏掉”；可证明的是旧run归属、discoveredAt缺失/格式或本身不在窗口时旧读路径不能计数，仍须样本逐项验证实际原因。

CommitService.project:103–128明确区分PROJECTED/ALREADY_PRESENT/APPLICATION_PRESENT并持久markProjection。读取这一事件不会触发资格重算。TaskProgressController:56/136与TaskExecutionController:106都调用refresh；不改控制器或HTML。

受影响store：verification只读；enrichment只读；ES仅_mget不改mapping/写路径；task_execution/task_progress_log只读。

共享表结构、全量读写路径见 [证据审计 E-6/E-7](apollo-repair-retention-evidence.md)，作为本计划规范性组成部分，执行/复验不可跳过。本子计划只修改下表白名单；其余路径为回归边界。

## 实现方案

1. **台账集合（I-1）**：在Repository增加按executionId读取满足条件的docId/source的方法，使用run_batch索引联run.mode，不按finishedAt过滤投影时间。不存在managed run时返回空。重复doc去重，source冲突确定选该run最新item.id，仅对满足条件的条目处理。
2. **集合合并（I-1/I-2）**：PromotionProgressService注入verification repository；先形成旧enrichment+ES集合，再union新ledger集合，managed条目无需依赖旧job归属或ES时间解析。旧路径中与managed本run条目重叠但projection非PROJECTED的记录不能凭存在性计新晋升；通过同次查询返回本run已激活ENFORCE条目的managed docIds及满足I-1的projected子集，先从legacy集合排除这些managed ids，再union projected。无managed归属的旧路径保持原窗口语义。
3. **保留已存同步计数下界（I-2/I-3）**：merge保留现有 max(原source.promoted, 去重union计数)兼容历史无doc级账的同步结果，不做相加；文档注明这是旧数据下界兼容，不宣称能从匿名聚合恢复精确集合。原聚合下界兼容可能使最终显示高于可核实集合；此例必须在对照报告标LEGACY_AGGREGATE_FLOOR，不得宣称最终数完全由台账证明。现代managed run用台账和集合校验；若原计数高于可验证集合，要在验证报告列差异，不能擅自降历史计数或伪造doc。
4. **测试（I-1/I-2/I-3）**：PromotionProgressServiceTest覆盖job归属旧run、finishedAt之后投影、同人legacy+managed、不同人两来源、非PROJECTED五种状态、ES故障、两refresh重载。RepositoryReportingIT覆盖owner/source/ENFORCE/activated/revision过滤和去重。使用Controller现有测试验证接口集成，不更改其源码。

## 变更文件清单

| 序号 | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` | 只读managed归属/投影集合 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressService.kt` | 集合合并读侧统计 |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressServiceTest.kt` | 双路径去重/时序/故障 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationReportingIT.kt` | 投影查询条件 |

共 4 个文件；1 个业务子系统；不修改前端、数据库 schema 或 ES mapping。新增测试数据放入列出的测试文件，不另建 fixture 文件。

## 验收标准

- I-1：1个PROJECTED+1个ALREADY_PRESENT得到自动准入2、新晋升1；仅PENDING投影新晋升0；AUDIT/别的run不计。
- I-2：同人双路径1；两个不同人双路径2；历史job归属另run但本runPROJECTED仍1；finishedAt之后PROJECTED仍1；managed非PROJECTED不被legacy存在性补计。
- I-3：运行中/已结束接口结果相同；读取前后SQL写入计数与ES写入均0；模拟依赖不可用保留原进度。
- 生产只读复验：先重新查27254投影，若仍203个不同doc且全部PROJECTED、无新变更，读侧managed贡献应203；不要仅用旧截图或原始resultSummary=0当最终API证据。

### 执行命令与通过条件

以下命令在仓库根目录执行；本次只写计划，未运行这些测试。新增用例写入白名单测试文件，复用已有MockRestServiceServer/Mockito/Testcontainers；不新增Mock服务、测试后台或运行时调试接口。

```sh
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryPromotionProgressServiceTest test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationReportingIT test
```

通过条件：指定测试实际执行，失败0、错误0、必要用例跳过0；检查本次target/surefire-reports时间戳，旧报告不算通过。数据库IT明确检查Docker可用，不得用环境不可用跳过。测试结果与该步代码diff一起交复验；人工清单另行完成。

## 人工验收清单

### A-1: 准入与晋升不同
- 前置条件：测试run两人均自动准入，其中1个PROJECTED、另1个ALREADY_PRESENT。
- 操作步骤：1. 打开运行中进度；2. 完成任务后打开执行详情。
- 预期结果：两个入口自动准入2、晋升1；无重复计数。
- 覆盖：I-1/I-3

### A-2: 异步和旧job归属
- 前置条件：测试run在采集结束后才PROJECTED一人，该人的enrichmentjob归属旧run；另有传统路径独立1人。
- 操作步骤：1. 查看该run执行详情；2. 刷新两次。
- 预期结果：晋升2，重复刷新仍2；没有改写任务resultSummary或ES文档。
- 覆盖：I-2/I-3

### A-3: 线上只读核对
- 前置条件：部署后重新只读确认27254投影条目和独立doc数；若当前状态与历史不同先记录差异。
- 操作步骤：1. 请求该run的核验summary；2. 请求任务detail；3. 对比doc去重后的PROJECTED数。
- 预期结果：投影台账203且无其他计数差异时managed贡献203；投影失败/已有候选不增加新晋升；任务原始日志保持。
- 覆盖：I-1/I-2/I-3

