# 公平额度与逐步扩量实施计划

> 执行：superpowers:executing-plans 按任务顺序实施；独立验证使用 fix-v。当前为待审阅方案，未开始代码修改。
**目标**：单次批量与总量独立配置，各来源有处理机会；在API额度、执行时间和安全上限内提高新增量。
**依赖**：01、02、03、04、08。
**设计基线**：[主方案](00-discovery-enrichment-master.md)。
**技术栈与约束**：Java 11、Kotlin、Spring Boot 2.7；不新增外部依赖；只改清单文件；不触碰工作区已有无关改动。新增类中辅助DTO/枚举置于所属清单文件内，不暗增文件。
**审查重点**：身份歧义、半页/重启、外部限流、三层并发、旧接口回归；下方用例覆盖本子计划相关项。

## 需求描述

单次批量与总量独立配置，各来源有处理机会；在API额度、执行时间和安全上限内提高新增量。

必须保持：主方案M-1至M-5全部适用，本子计划只改变下列实现项；不外发邮件、不扩大医学范围、不更改专家主键。
范围外：其他子计划的实现、前端样式改动、付费采购与全量无条件重跑。

## 关键不变量

### Invariant I-1：批量不等于任务总量
- Rule：作者补全批量100、论文页100；建议上线目标OpenAlex10000/全部论文源15000/作者20000，均可配置。0不能暗含无限量；全局cap不足所有来源基础份额时启动校验失败而非静默饿死后源。
- Applies to：配置与循环。
- Violation consequence：错误认为每天只处理100人。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-2：公平且不越界
- Rule：先给每个启用论文源保留至少一个page基础份额，再按来源配额顺序执行。其他源保留额不能被OpenAlex占用；失效或真实穷尽源释放剩余份额，来源cap以内再利用。ORCID记录限额独立1000且仍受总作者20000约束。
- Applies to：论文来源调度与ORCID。
- Violation consequence：主源吃满全局导致后源不执行。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-3：按明确停止原因续跑
- Rule：API预算、源cap、全局cap、4小时时间预算、取消分别记录stopReason；全部保存当前页检查点。不得因重复率高而自动跳过未知页面。日额度归UTC，定时02:00归Asia/Shanghai，worker按实际reset恢复补全。
- Applies to：发现及worker恢复。
- Violation consequence：丢数据、错过额度重置。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-4：上线凭新增实测
- Rule：先恢复小量端到端，再2500→5000→10000逐档放量；比较唯一新增专家、补全成功率、耗时、失败分布，不以搜索元数据数或名义API最大量作为完成指标。
- Applies to：发布验收。
- Violation consequence：扩量后只有重复/低质量数据。
- 来源：本次审计及本页现状审计列出的K条目。

## 现状审计

当前全局论文5000/作者20000、OpenAlex2500；源顺序固定且全局剩余额先到先得；ORCID单独最后执行。sourceStats.papersSearched对ORCID历史上代表记录数，本轮保持兼容并在summary明确分别列论文与ORCID记录，不追溯重算历史。各source默认cap归各Properties，当前已有可调环境变量。为避免上限5000改为更大后请求队列膨胀，本计划保留fetchConcurrency=4，不同时放大并发。只修改任务结果中的message/details，原表/前端结构不变。

所有跨模块存储读写总清单见主方案“存储与全部相关读写路径”；本页对应新增/改动点是其子集。新共享字段仅05的existing externalIds子键；07新建独立任务存储，其他共享表/索引不增列。前端不改动，无新增样式契约。

## 实现方案

### Task 1：配额分配
- 约束：I-1、I-2。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 实现run级配额计算：OpenAlex初始10000、Crossref1000、CORE1000、arXiv2000；合计14000<=15000，保留额可分配至其他被手动选源但不超过全局。默认scope仍排除EuropePMC/PMC OA。人工少源任务只分配所选源。

```kotlin
val reserveForLater = remainingSources.sumOf { minOf(pageSize, it.remainingCap) }
val thisSourceBudget = minOf(sourceCap, globalRemaining - reserveForLater)
require(globalCap >= selectedSources.sumOf { minOf(pageSize, it.cap) })
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

### Task 2：时间与上线记录
- 约束：I-3、I-4。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] run配置deadline，HTTP请求前/每页检查；超时不推进半页。发布runbook记录各档实际指标和回滚开关，不包含Key。恢复历史游标先核验完整记录；无法恢复时明确标记重扫期。

```kotlin
if (clock.instant() >= deadline) stopReason = "TIME_BUDGET"
// save current safe checkpoint; never claim source exhausted
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

新写路径与读者：本页现状审计明确的消费者继续读取相同字段；新增job由08消费、身份子键由06消费、请求policy由全部OpenAlex调用消费。依赖尚未上线时，新入口默认关闭，既有业务仍运行。

## 变更文件清单

| 文件 | 作用 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryProperties.kt` | 类型化配置 |
| `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexProperties.kt` | 类型化配置 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SourceStats.kt` | 数据契约 |
| `src/main/resources/application.yml` | 环境变量和默认配置 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt` | 新增或更新本计划回归验证 |
| `docs/plans/2026-09-21/discovery-enrichment-rollout.md` | 发布操作与验证记录 |

共 8 个文件；不存在的文件标记为新增，已存在者原位修改；辅助类型放对应文件内。迁移号碰撞是唯一允许在实施前同步改名的路径变化，须同步本清单。

## 验收标准

- V-1：全局上限恰好覆盖所有源一页，各源均有一次请求；一个来源耗尽释放配额；全局不足基础份额启动报清晰配置错误。
- V-2：重复率99%仍按游标前进，不跳未知页；时间预算退出检查点不丢；手动只选arXiv不受OpenAlex份额影响。
- I-1：逐条检查规则“批量不等于任务总量”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-2：逐条检查规则“公平且不越界”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-3：逐条检查规则“按明确停止原因续跑”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-4：逐条检查规则“上线凭新增实测”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。

定向验证：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest
```

各子计划定向用例通过后由集成阶段运行全量测试；无业务实现时不运行测试冒充修改已验证。

## 人工验收清单

### A-1：来源公平
- 前置条件：测试环境四个来源各有200条，globalCap=400、page=100。
- 操作步骤：执行默认发现。
- 预期结果：四源各至少搜索100篇，总数不超过400；不是OpenAlex单独消耗400。
- 覆盖：I-1、I-2。

### A-2：分档扩量
- 前置条件：恢复入口与游标后，记录2500档完整跑数。
- 操作步骤：依次配置5000和10000档，逐档运行并记录runbook。
- 预期结果：每档有新增/重复/补全/耗时数；来源保留份额未被侵占；发生错误可退回上一档且游标保留。
- 覆盖：I-3、I-4。

### A-3：保持原业务
- 前置条件：测试环境保留原人工补全三种scope、原资格规则及一位已申请专家；邮件调度关闭。
- 操作步骤：执行本子计划新行为，再执行原人工入口；对比前后专家详情、候选/申请记录、请求范围和邮件记录。
- 预期结果：原入口仍可执行；专家主键、邮箱、署名机构及运营状态不变；已申请专家不会新建候选；默认排除的医学来源仍不参与；邮件新增0；没有付费调用或已应用迁移被改写。
- 覆盖：主方案M-1/M-2/M-3/M-4/M-5及本页跨路径回归。

