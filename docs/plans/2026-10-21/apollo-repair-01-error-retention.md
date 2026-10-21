# 01 · HTTP 错误分类与脱敏留档

状态：可交付执行，尚未实施。修订：r2（2026-10-21）。基线：`962fcfe924ad7562a4ea917d174180df82ac3b5c`。依赖：无。

## 需求描述

可观察结果：新增失败请求能区分 Apollo 额度耗尽、参数错误、认证错误、限流与未知 HTTP 错误；逐条详情接口可读错误诊断。

必须保留：成功响应、真实 HTTP 次数、429 Retry-After、原准入规则与现有成功证据格式。

不做：推断历史 264 条全部额度不足；修改 Apollo 套餐；留存完整错误正文或认证头；新增 UI。

## 关键不变量

### Invariant I-1: 错误分类必须有证据
- Rule：HTTP 422 本身不足以证明参数无效或额度不足。仅 APOLLO 且明确额度不足错误码/短语产生 APOLLO_CREDITS_EXHAUSTED；单独 credits、limit、upgrade、plan 不足以命中。未知 422 使用 APOLLO_HTTP_422_UNCLASSIFIED。
- Applies to：VerificationHttp.request → Worker.completed → attempt.result_code
- Violation consequence：将供给侧故障错判为人不合格，或误暂停正常请求。
- 来源：本次线上单条复现及代码审计

### Invariant I-2: 诊断有限且不泄密
- Rule：错误正文最多读取 16 KiB + 1 字节检测截断；只保存白名单字段与固定分类。错误 response_json 使用 schemaVersion=1、kind=PROVIDER_ERROR；成功 response_json 保持原结构。
- Applies to：HTTP 非 2xx、解析错误、详情接口
- Violation consequence：泄露密钥/个人资料或破坏成功详情重用。
- 来源：original

### Invariant I-3: 哈希与缺失含义真实
- Rule：非成功 response_sha256 保持 NULL；诊断内 bodySha256 为实际读取字节 SHA256，hashScope=FULL/PREFIX，bodyTruncated 显式。历史无诊断返回 NULL/NOT_RECORDED，不能补成已分类。
- Applies to：attempt 写入、QueryService 读取
- Violation consequence：把前缀摘要冒充完整原文，伪造历史证据。
- 来源：K-verification-cached-evidence-replay

## 现状审计

`ApolloPeopleSearchClient.kt:74–100` 非 2xx 分支不读取正文，400/422 都转 REQUEST_INVALID；Success 才保留哈希。`DiscoveryVerificationWorker.kt:141–157` 只在显式 response 参数存在时写 response_json。`DiscoveryVerificationQueryService.kt:97–116` 的 attempt 摘要没有错误诊断。

受影响 store：attempt.response_json（已有 MEDIUMTEXT）和 result_code；本步不新增列。写入仍经 completeAttemptAndProgress 的租约 CAS；读取由 QueryService 和 ReplayTool.freeze 消费。成功 ApolloCandidate、ApolloSearchPage、ORCID 投影不改。

共享表结构、全量读写路径见 [证据审计 E-6/E-7](apollo-repair-retention-evidence.md)，作为本计划规范性组成部分，执行/复验不可跳过。本子计划只修改下表白名单；其余路径为回归边界。

## 实现方案

1. **HTTP 分类（I-1/I-2）**：修改 `ApolloPeopleSearchClient.kt`，给现有失败 Result 子类增加可选诊断参数（默认 NULL），不新增 sealed 子类以免遗漏 ORCID 分支。先按 provider/status 分流；401/403 保留 CONFIG_AUTH，429 保留 RATE_LIMIT，5xx 保留 PROVIDER_UNAVAILABLE。422 检查结构化 error/message/error_code 的有限标量文本；明确 `insufficient credits` / `not enough credits` / `credits exhausted` 才分类额度耗尽；明确参数校验才 REQUEST_INVALID；其余 APOLLO_HTTP_422_UNCLASSIFIED。不使用堆栈或整段响应作为异常 message。
2. **统一诊断模型（I-2/I-3）**：新增 `VerificationDiagnostics.kt`，定义错误诊断 DTO 与 sanitizer。字段固定：schemaVersion、kind、provider、endpointPath（无 query）、httpStatus、providerCode、message、requestId、retryAfter、contentType、bodyBytesRead、bodyTruncated、bodySha256、hashScope、classification。providerCode/requestId/retryAfter ≤128 字符，contentType ≤128，message ≤1024。只白名单提取错误标量，不序列化 people/person/contacts；message 中邮箱、电话号码、Bearer、key/token/password 及 URL query 值脱敏。非 JSON/不可解析正文 message=NULL，保留大小/哈希/类型；不要用任意原文兜底。对非 2xx 与 2xx 解析失败都覆盖。HTTP层仍持有bytes时记录实际大小；客户端对已成功JSON做结构校验失败时，如仅有原Success哈希可用，bodyBytesRead允许NULL、bodySha256引用该原完整响应哈希、hashScope=FULL，禁止从重序列化JSON伪造原响应大小/哈希。
3. **持久化和读取（I-2/I-3）**：`DiscoveryVerificationWorker.kt` 在 completed 将失败诊断写已有 response_json；成功结构不变。`DiscoveryVerificationQueryService.kt` 的 VerificationAttemptSummary 增加可选 diagnostic（默认 NULL），严格只解析 kind=PROVIDER_ERROR 的白名单 DTO。现有 `/api/expert-discovery/verification/items/{itemId}` 返回诊断，列表接口不展开全文。ReplayTool 冻结已有 response_json，无新文件格式。
4. **测试（I-1/I-2/I-3）**：在 HTTP/Worker/Query 三个现有测试文件覆盖完整链路；测试明确额度短语、普通 422、嵌套非标错误、HTML、超长正文、429 两种 Retry-After、敏感字段、200 正文格式不变。新增版本必须可读取历史 NULL。

## 变更文件清单

| 序号 | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClient.kt` | 错误分类与有界读取 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/VerificationDiagnostics.kt` | 新增：错误诊断 DTO/sanitizer |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | 诊断原子落库 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` | 详情 API 可选字段 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClientTest.kt` | HTTP 分类/截断/脱敏 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | attempt 持久化 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` | 新旧 JSON 兼容 |

共 7 个文件；1 个业务子系统；不修改前端、数据库 schema 或 ES mapping。新增测试数据放入列出的测试文件，不另建 fixture 文件。

## 验收标准

- I-1：测试中同为 422 的额度错误、参数错误、未知错误分别输出 3 个不同 result_code；无内容的历史错误不得改写。
- I-2：恶意正文含 token、email、phone、people 数组时，保存内容与 API 输出均不含这些原始值；大正文读取 ≤16385 字节；429 时间不变。
- I-3：完整/截断哈希作用域正确；所有非成功 response_sha256=NULL；200 成功 JSON 与基线一致；已有详情无诊断返回 NULL。
- 集成：Mock HTTP → Worker → MySQL attempt → item 详情；一次真实请求只增一次 request_count。

### 执行命令与通过条件

以下命令在仓库根目录执行；本次只写计划，未运行这些测试。新增用例写入白名单测试文件，复用已有MockRestServiceServer/Mockito/Testcontainers；不新增Mock服务、测试后台或运行时调试接口。

```sh
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ApolloPeopleSearchClientTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest test
```

通过条件：指定测试实际执行，失败0、错误0、必要用例跳过0；检查本次target/surefire-reports时间戳，旧报告不算通过。数据库IT明确检查Docker可用，不得用环境不可用跳过。测试结果与该步代码diff一起交复验；人工清单另行完成。

## 人工验收清单

### A-1: 422 可分辨
- 前置条件：测试环境用 Mock HTTP 返回三种 422：message=insufficient credits、明确 invalid parameter、空正文。
- 操作步骤：1. 各运行一条核验；2. 请求各 item 详情。
- 预期结果：result_code 依次为 APOLLO_CREDITS_EXHAUSTED、REQUEST_INVALID、APOLLO_HTTP_422_UNCLASSIFIED；各 1 次请求。
- 覆盖：I-1/I-2，HTTP→SQL→API

### A-2: 旧记录与成功记录
- 前置条件：使用一条历史无 response_json 的 422、一条现有 200 成功样本。
- 操作步骤：1. 查询两个 item 详情；2. 对照冻结前后的 outcome 与 attempt 次数。
- 预期结果：旧诊断为 NULL；200 证据未丢；outcome 与次数完全不变。
- 覆盖：I-3；保留既有准入/成功格式

### A-3: 截断和限流
- 前置条件：Mock 返回含 token 的 20 KiB 错误及 Retry-After=120 的 429。
- 操作步骤：1. 各执行一次；2. 查看详情、下一次尝试时间。
- 预期结果：错误 bodyTruncated=true、hashScope=PREFIX，无原始 token；429 不早于 120 秒再次请求。
- 覆盖：I-2/I-3；保留限流

