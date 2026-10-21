# 02 · 额度故障暂停与受限恢复

状态：可交付执行，尚未实施。修订：r2（2026-10-21）。基线：`962fcfe924ad7562a4ea917d174180df82ac3b5c`。依赖：01 完成并复验通过。

## 需求描述

可观察结果：明确额度耗尽后暂停 DETAIL 通道，避免每个人继续撞同一故障；额度恢复后允许单次探测再恢复。历史 422 可按冻结清单受限补跑。

必须保留：SEARCH、ORCID、直接准入与已准入投影继续；不恢复系统每日 500/每人 12 次额度；租约、人工保护、请求计数和旧留档不变。

不做：自动购买额度；自动补跑全部 ERROR；把历史 REQUEST_INVALID 批量改名为额度不足。

## 关键不变量

### Invariant I-1: 通道故障不是人不合格
- Rule：明确 APOLLO_CREDITS_EXHAUSTED 的 DETAIL 失败保留证据和游标，item=RETRY_WAIT、outcome=NULL、route=NONE；不计入 transient_attempts。
- Applies to：Worker.retry / Repository.reschedule
- Violation consequence：丢失可恢复的人，或冒充正常不匹配。
- 来源：original

### Invariant I-2: 跨进程单探测
- Rule：复用现有 Apollo 数据库锁；仅当持久 attempt 明确 DETAIL 额度耗尽后启动 1800 秒冷却，冷却中不发 DETAIL；每窗口最多一个新 DETAIL 探测，200 OK 后开放。冷却不等于正常请求额度限制。
- Applies to：reserveAttemptWithBudget、进程重启、并发 worker
- Violation consequence：重启失效/请求风暴，误限流其他通道。
- 来源：original

### Invariant I-3: 恢复必须逐项 CAS
- Rule：仅明示 executionId/itemIds/manifestHash 的历史 ERROR 422 项可恢复；运行已结束且核验无活动项，未准入、未投影、无租约、SQL中冻结身份/审核状态未变。恢复只排队，不保证当前RAW事实未变；正常Worker提交前仍由prepare/commit核对当前RAW及人工保护。只恢复原搜索/详情游标，不强制 DECIDE。
- Applies to：新受限重排队方法、ReplayTool apply-errors
- Violation consequence：覆盖人工结论、跳过必要 HTTP、产生重复准入。
- 来源：K-verification-cached-evidence-replay

### Invariant I-4: 历史账不可重写
- Rule：不删除/覆盖旧 attempts，不归零 request_count，不迁移 run/batch；每次补跑追加新 attempt。未经确认的 264 条保持原错误证据。
- Applies to：恢复、探测、重试、详情读取
- Violation consequence：失去前后对照依据。
- 来源：K-discovery-admission-read-write-parity

## 现状审计

现有 Worker 的 authBlocked 为进程内全局认证阻断，不能拿来表示额度耗尽：tick 顶部返回会连投影一起停。Repository.reserveAttemptWithBudget 已有跨进程 GET_LOCK，但目前 Apollo 配额参数被旁路。attempt 的 provider/requested_at 索引可支撑通道检查。

ReplayTool + requeuePendingForEvidenceReplay 只接受 COMPLETED/PENDING，且只允许 progress.stage=DECIDE，不适用于这批 ERROR422。需新增独立命令与方法，不放宽原命令。涉及写 store 仍只有 verification run/item/attempt；admission 仅加锁检查、不在恢复工具中写入。

共享表结构、全量读写路径见 [证据审计 E-6/E-7](apollo-repair-retention-evidence.md)，作为本计划规范性组成部分，执行/复验不可跳过。本子计划只修改下表白名单；其余路径为回归边界。

## 实现方案

1. **暂停/探测（I-1/I-2）**：修改 `DiscoveryVerificationRepository.kt`：在现有 Apollo 锁内为 DETAIL 检查已提交额度故障 attempt。使用持久账本确定最近故障、故障之后的成功及探测预约。按预约序号/请求时间排序，故障前已在途的晚完成成功不能解除暂停；仅故障之后预约、200 且 result_code=OK 的 DETAIL 成功解除。未完成的探测预约也占本窗口；崩溃后的窗口到期可再次探测。以 max(故障完成时间, 最新探测预约时间)+1800 秒确定 nextProbeAt，数据库时间按现有 UTC helper。锁内不发 HTTP。1800秒是本方案拟定的故障冷却值，不是Apollo官方额度/速率上限；故障前已在途的请求允许正常结束，不承诺取消已发请求。当前按部署共用单一Apollo凭据的通道处理，不跨其他provider。
2. **Worker 延后（I-1/I-2）**：`DiscoveryVerification.kt` 的 VerificationBudgetReason 新增 CREDITS_WAIT，reservation 增加可选 retryAt；Worker内部BudgetDeferred同步增加可选retryAt，reserve抛出时携带，catch时消费，不能丢掉后退化为固定60秒；`DiscoveryVerificationWorker.kt` 根据该结果 reschedule。阻止发出时不插 attempt、不增请求计数，保留 progress。额度错误进入 RETRY_WAIT；SEARCH/ORCID 不查 DETAIL 熔断条件，tick 的原认证保护不改。不新增后台定时器或内存缓存。QueryService 增加 APOLLO_CREDITS_EXHAUSTED/CREDITS_WAIT 的中文原因标签（额度不足，等待恢复）。
3. **受限恢复（I-3/I-4）**：在 `DiscoveryVerificationReplayTool.kt` 增加 `apply-errors`，沿用现有 freeze/load/manifest 校验与 0700/0600 文件权限。`DiscoveryVerificationRepository.kt` 新增独立事务方法：逐项比对 frozen item、snapshot/evidence/reasons 哈希及 admission revision；仅 ERROR，末次 APOLLO DETAIL HTTP422（REQUEST_INVALID 或新未知码/额度码），activatedAt 非空、admissionRevision=NULL、projection=NOT_REQUIRED、无身份/事实/人工保护。拒绝缺游标/损坏证据；不猜恢复位置。通过项 generation+1，state=QUEUED，outcome=NULL、route=NONE、qualification=NOT_EVALUATED，清 completedAt/lease/nextAttemptAt，reason 清空但原值已冻结，证据与游标保持；请求历史不动。一次最多 100 项，重复 apply 返回 STALE_OR_ALREADY_APPLIED。新增apply-errors不得复用expected(before)，因为现有expected会调用replayEvidence强制stage=DECIDE；构造ReplayExpected时使用原item.evidenceJson。拒绝非法stage；本次恢复白名单只允许APOLLO_SEARCH、已存在search/queries且末次失败personId仍属于未完成详情候选。保留旧 apply 的行为和参数。
4. **历史操作规程（I-3/I-4）**：先冻结 264 项到服务器受限目录，清单可拆 100/100/64；先单条受限恢复判断当前额度。其余批次只在前一批 settled 后执行。额度仍不足则停在 RETRY_WAIT，不触发全批强行重试。工具本身不携 API key、不发 HTTP，HTTP 只由正常 Worker 发出。是否执行生产补跑另按用户部署/恢复指令，本计划编写不执行。
5. **测试（I-1/I-2/I-3/I-4）**：RepositoryIT 覆盖双 worker 竞争只有一张探测 permit、冷却中 0 次 DETAIL、重启读账恢复、晚到旧成功不解除；WorkerTest 覆盖队列继续直接准入/投影与 ORCID；ReplayToolTest 覆盖 manifest 重复、过期、保护、原 stage 保留。

### 新恢复命令契约（I-3/I-4）

只扩展现有CLI的命令白名单，不新增脚本服务。`apply-errors --manifest <绝对路径> --manifest-sha256 <sha256>`只接受这两个参数，继承load校验；生产凭据仍由既有DB_URL/DB_USERNAME/DB_PASSWORD环境传入，禁止输出或写报告。先用现有freeze固定executionId与itemIds，工具不按错误码自动选择全量人。

输出`apply-errors.json`包含layer=QUEUE_ONLY、逐item receipt、queued总数，不含原人物字段。有效项QUEUED；过期/重复STALE_OR_ALREADY_APPLIED；运行未静止RUN_NOT_SETTLED；不合条件INELIGIBLE；人工保护PROTECTED_ADMISSION或PROTECTED_REASON。业务拒绝返回报告，输入/连接/事务失败沿现有退出码2且不打印敏感异常。旧apply继续只改PENDING的DECIDE阶段。

## 变更文件清单

| 序号 | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerification.kt` | 延后原因及 retryAt |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` | 通道许可和独立恢复 CAS |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | 额度等待恢复 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt` | 新增 apply-errors 命令 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` | 新错误原因标签 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt` | 并发/恢复事务 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | 通道与请求计数 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayToolTest.kt` | 清单保护 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` | 原因标签 |

共 9 个文件；1 个业务子系统；不修改前端、数据库 schema 或 ES mapping。新增测试数据放入列出的测试文件，不另建 fixture 文件。

## 验收标准

- I-1：额度失败后 outcome=NULL、route=NONE、RETRY_WAIT，证据与游标保留；transient_attempts 不增。
- I-2：两个服务实例同时争用只获 1 个探测许可；1800 秒内新增 DETAIL=0；下一窗口至多 1；旧在途成功不解除；SEARCH/ORCID 可继续；没有恢复内部每日/每人配额。
- I-3：保护项/已准入项/运行中/manifest 不符全部 0 写入；有效项只恢复原 stage；旧 apply 仍只接受 PENDING。
- I-4：恢复前后旧 attempt 哈希、run/batch、request_count 完全相同；新 HTTP 才增加计数；重复 apply 无副作用。
- 集成：422→等待→进程重启→单探测200→继续剩余详情→正常 Gate/Commit；实际补跑成功人数作为结果，不设虚构目标。

### 执行命令与通过条件

以下命令在仓库根目录执行；本次只写计划，未运行这些测试。新增用例写入白名单测试文件，复用已有MockRestServiceServer/Mockito/Testcontainers；不新增Mock服务、测试后台或运行时调试接口。

```sh
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationReplayToolTest,DiscoveryVerificationQueryServiceTest test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationRepositoryIT test
```

通过条件：指定测试实际执行，失败0、错误0、必要用例跳过0；检查本次target/surefire-reports时间戳，旧报告不算通过。数据库IT明确检查Docker可用，不得用环境不可用跳过。测试结果与该步代码diff一起交复验；人工清单另行完成。

## 人工验收清单

### A-1: 额度耗尽后暂停
- 前置条件：测试环境 3 个待 DETAIL 项，Mock 首次返回明确 insufficient credits，时钟可推进。
- 操作步骤：1. 启动两个 worker；2. 重启其一；3. 1800 秒内查 attempts；4. 推进 1800 秒并让 Mock 返回200。
- 预期结果：故障后冷却内新增 DETAIL=0；窗口到期最多1次探测，成功后恢复；无系统500次上限。
- 覆盖：I-1/I-2，HTTP→账本→重启

### A-2: 其他通道继续
- 前置条件：同环境另准备一项 ORCID、一个可直接准入项及一个待投影已准入项。
- 操作步骤：1. DETAIL 暂停时运行调度；2. 查逐条详情和投影。
- 预期结果：ORCID 有真实请求；直接项 DIRECT_CLEAR；已准入投影可 PROJECTED。
- 覆盖：I-2；保留其他通道/准入

### A-3: 恢复清单幂等与保护
- 前置条件：用测试数据库复制一项未准入 ERROR422，freeze；另准备人工拒绝项和已准入项。
- 操作步骤：1. 对3项运行 apply-errors；2. 再运行相同清单；3. 比较旧 attempt 与请求数。
- 预期结果：只有未保护 ERROR 项 QUEUED；另两项拒绝；再次操作无新增；旧 attempt、请求数不变。
- 覆盖：I-3/I-4；恢复→Worker 及人工保护

