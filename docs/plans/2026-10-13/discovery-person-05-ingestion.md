# 05 后续发现、重复发现与补证统一归人

状态：设计，未实施。依赖 [04](discovery-person-04-registry.md)；补证接口依赖原 01/02 的已验证产物，不授权旧待确认名单处理。上级：[总计划](discovery-identity-recovery-master.md)。

## 需求描述

以后同一可靠专家新增邮箱时增加联系方式，人物数不再按邮箱增长。保持原提取证据、下载预算、暂停、每邮箱资格、三层原 ID；仅作用于当前 discovered，不扩展解析器或其他标签回填。

## 关键不变量

### Invariant I-1：每条成功出口都登记
- Rule：同步 ORCID、论文消费、持久队列、相同邮箱重复、01 补证及 02 恢复，在事实成功持久化且范围有效后调用 04；不能在重复分支提前返回而跳过归人。原始邮箱事实可继续保留多条 ES 技术记录，但用户专家数由人物主档决定。
- Applies to：本计划四个生产文件。
- Violation consequence：迁移后继续产生无主档重复记录。
- 来源：ExpertDiscoveryService 消费出口审计。

### Invariant I-2：双存储中断可恢复
- Rule：先保留邮箱 ES 事实，再登记 SQL 关系，最后完成该发现结果；登记失败可幂等重试，从既有 ES 事实继续，不重置邮箱证明。映射未就绪的 discovered 不进入首发。事实/指纹变化返回 STALE 而非借旧关系通过；不能靠清空队列解决。
- Applies to：队列 ack、同步统计、补证/回源恢复。
- Violation consequence：假成功、无映射发送或重复补写。
- 来源：总计划 I-8、原 01 失败传播要求。

### Invariant I-3：单位清楚、预算不变
- Rule：分别统计新增人物、追加邮箱、重复事实、需复核、登记失败；原邮件事实条数不改叫人数。maxAuthors/队列批大小继续原有工作量限制，不因多邮箱聚合而无限抓取。姓名猜测、凭证伪造和其他标签写入均禁止。
- Applies to：ConsumeOutcomeResult、QueuedItemConsumption、DiscoveryPipelineService 结果及日志。
- Violation consequence：数量误导或超出原调用预算。
- 来源：当前一个 AuthorEmail 一个事实的流程。

## 现状审计

- `ExpertDiscoveryService`：同步 ORCID 的 `orcidRecordToAuthorEmails` 循环；`paperIdentityFactory`/`orcidIdentityFactory`；`consumeOutcomeInternal` 的新建与 duplicate 两出口；`consumeQueuedItem`；`recordIdentityDuplicate`；学术 `updateExpertAcademicFields`。消费写原始层，随后补全/准入；同步与队列并存。
- `DiscoveryPipelineService` 读消费结果决定重试/完成；把追加关系失败留在日志但 ack 成功会丢重试。原 01 已规划 identityRepairFailed，应沿用传播规则、区分人物失败。
- 原 01 `DiscoveryIdentityRepairService` 和原 02 `DiscoveryIdentityRecoveryService` 是计划中的新写路径，尚未实施时不能假称当前存在；实施本子计划须基于它们实际验收接口。
- 04 三表唯一写者仍是 DiscoveryPersonService；本计划新增调用者，不新增 SQL/ES 字段。邮箱验证/准入仍使用原 docId，06/08 再读取人物关系。
- 其他 ES 标签/运营/学术写入不在这里改造：06/08/09 必须检查标签及身份指纹；身份改变后的旧关系不自动有效，定向登记可恢复。非发现 `backfillRawEmailsAndPromote` 不纳入。

## 实现方案

1. **新建/重复统一出口（I-1/I-2）**：修改 ExpertDiscoveryService 接入 04；从当前持久化事实构造登记参数，不从无凭证 externalIds 猜身份。新 email 技术记录已存在时走同样登记；ORCID 与论文队列共用登记函数。
2. **补证后刷新（I-1/I-2）**：修改两个 recovery service，只对实时 discovered 成员在各层事实补齐成功后刷新人物登记；跨层部分失败先留失败，不抢先合并。身份指纹变化须先复验完整组，冲突保持独立并记录原因。
3. **传播与统计（I-2/I-3）**：DiscoveryPipelineService 将可重试登记异常纳入未完成结果；语义冲突记录为需复核，不无限重试。维护重新登记扫描可恢复“ES 成功、SQL 失败”，由 10 提供，禁止预估时补写。
4. **回归（I-1— I-3）**：四份定向测试覆盖原多邮箱 HTML 夹具和 same-ORCID 两邮箱场景；期待原邮箱证据 2 份、人物 1 位，而不是删掉第二邮箱。增加注册前/后进程中断、重复任务、来源冲突和暂停预算断言。

## 变更文件清单

|文件|动作|
|---|---|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt|修改|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt|修改|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt|在原 01 产物上修改|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt|在原 02 产物上修改|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt|修改|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt|修改|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairServiceTest.kt|在原 01 产物上修改|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt|在原 02 产物上修改|

共 8 文件；发现消费与身份恢复两个子系统，既有共享存储增字段 0。

## 验收标准

- I-1：每个入口同作者不同邮箱为 1 人 N 邮箱；重复邮箱补证也建立/刷新关系。
- I-2：登记失败不报完整成功，重试不重复成员、不改人工准入；三层事实不一致不聚合。
- I-3：追加邮箱不会增加新增人物数；maxAuthors/暂停行为保持；范围外写入 0。

## 人工验收清单

### A-1：后续新增与重复补证
- 前置条件：测试源固定返回一名作者两个邮箱，其中一个在库缺证；另准备其他标签记录和同名异人。
- 操作步骤：分别运行同步与队列发现，再运行定向补证，最后重复同一任务。
- 预期结果：可靠同人只有 1 个 personId、2 邮箱；重跑新增人 0；异人分开，其他标签写入 0，原身份/准入保留。
- 覆盖：I-1/I-3，发现与恢复→主档交互。

### A-2：中断恢复
- 前置条件：故障注入使 ES 写成功后 SQL 暂时失败，禁止真实发送。
- 操作步骤：消费一次，查看任务结果；解除故障重试；触发暂停并读取计数。
- 预期结果：第一次显示登记失败/可重试而非完整成功，第二次恰好 1 主档 1 成员；期间可发送目标 0；暂停后不增加外部请求。
- 覆盖：I-2/I-3、双存储及预算保持。
