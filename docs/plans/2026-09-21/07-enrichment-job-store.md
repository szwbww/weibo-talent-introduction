# 可恢复的补全任务存储实施计划

> 执行：superpowers:executing-plans 按任务顺序实施；独立验证使用 fix-v。当前为待审阅方案，未开始代码修改。
**目标**：待补全、限流待重试和失败任务持久化；重启不会丢任务，同一专家不会被多个工作者重复处理。
**依赖**：06。
**设计基线**：[主方案](00-discovery-enrichment-master.md)。
**技术栈与约束**：Java 11、Kotlin、Spring Boot 2.7；不新增外部依赖；只改清单文件；不触碰工作区已有无关改动。新增类中辅助DTO/枚举置于所属清单文件内，不暗增文件。
**审查重点**：身份歧义、半页/重启、外部限流、三层并发、旧接口回归；下方用例覆盖本子计划相关项。

## 需求描述

待补全、限流待重试和失败任务持久化；重启不会丢任务，同一专家不会被多个工作者重复处理。

必须保持：主方案M-1至M-5全部适用，本子计划只改变下列实现项；不外发邮件、不扩大医学范围、不更改专家主键。
范围外：其他子计划的实现、前端样式改动、付费采购与全量无条件重跑。

## 关键不变量

### Invariant I-1：持久化唯一事实
- Rule：新增独立任务表，不新增ES根级状态字段。UNIQUE(expert_doc_id)；状态PENDING/RUNNING/RETRY_WAIT/SUCCEEDED/UNMATCHED/FAILED；同专家多次入队合并，已成功且<30天不重入，可靠身份变更可重开未匹配。
- Applies to：enqueue、claim、finish、retry。
- Violation consequence：重复消耗或重启丢数据。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-2：租约和竞争
- Rule：claim用事务条件更新+lease_token，租期10分钟，worker每批续租；完成必须匹配token。进程崩溃后过期RUNNING重新可领；不在外部HTTP期间持有DB事务。MySQL版本未知，不依赖SKIP LOCKED。
- Applies to：多个触发器/worker。
- Violation consequence：双处理、旧worker覆盖新结果。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-3：重试分类
- Rule：429/日预算不足不消耗故障尝试数，next_attempt_at按reset；网络/5xx重试退避1m/5m/30m/2h，最多5次后FAILED；无可靠ID或查无作者为UNMATCHED，不能伪造SUCCEEDED。人工重试可显式重开FAILED。
- Applies to：result persistence。
- Violation consequence：无穷重试或误判完成。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-4：迁移只前进
- Rule：当前本地最新V130，新增暂定V131；执行前核对线上历史及目标分支最大版本，冲突时修改未发布迁移名与本计划清单。禁止修改已应用迁移/启用out-of-order。
- Applies to：Flyway 发布。
- Violation consequence：应用启动失败。
- 来源：本次审计及本页现状审计列出的K条目。

## 现状审计

新增独立MySQL表，无历史写者；后续08的生产者/消费者只经ExpertAcademicEnrichmentJobService写入。字段：id BIGINT PK、expert_doc_id VARCHAR(128) UNIQUE、source VARCHAR(32)、discovery_execution_id BIGINT NULL、status VARCHAR(24)、attempts INT、next_attempt_at DATETIME、lease_token VARCHAR(64) NULL、lease_until DATETIME NULL、last_error VARCHAR(1000) NULL、result_json TEXT NULL、created_at/updated_at DATETIME。索引(status,next_attempt_at,id)；不把密钥或邮箱明文放last_error。任务表可有多列，属于新建专用存储；对既有共享ES/任务表不加字段。FlywayMigrationIntegrationTest多处最新版本固定130须全部更新目标版本，历史V130语义断言保留。(来源: K-flyway-version-follows-deploy-order)

所有跨模块存储读写总清单见主方案“存储与全部相关读写路径”；本页对应新增/改动点是其子集。新共享字段仅05的existing externalIds子键；07新建独立任务存储，其他共享表/索引不增列。前端不改动，无新增样式契约。

## 实现方案

### Task 1：存储及CAS
- 约束：I-1、I-2、I-4。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 新migration、JDBC domain/repository和队列service；enqueue使用唯一键幂等，claim候选后以状态/租约条件CAS，不同token隔离完成。

```kotlin
fun enqueue(docId: String, source: String, executionId: Long?): Unit
fun claimDue(limit: Int, now: LocalDateTime): List<ExpertAcademicEnrichmentJob>
fun complete(id: Long, leaseToken: String, outcome: ProfileEnrichmentOutcome): Boolean
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

### Task 2：重试策略
- 约束：I-3。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 按明确结果持久化状态；每次完成清理租约。result_json保存基础/论文/层更新结果，作为重试和审计依据；成功记录用于30天新鲜度判断，不能替代专家真实字段。

```kotlin
val delayMinutes = listOf(1L, 5L, 30L, 120L)
// quota defer: attempts unchanged; transient failure: attempts + 1
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

新写路径与读者：本页现状审计明确的消费者继续读取相同字段；新增job由08消费、身份子键由06消费、请求policy由全部OpenAlex调用消费。依赖尚未上线时，新入口默认关闭，既有业务仍运行。

## 变更文件清单

| 文件 | 作用 |
|---|---|
| `src/main/resources/db/migration/V131__create_expert_academic_enrichment_job.sql` | 新增迁移（执行前核对版本占用） |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/ExpertAcademicEnrichmentJob.kt` | 数据契约 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/ExpertAcademicEnrichmentJobRepository.kt` | 持久化读写 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobService.kt` | 对应生产实现 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/ExpertAcademicEnrichmentJobRepositoryIT.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobServiceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 新增或更新本计划回归验证 |

共 7 个文件；不存在的文件标记为新增，已存在者原位修改；辅助类型放对应文件内。迁移号碰撞是唯一允许在实施前同步改名的路径变化，须同步本清单。

## 验收标准

- V-1：MySQL集成：两事务同时claim仅一个成功；过期租约可恢复；旧token完成返回false；重复enqueue唯一1行。
- V-2：PENDING→RUNNING→SUCCEEDED；网络失败→RETRY_WAIT；额度耗尽attempts不增加；第五次故障→FAILED；NO_ID→UNMATCHED。
- V-3：新库迁移和V130升级均通过；迁移版本按发布顺序。
- I-1：逐条检查规则“持久化唯一事实”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-2：逐条检查规则“租约和竞争”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-3：逐条检查规则“重试分类”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-4：逐条检查规则“迁移只前进”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。

定向验证：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertAcademicEnrichmentJobServiceTest,ExpertAcademicEnrichmentJobRepositoryIT,FlywayMigrationIntegrationTest -DmigrationIt=true
```
RepositoryIT需真实隔离MySQL；沿用项目集成测试方式，数据库版本与生产兼容。此验证不可只用H2替代。
各子计划定向用例通过后由集成阶段运行全量测试；无业务实现时不运行测试冒充修改已验证。

## 人工验收清单

### A-1：重启恢复
- 前置条件：测试任务库准备1条PENDING，worker进入RUNNING后终止进程。
- 操作步骤：等待租约到期，重新启动worker。
- 预期结果：同一行被重新领取并最终完成，任务总行数仍1。
- 覆盖：I-1、I-2。

### A-2：限流与重试
- 前置条件：代理第一次返回日额度429，第二次返回200。
- 操作步骤：运行补全，检查任务记录，等待reset后继续。
- 预期结果：首次RETRY_WAIT且attempts不增加；第二次SUCCEEDED；不产生重复专家。
- 覆盖：I-3。

### A-3：迁移回归
- 前置条件：测试库处于V130并有原有联系人。
- 操作步骤：执行新版本迁移。
- 预期结果：新表存在，历史联系人数量不变；迁移记录仅新增一个版本。
- 覆盖：I-4。

### A-4：保持原业务
- 前置条件：测试环境保留原人工补全三种scope、原资格规则及一位已申请专家；邮件调度关闭。
- 操作步骤：执行本子计划新行为，再执行原人工入口；对比前后专家详情、候选/申请记录、请求范围和邮件记录。
- 预期结果：原入口仍可执行；专家主键、邮箱、署名机构及运营状态不变；已申请专家不会新建候选；默认排除的医学来源仍不参与；邮件新增0；没有付费调用或已应用迁移被改写。
- 覆盖：主方案M-1/M-2/M-3/M-4/M-5及本页跨路径回归。

