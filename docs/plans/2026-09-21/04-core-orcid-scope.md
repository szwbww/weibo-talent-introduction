# CORE、ORCID 分页与研发范围实施计划

> 执行：superpowers:executing-plans 按任务顺序实施；独立验证使用 fix-v。当前为待审阅方案，未开始代码修改。
**目标**：CORE 可跨页跨日推进，ORCID 定时任务有真实查询条件；来源恢复后仍限定研发目标领域。
**依赖**：02、03。
**设计基线**：[主方案](00-discovery-enrichment-master.md)。
**技术栈与约束**：Java 11、Kotlin、Spring Boot 2.7；不新增外部依赖；只改清单文件；不触碰工作区已有无关改动。新增类中辅助DTO/枚举置于所属清单文件内，不暗增文件。
**审查重点**：身份歧义、半页/重启、外部限流、三层并发、旧接口回归；下方用例覆盖本子计划相关项。

## 需求描述

CORE 可跨页跨日推进，ORCID 定时任务有真实查询条件；来源恢复后仍限定研发目标领域。

必须保持：主方案M-1至M-5全部适用，本子计划只改变下列实现项；不外发邮件、不扩大医学范围、不更改专家主键。
范围外：其他子计划的实现、前端样式改动、付费采购与全量无条件重跑。

## 关键不变量

### Invariant I-1：稳定进度不用瞬时 scroll
- Rule：CORE 改用已实测的 offset/limit，cursor 保存 query 分片与 offset，不存 searchId/scrollId；页满推进原始返回数量，页空/实际末页结束。不能声称 offset 对变化中的源提供快照一致性。
- Applies to：CORE 和统一检查点。
- Violation consequence：反复首批或使用过期游标。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-2：ORCID 按原始记录翻页
- Rule：新增搜索结果封装，返回 rawRecordCount、nextCursor、带公开邮箱的 records；无公开邮箱的一整页不能终止搜索或停住 offset。定时关键词空时使用 scope 中的主题种子，手动关键词优先。
- Applies to：ORCID 搜索/发现循环。
- Violation consequence：过滤邮箱导致漏扫后续页。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-3：统一研发意图
- Rule：主题目录保留六类研发范围；CORE 查询用明确括号 OR；Crossref 针对目录主题轮换检索并保留来源/主题审计。ORCID 种子使用公开研究关键词字段，不把公司名、机构地址当国籍；来源关键词只能作为检索约束，不能声称等同 OpenAlex 分类。EuropePMC/PMC OA 仍保持当前排除。
- Applies to：SubjectScopeCatalog 与来源适配。
- Violation consequence：重新引入大量不相关专家。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-4：边界停止可见
- Rule：CORE offset 触达已验证供应商窗口时停止该分片并记录 WINDOW_LIMIT；不得把 searchId 改名伪作 cursor，也不能无限递增。每个请求保留超时/取消/配额处理。
- Applies to：各源分页。
- Violation consequence：循环、误报穷尽。
- 来源：本次审计及本页现状审计列出的K条目。

## 现状审计

CORE 搜索返回 totalHits/limit/offset/results/searchId，没有 scrollId；2026-09-21 同查询 offset=0/2 得到两组无交集 ID，证明 offset 生效。现 nonPersistableCursorSources={CORE} 必须对应移除。ORCID parseOrcidResponse 在返回前丢弃无邮箱者，discoverFromOrcid 依赖过滤后列表判断分页，必须成对修改。SubjectScopeCatalog 当前只有 OpenAlex/arXiv 真正接入研发范围；CORE 目录主题尚未接线、Crossref 未用 scope。只改 cursor 现有存储，不增共享字段。

所有跨模块存储读写总清单见主方案“存储与全部相关读写路径”；本页对应新增/改动点是其子集。新共享字段仅05的existing externalIds子键；07新建独立任务存储，其他共享表/索引不增列。前端不改动，无新增样式契约。

## 实现方案

### Task 1：CORE 分页及查询
- 约束：I-1、I-3、I-4。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] offset 持久化，按主题+单年分片；每分片最大 offset 9000，达到边界记录未覆盖尾部并切下一分片；下轮从检查点继续，全部分片遍历后新周期去重重扫。此上限为保守防护，不能视为供应商保证；完整拉取窗口之外另需官方契约验证。

```kotlin
data class CoreCursor(val topic: Int, val year: Int, val offset: Int)
val query = "(" + terms.joinToString(" OR ") + ") AND yearPublished=" + year
val nextOffset = cursor.offset + rawResults.size
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

### Task 2：ORCID 和 Crossref 范围
- 约束：I-2、I-3。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 在 OrcidDataSource.kt 内定义 OrcidSearchPage，使用原始条数推进；query 分片主题存入源游标。Crossref 用 query.bibliographic 主题种子降低噪音，不更改人工关键词；主题范围定义只在目录中维护。

```kotlin
data class OrcidSearchPage(val records: List<OrcidRecord>, val nextCursor: String?, val rawCount: Int)
// rawCount=100, usableRecords=0 still advances to next page
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

新写路径与读者：本页现状审计明确的消费者继续读取相同字段；新增job由08消费、身份子键由06消费、请求policy由全部OpenAlex调用消费。依赖尚未上线时，新入口默认关闭，既有业务仍运行。

## 变更文件清单

| 文件 | 作用 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSource.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` | 数据契约 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSourceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSourceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 新增或更新本计划回归验证 |

共 10 个文件；不存在的文件标记为新增，已存在者原位修改；辅助类型放对应文件内。迁移号碰撞是唯一允许在实施前同步改名的路径变化，须同步本清单。

## 验收标准

- V-1：CORE 首两页 ID 不相同，跨运行续 offset；查询年份/主题变更用独立检查点；到9000记录窗口限制。
- V-2：ORCID 首页100条无邮箱、次页1条有邮箱，最终获取1人；空关键词RND有请求、无scope无关键词保持明确跳过。
- V-3：Crossref/CORE 发出的研发关键词有正确括号/主题，人工指定关键词不被覆盖；EuropePMC/PMC OA 排除不变。
- I-1：逐条检查规则“稳定进度不用瞬时 scroll”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-2：逐条检查规则“ORCID 按原始记录翻页”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-3：逐条检查规则“统一研发意图”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-4：逐条检查规则“边界停止可见”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。

定向验证：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=CoreDataSourceTest,OrcidDataSourceTest,CrossrefDataSourceTest,SubjectScopeCatalogTest,ExpertDiscoveryServiceTest
```

各子计划定向用例通过后由集成阶段运行全量测试；无业务实现时不运行测试冒充修改已验证。

## 人工验收清单

### A-1：跨日推进
- 前置条件：测试 CORE 限额100，至少有200条结果。
- 操作步骤：连续运行两次相同条件，查看来源请求 offset。
- 预期结果：第二次从上次 offset 继续；不是再次 offset=0；失败也不清进度。
- 覆盖：I-1、I-4。

### A-2：ORCID 有效空页
- 前置条件：测试端点第一页100条不公开邮箱，第二页1条公开邮箱。
- 操作步骤：以默认研发范围运行 ORCID。
- 预期结果：请求覆盖两页，收录1个通过邮箱校验且未重复的人。
- 覆盖：I-2。

### A-3：范围回归
- 前置条件：研发范围默认配置不变。
- 操作步骤：执行默认发现，再手动执行关键词查询。
- 预期结果：默认请求含目录主题，EuropePMC/PMC OA未启用；手动请求保留指定关键词。
- 覆盖：I-3。

### A-4：保持原业务
- 前置条件：测试环境保留原人工补全三种scope、原资格规则及一位已申请专家；邮件调度关闭。
- 操作步骤：执行本子计划新行为，再执行原人工入口；对比前后专家详情、候选/申请记录、请求范围和邮件记录。
- 预期结果：原入口仍可执行；专家主键、邮箱、署名机构及运营状态不变；已申请专家不会新建候选；默认排除的医学来源仍不参与；邮件新增0；没有付费调用或已应用迁移被改写。
- 覆盖：主方案M-1/M-2/M-3/M-4/M-5及本页跨路径回归。

