# Child 02 approved execution brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master
- Branch: fast/batch-demand-master
- Exact approved plan: docs/plans/2026-10-22/batch-demand-02-cursors.md
- Plan identity: commit:f684da0a316b8cfb560fe7ebb0a8817d8a2594f2
- Approval: explicit user `/fast-p docs/plans/2026-10-22/batch-demand-master.md`, 2026-10-22. The invocation authorizes these exact bytes despite their pre-approval status wording; do not alter plan text.
- Depends on: 01
- Product base: prior child terminal Code head, supplied at dispatch

## Complete child contract

# 02 有界候选读取基础：ES PIT 与 NEW 重试游标

状态：待批准。依赖：01。范围：7文件；ES查询与联系人只读仓储两个子系统。本步只增加接口/测试，不切换业务执行；可单独部署而不改变发送行为。

## 需求描述

提供按页拉取、可早停、可释放的候选源，避免from/size深分页和重试全量加载。不得发送邮件、更新联系人、改变选人业务规则；不加ES字段、DB索引或全局HTTP配置。

## 关键不变量

### Invariant I-1: 稳定原始游标
- Rule: 每个层级单独PIT；保留既有sortFields(level)排序，追加_shard_doc asc唯一尾键。下一页使用最后一个原始hit的完整sort数组，不以过滤后列表推进；track_total_hits=false，不先count，不使用from。层顺序沿scope冻结。
- Applies to: ExpertSearchService新增openCandidateCursor、ExpertSearchCursor.nextPage。
- Violation consequence: 同排序值丢人、重复、超一万页失败。
- 来源: K-outreach-raw-page-filter-contract

### Invariant I-2: 生命期和失败明确
- Rule: PIT keep_alive=5m，持有期间每60秒用size=0/match_none/track_total_hits=false搜索续期，保留最新pit_id但不改变数据游标；nextPage/续期/close在同一锁下。连接3秒、读取20秒。读超时、分片失败、PIT过期抛受控异常，绝不等同空页或静默重开续用旧sort。
- Applies to: 新游标打开、页读取、续期、取消/异常finally关闭。
- Violation consequence: 遗漏或快照漂移、长时间SMTP等待后游标失效。
- 来源: original

### Invariant I-3: 资源与并发有界
- Rule: 按需打开当前层，上一层关闭后再开下一层；close幂等，释放最新PIT并终止续期任务；关闭后无新请求。工厂维护活动游标注册表，应用销毁时全部关闭；请求体日志不得含鉴权。普通查页与统计可独立游标。
- Applies to: ExpertSearchService工厂及销毁hook、ExpertSearchCursor.close。
- Violation consequence: 查询上下文/线程泄漏。
- 来源: original

### Invariant I-4: NEW只读分页
- Rule: 新增参数化查询 findNewRetryPage(campaignId, upperId, afterUpdatedAt?, afterId?, limit)，WHERE current_status='NEW' AND id<=upperId，排序updated_at DESC,id DESC，后续游标用严格小于组合。upperId首取同campaign MAX(id)，limit=1..500。读取后发生状态更新不回到前页，新建高id留待下一次执行。不得改变原findAll接口供旧统计使用。
- Applies to: ExpertContactRepository新SELECT。
- Violation consequence: 动态offset跳人、重试列表无界或修改旧调用合同。
- 来源: K-batch-send-filter-retry-parity

## 现状审计

### ES候选
- E-02已核实线上7.14.2，PIT打开/零结果续期/关闭成功；深翻页未实测，须测试。
- ExpertSearchService.scrollExpertsFiltered:749-810可全扫；searchAfterExpertsFiltered:815-858只按orcidId排序，不能假定多文档相同orcidId时具有唯一排序；searchExpertsFiltered:671及sortFields:611是当前执行查询基线。
- 原始写入由01账本列出的发现、补全、回填、晋升供给；执行还会写operatorStatus导致集合收缩。新PIT不改任何写入方，也不把旧_source作为发送授权。
- 读取接口已有findByDocumentIdsStrict:886，供04实时回查，禁止使用吞异常版本把读失败当无档案。
- 官方接口与续期/唯一尾键依据见账本E-04。

### expert_contact
- V1:79定义id主键、UNIQUE(campaign_id,orcid_id)、updated_at；V85增加绑定字段。原findAll...:27返回全量，调用点/测试桩见账本。
- 写路径保持：ManualInitialOutreachService创建NEW，ConversationStateService.transition保存状态，ManualOutreachTxHelper投递结果与状态，SenderAccountAssignmentService绑定，Contact management/manual路径见账本write-paths。新增查询只读这些既有字段。
- 不声称跨MySQL/ES事务快照：并发修改导致离开本次读取范围的联系人留待后次，发送前重新验证；不使用长事务锁住联系人。
- X1：发送更新联系人/ES→游标继续后续页；X2：相同排序值/相同orcid不同docId→无漏页；X3：长间隔与取消→资源释放。

## 实现方案

1. 清单1/2新增 AutoCloseable 的 ExpertSearchCursor 和工厂（I-1/2/3），HTTP request callback由ExpertSearchService提供认证、sourceFields、toExpertProfile；为游标使用局部有超时的客户端，沿用原converter/interceptor，不改全局RestTemplate bean。保留旧search/scroll API和现有消费者。
2. 清单2持有原始hits的sort信息，nextPage返回有界profiles及耗尽标记；size由调用方指定1..500。拒绝缺sort、重复不前进游标、partial shards；首次请求空页才是耗尽。续期只维护上下文，不能获取/预读候选。
3. 清单3新增MAX(id)及keyset查询（I-4），命名参数绑定，读取仍返回ExpertContact实体，不引入未经验证的DTO投影。现有updated_at语义不改；测试覆盖同秒id尾键。
4. 清单4/5测试真实请求形状、11001条跨页、所有原始页被过滤时上层仍可继续、PIT id更新、关闭、续期失败。清单5中同时包含默认mock HTTP测试与esIt=true门禁的真实ES7.14测试（独立容器/隔离测试索引，测试方法创建并清理自己的索引，严禁生产URL）；用已有Testcontainers GenericContainer基础类，不加pom依赖。清单6沿现有mysqlIt模式用MySQL8.0.36容器验证同秒keyset、状态变化、新建高id。只验证DB逻辑，不虚构生产EXPLAIN性能结论。
5. 清单7只同步行号守卫，不扩大白名单；所有旧调用接口仍存在（I-4）。

## 变更文件清单

| # | 文件（相对仓库根目录） |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchCursor.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt` |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchCursorTest.kt` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactCursorRepositoryIT.kt` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` |

## 验收标准

- I-1：>10000、相同candidateValidatedAt、相同orcidId不同_id、缺业务排序时间均不漏文档；size<=500；首次和后续均无_count/from；完整sort尾键保持JSON数值类型。
- I-2：模拟等待超过5分钟且续期成功，下一页正常；续期失败后下次消费必报错；close后零续期；HTTP20秒超时可结束，不挂住发送线程。
- I-3：完成/早停/取消/异常/应用关闭都释放PIT；重复close不报错、不重开；同时统计和发送的游标ID不串用。
- I-4：同updated_at用id游标不重复；NULL首游标正确；空页结束；DB0写入；旧接口测试不改变期望。
- 命令：Java11 mvn test -Dtest=ExpertSearchServiceTest,ExpertSearchCursorTest,OperatorStatusWriteSeamGuardTest；Java11 mvn test -Dtest=ExpertContactCursorRepositoryIT -DmysqlIt=true；Java11 mvn test -Dtest=ExpertSearchCursorTest -DesIt=true（真实容器测试要求Docker可用；跳过不算通过）。

## 人工验收清单

### A-1: 长名单分页
- 前置条件: 独立测试ES7.14索引由ExpertSearchCursorTest对应测试夹具写入11001条，含相同orcid/相同排序时间；禁止指向生产。
- 操作步骤: 1. 运行游标黑盒测试；2. 查看请求记录与返回docId数量；3. 提前读完第1页后关闭再查看上下文。
- 预期结果: 完整遍历11001个不同docId；提前关闭只请求所需页，最终PIT关闭；没有from>=10000请求。
- 覆盖: I-1/I-3、X2、按页能力。

### A-2: 发送期间状态变化与NEW分页
- 前置条件: 测试MySQL相同updated_at的NEW联系人501条；当前maxId冻结。
- 操作步骤: 1. 读第1页；2. 将其中已处理联系人改为WAITING_REPLY，再新增1条NEW；3. 读后续页。
- 预期结果: 原有后页不因前页缩小而跳过；新高id不混入本次；未改写任何联系人。
- 覆盖: I-4、X1、must-NOT-change。

### A-3: 续期/故障/释放
- 前置条件: 测试ES连接代理可注入超时，游标时钟可控制；使用本地测试，不用真实邮箱。
- 操作步骤: 1. 等价推进6分钟并续期；2. 再取页；3. 注入PIT404/分片失败；4. 调用close两次。
- 预期结果: 正常续期不预取人；故障明确报错而非返回空名单；close幂等，后台任务停止。
- 覆盖: I-2/I-3、X3、安全边界。



## Global constraints

Read the master invariants I-1 through I-4 at docs/plans/2026-10-22/batch-demand-master.md. Authorized Files are exclusively the exact table in the child plan above. Preserve date-only compatibility, qualification rules, realtime send safety, old execution interpretation, and sequential interfaces. No production sends, release, migrations, schema changes, or unauthorized changes. Real SMTP prohibited; isolated tests only. Existing worktree application.yml remains committed baseline; unrelated main-worktree mail attachment timeout changes are preserved there and not imported.

Use execute-p against the exact approved child plan. Local implementation commit is explicitly authorized with subject `feat(fast-p): implement 02`. Do not run verify-p/review-p/fix-v; fast-p independent lightweight verifier follows. Exclude fast-p evidence from product commit. Any plan amendment or extra file requires PLAN_CONFLICT. Do not modify knowledge/docs outside fast-p execution report, as authorized-file boundary supersedes general documentation cleanup. Do not run builds/tests/formatters mid-flight; collect required commands after implementation, once final state is established. Smoke must exercise changed path; real converter replay for 01, real cursor containers for 02, browser surface for 03/06. Record exact commands, exits, skips and environment limits, never infer PASS.

## Baseline evidence

Java 11.0.15 Zulu and Docker server 29.8.2 observed available. Initial child 01 Maven baseline is controller-owned and its result will be recorded before dispatch. No whole-system baseline is claimed. Prior child interfaces are only those explicitly approved in their plans, not later product changes.
