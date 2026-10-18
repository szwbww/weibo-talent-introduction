# 01 · 核验账本、持久状态与租约

日期2026-10-18；待实施。依赖按[总计划](apollo-verification-master.md)顺序。规范附录：[contracts](apollo-contracts.md)、[代码审计](apollo-code-audit.md)。实施前核对固定HEAD差异。

## 需求描述

O-1：为新核验提供可查询、可恢复、按档案去重的数据库事实；本片交付后可通过隔离测试库SQL观察状态。保持N-1原发现/审核/任务表不变，N-2不开外网请求、不改准入。范围不含worker与业务接入。

## 关键不变量

### Invariant I-1: 字段与状态完整约束
- Rule：逐字段实现contracts C-1～C-3；三个新表，现有表新增字段0；每个状态/route/outcome/qualification投影关系按C-3验证。数据库NULL有明确含义，未完成不得默认准入。
- Applies to：DDL、domain、repository。
- Violation consequence：空状态被算为成功。
- 来源：总I-4/I-8/I-10。

### Invariant I-2: 唯一键与归属不可漂移
- Rule：ENFORCE/AUDIT版本分离；首次owner_execution_id、source_item_key冻结；RESERVED的batch_number=0，首次activate分配正批次后冻结。重复reserve返回原item，不重设终态。正executionId强校验；唯一键冲突需读回核验身份。
- Applies to：reserve/activate/replay。
- Violation consequence：重复归属与重复计数。
- 来源：总I-6/I-7。

### Invariant I-3: 租约、锁序、原子预算
- Rule：所有方法遵守C-3锁序；claim/finish/retry以token+generation CAS；budget同连接命名锁+attempt占位。所有item变更同事务run.revision+1；不持锁出网。
- Applies to：repository所有写者。
- Violation consequence：并发覆盖/超额请求。
- 来源：总I-6/I-9/I-12。

### Invariant I-4: 只加能力，默认无副作用
- Rule：enabled与workerEnabled默认false；配置类不输出密钥；本片没有scheduler/业务调用者。MySQL测试仅独立容器。
- Applies to：配置、DI、测试。
- Violation consequence：部署后未经验证自动改数据。
- 来源：总I-3/I-12。



## 现状审计

现有schema V148准入、V131学术补全队列、V133采集队列已在audit§1/4/5审计；它们各有独立唯一键，不能挪用。新表当前0访问路径，后续写者/读者已列audit§3。配置不是自动扫描：按同repo FactRetrieverProperties 的同文件@EnableConfigurationProperties模式注册。IP-1/IP-5从本片存储契约开始；既有表清理策略不会通过外键删除证据。

## 实现方案

1. **DDL与模型（I-1/I-2）**：V158逐字采用C-2；DiscoveryVerification.kt定义run/item/attempt、枚举、证据结构、计数DTO、`RegistrationRecovery`接口（`recoverReserved(limit:Int):Int`，无实现也可启动）与`VerificationContext`（UNMANAGED/MANAGED_AUDIT/MANAGED_ENFORCE/UNAVAILABLE），不把DB错误编码成UNMANAGED。附有限长度校验和UTC转换；新domain可包含多个紧密相关data class，不为凑上限改无关domain。
2. **repository接缝（I-2/I-3）**：实现 `createRun, reserveItem, activateItem, closeInput, findRun, findManagedContexts(docIds≤500), claimDue, renewLease, reserveAttemptWithBudget, completeAttempt, saveEvidenceAndOutcome, markProjection, reschedule, listReserved`。调用者明确每方法写入字段；只读取该表的查询预留给06，禁止向TaskProgressStore写实时统计。事务方法必须由Spring代理跨bean调用，禁止同类self-invocation假事务。
3. **配置（I-4）**：新增properties与同文件config注册，数值范围启动校验；`toString`不含key。不改application.yml/线上secret，本片配置缺失正常启动且无业务变化。
4. **真实并发验证（I-1～I-4）**：repo IT使用Testcontainers，运行迁移至V158；20线程同键reserve只得一行；旧token finish返回false；事务故障后run revision和item同时回滚；预算并发不超过设置值。测试fixture helper作为IT内部函数，不增加未列文件。

## 变更文件清单

共6个文件；一个发现核验子系统（与既有准入/前端的交互按文中说明）；无清单外授权。

|#|文件|动作|
|---|---|---|
|1|`src/main/resources/db/migration/V158__create_discovery_verification.sql`|新增|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerification.kt`|新增|
|3|`src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`|新增|
|4|`src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryVerificationProperties.kt`|新增|
|5|`src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt`|新增|
|6|`src/test/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerificationContractTest.kt`|新增|

## 验收标准

I-1：迁移/序列化/非法枚举/NULL语义断言；I-2：重复和跨run归属断言；I-3：MySQL并发、旧租约、预算锁释放及回滚；I-4：Spring上下文无key且disabled启动，网络mock零调用。命令按C-7运行两个新测试类，真实MySQL并发不能用纯mock替代。

## 人工验收清单

### A-1: 唯一与回放
- 前置条件：测试工程提供seedLedgerFixture()，在容器库建立run1001/1002，同一doc-hash-version重复20次。
- 操作步骤：运行该fixture；只读SELECT item数量、owner_execution_id、batch_number；对run1002重放。
- 预期结果：只有1条item，owner仍1001，batch不变；两个run都保留自身记录。
- 覆盖：O-1，I-1/I-2，IP-1

### A-2: 重启与旧租约
- 前置条件：fixture设一项RUNNING，旧token=t1，lease已过期；提供测试控制器/测试驱动函数，不开放生产fixture接口。
- 操作步骤：重启测试实例；调用claim得到t2；分别用t1、t2完成。
- 预期结果：t1影响0行，t2影响1行，只有一个COMPLETED与一次run revision递增。
- 覆盖：I-3，IP-5

### A-3: 默认关闭无改动
- 前置条件：只部署01到隔离测试实例，既有发现任务和人工审核fixture各1条；不配置新enabled。
- 操作步骤：启动应用，运行原发现/原审核；比较V148与task字段。
- 预期结果：没有新增外部Apollo请求；原准入行为不变；新表无自动写入。
- 覆盖：N-1/N-2，I-4
