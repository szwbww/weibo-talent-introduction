# OpenAlex 认证与共享额度实施计划

> 执行：superpowers:executing-plans 按任务顺序实施；独立验证使用 fix-v。当前为待审阅方案，未开始代码修改。
**目标**：发现、作者补全和论文查询统一使用环境变量中的 API Key；额度耗尽可识别，密钥不会进入 URL、日志或其他站点。
**依赖**：无。
**设计基线**：[主方案](00-discovery-enrichment-master.md)。
**技术栈与约束**：Java 11、Kotlin、Spring Boot 2.7；不新增外部依赖；只改清单文件；不触碰工作区已有无关改动。新增类中辅助DTO/枚举置于所属清单文件内，不暗增文件。
**审查重点**：身份歧义、半页/重启、外部限流、三层并发、旧接口回归；下方用例覆盖本子计划相关项。

## 需求描述

发现、作者补全和论文查询统一使用环境变量中的 API Key；额度耗尽可识别，密钥不会进入 URL、日志或其他站点。

必须保持：主方案M-1至M-5全部适用，本子计划只改变下列实现项；不外发邮件、不扩大医学范围、不更改专家主键。
范围外：其他子计划的实现、前端样式改动、付费采购与全量无条件重跑。

## 关键不变量

### Invariant I-1：认证边界
- Rule：仅给配置的 OpenAlex API HTTPS origin 添加 Bearer；外部全文站点和通用 RestTemplate 不带 Key；Key 空时保持匿名兼容。禁止记录完整 Authorization、配置对象或含 key 的 URL。
- Applies to：openAlexRestTemplate 全部调用。
- Violation consequence：凭据泄漏或部分调用仍匿名。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-2：共享额度
- Rule：同一 JVM 的发现和补全使用同一 OpenAlexRequestPolicy；解析实际响应额度头。仅为请求频率 429 做有界退避，日额度耗尽返回可识别的 Deferred(resetAt)。无额度头时按已配置免费预算保守限流，不假定无限额；启动后先读取额度。
- Applies to：OpenAlex HTTP 请求。
- Violation consequence：两个任务抢光同一额度、无限等待。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-3：请求量口径
- Rule：最大请求速率建议 5/s，可配置且不超过官方 100/s；列表调用和全文下载分别统计。保留 20% 可用预算优先用于新增专家补全，历史回填优先级最低。
- Applies to：额度分配。
- Violation consequence：抓论文耗尽预算后新专家无法补全。
- 来源：本次审计及本页现状审计列出的K条目。

## 现状审计

OpenAlexProperties 无 apiKey；RestTemplateConfig.openAlexRestTemplate 仅设置超时。OpenAlexDataSource 的 works、authors、ORCID batch、recent works 均使用同一专用客户端，可集中拦截。配置只读来自 application.yml/环境变量；不引入数据库配置。进程内额度状态不是审计源，重启必须用 /rate-limit 或响应头重建。通用 RestTemplate 被 ES/其他来源复用，不能注入认证。官方资料见总方案 CP-1。

所有跨模块存储读写总清单见主方案“存储与全部相关读写路径”；本页对应新增/改动点是其子集。新共享字段仅05的existing externalIds子键；07新建独立任务存储，其他共享表/索引不增列。前端不改动，无新增样式契约。

## 实现方案

### Task 1：配置与认证
- 约束：I-1。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 新增 apiKey、每秒限额及预算保留比例；专用拦截器按 HTTPS origin 验证后注入 Bearer；跨 origin 重定向不得携带认证。

```kotlin
val trusted = uri.scheme == "https" && uri.host == apiHost && uri.port == apiPort
if (trusted && apiKey.isNotBlank()) headers.setBearerAuth(apiKey)
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

### Task 2：额度决策
- 约束：I-2、I-3。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 新增 OpenAlexRequestPolicy 单例，区分 DISCOVERY、NEW_ENRICHMENT、HISTORY_ENRICHMENT；串行保守预留每次请求成本，响应后按实际头校正；更新 resetAt；上层可识别额度暂停。；OpenAlexDataSource.searchPapers标记DISCOVERY；补全入口显式传NEW_ENRICHMENT/HISTORY_ENRICHMENT，上下文try/finally释放，不能按线程名猜。免费消费上限默认带Key为$1/UTC日、无Key为$0.10/UTC日，取配置上限与供应商剩余额度较小值，不自动使用付费余额。

```kotlin
sealed class Permit { object Allowed : Permit(); data class Deferred(val resetAt: Instant) : Permit() }
fun beforeRequest(kind: RequestKind): Permit
fun recordResponse(headers: HttpHeaders): Unit
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

新写路径与读者：本页现状审计明确的消费者继续读取相同字段；新增job由08消费、身份子键由06消费、请求policy由全部OpenAlex调用消费。依赖尚未上线时，新入口默认关闭，既有业务仍运行。

## 变更文件清单

| 文件 | 作用 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexProperties.kt` | 类型化配置 |
| `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 对应生产实现 |
| `src/main/resources/application.yml` | 环境变量和默认配置 |
| `src/test/kotlin/com/weibo/talentintroduction/config/RestTemplateConfigTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicyTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 新增或更新本计划回归验证 |

共 8 个文件；不存在的文件标记为新增，已存在者原位修改；辅助类型放对应文件内。迁移号碰撞是唯一允许在实施前同步改名的路径变化，须同步本清单。

## 验收标准

- V-1：带 Key 的 works/authors 请求使用 Bearer；空 Key 无认证头；外部 origin、HTTP 和重定向目标不带 Key；日志无 Key。
- V-2：固定时钟下剩余额度只够一次时，并发发现/补全最多放行一次；日额度 429 无密集重试，UTC reset 后可重新探测。
- V-3：保留额度生效：发现停止而新专家补全仍可消费，历史回填不抢占；无额度头与重启场景可恢复。
- I-1：逐条检查规则“认证边界”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-2：逐条检查规则“共享额度”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-3：逐条检查规则“请求量口径”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。

定向验证：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RestTemplateConfigTest,OpenAlexRequestPolicyTest,OpenAlexDataSourceTest
```

各子计划定向用例通过后由集成阶段运行全量测试；无业务实现时不运行测试冒充修改已验证。

## 人工验收清单

### A-1：Key 生效
- 前置条件：测试环境设置有效 OPENALEX_API_KEY，任务上限设为 1 页。
- 操作步骤：分别运行深度发现和补充学术数据；查看脱敏 HTTP 审计。
- 预期结果：两类请求均标记 authenticated=true；日志搜索该测试 Key 命中 0 次。
- 覆盖：I-1。

### A-2：额度暂停
- 前置条件：测试代理返回 remaining=0、reset=60 秒；关闭真实邮件任务。
- 操作步骤：启动发现和补全；60 秒后代理恢复额度。
- 预期结果：不会在一分钟内密集重试；显示额度等待；恢复后继续原位置。
- 覆盖：I-2、I-3。

### A-3：保持原业务
- 前置条件：测试环境保留原人工补全三种scope、原资格规则及一位已申请专家；邮件调度关闭。
- 操作步骤：执行本子计划新行为，再执行原人工入口；对比前后专家详情、候选/申请记录、请求范围和邮件记录。
- 预期结果：原入口仍可执行；专家主键、邮箱、署名机构及运营状态不变；已申请专家不会新建候选；默认排除的医学来源仍不参与；邮件新增0；没有付费调用或已应用迁移被改写。
- 覆盖：主方案M-1/M-2/M-3/M-4/M-5及本页跨路径回归。

