# Crossref 与 arXiv 入口修复实施计划

> 执行：superpowers:executing-plans 按任务顺序实施；独立验证使用 fix-v。当前为待审阅方案，未开始代码修改。
**目标**：恢复 Crossref 和 arXiv 检索；请求编码和 HTTP 跳转不再被误判为无数据。
**依赖**：02。
**设计基线**：[主方案](00-discovery-enrichment-master.md)。
**技术栈与约束**：Java 11、Kotlin、Spring Boot 2.7；不新增外部依赖；只改清单文件；不触碰工作区已有无关改动。新增类中辅助DTO/枚举置于所属清单文件内，不暗增文件。
**审查重点**：身份歧义、半页/重启、外部限流、三层并发、旧接口回归；下方用例覆盖本子计划相关项。

## 需求描述

恢复 Crossref 和 arXiv 检索；请求编码和 HTTP 跳转不再被误判为无数据。

必须保持：主方案M-1至M-5全部适用，本子计划只改变下列实现项；不外发邮件、不扩大医学范围、不更改专家主键。
范围外：其他子计划的实现、前端样式改动、付费采购与全量无条件重跑。

## 关键不变量

### Invariant I-1：编码一次
- Rule：Crossref query/filter/cursor/mailto 按组件编码一次，以最终 URI 调 RestTemplate；不能预编码后再调用 String 重载。
- Applies to：Crossref 首次/续页/关键词。
- Violation consequence：HTTP400 或游标乱码。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-2：HTTPS 与错误识别
- Rule：默认 arXiv 地址用 HTTPS；兼容旧配置的官方 http 入口规范化到 https。301 空体、非 Atom、error entry、解析错误都显式失败，不能返回正常零结果。
- Applies to：arXiv 搜索及解析。
- Violation consequence：故障长期零产出。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-3：领域与现有入口
- Rule：保留 arXiv RND_TARGET 分类、手动关键词、年份过滤；Crossref 手动查询行为保留；在本计划同步接入目录研发主题检索约束，04 再统一分片轮换，避免中间版本无条件抓全领域。
- Applies to：手动/定时调用。
- Violation consequence：恢复抓取却改变检索意图。
- 来源：本次审计及本页现状审计列出的K条目。

## 现状审计

两来源是无数据库状态的适配器，返回 PaperSearchResult，游标归 02 管理。Crossref 线上 400 已由单次/双次编码对照复现；arXiv 用线上 Java 11/Spring 实测 HTTP=301/空体，HTTPS=200/有 entry。application.yml 的环境变量缺省值覆盖 ArxivProperties 默认值，必须两处同时修改。下游 consumeOutcome 负责邮箱校验和入库，不在源适配器内写 ES。

所有跨模块存储读写总清单见主方案“存储与全部相关读写路径”；本页对应新增/改动点是其子集。新共享字段仅05的existing externalIds子键；07新建独立任务存储，其他共享表/索引不增列。前端不改动，无新增样式契约。

## 实现方案

### Task 1：Crossref 请求修复
- 约束：I-1。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 改统一 URI 构造；SubjectScopeCatalog新增crossrefQueries(scope)，默认研发检索使用主题词，手动关键词优先。测试真实发送到 HTTP 边界的解码后参数，不只 mock getForObject(String)。

```kotlin
val uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/works")
    .queryParam("filter", filter).queryParam("cursor", cursor ?: "*")
    .queryParam("rows", pageSize).build().encode().toUri()
restTemplate.getForObject(uri, JsonNode::class.java)
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

### Task 2：arXiv 修复
- 约束：I-2、I-3。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 默认 HTTPS；显式检测 HTTP 状态和 Atom error entry。对过滤后空页保留原始记录数推导的 nextCursor，交 02 继续翻页。

```kotlin
if (status.is3xxRedirection || body.isNullOrBlank()) throw IllegalStateException("ARXIV_EMPTY_OR_REDIRECT")
// parse raw entries before publication-year filtering
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

新写路径与读者：本页现状审计明确的消费者继续读取相同字段；新增job由08消费、身份子键由06消费、请求policy由全部OpenAlex调用消费。依赖尚未上线时，新入口默认关闭，既有业务仍运行。

## 变更文件清单

| 文件 | 作用 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSource.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSource.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/config/ArxivProperties.kt` | 类型化配置 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` | 共享Crossref主题契约 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt` | 主题契约验证 |
| `src/main/resources/application.yml` | 环境变量和默认配置 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSourceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSourceTest.kt` | 新增或更新本计划回归验证 |

共 8 个文件；不存在的文件标记为新增，已存在者原位修改；辅助类型放对应文件内。迁移号碰撞是唯一允许在实施前同步改名的路径变化，须同步本清单。

## 验收标准

- V-1：Crossref 解码后的 filter 含 : 和 ,，cursor 内 +/= 正确保留，中文关键词只编码一次。
- V-2：arXiv HTTPS 正常 entry；301空体、error entry、非法 XML 均为失败；年份过滤空页仍有 nextCursor。
- I-1：逐条检查规则“编码一次”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-2：逐条检查规则“HTTPS 与错误识别”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-3：逐条检查规则“领域与现有入口”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。

定向验证：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=CrossrefDataSourceTest,ArxivDataSourceTest,SubjectScopeCatalogTest
```

各子计划定向用例通过后由集成阶段运行全量测试；无业务实现时不运行测试冒充修改已验证。

## 人工验收清单

### A-1：两源恢复
- 前置条件：测试环境每源限额 2 篇，使用真实外部接口。
- 操作步骤：分别手动只选 Crossref、arXiv 执行。
- 预期结果：搜索论文数各>0，Crossref 无编码400，arXiv 无空301；最终新增人数允许因去重为0。
- 覆盖：I-1、I-2。

### A-2：手动检索回归
- 前置条件：选择 arXiv 并设置明确关键词/年份。
- 操作步骤：执行并查看保存的请求与源返回记录。
- 预期结果：查询包含该关键词，返回入处理阶段的论文均在设定年份内。
- 覆盖：I-3。

### A-3：保持原业务
- 前置条件：测试环境保留原人工补全三种scope、原资格规则及一位已申请专家；邮件调度关闭。
- 操作步骤：执行本子计划新行为，再执行原人工入口；对比前后专家详情、候选/申请记录、请求范围和邮件记录。
- 预期结果：原入口仍可执行；专家主键、邮箱、署名机构及运营状态不变；已申请专家不会新建候选；默认排除的医学来源仍不参与；邮件新增0；没有付费调用或已应用迁移被改写。
- 覆盖：主方案M-1/M-2/M-3/M-4/M-5及本页跨路径回归。

