# 06 · 唯一统计口径与逐条核验日志API

日期2026-10-18；待实施。依赖按[总计划](apollo-verification-master.md)顺序。规范附录：[contracts](apollo-contracts.md)、[代码审计](apollo-code-audit.md)。实施前核对固定HEAD差异。

## 需求描述

O-1：提供真实汇总/批次/逐条/证据API，准确区分自动准入来源与明确性；旧历史、空任务、接口失败可区分。保持N-1原采集统计/通用task接口和保留策略，N-2GET不触发写入或外部核验。范围不含通用任务活动中心重构。

## 关键不变量

### Invariant I-1: 统一分母分桶
- Rule：按C-5全部counts字段语义；route与qualification不能互相推导；只activated档案计分母；reason是多命中，不能用原因和算总失败。
- Applies to：repository聚合/query DTO。
- Violation consequence：数字无法对账。
- 来源：总I-8/I-9。

### Invariant I-2: 历史/错误不可伪造
- Rule：无run coverage=NOT_RECORDED计数null；有run零条是0；读取异常503不能fallback到0；AUDIT建议与真实auto区分。
- Applies to：controller/query。
- Violation consequence：旧记录伪精确。
- 来源：总I-10。

### Invariant I-3: 一致快照和分页
- Rule：一请求内REPEATABLE_READ，run.revision为snapshotVersion；records先服务端过滤再分页，q参数绑定转义；当前admission批量读，证据详情返回源时间/类型。
- Applies to：query/repository/controller。
- Violation consequence：前端翻页丢计/串数。
- 来源：总I-6/I-13。

### Invariant I-4: 纯读边界
- Rule：GET不修改task/resultSummary/ledger，不补建run，不访问Apollo/ORCID/ES修复；当前admission由MySQL批读；敏感凭据/未经筛选原响应不出网。
- Applies to：全部GET。
- Violation consequence：浏览就执行副作用。
- 来源：总I-11/I-12。



## 现状审计

目前TaskProgressController:103/130有logs/executions，TaskExecutionController:114任务详情，DiscoveryPromotionProgressService:73按旧enrichment任务+ES存在性读晋升。这些不是Apollo来源事实，本片不改其列语义/数据库字段。现有api拦截器保护/api/**，新controller放同路径；01新表及V148是唯一读源。IP-5/2（投影可解释）。

## 实现方案

1. **query DTO/service（I-1～I-4）**：在新QueryService实现C-5契约，复用01domain DTO；summary按execution批量GROUP BY，batches按激活批次归组，records返回筛选后精确total。尽量单SQL条件聚合，同一事务的COUNT和page保持一致；避免每个档案单查admission。run input_closed与active推核验状态，不修改原采集状态。
2. **controller（I-2/I-3/I-4）**：新增GET端点逐字C-5，参数显式验证，400/404/503分类；capabilities无secret。批次/records不存在账本也返回NOT_RECORDED，不把旧采集数据塞入新结果。已有执行ID被清理时若新run仍存在可以从账本读取，collectionStatus=EXPIRED而不伪造采集状态。
   **C06-CAPABILITY-AUTH已批准修正**：通过既有worker的只读accessor读取本进程当前authBlocked锁存状态，capabilities返回ready=false及CONFIG_AUTH；可选worker未创建时保持配置能力语义。GET仅读取，不重置锁存、不重试、不从历史attempt推断当前状态，不改worker领取/失败策略。401/403后的能力响应与不继续领取由既有及查询/controller回归覆盖。
3. **SQL视图（I-1/I-3）**：01repository新增聚合/分页只读方法；不新增物化统计表。owner_execution固定；auto是本次已经提交过的自动事件；currentAdmissionDecision是当前快照，后续人工变更不会重写本次历史事实。
4. **夹具与实测（I-1～I-4）**：交付清单内SQL夹具，仅允许导入名称带`_verification_fixture`的独立开发库，文件开头校验`DATABASE()`不符合则SIGNAL拒绝；合成ID统一`APV-FIXTURE-`前缀，使用固定executionId=3001/3002/3003，插入task_execution最小合法行便于原执行列表看见，不删除任何非fixture数据；F123重复导入幂等。给验收者明确命令 `mysql -h 127.0.0.1 -u <测试用户> -p <独立库名_verification_fixture> < src/test/resources/discovery/apollo-verification-manual-fixture.sql`，该命令由验收者在测试环境执行，计划阶段不执行。使用01/04测试helper构造C-6的F123和投影错误、人改状态、AUDIT变体；真实MySQL聚合验证等式，controller测试认证/异常与空历史语义；对每GET做SQL写调用及外部client零调用断言。

## 变更文件清单

共9个文件；原7个加既有worker只读认证阻断状态接缝及其现有回归测试；无清单外授权。

|#|文件|动作|
|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt`|新增|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationController.kt`|新增|
|3|`src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`|修改只读聚合|
|4|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt`|新增|
|5|`src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt`|新增|
|6|`src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationReportingIT.kt`|新增隔离MySQL聚合|
|7|`src/test/resources/discovery/apollo-verification-manual-fixture.sql`|新增仅手动验收夹具|
|8|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`|修改只读暴露当前authBlocked，不改变领取/失败/重置策略|
|9|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt`|修改覆盖只读认证阻断接缝|

## 验收标准

I-1：F123每列/每批/过滤total精确断言；I-2：旧null、新0、异常503、AUDIT auto0；I-3：同事务快照、分页排序、LIKE转义、批读上限；I-4：GET零写、零HTTP、密钥无回显。使用新3类测试和C-7MySQL条件；对123及至少10000条合成数据跑EXPLAIN，确认owner_execution索引被用、分页≤100，不承诺未测的毫秒延时。

## 人工验收清单

### A-1: 汇总与下钻对账
- 前置条件：测试seedF123(run3001)。
- 操作步骤：请求summary、batches，再按UNCLEAR和PENDING请求records；打开任一item证据。
- 预期结果：123/55/15/40/36/19/45/5/18；UNCLEAR total19，PENDING total45；完整reason未截3条；证据含sourceUrl/fetchedAt。
- 覆盖：O-1，I-1/I-3，IP-5

### A-2: 历史零和错误
- 前置条件：准备旧run3002（无账本）、新空run3003；可关闭测试DB。
- 操作步骤：分别请求summary；关闭DB后再次请求。
- 预期结果：旧coverage NOT_RECORDED且null，新RECORDED且0；DB关闭503，绝非200全0。
- 覆盖：I-2，IP-5

### A-3: 只读及后续人工决定
- 前置条件：F123中一条自动准入后再人工HOLD；记录所有表updated_at及HTTP请求计数。
- 操作步骤：连续GET明细/汇总20次。
- 预期结果：本次auto55仍是历史事实；该条currentAdmissionDecision=HOLD；20次GET没有新表写入、没有Apollo请求，原task接口不变。
- 覆盖：N-1/N-2，I-4，IP-2/IP-5

## 修正记录
- 用户“批准该计划并执行 后续也都授权 执行完所有子计划”批准C06-CAPABILITY-AUTH最小修正：新增以上既有worker及测试授权，上限7→9；仅将当前认证阻断状态传给既定capabilities接口，无新状态/表/重置/重试/历史推断。精确Before/After与批准记录见fast-p ledger Amendments。
