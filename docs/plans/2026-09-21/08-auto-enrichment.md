# 发现后自动补全与人工补偿实施计划

> 执行：superpowers:executing-plans 按任务顺序实施；独立验证使用 fix-v。当前为待审阅方案，未开始代码修改。
**目标**：全部参与来源的新增专家自动进入补全任务，每批最多100人，尾批不等待；人工补充入口继续用于历史回填和重试。
**依赖**：02、05、06、07。
**设计基线**：[主方案](00-discovery-enrichment-master.md)。
**技术栈与约束**：Java 11、Kotlin、Spring Boot 2.7；不新增外部依赖；只改清单文件；不触碰工作区已有无关改动。新增类中辅助DTO/枚举置于所属清单文件内，不暗增文件。
**审查重点**：身份歧义、半页/重启、外部限流、三层并发、旧接口回归；下方用例覆盖本子计划相关项。

## 需求描述

全部参与来源的新增专家自动进入补全任务，每批最多100人，尾批不等待；人工补充入口继续用于历史回填和重试。

必须保持：主方案M-1至M-5全部适用，本子计划只改变下列实现项；不外发邮件、不扩大医学范围、不更改专家主键。
范围外：其他子计划的实现、前端样式改动、付费采购与全量无条件重跑。

## 关键不变量

### Invariant I-1：RAW先落地再入队
- Rule：论文consumeOutcome与ORCID循环两个新增入口，RAW写成功后幂等enqueue；enqueue失败不得推进当前论文页。重放遇到已入库discovered专家时补建缺失任务，不重新写整份专家、不算新增。非本流程导入专家不因同邮箱被自动覆写身份。
- Applies to：论文源/ORCID/重放。
- Violation consequence：跨ES/MySQL崩溃窗口漏任务。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-2：自动并行且有界
- Rule：worker每30秒检查一次，领取至多100条到期任务；不足100也执行。独立单worker不占邮件调度线程；时间片结束/取消保存未完成状态；日额度耗尽只推迟OpenAlex补全，其他来源抓取继续。默认开关false，验收后线上开启。
- Applies to：worker、enrichmentExecutor、手动触发。
- Violation consequence：逐人等待阻塞发现或邮箱任务。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-3：统一补全与复评
- Rule：自动和手动都复用06核心，成功后仅对RAW-only执行定向复评；初始发现已有资格判断保持，不额外新增必需学术门槛。新资料到位可使原先缺指标的人重新符合规则；已有候选不自动降级。
- Applies to：discover→queue→enrich→promotion。
- Violation consequence：改变发送门禁或漏掉RAW专家。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-4：审计归属
- Rule：发现成功数仍是新入库人数，不加补全人数。job记录discovery_execution_id；自动补全有独立EXPERT_ENRICHMENT任务记录，details保留各源入队/成功/待补/未匹配数；原历史详情显示当时快照，后续补全看独立记录。
- Applies to：task_execution/progress/log。
- Violation consequence：一人重复计数或历史任务结果漂移。
- 来源：本次审计及本页现状审计列出的K条目。

## 现状审计

全部来源除ORCID都汇入consumeOutcome；ORCID单独循环，二者必须覆盖。现 enrichmentExecutor 是单线程且queueCapacity=0，不能每个专家submit；改由单worker领数据库任务。现手动/enrich拥有TaskProgressStore互斥和token；自动worker须走同一任务类型锁，手动与自动相互排斥但不影响discovery。DiscoveryResult等旧字段保持，附加计数放既有details_json/message，复用现有进度UI，不改前端。手动原有三种scope保留，增加DISCOVERY_PENDING用于明确重试自动任务；不每新增一人启动全候选扫描。

所有跨模块存储读写总清单见主方案“存储与全部相关读写路径”；本页对应新增/改动点是其子集。新共享字段仅05的existing externalIds子键；07新建独立任务存储，其他共享表/索引不增列。前端不改动，无新增样式契约。

## 实现方案

### Task 1：入队与崩溃恢复
- 约束：I-1、I-3。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 去重结果携带匹配文档真实_id；新入库及已发现重复文档ensureJob。重复时只读取持久化身份，不以新论文的模糊匹配覆盖旧记录。数据库错误向源抛可重试失败，02保留页。

```kotlin
if (rawWriteSucceeded) jobService.enqueue(docId, source, executionId)
// replay + duplicate discovered doc => ensure job, indexed count stays unchanged
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

### Task 2：后台和人工复用
- 约束：I-2、I-4。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] worker通过TaskProgressStore.tryStartWithToken获取EXPERT_ENRICHMENT锁，领取数据库<=100条，调用enrichProfiles及完成/复评；最近3论文获取开关在发布阶段开启。增加scope枚举和stats响应附加计数，不删除旧入口。；自动调用requestKind=NEW_ENRICHMENT，人工历史回填=HISTORY_ENRICHMENT。

```kotlin
@Scheduled(fixedDelay = 30000)
fun processDueEnrichmentJobs()
// acquire global enrichment task token; DB claim; enrich; finish; release token
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

新写路径与读者：本页现状审计明确的消费者继续读取相同字段；新增job由08消费、身份子键由06消费、请求policy由全部OpenAlex调用消费。依赖尚未上线时，新入口默认关闭，既有业务仍运行。

## 变更文件清单

| 文件 | 作用 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentWorker.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt` | 保留并扩展已有入口 |
| `src/main/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryProperties.kt` | 类型化配置 |
| `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt` | 对应生产实现 |
| `src/main/resources/application.yml` | 环境变量和默认配置 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentWorkerTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt` | 新增或更新本计划回归验证 |

共 9 个文件；不存在的文件标记为新增，已存在者原位修改；辅助类型放对应文件内。迁移号碰撞是唯一允许在实施前同步改名的路径变化，须同步本清单。

## 验收标准

- V-1：RAW成功后入队失败，次轮重复页可补建任务且只新增1人；进程重启前后可恢复。
- V-2：101条任务分100/1，1条任务30秒内可领取；自动任务与手动同锁；邮件executor不被占用。
- V-3：ORCID和论文路径均入队；无身份记录UNMATCHED；APPLICATION已存在不会重建候选；既有scope仍可调用。
- I-1：逐条检查规则“RAW先落地再入队”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-2：逐条检查规则“自动并行且有界”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-3：逐条检查规则“统一补全与复评”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-4：逐条检查规则“审计归属”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。

定向验证：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentWorkerTest,ExpertDiscoveryControllerTest
```

各子计划定向用例通过后由集成阶段运行全量测试；无业务实现时不运行测试冒充修改已验证。

## 人工验收清单

### A-1：自动补全
- 前置条件：测试源准备101位唯一专家，其中1人仅有A ID；开启自动补全。
- 操作步骤：只点击深度发现，等待两个工作批次，不点击补充按钮。
- 预期结果：新增101人，对可匹配者自动显示学术指标；批次100+1，发现成功数仍101。
- 覆盖：I-1、I-2、I-4。

### A-2：故障不中断发现
- 前置条件：OpenAlex补全代理返回429，其他来源返回2位新专家。
- 操作步骤：运行深度发现，再恢复额度。
- 预期结果：2位基础资料已保存，任务为待重试；恢复后补全，无重复新增。
- 覆盖：I-2、I-3。

### A-3：人工回填回归
- 前置条件：准备旧候选专家和失败自动任务。
- 操作步骤：分别使用原补充入口、DISCOVERY_PENDING重试。
- 预期结果：旧专家可更新，失败任务可恢复；重复操作不重复创建候选/申请专家。
- 覆盖：I-3、I-4。

### A-4：保持原业务
- 前置条件：测试环境保留原人工补全三种scope、原资格规则及一位已申请专家；邮件调度关闭。
- 操作步骤：执行本子计划新行为，再执行原人工入口；对比前后专家详情、候选/申请记录、请求范围和邮件记录。
- 预期结果：原入口仍可执行；专家主键、邮箱、署名机构及运营状态不变；已申请专家不会新建候选；默认排除的医学来源仍不参与；邮件新增0；没有付费调用或已应用迁移被改写。
- 覆盖：主方案M-1/M-2/M-3/M-4/M-5及本页跨路径回归。

