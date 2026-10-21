# 03 · 分页续查、查询留档与成功详情复用

状态：可交付执行，尚未实施。修订：r2（2026-10-21）。基线：`962fcfe924ad7562a4ea917d174180df82ac3b5c`。依赖：01、02 完成并复验通过。

## 需求描述

可观察结果：某查询达到现有 5 页上限后，其余已排查询仍执行；留档能解释每一步停在哪里。同一执行中已成功获取的同一 Apollo person 详情可复用。

必须保留：5 页/查询、100 条/页、最多4个姓名变体；搜索完整性、同名冲突保护；成功候选并集、真实请求计数、原论文样本目录和保留周期。

不做：解除分页上限；把14条续查机会宣称14人准入；全局长期缓存；缓存失败响应。

## 关键不变量

### Invariant I-1: 截断不等于完整
- Rule：达到页上限时记录 TRUNCATED，推进到下一条固定查询；任一必需查询截断，search.complete=false，不能被最后一条无结果查询改成 true。broadComplete 仅首个广搜自然结束时为 true。
- Applies to：Worker search merge/finish/restart、Rules完整性检查
- Violation consequence：截断被掩盖导致同名误准入。
- 来源：K-search-error-must-not-clear-cursor

### Invariant I-2: 查询快照与游标一致
- Rule：queryIndex 对应固定 queries；每页响应、诊断与下个游标在同一 completeAttemptAndProgress 事务提交。新增 surname fallback 只沿现有追加规则，不重排原查询。
- Applies to：Worker save/complete 与恢复
- Violation consequence：重启后重复/漏查或索引指向不同查询。
- 来源：K-institution-rule-persisted-context

### Invariant I-3: 复用仍可追溯
- Rule：仅同 ownerExecutionId/personId、已完成200 OK DETAIL、响应能按现有ApolloCandidate解析、personId相等且有来源attempt的成功结果可复用；不得跨运行或复用错误。复用写来源 attemptId 与哈希，不伪造 HTTP attempt。
- Applies to：Repository lookup、Worker detail merge
- Violation consequence：缓存串人、历史错误扩散、计费统计失真。
- 来源：original

### Invariant I-4: 诊断不是准入依据
- Rule：evidence_json 只新增一个 diagnostics 根对象，schemaVersion=1；缺失=NULL/NOT_RECORDED，原 evidence/search/progress 保持。诊断丢失、截断不能增强准入。
- Applies to：Worker、QueryService、Gate未知字段兼容
- Violation consequence：可观察性字段影响资格或旧记录反序列化。
- 来源：K-verification-cached-evidence-replay

## 现状审计

Worker:244–280 在 page.hasNext 且达到 maxPagesPerQuery 时直接 finish/return，因此后面的机构查询不执行。ApolloSearchEvidence.merge 会以最后查询自然结束设置 complete，修复必须一起审计。

现有详情去重只针对本 item.detailsCompleted；跨 item 相同 person 仍发请求。任务27254中成功详情2115次/2084个唯一ID，31次重复，30组保存响应一致。这是历史重复量，不是保证未来固定减少31次。

新增诊断只放已有 evidence_json，一个根字段；attempt 表保持原始请求账。QueryService 只在单条详情返回诊断，Gate 忽略诊断；ReplayTool.freeze 原样冻结该 JSON。论文 samples 文件不写、不变更保留策略。

共享表结构、全量读写路径见 [证据审计 E-6/E-7](apollo-repair-retention-evidence.md)，作为本计划规范性组成部分，执行/复验不可跳过。本子计划只修改下表白名单；其余路径为回归边界。

## 实现方案

1. **查询诊断（I-1/I-2/I-4）**：扩展01的 `VerificationDiagnostics.kt`，新增 WorkerDiagnostics(schemaVersion=1, queries, detailReuses)，在 `VerificationWorkerEvidence` 追加 nullable diagnostics。queries 按固定 queryIndex 记录 queryHash、kind、pagesReceived、totalEntries（末次供给值）、candidateCount、state（PENDING/RUNNING/EXHAUSTED/TRUNCATED/ERROR）、stopReason、lastAttemptId；查询原文仍只保存在既有 queries/request_json。最多32条诊断，超过时 totalQueryCount/truncated=true，原查询与规则证据不裁剪。
2. **续查实现（I-1/I-2）**：修改 Worker 和 `ApolloSearchEvidence.merge` 的完整性维护。第5页仍有下一页时，先设置该查询 TRUNCATED、next queryIndex+1/page=1，再原子写本页结果，不立即 finish；循环执行剩余查询。业务完整性只从已有progress.finishedQueryKeys与固定queries计算：只有自然读完的queryIndex进入finishedQueryKeys，且全部固定查询均在该集合中，才允许search.complete=true；diagnostics绝不作为条件。循环结束若仍有未自然完成的查询，直接PENDING+SEARCH_INCOMPLETE+SEARCH_PAGE_LIMIT并结束，不为已确定不完整的搜索继续额外DETAIL；不把截断索引放入代表自然完成的 finishedQueryKeys。旧payload无诊断仍用业务游标/finishedQueryKeys判断；原search.complete/broadComplete=true且无新增截断时保留旧事实，不能因缺diagnostics而重判。正在执行的页若因旧进程崩溃留下page=6且前5页已完成，则按已存page与上限推进下一查询，不发送第6页；未确认的页仍重试原页。历史已 COMPLETED 的55项不在此步自动重排队。
3. **详情复用（I-3）**：Repository 增加只读批量查同 execution 的成功 DETAIL；使用 run_batch/item 索引关联，按 personId 批量映射，仅当前需详情候选，避免每人再全表扫。默认解析同版本成功结构；可用 response_json 为事实，以 personId 校验，原 response_sha256 是HTTP原文哈希不能拿解析后JSON重新计算后宣称不一致。reuse只记录sourceAttemptId/sourceItemId/personId/providerResponseSha256；原响应仍在sourceAttempt.response_json。不新增解析JSON二次哈希或缓存实体。原hash只能用于引用追踪，不宣称重新验证了已丢弃的原HTTP正文。Worker 按同一 detail 合并函数处理命中与HTTP结果。并发时允许两次在途请求，不增加分布式 singleflight 系统。
4. **API 与测试（全部不变量）**：QueryService 的单条详情增加 nullable diagnostics；复用列表最多200条，totalReused/truncated 标志。添加分页中断重启、第5页后执行机构查询、后续空页不洗掉截断、复用不增HTTP请求、跨run/错误不复用、候选ID冲突仍阻断的测试。诊断范围内的候选只记录ID和引用，不复制完整人物字段。

## 变更文件清单

| 序号 | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/VerificationDiagnostics.kt` | 查询/复用诊断 DTO |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | 续查、原子游标、详情复用 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt` | 搜索证据完整性维护 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` | 同运行成功详情批量读取 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` | 单条诊断输出 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | 分页/重启/复用 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt` | 截断保持PENDING |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt` | 复用范围/查询 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` | 诊断兼容 |

共 9 个文件；1 个业务子系统；不修改前端、数据库 schema 或 ES mapping。新增测试数据放入列出的测试文件，不另建 fixture 文件。

## 验收标准

- I-1：第1查询第5页 hasNext=true，第2机构查询仍发出；最终仍 SEARCH_INCOMPLETE，不能 APOLLO_CLEAR。
- I-2：每个持久响应都有对应新游标；第5页完成后崩溃恢复不再发送第6页，不改变固定queries；原姓氏回退可按原规则追加。
- I-3：同run顺序处理同person两个item共1次DETAIL；跨run共2次；422不缓存；API真实HTTP次数与attempt一致。
- I-4：删除 diagnostics 再跑Gate，结论相同；32/200条上限出现截断标志；旧证据可读取；论文样本目录无改动。
- 集成：HTTP→attempt+evidence同事务→重启→QueryService→Gate；保留候选并集与冲突检测。

### 执行命令与通过条件

以下命令在仓库根目录执行；本次只写计划，未运行这些测试。新增用例写入白名单测试文件，复用已有MockRestServiceServer/Mockito/Testcontainers；不新增Mock服务、测试后台或运行时调试接口。

```sh
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationRepositoryIT test
```

通过条件：指定测试实际执行，失败0、错误0、必要用例跳过0；检查本次target/surefire-reports时间戳，旧报告不算通过。数据库IT明确检查Docker可用，不得用环境不可用跳过。测试结果与该步代码diff一起交复验；人工清单另行完成。

## 人工验收清单

### A-1: 分页续查
- 前置条件：Mock广搜5页均hasNext=true，固定队列另有一条机构查询返回1人。
- 操作步骤：1. 执行核验；2. 查询item详情和请求账。
- 预期结果：广搜5次、机构1次；第1查询TRUNCATED；最终PENDING、SEARCH_PAGE_LIMIT；候选已留存。
- 覆盖：I-1/I-2

### A-2: 成功复用
- 前置条件：同run两个item都需同person详情，Mock仅准备一次200；另一run同person另备200。
- 操作步骤：1. 顺序运行3项；2. 查看attempt与detailReuses。
- 预期结果：首run1次DETAIL且第二项有sourceAttemptId；另run另1次；无虚构HTTP账。
- 覆盖：I-3/I-4

### A-3: 历史与留档保留
- 前置条件：有旧payload无diagnostics；论文samples目录已有包及hash清单。
- 操作步骤：1. 查询旧item；2. 对照目录hash；3. 检查现有姓名变体和同名样本。
- 预期结果：旧诊断NULL；样本hash不变；上限仍5页/100条/4变体，同名歧义仍不准入。
- 覆盖：I-1/I-4；保留规则和论文样本

